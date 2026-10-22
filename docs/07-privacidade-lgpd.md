# 07 — Privacidade e LGPD

> ⚠️ Este documento é engenharia de privacidade, **não** parecer jurídico. As
> questões que exigem advogado estão marcadas e listadas em
> [10-riscos](10-riscos-e-questoes-abertas.md).

## Dados pessoais tratados

A LGPD define dado pessoal de forma ampla, e a ANPD inclui explicitamente
**geolocalização de celular** como exemplo
([ANPD — Titular de Dados](https://www.gov.br/anpd/pt-br/assuntos/titular-de-dados),
[Lei 13.709/2018](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709.htm)).

| Dado | Quando | Onde fica | Sensibilidade |
|---|---|---|---|
| Frase de Segurança | no cadastro | aparelho, cifrada | alta — se vazar, o gatilho morre |
| Segredo da Área protegida | no cadastro | aparelho, derivado, nunca em claro | alta |
| Áudio bruto | continuamente, armada | **só RAM**, sobrescrito, nunca em disco | altíssima |
| Áudio pós-disparo | só após disparo, se habilitado | aparelho + transmissão | altíssima, e **inclui terceiros** |
| Geolocalização | no disparo; com a Sentinela armada, atualizada a cada 30 s só na memória ([ADR 0013](../.claude/decisions/0013-disparo-so-com-frase-e-localizacao-continua.md)) | memória enquanto armada; Ocorrência e SMS no alerta | alta |
| Carimbo de tempo, gatilho | só no disparo | Ocorrência | média |
| Contatos da Rede de Apoio | no cadastro, digitados ou escolhidos um a um no seletor da agenda (sem `READ_CONTACTS`) | aparelho + Painel | alta — são **terceiros** |
| Telefones da Rede de Apoio | no pedido de ligação ao Painel ([ADR 0012](../.claude/decisions/0012-ligacao-pelo-painel-e-linha-aberta.md)) | memória do discador do Painel, nunca em `state.bin`; a tela mostra só os 4 últimos dígitos | alta — são **terceiros** |

Dois pontos que costumam passar batido:

- **A Rede de Apoio é formada por titulares de dados que não instalaram o app.**
  Nome e canal de contato deles são dados pessoais de terceiros.
- **O áudio pós-disparo grava o agressor.** Também é titular. Ver [Q3](10-riscos-e-questoes-abertas.md).

## O desenho que sustenta o discurso

```
ANTES DO DISPARO
──────────────────────────────────
microfone → VAD → motor local
                      ↓
                  descarta

✗ NÃO sobe áudio para servidor
✗ NÃO salva fala em disco
✗ NÃO precisa de internet


DEPOIS DO DISPARO
──────────────────────────────────
evento confirmado
      ↓
dados mínimos necessários
      ↓
proteção criptográfica
      ↓
rede previamente autorizada
      ↓
confirmação de recebimento
```

Isso é **minimização de verdade**, não slogan: o dado mais sensível do sistema
(áudio contínuo) nunca é persistido nem transmitido. Combina com os princípios de
segurança e minimização da LGPD e com a recomendação da ANPD de adotar medidas
administrativas e técnicas de proteção
([guia de segurança da ANPD](https://www.gov.br/anpd/pt-br/assuntos/noticias/anpd-publica-guia-de-seguranca-para-agentes-de-tratamento-de-pequeno-porte)).

É um discurso muito mais forte do que "gravamos tudo 24 h por dia" — e é a
diferença entre privacy by design e privacy by marketing.

## Princípios aplicados

| Princípio | Como aparece no Camaleão |
|---|---|
| **Finalidade** | Cada dado tem um uso declarado e só esse. Áudio serve para detectar a frase; não alimenta analytics, estatística nem melhoria de modelo. |
| **Necessidade / minimização** | O Buffer pré-evento existe em RAM e é sobrescrito (RF-23). A Ocorrência leva o mínimo (RF-41). |
| **Transparência** | A Área protegida diz exatamente quando a Sentinela para de funcionar (RF-27) e o que é enviado no disparo. |
| **Segurança** | Frase e banco cifrados (RF-15, [C4](02-modelo-de-ameaca.md)); chaves em Keystore/Keychain. |
| **Prevenção** | A opção de evidência pós-disparo vem **desligada** (RF-46). Consentimento é ato positivo, não padrão de fábrica. |
| **Não discriminação** | Sem perfilamento. O app não infere nada sobre a usuária. |

## Tensão real: transparência de plataforma vs. segurança da usuária

A regra 2.5.14 da Apple exige indicação clara de captura de microfone
([App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)),
e o Android obriga notificação persistente no Foreground Service.

Ao mesmo tempo, [C1 do modelo de ameaça](02-modelo-de-ameaca.md) mostra que essa
indicação é justamente o que pode denunciar a usuária ao agressor.

**Não existe solução que satisfaça os dois lados.** O que o SOT determina:

1. Jamais ocultar, suprimir ou falsificar indicador do sistema (RNF-09).
2. O texto da notificação pode ser discreto e coerente com a Fachada, mas não pode
   mentir sobre o uso do microfone.
3. A usuária é informada, no cadastro, de que o sistema mostra uso de microfone e de
   que isso é visível para quem olhar o aparelho. É informação de **segurança dela**,
   não letra miúda.

A decisão operacional sobre essa tensão está no [ADR 0009](../.claude/decisions/0009-notificacao-e-cancelamento-protegido.md) e é um ótimo
assunto para a defesa do projeto: demonstra que o time entendeu que os interesses
legítimos aqui conflitam.

## Checklist de hardening

Controles a implementar, não a discutir. Detalhamento criptográfico em ADR
[0004](../.claude/decisions/0004-integridade-da-ocorrencia.md).

| Controle | Implementação |
|---|---|
| Áudio antes do gatilho | só ring buffer em RAM, sobrescrito continuamente |
| Evidência após o gatilho | arquivo privado do app — **nunca** galeria ou downloads |
| Criptografia | AES-GCM ou equivalente autenticado |
| Chave de dados | chave aleatória **por evento**, wrapped por chave do Keystore/Keychain |
| Integridade | SHA-256 + autenticação criptográfica + manifesto |
| Autenticidade | manifesto assinado com chave do dispositivo, não exportável |
| Auditoria | IDs sequenciais, timestamps, versão do app, hashes, resultado de envio |
| Logs | **nunca** logar frase secreta, transcrição completa, GPS ou chave |
| Banco local | dados sensíveis cifrados, mínimo necessário |
| Backup | política explícita; evitar backup descontrolado de evidências |
| Retenção | prazo configurável e exclusão segura conforme a finalidade |
| Rede | TLS. Nunca HTTP simples |
| Estado de alerta | diferenciar `CREATED`, `QUEUED`, `SENT`, `ACKNOWLEDGED` |
| Código de cancelamento | **não** usar a mesma frase da ativação |
| Interface sensível | bloquear exposição acidental em preview e no app switcher |
| Build de release | remover logs de debug, endpoints de teste e modelos de desenvolvimento |
| Atualizações | assinar e versionar mudanças de modelo e de threshold |

No Android, o [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
permite chaves não exportáveis, vinculadas a hardware seguro (TEE / Secure Element)
quando disponível. No iOS, o
[Keychain Services](https://developer.apple.com/documentation/security/ksecclasskey)
cumpre o papel equivalente.

Princípio que resume tudo: **capturar o mínimo possível, pelo menor tempo possível.**

## Pendências jurídicas — não resolver por conta própria

| # | Questão | Estado | Quem responde |
|---|---|---|---|
| J1 | Gravação do agressor sem ciência dele, feita pela própria pessoa presente | **precedente favorável, não conclusão** — ver abaixo | Direito |
| J2 | Base legal para tratar dados dos contatos da Rede de Apoio, que não aceitaram termo nenhum | aberta | Direito / DPO |
| J3 | Se a usuária for adolescente: regras específicas para dados de crianças e adolescentes | aberta | Direito |
| J4 | Responsabilidade do time se o alerta falhar numa emergência real | aberta | Direito |
| J5 | Qual a base legal correta: quem é controlador, quem recebe os dados, qual o fluxo concreto. A LGPD contempla hipóteses específicas para dados sensíveis, incluindo **proteção da vida ou da incolumidade física** | aberta | Direito / DPO |

### J1 — o que se sabe, e o que não se pode afirmar

A jurisprudência brasileira admite, em várias circunstâncias, gravações feitas por
**um dos próprios interlocutores** sem conhecimento do outro. O STF consolidou
entendimento nesse sentido no **Tema 237**
([notícia STF](https://noticias.stf.jus.br/postsnoticias/stf-reconhece-repercussao-geral-e-reafirma-ser-possivel-aproveitar-gravacao-como-prova/)),
e o STJ segue tratando gravações desse tipo como potencialmente válidas.

**Mas há diferenças relevantes** entre gravação por interlocutor, gravação ambiental
por terceiro, gravação com auxílio policial e situações sob sigilo específico.

→ Consequência direta, que é requisito RF-53: **o aplicativo não pode exibir "esta
gravação é uma prova judicial válida".** Pode dizer apenas que preserva informações
de integridade e rastreabilidade, e que o uso jurídico depende do caso concreto.

O precedente torna J1 menos bloqueante do que parecia, mas não dispensa a consulta:
há faculdade de Direito na instituição. Este SOT não arrisca palpite sobre lei.

## Controles entregues e limites pendentes

`DeviceCrypto` cifra os conteúdos sensíveis dos registros SQLite e as configurações
com AES-GCM. Cada envelope tem chave de dados aleatória, protegida por chave AES do
Android Keystore. A assinatura é ECDSA P-256/SHA-256; a chave privada não é exportada
pela aplicação. Esses mecanismos usam as APIs do
[Android Keystore](https://developer.android.com/privacy-and-security/keystore).

O arquivo SQLite contém IDs e sequência, com o conteúdo cifrado em cada registro;
não é SQLCipher nem criptografia do arquivo SQLite inteiro. O segredo da Área
protegida é verificado com PBKDF2 e limitação de tentativas. O bloqueio de tentativas
de acesso não impede o código de coação. A chave de dados não é derivada desse
segredo: **o vínculo exigido em C4 ainda não foi entregue**. O recebimento em
background usa a chave do Keystore sem pedir novo código. Isso mantém a fila
autônoma, mas deixa essa parte do hardening pendente; não foi declarada conforme.

A Área protegida usa `FLAG_SECURE`, volta à Fachada limpa ao sair do aplicativo e
mostra Ocorrências somente mediante navegação explícita. A exportação pede ação e
confirmação e gera arquivo potencialmente sensível. Excluir recebidas remove as
linhas locais e mantém o encadeamento; não promete apagamento físico da memória
flash nem remove cópias do Painel. Retenção automática configurável ainda é pendente.

O Painel requer segredo de acesso, pareamento de chave pública e token de dispositivo.
O certificado TLS é fixado no Android por fingerprint informado no pareamento;
certificado divergente é recusado. O navegador exige validação manual do certificado
local. Os dados do Painel são cifrados em disco, mas a chave fica no notebook:
o operador e as permissões desse computador fazem parte da fronteira de confiança.
Não há serviço público, hospedagem externa, conta na nuvem ou chamada automática.

A revogação do consentimento de comunicação pausa a fila real nas próximas
transmissões. Não desfaz transmissões já realizadas. Revogar localização impede
incluí-la em novas Ocorrências; registros já assinados preservam seu conteúdo.
Para suspender a transmissão de registros pendentes, revogue a comunicação.
As pendências J1 a J5 continuam abertas.

Com [ADR 0010](../.claude/decisions/0010-palavra-por-funcao-e-sms-automatico.md):

- **Gravação:** existe como função desligada por padrão (RF-46). Grava o PCM da
  captura única em blocos AES-GCM de ~1 s, com chave aleatória por gravação
  envelopada pelo Keystore e índice do bloco como dado autenticado; fica em arquivo
  privado, nunca em galeria nem rede (RF-51). A exportação decifra para WAV só por
  ação explícita, confere tags, ordem e SHA-256 do áudio original, e o arquivo
  exportado sai **sem** cifragem. O responsável adiou J1 para o protótipo; a interface
  não afirma validade de prova (RF-53).
- **SMS:** o texto e o link de posição vão em claro pela rede da operadora, e a
  mensagem fica na caixa de **enviados** do aparelho (observado). Quem pegar o
  celular pode ver que houve alerta: limite direto de [C4](02-modelo-de-ameaca.md),
  informado no consentimento de comunicação.
- **Localização:** pontual, obtida ao disparar alerta ou compartilhar posição e só com
  consentimento; no compartilhamento, repetida a cada 5 minutos enquanto armada.
  Não há rastreamento em background fora disso.
- **Diagnóstico de voz:** somente em build debug, o log registra eventos e notas de
  similaridade, nunca o texto falado (RF-52).

Ao atualizar o primeiro protótipo, `LegacyMigration` preserva suas preferências em
um arquivo cifrado no Store e verifica a leitura antes de limpar as preferências
antigas. A frase e os contatos são reaproveitados; comunicação, localização e
calibração exigem nova configuração. A limpeza não garante apagamento físico de
blocos antigos da memória flash.
