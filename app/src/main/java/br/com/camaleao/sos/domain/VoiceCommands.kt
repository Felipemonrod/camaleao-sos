package br.com.camaleao.sos.domain

/** Funções acionáveis na janela de comando, cada uma com a palavra escolhida pela usuária. */
enum class VoiceAction(val key: String, val label: String, val defaultWords: String) {
    ALERT("alerta", "Avisar contatos por SMS", "enviar alerta"),
    LOCATION("localizacao", "Compartilhar localização por SMS", "compartilhar localização"),
    RECORD("gravacao", "Gravar áudio cifrado", "gravar evidência"),
}

data class VoiceCommand(val action: VoiceAction, val words: String, val enabled: Boolean)

object VoiceCommands {
    const val CANCEL = "cancelar"

    /** Devolve o motivo da recusa, ou null se o conjunto de comandos é aceitável. */
    fun problem(phrase: String, commands: List<VoiceCommand>): String? {
        val active = commands.filter { it.enabled }
        val phraseWords = PhraseMatcher.words(phrase)
        val seen = mutableListOf(CANCEL)
        for (c in active) {
            val w = PhraseMatcher.words(c.words)
            val n = PhraseMatcher.normalize(c.words)
            if (w.size !in 1..4 || n.length !in 4..40)
                return "“${c.action.label}”: use de 1 a 4 palavras."
            if (w.any { it in phraseWords })
                return "“${c.action.label}”: não repita palavras da Frase de Segurança."
            // Palavras parecidas confundem o reconhecimento e trocam uma função por outra.
            seen.firstOrNull { PhraseMatcher.similarity(it, n) >= .6 }?.let {
                return "“${c.action.label}” parece demais com “$it”."
            }
            seen += n
        }
        return null
    }

    fun grammar(commands: List<VoiceCommand>): List<String> =
        commands.filter { it.enabled }.map { PhraseMatcher.grammarForm(it.words) } + CANCEL

    /** Ação habilitada cujo texto é exatamente o reconhecido no estágio 2. */
    fun match(text: String, commands: List<VoiceCommand>): VoiceAction? {
        val heard = PhraseMatcher.normalize(text)
        return commands.firstOrNull { it.enabled && PhraseMatcher.normalize(it.words) == heard }?.action
    }
}
