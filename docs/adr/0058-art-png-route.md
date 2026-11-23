# ADR 0058 — Arte PNG da rota: card transparente 4:5/1:1

**Status:** Aceito — decisões de produto fechadas em 22/11/2026; F2–F8 implementadas; **spike de alpha validado no Instagram em 22/11/2026** (alpha preservado); **estendido pelo [ADR 0060](0060-art-png-9x16-layout.md)** em 23/11/2026 (9:16 + novo layout + prévia fixa). **Emendado pelo [ADR 0061](0061-art-photo-studio.md)** em 23/11/2026: a imagem final com foto em 9:16 full-bleed é permitida; a proibição de overlay 9:16 opaco continua valendo para mapa/tiles.
**Data:** 2026-11-22
**Responsável:** usuário 0 (decisões de produto) e implementação
**Documentos de origem:** [`PRD_ARTE_PNG_ROTA.md`](../PRD_ARTE_PNG_ROTA.md), [`ADR 0059`](0059-min-sdk-31.md), `docs/DESIGN_SYSTEM.md`, `docs/SPORTS_SCIENCE.md`

---

## 1. Contexto

O Story Studio já entrega vídeo 9:16, capa 9:16 e pôster 4:5, todos opacos e com composição fixa. O usuário quer um artefato leve, offline e **transparente** para colar sobre uma foto no feed ou no story — um card com o traçado da rota e métricas, não um overlay full-bleed.

O pôster existente é uma composição própria 4:5; a arte é uma composição própria 4:5/1:1/9:16, com personalização de formato, cores, fonte e extras. O renderer não pode copiar as coordenadas fixas do pôster: cada formato exige posições próprias.

## 2. Decisão

### 2.1 Produto: card, não overlay 9:16 opaco

- A arte é um **card 4:5 (default), 1:1 ou 9:16** para sobrepor a uma foto/story.
- **9:16 full-bleed opaco** fica pós-MVP. O 9:16 **transparente** foi adicionado pelo [ADR 0060](0060-art-png-9x16-layout.md).
- **`ROUTE_CROP`** (bbox do traçado) fica pós-MVP, com spec própria; o MVP não lida com aspect variável nem rota degenerada.
- **Plataforma:** Android 12+ (API 31+), conforme ADR 0059; sem caminho `<Q` e sem permissão de armazenamento.
- Acesso pelo **Story Studio**, em segmento “Vídeo | Arte PNG”.

### 2.2 Formatos e layout

| Formato | Tamanho | Layout |
|---|---|---|
| `PORTRAIT_4_5` | 1080×1350 | Marca no topo, distância + ritmo/tempo à direita, extras abaixo, rota quadrada; sem rodapé |
| `SQUARE` | 1080×1080 | Mesmo bloco, com posições compactas; rota quadrada no restante |
| `PORTRAIT_9_16` | 1080×1920 | Mesmo bloco de topo do 4:5; rota quadrada centralizada na altura extra |

- O layout comum e a remoção do rodapé estão no [ADR 0060](0060-art-png-9x16-layout.md).
- Sem backdrop (gradiente/glow/vignette); fundo transparente por construção (`ARGB_8888` sem `drawColor`).
- Rota sempre em área quadrada para preservar o aspecto da projeção normalizada; não esticar em retângulo.
- Não reutilizar as constantes fixas `WIDTH/HEIGHT`/y do `StoryPosterRenderer`; o layout é por blocos e format-aware.
- Marca no topo: logo bitmap do pôster, tintado com a cor do texto.

### 2.3 Métricas, extras e data

- Essenciais: distância, tempo total e ritmo médio, resolvidos por `StoryRenderSpecResolver.fromPlan`, com `localDate` explícito.
- Extras: no máximo **2**, entre data, FC média, elevação, calorias e cadência. Extra sem dado é omitido; chip desabilitado com motivo. Métrica fixa ausente mostra `—`, nunca `0`.
- Data da arte: **“24 out 2026”** (`d MMM uuuu`, pt-BR, sem ponto), com `ArtDateFormatter` próprio. O `StoryMetricCatalog` do vídeo continua em “24 out”.
- Correção de base: `ActivityDetailViewModel.calculateStoryPlan` agora copia `stored.localDate` para o plano; preview, pôster e worker não divergem mais na data.

### 2.4 Cores: 8 tokens existentes, sem valores novos

| Swatch | Valor | Papel |
|---|---|---|
| Lima | `#D8FF3E` (`accent/lime`) | default do traçado |
| Coral | `#FF6B5E` (`data/coral`) | FC/dados |
| Branco | `#F4F6F7` (`text/primary` dark) | default do texto |
| Preto | `#0A0B0D` (`text/primary` light) | texto sobre fundo claro |
| Cinza claro | `#A2A9B0` (`text/secondary` dark) | hierarquia |
| Cinza escuro | `#5C646D` (`text/secondary` light) | hierarquia |
| Ciano | `#5BD0E0` (`data/cyan`) | contraste alternativo |
| Azul | `#6C9CFF` (`data/blue`) | contraste alternativo |

- `accent/lime-pressed` **não** é swatch (é estado pressionado).
- `McColors` ganha `blue`; `data/cyan`/`data/blue` já existem nos tokens.
- Texto e traçado escolhidos independentemente; defaults branco/lima.
- A arte não promete AA sobre fundo desconhecido; controles ficam AA e a UI mostra aviso quando a combinação falhar no claro/escuro. Exceção registrada neste ADR.

### 2.5 Fontes: 6 famílias

- Inter e Geist (já embarcadas, variáveis).
- Bebas Neue, Anton, Oswald (500 + 700) e Archivo Black (4 novas famílias; 5 arquivos novos).
- Barlow Condensed fica fora.
- Picker: nomes em Inter + preview único da fonte selecionada; não renderizar 6 famílias na mesma tela.
- `FontFamily`/`Typeface` por peso; Oswald 700 não pode ser negrito sintético. Testar `Paint.hasGlyph` de “5,00 km · 31:04 · 5:58 /km” + “ç/ã/á” e `tnum` por fonte; se uma display não tiver tnum, a arte estática aceita números proporcionais (a regra de tnum continua no vídeo).
- OFL: NOTICE no APK (`assets/licenses` ou `res/raw`) e no repositório.

### 2.6 Persistência

- `ArtRecipe` puro em `core:domain`: `ArtFormat`, `ArtFont`, `ArtColor` (texto/traçado), extras ≤2.
- `ArtRecipeCodec` em `app/story`: JSON versionado, fallback para enum desconhecido, máx. 2/sem duplicado.
- `ArtRecipeStore` em `SharedPreferences` **`meucoach_art`** (não DataStore), com `reset()` próprio para não limpar `meucoach_story`.
- Switch **“Salvar como padrão”** ligado por default; grava ao **Salvar PNG** ou **Compartilhar**; “Restaurar padrão” na aba Arte.
- Extra indisponível é filtrado na abertura, como o Studio faz com o vídeo.

### 2.7 Export e share

- Extrair `GalleryImagePublisher` do `publishImage` privado de `CanvasStoryVideoExporter`:

```kotlin
data class ImagePublishResult(val uri: Uri, val savedToGallery: Boolean)
interface GalleryImagePublisher {
    fun publish(file: File): ImagePublishResult
}
```

- **Android 12+:** MediaStore `Pictures/MeuCoach`, `IS_PENDING`, PNG; sem `WRITE_EXTERNAL_STORAGE` e sem branch `<Q`.
- Preservar o contrato atual de capa/pôster: falha de publicação de imagem não falha o vídeo.
- `StoryFileNames.art(plan, format, stamp)` → `...-arte-4x5.png` / `...-arte-1x1.png`.
- **Compartilhar** é o CTA primário; **Salvar PNG** secundário; ambos salvam.
- Reuso: `ArtExportKey(activityId, recipe, format)`; se o mesmo key e o arquivo existir, compartilha o URI existente; se mudou/apagou, regenera. Não criar uma cópia por toque.

### 2.8 Prévia, estados e acessibilidade

- Preview em ~540 px no maior lado; export em 1080. Debounce ~120 ms, um render em voo, cancelamento cooperativo, bitmap reutilizado/reciclado com segurança.
- Checkerboard escuro default + toggle claro/escuro + label “Fundo transparente”.
- `plan == null`/rota insuficiente → “Sem rota suficiente para a arte”; controles visíveis, Salvar/Compartilhar desabilitados.
- Erro de render/salvar → inline + “Tentar novamente”; sem fechar o Studio.
- Controles com 48 dp, 200% de fonte, 360 dp; swatches/fontes em `FlowRow`/`LazyRow`.
- TalkBack por grupo, `selected`, borda + check; prévia com `contentDescription` resumindo formato/cores/fonte. A prévia é **fixa no topo da aba Arte** e o restante rola, conforme o [ADR 0060](0060-art-png-9x16-layout.md).
- Aviso de contraste quando texto/traço não passar no claro ou escuro; não usar só cor.

### 2.9 Dois PNGs no mesmo Studio

- **Pôster automático (opaco):** gerado com o vídeo, 4:5, sem escolha do usuário.
- **Arte transparente (personalizada):** manual, 4:5/1:1, fundo transparente.
- Copy e nomes de arquivo distintos; uma linha no Studio/card explica a diferença.

## 3. Alternativas rejeitadas

| Alternativa | Motivo |
|---|---|
| 9:16 full-bleed opaco no MVP | O caso escolhido é card; evita novo layout e promessa de “qualquer story”. O 9:16 transparente entrou pelo ADR 0060. |
| `ROUTE_CROP` no MVP | Aspect variável, bbox degenerado e clamp sem spec; não é necessário para o card. |
| 7 fontes (com Barlow) | Contradiz o pacote de 6 e a regra de 2 famílias no picker; mais APK/testes. |
| `lime-pressed` como swatch | É estado pressionado, não cor de paleta; dois verdes próximos. |
| DataStore | Não existe no projeto; SharedPreferences + codec versionado resolve. |
| Auto-save a cada toque | Sobrescreve o padrão sem intenção; o vídeo usa ação explícita. |
| Manter `minSdk 26` e feature-gate | Complexidade de dois caminhos de export/share sem ganho; alvo declarado é Android 12+ (ADR 0059). |
| `WRITE_EXTERNAL_STORAGE` em 26–28 | Permissão nova para uma feature de share; desnecessária com API 31+. |
| Arte opaca/só fundo sólido | Abandona a premissa de sobrepor. |
| Sem aviso de contraste | Conflita com AGENTS/DESIGN_SYSTEM sem exceção explícita. |
| Sempre gerar novo PNG no share | Enche a galeria; reuso por key resolve. |
| 3 extras | Abre layout/testes sem demanda comprovada; 2 cobre os casos comuns. |

## 4. Consequências

### Boas

- Feature offline, determinística e barata; sem mapa, rede ou worker.
- Preview, pôster e vídeo compartilham a mesma resolução de métricas e a mesma data.
- Paleta e fontes ficam dentro do design system, sem tokens duplicados.
- Android 12+ não ganha permissão nova.
- Dois artefatos 4:5 ficam semanticamente distintos.

### Ruins / riscos

- **Alpha no Instagram:** premissa a validar; se falhar, entra toggle de fundo sólido. **Fechado 22/11/2026:** spike no aparelho — 3 amostras 1080 px do `StoryArtRenderer` sobre foto, via figurinha; **alpha preservado nas 3**. Fundo transparente por construção fica; o toggle de fallback não entra no MVP.
- **Prévia: bitmap full-res é caro; 540 px/debounce/recycle são obrigatórios.** *(Spike de preview/540/debounce segue pendente para a F5.)*
- **Fontes display:** glifos/tnum/700 precisam de teste; o APK cresce.
- **1:1/9:16:** precisam de spec visual própria; não copiar o pôster. O 9:16 segue o ADR 0060.
- **Contraste:** a arte é conteúdo do usuário; o aviso não garante legibilidade.
- **Extração do publisher:** mexe no fluxo de capa/pôster; exige regressão.
- **Duas imagens 4:5:** a copy precisa ser clara ou o usuário confunde.

## 5. Implementação e aceite

Ordem:

1. **F0** — PRD v0.3, este ADR/ADR 0059/0060, spec visual 4:5/1:1/9:16 e spikes (alpha/Instagram, fontes, preview).
2. **F1** — bugfix `localDate` (feito em 22/11/2026; teste de regressão).
3. **F2** — domínio `ArtRecipe`/`ArtFormat`/`ArtFont`/`ArtColor` + testes.
4. **F3** — 4 fontes novas + tokens/`McColors` + NOTICE + testes de glifo.
5. **F4** — `StoryArtRenderer` + goldens 4:5/1:1/9:16 + alpha por pixel.
6. **F5** — chrome/segmento do Studio + estado + prévia + a11y.
7. **F6** — `GalleryImagePublisher` + salvar/compartilhar + reuso.
8. **F7** — `ArtRecipeCodec` + `ArtRecipeStore` integrados + polimento.
9. **F8** — docs/DoD.

Aceite:

- `assembleDebug`, `check`, `spotlessCheck`, `koverVerify`, lint/detekt verdes.
- `:app:verifyRoborazziDebug --tests "*Art*"` verde; alpha de fundo = 0 por pixel.
- `Paint.hasGlyph`/`tnum` nas 6 fontes; data “24 out 2026”.
- 4:5/1:1/9:16 sem distorção; sem rota → `null`; preview 540/export 1080.
- 360 dp/200%, TalkBack, checkerboard, aviso de contraste.
- Android 12+ (API 31+): MediaStore `Pictures/MeuCoach`; sem permissão de armazenamento.
- Vídeo/capa/pôster sem regressão; S24 Ultra + emulador API 31+.
- Sem dados reais; NOTICE OFL no APK/repo.

## 6. Referências

- [`PRD_ARTE_PNG_ROTA.md`](../PRD_ARTE_PNG_ROTA.md)
- `docs/DESIGN_SYSTEM.md` — cores, tipografia, AA, 48 dp
- `docs/SPORTS_SCIENCE.md` — métricas
- `docs/adr/0040-story-v3-recipe-and-map.md` — StoryRecipe, pôster 4:5, receita determinística
- `docs/adr/0015-story-video-map.md`, `docs/adr/0018-story-export-background.md`
- `app/src/main/kotlin/com/meucoach/app/story/StoryPosterRenderer.kt`
- `app/src/main/kotlin/com/meucoach/app/story/StoryVideoExporter.kt`
- `core/domain/src/main/kotlin/com/meucoach/core/domain/story/StoryRenderSpec.kt`
