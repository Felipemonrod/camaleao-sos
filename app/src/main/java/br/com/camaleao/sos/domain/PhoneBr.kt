package br.com.camaleao.sos.domain

/**
 * Número brasileiro no formato +55DDDNÚMERO, o mesmo para SMS e ligação. Tira o que a
 * agenda costuma trazer de discagem: 0 de longa distância, código de operadora (o "15"
 * de 0 15 11 …), 55 e 0055. Assim o código de operadora nunca fica gravado no contato.
 */
object PhoneBr {
    fun normalize(raw: String): String? {
        val plus = raw.trim().startsWith("+")
        var d = raw.filter(Char::isDigit)
        when {
            plus && !d.startsWith("55") -> return if (d.length in 8..15) "+$d" else null
            plus -> d = d.drop(2)
            d.startsWith("0055") -> d = d.drop(4)
            d.startsWith("00") -> return null
            // 0 + operadora (2 dígitos) + DDD + número, ou só 0 + DDD + número.
            d.startsWith("0") -> d = d.drop(1).let { if (it.length in 12..13) it.drop(2) else it }
            d.startsWith("55") && d.length in 12..13 -> d = d.drop(2)
        }
        if (d.length !in 10..11) return null
        // DDD vai de 11 a 99 e não tem zero; celular tem 9 dígitos começando com 9.
        if ('0' in d.take(2)) return null
        var number = d.drop(2)
        // Celular salvo antes do nono dígito (8 dígitos, de 6 a 9): ganha o 9 na frente.
        if (number.length == 8 && number[0] in '6'..'9') number = "9$number"
        if (number.length == 9 && number[0] != '9') return null
        if (number.length == 8 && number[0] !in '2'..'5') return null
        return "+55${d.take(2)}$number"
    }

    /** "+55 (11) 99999-0000" para conferir na tela; estrangeiro fica como está. */
    fun format(e164: String): String {
        if (!e164.startsWith("+55") || e164.length !in 13..14) return e164
        val n = e164.drop(5)
        return "+55 (${e164.substring(3, 5)}) ${n.dropLast(4)}-${n.takeLast(4)}"
    }
}
