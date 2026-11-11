# PLANO_TECNICO_CICLO_3_C3-06.md — Auto-conclusão da prova e referência da meta

**Versão:** 1.0 · **Data:** 11/11/2026
**Status:** Concluído em 11/11/2026 (decisões D1–D6 aprovadas; implementação e testes verdes)
**Escopo:** C3-06 do `PRD_ADAPTACAO_E_BRIEFING_v0.2.md`, complementando/revogando parcialmente o ADR 0030
**Referências:** [ADR 0032](adr/0032-race-autocompletion-and-target-projection.md), [ADR 0030](adr/0030-race-event-and-weekly-taper.md), [ADR 0024](adr/0024-completion-evidence.md), `SPORTS_SCIENCE.md` §8.10/§14

---

## 1. Objetivo e limites

Entregar duas mudanças aprovadas:

1. concluir automaticamente um evento `PLANNED` quando a corrida importada é
   compatível, com auditoria e correção;
2. derivar pace médio, faixa ±3% e passagens por km do tempo-alvo declarado, apenas
   como referência nos cards da prova.

**Fora do escopo:** previsão de tempo por VDOT/Riegel (D6-B, exige ADR próprio),
recuperação pós-prova (FR-207), limite de longão (FR-206), LLM, dependência nova e
persistência de valores derivados.

## 2. Decisões aprovadas

| ID | Decisão |
|---|---|
| D1 | Mesma data local + `RUNNING`/`TREADMILL` + não vinculada + `PLANNED` + distância entre **90% e 110%** |
| D2 | Mais de uma candidata elegível ⇒ não concluir; usar o fluxo explícito existente |
| D3 | Confirmação no card + `Não era a prova`; recusa persistida por `activityId` |
| D4 | `completionSource` (`USER` × `AUTO_IMPORT`) com migração Room `15→16` |
| D5 | Faixa de pace **±3%**, arredondada ao segundo |
| D6 | Apenas passagens aritméticas no ritmo da meta (D6-A) |

## 3. Contrato de domínio

### 3.1 `RaceAutoCompletionRule`

Entrada pura:

```kotlin
data class RaceAutoCompletionInput(
    val event: RaceEvent,
    val activityId: String,
    val activitiesOnDate: List<ActivitySummary>,
    val zoneId: ZoneId,
    val sessionLinkedActivityIds: Set<String>,
    val raceLinkedActivityIds: Set<String>,
    val declinedActivityIds: Set<String>,
)
```

Decisão: `Eligible(eventId, activityId)` ou `Rejected(reason)`. Rejeições:
`EVENT_NOT_PLANNED`, `ACTIVITY_NOT_FOUND`, `WRONG_DATE`, `WRONG_SPORT`,
`ACTIVITY_ALREADY_LINKED_TO_SESSION`, `ACTIVITY_ALREADY_LINKED_TO_RACE`,
`PREVIOUSLY_DECLINED`, `DISTANCE_TOO_SHORT`, `DISTANCE_TOO_LONG`,
`MULTIPLE_CANDIDATES`.

Ordem: estado → atividade/data/esporte → vínculos → recusa → banda → uma única
candidata. A data local usa o fuso do perfil (`TimeProvider.zoneId`).

### 3.2 `RaceTargetProjection`

```kotlin
data class RaceTargetProjection(
    val targetPaceSecPerKm: Int,
    val paceRangeSecPerKm: IntRange,
    val splits: List<RaceSplitProjection>,
    val finishSec: Int,
    val confidence: DataConfidence,
    val ruleIds: List<String>, // ["R-RACE-02"]
)
```

- `pace = round(targetTimeSec / (distance.meters / 1000))`.
- `faixa = pace - round(pace × 0,03) .. pace + round(pace × 0,03)`.
- `split(k) = round(targetTimeSec × k / km)`; a última passagem é a chegada exata.
- `null` sem `targetTimeSec` (ou `<= 0`).
- `confidence` descreve a aritmética/entrada declarada, não a chance de bater a meta.

## 4. Persistência — Room 16

`race_events` ganha:

```sql
ALTER TABLE `race_events` ADD COLUMN `completionSource` TEXT;
```

- nullable; `NULL` em eventos não concluídos e em conclusões anteriores ao C3-06
  (lidas como explícitas);
- `USER` para confirmação explícita, `AUTO_IMPORT` para inferência;
- nenhum backfill, nenhum evento reescrito;
- `complete(id, activityId, source = USER)`;
- `reopenAutoCompleted(id)` só aceita `COMPLETED` + `AUTO_IMPORT`, rejeita se já
  existe outro `PLANNED` (`activeKey` único) e limpa vínculo/origem/`resolvedAt`;
- export inclui `completionSource`; reset remove eventos e a recusa persistida.

## 5. Fluxo de importação

```text
ImportResult.Imported/Duplicate
  → AutoCompleteRaceFromActivity.evaluate(activityId)
      → lê atividade, evento PLANNED da data, atividades do dia, vínculos e recusas
      → RaceAutoCompletionRule.evaluate
      → RaceEventRepository.complete(..., AUTO_IMPORT)
  → OfferImportedRun.evaluate(activityId)   // continua sem oferecer treino no dia da prova
```

- `runCatching` no `StagedImportProcessor`: falha da auto-conclusão nunca vira falha
  de importação.
- Nenhum `SessionExecution` é criado; a corrida continua normal em volume/carga/zonas.
- Reimportar o mesmo arquivo: o evento já está `COMPLETED` → `NoPlannedEvent`; a
  recusa persistida bloqueia o mesmo `activityId` após um undo.

## 6. UI

- **Perfil:** `RaceEventCard` mostra a referência abaixo da meta e, no histórico,
  `Concluída pela corrida importada.` + `Não era a prova`.
- **Hoje:** `RaceDayCard` mostra a referência abaixo da meta e, quando
  `COMPLETED` + `AUTO_IMPORT`, a confirmação + `Não era a prova`.
- **Sheet:** `RaceTargetProjectionBlock` + diálogo `Passagens por km` (rolável,
  tabular, unidade em cada valor, 48 dp, TalkBack).
- **Fora:** prescrição, plano, briefing, troca e notificação de treino.
- Sem meta ⇒ bloco ausente; nenhum valor inventado.

## 7. Matriz de testes

**Domínio**

- Projeção: 5K 25:00 = 5:00/km; meia 1:45:00 e maratona 3:30:00 (pace 299 s/km);
  faixa; passagens; chegada; `null`; monotonicidade; `targetTimeSec` não altera
  `RacePlan`.
- Regra: limites 90%/110%, fora da banda, data, esporte, vínculo, recusa, evento
  terminal, múltiplas candidatas, fuso do perfil.
- Catálogo: `R-RACE-01/02` presentes e com título legível.

**Dados**

- Migração `15→16` adiciona a coluna e preserva eventos; schema 16 exportado.
- `complete` grava `USER`/`AUTO_IMPORT`; undo só em automático; undo com outra prova
  planejada rejeitado; expurgo zera o vínculo.
- Export inclui `completionSource`; reset remove eventos/recusas.
- Vínculo cruzado reverso: corrida de prova não conclui treino.

**App/UI**

- `AutoCompleteRaceFromActivity`: conclusão, rejeições, idempotência, treadmill,
  undo com recusa, conclusão explícita intocada.
- `StagedImportProcessor`: importação compatível auto-conclui sem treino concorrente.
- `RaceEventSectionTest`/`TodayRaceTest`: referência, sheet, ausência sem meta,
  confirmação e undo.
- Goldens: `profile-race-{dark,light}` e `today-race-{dark,light,narrow-dark}`.

## 8. Ordem de implementação

1. ADR 0032, plano, decisões e documentação coordenada.
2. Domínio: `RaceTargetProjection` + `RaceAutoCompletionRule` + testes.
3. Modelo/persistência: `completionSource`, Room 16, migração, repositório, export.
4. App: `AutoCompleteRaceFromActivity`, store, `StagedImportProcessor`, reset.
5. UI: bloco de referência, confirmação/undo, goldens e acessibilidade.
6. Documentação final, `PROGRESS`/`ROADMAP` e checklist manual.

Cada etapa termina com `./gradlew check :app:assembleDebug` e
`./gradlew :app:verifyRoborazziDebug` verdes.

## 9. Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Falso positivo de auto-conclusão | Data exata, esporte, banda 90–110%, uma única candidata, `PLANNED`, undo e recusa |
| Reimportação reabre a conclusão | `completionSource` + recusa por `activityId` + evento terminal |
| Undo com outra prova planejada | `activeKey` único e `ActiveEventExists` |
| Migração quebrar histórico | Coluna nullable, sem backfill; teste `15→16` |
| Arredondamento divergir do tempo | Pace é referência; passagens/chegada usam o tempo exato |
| Copy virar promessa | “Referência da meta, não prescrição nem garantia”; sem IDs na superfície |
| Projeção contaminar o motor | Objeto puro, sem persistência, só UI; teste de independência do plano |
| Lixeira/expurgo | FK `SET NULL`, vínculo suspenso, undo disponível |
| F4/pós-prova | Ramo de prova sem briefing (FR-404); FR-206/FR-207 seguem fora |
