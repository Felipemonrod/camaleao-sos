# Dossiê — Botão do Pânico Virtual (Paraná)

> **Prioridade de investigação: ALTA.** É o prior art mais próximo do Protocolo SOS do
> Camaleão, é política pública em operação, tem fonte primária acessível — e é a única
> fonte que vimos alegando **alerta sem internet**, o que, se verdade, é a informação
> mais valiosa deste dossiê. Ver [Q-BPV-1](#q-bpv-1--como-o-alerta-sai-sem-internet).

Última atualização: 2026-10-21. Fontes consultadas: 2021 a 2024 — ⚠️ nada confirma o
estado em 2026.

## 1. Ficha

| | |
|---|---|
| **Nome** | Botão do Pânico Virtual / Aplicativo do Pânico |
| **Onde** | Paraná, integrado ao **App 190 PR** da Polícia Militar |
| **Quem** | PMPR + SESP-PR + TJPR (CEVID) |
| **Lançamento** | 2021 |
| **Alcance** | todo o Paraná; reportado como beneficiando ~30 mil vítimas |
| **Acesso** | **restrito**: só por decisão judicial |
| **Central** | monitoramento 24/7, inclusive fins de semana e feriados |
| **Custo para a usuária** | gratuito |
| **Fonte primária** | [página de serviço PMPR](https://www.pmpr.pr.gov.br/servicos/Servicos/Emergencia/Acionar-Botao-do-Panico-virtual-vitimas-de-violencia-domestica-QJoR8b3w) · [TJPR/CEVID](https://www.tjpr.jus.br/web/cevid/aplicativo-panico) |

## 2. O que sabemos, com fonte

### Elegibilidade e cadastro

O recurso **depende de decisão judicial**. O app é gratuito, mas a liberação do Botão
do Pânico só ocorre quando há decisão judicial; pode ser concedido pelo magistrado
junto com a medida protetiva de urgência, ou depois dela
([TJPR/CEVID](https://www.tjpr.jus.br/web/cevid/aplicativo-panico)).

A vítima instala o app e informa os dados da medida. É necessário confirmar os dados
informados na medida protetiva e **liberar o uso do GPS**
([TJPR/CEVID](https://www.tjpr.jus.br/web/cevid/aplicativo-panico)).

### Acionamento

| Passo | Detalhe |
|---|---|
| 1 | deslizar o botão vermelho |
| 2 | **escolher o agressor** |
| 3 | a Polícia Militar é acionada |
| 4 | o app grava **1 minuto** do som ambiente |

Fonte: [TJPR/CEVID](https://www.tjpr.jus.br/web/cevid/aplicativo-panico),
[Governo do Paraná](https://www.parana.pr.gov.br/Noticia/Entenda-como-funciona-o-Botao-do-Panico-Virtual-ferramenta-para-vitimas-de-violencia).

O passo 2 é interessante: o modelo de dados prevê **mais de um agressor** por vítima.
O Camaleão não tem esse conceito.

### Desacoplamento entre alerta e gravação

As duas funcionalidades operam **independentemente**: se a vítima fechar o aplicativo
durante a gravação do som, isso **não interfere no atendimento**
([TJPR/CEVID](https://www.tjpr.jus.br/web/cevid/aplicativo-panico)).

→ Esta é a lição de arquitetura mais direta do dossiê. Ver [§4](#4-lições-de-projeto).

### A alegação sobre internet

Segundo a SESP-PR, o serviço é gratuito e **não precisa de internet nem de crédito no
celular**
([Governo do Paraná](https://www.parana.pr.gov.br/servicos/Cidadania/Rede-de-protecao-da-mulher/Acionar-Botao-do-Panico-virtual-vitimas-de-violencia-domestica-QJoR8b3w)).

⚠️ **Não entendemos o mecanismo.** É a pergunta mais importante deste dossiê:
[Q-BPV-1](#q-bpv-1--como-o-alerta-sai-sem-internet).

### Escopo de uso

O TJPR orienta usar o botão **apenas** quando houver ameaça real do agressor contra o
qual existe medida restritiva. Para roubo, furto ou acidente, o App 190 tem canais
próprios ([TJPR/CEVID](https://www.tjpr.jus.br/web/cevid/aplicativo-panico)).

## 3. O que não sabemos

### Q-BPV-1 — Como o alerta sai sem internet?

**A pergunta mais valiosa do projeto inteiro neste momento.**

O [ADR 0006](../.claude/decisions/0006-maquina-de-estados-do-alerta.md) afirma que
"offline-first vale para a detecção, não para a entrega", e que a mensagem precisa de
transporte. A SESP-PR alega entrega sem internet nem crédito. Uma das duas afirmações
está incompleta.

Hipóteses, em ordem de plausibilidade:

| Hipótese | Como confirmar | O que significa para o Camaleão |
|---|---|---|
| **H1** — "sem crédito" se refere a chamada de voz ao 190, que é gratuita; o app em si usa dados | ler a página de serviço inteira; testar o app com dados desligados | ADR 0006 está certo. Nada muda |
| **H2** — usa **SMS** como canal de fallback | documentação técnica, ou observar o app | **muda o Camaleão**: SMS é um canal que funciona sem dados e sem crédito em muitos planos. Entraria como fallback de `QUEUED` |
| **H3** — operadoras fazem *zero-rating* do app por acordo com o Estado | notícia de lançamento, ou perguntar à SESP | não replicável por um projeto acadêmico. Bom argumento para a banca sobre por que o Camaleão **não** pode prometer isso |
| **H4** — imprecisão de comunicação institucional | comparar com a página da PMPR | ADR 0006 está certo, e temos um exemplo real de alegação imprecisa — útil para a apresentação |

**Como descobrir, em ordem de custo:**

1. Ler as páginas da PMPR, SESP-PR e TJPR **inteiras** — 20 minutos, pode já responder.
2. Instalar o App 190 PR e observar o comportamento com dados desligados. **Não acionar
   o botão** (ver [§6](#6-ética-da-investigação)).
3. Perguntar por e-mail à SESP-PR ou ao CEVID/TJPR, se identificando como pesquisa
   acadêmica. Órgãos públicos costumam responder, e a resposta é citável.

**Se for H2, abra um `Qn` em [docs/10](../docs/10-riscos-e-questoes-abertas.md)**, porque
passa a ser decisão de arquitetura do Camaleão, não curiosidade.

### Q-BPV-2 — O que a central faz com o áudio de 60 segundos?

Guarda? Por quanto tempo? Entra no inquérito? Quem tem acesso?

**Como descobrir:** páginas do TJPR/CEVID, resolução ou portaria que institui o
serviço, pedido via Lei de Acesso à Informação.

**O que significa:** é o precedente institucional brasileiro para
[Q3](../docs/10-riscos-e-questoes-abertas.md) — se o áudio pós-disparo do Camaleão deve
existir. Um órgão público gravando e retendo áudio ambiente de vítimas, com regra
publicada, é a melhor referência que [J1](../docs/07-privacidade-lgpd.md) pode ter.

### Q-BPV-3 — Por que 60 segundos?

Número redondo, ou calibrado?

**Como descobrir:** documentação do serviço; se não houver, perguntar.

**O que significa:** se houver critério, adotamos. Se for arbitrário, o Camaleão escolhe
o seu com critério próprio e isso é um pequeno diferencial metodológico.

### Q-BPV-4 — Há gravação **anterior** ao acionamento?

As fontes falam de gravar 1 minuto **a partir** do acionamento. Nada sugere buffer
pré-evento.

**O que significa:** se não há, o [Buffer pré-evento](../docs/05-arquitetura.md) do
Camaleão é um diferencial técnico real — e também assume um risco jurídico que o
serviço estadual escolheu não assumir. Vale saber qual dos dois.

### Q-BPV-5 — Existe avaliação pública de eficácia?

Falsos acionamentos, tempo de resposta, desfecho dos atendimentos.

**Como descobrir:** relatórios da SESP-PR, dados abertos do Paraná, TCCs e artigos que
citem o programa, pedido via LAI.

**O que significa:** é o único jeito de ter número brasileiro de **falso positivo em
operação real** — que é o [RNF-03](../docs/03-requisitos.md), a métrica mais difícil do
Camaleão. Se existir, é ouro para o `docs/09`.

### Q-BPV-6 — O app é camuflado?

Nada nas fontes sugere disfarce. Ele é o App 190 da PM, com ícone de polícia.

**O que significa:** se não é camuflado, há um contraste direto com o Camaleão — e uma
pergunta de pesquisa interessante: um app de polícia visível no telefone de uma vítima
é, ele mesmo, um risco pelo [modelo de ameaça](../docs/02-modelo-de-ameaca.md)? O
Estado aceitou esse risco em troca de resposta garantida. É um trade-off que vale citar.

## 4. Lições de projeto

### L1 — Desacoplar alerta de gravação

Que fechar o app durante a gravação não interfira no atendimento é exatamente o
princípio do [ADR 0006](../.claude/decisions/0006-maquina-de-estados-do-alerta.md): o
alerta tem ciclo de vida próprio, independente da evidência e da interface.

**Aplicar:** no Camaleão, a transição `CREATED → QUEUED → SENT` não pode depender da
captura de evidência continuar. Se a gravação falhar, o alerta segue.

⚠️ Isso **não está explícito** nos requisitos. Candidato a requisito novo em
[docs/03](../docs/03-requisitos.md) — abrir issue.

### L2 — Modelo de dados com múltiplos agressores

"Escolher o agressor" no acionamento revela que o cadastro prevê mais de um.

**Aplicar, ou não:** para o protótipo, não. Mas é uma lacuna de produto que vale
registrar — a Rede de Apoio do Camaleão é modelada, o agressor não.

### L3 — Precedente para gravação de áudio

Um serviço oficial de segurança pública brasileiro grava som ambiente de vítima e
entrega à PM. Isso enfraquece muito o argumento de que a proposta do Camaleão seria
juridicamente temerária.

**Aplicar:** citar em [docs/07](../docs/07-privacidade-lgpd.md) ao tratar de
[J1](../docs/07-privacidade-lgpd.md), junto do STF Tema 237.

### L4 — Resposta garantida é o que o Camaleão não tem

Central 24/7 e PM acionada resolvem o [R17](../docs/10-riscos-e-questoes-abertas.md) —
"o alerta chega e nada acontece". O Camaleão não resolve, porque exige convênio.

**Aplicar:** dizer isso na apresentação, antes de ser perguntado. O diferencial do
Camaleão é **público** (quem ainda não tem medida protetiva), não resposta.

## 5. Roteiro de investigação

Em ordem de valor por hora gasta:

| # | Ação | Tempo | Responde |
|---|---|---|---|
| 1 | Ler as 3 páginas oficiais (PMPR serviço, PMPR notícia, TJPR/CEVID) inteiras | 30 min | Q-BPV-1 a Q-BPV-4, talvez |
| 2 | Procurar a norma que institui o serviço — resolução, portaria, decreto | 30 min | Q-BPV-2, Q-BPV-3 |
| 3 | Instalar o App 190 PR e observar. **Sem acionar** | 20 min | Q-BPV-1, Q-BPV-6 |
| 4 | Buscar TCCs e artigos que avaliem o programa | 1 h | Q-BPV-5 |
| 5 | E-mail à SESP-PR e ao CEVID/TJPR, como pesquisa acadêmica | 20 min + espera | o que sobrar |
| 6 | Pedido via Lei de Acesso à Informação, se o e-mail não voltar | 30 min + 20 dias | Q-BPV-2, Q-BPV-5 |

O passo 5 é subestimado: é um projeto de instituição federal de ensino perguntando a um
órgão público sobre política pública. A resposta é citável e vale mais que qualquer
inferência nossa.

## 6. Ética da investigação

**Não acione o Botão do Pânico.** Ele despacha Polícia Militar de verdade. Acionamento
de teste consome recurso de emergência e pode ser crime.

Instalar e navegar é aceitável. Cadastrar dados de medida protetiva fictícia, não.

Se precisarem ver o acionamento funcionando, peça demonstração ao órgão — isso também é
um caminho legítimo, e provavelmente mais informativo.

## 7. Fontes

**Primárias:**

- [PMPR — Acionar Botão do Pânico virtual](https://www.pmpr.pr.gov.br/servicos/Servicos/Emergencia/Acionar-Botao-do-Panico-virtual-vitimas-de-violencia-domestica-QJoR8b3w)
- [PMPR — notícia de lançamento (2021)](https://www.pmpr.pr.gov.br/Noticia/Parana-passa-contar-com-Botao-do-Panico-Virtual-app-190-da-PM)
- [TJPR / CEVID — Aplicativo do Pânico](https://www.tjpr.jus.br/web/cevid/aplicativo-panico)
- [Governo do PR — página de serviço](https://www.parana.pr.gov.br/servicos/Cidadania/Rede-de-protecao-da-mulher/Acionar-Botao-do-Panico-virtual-vitimas-de-violencia-domestica-QJoR8b3w)
- [Governo do PR — como funciona](https://www.parana.pr.gov.br/Noticia/Entenda-como-funciona-o-Botao-do-Panico-Virtual-ferramenta-para-vitimas-de-violencia)
- [SESP-PR — notícia](https://www.seguranca.pr.gov.br/Noticia/Parana-passa-contar-com-Botao-do-Panico-Virtual-app-190-da-PM)

**Secundárias — para datar e contextualizar, não para afirmar funcionamento:**

- [Gazeta do Povo — alcance estadual, ~30 mil vítimas](https://www.gazetadopovo.com.br/parana/botao-do-panico-chega-a-todo-o-parana-e-beneficia-30-mil-vitimas/)
- [Campo Largo — 62 mulheres com medidas ativas usando](https://www.bemparana.com.br/noticias/regiao-metropolitana/campo-largo-registra-mais-de-550-atendimentos-a-mulheres-vitimas-de-violencia-em-um-ano/)
