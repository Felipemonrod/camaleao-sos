package br.com.camaleao.sos.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.CheckBox
import android.widget.EditText
import br.com.camaleao.sos.LiveState
import br.com.camaleao.sos.domain.AlertMessage
import br.com.camaleao.sos.domain.VoiceAction
import br.com.camaleao.sos.domain.VoiceCommand
import br.com.camaleao.sos.domain.VoiceCommands
import br.com.camaleao.sos.sentinela.EvidenceRecorder
import br.com.camaleao.sos.sentinela.Vocabulary
import br.com.camaleao.sos.sos.Actions
import br.com.camaleao.sos.sos.EmergencySms

internal fun MainActivity.functions() {
    heading(
        "Palavras e funções.",
        "Depois da Frase de Segurança há ${VOICE_WINDOW_S} segundos para dizer a palavra de uma função. Só funções ligadas são ouvidas.",
    )
    val toggles = mutableMapOf<VoiceAction, CheckBox>()
    val fields = mutableMapOf<VoiceAction, EditText>()
    Actions.commands(store).forEach { c ->
        card {
            addView(text(c.action.label, 18, ink, true))
            addView(text(description(c.action), 13, muted))
            gap(this, 8)
            toggles[c.action] =
                CheckBox(this@functions).apply {
                    text = "Ligada"
                    setTextColor(ink)
                    isChecked = c.enabled
                    addView(this)
                }
            fields[c.action] =
                EditText(this@functions).apply {
                    hint = "Palavra ou expressão"
                    setText(c.words)
                    setTextColor(ink)
                    isSingleLine = true
                    background = bg(android.graphics.Color.rgb(244, 247, 242), 12)
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    addView(this)
                }
        }
    }
    body.addView(text("Mensagem do SMS de alerta", 15, ink, true))
    val message = input("Mensagem", store.get("sms_text", AlertMessage.DEFAULT), multiline = true)
    val minutes = input("Minutos de gravação · 1 a 30", Actions.recordMinutes(store).toString())
    val updates = input("Atualizações de localização a cada 5 min · 0 a 12", Actions.locationUpdates(store).toString())
    body.addView(
        text(
            "Cancelar é sempre “${VoiceCommands.CANCEL}”. As palavras não podem repetir a frase nem parecer umas com as outras.",
            13,
            muted,
        )
    )
    button("Salvar funções") {
        if (LiveState.active || LiveState.preparing) {
            toast("Desarme antes de mudar as funções.")
            return@button
        }
        val next =
            VoiceAction.entries.map {
                VoiceCommand(it, fields.getValue(it).text.toString().trim(), toggles.getValue(it).isChecked)
            }
        VoiceCommands.problem(store.phrase(), next)?.let {
            toast(it)
            return@button
        }
        if (next.none { it.enabled }) {
            toast("Ligue pelo menos uma função.")
            return@button
        }
        withVocabulary(next.filter { it.enabled }.map { it.words }) { saveFunctions(next, message.text.toString(), minutes.text.toString(), updates.text.toString()) }
    }
    button("Voltar à Sentinela", secondary = true) { navigate("home") }
}

/** Confere no modelo se todas as palavras existem; só então executa [then]. */
internal fun MainActivity.withVocabulary(texts: List<String>, then: () -> Unit) {
    toast("Conferindo as palavras no modelo de voz…")
    executor.execute {
        val missing = runCatching { Vocabulary.missing(this, texts) }
        runOnUiThread {
            missing
                .onSuccess {
                    if (it.isEmpty()) then()
                    else toast("O modelo de voz não conhece: ${it.joinToString()}. Troque essas palavras.")
                }
                .onFailure { toast("Não foi possível conferir o vocabulário. Tente novamente.") }
        }
    }
}

private fun MainActivity.saveFunctions(next: List<VoiceCommand>, message: String, minutes: String, updates: String) {
    next.forEach {
        store.put("cmd_${it.action.key}_words", it.words)
        store.put("cmd_${it.action.key}_on", it.enabled.toString())
    }
    store.put("sms_text", message.trim().take(160))
    store.put("record_minutes", (minutes.toIntOrNull() ?: 5).coerceIn(1, 30).toString())
    store.put("location_updates", (updates.toIntOrNull() ?: 3).coerceIn(0, 12).toString())
    val needed = mutableListOf<String>()
    if (next.any { it.enabled && it.action != VoiceAction.RECORD })
        needed += Manifest.permission.SEND_SMS
    if (next.any { it.enabled && it.action != VoiceAction.RECORD } && store.flag("location_consent"))
        needed += listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    if (next.any { it.enabled && it.action == VoiceAction.CALL }) needed += Manifest.permission.CALL_PHONE
    val missing = needed.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 4)
    toast("Funções salvas. Teste com a frase antes de depender delas.")
    navigate("home")
}

private fun description(a: VoiceAction) =
    when (a) {
        VoiceAction.ALERT ->
            "Cria a Ocorrência e envia SMS a todos os participantes, com link de mapa se houver posição. Sai depois da preparação de 5 s, que pode ser cancelada aqui."
        VoiceAction.LOCATION ->
            "Envia SMS com a posição atual e repete a cada 5 minutos enquanto a Sentinela estiver armada."
        VoiceAction.CALL ->
            "Pede ao Painel que ligue com voz gravada. Sem Painel, avisa por SMS e liga daqui com o volume no mínimo, mostrando a tela de chamada. Repete até a ligação durar 40 s ou a Sentinela ser desarmada."
        VoiceAction.RECORD ->
            "Grava o áudio do microfone já em uso, cifrado neste aparelho, pelo tempo escolhido. Desligada por padrão."
    }

/** Gravações e envios de SMS, mostrados na tela de Ocorrências. */
internal fun MainActivity.evidenceAndSms() {
    val log = EmergencySms.log(store).reversed()
    if (log.isNotEmpty())
        card {
            addView(text("SMS para a Rede de Apoio", 18, ink, true))
            addView(
                text(
                    "“Aceito pela operadora” não confirma entrega nem leitura.",
                    13,
                    muted,
                )
            )
            log.take(10).forEach {
                gap(this, 6)
                addView(text("${it.getString("at").take(19)} · ${it.getString("reason")} · ${it.getString("to")}\n${it.getString("status")}", 14, ink))
            }
            button("Limpar registro de SMS", this, true) {
                EmergencySms.clear(store)
                render()
            }
        }
    if (LiveState.recording)
        button("Parar gravação agora") {
            LiveState.stopRecording = true
            toast("Gravação será encerrada.")
        }
    EvidenceRecorder.list(store).forEach { e ->
        card {
            addView(text("Gravação · ${e.getString("state")}", 18, ink, true))
            addView(
                text(
                    "${e.getString("created_at").take(19)} · ${e.optLong("duration_s")} s · origem ${e.getString("origin")}\nSHA-256 original: ${e.optString("original_sha256").take(16)}…\nCifrada neste aparelho. Integridade não é validade judicial.",
                    13,
                    muted,
                )
            )
            if (e.getString("state") != "GRAVANDO") {
                button("Exportar áudio (WAV)", this, true) {
                    confirm(
                        "Exportar gravação decifrada?",
                        "O arquivo exportado fica sem cifragem no destino escolhido. Escolha um local seguro.",
                    ) {
                        pendingExport = e.getString("id")
                        startActivityForResult(
                            Intent(Intent.ACTION_CREATE_DOCUMENT)
                                .setType("audio/wav")
                                .putExtra(Intent.EXTRA_TITLE, "registro-${e.getString("created_at").take(10)}.wav"),
                            71,
                        )
                    }
                }
                button("Excluir gravação", this, true) {
                    confirm("Excluir gravação?", "O arquivo cifrado e sua chave serão apagados deste aparelho.") {
                        EvidenceRecorder.delete(this@evidenceAndSms, store, e.getString("id"))
                        render()
                    }
                }
            }
        }
    }
}

internal const val VOICE_WINDOW_S = 6
