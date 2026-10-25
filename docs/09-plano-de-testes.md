# 09 — Plano de testes

Um sistema de proteção sem números medidos é uma demonstração, não um sistema.

A característica principal de um KWS é a relação entre **não perder uma ativação
real** e **não disparar sozinho**. Tudo aqui gira em torno disso.

> Um app de emergência não se valida mostrando que "reconheceu minha frase três
> vezes no laboratório".

## Métricas

### Detecção

| Métrica | O que mede | Meta do protótipo | Requisito |
|---|---|---|---|
| **FRR** (false reject rate) | ativações reais perdidas | **< 5 %** em cenário normal; voz baixa investigada à parte | RNF-04 |
| **FAR** (false accept rate) | ativações falsas **por hora** | **≪ 0,5/h**; produto exige muito mais rigor | RNF-03 |
| Recall KWS | % das frases-alvo detectadas | maximizar sem explodir o FAR | RNF-04 |
| Precision KWS | % dos gatilhos que eram reais | alta | RNF-03 |
| **Command accuracy** | comando correto após o KWS | **maior** que precisão de ditado aberto | RNF-15 |
| WER / CER | erro textual do ASR | métrica **secundária** no modo comando | RNF-15 |

A referência de `< 5 % FRR` com `< 0,5 FA/h` vem das metas publicadas pelos modelos
do openWakeWord. **Serve como referência de projeto, não como garantia** para o
motor escolhido nem para o Camaleão. Para um app de segurança, o alvo de FAR deve ser
consideravelmente menor em testes longos.

### A assimetria é intencional

FRR e FAR **não têm o mesmo peso**. Um falso negativo significa que a usuária precisa
usar outro Gatilho — e é por isso que
[eles existem](../.claude/decisions/0005-gatilhos-multiplos.md). Um falso positivo com
o agressor presente é falha de segurança dela ([C2](02-modelo-de-ameaca.md)), e
vaza a frase para quem estiver ouvindo ([C8](02-modelo-de-ameaca.md)).

**Ao calibrar: minimize FAR primeiro, e só depois reduza FRR dentro dessa restrição.**
Nunca a média dos dois. E o limiar sai da curva, não de um `> 0,80` escolhido a dedo.

### Latência

| Métrica | O que mede | Requisito |
|---|---|---|
| Trigger latency p50/p95 | fim da frase → evento KWS | RNF-02 |
| Command latency p50/p95 | fim do comando → decisão | RNF-02 |
| Alert latency | comando → alerta em fila ou enviado | RNF-02 |

Medir em **hardware real**, online e offline, e reportar p95 — não só a média.

### Recursos

| Métrica | O que mede | Meta | Requisito |
|---|---|---|---|
| **Battery drain/h** | impacto da escuta prolongada | ≤ 10 %/h | RNF-05 |
| **RSS de RAM** | memória do KWS isolado e do KWS+ASR | medir por classe de aparelho | RNF-11 |
| CPU médio e de pico | custo do always-on | medir | RNF-12 |
| Thermal throttling | degradação após horas | sem degradação de detecção | RNF-13 |
| **Recovery rate** | recuperação após chamada, Bluetooth, suspensão | **100 %** dos cenários previstos | RNF-14 |
| Reboot state | estado exibido após restart | **nunca** "protegida" sem microfone ativo | RF-28 |

Sem número de bateria e de RAM, a afirmação "modo vigilância armado" não se sustenta:
ninguém arma um modo que esvazia o telefone ou mata os outros apps. A referência do
Vosk de ~300 MB para modelos pequenos é o motivo pelo qual RNF-11 existe.

## Os quatro grupos de teste

### 1. Positivos controlados

A própria pessoa pronuncia a frase **50 a 100 vezes**, variando velocidade, emoção e
intensidade.

### 2. Positivos difíceis

| Condição | Por que importa |
|---|---|
| sussurro / voz baixa | o cenário de coação real |
| telefone no bolso | atenuação severa de agudos |
| telefone na bolsa | atenuação + abafamento |
| 2 a 3 metros de distância | uso realista |
| televisão ligada | ruído com fala — o pior caso |
| trânsito | ruído de banda larga |
| ventilador | ruído estacionário |
| música | ruído sem fala |

Distâncias a cobrir: 0,5 m, 1 m, 2 m, mais bolso e bolsa.

### 3. Hard negatives

Palavras foneticamente próximas e frases contendo **apenas partes** da frase.
Derive variando uma dimensão por vez:

```
cadastrada:  "preciso buscar meu caderno azul"

troca de verbo:     "vou buscar meu caderno azul"
troca de pessoa:    "você precisa buscar meu caderno azul"
subconjunto:        "meu caderno azul"
troca de objeto:    "preciso buscar meu caderno verde"
ordem alterada:     "meu caderno azul, preciso buscar"
fonética próxima:   "preciso buscar meu caderno a mais"
contexto natural:   "preciso buscar meu caderno na escola amanhã"
```

**"Vou buscar meu caderno azul" tem que falhar.** É o passo 6 do roteiro de
demonstração — se falhar nos testes, a demo não acontece.

### 4. Background contínuo

> O teste mais importante de falso positivo **não é um arquivo de 20 minutos.** É
> processar **dezenas ou centenas de horas** de negativos e contar falsas ativações
> por hora.

Conversas, TV, podcasts, vídeos e ruído, sem a frase presente. O FAR só tem
significado estatístico com volume.

## Datasets e separação

| Dataset | Uso | Licença / observação |
|---|---|---|
| **Common Voice pt 27.0** | treino e validação da base acústica, negativos | **CC0 1.0** — preferência principal. 230,38 h totais, 188,84 h validadas, > 86 mil clips pt-BR |
| **CORAA ASR** | stress-test de fala brasileira espontânea | ~290 h. Restrições **não comerciais / sem derivadas** — revisar licença antes de derivar modelo; tratar como corpus de **avaliação** |
| Gravações próprias, consentidas | threshold e condições reais da usuária | melhor dado para calibração operacional |
| Ruído ambiente próprio | FAR: TV, rua, sala, transporte | sem dependência de licença externa |

**Separe rigorosamente treino, calibração e teste.** A pessoa cujas gravações
ajustaram o threshold precisa ter gravações **novas**, que nunca participaram do
ajuste. Sem isso, o time mede memorização do conjunto de calibração e chama de
generalização.

## Matriz de dispositivos

Android, no mínimo três:

| Classe | Por que |
|---|---|
| entrada, mais antigo | pior caso de CPU e RAM — onde o ADR 0002 pode reprovar |
| intermediário atual | o caso realista |
| agressivo em gerenciamento de bateria | onde o FGS morre primeiro |

## Baseline comparativo

Rode a mesma matriz com whisper.cpp (`tiny`, PT) sobre **as mesmas gravações**, para
responder:

- o estágio KWS com gramática está perdendo detecção que um ASR completo pegaria?
- quanto a gramática restrita custa em sensibilidade e quanto economiza em CPU?

Isso transforma a escolha do ADR 0002 de preferência em resultado medido.

## Testes de ciclo de vida

Checklist manual, Android, Sentinela armada:

| # | Ação | Esperado |
|---|---|---|
| T-01 | Bloquear a tela, esperar 30 min, falar a frase | detecta |
| T-02 | Abrir outros 5 apps, voltar, falar a frase | detecta |
| T-03 | Remover dos Recentes, falar a frase | detecta (`stopWithTask="false"`) |
| T-04 | Forçar parada, falar a frase | **não detecta** — avisa ao reabrir (RF-26) |
| T-05 | Revogar a permissão do microfone | Sentinela cai, usuária é avisada (RF-26) |
| T-06 | Chamada telefônica, encerrar, falar a frase | detecta, ou avisa que caiu (RNF-14) |
| T-07 | Reiniciar o telefone | **não detecta**; notificação de rearme; estado **nunca** "armada" (RF-28) |
| T-08 | Modo avião, disparar, religar a rede | `CREATED → QUEUED → SENT → ACKNOWLEDGED` (RF-48) |
| T-09 | Modo economia de bateria | documentar o comportamento, seja qual for |
| T-10 | Disparar e observar o aparelho | nenhum som, vibração ou mudança visível (RF-47) |
| T-11 | Conectar e desconectar Bluetooth durante a escuta | recupera (RNF-14) |
| T-12 | Siri / assistente do sistema interrompendo | recupera (RNF-14) |
| T-13 | Trocar a Frase de Segurança e recalibrar | funciona sem reinstalar (RF-37) |
| T-14 | Dizer a frase e deixar a janela de comando expirar | nenhuma ação, métrica registrada (RF-36) |

T-04 e T-07 **devem falhar na detecção**. Falhar ali é o sistema se comportando
conforme [04-restricoes](04-restricoes-plataforma.md) — registre como **aprovado**.

A documentação do Android permite testar restrições de `BOOT_COMPLETED` com comandos
`adb`, o que facilita automatizar T-07.

## Formato de registro

Planilha versionada em `referencias/`:

```
data | ambiente | distancia | tentativa | frase_dita | detectou | score | latencia_ms | obs
```

Dados brutos, não resumo. O resumo se recalcula; o dado bruto perdido não volta — e é
dele que sai a calibração do limiar e o gráfico da apresentação.

## Registro de execução

Ambiente: Windows, JDK 21.0.12, Gradle 8.13, AGP 8.13.0, Kotlin 2.2.20,
compile/target SDK 36. Emulador AOSP Android 15/API 35 x86_64, perfil Pixel 6,
aceleração WHPX. Data de entrega: 21/10/2026, America/Sao_Paulo.

| Verificação executada | Resultado observado | Evidência local |
|---|---|---|
| Compilação Android debug | aprovada | `artifacts/final-build.txt` |
| Testes unitários de domínio | 8 aprovados, zero falhas | relatório Gradle `testDebugUnitTest` |
| Testes HTTPS/criptografia do servidor | 8 aprovados, zero falhas | `artifacts/server-tests.txt` |
| Testes instrumentados do Android | 11 aprovados, zero falhas, sem ignorados | `artifacts/device-tests-final.txt` |
| Android Lint | 0 erros, 15 avisos | `artifacts/lint.html` |
| Assinatura e alinhamento do APK | assinatura debug v2 e ZIP 16 KiB aprovados | `artifacts/apk-verification.txt` |
| Invariantes documentais | 10 verificações, zero avisos | `artifacts/sot.txt` |
| Reinício real do emulador | lembrete de rearme; nenhum serviço de microfone | `artifacts/boot-notifications.txt`, `artifacts/boot-services.txt` |

Os avisos do Lint incluem versões mais recentes de dependências, literais pt-BR,
sugestões de extensões Kotlin e o `X509TrustManager` do pareamento local. O último
é deliberado e foi exercitado com certificado correto e pin incorreto; não é um
`trust-all`. Usa fingerprint exato, validade do certificado e verificação padrão de
hostname. Não há baseline nem configuração para ignorar erros.

### O que os testes provaram

- Quatro operações e precedência na Fachada; entrada da Área protegida por código;
  `FLAG_SECURE` na Área protegida e retorno à Fachada ao sair do aplicativo.
- Criptografia AES-GCM, detecção de adulteração e assinatura ECDSA verificável.
- Migração das preferências do primeiro protótipo para arquivo cifrado antes da
  limpeza; frase preservada e novos consentimentos de comunicação não presumidos.
- Cancelar a preparação não consome sequência do manifesto e não permite cancelar
  uma Ocorrência depois de ela entrar na fila.
- A gramática Vosk foi criada e reconfigurada no modelo incluído. A amostra
  **sintética** positiva abriu a janela de comando, sem enviar sozinha; o comando
  sintético confirmou o evento; o negativo sintético do roteiro não abriu janela.
- O serviço abriu `AudioRecord` e permaneceu ativo ao mover a Activity para
  background por alguns segundos. Não foi um ensaio de 30 minutos bloqueado.
- Android pareou por HTTPS, manteve `QUEUED` em falha de transporte, verificou o
  recibo real do Node e chegou a `ACKNOWLEDGED`. Um segundo manifesto preservou a cadeia.
- A preparação automática transmitiu um teste após sua fase local; uma preparação
  cancelada não entrou na fila.
- No emulador, Wi-Fi e dados foram desligados: a fila permaneceu `QUEUED`. Ao religar,
  o retry automático chegou a `ACKNOWLEDGED`, sem tocar novamente no aplicativo.
- Pin incorreto impediu o pareamento. O servidor rejeitou autenticação incorreta,
  assinatura inválida e buraco na cadeia; repetição retornou o mesmo recibo.
- Receiver testado isoladamente e reinício completo testado depois: não houve
  microfone no boot. O lembrete convida a rearmar manualmente.

### Limites da evidência

Os testes instrumentados usam fala sintetizada por voz Windows pt-BR e executam o
motor nativo real. Não são medição de acurácia com pessoas, sotaques, sussurro,
televisão, telefone no bolso ou distâncias. Não foram cumpridos os conjuntos longos
de FAR/FRR, a matriz de três aparelhos, bateria, termal, RSS por classe de aparelho
nem o baseline whisper.cpp.

T-07 foi exercitado em emulador. T-08 foi exercitado com desligamento dos dois
transportes do emulador e retorno de conectividade, sem afirmar comportamento de
rádio de todos os aparelhos. Os demais cenários manuais continuam pendentes, salvo
as partes explicitamente observadas acima. A navegação do Painel em navegador foi
interrompida por certificado local não confiado; não foi contornada a advertência.
Os testes HTTPS do servidor e do Android passaram. A revisão visual do Painel com
certificado confiado pelo operador continua manual.

A obtenção de GPS físico, SMS entregue, botão físico bloqueado, interrupção por
chamada/Bluetooth, biometria e avaliação jurídica não foram declarados aprovados.
As capturas Android em `artifacts/screenshots` são reais; o teste remove `FLAG_SECURE`
apenas na sua própria sessão para fotografar telas sem dados pessoais. O APK não
contém opção para desabilitar essa proteção.

### Execução de 22/10/2026 — funções por voz, SMS e aparelho real

Ambiente: Galaxy S25 (SM-S931B), Android 16/API 36, arm64, chip VIVO no slot 2, via
USB e Wi-Fi; notebook Windows com Painel local; Vosk `small-pt-0.3`. Mudanças em
[ADR 0010](../.claude/decisions/0010-palavra-por-funcao-e-sms-automatico.md) e
[ADR 0011](../.claude/decisions/0011-estagio-1-com-iscas-e-segunda-leitura.md).

| Verificação executada | Resultado observado |
|---|---|
| Testes unitários | 14 aprovados (8 anteriores + 6 de funções e SMS) |
| Testes do Painel (Node) | 8 aprovados |
| Instrumentados no S25 (`CompleteFlowTest`) | 13 aprovados e 2 ignorados por regra: alternar rede (só emulador) e SMS sem número autorizado |
| Instrumentados no emulador API 36 | 15 aprovados, incluindo SMS com link de mapa e alerta cancelado sem SMS |
| Voz no motor real (amostras sintéticas) | frase dentro de fala aceita; "Preciso buscar meu carro azul" recusada; cada palavra aciona sua função; função desligada não é ouvida |
| Vocabulário | "localizacao" detectada como ausente do modelo; "localização" presente |
| Gravação | blocos AES-GCM; adulterar 1 byte impede exportar; PCM não aparece em claro |
| Tela apagada, Sentinela armada (S25) | serviço `microphone\|location`; posição obtida (±53 m, leitura nova ou última com < 2 min); gravação de 3 s pelo microfone real exportada e conferida |
| 30 min armada, tela apagada, no carregador (S25) | 60/60 amostras ativa; sinal de vida ≤ 4 s; PSS 170–208 MB; CPU média 10,5 % de um núcleo (pico 11,8 %) |
| Painel do notebook pelo Wi-Fi (S25 → notebook) | pareamento com pin, dois manifestos `ACKNOWLEDGED` com recibo verificado |
| SMS real pelo S25 | recusado pela operadora (`480` no IMS, `SYSTEM_ERR` no rádio) para dois números; o app registrou `FALHOU` |
| SMS real em outro aparelho, pelo kit `tools/kit-sms` | **relatado pelo responsável** como enviado; relatório do kit não visto nesta sessão |
| Medição de voz no corpus sintético | ver [docs/06](06-reconhecimento-de-voz.md#ajuste-medido-do-estágio-1-e-das-palavras) |

**Limites desta execução:** o teste de 30 min ocorreu em silêncio e no carregador:
não mede falso positivo em conversa nem bateria (RNF-05), e sem Doze. A frase falada
por pessoa real ainda não foi medida. O SMS entregue não foi observado diretamente.

### Execução de 22/10/2026 — ligação para a Rede de Apoio (só no PC)

Mudança em [ADR 0012](../.claude/decisions/0012-ligacao-pelo-painel-e-linha-aberta.md).
Nenhuma ligação real foi feita.

| Verificação executada | Resultado observado |
|---|---|
| Compilação Android debug | aprovada |
| Testes unitários | 15 aprovados (14 anteriores + `FunctionsTest.callMessagesNeverInventPosition`) |
| Testes do Painel (Node) | 21 aprovados (8 anteriores + 13 de `server/test/call.test.mjs`): pedido sem token, alterado ou antigo recusado; sem discador responde 503; repetição devolve a mesma ligação; discador simulado liga em rodadas até atender; cancelamento pelo Painel; telefone fora de `state.bin` e da listagem; número com caractere de shell recusado |
| Formato de `dumpsys telephony.registry` no S25 | conferido: `Foreground call state: N` por chip, lido pelo discador |

**Falta no aparelho, antes de marcar RF-59 a RF-63:**

- ⚠️ **Não verificado:** `AudioManager.getMode()` devolvendo `MODE_IN_CALL` para app comum no S25 (Android 16). Sem isso, o caminho A para depois da primeira ligação.
- `TelecomManager.placeCall` com a Fachada em segundo plano e a Sentinela armada; volume de chamada no mínimo; retomada do microfone depois da chamada (RNF-14).
- Discador real: Android de discagem no notebook, `DISCADOR=adb DISCADOR_SERIAL=<serial> npm start`, ligação atendida, não atendida e recusada; estado `1` (ACTIVE) só no atendimento.
- Voz SAPI pt-BR no notebook audível pelo microfone do aparelho de discagem.
- Xiaomi com `CALL_PHONE` e a permissão própria do MIUI/HyperOS.

### Execução de 22/10/2026 — bateria completa no S25 após a 0.4.0

Commit `10f1c6f` (correções da calibração da frase, da sessão armada e do SMS sem
permissão, mais a função de ligação). `tools/validar.ps1 -Serial <S25>`, Painel local no PC
via `adb reverse`. Ao fim, `pm clear` no app.

| Verificação executada | Resultado observado |
|---|---|
| CI do commit no GitHub (Android e Painel) | aprovado |
| Compilação, Lint e testes unitários | aprovados; 16 testes unitários (15 anteriores + `onlyRecognizedReadingsCountAsPositive`) |
| Testes do Painel (Node) | 21 aprovados |
| Instrumentados no S25 (`CompleteFlowTest` + `GrammarProbeTest`) | `OK (15 tests)`: 13 aprovados e 2 ignorados por regra (alternar rede só no emulador; SMS sem número autorizado) |
| `ScreenOffTest`, posição com tela apagada e Sentinela armada | **instável: 2 aprovações e 3 falhas em 5 execuções** (2 dentro da bateria, 3 isoladas, 1 falha isolada). A falha é `Sem posição com tela apagada e Sentinela armada` no limite de 15 s. Na execução anterior desta data, passou |
| `ScreenOffTest`, execução longa | ignorado por regra (só roda quando pedido) |
| `ScreenOffTest` depois da correção do `LocationProbe` | 5 aprovados em 5 |

**Causa e correção da falha de posição:** com permissão fina, o `LocationProbe` pedia
posição só ao GPS. Dentro de casa, com a tela apagada, o GPS não fixava em 15 s e o
teste dependia de haver leitura de menos de 2 min por acaso. Na usuária, isso deixaria
o alerta sem localização dentro de casa. Agora `fused` (API 31+), rede e GPS são
pedidos juntos; a resposta com precisão de até 50 m encerra na hora, senão vale a melhor
até o prazo. Depois da correção, no mesmo S25 e no mesmo cômodo: **5 aprovações em 5
execuções** do `ScreenOffTest`, ~9 s cada. As correções da
calibração e da sessão armada passaram só nos testes automáticos; o fluxo pela tela
(salvar frase nova → 3 positivas → 1 negativa → calibrar → armar logo depois do teste)
ainda não foi feito à mão.

### Execução de 22/10/2026 — calibração, disparo, agenda e ligação no S25 (debug)

Mudanças: [ADR 0013](../.claude/decisions/0013-disparo-so-com-frase-e-localizacao-continua.md),
calibração por "segurar para falar" com porcentagem e limite pessoal a partir da
negativa, `LocationProbe` com vários provedores. Tudo feito pelo responsável no S25,
com o log `CamaleaoVoz`/`CamaleaoLigacao` acompanhado; `pm clear` ao fim.

| Verificação executada | Resultado observado |
|---|---|
| Bateria `tools/validar.ps1` no S25 depois das mudanças | aprovada: `OK (15 tests)` (13 + 2 ignorados por regra) e `ScreenOffTest` `OK (2 tests)`; 21 do Painel; unitários e Lint aprovados |
| Calibração "segurar para falar" | relatada pelo responsável: grava, mostra texto, % e se a Sentinela reconheceria |
| Negativa parecida com a frase | antes travava a calibração; agora sobe o limite pessoal. Relatado como funcionando |
| Armar sem autorização de comunicação | o app agora diz o que falta e abre a tela; depois armou |
| Frase + palavra com voz real | 4 frases aceitas (nota 1,00), mas sem palavra de função na janela, por isso nada disparou. Depois: frase aceita (0,95) seguida de `COMANDO ligacao` |
| Contato pela agenda | primeiro número recusado (formato da agenda); depois da regra do nono dígito e do preenchimento para correção, aceito e conferido pelo responsável |
| Ligação em linha aberta (A), número autorizado pelo responsável | 1 chamada de 49 s, encerrada pela regra dos 40 s (provável caixa postal). **`AudioManager.getMode()` vê a chamada no S25**: o ⚠️ da execução anterior está resolvido |
| Ligação que não parava (relato do responsável, versão anterior) | causa no código: contatos lidos uma vez e parada só pelo estado armado. Corrigido (RF-68) |
| "Parar ligações" e excluir contato durante as tentativas | **não comprovado**: o responsável relatou que parou, mas o log do aparelho não mostra nova frase nem ligação depois das 18:23 |

**Falta:** a prova de parada com chamada recusada; disparo só com a frase em voz real;
repetição do SMS (o chip do S25 recusa SMS); bateria com posição contínua (1 h fora do
carregador); tudo no APK de release.


### Execução de 24/10/2026 — função na mesma fala e gravação (só no PC)

Mudanças do [ADR 0014](../.claude/decisions/0014-funcao-na-mesma-fala-e-gravacao-honesta.md):
RF-69 (função dita junto com a frase), RF-70 (gravação começa pelo Buffer pré-evento) e
RF-71 (prazo e corte declarados quando o microfone é da chamada). **Nenhum aparelho foi
usado nesta execução.**

| Verificação executada | Resultado observado |
|---|---|
| Compilação Android debug | aprovada |
| Lint | aprovado, sem erro novo |
| Testes unitários | **25 aprovados, 0 falhas** (18 anteriores + 7 de `Stage1.commandAfterPhrase`) |
| Testes do Painel (Node) | 21 aprovados, 0 falhas |
| Função depois da frase, na mesma fala | aceita, com ou sem fala comum no meio |
| Função dita **antes** da frase | recusada |
| Palavras da função separadas ("enviar o alerta") | recusada: a exigência é contígua |
| Duas funções na mesma fala | vale a primeira depois da frase |
| `cancelar` na mesma fala | devolvido como `cancelar`, não como função |
| Função desligada dita na mesma fala | ignorada |

**O que esta execução não prova.** Tudo acima é leitura de listas de palavras em teste
unitário: nenhum áudio passou pelo Vosk, nenhuma frase foi falada, nenhum arquivo de
evidência foi gravado. Em particular, continuam **não verificados**:

- que a releitura com gramática combinada encontre a função em voz real;
- o custo em tempo da terceira leitura do Buffer, que roda na thread de captura — o
  `AudioRecord` tem folga de 4 s e a segunda leitura já consome 1–2 s;
- o pré-rolo de 8 s no arquivo exportado (`preroll_s`) e a conferência do hash depois dele;
- o `gap_s` durante uma ligação real.

### Revisão da ligação e da parada de ligações — 24/10/2026

Pedida pelo responsável depois do relato de um colega de que ligação e parada
funcionaram. O relato **não foi conferido aqui**; o que segue é leitura de código.

| Ponto | Resultado da revisão |
|---|---|
| Parar ao desarmar | correto: `going()` exige `LiveState.active` e `onDestroy` chama `OpenLineCall.stop()` |
| Parar ao excluir o último contato | correto: os contatos são relidos antes de cada chamada |
| "Parar ligações" tocado durante a espera por posição | **era perdido**: `run()` zerava o pedido ao entrar, depois dos até 10 s de `LocationProbe`. Corrigido com `OpenLineCall.begin()` na `Actions.call` |
| Chamada recusada pelo destinatário | o modo de chamada cai antes dos 40 s, o registro vira `SEM RESPOSTA (rodada n)` e a rodada seguinte acontece. Comportamento correto no código; **continua sem prova em aparelho** |
| Teto de 30 min do RF-68 | ⚠️ `going()` só é testado entre chamadas e cada chamada pode durar 15 min: o total pode passar de 30 min. Não corrigido — mudar a temporização do laço sem aparelho é risco maior que o desvio |
| Sentinela durante a ligação | ⚠️ o microfone é da chamada por até 15 min: a frase e o `cancelar` por voz **não funcionam** nesse intervalo. Já era assim; agora está escrito no ADR 0014 |

### Execução de 25/10/2026 — RF-69 no S25, com motor real e voz sintética

`tools/validar.ps1 -Serial <S25>` no Galaxy S25 (SM-S931B), APK **debug** do commit
`3302845`, Painel local pelo cabo (`adb reverse`, `https://127.0.0.1:8443`). A release
0.5.0 foi desinstalada pelo responsável: assinaturas diferentes impedem instalar o debug
por cima (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).

| Verificação executada | Resultado observado |
|---|---|
| Compilação debug, Lint e testes unitários | aprovados; **25 testes unitários** |
| Testes do Painel (Node) | **21 aprovados, 0 falhas** |
| Instrumentados no S25 (`CompleteFlowTest` + `GrammarProbeTest`) | **`OK (17 tests)`** em 44 s: 15 executados e 2 ignorados por regra (alternar rede só em emulador; SMS sem número autorizado) |
| `ScreenOffTest` | **`OK (2 tests)`** em 9,5 s |
| **RF-69 — frase e função numa tirada só** (`mesmafala.wav`) | **aprovado no motor real**: a função disparou `ALERT` e a `COMMAND_WINDOW` **não chegou a abrir** |
| **RF-69 — função dita antes da frase** (`antes.wav`) | **aprovado**: nada disparou e a janela abriu normalmente |

As amostras `mesmafala.wav` ("Preciso buscar meu caderno azul enviar alerta") e
`antes.wav` ("Enviar alerta preciso buscar meu caderno azul") foram acrescentadas a
`tools/gerar-amostras.ps1`. Os `.wav` são ignorados pelo Git e precisam ser gerados em
cada máquina — neste notebook não existiam, e a primeira bateria falhou com
`FileNotFoundException: frase.wav` em 3 testes antes de serem gerados.

**O que esta execução prova e o que não prova.** Prova que a releitura com gramática
combinada encontra a função falada junto com a frase, no Vosk real, no aparelho real, e
que a regra de posição recusa a palavra dita antes. Os 44 s da suíte incluem a terceira
leitura sem nenhum `Captura interrompida`, mas isso **não** é a medição do custo em
tempo pedida: o teste alimenta PCM em laço, não lê de `AudioRecord` com prazo real.

Continua **não verificado**:

- a função na mesma fala em **voz de pessoa**, e não sintetizada;
- o custo da terceira leitura na thread de captura com a Sentinela armada de verdade;
- o pré-rolo (`preroll_s`) num arquivo de evidência exportado e conferido por hash;
- o `gap_s` durante uma ligação real;
- tudo isso em APK de **release**: a chave de assinatura não está neste notebook, então
  a 0.5.0 publicada na página **não contém** nada deste ADR.
