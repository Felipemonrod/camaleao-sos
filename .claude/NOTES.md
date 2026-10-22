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
2. Decidir Q1 (texto da notificação do FGS) com a orientação — bloqueia a Fatia 2.
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
4. Decidir Q1 (texto da notificação do FGS) — bloqueia D5.
