# PROGRESS.md — Diário de progresso

**Atualizado:** 04/11/2026 (story em segundo plano e cancelamento real)

## Estado geral

- **Fase:** 1 — Coach (em andamento)
- **Build:** verde (`./gradlew check :app:assembleDebug :app:verifyRoborazziDebug koverVerify`)
- **Testes:** 772 testes, 0 falhas (27 em `core:model`, 279 em `core:domain`, 89 em `core:data`, 377 em `:app`)
- **Cobertura `core:domain`:** 98,17% de linhas (2.622/2.671), 83,16% de branches (2.049/2.464), 96,90% de instruções (19.475/20.099)
- **Parser FIT:** 93,01% de branches (572/615); gate próprio exige ≥ 90% de branches
- **Lint:** 0 erros; 23 avisos não bloqueantes (versão/target, KTX, naming, ícone e MapLibre 13.x)
- **Dados reais:** 4 FIT e 3 TCX sanitizados; GPX apenas sintético
- **Persistência:** Room 2.8.5, schema v9 (`heatLevel` no check-in e `treadmillAccess` no perfil), migrações 1→2 até 8→9 testadas.
- **Fonte de fontes:** `ActivitySummary.sources` expõe todos os formatos que contribuíram (ex.: `FIT · TCX · GPX`).
- **Importação real:** `ImportStaging`, `StagedImportProcessor`, `ImportWorker` (`@HiltWorker`), `ImportCoordinator` e `ShareIntentReader`. WorkManager 2.12.0 com `HiltWorkerFactory` (manifest sem `WorkManagerInitializer`).
- **UI de atividades:** `ActivitiesScreen`, `ActivitiesViewModel`, `ActivityFormatters` (`sourcesLabel`) e `ImportJobCard`.
- **Detalhe da atividade:** hero, métricas, gráfico pace × FC (Canvas), splits, rota offline e insights com `ruleId`/`evidenceId`/confiança.
- **Perfil e onboarding:** formulário em 3 passos (dados, objetivo/disponibilidade, rotina), validação, persistência e edição.
- **Calibração:** VDOT/LTHR/pace de limiar por prova ou TT ≤ 8 semanas, com tiers A/B/C e tela de resultado.
- **LTHR manual:** campo no perfil (100–230 bpm, acima da FC de repouso e abaixo da FC máxima vista) para quem já conhece o valor; o app não estima a partir de qualquer corrida.
- **Calibrar por uma corrida:** corridas de 25–35 min com FC sustentada podem pré-preencher a calibração; o botão só aparece quando o arquivo passa nos critérios e o salvamento exige confirmação explícita.
- **Zonas:** distribuição de tempo por zona de FC no detalhe, usando o LTHR calibrado.
- **Carga:** série diária TRIMP/rTSS, CTL/ATL/TSB, ACWR, monotonia e tela de Tendências com PMC.
- **Check-in:** 4 perguntas (sono, fadiga, dor, estresse) + sinais de doença/lesão e calor autorreportado, persistidos por data, usados na prontidão/prescrição e resumidos em um histórico dos últimos 7 check-ins com Hooper.
- **CSV diário:** parser de sono/RHR/HRV/SpO2, persistência, importação pelo perfil e agregação de janelas 7/30/60 dias na prontidão.
- **Métricas diárias:** CSV e Health Connect são mesclados por campo, com precedência `MANUAL` > `Health Connect` > `CSV`; um sync não apaga mais o que só o CSV tinha (ADR 0016).
- **Health Connect:** leitura read-only de sono/RHR/HRV/SpO2, agregação por dia, permissões, sincronização incremental (primeira execução 30 dias, depois desde o último sucesso) e sync periódico diário via WorkManager.
- **Diagnóstico:** status do Health Connect, base local de métricas e confiança por componente na tela Hoje.
- **Histórico de prontidão:** últimos 14 dias em Tendências, reconstruídos com os dados disponíveis em cada data.
- **Recomendação diária:** guardrails (doença/vermelho → descanso, sono curto/âmbar → fácil, ACWR alto → sem aumento), fácil em Z2 com pace/FC quando calibrado e RPE quando não; calor informado desacelera o pace em 3% e reduz a confiança.
- **Plano semanal:** distribuição por disponibilidade, até 2 qualidades com progressão, longão 25–30%, taper por prova e deload a cada 4ª semana; com acesso a esteira, a qualidade inclui a alternativa por velocidade/inclinação.
- **Plano persistido:** a semana é gerada uma vez e salva em Room v7; check-ins não a reescrevem.
- **Taper e qualidade:** redução de 45–55% nas semanas de prova e qualidade nomeada por experiência (tempo/intervalos).
- **Progressão:** com semana anterior ≥80% concluída e guardrails verdes, libera uma segunda qualidade; volume segue limitado pela disponibilidade (ADR 0010).
- **Deload:** a 4ª semana consecutiva de plano reduz o volume em 25%, mantendo o tipo/intensidade das sessões e citando `R-PLAN-04`/`EV-ISSURIN-2010`; taper tem precedência (ADR 0011).
- **Conclusão do plano:** `PlanCompletionCalculator` resume as últimas 8 semanas (feitas, puladas e pendentes passadas) e a tela Tendências mostra a adesão semanal sem streak ou culpa.
- **Tendências de atividades:** volume semanal, consistência com heatmap, relação pace×FC com gráfico de eficiência (m/batimento), filtro de período (6/12/26 semanas) e recordes pessoais (maior distância, melhor pace ≥ 3 km e maior tempo) no `ActivityTrendsCalculator`.
- **80/20 por tempo:** `IntensityDistributionCalculator` soma tempo em Z1–Z2, Z3 e Z4–Z5 a partir dos samples, com quebra semanal, confiança por cobertura e card próprio em Tendências.
- **Ações do plano:** concluir ou pular sessão com motivo, persistido por sessão (Room v8); iniciar sessão guiada com alvos e cronômetro; trocar o conteúdo de duas sessões planejadas da semana com validação de guardrails.
- **Explicabilidade:** bottom sheet “Por que este treino?” com rationale, regras, evidências, confiança e dados usados/ausentes; o catálogo de rótulos mantém o `ruleId`/`evidenceId` bruto auditável.
- **Story de treino (P3-05, protótipo):** planejador de rota/splits/timeline, renderer Canvas 9:16 com mapa vetorial (`MapLibre` + `OpenFreeMap`, dados OSM) e frame final de 3 s (distância, tempo, ritmo médio, FC média, kcal e logo), export MP4 1080×1920 via MediaCodec, com galeria e compartilhamento; validado em aparelho real (mapa vetorial pendente de validação).
- **Mapa do story:** snapshot 1080×1920 gerado no preview e reaproveitado no vídeo, com estilo vetorial OpenFreeMap (`liberty`) filtrado em tempo de execução (POIs, transporte e aeroportos ocultos) e labels ampliados; fallback para raster OSM; câmera calculada localmente a partir do `BoundingBox`, halo de foco na rota, ticks por km, atribuição OSM no frame e permissões de localização do SDK removidas. `MapLibre` 12.3.1 (última linha OpenGL), ABIs `arm64-v8a`/`x86_64`; snapshot em 2,25× (2430×4320), vídeo 1080p a 10 Mbps e 30 fps.
- **Story v2 — Fase A:** área segura do Instagram, câmera com zoom/rotação alinhada à rota, grão/vinheta/grade de cor, 30 fps, snapshot supersampleado e capa PNG do frame final.
- **Story v2 — Fase B:** veredito determinístico por km e no encerramento (`StoryVerdictCalculator`), rota colorida por zona de FC, barra de splits e contador no card; sem plano/calibração/FC o story não inventa alvo.
- **Story em segundo plano (ADR 0018):** exportação via `WorkManager` (`StoryExportWorker`) com nome único por atividade, plano reconstruído dos repositórios e mapa da prévia reaproveitado em cache; cancelamento cooperativo no encoder (MP4 parcial apagado), progresso a cada 0,6 s com ETA suavizado, notificação de concluído/falha e estado restaurado ao reabrir a tela.
- **Story v2 — câmera:** flyover que segue o corredor (overview 1,18×/8° → follow 1,72×/32°), rotação de até 22° pela direção da rota, zoom extra no card e pullback no encerramento; homografia aplicada ao mapa **e** à rota, com espessura da rota compensada pela escala.
- **Backup e reset:** tela Dados exporta todo o conteúdo em JSON e apaga perfil, corridas, check-ins, métricas e planos em uma transação, com confirmação.
- **Notificações locais:** lembrete de check-in às 18h e do treino às 7h, com deep link para Hoje/check-in e permissão no Android 13+; importação concluída (sucesso, duplicado ou falha) em canal próprio, sem som, com atalho para o treino.
- **Tela Hoje:** prontidão, treino recomendado, plano da semana, carga e atividade recente puxados do banco; sem nome/weather falsos e sem números de demonstração quando falta carga.
- **Share intent:** `ACTION_SEND`/`SEND_MULTIPLE` no manifest e no `MainActivity`; `ImportMessages` unifica o texto de fila.
- **Comunicação:** IDs de regra/evidência fora das telas comuns; fontes sob demanda em `Ver fontes e detalhes técnicos`; jargão traduzido em Hoje, Tendências, Atividade, Calibração, Check-in, Perfil e onboarding.
- **Perfil → Saúde e dados:** o card explica o Health Connect em linguagem de produto, mostra o estado real (conectado, dias salvos, última leitura) e separa “Saúde” de “Backup e privacidade”.
- **Tutorial de boas-vindas:** tour de 4 telas na primeira abertura com perfil criado, explicando as telas/cards, o que manter em dia (check-in, corridas importadas, Health Connect, calibração) e a privacidade; revisível em Perfil → Ajuda → “Rever tutorial”; flag em SharedPreferences (`meucoach_tutorial`), sem migração de Room.

## Sessão 2026-11-04 — Story público: destaques objetivos, mapa Terreno e aviso
**Objetivo:** o vídeo passa a fazer sentido para quem nunca viu o plano; o mapa ganha relevo e a primeira geração é explicada.

- **Sem julgamento de plano no vídeo:** saíram “longão forte demais”, “no alvo”, “leve demais” e “amanhã: 40 min leve”. O card por km agora mostra só ritmo, FC e calorias (com selo “KM MAIS RÁPIDO” quando faz sentido) e o encerramento usa `StoryHighlightsCalculator`: “2ª METADE MAIS RÁPIDA”, “RITMO CONSTANTE”, “KM MAIS RÁPIDO” ou “CORRIDA CONCLUÍDA”, com prova objetiva (ex.: “km 3 em 6:12”).
- **Leitura do coach continua privada:** o `StoryVerdictCalculator` segue no plano e aparece no card de prévia como “Leitura do coach (só você vê)”, fora do vídeo.
- **Mapa Terreno:** `StoryMapStyle.TERRAIN` adiciona `raster-dem` + `hillshade` (tiles públicos Mapzen/AWS) ao estilo `liberty`, com atribuição “Elevation Mapzen” no frame. O estilo Clássico continua o padrão.
- **Mapa Satélite:** `StoryMapStyle.SATELLITE` usa World Imagery (Esri/Maxar) com véu escuro e as mesmas labels vetoriais por cima; atribuição “Imagery © Esri · Data © OpenStreetMap”. É o estilo recomendado para cidades planas (hillshade praticamente não aparece em Natal).
- **Aviso de primeira vez:** `StoryIntroStore` mostra um cartão explicando o download de tiles (e satélite/relevo conforme o estilo), que fica em cache e não envia coordenadas exatas; “Entendi” dispensa e persiste.
- **Botão “Criar story”:** largura total e 48 dp de altura (`McPrimaryButton` ganhou `minHeight`).
- **Testes:** `StoryHighlightsCalculatorTest` (5), `StoryIntroStoreTest`, casos de estilo no VM, `OpenFreeMapStyleTest` (hillshade) e codec da paleta; goldens do story refeitos.
- **Verificação:** `check`, `assembleDebug` e `verifyRoborazziDebug` verdes.

## Sessão 2026-11-04 — Story: progresso real, foreground e encoder na GPU
**Objetivo:** destravar a geração no aparelho (0% eterno, cancelar simbólico, 3 min de espera).

- **Progresso em 0%:** `lastProgressMs = Long.MIN_VALUE` fazia `now - lastProgressMs` estourar para negativo; `setProgress` nunca era chamado. A notificação escapava pelo caso `fraction <= 0`, o que mascarava o bug.
- **Foreground service:** `StoryExportWorker` sobe como `dataSync` antes do trabalho pesado e atualiza a notificação via `setForeground` (nunca com `notify` no mesmo id — isso substituía a notificação do FGS e o sistema derrubava o job).
- **Estado restaurado:** o `init` do `ActivityDetailViewModel` sobrescrevia o estado com `ActivityDetailUiState()` novo, apagando o `Generating/Done` restaurado do WorkManager; agora usa `update {}`. `FAILED` sem `outputData` passa a ler a mensagem persistida por `StoryExportFailureStore` (o WorkManager não persiste outputData em falha).
- **Encoder na GPU:** medição no S24 Ultra: ~190 ms/frame (fill YUV 113 ms + render 75 ms). O `MediaCodec` passou a receber o frame pelo Surface (`COLOR_FormatSurface`, EGL/GLES via `StorySurfaceEncoder`) em thread única; `fill` caiu para ~1,5 ms. Filtro de cor do mapa por frame foi removido (~13 ms). Total ~42–52 ms/frame; export de 30 s caiu de ~3 min para ~50 s.
- **Validação no aparelho:** MP4 1080×1920, 30 fps, 900 frames, 30,000 s; capa correta; export concluiu com o app em segundo plano.
- **Testes:** `StoryExportFailureStoreTest` (2), `StoryNotificationTest` (1); 760 testes no total.
- **Docs:** `ADR-0018` §2.4 (GPU/foreground) e `STORY_V2` §15 (números medidos).

## Sessão 2026-11-04 — Story: mapa mais limpo e nítido (e o limite do tilt)
**Objetivo:** elevar a leitura do mapa sem quebrar o alinhamento rota/ruas.

- **Estilo outdoor:** `OpenFreeMapStyle` agora remove camadas de POI, transporte e aeroporto do `liberty` antes do snapshot; labels de cidade/rua seguem ampliados com halo. Mapa limpo, rota como único elemento colorido.
- **Nitidez:** supersampling do snapshot de 1,95× para 2,25× (2430×4320); custo estimado de ~42 MB de ARGB — validar em aparelho de RAM baixa.
- **Foco na rota:** halo radial lima acompanhando o marcador e ticks discretos por km percorrido, ambos no `Canvas` (determinísticos, sem rede).
- **Tilt nativo — bloqueio registrado:** o `MapSnapshotter` 12.3.1 aceita `tilt`/`bearing`, mas não expõe `Projection`/`getMapAsync` (confirmado no AAR). Sem projeção, um snapshot inclinado desalinharia a rota animada. Saídas mapeadas em `STORY_V2` §14 (`ADR-0015` addendum): portar a matriz de câmera do MapLibre ou assar a rota no snapshot com revelação por keyframes. Fica como spike com validação em aparelho.
- **Golden:** 7 snapshots do story regravados (halo + ticks); nenhuma mudança em tutorial/perfil.
- **Testes:** +1 em `OpenFreeMapStyleTest` (clutter removido); verificação visual do story verde.
- **Verificação:** `check`, `:app:assembleDebug` e `koverVerify` verdes.

## Sessão 2026-11-04 — Story: geração em segundo plano e cancelamento real
**Objetivo:** tirar o story do fluxo "prende a tela e o cancelar é simbólico", sem tocar no motor determinístico.

- **WorkManager (`ADR 0018`):** `exportStory()` virou `StoryExportCoordinator` → `StoryExportWorker` (`@HiltWorker`), com nome único `story-export-<activityId>` e `ExistingWorkPolicy.REPLACE`. O app pode ser fechado; o worker continuar e notificar.
- **Plano reconstruído:** `StoryPlanLoader` + `StorySessionResolver` recarregam atividade, LTHR e sessões planejadas dentro do worker; o mapa da prévia viaja como PNG em `cacheDir/stories/story-map-<id>.png` e só é re-renderizado se o cache sumir (com `Result.retry()` para falha transitória de rede).
- **Cancelamento de verdade:** `CanvasStoryVideoExporter` (novo nome da implementação; interface `StoryVideoExporter`) recebe `isCancelled` derivado do `Job`, checa antes de cada frame e antes do `END_OF_STREAM`, libera codec/muxer e apaga o MP4 parcial. `cancelStory()` chama `cancelUniqueWork` e o worker trata `CancellationException` como cancelamento.
- **Progresso e ETA:** `setProgress` a cada 600 ms com fração e `etaSec`; `StoryEtaEstimator` (puro) suaviza a taxa e faz clamp; a UI mostra “Cerca de N s restantes”, aviso “Pode sair da tela” e botão Cancelar no estado de geração.
- **Estado restaurado:** o card observa o `WorkInfo` por nome único; reabrir a tela adota `Generating`/`Done`/`Error` de um export em andamento ou recém-concluído, ignorando saídas obsoletas quando o usuário já mudou de estado.
- **Notificação:** `StoryNotifier` em canal `meucoach-story`, com deep link para o treino e respeito a `POST_NOTIFICATIONS`.
- **Testes:** 28 novos — `StoryEtaEstimatorTest` (8), `StoryPaletteCodecTest` (2), `StoryExportProcessorTest` (6), `StoryExportCoordinatorTest` (4), `ActivityDetailViewModelTest` (7 de fluxo/estado) e `ActivityDetailScreenTest` (1 de geração); o encoder real continua validado só em aparelho.
- **Verificação:** `./gradlew check`, `:app:assembleDebug` e `koverVerify` verdes; lint 0 erros.

## Sessão 2026-11-03 — Tutorial de boas-vindas (primeira abertura)

### Concluído

- **Tour de 4 telas** (`ui/tutorial`): boas-vindas (“Seu coach de corrida”), as telas do app (Hoje/Corridas/Tendências/Perfil), o que mantém o coach informado (check-in das 4 perguntas com lembrete às 18h, corridas importadas via FIT/TCX/GPX compartilhados, Health Connect, calibração por prova/teste de 30 min) e “Você no controle” (nada sai do aparelho, explicação do treino sem jargão, registrar conclusão/adiamento/pulo, exportar ou apagar dados). Ícone de página em `accentSoft`, bullets com ponto lima, dots de progresso, botões `Continuar`/`Voltar`/`Tudo pronto` e `Pular` (finaliza e marca como visto).
- **Gate de primeira execução:** quando existe perfil e o tutorial nunca foi concluído, o app abre o tour em tela cheia (sem bottom bar) antes de Hoje. Usuários antigos veem o tour uma única vez após atualizar. `TutorialViewModel` (`@HiltViewModel`) expõe `seen`; `TutorialStore` persiste a flag em SharedPreferences (`meucoach_tutorial`), no padrão do `HealthSyncStore` — sem migração de Room.
- **Rever o tutorial:** nova seção “Ajuda” no Perfil (antes de “Editar perfil”) com card “Rever o tour de boas-vindas” e botão “Rever tutorial”; rota `tutorial` esconde a bottom bar e sempre finaliza com `popBackStack`.
- **Texto alinhado ao produto:** sem jargão interno (uma menção genérica a FC/HRV/SpO₂ já traduzidas em outras telas), tom sem culpa (“o app fica conservador” quando falta dado), “Por que este treino?” como porta de explicação.
- **Testes:** 10 novos do tutorial (`TutorialStoreTest` 2, `TutorialViewModelTest` 3, `TutorialScreenTest` 3, `TutorialScreenshotTest` 2 com goldens dark/light) + 1 no perfil (card abre o callback de revisão). Call sites de `ProfileContent` atualizados; goldens de Perfil regravados.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug koverVerify` — sucesso.
- 726 testes, 0 falhas (27 `core:model`, 279 `core:domain`, 89 `core:data`, 331 `:app`).
- Lint: 0 erros; 23 avisos não bloqueantes (aviso `UseKtx` do tutorial corrigido com `prefs.edit { }`).
- Goldens novos: `tutorial-dark.png` e `tutorial-light.png`.

### Decisões e observações

- **SharedPreferences, não Room:** a flag é preferência de UI, não dado do atleta; acompanha o padrão do `HealthSyncStore` e nunca exige migração de schema.
- **Depois do onboarding, não antes:** o tour assume que o usuário já tem perfil, então explica “Calibrar” e “Health Connect” com contexto real; o onboarding continua sendo a única coisa na primeira execução sem perfil.
- **Pular = visto:** “Pular” também marca o tutorial como concluído; a revisão fica no Perfil, sem insistência.
- **Conteúdo declarativo, não interativo:** seguiram o padrão do próprio onboarding (steps declarativos) em vez de spotlight sobre cards reais — mais barato de manter e testável em CI.

### Próximo passo

1. Validar o tour no aparelho real (fluxo pós-onboarding e o acesso pelo Perfil).
2. `P1-12`: validação E2E com 4 semanas de uso real.

## Sessão 2026-11-03 — Câmera flyover do story

### Concluído

- **Câmera que segue o corredor:** `StoryMotion` agora mantém o ponto atual da rota em 43% da altura, começa no overview (1,18×/8°), mergulha em 1,8 s para 1,72×/32° e puxa de volta no primeiro 1,2 s do frame final.
- **Rotação com direção:** a rotação acompanha o heading suavizado da rota, limitado a ±22° para o snapshot ainda cobrir o 9:16; o pitch sobe até 32° e o yaw dá um roll de até 2,5°.
- **Perspectiva mais 3D:** `StoryPerspective` passou a derrubar a borda superior em direção ao horizonte, além de estreitar o topo e alargar a base. O mapa lê como plano inclinado, não apenas como trapézio.
- **Cards e encerramento:** `+0,06` de escala e `+3°` de pitch durante o card; o pullback do encerramento revela a rota inteira atrás do frame final.
- **Rota estável no zoom:** linha, glow e marcadores são compensados por `scale / cameraScale` para manter o mesmo tamanho de tela durante o voo.
- **Mapa nítido:** snapshot de 1,75× (1890×3360), escala de overview no layout e rota projetada no mesmo quadrado.
- **Testes:** `StoryMotionTest` reescrito para overview/mergulho/follow/heading/cards/pullback/teto de escala; `StoryPerspectiveTest` ganhou o caso do horizonte. Goldens do story refeitos.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug` — sucesso.
- 714 testes, 0 falhas (27 `core:model`, 279 `core:domain`, 89 `core:data`, 319 `:app`).
- Cobertura `core:domain`: 98,17% de linhas (2.622/2.671), 83,16% de branches (2.049/2.464), 96,90% de instruções (19.475/20.099).
- Lint: 0 erros; 23 avisos não bloqueantes.
- Goldens atualizados: `story-frame-dark`, `story-frame-logo-dark`, `story-map-dark`, `story-card-verdict-dark`, `story-final-dark`, `story-final-map-dark` e `story-final-verdict-dark`.

### Decisões e observações

- **Rotação limitada é intencional:** sem limite, o retângulo do snapshot gira para fora do frame e abre buracos pretos. Com 22° e 1,72× a cobertura é segura e o movimento continua evidente.
- **Zoom alto exige snapshot maior:** 1,75× mantém o mapa praticamente 1:1 no follow. Se o export no aparelho ficar lento, o primeiro ajuste é cair para 1,5×/1,55×.
- **Ainda não é terreno 3D:** a homografia inclina o plano do mapa; um flyover com relevo exigiria DEM e re-renderização do mapa, fora do escopo desta rodada.
- **Próximo passo:** gerar um vídeo real no aparelho e avaliar ritmo, legibilidade dos labels e tempo de export.

## Sessão 2026-11-03 — LTHR manual, calibrar por corrida e câmera do story

### Concluído

- **`CalibrationCandidateDetector`** (`core:domain`): aceita apenas corrida/esteira recente (≤ 8 semanas), de 25–35 min, ≥ 1 km, FC média presente, ≥ 10 amostras de FC cobrindo ≥ 60% dos samples, variação p10–p90 ≤ 30 bpm e sem pausa > 2 min. Fora disso, nenhuma oferta de calibração.
- **LTHR manual no perfil:** campo “FC no limite” no passo Rotina, validado contra 100–230 bpm, FC de repouso e FC máxima observada. Editar o perfil preserva VDOT/pace de limiar.
- **“Usar esta corrida para calibrar”:** card no detalhe da atividade quando o detector aprova; abre a calibração com distância, tempo, data e FC preenchidos e um aviso para confirmar que foi esforço máximo e contínuo. Nada é calculado nem salvo sem o toque em Calcular/Salvar.
- **Navegação:** rota `profile/calibration?activityId={activityId}`; o perfil continua abrindo a calibração sem atividade.
- **Câmera do story:** `StoryMotion` ganhou foco de zoom por card (+0,04), `pitch` de 0 → 4° → 0 e `yaw` de 0 → 2° → 0 → −2° → 0. `StoryPerspective` aplica uma homografia rasa ao mapa **e** à rota com a mesma matriz; `MAX_CAMERA_SCALE` subiu para 1,16 e o layout reserva esse push.
- **Testes:** 11 no detector, 2 no `OnboardingValidator`, 1 no `OnboardingScreen`, 2 no `CalibrationViewModel`, 2 no `ActivityDetailViewModel`, 2 no `ActivityDetailScreen`, 1 no `CalibrationScreen`, 2 no `StoryMotion` e 3 no `StoryPerspective`.

### Evidências

- `./gradlew check` — sucesso (inclui testes, spotless, koverVerify, lint e parser FIT).
- 709 testes, 0 falhas (27 `core:model`, 279 `core:domain`, 89 `core:data`, 314 `:app`).
- Cobertura `core:domain`: 98,17% de linhas (2.622/2.671), 83,16% de branches (2.049/2.464), 96,90% de instruções (19.475/20.099).
- Lint: 0 erros; 23 avisos não bloqueantes; nenhum no código novo.
- Goldens atualizados: `onboarding-routine-dark`, `profile-dark`, `profile-light`, `story-frame-dark`, `story-frame-logo-dark`, `story-map-dark`, `story-card-verdict-dark`, `story-final-verdict-dark`, `story-final-dark` e `story-final-map-dark`.
- `./gradlew :app:verifyRoborazziDebug` — sucesso.

### Decisões e observações

- **LTHR manual não é inferência:** o valor é do usuário, então não recebe `ruleId`; as recomendações continuam citando `R-CAL-01`/`R-CAL-02` quando usam a calibração.
- **Corrida elegível ainda pede confirmação:** o detector reduz o risco, mas não consegue provar que o esforço foi máximo. O app nunca grava um LTHR silenciosamente.
- **Câmera é uma aproximação consciente:** a homografia inclina o snapshot inteiro; um pitch real de MapLibre exigiria re-renderizar o mapa por frame e uma matriz de projeção própria. O alinhamento rota/ruas foi preservado e o limite está em `docs/STORY_V2.md` §12.
- **Próximo passo:** validar em aparelho real o fluxo de calibração por corrida e o movimento da câmera; se o tilt não convencer, avaliar `MapSnapshotter` com pitch na Fase D.

## Sessão 2026-11-03 — Story v2 (Fase B): veredito do coach e rota por zona

### Concluído

- **`StoryVerdictCalculator`** (`core:domain`): transforma sessão planejada + zonas de FC em manchete, prova (`% no alvo`), frase do coach, selos por km e metadados de auditoria. Alvos: fácil Z2, longão Z1–Z2, qualidade Z4–Z5; 70% do tempo medido dentro da zona conta como “no alvo”.
- **Fallbacks honestos e voltados ao público do story:** sem plano, sem LHTR ou sem FC, o veredito fica nulo e o encerramento usa “Corrida feita” + “Cada corrida conta.”, sem expor plano, calibração ou jargão interno.
- **Card por km:** selo `NO ALVO`/`FORTE DEMAIS`/`LEVE DEMAIS`, barra de splits com o km atual aceso e contador de ritmo/FC/kcal nos primeiros 0,45 s.
- **Frame final:** `TREINO CONCLUÍDO` vira overline, a manchete do veredito ganha destaque, o percentual entra abaixo e a frase do coach fecha com o próximo treino da semana.
- **Rota por zona:** `StoryZoneRun` pinta o trecho percorrido com as cores de Z1–Z5; sem LTHR, a rota continua no lima da marca.
- **Fluxo:** `ActivityDetailViewModel` carrega a sessão planejada da data local da atividade e a próxima sessão da semana antes de montar o story.
- **Testes:** 14 testes novos no domínio (alvo, acima, abaixo, longão, qualidade, descanso, sem plano, sem calibração, sem FC, selos e texto sem `ruleId`), 2 do planner, 2 do ViewModel e 2 goldens novos.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug koverVerify` — sucesso.
- 683 testes, 0 falhas (27 `core:model`, 268 `core:domain`, 89 `core:data`, 299 `:app`).
- Cobertura `core:domain`: 98,14% de linhas (2.590/2.639), 83,10% de branches (2.016/2.426), 96,89% de instruções (19.248/19.866).
- Goldens novos `story-card-verdict-dark.png` e `story-final-verdict-dark.png`; goldens finais existentes atualizados com o fallback “Corrida feita”.

### Decisões e observações

- **Veredito é leitura, não nota:** o selo descreve o esforço do dia (“forte demais hoje”), nunca culpa ou “errado” (`PRODUCT.md` §5.5).
- **70% é o piso:** abaixo disso, o lado com mais tempo decide o tom; empate escolhe a leitura mais gentil.
- **A próxima sessão só vira “amanhã” quando é o dia seguinte:** caso contrário, “Próximo”.
- **Sem dados, sem selo:** ausência de plano/calibração/FC reduz o veredito a esforço, sem inventar alvo.

## Sessão 2026-11-03 — Story v2 (Fase A): área segura, câmera e film look

### Concluído

- **Decisão de tom:** veredito de treinador com o número como prova — o selo grande usa “Fácil bem feito” e o percentual entra menor. Registrado em `docs/STORY_V2.md` §9.
- **Área segura do Instagram:** `StoryLayout` centraliza a geometria 9:16 e reserva 13% no topo e na base. Logo no topo, métricas em linha, atribuição OSM com fundo próprio, card até 84% da altura e assinatura “Seu treino, com contexto.” no frame final.
- **Câmera:** `StoryMotion` aplica zoom (1,08→1,13), rotação (0→2°→0) e deriva mínima ao mapa **e** à rota com a mesma matriz; o alinhamento às ruas continua exato. O fator fixo de 1,2× do mapa foi substituído pela moldura da rota.
- **Nitidez:** snapshot do mapa a 1,15× (1242×2208) desenhado reduzido; push e rotação sem borrar labels.
- **Film look:** grade de cor, grão determinístico, vinheta e sombra nas métricas para leitura sobre o mapa claro.
- **Formato e capa:** 30 fps, 10 Mbps, capa PNG 1080×1920 em `Pictures/MeuCoach` e mensagem “Vídeo e capa salvos na galeria”.
- **Performance:** buffer de pixels do encoder reutilizado (antes ~8 MB por frame) e grão aplicado pelo exporter (`StoryFilm`), fora dos goldens.
- **Testes:** `StoryLayoutTest`, `StoryMotionTest`, `StoryFilmTest`, câmera e goldens do story atualizados; teste de UI para a capa.

### Evidências

- `./gradlew spotlessCheck :app:testDebugUnitTest` — sucesso.
- Goldens `story-frame-dark`, `story-frame-logo-dark`, `story-map-dark`, `story-final-dark` e `story-final-map-dark` atualizados.

### Decisões e observações

- **A marca não pode morar no rodapé:** o rodapé é da UI do Instagram. O logo foi para o topo e a assinatura do encerramento foi para dentro do card.
- **A câmera move mapa e rota juntos:** qualquer transformação aplicada só à rota quebraria a correspondência com as ruas.
- **O card cobre a cauda da rota:** é intencional; o painel opaco evita que a linha cruze os números e mantém a leitura limpa.
- **Fase B é a que muda o patamar:** veredito por km, rota colorida por zona e HUD cinético ainda não estão implementados.

## Sessão 2026-11-03 — Perfil → Saúde e dados: explicar o Health Connect

### Concluído

- **Perfil:** a seção “Dados” virou “Saúde e dados”. O card “Alimente o coach com seu descanso” diz o benefício (prontidão e treino do dia mais certos), mostra o Health Connect com um pill de estado real (`Conectado · N dias`, `Ainda não conectado`, `Indisponível neste aparelho`) e explica em uma frase o que é o app de saúde do Android. O backup (“Exportar ou apagar dados”) saiu do mesmo bloco e ganhou um card próprio, “Seus dados, sob seu controle”.
- **Estado real no perfil:** `ProfileViewModel` combina perfil, métricas diárias e status do Health Connect (`HealthDailySource` + `HealthSyncStore`), com refresh no `onResume`; o perfil passa a provar que o coach já usa os dados, em vez de só anunciar a integração.
- **Tela de dados:** “Métricas diárias” virou “Seus dados de saúde”, com o Health Connect em primeiro lugar, um bloco “O que isso muda no treino” (prontidão, risco de exagero e um exemplo com os mesmos guardrails do motor), o CSV como alternativa explícita (“Sem Health Connect? Use um CSV”) e o diagnóstico renomeado para “O que já está salvo”.
- **Quando o Health Connect não está disponível:** a tela oferece “Instalar o Health Connect” (Play Store, com fallback web) e diz que, no Android 14+, ele já vem no sistema.
- **Vocabulário único:** `healthConnectStatusLabel` e `healthConnectLastSyncLabel` (em `ui/common`) são compartilhados pelo perfil e pela tela de dados; “variabilidade cardíaca (HRV)” e “oxigenação (SpO₂)” aparecem por extenso na primeira menção.
- **Testes:** 6 novos testes do vocabulário e casos de UI para conectar, sincronizar, instalar e exibir dias salvos; goldens de perfil atualizados com o estado conectado.

### Evidências

- `./gradlew spotlessCheck :app:testDebugUnitTest` — sucesso.
- `:app:verifyRoborazziDebug` — goldens de perfil e dados verificados.

### Decisões e observações

- **Benefício antes do mecanismo:** o usuário não precisa saber o que é Health Connect para entender que sono e recuperação mudam o treino de hoje; a definição vem logo depois, curta e sem jargão.
- **Status é prova:** um pill com “47 dias” comunica mais do que “Gerenciar métricas”; números ausentes continuam ausentes (sem `0 dias`).
- **Backup não é saúde:** misturar importar métricas com apagar tudo fazia o usuário hesitar; agora são duas intenções separadas.
- **Sem permissão não é erro:** “Ainda não conectado” convida; “Indisponível neste aparelho” explica, e o CSV permanece como caminho válido.

## Sessão 2026-11-03 — Fase 2: plano, carga, métricas e perfil

### Concluído

- **Plano da semana:** cada sessão em uma linha (dia · título · duração); pill só para “Feito”/”Pulado”. O rationale aparece em duas linhas e ganha “Ver mais/Ver menos” quando passa do preview.
- **Carga vazia:** quando não há corrida nem carga (`ctl`/`atl`/`tsb` zerados e 0 km), o card mostra “Nenhuma corrida nesta semana” e o atalho para Tendências, em vez de 0,0 km + 0/0/0.
- **Métricas diárias:** subtítulo curto, Health Connect sem pill lateral, formato CSV atrás de “Ver formato do CSV” e “Escolher CSV” como secundário — sobra um único botão lima (Sincronizar).
- **Perfil:** card “Métricas diárias” com subtítulo “Sono, FC de repouso, HRV e SpO₂ · Health Connect ou CSV” e ação “Gerenciar métricas”.
- **Testes:** formato CSV colapsado, carga vazia e rationale expansível cobertos; goldens verificados.

### Evidências

- `./gradlew check spotlessCheck koverVerify :app:assembleDebug :app:verifyRoborazziDebug` — sucesso.
- 645 testes, 0 falhas (27 `core:model`, 254 `core:domain`, 89 `core:data`, 275 `:app`).
- Cobertura `core:domain` inalterada: 98,40% de linhas, 83,65% de branches.
- Lint sem erros.

### Decisões e observações

- **Um primário por tela:** o sync é a ação lima; o CSV é a alternativa secundária.
- **Formato não é conteúdo de primeira tela:** o cabeçalho técnico do CSV fica sob demanda, mas continua acessível.
- **Zero não é estado vazio:** 0 km e 0/0/0 liam como fracasso; a mensagem explica que falta uma corrida, não que a carga é zero.
- **Rationale recolhível:** duas linhas preservam o resumo; “Ver mais” evita esconder a progressão e o deload.

### Próximo passo

1. Reimportar uma corrida e revisar os cards de Tendências e do detalhe da atividade com dados reais.
2. `P1-12`: validação E2E com 4 semanas de uso real.
3. Fase 3 do plano de apresentação: `ROADMAP.md` U-01 a U-04 (termos, check-in em uma linha, confiança do plano e revisão com corrida real).

## Sessão 2026-11-03 — Hoje: cards compactos e prontidão explicada

### Concluído

- **Sinais de prontidão sem quebra no meio da palavra:** cada sinal agora usa duas linhas (rótulo e valor + hint), com `maxLines`/ellipsis; “Variabilidade cardíaca / acima” deixou de virar “aci/ma” em 360 dp.
- **Menos ruído:** o hint “ok” do sono saiu; HRV mostra “média · 7 noites”; FC de repouso mostra “estável” quando o delta é zero (fim do “-0 bpm”) e “média mensal” quando há variação.
- **Sheet por contexto:** “Por que esta prontidão?” abre um rationale próprio, construído a partir de sono, HRV, FC de repouso e check-in; “Por que este treino?” continua com o rationale da recomendação.
- **Catálogo:** `R-READY-01`, `EV-PLEWS-2013`, `EV-BUCHHEIT-2014` e `EV-WALSH-2021` entraram no `ExplanationCatalog`, mantendo a auditoria.
- **Plano da semana:** a pill de tipo repetia o título (“Longão” ao lado de “Longão”); agora só aparece para sessões concluídas ou puladas (“Feito”/“Pulado”).
- **Golden novo:** `today-narrow-dark.png` em 360 dp, além de `today-dark`/`today-light`, para travar a regressão de largura.
- **Testes:** +4 em `:app` (título do sheet, rationale da prontidão, delta zero e pill única) e catálogo atualizado no domínio.

### Evidências

- `./gradlew check spotlessCheck koverVerify :app:assembleDebug :app:verifyRoborazziDebug` — sucesso.
- 644 testes, 0 falhas (27 `core:model`, 254 `core:domain`, 89 `core:data`, 274 `:app`).
- Cobertura `core:domain`: 98,40% de linhas, 83,65% de branches.
- Lint sem erros.

### Decisões e observações

- **Duas linhas por sinal:** o valor à direita espremia o rótulo em 360 dp; empilhar rótulo + valor/hint preserva a leitura e mantém a altura parecida.
- **“Estável” em vez de “0 bpm”:** zero é um delta válido, mas “estável” comunica melhor e evita o sinal negativo.
- **Sheet da prontidão é determinístico:** os textos vêm dos mesmos componentes do motor, sem LLM; regras e evidências continuam sob demanda.
- **Plano ainda tem rationale de 3 linhas:** reduzir isso e o card de carga vazio ficam para a Fase 2 do plano de apresentação.

### Próximo passo

1. Fase 2 do plano: compactar o plano da semana, o card de carga vazio e a tela de Métricas (um primário por tela, CSV colapsável).
2. Reimportar uma corrida e revisar os cards de Tendências e do detalhe da atividade com dados reais.
3. `P1-12`: validação E2E com 4 semanas de uso real.

## Sessão 2026-11-03 — Apagar dados zera a janela do Health Connect

### Concluído

- **Bug:** “Exportar ou apagar dados” apagava o banco, mas não a marca d'água do sync do Health Connect (`meucoach_health_sync`). Um perfil novo herdava a data do sync anterior e lia só o intervalo incremental (2 dias), em vez dos 30 dias iniciais.
- **`HealthSyncStore.reset()`** remove a data do último sync; `UserDataRepository.deleteAllData` chama o reset depois da transação.
- **Injeção:** `UserDataRepository` recebe o `HealthSyncStore` (mesmo singleton do `HealthSyncService`), garantindo que a tela e o worker usem a mesma marca.
- **Recuperação no aparelho:** com a marca antiga removida, o sync voltou a importar 30 dias (05/10 a 03/11), com 29 dias de sono, 30 de FC de repouso, 28 de HRV e 28 de SpO₂.
- **Testes:** `UserDataRepositoryTest` cobre o reset; constructores de `ActivityRepositoryTest` e `DataManagementViewModelTest` atualizados.

### Evidências

- `./gradlew check spotlessCheck koverVerify :app:assembleDebug` — sucesso.
- 640 testes, 0 falhas (27 `core:model`, 254 `core:domain`, 89 `core:data`, 270 `:app`).
- Cobertura `core:domain` inalterada: 98,39% de linhas, 83,65% de branches.
- No aparelho: “30 dias importados do Health Connect” e “30 dias salvos · última em 03/11/2026”.

### Decisões e observações

- **Reset fora da transação:** o `SharedPreferences` não participa da transação do Room; o reset acontece só depois de a exclusão do banco concluir.
- **Impacto:** apagar os dados e criar um perfil novo volta a ler a janela completa; apagar apenas um dia (`DailyMetricsRepository.delete`) não altera a marca.
- **Dias recentes incompletos são esperados:** o Health Connect ainda pode não ter sono/HRV/SpO₂ do dia corrente; ausência continua `null`.
- **Treinos não foram reimportados:** não havia `import_jobs`, arquivos em `files/imports` nem atividade após o reset. Se uma importação de FIT/TCX/GPX falhar, o próximo passo é capturar a mensagem da tela e o log do `ImportWorker`.

### Próximo passo

1. Reimportar um FIT/TCX/GPX e confirmar que a atividade volta à lista.
2. `P1-12`: validação E2E com 4 semanas de uso real.

## Sessão 2026-11-03 — Mescla de métricas diárias por fonte

### Concluído

- **`DailyMetricsMerger`** (puro, `core:domain`) mescla um dia existente com um dia recebido campo a campo.
- **Precedência:** `MANUAL` > `HEALTH_CONNECT` > `CSV`; fonte de menor precedência só preenche campos nulos e nunca apaga valores. Mesma fonte atualiza o dia, para que reimportar corrija.
- **`DailyMetricsRepository.saveAll`** lê a linha existente dentro da transação e grava a mescla, preservando `createdAt`.
- **`source`:** guarda a fonte dominante do dia. Proveniência por campo fica para um ADR futuro (exigiria schema v10).
- **Docs:** `ADR-0016`, índice de decisões, `DATA_MODEL.md` §3.5/§6 e `ROADMAP.md` (P1-09); corrigidos também dois itens desatualizados do roadmap (histórico de conclusão e “check-in com streak”, que contrariava o princípio sem culpa).
- **Testes:** 8 do merger no domínio, 2 no repositório e 1 de integração no `HealthSyncService`.

### Evidências

- `./gradlew check spotlessCheck koverVerify :app:assembleDebug` — sucesso.
- 639 testes, 0 falhas (27 `core:model`, 254 `core:domain`, 88 `core:data`, 270 `:app`).
- Cobertura `core:domain`: 98,39% de linhas, 83,65% de branches.
- Lint sem erros.

### Decisões e observações

- **Health Connect continua vencendo o CSV** em campos compartilhados, como o `DATA_MODEL.md` §6 já pedia; o CSV entra como fallback nos campos que o dia do Health Connect não trouxe.
- **Valores manuais são intocáveis** por fontes automáticas.
- **Sem mudança de schema:** a mescla acontece na leitura/gravação; nenhuma migração nova.
- **Custo:** uma leitura extra por dia dentro da transação; lotes de sync são de no máximo 30 dias.
- **Limite conhecido:** quando o dia mistura fontes, `source` registra só a dominante. Nenhuma tela exibe esse campo hoje.

### Próximo passo

1. Validar no aparelho um dia que receba CSV e sync do Health Connect.
2. `P1-12`: validação E2E com 4 semanas de uso real.
3. Se a proveniência por campo for necessária, abrir ADR com migração dedicada.

## Sessão 2026-11-03 — Apagar uma atividade pelo detalhe

### Concluído

- **Menu no detalhe:** o cabeçalho da atividade ganhou `Mais opções` → `Apagar atividade`, em coral de perigo.
- **Confirmação explícita:** dialog “Apagar esta atividade?” explica que ela sai do histórico, das tendências e do cálculo de carga, e que a ação não pode ser desfeita.
- **Soft delete:** `ActivityDetailViewModel.deleteActivity()` usa `ActivityRepository.deleteActivity` (marca `deletedAt`), mantendo a política do Room e permitindo restaurar ao reimportar o mesmo arquivo.
- **Feedback:** ao concluir, o detalhe volta para a lista; falha mostra um dialog “Não foi possível apagar”. Lista, Hoje e Tendências reagem sozinhos porque observam os Flows do repositório.
- **Testes:** `ActivityDetailViewModelTest` cobre a exclusão e o sumiço em `openActivity`; `ActivityDetailScreenTest` cobre menu, confirmação e cancelamento implícito. Goldens do detalhe atualizados com o novo menu.

### Evidências

- `./gradlew check koverVerify :app:assembleDebug` — sucesso.
- `./gradlew :app:recordRoborazziDebug :app:verifyRoborazziDebug` — sucesso.
- 628 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 85 `core:data`, 270 `:app`).

### Decisões e observações

- **Soft delete em vez de apagar em cascata:** consistente com `ActivityRepository` e com a importação (arquivo repetido pode restaurar). O export continua incluindo itens com `deletedAt`.
- **Sem “desfazer” na UI:** a confirmação é o único ponto de segurança; para recuperar, é preciso reimportar o arquivo original.
- **Ação no detalhe, não na lista:** evita exclusão acidental e mantém o menu de longo prazo para um gesto de long-press, se necessário.

### Próximo passo

1. Validar no aparelho o menu, o dialog e o retorno à lista.
2. Se a exclusão for frequente na lista, avaliar long-press com a mesma confirmação.

## Sessão 2026-11-03 — Comunicação: menos jargão, referência sob demanda

### Concluído

- **Referências científicas fora do fluxo principal:** o sheet “Por que este treino?” agora mostra `O que usei`, `O que faltou` e `Como decidi` (só títulos legíveis). Fontes e IDs brutos ficaram atrás de `Ver fontes e detalhes técnicos`, colapsado. `ActivityInsights`, `CalibrationScreen` e `TrendsScreen` não exibem mais `R-...`/`EV-...`.
- **Jargão traduzido:** `ACWR` → `Aumento`/`vs. mês`; `LTHR` → `FC no limite`; `VDOT` → `Aptidão`; `RPE` → `esforço percebido`; `Taper` → `semana de prova`; `Deload` → `semana de descarga`; `TT` → `teste de 30 min`; `Qualidade` → `treino forte`; `Pace` → `ritmo`; `Split` → `trecho`; `Hooper` → `Recuperação`; `SWC`/`baseline` → `acima`/`abaixo da média`.
- **Zonas com contexto:** `Z2` virou `Z2 · leve` na pill do treino; o detalhe da atividade mostra `Z1 · Muito leve` … `Z5 · Máximo`; as legendas de 80/20 mantêm `Fácil · Z1–Z2`.
- **Rationales do motor:** textos de prescrição e plano reescritos em linguagem de treinador (calor, carga, taper/deload, sono curto e treino forte).
- **Erros de borda:** falha de importação, séries temporais corrompidas e leitura do Health Connect deixaram de vazar mensagem técnica em inglês; agora dizem o que aconteceu e o que fazer.
- **Fim do texto de demonstração:** o rodapé “Fase 0 · dados de demonstração” saiu da tela Hoje. “Ver tendências” e o card da última corrida agora navegam de verdade (Tendências e detalhe da atividade), em vez de mostrar um toast de fase.
- **Testes:** `TodayPlanTest` cobre a divulgação progressiva (ID ausente antes do toggle, presente depois); `ActivityDetailScreenTest` e `CalibrationScreenTest` garantem ausência de IDs; goldens de Today, Trends, Atividade, Calibração, Check-in e Onboarding atualizados.

### Evidências

- `./gradlew :core:model:test :core:domain:test :core:data:test :app:testDebugUnitTest` — sucesso.
- `./gradlew spotlessCheck` — sucesso.
- `./gradlew :app:recordRoborazziDebug :app:verifyRoborazziDebug` — sucesso.
- 626 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 85 `core:data`, 268 `:app`).

### Decisões e observações

- **Auditoria preservada:** `ruleId`, `evidenceIds` e `confidence` continuam no domínio e no Room; a UI só os mostra sob demanda. Isso mantém `ADR-0004` e `PRODUCT.md` §1 sem entregar bibliografia para quem só quer treinar.
- **Confiança continua visível:** `McConfidenceBadge` permanece no card de prontidão e no plano; o que saiu foi o ID, não o nível de confiança.
- **Fonte única de explicação:** só o sheet de recomendação oferece detalhes técnicos; evita repetir referência em cinco telas.
- **Copy do domínio é copy de usuário:** rationales de `WorkoutRecommender`/`WeeklyPlanGenerator` alimentam Hoje, plano e notificação, então também passaram pela varredura.

### Próximo passo

1. Validar a compreensão com o usuário 0 (`TESTING.md` §7, meta ≥ 80%) e ajustar termos que ainda exijam explicação.
2. Verificar no aparelho o sheet com a divulgação progressiva e o novo detalhe de zonas.

## Sessão 2026-11-03 — Story: mapa vetorial em 1080p

### Concluído

- **Saída do vídeo:** 720×1280 → **1080×1920** (H.264, 24 fps, 8 Mbps), mantendo o layout 9:16 escalável.
- **Mapa vetorial:** troca do raster OSM pelo **OpenFreeMap** (`styles/liberty`, dados OSM) — sem chave, sem limite de visualizações. O `OpenFreeMapStyle` amplia e clareia os labels de lugar/rua/água, para dar para reconhecer o bairro/região em 1080p.
- **Fallback:** se o style do OpenFreeMap não baixar, o renderer usa o style raster OSM inline; o mapa claro mantém o mesmo casing da rota.
- **Contraste:** saturação 85% (antes 60%), sem scrim na animação e 80 no frame final; gradientes de topo/rodapé continuam protegendo o texto. A rota ganhou **casing escuro** e base cinza para o lima ficar legível sobre o mapa claro.
- **Timeout** do mapa: 30 s → 45 s (style + tiles vetoriais + glyphs).
- **Testes:** `OpenFreeMapStyleTest` cobre tamanho/cor dos labels, criação de `paint` e preservação de camadas não simbólicas.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug koverVerify` — sucesso.
- 625 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 85 `core:data`, 267 `:app`).
- Lint sem erros; APK de debug inalterado (~49,7 MB; o mapa é download em runtime).
- Saída do vídeo: ~30 MB para 30 s a 8 Mbps (antes ~15 MB a 4 Mbps).

### Decisões e observações

- **OpenFreeMap em vez de raster:** tiles vetoriais são nítidos em qualquer resolução e os labels podem ser ampliados, o que resolve o “não dá para distinguir a região”. O provedor é gratuito, sem chave e sem limite de requests; os dados continuam sendo OSM.
- **Custo aceito:** 1080p processa ~2,25× mais pixels por frame; o export fica mais lento e o arquivo maior, sem impacto no APK.
- **Fallback raster** é claro e também funciona com o casing; a atribuição `© OpenStreetMap contributors` cobre os dois.
- **Validação pendente no aparelho:** conferir labels reais, tempo de export e nitidez do mapa.

### Próximo passo

1. Instalar este APK e gerar um story real; avaliar o mapa vetorial e o tempo de export.
2. Se ainda faltar detalhe, aumentar o zoom (`MAP_ZOOM_FACTOR`) ou reduzir a área de labels.

## Sessão 2026-11-03 — Ajustes de UX: data, seletores, última corrida e mapa

### Concluído

- **Data de nascimento/prova:** o estado guarda só os 8 dígitos e `DateVisualTransformation` insere as barras apenas na tela (`DateMask` com `OffsetMapping`). Digitar `01111988` produz `01/11/1988`; o cursor acompanha os dígitos e apagar sobre a barra não trava mais.
- **Fuso horário e cidade:** os chips foram substituídos por campos de seleção compactos que abrem um **bottom sheet pesquisável** (busca sem acento), com lista de 56 dp, check na opção atual e “Não informar” para a cidade. O perfil mostra o rótulo amigável em vez do ID IANA.
- **Dados compartilhados:** `TimezoneOption`, `AVAILABLE_TIMEZONES`, `AVAILABLE_CITIES` e `timezoneLabel` foram para `ui/common/LocationOptions.kt`, usados pelo onboarding e pelo perfil (sem dependência entre features).
- **“Última corrida”:** o card só aparece quando existe atividade real. `TodayUiState.recentActivity` virou nullable e o fallback para os dados de demonstração foi removido — perfil novo não mostra mais corrida inventada.
- **Mapa do story:** enquadramento 20% mais próximo (`MAP_ZOOM_FACTOR = 1.2`), saturação de 60% e scrim leve (alpha 50 na animação, 90 no final), com gradientes no topo/rodapé para preservar a leitura do cabeçalho. A rota é ampliada pelo mesmo fator, mantendo o alinhamento com as ruas.
- **Testes:** digitação da data, seleção de fuso pela busca, ausência de última corrida, mapeamento do cursor e atualização dos goldens de onboarding/perfil/story com mapa.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug koverVerify` — sucesso.
- 622 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 85 `core:data`, 264 `:app`).
- Cobertura `core:domain` inalterada.

### Decisões e observações

- **Barras fora do valor:** manter a máscara no estado era a causa do cursor pulando e do backspace preso; o campo agora edita dígitos e a transformação só desenha.
- **Lista em vez de chips:** 15 fusos e 19 cidades em `FlowRow` poluíam a rotina; o sheet reduz a tela a dois campos e ainda permite buscar.
- **“Não informar”:** a cidade é opcional; antes não havia como limpar uma cidade escolhida.
- **Mapa menos apagado:** o cabeçalho ganhou um gradiente próprio em vez de escurecer o mapa inteiro; o centro do mapa e a rota ficam visíveis.
- **Dados de demonstração** continuam existindo só para previews/testes, nunca para o estado real.

### Próximo passo

1. Reinstalar o APK no aparelho e validar a digitação da data, os seletores e o story com o novo enquadramento.
2. Validação E2E com 4 semanas de uso real (P1-12).

## Sessão 2026-11-03 — P3-05 (entrega 2): mapa real no story

### Concluído

- **`MapLibre` 12.3.1 + OSM raster** (estilo inline, sem chave de API): `MapLibreBackgroundRenderer` gera um snapshot 720×1280 via `MapSnapshotter`; a linha 13.x foi descartada porque exige Vulkan (`uses-feature` obrigatório).
- **`StoryMapCameraCalculator`** (puro) alinha o `BoundingBox` ao mesmo quadrado usado pela rota, inclusive o offset vertical do cabeçalho. 5 testes cobrem centro, encaixe, zoom e geometria de preview.
- **Fluxo:** `createStory` baixa o mapa uma vez, mostra `Baixando o mapa do OpenStreetMap…` e guarda a `Bitmap` no estado `Preview`; o export reaproveita a imagem. Sem rede ou após 30 s, o story mostra erro explícito e não gera vídeo.
- **Renderer:** mapa full-bleed dessaturado a 60%, com scrim leve (alpha 50 na animação, 90 no final) e gradientes no topo/rodapé; zoom de 1,2× sobre o quadrado da rota para o mapa não parecer distante; rota base clara e atribuição `© OpenStreetMap contributors` em todo frame com mapa.
- **Manifest:** `INTERNET`/`ACCESS_NETWORK_STATE`; permissões de localização herdadas do MapLibre removidas com `tools:node="remove"`.
- **APK:** `abiFilters` em `arm64-v8a`/`x86_64`; o debug passou de ~24 MB para ~49 MB (~13 MB por ABI).
- **UI:** prévia agora mostra o mapa real; o texto do card explica que tiles do OSM são baixados e que coordenadas exatas não saem do aparelho.
- **Ícone do launcher:** o asset `design/icon/icon_mc.jpg` virou foreground adaptativo (PNGs por densidade sobre o fundo `#08090B`), substituindo o vetor “M” provisório; o `monochrome` usa a mesma silhueta.
- **Testes:** 5 da câmera, 2 novos do ViewModel (mapa no preview e falha de rede) e 2 goldens sintéticos com grade de ruas (`story-map-dark.png`, `story-final-map-dark.png`), sem dados reais.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug koverVerify` — sucesso.
- 618 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 85 `core:data`, 260 `:app`).
- Cobertura `core:domain` inalterada (98,37% de linhas).
- APK debug: ~49 MB com duas ABIs (antes ~24 MB).

### Decisões e observações

- **Versão:** `12.3.1` é a última linha OpenGL; `13.x` adiciona Vulkan obrigatório e reduziria a compatibilidade de aparelhos.
- **Tamanho acima do previsto:** o ADR estimava 3–5 MB; a biblioteca adiciona ~13 MB por ABI. Os filtros de ABI limitam o impacto, e 32 bits fica fora do MVP.
- **Mapa na prévia, não no export:** evita baixar tiles duas vezes e permite ver o enquadramento antes de gerar o vídeo.
- **Sem fallback offline silencioso:** o `ADR-0012` prometia vídeo 100% offline; o `ADR-0015` substitui isso por rede obrigatória, com erro claro. Timeout de 30 s evita travar a tela.
- **Atribuição OSM** é requisito de licença, não decoração; permanece visível mesmo com a logo no rodapé.
- **Pendente de validação real:** geração do snapshot, zoom/qualidade do OSM, alinhamento da rota, tempo de export e APK de release. O ponto de partida em emoji (§2.5) também segue pendente.

### Próximo passo

1. Validar o story com mapa em aparelho real (rede, enquadramento, export e tamanho do APK).
2. Validação E2E com 4 semanas de uso real (P1-12).

## Sessão 2026-11-03 — P3-05 (entrega 1): frame final de 3 s e bounding box

### Concluído

- **`StorySegmentType.FINAL`** encerra o vídeo com 3 s reservados **dentro** da duração alvo: com 30 s, a rota e os cards ocupam os 27 s iniciais. Alvos menores que 3 s geram só o encerramento.
- O encerramento também aparece em treinos sem km completo (sem cards), para o vídeo sempre terminar com o resumo.
- **`StoryFrameRenderer.drawFinal`** desenha o painel escuro com `TREINO CONCLUÍDO`, ritmo médio, FC média e calorias. Distância e tempo continuam no cabeçalho (sem salto de layout na transição) e a logo `MeuCoach` permanece no rodapé.
- **`StoryPlan`** ganhou `averagePace`, `averageHeartRate` e `boundingBox`. Pace e FC preferem os campos canônicos de `ImportedActivity`; quando ausentes, derivam de `durationSec / distância` e da média dos samples. Sem dado, o frame mostra `—`.
- **`BoundingBox`** calculado com todos os pontos GPS (não só a rota amostrada) para o mapa da entrega 2 não cortar extremos do traçado.
- Novo golden `story-final-dark.png` e 6 testes novos do planner (frame final, duração mínima, valores canônicos, ausência de FC e bounding box).

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug koverVerify` — sucesso.
- 609 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 85 `core:data`, 251 `:app`).
- Cobertura `core:domain` inalterada (98,37% de linhas).
- Lint sem erros.

### Decisões e observações

- **Sem dependência nova nesta entrega:** só `Canvas`. `MapLibre` e a `Bitmap` estática do mapa ficam para a entrega 2, como combinado.
- **`FINAL` sempre presente:** o §2.6 do ADR condicionava o encerramento a `splits` não vazio; o vídeo com menos de 1 km também merece fechamento. Divergência registrada na nota de implementação do ADR.
- **Cabeçalho preservado no frame final:** evita que a transição para o resumo pareça um corte de tela; o painel cobre o restante dos dados.
- **Campos canônicos primeiro:** a FC média do arquivo é mais confiável que a média dos samples; a derivação por GPS cobre fontes que não trazem o resumo.
- O ponto de partida em emoji e o fundo escurecido sobre o mapa continuam pendentes na entrega 2.

### Próximo passo

1. `P3-05` entrega 2: `MapLibre` + `Bitmap` estática do mapa a partir do `BoundingBox` + rota animada por cima.
2. Validação E2E com 4 semanas de uso real (P1-12).

## Sessão 2026-11-03 — Hoje: carga honesta quando não há dados

### Concluído

- `TodayUiState.ctl/atl/tsb` e `weeklyLoadKm` agora são **nulos** quando não existe `LoadSnapshot` (antes caíam em 24/31/−7 e 17,9 km de demonstração).
- O card **Carga da semana** mostra "Sem carga ainda" + atalho para Tendências, sem barras, km ou métricas inventadas.
- Barras semanais só aparecem quando há km real em algum dia; a linha de CTL/ATL/TSB só quando há carga calculada.
- `todayState` deixou de reutilizar `days`/`weeklyLoadKm` do estado de demonstração.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug koverVerify` — sucesso.
- 602 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 85 `core:data`, 244 `:app`).
- Golden da tela Hoje passou a usar um `LoadSnapshot` fixo, exercitando o card completo.

### Decisões e observações

- **Ausência é `null`** (`AGENTS.md` §3.7): sem carga não existe fitness, fadiga ou balanço; mostrar zero também seria mentira.
- O estado de demonstração continua existindo para previews e testes, mas não vaza mais para o estado real.
- O atalho "Ver tendências" permanece no card vazio para o usuário entender onde o dado vai aparecer.

### Próximo passo

1. `P3-05`: mapa real (OSM + MapLibre) e frame final de 3 s (ADR 0015).
2. Validação E2E com 4 semanas de uso real (P1-12).

## Sessão 2026-11-03 — P1-11: notificação de importação concluída

### Concluído

- **`ImportNotifier`** posta o resultado do worker em um canal próprio (`meucoach-imports`, importância baixa, sem som/badge).
- **Conteúdo por resultado** em `ImportNotification` (puro): sucesso, treino restaurado, duplicado e falha, com nome do arquivo, substituição, avisos e motivo do erro.
- **Deep link:** sucesso/duplicado abrem o detalhe do treino (`meucoach://activity/<id>`); falha abre a lista (`meucoach://activities`). `NotificationNavigation` reconhece os dois.
- **Worker:** `ImportWorker` notifica depois do processamento; retries de I/O continuam sem notificar.
- **Um id por vez:** importações em lote mantêm o shade limpo e o último resultado vence.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug koverVerify` — sucesso.
- 600 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 85 `core:data`, 242 `:app`).
- Novos testes: `ImportNotificationTest`, `ImportNotifierTest` e casos de rota em `NotificationNavigationTest`.

### Decisões e observações

- **Canal separado dos lembretes:** o usuário pode silenciar importação sem perder o lembrete de treino.
- **Importância baixa:** importar é uma ação que o usuário pediu; não deve interromper nem tocar.
- **Permissão:** já era pedida no primeiro launch (`MainActivity`); sem ela, o notifier simplesmente não posta.
- **Falha abre a lista**, porque ainda não existe uma tela de detalhe do job com o motivo completo.

### Próximo passo

1. `P3-05`: mapa real (OSM + MapLibre) e frame final de 3 s (ADR 0015).
2. Revisar o card de carga da tela Hoje, que ainda usa números de demonstração quando não há carga.
3. Validação E2E com 4 semanas de uso real (P1-12).

## Sessão 2026-11-03 — T-06: exportar e apagar todos os dados

### Concluído

- **Tela Dados** (`Perfil → Exportar ou apagar dados`):
  - Exporta um JSON com perfil, corridas, check-ins, métricas diárias, planos e importações.
  - Usa o seletor de documentos do Android (`CreateDocument`), sem permissão de armazenamento.
  - Apaga perfil, corridas, check-ins, métricas e planos em uma única transação, com diálogo de confirmação.
  - Mostra contagem do que foi removido e deixa claro que Health Connect e arquivos originais não são alterados.
- **`UserDataRepository`** (`core:data`) centraliza export/delete de todas as tabelas; `ActivityRepository` voltou a cuidar só de atividades.
- **DAO:** `getAll` de check-ins, `getAllPlans`/`getAllSessions`/`deleteAllPlans`/`deleteAllSessions` de planos.
- **Design system:** `McDangerButton` (borda e texto `danger`, só para ação destrutiva).
- **Testes:**
  - `UserDataRepositoryTest` cobre export de todas as tabelas e delete completo.
  - `DataManagementViewModelTest` cobre escrita do arquivo, falha de escrita e resumo do delete.
  - `DataManagementScreenTest` cobre confirmação, cancelamento e estado ocupado.
  - Goldens novos: `data-management-dark.png` e `data-management-light.png`.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug koverVerify` — sucesso.
- 591 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 85 `core:data`, 233 `:app`).
- Cobertura `core:domain`: 98,37% de linhas e 83,55% de branches (inalterada).

### Decisões e observações

- **JSON, sem ZIP:** os arquivos brutos são apagados depois da importação (`StagedImportProcessor`), então não há o que empacotar; as amostras continuam representadas pela contagem.
- **Exporta também itens com soft delete** (`getAllIncludingDeleted`), para o backup refletir o histórico real.
- **Delete é transacional:** se uma tabela falhar, nada é apagado.
- **Golden da tela Hoje** dependia da hora do dia (saudação "Bom dia"/"Boa tarde") e quebrava a cada regravação; o teste agora fixa `now = 08:00`.
- Apagar o perfil devolve o app ao onboarding, como esperado.

### Próximo passo

1. `P3-05`: mapa real (OSM + MapLibre) e frame final de 3 s (ADR 0015).
2. `P1-11`: notificação de importação concluída.
3. Revisar o card de carga da tela Hoje, que ainda usa números de demonstração quando não há carga.

## Sessão 2026-11-02 — P1-13: linguagem simples, logo no story e carga honesta

### Concluído

- **Linguagem (Hoje e Tendências):**
  - `CTL`/`ATL`/`TSB` viraram `Acumulada` (42 dias), `Recente` (7 dias) e `Balanço` (recuperação), com o mesmo rótulo nas duas telas.
  - `PMC` virou "Evolução da carga · 42 dias"; a legenda do gráfico usa "Acumulada"/"Recente".
  - `ACWR` aparece como "Proporção" com hint `ACWR` e o texto explica que é sinal de atenção, nunca meta.
  - `Monotonia` virou "Variedade"; recordes viraram "Seus melhores trechos"; consistência virou "Hábito · Dias ativos".
- **Story (P3-05):**
  - `StoryBranding` centraliza a marca em `core:designsystem` (`drawable-nodpi/logo_mc.png`).
  - O rodapé desenha a logo centralizada, com proporção preservada (3,8:1), na prévia e no vídeo.
  - Sem logo, o rodapé de texto "Gerado no aparelho · MeuCoach" continua (ADR 0015 §2.8).
  - Removidos os `println` de depuração e o `getIdentifier` reflexivo.
- **Bug corrigido:** o pill "ACWR 1,1" da tela Hoje era **hardcoded**. Agora vem de `LoadSnapshot.acwr` e desaparece quando não há histórico.
- **Onboarding:**
  - Máscara `dd/mm/aaaa` nos campos de data (nascimento e prova).
  - Data da prova passou a ser opcional (sem data não há taper; o plano continua conservador).
  - Fuso e cidade por chips, com rótulos legíveis ("Recife", "Brasília · Sudeste") mantendo o ID IANA como valor.
- **Docs:** `COMMUNICATION.md`, `GLOSSARY.md`, seções 15–17 do `DESIGN_SYSTEM.md` (tabela Do/Don't restaurada), ADR 0015 registrado em `DECISIONS.md`.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug koverVerify` — sucesso.
- 580 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 84 `core:data`, 223 `:app`).
- Cobertura `core:domain`: 98,37% de linhas (2.416/2.456), 83,55% de branches (1.895/2.268).
- Dois goldens novos: `story-frame-logo-dark.png` e `onboarding-routine-dark.png`.
- Nove goldens regravados pelas mudanças de texto.

### Decisões e observações

- Rótulo curto + `hint` substituiu descrições longas: em `McStat`, texto longo quebrava no meio da palavra ("RECUPERAÇÃ/O") e desalinhava os valores.
- Dado ausente continua `null`: sem histórico de carga não existe pill de proporção, e não há número de reserva.
- A logo fica em `drawable-nodpi` e em uma única cópia recortada; isso evita a escala automática de densidade e 40 KB duplicados no APK.
- `summarizes plan completion` falhava **só às segundas**: duas sessões na mesma data colidem na chave `(weekStart, date)`. O teste passou a ancorar na semana anterior.
- Fuso e cidade seguem em lista brasileira fixa (MVP). Usuário fora do Brasil não conclui o onboarding; registrado como T-09 no backlog.
- O card de carga da tela Hoje ainda cai nos valores de demonstração quando não há carga (`ctl`/`atl`/`tsb`); revisar junto com o T-06.

### Próximo passo

1. Exportar e apagar todos os dados na UI (T-06, critério de saída do MVP).
2. `P3-05`: mapa real (OSM + MapLibre) e frame final de 3 s (ADR 0015).
3. `P1-11`: notificação de importação concluída.

## Sessão 2026-11-02 — P3-05: evolução do story com mapa real (`ADR-0015`)

### Concluído

- `ADR-0015` criado e aceito (`docs/adr/0015-story-video-map.md`): mapa real (`OSM` + `MapLibre`) como background estático, frame final (`FINAL`, 3 s) e branding `MeuCoach`.
- `docs/DECISIONS.md` atualizado (`ADR-0012` marcado como `Substituído parcialmente`).

### Decisões e observações

- **Fonte do mapa:** `OpenStreetMap` (`tile.openstreetmap.org`) — zero custo e usado pelo mercado (`Komoot`, `AllTrails`, `Strava`). O `bounding box` é calculado localmente; só tiles da região aproximada são baixados.
- **Biblioteca:** `org.maplibre.gl:android-sdk` (`BSD-2-Clause`, `~3–5 MB` no APK), justificada pela regra 8 (`AGENTS.md`). **Ainda não foi adicionada**: a evolução não está implementada.
- **Renderização:** `Bitmap` estática do mapa gerada uma vez, antes da exportação, usada como background em todos os frames; a rota continua animada por cima (`drawPath` + `PathMeasure`), preservando o desempenho já validado.
- **Frame final:** novo `StorySegmentType.FINAL` com `distância`, `tempo`, `pace médio`, `FC média`, `kcal` e `MeuCoach`.
- **Privacidade:** a exportação passa a exigir rede para os tiles `OSM`, mas não envia coordenadas exatas (aceito pelo usuário 0, `resposta 2`).
- **Validação:** o protótipo (`P3-05`) está validado no aparelho (`resposta 8`); a evolução do `ADR-0015` ainda precisa ser implementada e validada.

### Próximos passos

1. `BoundingBox` no `StoryPlan` e no `StoryPlanCalculator`.
2. `StoryMapRenderer` para gerar a `Bitmap` estática via `MapLibre`.
3. `StoryFrameRenderer`/`StoryVideoExporter` recebendo `mapBackground` e o segmento `FINAL`.
4. Dependência `org.maplibre.gl:android-sdk` em `gradle/libs.versions.toml` e `:app`.
5. Validar no aparelho real (mapa preciso, desempenho, frame final, branding).

## Sessão 2026-10-31 — P3-05: prévia do story antes do export

### Concluído

- `StoryExportUiState.Preview(plan)` separa preparação de exportação.
- `ActivityDetailViewModel`:
  - `createStory()` calcula o plano e mostra a prévia.
  - `exportStory(palette)` inicia o MediaCodec somente após confirmação.
  - `cancelStory()` volta ao estado inicial.
- UI:
  - Card mostra um frame 9:16 da prévia, com “Gerar vídeo” e “Cancelar”.
  - O frame é renderizado com as cores/fonte do app, no ponto do primeiro card.
- Testes:
  - ViewModel: atividade com GPS prepara prévia e cancelamento volta a Idle.
  - UI: prévia visível e “Gerar vídeo” aciona o callback.
- Screenshot do frame segue válido.

### Evidências

- `./gradlew check :app:verifyRoborazziDebug` — sucesso.
- 570 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 84 `core:data`, 213 `:app`).
- Cobertura `core:domain`: 98,37% linhas / 83,55% branches.

### Decisões e observações

- O usuário vê um frame representativo antes de esperar a codificação.
- A prévia usa 360×640 para ser leve; o vídeo final continua 720×1280.
- O export ainda exige validação em aparelho real; o preview não depende do MediaCodec.

### Próximo passo

1. Testar o protótipo em aparelho real (tempo, tamanho, qualidade).
2. Avaliar 1080×1920.
3. Seguir para `P1-11`/`P1-12`.

## Sessão 2026-10-31 — P3-05: protótipo de story de treino

### Concluído

- `StoryPlanCalculator` (app):
  - Projeta a rota com `routeData` e calcula distância acumulada por Haversine.
  - Deriva splits por km com pace, FC média e kcal proporcional.
  - Monta timeline de 30 s: movimento proporcional + pausa de 2 s por card, no máximo 8 cards.
- `StoryFrameRenderer`:
  - Frame 720×1280 com cabeçalho, rota base, trecho percorrido, marcador e card do km.
  - Usa as cores do design system via `McColors.toStoryPalette()`.
- `StoryVideoExporter`:
  - MediaCodec H.264 (`COLOR_FormatYUV420Flexible`) + MediaMuxer, 24 fps.
  - Converte ARGB para YUV420 por frame, sem EGL nem dependência nova.
  - Android 10+ salva em `Movies/MeuCoach` via MediaStore; anteriores compartilham via FileProvider.
- UI:
  - Card “Story do treino” no detalhe, só quando existem amostras com GPS.
  - Estados ocioso/gerando/concluído/erro, com compartilhar e tentar novamente.
  - Provider de arquivos e intent `ACTION_SEND` com `video/mp4`.
- Testes:
  - Planner: splits, timeline, caso sem GPS e caso sem km completo.
  - ViewModel: erro claro sem rota.
  - UI: card e compartilhamento.
  - Screenshot do frame do story em dark.

### Evidências

- `./gradlew check :app:verifyRoborazziDebug` — sucesso.
- 568 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 84 `core:data`, 211 `:app`).
- Cobertura `core:domain`: 98,37% linhas / 83,55% branches.
- `story-frame-dark.png` gerado e verificado no repositório.

### Decisões e observações

- É um **protótipo**: a codificação compila e a lógica está testada, mas a qualidade/desempenho do MediaCodec precisa ser validada em aparelho real.
- Sem dependência nova; se o encoder não expõe YUV flexível, a UI mostra erro em vez de quebrar.
- A rota usa apenas amostras sanitizadas; o vídeo é gerado e compartilhado localmente.
- Próximo incremento: preview antes de exportar e, se necessário, 1080×1920.

### Próximo passo

1. Validar o protótipo em aparelho real e ajustar bitrate/tamanho.
2. Adicionar preview do story antes do export.
3. Seguir para `P1-11`/`P1-12`.

## Sessão 2026-10-31 — P1-09: sync incremental do Health Connect

### Concluído

- `HealthSyncService` compartilhado:
  - Primeira execução lê 30 dias; depois parte do último sucesso menos 1 dia.
  - Sucesso salva a data; falta de permissão, erro e indisponibilidade não avançam.
- `HealthSyncStore` em `SharedPreferences` guarda apenas a última data de sync.
- `HealthConnectViewModel` passou a usar o serviço, com mensagem de importação
  inicial vs. atualização incremental.
- `HealthSyncWorker` (`@HiltWorker`) roda diariamente via WorkManager, com
  atraso inicial de 30 min, backoff exponencial e tag própria.
- `MeuCoachApplication` agenda o worker de forma best-effort.
- Testes:
  - Serviço: janela inicial, incremental, permissões, erro e indisponibilidade.
  - ViewModel: sync manual via serviço.
  - Scheduler: work único, tag e estado enqueued.

### Evidências

- `./gradlew check :app:verifyRoborazziDebug` — sucesso.
- 562 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 84 `core:data`, 205 `:app`).
- Cobertura `core:domain`: 98,37% linhas / 83,55% branches.
- Screenshots inalterados (mudança é de background/sync).

### Decisões e observações

- A data de sync é metadado local, não dado de saúde; fica em SharedPreferences.
- Ler 1 dia antes do último sucesso captura registros atrasados do wearable.
- Sem permissão ou Health Connect indisponível, o worker não insiste; o ciclo
  diário e o botão manual continuam disponíveis.
- Dias com CSV e Health Connect ainda podem ser sobrescritos pelo sync; mesclar
  por fonte fica para uma evolução futura (ADR 0014).

### Próximo passo

1. Revisar o planejamento do P3-05 com o usuário 0.
2. `P1-11` (notificações) ou `P1-12` (E2E) conforme prioridade.
3. Mescla por fonte para dias com CSV + Health Connect, se necessário.

## Sessão 2026-10-31 — P1-10: explicabilidade do treino

### Concluído

- Domínio:
  - `ExplanationCatalog` com rótulos curtos para as regras e evidências citadas pelo motor.
  - O ID bruto é preservado; IDs não catalogados caem no fallback auditável.
- App:
  - `TodayExplanationUi` com rationale, confiança, regras, evidências, dados usados e ausentes.
  - `buildExplanation` deriva os dados usados/ausentes da prontidão, carga, calibração, calor e esteira.
  - Bottom sheet “Por que este treino?” aberto pela prontidão e pelo card do treino.
  - Botão “Ver explicação” no card do próximo treino; texto determinístico, sem LLM.
- Testes:
  - Catálogo: regras/evidências usadas pelo recomendador e fallback.
  - ViewModel: regras, evidências, confiança e lacunas de dados.
  - UI: abertura do sheet, dados usados, regras e ID auditável.
- Screenshots de Hoje regravados (novo botão “Ver explicação”).

### Evidências

- `./gradlew check :app:verifyRoborazziDebug` — sucesso.
- 556 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 79 `core:data`, 204 `:app`).
- Cobertura `core:domain`: 98,37% linhas / 83,55% branches.
- `today-dark.png` e `today-light.png` atualizados e verificados.

### Decisões e observações

- O sheet mostra o ID bruto junto do rótulo; a explicação continua auditável mesmo se o catálogo evoluir.
- Dados ausentes são listados, não preenchidos com zero; isso mantém a confiança honesta.
- O mesmo sheet serve prontidão e treino porque a recomendação depende dos dois.
- Nenhum texto é gerado por LLM; o rationale é o mesmo do motor determinístico.

### Próximo passo

1. `P1-09`: sync periódico/incremental do Health Connect via WorkManager.
2. Revisar o planejamento do P3-05 com o usuário 0.
3. `P1-11`/`P1-12` conforme prioridade.

## Sessão 2026-10-31 — P1-08: histórico de check-ins

### Concluído

- ViewModel:
  - `CheckInUiState.history` com os últimos 7 check-ins, ordenados do mais recente.
  - `observeAll()` mantém o histórico atualizado após salvar ou editar.
  - O formulário de hoje continua carregado separadamente, sem duplicar a entrada.
- UI:
  - Seção “Últimos check-ins” abaixo do formulário.
  - Cada linha mostra data, as quatro notas, Hooper `/20` e pills de doença/lesão ou calor.
  - Texto explica que Hooper menor é melhor e alimenta a prontidão do dia.
  - A seção só aparece quando há histórico, preservando o estado vazio/screenshot.
- Testes:
  - ViewModel: ordem do histórico e atualização após salvar.
  - UI: histórico, Hooper, sinais de doença e calor.

### Evidências

- `./gradlew check :app:verifyRoborazziDebug` — sucesso.
- 551 testes, 0 falhas (27 `core:model`, 243 `core:domain`, 79 `core:data`, 202 `:app`).
- Cobertura `core:domain`: 98,33% linhas / 83,53% branches.
- Screenshot de check-in inalterado (histórico vazio no golden).

### Decisões e observações

- Histórico é read-only: editar continua sendo o formulário de hoje, para não reescrever dias passados.
- O check-in de hoje aparece no histórico assim que é salvo; ao editar, a mesma linha é atualizada.
- O histórico não calcula prontidão retroativa; isso continua em Tendências/Hoje com os dados disponíveis.
- Sem streak ou julgamento: é um registro, não uma cobrança.

### Próximo passo

1. Revisar o planejamento do P3-05 com o usuário 0.
2. Avaliar `P1-09` (Health Connect) e `P1-10` (explicabilidade).
3. Expandir o histórico para uma tela dedicada se o volume crescer.

## Sessão 2026-10-31 — P1-06: iniciar e trocar treino

### Concluído

- Domínio (`SessionSwapper`):
  - Troca o conteúdo de duas sessões planejadas da mesma semana.
  - Só aceita datas de hoje em diante; recusa concluídas, puladas, passadas e descanso.
  - Revalida a semana: máx. 2 qualidades, sem qualidades consecutivas e qualidade longe do longão.
  - Recalcula `weeklyMinutes`, `longRunMinutes` e `qualityCount`.
- Dados:
  - `TrainingPlanDao.updateSessionContent` e `TrainingPlanRepository.updateSessions`.
  - Atualiza somente conteúdo, preservando `status`, `skipReason` e `completedAt`.
- UI:
  - “Iniciar” abre a sessão guiada com alvos, rationale, cronômetro e “Concluir treino”.
  - “Trocar” lista os dias candidatos; trocas inválidas aparecem desabilitadas com o motivo.
  - A ação de concluir a sessão guiada marca a sessão do dia como feita.
  - `TodayScreen` recebe `today` injetável para testes determinísticos.
- Testes:
  - Domínio: troca válida, datas passadas, status, descanso, qualidade perto do longão e qualidades consecutivas.
  - Dados: atualização de conteúdo sem perder status.
  - App: sessão guiada conclui o dia; troca chama o callback com as datas corretas; ViewModel persiste a troca.

### Evidências

- `./gradlew check :app:verifyRoborazziDebug` — sucesso.
- 548 testes, 0 falhas (27 `core:model`, 243 `core:domain`, 79 `core:data`, 199 `:app`).
- Cobertura `core:domain`: 98,33% linhas / 83,53% branches.
- Screenshots de Hoje inalterados (fluxo em diálogo, sem mudança de layout).

### Decisões e observações

- A troca preserva o histórico: data e status ficam no dia, só o conteúdo muda (ADR 0013).
- A sessão guiada é local e sem GPS; concluir não inventa distância, pace ou carga.
- Se não há sessão planejada hoje, a sessão guiada informa e não marca conclusão.
- A validação no domínio é a fonte de verdade; a UI pré-valida para explicar recusas.

### Próximo passo

1. Revisar o planejamento do P3-05 com o usuário 0.
2. Expandir o histórico de conclusão para uma tela própria.
3. Próximos itens do `P1-08`/`P1-09` conforme prioridade.

## Sessão 2026-10-31 — P1-07: heatmap de consistência

### Concluído

- Domínio:
  - `ConsistencyDay` (data, km e corridas) e `ActivityTrends.heatmap`.
  - Heatmap alinhado à segunda-feira e terminando hoje, com uma coluna por semana.
  - Usa a mesma janela do filtro: 6/12/26 semanas (42/84/182 dias).
- App:
  - Mapa de consistência dentro do card de Consistência.
  - Intensidade da célula proporcional ao volume do dia; dia sem corrida fica no fundo neutro.
  - Legenda “Menos/Mais” e contagem de dias ativos.
  - Descrição de acessibilidade com janela e dias ativos.
  - Filtro de período passou a alinhar consistência e heatmap (42/84/182 dias).
- Testes:
  - Domínio: alinhamento semanal, fim em hoje, soma de km por dia e dias vazios.
  - ViewModel: heatmap não vazio e janela de 42 dias no período padrão.
  - UI: mapa, legenda, contagem e descrição de acessibilidade.

### Evidências

- `./gradlew check :app:verifyRoborazziDebug` — sucesso.
- 539 testes, 0 falhas (27 `core:model`, 238 `core:domain`, 78 `core:data`, 196 `:app`).
- Cobertura `core:domain`: 98,37% linhas / 83,60% branches.
- Screenshots existentes inalterados (golden de Tendências não inclui atividades).

### Decisões e observações

- O heatmap é descritivo: mostra hábito, não pontua, não pune e não cria streak.
- A janela de consistência padrão passou de 28 para 42 dias para casar com o filtro de 6 semanas.
- Células usam volume relativo ao maior dia da janela; dias sem corrida permanecem neutros.
- O heatmap não altera carga, prontidão ou prescrição.

### Próximo passo

1. Iniciar/trocar treino a partir do plano.
2. Revisar o planejamento do P3-05 com o usuário 0.
3. Ampliar o histórico de conclusão para uma tela própria, se necessário.

## Sessão 2026-10-31 — P1-07: eficiência pace@FC e filtros de período

### Concluído

- Domínio:
  - `PaceHeartRatePoint.efficiencyMetersPerBeat` calcula metros por batimento (velocidade média ÷ FC).
  - `ActivityTrendsCalculator` aceita janelas de consistência de 7 a 365 dias.
- App:
  - Filtro de período das atividades: **6, 12 ou 26 semanas** (`TrendsPeriod`).
  - O filtro recalcula volume, consistência e pace@FC; carga e 80/20 mantêm janelas próprias, explicadas na UI.
  - Gráfico de eficiência no card “Pace @ FC”, com média em m/batimento e eixo temporal implícito.
  - Lista das últimas corridas preservada abaixo do gráfico.
  - `TrendsViewModel.selectPeriod` cancela a carga anterior e recarrega de forma determinística.
- Testes:
  - Domínio: cálculo de eficiência, janela de 6 meses e limites de validação.
  - ViewModel: troca de período recarrega 26 semanas e consistência de 182 dias.
  - UI: chips de período, gráfico e textos de eficiência; ajustes de rolagem nos testes existentes.

### Evidências

- `./gradlew check :app:verifyRoborazziDebug` — sucesso.
- 537 testes, 0 falhas (27 `core:model`, 237 `core:domain`, 78 `core:data`, 195 `:app`).
- Cobertura `core:domain`: 98,36% linhas / 83,57% branches.
- Screenshots existentes inalterados (golden de Tendências não inclui atividades).

### Decisões e observações

- Eficiência é um **proxy descritivo** (m/batimento), não diagnóstico de economia; calor e fadiga confundem a leitura.
- O filtro é escopado às atividades; PMC (42 dias) e 80/20 (28 dias) continuam com janelas próprias e rotuladas.
- Recordes continuam considerando todo o histórico, independentemente do filtro.
- Períodos longos (26 semanas) exigem mais dados, mas não mudam nenhuma regra de prescrição.

### Próximo passo

1. Heatmap de consistência.
2. Iniciar/trocar treino a partir do plano.
3. Revisar o planejamento do P3-05 (story de treino) com o usuário 0.

## Sessão 2026-10-31 — P1-06: conclusão do plano em Tendências

### Concluído

- Domínio (`PlanCompletionCalculator`):
  - Janela de 8 semanas (segunda-domingo), com `planned`, `done`, `skipped` por semana.
  - Conta apenas sessões até hoje; sessões futuras não reduzem a semana atual.
  - Ignora descanso; semana sem plano fica com razão `null`, não 0%.
- Dados:
  - `TrainingPlanRepository.getWeeks(weekStarts)` carrega as semanas existentes em lote.
- App:
  - `TrendsViewModel` injeta o repositório de planos e expõe `planCompletion`.
  - Card “Adesão ao plano” em Tendências, com barras por semana, total concluído e puladas.
  - Texto explica que semanas sem plano ficam vazias e a semana atual conta só até hoje.
  - Sem streak, chama ou vermelho de culpa.
- Testes:
  - Domínio: janela, sessões futuras, descanso, semanas sem plano, totais e validação.
  - Dados: carregamento de múltiplas semanas e lacunas.
  - ViewModel e UI: conclusão a partir do banco e card renderizado.

### Evidências

- `./gradlew check :app:verifyRoborazziDebug` — sucesso.
- 533 testes, 0 falhas (27 `core:model`, 235 `core:domain`, 78 `core:data`, 193 `:app`).
- Cobertura `core:domain`: 98,36% linhas / 83,53% branches.
- Screenshots de Tendências inalterados (o golden não inclui plano).

### Decisões e observações

- A adesão é descritiva e serve para transparência da progressão (`R-PLAN-02`), não para punir.
- Sessões passadas ainda `PLANNED` contam como não concluídas; é o que a regra de progressão já faz.
- Sem plano na semana, a razão é `null`; a barra fica vazia em vez de sugerir 0% de adesão.

### Próximo passo

1. Gráfico de eficiência pace@FC e filtros de período.
2. Heatmap de consistência.
3. Iniciar/trocar treino a partir do plano.

## Sessão 2026-10-31 — P1-07: recordes pessoais em Tendências

### Concluído

- Domínio (`ActivityTrendsCalculator`):
  - `PersonalRecords` com maior distância, melhor pace e maior tempo.
  - Considera apenas corridas/esteira; caminhada e outros esportes ficam fora.
  - Melhor pace exige ≥ 3 km para não transformar strides ou imports parciais em recorde.
  - Usa `averagePace` e, quando ausente, `calculatedPace`; datas no fuso do perfil.
  - Recordes usam todo o histórico importado, não só a janela de consistência.
- UI (`TrendsScreen`):
  - Seção “Recordes” depois de Consistência, com rótulo, valor e data/distância.
  - Texto explícito: recorde descritivo, não prescrição, e regra dos 3 km.
  - Sem chama, streak ou gamificação.
- Testes:
  - Domínio: maior distância, melhor pace, maior tempo, exclusão de caminhada e esforço < 3 km, histórico vazio.
  - UI: card visível com valores formatados em pt-BR.

### Evidências

- `./gradlew check :app:verifyRoborazziDebug` — sucesso.
- 526 testes, 0 falhas (27 `core:model`, 231 `core:domain`, 77 `core:data`, 191 `:app`).
- Cobertura `core:domain`: 98,38% linhas / 83,33% branches.
- Screenshots de Tendências inalterados (o golden não inclui atividades).

### Decisões e observações

- Recordes são descritivos: não geram recomendação, meta ou comparação com outros usuários.
- O limite de 3 km evita que um stride ou uma atividade truncada vire “melhor pace”.
- Não há recorde de 5k/10k ainda: isso exigirá melhores esforços a partir dos samples, não da média da atividade.

### Próximo passo

1. Gráfico de eficiência pace@FC e filtros de período.
2. Heatmap de consistência.
3. Histórico de conclusão de treinos (`P1-06`).

## Sessão 2026-10-31 — P1-05: deload a cada 4ª semana

### Concluído

- Domínio (`WeeklyPlanGenerator`):
  - `WeeklyPlanInput.weekNumberInBlock` (1-based) define a posição no bloco.
  - A 4ª semana consecutiva aplica `DELOAD_FACTOR = 0.75` (−25%) em todas as sessões.
  - O tipo/intensidade é preservado: qualidade continua qualidade, com duração menor.
  - Taper tem precedência e o deload não acumula com ele.
  - Rationale cita a semana do bloco; regra `R-PLAN-04` e evidência `EV-ISSURIN-2010`.
- Dados:
  - `TrainingPlanDao.getPlanWeeksBefore` e `TrainingPlanRepository.consecutiveWeeksBefore`.
  - O bloco é ancorado em semanas consecutivas persistidas; lacuna reinicia a contagem.
  - Sem mudança de schema (continua v9); nenhuma migração nova.
- App:
  - `TodayViewModel` carrega a anterior + a posição no bloco e só gera o plano quando o histórico está pronto, evitando persistir uma semana com posição errada.
- Testes:
  - Domínio: deload na 4ª semana, volume −25%, qualidade preservada e precedência do taper.
  - Dados: contagem de semanas consecutivas com lacuna.
  - App: plano real de deload após 3 semanas persistidas.

### Evidências

- `./gradlew check :app:verifyRoborazziDebug` — sucesso.
- 524 testes, 0 falhas (27 `core:model`, 230 `core:domain`, 77 `core:data`, 190 `:app`).
- Cobertura `core:domain`: 98,36% linhas / 83,63% branches.
- Screenshots existentes seguem válidos (mudança é de domínio/plano, não de layout).

### Decisões e observações

- O bloco usa o histórico de planos como fonte de verdade, em vez de uma coluna nova no Room (ADR 0011).
- Um intervalo sem plano reinicia o bloco; é conservador e determinístico.
- Deload reduz volume, não troca a sessão de qualidade por fácil.
- A geração agora espera o histórico carregar; isso também remove a corrida que podia salvar a semana antes de ler a anterior.

### Próximo passo

1. Histórico de conclusão e streak de check-in (`P1-06`).
2. Gráfico de eficiência pace@FC e recordes em Tendências.
3. Blocos/periodização explícitos, se necessários.

## Sessão 2026-10-31 — P1-05: calor e esteira na prescrição

### Concluído

- Modelo:
  - `HeatLevel` (`NONE`/`HOT`) no `DailyCheckIn` como sinal autorreportado, não temperatura medida.
  - `AthleteProfile.treadmillAccess` como preferência durável de rotina.
- Persistência (Room v9, `MIGRATION_8_9`):
  - `daily_check_ins.heatLevel` com default `NONE`.
  - `athlete_profile.treadmillAccess` com default `0`.
  - Teste de migração 8→9 e round-trip nos repositórios.
- Prescrição diária (`WorkoutRecommender`):
  - Calor usa o extremo conservador da faixa (+3%), subordinado a FC/RPE (`R-PLAN-07`, `EV-ELY-2007`).
  - Confiança cai um nível quando há calor informado.
  - Com esteira, a justificativa lembra que a modalidade é válida no calor (`R-PLAN-08`).
- Plano semanal (`WeeklyPlanGenerator`):
  - Qualidade com acesso a esteira cita inclinação de 5–10% em RPE 8/10 (`R-PLAN-08`, `EV-DANIELS-2014`).
  - Quando há pace de limiar, prescreve também velocidade pela zona T (ex.: 9,7–10,0 km/h).
- UI:
  - Switch “Está muito calor?” no check-in, mapeado para `heatLevel`.
  - Switch “Tenho acesso a esteira” no passo de rotina do onboarding/perfil.
  - `TodayViewModel` passa o calor do check-in para a prescrição.

### Evidências

- `./gradlew check` — sucesso (testes, spotless, lint e Kover).
- `./gradlew :app:verifyRoborazziDebug` — sucesso; screenshots existentes seguem válidos.
- 519 testes, 0 falhas (27 `core:model`, 227 `core:domain`, 76 `core:data`, 189 `:app`).
- Testes novos cobrem +3% no pace, downgrade de confiança, regras/evidências, esteira com e sem pace de limiar, migração 8→9 e round-trip de persistência.

### Decisões e observações

- Calor é autorreportado no MVP; clima automático continua na Fase 2 (P2-07).
- O ajuste não reescreve o plano semanal persistido: calor muda a recomendação do dia; a qualidade guardada sempre oferece a alternativa de esteira quando há acesso.
- Inclinação fica em RPE para não inventar intensidade sem dados; velocidade só aparece com limiar calibrado.
- Dados ausentes continuam `null`; calor ausente não vira recuperação ruim.

### Próximo passo

1. Deload por blocos (`P1-05`).
2. Tela de histórico de conclusão e streak de check-in.
3. Gráfico de eficiência pace@FC e recordes em Tendências.

## Sessão 2026-10-31 — P1-07: Distribuição 80/20 por tempo

### Concluído

- `IntensityDistributionCalculator` em `core/domain/.../trends/`:
  - Mede tempo em Z1–Z2 (fácil), Z3 (moderado) e Z4–Z5 (forte), conforme `R-ZONE-02`.
  - Reaproveita `HrZoneDistributionCalculator`: intervalo sem FC fica fora do total.
  - Janela de 28 dias, quebra por semana (segunda-domingo) e semanas vazias omitidas.
  - Confiança por cobertura: < 3 sessões baixa, 3–5 média, 6+ alta.
  - `meetsEasyTarget` apenas reporta a heurística de ~80% (`R-PLAN-06`); não altera o plano.
- `TrendsViewModel`:
  - Abre os samples das corridas (`Sport.RUNNING`/`TREADMILL`) dentro da janela, somente quando há LTHR calibrado.
  - Expõe `intensity` no `TrendsUiState`.
- Tela Tendências:
  - Seção “Distribuição · 80/20” depois de Pace @ FC.
  - Barra empilhada por tempo, legenda com duração e percentual, e linhas por semana.
  - `McConfidenceBadge`, descrição de acessibilidade da barra e citação `R-ZONE-02 · EV-SEILER-2010`.
  - Estados explícitos: sem limiar calibrado e sem corrida com FC no período.
- Testes:
  - Domínio: buckets, janela, lacunas de FC, agregação semanal, confiança e validações.
  - ViewModel: distribuição a partir do blob, ausência de LTHR e ausência de carga.
  - UI: card completo, percentuais, linha semanal, confiança e estados vazios.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug` — sucesso.
- 507 testes, 0 falhas (27 `core:model`, 219 `core:domain`, 73 `core:data`, 188 `:app`).
- `core:domain`: 98,37% de linhas, 83,38% de branches, 97,08% de instruções.
- `IntensityDistributionCalculator`: 100% de linhas e branches.
- `verifyParserBranchCoverage`: 93,01% (572/615).
- Screenshots de Tendências inalterados (estado sem atividades no golden).

### Decisões e observações

- A distribuição é medida, não prescrita: o card mostra tempo por bucket e cita a heurística, sem reprovar a semana.
- A confiança descreve cobertura de dados, não qualidade do treino; é derivada do número de sessões com FC.
- Corridas e esteira entram no cálculo; outros esportes ficam fora para não misturar modalidades.
- Sem LTHR não se estima zona por FC máxima (`R-ZONE-01`); a tela pede calibração.
- Abrir blobs de 28 dias é aceitável nesta fase; se a base crescer, o próximo passo é agregar zonas na importação.

### Próximo passo

1. Calor/esteira na prescrição (`R-PLAN-07`/`R-PLAN-08`).
2. Tela de histórico de conclusão e streak de check-in.
3. Gráfico de eficiência pace@FC, recordes e filtros de período em Tendências.

## Sessão 2026-10-30 — P1-07: Tendências de atividades (parcial)

### Concluído

- `ActivityTrendsCalculator`:
  - Volume por semana (6 semanas) em km e corridas.
  - Consistência de 28 dias: dias ativos, corridas e dias desde a última.
  - Pontos pace×FC para corridas com ambos.
  - Médias de pace e FC no período.
- Tela Tendências:
  - Barras de volume semanal em Canvas.
  - Card de consistência com km e pace médio.
  - Lista “Pace @ FC” com data, pace e bpm.
- Testes de domínio, ViewModel e UI.

### Pendente

- Distribuição 80/20 por tempo — concluída em 31/10/2026.
- Pace@FC como gráfico de eficiência e recordes.
- Heatmap de consistência e filtros de período.
- Calor/esteira e deload por blocos no `P1-05`.

### Próximo passo

1. Recordes e gráfico de eficiência em Tendências.
2. Calor/esteira na prescrição.
3. Tela de histórico de conclusão e streak de check-in.

## Sessão 2026-10-30 — P1-06: Hoje e notificações (parcial)

### Concluído

- Notificações locais via WorkManager:
  - Check-in diário às 18h, apenas se ainda não feito.
  - Treino do dia às 7h, apenas se a sessão estiver `PLANNED` e não for descanso.
  - Canal "Lembretes de treino" e permissão `POST_NOTIFICATIONS` no Android 13+.
  - Toque abre Hoje ou check-in via `meucoach://today` e `meucoach://check-in`.
  - Agendamento idempotente (`enqueueUniquePeriodicWork` com `UPDATE`).
- Testes de conteúdo, delay, navegação, canal/notificação e agendamento.
- Tela Hoje com dados reais e ações de concluir/pular.

### Pendente

- Tela de histórico de conclusão.
- Check-in com streak.
- Ações Iniciar/Trocar treino.

### Evidências

- `./gradlew check :app:assembleDebug spotlessCheck :app:verifyRoborazziDebug` — sucesso.
- 488 testes, 0 falhas (27 `core:model`, 206 `core:domain`, 73 `core:data`, 182 `:app`).

## Sessão 2026-10-30 — P1-05: recomendação diária (parcial)

### Concluído

- `WorkoutRecommender` em `core/domain/.../prescription/`:
  - Doença/lesão ou prontidão vermelha ⇒ descanso.
  - Dia sem disponibilidade explícita ⇒ descanso.
  - Sono < 6 h, âmbar ou `NO_QUALITY` ⇒ fácil sem qualidade.
  - ACWR > 1,5 ⇒ fácil, sem aumento de carga.
  - Verde + calibração ⇒ fácil Z2 com pace E e FC Z2.
  - Sem calibração ⇒ fácil por RPE, confiança baixa.
  - Disponibilidade ausente ⇒ 30 min e confiança reduzida.
  - Toda recomendação cita regras, evidências e confiança.
- `WeeklyPlanGenerator`:
  - Semana segunda→domingo a partir da disponibilidade.
  - No máximo 1 qualidade, nunca adjacente ao longão.
  - Longão 25–30% do volume e ≤150 min para iniciante.
  - Sem progressão até existir histórico de conclusão.
  - Sem disponibilidade ⇒ plano vazio explícito (`R-DATA-01`).
- Plano persistido em Room v7 (`weekly_plans`/`planned_sessions`):
  - Gerado uma vez por semana e reutilizado; check-ins não reescrevem o plano.
  - `TrainingPlanRepository` com leitura observável, substituição de sessões e exclusão.
  - Migração 6→7 e testes 1→7 até 6→7.
- Status por sessão em Room v8:
  - `PLANNED`/`DONE`/`SKIPPED` + motivo e `completedAt`.
  - Ações "Concluir" e "Pular" no card do plano, com diálogo de motivo.
  - Migração 7→8 e testes de migração.
- Card "Plano da semana" na tela Hoje com dias, tipo, duração, confiança e rationale.
- Tela Hoje passa a usar a recomendação real no card de treino.
- CTL/ATL/TSB da tela Hoje vêm do motor de carga quando há atividades.
- Testes dos guardrails, alvos calibrados, RPE e limites de duração.

### Pendente

- Blocos/periodização e deload.
- Calor e esteira.
- Tela de histórico de conclusão e limite visível de 2 qualidades/semana.

### Evidências

- `./gradlew check :app:assembleDebug spotlessCheck :app:verifyRoborazziDebug` — sucesso.
- 447 testes, 0 falhas (27 `core:model`, 194 `core:domain`, 66 `core:data`, 160 `:app`).
- `core:domain`: 98,23% linhas, 82,84% branches, 96,85% instruções.

### Próximos passos

1. Plano semanal e periodização (`P1-05`).
2. Histórico de prontidão e validação do Health Connect em aparelho.
3. `P1-06`: consolidar a tela Hoje com dados reais.

## Sessão 2026-10-30 — P1-04: check-in, CSV e prontidão

### Concluído

- `DailyCheckIn` em `core:model` com validação 1–5 e Hooper sum.
- Room v5:
  - `DailyCheckInEntity`/`DailyCheckInDao` com chave por data.
  - `CheckInRepository` com observação por data, upsert transacional e `createdAt` preservado.
  - `MIGRATION_4_5` e testes 1→5, 2→5, 3→5 e 4→5.
- Prontidão a partir do check-in:
  - `toSubjectiveCheckIn`, `toReadinessSignals`, `toReadinessResult`.
  - Sinal de doença/lesão força vermelho e `NO_QUALITY`.
- Tela de check-in:
  - Quatro escalas 1–5, switch de doença/lesão, edição e salvamento.
  - Rota própria, sem barra inferior.
- Tela Hoje:
  - Passa a ler o check-in real para score/nível/confiança.
  - Sem check-in: 50/âmbar/baixa confiança e sinal "Pendente".
  - Card muda para "Check-in feito hoje" e "Revisar".
- CSV diário:
  - `DailyMetricsCsvParser` com `date`, sono, RHR, HRV e SpO2; datas ISO/BR, aliases e erros por linha.
  - Room v6 (`daily_metrics`) e `DailyMetricsRepository`.
  - `DailyMetricsReadinessBuilder`: sono recente, HRV 7d/60d (≥4 noites), RHR 7d/30d e sinal de RHR elevada por 2 dias.
  - Tela de importação no perfil, com contagem de dias, duplicatas e linhas ignoradas.
  - Fixture `fixtures/csv/daily-metrics.csv`.
- Health Connect:
  - `HealthDailySource` + `HealthConnectDailySource` (read-only, 30 dias, paginação).
  - `HealthMetricsAggregator` puro: sono por data de término, sessões sobrepostas mescladas, médias diárias.
  - Permissões de sono/RHR/HRV/SpO2 no manifest e rationale.
  - Card de status, autorização e sincronização no perfil.
  - `HealthConnectViewModel` com fake source nos testes.
- Histórico de prontidão:
  - `ReadinessHistoryCalculator` recompõe cada dia com dados até aquela data.
  - Card "Prontidão · 14 dias" em Tendências.
- Screenshots dark/light do check-in.

### Pendente

- Validação do Health Connect em aparelho real (a leitura Android compila e a agregação tem testes, mas não há instrumentado no CI).
- Hoje ainda usa treino e atividade de demonstração; a prescrição real vem no P1-05.

### Evidências

- `./gradlew check :app:assembleDebug spotlessCheck :app:verifyRoborazziDebug` — sucesso.
- 425 testes, 0 falhas (27 `core:model`, 176 `core:domain`, 66 `core:data`, 156 `:app`).
- `core:domain`: 98,17% linhas, 83,69% branches, 96,82% instruções.
### Próximos passos

1. Conectar Health Connect e CSV ao `ReadinessCalculator`.
2. Tela de diagnóstico e histórico de prontidão.
3. `P1-05`: prescrição com guardrails e explicação.

## Sessão 2026-10-30 — P1-03: motor de carga e PMC

### Concluído

- `TrainingLoadEngine` em `core/domain/.../load/`:
  - Série diária de 42 dias com carga por dia.
  - TRIMP quando FC + repouso + máxima estão disponíveis; rTSS quando só há pace + limiar; nunca soma os dois no mesmo dia (`R-LOAD-01`).
  - CTL/ATL por EWMA, TSB, médias 7/28, ACWR e monotonia.
  - Dias sem métrica ficam com carga 0 e `missingDays` conta a lacuna.
- Tela Tendências:
  - Cards de CTL, ATL, TSB, carga 7d, ACWR e monotonia.
  - Flag de ACWR (>1,5 alerta; >1,3 atenção; <0,8 cautela) com texto explícito de que não é meta.
  - Gráfico PMC com CTL e ATL em Canvas e descrição de acessibilidade.
  - Estados loading, sem perfil e sem carga.
- Golden `G-LOAD-02` cobre motor de 28 dias com TRIMP + rTSS e valores de PMC.

### Evidências

- `./gradlew check :app:assembleDebug spotlessCheck :app:verifyRoborazziDebug` — sucesso.
- 360 testes, 0 falhas (22 `core:model`, 154 `core:domain`, 57 `core:data`, 127 `:app`).
- `core:domain`: 98,47% linhas, 84,57% branches, 96,73% instruções.

### Decisões e observações

- O motor usa um métrico por dia; não há mistura de TRIMP e rTSS.
- ACWR aparece como flag; a UI não sugere aumentar carga por estar em faixa.
- `Days without load` é explícito para não confundir ausência com recuperação.
- A tela ainda não tem filtros de período nem 80/20; ficam para a evolução de Tendências.

### Próximos passos

1. `P1-04`: prontidão com Health Connect, check-in e CSV.
2. Adicionar zonas de pace e distribuição 80/20 em Tendências.
3. `P1-05`: prescrição com guardrails.

## Sessão 2026-10-30 — P1-02: calibração e zonas

### Concluído

- `CalibrationCalculator` em `core:domain/.../calibration/`:
  - VDOT pelas equações de Daniels (VO2 e %VO2max).
  - Pace de limiar pela velocidade a 88% do VDOT (equação inversa).
  - LTHR = FC média de esforço entre 25 e 35 min; fora da janela fica `null`.
  - Tiers A/B/C: performance ≤ 56 dias, ≥ 3 treinos com FC+pace, ou base inicial.
  - Avisos: maxHR inconsistente (> 95% da FC média), performance antiga/ inválida, sem FC.
  - Toda saída cita `R-CAL-01`/`R-CAL-02` e `EV-DANIELS-2014`/`EV-FRIEL-2018`.
- Tela de calibração no app:
  - Entrada de distância, tempo, data e FC média.
  - Resultado com VDOT, LTHR, pace de limiar, tier, confiança e avisos.
  - Salvamento no perfil sem apagar FC máxima nem outros dados.
- Resumo de calibração no perfil, com CTA para calibrar/recalibrar.
- `HrZoneDistributionCalculator` e card de zonas no detalhe:
  - Tempo por Z1–Z5 por LTHR, intervalos sem FC ficam fora do total.
  - Barra empilhada + legenda com tempo e percentual.
- Testes de motor, formulário, ViewModel, tela e distribuição de zonas.

### Evidências

- `./gradlew check :app:assembleDebug spotlessCheck :app:verifyRoborazziDebug` — sucesso.
- 343 testes, 0 falhas (22 `core:model`, 146 `core:domain`, 57 `core:data`, 118 `:app`).
- `core:domain`: 98,58% linhas, 84,70% branches, 96,70% instruções.

### Decisões e observações

- A confiança da calibração é alta, mas o LTHR continua sendo estimativa; não há teste de lactato.
- Performance fora de 25–35 min não gera LTHR; o app não extrapola.
- Zonas de pace por VDOT existem no domínio, mas a UI do detalhe mostra apenas FC nesta fase.
- A calibração é manual; importar uma prova não a aplica automaticamente.

### Próximos passos

1. `P1-03`: motor de carga integrado às atividades e PMC.
2. Exibir zonas de pace no detalhe e na prescrição.
3. `P1-04`: prontidão com Health Connect e check-in.

## Sessão 2026-10-30 — P1-01: onboarding e perfil

### Concluído

- `AthleteProfile` estendido com `timezone`, `weeklyAvailability`, `goal`, `city`, `latitude`, `longitude` e `units`, com validação de IANA, faixas e minutos por dia.
- Persistência Room v4:
  - `AthleteProfileEntity` + `AthleteProfileDao` (singleton).
  - `ProfileRepository` com fluxo observável, upsert transacional e preservação de `createdAt`.
  - JSON para disponibilidade e objetivo (`ProfileJson`), com tolerância a dados corrompidos.
  - `MIGRATION_3_4` e migrações 1→4/2→4/3→4 testadas.
- Onboarding em 3 passos:
  - Dados pessoais, sexo, altura, peso e experiência.
  - Objetivo, data/tempo alvo e disponibilidade semanal.
  - FC de repouso/máxima, fuso e cidade.
  - Validação por passo, erros em pt-BR, edição preservando calibração existente.
- Gate de primeiro uso: sem perfil, o app mostra o onboarding; com perfil, o shell normal.
- Tela de perfil com resumo, disponibilidade, objetivo e edição.
- Testes: validador, ViewModel, labels, telas e screenshots dark/light.

### Evidências

- `./gradlew check :app:assembleDebug spotlessCheck :app:verifyRoborazziDebug` — sucesso.
- 309 testes, 0 falhas (22 `core:model`, 131 `core:domain`, 57 `core:data`, 99 `:app`).
- `core:domain`: 98,82% linhas, 85,90% branches, 96,88% instruções.

### Decisões e observações

- O perfil é singleton local; multi-perfil continua fora do MVP.
- `units` só tem `METRIC` por enquanto; a UI não oferece milhas.
- Zonas continuam bloqueadas: o perfil coleta FC máxima observada, mas zonas exigem LTHR de calibração (P1-02).
- O onboarding não pede nome para não criar dado que o motor ainda não usa.

### Próximos passos

1. `P1-02`: calibração por prova/TT, VDOT/LTHR e tela de resultado.
2. Ligar zonas de FC e pace ao detalhe da atividade após a calibração.
3. `P1-03`: motor de carga integrado às atividades.

## Sessão 2026-10-30 — P0-10: harness, golden e CI

### Concluído

- Golden scenarios em `fixtures/golden/` com `input.json`, `expected.json` e README:
  - `G-LOAD-01`: TRIMP, rTSS, sRPE, CTL/ATL/TSB, ACWR e monotonia.
  - `G-READY-01`…`04`: dia verde, ausência total, doença/lesão e sono curto.
  - Doubles com tolerância `1e-6`; enums e inteiros exatos.
- Harness `LoadGoldenTest`/`ReadinessGoldenTest` lê os JSON via test resources; `org.json` é dependência **de teste** do `core:domain`.
- Roborazzi 1.77.0 com Robolectric Native Graphics:
  - `ActivityDetailScreenshotTest` grava dark/light em `app/src/test/screenshots/`.
  - `recordRoborazziDebug` atualiza; `verifyRoborazziDebug` bloqueia regressão.
- CI publica testes, Kover, lint e diff de screenshots em toda execução (`if: always()`), além de rodar `verifyRoborazziDebug`.

### Evidências

- `./gradlew check :app:assembleDebug spotlessCheck :app:verifyRoborazziDebug` — sucesso.
- 260 testes, 0 falhas (17 `core:model`, 131 `core:domain`, 46 `core:data`, 66 `:app`).
- `core:domain`: 98,82% linhas, 85,90% branches, 96,88% instruções.
- `verifyParserBranchCoverage`: 93,01% (572/615).

### Decisões e observações

- Cenários `G-001`…`G-006` (plano, taper, calor) ficam para quando `PlanEngine`/`PrescriptionEngine` existirem; não faz sentido golden de motor inexistente.
- Goldens de screenshot usam fixture sintética; nenhuma imagem contém dado real.
- O harness roda no JVM; instrumentado continua reservado para fluxos que dependem de dispositivo.

### Próximos passos

1. `P1-01`: onboarding/perfil com persistência local.
2. `P1-02`: calibração (VDOT/LTHR) para liberar zonas e prescrição.
3. Manter goldens e screenshots atualizados a cada mudança visual/regra.

## Sessão 2026-10-30 — P0-09: detalhe da atividade

### Concluído

- Navegação lista → detalhe (`activity/{activityId}`) com barra inferior oculta e `SavedStateHandle`.
- `ActivityDetailViewModel` abre o agregado no repositório e expõe loading, não encontrado e dados.
- Hero: distância, tempo, pace, FC, cadência, potência, elevação, calorias e tempo em movimento.
- Card de fontes: `FIT · TCX · GPX` a partir do canônico + alternates.
- Gráfico `Pace × FC × tempo` em Canvas, com downsampling, legenda de faixas e descrição de acessibilidade.
- Splits por lap com pace do arquivo ou derivado de tempo/distância e FC.
- Rota offline em Canvas (equiretangular), sem tiles, rede ou nova dependência.
- Insights determinísticos:
  - `R-ACT-01`: ritmo constante/negative split vs queda > 3% na segunda metade.
  - `R-ACT-02`: deriva cardíaca ≥ 5% com pace estável.
  - Cada insight cita evidência (`EV-ABBISS-2008`, `EV-DANIELS-2014`, `EV-FRIEL-2018`) e confiança.
- Testes: ViewModel, formatadores, gráfico, rota, insights e Compose UI (loading, não encontrado, conteúdo e tema claro).
- `RealFixturesDetailTest`: importa e abre os 7 treinos reais sanitizados; TCX confere distância/duração/cadência do README.

### Evidências

- `./gradlew check :app:assembleDebug spotlessCheck` — sucesso.
- 256 testes, 0 falhas (17 `core:model`, 129 `core:domain`, 46 `core:data`, 64 `:app`).
- `core:domain`: 98,82% linhas, 85,90% branches, 96,88% instruções.
- `./gradlew :core:domain:verifyParserBranchCoverage` — 93,01% (572/615).

### Decisões e observações

- Zonas ficam para depois da calibração: LTHR ainda não existe no app, e estimar por maxHR violaria `R-ZONE-01`.
- A rota é um traçado local, não um mapa; mapas com tiles entram quando houver decisão de dependência/offline.
- O gráfico usa duas escalas independentes e declara as faixas na legenda para não sugerir uma unidade única.
- Insights são observações, não prescrição; não alteram carga nem plano.

### Próximos passos

1. `P0-10`: harness restante (goldens e relatórios) — concluído na sessão seguinte.
2. `P1-01`/`P1-02`: onboarding e calibração para liberar zonas e prescrição.
3. Export ZIP com arquivos originais e séries completas (Fase 1).

## Sessão 2026-10-30 — P0-07: persistência Room

### Concluído

- Módulo `:core:data` como Android library, com Room 2.8.5, KSP e schemas exportados em `core/data/schemas/`.
- Entidades e DAOs:
  - `ActivityEntity`: proveniência, esporte, tempo, distância, FC, cadência, potência, elevação, parser, `deletedAt` e índice único de `sourceFileSha256` quando presente.
  - `LapEntity`: chave composta `(activityId, lapIndex)` e FK com `CASCADE`.
  - `SampleBlobEntity`: blob comprimido, versão, encoding, contagem e início.
  - `ImportJobEntity`: status, hash, erro, timestamps e FK `SET NULL` para a atividade.
  - `ActivityDao`, `LapDao`, `SampleBlobDao` e `ImportJobDao`; consultas de lista observável e lixeira.
- `MeuCoachDatabase` v2:
  - `MIGRATION_1_2` adiciona `deletedAt` sem recriar a tabela.
  - Schemas 1 e 2 exportados; teste de migração com `MigrationTestHelper` em Robolectric.
  - Sem `fallbackToDestructiveMigration`.
- `SampleBlobCodec` (`gzip-columnar-v1`):
  - Colunas de offset, posição, altitude, velocidade, FC, cadência, potência e passada.
  - Developer fields preservados em coluna binária própria.
  - Timestamps reconstruídos a partir do início da atividade + offset.
  - Limites de tamanho/contagem, validação de presença, gzip inválido, versão desconhecida e truncamento.
- `ActivityRepository`:
  - Importação transacional de atividade + laps + blob.
  - Dedup por SHA-256 do arquivo; retorna `Duplicate` sem inserir.
  - Listagem/observação como `ActivitySummary`.
  - Abertura como `StoredActivity`, decodificando samples sob demanda.
  - Blob corrompido ou de versão desconhecida mantém a atividade, descarta samples e retorna `sampleWarning`.
  - Soft delete por padrão; exclusão permanente com cascata; `deleteAllData`.
  - Export JSON básico com atividades (incluindo lixeira), laps, contagem de samples e jobs.
  - UUID v7 local e ordenável por tempo.
- Backup Android permanece desativado no manifest (`allowBackup=false`, `fullBackupContent=false`).
- Testes novos em `core:data`:
  - `MeuCoachDatabaseTest`: CRUD, soft delete, cascata, `SET NULL` e unicidade de SHA.
  - `MeuCoachDatabaseMigrationTest`: migração 1→2 preserva dados.
  - `SampleBlobCodecTest`: round-trip, compressão, corrupção, versão, contagem absurda e ordem.
  - `ActivityRepositoryTest`: salvar, listar, abrir, duplicata, blob corrompido, delete, export.
  - `UuidV7Test`: versão, variante e determinismo.

### Evidências

- `./gradlew check :app:assembleDebug` — sucesso.
- `./gradlew :core:data:testDebugUnitTest` — 26 testes, 0 falhas.
- `./gradlew :core:domain:verifyParserBranchCoverage` — `93,01% (572/615)`.
- 160 testes, 0 falhas; `core:domain` com 98,91% de linhas e 86,02% de branches.
- Android Lint: 0 erros; 4 avisos de versão/target. `core:data` sem issues.
- APK debug gerado em `app/build/outputs/apk/debug/app-debug.apk`.

### Decisões e observações

- A dedup do repositório cobre apenas hash exato do arquivo. A dedup por chave de atividade (início ±1 s, duração ±2 s, distância ±1%) e os `alternates` ficam para o fluxo de importação do P0-08.
- Samples não guardam timestamp absoluto por ponto; o início da atividade + offset é a fonte canônica. Isso reduz o blob e evita duplicar informação.
- Developer fields são preservados, mas não entram no JSON de export básico; o blob completo continua no banco.
- A lixeira é lógica: filhos permanecem até a exclusão permanente. O export inclui atividades com `deletedAt`.
- `zoneId` é capturado na criação do repositório e `localDate` é derivada do início da atividade. Mudança de fuso do perfil será tratada no onboarding/perfil.
- `:core:data` ainda não está ligado ao app nem a um módulo Hilt; isso entra junto com a importação real no P0-08.

### Próximos passos

1. `P0-08`: file picker (SAF) + share intent, fila persistente com WorkManager e tela de importação.
2. Ligar `:core:data` ao app, com Hilt e `ActivityRepository`.
3. Dedup por chave de atividade, alternates e substituição FIT > TCX > GPX no fluxo real.
4. `P0-09`: detalhe da atividade com gráficos, splits e rota.
5. Fechar `P0-10`: Robolectric/Roborazzi, golden scenarios e relatórios no CI.

### Riscos/observações

- O codec limita o payload descomprimido a 128 MB e a 5 milhões de samples; arquivos muito maiores falham com erro claro.
- O export básico não inclui séries completas nem arquivos originais; export ZIP/arquivos fica para a Fase 1.
- A migração 1→2 é pequena por design; mudanças futuras de schema exigem novo teste e schema exportado.
- Nenhum dado de saúde real pode entrar no git; revisar `git status` antes de cada commit.

## Sessão 2026-10-29 — P0-06: parsers TCX/GPX e dedup

### Concluído

- Parser TCX em `core/domain/src/main/kotlin/com/meucoach/core/domain/parser/tcx/`:
  - Activity, laps, trackpoints, FC, cadência, posição, altitude, velocidade e potência por extensão.
  - Fallback de início (Activity/Id → Lap/StartTime → primeiro Trackpoint), duração por lap ou intervalo dos pontos e distância declarada → GPS → integração de velocidade.
  - Infere um lap quando o TCX não tem `Lap`; lap e atividade recebem resumo calculado.
  - Cadência de corrida/esteira é multiplicada por 2 (passadas/min → passos/min); walking mantém o valor.
  - Dados fora de faixa física viram `null`; o parser não inventa zero.
- Parser GPX em `core/domain/src/main/kotlin/com/meucoach/core/domain/parser/gpx/`:
  - GPX 1.0/1.1 por nome local, `trkpt`, `ele`, `time` e extensões `hr`, `cad`, `speed`, `power`.
  - Distância por haversine, fallback por integração de velocidade; sem elevação permanece `null`.
  - Sem laps; esporte inferido de `type` e padrão running; `creator` vira `deviceName`.
  - Cadência de corrida/esteira normalizada como no TCX.
- `SecureXml` compartilhado em `core/domain/.../parser/xml/`:
  - `DocumentBuilderFactory` namespace-aware, entidades externas bloqueadas, DOCTYPE desabilitado quando a feature é suportada, `EntityResolver` vazio e XInclude desativado.
  - Helpers DOM por nome local para TCX/GPX.
- Deduplicação em `core/domain/.../importer/ActivityDeduplicator.kt`:
  - Tolerância de ±1 s no início, ±2 s na duração e ±1% (mínimo 1 m) na distância.
  - Preferência FIT > TCX > GPX > Health Connect > Manual; em empate, mais samples e depois versão do parser.
- Sanitização e fixtures:
  - `tools/sanitize_tcx.py` remove `Position`, `Creator`, `Notes` e `Author`; valida o XML de saída.
  - 3 TCX reais sanitizados em `fixtures/tcx/` (~1,9 MB), com README e métricas esperadas.
  - 2 GPX sintéticos em `fixtures/gpx/` (extensões e sem elevação), com README.
- Testes novos:
  - `TcxParserRealFixturesTest`: 3 TCX reais + determinismo.
  - `TcxParserSyntheticTest`: laps, posição, cadência, valores inválidos, lap ausente, encoding, XML malformado e XXE.
  - `GpxParserTest`: extensões, sem elevação, potência, coordenadas inválidas, XML malformado, XXE e determinismo.
  - `ActivityDeduplicatorTest`: prioridade, tolerância, samples e chave.

### Evidências

- `./gradlew check :app:assembleDebug` — sucesso.
- `./gradlew :core:domain:verifyParserBranchCoverage` — `93,01% (572/615)` no pacote FIT.
- `./gradlew :core:domain:koverVerify` — sucesso.
- 134 testes, 0 falhas; `core:domain` com 98,91% de linhas e 86,02% de branches.
- Android Lint: 0 erros; 2 avisos intencionais.

### Decisões e observações

- TCX/GPX são adapters de fallback; FIT continua canônico.
- A remoção de posições dos TCX reais é intencional: GPS e múltiplos laps são cobertos por fixtures sintéticas, sem risco de localização.
- O dedup usa tolerância conforme `DATA_MODEL.md`; a chave exata (`keyOf`) fica disponível para diagnóstico e jobs de importação.
- Alternates e confiança de lap inferido serão ligados ao fluxo de importação no `P0-08`.
- O parser XML é JVM puro (`javax.xml`), sem dependência Android.

### Próximos passos

1. `P0-07`: `core:data` com Room, entidades, DAOs, blob comprimido e migração de teste.
2. `P0-08`: file picker/share intent, fila persistente, progresso/erro e dedup no fluxo real.
3. Integrar TCX/GPX ao detalhe da atividade e validar os 7 treinos reais.
4. Fechar `P0-10`: Robolectric/Roborazzi, golden scenarios e relatórios no CI.

### Riscos/observações

- TCX de outros fabricantes pode exportar cadência já em passos/min; o parser segue o padrão dos arquivos Zepp e a documentação atual. Revisar com uma fixture nova antes de mudar a heurística.
- GPX não tem esporte nem laps; a importação deve tratar distância calculada por GPS como menor confiança que FIT.
- O CI precisa manter platform 37 e build-tools 37.
- Nenhum dado de saúde real pode entrar no git; revisar `git status` antes de cada commit.

## Sessão 2026-10-28 — P0-05: parser FIT

### Concluído

- Modelos canônicos de importação em `core:model`:
  - `ImportedActivity`, `ActivityLap` e `ActivitySample`.
  - Dados ausentes permanecem `null`; nada é convertido em zero artificial.
- Sanitizador determinístico `tools/sanitize_fit.py`:
  - Trabalha no nível de bytes, preservando a estrutura FIT, escalas e developer fields.
  - Substitui todas as posições pelo sentinela inválido do FIT, em vez de deslocar GPS.
  - Limpa serial e identificadores de aplicação/dispositivo.
  - Substitui o developer field `source` por `example.invalid`.
  - Mantém timestamps, recalcula os CRCs e valida a abertura com `fitdecode`.
- Fixtures sanitizadas:
  - 4 arquivos reais do Amazfit Balance em `fixtures/fit/` (~132 KB no total).
  - Nenhum arquivo original ou dado identificável foi commitado.
  - Os 3 TCX antigos continuam em `~/Documents/Rafa/` e serão usados no `P0-06`.
- Parser FIT em `core:domain/src/main/kotlin/com/meucoach/core/domain/parser/fit/`:
  - `FitReader`: framing de 12/14 bytes, CRC de header e arquivo, little/big endian, definições, compressed timestamps, tipos base, developer fields e warnings.
  - `FitParser`: file_id, session, record, lap, event, activity e device_info; fallbacks de início/duração/distância; normalização de cadência; validação de faixas físicas.
  - Cadência de `record.cadence` e `session.avg/max_running_cadence` é strides/min e é multiplicada por 2; `lap.avg_cadence` já é passos/min.
  - Warnings para arquivo sem file_id/sessão, múltiplas sessões, esporte desconhecido, sample sem timestamp/offset inválido, CRC ausente, developer field desconhecido e dados extras.
- Testes:
  - 50 testes do parser, incluindo 4 fixtures reais, casos sintéticos, erros de framing/CRC e cobertura de branches.
  - `FitParserRealFixturesTest` valida resumo e samples dos 4 FIT sanitizados.
  - `FitParserSyntheticTest`, `FitReaderErrorsTest`, `FitParserCoverageTest`, `FitParserBranchTest` e `FitReaderCoverageTest` cobrem os caminhos de fallback e erro.
- Gate de cobertura:
  - Task `:core:domain:verifyParserBranchCoverage` lê o XML do Kover e falha abaixo de 90% de branches no pacote `parser.fit`.
  - Ligada a `check` e `koverVerify`; saída atual: `FIT parser branch coverage: 93,01% (572/615)`.
- Higiene:
  - `__pycache__/` e `*.pyc` passaram a ser ignorados pelo Git.

### Evidências

- `./gradlew check :app:assembleDebug` — sucesso.
- `./gradlew :core:domain:koverVerify` — sucesso, incluindo o gate do parser.
- `./gradlew :core:domain:verifyParserBranchCoverage` — 93,01% (572/615).
- Testes: 105 total, 0 falhas.
- Kover `core:domain`: 99,88% de linhas, 93,37% de branches, 99,04% de instruções.
- Android Lint: 0 erros, 2 avisos (`OldTargetApi` para targetSdk 36 e `NewerVersionAvailable` do Kover 0.9.11).
- APK debug: `app/build/outputs/apk/debug/app-debug.apk` (~20 MB).

### Decisões e observações

- O parser é determinístico, sem I/O e sem dependência de Android.
- O FIT continua canônico; TCX/GPX serão fallbacks com preferência FIT > TCX > GPX.
- Fixtures reais só entram após sanitização no nível de bytes; o CI usa apenas fixtures commitadas.
- A comparação com `fitdecode` foi feita localmente; não é uma dependência de runtime nem de CI.
- A cobertura de branches do parser é medida no pacote inteiro, não apenas em `FitParser`; isso evita esconder branches no leitor de bytes.

### Próximos passos

1. Iniciar `P0-06`: parser TCX com laps, trackpoints, FC, cadência e posição, usando os 3 TCX antigos fora do repo.
2. Implementar GPX com extensões comuns e fallback sem elevação.
3. Adicionar deduplicação entre formatos com preferência FIT > TCX > GPX.
4. Depois, `P0-07`: persistência Room com blob comprimido e migração de teste.
5. Fechar `P0-10`: Robolectric/Roborazzi, golden scenarios e relatórios no CI.

### Riscos/observações

- O parser FIT é o maior risco técnico; a suíte cobre o subconjunto do Amazfit, mas novos dispositivos podem expor campos/ordenações diferentes.
- O CI precisa manter platform 37 e build-tools 37.
- Nenhum dado de saúde real pode entrar no git; revisar `git status` antes de cada commit.
- O build local usa JDK 17; Gradle 9.8 também roda em 27, mas o CI fixa 17.

## Sessão 2026-10-27/28 — Planejamento e fundação

### Concluído

- Entrevista de produto, ciência e design.
- Análise inicial das atividades reais do Amazfit Balance; depois separadas em 4 FIT e 3 TCX.
- Health Connect validado para sono no dispositivo do usuário 0.
- Stack Android validada em projeto limpo:
  - AGP 9.4.1, Gradle 9.8.2, Kotlin 2.4.21, KSP 2.3.12, JDK 17.
  - Compose BOM 2026.09.00, Room 2.8.5, Hilt 2.60.1.
  - `compileSdk 37` necessário para `core-ktx 1.19.1`.
- Documentação criada:
  - `README.md`, `docs/PRODUCT.md`, `docs/ARCHITECTURE.md`, `docs/DATA_MODEL.md`.
  - `docs/SPORTS_SCIENCE.md`, `docs/DESIGN_SYSTEM.md`, `docs/TESTING.md`, `docs/ROADMAP.md`.
  - `docs/RUNBOOK.md`, `docs/DECISIONS.md` + 9 ADRs, `AGENTS.md`.
- Tokens DTCG em `design/tokens/meucoach.tokens.json`.
- Bootstrap Gradle completo:
  - Wrapper 9.8.2, version catalog, módulos `:app`, `:core:model`, `:core:domain`, `:core:designsystem`.
  - Spotless/ktlint, Android Lint, Kover, Makefile e CI GitHub Actions.
- Modelo e domínio iniciais:
  - `Distance`, `Pace`, `ActivitySummary`, `AthleteProfile`, enums e testes.
  - Zonas de FC por LTHR e zonas de pace por limiar.
  - TRIMP, rTSS, sRPE, EWMA, CTL/ATL/TSB, ACWR e monotonia.
  - Prontidão v1 com Hooper invertido, pesos renormalizados, confiança e flags.
- Design system:
  - Tema dark/light, Geist + Inter variáveis, shapes e paleta alinhada aos tokens.
  - `McCard`, botões, pills, stats, badge de confiança, gauge, section header, bottom bar e empty state.
  - Ajustes de contraste AA no claro (`accentText`, `textTertiary`, coral e âmbar) refletidos em tokens e docs.
- App:
  - Shell com 4 abas, navegação, edge-to-edge e ícone adaptativo com camada monochrome.
  - Tela Hoje premium com prontidão, próximo treino, carga semanal, check-in e última corrida.
  - Placeholders de Corridas, Tendências e Perfil com empty states distintos.
  - Backup/device-transfer desativados por regras de extração de dados.
- Verificação visual:
  - APK instalado em emulador API 35 (Pixel 7).
  - Tela Hoje verificada em dark e light; placeholder de Corridas verificado em dark.
  - Splash claro/escuro corrigido via `values-night`.

### Evidências da época

- `./gradlew check :app:assembleDebug` — sucesso.
- `./gradlew :core:domain:koverVerify` — sucesso.
- Kover `core:domain`: 216/216 linhas, 245/260 branches.
- Android Lint: 0 erros, 2 avisos (`OldTargetApi` e `NewerVersionAvailable` do Kover).
- APK debug: `app/build/outputs/apk/debug/app-debug.apk` (~20 MB).

### Decisões-chave

- FIT canônico; parser próprio com validação cruzada.
- Health Connect + check-in + CSV; Zepp direto só na Fase 3.
- Motor determinístico; LLM só para texto.
- Musculação fora do escopo; fadiga global no check-in.
- Sem SQLCipher/biometria no MVP.
- Dark-first com lima como único accent; lima como texto no claro usa `#4F6600`.
- Backend Ktor/Postgres/Dokploy na Fase 2.

## Histórico

| Data | Marco |
|---|---|
| 27/10/2026 | Planejamento concluído; documentação e ADRs; stack validada |
| 28/10/2026 | Fundação verde: build, testes, Kover, lint e tela Hoje em dark/light |
| 28/10/2026 | P0-05 concluído: sanitizador, 4 fixtures FIT, parser, 105 testes e gate de branches |
| 29/10/2026 | P0-06 concluído: parsers TCX/GPX, 3 TCX sanitizados, GPX sintético, dedup e 134 testes |
| 30/10/2026 | P0-07 concluído: Room v2, migração testada, blob comprimido, repositório, export/delete e 160 testes |
| 31/10/2026 | P1-07 80/20 por tempo concluído: calculadora de zonas, card em Tendências e 507 testes |
| 31/10/2026 | P1-05 parcial: calor autorreportado (+3%) e alternativa de esteira na qualidade; Room v9 e 519 testes |
| 31/10/2026 | P1-05 parcial: deload de −25% a cada 4ª semana consecutiva, ancorado no histórico de planos; 524 testes |
| 31/10/2026 | P1-07 parcial: recordes pessoais em Tendências (distância, pace ≥ 3 km e tempo); 526 testes |
| 31/10/2026 | P1-06 parcial: conclusão do plano em 8 semanas em Tendências, sem streak; 533 testes |
| 31/10/2026 | P1-07 parcial: eficiência pace@FC (m/batimento) e filtros de 6/12/26 semanas; 537 testes |
| 31/10/2026 | P1-07 parcial: heatmap de consistência alinhado por semana e por volume; 539 testes |
| 31/10/2026 | P1-06 parcial: sessão guiada com cronômetro e troca de dia com guardrails; 548 testes |
| 31/10/2026 | P1-08 parcial: histórico dos últimos 7 check-ins com Hooper e sinais; 551 testes |
| 31/10/2026 | P1-10 concluído: bottom sheet de explicação com regras, evidências, confiança e lacunas; 556 testes |
| 31/10/2026 | P1-09 concluído: sync incremental do Health Connect + worker diário via WorkManager; 562 testes |
| 31/10/2026 | P3-05 protótipo: planner, renderer Canvas 9:16 e export MP4 via MediaCodec; 568 testes |
| 31/10/2026 | P3-05: prévia antes do export + confirmação/cancelamento; 570 testes |
| 02/11/2026 | P3-05: validação em aparelho real confirmada (`resposta 8`); `ADR-0015` aceito — mapa real (`OSM` + `MapLibre`), frame final (3 s) e logo `MeuCoach` |
| 02/11/2026 | P1-13 concluído: linguagem simples em Hoje/Tendências, logo no story, proporção de carga real e onboarding mais robusto; 580 testes |
| 03/11/2026 | T-06 concluído: tela Dados exporta JSON completo e apaga todos os dados em transação, com confirmação; 591 testes |
| 03/11/2026 | P1-11 concluído: notificação de importação (sucesso/duplicado/falha) com deep link para o treino; 600 testes |
| 03/11/2026 | Hoje: card de carga deixa de usar números de demonstração sem dados; estado vazio honesto; 602 testes |
| 04/11/2026 | Story: geração em segundo plano via WorkManager, cancelamento real no encoder, ETA e notificação (ADR 0018); 754 testes |
| 04/11/2026 | Story: mapa outdoor sem POIs, snapshot 2,25×, foco na rota e ticks por km; bloqueio do tilt nativo documentado (ADR 0015 addendum); 755 testes |
| 04/11/2026 | Story: progresso/ETA reais, foreground service, estado restaurado e encoder na GPU (190→42 ms/frame, export ~50 s); 760 testes |
| 04/11/2026 | Story público: destaques objetivos sem julgamento de plano, mapa Terreno com hillshade e aviso de download; botão full-width; 771 testes |
| 04/11/2026 | Story: estilo Satélite (Esri + labels + véu), recomendado para cidades planas; 772 testes |
