# Prompt v5 — Ícone do app MeuCoach (corredor no M + cronômetro)

> **Status:** direção principal. Substitui o M com nó (v4) e o corredor + arco (v1–v3).
> **Variação em teste:** `icon-prompt-v6.md` (cronômetro grande, com o M/rota dentro do mostrador).
> Prompt autocontido: copie o bloco da seção 3 direto para o Gemini.

---

## 1. A composição em uma frase

**Um corredor em perfil corre sobre o topo de um M que é, ao mesmo tempo, uma rota de GPS; no início da rota, um cronômetro simples marca a partida.**

A história visual lê da esquerda para a direita:

```
[cronômetro] ─── sobe o M ─── [corredor no platô] ─── desce a rota ───→
   partida                         ação                    continua
```

## 2. A regra que faz ou quebra o ícone

Três elementos não podem ter o mesmo peso. Se o M, o corredor e o cronômetro competirem, vira uma cena e, em 48 px, vira borrão.

| Elemento | Papel | Peso |
|---|---|---|
| **M / rota** | silhueta, chão, caminho | 55–60% da largura; traço grosso |
| **Corredor** | herói lúdico | 22–26% da altura; único elemento lima |
| **Cronômetro** | detalhe de leitura (partida) | 16–18%; aro fino, coroa simples |

O lúdico vem da **pose do corredor**, do **platô do M** e da **diagonal** da composição — não de olhos, sorriso, confete, suor ou linhas de velocidade.

---

## 3. Prompt principal (copiar e colar no Gemini)

```text
Você é um designer de ícones de aplicativos premiados. Crie o ícone de launcher do MeuCoach, um app Android de coach de corrida. O resultado deve ser um símbolo plano, lúdico e premium — não uma ilustração, não um mascote, não um mockup.

CONTEXTO DA MARCA (não desenhe este texto):
- MeuCoach é um coach de corrida pessoal, local-first e baseado em evidências. Ele importa treinos, calcula carga e prontidão, prescreve o próximo treino e explica o porquê.
- A promessa é "Precisão que orienta". O app é escuro, silencioso e preciso, com um único accent de ação: lima ácido.
- Cores exatas: fundo #08090B (grafite quase preto), linhas #F4F6F7 (branco quente), corredor #D8FF3E (lima ácido). Nenhuma outra cor.
- Público: corredor iniciante/intermediário que quer saber o que fazer hoje e por quê.

A IDEIA EM UMA FRASE:
Um corredor em perfil corre sobre o topo de um M que é, ao mesmo tempo, uma rota de GPS; no início da rota, um cronômetro simples marca a partida. O corredor é o herói; o M é o chão; o cronômetro é a origem.

COMPOSIÇÃO (siga esta ordem):
1. FUNDO: quadrado 1:1, 1024×1024, preenchido por completo com #08090B. Sem moldura, sem margem branca, sem cantos arredondados (o launcher Android aplica a máscara).
2. M / ROTA: um traço branco contínuo, grosso e uniforme, com pontas e juntas arredondadas. Ele nasce no cronômetro, no canto inferior esquerdo, sobe até um pico arredondado à esquerda, desce até um vale no centro, sobe até o topo direito — que é um platô curto e levemente arredondado, como uma curva de rota — e desce até uma ponta arredondada no canto inferior direito. O M deve parecer uma rota de GPS, não uma letra de fonte. As pernas ficam quase verticais; nada de M aberto como montanha ou tenda.
3. CRONÔMETRO: no início da rota, encostado na base esquerda do M, um aro branco simples com uma coroa pequena e arredondada no topo. O mostrador é vazio: sem ponteiros, sem números, sem marcas, sem vidro. A linha da rota sai do lado direito do aro. O aro tem cerca de 16–18% do lado do ícone e um traço mais fino que o da rota; ele precisa ler como cronômetro, não como lupa, pino de mapa ou flor.
4. CORREDOR: em perfil, correndo para a direita, sobre o platô do topo direito do M. Uma perna de apoio toca a linha da rota; a outra perna fica no ar, dobrada para trás. Um braço vai à frente, o outro atrás. O tronco se inclina levemente para a frente. É um pictograma sólido, com 5–6 massas, sem rosto, sem roupa, sem tênis detalhado, sem músculos. A cabeça tem cerca de 1/5 da altura do corpo. Os membros são grossos o suficiente para sobreviver a 48 px. O corredor é o único elemento lima do ícone.
5. EQUILÍBRIO: composição diagonal — cronômetro embaixo à esquerda, corredor em cima à direita, rota conectando os dois. Nada toca as bordas: mantenha 12–15% de respiro. Centralize o conjunto opticamente, não matematicamente.

O QUE O ÍCONE NÃO PODE TER:
- Pingo, nó, bolinha ou ponto amarelo no meio do M. O único círculo isolado é o cronômetro; a cabeça do corredor faz parte da silhueta.
- M aberto como montanha, tenda ou "W"; pernas splayed.
- Corredor realista, musculoso, com rosto, cabelo, camiseta, shorts ou tênis detalhado. Nada de boneco de cabeça gigante.
- Corredor flutuando, sentado, saltando ou em cima do cronômetro. Ele corre no platô do M, com um pé na linha.
- Cronômetro detalhado com ponteiros, números, marcas ou mostrador cheio. Apenas aro + coroa.
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
1. A 48 px, o M e o corredor continuam legíveis? O corredor não pode virar um borrão verde.
2. A 48 px, o cronômetro simplifica para um aro com coroa, sem virar lupa ou pino?
3. Em uma cor (silhueta preta), o corredor ainda se distingue pela pose e pelo espaço ao redor?
4. Em escala de cinza, o corredor continua visível?
5. A 512 px, o cronômetro lê como cronômetro?
6. O M lê como M e como rota ao mesmo tempo, sem parecer montanha?
7. O ícone parece um coach premium e lúdico, não um app de academia ou um timer genérico?
Se falhar em 1, 2 ou 6, simplifique: engrosse o traço, diminua a cabeça do corredor, aumente o platô e reduza o cronômetro.

ENTREGA:
- Uma única imagem quadrada 1024×1024, plana, centralizada, sem moldura e sem mockup.
- Não inclua texto, assinatura ou explicação na imagem.
- Depois da imagem, escreva em texto (fora da imagem) um rationale de 3–5 linhas explicando a leitura: cronômetro → rota/M → corredor.
```

---

## 4. Correções rápidas (cole depois do resultado)

- **O M virou montanha/tenda:** `Verticalize as pernas do M. Reduza a abertura para no máximo 5°. O M é uma rota precisa, não uma montanha.`
- **O corredor virou um borrão verde:** `Aumente o corredor para 24–26% da altura, diminua a cabeça para 1/5 do corpo, engrosse os membros e simplifique a pose: uma perna no chão, outra no ar, um braço à frente, outro atrás.`
- **O corredor está flutuando:** `Coloque o pé de apoio exatamente sobre a linha do platô. O outro pé fica no ar, não precisa tocar a linha.`
- **O corredor está sentado/saltando:** `Corrija para uma passada de corrida: tronco inclinado à frente, perna de trás estendida, joelho da frente elevado.`
- **O cronômetro virou lupa/pino:** `Adicione uma coroa retangular arredondada no topo do aro e remova qualquer cabo, alça ou ponta. O mostrador fica vazio. O aro é mais fino que a rota.`
- **O cronômetro está grande demais:** `Reduza o cronômetro para 16% do lado e mantenha o corredor como o segundo elemento mais importante.`
- **O platô virou uma mesa:** `Suavize o platô: ele é uma curva de rota levemente achatada, não uma plataforma reta. O corredor precisa de apoio, mas o M não pode parecer uma mesa.`
- **Ficou uma cena/paisagem:** `Remova chão, cenário, sombra e perspectiva. Os únicos elementos são o M/rota, o cronômetro e o corredor.`
- **Ficou cartoon/mascote:** `Remova rosto, olhos, roupa, tênis e efeitos. O corredor é um pictograma geométrico, não um personagem.`
- **Apareceu pingo no meio do M:** `Remova qualquer ponto, nó ou bolinha no meio do M. O único círculo isolado é o cronômetro, no início da rota; a cabeça do corredor é parte do corpo.`
- **O M ficou uma letra de fonte:** `Desenhe o M como um traço de rota: juntas mais arredondadas, platô no topo direito e pontas de rota. Não use um M tipográfico.`
- **Apareceu texto/mockup:** `Remova todo texto, moldura, celular e apresentação. Entregue apenas o ícone quadrado.`

---

## 5. Variações controladas (peça uma por vez)

| Variação | O que muda | Quando usar |
|---|---|---|
| **A (principal)** | Cronômetro na partida (baixo à esquerda), corredor no platô direito. | Primeira tentativa; história clara. |
| **B** | Cronômetro na chegada (baixo à direita), corredor no pico esquerdo. | Se a versão A ficar pesada à esquerda. |
| **C** | Cronômetro menor, quase um aro de partida; corredor maior. | Se o cronômetro estiver competindo com o corredor. |
| **D** | Corredor no pico esquerdo, cronômetro no centro da base. | Só se o M continuar legível; cuidado para não virar "pingo". |

Para cada variação, mantenha: fundo #08090B, M/rota branco, corredor lima, um único accent, sem texto e sem mockup.

---

## 6. Nota técnica para depois

- Gere em **1024×1024 PNG**, não JPG. O JPG cria halos nas bordas e atrapalha a vetorização.
- Não peça ao Gemini para "fazer o SVG"; a imagem é conceito. Com o PNG aprovado, a geometria final é reconstruída em SVG com traço uniforme, hex exatos e máscara do cronômetro.
- Se o Gemini entregar o corredor como borrão, não tente "consertar" no SVG: primeiro acerte a pose e o tamanho na imagem. O SVG não inventa leitura.
- Para o launcher, o corredor precisa de uma variante óptica: a 24–32 px, aumente o corredor 10–15% e simplifique o cronômetro para aro + coroa.
