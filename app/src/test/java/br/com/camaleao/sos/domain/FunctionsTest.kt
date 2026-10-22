package br.com.camaleao.sos.domain

import org.junit.Assert.*
import org.junit.Test

class FunctionsTest {
    private val phrase = "preciso buscar meu caderno azul"

    private fun cmds(alert: String = "enviar alerta", location: String = "compartilhar localização", record: String = "gravar evidência", on: Boolean = true) =
        listOf(
            VoiceCommand(VoiceAction.ALERT, alert, true),
            VoiceCommand(VoiceAction.LOCATION, location, on),
            VoiceCommand(VoiceAction.RECORD, record, on),
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
}
