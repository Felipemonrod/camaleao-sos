# 03 — Requisitos

Numerados para rastreabilidade: cada item do [plano do protótipo](08-plano-prototipo.md)
e cada teste em [09-plano-de-testes](09-plano-de-testes.md) aponta para um ID daqui.

Prioridade: **P0** = sem isso não há protótipo · **P1** = necessário para a demo
completa · **P2** = desejável · **P3** = pós-protótipo.

## Requisitos funcionais

### Fachada e acesso

| ID | Requisito | Pri |
|---|---|---|
| RF-01 | A Fachada é uma calculadora **funcional**: as quatro operações funcionam corretamente. | P0 |
| RF-02 | O nome, o ícone e a tela inicial do aplicativo não revelam a finalidade de proteção. | P0 |
| RF-03 | A Área protegida é alcançada por uma sequência digitada na Fachada, configurável. | P0 |
| RF-04 | A sequência de acesso não pode ser descoberta por tentativa casual: nenhuma pista visual, nenhum feedback diferente ao errar. | P1 |
| RF-05 | Sair da Área protegida devolve a Fachada em estado limpo, sem histórico de navegação. | P1 |

### Frase de Segurança

| ID | Requisito | Pri |
|---|---|---|
| RF-10 | A usuária cadastra a Frase de Segurança em texto, na Área protegida. | P0 |
| RF-11 | O sistema recusa frases com menos de 3 palavras, explicando o motivo. | P0 |
| RF-12 | O sistema recusa frases compostas só de palavras muito frequentes do português. | P2 |
| RF-13 | O cadastro inclui calibração: a usuária fala a frase 3 vezes e o sistema informa a qualidade estimada. | P1 |
| RF-14 | A comparação é tolerante: normalização (minúsculas, sem pontuação, espaços colapsados) + similaridade com limiar, nunca igualdade exata. | P0 |
| RF-15 | A Frase de Segurança é armazenada cifrada, não em texto claro. | P1 |

### Sentinela de Voz Offline

| ID | Requisito | Pri |
|---|---|---|
| RF-20 | A Sentinela é **armada** explicitamente pela usuária, com o aplicativo em primeiro plano. | P0 |
| RF-21 | Armada, a Sentinela detecta a frase com a tela apagada e o aparelho bloqueado. | P0 |
| RF-22 | A detecção ocorre **inteiramente no dispositivo**, sem rede. | P0 |
| RF-23 | Nenhum áudio é persistido antes de um disparo. O [Buffer pré-evento](01-glossario.md) vive só em RAM e é sobrescrito. | P0 |
| RF-24 | A Área protegida mostra o estado da Sentinela: armada/desarmada, hora do último sinal de vida. | P1 |
| RF-25 | A Área protegida oferece "Testar minha frase", que confirma a detecção **sem** disparar o Protocolo SOS. | P1 |
| RF-26 | Se a Sentinela morrer por causa externa (permissão revogada, sessão interrompida, processo morto), o sistema registra e informa a usuária na próxima abertura. | P1 |
| RF-27 | A Área protegida declara, em texto claro, em que condições a Sentinela **para** de funcionar. | P0 |
| RF-28 | Após reboot, o estado persistido é restaurado e uma notificação convida a **rearmar**. O microfone **não** é religado automaticamente, e o estado exibido nunca é "armada". | P0 |
| RF-29 | Existe **um único** dono do microfone (`AudioCaptureEngine`). Os estágios de voz recebem buffers roteados internamente, nunca abrem capturas próprias. | P0 |

### Estágios de voz — ver ADR 0002

| ID | Requisito | Pri |
|---|---|---|
| RF-33 | O pipeline tem três estados: `WAITING_KEYWORD`, `COMMAND_WINDOW`, `SOS_EVENT`. | P0 |
| RF-34 | Em `WAITING_KEYWORD`, só o estágio KWS recebe áudio. | P0 |
| RF-35 | Detectada a frase, abre-se uma `COMMAND_WINDOW` de 4 a 6 s com gramática restrita de comandos. | P1 |
| RF-36 | Timeout ou comando ambíguo **não executa ação**: registra métrica e volta a `WAITING_KEYWORD`. | P0 |
| RF-37 | A frase pode ser **trocada** pela usuária, com recalibração, sem reinstalar o app. Ver ADR 0007. | P1 |
| RF-38 | O comando de cancelamento, se existir, **nunca** é a Frase de Segurança. | P0 |

### Gatilhos alternativos — ver ADR 0005

| ID | Requisito | Pri |
|---|---|---|
| RF-30 | Um código digitado na Fachada dispara o Protocolo SOS, sem transição visual perceptível. | P0 |
| RF-31 | Um padrão de botões físicos dispara o Protocolo SOS **com o aparelho bloqueado e sem usar o microfone**. | P1 |
| RF-32 | Todos os Gatilhos convergem para o mesmo Protocolo SOS, registrando qual gatilho o originou. | P0 |

### Protocolo SOS

| ID | Requisito | Pri |
|---|---|---|
| RF-40 | Ao disparar, obtém a localização mais precisa disponível dentro de um orçamento de tempo, e prossegue mesmo sem ela. | P0 |
| RF-41 | Monta uma Ocorrência com: carimbo de tempo, gatilho de origem, localização (se houver), identificador do dispositivo. | P0 |
| RF-42 | Transmite a Ocorrência à Rede de Apoio pelo canal configurado. | P0 |
| RF-43 | A Ocorrência é um **manifesto assinado** no dispositivo, encadeado ao anterior por `previous_manifest_hash`, e contra-assinado pelo servidor ao ser recebido. **Não** hash local isolado. Ver [C5](02-modelo-de-ameaca.md) e ADR 0004. | P1 |
| RF-44 | O Painel exibe a Ocorrência em tempo real e devolve recibo contra-assinado ao dispositivo. | P1 |
| RF-45 | Sem rede, a Ocorrência é enfileirada **cifrada** e transmitida quando houver conectividade, com retry. | P1 |
| RF-46 | A captura de evidência pós-disparo (áudio) é **opcional** e desligada por padrão. | P1 |
| RF-47 | O Protocolo SOS não produz som, vibração ou mudança visível na Fachada. Exceção: função de ligação, [ADR 0012](../.claude/decisions/0012-ligacao-pelo-painel-e-linha-aberta.md). | P0 |
| RF-48 | A Ocorrência tem quatro estados persistidos: `CREATED`, `QUEUED`, `SENT`, `ACKNOWLEDGED`. Ver ADR 0006. | P0 |
| RF-49 | A interface **nunca afirma mais do que o estado atual**. Linguagem de confirmação ("ajuda enviada") só é permitida em `ACKNOWLEDGED`. | P0 |
| RF-50 | Cada transição de estado é registrada no log de auditoria com carimbo de tempo. | P1 |
| RF-51 | A evidência é gravada em arquivo privado do app, cifrada com AES-GCM e chave aleatória por evento, protegida por chave do Keystore/Keychain. **Nunca** em galeria ou downloads. | P1 |
| RF-52 | Os logs nunca contêm a Frase de Segurança, transcrição completa, GPS ou chaves. | P0 |
| RF-53 | O app **não** afirma que a gravação é prova judicial válida. Afirma apenas preservar integridade e rastreabilidade. | P0 |

### Funções por voz e Rede de Apoio — ver ADR 0010 e ADR 0012

| ID | Requisito | Pri |
|---|---|---|
| RF-54 | Cada função (alerta, localização, gravação) tem palavra escolhida pela usuária e liga/desliga próprio. Só funções ligadas entram na gramática da `COMMAND_WINDOW`. | P1 |
| RF-55 | As palavras não repetem palavras da Frase de Segurança, não se parecem entre si nem com `cancelar`, e toda palavra é conferida no vocabulário do modelo antes de salvar. | P0 |
| RF-56 | A função de alerta envia SMS automático a toda a Rede de Apoio, com link de mapa quando houver posição, somente depois da preparação local e nunca se ela for cancelada. | P1 |
| RF-57 | O registro de SMS distingue pendente, aceito pela operadora e falha. Entrega ao destinatário não é afirmada. | P0 |
| RF-58 | A função de localização envia a posição pontual por SMS e repete em intervalo fixo apenas enquanto a Sentinela estiver armada. | P2 |
| RF-59 | A função de ligação tem palavra própria e vem **desligada** por padrão. O alerta silencioso nunca liga. | P1 |
| RF-60 | Com Painel alcançável, o aparelho pede a ligação num pedido assinado com a chave do dispositivo. O Painel liga em rodadas por toda a Rede de Apoio, fala o aviso em voz sintetizada até alguém atender ou o Painel cancelar, e o aparelho não produz nada visível. | P1 |
| RF-61 | Sem Painel, o aparelho envia SMS avisando que vai ligar em silêncio, com a posição, e liga com o volume de chamada no mínimo e o microfone aberto. Ligação de 40 s conta como atendida; as rodadas seguem enquanto a Sentinela estiver armada. | P2 |
| RF-62 | O registro distingue ligação pedida ao Painel, sem resposta, ligação que durou (pode ser caixa postal) e falha. Atendimento por pessoa não é afirmado. | P0 |
| RF-63 | Os telefones enviados ao Painel ficam só na memória do discador, nunca no banco do Painel, e o Painel mostra apenas os quatro últimos dígitos. | P0 |

## Requisitos não funcionais

| ID | Requisito | Pri |
|---|---|---|
| RNF-01 | **Custo de software e serviços: R$ 0** no protótipo. Nenhuma API paga, nenhum crédito, nenhuma licença de avaliação. | P0 |
| RNF-02 | Latência entre o fim da frase e o alerta no Painel: **< 10 s** em rede local. | P0 |
| RNF-03 | **FAR** — falsos acionamentos por hora em fala contínua que não contém a frase: **≪ 0,5/h**. Prioridade sobre RNF-04. | P0 |
| RNF-04 | **FRR** — ativações reais perdidas, cenário normal: **< 5 %**. Voz baixa investigada separadamente. | P0 |
| RNF-05 | Consumo de bateria com Sentinela armada: **≤ 10 %/h**, medido. Sem número medido, a Sentinela não é um recurso — é uma demo. | P1 |
| RNF-11 | **RSS de RAM** medido para KWS isolado e KWS+ASR, por classe de aparelho. Referência do Vosk para modelos pequenos: ~300 MB. | P1 |
| RNF-12 | CPU médio e de pico do always-on, medidos. | P1 |
| RNF-13 | Sem **thermal throttling** que degrade a detecção em teste prolongado. | P2 |
| RNF-14 | **Recovery rate de 100 %** nos cenários previstos: chamada, Bluetooth, perda de rota de áudio, suspensão. | P1 |
| RNF-15 | Precisão de comando (`COMMAND_WINDOW`) **maior que** a de ditado aberto. WER/CER é métrica secundária. | P1 |
| RNF-06 | A Sentinela sobrevive ≥ 30 min armada com tela bloqueada, sem intervenção. | P0 |
| RNF-07 | O protótipo é **Kotlin nativo, Android apenas**. A interface não conhece `AudioRecord` nem o motor de voz: fala com a Sentinela por uma interface estreita. Ver ADRs 0008 e 0001. | P0 |
| RNF-08 | Todo o tratamento de dados pessoais segue [07-privacidade-lgpd](07-privacidade-lgpd.md). | P0 |
| RNF-09 | O aplicativo não tenta ocultar, suprimir ou contornar indicadores de privacidade do sistema operacional. | P0 |
| RNF-10 | Android é a plataforma primária. iOS é alvo arquitetural, não entrega do protótipo. Ver ADR 0003. | P0 |

## Contra-requisitos

Coisas que o sistema **deve deixar de fazer**, registradas para não voltarem como
"boa ideia" em alguma reunião futura.

| ID | Contra-requisito | Motivo |
|---|---|---|
| CR-01 | Não gravar áudio continuamente em disco. | [07-privacidade](07-privacidade-lgpd.md), RF-23 |
| CR-02 | Não enviar áudio bruto à rede em nenhuma fase da detecção. | RF-22 |
| CR-03 | Não prometer operação com o aplicativo encerrado ou após reinício. | [04-restricoes](04-restricoes-plataforma.md) |
| CR-04 | Não usar `SpeechRecognizer` do Android como detector contínuo. | ADR 0002 |
| CR-05 | Não depender de motor com licença de avaliação comercial. | RNF-01, ADR 0002 |
| CR-06 | Não exibir contagem regressiva de cancelamento antes da decisão de fluxo em [ADR 0009](../.claude/decisions/0009-notificacao-e-cancelamento-protegido.md). | [C3](02-modelo-de-ameaca.md) |
| CR-07 | Não implementar `VoiceInteractionService` para burlar restrições de background. | Transforma o produto em assistente de sistema. |
| CR-08 | Não religar o microfone a partir de `BOOT_COMPLETED`. | Proibido em Android 14+; RF-28 |
| CR-09 | Não usar verificação de locutor como condição para disparar. | ADR 0007 — falha quando a voz está alterada |
| CR-10 | Não usar `vosk-model-pt-fb-v0.1.1` (1,6 GB, GPLv3). | tamanho e licença; ADR 0002 |
| CR-11 | Não usar modelos do openWakeWord. | CC BY-NC-SA 4.0 — non-commercial e share-alike |
| CR-12 | Não abrir duas capturas de microfone. | RF-29 |
| CR-13 | Não usar `SFSpeechRecognizer` da Apple no pipeline. | mantém a inferência independente de serviço online |
| CR-14 | Não fixar dependência com versão dinâmica (`vosk-android:0.3.32+`). | builds não reproduzíveis; fixar a versão validada |
