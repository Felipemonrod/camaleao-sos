package br.com.camaleao.sos.domain

import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    @Test
    fun operationsAndPrecedence() {
        assertEquals("14", Calculator.calculate("2+3×4"))
        assertEquals("0.5", Calculator.calculate("1÷2"))
        assertEquals("-4", Calculator.calculate("-2-2"))
        assertEquals("4", Calculator.calculate("1,5+2,5"))
    }

    @Test
    fun divisionByZeroAndBadInput() {
        assertThrows(ArithmeticException::class.java) { Calculator.calculate("1/0") }
        assertThrows(Exception::class.java) { Calculator.calculate("1++") }
    }

    @Test
    fun normalizedAndSimilarPhrase() {
        assertEquals(
            "preciso buscar meu caderno azul",
            PhraseMatcher.normalize("  PRECISO buscar meu caderno azul!! "),
        )
        assertTrue(
            PhraseMatcher.accepts(
                "preciso buscar meu caderno azul",
                "preciso buscar meu caderno azu",
                .95,
            )
        )
    }

    @Test
    fun hardNegativeCannotPassLongSharedSuffix() {
        assertFalse(
            PhraseMatcher.accepts(
                "preciso buscar meu caderno azul",
                "vou buscar meu caderno azul",
                .5,
            )
        )
        assertFalse(
            PhraseMatcher.accepts("preciso buscar meu caderno azul", "meu caderno azul", .5)
        )
    }

    @Test
    fun thresholdRequiresSeparatedRealSamples() {
        assertNull(PhraseMatcher.calibratedThreshold(listOf("a", "a", "a"), listOf("a"), "a"))
        val p = "preciso buscar meu caderno azul"
        assertNotNull(
            PhraseMatcher.calibratedThreshold(
                listOf(p, p, p),
                listOf("vou buscar meu caderno azul"),
                p,
            )
        )
    }

    @Test
    fun keywordAloneDoesNotDispatchAndTimeoutIsSafe() {
        val v = VoiceFlow("preciso buscar meu caderno azul", 1.0)
        assertTrue(v.phrase("preciso buscar meu caderno azul", 0))
        assertEquals(VoicePhase.COMMAND_WINDOW, v.phase)
        assertTrue(v.expire(6000))
        assertEquals(VoicePhase.WAITING_KEYWORD, v.phase)
        assertNull(v.command("enviar alerta", 6001))
    }

    @Test
    fun ambiguityAndCancellationNeverSend() {
        val v = VoiceFlow("preciso buscar meu caderno azul", 1.0)
        v.phrase("preciso buscar meu caderno azul", 0)
        assertNull(v.command("qualquer outra coisa", 100))
        assertEquals("cancel", v.command("cancelar", 200))
        assertEquals(VoicePhase.WAITING_KEYWORD, v.phase)
    }

    @Test
    fun alertNeedsExplicitCommandInsideWindow() {
        val v = VoiceFlow("preciso buscar meu caderno azul", 1.0)
        assertNull(v.command("enviar alerta", 0))
        v.phrase("preciso buscar meu caderno azul", 100)
        assertEquals("alerta", v.command("enviar alerta", 500))
        assertEquals(VoicePhase.SOS_EVENT, v.phase)
    }
}
