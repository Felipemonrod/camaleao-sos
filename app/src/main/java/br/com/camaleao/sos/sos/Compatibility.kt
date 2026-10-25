package br.com.camaleao.sos.sos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.telephony.TelephonyManager
import br.com.camaleao.sos.domain.InstallSource

/**
 * Diagnóstico de compatibilidade do aparelho (RF-72). Instalado fora da loja, o Android
 * 13+ trata SMS e telefone como **configuração restrita**: a permissão aparece acinzentada
 * e só é liberada em "Informações do app → ⋮ → Permitir configurações restritas". Alguns
 * fabricantes (Xiaomi/HyperOS, Motorola) ainda têm uma camada própria por cima disso.
 *
 * Sem este relatório o app falha em silêncio num aparelho alheio, e quem testa conclui
 * que o Camaleão não funciona — quando o que falta é um botão escondido do sistema.
 */
object Compatibility {

    enum class State {
        OK,
        FALTA,
        AUSENTE,
    }

    data class Item(val title: String, val state: State, val fix: String)

    private fun granted(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun telephony(context: Context) =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    /** Há chip pronto para uso? `SIM_STATE_READY` é o único estado que serve. */
    fun simReady(context: Context): Boolean =
        runCatching {
            context.getSystemService(TelephonyManager::class.java).simState ==
                TelephonyManager.SIM_STATE_READY
        }
            .getOrDefault(false)

    /** Instalado fora da loja: é o caso em que o Android esconde SMS e telefone. */
    fun sideloaded(context: Context): Boolean =
        runCatching {
            val installer =
                if (Build.VERSION.SDK_INT >= 30)
                    context.packageManager.getInstallSourceInfo(context.packageName)
                        .installingPackageName
                else null
            InstallSource.sideloaded(installer)
        }
            .getOrDefault(true)

    /** Xiaomi, Redmi e POCO têm uma segunda camada de permissões, fora da do Android. */
    fun xiaomi() = Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco")

    // A permissão do Android pode estar concedida e o MIUI/HyperOS ainda barrar o envio
    // em segundo plano: o SMS sai do app e aparece como "não enviada" no sistema.
    const val MIUI_SMS =
        "No Xiaomi, confira também Configurações → Apps → Cálculos → Outras permissões → " +
            "Enviar SMS: Permitir (não \"Perguntar\"). Reinstalar o app pode voltar essa opção para Perguntar."

    const val RESTRICTED =
        "Abra Informações do app → ⋮ (canto superior direito) → " +
            "\"Permitir configurações restritas\" e só então conceda a permissão. " +
            "A opção costuma aparecer somente depois de a permissão ter sido negada uma vez."

    fun report(context: Context): List<Item> {
        val items = mutableListOf<Item>()
        val hasTelephony = telephony(context)

        items +=
            Item(
                "Microfone",
                if (granted(context, Manifest.permission.RECORD_AUDIO)) State.OK else State.FALTA,
                "Sem ele não há Sentinela. Conceda em Permissões → Microfone.",
            )

        items +=
            if (!hasTelephony)
                Item(
                    "SMS",
                    State.AUSENTE,
                    "Este aparelho não tem telefonia: nenhum SMS sai daqui. " +
                        "Use o Painel ou um aparelho com chip.",
                )
            else if (granted(context, Manifest.permission.SEND_SMS))
                Item(
                    "SMS",
                    State.OK,
                    listOfNotNull(
                            if (simReady(context)) null else "Sem chip pronto: o envio vai falhar na operadora.",
                            if (xiaomi()) MIUI_SMS else null,
                        )
                        .joinToString(" "),
                )
            else
                Item(
                    "SMS",
                    State.FALTA,
                    if (sideloaded(context)) RESTRICTED
                    else "Conceda em Permissões → SMS.",
                )

        items +=
            if (!hasTelephony)
                Item("Ligação", State.AUSENTE, "Sem telefonia: a função de ligação não funciona neste aparelho.")
            else if (granted(context, Manifest.permission.CALL_PHONE))
                Item("Ligação", State.OK, "")
            else
                Item(
                    "Ligação",
                    State.FALTA,
                    if (sideloaded(context)) RESTRICTED else "Conceda em Permissões → Telefone.",
                )

        items +=
            Item(
                "Localização",
                if (granted(context, Manifest.permission.ACCESS_FINE_LOCATION)) State.OK
                else State.FALTA,
                "Sem ela o alerta sai sem posição. Conceda em Permissões → Local.",
            )

        if (Build.VERSION.SDK_INT >= 33)
            items +=
                Item(
                    "Notificação",
                    if (granted(context, "android.permission.POST_NOTIFICATIONS")) State.OK
                    else State.FALTA,
                    "O Android exige notificação visível para a escuta continuar.",
                )

        val power = context.getSystemService(PowerManager::class.java)
        items +=
            Item(
                "Economia de bateria",
                if (power.isPowerSaveMode) State.FALTA else State.OK,
                "Ligada, o fabricante pode interromper a escuta. Desligue para a demonstração.",
            )

        return items
    }

    /** Texto pronto para a caixa de diálogo: só o que está errado ganha explicação. */
    fun text(context: Context): String {
        val items = report(context)
        val marca = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"
        val linhas =
            items.joinToString("\n") {
                val marcador =
                    when (it.state) {
                        State.OK -> "OK"
                        State.FALTA -> "FALTA"
                        State.AUSENTE -> "NÃO EXISTE"
                    }
                if (it.state == State.OK && it.fix.isBlank()) "$marcador · ${it.title}"
                else "$marcador · ${it.title}\n   ${it.fix}"
            }
        return "$marca\n\n$linhas"
    }

    /** Há algo que impeça o aparelho de cumprir o que as funções ligadas prometem? */
    fun blocking(context: Context) = report(context).any { it.state != State.OK }
}
