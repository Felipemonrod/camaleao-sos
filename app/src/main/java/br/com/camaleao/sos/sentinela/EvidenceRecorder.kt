package br.com.camaleao.sos.sentinela

import android.content.Context
import br.com.camaleao.sos.LiveState
import br.com.camaleao.sos.security.DeviceCrypto
import br.com.camaleao.sos.sos.Store
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gravação pós-disparo (RF-46, RF-51). Recebe o PCM da captura única da Sentinela
 * (RF-29) e grava em arquivo privado, em blocos AES-GCM de ~1 s com chave aleatória
 * por gravação. A chave é guardada envelopada pelo Keystore. Nada vai para galeria.
 *
 * Formato: "CAMEV1" + taxa (int) e, por bloco: tamanho (int), IV (12 bytes) e
 * cifrado+tag. O índice do bloco é AAD, o que impede reordenar blocos sem detecção.
 */
class EvidenceRecorder(private val context: Context, private val store: Store) {
    private var out: DataOutputStream? = null
    private var key: SecretKey? = null
    private var digest: MessageDigest? = null
    private val pending = ByteArrayOutputStream()
    private var chunks = 0L
    private var samples = 0L
    private var preroll = 0L
    private var id = ""
    private var until = 0L
    /** Tempo sem captura, somado: o microfone fica da chamada em linha aberta (RF-71). */
    private var gap = 0L
    private var lastAudio = 0L
    private val random = SecureRandom()

    val active
        get() = out != null

    /** Só em build debug, e só estado — nunca áudio nem transcrição (RF-52). */
    private fun log(event: String) {
        if (
            (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        )
            android.util.Log.i("CamaleaoGrav", event)
    }

    /**
     * Começa uma gravação; se já houver uma, apenas estende o prazo. [before] é o Buffer
     * pré-evento e entra como primeiro trecho, para o arquivo não começar depois do
     * disparo (RF-70). Esta classe zera a cópia recebida.
     */
    fun start(origin: String, durationMs: Long, now: Long, before: ShortArray? = null): Boolean {
        if (active) {
            before?.fill(0)
            until = maxOf(until, now + durationMs)
            log("start($origin): já ativa, prazo estendido")
            return true
        }
        return try {
            id = UUID.randomUUID().toString()
            val dir = File(context.filesDir, "evidence").apply { mkdirs() }
            val k = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            val stream = DataOutputStream(BufferedOutputStream(FileOutputStream(File(dir, "$id.bin"))))
            stream.write(MAGIC)
            stream.writeInt(RATE)
            key = k
            out = stream
            digest = MessageDigest.getInstance("SHA-256")
            chunks = 0
            samples = 0
            preroll = 0
            gap = 0
            lastAudio = 0
            until = now + durationMs
            save(
                JSONObject()
                    .put("id", id)
                    .put("created_at", Instant.now().toString())
                    .put("origin", origin)
                    .put("state", "GRAVANDO")
                    .put("key", store.crypto.seal(DeviceCrypto.b64(k.encoded)))
            )
            before?.let {
                chunked(stream, it)
                preroll = it.size.toLong()
                it.fill(0)
            }
            LiveState.recording = true
            LiveState.changed()
            log("start($origin): id=${id.take(8)} preroll=${preroll / RATE}s")
            true
        } catch (e: Exception) {
            log("start($origin): falhou ${e.javaClass.simpleName}")
            stop("falha ao iniciar")
            false
        }
    }

    /**
     * O prazo corre mesmo sem captura. Durante a ligação em linha aberta o microfone é da
     * chamada e [accept] não é chamado; sem isto a gravação nunca terminaria (RF-71).
     */
    fun tick(now: Long) {
        if (out != null && now >= until) stop("prazo concluído")
    }

    fun accept(pcm: ShortArray, count: Int, now: Long) {
        val stream = out ?: return
        // Buraco na captura: anotado, nunca preenchido com amostras inventadas (RF-71).
        if (lastAudio != 0L && now - lastAudio > GAP_MS) gap += now - lastAudio
        lastAudio = now
        try {
            val bytes = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until count) bytes.putShort(pcm[i])
            pending.write(bytes.array())
            samples += count
            if (pending.size() >= RATE * 2) flush(stream)
            if (now >= until) stop("prazo concluído")
        } catch (_: IOException) {
            stop("falha de escrita")
        }
    }

    /** Escreve em blocos de 1 s: o exportador recusa bloco maior que `RATE * 4` bytes. */
    private fun chunked(stream: DataOutputStream, pcm: ShortArray) {
        var i = 0
        while (i < pcm.size) {
            val n = minOf(RATE, pcm.size - i)
            val bytes = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (j in 0 until n) bytes.putShort(pcm[i + j])
            pending.write(bytes.array())
            flush(stream)
            samples += n
            i += n
        }
    }

    private fun flush(stream: DataOutputStream) {
        if (pending.size() == 0) return
        val plain = pending.toByteArray()
        pending.reset()
        digest?.update(plain)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher =
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
                updateAAD(aad(chunks))
            }
        val sealed = cipher.doFinal(plain)
        plain.fill(0)
        stream.writeInt(sealed.size)
        stream.write(iv)
        stream.write(sealed)
        chunks++
    }

    fun stop(reason: String) {
        val stream = out
        out = null
        if (stream == null) {
            log("stop($reason): nada ativo neste gravador")
            LiveState.recording = false
            LiveState.changed()
            return
        }
        log("stop($reason): id=${id.take(8)}")
        var state = "CONCLUIDA"
        try {
            flush(stream)
            stream.close()
        } catch (_: IOException) {
            state = "INTERROMPIDA"
        }
        val file = File(File(context.filesDir, "evidence"), "$id.bin")
        update(id) {
            it.put("state", state)
                .put("ended_at", Instant.now().toString())
                .put("reason", reason)
                .put("chunks", chunks)
                .put("duration_s", samples / RATE)
                .put("preroll_s", preroll / RATE)
                .put("gap_s", gap / 1000)
                .put("original_sha256", digest?.digest()?.hex().orEmpty())
                .put("encrypted_sha256", if (file.exists()) sha256(file) else "")
        }
        key = null
        digest = null
        pending.reset()
        // Só aqui, depois de o registro estar fechado. Zerar antes faz a tela — e quem
        // espera o fim da gravação — ler um estado que ainda diz GRAVANDO. O fechamento
        // cifra o resto e passa SHA-256 no arquivo inteiro, então a janela não é curta.
        LiveState.recording = false
        LiveState.changed()
    }

    private fun save(entry: JSONObject) =
        synchronized(EvidenceRecorder::class.java) {
            store.put("evidence", JSONArray(store.get("evidence", "[]")).put(entry).toString())
        }

    private fun update(id: String, change: (JSONObject) -> Unit) = update(store, id, change)

    companion object {
        const val RATE = 16000
        /** Acima disto a pausa entre dois blocos é corte, não ritmo normal da captura. */
        private const val GAP_MS = 1500L
        private val MAGIC = "CAMEV1".toByteArray()

        private fun aad(index: Long) = ByteBuffer.allocate(8).putLong(index).array()

        private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

        fun sha256(file: File): String {
            val d = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { i ->
                val b = ByteArray(65536)
                while (true) {
                    val n = i.read(b)
                    if (n < 0) break
                    d.update(b, 0, n)
                }
            }
            return d.digest().hex()
        }

        fun list(store: Store): List<JSONObject> =
            JSONArray(store.get("evidence", "[]")).let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.reversed()
            }

        fun update(store: Store, id: String, change: (JSONObject) -> Unit) =
            synchronized(EvidenceRecorder::class.java) {
                val a = JSONArray(store.get("evidence", "[]"))
                for (i in 0 until a.length()) a.getJSONObject(i).takeIf { it.getString("id") == id }?.let(change)
                store.put("evidence", a.toString())
            }

        /** Decifra para WAV, conferindo cada tag GCM, a ordem dos blocos e o hash original. */
        fun exportWav(context: Context, store: Store, id: String, target: OutputStream) {
            val entry = list(store).first { it.getString("id") == id }
            val key = SecretKeySpec(DeviceCrypto.un64(store.crypto.open(entry.getString("key"))), "AES")
            val file = File(File(context.filesDir, "evidence"), "$id.bin")
            val digest = MessageDigest.getInstance("SHA-256")
            val pcm = ByteArrayOutputStream()
            DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
                val magic = ByteArray(MAGIC.size).also(input::readFully)
                require(magic.contentEquals(MAGIC) && input.readInt() == RATE) { "Formato desconhecido" }
                var index = 0L
                while (input.available() > 0) {
                    val size = input.readInt()
                    require(size in 16..RATE * 4) { "Bloco inválido" }
                    val iv = ByteArray(12).also(input::readFully)
                    val sealed = ByteArray(size).also(input::readFully)
                    val plain =
                        Cipher.getInstance("AES/GCM/NoPadding").run {
                            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                            updateAAD(aad(index++))
                            doFinal(sealed)
                        }
                    digest.update(plain)
                    pcm.write(plain)
                }
                if (entry.has("chunks")) require(index == entry.getLong("chunks")) { "Blocos faltando" }
            }
            entry.optString("original_sha256").takeIf { it.isNotBlank() }?.let {
                require(digest.digest().hex() == it) { "Hash do áudio original diverge" }
            }
            val data = pcm.toByteArray()
            val header =
                ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                    put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray())
                    put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
                    putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
                    put("data".toByteArray()); putInt(data.size)
                }
            target.write(header.array())
            target.write(data)
            data.fill(0)
        }

        fun delete(context: Context, store: Store, id: String) =
            synchronized(EvidenceRecorder::class.java) {
                File(File(context.filesDir, "evidence"), "$id.bin").delete()
                val a = JSONArray(store.get("evidence", "[]"))
                val keep = JSONArray()
                for (i in 0 until a.length()) if (a.getJSONObject(i).getString("id") != id) keep.put(a.get(i))
                store.put("evidence", keep.toString())
            }
    }
}
