# Camaleão SOS

> Um protocolo de emergência por voz **offline**, processado no próprio aparelho, que
> permanece ativo em segundo plano **enquanto a proteção está armada**, e combina
> frase secreta, reconhecimento local de comandos, integridade criptográfica e rotas
> alternativas de acionamento.

Aplicativo de proteção discreta para situações de violência doméstica. Interface
camuflada de calculadora, múltiplos gatilhos de coação, e detecção de uma **frase de
segurança** sem enviar áudio para a nuvem.

**Estado:** implementação Android e Painel local; consulte o [estado verificado](docs/08-plano-prototipo.md#estado-da-implementação).
**Protótipo:** Kotlin nativo, Android apenas, custo zero em software e serviços.

---

## Executar o protótipo

Instalação do APK, primeiro acesso pela calculadora, pareamento HTTPS, compilação e roteiro estão em [docs/08](docs/08-plano-prototipo.md#executar-a-versão-implementada).

## 📍 Comece por aqui

| Você quer | Vá para |
|---|---|
| **Entender o projeto** | [**SOT.md**](SOT.md) — o ponto de entrada único |
| Entender o método de documentação | [SISTEMA-SOT.md](SISTEMA-SOT.md) |
| Contribuir, sendo uma pessoa | [CONTRIBUTING.md](CONTRIBUTING.md) |
| Contribuir, sendo um agente de IA | [**AGENTS.md**](AGENTS.md) — leia inteiro antes de escrever |
| Começar a codar agora | [Corte de 24 horas](docs/08-plano-prototipo.md#-corte-de-24-horas) |
| Saber o que já existe lá fora | [docs/11](docs/11-trabalhos-relacionados.md) |
| Investigar um sistema parecido | [pesquisa/](pesquisa/README.md) — dossiês com roteiro |

## O que podemos e não podemos afirmar

Isto está no topo do README de propósito. É a fronteira que separa o projeto de uma
promessa tecnicamente impossível.

| | |
|---|---|
| ✅ **Podemos** | A proteção é **armada previamente**. Enquanto estiver armada e permitida pelo sistema, o aparelho monitora a frase **localmente**. Nenhum áudio precisa sair do dispositivo durante a detecção. |
| ❌ **Não podemos** | Que funciona com o app encerrado, após "forçar parada" ou após reiniciar o telefone; que o uso do microfone fica invisível ao sistema; que o alerta chega sem conectividade; que um hash torna a gravação prova judicial. |

Cada "não podemos" tem fonte primária em
[docs/04](docs/04-restricoes-plataforma.md). Esta honestidade **aumenta** a qualidade
do projeto: demonstra que as restrições reais das plataformas foram pesquisadas.

## As três regras de honestidade

1. **O app nunca afirma proteção que não está acontecendo.** Nem "ajuda enviada" sem
   recibo, nem "armada" sem microfone ativo.
   ([ADR 0006](.claude/decisions/0006-maquina-de-estados-do-alerta.md))
2. **A frase é um gatilho acústico, não uma credencial.**
   ([ADR 0007](.claude/decisions/0007-frase-nao-e-autenticacao.md))
3. **Falso positivo é incidente de segurança, não bug de qualidade.** Um alerta
   disparado por engano, com o agressor presente, pode machucar alguém.
   ([docs/02](docs/02-modelo-de-ameaca.md))

## Arquitetura, em um diagrama

```
app/ui/  (Kotlin)  Fachada · Área protegida · Testar
     │  armar / desarmar / testar / observarEstado
     ▼
app/sentinela/     Foreground Service tipo microphone
     ▼
AudioCaptureEngine   ← ÚNICO dono do microfone, PCM 16 kHz mono
     ▼
┌─────────────────────────────┐
│ WAITING_KEYWORD             │  KWS passivo, ring buffer só em RAM
│   ↓ frase detectada         │
│ COMMAND_WINDOW              │  ASR, gramática restrita, 4–6 s
│   ↓ comando reconhecido     │
│ SOS_EVENT                   │  localização · manifesto · fila
└─────────────────────────────┘
     ▼
CREATED → QUEUED → SENT → ACKNOWLEDGED
```

Detalhe em [docs/05](docs/05-arquitetura.md).

## Pilha técnica

| Camada | Escolha | Licença | ADR |
|---|---|---|---|
| App | Kotlin nativo, Android | — | [0008](.claude/decisions/0008-kotlin-nativo-sem-flutter.md) |
| Estágio KWS (protótipo) | Vosk `small-pt-0.3` + gramática restrita | Apache-2.0 | [0002](.claude/decisions/0002-motor-de-reconhecimento.md) |
| Estágio KWS (alvo) | sherpa-onnx KWS, modelo pt-BR próprio | Apache-2.0 | [0002](.claude/decisions/0002-motor-de-reconhecimento.md) |
| Estágio ASR | Vosk `small-pt-0.3`, gramática de comandos | Apache-2.0 | [0002](.claude/decisions/0002-motor-de-reconhecimento.md) |
| Integridade | manifesto assinado + encadeado + recibo | — | [0004](.claude/decisions/0004-integridade-da-ocorrencia.md) |
| Painel da demo | servidor local, sem nuvem | — | [docs/05](docs/05-arquitetura.md) |

**Custo de software e serviços: R$ 0.** É requisito (RNF-01), não preferência. Nenhuma
API paga, nenhum crédito, nenhuma licença de avaliação.

## O bloqueio que você precisa saber

Em outubro de 2026, **não existe modelo KWS pré-treinado em português** no
sherpa-onnx. O gargalo é linguístico, não de API.

Por isso o protótipo usa Vosk com gramática restrita como estágio 1, mantendo a
**mesma máquina de estados** da arquitetura alvo — para que a troca, quando o modelo
existir, seja local. Ver [docs/06](docs/06-reconhecimento-de-voz.md) e
[R1](docs/10-riscos-e-questoes-abertas.md).

## Verificar a documentação

```bash
python tools/verificar-sot.py
```

Checa links internos, front matter dos ADRs, e se todo ID de requisito, questão aberta
e risco citado existe de fato. Roda no CI em todo PR.

## Prior art

Apps camuflados de proteção **já existem, inclusive no Brasil** — o Clique 180 tem
tela de disfarce, e o Botão do Pânico Virtual do Paraná grava áudio ambiente
oficialmente. O levantamento honesto do que é e do que não é novo aqui está em
[docs/11](docs/11-trabalhos-relacionados.md). Leia antes de apresentar.

## Estrutura

```
SOT.md                  ponto de entrada único
SISTEMA-SOT.md          o método: seis invariantes
AGENTS.md               contrato para agentes de IA
CONTRIBUTING.md         fluxo para pessoas
RELATORIO-IA.md         registro do uso de IA
docs/00..11             documentação, um dono por assunto
docs/_templates/        modelos de ADR e de documento
pesquisa/               dossiês de investigação, um por sistema estudado
.claude/decisions/      ADRs
.claude/NOTES.md        cronológico
tools/verificar-sot.py  verificador das invariantes
referencias/            material de origem, íntegro
```

## Licença

⚠️ **Ainda não definida.** O projeto é institucional e usa dependências Apache-2.0
(Vosk, sherpa-onnx); Apache-2.0 ou MIT são os candidatos naturais, mas a escolha
envolve a instituição. **Definir antes de tornar o repositório público.**
