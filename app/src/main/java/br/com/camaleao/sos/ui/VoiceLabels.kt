package br.com.camaleao.sos.ui

import br.com.camaleao.sos.LiveState

internal fun voiceStatus(): String =
    when (LiveState.voicePhase) {
        "COMMAND_WINDOW" -> "Aguardando comando por até 6 segundos"
        "SOS_EVENT" -> "Preparando Ocorrência"
        else -> "Aguardando Frase de Segurança"
    }
