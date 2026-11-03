# Prompt melhorado — Ícone do app MeuCoach (launcher / Play Store) v2

> Este prompt substitui a versão anterior (`icon-prompt.md`). Agora reflete melhor que o app é um **coach de corrida** (prescreve treino, calcula carga, explica o porquê, monitora prontidão) — não apenas um tracker de GPS.

---

## Prompt para colar diretamente no Gemini (`gemini-2.5-pro-image-preview` ou `gemini-pro-vision`)

```
Crie um ícone de aplicativo (app icon / launcher icon) para um app Android chamado "MeuCoach".

O QUE O APP REALMENTE FAZ (contexto visual):
- É um coach de corrida pessoal, não apenas um rastreador de GPS.
- Importa treinos (FIT, TCX, GPX), calcula carga (TRIMP, ACWR), monitora prontidão (check-in diário), prescreve o próximo treino e explica o porquê com evidências científicas.
- O usuário é um corredor iniciante/intermediário que precisa de orientação, não só de registro.
- A marca é minimalista, escura, precisa — como um instrumento de treino, não como um app social ou de moda.

FORMATO E DIMENSÕES:
- Quadrado 1:1, alta resolução (512x512 e 1024x1024).
- Bordas arredondadas suaves (adaptive icon Android, raio ~20-25% da largura).
- Fundo sólido ESCURO: #08090B (quase preto, grafite profundo). NÃO transparente.
- Nenhum texto, nenhuma palavra, nenhuma letra. Apenas símbolo gráfico.

CORES (exatas, sem exceção):
- Fundo: #08090B
- Accent principal (o que chama atenção): #D8FF3E (lima ácida)
- Detalhe secundário (muito sutil): #F4F6F7 (quase branco, discreto)
- NÃO use coral, roxo, azul, verde escuro, vermelho, laranja, gradientes ou qualquer outra cor além das três acima.

SÍMBOLO (parte gráfica central — o conceito é diferente agora):
- A base é uma rota de corrida estilizada (linha fina, curva suave, cantos arredondados) — representa o treino real.
- MAS, no CENTRO da rota (não só na ponta), há um elemento que representa o "COACH" / orientação / prescrição:
  - Um pequeno quadrado ou losango sólido (#D8FF3E) posicionado no meio da curva. Esse ponto central representa o momento atual do treino sob orientação do app — como se o coach estivesse "no meio" do percurso, guiando.
  - OU: um pequeno círculo concêntrico (#D8FF3E) no centro, com um ponto menor (#F4F6F7) dentro — como um alvo ou um marcador de foco.
- No início da rota (parte inferior): um ponto sólido pequeno (#D8FF3E) — partida.
- No final da rota (parte superior): um ponto discreto (#F4F6F7) — chegada.
- A linha da rota passa pelo ponto central (o "coach"), conectando partida → orientação → chegada. Isso comunica que o app está presente em todo o treino, não só no início ou no fim.
- O símbolo deve ocupar ~50-60% do centro do quadrado, com espaço vazio ao redor (padding de ~20%).
- A linha deve ser fina (stroke 2-3 px), minimalista, sem preenchimento.
- O conjunto deve parecer: "um treino guiado por uma rota precisa" — não um mapa genérico, não um tracking simples.

COMPOSIÇÃO:
- Quadrado escuro (#08090B) com bordas arredondadas.
- Símbolo centralizado: rota com ponto de partida (lima) → ponto central de orientação (lima, maior) → ponto de chegada (branco discreto).
- Nenhum texto, nenhuma borda decorativa, nenhuma sombra pesada, nenhum efeito 3D.
- O símbolo deve ser legível em 48x48 dp (launcher pequeno) e em 512x512 px (Play Store).

ESTILO ADAPTATIVO:
- O símbolo deve funcionar quando cortado pelo formato do launcher (círculo, quadrado arredondado, etc.). O ponto central de orientação deve permanecer visível em todos os formatos.

REFERÊNCIA DO QUE NÃO É:
- NÃO é apenas um app de GPS (não é só uma linha de rota sem contexto).
- NÃO é uma academia (sem músculos, sem halteres, sem raios).
- NÃO é uma marca social ou de moda (sem fontes decorativas, sem gradientes, sem cores extras).
- NÃO é um dashboard corporativo (sem barras, sem gráficos complexos, sem muitos elementos).

O QUE QUERO VISUALMENTE:
- Um quadrado escuro, quase preto.
- Uma linha fina, elegante, que faz uma curva sutil (como uma rota de treino).
- Três pontos na rota: partida (lima, pequeno), centro/orientação (lima, mais proeminente — o coração do app), chegada (branco, discreto).
- A sensação é de precisão, orientação e movimento — como se o app estivesse acompanhando cada passo do treino.
```

---

## Instruções de correção se o Gemini errar

- Se o símbolo parecer apenas um GPS/track sem contexto: `Adicione um ponto central maior (#D8FF3E) no meio da rota para representar a orientação do coach. A rota não é só registrada — é guiada.`
- Se aparecer texto: `Remova qualquer texto ou letra. Apenas símbolo gráfico.`
- Se o fundo não for #08090B: `O fundo deve ser #08090B, sólido, escuro.`
- Se aparecerem cores extras: `Use apenas #D8FF3E, #F4F6F7 e #08090B.`
- Se a linha ficar grossa ou muito decorativa: `Afine a linha para stroke fino (2-3 px) e mantenha minimalista.`
- Se precisar de variações: `Gere 3 versões: (1) quadrado com bordas arredondadas, (2) circular (launcher circular), (3) quadrado sem bordas arredondadas.`

---

## Contexto técnico para implementação (`ADR-0015` e `AGENTS.md`)

- O ícone (`logoBitmap`) é opcional no `StoryFrameRenderer`. Se existir, é desenhado no rodapé (`drawFooter`).
- Se não existir, apenas o texto `"MeuCoach"` é desenhado.
- O arquivo final (`ic_launcher`) usa esse símbolo como `foreground` sobre `background` `#08090B`.
