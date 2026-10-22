package br.com.camaleao.sos.domain

import java.util.Locale

/** Posição já validada: coordenadas, precisão em metros e idade em segundos. */
data class Position(val lat: Double, val lon: Double, val accuracy: Float, val ageSeconds: Long)

object AlertMessage {
    const val DEFAULT = "Preciso de ajuda. Me ligue ou venha até mim."
    const val MAX = 300

    fun link(p: Position) =
        String.format(Locale.US, "https://maps.google.com/?q=%.5f,%.5f", p.lat, p.lon)

    /** Texto do SMS. Sem posição, diz que não há posição — nunca inventa uma. */
    fun alert(base: String, p: Position?): String =
        base.trim().ifBlank { DEFAULT }.take(160) +
            if (p == null) " (Localização indisponível.)"
            else " Localização: ${link(p)} (±${p.accuracy.toInt()} m)"

    /** Repetição do alerta, com a posição do momento. */
    fun repeat(base: String, p: Position?, n: Int, total: Int): String = "Repetindo ($n/$total): " + alert(base, p)

    /** Aviso por SMS antes da ligação em silêncio, para quem atende não falar. */
    fun openLine(p: Position?): String =
        "Vou te ligar e ficar em silêncio: é um alerta. Atenda e escute, não fale." +
            if (p == null) " (Localização indisponível.)"
            else " Localização: ${link(p)} ${coordinates(p)} (±${p.accuracy.toInt()} m)"

    /** Mensagem falada pelo discador do Painel. Sem link: ninguém clica numa ligação. */
    fun spoken(base: String, p: Position?): String =
        "Alerta do Camaleão. " + base.trim().ifBlank { DEFAULT }.take(160) +
            if (p == null) " A localização não está disponível. Verifique o SMS."
            else " A localização foi enviada por SMS."

    fun coordinates(p: Position) = String.format(Locale.US, "%.5f, %.5f", p.lat, p.lon)

    fun location(p: Position?, update: Int): String =
        if (p == null) "Localização indisponível agora."
        else
            (if (update == 0) "Minha localização: " else "Localização atualizada: ") +
                "${link(p)} (±${p.accuracy.toInt()} m)"
}
