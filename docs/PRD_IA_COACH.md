# PRD — IA Coach de Corrida (MVP)

**Versão:** 0.1 (rascunho pós-questionário) · **Data:** 18/11/2026 · **Status:** em implementação (F1/F4 + 3.2 + 3.3-A prontos)
**Visão base:** `docs/VISAO_PRODUTO_IA_COACH_CORRIDA.md` · **Fonte verdade:** código + `SPORTS_SCIENCE.md`
**MVP decidido:** 3.1 explicar semana + 3.2 revisão semana anterior + 3.3 replanejar imprevisto

## 1. Decisões travadas no questionário (18/11/2026)

| # | Tema | Decisão |
|---|---|---|
| 1 | MVP | 3.1 + 3.2 + 3.3 |
| 2 | Ordem | Pode reordenar por dependência técnica |
| 3 | Fora de escopo | Mantém tudo fora do MVP; liberado backend/proxy mínimo só p/ OpenRouter |
| 4 | Premissas | Corrigir visão pelo código onde divergir |
| 5 | Modelo | OpenRouter free = placeholder; critério técnico (pt-BR, latência, JSON mode, contexto) |
| 6 | Chave | Proxy backend na VPS, chave fora do APK |
| 7 | Local | Só estudo futuro |
| 8 | Latência/custo | Timeout ~10s, 1 retry, cache por `inputsHash`/`engineVersion` |
| 9 | Dados enviados | Agregados + atividades (volume, adesão, rationale, regras), sem GPS bruto/nome/timestamps exatos |
| 10 | Consentimento | Opt-in explícito; revogar apaga histórico IA; entra em export/delete |
| 11 | Retenção/região | **Risco aceito:** MVP sem declarar retenção/região (PRODUCT §8 já declara VPS EUA p/ sync, mas não p/ IA) |
| 12 | Sensíveis | Só bloqueio de conteúdo (sem diagnóstico, sem replanejar por cima de dor/doença) |
| 13 | Módulo | Sugestão técnica: manter `domain` puro; rede/persistência em `data`, UI em `app` |
| 14 | Contrato | IA só reescreve texto do motor; replanejo via motor existente + confirmação |
| 15 | Validação | Schema + fallback: barra número fora do plano/prescrição, cai p/ copy determinística |
| 16 | Persistência | Room v19 (conversa/resumo/memória), com migração, TTL, export/delete |
| 17 | UX | Nova aba central `Coach`; `Perfil` realocado; histórico + conversa desde MVP |
| 18 | Tom | pt-BR treinador sem culpa |
| 19 | Estados | Padrão MeuCoach (loading/empty/error/offline, TalkBack, fonte 200%, dark/light) |
| 20 | Proibições | Mantidas + hidratação estática ADR 0029 |
| 21 | Dor/doença | Interrompe e encaminha, registra motivo, não replaneja por cima |
| 22 | Auditabilidade | `ruleId`/`evidenceId`/`confidence` só em log, UI limpa |
| 23 | Dado ausente | **Fechado pós-conflito:** estimativa só textual qualitativa com aviso; nunca estima número (pace/FC/carga/volume/VDOT) |
| 24 | Memória | Resumo LLM persistido |
| 25 | Controle memória | Vê e apaga, sem edição |
| 26 | Intenções | Perdido + tempo/dia com slots formais |
| 27 | Aplicação | Sempre confirma + diff + undo via `ApplyReplan` |
| 28 | Temporário | Nunca altera `AthleteProfile` sem aceite explícito separado |
| 29 | Check-in futuro | Complementa Hooper, fora do MVP |
| 30 | Iniciativa | Até 1/dia se relevante (moderado), com opt-out, sem culpa |
| 31 | Testes CI | **Fechado pós-conflito:** CI só com mock/goldens (determinístico); real só manual no app contra VPS |
| 32 | Métricas | Só qualitativo (feedback usuário 0) no MVP |
| 33 | Rollout | Feature flag + usuário 0 primeiro; ADRs + docs atualizados |

## 2. Escopo MVP

Dentro:
- Explicação da semana (a partir de `WeeklyPlan` + `rationale` + regras).
- Revisão semana anterior (planejado × executado via `PlanCompletionCalculator` + `WeeklyReviewCalculator`).
- Replanejo de imprevisto: "perdi treino X" / "só tenho 30min dia Y" / "trocar dia A por B" → `PlanReplanner` + confirmação + diff + undo.
- Aba `Coach` com histórico da conversa + resumo persistido.
- Proxy mínimo na VPS (OpenRouter), opt-in, Room v19.

Fora do MVP (pós-MVP):
- 3.4 disponibilidade futura por conversa, 3.5 check-in inteligente, 3.6 aderência contínua, §4 memória longitudinal completa, §5 iniciativa plena.
- Modelo local, BYOK, analytics, social, Zepp, widgets, Wear OS.

## 3. Arquitetura proposta (a validar no plano técnico)

- `domain` continua puro: `CoachPromptBuilder` (puro, monta JSON minimizado) + `CoachResponseValidator` (puro, barra número alucinado) + `CoachMemorySummarizer` (puro, define campos do resumo).
- `data`: `CoachRepository` + `CoachApi` (Ktor/OkHttp p/ proxy VPS) + Room v19 (`coach_messages`, `coach_summaries`) + `MIGRATION_18_19` sem `fallbackToDestructiveMigration`.
- `app`: `CoachViewModel` (gate `HealthSyncGate`-like p/ IA, cache por `inputsHash`), navegação com aba central `Coach`, `Perfil` realocado (decisão UX pendente: top-bar vs submenu).
- Motor autoridade: explicação usa `rationale`/`ruleIds` existentes; replanejo chama `EvaluateReplan`/`ApplyReplan` existentes; IA nunca emite `ruleId` novo.
- Prompt: system com proibições (sem diagnóstico, sem nutrição/musculação, dado ausente=null, estimativa só textual) + JSON de entrada minimizado + saída em schema fechado `{title, body, bullets[], suggestedAction?}`.
- Fallback: timeout/erro/schema inválido → exibe copy determinística atual, sem quebrar Hoje.

## 4. UX aba Coach (MVP)

- Bottom bar: Hoje · Corridas · **Coach** (centro) · Tendências · Saúde; Perfil sai da bar → destino via avatar/config no topo (a definir com melhores práticas, sem quebrar deep links `meucoach://profile`).
- Tela Coach: resumo da semana (IA ou fallback) + revisão anterior + lista de conversas + input + ações rápidas ("Explicar semana", "Perdi um treino", "Só tenho 30 min").
- Replanejo: bottom-sheet diff (antes/depois) + Confirmar/Desfazer; reusa copy e selo `v{n}` do replanejamento.

## 5. Guardrails / segurança

- System prompt + pós-validação: bloqueia diagnóstico, prescrição de musculação/nutrição (exceto hidratação estática), dor ≥3/lesão/doença → interrompe + sugere avaliação + registra `ILLNESS_OR_INJURY`.
- Números: qualquer pace/FC/carga/volume/VDOT na resposta que não exista no input → resposta descartada, fallback determinístico.
- Estimativa permitida: só texto qualitativo ("parece que a rotina apertou") com marcador `estimativa`; números nunca estimados.
- Privacidade: opt-in explícito; payload minimizado (sem GPS/nome); revogar apaga `coach_*`; export inclui `coach_*`; risco retenção/região registrado como aceito.

## 6. Estratégia lean de testes (acordo 18/11/2026)

Princípio: manter `check` verde e gates, sem bateria exaustiva. Usuário 0 testa E2E manual no aparelho contra VPS.

Obrigatórios (segurança do motor):
- `CoachPromptBuilderTest`: payload minimizado, sem GPS/nome, dado ausente=null (3-4 casos).
- `CoachResponseValidatorTest`: barra número inventado, aceita texto válido, fallback (5-6 casos).
- `MIGRATION_18_19` + round-trip `coach_*` (1-2 testes).
- `CoachViewModelTest` com fake API: loading/erro/fallback, confirmação de replanejo (3-4 testes).
- 1 teste de consentimento/opt-out + delete.

Cortados no MVP (sem prejuízo dos gates existentes):
- Sem goldens novos de plano/carga, sem matriz 360/393/fonte 200% nova, sem screenshot Roborazzi nova salvo 1 da aba Coach se trivial, sem property-based, sem instrumentado, sem teste E2E automatizado.
- Manual do usuário 0 cobre: explicação compreensível, revisão correta, replanejo com diff/undo, offline/timeout, revogação.

Gates mantidos: `assembleDebug`, `spotlessCheck`, `koverVerify` (domain/data/app sem baixar meta), detekt, lint. Sem dados reais no CI. Sem `fallbackToDestructiveMigration`.

## 7. Riscos

- Proxy VPS vira novo ponto de falha/custo; mitigação: cache + fallback determinístico + flag.
- Chave OpenRouter em VPS exige hardening mínimo (a definir no plano técnico).
- Risco LGPD aceito (sem declaração retenção/região) — revisitar antes de release além do usuário 0.
- Free tier instável/troca de modelo — mitigação: critério técnico + fallback entre modelos.

## 8. Próximo passo

Plano técnico fatiado (proposta): F1 proxy+VPS+contrato, F2 prompt/validator puros, F3 Room v19, F4 aba Coach + ViewModel, F5 replanejo com diff/undo, F6 flag/consentimento/docs/ADR. Cada fatia com gate mínimo acima + teste manual roteirizado.

## 9. Adendos implementados (18/11/2026)

- F1 domínio: `CoachPromptBuilder` + `CoachResponseValidator` + 7 testes verdes.
- Proxy: `infra/coach-proxy/` Ktor 3.1.2 stateless, `X-Coach-Token`, `/health`, sem log payload, Dokploy com TLS.
- Navegação: bar vira Hoje/Corridas/**Coach**/Tendências/Saúde; Perfil sai da bar, acessível via avatar no Hoje + engrenagem global (todas menos Hoje); rota `profile` e sub-rotas intactas; `meucoach://profile` nunca existiu (só `meucoach://data`).
- Config app: URL default no BuildConfig (VPS) + token em prefs com override (F1 restante).
- Opt-in: primeira abertura do Coach, texto simples sem tecnicês (tela a implementar).
- F4 explicação real: `CoachPlanSource` (plano vigente + inputsHash) + `CoachViewModel` (consentimento → Loading → Ready/Fallback com rationale do motor) + tela com retry/Desativar; `CoachApi` sempre provisionado quando configurado, VM decide chamar. 4 testes verdes.
- CoachApi implementado: `CoachApi`/`HttpCoachApi`/`Fake`/`Disabled` em `core:data` (HttpURLConnection, sem dep nova; `org.json` só em teste); `BuildConfig.COACH_PROXY_URL/_TOKEN` via `-PcoachProxyUrl/-PcoachProxyToken` (vazio = desativado + fallback); `AppModule` provisiona HTTP quando configurado, VM decide chamar (consentimento dinâmico). 4 testes verdes.
- Correções validadas no aparelho: retry morto (distinct descartava o tick; removido + teste); 403 do free abortava a cadeia (agora só 400/401/402/422 param); validador aceita total semanal + executado; system prompt proíbe siglas de métricas; layout (statusBars, sem padding duplo, fallback amigável, botões alinhados); loading centralizado em card. Cadeia atual: `super-vl` → `openrouter/free` → `inkling-2-small` (`OPENROUTER_MODELS` sobrescreve). App timeout 45s/10s.
- 3.2 revisão da semana anterior: `CoachReviewPromptBuilder` + `allowedMinutes`/`allowedPercents` (domínio puro, payload sem texto livre), checagem opt-in de porcentagem no validador, `CoachApi.review` em `/api/v1/coach/review`, `WeeklyReviewAssembler` compartilhado com Tendências, `CoachReviewSource` da última semana fechada, `CoachReviewState` no `CoachViewModel` (cache/fallback) e cartão na aba Coach com narrativa + fatos do motor. Testes: builder (4), validador (2), endpoint (1), VM (3), source (3), tela (3).
- 3.3-A replanejamento na aba Coach: card “AJUSTAR A SEMANA” com “Ajustar a semana”, “Perdi um treino” e “Tenho menos tempo” (T3 explícito), bottom sheet com diff e selo `v{n} → v{n+1}`, confirmação via `ApplyReplan` (stale reavalia e nunca aplica a sugestão velha), undo append-only (`trigger = UNDO`) que restaura só o futuro `PLANNED` e preserva hoje/passado/`DONE`/`CLOSED`, e `ReplanController` compartilhado por Hoje/Perfil/Coach (o `ReplanViewModel` virou fachada). A seleção de dia/slot e a disponibilidade temporária ficam para 3.3-B. Testes: repository undo (5), controller/VM (2), Coach VM (2), tela (3).
