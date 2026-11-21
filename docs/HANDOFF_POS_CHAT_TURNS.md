# HANDOFF — Pós-ChatTurn (epic encerrado e validado no aparelho)

**Atualizado:** 21/11/2026 ~15:10 (BRT)
**Status:** F4 + epic `ChatTurn` (fatias 1–4) implementados, gates verdes e **validados no aparelho via adb**.
**Branch:** `dev` — tudo pushado até `061b0f3`.

---

## 1. Onde estamos (commits no ar, `dev`)

| Commit | O quê |
|---|---|
| `b2501d9` | F4-1: abertura do dia orientada a dados (`CoachOpeningLabels`) |
| `6424173` | F4-2a: system turn pós check-in |
| `c4b4b09` | F4-2b: system turns pós treino/import + `RoomCoachNextSource` via `coachSignals` |
| `777f99e` | ChatTurn f1: domínio puro + ADR 0054 |
| `1cb0dff` | ChatTurn f2: `chat_turns` + `MIGRATION_19_20` + teste dourado + schema `20.json` |
| `893d9f6` | ChatTurn f3: dual write, `observeTurns`, export/delete (`coachTurns`) |
| `a7933d5` | ChatTurn f4: `Ready(turns)`, delete unificado, card como timeline |
| `061b0f3` | docs: validação da fatia 4 no aparelho |

DB atual: **v20**. Proxy/Dokploy: OK (`explain ok` + `review ok` com acentos em 21/11 15:05).

## 2. Evidência da última validação (21/11, via adb, tela Coach aberta)

- Abertura: “Boa tarde! Amanhã tem Fácil e conversável · 40 min. Dorme bem hoje que conta.”
- Explicação em 3 bolhas, acentuada, números consistentes (longa de terça 34 min).
- Retrovisor rola até “Semana passada / Evolução / Histórico”; fade legível.
- Histórico: sem títulos de seção (timeline única) + diálogo genérico
  “Apagar este item do histórico?” (build fatia 4 confirmado); cancelado, nada apagado.
- Sem resumo gravado para o usuário 0 ainda — intercalado resumo+turno pendente de uso real.

## 3. Próxima tarefa recomendada — v21 (fechar o epic)

Remover a escrita dupla e as tabelas v19 (`coach_messages`, `coach_summaries`):

1. `recordMessage/recordSummary` escrevem **só** em `chat_turns`; `observeMessages/observeSummaries` e as entidades/DAOs v19 saem (ou viram banidos pelo detekt se sobrar uso).
2. `MIGRATION_20_21`: `DROP TABLE coach_messages/coach_summaries` (+ índices). Sem backfill (já migrado na 19→20).
3. Atualizar `MigrationTest` (cadeia até 21), `UserDataRepository` (export/delete sem as chaves v19 — **atenção**: export antigo com `coachMessages`/`coachSummaries` continua legível? hoje não há import de backup, só export; documentar), `MigrationTest` dourado 20→21.
4. Schema `21.json` exportado.
5. Gates: `:core:data:testDebugUnitTest --rerun-tasks`, `check`, `spotlessCheck`, proxy `test`.

Fora da fatia: qualquer mudança de UI/copy, input livre, `You`/`System` persistidos.

## 4. Depois da v21 (pós-MVP, só após F1–F4 validados — já estão)

- **Input livre digitado** (maior valor; exige ADR antes: parser de intenção, o que o validador barra, o que nunca vira prescrição).
- Unificar card replan × quick replies (aguardava 1 semana de observação).
- `P1-12`: 4 semanas de uso real rodando em paralelo.

## 5. Protocolo da sessão (AGENTS.md)

1. Ler `docs/PROGRESS.md` (topo) + roadmap; uma tarefa pequena e verificável.
2. TDD: teste que falha → código → gates.
3. Mudança de arquitetura/ciência → ADR (novo, sem reescrever o passado).
4. `check` + `spotlessCheck` + proxy `test` verdes; `PROGRESS.md` atualizado.
5. Nunca commitar dado real (`.fit/.tcx/.gpx`, prints, `local.properties`, keystore).
6. Aparelho via adb: `/home/rafaellima/Android/Sdk/platform-tools/adb` (`adb devices` → `RQCX209N5SK`); screenshots em `/tmp/opencode/` (fora do repo, apagar ao final); coordenadas de toque calibrar por crop (`magick`).
7. `./install.sh` precisa do token do Coach (só o usuário 0 tem); sem token = build sem IA.
