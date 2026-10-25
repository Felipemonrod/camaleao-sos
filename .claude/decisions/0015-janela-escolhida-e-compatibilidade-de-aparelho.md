---
data: 2026-10-25
status: aceito, condicionado a teste em aparelho de terceiro
---

# 0015 — Janela de comando escolhida pela usuária e diagnóstico de compatibilidade

> Ajusta a janela de comando definida no
> [ADR 0002](0002-motor-de-reconhecimento.md) e no RF-35, e acrescenta ao
> [ADR 0014](0014-funcao-na-mesma-fala-e-gravacao-honesta.md) o que falta para o
> aplicativo funcionar em aparelho que não é o do responsável.

## Contexto

**A janela de 6 s é curta para gente de verdade.** No teste com voz do responsável em
25/10, cinco disparos foram pelo caminho antigo, com intervalos medidos no log entre a
frase e a palavra: 2,0 s, 2,9 s, 2,7 s, 4,1 s e 2,5 s. Nenhum usou a releitura do
ADR 0014 — ou seja, ninguém fala tudo numa tirada só por padrão, e quem pausa chega
perto do limite. Com 6 s, a usuária que hesita perde o disparo.

**O app falha em silêncio em aparelho alheio.** Um colega não conseguiu habilitar SMS
num Motorola. A causa provável é a mesma já vista num Xiaomi: instalado fora da loja, o
Android 13+ trata SMS e telefone como **configuração restrita** e a permissão fica
acinzentada até a pessoa abrir "Informações do app → ⋮ → Permitir configurações
restritas". A tela do sistema que avisa isso ("O app não recebeu a seguinte permissão de
acesso: SMS") não diz onde fica o botão. Sem diagnóstico, quem testa conclui que o
Camaleão não funciona.

**A parada da gravação não respondia durante a ligação.** O `LiveState.stopRecording`
era lido depois do ponto em que o laço desvia quando o microfone é da chamada: tocar
"Parar gravação" não fazia nada até a ligação acabar.

## Decisão

1. **A espera da janela de comando é ajuste da usuária**, de 5 a 60 s, padrão **30 s**.
   O número aparece nas telas que o citam, lido do mesmo ajuste, para a interface não
   prometer um prazo diferente do que o serviço usa.
2. **A duração da gravação continua sendo escolha da usuária** (1 a 30 min, campo já
   existente) e vira requisito explícito, em vez de detalhe de implementação.
3. **"Parar gravação" passa a valer com ou sem captura**, pelo mesmo motivo do RF-71.
4. **Diagnóstico de compatibilidade no aparelho:** uma tela lista microfone, SMS,
   ligação, localização, notificação e economia de bateria com três estados — atendido,
   faltando, ou inexistente neste aparelho — e, no que falta, diz o que fazer. Quando o
   app foi instalado fora da loja, o texto do SMS e da ligação é o caminho da
   configuração restrita. Aparelho sem telefonia é dito como tal, não como permissão
   faltando.
5. **O registro de falha de SMS distingue os casos**: sem autorização da usuária, sem
   telefonia no aparelho, permissão bloqueada por configuração restrita, ou permissão
   simplesmente negada.

## Consequências

- **A janela de 30 s multiplica por cinco o tempo em que uma palavra de função é
  aceita.** O portão continua sendo a frase, com os dois estágios, e a gramática da
  janela é restrita — mas uma conversa que siga um reconhecimento correto da frase tem
  agora cinco vezes mais chance de conter a palavra. Quem quiser o comportamento antigo
  baixa o ajuste para 6 s. A medição de falso alarme com a janela longa **não foi
  feita**, e é o que condiciona o status deste ADR.
- O RF-35 dizia "4 a 6 s". Continua valendo como faixa de origem do ADR 0002; o valor
  em uso passa a ser o do RF-73. O ADR 0002 não foi alterado (R5).
- O diagnóstico lê `getInstallSourceInfo` para saber se o app veio de fora da loja. Em
  API < 30 a resposta é assumida como "fora da loja", que é o caso de quem instala pela
  página — e o texto mais longo não prejudica quem instalou pela loja.
- Nada disso resolve a camada própria de fabricante (MIUI/HyperOS, Motorola): o
  diagnóstico aponta o caminho padrão do Android, e o passo extra do fabricante continua
  sendo descoberta manual. ⚠️ **Não verificado** em Motorola nem em Xiaomi nesta sessão.
