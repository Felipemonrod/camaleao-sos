---
data: 2026-10-21
status: aceito
---

# 0006 — O alerta tem quatro estados, e a interface nunca mente sobre eles

> Decisão trazida da [análise sherpa+Vosk](../../referencias/analise-sherpa-vosk-2026-10-21.pdf),
> que lista "sem Internet mas interface diz que alerta foi enviado" como risco alto,
> com impacto "falsa sensação de proteção".

## Contexto

"Offline-first" é verdade para a detecção e **mentira** para a entrega.

KWS, ASR, criação da Ocorrência, hash, criptografia e fila podem ser inteiramente
locais. Mas a mensagem de emergência **não sai do aparelho sem transporte** —
Internet, SMS, telefonia ou outro canal. Nenhuma arquitetura resolve isso; é física
de rede.

O erro natural de implementação é mostrar "ajuda enviada" quando o comando foi
reconhecido. Isso é o pior tipo de bug possível neste produto: a usuária acredita que
pediu socorro e para de tentar por outros meios. Uma falsa sensação de proteção é
pior que nenhuma proteção, porque desloca o comportamento dela.

## Decisão

A Ocorrência tem **quatro estados**, distintos e persistidos:

| Estado | Significado | O que a usuária pode concluir |
|---|---|---|
| `CREATED` | gatilho reconhecido, Ocorrência montada e gravada localmente | o aparelho entendeu |
| `QUEUED` | na fila de envio, aguardando conectividade | ainda **não** saiu do aparelho |
| `SENT` | transmitido ao canal de despacho | saiu, mas ninguém confirmou |
| `ACKNOWLEDGED` | recibo do servidor recebido — ADR 0004 | **chegou** |

```
gatilho → CREATED → QUEUED → SENT → ACKNOWLEDGED
                      ↑         │
                      └─ retry ─┘
                   quando houver conectividade
```

**A interface nunca pode afirmar mais do que o estado atual.** Especificamente:

- `CREATED` ou `QUEUED` **nunca** exibem "ajuda enviada" ou equivalente;
- só `ACKNOWLEDGED` autoriza qualquer linguagem de confirmação;
- a fila offline é **criptografada**, como o resto (ADR 0004);
- o estado é registrado no log de auditoria a cada transição.

### Consequência para a Área protegida

O mesmo rigor vale para o estado da Sentinela (RF-24). **Nunca indicar "protegido" se
o microfone não estiver ativo.** Depois de um reboot, o estado correto é "proteção
desarmada — toque para reativar", não "protegido", mesmo que a usuária tenha deixado
armado antes de reiniciar. Ver [04-restricoes](../../docs/04-restricoes-plataforma.md).

Essas duas regras são a mesma regra: **o aplicativo não afirma proteção que não
está acontecendo.**

## Consequências

**Resolve:** elimina a classe de falha mais perigosa do produto — a usuária confiando
num alerta que não chegou.

**Resolve:** dá sentido ao recibo do servidor do ADR 0004, que passa a ser a
transição `SENT → ACKNOWLEDGED` em vez de um detalhe criptográfico solto.

**Resolve:** torna o comportamento offline demonstrável. No roteiro de demonstração,
mostrar o modo avião e a Ocorrência parada em `QUEUED`, depois religar e ver passar a
`ACKNOWLEDGED`, é uma demonstração melhor do que fingir que o problema não existe.

**Custa:** a interface fica menos tranquilizadora. Isso é correto, e precisa ser
explicado no texto, não suavizado: a usuária precisa saber quando ainda deve tentar
outro meio.

**Custa:** fila persistente, retry e log de auditoria. Mais código e mais testes de
estado do que um `POST` simples.

**Exige:** testar os quatro estados explicitamente, inclusive o caminho
`QUEUED → retry → SENT` com perda de rede no meio. Está em T-08 do
[plano de testes](../../docs/09-plano-de-testes.md).
