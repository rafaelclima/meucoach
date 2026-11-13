# Story V3 — Descobertas e plano de ação

**Status:** aprovado para implementação · decisões do usuário 0 em 13/11/2026
**Documento de análise:** [`STORY_V3_ANALISE_E_DIRECIONAMENTO.md`](STORY_V3_ANALISE_E_DIRECIONAMENTO.md)
**Escopo:** elevar o story/vídeo do MeuCoach ao melhor nível possível no MVP
**Regra de ouro:** nada de áudio, nada de máscara de privacidade, nada de
satélite/Google no MVP. O foco é **controle de métricas, mapa autoral e
qualidade de publicação**.

---

## 1. Decisões tomadas

| # | Tema | Decisão | Consequência no plano |
|---|---|---|---|
| 1 | Mapa | **OpenFreeMap + estilo autoral agora; Protomaps depois** | Criar `MeuCoach Dark` e `MeuCoach Light`; deixar interface pronta para trocar tiles; não implementar PMTiles no MVP |
| 2 | Áudio | **Vídeo mudo**; o usuário coloca música no Instagram/TikTok | Nenhuma trilha AAC, nenhum sintetizador, nenhuma tarefa de áudio |
| 3 | Métricas | **Distância, ritmo médio e tempo total fixos**; até **2 extras** | Receita com essenciais imutáveis + extras opcionais; remover calorias por km; calorias totais viram extra |
| 4 | Privacidade | **Sem máscara de início/fim** | Nenhuma tarefa de privacidade; a rota completa é intencional |
| 5 | Qualidade | **O melhor disponível já no MVP** | 1080p30 16 Mbps, BT.709, H.264 High, mapa escuro, 3 keyframes, pôster 4:5, prévia |
| 6 | Sequência | **Sprint A primeiro** (controle/qualidade) | Etapa 1 começa por receita, métricas, safe area, encode e Story Studio |

### 1.1 Fora de escopo (explícito)

- Áudio, música, AAC, batida procedural.
- Máscara de privacidade, blur de casa, “sem mapa”.
- Google Maps, Mapbox, MapTiler no MVP.
- Satélite Esri no MVP (licença comercial em revisão).
- 3D terrain real (MapLibre Native ainda não implementa `terrain`).
- Vídeo 4:5, 1:1, 16:9; 4K; HDR.
- Protomaps/PMTiles implementado (só a interface de troca).
- LLM em qualquer texto ou decisão.
- Leitura do coach no vídeo público (continua privada na tela da atividade).
- Grafo social, feed, ranking, desafios.

---

## 2. Descobertas que definem o plano

### 2.1 O layout atual está atrás da UI do Reels

O `StoryLayout` reserva 13% no topo e 13% na base (≈250 px). Em 2026, o Reels
cobre ~420 px na base e o TikTok chega a ~484 px. O card termina em 84%
(1613 px) e a atribuição em 86,3% (1657 px): ambos dentro da zona morta.

**Implicação:** o safe area é pré-requisito de qualidade, não polish. O novo
layout usa **13% topo / 22% base / 5,5% lados**, card em **62–78%** e atribuição
dentro do card.

### 2.2 O MapLibre já expõe a projeção exata

O ADR 0015 concluiu que não havia projeção porque o `MapSnapshotter` não expõe
`Projection`. Mas o `MapSnapshot` entregue no callback tem:

```kotlin
public final native PointF pixelForLatLng(LatLng)
public final native LatLng latLngForPixel(PointF)
```

Confirmado por `javap` no AAR 12.3.1 em uso. **Implicação:** a rota pode ser
desenhada com a matriz exata do mapa, inclusive em snapshot inclinado, sem o
port `StoryMapProjection` e sem deriva. O port fica apenas como fallback/teste.

### 2.3 MapLibre 13.7.1 OpenGL é menor e mais capaz

O AAR `android-sdk-opengl-13.7.1` tem 15,8 MB; o `.so` arm64 tem 11,0 MB,
contra 13,2 MB do 12.3.1. Ganhos: `pixelForLatLng`, PMTiles, color-relief,
correções de estabilidade. O flavor padrão 13.x usa Vulkan; o OpenGL evita
exigir Vulkan.

**Implicação:** atualizar é ganho de tamanho e de capacidade, não custo.

### 2.4 Google Maps é proibido para exportação

Os Termos do Google Maps Platform proíbem exportar/extrair o conteúdo para uso
fora do serviço. Um MP4 com tiles do Google é exatamente isso. Não é questão de
plano pago.

**Implicação:** o caminho é OSM + estilo autoral. Para corrida, isso é melhor:
trilhas, parques e ciclovias importam mais que POIs.

### 2.5 O modelo já tem as métricas extras

`ImportedActivity` já expõe `elevationGainM`, `averageCadenceSpm`, `maxHeartRate`,
`calories`, `averageHeartRate`, `laps` e `startTime`. Não é preciso dependência
nem migração para as métricas do MVP.

### 2.6 O vídeo atual é fixo e tem dois bugs de honestidade

- 30 s, 9:16, 3 métricas no card, 3 no final, sem escolha.
- “Calorias” por km é o total dividido pela distância e aparece igual em todos
  os cards; deve sair.
- Em 10 km, `capCards` gera 5 cards (1, 3, 5, 7, 9), não 8; a barra de splits
  mostra só esses 5 e parece que a corrida teve 5 km.

**Implicação:** a receita e a timeline precisam ser refeitas junto com as
métricas; não é só trocar textos.

### 2.7 A prévia guarda um bitmap de ~42 MB

`createStory` renderiza o mapa em 2,25× (2430×4320) e mantém esse bitmap no
estado da `Preview` para exibir 260 dp. Isso é desperdício de memória e risco
em aparelho de 4 GB.

**Implicação:** prévia em 540×960 ou 1080×1920; o worker renderiza a versão
high-res. Estágio do mapa em WebP/JPEG 95, não PNG.

### 2.8 O encode não declara cor nem perfil

O `MediaFormat` atual não define BT.709/SDR/limited, perfil High, level, bitrate
mode. O Instagram reencoda tudo; um master com cor declarada e 16 Mbps chega
visivelmente melhor.

### 2.9 O que não vamos fazer agora

Áudio, privacidade, satélite, 3D terrain, 4K/HDR, 4:5 em vídeo. O MVP é
**9:16 + pôster 4:5**, mudo, com rota completa e mapa autoral.

---

## 3. Especificação do MVP

### 3.1 Vídeo

| Item | Valor |
|---|---|
| Formato | MP4, 9:16, 1080×1920 |
| Codec | H.264 High, Level 4.1 |
| FPS | 30 (padrão); 60 como opção “Suave” após validação |
| Bitrate | 16 Mbps VBR (10–20 Mbps conforme cena) |
| Cor | BT.709, SDR, limited range |
| Áudio | **Nenhum** |
| Metadados | **Sem GPS**; nunca chamar `MediaMuxer.setLocation` |
| Keyframe | 1 s |
| Duração | Auto (padrão), 15, 30, 45, 60 s |
| Nome | `meucoach-2026-11-13-5k.mp4` |

**Por que não 4K/1440p:** Instagram/TikTok entregam 1080p; 1440p triplica o
tempo de export e é downscalado. O ganho real vem de bitrate, cor declarada,
nitidez do mapa e safe area. 60 fps entra como opção se o encoder do aparelho
suportar; o padrão é 30 fps.

### 3.2 Métricas

**Essenciais (sempre, não desmarcáveis):**

1. `DISTANCE` — distância percorrida.
2. `AVG_PACE` — ritmo médio.
3. `TOTAL_TIME` — tempo total.

**Extras (até 2, escolha do usuário):**

| Extra | Fonte | Formato | No card por km? |
|---|---|---|---|
| `CALORIES_TOTAL` | `activity.calories` | `312 kcal` | Não |
| `ELEVATION_GAIN` | `elevationGainM` ou altitude | `+84 m` | Sim |
| `AVG_HR` | `averageHeartRate` ou samples | `152 bpm` | Sim |
| `MAX_HR` | `maxHeartRate` ou samples | `178 bpm` | Não |
| `CADENCE` | `averageCadenceSpm` ou samples | `172 spm` | Sim |
| `FASTEST_KM` | splits | `km 4 · 5:02` | Sim (selo) |
| `DATE` | `localDate` | `13 nov` | Não |

**Regras:**

- Limite rígido de 2 extras; a UI não permite a terceira.
- Métrica sem dado fica desabilitada com motivo (“sem FC”, “sem elevação”,
  “sem calorias”); nunca vira `0`.
- **Calorias por km são removidas** de `StorySplit` e de qualquer card.
- O card por km mostra o ritmo do split + até 2 extras que tenham valor por km
  (FC média, elevação, cadência). Extras só de resumo aparecem no final.
- `FASTEST_KM`, quando escolhido, adiciona o selo “KM MAIS RÁPIDO” no card do km
  correspondente e a linha no encerramento; sem o extra, o selo não aparece.
- O encerramento mostra **sempre** as 3 essenciais + até 2 extras.
- Se o usuário não escolher extras, o encerramento tem 3 colunas; com 1 extra,
  3+1; com 2, 3+2.
- **Escopo por km:** `AVG_HR`, `CADENCE` e `ELEVATION_GAIN` só aparecem no card
  se houver amostras suficientes; o total pode vir dos campos de resumo. Se não
  houver nem total nem amostras, o extra fica desabilitado.
- **Elevação:** o total usa `elevationGainM` quando existir; senão, deriva de
  `altitudeM` com histerese de 1 m e pelo menos 10 amostras. O valor por km usa
  o mesmo critério no trecho, nunca o total dividido pelos kms.

**Presets:**

| Preset | Extras |
|---|---|
| Essencial | nenhum |
| Performance | `FASTEST_KM`, `ELEVATION_GAIN` |
| Fisiológico | `AVG_HR`, `CADENCE` |
| Completo | `AVG_HR`, `CALORIES_TOTAL` |
| Personalizado | o que o usuário escolher |

Um preset nunca liga uma métrica indisponível: ele seleciona o subconjunto
disponível e deixa o slot livre. Se o preset pede 2 extras e só 1 existe, o
contador fica “1/2” e a métrica ausente aparece desabilitada com o motivo.

### 3.3 Layout

**9:16 (Reels/TikTok/Stories):**

- Topo seguro: 13% (≈250 px).
- Base segura: 22% (≈422 px).
- Lados: 5,5% (≈59 px).
- Card: 62%–78%.
- Atribuição do mapa: dentro do card (rodapé) ou acima dele; nunca abaixo de
  78%.
- Header: distância à esquerda, tempo total à direita; logo no topo.
- Rota: moldura entre 26% e 76%, recalculada para o card novo.
- Encerramento: 3 essenciais + 2 extras; manchete; prova; logo/tagline.

**4:5 (pôster de feed):**

- 1080×1350.
- Rota como arte (sem mapa/labels), fundo escuro com gradiente.
- Distância, ritmo médio e tempo em tipografia grande.
- Data e “MeuCoach” pequenos.
- Capa 9:16 separada para o story.

### 3.4 Mapa

- **MapLibre 13.7.1 OpenGL** (`org.maplibre.gl:android-sdk-opengl`).
- Tiles **OpenFreeMap** (`tiles.openfreemap.org`), estilo `liberty` customizado.
- **MeuCoach Dark** (padrão): fundo grafite, água azul-petróleo, parques verde
  dessaturado, labels brancos com halo escuro, vias principais finas, POIs,
  transporte, aeroportos e vias menores ocultos.
- **MeuCoach Light** (opcional).
- **Terrain** (opcional, hillshade Mapzen) mantido se estável.
- **Satellite** fora do MVP por licença.
- Rota: casing escuro + base cinza + trecho percorrido em lima/zonas; ticks por
  km; pin com foto.
- **Projeção exata** via `pixelForLatLng`; `StoryMapProjection` só fallback.
- **3 keyframes:** abertura 30°, follow 42°, final 35°; cortes escondidos nos
  cards; no máximo 2 snapshots vivos.
- **Supersampling:** 2,25× no follow; 1,5–1,75× nos demais; 1,25× em low-RAM.
- **Cache:** style JSON em disco; snapshots em WebP/JPEG 95; manifesto com
  pixels da rota; LRU.
- **Protomaps seam:** `StoryTileSource`/`StoryStyleProvider` para trocar a
  fonte depois sem tocar no renderer.

### 3.5 Narrativa

- Timeline por esforço:
  - card base 1,8 s;
  - km mais rápido +0,6 s;
  - km de maior FC +0,3 s;
  - km mais lento −0,3 s;
  - clamp 1,2–3,0 s;
  - encerramento 3,0 s (3,5 s com recorde/prova, fast-follow).
- Duração auto: `clamp(15, 45, 8 + min(km, 12) * 2)`; o usuário pode escolher
  15/30/45/60. Se a duração alvo não couber com 8 cards dentro do clamp mínimo,
  reduzir a quantidade de cards (mínimo 4) antes de violar o clamp; o
  encerramento nunca é cortado.
- Cards: no máximo 8, sempre incluindo o primeiro km, o último km completo, o
  mais rápido e o mais lento.
- Barra de splits: **todos** os kms; os com card ficam acesos; para 21+ km,
  agrupar em blocos de 2–5 km com rótulo.
- Card: “KM 3 · 5:02”, selo “KM MAIS RÁPIDO”, extras por km.
- Encerramento: manchete de destaque (negative split, ritmo constante, km mais
  rápido, corrida concluída) + prova curta. Sem “CORRIDA CONCLUÍDA” competindo
  com a manchete.
- **Recorde/prova/primeira corrida** ficam para o Sprint 3 (fast-follow), não
  bloqueiam o MVP.

### 3.6 Story Studio

Tela modal aberta pelo card “Criar story” no detalhe da atividade:

```
┌──────────────────────────────────────────────┐
│  Story do treino                    [fechar] │
│                                              │
│  ┌──────────────┐   Prévia 9:16              │
│  │   frame      │   [Abertura] [Km] [Final]  │
│  └──────────────┘   scrubber                 │
│                                              │
│  [Essencial] [Performance] [Fisiológico] ... │
│                                              │
│  OBRIGATÓRIAS                                │
│  Distância · Ritmo médio · Tempo total       │
│                                              │
│  EXTRAS (0/2)                                │
│  [Calorias totais] [Elevação] [FC média]     │
│  [FC máx] [Cadência] [Km mais rápido] [Data] │
│                                              │
│  DURAÇÃO   [Auto] [15s] [30s] [45s] [60s]    │
│  MAPA      [Escuro] [Claro] [Terreno]        │
│  QUALIDADE [Padrão 1080p30] (60fps depois)   │
│                                              │
│  [ ] Salvar como padrão                      │
│  [ Gerar vídeo ]                             │
└──────────────────────────────────────────────┘
```

- Sem seção de áudio; sem seção de privacidade.
- Extras desabilitados com motivo; contador “0/2”.
- Prévia com 3 frames + scrubber; mapa re-renderizado só se estilo mudar.
- “Salvar como padrão” em `SharedPreferences` (`meucoach_story`).
- Após gerar: “Compartilhar vídeo”, “Compartilhar capa 4:5”, “Gerar outro”.

---

## 4. Arquitetura alvo

### 4.1 Camadas

```
core:domain/story                 (Kotlin puro, testável, Kover)
  ├─ StoryEssential / StoryExtra / StoryRecipe / StoryPreset
  ├─ StoryMetricCatalog           (disponibilidade, formato, perKm)
  ├─ StoryPlan / StoryTimeline / StorySegment / StorySplit
  ├─ StoryPlanCalculator          (movido de :app)
  ├─ StoryHighlightsCalculator    (movido de :app)
  ├─ RouteProjection              (movido de :app/ui/activitydetail)
  └─ StoryDuration / StoryTimelinePlanner

:app/story                        (Android)
  ├─ StoryRenderSpec              (resolvido, sem regra)
  ├─ StoryFrameRenderer           (orquestra)
  ├─ StoryHeaderRenderer / StoryCardRenderer / StoryFinalRenderer
  ├─ StoryPosterRenderer          (4:5)
  ├─ StoryLayout                  (por formato)
  ├─ StoryMapProvider             (MapLibre + pixelForLatLng)
  ├─ StoryMapCache                (WebP/JPEG + manifesto)
  ├─ CanvasStoryVideoExporter
  └─ StoryExportWorker / Coordinator

:app/ui/activitydetail
  ├─ ActivityDetailScreen         (card “Criar story”)
  └─ StoryStudioScreen / ViewModel
```

**Não criar módulo novo agora.** Mover o puro para `core:domain` e manter
render/UI em `:app` evita mexer no build em paralelo à feature.

### 4.2 Contratos principais

```kotlin
enum class StoryEssential { DISTANCE, AVG_PACE, TOTAL_TIME }

enum class StoryExtra {
    CALORIES_TOTAL, ELEVATION_GAIN, AVG_HR, MAX_HR,
    CADENCE, FASTEST_KM, DATE,
}

data class StoryRecipe(
    val preset: StoryPreset = StoryPreset.ESSENTIAL,
    val extras: List<StoryExtra> = emptyList(),
    val duration: StoryDuration = StoryDuration.AUTO,
    val mapStyle: StoryMapStyle = StoryMapStyle.MEUCOACH_DARK,
    val quality: StoryQuality = StoryQuality.STANDARD_1080P30,
) {
    init {
        require(extras.size <= 2) { "O story aceita no máximo 2 extras." }
        require(extras.distinct().size == extras.size) { "Extras duplicados." }
    }
}

data class StoryMapSnapshot(
    val bitmap: Bitmap,
    val keyframe: StoryKeyframe,
    /** Projeção exata do MapSnapshot.pixelForLatLng, no tamanho do bitmap. */
    val routePixels: List<PointF>,
    val startPixel: PointF?,
    val endPixel: PointF?,
)

data class StoryRenderSpec(
    val plan: StoryPlan,
    val header: List<ResolvedMetric>,   // distância, tempo
    val cards: List<StoryCardSpec>,     // ritmo + extras por km
    val final: List<ResolvedMetric>,    // 3 essenciais + até 2 extras
    val highlights: StoryHighlights,
    val layout: StoryLayoutSpec,
    val mapStyle: StoryMapStyle,
)
```

O renderer **não decide disponibilidade**; recebe tudo resolvido. A mesma
`StoryRecipe` alimenta prévia, worker e capa.

### 4.3 Fluxo de exportação

```
ActivityDetailScreen
  → StoryStudioViewModel
      ├─ carrega atividade, perfil, plano, disponibilidade
      ├─ monta StoryRecipe
      ├─ renderiza prévia (540×960, 3 frames)
      └─ “Gerar vídeo”
            → StoryExportCoordinator.start(activityId, recipeJson, previewMap?)
                 → WorkManager (unique story-export-<id>)
                      → StoryExportWorker
                           ├─ StoryPlanLoader + StoryRecipe
                           ├─ StoryMapCache/StoryMapProvider (3 keyframes)
                           ├─ CanvasStoryVideoExporter (1080×1920, 30 fps)
                           ├─ StoryPosterRenderer (4:5) + capa 9:16
                           └─ MediaStore + notificação + share
```

`WorkManager.Data`: `activityId`, `recipeJson` (≤2 KB), `mapDir` opcional,
`palette` (IntArray). Nada de bitmap no `Data`.

### 4.4 Cache e memória

- `cacheDir/stories/<activityId>/<mapVariantHash>/`
  - `mapVariantHash` = estilo + keyframes + resolução + hash dos samples GPS.
  - `manifest.json` com keyframes, pixels da rota, tamanho, estilo, versão.
  - `<style>-<keyframe>.webp` (qualidade 95) ou JPEG 95.
  - Trocar extras/duração **não** invalida o mapa; só estilo/keyframes/resolução
    ou uma nova importação da mesma atividade.
- Prévia: mapa 1080×1920; frame 540×960.
- Export: follow 2,25×; demais 1,5–1,75×; máximo 2 snapshots vivos.
- Low-RAM: 1,25×, 2 keyframes, 30 fps.
- Reciclar bitmaps ao sair da tela/trocar receita; não manter o mapa de export
  na `Preview` depois de estagiar.
- Limpar cache no reset de dados e por LRU (ex.: 200 MB).

### 4.5 Persistência

- `StoryRecipeStore` em `SharedPreferences` (`meucoach_story`), JSON versionado.
- `StoryRecipeCodec` para o worker.
- Reset de dados limpa a preferência.
- Nenhuma migração de Room.

Exemplo de payload (v1):

```json
{
  "v": 1,
  "preset": "ESSENTIAL",
  "extras": ["AVG_HR", "CADENCE"],
  "duration": "AUTO",
  "mapStyle": "MEUCOACH_DARK",
  "quality": "STANDARD_1080P30"
}
```

O codec ignora chaves desconhecidas, valida o número de extras e cai no
default seguro se o JSON estiver corrompido. Nunca falha o worker por uma
preferência inválida: usa `StoryRecipe()` e segue.

---

## 5. Plano de tarefas

Legenda: **P** ≤ 0,5 dia · **M** 1–2 dias · **G** 3–5 dias.
Status: `[ ]` pendente · `[~]` em andamento · `[x]` concluído.

### EPIC 0 — Documentação e decisões

| ID | Tarefa | Entrega | Arquivos | Dep. | Est. | Aceite |
|---|---|---|---|---|---|---|
| [x] D0.1 | ADR 0040 — Story V3 | Registra receita, métricas essenciais+2 extras, sem áudio, sem privacidade, OpenFreeMap/estilo autoral, encode | `docs/adr/0040-story-v3-recipe-and-map.md`, `docs/DECISIONS.md` | — | P | ADR aceito e indexado |
| [x] D0.2 | Corrigir ADR 0015 | Google é bloqueio legal; `pixelForLatLng` existe; MapLibre 13.7.1 OpenGL; satélite fora | `docs/adr/0015-story-video-map.md` | D0.1 | P | Sem afirmação falsa sobre projeção |
| [x] D0.3 | Atualizar análise/STORY_V2 | Banner de decisões e link para este plano | `docs/STORY_V3_ANALISE_E_DIRECIONAMENTO.md`, `docs/STORY_V2.md` | D0.1 | P | Decisões visíveis no topo |
| [x] D0.4 | Atualizar PROGRESS/ROADMAP ao concluir | Estado real, testes, pendências | `docs/PROGRESS.md`, `docs/ROADMAP.md` | fim | P | Documentos batem com o código |

### EPIC 1 — Domínio e receita

| ID | Tarefa | Entrega | Arquivos | Dep. | Est. | Aceite |
|---|---|---|---|---|---|---|
| [x] DOM-01 | Mover modelo puro para `core:domain/story` | `StoryPlan`, `StoryPlanCalculator`, `StoryHighlightsCalculator`, `RouteProjection`, `StorySplit`, `StorySegment`; mover/duplicar o formatador puro de duração | `core/domain/.../story/*`, `app/.../story/*`, testes | D0.1 | G | `:core:domain:test` verde; `:app` compila; sem `android.*` no domínio |
| [x] DOM-02 | Catálogo de métricas | `StoryEssential`, `StoryExtra`, `StoryMetricCatalog`, disponibilidade, formatação, `perKm` | `core/domain/.../story/StoryMetrics.kt` | DOM-01 | M | Testes de disponibilidade/ausência; sem calorias/km |
| [x] DOM-03 | `StoryRecipe` + presets | Essenciais fixos, extras ≤2, duração, mapa, qualidade; mover `StoryMapStyle`/`StoryQuality`/`StoryDuration` para o domínio | `core/domain/.../story/StoryRecipe.kt`, `StoryMapStyle.kt` | DOM-02 | M | `require` de 2 extras; presets não excedem; testes; sem `android.*` |
| [x] DOM-04 | Duração e timeline por esforço | Auto/15/30/45/60; bônus do km rápido/FC; clamp; normalização | `core/domain/.../story/StoryTimeline.kt` | DOM-03 | M | Duração total exata; monotônica; testes |
| [x] DOM-05 | Seleção de cards e barra de splits | Máx. 8; primeiro/último/mais rápido/mais lento; barra com todos os kms | `StoryPlanCalculator.kt` | DOM-04 | M | 10 km → 8 cards; barra com 10 segmentos; testes |
| [x] DOM-06 | Store e codec da receita | `StoryRecipeStore` + `StoryRecipeCodec` | `app/.../story/StoryRecipeStore.kt`, `StoryRecipeCodec.kt` | DOM-03 | M | Round-trip; reset limpa; `Data` ≤ 2 KB |
| [x] DOM-07 | Resolvedor de métricas | `StoryRenderSpec`/`ResolvedMetric` a partir de atividade + `localDate` + receita | `core/domain/.../story/StoryRenderSpec.kt` | DOM-02/03 | M | Nenhum `0` por ausência; strings formatadas; `DATE` correto |
| [x] DOM-08 | Testes de regressão | 25+ testes de receita, timeline, métricas | `core/domain/src/test/.../story/*` | DOM-01..07 | M | Kover ≥85% no domínio |
| [ ] DOM-09 (Sprint 3) | Recorde/prova/primeira corrida | `StoryRecordDetector` determinístico e badges | `core/domain/.../story/StoryRecordDetector.kt` | DOM-01 | M | ≥3 corridas; dados ausentes não inventam recorde |

### EPIC 2 — Mapa

| ID | Tarefa | Entrega | Arquivos | Dep. | Est. | Aceite |
|---|---|---|---|---|---|---|
| [ ] MAP-01 | Atualizar MapLibre 13.7.1 OpenGL | `libs.versions.toml` + módulo `android-sdk-opengl` | `gradle/libs.versions.toml`, `app/build.gradle.kts` | — | M | Build verde; snapshot em aparelho; sem Vulkan obrigatório |
| [ ] MAP-02 | Projeção exata via `pixelForLatLng` | `StoryMapSnapshot` com `routePixels`, `start/end` | `MapBackgroundRenderer.kt`, `StoryMapSnapshot.kt` | MAP-01 | G | Rota alinhada em pitch; paridade com o port ≤2 px no device; produção usa SDK |
| [ ] MAP-03 | Aposentar `StoryMapProjection` do caminho de produção | Fallback/teste apenas | `StoryMapProjection.kt`, renderer | MAP-02 | M | Nenhum uso no fluxo real; teste de paridade documentado |
| [ ] MAP-04 | Estilo `MeuCoach Dark`/`Light` | Paleta, filtros de POI/transporte/vias, labels, água/parques | `OpenFreeMapStyle.kt`, `StoryPalette.kt` | MAP-01 | G | Labels legíveis em 1080p; POIs ocultos; golden/screenshot |
| [ ] MAP-05 | 3 keyframes de câmera | Abertura 30°, follow 42°, final 35°; troca nos cards | `StoryMapScript.kt`, `StoryCameraScript.kt`, renderer | MAP-02 | G | 3 snapshots; rota alinhada; sem salto visível |
| [ ] MAP-06 | Cache de estilo e snapshots | Style JSON em disco; manifesto; WebP/JPEG; LRU | `MapLibreBackgroundRenderer.kt`, `StoryMapCache.kt` | MAP-05 | M | Reexport não baixa de novo; cache limpo no reset |
| [ ] MAP-07 | Low-RAM | `isLowRamDevice` → 1,25×, 2 keyframes | `StoryVideoExporter.kt`, `StoryMapScript.kt` | MAP-05 | P | Export completa em aparelho 4 GB |
| [ ] MAP-08 | Remover satélite do MVP | Tirar `SATELLITE` da UI e do fluxo; manter o valor no enum como `@Deprecated` para não quebrar ordinais de jobs antigos; retorno só com licença | `StoryMapStyle.kt`, `StoryPaletteCodec.kt`, UI | D0.1 | P | Nenhuma opção Esri no fluxo padrão; decode antigo não quebra |
| [ ] MAP-09 | Seam Protomaps | Interface `StoryTileSource` documentada | `StoryMapProvider.kt` | MAP-04 | P | Troca futura sem tocar no renderer |
| [ ] MAP-10 (Sprint 3) | Fallback sem rede | Pôster de rota sobre gradiente; vídeo sem mapa | `StoryMapProvider.kt`, `StoryPlanLoader.kt` | MAP-06 | M | Sem rede gera pôster; erro claro |

### EPIC 3 — Layout e render

| ID | Tarefa | Entrega | Arquivos | Dep. | Est. | Aceite |
|---|---|---|---|---|---|---|
| [x] VIS-01 | Safe area Reels + layout 9:16 | 13% topo/22% base/5,5% lados; card 62–78%; atribuição acima do card (nunca abaixo de 78%) | `StoryLayout.kt`, testes | — | P | Card/atribuição acima de 78%; golden novo |
| [x] VIS-02 | Slots dinâmicos de métricas | Header 2, card 1–3, final 3+2; tabular numerals; sem legenda | `StoryFrameRenderer.kt` | DOM-07 | G | 0/1/2 extras sem overflow; `tnum` |
| [x] VIS-03 | Card por km | Ritmo + extras por km; “KM 3 · 5:02”; selo quando `FASTEST_KM`; barra com todos os kms | `StoryFrameRenderer.kt` | VIS-02, DOM-05 | G | Sem calorias/km; 10 km → 10 segmentos |
| [x] VIS-04 | Encerramento | 3 essenciais + 2 extras; manchete/prova; logo/tagline | `StoryFrameRenderer.kt` | VIS-02 | M | 3 essenciais sempre; 2 extras quando escolhidos |
| [ ] VIS-05 | Pôster 4:5 + capa 9:16 | Rota como arte, tipografia, data, marca | `StoryPosterRenderer.kt` | VIS-01 | M | 1080×1350; sem crop no feed; PNG |
| [ ] VIS-06 | Prévia 3 frames + scrubber | 540×960; frames abertura/card/final; sem bitmap 42 MB | `StoryStudioScreen.kt`, ViewModel | VIS-02 | M | Troca de frame <100 ms; memória <20 MB |
| [ ] VIS-07 | Refatorar renderer | `StoryHeaderRenderer`, `StoryCardRenderer`, `StoryFinalRenderer`, `StoryPosterRenderer` | `app/.../story/render/*` | VIS-02 | G | Arquivos <300 linhas; goldens passam |
| [ ] VIS-08 | Goldens | Essencial/Performance/Fisiológico; 9:16/4:5; 0/1/2 extras; dark/light | `app/src/test/screenshots/*` | VIS-01..05 | M | Roborazzi verde; revisão visual |
| [ ] VIS-09 | Copy do vídeo | Remover “pronta para o feed” do frame 9:16; 4:5 é a capa | UI strings | VIS-05 | P | Copy correta |

### EPIC 4 — Encode e exportação

| ID | Tarefa | Entrega | Arquivos | Dep. | Est. | Aceite |
|---|---|---|---|---|---|---|
| [x] ENC-01 | Qualidade de encode | BT.709/SDR/limited, High/4.1, 16 Mbps VBR | `StoryVideoExporter.kt` | — | P | `ffprobe` mostra bt709; sem cor lavada |
| [ ] ENC-02 | Perfis de qualidade | 1080p30 padrão; parâmetro de 60 fps preparado; bitrate por perfil | `StoryQuality.kt`, exporter | ENC-01 | M | 30 fps validado; 60 fps só é exposto após `DEV-04` |
| [ ] ENC-03 | Sem áudio, sem GPS | Nenhuma trilha AAC; nunca `setLocation`; nome com data/distância | exporter | ENC-01 | P | MP4 só vídeo; sem metadata de local |
| [ ] ENC-04 | Estágio WebP/JPEG | Mapa de prévia e keyframes em WebP/JPEG 95 | Coordinator, cache | MAP-06 | M | Tempo de stage menor; decode ok |
| [ ] ENC-05 | Receita no worker | `StoryPlanLoader` + `StoryRecipe` + `mapDir`/manifesto | Worker, Processor, Coordinator | DOM-06, MAP-05 | G | Export respeita extras/duração; teste |
| [ ] ENC-06 | Progresso/ETA multi-snapshot | 0–10% mapa, 10–100% encode; cancelamento | Worker, ETA | ENC-05 | M | ETA estável; cancelar apaga parcial |
| [ ] ENC-07 | Publicação | Vídeo + capa 9:16 + pôster 4:5 no MediaStore; share múltiplo | exporter, UI | VIS-05 | M | 3 URIs; share sheet |
| [ ] ENC-08 | Memória | Reciclar bitmaps; não manter high-res na Preview | ViewModel, exporter | VIS-06 | M | Pico <150 MB em 30 s |
| [ ] ENC-09 (Sprint 3) | GPU/HardwareRenderer | Spike se export >90 s | exporter | ENC-08 | G | Medição antes/depois; fallback |

### EPIC 5 — UI / Story Studio

| ID | Tarefa | Entrega | Arquivos | Dep. | Est. | Aceite |
|---|---|---|---|---|---|---|
| [ ] UI-01 | Story Studio (modal) | Entrada no card, tela com preview e opções | `StoryStudioScreen.kt`, nav | VIS-06 | G | Abre/fecha; back; estado |
| [ ] UI-02 | Essenciais + extras | Chips fixos; extras 0/2; motivos; presets | `StoryStudioViewModel.kt` | DOM-03, UI-01 | G | 3ª extra bloqueada; dados ausentes |
| [ ] UI-03 | Duração/mapa/qualidade | Auto/15/30/45/60; Dark/Light/Terrain; Padrão 1080p30 (Suave só após `DEV-04`) | UI | UI-02 | M | Persistência; preview atualiza |
| [ ] UI-04 | Salvar padrão/reset | Preferência JSON; “restaurar padrão” | `StoryRecipeStore.kt` | DOM-06 | P | Sobrevive a reinício; reset limpa |
| [ ] UI-05 | Gerar/compartilhar | Preparing/Generating/Done/Error; cancelar; share vídeo/capa/pôster | UI + ViewModel | ENC-07 | G | Fluxo completo; notificação |
| [ ] UI-06 | Acessibilidade | 48 dp; TalkBack; seleção anunciada; contraste | UI | UI-04 | M | Testes de a11y; sem cor como único sinal |
| [ ] UI-07 | Copy | Sem áudio/privacidade; “métricas extras”; “capa 4:5” | strings | UI-05 | P | Revisão de texto |
| [ ] UI-08 | Remover opções inline antigas | Card só abre o Studio | `ActivityDetailScreen.kt` | UI-01 | P | Sem duplicação |
| [ ] UI-09 | Testes de UI | Loading/empty/error; presets; 0/1/2 extras | `app/src/test/...` | UI-01..08 | M | Roborazzi/semantics verdes |

### EPIC 6 — QA e gates

| ID | Tarefa | Entrega | Arquivos | Dep. | Est. | Aceite |
|---|---|---|---|---|---|---|
| [x] QA-01 | Testes de domínio | Receita, catálogo, timeline, cards, codec | `core/domain/src/test/.../story/*` | DOM-08 | M | ≥85% linhas |
| [ ] QA-02 | Goldens | 9:16/4:5; presets; extras; dark/light | screenshots | VIS-08 | M | `verifyRoborazziDebug` verde |
| [ ] QA-03 | Worker/coordinator | Receita, cache, retry, cancelamento | `app/src/test/.../story/*` | ENC-05 | M | Testes verdes |
| [ ] QA-04 | Gates | `check`, `spotlessCheck`, `koverVerify`, detekt | — | tudo | P | 0 erros; sem novo baseline |
| [ ] QA-05 | Roteiro de aparelho | S24 Ultra + mid-range 4 GB | `docs/RUNBOOK` ou anexo | ENC-08 | M | Tempo/memória/cor alinhados |
| [ ] QA-06 | Upload real | Instagram Reels + TikTok + WhatsApp | — | QA-05 | M | Safe area, nitidez, cor aprovadas |

### EPIC 7 — Validação em aparelho

| ID | Tarefa | Entrega | Dep. | Est. | Aceite |
|---|---|---|---|---|---|
| [ ] DEV-01 | Smoke MapLibre 13.7.1 | Snapshot dark/light em pitch | MAP-04 | P | Sem crash; labels legíveis |
| [ ] DEV-02 | Alinhamento da rota | Comparar rota desenhada vs ruas | MAP-02 | P | Erro visual imperceptível |
| [ ] DEV-03 | Export 5k/10k | Tempo, memória, tamanho | ENC-08 | M | 30 s ≤90 s; pico <150 MB |
| [ ] DEV-04 | 60 fps | Encoder suporta? | ENC-02 | P | Se falhar, esconder opção |
| [ ] DEV-05 | Share real | Reels/TikTok/WhatsApp | UI-05 | M | Sem corte; vídeo mudo aceito |
| [ ] DEV-06 | Metadados | `ffprobe`/MediaMetadataRetriever | ENC-03 | P | Sem GPS |
| [ ] DEV-07 | Pôster 4:5 | Grid do Instagram | VIS-05 | P | Sem crop |

---

### 5.1 Caminho crítico e paralelismo

```
D0.1 ──► DOM-01 ──► DOM-02 ──► DOM-03 ──► DOM-04 ──► DOM-05
                                      │
                                      ├──► DOM-06 ──► DOM-07 ──► VIS-02
                                      │                         │
                                      │                         ├──► VIS-03
                                      │                         └──► VIS-04
                                      │
                                      └──► UI-02 ──► UI-03 ──► UI-05 ──► ENC-05
                                                                 │
                                                                 └──► QA-03

MAP-01 ──► MAP-02 ──► MAP-03 ──► MAP-04 ──► MAP-05 ──► VIS-05
                                                   │
                                                   └──► MAP-06/ENC-04

VIS-01 ──► VIS-02 (independente; fazer primeiro)
ENC-01 ──► ENC-02 (independente; fazer primeiro)
```

**Pode rodar em paralelo:**

- `VIS-01` e `ENC-01` não dependem de nada; são os primeiros quick wins.
- `MAP-01`–`MAP-04` não dependem do domínio; podem começar enquanto a receita
  é implementada, mas o renderer só consome a projeção depois de `VIS-02`.
- `D0.1`–`D0.3` são documentação e podem fechar antes do código.
- `UI-01` (scaffold) pode começar depois de `VIS-06`; `UI-02` depende de
  `DOM-03`.
- `QA-01` acompanha `DOM-01..07`; `QA-02` acompanha `VIS-01..05`.

**Não paralelizar:** `DOM-01` (move arquivos) com mudanças grandes no renderer;
`MAP-05` (keyframes) com `VIS-03/04` sem a projeção estável.

---

## 6. Sequenciamento

### Etapa 1 — “Controle e qualidade” (Sprint A)

**Objetivo:** o usuário escolhe as métricas, o vídeo cabe no Reels e o encode
está correto.

Ordem:

1. `D0.1`–`D0.3` (ADR e correções de doc).
2. `VIS-01` (safe area), `ENC-01` (cor/profile), `VIS-02` (tnum/legenda).
3. `DOM-01`–`DOM-07` (domínio, receita, métricas, timeline).
4. `VIS-02`–`VIS-04` (slots, card, final).
5. `VIS-06` (prévia 3 frames) e `UI-01`–`UI-05` (Story Studio, gerar).
6. `ENC-05` (receita no worker) e `ENC-08` (memória).
7. `QA-01`–`QA-04`.

**Checkpoint:** dois stories do mesmo treino, um com 0 extras e outro com 2,
gerados no aparelho; ambos com distância/ritmo/tempo e sem corte no Reels.

### Etapa 2 — “Mapa autoral e narrativa” (Sprint B)

**Objetivo:** o vídeo parece filmado, não printado.

Ordem:

1. `MAP-01`–`MAP-03` (MapLibre 13.7.1 + projeção exata).
2. `MAP-04` (estilo dark/light).
3. `MAP-05` (3 keyframes) e ajuste de `VIS-03`/`VIS-04` à câmera.
4. `VIS-05` (pôster 4:5).
5. `MAP-06` (cache) e `ENC-04` (WebP/JPEG).
6. `QA-05`, `DEV-01`–`DEV-03`.

**Checkpoint:** story de 10 km com 8 cards, barra de 10 km, rota alinhada em
pitch, 3 keyframes e pôster 4:5.

### Etapa 3 — “Assinatura” (Sprint C, fast-follow)

- `DOM-09` recorde/prova/primeira corrida.
- `ENC-02` — expor 60 fps após `DEV-04` (se o encoder suportar).
- `MAP-10` fallback offline.
- `ENC-09` GPU se o export passar de 90 s.
- `QA-06` upload real e ajuste fino de labels/bitrate.

**Checkpoint:** vídeo com selo de recorde, 60 fps opcional e fallback offline.

### Resumo de esforço (ordem de grandeza, solo, com testes)

| Etapa | Conteúdo | Estimativa |
|---|---|---|
| 1 | Receita, métricas, safe area, encode, Story Studio, worker | 8–12 dias |
| 2 | MapLibre 13.7.1, projeção exata, estilo dark, keyframes, pôster, cache | 8–12 dias |
| 3 | Recorde/prova, 60 fps, fallback offline, upload real | 4–6 dias |
| **MVP** | **Etapas 1 + 2** | **16–24 dias** |

### Etapa 4 — Pós-MVP

- Protomaps/PMTiles em R2 + pacotes regionais.
- Áudio (se você mudar de ideia).
- 4:5 em vídeo, 1:1, 16:9.
- Satélite com licença (MapTiler/Esri).
- 3D terrain quando o MapLibre Native suportar.
- Year in Review.
- Leitura do coach opt-in no vídeo.

---

## 7. Critérios de aceite do MVP

O MVP está pronto quando **todos** forem verdade:

- [ ] O Story Studio abre pelo detalhe da atividade e permite escolher 0–2 extras.
- [ ] Distância, ritmo médio e tempo total aparecem sempre no encerramento.
- [ ] Calorias por km não existem em nenhum card; calorias totais são um extra.
- [ ] O card mostra ritmo do km + extras por km disponíveis.
- [ ] 10 km geram 8 cards e uma barra com 10 segmentos.
- [ ] O layout 9:16 respeita 13% topo / 22% base / 5,5% lados; nada importante
      atrás da UI do Reels/TikTok.
- [ ] O mapa padrão é o `MeuCoach Dark`, com rota lima e labels legíveis.
- [ ] A rota usa `pixelForLatLng`; não há deriva visível em pitch.
- [ ] O vídeo tem 3 keyframes e cortes escondidos nos cards.
- [ ] O vídeo é 1080p30, H.264 High, 16 Mbps, BT.709/SDR/limited, sem áudio,
      sem GPS.
- [ ] O pôster 4:5 e a capa 9:16 são gerados e compartilháveis.
- [ ] A prévia tem 3 frames + scrubber; não guarda bitmap de 42 MB.
- [ ] Export em segundo plano, progresso/ETA, cancelamento e notificação.
- [ ] `./gradlew check`, `spotlessCheck`, `koverVerify` e detekt verdes.
- [ ] Validação em aparelho e upload real no Instagram aprovados.
- [ ] ADR 0040, ADR 0015, PROGRESS e ROADMAP atualizados.
- [ ] Nenhum dado real de saúde no diff; nenhum áudio/privacidade acidental.

---

## 8. Testes e gates

### 8.1 Unitários (core:domain)

- `StoryRecipeTest`: essenciais imutáveis; 0/1/2 extras; 3 extras falha;
  duplicata falha; presets.
- `StoryMetricCatalogTest`: cada extra com dado presente, ausente e zero;
  `CALORIES_TOTAL` só no final; `ELEVATION_GAIN`/`AVG_HR`/`CADENCE` por km.
- `StoryTimelineTest`: auto 5/10/21 km; 15/30/45/60; soma exata; sem overlap;
  bônus do km rápido; card clamp.
- `StoryCardSelectionTest`: 10 km → 8 cards; primeiro/último/mais rápido/mais
  lento presentes; sem duplicata; 21 km agrupa barra.
- `StoryHighlightsTest`: negative split, consistência, km mais rápido, fallback.
- `StoryRecipeCodecTest`: round-trip; JSON inválido; versão.
- `StoryRenderSpecTest`: strings formatadas; nenhum “0” por ausência.

### 8.2 Goldens (Roborazzi)

- 9:16 dark: Essencial (0 extras), Performance (2), Fisiológico (2).
- 9:16 light: Essencial.
- 4:5 poster: 5k e 10k.
- Card: 1, 2 e 3 métricas; com e sem selo de km mais rápido.
- Final: 3, 4 e 5 métricas.
- Safe area: card/atribuição acima de 78%.
- Studio: idle, 0/2, 2/2, erro, gerando.

### 8.3 Worker/UI

- `StoryExportProcessorTest`: receita no worker; cache hit/miss; fallback;
  cancelamento; retry.
- `StoryExportCoordinatorTest`: input com recipe; nome único; REPLACE.
- `StoryStudioViewModelTest`: presets, extras, disponibilidade, persistência.
- `ActivityDetailScreenTest`: card abre studio; estados.
- A11y: TalkBack labels, 48 dp, contraste.

### 8.4 Comandos

```bash
./gradlew :app:assembleDebug
./gradlew check
./gradlew spotlessCheck
./gradlew koverVerify
./gradlew :core:domain:test
./gradlew :app:testDebugUnitTest --tests "com.meucoach.app.story.*"
./gradlew :app:recordRoborazziDebug
./gradlew :app:verifyRoborazziDebug
```

### 8.5 Roteiro de aparelho

1. Instalar debug em S24 Ultra e em um aparelho 4 GB.
2. Exportar 5k e 10k; medir tempo, memória e tamanho.
3. Conferir alinhamento da rota no início/meio/fim.
4. Conferir labels no mapa escuro em 1080p.
5. Subir no Instagram Reels, TikTok e WhatsApp; conferir safe area.
6. Verificar que o MP4 não tem trilha de áudio nem GPS.
7. Abrir o pôster 4:5 no grid do Instagram.
8. Cancelar no meio, sair do app, matar o processo e reabrir.

---

## 9. Riscos e mitigação

| Risco | Prob. | Impacto | Mitigação |
|---|---|---|---|
| MapLibre 13.7.1 quebra algo | Média | Alto | Spike primeiro; fallback 12.3.1 + `StoryMapProjection` |
| Labels ilegíveis no mapa escuro | Média | Alto | Teste em 1080p; aumentar sizes; esconder vias menores |
| 3 snapshots estouram memória | Média | Alto | 2 vivos; low-RAM 1,25×; WebP/JPEG |
| Export >90 s | Média | Médio | Perfis; medir; GPU só se necessário |
| 60 fps não suportado | Alta | Baixo | Esconder opção se encoder falhar |
| Extras demais poluem | Baixa | Médio | Limite 2; presets; preview |
| Kover cair no refactor | Média | Médio | Testes junto com DOM-01 |
| Story Studio virar formulário | Média | Médio | Presets primeiro; “personalizar” depois |
| Satélite/licença | Certa | Médio | Fora do MVP; MapTiler só com contrato |
| Falta de tempo | Média | Alto | Etapa 1 é o MVP mínimo; Etapa 2 é o salto visual |

---

## 10. Comece aqui na próxima sessão

### 10.1 Leitura obrigatória (15 min)

1. `AGENTS.md`.
2. `docs/STORY_V3_PLANO_DE_ACAO.md` (este documento).
3. `docs/STORY_V3_ANALISE_E_DIRECIONAMENTO.md` (evidências).
4. `docs/PROGRESS.md` (estado atual).

### 10.2 Primeira tarefa (meio dia)

**Objetivo:** ver o efeito no aparelho antes de mexer na arquitetura.

1. `D0.1` — criar ADR 0040 com as decisões deste documento.
2. `VIS-01` — safe area 13/22/5,5; card 62–78%; atribuição no card.
3. `ENC-01` — BT.709/SDR/limited, High/4.1, 16 Mbps VBR.
4. `VIS-02` (parcial) — `fontFeatureSettings = "tnum"` e remover a legenda.
5. Rodar `./gradlew :app:assembleDebug :app:verifyRoborazziDebug`.
6. Instalar e gerar um story; conferir no Reels.

**Critério de saída:** card e atribuição visíveis no Reels; cor do MP4
declarada; nenhum texto tremendo.

### 10.3 Segunda tarefa (2–4 dias)

**Objetivo:** receita e métricas.

1. `DOM-01` — mover o puro para `core:domain/story`.
2. `DOM-02`/`DOM-03` — catálogo, essenciais, extras, presets.
3. `DOM-04`/`DOM-05` — timeline e cards.
4. `DOM-06`/`DOM-07` — store, codec, `StoryRenderSpec`.
5. `QA-01` — testes.

**Critério de saída:** `StoryRecipe` com 3 essenciais + 2 extras; 10 km → 8
cards; 10 segmentos na barra; testes verdes.

### 10.4 Terceira tarefa (3–5 dias)

**Objetivo:** Story Studio e worker.

1. `UI-01`–`UI-05` + `VIS-06` (prévia).
2. `ENC-05` (receita no worker) + `ENC-08` (memória).
3. `VIS-02`–`VIS-04` — slots, card, final.
4. `QA-03`/`QA-04`.

**Critério de saída:** gerar dois stories com receitas diferentes; ambos
respeitam a receita no worker; `check` verde.

### 10.5 Ordem do Sprint B (depois)

1. `MAP-01` → `MAP-02` → `MAP-03` (projeção).
2. `MAP-04` (estilo).
3. `MAP-05` (keyframes).
4. `VIS-05` (pôster).
5. `MAP-06`/`ENC-04` (cache/estágio).
6. `QA-05`/`DEV-01`–`DEV-03`.

---

## 11. Registro de decisões para o ADR 0040

Copiar para o ADR:

- **Contexto:** story atual fixo, sem escolha de métricas, com safe area de
  Stories (não Reels), encode sem cor declarada, mapa claro genérico.
- **Decisão 1:** receita com 3 essenciais fixas e até 2 extras; sem calorias/km;
  calorias totais como extra.
- **Decisão 2:** sem áudio no MVP; música no app de destino.
- **Decisão 3:** sem máscara de privacidade; rota completa por escolha do
  usuário 0.
- **Decisão 4:** OpenFreeMap + estilo autoral agora; Protomaps depois; Google
  proibido para exportação; satélite fora do MVP.
- **Decisão 5:** MapLibre 13.7.1 OpenGL + `pixelForLatLng`.
- **Decisão 6:** 1080p30 16 Mbps BT.709 High; 60 fps fast-follow; 1440p/4K fora.
- **Decisão 7:** 9:16 Reels-safe + pôster 4:5; 4:5 em vídeo fora.
- **Consequências:** refactor do domínio, Story Studio, cache de mapa, mudança
  de goldens; nenhuma migração de Room; nenhuma dependência nova além do
  upgrade do MapLibre.
- **Alternativas rejeitadas:** Google (legal), Mapbox (custo/termos), MapTiler
  no MVP (custo/licença), áudio procedural (decisão de produto), máscara de
  privacidade (decisão de produto), 3D terrain (não suportado).

---

## 12. Prompt de abertura da próxima sessão

Copie o bloco abaixo para começar a implementação sem reabrir as decisões já
tomadas:

```text
Leia, nesta ordem: AGENTS.md, docs/STORY_V3_PLANO_DE_ACAO.md,
docs/STORY_V3_ANALISE_E_DIRECIONAMENTO.md e docs/PROGRESS.md.

Objetivo: implementar a Etapa 1 do Story V3 (Sprint A) do MeuCoach.

Decisões já tomadas (não reabrir):
- vídeo sem áudio; sem máscara de privacidade;
- OpenFreeMap + estilo autoral agora; Protomaps depois; satélite fora;
- 3 métricas essenciais fixas (distância, ritmo médio, tempo total) + até 2 extras;
- calorias por km removidas; calorias totais como extra;
- 9:16 Reels-safe (13% topo / 22% base / 5,5% lados) + pôster 4:5;
- 1080p30, H.264 High, 16 Mbps, BT.709/SDR/limited;
- MVP = Etapas 1 + 2; 60 fps/recorde/fallback offline só depois.

Comece por:
1. D0.1 — ADR 0040 com as decisões deste plano.
2. VIS-01 — safe area de Reels, card 62–78%, atribuição dentro do card.
3. ENC-01 — BT.709/SDR/limited, High/4.1, 16 Mbps VBR.
4. VIS-02 (parcial) — tabular numerals e remoção da legenda do card.
5. DOM-01 — mover o modelo puro para core:domain/story.
6. DOM-02..07 — catálogo, receita, timeline, cards, store, render spec.

Regras:
- TDD quando possível; todo bug ganha teste de regressão.
- Não incluir áudio, privacidade, satélite, 4:5 em vídeo, LLM ou 3D terrain.
- Não usar dados reais de saúde.
- Rodar ./gradlew check, spotlessCheck e koverVerify antes de concluir.
- Atualizar PROGRESS.md e ROADMAP.md ao final.
- Se o MapLibre 13.7.1 quebrar, voltar para 12.3.1 e manter o port de projeção
  como fallback; registrar no ADR 0015.
- Parar e mostrar o diff antes de mexer em arquivos fora do escopo do Story.
```

---

## 13. Uma frase

O MVP do Story V3 é: **três métricas essenciais + duas extras, mapa escuro
autoral, rota exata em três keyframes, 1080p30 a 16 Mbps e um pôster 4:5** —
mudo, sem máscara, feito para o Reels e para o feed.
