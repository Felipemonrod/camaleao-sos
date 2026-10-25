package br.com.camaleao.sos.sos

import android.content.Context
import br.com.camaleao.sos.LiveState
import br.com.camaleao.sos.domain.AlertMessage
import br.com.camaleao.sos.domain.VoiceAction
import br.com.camaleao.sos.domain.VoiceCommand
import br.com.camaleao.sos.sentinela.LocationProbe
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Funções escolhidas pela usuária. Todos os Gatilhos de alerta convergem aqui (RF-32). */
object Actions {
    private val worker = Executors.newSingleThreadExecutor()
    const val UPDATE_MS = 5 * 60_000L

    fun commands(store: Store): List<VoiceCommand> =
        VoiceAction.entries.map {
            VoiceCommand(
                it,
                store.get("cmd_${it.key}_words", it.defaultWords),
                store.get("cmd_${it.key}_on", (it == VoiceAction.ALERT).toString()) == "true",
            )
        }

    fun enabled(store: Store, action: VoiceAction) =
        commands(store).first { it.action == action }.enabled

    fun recordMinutes(store: Store) = store.get("record_minutes", "5").toIntOrNull()?.coerceIn(1, 30) ?: 5

    fun locationUpdates(store: Store) =
        store.get("location_updates", "3").toIntOrNull()?.coerceIn(0, 12) ?: 3

    /**
     * Cria a Ocorrência e, se a função de alerta estiver ligada, avisa a Rede de Apoio
     * por SMS com a posição. O SMS espera a mesma preparação local da Ocorrência e não
     * sai se ela for cancelada na Área protegida (ADR 0009).
     */
    fun alert(context: Context, origin: String): String? {
        val app = context.applicationContext
        val store = Store(app)
        val id = Protocol.create(app, origin, false) ?: return null
        if (!enabled(store, VoiceAction.ALERT)) return id
        LocationProbe.request(app, store, 10_000) { position ->
            val due = store.record(id)?.optLong("due") ?: 0L
            LiveState.main.postDelayed(
                {
                    worker.execute {
                        if (store.record(id)?.optBoolean("cancelled") == true) return@execute
                        val sent =
                            EmergencySms.send(
                                app,
                                store,
                                "alerta",
                                AlertMessage.alert(store.get("sms_text", AlertMessage.DEFAULT), position),
                            )
                        if (sent > 0) repeatAlert(app, store, id, 1)
                    }
                },
                maxOf(0L, due - System.currentTimeMillis()),
            )
        }
        return id
    }

    /** Repetições do SMS de alerta: a cada 5 min por 30 min, ou até desarmar. */
    const val ALERT_REPEATS = 6

    /** Repete o alerta com a posição atual, para quem não viu o primeiro SMS. */
    private fun repeatAlert(app: Context, store: Store, id: String, n: Int) {
        if (n > ALERT_REPEATS) return
        LiveState.main.postDelayed(
            {
                if (LiveState.active && store.record(id)?.optBoolean("cancelled") != true)
                    LocationProbe.request(app, store, 10_000) { position ->
                        worker.execute {
                            EmergencySms.send(
                                app,
                                store,
                                "alerta $n/$ALERT_REPEATS",
                                AlertMessage.repeat(store.get("sms_text", AlertMessage.DEFAULT), position, n, ALERT_REPEATS),
                            )
                        }
                        repeatAlert(app, store, id, n + 1)
                    }
            },
            UPDATE_MS,
        )
    }

    private val calling = AtomicBoolean(false)

    /**
     * Liga para a Rede de Apoio. Com o Painel no ar, ele liga e fala o aviso, e o aparelho
     * fica quieto. Sem Painel, avisa por SMS e liga daqui em linha aberta (OpenLineCall).
     */
    fun call(context: Context) {
        val app = context.applicationContext
        val store = Store(app)
        if (!enabled(store, VoiceAction.CALL) || !store.flag("share_consent")) return
        if (!calling.compareAndSet(false, true)) return
        OpenLineCall.begin()
        LocationProbe.request(app, store, 10_000) { position ->
            Thread {
                    try {
                        val phones =
                            EmergencySms.contacts(store).map {
                                it.getString("phone").filterIndexed { i, ch -> ch.isDigit() || (i == 0 && ch == '+') }
                            }
                        if (phones.isEmpty()) return@Thread
                        val text = store.get("sms_text", AlertMessage.DEFAULT)
                        if (PanelTransport(store).call(phones, AlertMessage.spoken(text, position))) {
                            EmergencySms.note(store, "ligação", "Rede de Apoio", "PEDIDA AO PAINEL")
                            EmergencySms.send(app, store, "ligação", AlertMessage.alert(text, position))
                        } else {
                            EmergencySms.send(app, store, "ligação", AlertMessage.openLine(position))
                            OpenLineCall.run(app, store)
                        }
                    } finally {
                        calling.set(false)
                    }
                }
                .start()
        }
    }

    /** Envia a posição agora e repete a cada 5 min enquanto a Sentinela estiver armada. */
    fun shareLocation(context: Context, update: Int = 0) {
        val app = context.applicationContext
        val store = Store(app)
        if (!enabled(store, VoiceAction.LOCATION)) return
        LocationProbe.request(app, store) { position ->
            worker.execute {
                EmergencySms.send(app, store, "localização", AlertMessage.location(position, update))
            }
            if (update < locationUpdates(store))
                LiveState.main.postDelayed(
                    { if (LiveState.active) shareLocation(app, update + 1) },
                    UPDATE_MS,
                )
        }
    }
}
