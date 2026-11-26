# HANDOFF — Pós-ChatTurn (epic encerrado; estado atual em §0)

**Atualizado:** 26/11/2026
**Status:** §1–§4 são o registro histórico de 21/11/2026. O estado atual está em §0.
**Branch:** `dev`.

---

## 0. Estado atual (26/11/2026)

- **DB v22** (`chat_turns` como tabela única; `origin` nullable para `YOU`). O epic `ChatTurn` foi encerrado e ampliado por input livre (ADRs 0056/0057), conversa aberta (ADR 0063), lembrete fiel (ADR 0064) e dossiê do executado (emenda ADR 0063).
- **Fatia 7 da documentação concluída:** `PRODUCT.md` v1.3, `ROADMAP.md` por subfeature, `TESTING.md` com flakes/contagens, `DECISIONS.md`/`ARCHITECTURE.md` e `PRD_IA_COACH.md` (timeouts reais e escopo médico fail-closed). Nenhuma feature, schema, migração ou dependência nova.
- **Motores:** `weekly-plan-v9`/`daily-prescription-v7`; ADRs 0066–0069 implementados e goldens executáveis.
- **Pendente e não substituível por teste JVM:** redeploy do proxy no Dokploy com os prompts novos e validação no aparelho com token (Fatia 8). P1-12 (4 semanas de uso real) segue aberto.
- **Próxima fatia:** Fatia 8 — checklist manual no aparelho com o usuário 0. Não marcar validação física sem aparelho.

---

## 1. Onde estávamos (21/11/2026 — histórico)

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

DB à época: **v20** (hoje v22 — ver §0). Proxy/Dokploy: OK em 21/11 (`explain ok` + `review ok` com acentos em 21/11 15:05); o redeploy dos prompts novos segue pendente.

## 2. Evidência da última validação (21/11, via adb, tela Coach aberta)

- Abertura: “Boa tarde! Amanhã tem Fácil e conversável · 40 min. Dorme bem hoje que conta.”
- Explicação em 3 bolhas, acentuada, números consistentes (longa de terça 34 min).
- Retrovisor rola até “Semana passada / Evolução / Histórico”; fade legível.
- Histórico: sem títulos de seção (timeline única) + diálogo genérico
  “Apagar este item do histórico?” (build fatia 4 confirmado); cancelado, nada apagado.
- Sem resumo gravado para o usuário 0 ainda — intercalado resumo+turno pendente de uso real.

## 3. Próxima tarefa recomendada à época — v21 (histórico; concluída)

Remover a escrita dupla e as tabelas v19 (`coach_messages`, `coach_summaries`):

1. `recordMessage/recordSummary` escrevem **só** em `chat_turns`; `observeMessages/observeSummaries` e as entidades/DAOs v19 saem (ou viram banidos pelo detekt se sobrar uso).
2. `MIGRATION_20_21`: `DROP TABLE coach_messages/coach_summaries` (+ índices). Sem backfill (já migrado na 19→20).
3. Atualizar `MigrationTest` (cadeia até 21), `UserDataRepository` (export/delete sem as chaves v19 — **atenção**: export antigo com `coachMessages`/`coachSummaries` continua legível? hoje não há import/restauração de backup, só exportação JSON; documentar), `MigrationTest` dourado 20→21.
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
