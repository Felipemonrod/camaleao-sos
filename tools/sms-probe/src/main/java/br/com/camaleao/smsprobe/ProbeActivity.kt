package br.com.camaleao.smsprobe

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import android.widget.TextView

/**
 * Sonda de SMS. Faz UMA coisa: manda um SMS por `SmsManager`, como app comum, e escreve
 * no log o que o Android respondeu. Serve para separar "este aparelho deixa um app que
 * não é o de mensagens enviar SMS?" de qualquer lógica do Camaleão.
 *
 *   adb install -r tools/sms-probe/build/outputs/apk/debug/sms-probe-debug.apk
 *   adb shell pm grant br.com.camaleao.smsprobe android.permission.SEND_SMS
 *   adb shell am start -n br.com.camaleao.smsprobe/.ProbeActivity --es to "+5564999999999"
 *   adb logcat -s SmsProbe:I
 *
 * O texto é fixo e identificado como teste. Nada aqui lê contatos nem guarda estado.
 */
class ProbeActivity : Activity() {
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        val view = TextView(this).apply { textSize = 16f; setPadding(40, 80, 40, 40) }
        setContentView(view)

        val to = intent.getStringExtra("to")
        if (to.isNullOrBlank()) {
            say(view, "Faltou --es to \"+55...\"")
            return
        }
        val text = intent.getStringExtra("text") ?: "oi"

        val granted =
            checkSelfPermission(android.Manifest.permission.SEND_SMS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        Log.i(TAG, "permissao SEND_SMS concedida=$granted")
        if (!granted) {
            say(view, "Sem permissao SEND_SMS")
            return
        }

        val sms =
            if (android.os.Build.VERSION.SDK_INT >= 31) getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()

        fun pending(tag: String) =
            PendingIntent.getBroadcast(
                this,
                tag.hashCode(),
                Intent(this, SentReceiver::class.java).putExtra("qual", tag),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        // Dois caminhos: o simples e o mesmo que o app usa. Se um sair e o outro não,
        // a diferença está no método, não no aparelho.
        try {
            sms.sendTextMessage(to, null, text, pending("simples"), null)
            Log.i(TAG, "simples: sendTextMessage chamado para ${to.takeLast(4)}")
        } catch (e: Exception) {
            Log.i(TAG, "simples: lancou ${e.javaClass.simpleName}: ${e.message}")
        }
        try {
            val parts = sms.divideMessage(text)
            val intents = ArrayList(parts.indices.map { pending("multi$it") })
            sms.sendMultipartTextMessage(to, null, parts, intents, null)
            Log.i(TAG, "multi: sendMultipartTextMessage chamado, ${parts.size} parte(s)")
        } catch (e: Exception) {
            Log.i(TAG, "multi: lancou ${e.javaClass.simpleName}: ${e.message}")
        }
        say(view, "Dois envios entregues ao Android. Veja o log SmsProbe.")
    }

    private fun say(view: TextView, message: String) {
        view.text = message
        Log.i(TAG, message)
    }

    companion object {
        const val TAG = "SmsProbe"
    }
}

/** Resultado que o Android devolve para o envio. É o dado que falta no diagnóstico. */
class SentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val motivo =
            when (resultCode) {
                Activity.RESULT_OK -> "RESULT_OK (aceito pelo sistema)"
                SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "GENERIC_FAILURE"
                SmsManager.RESULT_ERROR_RADIO_OFF -> "RADIO_OFF"
                SmsManager.RESULT_ERROR_NULL_PDU -> "NULL_PDU"
                SmsManager.RESULT_ERROR_NO_SERVICE -> "NO_SERVICE"
                SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "LIMIT_EXCEEDED"
                else -> "codigo $resultCode"
            }
        Log.i(ProbeActivity.TAG, "RESULTADO ${intent.getStringExtra("qual")}: $motivo")
    }
}
