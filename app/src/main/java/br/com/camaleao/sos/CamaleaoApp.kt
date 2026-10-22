package br.com.camaleao.sos

import android.app.Application
import br.com.camaleao.sos.sos.Protocol
import br.com.camaleao.sos.sos.Store

class CamaleaoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val store = Store(this)
        if (store.flag("was_armed")) {
            store.flag("unexpected_stop", true)
            store.flag("was_armed", false)
        }
        Protocol.pump(this)
        if (
            store.list().any {
                it.getString("state") != "ACKNOWLEDGED" && !it.optBoolean("cancelled")
            }
        )
            Protocol.schedule(this)
    }
}
