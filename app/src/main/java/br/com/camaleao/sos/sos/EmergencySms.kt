package br.com.camaleao.sos.sos

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import br.com.camaleao.sos.LiveState
import java.time.Instant
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * SMS automático para a Rede de Apoio. O registro só passa a "aceito pela operadora"
 * quando o Android devolve RESULT_OK no sentIntent; entrega ao destinatário não é afirmada.
 */
object EmergencySms {
    private const val LOG = "sms_log"

    fun allowed(context: Context, store: Store) =
        store.flag("share_consent") &&
            context.checkSelfPermission(Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED

    fun contacts(store: Store): List<JSONObject> =
        JSONArray(store.get("contacts", "[]")).let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }
        }

    /** Envia [text] a todos os participantes. Devolve quantos envios foram entregues ao Android. */
    fun send(context: Context, store: Store, reason: String, text: String): Int {
        if (!allowed(context, store)) return 0
        val sms =
            if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
        var started = 0
        for (c in contacts(store)) {
            val id = UUID.randomUUID().toString()
            log(store, id, reason, c.getString("name"), "PENDENTE")
            try {
                val parts = sms.divideMessage(text)
                val sent =
                    ArrayList(
                        parts.indices.map {
                            PendingIntent.getBroadcast(
                                context,
                                (id.hashCode() * 31) + it,
                                Intent(context, SmsResultReceiver::class.java).putExtra("id", id),
                                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
                            )
                        }
                    )
                sms.sendMultipartTextMessage(c.getString("phone"), null, parts, sent, null)
                started++
            } catch (e: Exception) {
                update(store, id, "FALHOU: ${e.javaClass.simpleName}")
            }
        }
        LiveState.changed()
        return started
    }

    fun log(store: Store): List<JSONObject> =
        JSONArray(store.get(LOG, "[]")).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }

    @Synchronized
    private fun log(store: Store, id: String, reason: String, name: String, status: String) {
        val a = JSONArray(store.get(LOG, "[]"))
        a.put(
            JSONObject()
                .put("id", id)
                .put("at", Instant.now().toString())
                .put("reason", reason)
                .put("to", name)
                .put("status", status)
        )
        // Mantém só os 50 mais recentes.
        val keep = JSONArray()
        for (i in maxOf(0, a.length() - 50) until a.length()) keep.put(a.get(i))
        store.put(LOG, keep.toString())
    }

    @Synchronized
    fun update(store: Store, id: String, status: String) {
        val a = JSONArray(store.get(LOG, "[]"))
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            // Mensagem longa tem várias partes: uma falha prevalece sobre os OKs.
            if (o.getString("id") == id && !o.getString("status").startsWith("FALHOU"))
                o.put("status", status)
        }
        store.put(LOG, a.toString())
        LiveState.changed()
    }

    @Synchronized
    fun clear(store: Store) = store.put(LOG, "[]")
}

class SmsResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        EmergencySms.update(
            Store(context),
            id,
            if (resultCode == android.app.Activity.RESULT_OK) "ACEITO PELA OPERADORA"
            else "FALHOU: código $resultCode",
        )
    }
}
