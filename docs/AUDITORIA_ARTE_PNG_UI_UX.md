# Auditoria UI/UX — Aba Arte PNG (Story Studio) — v2 acionável

**Data da auditoria original:** 23/11/2026
**Revisão de confronto e correção:** 23/11/2026
**Feature auditada:** Arte PNG da rota — card transparente 4:5/1:1 (ADR 0058, F2–F8)
**Documentos de referência:** `docs/PRD_ARTE_PNG_ROTA.md` v0.2,
`docs/adr/0058-art-png-route.md`, `docs/adr/0059-min-sdk-31.md`,
`docs/DESIGN_SYSTEM.md`, `AGENTS.md`.
**Natureza:** auditoria de UI/UX + plano de execução. Nenhum código-fonte,
teste ou outro documento foi alterado nesta revisão; o único arquivo editado
é este.
**Como usar:** este documento é a fonte de trabalho. Cada tarefa tem ID,
severidade, evidência, correção, teste e aceite. Não implemente itens
marcados como “não fazer”. Atualize o status das tarefas e o `PROGRESS.md`
a cada fatia.

---

## 0. Resumo executivo

A feature está **bem engenhada e dentro do escopo** (PRD §4 respeitado: sem
9:16, sem `ROUTE_CROP`, sem mapa, sem color picker livre). Domínio puro,
renderer com alpha por pixel, reuso por key, MediaStore sem permissão e
goldens verdes — tudo preservável.

O problema é de **fluxo, comunicação e estado**, não de motor. A auditoria
original acertou o diagnóstico central, mas misturou achados verificados com
falsos positivos, errou um exemplo de contraste, tratou sugestões como
requisito e deixou passar o bug mais grave: **o export pode entregar um PNG
de uma receita que já não está na tela**.

### 0.1 Veredito dos achados da v1

| Achado | Veredito v2 | Severidade |
|---|---|---|
| C1 — prévia não troca de fonte | **Confirmado** (explicação corrigida) | P0 |
| C2 — share sheet reabre sozinho | **Confirmado** (gatilho corrigido) | P0 |
| C3 — `Done`/`Error` obsoletos ao editar | **Confirmado** | P1 |
| A1 — falta copy pôster × arte | **Confirmado** (sem os “3 passos”) | P1 |
| A2 — prévia pequena e longe | **Confirmado** | P2 |
| A3 — cantos arredondados | **Cosmético / decisão** | P3 |
| A4 — feedback/cache/erro | **Parcial** (spinner é requisito; “Salvar sempre novo” é design) | P1/P3 |
| M1 — pills × segmento | **Refutado** | — |
| M2 — limite de extras invisível | **Parcial** (comparação errada) | P2 |
| M3 — swatches | **Parcial + exemplo invertido** | P2 |
| M4 — aviso de contraste | **Confirmado, correção incompleta** | P1 |
| M5 — “Salvar como padrão” | **Confirmado** | P3 |
| M6 — vazio duplicado | **Parcial; solução conflita com o PRD** | P3 |
| T1, T2, T4, T6 | **Válidos como P3** | P3 |
| T3 — 360 dp/200% | **Gap real; exemplo fraco** | P2 |
| T5 — warning AA | **Fechado: tokens passam** | — |
| T7 — preview × swatches | **Refutado** | — |
| **Novo:** export com receita antiga | **Não visto pela v1** | P0 |

### 0.2 Ordem de execução

1. **P0:** C1 (fonte), C2 (share), race do export.
2. **P1:** C3 (estado), A1 (copy), spinner (PRD §8), M4 (contraste).
3. **P2:** swatches, 360 dp/200%, limite de extras, prévia/scroll, cache/erro.
4. **P3:** T1/T2/T4/T6, M5, M6, A3, feedback de reuso.

### 0.3 O que NÃO fazer

- Não implementar M1 (não há segmento de formato no vídeo).
- Não implementar T7 (não há overflow; o `FlowRow` resolve).
- Não “consertar” T5 (tokens já passam AA).
- Não colapsar os controles no estado vazio: o PRD §8 exige controles
  visíveis e CTAs desabilitados.
- Não adicionar os “3 passos” como se fossem requisito: o PRD/ADR exigem
  só a linha “pôster × arte”.

---

## 1. Contexto, escopo e estado atual

### 1.1 A feature

A aba **Arte PNG** do Story Studio gera um card PNG com fundo transparente,
rota e métricas, em 4:5 (1080×1350) ou 1:1 (1080×1080). O usuário escolhe
formato, até 2 extras, cor do texto, cor do traçado e uma das 6 fontes. O
card é pensado para virar figurinha sobre uma foto no Instagram, não para
ser um overlay full-bleed.

### 1.2 Fluxo atual

```
Detalhe > openStoryStudio > aba Vídeo (default) > aba Arte PNG
  > prévia 540 px + checker escuro/claro + label "Fundo transparente"
  > formato (4:5/1:1) > extras (máx. 2) > cor texto + cor traçado
  > fonte + preview único > salvar-como-padrão + restaurar
  > Compartilhar (primário) / Salvar PNG (secundário)
  > share sheet / MediaStore Pictures/MeuCoach
```

### 1.3 O que está bom e deve ser preservado

1. **Domínio puro:** `ArtRecipe`/`ArtFormat`/`ArtColor`/`ArtExportKey` em
   `core:domain`, sem Android.
2. **Transparência por construção:** `StoryArtRenderer` sem `drawColor`,
   alpha 0 por pixel, rota em área quadrada sem distorção.
3. **Export barato e offline:** síncrono em 1080 px, sem Worker;
   `GalleryImagePublisher` extraído sem regressão de capa/pôster.
4. **Reuso por key:** `Compartilhar` reutiliza o PNG inalterado; `Salvar`
   sempre publica nova cópia (intenção explícita).
5. **Persistência isolada:** `meucoach_art` separado de `meucoach_story`;
   reset não cruza stores.
6. **Acessibilidade base:** 48 dp, `selected` + check, `contentDescription`
   por grupo, checkerboard com label, aviso de contraste.
7. **Goldens travando o essencial:** 4:5/1:1 + customizado sobre
   checkerboard + aba do Studio.

### 1.4 Guardrails (PRD §4 é lei)

Sem 9:16, sem `ROUTE_CROP`, sem recorte por bbox, sem mapa/tiles, sem color
picker HSV livre, sem 7ª fonte, sem fundo sólido permanente, sem dinâmico do
Material You. A arte é transparente; não prometemos AA sobre toda foto.

### 1.5 Mapa de arquivos

**Produção**

- `app/src/main/kotlin/com/meucoach/app/ui/activitydetail/ArtStudioContent.kt`
- `.../ui/activitydetail/StoryStudioSheet.kt`
- `.../ui/activitydetail/StoryStudioComponents.kt`
- `.../ui/activitydetail/ActivityDetailViewModel.kt`
- `.../ui/activitydetail/ActivityDetailUiState.kt`
- `.../ui/activitydetail/ActivityDetailScreen.kt`
- `app/src/main/kotlin/com/meucoach/app/story/ArtContrast.kt`
- `.../story/ArtPngExporter.kt`
- `.../story/GalleryImagePublisher.kt`
- `.../story/StoryArtRenderer.kt`
- `.../story/ArtRecipeStore.kt`
- `.../story/ArtFonts.kt`
- `core/domain/src/main/kotlin/com/meucoach/core/domain/story/ArtRecipe.kt`
- `core/designsystem/src/main/kotlin/com/meucoach/core/designsystem/component/McButtons.kt`
- `core/designsystem/src/main/kotlin/com/meucoach/core/designsystem/theme/McColors.kt`

**Testes**

- `app/src/test/kotlin/com/meucoach/app/ui/activitydetail/ArtStudioContentTest.kt`
- `.../ui/activitydetail/ActivityDetailViewModelTest.kt`
- `.../ui/activitydetail/StoryStudioScreenshotTest.kt`
- `.../ui/activitydetail/StoryStudioContentTest.kt`
- `app/src/test/kotlin/com/meucoach/app/story/ArtPngScreenshotTest.kt`
- `.../story/ArtPngExportTest.kt`
- `.../story/ArtContrastTest.kt`
- `core/domain/src/test/.../story/ArtRecipeTest.kt`

**Goldens**

- `app/src/test/screenshots/art-png-4x5-default.png`
- `art-png-1x1-default.png`
- `art-png-4x5-customized.png`
- `art-studio-tab-dark.png`

---

## 2. Método e confiabilidade do confronto

### 2.1 O que foi verificado

- Leitura linha a linha de `ArtStudioContent.kt`, `StoryStudioSheet.kt`,
  `StoryStudioComponents.kt`, `ActivityDetailViewModel.kt`,
  `ActivityDetailUiState.kt`, `ActivityDetailScreen.kt`,
  `ArtPngExporter.kt`, `ArtContrast.kt`, `StoryArtRenderer.kt`,
  `ArtRecipe.kt`, `ArtFonts.kt`, `ArtRecipeStore.kt`,
  `GalleryImagePublisher.kt`, `McButtons.kt`, `McColors.kt`.
- Leitura do PRD (§8, §12), ADR 0058 (§2.4, §2.7–§2.9, §5) e
  `DESIGN_SYSTEM.md` (§3.4, §8.1, §10).
- Leitura dos testes existentes (`ArtStudioContentTest`, `ArtPngExportTest`,
  `ArtPngScreenshotTest`, `StoryStudioScreenshotTest`, trechos de
  `ActivityDetailViewModelTest`).
- Cálculo dos contrastes com a fórmula WCAG usada em `ArtContrast.kt`
  (script local, sem alterar arquivos).

### 2.2 Limitações

- Não rodei a suíte completa nesta revisão; a análise é estática + leitura
  dos testes.
- Os números de contraste são calculados sobre cores opacas; a arte real
  compõe com a foto, então o aviso continua sendo consultivo.
- A reprodução em aparelho dos bugs C1/C2 ainda deve ser feita pelo usuário
  0 depois das correções.

### 2.3 Números de contraste que sustentam os achados

| Par | Razão | Leitura |
|---|---|---|
| Branco `#F4F6F7` × surface claro `#FFFFFF` | **1,08:1** | swatch branco some no tema claro |
| Borda clara `#E3E6E9` × `#FFFFFF` | **1,25:1** | borda não resolve sozinha |
| Check branco × ciano `#5BD0E0` | **1,82:1** | falha no 3:1 (elemento não textual) |
| Check branco × azul `#6C9CFF` | **2,68:1** | falha no 3:1 |
| Check preto × coral `#FF6B5E` | **7,52:1** | passa; o exemplo da v1 está invertido |
| Rota preta `#0A0B0D` × casca `#08090B` | **1,01:1** | rota preta é invisível sobre a casca |
| Rota preta × checker claro `#E3E6E9` | **~15,7:1** | o aviso atual não dispara no claro, mas a casca continua escura |
| Warning escuro `#FFB454` × `#191C21` | **~9,7:1** | passa AA |
| Warning claro `#8A5200` × `#FFFFFF` | **~6,4:1** | passa AA; já documentado no DESIGN_SYSTEM §3.4 |

---

## 3. Tarefas P0 — correção

### P0-1 — A prévia não troca de fonte (C1)

**Status:** [ ] pendente
**Tipo:** bug de estado
**Severidade:** P0

**Evidência**

- `ArtStudioContent.kt:314`:
  `val typeface = remember(context) { state.artRecipe.font.typeface(context) }`.
- `ArtStudioContent.kt:328-334`: `produceState` tem `plan`, `recipe`,
  `darkPreview` e `retry` como chaves — a receita muda e o produtor
  reinicia, mas captura o `typeface` velho.
- `ArtPngExporter.kt:88` usa `recipe.font.typeface(context)` na hora; logo,
  prévia e export divergem.

**Causa**

`remember(context)` não tem a fonte como chave. Recomporem não recalcula o
`remember`; o valor antigo persiste até o composable sair e voltar (troca de
aba, rotação) — não “até recomposição por outro motivo”, como dizia a v1.

**Correção**

1. Mover `val recipe = state.artRecipe` para antes do `typeface`.
2. Trocar para `remember(context, recipe.font) { recipe.font.typeface(context) }`.
3. Opcional: incluir `typeface` nas chaves do `produceState`; é redundante se
   o `remember` estiver correto, mas não faz mal. **Não** confie só nisso:
   sem corrigir o `remember`, a chave nova não muda o valor capturado.
4. Se quiser um sinal determinístico para teste, guardar a receita
   renderizada no estado `Ready` (ex.: `Ready(bitmap, renderedFont)`) e
   expor um `testTag("art-preview-ready-${font.name.lowercase()}")`.

**Teste de regressão (TDD)**

- Em `ArtStudioContentTest`, usar estado mutável: abre a aba Arte com
  `ArtFont.INTER`, espera `art-preview-ready`; troca para `ArtFont.ANTON`;
  espera o novo render (tag por fonte ou golden) e compara com o golden
  `art-preview-anton` (ou com o bitmap do Inter). O debounce de 120 ms exige
  `waitUntil`, não apenas `waitForIdle`.
- O teste deve **falhar** no código atual e passar após a correção.

**Aceite**

- Trocar Inter→Anton atualiza a prévia principal sem sair da aba.
- Prévia e export usam a mesma fonte.
- Golden novo/atualizado apenas se o layout mudar.

**Arquivos:** `ArtStudioContent.kt`, `ArtStudioContentTest.kt`,
`StoryStudioScreenshotTest.kt` (se o golden for afetado).

---

### P0-2 — O share sheet reabre sozinho (C2)

**Status:** [ ] pendente
**Tipo:** bug de evento/estado
**Severidade:** P0

**Evidência**

- `ArtStudioContent.kt:240-243`:
  `LaunchedEffect(export) { if (Done && forShare) onShare(uri) }`.
- `ActivityDetailUiState.kt:81`: `artExport` vive na VM e sobrevive a
  rotação; a aba é `rememberSaveable`.
- `ActivityDetailScreen.kt:281-283`: o sheet é montado sempre que
  `storyStudio.isOpen`.
- `ActivityDetailScreen.kt:848-856`: `shareMedia` abre o chooser.

**Gatilhos reais**

- Trocar para a aba Vídeo e voltar para Arte.
- Girar a tela (recriação da Activity; o estado da VM permanece).
- Fechar e reabrir o Studio e entrar na aba Arte.
- **Rolar não dispara** — o efeito não observa scroll. A v1 errou esse
  gatilho.

**Correção (escolher uma)**

- **A (recomendada, menor):** a UI chama `onShare(uri)` e, em seguida,
  `onShareConsumed()`; a VM volta `artExport` para `Idle` somente se ainda
  for o mesmo `Done`. Sem loop: o efeito só roda quando a chave muda ou o
  composable reentra.
- **B (mais limpa):** trocar por `Channel<ArtShareEvent>`/`SharedFlow` na
  VM; a UI coleta e abre o sheet. Cuidado com `LargeClass` do detekt; pode
  exigir extrair um helper.
- Não usar `rememberSaveable`/flag local para “lembrar” que já compartilhou:
  o evento pertence à VM.

**Teste de regressão (TDD)**

- Em `ArtStudioContentTest`, estado mutável `Idle → Done(forShare=true)`;
  `onShare` conta chamadas; `onShareConsumed` volta para `Idle`.
- Compõe, espera a primeira chamada (1); troca para Vídeo e volta para
  Arte; espera; a contagem continua 1.
- O teste atual `a done share export hands the uri to the share sheet`
  começa em `Done` e espera 1; ele precisa ser adaptado ao novo contrato
  (a v1 não sinalizou esse acoplamento).
- Se a opção B for escolhida, o teste de UI deve emitir o evento pelo
  fake/estado, e o teste da VM deve verificar que a VM emite uma única vez
  por export.

**Aceite**

- O primeiro Compartilhar abre o sheet uma vez.
- Voltar à aba, girar a tela ou reabrir o Studio não reabre o sheet.
- O teste falha no código atual e passa após a correção.

**Arquivos:** `ArtStudioContent.kt`, `ActivityDetailViewModel.kt`,
`ActivityDetailUiState.kt`, `ActivityDetailScreen.kt`,
`ArtStudioContentTest.kt`, `ActivityDetailViewModelTest.kt`.

---

### P0-3 — Export com receita obsoleta (novo, não visto pela v1)

**Status:** [ ] pendente
**Tipo:** bug de correção/race
**Severidade:** P0

**Evidência**

- `ActivityDetailViewModel.kt:284-327`: `exportArt` captura
  `studio.artRecipe` e passa ao exporter; o resultado é aceito se
  `artExport` ainda for `Exporting` (`:307`).
- Os controles de formato/extras/cores/fonte **não são desabilitados**
  durante `Exporting`; só os CTAs são (`ArtStudioContent.kt:244-262`).
- O export em `Dispatchers.Default` leva tempo; o usuário pode mudar a cor
  ou a fonte antes de o resultado voltar.

**Efeito**

O chooser abre (ou o “PNG salvo” aparece) com um PNG da receita antiga,
enquanto a tela mostra a nova. É mais grave que C3: o artefato entregue não
corresponde ao que o usuário vê.

**Correção (escolher uma)**

- **A (recomendada):** capturar a `ArtExportKey`/receita no início e
  compará-la com a receita atual no retorno; se mudou, descartar o
  resultado e voltar a `Idle` (ou reiniciar automaticamente, se o produto
  preferir). O usuário deve saber que a arte mudou: uma linha “Receita
  alterada — gere de novo” é suficiente.
- **B:** desabilitar todos os controles da arte enquanto `Exporting`
  (curto, mas trava a edição; testar 200%).
- **C:** resetar `artExport` em `updateArtRecipe` (também resolve C3), mas
  o guard `:307` então descarta o resultado em voo silenciosamente; se for
  essa opção, diga ao usuário ou reinicie.
- Não aceitar o resultado só porque ainda está `Exporting`.

**Teste de regressão (TDD)**

- VM: fake com `CompletableDeferred<ArtPngExport>`; inicia `shareArt()`,
  muda `selectArtTextColor(CYAN)` antes de completar, completa com
  `Published(...)`; asserta que nenhum `Done`/share com a URI antiga foi
  entregue (estado `Idle`, ou `Exporting` de novo se a opção for
  auto-reiniciar).
- Caminho feliz: sem mudança, `Done` normal com a URI.
- UI: enquanto `Exporting`, ou os controles estão desabilitados (opção B),
  ou o resultado antigo não abre o sheet (opção A/C).

**Aceite**

- O PNG compartilhado/salvo sempre corresponde à receita visível.
- Nenhum `Done` obsoleto é aceito.
- Teste falha no código atual.

**Arquivos:** `ActivityDetailViewModel.kt`, `ActivityDetailUiState.kt`,
`ArtStudioContent.kt`, `ActivityDetailViewModelTest.kt`,
`ArtStudioContentTest.kt`.

---

## 4. Tarefas P1 — spec e produto

### P1-4 — `Done`/`Error` obsoletos ao editar (C3)

**Status:** [ ] pendente
**Tipo:** higiene de estado
**Severidade:** P1

**Evidência**

- `ActivityDetailViewModel.kt:329-331`: `updateArtRecipe` não toca em
  `artExport`; “Galeria indisponível” e “PNG salvo em Pictures/MeuCoach”
  continuam após trocar cor/fonte/formato.

**Correção**

- Em `updateArtRecipe`, calcular `next`; se `next == current`, não emitir
  (evita reset por toque no mesmo valor).
- Se `artExport` for `Done` ou `Error`, voltar a `Idle` ao aplicar uma
  mudança real.
- Se `artExport` for `Exporting`, aplicar a decisão do P0-3 (não resetar
  cegamente sem tratar o in-flight).

**Teste**

- VM: sucesso de save → `selectArtFont(ANTON)` → `Idle`.
- VM: erro → `selectArtTextColor` → `Idle`.
- VM: selecionar o mesmo valor não limpa o `Done`.

**Aceite:** confirmações/erros somem na primeira edição real; toque no
mesmo valor não é “edição”.

**Arquivos:** `ActivityDetailViewModel.kt`, `ActivityDetailViewModelTest.kt`.

---

### P1-5 — Copy “pôster automático × arte transparente” (A1)

**Status:** [ ] pendente
**Tipo:** desvio de PRD
**Severidade:** P1

**Evidência**

- PRD §8 (linha 156): “Pôster automático (opaco)” vs “Arte transparente
  (personalizada)”; uma linha explica que o pôster nasce com o vídeo e a
  arte é para sobrepor.
- ADR 0058 §2.9 (linha 111): “uma linha no Studio/card explica a diferença”.
- A UI atual tem “Arte transparente” (`ArtStudioContent.kt:365`) e “Fundo
  transparente” (`:462`), mas não a comparação.
- Existe uma linha parecida no resultado do vídeo
  (`ActivityDetailScreen.kt:730`: “A capa 9:16 é o frame final… O pôster
  4:5 é a capa do feed”), mas ela não fala da arte. A v1 disse “não existe
  na UI”; é impreciso, mas o requisito **na aba Arte** continua faltando.

**Correção**

- Adicionar uma linha/cartão compacto no topo da aba Arte (abaixo do
  header, antes da prévia), por exemplo:
  > “Pôster automático: opaco, nasce com o vídeo. Arte PNG: transparente,
  > para colar sobre uma foto.”
- Validar a copy com o usuário 0 antes de fechar.
- **Não** adicionar os “3 passos” (salvar → Instagram → figurinha) como se
  fossem requisito: o PRD/ADR não os pedem. Se o usuário quiser, entram
  como polimento separado.

**Teste**

- `ArtStudioContentTest` asserta a linha na aba Arte.
- Golden da aba atualizado (mudança de layout).
- Nenhum dado real.

**Aceite:** o usuário entende por que existem dois PNGs 4:5 e qual é
transparente.

**Arquivos:** `ArtStudioContent.kt`, `StoryStudioSheet.kt` (se o header for
o lugar), `ArtStudioContentTest.kt`, `StoryStudioScreenshotTest.kt`.

---

### P1-6 — Spinner no CTA de export (A4 — requisito do PRD)

**Status:** [ ] pendente
**Tipo:** desvio de PRD
**Severidade:** P1

**Evidência**

- PRD §8 (linha 161): “Loading: spinner no botão durante render/salvar”.
- `ArtStudioContent.kt:244-262`: o CTA só troca o texto
  (“Compartilhando…”/“Salvando…”); não há indicador visual.
- `McPrimaryButton`/`McSecondaryButton` (`McButtons.kt:28-104`) só aceitam
  `leadingIcon`, não um slot/spinner.

**Correção**

1. Adicionar `loading: Boolean = false` nos dois botões do design system.
2. Quando `loading`, renderizar um `CircularProgressIndicator` de 18 dp
   (`strokeWidth = 2.dp`) no lugar do `leadingIcon`, mantendo o texto.
3. Default `false`; nenhum uso existente muda.
4. Em `ArtStudioContent`, passar `loading = exporting`; manter os labels e
   `testTag("art-share-progress")`/`art-save-progress` para o teste.
5. O botão segue desabilitado durante o export; o spinner usa o
   `disabledContentColor` (`textTertiary`) — contraste já validado
   (~5,5:1 no escuro, ~4,9:1 no claro).

**Teste**

- `ArtStudioContentTest`: com `Exporting(forShare=true)`, o nó do spinner
  existe e o CTA está desabilitado; com `Idle`, não existe.
- Teste do design system para `loading=true` (se houver suíte de
  componentes).
- Golden atualizado.

**Aceite:** o usuário vê progresso no próprio botão; sem salto de layout;
TalkBack continua lendo “Compartilhando…”/“Salvando…”.

**Arquivos:** `McButtons.kt`, `ArtStudioContent.kt`,
`ArtStudioContentTest.kt`, `StoryStudioScreenshotTest.kt`.

---

### P1-7 — Aviso de contraste só olha um fundo (M4)

**Status:** [ ] pendente
**Tipo:** lógica de acessibilidade / desvio de spec
**Severidade:** P1

**Evidência**

- `ArtContrast.kt:29-39`: `warning(text, route, darkPreview)` usa só o
  checker visível.
- ADR 0058 §2.4 (linha 105): o aviso deve considerar “claro ou escuro”.
- A rota não é desenhada sobre o checker: `StoryArtRenderer.kt:255` desenha
  a casca `#08090B` a 70% e a rota por cima. Uma rota preta contra a casca
  dá **~1,01:1**; no checker claro ela passa (~15,7:1), então o aviso
  **não dispara** no caso invisível.

**Correção**

1. Separar as checagens:
   - **Texto:** falhar se `< 4,5:1` contra `DARK_CHECKER_WORST` **ou**
     `LIGHT_CHECKER_WORST`; a mensagem pode dizer “no fundo claro/escuro —
     confira sobre a foto”.
   - **Rota:** falhar se `< 3:1` contra a casca composta nos dois extremos
     de foto: `casing` sobre preto e `casing` sobre branco. A casca é
     `#08090B` com alpha 178; compor com `#000000`/`#FFFFFF`.
2. Manter a natureza consultiva (a arte compõe com a foto; não há garantia).
3. Mostrar o aviso de texto e/ou rota; se as duas falharem, priorizar o
   texto ou listar as duas. A v1 sugeriu mover para o card CORES; manter
   sob a prévia também é aceitável — decidir com o usuário 0. O importante
   é a lógica, não a posição.

**Teste**

- `ArtContrastTest`:
  - texto branco avisa mesmo com `darkPreview=true` (falha no claro);
  - rota preta avisa mesmo com `darkPreview=false` (falha na casca);
  - rota branca não avisa falsamente contra a casca;
  - combinações que passam nos dois fundos não avisam;
  - `ratio` continua público e estável.
- Golden da aba com aviso, se a posição mudar.

**Aceite:** o aviso não depende do toggle do checker para os casos reais de
texto/rota; nenhum falso-negativo para rota escura.

**Arquivos:** `ArtContrast.kt`, `ArtStudioContent.kt`,
`ArtContrastTest.kt`, `ArtStudioContentTest.kt`.

---

## 5. Tarefas P2 — UX, acessibilidade e resiliência

### P2-8 — Swatches: check, borda clara, label e role (M3)

**Status:** [ ] pendente
**Tipo:** acessibilidade / contraste
**Severidade:** P2

**Evidência**

- `ArtStudioContent.kt:499-549`: `isDarkSwatch` (`:588-591`) usa check
  branco em `BLACK`, `GRAY_DARK`, `CYAN` e `BLUE`; coral recebe check preto.
- Números: branco × ciano **1,82:1**; branco × azul **2,68:1** (falham
  3:1). Preto × coral **7,52:1** (passa) — o exemplo da v1 está invertido.
- Branco `#F4F6F7` sobre surface claro `#FFFFFF` = **1,08:1**; borda
  `borderSubtle` = **1,25:1**.
- O PRD §8 pede `selected` + borda + check; o código tem, mas não tem
  `Role.RadioButton`/`selectableGroup`.
- `DESIGN_SYSTEM.md` §8.1: “Nunca cor sozinha. Rótulo, forma ou padrão.”

**Correção**

1. Substituir `isDarkSwatch` por uma escolha de contraste real:
   `checkTint(color) = if (ratio(WHITE, color) >= ratio(BLACK, color)) White else Black`.
   Para os 8 valores, o escolhido tem ≥ 3:1; o menor contraste escolhido é
   gray_dark com check branco (~6:1), e a regra evita o check preto de
   gray_dark (~3,5:1) e o branco de ciano/azul (1,8/2,7:1).
2. Borda dos swatches claros no tema claro: usar 1–2 dp de um token com
   ≥ 3:1 contra `surface` (ex.: `textSecondary` no claro); a espessura
   sozinha não resolve o contraste. **Não** mudar o `argb` da arte.
   Validar no golden light.
3. Adicionar um label visível abaixo de cada grupo:
   “Selecionada: lima” (`testTag("art-text-color-selected")` /
   `art-route-color-selected`). Mantém o `contentDescription` por swatch.
4. Trocar `clickable` por `Modifier.selectable(selected, role = Role.RadioButton)`
   e envolver o `FlowRow` em `Modifier.selectableGroup()`. Remover o
   `selected` manual duplicado, se o `selectable` já o fornece.
5. Não transformar os swatches em `LazyRow`; o `FlowRow` já resolve 320 dp.

**Teste**

- `ArtStudioContentTest`: o check de ciano/azul é preto (ou a função
  `checkTint` retorna o esperado); o label “Selecionada: lima” aparece;
  o nó tem `Role.RadioButton` e o grupo tem `SelectableGroup`.
- Golden light da aba para o swatch branco.
- Manter o teste de 48 dp.

**Aceite:** nenhum check abaixo de 3:1; swatch branco visível no claro;
seleção não depende só de cor; TalkBack anuncia rádio + selecionado.

**Arquivos:** `ArtStudioContent.kt`, `ArtStudioContentTest.kt`,
`StoryStudioScreenshotTest.kt`.

---

### P2-9 — 360 dp e fonte 200% (T3)

**Status:** [ ] pendente
**Tipo:** acessibilidade / DoD do PRD
**Severidade:** P2

**Evidência**

- PRD §12 (linha 239): “UI: 360 dp, 200%, TalkBack…”. O `PROGRESS` da F8
  (linhas 22–25) cita TalkBack/48 dp/estados, mas não 360 dp/200%.
- O golden da aba usa `w393dp` (`StoryStudioScreenshotTest`), não 360.
- O caso concreto mais arriscado é o `Row` dos pills “Fundo escuro / Fundo
  claro” (`ArtStudioContent.kt:470-483`), que não é `FlowRow`; em 200% o
  texto pode espremer/empilhar. O “texto de empty cortado” citado pela v1
  é especulativo (o texto cabe no box de 240×300).

**Correção**

1. Adicionar um teste de composição em `w360dp-h800dp-xxhdpi` com
   `LocalDensity` + `fontScale = 2f` (padrão já usado em
   `TodayBriefingTest.kt:305-311` e `CoachBubbleCardsTest.kt:157-163`).
2. Trocar o `Row` dos pills do checker por `FlowRow` (ou dar `weight(1f)`
   e permitir wrap) para 200%.
3. Verificar o header (título “Arte transparente” + badge) em 200%; se
   espremer, empilhar o badge abaixo do título.
4. Golden novo `art-studio-tab-narrow-200-dark.png`; rodar
   `verifyRoborazziDebug --tests "*Art*"` **e** `"*StoryStudio*"`.
5. Atualizar `PROGRESS.md` com a evidência.

**Aceite:** sem clipping/overflow horizontal em 360 dp/200%; prévia,
controles e CTAs utilizáveis; TalkBack ok.

**Arquivos:** `ArtStudioContent.kt`, `StoryStudioScreenshotTest.kt`,
`ArtStudioContentTest.kt`, `PROGRESS.md`.

---

### P2-10 — Limite de extras invisível (M2)

**Status:** [ ] pendente
**Tipo:** UX / clareza
**Severidade:** P2

**Evidência**

- `ArtStudioContent.kt:136`: `blocked = !selected && extras.size >= MAX_EXTRAS`.
- `StoryStudioComponents.kt:195-203`: para um tile bloqueado por limite,
  `hint` cai no `extraHint` normal (“Média do treino”); o tile fica
  desabilitado sem dizer por quê.
- Para um tile sem dado, o `status` inclui o motivo (“FC média · sem FC”) e
  o `hint` é o motivo — então a comparação da v1 (“mesmo hint de quando
  falta dado”) está **errada**. O problema real é o tile bloqueado não
  explicar o limite.

**Correção**

- Passar `blockedByLimit` ao `ExtraTile` (ou um `hintOverride`).
- Quando bloqueado e não selecionado:
  - hint: “Limite de 2 — toque para trocar”;
  - `contentDescription`: “Cadência, limite de 2 atingido”.
- Manter o caminho “sem dado” como está (motivo no status/hint).
- O `ExtrasCounter` 2/2 continua; não mudar o renderer value-only.

**Teste**

- Com 2 extras selecionados, o terceiro tile mostra o hint de limite e o
  TalkBack distinto; o tile sem dado continua “sem FC”.
- Nenhuma mudança no golden do PNG.

**Aceite:** o usuário distingue “sem dado” de “limite de 2” sem adivinhar.

**Arquivos:** `ArtStudioContent.kt`, `StoryStudioComponents.kt`,
`ArtStudioContentTest.kt`.

---

### P2-11 — Prévia pequena/longe, badge e scroll (A2)

**Status:** [ ] pendente
**Tipo:** UX de fluxo
**Severidade:** P2

**Evidência**

- `ArtStudioContent.kt:395-400`: prévia com `height(300.dp)` e largura
  proporcional (240×300 no 4:5).
- `ArtStudioContent.kt:376`: badge “4:5 · 1080 px” (não diz 1080×1350).
- `StoryStudioSheet.kt:190-194`: um único `rememberScrollState` para as
  duas abas; trocar de aba mantém o offset — quem estava no fim do vídeo
  cai no fim da arte e perde a prévia.
- O sheet tem ~1.800–2.000 dp de conteúdo; a prévia fica no topo e os
  controles de cor/fonte ficam embaixo.

**Correção**

1. Subir o `rememberScrollState` para `StoryStudioContent` e adicionar
   `LaunchedEffect(tab) { scrollState.scrollTo(0) }` (ou animar).
2. Badge: `"${format.width}×${format.height}"` →
   “1080×1350” / “1080×1080” (`testTag("art-format-badge")`).
3. Adicionar uma âncora “Ver prévia” após os CTAs (ou um botão flutuante)
   que rola ao topo; é o menor passo verificável.
4. Opcional/decisão: prévia em largura cheia (`fillMaxWidth` +
   `aspectRatio`) com limite de altura; ganha tamanho, mas aumenta o
   scroll. Sticky de verdade é uma fatia estrutural separada.
5. Não remover o toggle do checker nem o label “Fundo transparente”.

**Teste**

- Trocar de aba a partir do fim do vídeo e assertar que a prévia está
  visível / o scroll voltou a 0.
- Badge com as duas dimensões.
- Âncora rola ao topo (teste de clique + `assertIsDisplayed`).
- Golden atualizado (badge/âncora).

**Aceite:** editar cor/fonte e voltar à prévia custa no máximo um toque;
o badge informa as duas dimensões; a troca de aba não herda o scroll.

**Arquivos:** `ArtStudioContent.kt`, `StoryStudioSheet.kt`,
`ArtStudioContentTest.kt`, `StoryStudioScreenshotTest.kt`.

---

### P2-12 — Erro amigável e cache com bound (A4)

**Status:** [ ] pendente
**Tipo:** robustez / privacidade
**Severidade:** P2

**Evidência**

- `ArtPngExporter.kt:81`: `Failure(error.message ?: "Não foi possível…")`
  vaza mensagem técnica (ex.: “Permission denied”) para a UI.
- `ArtPngExporter.kt:91`: `cacheDir/art` cresce sem pruning; o vídeo apaga
  seus temporários (`StoryVideoExporter.kt:228,293,297`), a arte não.
- `ArtExportUiState.Done.reused` é gravado (`ActivityDetailViewModel.kt:316`)
  e nunca lido pela UI; `savedToGallery` também é ignorado.

**Correção**

1. Adicionar `StoryMessages.ART_EXPORT_FAILED` com copy pt-BR amigável;
   o VM mapeia `Failure` para essa constante. O detalhe técnico vai para o
   log (seguir o padrão de log do app), nunca para a tela.
2. Pruning do cache: manter os últimos N arquivos (sugestão: 8) **e** o
   `lastFile` do reuso; apagar os mais antigos após um publish bem-sucedido.
   Não apagar o arquivo que `lastUri` ainda referencia.
3. Decidir com o usuário 0 se `reused` deve ser comunicado. Default:
   manter silencioso (o PRD só exige o reuso; o sheet já é o feedback).
   Se comunicar, usar uma legenda curta e testá-la.
4. Não adicionar Worker/notificação: o export continua síncrono.

**Teste**

- `ArtPngExportTest`: falha do publisher → `Failure` com mensagem amigável;
  a UI (`ArtStudioContentTest`) mostra a copy amigável, não “Permission
  denied”.
- Pruning: criar 10 arquivos, publicar, assertar que os antigos foram
  removidos e o último/reuso permanece.
- Reuso continua funcionando após o pruning.

**Aceite:** nenhuma mensagem técnica na UI; cache limitado; reuso intacto;
galeria sem duplicatas.

**Arquivos:** `ArtPngExporter.kt`, `StoryMessages.kt`,
`ActivityDetailViewModel.kt`, `ArtPngExportTest.kt`,
`ArtStudioContentTest.kt`.

---

## 6. Tarefas P3 — polish

### P3-13 — Acessibilidade e estados menores

**Status:** [ ] pendente

1. **T1 — extras no `contentDescription` da prévia.** Hoje
   `ArtStudioContent.kt:321-327` resume formato/cores/fonte, mas não
   extras. Adicionar “extras data e FC média” quando houver. O PRD só pede
   formato/cores/fonte, então é enhancement; testar a string.
2. **T2 — spinner no primeiro load.** `ArtStudioContent.kt:456` é
   `Loading -> Unit`; mostrar um `CircularProgressIndicator` pequeno (ou
   “Gerando prévia…”) **somente** quando não há bitmap anterior. A prévia
   anterior já permanece em re-render (o `produceState` não reseta); a v1
   sugeriu manter, mas isso já existe.
3. **T4 — heading do grupo de cor.** `SwatchRow` (`:499-506`) usa
   `semantics(mergeDescendants = true) {}` sem `heading()`. Trocar por
   `SectionLabel` (que adiciona `heading()`) ou adicionar `heading()`;
   verificar se não cria headings demais no TalkBack.
4. **T6 — dois “Tentar novamente”.** Renomear o da prévia para
   “Recarregar prévia” e manter o do export como “Tentar de novo” (ou
   “Tentar salvar de novo” se o erro carregar o alvo). Testar os dois
   rótulos.

**Testes:** `ArtStudioContentTest` (contentDescription, spinner, heading,
labels). Goldens se o layout mudar.

---

### P3-14 — Copy, undo e decisões cosméticas

**Status:** [ ] pendente

1. **M5 — “Salvar como padrão”.** A copy atual “A próxima arte já abre com
   estas escolhas.” (`ArtStudioContent.kt:235`) não diz *quando* salva.
   Trocar por “Salva ao compartilhar ou salvar.” (validar com o usuário 0).
   O switch ligado por default é spec (PRD decisão 4), não bug.
   “Restaurar padrão” limpa o store sem undo; um snackbar com “Desfazer”
   exige um `SnackbarHost` na tela (não existe no Activity Detail hoje) —
   deixar como decisão aberta, não implementar por impulso.
2. **M6 — vazio duplicado.** A prévia mostra “Sem rota suficiente para a
   arte” e o caption abaixo dos CTAs repete (`ArtStudioContent.kt:297-304`).
   Remover o caption duplicado ou trocar por uma explicação útil (“Esta
   atividade não tem rota GPS; a arte precisa de pelo menos 2 pontos”).
   **Manter os controles visíveis** — o PRD §8 (linha 162) exige.
3. **A3 — cantos.** A prévia usa `clip(RoundedCornerShape(20.dp))`
   (`:400`) e o PNG tem cantos transparentes/retos. O vídeo usa o mesmo
   frame de 20 dp; é convenção de prévia, não desvio de design system.
   Default: manter. Se o usuário 0 preferir literal, remover o clip e
   atualizar o golden.
4. **A4 — feedback de reuso.** Ver P2-12; default silencioso.
5. **T3 — 200%.** Coberto em P2-9.

**Testes:** copy (texto exato), ausência de duplicata, golden se o clip/
copy mudar.

---

## 7. Itens refutados / não fazer

### 7.1 M1 — formato como pills × segmento do vídeo

**Refutado.** O vídeo **não tem** seletor 4:5/1:1; o formato do vídeo é
fixo 9:16. O “segmento” do Studio é o de abas (`Vídeo | Arte PNG`) e o de
quadros da prévia, não um seletor de formato. O PRD §8 (linha 148) pede
“2 chips (4:5 · 1:1)” — os `StudioPill` atuais estão alinhados. Trocar por
segmento seria preferência, não correção. Não fazer.

### 7.2 T7 — preview 300 dp × 5 swatches

**Refutado.** A largura da prévia não tem relação com o `FlowRow` dos
swatches; o `FlowRow` quebra linha sozinho. Em 320 dp, o
`Modifier.width(300.dp)` é limitado pelas constraints do pai (o
`enforceIncoming` do Compose), então o quadrado 1:1 vira ~280 dp e não
estoura. “5 swatches em 280 dp” é o comportamento saudável do `FlowRow`,
não um risco. Não fazer.

### 7.3 T5 — warning AA

**Fechado.** `#FFB454` sobre `#191C21` ≈ 9,7:1; `#8A5200` sobre `#FFFFFF`
≈ 6,4:1. O `DESIGN_SYSTEM.md` §3.4 (linha 110) já documenta o ajuste do
warning claro. Nenhuma ação.

### 7.4 “3 passos” e “rolar reabre o sheet”

- Os 3 passos não são requisito do PRD/ADR; são sugestão. Não tratar como
  copy obrigatória.
- Rolar não dispara o `LaunchedEffect`; o C2 tem outros gatilhos. Não
  gastar tempo “reproduzindo por scroll”.

---

## 8. Achados adicionais (origem dos itens novos)

1. **Race do export (P0-3):** a receita é capturada antes do render e os
   controles continuam ativos; o resultado antigo é aceito. É o achado mais
   grave que a v1 não viu.
2. **Goldens mascaram o C1:** o `art-studio-tab-dark` nasce com
   `ArtFont.ANTON`; o `remember(context)` calcula Anton na primeira
   composição, então o golden fica correto. É preciso um teste que **mude**
   a fonte depois da composição inicial.
3. **Acoplamento do teste de share:** `ArtStudioContentTest` começa em
   `Done` e espera 1 chamada; não testa reentrada. A correção do C2 exige
   adaptar esse teste.
4. **200% nos pills do checker:** o `Row` de “Fundo escuro / Fundo claro”
   não é `FlowRow`; é o caso concreto de 200% que a v1 não apontou.
5. **Rota × casca:** o aviso de contraste da rota não pode usar o checker
   como fundo real; a rota preta contra a casca é ~1,01:1 e passa no
   checker claro (~15,7:1).
6. **Recipe salva antes do export:** `ActivityDetailViewModel.kt:293`
   persiste a receita antes de saber se o export falhou. O comentário diz
   “successful intent”; decidir se é intencional. Não é bug por si só, mas
   documentar a decisão.
7. **`ArtExportKey(activityId, recipe, recipe.format)`** repete o formato
   que já está em `ArtRecipe`; limpeza opcional, sem impacto de UX.
8. **Referências da v1:** `ArtPngScreenshotTest` está em
   `app/src/test/.../story/`, não em `ui/activitydetail/`;
   `StoryStudioSheet.kt:189` é a aba e o scroll é `:190-194`;
   `StoryArtRenderer.kt:254` é `:255`. Corrigir ao citar em PRs.

---

## 9. Testes e gates

### 9.1 TDD

Para cada tarefa P0/P1/P2:

1. Escrever o teste que falha (ou o golden que muda).
2. Implementar o mínimo.
3. Rodar o teste isolado.
4. Rodar os gates completos.
5. Atualizar `PROGRESS.md` e `ROADMAP.md`.

### 9.2 Comandos

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew check
./gradlew spotlessCheck
./gradlew koverVerify
./gradlew :app:verifyRoborazziDebug --tests "*Art*"
./gradlew :app:verifyRoborazziDebug --tests "*StoryStudio*"
```

Nota do `PROGRESS` da F8: rodar o gate visual em invocação separada; o
`--tests` combinado com kover re-executa a suíte filtrada e derruba a
cobertura do artefato.

### 9.3 Aparelho (usuário 0)

- Trocar Inter→Anton→Archivo e conferir a prévia principal (C1).
- Compartilhar, girar a tela, trocar de aba, fechar/reabrir: o sheet não
  reabre (C2).
- Compartilhar e trocar a cor durante “Compartilhando…”: o PNG entregue
  corresponde à tela (P0-3).
- Errar o export, mudar a cor: o erro some (C3).
- 360 dp + fonte 200% + TalkBack + tema claro/escuro.
- Swatch branco no tema claro; check de ciano/azul; label “Selecionada”.
- Compartilhar/Salvar, reuso, “Ver na galeria” se existir, cache.
- Vídeo/capa/pôster sem regressão.

### 9.4 Goldens

- Regravar somente depois da spec visual aprovada; não regravar todos às
  cegas para “ficar verde”.
- Goldens esperados de mudar: `art-studio-tab-dark.png` (copy/badge/
  spinner/swatches), possível `art-studio-tab-light.png` (novo, se
  criarmos), `art-studio-tab-narrow-200-dark.png` (novo).
- Os goldens dos PNGs (`art-png-*`) só mudam se o renderer mudar — não
  deve acontecer neste plano, exceto se a spec visual for alterada.

---

## 10. Definição de pronto (remediação)

- [ ] P0-1, P0-2, P0-3 implementados com teste que falha antes.
- [ ] P1-4, P1-5, P1-6, P1-7 implementados e validados.
- [ ] P2-8, P2-9, P2-10, P2-11, P2-12 implementados ou explicitamente
      adiados com o usuário 0.
- [ ] `assembleDebug`, `check`, `spotlessCheck`, `koverVerify` verdes.
- [ ] `verifyRoborazziDebug --tests "*Art*"` e `"*StoryStudio*"` verdes.
- [ ] Lint/detekt sem erros/supressões novas.
- [ ] Validação no aparelho (S24 Ultra) e emulador API 31+.
- [ ] Vídeo/capa/pôster sem regressão.
- [ ] `PROGRESS.md` e `ROADMAP.md` atualizados; `DESIGN_SYSTEM.md` se a
      copy/componente mudar; ADR se alguma regra mudar (não é esperado).
- [ ] Nenhum dado real no diff; NOTICE OFL intacto.
- [ ] Nenhum TODO crítico escondido.

---

## 11. Decisões abertas (usuário 0)

| # | Decisão | Opções | Default recomendado |
|---|---|---|---|
| 1 | Copy A1 exata e posição | linha no topo / cartão / abaixo do header | linha curta no topo da aba Arte |
| 2 | Prévia A2 | manter 300 dp + âncora / largura cheia / sticky | manter + âncora (menor fatia) |
| 3 | Race do export | token/descartar / desabilitar controles / auto-reiniciar | token/descartar + aviso |
| 4 | `reused` na UI | silencioso / legenda | silencioso |
| 5 | Undo do “Restaurar padrão” | snackbar / nenhum | nenhum por agora |
| 6 | Cantos da prévia (A3) | manter 20 dp / literal | manter (consistente com o vídeo) |
| 7 | Heading dos grupos de cor (T4) | `SectionLabel` / manter | `SectionLabel` se o TalkBack não ficar verboso |
| 8 | Borda do swatch claro | `textSecondary` / outro token | validar visualmente com o usuário 0 |

---

## 12. Riscos e regressões

- **Componentes compartilhados:** `StoryStudioComponents.kt` e
  `StoryStudioSheet.kt` são usados pela aba Vídeo. Rodar
  `StoryStudioContentTest` e os goldens de vídeo em toda mudança.
- **`McButtons`:** o `loading` é transversal; manter default `false` e
  rodar a suíte de design system/goldens que usam os botões.
- **C2:** a mudança de evento pode quebrar o teste atual de share; adaptar
  o teste, não afrouxar o assert.
- **C1:** o teste precisa de um sinal determinístico (tag por fonte ou
  estado `Ready` com a fonte renderizada); `waitForIdle` sozinho pode
  capturar o bitmap antigo por causa do debounce de 120 ms.
- **P0-3:** se optar por desabilitar controles, verificar 200% e o
  TalkBack; se optar por descartar, garantir que o usuário saiba que a
  arte mudou.
- **Pruning:** não apagar o `lastFile` do reuso; testar reuso após o
  pruning.
- **Goldens:** regravar só o necessário; um golden novo de 200% pode
  revelar outros problemas de layout, não só os pills.
- **Escopo:** nenhuma mudança aqui deve tocar no renderer, no domínio, no
  worker ou no MediaStore além do necessário. A arte continua 4:5/1:1,
  transparente, com 6 fontes e 8 swatches.

---

## 13. Registro do confronto (v1 → v2)

**Confirmados com correção**

- **C1:** bug real; a explicação “até recomposição” está errada (só sai ao
  reentrar na composição); a correção precisa do `remember` com a fonte
  como chave.
- **C2:** bug real; “rolar” não é gatilho; os gatilhos são troca de aba,
  rotação e reabertura.
- **C3:** bug real; P1, não bloqueador.
- **A1:** requisito real; “não existe na UI” é impreciso (há uma linha
  capa/pôster no resultado do vídeo), mas falta na aba Arte; os 3 passos
  não são requisito.
- **A2:** requisito de UX real; “largura cheia” é trade-off, não consenso.
- **M4:** direção correta; a correção precisa considerar a casca da rota,
  não só os dois checkers.
- **M5:** copy/UX real; “ligado por default” é spec; undo é opcional.
- **T3:** gap real de DoD; o exemplo do texto de empty é fraco; o caso
  concreto é o `Row` dos pills do checker.
- **T1/T2/T4/T6:** válidos como P3; T2 só afeta o primeiro load.

**Parciais**

- **A4:** spinner é requisito do PRD; cache sem bound é real; “Salvar
  sempre novo” é decisão de produto, não bug; `reused` não é comunicado.
- **M2:** o tile bloqueado não explica o limite; a comparação com “sem
  dado” está errada (o tile sem dado mostra o motivo).
- **M3:** branco no tema claro é real; `isDarkSwatch` está invertido no
  exemplo (coral passa; ciano/azul falham); role de rádio é enhancement.
- **M6:** a duplicação existe; colapsar os controles contraria o PRD §8.

**Refutados/fechados**

- **M1:** não há segmento de formato no vídeo; o PRD pede chips.
- **T7:** a prévia não interfere no `FlowRow`; não há overflow em 320 dp.
- **T5:** tokens passam AA; já documentado.
- **“2 bugs”:** são 3 estados + o race do export (4 itens).
- **“3 passos”:** sugestão, não requisito.
- **“Rolar reabre o sheet”:** falso.

**Adicionados**

- Race do export (P0-3).
- Goldens mascaram o C1.
- Acoplamento do teste de share.
- 200% nos pills do checker.
- Rota × casca no aviso de contraste.
- Receita salva antes do resultado do export.
- `ArtExportKey` com formato redundante.
- Correções de referências de linha/path.

---

## 14. Referências de código (verificadas)

- `ArtStudioContent.kt:89` (aba), `:240-243` (share), `:244-262` (CTAs),
  `:297-304` (empty duplicado), `:309-496` (prévia), `:314` (C1),
  `:321-327` (contentDescription), `:328-334` (produceState),
  `:376` (badge), `:395-400` (tamanho/clip), `:456` (Loading),
  `:470-483` (pills do checker), `:499-549` (swatches),
  `:588-591` (`isDarkSwatch`), `:593-595` (`summaryLine`).
- `StoryStudioSheet.kt:189` (aba default), `:190-194` (scroll),
  `:236` (segmento), `:238-254` (troca de aba), `:476-521` (segmento),
  `:524-632` (prévia do vídeo).
- `StoryStudioComponents.kt:55-89` (header), `:110-162` (pill),
  `:176-261` (tile), `:195-203` (hint), `:263-317` (save default).
- `ActivityDetailViewModel.kt:149-176` (abrir/fechar),
  `:234-267` (controles da arte), `:269-327` (export), `:284-327`
  (exportArt), `:293` (save antes), `:307` (guard), `:329-331` (C3).
- `ActivityDetailUiState.kt:32-47` (`ArtExportUiState`), `:69-82`
  (`StoryStudioUiState`), `:79` (`artSaveAsDefault` default true),
  `:81` (`artExport`).
- `ArtPngExporter.kt:15-28` (resultado), `:63-83` (export),
  `:69-71` (reuso), `:81` (mensagem crua), `:85-99` (render),
  `:91` (cacheDir/art).
- `StoryArtRenderer.kt:198-216` (extras value-only), `:224-259` (rota),
  `:255` (casca), `:270-278` (footer), `:296-301` (constantes).
- `ArtContrast.kt:11-18` (checkers), `:20-26` (ratio), `:29-39` (warning).
- `ArtRecipe.kt:30-39` (cores), `:48-58` (receita), `:80-95` (extras),
  `:98-102` (`ArtExportKey`).
- `ArtFonts.kt:42-49` (`typeface`).
- `GalleryImagePublisher.kt:29-54` (publish).
- `ActivityDetailScreen.kt:101-103` (share vídeo/capa/pôster),
  `:126-127` (share arte), `:281-283` (sheet), `:730` (copy capa/pôster),
  `:848-856` (`shareMedia`).
- `McButtons.kt:28-104` (botões primário/secundário).
- `McColors.kt:40-72` (dark), `:74-108` (light).
- `StoryStudioScreenshotTest.kt:75-105` (golden da aba Arte).
- `ArtPngScreenshotTest.kt` (em `app/src/test/kotlin/com/meucoach/app/story/`).
- `ArtStudioContentTest.kt:274-290` (share Done), `:292-305` (erro/retry).
- `ActivityDetailViewModelTest.kt:473-479` (defaults),
  `:484-499` (seleções), `:566-608` (persistência/reset),
  `:627-690` (export/share/erro).
- PRD: §8 (linhas 143-170), §12 (linhas 233-244).
- ADR 0058: §2.4 (linha 105), §2.9 (linhas 107-111), §5 (linhas 150-173).
- `DESIGN_SYSTEM.md`: §3.4 (linha 110), §8.1 (linha 326), §10 (linhas
  378-382).
