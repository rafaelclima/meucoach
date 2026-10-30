# MeuCoach

Coach de corrida pessoal, **local-first** e baseado em evidências.

> Importa treinos de vestíveis, cruza carga, prontidão e desempenho, e prescreve o próximo treino com um "por quê" auditável.

## Status

| Item | Estado |
|---|---|
| Planejamento de produto, ciência, dados e design | Concluído em 27/10/2026 |
| Fundação técnica | `check` verde, 309 testes, parsers FIT/TCX/GPX, dedup, Room v4 + perfil, importação real (SAF/WorkManager), detalhe da atividade, onboarding, goldens e screenshots (30/10/2026) |
| Fase atual | **Fase 0 — Fundação** |
| App | Android nativo — Kotlin + Jetpack Compose + Material 3 |
| Backend | Fase 2 — Kotlin/Ktor + PostgreSQL + Dokploy |
| Dados diários | Health Connect (sono já validado) + check-in + CSV |
| Integração Zepp direta | Fase 3, experimental |

## Princípios

1. **Evidência antes de opinião.** Cada recomendação carrega regra, fonte e nível de confiança. O motor é determinístico; LLM, se existir, só explica — nunca inventa números.
2. **Dado ausente não é dado ruim.** O coach funciona em níveis de confiança e nunca bloqueia por falta de HRV ou sono.
3. **Local-first.** O app funciona 100% offline. A nuvem é conveniência, não dependência.
4. **Proveniência.** Todo dado importado sabe de onde veio, quando e em qual formato.
5. **Segurança conservadora.** Na dúvida, menos carga. Sem diagnóstico médico.
6. **Uma coisa bem feita.** Corrida. Musculação é rotina do usuário e fica fora do escopo do app.

## Stack verificada (out/2026)

- AGP 9.4.1 · Gradle 9.8.2 · Kotlin 2.4.21 · KSP 2.3.12 · JDK 17
- compileSdk 37 · targetSdk 36 · minSdk 26
- Compose BOM 2026.09.00 · Material 3 · Activity 1.13.0 · Lifecycle 2.11.0
- Room 2.8.5 · Hilt 2.60.1 · DataStore 1.3.0 · WorkManager 2.12.0
- Health Connect 1.1.0 · Vico 3.3.1
- Testes: JUnit 4.13.2 · AssertJ 3.27.7 · MockK 1.14.11 · Turbine 1.2.1 · Kotest 6.2.6 · Robolectric 4.17 · Roborazzi 1.77.0
- Qualidade: Detekt 1.23.8 · Spotless 8.10.4 · Kover 0.9.9

Versões ficam em `gradle/libs.versions.toml`. Consulte a documentação oficial via Context7 antes de atualizar APIs.

## Quick start

Pré-requisitos: JDK 17+, Android SDK com **platform 37** e **build-tools 37.0.0**, e um dispositivo/emulador API 26+.

```bash
git clone https://github.com/rafaelclima/meucoach.git
cd meucoach
cp local.properties.example local.properties   # ajuste sdk.dir
./gradlew :app:assembleDebug
./gradlew check
./gradlew :app:installDebug
```

No Android Studio: abra a raiz do repositório e rode a configuração `app`.

## Estrutura

```
.
├── app/                     # entry point, navegação, DI
├── core/
│   ├── model/               # tipos puros (distância, pace, FC, atividade)
│   ├── domain/              # motores: zonas, carga, prontidão, prescrição, parsers
│   ├── data/                # Room, repositórios, import, Health Connect
│   ├── designsystem/        # tokens, tema, componentes, gráficos
│   └── testing/             # fixtures, fakes, regras de teste
├── feature/                 # telas por feature (a partir da Fase 1)
├── docs/                    # produto, arquitetura, ciência, design, testes, roadmap
├── design/tokens/           # tokens DTCG
├── fixtures/                # fixtures sanitizadas (nunca dados reais)
├── infra/                   # Docker Compose e Dokploy (Fase 2)
└── .github/workflows/       # CI
```

## Documentação

- [Produto](docs/PRODUCT.md)
- [Arquitetura](docs/ARCHITECTURE.md)
- [Modelo de dados](docs/DATA_MODEL.md)
- [Ciência do esporte](docs/SPORTS_SCIENCE.md)
- [Design system](docs/DESIGN_SYSTEM.md)
- [Testes](docs/TESTING.md)
- [Roadmap](docs/ROADMAP.md)
- [Runbook](docs/RUNBOOK.md)
- [Decisões (ADRs)](docs/DECISIONS.md)
- [Instruções para agentes](AGENTS.md)
- [Diário de progresso](docs/PROGRESS.md)

## Dados privados

Nunca commite arquivos de saúde reais. Exportações do Zepp, FIT/TCX/GPX pessoais e CSV diários devem ficar em `fixtures/private/` (ignorado pelo git) ou fora do repositório. Fixtures de teste são **sanitizadas** (posições inválidas, serial/IDs e nomes removidos) ou sintéticas. Nunca commite os originais.

## Aviso

MeuCoach é uma ferramenta de apoio ao treinamento e não substitui avaliação médica. Se houver dor, doença ou sinal de alerta, interrompa o treino e procure um profissional.
