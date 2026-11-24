# ADR 0062 — Traçados da rota no vídeo, arte e pôster

**Status:** Aceito e implementado em 24/11/2026 (validação visual no aparelho pendente)
**Data:** 2026-11-24
**Responsável:** usuário 0 (decisão de produto) e implementação
**Documentos de origem:** [`ADR 0015`](0015-story-video-map.md), [`ADR 0040`](0040-story-v3-recipe-and-map.md), [`ADR 0058`](0058-art-png-route.md), [`ADR 0060`](0060-art-png-9x16-layout.md), [`ADR 0061`](0061-art-photo-studio.md), `docs/DESIGN_SYSTEM.md`, `docs/PRD_ARTE_PNG_ROTA.md`

---

## 1. Contexto

O Story Studio desenhava a rota com um único estilo em todos os outputs:

- **Vídeo:** `StoryRoutePainter` desenhava linha sólida com casca escura, base e trecho percorrido em lima (ou cor da zona).
- **Arte PNG/foto:** `StoryArtRenderer` desenhava casca a 70% + linha de 14 px + marcadores; só a cor era configurável (`ArtRecipe.routeColor`).
- **Pôster:** `StoryPosterRenderer` tinha uma composição própria de halo + casca + linha, também fixa.

O usuário 0 pediu escolha de traçado no vídeo, na arte transparente e na imagem com foto. O objetivo é dar variedade visual sem transformar o Studio em um editor gráfico e sem quebrar a identidade da marca.

---

## 2. Decisão

### 2.1 Catálogo fechado de quatro estilos

`core:domain` ganha `StoryRouteStroke`:

| Valor | Leitura | Uso principal |
|---|---|---|
| `SOLID` | linha contínua com casca | default; melhor leitura sobre mapa carregado |
| `NEON` | halo suave atrás da linha | mapa escuro e foto |
| `DASHED` | travessões com casca | arte/foto, leitura editorial |
| `DOTTED` | pontos redondos com casca | arte/foto, visual minimalista |

O catálogo é fechado. Gradiente ao longo do path, espessura variável (tapered), setas de direção e linhas animadas por shader ficam fora desta decisão: exigem mais mudanças no renderer e nos goldens, sem ganho proporcional no MVP.

### 2.2 Um valor por receita, compartilhado por todos os outputs

- `StoryRecipe.routeStroke` e `ArtRecipe.routeStroke`, default `SOLID`.
- `ArtRecipe.toStoryRecipe()` repassa o valor, então a arte usa o mesmo caminho de resolução do vídeo.
- `StoryFrameRenderer` lê `spec.recipe.routeStroke`; `StoryPosterRenderer` lê a mesma receita; `StoryArtRenderer` lê `ArtRecipe.routeStroke`.
- Preview, export em background, capa, pôster, PNG transparente e JPG com foto não podem divergir.

### 2.3 Cores continuam separadas da geometria

- **Vídeo:** a cor segue a marca e as zonas de FC. O estilo muda o desenho, não a paleta; não há color picker no vídeo.
- **Arte/foto:** a cor do traçado continua sendo um dos 8 swatches, independente do texto; o estilo é ortogonal.
- **Pôster:** usa a paleta do story (lima + casca + coral dos marcadores).

### 2.4 Comportamento por superfície

- **Vídeo:** casca e base recebem o mesmo padrão do trecho percorrido; em tracejado/pontilhado a casca não vira uma linha escura contínua. O neon adiciona halo apenas no trecho percorrido, preservando o spotlight e as zonas.
- **Fase do tracejado:** `StoryRoutePainter` calcula a fase a partir da distância já percorrida em cada run de zona, para o padrão continuar em vez de reiniciar em cada fronteira.
- **Arte/foto:** casca e linha recebem o mesmo `PathEffect`; neon desenha um halo mais largo antes da casca. Marcadores de início/fim e ticks de km continuam iguais.
- **Pôster:** sólido mantém a composição atual; neon ganha halo extra; tracejado/pontilhado desenham casca e linha com o mesmo padrão, sem halo contínuo.

### 2.5 Persistência

- `StoryRecipeCodec` v2: v1 (sem o campo) decodifica como `SOLID`; campo desconhecido cai em `SOLID`; versão desconhecida cai na receita padrão. O payload continua abaixo de 2 KB.
- `ArtRecipeCodec` v3: v1/v2 decodificam como `SOLID`; o restante do codec continua tolerante por campo.
- `StoryRecipeStore` (`meucoach_story`) e `ArtRecipeStore` (`meucoach_art`) persistem o estilo junto da receita; “Restaurar padrão” volta a `SOLID`.
- Nenhuma migração de banco, nenhum dado novo de saúde e nenhuma dependência nova.

### 2.6 UI

- Aba **Vídeo:** card “ROTA · Traçado” com quatro pills de 48 dp, tag `story-route-stroke-*`, legenda que descreve o estilo selecionado.
- Aba **Arte:** seção “ESTILO DO TRAÇADO” dentro do card de cores, tags `art-route-stroke-*`.
- TalkBack anuncia nome + estado selecionado; a seleção não depende só de cor. O estilo entra no `produceState` da prévia, então muda ao toque.
- O resumo textual existente não ganhou o estilo para não alongar a linha; o estado fica visível nas pills e na prévia.

---

## 3. Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Manter só sólido | Não atende ao pedido e desperdiça um renderer que já trabalha com `Path` e `PathMeasure`. |
| Estilos diferentes por output | A prévia mentiria sobre o vídeo e o pôster; aumenta a matriz de testes e confunde a marca. |
| Gradiente/tapered/setas agora | Custo alto no Canvas, difícil manter determinístico no vídeo e nos goldens; fica para uma decisão futura. |
| Color picker no vídeo | Quebra a identidade e o significado das zonas de FC. A cor do vídeo continua sendo decisão de design. |
| Estilo como texto livre/LLM | O motor é determinístico; estilo é enum fechado, não geração. |

---

## 4. Consequências

**Boas**

- Quatro estilos com um único enum e uma única receita; preview, vídeo, arte e pôster não divergem.
- Zero dependência nova; o desenho continua em Canvas e determinístico.
- Codecs compatíveis com receitas antigas; jobs do WorkManager antigos continuam decodificando.
- Testes de renderer provam que cada estilo produz pixels distintos; UI cobre seleção e TalkBack.

**Ruins / limites**

- Mais quatro entradas de UI e quatro tags de teste; o Studio fica um pouco mais longo.
- Tracejado/pontilhado sobre mapa perdem parte da leitura de “progresso” do vídeo; a cor e os ticks continuam compensando.
- O neon depende de alpha e pode ficar mais sutil no reencode do Instagram; a validação visual no aparelho é gate.
- A arte transparente não promete contraste AA sobre qualquer foto; o aviso existente continua valendo.

---

## 5. Arquivos tocados

- `core/domain/story/StoryRouteStroke.kt` (novo), `StoryRecipe.kt`, `ArtRecipe.kt`
- `app/story/render/StoryRouteStrokePaint.kt` (novo), `StoryRoutePainter.kt`, `StoryRouteRenderer.kt`
- `app/story/StoryFrameRenderer.kt`, `StoryPosterRenderer.kt`, `StoryArtRenderer.kt`
- `app/story/StoryRecipeCodec.kt`, `ArtRecipeCodec.kt`
- `app/ui/activitydetail/StoryStudioSheet.kt`, `StoryStudioComponents.kt`, `ArtStudioContent.kt`, `ActivityDetailScreen.kt`, `ActivityDetailViewModel.kt`
- Testes: domínio, codecs/stores, renderers, ViewModel, UI e goldens (`art-png-4x5-{neon,dashed,dotted}.png`, `art-photo-4x5-dashed.png`, `story-map-dashed-dark.png`, `story-studio-route-strokes-dark.png`).
- A prévia sem mapa usa `textTertiary` na base da rota (alpha 220) para o estilo escolhido continuar legível; com mapa, a base continua `mapBaseRoute`.

---

## 6. Evidências e verificação

- `./gradlew :core:domain:test` e `:app:testDebugUnitTest` verdes.
- `./gradlew :app:verifyRoborazziDebug --tests "*Art*"` e `...StoryStudioScreenshotTest` verdes; goldens novos revisados.
- `StoryArtRendererTest`, `StoryFrameRendererTest` e `StoryPosterRendererTest` comparam os quatro estilos com `Bitmap.sameAs`.
- Sem dados reais no diff; sem permissão nova; sem rede nova.
