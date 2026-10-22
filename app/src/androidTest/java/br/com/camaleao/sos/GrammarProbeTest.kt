package br.com.camaleao.sos

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.camaleao.sos.sentinela.ModelFiles
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.vosk.Model
import org.vosk.Recognizer

@RunWith(AndroidJUnit4::class)
class GrammarProbeTest {
    @Test
    fun grammarAndSyntheticPortuguese() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val model = Model(ModelFiles.prepare(inst.targetContext).absolutePath)
        try {
            val grammar = JSONArray(listOf("preciso buscar meu caderno azul", "[unk]")).toString()
            Recognizer(model, 16000f, grammar).use { r ->
                val bytes = inst.context.assets.open("frase.wav").use { it.readBytes() }
                val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                var offset = 12
                while (String(bytes, offset, 4) != "data") offset += 8 + b.getInt(offset + 4)
                val count = b.getInt(offset + 4) / 2
                val pcm = ShortArray(count) { b.getShort(offset + 8 + it * 2) } + ShortArray(20000)
                val texts = mutableListOf<String>()
                for (i in pcm.indices step 1600) {
                    val p = pcm.copyOfRange(i, minOf(i + 1600, pcm.size))
                    if (r.acceptWaveForm(p, p.size)) texts += JSONObject(r.result).optString("text")
                }
                texts += JSONObject(r.finalResult).optString("text")
                assertTrue("Amostra sintética: $texts", texts.any { it.contains("caderno azul") })
                r.reset()
                r.setGrammar(JSONArray(listOf("enviar alerta", "cancelar", "[unk]")).toString())
                assertFalse(r.acceptWaveForm(ShortArray(1600), 1600))
            }
        } finally {
            model.close()
        }
    }
}
