# TESTING.md — MeuCoach

**Versão:** 1.11 · **Data:** 12/11/2026 · Atualiza a evidência de testes e cobre a recuperação pós-prova (C3-07): janela por distância, tetos, bloqueio de qualidade/longão e sugestão `RACE_COMPLETED`.

## 1. Princípios

1. **Teste no lugar certo.** Fórmula no domínio, parser no parser, comportamento na UI.
2. **Dado real é o melhor professor.** Usar os arquivos reais do usuário 0 **sanitizados** como fixtures de regressão.
3. **Teste que não falha não protege.** Todo bug corrigido ganha um teste que falharia antes.
4. **Determinismo.** Nada de teste dependente de relógio, rede ou ordem de execução.
5. **Rápido por padrão.** A suíte unitária roda em segundos; instrumentados ficam separados.
6. **Privacidade.** Nenhum dado de saúde real no git ou no CI.
7. **Cobertura é meio, não fim.** O alvo é confiança nas regras críticas, não 100% de linhas.

## 2. Pirâmide

| Camada | Ferramenta | O que cobre | Onde roda |
|---|---|---|---|
| Unitário | JUnit 4 + AssertJ | Fórmulas, zonas, parser, regras, validações | JVM, cada PR |
| Property-based | Kotest Property (planejado, não configurado) | Invariantes (monotonicidade, limites) | — |
| Golden | JUnit + JSON | Cenários completos de carga/prontidão | JVM, cada PR |
| Parser de referência | Python `fitdecode` (script) | Validação cruzada do parser FIT | Local/CI opcional |
| Banco | Room `MigrationTestHelper` | Migrações e queries | JVM (Robolectric) |
| UI | Compose UI Test + Robolectric | Estados, acessibilidade básica | JVM, cada PR |
| Screenshot | Roborazzi | Regressão visual dark/light | JVM, cada PR |
| Gráficos | Roborazzi + Vico | Eixos, unidades, lacunas e cores de nível nas telas Tendências e Saúde | JVM, cada PR |
| Render de gráficos | Compose UI Test + pixels | Série realmente pintada após a composição (linha e barra), sem depender do producer assíncrono | JVM, cada PR |
| Instrumentado | Compose UI Test + AndroidX Test | Fluxos reais, Health Connect fake | **0 hoje**; só se houver distribuição |
| E2E | Fluxo import → hoje | Aceitação do MVP | Manual (P1-12); sem suíte automatizada |

> Kotest, MockK e Turbine **não estão no version catalog**; a suíte usa JUnit 4, AssertJ, fakes próprios, Robolectric, Compose UI Test e Roborazzi. Property-based e instrumentados ficam no backlog técnico (T-04/TEST-01).

## 3. Estrutura de testes

```
core/model/src/test/          # value objects (pace, distância)
core/domain/src/test/
  zones/                      # FC, pace, distribuição
  load/                       # TRIMP, rTSS, CTL/ATL/TSB, ACWR, cobertura
  health/                     # séries diárias de sono, FC de repouso, HRV e SpO2
  readiness/                  # score, confiança, overrides
  prescription/               # plano, prescrição diária, evidência, conclusão
  parser/                     # FIT, TCX, GPX, CSV
  golden/                     # cenários JSON
core/data/src/test/           # Room, migrações, repositórios, export, época
app/src/test/                 # ViewModels, casos de uso, workers, formatadores, screenshots e render de gráficos
fixtures/
  fit/                        # fixtures sanitizadas/sintéticas
  tcx/
  gpx/
  csv/
  golden/                     # entradas e saídas esperadas
  private/                    # IGNORADO — dados reais
```

Não existe `app/src/androidTest/` hoje. A cobertura de fluxo real é manual (`P1-12` e roteiro do §7.6 do plano do ciclo 1).

## 4. Fixtures e privacidade

- Dados reais ficam em `fixtures/private/` (gitignored) ou fora do repo.
- Fixtures commitadas são **sanitizadas**:
  - Posições FIT substituídas pelo sentinela inválido do protocolo; não há GPS real.
  - Serial e identificadores de aplicação/dispositivo removidos.
  - Nomes, notas, e-mails e URLs pessoais removidos.
  - Timestamps podem ser mantidos (não identificam sozinhos) ou deslocados.
  - CRCs recalculados para que o arquivo continue válido.
- Fixtures sintéticas devem cobrir casos que os arquivos reais não têm:
  - FIT com developer fields, big-endian, timestamps comprimidos, CRC inválido.
  - TCX sem laps, sem GPS, com pausas.
  - GPX com extensões de FC/cadência e sem elevação.
  - CSV com colunas faltantes, datas duplicadas, valores vazios.
- O CI usa apenas fixtures commitadas; nunca acessa `~/Documents`.
- Scripts (Fase 0):
  - `tools/sanitize_fit.py` opera no nível de bytes, invalida posições, limpa serial/IDs e `source`, recalcula CRC e valida com `fitdecode`.
  - `tools/sanitize_tcx.py` remove `Position`, `Creator`, `Notes` e `Author`, preserva timestamps/métricas e valida o XML.
- Estado atual: 4 FIT reais sanitizados em `fixtures/fit/`, 3 TCX reais sanitizados em `fixtures/tcx/` e 2 GPX sintéticos em `fixtures/gpx/`. Nenhum GPX real é commitado porque os originais têm nomes de eventos e coordenadas.

## 5. Testes de parser

Para cada formato:

1. **Smoke:** arquivo válido → atividade canônica com campos obrigatórios.
2. **Resumo:** distância, duração, FC média/máx, cadência, potência, elevação.
3. **Samples:** contagem, monotonicidade do tempo, limites físicos (FC 30–250, pace > 0).
4. **Laps:** quantidade, somas, boundary.
5. **Erros:** arquivo truncado, CRC inválido, XML malformado, encoding, campos ausentes.
6. **Determinismo:** parse duas vezes → mesmo resultado.
7. **Referência:** comparar resumo com `fitdecode`/biblioteca independente.
8. **Dedup:** FIT + TCX + GPX da mesma sessão → uma atividade, FIT vence.

O parser FIT deve ser testado contra um **vetor de bytes conhecido** e contra os 4 arquivos FIT reais sanitizados em `fixtures/fit/`. Os 3 TCX reais sanitizados em `fixtures/tcx/` cobrem laps, trackpoints, FC, cadência e resumo; posição, múltiplos laps e XML inválido são cobertos por fixtures sintéticas. Nenhum teste pode depender de rede.

Evidência atual (12/11/2026):

- 1.497 testes no repositório, 0 falhas e 0 `@Ignore` (39 em `core:model`, 592 em `core:domain`, 162 em `core:data`, 704 em `:app`).
- 4 FIT + 3 TCX reais sanitizados; 2 GPX sintéticos.
- `parser.fit`: 93,01% de branches (572/615).
- `core:domain`: 97,56% de linhas, 81,13% de branches, 96,23% de instruções.
- Comando: `./gradlew :core:domain:verifyParserBranchCoverage`.
- A task falha o build se o pacote FIT ficar abaixo de 90% de branches; ela roda em `check` e `koverVerify`.
- `ActivityDeduplicatorTest` cobre prioridade, tolerância de ±1 s/±2 s/±1% e escolha por samples.
- Testes Robolectric usam `android.app.Application` (`app/src/test/resources/robolectric.properties`) para não iniciar os coletores do `MeuCoachApplication` real entre testes.

## 5.1 Testes de persistência

- `MeuCoachDatabaseTest` (Robolectric): CRUD do agregado, lista observável, soft delete, cascata de laps/samples, `SET NULL` em import jobs e unicidade de `sourceFileSha256`.
- `MeuCoachDatabaseMigrationTest` (Robolectric + `MigrationTestHelper`): migrações 1→2 até 14→15, incluindo backfill de `DONE → LEGACY_UNVERIFIED`, `SKIPPED → CLOSED/USER_SKIPPED`, hashes legados desconhecidos na 12→13, fingerprint/checkpoint `NULL` na 13→14 e `race_events` vazia sem backfill na 14→15.
- `SampleBlobCodecTest`: round-trip de todas as colunas e developer fields, compressão, timestamps reconstruídos, gzip inválido, truncamento, versão desconhecida, contagem absurda, bytes extras e ordem dos samples.
- `ActivityRepositoryTest`: salvar/listar/abrir, dedup por SHA-256, blob corrompido e encoding desconhecido com `sampleWarning`, soft delete, exclusão permanente e export JSON.
- `UuidV7Test`: versão 7, variante RFC e determinismo com clock/random fixos.
- Schemas exportados em `core/data/schemas/` são assets dos testes; sem `fallbackToDestructiveMigration`.
- Comando: `./gradlew :core:data:testDebugUnitTest`.

## 5.2 Testes de importação e detalhe

- `ImportServiceTest`: FIT/TCX/GPX, hash exato, substituição e alternates.
- `RealFixturesDetailTest`: importa e abre os 4 FIT + 3 TCX reais sanitizados; valida resumo, laps, samples e ausência de `sampleWarning` (TCX confere distância, duração e cadência).
- `ActivityInsightsCalculatorTest`: `R-ACT-01` (negative split, ritmo constante, queda), `R-ACT-02` (drift e limites) e ausência de dados.
- `ActivityDetailViewModelTest`: carregamento, atividade ausente e id obrigatório.
- `ActivityChartDataTest`: pace derivado de velocidade, downsampling, faixas de FC e entradas inválidas.
- `RouteDataTest`: projeção normalizada, norte para cima, downsampling e ausência de GPS.
- `ActivityDetailScreenTest` (Robolectric + Compose UI): loading, não encontrado, seções com scroll e tema claro; valida descrição de acessibilidade do gráfico.
- `ActivityDetailFormattersTest`: distância, metas de lap, faixas do gráfico, confiança e fontes.

## 5.3 Testes de perfil e onboarding

- `AthleteProfileTest`: disponibilidade, objetivo, fuso, coordenadas e limites.
- `ProfileJsonTest`: round-trip de disponibilidade/objetivo, dias desconhecidos, JSON corrompido e metas ausentes.
- `ProfileRepositoryTest`: singleton, round-trip completo, preservação de `createdAt`, edição e exclusão.
- `MeuCoachDatabaseMigrationTest`: migrações 1→4, 2→4 e 3→4 criam `athlete_profile` sem perder atividades.
- `OnboardingValidatorTest`: validação por passo, datas estritas, tempos `mm:ss`/`h:mm:ss`, FC, fuso e arredondamentos.
- `OnboardingViewModelTest`: bloqueio de passo inválido, salvamento, edição preservando LTHR/VDOT e disponibilidade.
- `OnboardingScreenTest` e `ProfileScreenTest` (Compose UI): estados, erros, loading, tema claro e ação de edição.
- `ProfileLabelsTest`: idade, meta, tempo alvo, disponibilidade e labels de enums.

## 5.4 Testes de calibração e zonas

- `CalibrationCalculatorTest`: VDOT, pace de limiar, tier A/B/C, LTHR, performance antiga/inválida, aviso de maxHR.
- `CalibrationFormTest`: distância, tempos `mm:ss`/`h:mm:ss`, data estrita, FC e contagem de esforços recentes.
- `CalibrationViewModelTest`: carregamento, cálculo, invalidação ao editar e salvamento no perfil.
- `CalibrationScreenTest`: formulário, resultado, ausência de métricas, erros e ação de salvar.
- `HrZoneDistributionCalculatorTest`: alocação por zona, ordenação, intervalos sem FC e LTHR inválido.
- `TrainingLoadEngineTest`: TRIMP vs rTSS, CTL/ATL/TSB, ACWR, monotonia, janela, dias ausentes e burn-in de 180 dias (histórico anterior à janela alimenta as EMAs; sem histórico, a série começa em zero; aquecimento não entra em cobertura nem em `lastActivityAt`).
- `LoadEngineGoldenTest`: cenário `G-LOAD-02` de 28 dias.
- `TrendsViewModelTest` e `TrendsScreenTest`: carga a partir das atividades, estados vazios e PMC.
- `DailyCheckInTest`: validação 1–5 e Hooper sum.
- `CheckInRepositoryTest`: round-trip por data, upsert, exclusão e `createdAt`.
- `CheckInReadinessTest`: mapeamento e prontidão só com check-in.
- `CheckInViewModelTest` e `CheckInScreenTest`: prefill, salvamento, escalas e loading.
- `DailyMetricsCsvParserTest`: template, datas ISO/BR, aliases, erros por linha e duplicatas.
- `DailyMetricsRepositoryTest`: lote, atualização por data, `createdAt` e exclusão.
- `DailyMetricsReadinessBuilderTest`: janelas 7/30/60, HRV mínimo, sono recente, RHR elevada 2 dias, estatísticas em log e rejeição de valores não finitos.
- `ReadinessCalculatorTest`: pesos renormalizados, níveis/limites, clamp de HRV/RHR, z-score em log e validação de entradas.
- `MetricsImportViewModelTest` e `MetricsImportScreenTest`: importação, erros, duplicatas e template.
- `HealthMetricsAggregatorTest`: sono por data local, sobreposição, médias diárias e fuso.
- `HealthConnectViewModelTest`: status, permissões faltantes, sync, indisponibilidade e erro.
- `HealthConnectContentTest`: estados do card, autorização e sincronização.
- `WorkoutRecommenderTest`: guardrails, fácil Z2 calibrado, RPE, ACWR e duração.
- `WeeklyPlanGeneratorTest`: disponibilidade, longão 25–30%, qualidade única/dupla, progressão, taper, ACWR e semana vazia; recuperação pós-prova bloqueia qualidade/longão, aplica tetos por fase e volta ao normal depois da janela; volume parte do concluído (base 120 → 128), mantém abaixo de 80%, não cresce em deload/taper/prova/recuperação, respeita o teto da agenda e limita a distância ao alvo depois da escala.
- `VolumeProgressionTest`: sem histórico → teto; +6% sobre a semana anterior; semana < 80% mantém; base antiga não cresce; deload/taper/prova/recuperação; prontidão/carga/ACWR; teto da agenda; plano parcial e janela de 4 semanas; empate pela mais recente; só evidência forte conta.
- `TodayViewModelTest`: recomendação no card, prontidão e sinais objetivos.
- `RecoveryWindowTest`: janela 2/3/5/10, borda inclusiva, dia da prova fora, tetos por distância, `blocks`, `dayOfWindow`, `intersects`, escolha da prova mais recente e invariantes.
- `DailyPrescriptionEngineTest`: dentro da janela, `QUALITY`/`LONG` viram fácil com teto, fácil acima do teto é reduzido, dia `DONE` não é reescrito e fora da janela nada muda.
- `PlanReplannerTest`: `RACE_COMPLETED` sugere revisão quando a semana persistida viola a janela, `NoChange` quando já está compatível e depois da janela; hash muda com o histórico de volume e a candidata respeita a base concluída.
- `TodayPlanTest`: card do plano semanal na tela Hoje.
- `TodayReplanTest`: selo de versão, botão explícito, diff “o que mudou” e aceitar/recusar/suprimir na tela Hoje.
- `SessionBriefingEngineTest`: fidelidade de cada campo à prescrição, determinismo, RPE sem calibração, ausência de blocos/intervalos/alternativas, `DONE`/`CLOSED` sem briefing, downgrade `QUALITY → EASY` e IDs preservados.
- `RaceEventTest`: distâncias suportadas, estados, `resolvedAt`, vínculo só em `COMPLETED` e data passada ainda `PLANNED`.
- `TodayDecisionResolverTest`: treino × competição, evento terminal no dia, ausência de decisão e data divergente.
- `PlanningGoalResolverTest`: precedência do evento `PLANNED`, equivalência com a meta legada, meta fora do fingerprint e fim do fallback após o primeiro evento.
- `RacePlanTest`: janelas 14/21, cutoff 10/14 inclusivo, semana da prova sem qualidade, prova passada e fator profundo.
- `WeeklyPlanGeneratorTest`: sem sessão no dia/depois da prova, qualidade removida no cutoff, taper por data da sessão, janela de maratona e data de evento sem taper.
- `PlanReplannerTest`: nenhuma sessão em data de evento, sessão passada em evento não conta como perdida, qualidade legada na semana da prova e hash sensível a corrida/datas.
- `PlanCompletionCalculatorTest`: datas de evento fora do numerador e denominador.
- `SessionSwapperTest`: troca envolvendo data de evento é rejeitada.
- `RaceEventRepositoryTest`: evento ativo único, edição, conclusão/cancelamento/não comparecimento, vínculo único e cruzado com execução, lixeira/expurgo e janela de datas.
- `RaceTargetProjectionTest`: pace de 5K/10K/meia/maratona, faixa ±3% arredondada, passagens por km, chegada fracionária, `null` sem meta e monotonicidade.
- `RaceAutoCompletionRuleTest`: mesma data, 90%/110% inclusivos, fora da banda, esporte, vínculo com treino/prova, recusa, evento terminal, múltiplas candidatas e fuso do perfil.
- `AutoCompleteRaceFromActivityTest`: conclusão com corrida compatível sem `SessionExecution`, rejeições, idempotência, treadmill, undo com recusa persistida e conclusão explícita intocada.
- `RaceEventRepositoryTest` (C3-06): `USER` × `AUTO_IMPORT`, reopen automático, undo com outra prova planejada, undo de evento planejado e vínculo cruzado reverso.
- `MeuCoachDatabaseMigrationTest` (14→16 e 15→16): `race_events` vazia e `completionSource` nullable preservando o histórico.
- `RaceEventSectionTest` e `TodayRaceTest`: referência da meta, sheet de passagens, ausência de projeção sem meta, confirmação de auto-conclusão e “Não era a prova”.
- `StagedImportProcessorTest` (C3-06): importação compatível auto-conclui a prova sem criar treino concorrente.
- `UserDataRepositoryTest` (C3-06): export inclui `completionSource`; reset remove eventos e recusas.
- `RacePlanTest` (C3-06): `targetTimeSec` não altera a projeção de planejamento.
- `MeuCoachDatabaseMigrationTest` (14→15): `race_events` vazia, dados legados preservados e schema validado.
- `MeuCoachDatabaseMigrationTest` (16→17): perfil legado recebe proveniência de calibração `NULL`; Room 17 e export a preservam.
- `SessionComparisonCalculatorTest`: tolerância inclusiva de 10%, direções de duração/distância/pace/FC/RPE, FC sem teto, ausência de um dos lados, pace derivado, treino livre, plano a partir de prescrição/sessão e `REST`.
- `GetSessionComparisonTest`: prescrição preferida à sessão, fallback da semana, vínculo por execução, corrida não vinculada em dia com plano e corrida sem plano.
- `ActivityComparisonFormatTest`: linhas, omissão de dado ausente, notas factuais, resumo “tudo dentro” e treino livre com “—”.
- `ActivityDetailScreenTest` (FR-010): card no detalhe, ausência de IDs técnicos e treino livre sem alvo.
- `WeeklyReviewCalculatorTest`: semana completa/parcial, semana sem plano, semana sem dados, doença primeiro, sono/prontidão repetidos, volume, intensidade, `NO_ALERTS`, determinismo, filtro de datas, deduplicação e invariantes.
- `WeeklyReviewTest`: invariantes de adesão, volume, prontidão, sono, janela, intensidade, limite de 3 observações e estado vazio.
- `WeeklyReviewFormatTest`: data, adesão, minutos, volume, prontidão/sono, cada observação, singular/plural e ausência de `R-`/`EV-`.
- `TrendsViewModelTest` (FR-011): última semana fechada, consolidação com plano/corridas/métricas e semana sem plano.
- `TrendsScreenTest` (FR-011): card com adesão/volume/zonas/observações, estados vazios, ausência de IDs e fonte 200%.
- `WeeklyReviewScreenshotTest`: goldens dark/light/estreito do card de revisão.
- `TodayBriefingTest`: CTA só em sessão não concluída, abertura do sheet, ordem de leitura, estados loading/vazio/erro, sessão concluída sem briefing, RPE sem calibração e fonte 200% em tela estreita.
- `EvaluateReplanTest` e `ApplyReplanTest`: gatilhos T1/T2/T3, baseline, doença, inputs alterados, versão obsoleta, semana removida, proteção de hoje/passado/`DONE` e prescrição do dia intacta.
- `ReplanViewModelTest`: sugestão automática, supressão do dia, recusa por proposta, aceite, stale e ausência de semana.
- `ReplanSuggestionStoreTest`: recusa por semana/versão/hash, supressão por dia, clear e reset.
- `TodayScreenshotTest`: golden dark/light da Hoje com prontidão, treino, CTA do briefing e plano (inclui o card do plano com versão).
- `SessionBriefingScreenshotTest`: golden dark/light do conteúdo do briefing (foco, alvo, rationale, como correr e se estiver difícil).
- `ReplanScreenshotTest`: golden dark/light do card de sugestão de replanejamento.
- `TrainingPlanRepositoryTest`: round-trip da semana, substituição de sessões, status/motivo, exclusão e semana vazia.
- `MeuCoachDatabaseMigrationTest`: 7→8 adiciona status da sessão sem perder o plano.
- `ReadinessHistoryCalculatorTest`: replay por data, doença, confiança e lacunas.
- `ReadinessHistoryViewModelTest` e `TrendsScreenTest`: histórico real e card de 14 dias.
- `ReminderContentTest`: check-in, treino, status e delay diário.
- `NotificationNavigationTest`: deep links e fila de navegação.
- `ReminderNotifierTest`: canal e notificação postada.
- `ReminderSchedulerTest`: dois trabalhos periódicos únicos com tag.
- `ActivityTrendsCalculatorTest`: volume semanal, consistência, pace×FC e janelas inválidas.
- `TrendsScreenTest`: cards de volume, consistência e pace×FC.

## 5.5 Testes de tutorial e primeiro uso

- `TutorialScreenTest`: 3 cenas, progresso, back, CTAs dinâmicos, fallback de carregamento/erro, fonte 200% e fonte escalável.
- `TutorialSystemBackTest`: back do sistema na primeira cena marca como visto.
- `TutorialViewModelTest`: perfil, plano real gerado/persistido, prontidão, Health Connect e `markSeen`.
- `TutorialStoreTest`: flag de intro e dismiss do checklist por 7 dias.
- `FirstRunChecklistViewModelTest`: estado inicial, check-in, Health Connect, dismiss e conclusão dos 3 passos.
- `FirstRunChecklistCardTest` e `TodayPlanTest`: card no Hoje, ações e dismiss.
- `FirstRunMetricsStoreTest`: timeline do tutorial, maior passo, primeira ação e eventos do checklist.
- `FirstRunTipStoreTest` e `FirstRunTipCardTest`: dica única, ação e dismiss.
- `DataManagementViewModelTest`: export inclui o objeto `firstRun`.
- Goldens: 3 cenas × dark/light + `today-checklist-dark`.

### 5.6 Testes do ciclo 1

- `TimeProviderTest` / `TimeDrivenStateTest`: relógio fixo, 23:59→00:01, domingo→segunda, fuso, DST e plano semanal.
- `TrainingLoadEngineTest`: `LoadCoverage`, `LoadDataState`, frescor, 7/8 dias e limites 0,79/0,8/1,3/1,31.
- `DailyPrescriptionEngineTest`, `DailyPrescriptionRepositoryTest` e `GetTodayPrescriptionTest`: `KEEP`/`REDUCE`/`DOWNGRADE`/`REST`/`NO_PLAN`, N-01/N-02, calor, sem calibração, versão e hash.
- `SessionMatcherTest`, `SessionCompletionRulesTest`, `SessionExecutionRepositoryTest` e `CompleteSessionTest`: vínculo 1:1, manual válido/inválido, futuro, ontem, descanso/doença e legado.
- `TrashPolicyTest`, `DataEpochStoreTest`, `DataResetCoordinatorTest`, `HealthSyncServiceTest` e `ImportServiceTest`: lixeira de 7 dias, expurgo por clock, reset sem ressurreição e época.
- `MeuCoachDatabaseMigrationTest`: 10→11, 11→12 e 12→13 com banco antigo, backfill e preservação de status/planos.
- `Cycle1RegressionTest`: gates de disponibilidade, espaçamento, prontidão vermelha, ACWR desconhecido, cobertura insuficiente, plano no meio da semana, `DONE` sem evidência e progressão bloqueada por legado.

## 6. Testes de domínio

### 6.1 Zonas

- Limites exatos: 80,9% / 81% / 89,9% / 90% / 94% / 100%.
- LTHR inválida → erro claro.
- Pace de limiar → 5 zonas sem sobreposição nos alvos.
- Usuário 0: 170 bpm com LTHR 180 ⇒ Z4; 150 ⇒ Z2; 181 ⇒ Z5.

### 6.2 Carga

- TRIMP calculado à mão para 30 min, FC 150, repouso 58, máx 187 ⇒ 53,85 pontos (fórmula em minutos, não segundos).
- rTSS: 60 min no limiar = 100; 30 min = 50; pace 10% mais lento ⇒ ~82,6.
- CTL/ATL: série constante → converge; TSB = CTL − ATL.
- ACWR: crônico 0 → `null`; 7d/28d conhecido.
- Monotonia: desvio 0 → `null` (não infinito).
- Nenhum resultado pode ser NaN/Infinity/negativo.

### 6.3 Prontidão

- Todos os inputs bons → verde, confiança alta.
- Sem HRV/RHR → confiança média, sem penalizar como recuperação ruim.
- Sono curto → no máximo âmbar + flag `SHORT_SLEEP`.
- Doença/lesão → vermelho + `ILLNESS_OR_INJURY`.
- RHR +7 por 2 dias → no máximo âmbar.
- Nenhum dado → âmbar neutro (50) + `INSUFFICIENT_DATA`, confiança baixa.
- Pesos renormalizados: remover componente não muda a escala dos demais.
- Limites 39/40/69/70.

### 6.4 Prescrição

- Semana de base respeita 80/20, 2 qualidades máximo, longão 25–30%.
- Progressão bloqueada se vermelho, ACWR > 1,5, sono < 6,5 h ou dor.
- Deload a cada 4ª semana.
- Taper reduz volume 40–60% e mantém intensidade.
- Nenhum plano coloca qualidade em dias consecutivos.
- Calor ajusta pace dentro da banda e nunca abaixo do limite seguro.
- Toda sessão tem ≥ 1 regra e ≥ 1 evidência.

## 7. Golden scenarios

Arquivos em `fixtures/golden/<cenario>/`:

```
input.json       # perfil, atividades, métricas, check-ins, data
expected.json    # saída do motor (com tolerância para doubles)
README.md        # o que o cenário prova
```

Cenários implementados hoje:

| ID | Prova |
|---|---|
| `G-LOAD-01` | TRIMP, rTSS, sRPE, CTL/ATL/TSB, ACWR e monotonia |
| `G-LOAD-02` | Série diária e PMC de 28 dias |
| `G-READY-01` | Dia verde com dados completos |
| `G-READY-02` | Dados ausentes totais → não quebra, confiança baixa |
| `G-READY-03` | Sinal de doença/lesão suspende qualidade |
| `G-READY-04` | Sono curto bloqueia qualidade |

Cenários de plano/prescrição (`G-001`…`G-006`) **não existem**. Hoje, plano e
prescrição são cobertos por testes unitários determinísticos
(`WeeklyPlanGeneratorTest`, `DailyPrescriptionEngineTest`, `Cycle1RegressionTest`);
um golden de plano/prescrição só entra se o cenário provar algo que os testes de
contrato não provam.

Doubles comparados com tolerância `1e-6`; inteiros exatos. `engineVersion` entra
no expected quando o cenário for auditado.

### 7.1 Regressões preparadas para o ciclo 1

A estratégia completa de C1-01 a C1-06 está em
[`docs/PLANO_TECNICO_CICLO_1.md`](PLANO_TECNICO_CICLO_1.md) §7, com matriz por
tipo (unitário, integração, migração, concorrência, WorkManager, data/fuso,
exclusão, decisão diária, vínculo e indicadores).

`core/domain/src/test/kotlin/com/meucoach/core/domain/prescription/Cycle1RegressionTest.kt`
contém 9 cenários executáveis e verdes:

- Disponibilidade configurada, espaçamento de qualidade, prontidão vermelha,
  adesão ignorando futuro/REST, ACWR desconhecido, cobertura insuficiente,
  plano no meio da semana começando em hoje, `DONE` sem evidência e progressão
  bloqueada por legado.

Regra: nenhum cenário do ciclo 1 permanece `@Ignore`. Quando uma correção entra,
o teste é habilitado no mesmo commit e passa a ser gate. Cenários que dependem
de classes inexistentes ficam documentados na matriz, não em estruturas
artificiais.

## 8. Testes de UI

- **Estados:** loading, empty, error, stale, success.
- **Acessibilidade:** `contentDescription` de gráficos, labels de botões, ordem de foco.
- **Formatação:** pace `7:40`, distância `5,11 km`, data pt-BR, unidade.
- **Interação:** trocar treino, concluir check-in, abrir "por quê".
- **Temas:** dark e light, fonte 100% e 200%.
- **Data e duração:** seguir `DESIGN_SYSTEM.md` §7.3. Testar dígitos crus, máscara visual, edição no meio, troca de unidade (`Minutos` × `Horas`), segmentos inválidos (minutos/segundos > 59), vazio opcional, zero inválido e fonte 200%.
- **Referência da meta:** pace/faixa sempre com unidade, passagens em diálogo rolável, `null` sem tempo-alvo, números tabulares, TalkBack e fonte 200% em tela estreita.
- **Screenshot:** tamanhos reais usados — 393×852 (principal), 360×760/800 (estreito) e capturas focadas de altura variável (ex.: 393×760, 393×1500) × 2 temas, apenas telas-chave.

Ferramentas: Compose UI Test, Robolectric (JVM), Roborazzi (screenshot), AndroidX Test (instrumentado). Sem Espresso puro; Compose-first.

Screenshots atuais (66 goldens em `app/src/test/screenshots/`, Roborazzi + Robolectric Native Graphics):

| Grupo | Arquivos |
|---|---|
| Atividade | `activity-detail-dark`, `activity-detail-light`, `activity-comparison-dark`, `activity-comparison-light`, `activity-comparison-free-dark` |
| Calibração | `calibration-dark`, `calibration-light`, `calibration-tier-b-dark`, `calibration-tier-b-light` |
| Check-in | `checkin-dark`, `checkin-light` |
| Dados | `data-management-dark`, `data-management-light` (lixeira e opção de backup) |
| Saúde | `health-dark`, `health-light`, `health-narrow-dark`, `health-charts-dark`, `health-recovery-dark`, `health-sources-dark`, `health-today-hint-dark` |
| Onboarding | `onboarding-dark`, `onboarding-light`, `onboarding-goal-dark`, `onboarding-routine-dark` |
| Perfil | `profile-dark`, `profile-light`, `profile-health-dark`, `profile-health-light`, `profile-race-dark`, `profile-race-light` |
| Story | `story-frame-dark`, `story-frame-logo-dark`, `story-final-dark`, `story-map-dark`, `story-final-map-dark`, `story-card-verdict-dark`, `story-final-verdict-dark`, `story-avatar-dark` |
| Hoje | `today-dark`, `today-light`, `today-narrow-dark`, `today-checklist-dark`, `today-plan-dark`, `today-plan-light`, `today-briefing-dark`, `today-briefing-light`, `today-race-dark`, `today-race-light`, `today-race-narrow-dark` |
| Replanejamento | `replan-dark`, `replan-light` |
| Tendências | `trends-dark`, `trends-light`, `trends-charts-dark`, `trends-volume-dark`, `trends-efficiency-dark`, `trends-readiness-dark` |
| Revisão semanal | `trends-weekly-review-dark`, `trends-weekly-review-light`, `trends-weekly-review-narrow-dark` |
| Tutorial | `tutorial-dark`, `tutorial-light`, `tutorial-2-dark`, `tutorial-2-light`, `tutorial-3-dark`, `tutorial-3-light` |

`./gradlew :app:recordRoborazziDebug` atualiza os goldens;
`./gradlew :app:verifyRoborazziDebug --tests "*ScreenshotTest"` falha se a tela mudar.
`check` **não** verifica goldens: o gate visual é explícito e roda em invocação própria,
para não disputar frames com a suíte lógica. O CI roda a verificação e publica o diff em
artifacts. Não há dados de saúde nas imagens (fixture sintética).

Goldens de C3-06: `profile-race-{dark,light}` e `today-race-{dark,light,narrow-dark}` foram regravados com o bloco “Referência da meta” e os mesmos fixtures sintéticos.

Goldens de D6-B (ADR 0036): os mesmos `profile-race-{dark,light}` e `today-race-{dark,light,narrow-dark}` foram regravados com o bloco “Previsão de referência” (tempo, faixa, confiança, fonte e aviso de não garantia), ainda com fixtures sintéticos.

Goldens de FR-010: `activity-comparison-{dark,light}` e `activity-comparison-free-dark` capturam o card isolado (tema escuro/claro e treino livre).

Goldens de FR-011: `trends-weekly-review-{dark,light,narrow-dark}` capturam o card isolado com adesão, volume, zonas, prontidão, sono e observações. `HealthMetricsScreenshotTest`, `TrendsFullScreenshotTest` e `WeeklyReviewScreenshotTest` usam `captureStableScreenshot`, que espera 8 quadros idênticos (~700 ms) e, nos gráficos, 1 s real inicial; o helper não faz nada quando nenhum modo do Roborazzi está ativo. É uma proteção geral de captura; o flake de gráficos foi eliminado na raiz pelo modelo síncrono (abaixo).

Goldens de C3-07: **nenhum golden novo**. A recuperação só aparece quando existe prova `COMPLETED` com janela ativa e o estado de demo não tem janela; `TodayRecoveryTest` cobre a linha “Recuperação após a prova · dia N de M · até dd/mm” no card e o downgrade da prescrição.

Gráficos síncronos (12/11/2026): `McLineChart`/`McBarChart` montam um `CartesianChartModel` com `LineCartesianLayerModel.build`/`ColumnCartesianLayerModel.build` e passam `model` ao `CartesianChartHost`. O Vico renderiza esse caminho de forma síncrona; o antigo `CartesianChartModelProducer` aplicava o modelo em outro dispatcher e o Roborazzi podia capturar eixos sem série (`health-charts-dark`, `health-recovery-dark`). O componente não expõe mais `mc-chart-loading`: `mc-chart-ready` está presente desde a primeira composição. `ChartRenderTest` compõe linha e barra com uma cor exclusiva e exige pixels dessa cor após `waitForIdle`, travando a regressão.

Goldens de calibração: `calibration-{dark,light}` cobrem a tela com a ação “Estimar pelos meus treinos” e o resultado Tier A; `calibration-tier-b-{dark,light}` rolam até o resultado Tier B e capturam o rótulo de estimativa, a copy honesta, VDOT/LTHR/ritmo e o salvamento explícito.

`McLineChart`/`McBarChart` expõem `mc-chart-ready` desde a primeira composição; testes de
screenshot podem esperar pela tag para garantir que o gráfico entrou na árvore. O gate visual
roda em invocação própria (`verifyRoborazziDebug --tests "*ScreenshotTest"`), porque a suíte
inteira pode disputar frames com o Compose/Roborazzi.

## 9. Health Connect

- Interface `HealthConnectSource` + fake para testes.
- Testar: permissões negadas, app ausente, dados vazios, dados parciais, registro duplicado, fuso.
- Nunca usar dados reais no CI.
- Teste manual documentado no `RUNBOOK.md` com o dispositivo do usuário.

## 10. CI

```yaml
- ./gradlew spotlessCheck check :core:domain:koverVerify :app:assembleDebug
- ./gradlew :app:verifyRoborazziDebug --tests "*ScreenshotTest"
```

- JDK 17, SDK 37, build-tools 37.
- Cache Gradle.
- `check` cobre a lógica; o gate de goldens é a invocação própria de `verifyRoborazziDebug`
  com os testes de screenshot, para não disputar frames com a suíte lógica.
- Relatórios de teste, Kover e lint como artifacts em toda execução (`if: always()`); o diff do Roborazzi também é publicado.
- **Não há** nightly de emulador nem `connectedDebugAndroidTest`; instrumentados dependem de decisão de distribuição (TEST-01).
- Branch protection: CI verde obrigatório.

## 11. Cobertura

| Módulo | Meta | Verificação |
|---|---|---|
| `core:domain` | ≥ 85% linhas (atual 97,56%) | Kover `:core:domain:koverVerify` |
| Pacote `parser.fit` | ≥ 90% branches (atual 93,01%) | `verifyParserBranchCoverage` (XML do Kover) |
| `core:model` | ≥ 90% (**proposta**, sem gate) | Não configurado |
| `core:data` | ≥ 75% (**proposta**, sem gate) | Não configurado |
| `app`/features | ≥ 60% (**proposta**, sem gate) | Não configurado |

Só `core:domain` e o pacote FIT têm gate bloqueante hoje. As demais metas são
intenção de roadmap (T-03), não promessa cumprida. Exclusões permitidas: código
gerado, `data class` triviais, `MainActivity`, previews. Toda exclusão é
justificada no build.

## 11.1 Teste manual de notificações

Em aparelho/emulador API 33+:

1. Instalar o APK e aceitar a permissão de notificações.
2. Abrir o app e confirmar o canal "Lembretes de treino" em Configurações → Apps → MeuCoach → Notificações.
3. Com check-in pendente e um treino `PLANNED` para hoje, disparar os workers:
   `adb shell cmd jobscheduler run -f com.meucoach.app <job-id>` ou aguardar 7h/18h.
4. Tocar na notificação de treino: deve abrir Hoje.
5. Tocar na notificação de check-in: deve abrir o check-in.
6. Fazer o check-in e confirmar que o lembrete seguinte não aparece.

## 12. Definition of Done

Uma tarefa só termina quando:

- [ ] Código compila (`assembleDebug`).
- [ ] Testes novos cobrem o comportamento e falham sem a correção.
- [ ] `./gradlew check` verde.
- [ ] `./gradlew spotlessCheck` verde.
- [ ] `koverVerify` verde (quando aplicável).
- [ ] Lint sem erros.
- [ ] Documentação atualizada (se mudou regra, dado ou UX).
- [ ] `docs/PROGRESS.md` atualizado.
- [ ] Nenhum dado real de saúde no diff.

## 13. Comandos

```bash
./gradlew test
./gradlew :core:domain:test --tests "*Readiness*"
./gradlew check
./gradlew spotlessApply
./gradlew koverHtmlReport
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest   # device/emulador
```

## 14. Riscos de teste

| Risco | Mitigação |
|---|---|
| Fixtures reais vazarem | `.gitignore`, revisão manual, script de sanitização |
| Parser FIT frágil | Golden + referência + fuzz leve |
| Fórmulas com erro sutil | Casos à mão + referência independente |
| Screenshot flaky | Robolectric NATIVE, versão fixa, tolerância e espera por frame estável (`captureStableScreenshot`) |
| Testes lentos | Separar unit/instrumentado; cache |
| Cobertura performática | Focar em branches críticos, não em getters |
