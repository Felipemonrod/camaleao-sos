# 08 — Plano do protótipo

Princípio: **fatias verticais, risco primeiro.** Nenhuma tela bonita antes de a
detecção offline estar provada. O que pode matar o projeto é técnico, não visual.

Plataforma: **Android**, Kotlin nativo
(ADRs [0003](../.claude/decisions/0003-android-primeiro.md) e
[0008](../.claude/decisions/0008-kotlin-nativo-sem-flutter.md)).

---

# ⏱ Corte de 24 horas

> **As cinco fatias abaixo não cabem em um dia.** Isto não é pessimismo: a Fatia 1
> sozinha inclui 2 h de áudio de background mais um baseline comparativo. O plano
> completo é de semanas.
>
> O que **cabe** em um dia é uma fatia vertical fina que atravessa o sistema inteiro
> e prova o conceito. É isso que se leva para amanhã.

## O que entra — a Demo Mínima

Tudo em Kotlin, um módulo, um aparelho.

| # | Entrega | Requisito | Por quê é indispensável |
|---|---|---|---|
| D1 | Tarefa 1.1 respondida: `small-pt-0.3` aceita gramática? | — | **primeira coisa.** Se der errado, o dia inteiro muda |
| D2 | `AudioCaptureEngine` — `AudioRecord` 16 kHz mono PCM16 | RF-29 | base de tudo |
| D3 | Estágio KWS: Vosk com gramática da frase + `[unk]` | RF-34 | o diferencial |
| D4 | Normalização + PhraseMatcher com limiar | RF-14 | sem isso, igualdade exata e demo frágil |
| D5 | `SentinelaService` FGS `microphone`, armado com Activity visível | RF-20, RF-21 | sem isso não funciona com tela bloqueada |
| D6 | Detecta com a tela bloqueada | RF-21 | **o momento da demo** |
| D7 | `POST` para servidor local + painel web mínimo | RF-42, RF-44 | fecha o fluxo fim a fim |
| D8 | Fachada de calculadora funcional e sequência de acesso | RF-01, RF-03 | o conceito de camuflagem |
| D9 | Teste negativo passando: "Vou buscar meu caderno azul" **não** ativa | — | **vale mais nota que todo o resto** |
| D10 | Texto de RF-27 na tela de estado | RF-27 | honestidade, e custa 10 minutos |

**Critério de aceitação da Demo Mínima:**

```
frase dita com o telefone bloqueado na mesa, sem internet
   → alerta no painel em < 10 s
frase parecida dita do mesmo jeito
   → nada acontece
```

As duas metades contam igual. Uma demo que só tem a primeira metade é um detector de
som.

## O que fica de fora — e o que dizer sobre cada coisa

Cortar é obrigatório. **Mentir sobre o corte, não.** A coluna da direita é o que se
diz à banca se perguntarem — e é o que transforma corte em escopo consciente.

| Fica de fora | Estado honesto a declarar |
|---|---|
| Cadastro e calibração da frase pela interface | "a frase está em arquivo de configuração; o cadastro com calibração de 3 amostras está especificado em RF-13" |
| Manifesto assinado, `previous_manifest_hash`, recibo | "a demo usa SHA-256 simples; o esquema de assinatura e contra-assinatura está no ADR 0004, e sabemos por que o hash isolado não basta" |
| Banco cifrado, AES-GCM, Keystore | "o hardening está em docs/07; a demo não o implementa" |
| Máquina de 4 estados do alerta | "a demo vai de `CREATED` a `SENT`; `QUEUED` e `ACKNOWLEDGED` estão no ADR 0006" |
| Gatilho por botão físico | "é o ADR 0005, e é o que garante que a voz não seja ponto único de falha" |
| Estágio `COMMAND_WINDOW` | "a arquitetura é de dois estágios (ADR 0002); a demo exercita o primeiro" |
| Rearme após reboot | "o padrão correto está em docs/04; o `BootReceiver` não foi implementado" |
| Medição de bateria, RAM, FAR em horas | "não medimos. É RNF-05 e RNF-11, e sem esses números não afirmamos que o modo pode ficar armado por dias" |
| iOS | "ADR 0003: Android é a plataforma do protótipo, e o motivo é técnico" |

**A última linha da tabela é a mais importante.** Não diga "funciona em background
indefinidamente". Diga "funciona armado, e não medimos o consumo ainda".

## Ordem das 24 horas

```
  D1 ─────▶ se falhar, PARE e releia docs/06 §Q4 antes de seguir
   │
   ▼
  D2 ─▶ D3 ─▶ D4        ← detecção funcionando em primeiro plano
   │
   ▼
  D5 ─▶ D6              ← o momento da demo. Se travar, é aqui
   │
   ▼
  D7                    ← servidor pode ser escrito em paralelo por outra pessoa
   │
   ▼
  D8 ─▶ D10             ← telas, por último, de propósito
   │
   ▼
  D9                    ← ensaiar até ficar entediante
```

**Paralelizável com segurança:** D7 (servidor e painel) não toca o app. Uma pessoa
pode fazer isso desde a primeira hora.

**Não paralelize D2 → D6.** É uma linha só, e cada passo depende do anterior rodar no
aparelho de verdade.

## Plano B, decidido agora e não às três da manhã

| Se | Então |
|---|---|
| D1 falhar (sem gramática) | vocabulário aberto + PhraseMatcher. Funciona, gasta mais CPU. Siga |
| D5/D6 travar no FGS | demonstre em primeiro plano, com a tela **ligada**, e declare que o background é o próximo passo. Não invente |
| D7 não ficar pronto | log na tela do aparelho, projetado. Prova a detecção, não o transporte |
| Nada detectar de forma confiável | **grave o vídeo da demo que funcionou** e apresente o vídeo + o SOT. É pior, e é honesto |

A linha de baixo existe porque a alternativa — improvisar na frente da banca — é pior
que todas as outras.

## Gravar o vídeo antes

Quando a Demo Mínima funcionar pela primeira vez, **pare e grave**. Rede local,
microfone e GPS numa sala com dezenas de celulares são três modos de falha que não
dependem de vocês.

---

# Plano completo

O que segue é o plano de verdade, do qual o corte acima é a primeira fatia fina. Serve
para depois de amanhã, e para mostrar à banca que o protótipo tem continuação pensada.

## Ordem das fatias

```
Fatia 1  ──→  Fatia 2  ──→  Fatia 3  ──→  Fatia 4  ──→  Fatia 5
detecção      background     protocolo     fachada e      demo e
funciona?     sobrevive?     SOS           cadastro       medição
   ▲
   └── se falhar aqui, o projeto muda, não continua
```

## Fatia 1 — A detecção funciona?

**Objetivo:** provar que Vosk com gramática restrita reconhece a Frase de Segurança
em pt-BR, no aparelho, offline. Sem UI, sem background.

Entregável: app Android nativo mínimo, um botão, log na tela.

| # | Tarefa | Requisito |
|---|---|---|
| 1.1 | **Responder [Q4](10-riscos-e-questoes-abertas.md):** `vosk-model-small-pt-0.3` aceita reconfiguração de gramática (`SetGrm`) ou é HCLG pré-compilado? | — |
| 1.2 | Integrar Vosk via Maven — **versão fixa, não `0.3.32+`** (CR-14) — e carregar o modelo pt-BR do diretório interno do app | RF-22 |
| 1.3 | `AudioCaptureEngine` único: `AudioRecord` 16 kHz / mono / PCM16 | RF-29 |
| 1.4 | Estágio 1 (`WAITING_KEYWORD`): gramática com a frase + `[unk]` | RF-34 |
| 1.5 | Estágio 2 (`COMMAND_WINDOW`): gramática de comandos, janela de 4–6 s, timeout sem ação | RF-35, RF-36 |
| 1.6 | Normalização + PhraseMatcher com limiar | RF-14 |
| 1.7 | Medir: 50–100 positivos, hard negatives, 2 h de background contínuo | RNF-03, RNF-04 |
| 1.8 | Baseline whisper.cpp `tiny` sobre as **mesmas** gravações | — |

**Critério de saída:** FRR < 5 % e **nenhum** falso acionamento nas 2 h de background,
em ambiente silencioso, com a máquina de três estados funcionando. Se não passar,
volte para [06-reconhecimento-de-voz](06-reconhecimento-de-voz.md) antes de escrever
mais código.

**Se 1.1 der "não suporta gramática":** plano B é vocabulário aberto + PhraseMatcher.
Mede-se bateria e RAM na Fatia 5 e, se reprovar, treinar modelo KWS pt-BR a partir do
Common Voice (CC0) entra no caminho crítico — o que é projeto de pesquisa, não de
hackathon. Decida isso explicitamente, não por inércia.

**Referência de partida:** o app oficial `android/SherpaOnnxKws` do sherpa mostra o
laço `acceptWaveform()` / `isReady()` / `decode()` / `reset()` com `AudioRecord`.
Mesmo usando Vosk no protótipo, é o modelo de estrutura a copiar — não escreva JNI do
zero.

## Fatia 2 — Sobrevive em background?

**Objetivo:** provar o ciclo de vida. É aqui que mora o segundo maior risco.

| # | Tarefa | Requisito |
|---|---|---|
| 2.1 | `SentinelaService` como Foreground Service tipo `microphone` | RF-21 |
| 2.2 | Permissões de Android 14+, armar só com Activity visível | RF-20 |
| 2.3 | Notificação discreta, coerente com a Fachada — ver [C1](02-modelo-de-ameaca.md) | RF-21 |
| 2.4 | Pipeline de áudio em thread própria: `AudioRecord` → VAD → motor | RF-22 |
| 2.5 | Buffer circular em RAM, sobrescrito, nada em disco | RF-23 |
| 2.6 | Tratar `onTaskRemoved`, `START_STICKY`, perda de permissão | RF-26 |
| 2.7 | Sinal de vida periódico persistido, para RF-24 | RF-24 |
| 2.8 | `BootReceiver`: restaura configuração e publica notificação de **rearme**. Não religa o microfone | RF-28, CR-08 |
| 2.9 | Tratar interrupções: chamada, Bluetooth, perda de rota de áudio, suspensão | RNF-14 |
| 2.10 | **Spike paralelo, caixa de tempo:** PoC do build Vosk em iOS — compilar nativo, `XCFramework`, ponte Swift | ADR 0003 |

**Critério de saída:** 30 minutos armada, tela bloqueada, telefone no bolso, frase
dita → detecta. Mais: remover dos Recentes e ainda detectar. Mais: reiniciar o
telefone e ver a notificação de rearme, com o estado exibido **nunca** "armada".

2.10 não entrega iOS. Entrega um número: quanto custa a ponte. Se o custo for
proibitivo, a resposta é usar sherpa nos dois estágios no iOS — possível justamente
porque o ADR 0002 separou os estágios.

## Fatia 3 — Protocolo SOS fim a fim

| # | Tarefa | Requisito |
|---|---|---|
| 3.1 | Servidor local — FastAPI ou Node — recebendo Ocorrência | RF-42 |
| 3.2 | Painel web com atualização em tempo real | RF-44 |
| 3.3 | Captura de localização com orçamento de tempo, segue sem ela | RF-40 |
| 3.4 | Montagem e persistência da Ocorrência em SQLite cifrado | RF-41, [C4](02-modelo-de-ameaca.md) |
| 3.5 | Manifesto assinado com chave do Keystore + `previous_manifest_hash` — ADR [0004](../.claude/decisions/0004-integridade-da-ocorrencia.md) | RF-43 |
| 3.6 | Pareamento de chaves e recibo contra-assinado do servidor | RF-43, RF-44 |
| 3.7 | Máquina de estados do alerta: `CREATED → QUEUED → SENT → ACKNOWLEDGED`, com retry | RF-48, RF-45 |
| 3.8 | Interface que **nunca** afirma mais do que o estado atual | RF-49 |
| 3.9 | Log de auditoria por transição; nunca logar frase, transcrição, GPS ou chave | RF-50, RF-52 |
| 3.10 | Garantir silêncio total: sem som, vibração ou mudança visível | RF-47 |

**Critério de saída:** frase dita → alerta no Painel em < 10 s (RNF-02), com manifesto
verificável e nenhum sinal perceptível no aparelho. Mais: em modo avião, a Ocorrência
para em `QUEUED` e a interface **não** diz "enviado"; ao religar a rede, chega a
`ACKNOWLEDGED`.

## Fatia 4 — Fachada, Área protegida e gatilhos alternativos

| # | Tarefa | Requisito |
|---|---|---|
| 4.1 | Calculadora Kotlin que calcula de verdade | RF-01 |
| 4.2 | Ícone e nome neutros | RF-02 |
| 4.3 | Entrada na Área protegida por sequência, sem pista visual | RF-03, RF-04 |
| 4.4 | Interface estreita `ui/` → `sentinela/`: armar, desarmar, testar, observar estado | RNF-07, RF-29 |
| 4.5 | Cadastro da frase com validação e calibração de 3 amostras | RF-10 … RF-13 |
| 4.6 | Armazenamento cifrado da frase e do segredo (Keystore) | RF-15 |
| 4.7 | Tela de estado da Sentinela + texto obrigatório de RF-27 | RF-24, RF-27 |
| 4.8 | "Testar minha frase" sem disparar o protocolo | RF-25 |
| 4.9 | Gatilho por código na Fachada | RF-30 |
| 4.10 | Gatilho por padrão de botões físicos, com tela bloqueada | RF-31 |
| 4.11 | Sair da Área protegida limpa o estado de navegação | RF-05 |

## Fatia 5 — Medição e demonstração

| # | Tarefa | Requisito |
|---|---|---|
| 5.1 | Rodar a bateria de testes de [09-plano-de-testes](09-plano-de-testes.md), incluindo **centenas de horas de negativos** | RNF-03, RNF-04 |
| 5.2 | **Medir bateria, RSS de RAM, CPU e throttling** | RNF-05, RNF-11 … RNF-13 |
| 5.3 | Calibrar o limiar pela curva FAR/FRR, não por intuição | RF-14 |
| 5.4 | Rodar a matriz de três aparelhos | — |
| 5.5 | Decidir [Q7](10-riscos-e-questoes-abertas.md) com o número de RAM na mão | RNF-11 |
| 5.6 | Ensaiar o roteiro de demonstração até ficar entediante | — |
| 5.7 | Plano B de demo: vídeo gravado, caso a rede ou o aparelho falhem na hora | — |

5.5 não é pessimismo. É um sistema que depende de microfone, Wi-Fi e GPS numa sala
com dezenas de celulares.

## Roteiro da demonstração

Vem da pesquisa original e é a melhor parte dela. O teste negativo é o que
transforma a demo em evidência.

```
1. Abre o Camaleão              → parece uma calculadora. Faz uma conta.
2. Entra na Área protegida      → sequência digitada
3. Cadastra a frase             → "Preciso buscar meu caderno azul"
4. [ Armar Sentinela ]
5. Sai do app, bloqueia a tela  → telefone na mesa, ninguém toca
6. Alguém diz:
   "Vou buscar meu caderno azul"       → ✗ NÃO ATIVA
7. Alguém diz:
   "Preciso buscar meu caderno azul"   → ✓ ATIVA

8. No notebook:
   ┌───────────────────────────────┐
   │ ⚠ ALERTA CAMALEÃO RECEBIDO    │
   │   21:43:08                    │
   │   Localização: recebida       │
   │   Gatilho: voz                │
   │   Manifesto: verificado ✓     │
   │   Estado: ACKNOWLEDGED        │
   └───────────────────────────────┘

9. Mostra o Wi-Fi sem internet  → a detecção foi local
10. Liga o modo avião e dispara de novo
    → a tela mostra QUEUED, e NÃO diz "ajuda enviada"
    → religa a rede → SENT → ACKNOWLEDGED
11. Reinicia o telefone
    → notificação "toque para reativar"; o estado NÃO é "armada"
12. Revoga a permissão do microfone
    → a Sentinela avisa que caiu, e o gatilho por botão ainda funciona
```

Os passos que valem nota são 6, 9, 10, 11 e 12 — e nenhum deles é a parte bonita:

| Passo | O que prova |
|---|---|
| 6 | existe discriminação; não é "qualquer som ativa" |
| 9 | o processamento é local de verdade |
| 10 | o sistema não mente sobre entrega — a distinção detectado / armazenado / entregue |
| 11 | o time conhece o limite da plataforma e o respeita em vez de fingir |
| 12 | o sistema degrada em vez de morrer |

São exatamente as cinco perguntas que uma banca boa vai fazer. Responder antes de ser
perguntado é a diferença entre um protótipo e uma demonstração.

## Meta interna honesta

**Uma frase pré-validada que funcione de forma muito confiável no aparelho da
demonstração.** Não "qualquer frase, qualquer voz, qualquer aparelho, qualquer
ambiente". Essa é a diferença entre um protótipo defensável e uma promessa que a
primeira pergunta da banca derruba.
