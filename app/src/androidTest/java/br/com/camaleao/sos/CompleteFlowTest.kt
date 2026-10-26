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
import br.com.camaleao.sos.domain.VoiceAction
import br.com.camaleao.sos.domain.VoiceCommand
import br.com.camaleao.sos.ui.MainActivity
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompleteFlowTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext

    // Emulador: 10.0.2.2 é o notebook. Aparelho físico: adb reverse + 127.0.0.1.
    private val panelUrl =
        InstrumentationRegistry.getArguments().getString("panelUrl") ?: "https://10.0.2.2:8443"

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

    private val phrase = "Preciso buscar meu caderno azul"

    private fun commands(recordOn: Boolean = true) =
        listOf(
            VoiceCommand(VoiceAction.ALERT, "enviar alerta", true),
            VoiceCommand(VoiceAction.LOCATION, "compartilhar localização", true),
            VoiceCommand(VoiceAction.RECORD, "gravar evidência", recordOn),
        )

    /** Alimenta amostras em sequência como o laço de captura; devolve ações e fases. */
    private fun run(vararg clips: String, recordOn: Boolean = true): Pair<List<VoiceAction>, List<String>> {
        val actions = mutableListOf<VoiceAction>()
        val phases = mutableListOf<String>()
        VoskPipeline(
                ModelFiles.prepare(context),
                phrase,
                1.0,
                false,
                commands(recordOn),
                {},
                { actions += it },
                { phases += it },
                diagnostics = true,
            )
            .use { p ->
                var now = 0L
                for (name in clips) {
                    val audio = pcm(name)
                    for (i in audio.indices step 1600) {
                        val part = audio.copyOfRange(i, minOf(i + 1600, audio.size))
                        p.accept(part, part.size, now)
                        now += 100
                    }
                }
            }
        return actions to phases
    }

    @Test
    fun portugueseTwoStageRequiresExplicitCommandAndRejectsNegative() {
        val (alone, phases) = run("frase")
        assertTrue(phases.contains("COMMAND_WINDOW"))
        assertTrue("Frase sozinha não executa função: $alone", alone.isEmpty())
        assertEquals(listOf(VoiceAction.ALERT), run("frase", "comando").first)
        for (negative in listOf("negativo", "carro")) {
            val (a, p) = run(negative, "comando")
            assertFalse("Negativo $negative não deve abrir janela: $p", p.contains("COMMAND_WINDOW"))
            assertTrue(a.isEmpty())
        }
    }

    /** RF-69: frase e função numa tirada só, sem a pausa da janela de comando. */
    @Test
    fun functionInTheSameBreathFiresWithoutTheWindow() {
        val (actions, phases) = run("mesmafala")
        assertEquals(listOf(VoiceAction.ALERT), actions)
        assertFalse("A função saiu na releitura, sem abrir janela: $phases", phases.contains("COMMAND_WINDOW"))
    }

    /** RF-69: a posição é o que vale — palavra dita antes da frase não é função. */
    @Test
    fun functionBeforeThePhraseDoesNotFire() {
        val (actions, phases) = run("antes")
        assertTrue("Palavra antes da frase não dispara: $actions", actions.isEmpty())
        assertTrue("A frase ainda abre a janela: $phases", phases.contains("COMMAND_WINDOW"))
    }

    @Test
    fun phraseInsideSpeechAndChosenWordsTriggerTheirFunctions() {
        assertEquals(listOf(VoiceAction.ALERT), run("contexto", "comando").first)
        assertEquals(listOf(VoiceAction.LOCATION), run("frase", "localizacao").first)
        assertEquals(listOf(VoiceAction.RECORD), run("frase", "gravar").first)
        assertTrue("Função desligada não é ouvida", run("frase", "gravar", recordOn = false).first.isEmpty())
    }

    @Test
    fun vocabularyCheckFindsWordsTheModelWouldDrop() {
        assertEquals(
            listOf("localizacao"),
            Vocabulary.missing(context, listOf("compartilhar localização", "localizacao")),
        )
    }

    @Test
    fun evidenceIsEncryptedPerChunkAndTamperingIsDetected() {
        val store = isolated()
        val rec = EvidenceRecorder(context, store)
        assertTrue(rec.start("test", 60_000, 0))
        val pcm = ShortArray(1600) { (it * 37 % 2000 - 1000).toShort() }
        for (t in 0 until 30) rec.accept(pcm, pcm.size, t * 100L)
        rec.stop("teste")
        val e = EvidenceRecorder.list(store).first()
        assertEquals("CONCLUIDA", e.getString("state"))
        assertEquals(3L, e.getLong("duration_s"))
        val file = File(File(context.filesDir, "evidence"), e.getString("id") + ".bin")
        val raw = file.readBytes()
        val plain = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply { pcm.forEach { putShort(it) } }.array()
        assertEquals("PCM não pode aparecer em claro", -1, String(raw, Charsets.ISO_8859_1).indexOf(String(plain.copyOf(64), Charsets.ISO_8859_1)))
        val wav = java.io.ByteArrayOutputStream()
        EvidenceRecorder.exportWav(context, store, e.getString("id"), wav)
        assertEquals(44 + 30 * plain.size, wav.size())
        raw[raw.size - 5] = (raw[raw.size - 5].toInt() xor 1).toByte()
        file.writeBytes(raw)
        assertThrows(Exception::class.java) {
            EvidenceRecorder.exportWav(context, store, e.getString("id"), java.io.ByteArrayOutputStream())
        }
        EvidenceRecorder.delete(context, store, e.getString("id"))
        assertFalse(file.exists())
        store.close()
    }

    /** SMS para o próprio emulador (porta 5554 = número 15555215554) e posição simulada. */
    @Test
    fun alertSendsSmsWithMapLinkAfterPreparation() {
        // Aparelho físico só envia SMS real com -e smsTo <número> autorizado pela pessoa.
        val args = InstrumentationRegistry.getArguments()
        val emulator = android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk")
        org.junit.Assume.assumeTrue(emulator || args.getString("smsTo") != null)
        val to = args.getString("smsTo") ?: "15555215554"
        val withLocation = args.getString("smsLocation") != "false"
        val store = Store(context)
        store.flag("share_consent", true)
        store.flag("location_consent", withLocation)
        store.put("sms_text", if (emulator) "" else "TESTE do app Camaleão SOS (hackathon), pode ignorar.")
        store.put("contacts", JSONArray().put(JSONObject().put("name", "Teste").put("phone", to)).toString())
        store.put("cmd_alerta_on", "true")
        EmergencySms.clear(store)
        // Localização "durante o uso": o app precisa estar visível, como no código SOS
        // digitado na Fachada. Pela voz, quem garante isso é o serviço tipo location.
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val got = java.util.concurrent.atomic.AtomicReference<br.com.camaleao.sos.domain.Position?>()
        val latch = java.util.concurrent.CountDownLatch(1)
        LocationProbe.request(context, store) { got.set(it); latch.countDown() }
        latch.await(15, java.util.concurrent.TimeUnit.SECONDS)
        if (withLocation) assertNotNull("Sem posição no prazo", got.get())
        val id = Actions.alert(context, "manual")!!
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < deadline &&
            EmergencySms.log(store).none { it.getString("status") == "ACEITO PELA OPERADORA" })
            Thread.sleep(250)
        val log = EmergencySms.log(store)
        android.util.Log.i("CamaleaoTest", "SMS para $to: ${log.last().getString("status")} posição=${got.get()}")
        assertEquals("ACEITO PELA OPERADORA", log.last().getString("status"))
        assertEquals("alerta", log.last().getString("reason"))
        assertNotNull(store.record(id))
        // Cancelada na preparação: nenhum SMS novo.
        EmergencySms.clear(store)
        val cancelled = Actions.alert(context, "manual")!!
        assertTrue(store.cancel(cancelled))
        Thread.sleep(16_000)
        assertTrue("Cancelada não envia SMS: ${EmergencySms.log(store)}", EmergencySms.log(store).isEmpty())
        scenario.close()
    }

    @Test
    fun realHttpsDeliveryPersistsQueueAndVerifiesServerReceipt() {
        val pairing =
            InstrumentationRegistry.getArguments().getString("pairing")
                ?: throw AssertionError("Forneça -e pairing com o código do servidor de teste")
        val store = isolated()
        PanelTransport(store).pair(pairing, panelUrl)
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
        PanelTransport(store).pair(pairing, panelUrl)
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
        PanelTransport(store).pair(pairing, panelUrl)
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
                .pair(DeviceCrypto.b64(wrong.toByteArray()), panelUrl)
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
            // Desde a 0.6.3 a Área protegida não bloqueia print nem gravação de tela.
            s.onActivity {
                assertTrue(
                    it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0
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
