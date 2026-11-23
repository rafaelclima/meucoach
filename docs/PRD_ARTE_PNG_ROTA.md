# PRD — Arte PNG da rota (card transparente 4:5/1:1/9:16)

**Versão:** 0.2 · **Data:** 22/11/2026 · **Status:** Decisões de produto fechadas; ADR 0058 e spikes pendentes
**Visão base:** ideia do Rafael (22/11/2026) — card de rota estilo Strava para sobrepor a uma foto
**Fonte verdade:** este PRD + [`ADR 0058`](adr/0058-art-png-route.md) + [`ADR 0059`](adr/0059-min-sdk-31.md) + `docs/SPORTS_SCIENCE.md` (métricas) + `docs/DESIGN_SYSTEM.md` (cores/tipografia)
**Depende de:** ADR 0058/0059 + spike de alpha no Instagram + spec visual do 1:1 antes de F2

## 1. Contexto e problema

O app já gera vídeo 9:16, capa 9:16 e pôster 4:5 no **Story Studio** (`app/src/main/kotlin/com/meucoach/app/ui/activitydetail/StoryStudioSheet.kt`), mas todos os outputs têm fundo opaco e visual fixo. Falta um **card** leve, rápido de gerar e **sobre fundo transparente**, que o usuário possa colar sobre uma foto no feed ou no story. Não é um overlay 9:16 opaco: o card tem formato próprio (4:5, 1:1 ou 9:16) e o usuário o posiciona sobre a imagem.

Diferente do vídeo, um PNG com rota desenhada localmente é barato: sem tiles, sem rede, sem encode de vídeo. Por isso a geração é **síncrona no Studio**, sem WorkManager.

## 2. Objetivo

Gerar um **card PNG** com fundo transparente contendo o traçado da rota e informações do treino, com o usuário escolhendo: formato (4:5 default, 1:1 ou 9:16), quais extras aparecem, cores de texto e do traçado (independentes) e a fonte. Acesso pelo mesmo local do vídeo (Story Studio).

**Não é objetivo:** overlay 9:16 full-bleed opaco, mapa no fundo, animação, edição livre (arrastar texto), color picker livre, export em lote, publicação automática. O 9:16 da arte é um card transparente, não um overlay opaco (ADR 0060).

> **Emenda (ADR 0061, 23/11/2026):** o modo Foto pode gerar uma **imagem final 9:16 full-bleed com foto**; a proibição de overlay 9:16 opaco continua valendo para **mapa/tiles**. O card transparente 9:16 segue igual.

## 3. Decisões de produto (fechadas em 22/11/2026)

| # | Tema | Decisão |
|---|---|---|
| 1 | Formato/uso | **Card para sobrepor**, 4:5/1:1/9:16 transparente. Sem overlay 9:16 full-bleed opaco (ADR 0060). |
| 2 | Formatos | **4:5 default + 1:1 + 9:16** (1080×1920). `ROUTE_CROP` fica pós-MVP, com spec própria de bbox/aspect. |
| 3 | Fontes | **6 famílias**: Inter, Geist, Bebas Neue, Anton, Oswald, Archivo Black. Barlow Condensed fica fora. |
| 4 | Persistência | Switch **“Salvar como padrão”** ligado por default; grava a receita em `meucoach_art` ao **Salvar PNG** ou **Compartilhar**. **Restaurar padrão** volta aos defaults. |
| 5 | Plataforma | **Android 12+ (API 31+)**; salvar sempre via MediaStore em `Pictures/MeuCoach`, sem permissão e sem share-only. |
| 6 | Contraste | Controles em **AA**; a arte não promete AA sobre fundo desconhecido. A UI mostra **aviso de contraste** quando texto/traço ficar baixo na prévia clara ou escura. Exceção registrada no ADR. |
| 7 | Extras | **Máx. 2**: data, FC média, elevação, calorias e cadência. |
| 8 | Cores | **8 swatches**: lima, coral, branco, preto, cinza claro, cinza escuro, ciano e azul. Sem `lime-pressed`; reutilizar `data/cyan`/`data/blue`. |
| 9 | Layout | **Comum aos três formatos**: marca no topo, distância um pouco menor, ritmo/tempo empilhados à direita, extras abaixo, rota quadrada e **sem rodapé** (ADR 0060). |
| 10 | Defaults | **4:5, Inter, sem extras, texto branco, traço lima, prévia escura**. |
| 11 | Data | Quando escolhida, **“24 out 2026”** (`d MMM uuuu`), com formatador próprio da arte. |
| 12 | Fonte na UI | Nomes em **Inter** + **preview único da fonte selecionada**; sem 6 famílias na mesma tela. |
| 13 | Marca | **Logo bitmap do pôster**, tintado com a cor do texto, no topo. |
| 14 | Alpha | **Spike no Instagram antes de F5**; se o alpha for achatado, entra toggle “Fundo: transparente / claro / escuro”. **Validado 22/11/2026:** alpha preservado nas 3 amostras (4:5 e 1:1) sobre foto, via figurinha; sem fallback no MVP. O 9:16 usa o mesmo caminho de alpha (ADR 0060). |
| 15 | Compartilhar | **Reaproveitar o último PNG** enquanto atividade + receita + formato não mudarem; se mudar, gera novo. |
| 16 | CTA | **Compartilhar** primário (lima); **Salvar PNG** secundário. |
| 17 | Dois PNGs | Copy/nomes: **“Pôster automático (opaco)”** vs **“Arte transparente (personalizada)”**. |
| 18 | Prévia | **Checkerboard escuro default + toggle claro/escuro + label “Fundo transparente”**. |
| 19 | Vazio/erro | Sem rota: **“Sem rota suficiente para a arte”**, botões desabilitados. Métrica fixa ausente: **“—”**. Erro de render/salvar: inline com **“Tentar novamente”**. |

## 4. Escopo

### Dentro do MVP
- Aba “Arte PNG” no Story Studio, com chrome compartilhado com o vídeo.
- Prévia fixa no topo da aba Arte (o restante rola), em checkerboard, com toggle claro/escuro e label de transparência.
- Três formatos: 4:5 (1080×1350, default), 1:1 (1080×1080) e 9:16 (1080×1920).
- Métricas fixas + até 2 extras.
- 8 swatches para texto e 8 para traçado, independentes.
- 6 fontes, com preview único da selecionada.
- Export síncrono: Android 12+ salva em `Pictures/MeuCoach` via MediaStore, sem permissão.
- Estados loading/empty/error, TalkBack, dark/light (a arte é independente do tema).
- Aviso de contraste e label “Fundo transparente”.

### Fora do MVP (pós-MVP)
- `ROUTE_CROP`/recorte por bbox.
- 9:16 full-bleed **opaco** com arte/mapa.
- Localidade do treino (exige campo novo no modelo ou geocoding).
- Color picker HSV livre.
- Mapa/tiles no fundo da arte.
- Fonte do usuário (arquivo .ttf importado).
- Ativar/desligar marca.
- Animação (GIF/MP4 da rota sendo desenhada).
- Fundo sólido permanente (só entra como fallback se o spike de alpha falhar).

## 5. Especificação da arte

### 5.1 Formatos (`ArtFormat`)
| Valor | Tamanho | Uso |
|---|---|---|
| `PORTRAIT_4_5` | 1080×1350 | Default; feed e card 4:5 |
| `SQUARE` | 1080×1080 | Card quadrado/feed |
| `PORTRAIT_9_16` | 1080×1920 | Story/Reels; card transparente, não overlay opaco (ADR 0060) |

`ROUTE_CROP` não entra no MVP. O canvas inicial é `ARGB_8888` **sem `drawColor`**: o fundo fica transparente por construção.

### 5.2 Conteúdo
- **Fixos:** distância (número grande), tempo total e ritmo médio, resolvidos por `StoryRenderSpecResolver.fromPlan` (mesma fonte do vídeo/pôster; nunca recalculados na UI). O `localDate` é passado explicitamente — o bug do preview sem data foi corrigido em 22/11/2026.
- **Extras (máx. 2):** data, FC média, elevação (VD+), calorias e cadência. Extra sem dado é omitido; o chip fica desabilitado com motivo. Métrica fixa sem dado mostra `—` (nunca `0`).
- **Data:** formatador próprio da arte, `d MMM uuuu` pt-BR, sem ponto final: **“24 out 2026”**. O `StoryMetricCatalog` do vídeo continua em “24 out”.
- **Marca:** logo bitmap do pôster, tintado com a cor do texto, no **topo**.
- **Traçado:** path normalizado do `StoryPlan.route`, com casca escura fixa (`#08090B` a 70%), cor principal escolhida e marcadores de início/fim. Início na cor do texto; fim no coral. Sem ticks de splits (ticks são do pôster).
- **Sem** mapa, tiles ou atribuição OSM.

### 5.3 Layout
**Comum aos três formatos (ADR 0060):** marca no topo → rótulo `DISTÂNCIA` próximo do número → distância à esquerda (fonte ~10% menor) e, na margem direita, `RITMO MÉDIO` sobre `6:00 /km` e `TEMPO TOTAL` sobre `30:00`, um bloco abaixo do outro → extras imediatamente abaixo → rota quadrada no restante. **Sem rodapé/tagline.**

- 4:5 e 9:16 compartilham as posições do bloco de topo; o 9:16 usa a altura extra na rota.
- A distância é ajustada por `fittedSize` para não invadir a coluna da direita; a rota usa `min(largura, altura disponível)` e nunca estica.
- Rótulos e valores ficam mais próximos entre si; os rótulos usam a cor do texto com alpha 70%.

Regras comuns:
- Sem backdrop (gradiente/glow/vignette); só o que é opaco recebe alpha 255.
- Texto na cor escolhida; rótulos usam a mesma cor com alpha 70%.
- Números tabulares (`fontFeatureSettings = "tnum"`) quando a fonte suportar; a arte estática não depende de tnum para não “dançar”.
- Distância e ritmo/tempo usam `fittedSize` para não estourar; extras têm limite de largura e não quebram linha.
- Nada de coordenadas fixas herdadas do pôster: a rota é medida como quadrado dentro do espaço restante.

## 6. Cores

Paleta fechada de **8 swatches** (tokens existentes; a arte é independente do tema):

| Swatch | Token/valor | Papel |
|---|---|---|
| Lima | `accent/lime` `#D8FF3E` | default do traçado |
| Coral | `data/coral` `#FF6B5E` | FC/dados |
| Branco | `text/primary` dark `#F4F6F7` | default do texto |
| Preto | `text/primary` light `#0A0B0D` | texto sobre fundo claro |
| Cinza claro | `text/secondary` dark `#A2A9B0` | hierarquia |
| Cinza escuro | `text/secondary` light `#5C646D` | hierarquia |
| Ciano | `data/cyan` `#5BD0E0` | contraste alternativo |
| Azul | `data/blue` `#6C9CFF` | contraste alternativo |

Regras:
- Defaults: texto `#F4F6F7`, traçado `#D8FF3E`.
- Sem `accent/lime-pressed` (é estado pressionado, não paleta).
- Não criar valores novos: usar os tokens existentes. `McColors` ganha a propriedade `blue` para o azul.
- A arte é transparente; **não** prometemos AA sobre todo fundo. A prévia dupla é a mitigação; a UI mostra **aviso de contraste** quando a combinação falhar em claro ou escuro.
- Controles do Studio seguem AA (4.5:1) e 48 dp.
- Sem hex solto em telas: swatches referenciam tokens; os valores acima são especificação, não literais de UI.

## 7. Fontes (pacote de 6)

Todas Google Fonts, licença **SIL OFL 1.1**; incluir NOTICE no APK (`assets/licenses` ou `res/raw`) e no repositório. Peso estimado: medir no F3; o APK atual já tem Inter e Geist.

| # | Fonte | Caráter | Pesos |
|---|---|---|---|
| 1 | **Inter** (existente) | neutra, padrão do app | variable |
| 2 | **Geist** (existente) | geométrica, moderna | variable |
| 3 | **Bebas Neue** | condensada alta, all-caps | regular |
| 4 | **Anton** | ultra-bold, impacto | regular |
| 5 | **Oswald** | condensada esportiva | 500 + 700 |
| 6 | **Archivo Black** | grotesca pesada | regular |

→ **6 famílias no total** (2 já embarcadas + 4 novas). Barlow Condensed fica fora.

Implementação:
- Fontes em `core/designsystem/src/main/res/font/`.
- O picker mostra os nomes em **Inter** e um **preview único** da fonte selecionada; não renderiza 6 famílias na mesma tela.
- O renderer recebe `ArtFont` + `FontFamily`/`Typeface` resolvido no app; Oswald 700 usa o arquivo 700, não negrito sintético. Fontes variáveis usam o eixo de peso quando necessário.
- Validar `Paint.hasGlyph` para “5,00 km · 31:04 · 5:58 /km” e “ç/ã/á” em cada fonte; golden sozinho não detecta fallback silencioso.
- `tnum` é testado por fonte; se uma display não tiver, a arte estática aceita números proporcionais (a regra de tnum continua no vídeo).

## 8. UX — seção no Story Studio

O `StoryStudioSheet` ganha um **segmento no topo: “Vídeo” | “Arte PNG”**. O chrome (header, fechar, tab bar) é compartilhado; o conteúdo de vídeo deixa de duplicar header/fechar. O conteúdo da aba Arte:

1. **Prévia fixa** 4:5/1:1/9:16 sobre checkerboard escuro; o restante da aba rola abaixo dela; toggle claro/escuro; label “Fundo transparente”; aviso de contraste quando necessário.
2. **Formato:** 3 chips (4:5 · 1:1 · 9:16), 4:5 default.
3. **Extras:** 5 chips (data/FC/elevação/calorias/cadência), máx. 2; indisponível → desabilitado com motivo. Reutilizar o padrão `ExtrasCounter`.
4. **Cor do texto:** 8 swatches, com nome e estado selecionado.
5. **Cor do traçado:** 8 swatches, independente.
6. **Fonte:** nomes em Inter + preview único da selecionada.
7. **Botões:** `Compartilhar` (primário, lima) e `Salvar PNG` (secundário). Sem permissão de armazenamento: os dois usam MediaStore.

Copy:
- “Pôster automático (opaco)” vs “Arte transparente (personalizada)” no Studio/card; uma linha explica que o pôster nasce com o vídeo e a arte é para sobrepor.
- `Compartilhar` salva uma cópia e abre a share sheet; se a arte não mudou, reutiliza o último PNG.
- `Salvar PNG` salva sem abrir a share sheet.

Estados:
- **Loading:** spinner no botão durante render/salvar; prévia anterior permanece visível.
- **Empty:** `plan == null`/rota insuficiente → “Sem rota suficiente para a arte”; controles visíveis, `Salvar`/`Compartilhar` desabilitados.
- **Error:** render/salvar falhou → mensagem inline + “Tentar novamente”; sem fechar o Studio.

Acessibilidade:
- Labels TalkBack por grupo (“Cor do traçado, lima selecionada”), swatch com `selected` + borda + check.
- Alvos ≥ 48 dp; 8 swatches/6 fontes não cabem em uma linha de 320 dp: usar `FlowRow`/`LazyRow`.
- Suportar fonte do sistema a 200% e tela de 360 dp sem corte; a arte em si tem tipografia fixa.
- Checkerboard com label; prévia com `contentDescription` resumindo formato/cores/fonte.
- Não usar informação só por cor.

## 9. Arquitetura e técnica

### 9.1 Camadas
- **`core:domain` (puro):** `ArtRecipe` (formato, extras ≤2, fonte, `ArtColor` de texto e traçado), `ArtFormat` (`PORTRAIT_4_5`, `SQUARE`, `PORTRAIT_9_16`), `ArtFont` (6), `ArtColor` (8). Sem `android.*`; `require` para máx. 2/sem duplicado; `toggleExtra` **ignora** o terceiro toque (não derruba a tela). `ArtRecipe.toStoryRecipe()` mapeia os extras para `StoryRecipe(extras = ...)`; reutilizar `StoryMetricValuesResolver`/`StoryRenderSpecResolver.fromPlan`; não criar `ArtMetricValues`.
- **`app/story`:** `StoryArtRenderer` recebe `plan`, `localDate`, `ArtRecipe`, `Typeface`/`FontFamily`, `logoBitmap`, `width`, `height`; devolve `Bitmap?` ou escreve PNG. Preview em ~540 px; export em 1080. Síncrono, sem Compose, testável.
- **Data:** `ArtDateFormatter` com `d MMM uuuu` (24 out 2026), separado do `StoryMetricCatalog`.
- **Persistência:** `ArtRecipeCodec` (JSON versionado, fallback para enum desconhecido) + `ArtRecipeStore` em `SharedPreferences` **`meucoach_art`** (não DataStore). Save-as-default; reset separado do `meucoach_story`.
- **Export:** extrair `GalleryImagePublisher` do `publishImage` privado de `CanvasStoryVideoExporter`, com `ImagePublishResult(uri, savedToGallery)`. Android 12+ → MediaStore `Pictures/MeuCoach`, sem permissão e sem branch `<Q`. Preservar o comportamento atual de capa/pôster: falha de imagem não falha o vídeo.
- **Reuso:** `ArtExportKey(activityId, recipe, format)`; se o mesmo key e o arquivo ainda existir, `Compartilhar` reutiliza o URI; se não, renderiza/salva de novo.
- **Arquivos:** `StoryFileNames.art(plan, format, stamp)` → `...-arte-4x5.png` / `...-arte-1x1.png`; pôster automático continua `...-poster-4x5.png`.

### 9.2 Fluxo
```
Studio (aba Arte PNG) → ArtRecipe (domain) → StoryArtRenderer.render(plan, localDate, recipe, typeface, logo, 540/1080)
    → preview (Bitmap → Image) / Compartilhar|Salvar (PNG → GalleryImagePublisher → share sheet)
```
Sem Worker, sem notificação, sem mapa. Render no `Dispatchers.Default`, com um render em voo, debounce de ~120 ms e resultado na main. Export full-res só no Salvar/Compartilhar.

### 9.3 Testes (TDD)
- `core:domain`: `ArtRecipe` (limite 2, duplicados, toggle ignora terceiro, defaults), `ArtColor`/`ArtFont` estáveis, codec (versão/unknown).
- `StoryArtRenderer`: goldens 4:5, 1:1 e 9:16; alpha de fundo = 0 por pixel (não só golden); caso sem rota → `null`; sem distorção da rota; `Paint.hasGlyph` nas 6 fontes; preview 540 vs export 1080.
- UI: aba, prévia fixa, 3 formatos, loading/empty/error, limite de 2, TalkBack, 200%/360 dp, checkerboard, aviso de contraste, CTA, share/save.
- Export: nome, MediaStore, share intent, reuso do último PNG, falha de save, sem permissão de armazenamento.
- Regressão: vídeo/capa/pôster e `StoryStudioContentTest`/goldens existentes sem mudança de comportamento.

## 10. Fatias de execução

| # | Fatia | Entregável verificável |
|---|---|---|
| F0 | **PRD + ADR 0058/0059/0060 + spec visual + spikes** (alpha/Instagram, fontes, preview) | ADR aceito; decisões dos spikes registradas; spec 4:5/1:1/9:16 aprovada |
| F1 | **Bugfix `localDate`** (feito em 22/11/2026) | `ActivityDetailViewModelTest` verde; preview/pôster com data |
| F2 | Domínio: `ArtRecipe`/`ArtFormat`/`ArtFont`/`ArtColor` + testes | `:core:domain:test` verde |
| F3 | Fontes (4 novas) + tokens/`McColors` + NOTICE + testes de glifo | fontes carregam; `Paint.hasGlyph`/`tnum` verdes |
| F4 | `StoryArtRenderer` + goldens 4:5/1:1/9:16 + alpha por pixel | renderer sem rota = null; alpha 0; sem distorção |
| F5 | UI: chrome/segmento + estado da arte + prévia fixa/debounce + a11y (extrair `ExtrasCounter`) | testes de composição e goldens da aba |
| F6 | `GalleryImagePublisher` + salvar/compartilhar + reuso | testes de export/share; validação no aparelho |
| F7 | `ArtRecipeCodec` + `ArtRecipeStore` integrados + polimento | receita sobrevive a restart/update; reset |
| F8 | Docs, `PROGRESS`, `ROADMAP`, `DESIGN_SYSTEM`, DoD | `check` + `spotlessCheck` + `verifyRoborazziDebug` verdes |

Cada fatia = 1 PR pequeno, Conventional Commits. F0/F1 podem ir em paralelo; F3 antes de F4 para não regravar goldens duas vezes.

## 11. Riscos e pendências

| Risco | Mitigação |
|---|---|
| Alpha achatado no Instagram | Spike antes de F5; fallback “Fundo: transparente/claro/escuro” só se falhar — **fechado 22/11/2026: alpha preservado** |
| Memória/tempo da prévia | 540 px, debounce ~120 ms, um render em voo, recycle seguro, budget no F5 |
| Fontes display sem glifo/tnum/700 | `Paint.hasGlyph`, `FontFamily` com pesos, teste por fonte; cortar fonte problemática se necessário |
| Layout 1:1/9:16 apertado | Spec visual própria; rota quadrada; sem coordenadas fixas do pôster; prévia fixa com altura adaptativa |
| Plataforma Android 12+ | ADR 0059; sem branch `<Q`; testar API 31+ |
| Contraste sobre foto | Aviso na prévia clara/escura; ADR de exceção; controles AA |
| Dois PNGs 4:5 | Copy “Pôster automático” vs “Arte transparente”; nomes de arquivo distintos |
| Duplicar PNG a cada share | `ArtExportKey` + reuso do último URI; regenerar se mudou/apagou |
| Escopo creep (overlay 9:16 opaco, crop, mapa) | §4 é lei; novas ideias viram pós-MVP. Card 9:16 transparente está no escopo pelo ADR 0060 |

### Pendências de validação (F0)
- [x] Spike de alpha no Instagram Story/Feed — **validado 22/11/2026**: 3 amostras do `StoryArtRenderer` (1080 px, fundo transparente) sobre foto via figurinha; alpha preservado nas 3.
- [ ] Spike de fontes: glifos, 700, tnum, tamanho de APK.
- [ ] Spec visual 4:5/1:1/9:16: posições, tipografia, rota, casca/marcadores (ADR 0060 em implementação).
- [ ] Decidir fallback de fundo sólido apenas se o alpha falhar.

## 12. Definition of Done (feature completa)

- [ ] `./gradlew :app:assembleDebug`, `check`, `spotlessCheck`, `koverVerify` e lint/detekt verdes.
- [ ] `./gradlew :app:verifyRoborazziDebug --tests "*Art*"` verde (o `check` não roda goldens).
- [ ] Goldens 4:5, 1:1 e 9:16; alpha de fundo = 0 por pixel; caso sem rota = `null`.
- [ ] `Paint.hasGlyph` e `tnum` testados nas 6 fontes; data “24 out 2026”.
- [ ] UI: prévia fixa, 360 dp, 200%, TalkBack, checkerboard, aviso de contraste, CTA, share/save, reuso.
- [ ] Android 12+ (API 31+): salvar via MediaStore; sem permissão de armazenamento.
- [ ] Vídeo, capa e pôster existentes sem regressão.
- [ ] Validado no aparelho (S24 Ultra) e em emulador API 31+.
- [ ] `PROGRESS.md`, `ROADMAP.md`, `DESIGN_SYSTEM.md` e `DECISIONS.md` atualizados; ADR 0058/0060 publicados.
- [ ] Nenhum dado real no diff; NOTICE OFL no APK e no repositório.
