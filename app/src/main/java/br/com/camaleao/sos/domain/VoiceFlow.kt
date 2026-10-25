package br.com.camaleao.sos.domain

enum class VoicePhase {
    WAITING_KEYWORD,
    COMMAND_WINDOW,
    SOS_EVENT,
}

class VoiceFlow(
    private val phrase: String,
    private val threshold: Double,
    private val commands: List<VoiceCommand> =
        listOf(VoiceCommand(VoiceAction.ALERT, VoiceAction.ALERT.defaultWords, true)),
    /** Quanto a janela de comando espera. A usuária escolhe; ver ADR 0015. */
    private val windowMs: Long = WINDOW_MS,
) {
    var phase = VoicePhase.WAITING_KEYWORD
        private set

    private var deadline = 0L

    fun phrase(text: String, now: Long): Boolean {
        if (phase != VoicePhase.WAITING_KEYWORD || !PhraseMatcher.accepts(phrase, text, threshold))
            return false
        return open(now)
    }

    /** Abre a janela de comando; usado quando o estágio 1 já decidiu pela frase. */
    fun open(now: Long): Boolean {
        if (phase != VoicePhase.WAITING_KEYWORD) return false
        phase = VoicePhase.COMMAND_WINDOW
        deadline = now + windowMs
        return true
    }

    /**
     * Devolve a chave da ação ([VoiceAction.key]), "cancel", "timeout" ou null quando a
     * fala não é comando. Alerta leva a SOS_EVENT; as demais funções voltam à espera.
     */
    fun command(text: String, now: Long): String? {
        if (phase != VoicePhase.COMMAND_WINDOW) return null
        if (now >= deadline) {
            reset()
            return "timeout"
        }
        if (PhraseMatcher.normalize(text) == VoiceCommands.CANCEL) {
            reset()
            return "cancel"
        }
        val action = VoiceCommands.match(text, commands) ?: return null
        if (action == VoiceAction.ALERT) phase = VoicePhase.SOS_EVENT else reset()
        return action.key
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

    companion object {
        /** Padrão da janela de comando: 30 s, medido com voz real em 25/10 (ADR 0015). */
        const val WINDOW_MS = 30_000L
    }
}
