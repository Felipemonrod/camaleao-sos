# CLAUDE.md

O contrato para agentes de IA deste repositório é o [AGENTS.md](AGENTS.md).
**Leia-o inteiro antes do primeiro `write`.** Este arquivo só acrescenta o que é
específico do Claude Code.

## Ordem de leitura

1. [AGENTS.md](AGENTS.md) — o contrato
2. [SISTEMA-SOT.md](SISTEMA-SOT.md) — as seis invariantes do método
3. [SOT.md](SOT.md) — o estado do projeto

## Específico do Claude Code

### Ritual de fechamento de sessão

Antes de encerrar, rode `/fechar-sessao`. Ele está em
[`.claude/commands/fechar-sessao.md`](.claude/commands/fechar-sessao.md) e grava o que
for durável: decisão de verdade vira ADR em `.claude/decisions/`, o resto vai para
`.claude/NOTES.md`.

### Verificação antes de commitar

```bash
python tools/verificar-sot.py
```

Falhou, não commite. O CI roda o mesmo script.

### Commits

- Mensagens em português, no imperativo: `docs: acrescenta ADR 0008`, `feat: detecta frase com tela bloqueada`
- **Sem `Co-Authored-By` e sem rodapé de IA** (AGENTS.md R10). O uso de IA vai em
  [RELATORIO-IA.md](RELATORIO-IA.md)
- `git add` por caminho explícito. Nunca `git add -A` nem `git commit -a`
- Conferir com `git diff --cached --name-only` antes de confirmar

### Limites

- Não editar `referencias/` (R6)
- Não fechar questão aberta `Qn` sem instrução explícita (R3)
- Não criar documento novo em `docs/` sem perguntar (AGENTS.md §5)
