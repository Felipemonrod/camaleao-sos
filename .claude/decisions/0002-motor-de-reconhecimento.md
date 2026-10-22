---
data: 2026-10-21
status: aceito, condicionado a tarefa 1.1
---

# 0002 — sherpa-onnx KWS → Vosk ASR como arquitetura; Vosk em dois estágios no protótipo

> **Esta decisão divergiu da pesquisa 1**, que recomendava whisper.cpp como motor do
> MVP. Ela segue a [análise sherpa+Vosk](../../referencias/analise-sherpa-vosk-2026-10-21.pdf),
> que é mais aprofundada, e acrescenta a resolução de um problema que essa análise
> deixa aberto: o que fazer no protótipo, dado que o modelo KWS pt-BR não existe.

## Contexto

O Camaleão precisa reconhecer **uma** frase em português, continuamente, offline, num
aparelho a bateria, com custo zero de licença (RNF-01) — e depois entender um
**comando** ("enviar alerta", "cancelar").

São dois problemas diferentes, com custos muito diferentes:

- **KWS** responde "a frase foi dita?". Barato, vocabulário fechado, roda sempre.
- **ASR** transcreve. Caro, roda só quando chamado.

A arquitetura correta usa os dois em série: KWS passivo como porteiro, e uma janela
curta de ASR com gramática restrita depois do gatilho. Isso é muito mais barato que
transcrever continuamente, e muito mais capaz que só detectar a frase.

### A pesquisa 1 recomendou whisper.cpp — e isso está errado para este caso

O argumento era ser o melhor denominador comum cross-platform (MIT, Android e iOS,
VAD incluído). A mesma pesquisa reconhece que whisper.cpp **não é KWS** e que
rodá-lo permanentemente é mais trabalho que o necessário — e, em outra seção,
recomenda **Android primeiro**. As duas posições se contradizem: o único benefício
do whisper.cpp aqui é a portabilidade iOS, que o próprio plano não entrega no
protótipo. Pagar custo de energia à vista por um benefício que não chega.

### O bloqueio real

Em outubro de 2026 o sherpa-onnx **não publica modelo KWS pré-treinado em
português**. Os oficiais são chinês+inglês, chinês e inglês. A API aceita keywords
customizadas sem retreinar, mas só para idiomas representados pelo modelo acústico e
pelo tokenizador — "open-vocabulary KWS" não significa "qualquer idioma".

O gargalo é **linguístico, não de API**: `KeywordSpotter`, `createStream()` e os
thresholds já existem em Kotlin e Swift.

Produzir um modelo KWS pt-BR é viável — Common Voice pt 27.0 está sob CC0 1.0, com
188,84 h validadas — mas é trabalho de pesquisa, não de hackathon. Não pode ser
pré-requisito da primeira entrega.

## Decisão

### Arquitetura alvo

```
AudioCaptureEngine único (PCM mono 16 kHz)
        │
        ▼
sherpa-onnx KWS, modelo pt-BR próprio      ← estágio 1, sempre ligado
        │ frase detectada
        ▼
Vosk small-pt-0.3, gramática restrita      ← estágio 2, janela de 4–6 s
        │ comando reconhecido
        ▼
máquina de estados SOS
```

### Protótipo

**Os dois estágios existem desde o início, mas ambos rodam em Vosk
`small-pt-0.3`:** o estágio 1 com gramática de uma única frase + `[unk]`, o estágio 2
com a gramática de comandos. O modelo é carregado uma vez; o que muda entre estados é
a gramática do recognizer.

Isso dá ao protótipo a **mesma máquina de estados** da arquitetura alvo, com o
estágio 1 implementado pelo plano B em vez do ideal. Quando o modelo KWS pt-BR
existir, troca-se o estágio 1 e o resto fica.

### Vetos

| Vetado | Motivo |
|---|---|
| `vosk-model-pt-fb-v0.1.1` (1,6 GB) | tamanho impraticável em celular, e **GPLv3** |
| whisper.cpp como motor de produção | ASR contínuo para tarefa de KWS; fica como **baseline de comparação** nos testes |
| Picovoice Porcupine | Free Trial para desenvolvedores empresariais, sem plano pessoal/não comercial. CR-05 |
| openWakeWord | modelos oficiais em inglês e sob **CC BY-NC-SA 4.0** — non-commercial e share-alike |
| Android `SpeechRecognizer` | documentação diz que pode transmitir áudio a servidores e que não foi feito para uso contínuo. CR-04 |
| Speaker verification no MVP | ver ADR [0007](0007-frase-nao-e-autenticacao.md) |

### Regra de implementação

**Um único dono do microfone.** sherpa e Vosk nunca abrem capturas separadas. Existe
um `AudioCaptureEngine` e o roteamento é interno à máquina de estados.

### Condição de validade

A gramática rápida do Vosk exige modelo compatível com reconfiguração rápida; grafos
HCLG pré-compilados não suportam. A documentação dos modelos afirma que **a maioria
dos modelos pequenos permite reconfiguração dinâmica de vocabulário**, e o
`small-pt-0.3` é classificado para mobile — logo é provável que suporte.

**Confirmar na tarefa 1.1.** Se não suportar, o estágio 1 vira vocabulário aberto +
PhraseMatcher, mais caro em CPU, com a medição de bateria da Fatia 5 decidindo se
segue. Ver [Q4](../../docs/10-riscos-e-questoes-abertas.md).

## Consequências

**Resolve:** a máquina de estados certa é construída desde o protótipo, e a evolução
para sherpa KWS fica local — troca de um estágio, não refatoração.

**Resolve:** o modelo KWS pt-BR sai do caminho crítico da primeira entrega, sem sair
do projeto. Deixa de ser bloqueio e passa a ser melhoria com lugar definido.

**Resolve:** elimina dependência de licença que expira (RNF-01) e de modelo com
licença non-commercial.

**Custa:** o estágio 1 do protótipo é mais caro em CPU e RAM do que um KWS dedicado.
É exatamente o que a medição de bateria (RNF-05) e de RAM (RNF-11) existe para
expor. O Vosk declara ~300 MB de memória de execução como referência para modelos
pequenos — em aparelho de entrada, isso é muito, e precisa ser medido.

**Custa:** abandona a portabilidade iOS imediata. O build iOS do Vosk é "available on
request" e o plugin Flutter oficial não cobre iOS. Aceito por ADR 0003 — mas com a
PoC iOS antecipada, não deixada para o fim.

**Exige:** medir, não acreditar. A escolha só se sustenta se a Fatia 1 mostrar
FRR < 5 % e FAR bem abaixo de 0,5/h nas horas de negativos, e se o baseline
whisper.cpp não revelar perda grande de sensibilidade.

**Risco residual:** se a tarefa 1.1 e o plano B falharem, o caminho restante é
treinar o modelo KWS pt-BR antes de qualquer outra coisa — e aí o cronograma do
hackathon muda. Ver [R4](../../docs/10-riscos-e-questoes-abertas.md).
