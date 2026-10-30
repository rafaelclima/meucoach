# TESTING.md — MeuCoach

**Versão:** 1.2 · **Data:** 30/10/2026

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
| Property-based | Kotest Property (Fase 1) | Invariantes (monotonicidade, limites) | JVM, cada PR |
| Golden | JUnit + JSON | Cenários completos de carga/prontidão/plano | JVM, cada PR |
| Parser de referência | Python `fitdecode` (script) | Validação cruzada do parser FIT | Local/CI opcional |
| Banco | Room `MigrationTestHelper` | Migrações e queries | JVM/instrumentado |
| UI | Compose UI Test + Robolectric | Estados, acessibilidade básica | JVM, cada PR |
| Screenshot | Roborazzi | Regressão visual dark/light | JVM, cada PR (Fase 1) |
| Instrumentado | Compose UI Test + AndroidX Test | Fluxos reais, Health Connect fake | Emulador, nightly |
| E2E | Fluxo import → hoje | Aceitação do MVP | Emulador, release |

## 3. Estrutura de testes

```
core/model/src/test/          # value objects (pace, distância)
core/domain/src/test/
  zones/                      # FC, pace, distribuição
  load/                       # TRIMP, rTSS, CTL/ATL/TSB, ACWR
  readiness/                  # score, confiança, overrides
  prescription/               # plano, progressão, taper, guardrails
  parser/                     # FIT, TCX, GPX, CSV
  golden/                     # cenários JSON
app/src/test/                 # ViewModels, formatadores, screenshots
app/src/androidTest/          # navegação e fluxos com emulador
fixtures/
  fit/                        # fixtures sanitizadas/sintéticas
  tcx/
  gpx/
  csv/
  golden/                     # entradas e saídas esperadas
  private/                    # IGNORADO — dados reais
```

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

Evidência atual (30/10/2026):

- 425 testes no repositório, 0 falhas (27 em `core:model`, 176 em `core:domain`, 66 em `core:data`, 156 em `:app`).
- 4 FIT + 3 TCX reais sanitizados; 2 GPX sintéticos.
- `parser.fit`: 93,01% de branches (572/615).
- `core:domain`: 98,82% de linhas, 85,90% de branches, 96,88% de instruções.
- Comando: `./gradlew :core:domain:verifyParserBranchCoverage`.
- A task falha o build se o pacote FIT ficar abaixo de 90% de branches; ela roda em `check` e `koverVerify`.
- `ActivityDeduplicatorTest` cobre prioridade, tolerância de ±1 s/±2 s/±1% e escolha por samples.

## 5.1 Testes de persistência

- `MeuCoachDatabaseTest` (Robolectric): CRUD do agregado, lista observável, soft delete, cascata de laps/samples, `SET NULL` em import jobs e unicidade de `sourceFileSha256`.
- `MeuCoachDatabaseMigrationTest` (Robolectric + `MigrationTestHelper`): banco v1 com dados migra para v2, preserva a atividade e adiciona `deletedAt`.
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
- `TrainingLoadEngineTest`: TRIMP vs rTSS, CTL/ATL/TSB, ACWR, monotonia, janela e dias ausentes.
- `LoadEngineGoldenTest`: cenário `G-LOAD-02` de 28 dias.
- `TrendsViewModelTest` e `TrendsScreenTest`: carga a partir das atividades, estados vazios e PMC.
- `DailyCheckInTest`: validação 1–5 e Hooper sum.
- `CheckInRepositoryTest`: round-trip por data, upsert, exclusão e `createdAt`.
- `CheckInReadinessTest`: mapeamento e prontidão só com check-in.
- `CheckInViewModelTest` e `CheckInScreenTest`: prefill, salvamento, escalas e loading.
- `DailyMetricsCsvParserTest`: template, datas ISO/BR, aliases, erros por linha e duplicatas.
- `DailyMetricsRepositoryTest`: lote, atualização por data, `createdAt` e exclusão.
- `DailyMetricsReadinessBuilderTest`: janelas 7/30/60, HRV mínimo, sono recente e RHR elevada 2 dias.
- `MetricsImportViewModelTest` e `MetricsImportScreenTest`: importação, erros, duplicatas e template.
- `HealthMetricsAggregatorTest`: sono por data local, sobreposição, médias diárias e fuso.
- `HealthConnectViewModelTest`: status, permissões faltantes, sync, indisponibilidade e erro.
- `HealthConnectContentTest`: estados do card, autorização e sincronização.

## 6. Testes de domínio

### 6.1 Zonas

- Limites exatos: 80,9% / 81% / 89,9% / 90% / 94% / 100%.
- LTHR inválida → erro claro.
- Pace de limiar → 5 zonas sem sobreposição nos alvos.
- Usuário 0: 170 bpm com LTHR 180 ⇒ Z4; 150 ⇒ Z2; 181 ⇒ Z5.

### 6.2 Carga

- TRIMP calculado à mão para 30 min, FC 150, repouso 58, máx 187.
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

Cenários de plano/prescrição (`G-001`…`G-006`) entram quando
`PrescriptionEngine`/`PlanEngine` existirem: baseline conservador, progressão
verde, HRV baixa, taper, dados ausentes e calor.

Doubles comparados com tolerância `1e-6`; inteiros exatos. `engineVersion` entra
no expected quando o motor completo existir.

## 8. Testes de UI

- **Estados:** loading, empty, error, stale, success.
- **Acessibilidade:** `contentDescription` de gráficos, labels de botões, ordem de foco.
- **Formatação:** pace `7:40`, distância `5,11 km`, data pt-BR, unidade.
- **Interação:** trocar treino, concluir check-in, abrir "por quê".
- **Temas:** dark e light, fonte 100% e 200%.
- **Screenshot:** 3 tamanhos (360×800, 393×852, 412×915) × 2 temas, apenas telas-chave.

Ferramentas: Compose UI Test, Robolectric (JVM), Roborazzi (screenshot), AndroidX Test (instrumentado). Sem Espresso puro; Compose-first.

Screenshots atuais (Roborazzi + Robolectric Native Graphics):

- `app/src/test/screenshots/activity-detail-dark.png`
- `app/src/test/screenshots/activity-detail-light.png`
- `app/src/test/screenshots/onboarding-dark.png`
- `app/src/test/screenshots/onboarding-light.png`
- `app/src/test/screenshots/profile-dark.png`
- `app/src/test/screenshots/profile-light.png`
- `app/src/test/screenshots/calibration-dark.png`
- `app/src/test/screenshots/calibration-light.png`
- `app/src/test/screenshots/trends-dark.png`
- `app/src/test/screenshots/trends-light.png`
- `app/src/test/screenshots/checkin-dark.png`
- `app/src/test/screenshots/checkin-light.png`
`./gradlew :app:recordRoborazziDebug` atualiza os goldens; `./gradlew :app:verifyRoborazziDebug`
falha se a tela mudar. O CI roda a verificação e publica o diff em artifacts. Não há dados de saúde nas imagens (fixture sintética).

## 9. Health Connect

- Interface `HealthConnectSource` + fake para testes.
- Testar: permissões negadas, app ausente, dados vazios, dados parciais, registro duplicado, fuso.
- Nunca usar dados reais no CI.
- Teste manual documentado no `RUNBOOK.md` com o dispositivo do usuário.

## 10. CI

```yaml
- ./gradlew spotlessCheck
- ./gradlew check
- ./gradlew koverVerify
- ./gradlew :app:assembleDebug
```

- JDK 17, SDK 37, build-tools 37.
- Cache Gradle.
- Relatórios de teste, Kover e lint como artifacts em toda execução (`if: always()`).
- Nightly: `connectedDebugAndroidTest` em emulador API 35/36 (quando estável).
- Branch protection: CI verde obrigatório.

## 11. Cobertura

| Módulo | Meta | Verificação |
|---|---|---|
| `core:domain` | ≥ 85% linhas | Kover `koverVerify` |
| Pacote `parser.fit` | ≥ 90% branches | `verifyParserBranchCoverage` (XML do Kover) |
| `core:model` | ≥ 90% | Kover |
| `core:data` | ≥ 75% | Kover (meta a partir do P0-08) |
| `app`/features | ≥ 60% | Kover + UI tests (Fase 1) |

Exclusões permitidas: código gerado, `data class` triviais, `MainActivity`, previews. Toda exclusão é justificada no build.

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
| Screenshot flaky | Robolectric NATIVE, versão fixa, tolerância |
| Testes lentos | Separar unit/instrumentado; cache |
| Cobertura performática | Focar em branches críticos, não em getters |
