---
data: 2026-10-21
status: aceito
---

# 0009 — Notificação clara e cancelamento dentro da Área protegida

## Contexto

O time precisava definir o aviso de microfone e o cancelamento. Na sessão de
implementação, o responsável aprovou explicitamente: aviso claro de microfone ativo
e cancelamento somente dentro da Área protegida. A menção anterior a vibração foi
esclarecida como dúvida sobre a pergunta, não solicitação de alterar RF-47.

## Decisão

A notificação informa microfone ativo e permite Desarmar. O aplicativo mantém a
Fachada, sem tentar esconder indicadores do sistema. A preparação de uma Ocorrência
pode ser cancelada dentro da Área protegida antes de entrar na fila. A janela de
comando também pode ser encerrada por uma ação nessa área ou pelo comando distinto
`cancelar`. A Fachada não mostra contagem, alerta, som nem vibração.

Depois da transmissão não existe revogação retroativa do recebimento: o aplicativo
informa o estado observado e preserva o recibo. Cancelar uma preparação não apaga
Ocorrências anteriores nem quebra a sequência dos manifestos.

## Consequências

Resolve a decisão operacional antes aberta sobre notificação e cancelamento.
Mantém o comando de cancelamento diferente da Frase de Segurança.

**Custa:** o aviso pode revelar que existe captura de áudio. A Área protegida informa
esse risco. O cancelamento exige conseguir acessar essa área antes da fila; não é
uma garantia de desfazer um envio. Existe preparação breve local antes da fila,
cujo custo de latência deve ser medido em RNF-02.

**Não resolve:** a adequação para vítimas reais, políticas de loja, atendimento ou
pendências jurídicas. Essas avaliações continuam com os responsáveis indicados no
SOT. Esta decisão não autoriza afirmar que a Fachada torna o aplicativo invisível.
