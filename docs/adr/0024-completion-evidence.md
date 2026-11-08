# ADR 0024 — Conclusão de sessão com evidência e estados próprios

**Status:** aceito
**Data:** 2026-11-06
**Responsável:** planejamento com Rafael (usuário 0)
**Implementação:** C1-04 para `ACTIVITY`/`MANUAL` e vínculo; contrato de `GUIDED` preparado agora, implementação persistente no ciclo 3.

---

## 1. Contexto

Hoje, `DONE` é um clique:

- `TodayViewModel.completeSession()` chama `TrainingPlanRepository.updateStatus(..., DONE)`;
- `TodayScreen` oferece “Concluir” no cartão do plano e “Concluir treino” no diálogo da sessão guiada;
- não existe `SessionExecution`, `activityId`, `executionId` nem evidência;
- `PlanCompletionCalculator` conta qualquer `DONE` como concluído;
- a sessão guiada é um cronômetro em memória, sem persistência e sem recuperação;
- não há vínculo entre uma corrida importada e a sessão planejada;
- em dia de descanso/doença, o botão de conclusão pode continuar disponível quando existe sessão planejada e a recomendação é `REST`.

O PRD e a revisão identificam isso como BUG-02 e N-10: a adesão é auto-declarada, a progressão pode ser liberada por clique e o passado `DONE` não tem evidência.

A decisão de produto aprovada define três níveis de evidência (`ACTIVITY`, `GUIDED`, `MANUAL`), exige que adesão e execução comprovada sejam indicadores distintos, e determina que descanso, doença e treino não realizado tenham estados próprios. Uma indicação de doença não pode impedir o registro fiel de uma atividade que aconteceu, mas também não pode estimular a realização dela.

## 2. Problema

Sem evidência e sem estados próprios:

1. o usuário pode “concluir” um treino que não fez;
2. o coach não consegue comparar planejado × executado;
3. a progressão e a elegibilidade de qualidade usam uma conclusão sem confiança;
4. um dia de descanso ou doença fica pendente para sempre e distorce a adesão;
5. uma corrida real feita durante uma doença não tem onde ser registrada sem parecer que o app aprovou o treino;
6. a migração de dados antigos pode transformar cliques em “verdade” silenciosamente;
7. uma mesma atividade pode ser vinculada a duas sessões, ou duas atividades à mesma sessão.

## 3. Decisão

### 3.1 Níveis de evidência

`completionEvidence` passa a ser um campo explícito:

| Nível | O que significa | Confiança | Entra em volume/carga/zonas? |
|---|---|---|---|
| `ACTIVITY` | Atividade real registrada/importada e vinculada à sessão, com confirmação do usuário. | Alta | Sim, pelos samples da atividade. |
| `GUIDED` | Execução iniciada e encerrada pelo app, com persistência e recuperação (duração, pausas, início/fim). | Média-alta para duração; baixa para distância. | Duração/tempo; distância só se houver fonte confiável. |
| `MANUAL` | Declaração explícita “fiz sem registro”, com duração > 0 e RPE 1–10. | Baixa | **Não** cria atividade, distância, pace, FC, zonas ou carga fisiológica. |
| `LEGACY_UNVERIFIED` | `DONE` histórico migrado, sem evidência. | Não verificada | Não; aparece separado e não conta para progressão. |

Regras:

1. **`DONE` só existe com uma das três evidências novas.** Não há clique sem evidência.
2. `ACTIVITY` exige vínculo explícito; o app pode sugerir, mas nunca vincula automaticamente.
3. `GUIDED` exige execução persistida e recuperável. O contrato é definido agora; a implementação completa da sessão guiada fica no ciclo 3. Até lá, o app não produz `GUIDED` novo.
4. `MANUAL` exige duração e RPE; não pode gerar métricas fisiológicas fictícias. A UI diz claramente que a corrida **não** entrou no histórico/volume.
5. `LEGACY_UNVERIFIED` é somente leitura e serve para não reescrever o passado como verdade.
6. **Correção explícita de `MANUAL`:** um `DONE` + `MANUAL` pode ser substituído por uma atividade real do mesmo dia, com confirmação do usuário. A execução manual é atualizada no lugar (`source = ACTIVITY`, `activityId`, duração/distância reais; RPE descartado) e a sessão troca a evidência para `ACTIVITY`, mantendo data e vínculo 1:1. Somente `MANUAL` é corrigível: `ACTIVITY` já é verificado, `GUIDED` tem execução própria e `LEGACY_UNVERIFIED` não é promovido.

### 3.2 Estados próprios de descanso, doença e não realizado

O status da sessão planejada deixa de ser apenas `PLANNED`/`DONE`/`SKIPPED`. A decisão é separar **resultado da sessão** de **motivo de encerramento**:

```kotlin
enum class SessionStatus { PLANNED, DONE, CLOSED }

enum class SessionClosureReason {
    REST_PRESCRIBED,
    ILLNESS_OR_INJURY,
    USER_SKIPPED,
    MISSED,
    PLAN_REPLACED,
}
```

- `REST_PRESCRIBED`: a prescrição do dia foi `REST` porque o plano/descanso assim definiu. Não é falha de adesão.
- `ILLNESS_OR_INJURY`: a sessão foi encerrada porque o usuário sinalizou doença/lesão. Não é falha de adesão e não estimula treino.
- `USER_SKIPPED`: o usuário pulou explicitamente, com motivo opcional; conta como não realizada.
- `MISSED`: o dia passou sem ação; conta como não realizada, sem notificação passivo-agressiva.
- `PLAN_REPLACED`: a sessão foi substituída por replanejamento; não conta como falha.
- `CLOSED` com qualquer motivo não é `DONE`.

> Compatibilidade: `DONE` atual continua existindo como status de conclusão com evidência. `SKIPPED` atual é migrado para `CLOSED` com `USER_SKIPPED` (ou `MISSED` quando o dia já passou e não houve ação). Nenhum `DONE` antigo é convertido em `ACTIVITY`; ele vira `LEGACY_UNVERIFIED`.

### 3.3 Adesão e execução comprovada são indicadores distintos

- **Adesão ao planejamento:** numerador = sessões `DONE` com qualquer evidência; denominador = sessões treináveis prescritas a partir da criação do plano, excluindo `REST_PRESCRIBED`, `ILLNESS_OR_INJURY` e `PLAN_REPLACED`. `MANUAL` conta para adesão, com confiança baixa.
- **Execução comprovada:** contagem separada por nível (`ACTIVITY`, `GUIDED`, `MANUAL`, `LEGACY_UNVERIFIED`). A UI e a progressão olham primeiro para `ACTIVITY`/`GUIDED`.
- **Progressão:** apenas `ACTIVITY` e `GUIDED` contam como evidência forte. `MANUAL` não libera progressão de volume/qualidade neste ciclo. `LEGACY_UNVERIFIED` não conta.
- Não existe um número único de “% feito” que esconda a diferença; a tela mostra “feito” e “feito com evidência”.

### 3.4 Doença, descanso e o direito de registrar a verdade

1. Doença/lesão ou prontidão vermelha ⇒ prescrição `REST`; a sessão planejada é encerrada como `CLOSED/ILLNESS_OR_INJURY` ou `CLOSED/REST_PRESCRIBED`, não fica pendente.
2. O app não oferece “Iniciar” nem “Concluir” para a sessão bloqueada.
3. Se o usuário correr mesmo assim e importar a corrida, ela é registrada fielmente como **treino livre**, sem vínculo automático com a sessão bloqueada.
4. Se o usuário fizer questão de vincular (override explícito), a evidência é `ACTIVITY` com `overrideReason = ILLNESS_OR_INJURY`; a UI rotula “registrado durante doença”, não conta para progressão e não transforma o dia em cumprimento do plano.
5. O app nunca sugere, parabeniza ou estimula treino em dia de doença; apenas registra o que aconteceu.

### 3.5 Vínculo atividade ↔ sessão

- Chave de compatibilidade: mesma data local no fuso do perfil, esporte corrida/esteira, duração e distância dentro de tolerância explícita.
- Tolerâncias iniciais (configuráveis e testadas; não são regra fisiológica):
  - duração: ±20% ou ±10 min, o que for maior;
  - distância: ±15% ou ±1 km, o que for maior;
  - horário: mesma data local; atividades que cruzam meia-noite usam o dia de início.
- O match é sempre **sugestão**; o usuário confirma.
- Restrições de unicidade:
  - uma atividade só pode ser vinculada a uma sessão;
  - uma sessão só pode ter uma atividade;
  - o vínculo é transacional e concorrente-seguro (índice único + transação).
- Rejeitar a sugestão mantém a sessão `PLANNED` e a corrida como treino livre.
- Sessão de ontem da mesma semana pode ser concluída com aviso; sessão futura é bloqueada.

### 3.6 Persistência

- `session_executions`: `id`, `plannedSessionId?`, `activityId?`, `startedAt`, `endedAt`, `pauses`, `durationSec`, `distanceM?`, `rpe?`, `source`, `createdAt`.
- `planned_sessions`: `completionEvidence`, `executionId?`, `activityId?`, `closureReason?`, `closedAt?`.
- O status `DONE` é derivado da existência de execução/atividade com evidência; não é setado por clique.
- Migração `11 → 12` (ou a migração seguinte disponível) preserva status, `completedAt` e planos; `DONE` antigo vira `LEGACY_UNVERIFIED`; `SKIPPED` vira `CLOSED/USER_SKIPPED`.

## 4. Alternativas consideradas

1. **Só execução guiada no app.**
   - Evidência forte de tempo, mas exclui quem usa relógio/esteira e exige uma sessão guiada que ainda não existe.
2. **Só atividade vinculada.**
   - Evidência forte de corrida, mas pune quem esqueceu o dispositivo ou fez esteira sem arquivo.
3. **Só confirmação manual/clique.**
   - Simples, mas mantém a adesão como opinião e não resolve BUG-02.
4. **Níveis de evidência (decisão adotada).**
   - Cobre relógio, esteira, esquecimento e doença; expõe a confiança; exige modelo e UI.
5. **Tratar doença como `SKIPPED`.**
   - Mistura “não treinei porque estava doente” com “pulei o treino”; distorce adesão e estimula culpa.
6. **Transformar todo `DONE` antigo em `ACTIVITY`/verdade.**
   - Reescreve o passado sem base; a migração deve ser explícita e conservadora.
7. **Criar uma atividade manual completa neste ciclo.**
   - Exige modelo de atividade, samples/distância e impacto em carga; fica para o ciclo 2. `MANUAL` aqui é execução, não atividade.

## 5. Justificativa

O produto precisa funcionar para três realidades:

- quem usa relógio e importa arquivo;
- quem faz esteira sem arquivo;
- quem esqueceu o dispositivo.

Recusar o manual pune o usuário; aceitar o clique pune o coach. Níveis de evidência são a única solução que preserva os dois lados e torna a confiança explícita.

Separar adesão de execução comprovada impede que uma declaração de baixa confiança libere progressão. E separar `REST`/doença de “pulado” impede que o app trate descanso prescrito como falha, além de permitir registrar uma corrida real sem glamourizar o treino durante a doença.

## 6. Consequências

### 6.1 Positivas

- `DONE` deixa de ser opinião; toda conclusão tem evidência.
- A adesão fica honesta e não pune descanso/doença.
- A progressão usa evidência forte, não clique.
- O vínculo atividade ↔ sessão fecha o loop planejado × executado.
- Dados antigos são preservados como não verificados, sem reescrever o passado.
- A UI pode explicar “feito” vs. “feito com evidência” e “sem registro”.

### 6.2 Negativas e custos

- Schema novo e migração; risco alto de regressão em adesão e planos antigos.
- UI nova de conclusão (vincular atividade, “fiz sem registro”, bloqueio em descanso/doença).
- `MANUAL` não alimenta volume/carga; a UI precisa dizer isso sem parecer punição.
- A sessão guiada persistente não entra neste ciclo; `GUIDED` fica como contrato e pode gerar expectativa.
- Duplicidade/vínculo exigem índices e transações; mais testes de concorrência.
- Usuários com muitos `DONE` antigos verão “não verificado”; a copy precisa ser cuidadosa.

### 6.3 Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Migração transformar clique em verdade | Backfill explícito para `LEGACY_UNVERIFIED`; teste com banco v11 sanitizado |
| Perder status/`completedAt` | Teste de migração campo a campo; nenhum `fallbackToDestructiveMigration` |
| Duas atividades na mesma sessão | Índice único + transação + teste concorrente |
| Usuário não entender por que o botão sumiu | Copy “descanso prescrito”/“doença” + link para registrar treino livre |
| `MANUAL` virar métrica fisiológica | Modelo separado de execução; nenhum campo de distância/pace/FC obrigatório; teste de export |
| Atividade na lixeira manter evidência | ADR 0025: vínculo suspenso enquanto na lixeira; expurgo remove vínculo |
| Sessão guiada prometida e não entregue | Status `GUIDED` só é produzido quando a implementação existir; contrato documentado, sem botão falso |

## 7. Impactos arquiteturais

- **`core:model`:** `CompletionEvidence`, `SessionClosureReason`, `SessionExecution`, `ExecutionSource`.
- **`core:domain`:** `SessionMatcher` puro; regras de conclusão/bloqueio; `PlanCompletionCalculator` com verificados/não verificados; `WorkoutType.REST` e doença como estados de encerramento.
- **`core:data`:** `SessionExecutionEntity`, novas colunas em `PlannedSessionEntity`, DAO, `SessionExecutionRepository`; migração; consultas de adesão por evidência; vínculo transacional.
- **`:app/application`:** `CompleteSession`, `LinkActivityToSession`, `CloseSession` com regras de data/estado.
- **`:app/ui`:** fluxo de conclusão no Hoje, sugestão no detalhe da atividade, formulário manual com duração/RPE, bloqueio em `REST`/doença, rótulos de evidência no plano e em Tendências.
- **Explicabilidade:** novos IDs (`R-EVID-01`, `R-EVID-02`, ou equivalentes) no catálogo; rationale de bloqueio/override.
- **WorkManager:** nenhuma nova tarefa obrigatória; a sessão guiada persistente, quando existir, pode usar foreground service no ciclo 3.
- **Privacidade/exportação:** execução e evidência entram no export; `MANUAL` não inventa atividade.

## 8. Dependências

- C1-03 (prescrição única) para saber qual sessão foi prescrita e se o dia é `REST`.
- ADR 0022 (data/fuso) para casar atividades e sessões no dia local correto.
- ADR 0023 (plano × prescrição) para separar intenção de decisão.
- ADR 0025 (lixeira) para suspender/retomar vínculo quando a atividade é excluída.
- Migração Room `11 → 12`; nenhuma dependência externa nova.
- `GUIDED` persistente depende do ciclo 3 (timer, recuperação, foreground service), fora deste ciclo.

## 9. Relação com os requisitos do PRD

| Requisito | Como este ADR atende |
|---|---|
| **FR-003 — Vínculo atividade ↔ sessão e conclusão auditável** | Evidência obrigatória, vínculo 1:1, bloqueio de descanso/doença. |
| **FR-002 — Execução guiada persistente** | Define o contrato de `GUIDED`; implementação completa no ciclo 3. |
| **BUG-02** | Elimina conclusão por clique e conclusão em descanso/doença. |
| **N-10** | Migra `DONE` antigo para `LEGACY_UNVERIFIED`, sem reescrever o passado. |
| **NFR-001 — Determinismo e auditabilidade** | Toda conclusão carrega fonte, confiança e vínculo auditável. |
| **FR-009 / NFR-003** | Exclusão de atividade suspende/remove evidência; sem ressurreição. |
| **BL-002 / BL-009** | Entrega a base de conclusão auditável; sessão guiada fica preparada. |

## 10. Referências

- `docs/PRD_COACH_CORRIDA.md` §6 (FR-002, FR-003), §2.1 (BUG-02), §3 (N-10)
- `docs/REVISAO_E_DIRECIONAMENTO_PRODUTO.md` §5.D2, §6.2–6.6, §7.6
- `docs/SPORTS_SCIENCE.md` §7 e §8 (recuperação, doença, descanso)
- `docs/adr/0022-deterministic-time-and-week.md`
- `docs/adr/0023-weekly-plan-vs-daily-prescription.md`
- `docs/adr/0025-deletion-retention-reset.md`
- `docs/PLANO_TECNICO_CICLO_1.md` §6.4 e §7

## 11. Status da implementação (06/11/2026)

- `ACTIVITY` e `MANUAL` implementados; `GUIDED` existe como contrato, sem produção de novas execuções até o ciclo 3.
- `session_executions` e as colunas de evidência/vínculo/encerramento estão no schema v12 (migração 11→12 testada).
- O modelo rejeita `DONE` sem evidência e `CLOSED` sem motivo; o vínculo atividade ↔ sessão é 1:1 por índice único e transação.
- A lixeira suspende a evidência, restaurar devolve e expurgar remove o vínculo mantendo `LEGACY_UNVERIFIED`.
- Adesão e execução comprovada aparecem separadas em Tendências; só `ACTIVITY`/`GUIDED` liberam progressão.
- **08/11/2026 — correção de `MANUAL`:** `SessionExecutionRepository.replaceManualWithActivity` e `ReplaceManualCompletion` permitem trocar um “fiz sem registro” pela corrida importada do mesmo dia. A operação é transacional, atualiza a execução existente e troca a evidência para `ACTIVITY`; não há migração de schema. A oferta aparece no overlay do import e no card do Hoje (“Corrigir registro”).
