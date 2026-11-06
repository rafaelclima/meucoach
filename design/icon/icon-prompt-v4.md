# Prompt v4 — Ícone do app MeuCoach (direção "M-Percurso")

> **Substituído:** a direção do M com nó lima foi rejeitada. Use `icon-prompt-v5.md` (corredor no M + cronômetro).

> **Status:** proposta de direção. Substitui a exploração literal de "corredor + rota" das versões 1–3.
> O prompt abaixo é autocontido: pode ser copiado direto para ChatGPT, Gemini ou outra LLM com geração de imagem.

---

## 1. Por que o ícone atual não está no nível do resto

- **Ilustra em vez de marcar.** Corredor + arco + losango é uma cena, não um símbolo. Em 48 dp, a anatomia e o traço fino viram ruído.
- **Fala o código genérico da categoria.** Silhueta humana + trajetória diz "tracker de corrida", não "o coach que decide e explica".
- **Tem três protagonistas.** Corpo branco, arco lima e losango lima competem; não há um foco.
- **Contradiz o sistema.** A marca é "instrumento de precisão"; o ícone é uma ilustração emotiva. O logo é uma rota; o ícone é um corredor — duas metáforas para a mesma marca.
- **Não sobrevive à escala.** O traço de 2–3 px some a 48 dp. E o layer `monochrome` atual reaproveita o foreground colorido, então o ícone temático do Android vira uma silhueta tingida do corredor + arco, sem a hierarquia planejada.

O salto de patamar não é "desenhar um corredor melhor". É trocar ilustração por **marca**.

---

## 2. A direção: "M-Percurso"

**Uma frase:** um **M** construído como um **percurso**; no vértice central, um único **nó lima** — o ponto onde o coach decide o próximo passo.

- À primeira vista: a inicial de **MeuCoach**.
- À segunda: um **traço de rota** com uma parada precisa.
- Não é corredor, não é mapa, não é medidor. É uma marca.

**Por que funciona:**

| Critério | Como o M-Percurso resolve |
|---|---|
| Recall de marca | Monograma é lido em 24 px e sobrevive às máscaras comuns, se respeitar o safe zone. |
| Categoria | O traço contínuo, as juntas arredondadas e o nó leem como percurso. |
| Diferencial | O nó lima é o "coach" no ponto de decisão — não é só mais uma rota. |
| Sistema | Grafite + branco quente + um único lima; o M branco ecoa o "MEU" do wordmark e o nó lima ecoa o "COACH". |
| Escala | Uma forma, um accent, sem detalhe fino. Funciona de 24 px a 512 px. |
| Acessibilidade | O nó se separa do traço pelo **vazio**, não só pela cor; funciona em escala de cinza. |

O M é o ponto de partida, não uma letra de fonte. O desenho deve parecer um percurso que, visto de longe, lembra um M — e não um "M" digitado em Geist.

---

## 3. Prompt principal (copiar e colar)

```text
Você é um diretor de design de identidade visual especializado em ícones de aplicativos. Crie o ícone de launcher do MeuCoach, um app Android de coach de corrida. O nível de acabamento deve ser o de um ícone de app premiado — não o de um ícone genérico gerado por IA.

CONTEXTO DA MARCA (leia antes de desenhar; não desenhe este texto):
- MeuCoach é um coach de corrida pessoal, local-first e baseado em evidências. Ele importa treinos, calcula carga e prontidão, prescreve o próximo treino e explica o porquê.
- A promessa é "Precisão que orienta". O app é um instrumento pessoal de treino: escuro, silencioso, denso na medida certa, com um único gesto de cor que significa "faça isto".
- O logo atual é uma rota fina com um ponto lima. A linguagem da marca é "traço + nó", não "corredor + arco". Use essa linguagem, mas não copie o desenho do logo.
- Estética: grafite profundo (#08090B), branco quente (#F4F6F7) e lima ácido (#D8FF3E). Nada de coral, azul, roxo, verde, laranja, dourado, prata, gradiente, neon, vidro, metal ou 3D.
- Público: corredor iniciante/intermediário que quer saber o que fazer hoje e por quê — não um atleta de feed social, não uma academia.

A IDEIA (não negocie a ideia; negocie só a execução):
Um símbolo que é, ao mesmo tempo, a inicial M de MeuCoach e um percurso de corrida. O M é desenhado como um traço contínuo, grosso e preciso, com juntas arredondadas. No vértice central do M — o ponto mais baixo do percurso — há um único nó circular lima, sólido, separado do traço por um fino respiro na cor do fundo. O nó é o coach: o ponto exato onde o caminho encontra a decisão. O resto é silêncio.

O QUE O ÍCONE DEVE SER:
- Um símbolo gráfico plano, vetorial, de leitura imediata.
- Um M não tipográfico: desenhado como percurso, com proporções próprias, sem serifa, sem parecer uma letra de fonte.
- Um traço de espessura uniforme, pontas e juntas arredondadas, sem afinamento, sem pincel, sem caligrafia.
- Uma composição assimétrica: o M pode inclinar 4–6° para a direita (movimento) e a perna direita pode ser ligeiramente mais curta/alta que a esquerda; não exagere a ponto de parecer torto.
- Um único nó lima no vértice central. Ele deve ser o único elemento colorido do ícone.
- Fundo grafite pleno, sem textura, sem vinheta, sem gradiente.

O QUE O ÍCONE NÃO DEVE SER (evite estes clichês e sinais de IA):
- Não desenhe corredor, silhueta humana, cabeça, braços, pernas, tênis, pegada, pista, cronômetro, mapa, pino de mapa, bússola, alvo, coração, raio, chama, haltere, troféu, pódio, medalha, laurel, engrenagem, gráfico, anel de medidor ou fita de chegada.
- Não use letras além do M desenhado; não escreva "MEUCOACH", "MC", "COACH" ou qualquer palavra/número.
- Não use mais de uma cor de destaque; não use gradiente, brilho, neon, sombra, relevo, bisel, reflexo, vidro, metal, plástico, 3D ou textura.
- Não use mockup de celular, mão, mesa, cartão, moldura, apresentação, grade, colagem ou múltiplos ícones. Uma única imagem quadrada, só o ícone.
- Não use cantos arredondados no desenho: o quadrado é full-bleed; o arredondamento é aplicado pelo launcher Android.
- Não use traço fino, detalhes pequenos ou elementos que desapareçam a 24 px.
- Não use estilo "app de academia", "app de moda", "app financeiro" ou "app de mapa".

GEOMETRIA E COMPOSIÇÃO (guia, não dogma):
- Tela quadrada 1024×1024. Fundo #08090B em 100% da área.
- O M ocupa cerca de 55% da largura e 50% da altura, com pelo menos 22% de respiro em todos os lados.
- Traço com espessura entre 9% e 10% do lado (aprox. 92–102 px em 1024). Uniforme.
- O M tem quatro segmentos: sobe da base esquerda até o topo esquerdo, desce até o vértice central, sobe até o topo direito, desce até a base direita. O vértice central fica ligeiramente abaixo do centro óptico.
- O nó fica exatamente no vértice central, com diâmetro de 1,5–1,7× a espessura do traço. Um respiro radial de cerca de 0,13× o diâmetro do nó, na cor do fundo, separa o nó do traço. O nó não encosta no traço.
- Centralize o conjunto opticamente, não matematicamente: o peso do M e do nó deve parecer equilibrado; ajuste alguns pixels se necessário.
- O nó deve continuar visível em escala de cinza e a uma cor, pelo vazio ao redor — não dependa só do contraste de cor.

COR (exata):
- Fundo: #08090B (grafite quase preto).
- Traço do M: #F4F6F7 (branco quente).
- Nó: #D8FF3E (lima ácido).
- Nenhuma outra cor. Sem transparências, sem opacidade, sem gradiente.
- Se o gerador não respeitar hex exato, use o mais próximo e informe; não invente uma paleta.

ACABAMENTO:
- Cor plana, bordas nítidas, sem ruído, sem grão, sem textura.
- Sem sombra, sem brilho, sem reflexo, sem profundidade.
- A imagem deve parecer um arquivo vetorial exportado, não uma pintura ou um render 3D.

TESTES DE QUALIDADE (faça antes de finalizar):
1. Teste de 24 px: o M ainda é reconhecível? O nó ainda é um ponto, não uma mancha?
2. Teste de uma cor: em preto puro sobre branco, o símbolo ainda funciona como silhueta? O nó ainda se separa pelo vazio?
3. Teste de escala de cinza: o nó não desaparece?
4. Teste de vizinhança: ao lado de ícones de corrida conhecidos, ele parece uma marca de produto premium ou mais um app de tracker?
5. Teste de silhueta: preencha o símbolo de preto e veja se a forma é memorável e não parece uma letra de fonte genérica.
6. Teste de respiro: o ícone respira ou está apertado?
Se falhar em 1, 2 ou 5, simplifique: menos detalhe, mais espessura, mais respiro.

ENTREGA:
- Uma única imagem quadrada 1024×1024, o ícone centralizado, sem moldura e sem mockup.
- Não inclua texto, assinatura, marca d'água ou explicação na imagem.
- Depois da imagem, escreva em texto (fora da imagem) um rationale de 3–5 linhas explicando como o símbolo cumpre "M + percurso + nó".
```

---

## 4. Protocolo de iteração (não peça "mais opções" soltas)

Peça **uma variação por vez**, mudando só um eixo. Isso evita que o modelo "conserte" o conceito e entregue outra coisa.

| Eixo | Valores | O que observar |
|---|---|---|
| Inclinação | 0°, 4°, 6° | 0° é mais instrumento; 4–6° é mais movimento. |
| Peso do traço | 8%, 9%, 10% | 10% é mais legível a 24 px; 8% é mais elegante. |
| Proporção do M | 50%, 55%, 60% da largura | 60% pode ser cortado pela máscara circular. |
| Nó | círculo, losango arredondado, quadrado arredondado | O círculo é o mais universal; o losango é mais "waypoint". |
| Posição do nó | vértice central, topo direito, meio da perna direita | O vértice central é o mais estável e o mais "coach". |
| Assimetria | pernas iguais, perna direita 2–3% mais curta | A assimetria sutil evita cara de letra de fonte. |

**Ordem recomendada:**

1. Gere a versão base (0° ou 4°, traço 9,5%, nó no vértice).
2. Gere a mesma versão com o nó 15% maior para testar 24 px.
3. Gere a mesma versão em uma cor (silhueta).
4. Só então teste inclinação e proporção.
5. Escolha **uma** e peça o SVG de produção.

---

## 5. Prompt de correção (cole depois do resultado)

- **Parece uma letra de fonte:** `O M está tipográfico demais. Desenhe como percurso: aumente a espessura, arredonde mais as juntas, deixe as pernas ligeiramente assimétricas e remova qualquer serifa ou terminal de fonte.`
- **Parece um corredor/humano:** `Remova qualquer figura humana, cabeça, braço, perna ou tênis. Os únicos elementos são o M, o nó e o fundo.`
- **O nó parece uma bola:** `Reduza o nó para 1,5× a espessura do traço, aumente o respiro ao redor e encoste-o exatamente no vértice central do M.`
- **O nó desaparece a 48 px:** `Aumente o nó para 1,8× a espessura e o respiro para 0,2× o diâmetro. O nó precisa ler como um ponto separado, não como parte do traço.`
- **Ficou 3D/plástico:** `Achate tudo. Remova sombra, brilho, reflexo, bisel e profundidade. Use apenas cor plana.`
- **Apareceu texto:** `Remova todo texto, letra, número e assinatura. O único glifo é o M desenhado.`
- **Ficou desalinhado:** `Centralize o conjunto opticamente. O nó deve estar no vértice central e o respiro deve ser igual nos quatro lados.`
- **Ficou genérico:** `Torne o M mais autoral: perna direita 2–3% mais curta, juntas mais arredondadas, nó com respiro mais evidente. Evite um M de fonte.`
- **Ficou pequeno demais:** `Aumente o M para 55% da largura, mantendo pelo menos 20% de respiro.`
- **Ficou com gradiente/neon:** `Use somente #08090B, #F4F6F7 e #D8FF3E. Sem gradiente, sem glow, sem transparência.`

---

## 6. Bônus: prompt para o ChatGPT gerar o SVG de produção

Imagem gerada por IA é **conceito**. Para produção, peça o SVG. O ChatGPT é bom em código e devolve geometria exata, hex exato e arquivo escalável.

```text
Você é um designer de ícones e especialista em SVG. Gere o código SVG completo e limpo do ícone "M-Percurso" do MeuCoach, pronto para abrir no Figma/Inkscape e para virar adaptive icon do Android.

REGRAS:
- viewBox="0 0 1024 1024"; width="1024"; height="1024".
- Fundo (somente na versão colorida): <rect width="1024" height="1024" fill="#08090B"/>.
- O M é um <polyline> com points="280,740 280,330 512,590 744,330 744,740", fill="none", stroke="#F4F6F7", stroke-width="98", stroke-linecap="round", stroke-linejoin="round".
- O nó é um <circle cx="512" cy="590" r="76" fill="#D8FF3E"/>.
- Para criar o respiro ao redor do nó, use uma <mask id="node-gap" maskUnits="userSpaceOnUse" maskContentUnits="userSpaceOnUse"> com um <rect width="1024" height="1024" fill="#FFFFFF"/> e um <circle cx="512" cy="590" r="96" fill="#000000"/>. Aplique mask="url(#node-gap)" ao polyline; desenhe o nó depois.
- Não use <text>, fontes, filtros, gradientes, sombras, opacidade, blur ou clipPath desnecessário.
- Ordem: <defs> com a mask, retângulo de fundo, polyline com mask, círculo do nó.
- Inclua comentários curtos em português.
- Gere duas versões:
  1. Colorida: fundo #08090B, traço #F4F6F7, nó #D8FF3E.
  2. Monocromática: fundo transparente, traço e nó #FFFFFF, mesma mask (o respiro fica transparente). Essa versão será tintada pelo Android.
- Não arredonde os cantos do quadrado; o SVG é full-bleed.
- Verifique que o nó não encosta no traço e que o desenho está centralizado.
- Entregue apenas o código SVG, sem explicação antes ou depois, em dois blocos separados.
```

**Ajuste fino antes de exportar:**

- Se o M parecer pequeno no launcher, aumente o traço para 102 e o M para 58% da largura — mas teste a máscara circular.
- Se o nó parecer solto, reduza o respiro de 96 para 90.
- Se o nó parecer uma bola, reduza o raio de 76 para 70.
- Se quiser movimento, incline o grupo inteiro em `rotate(4 512 512)` (topo para a direita); mantenha o nó no vértice.

---

## 7. Checklist de exportação Android

### 7.1 Arquivos

| Uso | Especificação |
|---|---|
| Master | 1024×1024 PNG, sRGB, full-bleed, sem cantos arredondados. |
| Play Store | 512×512 PNG 32-bit, full-bleed. |
| Adaptive foreground | 108 dp @ densidade (432×432 em xxxhdpi), **transparente**, só o símbolo. |
| Adaptive background | Cor sólida `#08090B` (recurso `@color/ic_launcher_background`). |
| Monochrome | 108 dp, **transparente**, uma única cor (o sistema tinge). |
| Notification | 24 dp, branco puro sobre transparente. |
| Story/footer | versão monocromática branca, para fundos de vídeo. |

### 7.2 Regras que o asset atual quebra

- **Foreground transparente.** O `ic_launcher_foreground.png` atual já é transparente e deve continuar assim: o fundo é a camada `background`, não o foreground.
- **Monochrome não pode ser o foreground colorido.** Hoje `ic_launcher.xml` aponta `monochrome` para o mesmo `@mipmap/ic_launcher_foreground`. O sistema tinge a silhueta inteira (corredor + arco + losango), então o ícone temático perde a hierarquia e o nó. Crie `ic_launcher_monochrome` separado, transparente, com o M e o nó em uma única cor.
- **Sem cantos arredondados no arquivo.** O launcher aplica a máscara (círculo, squircle, quadrado arredondado, teardrop). O asset é quadrado.
- **Safe zone.** O foreground tem 108×108 dp; o conteúdo crítico deve caber no círculo central de ~66%. Teste as quatro máscaras e confirme que os topos e os pés do M não são cortados. Se forem, reduza o M ou suba a base.
- **Small-size variant.** A 24–32 px, o respiro pode fechar. Prepare uma variante com o nó 10–15% maior e o respiro mais evidente; use no notification icon e em contextos pequenos.

### 7.3 Estrutura esperada

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
```

### 7.4 Testes finais

- [ ] 1024, 512, 192, 144, 96, 72, 48 e 24 px.
- [ ] Máscara circular, squircle, quadrado arredondado e teardrop.
- [ ] Silhueta preta sobre branco.
- [ ] Escala de cinza: o nó continua visível pelo vazio.
- [ ] Tema claro do launcher: o ícone não some.
- [ ] Ícone temático do Android 13+: o monochrome está correto.
- [ ] Nenhum texto, sombra, gradiente ou canto arredondado no asset.
- [ ] O ícone parece um instrumento de precisão ao lado de Strava, Nike Run Club e Runna — e não uma variação deles.

---

## 8. Como julgar o resultado (rubrica rápida)

Dê nota de 1 a 5 para cada critério. Abaixo de 4 em qualquer um, itere.

1. **Ideia:** em uma frase, dá para explicar por que é MeuCoach e não outro app?
2. **Silhueta:** preenchido de preto, continua memorável?
3. **24 px:** o M e o nó continuam legíveis?
4. **Uma cor:** funciona sem o lima?
5. **Escala de cinza:** o nó se separa pelo vazio?
6. **Sistema:** parece o mesmo universo do logo, dos gráficos e dos cards?
7. **Categoria:** parece um coach premium, não um tracker genérico?
8. **Acabamento:** parece vetor desenhado, não render de IA?

---

## 9. O que não fazer

- Não volte para a silhueta de corredor, nem como "detalhe dentro do M".
- Não coloque um segundo accent (coral, azul, lavanda) para "dar vida".
- Não adicione gradiente, glow, sombra ou 3D para parecer "premium". Premium aqui é silêncio, precisão e respiro.
- Não use uma fonte para desenhar o M. O M é um desenho, não tipografia.
- Não peça ao modelo uma folha com 20 ícones. Ele vai entregar 20 conceitos rasos. Peça uma direção por vez.
- Não aprove o primeiro resultado por ser "bonito". Aprove quando passar nos testes de 24 px, uma cor e silhueta.
