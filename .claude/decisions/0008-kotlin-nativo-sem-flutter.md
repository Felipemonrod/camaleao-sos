---
data: 2026-10-21
status: aceito
---

# 0008 — O protótipo é 100 % Kotlin nativo, sem Flutter

> **Substitui parcialmente o [ADR 0001](0001-ui-flutter-nucleo-nativo.md)**, que
> definia interface em Flutter com núcleo nativo. A fronteira UI / núcleo do 0001
> continua valendo como princípio; o que muda é a tecnologia da camada de interface
> no protótipo.

## Contexto

O ADR 0001 escolheu Flutter para a interface com um objetivo específico: ter a mesma
camada visual em Android e iOS, mantendo o núcleo de microfone em código nativo.

Duas coisas mudaram desde então.

Primeiro, o ADR [0003](0003-android-primeiro.md) definiu que **o protótipo é Android
apenas**, e isso foi agora confirmado como decisão de produto, não como preferência
técnica. O benefício que justificava o Flutter — uma UI para duas plataformas —
deixou de existir dentro do escopo do protótipo.

Segundo, o prazo. A entrega do protótipo é de **um dia**. Flutter, nesse contexto,
cobra três custos sem devolver nada:

| Custo | Detalhe |
|---|---|
| Platform Channel | contrato de serialização entre Dart e Kotlin, com eventos assíncronos de disparo, a escrever e depurar |
| Dois ciclos de vida | o do widget tree e o do `Service`, que precisam ser reconciliados |
| Dois toolchains | build do Flutter mais build do Gradle, cada um com seus modos de falha |

Toda a parte que decide o projeto já era Kotlin: `AudioRecord`, `AudioCaptureEngine`,
`SentinelaService`, máquina de estados da voz, Vosk via Maven. O que sobrava para o
Flutter era a Fachada, o cadastro e a tela de estado — telas simples, que em Kotlin
custam menos do que a ponte custa.

## Decisão

**O protótipo é um aplicativo Android nativo, escrito inteiramente em Kotlin.** Sem
Flutter, sem Dart, sem Platform Channel.

| Camada | Tecnologia |
|---|---|
| Fachada (calculadora) | Kotlin — Activity, Jetpack Compose ou Views |
| Área protegida, cadastro, estado | Kotlin |
| `AudioCaptureEngine`, estágios de voz | Kotlin |
| `SentinelaService` (FGS `microphone`) | Kotlin |
| Vosk | `com.alphacephei:vosk-android`, versão **fixa** (CR-14) |
| Painel | web, servidor local — fora do app |

### O que se preserva do ADR 0001

A **fronteira lógica** entre interface e núcleo continua obrigatória, agora como
separação de módulos ou pacotes em vez de separação de linguagem:

```
app/
  ui/           ← Activities, telas. NÃO conhece AudioRecord nem Vosk
  sentinela/    ← AudioCaptureEngine, estágios, máquina de estados
  sos/          ← Ocorrência, manifesto, fila, estados do alerta
  dados/        ← persistência cifrada, Keystore
```

`ui/` fala com `sentinela/` por uma interface estreita — `armar`, `desarmar`,
`testar`, `observarEstado` — exatamente o contrato que seria o Platform Channel. A
razão é a mesma de antes: o ciclo de vida do microfone não pode depender do ciclo de
vida de uma tela.

### Status do iOS

Inalterado pelo ADR 0003: alvo arquitetural, não entrega. A PoC de build do Vosk em
iOS continua valendo como spike.

A escolha do Kotlin **não fecha** a porta do iOS. Quando ela abrir, as opções serão
Swift nativo ou KMP, e a decisão será nova. Essa escolha não precisa ser feita agora, e
fazê-la agora atrasaria o protótipo sem informação nenhuma a mais.

## Consequências

**Resolve:** elimina a ponte Dart ↔ Kotlin e a reconciliação de dois ciclos de vida.
Num prazo de um dia, é a diferença entre caber e não caber.

**Resolve:** um toolchain só. Um `gradlew assembleDebug` em vez de dois builds
encadeados.

**Resolve:** o exemplo oficial `android/SherpaOnnxKws` e os exemplos Android do Vosk
são Kotlin/Java. Passam a ser copiáveis direto, sem tradução.

**Custa:** se o iOS entrar depois, a interface é reescrita. Aceito — era reescrita de
qualquer jeito na camada nativa, e as telas do protótipo são simples.

**Custa:** perde-se hot reload. Irrelevante em telas desta simplicidade.

**Exige:** disciplina de módulos. Sem a barreira de linguagem, nada **impede**
`ui/` de chamar `AudioRecord` direto. Isso precisa ser pego em revisão de código, não
pelo compilador.

**Exige:** atualizar RNF-07, que mencionava Flutter.

**Risco residual:** se o projeto decidir iOS logo depois do hackathon, o custo da
reescrita de UI aparece ali. É um custo conhecido, pequeno nesta escala, e pago depois
de o conceito estar provado — que é a ordem certa.
