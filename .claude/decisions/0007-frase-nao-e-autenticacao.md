---
data: 2026-10-21
status: aceito
---

# 0007 — A Frase de Segurança não é autenticação, e não haverá verificação de locutor no MVP

> Decisão trazida da [análise sherpa+Vosk](../../referencias/analise-sherpa-vosk-2026-10-21.pdf).

## Contexto

É tentador tratar a Frase de Segurança como credencial. Ela não é.

O KWS detecta **"alguém pronunciou esta sequência acústica"**. Não detecta "a
proprietária do aparelho pronunciou esta sequência". Se alguém descobrir a frase —
ouvindo a usuária falar, vendo a configuração, ou por acaso — também poderá acioná-la.

O reflexo seguinte é adicionar verificação de locutor. O sherpa-onnx tem
funcionalidades de identificação e verificação de locutor, então é tecnicamente
possível.

**E é uma má ideia no MVP**, por um motivo específico deste produto: a verificação
biométrica de voz aumenta o falso negativo **exatamente quando a voz está alterada**
— por medo, choro, sussurro, distância, baixa intensidade, ruído. Ou seja: falha mais
justamente na situação para a qual o sistema existe.

Trocar um risco de acionamento indevido por um risco de não acionar numa emergência
real é o pior trade possível aqui.

## Decisão

**A frase é um gatilho, não uma credencial.** O projeto trata isso explicitamente:

1. **Sem verificação de locutor no MVP.** Fica como recurso opcional futuro, e
   **nunca** como única condição para disparar ajuda. Se algum dia entrar, entra como
   sinal adicional que pode aumentar a confiança — jamais como veto que impeça o
   disparo.
2. **Rotação simples da frase.** A Área protegida permite trocar a frase facilmente,
   com recalibração. Se a usuária suspeita que a frase foi descoberta, trocar precisa
   ser trivial, não um reinstalar.
3. **A frase descoberta é risco aceito e documentado**, mitigado pela rotação e pelos
   gatilhos alternativos (ADR 0005), não por biometria.
4. **O texto da configuração não chama a frase de senha.** Ver
   [01-glossario](../../docs/01-glossario.md): "senha" e "palavra-chave" são termos
   proibidos justamente por sugerirem autenticação.

### O que isso implica para o modelo de ameaça

O adversário ouve a usuária falando. Se a frase for dita numa emergência real, ele
pode aprendê-la. Portanto:

- a frase precisa ser trocável **depois** de um episódio;
- o acionamento indevido por terceiro que conhece a frase é um cenário previsto, não
  um furo imprevisto;
- isso reforça por que o falso positivo precisa ser baixíssimo: cada disparo é
  informação vazando.

## Consequências

**Resolve:** evita construir biometria que degradaria o sistema no cenário crítico.

**Resolve:** nomeia corretamente o que a frase é, o que impede expectativa errada na
equipe e na banca. "É um gatilho acústico, não um fator de autenticação" é uma frase
que sustenta pergunta difícil.

**Custa:** o sistema aceita que quem souber a frase pode acioná-la. Risco declarado,
com mitigação declarada.

**Exige:** rotação de frase como requisito de interface, não como item de backlog.

**Exige:** que o pessoal de produto resista à sugestão de "e se reconhecesse a voz
dela?" — que vai aparecer, e tem resposta pronta aqui.
