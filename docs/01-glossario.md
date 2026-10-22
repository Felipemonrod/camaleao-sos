# 01 — Glossário

Nomes canônicos. Use **exatamente** estes termos no código, nos documentos e na
apresentação. Divergência de nome é a forma mais barata de criar confusão caríssima.

| Termo | Significado | Não chame de |
|---|---|---|
| **Camaleão SOS** | O produto inteiro. | app, aplicativo X |
| **Fachada** | A interface camuflada (calculadora/notas) que o agressor vê. | disfarce, máscara |
| **Área protegida** | Configuração do Camaleão, alcançada por um segredo digitado na Fachada. | menu secreto, admin |
| **Sentinela de Voz Offline** | O subsistema que escuta localmente e reconhece a Frase de Segurança. Nome público do recurso. | reconhecimento de voz, wake word, assistente |
| **Armar / Desarmar** | Ligar e desligar a Sentinela. A Sentinela só existe **armada**. | ativar/desativar (ambíguo com o SOS) |
| **Frase de Segurança** | A frase cadastrada pela usuária que dispara o SOS. | senha, palavra-chave, comando |
| **Gatilho** | Qualquer caminho que leve ao Protocolo SOS: voz, código ou botão. | trigger, acionador |
| **Protocolo SOS** | A sequência completa pós-gatilho: localização, evidência, registro, alerta, confirmação. | SOS, alarme, emergência |
| **Ocorrência** | O registro de um Protocolo SOS executado. É a entidade persistida. | evento, log, alerta |
| **Rede de Apoio** | As pessoas pré-autorizadas que recebem a Ocorrência. | contatos, emergência |
| **Painel** | A interface web onde a Rede de Apoio vê as Ocorrências. | dashboard, central |
| **Buffer pré-evento** | Áudio recente mantido só em RAM, sobrescrito continuamente. | gravação, histórico |
| **Núcleo nativo** | `app/sentinela/`: microfone, ciclo de vida, detecção. Separado de `app/ui/`. | plugin, backend |

## Distinções que importam

**Armar ≠ disparar.** Armar é a usuária decidindo, com calma, que o modo fique
ativo. Disparar é a Frase de Segurança sendo reconhecida. Confundir os dois
confunde a apresentação inteira.

**Falso positivo ≠ inconveniência.** Um Protocolo SOS disparado por engano, na
frente do agressor, é uma **falha de segurança da usuária** — possivelmente a
pior falha que este sistema pode ter. Ver [02-modelo-de-ameaca](02-modelo-de-ameaca.md).

**Camuflada ≠ invisível.** A Fachada esconde a *finalidade* do aplicativo. Ela não
esconde, e não deve tentar esconder, os indicadores de microfone do sistema
operacional. Ver [04-restricoes](04-restricoes-plataforma.md).
