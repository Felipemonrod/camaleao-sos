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
