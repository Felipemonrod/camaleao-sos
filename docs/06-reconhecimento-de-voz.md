# 06 — Reconhecimento de voz

Decisão em ADR [0002](../.claude/decisions/0002-motor-de-reconhecimento.md). Este
documento é a análise que a sustenta.

Fontes: [pesquisa 1](../referencias/pesquisa-2026-10-21.md) e
[análise sherpa+Vosk](../referencias/analise-sherpa-vosk-2026-10-21.pdf), a segunda
mais aprofundada e, onde divergem, a que prevalece.

## O problema não é transcrever — é detectar, e depois entender

Distinção que organiza tudo:

| | |
|---|---|
| **KWS** (keyword spotting) | responde "a frase X foi dita?". Barato. Vocabulário fechado. Roda **sempre**. |
| **ASR** (transcrição) | converte fala em texto. Caro. Vocabulário aberto ou restrito. Roda **só quando chamado**. |

A arquitetura correta usa **os dois, em série**:

```
sempre ligado          só após o gatilho
┌──────────────┐       ┌─────────────────┐
│  KWS passivo │──────▶│ janela ASR 4–6s │──────▶ evento SOS
│ custo baixo  │ frase │ comando restrito│ ação
└──────────────┘       └─────────────────┘
```

Rodar ASR continuamente para achar uma frase é trabalho desnecessário 24 h por dia
num aparelho a bateria. Rodar só KWS não permite distinguir "enviar alerta" de
"cancelar". O handoff resolve os dois problemas.

É isso que faz a arquitetura `sherpa-onnx KWS → Vosk ASR` ser coerente — e o motivo
**não** é as duas terem licença Apache 2.0. Licença não é arquitetura. O motivo é
que ambas aceitam streaming local em torno de PCM mono 16 kHz, o que permite um
único dono do microfone distribuindo os mesmos buffers conforme o estado.

### Regra de implementação não negociável

**Um único `AudioCaptureEngine`.** sherpa e Vosk **não** abrem dois microfones. A
captura é única e o roteamento é interno à máquina de estados. Dois donos do
microfone no Android é caminho garantido para conflito de sessão.

## Os três estados

| Estado | Quem recebe áudio | O que acontece |
|---|---|---|
| `WAITING_KEYWORD` | só sherpa KWS | ring buffer em RAM, sobrescrito; nada persistido |
| `COMMAND_WINDOW` | só Vosk, gramática restrita | janela de 4–6 s; timeout volta a KWS |
| `SOS_EVENT` | gravação pós-gatilho, se habilitada | localização, manifesto, fila de alerta |

Decisão de engenharia a **medir**, não a chutar: manter o Vosk carregado em RAM
reduz a latência de ativação mas custa memória; descarregá-lo poupa RAM e aumenta a
latência. O projeto Vosk dá ~**300 MB de memória de execução** como referência para
seus modelos pequenos — número a confirmar no aparelho e modelo escolhidos. Em
aparelho de entrada, isso é muito.

## O bloqueio central: não existe modelo KWS pt-BR

**Este é o risco crítico do projeto** ([R4](10-riscos-e-questoes-abertas.md)).

Em outubro de 2026, o sherpa-onnx **não publica modelo KWS pré-treinado oficial em
português**. Os modelos KWS oficiais listados são chinês+inglês
(`sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20`), chinês (WenetSpeech, 3,3 M
parâmetros) e inglês (GigaSpeech, 3,3 M)
([modelos KWS](https://k2-fsa.github.io/sherpa/onnx/kws/pretrained_models/)).

A API permite keywords customizadas **sem retreinar** — mas só funciona
adequadamente para idiomas representados pelo modelo acústico e pelo tokenizador.
**"Open-vocabulary KWS" não significa "qualquer idioma".** Escrever uma frase
portuguesa num modelo inglês não funciona.

O gargalo é **linguístico, não de API**: `KeywordSpotter`, `createStream()`,
thresholds e streaming já existem em Kotlin e Swift
([KeywordSpotter.kt](https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/kotlin-api/KeywordSpotter.kt)).
Falta o modelo.

## Comparativo de soluções KWS

| Solução | PT-BR pronto? | Frase customizada | Licença | Avaliação |
|---|---|---|---|---|
| **sherpa-onnx KWS Zipformer** | ❌ sem modelo oficial pt-BR | ✅ quando o modelo suporta o idioma | código Apache-2.0; **licença do modelo a verificar** | **Melhor arquitetura — exige resolver o pt-BR antes** |
| **Vosk small-pt como pseudo-KWS** | ✅ | ✅ via gramática restrita | Apache-2.0 | **Plano B, e o caminho do protótipo** |
| openWakeWord | ❌ modelos oficiais em inglês | exige treinar o modelo | código Apache-2.0; **modelos CC BY-NC-SA 4.0** | Bom tecnicamente, ruim em licença e pt-BR |
| PocketSphinx KWS | depende de modelo acústico/dicionário pt | ✅ | BSD-like | Possível, modelagem antiga |
| Mycroft Precise | ❌ sem caminho móvel pt-BR | ✅ treina qualquer wake word | Apache-2.0 | Desenhado para Linux. Pouco atraente |
| Picovoice Porcupine | ✅ | ✅ | ❌ **trial comercial** | Tecnicamente o melhor; reprova em RNF-01 |
| Android `SpeechRecognizer` | ✅ | ✅ | API do sistema | ❌ CR-04: não feito para uso contínuo |

Referência útil de metas: o openWakeWord publica objetivos de **< 5 % FRR** e
**< 0,5 falsos acionamentos por hora** para seus modelos
([openWakeWord](https://github.com/dscripka/openWakeWord)). Serve como alvo de
projeto, não como garantia para o sherpa nem para o Camaleão — e, para um app de
segurança, o FAR deveria ser bem menor.

A licença CC BY-NC-SA 4.0 dos modelos do openWakeWord é um impedimento sério:
non-commercial e share-alike num app que pode ser distribuído.

### Picovoice Porcupine — rejeitado por licença, não por técnica

Android, iOS, Flutter, palavras customizadas, português
([Android](https://picovoice.ai/quick-start/wake-word-android/),
[iOS](https://picovoice.ai/docs/quick-start/porcupine-ios/)). O impedimento: o plano
gratuito é **Free Trial para desenvolvedores empresariais**, não renova
automaticamente, e não há plano para projetos pessoais ou não comerciais
([FAQ](https://picovoice.ai/docs/faq/general/),
[termos](https://picovoice.ai/docs/terms-of-use.html)). Dependência que expira no
núcleo de um sistema de custo zero. Vetado por RNF-01 e CR-05.

## A segunda etapa: Vosk com gramática restrita

Aqui o cenário é favorável. O catálogo oficial do Vosk tem dois modelos pt-BR
([modelos](https://alphacephei.com/vosk/models)):

| Modelo | Tamanho | WER CORAA dev | WER Common Voice test | Licença | Veredito |
|---|---|---|---|---|---|
| **`vosk-model-small-pt-0.3`** | 31 MB | 68,92 % | 32,60 % | **Apache-2.0** | ✅ **escolha do Camaleão** |
| `vosk-model-pt-fb-v0.1.1` | 1,6 GB | 54,34 % | 27,70 % | **GPLv3** | ❌ grande demais para celular, e GPLv3 |

### Por que esses WERs não condenam a escolha

São números de **ditado aberto**. O Camaleão não faz ditado — faz
**command-and-control** sobre um punhado de comandos conhecidos. Em vez de pedir
"transcreva qualquer coisa", pede-se:

```json
[
  "enviar alerta",
  "cancelar",
  "gravar evidência",
  "compartilhar localização",
  "[unk]"
]
```

A API do Vosk aceita instanciar o recognizer com **lista JSON de frases**
(`vosk_recognizer_new_grm`, `SetGrm`), documentada como ganho de velocidade e
precisão, retornando `[unk]` para fala fora da gramática — exatamente o
comportamento desejado.

**[Q4](10-riscos-e-questoes-abertas.md) está quase respondida:** a documentação dos
modelos informa que **a maioria dos modelos pequenos permite reconfiguração dinâmica
de vocabulário**. O modo exige modelo compatível com reconfiguração rápida de
gramática, e `vosk-model-small-pt-0.3` é classificado para mobile/Android/Raspberry
Pi — logo é provável que suporte. Ainda assim: **confirmar na tarefa 1.1**, porque a
decisão inteira repousa nisso.

A API Java do Vosk aceita PCM mono 16 bits em `short[]` diretamente
([Recognizer.java](https://github.com/alphacep/vosk-api/blob/master/java/lib/src/main/java/org/vosk/Recognizer.java)),
o que torna o handoff do `AudioCaptureEngine` trivial.

## Calibração: os parâmetros que importam

O sherpa expõe dois, e os dois são por frase, não globais:

| Parâmetro | Efeito |
|---|---|
| `keywordsScore` | influencia a sobrevivência da keyword durante a busca |
| `keywordsThreshold` | controla o quão difícil é aceitar a detecção |

**Não use um `confiança > 0,80` arbitrário.** O limiar sai do conjunto de validação
do próprio projeto, procurando a região da curva onde o FAR por hora é aceitável sem
elevar demais o FRR. Ver [09-plano-de-testes](09-plano-de-testes.md).

"Socorro" tende a ocorrer acidentalmente em TV e conversa muito mais que uma frase
longa como "a encomenda chegou cedo". Por isso o limiar é **por frase**.

## Escolha da Frase de Segurança

| Regra | Motivo |
|---|---|
| 3 a 6 palavras | curta → falso positivo; longa → falso negativo e difícil sob estresse |
| boa diversidade fonética | reduz colisão acústica |
| baixa frequência em conversa comum | evita disparo acidental |
| não só palavras curtas | palavras curtas colidem mais |
| não pode ser frase que a usuária diga rotineiramente | o óbvio, e o mais esquecido |

Exemplos de referência: **"Preciso buscar meu caderno azul"**, **"A encomenda chegou
cedo"**.

Toda frase escolhida precisa ser calibrada com gravações reais da pessoa **e** com
*hard negatives* — TV, YouTube, conversas e frases foneticamente próximas.

## Datasets

Para treinar ou adaptar um modelo KWS pt-BR, e para calibrar thresholds:

| Dataset | Uso | Licença / observação |
|---|---|---|
| **Common Voice pt 27.0** (set/2026) | treino e validação da base acústica, negativos | **CC0 1.0** — licença limpa. 230,38 h totais, 188,84 h validadas, > 86 mil clips pt-BR ([Mozilla Data Collective](https://mozilladatacollective.com/datasets/cmu5wui2w00e8nq07idithpm6)) |
| **CORAA ASR** | stress-test de fala brasileira espontânea | ~290 h. Condições publicadas envolvem restrições **não comerciais / sem derivadas** — tratar como corpus de **avaliação** até revisão de licença ([artigo](https://link.springer.com/article/10.1007/s10579-022-09621-4)) |
| Gravações próprias, consentidas | threshold e condições reais da usuária | melhor dado para calibração operacional |
| Ruído ambiente próprio | FAR: TV, rua, sala, transporte | sem dependência de licença externa |

**Separe rigorosamente treino, calibração e teste.** A pessoa cujas gravações
ajustaram o threshold precisa ter gravações novas, que nunca entraram no ajuste.
Sem isso, o time mede memorização do conjunto de calibração e chama de
generalização.

## Caminho em duas etapas

```
PROTÓTIPO                              ALVO
─────────────────────────              ──────────────────────────
Vosk small-pt + gramática              sherpa-onnx KWS, modelo pt-BR
como KWS e como ASR                            ↓
Android nativo                         Vosk small-pt, gramática restrita
                                       Android + iOS
```

A arquitetura de [05-arquitetura](05-arquitetura.md) separa o estágio KWS do estágio
ASR **desde o protótipo**, mesmo com os dois rodando em Vosk. É o que permite trocar
só o primeiro estágio quando o modelo pt-BR existir — sem reescrever o resto.

Essa fronteira é a razão de ser do ADR 0001.

## Experimento Android da tarefa 1.1

`GrammarProbeTest` carregou o modelo distribuído, criou o recognizer com lista JSON,
processou uma amostra sintética pt-BR e trocou para a gramática de comandos com
`reset`/`setGrammar`. Resultado real e cobertura em [docs/09](09-plano-de-testes.md#registro-de-execução).
A observação apoia a condição do ADR 0002 no artefato testado. Q4 permanece aberta
para a revisão formal do time; nenhum ADR aceito foi reescrito.

A versão entregue usa Vosk Android `0.3.75`, JNA `5.18.1` e o pacote oficial
`vosk-model-small-pt-0.3.zip`, cuja licença consta como Apache-2.0 no
[catálogo oficial](https://alphacephei.com/vosk/models). O download é verificado por
SHA-256 em `tools/preparar-modelos.py`; o README original é preservado em assets.
Não é incluído modelo sherpa-onnx em inglês apresentado como português.

`VoskPipeline` mantém um modelo e um recognizer, alterando a gramática entre os dois
estágios; desde o [ADR 0011](../.claude/decisions/0011-estagio-1-com-iscas-e-segunda-leitura.md)
há também um recognizer sem gramática para a segunda leitura.

## Ajuste medido do estágio 1 e das palavras

### Corpus e método

`tools/voz/gerar_corpus.py` sintetiza 3.315 amostras (16 kHz) em `dados-brutos/voz/`,
ignorado pelo Git: 13 locutores sintéticos (Microsoft Maria e Daniel via WinRT; Piper
`faber`, `cadu`, `jeff`, `edresson`; cada um com tom normal e alterado), 3 frases de
segurança, a frase sozinha e dentro de falas maiores, 10–12 negativos difíceis por
frase, 80 frases de conversa e TV, comandos e não-comandos.

**Separação:** 6 locutores de **ajuste** escolhem parâmetros; 7 de **teste** nunca
entram no ajuste. As 3 leituras de calibração de cada locutor ficam fora da medição.

`tools/voz/avaliar.py` reproduz o app no PC — mesmo modelo, blocos de 1600 amostras,
resultado por endpoint — em cinco condições: limpo, ruído rosa a 20 e 10 dB, TV
(fala de outros locutores) a 10 dB e longe (reverberação, −14 dB, ruído a 15 dB).

⚠️ São vozes **sintéticas**: duas delas degradadas (Piper `edresson` de baixa
qualidade e tons alterados por reamostragem). Não substituem o protocolo com pessoas
de [docs/09](09-plano-de-testes.md); servem para comparar estratégias.

### Resultados — locutores de teste, frase sozinha

| Estratégia | Limpo | TV 10 dB | Longe | Negativo difícil dispara | Conversa dispara |
|---|---|---|---|---|---|
| Anterior (frase exata) | 100 %¹ | — | — | 10–12 %¹ | 0 |
| Iscas + 2ª leitura 0,70 + junção (**entregue**) | 81 % | 54 % | 43 % | 0,8–2,0 % | **0 em 474 min** |
| Mesma, limiar 0,65 | 81 % | 57 % | — | ~2 % | 0 |

¹ medido com 6 locutores, voz limpa e ruído 10 dB; a frase dentro de fala caía para
77–83 %. Combinações locutor×frase cuja calibração passa (o app não arma sem ela):
82,7 % de detecção média nas cinco condições, 1,5 % de negativo difícil aceito.

A perda concentra-se em `edresson` e nos tons alterados: vozes naturais ficam entre
94 e 100 % em voz limpa. O erro típico é a primeira palavra engolida ("olha buscar meu
caderno azul") — o mesmo padrão observado em gravação real. Ver a decisão de não
afrouxar a primeira palavra no ADR 0011.

**Comandos** (estágio 2, gramática com iscas): 98–100 % de comando certo, **0 %** de
função trocada por outra; fala comum vira `cancelar` em ~3 % — sem efeito, só fecha a
janela.

### Vocabulário acentuado

O vocabulário do `small-pt-0.3` é acentuado: contém "localização" e "não", não contém
"localizacao" nem "nao". O Vosk também não decodifica escapes `ç` na gramática
JSON. Palavra ausente é descartada com um aviso de log e a frase nunca seria
detectada. A gramática usa `PhraseMatcher.grammarForm` (com acentos) e a comparação
usa `normalize` (sem acentos); `Vocabulary.missing` confere cada palavra ao salvar.

### Voz real

Uma gravação de 29 s (WhatsApp, Opus, saturada) com negativos difíceis reais ("tenho
que buscar o meu caderno azul"), comandos e conversa: 3/3 negativos recusados,
comando reconhecido, nenhum disparo. A frase correta falada por pessoa **ainda não foi
medida**. O ajuste local requer três leituras positivas e pelo menos uma negativa;
calcula a separação textual e recusa salvar sem separação. Isto não substitui o
protocolo de FAR/FRR de [docs/09](09-plano-de-testes.md). Frase parecida do roteiro foi
usada como negativo sintético; não foi realizado corpus de centenas de horas.
