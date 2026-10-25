package br.com.camaleao.sos.domain

import br.com.camaleao.sos.sentinela.Stage1
import org.junit.Assert.*
import org.junit.Test

class FunctionsTest {
    private val phrase = "preciso buscar meu caderno azul"

    private fun cmds(alert: String = "enviar alerta", location: String = "compartilhar localização", record: String = "gravar evidência", on: Boolean = true) =
        listOf(
            VoiceCommand(VoiceAction.ALERT, alert, true),
            VoiceCommand(VoiceAction.LOCATION, location, on),
            VoiceCommand(VoiceAction.RECORD, record, on),
            VoiceCommand(VoiceAction.CALL, VoiceAction.CALL.defaultWords, on),
        )

    @Test
    fun defaultCommandsAreAccepted() {
        assertNull(VoiceCommands.problem(phrase, cmds()))
    }

    @Test
    fun commandCannotReusePhraseWordsOrCancel() {
        assertNotNull(VoiceCommands.problem(phrase, cmds(alert = "caderno")))
        assertNotNull(VoiceCommands.problem(phrase, cmds(alert = "cancelar")))
        assertNotNull(VoiceCommands.problem(phrase, cmds(alert = "cancela")))
    }

    @Test
    fun similarCommandsAreRefusedOnlyWhenBothEnabled() {
        assertNotNull(VoiceCommands.problem(phrase, cmds(alert = "gravar agora", record = "gravar agora")))
        assertNull(VoiceCommands.problem(phrase, cmds(alert = "gravar agora", record = "gravar agora", on = false)))
    }

    @Test
    fun disabledFunctionIsNeitherHeardNorMatched() {
        val c = cmds(on = false)
        assertEquals(listOf("enviar alerta", "cancelar"), VoiceCommands.grammar(c))
        assertNull(VoiceCommands.match("gravar evidência", c))
        assertEquals(VoiceAction.ALERT, VoiceCommands.match("Enviar alerta!", c))
    }

    @Test
    fun customWordTriggersItsOwnFunction() {
        val c = cmds(record = "chama a vizinha")
        val v = VoiceFlow(phrase, 1.0, c)
        assertTrue(v.phrase(phrase, 0))
        assertEquals(VoiceAction.RECORD.key, v.command("chama a vizinha", 1000))
        assertEquals(VoicePhase.WAITING_KEYWORD, v.phase)
    }

    @Test
    fun smsNeverInventsPosition() {
        assertEquals(
            "Preciso de ajuda. Me ligue ou venha até mim. (Localização indisponível.)",
            AlertMessage.alert("", null),
        )
        val m = AlertMessage.alert("Socorro", Position(-23.5505199, -46.6333094, 12.7f, 3))
        assertEquals("Socorro Localização: https://maps.google.com/?q=-23.55052,-46.63331 (±12 m)", m)
        assertTrue(AlertMessage.location(null, 0).contains("indisponível"))
    }

    @Test
    fun callMessagesNeverInventPosition() {
        assertTrue(AlertMessage.openLine(null).contains("indisponível"))
        assertFalse(AlertMessage.spoken("", null).contains("http"))
        val p = Position(-23.55052, -46.63331, 12f, 3)
        assertTrue(AlertMessage.openLine(p).contains("-23.55052, -46.63331"))
        assertTrue(AlertMessage.spoken("", p).contains("enviada por SMS"))
    }

    @Test
    fun onlyRecognizedReadingsCountAsPositive() {
        val p = "Preciso buscar meu caderno azul"
        assertTrue(PhraseMatcher.recognized(p, "preciso buscar meu caderno azul"))
        assertFalse(PhraseMatcher.recognized(p, "o preciso buscar meu caderno azul"))
        assertFalse(PhraseMatcher.recognized(p, "preciso buscar meu carro azul"))
        val hit = List(3) { "preciso buscar meu caderno azul" }
        assertNotNull(PhraseMatcher.calibratedThreshold(hit, listOf("preciso buscar meu carro azul"), p))
        // Negativa que disparou como a frase bloqueia o ajuste.
        assertNull(PhraseMatcher.calibratedThreshold(hit, listOf(hit[0]), p))
    }

    @Test
    fun similarNegativeRaisesThePersonalThreshold() {
        // Negativa que passaria no padrão (85 % >= 70 %) sobe a exigência para separar.
        assertEquals(0.9, PhraseMatcher.personalVerify(listOf(95, 100, 96), listOf(85), 0.7)!!, 1e-9)
        // Negativa distante não baixa a exigência abaixo do piso.
        assertEquals(0.7, PhraseMatcher.personalVerify(listOf(90, 92, 95), listOf(30), 0.7)!!, 1e-9)
        assertNull(PhraseMatcher.personalVerify(listOf(88, 100, 95), listOf(88), 0.7))
        assertNull(PhraseMatcher.personalVerify(listOf(100, 100), listOf(10), 0.7))
    }

    @Test
    fun brazilianNumbersLoseCarrierCodeAndKeepDigits() {
        assertEquals("+5511999990000", PhoneBr.normalize("0 15 11 99999-0000"))
        assertEquals("+5511999990000", PhoneBr.normalize("(11) 99999-0000"))
        assertEquals("+5511999990000", PhoneBr.normalize("+55 11 99999-0000"))
        assertEquals("+5511999990000", PhoneBr.normalize("011 99999-0000"))
        assertEquals("+5511999990000", PhoneBr.normalize("5511999990000"))
        assertEquals("+551133334444", PhoneBr.normalize("015 11 3333-4444"))
        assertEquals("+5555999990000", PhoneBr.normalize("55 99999-0000"))
        assertNull(PhoneBr.normalize("11 89999-0000"))
        assertEquals("+5511988887777", PhoneBr.normalize("0 15 11 8888-7777"))
        assertNull(PhoneBr.normalize("99999-0000"))
        assertNull(PhoneBr.normalize("11 1234-5678"))
        assertEquals("+55 (11) 99999-0000", PhoneBr.format("+5511999990000"))
        assertTrue(AlertMessage.repeat("", null, 2, 6).startsWith("Repetindo (2/6): "))
    }

    /** RF-73: a espera da janela de comando é escolhida pela usuária. */
    @Test
    fun theCommandWindowLengthIsChosenByTheUser() {
        val long = VoiceFlow(phrase, 0.9, cmds(), 30_000L)
        assertTrue(long.open(0))
        assertEquals(VoiceAction.ALERT.key, long.command("enviar alerta", 25_000))

        val short = VoiceFlow(phrase, 0.9, cmds(), 6_000L)
        assertTrue(short.open(0))
        assertEquals("timeout", short.command("enviar alerta", 25_000))

        assertEquals(30_000L, VoiceFlow.WINDOW_MS)
    }

    // RF-69 — função dita na mesma fala da frase.

    private val target = PhraseMatcher.words(phrase)
    private val entries = VoiceCommands.grammar(cmds())

    private fun heard(text: String) = PhraseMatcher.words(text).map { it to 1.0 }

    @Test
    fun functionSpokenRightAfterThePhraseIsFound() {
        assertEquals(
            "enviar alerta",
            Stage1.commandAfterPhrase(heard("$phrase enviar alerta"), target, entries),
        )
        // Com fala comum entre a frase e a palavra, a função continua valendo.
        assertEquals(
            "chamar contato",
            Stage1.commandAfterPhrase(heard("$phrase por favor chamar contato"), target, entries),
        )
    }

    @Test
    fun functionSpokenBeforeThePhraseDoesNotCount() {
        assertNull(Stage1.commandAfterPhrase(heard("enviar alerta $phrase"), target, entries))
    }

    @Test
    fun functionWordsMustBeContiguous() {
        assertNull(Stage1.commandAfterPhrase(heard("$phrase enviar o alerta"), target, entries))
    }

    @Test
    fun earliestFunctionAfterThePhraseWins() {
        assertEquals(
            "chamar contato",
            Stage1.commandAfterPhrase(
                heard("$phrase chamar contato enviar alerta"),
                target,
                entries,
            ),
        )
    }

    @Test
    fun withoutThePhraseThereIsNoFunction() {
        assertNull(Stage1.commandAfterPhrase(heard("enviar alerta agora"), target, entries))
        assertNull(Stage1.commandAfterPhrase(heard(phrase), target, entries))
    }

    @Test
    fun cancelInTheSameBreathIsReported() {
        assertEquals(
            VoiceCommands.CANCEL,
            Stage1.commandAfterPhrase(heard("$phrase cancelar"), target, entries),
        )
        // E não é confundido com uma função: quem decide é o VoskPipeline.
        assertNull(VoiceCommands.match(VoiceCommands.CANCEL, cmds()))
    }

    @Test
    fun disabledFunctionIsNotHeardInTheSameBreath() {
        val onlyAlert = cmds(on = false)
        assertNull(
            Stage1.commandAfterPhrase(
                heard("$phrase chamar contato"),
                target,
                VoiceCommands.grammar(onlyAlert),
            )
        )
    }
}
