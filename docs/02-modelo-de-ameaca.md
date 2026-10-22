# 02 — Modelo de ameaça

> Nenhum dos dois documentos de referência tem um modelo de ameaça explícito. Este
> existe porque eles tratam as limitações de plataforma sobretudo como problema de
> *honestidade com a banca*, quando várias delas são problema de **segurança da
> usuária**. A diferença muda decisões de projeto.
>
> C5, C8 e C9 incorporam pontos da
> [análise sherpa+Vosk](../referencias/analise-sherpa-vosk-2026-10-21.pdf).

## O adversário

O adversário do Camaleão **não** é um invasor remoto, nem o Estado, nem um
criminoso técnico. É:

> **Uma pessoa com acesso físico frequente ao telefone da usuária, que
> provavelmente conhece a senha do aparelho, está fisicamente presente, e para
> quem a descoberta do aplicativo é um motivo plausível de escalada de violência.**

Isso é um modelo de ameaça incomum e tem consequências contraintuitivas.

### O que o adversário consegue fazer

| Capacidade | Consequência para o projeto |
|---|---|
| Pegar o telefone destrancado | Qualquer coisa persistida no aparelho é legível por ele. |
| Ver a barra de notificações | O Foreground Service do Android **aparece ali**. |
| Ver o indicador laranja de microfone (iOS 14+) | Delata uso de microfone enquanto a Sentinela está armada. |
| Consultar o Control Center / painel de privacidade | Vê qual app usou o microfone recentemente. |
| Abrir a Fachada e brincar com a calculadora | Pode tropeçar no segredo por acidente. |
| Forçar parada / desinstalar | Mata a Sentinela, e o sistema não a ressuscita. |
| Revogar a permissão de microfone | Mata a Sentinela silenciosamente. |
| **Ouvir a Frase de Segurança sendo falada** | Pode aprendê-la e usá-la, ou reconhecê-la depois. |
| Estar presente quando o alerta disparar | Um falso positivo o informa de tudo. |

### O que o adversário não precisa fazer

Ele não precisa quebrar criptografia nem interceptar rede. **O ataque realista é
olhar a tela do telefone.** Todo esforço criptográfico que não enderece isso é
decorativo.

## Ativos a proteger, em ordem

1. **A integridade física da usuária.** Tudo abaixo é meio para isto.
2. **O fato de que o Camaleão existe no aparelho.** Mais sensível que qualquer dado.
3. **A Frase de Segurança.** Se vazar, o gatilho morre.
4. **O histórico de Ocorrências.** É prova contra o agressor — e, se encontrado,
   prova contra a usuária, do ponto de vista dele.
5. A evidência pós-evento (áudio, localização).

## Consequências de projeto

Estas não são sugestões. São o que o modelo de ameaça obriga.

### C1 — A notificação do Foreground Service é um risco, não um detalhe legal

A pesquisa conclui corretamente que a notificação é obrigatória no Android e que
não se deve prometer microfone clandestino. Mas para o modelo de ameaça acima, uma
notificação persistente dizendo qualquer coisa suspeita **é o pior vazamento do
sistema**, porque fica visível 100% do tempo em que a proteção está ativa.

O texto e o ícone são uma decisão de segurança. O comportamento aprovado para o
protótipo está no [ADR 0009](../.claude/decisions/0009-notificacao-e-cancelamento-protegido.md).
A indicação honesta do microfone pode revelar a captura ao agressor; a Área protegida
informa esse custo. A Fachada não torna o uso do hardware invisível.

### C2 — Falso positivo é incidente de segurança, não bug de qualidade

Um Protocolo SOS disparado por engano, com o agressor presente, pode:

- expor o aplicativo;
- expor a existência de uma Rede de Apoio;
- provocar a chegada de terceiros sem contexto;
- provocar escalada imediata de violência.

Consequência: a calibragem do limiar **não** é um trade-off simétrico. O projeto
prefere errar para o lado de não disparar. Ver
[06-reconhecimento-de-voz](06-reconhecimento-de-voz.md) e as métricas em
[09-plano-de-testes](09-plano-de-testes.md).

### C3 — Não existe cancelamento seguro

O reflexo natural é oferecer "SOS enviado — cancelar em 10s". **Não faça isso sem
definir o cancelamento conforme ADR 0009.** Uma contagem visível na tela:

- informa o agressor, se ele estiver olhando;
- pode ser cancelada *por ele*, se ele pegar o telefone.

E um cancelamento invisível é indistinguível, para a usuária, de um sistema que não
disparou. O comportamento autorizado está no [ADR 0009](../.claude/decisions/0009-notificacao-e-cancelamento-protegido.md).

### C4 — O histórico local precisa ser negável ou ausente

Guardar Ocorrências em SQLite claro no aparelho cria um ativo incriminador para a
usuária, acessível por quem pega o telefone destrancado. Mínimo aceitável:

- cifrar o banco com chave derivada do segredo da Área protegida (não do PIN do
  aparelho, que o agressor conhece);
- **não** exibir histórico de Ocorrências na Área protegida por padrão;
- considerar enviar e **apagar localmente**, mantendo a prova no Painel.

### C5 — SHA-256 local não é integridade nem cadeia de custódia

A proposta original usa SHA-256 sobre a Ocorrência como garantia de integridade e
de cadeia de custódia. Como descrito, não prova nada: a mesma parte produz o dado e
o hash, e quem alterar o dado recalcula o hash. E o Código de Processo Penal define
cadeia de custódia como a documentação da história cronológica do vestígio —
um hash não diz quem coletou, quando, nem quem teve acesso.

O desenho correto é um **manifesto assinado e encadeado**, com recibo do servidor.
Ver ADR [0004](../.claude/decisions/0004-integridade-da-ocorrencia.md).

### C8 — A frase é um gatilho acústico, não uma credencial

O KWS detecta que *alguém* pronunciou a sequência — não que **a usuária** a
pronunciou. Quem descobrir a frase pode acioná-la.

A solução aparentemente óbvia — verificação de locutor — é **rejeitada**, porque
aumenta o falso negativo exatamente quando a voz está alterada por medo, choro,
sussurro ou distância: falha na situação para a qual o sistema existe.

A mitigação é rotação fácil da frase e os gatilhos alternativos de C6, não
biometria. Ver ADR [0007](../.claude/decisions/0007-frase-nao-e-autenticacao.md).

Isto reforça C2 por outro caminho: cada disparo indevido é a frase vazando para
quem estiver ouvindo.

### C9 — O aplicativo nunca afirma proteção que não está acontecendo

Duas mentiras possíveis, as duas graves:

- dizer "ajuda enviada" quando a Ocorrência só foi criada ou enfileirada;
- dizer "protegida" quando o microfone não está ativo — depois de um reboot, por
  exemplo.

Nos dois casos a usuária para de tentar outros meios. Uma falsa sensação de proteção
é pior que nenhuma, porque desloca o comportamento dela. Ver ADR
[0006](../.claude/decisions/0006-maquina-de-estados-do-alerta.md).

### C6 — A voz não pode ser o único gatilho

Microfone revogado, chamada telefônica em curso, ambiente ruidoso, sessão de áudio
interrompida pelo sistema, processo encerrado: tudo isso mata a Sentinela, em geral
sem a usuária perceber. Um sistema de proteção com ponto único de falha no
componente mais frágil não é um sistema de proteção.

Ver ADR [0005](../.claude/decisions/0005-gatilhos-multiplos.md).

### C7 — A Sentinela precisa ser auditável pela própria usuária

Como a Sentinela pode morrer silenciosamente, a usuária precisa de um jeito de
responder "ela está mesmo ligada?" sem adivinhar. Isso exige, na Área protegida:
estado atual, hora do último sinal de vida, e um teste real da frase.
