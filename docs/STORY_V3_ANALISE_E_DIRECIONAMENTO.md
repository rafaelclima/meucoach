# Story V3 — Análise profunda e direcionamento

**Status:** análise histórica · decisões tomadas em 13/11/2026
**Data:** 13/11/2026
**Escopo:** funcionalidade de story/vídeo para redes sociais do MeuCoach
**Público:** Rafael (usuário 0) e implementação

> **Decisões finais de 13/11/2026 (não reabrir):** vídeo **sem áudio**, **sem
> máscara de privacidade**, **OpenFreeMap + estilo autoral** agora (Protomaps
> depois; Google é bloqueio legal; satélite fora), **3 métricas essenciais
> fixas (distância, ritmo médio, tempo total) + até 2 extras**, **calorias por
> km removidas** e **calorias totais como extra**, **9:16 Reels-safe
> (13% topo / 22% base / 5,5% lados) + pôster 4:5**, **1080p30 H.264 High
> 16 Mbps BT.709/SDR/limited**, com **MapLibre 13.7.1 OpenGL + `pixelForLatLng`
> como decisão pendente do Sprint B**. O plano executável, com tarefas e
> critérios de aceite, está em
> [`STORY_V3_PLANO_DE_ACAO.md`](STORY_V3_PLANO_DE_ACAO.md); as decisões de
> produto estão no [`ADR 0040`](adr/0040-story-v3-recipe-and-map.md).
> As recomendações de áudio/privacidade e as perguntas abertas abaixo ficam
> como registro histórico da análise; o plano é a fonte de verdade.

---

## 1. Resumo executivo

O story do MeuCoach já é tecnicamente melhor que o de quase todos os apps de corrida:
mapa vetorial real, rota animada, zonas de FC, cards por km, veredito privado,
export em segundo plano, cancelamento real e 1080×1920 a 30 fps. O problema não é
capacidade — é **controle, acabamento e narrativa**.

As sete conclusões que importam:

1. **O maior bug de qualidade hoje é a área segura.** O layout reserva 13% no topo
   e 13% na base (≈250 px). O Instagram Reels, em 2026, cobre cerca de **420 px**
   na base; TikTok chega a **484 px**. O card (66%–84%) e a atribuição do mapa
   (86,3%) caem dentro da UI do Reels. O vídeo parece “amador” não pelo mapa, mas
   porque o número principal fica atrás do botão de curtir.
2. **O maior ganho visual disponível é usar a projeção real do MapLibre.**
   O ADR 0015 conclui que “não há projeção” porque o `MapSnapshotter` não expõe
   `Projection`. A conclusão é **falsa na prática**: o objeto `MapSnapshot`
   entregue no callback tem `pixelForLatLng(LatLng): PointF` (confirmado no AAR
   12.3.1 em uso). Dá para desenhar a rota com a matriz exata do mapa, inclusive
   em snapshot inclinado, sem o port manual `StoryMapProjection` e sem deriva.
3. **O maior ganho de produto é um “Story Studio” com receita configurável.**
   Hoje o vídeo é fixo: 30 s, 9:16, 3 métricas no card, 3 no encerramento, sem
   áudio, sem privacidade. O usuário não escolhe nada além do estilo do mapa.
   Uma `StoryRecipe` com presets + slots de métricas resolve isso sem inflar a
   tela de detalhe da atividade.
4. **O maior ganho emocional é uma linha do tempo por esforço, não por km.**
   Hoje cada km recebe 2 s iguais. Um km a 4:00 e um a 7:00 duram o mesmo. O
   vídeo não respira o treino. Uma timeline que dá mais tempo ao km mais rápido,
   ao km mais difícil e ao encerramento muda a percepção de “print animado” para
   “filme do treino”.
5. **O maior ganho de confiança é privacidade + fallback offline.**
   A rota mostra a porta de casa. Strava esconde início/fim; o MeuCoach não.
   E sem rede o story simplesmente falha. Para um app local-first, isso é uma
   contradição: o vídeo deveria funcionar com mapa em cache ou com um pôster
   abstrato da rota.
6. **“Mapa nível Google Maps” não é o caminho — e é legalmente bloqueado.**
   Os Termos do Google Maps Platform proíbem exportar/extrair o conteúdo para
   uso fora do serviço. Um MP4 com tiles do Google é exatamente isso. Para
   corrida, o OSM + um estilo autoral é *melhor* que o Google: trilhas, parques
   e ciclovias são o que importa, não POIs. A decisão certa é **OpenFreeMap +
   estilo próprio** agora e **Protomaps/PMTiles self-hosted** depois; MapTiler
   é o plano B pago; Mapbox e Google ficam fora.
7. **O acabamento técnico do encode está incompleto.** Falta declarar
   BT.709/SDR no `MediaFormat`, perfil High, bitrate mode e, principalmente,
   **áudio AAC**. O Instagram reencoda tudo; um master com cor declarada,
   12–16 Mbps e áudio a −14 LUFS chega visivelmente melhor.

---

## 2. Auditoria do que existe hoje

### 2.1 Arquitetura atual

```
ActivityDetailScreen
  └─ ActivityDetailViewModel
       ├─ StorySessionResolver (plano do dia + próxima sessão)
       ├─ StoryPlanCalculator (app/story, 372 linhas)
       │    ├─ routeData() (UI, projeção normalizada)
       │    ├─ kmSplits() / capCards() / timeline()
       │    └─ StoryVerdictCalculator (core:domain)
       ├─ MapBackgroundRenderer (MapLibre snapshot, 2 mapas)
       └─ StoryExportCoordinator → WorkManager
            └─ StoryExportWorker → StoryExportProcessor
                 ├─ StoryPlanLoader (reconstrói o plano)
                 ├─ MapBackgroundRenderer (fallback)
                 └─ CanvasStoryVideoExporter
                      ├─ StoryFrameRenderer (918 linhas)
                      ├─ StorySurfaceEncoder (EGL/GLES)
                      └─ MediaCodec + MediaMuxer
```

**Pontos fortes reais:**

- `core:domain/StoryVerdictCalculator` é puro e testado; o vídeo público não
  julga o plano (decisão correta de produto).
- `StoryFrameRenderer` é determinístico, com goldens; o mesmo renderer gera
  prévia, vídeo e capa.
- `StorySurfaceEncoder` usa Surface/EGL: o gargalo deixou de ser a conversão
  RGB→YUV; o export caiu de ~3 min para ~50 s no S24 Ultra.
- WorkManager com foreground service, ETA, cancelamento cooperativo e
  restauração de estado — maduro.
- `StoryPlanLoader` reconstrói o plano no worker: o export sobrevive à morte da
  tela e do processo.

### 2.2 Limites concretos (com arquivo)

| Limite | Onde | Efeito |
|---|---|---|
| 30 s fixos | `StoryPlanLoader.TARGET_DURATION_SEC` | Nenhum controle de duração |
| 9:16 fixo | `StoryVideoExporter.WIDTH/HEIGHT` | Sem 4:5, 1:1, 16:9 |
| 2 s por card, 8 cards | `StoryPlanCalculator.CARD_SECONDS/MAX_CARDS` | Ritmo igual para kms diferentes |
| 3 métricas fixas no card | `StoryFrameRenderer.drawCard` | Sem escolha do usuário |
| 3 métricas fixas no final | `StoryFrameRenderer.drawFinal` | Sem recorde, elevação, cadência, zonas |
| Cabeçalho fixo: distância + tempo | `drawHeader` | Sem data, título, local |
| Calorias iguais em todo card | `caloriesPerKm` em `kmSplits` | Número potencialmente enganoso |
| Barra de splits só com cards selecionados | `capCards` + `drawSplitBar` | 10 km podem parecer 5 km |
| Rota sempre completa | `StoryPlan.geoPoints` | Sem máscara de início/fim |
| Sem áudio | `StoryVideoExporter` | Vídeo mudo; menos retenção fora do IG |
| Sem cor declarada no encode | `MediaFormat` | Risco de cor lavada/apagada no reencode |
| Prévia de 1 frame | `StoryPreview` | Usuário não vê o encerramento antes de gerar |
| Mapa de prévia em 2,25× | `StoryVideoExporter.mapWidth()` | ~42 MB de bitmap só para mostrar 260 dp |
| Estágio em PNG | `WorkManagerStoryExportCoordinator.stageMap` | PNG grande/lento; WebP/JPEG resolveria |
| Sem cache de estilo | `MapLibreBackgroundRenderer.cachedStyles` (memória) | Re-download a cada processo |
| Sem fallback offline | `hasNetwork()` | Story falha sem rede |
| “Google Maps” citado como alternativa | ADR 0015 §2.1 | Justificativa errada: é proibido, não só caro |

### 2.3 O que o modelo de dados já permite (e o vídeo não usa)

`ImportedActivity` já tem:

- `elevationGainM` / `elevationLossM`
- `averageCadenceSpm` / `maxCadenceSpm`
- `maxHeartRate`
- `averagePowerW` / `maxPowerW`
- `averageStepLengthM`
- `laps` com FC, cadência, potência, elevação
- `startTime`, `localDate` (via `StoredActivity`)

Ou seja: **elevação, cadência, FC máxima e data já estão disponíveis** e não
aparecem em nenhum lugar do vídeo. O card pode ser muito mais rico sem
dependência nova.

---

## 3. O que “outro nível” significa

Vou separar em três eixos, porque “qualidade” sem critério vira gosto:

### 3.1 Qualidade visual

- O vídeo precisa parecer **feito para o feed**, não um print do app.
- Mapa escuro autoral, rota lima, coral fisiológico, tipografia grande,
  números tabulares, grão fino, vinheta, movimento com intenção.
- Área segura real de Reels/TikTok; nada importante atrás da UI.
- Capa 4:5 separada do frame final 9:16.

### 3.2 Qualidade narrativa

- Abertura que situa: data, distância, tempo.
- Meio que respira: o km mais rápido e o mais difícil ganham tempo.
- Encerramento que entrega um fato memorável: recorde, negative split,
  consistência, prova, primeira corrida.
- Sem julgamento público de plano; a leitura do coach é opt-in.

### 3.3 Qualidade de produto

- O usuário escolhe o que entra no vídeo, com presets e limites de legibilidade.
- Dado ausente fica ausente (nunca 0), com motivo claro.
- Prévia animada antes de gerar.
- Exporta em segundo plano, com ETA, cancelamento e notificação.
- Funciona offline com cache ou com pôster de rota.

---

## 4. As lacunas, priorizadas

### P0 — quebra a percepção de qualidade hoje

1. **Área segura de Reels/TikTok.**
2. **Métricas configuráveis** (o pedido explícito do usuário).
3. **Privacidade de início/fim.**
4. **Encode com cor declarada + perfil High + bitrate adequado.**
5. **Projeção exata do mapa** (`pixelForLatLng`), eliminando o port manual.

### P1 — elevam de “bom” para “ótimo”

6. **Timeline por esforço** e seleção honesta de splits.
7. **Áudio AAC** (batida procedural ou trilha local).
8. **Formatos e durações** (9:16, 4:5, 1:1; auto/15/30/45/60).
9. **Capa 4:5 + pôster de rota.**
10. **Recorde / prova / primeira corrida.**
11. **Fallback offline** (rota sobre fundo escuro ou pôster).

### P2 — apostas maiores

12. **Story Studio** como tela modal dedicada.
13. **Câmera com 3–4 keyframes** de pitch/bearing (não 3D real).
14. **Renderer em GPU** (`HardwareRenderer`/GL) para export mais rápido.
15. **Protomaps/PMTiles self-hosted** e pacotes regionais offline.
16. **Year in Review** (Wrapped) com o histórico do atleta.

---

## 5. Mapa: “nível Google Maps” sem violar licença

### 5.1 O bloqueio legal do Google

Os Termos do Google Maps Platform dizem, na parte geral:

> “Customer will not export, extract, or otherwise scrape Google Maps Content
> for use outside the Services.”

Um vídeo MP4 com tiles do Google é conteúdo exportado para uso fora do serviço.
Não é questão de plano pago, cota ou chave: **é proibido**. O `GoogleMap.snapshot()`
existe tecnicamente, mas não pode ser usado para gerar um vídeo compartilhável.
Isso precisa entrar no ADR 0015 como motivo de rejeição — hoje o documento diz
“custo”, o que é uma justificativa mais fraca e tecnicamente imprecisa.

### 5.2 Comparativo dos provedores

| Provedor | Qualidade de mapa | Custo | Chave | Licença para vídeo | Offline | Privacidade | Esforço | Veredito |
|---|---|---|---|---|---|---|---|---|
| **Google Maps** | Excelente (POIs/endereços) | Pago por uso | Sim | **Proibido exportar** | Limitado | Terceiro | Baixo | **Não usar** |
| **Mapbox** | Excelente cartografia/3D | MAU + tiles | Sim | Exige contrato; static API ≤1280 px | Parcial | Terceiro | Médio | Evitar no MVP |
| **MapTiler** | Muito boa (Outdoor/Dataviz, hillshade, satélite) | US$ 30/mês (Flex) | Sim (User-Agent) | Termos citam vídeo com atribuição, mas proíbem “manipular” pixels | Parcial | Terceiro | Médio | Plano B pago, com revisão jurídica |
| **OpenFreeMap** | Boa (OSM, liberty) | Grátis | Não | OSM/ODbL + estilo aberto | Cache | Terceiro (região aproximada) | Baixo | **Agora** |
| **Protomaps/PMTiles** | Boa, estilo próprio | R2: armazenamento + requests; egress grátis | Não | OSM/ODbL; controle total | **Sim** | CDN própria ou bundled | Alto | **Depois** |
| **Esri World Imagery** | Satélite excelente | Licença comercial | Não/API | Uso comercial exige licença | Cache | Terceiro | Baixo | Risco atual; trocar |

### 5.3 O que “nível Google” significa para corrida

Para um vídeo de corrida, o que faz o mapa parecer premium não é POI nem
endereço. É:

- geometria precisa de ruas, parques, trilhas e ciclovias (OSM é forte nisso);
- contraste e hierarquia visual (o Google usa um estilo genérico, não o da sua marca);
- relevo sombreado e satélite quando fizer sentido;
- labels legíveis em 1080p;
- rota como protagonista, não o mapa.

Um estilo autoral OSM pode ser **mais bonito** que o Google Maps para esse caso.
O caminho não é licenciar o Google; é desenhar o mapa do MeuCoach.

### 5.4 Recomendação em três camadas

**Camada 1 — agora (baixo risco, zero custo):**
- Manter OpenFreeMap como fonte de tiles.
- Criar um estilo **“MeuCoach Dark”** (grafite, labels brancos, parques/água
  dessaturados, rota lima) e um **“MeuCoach Light”**.
- Esconder POIs, transporte e vias menores; manter cidade, bairro, parques,
  água e avenidas.
- Atualizar para **MapLibre 13.7.1 OpenGL** (`android-sdk-opengl`): o AAR é
  ~15,8 MB e o `.so` arm64 é 11,0 MB, **menor** que o 12.3.1 atual (13,2 MB).
  Ganhos: `pixelForLatLng`, correções de estabilidade, PMTiles, color-relief.

**Camada 2 — próximo ciclo (privacidade e offline):**
- Protomaps/PMTiles em Cloudflare R2 (Brasil ou Nordeste), com `pmtiles://`.
- Estilo próprio versionado; sem chave; custo marginal quase zero.
- Pacote regional opcional para download offline.
- Satélite: substituir Esri por MapTiler Satellite (pago) ou Sentinel-2
  cloudless (grátis, menor resolução) — decidir com licença.

**Camada 3 — se o produto justificar (premium):**
- MapTiler Cloud com estilo customizado, satélite e hillshade.
- Exige revisão da cláusula “No Manipulation or Modification” e do “No
  Commercial Derivative Work”; pedir confirmação por escrito para vídeo.
- Chave restrita por User-Agent (a MapTiler não tem restrição por package
  Android; User-Agent é spoofável).

### 5.5 3D real: não prometer ainda

O MapLibre Native **ainda não implementa o `terrain` do style spec** (o
Terrain3D está em desenvolvimento em 2026). O que existe é `raster-dem` +
`hillshade`/color-relief, que é relevo sombreado, não terreno 3D. Para
“Google Earth” de verdade seria preciso outro engine (Mapbox, com custo/licença)
ou um renderer de DEM próprio — esforço desproporcional agora.

O caminho realista para o “wow” é:
- snapshot com pitch 45–55° (já suportado);
- 3–4 keyframes de câmera (pitch/bearing/zoom) ao longo da rota;
- `pixelForLatLng` para a rota ficar exatamente sobre as ruas;
- hillshade/color-relief para relevo;
- transições escondidas em cards, não crossfade longo.

### 5.6 O erro do ADR 0015 que precisa ser corrigido

O adendo de 2026-11-04 diz:

> “O snapshotter não expõe `Projection`, `getMapAsync` nem `getMap()`; só
> `addImage`, `getLayer`, `getSource`, `setObserver` e `Options.withPixelRatio`.”

O `MapSnapshotter` realmente não expõe `Projection`, mas o **`MapSnapshot`
entregue no callback** expõe:

```kotlin
public final native PointF pixelForLatLng(LatLng)
public final native LatLng latLngForPixel(PointF)
```

Confirmado por `javap` no AAR 12.3.1 em 13/11/2026. O port `StoryMapProjection`
pode ser substituído por uma projeção extraída do próprio snapshot (ou mantido
apenas como fallback/teste de paridade). Isso remove a maior fonte de
desalinhamento em mapas inclinados e simplifica o código.

---

## 6. Métricas: catálogo, presets e regras

### 6.1 Catálogo proposto

| Métrica | Fonte | Disponível quando | Formato |
|---|---|---|---|
| `DISTANCE` | `distanceM` | > 0 | `5,02 km` |
| `DURATION` | `durationSec` | > 0 | `28:14` |
| `DATE` | `StoredActivity.localDate` | sempre | `13 nov` |
| `AVG_PACE` | `averagePace` ou derivado | > 0 | `5:37 /km` |
| `AVG_HR` | `averageHeartRate` ou samples | > 0 | `152 bpm` |
| `MAX_HR` | `maxHeartRate` ou samples | > 0 | `178 bpm` |
| `CALORIES` | `calories` | > 0 | `312 kcal` |
| `ELEVATION_GAIN` | `elevationGainM` ou altitude | ≥ 0 e fonte | `+84 m` |
| `CADENCE` | `averageCadenceSpm` ou samples | > 0 | `172 spm` |
| `POWER` | `averagePowerW` | > 0 | `245 W` |
| `ZONES` | LTHR + samples | LTHR e FC | barra Z1–Z5 |
| `FASTEST_KM` | splits | ≥ 2 splits | `km 4 · 5:02` |
| `NEGATIVE_SPLIT` | splits | ≥ 4 splits | `2ª metade 3% mais rápida` |
| `CONSISTENCY` | splits | ≥ 3 splits | `variação 4%` |
| `RECORD` | histórico | ≥ 3 corridas + recorde | `melhor 5k` |
| `RACE` | `RaceEvent` | prova na data | `10k · 52:10` |
| `COACH_READ` | `StoryVerdict` | veredito != null | manchete + prova |

**Nunca** mostrar `0` por ausência. Se a métrica não existe, ela aparece
desabilitada com motivo (“sem FC”, “sem elevação”, “sem histórico”) ou é
omitida da prévia.

### 6.2 Presets

| Preset | Cabeçalho | Card por km | Encerramento | Extras |
|---|---|---|---|---|
| **Essencial** | distância, tempo | ritmo, FC, calorias | ritmo médio, FC média, calorias | splits, sem zonas |
| **Performance** | distância, tempo | ritmo, FC, elevação | ritmo médio, FC máx, recorde | splits + km mais rápido |
| **Fisiológico** | distância, tempo | ritmo, FC, cadência | FC média, FC máx, zonas | zonas na rota + barra Z1–Z5 |
| **Completo** | distância, tempo | ritmo, FC, elevação | ritmo, FC, elevação, cadência, zonas, recorde | tudo |
| **Minimal** | distância | ritmo | ritmo médio | sem splits, sem cards extras |
| **Prova** | distância, tempo | ritmo, FC | tempo, ritmo, recorde | nome da prova, medalha |

O preset é um ponto de partida; o usuário pode ajustar até 3 métricas por
seção. “Completo” não deve mostrar 6 números no card: ele distribui as métricas
entre card e encerramento.

### 6.3 Modelo de domínio sugerido

```kotlin
// core:domain/story
enum class StoryMetric { DISTANCE, DURATION, DATE, AVG_PACE, AVG_HR, MAX_HR,
    CALORIES, ELEVATION_GAIN, CADENCE, POWER, ZONES, FASTEST_KM,
    NEGATIVE_SPLIT, CONSISTENCY, RECORD, RACE, COACH_READ }

enum class StoryFormat { STORIES_9_16, REELS_9_16, FEED_4_5, SQUARE_1_1 }
enum class StoryDuration { AUTO, SEC_15, SEC_30, SEC_45, SEC_60 }
enum class StoryAudio { NONE, PROCEDURAL_BEAT, LOCAL_FILE }
enum class StoryQuality { STANDARD, HIGH, SMOOTH_60, MASTER_1440 }

data class StoryPrivacy(
    val maskStartEnd: Boolean = true,
    val radiusMeters: Int = 300,
    val hideMap: Boolean = false,
)

data class StoryRecipe(
    val format: StoryFormat = StoryFormat.REELS_9_16,
    val duration: StoryDuration = StoryDuration.AUTO,
    val template: StoryTemplate = StoryTemplate.ESSENTIAL,
    val headerMetrics: List<StoryMetric> = listOf(DISTANCE, DURATION),
    val cardMetrics: List<StoryMetric> = listOf(AVG_PACE, AVG_HR, CALORIES),
    val finalMetrics: List<StoryMetric> = listOf(AVG_PACE, AVG_HR, CALORIES),
    val showSplits: Boolean = true,
    val showZones: Boolean = false,
    val colorRouteByZone: Boolean = true,
    val showKmTicks: Boolean = true,
    val showAvatar: Boolean = true,
    val showHighlights: Boolean = true,
    val coachRead: CoachReadVisibility = CoachReadVisibility.PRIVATE,
    val mapStyle: StoryMapStyle = StoryMapStyle.MEUCOACH_DARK,
    val privacy: StoryPrivacy = StoryPrivacy(),
    val audio: StoryAudio = StoryAudio.NONE,
    val quality: StoryQuality = StoryQuality.HIGH,
) {
    init {
        require(headerMetrics.size <= 2)
        require(cardMetrics.size <= 3)
        require(finalMetrics.size <= 3)
        require(cardMetrics.distinct().size == cardMetrics.size)
        require(finalMetrics.distinct().size == finalMetrics.size)
    }
}
```

O `StoryPlanCalculator` recebe a receita e devolve um `StoryPlan` já resolvido:
métricas disponíveis, textos formatados, durações de segmento, pontos de
privacidade, paleta. O `StoryFrameRenderer` não decide nada — só desenha um
`StoryRenderSpec`. Isso mantém o renderer testável e o preview idêntico ao vídeo.

### 6.4 UI: Story Studio

A tela de detalhe da atividade não deve virar um formulário gigante. Proposta:

```
┌──────────────────────────────────────────────┐
│  Story do treino                    [fechar] │
│                                              │
│  ┌──────────────┐   Prévia animada 9:16      │
│  │   vídeo      │   ▶ 0:00 / 0:27            │
│  │   9:16       │   [Abertura] [Km] [Final]  │
│  └──────────────┘                            │
│                                              │
│  [Essencial] [Performance] [Fisiológico] ... │
│                                              │
│  FORMATO      [Reels 9:16] [Feed 4:5] ...    │
│  DURAÇÃO      [Auto] [15s] [30s] [45s] [60s] │
│  MAPA         [Escuro] [Clássico] [Satélite] │
│                                              │
│  NO CARD POR KM              2 de 3           │
│  [Ritmo ✓] [FC ✓] [Calorias ✓] [Elevação]    │
│  [Cadência] [FC máx]                         │
│                                              │
│  NO ENCERRAMENTO             3 de 3           │
│  [Ritmo médio ✓] [FC média ✓] [Zonas ✓] ...  │
│                                              │
│  PRIVACIDADE  [Ocultar início/fim ✓] 300 m   │
│  ÁUDIO        [Sem áudio] [Batida]           │
│                                              │
│  [ Gerar vídeo ]                             │
└──────────────────────────────────────────────┘
```

Regras de UX:

- Métricas indisponíveis ficam desabilitadas com motivo, não somem sem
  explicação.
- Limite de 3 por seção; ao tentar a 4ª, a mais antiga sai (ou um aviso).
- Prévia muda em tempo real; o mapa só é re-renderizado se estilo/formato/
  privacidade mudarem.
- “Salvar como padrão” persiste a receita em `SharedPreferences`
  (`meucoach_story`), no padrão de `StoryIntroStore`.
- Acessibilidade: chips de 48 dp, `contentDescription`, estado selecionado
  anunciado, sem depender só de cor.

### 6.5 Persistência e WorkManager

- `StoryRecipeStore` em `SharedPreferences` com JSON versionado (`org.json`).
- `StoryRecipeCodec` para o `Data` do WorkManager (`putString`, ~1 KB).
- O worker reconstrói `StoryPlan` + `StoryRecipe` e aplica a mesma resolução
  de métricas; nada de estado de UI.
- A paleta continua em `StoryPaletteCodec` (IntArray); não misturar com a
  receita.
- Testes: round-trip do codec, presets, limites, disponibilidade, goldens por
  preset, 1/2/3 métricas, formato.

---

## 7. Narrativa e visual: como sair do “print animado”

### 7.1 Timeline por esforço

Proposta determinística:

- Duração base do card: 1,8 s.
- Bônus para o km mais rápido: +0,6 s.
- Bônus para o km com maior FC (se houver): +0,3 s.
- Penalidade para o km mais lento: −0,3 s.
- Clamp: 1,2–3,0 s por card.
- Movimento entre cards: proporcional à distância, com easing.
- Encerramento: 3,0 s (ou 3,5 s se houver recorde/prova).
- Duração total normalizada para a duração alvo; se sobrar tempo, distribuir
  no movimento; se faltar, reduzir cards (não cortar o encerramento).

Efeito: um treino de tiros passa rápido nos tiros e demora no recover; um
longão constante tem ritmo de meditação. O vídeo conta o esforço.

### 7.2 Seleção de splits honesta

Hoje `capCards` pega `index % step == 0`, o que para 10 km gera 5 cards
(1, 3, 5, 7, 9) — não 8. E a barra de splits mostra só esses 5, dando a
impressão de 5 km.

Correção:

- Selecionar no máximo 8 cards, sempre incluindo o km 1, o último km completo,
  o km mais rápido e o km mais lento.
- A barra de splits mostra **todos** os kms como segmentos finos; os kms com
  card ficam acesos. Se não couber, agrupar por 2 km com rótulo.
- O card pode mostrar “km 3 de 10” para dar contexto.

### 7.3 Câmera com keyframes

Estado atual: 2 snapshots (overview 32°, follow 42°) e um reveal radial. É um
corte suave, não um voo.

Proposta:

| Keyframe | Tempo | Pitch | Bearing | Zoom | Uso |
|---|---|---|---|---|---|
| K0 | 0 s | 30° | 0° | fit | abertura |
| K1 | 1,5 s | 42° | heading do início | 1,6× | mergulho |
| K2 | 50% | 45° | heading do meio | 1,7× | follow |
| K3 | final | 35° | heading do fim | 1,2× | pullback |

- Cada keyframe é um `MapSnapshotter` próprio, com `pixelForLatLng` para a rota.
- Transições: corte curto (0,2–0,3 s) escondido no início de um card; evitar
  crossfade longo, que duplica labels.
- Memória: manter no máximo 2 snapshots vivos; carregar o próximo no boundary.
- Supersampling: 1,75× para keyframes (25 MB cada), 2,25× só no K1 se o
  aparelho aguentar; fallback 1,25× em `isLowRamDevice`.

Se o custo de 4 snapshots for alto, começar com 3 (abertura, follow, final) —
já elimina o “mapa parado”.

### 7.4 Estilo visual

- **Mapa escuro autoral** como padrão: base grafite, água azul-petróleo,
  parques verde dessaturado, labels brancos com halo escuro, vias principais
  finas, POIs ocultos. A rota lima vira o único acento.
- **Rota**: casing escuro + base cinza + trecho percorrido em lima ou zonas;
  ticks por km; pin com foto; halo suave.
- **Cards**: fundo `surface` com 92% de opacidade, borda sutil, tipografia
  Inter com números tabulares (`tnum`), labels em caixa alta pequenas.
- **Encerramento**: Manchete grande, prova curta, 3 métricas, linha do coach
  (se opt-in), logo e tagline. Sem “CORRIDA CONCLUÍDA” genérico competindo com
  a manchete.
- **Grão/vinheta**: manter, mas testar o reencode do Instagram; grão fino pode
  desaparecer. Preferir grão de baixa frequência.
- **Legenda do card**: remover “Ritmo = tempo por km · FC = frequência…” —
  é ruído para o público social. Se necessário, um “por km” no título.

### 7.5 Novos templates de encerramento

1. **Recorde pessoal** — “MELHOR 5K” com tempo e delta vs. anterior.
2. **Prova** — nome, distância, tempo, alvo vs. realizado, medalha.
3. **Primeira corrida** — “PRIMEIRA CORRIDA” com data e distância.
4. **Volta** — após 14+ dias sem correr (“DE VOLTA”).
5. **Consistência** — “4 SEMANAS SEGUIDAS” (se houver histórico).
6. **Negativa split** — já existe; manter.
7. **Zonas** — barra Z1–Z5 com tempo em cada zona; ótimo para fisiológico.

Cada template precisa de regra determinística, `ruleId`/`evidenceId` e teste.
Nada de LLM.

### 7.6 Pôster de rota (capa de feed)

Além do frame final 9:16, gerar um **pôster 1080×1350 (4:5)**:

- fundo escuro com gradiente sutil;
- rota como arte (linha lima, sem mapa/labels);
- distância, tempo e ritmo em tipografia grande;
- data e “MeuCoach” pequenos;
- opcional: mapa miniatura em um canto.

Esse é o formato que performa no feed. O frame final 9:16 continua sendo a capa
do story/Reels. Hoje o app chama o frame final de “pronta para o feed”, mas
9:16 é cortado no grid 4:5 — corrigir a copy e entregar o pôster.

### 7.7 Áudio

Três opções:

1. **Sem áudio (padrão para Instagram):** o usuário adiciona música no app;
   evita conflito de licença e de mixagem.
2. **Batida procedural:** sintetizada no aparelho, sem licença. BPM derivado da
   cadência (ex.: 170 spm → 85 BPM em meio-tempo), kick, hi-hat, sub, pad,
   whoosh por km e logo sonoro de 3 notas. Normalizar a −14 LUFS / −1 dBTP,
   AAC-LC 48 kHz stereo 192 kbps, fade in/out. Determinístico e testável.
3. **Trilha local:** o usuário escolhe um arquivo do celular; o app muxa.
   Licença é responsabilidade dele. Fase posterior.

Recomendação: oferecer “Sem áudio” e “Batida” no MVP; “Trilha local” depois.
Um vídeo mudo é aceitável no Instagram, mas um vídeo com batida própria se
destaca no WhatsApp/TikTok e em telas sem som.

---

## 8. Qualidade de encode e publicação

### 8.1 Especificação alvo

| Item | Valor | Motivo |
|---|---|---|
| Container | MP4 | Instagram/TikTok |
| Vídeo | H.264 High, Level 4.1 | Compatibilidade + compressão |
| Resolução | 1080×1920 (9:16) | Padrão Instagram |
| FPS | 30 (padrão), 60 opcional | 30 é cinematográfico; 60 para voo |
| Bitrate | 12–16 Mbps VBR/CBR | Ruas/labels sobrevivem ao reencode |
| Cor | BT.709, SDR, limited range | Evita cor lavada/apagada |
| Áudio | AAC-LC 48 kHz stereo 192 kbps | Padrão Instagram |
| Loudness | −14 LUFS, −1 dBTP | Normalização das plataformas |
| Keyframe | 1–2 s | Seek/reencode |
| Metadados | **sem GPS** | Privacidade |
| Arquivo | `meucoach-2026-11-13-5k.mp4` | Organização |

### 8.2 O que declarar no `MediaFormat`

```kotlin
format.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
format.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
format.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
format.setInteger(
    MediaFormat.KEY_PROFILE,
    MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
)
format.setInteger(
    MediaFormat.KEY_LEVEL,
    MediaCodecInfo.CodecProfileLevel.AVCLevel41,
)
format.setInteger(
    MediaFormat.KEY_BITRATE_MODE,
    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR,
)
```

Validar por aparelho: alguns encoders ignoram `KEY_COLOR_RANGE` no caminho
Surface; se ignorarem, manter o padrão e testar o resultado no Instagram. O
ganho de declarar BT.709 é evitar que o player “adivinhe” a transferência e
lave o contraste.

### 8.3 Números tabulares

Contadores de ritmo/FC/calorias mudam a cada frame. Com fonte proporcional, os
dígitos “dançam” e o número parece tremer. Usar:

```kotlin
paint.fontFeatureSettings = "tnum"
```

Ou uma variante monoespaçada da Inter. É um detalhe de 1 linha que separa
“app” de “vídeo”.

### 8.4 Área segura de 2026

| Plataforma | Topo | Base | Lados |
|---|---|---|---|
| Instagram Stories | ~250 px | ~250 px | ~60 px |
| Instagram Reels | ~250 px | **~420 px** | ~60 px |
| TikTok | ~130 px | **~484 px** | ~60 px |
| YouTube Shorts | ~200 px | ~300 px | ~60 px |

Recomendação: adotar **13% topo / 22% base / 5,5% lados** como padrão 9:16.
Isso cobre Reels e TikTok e continua elegante no Stories. Se o usuário quiser
mais área para o card, oferecer “Stories” como variante.

Impacto no layout atual:

- Card: de 66%–84% para **62%–78%**.
- Atribuição: de 86,3% (dentro da UI do Reels) para **dentro do card** ou
  logo acima dele (~60%); não pode ficar abaixo de 78%.
- Logo/header: 13,6%–22,8% já estão abaixo de 250 px; manter, mas revisar o
  logo em 13,6% (261 px) — está no limite.
- Rota: `routeBottom` de 920/1280 (71,9%) para ~76%? O card sobe, então a rota
  pode descer um pouco; recalcular `StoryLayout` por formato.

### 8.5 Capa e formatos

- **9:16** para Stories/Reels.
- **4:5 (1080×1350)** para feed; capa do grid.
- **1:1 (1080×1080)** opcional para feed antigo.
- **16:9 (1920×1080)** opcional para YouTube; exige layout horizontal, não é
  só trocar o tamanho.
- O pôster 4:5 é uma composição própria, não um crop do 9:16.

### 8.6 Validação real de qualidade

Antes de considerar “pronto”, fazer um teste controlado:

1. Exportar o mesmo treino em 10, 12 e 16 Mbps.
2. Subir no Instagram como Reel (conta de teste), baixar o arquivo reencodado.
3. Comparar nitidez de labels, grão, banding no céu/água e cor do lima/zonas.
4. Medir tempo de export e memória pico em um aparelho médio (não só S24 Ultra).
5. Repetir com 60 fps e 1440p, se implementados.

Sem esse teste, “melhor qualidade” é opinião. Com ele, vira critério de aceite.

---

## 9. Arquitetura proposta

### 9.1 Separação de camadas

```
core:domain/story            (Kotlin puro, testável, Kover)
  ├─ StoryMetric / StoryRecipe / StoryPreset
  ├─ StoryPlan / StoryTimeline / StorySegment
  ├─ StoryPlanCalculator (move de :app)
  ├─ StoryHighlightsCalculator (move de :app)
  ├─ StoryRecordDetector (novo)
  ├─ StoryPrivacyMask (novo)
  └─ StoryAvailability (novo)
        │
        ▼
:app/story                   (Android)
  ├─ StoryRenderSpec (resolvido)
  ├─ StoryFrameRenderer (desenha)
  ├─ StoryLayout (por formato)
  ├─ MapSnapshotProvider (MapLibre + projeção)
  ├─ StoryAudioComposer (AAC)
  ├─ CanvasStoryVideoExporter
  └─ StoryExportWorker / Coordinator
```

Ganhos:

- `StoryPlanCalculator` deixa de depender de `RouteData` da UI; `routeData`
  vira `core:domain/story/RouteProjection`.
- Métricas e presets testados sem Android; Kover sobe.
- O renderer recebe um `StoryRenderSpec` imutável: sem `if (métrica existe)`
  no meio do desenho.
- O worker e a prévia usam exatamente a mesma resolução.

### 9.2 Contrato do mapa

```kotlin
data class StoryMapSnapshot(
    val bitmap: Bitmap,
    val keyframe: StoryCameraKeyframe,
    val routePixels: List<PointF>,   // pixelForLatLng, já no tamanho do bitmap
    val startPixel: PointF?,
    val endPixel: PointF?,
)

interface StoryMapProvider {
    suspend fun snapshot(
        bounds: BoundingBox,
        keyframe: StoryCameraKeyframe,
        size: IntSize,
        style: StoryMapStyle,
    ): StoryMapSnapshot
}
```

O renderer escala `routePixels` de `bitmap.width` para `frameWidth` e aplica a
mesma matriz de câmera do mapa. Sem port de Mercator, sem deriva.

Para o pôster, o mapa pode ser omitido (rota sobre gradiente), então o provider
é opcional.

### 9.3 Cache e memória

- **Prévia:** snapshot em 1080×1920 (8 MB), não em 2430×4320 (42 MB).
- **Export:** snapshot em 1,75×–2,25× conforme RAM; máximo 2 vivos.
- **Estágio:** WebP 95/JPEG 95 para o mapa (sem alpha), PNG só para a capa.
- **Cache:** `cacheDir/stories/<activityId>/<recipeHash>/` com manifest JSON;
  LRU e limpeza no reset.
- **Ciclo de vida:** reciclar bitmaps ao sair da tela/trocar receita; não manter
  o mapa de export na `Preview` depois de estagiar.
- **Low RAM:** `ActivityManager.isLowRamDevice` → 1,25×, 30 fps, 2 keyframes.

### 9.4 Renderer em GPU (P2)

O gargalo atual é o desenho em Canvas de software (~36–50 ms/frame). Duas
saídas, ambas sem dependência nova:

1. **`HardwareRenderer` + `RenderNode`** (API 29+): gravar o frame em um
   `RenderNode` e renderizar direto no `Surface` do encoder. O Canvas vira
   display list e o GPU desenha mapa, rota, texto e grão. Fallback para o
   caminho atual em API 26–28.
2. **Pipeline GL dedicado**: mapa como textura, rota em shader, overlays em
   texturas; mais controle, mais código.

Antes disso, medir: separar tempo de `drawBitmap(map)`, `drawPath(route)`,
`drawText` e `drawGrain`. Se o mapa for 60% do frame, pré-compor o mapa em uma
textura e desenhar só overlays já ajuda.

### 9.5 Testes

- `StoryRecipeTest`: presets, limites, deduplicação, codec JSON.
- `StoryAvailabilityTest`: cada métrica com dado presente/ausente/zero.
- `StoryTimelineTest`: duração total, monotonicidade, cards selecionados,
  bônus do km rápido, ausência de sobreposição.
- `StoryPrivacyMaskTest`: raio, remoção de pontos, início/fim, rota curta.
- `StoryHighlightsTest`: recorde, prova, negative split, consistência.
- Goldens: Essencial/Performance/Fisiológico em 9:16 e 4:5; 1/2/3 métricas;
  tema escuro/claro; safe area Reels.
- `StoryExportProcessorTest`: receita no worker, cache, fallback offline.
- Teste de encode real só em aparelho (MediaCodec não roda em Robolectric).

---

## 10. Roadmap priorizado

### Sprint A — “Qualidade e controle” (3–5 dias)

**Objetivo:** o vídeo para de esconder números e o usuário escolhe o que entra.

- [ ] Safe area Reels/TikTok (13% topo, 22% base, 5,5% lados).
- [ ] `StoryRecipe` + `StoryRecipeStore` + codec do worker.
- [ ] Story Studio (modal) com presets e slots de 1–3 métricas.
- [ ] Catálogo de métricas com disponibilidade e motivo.
- [ ] Duração Auto/15/30/45/60.
- [ ] Encode: BT.709, High, bitrate 12–16 Mbps, `tnum`.
- [ ] Prévia de 3 frames (abertura/card/final) + scrubber.
- [ ] Remover legenda redundante do card; corrigir copy da capa.

**Aceite:** gerar dois stories do mesmo treino com métricas diferentes; ambos
cabem no Reels sem corte; goldens de 1/2/3 métricas; `check` verde.

### Sprint B — “Mapa e narrativa” (5–7 dias)

**Objetivo:** o vídeo parece filmado, não printado.

- [ ] `pixelForLatLng` no `MapBackgroundRenderer` (extrair pixels no callback).
- [ ] Remover/limitar `StoryMapProjection` a fallback de teste.
- [ ] 3 keyframes de câmera (abertura/follow/final) + cortes nos cards.
- [ ] Timeline por esforço e seleção honesta de splits (todos os kms na barra).
- [ ] Máscara de privacidade início/fim (300 m padrão).
- [ ] Fallback offline: pôster de rota sobre gradiente.
- [ ] Atualizar MapLibre para 13.7.1 OpenGL (spike + validação em aparelho).
- [ ] Pôster 4:5 separado.

**Aceite:** rota alinhada em snapshot inclinado sem port; story offline gera
pôster; 10 km mostra 8 cards + barra com 10 segmentos; privacidade visível.

### Sprint C — “Assinatura” (5–7 dias)

**Objetivo:** o vídeo é reconhecível como MeuCoach em 0,5 s.

- [ ] Áudio procedural (batida + whoosh + logo sonoro), AAC 48k, −14 LUFS.
- [ ] Estilo “MeuCoach Dark” (mapa escuro autoral).
- [ ] Template Recorde / Prova / Primeira corrida.
- [ ] Leitura do coach opt-in no encerramento.
- [ ] 60 fps opcional / 1440p master.
- [ ] Pôster e capa 4:5 no share sheet.
- [ ] Teste real de upload no Instagram (10/12/16 Mbps).

**Aceite:** vídeo com áudio e sem áudio; upload real aprovado; recorde
detectado com teste; 4:5 no feed sem crop.

### Sprint D — “Escala” (opcional)

- [ ] Protomaps/PMTiles em R2 + estilo próprio + pacote regional offline.
- [ ] `HardwareRenderer`/GL para export < 20 s.
- [ ] Year in Review.
- [ ] Templates de treino (intervalado, longão, esteira).

---

## 11. Riscos e mitigação

| Risco | Impacto | Mitigação |
|---|---|---|
| Google Maps proibido para export | Legal | Não usar; documentar no ADR |
| Esri World Imagery sem licença comercial | Legal | Substituir por MapTiler Satellite ou Sentinel-2 |
| MapTiler proíbe “manipular” pixels | Legal | Revisão jurídica/contrato antes de produção |
| MapLibre 13.x com regressões | Técnico | Spike + flavor OpenGL + testes em aparelho |
| 4 snapshots estouram memória | Técnico | 2 vivos, low-RAM fallback, WebP/JPEG |
| Áudio conflita com música do IG | Produto | Padrão “sem áudio”; batida opt-in |
| Métricas demais poluem | Produto | Limite de 3, presets, preview |
| Privacidade da rota | Confiança | Máscara 300 m, opção sem mapa |
| Reencode do IG suaviza grão | Qualidade | Teste real; grão de baixa frequência |
| Customização infinita | Escopo | Presets primeiro; “avançado” depois |

---

## 12. Travas dos documentos que eu questiono

Você pediu para avisar. Estas são as travas explícitas que eu recomendo rever:

1. **`AGENTS.md` §12 — “Não implementar social…”**
   O story já é uma superfície social de exportação. A trava deveria dizer
   “sem grafo social/feed/ranking”, não “sem exportação”. Proponho ajustar a
   redação para não bloquear a evolução do story.

2. **`PRODUCT.md` §5.1 — “explicar, não impressionar”**
   O `STORY_V2` já cria uma exceção para o vídeo. Áudio, mapa escuro e pôster
   são a mesma exceção; não é uma nova filosofia de produto.

3. **`STORY_V2.md` — “sem áudio”**
   Áudio é metade da percepção de qualidade em vídeo social. Proponho
   “sem áudio por padrão, batida procedural opt-in”, não “nunca”.

4. **ADR 0015 — “Google rejeitado por custo” e “sem projeção”**
   O motivo correto é legal (exportação proibida), e a projeção existe
   (`pixelForLatLng`). Corrigir o ADR evita decisões futuras erradas.

5. **ADR 0015 — 9:16/30 s fixos**
   O formato e a duração são decisões de produto, não de arquitetura. 4:5 e
   Auto são evoluções naturais.

6. **“Sem backend”**
   Tiles já são rede. Um CDN estático de PMTiles não é backend de dados; é
   infraestrutura de mapa. Se a regra for “nenhum servidor”, o offline com
   tiles bundled é a saída.

7. **“Nenhuma dependência nova”**
   A atualização do MapLibre não é dependência nova; é upgrade e reduz o `.so`.
   Media3 é opcional: o pipeline atual + AAC resolve sem ela.

8. **“O motor determinístico decide tudo”**
   Concordo. A receita não decide treino; só decide **o que mostrar**. Nenhuma
   métrica é inventada; ausência continua ausência.

---

## 13. Decisões tomadas em 13/11/2026

1. **Mapa:** OpenFreeMap + estilo autoral agora; Protomaps/PMTiles depois.
   Google e Mapbox fora; satélite fora do MVP por licença.
2. **Áudio:** vídeo mudo. O usuário coloca música no Instagram/TikTok.
3. **Métricas:** distância, ritmo médio e tempo total são essenciais e fixos;
   até 2 extras. Calorias por km removidas; calorias totais viram extra.
4. **Privacidade:** sem máscara de início/fim; rota completa por decisão do
   usuário 0.
5. **Qualidade:** o melhor disponível no MVP — 1080p30 16 Mbps, BT.709,
   H.264 High, mapa escuro autoral, 3 keyframes, pôster 4:5.
6. **Sequência:** Sprint A (controle/qualidade) primeiro.
7. **Arquitetura:** mover o puro para `core:domain/story`, criar `StoryRecipe`,
   `StoryRenderSpec` e o Story Studio; sem módulo novo agora.
8. **Leitura do coach:** continua privada na tela da atividade; opt-in no vídeo
   fica para depois.
9. **Fora do MVP:** áudio, privacidade, satélite, 3D terrain, 4:5 em vídeo,
   4K/HDR, Protomaps implementado, LLM, grafo social.

O plano executável está em
[`STORY_V3_PLANO_DE_ACAO.md`](STORY_V3_PLANO_DE_ACAO.md).

---

## 14. Evidências e referências

- **MapLibre 12.3.1 (AAR em uso):** `javap` confirma
  `MapSnapshot.pixelForLatLng(LatLng): PointF` e
  `latLngForPixel(PointF): LatLng`.
- **MapLibre 13.7.1:** `android-sdk-opengl-13.7.1.aar` (15,8 MB; `.so` arm64
  11,0 MB) disponível no Maven Central; `MapSnapshotter` mantém
  `pixelForLatLng`; `android-sdk` padrão usa Vulkan, por isso o flavor OpenGL.
- **MapLibre Native e 3D terrain:** issue #252 e newsletter de 2026 indicam
  Terrain3D em desenvolvimento; o `terrain` do style spec ainda não é
  implementado no Native.
- **Google Maps Platform Terms of Service:** proibição de exportar/extrair
  conteúdo para uso fora dos serviços.
- **MapTiler Terms:** atribuição para “printed maps, images or video recording”;
  cláusulas de “No Manipulation or Modification” e “No Commercial Derivative
  Work” exigem revisão.
- **Instagram Reels 2026:** 1080×1920, H.264, AAC, 30/60 fps, ~10–16 Mbps;
  safe zone de ~250 px no topo e ~420 px na base.
- **Media3:** 1.11.1 estável em nov/2026; Transformer suporta overlays, efeitos
  e mixagem de áudio, mas não é necessário para o MVP.
- **Código atual:** `app/src/main/kotlin/com/meucoach/app/story/*`,
  `ActivityDetailViewModel.kt`, `ActivityDetailScreen.kt`,
  `core/model/ActivityImport.kt`, `core/domain/story/StoryVerdictCalculator.kt`.

---

## 15. Anexo A — Quick wins de código (≤ 1 dia cada)

Estes são os ajustes que dá para fazer sem esperar a decisão de produto completa.
Todos têm risco baixo e efeito visível.

| # | Arquivo | Mudança | Risco |
|---|---|---|---|
| 1 | `StoryLayout.kt` | `SAFE_BOTTOM_FRACTION` para 0,78; card 0,62–0,78; atribuição dentro do card | Goldens mudam |
| 2 | `StoryFrameRenderer.kt` | `fontFeatureSettings = "tnum"` nos paints numéricos | Nenhum |
| 3 | `StoryFrameRenderer.kt` | Remover a legenda “Ritmo = tempo por km…” | Nenhum |
| 4 | `StoryVideoExporter.kt` | BT.709/SDR/limited, High, Level 4.1, 12 Mbps | Testar em aparelho |
| 5 | `StoryVideoExporter.kt` | Nome do arquivo com data/distância; nunca `setLocation` | Nenhum |
| 6 | `StoryExportCoordinator.kt` | Estágio do mapa em WebP/JPEG 95, não PNG | Testar decode |
| 7 | `ActivityDetailViewModel.kt` | Prévia em 1080×1920; worker renderiza o high-res | Menos memória |
| 8 | `StoryPlanCalculator.kt` | `capCards` inclui primeiro, último, mais rápido e mais lento | Teste novo |
| 9 | `StoryPlanCalculator.kt` | Calorias por km honestas (ou só no final) | Copy/teste |
| 10 | `MapBackgroundRenderer.kt` | Cache do style JSON em disco | Nenhum |
| 11 | `StoryPlanLoader.kt` | Aceitar `StoryRecipe` (primeiro passo do Sprint A) | Médio |
| 12 | `StoryFrameRenderer.kt` | Extrair `StoryCardRenderer`/`StoryFinalRenderer` | Refactor |

Ordem recomendada: 1 → 4 → 2 → 5 → 6 → 7 → 8 → 9 → 10. Os itens 11–12
preparam o Sprint A.

---

## 16. Uma frase

O story do MeuCoach já tem o motor; falta **direção de arte, controle do atleta
e acabamento de publicação**. O caminho não é mais features soltas: é uma
receita configurável, um mapa autoral e um encode que sobrevive ao Instagram.
