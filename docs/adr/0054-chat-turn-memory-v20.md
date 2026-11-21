# ADR 0054 — Timeline única do Coach (`chat_turns`, Room v20)

**Status:** Aceito e implementado em 21/11/2026 (fatias 1–4: domínio, v20, repository, histórico).
**Data:** 2026-11-21
**Escopo:** `core/domain/coach/ChatTurn.kt`, futuro `chat_turns` (v20),
`CoachMemoryRepository`, `CoachViewModel`, `UserDataRepository`, `MigrationTest`.
**Relacionados:** ADR 0002 (local-first), ADR 0020 (backup/export), ADR 0025
(exclusão e época de dados), ADR 0051 (proxy stateless), ADR 0052 (memória v19),
`docs/PRD_COACH_CHAT.md` §5.1/§9.
**PRD:** `docs/PRD_COACH_CHAT.md` decisão F3 ("fundir `messages+summaries+eventos`
num `ChatTurn` persistido", observar 1 semana — observação concluída em 21/11/2026
com a F4 validada no aparelho).

---

## 1. Contexto

### 1.1 O problema

A memória do Coach vive em duas tabelas com dois vocabulários: `coach_messages`
(turnos `EXPLAIN`/`REVIEW` com `inputsHash`) e `coach_summaries` (resumo por
`weekStart`). A aba reconstrói o arquivo a cada abertura via `CoachHistory.view`
e o histórico em bottom sheet mostra "1 resumo + 2 turnos + ver conversa" —
arquivo morto, não continuação da conversa. O PRD pede uma timeline única
(`ChatTurn.You/Coach/System`) onde o arquivo é continuação da timeline (§5.1).

### 1.2 Restrições (herdadas do ADR 0052, mantidas)

- Só texto validado pelo `CoachResponseValidator` é persistido; `ChatTurn` nunca
  vira fonte de número, pace, carga ou plano.
- TTL por origem: turnos de explicação/revisão 90 d, resumos 180 d
  (`CoachMemoryPolicy`).
- Revogar o opt-in apaga tudo; export/delete incluem a memória (ADR 0020/0025).
- Sem `fallbackToDestructiveMigration`; migração explícita com `MigrationTest`.
- `core:domain` continua Kotlin puro (sem Android/Room/Hilt).
- `You` (input livre digitado) e `System` (eventos) seguem **efêmeros**: o feed já
  os deriva do estado vivo. Persisti-los é fatia futura com ADR próprio.

## 2. Decisão

### 2.1 Domínio (fatia 1 — implementada)

- `sealed interface ChatTurn` com `ChatTurn.Coach(id, title, body, bullets,
  createdAt, origin)` e `Origin { EXPLAIN, REVIEW, SUMMARY }` em
  `core/domain/coach/ChatTurn.kt` — puro, sem Android/Room/Hilt.
- `ChatTurns.fromMemory(messages, summaries, currentExplainHash,
  currentReviewWeek)` reutiliza `CoachHistory.view` (paridade total das regras:
  resumo substitui review do mesmo hash, explicação corrente e semana em revisão
  ficam de fora) e intercala por `createdAt` desc (empate: resumo antes).
- IDs de turn derivados e estáveis (`message-{id}`, `summary-{weekStart}`).

### 2.2 Persistência (fatia 2 — planejada, Room v20)

Nova tabela `chat_turns`, aditiva (as tabelas v19 permanecem até a v21):

```sql
CREATE TABLE chat_turns (
  id TEXT PRIMARY KEY,
  role TEXT NOT NULL,            -- 'COACH' (v20); 'YOU'/'SYSTEM' reservados
  title TEXT NOT NULL,
  body TEXT NOT NULL,
  bulletsJson TEXT NOT NULL,     -- JSON array, como nas tabelas v19
  origin TEXT NOT NULL,          -- 'EXPLAIN' | 'REVIEW' | 'SUMMARY'
  weekStart TEXT,                -- só para origin SUMMARY
  createdAt INTEGER NOT NULL,
  expiresAt INTEGER NOT NULL,    -- TTL por origem calculado na escrita
  UNIQUE(origin, weekStart)       -- um resumo por semana (quando weekStart não nulo)
);
CREATE INDEX index_chat_turns_createdAt ON chat_turns (createdAt);
CREATE INDEX index_chat_turns_expiresAt ON chat_turns (expiresAt);
```

- `MIGRATION_19_20`: cria a tabela + índices e faz **backfill** —
  `coach_messages` → `role='COACH'`, `origin=kind`, `expiresAt =
  createdAt + 90 d`; `coach_summaries` → `role='COACH'`, `origin='SUMMARY'`,
  `weekStart` preenchido, `expiresAt = createdAt + 180 d`. Linhas corrompidas
  (título/corpo em branco, `body` objeto JSON — os mesmos filtros do repository)
  ficam de fora do backfill.
- O repository passa a ler de `chat_turns` (ordenado por `createdAt` desc);
  `recordMessage/recordSummary` escrevem em `chat_turns` (mantêm a escrita nas
  tabelas v19 por uma release para downgrade seguro — removida na v21).
- Poda por `expiresAt < now` (em vez de dois cutoffs por tabela).
- `clearAll`, `deleteMessage/deleteSummary` (vira `deleteTurn(id)`),
  export/delete do `UserDataRepository` e `DataManagementScreen` passam a cobrir
  `chat_turns` — paridade de privacidade com o ADR 0052.
- `MigrationTest`: v19 com linhas douradas (message + summary + linha corrompida)
  → v20 confere backfill, TTL e exclusão da corrompida.

### 2.3 UI (fatia 3 — planejada)

- `CoachHistoryState.Ready` passa a carregar `List<ChatTurn>` via
  `ChatTurns.fromMemory` (ou direto de `chat_turns` após a v20); o histórico em
  bottom sheet vira continuação da timeline (resumo + turnos intercalados),
  mantendo "Ver conversa completa (N)" e apagar por item.
- Nenhum número novo, nenhuma decisão de treino, nenhuma mudança de contrato
  com o proxy.

## 3. Alternativas avaliadas

| Alternativa | Por que não |
|---|---|
| View em SQL (`UNION` messages+summaries) | Regras de visibilidade moram no domínio e têm testes; duplicá-las em SQL diverge. |
| Apagar as tabelas v19 na v20 | Downgrade 20→19 perderia histórico; aditivo + remoção na v21 é o padrão do repo. |
| `expiresAt` global único | Quebraria a paridade 90/180 d do ADR 0052 sem motivo. |
| Persistir `You`/`System` já nesta fatia | Input livre e eventos persistidos exigem desenho próprio (conteúdo do usuário, retenção); fora do PRD desta fase. |

## 4. Rollout

1. ~~Fatia 1 (esta): domínio `ChatTurn` + `ChatTurns.fromMemory` + `ChatTurnTest`.~~
2. Fatia 2: entidade/DAO/`MIGRATION_19_20` + backfill + `MigrationTest` dourado.
3. Fatia 3: repository lendo `chat_turns` (escrita dupla temporária) + export/delete.
4. Fatia 4: `CoachHistoryState` com `List<ChatTurn>` + histórico como timeline.
5. v21 (futura): remover escrita dupla e tabelas v19.
