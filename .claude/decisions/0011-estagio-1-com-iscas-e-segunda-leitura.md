---
data: 2026-10-22
status: aceito, condicionado à medição com voz real
---

# 0011 — Estágio 1 com iscas na gramática e segunda leitura do Buffer pré-evento

> Refina a parte "protótipo" do [ADR 0002](0002-motor-de-reconhecimento.md), que
> continua valendo: mesmo motor (Vosk `small-pt-0.3`), mesmo modelo, mesma máquina de
> estados. Muda só **como** o estágio 1 decide que a frase foi dita.

## Contexto

O estágio 1 usava gramática com a frase inteira, duas variantes ("vou …", "você …")
e `[unk]`, e exigia o texto exatamente igual à frase. Medido no corpus sintético
([docs/06](../../docs/06-reconhecimento-de-voz.md#ajuste-medido-do-estágio-1-e-das-palavras)),
isso perdia ~20 % das vezes em que a frase vinha dentro de uma fala maior e aceitava
**10–12 %** dos negativos difíceis ("Preciso buscar meu **carro** azul"): com poucas
saídas possíveis, o decodificador força fala parecida para a frase. Pelo
[docs/02](../../docs/02-modelo-de-ameaca.md), falso positivo é incidente de segurança.

Também se descobriu que o vocabulário do modelo é **acentuado** e que a gramática era
montada sem acentos: palavra ausente é descartada pelo Vosk só com um aviso de log.

## Decisão

1. **Gramática com iscas:** a frase, cada palavra dela e ~60 palavras frequentes do
   português, mais `[unk]`. Fala parecida sai como palavras soltas.
2. **Frase contígua:** aceita a frase dentro de uma fala maior, inclusive através de
   dois resultados seguidos (o endpoint pode cortar numa micro-pausa, até 2,5 s).
3. **Segunda leitura:** com acerto da gramática, relê os últimos 8 s do Buffer
   pré-evento (só RAM) com vocabulário aberto e exige similaridade ≥ **0,70** com a
   frase em alguma janela de mesmo tamanho. Só então abre a `COMMAND_WINDOW`.
4. **Gramática com acentos** e conferência de vocabulário pela função nativa
   `vosk_model_find_word` ao salvar frase e palavras (RF-55).
5. A primeira palavra **continua obrigatória**. Afrouxá-la recupera detecções perdidas,
   mas é exatamente o que separa a frase de "Vou buscar meu caderno azul".

## Consequências

**Resolve:** no corpus sintético de 13 vozes, negativos difíceis caíram de ~11 % para
~1 %, sem disparo em ~14 h de conversa; a frase dentro de fala passou a ser aceita.

**Custa:** a segunda leitura bloqueia o laço de captura por ~1–2 s após cada acerto da
gramática; o buffer do `AudioRecord` passou a 4 s para não perder o comando dito em
seguida. Há um segundo `Recognizer` (sem gramática) em memória: PSS medido de
170–208 MB e ~10 % de um núcleo no Galaxy S25 em 30 min armada.

**Não resolve:** perda da frase quando a voz é distante, com TV, ou quando a primeira
palavra é engolida — 43–54 % de detecção nessas condições nas vozes de teste. A meta de
FRR < 5 % (RNF-04) **não** está atingida nessas condições. O critério de aceite
definitivo é a medição com pessoas ([docs/09](../../docs/09-plano-de-testes.md)).

**Condição:** se a medição com voz real mostrar FRR alto em condição normal, revisar
o limiar e a lista de iscas com os dados reais, e reavaliar o modelo KWS próprio
([R1](../../docs/10-riscos-e-questoes-abertas.md)).
