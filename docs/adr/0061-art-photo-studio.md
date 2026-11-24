# ADR 0061 — Arte com foto: fundo, layout móvel e escala

**Status:** **Aceito para implementação** — revisão pós-auditoria independente em 23/11/2026. Validação visual/memória no aparelho é gate de release (F4/F5).
**Data:** 2026-11-23
**Responsável:** usuário 0 (decisões de produto) e revisão técnica de implementação
**Documentos de origem:** [`PRD_ARTE_FOTO_EDITOR.md`](../PRD_ARTE_FOTO_EDITOR.md) v0.3, [`ADR 0058`](0058-art-png-route.md), [`ADR 0059`](0059-min-sdk-31.md), [`ADR 0060`](0060-art-png-9x16-layout.md), `docs/DESIGN_SYSTEM.md`, `docs/SPORTS_SCIENCE.md`

---

## 1. Contexto

O ADR 0058 fechou a Arte PNG como card transparente com layout fixo; o ADR 0060 adicionou o 9:16 transparente, o layout comum sem rodapé e a prévia fixa. O passo seguinte, pedido pelo usuário 0 e fechado no PRD v0.2, é um mini-estúdio: foto de fundo + blocos móveis + escala da fonte, mantendo as escolhas atuais (formato, extras, cores, fonte).

A auditoria independente de 23/11/2026 encontrou problemas de engenharia no PRD v0.2: propriedade e ciclo de vida da foto, crop entre formatos, reflow de `fontScale`, contraste real da scrim, fallback do codec e ordem das fatias. Esta revisão fecha essas decisões sem mudar o produto aprovado: modo Foto aditivo, uma foto local, presets + nudge, escala global e JPG q92. O PRD v0.3 é a especificação normativa; este ADR registra as decisões arquiteturais.

---

## 2. Decisão

### 2.1 Modo de fundo

- `ArtBackgroundMode { TRANSPARENT, PHOTO }` em `core:domain` (puro).
- `TRANSPARENT` é o default e o caminho atual: `ARGB_8888` sem `drawColor`, alpha 0 preservado, sem scrim, sem foto.
- `PHOTO` compõe foto + scrim + blocos e exporta **JPG q92** 1080 de largura (`…-arte-foto-4x5.jpg`). Sem alpha, sem PNG flattened no MVP.
- O modo Foto é **aditivo e por atividade**: o estilo default continua compartilhado, mas a foto e o modo efetivo pertencem à atividade aberta.

### 2.2 Foto: 1 por atividade, local, com crop por foco + zoom

- Picker: `PickVisualMedia` do sistema (PhotoPicker; sem permissão, API 31+). Cancelar é no-op.
- **EXIF:** orientação/espelhamento aplicados no import; a cópia privada fica “em pé”. Testar as 8 orientações.
- Cópia privada em `files/art_photo/<photoId>.jpg`, downsample para lado maior ≤ **3072**, JPEG q90. O source maior reduz upscale em 9:16; preview/export decodificam a região do crop no alvo. O import decodifica já no tamanho alvo (bounds + `inSampleSize`/`ImageDecoder`), sem alocar os 12 MP.
- **Atomicidade:** escrever `.jpg.tmp` → validar → rename; escrever `.json.tmp` → rename; só então apagar a anterior. Falha em qualquer etapa mantém a foto e o metadata antigos.
- `ArtPhotoRef(photoId, activityId, crop)` puro: `photoId` UUID, `activityId` dono, `crop` = `ArtPhotoCrop(focusX, focusY, zoom)` normalizado no source upright.
- **Crop manual** em etapa “Ajustar foto” (tela cheia dentro do Studio): pan/zoom por gesto como acelerador + setas/`−/+`/`Centralizar`/`Enquadrar` acessíveis (48 dp, TalkBack). Default centro + zoom 1; trocar formato mantém foco/zoom e re-deriva o crop, sem pedir crop de novo.
- **1 foto por atividade**: `Trocar` substitui e apaga a anterior no commit; `Remover` confirma e volta a Transparente; o toggle Transparente mantém a foto em espera. Sem LRU/histórico.
- Privacidade: fora do git, do export JSON e do backup; `resetArtRecipe`, `DataResetCoordinator` e exclusão da atividade apagam metadata + arquivo (inclusive ao mover para a lixeira; restaurar não recupera a foto); varredura de órfãos no startup/reset. Sem upload, sem rede, sem permissão nova.

### 2.3 Estilo × foto: dois stores, dois codecs

- `meucoach_art` guarda **somente estilo** (formato, extras, fonte, cores, template, offsets, escala) com `ArtRecipeCodec` **v2**. v1 → defaults novos; decode por campo; campo inválido não apaga o restante.
- `files/art_photo/<sha256(activityId)>.json` guarda o **registro por atividade** (`background`, `photoId`, foco/zoom) com `ArtPhotoCodec` v1. Arquivo ausente/corrompido → Transparente + `photo=null` + aviso; nunca crash.
- `ArtRecipeStore.save` normaliza `background=TRANSPARENT, photo=null`; a foto não vira default global. O switch “Salvar como padrão” salva só o estilo; a foto é salva automaticamente ao importar/ajustar.
- Essa separação resolve a promessa “excluir atividade apaga a foto” e impede que a foto de uma corrida apareça em outra.

### 2.4 Layout: templates, escala de texto e offsets

- `ArtLayout` tem quatro offsets explícitos (`distance`, `paceTime`, `extras`, `route`) e `textScalePercent: Int` (80..130, passo 5). Sem `Map` mutável e sem float de porcentagem.
- `ArtOffset` é fração do canvas: `dx` sobre a largura, `dy` sobre a altura, ±0,5; nudge de 0,02 por eixo. Offsets são aplicados depois do template e não são multiplicados pela escala.
- `ArtLayoutSpec` (puro, design 1080) define margens, marca, gaps, safe areas e mínimos. `ArtLayoutEngine` (app/story) mede o texto e calcula as caixas reais; preview, export e clamp compartilham o mesmo motor.
- Templates:
  - `TOP`: layout atual do ADR 0060 (marca, distância, ritmo/tempo, extras, rota).
  - `CENTER`: grupo de texto verticalmente centralizado; rota compacta abaixo.
  - `BOTTOM`: rota no topo; grupo de texto alinhado à base.
  - Em 100%/offset 0/transparente, `TOP` preserva os goldens atuais; `CENTER`/`BOTTOM` têm goldens próprios.
- `textScalePercent` multiplica tamanhos, altura de linha e paddings internos dos blocos; `fittedSize` e reserva da coluna direita são medidos. Marca/logo, rota, marcadores e casca não escalam. O layout é recalculado por inteiro (sem baselines fixos soltos).
- Safe area: 4:5/1:1 e 9:16 transparente = margem 88 nas laterais/topo e 150 na base (P3-08); **9:16 Photo = topo 250, base 340, laterais 88** (UI do story), em design px. O clamp do nudge é pela caixa do bloco + região livre do canvas (32 px, P3-08); os templates posicionam dentro da safe area. A rota é sempre quadrada (lado máximo 640, P3-08) e nunca sai do canvas.
- Defaults/templates não sobrepõem em 80/100/130 nos três formatos (gate de teste). Sobreposição por offset do usuário é permitida; o preview mostra o resultado real.

### 2.5 Contraste: scrim 58% + aviso sobre o composto

- No PHOTO, retângulo arredondado preto a **58% (alpha 148)**, raio **48 design px**, padding **36 design px**, atrás da marca e dos blocos de texto (distância, ritmo/tempo, extras). Desenhada sobre a foto, sob o texto.
- Unidades de design (1080), não `dp` de tela: o JPG não muda com a densidade do aparelho. 36 design px ≈ 12 dp em 360 dp de largura.
- A scrim de 32% do v0.2 foi rejeitada: branco sobre `#ADADAD` dá ~2,07:1 e não resolve o caso “céu claro”. Com 58%, o branco padrão passa no pior caso; cores escuras podem falhar e recebem aviso.
- `ArtContrast` no PHOTO compara texto com o composto “scrim sobre branco/preto”; no TRANSPARENTE mantém os checkers. A rota continua na casca (70%). O aviso orienta trocar a cor ou mover o bloco; não prometemos AA para todas as combinações.
- A marca/logo é incluída na scrim do PHOTO; a casca da rota continua nos dois modos.

### 2.6 Renderer, loader e export

- `StoryArtRenderer.render(..., photoBitmap?)`: o bitmap chega **já recortado, orientado e no tamanho alvo** pelo `ArtPhotoLoader`; o renderer desenha full-bleed e não refaz crop. PHOTO com bitmap nulo → `null`.
- O `ArtPhotoLoader` decodifica a **região do crop** (ex.: `BitmapRegionDecoder`/`ImageDecoder` com região) no alvo 540/1080, sem carregar o source 3072 inteiro; um preview em voo é cancelado/descartado e o bitmap anterior reciclado.
- Ordem PHOTO: foto → scrim → marca/distância/ritmo/tempo/extras (layout medido) → rota quadrada. Preview 540; export 1080.
- `writePng` (transparente) inalterado; novo `writeJpg(q92)` para foto.
- `ArtPhotoLoader` resolve `ArtPhotoCrop` para o formato, decodifica no alvo (540/1080) e aplica EXIF já no import; se o crop for menor que o alvo, upscale com filtro e aviso.
- `StoryFileNames.artPhoto(plan, format, stamp)` → `…-arte-foto-4x5.jpg`. `GalleryImagePublisher` ganha `publishJpg`; MediaStore `Pictures/MeuCoach`, `image/jpeg`, sem permissão (API 31+).
- `ArtExportKey(activityId, planFingerprint, recipe, format)`:
  - `recipe` carrega foto/foco/zoom/layout/escala/cores;
  - `planFingerprint` é SHA-256 de data, distância, duração, pace e pontos normalizados da rota;
  - reuso só com key igual, cache existente, URI do MediaStore abrindo e foto existente;
  - o exporter confere a key/receita antes de publicar; se a receita/foto mudou durante o render, descarta o bitmap e não cria linha no MediaStore;
  - Salvar sempre publica nova cópia; cache `cache/art` poda PNG+JPG (limite 8).
- Reset limpa `meucoach_art`, `files/art_photo/`, `cache/art` e o estado do exporter; deleteActivity apaga metadata/foto e invalida a key.

### 2.7 UI, copy e acessibilidade

- Aba “Arte” (o rótulo “Arte PNG” deixa de ser exato no modo Foto; a tag de teste não muda).
- Toggle `Transparente | Foto`; card Foto só em PHOTO; cards Posição e Tamanho sempre visíveis; prévia fixa compõe a foto.
- Copy condicional: “Salvar JPG”/“Salvar PNG”, “JPG salvo”/“PNG salvo”, erro neutro; checker só no transparente; “Salvar como padrão” salva só o estilo.
- Remover pede confirmação; Trocar só apaga a antiga no commit; toggle Transparente mantém a foto em espera.
- Controles: templates em `selectableGroup`/`Role.RadioButton`, nudges e stepper em 48 dp com TalkBack, crop com setas/zoom/centralizar/enquadrar, 200%/360 dp sem corte. A prévia tem `contentDescription` com modo/template/escala.
- Estados de import/crop/export com loading/erro/retry; controles de receita desabilitados durante export para evitar corrida.
- **I/O e bitmaps:** `ArtPhotoStore`/`ArtPhotoImporter`/`ArtPhotoLoader` rodam fora da main, com mutex compartilhado com reset/export; o loader entrega bitmap de propriedade do chamador, o preview mantém um item e recicla ao trocar, o export recicla após `writeJpg`, e o renderer nunca recicla o bitmap recebido.

### 2.8 9:16

- `PORTRAIT_9_16(1080, 1920)` foto full-bleed é **permitida** como imagem final com foto; não é overlay de mapa.
- A proibição do ADR 0058/0060 continua para **mapa/tiles** e overlay 9:16 opaco de mapa.
- O 9:16 Photo usa safe area de story (topo 250/base 340); o 9:16 transparente continua com margem 88, pois é figurinha posicionada pelo usuário.

---

## 3. Alternativas rejeitadas

| Alternativa | Motivo |
|---|---|
| Drag-and-drop livre | Gesto impreciso, sem equivalente em TalkBack/200%, custo de teste alto; presets + nudge resolvem 90% |
| Resize/rotate por bloco | Explode matriz de teste e colisões; escala global basta no MVP |
| LRU de 10 fotos | Usuário 0 escolheu 1 foto; agora 1 por atividade, sem poda silenciosa |
| PNG flattened na foto | ~4–6× maior que JPG q92 sem ganho visível em 1080 para share |
| Nudge só em PHOTO | Posição também ajuda no transparente na hora de colar a figurinha |
| Filtros/blur/ajustes da foto | Editor genérico; fora do “estúdio de corrida” |
| Multi-foto/colagem/stickers/texto livre | Escopo de editor social, não de card de treino |
| Mapa/tiles no fundo | Continua proibido pelo PRD base (§4 é lei) |
| Foto na nuvem/sync | Fase 2 decide; MVP é local-first |
| DataStore para a receita | Mesmo motivo do 0058: SharedPreferences + codec versionado resolve |
| `CropRect` livre persistido | Troca de formato ambígua, perde enquadramento, permite crop fora do source |
| Baselines fixos com `fontScale` | Sobreposição em 130%; não escala altura de linha/gaps |
| Scrim 32% | Não garante contraste de texto claro em foto clara (2,07:1) |
| Foto no default global | Foto de uma corrida apareceria em outra; quebra a promessa de exclusão |
| Codec único com tudo | Um campo inválido apagava a receita inteira; foto e estilo têm ciclos de vida diferentes |
| 2048 no source | Upscale frequente em 9:16 landscape |
| 9:16 foto proibida | O produto quer foto em story; a proibição era para overlay de mapa |
| F1–F7 com codec no fim | UI salvaria campos que o codec v1 descartaria |

---

## 4. Consequências

### Boas

- Transparente intacto: zero regressão de alpha, goldens e reuso.
- Foto por atividade: exclusão de atividade cumpre a privacidade; sem foto compartilhada entre corridas.
- Estilo × foto separados: “Salvar como padrão” não prende a foto; codecs tolerantes independentes.
- Crop por foco/zoom: troca de formato sem perda e controles acessíveis.
- `textScalePercent` + layout medido: 80/100/130 sem sobreposição; preview/export/clamp no mesmo motor.
- Scrim 58% + aviso sobre o composto: o caso “céu claro” deixa de ser uma promessa vazia.
- 9:16 foto permitido com safe area; mapa/tiles continuam fora.
- Atomicidade e cache: troca de foto não perde a única cópia; reset não deixa resíduo.

### Ruins / riscos

- **OOM:** source 3072; decode no alvo; ownership/recycle; spike no S24 e low-RAM.
- **Foto em baixa:** crop pequeno upscala; aviso <1080 e forte <540; zoom limitado.
- **Goldens novos:** `art-photo-*`, templates e 80/100/130; TOP/100/transparente precisa preservar o ADR 0060.
- **JPG vs texto:** q92 pode serrilhar micro-texto; validar no aparelho.
- **Sobreposição permitida:** usuário pode empilhar blocos; é liberdade de editor, não bug.
- **Scrim 58%** pode ser visualmente forte; o gate é contraste + validação no aparelho.
- **9:16 safe area** é uma convenção da plataforma; validar no Instagram e ajustar o token se necessário.
- **Dois stores** aumentam o número de arquivos; a varredura de órfãos e o deleteActivity são obrigatórios.

---

## 5. Implementação e aceite

Ordem: **F0** spec/ADR → **F1** domínio → **F2** codec de estilo/store → **F3** foto/import/crop → **F4** renderer → **F5** UI → **F6** export/share → **F7** limpeza/ciclo de vida → **F8** docs/DoD. F2 pode andar em paralelo com F3 depois de F1.

Aceite:

- `assembleDebug`, `check`, `spotlessCheck`, `koverVerify`, lint/detekt verdes.
- `:app:verifyRoborazziDebug --tests "*Art*"` verde: transparentes intactos + `art-photo-4x5/1x1/9x16` + templates + 80/100/130 + `art-studio-tab-photo-*`.
- PHOTO: JPG 1080 com MIME `image/jpeg`, foto + scrim 58% + blocos; TRANSPARENT alpha 0 por pixel.
- `textScalePercent` 80/100/130 sem sobreposição/clipping em 4:5/1:1/9:16 e 3 templates; rota quadrada.
- Foto: EXIF 8 orientações; 3072/q90; atômica; 1 por atividade; troca/remoção/toggle; reset/deleteActivity/órfãos.
- Codec de estilo v1→v2 por campo; foto ausente/corrompida → Transparente + aviso.
- Export: key com `planFingerprint`; reuso válido; URI morto regenera; cache PNG+JPG; reset limpa.
- UI: 360 dp/200%, TalkBack, crop acessível, 48 dp, copy JPG/PNG, checker só no transparente.
- API 31+: PhotoPicker sem permissão; MediaStore; sem `WRITE_EXTERNAL_STORAGE`.
- Sem dados/fotos reais no diff; NOTICE OFL intacto.

---

## 6. Referências

- [`PRD_ARTE_FOTO_EDITOR.md`](../PRD_ARTE_FOTO_EDITOR.md) v0.3
- [`ADR 0058`](0058-art-png-route.md) — card transparente
- [`ADR 0059`](0059-min-sdk-31.md) — Android 12+
- [`ADR 0060`](0060-art-png-9x16-layout.md) — layout comum + prévia fixa
- `docs/AUDITORIA_ARTE_PNG_UI_UX.md` — auditoria da arte transparente
- `app/story/StoryArtRenderer.kt`, `ArtRecipeCodec.kt`, `ArtRecipeStore.kt`, `CanvasArtPngExporter.kt`, `GalleryImagePublisher.kt`, `StoryFileNames.kt`, `DataResetCoordinator.kt`
- `core/domain/src/main/kotlin/com/meucoach/core/domain/story/ArtRecipe.kt`
