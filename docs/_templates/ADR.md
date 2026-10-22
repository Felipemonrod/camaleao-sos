---
data: AAAA-MM-DD
status: proposto
---

# NNNN — Título curto, afirmativo, da decisão

> Use este blockquote só se a decisão **divergir** do material em `referencias/` ou
> **substituir** outro ADR. Diga qual e por quê. Discordar é permitido; discordar em
> silêncio não.

## Contexto

O que estava acontecendo que forçou uma escolha. O problema, não a história do
projeto.

Inclua as alternativas que foram consideradas de verdade e o que cada uma custava.
Toda afirmação de plataforma aqui precisa de link para fonte primária (invariante I2).

## Decisão

O que vale agora, de forma direta. Sem justificar — justificativa é Contexto.

Se a decisão tem condição de validade (depende de um experimento), diga qual, e
reflita isso no `status` do front matter:
`aceito, condicionado a <experimento>`.

## Consequências

Separe em quatro, sempre nesta ordem:

**Resolve:** o que isso conserta.

**Custa:** o que isso passa a cobrar. **Obrigatório.** Consequências sem custo é
propaganda, não ADR. Se você não achou o custo, não entendeu a decisão.

**Exige:** o que o time passa a ter de fazer ou manter por causa disto.

**Risco residual:** o que continua podendo dar errado, e para onde aponta
(`Rn` ou `Qn` em `docs/10`).
