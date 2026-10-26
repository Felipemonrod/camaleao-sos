package br.com.camaleao.sos.ui

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import br.com.camaleao.sos.LiveState
import br.com.camaleao.sos.domain.Calculator
import br.com.camaleao.sos.security.AccessGate
import br.com.camaleao.sos.sentinela.*
import br.com.camaleao.sos.sos.*
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors
import org.json.JSONArray

class MainActivity : ComponentActivity() {
    internal lateinit var store: Store
    internal lateinit var gate: AccessGate
    internal lateinit var root: LinearLayout
    internal lateinit var body: LinearLayout
    internal val green = Color.rgb(22, 122, 99)
    internal val ink = Color.rgb(23, 52, 45)
    internal val muted = Color.rgb(91, 111, 101)
    internal var page = "calculator"
    private var expression = ""
    private var display: TextView? = null
    internal var status: TextView? = null
    internal var heartbeat: TextView? = null
    internal var phase: TextView? = null
    internal var sample: TextView? = null
    internal val executor = Executors.newSingleThreadExecutor()
    internal val positives = mutableListOf<br.com.camaleao.sos.sentinela.SampleResult>()
    internal val negatives = mutableListOf<br.com.camaleao.sos.sentinela.SampleResult>()
    internal var capturedSerial = -1
    /** Enquanto a agenda aberta pelo próprio app está na frente, a Fachada não volta. */
    internal var picking = false
    internal var draftName = ""
    internal var draftPhone = ""
    internal var lastSample: br.com.camaleao.sos.sentinela.SampleResult? = null
    internal var phraseSample: br.com.camaleao.sos.sentinela.PhraseSample? = null
    internal var recordingSample = false
    internal var analyzingSample = false
    private var lastSnapshot = ""
    internal var locator: PositionRequest? = null
    internal var pendingExport = ""
    private val updates: () -> Unit = {
        if (page != "calculator") {
            status?.text = LiveState.status
            phase?.text = voiceStatus()
            heartbeat?.text =
                "Último sinal de vida: " +
                    if (LiveState.heartbeat > 0)
                        DateFormat.getTimeInstance().format(Date(LiveState.heartbeat))
                    else "nenhum nesta sessão"
            sample?.text =
                LiveState.lastHeard.ifBlank { "Aguardando fala. Nenhuma transcrição é salva." }
            val snapshot =
                "${LiveState.active}/${LiveState.preparing}/${LiveState.voicePhase}" +
                    if (page == "events")
                        store.list().joinToString {
                            it.getString("state") + it.optBoolean("cancelled")
                        }
                    else ""
            if (snapshot != lastSnapshot && page in listOf("home", "events")) {
                lastSnapshot = snapshot
                render()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        store = Store(this)
        gate = AccessGate(store)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (page != "calculator") navigate("calculator") else finish()
                }
            },
        )
        render()
    }

    override fun onStart() {
        super.onStart()
        LiveState.listeners.add(updates)
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) render()
    }

    override fun onStop() {
        if (picking) return super.onStop()
        LiveState.listeners.remove(updates)
        locator?.stop()
        page = "calculator"
        expression = ""
        LiveState.lastHeard = ""
        positives.clear()
        negatives.clear()
        lastSample = null
        phraseSample?.discard()
        phraseSample = null
        recordingSample = false
        super.onStop()
    }

    /** Só em build debug: libera espelhamento da Área protegida para apresentar. */
    internal val debuggable
        get() = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    internal val presenting
        get() = debuggable && store.flag("demo_mode")

    internal fun dp(n: Int) = (resources.displayMetrics.density * n).toInt()

    internal fun bg(color: Int, radius: Int = 20) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    internal fun text(value: String, size: Int = 16, color: Int = ink, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            setLineSpacing(dp(3).toFloat(), 1f)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    internal fun gap(parent: LinearLayout = body, n: Int = 12) {
        parent.addView(Space(this), LinearLayout.LayoutParams(1, dp(n)))
    }

    internal fun heading(title: String, sub: String) {
        body.addView(text(title, 30, ink, true))
        gap(n = 8)
        body.addView(text(sub, 15, muted))
        gap(n = 22)
    }

    internal fun card(color: Int = Color.WHITE, block: LinearLayout.() -> Unit) {
        val box =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = bg(color)
                setPadding(dp(20), dp(20), dp(20), dp(20))
            }
        body.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        box.block()
    }

    internal fun button(
        label: String,
        parent: LinearLayout = body,
        secondary: Boolean = false,
        action: () -> Unit,
    ): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            setTextColor(if (secondary) green else Color.WHITE)
            background = bg(if (secondary) Color.rgb(227, 237, 224) else green, 14)
            minHeight = dp(52)
            setPadding(dp(10), dp(12), dp(10), dp(12))
            parent.addView(
                this,
                LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(8)
                    bottomMargin = dp(4)
                },
            )
            setOnClickListener { action() }
        }

    internal fun input(
        label: String,
        initial: String = "",
        secret: Boolean = false,
        multiline: Boolean = false,
    ): EditText =
        EditText(this).apply {
            hint = label
            setText(initial)
            setTextColor(ink)
            textSize = 16f
            background = bg(Color.WHITE, 12)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isSingleLine = !multiline
            inputType =
                if (secret)
                    android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
                else
                    android.text.InputType.TYPE_CLASS_TEXT or
                        if (multiline) android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0
            contentDescription = label
            body.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        }

    internal fun check(label: String, value: Boolean, action: (Boolean) -> Unit) {
        body.addView(
            CheckBox(this).apply {
                text = label
                setTextColor(ink)
                isChecked = value
                setOnCheckedChangeListener { _, v -> action(v) }
            }
        )
    }

    internal fun navigate(next: String) {
        if (next == "calculator") {
            expression = ""
            LiveState.lastHeard = ""
        }
        page = next
        render()
    }

    internal fun render() {
        status = null
        heartbeat = null
        phase = null
        sample = null
        // Print e gravação de tela liberados em todas as telas, a pedido do responsável,
        // para a apresentação (0.6.3). Custo: a Área protegida aparece em capturas e na
        // lista de recentes; ver docs/07.
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.rgb(244, 247, 242))
            }
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val b =
                    insets.getInsets(
                        WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                    )
                view.setPadding(b.left, b.top, b.right, b.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom,
                )
            }
            insets
        }
        if (page == "calculator") calculator()
        else {
            val top =
                LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(24), dp(16), dp(24), dp(8))
                }
            top.addView(
                text(if (presenting) "◔ camaleão · APRESENTAÇÃO" else "◔ camaleão", 22, if (presenting) Color.rgb(196, 80, 30) else green, true),
                LinearLayout.LayoutParams(0, -2, 1f),
            )
            top.addView(
                text("Fechar", 14, muted, true).apply {
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    setOnClickListener { navigate("calculator") }
                }
            )
            root.addView(top)
            val scroll = ScrollView(this)
            body =
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(24), dp(18), dp(24), dp(24))
                }
            scroll.addView(body)
            root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            when (page) {
                "setup" -> setup()
                "phrase" -> phrase()
                "calibration" -> calibration()
                "network" -> network()
                "contacts" -> contacts()
                "functions" -> functions()
                "events" -> events()
                "privacy" -> privacy()
                else -> home()
            }
            if (store.flag("configured")) {
                val nav = LinearLayout(this).apply { setBackgroundColor(Color.WHITE) }
                listOf("Sentinela" to "home", "Rede" to "network", "Ocorrências" to "events")
                    .forEach { (name, route) ->
                        nav.addView(
                            text(name, 13, if (page == route) green else muted, true).apply {
                                gravity = Gravity.CENTER
                                setPadding(0, dp(18), 0, dp(18))
                                setOnClickListener { navigate(route) }
                            },
                            LinearLayout.LayoutParams(0, dp(56), 1f),
                        )
                    }
                root.addView(nav)
            }
        }
        setContentView(root)
        root.requestApplyInsets()
    }

    private fun calculator() {
        root.addView(
            text("Cálculos", 22, ink, true).apply { setPadding(dp(24), dp(22), dp(24), dp(8)) }
        )
        display =
            text(expression.ifBlank { "0" }, 42, ink, true).apply {
                gravity = Gravity.END or Gravity.BOTTOM
                setPadding(dp(24), dp(20), dp(24), dp(28))
                maxLines = 3
            }
        root.addView(display, LinearLayout.LayoutParams(-1, 0, 1f))
        listOf(
                listOf("C", "⌫", "÷", "×"),
                listOf("7", "8", "9", "−"),
                listOf("4", "5", "6", "+"),
                listOf("1", "2", "3", "00"),
                listOf("0", ",", "="),
            )
            .forEach { row ->
                val layout = LinearLayout(this)
                row.forEach { key ->
                    layout.addView(
                        Button(this).apply {
                            text = key
                            textSize = 25f
                            isAllCaps = false
                            setTextColor(if (key == "=") Color.WHITE else ink)
                            background = bg(if (key == "=") green else Color.WHITE, 18)
                            setOnClickListener { key(key) }
                        },
                        LinearLayout.LayoutParams(
                                0,
                                dp(72),
                                if (row.size == 3 && key == "=") 2f else 1f,
                            )
                            .apply { setMargins(dp(5), dp(5), dp(5), dp(5)) },
                    )
                }
                root.addView(layout)
            }
    }

    private fun key(value: String) {
        when (value) {
            "C" -> expression = ""
            "⌫" -> expression = expression.dropLast(1)
            "=" -> {
                when (gate.match(expression)) {
                    "access" -> {
                        expression = ""
                        navigate(if (store.flag("configured")) "home" else "setup")
                        return
                    }
                    "sos" -> Actions.alert(this, "code")
                }
                expression =
                    runCatching { Calculator.calculate(expression.replace('−', '-')) }
                        .getOrDefault("Erro")
            }
            else -> {
                if (expression == "Erro") expression = ""
                if (expression.length < 100) expression += value
            }
        }
        display?.text = expression.ifBlank { "0" }
    }

    internal fun listen(test: Boolean) {
        if (LiveState.active || LiveState.preparing) {
            toast("Desarme a sessão atual primeiro.")
            return
        }
        if (
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        if (
            Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
            return
        }
        if (!ServiceNotice.manager(this).areNotificationsEnabled()) {
            toast("Habilite as notificações nas configurações do Android.")
            return
        }
        // Diz o que falta e leva à tela certa: a autorização fica em Contatos e consentimentos.
        if (!test && !store.flag("calibrated")) {
            toast("Falta calibrar a frase: grave 3 positivas e 1 negativa e calcule o ajuste.")
            navigate("calibration")
            return
        }
        if (!test && !store.flag("share_consent")) {
            toast("Falta autorizar a comunicação: marque a primeira autorização desta tela.")
            navigate("contacts")
            return
        }
        LiveState.preparing = true
        try {
            startForegroundService(
                Intent(this, SentinelaService::class.java).putExtra("test", test)
            )
        } catch (_: RuntimeException) {
            LiveState.preparing = false
            toast("Android bloqueou o serviço. Confira as permissões.")
        }
        render()
    }

    internal fun stopListening() {
        br.com.camaleao.sos.sos.OpenLineCall.stop()
        store.flag("wanted_armed", false)
        stopService(Intent(this, SentinelaService::class.java))
        LiveState.cancelCommand = true
        render()
    }

    internal fun confirm(title: String, message: String, action: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("Voltar", null)
            .setPositiveButton("Confirmar") { _, _ -> action() }
            .show()
    }

    internal fun toast(value: String) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    }

    internal fun permissions() {
        AlertDialog.Builder(this)
            .setTitle("Compatibilidade deste aparelho")
            .setMessage(
                br.com.camaleao.sos.sos.Compatibility.text(this) +
                    "\n\nFabricantes podem interromper o serviço. Não há isenção automática de bateria."
            )
            .setPositiveButton("Permissões") { _, _ ->
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName"),
                    )
                )
            }
            .setNeutralButton("Bateria") { _, _ ->
                runCatching {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<String>, r: IntArray) {
        super.onRequestPermissionsResult(code, p, r)
        if (code == 3 && r.any { it == PackageManager.PERMISSION_GRANTED }) requestLocation()
        else render()
    }

    @Deprecated("Compatibilidade com API 26")
    override fun onActivityResult(code: Int, result: Int, data: Intent?) {
        super.onActivityResult(code, result, data)
        if (code == 72) picking = false
        if (code == 72 && result == RESULT_OK)
            data?.data?.let { uri ->
                runCatching {
                        contentResolver
                            .query(
                                uri,
                                arrayOf(
                                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                                ),
                                null,
                                null,
                                null,
                            )
                            ?.use { if (it.moveToFirst()) it.getString(0).orEmpty() to it.getString(1).orEmpty() else null }
                    }
                    .getOrNull()
                    ?.let { (name, number) -> addContact(name, number) } ?: toast("Não foi possível ler o contato.")
            }
        if (code == 70 && result == RESULT_OK)
            data?.data?.let { uri ->
                runCatching {
                        contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                            it.write(JSONArray(store.list()).toString(2))
                        }
                    }
                    .onSuccess { toast("Registros exportados. Guarde em local seguro.") }
                    .onFailure { toast("Não foi possível exportar.") }
            }
        if (code == 71 && result == RESULT_OK && pendingExport.isNotBlank()) {
            val id = pendingExport
            pendingExport = ""
            data?.data?.let { uri ->
                executor.execute {
                    val r = runCatching {
                        contentResolver.openOutputStream(uri)?.use {
                            EvidenceRecorder.exportWav(this, store, id, it)
                        }
                    }
                    runOnUiThread {
                        toast(
                            if (r.isSuccess) "Gravação exportada e conferida pelo hash."
                            else "Falha na exportação: ${r.exceptionOrNull()?.message?.take(80)}"
                        )
                    }
                }
            }
        }
    }
}
