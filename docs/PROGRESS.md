# PROGRESS.md — Diário de progresso

**Atualizado:** 30/10/2026

## Estado geral

- **Fase:** 0 — Fundação
- **Build:** verde (`./gradlew check :app:assembleDebug`)
- **Testes:** 410 testes, 0 falhas (27 em `core:model`, 170 em `core:domain`, 66 em `core:data`, 147 em `:app`)
- **Cobertura `core:domain`:** 98,10% de linhas (1.604/1.635), 84,10% de branches (1.381/1.642), 96,78% de instruções (12.447/12.861)
- **Parser FIT:** 93,01% de branches (572/615); gate próprio exige ≥ 90% de branches
- **Lint:** 0 erros; 4 avisos de versão/target, nenhum de código
- **Dados reais:** 4 FIT e 3 TCX sanitizados; GPX apenas sintético
- **Persistência:** Room 2.8.5, schema v6 (`daily_metrics`), migrações 1→2 até 5→6 testadas.
- **Fonte de fontes:** `ActivitySummary.sources` expõe todos os formatos que contribuíram (ex.: `FIT · TCX · GPX`).
- **Importação real:** `ImportStaging`, `StagedImportProcessor`, `ImportWorker` (`@HiltWorker`), `ImportCoordinator` e `ShareIntentReader`. WorkManager 2.12.0 com `HiltWorkerFactory` (manifest sem `WorkManagerInitializer`).
- **UI de atividades:** `ActivitiesScreen`, `ActivitiesViewModel`, `ActivityFormatters` (`sourcesLabel`) e `ImportJobCard`.
- **Detalhe da atividade:** hero, métricas, gráfico pace × FC (Canvas), splits, rota offline e insights com `ruleId`/`evidenceId`/confiança.
- **Perfil e onboarding:** formulário em 3 passos (dados, objetivo/disponibilidade, rotina), validação, persistência e edição.
- **Calibração:** VDOT/LTHR/pace de limiar por prova ou TT ≤ 8 semanas, com tiers A/B/C e tela de resultado.
- **Zonas:** distribuição de tempo por zona de FC no detalhe, usando o LTHR calibrado.
- **Carga:** série diária TRIMP/rTSS, CTL/ATL/TSB, ACWR, monotonia e tela de Tendências com PMC.
- **Check-in:** 4 perguntas (sono, fadiga, dor, estresse) + sinal de doença/lesão, persistido por data e usado na prontidão da tela Hoje.
- **CSV diário:** parser de sono/RHR/HRV/SpO2, persistência, importação pelo perfil e agregação de janelas 7/30/60 dias na prontidão.
- **Health Connect:** pendente no P1-04.
- **Share intent:** `ACTION_SEND`/`SEND_MULTIPLE` no manifest e no `MainActivity`; `ImportMessages` unifica o texto de fila.

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
- Screenshots dark/light do check-in.

### Pendente

- Health Connect (sono, RHR, HRV, SpO2).
- Tela de diagnóstico de dados e histórico de prontidão.
- Hoje ainda usa treino e atividade de demonstração; a prescrição real vem no P1-05.

### Evidências

- `./gradlew check :app:assembleDebug spotlessCheck :app:verifyRoborazziDebug` — sucesso.
- 410 testes, 0 falhas (27 `core:model`, 170 `core:domain`, 66 `core:data`, 147 `:app`).
- `core:domain`: 98,10% linhas, 84,10% branches, 96,78% instruções.
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
