# 00 — Visão de produto

## O problema

Uma pessoa em situação de violência doméstica, na presença do agressor,
frequentemente **não consegue**:

- desbloquear o telefone sem ser vista;
- abrir um aplicativo cuja finalidade é visível no ícone ou no nome;
- falar ao telefone com alguém;
- justificar por que está mexendo no aparelho.

O gargalo não é a falta de canais de denúncia. É o **momento da coação**: o
intervalo em que pedir ajuda pelos meios normais é mais perigoso do que não pedir.

> ⚠️ O enunciado acima é reconstruído a partir da pesquisa de viabilidade. O
> documento original da proposta do hackathon ainda não está em `referencias/`.
> Até lá, trate esta seção como hipótese do time, não como requisito citado.

## A pessoa protegida

Suposições a validar (ver [10-riscos](10-riscos-e-questoes-abertas.md)):

- tem um smartphone próprio, que o agressor **eventualmente manuseia**;
- tem uma rede de apoio de 1 a 3 pessoas de confiança;
- não é tecnicamente sofisticada e não vai configurar nada complexo sob estresse;
- configura o aplicativo **em um momento de segurança**, não durante a crise.

A última suposição é a mais importante do projeto: tudo que exige decisão do
usuário acontece **antes**, nunca durante.

## A proposta

Interface camuflada (calculadora ou notas) + **múltiplos gatilhos** que levam ao
mesmo Protocolo SOS. Ver [05-arquitetura](05-arquitetura.md).

O diferencial técnico é a **Sentinela de Voz Offline**: a pessoa arma o modo com
antecedência e, depois disso, uma frase falada dispara o protocolo sem que ela
toque no aparelho.

## Escopo

### Dentro do escopo do protótipo

- Interface camuflada funcional (a calculadora precisa calcular).
- Cadastro e calibração da frase de segurança.
- Armar / desarmar a Sentinela, com estado visível para a própria usuária.
- Detecção local da frase com tela bloqueada, sem internet.
- Protocolo SOS: localização, registro do evento, alerta para um painel.
- Gatilho alternativo que não depende de microfone.
- Painel da rede de apoio recebendo o alerta em tempo real.

### Fora do escopo do protótipo — e dito explicitamente

| Fora | Por quê |
|---|---|
| Publicação em loja | Ver [10-riscos](10-riscos-e-questoes-abertas.md) — há risco real de política de loja, não só custo. |
| Integração com polícia / 190 / Ligue 180 | Exige convênio institucional. Não se improvisa, e um alerta que falha é pior que nenhum. |
| WhatsApp, SMS, push de produção | Dependem de serviço pago ou de aprovação. O protótipo usa servidor local. |
| Backend em nuvem | Demonstração roda em notebook na rede local. |
| Treinar modelo KWS próprio em PT-BR | Trabalho de pesquisa separado. Caminho futuro, não protótipo. |
| Funcionamento com o app encerrado | **Impossível** para app comum. Ver [04-restricoes](04-restricoes-plataforma.md). |
| Resposta da rede de apoio | O protótipo prova que o alerta **chega**. O que a rede faz com ele é desenho de serviço, não de software. |

### Critério de sucesso do protótipo

Uma pessoa fala a frase, sem tocar no telefone bloqueado, e o alerta aparece no
painel em menos de 10 segundos — **e** uma frase parecida, mas diferente, não
dispara nada. As duas metades contam igual.
