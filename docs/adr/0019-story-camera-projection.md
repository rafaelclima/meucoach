# ADR 0019 — Câmera 3D real no snapshot do story (port da projeção do MapLibre)

**Status:** aceito e implementado
**Data:** 2026-11-04
**Responsável:** implementação com Rafael (usuário 0)

---

## 1. Contexto

O story usava **um snapshot top-down** do MapLibre + uma homografia (`StoryPerspective`) para simular inclinação. O resultado lia como "mapa esticado", sem perspectiva real de ruas/prédios — o ponto mais distante do visual "Strava-like".

Avaliamos inclinar o snapshot de verdade (`CameraPosition.tilt`). O obstáculo: o `MapSnapshotter` 12.3.1 **não expõe `Projection`/`getMapAsync`**, então a rota desenhada no `Canvas` não teria como acompanhar a perspectiva e sairia das ruas.

Alternativas consideradas:

- `MapView` offscreen: frágil em background (janela/superfície) e dependente da main thread.
- Assar a rota no snapshot via GeoJSON e revelar por keyframes: alinhamento garantido, mas perde a fluidez do traçado.
- **Portar a matriz de câmera do MapLibre para Kotlin puro** (escolhida).

---

## 2. Decisão

### 2.1 Projeção pura (`StoryMapProjection`)

Port fiel da cadeia usada pelo MapLibre (`_calcMatrices`): perspectiva (FOV vertical padrão `2·atan(1/3)` ≈ 36,87°) → flip Y → translação pela distância da câmera → `rotateX(pitch)` → `rotateZ(-bearing)` → translação para o alvo, com Mercator de tile 512 px. `project(lat, lon)` devolve pixels de tela.

- **Teste de paridade top-down:** a câmera de `StoryMapCameraCalculator.calculate` projetada pela nova matriz reproduz o mesmo "route square" do snapshot atual (tolerância de 2 px). Isso ancora a matemática no comportamento já validado.
- **Teste de enquadramento inclinado:** a câmera de `calculateForStory(pitch=38°)` mantém os quatro cantos do bounding box dentro do route square.
- **Bearing:** rotação de 90° desloca a cena de forma mensurável (sem depender de sinal visual).

### 2.2 Snapshot inclinado + rota projetada

- `MapLibreBackgroundRenderer` renderiza com `CameraPosition.tilt(38°)` e `bearing`.
- `StoryPlan` ganhou `geoPoints` (amostrados até 400 pontos), e o renderer desenha a rota **projetada pela mesma câmera**, não mais por coordenadas normalizadas.
- A câmera cinematográfica (`StoryMotion`) continua atuando em **2D** (zoom/pan/rotação); a homografia sintética é pulada (`StoryPerspective.cameraMatrix(..., nativeTilt = true)`), evitando dupla perspectiva.
- O pin da foto é desenhado **fora** da matriz da câmera (sempre em pé).
- O `calculateForStory` ajusta zoom iterativamente para o bounding box caber no route square; o alvo herda o enquadramento do top-down.

### 2.3 Limitações conscientes

- O tilt é **fixo no snapshot** (38°); a câmera do vídeo ainda é 2D. Um flyover 3D com mudança de pitch exigiria keyframes de snapshots ou pipeline GL.
- É perspectiva de **plano Mercator**: não há terreno/DEM 3D (hillshade é sombreamento).
- A paridade com o renderer nativo foi validada no S24 Ultra (rota sobre as ruas em Clássico e Satélite) e pelo teste top-down; pequenas diferenças de versão do MapLibre podem exigir recalibração.

---

## 3. Consequências

- Positivas: perspectiva real de ruas e prédios, rota alinhada, menos "print inclinado"; nenhuma dependência nova; tudo determinístico e testável.
- Custos: ~150 linhas de matemática de matrizes; recalcular a projeção por frame é barato (400 pontos).
- Próximos passos naturais: bearing acompanhando a direção da rota, keyframes de câmera (M2) e composição em GL para 30 fps com folga.
