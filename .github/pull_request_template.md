## O que muda

<Uma ou duas frases. Se for documentação, diga qual documento é o dono do assunto.>

## Tipo

- [ ] Documentação
- [ ] Novo ADR / decisão
- [ ] Código
- [ ] Correção de fonte ou link

## Rastreabilidade

- Requisitos atendidos: <RF-nn / RNF-nn, ou "nenhum, é documentação">
- ADRs afetados: <NNNN, ou "nenhum">
- Questões abertas fechadas: <Qn, ou "nenhuma">

## Checklist do Sistema SOT

Ver [SISTEMA-SOT.md §8](../SISTEMA-SOT.md#8-checklist-antes-de-abrir-pr).

- [ ] Nenhum fato duplicado — repeti algo? trocei por link (I1)
- [ ] Toda afirmação nova de plataforma tem link para fonte primária (I2)
- [ ] Nenhuma decisão foi discutida fora de um ADR (I3)
- [ ] Toda dúvida virou `Qn` em `docs/10`, com dono e bloqueio (I4)
- [ ] Nenhum status ficou mais otimista que a realidade (I5)
- [ ] `python tools/verificar-sot.py` passou (I6)
- [ ] Usei os nomes de `docs/01`
- [ ] Mexi em requisito? ID novo, não reciclado
- [ ] Novo ADR? Consequências incluem **custo**, e está na tabela do `SOT.md`
- [ ] `.claude/NOTES.md` atualizado, sem repetir o que virou ADR
- [ ] `RELATORIO-IA.md` atualizado, se usei IA
- [ ] Sem `Co-Authored-By` nem rodapé de IA nos commits

## Se for código

- [ ] Conferi os contra-requisitos `CR-nn` de `docs/03`
- [ ] Rodei o teste correspondente de `docs/09`
- [ ] Reportei o resultado **real**, inclusive se falhou
