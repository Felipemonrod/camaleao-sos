package br.com.camaleao.sos.ui

import android.app.AlertDialog
import android.content.*
import android.graphics.Color
import android.net.Uri
import br.com.camaleao.sos.LiveState
import br.com.camaleao.sos.domain.PhoneBr
import br.com.camaleao.sos.domain.PhraseMatcher
import br.com.camaleao.sos.domain.VoiceCommands
import br.com.camaleao.sos.sos.Actions
import br.com.camaleao.sos.sos.*
import br.com.camaleao.sos.sentinela.PhraseSample
import br.com.camaleao.sos.sentinela.Stage1
import java.text.DateFormat
import java.util.Date
import org.json.JSONArray
import org.json.JSONObject

internal fun MainActivity.setup() {
    heading(
        "Seu espaço protegido.",
        "Configure os códigos em um momento seguro. A Fachada não oculta os indicadores de microfone do Android.",
    )
    val access = input("Código de acesso · 6 a 12 dígitos", secret = true)
    val sos = input("Código SOS diferente · 6 a 12 dígitos", secret = true)
    body.addView(
        text(
            "Digite um código e depois = na calculadora. O código de acesso abre esta área. O código SOS prepara a Ocorrência sem mudar de tela, depois da autorização de comunicação. Memorize os dois; não há recuperação de código.",
            14,
            muted,
        )
    )
    button("Salvar meus códigos") {
        runCatching { gate.configure(access.text.toString(), sos.text.toString()) }
            .onSuccess { navigate("phrase") }
            .onFailure { toast("Use códigos diferentes, de 6 a 12 dígitos.") }
    }
    button("Privacidade e limites", secondary = true) { navigate("privacy") }
}

internal fun MainActivity.home() {
    if (!store.flag("configured")) {
        page = "setup"
        setup()
        return
    }
    heading(
        "Sentinela de\nVoz Offline",
        "Sua voz é processada neste celular. Armar não é disparar um Protocolo SOS.",
    )
    card(if (LiveState.active) Color.rgb(215, 244, 153) else Color.WHITE) {
        status = text(LiveState.status, 22, ink, true)
        addView(status)
        gap(this, 10)
        heartbeat =
            text(
                "Último sinal de vida: " +
                    store
                        .get("heartbeat")
                        .toLongOrNull()
                        ?.let { DateFormat.getTimeInstance().format(Date(it)) }
                        .orEmpty(),
                13,
                muted,
            )
        addView(heartbeat)
        phase = text(voiceStatus(), 13, muted)
        addView(phase)
        button(
                if (LiveState.active || LiveState.preparing) "Desarmar Sentinela"
                else "Armar Sentinela",
                this,
            ) {
                if (LiveState.active || LiveState.preparing) stopListening() else listen(false)
            }
            .isEnabled = !LiveState.preparing
        if (LiveState.voicePhase == "COMMAND_WINDOW")
            button("Cancelar comando pendente", this, true) { LiveState.cancelCommand = true }
        if (LiveState.callInProgress)
            button("Parar ligações", this) {
                OpenLineCall.stop()
                toast("Sem novas ligações. Encerre a chamada atual na tela de chamada.")
            }
    }
    if (store.flag("unexpected_stop"))
        card {
            addView(text("A sessão anterior foi interrompida.", 17, ink, true))
            addView(
                text(
                    "Confira as permissões e arme novamente. O estado anterior não é prova de que a escuta continua.",
                    14,
                    muted,
                )
            )
        }
    if (LiveState.recording)
        card(Color.rgb(255, 236, 214)) {
            addView(text("Gravação cifrada em andamento", 17, ink, true))
            button("Parar gravação", this, true) { LiveState.stopRecording = true }
        }
    button("Testar minha frase", secondary = true) { navigate("calibration") }
    button("Frase de Segurança", secondary = true) { navigate("phrase") }
    button("Palavras e funções", secondary = true) { navigate("functions") }
    button("Rede de Apoio e consentimentos", secondary = true) { navigate("contacts") }
    button("Criar Ocorrência manual", secondary = true) {
        confirm(
            "Preparar Protocolo SOS?",
            "A Ocorrência será encaminhada ao Painel pareado após a preparação. É possível cancelar a preparação dentro desta área.",
        ) {
            if (Actions.alert(this, "manual") == null)
                toast("Autorize a comunicação na Rede de Apoio.")
            else navigate("events")
        }
    }
    card {
        addView(text("Quando a Sentinela para", 17, ink, true))
        gap(this, 8)
        addView(
            text(
                "A proteção por voz é encerrada ao forçar parada, se o sistema interromper o áudio, se a permissão do microfone for removida ou se o celular reiniciar. Após reiniciar, abra esta área e rearme. Fabricantes e economia de bateria podem encerrar o serviço.",
                14,
                muted,
            )
        )
    }
    button("Microfone, notificações e bateria", secondary = true) { permissions() }
    button("Privacidade, dados e licenças", secondary = true) { navigate("privacy") }
    button("Trocar códigos", secondary = true) {
        stopListening()
        navigate("setup")
    }
}

internal fun MainActivity.phrase() {
    heading(
        "Uma frase só sua.",
        "Escolha pelo menos três palavras, fora da conversa habitual. A frase é um Gatilho acústico, não uma credencial.",
    )
    val value = input("Frase de Segurança", store.phrase())
    card {
        addView(text("Português · Vosk em dois estágios", 17, ink, true))
        gap(this, 8)
        addView(
            text(
                "Após a frase, diga a palavra de uma função ligada em até ${Actions.commandWindowS(store)} segundos, ou as duas na mesma fala: " +
                    Actions.commands(store).filter { it.enabled }.joinToString { "“${it.words}”" } +
                    ". Timeout ou outra fala volta à espera sem agir. O sherpa-onnx é a arquitetura alvo; não há modelo KWS PT validado incluído.",
                14,
                muted,
            )
        )
    }
    button("Salvar e calibrar") {
        if (LiveState.active || LiveState.preparing) {
            toast("Desarme antes de trocar a frase.")
            return@button
        }
        val p = value.text.toString().trim()
        if (PhraseMatcher.words(p).size !in 3..10 || p.length > 100) {
            toast("Use de 3 a 10 palavras, até 100 caracteres.")
            return@button
        }
        VoiceCommands.problem(p, Actions.commands(store))?.let {
            toast("Conflito com as palavras das funções. $it")
            return@button
        }
        withVocabulary(listOf(p)) {
            store.put("phrase", p)
            store.flag("calibrated", false)
            store.put("threshold", "1.0")
            store.put("verify", "")
            positives.clear()
            negatives.clear()
            navigate("calibration")
        }
    }
}

internal fun MainActivity.calibration() {
    heading(
        "Teste antes de armar.",
        "Segure o botão, fale e solte, como um áudio de mensagem. Grave a Frase de Segurança três vezes e uma frase parecida uma vez.",
    )
    card {
        addView(text("Frase: ${store.phrase()}", 18, ink, true))
        gap(this, 10)
        val result = lastSample
        addView(
            text(
                when {
                    recordingSample -> "Gravando… solte para analisar."
                    analyzingSample -> "Analisando no aparelho…"
                    result == null -> "Nenhuma amostra analisada. O áudio fica só na memória e é apagado depois."
                    else ->
                        "Entendi: “${result.heard.ifBlank { "(nada)" }}”\n" +
                            "${result.percent}% parecido com a frase · " +
                            if (result.recognized) "a Sentinela reconheceria ✓"
                            else "a Sentinela não reconheceria ✗"
                },
                15,
                if (result?.recognized == true) green else muted,
            )
        )
    }
    holdToTalk()
    lastSample?.let {
        button("Era a frase: guardar como positiva") { classify(true) }
        button("Era outra fala: guardar como negativa", secondary = true) { classify(false) }
    }
    body.addView(
        text(
            "Positivas ${positives.size}/3: ${positives.joinToString { "${it.percent}%" }.ifBlank { "nenhuma" }} · " +
                "negativas ${negatives.size}/1: ${negatives.joinToString { "${it.percent}%" }.ifBlank { "nenhuma" }}",
            14,
            muted,
        )
    )
    button("Recomeçar amostras", secondary = true) {
        positives.clear()
        negatives.clear()
        lastSample = null
        render()
    }
    button("Calcular ajuste e salvar") {
        if (positives.size < 3 || negatives.isEmpty()) {
            toast("Faltam amostras: três positivas reconhecidas e uma negativa.")
            return@button
        }
        val worst = positives.minOf { it.percent }
        val closest = negatives.maxOf { it.percent }
        val verify = PhraseMatcher.personalVerify(positives.map { it.percent }, negatives.map { it.percent }, Stage1.VERIFY)
        if (verify == null) {
            toast("A frase parecida ficou tão perto da sua frase ($closest% contra $worst%). Troque a Frase de Segurança ou recomece as amostras.")
            return@button
        }
        store.put("verify", verify.toString())
        store.flag("calibrated", true)
        toast("Ajuste salvo: a Sentinela exige ${(verify * 100).toInt()}% (sua frase: a partir de $worst%; a parecida: $closest%). FAR e FRR ainda precisam de validação real.")
        navigate("home")
    }
    body.addView(
        text(
            "Este ajuste não certifica segurança nem estima FAR/FRR. A porcentagem compara o texto entendido com a frase; a Sentinela aceita a partir de ${(store.verify() * 100).toInt()}% e com a frase inteira, na ordem. Uma negativa que dispara não é perdida: o ajuste passa a exigir mais que ela.",
            13,
            muted,
        )
    )
}

@android.annotation.SuppressLint("ClickableViewAccessibility")
private fun MainActivity.holdToTalk() {
    val talk = button(if (recordingSample) "Solte para analisar" else "🎙 Segure para falar") {}
    talk.setOnTouchListener { _, event ->
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                if (LiveState.active || LiveState.preparing) {
                    toast("Desarme a Sentinela antes de testar a frase.")
                } else if (!analyzingSample && !recordingSample) {
                    val recorder = PhraseSample()
                    if (recorder.start(this)) {
                        phraseSample = recorder
                        recordingSample = true
                        talk.text = "Solte para analisar"
                    } else {
                        requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 1)
                    }
                }
                true
            }
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL -> {
                val recorder = phraseSample
                if (recordingSample && recorder != null) {
                    recordingSample = false
                    phraseSample = null
                    if (recorder.stop() < 0.8) {
                        recorder.discard()
                        toast("Muito curto. Segure enquanto fala a frase inteira.")
                        render()
                    } else {
                        analyzingSample = true
                        render()
                        val phrase = store.phrase()
                        val verify = store.verify()
                        executor.execute {
                            val result = runCatching { recorder.evaluate(this, phrase, verify) }.getOrNull()
                            runOnUiThread {
                                analyzingSample = false
                                lastSample = result
                                if (result == null) toast("Não foi possível analisar. Tente de novo.")
                                render()
                            }
                        }
                    }
                }
                true
            }
            else -> true
        }
    }
}

private fun MainActivity.classify(positive: Boolean) {
    val result = lastSample ?: return
    if (positive && !result.recognized) {
        toast("Com essa gravação a Sentinela não dispararia. Grave a frase de novo; nada foi guardado.")
        return
    }
    if (positive) {
        if (positives.size < 3) positives += result
    } else {
        negatives += result
        if (result.recognized)
            toast("Esta frase parecida dispararia hoje (${result.percent}%). O ajuste vai exigir mais que isso para separar da sua frase.")
    }
    lastSample = null
    render()
}

internal fun MainActivity.network() {
    heading(
        "Sua Rede de Apoio.",
        "Pareie com o notebook de participantes autorizados. HTTPS com certificado fixado e recibos assinados.",
    )
    card {
        addView(
            text(
                if (store.get("endpoint").isBlank()) "Painel não pareado" else "Painel pareado",
                19,
                ink,
                true,
            )
        )
        gap(this, 8)
        addView(
            text(
                store.get("endpoint", "Configure o servidor local antes de transmitir."),
                14,
                muted,
            )
        )
    }
    val code = input("Cole o código de pareamento", multiline = true)
    val url = input("URL alternativa · opcional")
    button("Parear com o Painel") {
        val c = code.text.toString()
        val u = url.text.toString().trim()
        executor.execute {
            val result = runCatching { PanelTransport(store).pair(c, u) }
            runOnUiThread {
                result
                    .onSuccess {
                        toast("Pareamento concluído com certificado verificado.")
                        Protocol.schedule(this)
                        Protocol.pump(this)
                        render()
                    }
                    .onFailure { toast("Falha no pareamento: ${it.message?.take(120)}") }
            }
        }
    }
    button("Enviar teste identificado ao Painel", secondary = true) {
        confirm(
            "Enviar uma Ocorrência de teste?",
            "O Painel receberá um registro marcado TESTE. Nenhuma pessoa ou central pública é acionada automaticamente.",
        ) {
            Protocol.create(this, "test", true)
            navigate("events")
        }
    }
    button("Tentar transmitir a fila agora", secondary = true) {
        Protocol.pump(this)
        toast("Tentativa iniciada. Confira o estado de cada Ocorrência.")
    }
    button("Contatos e consentimentos", secondary = true) { navigate("contacts") }
    body.addView(
        text(
            "Recebimento no Painel não significa atendimento. Sem conectividade, a Ocorrência permanece cifrada na fila. O notebook precisa estar ligado, acessível e com o servidor aberto.",
            14,
            muted,
        )
    )
}

internal fun MainActivity.contacts() {
    heading(
        "Rede de Apoio\ncom consentimento.",
        "Use apenas participantes que aceitaram o teste. Da agenda, o app recebe só o contato que você escolher.",
    )
    val contacts = JSONArray(store.get("contacts", "[]"))
    for (i in 0 until contacts.length()) {
        val c = contacts.getJSONObject(i)
        card {
            addView(text(c.getString("name"), 18, ink, true))
            addView(text(PhoneBr.format(c.getString("phone")), 14, muted))
            button("Remover", this, true) {
                val next = JSONArray()
                for (j in 0 until contacts.length()) if (j != i) next.put(contacts.get(j))
                store.put("contacts", next.toString())
                render()
            }
        }
    }
    if (contacts.length() < 3) {
        button("Escolher da agenda") {
            try {
                picking = true
                startActivityForResult(
                    Intent(Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI),
                    72,
                )
            } catch (_: ActivityNotFoundException) {
                picking = false
                toast("Nenhuma agenda disponível. Digite o número abaixo.")
            }
        }
        val name = input("Nome ou apelido", draftName)
        val phone = input("Telefone com DDD, ex.: 11 99999-0000", draftPhone)
        button("Adicionar participante", secondary = true) {
            addContact(name.text.toString(), phone.text.toString())
        }
    }
    check(
        "Autorizo transmitir Ocorrências ao Painel pareado e enviar SMS automático aos participantes que concordaram. O SMS enviado pode aparecer no aplicativo de mensagens deste celular.",
        store.flag("share_consent"),
    ) {
        store.flag("share_consent", it)
        if (!it) stopListening()
    }
    check(
        "Autorizo obter uma posição pontual ao disparar um alerta ou compartilhar localização, e incluí-la no SMS e na Ocorrência.",
        store.flag("location_consent"),
    ) {
        store.flag("location_consent", it)
        if (!it) {
            LiveState.location = null
            locator?.stop()
        }
    }
    check("Lembrar de rearmar após reiniciar", store.flag("rearm_reminder")) {
        store.flag("rearm_reminder", it)
    }
    button("Obter localização agora", secondary = true) { requestLocation() }
    button("Voltar à Sentinela") { navigate("home") }
}

/**
 * Confirma o número já normalizado antes de gravar: o código de operadora sai e o
 * número mostrado é o que vai receber SMS e ligação.
 */
internal fun MainActivity.addContact(rawName: String, rawPhone: String) {
    val n = rawName.trim()
    val p = PhoneBr.normalize(rawPhone)
    if (n.isBlank() || n.length > 60 || p == null) {
        // Leva o que veio da agenda para os campos, para corrigir só o que falta (ex.: DDD).
        draftName = n.take(60)
        draftPhone = rawPhone.trim()
        render()
        toast(
            if (n.isBlank()) "Informe um nome para o participante."
            else "Não reconheci “${rawPhone.trim()}”. Precisa ter DDD + número, ex.: 11 99999-0000. Corrija abaixo."
        )
        return
    }
    draftName = ""
    draftPhone = ""
    val contacts = JSONArray(store.get("contacts", "[]"))
    if ((0 until contacts.length()).any { contacts.getJSONObject(it).getString("phone") == p }) {
        toast("Esse número já está na Rede de Apoio.")
        return
    }
    if (contacts.length() >= 3) return
    confirm("Adicionar $n?", "Número que vai receber SMS e ligação: ${PhoneBr.format(p)}. Confira os dígitos.") {
        contacts.put(JSONObject().put("name", n).put("phone", p))
        store.put("contacts", contacts.toString())
        render()
    }
}

internal fun MainActivity.events() {
    heading(
        "Ocorrências.",
        "Confirmação somente após verificar o recibo do Painel. Isso não comprova atendimento.",
    )
    val records = store.list()
    if (records.isEmpty())
        card {
            addView(text("Nenhuma Ocorrência registrada.", 17, ink, true))
            addView(text("O teste de frase não dispara o Protocolo SOS.", 14, muted))
        }
    records.forEach { r ->
        card {
            val state = r.getString("state")
            val cancelled = r.optBoolean("cancelled")
            addView(
                text(
                    (if (r.getBoolean("test")) "TESTE · " else "") +
                        (if (cancelled) "Cancelada antes da fila" else state),
                    19,
                    ink,
                    true,
                )
            )
            gap(this, 8)
            addView(
                text(r.getString("created_at") + "\nOrigem: " + r.getString("trigger"), 13, muted)
            )
            gap(this, 8)
            addView(
                text(
                    when {
                        cancelled -> "Não foi transmitida."
                        state == "ACKNOWLEDGED" ->
                            "Recebida no Painel; recibo assinado verificado. Atendimento não confirmado."
                        state == "SENT" -> "Transmitida; aguardando recibo verificável."
                        state == "QUEUED" ->
                            "Na fila cifrada. Ainda sem confirmação de recebimento."
                        else -> "Preparando localmente. Pode cancelar dentro desta área."
                    },
                    14,
                    muted,
                )
            )
            if (state == "CREATED" && !cancelled)
                button("Cancelar preparação", this, true) {
                    if (!store.cancel(r.getString("id")))
                        toast("A preparação terminou. Um envio anterior não pode ser desfeito.")
                    render()
                }
            if (!cancelled)
                button("Auditoria e recibo", this, true) {
                    AlertDialog.Builder(this@events)
                        .setTitle("Estado verificável")
                        .setMessage(
                            r.optJSONArray("audit").toString() +
                                "\n\n" +
                                r.optString("receipt", "Ainda sem recibo")
                        )
                        .setPositiveButton("Fechar", null)
                        .show()
                }
            if (!r.getBoolean("test") && !cancelled)
                button("Revisar SMS para um participante", this, true) { sms(r) }
        }
    }
    evidenceAndSms()
    button("Tentar transmitir fila", secondary = true) { Protocol.pump(this) }
    button("Exportar registros e recibos", secondary = true) {
        confirm(
            "Exportar dados sensíveis?",
            "O arquivo pode incluir localização. Escolha um destino confiável.",
        ) {
            startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE, "camaleao-recibos.json"),
                70,
            )
        }
    }
    button("Excluir registros já recebidos", secondary = true) {
        confirm(
            "Excluir cópias locais recebidas?",
            "A fila pendente e a sequência criptográfica são preservadas; a cópia do Painel continua existindo.",
        ) {
            store.deleteReceived()
            render()
        }
    }
}

private fun MainActivity.sms(record: JSONObject) {
    if (!store.flag("share_consent")) {
        toast("Autorize a comunicação primeiro.")
        return
    }
    val contacts = JSONArray(store.get("contacts", "[]"))
    if (contacts.length() == 0) {
        toast("Cadastre um participante autorizado.")
        return
    }
    AlertDialog.Builder(this)
        .setTitle("Escolha o participante")
        .setItems(Array(contacts.length()) { contacts.getJSONObject(it).getString("name") }) {
            _,
            index ->
            val c = contacts.getJSONObject(index)
            val message =
                "Preciso de ajuda. Por favor, entre em contato comigo. Ocorrência preparada no Camaleão SOS em ${record.getString("created_at")}."
            confirm("Revisar mensagem para ${c.getString("name")}?", message) {
                try {
                    startActivity(
                        Intent(
                                Intent.ACTION_SENDTO,
                                Uri.fromParts("smsto", c.getString("phone"), null),
                            )
                            .putExtra("sms_body", message)
                    )
                } catch (_: ActivityNotFoundException) {
                    toast("Nenhum aplicativo SMS disponível.")
                }
            }
        }
        .setNegativeButton("Voltar", null)
        .show()
}

internal fun MainActivity.privacy() {
    heading(
        "Privacidade\ne limites claros.",
        "Configuração e fila cifradas com chaves protegidas pelo Android Keystore.",
    )
    card {
        addView(text("Seu áudio fica no aparelho", 18, ink, true))
        gap(this, 8)
        addView(
            text(
                "O Buffer pré-evento vive em RAM e é sobrescrito. A gravação pós-disparo só existe se você ligar a função; fica cifrada neste aparelho e nunca é enviada. O uso como evidência depende de avaliação jurídica.",
                14,
                muted,
            )
        )
    }
    card {
        addView(text("Integridade não é validade judicial", 18, ink, true))
        gap(this, 8)
        addView(
            text(
                "O manifesto é assinado e encadeado. Recibo válido comprova recebimento técnico pelo Painel. Não garante socorro nem torna o registro prova judicial válida.",
                14,
                muted,
            )
        )
    }
    card {
        addView(text("Ainda exige medição", 18, ink, true))
        gap(this, 8)
        addView(
            text(
                "Precisão, falsos positivos, voz baixa, ruído, aparelhos reais, bateria e interrupções de áudio. A calibração local não substitui esses ensaios. Sem integração com polícia, SAMU ou centrais públicas.",
                14,
                muted,
            )
        )
    }
    if (debuggable)
        check(
            "Modo apresentação (só nesta versão de teste): permite espelhar ou capturar a Área protegida. Desligue depois.",
            store.flag("demo_mode"),
        ) {
            store.flag("demo_mode", it)
            render()
        }
    button("Licenças Vosk / JNA / Kotlin", secondary = true) {
        AlertDialog.Builder(this)
            .setTitle("Licenças de terceiros")
            .setMessage(assets.open("licenses/NOTICES.txt").bufferedReader().use { it.readText() })
            .setPositiveButton("Fechar", null)
            .setNeutralButton("Apache 2.0") { _, _ ->
                AlertDialog.Builder(this)
                    .setMessage(
                        assets.open("licenses/vosk-APACHE-2.0.txt").bufferedReader().use {
                            it.readText()
                        }
                    )
                    .setPositiveButton("Fechar", null)
                    .show()
            }
            .show()
    }
    button("Voltar", secondary = true) {
        navigate(if (store.flag("configured")) "home" else "setup")
    }
}
