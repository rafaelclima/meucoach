# PRD — IA Coach de Corrida (MVP)

**Versão:** 0.1 (rascunho pós-questionário) · **Data:** 18/11/2026 · **Status:** em implementação (F1/F4 + 3.2 + 3.3-A/B/C + Room v19 + histórico com apagar individual prontos)
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
| 16 | Persistência | Room v19 (conversa/resumo/memória), com migração, TTL de 90/180 dias (§10), export/delete |
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
- Replanejo de imprevisto: "perdi treino X" / "só tenho 30min dia Y" (aceita vários dias e `0` remove o dia) / "trocar dia A por B" → `PlanReplanner` + confirmação + diff + undo.
- Aba `Coach` com histórico da conversa + resumo persistido.
- Proxy mínimo na VPS (OpenRouter), opt-in, Room v19.

Fora do MVP (pós-MVP):
- 3.4 disponibilidade futura por conversa, 3.5 check-in inteligente, 3.6 aderência contínua, §4 memória longitudinal completa, §5 iniciativa plena.
- Modelo local, BYOK, analytics, social, Zepp, widgets, Wear OS.

## 3. Arquitetura proposta (a validar no plano técnico)

- `domain` continua puro: `CoachPromptBuilder` (puro, monta JSON minimizado) + `CoachResponseValidator` (puro, barra número alucinado) + `CoachMemorySummarizer` (puro, define campos do resumo; pós-MVP — o MVP persiste a narrativa validada da revisão).
- `data`: `CoachRepository` + `CoachApi` (Ktor/OkHttp p/ proxy VPS) + Room v19 (`coach_messages`, `coach_summaries`) + `MIGRATION_18_19` sem `fallbackToDestructiveMigration`.
- `app`: `CoachViewModel` (gate `HealthSyncGate`-like p/ IA, cache por `inputsHash`), navegação com aba central `Coach`, `Perfil` realocado (decisão UX pendente: top-bar vs submenu).
- Motor autoridade: explicação usa `rationale`/`ruleIds` existentes; replanejo chama `EvaluateReplan`/`ApplyReplan` existentes; IA nunca emite `ruleId` novo.
- Prompt: system com proibições (sem diagnóstico, sem nutrição/musculação, dado ausente=null, estimativa só textual) + JSON de entrada minimizado + saída em schema fechado `{title, body, bullets[], suggestedAction?}`.
- Fallback: timeout/erro/schema inválido → exibe copy determinística atual, sem quebrar Hoje.

## 4. UX aba Coach (MVP)

- Bottom bar: Hoje · Corridas · **Coach** (centro) · Tendências · Saúde; Perfil sai da bar → destino via avatar/config no topo (a definir com melhores práticas, sem quebrar deep links `meucoach://profile`).
- Tela Coach: resumo da semana (IA ou fallback) + revisão anterior + lista de conversas + ações rápidas ("Explicar semana", "Perdi um treino", "Só tenho 30 min"). A lista de histórico é somente leitura, aparece só com opt-in e permite apagar o histórico em bloco ou item a item; o input livre continua fora do MVP.
- Replanejo: bottom-sheet diff (antes/depois) + Confirmar/Desfazer; reusa copy e selo `v{n}` do replanejamento.

## 5. Guardrails / segurança

- System prompt + pós-validação: bloqueia diagnóstico, prescrição de musculação/nutrição (exceto hidratação estática), dor ≥3/lesão/doença → interrompe + sugere avaliação + registra `ILLNESS_OR_INJURY`.
- Números: qualquer pace/FC/carga/volume/VDOT na resposta que não exista no input → resposta descartada, fallback determinístico.
- Estimativa permitida: só texto qualitativo ("parece que a rotina apertou") com marcador `estimativa`; números nunca estimados.
- Privacidade: opt-in explícito; payload minimizado (sem GPS/nome); revogar apaga `coach_*`; export inclui `coach_*`; risco retenção/região registrado como aceito.
- Retenção local: mensagens do Coach expiram em 90 dias e resumos em 180 dias; a limpeza é preguiçosa (abertura/escrita), sem worker novo e sem alterar treino.

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

O fatiamento proposto foi concluído até F3: F1 proxy/contrato, F2 prompt/validator, F3 Room v19 (memória), F4 aba Coach/ViewModel, F5 replanejo com diff/undo, F6 opt-in/docs/ADR, a lista visual de histórico (decisões 17/25, com apagar individual) e a 3.3-C (dia inteiro/múltiplos dias). Restam os goldens da bar.

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
- 3.3-B disponibilidade temporária e treino perdido específico: `ReplanIntent`/`TemporaryAvailability` puros entram no `ReplanInput`, no `inputsHash` e na `ReplanSuggestion`. “Só tenho 30 min no dia Y” reduz só um dia futuro e apenas no perfil efetivo usado pelo gerador/validação; nunca grava em `AthleteProfile` nem em `weeklyPlans.availabilityHash`. “Perdi o treino X” lista `PlanReplanner.eligibleMissedSessions` e força o T1 mesmo com 1 sessão, sem repor carga. `ApplyReplan` reconstrói o input com `suggestion.intent`; intent inválido/alterado vira `Stale`/`NoChange` com a sugestão nova. Pickers no Coach (treino perdido e dias futuros + minutos com validação local), `requestNote` no sheet e `ReplanRequestQueue` preservando o intent de um pedido enfileirado. Sem migração; schema v18. Testes: domínio (6), `EvaluateReplan` (2), `ApplyReplan` (2), Coach VM (3), fila (3), tela (5).
- F3/Room v19 memória local: `coach_messages` (turnos validados, único por `kind + inputsHash`) e `coach_summaries` (uma narrativa validada por semana fechada), `MIGRATION_18_19` aditiva e schema v19. TTL 90/180 dias com expiração preguiçosa; revogar o opt-in bloqueia chamadas e apaga as duas tabelas em escopo de aplicação; export/delete incluem `coach_*`. Nenhuma chamada de IA nova; a memória não entra em prompt nesta fatia e nunca decide carga, pace ou plano. Testes: política de TTL (3), repositório (6), migração 18→19 (1), export/delete (2), ViewModel (4) e exclusão na tela Dados (1).
- Histórico do Coach (decisões 17/25): `CoachMessageDao.observeAll`/`CoachSummaryDao.observeAll` + `CoachMemoryRepository.observeMessages/observeSummaries` expõem a memória como Flow somente leitura, mais recente primeiro, para `CoachMemoryMessage`/`CoachMemorySummary` puros. `CoachHistoryState` cobre Hidden (sem opt-in), Loading, Empty, Ready e Error; a aba mostra resumos por semana e conversa, aviso de que o motor decide, retry no erro e “Apagar histórico” em bloco. `deleteMessage`/`deleteSummary` removem um item por vez com confirmação própria, mantendo o restante e sem tocar em treinos/perfil. TalkBack, dark/light e fonte 200% cobertos. Sem chamada de IA nova, sem schema novo e sem entrada em prompt. Testes: repositório (4), ViewModel (6) e tela (10).
- Apagar item individual do histórico: `CoachMessageDao.deleteById`/`CoachSummaryDao.deleteByWeek` e `CoachMemoryRepository.deleteMessage`/`deleteSummary` (retorno booleano, item inexistente é no-op) com confirmação por linha no `CoachHistoryCard`; o “Apagar histórico” em bloco permanece. Escopo de aplicação para a exclusão terminar mesmo se a aba fechar; treinos/perfil intactos. Testes: repositório (2), ViewModel (2) e tela (2).
- 3.3-C disponibilidade temporária com dia inteiro/múltiplos dias: `TemporaryAvailability` aceita `0` (dia removido) e `ReplanIntent.temporaryAvailability` é uma lista ordenada no `canonical` (datas duplicadas recusadas). `PlanReplanner` valida cada dia (futuro da semana, existente, só redução), aplica todas as entradas no perfil efêmero e explica remoção/vários dias; `AthleteProfile` e `availabilityHash` não mudam. `ReplanController.replanLessTime` recebe `LessTimeChange` (lista), e o `LessTimeSheet` permite selecionar vários dias com um valor único (`0` remove). Testes: domínio (3), ViewModel (1) e tela (1).

## 10. Memória local e retenção (Room v19)

A memória do Coach é **local e opcional**. Ela existe para dar continuidade à
conversa e ao resumo semanal, não para decidir treino.

| Tabela | Conteúdo | Chave | TTL |
|---|---|---|---|
| `coach_messages` | turno validado da explicação (`EXPLAIN`) ou da revisão (`REVIEW`) | `id`; único em `(kind, inputsHash)` | 90 dias |
| `coach_summaries` | narrativa validada da semana fechada | `weekStart` | 180 dias |

Regras:

- Só entra texto que o `CoachResponseValidator` aceitou; resposta crua, prompt,
  timeout e fallback determinístico não viram memória.
- A memória não guarda nome, GPS, timestamps exatos de atividade nem texto livre
  do atleta.
- A expiração é preguiçosa (`CoachMemoryPolicy` + `CoachMemoryRepository`): roda
  na abertura da aba e antes de cada escrita; não há worker novo.
- Revogar o opt-in bloqueia chamadas na hora e apaga `coach_messages` e
  `coach_summaries`; treinos, perfil, check-ins e métricas não são tocados.
- “Apagar tudo” e o export incluem as duas tabelas; a tela Dados informa quantas
  mensagens/resumos do Coach foram removidos.
- A aba Coach lista o histórico somente leitura apenas com opt-in; “Apagar
  histórico” usa o mesmo `clearAll` e `deleteMessage`/`deleteSummary` removem um
  item por vez, sem tocar em treinos, perfil, check-ins ou métricas.
- Quando a memória for usada em prompt (pós-MVP), ela será apenas contexto de
  copy: todo texto volta ao validador e nenhum número nasce dela.
