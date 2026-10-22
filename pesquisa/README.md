# pesquisa/ — material de investigação

Esta pasta guarda **dossiês de investigação**: um arquivo por sistema que estamos
estudando, com o que sabemos, o que não sabemos, e como descobrir.

## A divisão com docs/11

| | Dona de | Formato |
|---|---|---|
| [`docs/11`](../docs/11-trabalhos-relacionados.md) | as **conclusões** — o que existe, o que é novo no Camaleão, como apresentar | documento do SOT, estável |
| `pesquisa/*.md` | a **investigação** — fatos detalhados por sistema, perguntas abertas de pesquisa, roteiro para respondê-las | dossiê, muda enquanto se investiga |

Isso respeita a invariante **I1** do [Sistema SOT](../SISTEMA-SOT.md): o detalhe sobre
cada app mora no dossiê; `docs/11` não o repete, só conclui e linka.

Quando uma investigação fecha, a **conclusão** sobe para `docs/11` e o dossiê fica
como o registro de como se chegou nela.

## Numeração das perguntas

Dossiês usam prefixo próprio — `Q-C180-1`, `Q-BPV-1` — para **não** colidir com as
questões abertas `Qn` de [`docs/10`](../docs/10-riscos-e-questoes-abertas.md).

A diferença não é cosmética:

| | O que é | Onde |
|---|---|---|
| `Qn` | **bloqueia** uma decisão ou uma fatia do projeto | `docs/10` |
| `Q-XXX-n` | pergunta de pesquisa. Responder **melhora** o projeto, não o destrava | dossiê |

Se uma pergunta de pesquisa virar bloqueio, promova-a: abra um `Qn` em `docs/10` e
deixe o dossiê apontando para ele.

## Como escrever um dossiê

Siga a estrutura dos que já existem:

```
1. Ficha            — o que é, quem fez, plataformas, status, fonte primária
2. O que sabemos    — fatos COM fonte. Sem fonte não entra (invariante I2)
3. O que não sabemos— Q-XXX-n, cada uma com: como descobrir + o que cada
                      resposta significa para o Camaleão
4. Lições           — o que já dá para aplicar, apontando para o ADR ou Cn
5. Roteiro          — passos práticos de investigação, em ordem de valor
6. Fontes           — links primários
```

O item 3 é o coração. Uma pergunta sem "o que cada resposta significa" é curiosidade,
não pesquisa.

## Regras

- **Fonte primária sempre que existir.** Site do órgão, página de serviço, lei. Notícia
  serve para datar e contextualizar, não para afirmar funcionamento.
- **Data tudo.** App muda, reportagem envelhece. Toda afirmação diz de quando é.
- **Marque o não verificado** com ⚠️, como em qualquer documento do SOT.
- **Não copie texto** das fontes. Resuma e linke.

## Dossiês

| Sistema | Por que interessa |
|---|---|
| [Clique 180](clique-180.md) | prior art direto da **Fachada** — camuflagem já feita por política pública federal |
| [Botão do Pânico Virtual — PR](botao-do-panico-pr.md) | prior art direto do **Protocolo SOS**, e a única fonte que vimos alegando alerta **sem internet** |
