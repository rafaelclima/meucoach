# PROGRESS.md — Diário de progresso

**Atualizado:** 28/10/2026

## Estado geral

- **Fase:** 0 — Fundação
- **Build:** verde (`./gradlew check :app:assembleDebug`)
- **Testes:** 105 testes, 0 falhas (15 em `core:model`, 90 em `core:domain`)
- **Cobertura `core:domain`:** 99,88% de linhas (828/829), 93,37% de branches (817/875), 99,04% de instruções (6425/6487)
- **Parser FIT:** 100% de linhas e 93,01% de branches (572/615); gate próprio exige ≥ 90% de branches
- **Lint:** 0 erros; 2 avisos intencionais (`targetSdk 36` e atualização do Kover)
- **Dados reais:** 4 FIT sanitizados em `fixtures/fit/`; 3 TCX antigos permanecem fora do repositório
- **Próximo marco:** `P0-06` — parser TCX/GPX e deduplicação entre formatos

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
