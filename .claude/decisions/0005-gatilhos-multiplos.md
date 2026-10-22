---
data: 2026-10-21
status: proposto — depende de Q5
---

# 0005 — A voz nunca é o único Gatilho

> A pesquisa 1 recomenda manter frase, código e gesto. A análise sherpa+Vosk lista
> "falso negativo da keyword" como risco **alto**, com mitigação "gesto/código
> alternativo". Este ADR torna isso requisito explícito, com motivo de segurança, e
> corrige um detalhe: substitui "gesto na tela" por algo que funcione com o aparelho
> bloqueado. Marcado como `proposto` porque depende de Q5.

## Contexto

A Sentinela de Voz Offline é o diferencial do projeto. É também o componente mais
frágil, e falha **silenciosamente**:

- permissão de microfone revogada;
- chamada telefônica, Siri ou troca de rota Bluetooth interrompendo a sessão de áudio;
- processo morto pelo sistema;
- forçar parada;
- telefone reiniciado — e, no Android moderno, o microfone **não** pode ser religado
  de `BOOT_COMPLETED`;
- ambiente ruidoso, voz baixa, telefone no bolso, distância;
- **a usuária sem condição de falar** — que é justamente o cenário de coação.

O último item é o mais sério, e nenhuma melhoria de engenharia o resolve.

Há também o caso contrário. A frase pode ser reconhecida quando não devia: TV,
conversa, frase foneticamente próxima. Um disparo indevido com o agressor presente é
falha de segurança, não inconveniência ([C2](../../docs/02-modelo-de-ameaca.md)).

A pesquisa 1 propõe três gatilhos — frase, código na calculadora e gesto discreto —
mas apresenta isso como robustez genérica. Vale elevar a requisito, e corrigir: um
"gesto discreto" na tela exige o aparelho **destrancado e na mão**, que é
precisamente o que a usuária pode não conseguir.

## Decisão

O Camaleão tem **três Gatilhos independentes**, convergindo para o mesmo Protocolo
SOS, com registro de qual o originou (RF-32):

| Gatilho | Requisito | Funciona bloqueado? | Depende de microfone? |
|---|---|---|---|
| Frase de Segurança | RF-21 | sim | **sim** |
| Código na Fachada | RF-30 | não — exige destravar | não |
| **Padrão de botões físicos** | RF-31 | **sim** | **não** |

O terceiro é a adição. É o único que funciona bloqueado **e** sem microfone, e por
isso é o piso de confiabilidade do sistema: se tudo falhar, ele responde.

A forma exata fica aberta em [Q5](../../docs/10-riscos-e-questoes-abertas.md):
candidatos são padrão de volume, botão de energia repetido, tile de Configurações
Rápidas ou serviço de acessibilidade. Cada um tem restrição de plataforma própria, a
verificar com a mesma disciplina de
[04-restricoes](../../docs/04-restricoes-plataforma.md) — **sem** chutar.

### Corolário: o comando de cancelamento não usa a frase de ativação

Se o cancelamento existir (ver [Q2](../../docs/10-riscos-e-questoes-abertas.md)), ele
tem comando próprio. Reutilizar a Frase de Segurança para cancelar cria uma
ambiguidade perigosa justamente no pior momento.

## Consequências

**Resolve:** elimina o ponto único de falha no componente mais frágil.

**Resolve:** dá à demonstração o passo 10 do roteiro — revogar a permissão do
microfone e mostrar que o sistema degrada em vez de morrer. É o momento mais forte da
apresentação, porque responde à pergunta que a banca ia fazer.

**Resolve, parcialmente, Q2.** Com um gatilho confiável disponível, a Sentinela pode
ser calibrada conservadora — preferindo não disparar — sem deixar a usuária sem
recurso. Isso é o que torna a meta de FAR baixíssimo aceitável.

**Custa:** mais superfície de implementação e mais testes.

**Custa:** mais coisas para a usuária lembrar, sob estresse. Mitigação: o padrão de
botões precisa ser trivial e fisicamente memorável, não uma senha.

**Exige:** que o Protocolo SOS seja escrito uma vez, independente do gatilho. Se a
lógica de disparo se espalhar por três caminhos, os três divergem.

**Risco novo:** um padrão simples o bastante para ser lembrado sob coação pode ser
acionado por acidente no bolso. Falso positivo por botão é tão perigoso quanto por
voz, e precisa entrar na bateria de testes de falso positivo.
