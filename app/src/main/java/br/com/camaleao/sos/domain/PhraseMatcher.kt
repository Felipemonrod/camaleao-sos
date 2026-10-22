package br.com.camaleao.sos.domain

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

object PhraseMatcher {
    fun normalize(value: String) =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    /**
     * Forma enviada à gramática do Vosk: minúsculas e sem pontuação, mas COM acentos.
     * O vocabulário do modelo é acentuado ("localização", "não"); a forma sem acento
     * seria descartada da gramática sem erro, só com um aviso no log.
     */
    fun grammarForm(value: String) =
        value
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    fun words(value: String) = normalize(value).split(' ').filter(String::isNotBlank)

    fun similarity(a: String, b: String): Double {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        var row = IntArray(y.length + 1) { it }
        x.forEachIndexed { i, c ->
            val next = IntArray(y.length + 1)
            next[0] = i + 1
            y.forEachIndexed { j, d ->
                next[j + 1] = minOf(next[j] + 1, row[j + 1] + 1, row[j] + if (c == d) 0 else 1)
            }
            row = next
        }
        return 1.0 - row.last().toDouble() / max(x.length, y.length)
    }

    /** Maior similaridade entre a frase e qualquer trecho contíguo de mesmo tamanho. */
    fun bestWindow(expected: String, heard: String): Double {
        val target = words(expected)
        val got = words(heard)
        if (got.isEmpty()) return 0.0
        if (got.size <= target.size) return similarity(expected, heard)
        return (0..got.size - target.size).maxOf {
            similarity(target.joinToString(" "), got.subList(it, it + target.size).joinToString(" "))
        }
    }

    /** O texto é a frase inteira, sem nada a mais: a forma que o teste devolve no acerto. */
    fun recognized(expected: String, heard: String) =
        words(expected).let { it.size >= 3 && it == words(heard) }

    fun accepts(expected: String, actual: String, threshold: Double): Boolean {
        val wanted = words(expected)
        val heard = words(actual)
        if (wanted.size < 3 || heard.size != wanted.size || "unk" in heard) return false
        // Evita que a semelhança da cauda aceite outro início, como "vou buscar...".
        if (similarity(wanted.first(), heard.first()) < .9) return false
        return similarity(expected, actual) >= threshold
    }

    /**
     * Exigência pessoal da segunda leitura, em fração: o meio entre a pior positiva e a
     * negativa mais parecida, nunca abaixo de [floor]. Null se elas não se separam.
     */
    fun personalVerify(positives: List<Int>, negatives: List<Int>, floor: Double): Double? {
        if (positives.size < 3 || negatives.isEmpty()) return null
        val worst = positives.min()
        val closest = negatives.max()
        if (worst <= closest) return null
        return maxOf(floor, (worst + closest) / 200.0)
    }

    fun calibratedThreshold(
        positives: List<String>,
        negatives: List<String>,
        expected: String,
    ): Double? {
        if (positives.size < 3 || negatives.isEmpty()) return null
        val floor = positives.minOf { similarity(expected, it) }
        val ceiling = negatives.maxOf { similarity(expected, it) }
        if (floor <= ceiling || floor < .9) return null
        return (floor + ceiling) / 2
    }
}
