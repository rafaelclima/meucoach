# G-DATA-02 — sono plausível, não noite curta

Congela a FR-407/ADR 0049 §2.4 (matriz de testes): quatro noites com o mesmo
check-in 4/2/2/2 e carga sintética que passa em tudo, para isolar o sono.

O motor prova:

- 36 min sozinho: `SUSPECT` (`minutes=null`), `sleepDurationMin=null` persistido,
  `suspectSleepNights=1`; sem `SHORT_SLEEP`; prontidão 75 `GREEN` só com o
  subjetivo e qualidade mantida (`R-PER-03`) — artefato nunca é noite curta;
- noite 6 h + cochilo 30 min: 360 min válidos (o cochilo das 13:00 cai fora da
  janela 18:00–12:00 e não soma), sem fragmentação; prontidão 60 `AMBER`
  (sono 6 h na fronteira vale 40) e gate `LOW_READINESS`/`R-READY-03`;
- 5h30 válida: 330 min, `SHORT_SLEEP`, prontidão 52 `AMBER`, gate
  `SHORT_SLEEP`/`R-READY-04` — noite curta de verdade bloqueia;
- fragmentada (4 h + gap 3 h): só o maior bloco (240 min), `fragmented=true`,
  `fragmentedSleepNights=1`; 240 < 360 → `SHORT_SLEEP` e gate `SHORT_SLEEP` —
  somar os blocos (480) superestimaria o sono.

Nenhum dado real de saúde.
