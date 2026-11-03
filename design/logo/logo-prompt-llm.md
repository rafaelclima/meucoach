# Prompt para LLM (ChatGPT / Gemini) — Logo MeuCoach

> **Nota:** este arquivo é autocontido. Não faz referência a arquivos internos do projeto. Pode ser copiado diretamente para o chat da LLM.

---

## Prompt principal (copiar e colar no chat)

```
Crie uma logo minimalista para um app de corrida chamado "MeuCoach".

ESTILO E CONCEITO:
- Minimalista, escuro e preciso (não vibrante, não corporativo).
- O app é um instrumento pessoal de treino — silencioso, denso na medida certa.
- A marca deve comunicar movimento, rota e progresso, sem ser genérica.

CORES (exatas, hex):
- Fundo / contexto: #08090B (quase preto, grafite profundo)
- Accent único: #D8FF3E (lima ácida, o único destaque de cor)
- Texto / linhas principais: #F4F6F7 (quase branco, limpo)
- NÃO use coral (#FF6B5E), lavanda, ciano, azul ou outras cores. Apenas lima + branco + preto.

SÍMBOLO (parte gráfica):
- Uma linha estilizada que represente uma rota de corrida (como um traçado GPS simplificado).
- A linha deve ser fina (stroke 2-3 px), com cantos arredondados suaves.
- No início da rota: um pequeno ponto/círculo sólido (#D8FF3E) — representa o local de partida (como um corredor começando o treino).
- No final da rota: um ponto menor (#F4F6F7) — representa a chegada.
- A linha deve ter uma leve curva natural (não reta), sugerindo movimento real.
- O símbolo deve funcionar sobre fundo escuro (#08090B) e também sobre mapas coloridos (como mapas de OpenStreetMap), então precisa de contraste forte.

TIPOGRAFIA (integrada ao símbolo ou abaixo/ao lado):
- Nome: "MEUCOACH"
- Fonte: Geometrica, sans-serif, peso médio-alto (equivalente a Geist 600 ou Inter 600).
- Estilo: letras maiúsculas, tracking apertado (letras próximas), sem itálico exagerado.
- Cor do texto: #F4F6F7 (quase branco) com possível destaque em #D8FF3E apenas no "MEU" ou no ponto/acento.
- Evite sombras, gradientes ou efeitos 3D.

COMPOSIÇÃO:
- Opção principal: símbolo (linha de rota) à esquerda + "MEUCOACH" à direita, alinhados verticalmente no centro.
- Alternativa: símbolo acima + "MEUCOACH" abaixo, centralizado (para versão quadrada/icon).
- A logo deve ser legível em tamanho pequeno (ex.: 32px de altura, como no rodapé de um vídeo vertical 9:16).

FORMATO DE ENTREGA (descreva o resultado visual):
- Fundo transparente (sem quadrado branco ao redor).
- Resolução alta, vetorial (SVG mental) ou pixel perfeitio.
- Sem molduras, sem badges, sem círculos de fundo.

REFERÊNCIAS VISUAIS PARA INSPIRAÇÃO (não para copiar):
- Strava (mapa + rota), mas com estética totalmente diferente: menos colorido, mais escuro, mais minimalista.
- Nike Run Club (tipografia grande e movimento).
- Evite: logotipos de academia com músculos, raios, corações ou designs muito coloridos.

EXEMPLOS DO QUE QUERO (descreva visualmente):
- Uma linha curva fina, quase um arco sutil, com um ponto luminoso (#D8FF3E) no início e um ponto discreto no final.
- O texto "MEUCOACH" ao lado ou abaixo, limpo, geométrico, sem serifas.
- O conjunto parece um instrumento de precisão, não um brinquedo.
```

---

## Prompt alternativo — se a LLM gerar apenas o símbolo (sem texto)

```
Gere apenas o símbolo gráfico (ícone) para a marca "MeuCoach" (app de corrida).
- Uma linha estilizada de rota (GPS) com um ponto de partida (#D8FF3E) e um ponto de chegada (#F4F6F7).
- Linha fina, cantos arredondados, sem preenchimento (stroke apenas).
- Fundo transparente, estilo minimalista, escuro.
- Não inclua texto. Apenas o símbolo.
```

---

## Prompt para versão com logo + nome separados (para edição posterior)

```
Crie dois arquivos visuais para a logo "MeuCoach":
1. Símbolo: uma linha de rota estilizada (como descrito acima), fundo transparente, apenas o ícone, sem texto.
2. Tipografia: o nome "MEUCOACH" em letras maiúsculas, fonte geométrica (Geist/Inter), cor #F4F6F7, fundo transparente, sem símbolo.
Ambos devem combinar quando colocados lado a lado (símbolo à esquerda, texto à direita).
```

---

## Dicas para obter melhor resultado

- **ChatGPT (DALL-E 3):** use o prompt principal. Se o resultado vier muito colorido, adicione ao final: `Reforço: apenas as cores #D8FF3E, #F4F6F7 e #08090B. Nada de vermelho, azul, roxo ou gradientes.`
- **Gemini:** o prompt funciona diretamente. Se precisar de ajuste, peça: `Torne a linha mais fina, o texto mais geométrico e remova qualquer sombra.`
- **Se a LLM não respeitar o fundo transparente:** peça explicitamente: `Fundo transparente, sem quadrado branco ao redor, apenas a logo.`
- **Se o símbolo ficar muito abstrato:** peça: `Aumente a referência à rota: adicione um ponto de partida luminoso (#D8FF3E) e um ponto de chegada discreto. A linha deve parecer um traçado real de corrida.`

---

## Como usar o resultado no projeto

1. Salve o arquivo gerado como `design/logo/meucoach_logo.png` (fundo transparente) e `meucoach_logo.svg` (vetor).
2. Se a LLM não entregou `SVG`, converta o `PNG` para `SVG` via `Inkscape`, `Figma` ou `Vectorizer.AI`.
3. Coloque o arquivo em `app/src/main/res/drawable-xxxhdpi/logo_meucoach.png` para uso no `StoryFrameRenderer`.
4. O `logoBitmap` é opcional: se o arquivo não existir, o `StoryFrameRenderer` desenha apenas o texto `"MeuCoach"` (`ADR-0015` §2.8).
