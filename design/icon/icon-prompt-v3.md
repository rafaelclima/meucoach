# Prompt atualizado — Ícone do app MeuCoach (launcher / Play Store) v3

> **Nova direção:** silhueta estilizada de um corredor com uma rota saindo dos seus pés. Representa que o app é um **coach de corrida** (não só tracking) — o corredor é o usuário, a rota é o treino guiado.

---

```
Crie um ícone de aplicativo (app icon / launcher icon) para um app Android chamado "MeuCoach".

CONCEITO (nova direção — leia com atenção):
- Uma silhueta estilizada e minimalista de um corredor correndo (não realista, não detalhada — apenas a forma essencial: corpo inclinado para frente, uma perna à frente, outra atrás, braços em movimento).
- A silhueta está posicionada na parte inferior-esquerda ou inferior do quadrado, inclinada levemente para a direita (como se estivesse correndo para frente).
- Dos pés do corredor (especialmente do pé da frente) sai uma linha fina e elegante — uma rota de corrida — que se estende para a frente e para a direita, fazendo uma leve curva ascendente (como se fosse para um destino/objetivo).
- No meio dessa rota (não na ponta, não no início), há um pequeno ponto ou losango sólido (#D8FF3E) — representa o momento atual do treino sob orientação do coach. É o "coração" do ícone.
- No final da rota (parte superior-direita): um ponto discreto (#F4F6F7), quase invisível, apenas para fechar o caminho.
- A mensagem visual é: "um corredor guiado por uma rota precisa, com orientação no meio do caminho". Não é só um GPS. É treino orientado.

FORMATO E DIMENSÕES:
- Quadrado 1:1, alta resolução (512x512 e 1024x1024).
- Bordas arredondadas suaves (adaptive icon Android, raio ~20-25%).
- Fundo sólido ESCURO: #08090B (quase preto, grafite profundo). NÃO transparente.
- Nenhum texto, nenhuma palavra, nenhuma letra. Apenas símbolo gráfico.

CORES (exatas, sem exceção):
- Fundo: #08090B
- Silhueta do corredor: #F4F6F7 (quase branco, discreto, mas legível) OU #D8FF3E (se quiser destacar mais — mas prefira branco para o corpo, para não competir com o ponto da rota).
- Rota (linha): #D8FF3E (lima ácida, fina, elegante — stroke 2-3 px).
- Ponto central (orientação/coach): #D8FF3E (lima, sólido, pequeno quadrado ou losango — é o elemento de maior contraste e destaque).
- Ponto final: #F4F6F7 (branco muito discreto, quase um ponto de luz).
- NÃO use coral, roxo, azul, verde escuro, vermelho, laranja, gradientes ou qualquer outra cor além das três acima.

COMPOSIÇÃO:
- Quadrado escuro (#08090B) com bordas arredondadas.
- Silhueta do corredor na parte inferior (cerca de 30-40% da altura do quadrado), inclinada para a frente.
- A rota sai dos pés do corredor e sobe/avança para a direita, ocupando cerca de 50% da área central-superior.
- O ponto central (#D8FF3E) está no meio dessa rota — visível, mas não exagerado.
- O símbolo deve ocupar ~60-70% da área central, com espaço vazio ao redor (padding ~15-20%).
- A silhueta deve ser simples: linhas finas, sem detalhes de rosto, sem roupas detalhadas, sem sombras. Estilo outline ou preenchimento sólido discreto.
- O conjunto deve parecer: "um atleta em movimento, guiado com precisão" — minimalista, escuro, preciso.

ESTILO ADAPTATIVO:
- O símbolo deve funcionar quando cortado pelo formato do launcher (círculo, quadrado arredondado, etc.). A silhueta e a rota devem permanecer legíveis.
- Se o launcher for circular, o corredor deve ficar visível na parte inferior e a rota deve subir para o centro-direito.

REFERÊNCIA DO QUE NÃO É:
- NÃO é apenas uma linha de GPS (sem contexto humano).
- NÃO é um mapa completo (sem ruas, sem nomes, sem topografia).
- NÃO é uma figura de academia (sem músculos exagerados, sem halteres).
- NÃO é uma silhueta realista ou fotográfica — é estilizada, geométrica, minimalista.
- NÃO é uma marca social ou de moda (sem fontes decorativas, sem cores extras).

O QUE QUERO VISUALMENTE:
- Um quadrado escuro, quase preto.
- Um corredor estilizado, simples, na parte inferior, correndo para a direita.
- Dos seus pés, uma rota fina (#D8FF3E) que sobe e avança.
- No meio dessa rota: um ponto sólido (#D8FF3E) que representa a orientação do app.
- No final da rota: um ponto discreto (#F4F6F7).
- Nenhum texto. Minimalista. Escuro. Preciso.
```

---

## Instruções de correção se o Gemini errar

- Se aparecer texto: `Remova qualquer texto ou letra. Apenas símbolo gráfico.`
- Se o fundo não for #08090B: `O fundo deve ser #08090B, sólido, escuro.`
- Se aparecerem cores extras: `Use apenas #D8FF3E, #F4F6F7 e #08090B.`
- Se o corredor ficar muito detalhado ou realista: `Simplifique a silhueta para linhas finas e formas geométricas — sem detalhes de rosto, sem roupas detalhadas.`
- Se a rota ficar muito grossa ou decorativa: `Afine a rota para stroke 2-3 px, elegante, minimalista.`
- Se o ponto central não aparecer: `Adicione um pequeno quadrado ou losango #D8FF3E no meio da rota — é essencial para comunicar a orientação do coach.`
- Se precisar de variações: `Gere 3 versões: (1) quadrado com bordas arredondadas, (2) circular (launcher circular), (3) com o corredor um pouco maior ou menor.`

---

## Como usar no projeto

- Salve como `design/icon/meucoach_icon_v3.png` e `meucoach_icon_v3.svg`.
- Para launcher Android (`adaptive icon`): use o símbolo como `foreground` (`ic_launcher_foreground.xml`) sobre `background` `#08090B`.
- Se o Gemini gerar `PNG`, converta para `SVG` via `Inkscape` ou `Figma` para uso no código.
