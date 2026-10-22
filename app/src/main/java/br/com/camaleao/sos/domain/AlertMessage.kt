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

    fun location(p: Position?, update: Int): String =
        if (p == null) "Localização indisponível agora."
        else
            (if (update == 0) "Minha localização: " else "Localização atualizada: ") +
                "${link(p)} (±${p.accuracy.toInt()} m)"
}
