# DESIGN_SYSTEM.md — MeuCoach

**Versão:** 1.0 · **Data:** 27/10/2026 · **Status:** direção aprovada

## 1. Ideia de marca

**"Precisão que orienta."**

O MeuCoach não é um app de ginástica vibrante, nem um dashboard corporativo. É um instrumento pessoal de treino: escuro, silencioso, denso na medida certa, com um único gesto de cor que indica "faça isto". A estética combina:

- **Grafite profundo** e superfícies em camadas (referência Dribbble).
- **Lima ácida** como único accent de ação.
- **Coral** reservado à fisiologia (FC/esforço), nunca a CTA.
- **Cartões grandes**, cantos generosos, tipografia editorial e números gigantes.
- **Anéis e gauges** com brilho sutil (referência Pinterest), sem excesso de neon.

O resultado deve parecer um equipamento de precisão, não um brinquedo.

## 2. Princípios visuais

1. **Dark-first, light completo.** O tema escuro é o padrão; o claro é equivalente, não um remendo.
2. **Um accent.** Lima é a única cor de ação. Coral/lavanda/ciano são dados.
3. **Números primeiro.** Métricas usam numerais tabulares e escala grande.
4. **Densidade calma.** Muita informação, mas com respiro e hierarquia clara.
5. **Bordas, não sombras.** Profundidade por superfícies e contraste; sombras só em sheets.
6. **Movimento com propósito.** Nada de animação decorativa; feedback e transição.
7. **Acessível por construção.** Contraste AA, alvos de 48 dp, TalkBack, fonte escalável.
8. **Sem gamificação agressiva.** Sem chamas, streaks punitivas ou vermelho de culpa.

## 3. Cores

### 3.1 Tokens escuros (padrão)

| Token | Hex | Uso |
|---|---|---|
| `bg/base` | `#08090B` | Fundo do app |
| `bg/subtle` | `#0E1013` | Fundo de seções |
| `surface/default` | `#121418` | Cards |
| `surface/elevated` | `#191C21` | Cards em destaque, sheets |
| `surface/inset` | `#0B0D10` | Campos, áreas recessadas |
| `border/subtle` | `#23272D` | Bordas de 1 dp |
| `border/strong` | `#343A42` | Divisores e foco |
| `text/primary` | `#F4F6F7` | Títulos e números |
| `text/secondary` | `#A2A9B0` | Texto de apoio |
| `text/tertiary` | `#7F8895` | Labels, metadados; ajustado para AA em `surface/elevated` |
| `text/inverse` | `#0A0B0D` | Texto sobre lima |
| `accent/lime` | `#D8FF3E` | CTA, seleção, progresso |
| `accent/lime-pressed` | `#C2E82F` | Estado pressionado |
| `accent/lime-soft` | `#2A3310` | Fundo sutil de destaque |
| `data/coral` | `#FF6B5E` | FC, esforço, alerta fisiológico |
| `data/lavender` | `#B9A6FF` | Categoria secundária |
| `data/cyan` | `#5BD0E0` | Categoria terciária |
| `data/amber` | `#FFB454` | Atenção em dados |
| `data/blue` | `#6C9CFF` | Categoria |
| `data/green` | `#56D68B` | Sucesso em dados |

### 3.2 Tokens claros

| Token | Hex | Uso |
|---|---|---|
| `bg/base` | `#F6F7F8` | Fundo |
| `bg/subtle` | `#EEF1F3` | Seções |
| `surface/default` | `#FFFFFF` | Cards |
| `surface/elevated` | `#FFFFFF` | Sheets (com borda) |
| `surface/inset` | `#F1F3F5` | Campos |
| `border/subtle` | `#E3E6E9` | Bordas |
| `border/strong` | `#C9CFD5` | Divisores |
| `text/primary` | `#0A0B0D` | Texto |
| `text/secondary` | `#5C646D` | Apoio |
| `text/tertiary` | `#626C5A` | Labels; ajustado para AA no claro |
| `text/inverse` | `#0A0B0D` | Sobre lima |
| `accent/lime` | `#D8FF3E` | Preenchimento de CTA |
| `accent/lime-text` | `#4F6600` | Lima como **texto** no claro |
| `accent/lime-soft` | `#F1FBCB` | Fundo sutil |
| `data/coral` | `#B33A32` | FC; ajustado para AA no claro |
| `data/lavender` | `#6E54C8` | Categoria |
| `data/cyan` | `#0E7C8C` | Categoria |
| `data/amber` | `#8A5200` | Atenção; ajustado para AA no claro |
| `data/blue` | `#2E5BCC` | Categoria |
| `data/green` | `#1F7A4D` | Sucesso |

### 3.3 Semântica

| Token | Escuro | Claro | Uso |
|---|---|---|---|
| `semantic/success` | `#4ADE80` | `#1F7A4D` | Concluído, verde |
| `semantic/warning` | `#FFB454` | `#8A5200` | Âmbar |
| `semantic/danger` | `#FF5A5F` | `#C62828` | Erro, sinal vermelho |
| `semantic/info` | `#6C9CFF` | `#2E5BCC` | Informação |

### 3.4 Zonas de FC

| Zona | Cor escura | Cor clara | Label |
|---|---|---|---|
| Z1 | `#6C9CFF` | `#2E5BCC` | Recuperação |
| Z2 | `#56D68B` | `#1F7A4D` | Base |
| Z3 | `#FFD166` | `#8A5A00` | Moderado |
| Z4 | `#FF9F43` | `#A35A00` | Limiar |
| Z5 | `#FF5A5F` | `#C62828` | VO2max |

**Regra:** nunca codificar zona apenas por cor. Sempre usar rótulo (Z1, Z2…), posição na barra e descrição acessível.

### 3.5 Contraste e uso do lima

- Lima `#D8FF3E` sobre `#08090B`: contraste alto — ok para texto grande, ícones e CTA.
- Lima como **texto** no tema claro é proibido; usar `#4F6600`.
- Texto sobre lima é sempre `#0A0B0D`.
- Para cumprir 4.5:1, `text/tertiary` foi ajustado para `#7F8895` no escuro e `#626C5A` no claro; `data/coral` claro para `#B33A32`; `data/amber`/`semantic/warning` claro para `#8A5200`. Os tokens refletem esses valores.
- Pills com cor de zona/data usam fundo em 10% de opacidade no tema claro para preservar 4.5:1 no texto.
- Coral não é usado em botões; apenas em dados, ícones fisiológicos e gráficos.
- Vermelho de perigo é distinto do coral de FC; não misturar contextos.

## 4. Tipografia

### 4.1 Famílias

| Papel | Fonte | Licença | Fallback |
|---|---|---|---|
| Display / números | **Geist** | OFL | `sans-serif` |
| Texto / UI | **Inter** | OFL | `sans-serif` |
| Dados monoespaçados (opcional) | **Geist Mono** | OFL | `monospace` |

Ambas são embarcadas como fontes variáveis. Sem fontes proprietárias (Satoshi/General Sans) para evitar risco de licença no APK.

### 4.2 Escala

| Token | Tamanho / linha | Peso | Uso |
|---|---|---|---|
| `display/xl` | 56/56 | 600 | Métrica hero (pace, distância) |
| `display/lg` | 44/48 | 600 | Número de destaque |
| `display/md` | 36/40 | 600 | Métrica de card |
| `headline/lg` | 28/32 | 600 | Título de tela |
| `headline/md` | 22/28 | 600 | Título de seção |
| `title/lg` | 18/24 | 600 | Título de card |
| `body/lg` | 16/24 | 400 | Texto principal |
| `body/md` | 14/20 | 400 | Texto secundário |
| `label/lg` | 13/18 | 500 | Labels |
| `label/sm` | 11/14 | 500 | Metadados, uppercase, tracking 0.08em |
| `metric/hero` | 64/64 | 600 | Número gigante (readiness) |

Regras:

- Números de métricas usam `fontFeatureSettings = "tnum"` (tabular) e `letterSpacing = -0.02em`.
- Display usa `-0.03em`; labels uppercase usam `0.08em`.
- Nunca usar mais de 2 famílias na mesma tela.
- No tema claro, reduzir peso de display em um passo se necessário para conforto.

## 5. Espaçamento, raios e layout

- Grid base: **4 dp**.
- Escala: 4, 8, 12, 16, 20, 24, 32, 40, 48, 64.
- Margem lateral: **20 dp** (compacto) / 24 dp (médio) / 32 dp (tablet).
- Largura máxima de conteúdo: 640 dp.
- Altura mínima de toque: **48 dp**.
- Bottom bar: 72 dp + safe area; ação central circular de 56 dp.

| Token | Valor | Uso |
|---|---|---|
| `radius/sm` | 12 dp | Chips, inputs |
| `radius/md` | 20 dp | Cards internos |
| `radius/lg` | 28 dp | Cards principais |
| `radius/xl` | 36 dp | Hero cards |
| `radius/pill` | 999 dp | Botões, pills |

**Elevação:** superfícies em camadas + borda 1 dp. Sombra apenas em bottom sheets/dialogs (`8 dp`, 24% de opacidade). Glow de lima: apenas em gauge/CTA ativo, opacidade ≤ 12%.

## 6. Iconografia

- Estilo: linha, 1.5–2 dp, cantos arredondados, grade 24 dp.
- Tamanhos: 16, 20, 24, 32.
- Ícones nunca substituem labels em ações críticas.
- Ícones de zona/FC podem ser preenchidos para reforço.
- Usar Material Symbols (outlined) no MVP; ícones customizados só com necessidade.

## 7. Componentes

### 7.1 Estrutura

| Componente | Especificação |
|---|---|
| `McScaffold` | edge-to-edge, fundo `bg/base`, conteúdo com padding 20 dp, bottom bar opcional |
| `McTopBar` | saudação + data + avatar/ação; título 28/32 |
| `McSectionHeader` | título 22/28 + ação textual opcional |
| `McCard` | superfície, raio 28, borda 1 dp, padding 20; variantes `default`, `accent`, `inset` |
| `McHeroCard` | raio 36, gradiente sutil, número display/xl |
| `McPrimaryButton` | altura 56, pill, lima, texto ink, ícone opcional; estado pressed `lime-pressed` |
| `McSecondaryButton` | borda 1 dp, fundo transparente, texto primary |
| `McDangerButton` | borda 1 dp `danger`, texto `danger`, só para ação destrutiva confirmada |
| `McChip` | pill, 36 dp, selecionado lima/ink |
| `McStat` | label/sm + valor display/md + unidade body/md + delta opcional |
| `McMetricRow` | 3–4 métricas com divisores |
| `McGauge` | anel de 220° com score, nível e confiança; espessura 10 dp |
| `McConfidenceBadge` | HIGH/MEDIUM/LOW com ponto + label; nunca só cor |
| `McSparkline` | linha 2 dp, área 12% opacidade, sem eixos |
| `McLineChart` | Vico; eixos, tooltip, legenda, unidade |
| `McBarChart` | barras com raio 6; gaps 8; destaque no dia atual |
| `McZoneBar` | barra empilhada 5 zonas + legenda com tempo/% |
| `McCalendarHeatmap` | 7×N, 12 dp, escala de 5 níveis, tooltip |
| `McSplitTable` | splits por km, pace, FC, elevação, destaque do melhor |
| `McWhySheet` | bottom sheet com rationale, regras, evidências, confiança |
| `McEmptyState` | ícone, título, descrição, CTA opcional |
| `McErrorState` | causa, ação, detalhes técnicos colapsáveis |
| `McSkeleton` | shimmer sutil, respeita reduced motion |
| `McBottomBar` | 4 destinos + ação central circular lima |

### 7.2 Estados

Todo componente interativo define: default, pressed, focused, disabled, loading, error. Todo dado remoto define: loading, empty, error, success, stale (com timestamp).

## 8. Data visualization

### 8.1 Regras

1. **Uma pergunta por gráfico.** Cada chart responde a uma pergunta clara.
2. **Eixos com unidade.** Pace invertido (mais rápido para cima), tempo em mm:ss, distância em km.
3. **Nada de pizza.** Distribuição = barra empilhada, anel ou barras.
4. **Downsampling.** No máximo 1 ponto por 2 dp de largura; nunca renderizar 10 mil pontos.
5. **Tooltip** com data, valor, zona e fonte.
6. **Cores com significado fixo.** Lima = ação; coral = FC; zonas = escala fixa.
7. **Nunca cor sozinha.** Rótulo, forma ou padrão.
8. **Acessibilidade.** Cada gráfico tem `contentDescription` com resumo e opção "ver tabela".
9. **Contexto.** Linha de referência (limiar, média 30d, meta) quando ajudam.
10. **Sem 3D, sem gradientes enganosos, sem eixos truncados sem aviso.**

### 8.2 Gráficos por tela

| Tela | Gráfico | Pergunta |
|---|---|---|
| Hoje | Gauge de prontidão | "Como estou hoje?" |
| Hoje | Iniciar/Trocar treino | "O que faço agora?" |
| Hoje | Explicação do treino | "Por que este treino?" |
| Check-in | Histórico Hooper | "Como estive?" |
| Hoje | Mini PMC (7d) | "Estou acumulando carga?" |
| Atividade | Pace × FC × tempo | "Como foi o esforço?" |
| Atividade | Story do treino | "Quero compartilhar?" |
| Atividade | Zonas empilhadas | "Onde passei o tempo?" |
| Atividade | Splits | "Onde quebrou?" |
| Tendências | PMC (CTL/ATL/TSB) | "Estou evoluindo sem estourar?" |
| Tendências | Volume semanal | "Minha consistência?" |
| Tendências | Conclusão do plano | "Estou seguindo o plano?" |
| Tendências | Distribuição 80/20 | "Estou no equilíbrio?" |
| Tendências | Pace @ FC (eficiência) | "Fiquei mais eficiente?" |
| Tendências | Recordes | "Qual foi meu melhor esforço?" |
| Tendências | Heatmap de consistência | "Mantive o hábito?" |

### 8.3 Paleta de séries

Ordem para séries categóricas: lima (destaque), coral, lavanda, ciano, azul, âmbar. Em gráficos com mais de 4 séries, usar linha + label direto; não confiar na cor.

## 9. Movimento e háptica

| Token | Duração | Uso |
|---|---|---|
| `motion/fast` | 160 ms | Chips, toggles |
| `motion/base` | 220 ms | Transições de card, sheets |
| `motion/slow` | 320 ms | Entrada de tela, gauge |
| `motion/spring` | — | Cards e bottom sheets (damping 0.85, stiffness 380) |

- Respeitar `Settings.Global.ANIMATOR_DURATION_SCALE` e redução de movimento.
- Haptics: leve em seleção, médio em concluir check-in, nenhum em scroll.
- Sem animação de números que impeça leitura; contar de forma suave só no primeiro carregamento.

## 10. Acessibilidade

- Contraste mínimo **4.5:1** para texto e **3:1** para ícones/linhas.
- Suporte a fonte do sistema até 200%; layouts não podem cortar métricas.
- TalkBack: cada gráfico tem descrição; cada card tem `contentDescription` agrupado.
- Alvos de toque ≥ 48×48 dp.
- Não usar apenas cor para status; incluir ícone/texto.
- Foco visível com anel de 2 dp `accent/lime`.
- Textos em pt-BR claros, sem jargão não explicado.

## 11. Blueprints de tela

### Hoje

```
[ Bom dia, Rafael ]                     [avatar]
[ Ter, 27 de outubro · Natal, 28° ]

[ HeroCard: Prontidão 72 · Verde · confiança média ]
[ Gauge 220° ]  Sono 9h13 / Variabilidade média · 7 noites / FC estável
[ "Por que esta prontidão?" ]

[ Próximo treino ]
[ Fácil Z2 · 40 min · 6,5 km ]
[ Pace 7:40–8:20/km · FC 145–160 ]
[ Por que: base aeróbica + prontidão ]
[ Iniciar / Trocar / Concluir ]

[ Carga da semana: mini PMC + 18 km ]
[ Check-in pendente · 15 s ]

[ Última atividade: 5,11 km · 30:59 · Z5 ]
```

Na prontidão, cada sinal ocupa duas linhas (rótulo e valor + hint curto). O rótulo usa `maxLines = 1` com ellipsis e nunca quebra no meio da palavra; o hint só aparece quando acrescenta contexto (ex.: “abaixo de 6 h”, “7 noites”, “média mensal”).

### Atividade

```
[ Voltar ] [ Corrida do empreendedor ] [ ... ]
[ 5,11 km ] [ 30:59 ] [ 6:04/km ]
[ FC 181/187 ] [ Cadência 188 ] [ Potência 322 W ]

[ Gráfico pace × FC × tempo ]
[ Zonas empilhadas ]
[ Splits por km ]
[ Mapa / elevação ]
[ O que aprendemos: "Ritmo de prova forte; sua média ficou 97% da LTHR." ]
```

### Tendências

```
[ PMC: CTL, ATL, TSB ]
[ Filtro: 6 / 12 / 26 semanas ]
[ Volume semanal ]
[ Conclusão do plano ]
[ Distribuição 80/20 ]
[ Pace @ FC (eficiência) ]
[ Consistência (heatmap) ]
[ Recordes ]
```

### Importar

```
[ Arraste ou selecione FIT, TCX, GPX ]
[ Últimos imports + status ]
[ Deduplicação: "1 substituída (FIT > GPX)" ]
[ Erros com detalhes ]
```

## 12. Conteúdo e tom

- **Direto, caloroso, sem julgamento.** "Hoje é dia de fácil" em vez de "Você não deve forçar".
- Explicar termos na primeira aparição.
- Nunca culpar o usuário por treino perdido.
- Números sempre com unidade e contexto.
- Evitar emojis em excesso; no máximo um em saudações.
- pt-BR como idioma inicial; strings externalizadas para i18n futura.

## 13. Implementação

- Tokens em `design/tokens/meucoach.tokens.json` (formato DTCG simplificado).
- Tema Compose em `core/designsystem`: `McTheme` com `darkColorScheme`/`lightColorScheme`, tipografia e shapes.
- Sem dynamic color do Material You; a marca manda.
- Fontes em `core/designsystem/src/main/res/font/` (Geist, Inter, Geist Mono).
- Vico para line/bar; Canvas para gauge, zone bar e heatmap.
- Componentes têm previews e testes de screenshot (Fase 1).
- Toda tela usa `McScaffold` e tokens; nenhum hexadecimal solto no código.

## 14. Do / Don't

| Do | Don't |
|---|---|
| Um CTA lima por tela | Vários botões coloridos competindo |
| Números tabulares grandes | Números proporcionais que "dançam" |
| Cards com borda e camadas | Sombras pesadas e vidro fosco |
| Gráficos com pergunta clara | Dashboard com 12 gráficos sem hierarquia |
| Coral para FC | Coral em botão de ação |
| Zonas com rótulo | Zonas só por cor |
| Texto claro e curto | Jargão fisiológico sem explicação |
| Movimento funcional | Animação decorativa infinita |

---

## 15. Princípios de conteúdo e comunicação

> Baseado em `docs/COMMUNICATION.md` (§2) e nos princípios do produto (`AGENTS.md` §5, `PRODUCT.md` §5).

1. **Uma pergunta por texto.** Cada texto na UI responde a uma pergunta clara do usuário (`Hoje`: "Como estou?" / `Trends`: "Estou evoluindo sem estourar?" / `Story`: "Quero compartilhar?").
2. **Contexto antes do termo.** Todo termo técnico (`TRIMP`, `ACWR`, `LTHR`, `Z2`) deve ser acompanhado de uma definição simples (`docs/GLOSSARY.md`). Se não há contexto, não aparece sozinho.
3. **Números com unidade e contexto.** Sempre explicar o que o número diz (`Carga crônica (42 dias)`, `Balanço (recuperação)`, `Proporção carga (ACWR)`).
4. **Sem jargão sem explicação.** `TRIMP` → `Carga de treino (baseada em FC)`; `Monotonia` → `Variedade de treino`; `Recorde` → `Seus melhores trechos`; `Qualidade` → `treino forte`; `Pace` → `ritmo`.
5. **Conservador por padrão.** A comunicação reforça que `ACWR > 1,5` é alerta (`nunca meta`), que `TSB < 0` é descanso (`não força`), e que `Pace de limiar` é um limite (`não um alvo diário`).
6. **Acessível.** Contraste AA, fontes escaláveis (`Geist`/`Inter`), alvos de 48 dp, TalkBack (`DESIGN_SYSTEM.md` §10). Os textos devem ser curtos (`≤ 2 linhas` para legendas), claros e sem abreviações obscuras (`FC` é aceito porque é explicado; `TRIMP` não aparece sem contexto).
7. **Auditável.** Os `ruleId` (`R-ACT-01`, `R-PLAN-01`) e `evidenceId` (`EV-...`) continuam no código (`core:domain`) para rastreabilidade (`ADR-0004`), mas a UI traduz para linguagem simples (`docs/COMMUNICATION.md` §3).
8. **Referência sob demanda.** Fonte e ID não competem com a decisão: aparecem no máximo uma vez, colapsados, atrás de `Ver fontes e detalhes técnicos`. Nenhuma tela comum exibe `R-` ou `EV-`.

---

## 16. Acessibilidade e testes

- Contraste mínimo **4.5:1** para texto e **3:1** para linhas (`DESIGN_SYSTEM.md` §3.5, §10).
- Alvos de toque ≥ **48×48 dp** (`DESIGN_SYSTEM.md` §5).
- Todo gráfico tem `contentDescription` (`DESIGN_SYSTEM.md` §8.2).
- Screenshots (`Roborazzi`) atualizados após mudanças visuais (`docs/TESTING.md` §4).
- Cobertura mínima `core:domain`: **≥ 85%** (`docs/TESTING.md` §7).

---

## 17. Referências

- `docs/PRODUCT.md` (visão, usuário 0, métricas)
- `docs/ARCHITECTURE.md` (módulos, dependências, privacidade)
- `docs/SPORTS_SCIENCE.md` (ciência do esporte, evidências)
- `docs/COMMUNICATION.md` (reformulação de textos, princípios de conteúdo)
- `docs/GLOSSARY.md` (definições simples de termos técnicos)
- `docs/adr/0012-story-video.md` (story de treino)
- `docs/adr/0015-story-video-map.md` (mapa real no story)
