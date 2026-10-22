# Como contribuir

Este repositório usa um **SOT** — Source of Truth. O método está em
[SISTEMA-SOT.md](SISTEMA-SOT.md) e a regra que o resume é: **cada fato mora em
exatamente um lugar.**

Leia [SISTEMA-SOT.md](SISTEMA-SOT.md) antes do primeiro PR. São 10 minutos, e evitam
que seu PR seja devolvido por duplicar um fato.

Se você é um agente de IA, seu contrato é o [AGENTS.md](AGENTS.md).

---

## Antes de abrir PR

```bash
python tools/verificar-sot.py
```

Falhou, não abra. O CI roda o mesmo script e vai barrar.

Depois, o checklist de [SISTEMA-SOT.md §8](SISTEMA-SOT.md#8-checklist-antes-de-abrir-pr).

## Fluxo

### 1. Abra uma issue primeiro, nestes casos

| Caso | Template |
|---|---|
| Propor uma decisão de arquitetura | [ADR proposta](.github/ISSUE_TEMPLATE/adr-proposta.md) |
| Levantar uma dúvida que precisa ser decidida | [Questão aberta](.github/ISSUE_TEMPLATE/questao-aberta.md) |
| Apontar um risco | [Risco](.github/ISSUE_TEMPLATE/risco.md) |
| Criar um documento novo em `docs/` | issue livre, explicando por quê |

Correção de erro, link quebrado, typo, ou acrescentar fonte: vá direto ao PR.

### 2. Branch

```
docs/<assunto-curto>      mudança de documentação
adr/<NNNN>-<slug>         novo ADR
feat/<assunto>            código
fix/<assunto>             correção
```

### 3. Commits

Em português, no imperativo, com prefixo:

```
docs: acrescenta ADR 0008 sobre Kotlin nativo
docs: corrige fonte da restrição de BOOT_COMPLETED
feat: detecta frase com tela bloqueada
fix: libera AudioRecord ao desarmar a Sentinela
test: mede FAR em 2 h de áudio de background
```

**Sem rodapé de atribuição a IA.** Nada de `Co-Authored-By`, nada de "Generated
with". O uso de IA se registra em [RELATORIO-IA.md](RELATORIO-IA.md), que é o lugar
próprio — e, no contexto acadêmico deste projeto, vale nota.

Adicione arquivos por caminho explícito. **Nunca `git add -A` nem `git commit -a`**:
mais de uma pessoa, e mais de um agente, podem estar na mesma árvore.

```bash
git add docs/05-arquitetura.md .claude/decisions/0008-*.md
git diff --cached --name-only   # confirme que é só o que você quis
git commit -m "docs: ..."
```

---

## As cinco devoluções mais comuns

Se o seu PR for devolvido, provavelmente é uma destas.

### 1. Fato duplicado

Você escreveu em `docs/05` algo que já está em `docs/04`.

→ Apague e coloque um link. A tabela de anatomia em
[SISTEMA-SOT.md §3](SISTEMA-SOT.md#3-anatomia-onde-cada-coisa-mora) diz quem é o dono
de cada assunto.

### 2. Afirmação de plataforma sem fonte

"O Android não permite X."

→ Precisa de link para `developer.android.com`. Sem fonte, use:

```markdown
⚠️ **Não verificado:** <afirmação>. Verificar em <onde>. Registrado como Qn.
```

e abra o `Qn` em `docs/10`.

### 3. Decisão discutida fora de um ADR

Você explicou, dentro de um documento, por que escolheu A em vez de B.

→ Isso é um ADR. O documento descreve o **estado atual** e aponta para o ADR.

### 4. Dúvida como parágrafo hesitante

"Talvez seja interessante considerar se..."

→ Vira `Qn` em `docs/10`, com **quem decide** e **o que bloqueia**.

### 5. Status otimista

Requisito marcado como atendido sem teste; ADR marcado `aceito` sem a decisão ter sido
tomada.

→ Invariante I5. Status nunca é mais otimista que a realidade. É a mesma regra que o
produto segue ([ADR 0006](.claude/decisions/0006-maquina-de-estados-do-alerta.md)).

---

## Escrevendo um ADR

Copie [`docs/_templates/ADR.md`](docs/_templates/ADR.md). Próximo número livre em
`.claude/decisions/`.

Três coisas que o revisor vai checar:

1. **Contexto** diz qual era o problema, não a história do projeto.
2. **Decisão** decide, em vez de justificar.
3. **Consequências** incluem o **custo**. Consequências sem custo é propaganda, não
   ADR — e é a devolução mais frequente em ADRs.

Depois, acrescente o ADR à tabela de decisões do [SOT.md](SOT.md). O verificador
checa isso (V3).

---

## Escrevendo código

O protótipo é **Kotlin nativo, Android apenas**
([ADR 0008](.claude/decisions/0008-kotlin-nativo-sem-flutter.md)).

| Regra | Detalhe |
|---|---|
| Todo PR de código aponta um RF ou RNF | não achou o requisito? ou falta requisito, ou a tarefa está fora de escopo — abra issue |
| Leia os contra-requisitos antes | `CR-nn` em [docs/03](docs/03-requisitos.md) existem para impedir erros que já foram analisados |
| `app/ui/` não conhece `AudioRecord` nem o motor de voz | sem a barreira de linguagem, nada impede — é revisão de código que pega |
| Sem teste, a tarefa não acabou | o teste correspondente está em [docs/09](docs/09-plano-de-testes.md) |
| Reporte o resultado real | falhou? diga que falhou, com a saída |

### Os contra-requisitos mais fáceis de violar

| CR | Não faça |
|---|---|
| CR-04 | usar `SpeechRecognizer` do Android |
| CR-08 | religar o microfone em `BOOT_COMPLETED` |
| CR-12 | abrir uma segunda captura de microfone |
| CR-14 | dependência com versão dinâmica (`vosk-android:0.3.32+`) |

---

## Sobre o assunto deste projeto

Este software é para pessoas em risco. Duas consequências práticas na revisão:

1. **Falso positivo é incidente de segurança.** Um alerta disparado por engano, com o
   agressor presente, pode machucar alguém. Na dúvida entre detectar mais e errar
   menos, **erre menos**.
2. **Não escreva nada que o app não possa provar.** Texto de interface que afirma
   proteção inexistente é um defeito tão grave quanto um crash — e mais perigoso,
   porque a usuária para de tentar outros meios.
