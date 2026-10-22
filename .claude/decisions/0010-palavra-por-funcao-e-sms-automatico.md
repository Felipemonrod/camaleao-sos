---
data: 2026-10-22
status: aceito
---

# 0010 — Palavra por função na janela de comando e SMS automático à Rede de Apoio

## Contexto

O protótipo tinha um único comando de voz (`enviar alerta`) e a comunicação com a Rede
de Apoio era um compositor de SMS **manual**, que dependia de a usuária revisar e
tocar em enviar. O responsável pediu três funções — avisar contato de emergência,
compartilhar localização e gravar áudio — e que cada uma pudesse ser ligada ou
desligada e acionada por uma palavra escolhida. Duas formas de acionamento foram
oferecidas: palavra por função dentro da janela de comando, ou uma frase de segurança
por função. A segunda multiplica frases sempre ouvidas e, com elas, o falso positivo
([R7](../../docs/10-riscos-e-questoes-abertas.md)). Para o canal, foram oferecidos SMS
automático, SMS + Painel e só Painel.

## Decisão

O responsável escolheu **palavra por função** e **SMS automático**.

- A Frase de Segurança continua única. Depois dela, na `COMMAND_WINDOW` de 6 s, a
  usuária diz a palavra de uma função **ligada**. Funções desligadas não entram na
  gramática. `cancelar` é fixo e nunca pode ser palavra de função (RF-38).
- Funções: **alerta** (Ocorrência + SMS a toda a Rede de Apoio, com link de mapa
  quando há posição), **localização** (SMS com a posição, repetido a cada 5 min
  enquanto armada) e **gravação** (áudio cifrado no aparelho). Alerta vem ligado;
  as outras, desligadas.
- O SMS de alerta espera a mesma preparação local da Ocorrência e **não sai** se ela
  for cancelada na Área protegida ([ADR 0009](0009-notificacao-e-cancelamento-protegido.md)).
- O registro de SMS só diz "aceito pela operadora" quando o Android devolve sucesso no
  `sentIntent`. Entrega e leitura não são afirmadas ([ADR 0006](0006-maquina-de-estados-do-alerta.md)).
- O código SOS digitado na Fachada e a Ocorrência manual convergem para a mesma função
  de alerta (RF-32).
- Para o protótipo do hackathon, o responsável **adiou a avaliação jurídica** da
  gravação: a função existe, desligada por padrão (RF-46). Isso **não** fecha
  [Q3](../../docs/10-riscos-e-questoes-abertas.md) nem [J1](../../docs/07-privacidade-lgpd.md).

## Consequências

**Resolve:** a Rede de Apoio é avisada sem depender do Painel nem de internet —
basta sinal de celular. A usuária escolhe palavras discretas e só as funções que quer.

**Custa:** o SMS enviado por um aplicativo fica na caixa de **enviados** do aparelho e
pode ser visto por quem pegar o celular ([C4](../../docs/02-modelo-de-ameaca.md)). Foi
observado no teste; ver [docs/07](../../docs/07-privacidade-lgpd.md#controles-entregues-e-limites-pendentes).
O envio depende de plano com SMS e da operadora: no primeiro aparelho real a rede
recusou o envio ([registro](../../docs/09-plano-de-testes.md#registro-de-execução)).

**Custa:** ⚠️ **Não verificado:** o Google Play restringe apps que pedem `SEND_SMS`
fora do papel de app de SMS padrão. Verificar na política de permissões de SMS do
Play Console antes de qualquer distribuição em loja; soma-se a
[R5](../../docs/10-riscos-e-questoes-abertas.md). O protótipo é instalação direta.

**Custa:** cada palavra a mais na janela é mais uma chance de a fala comum virar
função. Mitigado por: só funções ligadas na gramática, palavras não podem repetir a
frase nem parecer entre si, e checagem de vocabulário do modelo
([docs/06](../../docs/06-reconhecimento-de-voz.md#ajuste-medido-do-estágio-1-e-das-palavras)).
