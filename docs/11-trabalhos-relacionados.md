# 11 — Trabalhos relacionados

Nenhum dos dois documentos em `referencias/` tem levantamento de prior art. Isto é uma
lacuna séria num projeto acadêmico: a primeira pergunta de qualquer banca é **"isso já
existe?"**. A resposta honesta é **sim, várias partes existem** — e saber exatamente
quais torna o diferencial defensável em vez de ingênuo.

> ⚠️ **Levantamento preliminar.** Feito por busca em 2026-10-21, não por revisão
> sistemática. Datas e status de aplicativos envelhecem rápido; confirmar
> disponibilidade atual nas lojas antes de citar na apresentação. Registrado como
> [Q8](10-riscos-e-questoes-abertas.md).

## O que já existe, por componente

| Componente do Camaleão | Já existe? | Onde |
|---|---|---|
| Interface camuflada | ✅ **sim, inclusive no Brasil** | Clique 180, Aspire News |
| Alerta discreto para rede de apoio | ✅ sim, muito comum | quase todos os apps de DV |
| Gravação de áudio como evidência | ✅ **sim, inclusive oficial** | Botão do Pânico Virtual (PR) |
| Localização no disparo | ✅ sim | todos |
| Gatilho por gesto / toque múltiplo | ✅ sim | Clique 180, Aspire News |
| **Detecção de frase por KWS local em pt-BR** | ❌ **não encontrado** | — |
| **Máquina de estados honesta do alerta** | ❌ não encontrado | — |
| Botão do pânico como política pública | ✅ sim | PR, ES, RR e outros estados |

As duas linhas marcadas com ❌ são onde o projeto tem espaço real. Todo o resto é
reimplementação de algo que existe — o que é legítimo num trabalho acadêmico, mas não
é diferencial.

## Brasil

Os dois sistemas mais relevantes têm **dossiê de investigação próprio** em
[`pesquisa/`](../pesquisa/README.md), com os fatos detalhados, as perguntas abertas e o
roteiro para respondê-las. Aqui ficam só as conclusões.

### Clique 180 — prior art da Fachada

→ Dossiê completo: [`pesquisa/clique-180.md`](../pesquisa/clique-180.md)

App da ONU Mulheres com a Secretaria de Políticas para as Mulheres. **Tem tela de
disfarce** e ativação por gesto, desde 2018 no mínimo.

**Conclusão para o Camaleão:** a camuflagem **não é o nosso diferencial**. Já foi feita
por política pública federal. O diferencial tem de ser o gatilho por voz, ou nada.

Duas conclusões secundárias, detalhadas no dossiê: a Fachada dele é **passiva** (uma
frase), enquanto a nossa calcula de verdade — camuflagem mais forte; e os gatilhos dele
exigem o aparelho destravado e na mão, que é a lacuna que o
[ADR 0005](../.claude/decisions/0005-gatilhos-multiplos.md) ataca.

⚠️ **Não cite na apresentação antes de confirmar se o app ainda está nas lojas.**
É `Q-C180-1` no dossiê, e são cinco minutos.

### Botão do Pânico Virtual (Paraná) — prior art do Protocolo SOS

→ Dossiê completo: [`pesquisa/botao-do-panico-pr.md`](../pesquisa/botao-do-panico-pr.md)

Integrado ao App 190 da PMPR desde 2021, em todo o estado. Liberado **só por decisão
judicial**. Aciona a Polícia Militar com geolocalização e **grava 60 segundos de som
ambiente**. Central de monitoramento 24/7.

**Três conclusões:**

1. **Gravação de áudio como evidência já é prática oficial no Brasil.** Ajuda muito em
   [J1](07-privacidade-lgpd.md): não estamos propondo nada inédito nem juridicamente
   temerário.
2. **O alerta vai para a PM, não para uma rede informal.** Isso resolve o
   [R17](10-riscos-e-questoes-abertas.md) de um jeito que o Camaleão **não** resolve,
   porque exige convênio institucional. Diga isso antes de ser perguntado.
3. **É restrito a quem tem medida protetiva.** O Camaleão atende quem está **antes**
   disso — a maioria, e a mais desassistida. **Este é o nosso diferencial de público, e
   é real.**

⚠️ **Um achado que pode mudar o ADR 0006:** a SESP-PR alega que o serviço funciona
**sem internet nem crédito no celular**. Nosso
[ADR 0006](../.claude/decisions/0006-maquina-de-estados-do-alerta.md) afirma o
contrário para a entrega. Uma das duas afirmações está incompleta, e descobrir qual é a
pergunta mais valiosa da pesquisa agora — `Q-BPV-1` no dossiê.

### Base legal estadual

A legislação de estados como Roraima já define botão do pânico como "dispositivo
eletrônico ou aplicativo de segurança preventiva com GPS, capaz de transmitir
informações para uma central de operações na área de segurança pública"
([texto legislativo](https://transparencia.al.rr.leg.br/materias/18972/texto-original)).

→ Existe vocabulário jurídico consolidado. Usar os termos dele na apresentação é mais
forte do que inventar nomes.

### Pesquisa acadêmica brasileira

Há TCCs e artigos sobre o tema, incluindo um estudo da UnB sobre Macapá que aponta
que, **apesar da eficácia, há desafios estruturais e legais** — segurança de dados,
infraestrutura e acesso em áreas rurais
([UnB, 2025](https://bdm.unb.br/bitstream/10483/43630/1/2025_Desafios_Implementacao_Aplicativos_Emergencia_tcc.pdf)).

→ O gargalo documentado na literatura brasileira é **implantação**, não detecção.
Vale saber: a parte difícil do problema real não é a que o Camaleão resolve.

## Internacional

### Aspire News (EUA) — e a lição mais importante desta página

Lançado em 2013 pela Robin McGraw Revelation Foundation. Disfarçado de app de notícias.
Tocar três vezes na barra superior alerta contatos de confiança com mensagem
pré-escrita, nota de voz pré-gravada e localização por SMS
([TechCrunch](https://techcrunch.com/2020/06/25/aspire-app-dr-phil)).

**E duas falhas que o Camaleão precisa não repetir:**

| Falha | Fonte | O que isso ensina |
|---|---|---|
| Alegação de que o alerta ia para autoridades foi **contestada** — um despachante de 911 da Virgínia disse que não era assim que o alerta chegava | [Snopes](https://www.snopes.com/2015/06/29/aspire-news-app/) | é exatamente o [ADR 0006](../.claude/decisions/0006-maquina-de-estados-do-alerta.md): nunca afirmar entrega que não se pode provar |
| As notas de voz enviadas ficaram **expostas num servidor de nuvem sem proteção** | [Snopes](https://www.snopes.com/2015/06/29/aspire-news-app/) | é o [C4](02-modelo-de-ameaca.md) e o hardening de [docs/07](07-privacidade-lgpd.md) |

Um app de proteção com vazamento de áudio de vítimas é o pior resultado possível deste
tipo de projeto. Vale citar na apresentação como motivação do hardening — mostra que o
rigor do SOT não é teatro.

### Outros

- **Grécia** — Panic Button nacional para vítimas de violência doméstica, estendido a
  todo o país em junho de 2024
  ([Cyprus Mail](https://cyprus-mail.com/2024/07/08/panic-button-to-be-rolled-out-for-domestic-violence-cases)).
- **Ucrânia** — app com botão de alarme sob ícone neutro na tela
  ([Mezha](https://mezha.media/en/?p=55251)).

### A crítica acadêmica que o SOT precisa absorver

Um estudo da Universidade de Durham constatou que a função mais comum nesses apps é o
alarme de pânico, e **levantou a preocupação de que alguns desses apps podem causar
mais dano do que benefício**
([9to5Mac](https://9to5mac.com/2022/05/24/domestic-violence-app/)).

Isso é o [modelo de ameaça](02-modelo-de-ameaca.md) deste SOT, dito por pesquisadores.
Confirma de fora que [C1](02-modelo-de-ameaca.md) e [C2](02-modelo-de-ameaca.md) não
são preciosismo nosso.

### Segurança computacional clínica para vítimas de VPI

A linha de pesquisa mais relevante para o modelo de ameaça é a de **Thomas Ristenpart
e Nicola Dell** (Cornell Tech), com Diana Freed e Sam Havron, sobre *clinical computer
security*: profissionais treinados atendem vítimas para diagnosticar insegurança
digital e orientar remediação.

No estudo apresentado no USENIX Security 2019, em parceria com o escritório da
prefeitura de Nova York para violência doméstica e de gênero, os pesquisadores
encontraram **spyware potencial, contas comprometidas ou configurações exploráveis em
23 de 44 clientes atendidos**
([USENIX Security 2019](https://www.usenix.org/conference/usenixsecurity19/presentation/havron),
[Cornell](https://news.cornell.edu/index%2Ephp/node/312811)).

**Por que importa aqui:** o adversário com acesso físico e às contas do aparelho é um
modelo de ameaça **documentado empiricamente**, não uma hipótese nossa. Em mais da
metade dos casos atendidos havia comprometimento real do dispositivo.

→ Isso reforça [C4](02-modelo-de-ameaca.md) (histórico local é ativo incriminador) e
[C8](02-modelo-de-ameaca.md) (a frase não é credencial), e é a melhor citação
disponível para justificar o rigor do hardening.

⚠️ Não confirmei se essa literatura trata especificamente do risco de **apps de
segurança** serem descobertos. A busca não achou; os artigos originais podem tratar.
Vale ler antes de citar esse ponto específico.

## O que, afinal, é novo no Camaleão

Sendo rigoroso, e esta honestidade é o que sustenta a defesa:

| Aspecto | Novidade |
|---|---|
| App camuflado para DV | ❌ nenhuma. Clique 180 e Aspire News fazem |
| Alerta com GPS e áudio | ❌ nenhuma. Botão do Pânico Virtual faz, oficialmente |
| **Gatilho por frase, com KWS local offline em pt-BR** | ✅ **não encontrei equivalente** |
| **Não exigir medida protetiva** | ✅ público diferente dos programas estaduais |
| **Máquina de estados honesta de entrega** | ✅ a falha do Aspire News sugere que é incomum |
| **Modelo de ameaça explícito, com indicadores de SO tratados como risco** | ✅ a crítica de Durham sugere que é raro |

### Como apresentar isso

Não: "criamos um app de proteção para mulheres".

Sim, aproximadamente:

> Apps camuflados de proteção existem, inclusive no Brasil — o Clique 180 tem tela de
> disfarce desde 2018, e o Botão do Pânico Virtual do Paraná já grava áudio ambiente
> oficialmente. O que não encontramos em nenhum deles é **acionamento por frase falada
> reconhecida localmente, sem nuvem e sem internet**, que resolve o caso em que a
> pessoa não consegue tocar no aparelho. E a literatura — Durham, Cornell Tech —
> mostra que apps desse tipo podem causar dano quando descobertos; por isso o projeto
> tem modelo de ameaça explícito e não promete invisibilidade que o sistema
> operacional não permite.

Essa formulação é mais forte do que alegar originalidade total, e sobrevive à pergunta
difícil — que vai vir.
