package br.com.camaleao.sos.ui

import android.app.AlertDialog
import android.content.*
import android.graphics.Color
import android.net.Uri
import br.com.camaleao.sos.LiveState
import br.com.camaleao.sos.domain.PhraseMatcher
import br.com.camaleao.sos.domain.VoiceCommands
import br.com.camaleao.sos.sos.Actions
import br.com.camaleao.sos.sos.*
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
                "Após a frase, diga a palavra de uma função ligada em até $VOICE_WINDOW_S segundos: " +
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
            positives.clear()
            negatives.clear()
            navigate("calibration")
        }
    }
}

internal fun MainActivity.calibration() {
    heading(
        "Teste antes de armar.",
        "Fale a Frase de Segurança três vezes, com pequenas pausas. Depois fale uma frase parecida que não deveria disparar.",
    )
    card {
        addView(text("Frase: ${store.phrase()}", 18, ink, true))
        gap(this, 10)
        sample =
            text(
                LiveState.lastHeard.ifBlank { "Aguardando fala. Nenhuma transcrição é salva." },
                15,
                muted,
            )
        addView(sample)
    }
    button("Iniciar teste de voz offline") { listen(true) }
    button("Encerrar teste", secondary = true) { stopListening() }
    body.addView(
        text("Amostras positivas: ${positives.size}/3 · negativas: ${negatives.size}/1", 14, muted)
    )
    button("Guardar leitura como positiva", secondary = true) { captureSample(true) }
    button("Guardar leitura como negativa", secondary = true) { captureSample(false) }
    button("Recomeçar amostras", secondary = true) {
        positives.clear()
        negatives.clear()
        render()
    }
    button("Calcular ajuste e salvar") {
        val threshold = PhraseMatcher.calibratedThreshold(positives, negatives, store.phrase())
        if (threshold == null) {
            toast(
                if (positives.size < 3 || negatives.isEmpty())
                    "Faltam amostras: três positivas reconhecidas e uma negativa."
                else "Uma negativa disparou como a frase. Recomece as amostras ou troque a frase."
            )
            return@button
        }
        store.put("threshold", threshold.toString())
        store.flag("calibrated", true)
        stopListening()
        toast("Ajuste local salvo. FAR e FRR ainda precisam de validação real.")
        navigate("home")
    }
    body.addView(
        text(
            "Este ajuste não certifica segurança nem estima FAR/FRR. As amostras são texto temporário em memória; áudio não é salvo. Sair do aplicativo limpa as amostras.",
            13,
            muted,
        )
    )
}

private fun MainActivity.captureSample(positive: Boolean) {
    if (
        !LiveState.active ||
            LiveState.lastHeard.isBlank() ||
            capturedSerial == LiveState.sampleSerial
    ) {
        toast("Fale novamente e aguarde o resultado antes de guardar.")
        return
    }
    // No teste, a Sentinela devolve a própria frase só quando a reconheceu (VoskPipeline).
    val recognized = PhraseMatcher.recognized(store.phrase(), LiveState.lastHeard)
    if (positive && !recognized) {
        toast("A Sentinela não reconheceu esta leitura. Fale a frase de novo; nada foi guardado.")
        return
    }
    capturedSerial = LiveState.sampleSerial
    if (positive) {
        if (positives.size < 3) positives += LiveState.lastHeard
    } else {
        negatives += LiveState.lastHeard
        if (recognized)
            toast("Esta frase parecida disparou: seria um falso alerta. Recomece as amostras ou troque a Frase de Segurança.")
    }
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
        "Use apenas participantes que aceitaram o teste. Nenhum contato é importado da agenda.",
    )
    val contacts = JSONArray(store.get("contacts", "[]"))
    for (i in 0 until contacts.length()) {
        val c = contacts.getJSONObject(i)
        card {
            addView(text(c.getString("name"), 18, ink, true))
            addView(text(c.getString("phone"), 14, muted))
            button("Remover", this, true) {
                val next = JSONArray()
                for (j in 0 until contacts.length()) if (j != i) next.put(contacts.get(j))
                store.put("contacts", next.toString())
                render()
            }
        }
    }
    if (contacts.length() < 3) {
        val name = input("Nome ou apelido")
        val phone = input("Telefone com DDD e país")
        button("Adicionar participante", secondary = true) {
            val n = name.text.toString().trim()
            val p = phone.text.toString().replace(Regex("[\\s()-]"), "")
            if (n.isBlank() || n.length > 60 || !p.matches(Regex("\\+?[0-9]{10,15}"))) {
                toast("Informe nome e telefone válidos.")
                return@button
            }
            contacts.put(JSONObject().put("name", n).put("phone", p))
            store.put("contacts", contacts.toString())
            render()
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
