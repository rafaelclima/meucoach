# PRD — IA Coach de Corrida (MVP)

**Versão:** 0.2 · **Data:** 26/11/2026 · **Status:** MVP implementado + conversa aberta + Fatia 7 de documentação (decisões 8/21 e escopo médico reconciliados; Room v22; validação no aparelho e redeploy do proxy pendentes)
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
| 8 | Latência/custo | Timeout real: **45 s** para explain/review no app (conexão limitada a 10 s) e **25 s** para `free-answer`; no proxy, **18 s por modelo** e **20 s de orçamento** do `free-answer` (MiMo+GLM em paralelo). Sem retry automático: o retry é manual e mira o exchange. Cache por `inputsHash`/`engineVersion`; qualquer falha cai no determinístico. |
| 9 | Dados enviados | Agregados + atividades (volume, adesão, rationale, regras), sem GPS bruto/nome/timestamps exatos |
| 10 | Consentimento | Opt-in explícito; revogar apaga histórico IA; entra em export/delete |
| 11 | Retenção/região | **Risco aceito:** MVP sem declarar retenção/região (PRODUCT §8 já declara VPS EUA p/ sync, mas não p/ IA) |
| 12 | Sensíveis | Só bloqueio de conteúdo (sem diagnóstico, sem replanejar por cima de dor/doença) |
| 13 | Módulo | Sugestão técnica: manter `domain` puro; rede/persistência em `data`, UI em `app` |
| 14 | Contrato | IA só reescreve texto do motor; replanejo via motor existente + confirmação |
| 15 | Validação | Schema + fallback: barra número fora do plano/prescrição, cai p/ copy determinística |
| 16 | Persistência | Room **v22** (`chat_turns`: conversa/resumo/memória), com migrações explícitas 1→22, TTL de 90/180 dias (§10), export/delete; as tabelas v19 são históricas (ADR 0055) |
| 17 | UX | Nova aba central `Coach`; `Perfil` realocado; histórico + conversa desde MVP |
| 18 | Tom | pt-BR treinador sem culpa |
| 19 | Estados | Padrão MeuCoach (loading/empty/error/offline, TalkBack, fonte 200%, dark/light) |
| 20 | Proibições | Mantidas + hidratação estática ADR 0029 |
| 21 | Dor/doença | **Fail-closed:** sintomas/condições de alerta (dor, lesão, doença, arritmia, palpitação, aperto no peito, desmaio, falta de ar etc.) são recusados **localmente, antes de qualquer chamada de rede**, com CTA “registre no check-in e procure avaliação profissional”. Não diagnostica, não promete resposta aberta e não replaneja por cima. |
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
- Aba `Coach` com histórico da conversa + resumo persistido em `chat_turns` (Room v22).
- Input livre com parser local e conversa aberta com fatos fechados (ADRs 0056/0057/0063): a pergunta digitada é sanitizada e só `intent + slots` ou a pergunta aprovada pela porteira vão ao proxy.
- Proxy mínimo na VPS (OpenRouter + OpenCode Go via cadeia com fallback), opt-in, sem log de payload e com fallback determinístico.

Fora do MVP (pós-MVP):
- 3.4 disponibilidade futura por conversa, 3.5 check-in inteligente, 3.6 aderência contínua, §4 memória longitudinal completa, §5 iniciativa plena.
- Modelo local, BYOK, analytics, social, Zepp, widgets, Wear OS.

## 3. Arquitetura proposta (a validar no plano técnico)

- `domain` continua puro: `CoachPromptBuilder` (puro, monta JSON minimizado) + `CoachResponseValidator` (puro, barra número alucinado) + `CoachMemorySummarizer` (puro, define campos do resumo; pós-MVP — o MVP persiste a narrativa validada da revisão).
- `data`: `CoachRepository` + `CoachApi` (HTTP nativo para o proxy VPS) + Room **v22** (`chat_turns`, tabela única desde a v21; `origin` nullable na v22 para `YOU`) + migrações explícitas 1→22, sem `fallbackToDestructiveMigration`. As tabelas v19 `coach_messages`/`coach_summaries` são históricas e foram removidas na 20→21 (ADR 0055).
- `app`: `CoachViewModel` (gate `HealthSyncGate`-like p/ IA, cache por `inputsHash`), navegação com aba central `Coach` e `Perfil` fora da barra (avatar no Hoje + engrenagem global).
- Motor autoridade: explicação usa `rationale`/`ruleIds` existentes; replanejo chama `EvaluateReplan`/`ApplyReplan` existentes; IA nunca emite `ruleId` novo.
- Prompt: system com proibições (sem diagnóstico, sem nutrição/musculação, dado ausente=null, estimativa só textual) + JSON de entrada minimizado + saída em schema fechado `{title, body, bullets[], suggestedAction?}`.
- Fallback: timeout/erro/schema inválido → exibe copy determinística atual, sem quebrar Hoje.

## 4. UX aba Coach (MVP)

- Bottom bar: Hoje · Corridas · **Coach** (centro) · Tendências · Saúde; Perfil sai da bar → destino via avatar/config no topo (a definir com melhores práticas, sem quebrar deep links `meucoach://profile`).
- Tela Coach: resumo da semana (IA ou fallback) + revisão anterior + lista de conversas + ações rápidas ("Explicar semana", "Perdi treino", "Menos tempo"). A explicação começa recolhida (5 linhas) e expande sob demanda; a lista de histórico é somente leitura, aparece só com opt-in e funciona como arquivo do que não está no card atual (sem repetir explicação/revisão correntes), com apagar em bloco ou item a item. O input livre foi entregue no pós-MVP (ADRs 0056/0057/0063).
- Replanejo: bottom-sheet diff (antes/depois) + Confirmar/Desfazer; reusa copy e selo `v{n}` do replanejamento.

## 5. Guardrails / segurança

- Porteira local + pós-validação (fail-closed): bloqueia diagnóstico, prescrição de musculação/nutrição (exceto hidratação estática) e sintomas/condições de alerta. Pergunta digitada sobre dor, lesão, doença, arritmia, palpitação, aperto no peito, desmaio ou falta de ar vira `OUT_OF_SCOPE` **antes da rede**, com CTA “registre no check-in do Hoje e procure avaliação profissional”; não há diagnóstico nem promessa de resposta aberta. No check-in, dor ≥3/lesão/doença continuam registrando `ILLNESS_OR_INJURY` e interrompem o treino; o Coach não replaneja por cima.
- Números: qualquer pace/FC/carga/volume/VDOT na resposta que não exista no input → resposta descartada, fallback determinístico.
- Estimativa permitida: só texto qualitativo ("parece que a rotina apertou") com marcador `estimativa`; números nunca estimados.
- Timeouts (decisão 8): explain/review 45 s no app (conexão 10 s), `free-answer` 25 s no app e 18 s por modelo/20 s de orçamento no proxy; sem retry automático, com fallback determinístico e retry manual por exchange.
- Privacidade: opt-in explícito; payload minimizado (sem GPS/nome); revogar apaga `chat_turns`; export inclui `chat_turns`; risco retenção/região registrado como aceito. Desde o consentimento v2 (ADR 0063), a pergunta digitada é enviada ao proxy somente após a porteira local e com um dossiê de fatos do motor; nome, GPS e timestamps exatos **do app** continuam fora. A emenda de 25/11 inclui só o resumo do executado do dia ou da última sessão concluída (duração, distância, RPE, evidência e veredictos) e a prescrição que ajustou o dia executado (plano × prescrição), sem arquivo bruto e sem pace/FC em prosa; os allow-lists cobrem todo número impresso pelo dossiê.
- Retenção local: mensagens do Coach expiram em 90 dias e resumos em 180 dias; a limpeza é preguiçosa (abertura/escrita), sem worker novo e sem alterar treino.

## 6. Estratégia lean de testes (acordo 18/11/2026)

Princípio: manter `check` verde e gates, sem bateria exaustiva. Usuário 0 testa E2E manual no aparelho contra VPS.

Obrigatórios (segurança do motor):
- `CoachPromptBuilderTest`: payload minimizado, sem GPS/nome, dado ausente=null (3-4 casos).
- `CoachResponseValidatorTest`: barra número inventado, aceita texto válido, fallback (5-6 casos).
- Migrações 1→22 + round-trip de `chat_turns` (cadeia v19→v22 coberta por `MeuCoachDatabaseMigrationTest`).
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

O MVP está implementado: F1 proxy/contrato, F2 prompt/validator, F3 memória (hoje em `chat_turns`, Room v22), F4 aba Coach/ViewModel, F5 replanejo com diff/undo, F6 opt-in/docs/ADR, a lista visual de histórico (decisões 17/25, com apagar individual), a 3.3-C (dia inteiro/múltiplos dias) e os goldens da bar. Evoluções pós-MVP entregues: input livre (ADRs 0056/0057), conversa aberta com fatos fechados e consentimento v2 (ADR 0063), lembrete fiel ao dia (ADR 0064) e dossiê do último executado (emenda ADR 0063). A Fatia 7 (26/11/2026) reconciliou este PRD com os timeouts reais e o escopo médico fail-closed; nenhuma feature nova. **Pendente:** redeploy do proxy no Dokploy com os prompts novos e validação no aparelho com token (Fatia 8). Continuam fora: memória como contexto de copy em prompt e “quero mais tempo”.

## 9. Adendos implementados (18/11/2026)

- F1 domínio: `CoachPromptBuilder` + `CoachResponseValidator` + 7 testes verdes.
- Proxy: `infra/coach-proxy/` Ktor 3.1.2 stateless, `X-Coach-Token`, `/health`, sem log payload, Dokploy com TLS.
- Navegação: bar vira Hoje/Corridas/**Coach**/Tendências/Saúde; Perfil sai da bar, acessível via avatar no Hoje + engrenagem global (todas menos Hoje); rota `profile` e sub-rotas intactas; `meucoach://profile` nunca existiu (só `meucoach://data`).
- Config app: URL default no BuildConfig (VPS) + token em prefs com override (F1 restante).
- Opt-in: primeira abertura do Coach, texto simples sem tecnicês (tela a implementar).
- F4 explicação real: `CoachPlanSource` (plano vigente + inputsHash) + `CoachViewModel` (consentimento → Loading → Ready/Fallback com rationale do motor) + tela com retry/Desativar; `CoachApi` sempre provisionado quando configurado, VM decide chamar. 4 testes verdes.
- CoachApi implementado: `CoachApi`/`HttpCoachApi`/`Fake`/`Disabled` em `core:data` (HttpURLConnection, sem dep nova; `org.json` só em teste); `BuildConfig.COACH_PROXY_URL/_TOKEN` via `-PcoachProxyUrl/-PcoachProxyToken` (vazio = desativado + fallback); `AppModule` provisiona HTTP quando configurado, VM decide chamar (consentimento dinâmico). 4 testes verdes.
- Correções validadas no aparelho: retry morto (distinct descartava o tick; removido + teste); 403 do free abortava a cadeia (agora só 400/401/402/422 param); validador aceita total semanal + executado; system prompt proíbe siglas de métricas; layout (statusBars, sem padding duplo, fallback amigável, botões alinhados); loading centralizado em card. Cadeia atual: `openrouter/free` → `super-vl` → `gemma-4-31b-it:free` (`OPENROUTER_MODELS` sobrescreve; `inkling-2-small` morreu com 404 e saiu da cadeia em 20/11/2026). App timeout 45s/10s.
- 3.2 revisão da semana anterior: `CoachReviewPromptBuilder` + `allowedMinutes`/`allowedPercents` (domínio puro, payload sem texto livre), checagem opt-in de porcentagem no validador, `CoachApi.review` em `/api/v1/coach/review`, `WeeklyReviewAssembler` compartilhado com Tendências, `CoachReviewSource` da última semana fechada, `CoachReviewState` no `CoachViewModel` (cache/fallback) e cartão na aba Coach com narrativa + fatos do motor. Testes: builder (4), validador (2), endpoint (1), VM (3), source (3), tela (3).
- 3.3-A replanejamento na aba Coach: card “AJUSTAR A SEMANA” com “Ajustar a semana”, “Perdi um treino” e “Tenho menos tempo” (T3 explícito), bottom sheet com diff e selo `v{n} → v{n+1}`, confirmação via `ApplyReplan` (stale reavalia e nunca aplica a sugestão velha), undo append-only (`trigger = UNDO`) que restaura só o futuro `PLANNED` e preserva hoje/passado/`DONE`/`CLOSED`, e `ReplanController` compartilhado por Hoje/Perfil/Coach (o `ReplanViewModel` virou fachada). A seleção de dia/slot e a disponibilidade temporária ficam para 3.3-B. Testes: repository undo (5), controller/VM (2), Coach VM (2), tela (3).
- 3.3-B disponibilidade temporária e treino perdido específico: `ReplanIntent`/`TemporaryAvailability` puros entram no `ReplanInput`, no `inputsHash` e na `ReplanSuggestion`. “Só tenho 30 min no dia Y” reduz só um dia futuro e apenas no perfil efetivo usado pelo gerador/validação; nunca grava em `AthleteProfile` nem em `weeklyPlans.availabilityHash`. “Perdi o treino X” lista `PlanReplanner.eligibleMissedSessions` e força o T1 mesmo com 1 sessão, sem repor carga. `ApplyReplan` reconstrói o input com `suggestion.intent`; intent inválido/alterado vira `Stale`/`NoChange` com a sugestão nova. Pickers no Coach (treino perdido e dias futuros + minutos com validação local), `requestNote` no sheet e `ReplanRequestQueue` preservando o intent de um pedido enfileirado. Sem migração; schema v18. Testes: domínio (6), `EvaluateReplan` (2), `ApplyReplan` (2), Coach VM (3), fila (3), tela (5).
- F3/Room v19 memória local (histórico; hoje unificado em `chat_turns` v22 — ver §10): `coach_messages` (turnos validados, único por `kind + inputsHash`) e `coach_summaries` (uma narrativa validada por semana fechada), `MIGRATION_18_19` aditiva e schema v19. TTL 90/180 dias com expiração preguiçosa; revogar o opt-in bloqueia chamadas e apaga as duas tabelas em escopo de aplicação; export/delete incluem `coach_*`. Nenhuma chamada de IA nova; a memória não entra em prompt nesta fatia e nunca decide carga, pace ou plano. Testes: política de TTL (3), repositório (6), migração 18→19 (1), export/delete (2), ViewModel (4) e exclusão na tela Dados (1).
- Histórico do Coach (decisões 17/25): `CoachMessageDao.observeAll`/`CoachSummaryDao.observeAll` + `CoachMemoryRepository.observeMessages/observeSummaries` expõem a memória como Flow somente leitura, mais recente primeiro, para `CoachMemoryMessage`/`CoachMemorySummary` puros. `CoachHistoryState` cobre Hidden (sem opt-in), Loading, Empty, Ready e Error; a aba mostra resumos por semana e conversa, aviso de que o motor decide, retry no erro e “Apagar histórico” em bloco. `deleteMessage`/`deleteSummary` removem um item por vez com confirmação própria, mantendo o restante e sem tocar em treinos/perfil. TalkBack, dark/light e fonte 200% cobertos. Sem chamada de IA nova, sem schema novo e sem entrada em prompt. Testes: repositório (4), ViewModel (6) e tela (10).
- Apagar item individual do histórico: `CoachMessageDao.deleteById`/`CoachSummaryDao.deleteByWeek` e `CoachMemoryRepository.deleteMessage`/`deleteSummary` (retorno booleano, item inexistente é no-op) com confirmação por linha no `CoachHistoryCard`; o “Apagar histórico” em bloco permanece. Escopo de aplicação para a exclusão terminar mesmo se a aba fechar; treinos/perfil intactos. Testes: repositório (2), ViewModel (2) e tela (2).
- 3.3-C disponibilidade temporária com dia inteiro/múltiplos dias: `TemporaryAvailability` aceita `0` (dia removido) e `ReplanIntent.temporaryAvailability` é uma lista ordenada no `canonical` (datas duplicadas recusadas). `PlanReplanner` valida cada dia (futuro da semana, existente, só redução), aplica todas as entradas no perfil efêmero e explica remoção/vários dias; `AthleteProfile` e `availabilityHash` não mudam. `ReplanController.replanLessTime` recebe `LessTimeChange` (lista), e o `LessTimeSheet` permite selecionar vários dias com um valor único (`0` remove). Testes: domínio (4), ViewModel (1) e tela (1).
- Goldens da bar: `BottomBarScreenshotTest` grava `bottom-bar-dark`/`bottom-bar-light` a 393 dp com os cinco tabs e o Coach selecionado; verifica existência dos tabs, seleção e clique. `McBottomBar` ganhou tags de teste e os destinos do app foram expostos como `internal` para o teste usar a mesma lista. O gate é separado (`verifyRoborazziDebug --tests ...`); o drift de outras telas não foi tocado. Testes: 3.
- Auditoria de utilidade no aparelho (18/11/2026): parser estrito (`title`/`body` strings não vazias; body objeto/JSON inválido cai no determinístico e não vira memória); linhas legadas com body JSON são ignoradas; histórico não repete a explicação corrente nem o resumo da semana da revisão e a mensagem `REVIEW` é substituída pelo resumo do mesmo `inputsHash`; explicação compacta (5 linhas + expandir), “Desativar” no fim/neutro, labels curtos e lixeira discreta por item. O system prompt do proxy também passa a exigir strings (redeploy manual). Testes: domínio (5), API (3), ViewModel (3) e card (2). A validação visual do estado Ready depende do build com token.

## 10. Memória local e retenção (Room v22)

A memória do Coach é **local e opcional**. Ela existe para dar continuidade à
conversa e ao resumo semanal, não para decidir treino. O schema atual é o **v22**;
as tabelas v19 `coach_messages`/`coach_summaries` foram unificadas em `chat_turns`
na v21 (ADR 0055) e o turno `YOU` entrou na v22 (ADR 0056).

| Tabela | Conteúdo | Chave | TTL |
|---|---|---|---|
| `chat_turns` | turnos validados `EXPLAIN`/`REVIEW`/`ANSWER`/`SUMMARY` e perguntas `YOU` (texto sanitizado) | `id`; único em `(origin, inputsHash)` para turnos do Coach | 90 dias (`EXPLAIN`/`REVIEW`/`ANSWER`/`YOU`); 180 dias (`SUMMARY`) |

Regras:

- Só entra texto que o `CoachResponseValidator` aceitou; resposta crua, prompt,
  timeout e fallback determinístico não viram memória.
- O turno `YOU` é o texto sanitizado pelo parser (trim + teto de 500), nunca as
  teclas cruas; só `intent + slots` (ou a pergunta aprovada pela porteira) vão ao
  proxy. Nome, GPS e timestamps exatos de atividade continuam fora.
- A expiração é preguiçosa (`CoachMemoryPolicy` + `CoachMemoryRepository`): roda
  na abertura da aba e antes de cada escrita; não há worker novo.
- Revogar o opt-in bloqueia chamadas na hora e apaga `chat_turns`; treinos,
  perfil, check-ins e métricas não são tocados.
- “Apagar tudo” e o export incluem `chat_turns` (com `inputsHash`); a tela Dados
  informa quantos turnos do Coach foram removidos.
- A aba Coach lista o histórico somente leitura apenas com opt-in; “Apagar
  histórico” usa o mesmo `clearAll` e as remoções individuais não tocam em
  treinos, perfil, check-ins ou métricas.
- Quando a memória for usada em prompt (pós-MVP), ela será apenas contexto de
  copy: todo texto volta ao validador e nenhum número nasce dela.
