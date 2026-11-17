# G-READY-05 — matriz do check-in sobre base objetiva verde

Congela a FR-406/ADR 0049 §2.1 (matriz de testes): mesma base objetiva verde
(sono 7h30, HRV, FC de repouso) com quatro check-ins.

O motor prova:

- sem check-in: score 76 `GREEN` com dados parciais (`SUBJECTIVE_MISSING`),
  `qualityEligibleByReadiness=false`; o `QualityGate` rebaixa com
  `SUBJECTIVE_MISSING`/`R-READY-06` e a dica "Faça o check-in de hoje para
  liberar o treino forte.";
- 3/3/3/3: score 67 `AMBER` (`NO_QUALITY`); o gate rebaixa com
  `LOW_READINESS`/`R-READY-03` ("prontidão moderada");
- 4/2/2/2: score 76 `GREEN` e 5/1/1/1: score 84 `GREEN`; ambos mantêm a
  qualidade planejada (`R-PER-03`, sem motivo de bloqueio).

A carga do gate é sintética e passa em tudo (objetiva, confiável, ACWR 1,0),
para isolar a prontidão: só o check-in decide.

Numerado `G-READY-05` porque `G-READY-03` já nomeia o fixture de doença
(`readiness-illness`), anterior ao PRD; a renumeração vai ao ADR 0049/PRD na
C4-33. Nenhum dado real de saúde.
