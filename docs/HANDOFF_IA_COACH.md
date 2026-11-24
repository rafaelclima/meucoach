# HANDOFF — IA Coach

**Atualizado:** 24/11/2026
**Concluído:** 3.1 (proxy + opt-in + aba) · 3.2 (revisão da semana anterior) · 3.3-A (confirmação + diff + undo) · 3.3-B (intent explícito) · 3.3-C (dia inteiro/múltiplos dias) · ADR 0051 · Room v19 (memória/conversa) · histórico somente leitura com apagar em bloco e individual · goldens da bar · auditoria de utilidade no aparelho · input livre (ADRs 0056/0057) · conversa aberta com fatos fechados/consentimento v2 (ADR 0063) · lembrete fiel à prescrição do dia (ADR 0064)
**Próxima fatia:** redeploy do proxy com o `SYSTEM_FREE_ANSWER` novo e validação no aparelho; retomar P1-12/Ciclo 4 (§5.4)
**Fonte de verdade:** `docs/PRD_IA_COACH.md` + código. Este doc é o estado operacional da feature; se divergir do código, o código vence.

---

## 1. Como retomar em uma sessão nova

Prompt para colar no início da sessão:

> Continue o MeuCoach pela feature IA Coach. Leia `AGENTS.md`, `docs/PROGRESS.md` (sessão "Lembrete fiel ao dia + Coach com conversa aberta"), `docs/PRD_IA_COACH.md`, `docs/PRD_COACH_CHAT.md`, `docs/HANDOFF_IA_COACH.md`, `docs/adr/0052-ia-coach-memory-schema-v19.md`, `docs/adr/0063-coach-open-answers.md` e `docs/adr/0064-workout-reminder-day-authority.md`. A feature está encerrada no MVP e ganhou conversa aberta com fatos fechados; falta o redeploy do proxy com o `SYSTEM_FREE_ANSWER` novo e a validação no aparelho com `-PcoachProxyToken=...`. Depois, retomar P1-12/Ciclo 4. Antes de codar, confirme `git status` e rode os gates do AGENTS.md.

Ordem de leitura:

1. `AGENTS.md` — regras invioláveis.
2. `docs/PROGRESS.md` — sessão **IA Coach 3.3-B** no topo.
3. `docs/PRD_IA_COACH.md` — decisões travadas, escopo, guardrails.
4. Este handoff — estado e próxima fatia.
5. `docs/adr/0051-ia-coach-proxy.md` — por que existe proxy e o que ele não faz.
6. `docs/adr/0052-ia-coach-memory-schema-v19.md` — schema v19, TTL e revogação.

Regra de trabalho: **uma fatia pequena e verificável por sessão**; não abrir pendências da 3.3 e goldens ao mesmo tempo.

---

## 2. Estado atual

| Fatia | O que está pronto | Onde |
|---|---|---|
| 3.1 / F1 | Proxy Ktor stateless no Dokploy, `X-Coach-Token`, `/health`, cadeia de modelos com fallback, sem log de payload | `infra/coach-proxy/` |
| 3.1 / F4 | Aba central Coach, opt-in/revogação, explicação da semana (IA ou rationale), retry, fallback | `app/ui/coach/`, `app/coach/`, `core/data/coach/` |
| 3.2 | Revisão da semana fechada: payload minimizado, validação de duração/porcentagem, cartão determinístico + narrativa, cache por hash | `CoachReviewPromptBuilder`, `CoachApi.review`, `WeeklyReviewAssembler`, `CoachReviewSource`, `CoachReviewCard` |
| 3.3-A | Ações rápidas T3 na aba Coach, bottom sheet com diff/selo, aceite via `ApplyReplan` (stale reavalia), undo append-only e `ReplanController` compartilhado com Hoje/Perfil | `app/replan/ReplanController*`, `app/application/UndoReplan`, `TrainingPlanRepository.undoReplan`, `CoachReplanCard`, `ReplanSuggestionSheet` |
| 3.3-B | “Perdi o treino X” e “só tenho 30 min no dia Y”: `ReplanIntent` no input/hash/sugestão, perfil efêmero só no gerador, pickers no Coach, `requestNote`, stale por intent e fila preservando o intent | `core/domain/replan/ReplanModels.kt`, `PlanReplanner.eligibleMissedSessions`, `EvaluateReplan.missedCandidates`, `ReplanController`, `CoachIntentSheets` |
| 3.3-C | “Tenho menos tempo” com vários dias por pedido e `0` removendo o dia: lista de `TemporaryAvailability`, validação por dia, perfil efêmero com a chave removida, canonical ordenado, picker multi-seleção | `ReplanModels.kt` (`TemporaryAvailability`/`ReplanIntent`), `PlanReplanner`, `ReplanController.replanLessTime`, `LessTimeSheet`/`LessTimeChange` |
| Goldens da bar | `bottom-bar-dark/light.png` com os cinco tabs e o Coach selecionado; tags de teste na `McBottomBar`; revisão visual dark/light e clique/seleção cobertos | `McBottomBar`, `destinations`, `app/ui/navigation/BottomBarScreenshotTest` |
| Auditoria de utilidade | Parser estrito (`title`/`body` strings), legado JSON oculto, histórico sem duplicar card atual, explicação compacta/expansível, “Desativar” neutro, lixeira discreta e gear com fundo | `HttpCoachApi.parse`, `CoachMemoryRepository`, `CoachHistory`, `CoachExplanationCard`, `CoachScreen`, `CoachHistoryCard`, `MeuCoachApp` |
| Room v19 | Memória local: `coach_messages`/`coach_summaries`, TTL 90/180, revogação apaga, export/delete incluem, texto sempre validado | `core/data/db/entity/Coach*`, `CoachMemoryRepository`, `CoachMemoryPolicy`, `MIGRATION_18_19`, `CoachViewModel`, `UserDataRepository` |
| Histórico UI | Lista somente leitura (resumos + conversa) só com opt-in, loading/vazio/erro, retry, “Apagar histórico” em bloco e apagar item a item com confirmação, TalkBack, dark/light e fonte 200% | `CoachHistoryState`/`CoachViewModel`, `CoachHistoryCard`, `CoachMemoryRepository.observe*/delete*`, DAOs `observeAll`/`delete*` |
| ADR 0051 | Contrato, segurança mínima, trade-offs e risco LGPD aceito | `docs/adr/0051-ia-coach-proxy.md` |
| ADR 0052 | Schema v19, TTL, revogação e limites da memória | `docs/adr/0052-ia-coach-memory-schema-v19.md` |

Verificação no fechamento: **2.435 testes** (domain 1130, data 232, app 1033, model 40), 0 falhas/erros; `assembleDebug`, `spotlessCheck`, `check -x :app:testDebugUnitTest` (detekt incluído), `koverVerify`, `lintDebug` e `verifyRoborazziDebug --tests BottomBarScreenshotTest` verdes. Schema **v19** com `MIGRATION_18_19` testada; sem `fallbackToDestructiveMigration`.

Pontos de arquitetura que não devem regredir:

- Domínio puro: prompt/validator não importam Android, Room, Hilt ou rede.
- `WeeklyReviewAssembler` é a única montagem da semana fechada; Tendências e Coach usam o mesmo resultado.
- A IA **só reescreve texto**. Nenhum número, pace, FC, carga ou plano nasce da resposta.
- Qualquer falha (timeout, 4xx, schema inválido, número inventado) cai no determinístico; a aba nunca quebra.
- Revogar o opt-in bloqueia a chamada imediatamente e apaga `coach_messages`/`coach_summaries` (escopo de aplicação); treinos não são tocados.
- O payload não leva nome, GPS, timestamps exatos nem texto livre do atleta.
- **Memória é copy:** só texto aprovado pelo `CoachResponseValidator` é persistido; TTL de 90/180 dias; ela não entra em prompt nesta fatia e nunca decide carga, pace ou plano.
- **Uma única máquina de aceite:** Hoje, Perfil e Coach delegam a `ReplanController`; as telas não repetem `evaluate/accept/stale`.
- **Intent é memória de avaliação:** `ReplanIntent` entra no `inputsHash` e é revalidado no aceite; nunca é gravado em `AthleteProfile`/`weeklyPlans.availabilityHash`.
- **Undo é revisão nova** (`trigger = UNDO`), nunca rewrite nem rollback do número de versão (ADR 0028).

---

## 3. 3.3-A e 3.3-B concluídas (18/11/2026)

### 3.1 Objetivo entregue (3.3-A)

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

### 3.5 Objetivo entregue (3.3-B)

Completar as intenções do PRD sem criar regra de treino:

- **Intent de domínio:** `TemporaryAvailability(date, minutes)` e `ReplanIntent(temporaryAvailability, requestedMissedDate)` em `ReplanModels.kt`. `ReplanInput` e `ReplanSuggestion` carregam o intent; `ReplanCause.TEMPORARY_AVAILABILITY` é só copy (nenhum `ruleId`/`evidenceId` novo).
- **“Tenho menos tempo”:** `PlanReplanner` valida dia futuro da semana vigente, existente na disponibilidade persistida e com minutos menores; monta um perfil efêmero (`profile.copy(weeklyAvailability = ...)`) usado **somente** no `WeeklyPlanInput` e na validação de dias. `availabilityHash`, `expectedAvailabilityHash` e o baseline persistido continuam os do perfil real.
- **“Perdi um treino”:** `eligibleMissedSessions(input)` virou API pública e é a única regra de elegibilidade; `EvaluateReplan.missedCandidates()` alimenta o picker. Escolher uma data elegível força T1 mesmo com 1 sessão (`missedTriggered`), com `causes = [MISSED_SESSIONS]`, sem repor carga/qualidade.
- **Stale por intent:** `intent.canonical` entra no `inputsHash`; `ApplyReplan` reconstrói com `suggestion.intent` e a reavaliação de stale mantém o mesmo intent. Intent inválido (dia passado/fora da semana/dia inexistente/aumento/data não perdida) retorna `NoChange` com motivo claro.
- **Fila:** `ReplanRequestQueue` substitui `queuedExplicit`; um pedido explícito enfileirado durante avaliação automática mantém o intent exato (o último vence).
- **UI:** “Perdi um treino” abre `MissedSessionPickerSheet` (data + título + duração; vazio explica); “Tenho menos tempo” abre `LessTimeSheet` (chips dos dias futuros + minutos, validação local `1..current-1`); o sheet de sugestão mostra `requestNote`. Hoje/Perfil não mudaram.

### 3.6 Testes da fatia 3.3-B

- Domínio: temp força revisão e mantém o baseline; redução não aumenta volume/qualidade; `inputsHash` muda com o intent; hoje/fora da semana/dia inexistente/aumento → `NoChange`; treino perdido com 1 sessão força T1 sem compensação; data futura/doente → `NoChange`.
- App: `missedCandidates` só lista elegíveis; contexto propaga o intent sem alterar o perfil; aceitar temp não muda `availabilityHash` nem `AthleteProfile`; temp que deixou de valer vira `Stale`/`NoChange` sem escrita.
- Coach: picker lista e avalia o treino perdido; menos tempo lista o dia e aceita sem persistir; minutos que não reduzem são recusados sem chamar o motor; `ReplanRequestQueueTest` cobre o intent enfileirado; tela cobre pickers, validação local e `requestNote`.

### 3.7 Decisões e observações (3.3-B)

- A disponibilidade temporária é **memória de avaliação**, não dado: nenhum caminho grava em `AthleteProfile`/`weeklyPlans`.
- O intent entra no `inputsHash` (e não em `availabilityHash`), o que faz o aceite detectar troca de dia/minutos sem fingir mudança de perfil.
- Um intent não vazio não gera a cause genérica `USER_REQUEST`; a copy específica (“tempo reduzido”/treino não concluído) é mais útil e auditável.
- Na 3.3-B, remover um dia inteiro, múltiplos dias por pedido e “quero aumentar meu tempo” ficaram fora do escopo; a 3.3-C fechou os dois primeiros e “quero mais tempo” segue fora.
- Nenhuma migração; schema v18. O undo da 3.3-A continua valendo para a revisão criada pela 3.3-B.

### 3.8 Objetivo entregue (3.3-C)

Fechar a pendência funcional da disponibilidade temporária sem criar regra nova:

- **Dia inteiro:** `TemporaryAvailability.minutes = 0` remove o dia; o perfil efêmero perde a chave e o gerador trata como indisponível. `AthleteProfile` e `availabilityHash` não mudam.
- **Vários dias:** `ReplanIntent.temporaryAvailability` virou lista; datas duplicadas são recusadas no `init` e o `canonical` ordena por data, então a ordem não muda o `inputsHash`.
- **Validação:** `PlanReplanner` valida cada dia (futuro da semana, existente na disponibilidade, apenas redução) e explica remoção/múltiplos dias no rationale. O `inputsHash` de um único dia permanece igual.
- **UI:** `LessTimeChange` substitui o par data/minutos; o controller valida lista, duplicatas e faixa `0..current-1`; o `LessTimeSheet` tem chips multi-seleção e um valor único aplicado aos dias escolhidos (`0` remove). `requestNote` descreve um dia, vários dias ou “sem treino”.
- **Aceite:** `ApplyReplan` reconstrói o intent inteiro (lista); o perfil persistido e o baseline continuam iguais.

### 3.9 Testes da fatia 3.3-C

- Domínio: remover um dia + reduzir outro no mesmo pedido, `canonical` independente da ordem, datas duplicadas recusadas e canonical vazio legado preservado.
- App: ViewModel remove quinta e reduz sexta sem persistir o override; tela seleciona dois dias, informa `0` e envia os dois `LessTimeChange`.
- Total da feature no fechamento: **2.421 testes** (domain 1125, data 229, app 1027, model 40).

---

## 4. Room v19 + histórico do Coach — concluído (18/11/2026)

### 4.1 Objetivo entregue

Memória local do Coach **sem** transformar memória em decisão:

- `coach_messages` (turnos validados da explicação/revisão) e `coach_summaries` (uma narrativa validada por semana fechada).
- `MIGRATION_18_19` explícita, aditiva, sem `fallbackToDestructiveMigration`; schema **v19**.
- Revogar o opt-in apaga `coach_*`; export/delete incluem as tabelas; TTL no PRD (90/180 dias).
- O motor continua a autoridade: o resumo é contexto de copy e não entra em prompt nesta fatia.

### 4.2 Modelo e invariantes

- `CoachMessageEntity`/`CoachSummaryEntity` e DAOs em `core:data/db`; `MeuCoachDatabase.VERSION = 19` com `coachMessageDao()`/`coachSummaryDao()`.
- `CoachMessageEntity` tem índice único `(kind, inputsHash)`: reabrir a aba ou repetir a mesma avaliação não duplica a conversa. `CoachSummaryEntity` usa `weekStart` como PK (no máximo um resumo por semana).
- Só texto aprovado pelo `CoachResponseValidator` é gravado; resposta inválida, timeout e fallback determinístico não viram memória.
- `CoachMemoryPolicy` (domínio puro): mensagens 90 dias, resumos 180 dias, corte inclusivo.
- `CoachMemoryRepository`: `recordMessage`, `recordSummary`, `pruneExpired` (na abertura e antes de cada escrita) e `clearAll`; sem worker novo.
- `CoachViewModel` grava somente com opt-in; revogar bloqueia chamadas na hora e apaga as duas tabelas em `@ApplicationScope`, para a exclusão terminar mesmo se a aba fechar.
- `UserDataRepository` exporta `coachMessages`/`coachSummaries` e apaga as duas no “apagar tudo”; a tela Dados informa quantas mensagens/resumos foram removidos.

### 4.3 Testes da fatia

- `CoachMemoryPolicyTest` (3): TTL e corte inclusivo de mensagem/resumo.
- `CoachMemoryRepositoryTest` (6): round-trip/ordenação, dedupe por contexto, um resumo por semana, prune com boundary, prune na escrita e clear.
- `MeuCoachDatabaseMigrationTest` (17 no total): 18→19 preserva plano/sessão, cria tabelas vazias e o round-trip passa; todas as migrações anteriores agora validam o schema 19.
- `UserDataRepositoryTest` (+2 asserts): export traz `coachMessages`/`coachSummaries`; delete conta e remove as duas.
- `CoachViewModelTest` (+4): grava explicação válida, não grava fallback, grava revisão + resumo e revogar apaga sem tocar no plano.
- `DataManagementViewModelTest` (+1): a mensagem de exclusão cita mensagens/resumos do Coach.

### 4.4 Decisões e observações

- O resumo persistido é a narrativa **LLM validada** da semana fechada (PRD decisão 24), ligada ao `inputsHash` da revisão; não é regra nem número novo.
- A memória não entra em prompt ainda. Quando entrar, continuará contexto de copy e todo texto voltará ao validador.
- Não há UI para listar mensagem individual nesta fatia; a lista visual, o
  “apagar histórico” em bloco e o apagar item a item foram entregues nas fatias
  de histórico seguintes.
- `CoachSummaryDao` faz upsert por `weekStart`; `CoachMessageDao` ignora conflito de `(kind, inputsHash)` (o primeiro texto validado é estável).
- Nenhuma regra fisiológica, de volume ou de qualidade foi alterada.

### 4.5 Fora do escopo

- Memória no servidor (o proxy continua stateless).
- Busca semântica, embeddings, resumo por LLM com decisão de treino.
- Edição de texto e migração destrutiva/`fallbackToDestructiveMigration`.

### 4.6 Critérios de aceite

- [x] App abre com banco v18 existente e migra para v19 sem perder dado.
- [x] Revogar o opt-in apaga a memória local; export/delete a incluem.
- [x] Nenhuma regra de treino, ciência ou schema existente muda.
- [x] Gates do AGENTS.md verdes; `PROGRESS`/`PRD`/este handoff atualizados.

### 4.7 UI de histórico (decisões 17/25) — concluída

- `CoachMessageDao.observeAll`/`CoachSummaryDao.observeAll` entregam Flow (mais
  recente primeiro) e o repositório mapeia para `CoachMemoryMessage`/
  `CoachMemorySummary` puros; linha ilegível é ignorada, bullets inválidos viram
  lista vazia.
- `CoachHistoryState` cobre `Hidden` (sem opt-in), `Loading`, `Empty`, `Ready` e
  `Error`; `clearHistory()` usa `CoachMemoryRepository.clearAll()` em escopo de
  aplicação e `retryHistory()` reativa a coleta.
- `CoachHistoryCard` mostra resumos por semana e conversa em leitura, avisa que o
  motor decide, tem retry no erro e “Apagar histórico” com confirmação explícita
  (treinos/perfil/check-ins intactos). TalkBack, dark/light e fonte 200% cobertos.
- Testes: repositório (2), ViewModel (4) e tela (8). Sem IA nova, sem schema novo
  e sem memória em prompt.

### 4.8 Apagar item individual — concluído

- `CoachMessageDao.deleteById`/`CoachSummaryDao.deleteByWeek` e
  `CoachMemoryRepository.deleteMessage(id)`/`deleteSummary(weekStart)` removem
  só a linha pedida; item inexistente retorna `false` e não quebra a lista.
- `CoachViewModel.deleteHistoryMessage`/`deleteHistorySummary` rodam em escopo de
  aplicação, exigem opt-in e usam `Error` com retry se a escrita falhar.
- Cada linha do `CoachHistoryCard` tem “Apagar” (alvo de 48 dp e
  `contentDescription` próprio) com confirmação por item; o apagar em bloco
  continua. Treinos/perfil/check-ins não são tocados e não há edição.
- Testes: repositório (2), ViewModel (2) e tela (2). O flake de corrida do
  `TodayViewModelPersistTest` foi corrigido esperando também a
  `completionMessage`, sem reduzir assert.

### 4.9 Auditoria da aba Coach no aparelho — concluída

- **Parser estrito:** `HttpCoachApi.parse` exige `title`/`body` como strings
  não vazias; body objeto, campos ausentes, JSON inválido e título vazio viram
  `IOException` e caem no determinístico, sem gravar memória.
- **Proxy:** os system prompts de explain/review/replan passam a pedir strings e
  proibir body objeto; o redeploy no Dokploy é manual e fica pendente.
- **Legado:** linhas cujo body começa com `{` são ignoradas na leitura; nada é
  apagado à força e o TTL continua.
- **Histórico sem espelho:** a explicação corrente e o resumo da semana da
  revisão não aparecem no arquivo; a mensagem `REVIEW` é substituída pelo resumo
  do mesmo `inputsHash`. O arquivo mostra só o que não está na tela.
- **Utilidade:** card de explicação com 5 linhas + expandir, header curto,
  “Desativar” no fim/neutro, labels “Perdi treino”/“Menos tempo” e lixeira
  discreta por item.
- **Gear:** fundo circular com a cor do background para não misturar com o texto
  ao rolar (uma top bar fixa fica como evolução).
- **Limite da validação:** o build do aparelho não tinha token, então o card
  Ready e o histórico real precisam ser conferidos no build com
  `-PcoachProxyToken=...`. O overlay de fotos visto nas capturas era de outro
  app e não foi tratado como bug.

### 4.10 Conversa aberta + consentimento v2 (ADR 0063) — 24/11/2026

- **Problema:** `intent + slots` deixava perguntas legítimas em `OUT_OF_SCOPE`
  (ex.: "o que é fartlek?", "posso correr na chuva?", pace/FC bloqueado no
  parser). A conversa era um formulário com respostas fixas.
- **Decisão:** manter a porteira local para diagnóstico/nutrição/musculação/
  plano novo e abrir o resto como `ASK_COACHING`. A pergunta sanitizada vai no
  campo `question`; o `engineContext` leva um dossiê (semana, prescrição do
  dia, prontidão/sono/check-in, última semana e rollup de 4 semanas).
- **Validador:** `allowedCounts` barra contagem sem unidade fora dos fatos;
  seguem barrados pace/bpm, número/percentual/distância fora do contexto,
  verbo de prescrição com número/estrutura, código interno, léxico médico e
  promessa de memória. Inválido → fallback falado, não persiste.
- **Referência do motor:** o cartão só acompanha sessão/como correr ou uma
  resposta aberta que cite pace/ritmo/FC/zona/esforço/treino de hoje.
  Definição genérica ("o que é intervalado?") não mostra mais o pace/FC do dia.
- **Consentimento:** `CoachConsentStore.CONSENT_VERSION = 2`; um grant antigo
  (só resumo) exige novo aceite. A copy diz que a IA responde as perguntas
  digitadas e envia um resumo do treino, da prontidão e o texto, sem nome e sem mapa.
- **Proxy:** `SYSTEM_FREE_ANSWER` responde à `question` usando só o dossiê;
  **redeploy manual no Dokploy pendente**. O app já manda os campos novos e a
  pergunta também no `engineContext`, então um proxy antigo não quebra.
- **Testes:** parser (pace/dia, dúvidas abertas, afirmação fora do tema),
  validador (`allowedCounts`), `CoachOpenAnswer`, `CoachApi`/payload,
  consentimento v1→v2, VM (pergunta/fatos, contagem inventada cai sem gravar,
  falha de rede) e tela (copy do hint/convite).

### 4.11 Lembrete fiel ao dia (ADR 0064) — 24/11/2026

- **Problema:** o lembrete das 07:00 lia `WeeklyPlan` e dizia "40 min
  intervalado" mesmo depois de o Hoje rebaixar o treino por prontidão.
- **Decisão:** `ReminderWorker` resolve o dia via `GetTodayDecision`/
  `DailyPrescription` (prontidão, carga, calor, recuperação, prova). O corpo
  mostra o título/duração prescritos e, quando difere do plano, começa com
  "Ajustado para hoje".
- **App inteiro:** `TodayPrescriptionSync` observa check-in/métricas/
  atividades/execuções/perfil/semana/provas no escopo de aplicação e reavalia
  o dia append-only. `WorkoutReminderSync` reescreve a notificação ativa em
  silêncio ou cancela em descanso/prova/`DONE`/`CLOSED`; não ressuscita uma
  notificação dispensada.
- **Sem regra nova:** nenhuma duração, pace, FC ou plano nasce do lembrete; ele
  só lê a decisão. Sem schema, sem canal/permissão nova.

---

## 5. Pendências restantes

### 5.1 3.3 — estado

- Remover um dia inteiro e mais de um dia por pedido: **concluído na 3.3-C** (`0` remove o dia; um valor de minutos para todos os dias selecionados).
- Aumentar a disponibilidade temporária (“quero mais tempo”) permanece fora do escopo por ora.
- Tempos diferentes por dia no mesmo pedido não são suportados; se virarem necessidade real, viram outra fatia.

### 5.2 Goldens Roborazzi da bar — concluída

- `BottomBarScreenshotTest` grava `bottom-bar-dark.png`/`bottom-bar-light.png` a 393 dp com o Coach selecionado e verifica existência dos cinco tabs, seleção e clique.
- `McBottomBar` tem `testTag("bottom-bar-{route}")`; `destinations`/`AppDestination` ficaram `internal` para o teste usar a lista real.
- O gate continua explícito: `./gradlew :app:verifyRoborazziDebug --tests 'com.meucoach.app.ui.navigation.BottomBarScreenshotTest'`. O drift pré-existente de `activity-comparison-*`, `checkin-*` e `health-*` não foi tocado.
- Ao alterar a barra, regravar os dois goldens e revisar dark/light.

### 5.3 UI de histórico do Coach — concluída

- Decisões 17 (histórico + conversa) e 25 (vê/apaga, sem edição): lista somente leitura, só com opt-in, resumos + conversa, estados loading/vazio/erro, retry, “Apagar histórico” em bloco e apagar item a item com confirmação.
- `deleteMessage`/`deleteSummary` removem só a linha pedida; treinos/perfil/check-ins ficam intactos. O input livre de conversa continua fora do MVP.
- A memória continua local, expira por TTL e é apagada na revogação/“apagar tudo”; nenhum caminho a usa para decidir treino.

### 5.4 Encerramento

- A IA Coach está concluída no MVP, recebeu input livre (0056/0057) e conversa
  aberta com fatos fechados (0063); não há pendência funcional sem token.
- Validação restante: gerar o APK com `-PcoachProxyToken=...`, abrir o Coach e
  conferir o card Ready recolhido/expandido, o histórico real, uma pergunta
  aberta ("o que é fartlek?"), a recusa médica local e o consentimento v2.
- Reimplantar o proxy no Dokploy para o `SYSTEM_FREE_ANSWER` novo (pergunta +
  dossiê) e para os prompts com `title`/`body` strings.
- O lembrete de treino já segue a prescrição do dia (ADR 0064); validar no
  aparelho a reescrita silenciosa depois do check-in e o cancelamento em
  descanso/prova/conclusão.
- Pós-MVP: memória como contexto de copy em prompt e “quero mais tempo” na
  disponibilidade temporária.
- Próxima prioridade: P1-12 (4 semanas de uso real) / Ciclo 4.

---

## 6. Build e ambiente

Comandos:

```bash
./install.sh
./gradlew :app:testDebugUnitTest --tests 'com.meucoach.app.ui.coach.*' --tests 'com.meucoach.app.ui.replan.*' --tests 'com.meucoach.app.application.*' --tests 'com.meucoach.app.replan.*'
./gradlew :core:data:testDebugUnitTest --tests 'com.meucoach.core.data.repository.TrainingPlanRepositoryTest'
./gradlew :core:data:testDebugUnitTest --tests 'com.meucoach.core.data.db.MeuCoachDatabaseMigrationTest'
./gradlew :core:data:testDebugUnitTest --tests 'com.meucoach.core.data.coach.CoachMemoryRepositoryTest'
./gradlew :core:domain:test :core:data:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew check -x :app:testDebugUnitTest
./gradlew spotlessApply
./gradlew -p infra/coach-proxy test
./gradlew :app:recordRoborazziDebug --tests 'com.meucoach.app.ui.navigation.BottomBarScreenshotTest'
./gradlew :app:verifyRoborazziDebug --tests 'com.meucoach.app.ui.navigation.BottomBarScreenshotTest'
```

`./install.sh` (raiz) compila, pergunta o token do Coach **a cada execução** e instala com `adb install -r`; Enter vazio instala sem IA. O token não é salvo no repo.

Proxy (não commitado):

- URL: `https://meucoach-api.hojeafestaenossa.site`
- Build com `-PcoachProxyUrl=... -PcoachProxyToken=...`; vazio = Coach desativado e fallback.
- Token de produção no Dokploy (`COACH_PROXY_TOKEN`); chave OpenRouter em `OPENROUTER_API_KEY`, chave do Go em `COACH_GO_API_KEY` (`COACH_GO_MODELS` opcional, default `glm-5.3-flash,mimo-v2.6-flash,deepseek-v4.1-flash`). OpenRouter é free-only por política (sem créditos naquela conta); a cota é a da assinatura Go. Nunca no repo, APK, log, issue ou prompt.
- Backup real em `fixtures/private/` (gitignored); não usar em CI.

Flakes conhecidos (ver `TESTING.md`): `TutorialSystemBackTest` (transação Room vazada) e `MetricsImportViewModelTest` (timeout de 30 s). Rodar isolado ou `--no-parallel`; consertar, não ignorar.

---

## 7. Definition of Done da sessão

- [x] Compila (`assembleDebug`).
- [x] Testes novos e existentes verdes; `spotlessCheck` e `koverVerify` verdes.
- [x] Detekt/lint sem erros; sem adicionar achado ao baseline sem justificativa.
- [x] Goldens da barra gravados e verificados em dark/light.
- [x] Nenhum dado real no diff; migração 18→19 aditiva, sem `fallbackToDestructiveMigration`.
- [x] Docs da regra/UX + `PROGRESS.md` + este handoff atualizados.
- [x] Commit em Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`).
