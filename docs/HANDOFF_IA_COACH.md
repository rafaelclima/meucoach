# HANDOFF — IA Coach

**Atualizado:** 18/11/2026
**Concluído:** 3.1 (proxy + opt-in + aba) · 3.2 (revisão da semana anterior) · ADR 0051
**Próxima fatia:** 3.3-A — confirmação + diff + undo na aba Coach
**Fonte de verdade:** `docs/PRD_IA_COACH.md` + código. Este doc é o estado operacional da feature; se divergir do código, o código vence.

---

## 1. Como retomar em uma sessão nova

Prompt para colar no início da sessão:

> Continue o MeuCoach pela feature IA Coach. Leia `AGENTS.md`, `docs/PROGRESS.md` (sessão IA Coach 3.2), `docs/PRD_IA_COACH.md`, `docs/HANDOFF_IA_COACH.md` e `docs/adr/0051-ia-coach-proxy.md`. A próxima fatia é a **3.3-A: confirmação + diff + undo na aba Coach**, reusando `EvaluateReplan`/`ApplyReplan`. Antes de codar, confirme `git status` e rode os testes do Coach. Implemente com TDD, rode os gates do AGENTS.md e atualize `PROGRESS`/`PRD`/este handoff.

Ordem de leitura:

1. `AGENTS.md` — regras invioláveis.
2. `docs/PROGRESS.md` — sessão **IA Coach 3.2** no topo.
3. `docs/PRD_IA_COACH.md` — decisões travadas, escopo, guardrails.
4. Este handoff — estado e próxima fatia.
5. `docs/adr/0051-ia-coach-proxy.md` — por que existe proxy e o que ele não faz.

Regra de trabalho: **uma fatia pequena e verificável por sessão**; não abrir 3.3, Room v19 e goldens ao mesmo tempo.

---

## 2. Estado atual

| Fatia | O que está pronto | Onde |
|---|---|---|
| 3.1 / F1 | Proxy Ktor stateless no Dokploy, `X-Coach-Token`, `/health`, cadeia de modelos com fallback, sem log de payload | `infra/coach-proxy/` |
| 3.1 / F4 | Aba central Coach, opt-in/revogação, explicação da semana (IA ou rationale), retry, fallback | `app/ui/coach/`, `app/coach/`, `core/data/coach/` |
| 3.2 | Revisão da semana fechada: payload minimizado, validação de duração/porcentagem, cartão determinístico + narrativa, cache por hash | `CoachReviewPromptBuilder`, `CoachApi.review`, `WeeklyReviewAssembler`, `CoachReviewSource`, `CoachReviewCard` |
| ADR 0051 | Contrato, segurança mínima, trade-offs e risco LGPD aceito | `docs/adr/0051-ia-coach-proxy.md` |

Verificação no fechamento: **2.345 testes** (domain 1112, data 213, app 980, model 40), 0 falhas; `assembleDebug`, `spotlessCheck`, `koverVerify`, detekt e lint verdes. Nenhuma migração; schema continua **v18**.

Pontos de arquitetura que não devem regredir:

- Domínio puro: prompt/validator não importam Android, Room, Hilt ou rede.
- `WeeklyReviewAssembler` é a única montagem da semana fechada; Tendências e Coach usam o mesmo resultado.
- A IA **só reescreve texto**. Nenhum número, pace, FC, carga ou plano nasce da resposta.
- Qualquer falha (timeout, 4xx, schema inválido, número inventado) cai no determinístico; a aba nunca quebra.
- Revogar o opt-in bloqueia a chamada imediatamente.
- O payload não leva nome, GPS, timestamps exatos nem texto livre do atleta.

---

## 3. Próxima fatia — 3.3-A (recomendada)

### 3.1 Objetivo

Levar o replanejamento para a aba Coach com **confirmação explícita, diff antes/depois e undo**, sem duplicar a regra de treino:

- Ações rápidas na aba: **“Ajustar a semana”**, **“Perdi um treino”** e **“Tenho menos tempo”**. Nesta fatia as duas últimas só disparam a avaliação T3 (`USER_REQUEST`); a seleção de dia/slot específico fica para 3.3-B.
- Bottom sheet com a sugestão existente (`ReplanSuggestionCard`/`ReplanChangesSheet`) e o selo `v{n} → v{n+1}`.
- Confirmar → `ApplyReplan` (revalida versão, inputs, fingerprint e disponibilidade). Se ficou stale, mostrar a sugestão nova, nunca aplicar a velha.
- Após aplicar: mensagem de sucesso + **Desfazer** persistente no sheet “O que mudou”.
- Recusar/suprimir continuam valendo pelas regras atuais (`ReplanSuggestionStore`).

### 3.2 O que já existe (reusar, não reescrever)

| Peça | Papel |
|---|---|
| `PlanReplanner` | Decide T1–T5, candidato e `ReplanSuggestion`; nunca aplica. |
| `ReplanContextBuilder` | Monta o input persistido (perfil, carga, prontidão, volume, prova, recuperação). |
| `EvaluateReplan` | `Suggestion` / `NoChange` / `Unavailable`. |
| `ApplyReplan` | Revalida e aplica; devolve `Applied` / `Stale(reevaluated)` / `Rejected`. |
| `TrainingPlanRepository.applyReplan` | Transação + revisão append-only + proteção de passado/hoje/`DONE`/`CLOSED`. |
| `ReplanUi` / `ReplanCard` | Copy, diff e selo de versão. |
| `ReplanViewModel` | Máquina de estados de aceite/stale/recusa de Hoje. |
| `ReplanSuggestionStore` | Recusa por versão+inputs e supressão diária. |

**Sugestão de desenho:** extrair de `ReplanViewModel` um `ReplanController` (app/application ou app/replan) com a máquina `evaluate/accept/decline` + tratamento de stale, e fazer Hoje e Coach delegarem. Assim a aba Coach não importa a feature Hoje nem duplica ~60 linhas de aceite; `ReplanUi` pode ser promovido a `app/ui/replan` compartilhado.

### 3.3 Undo — desenho mínimo

O `weekly_plan_revisions` já guarda `replacedSessionsJson`, `fromVersion`, `toVersion` e os hashes. Implementar:

1. `TrainingPlanRepository.undoReplan(weekStart, today, expectedVersion)` dentro de `withTransaction`:
   - exige `stored.version == expectedVersion` (senão stale);
   - lê a última revisão e confere `revision.toVersion == expectedVersion`;
   - restaura somente `replacedSessions` com `date > today` e `status == PLANNED`, sem tocar em hoje/passado/`DONE`/`CLOSED`;
   - remove as futuras `PLANNED` atuais e insere as restauradas (nunca `REPLACE`);
   - grava **uma nova revisão** (v+1) com `trigger = "UNDO"`, motivo “Você desfez o ajuste”, `replacedSessionsJson` = o que saiu, e os metadados `from*` da revisão desfeita;
   - recalcula `weeklyMinutes`, `longRunMinutes`, `qualityCount` das sessões mescladas;
   - se qualquer protegida conflitar, aborta sem escrever (`PROTECTED_CONFLICT`).
2. Use case `UndoReplan` em `app/application` com resultado `Applied/Stale/Rejected`, sem regra de treino nova.
3. UI: ação “Desfazer” no sheet de mudanças; depois de desfazer, mostrar “Ajuste desfeito” e o diff da reversão.

Não reescrever a revisão antiga nem voltar o número da versão: o histórico é append-only (ADR 0028). Um undo é uma revisão nova.

### 3.4 Testes mínimos da 3.3-A

- Domínio/app: T3 continua `NoChange` sem motivo de mudança; aceite aplica só o futuro (já coberto — não reduzir asserts).
- `ApplyReplan`: stale por versão/inputs/fingerprint; conflito de protegida não escreve (reuso dos testes existentes).
- Repository: `undoReplan` restaura o futuro exato, mantém hoje/passado/`DONE`, grava v+1 com trigger `UNDO`, e faz no-op/stale se a versão mudou.
- ViewModel/Coach: ação rápida avalia, mostra diff, confirmar aplica, stale atualiza a sugestão, undo desfaz.
- UI: bottom sheet com diff e selo; “Desfazer” visível após aplicar; TalkBack/testTags.

### 3.5 Fora do escopo da 3.3-A (vai para 3.3-B)

- “Só tenho 30 min no dia Y” como **disponibilidade temporária**: precisa de override efêmero no `ReplanInput` e validação no aceite; **nunca** alterar `AthleteProfile` sem aceite explícito separado (PRD decisão 28).
- “Perdi o treino X” escolhendo a sessão específica (hoje o T1 já detecta elegíveis, mas não recebe o alvo).
- Interpretação livre de texto por LLM para slots: o proxy tem `replan-suggest`, mas o motor continua sendo a autoridade; no máximo a IA reescreve copy. Não implementar parser de intenção por LLM no MVP.

### 3.6 Critérios de aceite

- [ ] Aba Coach oferece replanejamento sem sair dela.
- [ ] Nenhuma mudança é aplicada sem confirmação explícita.
- [ ] O diff mostra só o futuro que muda; o selo `v{n} → v{n+1}` aparece.
- [ ] Stale nunca aplica a sugestão velha.
- [ ] Undo restaura o futuro anterior e fica auditado como nova versão.
- [ ] Hoje e Coach usam a mesma máquina de aceite.
- [ ] Testes novos + suíte Coach verdes; gates do AGENTS.md verdes.
- [ ] `PROGRESS`, `PRD` (adendo) e este handoff atualizados.

---

## 4. Depois da 3.3

### 4.1 Room v19 — memória/conversa

- `coach_messages` e `coach_summaries`; `MIGRATION_18_19` explícita, sem `fallbackToDestructiveMigration`.
- Resumo é memória do produto, não decisão: o motor continua autoridade; IA não decide carga.
- Revogar o opt-in **apaga** `coach_*`; export/delete incluem as tabelas; TTL definido no PRD.
- Testes: migração 18→19, round-trip, delete na revogação, export.
- Atualizar `DATA_MODEL`/`ARCHITECTURE` e `MeuCoachDatabase.VERSION`.

### 4.2 Goldens Roborazzi da bar

- `./gradlew :app:verifyRoborazziDebug` é invocação separada.
- Drift pré-existente em `activity-comparison-*`, `checkin-*` e `health-*` registrado em `TESTING.md`; só mexer ao tocar essas telas.
- Ao alterar a barra, regravar os goldens da navegação e revisar visualmente dark/light.

---

## 5. Build e ambiente

Comandos:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.meucoach.app.ui.coach.*' --tests 'com.meucoach.app.coach.*'
./gradlew :core:domain:test :core:data:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew check -x :app:testDebugUnitTest
./gradlew spotlessApply
```

Proxy (não commitado):

- URL: `https://meucoach-api.hojeafestaenossa.site`
- Build com `-PcoachProxyUrl=... -PcoachProxyToken=...`; vazio = Coach desativado e fallback.
- Token de produção no Dokploy (`COACH_PROXY_TOKEN`); chave OpenRouter em `OPENROUTER_API_KEY`. Nunca no repo, APK, log, issue ou prompt.
- Backup real em `fixtures/private/` (gitignored); não usar em CI.

Flakes conhecidos (ver `TESTING.md`): `TutorialSystemBackTest` (transação Room vazada) e `MetricsImportViewModelTest` (timeout de 30 s). Rodar isolado ou `--no-parallel`; consertar, não ignorar.

---

## 6. Definition of Done da sessão

- [ ] Compila (`assembleDebug`).
- [ ] Testes novos e existentes verdes; `spotlessCheck` e `koverVerify` verdes.
- [ ] Detekt/lint sem erros; sem adicionar achado ao baseline sem justificativa.
- [ ] Nenhum dado real no diff; nenhuma migração destrutiva.
- [ ] Docs da regra/UX + `PROGRESS.md` + este handoff atualizados.
- [ ] Commit em Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`).
