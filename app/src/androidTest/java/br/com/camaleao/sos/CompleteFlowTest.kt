package br.com.camaleao.sos

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.*
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.camaleao.sos.security.*
import br.com.camaleao.sos.sentinela.*
import br.com.camaleao.sos.sos.*
import br.com.camaleao.sos.ui.MainActivity
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompleteFlowTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext

    private fun isolated() = Store(context, "test-${UUID.randomUUID()}.db")

    private fun pcm(name: String): ShortArray {
        val bytes = inst.context.assets.open("$name.wav").use { it.readBytes() }
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        while (String(bytes, pos, 4) != "data") pos += 8 + b.getInt(pos + 4)
        return ShortArray(b.getInt(pos + 4) / 2) { b.getShort(pos + 8 + it * 2) } +
            ShortArray(20000)
    }

    @Test
    fun encryptionAndSignatureCannotBeTampered() {
        val c = DeviceCrypto()
        val sealed = c.seal("conteudo sensivel")
        assertFalse(sealed.contains("conteudo sensivel"))
        assertEquals("conteudo sensivel", c.open(sealed))
        val envelope = JSONObject(sealed)
        val bytes = DeviceCrypto.un64(envelope.getString("data"))
        bytes[0] = (bytes[0].toInt() xor 1).toByte()
        envelope.put("data", DeviceCrypto.b64(bytes))
        assertThrows(Exception::class.java) { c.open(envelope.toString()) }
        val signature = c.sign("original")
        assertTrue(DeviceCrypto.verify(c.publicKey(), "original", signature))
        assertFalse(DeviceCrypto.verify(c.publicKey(), "alterado", signature))
    }

    @Test
    fun cancelledDraftDoesNotBreakSequenceAndCannotCancelQueued() {
        val store = isolated()
        val a = store.create("manual", true)
        assertTrue(store.cancel(a))
        store.enqueue(a)
        assertFalse(store.record(a)!!.has("manifest"))
        val b = store.create("manual", true)
        store.enqueue(b)
        assertEquals(1, store.record(b)!!.getInt("sequence"))
        assertFalse(store.cancel(b))
        store.close()
    }

    @Test
    fun portugueseTwoStageRequiresExplicitCommandAndRejectsNegative() {
        val root = ModelFiles.prepare(context)
        var alerts = 0
        val phases = mutableListOf<String>()
        fun feed(p: VoskPipeline, name: String, offset: Long): Long {
            var now = offset
            val audio = pcm(name)
            for (i in audio.indices step 1600) {
                val part = audio.copyOfRange(i, minOf(i + 1600, audio.size))
                p.accept(part, part.size, now)
                now += 100
            }
            return now
        }
        VoskPipeline(
                root,
                "Preciso buscar meu caderno azul",
                1.0,
                false,
                {},
                { alerts++ },
                { phases += it },
            )
            .use { p ->
                val end = feed(p, "frase", 0)
                assertTrue(phases.contains("COMMAND_WINDOW"))
                assertEquals(0, alerts)
                feed(p, "comando", end)
                assertEquals(1, alerts)
            }
        phases.clear()
        alerts = 0
        VoskPipeline(
                root,
                "Preciso buscar meu caderno azul",
                1.0,
                false,
                {},
                { alerts++ },
                { phases += it },
            )
            .use { p -> feed(p, "negativo", 0) }
        assertFalse(
            "Negativo sintético não deve abrir janela: $phases",
            phases.contains("COMMAND_WINDOW"),
        )
        assertEquals(0, alerts)
    }

    @Test
    fun realHttpsDeliveryPersistsQueueAndVerifiesServerReceipt() {
        val pairing =
            InstrumentationRegistry.getArguments().getString("pairing")
                ?: throw AssertionError("Forneça -e pairing com o código do servidor de teste")
        val store = isolated()
        PanelTransport(store).pair(pairing, "https://10.0.2.2:8443")
        val id = store.create("test", true)
        store.enqueue(id)
        assertEquals("QUEUED", store.record(id)!!.getString("state"))
        val endpoint = store.get("endpoint")
        store.put("endpoint", "https://10.0.2.2:1")
        assertThrows(Exception::class.java) { PanelTransport(store).send(store.record(id)!!) }
        assertEquals("QUEUED", store.record(id)!!.getString("state"))
        store.put("endpoint", endpoint)
        assertThrows(Exception::class.java) { store.acknowledge(id, "{}", "invalida") }
        PanelTransport(store).send(store.record(id)!!)
        val done = store.record(id)!!
        assertEquals("ACKNOWLEDGED", done.getString("state"))
        assertTrue(done.has("receipt_signature"))
        assertFalse(done.getString("manifest").contains("Preciso buscar"))
        val second = store.create("test", true)
        store.enqueue(second)
        PanelTransport(store).send(store.record(second)!!)
        assertEquals("ACKNOWLEDGED", store.record(second)!!.getString("state"))
        store.close()
    }

    @Test
    fun bootReminderNeverStartsMicrophone() {
        val store = Store(context)
        store.flag("rearm_reminder", true)
        inst.runOnMainSync {
            BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        }
        assertFalse(LiveState.active)
        assertFalse(store.flag("was_armed"))
        assertTrue(
            ServiceNotice.manager(context).activeNotifications.any { it.id == ServiceNotice.REMIND }
        )
    }

    @Test
    fun protocolAutomaticallyDeliversAndCancellationStaysLocal() {
        val pairing =
            InstrumentationRegistry.getArguments().getString("pairing")
                ?: throw AssertionError("Forneça o código do Painel local")
        val store = Store(context)
        PanelTransport(store).pair(pairing, "https://10.0.2.2:8443")
        val cancelled = Protocol.create(context, "test", true)!!
        assertTrue(store.cancel(cancelled))
        val sent = Protocol.create(context, "test", true)!!
        val deadline = SystemClock.elapsedRealtime() + 25000
        while (
            store.record(sent)!!.getString("state") != "ACKNOWLEDGED" &&
                SystemClock.elapsedRealtime() < deadline
        ) Thread.sleep(200)
        assertEquals("ACKNOWLEDGED", store.record(sent)!!.getString("state"))
        assertTrue(store.record(cancelled)!!.getBoolean("cancelled"))
        assertFalse(store.record(cancelled)!!.has("manifest"))
    }

    @Test
    fun outboxRetriesAfterEmulatorNetworkReturns() {
        org.junit.Assume.assumeTrue(
            InstrumentationRegistry.getArguments().getString("toggleNetwork") == "true"
        )
        val pairing = InstrumentationRegistry.getArguments().getString("pairing")!!
        val store = Store(context)
        PanelTransport(store).pair(pairing, "https://10.0.2.2:8443")
        fun shell(command: String) {
            inst.uiAutomation.executeShellCommand(command).use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
        }
        var id = ""
        try {
            shell("svc wifi disable")
            shell("svc data disable")
            Thread.sleep(1500)
            id = Protocol.create(context, "test", true)!!
            Thread.sleep(7500)
            assertEquals("QUEUED", store.record(id)!!.getString("state"))
        } finally {
            shell("svc wifi enable")
            shell("svc data enable")
        }
        val deadline = SystemClock.elapsedRealtime() + 45000
        while (
            store.record(id)!!.getString("state") != "ACKNOWLEDGED" &&
                SystemClock.elapsedRealtime() < deadline
        ) Thread.sleep(250)
        assertEquals("ACKNOWLEDGED", store.record(id)!!.getString("state"))
    }

    @Test
    fun wrongPinnedCertificateCannotPair() {
        val code = InstrumentationRegistry.getArguments().getString("pairing")!!
        val wrong =
            JSONObject(String(DeviceCrypto.un64(code))).put("pin", "0".repeat(64)).toString()
        assertThrows(Exception::class.java) {
            PanelTransport(isolated())
                .pair(DeviceCrypto.b64(wrong.toByteArray()), "https://10.0.2.2:8443")
        }
    }

    @Test
    fun legacyPreferencesArePreservedEncryptedBeforeCleanup() {
        val name = "legacy-test-${UUID.randomUUID()}"
        val prefs = context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE)
        prefs
            .edit()
            .putString("phrase", "frase privada de migracao")
            .putString("contacts", "[]")
            .putBoolean("share", true)
            .commit()
        val store = isolated()
        LegacyMigration.run(context, store, name)
        assertTrue(prefs.all.isEmpty())
        assertEquals("frase privada de migracao", store.phrase())
        assertTrue(store.get("legacy_archive").contains("frase privada de migracao"))
        assertFalse(store.flag("share_consent"))
        assertFalse(store.flag("calibrated"))
    }

    private fun find(v: View, value: String): TextView? {
        if (v is TextView && v.text.toString() == value) return v
        if (v is ViewGroup)
            for (i in 0 until v.childCount) find(v.getChildAt(i), value)?.let {
                return it
            }
        return null
    }

    private fun click(s: ActivityScenario<MainActivity>, value: String) {
        s.onActivity { a ->
            val v = find(a.window.decorView, value)
            assertNotNull(value, v)
            v!!.performClick()
        }
        inst.waitForIdleSync()
    }

    private fun screenshot(s: ActivityScenario<MainActivity>, name: String) {
        // Apenas o teste remove FLAG_SECURE para capturar telas sem dados pessoais.
        s.onActivity { it.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        inst.waitForIdleSync()
        Thread.sleep(800)
        val dir = File(context.getExternalFilesDir(null), "screenshots-v2").apply { mkdirs() }
        val b = inst.uiAutomation.takeScreenshot()
        File(dir, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        b.recycle()
    }

    @Test
    fun facadeProtectedAreaAndMicrophoneBackground() {
        val store = Store(context)
        AccessGate(store).configure("123456", "654321")
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            screenshot(s, "01-fachada")
            for (key in listOf("2", "+", "3", "×", "4", "=")) click(s, key)
            s.onActivity { assertNotNull(find(it.window.decorView, "14")) }
            click(s, "C")
            for (key in listOf("1", "2", "3", "4", "5", "6", "=")) click(s, key)
            s.onActivity {
                assertTrue(
                    it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
                )
            }
            screenshot(s, "02-area-protegida")
            for ((route, name) in
                listOf(
                    "phrase" to "03-frase",
                    "calibration" to "04-calibracao",
                    "network" to "05-rede",
                    "contacts" to "06-consentimentos",
                    "events" to "07-ocorrencias",
                )) {
                s.onActivity { it.navigate(route) }
                screenshot(s, name)
            }
            s.onActivity { a ->
                a.startForegroundService(
                    Intent(a, SentinelaService::class.java).putExtra("test", true)
                )
            }
            val end = SystemClock.elapsedRealtime() + 20000
            while (!LiveState.active && SystemClock.elapsedRealtime() < end) Thread.sleep(200)
            assertTrue("Captura real: ${LiveState.status}", LiveState.active)
            s.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            Thread.sleep(5500)
            assertTrue(LiveState.active)
            assertTrue(LiveState.heartbeat > 0)
            s.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            s.onActivity { a ->
                assertNotNull(find(a.window.decorView, "Cálculos"))
                a.stopService(Intent(a, SentinelaService::class.java))
            }
            Thread.sleep(400)
            assertFalse(LiveState.active)
        }
    }
}
