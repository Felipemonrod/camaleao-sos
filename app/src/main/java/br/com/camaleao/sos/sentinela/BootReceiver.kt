package br.com.camaleao.sos.sentinela

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.camaleao.sos.sos.Store

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val store = Store(context)
        val rearm =
            store.flag("wanted_armed") || store.flag("was_armed") || store.flag("rearm_reminder")
        store.flag("was_armed", false)
        store.flag("unexpected_stop", true)
        if (rearm) ServiceNotice.reminder(context)
    }
}
