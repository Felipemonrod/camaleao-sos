---
data: 2026-10-25
status: aceito
---

# 0016 — Silêncio do sistema vira falha, e o SMS ganha plano B

> Acrescenta ao [ADR 0010](0010-palavra-por-funcao-e-sms-automatico.md) o que fazer
> quando o aparelho aceita o envio e não entrega nada. Não altera o RF-57: entrega ao
> destinatário continua não sendo afirmada.

## Contexto

Num Redmi Note 12 (HyperOS OS2.0, Android 15), nenhum SMS automático do Camaleão saía,
enquanto ligação e envio manual funcionavam. A investigação descartou, com evidência:

- **chip e operadora** — o mesmo aparelho enviava por outros caminhos;
- **dois chips / chip padrão** — há um chip só, com `defaultSmsSubId` válido;
- **permissão do Android** — `SEND_SMS` concedida, appop em `allow`;
- **regressão de versão** — o caminho de envio é byte a byte idêntico da 0.3.0 à 0.6.0,
  e só a 0.6.1 acrescenta a escolha de chip.

O que fechou foi uma sonda isolada (`tools/sms-probe/`): um aplicativo de 60 linhas, sem
nada do Camaleão, que só chama `SmsManager.sendTextMessage`. Nele, três vezes seguidas:
a chamada retorna sem exceção, o sistema **contabiliza o appop como permitido**, e depois
disso **nada acontece** — nenhum resultado no `sentIntent`, nenhuma linha na pilha de SMS,
nenhuma transmissão.

O bloqueio está abaixo da camada de permissão e é invisível ao aplicativo. Não há API
que o detecte antes de tentar.

## Decisão

1. **Silêncio é falha.** Passados 30 s sem resposta do `sentIntent`, o registro que
   estava `PENDENTE` vira `FALHOU: sem resposta do sistema em 30 s — o aparelho pode
   estar bloqueando o envio automático`. Nada fica `PENDENTE` para sempre.
2. **O caminho manual deixa de ser detalhe.** Havendo falha no registro, a Ocorrência
   passa a mostrar, em destaque, o botão **"Enviar pelo aplicativo de mensagens"**, que
   abre o aplicativo padrão com o texto pronto — o caminho que funciona nos aparelhos
   onde o automático é barrado.

## Consequências

- **A promessa do produto muda, e para menor.** Deixa de ser "o alerta sai sozinho" e
  passa a ser "sai sozinho, e quando o aparelho não deixar, você envia em um toque". É
  pior de vender e é verdade. Um `PENDENTE` eterno se parece com sucesso, e parecer
  proteção sem proteger é o que a primeira regra do produto proíbe.
- O plano B exige a usuária tocar na tela — exatamente o que a Fachada existe para
  evitar. Não é substituto do automático: é o que resta quando o automático não existe
  naquele aparelho. A tela diz isso com todas as letras.
- 30 s é escolha sem medição: é o que pareceu tolerável entre "falso alarme de falha" e
  "demora até a usuária saber". ⚠️ Não foi medido quanto tempo o `sentIntent` leva num
  aparelho que funciona.
- A sonda fica versionada. Ela respondeu em dez minutos o que três horas de teste pela
  interface não responderam, e serve para o próximo aparelho que der problema.
- Continua **sem solução** o caso de fundo: nestes aparelhos o alerta automático por SMS
  não funciona, e o Camaleão não tem como saber disso antes de tentar.
