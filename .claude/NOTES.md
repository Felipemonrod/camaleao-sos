# NOTES — Camaleão SOS

Anotações cronológicas. O que virou decisão formal mora em `decisions/` e **não** se
repete aqui.

---

## 2026-10-21 — Criação do SOT

Partida: duas pesquisas sobre a viabilidade do "Modo 2" (ativação por frase de
segurança), ambas preservadas em `referencias/`. A segunda (sherpa+Vosk) é bem mais
aprofundada e prevalece onde divergem.

Montado o SOT: 11 documentos em `docs/`, 7 ADRs, requisitos numerados RF/RNF/CR.

### O que as pesquisas acertaram, e que foi mantido

- A tabela de ciclo de vida de Android/iOS, com fonte primária. É o ativo mais valioso
  dos dois documentos.
- O reenquadramento de "app camuflado que grava sempre" para "Sentinela armada
  previamente".
- Rejeitar Picovoice por licença e `SpeechRecognizer` por não ser feito para uso
  contínuo.
- O roteiro de demonstração com **teste negativo** ("Vou buscar meu caderno azul" não
  ativa). Melhor ideia dos dois documentos.
- A arquitetura de dois estágios KWS → ASR, e o diagnóstico de que não existe modelo
  KWS pt-BR oficial.

### Onde o SOT divergiu

| Tema | Pesquisa | SOT |
|---|---|---|
| Motor do MVP | whisper.cpp (pesquisa 1) | Vosk em dois estágios — ADR 0002 |
| PoC iOS | implícita, tardia | antecipada como spike na Fatia 2 — ADR 0003 |
| Terceiro gatilho | "gesto discreto" | padrão de botões físicos, que funciona bloqueado — ADR 0005 |
| Modelo de ameaça | ausente nos dois | documento próprio, `docs/02` |

### Pendências que não são técnicas

- **Documento do hackathon não veio nas exportações.** Os dois PDFs o citam
  (`fileciteturn0file0`). Sem ele, a visão de produto é reconstrução. **Conseguir esse
  arquivo é a pendência mais barata e mais valiosa da lista.**
- Q6 — nenhuma das suposições sobre a usuária foi validada com quem atende vítimas.
- J1 a J5 — pendências jurídicas. J1 tem precedente favorável (STF Tema 237), as
  outras estão abertas. Há faculdade de Direito na instituição.

### Para a próxima sessão

1. **Tarefa 1.1**: `vosk-model-small-pt-0.3` aceita `SetGrm`? Responde Q4 e valida o
   ADR 0002. Primeira linha de código do projeto.
2. Decidir o texto da notificação (decisão posterior no ADR 0009) (texto da notificação do FGS) com a orientação — bloqueia a Fatia 2.
3. Conseguir o documento do hackathon.

---

## 2026-10-21 (2) — Preparação para GitHub e três decisões novas

### Decisões tomadas pelo time nesta sessão

| Decisão | Registro |
|---|---|
| Protótipo em **Kotlin nativo**, sem Flutter | ADR 0008 (novo). Substitui em parte o 0001 |
| Protótipo **Android apenas** | confirma o ADR 0003, que já dizia isso |
| Motor pela **união de Vosk com KWS** | confirma o ADR 0002, que passou a `aceito, condicionado a tarefa 1.1` |

O ADR 0008 derruba o Flutter por dois motivos: Android-only tira o benefício
cross-platform que justificava, e o prazo de um dia não paga o custo do Platform
Channel. A fronteira `ui/` ↔ `sentinela/` do ADR 0001 continua valendo, agora como
separação de pacotes.

### Sistema SOT extraído do projeto

O método virou documentação própria, para que outras pessoas e outros agentes de IA
contribuam sem divergir:

- `SISTEMA-SOT.md` — seis invariantes, onde cada fato mora, como escrever requisito e
  ADR, como fechar questão aberta, voz e forma, e como transplantar para outro projeto
- `AGENTS.md` — contrato para agentes de IA: ordem de leitura, dez regras rígidas,
  fluxos, quando parar e perguntar, anti-padrões de texto gerado por IA
- `CLAUDE.md` — aponta para o AGENTS.md e acrescenta o específico do Claude Code
- `CONTRIBUTING.md` — fluxo para pessoas, com as cinco devoluções mais comuns
- `tools/verificar-sot.py` — **o que faz o sistema funcionar.** 9 verificações
- CI em `.github/workflows/`, templates de PR e de issue

A aposta do desenho: o que garante consistência entre agentes não são as instruções,
é o script. Instrução se ignora; CI não.

O verificador já se provou nesta sessão — pegou três links para um `RELATORIO-IA.md`
que ainda não existia.

### Novo documento: docs/11, trabalhos relacionados

Lacuna séria que nenhuma das duas pesquisas cobria. O resultado reposiciona o projeto:

- **app camuflado para violência doméstica não é novidade**, inclusive no Brasil — o
  Clique 180 tem tela de disfarce desde 2018;
- **gravar áudio como evidência também não** — o Botão do Pânico Virtual do Paraná faz
  isso oficialmente, com a PM recebendo o alerta;
- o que não encontrei equivalente: **KWS local em pt-BR para frase de coação**, e a
  máquina de estados honesta de entrega.

Duas descobertas que valem citação na apresentação:

1. O **Aspire News** (EUA) teve a alegação de alerta às autoridades contestada e
   **vazou áudios de vítimas** num servidor sem proteção. É a melhor justificativa
   externa para o ADR 0006 e para o hardening de `docs/07`.
2. Um estudo da **Universidade de Durham** concluiu que alguns desses apps podem
   causar mais dano do que benefício. É o nosso `docs/02`, dito por pesquisadores.

### Corte de 24 horas

O plano completo de `docs/08` é de semanas; o prazo é de um dia. Acrescentei a seção
`⏱ Corte de 24 horas` com dez entregas (D1 a D10), o que fica de fora, **o que dizer
à banca sobre cada corte**, e plano B decidido agora em vez de às três da manhã.

A regra da seção: cortar é obrigatório, mentir sobre o corte não é.

### Para a próxima sessão

1. **D1 / tarefa 1.1** — `vosk-model-small-pt-0.3` aceita `SetGrm`? Primeira linha de
   código. Se falhar, o dia muda de forma.
2. Definir a licença do repositório antes de torná-lo público — está marcada com ⚠️ no
   README.
3. Conseguir o documento do hackathon.
4. Decidir o texto da notificação (decisão posterior no ADR 0009) (texto da notificação do FGS) — bloqueia D5.

---

## 2026-10-21 (3) — Dossiês de pesquisa e pasta `pesquisa/`

Criada a pasta `pesquisa/` com dossiês dos dois sistemas mais próximos do Camaleão.
A divisão: `docs/11` é dono das **conclusões**, o dossiê é dono da **investigação**.
Numeração `Q-XXX-n` para não colidir com as `Qn` de `docs/10` — a diferença é que `Qn`
bloqueia o projeto e `Q-XXX-n` só o melhora. Registrado em `SISTEMA-SOT.md` §3 e
checado pela nova verificação V10 (dossiê órfão falha o CI).

### O achado que importa

A SESP-PR alega que o Botão do Pânico Virtual funciona **sem internet nem crédito no
celular**. O nosso ADR 0006 afirma o contrário para a entrega do alerta. Uma das duas
afirmações está incompleta.

Quatro hipóteses no dossiê, e duas delas mudariam o projeto: se for SMS como fallback,
entra na arquitetura; se for zero-rating por acordo com o Estado, é um bom argumento
para explicar à banca por que o Camaleão **não** pode prometer o mesmo.

É a pergunta mais valiosa da pesquisa agora — `Q-BPV-1`.

### Outras duas coisas que já dá para aproveitar

- **Desacoplamento alerta ↔ gravação.** No serviço do PR, fechar o app durante a
  gravação não interfere no atendimento. É o princípio do ADR 0006, validado por um
  sistema em produção. ⚠️ Não está explícito nos nossos requisitos — candidato a
  requisito novo em `docs/03`.
- **Fachada funcional > fachada passiva.** O Clique 180 usa uma frase motivacional; a
  nossa calculadora calcula. Isso transforma o RF-01 em requisito de **segurança**, não
  de polimento.

### Pendência mais barata do projeto

Buscar "Clique 180" nas duas lojas. Cinco minutos, e decide se podemos citá-lo na
apresentação — além de gerar evidência sobre a camuflagem passar ou não pela revisão da
Apple (regra 2.5.14, nosso R4).

## 2026-10-21 — Implementação Android e Painel local

Implementação na branch `feat/app-android-funcional`. Escopo executável e roteiro no
[estado da implementação](../docs/08-plano-prototipo.md#estado-da-implementação).
Resultados verificáveis no [registro de execução](../docs/09-plano-de-testes.md#registro-de-execução).
A decisão humana sobre notificação e cancelamento foi registrada no
[ADR 0009](decisions/0009-notificacao-e-cancelamento-protegido.md); apontadores atualizados.
A observação de gramática apoia o ADR 0002; a questão experimental continua em revisão
formal do time. As referências de origem e os ADRs aceitos anteriores foram preservados.

Revisão final: migração de atualização verificada e registrada nos documentos donos
[07](../docs/07-privacidade-lgpd.md#controles-entregues-e-limites-pendentes) e
[09](../docs/09-plano-de-testes.md#registro-de-execução). A entrega permanece local,
conforme escolha explícita do responsável; sem publicação remota.

## 2026-10-22 — Preparação para publicação no GitHub

O responsável autorizou publicar os fontes em uma nova branch do repositório
Felipemonrod/camaleao-sos. A cópia de Downloads foi comparada com os arquivos
versionados da área de trabalho antes da preparação.

As exclusões de publicação estão em [`.gitignore`](../.gitignore), incluindo
dependências Node.js, segredos, dados locais e modelos de voz. O Gradle Wrapper
permanece versionado. A branch de publicação parte da implementação local e mantém
o histórico da documentação existente; a branch principal não é alterada.

## 2026-10-22 — Correção da preparação do Android no CI

A primeira execução remota parou em `setup-android`, ao tentar instalar o pacote
`tools` da configuração padrão da action. A lista de pacotes em
[`android.yml`](../.github/workflows/android.yml) passa a indicar os componentes
usados pela compilação. O resultado da execução original está no
[job do GitHub Actions](https://github.com/Felipemonrod/camaleao-sos/actions/runs/39777720370/job/119820816885).

## 2026-10-22 — Funções por voz, SMS, voz medida e passagem de sessão

Decisões do responsável em [ADR 0010](decisions/0010-palavra-por-funcao-e-sms-automatico.md);
estágio 1 refeito em [ADR 0011](decisions/0011-estagio-1-com-iscas-e-segunda-leitura.md);
números em [docs/06](../docs/06-reconhecimento-de-voz.md#ajuste-medido-do-estágio-1-e-das-palavras)
e execução em [docs/09](../docs/09-plano-de-testes.md#registro-de-execução).

O responsável **adiou a avaliação jurídica** da gravação para depois do hackathon
(Q3 e J1 continuam abertas). Pediu testes só no Galaxy S25 pessoal, sem emulador:
não mexer em Wi-Fi/dados/configurações do aparelho e apagar os dados do app
(`pm clear br.com.camaleao.sos`) ao fim de cada rodada.

Achados que valem lembrar:

- Defeito antigo corrigido: a gramática era montada sem acentos; o vocabulário do
  modelo é acentuado e o Vosk descarta palavra ausente sem erro.
- `server/setup.mjs` usava o primeiro IP do Windows (no PC, o do Radmin VPN): agora
  lista as redes, ignora adaptadores virtuais e aceita `--ip`/`--renovar`.
- Testes que apagam a tela deixam o aparelho bloqueado: ficam em `ScreenOffTest`, que
  `tools/validar.ps1` roda por último; o script recusa começar com o celular bloqueado.
- SMS: o S25 do responsável tem a operadora recusando SMS; outro aparelho enviou.

### Passagem para a próxima sessão

Pendente, em ordem:

1. Bateria completa no S25 com a tela desbloqueada de fato (`tools/validar.ps1 -Serial <serial do aparelho>`).
2. APK de **release assinado** + página com QR para a banca instalar; o responsável sobe
   na Cloudflare manualmente. Testar a instalação num celular sem modo desenvolvedor
   (risco de o Play Protect bloquear: perfil parecido com stalkerware).
3. Sessão com a voz real (roteiro em docs/08) e medição de bateria fora do carregador.
4. Ensaio da demo no hotspot: `tools/preparar-notebook.ps1 -Ip <ip> -Renovar` no notebook.

Ambiente: notebook `desktop-6gaa1oa` acessível por SSH via Tailscale com chave dedicada
(`~/.ssh/id_camaleao_notebook`, só no PC do responsável); projeto copiado em
`C:\Users\felipe\Documents\camaleao-sos` (cópia de arquivos, não clone do Git); firewall
8443 liberado só em rede Privada. Corpus e avaliador de voz em `tools/voz/`
(dados em `dados-brutos/`, ignorado).

### Página da banca (feito depois da passagem)

Página com QR em https://felipemonrod.github.io/camaleao-sos/ (branch `gh-pages`, só o
site; não publica `docs/`). APK de release 0.3.0 na pré-release `v0.3.0-demo`, SHA-256
`b390c1c0…aff715`. Assinatura: chave em `~/.camaleao/release.jks`, senha em
`keystore.properties` (ambos fora do Git, só no PC do responsável — **fazer backup**: sem
essa chave, uma nova versão exige desinstalar a anterior). O build release não é
depurável: modo apresentação e log de diagnóstico ficam desligados. Instalação num
celular sem modo desenvolvedor ainda não testada (risco de Play Protect).

## 2026-10-22 — SMS no Xiaomi e função de ligação (ADR 0012)

Colega instalou o APK 0.3.0 da página num Xiaomi e o SMS não saiu, mesmo liberando
"aplicativos externos" nas configurações. Causa provável: no Android 15+ a permissão de
SMS é configuração restrita para app instalado fora da loja (App info → ⋮ → Permitir
configurações restritas, opção que só aparece depois de uma negação e às vezes some em
Redmi), mais a camada própria de permissões do MIUI/HyperOS ("Outras permissões").
Não confirmado no aparelho: falta o registro da tela de Ocorrências e a versão do sistema.
Defeito nosso, ainda aberto: `EmergencySms.send` sem permissão devolve 0 sem registrar nada.

Localização por SMS já existia (link do mapa e precisão). Ligação: o responsável queria
voz robotizada para quem atende, celular mudo e insistir até atender. Limites
explicados (sem injeção de áudio na chamada nem estado "atendida" para app comum; tela
de chamada quebra o RF-47). Decisões do responsável: função própria `ligar`, desligada
por padrão; telefones vão no pedido ao Painel; sem Twilio (prazo de horas), com
discador próprio. Resultado no ADR 0012 e em docs/09.

Para rodar o caminho B: segundo Android com chip e créditos ligado por USB ao notebook,
`DISCADOR=adb DISCADOR_SERIAL=<serial> npm start` em `server/` (ou `DISCADOR=simulado`
para ensaiar). O notebook fala pelos alto-falantes, então o aparelho de discagem fica
ao lado dele com o som ligado. Caixa postal conta como atendida.

Pendente: testes em aparelho listados em docs/09; aviso visível de "SMS sem permissão"
e plano B pelo app de mensagens (`ACTION_SENDTO`) não foram feitos; nova release só
depois do teste. Nada commitado nesta etapa.

## 2026-10-22 — frase nova que não armava e release 0.4.0

Usuário com três positivas guardadas não conseguia calibrar a frase nova. Causas
corrigidas: (1) a tela guardava como positiva uma leitura que a Sentinela não
reconheceu, e com o limite de 3 sem como apagar a calibração travava; agora só leitura
reconhecida conta, negativa que dispara é avisada e há "Recomeçar amostras"; (2) o teste
de frase não juntava a frase cortada por micro-pausa, como o modo armado já fazia; (3) a
thread da sessão de teste, ao terminar depois do `onDestroy`, marcava desarmado e podia
parar a sessão armada logo em seguida. Também: SMS sem permissão ou sem consentimento
agora fica registrado como `FALHOU` em vez de sumir. Testes unitários passam; nada
disso foi testado no S25 ainda. APK 0.4.0 publicado na pré-release `v0.4.0-demo` para um
amigo testar pela página.

## 2026-10-22 (noite) — teste com voz real em casa e 0.5.0

Localização com tela apagada falhava 3 em 5: o `LocationProbe` pedia só ao GPS, que não
fixa dentro de casa com a tela apagada. Agora pede a fused, rede e GPS juntos (5 em 5).
A calibração virou "segurar para falar", com porcentagem, e a negativa parecida passou a
subir um limite pessoal da segunda leitura (`verify`), em vez de travar. Armar agora diz
o que falta. O ADR 0013 trouxe o modo "só a frase", a posição contínua armada, o SMS
repetido por 30 min, a agenda com número normalizado (código de operadora e nono
dígito) e as ligações que param. Pendências reais: ver o fim de docs/09. A
`stay_on_while_plugged_in` do S25 já era 15 e foi mantida.


## 2026-10-24 — Função na mesma fala, gravação com começo e revisão da ligação

A banca foi adiada. O responsável pediu três coisas e uma revisão, com o relato de um
colega de que SMS, ligação, parada de ligações e localização funcionaram no aparelho
dele — relato, não evidência conferida; o único problema apontado foi a gravação.

Decisões no [ADR 0014](decisions/0014-funcao-na-mesma-fala-e-gravacao-honesta.md),
requisitos novos RF-69 a RF-71 em [docs/03](../docs/03-requisitos.md), execução real em
[docs/09](../docs/09-plano-de-testes.md#registro-de-execução).

### O que estava errado

- **A pausa entre a frase e a função era obrigatória por construção.** A gramática do
  estágio 1 nunca teve as palavras de função, e o `reset()` ao aceitar a frase descartava
  o resto do áudio. Explica as quatro frases aceitas sem disparo em 22/10.
- **A gravação começava depois do disparo**: o arquivo abria com a cena em andamento.
  É o que o colega ouviu.
- **A ligação abria um buraco silencioso na gravação.** O prazo era testado dentro do
  `accept()`, que não roda com o microfone cedido à chamada: a gravação ficava
  `GRAVANDO` a chamada inteira, e o arquivo colava os dois lados do corte sem marca.

### O que foi feito

A releitura com gramática combinada reaproveita o Buffer pré-evento que a segunda
leitura já usava, e roda **só quando a frase é aceita** — no caminho comum não há custo
novo. A posição (comando depois da frase) é o que impede palavra dita antes valer como
função; a comparação é de igualdade exata, nunca similaridade. O pré-rolo e o `gap_s`
saem do mesmo buffer e do mesmo laço.

Escolha que vale lembrar: o corte é **declarado**, não preenchido com silêncio.
Fabricar amostras num arquivo chamado de evidência é pior que admitir o corte.

### Revisão da ligação

Um defeito real encontrado e corrigido: "Parar ligações" tocado durante os até 10 s de
espera por posição era apagado pelo `run()` ao entrar. Agora `OpenLineCall.begin()` abre
o pedido lá na `Actions.call`.

Dois limites registrados e **não** corrigidos, por serem risco maior que o desvio na
véspera: o teto de 30 min do RF-68 pode ser ultrapassado, porque `going()` só é testado
entre chamadas e cada uma pode durar 15 min; e durante a ligação a Sentinela não ouve,
então nem a frase nem o `cancelar` por voz funcionam por até 15 min.

### Nada disso foi testado em aparelho

25 testes unitários, Lint e 21 do Painel passam. Nenhum áudio passou pelo Vosk, nenhuma
frase foi falada, nenhum arquivo de evidência foi gravado. O custo em tempo da terceira
leitura na thread de captura é a medição mais importante que falta: a segunda já consome
1–2 s e o `AudioRecord` tem folga de 4 s.

### Ambiente desta sessão

`ANDROID_HOME` não estava definido e o Gradle parou; resolvido apontando para
`C:\Users\FELIPE\AppData\Local\Android\Sdk` na chamada (o `local.properties` é ignorado
pelo Git). O `npm start` não roda neste notebook: a política de execução do PowerShell
bloqueia o `npm.ps1`, e o `npm.cmd` deixa um `Deseja finalizar o arquivo em lotes (S/N)?`
pendurado no Ctrl-C — use `node server.mjs`.

Em 22/10 o S25 alcançou o Painel pelo hotspot numa rede classificada como **Pública**,
com a regra de firewall valendo só em Privada e sem nenhuma outra regra encontrada para
a porta 8443 ou para o `node.exe`. O alcance é fato (`HTTP 200` pedido do aparelho); a
explicação não. Na banca, se não conectar, marcar a rede do hotspot como Privada.

## 2026-10-25 — RF-69 comprovado no S25 e o limite da chave de release

Bateria completa no S25 com o APK debug do commit `3302845`: 25 unitários, 21 do Painel,
`OK (17 tests)` nos instrumentados e `OK (2 tests)` no `ScreenOffTest`. Detalhes em
[docs/09](../docs/09-plano-de-testes.md#registro-de-execução).

O que mudou de status: a função dita **na mesma fala** da frase agora tem prova no motor
real, em aparelho real — `ALERT` disparou e a `COMMAND_WINDOW` não abriu. A amostra
contrária (palavra antes da frase) também passou: nada disparou. Voz sintética, não
pessoa.

Duas armadilhas de ambiente que custaram uma bateria cada:

- Os `.wav` de `app/src/androidTest/assets` são ignorados pelo Git e não existiam neste
  notebook: 3 testes falharam com `FileNotFoundException` até rodar
  `tools/gerar-amostras.ps1`. Vale rodar o gerador **antes** da primeira bateria em
  máquina nova.
- O `ScreenOffTest` termina deixando o aparelho bloqueado, e o `validar.ps1` se recusa a
  começar bloqueado. Duas baterias seguidas exigem desbloquear no meio.

Para instalar o debug foi preciso desinstalar a release 0.5.0 (assinaturas diferentes,
mesmo `applicationId`). O responsável autorizou desinstalar a antiga e instalar a nova
quando for necessário.

**O limite que decide a apresentação:** a chave de assinatura não está neste notebook
(`release.jks` e `keystore.properties` ausentes). A 0.5.0 da página **não contém** o
ADR 0014. Levar estas melhorias à banca exige dar merge no PR #4 e gerar a 0.6.0 no PC
que tem a chave, republicando a página com o SHA-256 novo.

Auto-fix ligado no PR #4 (CI verde, `MERGEABLE`/`CLEAN`).

Pergunta do responsável, respondida sem implementar: **enviar o áudio por SMS** para a
prova não morrer com o aparelho. Não cabe — SMS carrega 140 bytes por parte e nem o
multipart chega perto de um minuto de áudio; MMS caberia, mas exige dados móveis, custo
e ser o app de SMS padrão, e o chip do S25 já recusa SMS comum. O caminho que o projeto
já tem para isso é subir a evidência ao Painel pelo transporte HTTPS com fila e recibo
(RF-43, RF-45). Isso é decisão nova e mexe com J1 e com o docs/07: não foi feito.

## 2026-10-25 (tarde) — o log desmentiu a boa notícia, e isso foi útil

O responsável testou com a própria voz e relatou que funcionou perfeitamente. Funcionou
mesmo: cinco frases aceitas com `nota=1.00` e os comandos `ligacao`, `alerta` e
`gravacao` disparados. Mas o log não tem **nenhuma** linha `na mesma fala`: os cinco
disparos saíram pela `COMMAND_WINDOW`. O RF-69 continua provado só com amostra
sintética.

Os intervalos entre a frase aceita e o comando, medidos no log: 2,0 s · 2,9 s · 2,7 s ·
4,1 s · 2,5 s. Com janela de 6 s, quem hesita perde. É o dado que motivou o
[ADR 0015](decisions/0015-janela-escolhida-e-compatibilidade-de-aparelho.md), e o pedido
do responsável por 30 s tinha razão de ser.

**Achado bom no mesmo log:** `parada: armada=true pedido=true` depois de duas chamadas
de 15 s e 10 s. O botão "Parar ligações" para as rodadas com a Sentinela ainda armada —
o RF-68 saiu da lista de não comprovados nessa parte. O log não distingue recusa de não
atendimento, então a prova específica com chamada recusada continua pendente.

### O que entrou

Janela de comando virou ajuste da usuária (5 a 60 s, padrão 30 s) e as telas passaram a
citar o valor em uso em vez de um `6` fixo. A duração da gravação já era ajuste e virou
requisito (RF-74). "Parar gravação" passou a valer com o microfone na chamada — era o
mesmo defeito do prazo, no mesmo ponto do laço.

`Compatibility.kt`: diagnóstico no aparelho que separa permissão negada, permissão
**bloqueada por configuração restrita** (instalação fora da loja, Android 13+) e recurso
que o aparelho não tem. É o que faltava para o colega do Motorola entender por que o SMS
não saía — e o registro de falha do `EmergencySms` agora diz qual dos três é.

### Custo declarado, não escondido

A janela de 30 s multiplica por cinco a janela em que uma palavra de função é aceita
depois de um reconhecimento correto da frase. A medição de falso alarme com a janela
longa **não foi feita**, e é o que condiciona o status do ADR 0015.

### Pendente

O S25 foi desconectado antes de rodar a bateria instrumentada com estas mudanças. A
página do `gh-pages` foi reescrita (menos cara de texto gerado, mais seção de problemas
de permissão de SMS/ligação) mas **não foi publicada** — espera o aval do responsável.

## 2026-10-25 (fim) — preparo da 0.6.0 e a instrumentação da intermitência

Versão subida para **0.6.0 / versionCode 6** em `app/build.gradle.kts`. O APK assinado
**não** sai deste notebook: `release.jks` e `keystore.properties` continuam ausentes
(conferido de novo nesta sessão).

### Para publicar a 0.6.0, no PC que tem a chave

1. `git checkout main && git pull` (depois do merge do PR #4).
2. `./gradlew :app:assembleRelease` — exige `keystore.properties` no lugar.
3. Conferir o SHA-256 do APK gerado.
4. Criar a pré-release `v0.6.0-demo` e subir o APK.
5. Em `gh-pages/index.html`, trocar três coisas: o `href` do botão, o rótulo
   "0.5.0 · 73 MB" e o SHA-256 do rodapé. Trocar também a frase "Na versão 0.5.0 a
   janela para a palavra é de 6 segundos" — na 0.6.0 o padrão é 30 s e é ajustável.

Enquanto o site apontar para a 0.5.0, a banca baixa a versão de 22/10, sem nada do
ADR 0014 nem do 0015. Isso é consequência da chave morar em outra máquina, não escolha.

### Instrumentação da intermitência do RF-69

`sameBreath()` passou a registrar, quando não acha o comando, qual dos dois casos
ocorreu: "frase não localizada" ou "sem comando depois da frase", com a contagem de
palavras. Só contagem — transcrição em log violaria o RF-52. O `CompleteFlowTest` roda
com `diagnostics = true` para esse registro aparecer.

A reprodução ainda não aconteceu com a instrumentação no ar: a bateria seguinte parou
antes de começar porque o `ScreenOffTest` da rodada anterior deixou o aparelho
bloqueado, e o bloqueio é seguro. Rodar de novo exige a tela destravada por quem tem a
senha.

## 2026-10-25 (noite) — a causa da gravação que não fechava

Duas intermitências, uma resolvida.

**Resolvida.** `ScreenOffTest` falhava com `expected:<[CONCLUIDA]> but was:<[GRAVANDO]>`,
3 vezes em 6. O log do gravador mostrou que a parada acontecia no registro certo — então
o problema não era a parada, era a ordem. O `stop()` zerava `LiveState.recording` antes
do `update()`, e entre os dois há o fechamento AES-GCM e um SHA-256 do arquivo inteiro.
Quem esperava o fim lia `recording == false` com o registro ainda em `GRAVANDO`.

O defeito é antigo; o pré-rolo de 8 s do RF-70 triplicou o arquivo e tornou a janela
grande o bastante para aparecer. Depois da correção: **8 em 8**.

A lição que vale guardar: `LiveState` é promessa para a tela. Zerar um estado antes de o
registro refletir a realidade é a mesma classe de erro que o produto proíbe no alerta —
dizer que acabou antes de ter acabado.

**Aberta.** A falha única do RF-69 não reproduziu em mais nenhuma das 9 execuções
seguintes. A instrumentação ficou: quando a releitura não acha o comando, o log diz se a
frase não foi localizada ou se não havia comando depois dela, com contagem de palavras
(nunca texto, RF-52).

**Guarda que não era a causa.** `current === session` nos pedidos da tela foi acrescentada
na hipótese errada e ficou: o risco que ela descreve — thread de sessão encerrada
consumindo o pedido de quem está no ar — é real, mas não era este defeito.

**Armadilha de bancada:** o `ScreenOffTest` deixa a tela bloqueada e o bloqueio é seguro.
A suíte principal logo depois falha no teste da Activity com `Activity never becomes
requested state "[RESUMED]"`. É a tela, não o código. Entre uma bateria e outra, alguém
precisa destravar.
