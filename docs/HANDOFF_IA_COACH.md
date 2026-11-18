# HANDOFF — IA Coach

**Atualizado:** 18/11/2026
**Concluído:** 3.1 (proxy + opt-in + aba) · 3.2 (revisão da semana anterior) · 3.3-A (confirmação + diff + undo) · ADR 0051
**Próxima fatia:** 3.3-B — disponibilidade temporária e slot específico
**Fonte de verdade:** `docs/PRD_IA_COACH.md` + código. Este doc é o estado operacional da feature; se divergir do código, o código vence.

---

## 1. Como retomar em uma sessão nova

Prompt para colar no início da sessão:

> Continue o MeuCoach pela feature IA Coach. Leia `AGENTS.md`, `docs/PROGRESS.md` (sessão IA Coach 3.3-A), `docs/PRD_IA_COACH.md`, `docs/HANDOFF_IA_COACH.md` e `docs/adr/0051-ia-coach-proxy.md`. A próxima fatia é a **3.3-B: disponibilidade temporária e slot específico**, reusando `ReplanInput`/`ApplyReplan` e o `ReplanController` compartilhado. Antes de codar, confirme `git status` e rode os testes do Coach. Implemente com TDD, rode os gates do AGENTS.md e atualize `PROGRESS`/`PRD`/este handoff.

Ordem de leitura:

1. `AGENTS.md` — regras invioláveis.
2. `docs/PROGRESS.md` — sessão **IA Coach 3.3-A** no topo.
3. `docs/PRD_IA_COACH.md` — decisões travadas, escopo, guardrails.
4. Este handoff — estado e próxima fatia.
5. `docs/adr/0051-ia-coach-proxy.md` — por que existe proxy e o que ele não faz.

Regra de trabalho: **uma fatia pequena e verificável por sessão**; não abrir 3.3-B, Room v19 e goldens ao mesmo tempo.

---

## 2. Estado atual

| Fatia | O que está pronto | Onde |
|---|---|---|
| 3.1 / F1 | Proxy Ktor stateless no Dokploy, `X-Coach-Token`, `/health`, cadeia de modelos com fallback, sem log de payload | `infra/coach-proxy/` |
| 3.1 / F4 | Aba central Coach, opt-in/revogação, explicação da semana (IA ou rationale), retry, fallback | `app/ui/coach/`, `app/coach/`, `core/data/coach/` |
| 3.2 | Revisão da semana fechada: payload minimizado, validação de duração/porcentagem, cartão determinístico + narrativa, cache por hash | `CoachReviewPromptBuilder`, `CoachApi.review`, `WeeklyReviewAssembler`, `CoachReviewSource`, `CoachReviewCard` |
| 3.3-A | Ações rápidas T3 na aba Coach, bottom sheet com diff/selo, aceite via `ApplyReplan` (stale reavalia), undo append-only e `ReplanController` compartilhado com Hoje/Perfil | `app/replan/ReplanController*`, `app/application/UndoReplan`, `TrainingPlanRepository.undoReplan`, `CoachReplanCard`, `ReplanSuggestionSheet` |
| ADR 0051 | Contrato, segurança mínima, trade-offs e risco LGPD aceito | `docs/adr/0051-ia-coach-proxy.md` |

Verificação no fechamento: **2.357 testes** (domain 1112, data 218, app 987, model 40); a suíte do app teve 1 falha conhecida (`TutorialSystemBackTest`, `UncaughtExceptionsBeforeTest`), verde isolada — ver `TESTING.md`. `assembleDebug`, `spotlessCheck`, `koverVerify`, detekt e lint verdes. Nenhuma migração; schema continua **v18**.

Pontos de arquitetura que não devem regredir:

- Domínio puro: prompt/validator não importam Android, Room, Hilt ou rede.
- `WeeklyReviewAssembler` é a única montagem da semana fechada; Tendências e Coach usam o mesmo resultado.
- A IA **só reescreve texto**. Nenhum número, pace, FC, carga ou plano nasce da resposta.
- Qualquer falha (timeout, 4xx, schema inválido, número inventado) cai no determinístico; a aba nunca quebra.
- Revogar o opt-in bloqueia a chamada imediatamente.
- O payload não leva nome, GPS, timestamps exatos nem texto livre do atleta.
- **Uma única máquina de aceite:** Hoje, Perfil e Coach delegam a `ReplanController`; as telas não repetem `evaluate/accept/stale`.
- **Undo é revisão nova** (`trigger = UNDO`), nunca rewrite nem rollback do número de versão (ADR 0028).

---

## 3. 3.3-A concluída (18/11/2026)

### 3.1 Objetivo entregue

Levar o replanejamento para a aba Coach com confirmação explícita, diff antes/depois e undo, sem duplicar a regra de treino:

- Card **“AJUSTAR A SEMANA”** com três ações: “Ajustar a semana”, “Perdi um treino” e “Tenho menos tempo”. Nesta fatia as três disparam a mesma avaliação T3 (`USER_REQUEST`); a escolha de dia/slot fica para 3.3-B.
- Bottom sheet com a sugestão existente (`ReplanSuggestionCard`), diff “O QUE MUDA” e selo `v{n} → v{n+1}`.
- Confirmar chama `ApplyReplan`, que revalida versão, inputs, fingerprint e disponibilidade. Se ficou stale, a sugestão nova aparece; a velha nunca é aplicada.
- Após aplicar: “Semana ajustada…” com **Desfazer** e **Ver o que mudou**; o sheet “O que mudou” também oferece “Desfazer ajuste”.
- Recusar/suprimir continuam valendo por `ReplanSuggestionStore` (recusa por semana/versão/hash; supressão até o fim do dia).

### 3.2 O que foi extraído/reusado

| Peça | Papel |
|---|---|
| `ReplanController` (`app/replan/`) | Máquina de estado de aceite: `refresh/replanNow/accept/decline/suppress/undo/dismiss`, tratamento de stale e mensagem. |
| `ReplanControllerFactory` + `ReplanOperations` | Ligam o controller ao `viewModelScope` de cada tela; agrupam `EvaluateReplan`/`ApplyReplan`/`UndoReplan` sem inflar construtores. |
| `ReplanViewModel` | Fachada fina de Hoje/Perfil; comportamento preservado. |
| `UndoReplan` (`app/application/`) | Mapeia `TrainingPlanRepository.undoReplan` para `Applied/Stale/Rejected`. |
| `TrainingPlanRepository.undoReplan` | Transação append-only: exige versão, confere a última revisão, restaura só futuro `PLANNED`, preserva hoje/passado/`DONE`/`CLOSED`, grava v+1 com trigger `UNDO`, recalcula agregados e aborta em conflito protegido. |
| `ReplanSuggestionSheet` / `ReplanMessageCard` / `ReplanChangesSheet` | UI compartilhada do diff, confirmação e undo. |
| `CoachReplanCard` | Ações rápidas e copy “nada muda sem você confirmar”. |

### 3.3 Testes da fatia

- Repository: undo restaura o futuro como v+1, recalcula agregados, recusa versão obsoleta, sem revisão anterior, segundo undo e conflito com futuro `DONE`.
- `ReplanViewModelTest`: aceite com `canUndo`, undo restaura e limpa a oferta, undo sem revisão explica.
- `CoachViewModelTest`: ação rápida → sugestão → aceite → undo; ação sem semana explica.
- `CoachScreenTest`: ações rápidas, sheet com diff/selo/confirmação e Desfazer na mensagem + no sheet do diff.

### 3.4 Decisões e observações

- O undo não é oferecido para revisões de sistema (`ENGINE_UPGRADE`/`DATA_READY`): restaurar sessões velhas sob a versão nova do motor/dados deixaria conteúdo obsoleto que a abertura não revisita.
- `canUndo` deriva da última revisão + versão atual; cada tela tem sua instância de controller, mas o `ReplanSuggestionStore` é compartilhado.
- O plano mantém `createdAt`/`createdOn`; o undo move só `revisedAt`/`version` e restaura `inputsHash`/`availabilityHash`/proveniência da revisão desfeita.
- Nenhuma regra fisiológica, schema ou migração nova.

---

## 4. Próxima fatia — 3.3-B (recomendada)

### 4.1 Objetivo

Completar as intenções do PRD que a 3.3-A deixou fora:

- **“Só tenho 30 min no dia Y”** como disponibilidade temporária: override efêmero no `ReplanInput`, validado no aceite; **nunca** alterar `AthleteProfile` sem aceite explícito separado (PRD decisão 28).
- **“Perdi o treino X”** escolhendo a sessão específica (hoje o T1 detecta elegíveis, mas não recebe o alvo).
- Manter o motor como autoridade e a IA só reescrevendo copy. Não implementar parser de intenção por LLM no MVP.
- Reusar `ReplanController`/`ReplanSuggestionSheet`; nenhuma máquina de aceite nova.

### 4.2 Critérios de aceite

- [ ] Disponibilidade temporária não persiste no perfil e expira ao fim do dia/semana definido.
- [ ] A sugestão mostra o slot/dia afetado e o selo de versão.
- [ ] Stale por disponibilidade é reavaliado e nunca aplica a sugestão velha.
- [ ] Undo continua disponível e auditado como revisão `UNDO`.
- [ ] Testes novos + suíte Coach/replan verdes; gates do AGENTS.md verdes.
- [ ] `PROGRESS`, `PRD` (adendo) e este handoff atualizados.

---

## 5. Depois da 3.3

### 5.1 Room v19 — memória/conversa

- `coach_messages` e `coach_summaries`; `MIGRATION_18_19` explícita, sem `fallbackToDestructiveMigration`.
- Resumo é memória do produto, não decisão: o motor continua autoridade; IA não decide carga.
- Revogar o opt-in **apaga** `coach_*`; export/delete incluem as tabelas; TTL definido no PRD.
- Testes: migração 18→19, round-trip, delete na revogação, export.
- Atualizar `DATA_MODEL`/`ARCHITECTURE` e `MeuCoachDatabase.VERSION`.

### 5.2 Goldens Roborazzi da bar

- `./gradlew :app:verifyRoborazziDebug` é invocação separada.
- Drift pré-existente em `activity-comparison-*`, `checkin-*` e `health-*` registrado em `TESTING.md`; só mexer ao tocar essas telas.
- Ao alterar a barra, regravar os goldens da navegação e revisar visualmente dark/light.

---

## 6. Build e ambiente

Comandos:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.meucoach.app.ui.coach.*' --tests 'com.meucoach.app.ui.replan.*' --tests 'com.meucoach.app.application.*'
./gradlew :core:data:testDebugUnitTest --tests 'com.meucoach.core.data.repository.TrainingPlanRepositoryTest'
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

## 7. Definition of Done da sessão

- [ ] Compila (`assembleDebug`).
- [ ] Testes novos e existentes verdes; `spotlessCheck` e `koverVerify` verdes.
- [ ] Detekt/lint sem erros; sem adicionar achado ao baseline sem justificativa.
- [ ] Nenhum dado real no diff; nenhuma migração destrutiva.
- [ ] Docs da regra/UX + `PROGRESS.md` + este handoff atualizados.
- [ ] Commit em Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`).
