---
projeto: Camaleão SOS
documento: Source of Truth (SOT)
versao: 0.3
status: protótipo — implementação em validação
atualizado: 2026-10-21
---

# Camaleão SOS — Source of Truth

Este é o **ponto de entrada único** do projeto. Nenhum fato do Camaleão mora em dois
lugares: se está aqui, está aqui; se está num documento abaixo, não se repete aqui.
Quando algo mudar, muda no arquivo dono do assunto — não em cópias.

## O que é o Camaleão SOS, em uma frase

> Um protocolo de emergência por voz **offline**, processado no próprio aparelho, que
> permanece ativo em segundo plano **enquanto a proteção está armada**, e combina
> frase secreta, reconhecimento local de comandos, integridade criptográfica e rotas
> alternativas de acionamento.

A interface é camuflada (calculadora). A camuflagem protege a **interface de
acesso** — ela não tenta burlar indicadores nem políticas do sistema operacional.

**Protótipo:** Kotlin nativo, **Android apenas**, motor de voz pela união de Vosk
com KWS em dois estágios.

## A afirmação que podemos defender (e a que não podemos)

Isto está no topo de propósito. É a fronteira que separa o projeto de uma promessa
tecnicamente impossível.

| | |
|---|---|
| ✅ **Podemos afirmar** | A Sentinela é **armada previamente**. Enquanto estiver armada e permitida pelo sistema, o aparelho monitora a frase **localmente**. Nenhum áudio precisa sair do dispositivo durante a detecção. |
| ❌ **Não podemos afirmar** | Que funciona com o app encerrado, após "forçar parada", após reiniciar o telefone; que o uso do microfone fica invisível ao sistema; que o alerta chega sem conectividade; que o hash torna a gravação prova judicial. |

Detalhamento e fontes: [restrições de plataforma](docs/04-restricoes-plataforma.md).

## As três regras de honestidade

Atravessam todo o SOT e valem mais que qualquer recurso:

1. **O app nunca afirma proteção que não está acontecendo.** Nem "ajuda enviada" sem
   recibo, nem "armada" sem microfone ativo. ([ADR 0006](.claude/decisions/0006-maquina-de-estados-do-alerta.md))
2. **A frase é um gatilho acústico, não uma credencial.** ([ADR 0007](.claude/decisions/0007-frase-nao-e-autenticacao.md))
3. **Falso positivo é incidente de segurança, não bug de qualidade.** ([C2](docs/02-modelo-de-ameaca.md))

## Índice

| # | Documento | Dono do assunto |
|---|---|---|
| 00 | [Visão de produto](docs/00-visao-produto.md) | problema, usuária, escopo dentro/fora |
| 01 | [Glossário](docs/01-glossario.md) | nomes canônicos — use exatamente estes |
| 02 | [Modelo de ameaça](docs/02-modelo-de-ameaca.md) | quem é o adversário e o que ele consegue |
| 03 | [Requisitos](docs/03-requisitos.md) | RF / RNF / CR numerados e rastreáveis |
| 04 | [Restrições de plataforma](docs/04-restricoes-plataforma.md) | o que Android e iOS permitem de fato |
| 05 | [Arquitetura](docs/05-arquitetura.md) | camadas, máquina de estados, fluxo de dados |
| 06 | [Reconhecimento de voz](docs/06-reconhecimento-de-voz.md) | KWS, ASR, modelos, datasets, calibração |
| 07 | [Privacidade e LGPD](docs/07-privacidade-lgpd.md) | dados tratados, hardening, pendências jurídicas |
| 08 | [Plano do protótipo](docs/08-plano-prototipo.md) | fatias verticais, marcos, roteiro de demo |
| 09 | [Plano de testes](docs/09-plano-de-testes.md) | FRR, FAR, latência, recursos, ciclo de vida |
| 10 | [Riscos e questões abertas](docs/10-riscos-e-questoes-abertas.md) | o que ainda não está decidido |
| 11 | [Trabalhos relacionados](docs/11-trabalhos-relacionados.md) | prior art, e o que nos diferencia |

Dossiês de investigação, com perguntas abertas e roteiro, em
[`pesquisa/`](pesquisa/README.md): [Clique 180](pesquisa/clique-180.md) e
[Botão do Pânico — PR](pesquisa/botao-do-panico-pr.md).

## Decisões

Uma por arquivo, formato Contexto / Decisão / Consequências. **Não discuta decisão
fechada nos documentos acima** — aponte para o ADR.

| ADR | Decisão | Status |
|---|---|---|
| [0001](.claude/decisions/0001-ui-flutter-nucleo-nativo.md) | Interface Flutter, núcleo crítico nativo | **substituído em parte por 0008** |
| [0002](.claude/decisions/0002-motor-de-reconhecimento.md) | sherpa KWS → Vosk ASR; Vosk em dois estágios no protótipo | aceito, condicionado à tarefa 1.1 |
| [0003](.claude/decisions/0003-android-primeiro.md) | Android primeiro; iOS alvo arquitetural, com PoC antecipada | aceito |
| [0004](.claude/decisions/0004-integridade-da-ocorrencia.md) | Manifesto assinado e encadeado, não SHA-256 isolado | aceito |
| [0005](.claude/decisions/0005-gatilhos-multiplos.md) | A voz nunca é o único Gatilho | proposto — depende de Q5 |
| [0006](.claude/decisions/0006-maquina-de-estados-do-alerta.md) | Quatro estados do alerta; a interface não mente | aceito |
| [0007](.claude/decisions/0007-frase-nao-e-autenticacao.md) | A frase não é credencial; sem verificação de locutor no MVP | aceito |
| [0008](.claude/decisions/0008-kotlin-nativo-sem-flutter.md) | Protótipo 100 % Kotlin nativo, sem Flutter | aceito |
| [0009](.claude/decisions/0009-notificacao-e-cancelamento-protegido.md) | Notificação clara e cancelamento dentro da Área protegida | aceito |
| [0010](.claude/decisions/0010-palavra-por-funcao-e-sms-automatico.md) | Palavra por função na janela de comando; SMS automático à Rede de Apoio | aceito |
| [0011](.claude/decisions/0011-estagio-1-com-iscas-e-segunda-leitura.md) | Estágio 1 com iscas na gramática e segunda leitura do Buffer pré-evento | aceito, condicionado à medição com voz real |
| [0012](.claude/decisions/0012-ligacao-pelo-painel-e-linha-aberta.md) | Ligação pelo Painel com voz e, sem Painel, linha aberta no aparelho | aceito, condicionado ao teste no S25 e no aparelho de discagem |

**Anotações cronológicas** do dia a dia vão em [`.claude/NOTES.md`](.claude/NOTES.md).

## Origem

Este SOT foi derivado de duas pesquisas de 2026-10-21, preservadas em
[`referencias/`](referencias/):

| Documento | Peso |
|---|---|
| [pesquisa-2026-10-21.md](referencias/pesquisa-2026-10-21.md) | panorama de viabilidade. Fontes resolvidas em notas de rodapé |
| [analise-sherpa-vosk-2026-10-21.pdf](referencias/analise-sherpa-vosk-2026-10-21.pdf) | análise aprofundada. **Onde as duas divergem, esta prevalece** |

Três avisos sobre essa origem:

1. As duas citam um **documento de proposta do hackathon** que não veio nas
   exportações. Enquanto ele não entrar em `referencias/`, o enunciado do problema em
   [00-visao-produto](docs/00-visao-produto.md) é reconstrução, não citação.
2. Onde este SOT **divergiu** das pesquisas, existe um ADR explicando por quê: 0002
   (motor), 0003 (PoC iOS antecipada) e 0005 (gatilho por botão físico). O documento
   [02-modelo-de-ameaca](docs/02-modelo-de-ameaca.md) não tem equivalente em nenhuma
   das duas.
3. O ADR 0004 **corrige** a proposta original do hackathon quanto a SHA-256 e cadeia
   de custódia.

## Como contribuir

| Você é | Leia |
|---|---|
| pessoa | [CONTRIBUTING.md](CONTRIBUTING.md), depois [SISTEMA-SOT.md](SISTEMA-SOT.md) |
| agente de IA | [AGENTS.md](AGENTS.md) **inteiro**, antes do primeiro `write` |

O método está em [SISTEMA-SOT.md](SISTEMA-SOT.md): seis invariantes, e um script que
checa as checáveis.

```bash
python tools/verificar-sot.py
```

## Estado atual

A implementação e as evidências verificadas estão no [estado de execução do protótipo](docs/08-plano-prototipo.md#estado-da-implementação).
Métricas e testes mantêm seu documento dono em [docs/09](docs/09-plano-de-testes.md#registro-de-execução).
O objetivo institucional de produto completo não foi convertido em afirmação de confiabilidade sem medição.
