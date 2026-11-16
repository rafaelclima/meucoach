# ADR 0049 — Prontidão, check-in e sono plausível

**Status:** Aprovado em 2026-11-16 — domínio implementado (C4-28, C4-34..C4-36); UI, goldens e docs pendentes (C4-30..C4-33, C4-37).
**Data:** 2026-11-16
**Escopo:** `ReadinessCalculator`, `ReadinessResult`, `ReadinessFlag`,
`DailyMetricsReadinessBuilder`, `HealthMetricsAggregator`, `CheckInViewModel`/`CheckInScreen`,
`DailyPrescriptionEngine`, `QualityGate`, `WeeklyReview`.
**Relacionados:** ADR 0022 (tempo), ADR 0023 (plano × prescrição), ADR 0024
(evidência), ADR 0044 (oportunidade de qualidade), ADR 0046 (carga), ADR 0048
(schema).
**PRD:** `docs/PRD_TREINO_REAL.md` FR-406, FR-407, §11.5 (Q-02).
**Decisão do usuário 0:** 16/11/2026.

---

## 1. Contexto

### 1.1 O que existe hoje

- O check-in abre com **3/3/3/3** (`CheckInViewModel.kt:20-23`).
- O componente subjetivo é `(20 − Hooper)/16 × 100`
  (`ReadinessCalculator.kt:94-95`); 3/3/3/3 = **50**.
- O subjetivo pesa **35%** (`:11-17`). Com sono/HRV/RHR típicos, a prontidão
  fica em ~66–68 (âmbar), e `NO_QUALITY` é ligado (`:78-80`).
- Sem check-in, os pesos são renormalizados e a prontidão pode subir (ex.: 76
  verde). O app recompensa não responder.
- `HealthMetricsAggregator` soma as sessões de sono e aceita qualquer duração
  (`:58-96`). Uma sessão parcial de **36 min** vira noite real, zera o score de
  sono e liga `SHORT_SLEEP` (`:56-62,97-107`).
- `DailyPrescriptionEngine` usa `absenceOnly`/`negativeReadiness` para bloquear
  qualidade, mas não distingue “sem check-in” de “check-in neutro” de forma
  explícita.

### 1.2 O problema

Há uma inversão de incentivo: o usuário que responde honestamente com valores
neutros bloqueia a qualidade; o que não responde fica mais verde. E um artefato
de sincronização (36 min) é tratado como noite curta, bloqueando qualidade por
um dado que não existe.

### 1.3 Decisão do usuário 0

- Q-02: check-in ausente **bloqueia** qualidade, com prompt para responder.
- O check-in não deve abrir com 3/3/3/3; o usuário deve escolher.
- Um check-in tardio pode restaurar a qualidade antes de a sessão ser concluída.
- Sono implausível é ausente, não curto (PRD P-08/Q-02).

---

## 2. Decisão

### 2.1 Elegibilidade por prontidão

`ReadinessResult` ganha:

- `qualityEligibleByReadiness: Boolean` (computed property ou campo).
- `ReadinessFlag.SUBJECTIVE_MISSING`.
- `ReadinessFlag.SLEEP_DATA_SUSPECT` (ou equivalente).

Regra:

```
qualityEligibleByReadiness =
    level == GREEN &&
    SUBJECTIVE in components &&
    SHORT_SLEEP !in flags &&
    ILLNESS_OR_INJURY !in flags
```

- Sem check-in: `SUBJECTIVE_MISSING` é adicionado; o score pode ser exibido com
  “dados parciais”, mas `qualityEligibleByReadiness=false`.
- Check-in 3/3/3/3: 68 âmbar; não elegível.
- Check-in 4/2/2/2: 75 verde; elegível.
- Check-in 5/1/1/1: 84 verde; elegível.
- A prontidão pode continuar sendo exibida como “Alta (dados parciais)” quando o
  subjetivo falta; a confiança cai e o gate de qualidade é falso. Não é “corpo
  ruim”; é “dado insuficiente para liberar”.

### 2.2 Check-in sem default enganoso

- O formulário não abre com 3/3/3/3.
- Opção A (recomendada): campos nulos até o usuário escolher; o botão salvar fica
  desabilitado até as quatro respostas.
- Opção B: pré-preencher com o último check-in (≤7 dias) e exigir confirmação
  explícita; se não houver, campos nulos.
- Labels de 1/3/5 visíveis:
  - Sono: Ruim / Regular / Ótima;
  - Fadiga: Nenhuma / Moderada / Muita;
  - Dor: Nenhuma / Moderada / Muita;
  - Estresse: Baixo / Moderado / Alto.
- O texto explica que 3 é “moderado”, não “normal”.
- O check-in continua em ≤20 s; a mudança é de clareza, não de burocracia.

### 2.3 Check-in tardio e reavaliação

- Se a prescrição do dia foi `EASY` por falta de check-in e o usuário faz o
  check-in antes de a sessão ser concluída, o `GetTodayPrescription` reavalia.
- Se o `QualityGate` agora é elegível e o plano tem uma oportunidade, a nova
  versão pode ser `QUALITY`/`KEEP`.
- A versão anterior fica no histórico; o usuário vê “Atualizado: com o check-in,
  a qualidade foi liberada”.
- Se a sessão já está `DONE`/`CLOSED`, não reavalia.
- Se houver execução guiada ativa (ciclo 3), não muda a prescrição em andamento.
- `R-PRESC-02` é emendada: com os mesmos inputs, o dia só reduz; com inputs
  novos, o dia é reavaliado e pode restaurar a qualidade planejada antes da
  conclusão.

### 2.4 Sono plausível

`SleepNightValidator` (domínio de saúde):

```
nightWindow = [previous 18:00, current 12:00] local
sessions = filter(overlaps nightWindow)
blocks = merge(sessions, gap <= 120 min)
minutes = sum(blocks)
if (minutes !in 180..840) -> SUSPECT
else -> VALID(minutes)
```

- Sessão de 36 min sozinha → `SUSPECT`; persistir `sleepDurationMin = null`.
- Cochilo diurno não entra na janela noturna; não soma na noite.
- 6 h + cochilo 30 min → 6 h válido (o cochilo fica fora).
- 5 h 30 válido → `SHORT_SLEEP` (<360).
- Duas sessões de 4 h com gap de 30 min → 8 h (merge).
- Duas sessões de 4 h com gap de 3 h → maior bloco 4 h; declarar “sono
  fragmentado” e não somar; a decisão é documentada.
- `SUSPECT` não zera o sono nem liga `SHORT_SLEEP`; o componente fica ausente, a
  confiança cai e o app diz “sono não confiável” no diagnóstico.
- O `WeeklyReview` não conta noites suspeitas como medidas.
- O diagnóstico de sync pode mostrar “1 noite ignorada por dados implausíveis”,
  sem valor bruto na UI principal.

### 2.5 Gate de qualidade

O `QualityGate` (ADR 0044) usa a precedência:

1. doença/vermelho;
2. `BEGINNER_GUARD`;
3. `SUBJECTIVE_MISSING`;
4. `SHORT_SLEEP`;
5. recovery/taper/race;
6. carga;
7. monotonia;
8. strain;
9. ACWR;
10. elegível.

O `DailyPrescriptionEngine` deixa de usar `absenceOnly` como proxy de
“dados insufficientes”; usa o `QualityGate` e o `qualityEligibleByReadiness`.

### 2.6 Invariantes

- Check-in ausente não libera qualidade.
- 3/3/3/3 não é default.
- Noite <3 h ou sessão parcial não é `SHORT_SLEEP`.
- Noite válida <6 h é `SHORT_SLEEP`.
- Check-in tardio pode restaurar a qualidade antes do `DONE`.
- `DONE`/`CLOSED` nunca são reavaliados.
- Doença/dor/vermelho continuam prevalecendo.

---

## 3. Dados e APIs

- `ReadinessResult.qualityEligibleByReadiness`; `ReadinessFlag.SUBJECTIVE_MISSING`;
  `ReadinessFlag.SLEEP_DATA_SUSPECT`.
- `ReadinessCalculator` adiciona os flags; `DailyMetricsReadinessBuilder` usa o
  sono validado.
- `SleepNightValidator`/`SleepNight` no domínio de saúde.
- `HealthMetricsAggregator` usa o validador; suspeita → `sleepDurationMin=null`.
- `CheckInForm`/`CheckInUiState` com campos nulos ou draft; labels; validação.
- `GetTodayPrescription` reavaliação (já versionada por `inputsHash`).
- `QualityGate`/`QualityBlockReason` (ADR 0044).
- `WeeklyReview` ignora noites suspeitas.

Sem migração de schema para este ADR (o `sleepDurationMin=null` já é suportado).

---

## 4. Alternativas consideradas

1. **Manter 3/3/3/3 como default.**
   Rejeitada: pune o usuário que aceita o default e bloqueia qualidade sem
   intenção.
2. **Não bloquear qualidade sem check-in.**
   Rejeitada: mantém a inversão (omitir é melhor que responder).
3. **Usar só dados objetivos.**
   Rejeitada: o subjetivo é primeira classe (`EV-SAW-2016`); sem ele, a confiança
   cai.
4. **Tratar 36 min como noite curta.**
   Rejeitada: é um artefato de sincronização, não uma noite real.
5. **Somar todos os cochilos na noite.**
   Rejeitada: superestima o sono; a janela noturna é mais robusta.
6. **Reavaliar depois do DONE.**
   Rejeitada: reescreveria o histórico.

---

## 5. Consequências

### 5.1 Positivas

- O app deixa de recompensar a omissão.
- O check-in fica claro; 3 = moderado.
- Um check-in tardio não perde a qualidade.
- O sono não é zerado por um glitch.
- A prontidão fica mais honesta: “dados parciais” não é “corpo ruim”.

### 5.2 Riscos

- Exigir check-in pode irritar; o app pré-preenche o último e explica, mas a
  política é explícita.
- A reavaliação tardia pode confundir; a UI mostra a nova versão e o motivo.
- A janela noturna/merge pode não capturar todos os dispositivos; o diagnóstico
  ajuda e a decisão é conservadora (suspeita = ausente).
- A mudança de flags pode exigir atualização de testes antigos; justificar.

### 5.3 Segurança

- Doença, dor, vermelho, sono curto válido e recuperação continuam bloqueando.
- O check-in tardio só restaura antes do DONE; não muda o histórico.
- A ausência de sono não é “sono bom”; é dado ausente, com confiança menor.

---

## 6. Matriz de testes

| Caso | Entrada | Saída |
|---|---|---|
| `G-READY-03` | sem check-in | `SUBJECTIVE_MISSING`; qualidade bloqueada; score exibido com dados parciais |
| 3/3/3/3 | check-in | âmbar; qualidade bloqueada |
| 4/2/2/2 | check-in | verde; qualidade mantida |
| 5/1/1/1 | check-in | verde; qualidade mantida |
| Check-in tardio | EASY → check-in antes do DONE | nova versão QUALITY |
| DONE | check-in depois | histórico intacto |
| `G-DATA-02` | 36 min | suspect/null; sem SHORT_SLEEP |
| 5h30 | válido | SHORT_SLEEP; qualidade bloqueada |
| Cochilo | 6h + 30 min | 6h válido |
| Fragmentado | 4h + gap 3h | maior bloco; sem soma |
| UI | formulário | sem 3/3/3/3; labels; salvar desabilitado |
| A11y | labels/TalkBack/48 dp | ok |

---

## 7. Rollout

- `daily-prescription-v6`; `readiness` novo.
- O check-in UI muda no app; o `CheckInViewModel`/`Screen` são atualizados.
- O `QualityGate` usa os novos flags; o `TodayViewModel` mostra o prompt.
- O `WeeklyReview` ignora noites suspeitas.
- `SPORTS_SCIENCE` §7.3/§7.4 e `R-READY-06`/`R-DATA-02` atualizados.

---

## 8. Fora de escopo

- Novas perguntas de check-in.
- Diagnóstico médico.
- Suplementos/nutrição.
- Sensores novos.
- Alterar os pesos 35/25/25/15 neste ciclo (a mudança é de default/flag, não de
  peso).

---

## 9. Referências

- `docs/PRD_TREINO_REAL.md` FR-406/FR-407, §11.5 (Q-02).
- `docs/SPORTS_SCIENCE.md` §7.1, §7.3, §7.4.
- ADR 0022, 0023, 0024, 0044, 0046, 0048.
- `EV-SAW-2016`, `EV-BUCHHEIT-2014`, `EV-HALSON-2014`, `EV-WALSH-2021`,
  `EV-WATSON-2015`.
