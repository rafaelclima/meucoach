# G-LOAD-02 — motor de carga em 28 dias

Prova a série diária do `TrainingLoadEngine`:

- rTSS quando não há FC (corrida de 60 min no limiar = 100).
- TRIMP quando há FC confiável (30 min a 150 bpm).
- rTSS em pace mais lento (40 min a 6:40/km = 54).
- CTL/ATL/TSB, ACWR, monotonia e dias sem carga.

O dia 27 usa TRIMP e os dias 20/29 usam rTSS; o motor nunca soma os dois no
mesmo dia. Doubles comparados com tolerância `1e-6`.
