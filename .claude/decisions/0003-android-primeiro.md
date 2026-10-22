---
data: 2026-10-21
status: aceito
---

# 0003 — Android é a plataforma do protótipo; iOS é alvo arquitetural, com PoC antecipada

## Contexto

O projeto quer rodar nas duas plataformas. As duas não oferecem o mesmo caminho.

No Android existe um caminho **oficialmente documentado** para captura contínua de
microfone em background: Foreground Service do tipo `microphone`. É restrito, exige
notificação e permissões específicas, mas é suportado e previsível. O sherpa tem
exemplo oficial Android com `AudioRecord` + KWS em Kotlin, e o Vosk publica artefato
Android via Maven.

No iOS a situação é assimétrica entre as duas bibliotecas:

- **sherpa-onnx está bem:** exemplos Swift/iOS, `Package.swift` declarando iOS 15+,
  `XCFramework` distribuído, e caminho de build manual documentado.
- **Vosk está mal:** apesar de o projeto declarar suporte a iOS e ter código iOS no
  repositório, a página oficial de instalação continua dizendo que o build iOS está
  "available on request", ao contrário do artefato Android pronto. Uma build
  reproduzível provavelmente exige compilar a biblioteca nativa, empacotar como
  `XCFramework` e criar uma ponte C/Objective-C++/Swift.

Há ainda atritos de distribuição:

- Personal Team gratuita limita App IDs, dispositivos e apps, e o perfil de
  provisionamento **expira em 7 dias**, exigindo recompilar e reinstalar;
- publicar exige Apple Developer Program a US$ 99/ano, com isenção possível apenas
  para **instituição de ensino credenciada** — o que vale investigar, dado que o
  projeto é institucional (IF Goiano).

O perfil de 7 dias é incompatível com o próprio recurso: a Sentinela existe para
ficar armada por semanas. Um protótipo iOS que precisa ser reinstalado toda semana
não demonstra o que o produto promete.

## Decisão

**O protótipo é Android.** Todos os requisitos P0 e P1 são entregues no Android.

**iOS é alvo arquitetural.** O contrato de Platform Channel (ADR 0001) e a fronteira
dos estágios de voz (ADR 0002) ficam preparados para Swift, e
[04-restricoes](../../docs/04-restricoes-plataforma.md) documenta as regras do iOS com
fonte primária.

**Mas a PoC do Vosk em iOS é antecipada, não adiada.** Compilar a lib nativa,
empacotar como `XCFramework` e fazer a ponte Swift é um item isolado, de risco alto e
desconhecido. Entra como spike na Fatia 2, em paralelo, por **uma** pessoa, com
caixa de tempo. O objetivo não é entregar iOS — é saber o tamanho do problema antes
de apostar nele numa fase posterior.

Na apresentação, dizer direto: "a arquitetura é das duas plataformas, o protótipo é
Android, e o motivo é o ciclo de vida do iOS, o build iOS do Vosk e o perfil de
provisionamento de 7 dias". É resposta técnica, não desculpa.

## Consequências

**Resolve:** concentra o esforço onde o caminho é documentado, dobrando o tempo para
as partes que decidem o projeto — detecção e ciclo de vida.

**Resolve:** destrava o ADR 0002, que pode escolher o motor por adequação à tarefa em
vez de por portabilidade.

**Resolve, parcialmente, o risco do iOS.** A PoC antecipada transforma "não sabemos
se dá" em um número. Se a ponte Vosk/iOS se mostrar inviável, a resposta pode ser
usar sherpa nos **dois** estágios no iOS — que é possível justamente porque o ADR
0002 separou os estágios.

**Custa:** metade do público-alvo não é atendida no protótipo. Deve ser dito, não
escondido.

**Custa:** uma pessoa do time sai do caminho principal para a PoC iOS. É caro, e é
mais barato que descobrir o problema na última semana.

**Exige:** não deixar o Platform Channel vazar suposições de Android. Se o contrato
assumir Foreground Service, o iOS fica mais caro depois.
