package br.com.camaleao.sos.sos

import android.content.Context
import br.com.camaleao.sos.LiveState
import br.com.camaleao.sos.domain.AlertMessage
import br.com.camaleao.sos.domain.VoiceAction
import br.com.camaleao.sos.domain.VoiceCommand
import br.com.camaleao.sos.sentinela.LocationProbe
import java.util.concurrent.Executors

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
                        EmergencySms.send(
                            app,
                            store,
                            "alerta",
                            AlertMessage.alert(store.get("sms_text", AlertMessage.DEFAULT), position),
                        )
                    }
                },
                maxOf(0L, due - System.currentTimeMillis()),
            )
        }
        return id
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
