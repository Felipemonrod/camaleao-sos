# O Sistema SOT

Este documento descreve **o método**, não o projeto. O projeto está em
[SOT.md](SOT.md).

Leia isto antes de escrever qualquer linha na documentação. Vale igual para pessoas e
para agentes de IA. Se você é um agente, leia também [AGENTS.md](AGENTS.md).

---

## 1. O que é um SOT

**Source of Truth:** um corpo de documentação onde cada fato do projeto mora em
**exatamente um lugar**, e esse lugar é descobrível a partir de um único ponto de
entrada.

O inimigo é a documentação que cresce por acumulação: três arquivos dizendo coisas
parecidas sobre a mesma decisão, dois deles desatualizados, e ninguém sabendo qual
vale. Um SOT previne isso por construção, não por disciplina.

### O que um SOT não é

| Não é | Porque |
|---|---|
| Um wiki | Wiki cresce por adição. SOT cresce por **substituição**. |
| Um diário | O histórico cronológico vive em `.claude/NOTES.md`, não nos documentos. |
| Um lugar para ideias | Ideia não decidida é **questão aberta** em `docs/10`, com dono e critério. |
| Uma lista de tarefas | Tarefas vivem em issues e em `docs/08`. |
| Documentação de API | Isso se gera do código. SOT é sobre **por quê**, não sobre *como chamar*. |

---

## 2. As seis invariantes

Se uma mudança viola qualquer uma delas, a mudança está errada — não a invariante.

### I1 — Um fato, um dono

Todo fato tem **um** documento dono. Outros documentos **apontam** para ele, nunca o
repetem. Se você precisa repetir, ou o fato está no documento errado, ou você precisa
de um link.

> Exemplo: "o Android exige notificação no Foreground Service" mora em
> `docs/04`. `docs/02` fala das **consequências de segurança** disso e **linka**.
> Nenhum dos dois repete o fato do outro.

### I2 — Toda afirmação de plataforma tem fonte primária

"O Android não permite X" sem link para `developer.android.com` não entra. Fonte
primária significa: documentação oficial do fornecedor, lei, norma, ou repositório do
projeto. Não blog, não Stack Overflow, não "eu lembro".

Se você não tem fonte, marque:

```markdown
⚠️ **Não verificado:** <a afirmação>. Verificar em <onde>. Registrado como Qn.
```

### I3 — Decisão fechada vive num ADR, e só lá

Documentos descrevem **o estado atual**. A discussão que levou até ele — alternativas,
trade-offs, o que foi rejeitado e por quê — vive em `.claude/decisions/`.

Nunca re-litigue uma decisão fechada dentro de um documento. Aponte para o ADR.

### I4 — Dúvida é questão aberta numerada, não parágrafo hesitante

Não escreva "talvez devêssemos considerar". Abra um `Qn` em `docs/10` com: o
problema, as opções, **quem decide** e **o que isso bloqueia**.

Hesitação espalhada pelo texto é como um SOT apodrece.

### I5 — O status nunca é otimista

Se não está implementado, o documento diz que não está. Se um teste não rodou, não
está "ok". Se uma decisão depende de experimento, o status é `proposto`, não `aceito`.

Esta invariante é a mesma regra que o produto segue
([ADR 0006](.claude/decisions/0006-maquina-de-estados-do-alerta.md)): **não afirmar
mais do que o estado real**. Vale para o software e para a documentação dele.

### I6 — O que é verificável, verifica-se

Links internos, IDs de requisito, referências a ADRs e a questões abertas são
checados por script:

```bash
python tools/verificar-sot.py
```

Roda no CI em todo PR. Se falhar, o PR não entra.

---

## 3. Anatomia: onde cada coisa mora

```
SOT.md                    ← ponto de entrada. Índice + a afirmação defensável + status
SISTEMA-SOT.md            ← este arquivo. O método
AGENTS.md                 ← contrato para agentes de IA
CONTRIBUTING.md           ← fluxo de contribuição para pessoas
RELATORIO-IA.md           ← registro do uso de IA no projeto

docs/
  00-visao-produto        ← problema, usuária, escopo DENTRO e FORA
  01-glossario            ← nomes canônicos. Único dono dos termos
  02-modelo-de-ameaca     ← adversário, ativos, consequências de projeto (Cn)
  03-requisitos           ← RF / RNF / CR numerados. Único dono dos IDs
  04-restricoes-plataforma← o que o SO permite. Único dono das fontes de plataforma
  05-arquitetura          ← camadas, estados, fluxo de dados
  06-reconhecimento-de-voz← motores, modelos, datasets, calibração
  07-privacidade-lgpd     ← dados tratados, hardening, pendências jurídicas (Jn)
  08-plano-prototipo      ← fatias, marcos, roteiro de demo
  09-plano-de-testes      ← métricas e protocolo. Único dono das metas numéricas
  10-riscos-e-questoes    ← Qn abertas e Rn de risco. Único dono do que não se decidiu
  11-trabalhos-relacionados← prior art. O que já existe e o que nos diferencia
  _templates/             ← modelos para copiar

.claude/
  NOTES.md                ← cronológico. O que não virou decisão formal
  decisions/NNNN-*.md     ← ADRs
  commands/               ← rituais de sessão

pesquisa/                 ← dossiês de investigação, um por sistema estudado
  README.md               ← como a pasta funciona e como escrever um dossiê
  <sistema>.md            ← fatos, perguntas Q-XXX-n, roteiro

tools/verificar-sot.py    ← o verificador das invariantes
referencias/              ← material de origem, íntegro, nunca editado
```

### A pasta `pesquisa/`

| | Dona de | Numeração |
|---|---|---|
| `docs/11` | as **conclusões** de prior art | — |
| `pesquisa/*.md` | a **investigação**: fatos por sistema, perguntas, roteiro | `Q-XXX-n` |

Um dossiê é material de trabalho: muda enquanto se investiga, e quando uma conclusão
amadurece ela sobe para `docs/11`. A numeração com prefixo evita colisão com as
questões abertas `Qn` de `docs/10`, e a distinção é real: `Qn` **bloqueia** o projeto;
`Q-XXX-n` apenas o melhora. Promova uma em outra quando virar bloqueio.

Todo dossiê precisa estar linkado de `docs/11` — o verificador checa (V10), para que
pesquisa não vire pasta órfã.

### A pergunta que resolve 90% dos casos

**"Onde ponho este fato?"**

```
É uma escolha entre alternativas, com trade-off?
├── sim → ADR em .claude/decisions/
└── não
    │
    É algo que o sistema operacional permite ou proíbe?
    ├── sim → docs/04 (com fonte primária)
    └── não
        │
        É algo que ainda não foi decidido?
        ├── sim → docs/10, como Qn
        └── não
            │
            É um número que o sistema precisa atingir?
            ├── sim → docs/03 (RNF) e docs/09 (como medir)
            └── não
                │
                É um nome?
                ├── sim → docs/01
                └── não → o documento de 00–11 cujo assunto é esse.
                          Se nenhum serve, você achou um documento que falta:
                          abra uma issue antes de criar o arquivo.
```

---

## 4. Como escrever um requisito

Em `docs/03`, e **só** lá.

```markdown
| RF-42 | Transmite a Ocorrência à Rede de Apoio pelo canal configurado. | P0 |
```

Regras:

| Regra | Detalhe |
|---|---|
| ID estável | `RF-nn` funcional, `RNF-nn` não funcional, `CR-nn` contra-requisito |
| **IDs nunca são reciclados** | Requisito removido → marque `~~RF-42~~ removido em ADR 00xx`. O número morre com ele |
| Testável | Se você não sabe como provar que foi atendido, não é requisito — é desejo |
| Uma frase | Se precisa de parágrafo, são dois requisitos |
| Prioridade | `P0` sem isso não há protótipo · `P1` necessário para a demo · `P2` desejável · `P3` depois |
| Sem solução embutida | "usar AES-GCM" é decisão (ADR), não requisito. O requisito é "a evidência é cifrada com algoritmo autenticado" |

### Contra-requisitos

`CR-nn` registra o que o sistema **deve deixar de fazer**, com o motivo. Existe para
que uma má ideia já rejeitada não volte como novidade em alguma reunião futura. Todo
`CR` aponta para o ADR ou o `Cn` que o justifica.

---

## 5. Como escrever um ADR

Copie [`docs/_templates/ADR.md`](docs/_templates/ADR.md). Numeração sequencial,
próximo número livre em `.claude/decisions/`.

```
.claude/decisions/0008-titulo-curto-em-kebab-case.md
```

### Ciclo de vida do status

```
proposto ──────▶ aceito ──────▶ substituído por NNNN
    │                │
    └──▶ rejeitado   └──▶ aceito, condicionado a <experimento>
```

| Status | Significado |
|---|---|
| `proposto` | a decisão está escrita, mas **não** foi tomada. Depende de alguém ou de algo |
| `aceito` | vale agora. O código e os documentos seguem isto |
| `aceito, condicionado a X` | vale agora, mas um experimento pode derrubá-la. Diga qual |
| `rejeitado` | foi considerada e descartada. **Não apague** — o registro do descarte tem valor |
| `substituído por NNNN` | uma decisão posterior mudou isto. Deixe o arquivo e aponte |

**ADR nunca é apagado nem reescrito depois de `aceito`.** Se a decisão mudou, escreva
um ADR novo que substitui o antigo. O histórico de decisões é o ativo mais valioso de
um projeto de meses — é o que impede refazer a mesma discussão três vezes.

### As três seções são obrigatórias, e cada uma tem um trabalho

| Seção | Pergunta que responde | Erro comum |
|---|---|---|
| **Contexto** | o que estava acontecendo que forçou uma escolha? | contar a história do projeto em vez do problema |
| **Decisão** | o que vale agora? | justificar em vez de decidir. Justificativa é Contexto |
| **Consequências** | o que isso resolve, custa e exige? | listar só benefícios |

**Consequências sem custo é propaganda, não ADR.** Toda decisão real custa algo. Se
você não encontrou o custo, você não entendeu a decisão.

Quando um ADR divergir do material de origem em `referencias/`, diga isso no topo, em
blockquote, e por quê. Discordar é permitido; discordar em silêncio não.

---

## 6. Como fechar uma questão aberta

`Qn` vive em `docs/10` com este formato:

```markdown
### Q7 — Manter o estágio ASR carregado em RAM?

<o problema, em 2–4 linhas>

<as opções, com o custo de cada uma>

**Decide:** quem ou o quê. **Bloqueia:** o que não pode andar sem isso.
```

Ao fechar:

1. Escreva o ADR com a decisão.
2. **Remova** o `Qn` de `docs/10` — não o marque como resolvido lá, remova.
3. Atualize os documentos que apontavam para `Qn` para apontarem para o ADR.
4. Registre em `.claude/NOTES.md` que `Qn` fechou e como.
5. Rode `python tools/verificar-sot.py` — ele acusa referências órfãs.

**Números de `Qn` também não são reciclados.** Q7 fechada não volta como outra
pergunta.

---

## 7. Voz e forma

O SOT tem um registro, e ele é parte da utilidade. Documentação que ninguém lê não
protege ninguém.

| Faça | Não faça |
|---|---|
| Frase afirmativa e direta | "é importante notar que, de certa forma," |
| Tabela para comparar 3+ coisas | parágrafo com comparação embutida |
| Diagrama ASCII para fluxo e estado | prosa descrevendo um fluxo |
| ⚠️ para o não verificado | afirmar com confiança o que não se checou |
| Dizer o custo de cada escolha | listar só vantagens |
| Nomes do `docs/01`, sempre | sinônimos criativos |
| Português do Brasil | inglês onde existe termo em português |
| Termo técnico em inglês quando é o nome da coisa (`Foreground Service`, `KWS`) | traduzir nome de API |

### O tom

Este SOT assume um leitor inteligente, com pressa, que vai tomar decisão com base no
que lê. Então: nada de encher, nada de suavizar problema, nada de hesitar em texto
corrido. Se uma coisa é ruim, diga que é ruim e por quê.

A honestidade aqui não é estilo — é requisito. Um documento que esconde um limite
técnico faz alguém prometer esse limite para uma banca, ou para uma usuária.

---

## 8. Checklist antes de abrir PR

```
[ ] Nenhum fato foi duplicado. Se repeti algo, troquei por link           (I1)
[ ] Toda afirmação nova de plataforma tem link para fonte primária        (I2)
[ ] Nenhuma decisão foi discutida fora de um ADR                          (I3)
[ ] Toda dúvida virou Qn em docs/10, com dono e bloqueio                  (I4)
[ ] Nenhum status ficou mais otimista que a realidade                     (I5)
[ ] python tools/verificar-sot.py passou                                  (I6)
[ ] Usei os nomes de docs/01
[ ] Se mexi em requisito: ID novo, não reciclado
[ ] Se tomei decisão: ADR escrito, com Consequências que incluem custo
[ ] Se fechei Qn: removida de docs/10 e referências atualizadas
[ ] .claude/NOTES.md atualizado, sem repetir o que virou ADR
[ ] RELATORIO-IA.md atualizado, se usei IA
```

---

## 9. Reusar este sistema em outro projeto

O método não tem nada de Camaleão. Para transplantar:

1. Copie `SISTEMA-SOT.md`, `AGENTS.md`, `CONTRIBUTING.md`, `docs/_templates/`,
   `tools/verificar-sot.py` e `.github/`.
2. Esvazie `docs/`, mantendo só os documentos que fazem sentido. Os únicos
   obrigatórios são: visão, glossário, requisitos, arquitetura, riscos e questões.
3. Reescreva `SOT.md` — índice, a afirmação defensável, status.
4. Comece o `0001` com a decisão estrutural do novo projeto.
5. Rode o verificador. Ele deve passar num repositório vazio.

O que faz o sistema funcionar não são os onze documentos. São as seis invariantes e o
fato de um script checar as que são checáveis.
