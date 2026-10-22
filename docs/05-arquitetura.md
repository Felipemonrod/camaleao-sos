# 05 — Arquitetura

Duas decisões estruturais:

- ADR [0008](../.claude/decisions/0008-kotlin-nativo-sem-flutter.md) — **o protótipo
  é 100 % Kotlin nativo, Android apenas.** A fronteira entre interface e núcleo de
  microfone do ADR [0001](../.claude/decisions/0001-ui-flutter-nucleo-nativo.md)
  continua valendo, agora como separação de pacotes em vez de linguagem.
- ADR [0002](../.claude/decisions/0002-motor-de-reconhecimento.md) — **dois estágios
  de voz em série**: KWS passivo como porteiro, janela curta de ASR com gramática
  restrita depois do gatilho.

## Camadas

```
┌──────────────────────────────────────────────────────────┐
│              CAMALEÃO SOS — app/ui/  (Kotlin)            │
│   Fachada (calculadora)  ·  Área protegida  ·  Testar    │
│                                                          │
│   NÃO conhece AudioRecord. NÃO conhece o motor de voz.   │
└─────────────────────────┬────────────────────────────────┘
                          │  interface estreita
                          │  armar / desarmar / testar / observarEstado
                          ▼
┌──────────────────────────────────────────────────────────┐
│                 app/sentinela/  (Kotlin)                 │
│   SentinelaService — Foreground Service tipo microphone  │
│   ciclo de vida, permissões, interrupções                │
└─────────────────────────┬────────────────────────────────┘
                          ▼
             ┌─────────────────────────┐
             │   AudioCaptureEngine    │   ← ÚNICO dono do microfone
             │   PCM 16 kHz mono 16bit │
             └────────────┬────────────┘
                          │
              roteamento interno por estado

iOS (futuro, ADR 0003): mesma estrutura, AVAudioSession + AVAudioEngine
no lugar do Foreground Service. Fora do escopo do protótipo.
```

### Regra não negociável: um único dono do microfone

Os dois motores de voz **não** abrem capturas separadas. Existe um
`AudioCaptureEngine` e o roteamento é interno à máquina de estados. Dois donos do
microfone no Android é caminho garantido para conflito de sessão de áudio.

## Máquina de estados da voz

```
                  ┌──────────────────────────────┐
                  │      WAITING_KEYWORD         │
                  │  só o estágio KWS recebe     │
                  │  ring buffer em RAM          │
                  │  nada persistido             │
                  └──────────────┬───────────────┘
                                 │ frase detectada
                                 │ (threshold aprovado)
                                 ▼
                  ┌──────────────────────────────┐
                  │      COMMAND_WINDOW          │
         timeout  │  janela de 4–6 s             │
      ┌───────────┤  só o estágio ASR recebe     │
      │           │  gramática restrita          │
      │           └──────────────┬───────────────┘
      │                          │ comando reconhecido
      │  registra métrica,       │ confiança + correspondência
      │  NÃO executa ação        ▼
      │           ┌──────────────────────────────┐
      └──────────▶│         SOS_EVENT            │
                  │  localização · manifesto     │
                  │  evidência · fila de alerta  │
                  └──────────────────────────────┘
```

O `timeout` ambíguo **não executa ação destrutiva**: registra a métrica e volta a
`WAITING_KEYWORD`. Essa é a diferença entre um sistema que erra para o lado seguro e
um que dispara na dúvida.

### Por que dois estágios

| | Custo | Quando roda |
|---|---|---|
| **KWS** — "a frase foi dita?" | baixo | **sempre** |
| **ASR** — "qual comando?" | alto | só depois do gatilho |

Transcrever continuamente para achar uma frase é trabalho desnecessário 24 h por dia
num aparelho a bateria. Só detectar a frase não permite distinguir "enviar alerta" de
"cancelar". O handoff resolve os dois.

**No protótipo os dois estágios rodam em Vosk `small-pt-0.3`**, mudando apenas a
gramática do recognizer entre estados. A máquina de estados é a definitiva; só o
estágio 1 é provisório. Ver ADR 0002.

Decisão a **medir**: manter o recognizer do estágio 2 carregado reduz latência e custa
RAM (~300 MB de referência para modelos pequenos do Vosk); descarregá-lo poupa
memória e aumenta a latência de resposta.

## Convergência dos gatilhos

Os três Gatilhos são **independentes** e desembocam no mesmo lugar (RF-32). É isso
que elimina o ponto único de falha de [C6](02-modelo-de-ameaca.md).

```
                     CAMALEÃO SOS
                          │
           ┌──────────────┴───────────────┐
           │                              │
       Fachada                     Área protegida
           │                              │
    ┌──────┴──────┐           ┌───────────┴──────────┐
    │             │           │                      │
 código      padrão de    Frase de              configuração
 digitado    botões       Segurança              e calibração
    │          físicos         │
    │             │      KWS → ASR
    └─────────────┴────────────┘
                  ▼
          EVENTO DE COAÇÃO
```

## Do evento ao alerta

```
          EVENTO DE COAÇÃO
                  │
     ┌────────────┼────────────┬──────────────┐
     ▼            ▼            ▼              ▼
 localização   carimbo     gatilho de     evidência
              de tempo      origem        (opcional,
     │            │            │          desligada
     └────────────┴────────────┴──── por padrão)
                  ▼
        MANIFESTO DE EVIDÊNCIA           ← ADR 0004
        original_sha256
        encrypted_file_sha256
        previous_manifest_hash
        signature(device_key)
                  ▼
        armazenamento local protegido
        AES-GCM · chave por evento
        wrapped por Keystore/Keychain
                  ▼
        ┌─────────────────────────┐
        │  CREATED                │       ← ADR 0006
        │     ↓                   │
        │  QUEUED  ←─ sem rede    │
        │     ↓                   │
        │  SENT                   │
        │     ↓                   │
        │  ACKNOWLEDGED           │  recibo contra-assinado
        └─────────────────────────┘
                  ▼
             Rede de Apoio
```

Os quatro estados são visíveis e persistidos. **A interface nunca afirma mais do que
o estado atual** — "ajuda enviada" só existe em `ACKNOWLEDGED`.

## Fluxo do áudio e o Buffer pré-evento

O princípio: **nada que o microfone ouve é salvo, até existir um disparo.**

```
tempo →
   -8s        -4s         0s                 +20s
    |----------|----------|--------------------|
                          ↑
                  frase detectada

  [  ring buffer em RAM   ] [ evidência pós-disparo ]
         sobrescrito                  opcional,
        continuamente              desligado por padrão
```

É o estágio KWS, barato, que torna isso viável: o áudio em espera nunca chega ao
estágio caro nem ao disco.

**Preservar o trecho anterior ao disparo é decisão de produto, não técnica**, e
envolve gravar terceiros. Em aberto como [Q3](10-riscos-e-questoes-abertas.md).

## Comparação da frase e dos comandos

Igualdade exata é frágil e está proibida por RF-14:

```kotlin
// ❌ NÃO
if (texto == "preciso buscar meu caderno azul")
```

O caminho é normalização + similaridade:

```
Reconhecido:  "preciso busca meu caderno azul"
Cadastrado:   "preciso buscar meu caderno azul"
                        ↓
              minúsculas · pontuação removida
              espaços colapsados · acentos normalizados
                        ↓
                 similaridade textual
                        ↓
                  0,93  ≥  limiar
                        ↓
                      DISPARA
```

O limiar **não é um `> 0,80` escolhido no chute.** Sai do conjunto de validação do
projeto, na região da curva onde o FAR por hora é aceitável sem elevar o FRR. Ver
[09-plano-de-testes](09-plano-de-testes.md).

### Gramática do estágio 2

```json
["enviar alerta", "cancelar", "gravar evidência", "compartilhar localização", "[unk]"]
```

O `[unk]` é o que permite ao motor dizer "foi outra coisa" em vez de forçar a fala
para o comando mais próximo. **O comando de cancelamento nunca é a Frase de
Segurança** (ADR 0005).

### Sensibilidade configurável

Dado que falso positivo é incidente de segurança ([C2](02-modelo-de-ameaca.md)), a
sensibilidade é escolha da usuária, feita com calma na configuração:

```
Sensibilidade ALTA          Sensibilidade NORMAL
uma detecção válida         frase + confiança mínima
       ↓                    + repetição em até 2 s
   SOS imediato                      ↓
                                    SOS
```

Padrão do protótipo: **NORMAL**. Prefere-se falhar em não disparar.

## Infraestrutura da demonstração

Zero serviço externo (RNF-01):

```
   SMARTPHONE
       │ Wi-Fi local, sem internet
       ▼
NOTEBOOK DA EQUIPE
       │ FastAPI ou Node · TLS
       ▼
     PAINEL
 "Alerta recebido · hora · localização · gatilho · manifesto ✓"
       │
       └──▶ recibo contra-assinado de volta ao aparelho
```

**Offline-first vale para a detecção, não para a entrega.** KWS, ASR, manifesto,
criptografia e fila são locais. A mensagem precisa de transporte — e é por isso que
o ADR 0006 existe.

Para um **produto real**, "R$ 0 para sempre" e "alerta remoto confiável em
emergência" são objetivos em tensão: alguém tem de manter infraestrutura e
conectividade. O SOT separa deliberadamente **protótipo gratuito** de
**infraestrutura de produção** — a segunda está fora de escopo.

## Divisão de implementação

| Parte | Onde |
|---|---|
| Fachada / calculadora | Kotlin — `app/ui/` |
| Área protegida, cadastro, calibração | Kotlin — `app/ui/` |
| Armazenamento de chaves | Kotlin — Android Keystore |
| Botão "Armar Sentinela" | `app/ui/` → interface estreita de `app/sentinela/` |
| Ciclo de vida em background | Kotlin — Foreground Service tipo `microphone` |
| `AudioCaptureEngine` | nativo, **um só** |
| Estágio KWS | nativo — Vosk+gramática no protótipo, sherpa-onnx depois |
| Estágio ASR | nativo — Vosk `small-pt-0.3`, gramática de comandos |
| Máquina de estados da voz | nativa, junto aos motores |
| Buffer pré-evento | RAM, nativo |
| Manifesto, assinatura, AES-GCM | nativo |
| Ocorrência e fila | Kotlin — SQLite cifrado, `app/sos/` |
| Localização | Kotlin — APIs nativas |
| Painel e recibo | web, servidor local |
| Internet para detectar a frase | **não necessária** |
| Internet para **entregar** o alerta | **necessária** — ADR 0006 |
| API paga | **nenhuma** |
