# ADR 0046 — Load gate v3: ACWR confiável e limite único

**Status:** Implementado em 2026-11-17 (C4-22..C4-27, sessões S3/S14); bump de versões em C4-53.
**Data:** 2026-11-16
**Escopo:** `TrainingLoadEngine`, `LoadSnapshot`, `LoadCoverage`, `ActivitySourceStatus`,
`DailyPrescriptionEngine`, `WeeklyPlanGenerator`, `WorkoutRecommender`, `VolumeProgression`.
**Relacionados:** ADR 0026 (cobertura de carga), ADR 0038 (progressão), ADR 0039
(sRPE), ADR 0044 (qualidade), ADR 0048 (dados).
**PRD:** `docs/PRD_TREINO_REAL.md` FR-405, §11.5 (Q-06).
**Decisão do usuário 0:** 16/11/2026.

---

## 1. Contexto

### 1.1 O que existe hoje

- `LoadSnapshot.dataState` fica `KNOWN` com fonte fresca + ≥8 dias com carga + ACWR
  não nulo (`TrainingLoadEngine.kt:292-300`).
- `objectiveDataState` exige ≥8 dias objetivos (`:172-178`).
- `permitsQualityAndProgression` exige carga objetiva + monotonia <2,0 + sem
  strain spike (`:211-212`).
- O ACWR é calculado sobre a janela de 28 dias, mas os dias antes do primeiro
  import contam como “sem atividade” com fonte fresca; o motor os trata como
  descanso, não como ausência de histórico.
- `DailyPrescriptionEngine` bloqueia qualidade com `acwr > 1.3`
  (`DailyPrescriptionEngine.kt:74,601`).
- `WeeklyPlanGenerator` usa 1,3 só para a segunda qualidade/progressão
  (`:239,994-995`); `WorkoutRecommender` usa 1,5 (`:269`); o
  `SPORTS_SCIENCE.md` fala 1,5 (`:174,336`).

### 1.2 O problema

Há três limites diferentes no mesmo produto. O mais rígido (1,3) está no motor
diário e não está documentado. Além disso, o ACWR é instável com janela
incompleta: no cenário real, ele vai de **1,75** para **0,41** em quatro dias
sem treinar, porque a média crônica de 28 dias inclui dias antes do primeiro
import. O app diz “sua carga subiu rápido” quando o número é um artefato.

O resultado é um veto de qualidade que parece fisiológico, mas é matemático. O
usuário perde a qualidade e não entende.

### 1.3 Decisão do usuário 0

Q-06: ACWR **1,5** em todos os caminhos; só veta qualidade quando **confiável**
(28 dias de histórico); não confiável não veta; progressão exige confiável em
0,8–1,3.

---

## 2. Decisão

### 2.1 Confiabilidade (`acwrReliable`)

```
historyStart = firstActivityAt?.atZone(zone).toLocalDate()
daysBeforeHistory = max(0, daysBetween(windowStart, historyStart))
daysWithoutActivity = if (sourceFresh) {
    window.count { date >= historyStart && metric == null && date !in activityDates }
} else 0
daysWithoutData = window.size - daysWithLoad - daysWithoutActivity

acwrReliable =
    sourceFresh &&
    daysBeforeHistory == 0 &&
    daysWithoutData == 0 &&
    daysWithLoad >= MIN_DAYS_WITH_LOAD &&
    subjectiveDaysInWindow == 0
```

Interpretação:

- **28 dias completos** de histórico; nenhum dia antes do primeiro import.
- Todo dia é classificado como “com carga” ou “descanso observado”; nenhum dia
  “sem dado”.
- Carga objetiva (TRIMP/rTSS), nunca só sRPE.
- Fonte fresca (≤14 dias).

`acwrReliable=false` não é “carga ruim”; é “ainda não sei”. O ACWR pode ser
exibido como “em calibração”, mas não decide.

### 2.2 Limites

```
acwrHigh = acwrReliable && acwr > 1.5
strainReliable = acwrReliable
permitsQuality =
    objectiveDataState == KNOWN &&
    monotonyOk &&
    !(strainSpike && strainReliable) &&
    !acwrHigh
permitsProgression =
    permitsQuality &&
    acwrReliable &&
    acwr in 0.8..1.3
```

- **1,3 deixa de ser veto de qualidade.** Ele continua sendo o limite superior
  da faixa de progressão (0,8–1,3).
- **1,5 é o único limite de redução do dia** (>1,5 → fácil/reduzido).
- ACWR 1,3–1,5: mantém a qualidade, não progride volume.
- ACWR <0,8: mantém, nunca acelera (documento).
- ACWR não confiável: não veta qualidade, não libera progressão; a decisão usa
  prontidão, monotonia e strain (se confiável).

### 2.3 Pré-história não é descanso

`ActivitySourceStatus` ganha `firstActivityAt: Instant?` (ou `firstImportAt`).
O `LoadCoverage` calcula `daysBeforeHistory` e os classifica como
`daysWithoutData`, nunca `daysWithoutActivity`. A UI mostra “8 de 28 dias de
histórico; ACWR em calibração”.

O `sourceFresh` continua baseado na última importação; a diferença é que a
ausência de histórico não vira zero fisiológico.

### 2.4 Strain e monotonia

- `monotony` é uma janela de 7 dias; com dados reais, continua válida. Se null,
  `monotonyOk=false` (conservador).
- `strainSpike` só bloqueia se `strainReliable`; sem baseline completo, ele é
  informativo (“carga concentrada, em calibração”), não veto.
- A UI não deve dizer “carga subiu rápido” quando `acwrReliable=false`; deve
  dizer “carga em calibração”.

### 2.5 Onde cada gate é usado

| Decisão | Gate |
|---|---|
| Qualidade do dia | `permitsQuality`; 1,5; sem ACWR não confiável |
| Segunda qualidade / progressão de volume | `permitsProgression`; ACWR confiável 0,8–1,3 |
| Longão do dia | `acwrHigh` (1,5) + prontidão; sem ACWR não confiável |
| Recomendador sem plano | 1,5; sem ACWR não confiável |
| Review/UI | exibe `acwrReliable`, `historyDays`, “em calibração” |

### 2.6 Invariantes

- Nenhum caminho usa 1,3 como veto de qualidade.
- ACWR não confiável nunca produz “carga subiu rápido”.
- Progressão exige ACWR confiável; qualidade não.
- A carga subjetiva (`sRPE`) conserva, nunca libera progressão (ADR 0039).
- Doença, dor, prontidão, sono e recuperação continuam prevalecendo.

---

## 3. Dados e APIs

- `ActivitySourceStatus.firstActivityAt: Instant?`.
- `LoadCoverage.daysBeforeHistory: Int`.
- `LoadSnapshot.acwrReliable: Boolean`, `strainReliable: Boolean`,
  `historyDays: Int`, `baselineDays: Int`.
- `LoadSnapshot.permitsQuality` e `permitsProgression` separados; o antigo
  `permitsQualityAndProgression` pode virar alias depreciado por um release.
- `WeeklyPlanGenerator.VolumeGates` ganha `acwrReliable`; `acwrInRange` passa a
  `acwrReliable && acwr in 0.8..1.3`.
- `DailyPrescriptionEngine.ACWR_MAX = 1.5`.
- `WorkoutRecommender` usa 1,5 + confiabilidade.
- `TodayViewModel`/UI expõem `acwrReliable` e o progresso de calibração.

Sem migração de schema para este ADR (as decisões usam os campos do ADR 0048).

---

## 4. Alternativas consideradas

1. **Manter 1,3 no motor diário.**
   Rejeitada: divergente do documento, do gerador e do recomendador; bloqueia
   qualidade em 1,3–1,5 sem base documentada.
2. **Remover o ACWR do produto.**
   Rejeitada: perde um flag soft útil; a crítica de Impellizzeri é sobre o uso
   como lei, não sobre exibi-lo.
3. **Usar 8 dias como confiável.**
   Rejeitada: é a causa da instabilidade; 8 dias em 28 não é uma janela crônica.
4. **Preencher os dias pré-história com zero.**
   Rejeitada: zero é um dado fisiológico falso; ausência é ausência.
5. **Bloquear progressão quando o ACWR não é confiável.**
   **Aceito** para volume (conservador), mas **não** para qualidade.

---

## 5. Consequências

### 5.1 Positivas

- Qualidade deixa de ser vetada por um número instável.
- O usuário vê “em calibração” em vez de “carga subiu rápido”.
- O volume continua conservador: sem ACWR confiável, não cresce.
- O produto fala a mesma língua em todos os caminhos (1,5).

### 5.2 Riscos

- Liberar qualidade com ACWR não confiável pode parecer inseguro. Mitigação:
  prontidão, sono, monotonia, strain e recuperação continuam; o volume não cresce;
  a UI declara a calibração.
- A janela de 28 dias pode demorar a ficar confiável em usuários novos; o app
  precisa explicar o progresso e não parecer “travado”.
- A mudança de 1,3 para 1,5 pode aumentar a qualidade em semanas de carga alta;
  o limite 1,5 e os demais gates são a proteção.

### 5.3 Segurança

- ACWR >1,5 continua reduzindo o dia.
- ACWR não confiável não libera progressão.
- sRPE continua conservando.
- Nenhuma mudança afeta doença, dor, prontidão vermelha, sono curto, taper ou
  recuperação.

---

## 6. Matriz de testes

| Caso | Entrada | Saída |
|---|---|---|
| `G-LOAD-03` | 8 dias de carga, 13 dias pré-história | `acwrReliable=false`; qualidade não vetada por ACWR; volume mantido; copy “em calibração” |

> **Renumeração (C4-26/C4-27):** a matriz original chamava este cenário de
> `G-LOAD-02`, mas o ID já nomeava o fixture `load-engine` de série completa
> (commit anterior ao PRD). O cold-start foi congelado como **`G-LOAD-03`**
> (`fixtures/golden/load-calibration/`); o `G-LOAD-02` histórico fica intocado.
| Janela completa 1,6 | 28 dias cobertos | qualidade rebaixada; detalhe “1,62 > 1,50” |
| Janela completa 1,4 | 28 dias cobertos | qualidade mantida; progressão bloqueada |
| Janela completa 0,7 | 28 dias cobertos | mantém; não acelera |
| Fonte velha | `lastImportedAt` >14d | `STALE`; qualidade bloqueada por dado |
| sRPE | dias subjetivos | `acwrReliable=false`; não libera progressão |
| Strain | janela incompleta, spike | não bloqueia; informativo |
| Consistência | grep/teste | nenhum caminho usa 1,3 como veto |
| Determinismo | mesmos inputs | mesmo snapshot/flags |

Atualizar os testes legados que usam `acwr = 1.0` para verde e `1.4` para não
verde; adicionar o caso verde + 1,4 (mantém) e verde + 1,6 (rebaixa).

---

## 7. Rollout

- `daily-prescription-v6` (novo `ACWR_MAX`) e `weekly-plan-v7` (volume gates).
- O `GetTodayPrescription` reavalia o dia com a nova versão.
- O plano histórico não é reescrito; a semana vigente é atualizada pelo
  `PlanUpgrade` (ADR 0048).
- A UI ganha “ACWR em calibração” e o progresso de dias.

---

## 8. Fora de escopo

- Substituir o ACWR por outro modelo (ex.: EWMA assimétrico) neste ciclo.
- Aumentar a janela de 28 dias.
- Usar o ACWR como meta ou gamificação.
- Alterar a fórmula do TRIMP/rTSS.

---

## 9. Referências

- `docs/PRD_TREINO_REAL.md` FR-405, §11.5 (Q-06).
- `docs/SPORTS_SCIENCE.md` §6.1, §6.3, §7.2, §8.3.
- ADR 0026, 0038, 0039, 0044.
- `EV-GABBETT-2016`, `EV-IMPELLIZZERI-2020`, `EV-FOSTER-2001`.
- Golden `G-LOAD-03` (`fixtures/golden/load-calibration/`).

---

## 10. Registro de implementação

- **C4-22..C4-25 (S3):** `firstActivityAt` + `daysBeforeHistory` (pré-história
  como “sem dado”); `acwrReliable`/`strainReliable`/`historyDays`/`baselineDays`
  com janela completa de 28 dias; `ACWR_MAX = 1,5` como único limite de redução
  em `QualityGate`, `DailyPrescriptionEngine`, `WeeklyPlanGenerator` e
  `WorkoutRecommender`; `permitsQuality`/`permitsProgression` separados.
  `LoadReliabilityTest` (7), `LoadGateV3Test` (10).
- **C4-26 (S14):** golden `G-LOAD-03` (`fixtures/golden/load-calibration`):
  8 dias de carga rTSS + 13 de pré-história → `acwrReliable=false` com
  `acwr=1,6` (acima do veto, e mesmo assim `acwrHigh=false`); qualidade mantida
  via `QualityGate` (`R-PER-03`, verde + check-in); volume mantido com
  `ACWR_UNRELIABLE`; copy "8 de 28 dias de histórico; ACWR em calibração"
  (`LoadSnapshot.calibrationNote()`, com `baselineDays`). UI: pill "Carga em
  calibração" no Hoje, "Aumento" + nota em Tendências, nota no "Por quê?".
  `LoadCalibrationGoldenTest` (1); `TodayPlanTest` (+1), `TodayExplanationLoadTest`
  (+1), `TrendsScreenTest` (+2).
- **C4-27 (S14):** docs `R-LOAD-03 v3` (`SPORTS_SCIENCE` §6.3/§7.2/§8.3/§14)
  e renumeração `G-LOAD-02` → `G-LOAD-03` aqui e no PRD.
