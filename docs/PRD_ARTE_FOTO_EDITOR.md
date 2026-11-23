# PRD — Arte com foto: estúdio de foto + posicionamento + escala

**Versão:** 0.3 · **Data:** 23/11/2026
**Status:** **Pronto para implementação.** Decisões de produto preservadas e decisões de engenharia fechadas após a auditoria independente. O [ADR 0061](adr/0061-art-photo-studio.md) foi revisado e aceito para implementação. A validação visual/memória no aparelho (F4/F5) é gate de release, não de início.
**Visão base:** ideia do Rafael (23/11/2026) — evoluir a Arte PNG transparente para um mini-estúdio: foto como fundo + posição dos textos/rota + tamanho da fonte, mantendo as escolhas atuais.
**Fonte verdade:** este PRD + [ADR 0058](adr/0058-art-png-route.md) + [ADR 0059](adr/0059-min-sdk-31.md) + [ADR 0060](adr/0060-art-png-9x16-layout.md) + [ADR 0061](adr/0061-art-photo-studio.md) + `docs/SPORTS_SCIENCE.md` + `docs/DESIGN_SYSTEM.md`.
**Depende de:** ADR 0058/0059/0060 implementados; PhotoPicker do sistema; nenhum backend/rede novo.

---

## 0. O que mudou na v0.3 (correções da auditoria)

| Área | v0.2 | v0.3 |
|---|---|---|
| Propriedade da foto | “1 foto por receita” ambíguo, com store global | **1 foto por atividade**; estilo global separado da foto; exclusão de atividade apaga a foto |
| Ciclo de vida | Trocar apagava a anterior “no ato” | **Import atômico**; só apaga a antiga após o commit; operações serializadas |
| Crop | `CropRect` livre + `cover` + “re-enquadra” | **Foco + zoom** normalizados; crop derivado por formato; troca de formato preserva foco/zoom |
| Escala de texto | `fontScale: Float` + baselines fixos | **`textScalePercent: Int` (80–130)** + `ArtLayoutEngine` medido; reflow de linha, padding e rota |
| Scrim | 32% e aviso “raro” | **58% (alpha 148)** em unidades de design + aviso calculado sobre o composto; só PHOTO |
| 9:16 | Foto 9:16 em conflito com a proibição de full-bleed | **Foto 9:16 full-bleed permitida**; a proibição continua para **mapa/tiles** e overlay opaco de mapa |
| Persistência | Codec v2 com tudo, fallback para `ArtRecipe()` | **Codec de estilo v2** + **codec de foto v1**; fallback por campo; arquivo ausente tem aviso |
| Export/reuso | Key só com receita | Key com **fingerprint do plano**; valida URI/arquivo; cache PNG+JPG |
| Memória | Downsample 2048 e bitmaps sem dono | **3072 no source**, decode no alvo/crop, ownership explícito e reciclagem |
| Acessibilidade | Crop por gesto + “botões de enquadrar” | **Setas + zoom acessíveis**, 48 dp, TalkBack e teste em 200%/360 dp |
| Fatias | F0–F7 com codec no fim | **F0–F8**, codec cedo, limpeza própria e docs no fim |

---

## 1. Contexto e problema

A Arte PNG atual é um **card transparente** (4:5/1:1/9:16) para colar sobre uma foto no Instagram — o usuário exporta o PNG e posiciona manualmente como figurinha. Funciona (alpha validado no aparelho), mas tem três fricções:

1. O posicionamento acontece **fora do app**, sem prévia real sobre a foto.
2. O layout é fixo: se a foto tem céu claro no topo, o texto branco some e o usuário não tem para onde mover.
3. O tamanho da fonte é fixo: não dá para encolher para caber nem aumentar para story.

Nenhum app de corrida oferece edição da arte **sobre a própria foto** com blocos móveis. A proposta transforma a aba Arte num estúdio simples, sem virar um editor genérico (sem filtros, stickers, drag livre ou IA).

A v0.3 corrige a auditoria independente de 23/11/2026: o produto estava bem delimitado, mas a engenharia não estava pronta (propriedade da foto, ciclo de vida, matemática de crop, reflow de escala, contraste real, codec e ordem de fatias).

---

## 2. Objetivo

Permitir, na mesma aba Arte do Story Studio:

1. **Foto de fundo** (upload via PhotoPicker) com **crop manual por foco + zoom** para o formato do card.
2. **Posicionamento** dos 4 blocos (distância, ritmo/tempo, extras, rota) por **presets + ajuste fino acessível**.
3. **Escala global da fonte** (80–130%) + **scrim automática** para legibilidade.
4. Manter tudo que existe: formato, extras ≤2, 8+8 swatches, 6 fontes, prévia fixa, compartilhar/salvar, reuso, persistência.

**Não é objetivo:** editor livre com drag, rotação, color picker, filtros, mapa no fundo, vídeo com foto, multi-foto, remoção de fundo por IA, sincronização em nuvem ou publicação automática no Instagram.

---

## 3. Decisões de produto (preservadas; revisões da v0.3 marcadas)

| # | Tema | Decisão |
|---|---|---|
| 1 | Modo de fundo | **Aditivo.** `TRANSPARENTE` (atual, default) + `FOTO`. Nada muda no transparente; foto é opt-in por atividade. |
| 2 | Posicionamento | **Presets + ajuste fino.** 3 templates (Topo / Centro / Base) + nudge por bloco via botões `−/+` horizontal/vertical (acessível, sem drag). Drag livre fica pós-MVP. |
| 3 | Granularidade | **Blocos independentes.** `DISTÂNCIA`, `RITMO_TEMPO`, `EXTRAS`, `ROTA` movem separado; cada um guarda um offset normalizado. O MVP entrega os 4. |
| 4 | Foto × formato | **Crop manual por foco + zoom.** PhotoPicker do sistema (sem permissão) → cópia privada → foco/zoom para o aspecto do formato. Default centro + zoom 1. Trocar formato mantém foco/zoom e re-deriva o crop; não pede crop de novo e preserva o ponto focal (o recorte exato muda com o aspecto, como esperado). |
| 5 | Fonte × contraste | **Escala global + scrim.** Stepper único 80–130% (passo 5%) + scrim preta a **58%** atrás da marca e dos blocos de texto **só no modo Foto**; casca da rota mantida. O aviso de contraste é calculado sobre o composto (scrim + foto). Sem scrim no transparente. |
| 6 | Export | **JPG q92 no modo Foto** (`…-arte-foto-4x5.jpg`, 1080 de largura). **PNG transparente inalterado** no modo Transparente. Share reutiliza por key estendida; Salvar sempre cria nova cópia. |
| 7 | Privacidade | **Local-only, 1 foto por atividade.** Trocar substitui e apaga a anterior somente após o commit; sem LRU/histórico. Cópia em `files/art_photo/`, fora do JSON/backup; Reset/Apagar tudo apaga; excluir atividade apaga. Sem upload, sem analytics. |
| 8 | Persistência | **Estilo global + foto por atividade.** `meucoach_art` guarda formato, extras, fonte, cores, layout e escala; um codec separado guarda a foto e o modo por atividade. v1 preserva os campos antigos e assume Transparente + layout default nos novos. Foto ausente → fallback transparente com aviso, nunca crash. **Posição/escala sempre visíveis** (também no transparente). |
| 9 | 9:16 | **Foto 9:16 full-bleed é permitida** (imagem final com foto). A proibição de “overlay 9:16 full-bleed opaco” continua valendo para **mapa/tiles** e overlay opaco de mapa. |
| 10 | Ciclo de vida | **Atômico e por atividade.** Import grava em temporário, valida, commita e só então apaga a foto antiga; export e reset são serializados; arquivos órfãos têm varredura. |

---

## 4. Escopo

### Dentro do MVP

- Toggle `Transparente | Foto` na aba Arte (default Transparente).
- `Escolher foto` (PhotoPicker) + `Trocar`/`Remover` + confirmação de remoção; thumbnail + estado vazio (“Escolha uma foto para o fundo”).
- Crop manual por **pan/zoom** (foco + zoom) + controles acessíveis (setas, `−/+`, `Centralizar`, `Enquadrar`); preview ao vivo no card.
- 3 templates (Topo/Centro/Base) + seletor de bloco (4 chips) + 4 botões nudge (←/→/↑/↓, passo 2% do canvas, 48 dp, TalkBack).
- Escala da fonte: stepper `− 100% +` (80–130%, passo 5%) com label “Tamanho do texto: 100%”.
- Scrim automática no modo Foto (58%, detalhe §5.4); aviso de contraste calculado sobre o composto.
- Renderer foto: recebe o bitmap já recortado/Orientado; desenha foto + scrim + blocos + rota; export JPEG 1080 via `GalleryImagePublisher` estendido.
- `ArtExportKey` estendido com `(activityId, planFingerprint, background, photo+crop, layout, textScale, format)`; reuso continua valendo.
- Persistência de estilo (`meucoach_art`) + foto/modo por atividade (`files/art_photo/`); reset, exclusão de atividade e varredura de órfãos.
- TalkBack por grupo, 48 dp, 200%/360 dp, prévia fixa com `contentDescription` incluindo modo/template/escala.
- Sem rota → mesmos estados atuais; modo Foto sem foto → CTA desabilitado com motivo.

### Fora do MVP (pós-MVP)

- Drag-and-drop livre, rotação de bloco, resize por bloco (só escala global no MVP).
- Color picker livre, fonte do usuário (.ttf), filtros/ajustes da foto (brilho/blur).
- Mapa/tiles no fundo, `ROUTE_CROP` por bbox, **overlay 9:16 opaco de mapa**.
- Multi-foto, colagem, stickers, texto livre digitado, remoção de fundo por IA.
- Sync/backup da foto na nuvem (Fase 2 decide).
- Publicação automática no Instagram.

> **Emenda explícita:** o 9:16 do modo Foto é uma imagem final opaca com foto, não um overlay de mapa. O ADR 0061 registra a emenda ao ADR 0058/0060.

---

## 5. Especificação

### 5.1 Modelo (`core:domain`, puro — sem `android.*`)

```kotlin
enum class ArtBackgroundMode { TRANSPARENT, PHOTO }
enum class ArtBlock { DISTANCE, PACE_TIME, EXTRAS, ROUTE }
enum class ArtTemplate { TOP, CENTER, BOTTOM } // Topo, Centro, Base

/** Offset em fração do canvas: dx sobre a largura, dy sobre a altura; 0 = template. */
data class ArtOffset(val dx: Float = 0f, val dy: Float = 0f) {
    init { require(dx.isFinite() && dy.isFinite()); require(dx in -0.5f..0.5f && dy in -0.5f..0.5f) }
}

data class ArtLayout(
    val template: ArtTemplate = ArtTemplate.TOP,
    val distanceOffset: ArtOffset = ArtOffset(),
    val paceTimeOffset: ArtOffset = ArtOffset(),
    val extrasOffset: ArtOffset = ArtOffset(),
    val routeOffset: ArtOffset = ArtOffset(),
    val textScalePercent: Int = 100, // 80..130, passo 5
) {
    init { require(textScalePercent in 80..130) }
    fun offset(block: ArtBlock): ArtOffset = when (block) { ... }
    fun withOffset(block: ArtBlock, offset: ArtOffset): ArtLayout = when (block) { ... }
}

/** Foco normalizado no source upright; zoom 1 = maior crop do aspecto alvo. */
data class ArtPhotoCrop(
    val focusX: Float = 0.5f,
    val focusY: Float = 0.5f,
    val zoom: Float = 1f, // 1..4
) {
    init {
        require(focusX.isFinite() && focusY.isFinite() && zoom.isFinite())
        require(focusX in 0f..1f && focusY in 0f..1f)
        require(zoom in 1f..4f)
    }
    fun resolve(format: ArtFormat, sourceWidth: Int, sourceHeight: Int): ArtCropRect? = ...
}

data class ArtPhotoRef(
    val photoId: String,       // UUID
    val activityId: String,    // dono; confere com a atividade aberta
    val crop: ArtPhotoCrop = ArtPhotoCrop(),
) {
    init { require(photoId.isUuid()); require(activityId.isNotBlank()) }
}

/** Retângulo derivado (não persistido), normalizado em 0..1 no source. */
data class ArtCropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite())
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f)
        require(left < right && top < bottom)
    }
}

/** SHA-256 estável de data, distância, duração, pace e rota normalizada. */
@JvmInline
value class ArtPlanFingerprint(val value: String)

data class ArtExportKey(
    val activityId: String,
    val planFingerprint: ArtPlanFingerprint,
    val recipe: ArtRecipe,
    val format: ArtFormat,
)

data class ArtRecipe(
    val format: ArtFormat = ArtFormat.PORTRAIT_4_5,
    val extras: List<StoryExtra> = emptyList(),
    val font: ArtFont = ArtFont.INTER,
    val textColor: ArtColor = ArtColor.WHITE,
    val routeColor: ArtColor = ArtColor.LIME,
    val background: ArtBackgroundMode = ArtBackgroundMode.TRANSPARENT,
    val layout: ArtLayout = ArtLayout(),
    val photo: ArtPhotoRef? = null,
)
```

Persistência (app/story, não domínio):

```kotlin
data class ArtPhotoRecord(
    val background: ArtBackgroundMode,
    val photo: ArtPhotoRef?,
)
```


- `require` de `ArtRecipe`: extras ≤2/sem duplicado (mantido); `background=PHOTO` **pode** ter `photo=null` apenas no estado transitório da UI (persistência exige foto, §5.7).
- `ArtPhotoCrop.resolve` é puro e determinístico:
  - base = maior retângulo de aspecto `format.width/format.height` dentro do source, centrado em (focusX, focusY), clampado às bordas;
  - em `zoom=1`, usa a base; em `zoom>1`, reduz largura/altura por `1/zoom` ao redor do foco, clampando;
  - retorna `ArtCropRect(left, top, right, bottom)` normalizado em 0..1; nunca amostra fora do source; source inválido (≤0) retorna `null`/erro, nunca divide por zero.
- `ArtLayoutSpec` (puro, unidades de design 1080) define margens, marca, gaps, safe areas e mínimos; `ArtLayoutEngine` (app/story) mede texto e calcula as caixas reais. O domínio não importa Android; o renderer não inventa coordenadas.
- `toggleExtra`, `EXTRA_CHOICES`, `MAX_EXTRAS`, `toStoryRecipe()` inalterados; layout/foto nunca entram em métrica.
- `resetArtRecipe` volta ao estilo default **e** limpa a foto da atividade (via stores, não no domínio).

### 5.2 Foto: import → crop → storage (app, borda)

#### Importação

- **Picker:** `ActivityResultContracts.PickVisualMedia` (PhotoPicker; sem `READ_MEDIA_IMAGES`, API 31+). Cancelar → sem estado, sem erro.
- **Orientação:** aplicar EXIF (rotação/espelhamento) no import; a cópia privada fica “em pé”. Testar as 8 orientações.
- **Cópia privada:** decodificar já no tamanho alvo (bounds + `inSampleSize`/`ImageDecoder.setTargetSampleSize`, sem alocar os 12 MP), downsample para lado maior ≤ **3072**, JPEG q90, em `files/art_photo/<photoId>.jpg`. O source é maior que 2048 para reduzir upscale em 9:16; o preview/export decodificam a região do crop no alvo.
- **Atomicidade:** gravar `<photoId>.jpg.tmp` → validar decode → rename para `.jpg`; gravar metadata `.json.tmp` → rename; **só então** apagar a foto anterior. Se qualquer etapa falhar, a foto antiga e o metadata antigo permanecem.
- **Erros:** URI revogada, decode inválido, HEIF/AVIF não suportado, disco cheio e IO entram em estado `Error` com retry; nunca apagam a foto atual.
- Originais SAF nunca são referenciados direto (grant expira).

#### Crop

- **Modelo:** foco normalizado (`focusX`, `focusY`) + `zoom` (1..4). O crop de cada formato é derivado; não existe `CropRect` persistido livre.
- **Default:** centro (0,5/0,5), zoom 1; `zoom=1` = maior crop do aspecto alvo dentro do source.
- **Troca de formato:** mantém foco/zoom; o loader resolve o novo aspecto; não pede crop de novo e preserva o ponto focal (o recorte exato muda com o aspecto). O preview mostra o resultado real.
- **UI:** etapa “Ajustar foto” em tela cheia dentro do Studio (não um bottom sheet aninhado), com preview, Cancelar/Aplicar:
  - gesto pan/zoom como acelerador;
  - setas acessíveis movem o foco em 2% do source;
  - `−/+` alteram zoom em 0,1 (48 dp, TalkBack);
  - `Centralizar` volta o foco ao centro; `Enquadrar` volta zoom a 1 e foco ao centro;
  - o zoom efetivo é limitado para manter o crop com lado menor ≥ 540 px; se o source já for menor, mostra aviso e permite com upscale filtrado.
- **Baixa resolução:** se o crop tiver lado menor < 1080 px, aviso “Foto em baixa — o JPG pode ficar suave”; se < 540 px, aviso mais forte. Nunca bloqueia o export, mas o resultado é honesto no preview.

#### Storage e propriedade

- **1 foto por atividade.** Nada de LRU, histórico ou poda silenciosa de fotos.
- `files/art_photo/`:
  - `<photoId>.jpg` — cópia privada;
  - `<sha256(activityId)>.json` — `ArtPhotoRecord` com `background`, `photoId`, foco e zoom;
  - o JSON carrega `activityId` e o store rejeita divergência.
- **Trocar:** novo import atômico; após o commit, apaga a foto antiga.
- **Toggle Transparente:** mantém a foto da atividade em espera (não aparece nem é exportada); voltar para Foto reutiliza a mesma foto.
- **Remover:** confirmação (“Remover a foto? A arte volta ao fundo transparente.”); grava `background=TRANSPARENT`, `photo=null` e apaga o arquivo.
- **Reset/Restaurar padrão:** limpa o estilo global e a foto da atividade atual.
- **Excluir atividade:** apaga metadata + arquivo daquela atividade após o delete do banco. A exclusão para a lixeira também apaga a foto; restaurar a atividade não restaura a foto (decisão de privacidade). O `DataResetCoordinator` cobre o “apagar tudo”.
- **Órfãos:** varredura no startup e após reset: apaga `.tmp` e `.jpg` sem metadata referenciando, com idade > 24 h; nunca apaga foto referenciada.
- **Privacidade:** a cópia privada tem EXIF removido pela re-codificação; backup/device-transfer desativados; sem upload, sem analytics; logs não incluem URI, caminho ou conteúdo.

### 5.3 Layout: templates, escala e offsets

#### Sistema de coordenadas

- Canvas em **unidades de design**: largura 1080, altura do formato (1350/1080/1920). Preview = 540 de largura; export = 1080.
- `ArtOffset.dx` é fração da **largura**; `dy` é fração da **altura**. Nudge = ±0,02 em cada eixo (2% da dimensão correspondente).
- Offsets são aplicados **depois** do template e **não** são multiplicados pela escala de texto.
- A rota é sempre quadrada e nunca distorce; offsets a transladam, não a esticam.

#### Constantes de design (`ArtLayoutSpec`)

| Constante | Valor (design px) | Uso |
|---|---:|---|
| `DESIGN_WIDTH` | 1080 | base de escala |
| `MARGIN` | 88 | margem lateral/padrão |
| `BRAND_TOP` | 84 (4:5/1:1/9:16 transparente) | topo da marca |
| `BRAND_HEIGHT` | 44 | logo |
| `BRAND_GAP` | 56 | marca → conteúdo |
| `BLOCK_GAP` | 32 | entre blocos de texto |
| `ROUTE_GAP` | 56 | texto → rota |
| `ROUTE_INSET` | 24 | inset da área quadrada |
| `MIN_ROUTE_SIDE` | 280 | rota mínima no MVP |
| `PHOTO_9X16_SAFE_TOP` | 250 | UI do story no topo |
| `PHOTO_9X16_SAFE_BOTTOM` | 340 | UI do story na base |

Tamanhos de texto base em 100% (design px): distância **135** em 4:5/9:16 e **117** em 1:1; rótulo de distância 24; rótulo de ritmo/tempo 22; valor de ritmo/tempo 52; extras 34; marca textual 30. O logo tem altura 44. `textScalePercent` multiplica os textos dos blocos; o logo/marca não escala.

- **Safe area:** 4:5/1:1 e 9:16 transparente usam margem 88; **9:16 Photo** usa topo 250 + base 340 + laterais 88 para não ficar sob a UI nativa do story.
- **Marca:** no 9:16 Photo, `BRAND_TOP = safeTop`; nos demais, 84. O logo é tintado pela cor do texto e tem altura fixa; não escala com `textScalePercent`.

#### Templates

- **TOP (Topo):** layout atual do ADR 0060 — marca no topo, distância à esquerda, ritmo/tempo à direita, extras abaixo, rota no restante. Em 100%/offset zero/transparente, o resultado deve preservar os goldens atuais.
- **CENTER (Centro):** o grupo de texto (distância + ritmo/tempo + extras) é verticalmente centralizado na área segura; a rota fica abaixo, quadrada e compacta.
- **BOTTOM (Base):** rota no topo, abaixo da marca; grupo de texto alinhado à base da área segura.
- O `ArtLayoutEngine` calcula as caixas com base na medição real do texto; os goldens de F4 congelam as posições resultantes. Não há coordenadas duplicadas entre domínio e renderer.
- Se o grupo de texto crescer com 130%, a rota encolhe até `MIN_ROUTE_SIDE`; se ainda não couber, o preview mostra o overflow real e a UI sugere reduzir a escala. Os defaults/templates não sobrepõem em 80/100/130 nos três formatos (gate de teste).

#### Escala de texto

- `textScalePercent` multiplica **tamanhos, altura de linha e paddings internos** dos blocos de texto (distância, ritmo/tempo, extras), incluindo `fittedSize` e a reserva medida da coluna direita.
- A marca/logo, a rota, os marcadores e a casca **não** escalam.
- O layout é recalculado por inteiro a cada mudança de escala; não há baselines fixos desconectados do tamanho da fonte.
- `fontScale` do sistema (Compose) não se confunde com `textScalePercent` da arte; o código usa nomes distintos.

#### Nudge e clamp

- O bloco selecionado é movido por 4 botões (48 dp) com `contentDescription` (“Mover distância para cima”); segurar pode repetir, mas o toque simples sempre funciona.
- O clamp é **por caixa do bloco + safe area**, usando o `ArtLayoutEngine` atual; o offset armazenado é o offset efetivo (não um valor que o renderer depois corrige em silêncio).
- A rota é clampada pelo quadrado + margem + raio dos marcadores; nunca sai do canvas.
- Sobreposição entre blocos é permitida no MVP (o usuário é o diretor de arte); os presets não se sobrepõem por default.

### 5.4 Texto e contraste sobre foto

- **Scrim (só PHOTO):** retângulo arredondado preto a **58% (alpha 148)**, raio **48 design px**, padding **36 design px**, atrás da marca e de cada bloco de texto presente (distância, ritmo/tempo, extras). Desenhada **sob** o conteúdo, **sobre** a foto. Transparente continua sem scrim.
- As unidades são de design (1080), não `dp` da tela; o export não muda com a densidade do aparelho. 36 px de design ≈ 12 dp em uma tela de 360 dp de largura.
- **Aviso de contraste:** no PHOTO, o texto é comparado ao composto “scrim sobre foto” nos extremos claro/escuro (branco e preto), com limiar 4,5:1; no TRANSPARENTE, mantém a comparação com os dois checkers. A rota continua comparada à casca.
- Com a scrim de 58%, o texto claro padrão (branco) passa no pior caso (foto branca). Cores escuras podem falhar e o aviso orienta trocar a cor ou mover o bloco; não prometemos AA para todas as combinações.
- A copy do aviso passa a sugerir “troque a cor do texto ou mova o bloco”.

### 5.5 Renderer (`app/story`)

- `StoryArtRenderer.render(plan, localDate, recipe, typeface, logo, width, height, photoBitmap?)`:
  - `TRANSPARENT` ignora `photoBitmap` (caminho atual, alpha 0 preservado);
  - `PHOTO` exige bitmap (nulo → retorna `null`; a UI mostra “Escolha uma foto”).
- **O bitmap recebido já está recortado, orientado e no tamanho alvo** pelo `ArtPhotoLoader`; o renderer desenha full-bleed e não refaz crop. O loader decodifica a **região do crop** (ex.: `BitmapRegionDecoder`/`ImageDecoder` com região), no alvo 540/1080, sem carregar o source 3072 inteiro duas vezes. Isso mantém o renderer determinístico e testável com um bitmap sintético.
- Ordem PHOTO: `drawPhoto(cover)` → `drawScrim` → `drawBrand/distance/paceTime/extras` (layout medido, template + offset, `textScalePercent`) → `drawRoute` (quadrada, template + offset).
- Preview 540 / export 1080. O loader aplica o crop derivado; se a região do crop for menor que o alvo, upscale com `FILTER_BITMAP_FLAG` e aviso. Um preview em voo é cancelado/descartado ao mudar receita/foto; o bitmap anterior é reciclado com segurança.
- `writeJpg(...)` novo para PHOTO (JPEG q92, sem alpha); `writePng` continua para TRANSPARENT.
- `ArtLayoutEngine` é compartilhado por preview, export e clamp; não há cálculo de posição na UI.

### 5.6 UX — aba Arte (delta)

1. Rótulo da aba: **“Arte”** (o nome “Arte PNG” deixa de ser exato no modo Foto; a tag de teste `story-tab-art` permanece).
2. Toggle `Transparente | Foto` abaixo do label de fundo (default Transparente).
3. Card `Foto de fundo` (só em PHOTO): thumbnail + `Escolher foto`/`Trocar`/`Remover` + `Ajustar enquadramento`.
4. Card `Posição`: **sempre visível** — templates (Topo/Centro/Base), seletor de bloco (4 chips) e nudges.
5. Card `Tamanho do texto`: stepper 80–130% com valor falado.
6. Prévia fixa compõe foto quando em PHOTO (mesma altura adaptativa); `contentDescription` ganha “, fundo foto, layout Topo, texto 100%”.
7. CTAs: `Compartilhar`/`Salvar` mantidos; labels condicionais **“Salvar JPG”** / **“Salvar PNG”**; confirmação “JPG salvo em Pictures/MeuCoach.” vs “PNG salvo…”. Erro de export é neutro: “Não foi possível gerar a imagem da arte. Tente novamente.”
8. Nomes: `…-arte-4x5.png` (transparente) vs `…-arte-foto-4x5.jpg` (foto). Copy: “Pôster automático: opaco, nasce com o vídeo. Arte: transparente (PNG) ou com sua foto (JPG).”
9. **Checker:** visível/ativo só em TRANSPARENTE; em PHOTO o label é “Foto de fundo” e o checker some.
10. **Remover:** confirmação; a arte volta a Transparente e a foto é apagada. **Trocar:** novo import; a antiga só é apagada no commit. **Toggle Transparente:** mantém a foto em espera; não exporta.
11. **Salvar como padrão:** salva **apenas o estilo** (formato, extras, fonte, cores, posição, escala). A foto é por atividade e é salva automaticamente ao importar/ajustar; a copy explica isso.
12. Estados: import/crop/export com loading, erro e retry; CTAs e controles de receita desabilitados durante operações destrutivas/export para evitar corrida.
13. A11y: templates `Role.RadioButton` em `selectableGroup`; nudge com direção + bloco; stepper com valor; crop com setas/zoom/48 dp; confirmação de remoção acessível; 200%/360 dp testados.
14. Foto sem rota: mesmos estados da arte sem rota; Foto sem foto: CTA desabilitado com “Escolha uma foto para o fundo”; foto ausente no load: aviso “A foto anterior não foi encontrada; a arte voltou ao fundo transparente.”

### 5.7 Persistência e export

#### Estilo (`ArtRecipeCodec` v2 em `meucoach_art`)

- Campos: `v`, `format`, `extras`, `font`, `textColor`, `routeColor`, `template`, `offsets` (distance/paceTime/extras/route com `dx`/`dy`), `textScalePercent`.
- v1 → v2: preserva formato, extras, fonte e cores; `template=TOP`, offsets 0, `textScalePercent=100`.
- Decode **por campo**:
  - enum desconhecido → default daquele campo;
  - offset inválido/ausente → (0,0) do bloco;
  - `textScalePercent` inválido → 100 (ou clamp 80/130) sem apagar o restante;
  - JSON corrompido/versão futura desconhecida → `ArtRecipe()` (documentado);
  - nunca lança para a UI.
- `ArtRecipeStore` guarda **somente estilo**; `save(recipe)` normaliza `background=TRANSPARENT`, `photo=null`; `reset()` limpa o estilo. A foto não entra no default global.

#### Foto (`ArtPhotoCodec` v1 + `ArtPhotoStore`)

- Metadata por atividade: `v`, `activityId`, `background`, `photoId`, `focusX`, `focusY`, `zoom`.
- `photoId` UUID; `activityId` confere com a atividade; `background=PHOTO` exige arquivo existente.
- Fallback:
  - metadata ausente → Transparente;
  - arquivo ausente/corrompido → Transparente + `photo=null` + aviso único; remove metadata inválido;
  - foco/zoom inválidos → clamp/default por campo;
  - nunca crash.
- O `ArtPhotoStore` é a camada de persistência da foto; `ArtPhotoImporter`/`ArtPhotoLoader` também são app/story; o codec de estilo continua puro/testável.

#### Export e reuso

- `ArtExportKey(activityId, planFingerprint, recipe, format)`:
  - `planFingerprint` = SHA-256 de `localDate`, distância, duração, pace médio e pontos normalizados da rota (5 casas). Evita reutilizar imagem de um plano/rota alterado.
  - `recipe` já carrega foto/foco/zoom/layout/escala/cores; quando `background=TRANSPARENT`, a key normaliza `photo=null` (foto em espera não invalida PNG).
- Reuso de Compartilhar somente se: key igual, arquivo em cache existe, URI do MediaStore ainda abre e a foto referenciada existe. Qualquer falha → regenera.
- O exporter confere a key/receita **antes de publicar** (não só depois): se a receita/foto mudou durante o render, descarta o bitmap e não cria linha no MediaStore. A VM também bloqueia os controles durante o export.
- Salvar sempre publica uma nova cópia (intenção explícita).
- Cache `cache/art`: PNG **e** JPG, limite total 8 arquivos, preserva o reusável; `.tmp` nunca conta.
- `GalleryImagePublisher.publishJpg(file)` ao lado de `publish`; MediaStore `Pictures/MeuCoach`, `MIME image/jpeg`, `IS_PENDING`, sem permissão (API 31+), limpeza da linha pendente em falha.
- `StoryFileNames.artPhoto(plan, format, stamp)` → `…-arte-foto-4x5.jpg`.
- `DataResetCoordinator`: limpa `meucoach_art`, `files/art_photo/`, `cache/art`, estado do exporter e `ArtPhotoStore`; não toca em `meucoach_story`.
- `deleteActivity`: apaga metadata/foto da atividade após o delete do banco; invalida a key do exporter; falha de arquivo entra na varredura de órfãos.

---

## 6. Arquitetura e técnica

### 6.1 Camadas

- **`core:domain` (puro):**
  - `ArtBackgroundMode`, `ArtBlock`, `ArtTemplate`, `ArtOffset`, `ArtLayout`, `ArtPhotoCrop`, `ArtPhotoRef`, `ArtCropRect`, `ArtLayoutSpec`;
  - validações (`require`) e `ArtPhotoCrop.resolve`;
  - `ArtExportKey` + `ArtPlanFingerprint` (hash determinístico);
  - `toStoryRecipe()` inalterado; nada de Android, Compose, Room, Hilt ou rede.
- **`app/story`:**
  - `ArtPhotoImporter` (PhotoPicker/EXIF/downsample/atomic),
  - `ArtPhotoStore` (metadata por atividade, load/delete/orphan),
  - `ArtPhotoLoader` (decode no alvo + crop),
  - `ArtLayoutEngine` (medição de texto, templates, offsets, clamp),
  - `StoryArtRenderer` (foto/scrim/blocos/rota, `writeJpg`/`writePng`),
  - `ArtExporter` (PNG/JPG, cache, reuse; evolui `ArtPngExporter`),
  - `ArtRecipeCodec` v2 (estilo), `ArtPhotoCodec` v1 (foto), `ArtRecipeStore`.
- **Export:** `GalleryImagePublisher.publishJpg`, `StoryFileNames.artPhoto`.
- **VM/UI:** `ActivityDetailViewModel` (não existe `StoryStudioViewModel`; o PRD v0.2 citava um nome que não existe) ganha `selectBackground/setTemplate/selectBlock/nudgeBlock/setTextScale/pickPhoto/cropPhoto/removePhoto`; `ArtStudioContent` ganha toggle, cards, crop e estados. A UI só emite intenção; medição/layout ficam no `ArtLayoutEngine`.
- **I/O e threads:** `ArtPhotoStore`/`ArtPhotoImporter`/`ArtPhotoLoader` rodam em `Dispatchers.IO`/`Default`; a VM expõe estado (loading/error), nunca toca disco na main. O store usa um mutex próprio (compartilhado com reset/export) para serializar import, troca, remoção e leitura.
- **Ciclo de vida do bitmap:** o loader entrega um bitmap de propriedade do chamador; o preview o mantém em cache de um item e o recicla ao trocar foto/receita; o export recicla após `writeJpg`; o renderer nunca recicla o bitmap recebido.

### 6.2 Fluxo

```text
Picker → ArtPhotoImporter (EXIF, ≤3072, JPEG q90, atômico)
       → ArtPhotoStore (metadata por atividade + arquivo privado)
Crop (foco/zoom; setas/gesto) → ArtPhotoStore
Studio (Foto) → recipe(background, photo, layout, textScale)
             → ArtPhotoLoader(crop, alvo 540/1080)
             → StoryArtRenderer(foto + scrim + blocos + rota)
             → preview 540 / JPG 1080 → MediaStore → share sheet
```

### 6.3 Testes (TDD)

**Domínio**
- `textScalePercent` fora de 80..130; offsets não finitos/fora de ±0,5; foco/zoom inválidos.
- `ArtPhotoCrop.resolve` para 4:5/1:1/9:16, source landscape/portrait, foco nas bordas, zoom 1/2/4; nunca fora do source; aspecto correto.
- `ArtLayoutSpec` por template/formato; `ArtPlanFingerprint` determinístico.
- `ArtRecipeCodec` v1→v2, v2 round-trip, campo inválido preserva os demais, JSON corrompido.
- `ArtPhotoCodec` round-trip, background/photo inválidos, `photoId` não UUID.

**Foto/store**
- Import com as 8 orientações EXIF; downsample 3072; JPEG q90.
- Troca atômica: falha no decode/IO mantém a antiga; sucesso apaga a antiga; `.tmp` não fica.
- Uma foto por atividade; isolamento entre atividades; troca de formato mantém foco/zoom.
- Arquivo ausente/corrompido → Transparente + aviso; metadata inválido removido.
- Órfãos: `.jpg` sem metadata é apagado após 24 h; referenciado não é.
- Reset e deleteActivity apagam metadata + arquivo; toggle Transparente mantém, Remover apaga.
- Remoção/troca durante export: controles bloqueados; se ocorrer por outra via, o resultado é descartado e não publicado no MediaStore.

**Renderer**
- PHOTO desenha foto, scrim, blocos e rota; TRANSPARENT alpha 0 por pixel.
- Scrim só em PHOTO; bounds/alpha 58%; contraste claro/escuro; marca incluída.
- `textScalePercent` 80/100/130 altera texto **sem sobreposição, clipping ou rota distorcida** em 4:5/1:1/9:16 e nos 3 templates.
- TOP/100/offset 0/TRANSPARENT preserva os goldens atuais; CENTER/BOTTOM têm goldens próprios.
- Offsets movem a bbox; clamp block-aware; rota sempre quadrada.
- Sem foto → `null`; `writeJpg` assinatura JPEG, 1080 de largura, sem alpha.
- `ArtContrast` PHOTO: branco sobre foto branca passa com 58%; preto pode avisar; rota na casca.

**Export/UI**
- `publishJpg` MIME `image/jpeg`, `Pictures/MeuCoach`, limpeza de pendente.
- Reuso: mesma key reusa; foto/layout/escala/formato mudam → regenera; URI morto/arquivo ausente → regenera; plano muda → regenera.
- Cache PODA PNG+JPG em 8; reset limpa `cache/art`.
- Toggle/cards visíveis por modo; “Salvar JPG/PNG”; erro neutro; checker só no transparente.
- Crop acessível (setas/zoom/48 dp/TalkBack), nudge, stepper, 200%/360 dp, estados empty/error/busy.
- Confirmação de remoção; toggle Transparente mantém a foto; retorno a Foto reutiliza.
- TalkBack da prévia inclui modo/template/escala.
- Nenhum dado/foto real nos fixtures/goldens.

**Aparelho**
- S24 Ultra + emulador API 31 + um aparelho/emulador de baixa RAM: picker real, import 12 MP, preview, export, reset, deleteActivity, 200%/360 dp.
- Validar safe area 9:16 Photo, scrim 58% no céu claro e legibilidade do texto.
- Regressão de vídeo, capa, pôster e arte transparente.

---

## 7. Fatias de execução

Cada sub-fatia é um PR pequeno, Conventional Commits. A ordem abaixo é a única sequência aprovada; codec de estilo antes de qualquer UI que salve.

| # | Fatia | Entregável verificável |
|---|---|---|
| **F0** | Especificação e ADR | Este PRD v0.3 + ADR 0061 revisado/aceito + `ArtLayoutSpec` numérico + decisões de ownership/crop/scrim/9:16. Sem código. |
| **F1** | Domínio | `ArtBackgroundMode/ArtBlock/ArtTemplate/ArtOffset/ArtLayout/ArtPhotoCrop/ArtPhotoRef/ArtCropRect/ArtLayoutSpec/ArtExportKey` + validações + `ArtPhotoCrop.resolve` + `planFingerprint`; `:core:domain:test` verde. |
| **F2** | Persistência de estilo | `ArtRecipeCodec` v2 (estilo) + migração v1→v2 + `ArtRecipeStore` style-only + testes de fallback por campo; sem foto. |
| **F3** | Foto: import/store/crop | `ArtPhotoCodec` v1, `ArtPhotoImporter`, `ArtPhotoStore`, `ArtPhotoLoader` (EXIF/3072/atômico/por atividade/órfãos); teste Robolectric + aparelho com URI fake (o picker real é F5). |
| **F4** | Renderer | `ArtLayoutEngine` + `StoryArtRenderer` PHOTO + scrim 58% + templates + `textScalePercent` + `writeJpg` + goldens; transparente sem regressão. |
| **F5** | UI Studio | toggle, cards, picker real, crop em tela cheia, preview composta, copy por modo, estados, a11y 48/200%/360; `ArtStudioContentTest` + goldens da aba. |
| **F6** | Export/share | `publishJpg` + `artPhoto()` + key com `planFingerprint` + reuso/URI/cache PNG+JPG; `ArtExportTest` + validação no aparelho. |
| **F7** | Limpeza e ciclo de vida | reset (`DataResetCoordinator`), deleteActivity, toggle/remover, órfãos, cache, invalidar exporter; testes de privacidade. |
| **F8** | Docs/DoD | `PROGRESS`, `ROADMAP`, `DESIGN_SYSTEM`, `DECISIONS`, DoD; gates `check`, `spotlessCheck`, `koverVerify`, `verifyRoborazziDebug --tests "*Art*"`. |

**Dependências e paralelização**
- F1 → F2 ∥ F3 → F4 → F5 → F6 → F7 → F8.
- F2 pode andar em paralelo com F3 depois de F1.
- F4 depende de F0/F1/F3 (foto real) e F2 (receita persistida).
- F5 depende de F2/F3/F4; F6 de F4/F5; F7 de F3/F6.
- F0 é gate: nenhuma fatia de código começa antes do ADR/spec aceitos.

---

## 8. Riscos e pendências

| Risco | Mitigação |
|---|---|
| OOM com foto 12 MP | Source ≤3072; decode no alvo 540/1080; crop por região quando necessário; bitmap ownership/recycle; spike F3/F4 no S24 e low-RAM. |
| Foto em baixa (crop pequeno) | Aviso em <1080; forte em <540; zoom limitado para manter ≥540; upscale filtrado; nunca prometer pixel inventado. |
| Scrim “suja” a foto | 58% + raio 48 + padding 36; só PHOTO; validação visual no aparelho; o aviso calcula o composto. |
| Nudge vira burden | Templates resolvem 90%; nudge opcional; passo 2%; clamp exato; reset no “Restaurar padrão”. |
| Key de reuso | `planFingerprint` + receita + validação de URI/foto; cache PNG+JPG com limite 8. |
| Privacidade | 1 foto por atividade; files privados; backup off; reset/delete/órfãos; logs sem URI/caminho. |
| Scope creep | §4 é lei; drag/filtros/stickers exigem PRD próprio. |
| 9:16 | Foto full-bleed permitida; mapa/tiles/overlay de mapa seguem proibidos; safe area 250/340. |
| Migração | SharedPreferences/JSON, sem Room; codec v1→v2 testado; downgrade para build v1 reseta o estilo (documentado). |
| Corrida export × foto | Controles de receita desabilitados durante export; store com mutex; resultado só publica se key/receita ainda conferem. |

### Pendências de validação (não bloqueiam o início)

- [x] Templates Topo/Centro/Base e 9:16 Foto full-bleed aprovados na v0.3.
- [x] JPG q92 vs PNG flattened — JPG q92.
- [x] 1 foto por atividade; sem LRU/histórico.
- [x] Nudge sempre visível; foco/zoom para crop.
- [x] Scrim 58% + aviso calculado sobre o composto.
- [x] Estilo global separado da foto por atividade; codecs separados.
- [ ] Spike de memória no S24 Ultra e low-RAM (F3/F4): import 12 MP + preview + export 1080.
- [ ] Validação visual da scrim/safe area no aparelho (F4/F5).
- [ ] Goldens finais de templates/escala (F4).

---

## 9. Definition of Done (feature completa)

- [ ] `./gradlew :app:assembleDebug`, `check`, `spotlessCheck`, `koverVerify` e lint/detekt verdes.
- [ ] `:app:verifyRoborazziDebug --tests "*Art*"` verde: transparentes intactos + `art-photo-4x5/1x1/9x16` + templates + 80/100/130 + `art-studio-tab-photo-*`.
- [ ] F1/F2/F3/F4/F5/F6/F7/F8 concluídas; ADR 0061 aceito; `DESIGN_SYSTEM` com scrim/safe area.
- [ ] PHOTO exporta JPG 1080 (MIME/JPEG/qualidade) com foto + scrim + blocos; TRANSPARENT sem regressão (alpha 0 por pixel).
- [ ] TOP/100/offset 0/TRANSPARENT preserva os goldens do ADR 0060; CENTER/BOTTOM sem sobreposição em 80/100/130.
- [ ] `Paint.hasGlyph`/`tnum` intactos nas 6 fontes; data “24 out 2026” inalterada.
- [ ] UI: prévia fixa, 360 dp, 200%, TalkBack, crop acessível, aviso de contraste, copy JPG/PNG, CTA share/save por modo, 48 dp.
- [ ] Foto: EXIF 8 orientações, 1 por atividade, atômica, troca/remoção/toggle, reset, deleteActivity, órfãos, cache PNG+JPG.
- [ ] Android 12+ (API 31+): PhotoPicker sem permissão; MediaStore; sem `WRITE_EXTERNAL_STORAGE`; backup/device-transfer off.
- [ ] Vídeo, capa, pôster e arte transparente sem regressão.
- [ ] Validado no S24 Ultra + emulador API 31 + low-RAM.
- [ ] `PROGRESS.md`, `ROADMAP.md`, `DESIGN_SYSTEM.md` e `DECISIONS.md` atualizados; nenhum dado/foto real no diff; NOTICE OFL intacto.

---

## 10. Decisões preservadas e emendas

**Preservadas**
- Modo aditivo, com Transparente default e caminho atual inalterado.
- Uma foto por **atividade**, local-only, sem LRU/histórico.
- PhotoPicker do sistema, cópia privada, sem permissão, sem upload.
- Domínio puro, renderer determinístico, sem LLM decidindo conteúdo.
- Quatro blocos independentes, presets + nudge, sem drag livre.
- Escala global 80–130%, sem resize/rotação por bloco.
- JPG q92 para foto; PNG transparente; rota sempre quadrada.
- Scrim só no modo Foto; casca da rota nos dois modos.
- 4:5/1:1/9:16; até 2 extras; 8 swatches; 6 fontes.
- 48 dp, TalkBack, 360 dp/200%.
- Reuso por `ArtExportKey`; Salvar sempre novo.
- Sem mapa/tiles, backend, analytics ou editor genérico.

**Emendas registradas**
- Foto 9:16 full-bleed permitida; proibição de overlay opaco de mapa mantida.
- Estilo default não carrega a foto; a foto é por atividade e o switch “Salvar como padrão” salva só o estilo.
- Crop livre `CropRect` substituído por foco + zoom.
- `fontScale` substituído por `textScalePercent` + reflow medido.
- Scrim 32% substituída por 58% + aviso sobre o composto.
- Codec único v2 substituído por codec de estilo v2 + codec de foto v1.
- F0–F7 substituídas por F0–F8, com codec antes da UI que salva.

---

## 11. Referências

- [`ADR 0058`](adr/0058-art-png-route.md) — card transparente.
- [`ADR 0059`](adr/0059-min-sdk-31.md) — Android 12+.
- [`ADR 0060`](adr/0060-art-png-9x16-layout.md) — 9:16 transparente, layout e prévia fixa.
- [`ADR 0061`](adr/0061-art-photo-studio.md) — Arte com foto (revisado na v0.3).
- `docs/SPORTS_SCIENCE.md` — métricas.
- `docs/DESIGN_SYSTEM.md` — cores, tipografia, AA, 48 dp.
- `docs/AUDITORIA_ARTE_PNG_UI_UX.md` — auditoria da arte transparente.
- Código atual: `ArtRecipe.kt`, `ArtRecipeCodec.kt`, `ArtRecipeStore.kt`, `StoryArtRenderer.kt`, `ArtStudioContent.kt`, `CanvasArtPngExporter.kt`, `GalleryImagePublisher.kt`, `StoryFileNames.kt`, `DataResetCoordinator.kt`.
