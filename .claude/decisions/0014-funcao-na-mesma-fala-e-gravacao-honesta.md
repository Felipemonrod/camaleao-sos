---
data: 2026-10-24
status: aceito, condicionado a teste em aparelho
---

# 0014 — Função dita na mesma fala e gravação que não inventa áudio

> Acrescenta ao [ADR 0010](0010-palavra-por-funcao-e-sms-automatico.md) uma terceira
> forma de acionar a função, sem mudar o portão da frase definido no
> [ADR 0011](0011-estagio-1-com-iscas-e-segunda-leitura.md). Corrige o que o
> [ADR 0013](0013-disparo-so-com-frase-e-localizacao-continua.md) deixou em aberto
> entre a ligação em linha aberta e a gravação de evidência.

## Contexto

Três problemas distintos, encontrados na revisão do código em 24/10/2026.

**A pausa entre a frase e a função era obrigatória por construção.** A gramática do
estágio 1 traz a frase, as palavras dela e as iscas — nunca as palavras de função. Ao
aceitar a frase, o `VoskPipeline` fazia `reset()` e trocava a gramática. Quem dissesse
"⟨frase⟩ enviar alerta" numa tirada só perdia a função duas vezes: ela não estava na
gramática que a ouviu, e o `reset()` descartava o áudio restante. É o que aconteceu no
teste de 22/10, com quatro frases aceitas e nada disparado.

**A gravação começava depois do disparo.** O `EvidenceRecorder` só recebia PCM a partir
do bloco seguinte ao `start()`. O arquivo abria com a cena já em andamento: sem a frase,
sem a função, sem o que veio antes. Quem ouvia concluía que a gravação falhou.

**A ligação em linha aberta abria um buraco silencioso na gravação.** Com o microfone
cedido à chamada, o laço de captura não chama `accept()`. O prazo da gravação era testado
dentro do `accept()`, então não vencia: a gravação ficava `GRAVANDO` durante toda a
chamada. E o arquivo juntava os dois lados do corte sem marca nenhuma — dois minutos de
relógio podiam render vinte segundos de áudio colados como se fossem contínuos.

## Decisão

1. **Função na mesma fala, por releitura com gramática combinada.** Aceita a frase, o
   Buffer pré-evento (8 s em RAM, o mesmo da segunda leitura) é relido **uma vez** com
   uma gramática que traz a frase, as palavras de função habilitadas, `cancelar`, as
   iscas e `[unk]`. A função só vale se suas palavras aparecerem **contíguas e depois**
   da frase. Encontrada, dispara na hora; não encontrada, a janela de comando de 6 s
   abre como sempre. A mudança é aditiva: quem fala com pausa não perde nada.

2. **A gravação começa 8 s antes do disparo.** O `start()` recebe o Buffer pré-evento e
   o escreve como primeiro trecho. O registro da evidência guarda `preroll_s`, para que
   ninguém confunda o início do arquivo com o instante do disparo.

3. **A gravação não inventa áudio para tapar o corte.** O prazo passa a ser verificado a
   cada volta do laço, com microfone ou sem. O tempo sem captura é somado e gravado como
   `gap_s` no registro. O arquivo continua sendo só o que o microfone entregou: nenhum
   silêncio é fabricado para fazer a linha do tempo fechar.

4. **O pedido de parar ligações vale desde o começo.** `OpenLineCall.begin()` passa a
   zerar o pedido de parada no início da `Actions.call`, antes da espera por posição.
   Antes, `run()` zerava ao entrar e engolia o "Parar ligações" tocado durante os até
   10 s de espera.

## Consequências

- A releitura com gramática combinada roda **só quando a frase é aceita**, uma vez por
  disparo. No caminho comum — fala que não contém a frase — não há custo novo.
- O risco que a releitura acrescenta não é "alerta sem frase": o portão continua sendo a
  frase, com os dois estágios. É "disparou a função errada", e por isso a exigência é de
  igualdade exata das palavras, contíguas, depois da frase — nunca similaridade. A regra
  do RF-55, que proíbe palavra de função repetindo palavra da frase, é o que torna a
  leitura por posição confiável.
- `preroll_s` entrega 8 s gravados **antes** de a usuária disparar qualquer coisa. É
  áudio que já existia em RAM por força do RF-29, mas que agora persiste em disco. Custo
  de privacidade assumido em troca do contexto do incidente; o arquivo continua cifrado
  e fora da galeria (RF-51).
- Declarar `gap_s` é menos confortável que preencher o corte com silêncio, e é a escolha
  certa: fabricar amostras num arquivo chamado de evidência é pior que admitir o corte.
  O RF-53 continua valendo — não afirmamos prova judicial.
- Durante a ligação em linha aberta a Sentinela **não ouve**: o microfone é da chamada,
  por até 15 min. A frase e o "cancelar" por voz não funcionam nesse intervalo. Isso já
  era verdade antes deste ADR; aqui fica escrito.
- Nada disto foi exercitado em aparelho. O status deste ADR muda quando `docs/09` tiver
  a execução real.
