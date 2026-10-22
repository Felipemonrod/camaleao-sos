package br.com.camaleao.sos

import android.location.Location
import android.os.Handler
import android.os.Looper

object LiveState {
    @Volatile var active = false
    @Volatile var preparing = false
    @Volatile var status = "Desarmada — arme com o app aberto"
    @Volatile var voicePhase = "WAITING_KEYWORD"
    @Volatile var heartbeat = 0L
    @Volatile var lastHeard = ""
    @Volatile var testMatches = 0
    @Volatile var sampleSerial = 0
    @Volatile var cancelCommand = false
    @Volatile var recording = false
    @Volatile var recordRequest = false
    @Volatile var stopRecording = false
    @Volatile var lastCommand = ""
    /** Tipos com que o Foreground Service subiu (ServiceInfo.FOREGROUND_SERVICE_TYPE_*). */
    @Volatile var foregroundTypes = 0
    @Volatile var location: Location? = null
    val main = Handler(Looper.getMainLooper())
    val listeners = linkedSetOf<() -> Unit>()

    fun changed() {
        main.post { listeners.toList().forEach { it() } }
    }
}
