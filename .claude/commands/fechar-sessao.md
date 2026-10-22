Resuma esta sessão de trabalho e registre o que for durável, antes de encerrar.

1. Liste as decisões de arquitetura ou de produto tomadas nesta sessão — não passos
   de implementação, decisões de verdade: algo que, se revertido, mudaria o resultado.
2. Para cada decisão importante, crie um arquivo em `.claude/decisions/NNNN-titulo.md`
   (use o próximo número disponível na pasta) seguindo o formato Contexto / Decisão /
   Consequências.
3. Para o resto — o que ficou pra próxima sessão, pontos soltos, coisas testadas que
   não funcionaram — acrescente uma entrada no `.claude/NOTES.md`, em ordem
   cronológica, com a data.
4. Não repita no NOTES.md o que já virou uma decisão formal — cada fato mora em um
   lugar só.
5. Ao final, rode:
   `git add .claude/ && git commit -m "sessão: <resumo curto>" && git push`
