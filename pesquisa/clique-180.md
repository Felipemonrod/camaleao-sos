# Dossiê — Clique 180

> **Prioridade de investigação: MÉDIA-ALTA.** É o prior art direto da **Fachada**: a
> camuflagem do Camaleão já foi feita, por política pública federal, há anos. Isso não
> derruba o projeto — reposiciona o que podemos chamar de diferencial.
>
> A pergunta mais urgente é banal e barata: **o app ainda existe?**
> Ver [Q-C180-1](#q-c180-1--o-app-ainda-está-no-ar).

Última atualização: 2026-10-21. ⚠️ **Todas as fontes encontradas são de 2018 ou
anteriores.** Nada confirma o estado atual.

## 1. Ficha

| | |
|---|---|
| **Nome** | Clique 180 |
| **Quem** | ONU Mulheres + Secretaria de Políticas para as Mulheres (SPM), com apoio da Embaixada Britânica |
| **Plataformas** | ⚠️ **fontes divergem** — uma diz Android apenas, outra diz Android e iOS |
| **Status atual** | ⚠️ **desconhecido.** Nenhuma fonte menciona descontinuação, e nenhuma confirma disponibilidade |
| **Fonte primária** | ⚠️ **não localizada.** Só temos imprensa e sites de terceiros |

A ausência de fonte primária é, ela mesma, um dado: a SPM passou por reestruturações
desde 2016, e material institucional desse período frequentemente saiu do ar.

## 2. O que sabemos, com fonte

### A camuflagem

Ao abrir, o app mostra uma **tela de disfarce com uma frase motivacional**. Para acessar
as funções, é preciso **agitar o celular** ou **tocar a tela quatro vezes com dois
dedos**
([Olhar Digital, 2018](https://olhardigital.com.br/2018/03/05/dicas-e-tutoriais/como-usar-aplicativo-que-combate-violencia-contra-mulher/)).

Duas observações de projeto:

- A Fachada é **passiva** — uma frase, não um app funcional. O Camaleão propõe uma
  calculadora que calcula de verdade ([RF-01](../docs/03-requisitos.md)), que é
  camuflagem mais forte: resiste a alguém mexer.
- Os dois gatilhos de entrada exigem **o aparelho destravado e na mão**. É exatamente a
  limitação que o [ADR 0005](../.claude/decisions/0005-gatilhos-multiplos.md) aponta no
  "gesto discreto" e tenta resolver com padrão de botões físicos.

### Funções

| Função | Observação |
|---|---|
| Botão para ligar ao 180 | canal de atendimento, não despacho policial |
| Informação sobre tipos de violência | conteúdo |
| Informação sobre a Lei Maria da Penha | conteúdo |
| "Minha cidade mais segura" | mapa colaborativo de locais onde houve agressão |

Fonte: [Olhar Digital, 2018](https://olhardigital.com.br/2018/03/05/dicas-e-tutoriais/como-usar-aplicativo-que-combate-violencia-contra-mulher/).

→ **Não há botão de pânico com despacho, nem GPS no acionamento, nem gravação.** O
Clique 180 é prior art da **Fachada**, não do Protocolo SOS. Para esse, ver
[Botão do Pânico — PR](botao-do-panico-pr.md).

## 3. O que não sabemos

### Q-C180-1 — O app ainda está no ar?

**Pergunta mais barata e mais urgente do dossiê.** Resposta em 5 minutos.

**Como descobrir:** buscar "Clique 180" na Google Play e na App Store. Se estiver lá,
olhar data da última atualização, número de instalações e avaliações recentes.

**O que significa:**

| Se | Então |
|---|---|
| **está no ar e atualizado** | é prior art vivo. Instalar e estudar a Fachada. Citar como estado da arte |
| **está no ar mas abandonado** (última atualização anos atrás) | ótimo argumento: política pública lança app e não mantém. O Camaleão pode se posicionar sobre sustentabilidade |
| **saiu das lojas** | citar como "existiu, e não está mais disponível". **Não** afirme que foi descontinuado sem fonte |

⚠️ **Não cite o Clique 180 na apresentação sem responder isto.** Afirmar que um app
existe quando ele saiu do ar é o tipo de erro que uma banca pega em 30 segundos — e é
fácil de evitar.

### Q-C180-2 — Android apenas, ou também iOS?

As fontes divergem.

**Como descobrir:** junto com Q-C180-1, nas duas lojas.

**O que significa:** se chegou ao iOS, interessa saber **como** passou pela revisão da
Apple sendo um app camuflado — é diretamente a regra 2.5.14 e o
[R4](../docs/10-riscos-e-questoes-abertas.md). Se não chegou, é evidência indireta de
que a camuflagem **é** um problema na App Store, o que fortalece o
[ADR 0003](../.claude/decisions/0003-android-primeiro.md).

Esta é a pergunta com melhor relação valor/custo do dossiê: cinco minutos de busca
podem gerar evidência para dois documentos.

### Q-C180-3 — Como ele sobreviveu à política de Comportamento Enganoso do Google Play?

O Camaleão tem o [R5](../docs/10-riscos-e-questoes-abertas.md) — a camuflagem pode ser
proibida. Se o Clique 180 esteve anos na Play Store com tela de disfarce, há um caminho.

**Como descobrir:** se o app estiver no ar, ler a **descrição na loja**. A hipótese é
que ele declare a finalidade abertamente, tratando o disfarce como recurso documentado
de segurança — exatamente a mitigação que o
[R5](../docs/10-riscos-e-questoes-abertas.md) propõe.

**O que significa:** confirma ou derruba a mitigação do R5 com evidência real, em vez de
raciocínio nosso. Se um app federal brasileiro fez assim e ficou, é um precedente forte.

### Q-C180-4 — Existe avaliação de uso ou de eficácia?

Downloads, uso real, avaliação pelas usuárias, relatório da ONU Mulheres ou da SPM.

**Como descobrir:** repositórios institucionais da ONU Mulheres; busca acadêmica por
"Clique 180"; TCCs já apareceram na primeira busca.

**O que significa:** se houver avaliação mostrando baixa adoção, é informação importante
e desconfortável — sugere que o gargalo do problema real não é tecnologia, o que está
alinhado com o [estudo da UnB](../docs/11-trabalhos-relacionados.md) sobre desafios de
implantação.

### Q-C180-5 — Qual era a arquitetura?

Era aberto? Há repositório? Como funcionava o mapa colaborativo?

**Como descobrir:** buscar em GitHub e GitLab; procurar material técnico da ONU Mulheres;
verificar se houve licitação ou contrato público com especificação.

**O que significa:** baixo valor para o protótipo. Faça só se as outras perguntas já
estiverem respondidas.

## 4. Lições de projeto

### L1 — A camuflagem não é o diferencial. Pare de tratá-la como tal

Tela de disfarce com gatilho por gesto existe em app de política pública federal
brasileira desde, no mínimo, 2018.

**Aplicar:** em [docs/11](../docs/11-trabalhos-relacionados.md) e na apresentação, o
diferencial reivindicado é **o acionamento por frase reconhecida localmente**, não o
disfarce. Já está assim; este dossiê é a evidência.

### L2 — Fachada funcional é melhor que fachada passiva

Frase motivacional não resiste a alguém pegando o telefone e mexendo. Calculadora que
calcula, resiste.

**Aplicar:** reforça [RF-01](../docs/03-requisitos.md) — "a calculadora precisa calcular
de verdade" — como requisito de **segurança**, não de polimento. Vale deixar isso
explícito no racional do requisito.

### L3 — Gatilho que exige o aparelho na mão é insuficiente

Agitar o celular e tocar quatro vezes com dois dedos pressupõem aparelho destravado e
disponível. É a lacuna que o [ADR 0005](../.claude/decisions/0005-gatilhos-multiplos.md)
identifica.

**Aplicar:** já aplicado. O Clique 180 é o exemplo concreto do problema que o ADR 0005
descreve em abstrato — vale citá-lo lá quando este dossiê fechar.

## 5. Roteiro de investigação

| # | Ação | Tempo | Responde |
|---|---|---|---|
| 1 | **Buscar nas duas lojas** | 5 min | Q-C180-1, Q-C180-2 |
| 2 | Se estiver no ar: ler a descrição da loja com atenção | 10 min | Q-C180-3 |
| 3 | Se estiver no ar: instalar e estudar a Fachada e os gatilhos | 20 min | detalhe das lições |
| 4 | Buscar fonte primária: ONU Mulheres Brasil, SPM/Ministério das Mulheres | 30 min | ficha, Q-C180-4 |
| 5 | Busca acadêmica por "Clique 180" | 1 h | Q-C180-4 |
| 6 | Buscar repositório público | 20 min | Q-C180-5 |

**Faça o passo 1 antes de qualquer outra coisa deste dossiê.** Cinco minutos decidem se
o resto vale o esforço.

## 6. Ética da investigação

Instalar e explorar um app público é pesquisa normal. Duas ressalvas:

- **Não use o botão do 180** para teste. É central de atendimento real.
- **Não polua o mapa colaborativo** com dados falsos, se ele existir e aceitar
  contribuição.

## 7. Fontes

**Primárias:** ⚠️ nenhuma localizada. Procurar em ONU Mulheres Brasil e no Ministério
das Mulheres — é o item 4 do roteiro.

**Secundárias — datam e contextualizam, não confirmam estado atual:**

- [Olhar Digital (2018) — tutorial com a descrição da tela de disfarce](https://olhardigital.com.br/2018/03/05/dicas-e-tutoriais/como-usar-aplicativo-que-combate-violencia-contra-mulher/)
- [TechTudo — página do app](https://www.techtudo.com.br/tudo-sobre/clique-180/)
- [APF — Defesa da Mulher, aplicativo Clique 180](http://www.apf.org.br/fundacoes/index.php/noticias/noticias-de-associadas/3079-defesa-da-mulher-aplicativo-clique-180.html)
- [Estado de Minas (2016) — lista de 5 apps do gênero](https://www.em.com.br/app/noticia/especiais/bigideia/bigideia-noticia/2016/05/10/bigideia,760987/fuja-do-perigo-confira-5-apps-que-combatem-a-violencia-contra-a-mulhe.shtml)
