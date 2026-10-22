package br.com.camaleao.sos.domain

enum class VoicePhase {
    WAITING_KEYWORD,
    COMMAND_WINDOW,
    SOS_EVENT,
}

class VoiceFlow(private val phrase: String, private val threshold: Double) {
    var phase = VoicePhase.WAITING_KEYWORD
        private set

    private var deadline = 0L

    fun phrase(text: String, now: Long): Boolean {
        if (phase != VoicePhase.WAITING_KEYWORD || !PhraseMatcher.accepts(phrase, text, threshold))
            return false
        phase = VoicePhase.COMMAND_WINDOW
        deadline = now + 6000
        return true
    }

    fun command(text: String, now: Long): String? {
        if (phase != VoicePhase.COMMAND_WINDOW) return null
        if (now >= deadline) {
            reset()
            return "timeout"
        }
        return when (PhraseMatcher.normalize(text)) {
            "enviar alerta" -> {
                phase = VoicePhase.SOS_EVENT
                "alert"
            }
            "cancelar" -> {
                reset()
                "cancel"
            }
            else -> null
        }
    }

    fun expire(now: Long): Boolean {
        if (phase == VoicePhase.COMMAND_WINDOW && now >= deadline) {
            reset()
            return true
        }
        return false
    }

    fun reset() {
        phase = VoicePhase.WAITING_KEYWORD
    }
}
