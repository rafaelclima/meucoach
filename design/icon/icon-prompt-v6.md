# Prompt v6 — Ícone do app MeuCoach (cronômetro grande com M/rota dentro)

> **Status:** variação para testar. A v5 (cronômetro na partida) é a versão mais segura para 48 px.
> Prompt autocontido: copie o bloco da seção 3 direto para o Gemini.

---

## 1. A composição em uma frase

**Um cronômetro grande é a moldura; dentro do mostrador, o M vira uma rota de GPS e o corredor corre sobre o topo do M.**

A leitura é: **tempo (aro) → percurso (M) → ação (corredor)**. O cronômetro não é um objeto solto ao lado; ele é o campo onde o treino acontece.

```
        ┌──────────────┐
        │   (corredor) │  ← lima, sobre o M
        │   ╱╲    ╱╲   │
        │  ╱  ╲  ╱  ╲  │  ← M/rota branco
        │ ╱    ╲╱    ╲ │
        └──────────────┘
             cronômetro
```

## 2. A hierarquia

| Elemento | Papel | Peso |
|---|---|---|
| **Aro do cronômetro** | moldura, leitura de "tempo" | ~74–80% do lado; traço grosso |
| **M / rota** | percurso dentro do mostrador | ~48–54% da largura; traço médio |
| **Corredor** | herói lúdico | ~20–22% da altura; único lima |

O risco desta composição é density: aro + M + corredor no mesmo espaço. Para funcionar, o mostrador precisa ficar **vazio** (sem ticks, números, ponteiros) e o corredor precisa de **folga** do aro.

---

## 3. Prompt principal (copiar e colar no Gemini)

```text
Você é um designer de ícones de aplicativos premiados. Crie o ícone de launcher do MeuCoach, um app Android de coach de corrida. O resultado deve ser um símbolo plano, lúdico e premium — não uma ilustração, não um mascote, não um mockup.

CONTEXTO DA MARCA (não desenhe este texto):
- MeuCoach é um coach de corrida pessoal, local-first e baseado em evidências. Ele importa treinos, calcula carga e prontidão, prescreve o próximo treino e explica o porquê.
- A promessa é "Precisão que orienta". O app é escuro, silencioso e preciso, com um único accent de ação: lima ácido.
- Cores exatas: fundo #08090B (grafite quase preto), aro/M #F4F6F7 (branco quente), corredor #D8FF3E (lima ácido). Nenhuma outra cor.
- Público: corredor iniciante/intermediário que quer saber o que fazer hoje e por quê.

A IDEIA EM UMA FRASE:
Um cronômetro grande é a moldura do ícone; dentro do mostrador, o M vira uma rota de GPS e o corredor corre sobre o topo do M. O corredor é o herói; o M é o percurso; o cronômetro é o tempo.

COMPOSIÇÃO (siga esta ordem):
1. FUNDO: quadrado 1:1, 1024×1024, preenchido por completo com #08090B. Sem moldura, sem margem branca, sem cantos arredondados (o launcher Android aplica a máscara).
2. CRONÔMETRO / MOLDURA: um aro branco grande, circular, plano, ocupando cerca de 74–80% do lado do ícone. O traço do aro é grosso e uniforme, com cerca de 7–8% do lado. No topo do aro, uma coroa retangular arredondada, como o botão de um cronômetro; opcionalmente, um pequeno botão lateral a 2 horas. O mostrador é vazio e tem o mesmo fundo #08090B: sem ponteiros, sem números, sem ticks, sem vidro, sem sombra. O aro não pode parecer uma moldura de foto, uma lupa ou um pino de mapa.
3. M / ROTA: dentro do mostrador, um traço branco contínuo, com pontas e juntas arredondadas, que forma um M. Ele nasce na parte inferior esquerda do mostrador, sobe até um pico arredondado, desce até um vale no centro, sobe até o topo direito — que é um platô curto e levemente arredondado, como uma curva de rota — e desce até a parte inferior direita. O M ocupa cerca de 48–54% da largura do ícone e fica na metade inferior do mostrador, deixando espaço para o corredor em cima. As pernas ficam quase verticais; nada de M aberto como montanha ou tenda. O M deve parecer uma rota de GPS, não uma letra de fonte. Deixe uma folga de pelo menos 8% entre o M e o aro.
4. CORREDOR: dentro do mostrador, em perfil, correndo para a direita, sobre o platô do topo direito do M. Uma perna de apoio toca a linha da rota; a outra perna fica no ar, dobrada para trás. Um braço vai à frente, o outro atrás. O tronco se inclina levemente para a frente. É um pictograma sólido, com 5–6 massas, sem rosto, sem roupa, sem tênis detalhado, sem músculos. A cabeça tem cerca de 1/5 da altura do corpo. Os membros são grossos o suficiente para sobreviver a 48 px. O corredor é o único elemento lima do ícone. Ele fica totalmente dentro do mostrador, sem tocar o aro: mantenha pelo menos 8% de folga entre a cabeça/braços e a borda interna superior.
5. EQUILÍBRIO: o aro é a moldura; o M é a pista; o corredor é o acento. O conjunto deve parecer um cronômetro com um percurso dentro, não um relógio de parede com um gráfico. Centralize opticamente o M e o corredor dentro do mostrador; nada toca as bordas externas do ícone (12–15% de respiro).

O QUE O ÍCONE NÃO PODE TER:
- Pingo, nó, bolinha ou ponto amarelo no meio do M. O único círculo isolado é o aro do cronômetro; a cabeça do corredor faz parte da silhueta.
- M aberto como montanha, tenda ou "W"; pernas splayed.
- Corredor realista, musculoso, com rosto, cabelo, camiseta, shorts ou tênis detalhado. Nada de boneco de cabeça gigante.
- Corredor flutuando, sentado, saltando, fora do mostrador ou em cima do aro. Ele corre no platô do M, dentro do aro, com um pé na linha.
- Cronômetro detalhado com ponteiros, números, marcas, ticks ou mostrador cheio. Apenas aro + coroa (+ botão lateral opcional).
- Aro com preenchimento claro, fundo de relógio, sombra ou efeito de vidro. O mostrador é o mesmo #08090B.
- Linhas de velocidade, suor, poeira, confete, estrelas, raios ou efeitos de movimento.
- Gradiente, brilho, neon, sombra, relevo, bisel, reflexo, vidro, metal, plástico, 3D ou textura.
- Mapa, ruas, pino, bússola, alvo, coração, haltere, troféu, pódio, cronômetro de academia ou fita de chegada.
- Texto, letras, números, "MEUCOACH", assinatura ou marca d'água.
- Mockup de celular, mão, mesa, moldura, cartão, grade, colagem ou múltiplos ícones. Uma única imagem quadrada, só o ícone.
- Traço fino, detalhe pequeno ou qualquer coisa que desapareça a 48 px.

ESTILO:
- Vetorial plano, geométrico, com contraste alto e acabamento limpo.
- Traço uniforme, cantos e pontas arredondadas, sem afinamento, sem pincel, sem caligrafia.
- A imagem deve parecer um arquivo vetorial exportado, não uma pintura, não um render 3D.
- O lúdico vem da pose do corredor e da curva do platô; não de elementos cartoon.

TESTES DE QUALIDADE (faça antes de finalizar):
1. A 48 px, o aro e o M continuam legíveis? O corredor não pode virar um borrão verde.
2. A 48 px, o aro ainda lê como cronômetro (aro + coroa), sem virar lupa ou pino?
3. Em uma cor (silhueta preta), o corredor ainda se distingue pela pose e pelo espaço ao redor?
4. Em escala de cinza, o corredor continua visível?
5. A 512 px, o cronômetro lê como cronômetro e o M lê como rota?
6. O M lê como M e como rota ao mesmo tempo, sem parecer montanha?
7. O ícone parece um coach premium e lúdico, não um app de academia ou um timer genérico?
Se falhar em 1, 2 ou 6, simplifique: engrosse o aro, aumente o mostrador, diminua a cabeça do corredor, aumente a folga do corredor para o aro e reduza o M.

ENTREGA:
- Uma única imagem quadrada 1024×1024, plana, centralizada, sem moldura e sem mockup.
- Não inclua texto, assinatura ou explicação na imagem.
- Depois da imagem, escreva em texto (fora da imagem) um rationale de 3–5 linhas explicando a leitura: tempo (aro) → percurso (M) → ação (corredor).
```

---

## 4. Correções rápidas (cole depois do resultado)

- **O corredor virou um borrão verde:** `Aumente o corredor para 22% da altura, diminua a cabeça para 1/5 do corpo, engrosse os membros e simplifique a pose: uma perna no chão, outra no ar, um braço à frente, outro atrás. Mantenha-o dentro do mostrador.`
- **O corredor está encostando no aro:** `Reduza o corredor em 10–15% ou desça o M/platô. Deixe pelo menos 8% de folga entre o corredor e a borda interna superior do aro.`
- **O M está encostando no aro:** `Reduza o M para 48–50% da largura e centralize-o no mostrador, com 8–10% de folga em relação ao aro.`
- **O aro virou uma moldura de foto:** `Afine o aro, remova qualquer preenchimento claro e adicione a coroa no topo. O mostrador é vazio, na cor do fundo.`
- **O aro virou lupa/pino:** `Remova cabo, alça ou ponta. O aro é um círculo completo; a coroa retangular no topo o identifica como cronômetro.`
- **O mostrador ficou cheio de ticks/números:** `Limpe o mostrador. Nada de ponteiros, números ou marcas; apenas o M/rota e o corredor.`
- **O M virou montanha/tenda:** `Verticalize as pernas do M. Reduza a abertura para no máximo 5°. O M é uma rota precisa, não uma montanha.`
- **O platô virou uma mesa:** `Suavize o platô: é uma curva de rota levemente achatada, não uma plataforma reta.`
- **Ficou um relógio de parede:** `O aro não é um relógio; é um cronômetro de pulso. Aro fino, coroa simples, mostrador vazio, M/rota dentro.`
- **Apareceu pingo no meio do M:** `Remova qualquer ponto, nó ou bolinha no meio do M. O único círculo isolado é o aro do cronômetro; a cabeça do corredor é parte do corpo.`
- **Ficou uma cena/paisagem:** `Remova chão, cenário, sombra e perspectiva. Os únicos elementos são o aro, o M/rota e o corredor.`

---

## 5. Variações controladas

| Variação | O que muda | Quando usar |
|---|---|---|
| **A (principal)** | Aro grande + M na metade inferior + corredor no platô direito. | Teste principal desta v6. |
| **B** | Corredor no pico esquerdo, M mais baixo. | Se o corredor estiver colidindo com o aro no lado direito. |
| **C** | Aro mais fino, M maior. | Se o aro estiver dominando e o M sumindo. |
| **D** | Corredor maior, aro mais fino, sem botão lateral. | Se o corredor estiver virando borrão a 48 px. |

Mantenha sempre: fundo #08090B, aro/M brancos, corredor lima, mostrador vazio, sem texto e sem mockup.

---

## 6. Nota honesta sobre esta composição

Ela é a mais densa das três direções (M com nó, cronômetro na partida, cronômetro como moldura). O ponto crítico é o **corredor a 48 px**: dentro de um aro grande, ele tem menos espaço e pode virar um borrão verde. Se o Gemini não resolver o corredor com 5–6 massas e membros grossos, a v5 (cronômetro na partida, fora do M) é a versão mais segura para o launcher.

Gere em **PNG 1024×1024**, não JPG. Quando tiver um resultado que goste, me manda o arquivo: eu reconstruo o SVG limpo, faço a versão monochrome e monto o pacote de adaptive icon.
