# 10 — Riscos e questões abertas

Tudo que **não** está decidido mora aqui. Quando uma questão fechar, ela sai deste
documento e vira um ADR em [`.claude/decisions/`](../.claude/decisions/).

## Questões abertas

### Q1 — A notificação obrigatória delata a usuária?

O Android exige notificação persistente no Foreground Service; o iOS mostra indicador
laranja; as lojas exigem indicação clara de captura de microfone. Para o
[modelo de ameaça](02-modelo-de-ameaca.md), isso é exatamente o que pode expor a
usuária ao agressor que olha o telefone.

**Tensão genuína, sem solução limpa.** Opções:

| Opção | Custo |
|---|---|
| Notificação discreta e coerente com a Fachada | pode ser lida como enganosa pelas lojas |
| Notificação honesta e explícita | expõe a usuária |
| Sentinela armada só por janelas curtas, sob demanda | reduz exposição, reduz proteção |

**Decide:** time + orientação. **Bloqueia:** Fatia 2, tarefa 2.3.

### Q2 — Existe cancelamento seguro de um disparo?

Contagem regressiva visível informa o agressor e pode ser cancelada por ele.
Cancelamento invisível é indistinguível, para a usuária, de um sistema que não
disparou. Ver [C3](02-modelo-de-ameaca.md).

**Hipótese a avaliar:** não oferecer cancelamento por interface; em vez disso, reduzir
o FAR até que cancelamento seja desnecessário. Isso joga todo o peso em RNF-03 — o
que é aceitável porque os gatilhos alternativos (ADR 0005) dão à usuária outro
caminho.

Se o cancelamento existir, o comando **nunca** é a Frase de Segurança (RF-38).

**Decide:** time, com quem entende de atendimento a vítimas. **Bloqueia:** Fatia 3.

### Q3 — O áudio pós-disparo deve existir?

Grava o agressor sem ciência dele. Depende de [J1](07-privacidade-lgpd.md).

**Novidade:** J1 tem **precedente favorável** — o STF consolidou no Tema 237 que
gravação feita por um dos interlocutores pode ser aproveitada como prova. Mas há
diferenças entre gravação por interlocutor, ambiental por terceiro e com auxílio
policial, e o app **não pode afirmar** que a gravação é prova válida (RF-53).

**Enquanto não houver parecer:** fica desligado por padrão (RF-46). O protótipo
demonstra o fluxo sem ele.

**Decide:** consulta jurídica. **Bloqueia:** nada do protótipo.

### Q4 — `vosk-model-small-pt-0.3` aceita gramática restrita?

**Quase respondida.** A documentação dos modelos do Vosk informa que **a maioria dos
modelos pequenos permite reconfiguração dinâmica de vocabulário**, e o `small-pt-0.3`
é classificado para mobile/Android/Raspberry Pi. O modo exige modelo compatível com
reconfiguração rápida de gramática — grafos HCLG pré-compilados não suportam.

**Decide:** experimento. É a **tarefa 1.1**, a primeira linha de código do projeto.
**Bloqueia:** a condição de validade do ADR 0002.

### Q5 — Qual é o gatilho por botão físico, exatamente?

RF-31 pede um gatilho que funcione bloqueado e sem microfone, mas não define qual.
Candidatos: padrão de volume, botão de energia repetido, tile de Configurações
Rápidas, serviço de acessibilidade.

Cada um tem restrição de plataforma própria, a verificar com a mesma disciplina de
[04-restricoes](04-restricoes-plataforma.md) — **sem** chutar.

**Decide:** investigação técnica. **Bloqueia:** Fatia 4, tarefa 4.10.

### Q6 — As suposições sobre a usuária estão certas?

[00-visao-produto](00-visao-produto.md) lista quatro suposições sobre a pessoa
protegida, nenhuma validada com ninguém. A mais frágil: que ela **configura em um
momento de segurança**. Se não houver tal momento, o produto inteiro muda.

**Decide:** conversa com quem atende vítimas — delegacia da mulher, CRAS, ONG.
**Bloqueia:** nada tecnicamente; bloqueia a confiança no produto.

### Q7 — Manter o estágio ASR carregado em RAM?

Manter o recognizer de comandos carregado reduz a latência de resposta após o gatilho e
custa memória; descarregá-lo poupa RAM e aumenta a latência. A referência do Vosk para
modelos pequenos é ~300 MB de memória de execução — em aparelho de entrada, isso é
muito.

**Decide:** medição (RNF-11), na matriz de dispositivos. **Bloqueia:** nada; é ajuste.

### Q8 — O levantamento de prior art está completo?

[docs/11](11-trabalhos-relacionados.md) foi feito por busca, não por revisão
sistemática. Três lacunas conhecidas:

- não confirmei se o **Clique 180** ainda está nas lojas (informação de 2018);
- não confirmei se existe algum app, em qualquer país, com **KWS local para frase de
  coação** — a ausência de resultado não é prova de ausência;
- não confirmei se a literatura de *clinical computer security* trata do risco
  específico de **apps de segurança** serem descobertos pelo agressor.

A terceira é a mais valiosa: se existir, é a melhor citação possível para
[C1](02-modelo-de-ameaca.md).

**Decide:** uma hora de busca, ou uma consulta à biblioteca da instituição.
**Bloqueia:** nada do protótipo; fortalece a apresentação.

## Riscos

Prioridade, impacto e mitigação. A tabela consolida os dois documentos de referência.

### Críticos

| Risco | Impacto | Mitigação |
|---|---|---|
| **R1 — Não existe modelo KWS sherpa pt-BR** | bloqueia keyword portuguesa confiável na arquitetura alvo | Vosk + gramática como estágio 1 do protótipo (ADR 0002); modelo pt-BR a partir de Common Voice CC0 como trabalho posterior |
| **R2 — Android não religa FGS de microfone após boot** | proteção fica inativa após reboot | restaurar configuração + notificação obrigatória de rearme (RF-28); **nunca** exibir "armada" |
| **R3 — iOS não oferece daemon de boot para esse caso** | paridade "Ok Google" impossível para app comum | assumir reativação manual e comunicar o estado real |
| **R4 — Gravação totalmente oculta viola requisito publicado da App Store** | distribuição inviável no iOS | consentimento e indicador conforme 2.5.14; RNF-09; ver Q1 |

### Altos

| Risco | Impacto | Mitigação |
|---|---|---|
| **R5 — Política de loja: a camuflagem pode ser proibida** | suspensão / distribuição inviável | ver detalhamento abaixo |
| **R6 — Falso negativo da keyword** | a pessoa pede ajuda e nada acontece | keyword calibrada, gatilhos alternativos (ADR 0005), testes de positivos difíceis |
| **R7 — Falso positivo** | alerta indevido, evidência indevida, frase vazada | frase longa, thresholds por frase, segunda etapa de comando, centenas de horas de negativos |
| **R8 — Vosk iOS sem caminho de instalação pronto** | atraso grande na implementação | **PoC iOS imediata**, não na última etapa (ADR 0003) |
| **R9 — "SHA-256 = cadeia de custódia"** | promessa jurídica incorreta | manifesto assinado e encadeado, audit trail, criptografia (ADR 0004); RF-53 |
| **R10 — Interface diz "enviado" sem rede** | falsa sensação de proteção | máquina de estados `CREATED → QUEUED → SENT → ACKNOWLEDGED` (ADR 0006) |
| **R11 — Bateria reprova a Sentinela** | o recurso é abandonado pela usuária | RNF-05 medido; KWS barato antes do ASR; ASR só após gatilho |

### Médios

| Risco | Impacto | Mitigação |
|---|---|---|
| **R12 — KWS + ASR na RAM** | app morto pelo sistema em aparelho de entrada | benchmark de RSS (RNF-11), Q7, modelo pequeno |
| **R13 — Frase secreta descoberta** | acionamentos indevidos | rotação simples da frase (RF-37); **não** tratar como autenticação (ADR 0007) |
| **R14 — Evidência no aparelho é descoberta** | risco físico e de privacidade | dados cifrados, nomes internos neutros, tela sensível protegida, sem histórico por padrão ([C4](02-modelo-de-ameaca.md)) |
| **R15 — Escopo infla e nada fica pronto** | o risco mais provável desta lista | só P0 no protótipo; fatias sequenciais com critério de saída |
| **R16 — Dependência de um único aparelho de demo** | demo falha no dia | matriz de três aparelhos a partir da Fatia 2; levar dois |
| **R17 — O alerta chega e nada acontece** | o software funciona, a proteção não | declarado fora de escopo e **dito na apresentação** |

## R5 em detalhe — política de loja

A política de **Comportamento Enganoso** do Google Play proíbe apps com **recursos
ocultos, dormentes ou não documentados**, exige que descrição e imagens reflitam a
funcionalidade real, e diz explicitamente que alegar "pegadinha" ou "para fins de
entretenimento" **não isenta**
([Deceptive Behavior](https://support.google.com/googleplay/android-developer/answer/9888077)).

Um aplicativo que se apresenta como calculadora e grava áudio em background é,
literalmente, a descrição do que essa política proíbe — mesmo com a melhor das
intenções. No iOS, a regra 2.5.14 vai no mesmo sentido.

As duas pesquisas de referência tratam a publicação sobretudo como problema de
**custo** (US$ 99/ano da Apple). O problema maior é de **política**, e vale para as
duas lojas.

**Mitigação:** para o protótipo, irrelevante — instalação direta. Para produto real, o
caminho provável é **declarar a finalidade abertamente na loja** e tratar a camuflagem
como recurso documentado de segurança, não como segredo. Vários apps de segurança
pessoal fazem isso. Precisa ser verificado caso a caso, com a política na mão.

Vale dizer na apresentação: a camuflagem protege a **interface de acesso**; ela não
tenta burlar indicadores nem políticas do sistema operacional.
