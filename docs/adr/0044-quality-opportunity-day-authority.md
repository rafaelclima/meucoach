# ADR 0044 — Oportunidade de qualidade e autoridade do dia

**Status:** Aprovado em 2026-11-16 — implementado em 17/11/2026 (C4-01..C4-08, C4-29, C4-50..C4-52; golden `G-PER-03`); copy do rationale refinada em C4-59; `R-PER-03` documentado no `SPORTS_SCIENCE` §8.8/§14.
**Data:** 2026-11-16
**Escopo:** `WeeklyPlanGenerator`, `DailyPrescriptionEngine`, `GetTodayPrescription`, `PlanReplanner`, `PlanUpgrade`, Hoje/Plano/Por quê.
**Relacionados:** ADR 0010 (teto de disponibilidade), ADR 0023 (plano × prescrição),
ADR 0024 (evidência de conclusão), ADR 0026 (cobertura de carga), ADR 0028 (replanejamento),
ADR 0038 (progressão de volume), ADR 0041 (mesociclo), ADR 0042 (graduação),
ADR 0043 (periodização), ADR 0046 (carga v3), ADR 0049 (prontidão/check-in).
**PRD:** `docs/PRD_TREINO_REAL.md` FR-401, FR-406, FR-409, §11.5 (Q-01, Q-02, Q-12).
**Decisão do usuário 0:** 16/11/2026.

---

## 1. Contexto

### 1.1 O que existe hoje

- `WeeklyPlanGenerator` decide `qualityAllowed` com uma única `ReadinessResult` e um
  único `LoadSnapshot` (`WeeklyPlanGenerator.kt:240-245`). A decisão vale para a
  semana inteira.
- `TodayViewModel` gera o plano na primeira composição e persiste
  (`TodayViewModel.kt:197-225`). Depois disso, o plano salvo é usado; não há
  regeneração quando a prontidão ou a carga mudam.
- `PlanReplanner` tem gatilhos de perfil, fuso, disponibilidade, sessões perdidas
  e recuperação (`PlanReplanner.kt:36-77`), mas **não** de prontidão/carga. Além
  disso, o futuro não pode ganhar qualidade além da intenção vigente
  (`PlanReplanner.kt:120-135`).
- `DailyPrescriptionEngine` só mantém ou reduz. Se a qualidade foi bloqueada na
  segunda, o dia não pode restaurá-la (`DailyPrescriptionEngine.kt:98-176`).
- O plano é gerado antes do sync do Health Connect; um atraso de minutos pode
  decidir a semana inteira (PRD P-02).

### 1.2 O problema

O plano tenta adivinhar na segunda se a sexta será um bom dia. Se a segunda está
âmbar, sem check-in ou sem carga suficiente, a semana nasce sem qualidade. A
sexta pode estar verde e nada muda. O usuário sente que o app não o treina,
porque ele **nunca vê** uma qualidade para confirmar.

### 1.3 Por que agora

A auditoria de 16/11/2026 (PRD P-01/P-02) mostrou o caso real: duas semanas
fechadas com 114 min, 3 dias e **0 qualidade**; a terceira foi a primeira com
1 qualidade. O usuário aprovou a decisão Q-01: a primeira qualidade é
estrutural; o dia confirma.

---

## 2. Decisão

### 2.1 Duas camadas, não uma

Separar a elegibilidade em duas camadas determinísticas:

| Camada | Pergunta | Quando | Quem |
|---|---|---|---|
| **Estrutural** | A semana **pode** ter uma qualidade? | Geração/revisão do plano | `WeeklyPlanGenerator` |
| **Do dia** | A qualidade **deve** ser mantida hoje? | Prescrição do dia | `QualityGate` / `DailyPrescriptionEngine` |

A camada estrutural não olha a prontidão/carga da segunda. Ela olha nível, fase,
dias, espaçamento, taper, recovery, race week e disponibilidade. A camada do dia
olha prontidão, sono, check-in, carga, monotonia, strain e recuperação.

### 2.2 `QualityOpportunity`

`QualityOpportunity` é um **contrato de leitura** sobre uma `PlannedSession` do
tipo `QUALITY`, não uma nova entidade persistida. A sessão carrega:

- `qualityKind: QualityKind?` (CONTINUOUS, INTERVALS, FARTLEK, HILLS, RACE_PACE; `STRIDES_ONLY` apenas na micro-dose);
- `structure: WorkoutStructure?` (aquecimento, blocos, recuperações, volta à calma);
- `planDecision: PlanDecision?` (por que a oportunidade existe ou não);
- `status`: `PLANNED` (o dia decide), `DONE`/`CLOSED` (histórico).

`WeeklyPlan.qualityCount` passa a significar **oportunidades planejadas**, não
sessões confirmadas. A UI distingue:

- **planejada** — existe no plano;
- **mantida** — a prescrição do dia é `QUALITY`/`KEEP`;
- **rebaixada** — a prescrição do dia é `EASY`/`DOWNGRADE`, com motivo;
- **não feita** — o dia passou sem execução.

### 2.3 Elegibilidade estrutural

```
structuralEligible =
  profile.experience != BEGINNER &&
  days.size >= 3 &&
  phase !in { TAPER, RECOVERY } &&
  !raceWeek &&
  qualityCandidateDays.isNotEmpty()
```

A primeira oportunidade é agendada quando `structuralEligible`. A segunda só
quando:

```
secondQualityAllowed =
  structuralEligible &&
  days.size >= 4 &&
  previousWeekStrongRatio >= 0.80 &&
  acwrReliable && acwr in 0.8..1.3 &&
  !taperWindow
```

`selectQualityDays` mantém as regras atuais: no máximo 2, nunca no mesmo dia ou
adjacente ao longão, nunca duas consecutivas, respeitando taper/recovery/race.

A micro-dose de taper (`R-PLAN-05/10`) é **estrutural** e não depende da
prontidão da segunda: a semana de taper/race week sempre tem 1 micro-dose ≤25 min
quando há um dia elegível. O dia ainda pode rebaixá-la por segurança, mas o plano
não nasce com zero estímulo.

### 2.4 Elegibilidade do dia (`QualityGate`)

O `QualityGate` é uma função pura:

```kotlin
data class QualityEligibility(
    val eligible: Boolean,
    val reason: QualityBlockReason?,
    val detail: String?,
    val ruleId: String,
    val evidenceIds: List<String>,
    val unlockHint: String?,
)
```

Precedência determinística (primeiro que falha vence):

1. `ILLNESS_OR_RED_READINESS` — doença/lesão ou prontidão vermelha.
2. `BEGINNER_GUARD` — iniciante com `QUALITY` planejada (`R-PRESC-04`).
3. `SUBJECTIVE_MISSING` — sem check-in (`R-READY-06`).
4. `SHORT_SLEEP` — sono válido < 6 h (`R-READY-04`).
5. `LOW_READINESS` — prontidão âmbar: recuperação parcial; o dia não confirma
   a qualidade (`R-READY-03`, ADR 0049 §2.1/FR-406).
6. `RECOVERY_WINDOW` / `TAPER_WINDOW` / `RACE_WEEK`.
7. `LOAD_STALE` / `LOAD_INSUFFICIENT`.
8. `MONOTONY_HIGH`.
9. `STRAIN_SPIKE` (só se `strainReliable`).
10. `ACWR_HIGH` (só se `acwrReliable && acwr > 1.5`).
11. Caso contrário, elegível.

O gate não olha a prontidão da segunda. O detalhe deve trazer o valor que falhou
(ex.: “ACWR 1,62 > 1,50”; “sono 5h20 < 6h”; “7 de 28 dias de carga”) e o
`unlockHint` (ex.: “faça o check-in”, “aumente a disponibilidade do domingo”).

### 2.5 `R-PRESC-02` emendada

`R-PRESC-02` continua: **com os mesmos inputs, o dia só reduz**. Mas uma
reavaliação com **inputs novos** (check-in, sono, carga) pode restaurar a
qualidade planejada antes de a sessão ser concluída. A nova decisão é uma nova
versão persistida; a anterior fica no histórico.

Condições:

- a sessão não está `DONE`/`CLOSED`;
- não há execução guiada ativa (quando o ciclo 3 existir);
- o `inputsHash` mudou;
- o `QualityGate` agora retorna elegível;
- a UI mostra “Atualizado: com o check-in, a qualidade foi liberada”.

### 2.6 Plano antigo (upgrade)

`PlanUpgrade` pode adicionar **1 oportunidade estrutural** a um plano v6 que não
tinha nenhuma, sem adicionar volume, longão extra ou segunda qualidade. A regra
de “não ganhar qualidade além da intenção futura” do `PlanReplanner` ganha uma
exceção explícita `allowStructuralQualitySlot = true`, usada apenas no
`ENGINE_UPGRADE` e no `DATA_READY` quando o plano nasceu incompleto.

### 2.7 Invariantes

- O plano nunca contém mais de 2 qualidades.
- Qualidade nunca é adjacente ao longão.
- Qualidade nunca em taper/recovery/race week.
- A primeira oportunidade não depende da prontidão/carga da segunda.
- O dia nunca aumenta duração/tipo/intensidade além do plano (exceto a restauração
  de uma qualidade já planejada, com inputs novos).
- O histórico `DONE`/`CLOSED` nunca é reescrito.
- Mesmos inputs + mesma versão ⇒ mesma saída.

---

## 3. Dados e APIs

### 3.1 Domínio

- `PlannedSession` ganha `qualityKind`, `structure`, `planDecision`.
- `WeeklyPlan` ganha `engineVersion`; `qualityCount` documentado como oportunidades.
- `QualityGate`, `QualityEligibility`, `QualityBlockReason` (novos).
- `PlanDecision`/`PlanDecisionReason` (novos).
- `DailyPrescription` ganha `structure`, `qualityBlockReason`, `decisionDetail`,
  `unlockHint`.
- `PrescriptionTargets` ganha `primary: TargetKind` (FR-410/ADR 0047).

### 3.2 Persistência

As colunas estão no ADR 0048 (schema v18): `planned_sessions.structureJson`,
`planDecisionCode`, `planDecisionDetail`; `daily_prescriptions.structureJson`,
`qualityBlockReason`, `decisionDetail`, `unlockHint`.

### 3.3 Fluxo

```
App abre
  → gate de dados (ADR 0048)
  → plano v7: 1 oportunidade estrutural (se aplicável)
  → dia: QualityGate
      → mantém QUALITY (KEEP)
      → rebaixa EASY (DOWNGRADE) com reason/detail/unlockHint
  → novo check-in antes do DONE
      → nova versão: pode restaurar QUALITY
```

---

## 4. Alternativas consideradas

1. **Manter o gate na segunda e só replanejar quando a semana muda.**
   Rejeitada: o replanner não tem gatilho de prontidão/carga e não pode adicionar
   qualidade; o caso “segunda âmbar, sexta verde” continua perdido.
2. **Permitir que o motor diário promova EASY → QUALITY.**
   Rejeitada: cria um caminho de aumento sem intenção semanal; a oportunidade
   estrutural é mais simples e auditável.
3. **Aceitar a qualidade como garantia e não rebaixar.**
   Rejeitada: viola segurança, prontidão e carga.
4. **Override manual “quero forte hoje”.**
   Fora do ciclo (decisão Q-11); a oportunidade + check-in é o caminho.
5. **Micro-dose de taper sujeita ao gate da segunda.**
   Rejeitada: viola “nunca zero estímulo” no taper.

---

## 5. Consequências

### 5.1 Positivas

- O usuário vê o app **tentando** toda semana; a qualidade deixa de desaparecer
  por um dado de segunda.
- A decisão do dia fica mais explicável: gate, valor, motivo, dica.
- O plano e a prescrição voltam a ter papéis claros (ADR 0023).
- A reavaliação com check-in tardio remove a penalidade de responder tarde.
- O upgrade do plano atual não espera a próxima segunda.

### 5.2 Negativas/riscos

- Mais oportunidades podem ser rebaixadas; a UI precisa deixar claro que é uma
  **oportunidade**, não uma promessa.
- O usuário pode se frustrar com “qualidade rebaixada” toda semana; o review
  semanal deve mostrar o padrão e sugerir recuperação/volume quando aplicável.
- A restauração de qualidade com check-in tardio exige versionamento correto e
  não pode reescrever um dia `DONE`.

### 5.3 Segurança

- Doença, dor, prontidão vermelha, sono curto, recovery, taper e race week
  continuam bloqueando.
- A segunda qualidade continua condicionada à progressão e à carga confiável.
- A primeira oportunidade não é um aumento de volume; é uma intenção que o dia
  pode rebaixar.

---

## 6. Matriz de testes

| Caso | Entrada | Saída |
|---|---|---|
| `G-PER-03` | 3×40, intermediário, 2 semanas | 1 oportunidade; longão domingo 40; fácil sexta; sem “Longão 34” |
| Segunda âmbar, sexta verde | Plano gerado com âmbar; check-in verde na sexta | Oportunidade existe; sexta mantém `QUALITY` |
| Segunda verde, sexta âmbar | Plano com verde; check-in âmbar na sexta | Oportunidade existe; sexta rebaixa com motivo |
| Check-in tardio | Prescrição EASY sem check-in; check-in 4/2/2/2 antes do DONE | Nova versão `QUALITY`; versão anterior no histórico |
| DONE | Sessão concluída | Nenhuma reavaliação; histórico intacto |
| 5 dias + progressão | Semana anterior ≥80%, ACWR 1,0 | 2 oportunidades |
| 3 dias | Disponível | 1 oportunidade |
| Iniciante | `BEGINNER` | 0 qualidade; strides/intro fartlek `EASY` |
| Taper/recovery/race | Fase | 0 qualidade principal; micro-dose estrutural no taper |
| Upgrade | Plano v6 sem qualidade, DONE na terça | v7 com 1 oportunidade futura; terça preservada |
| Determinismo | Mesmos inputs | Mesmas datas/kinds |

Testes de propriedade:

- `qualityCount <= 2`.
- Nenhuma qualidade adjacente ao longão.
- Nenhuma qualidade em taper/recovery/race.
- `fitToVolume` nunca dropa qualidade.
- O dia nunca aumenta duração/tipo além do plano.

---

## 7. Rollout

- `weekly-plan-v7` e `daily-prescription-v6` (ADR 0048).
- `PlanUpgrade` na primeira abertura; histórico intocado.
- A oportunidade aparece no plano da semana vigente; a prescrição do dia é
  recalculada com a nova versão.
- Se o upgrade falhar, o plano v6 continua válido; tentar na próxima abertura.

---

## 8. Fora de escopo

- Override manual de treino forte (Q-11).
- Sessão guiada persistente (ciclo 3).
- Novas telas além de Hoje/Plano/Por quê.
- Mudança na disponibilidade do usuário (o app só declara e sugere).
- Auto-aplicar a segunda qualidade sem os gates.

---

## 9. Referências

- `docs/PRD_TREINO_REAL.md` FR-401/FR-406/FR-409, §11.5.
- `docs/SPORTS_SCIENCE.md` §7.3, §8.4, §8.7, §8.8.
- ADR 0023, 0024, 0026, 0028, 0038, 0041, 0042, 0043.
- `EV-SEILER-2010`, `EV-DANIELS-2014`, `EV-FOSTER-2001`.
