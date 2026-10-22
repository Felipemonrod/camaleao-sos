---
data: 2026-10-21
status: aceito
---

# 0004 — Manifesto de evidência assinado e encadeado, não SHA-256 isolado

> **Esta decisão corrige a proposta original do hackathon**, que apresentava SHA-256
> como garantia de "validade probatória e cadeia de custódia". As duas análises de
> referência apontam o problema; a segunda traz o desenho correto.

## Contexto

A proposta original inclui SHA-256 sobre a Ocorrência, apresentado como garantia de
integridade e de cadeia de custódia da evidência.

Essa formulação é forte demais, por dois motivos.

**Tecnicamente:** a mesma parte produz o dado e o hash. Quem alterar o dado
recalcula o hash e envia os dois. Um hash só tem valor se existir ponto de comparação
que o adversário não controle.

**Juridicamente:** o Código de Processo Penal define cadeia de custódia como o
conjunto de procedimentos para manter e documentar a história cronológica do
vestígio — reconhecimento, coleta, acondicionamento, transporte, recebimento,
processamento, armazenamento e descarte. SHA-256 demonstra que uma sequência de
bytes não mudou. **Não demonstra quem coletou, quando, quem teve acesso, nem como o
arquivo foi manipulado.**

Isso importa porque o valor da Ocorrência é ser prova, e uma banca de Computação é
exatamente o público que pergunta "integridade contra quem?".

## Decisão

A Ocorrência não é um arquivo com um hash. É um **manifesto de evidência assinado**,
encadeado ao anterior, com recibo do servidor.

```
event_id
   ├── created_at_device
   ├── monotonic_sequence
   ├── device / app version
   ├── audio metadata
   ├── original_sha256
   ├── encrypted_file_sha256
   ├── previous_manifest_hash        ← encadeia os eventos
   └── signature(device_private_key) ← chave não exportável
            ↓
      manifesto de evidência
            ↓
      armazenamento local protegido
            ↓
   quando houver rede
            ↓
      cópia + recibo do servidor     ← carimbo que o aparelho não falsifica
```

Três mecanismos, cada um resolvendo uma coisa diferente:

1. **`previous_manifest_hash`** forma uma cadeia encadeada. Apagar ou alterar um
   evento no meio quebra a cadeia de forma detectável.
2. **Assinatura com chave privada protegida pelo sistema** — Android Keystore, com
   chave não exportável e vinculada a hardware seguro (TEE/Secure Element) quando
   disponível; Keychain Services no iOS. Dificulta fabricar um manifesto depois sem a
   chave.
3. **Recibo do servidor:** ao receber, o servidor assina
   `(hash do manifesto, hora de recebimento)` com a própria chave e devolve o recibo,
   que o dispositivo guarda. Estabelece que aquele conteúdo existia naquele momento.

O recibo também serve de **confirmação de recebimento** (RF-44) e de transição
`SENT → ACKNOWLEDGED` na máquina de estados do alerta (ADR 0006). Um mecanismo, três
funções.

### Criptografia dos dados

| Controle | Implementação |
|---|---|
| Criptografia | AES-GCM, ou equivalente autenticado |
| Chave de dados | chave aleatória **por evento**, protegida (wrapped) por chave do Keystore/Keychain |
| Evidência | arquivo privado do app — **nunca** galeria ou downloads |
| Rede | TLS. Nunca HTTP simples |

### O que o aplicativo pode e não pode afirmar

| ❌ Não pode dizer | ✅ Pode dizer |
|---|---|
| "esta gravação é prova judicial válida" | "o registro preserva informações de integridade e rastreabilidade" |
| "SHA-256 garante a cadeia de custódia" | "o manifesto é assinado no dispositivo, encadeado ao anterior e contra-assinado pelo servidor" |

O termo SHA-256 continua correto e pode aparecer na apresentação — é a função de hash
dentro do esquema. O que muda é a afirmação em volta dele.

## Consequências

**Resolve:** a integridade passa a ter significado verificável e defensável, e a
afirmação jurídica passa a ser sustentável.

**Resolve:** a confirmação de recebimento sai de graça.

**Custa:** pareamento inicial entre dispositivo e servidor para trocar chaves
públicas. Mais um passo na configuração.

**Custa:** gerenciamento de chaves no servidor local da demonstração. É pouco código,
mas é código.

**Custa:** o `monotonic_sequence` e o `previous_manifest_hash` exigem estado
persistente consistente. Um reset de dados do app quebra a cadeia, e isso precisa ser
tratado como evento registrado, não como erro silencioso.

**Limite honesto a declarar:** isto protege contra alteração **depois** da criação.
Não protege contra um aparelho comprometido que assine um manifesto falso na origem.
Nenhum esquema de assinatura protege contra isso, e dizer o limite é melhor que
deixar a banca encontrá-lo.
