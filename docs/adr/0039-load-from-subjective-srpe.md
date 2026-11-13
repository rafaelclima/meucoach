# ADR 0039 — Carga subjetiva por sRPE (FR-013 / BL-022)

**Status:** Aceito em 2026-11-13 (aprovação do usuário 0) e implementado no mesmo dia nas três fatias (motor, coleção/wiring, UI/copy).
**Data:** 2026-11-13
**Escopo:** PRD FR-013/BL-022, `ROADMAP` P1; integra o esforço percebido ao motor de carga.
**Relacionados:** ADR 0024 (evidência de conclusão), ADR 0026 (cobertura de carga), ADR 0038 (progressão de volume), ADR 0029 (copy educativa). Fonte: `EV-FOSTER-2001` (sRPE de Foster).

---

## 1. Contexto

### 1.1 O que já existe

- O motor de carga (`TrainingLoadEngine`) só enxerga **corridas importadas** e,
  dentro delas, só usa FC (`TRIMP`) ou pace calibrado (`rTSS`), uma métrica por
  dia (`R-LOAD-01`).
- `TrainingLoadCalculator.srpe(rpe, durationMin)` existe e é testada desde o
  início, mas nenhuma execução a alimenta: para o PMC, um treino registrado
  manualmente ("fiz sem registro", 40 min, RPE 6) **não aconteceu**.
- Execuções `MANUAL` contam adesão e nunca entram em volume (ADR 0024);
  `session_executions.rpe` já existe no schema v17 (nullable, 1–10) — o
  `MANUAL` já exige RPE.
- Consequência atual: um iniciante sem FC confiável que registra honestamente
  fica com `daysWithLoad` baixo, `LoadDataState` `INSUFFICIENT`/`STALE`, ACWR
  `null` e treino forte bloqueado por "dados desatualizados" — não por
  prontidão ruim.

### 1.2 Por que agora

É o único buraco de evidência restante no laço central: prova, recuperação,
progressão e revisão já estão entregues. O PRD (FR-013/BL-022) já define o
papel do sRPE: *fallback quando TRIMP/rTSS não estiverem disponíveis; nunca
sobrepor FC confiável*. O desenho completo está em
`docs/PLANO_FR013_SRPE_CARGA_SUBJETIVA.md` (decisões D1–D9, aprovadas).

---

## 2. Restrições

1. **Nunca sobrepor FC/pace.** Um dia com corrida importada com FC **nunca**
   muda de métrica por causa de sRPE (D1: `TRIMP > rTSS > SRPE`).
2. **Um dia, uma métrica.** sRPE não soma com TRIMP/rTSS no mesmo dia
   (`R-LOAD-01`, D1).
3. **Dado subjetivo justifica conservação, nunca aceleração (D5).** sRPE
   alimenta ACWR e guardrails de redução; a trava de volume +6% (ADR 0038) e a
   liberação de qualidade passam a exigir carga **objetiva** conhecida.
4. **Volume e adesão intocados (D7).** `MANUAL` segue contando adesão e nunca
   entra na base de volume; sRPE só toca a série de carga.
5. **Dado ausente não vira zero; ausência reduz confiança (regra geral).**
6. **Sem migração.** `session_executions.rpe` já existe; a carga não é
   persistida — é recomputada a cada leitura.
7. **Motor determinístico.** Nenhuma UI calcula carga; o `core:domain` recebe
   as execuções e devolve o snapshot.

---

## 3. Decisão

### 3.1 Terceiro nível de carga

`LoadMetric` ganha `SRPE`. `estimate()` aplica, nesta ordem, uma métrica por
dia: `TRIMP` (FC dentro de repouso..máxima + RHR/FM do perfil) → `rTSS` (pace +
pace de limiar calibrado) → `SRPE = Σ(RPE × minutos)` das execuções com RPE do
dia, **só quando nenhum nível objetivo se aplica**.

### 3.2 Quem alimenta sRPE (D2)

`LoadExecution(date, durationMin, rpe)` — execuções com `rpe != null` e
duração positiva que não tenham atividade com métrica objetiva no dia:
`MANUAL` (RPE obrigatório) e `ACTIVITY`/`GUIDED` quando o usuário registra RPE
e a corrida vinculada não tem FC/pace útil. A corrida com FC satisfaz nível 1–2
e o sRPE do dia é ignorado. **Nunca os dois no mesmo dia.**

A coleção acompanha o fechamento: o diálogo "Concluir treino" mostra o campo
**"Como foi o esforço? (opcional)"** quando uma corrida é selecionada, grava em
`session_executions.rpe` (coluna existente, sem migração) e o app traduz as
execuções em `LoadExecution` no `buildLoadSnapshot` — a data vem do início da
corrida ou do `plannedSessionId` (registro manual). O RPE também aquece o
frescor da fonte (`lastSubjectiveLogAt`).

### 3.3 Cobertura e frescor (D3/D4)

- Dias `SRPE` contam em `daysWithLoad` (mínimo de 8/28 mantido) e o snapshot
  expõe `subjectiveDaysInWindow` — a fração subjetiva da janela é declarada,
  nunca escondida.
- Frescor dual: `ActivitySourceStatus` considera a última importação **ou** a
  última execução com RPE (`lastSubjectiveLogAt`), com o mesmo teto de 14 dias
  (ADR 0026). Registrar esforço é declarar o que aconteceu — menos preciso que
  um arquivo, mas é declarar.

### 3.4 A assimetria objetiva × subjetiva (D5)

O `LoadSnapshot` ganha `objectiveDaysWithLoad` e `objectiveDataState` (mesmas
regras de `dataState`, restritas a TRIMP/rTSS). Nos geradores:

- **Confiança objetiva** (`objectiveDataState == KNOWN`) governa: gate de
  qualidade, `VolumeGates.loadKnown` (volume +6%) e downgrade de confiança.
- **Carga conhecida** (`dataState == KNOWN`) governa: ACWR, guardrails de
  redução (prontidão vermelha e `acwrHigh` continuam reduzindo longão e
  qualidade mesmo com carga só-subjetiva) e mensagens de dados.

Nova regra `R-LOAD-04` — "Carga estimada pelo esforço percebido: conserva,
nunca libera aumento" (LIMITED, `EV-FOSTER-2001`) — citada quando
`loadState == KNOWN` mas a carga não é objetiva, com rationale aproximado
("sua carga recente vem do esforço percebido: treino forte precisa de carga
medida por FC ou ritmo").

### 3.5 Versionamento (D8)

`PrescriptionPolicy.ENGINE_VERSION` → `daily-prescription-v5`;
`WeeklyPlanGenerator.ENGINE_VERSION` → `weekly-plan-v5`. Prescrições e planos
persistidos não são reescritos; a nova versão vale para novos cálculos.
Schema permanece **v17**, sem migração.

---

## 4. Alternativas consideradas

1. **sRPE só para exibição.** Rejeitada: não resolve o bloqueio de treino
   forte por "dados desatualizados" de quem treina e registra honestamente.
2. **sRPE combinado com TRIMP/rTSS no mesmo dia.** Rejeitada: violaria
   `R-LOAD-01` e dobraria carga.
3. **sRPE só para `MANUAL`.** Rejeitada: deixa fora o caso explícito do PRD
   (corrida de esteira/sem FC vinculada com RPE registrado).
4. **sRPE liberando aumento de volume.** Rejeitada (D5): iniciantes
   superestimam esforço fácil; dado subjetivo viraria permissão de acelerar.
5. **Frescor só de importação.** Rejeitada (D4): quem registra manualmente
   ficaria eternamente `STALE` — sRPE inócuo.

---

## 5. Matriz de testes

**Domínio (`core:domain`, TDD)**
- `TrainingLoadEngineSrpeTest`: sRPE preenche só quando nem TRIMP nem rTSS
  aplicam; múltiplas execuções somam; atividade sem métrica objetiva não
  bloqueia o dia subjetivo; `subjectiveDaysInWindow`/`objectiveDataState`
  declarados; espelhamento sem dias subjetivos; frescor dual (RPE aquece,
  import antigo não é resgatado por log subjetivo antigo).
- `WeeklyPlanGeneratorTest`: carga só-subjetiva mantém o volume (sem +6%),
  qualidade bloqueada com rationale do esforço percebido e `R-LOAD-04`.
- `DailyPrescriptionEngineTest`: qualidade planejada com carga só-subjetiva
  vira fácil citando `R-LOAD-04`; dia fácil cita a regra.
- `ExplanationCatalogTest`: `R-LOAD-04` catalogada.

**App (fatias 2–3)**
- `TrainingSignalsTest`: execução manual vira dia `SRPE`; corrida com FC mantém
  TRIMP e ignora o RPE; corrida sem métrica objetiva usa o RPE; log recente
  mantém a fonte fresca; execução sem RPE não cria carga.
- `CompleteSessionTest` / `SessionExecutionRepositoryTest`: RPE opcional no
  fechamento com corrida vinculada é persistido e `observeAll()` o expõe; valor
  fora de 1–10 é rejeitado.
- `TodayPlanTest`: o campo opcional aparece ao selecionar a corrida, o valor é
  enviado ao vincular e a nota "carga estimada pelo esforço que você sentiu"
  aparece no card quando o dia é `SRPE`.
- `ActivityComparisonFormatTest`: o RPE registrado aparece no detalhe mesmo sem
  alvo de esforço (linha "Esforço" com "—" no planejado; resumo de treino livre
  ganha "Esforço RPE n").
- `TodayExplanationLoadTest`: o sheet declara "Esforço percebido (RPE)" em dados
  usados, "FC ou ritmo para medir a carga objetivamente" em dados ausentes e
  cita `R-LOAD-04` com o título do catálogo.
- `TrendsViewModelTest`/`TutorialViewModelTest`/`ReplanContextBuilder`: todos os
  consumidores leem o mesmo `TrainingSignalSource` (com as execuções).

---

## 6. Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Iniciante superestimar esforço fácil | sRPE nunca libera aumento (D5) e confiança fica baixa |
| CTL/ATL misturando métricas distorcer tendência | Fração subjetiva exposta no snapshot; limitação documentada no `SPORTS_SCIENCE` §Carga |
| Gaming involuntário do ACWR | RPE alto não libera nada; RPE baixo só reduz a própria carga |
| Plano/prescrição persistidos mudarem sozinhos | Nada é reescrito; novas versões valem para novos cálculos |
| Doc e código divergirem | `R-LOAD-04` no catálogo com teste; `SPORTS_SCIENCE` e `DATA_MODEL` atualizados |

---

## 7. Fora de escopo

- `SessionFeedback` (tabela planejada no DATA_MODEL §3.9) — o RPE de execuções
  já cobre o fluxo atual.
- Nota factual da revisão semanal sobre dias subjetivos.

---

## 8. Consequências

- Quem treina sem FC e registra RPE — manualmente ou ao vincular uma corrida —
  passa a ter série de carga, ACWR e guardrails vivos, com mensagens honestas
  sobre a fonte do dado.
- O sRPE **conserva**: reduz e protege, nunca acelera. A progressão de volume
  e a qualidade continuam exigindo evidência objetiva.
- `ENGINE_VERSION` → `daily-prescription-v5` / `weekly-plan-v5`; schema **v17**,
  sem migração; nenhuma dependência nova. Tendências, tutorial, replanejamento e
  Hoje compartilham o mesmo `TrainingSignalSource`, então a série é única em
  todas as telas.
