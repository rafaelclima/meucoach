# ADR 0050 — Volume real, treino extra e reparo de evidência legada

**Status:** Aprovado em 2026-11-16 — implementação em andamento (C4-38..C4-40 concluídas; golden, copy e docs em C4-41..C4-43).
**Data:** 2026-11-16
**Escopo:** `VolumeProgression`, `MesoCycle`, `LevelGraduationEngine`,
`VolumeReference`, `StrongRun`, `TrainingPlanRepository`/`ActivityRepository` (projeção),
`SessionCompletionRules`, `TodayViewModel`/`ImportService` (reparo).
**Relacionados:** ADR 0024 (evidência), ADR 0038 (progressão), ADR 0041
(mesociclo), ADR 0042 (graduação), ADR 0044 (qualidade), ADR 0046 (carga).
**PRD:** `docs/PRD_TREINO_REAL.md` FR-404, FR-408, §11.5 (Q-03, Q-09).
**Decisão do usuário 0:** 16/11/2026.

---

## 1. Contexto

### 1.1 O que existe hoje

- `TrainingLoadEngine` inclui **todas** as atividades na carga (`:360-419`); uma
  corrida extra aumenta o ACWR.
- `VolumeProgression.completedVolumeMinutes()` soma apenas `durationMin` de
  sessões **planejadas** com evidência forte (`:183-194`).
- `MesoCycle.isLoadWeek` olha o plano (`:40-44`).
- `LevelGraduation.hasLongRun` olha sessões planejadas (`:259-266`).
- O primeiro longão do cenário real ficou `LEGACY_UNVERIFIED`, sem `activityId`,
  e não contou como semana forte.
- `LEGACY_UNVERIFIED` é, por desenho, somente leitura; a correção só cobre
  `MANUAL` → `ACTIVITY` (ADR 0024).

### 1.2 O problema

O treino extra aumenta a fadiga e o ACWR, mas **não** aumenta a base nem conta
para a graduação. É um loop perverso: quanto mais o usuário treina por fora,
mais o app o segura. O primeiro longão importado no mesmo dia virou legado e não
contou; o usuário vê um treino real desaparecer da progressão.

### 1.3 Decisão do usuário 0

- Q-03: atividade forte não planejada conta para volume/graduação (longão real),
  mas não para adesão ao plano.
- Q-09: `LEGACY_UNVERIFIED` reparável só no import do mesmo dia, com confirmação;
  histórico migrado continua somente leitura.

---

## 2. Decisão

### 2.1 `VolumeReference`

O motor de volume deixa de depender só de `WeeklyPlan` e passa a receber uma
projeção de execução por semana:

```kotlin
data class VolumeReference(
    val weekStart: LocalDate,
    val plannedMinutes: Int?,
    val executedMinutes: Int,
    val strongMinutes: Int,
    val strongSessions: Int,
    val plannedSessions: Int,
    val createdOn: LocalDate?,
    val specialWeek: Boolean,
)

data class StrongRun(
    val date: LocalDate,
    val durationMin: Int,
    val source: ActivitySource,
    val plannedSessionId: String?,
)
```

- `executedMinutes`/`strongMinutes`: soma das atividades RUNNING/TREADMILL não
  deletadas com evidência forte, **planejadas ou não**.
- `strongSessions`: contagem de atividades fortes.
- `plannedSessions`: sessões treináveis do plano.
- `createdOn`: `WeeklyPlan.createdOn`; só semanas criadas na segunda são
  referências comparáveis (ADR 0038).
- `specialWeek`: deload/taper/recovery/race.
- A projeção é construída na borda (`:app`/`core:data`) a partir de
  `WeeklyPlan` + atividades; o domínio continua puro.

### 2.2 Base de volume

- A base é `max(strongMinutes)` entre as semanas usáveis, não
  `plannedMinutes`.
- A progressão continua +6% e continua exigindo semana anterior ≥80% de adesão
  **planejada**, prontidão, ACWR confiável 0,8–1,3, monotonia e strain.
- A adesão continua lida do `WeeklyPlan` da semana anterior
  (`strongCompletionRatio`): `VolumeReference.strongSessions` conta também
  treino extra e não é o numerador de adesão. Treino extra **não** infla adesão.
- Uma semana sem plano não é referência de volume (conservador).
- Uma semana de deload/taper/recovery não é referência.
- Atividade deletada não conta; atividade sem métrica conta para volume, mas não
  para carga objetiva.

### 2.3 Mesociclo e graduação

- `MesoCycle.isLoadWeek` usa `strongSessions >= 1` + `createdOn == weekStart` +
  não especial, em vez de `completedVolumeMinutes() > 0`.
- `LevelGraduation.hasLongRun` usa `StrongRun.durationMin` (real), não a duração
  planejada; 60 min na base, 90 no avançado.
- As semanas fortes da graduação continuam baseadas na adesão planejada
  (`strongCompletionRatio`); treino extra não conta como adesão. Isso preserva a
  distinção “treinou por fora” vs. “seguiu o plano”.
- O card de graduação pode citar o longão real e a origem (“não planejado”),
  quando aplicável.

### 2.4 Reparo de `LEGACY_UNVERIFIED`

- O reparo é permitido **apenas** para uma atividade importada no mesmo dia de
  uma sessão `PLANNED`/`DONE`/`MANUAL`/`LEGACY` que ficou sem vínculo por uma
  falha de import/link.
- Requer confirmação explícita do usuário (“Essa corrida é o treino de X?”).
- O reparo atualiza `activityId`, `completionEvidence=ACTIVITY`, `status=DONE`,
  e a execução correspondente; preserva data, duração e vínculo 1:1.
- Histórico migrado de versões antigas **não** é promovido em massa; continua
  `LEGACY_UNVERIFIED` e somente leitura.
- O reparo é auditável (quem/quando/origem) e reversível? A reversão segue a
  lixeira/undo do vínculo; o histórico migrado não é tocado.
- Se a sessão já está `DONE` com `ACTIVITY`, não há reparo.

> **Implementação (C4-40):** `SessionCompletionRules.checkLegacyRepair`,
> `SessionExecutionRepository.repairLegacyWithActivity` e
> `RepairLegacyCompletion`; o prompt de import oferece a correção para
> `MANUAL`/`LEGACY` e envia `confirmed=true` só após o toque. O reparo só
> aceita o dia atual e uma sessão sem `activityId`/`executionId`; o histórico
> antigo não é tocado. `PLANNED` continua no fluxo normal de vínculo; `MANUAL`
> continua no `ReplaceManualCompletion`.

### 2.5 Sem dupla contagem

- Uma atividade vinculada a uma sessão planejada conta uma vez, pelo tempo real.
- Uma atividade não planejada conta uma vez, como volume.
- Uma execução `MANUAL` não conta como volume forte; conta adesão com confiança
  baixa (ADR 0024).
- Uma atividade deletada não conta.
- O `VolumeReference` é determinístico e não depende da ordem das atividades.

### 2.6 Invariantes

- Treino extra conta para volume/longão, não para adesão.
- A base é o volume real forte, não o planejado.
- Semanas sem plano/especiais não são referências.
- `LEGACY` migrado não é promovido.
- Reparo só no mesmo dia, com confirmação.
- A carga continua incluindo todas as atividades; a assimetria “extra só
  aumenta a carga” deixa de existir para volume.

---

## 3. Dados e APIs

- `VolumeReference`/`StrongRun` no domínio.
- `VolumeProgression.decide(references: List<VolumeReference>, previousWeek: WeeklyPlan?, ...)`;
  a base vem das referências e a adesão do plano anterior; o
  `WeeklyPlanGenerator` recebe `volumeReferences`.
- `MesoCycle`/`LevelGraduation` usam as referências/strong runs.
- `LevelGraduationInput.strongRuns` montado a partir das atividades.
- `TrainingPlanRepository`/`ActivityRepository` expõem a projeção semanal.
- `SessionCompletionRules`/`ImportService` ganham o reparo com confirmação.
- Sem migração de schema; a projeção é derivada.

---

## 4. Alternativas consideradas

1. **Manter só o planejado.**
   Rejeitada: o treino extra só aumenta a carga; o usuário é segurado por treinar.
2. **Contar tudo, inclusive `MANUAL`/`LEGACY`.**
   Rejeitada: clique não é evidência; legado migrado não é verdade.
3. **Contar treino extra também na adesão.**
   Rejeitada: inflaria a adesão e a progressão sem seguir o plano.
4. **Promover todo `LEGACY`.**
   Rejeitada: reescreveria o passado; o reparo é excepcional e auditável.
5. **Ignorar semanas sem plano.**
   **Aceito**: sem plano, não há intenção comparável; conservador.

---

## 5. Consequências

### 5.1 Positivas

- O treino real conta; o loop “treina por fora, é segurado” diminui.
- O longão real pode contar para a graduação.
- O primeiro longão importado no mesmo dia pode ser recuperado com confirmação.
- A adesão continua honesta: só o plano conta.
- A base de volume reflete o que o atleta fez, não só o que o app mandou.

### 5.2 Riscos

- Junk volume pode inflar a base; mitigação: só atividades fortes, 6%,
  disponibilidade, semana com plano na segunda, e a adesão separada.
- O reparo pode ser usado para “consertar” um treino que não era a sessão;
  mitigação: confirmação explícita, mesma data, esporte compatível, vínculo 1:1.
- A projeção adiciona queries; indexar por data e limitar a janela de 4/12
  semanas.
- A mudança de `MesoCycle` pode alterar deloads; testes e goldens.

### 5.3 Segurança

- O volume não cresce sem os gates (prontidão, ACWR confiável, monotonia,
  strain).
- A carga continua incluindo todas as atividades; a segurança não muda.
- `MANUAL`/`LEGACY` não liberam progressão.
- O reparo exige confirmação e não reescreve o migrado.

---

## 6. Matriz de testes

| Caso | Entrada | Saída |
|---|---|---|
| `G-VOL-02` | 114 planejado + extra 28 + LEGACY 33 | base 127; adesão 3/3; long_run 60 continua não |
| Extra | 4 atividades, 3 planejadas | volume 4; adesão 3/3 |
| Longão real | `StrongRun` 60/90 | conta para graduação |
| Sem plano | semana sem `WeeklyPlan` | não é referência |
| Deletada | atividade com `deletedAt` | não conta |
| `MANUAL` | execução manual | adesão baixa; não volume forte |
| Reparo | atividade do mesmo dia, confirmação | `ACTIVITY`; sem duplicar |
| Migrado | `LEGACY` antigo | intocado |
| Dupla contagem | atividade vinculada | conta uma vez |
| Determinismo | ordem das atividades | mesmo resultado |

---

## 7. Rollout

- `weekly-plan-v7`/`daily-prescription-v6`; a projeção entra no `TodayViewModel`
  e no `PlanReplanner`.
- O `LevelGraduationContextBuilder` passa a montar `StrongRun`.
- O reparo entra no fluxo de import/oferta do mesmo dia.
- A UI do review mostra “treino extra contou para volume”.
- `PROGRESS`/`ROADMAP`/`DECISIONS`/`DATA_MODEL` atualizados.

---

## 8. Fora de escopo

- Alterar o teto de 6% (ADR 0038).
- Contar musculação/cross-training como volume de corrida.
- Promover histórico migrado em massa.
- Mudar a lixeira/undo.
- Backfill de atividades antigas.

---

## 9. Referências

- `docs/PRD_TREINO_REAL.md` FR-404/FR-408, §11.5 (Q-03, Q-09).
- `docs/SPORTS_SCIENCE.md` §8.3, §8.11.
- ADR 0024, 0038, 0041, 0042, 0044, 0046.
- `EV-SEILER-2010`, `EV-PFITZINGER-2008`, `EV-DANIELS-2014`.
