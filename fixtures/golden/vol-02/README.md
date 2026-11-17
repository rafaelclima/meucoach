# G-VOL-02 — treino extra conta para volume, não para adesão

Prova `FR-408` (ADR 0050 §2.1–§2.2): a base de volume usa o executado real
(`strongMinutes`), e a adesão continua só no plano.

- Semana anterior (26/10): 3 planejadas de 38 min concluídas com `ACTIVITY`
  (114 min, adesão 3/3) + 1 corrida extra de 28 min sem vínculo.
- Referência da semana: `strongMinutes = 142` (114 + 28), `strongSessions = 4`,
  `plannedSessions = 3`. Uma sessão `LEGACY_UNVERIFIED` de 33 min, quando existe,
  não gera `StrongRun` e não entra na base (só `ACTIVITY`/`GUIDED` contam).
- Semana alvo (02/11, intermediário, seg 60/qua 60/dom 90, verde com check-in,
  ACWR 1,0 confiável): base 142 → alvo 151 (+6%).
- O rationale declara a base, o +6% e o crédito:
  "Seu treino extra contou para o volume (+28 min além do plano),
  mas a adesão considera só o plano (3/3)."
- Nenhuma corrida de 60 min na janela: o longão real de 60/90 para graduação
  continua pendente (`long_run` não atendido).

Nenhum dado real de saúde.
