# ADR 0048 — Prontidão de dados, upgrade do plano e schema v18

**Status:** Aprovado em 2026-11-16 — implementação pendente (C4-44..C4-49).
**Data:** 2026-11-16
**Escopo:** `HealthSyncCoordinator`/`HealthSyncState`, `TodayViewModel`, `PlanUpgrade`,
`PlanReplanner`, `WeeklyPlan`/`WeeklyPlanEntity`, `DailyPrescription`/entidade,
`MeuCoachDatabase` schema v18, serialização de plano/prescrição.
**Relacionados:** ADR 0022 (tempo), ADR 0023 (plano × prescrição), ADR 0028
(replanejamento), ADR 0031 (fingerprint), ADR 0044 (qualidade), ADR 0045 (longão),
ADR 0046 (carga), ADR 0047 (catálogo).
**PRD:** `docs/PRD_TREINO_REAL.md` FR-409, FR-413, §11.5 (Q-10).
**Decisão do usuário 0:** 16/11/2026.

---

## 1. Contexto

### 1.1 O que existe hoje

- `TodayViewModel` gera o plano na primeira composição e o persiste
  (`TodayViewModel.kt:197-225`). O `HealthSyncCoordinator.syncOnResume()` é
  disparado no `LifecycleResumeEffect` (`MeuCoachApp.kt:133-140`) em paralelo.
- No caso real, o plano foi salvo **8 min antes** de as métricas do dia
  chegarem; o plano declarou “prontidão não está verde” e a semana ficou sem
  qualidade.
- O `PlanReplanner` não reage a dados novos de prontidão/carga e não pode
  adicionar qualidade além da intenção futura (`PlanReplanner.kt:36-77,120-135`).
- O `WeeklyPlan` não persiste a versão do gerador. O `weekly_plans` tem
  `version` (revisão), `generatedFromFingerprintVersion` (planning-v1), mas não
  `generatorVersion`.
- `structure`/`decision` não têm onde ser persistidos; o `targetsJson` é sobre
  alvos, não sobre a estrutura da sessão.
- `TrainingPlanRepository.saveWeek` grava `createdAt = clock.millis()` em toda
  gravação; o campo deixa de ser “criação” (`:144-170`).
- `daily_prescriptions` já tem `engineVersion`; o plano não tem versão do
  gerador.

### 1.2 O problema

O plano pode ser gerado com dados incompletos, é congelado e não há mecanismo
de upgrade. A nova estrutura da sessão e os códigos de decisão precisam de
persistência. Sem isso, a oportunidade de qualidade (ADR 0044), o longão (ADR
0045) e o catálogo (ADR 0047) não chegam ao usuário de forma confiável.

### 1.3 Decisão do usuário 0

Q-10: schema v18 aditivo com `generatorVersion`, `structureJson` e códigos de
decisão, mais `PlanUpgrade` idempotente na semana vigente, histórico preservado.

---

## 2. Decisão

### 2.1 Gate de dados

`HealthSyncCoordinator` expõe:

```kotlin
sealed interface HealthSyncState {
    data object Idle : HealthSyncState
    data object Syncing : HealthSyncState
    data class Ready(val at: Instant) : HealthSyncState
    data class Failed(val message: String) : HealthSyncState
}
```

O `TodayViewModel` só gera o plano quando:

- perfil carregado;
- eventos carregados;
- atividades carregadas (primeira emissão do Room);
- `HealthSyncState` é `Ready` ou `Failed`/`Unavailable`;
- ou timeout de **3 s** → gera com `dataIncomplete = true`.

Enquanto o gate não fecha, o Hoje mostra “Sincronizando seus dados…”. Se o
sync falhar, o plano é gerado com `dataIncomplete` e rationale explícito; quando
o sync completar, o `PlanUpgrade`/`DATA_READY` reavalia a semana vigente.

O gate não espera o check-in: a ausência de check-in é um estado válido e o
`QualityGate` (ADR 0044) decide a qualidade do dia.

### 2.2 `PlanUpgrade`

Na primeira abertura após a atualização, se o plano da semana vigente tem
`generatorVersion != WeeklyPlanGenerator.ENGINE_VERSION`:

1. Preservar hoje, passado, `DONE` e `CLOSED`.
2. Regenerar apenas os dias estritamente futuros.
3. Permitir **1 oportunidade estrutural** de qualidade ausente
   (`allowStructuralQualitySlot = true`).
4. Não adicionar volume, longão extra ou segunda qualidade.
5. Preservar `createdOn`/`createdAt` originais; registrar `revisedAt`, `version`,
   `WeeklyPlanRevision` com `trigger = ENGINE_UPGRADE`.
6. Ser idempotente e transacional.
7. Não tocar em semanas passadas.

O `PlanUpgrade` é o único caminho de atualização da semana vigente; o
`PlanReplanner` continua para os gatilhos existentes (perfil, fuso,
disponibilidade, sessões perdidas, recuperação) e ganha `DATA_READY` para o caso
de plano gerado com `dataIncomplete`.

### 2.3 Schema v18

Migração aditiva, sem `fallbackToDestructiveMigration`:

| Tabela | Coluna | Tipo | Default | Uso |
|---|---|---|---|---|
| `weekly_plans` | `generatorVersion` | TEXT | `'weekly-plan-v6'` | versão do gerador |
| `planned_sessions` | `structureJson` | TEXT | NULL | estrutura da sessão |
| `planned_sessions` | `qualityKind` | TEXT | NULL | natureza do estímulo (C4-45; fora da tabela original: sem a coluna o kind seria descartado a cada leitura) |
| `planned_sessions` | `planDecisionCode` | TEXT | NULL | motivo do plano |
| `planned_sessions` | `planDecisionDetail` | TEXT | NULL | detalhe legível |
| `daily_prescriptions` | `structureJson` | TEXT | NULL | estrutura prescrita |
| `daily_prescriptions` | `qualityBlockReason` | TEXT | NULL | motivo do dia |
| `daily_prescriptions` | `decisionDetail` | TEXT | NULL | detalhe legível |
| `daily_prescriptions` | `unlockHint` | TEXT | NULL | dica de desbloqueio |

- `WeeklyPlan` ganha `engineVersion: String`.
- `PlannedSession` ganha `structure`, `planDecision`.
- `DailyPrescription` ganha `structure`, `qualityBlockReason`, `decisionDetail`,
  `unlockHint`.
- `PlanJson`/`PrescriptionJson` serializam os novos campos com `version: 1` e
  retrocompatibilidade (campos ausentes = null).
- `createdAt` deixa de ser sobrescrito no `saveWeek`; a revisão usa `revisedAt`/
  `version`. O `createdOn` original é preservado no upgrade.

### 2.4 Versões de motor

| Motor | Antiga | Nova |
|---|---|---|
| Plano semanal | `weekly-plan-v6` | `weekly-plan-v7` |
| Prescrição diária | `daily-prescription-v5` | `daily-prescription-v6` |
| Macro (se FR-411) | `macro-plan-v1` | `macro-plan-v2` |
| Biblioteca | implícita | `session-library-v2` (citada no rationale) |

O `DailyPrescription.inputsHash` já inclui a versão; o `WeeklyPlan` persiste a
versão. O `PlanReplanner` inclui a versão no `inputsHash` e na revisão.

### 2.5 Rollback

- O banco v18 não é aberto por uma versão antiga do app sem migração reversa.
  Para uso pessoal, reverter exige reinstalar/limpar ou exportar o JSON antes e
  restaurar depois.
- O `PlanUpgrade` é idempotente; rodar de novo não muda nada.
- Se o upgrade falhar, o plano v6 continua válido; o app tenta de novo na próxima
  abertura.
- O upgrade não apaga dados; o export existente pode ser usado como ponto de
  restauração.

### 2.6 Invariantes

- Nenhum plano é gerado antes do gate (ou com `dataIncomplete` explícito).
- O upgrade nunca reescreve hoje/passado/`DONE`/`CLOSED`.
- O upgrade nunca adiciona volume ou segunda qualidade.
- O histórico de planos/prescrições permanece.
- O `createdAt` é estável; `revisedAt` distingue a revisão.
- A migração é aditiva e testada.

---

## 3. Dados e APIs

- `HealthSyncState`/`HealthSyncCoordinator.state`.
- `TodayViewModel` gate + loading.
- `PlanUpgrade` use case; `ReplanTrigger.DATA_READY`/`ENGINE_UPGRADE`.
- `WeeklyPlanEntity.generatorVersion`.
- `PlannedSessionEntity.structureJson/planDecisionCode/planDecisionDetail`.
- `DailyPrescriptionEntity.structureJson/qualityBlockReason/decisionDetail/unlockHint`.
- `WeeklyPlan.engineVersion`.
- `PlanJson`/`PrescriptionJson` v2.
- `Room` schema v18 + migração 17→18; testes `MigrationTestHelper`.

---

## 4. Alternativas consideradas

1. **Esperar o sync indefinidamente.**
   Rejeitada: o app pode parecer travado; o timeout com `dataIncomplete` é
   honesto.
2. **Regenerar o plano em toda abertura.**
   Rejeitada: perde o histórico, reescreve decisões e quebra a autoridade do dia.
3. **Não persistir a versão do gerador.**
   Rejeitada: não há como saber se o plano precisa de upgrade; o usuário espera
   uma semana.
4. **Guardar estrutura/decisão em `targetsJson`.**
   Rejeitada: mistura alvos com estrutura; menos consultável e mais acoplado.
5. **Migração destrutiva.**
   Rejeitada pela regra do projeto.
6. **Upgrade de todas as semanas.**
   Rejeitada: reescreveria o passado; só a semana vigente.

---

## 5. Consequências

### 5.1 Positivas

- O plano nasce com dados completos (ou declara que não nasceu).
- O usuário vê a nova versão na semana vigente, sem esperar segunda.
- A estrutura e a decisão ficam auditáveis.
- O histórico é preservado; a migração é reversível por export.
- O `createdAt` volta a significar criação.

### 5.2 Riscos

- A migração v17→v18 precisa de teste; o app é local, mas os dados são valiosos.
- O gate de 3 s pode atrasar o Hoje; a UI precisa de loading.
- O upgrade pode falhar em casos de plano parcial; a transação e o fallback v6
  são obrigatórios.
- O rollback de app é limitado; documentar e recomendar export.

### 5.3 Segurança

- O upgrade preserva `DONE`/`CLOSED` e não altera sessões concluídas.
- O upgrade não aumenta volume; só adiciona a oportunidade estrutural.
- O gate não inventa dados; `dataIncomplete` é explícito.
- O `QualityGate` continua sendo a autoridade do dia.

---

## 6. Matriz de testes

| Caso | Entrada | Saída |
|---|---|---|
| `G-UPGRADE-01` | Plano v6 com DONE na terça e fácil na sexta | v7; terça preservada; oportunidade na sexta; histórico |
| Gate | Sync `Syncing` | Hoje em loading; sem plano salvo |
| Timeout | Sync não responde em 3 s | Plano com `dataIncomplete`; upgrade depois |
| Sync falha | `Failed` | Plano com os dados disponíveis; rationale |
| Idempotência | Rodar upgrade 2× | Nenhuma mudança na segunda |
| Transação | Falha no meio | Plano v6 intacto |
| Migração | 17→18 | Colunas/defaults; sem perda |
| JSON | Estrutura/decision | Round-trip; desconhecido → null |
| `createdAt` | Criar, salvar, revisar | `createdAt` estável; `revisedAt` muda |
| Histórico | Semana passada v6 | Intocada |

---

## 7. Rollout

1. Schema v18 + migração.
2. `PlanUpgrade` + gate.
3. `weekly-plan-v7`/`daily-prescription-v6`.
4. `PlanUpgrade` na primeira abertura; banner “Plano atualizado”.
5. Smoke no aparelho com dados sintéticos e leitura real.
6. `PROGRESS`/`ROADMAP`/`DATA_MODEL`/`ARCHITECTURE`/`TESTING`.

---

## 8. Fora de escopo

- Sync de backend.
- Migração destrutiva.
- Reescrever semanas passadas.
- Export/restore novo (o existente serve).
- Sessão guiada.

---

## 9. Referências

- `docs/PRD_TREINO_REAL.md` FR-409/FR-413, §11.5 (Q-10).
- ADR 0022, 0023, 0028, 0031, 0044, 0045, 0046, 0047.
- `docs/ARCHITECTURE.md`, `docs/DATA_MODEL.md`, `docs/TESTING.md`.
