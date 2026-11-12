# ADR 0038 — Progressão de volume a partir do realizado (FR-004 / N-07)

**Status:** Aceito em 2026-11-12 e implementado no mesmo dia.
**Data:** 2026-11-12
**Escopo:** PRD FR-004/BL-004, N-07, `ROADMAP` P1-05; substitui o ponto de volume do ADR 0010.
**Relacionados:** ADR 0010 (disponibilidade como teto), ADR 0011 (deload), ADR 0023 (plano × prescrição), ADR 0024 (evidência de conclusão), ADR 0026 (cobertura de carga), ADR 0028 (replanejamento), ADR 0030 (prova/taper), ADR 0037 (recuperação).

---

## 1. Contexto

### 1.1 O que já existe

- O plano semanal é montado a partir da disponibilidade: cada dia recebe
  `disponibilidade` limitada por `MIN/MAX` de cada tipo
  (`WeeklyPlanGenerator.kt:226-299`). O volume total é a soma dessas sessões.
- A única progressão implementada é a **dose de qualidade**: com semana anterior
  ≥ 80% concluída com evidência forte, libera-se uma segunda qualidade
  (`ADR 0010`, `R-PLAN-02`).
- Deload (−25%) e taper já reduzem volume depois da distribuição; recuperação
  pós-prova limita dias e tipos (`ADR 0037`).
- `WeeklyPlanInput` já recebe `previousWeek` e o app o lê do repositório; o
  replanejador recebe o mesmo campo.

### 1.2 O problema

1. **Volume estático.** Sem mudança de disponibilidade, o plano repete o mesmo
   volume para sempre; o coach não progride nem regride com o realizado.
2. **Salto quando a disponibilidade muda.** Um usuário que corre 20 min em 3 dias
   e passa a ter 6×60 min recebe ~seis vezes o volume da semana anterior.
3. **Promessa não executada.** `R-PLAN-01` diz “volume semanal +5–8% só se verde,
   ACWR 0,8–1,3 e sono ok” (`SPORTS_SCIENCE.md` §8.3/§14) e **nunca é emitida
   pelo gerador**. Pior: o catálogo a descreve como “Disponibilidade define os
   dias de treino” (`ExplanationCatalog.kt:32`) e o recomendador a usa quando não
   há disponibilidade (`WorkoutRecommender.kt:92`). A mesma regra tem três
   significados.
4. **Decisão vencida.** O ADR 0010 rejeitou progressão por percentual porque “sem
   histórico confiável de conclusão, vira progressão inventada”. Esse histórico
   existe desde o C1-04 (`CompletionEvidence` forte, `PlanCompletionCalculator`).

### 1.3 Por que agora

É o único item P1 do laço central de coaching que resta: prova, recuperação,
briefing, previsão e revisão semanal já estão entregues. Também fecha N-07
(auditabilidade do `R-PLAN-01`) e a inconsistência doc × código apontada no
PRD §0.4.

---

## 2. Restrições

1. **Disponibilidade continua sendo teto rígido.** O app não prescreve minutos
   além do que o usuário declarou ter disponível (ADR 0010 mantido nesse ponto).
2. **Dado ausente não vira número.** Sem histórico concluído, o volume continua
   derivado da disponibilidade, com aviso e confiança baixa; nada é inferido.
3. **Não punir.** Uma semana fraca não reduz o volume planejado abaixo do que já
   foi demonstrado; ela apenas bloqueia o aumento.
4. **Nada de volume novo em semanas especiais.** Deload, taper, semana de prova e
   recuperação pós-prova nunca crescem.
5. **Motor determinístico.** Nenhuma UI calcula volume; o `core:domain` recebe os
   planos anteriores e devolve o alvo.
6. **Sem migração e sem dependência nova.** `WeeklyPlan` já é persistido com
   status, duração e evidência; o histórico é lido de `weekly_plans`.
7. **Replanejamento não repõe carga.** A revisão do meio da semana continua
   proibida de aumentar o volume futuro (ADR 0028).

---

## 3. Decisão

### 3.1 Base de volume

Novo objeto puro em `core/domain/prescription`:

```kotlin
data class VolumeReference(
    val weekStart: LocalDate,
    val plannedMinutes: Int,
    val completedMinutes: Int,
    val completionRatio: Double?, // evidência forte
    val createdOn: LocalDate,
    val ruleIds: List<String>,
)

data class VolumeDecision(
    val minutes: Int,
    val baseMinutes: Int?,        // null quando não há histórico
    val baseWeekStart: LocalDate?,
    val growthPercent: Int,       // 0 = mantido
    val cappedByAvailability: Boolean,
    val block: VolumeGrowthBlock, // NONE quando cresceu
)
```

`VolumeProgression.decide(references, weekStart, availabilityCeiling, growthGates)`
aplica, nesta ordem:

1. **Semanas de referência usáveis:** `weekStart` anterior à semana alvo, plano
   criado no início da semana (`createdOn == weekStart`, nunca plano parcial),
   fora de deload/taper/recuperação (`R-PLAN-04`, `R-PLAN-05/10`, `R-RACE-04`),
   com duração planejada e ao menos uma sessão concluída com evidência forte.
   Olha no máximo **4 semanas** para trás.
2. **Base:** maior volume **concluído** (soma de `durationMin` das sessões `DONE`
   com evidência forte) entre as semanas usáveis. Empate resolve pela mais
   recente. É um volume já demonstrado pelo atleta — não uma projeção.
3. **Sem base:** `minutes = ceiling` (comportamento atual por disponibilidade),
   `block = NO_HISTORY`, confiança rebaixada para `LOW`: o plano ainda é honesto,
   mas não se apoia em volume demonstrado.
4. **Com base:** `minutes = min(ceiling, base × (1 + 6%))` quando todas as
   condições de crescimento valem; caso contrário `minutes = min(ceiling, base)`.
   - **+6%** é o ponto médio da faixa de 5–8% de `R-PLAN-01`; um número único e
     arredondado evita “média inventada” e mantém o teste determinístico.
   - **Crescimento só sobre a semana anterior.** Se a base veio de uma semana
     mais antiga (a anterior foi fraca ou especial), o volume é mantido, nunca
     aumentado. Assim o app não salta de uma semana fraca para um máximo antigo.
   - **Disponibilidade manda no teto:** se `base × 1,06` exceder o total que cabe
     na agenda, o alvo é o teto e o rationale diz que a disponibilidade limitou.
5. **Distribuição:** as sessões já montadas pela disponibilidade são escaladas
   proporcionalmente ao alvo (`duração × alvo / teto`), respeitando os pisos de
   cada tipo (fácil 20, qualidade 25, longão 30). Quando os pisos não cabem no
   alvo, os dias **fáceis mais curtos** saem da semana (nunca qualidade ou longão,
   e pelo menos um fácil permanece): o volume demonstrado é a dose e a agenda é
   só o teto. O longão continua limitado a 25–30% do volume quando o piso não o
   impede (em semanas muito pequenas o piso vence, como já acontecia).

### 3.2 Condições de crescimento

Todas obrigatórias (mesma lista já documentada em `SPORTS_SCIENCE` §8.3):

| Condição | Fonte no código |
|---|---|
| Base é a semana imediatamente anterior | `VolumeProgression` |
| Semana anterior ≥ 80% concluída **com evidência forte** | `CompletionEvidence.isStrong` |
| Prontidão verde | `ReadinessResult.level == GREEN` |
| Sem doença/lesão, sono curto ou `NO_QUALITY` | `ReadinessFlag` |
| Carga conhecida e ACWR entre 0,8 e 1,3 | `LoadSnapshot` (`R-LOAD-03`) |
| Semana sem deload, taper, prova ou recuperação | `weekly-plan-*` e `R-RACE-04` |

Fora dessas condições o volume é **mantido**. Não existe redução automática por
semana fraca: a prescrição do dia e os guardrails de prontidão já reduzem a dose
quando necessário, e cortar o plano inteiro seria punição sem evidência.

### 3.3 Onde entra

| Camada | Mudança |
|---|---|
| `WeeklyPlanInput` / `ReplanInput` | novo campo `recentWeeks: List<WeeklyPlan> = emptyList()`; app passa até 4 semanas anteriores |
| `WeeklyPlanGenerator` | calcula teto, decide o alvo, escala sessões, cita `R-PLAN-01` e `EV-GABBETT-2016`, escreve a base no rationale |
| `PlanReplanner` | repassa `recentWeeks` ao gerador e inclui o histórico no `inputsHash`; a regra de “não repor carga” continua impedindo aumento no meio da semana |
| `TodayViewModel`, `TutorialViewModel`, `ReplanContextBuilder` | leem `getWeeks(1..4)` do `TrainingPlanRepository` |
| `WorkoutRecommender` | deixa de usar `R-PLAN-01` para “sem disponibilidade” (passa a citar `R-DATA-01`) |
| `ExplanationCatalog` | `R-PLAN-01` passa a significar progressão de volume |
| `ENGINE_VERSION` | `weekly-plan-v3` |

### 3.4 Copy e rationale

- Sem histórico: “Volume definido pela sua disponibilidade: ainda não há semana
  concluída com evidência para ajustar.”
- Cresceu: “Volume: base de 120 min concluídos na semana de 27/10; +6% nesta
  semana, alvo de 127 min.”
- Limitado pela agenda: “…; limitado pela sua disponibilidade em 110 min.”
- Mantido: “Volume: base de 120 min concluídos na semana de 27/10; alvo mantido em
  120 min (semana anterior abaixo de 80%).”
- Dias reduzidos: “A sua disponibilidade permite mais dias; esta semana usa o
  volume que cabe nela.”
- Motivo do bloqueio aparece em uma cláusula curta (conclusão, prontidão, carga,
  ACWR, deload, prova ou recuperação). Nenhum ID técnico na superfície comum.

### 3.5 Regra e catálogo

| ID | Texto | Evidências | Força |
|---|---|---|---|
| `R-PLAN-01` | Volume semanal cresce no máximo 6% (faixa 5–8%), só com semana anterior ≥ 80% concluída com evidência, prontidão verde, carga conhecida e ACWR 0,8–1,3; a disponibilidade é o teto | SEILER, GABBETT; ADR 0038 | MODERATE |

- `R-PLAN-01` volta a ser emitida pelo gerador, como o `SPORTS_SCIENCE` sempre
  descreveu. Não é criada regra nova.
- “Dia sem disponibilidade explícita ⇒ descanso” deixa de citar `R-PLAN-01` e
  passa a citar `R-DATA-01` (disponibilidade ausente é dado ausente, não regra de
  progressão).
- `SPORTS_SCIENCE` §8.3/§8.7/§14 são reconciliados com o código.

---

## 4. Alternativas consideradas

1. **Aumentar 5–8% acima da disponibilidade.** Rejeitada: viola a rotina
   declarada e a adesão (já rejeitada no ADR 0010).
2. **Usar o volume planejado da semana anterior, não o concluído.** Rejeitada:
   um plano de 171 min com 50% de conclusão não prova 171 min de capacidade.
3. **Usar sempre o volume da semana anterior, sem janela de 4 semanas.**
   Rejeitada: uma semana atípica (viagem, prova, deload) zeraria a base e o app
   voltaria a saltar na semana seguinte.
4. **Reduzir o volume depois de uma semana fraca.** Rejeitada: punição sem
   evidência e sem ganho fisiológico; a dose do dia já se ajusta.
5. **Crescer sobre uma base de 2–4 semanas atrás.** Rejeitada: transformaria
   volume antigo em salto atual. Base antiga só mantém, nunca cresce.
6. **Escalar a partir de km executados nas atividades.** Rejeitada nesta fatia:
   exigiria cruzar atividades com sessões e tratar treino livre; minutos
   prescritos com evidência forte são o dado já auditável. Fica como evolução.

---

## 5. Matriz de testes

**Domínio**
- `VolumeProgression`: sem histórico → teto; base concluída → +6%; semana anterior
  < 80% → mantém; base antiga → mantém; deload/taper/recuperação → excluídos;
  plano parcial (`createdOn > weekStart`) → excluído; base acima da agenda →
  limitada ao teto; empate → mais recente.
- `WeeklyPlanGenerator`: base 120 min + agenda 210 → 127 min (não 171); agenda
  6×60 com base 120 → ≤ 127; sem histórico mantém o comportamento atual; semana
  vermelha/doente não cresce; deload/taper/prova/recuperação não crescem;
  `R-PLAN-01` e rationale com a base; determinismo.
- `PlanReplanner`: `inputsHash` muda com o histórico; revisão não aumenta volume
  futuro; semana atual e passado protegidos; mesmo histórico ⇒ mesmo candidato.

**App**
- `TodayViewModel` e `ReplanContextBuilder` passam as 4 semanas anteriores.
- `WorkoutRecommender` sem disponibilidade cita `R-DATA-01`, não `R-PLAN-01`.
- Catálogo cobre `R-PLAN-01` com o texto novo.

**Regressão**
- Nenhum golden muda: os estados de screenshot são estáticos e não têm histórico.
- Semana sem histórico continua idêntica à v2.

---

## 6. Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Volume crescer além da rotina | Teto de disponibilidade e +6% máximo |
| Semana fraca virar punição | Base é o maior volume concluído; nunca reduz por falha |
| Histórico antigo virar salto | Crescimento só sobre a semana imediatamente anterior |
| Plano persistido antigo mudar sozinho | Nada é reescrito; a próxima geração usa v3 |
| Replanejamento inflar a semana | Regra existente “não repor carga” continua valendo |
| Doc e código divergirem de novo | `R-PLAN-01` emitida, catálogo e `SPORTS_SCIENCE` atualizados e testados |

---

## 7. Fora de escopo

- Periodização em blocos (Base/Build/Peak) e FR-005 (qualidade para iniciante).
- Volume em km e uso de treino livre como base.
- FR-206 (longão por fração da prova) e N-03 (qualidade no taper).
- Migração, dependência nova e mudança de UI além do texto do rationale.

---

## 8. Consequências

- O plano deixa de ser estático: progride no máximo 6% enquanto a agenda permitir
  e mantém o volume quando os dados não sustentam aumento.
- Aumentar a disponibilidade não multiplica o volume da noite para o dia.
- `R-PLAN-01` passa a existir no código, no catálogo e na ciência com o mesmo
  significado; N-07 e FR-004 são fechados.
- `ENGINE_VERSION` → `weekly-plan-v3`; schema permanece **v17**, sem migração.
