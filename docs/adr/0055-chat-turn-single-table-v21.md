# ADR 0055 — Coach em tabela única (`chat_turns`, Room v21)

**Status:** Aceito e implementado em 21/11/2026 (v21: escrita só em `chat_turns`).
**Data:** 2026-11-21
**Escopo:** `ChatTurn`/`ChatTurns`, `ChatTurnEntity`, `CoachMemoryRepository`,
`CoachViewModel` (histórico), `UserDataRepository`, `DataManagementViewModel`,
`MIGRATION_20_21`.
**Relacionados:** ADR 0020 (backup/export), ADR 0025 (exclusão e época de dados),
ADR 0051 (proxy stateless), ADR 0052 (memória v19), ADR 0054 (timeline única v20).
**PRD:** `docs/PRD_COACH_CHAT.md` decisão F3/pós-F4.

---

## 1. Contexto

A ADR 0054 unificou a memória do Coach em `chat_turns` (v20) com escrita dupla
temporária nas tabelas v19 (`coach_messages`, `coach_summaries`) para downgrade
seguro. O passo 5 do rollout previa remover a escrita dupla e as tabelas v19 na
v21 — é esta decisão.

A troca não é só apagar: as regras de visibilidade do arquivo ("resumo substitui
o review do mesmo hash", "explicação corrente e semana em revisão ficam de fora")
precisam do `inputsHash` por turn, e `chat_turns` não o tinha. Sem ele, ler direto
da tabela única regrediria o histórico (duplicar explicação corrente e
review+resumo idênticos). As tabelas v19, ainda presentes na migração, são a única
fonte desse contexto para as linhas legadas.

## 2. Decisão

### 2.1 Schema (v21)

- `chat_turns` ganha `inputsHash TEXT` nullable + índice único
  `(origin, inputsHash)`, que restaura a garantia v19 de escrita única por
  contexto (reabrir a aba ou repetir não duplica a conversa). `NULL` nunca
  conflita no SQLite e nunca esconde um turn: ausência reduz a filtragem, não
  a leitura.
- `MIGRATION_20_21`: adiciona a coluna, preenche `inputsHash` a partir das
  tabelas v19 (turnos `message-*` via `coach_messages.id`, turnos `summary-*`
  via `coach_summaries.weekStart`) e só então derruba `coach_messages` e
  `coach_summaries` com seus índices. Sem backfill de linhas (já feito na
  19→20) — só o preenchimento da coluna, sem nenhum número inventado.
- Entidades, DAOs e acessores v19 removidos; `VERSION = 21`; schema `21.json`
  exportado. `MigrationTest` dourado 20→21 (backfill de `inputsHash`, TTL
  intacto, tabelas v19 ausentes) e cadeia 1→21 atualizada.

### 2.2 Domínio

- `ChatTurn.Coach` ganha `inputsHash: String?` (ausência honesta, nunca `""`).
- `ChatTurns.visibleTurns(turns, currentExplainHash, currentReviewWeek)` aplica
  as regras do antigo `ChatTurns.fromMemory` direto nos turns (resumo esconde o
  review do mesmo hash não-nulo; explicação corrente e semana em revisão ficam
  de fora; empate de `createdAt` mostra o resumo antes) e ordena newest-first.
- O vocabulário v19 (`CoachMemoryMessage`, `CoachMemorySummary`,
  `CoachHistory.view`, `fromMemory`) sai: mesma cobertura, agora sobre turns.

### 2.3 Repositório e app

- `recordMessage/recordSummary` escrevem **só** em `chat_turns` (com poda por
  `expiresAt` em toda escrita); `observeMessages/observeSummaries`,
  `deleteMessage/deleteSummary` e a poda por dois cutoffs saem.
  `CoachMemoryPruneResult` volta a contar só `turns`.
- `CoachViewModel` observa `observeTurns()` + `visibleTurns` com seu estado vivo;
  `deleteHistoryMessage/Summary` saem, `deleteHistoryTurn` apaga só o turn.
  Efeito colateral corrigido: o `toTurn` da f3 não preenchia `weekStart`, então
  resumos lidos do banco perdiam a semana; a v21 preenche.
- Export perde `coachMessages`/`coachSummaries` e o turn exportado ganha
  `inputsHash`; `UserDataDeletion` perde as duas contagens e a nota da tela
  Dados vira "O histórico do Coach também foi apagado (N turno(s))".
- Não há import de backup no produto (só export), então exports antigos com as
  chaves v19 não precisam ser lidos: seguem como arquivo morto legível fora do
  app. Sem mudança de motor, ciência, contrato com o proxy ou regra de treino.

## 3. Alternativas avaliadas

| Alternativa | Por que não |
|---|---|
| v21 só com `DROP TABLE` (handoff §3 literal) | `visibleTurns` sem `inputsHash` não filtra explicação corrente nem review coberto pelo resumo — regressão visível no aparelho. |
| Manter `fromMemory` lendo turns convertidos | A conversão recriaria `(message, summary)` sem `inputsHash` real; mesma lacuna, mais código morto. |
| Preencher `inputsHash` sem as tabelas v19 | Não há outra fonte para linhas legadas; inventar hash violaria a regra de dado ausente. |

## 4. Rollout

1. ~~v21: escrita só em `chat_turns` + `MIGRATION_20_21` + export/delete + schema.~~
2. Próximo (pós-MVP): input livre digitado (exige ADR de parser/intenção).
