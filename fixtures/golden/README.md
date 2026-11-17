# Golden scenarios

Cenários de regressão do motor em JSON: `input.json` + `expected.json` + `README.md`.

Estado atual (16/11/2026):

| ID | Motor | Prova |
|---|---|---|
| `G-LOAD-01` | `TrainingLoadCalculator` | TRIMP, rTSS, sRPE, CTL/ATL/TSB, ACWR e monotonia |
| `G-LOAD-02` | `TrainingLoadEngine` | Série diária de 28 dias e PMC |
| `G-PER-03` | `WeeklyPlanGenerator` | Oportunidade de qualidade estrutural, longão honesto de 40 min e fácil de sexta |
| `G-PER-05` | `WeeklyPlanGenerator` | Semana 5K do catálogo: contínuo estruturado em BUILD, strides EASY com estrutura, longão 45; PEAK vira pace de prova; iniciante sem QUALITY |
| `G-UPGRADE-01` | `PlanUpgrade` | v6→v7, terça `DONE` preservada, sexta vira oportunidade, volume intacto |
| `G-VOL-02` | `WeeklyPlanGenerator` | 3×38 concluídas + extra 28 → base real 142, alvo 151 (+6%), adesão 3/3, copy "treino extra contou" |
| `G-READY-01` | `ReadinessCalculator` | Dia verde com dados completos |
| `G-READY-02` | `ReadinessCalculator` | Ausência total de dados |
| `G-READY-03` | `ReadinessCalculator` | Sinal de doença/lesão |
| `G-READY-04` | `ReadinessCalculator` | Sono curto bloqueia qualidade |

Os cenários `G-001`…`G-006` do `TESTING.md` cobrem plano, taper e calor; entram
quando `PrescriptionEngine`/`PlanEngine` existirem.

Doubles são comparados com tolerância `1e-6`; inteiros e enums são exatos.
Nenhum cenário contém dados reais de saúde.
