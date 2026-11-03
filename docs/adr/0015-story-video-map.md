# ADR 0015 — Mapa real no story de treino (OpenStreetMap + MapLibre)

**Status:** aceito para implementação — substitui a decisão de rota apenas offline em `ADR-0012`.
**Data:** 2026-11-02
**Responsável:** planejamento com Rafael (usuário 0)

---

## 1. Contexto

O protótipo do `P3-05` (`ADR-0012`) gera um vídeo 9:16 com uma rota animada sobre um fundo escuro (`Canvas` + `MediaCodec`). A validação no aparelho real confirmou que o desempenho é bom e a qualidade atende ao objetivo de "postar com vontade".

O usuário 0 (Rafael) solicita agora uma evolução: o vídeo deve mostrar um **mapa real da região do treino** (como nos apps Strava, Komoot, AllTrails), com o **local de partida real** e a rota animada km a km, mantendo os cards de split (`pace`, `FC média`, `kcal`). Ao final do percurso, deve aparecer uma **tela de encerramento** com os dados do treino e o branding `MeuCoach`.

A mudança é significativa porque:
- Introduz uma **biblioteca de mapas** no APK (`AGENTS.md` regra 8: toda dependência precisa ser justificada com peso, licença e manutenção).
- Muda a fonte de tiles de "offline, sem rede" (`ADR-0012`) para uma fonte de mapa real (`OpenStreetMap`).
- Adiciona um **frame final** (3 s) ao renderer e ao `StoryPlanCalculator`.
- Exige **logo/branding** no vídeo (logo será criada posteriormente via LLM com prompt estruturado).

---

## 2. Decisão

### 2.1 Fonte do mapa: OpenStreetMap (`tile.openstreetmap.org`)

**Escolha:** tiles raster do `OpenStreetMap` (`https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png`).

**Justificativa:**
- **Sem custo:** uso gratuito, sem chave de API, sem limite de chamadas para volumes moderados (o vídeo é gerado sob demanda, não em massa).
- **Validado pelo mercado:** usado por `Komoot`, `AllTrails` (base), `Strava` (base antes do Mapbox) e diversos apps de ciclismo/corrida.
- **Precisão:** as coordenadas GPS (`latitude`/`longitude`) da atividade são usadas diretamente para calcular o `bounding box` e o nível de zoom (`zoom` derivado da extensão da rota). A região renderizada corresponde exatamente ao treino.
- **Sem dados pessoais enviados:** o `bounding box` é calculado localmente; nenhuma API recebe coordenadas exatas do usuário (apenas o pedido de tiles para uma região aproximada, sem identificação).

**Alternativa rejeitada — `Mapbox` / Google Maps SDK:** exige chave de API, tem limite gratuito baixo e gera custo após o limite. Não atende ao requisito "sem custo".

**Alternativa rejeitada — `FFmpeg` com filtro `overlay`:** `FFmpeg` não renderiza tiles de mapa; exigiria uma solução complexa de captura de frames externos, pesada e frágil para o MVP.

---

### 2.2 Biblioteca Android: `MapLibre Native` (`org.maplibre.gl:android-sdk`)

**Escolha:** `org.maplibre.gl:android-sdk` (versão estável mais recente compatível com AGP 9.4.1 / minSdk 26).

**Justificativa:**
- **Open source (BSD-2-Clause):** sem restrições comerciais.
- **Leve comparado ao Mapbox:** o APK aumenta em ~3–5 MB (aceitável para uma feature de exportação visual; `AGENTS.md` regra 8 justificada pelo impacto de produto).
- **Suporta renderização para `Bitmap`:** `MapLibreMap.snapshot()` ou renderização em uma `Surface` permite gerar uma `Bitmap` estática do mapa para um `bounding box` definido pelo usuário.
- **Validado pelo mercado:** usado por apps como `Komoot`, `Organic Maps`, `BikeComputer`.

**Consequência:** a dependência será adicionada ao `gradle/libs.versions.toml` e ao `build.gradle.kts` do `:app`. Se a versão mais recente tiver incompatibilidade com `minSdk 26` ou `compileSdk 37`, avaliaremos uma versão anterior (`11.x` ou `10.x`) em um `ADR` futuro (`0016`?).

---

### 2.3 Modo de renderização: `Bitmap` de fundo estático + animação de rota no `Canvas`

**Decisão técnica:** o vídeo não renderiza tiles de mapa **por frame** (seria extremamente lento: 720 frames × renderização de mapa). Em vez disso:

1. **Antes da exportação**, o `StoryVideoExporter` calcula o `bounding box` da rota (`minLat`, `maxLat`, `minLon`, `maxLon`) a partir dos `GeoPoint` (`StoryPlanCalculator`).
2. **Uma única `Bitmap` de mapa** (resolução alta, ex.: 1080×1920 ou maior, cortada para 720×1280) é renderizada via `MapLibre` para esse `bounding box`, com `zoom` derivado da extensão da rota (`zoom ≈ 13–16` para corridas urbanas, `zoom ≈ 11–12` para rotas longas).
3. **Todos os frames do vídeo** usam essa `Bitmap` como `background` no `Canvas` (`canvas.drawBitmap(mapBitmap, 0f, 0f, null)`).
4. **A rota** (`RouteData.points`) é desenhada por cima com `drawPath`, `PathMeasure` e animação de progresso, exatamente como no `StoryFrameRenderer` atual.
5. **O mapa é estático**; a rota se move. Isso é o padrão de vídeos de treino (`Strava`, `Nike Run Club`, `Runkeeper`) — o usuário vê a região real e o percurso animando sobre ela.

**Justificativa:**
- **Performance:** renderizar uma `Bitmap` uma vez é rápido; usar como background é simples (`Canvas.drawBitmap`).
- **Qualidade:** o mapa é nítido (resolução nativa) e não sofre de artefatos de renderização dinâmica.
- **Offline parcial:** a `Bitmap` pode ser salva em cache (`FileCacheDir`) para reutilização se o mesmo treino for exportado novamente; mas a geração inicial exige uma chamada de rede para baixar os tiles (`tile.openstreetmap.org`).

**Consequência:** o vídeo não é 100% offline no momento da geração (exige rede para os tiles). Como o usuário aceita compartilhamento (`resposta 2`) e quer precisão (`resposta 1`), isso é aceitável. Se o usuário quiser totalmente offline no futuro, avaliaremos `.mbtiles` pré-baixados em `ADR-0016`.

---

### 2.4 Design do vídeo atualizado

**Frame de animação (durante o percurso):**
- **Background:** `Bitmap` do mapa real (`OSM`) para a região do treino.
- **Rota:** `Path` animada (`drawPath` com `PathMeasure`), como hoje, mas agora sobre o mapa real. A rota usa as coordenadas `GeoPoint` (`latitude`/`longitude`) projetadas no `bounding box` do mapa, garantindo que o traçado corresponda ao local real.
- **Cards por km (`CARD`):** mantidos (`pace`, `FC`, `kcal`), com posição e cores do design system (`lima` para destaque, `coral` para fisiologia).
- **Ponto de partida:** um emoji de corredor (`🏃` ou `🏃‍♂️`/`🏃‍♀️`, conforme o perfil) no início da rota, substituindo o ponto simples atual (`drawCircle` com cor `textPrimary`).
- **Marca de progresso:** ponto animado (`accent`) percorre a rota, como hoje.

**Frame final (`FINAL`) — 3 segundos:**
- **Tipo:** `StorySegmentType.FINAL` (novo enum no `StorySegmentType`).
- **Duração:** `3.0f` segundos (`CARD_SECONDS_FINAL = 3.0`).
- **Layout (9:16, 720×1280):**
  - **Fundo:** mesmo `Bitmap` do mapa (ou uma versão escurecida, com `alpha` reduzido para não competir com o texto).
  - **Dados do treino (centralizados, parte superior/intermediária):**
    - `Distância`: `String.format("%.2f", plan.distanceKm)` + `" km"`
    - `Tempo`: `formatDuration(plan.durationTotalSec)`
    - `Pace`: `plan.splits.firstOrNull()?.pace?.format()` ou `plan.splits.lastOrNull()?.pace?.format()`? Melhor: `pace médio` derivado de `distanceKm / (durationTotalSec / 60.0)`.
    - `FC média`: média dos `heartRate` disponíveis nos samples (se houver); caso contrário, `—`.
    - `Kcal`: `plan.calories?.toString()` ou `—`.
  - **Branding (parte inferior, centralizado):**
    - Nome: `"MeuCoach"` em fonte `Geist` (display), tamanho grande (`36f` ou `42f`), cor `textPrimary`.
    - Logo: será adicionada posteriormente (`resposta 3`). O `StoryFrameRenderer` deve aceitar um `Bitmap?` opcional para a logo; enquanto não existe, renderiza apenas o texto.
  - **Estilo:** card escuro (`surface/default` com `alpha` 245) sobre o mapa, ou texto direto sobre o mapa com sombra leve (`Paint` com `shadowColor` e `shadowRadius`). A decisão de layout exato será feita quando o logo for criado.

---

### 2.5 Atualizações no renderer (`StoryFrameRenderer`)

**Mudanças no `drawRoute`:**
- Adicionar `Bitmap?` de mapa (`mapBackground`) ao `StoryFrameRenderer`.
- Se `mapBackground != null`: `canvas.drawBitmap(mapBackground, 0f, 0f, null)` antes de `drawHeader` e `drawRoute`.
- A rota (`routePath`) continua desenhada com `drawPath`, mas agora os pontos (`RoutePoint.x`, `.y`) precisam ser mapeados para a posição no `mapBackground`. Como o `mapBackground` já está no mesmo `bounding box` usado para normalizar `RoutePoint`, a correspondência é direta (`left + point.x * side`, `top + point.y * side`).

**Mudança no ponto de partida:**
- Substituir o `drawCircle` simples do início da rota (`plan.route.start`) por um emoji (`String` com `"🏃"` ou `"🏃‍♂️"`).
- O emoji é desenhado via `canvas.drawText` com uma fonte que suporte emojis (`Typeface.DEFAULT` ou `Typeface.create`).

**Mudança no footer:**
- Manter `"Gerado no aparelho · MeuCoach"` (como é hoje), mas agora com a opção de incluir o logo quando disponível.
- Se `logoBitmap != null`: desenhar `logoBitmap` à esquerda ou ao lado do texto.

**Nova função `drawFinal`:**
- Chamada quando `segment?.type == StorySegmentType.FINAL`.
- Desenha os dados (`distance`, `duration`, `pace`, `fc`, `calories`) e o branding (`MeuCoach` + logo opcional).

---

### 2.6 Atualizações no planner (`StoryPlanCalculator`)

- Adicionar `StorySegmentType.FINAL` ao enum.
- Adicionar `FINAL_SECONDS = 3.0` como constante.
- No `timeline`, se `splits` não estiver vazio, adicionar um segmento final após o último `CARD` ou após o `MOVE` final:
  ```
  segments += StorySegment(
      startSec = cursor.toFloat(),
      endSec = (cursor + FINAL_SECONDS).toFloat(),
      type = StorySegmentType.FINAL,
      progressStart = 1f,
      progressEnd = 1f,
  )
  ```
- Normalizar o `endSec` do último segmento para `targetDurationSec` (como já é feito), mas agora considerando que o `FINAL` ocupa `3.0` segundos do total de `30`. Se o usuário quer `30` segundos totais, o `FINAL` deve ser incluído no cálculo, ou ser um segmento adicional após os 30 segundos?

**Decisão:** o usuário quer que o vídeo termine com o frame final. Se o vídeo tem `30` segundos padrão (`targetDurationSec = 30`), o frame final deve ser parte desses 30 segundos, não além deles. Portanto, o `timeline` deve reservar `FINAL_SECONDS` do total:
- `FINAL_SECONDS = 3.0`
- `CARD_SECONDS` e `MOVE` são calculados sobre `(targetDurationSec - FINAL_SECONDS)`.
- O `FINAL` é o último segmento, com `endSec = targetDurationSec`.

Se `targetDurationSec` for menor que `FINAL_SECONDS` (ex.: `10`), o `FINAL` ainda é incluído, mas o restante do vídeo é comprimido. Se `targetDurationSec < FINAL_SECONDS`, o vídeo é apenas o `FINAL` (3 segundos mínimos).

---

### 2.7 Atualizações no exporter (`StoryVideoExporter`)

- Antes de iniciar a codificação (`encode`), calcular o `bounding box` a partir de `plan.route` (ou `plan.splits`, que têm `progress`, mas não `latitude`/`longitude`).
- Como `StoryPlan` não armazena `latitude`/`longitude` diretamente (apenas `RoutePoint` normalizado), precisamos passar os `samples` originais (`ImportedActivity`) para o `StoryVideoExporter`, ou adicionar `boundingBox` ao `StoryPlan`.

**Proposta:** adicionar `boundingBox: BoundingBox?` ao `StoryPlan`, calculado no `StoryPlanCalculator` a partir dos `GeoPoint` (`samples`).

```kotlin
data class BoundingBox(
    val minLat: Double, val maxLat: Double,
    val minLon: Double, val maxLon: Double,
)
```

No `export`:
```kotlin
val mapBitmap = if (plan.boundingBox != null) {
    MapBackgroundRenderer.render(
        boundingBox = plan.boundingBox,
        width = 720,
        height = 1280,
        scale = 2f, // alta resolução
        zoom = calculateZoom(plan.boundingBox),
        urlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
    )
} else null
```

O `mapBitmap` é passado para cada `StoryFrameRenderer.render(...)`.

---

### 2.8 Logo (branding)

- A logo será criada posteriormente (`resposta 3`).
- O `StoryFrameRenderer` aceita um parâmetro opcional `logoBitmap: Bitmap?` no construtor.
- Quando `logoBitmap != null`, o `drawFooter` desenha a logo ao lado do texto `"MeuCoach"`.
- Se `logoBitmap == null`, apenas o texto é desenhado (`drawFooter` atual, com `"MeuCoach"` explícito).
- A posição da logo será definida quando o arquivo for criado; por padrão, `logoBitmap` é desenhado à direita do texto do footer, com tamanho `32f * scale`.

---

## 3. Alternativas rejeitadas

1. **`Mapbox` (SDK oficial):** exige chave de API, limite gratuito baixo (`50k` tiles/mês), custo após o limite. Não atende ao requisito "sem custo" (`resposta 1`).
2. **`FFmpeg` com `filter_complex` + `overlay`:** `FFmpeg` não renderiza tiles de mapa; exigiria uma solução externa para baixar imagens estáticas e combiná-las, que seria mais complexa e pesada que `MapLibre`.
3. **Renderização dinâmica de mapa por frame (`MapLibre` em cada frame do `MediaCodec`):** extremamente lento (`720` frames × `MapLibre` render). Rejeitado por desempenho.
4. **`Canvas` puro com tiles desenhadas manualmente (`TileLoader` customizado):** exigiria reimplementar um cliente `OSM` do zero (`TileLoader`, `TileCache`, `Projection`), o que viola a regra 8 (preferir bibliotecas estáveis) e aumenta o risco de bugs.

---

## 4. Consequências

### Boas
- O vídeo do `P3-05` passa a mostrar uma **rota real sobre mapa real**, aumentando o valor percebido para o usuário (`"postar com vontade"`).
- A região é precisa porque usa as coordenadas GPS da atividade (`bounding box` derivado de `GeoPoint`).
- `OpenStreetMap` é gratuito e sem chave, atendendo ao requisito de custo zero.
- `MapLibre` é open source (`BSD-2-Clause`) e validado pelo mercado.
- A arquitetura (`Bitmap` estática + `Canvas` animado) preserva o desempenho já validado no aparelho.

### Ruins / Riscos
- **Peso do APK:** `MapLibre` adiciona `~3–5 MB`. Justificado pelo impacto de produto (`AGENTS.md` regra 8), mas precisa ser monitorado.
- **Rede necessária para tiles:** a geração do vídeo exige uma chamada a `tile.openstreetmap.org`. Se o usuário estiver sem rede no momento do export, o vídeo falha (mostra erro no `StoryFrameRenderer`, como já é feito para `MediaCodec` quando `YUV` falha). O usuário aceita isso (`resposta 2`).
- **Dependência de versão:** `MapLibre` precisa ser compatível com `minSdk 26`, `compileSdk 37` e `AGP 9.4.1`. Se a versão mais recente tiver problemas, um `ADR` futuro (`0016`) avaliará uma versão anterior.
- **Logo:** ainda não existe. O `StoryFrameRenderer` está preparado (`logoBitmap: Bitmap?` opcional), mas o branding só será completo após a criação do arquivo.
- **Mudança no `ADR-0012`:** este ADR (`0015`) substitui partes de `ADR-0012` (fonte de mapas, privacidade, renderização). `ADR-0012` não é removido, mas marcado como `Substituído parcialmente por ADR-0015`.

---

## 5. Próximos passos (após aceitação deste ADR)

1. **Atualizar `docs/DECISIONS.md`:** registrar `ADR-0015` e marcar `ADR-0012` como `Substituído parcialmente`.
2. **Atualizar `docs/ROADMAP.md`:** adicionar `P2-05 Mapa interativo` como `Em andamento` se aplicável, ou manter `P3-05` atualizado.
3. **Criar `docs/adr/0015-story-video-map.md`:** este arquivo.
4. **Implementar `BoundingBox` e `StoryPlan` atualizado.**
5. **Implementar `MapBackgroundRenderer` (ou `MapLibreBitmapRenderer`).**
6. **Atualizar `StoryFrameRenderer` para aceitar `mapBackground` e `logoBitmap`.**
7. **Atualizar `StoryPlanCalculator` para incluir `boundingBox` e `FINAL` segment.**
8. **Atualizar `StoryVideoExporter` para gerar `Bitmap` do mapa antes da exportação.**
9. **Criar logo (`MeuCoach`) via LLM (ChatGPT/Gemini) com prompt estruturado, conforme `resposta 3`.**
10. **Validar no aparelho real (`resposta 8`): desempenho, qualidade do mapa, precisão da região e frame final.**
11. **Atualizar `docs/PROGRESS.md`.**

---

## 6. Nota de implementação — entrega 1 (03/11/2026)

**Escopo entregue:** `BoundingBox` no plano e frame final de 3 s, apenas `Canvas` — **sem dependência nova**. O mapa real (`MapLibre`) veio na entrega 2.

- `StoryPlanCalculator` passou a expor `boundingBox`, `averagePace` e `averageHeartRate`.
  - O `boundingBox` usa todos os pontos GPS (não só a rota amostrada) para não cortar extremos do traçado.
  - Pace e FC médias preferem os campos canônicos de `ImportedActivity`; quando ausentes, derivam de `durationSec / distância` e da média dos samples. Sem dado, o campo fica `null` e o frame desenha `—`.
- Novo `StorySegmentType.FINAL` com `FINAL_SECONDS = 3.0`, **reservado dentro** da duração alvo (padrão 30 s → movimento/cards nos 27 s iniciais).
  - Duração mínima de 3 s: alvos menores que o frame final geram só o encerramento.
  - O `FINAL` também aparece em treinos sem km completo (sem cards), ao contrário da condição de `splits` não vazio no §2.6 — o vídeo sempre termina com o encerramento.
- `StoryFrameRenderer.drawFinal` desenha o painel com `TREINO CONCLUÍDO`, ritmo médio, FC média e calorias; distância e tempo continuam no cabeçalho para não haver salto de layout na transição, e a logo permanece no rodapé.
- Métricas do frame final: `—` quando `null`, nunca zero (`AGENTS.md` §3.7).
- Validado com golden `story-final-dark.png` e testes de unidade do planner (bounding box, duração mínima e valores canônicos).

## 7. Nota de implementação — entrega 2 (03/11/2026)

**Escopo entregue:** mapa real com `MapLibre` + `OpenStreetMap`, gerado uma vez por story e reaproveitado em todos os frames.

- **Versão:** `org.maplibre.gl:android-sdk:12.3.1` — a linha **13.x exige Vulkan** (`uses-feature android.hardware.vulkan.version required=true`), o que restringiria aparelhos compatíveis. `12.3.1` é a última linha OpenGL (minSdk 23) e atende `minSdk 26`/AGP 9.4.1.
- **Tamanho real:** a AAR adiciona ~13,2 MB (arm64-v8a) e ~13,6 MB (x86_64) de biblioteca nativa, bem acima da estimativa inicial de 3–5 MB. Para conter o APK, `abiFilters` limita o app a `arm64-v8a` e `x86_64`; 32 bits fica fora do MVP. O APK de debug passou de ~24 MB para ~49 MB com as duas ABIs.
- **Geração:** `MapLibreBackgroundRenderer` usa `MapSnapshotter` com estilo raster OSM inline (sem chave/API), `withLogo(false)`/`withAttribution(false)` e câmera calculada por `StoryMapCameraCalculator`. O snapshot é criado no `createStory` (prévia já mostra o mapa) e guardado no estado `Preview` para o export não baixar tiles duas vezes.
- **Alinhamento:** `StoryMapCameraCalculator` projeta o `BoundingBox` no mesmo quadrado usado por `drawRoute` (`routeSquare`), com Mercator e o offset vertical do cabeçalho. A matemática é pura e testada sem carregar a biblioteca nativa.
- **Legibilidade:** a foto OSM é dessaturada a 60% e recebe um scrim leve (alpha 50 na animação, 90 no `FINAL`) + gradientes no topo/rodapé. O enquadramento usa **1,2× de zoom** sobre o quadrado da rota, para o mapa não parecer distante, mantendo o traçado alinhado às ruas.
- **Rede:** sem conexão, o `createStory` mostra "Não foi possível baixar o mapa. Verifique a conexão e tente novamente." e não gera o vídeo — o fallback offline do `ADR-0012` foi substituído conscientemente. Há timeout de 30 s.
- **Privacidade e licença:** apenas tiles da região aproximada vão para `tile.openstreetmap.org`; coordenadas exatas nunca são enviadas. A atribuição `© OpenStreetMap` é desenhada em todo frame com mapa, e as permissões de localização herdadas do MapLibre foram removidas no manifest (`tools:node="remove"`).
- **Testes:** 5 testes da câmera, testes do `ActivityDetailViewModel` (mapa no preview, tamanho do frame, falha de rede) e goldens sintéticos `story-map-dark.png`/`story-final-map-dark.png` (grade de ruas, sem dados reais).

**Pendente:** validar em aparelho real a geração do snapshot, a qualidade/zoom do OSM, o alinhamento da rota, o tempo de export e o APK de release; o ponto de partida em emoji (§2.5) também segue pendente.

## 8. Atualização — mapa vetorial e 1080p (03/11/2026)

A primeira versão do mapa usava raster do `tile.openstreetmap.org` em 720×1280. No aparelho, os labels ficaram pequenos/borrados e não dava para reconhecer a região. A entrega foi ajustada sem mudar a arquitetura (snapshot + Canvas):

- **Fonte:** `OpenFreeMap` (`https://tiles.openfreemap.org/styles/liberty`, dados OSM). É gratuito, sem chave, sem cadastro e sem limite de visualizações; o estilo vetorial mantém ruas e labels nítidos em qualquer resolução. O fallback é o style raster OSM inline, caso o download do style falhe.
- **Labels:** `OpenFreeMapStyle` baixa o style e amplia/clareia os textos de lugar, rua, água e POI (14–28 px, halo branco) antes de entregar o JSON ao `MapSnapshotter`. Sem isso, o estilo padrão desenha labels de 10 px, ilegíveis no vídeo.
- **Resolução:** o vídeo passou de 720×1280 para **1080×1920** (H.264, 24 fps, 8 Mbps). O snapshot do mapa acompanha o tamanho do frame; o layout já era escalável (`scale = width / 720`).
- **Contraste:** saturação em 85%, sem scrim na animação e 80 no `FINAL`, mantendo os gradientes de topo/rodapé. A rota ganhou casing escuro e base cinza (`StoryPalette.mapCasing`/`mapBaseRoute`) para o lima continuar legível sobre um mapa claro.
- **Custo:** ~2,25× mais pixels por frame; o export fica mais lento e o MP4 de 30 s passa de ~15 MB para ~30 MB. O APK não cresce (o mapa é download em runtime). Timeout do mapa: 45 s.
- **Privacidade:** os requests vão para `tiles.openfreemap.org` com a região aproximada; coordenadas exatas continuam só no aparelho. A atribuição `© OpenStreetMap · OpenMapTiles · OpenFreeMap` é desenhada no frame.

**Pendente:** validar no aparelho os labels reais, o tempo de export em 1080p e a nitidez; se necessário, ajustar `MAP_ZOOM_FACTOR` (1,2) ou os tamanhos de label.
