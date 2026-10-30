# ROADMAP.md — MeuCoach

**Versão:** 1.1 · **Data:** 30/10/2026

> Sem prazo. O critério é qualidade e confiança, não velocidade. Cada fase termina com build verde, testes e documentação atualizada.

## Visão de fases

| Fase | Objetivo | Status |
|---|---|---|
| **0 — Fundação** | Repositório, build, CI, design system, modelo, domínio inicial, import e detalhe de atividade | **Concluída em 30/10/2026** |
| **1 — Coach** | Perfil, calibração, carga, prontidão, prescrição, Hoje, Tendências, Health Connect | **Em andamento** |
| **2 — Nuvem e extensões** | Backend Ktor/Postgres, sync, Dokploy, widgets, mapas, release | Planejada |
| **3 — Experimental** | Social, conector Zepp, explicações com LLM | Planejada |

---

## Fase 0 — Fundação

### P0-01 · Documentação e decisões

**Status:** concluído em 27/10/2026

- [x] Produto, arquitetura, dados, ciência, design, testes, roadmap, runbook.
- [x] ADRs iniciais.
- [x] Tokens DTCG.
- [x] Avaliação inicial do usuário 0 com dados reais.

**Aceite:** documentação revisável, decisões rastreáveis, nenhum dado real no repo.

### P0-02 · Bootstrap do repositório

**Status:** concluído em 28/10/2026

- [x] Stack validada em projeto limpo (AGP 9.4.1, Gradle 9.8.2, Kotlin 2.4.21, Room, Hilt, KSP).
- [x] Version catalog, wrapper, `.gitignore`, `.editorconfig`.
- [x] Módulos `:app`, `:core:model`, `:core:domain`, `:core:designsystem`.
- [x] `spotless`, lint Android, Kover.
- [x] CI GitHub Actions.
- [x] Makefile.

**Aceite:** `./gradlew check assembleDebug` verde em clone limpo; CI configurado.

### P0-03 · Design system base

**Status:** concluído em 28/10/2026

- [x] Tokens em Kotlin (dark/light), tipografia Geist/Inter, shapes.
- [x] Componentes: `McCard`, `McPrimaryButton`, `McSecondaryButton`, `McStat`, `McConfidenceBadge`, `McGauge`, `McSectionHeader`, `McPill`, `McEmptyState`.
- [x] Previews dark/light da tela Hoje.
- [x] Tela Hoje de demonstração com dados estáticos.
- [x] Navegação inferior e shell do app.
- [x] Instalação e verificação visual em emulador API 35.

**Aceite:** app instala e mostra Hoje premium em dark/light; componentes sem hex solto.

### P0-04 · Modelo e domínio inicial

**Status:** concluído em 28/10/2026

- [x] `Pace`, `Distance`, `ActivitySummary`, `AthleteProfile`, enums.
- [x] Zonas de FC por LTHR + testes de limite.
- [x] Zonas de pace por limiar + testes.
- [x] TRIMP, rTSS, sRPE, CTL/ATL/TSB, ACWR + testes.
- [x] Prontidão v1 com pesos renormalizados, confiança e flags + testes.
- [x] Cobertura `core:domain` ≥ 85% de linhas (atual: 99,88%).

**Aceite:** cálculos conferem com casos à mão; nenhuma exceção inesperada; Kover verde.

### P0-05 · Parser FIT

**Status:** concluído em 28/10/2026

- [x] Decodificador do subconjunto FIT (file_id, record, lap, session, event, device_info, developer fields).
- [x] CRC, endianness, compressed timestamps.
- [x] Normalização de cadência (strides/min → passos/min).
- [x] Testes com as 4 fixtures FIT sanitizadas dos arquivos reais.
- [x] Comparação local com `fitdecode`.
- [x] Testes de erro (truncado, CRC, definição inválida).
- [x] Gate de cobertura de branches ≥ 90% para o pacote do parser.

**Aceite:** resumo e samples dos 4 FIT batem com a referência; parser não quebra com lixo. Os 3 TCX antigos ficam para o P0-06.

### P0-06 · Parser TCX e GPX

**Status:** concluído em 29/10/2026

- [x] TCX com laps, trackpoints, FC, cadência, posição (usar os 3 TCX antigos em `~/Documents/Rafa/`).
- [x] GPX com extensões comuns e fallback sem elevação.
- [x] Testes de XML malformado, namespaces, encoding.
- [x] Dedup entre formatos.

**Aceite:** FIT > TCX > GPX; mesma atividade não duplica; os 4 FIT e os 3 TCX reais abrem sem erro. Os 3 TCX foram sanitizados para `fixtures/tcx/`; GPX real não entrou no repo, e as extensões são cobertas por fixtures sintéticas.

### P0-07 · Persistência (Room)

**Status:** concluído em 30/10/2026

- [x] `core:data` com entidades `Activity`, `Lap`, `SampleBlob`, `ImportJob`.
- [x] DAOs + repositório.
- [x] Migração 1→2 de teste.
- [x] Blob comprimido com round-trip.
- [x] Backup desativado; export/delete básico.

**Aceite:** importar, listar, abrir e apagar atividade sem perder consistência. Entregue com Room v2, migração testada, `SampleBlobCodec` (`gzip-columnar-v1`), `ActivityRepository`, export JSON básico e 26 testes em `core:data`.

### P0-08 · Importação — CONCLUÍDO

- [x] File picker (SAF) + share intent.
- [x] Fila persistente (`WorkManager` 2.12.0, `HiltWorkerFactory`).
- [x] Progresso, erro, dedup, substituição `FIT > TCX > GPX`, alternates, restauração.
- [x] Tela `ActivitiesScreen`, `sourcesLabel` (`FIT · TCX · GPX`), histórico de import.
- [x] Testes de `ViewModel`, `Staging`, `Coordinator`, `ShareIntentReader`.

**Aceite verificado:** `./gradlew check :app:assembleDebug spotlessCheck` — 214 testes, 0 falhas; `core:domain` 98,91% / 86,12% branches; `core:data` 44 testes; share + picker funcionando.

**Aceite:** arrastar/selecionar 3 formatos da mesma corrida → 1 atividade, mensagem clara.

### P0-09 · Detalhe da atividade — CONCLUÍDO

- [x] Hero (distância, tempo, pace).
- [x] Gráfico pace × FC × tempo.
- [x] Splits, cadência, potência, elevação.
- [x] Rota (polyline estática offline).
- [x] "O que aprendemos" com `R-ACT-01`/`R-ACT-02`, evidência e confiança.
- [x] Zonas de FC por LTHR (dependem da calibração do P1-02).

**Aceite verificado:** `RealFixturesDetailTest` importa e abre os 7 treinos reais sanitizados sem erro; TCX confere distância, duração e cadência com o README; suíte verde.

### P0-10 · Harness de testes

**Status:** concluído em 30/10/2026

- [x] Fixtures sanitizadas + scripts (`tools/sanitize_fit.py`, 4 FIT; `tools/sanitize_tcx.py`, 3 TCX) e GPX sintético.
- [x] Golden scenarios iniciais (`G-LOAD-01`, `G-READY-01`…`04`).
- [x] Robolectric + Compose UI e Roborazzi (dark/light do detalhe).
- [x] CI com relatórios e artefatos.
- [x] `docs/PROGRESS.md` atualizado.

**Aceite:** suíte roda em < 2 min local; CI bloqueia regressão.

---

## Fase 1 — Coach

### P1-01 · Onboarding e perfil

**Status:** concluído em 30/10/2026

- [x] Formulário em 3 passos: dados pessoais, objetivo/disponibilidade e rotina.
- [x] Persistência local em Room v4 e edição preservando calibração.
- [x] Gate de primeiro uso e tela de resumo do perfil.
- [x] Validação por passo, mensagens em pt-BR e testes de ViewModel/UI.

**Aceite:** perfil completo é salvo e reaberto; ausência de dados não bloqueia o app depois do onboarding.

### P1-02 · Calibração

**Status:** concluído em 30/10/2026

- [x] Tier A/B/C por performance recente e treinos com FC+pace.
- [x] VDOT, pace de limiar e LTHR com fórmulas documentadas.
- [x] Tela de entrada/resultado, confiança e avisos.
- [x] Salvamento no perfil e resumo de calibração.
- [x] Zonas de FC no detalhe da atividade após calibrar.

**Aceite:** performance recente gera VDOT/LTHR/pace; ausência de dados não inventa valores; perfil mantém a calibração.

### P1-03 · Motor de carga

**Status:** concluído em 30/10/2026

- [x] Integração com atividades importadas e perfil calibrado.
- [x] TRIMP/rTSS sem soma no mesmo dia; CTL/ATL/TSB.
- [x] ACWR e monotonia como flags, com dias sem carga explícitos.
- [x] Tela Tendências com PMC em Canvas.
- [x] Golden `G-LOAD-02` do motor de 28 dias.

**Aceite:** série de carga bate com o golden; ausência de dados não gera ACWR falso; a UI não trata ACWR como meta.

### P1-04 · Prontidão

**Status:** em andamento — check-in e CSV concluídos em 30/10/2026

- [x] Check-in diário de 15 s (sono, fadiga, dor, estresse + doença/lesão).
- [x] Persistência por data e prontidão real na tela Hoje.
- [x] CSV diário (sono, RHR, HRV, SpO2) com importação no perfil.
- [ ] Health Connect (sono, RHR, HRV, SpO2).
- [ ] Diagnóstico de dados, histórico e confiança por componente.

### P1-05 · Prescrição
Plan engine, periodização, guardrails, calor, esteira, taper. Explicações com regras/evidências.

### P1-06 · Tela Hoje
Prontidão, treino, carga, check-in, última atividade. Ações: iniciar, trocar, concluir, pular com motivo.

### P1-07 · Tendências
PMC, volume, 80/20, pace@FC, consistência, recordes. Filtros por período.

### P1-08 · Check-in
15 s, Hooper, sono percebido, dor, stress. Histórico e impacto na prontidão.

### P1-09 · Health Connect
Permissões, sync incremental, WorkManager, diagnóstico e fallback.

### P1-10 · Explicabilidade
Bottom sheet "Por que este treino?", regras, evidências, confiança, dados usados/faltantes.

### P1-11 · Notificações
Treino do dia, check-in, import concluído. Sem culpa/streak.

### P1-12 · E2E e validação
Fluxo completo com dados reais; 4 semanas de uso; ajuste de pesos e paces.

---

## Fase 2 — Nuvem e extensões

- P2-01 Backend Ktor + Postgres + Flyway.
- P2-02 Auth + sync delta + conflitos.
- P2-03 Docker Compose local + Dokploy (TLS, backup, healthcheck).
- P2-04 Widgets (Glance).
- P2-05 Mapa interativo (MapLibre/OSM) se necessário.
- P2-06 Release APK assinado + GitHub Releases.
- P2-07 Open-Meteo (clima) com cache e fallback.
- P2-08 Macrobenchmark e baseline profiles.

## Fase 3 — Experimental

- P3-01 Conector Zepp não oficial (avaliar riscos e termos).
- P3-02 Social (opt-in, privacidade).
- P3-03 LLM apenas para reescrever explicações determinísticas, com validação.
- P3-04 Wear OS / companion.

---

## Backlog técnico

| ID | Item | Quando |
|---|---|---|
| T-01 | Convention plugins Gradle | quando houver > 6 módulos |
| T-02 | Detekt + baseline | Fase 1 |
| T-03 | Kover para Android modules | Fase 1 |
| T-04 | Property-based tests (Kotest) | Fase 1 |
| T-05 | Fuzz leve do parser FIT | Fase 0/1 |
| T-06 | Export JSON + ZIP | Fase 1 |
| T-07 | Migração para MapLibre | Fase 2 |
| T-08 | i18n (pt-BR base, en futuro) | Fase 2 |

## Critérios de saída do MVP

- [ ] Importa FIT/TCX/GPX e deduplica.
- [ ] Abre detalhe com gráficos e splits.
- [ ] Calcula carga e prontidão com confiança.
- [ ] Prescreve a semana e o treino do dia com rationale.
- [ ] Funciona offline e sem conta.
- [ ] Exporta e apaga todos os dados.
- [ ] Testes verdes, cobertura ≥ 85% no domínio.
- [ ] Nenhum dado real no repositório.
