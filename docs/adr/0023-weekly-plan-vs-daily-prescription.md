# ADR 0023 — Plano semanal como intenção e prescrição diária como autoridade

**Status:** aceito e implementado (C1-03, 06/11/2026)
**Data:** 2026-11-06
**Responsável:** planejamento com Rafael (usuário 0)
**Implementação:** `DailyPrescriptionEngine`, `daily_prescriptions` (Room 11), `GetTodayPrescription`, card único no Hoje e corte de adesão por `createdOn`.

---

## 1. Contexto

O app tem hoje **dois centros de decisão** para o mesmo dia:

- `WeeklyPlanGenerator` monta e persiste a semana em `weekly_plans`/`planned_sessions`;
- `TodayViewModel` chama `WorkoutRecommender` diretamente para o cartão principal, sem persistir a decisão;
- `TodayScreen` mostra o cartão recomendado **e** o plano da semana, cada um com sua própria ação de concluir/pular.

Isso produz contradições reais:

- o plano pode dizer “Tempo” e o cartão principal dizer “Fácil”;
- o usuário vê dois treinos e escolhe o que preferir;
- a recomendação de ontem não existe mais, então não há auditoria;
- a sessão planejada não tem alvos de pace/FC, distância, `activityId` ou `executionId` (N-01);
- o plano gerado no meio da semana inclui dias já passados, que não podem ser concluídos nem pulados e entram na adesão como pendentes (N-02);
- não se sabe se uma sessão foi mantida, reduzida, rebaixada ou trocada por descanso, nem por quê.

A decisão de produto aprovada é: **o plano é a intenção; a prescrição diária é a autoridade do dia**. O plano continua visível, mas não é uma segunda ordem.

## 2. Problema

Sem uma decisão diária única e persistida:

1. a promessa “uma decisão por dia” não se sustenta;
2. o usuário não consegue explicar por que o treino de hoje difere do planejado;
3. a adesão compara o executado com um plano que pode ter sido ignorado no dia;
4. a progressão semanal não consegue distinguir “plano mantido” de “plano rebaixado”;
5. o mesmo input pode gerar decisões diferentes em telas diferentes;
6. não há como auditar a decisão de ontem com a versão do motor, as regras e os dados usados;
7. mudanças de check-in durante o dia não têm histórico.

Também é necessário resolver N-01 (modelo pobre da sessão planejada) e N-02 (plano gerado no meio da semana) **na mesma camada**, porque a prescrição não pode ser construída sobre uma sessão sem alvos nem sobre dias que já passaram.

## 3. Decisão

1. **`WeeklyPlan` é intenção, não prescrição.**
   - Representa tipo, distribuição, duração, longão, qualidade, taper e deload da semana.
   - É gerado uma vez e preservado; não é reescrito por check-in, prontidão ou execução.
   - Alterações explícitas do usuário (troca de sessões do ADR 0013) preservam `status`, `completedAt` e histórico; se houver replanejamento, o plano ganha `version`, `revisionReason` e `revisedAt` em vez de sobrescrever o original.
2. **`DailyPrescription` é a única autoridade do dia.**
   - É calculada a partir de: perfil, plano, sessão planejada, prontidão, carga, check-in (incluindo calor), execução e data/fuso.
   - Pode apenas:
     - `KEEP`: manter tipo, duração e alvos;
     - `REDUCE`: reduzir duração, mantendo o tipo;
     - `DOWNGRADE`: qualidade → fácil; longão → fácil reduzido; qualquer tipo → `REST` em doença/vermelho;
     - `REST`: sem treino;
     - `NO_PLAN`: fácil conservador com confiança baixa quando não há plano.
   - **Nunca aumenta** tipo, volume, duração ou intensidade em relação ao plano. Um “upgrade explícito” só existiria como decisão de produto separada, com confirmação do usuário; não faz parte deste ADR.
3. **Uma decisão por dia, consumida por todas as telas.**
   - `DailyPrescriptionRepository.observeToday()` (ou caso de uso equivalente) é a fonte do cartão principal do Hoje.
   - Nenhuma tela, ViewModel ou componente recalcula a prescrição.
   - O plano da semana é contexto visual, com selo “ajustado hoje” quando a prescrição difere da intenção; não tem CTA de conclusão próprio.
4. **Prescrição persistida e versionada.**
   - Cada decisão é uma linha com `date`, `plannedSessionId?`, `plannedType`, `prescribedType`, `plannedDurationMin`, `prescribedDurationMin`, `targets`, `adjustment`, `rationale`, `ruleIds`, `evidenceIds`, `confidence`, `engineVersion`, `inputsHash`, `createdAt` e `supersedesId?`.
   - A versão vigente é a última do dia. Editar o check-in **acrescenta** uma versão; não apaga a anterior.
   - `inputsHash` permite detectar que uma decisão foi gerada com dados diferentes dos atuais.
5. **Alvos derivados de forma explícita.**
   - A sessão planejada passa a carregar `targetZone`/`targetPace`/`targetHr`/`distanceM` quando aplicável.
   - Se não houver calibração, a prescrição usa RPE e `null` para pace/FC; nunca inventa número.
   - Calor reduz o pace alvo e/ou aumenta o RPE; nunca aumenta a duração.
6. **Plano gerado no meio da semana começa em hoje (N-02).**
   - `WeeklyPlanGenerator` recebe `today` e gera apenas sessões de `max(weekStart, today)` até domingo.
   - O plano persiste `createdOn` (a entidade já tem `createdAt`; o modelo passa a expor a data) e a adesão ignora sessões anteriores à criação do plano.
   - Se o plano for revisado, a adesão usa a data de criação da versão que contém a sessão, não uma data retroativa.
7. **Auditoria planejado × recomendado × executado.**
   - A prescrição guarda a intenção original e a decisão do dia.
   - A execução (ADR 0024) guarda o que aconteceu.
   - A comparação é derivada, não uma nova decisão: mostra diferenças de duração, distância, pace, FC, zona e RPE quando existirem.
8. **Explicação obrigatória.**
   - Toda prescrição cita ao menos uma regra e uma evidência; o catálogo cobre todos os IDs emitidos.
   - A UI mostra “planejado → ajustado → por quê” em um único caminho, sem jargão.
   - Dados ausentes são declarados; ausência não vira zero.
9. **Sem novas superfícies neste ciclo.**
   - A prescrição diária é uma correção do loop, não uma feature de sessão guiada, revisão semanal ou periodização.

### 3.1 Contrato de domínio

```kotlin
// core:domain — prescription/DailyPrescription.kt
enum class PrescriptionAdjustment { KEEP, REDUCE, DOWNGRADE, REST, NO_PLAN }

data class PrescriptionTargets(
    val pace: PaceTarget? = null,
    val heartRate: HeartRateTarget? = null,
    val zone: String? = null,
    val distanceKm: Double? = null,
    val rpe: Int? = null,
)

data class DailyPrescription(
    val date: LocalDate,
    val plannedSessionId: String?,
    val plannedType: WorkoutType?,
    val plannedTitle: String?,
    val plannedDurationMin: Int?,
    val prescribedType: WorkoutType,
    val title: String,
    val prescribedDurationMin: Int?,
    val targets: PrescriptionTargets,
    val adjustment: PrescriptionAdjustment,
    val rationale: String,
    val ruleIds: List<String>,
    val evidenceIds: List<String>,
    val confidence: DataConfidence,
    val engineVersion: String,
    val inputsHash: String,
    val id: String? = null,
    val supersedesId: String? = null,
)

class DailyPrescriptionEngine {
    fun prescribe(input: DailyPrescriptionInput): DailyPrescription
}
```

### 3.2 Regras de decisão (ordem determinística)

1. Doença/lesão ou prontidão vermelha ⇒ `REST`/`DOWNGRADE` para descanso.
2. Sem plano ⇒ `NO_PLAN` (fácil conservador, confiança baixa).
3. Sessão planejada é `REST` ⇒ `REST`.
4. Prontidão âmbar, sono curto, `NO_QUALITY` ou carga desatualizada ⇒ `REDUCE`/`DOWNGRADE`, nunca `KEEP` de qualidade.
5. ACWR desconhecido, cobertura insuficiente ou fonte desatualizada ⇒ sem qualidade; ADR 0026.
6. Caso contrário ⇒ `KEEP` da intenção, com alvos derivados do perfil.
7. Calor autorreportado ⇒ ajuste conservador de pace/RPE, sem aumentar duração.
8. Nenhuma decisão pode aumentar o que o plano pediu.

## 4. Alternativas consideradas

1. **Plano manda; prontidão só rebaixa.**
   - Seguro e simples, mas não cobre “sem plano”, doença, calor e redução de duração; a decisão diária ficaria escondida no plano.
2. **Recomendação diária independente (estado atual).**
   - Reage ao dia, mas contradiz o plano e mantém dois motores. É exatamente o BUG-01.
3. **Plano e recomendação “negociam”.**
   - Parece inteligente, mas é imprevisível e difícil de explicar; viola o determinismo.
4. **Prescrição apenas em memória.**
   - Mais barata, mas não permite auditoria, versão, comparação nem “por que mudou”.
5. **Sobrescrever o plano com a recomendação do dia.**
   - Perde a intenção original e impede comparar planejado × executado.
6. **Gerar a semana inteira no meio da semana e marcar dias passados como `SKIPPED`.**
   - Continua punindo o usuário por algo que ele não podia fazer; N-02 exige cortar a geração.
7. **Criar um módulo `:feature:prescription`.**
   - Não se justifica agora; o custo está no contrato e na persistência, não na separação de módulos.

## 5. Justificativa

O produto promete “uma decisão por dia”. Isso só é verdade se existir **um** objeto que decida o dia e se todas as telas o consumirem. O plano continua necessário para dar coerência semanal, mas não pode competir com a decisão diária.

Separar intenção de prescrição também resolve a auditoria: o usuário vê o que estava planejado, o que foi recomendado e o que foi executado, com regra, evidência e confiança. Sem essa separação, qualquer mudança de check-in apaga a história e a progressão não consegue distinguir adaptação de falha.

Persistir a prescrição é o custo necessário para:

- explicar ontem;
- comparar intenção e execução;
- evoluir o motor com `engineVersion`;
- testar a tabela de decisão de forma determinística.

## 6. Consequências

### 6.1 Positivas

- Um único treino no Hoje; fim dos dois cards concorrentes.
- O plano permanece estável e auditável.
- A decisão do dia tem versão, regra, evidência, confiança e hash de inputs.
- É possível comparar planejado × recomendado × executado.
- N-01 e N-02 são resolvidos na mesma camada, sem espalhar regras pela UI.
- A progressão passa a saber se a sessão foi mantida ou rebaixada.

### 6.2 Negativas e custos

- Nova tabela e migração `10 → 11` (C1-03), com teste de migração.
- `TodayViewModel` e `TodayScreen` mudam de forma significativa; goldens serão refeitos.
- O plano precisa de `createdOn` no modelo e de regra de corte de adesão.
- A UI precisa aprender a mostrar “ajustado hoje” sem transformar o plano em segunda ordem.
- A prescrição de dias antigos não existe para o passado; o histórico começa na implantação.
- Existe risco de o usuário sentir que “o plano mudou” quando apenas a prescrição foi reduzida; a copy precisa separar intenção de decisão.

### 6.3 Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Regressão alta no Hoje | Contrato de domínio testado antes da UI; commits pequenos; manter o recomendador como fallback temporário durante a migração |
| Duas fontes de verdade durante a transição | Feature flag interna de leitura; teste que falha se a tela chamar `WorkoutRecommender` diretamente |
| Migração perder planos/status | `MigrationTestHelper` com banco v10 sanitizado; backfill explícito; nenhum `fallbackToDestructiveMigration` |
| Prescrição antiga “vazar” para hoje | Chave por `date` + versão; consulta sempre pela data do `TimeProvider` |
| Plano gerado no meio da semana ainda criar passado | Teste de geração com `today` no meio da semana; asserção de que nenhuma sessão é anterior a `today` |
| Explicação incompleta | Teste de catálogo varrendo todos os `ruleId`/`evidenceId` emitidos |

## 7. Impactos arquiteturais

- **`core:model`:** `PrescriptionAdjustment`, `PrescriptionTargets`, `DailyPrescription` (ou tipos equivalentes); enriquecimento de `PlannedSession` com alvos e vínculos.
- **`core:domain`:** novo `DailyPrescriptionEngine` puro; `WeeklyPlanGenerator` recebe `today` e não gera passado; `PlanCompletionCalculator` corta por `createdOn`.
- **`core:data`:** `DailyPrescriptionEntity`, DAO, `DailyPrescriptionRepository`; migração `10 → 11`; `TrainingPlanRepository` expõe `createdOn`.
- **`:app/application`:** caso de uso `GetTodayPrescription` (ou `ObserveTodayPrescription`) que orquestra repositórios e persiste a versão.
- **`:app/ui`:** `TodayViewModel` só coleta estado; `TodayScreen` tem um card principal e o plano como contexto; `TodayExplanation` mostra “planejado → ajustado”.
- **Explicabilidade:** novos IDs no `ExplanationCatalog`; `R-DATA-01` para dados ausentes; regras de prescrição existentes (`R-READY-03`, `R-READY-04`, `R-LOAD-03`, `R-SAFE-01`, `R-ZONE-01`, `R-CAL-02`, `R-PLAN-07`).
- **Testes:** contrato de tabela de decisão, persistência/versão, migração, UI de um card, corte de adesão, golden.
- **Sem novos módulos**; a regra de “não refatorar o app inteiro” é preservada.

## 8. Dependências

- C1-01 (data/fuso) para `date`, `today` e corte de adesão.
- C1-02 (guardrails de carga) para impedir qualidade com ACWR desconhecido.
- ADR 0022 (tempo) e ADR 0026 (carga/qualidade).
- ADR 0010 (disponibilidade como teto) e ADR 0013 (troca de sessões) continuam válidos.
- Migração Room `10 → 11`; nenhuma dependência externa nova.
- C1-04 depende desta entrega para vincular execução à sessão prescrita.

## 9. Relação com os requisitos do PRD

| Requisito | Como este ADR atende |
|---|---|
| **FR-001 — Prescrição única do dia** | Uma decisão persistida, um card, plano como contexto. |
| **BUG-01** | Elimina os dois motores concorrentes. |
| **N-01** | Enriquece a sessão planejada com alvos e vínculos; a prescrição deriva o que falta do perfil. |
| **N-02** | Plano gerado a partir de hoje; adesão cortada pela criação. |
| **NFR-001 — Determinismo e auditabilidade** | `engineVersion`, `inputsHash`, regra/evidência/confiança e versão por dia. |
| **FR-010 — Comparação planejado × executado** | Prepara a base (intenção + recomendação + execução), sem implementar a tela agora. |
| **BL-001 — Prescrição única** | Entrega o núcleo do bloco. |

## 10. Referências

- `docs/PRD_COACH_CORRIDA.md` §6 (FR-001, FR-010), §7 (NFR-001), §2.1 (BUG-01)
- `docs/REVISAO_E_DIRECIONAMENTO_PRODUTO.md` §5.D3, §6.1–6.6, §7.5
- `docs/SPORTS_SCIENCE.md` §8.7–8.9
- `docs/adr/0010-availability-hard-cap-progression.md`
- `docs/adr/0013-manual-session-swap.md`
- `docs/adr/0022-deterministic-time-and-week.md`
- `docs/adr/0024-completion-evidence.md`
- `docs/adr/0026-load-coverage-quality-eligibility.md`
- `docs/PLANO_TECNICO_CICLO_1.md` §6.3 e §7
