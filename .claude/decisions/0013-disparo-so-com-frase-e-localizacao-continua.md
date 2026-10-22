---
data: 2026-10-22
status: aceito, condicionado à medição de bateria e falso alarme
---

# 0013 — Disparo só com a frase, localização contínua armada e alerta repetido

> Acrescenta opções ao [ADR 0010](0010-palavra-por-funcao-e-sms-automatico.md) e
> limites ao [ADR 0012](0012-ligacao-pelo-painel-e-linha-aberta.md). Abre exceção ao
> RF-40 ("sem rastreamento contínuo") enquanto a Sentinela está armada.

## Contexto

Teste do responsável no S25, em 22/10/2026. A frase foi reconhecida, mas nada disparou
porque a palavra da função não veio dentro da janela. O responsável pediu três coisas:
que o usuário escolha disparar só com a frase; que a posição fique ativa o tempo todo
com a Sentinela armada; e que o SMS se repita até alguém ver. Também pediu escolher o
contato na agenda. Um número digitado com código de operadora (o "15") ou sem o nono
dígito ligaria para o número errado. No mesmo teste, as ligações em linha aberta não
pararam depois de desarmar nem de excluir o contato.

## Decisão

1. **Modo de disparo à escolha**, em Funções: "frase + palavra" (padrão) ou "só a
   frase", que aciona todas as funções ligadas de uma vez, primeiro o alerta.
2. **Localização contínua enquanto armada:** `fused` a cada 30 s com qualidade
   equilibrada (Wi-Fi e células), só na memória. Sai do aparelho apenas no alerta ou na
   função de localização, como antes.
3. **Alerta repetido:** depois do primeiro SMS, mais 6 com a posição do momento, a cada
   5 min (30 min no total), parando ao desarmar ou cancelar a Ocorrência.
4. **Contato pela agenda:** seletor do sistema, sem a permissão `READ_CONTACTS`. O
   número é gravado como `+55DDDNÚMERO`. Saem o 0, o código de operadora, o 55 e o 0055,
   e celular de 8 dígitos ganha o 9. O app mostra o número final para conferir antes de
   gravar.
5. **Ligação que para:** os contatos são relidos antes de cada chamada. Desarmar,
   excluir o último contato ou tocar em "Parar ligações" encerra as novas tentativas. Há
   também um teto de 30 min. A chamada em curso só termina na tela de chamada.

## Consequências

- "Só a frase" troca segurança por simplicidade: um falso reconhecimento envia SMS e
  liga. A calibração com negativa
  ([ADR 0011](0011-estagio-1-com-iscas-e-segunda-leitura.md)) é a única barreira.
- A posição contínua gasta bateria enquanto armada. A medição de 1 h fora do carregador
  continua pendente (docs/09).
- O ADR 0012 pedia insistir até alguém atender. Agora há teto de 30 min: depois disso,
  só um novo alerta liga de novo.
- Um número de 10 dígitos digitado errado pode ser lido como outro DDD. A confirmação
  com o número formatado é a defesa.
