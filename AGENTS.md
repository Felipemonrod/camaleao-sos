# AGENTS.md — contrato para agentes de IA

Este arquivo é o contrato. Se você é um agente de IA trabalhando neste repositório —
Claude Code, Copilot, Cursor, Gemini, Codex ou outro — leia isto **inteiro** antes do
primeiro `write`.

Objetivo: qualquer agente deve produzir documentação indistinguível da que já está
aqui. Não "parecida". Indistinguível, nas invariantes.

---

## 1. Ordem de leitura obrigatória

Antes de escrever qualquer coisa:

| # | Arquivo | Por quê |
|---|---|---|
| 1 | [SISTEMA-SOT.md](SISTEMA-SOT.md) | as seis invariantes e onde cada fato mora. **Não negociável** |
| 2 | [SOT.md](SOT.md) | o estado do projeto e o índice |
| 3 | [docs/01-glossario.md](docs/01-glossario.md) | os nomes. Usar outro nome é erro |
| 4 | [docs/10-riscos-e-questoes-abertas.md](docs/10-riscos-e-questoes-abertas.md) | o que **não** está decidido. Não decida por conta própria |
| 5 | o documento que você vai alterar | |
| 6 | os ADRs que ele referencia | |

Se a tarefa toca plataforma, modelo de voz, privacidade ou integridade, leia também o
documento dono do assunto — a tabela de anatomia em `SISTEMA-SOT.md` §3 diz qual.

---

## 2. Regras rígidas

Violar qualquer uma destas é defeito, não estilo.

### R1 — Nunca invente uma fonte

Jamais escreva uma URL que você não leu nesta sessão, nem cite um número que você não
viu. Não há exceção.

Se você precisa de um fato de plataforma e não tem a fonte em mãos: **busque**. Se não
puder buscar, escreva:

```markdown
⚠️ **Não verificado:** <afirmação>. Verificar em <onde>. Registrado como Qn.
```

e abra o `Qn` em `docs/10`. Um `⚠️` honesto vale mais que uma citação plausível.

### R2 — Nunca prometa comportamento de plataforma sem fonte primária

Este projeto existe num terreno onde Android e iOS mudam as regras a cada versão.
Afirmação de ciclo de vida, permissão, background ou política de loja **só** entra com
link para `developer.android.com`, `developer.apple.com`, `support.google.com` ou o
repositório oficial do projeto em questão.

### R3 — Nunca decida uma questão aberta sozinho

`docs/10` lista `Qn` com **quem decide**. Se a sua tarefa esbarra numa questão aberta:

1. faça tudo o que **não** depende dela;
2. para o que depende, declare a suposição no texto, em `⚠️`;
3. diga ao humano, na resposta, qual `Qn` bloqueou e o que você assumiu.

Nunca fechar `Qn` sem instrução explícita do humano.

### R4 — Nunca torne um status mais otimista

Não marque requisito como atendido sem evidência. Não mude ADR de `proposto` para
`aceito`. Não escreva "implementado" porque o código existe — escreva quando um teste
de `docs/09` passou e você viu a saída.

Esta é a invariante I5, e é a mesma regra que o produto segue
([ADR 0006](.claude/decisions/0006-maquina-de-estados-do-alerta.md)).

### R5 — Nunca apague nem reescreva um ADR aceito

Decisão mudou? Escreva um ADR novo, marque o antigo como
`substituído por NNNN`, e deixe o arquivo. O histórico é o ativo.

### R6 — Nunca edite `referencias/`

É material de origem. Íntegro, para sempre. Se precisa comentar algo dele, comente no
documento ou no ADR que o cita.

### R7 — Nunca duplique um fato

Se você está escrevendo algo que já existe em outro documento, pare e coloque um link.
Duplicação é como este SOT morre.

### R8 — Nunca suavize um problema

Se uma escolha tem custo, escreva o custo. Se um limite técnico derruba uma ideia boa,
diga que derruba. Se você discorda do material em `referencias/`, discorde por escrito,
num ADR, com o motivo.

Documentação que esconde um limite faz alguém prometer esse limite para uma banca —
ou para uma usuária em risco.

### R9 — Português do Brasil

Documentos, ADRs, commits e comentários em pt-BR. Nomes de API em inglês
(`Foreground Service`, `AudioRecord`, `KWS`). Não traduza nome de classe.

### R10 — Sem atribuição de IA em commit ou PR

Nada de `Co-Authored-By`, nada de "Generated with". O autor é o humano. O uso de IA se
registra em [RELATORIO-IA.md](RELATORIO-IA.md), que é o lugar próprio e vale nota no
contexto acadêmico deste projeto.

---

## 3. Fluxo para tarefa de documentação

```
1. Ler, na ordem da §1
2. Identificar o documento DONO do assunto (SISTEMA-SOT §3)
3. A mudança é uma decisão?
   ├── sim → escrever ADR primeiro, depois ajustar os documentos para refleti-lo
   └── não → editar só o documento dono
4. Atualizar quem aponta para o que mudou (links, não cópias)
5. Mexeu em requisito? ID novo. Nunca reciclar
6. Surgiu dúvida? Qn em docs/10, com dono e bloqueio
7. python tools/verificar-sot.py
8. Atualizar .claude/NOTES.md (cronológico, sem repetir ADR)
9. Atualizar RELATORIO-IA.md
10. Rodar o checklist de SISTEMA-SOT.md §8
```

---

## 4. Fluxo para tarefa de código

O protótipo é **Kotlin nativo, Android apenas**
([ADR 0008](.claude/decisions/0008-kotlin-nativo-sem-flutter.md)).

```
1. Ler docs/03 (requisitos), docs/05 (arquitetura), docs/04 (restrições)
2. Localizar o RF/RNF que a tarefa atende. Não existe? Pare: ou falta requisito,
   ou a tarefa está fora de escopo. Pergunte ao humano
3. Conferir se a implementação respeita os contra-requisitos (CR-nn)
4. Implementar
5. Rodar o teste correspondente em docs/09. Sem teste, a tarefa não acabou
6. Reportar o resultado REAL. Falhou? Diga que falhou, com a saída
7. Atualizar docs/08 com o estado da tarefa — e nada além do que foi verificado
```

### Contra-requisitos que são fáceis de violar sem perceber

| CR | O que não fazer |
|---|---|
| CR-04 | usar `SpeechRecognizer` do Android |
| CR-08 | religar o microfone em `BOOT_COMPLETED` |
| CR-12 | abrir uma segunda captura de microfone |
| CR-14 | dependência com versão dinâmica (`vosk-android:0.3.32+`) |

A lista completa está em [docs/03](docs/03-requisitos.md). Leia antes de codar.

---

## 5. Quando parar e perguntar ao humano

Pare e pergunte — não assuma — se:

- a tarefa exige fechar uma `Qn`;
- a tarefa contraria um ADR `aceito`;
- a tarefa exige afirmar algo sobre plataforma que você não conseguiu verificar;
- a tarefa toca uma pendência jurídica `Jn` de `docs/07`;
- a tarefa pede remover um requisito `P0`;
- a tarefa criaria um documento novo em `docs/`;
- você percebeu que um ADR `aceito` está factualmente errado.

O último caso é importante: **reporte, não corrija em silêncio.** Um ADR errado é um
achado valioso, e a correção é decisão do humano.

---

## 6. Voz

Siga `SISTEMA-SOT.md` §7. Em resumo:

- frase afirmativa, direta, sem encher;
- tabela para comparar 3 ou mais coisas;
- diagrama ASCII para fluxo e máquina de estados;
- `⚠️` no que não foi verificado;
- custo de cada escolha sempre explícito;
- nomes do `docs/01`, sem sinônimo criativo.

### Anti-padrões que aparecem em texto gerado por IA

Estes são os que mais sujam este repositório. Evite ativamente:

| Anti-padrão | Em vez disso |
|---|---|
| "É importante notar que..." | diga a coisa |
| "Em resumo, podemos concluir que..." | conclua |
| Parágrafo de abertura resumindo o que vem | comece pelo conteúdo |
| Parágrafo de fechamento resumindo o que veio | pare quando acabar |
| Listar vantagens sem desvantagens | toda escolha tem custo |
| "Isso pode variar dependendo do contexto" | diga de que depende, ou abra `Qn` |
| Hedge em texto corrido | `⚠️` ou `Qn` |
| Entusiasmo ("excelente abordagem!") | avaliação com motivo |
| Reescrever um documento inteiro para mudar um parágrafo | edição cirúrgica |

---

## 7. O que este projeto é, em uma tela

Para você se orientar rápido. Nada aqui substitui ler o `SOT.md`.

| | |
|---|---|
| **Problema** | pessoa em violência doméstica não consegue pedir ajuda com o agressor presente |
| **Solução** | app com interface camuflada e múltiplos gatilhos, entre eles uma frase de segurança reconhecida **localmente** |
| **Protótipo** | Kotlin nativo, Android apenas, Vosk + KWS em dois estágios |
| **Custo** | R$ 0 em software e serviços. Requisito, não preferência |
| **Plataforma alvo futura** | iOS, arquiteturalmente previsto, não entregue |

### As três regras de honestidade do produto

Se você entender só três coisas deste projeto, que sejam estas:

1. **O app nunca afirma proteção que não está acontecendo.** Nem "ajuda enviada" sem
   recibo, nem "armada" sem microfone ativo.
2. **A frase é um gatilho acústico, não uma credencial.**
3. **Falso positivo é incidente de segurança, não bug de qualidade.** Um alerta
   disparado por engano, com o agressor presente, pode machucar alguém.

A terceira é a que muda decisões de engenharia com mais frequência. Quando em dúvida
entre detectar mais e errar menos, **erre menos**.
