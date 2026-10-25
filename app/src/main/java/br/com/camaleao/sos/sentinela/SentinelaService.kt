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
import br.com.camaleao.sos.domain.VoiceAction
import br.com.camaleao.sos.sos.Actions
import br.com.camaleao.sos.sos.Store
import java.util.concurrent.atomic.AtomicBoolean

class SentinelaService : Service() {
    companion object {
        // Sessão dona do LiveState. A thread de uma sessão encerrada termina depois do
        // onDestroy e não pode desarmar a sessão que já começou (teste → armar).
        @Volatile private var current: Any? = null
    }

    private val running = AtomicBoolean(false)
    private val session = Any()
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
            // Localização só entra no tipo se autorizada: o serviço é criado com o app
            // visível, condição exigida pelo Android para tipos com permissão while-in-use.
            val types =
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    if (!test && LocationProbe.allowed(this, store))
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    else 0
            if (Build.VERSION.SDK_INT >= 30) startForeground(ServiceNotice.ACTIVE, n, types)
            else startForeground(ServiceNotice.ACTIVE, n)
            LiveState.foregroundTypes = types
        } catch (_: RuntimeException) {
            LiveState.status = "Desarmada — Android bloqueou o início; confira permissões."
            LiveState.preparing = false
            LiveState.changed()
            stopSelf()
            return START_NOT_STICKY
        }
        running.set(true)
        current = session
        LiveState.preparing = true
        LiveState.lastHeard = ""
        LiveState.testMatches = 0
        LiveState.changed()
        Thread({ capture(test) }, "SentinelaAudio").start()
        return START_NOT_STICKY
    }

    private fun capture(test: Boolean) {
        var engine: VoskPipeline? = null
        val evidence = EvidenceRecorder(this, store)
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
                    Actions.commands(store),
                    onPhrase = { text ->
                        LiveState.lastHeard = text
                        LiveState.sampleSerial++
                        if (PhraseMatcher.accepts(store.phrase(), text, store.threshold()))
                            LiveState.testMatches++
                        LiveState.changed()
                    },
                    onCommand = { action ->
                        LiveState.lastCommand = action.label
                        when (action) {
                            VoiceAction.ALERT -> LiveState.main.post { Actions.alert(this, "voice") }
                            VoiceAction.LOCATION -> LiveState.main.post { Actions.shareLocation(this) }
                            VoiceAction.CALL -> LiveState.main.post { Actions.call(this) }
                            VoiceAction.RECORD ->
                                evidence.start(
                                    "voice",
                                    Actions.recordMinutes(store) * 60_000L,
                                    SystemClock.elapsedRealtime(),
                                    engine?.snapshot(),
                                )
                        }
                    },
                    onPhase = {
                        LiveState.voicePhase = it
                        LiveState.changed()
                    },
                    diagnostics =
                        (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0,
                    verify = store.verify(),
                    phraseFires = store.flag("phrase_fires"),
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
                    // 4 s: a segunda leitura do estágio 1 bloqueia este laço por ~1–2 s e o
                    // comando dito logo após a frase não pode se perder nesse intervalo.
                    maxOf(minimum, 16000 * 2 * 4),
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
                                !LiveState.callInProgress &&
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
            if (!test) LiveState.main.post { LocationProbe.track(this, store) }
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
                val elapsed = SystemClock.elapsedRealtime()
                // O prazo da gravação corre mesmo sem captura (RF-71).
                evidence.tick(elapsed)
                // Durante a ligação em linha aberta o microfone é da chamada: espera voltar.
                if (count <= 0 && LiveState.callInProgress) {
                    SystemClock.sleep(200)
                    continue
                }
                check(count > 0) { "Captura interrompida" }
                if (LiveState.cancelCommand) {
                    engine.cancel()
                    LiveState.cancelCommand = false
                }
                if (LiveState.recordRequest) {
                    LiveState.recordRequest = false
                    if (!test)
                        evidence.start(
                            "manual",
                            Actions.recordMinutes(store) * 60_000L,
                            elapsed,
                            engine?.snapshot(),
                        )
                }
                if (LiveState.stopRecording) {
                    LiveState.stopRecording = false
                    evidence.stop("parada pela usuária")
                }
                // O mesmo bloco vai para a gravação e para o reconhecimento (RF-29).
                evidence.accept(pcm, count, elapsed)
                engine.accept(pcm, count, elapsed)
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
            runCatching { evidence.stop("Sentinela desarmada") }
            runCatching { audio?.stop() }
            audio?.release()
            recorder = null
            runCatching { engine?.close() }
            if (wake.isHeld) wake.release()
            if (current === session) {
                current = null
                LiveState.main.post { LocationProbe.untrack(this) }
                LiveState.active = false
                LiveState.preparing = false
                store.flag("was_armed", false)
                LiveState.changed()
                LiveState.main.post { if (current == null) stopSelf() }
            }
        }
    }

    override fun onDestroy() {
        running.set(false)
        br.com.camaleao.sos.sos.OpenLineCall.stop()
        LocationProbe.untrack(this)
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
