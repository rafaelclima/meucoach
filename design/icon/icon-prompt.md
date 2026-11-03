# Prompt para Gemini — Ícone do app MeuCoach (launcher / Play Store)

> Copie e cole diretamente no chat do Gemini (`gemini-2.5-pro-image-preview` ou `gemini-pro-vision`). Não faz referência a arquivos internos.

---

```
Crie um ícone de aplicativo (app icon / launcher icon) para um app Android chamado "MeuCoach".

FORMATO E DIMENSÕES:
- Quadrado, proporção 1:1.
- Resolução alta: 512x512 pixels (padrão Play Store) e 1024x1024 (para qualidade máxima).
- Bordas arredondadas suaves (como os adaptive icons do Android) — raio de canto aproximadamente 20-25% da largura.
- Fundo sólido (não transparente), porque é um ícone de launcher.
- Nenhum texto, nenhuma palavra, nenhuma letra. Apenas símbolo gráfico.

ESTILO VISUAL (exato):
- Minimalista, escuro e preciso. Nada de efeitos 3D, sombras pesadas, gradientes ou brilhos exagerados.
- O app é um instrumento pessoal de treino de corrida — silencioso, denso, não vibrante.
- Design "dark-first": o fundo deve ser escuro, quase preto.

CORES (exatas, sem variação):
- Fundo do ícone: #08090B (grafite profundo, quase preto).
- Símbolo / accent: #D8FF3E (lima ácida, o único destaque de cor — deve ser o ponto de maior contraste).
- Se precisar de contraste secundário (ex.: ponto final sutil): #F4F6F7 (quase branco, muito discreto).
- NÃO use: #FF6B5E (coral), roxo, azul, laranja, vermelho, verde escuro, ou qualquer outra cor além de lima + branco + preto.

SÍMBOLO (parte gráfica central):
- Uma linha estilizada que represente uma rota de corrida (como um traçado GPS simplificado).
- A linha deve ser fina (stroke 2-3 px em relação à escala), com cantos arredondados suaves.
- Forma: uma leve curva natural (não reta, não círculo perfeito) — sugere movimento real de um treino ao ar livre.
- No início da rota (parte inferior esquerda ou centro inferior): um pequeno círculo sólido (#D8FF3E) — representa o ponto de partida.
- No final da rota (parte superior direita ou centro superior): um ponto muito pequeno (#F4F6F7) — representa a chegada, quase invisível, apenas para fechar a composição.
- O símbolo deve ocupar aproximadamente 50-60% da área central do quadrado, com espaço vazio ao redor (padding visual de ~20-25%).
- O símbolo deve ser legível em tamanho muito pequeno (ex.: 48x48 dp no launcher do Android).

REFERÊNCIA VISUAL (o que NÃO é):
- NÃO é uma academia (sem músculos, sem halteres).
- NÃO é uma marca de moda ou lifestyle (sem letras cursivas, sem fontes decorativas).
- NÃO é um app de mapas genérico (o símbolo deve ser uma rota estilizada, não um mapa completo com ruas e nomes).
- NÃO é colorido como apps sociais (sem arco-íris, sem gradientes de cor).

COMPOSIÇÃO DO ÍCONE:
- Quadrado escuro (#08090B) com bordas arredondadas.
- No centro: o símbolo da rota (linha + ponto lima + ponto discreto branco).
- Nenhum texto, nenhum nome, nenhuma data, nenhuma borda decorativa extra.
- O símbolo deve parecer flutuante no centro, com equilíbrio visual.

ESTILO ADAPTATIVO (Android Adaptive Icon):
- Se possível, sugira como o símbolo seria cortado pelo formato do launcher (círculo, quadrado arredondado, etc.). O símbolo deve funcionar em todos esses formatos sem perder o ponto de partida.

USO FINAL:
- Launcher do Android (ícone na área de trabalho).
- Play Store (lista de apps, página de detalhes).
- Compartilhamento do story (`P3-05`) — quando o usuário compartilha o vídeo, o app precisa de um ícone reconhecível.

EXEMPLO DO QUE QUERO:
- Um quadrado escuro, quase preto, com uma linha fina e elegante que faz uma leve curva do canto inferior esquerdo até o canto superior direito.
- No início da curva: um ponto luminoso de cor lima (#D8FF3E).
- No final: um ponto quase invisível branco (#F4F6F7).
- Nada mais. Minimalista, escuro, preciso.
```

---

## Dicas para obter melhor resultado no Gemini

- Se o resultado vier com texto: `Remova qualquer texto ou letra. Apenas o símbolo gráfico.`
- Se o fundo não for escuro: `O fundo deve ser #08090B (quase preto), não branco nem transparente.`
- Se a linha ficar muito grossa: `Afine a linha para stroke fino (2-3 px), estilo minimalista.`
- Se aparecerem cores extras: `Use apenas #D8FF3E (lima), #F4F6F7 (branco) e #08090B (preto). Nenhuma outra cor.`
- Se o símbolo não parecer uma rota: `Ajuste para que a linha seja uma curva natural de rota, não um círculo nem uma linha reta perfeita.`
- Se precisar de variações: `Gere 3 versões: (1) quadrado com bordas arredondadas, (2) versão circular (como seria no launcher circular), (3) versão quadrada sem bordas arredondadas.`

---

## Como usar no projeto

1. Salve como `design/icon/meucoach_icon.png` e `meucoach_icon.svg`.
2. Para o launcher Android (`adaptive icon`): crie `ic_launcher_foreground.xml` (vetor) ou `ic_launcher.png` (`drawable-xxxhdpi`) com o símbolo centralizado.
3. Se a versão gerada for `PNG`, converta para `SVG` via `Inkscape` ou `Figma` para uso no código (`adaptive-icon`).
4. Se o ícone precisar ser adaptativo (`API 26+`), o símbolo deve ser desenhado em uma camada (`foreground`) sobre um fundo (`background` `#08090B`).
