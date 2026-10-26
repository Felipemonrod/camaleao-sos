package br.com.camaleao.sos.sos

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
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
        if (!allowed(context, store)) {
            // Sem isso o alerta some sem rastro: no Xiaomi a permissão costuma estar restrita.
            if (contacts(store).isNotEmpty())
                note(
                    store,
                    reason,
                    "Rede de Apoio",
                    if (!store.flag("share_consent")) "FALHOU: comunicação não autorizada"
                    else if (
                        !context.packageManager.hasSystemFeature(
                            android.content.pm.PackageManager.FEATURE_TELEPHONY
                        )
                    )
                        "FALHOU: este aparelho não tem telefonia"
                    else if (Compatibility.sideloaded(context))
                        "FALHOU: permissão de SMS bloqueada — veja Compatibilidade"
                    else "FALHOU: sem permissão de SMS (veja Configurações do app)",
                )
            return 0
        }
        val sms = manager(context)
        var started = 0
        val waiting = mutableListOf<String>()
        for (c in contacts(store)) {
            val id = UUID.randomUUID().toString()
            log(store, id, reason, c.getString("name"), PENDING)
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
                waiting += id
                started++
            } catch (e: Exception) {
                update(store, id, "FALHOU: ${e.javaClass.simpleName}")
            }
        }
        // Há aparelho que aceita a chamada, contabiliza a permissão e engole a mensagem
        // sem devolver resultado nenhum. Sem este prazo o registro fica PENDENTE para
        // sempre, e PENDENTE eterno se parece com sucesso (RF-57).
        if (waiting.isNotEmpty())
            LiveState.main.postDelayed({ expirePending(store, waiting) }, NO_RESULT_MS)
        LiveState.changed()
        return started
    }

    /** Prazo para o Android responder ao envio. */
    const val NO_RESULT_MS = 30_000L

    const val NO_RESULT =
        "FALHOU: sem resposta do sistema em 30 s — o aparelho pode estar bloqueando o " +
            "envio automático. Use “Revisar SMS para um participante”."

    /** O que continuou PENDENTE virou falha: silêncio não é entrega. */
    @Synchronized
    fun expirePending(store: Store, ids: List<String>) {
        val a = JSONArray(store.get(LOG, "[]"))
        var changed = false
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            if (o.getString("id") in ids && o.getString("status") == PENDING) {
                o.put("status", NO_RESULT)
                changed = true
            }
        }
        if (changed) {
            store.put(LOG, a.toString())
            LiveState.changed()
        }
    }

    const val PENDING = "PENDENTE"

    /** Registro avulso, para ligações e falhas que não chegam a virar SMS. */
    fun note(store: Store, reason: String, to: String, status: String) {
        log(store, UUID.randomUUID().toString(), reason, to, status)
        LiveState.changed()
    }

    /**
     * Gerenciador de SMS do chip certo. Em celular com dois chips e "perguntar sempre"
     * não existe chip padrão de SMS, e o envio em segundo plano falha: cai para o chip
     * padrão de voz ou de dados. Os três ids vêm sem permissão de telefone.
     */
    private fun manager(context: Context): SmsManager {
        val base =
            if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
        val sub =
            listOf(
                    SubscriptionManager.getDefaultSmsSubscriptionId(),
                    SubscriptionManager.getDefaultVoiceSubscriptionId(),
                    SubscriptionManager.getDefaultDataSubscriptionId(),
                )
                .firstOrNull { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID } ?: return base
        return runCatching {
                if (Build.VERSION.SDK_INT >= 31) base.createForSubscriptionId(sub)
                else @Suppress("DEPRECATION") SmsManager.getSmsManagerForSubscriptionId(sub)
            }
            .getOrDefault(base)
    }

    /** Motivo legível do código devolvido pelo Android no envio (SmsManager.RESULT_ERROR_*). */
    fun reason(code: Int): String =
        when (code) {
            SmsManager.RESULT_ERROR_GENERIC_FAILURE ->
                "recusado pelo sistema ou pela operadora (no Xiaomi: Outras permissões → Enviar SMS)"
            SmsManager.RESULT_ERROR_RADIO_OFF -> "rádio desligado (modo avião?)"
            SmsManager.RESULT_ERROR_NULL_PDU -> "mensagem inválida"
            SmsManager.RESULT_ERROR_NO_SERVICE -> "sem sinal da operadora"
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "limite de envio do sistema"
            SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED,
            SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> "número curto bloqueado"
            // Códigos 100+ vêm do rádio, abaixo do Android. Medidos num Redmi Note 12 que
            // recusava todo SMS enquanto ligação e dados funcionavam (docs/09, 25/10).
            SmsManager.RESULT_RIL_MODEM_ERR,
            SmsManager.RESULT_RIL_INVALID_MODEM_STATE ->
                "o modem do aparelho recusou o envio — reiniciar o celular costuma resolver"
            SmsManager.RESULT_RIL_INVALID_SMSC_ADDRESS ->
                "central de mensagens (SMSC) inválida — confira nas configurações do chip"
            SmsManager.RESULT_RIL_NETWORK_REJECT -> "a operadora recusou o envio"
            SmsManager.RESULT_RIL_RADIO_NOT_AVAILABLE -> "rádio indisponível"
            SmsManager.RESULT_RIL_REQUEST_RATE_LIMITED -> "limite de envio do rádio"
            SmsManager.RESULT_RIL_SYSTEM_ERR -> "erro de sistema no rádio"
            else -> "código $code"
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
            else "FALHOU: ${EmergencySms.reason(resultCode)}",
        )
    }
}
