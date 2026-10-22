---
data: 2026-10-22
status: aceito, condicionado ao teste no S25 e no aparelho de discagem
---

# 0012 — Ligação pelo Painel com voz e, sem Painel, linha aberta no aparelho

> Acrescenta uma função ao [ADR 0010](0010-palavra-por-funcao-e-sms-automatico.md).
> Abre uma exceção ao RF-47 só para essa função, que vem desligada.

## Contexto

Um colega não conseguiu enviar SMS num Xiaomi com o APK da página de demonstração: a
permissão de SMS é restrita para app instalado fora da loja, e o MIUI/HyperOS tem uma
camada própria de permissões. A proposta foi ligar para a Rede de Apoio com uma voz
sintetizada avisando do risco, o celular mudo e novas tentativas até alguém atender.

Num app comum isso não fecha, por três limites:

| Limite | Efeito |
|---|---|
| Pôr áudio dentro da chamada exige `MODIFY_PHONE_STATE`, de app de sistema ([commit AOSP](https://gerrit.omnirom.org/plugins/gitiles/android_frameworks_av/+/766fb020a50b64bf9a808bd80df60c54c2ca8cc3)) | a voz só chegaria tocada no alto-falante, e o agressor ouviria |
| O estado "atendida" exige `READ_PRECISE_PHONE_STATE`, também privilegiada | o app não sabe se alguém atendeu |
| A chamada abre a tela de ligação do sistema | quebra a Fachada, contra o RF-47 |

Não há prazo para um provedor de telefonia pago (Twilio e afins) antes da apresentação.

## Decisão

1. **Função própria `ligar`** (palavra padrão "chamar contato"), **desligada por
   padrão**. O alerta silencioso continua sem ligação.
2. **Com Painel alcançável (caminho B):** o aparelho manda ao Painel um pedido assinado
   com a chave do dispositivo, com os telefones e o texto. O Painel liga por um Android
   de discagem ligado por USB ao notebook (`adb`), lê o estado preciso da chamada no
   `dumpsys telephony.registry` (Foreground call state 1 = ACTIVE) e fala o aviso pelos
   alto-falantes do notebook, ao lado desse aparelho. Liga em rodadas por todos os
   contatos até alguém atender ou o Painel cancelar. O celular da usuária não muda nada.
3. **Sem Painel (caminho A):** SMS avisando "vou ligar e ficar em silêncio" com a
   posição, depois `TelecomManager.placeCall` com o volume de chamada no mínimo e o
   microfone aberto. Ligação que dura 40 s conta como atendida. Repete enquanto a
   Sentinela estiver armada.
4. Os telefones vão ao Painel só no pedido e ficam na memória do discador, nunca no
   banco cifrado. O Painel mostra só os quatro últimos dígitos.

## Consequências

- Mesmo funcionamento sem Twilio, com custo físico: o caminho B exige um segundo
  Android com chip e créditos, um cabo e o notebook com som ligado.
- **Caixa postal conta como atendida** nos dois caminhos. A telefonia não distingue.
- No caminho A a tela de chamada aparece, o volume mínimo pode não ser mudo, e durante
  a chamada a Sentinela perde o microfone: "cancelar" por voz não funciona até a
  chamada acabar. Desarmar pela tela interrompe as novas tentativas.
- O caminho A também depende de permissão: `CALL_PHONE` e, no Xiaomi, a permissão
  própria do sistema. Falha sem permissão fica registrada como `FALHOU`.
- Telefones da Rede de Apoio passam a sair do aparelho quando o Painel existe
  ([docs/07](../../docs/07-privacidade-lgpd.md)).
- ⚠️ **Não verificado:** que `AudioManager.getMode()` devolve `MODE_IN_CALL` para um app
  comum durante chamada de celular no Android 16 do S25. Se não devolver, o caminho A
  registra "chamada não detectada" e para depois da primeira ligação. Verificar no S25
  (docs/09).
