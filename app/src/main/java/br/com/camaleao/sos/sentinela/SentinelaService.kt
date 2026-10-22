package br.com.camaleao.sos.sentinela

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.*
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import br.com.camaleao.sos.LiveState
import br.com.camaleao.sos.domain.PhraseMatcher
import br.com.camaleao.sos.sos.Protocol
import br.com.camaleao.sos.sos.Store
import java.util.concurrent.atomic.AtomicBoolean

class SentinelaService : Service() {
    private val running = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private lateinit var store: Store
    private var recordingCallback: AudioManager.AudioRecordingCallback? = null

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            store.flag("wanted_armed", false)
            stopSelf()
            return START_NOT_STICKY
        }
        if (running.get()) return START_NOT_STICKY
        val test = intent?.getBooleanExtra("test", true) != false
        if (!test && (!store.flag("calibrated") || !store.flag("share_consent"))) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val n = ServiceNotice.notice(this, "Preparando escuta local…", true)
            if (Build.VERSION.SDK_INT >= 30)
                startForeground(
                    ServiceNotice.ACTIVE,
                    n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                )
            else startForeground(ServiceNotice.ACTIVE, n)
        } catch (_: RuntimeException) {
            LiveState.status = "Desarmada — Android bloqueou o início; confira permissões."
            LiveState.preparing = false
            LiveState.changed()
            stopSelf()
            return START_NOT_STICKY
        }
        running.set(true)
        LiveState.preparing = true
        LiveState.lastHeard = ""
        LiveState.testMatches = 0
        LiveState.changed()
        Thread({ capture(test) }, "SentinelaAudio").start()
        return START_NOT_STICKY
    }

    private fun capture(test: Boolean) {
        var engine: VoskPipeline? = null
        var audio: AudioRecord? = null
        val wake =
            getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Camaleao:Audio")
                .apply { setReferenceCounted(false) }
        try {
            engine =
                VoskPipeline(
                    ModelFiles.prepare(this),
                    store.phrase(),
                    store.threshold(),
                    test,
                    onPhrase = { text ->
                        LiveState.lastHeard = text
                        LiveState.sampleSerial++
                        if (PhraseMatcher.accepts(store.phrase(), text, store.threshold()))
                            LiveState.testMatches++
                        LiveState.changed()
                    },
                    onAlert = { Protocol.create(this, "voice", false) },
                    onPhase = {
                        LiveState.voicePhase = it
                        LiveState.changed()
                    },
                )
            if (!running.get()) return
            if (
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                    PackageManager.PERMISSION_GRANTED
            )
                throw SecurityException()
            val minimum =
                AudioRecord.getMinBufferSize(
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
            check(minimum > 0)
            audio =
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minimum, 8192),
                )
            recorder = audio
            check(audio.state == AudioRecord.STATE_INITIALIZED)
            if (!running.get()) return
            audio.startRecording()
            check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING)
            if (Build.VERSION.SDK_INT >= 29) {
                val callback =
                    object : AudioManager.AudioRecordingCallback() {
                        override fun onRecordingConfigChanged(
                            configs: MutableList<AudioRecordingConfiguration>
                        ) {
                            if (
                                configs.any {
                                    it.clientAudioSessionId == recorder?.audioSessionId &&
                                        it.isClientSilenced
                                }
                            ) {
                                store.flag("unexpected_stop", true)
                                LiveState.status =
                                    "Desarmada — microfone interrompido por outro uso"
                                stopSelf()
                            }
                        }
                    }
                recordingCallback = callback
                getSystemService(AudioManager::class.java)
                    .registerAudioRecordingCallback(callback, LiveState.main)
            }
            wake.acquire(60_000)
            LiveState.active = true
            LiveState.preparing = false
            LiveState.voicePhase = "WAITING_KEYWORD"
            LiveState.status =
                if (test) "Teste de frase — nenhum Protocolo SOS" else "Armada — microfone ativo"
            store.flag("was_armed", true)
            store.flag("wanted_armed", true)
            store.flag("unexpected_stop", false)
            ServiceNotice.manager(this)
                .notify(ServiceNotice.ACTIVE, ServiceNotice.notice(this, LiveState.status, true))
            LiveState.changed()
            val pcm = ShortArray(1600)
            var lastBeat = 0L
            while (running.get()) {
                if (!wake.isHeld) wake.acquire(60_000)
                val count = audio.read(pcm, 0, pcm.size)
                if (!running.get()) break
                check(count > 0) { "Captura interrompida" }
                if (LiveState.cancelCommand) {
                    engine.cancel()
                    LiveState.cancelCommand = false
                }
                engine.accept(pcm, count, SystemClock.elapsedRealtime())
                val now = System.currentTimeMillis()
                if (now - lastBeat > 5000) {
                    lastBeat = now
                    LiveState.heartbeat = now
                    store.put("heartbeat", now.toString())
                    LiveState.changed()
                }
            }
            pcm.fill(0)
        } catch (_: Exception) {
            store.flag("unexpected_stop", true)
            LiveState.status =
                "Desarmada — falha de áudio/modelo; revise permissões e reinicie a sessão"
        } catch (_: LinkageError) {
            store.flag("unexpected_stop", true)
            LiveState.status = "Desarmada — biblioteca de voz incompatível com este aparelho"
        } finally {
            running.set(false)
            runCatching { audio?.stop() }
            audio?.release()
            recorder = null
            runCatching { engine?.close() }
            if (wake.isHeld) wake.release()
            LiveState.active = false
            LiveState.preparing = false
            store.flag("was_armed", false)
            LiveState.changed()
            LiveState.main.post { stopSelf() }
        }
    }

    override fun onDestroy() {
        running.set(false)
        runCatching { recorder?.stop() }
        recordingCallback?.let {
            getSystemService(AudioManager::class.java).unregisterAudioRecordingCallback(it)
        }
        LiveState.active = false
        LiveState.preparing = false
        if (!store.flag("unexpected_stop")) LiveState.status = "Desarmada — arme com o app aberto"
        store.flag("was_armed", false)
        LiveState.changed()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
