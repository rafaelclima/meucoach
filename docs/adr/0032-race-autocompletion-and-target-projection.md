# ADR 0032 — Auto-conclusão da prova e projeção da meta

**Status:** Aceito em 2026-11-11 (decisões D1–D6 aprovadas pelo usuário 0); implementado em C3-06 na mesma data (regra, Room 16, undo, referência no Perfil/Hoje, testes e goldens verdes)
**Data:** 2026-11-11
**Escopo:** C3-06, complementa/revoga parcialmente o ADR 0030 e o `PRD_ADAPTACAO_E_BRIEFING_v0.2.md`
**Relacionados:** ADR 0023 (prescrição diária), ADR 0024 (conclusão/evidência), ADR 0029 (F4), ADR 0030 (evento/taper), ADR 0031 (fingerprint)
**Plano técnico:** [PLANO_TECNICO_CICLO_3_C3-06.md](PLANO_TECNICO_CICLO_3_C3-06.md)

---

## 1. Contexto

O ADR 0030 fechou o evento de prova com duas restrições explícitas:

- importar uma corrida nunca marca o evento como realizado automaticamente; o
  vínculo é sempre explícito (§3.1);
- `targetTimeSec` é meta declarada e “nunca vira pace, faixa ou previsão” (§3.5).

Depois da entrega C3-05, o usuário pediu duas mudanças de produto:

1. concluir a prova automaticamente quando a corrida importada for compatível;
2. mostrar pace, faixa e passagens derivados do tempo-alvo declarado.

As duas mudanças contrariam textos vigentes. Este ADR registra a decisão e a
revogação parcial, sem reescrever o histórico do ADR 0030.

## 2. Decisões aprovadas (D1–D6)

| ID | Decisão |
|---|---|
| **D1** | Auto-concluir apenas com atividade na **mesma data local**, esporte `RUNNING`/`TREADMILL`, não vinculada a treino/prova, evento `PLANNED` e distância entre **90% e 110%** da distância da prova. |
| **D2** | Se houver **mais de uma candidata elegível** na data, não auto-concluir; o card da prova oferece a escolha explícita já existente. |
| **D3** | Confirmação passiva no card da prova (Hoje/Perfil) e ação **“Não era a prova”**, que volta o evento para `PLANNED` e desvincula a atividade. A recusa é persistida para o mesmo `activityId` não auto-concluir de novo. |
| **D4** | Persistir `completionSource` (`USER` × `AUTO_IMPORT`) em `race_events`, com migração Room `15→16`, export e testes. |
| **D5** | Faixa de pace = pace médio da meta **±3%**, arredondada ao segundo, rotulada “faixa de referência, não prescrição”. |
| **D6** | Fase 1 entrega **apenas passagens por km no ritmo da meta** (aritmética declarada). Previsão por VDOT/Riegel (D6-B) fica para um ADR próprio. |

## 3. Regras da auto-conclusão

Uma corrida só conclui a prova quando **todas** as condições valem:

1. o evento está `PLANNED`;
2. a data local da atividade (fuso do perfil) é igual a `event.targetDate`;
3. o esporte é `RUNNING` ou `TREADMILL`;
4. a atividade existe, não está na lixeira e não está vinculada a
   `session_executions` nem a outra `race_events`;
5. `0,90 × distância da prova ≤ distância da atividade ≤ 1,10 × distância da prova`;
6. existe **exatamente uma** candidata elegível na data;
7. a atividade não foi recusada por um “Não era a prova” anterior.

Consequências:

- `CANCELLED`, `NO_SHOW` e `COMPLETED` nunca são auto-concluídos;
- reimportar o mesmo arquivo não cria uma segunda conclusão; a transação existente
  rejeita evento já encerrado e o caso de uso trata como “nada a fazer”;
- não nasce `SessionExecution`: o vínculo é apenas `race_events.activityId`;
- a corrida vinculada continua uma `Activity` normal em volume, carga e zonas; o
  evento não adiciona carga;
- lixeira/restauração/expurgo preservam o comportamento atual (`SET NULL` e vínculo
  suspenso);
- a conclusão automática é **idempotente** e auditável por `completionSource`.

O caso de uso é do app (`AutoCompleteRaceFromActivity`), mas a regra é pura em
`core:domain` (`RaceAutoCompletionRule`). A UI não decide elegibilidade.

### 3.1 Undo e recusa

`reopenAutoCompleted` só aceita evento `COMPLETED` com `completionSource =
AUTO_IMPORT`. Ele volta para `PLANNED`, zera `activityId`, `resolvedAt` e
`completionSource`, e reocupa o `activeKey`. Se já existir outra prova `PLANNED`,
a operação é rejeitada (o índice único continua sendo a autoridade).

O `RaceAutoCompletionStore` (SharedPreferences, como o `ImportedRunPromptStore`)
guarda os `activityId` recusados. A recusa não expira; o reset de dados a limpa.
O fluxo explícito “Concluir prova” continua disponível e não é bloqueado pela
recusa.

### 3.2 Notificação

A notificação de importação continua com o texto atual e abre a atividade. A
confirmação da auto-conclusão é passiva no card da prova (Hoje ou Perfil), que é
a superfície de gestão do evento. Não há uma segunda notificação para evitar
ruído e duplicidade de mensagens.

## 4. Projeção da meta

### 4.1 Cálculo

`RaceTargetProjector` é puro e não persiste nada:

```text
pace = round(targetTimeSec / (distance.meters / 1000))
faixa = pace - round(pace × 0,03) .. pace + round(pace × 0,03)
split(k) = round(targetTimeSec × k / (distance.meters / 1000))
chegada = targetTimeSec
```

- Sem `targetTimeSec`, o resultado é `null`; nenhum alvo é inventado.
- A última passagem é sempre a chegada exata; um último trecho fracionário
  (meia/maratona) é rotulado “Chegada”, não “km 22”.
- A faixa é convenção de produto, não prescrição nem garantia.
- `confidence` descreve a entrada declarada e a aritmética, nunca a chance de o
  atleta bater a meta.

### 4.2 Limites de escopo

A projeção é **informativa** e não entra em:

- `DailyPrescription`;
- `PlannedSession`;
- `WeeklyPlanGenerator`;
- `PlanReplanner`;
- `SessionSwapper`;
- briefing ou alvos de treino.

Ela aparece apenas no card “Prova” do Perfil e no card de prova do Hoje. Testes
de independência provam que o plano e a prescrição são idênticos com e sem
`targetTimeSec`.

## 5. Revogação parcial de decisões anteriores

| Documento | Texto anterior | Decisão C3-06 |
|---|---|---|
| ADR 0030 §3.1 | “Importar atividade nunca marca o evento como realizado automaticamente” | Revogado **apenas** para a regra D1/D2; o vínculo explícito continua sendo o caminho padrão e o undo existe. |
| ADR 0030 §3.5 | “`targetTimeSec` … sem conversão em pace, intervalo, previsão ou promessa” | Revogado **apenas** para a referência aritmética da meta; continua proibido virar prescrição, alvo de treino ou promessa. |
| PRD FR-208 | “mudança de tempo-alvo … sem pace/previsão” | Revogado parcialmente: a meta pode gerar referência aritmética, mas não entra no motor. |
| PRD FR-210 | “transição explícita e vínculo opcional” | Complementado: a transição `PLANNED → COMPLETED` pode ser inferida pela regra D1 e corrigida por undo auditável. |
| PRD §13 | “previsão/faixa de pace fora da sequência” | Revogado para a referência aritmética; VDOT/Riegel continua fora até ADR próprio. |
| `COMMUNICATION.md` §6.9 | “Nunca deriva pace, faixa ou previsão” | Atualizado para “não vira pace prescrito, alvo de execução ou garantia; pode aparecer como referência da meta”. |
| `RaceEvent` KDoc | “nunca vira pace, faixa ou previsão” | Atualizado com a mesma distinção. |

**Não revogado:** FR-206 (limite de longão), FR-207 (recuperação pós-prova),
`COMPLETED` sem atividade, ausência de recuperação automática, evento no dia como
autoridade única e F4/ADR 0029 (o ramo de prova continua sem briefing).

## 6. Catálogo e evidência

| ID | Texto | Evidência | Confiança |
|---|---|---|---|
| `R-RACE-01` | Auto-conclusão por corrida compatível na mesma data, com banda de distância e undo | Este ADR; convenção de produto | `LIMITED` — não é alegação fisiológica |
| `R-RACE-02` | Pace, faixa e passagens derivados aritmeticamente da meta declarada | Este ADR; aritmética declarada | `LIMITED` — referência, não prescrição |
| `R-RACE-03` | Previsão por VDOT/Riegel (fora desta fase) | `EV-DANIELS-2014`, `EV-RIEGEL-1981` | `LIMITED` — só com ADR próprio e calibração explícita |

`R-RACE-03` não é implementado nesta fase. Riegel não é garantia; qualquer texto
futuro deve dizer “referência” e “não é promessa”.

## 7. Alternativas consideradas

1. **Não concluir automaticamente; só oferecer vínculo.** Rejeitada pelo usuário:
   o pedido é automático, com correção explícita.
2. **Banda ≥95% sem teto.** Rejeitada: mais falsos negativos de GPS e um longão
   maior poderia concluir uma prova curta.
3. **Escolher automaticamente entre múltiplas corridas.** Rejeitada: aumenta o
   risco de vincular a corrida errada; o fluxo explícito já existe.
4. **Sem `completionSource`.** Rejeitada: não haveria como distinguir inferência
   de ação explícita nem limitar o undo com segurança.
5. **Faixa fixa em segundos.** Rejeitada: não escala entre 5K e maratona.
6. **Previsão por VDOT/Riegel junto com as passagens.** Rejeitada nesta fase:
   exige modelo, janela de calibração, confiança e ausência explícita.

## 8. Consequências e riscos

**Positivas:** fecha o ciclo da prova sem exigir ação manual; a inferência é
auditável, corrigível e idempotente; a meta ganha uma referência útil sem
contaminar o motor; a distinção “meta × prescrição” fica explícita na copy.

**Riscos e mitigação:**

| Risco | Mitigação |
|---|---|
| Falso positivo de auto-conclusão | Data exata, esporte, banda 90–110%, evento `PLANNED`, uma única candidata, undo e recusa persistida |
| Reimportação reabre a conclusão | `completionSource` + recusa por `activityId` + transação rejeita evento encerrado |
| Undo com outra prova planejada | `activeKey` único; reopen rejeitado com mensagem clara |
| Arredondamento do pace divergir do tempo | Pace é referência arredondada; as passagens e a chegada usam o tempo exato |
| Copy virar promessa | “Referência da meta”, “não é prescrição”, “não é garantia”; sem `R-`/`EV-` na superfície comum |
| Projeção contaminar treino | Objeto puro e somente UI; testes de independência do plano/prescrição |
| Migração quebrar histórico | `15→16` só adiciona coluna nullable; legado `NULL` = conclusão explícita anterior |

## 9. Impactos

- **`core:model`:** `RaceCompletionSource` e `completionSource` em `RaceEvent`.
- **`core:domain`:** `RaceTargetProjection`/`RaceTargetProjector` e
  `RaceAutoCompletionRule`; catálogo `R-RACE-01/02`.
- **`core:data`:** coluna `completionSource`, migração `15→16`, `complete(...,
  source)`, `reopenAutoCompleted`, export; checagem cruzada reversa em
  `SessionExecutionRepository`.
- **`:app`:** `AutoCompleteRaceFromActivity`, `UndoRaceAutoCompletion`,
  `RaceAutoCompletionStore`, wiring no `StagedImportProcessor`, cards do Perfil e
  do Hoje, reset.
- **Sem dependência nova, sem LLM, sem persistência de valores derivados.**

## 10. Testes previstos

- Regra pura: cada rejeição, limites 90%/110%, múltiplas candidatas, recusa,
  vínculo, esporte, data, evento não `PLANNED`.
- Projeção: 5K 25:00 = 5:00/km; meia 1:45:00 e maratona 3:30:00 com
  arredondamento; faixa; passagens; chegada; `null` sem meta.
- Dados: migração `15→16`, `completionSource`, undo, vínculo cruzado, export/reset.
- App: importação `Imported`/`Duplicate`, idempotência, falha não derruba import,
  undo grava recusa, reimportação não reconclui.
- UI: confirmação, undo, TalkBack, claro/escuro, 200%, tela estreita, goldens.
- Independência: plano e prescrição iguais com e sem `targetTimeSec`.
