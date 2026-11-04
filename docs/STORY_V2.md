# Story do treino v2 — “O Veredito”

**Status:** Fases A e B implementadas em 03/11/2026 · câmera v2 (zoom por card + pitch/yaw) em 03/11/2026 · geração em segundo plano e cancelamento real em 04/11/2026 (`ADR 0018`) · vídeo público sem julgamento de plano e mapa Terreno em 04/11/2026 (§16) · Fases C–D propostas
**Data:** 03/11/2026
**Não implementado:** áudio, morph rota → logo e auto-seleção dos kms (Fases C/D). Nenhuma linha deste documento altera o motor determinístico.

---

## 1. O que existe hoje (e por que ainda não viraliza)

O `P3-05` já entrega o que a maioria dos apps de corrida não entrega: vídeo 9:16, 1080×1920, mapa vetorial real, rota animada, card por km e frame final com logo, tudo no aparelho. Tecnicamente está à frente de 90% dos apps.

O problema não é capability, é **narrativa e assinatura visual**:

| Hoje | Efeito percebido |
|---|---|
| Mapa estático em top-down; só a linha se move | Lê como “print animado”, não como vídeo |
| Card por km com 3 números fixos | Informativo, mas genérico; Strava/Relive também mostram números |
| Cards aparecem/somem sem coreografia | Não há ritmo, tensão nem clímax |
| “TREINO CONCLUÍDO” no final | Todos os apps dizem isso; não diz nada sobre *este* treino |
| Rota sempre lima, sem relação com o esforço | Perde a informação mais bonita que o app tem: a FC |
| Header em y≈144–320 px e rodapé em y≈1776–1860 px | Ficam **debaixo da UI do Instagram** (topo ~250 px, base ~250 px) |
| 24 fps, sem áudio, sem capa | Instagram reencoda a 30 fps; o story chega “duro” e mudo |

O ativo exclusivo do MeuCoach não é o mapa. É o **julgamento**: o app sabe se o treino foi o treino que ele prescreveu. Nenhum concorrente coloca isso no vídeo.

---

## 2. O que o mercado tem (e você talvez não saiba)

| App / serviço | O que faz | O que falta |
|---|---|---|
| **Strava Flyover** | Voo 3D sobre o terreno com a linha laranja, compartilhável nos Stories | Não avalia o treino; não mostra FC; é sobre o lugar, não sobre o atleta |
| **Relive** | Voo 3D com fotos e música, gerado automaticamente | Genérico, “postal de viagem”; nenhuma leitura de treino |
| **Pebbls / AvoMap / Rumbo** | Geradores de flyover a partir de GPX, até 4K | Ferramenta, não coach; sem dados fisiológicos |
| **Garmin** | Flyover 3D do percurso | Idem; preso ao ecossistema |
| **Nike Run Club / Apple Fitness** | Cards estáticos bonitos | Sem mapa animado, sem narrativa |
| **Runna** | Cards de plano e adesão | Estático; não vira vídeo |
| **Tendência 2025–26** | “Data storytelling”: Wrapped, kinetic typography, route art, pôsteres de percurso | Quase ninguém junta dado fisiológico + veredito + movimento |

Conclusão: **flyover 3D já é commodity**. O espaço em branco é o vídeo que responde “eu treinei certo?”. É exatamente onde o MeuCoach tem vantagem injusta.

---

## 3. Conceito: “O Veredito” em três atos

Duração 24–30 s, 30 fps, 1080×1920, silencioso-primeiro (funciona sem áudio) e com áudio-assinatura.

### Ato 1 — O voo (0–8 s): o lugar

- **Câmera com movimento real**: o snapshot do mapa é renderizado a 1,5× e o frame mostra um recorte que faz *zoom-in* lento, *pan* e uma leve **rotação (bearing)** acompanhando a direção da corrida.
- **Perspectiva** (fase 2): `MapSnapshotter` com `pitch` de 45–55°. Como o mapa é plano, a projeção é uma **homografia** — a rota pode ser desenhada com a mesma matriz (`Canvas.concat`), sem sair das ruas.
- **Grade de cor cinematográfica**: contraste + leve *teal* nas sombras, grão de filme (bitmap de ruído pré-gerado), vinheta radial e *bloom* suave no lima. Custa quase nada e muda a percepção de “print” para “filme”.
- **A rota nasce**: um “cometa” lima desenha o traçado com cauda que desbota; um pulso percorre a linha. O km atual aparece como um *tick* grande no mapa.

### Ato 2 — Os números (8–22 s): o esforço

- **Rota pintada pelo esforço**: cada trecho recebe a cor da zona de FC (azul → verde → amarelo → laranja → coral). É o dado mais bonito do app e ninguém faz isso em vídeo.
- **HUD cinético**: os cards deixam de ser painéis estáticos. Entram com *spring*, números rolam (contador), e uma **barra de splits** cresce no rodapé mostrando todos os kms — o km atual acende.
- **Veredito por km**: cada card ganha um selo de uma palavra, derivado da sessão prescrita e das zonas:
  - `NO ALVO` (lima) — esforço dentro do que foi pedido;
  - `FORTE DEMAIS` (coral) — acima da zona-alvo;
  - `LEVE DEMAIS` (azul) — abaixo da zona-alvo.
  Não é opinião de LLM: é a mesma regra determinística do motor, com teste.
- **Ritmo de câmera**: *speed ramp* — desacelera no km, acelera entre kms. O vídeo respira.

### Ato 3 — O veredito (22–30 s): o coach

- **Selo grande** com o resultado da sessão: `82% NO ALVO`, `FÁCIL BEM FEITO`, `QUALIDADE COMPLETA` — com um “thump” visual (escala + glow) no primeiro frame.
- **Uma frase do coach**, curta e determinística: “Fácil bem executado. Carga +4. Amanhã: 40 min leve.” — fecha o ciclo esforço → leitura → próximo passo. Nenhum concorrente faz isso.
- **Assinatura de marca**: a rota se contrai e vira o monograma/wordmark do MeuCoach (morph). É o momento “que app é esse?”.
- **Capa/pôster**: exportar também um PNG 1080×1920 do frame final (com a rota como arte) para feed e para a capa do story.

---

## 4. Os sete detalhes que criam a assinatura

1. **Rota colorida por zona de FC** — informação + beleza; só o MeuCoach tem zonas calibradas.
2. **Selo de veredito por km** — transforma métrica em julgamento; é o “porquê” do produto.
3. **Câmera com zoom, pan e rotação** — tira o vídeo do lugar de “mapa parado”.
4. **Grão + vinheta + grade de cor** — o “filme” que o Instagram não reencoda para baixo.
5. **HUD cinético com barras de split** — ritmo interno, não slideshow.
6. **Áudio procedural** — batida na cadência do corredor (passos/min ÷ 2), *tick* a cada km e um logo sonoro de 3 notas. 100% sintetizado, sem licença. Se o Instagram trocar a música, o vídeo continua funcionando mudo.
7. **Morph rota → logo** — assinatura de movimento reconhecível em 1 segundo, mesmo sem som.

---

## 5. O que está faltando e ninguém no mercado entrega junto

- **Veredito de adesão ao plano** no vídeo (não só “o que eu fiz”, mas “era isso que eu devia fazer”).
- **Zona de FC pintada na rota** (data art fisiológica).
- **Próximo treino** no encerramento (“amanhã: leve”) — o story vira coaching, não vaidade.
- **Segurança de privacidade no vídeo**: mascarar 200–300 m ao redor de casa no início/fim. Strava tem zonas de privacidade no app, mas não nos vídeos.
- **Auto-seleção do que importa**: em vez de todos os kms, escolher o km mais rápido, o mais lento e o “momento” (maior FC) — narrativa em 30 s, não planilha.
- **Área segura do Instagram** respeitada por design (hoje o header e o rodapé ficam sob a UI do app).
- **Capa + vídeo + pôster** como um pacote de publicação, não só um MP4.
- **Recorde/marco detectado** (“primeiro 5k”, “melhor pace em 3 km”) com selo próprio — usa o histórico que o app já calcula.

---

## 6. Como fazer sem quebrar o que importa

| Camada | Custo | Ganho | Dependência |
|---|---|---|---|
| Área segura 9:16 + 30 fps + capa PNG | Baixo | Alto | Nenhuma |
| Grade de cor, grão, vinheta | Baixo | Alto | Nenhuma |
| Câmera zoom/pan/rotação | Baixo | Alto | Nenhuma (matemática pura) |
| Rota por zona de FC | Baixo/Médio | Muito alto | `HrZoneDistribution` (já existe) |
| HUD cinético + barras de split | Médio | Alto | Nenhuma |
| Veredito por km + frase final | Médio | **Muito alto** | `PlannedSession` + zonas (helper puro novo) |
| Morph rota → logo | Médio | Alto | Asset da logo |
| Áudio procedural | Médio | Médio/Alto | `MediaCodec` de áudio + `MediaMuxer` |
| Perspectiva (pitch/homografia) | Alto | Alto | `MapSnapshotter` + matriz |
| Terreno 3D real (DEM) | Muito alto | Alto | Fonte de DEM + suporte do snapshotter |

**Regras que continuam valendo:** o motor determinístico decide o veredito; nenhum LLM entra no vídeo; nenhuma coordenada exata sai do aparelho; tudo é gerado localmente (o mapa continua sendo a única chamada de rede).

**Regras que precisam de exceção consciente (registradas em `ADR-0012`/`ADR-0015`, restritas à exportação social):**

- `DESIGN_SYSTEM.md` §2.6 (“movimento com propósito; nada decorativo”): grão, câmera e morph são **decoração deliberada** no contexto de exportação social. A exceção vale só para o vídeo, não para a UI.
- `PRODUCT.md` §5.1 (“explicar, não impressionar”): o story é a única superfície em que impressionar é o objetivo — mas cada número continua vindo do motor e o veredito é explicável.
- Paleta: o vídeo pode usar contraste e saturação acima do app; os tokens de marca (lima, coral, tipografia) continuam sendo a base.

---

## 7. Roadmap sugerido

### Fase A — “Parece filme” (1–2 dias)
- Área segura do Instagram (header abaixo de 250 px, rodapé acima de 250 px).
- 30 fps, 10 Mbps; capa PNG do frame final.
- Grão, vinheta, grade de cor e *speed ramp* no card.
- Câmera: zoom lento + pan + rotação pelo bearing da rota.

**Aceite:** o golden do frame e um MP4 real no aparelho passam de “print animado” para “vídeo”.

### Fase B — “O veredito” (2–4 dias) — implementada em 03/11/2026
- `StoryVerdictCalculator` puro: `PlannedSession` + distribuição de zonas → selo por km + selo final + frase.
- Selo de veredito no card, barra de splits, contador animado.
- Frase do coach no encerramento (com fallback quando não há plano ou calibração: “Corrida feita. Cada corrida conta.”).
- Rota colorida por zona.

**Aceite:** teste unitário do veredito com zona no alvo, acima e abaixo; golden com selo; nenhum `ruleId` visível.

### Fase C — “Assinatura” (2–3 dias)
- Morph rota → logo.
- Áudio procedural (cadência, ticks, logo sonoro) e opção “sem áudio”.
- Auto-seleção dos kms “interessantes”.
- Mascaramento de início/fim (privacidade).

**Aceite:** reconhecível em 0,5 s sem som; áudio mudo não quebra a narrativa.

### Fase D — “Próximo nível” (avaliar)
- Perspectiva 45–55° com homografia.
- Terreno/DEM, se o `MapSnapshotter` suportar sem dependência nova.
- Export 4:5 e 1:1 para feed/WhatsApp.
- Recorde/marco com selo.

---

## 8. Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Export 1080p a 30 fps ficar lento | Medir no aparelho; manter 24 fps como fallback configurável |
| Áudio aumentar o tempo de export | Sintetizar em paralelo ao vídeo; muxer aceita trilha AAC |
| Instagram reencodar e perder grão | Grão grande e de baixa frequência; testar com upload real |
| Veredito soar julgador | Linguagem sem culpa (`PRODUCT.md` §5.5): “forte demais hoje” em vez de “errado” |
| Plano ausente/atividade antiga | Fallback explícito: o vídeo vira “esforço” e não inventa alvo |
| Mapa sem rede | Já tratado com mensagem clara; a capa também depende do mapa |
| Privacidade da rota | Máscara de 200–300 m no início/fim, documentada no ADR |

---

## 9. Decisões que o usuário 0 precisa tomar

1. **Tom do veredito:** técnico (“82% no alvo”) ou de treinador (“fácil bem feito”)?
2. **Nível de ousadia visual:** aceita grão, vinheta e morph (mais “filme”), ou prefere manter o minimalismo do app?
3. **Escopo da Fase A:** implemento já a área segura + 30 fps + capa + câmera, ou espero a validação do mapa em aparelho real?

### Decisões tomadas em 03/11/2026

1. **Tom de treinador, com o número como prova.** O selo grande usa a frase (“Fácil bem feito”, “Qualidade completa”); o percentual entra menor, logo abaixo (“82% no alvo”). A frase cria orgulho; o número sustenta a credibilidade e mantém a auditoria. Nada de “errado” ou culpa (`PRODUCT.md` §5.5).
2. **Ousadia visual aprovada:** grão, vinheta, grade de cor e morph rota → logo fazem parte da assinatura do vídeo. A exceção fica restrita à exportação social; a UI do app continua minimalista.
3. **Fase A aprovada e implementada.** Fases B (veredito) e C (áudio/morph) ficam para as próximas sessões.

---

## 10. Nota de implementação — Fase A (03/11/2026)

**Objetivo:** tirar o vídeo de “print animado” sem tocar no motor e sem dependência nova.

- **Área segura do Instagram:** a moldura 9:16 passou a reservar 13% no topo e 13% na base (`StoryLayout`). Logo, métricas, card e atribuição OSM ficam fora da UI do Instagram. O rodapé com logo foi substituído por: logo no topo, atribuição com fundo próprio e, no frame final, o logo + “Seu treino, com contexto.” dentro do card.
- **Câmera cinematográfica:** `StoryMotion` aplica zoom (1,08 → 1,13), rotação suave (0 → 2° → 0) e deriva mínima ao mapa **e** à rota, com a mesma matriz, mantendo o traçado alinhado às ruas.
- **Supersampling:** o snapshot do mapa passou a ser renderizado a 1,15× (1242×2208) e desenhado reduzido no frame; o push continua nítido em 1080p.
- **Film look:** grade de cor no mapa (saturação 0,88 + contraste), grão de filme determinístico (semente fixa), vinheta radial e sombra leve nas métricas do cabeçalho para leitura sobre o mapa claro.
- **Ritmo:** 30 fps (o Instagram reencoda para 30) e 10 Mbps; a rota desacelera perto do card e acelera no meio do trecho (`StoryMotion.progressAt` com smoothstep).
- **Capa:** o exporter salva também um PNG 1080×1920 do frame final em `Pictures/MeuCoach` (Android 10+) e a UI informa “Vídeo e capa salvos na galeria”. No Android 9 ou anterior a capa não vai para a galeria.
- **Performance:** o buffer de pixels do encoder é reutilizado entre frames (antes eram ~8 MB alocados por frame). O grão é aplicado pelo exporter (`StoryFilm`), fora do renderer, para não inflar os goldens.
- **Testes:** `StoryLayoutTest` (safe areas, sobreposição e escala), `StoryMotionTest` (easing, câmera e speed ramp), `StoryFilmTest` (grão determinístico), câmera e screenshots do story atualizados; teste de UI para a mensagem da capa.

**Ainda pendente da Fase A:** validar em aparelho real o tempo de export a 30 fps, a nitidez do supersampling e a rotação de 2° com o Instagram reencodando.

---

## 11. Nota de implementação — Fase B (03/11/2026)

**Objetivo:** transformar métrica em leitura de treino sem inventar julgamento.

- **`StoryVerdictCalculator`** (`core:domain`): recebe a `PlannedSession`, a distribuição de zonas, o LTHR, a FC média por km e a próxima sessão. Devolve tom, manchete, prova (`% no alvo`), frase do coach, selos por km, zonas-alvo, `ruleId`/`evidenceId` e confiança. Nada de LLM.
- **Alvos por tipo:** fácil → Z2 (`R-ZONE-01`); longão → Z1–Z2; qualidade → Z4–Z5. Uma sessão conta como “no alvo” com pelo menos 70% do tempo medido dentro das zonas-alvo. Abaixo disso, o lado com mais tempo decide entre “forte demais” e “leve demais”; empate escolhe a leitura mais gentil.
- **Fallbacks honestos e voltados ao público do story:** sem plano, sem calibração ou sem FC, o tom fica nulo e o encerramento assume “Corrida feita” + “Cada corrida conta.”. A cópia não cita plano, calibração, LTHR ou “sem alvo”: quem assiste quer o treino, não o diagnóstico do app. Nenhum selo é inventado.
- **Frase do coach:** combina a manchete com o próximo treino da mesma semana. “Amanhã” só aparece quando a sessão é literalmente do dia seguinte; nos demais casos, “Próximo”.
- **Card por km:** selo `NO ALVO` (lima), `FORTE DEMAIS` (coral) ou `LEVE DEMAIS` (azul), barra de splits com o km atual aceso e contador de ritmo/FC/kcal nos primeiros 0,45 s.
- **Frame final:** `TREINO CONCLUÍDO` vira overline; a manchete do veredito ocupa o lugar principal, o percentual entra logo abaixo e a frase do coach fecha o card.
- **Rota por zona:** `StoryPlanCalculator` produz `StoryZoneRun`s a partir da FC de cada trecho; o renderer pinta a parte percorrida com as cores de Z1–Z5, mantendo o casco escuro e o marcador lima. Sem LTHR, a rota continua no lima da marca.
- **Fluxo:** o `ActivityDetailViewModel` busca a sessão planejada da data local da atividade e a próxima sessão da semana antes de montar o plano.
- **Testes:** 14 novos em `StoryVerdictCalculatorTest` (no alvo, acima, abaixo, longão, qualidade, descanso, sem plano, sem calibração, sem FC, selos por km e texto sem `ruleId`), 2 em `StoryPlanCalculatorTest`, 2 em `ActivityDetailViewModelTest` e 2 goldens novos (`story-card-verdict-dark.png`, `story-final-verdict-dark.png`).

**Ainda pendente da Fase B:** validar a leitura dos selos em aparelho real e revisar o tom das manchetes com o usuário 0.

---

## 12. Nota de implementação — câmera flyover (03/11/2026)

**Objetivo:** sair do "mapa parado com tilt mínimo" e criar um voo perceptível, sem sair do motor determinístico e sem dependência nova.

- **Câmera que segue o corredor:** `StoryMotion.camera(timeSec, plan)` calcula o ponto da rota no progresso atual, mantém esse ponto em 43% da altura e puxa o mapa para perto. O vídeo começa no overview (1,20×/6°), mergulha em 1,6 s para 1,65×/18° e volta ao overview no início do frame final.
- **Ângulo de câmera:** a rotação acompanha a direção da rota (limitada a ±6° para o snapshot ainda cobrir o 9:16), o `pitch` chega a 18° e o `yaw` dá um roll de até 1,5°. O `StoryPerspective` agora também derruba a borda superior em direção ao horizonte, o que dá leitura de plano inclinado, não só de trapézio horizontal.
- **Zoom por card:** enquanto um card de km está na tela, entram +0,06 de escala e +3° de pitch. Entre cards a câmera respira; no encerramento ela se afasta para mostrar a rota inteira.
- **Rota legível no zoom:** a espessura da linha, o glow e os marcadores são divididos pela escala da câmera, então continuam com o mesmo tamanho de tela enquanto o mapa cresce.
- **Mapa nítido:** o snapshot subiu para 2,25× (2430×4320) e o layout reserva a escala de overview; o zoom de 1,65× continua praticamente 1:1 (ver §14).
- **Limite consciente:** ainda é uma homografia sobre o snapshot, não terreno 3D. Um pitch real de MapLibre exigiria re-renderizar o mapa por frame; a versão atual preserva o alinhamento rota/ruas e é viável no MediaCodec.
- **Testes:** `StoryMotionTest` cobre overview, mergulho, follow do ponto, heading, cards, pullback e teto de escala; `StoryPerspectiveTest` cobre o horizonte e a compressão lateral. Goldens do story atualizados.

**Ainda pendente:** validar o movimento em aparelho real e medir o tempo de export com o snapshot maior; se o custo pesar, reduzir o supersampling para 1,5× e o follow para 1,55×.

---

## 14. Nota de implementação — mapa: polimento e limite técnico do tilt (04/11/2026)

**Objetivo:** lapidar o mapa do story sem quebrar o alinhamento rota/ruas e registrar por que o tilt nativo ainda não entrou.

- **Estilo mais limpo (`OpenFreeMapStyle`):** o `liberty` é filtrado em tempo de execução para remover camadas de POI, transporte e aeroporto (ícones e rótulos minúsculos que competiam com o traçado). Labels de cidade/rua continuam ampliados e com halo branco. O resultado é um mapa de leitura "outdoor", com a rota como único elemento de cor.
- **Mais nitidez:** supersampling do snapshot subiu de 1,95× (2106×3744) para 2,25× (2430×4320). O custo é memória (~42 MB de ARGB no preview/worker) — validar em aparelho de RAM baixa antes de subir de novo.
- **Foco na rota:** um halo radial lima (alpha baixo) acompanha o marcador durante o vídeo, e ticks discretos marcam cada km já percorrido. São mudanças de `Canvas`, determinísticas, sem custo de rede.
- **O que o tilt nativo exige (e por que ficou para um spike):** `MapSnapshotter` do MapLibre 12.3.1 aceita `CameraPosition` com `tilt`/`bearing`, mas **não expõe `Projection` nem `getMapAsync`** (confirmado inspecionando o AAR). Sem projeção, desenhar a rota animada no `Canvas` sobre um snapshot inclinado exigiria reimplementar a matriz de câmera do MapLibre (ou renderizar via `MapView` offscreen), e o alinhamento rota/ruas só pode ser validado em aparelho real. Duas saídas possíveis:
  1. **Portar a matemática de câmera** (`TransformState::latLngToScreenCoordinate` + `getProjMatrix`/`getCoordMatrix`) para Kotlin puro, com teste de paridade no top-down e checklist de calibração no aparelho;
  2. **Assar a rota no snapshot** via `GeoJSON`/`line-gradient` (alinhamento garantido pelo próprio MapLibre) e revelar por keyframes, aceitando menos fluidez no traçado.
- **Decisão:** manter o tilt "de matriz" atual nesta rodada e tratar o tilt nativo como spike próprio, com validação em aparelho. A Fase M2 (flyover por keyframes) só faz sentido depois dessa decisão.

---

## 15. Nota de implementação — performance do export (04/11/2026)

O story deixou de prender a tela (ADR 0018) e o tempo de geração caiu de **~3 min para ~50 s** no Galaxy S24 Ultra.

- **O que estava lento:** medição por frame no aparelho mostrou ~190 ms/frame — `fill` (RGB→YUV no CPU, 2 M pixels) 113 ms e `render` 75 ms. O encoder não era o gargalo.
- **O que mudou:** o `MediaCodec` passou a receber o frame pelo **Surface** (`COLOR_FormatSurface`) via EGL/GLES (`StorySurfaceEncoder`), com o pipeline em **thread única** (contexto EGL é preso à thread; os callbacks de progresso suspendem a corrotina). Isso levou `fill` para ~1,5 ms.
- **Render:** removemos o filtro de cor aplicado ao bitmap do mapa **a cada frame** (~13 ms); o estilo OpenFreeMap já entrega o contraste da marca. A composição do mapa em GL (para chegar a 30 fps com folga) fica como próximo passo.
- **Foreground:** o worker sobe como serviço `dataSync`; a notificação pertence ao WorkManager e é atualizada com `setForeground(...)` — postar uma notificação própria com o mesmo id derrubava o serviço.
- **Validado no aparelho:** MP4 final com 1080×1920, 30 fps, 900 frames, 30,000 s; capa PNG correta; export concluído com o app em segundo plano.

---

## 16. Nota de implementação — vídeo público, mapa Terreno e primeiro uso (04/11/2026)

**Objetivo:** o story é assistido por quem nunca viu o plano; julgar adesão ("longão forte demais", "no alvo") só faz sentido para o dono do treino.

- **Destaques objetivos:** o vídeo deixou de mostrar veredito de plano. O encerramento usa `StoryHighlightsCalculator` (puro): `2ª METADE MAIS RÁPIDA`, `RITMO CONSTANTE`, `KM MAIS RÁPIDO` ou `CORRIDA CONCLUÍDA`, com prova curta e autoexplicativa. O selo por km virou `KM MAIS RÁPIDO` (somente em corridas com 3+ kms).
- **Leitura do coach é privada:** a prévia no app mostra "Leitura do coach (só você vê): …" usando o veredito determinístico; ele não entra mais no vídeo.
- **Estilos de mapa:** Clássico (vetorial), Terreno (hillshade Mapzen) e Satélite (World Imagery + labels + véu). Em cidades planas o relevo quase não aparece — satélite é o estilo que melhor responde “onde eu corri”.
- **Mapa Terreno:** novo `StoryMapStyle.TERRAIN` insere `raster-dem` (Mapzen/AWS, `terrarium`) e uma camada `hillshade` no estilo `liberty`; a atribuição do frame passa a citar "Elevation Mapzen". É relevo (sombreamento), não terreno 3D — o spike de câmera inclinada segue em §14.
- **Primeiro uso:** `StoryIntroStore` mostra uma vez o aviso de que os tiles (e o relevo) são baixados e ficam em cache, sem enviar coordenadas exatas.
- **Botão:** "Criar story" ocupa a largura do card e usa 48 dp (`McPrimaryButton.minHeight`).
- **Testes:** `StoryHighlightsCalculatorTest`, `StoryIntroStoreTest`, seleção de estilo no `ActivityDetailViewModelTest`, hillshade no `OpenFreeMapStyleTest` e estilo no codec da paleta; goldens do story refeitos.

---

## 17. Nota de implementação — rosto no story e leitura do coach fora da prévia (04/11/2026)

- **Pin com a foto do perfil:** o marcador que percorre a rota virou um badge circular com a foto do usuário (anel lima + ponteiro). Ele é desenhado **em espaço de tela**, fora da matriz da câmera — assim não gira junto com o mapa — com diâmetro de ~114 px em 1080p (visível no Instagram sem cobrir a rota). Sem foto, volta o ponto lima antigo.
- **Foto na capa:** o exportador carrega a foto do perfil e usa o mesmo pin no frame final/capa.
- **Leitura do coach saiu da prévia:** o veredito determinístico deixou de aparecer na hora de gerar o story; agora é um card privado na tela da atividade (“LEITURA DO COACH”), separado do vídeo público.
- **Dados:** nome e caminho da foto vivem no perfil (Room v10); a foto é copiada para armazenamento privado do app e removida no “apagar todos os dados”.
- **Testes:** golden `story-avatar-dark.png` (pin), migração 9→10, nome obrigatório no onboarding e leitura do coach no `ActivityDetailViewModelTest`.

---

## 18. Nota de implementação — câmera 3D real (projeção MapLibre portada, 04/11/2026)

- **O que mudou:** o snapshot deixou de ser top-down com tilt falso. O MapLibre renderiza com **pitch de 38°** e a rota é desenhada pela **mesma câmera**, usando a projeção portada (`StoryMapProjection`) — o traçado continua sobre as ruas.
- **Como:** port fiel da cadeia de matrizes (`perspective → flip → translate → rotateX(pitch) → rotateZ(-bearing)`), FOV vertical de 36,87°, Mercator 512. `calculateForStory` ajusta o zoom iterativamente para o bounding box caber no route square.
- **Câmera do vídeo:** a movimentação (`StoryMotion`) continua 2D (zoom/pan/rotação); o warp sintético do `StoryPerspective` é desligado (`nativeTilt = true`) para não duplicar a perspectiva.
- **Dados:** `StoryPlan.geoPoints` (amostrados até 400) são a fonte da rota no mapa; o pin da foto é desenhado em espaço de tela.
- **Validação:** teste de paridade top-down (±2 px) e de enquadramento inclinado; frames reais em Clássico e Satélite com a linha sobre as ruas no S24 Ultra.
- **Limite:** tilt fixo no snapshot; mudança de pitch durante o vídeo exigiria keyframes de snapshots ou pipeline GL (ADR 0019).
