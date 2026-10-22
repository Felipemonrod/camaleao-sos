package br.com.camaleao.sos.domain

import java.math.BigDecimal
import java.math.MathContext

/** Parser de quatro operações, com precedência e limite de entrada. */
object Calculator {
    fun calculate(expression: String): String {
        require(expression.length in 1..120)
        val source = expression.replace(',', '.').replace('×', '*').replace('÷', '/')
        var pos = 0
        fun number(): BigDecimal {
            val start = pos
            if (pos < source.length && (source[pos] == '-' || source[pos] == '+')) pos++
            while (pos < source.length && (source[pos].isDigit() || source[pos] == '.')) pos++
            require(pos > start) { "Número inválido" }
            return source.substring(start, pos).toBigDecimal()
        }
        fun term(): BigDecimal {
            var value = number()
            while (pos < source.length && source[pos] in "*/") {
                val op = source[pos++]
                val b = number()
                value =
                    if (op == '*') value.multiply(b, MathContext.DECIMAL64)
                    else value.divide(b, MathContext.DECIMAL64)
            }
            return value
        }
        var result = term()
        while (pos < source.length) {
            val op = source[pos++]
            require(op == '+' || op == '-')
            val b = term()
            result = if (op == '+') result.add(b) else result.subtract(b)
        }
        return result.stripTrailingZeros().toPlainString().take(80)
    }
}
