package br.com.camaleao.sos.sentinela

import br.com.camaleao.sos.domain.PhraseMatcher
import br.com.camaleao.sos.domain.VoiceFlow
import br.com.camaleao.sos.domain.VoicePhase
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer

/** ADR 0002: o modelo é único; a gramática muda entre os dois estágios. */
class VoskPipeline(
    directory: File,
    private val phrase: String,
    threshold: Double,
    private val test: Boolean,
    private val onPhrase: (String) -> Unit,
    private val onAlert: () -> Unit,
    private val onPhase: (String) -> Unit,
) : AutoCloseable {
    init {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
    }

    private val model = Model(directory.absolutePath)
    private val flow = VoiceFlow(phrase, threshold)
    private val recognizer = Recognizer(model, 16000f, keywordGrammar()).apply { setWords(true) }
    private val ring = ShortArray(64000)
    private var cursor = 0

    private fun keywordGrammar(): String {
        val words = PhraseMatcher.words(phrase)
        val tail = words.drop(1).joinToString(" ")
        return JSONArray(
                listOf(PhraseMatcher.normalize(phrase), "vou $tail", "voce $tail", "[unk]")
            )
            .toString()
    }

    fun accept(pcm: ShortArray, count: Int, now: Long) {
        for (i in 0 until count) {
            ring[cursor] = pcm[i]
            cursor = (cursor + 1) % ring.size
        }
        if (flow.expire(now)) {
            waiting()
            return
        }
        if (!recognizer.acceptWaveForm(pcm, count)) return
        val text = JSONObject(recognizer.result).optString("text")
        if (flow.phase == VoicePhase.WAITING_KEYWORD) {
            if (test) {
                if (text.isNotBlank()) onPhrase(text)
                return
            }
            if (flow.phrase(text, now)) {
                recognizer.reset()
                recognizer.setGrammar(
                    JSONArray(listOf("enviar alerta", "cancelar", "[unk]")).toString()
                )
                onPhase(flow.phase.name)
            }
        } else if (flow.phase == VoicePhase.COMMAND_WINDOW) {
            when (flow.command(text, now)) {
                "alert" -> {
                    onPhase(VoicePhase.SOS_EVENT.name)
                    onAlert()
                    waiting()
                }
                "cancel",
                "timeout" -> waiting()
            }
        }
    }

    private fun waiting() {
        flow.reset()
        recognizer.reset()
        recognizer.setGrammar(keywordGrammar())
        onPhase(flow.phase.name)
    }

    fun cancel() = waiting()

    override fun close() {
        ring.fill(0)
        recognizer.close()
        model.close()
    }
}
