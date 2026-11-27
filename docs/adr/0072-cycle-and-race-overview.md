# ADR 0072 — Visão de ciclo e prova

**Status:** Aceito e implementado em 2026-11-27 (domínio + app + UI + testes/goldens).
**Data:** 2026-11-27
**Escopo:** Produto, arquitetura e UI; projeção somente leitura de `MacroPlan`/`MesoCycle`.
**Relacionados:** ADR 0023 (plano como intenção), ADR 0030 (evento de prova), ADR 0037
(recuperação pós-prova), ADR 0041 (mesociclo), ADR 0043 (periodização), ADR 0066
(taper), ADR 0070 (prévia de domingo), ADR 0071 (vocabulário e voz).

---

## 1. Contexto

### 1.1 O que já existe

- `MacroPlanResolver` (`macro-plan-v2`) distribui `BASE`/`BUILD`/`PEAK`/`TAPER`
  por distância (5K 12/8 semanas, 10K 8, meia 12, maratona 16) e devolve
  `MacroWeekIntent` da semana atual até a prova. Fora do horizonte, devolve o
  modo geral sem semanas.
- `MesoCycle` deriva fase e descarga de semanas reais. A descarga ocorre após 3
  semanas de carga consecutivas, só em `BASE`/`BUILD`; `PEAK`, `TAPER` e
  `RECOVERY` nunca descarregam. Sem histórico, usa fallback conservador.
- `RacePlan` define a janela de redução (14 dias para 5K–meia; 21 para
  maratona), o corte de qualidade e o fator de taper.
- `TaperBaselineResolver` calcula o baseline numérico apenas quando a primeira
  semana de taper já é conhecida; não serve para projetar um taper distante.
- `VolumeProgression` decide o volume de uma única semana a partir de
  referências reais e gates. Não é projetável sem inventar prontidão, carga e
  adesão futuras.
- O `WeeklyPlanGenerator` calcula `MacroPlan` + `MesoCycle` a cada geração, mas
  o `WeeklyPlan` persistido não carrega fase nem descarga. A descarga é
  inferível por `R-PLAN-04` em `plan.ruleIds`.
- `TodayPreviewGenerator` gera e persiste a semana seguinte na primeira abertura
  de domingo. O `TodayUiState` expõe `plan` e `nextWeekPlan`; o
  `PlanWeekCard` e o `NextWeekPreviewCard` renderizam esses dados.
- A revisão semanal cobre a última semana fechada e mostra um bloco "Próxima
  semana" que recebe a semana atual (`currentWeekPlan`), não a prévia de
  domingo. O rótulo é ambíguo, mas o conteúdo é a semana seguinte à revisada.

### 1.2 O problema

Com uma prova a 6, 8 ou 12 semanas, o usuário vê o treino de hoje e a semana
atual, mas não vê:

- quando começa a redução para a prova;
- quando cai uma semana de descarga;
- como o volume se move entre base, construção, pico e prova;
- quais semanas são de carga e quais são de recuperação;
- quanto tempo falta e como planejar a vida em torno do ciclo.

O app já sabe a maior parte disso, mas descarta a informação a cada geração.

### 1.3 Restrições

1. O motor é determinístico e a UI não calcula regra de treino.
2. O plano é intenção; a prescrição do dia é autoridade (ADR 0023).
3. Dado ausente é `null`, nunca zero; ausência reduz confiança.
4. Sem dados, não aumentar carga.
5. Nenhuma segunda prescrição; ajustes continuam no replanejamento existente.
6. Sem dependência nova, sem dynamic color, sem migração destrutiva.
7. Semanas futuras não podem contar em adesão, volume, mesociclo, descarga ou
   referência de carga antes de começarem.
8. UI em pt-BR, números tabulares, contraste AA, alvos de 48 dp, TalkBack e
   dark/light; vocabulário do ADR 0071.

---

## 2. Decisão

### 2.1 Projeção somente leitura, não plano multi-semana persistido

Criar um calculator puro `CycleOverviewCalculator` em `core:domain/cycle`, que
lê a prova, a semana atual, a experiência, o plano atual, a prévia, as
referências reais e a recuperação. Ele devolve `CycleOverview` com semanas,
marcos, direção de volume, limite de treinos fortes e status de descarga.

O calculator **não gera `WeeklyPlan`, não gera `PlannedSession`, não grava nada
e não altera schema**. A autoridade continua sendo o plano da semana + a
prescrição do dia.

**Por quê:** persistir 8–16 semanas de plano viola o ADR 0023 e o ADR 0043;
qualquer check-in invalida o futuro e o mecanismo `STALE` já existe. A projeção
derivada é barata, sempre fresca e não cria uma segunda verdade.

### 2.2 Horizonte: 6 semanas ou até a prova

- A lista mostra a semana atual + as 5 seguintes, **ou até a semana da prova**,
  o que vier primeiro.
- Marcos cobrem o que está longe: início da preparação específica, início da
  redução e data da prova.
- A descarga condicional só aparece se cair dentro das 6 semanas visíveis. Fora
  disso, o rodapé explica a regra; não há data distante inventada.

**Por quê:** 4 semanas é o mínimo, mas pode esconder a transição de bloco; 6
semanas mostram um bloco completo de descarga e cabem em um sheet. "Até a prova"
transformaria 8–16 semanas em linhas especulativas e pareceria um contrato.

### 2.3 Compromisso × intenção

| Elemento | Status | Fonte |
|---|---|---|
| Data, distância e existência da prova | Compromisso atual; muda se o usuário editar | `RaceEvent` persistido |
| Fase de cada semana | Planejado, sujeito a ajuste | Tabela macro |
| Início da redução e semana da prova | Planejado | `RacePlan` + tabela macro |
| Sessões da semana atual | Esta semana; o dia confirma ou ajusta | Plano + prescrição |
| Semana seguinte no domingo | Prévia; pode mudar até segunda | Prévia persistida |
| Volume e dias fortes futuros | Intenção, sem número exato | Fase + experiência |
| Descarga atual/próxima | Confirmada | `R-PLAN-04` no plano |
| Descarga futura | Cenário condicional, "se" | Simulação local de `MesoCycle` |
| Prontidão para a prova | Não é mostrada | Não existe modelo |

A UI usa três rótulos: **"Esta semana"**, **"Prévia"** e **"A confirmar"**. O
rodapé diz: "Planejado, sujeito a ajuste. O check-in do dia confirma ou reduz."

### 2.4 Incerteza sem números inventados

- Fase: enum canônico + rótulo (`Base`, `Construção`, `Pico`, `Semana de prova`,
  `Recuperação`).
- Volume futuro: direção (`Constrói`, `Estável`, `Reduz`, `Recupera`), nunca
  minutos.
- Minutos só para a semana atual e para a prévia, com a fonte explícita.
- Descarga: `CONFIRMED`, `CONDITIONAL` ou ausente. A condicional diz "se você
  completar N semanas de carga" e tem confiança `LOW`.
- Desconhecido é `null`/"—", nunca zero.
- Confiança máxima `MEDIUM` para a tabela macro; `LOW` para descarga
  condicional.

### 2.5 Onde a visão vive

- **Hoje:** resumo compacto dentro do `PlanWeekCard` (fase, prova em X semanas,
  link "Ver caminho") e um `ModalBottomSheet` com a visão completa.
- **Perfil/Prova:** o mesmo resumo no `RaceEventCard` e o mesmo sheet. Uma única
  composable e um único cálculo.
- **Tendências:** nenhum card novo. O bloco "Próxima semana" da revisão ganha o
  intervalo de datas; opcionalmente um link para o sheet, sem duplicar
  conteúdo.
- **Coach:** não entra nesta entrega. Se responder no futuro, usa o mesmo
  `CycleOverview`, nunca uma segunda projeção.
- **Sem aba nova.**

**Por quê:** a pergunta nasce ao ver o plano de hoje; a prova é configurada no
Perfil. O sheet mantém o contexto e evita uma sexta aba.

### 2.6 Reação a mudanças

| Evento | Comportamento |
|---|---|
| Data/distância da prova muda | A projeção recalcula na leitura. O plano da semana atual continua autoridade; se a prova mudou depois da criação do plano, aparece "A prova mudou depois deste plano. Ajuste a semana para alinhar." O botão existente "Ajustar" abre o replanejamento. Nada é reescrito sozinho. |
| Doença / semana perdida | Nenhuma semana futura conta como histórico. A descarga condicional se desloca. Copy sem culpa: "Uma semana perdida empurra a descarga; o caminho continua." |
| Viagem / disponibilidade | Fases e marcos não mudam (dependem da prova), mas não prometemos dias futuros. O plano da semana atual e o replanejamento existente resolvem a execução. |
| Replanejamento da semana | A linha "Esta semana" reflete o plano novo; as futuras não mudam. Nenhuma segunda prescrição. |
| Prova removida / concluída | Sem prova planejada: estado vazio honesto (ou recuperação ativa). Nada de ciclo inventado. |
| Sem histórico | Fases macro aparecem como intenção; descarga futura não ganha data, só a regra. |

### 2.7 Não conflitar com a prévia nem com o card de hoje

- O `CycleOverview` lê **o mesmo** `currentPlan` e o **mesmo** `nextWeekPlan`
  que os cards. Não gera uma segunda prévia.
- A linha "Próxima" só tem minutos quando
  `nextWeekPlan.weekStart == currentWeekStart.plusWeeks(1)`; é a prévia
  persistida.
- A revisão semanal continua respondendo "o que mudou na semana que começou"; o
  ciclo responde "o que vem depois". O intervalo de datas elimina a ambiguidade
  entre a semana revisada, a semana atual e a prévia.
- Teste de consistência compara minutos, dias fortes e descarga da linha atual
  com o `PlanWeekCard`, e da linha seguinte com o `NextWeekPreviewCard`.

### 2.8 Semanas futuras nunca contam antes de começar

- A projeção é display-only; não é passada para `VolumeProgression`,
  `MesoCycle` real, `TaperBaselineResolver`, `WeeklyReviewCalculator` ou carga.
- A simulação de descarga condicional é local ao cálculo, não é persistida, não
  entra em `recentWeeks` e não altera `R-PLAN-04` de nenhum plano.
- A descarga real continua derivada apenas de semanas concluídas.
- Testes de regressão provam que adicionar uma prévia futura não muda adesão,
  volume, mesociclo, descarga nem referência de carga.

---

## 3. Modelo

```kotlin
enum class CycleWeekState { CURRENT, NEXT, FUTURE, RACE, RECOVERY }
enum class CycleVolumeIntent { BUILD, MAINTAIN, REDUCE, RACE, RECOVER, UNKNOWN }
enum class CycleStrongIntent { NONE, MICRO_DOSE, UP_TO_ONE, UP_TO_TWO }
enum class CycleDeload { NONE, CONFIRMED, CONDITIONAL }
enum class CycleMilestoneKind { PREPARATION_START, TAPER_START, RACE, DELOAD }

data class CycleWeek(
    val weekStart: LocalDate,
    val phase: MesoPhase?,
    val state: CycleWeekState,
    val isCurrent: Boolean,
    val isNext: Boolean,
    val volume: CycleVolumeIntent,
    val strong: CycleStrongIntent,
    val plannedMinutes: Int?,
    val strongDays: List<LocalDate>,
    val isPreview: Boolean,
    val partialWeek: Boolean,
    val deload: CycleDeload,
    val deloadConditionWeeks: Int?,
    val confidence: DataConfidence,
)

data class CycleMilestone(
    val kind: CycleMilestoneKind,
    val weekStart: LocalDate,
    val conditional: Boolean,
)

data class CycleOverview(
    val today: LocalDate,
    val race: CycleRace?,
    val weeks: List<CycleWeek>,
    val milestones: List<CycleMilestone>,
    val preparationStart: LocalDate?,
    val staleRace: Boolean,
    val hasLoadHistory: Boolean,
    val confidence: DataConfidence,
    val ruleIds: List<String>,
    val evidenceIds: List<String>,
    val recoveryUntil: LocalDate?,
)
```

O calculator resolve a fase de cada semana com `MacroPlanResolver.resolve(race,
weekStart)`, não com uma tabela paralela. Para descarga, usa
`MesoCycle.resolve` com a fase macro e referências reais; a simulação de
semanas futuras é interna, marcada como especial/descarga quando aplicável e
nunca persistida.

`MacroPlan` ganha dois campos aditivos (`preparationStart`, `horizonWeeks`) para
o marco de início da preparação específica. Não mudam decisão, `inputsHash` nem
`macro-plan-v2`.

O mapa de rótulos de `MesoPhase` é extraído para `MesoPhaseLabels`, reutilizado
pelo `WeeklyPlanGenerator` e pelo ciclo. Texto idêntico, sem bump.

---

## 4. Alternativas descartadas

| Alternativa | Motivo |
|---|---|
| Persistir 8–16 semanas de plano | Viola plano-como-intenção (ADR 0023); check-in invalida o futuro; schema e segunda autoridade. |
| Gerar `WeeklyPlan` futuros sob demanda | Exigiria inventar prontidão, carga e adesão; produziria durações/paces que o app não vai cumprir. |
| Mostrar só a semana atual + prévia | É o estado atual; não responde ao gap 7. |
| Listar todas as semanas até a prova | 8–16 linhas especulativas, precisão falsa e sensação de contrato. |
| Aba "Ciclo" | Custo de navegação, duplicação e perda do contexto de decisão do Hoje. |
| Descarga em calendário fixo | A descarga depende de 3 semanas de carga concluídas; não é data de calendário. |
| LLM narrando o caminho | O motor é determinístico; LLM só reescreveria texto e não é necessário. |
| Persistir fase no plano agora | Mudaria schema sem necessidade; a fase é re-derivável de prova + semana. Pode virar decisão futura se a auditoria exigir proveniência. |

---

## 5. Matriz de testes

- `CycleOverviewCalculatorTest`: 5K 6/8/12 semanas, 10K, meia, maratona, prova
  além do horizonte, sem prova, prova passada, prova no meio da semana.
- `CycleDeloadTest`: 0/1/2/3 semanas de carga, fase PEAK/TAPER, sem histórico,
  descarga confirmada na prévia.
- `CyclePartialWeekTest`: plano criado no meio da semana; primeira semana.
- `CycleStaleRaceTest`: prova alterada depois do plano; projeção nova e aviso.
- `CycleRecoveryTest`: recuperação ativa sem prova planejada.
- `CycleDeterminismTest`: mesmos inputs, mesmo output.
- `FutureWeekIsolationTest`: prévia futura não altera adesão, volume, meso,
  descarga nem referência de carga.
- `CycleOverviewFormatTest`: datas, fases, volume, fortes, "a confirmar".
- `CycleOverviewSheetTest`: loading/empty/failed/ready.
- `TodayViewModelCycleTest`/`ProfileViewModelCycleTest`: estado, prévia, prova
  alterada, sem escrita.
- `CycleOverviewA11yTest` + goldens dark/light/estreito/200%/sem prova.
- Gate de vocabulário do domínio e da UI, sem novas linhas de baseline.

---

## 6. Consequências

- O usuário responde "quando é a prova, quando começa a redução, quando é a
  descarga, como o volume se move" sem o app prometer semanas fechadas.
- Nenhuma migração, nenhuma dependência, nenhuma mudança de motor.
- Limitações explícitas: volume exato futuro não existe; descarga futura é
  cenário; prontidão de prova não é respondida.
- A UI ganha uma superfície nova (sheet) sem aba nova; o Perfil ganha um link
  contextual.
- O Coach só poderá citar o ciclo no futuro a partir do mesmo `CycleOverview`;
  nenhuma prosa nova decide fase, carga ou plano.
