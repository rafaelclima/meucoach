# AGENTS.md — Instruções para agentes de IA

Este arquivo é a memória operacional do projeto. Leia antes de alterar código.

## 1. Contexto

**MeuCoach** é um app Android nativo de coach de corrida, local-first e baseado em evidências. O usuário 0 é o Rafael (iniciante/intermediário, 5k ≈ 31 min, Natal/RN). O produto importa FIT/TCX/GPX, calcula carga e prontidão, e prescreve o próximo treino com rationale auditável.

Leia primeiro:

1. `README.md`
2. `docs/PRODUCT.md`
3. `docs/ARCHITECTURE.md`
4. `docs/SPORTS_SCIENCE.md`
5. `docs/TESTING.md`
6. `docs/PROGRESS.md` (estado atual)

## 2. Stack e comandos

- AGP 9.4.1, Gradle 9.8.2, Kotlin 2.4.21, KSP 2.3.12, JDK 17.
- compileSdk 37, targetSdk 36, minSdk 26.
- Compose BOM 2026.09.00, Material 3, Hilt 2.60.1, Room 2.8.5.
- Sempre use o wrapper: `./gradlew`, nunca um Gradle global.
- Comandos:
  - `./gradlew :app:assembleDebug`
  - `./gradlew check`
  - `./gradlew spotlessApply`
  - `./gradlew koverVerify`
  - `./gradlew :core:domain:test`
- SDK local: `local.properties` (ignorado). Platform 37 e build-tools 37.0.0.

## 3. Regras de arquitetura

1. `core:model` e `core:domain` são **Kotlin/JVM puro**. Não importar `android.*`, Compose, Room, Hilt ou rede.
2. A UI não calcula regra de treino. Cálculo fica em use case/domain.
3. Features não dependem de features; dependem de `core`.
4. Toda entrada externa (FIT, TCX, GPX, CSV, Health Connect, Zepp futuro) é um **adapter** que produz o modelo canônico.
5. Toda recomendação cita `ruleId`, `evidenceId` e `confidence`. Nada de números inventados.
6. O motor é **determinístico**. LLM, se um dia existir, só reescreve texto; nunca decide carga, pace ou plano.
7. Dado ausente é `null`, nunca `0`. Ausência reduz confiança; não vira recuperação ruim.
8. Não adicionar dependência sem justificar (peso, licença, manutenção). Preferir bibliotecas AndroidX/Kotlin estáveis.
9. Não usar `fallbackToDestructiveMigration`.
10. Não usar dynamic color do Material You; a marca manda.

## 4. Privacidade e dados

- **Nunca** commitar dados de saúde reais (.fit/.tcx/.gpx/.csv pessoais, prints, exports).
- Dados reais ficam em `fixtures/private/` (gitignored) ou fora do repo.
- Fixtures commitadas: sanitizadas (posições inválidas, serial/IDs e nomes removidos) ou sintéticas.
- Não colar dados de saúde em issues, PRs, logs ou prompts.
- Backup Android desativado; export/delete explícitos.
- Sem analytics/ads no MVP.

## 5. Ciência e produto

- Fonte de verdade: `docs/SPORTS_SCIENCE.md`. Se o código divergir, atualize o doc **e** o código com justificativa.
- Não prescrever musculação, nutrição, emagrecimento ou diagnóstico. Única exceção: copy educativa de hidratação, estática e sem números, conforme ADR 0029.
- Força é rotina do usuário; o app só captura fadiga global no check-in.
- Esteira é modalidade válida para qualidade/calor.
- Toda regra nova precisa de evidência e teste.
- Conservador por padrão: sem dados, não aumentar carga.

## 6. Design

- Fonte de verdade: `docs/DESIGN_SYSTEM.md` + `design/tokens/meucoach.tokens.json`.
- Dark-first, light completo. Lima `#D8FF3E` é o único accent de ação. Coral é fisiologia.
- Sem hex solto em telas; usar tokens do `core:designsystem`.
- Números tabulares, contraste AA, alvos de 48 dp, TalkBack.
- Não criar novos componentes sem antes verificar se existe no design system.
- Gráficos: uma pergunta por gráfico, eixos com unidade, sem pizza.

## 7. Testes

- TDD quando possível: escreva o teste que falha, depois o código.
- Todo bug corrigido ganha teste de regressão.
- `core:domain` ≥ 85% de linhas (Kover).
- Parser FIT ≥ 90% de branches + golden com fixtures reais sanitizadas.
- Rode `./gradlew check` e `./gradlew spotlessCheck` antes de concluir.
- Não usar dados reais no CI.
- Testes de UI: estados loading/empty/error, acessibilidade e temas.
- Se um teste for flaky, conserte ou remova com justificativa; não ignore.

## 8. Estilo de código

- Kotlin oficial, 4 espaços, max line 120, trailing comma.
- Nomes claros em inglês para código; textos de UI em pt-BR.
- Value classes para unidades (`Pace`, `Distance`).
- `data class` imutável; sem setters públicos.
- Funções puras no domínio; efeitos colaterais nas bordas.
- Evitar `!!`; usar `require`/`check` com mensagens claras.
- Sem comentários óbvios; comentar decisões e fórmulas.
- Commits: Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`, `chore:`).

## 9. Definition of Done

- [ ] Compila (`assembleDebug`).
- [ ] Testes novos e existentes verdes.
- [ ] `spotlessCheck` verde.
- [ ] `koverVerify` verde quando aplicável.
- [ ] Lint sem erros.
- [ ] Documentação atualizada (regra/dado/UX).
- [ ] `docs/PROGRESS.md` atualizado.
- [ ] Nenhum dado real no diff.
- [ ] Nenhum TODO crítico escondido.

## 10. Protocolo de sessão

1. Leia `docs/PROGRESS.md` e o roadmap.
2. Escolha **uma** tarefa pequena e verificável.
3. Se a tarefa mudar arquitetura/ciência, crie/atualize um ADR.
4. Implemente com testes.
5. Rode build/check.
6. Atualize `PROGRESS.md` e `ROADMAP.md`.
7. Resuma o que mudou, o que ficou pendente e o próximo passo.

## 11. Context7

Use o MCP **Context7** para documentação atual de bibliotecas (Compose, Room, Hilt, Health Connect, Ktor, Vico) antes de usar APIs novas. Não confie só na memória do modelo. Para versões, confira Google Maven/Maven Central.

## 12. O que NÃO fazer

- Não implementar social, backend, widgets ou Zepp antes da fase definida.
- Não trocar a stack sem ADR.
- Não usar LLM para decidir treino.
- Não "consertar" teste reduzindo cobertura ou removendo assert.
- Não commitar keystore, `.env`, `local.properties` ou dados de saúde.
- Não reescrever documentos inteiros sem necessidade; edite com precisão.
- Não adicionar features de musculação/nutrição. A copy educativa de hidratação do ADR 0029 é conteúdo estático aprovado por gate, não uma feature de nutrição.
