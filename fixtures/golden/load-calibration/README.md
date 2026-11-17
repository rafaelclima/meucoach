# G-LOAD-03 — cold start em calibração

Congela o cenário da FR-405/ADR 0046 §6 (matriz de testes): 8 dias de carga
objetiva (rTSS) com 13 dias de pré-história na janela de 28 dias.

O motor prova:

- `daysBeforeHistory = 13`, `daysWithLoad = 8`, `daysWithoutData = 13`,
  `historyDays = 15`, `baselineDays = 8`;
- `acwrReliable = false` (janela incompleta) mesmo com `acwr = 1,6`,
  acima do limite de 1,5 — `acwrHigh` continua falso;
- `permitsQuality = true` (o artefato não veta a oportunidade) e
  `permitsProgression = false` (sem baseline, o volume não cresce);
- o `QualityGate` mantém a qualidade planejada (`R-PER-03`) com prontidão
  verde e check-in, e o `VolumeProgression` mantém 120 min com
  `ACWR_UNRELIABLE`;
- a copy de calibração: "8 de 28 dias de histórico; ACWR em calibração".

Doubles comparados com tolerância `1e-6`. Nenhum dado real de saúde.

(Numerado `G-LOAD-03` porque `G-LOAD-02` já nomeia o fixture
`load-engine` de série completa, anterior ao PRD; a renumeração vai ao
ADR 0046/PRD na C4-27.)
