package br.com.camaleao.sos.security

import android.content.Context
import br.com.camaleao.sos.sos.Store
import org.json.JSONObject

/** Preserva dados do primeiro protótipo antes de remover suas preferências em claro. */
object LegacyMigration {
    fun run(context: Context, store: Store, preferencesName: String = "camaleao") {
        val legacy = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val old = legacy.all
        if (old.isEmpty()) return
        // O arquivo antigo só é limpo depois da cópia cifrada e sua leitura verificável.
        val archive = JSONObject(old).toString()
        store.put("legacy_archive", archive)
        check(JSONObject(store.get("legacy_archive")).length() == old.size)
        (old["phrase"] as? String)?.takeIf { it.isNotBlank() }?.let { store.put("phrase", it) }
        (old["contacts"] as? String)?.let { store.put("contacts", it) }
        (old["boot"] as? Boolean)?.let { store.flag("rearm_reminder", it) }
        // Preparar SMS no protótipo anterior não autoriza transmissão ao novo Painel.
        store.flag("share_consent", false)
        store.flag("location_consent", false)
        store.flag("calibrated", false)
        check(legacy.edit().clear().commit())
    }
}
