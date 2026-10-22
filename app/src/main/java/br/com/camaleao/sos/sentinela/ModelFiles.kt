package br.com.camaleao.sos.sentinela

import android.content.Context
import java.io.File

object ModelFiles {
    @Synchronized
    fun prepare(c: Context): File {
        val root = File(c.filesDir, "voice-model-v1")
        val done = File(root, ".complete")
        if (!done.exists()) {
            fun copy(path: String, out: File) {
                val children = c.assets.list(path).orEmpty()
                if (children.isEmpty()) {
                    out.parentFile?.mkdirs()
                    c.assets.open(path).use { i -> out.outputStream().use { i.copyTo(it) } }
                } else {
                    out.mkdirs()
                    children.forEach { copy("$path/$it", File(out, it)) }
                }
            }
            copy("models/vosk-pt", root)
            done.writeText("small-pt-0.3")
        }
        return root
    }
}
