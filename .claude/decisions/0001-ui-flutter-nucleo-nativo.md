---
data: 2026-10-21
status: substituido por 0008 (parcialmente)
---

# 0001 — Interface em Flutter, núcleo crítico em código nativo

> ⚠️ **Parcialmente substituído pelo [ADR 0008](0008-kotlin-nativo-sem-flutter.md).**
> A escolha do Flutter para a interface não vale mais: o protótipo é 100 % Kotlin.
> O que **continua valendo** é o princípio central deste ADR — a fronteira entre
> interface e núcleo de microfone, e a razão dela. Leia os dois juntos.

## Contexto

O Camaleão precisa de duas coisas com naturezas opostas: uma interface camuflada
que funcione igual nas duas plataformas, e um subsistema de microfone que sobreviva
em background respeitando regras de ciclo de vida que são completamente diferentes
entre Android e iOS.

A tentação é implementar tudo em Flutter, com um pacote de gravação de áudio. Isso
colocaria a captura de microfone e a detecção no mesmo isolate da interface, sujeita
ao ciclo de vida do widget tree.

No Android, o caminho documentado é um Foreground Service do tipo `microphone`, com
permissões específicas desde o Android 14 e restrição de início em background desde
o Android 12. No iOS, é preciso administrar `AVAudioSession`, modos de background e
interrupções. As restrições documentadas por Google e Apple vivem justamente nessas
camadas de sistema — não há abstração Flutter que as faça desaparecer.

## Decisão

A interface — Fachada, Área protegida, cadastro, calibração — é **Flutter**.

O núcleo — captura de microfone, VAD, motor de reconhecimento, PhraseMatcher,
ciclo de vida do serviço — é **nativo**: Kotlin no Android, Swift no iOS.

A fronteira é um Platform Channel com um contrato pequeno e estável:
`armar`, `desarmar`, `testar`, `consultarEstado`, e um evento de disparo.

O PhraseMatcher fica do lado nativo, junto ao motor, e **não** em Dart. Atravessar o
canal a cada janela de áudio seria custo por chamada em laço quente, sem ganho.

## Consequências

**Resolve:** o ciclo de vida do microfone passa a usar as primitivas corretas de
cada plataforma. No Android, o Foreground Service é usado como documentado; no iOS,
a sessão de áudio e as interrupções podem ser tratadas de verdade.

**Isola o motor.** Com o PhraseMatcher e o VAD nativos, trocar o motor de
reconhecimento (ADR 0002) é mudança local. É o que torna o caminho em duas etapas de
[06-reconhecimento-de-voz](../../docs/06-reconhecimento-de-voz.md) viável.

**Custa:** o núcleo é escrito duas vezes, uma por plataforma. Mitigado por ADR 0003
(Android primeiro) — no protótipo só uma é implementada.

**Custa:** o time precisa de alguém confortável em Kotlin, não só em Dart. A parte
difícil do projeto não é Flutter.

**Exige:** um contrato de canal estável desde o começo, porque os dois lados evoluem
em ritmos diferentes.
