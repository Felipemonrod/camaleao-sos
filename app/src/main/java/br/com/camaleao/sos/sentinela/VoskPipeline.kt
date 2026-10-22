package br.com.camaleao.sos.sentinela

import br.com.camaleao.sos.domain.PhraseMatcher
import br.com.camaleao.sos.domain.VoiceAction
import br.com.camaleao.sos.domain.VoiceCommand
import br.com.camaleao.sos.domain.VoiceCommands
import br.com.camaleao.sos.domain.VoiceFlow
import br.com.camaleao.sos.domain.VoicePhase
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer

/**
 * ADR 0002: o modelo é único; a gramática muda entre os dois estágios.
 *
 * Estágio 1 (ver docs/06, "Ajuste medido"): a gramática traz a frase, as palavras dela
 * e palavras comuns como iscas. Assim, fala parecida sai como palavras soltas em vez
 * de ser forçada para a frase inteira. A frase só conta se aparecer contígua, com
 * confiança mínima por palavra.
 */
class VoskPipeline(
    directory: File,
    private val phrase: String,
    threshold: Double,
    private val test: Boolean,
    private val commands: List<VoiceCommand>,
    private val onPhrase: (String) -> Unit,
    private val onCommand: (VoiceAction) -> Unit,
    private val onPhase: (String) -> Unit,
    /** Só em build debug: eventos e notas para medição com voz real, nunca texto (RF-52). */
    private val diagnostics: Boolean = false,
) : AutoCloseable {
    init {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
    }

    private val model = Model(directory.absolutePath)
    private val flow = VoiceFlow(phrase, threshold, commands)
    private val target = PhraseMatcher.words(phrase)
    private val recognizer = Recognizer(model, 16000f, keywordGrammar()).apply { setWords(true) }
    // Segunda leitura: vocabulário aberto sobre o Buffer pré-evento, só após acerto da gramática.
    private val verifier = Recognizer(model, 16000f)
    private val ring = ShortArray(RING)
    private var cursor = 0

    private fun keywordGrammar(): String =
        JSONArray(Stage1.grammar(phrase)).toString()

    private fun commandGrammar(): String =
        JSONArray(Stage1.withDecoys(VoiceCommands.grammar(commands))).toString()

    fun accept(pcm: ShortArray, count: Int, now: Long) {
        for (i in 0 until count) {
            ring[cursor] = pcm[i]
            cursor = (cursor + 1) % ring.size
        }
        if (flow.expire(now)) {
            log("janela: timeout")
            waiting()
            return
        }
        if (!recognizer.acceptWaveForm(pcm, count)) return
        val result = JSONObject(recognizer.result)
        val text = result.optString("text")
        if (flow.phase == VoicePhase.WAITING_KEYWORD) {
            if (test) {
                if (text.isNotBlank())
                    onPhrase(
                        if (Stage1.found(Stage1.words(result), target) && verified())
                            target.joinToString(" ")
                        else text
                    )
                return
            }
            // O endpoint pode cortar a frase numa micro-pausa: junta com o resultado
            // anterior se ele acabou há pouco.
            val current = Stage1.words(result)
            val joined = if (now - carryAt <= CARRY_MS) carry + current else current
            carry = current
            carryAt = now
            val hit = Stage1.found(joined, target)
            val ok = hit && verified()
            if (current.isNotEmpty() || hit)
                log(
                    if (!hit) "fala sem a frase (${current.size} palavras)"
                    else if (ok) "FRASE aceita nota=$lastScore"
                    else "frase recusada na 2a leitura nota=$lastScore"
                )
            if (ok && flow.open(now)) {
                recognizer.reset()
                recognizer.setGrammar(commandGrammar())
                onPhase(flow.phase.name)
            }
        } else if (flow.phase == VoicePhase.COMMAND_WINDOW) {
            val spoken = Stage1.command(result, VoiceCommands.grammar(commands))
            if (spoken == null) {
                log("janela: fala sem comando")
                return
            }
            when (val key = flow.command(spoken, now)) {
                null -> Unit
                "cancel",
                "timeout" -> {
                    log("janela: $key")
                    waiting()
                }
                else -> {
                    val action = VoiceAction.entries.first { it.key == key }
                    log("COMANDO ${action.key}")
                    if (action == VoiceAction.ALERT) onPhase(VoicePhase.SOS_EVENT.name)
                    onCommand(action)
                    waiting()
                }
            }
        }
    }

    /** Relê os últimos 8 s em RAM com vocabulário aberto e confirma a frase. */
    private fun verified(): Boolean {
        verifier.reset()
        val ordered = ShortArray(RING)
        System.arraycopy(ring, cursor, ordered, 0, RING - cursor)
        System.arraycopy(ring, 0, ordered, RING - cursor, cursor)
        var i = 0
        while (i < RING) {
            val n = minOf(3200, RING - i)
            verifier.acceptWaveForm(ordered.copyOfRange(i, i + n), n)
            i += n
        }
        val text = JSONObject(verifier.finalResult).optString("text")
        ordered.fill(0)
        verifier.reset()
        lastScore = "%.2f".format(java.util.Locale.ROOT, PhraseMatcher.bestWindow(phrase, text))
        return PhraseMatcher.bestWindow(phrase, text) >= Stage1.VERIFY
    }

    private var lastScore = ""
    private var carry = emptyList<Pair<String, Double>>()
    private var carryAt = Long.MIN_VALUE / 2

    private fun log(event: String) {
        if (diagnostics) android.util.Log.i("CamaleaoVoz", event)
    }

    private fun waiting() {
        carry = emptyList()
        flow.reset()
        recognizer.reset()
        recognizer.setGrammar(keywordGrammar())
        onPhase(flow.phase.name)
    }

    fun cancel() = waiting()

    override fun close() {
        ring.fill(0)
        verifier.close()
        recognizer.close()
        model.close()
    }
}

private const val RING = 8 * 16000
private const val CARRY_MS = 2500L

/** Regras do estágio 1 e da leitura de comandos, separadas para teste sem aparelho. */
object Stage1 {
    /** Nota mínima da segunda leitura, escolhida em tools/voz/avaliar.py. */
    const val VERIFY = 0.70

    /** Confiança mínima por palavra da frase, escolhida em tools/voz/avaliar.py. */
    var minConfidence = 0.0

    // Palavras frequentes que dão ao decodificador uma saída "barata" para fala comum.
    // A mesma lista está em tools/voz/avaliar.py (ISCAS); mudar uma exige mudar a outra.
    val DECOYS =
        ("a o e de do da que em um uma eu você ele ela não sim vou vai tem tá " +
                "para pra com por mais muito agora hoje aqui la isso esse essa meu minha " +
                "seu sua casa porta mesa comida conta carro escola casaco cedo tarde " +
                "quero preciso vamos olha gente ah bom dia noite onde quando")
            .split(' ')

    fun withDecoys(entries: List<String>): List<String> {
        val words = entries.flatMap { it.split(' ') }
        return entries + (words + DECOYS).distinct().filter { it !in entries } + "[unk]"
    }

    fun grammar(phrase: String): List<String> = withDecoys(listOf(PhraseMatcher.grammarForm(phrase)))

    fun words(result: JSONObject): List<Pair<String, Double>> {
        val a = result.optJSONArray("result") ?: return emptyList()
        return (0 until a.length()).map {
            val w = a.getJSONObject(it)
            PhraseMatcher.normalize(w.getString("word")) to w.optDouble("conf", 1.0)
        }
    }

    /** A frase aparece contígua nas palavras, com confiança mínima em cada uma. */
    fun found(ws: List<Pair<String, Double>>, target: List<String>): Boolean {
        for (i in 0..ws.size - target.size) {
            val window = ws.subList(i, i + target.size)
            if (window.map { it.first } == target && window.all { it.second >= minConfidence })
                return true
        }
        return false
    }

    /** Primeiro comando da gramática que aparece contíguo no resultado do estágio 2. */
    fun command(result: JSONObject, entries: List<String>): String? {
        val ws = words(result).map { it.first }
        return entries.firstOrNull { entry ->
            val t = PhraseMatcher.words(entry)
            (0..ws.size - t.size).any { ws.subList(it, it + t.size) == t }
        }
    }
}
