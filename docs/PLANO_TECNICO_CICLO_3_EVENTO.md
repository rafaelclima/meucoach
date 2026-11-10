# PLANO_TECNICO_CICLO_3_EVENTO.md — Evento de prova, TodayDecision e migração

**Versão:** 1.0 · **Data:** 10/11/2026
**Status:** C3-G1 concluído (desenho técnico) e C3-05 concluído em 10/11/2026: `RaceEvent`, `TodayDecision`, `PlanningGoalResolver`, Room 15, repositório, export/reset, `RacePlan`, taper/cutoff por data, adesão, guarda de troca, wiring do app, notificação e UI de Perfil/Hoje/Plano implementados e testados.
**Escopo:** F2/C3-G1 do `PRD_ADAPTACAO_E_BRIEFING_v0.2.md`: contrato de `RaceEvent`, `TodayDecision`,
schema Room, vínculo com atividade, taper por data, adesão, notificação, export/reset e testes.
**Referências:** ADR 0030 (decisão de produto), ADR 0022 (tempo), ADR 0023 (plano × prescrição),
ADR 0024 (evidência), ADR 0028 (replanejamento), ADR 0031 (fingerprint), `SPORTS_SCIENCE.md` §8.6.

---

## 1. Objetivo e limites

Fechar os detalhes técnicos que o ADR 0030 deixou como gate, sem antecipar a implementação:

1. contrato puro de `RaceEvent` e `TodayDecision`;
2. tabela `race_events`, índices, FK e migração `14→15`;
3. política de backfill a partir de `TrainingGoal` sem inferir realização;
4. unicidade e comportamento do vínculo com atividade (lixeira/expurgo);
5. regras de taper/cutoff por data de cada sessão e o impacto em replanejamento/adesão;
6. notificação, UI e export/reset;
7. matriz de testes que prova cada decisão.

**Fora deste gate:** código de produção, migração aplicada, `RaceEvent` na UI, recuperação pós-prova
(FR-207), limite de longão por fração da prova (FR-206), `TodayDecision` de competição renderizada,
nova dependência ou mudança de stack.

---

## 2. Decisões técnicas fechadas

| Tema | Decisão | Justificativa |
|---|---|---|
| Local do modelo | `RaceDistance`, `RaceStatus` e `RaceEvent` em `core:model`; `TodayDecision` e regras em `core:domain` | PRD §8: modelo puro fora da regra; domínio depende do modelo |
| Estados | `PLANNED`, `COMPLETED`, `CANCELLED`, `NO_SHOW`; transições só a partir de `PLANNED` e sempre explícitas | ADR 0030 §3.1 |
| Evento ativo | No máximo um `PLANNED`; encerrados ficam no histórico | ADR 0030 §3.1 |
| Backfill | **Nenhum** evento é criado pela migração | `TrainingGoal` é objetivo declarado, não prova realizada; evita inferência e T4 falso |
| Fonte de planejamento | Evento `PLANNED` quando existir; senão projeção legada de `TrainingGoal`; depois de qualquer evento, `TrainingGoal` não volta a planejar | Projeção canônica do ADR 0031 e transição sem duas fontes |
| Autoridade do dia | `TodayDecision.Training(DailyPrescription)` ou `TodayDecision.Competition(RaceEvent)` | ADR 0030 §3.3 (opção A) |
| Evento no dia | Qualquer evento com `targetDate == today` vence; o estado é exibido e `CANCELLED`/`NO_SHOW` não iniciam recuperação | Evita duas instruções; não reescreve plano |
| Prescrição no dia da prova | Não é calculada nem persistida naquele dia; prescrições legadas permanecem intactas e ocultas | Não criar decisão de treino concorrente |
| Vínculo com atividade | `activityId` opcional, único, FK `ON DELETE SET NULL`; lixeira suspende a exibição, restaurar devolve, expurgo remove o vínculo | Consistente com `session_executions`/ADR 0024 |
| Taper | Calculado por `targetDate - session.date`, janela 14 dias (5K–meia) / 21 (maratona); sem `QUALITY` na semana civil da prova; cutoff 10/14 dias inclusivo | ADR 0030 §3.4 |
| Cutoff | Regra própria `R-PLAN-10`, classificação **LIMITED/convenção de produto** | Não apresentar 10/14 como consenso científico |
| Adesão | Sessões em datas de evento (qualquer estado) saem do denominador | A sessão sobreposta não é falha do atleta |
| Migração | Room `15`; `race_events` vazia após a migração | Sem backfill, sem reescrever passado |

---

## 3. Contrato de domínio

### 3.1 `core:model`

```kotlin
enum class RaceDistance(val meters: Double) {
    FIVE_K(5_000.0),
    TEN_K(10_000.0),
    HALF_MARATHON(21_097.5),
    MARATHON(42_195.0),
}

enum class RaceStatus { PLANNED, COMPLETED, CANCELLED, NO_SHOW }

data class RaceEvent(
    val id: String,
    val distance: RaceDistance,
    val targetDate: LocalDate,
    val targetTimeSec: Int? = null,
    val status: RaceStatus = RaceStatus.PLANNED,
    val activityId: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Preenchido quando o estado deixa de ser PLANNED. */
    val resolvedAt: Instant? = null,
) {
    init {
        require(targetTimeSec == null || targetTimeSec > 0)
        require((status == RaceStatus.PLANNED) == (resolvedAt == null))
        require(activityId == null || status == RaceStatus.COMPLETED)
    }
}
```

Regras:

- `targetDate` é data local no fuso do perfil; não carrega hora nem fuso.
- `targetTimeSec` é meta declarada. Não gera pace, faixa, previsão nem alvo de execução.
- Evento com data passada continua `PLANNED` até ação explícita; o modelo não valida “futuro”.
- `COMPLETED` pode existir sem atividade. `CANCELLED`/`NO_SHOW` nunca têm atividade.
- `RaceDistance` é a única allowlist de distância; `GoalType.CUSTOM`/`GENERAL_FITNESS` não viram evento.

### 3.2 Projeção de planejamento

`PlanningGoal` (ADR 0031) passa a ser resolvido por uma função pura:

```kotlin
fun planningGoal(events: List<RaceEvent>, legacyGoal: TrainingGoal?): PlanningGoal? =
    when {
        events.any { it.status == RaceStatus.PLANNED } ->
            events.last { it.status == RaceStatus.PLANNED }.toPlanningGoal()
        events.isNotEmpty() -> null          // histórico de evento assume o objetivo
        else -> PlanningGoal.from(legacyGoal) // compatibilidade legada
    }
```

- `RaceEvent.toPlanningGoal()` usa `GoalType` equivalente e `distance.meters`; o mesmo par
  distância/data produz o mesmo hash vindo de `TrainingGoal` ou de `RaceEvent`.
- Evento `PLANNED` tem precedência. Depois que existe qualquer evento, o `TrainingGoal` deixa de
  alimentar o plano; evita duas fontes de verdade.
- A migração não cria evento, então não há mudança de fingerprint no upgrade.
- Criar/editar distância ou data de evento é ação explícita do usuário e pode gerar T4; mudar
  `targetTimeSec` não gera T4. A implementação deve usar a causa `RACE_EVENT_CHANGED` (novo
  `ReplanCause`/gatilho no C3-05), não rotular como `PROFILE_CHANGED`.
- `CANCELLED`/`NO_SHOW` deixam a projeção nula. Cancelar não reescreve o plano automaticamente;
  a próxima avaliação/replanejamento pode sugerir o futuro com a causa de evento.

### 3.3 `TodayDecision`

```kotlin
// core:domain/today/TodayDecision.kt
sealed interface TodayDecision {
    data class Training(val prescription: DailyPrescription) : TodayDecision
    data class Competition(val event: RaceEvent) : TodayDecision
}

object TodayDecisionResolver {
    /** Retorna null apenas quando não há evento nem prescrição (perfil ausente). */
    fun resolve(date: LocalDate, event: RaceEvent?, prescription: DailyPrescription?): TodayDecision? {
        require(event == null || event.targetDate == date)
        return when {
            event != null -> TodayDecision.Competition(event)
            prescription != null -> TodayDecision.Training(prescription)
            else -> null
        }
    }
}
```

- Exatamente um ramo; nunca os dois.
- `Competition` vence mesmo com prescrição legada persistida. A prescrição não é apagada nem
  recalculada, apenas não é a decisão do dia.
- `CANCELLED`/`NO_SHOW` no dia continuam `Competition` (o evento do dia prevalece), mas não
  iniciam recuperação nem liberam treino automático.
- A UI não escolhe o ramo: recebe `TodayDecision` pronto e só renderiza.

---

## 4. Persistência — Room 15

### 4.1 Entidade

```kotlin
@Entity(
    tableName = "race_events",
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activityId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["targetDate"]),
        Index(value = ["activityId"], unique = true),
        Index(value = ["activeKey"], unique = true),
    ],
)
data class RaceEventEntity(
    @PrimaryKey val id: String,
    val distance: String,
    val targetDate: String,
    val targetTimeSec: Int?,
    val status: String,
    val activityId: String?,
    /** "ACTIVE" somente em PLANNED; null nos encerrados. */
    val activeKey: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val resolvedAt: Long?,
)
```

- `activeKey` materializa “no máximo um `PLANNED`” com índice único; SQLite trata `NULL` como
  distintos, permitindo vários encerrados. O valor é constante `"ACTIVE"`.
- `activityId` único impede duas provas na mesma atividade; a exclusão da atividade zera o vínculo
  sem apagar o evento.
- Unicidade **entre** `race_events` e `session_executions` não é expressável por índice SQLite;
  é garantida na transação de vínculo e testada nos dois sentidos.
- Datas em `TEXT` ISO (`LocalDate.toString()`), instantes em epoch millis, como no restante do banco.

### 4.2 DAO e repositório

`RaceEventDao`:

- `observeAll()`, `getAll()`;
- `getPlanned()`, `getByDate(date)`;
- `getBetween(start, end)` para adesão/notificação;
- `insert`, `update`, `deleteAll`.

`RaceEventRepository` (interface em `core:data`):

- `observeActive(): Flow<RaceEvent?>`;
- `getForDate(date): RaceEvent?`;
- `getDatesBetween(start, end): Set<LocalDate>`;
- `create(request)`, `update(request)`, `complete(eventId, activityId?, at)`,
  `cancel(eventId, at)`, `noShow(eventId, at)`.

Escritas sempre em `withTransaction`:

1. validar estado de origem (`PLANNED` para transições);
2. validar distância/date/time;
3. `create` rejeita se já existe `PLANNED` (`ACTIVE_EVENT_EXISTS`), salvo substituição explícita
   (cancelar o antigo + criar o novo na mesma transação);
4. `complete` valida atividade existente, não expurgada e não vinculada a outra prova/execução;
5. `update` só em `PLANNED`; distância/data podem gerar T4, tempo-alvo não.

### 4.3 Migração `14 → 15`

```sql
CREATE TABLE IF NOT EXISTS `race_events` (
  `id` TEXT NOT NULL,
  `distance` TEXT NOT NULL,
  `targetDate` TEXT NOT NULL,
  `targetTimeSec` INTEGER,
  `status` TEXT NOT NULL,
  `activityId` TEXT,
  `activeKey` TEXT,
  `createdAt` INTEGER NOT NULL,
  `updatedAt` INTEGER NOT NULL,
  `resolvedAt` INTEGER,
  PRIMARY KEY(`id`),
  FOREIGN KEY(`activityId`) REFERENCES `activities`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS `index_race_events_targetDate` ON `race_events` (`targetDate`);
CREATE UNIQUE INDEX IF NOT EXISTS `index_race_events_activityId` ON `race_events` (`activityId`);
CREATE UNIQUE INDEX IF NOT EXISTS `index_race_events_activeKey` ON `race_events` (`activeKey`);
```

- Tabela nasce vazia; nenhum `INSERT` de backfill.
- Planos, sessões, revisões, prescrições, execuções, perfil e metas existentes permanecem
  byte a byte.
- `15.json` exportado e migração testada com `MigrationTestHelper` a partir de um banco v14 com
  plano/sessão/revisão/execução/prescrição.

---

## 5. Atividade, lixeira e exclusão

- **Vínculo explícito:** só `CompleteRaceEvent` grava `activityId`. Importar ou concluir uma corrida
  nunca marca prova como realizada; no máximo a UI oferece “usar esta corrida”.
- **Lixeira:** a atividade continua com `deletedAt`; o vínculo permanece no banco. Consultas de
  exibição ignoram atividade na lixeira e mostram “vínculo suspenso”. Restaurar devolve o vínculo
  sem ação extra; isso replica o comportamento de `session_executions`.
- **Expurgo:** a FK `ON DELETE SET NULL` remove o vínculo e preserva o evento. Teste explícito.
- **Unicidade cruzada:** ao vincular uma atividade a um evento, rejeitar se ela já está em
  `session_executions`; ao concluir sessão com atividade, rejeitar se ela está em `race_events`.
  Não usar a mesma corrida como prova e treino.
- **Adesão:** a corrida vinculada continua uma `Activity` normal (volume/carga/zonas); o evento
  não adiciona carga.

---

## 6. Plano, taper e replanejamento

### 6.1 Entrada canônica

`WeeklyPlanInput` e `ReplanInput` recebem `race: RacePlan?`:

```kotlin
data class RacePlan(
    val eventId: String,
    val distance: RaceDistance,
    val targetDate: LocalDate,
) {
    val taperWindowDays: Int get() = if (distance == RaceDistance.MARATHON) 21 else 14
    val qualityCutoffDays: Int
        get() = when (distance) {
            RaceDistance.FIVE_K, RaceDistance.TEN_K -> 10
            RaceDistance.HALF_MARATHON, RaceDistance.MARATHON -> 14
        }
}
```

O app resolve `RacePlan` da mesma projeção do §3.2 (evento `PLANNED` ou meta legada), para não
existirem dois caminhos de taper. `RACE` continua ausente de `WorkoutType`.

### 6.2 Regras por sessão

Para cada sessão candidata, com `daysToRace = targetDate - session.date` em datas locais:

1. `session.date == targetDate` ⇒ **nenhuma sessão** é gerada/deslocada para o dia da prova.
2. Semana civil local da prova (segunda a domingo que contém `targetDate`) ⇒ nenhuma `QUALITY`.
3. `session.type == QUALITY` e `daysToRace <= qualityCutoffDays` ⇒ remover a `QUALITY` candidata,
   sem criar ativação ou substituta de outra intensidade. O dia pode ficar vazio; não repor carga.
4. `0 <= daysToRace <= taperWindowDays` ⇒ reduzir duração de EASY/LONG e de `QUALITY` que ainda
   esteja antes do cutoff. Fatores atuais: `0,55` na janela e `0,45` nos últimos 7 dias.
5. Pisos atuais (`MIN_QUALITY_MIN`, `MIN_EASY_MIN`) continuam valendo. Se o piso impedir redução,
   a sessão mantém o piso e o rationale não promete redução que não ocorreu.
6. Hoje, passado e futuras `DONE`/`CLOSED` não são tocados pelo ADR 0028.
7. Prova passada ou a menos de 10 dias: sem taper retroativo nem promessa de preparação completa;
   a decisão do dia continua conservadora.
8. Volume semanal é recalculado depois da remoção/redução; o rationale cita `R-PLAN-05` (volume) e
   `R-PLAN-10` (cutoff/semana da prova) separadamente.

### 6.3 Replanejamento e troca

- `PlanReplanner` recebe `raceDates` e `race`; sessões candidatas em data de evento são descartadas,
  nunca “repostas” em outro dia. A validação da semana mesclada inclui “nenhuma sessão em data de
  evento” e “nenhuma `QUALITY` na semana da prova”.
- `SessionSwapper`: rejeita troca que coloque qualquer sessão em data de evento ou traga a sessão
  de um dia de evento para outro dia.
- `SessionMatcher`: não muda; no dia do evento a sessão ordinária não é exibida. Se houver sessão
  legada sobreposta, a atividade importada não é oferecida como conclusão de treino naquele dia.
- `CompleteSession`: rejeita concluir sessão legada em data de evento (o caminho é
  `CompleteRaceEvent`), sem reescrever histórico.
- `WeeklyPlanGenerator.ENGINE_VERSION` passa a `weekly-plan-v2` quando o código for implementado;
  planos antigos preservam sua versão e não são reinterpretados.

### 6.4 Regra e classificação

Nova regra no catálogo:

| ID | Texto | Evidência | Confiança |
|---|---|---|---|
| `R-PLAN-10` | Sem treino forte na semana da prova e cutoff conservador antes dela | `EV-BOSQUET-2007`, `EV-MUJIKA-2003` apenas como princípio geral de taper; cutoff é convenção de produto | **LIMITED** |

`R-PLAN-05` continua cobrindo a redução de volume. A copy não deve citar 10/14 como consenso
científico. `SPORTS_SCIENCE.md` §8.6 e o catálogo recebem a classificação LIMITED na implementação.

### 6.5 Adesão

`PlanCompletionCalculator.calculate` ganha `excludedDates: Set<LocalDate> = emptySet()`:

- sessões cuja `date` está em `excludedDates` saem do numerador e do denominador;
- o app passa todas as datas de evento da janela de 8 semanas, de qualquer estado;
- plano legado com sessão no dia da prova deixa de contar como pendente/falha;
- `DONE`/`CLOSED` reais de outras datas permanecem inalterados.

---

## 7. Notificação, UI e export

### 7.1 Notificação

`ReminderWorker` lê o evento do dia antes do plano:

- evento no dia ⇒ `ReminderContent.race(event)` com “Prova hoje: {distância}”, data e deep link
  `meucoach://today`; sem copy de treino;
- sem evento ⇒ comportamento atual (planejado/prescrição);
- tocar abre o Hoje; o Hoje resolve `TodayDecision` com os dados vigentes antes de renderizar.

### 7.2 UI (C3-05)

- **Perfil:** card “Prova” com distância (4 opções), data, tempo-alvo opcional, estado e ações
  explícitas: criar/editar (PLANNED), concluir com atividade opcional, cancelar, não compareceu.
  Sem campo de pace/faixa/previsão.
- **Hoje:** ramo `Competition` mostra distância, data, estado e meta declarada, sem CTA de
  iniciar/trocar/concluir treino, sem briefing. Ações de conclusão/cancelamento/não comparecimento
  conforme o estado.
- **Plano da semana:** dia da prova marcado como evento; sem card de sessão ordinária; sessão
  legada sobreposta oculta.
- **Acessibilidade:** tokens do `core:designsystem`, 48 dp, AA, TalkBack, claro/escuro, 200% e tela
  estreita; goldens novos do card de evento e do Hoje no dia da prova.

### 7.3 Export e reset

- Export inclui `raceEvents` (todos os estados, `activityId` quando houver) antes de `activities`.
- Reset inclui `raceEventDao().deleteAll()` antes de apagar atividades; `UserDataDeletion` ganha
  `raceEvents` e `removedAnything` considera o novo campo.
- Sem import/restore de export neste ciclo.

---

## 8. Matriz de testes

**Domínio (`core:domain`)**

- `RaceEvent`: invariantes de estado/`resolvedAt`/`activityId`; distância allowlist; data passada
  continua PLANNED.
- `TodayDecisionResolver`: evento vence; treino quando não há evento; `null` sem ambos; evento em
  outra data é rejeitado; `CANCELLED`/`NO_SHOW` no dia não iniciam recuperação.
- `PlanningGoal`: mesma distância/data gera o mesmo hash vindo de `TrainingGoal` ou `RaceEvent`;
  `targetTimeSec` não altera; evento encerrado zera a projeção; sem evento usa legado.
- Taper: cada dia da semana, véspera, dia da prova, 10/14 inclusivo, semana parcial, virada
  domingo/segunda, fuso, maratona vs. 5K/10K/meia, `DONE`/`CLOSED` protegidos.
- Adesão: sessão em data de evento sai do denominador em todos os estados; demais dias intactos.
- Replanejamento: nenhuma sessão em data de evento; nenhuma `QUALITY` na semana; hoje/passado
  protegidos; sem reposição de carga.
- Swapper rejeita datas de evento; matcher/conclusão rejeitam sessão legada no dia.

**Dados (`core:data`)**

- Migração `14→15`: tabela/índices/FK criados, dados anteriores preservados, tabela vazia.
- `create`: um `PLANNED` por vez; substituição explícita; evento encerrado não bloqueia novo.
- Transições: `PLANNED→COMPLETED/CANCELLED/NO_SHOW`; transições inválidas rejeitadas; idempotência
  não permitida.
- Vínculo: atividade única, FK `SET NULL` no expurgo, lixeira/restauração, unicidade cruzada com
  `session_executions`.
- Export/reset incluem eventos; reset respeita ordem de FK.
- `saveWeek`/revisões preservam o engine version e não reinterpretam plano antigo.

**App/UI**

- Perfil: criar/editar/concluir/cancelar/não comparecer; distância/data; meta sem pace.
- Hoje: `Competition` suprime treino/briefing; `Training` permanece igual.
- Plano: marcador no dia; sessão sobreposta oculta; notificação abre Hoje e revalida.
- Estados loading/empty/error, claro/escuro, tela estreita, 200%, 48 dp, semântica/ordem.
- Goldens: card de evento, Hoje no dia da prova (dark/light) e Perfil com prova.

---

## 9. Ordem de implementação (C3-05)

1. `core:model` (`RaceDistance`, `RaceStatus`, `RaceEvent`) + testes.
2. `core:domain` (`TodayDecision`, projeção, taper, cutoff, adesão, swapper) + testes.
3. Room `15`, entidade/DAO/repositório, migração e export/reset + testes.
4. Casos de uso do app (`GetTodayDecision`, `CompleteRaceEvent`, `CancelRaceEvent`, etc.).
5. `WeeklyPlanGenerator`/`PlanReplanner`/notificação + regressões.
6. UI Perfil/Hoje/Plano + goldens + checklist manual.
7. Documentação (`PROGRESS`, `ROADMAP`, `ARCHITECTURE`, `SPORTS_SCIENCE`, `COMMUNICATION`, `TESTING`).

Cada etapa termina com `./gradlew check :app:assembleDebug`, `verifyRoborazziDebug`, Kover/spotless
verdes e nenhum dado real no diff. A recuperação pós-prova (FR-207) e o limite de longão (FR-206)
continuam fora.

---

## 10. Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Duas fontes de verdade (`TrainingGoal` × `RaceEvent`) | Projeção única, evento PLANNED com precedência e fim do fallback após o primeiro evento |
| T4 falso na troca de fonte | Migração sem backfill; mesma projeção distância/data; `targetTimeSec` fora do fingerprint |
| Duas instruções no Hoje | `TodayDecision` discriminada; `Competition` suprime treino/briefing |
| Sessão legada no dia da prova | Decisão do dia vence, adesão exclui a data e conclusão de treino é rejeitada |
| Vínculo órfão/duplicado | FK `SET NULL`, índices únicos e validação cruzada transacional |
| Cutoff virar alegação científica | `R-PLAN-10` LIMITED e copy explícita; 10/14 como convenção de produto |
| Histórico reescrito | Nenhuma transição automática; passado/`DONE`/`CLOSED` protegidos |
