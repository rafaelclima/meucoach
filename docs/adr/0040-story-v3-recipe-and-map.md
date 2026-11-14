# ADR 0040 — Story V3: receita, métricas, safe area e mapa autoral

**Status:** aceito — Sprint A avançou em 13/11/2026 (D0.1, VIS-01, ENC-01, VIS-02 parcial, DOM-01 e DOM-02..DOM-07; renderer consumindo `StoryRenderSpec`); Etapa 1 segue com Story Studio, prévia de 3 frames e receita completa no worker
**Data:** 2026-11-13
**Responsável:** usuário 0 (decisões de produto) e implementação
**Documentos de origem:** [`STORY_V3_PLANO_DE_ACAO.md`](../STORY_V3_PLANO_DE_ACAO.md) e
[`STORY_V3_ANALISE_E_DIRECIONAMENTO.md`](../STORY_V3_ANALISE_E_DIRECIONAMENTO.md)

---

## 1. Contexto

O story do MeuCoach já exporta um MP4 9:16 com mapa vetorial, rota animada,
cards por km e encerramento. A auditoria de 13/11/2026 encontrou quatro limites
que impedem o vídeo de ser publicado com a qualidade pretendida:

1. **Área segura errada.** O layout reserva 13% no topo e 13% na base, medidas
   de Stories. Em 2026 o Instagram Reels cobre cerca de 420 px na base e o
   TikTok chega a 484 px; o card termina em 84% e a atribuição em 86,3%, ambos
   atrás da UI do aplicativo de destino.
2. **Receita fixa.** O vídeo tem 30 s, três métricas no card e três no
   encerramento. O usuário não escolhe o que aparece, e “calorias por km” é o
   total dividido pela distância, repetido em todos os cards — um número que
   sugere uma precisão que o dado não tem.
3. **Encode sem declaração de cor.** O `MediaFormat` não define BT.709, SDR,
   faixa limitada, perfil High nem modo de bitrate. O Instagram reencoda tudo;
   um master sem cor declarada chega lavado.
4. **Mapa genérico e projeção aproximada.** O estilo `liberty` claro não é a
   marca, e a rota é desenhada por um port manual da projeção. O
   `MapSnapshot.pixelForLatLng` do MapLibre existe e elimina a deriva em
   snapshots inclinados.

O MVP é composto pelas **Etapas 1 + 2** do plano: controle/qualidade e mapa
autoral/narrativa. Áudio, máscara de privacidade, satélite, 4:5 em vídeo,
60 fps, recorde e fallback offline ficam para depois.

---

## 2. Decisão

### 2.1 Receita com três essenciais e até dois extras

O story passa a ser descrito por uma `StoryRecipe` determinística:

- **Essenciais, sempre visíveis e não desmarcáveis:** `DISTANCE` (distância),
  `AVG_PACE` (ritmo médio) e `TOTAL_TIME` (tempo total).
- **Extras, no máximo dois:** `CALORIES_TOTAL`, `ELEVATION_GAIN`, `AVG_HR`,
  `MAX_HR`, `CADENCE`, `FASTEST_KM` e `DATE`.
- **Calorias por km são removidas** de `StorySplit` e de qualquer card.
  Calorias totais existem apenas como extra de resumo.
- Métrica sem dado fica desabilitada com motivo (“sem FC”, “sem elevação”,
  “sem calorias”); ausência nunca vira `0`.
- Presets (`Essencial`, `Performance`, `Fisiológico`, `Completo`) selecionam
  apenas o subconjunto disponível e nunca ligam uma métrica sem dado.

O renderer não decide disponibilidade: recebe um `StoryRenderSpec` resolvido.
A mesma receita alimenta prévia, worker e capa.

### 2.2 Vídeo mudo

O MVP não tem trilha de áudio, AAC, sintetizador nem batida procedural. O
usuário adiciona música no Instagram/TikTok. Isso evita conflito de licença,
mixagem e normalização de loudness, e mantém o pipeline de encode simples.

### 2.3 Sem máscara de privacidade

A rota completa é intencional por decisão do usuário 0. Não há blur de início
ou fim, raio de ocultação nem opção “sem mapa” no MVP. A privacidade continua
garantida por: dados processados no aparelho, nenhum GPS nos metadados do MP4
e nenhuma chamada de `MediaMuxer.setLocation`.

### 2.4 Mapa: OpenFreeMap + estilo autoral agora; Protomaps depois

- **Fonte de tiles:** OpenFreeMap (`tiles.openfreemap.org`), dados OSM.
- **Estilos:** `MeuCoach Dark` como padrão (fundo grafite, água azul-petróleo,
  parques dessaturados, labels claros com halo escuro, POIs/transporte/vias
  menores ocultos) e `MeuCoach Light` opcional.
- **Protomaps/PMTiles:** fica atrás de uma interface (`StoryTileSource` /
  `StoryStyleProvider`) para troca futura sem tocar no renderer. Não é
  implementado no MVP.
- **Satélite e Google Maps:** fora do MVP. O Google é bloqueio legal — os
  Termos do Google Maps Platform proíbem exportar o conteúdo para uso fora do
  serviço; não é uma questão de plano pago. Satélite depende de licença
  comercial (Esri/MapTiler) e fica para depois.
- **Projeção:** usar `MapSnapshot.pixelForLatLng`/`latLngForPixel` do MapLibre
  13.7.1 OpenGL para desenhar a rota com a matriz exata do mapa. O port
  `StoryMapProjection` permanece apenas como fallback de teste.
- **3D terrain:** não existe no MapLibre Native; o que há é `raster-dem` +
  `hillshade`/color-relief. Nada de “Google Earth” no MVP.

Se o MapLibre 13.7.1 OpenGL quebrar a geração, a decisão de contingência é
voltar para 12.3.1 e manter o port de projeção como caminho de produção; o
episódio deve ser registrado no ADR 0015.

### 2.5 Encode de publicação

| Item | Valor |
|---|---|
| Container | MP4, 9:16, 1080×1920 |
| Codec | H.264 **High**, **Level 4.1** |
| FPS | 30 (padrão); 60 só depois de validação em aparelho |
| Bitrate | **16 Mbps VBR** (10–20 Mbps conforme a cena) |
| Cor | **BT.709, SDR, limited range** |
| Áudio | nenhum |
| Metadados | sem GPS; nunca chamar `MediaMuxer.setLocation` |
| Keyframe | 1 s |

Perfil/level e VBR só são declarados quando o encoder do aparelho reporta
suporte; caso contrário o encoder usa o padrão e a exportação continua. As
chaves de cor são declaradas sempre. Resoluções 1440p/4K e HDR ficam fora: as
plataformas entregam 1080p e o ganho real vem de bitrate, cor declarada,
nitidez do mapa e safe area.

### 2.6 Layout 9:16 Reels-safe + pôster 4:5

- **Safe area:** 13% topo, **22% base**, 5,5% lados.
- **Card:** 62%–78% da altura.
- **Atribuição do mapa:** colada no canto inferior esquerdo, na borda do vídeo.
  Por decisão explícita do produto, ela pode ficar sob a UI do Reels/TikTok e
  fora da área segura.
- **Header:** distância à esquerda, tempo total à direita, dentro do topo
  seguro.
- **Rota:** moldura entre 26% e 76%, recalculada para o card novo.
- **Encerramento:** três essenciais + até dois extras, manchete, prova,
  logo/tagline.
- **Pôster:** 1080×1350 (4:5) com a rota como arte, tipografia grande, data e
  marca; a capa 9:16 continua sendo o frame final do story. **4:5 em vídeo
  fica fora** — o pôster é uma composição própria, não um crop.
- **Publicação:** vídeo em `Movies/MeuCoach`; capa e pôster em
  `Pictures/MeuCoach`. O compartilhamento é individual por arquivo
  (`ACTION_SEND`), sem `ACTION_SEND_MULTIPLE`: a maioria dos destinos escolhe
  um tipo por vez e um envio conjunto prometeria o que o app não entrega.
  Falha do pôster não invalida o vídeo; sem rota, o pôster é `null`.

> **Revisão de 13/11/2026:** feedback do usuário 0 no S24 Ultra. A atribuição
> do OpenStreetMap foi fixada no **canto inferior esquerdo, na borda do
> vídeo**, fora da área segura por decisão explícita do produto; o card voltou
> a **62–78%**. A regra anterior de manter a atribuição acima de 78% deixa de
> valer para o Story V3.

### 2.7 Números tabulares e card sem legenda

Ritmo, FC, calorias e tempo usam figuras tabulares (`fontFeatureSettings =
"tnum"`) para os dígitos não “dançarem” a cada frame. A legenda
“Ritmo = tempo por km · FC = frequência cardíaca · Calorias = energia gasta”
sai do card: é ruído para o público social e não ensina nada que o contexto
não diga.

### 2.8 Arquitetura: domínio puro em `core:domain/story`

O modelo puro do story (`StoryPlan`, `StoryPlanCalculator`,
`StoryHighlightsCalculator`, `StorySplit`, `StorySegment`, `RouteData`/
`RoutePoint`/`routeData`) sai de `:app` e passa a viver em
`core:domain/story`. `:app` mantém render, mapa, encode, WorkManager e UI.
Não há módulo novo, migração de Room nem dependência nova.

---

## 3. Alternativas rejeitadas

| Alternativa | Motivo da rejeição |
|---|---|
| Google Maps / `GoogleMap.snapshot()` | Termos proíbem exportar conteúdo para uso fora do serviço; MP4 é exportação. |
| Mapbox | Custo e termos; exige contrato para uso estático/exportação. |
| MapTiler no MVP | Custo mensal e cláusulas de “no manipulation” a revisar; plano B pago. |
| Satélite Esri no MVP | Licença comercial em revisão; satélite não é diferencial para corrida. |
| Áudio procedural/AAC | Decisão de produto: vídeo mudo, música no app de destino. |
| Máscara de início/fim | Decisão de produto do usuário 0: rota completa. |
| 3D terrain real | MapLibre Native ainda não implementa `terrain`; esforço desproporcional. |
| 4:5 em vídeo, 4K, HDR | Plataformas entregam 1080p; 1440p/4K triplica export e é downscalado. |
| LLM em texto ou decisão | O motor é determinístico; a receita só decide o que mostrar. |
| Protomaps/PMTiles agora | Custo de implantação e operação; a interface de troca já prepara o caminho. |

---

## 4. Consequências

### Boas

- O card e a atribuição ficam visíveis no Reels/TikTok, sem corte de UI.
- O usuário controla o que entra no vídeo, com limite rígido de legibilidade
  (3 essenciais + 2 extras) e presets.
- Nenhum número enganoso: calorias por km saem; ausência continua ausência.
- O master chega ao Instagram com cor declarada, perfil High e 16 Mbps.
- O mapa pode ser autoral e trocar de fonte sem reescrever o renderer.
- O domínio puro fica testável sem Android e sobe a cobertura de `core:domain`.

### Ruins / riscos

- **Refactor do domínio:** move arquivos e atualiza imports; o renderer e o
  worker passam a depender de `core:domain/story`.
- **Goldens mudam:** safe area, card e atribuição alteram todos os frames do
  story; a regravação é intencional e revisada.
- **Suporte de encoder varia por aparelho:** perfil/level e VBR são
  condicionais; sem suporte, o vídeo ainda é gerado, mas sem a declaração.
- **MapLibre 13.7.1:** upgrade posterior (Sprint B) pode exigir rollback para
  12.3.1; o port de projeção fica como contingência.
- **Sem áudio:** o vídeo é mudo por decisão; a percepção fora do Instagram
  depende do usuário adicionar música.
- **Sem satélite:** o mapa é vetorial/autoral; não há imagery no MVP.

### Fora de escopo (explícito)

Áudio/música, máscara de privacidade, Google/Mapbox/MapTiler, satélite, 3D
terrain, vídeo 4:5/1:1/16:9, 4K/HDR, Protomaps implementado, LLM, grafo
social, recorde/prova, 60 fps e fallback offline. Esses itens só entram depois
das Etapas 1 + 2 e de validação em aparelho.

---

## 5. Implementação e aceite

Ordem do Sprint A (Etapa 1):

1. `D0.1` — este ADR e o índice em `DECISIONS.md`.
2. `VIS-01` — safe area 13/22/5,5; card 62–78%; atribuição no canto inferior
   esquerdo, fora da safe area por decisão do produto.
3. `ENC-01` — BT.709/SDR/limited, High/4.1 condicional, 16 Mbps VBR.
4. `VIS-02` (parcial) — `tnum` nos textos e remoção da legenda do card.
5. `DOM-01` — modelo puro em `core:domain/story`.

Aceite do Sprint A: `:app:assembleDebug`, `check`, `spotlessCheck` e
`koverVerify` verdes; goldens do story regravados e revisados; card acima de
78% e atribuição no canto inferior esquerdo por decisão do produto; nenhuma
trilha de áudio, nenhum GPS e nenhum dado real de saúde no diff.

## 6. Referências

- `docs/STORY_V3_PLANO_DE_ACAO.md` §1, §3, §4, §11.
- `docs/STORY_V3_ANALISE_E_DIRECIONAMENTO.md` §2, §5–§8.
- `docs/adr/0015-story-video-map.md` — mapa, snapshot e atribuição.
- `docs/adr/0018-story-export-background.md` — export em segundo plano.
- `docs/adr/0019-story-camera-projection.md` — câmera e projeção.
- Google Maps Platform Terms of Service — proibição de exportar conteúdo.
- OpenFreeMap / OpenMapTiles / OpenStreetMap (ODbL) — tiles e atribuição.
