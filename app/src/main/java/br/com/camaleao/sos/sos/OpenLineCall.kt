package br.com.camaleao.sos.sos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.telecom.TelecomManager
import br.com.camaleao.sos.LiveState

/**
 * Ligação em linha aberta, usada quando o Painel não pode ligar. Um app comum não põe
 * áudio dentro da chamada nem sabe quando ela foi atendida: aqui o microfone fica aberto,
 * o volume da chamada vai ao mínimo e uma ligação que dura [CONNECTED_MS] conta como
 * atendida, o que inclui caixa postal. A tela de chamada aparece (exceção ao RF-47).
 */
object OpenLineCall {
    const val CONNECTED_MS = 40_000L
    private const val START_MS = 10_000L
    private const val LIMIT_MS = 15 * 60_000L
    private const val GAP_MS = 10_000L

    /** Bloqueia a thread que chamou. Liga em rodadas enquanto a Sentinela estiver armada. */
    fun run(context: Context, store: Store) {
        val contacts = EmergencySms.contacts(store)
        if (contacts.isEmpty()) return
        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            EmergencySms.note(store, "ligação", "Rede de Apoio", "FALHOU: sem permissão de ligação")
            return
        }
        val audio = context.getSystemService(AudioManager::class.java)
        val telecom = context.getSystemService(TelecomManager::class.java)
        val volume = audio.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
        try {
            var round = 0
            while (LiveState.active) {
                round++
                for (c in contacts) {
                    if (!LiveState.active) return
                    val name = c.getString("name")
                    quiet(audio)
                    // placeCall não abre Activity do app: funciona com a Fachada em segundo plano.
                    telecom.placeCall(
                        Uri.fromParts("tel", c.getString("phone"), null),
                        Bundle().apply {
                            putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)
                        },
                    )
                    val ms = duration(audio)
                    when {
                        ms == null -> {
                            EmergencySms.note(store, "ligação", name, "FALHOU: chamada não detectada")
                            return
                        }
                        ms >= CONNECTED_MS -> {
                            EmergencySms.note(store, "ligação", name, "DUROU ${ms / 1000} s (pode ser caixa postal)")
                            return
                        }
                        else -> EmergencySms.note(store, "ligação", name, "SEM RESPOSTA (rodada $round)")
                    }
                }
                SystemClock.sleep(GAP_MS)
            }
        } catch (e: SecurityException) {
            EmergencySms.note(store, "ligação", "Rede de Apoio", "FALHOU: ${e.javaClass.simpleName}")
        } finally {
            runCatching { audio.setStreamVolume(AudioManager.STREAM_VOICE_CALL, volume, 0) }
        }
    }

    private fun quiet(audio: AudioManager) {
        // O Android limita ao mínimo do aparelho, que pode não ser mudo.
        runCatching { audio.setStreamVolume(AudioManager.STREAM_VOICE_CALL, 0, 0) }
    }

    /** Duração da chamada em ms, ou null se o modo de chamada nunca apareceu. */
    private fun duration(audio: AudioManager): Long? {
        val asked = SystemClock.elapsedRealtime()
        while (audio.mode != AudioManager.MODE_IN_CALL) {
            if (SystemClock.elapsedRealtime() - asked > START_MS) return null
            SystemClock.sleep(250)
        }
        val start = SystemClock.elapsedRealtime()
        quiet(audio)
        while (audio.mode == AudioManager.MODE_IN_CALL && SystemClock.elapsedRealtime() - start < LIMIT_MS)
            SystemClock.sleep(500)
        return SystemClock.elapsedRealtime() - start
    }
}
