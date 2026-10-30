# Golden scenarios

Cenários de regressão do motor em JSON: `input.json` + `expected.json` + `README.md`.

Estado atual (30/10/2026):

| ID | Motor | Prova |
|---|---|---|
| `G-LOAD-01` | `TrainingLoadCalculator` | TRIMP, rTSS, sRPE, CTL/ATL/TSB, ACWR e monotonia |
| `G-READY-01` | `ReadinessCalculator` | Dia verde com dados completos |
| `G-READY-02` | `ReadinessCalculator` | Ausência total de dados |
| `G-READY-03` | `ReadinessCalculator` | Sinal de doença/lesão |
| `G-READY-04` | `ReadinessCalculator` | Sono curto bloqueia qualidade |

Os cenários `G-001`…`G-006` do `TESTING.md` cobrem plano, taper e calor; entram
quando `PrescriptionEngine`/`PlanEngine` existirem.

Doubles são comparados com tolerância `1e-6`; inteiros e enums são exatos.
Nenhum cenário contém dados reais de saúde.
