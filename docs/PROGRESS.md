# PROGRESS.md — Diário de progresso

**Atualizado:** 28/10/2026

## Estado geral

- **Fase:** 0 — Fundação
- **Build:** verde (`./gradlew check :app:assembleDebug`)
- **Testes:** 53 testes, 0 falhas
- **Cobertura `core:domain`:** 100% de linhas, 94,23% de branches, 99,24% de instruções
- **Lint:** 0 erros; 2 avisos intencionais (`targetSdk 36` e atualização do Kover)
- **Dados reais:** analisados, fora do repositório
- **Próximo marco:** `P0-05` — parser FIT com fixtures sanitizadas

## Sessão 2026-10-27/28 — Planejamento e fundação

### Concluído

- Entrevista de produto, ciência e design.
- Análise de 7 atividades reais do Amazfit Balance em `~/Documents/Rafa/Zepp/`.
- Health Connect validado para sono no dispositivo do usuário 0.
- Stack Android validada em projeto limpo:
  - AGP 9.4.1, Gradle 9.8.2, Kotlin 2.4.21, KSP 2.3.12, JDK 17.
  - Compose BOM 2026.09.00, Room 2.8.5, Hilt 2.60.1.
  - `compileSdk 37` necessário para `core-ktx 1.19.1`.
- Documentação criada:
  - `README.md`
  - `docs/PRODUCT.md`
  - `docs/ARCHITECTURE.md`
  - `docs/DATA_MODEL.md`
  - `docs/SPORTS_SCIENCE.md`
  - `docs/DESIGN_SYSTEM.md`
  - `docs/TESTING.md`
  - `docs/ROADMAP.md`
  - `docs/RUNBOOK.md`
  - `docs/DECISIONS.md` + 9 ADRs
  - `AGENTS.md`
- Tokens DTCG em `design/tokens/meucoach.tokens.json`.
- Bootstrap Gradle completo:
  - Wrapper 9.8.2, version catalog, módulos `:app`, `:core:model`, `:core:domain`, `:core:designsystem`.
  - Spotless/ktlint, Android Lint, Kover, Makefile, CI GitHub Actions.
- Modelo inicial:
  - `Distance`, `Pace`, `ActivitySummary`, `AthleteProfile`, enums e testes.
- Domínio inicial:
  - Zonas de FC por LTHR e zonas de pace por limiar, com testes de limite.
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

### Evidências

- `./gradlew check :app:assembleDebug` — sucesso.
- `./gradlew :core:domain:koverVerify` — sucesso.
- Testes: 10 em `core:model`, 40 em `core:domain`, 3 em `app`.
- Kover `core:domain`: 216/216 linhas, 245/260 branches.
- Android Lint: 0 erros, 2 avisos (`OldTargetApi` para targetSdk 36 e `NewerVersionAvailable` do Kover 0.9.11).
- APK debug: `app/build/outputs/apk/debug/app-debug.apk` (~20 MB).

### Decisões-chave da sessão

- FIT canônico; parser próprio com validação cruzada.
- Health Connect + check-in + CSV; Zepp direto só na Fase 3.
- Motor determinístico; LLM só para texto.
- Musculação fora do escopo; fadiga global no check-in.
- Sem SQLCipher/biometria no MVP.
- Dark-first com lima como único accent; lima como texto no claro usa `#4F6600`.
- Backend Ktor/Postgres/Dokploy na Fase 2.

### Em andamento

- Nada bloqueado. Fundação pronta para o parser FIT.

### Próximos passos

1. Iniciar `P0-05`: decodificador FIT com CRC, endianness e compressed timestamps.
2. Sanitizar fixtures dos 7 arquivos reais e comparar com `fitdecode`.
3. Cobrir erros (truncado, CRC inválido, definição inválida) e golden tests.
4. Fechar `P0-10`: Robolectric/Roborazzi e relatórios no CI.

### Riscos/observações

- O parser FIT é o maior risco técnico; reservar tempo para testes de referência.
- A plataforma 37 e build-tools 37 precisam estar no CI.
- Dados reais do Zepp não podem entrar no git.
- O build local usa JDK 17; Gradle 9.8 também roda em 27, mas CI fixa 17.
- `targetSdk 36` é intencional nesta fase; o aviso do lint será reavaliado antes do release.

## Histórico

| Data | Marco |
|---|---|
| 27/10/2026 | Planejamento concluído; documentação e ADRs; stack validada |
| 28/10/2026 | Fundação verde: build, 53 testes, Kover, lint e tela Hoje verificada em dark/light |
