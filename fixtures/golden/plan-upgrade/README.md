# G-UPGRADE-01 — upgrade v6 → v7 com história preservada

Prova `FR-409`/ADR 0048 §2.2 e §6: o `PlanUpgrade` regenera só os dias
estritamente futuros, preserva hoje/passado/`DONE` e adiciona no máximo
1 oportunidade estrutural de qualidade, sem crescer volume, longão ou
segunda qualidade.

- Semana vigente `2026-11-02`, hoje `2026-11-04` (quarta).
- Plano v6 armazenado: segunda fácil, **terça `DONE` manual**, quarta (hoje)
  fácil, sexta fácil 40, domingo “Corrida mais longa” 40. Semana: 200 min.
- Regenerado v7: o mesmo, com a sexta virando `QUALITY` 40 (“Fartlek leve”,
  `FARTLEK`, `QUALITY_OPPORTUNITY`); mesma duração, mesmo volume.
- Esperado: terça `DONE` intacta, sexta `QUALITY` 40, volume 200, 1 qualidade,
  `engineVersion = weekly-plan-v7`, nenhum “Longão”.

Nenhum dado real de saúde.
