# Golden scenarios

Cenários de regressão do motor em JSON: `input.json` + `expected.json` + `README.md`.

Estado atual (16/11/2026):

| ID | Motor | Prova |
|---|---|---|
| `G-LOAD-01` | `TrainingLoadCalculator` | TRIMP, rTSS, sRPE, CTL/ATL/TSB, ACWR e monotonia |
| `G-LOAD-02` | `TrainingLoadEngine` | Série diária de 28 dias e PMC |
| `G-LOAD-03` | `TrainingLoadEngine` + gate + volume | Cold start: 8 dias de carga + 13 de pré-história; sem veto, volume mantido, copy "em calibração" |
| `G-PER-03` | `WeeklyPlanGenerator` | Oportunidade de qualidade estrutural, longão honesto de 40 min e fácil de sexta |
| `G-PER-05` | `WeeklyPlanGenerator` | Semana 5K do catálogo: contínuo estruturado em BUILD, strides EASY com estrutura, longão 45; PEAK vira pace de prova; iniciante sem QUALITY |
| `G-UPGRADE-01` | `PlanUpgrade` | v6→v7, terça `DONE` preservada, sexta vira oportunidade, volume intacto |
| `G-VOL-02` | `WeeklyPlanGenerator` | 3×38 concluídas + extra 28 → base real 142, alvo 151 (+6%), adesão 3/3, copy "treino extra contou" |
| `G-READY-01` | `ReadinessCalculator` | Dia verde com dados completos |
| `G-READY-02` | `ReadinessCalculator` | Ausência total de dados |
| `G-READY-03` | `ReadinessCalculator` | Sinal de doença/lesão |
| `G-READY-04` | `ReadinessCalculator` | Sono curto bloqueia qualidade |
| `G-READY-05` | `ReadinessCalculator` + gate | Matriz do check-in (sem check-in vs 3/3/3/3 vs 4/2/2/2 vs 5/1/1/1) + copy |
| `G-DATA-02` | `SleepNightValidator` + agregador + gate | Sono plausível (36 min, 6 h + cochilo, 5h30, fragmentada) |

Os cenários `G-001`…`G-006` do `TESTING.md` cobrem plano, taper e calor; entram
quando `PrescriptionEngine`/`PlanEngine` existirem.

Doubles são comparados com tolerância `1e-6`; inteiros e enums são exatos.
Nenhum cenário contém dados reais de saúde.
