# ADR 0028 — Replanejamento versionado e sugerido da semana

**Status:** aceito e implementado (R-01, R-02 e R-03, 08/11/2026)
**Data:** 2026-11-08
**Responsável:** planejamento com Rafael (usuário 0)
**Implementação:** `PlanReplanner` puro no `core:domain`; schema 13 com `weekly_plan_revisions`; `TrainingPlanRepository.applyReplan` transacional; `EvaluateReplan`/`ApplyReplan` no app; card de sugestão com versão, diff e aceitar/recusar/suprimir.

---

## 1. Contexto

O `WeeklyPlanGenerator` monta a semana uma vez e o resultado é persistido em `weekly_plans`/`planned_sessions`. Check-ins e prescrições não reescrevem o plano (ADR 0023). Isso está correto para a **prescrição do dia**, mas cria um descompasso quando a semana real desvia:

- o usuário perde segunda e quinta e o restante da semana continua desenhado para um mundo que não existe mais;
- a disponibilidade muda no meio da semana e só afeta a próxima geração;
- `weekly_plans` tem uma única linha por semana, então qualquer revisão seria uma sobrescrita silenciosa, sem histórico nem “o que mudou”;
- o ADR 0023 previu `version`/`revisionReason`/`revisedAt`, mas o modelo ainda não os tinha;
- reescrever sozinho quebraria o princípio “plano como intenção”: o usuário perderia a referência do que considerava a semana.

O replanejamento é uma hipótese de produto a validar com o usuário 0; nenhuma alegação de mercado é usada como evidência científica.

## 2. Problema

1. Sessões perdidas acumulam e o futuro não se redistribui.
2. Mudança de disponibilidade não reorganiza o resto da semana.
3. Não há versionamento nem auditoria de uma revisão.
4. A cláusula de adesão “pela versão que contém a sessão” (ADR 0023 §3.6) não é expressável.
5. Alterar o plano sem confirmação destrói a confiança e a intenção original.
6. Repor carga perdida ou aumentar o estímulo para “compensar” violaria o conservadorismo do app.

## 3. Decisão

### 3.1 Replanejar = regenerar o futuro e validar a semana mesclada

- O `PlanReplanner` recebe o plano vigente, perfil, prontidão, carga, semana anterior, posição no bloco, datas passadas comprovadamente não treináveis e `today`.
- O `WeeklyPlanGenerator` gera **apenas** os dias estritamente futuros.
- O replanejador mescla o futuro gerado com as sessões preservadas e valida a semana completa: máximo de 2 qualidades, um longão, espaçamento qualidade↔qualidade e qualidade↔longão, disponibilidade e agregados coerentes.
- Sessões já feitas contam para os limites.
- A revisão **não compensa** treino perdido: volume/duração e quantidade de qualidades futuras não podem exceder a intenção futura vigente.
- Se o candidato não puder ser validado sob esse teto e os demais guardrails, o resultado é `NoChange`; nada é aplicado silenciosamente.
- Nenhuma regra fisiológica nova é criada: o replanejador reaplica o gerador, o deload/taper e os guardrails existentes.

### 3.2 Toque apenas em `date > today`

- Sessões de hoje e passadas — `DONE`, `CLOSED` ou `PLANNED` — **nunca** são tocadas.
- Sessões futuras `DONE`/`CLOSED` também são preservadas campo a campo.
- A decisão de hoje continua sendo da prescrição diária (ADR 0023): “uma decisão por dia” é preservada por construção.
- A prescrição de hoje não muda no aceite; amanhã ela recalcula normalmente por hash.

### 3.3 Versionamento com auditoria

`weekly_plans` ganha:

- `version` (começa em 1);
- `revisionReason?`;
- `revisedAt?`;
- `inputsHash?`;
- `availabilityHash?`.

A tabela append-only `weekly_plan_revisions` guarda:

- `fromVersion`/`toVersion`;
- hashes de inputs e disponibilidade antes/depois;
- versão do gerador;
- snapshot JSON dos metadados da versão anterior;
- snapshot JSON das sessões futuras `PLANNED` substituídas;
- trigger, motivo, regras/evidências, confiança e `createdAt`.

Decisões registradas:

- **A1:** `createdOn` permanece o original para revisões da semana vigente.
- **A2:** uma linha vigente em `weekly_plans` + snapshots completos append-only; não há PK composta por semana/versão.
- Planos legados da migração têm hash desconhecido; a migração não inventa histórico.
- A disponibilidade atual pode ser capturada como baseline explícito após a migração; isso não afirma qual era a disponibilidade original.
- `inputsHash` registra os inputs que produziram a **versão vigente**. Ele é auditoria, não guarda de obsolescência: compará-lo com uma sugestão nova rejeitaria revisões legítimas sempre que prontidão/carga mudassem. O aceite usa `expectedVersion`, baseline de disponibilidade e, opcionalmente, `currentInputsHash` recalculado.

### 3.4 Gatilhos determinísticos

- **T1 — desvio de adesão:** conta sessões elegíveis com `date < today`, `date >= createdOn`, `status = PLANNED`, excluindo `REST` e datas comprovadamente não treináveis por check-in/prescrição. O limiar inicial é **2**, marcado como heurística de produto (A6), não limiar científico.
- **A5:** as sessões passadas `PLANNED` são contadas como não feitas **sem** serem fechadas automaticamente.
- **T2 — disponibilidade mudou:** compara a disponibilidade atual apenas com `availabilityHash`. Baseline desconhecido é capturado, nunca inferido como mudança.
- **T3 — pedido explícito:** o botão “Replanejar minha semana” força a avaliação mesmo sem T1/T2.

### 3.5 Sugestão, nunca automação

- A revisão é um convite: o app avisa, o usuário aceita ou recusa.
- A recusa é por proposta (`semana + versão + hash dos inputs`); uma nova versão/inputs pode reaparecer.
- “Não sugerir hoje” suprime as sugestões automáticas até o fim do dia em `SharedPreferences`; o botão explícito continua funcionando.
- A UI mostra:
  - selo de versão (`v1`, `v2`…);
  - diff “o que mudou” apenas das sessões futuras `PLANNED`;
  - aceitar, recusar esta proposta e não sugerir hoje;
  - confiança e, sob demanda, o motivo auditável.
- A copy evita culpa: “Dá para ajustar o resto da semana”, “Agora não” e “Não sugerir hoje”. O par `Agora não` × `Não sugerir hoje` foi escolhido com o usuário 0 para distinguir “esta proposta” de “o resto do dia”.

### 3.6 Aceite transacional e obsolescência

`ApplyReplan`/`applyReplan`:

1. relê o estado atual;
2. exige `expectedVersion`;
3. recalcula o `inputsHash` com os inputs atuais;
4. valida o baseline de disponibilidade;
5. grava o snapshot da versão anterior em `weekly_plan_revisions`;
6. remove apenas sessões futuras `PLANNED`;
7. insere as candidatas com conflito `ABORT` (nunca `REPLACE`);
8. recalcula `weeklyMinutes`, `longRunMinutes`, `qualityCount`, rationale/confiança/regras/evidências a partir da semana mesclada;
9. incrementa `version` e mantém `createdOn`.

Versão, data ou inputs diferentes devolvem `Stale` com uma avaliação nova; conflito com sessão protegida ou candidato inválido devolve `Rejected`. A validação condicional vive dentro da mesma transação, sem janela entre leitura e escrita.

### 3.7 Interação com troca, prescrição e `PLAN_REPLACED`

- Trocas manuais (ADR 0013) são edições da versão vigente; uma revisão as substitui e o snapshot as preserva.
- A UI explica no diff que o futuro da semana será substituído, incluindo trocas.
- O toque é `date > today`, então a prescrição de hoje **não** muda na revisão.
- `PLAN_REPLACED` (ADR 0024) continua existindo, mas a revisão **apaga** futuras `PLANNED` em vez de encerrá-las; o motivo fica no snapshot, não em status de sessão.

### 3.8 Determinismo e explicabilidade

- Mesma entrada ⇒ mesmo plano mesclado e `inputsHash` estável.
- `NoChange` compara a projeção semântica completa do futuro: data, tipo, título, duração, alvos, regras e evidências. Mudança só de alvos/regras já produz sugestão.
- Toda revisão cita ao menos uma regra e uma evidência realmente emitidas pelo gerador.
- Não criar `R-PLAN-09` e não citar `R-PLAN-01` como aplicada: a progressão de volume ainda não está implementada.
- Sem LLM: o áudio/texto futuro, se existir, só reescreve a decisão; não decide carga, pace ou plano.

### 3.9 Contrato de domínio (implementado)

```kotlin
// core:domain — replan/ReplanModels.kt
enum class ReplanTrigger { MISSED_SESSIONS, AVAILABILITY_CHANGED, USER_REQUEST }

data class ReplanSuggestion(
    val weekStart: LocalDate,
    val trigger: ReplanTrigger,
    val missedTrainableCount: Int,
    val expectedVersion: Int,
    val expectedInputsHash: String,
    val expectedAvailabilityHash: String?,
    val availabilityHash: String,
    val rationale: String,
    val ruleIds: List<String>,
    val evidenceIds: List<String>,
    val confidence: DataConfidence,
)

sealed interface ReplanResult {
    data class Suggest(val suggestion: ReplanSuggestion, val candidate: WeeklyPlan) : ReplanResult
    data class NoChange(val reason: String, val availabilityHash: String) : ReplanResult
}

data class ReplanInput(
    val currentPlan: WeeklyPlan,
    val currentVersion: Int,
    val currentInputsHash: String? = null,
    val currentAvailabilityHash: String? = null,
    val trigger: ReplanTrigger,
    val profile: AthleteProfile,
    val readiness: ReadinessResult,
    val load: LoadSnapshot? = null,
    val previousWeek: WeeklyPlan? = null,
    val weekNumberInBlock: Int = 1,
    val nonTrainablePastDates: Set<LocalDate> = emptySet(),
    val missedSessionThreshold: Int = PlanReplanner.PRODUCT_MISSED_SESSION_THRESHOLD,
    val today: LocalDate,
)
```

## 4. Alternativas consideradas

1. **Sobrescrever o plano em silêncio (upsert).**
   - Perde a intenção original e viola o ADR 0023; o usuário vê o plano mudar sem saber por quê.
2. **Replanejar sozinho ao detectar desvio.**
   - Confortável, mas fere a confiança e o princípio de decisão explícita já usado na oferta de importação.
3. **Linhas novas em `weekly_plans` por versão (PK composta).**
   - Historicamente mais limpo, mas quebra a FK de `planned_sessions`, exige refazer índices/queries e testes de migração; o benefício é equivalente ao snapshot em tabela separada.
4. **Deixar a prescrição diária cuidar de tudo.**
   - A prescrição só rebaixa o dia; não redistribui a semana nem reorganiza dias disponíveis.
5. **Revisão semanal automática no domingo (FR-011).**
   - Resolve “para a próxima semana”, não “esta semana está quebrada”; é a camada seguinte, fora deste ADR.
6. **LLM escrevendo o novo plano.**
   - Proibido pelo ADR 0004 e pela regra 6 do AGENTS.

## 5. Justificativa

O `WeeklyPlanGenerator` é o primitivo de geração reutilizável: já conhece `today` (N-02), disponibilidade, guardrails e deload, mas não conhece as sessões já concluídas desta semana. `PlanReplanner` mescla e valida a semana inteira antes de sugerir; a parte adicional é governança: versionar, sugerir e preservar sessões passadas/atuais e estados protegidos.

Sugerir em vez de executar alinha o app ao padrão já aceito na oferta de importação e ao princípio “sem culpa”: a revisão é um convite (“2 treinos não aconteceram; quer remontar o resto da semana?”), não uma punição nem uma reescrita escondida.

## 6. Consequências

### 6.1 Positivas

- O plano volta a descrever a realidade do usuário no meio da semana sem exceder limites ao combinar sessões feitas e novas.
- Auditoria por revisão: snapshots e metadados suficientes para explicar o estado substituído e o vigente.
- Reuso do gerador e dos guardrails; zero regra fisiológica nova.
- `createdOn` original mantém a semântica de adesão N-02 e evita punição retroativa.
- Base pronta para FR-011/N-03/periodização, que virão com ADR próprio.
- A prescrição do dia permanece intacta.

### 6.2 Negativas e custos

- Migração `12 → 13` (colunas + tabela nova) com teste de migração.
- UI nova: sugestão, selo de versão, sheet “o que mudou”; goldens.
- `PlanCompletionCalculator` e queries de plano precisam olhar a versão vigente sem perder histórico.
- Risco de churn de prescrição amanhã (hash muda após revisão) — esperado e auditável, mas a copy precisa explicar.
- Mais uma superfície de explicação para o usuário entender “o plano mudou por quê”.

### 6.3 Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Regressão em adesão/progressão | `createdOn` preservado; testes campo a campo de hoje/passado e cenário Mon→Fri |
| Revisão exceder limites por ignorar treino concluído | Validar invariantes sobre o plano mesclado, incluindo qualidade/longão já feitos |
| Linha protegida sobrescrita por ID determinístico | Inserção sem `REPLACE`; conflito com `DONE`/`CLOSED` aborta a transação |
| Revisão virar “recompensa de pular treino” | Teto de volume/duração/qualidade futura e teste de não reposição |
| Snapshot incompatível com export/reset | Incluir `weekly_plan_revisions` no JSON e no reset |
| Sugerir em dia sem plano/fim de semana | `NoChange` sem plano futuro; teste de borda domingo 23h |
| Dupla sugestão encher a tela | Recusa por proposta + supressão por dia; o botão explícito continua |
| Hash histórico inexistente | Hashes legados `NULL`; baseline de disponibilidade capturado explicitamente |
| Aceite obsoleto | `expectedVersion` + inputs recalculados + baseline dentro da transação |
| Troca + revisão confundir | Copy no diff: a revisão substitui o futuro desta semana, incluindo trocas |

## 7. Impactos arquiteturais

- **`core:domain`:** `ReplanTrigger`, `ReplanSuggestion`, `ReplanResult`, `ReplanInput` e `PlanReplanner` puros; `WeeklyPlanGenerator` sem mudança de regra.
- **`core:data`:** colunas novas em `weekly_plans`; `weekly_plan_revisions` + DAO; `applyReplan` transacional; `captureAvailabilityBaseline`; `getWeekRecord`/`getLatestRevision`; migração `12 → 13`; export/reset.
- **`:app/application`:** `ReplanContextBuilder`, `EvaluateReplan`, `ApplyReplan`, `ReplanDiff`; a UI não decide regra.
- **`:app/ui`:** `ReplanViewModel`, card de sugestão, selo de versão, “Ver o que mudou” e botão explícito no card do plano.
- **Explicabilidade:** reaplica IDs realmente emitidos; sem `R-PLAN-09`.
- **Sem novos módulos** e sem dependência externa nova.

## 8. Dependências

- ADR 0023 (intenção × autoridade; `version` previsto) e ADR 0024 (`PLAN_REPLACED`).
- ADR 0022 (`TimeProvider` — gatilhos por data local).
- ADR 0010/0011/0026 (guardrails reaproveitados pelo gerador).
- `GetTodayPrescription` (recálculo por hash).
- Migração Room `12 → 13`.

## 9. Relação com o PRD

| Requisito | Como este ADR atende |
|---|---|
| **FR-011 (relacionado, não atendido)** | O replanejamento é pré-requisito para a prévia da próxima semana, mas não entrega o card de revisão, métricas e observações. O requisito continua pendente. |
| **ADR 0023 §3.6** | Implementa `version`/`revisionReason`/`revisedAt` e registra a revisão sem perder os metadados da versão substituída. |
| **FR-001** | Preservado: a prescrição do dia não é tocada (`date > today`). |
| **NFR-001** | Revisão versionada, determinística, com hash, regra, evidência e confiança. |
| **BL-020** | Pré-requisito para a revisão semanal completa. |

## 10. Notas de implementação (08/11/2026)

- **R-01 (domínio):** `PlanReplanner` e contratos em `core/domain/replan`; 28 testes em `PlanReplannerTest` cobrindo limiar, `createdOn`, doença/REST, disponibilidade, T3, troca manual, teto de volume/qualidade, estados protegidos, semana/futuro ausentes e determinismo/hashes.
- **R-02 (dados):** schema 13, `weekly_plan_revisions`, `applyReplan`, baseline explícito, `getWeekRecord`, export/reset. Migração `12→13` testada com hashes legados `NULL`.
- **R-03 (app/UI):** `ReplanContextBuilder`, `EvaluateReplan`, `ApplyReplan`, `ReplanDiff`, `ReplanSuggestionStore`, `ReplanViewModel` e card. O aceite revalida versão/inputs; stale reavalia; a prescrição de hoje é testada antes/depois.
- **Decisões de produto aplicadas:** A1, A2, A5 e A6. A3/A4/A7 pertencem à sessão guiada (ADR 0027) e não bloqueiam o replanejamento.
- **Correção documental:** `inputsHash` é auditoria da versão vigente, não guarda de obsolescência; o aceite usa `expectedVersion` + baseline + `currentInputsHash` recalculado.
- **Escopo mantido:** sem LLM, sRPE, nova regra fisiológica, alteração de outra semana ou FR-011 completo.

## 11. Referências

- `docs/PLANO_CICLO_2_GUIADO_E_REPLANEJAMENTO.md` §4 e §5
- `docs/adr/0023-weekly-plan-vs-daily-prescription.md` (§3, §3.6)
- `docs/adr/0024-completion-evidence.md` (`PLAN_REPLACED`)
- `docs/adr/0013-manual-session-swap.md` (troca de sessões)
- `docs/PRD_COACH_CORRIDA.md` §6 (FR-011), §8 (BL-020)
- `docs/SPORTS_SCIENCE.md` §8.8 (plano semanal conservador e replanejamento)
- `docs/PLANO_TECNICO_CICLO_1.md` §6.3 (C1-03)
