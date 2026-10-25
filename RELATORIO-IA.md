# Relatório de uso de IA

Registro do uso de inteligência artificial neste projeto. Este é o **único** lugar
onde o uso de IA é documentado — por decisão de projeto, não há atribuição de IA em
mensagens de commit nem em descrições de PR
([AGENTS.md R10](AGENTS.md)).

Formato: uma entrada por sessão, em ordem cronológica. Cada entrada diz **o que foi
pedido**, **o que a IA produziu**, **o que foi verificado ou corrigido por uma
pessoa** e **o que não foi verificado**.

A última coluna é a mais importante. Documentação gerada por IA e não verificada é
exatamente o tipo de coisa que leva alguém a prometer, para uma banca ou para uma
usuária, algo que o sistema não faz.

---

## Como registrar uma sessão

Copie o bloco abaixo e preencha. Não apague entradas antigas.

```markdown
## AAAA-MM-DD — <assunto da sessão>

**Ferramenta:** <Claude Code / Copilot / outra, com o modelo se souber>
**Pessoa responsável:** <nome>

**Pedido:**
<o que foi solicitado, em 1–3 linhas>

**Produzido:**
<arquivos criados ou alterados, e o que mudou>

**Verificado por pessoa:**
- [ ] <o que foi conferido, e como>

**Não verificado — pendente:**
- <o que ficou sem conferência, e o que pode dar errado se estiver errado>

**Correções manuais feitas depois:**
<o que a pessoa teve de corrigir. "Nada" é resposta válida, se for verdade>
```

---

## 2026-10-21 — Criação do SOT a partir das pesquisas de viabilidade

**Ferramenta:** Claude Code (Claude Opus 5)
**Pessoa responsável:** Felipe Montalvão Rodrigues

**Pedido:**
Documentar o sistema Camaleão SOS a partir de duas pesquisas de viabilidade, dar uma
opinião crítica sobre ele, e organizar tudo num modelo SOT para começar um protótipo.
Em seguida, preparar o repositório para o GitHub, incluindo o próprio sistema SOT
documentado para que outras pessoas e outros agentes de IA possam contribuir do mesmo
jeito.

**Produzido:**

| Área | Arquivos |
|---|---|
| Método | `SISTEMA-SOT.md`, `AGENTS.md`, `CLAUDE.md`, `CONTRIBUTING.md`, `docs/_templates/` |
| Documentação | `SOT.md`, `README.md`, `docs/00` a `docs/11` |
| Decisões | `.claude/decisions/0001` a `0008` |
| Ferramenta | `tools/verificar-sot.py`, `.github/workflows/verificar-sot.yml` |
| GitHub | templates de PR e de issue, `.gitignore` |
| Referências | as duas pesquisas, íntegras, com os marcadores de citação resolvidos em links |

**Verificado automaticamente:**

- `python tools/verificar-sot.py` — 9 verificações, 0 falhas. Checa links internos,
  front matter dos ADRs, e se todo ID de requisito, questão aberta e risco citado
  existe de fato.

**Verificado por busca durante a sessão:**

- suporte do Vosk a lista de frases / gramática restrita (`vosk_recognizer_new_grm`,
  `SetGrm`), e a ressalva de que exige modelo com lookahead;
- política de Comportamento Enganoso do Google Play sobre recursos ocultos;
- prior art brasileiro: Clique 180, Botão do Pânico Virtual do Paraná;
- prior art internacional: Aspire News e as duas falhas documentadas dele;
- linha de pesquisa de *clinical computer security* (Cornell Tech).

**⚠️ Não verificado — pendente de conferência humana:**

| Pendência | Risco se estiver errado | Onde |
|---|---|---|
| As fontes de plataforma herdadas das duas pesquisas (Android FGS, iOS background, App Review 2.5.14, taxas da Apple) **não foram abertas uma por uma nesta sessão** | uma afirmação de plataforma errada em `docs/04` se propaga para a apresentação | `docs/04` |
| `vosk-model-small-pt-0.3` aceita reconfiguração de gramática? | derruba a condição de validade do ADR 0002 | [Q4](docs/10-riscos-e-questoes-abertas.md) |
| Clique 180 ainda está nas lojas? Informação de 2018 | citação desatualizada na apresentação | [Q8](docs/10-riscos-e-questoes-abertas.md) |
| Existe algum app com KWS local para frase de coação? Ausência de resultado não é prova de ausência | a alegação de diferencial enfraquece | [Q8](docs/10-riscos-e-questoes-abertas.md) |
| Pendências jurídicas J1 a J5 | afirmação incorreta sobre prova e sobre base legal | `docs/07` |

**Correções manuais feitas depois:**

<a preencher pela pessoa responsável>

**Observação sobre a origem do material:**

As duas pesquisas em `referencias/` foram produzidas por outra ferramenta de IA
(metadados indicam `gpt-5-thinking`). O SOT **divergiu** delas em três pontos, cada um
com ADR explicando o motivo — ADRs 0002, 0003 e 0005 — e acrescentou um modelo de
ameaça que nenhuma das duas tinha (`docs/02`). O ADR 0004 **corrige** a proposta
original do hackathon quanto a SHA-256 e cadeia de custódia.

Isso é relevante para o relatório: o material de partida não foi aceito como verdade,
e as divergências estão registradas e justificadas por escrito.

**Pendência de material:**

As duas pesquisas citam um **documento de proposta do hackathon**
(`fileciteturn0file0`) que não veio em nenhuma das exportações. Sem ele, a visão de
produto em `docs/00` é reconstrução, não citação. Conseguir esse arquivo é a pendência
mais barata e mais valiosa da lista.

## 2026-10-21 — Sessão de implementação com Codex

**Solicitação humana:** continuar a criação do protótipo no repositório informado,
com aplicativo funcional. O responsável aprovou notificação clara de microfone ativo
e cancelamento dentro da Área protegida; decisão em ADR 0009.

**Trabalho realizado:** código Android Kotlin, motor Vosk em dois estágios, fila
cifrada, manifesto/recibo, servidor HTTPS local, testes, bootstrap de modelos e CI.
Detalhes de entrega pertencem a [docs/08](docs/08-plano-prototipo.md#estado-da-implementação);
resultados observados pertencem a [docs/09](docs/09-plano-de-testes.md#registro-de-execução).

**Uso de ferramentas:** compilação Gradle e Android Lint, testes unitários Kotlin,
instrumentação em emulador, testes Node, leitura das fontes primárias Android e
Vosk, geração de amostras de voz sintética locais. Não houve gravação de terceiros
nem envio de SMS para participantes reais. A sessão não resolve as pendências
jurídicas, a licença institucional, as métricas físicas ou a decisão do botão físico.

**Revisão humana necessária:** comportamento e segurança em aparelhos reais,
validação de vítimas/atendimento, conformidade jurídica e os limites de hardening
registrados em [docs/07](docs/07-privacidade-lgpd.md#controles-entregues-e-limites-pendentes).
A implementação não foi declarada homologada por existir código ou por passar
em testes sintéticos. Não foi adicionada atribuição de IA em commit ou PR.

Na revisão final, foi acrescentada a migração do primeiro protótipo e seu teste de
regressão; controles em [docs/07](docs/07-privacidade-lgpd.md#controles-entregues-e-limites-pendentes).
O responsável escolheu manter somente os arquivos locais. Nenhum commit foi enviado
ao repositório remoto.

## 2026-10-22 — Preparação de publicação com Codex

**Solicitação humana:** inspecionar a pasta local, configurar o `.gitignore` e
publicar o aplicativo Android e o servidor Node.js no repositório informado,
em uma nova branch, preservando a documentação existente.

**Trabalho realizado:** comparação dos fontes de Downloads com a cópia da área de
trabalho, revisão dos arquivos versionados e do histórico local a enviar, ajuste
das exclusões de publicação e validação das regras de inclusão e exclusão.
As regras ficam em [`.gitignore`](.gitignore).

Esta sessão não altera requisitos, ADRs ou decisões de produto. A nova autorização
de publicação substitui a escolha de entrega somente local registrada na sessão
anterior.

## 2026-10-22 — Correção do CI com Codex

**Solicitação humana:** investigar a falha de validação apresentada na captura do
GitHub Actions. Foram lidos os logs remotos, a definição da action e a tabela
oficial de compatibilidade do AGP. A lista de pacotes do SDK foi explicitada no
workflow, sem alteração do aplicativo ou das decisões de produto.

Diagnóstico e execução original: [anotação da sessão](.claude/NOTES.md#2026-10-22--correção-da-preparação-do-android-no-ci).

## 2026-10-22 — Funções por voz, SMS, localização, gravação e medição de voz com Claude Code

**Solicitação humana:** restaurar o ambiente de desenvolvimento, elevar o acerto da
Sentinela usando voz sintética, e implementar contato de emergência, localização e
gravação acionados por palavra escolhida; depois, preparar o notebook e a demonstração.

**Trabalho realizado:** corpus sintético de 13 vozes e avaliador reproduzindo o app;
comparação de estratégias e nova regra do estágio 1; palavras por função, SMS
automático, localização pontual, gravação cifrada, checagem de vocabulário; testes
unitários e instrumentados; testes no Galaxy S25 do responsável; kit de teste de SMS
por Wi-Fi; setup do Painel com escolha de rede; preparação remota do notebook via
Tailscale. Decisões humanas registradas nos ADRs 0010 e 0011.

**Revisão humana necessária:** acerto com voz de pessoas reais, bateria fora do
carregador, instalação por terceiros (Play Protect), conteúdo do SMS e as pendências
jurídicas adiadas. Nenhum resultado sintético foi apresentado como medição com pessoas.
Sem atribuição de IA em commit ou PR (AGENTS.md R10).

## 2026-10-22 — Ligação para a Rede de Apoio com Claude Code

**Solicitação humana:** analisar por que o APK da página não enviou SMS num Xiaomi de
um colega e avaliar localização por SMS e ligação; depois, implementar ligação com voz
sintetizada, celular mudo e novas tentativas até alguém atender.

**Trabalho realizado:** análise do bloqueio (permissão de SMS restrita para app
instalado fora da loja no Android 15+ e camada própria de permissões do MIUI/HyperOS;
o app falha sem registro quando a permissão falta, defeito ainda aberto). A IA contestou o desenho
original: app comum não põe áudio dentro da chamada nem sabe se ela foi atendida. Com as
escolhas do responsável, implementou a função de ligação (caminho pelo Painel com
discador próprio via `adb` e voz no notebook; caminho em linha aberta no aparelho),
testes do Painel e de domínio, e o ADR 0012.

**Revisão humana necessária:** teste no S25 e num aparelho de discagem real, voz pt-BR
no notebook, comportamento no Xiaomi e a exceção ao RF-47. Nada foi marcado como
atendido sem teste em aparelho. Sem atribuição de IA em commit ou PR (AGENTS.md R10).

## 2026-10-22 — Calibração, disparo, agenda e localização com Claude Code

**Solicitação humana:** corrigir a frase nova que não armava e revisar fala, Sentinela e
cadastro; achar a falha de localização com tela apagada; simplificar a calibração
("como áudio de mensagem", com porcentagem); permitir disparo só com a frase; manter a
posição ativa enquanto armada; repetir o SMS; escolher contato na agenda sem errar o
número; fazer as ligações pararem.

**Trabalho realizado:** a IA achou e corrigiu a calibração que travava, a regra do teste
diferente do modo armado, a sessão antiga que derrubava a nova, o SMS sem permissão que
sumia, a localização só por GPS (3 falhas em 5 → 5 aprovações em 5) e o laço de ligação
que só parava pelo estado armado. Implementou a calibração por segurar para falar com
limite pessoal a partir da negativa, o ADR 0013 e os testes de domínio. Os testes com voz
real foram feitos pelo responsável e acompanhados pelo log. A IA registrou como não
comprovado o que o log não mostrou.

**Revisão humana necessária:** parada das ligações com chamada recusada, disparo só com
a frase, bateria com posição contínua, e o APK de release num celular sem modo
desenvolvedor.

## 2026-10-24 — Função na mesma fala, gravação e revisão da ligação com Claude Code

**Solicitação humana:** o responsável relatou que um colega testou SMS, ligação, parada
de ligações e localização com sucesso, apontando só a gravação de áudio como problema.
Pediu a análise dessa parte, uma avaliação do que melhorar com a banca adiada, e propôs
ele mesmo a ideia central: capturar a palavra de função na mesma fala da Frase de
Segurança, em vez de exigir a pausa. Pediu também uma revisão da ligação.

**Trabalho realizado:** a IA leu o caminho de voz e o de gravação e mostrou, no código,
por que a pausa era obrigatória — a gramática do estágio 1 nunca teve as palavras de
função e o `reset()` descartava o áudio restante. Propôs duas formas de atender à ideia
do responsável e recomendou a mais cara e mais segura (releitura do Buffer pré-evento
com gramática combinada e exigência de posição), em vez da busca no texto de vocabulário
aberto. Encontrou três defeitos na gravação que o responsável não havia pedido para
procurar: o arquivo começava depois do disparo, o prazo não vencia com o microfone
cedido à chamada, e o corte era colado sem marca. Na revisão da ligação, encontrou e
corrigiu o "Parar ligações" perdido durante a espera por posição. Escreveu o ADR 0014,
os RF-69 a RF-71 e sete testes unitários novos.

A IA recusou duas mudanças que pareciam melhorias: preencher o corte da gravação com
silêncio, porque fabricar amostras num arquivo chamado de evidência é pior que declarar
o corte; e ajustar a temporização do laço de ligação para fechar o teto de 30 min do
RF-68, por ser risco maior que o desvio sem aparelho para testar. Ambas ficaram
registradas como limites, não como pendências silenciosas.

**Revisão humana necessária:** nada desta sessão passou por aparelho. Falta a função em
voz real na mesma fala, o custo em tempo da terceira leitura na thread de captura, o
pré-rolo no arquivo exportado, o `gap_s` numa ligação real, e tudo isso no APK de
release. O relato do colega continua sendo relato: não foi conferido aqui. Sem
atribuição de IA em commit ou PR (AGENTS.md R10).

## 2026-10-25 — Prova em aparelho do RF-69 com Claude Code

**Solicitação humana:** explicar o Auto-fix do PR, testar no S25 já conectado, subir as
alterações e dizer se o app pode ser apresentado como está. O responsável também
perguntou se daria para enviar o áudio por SMS, para a prova não se perder com o
aparelho.

**Trabalho realizado:** a IA descobriu que as amostras sintéticas de
`app/src/androidTest/assets` são ignoradas pelo Git e não existiam neste notebook — três
testes falhavam por arquivo ausente, e não por defeito. Gerou as amostras, acrescentou
duas novas ao `tools/gerar-amostras.ps1` (frase e função numa tirada só, e a palavra
dita antes da frase) e escreveu dois testes instrumentados para o RF-69, que passaram no
motor Vosk real no S25: a função disparou sem a janela de comando abrir, e a palavra
anterior à frase não disparou nada.

A IA respondeu à pergunta do SMS com áudio sem implementar: o limite de carga do SMS
inviabiliza, o MMS exige dados e ser o app padrão de mensagens, e o caminho que o
projeto já tem é subir a evidência ao Painel. Apontou que isso é decisão nova e esbarra
na pendência jurídica J1, então parou e devolveu ao responsável (AGENTS.md §5).

Também registrou o limite que decide a apresentação: sem a chave de assinatura neste
notebook, o APK 0.5.0 da página não contém nenhuma das melhorias destes dois dias.

**Revisão humana necessária:** a função na mesma fala em voz de pessoa, o custo em tempo
da terceira leitura com a Sentinela armada, o pré-rolo num arquivo exportado, o `gap_s`
numa ligação real, e a geração da 0.6.0 no PC que tem a chave. Sem atribuição de IA em
commit ou PR (AGENTS.md R10).

## 2026-10-25 (tarde) — Janela ajustável e compatibilidade com Claude Code

**Solicitação humana:** o responsável testou com a própria voz, relatou sucesso, e pediu
quatro coisas: aumentar a janela de comando de 6 para 30 s, reescrever a página pública
com menos cara de texto gerado por IA, melhorar a compatibilidade com outros aparelhos
(um colega não conseguiu habilitar SMS num Motorola) e deixar a duração da gravação a
cargo do usuário.

**Trabalho realizado:** antes de aceitar o relato, a IA leu o log do aparelho e
encontrou algo que o responsável não tinha notado: os cinco disparos saíram pela janela
de comando, e **nenhum** pela releitura do ADR 0014 — o RF-69 segue provado só com
amostra sintética. Os intervalos medidos no log (2,0 a 4,1 s) confirmaram, com número, o
motivo do pedido de uma janela maior. O mesmo log trouxe evidência de uma pendência
antiga: o botão "Parar ligações" parou as rodadas com a Sentinela armada.

A IA tornou a janela um ajuste da usuária (5 a 60 s, padrão 30 s) em vez de trocar uma
constante por outra, e fez as telas citarem o valor em uso. Escreveu o `Compatibility.kt`,
que separa permissão negada, permissão bloqueada por configuração restrita e recurso
inexistente no aparelho — a causa provável do caso do Motorola. Corrigiu a parada da
gravação, que não respondia com o microfone na chamada. Reescreveu a página pública com
uma seção específica sobre destravar SMS e ligação.

A IA declarou o custo em vez de omiti-lo: a janela de 30 s multiplica por cinco o tempo
em que uma palavra de função é aceita, e a medição de falso alarme com a janela longa
não foi feita. É o que condiciona o status do ADR 0015.

**Revisão humana necessária:** bateria instrumentada com estas mudanças (o aparelho foi
desconectado antes), o diagnóstico numa tela de Motorola ou Xiaomi, o efeito da janela
longa sobre falso alarme, e a publicação da página, que não foi feita sem aval. Sem
atribuição de IA em commit ou PR (AGENTS.md R10).
