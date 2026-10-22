package br.com.camaleao.sos

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.camaleao.sos.sentinela.*
import br.com.camaleao.sos.sos.*
import br.com.camaleao.sos.ui.MainActivity
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Testes que apagam a tela. Com bloqueio por senha, o aparelho fica bloqueado depois
 * deles, então rodam numa instrumentação separada, depois de [CompleteFlowTest].
 */
@RunWith(AndroidJUnit4::class)
class ScreenOffTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext

    /**
     * Sentinela armada de verdade (microfone real, tipo microphone|location), tela
     * apagada: a posição continua acessível e a gravação usa a mesma captura.
     * Sem contatos cadastrados: nenhum SMS sai.
     */
    @Test
    fun armedSentinelaKeepsLocationAndRecordsWithScreenOff() {
        val store = Store(context)
        store.put("contacts", "[]")
        store.flag("share_consent", true)
        store.flag("location_consent", true)
        store.flag("calibrated", true)
        store.put("cmd_gravacao_on", "true")
        fun shell(c: String) =
            inst.uiAutomation.executeShellCommand(c).use { d ->
                java.io.FileInputStream(d.fileDescriptor).use { String(it.readBytes()) }
            }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            context.startForegroundService(Intent(context, SentinelaService::class.java).putExtra("test", false))
            var deadline = SystemClock.elapsedRealtime() + 30_000
            while (!LiveState.active && SystemClock.elapsedRealtime() < deadline) Thread.sleep(200)
            assertTrue("Sentinela não armou: ${LiveState.status}", LiveState.active)
            assertTrue(
                "Serviço sem tipo location",
                LiveState.foregroundTypes and android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION != 0,
            )
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            shell("input keyevent KEYCODE_SLEEP")
            Thread.sleep(4000)
            val got = java.util.concurrent.atomic.AtomicReference<br.com.camaleao.sos.domain.Position?>()
            val latch = java.util.concurrent.CountDownLatch(1)
            LocationProbe.request(context, store, 15_000) { got.set(it); latch.countDown() }
            latch.await(20, java.util.concurrent.TimeUnit.SECONDS)
            assertNotNull("Sem posição com tela apagada e Sentinela armada", got.get())
            LiveState.recordRequest = true
            Thread.sleep(3500)
            LiveState.stopRecording = true
            deadline = SystemClock.elapsedRealtime() + 5000
            while (LiveState.recording && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
            val e = EvidenceRecorder.list(store).first()
            assertEquals("CONCLUIDA", e.getString("state"))
            assertTrue("Gravação curta demais: ${e.optLong("duration_s")}", e.optLong("duration_s") >= 2)
            val wav = java.io.ByteArrayOutputStream()
            EvidenceRecorder.exportWav(context, store, e.getString("id"), wav)
            assertTrue(wav.size() > 44 + 2 * 16000 * 2)
            assertTrue("Sentinela caiu com tela apagada", LiveState.active)
            android.util.Log.i("CamaleaoTest", "tela apagada: posição ±${got.get()!!.accuracy.toInt()} m, gravação ${e.optLong("duration_s")} s")
        } finally {
            context.stopService(Intent(context, SentinelaService::class.java))
            shell("input keyevent KEYCODE_WAKEUP")
            scenario.close()
        }
    }

    /**
     * RNF-06/11/12: Sentinela armada com tela apagada por -e minutos N. Registra a cada
     * 30 s se continua ativa, a idade do sinal de vida, PSS e CPU do processo.
     * Só roda quando pedido explicitamente; sem contatos, nenhum SMS sai.
     */
    @Test
    fun longArmedRunWithScreenOff() {
        val minutes = InstrumentationRegistry.getArguments().getString("minutes")?.toIntOrNull()
        org.junit.Assume.assumeTrue(minutes != null)
        val store = Store(context)
        store.put("contacts", "[]")
        store.flag("share_consent", true)
        store.flag("location_consent", true)
        store.flag("calibrated", true)
        fun shell(c: String) =
            inst.uiAutomation.executeShellCommand(c).use { d ->
                java.io.FileInputStream(d.fileDescriptor).use { String(it.readBytes()) }
            }
        fun cpuTicks() =
            File("/proc/self/stat").readText().substringAfterLast(')').trim().split(' ').let {
                it[11].toLong() + it[12].toLong()
            }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var drops = 0
        try {
            context.startForegroundService(Intent(context, SentinelaService::class.java).putExtra("test", false))
            val armDeadline = SystemClock.elapsedRealtime() + 30_000
            while (!LiveState.active && SystemClock.elapsedRealtime() < armDeadline) Thread.sleep(200)
            assertTrue("Sentinela não armou", LiveState.active)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            shell("input keyevent KEYCODE_SLEEP")
            val start = SystemClock.elapsedRealtime()
            var ticks = cpuTicks()
            var last = start
            while (SystemClock.elapsedRealtime() - start < minutes!! * 60_000L) {
                Thread.sleep(30_000)
                val now = SystemClock.elapsedRealtime()
                val t = cpuTicks()
                val cpu = 100.0 * (t - ticks) / 100.0 / ((now - last) / 1000.0)
                ticks = t
                last = now
                val age = (System.currentTimeMillis() - LiveState.heartbeat) / 1000
                if (!LiveState.active || age > 15) drops++
                android.util.Log.i(
                    "CamaleaoLongo",
                    "t=${(now - start) / 1000}s ativa=${LiveState.active} sinal=${age}s pss=${android.os.Debug.getPss() / 1024}MB cpu=${"%.1f".format(java.util.Locale.ROOT, cpu)}%",
                )
            }
            assertEquals("Amostras com Sentinela inativa ou sem sinal de vida", 0, drops)
        } finally {
            context.stopService(Intent(context, SentinelaService::class.java))
            shell("input keyevent KEYCODE_WAKEUP")
            scenario.close()
        }
    }

}
