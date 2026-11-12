# ADR 0037 — Recuperação pós-prova (C3-07 / FR-207)

**Status:** Aceito em 2026-11-12 (decisões D1–D6 aprovadas pelo usuário 0) e implementado no mesmo dia (C3-07).
**Data:** 2026-11-12
**Escopo:** C3-07 do `PRD_ADAPTACAO_E_BRIEFING_v0.2.md`; FR-207; BUG-05 (segunda parte); N-03 (recuperação pós-prova).
**Relacionados:** ADR 0023 (plano × prescrição), ADR 0024 (evidência), ADR 0028 (replanejamento), ADR 0030 (evento/taper), ADR 0032 (auto-conclusão), ADR 0036 (previsão).

---

## 1. Contexto

### 1.1 O que já existe

O Ciclo 3 implementou o evento de prova (`RaceEvent`, Room 15), a autoridade diária
(`TodayDecision`), o taper por data, a auto-conclusão conservadora (Room 16) e a
referência aritmética da meta. A primeira metade do BUG-05 **já está resolvida**: o
gerador descarta os dias posteriores à prova dentro da semana civil dela
(`WeeklyPlanGenerator.kt:160-172`, ADR 0030 §3.4).

### 1.2 O que falta

Não existe nenhum estado de recuperação. Verificado no código em 2026-11-12:

1. `ReplanContextBuilder.kt:101-105` e `TodayViewModel.kt:188-193` só projetam
   `RaceStatus.PLANNED`. Ao concluir a prova, o evento **deixa de influenciar o
   planejamento**.
2. `RacePlan.allowsQuality` (`RacePlan.kt:45-50`) retorna `true` para qualquer data
   posterior à prova (`days < 0`).
3. A semana seguinte é gerada como treino normal: longão, duas qualidades e volume
   cheio voltam no dia seguinte à prova, limitados apenas pelos guardrails de carga.
4. Em 5k/10k o ACWR pode permanecer entre 0,8 e 1,3 e liberar `QUALITY` um ou dois
   dias após a prova. Em provas longas o ACWR talvez bloqueie por acaso — acidente de
   guardrail não é política de recuperação.

`SPORTS_SCIENCE.md` §8.10 já documenta a heurística (“1 dia fácil por 3 km de prova,
mínimo 3 dias sem qualidade após 5k/10k”), mas o código não a implementa. Pelo
`AGENTS.md` §5, doc e código precisam convergir.

### 1.3 Por que agora

O gate do C3-07 era “saber se a prova foi realizada e qual referência sustenta a
janela”. O C3-06 entregou `RaceStatus.COMPLETED`, `completionSource` e undo auditável.
Resta apenas a política aprovada — que é o objeto deste ADR.

---

## 2. Restrições

1. **Não reescrever histórico**: `DONE`/`CLOSED`, hoje e passado continuam intocados
   (ADR 0028/0024).
2. **Não criar `WorkoutType.RECOVERY` nem `RACE`**: a recuperação é um estado do
   calendário aplicado ao plano e à prescrição, não um treino novo.
3. **Sem migração**: `RaceEvent` já carrega `targetDate` e `resolvedAt`; nenhum campo
   novo é necessário. Se uma decisão exigir persistir checkpoint, isso vira gate
   próprio e migração 17→18.
4. **Sem alegação científica nova**: a janela é heurística de produto, classificada
   `LIMITED`, como `R-PRESC-04` e o cutoff do `R-PLAN-10`.
5. **Sem culpa**: recuperação não vira nota, streak ou cobrança.
6. **Prescrição continua autoridade do dia** (ADR 0023): o plano é intenção.

---

## 3. Decisão

### 3.1 Modelo puro

Novo objeto em `core/domain/prescription` (sem Android, sem persistência):

```kotlin
data class CompletedRace(val eventId: String, val distance: RaceDistance, val raceDate: LocalDate)

data class RecoveryWindow(val race: CompletedRace) {
    /** Dias corridos após a prova em que a janela está ativa (1..N). */
    val totalDays: Int get() = when (race.distance) {
        RaceDistance.FIVE_K -> 2
        RaceDistance.TEN_K -> 3
        RaceDistance.HALF_MARATHON -> 5
        RaceDistance.MARATHON -> 10
    }

    fun isActive(date: LocalDate): Boolean = dayNumber(date) in 1..totalDays.toLong()
    fun allowsQuality(date: LocalDate): Boolean = !isActive(date)
    fun allowsLongRun(date: LocalDate): Boolean = !isActive(date)
    fun maxSessionMinutes(date: LocalDate): Int?   // ver tabela §3.2
    fun daysRemaining(date: LocalDate): Int
}
```

Regras:

- **Âncora:** `raceDate = RaceEvent.targetDate`, nunca `resolvedAt`. Uma auto-conclusão
  importada dois dias depois não desloca a recuperação; a janela simplesmente já
  começou.
- **Somente `COMPLETED` inicia janela.** `CANCELLED` e `NO_SHOW` não iniciam
  (ADR 0030 §3.1). `PLANNED` continua com o comportamento de taper atual.
- **Prova legada via `TrainingGoal`** não pode ser concluída, logo nunca gera janela.
  Documentado como limitação.
- **Dia da prova não entra na janela** (`0`): o dia é `Competition` e não tem
  prescrição.
- **Undo** da auto-conclusão (`reopenAutoCompleted`) remove a janela; nada é
  reescrito retroativamente.

### 3.2 Tabela aprovada (D1/D3)

Derivada de “1 dia fácil por 3 km, mínimo 3 dias sem qualidade”, mas **encurtada por
decisão explícita do usuário 0** em 2026-11-12: a janela ficou mais leve que o texto
anterior de `SPORTS_SCIENCE` §8.10. A justificativa é que a distância curta produz
menos dano muscular e que a prescrição do dia continua limitada pelos guardrails de
prontidão e ACWR depois da janela; a classificação segue `LIMITED`.

| Distância | Janela ativa | Sem `QUALITY` | Longão | Teto de duração por dia |
|---|---:|---:|---|---|
| 5 km | 2 dias | 2 | bloqueado | 30 min em toda a janela |
| 10 km | 3 dias | 3 | bloqueado | 30 min em toda a janela |
| Meia maratona | 5 dias | 5 | bloqueado | 30 min (dias 1–3); 45 min (dias 4–5) |
| Maratona | 10 dias | 10 | bloqueado | 30 min (dias 1–5); 60 min (dias 6–10) |

- Os tetos são **limites superiores**; a sessão original pode ser menor.
- A frequência (número de dias) é mantida: recuperação reduz duração, não elimina
  rotina. Isso evita dias vazios e mantém a adesão honesta (D2).
- Qualquer número desta tabela é convenção de produto. Mudança exige outro ADR.
- `SPORTS_SCIENCE` §8.10 foi reescrito para esta tabela; o texto antigo (“mínimo 3
  dias” para 5k) deixou de valer com a justificativa registrada aqui.

### 3.3 Onde a janela entra

| Camada | Mudança |
|---|---|
| `WeeklyPlanInput` / `ReplanInput` | novo campo `recovery: RecoveryWindow?`; o app resolve a partir dos eventos `COMPLETED` cuja janela intersecta a semana |
| `WeeklyPlanGenerator` | qualidade nunca é candidata em dia de recuperação; `LONG` vira `EASY` com teto; `EASY`/`QUALITY` elegíveis recebem o teto; rationale cita `R-RACE-04` |
| `PlanReplanner` | recebe a janela, passa ao gerador, valida a semana mesclada com “sem qualidade/longão em recuperação”, **não repõe** carga e sugere revisão quando a semana persistida ainda viola a janela (D6) |
| `DailyPrescriptionEngine` | defesa em profundidade: janela ativa + sessão `PLANNED` ⇒ `QUALITY`/`LONG` viram `EASY` (`DOWNGRADE`) e `EASY` é limitado ao teto (`REDUCE`); `DONE`/`CLOSED` intactos |
| `TodayViewModel` / `ReplanContextBuilder` | leem eventos `COMPLETED` e resolvem a janela vigente; nenhuma UI calcula |
| `SessionSwapper` | não pode mover sessão para dia de recuperação nem trazer para fora dela uma sessão limitada |
| `PlanCompletionCalculator` | sem mudança: dias de recuperação contam como sessões planejadas normais (D2) |

**Efeito em plano já persistido (D6):** se a semana foi gerada antes da conclusão da
prova e ainda tem qualidade/longão ou duração acima do teto dentro da janela, o
`PlanReplanner` **sugere** uma revisão (aceitar/recusar), com causa própria
`RACE_COMPLETED`. Nada é aplicado automaticamente (ADR 0028). Recusar/suprimir é
lembrado por **semana + evento** em `ReplanSuggestionStore`, para o aviso não voltar
todo dia durante a janela. Quando a recuperação termina no meio da semana, o plano
segue conservador até a próxima geração: o app não aumenta carga no meio da semana.

### 3.7 Sugestão de revisão por recuperação

- Novo `ReplanCause.RACE_COMPLETED` e `ReplanTrigger.RACE_COMPLETED`.
- A causa só existe quando a janela está ativa **e** alguma sessão futura `PLANNED`
  ainda viola a recuperação (tipo forte/longão ou duração acima do teto). Semana já
  compatível não gera aviso.
- `ReplanSuggestion` ganha `recoveryEventId: String?`, usado apenas para lembrar a
  recusa; não é persistido em Room.
- Copy: “Ajustar o resto da semana após a prova?”; corpo explica que só os próximos
  dias mudam, sem repor carga e sem mexer no que já passou.
- Sem migração: a decisão fica em SharedPreferences, como tutorial e prompt de
  importação.

### 3.4 Regra e catálogo

| ID | Texto | Evidência | Confiança |
|---|---|---|---|
| `R-RACE-04` | Após prova concluída, período sem treino forte e sem longão, com sessões curtas por distância | `ADR 0037`; heurística de produto derivada de `SPORTS_SCIENCE` §8.10 | **LIMITED** — convenção conservadora de produto, não consenso científico |

- Sem `evidenceId` novo: as meta-análises de taper (Bosquet/Mujika) não sustentam
  janela de recuperação e não serão citadas como prova. Precedente: `R-PRESC-04`.
- `SPORTS_SCIENCE.md` §8.10 passa a descrever exatamente a tabela implementada;
  hoje o texto promete algo que o código não faz.
- Copy em pt-BR sem IDs: “Recuperação após a prova · dia 2 de 7”, “Sessão curta para
  recuperar; sem treino forte nesta semana”.

### 3.5 Superfícies aprovadas (D4)

- **Hoje:** o card principal mostra a sessão limitada e uma linha de recuperação
  (dia N de M, distância). Nenhum CTA de treino forte. Briefing continua derivado da
  prescrição, sem números novos.
- **Plano da semana:** selo “Recuperação pós-prova” no cabeçalho; dias limitados
  mostram a duração real.
- **Perfil:** o card do evento concluído mostra “Recuperação até dd/mm”.
- **Notificação:** sem mudança; usa a prescrição vigente.

### 3.6 Versões de motor (técnico)

- `PrescriptionPolicy.ENGINE_VERSION` → `daily-prescription-v4`, forçando a
  reavaliação da prescrição de hoje com a regra nova (precedente do BUG-04).
- `WeeklyPlanGenerator.ENGINE_VERSION` → `weekly-plan-v2`: a semântica do gerador
  mudou (janela de recuperação). Não há migração; planos antigos preservam a versão
  com que foram gravados.

---

## 4. Alternativas consideradas

1. **Só bloquear qualidade, sem teto de duração.** Rejeitada: não reduz volume, que é
   a variável central da recuperação.
2. **Apagar todos os dias da janela (sem sessões).** Rejeitada: cria dias vazios por
   até duas semanas, some com a rotina e distorce a adesão; além de parecer que o app
   parou de funcionar.
3. **Inferir recuperação pelo calendário (`targetDate` no passado).** Rejeitada pelo
   ADR 0030 §4.5: pode iniciar sem prova realizada. Só `COMPLETED` conta.
4. **Recuperação proporcional ao tempo real da prova.** Rejeitada: exigiria tempo
   realizado confiável e uma fórmula sem evidência; distância é o dado estável.
5. **Novo `WorkoutType.RECOVERY`.** Rejeitada: muda troca, matcher, conclusão, alvos,
   prescrição e adesão; o estado é de calendário, não de treino.
6. **Replanejar automaticamente ao concluir a prova.** Rejeitada: viola ADR 0028
   (revisão é sugestão) e reescreveria uma semana já aceita.

---

## 5. Decisões aprovadas (2026-11-12)

| ID | Decisão | Escolha do usuário 0 |
|---|---|---|
| **D1** | Tamanho da janela por distância | **2/3/5/10 dias** (5k/10k/meia/maratona) — mais leve que a heurística anterior |
| **D2** | Dias da janela recebem sessão | **Sessões curtas e fáceis**, contando adesão normal |
| **D3** | Teto de duração | **30 min**, depois **45 min** (meia) / **60 min** (maratona) |
| **D4** | Superfícies | **Hoje, Plano e Perfil**; notificação sem mudança |
| **D5** | Regra/catálogo | **`R-RACE-04` LIMITED**, sem `evidenceId` novo |
| **D6** | Plano já persistido / undo | **Sugerir replanejamento** (aceitar/recusar), sem aplicar sozinho |

Decisões técnicas tomadas por padrão, confirmadas na aprovação: âncora em
`targetDate`; `CANCELLED`/`NO_SHOW` sem janela; prova legada sem janela; tetos como
limite superior; bump para `daily-prescription-v4` e `weekly-plan-v2`; nenhuma
migração.

---

## 6. Matriz de testes prevista

**Domínio**
- `RecoveryWindow`: janela por distância, borda (`+N` ativo, `+N+1` livre), prova no
  dia 0 sem janela, `daysRemaining`, tetos por faixa, data anterior à prova.
- `WeeklyPlanGenerator`: nenhuma `QUALITY`/`LONG` na janela; duração limitada; piso de
  20 min respeitado; fora da janela volta ao normal; disponibilidade preservada;
  rationale e `R-RACE-04`; determinismo.
- `PlanReplanner`: não repõe carga; hoje/passado/`DONE`/`CLOSED` protegidos;
  validação da semana mesclada; janela alterada muda o `inputsHash`; sugere revisão
  com `RACE_COMPLETED` quando a semana persistida viola a janela; nada a sugerir
  quando ela já está compatível; não sugere quando a janela já terminou.
- `DailyPrescriptionEngine`: `QUALITY`→`EASY` e `LONG`→`EASY` dentro da janela;
  `EASY` reduzido ao teto; `DONE`/`CLOSED` intactos; sem janela, comportamento atual;
  confiança e regras.
- `SessionSwapper`: rejeita dia de recuperação.

**App/UI**
- Hoje: linha de recuperação com dia N de M, sem ID técnico; cards de plano limitados.
- Plano: selo e duração real; Perfil: “Recuperação até dd/mm”.
- Goldens dark/light/estreito; TalkBack e fonte 200%.
- Regressões: prova `PLANNED` continua igual; `CANCELLED`/`NO_SHOW` não geram janela;
  undo remove a janela.

---

## 7. Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Janela virar alegação científica | `R-RACE-04` LIMITED, sem EV, copy explícita e teste de texto |
| Recuperação virar punição/streak | Sessões curtas contam adesão normal; sem nota ou cobrança |
| Plano antigo mostrar qualidade | Prescrição do dia é autoridade e reduz; plano não é reescrito |
| Undo deixar estado inconsistente | Janela é derivada do evento, não persistida; undo só volta o status |
| Duas fontes de verdade | Janela sempre derivada de `RaceEvent` `COMPLETED` |
| Zona/fuso errado | `targetDate` já é data local do perfil (ADR 0022) |

---

## 8. Fora de escopo

- FR-206 (limite de longão por fração da distância da prova).
- Retorno gradual por desempenho real (pace/FC da prova) em vez de distância.
- Reduzir a duração da `QUALITY` no taper (N-03, item separado).
- `WorkoutType.RECOVERY`, periodização/blocos e LLM.
- Migração, dependência nova e restauração de backup.

---

## 9. Consequências

- Fecha o BUG-05 e o C3-07: planejar taper → correr → concluir → recuperar.
- `SPORTS_SCIENCE.md`, `COMMUNICATION.md`, `ROADMAP.md`, `PROGRESS.md` e o catálogo
  passam a descrever o mesmo comportamento do código.
- Nenhuma migração; schema permanece **v17**.
- Toda a regra é determinística e testável no `core:domain`, sem UI calculando.
