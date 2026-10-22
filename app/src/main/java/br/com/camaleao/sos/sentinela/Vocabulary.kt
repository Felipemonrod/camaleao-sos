package br.com.camaleao.sos.sentinela

import android.content.Context
import br.com.camaleao.sos.domain.PhraseMatcher
import com.sun.jna.Native
import com.sun.jna.Pointer
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model

/**
 * Confere se cada palavra existe no vocabulário do modelo. Palavra ausente é
 * descartada da gramática pelo Vosk sem erro — a frase nunca seria detectada.
 * A API Java não expõe essa busca; a função nativa `vosk_model_find_word` sim.
 */
object Vocabulary {
    private object Native0 {
        init {
            Native.register(Native0::class.java, "vosk")
        }

        @JvmStatic external fun vosk_model_find_word(model: Pointer, word: String): Int
    }

    /** Palavras ausentes do modelo, na forma digitada. Carrega o modelo: chamar fora da UI. */
    fun missing(context: Context, texts: List<String>): List<String> {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        Model(ModelFiles.prepare(context).absolutePath).use { model ->
            return texts
                .flatMap { PhraseMatcher.grammarForm(it).split(' ') }
                .filter { it.isNotBlank() }
                .distinct()
                .filter { Native0.vosk_model_find_word(model.pointer, it) < 0 }
        }
    }
}
