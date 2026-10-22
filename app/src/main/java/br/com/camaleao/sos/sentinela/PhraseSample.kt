package br.com.camaleao.sos.sentinela

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import br.com.camaleao.sos.domain.PhraseMatcher
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer

/** Resultado de uma amostra: se a Sentinela aceitaria e quanto o texto lembra a frase. */
data class SampleResult(val recognized: Boolean, val percent: Int, val heard: String)

/**
 * Amostra de calibração gravada enquanto o botão fica pressionado, como áudio de
 * mensageiro. Fica só na RAM e é zerada depois da análise. Nunca roda junto com a
 * Sentinela: seria uma segunda captura de microfone (CR-12).
 */
class PhraseSample {
    private val pcm = ShortArray(MAX)
    @Volatile private var length = 0
    @Volatile private var recording = false
    private var thread: Thread? = null

    fun start(context: Context): Boolean {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            return false
        val minimum =
            AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val audio =
            runCatching {
                    AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        maxOf(minimum, RATE),
                    )
                }
                .getOrNull() ?: return false
        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            audio.release()
            return false
        }
        length = 0
        recording = true
        audio.startRecording()
        thread =
            Thread({
                    try {
                        while (recording && length < MAX) {
                            val n = audio.read(pcm, length, minOf(1600, MAX - length))
                            if (n <= 0) break
                            length += n
                        }
                    } finally {
                        runCatching { audio.stop() }
                        audio.release()
                    }
                }, "CalibracaoAudio")
                .apply { start() }
        return true
    }

    /** Para a gravação e devolve quantos segundos foram gravados. */
    fun stop(): Double {
        recording = false
        thread?.join(2000)
        return length.toDouble() / RATE
    }

    /** Mesma regra do estágio 1 armado: frase contígua na gramática e segunda leitura. */
    fun evaluate(context: Context, phrase: String, verify: Double): SampleResult {
        try {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            Model(ModelFiles.prepare(context).absolutePath).use { model ->
                val words = mutableListOf<Pair<String, Double>>()
                Recognizer(model, RATE.toFloat(), JSONArray(Stage1.grammar(phrase)).toString()).use { r ->
                    r.setWords(true)
                    feed(r) { words += Stage1.words(JSONObject(it)) }
                    words += Stage1.words(JSONObject(r.finalResult))
                }
                val texts = mutableListOf<String>()
                Recognizer(model, RATE.toFloat()).use { r ->
                    feed(r) { texts += JSONObject(it).optString("text") }
                    texts += JSONObject(r.finalResult).optString("text")
                }
                val heard = texts.filter { it.isNotBlank() }.joinToString(" ")
                val score = PhraseMatcher.bestWindow(phrase, heard)
                val ok = Stage1.found(words, PhraseMatcher.words(phrase)) && score >= verify
                return SampleResult(ok, (score * 100).roundToInt(), heard)
            }
        } finally {
            pcm.fill(0)
            length = 0
        }
    }

    /** Descarta sem analisar: amostra curta ou tela fechada no meio. */
    fun discard() {
        stop()
        pcm.fill(0)
        length = 0
    }

    private fun feed(r: Recognizer, result: (String) -> Unit) {
        var i = 0
        while (i < length) {
            val n = minOf(3200, length - i)
            if (r.acceptWaveForm(pcm.copyOfRange(i, i + n), n)) result(r.result)
            i += n
        }
    }

    companion object {
        private const val RATE = 16000
        private const val MAX = RATE * 10
    }
}
