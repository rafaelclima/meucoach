# Ciclo 4 — Treino real: checklist de execução

**Versão:** 1.0 · **Data:** 16/11/2026
**Status:** S0 a S3 concluídas; **S4 é a próxima sessão**.
**Fonte de verdade:** `docs/PRD_TREINO_REAL.md` v1.1 + ADRs 0044–0050.
**Uso:** **este é o primeiro arquivo a abrir em cada sessão.** O bloco “Estado atual” manda; se ele divergir do git, o git vence e o bloco deve ser corrigido antes de qualquer código.

---

## 0. Estado atual

> **Última atualização:** 16/11/2026 — S5
> **Último commit:** S5 — `feat(domain): add real volume reference projection (C4-38)` (S4: `6678165`)
> **Fase:** 1 — Domínio — **em andamento**
> **Sessão atual:** **S5 parcial — C4-38 concluída; C4-39 pendente**
> **Task atual:** C4-39 (TODO)
> **Próxima task:** C4-39
> **Bloqueios:** nenhum
> **Gates da última sessão:** `:core:domain:test` 887 verdes; `:core:data:test` 171; `:app:testDebugUnitTest` 890; `:app:assembleDebug`; `spotlessCheck`; `:core:domain:koverVerify`; `:core:domain:detekt`; `:app:detekt`; FIT 93,01% de branches
> **Próxima ação:** ler ADR 0050 §2.2–2.3 e FR-408; abrir C4-39 (`VolumeProgression`/`MesoCycle`/`LevelGraduation` usando `VolumeReference`/`StrongRun`, com adesão separada), TDD.

### 0.1 O que já está pronto

**S0 — documentação**

- [x] PRD `docs/PRD_TREINO_REAL.md` v1.1 aprovada (12/12 decisões; §11.5).
- [x] ADR 0044 — oportunidade de qualidade e autoridade do dia.
- [x] ADR 0045 — dimensionamento do longão.
- [x] ADR 0046 — load gate v3/ACWR confiável.
- [x] ADR 0047 — catálogo/periodização e iniciante.
- [x] ADR 0048 — prontidão de dados/upgrade/schema v18.
- [x] ADR 0049 — prontidão/check-in/sono.
- [x] ADR 0050 — volume real/treino extra/LEGACY.
- [x] `docs/DECISIONS.md` r1.43 com os 7 ADRs.
- [x] Este checklist.

**S1 — domínio**

- [x] `QualityKind` ganhou `FARTLEK`, `HILLS` e `RACE_PACE`, preservando os valores legados.
- [x] `QualityOpportunity`, `PlanDecision`, `PlanDecisionReason`, `QualityBlockReason` e `QualityEligibility` no domínio puro.
- [x] `PlannedSession` ganhou `qualityKind`/`planDecision` com default `null`; o gerador não mudou.
- [x] `WeeklyPlan.qualityCount` documentado como oportunidades planejadas.
- [x] `QualityGate.evaluate(...)` puro com a precedência do ADR 0044 §2.4.
- [x] 42 testes novos: `QualityOpportunityTest` (15) e `QualityGateTest` (27).

**S2 — longão**

- [x] Comparador determinístico do long day: disponibilidade > sáb/dom > sexta > dia mais tarde (ADR 0045 §2.1).
- [x] Piso 45/60 (30 em deload/taper), alvo 28% e `longestOther + 5`; disponibilidade e teto 150/180 como limites.
- [x] Título honesto “Corrida mais longa” + `PlanDecision(LONG_DAY_TOO_SHORT)` abaixo do piso; deload/taper com título honesto sem dica de agenda.
- [x] `fitToVolume` ancorado no piso: nunca cresce, nunca fica abaixo do piso; escala o restante e dropa só fácil (mínimo 1); semana mínima declarada no rationale.
- [x] Longão nunca menor que o fácil mais longo, inclusive após taper/deload por data.
- [x] Replanner mantém a guarda em T1/T2/T3; aceita a correção estrutural do longão legado até o piso e, em T4, a nova disponibilidade que move/cria o longão, sem crescer as demais sessões futuras.
- [x] Golden `G-PER-04` (2×40 + sábado 90 → longão 45, “Longão”, volume 125, qualidade).
- [x] 9 testes novos em `LongRunSizingTest`; testes legados atualizados com justificativa.

**S3 — carga/ACWR v3**

- [x] `ActivitySourceStatus.firstActivityAt` e pré-história classificada como “sem dado”; o engine deriva a data quando a fonte não a informa.
- [x] `LoadCoverage.daysBeforeHistory`; dias sem atividade só são descanso observado depois da primeira atividade e com fonte fresca.
- [x] `LoadSnapshot.acwrReliable`, `strainReliable`, `historyDays`, `baselineDays` e `acwrHigh` derivados de uma janela completa de 28 dias.
- [x] `permitsQuality` e `permitsProgression` separados; progressão exige ACWR confiável em 0,8–1,3; ACWR não confiável não veta qualidade.
- [x] 1,5 é o único limite de redução em todos os caminhos; `DailyPrescriptionEngine`, `QualityGate`, `WeeklyPlanGenerator`, `WorkoutRecommender` e `VolumeGates` usam `LoadSnapshot.ACWR_MAX`.
- [x] `firstActivityAt` fiado em `TrainingSignals`/`TodayViewModel`; volume sem janela completa mantém e explica “em calibração”.
- [x] 19 testes novos líquidos: `LoadReliabilityTest` (7), `LoadGateV3Test` (10), limites e confiabilidade no diário/gerador/recomendador/volume.

**S4 — prontidão/check-in/sono**

- [x] `ReadinessFlag.SUBJECTIVE_MISSING` e `ReadinessResult.qualityEligibleByReadiness` (verde + subjetivo + sem sono curto + sem doença); sem check-in o score pode ficar verde, mas a qualidade é inelegível.
- [x] `SleepNightValidator`: janela 18:00–12:00 local, blocos com gap ≤120 min, união sem contar o gap, maior bloco em noite fragmentada e faixa plausível 180–840 min.
- [x] Agregador converte noite suspeita em `sleepDurationMin=null`, conta noites suspeitas/fragmentadas e propaga o diagnóstico até a UI; readiness/review não geram `SHORT_SLEEP` falso.
- [x] 29 testes novos no domínio (`ReadinessEligibilityTest` 8, `SleepNightValidatorTest` 12, `SleepPlausibilityTest` 4, agregador 5) + 3 em `:core:data` + 1 no `:app`.

**S5 — volume real (parcial)**

- [x] `StrongRun` e `VolumeReference` no domínio puro, com validações e `hasPlan`.
- [x] `VolumeReferenceBuilder.build(...)`: agrega as atividades fortes da semana (`strongMinutes`/`strongSessions`), preenche `plannedMinutes`, `plannedSessions`, `createdOn` e `specialWeek`, e ignora corridas fora da semana.
- [x] `VolumeReferenceProjection` na borda (`:app`): converte `ActivitySummary` RUNNING/TREADMILL importadas em `StrongRun`, vincula o `plannedSessionId` quando a atividade está ligada a uma sessão e devolve uma referência por semana planejada; ordem determinística; atividade manual/curta não conta.
- [x] 14 testes em `VolumeReferenceTest` e 8 em `VolumeReferenceProjectionTest`; `SPECIAL_WEEK_RULE_IDS` unifica a lista usada por `VolumeProgression`/`MesoCycle`.
- [ ] C4-39 pende: `VolumeProgression`, `MesoCycle` e `LevelGraduation` ainda usam sessões planejadas, não a referência real.

### 0.2 O que **não** está pronto

- `QualityGate` ainda **não** está fiado no `DailyPrescriptionEngine`/plano (C4-02/C4-04/C4-05).
- `VolumeReference`/`StrongRun` existem e a projeção está testada, mas `VolumeProgression`, `MesoCycle` e `LevelGraduation` ainda leem sessões planejadas; a fiação é C4-39. O golden `G-VOL-02` e os docs `R-PLAN-01 v2`/`R-LVL-03` ficam para C4-41/C4-42.
- `qualityEligibleByReadiness` já existe, mas o diário e o recomendador ainda não o leem; a fiação é C4-04/C4-29.
- A copy/UI de “em calibração” e o golden `G-LOAD-02` ficam para C4-26/C4-27; o domínio já expõe `acwrReliable`, `historyDays` e `baselineDays`.
- Copy/catálogo de `R-PER-03`/`R-READY-06` e os trechos de `SPORTS_SCIENCE` ligados a essas regras ficam para C4-06/C4-08/C4-33/C4-52.
- O check-in UI (C4-30), a reavaliação tardia (C4-31), o golden `G-READY-03`/copy (C4-32) e os docs de prontidão (C4-33) continuam pendentes.
- O golden `G-DATA-02` e os docs de sono (C4-37) continuam pendentes. O diagnóstico de sono é exposto no sync (`suspectSleepNights`/`fragmentedSleepNights`) e na mensagem do Health Connect; não há `ReadinessFlag.SLEEP_DATA_SUSPECT` porque a noite suspeita é persistida como `null` e o ADR 0049 não prevê migração.
- A persistência/JSON de `qualityKind`/`planDecision` fica para C4-45/C4-46 (schema v18); por enquanto os campos são `null` no round-trip.
- `role`/`structure` da oportunidade entram com o catálogo/estrutura (C4-14/C4-15); o read model atual cobre data, kind, motivo, status e decisão.
- O bump `weekly-plan-v6` → `weekly-plan-v7` e `daily-v6` fica para C4-53; nenhuma migração v18 foi escrita/testada; nenhuma UI de check-in foi alterada.
- `G-PER-03`, `G-PER-05`, `G-LOAD-02`, `G-READY-03`, `G-DATA-02`, `G-VOL-02` e `G-UPGRADE-01` ainda não existem.
- O novo título “Corrida mais longa” ainda não tem link de UI para Perfil/Disponibilidade (C4-51).

---

## 1. Como usar este documento

### 1.1 Ordem de leitura no início de cada sessão

1. `AGENTS.md` (regras do projeto).
2. A última entrada de `docs/PROGRESS.md` (o que a sessão anterior fez).
3. **Este documento**: §0 (estado), §2 (protocolo), a sessão atual no §5 e a task no §6.
4. O ADR/FR da task (links no §6).
5. Os testes existentes da área antes de escrever código.

### 1.2 Regra de ouro

**O estado manda.** Se o bloco §0 diz que C4-01 está `IN_PROGRESS`, continue de onde parou; não recomece. Se diz `DONE`, não refaça. Se o git mostrar outra coisa, pare e reconcilie o §0 com o git antes de codar.

### 1.3 O que atualizar no fim de **toda** sessão

1. §0 (estado, task atual, próxima, bloqueios, gates).
2. A linha da task no §6 (status, evidência, commit).
3. A entrada da sessão no §7 (o que foi feito, testes, gates, commit, próximo).
4. `docs/PROGRESS.md` (entrada da sessão, no padrão do projeto).
5. `docs/ROADMAP.md`, se a fase mudou.
6. `docs/DECISIONS.md`/ADR, se alguma decisão mudou.
7. Commit (ver §9).

### 1.4 O que **nunca** fazer

- Começar uma task cuja dependência não está `DONE` (a não ser que o §6 diga “paralela”).
- Marcar `DONE` sem os gates da task.
- “Consertar” teste reduzindo assert, cobertura ou removendo caso.
- Introduzir dado real de saúde no repo, em print, log ou prompt.
- Usar `fallbackToDestructiveMigration`.
- Alterar o PRD/ADR sem registrar a decisão.
- Deixar o repo vermelho sem registrar o comando e o estado exato.
- Fazer push (o fluxo é local; push só se o usuário pedir).
- Implementar UI antes do domínio/golden quando a task tem domínio.
- Abrir escopo novo (backend, LLM, nutrição, musculação, social, GPS nativo).
- Afrouxar doença, dor, prontidão vermelha, sono curto, taper ou recovery.

---

## 2. Protocolo de sessão

### 2.1 Abertura (5 min)

```
git status --short
git log -5 --oneline
```

- Se houver mudanças não commitadas de uma sessão anterior, leia o §7 para saber se são intencionais.
- Confirme que o §0 e o git contam a mesma história.
- Rode o teste focado da última task `IN_PROGRESS`, se houver:
  `./gradlew :core:domain:test --tests "*NomeDoTeste*"` ou
  `./gradlew :app:testDebugUnitTest --tests "*NomeDoTeste*"`.
- Se o teste falhar e a falha for a esperada do TDD, continue; se for outra, investigue antes.

### 2.2 Execução

1. Leia o ADR/FR e os testes existentes.
2. Escreva o teste que falha (RED).
3. Implemente o mínimo para passar (GREEN).
4. Refatore mantendo o teste.
5. Rode o teste focado e o módulo.
6. Rode `spotlessCheck` (ou `spotlessApply`).
7. Atualize docs da task (rule/evidence/ADR/SPORTS_SCIENCE, se aplicável).
8. Atualize §0, §6, §7 e `PROGRESS.md`.
9. Commit da task (se verde).

### 2.3 Encerramento

- Se a task terminou: marque `DONE`, registre a evidência e o commit; escolha a próxima.
- Se a sessão acabou no meio: marque `IN_PROGRESS`, registre **exatamente** onde parou (arquivo, teste, comando, saída), o que falta e o próximo passo; **não commite** se o gate estiver vermelho.
- Se bloqueou: marque `BLOCKED`, registre o bloqueio, a tentativa e o que precisa; escolha uma task paralela que não dependa dela.
- Se o escopo mudou: pare, registre a decisão no PRD/ADR/§8 e só depois continue.

### 2.4 Regra de tamanho

- Uma task por sessão por padrão.
- Tasks S podem ser agrupadas se os gates estiverem verdes e a sessão tiver folga.
- Tasks L/XL devem ser divididas em subpassos `C4-XXa`, `C4-XXb` no §6, com o mesmo aceite guarda-chuva. O PRD/ADR não muda; o checklist registra a divisão.

---

## 3. Mapa do ciclo

| Fase | Sessões | Objetivo | Gate de saída |
|---|---|---|---|
| 0 — Documentação | S0 | PRD + ADRs + checklist | Revisão do usuário; docs consistentes |
| 1 — Domínio | S1–S6 | Modelos, gates, longão, ACWR, prontidão/sono, volume, catálogo | `:core:domain:test` verde; testes novos |
| 2 — Fiação | S7–S10 | Gerador, prescrição diária, schema v18, upgrade | Goldens; migração; `:app:testDebugUnitTest` verde |
| 3 — UI/copy | S11–S15 | Hoje/Plano/Por quê, check-in, review, alvos | UI tests; a11y; copy |
| 4 — Fechamento | S16–S17 | `check` completo, goldens, device, docs | `check`/`koverVerify`/`assembleDebug`; smoke; `PROGRESS`/`ROADMAP` |

### 3.1 Caminho crítico

```
C4-01 → C4-03 → C4-02 → C4-04 → C4-05
C4-09 → C4-10 → C4-11 → C4-12
C4-22 → C4-23 → C4-24 → C4-25
C4-28 → C4-29 → C4-31
C4-45 → C4-46 → C4-47 → C4-48
C4-53 → C4-54 → C4-55 → C4-56
```

### 3.2 Dependências que costumam travar

- C4-45 (schema v18) depende de C4-14 (estrutura) e C4-38 (volume) para as colunas fazerem sentido. Se o schema for necessário antes, faça a migração com as colunas e preencha os modelos depois; registre a ordem no §7.
- C4-47 (PlanUpgrade) depende de C4-45/C4-46 e de C4-02/C4-04.
- C4-55 (device) depende de C4-54 (gates completos).
- C4-58 (pace/horizonte) é P2; não bloqueia o núcleo.

---

## 4. Plano de sessões

> O plano é uma recomendação. O §6 é a fonte de verdade; se uma task for maior, divida e registre.

| Sessão | Task(s) | Objetivo | Gate mínimo | Evidência |
|---|---|---|---|---|
| **S0** | — | PRD, ADRs 0044–0050, DECISIONS, checklist | Revisão manual; `git diff --check` | commits/docs |
| **S1** | C4-01, C4-03 | Modelos de oportunidade/gate; `QualityGate` puro | `:core:domain:test` focado; testes de precedência | commit + testes |
| **S2** | C4-09, C4-10, C4-11, C4-12, C4-13 | Long day, piso/share, `fitToVolume`, golden/docs | testes do gerador; `G-PER-04` | commit + golden |
| **S3** | C4-22..C4-25 | ACWR confiável, limites, progressão | testes de carga; consistência | commit + testes |
| **S4** | C4-28, C4-34..C4-36 | Prontidão, check-in, sono | testes de readiness/health | commit + testes |
| **S5** | C4-38, C4-39 | `VolumeReference`/`StrongRun`; meso/graduação | testes de volume | C4-38 concluída (`VolumeReferenceTest`, `VolumeReferenceProjectionTest`); C4-39 pendente |
| **S6** | C4-14..C4-17 | `WorkoutStructure`, catálogo, rotação, gerador | testes de catálogo/estrutura | commit + testes |
| **S7** | C4-02, C4-07 | Gerador estrutural + `G-PER-03` | `G-PER-03`; propriedades | commit + golden |
| **S8** | C4-04, C4-05, C4-29 | `QualityGate` no dia; reavaliação de domínio | testes do daily engine | commit + testes |
| **S9** | C4-45, C4-46 | Schema v18, JSON, `generatorVersion` | migração 17→18; round-trip | commit + migração |
| **S10** | C4-44, C4-47, C4-48, C4-49 | Gate de dados, upgrade, replanner | `G-UPGRADE-01`; idempotência | commit + golden |
| **S11** | C4-06, C4-30, C4-31, C4-50..C4-52, C4-08 | Copy, check-in UI, reavaliação tardia, decisão/catálogo | UI tests; catálogo | commit + testes |
| **S12** | C4-18, C4-19, C4-20, C4-21 | Strides/intro fartlek; estrutura no briefing; `G-PER-05`; docs | testes de iniciante; UI | commit + testes |
| **S13** | C4-40..C4-43 | Reparo LEGACY; crédito do treino extra; UI | `G-VOL-02`; testes de reparo | commit + golden |
| **S14** | C4-26, C4-27, C4-32, C4-33, C4-37 | UI/copy de carga, prontidão, sono; docs | goldens; UI/a11y; docs | commit + testes |
| **S15** | C4-57..C4-60 | Alvos pace×FC, horizonte/pace dinâmico, copy, `createdAt` | testes; copy | commit + testes |
| **S16** | C4-53, C4-54 | Versões, suíte completa, Kover, detekt, spotless | `check`/`koverVerify`/`assembleDebug` | commit + logs |
| **S17** | C4-55, C4-56 | Device, docs, fechamento | smoke; `PROGRESS`/`ROADMAP` | commit + checklist final |

---

## 5. Sessão S5 — C4-38 concluída; C4-39 pendente

> O caderno completo da S4 fica no §7. Esta sessão abriu a projeção de volume real; a fiação da decisão é a próxima task.

**Objetivo:** dar ao motor de volume uma projeção determinística do que o atleta realmente correu, separando volume forte de adesão ao plano, sem UI nem migração.

**Contexto obrigatório:**

- `docs/adr/0050-volume-credit-unplanned-activities.md` §2.1–2.5 e §6.
- `docs/PRD_TREINO_REAL.md` FR-408 e §5.6.
- `VolumeProgression`, `MesoCycle`, `LevelGraduationEngine`, `WeeklyPlanGenerator` e os testes de volume existentes.

**Task C4-38 — `VolumeReference` + builder**

- [x] `StrongRun` (data, duração, fonte, vínculo planejado) e `VolumeReference` (planejado, executado, forte, sessões, `createdOn`, semana especial) no domínio puro.
- [x] `VolumeReferenceBuilder.build(...)` agrega as corridas fortes da semana, preenche os metadados do plano e ignora atividades fora da semana.
- [x] `VolumeReferenceProjection` na borda (`:app`): RUNNING/TREADMILL importadas, vínculo 1:1 com sessão planejada, atividade manual/curta fora, ordem determinística e uma referência por semana planejada.
- [x] `SPECIAL_WEEK_RULE_IDS` unifica as regras de deload/taper/prova/recuperação usadas por `VolumeProgression` e `MesoCycle`.
- [x] 14 casos em `VolumeReferenceTest` e 8 em `VolumeReferenceProjectionTest`.
- [ ] C4-39: `VolumeProgression`/`MesoCycle`/`LevelGraduation` passam a consumir `VolumeReference`/`StrongRun`; adesão continua planejada. **Próxima sessão.**

**Aceite da sessão (C4-38):**

- [x] `./gradlew :core:domain:test` verde (887).
- [x] `:app:testDebugUnitTest` 890 verdes; `:core:data:test` 171; `:app:assembleDebug`.
- [x] `spotlessCheck`, `:core:domain:koverVerify`, `:core:domain:detekt` e `:app:detekt` verdes; FIT 93,01%.
- [x] Nenhum dado real; nenhuma migração; nenhuma UI alterada.
- [x] §0/§6/§7/PROGRESS atualizados.
- [x] Commit: `feat(domain): add real volume reference projection (C4-38)`.

**Notas de implementação:**

- A projeção recebe as atividades já sem lixeira (`listActivitySummaries`/`observeActivitySummaries`); atividade deletada continua fora na origem.
- Uma corrida com menos de 60 s arredonda para 0 min e não conta; o volume nunca é inflado.
- `executedMinutes` e `strongMinutes` são hoje o mesmo número (ADR 0050 §2.1); a distinção fica no modelo para evolução sem quebrar o contrato.
- O builder não decide se a semana é referência: `VolumeProgression` continuará filtrando `createdOn == weekStart`, semana não especial e sessões treináveis (C4-39).
- `G-VOL-02`, o rationale “treino extra contou” e os docs `R-PLAN-01 v2`/`R-LVL-03` ficam em C4-41/C4-42. Há uma inconsistência numérica no golden (114 + 28 + 33 → 127) a reconciliar antes de escrevê-lo.

**Se a sessão acabar no meio:** registre qual teste está vermelho, o comando exato, a saída e o próximo passo. Não marque `DONE`.

---

## 6. Task board

> **Status:** `[ ]` TODO · `[~]` IN_PROGRESS · `[x]` DONE · `[!]` BLOCKED · `[-]` DEFERRED.
> **Evidência:** commit, teste, golden ou decisão. Preencher ao concluir.
> **Detalhe:** PRD/ADR da coluna “Contexto”. O aceite abaixo é o mínimo; o FR/ADR tem o resto.

### E1 — Oportunidade de qualidade

| Status | ID | Task | Depende | Tam. | Sessão | Contexto | Aceite | Evidência |
|---|---|---|---|---|---|---|---|---|
| [x] | C4-01 | Modelos de oportunidade/gate | — | M | S1 | ADR 0044/FR-401 | Tipos puros, testes, sem UI | `QualityOpportunityTest` (15); commit S1 |
| [ ] | C4-02 | Separar gates estrutural × do dia no gerador | C4-01 | L | S7 | ADR 0044 | 3×40 gera 1 oportunidade; 2ª com progressão | — |
| [x] | C4-03 | `QualityGate` puro | C4-01 | L | S1 | ADR 0044 | Precedência completa; determinístico | `QualityGateTest` (27); commit S1 |
| [ ] | C4-04 | Daily engine usa `QualityGate` | C4-03 | M | S8 | ADR 0044 | Mantém/rebaixa com motivo; `R-PRESC-02` | — |
| [ ] | C4-05 | Reavaliação com check-in/carga antes do DONE | C4-04 | M | S8 | ADR 0044/0049 | EASY→QUALITY antes do DONE; DONE intacto | — |
| [ ] | C4-06 | Copy/rationale da oportunidade e rebaixamento | C4-03 | M | S11 | ADR 0044/FR-412 | Sem contradição; IDs catalogados | — |
| [ ] | C4-07 | Golden `G-PER-03` + propriedades | C4-02/03 | M | S7 | ADR 0044 | Golden e invariantes | — |
| [ ] | C4-08 | Docs `R-PER-03`, SPORTS_SCIENCE, DECISIONS | C4-02..07 | S | S11 | ADR 0044 | Docs atualizados | — |

### E2 — Longão

| Status | ID | Task | Depende | Tam. | Sessão | Contexto | Aceite | Evidência |
|---|---|---|---|---|---|---|---|---|
| [x] | C4-09 | Comparator do long day | — | S | S2 | ADR 0045 | Empate 3×40 → domingo | `LongRunSizingTest` (comparator); commit S2 |
| [x] | C4-10 | Piso/share/availability e título honesto | C4-09 | M | S2 | ADR 0045 | 3×40→40; 2×40+90→45; título | `LongRunSizingTest` (piso/título); commit S2 |
| [x] | C4-11 | `fitToVolume` preserva o piso | C4-10 | M | S2 | ADR 0045 | Longão nunca < fácil; dropa fácil | `LongRunSizingTest` (fit/minimal); commit S2 |
| [x] | C4-12 | Golden `G-PER-04` + atualizar teste legado | C4-10/11 | M | S2 | ADR 0045 | Golden; teste de 30→45 justificado | `G-PER-04`; legados atualizados; commit S2 |
| [x] | C4-13 | Docs `R-PLAN-03 v2`, SPORTS_SCIENCE, ADR | C4-10..12 | S | S2 | ADR 0045 | Docs atualizados | `SPORTS_SCIENCE` §8.5/§8.8; DECISIONS r1.43; commit S2 |

### E3 — Catálogo/periodização

| Status | ID | Task | Depende | Tam. | Sessão | Contexto | Aceite | Evidência |
|---|---|---|---|---|---|---|---|---|
| [ ] | C4-14 | `WorkoutStructure`/`WorkoutBlock` + JSON | C4-01 | L | S6 | ADR 0047/0048 | Round-trip; retrocompatível | — |
| [ ] | C4-15 | `QualityKind` + matriz da `SessionLibrary` | C4-14 | XL | S6 | ADR 0047 | Matriz fase/nível/distância | — |
| [ ] | C4-16 | Rotação determinística e 2 kinds | C4-15 | M | S6 | ADR 0047 | Duas semanas não repetem | — |
| [ ] | C4-17 | Gerador usa estrutura/kind; PEAK específico | C4-15/16 | L | S6 | ADR 0047 | PEAK ≠ BUILD; testes | — |
| [ ] | C4-18 | Strides para todos + intro fartlek do iniciante | C4-15 | M | S12 | ADR 0047 | Iniciante EASY; qualidade 0 | — |
| [ ] | C4-19 | Briefing/UI da estrutura | C4-14/17 | M | S12 | ADR 0047 | Não promete mais tempo | — |
| [ ] | C4-20 | Golden `G-PER-05` + matriz + guarda | C4-15..19 | M | S12 | ADR 0047 | Golden; iniciante sem QUALITY | — |
| [ ] | C4-21 | Docs `R-PER-04`, SPORTS_SCIENCE, evidências | C4-15..20 | S | S12 | ADR 0047 | Docs/IDs atualizados | — |

### E4 — Carga/ACWR v3

| Status | ID | Task | Depende | Tam. | Sessão | Contexto | Aceite | Evidência |
|---|---|---|---|---|---|---|---|---|
| [x] | C4-22 | `firstActivityAt`; pré-história como “sem dado” | — | M | S3 | ADR 0046 | Cold start não é descanso | `LoadReliabilityTest` (7); commit S3 |
| [x] | C4-23 | `acwrReliable`/`strainReliable`/`historyDays` | C4-22 | M | S3 | ADR 0046 | Flags e testes de janela | `LoadReliabilityTest`; `LoadGateV3Test`; commit S3 |
| [x] | C4-24 | ACWR 1,5 em todos os caminhos | C4-23 | S | S3 | ADR 0046 | Nenhum 1,3 como veto | `LoadSnapshot.ACWR_MAX`; testes diário/gerador; commit S3 |
| [x] | C4-25 | `permitsQuality`/`permitsProgression` separados | C4-23/24 | M | S3 | ADR 0046 | 1,4 mantém; 1,6 rebaixa; 0,7 mantém | `LoadGateV3Test` (10); commit S3 |
| [ ] | C4-26 | Golden `G-LOAD-02` + copy “em calibração” | C4-23..25 | M | S14 | ADR 0046 | Golden/copy | — |
| [ ] | C4-27 | Docs `R-LOAD-03 v3`, SPORTS_SCIENCE, ADR | C4-22..26 | S | S14 | ADR 0046 | Docs atualizados | — |

### E5 — Prontidão/check-in

| Status | ID | Task | Depende | Tam. | Sessão | Contexto | Aceite | Evidência |
|---|---|---|---|---|---|---|---|---|
| [x] | C4-28 | `qualityEligibleByReadiness` + `SUBJECTIVE_MISSING` | — | M | S4 | ADR 0049 | Sem check-in = falso | `ReadinessEligibilityTest` (8); golden `readiness-no-data`; commit S4 |
| [ ] | C4-29 | Daily/recommender usam o gate | C4-28 | M | S8 | ADR 0044/0049 | Sem qualidade sem check-in | — |
| [ ] | C4-30 | Check-in sem 3/3/3/3; labels; pré-preencher | C4-28 | M | S11 | ADR 0049 | UI; salvar desabilitado | — |
| [ ] | C4-31 | Reavaliação após check-in | C4-05/30 | M | S11 | ADR 0049 | EASY→QUALITY antes do DONE | — |
| [ ] | C4-32 | Golden `G-READY-03` + copy | C4-28..31 | M | S14 | ADR 0049 | Golden | — |
| [ ] | C4-33 | Docs `R-READY-06`, SPORTS_SCIENCE, ADR | C4-28..32 | S | S14 | ADR 0049 | Docs | — |

### E6 — Sono

| Status | ID | Task | Depende | Tam. | Sessão | Contexto | Aceite | Evidência |
|---|---|---|---|---|---|---|---|---|
| [x] | C4-34 | `SleepNightValidator` | — | M | S4 | ADR 0049 | Janela/merge/180–840 | `SleepNightValidatorTest` (12); commit S4 |
| [x] | C4-35 | Agregador usa o validador; suspeita → null | C4-34 | M | S4 | ADR 0049 | Testes; diagnóstico | `HealthMetricsAggregatorTest` (5 novos); sync/UI; commit S4 |
| [x] | C4-36 | Readiness/review ignoram suspeita | C4-35 | M | S4 | ADR 0049 | Sem SHORT_SLEEP falso | `SleepPlausibilityTest` (4); commit S4 |
| [ ] | C4-37 | Golden `G-DATA-02` + docs | C4-34..36 | S | S14 | ADR 0049 | Golden/docs | — |

### E7 — Volume/treino extra

| Status | ID | Task | Depende | Tam. | Sessão | Contexto | Aceite | Evidência |
|---|---|---|---|---|---|---|---|---|
| [x] | C4-38 | `VolumeReference` + builder | C4-22 | L | S5 | ADR 0050 | Projeção determinística | `VolumeReferenceTest` (14); `VolumeReferenceProjectionTest` (8); commit S5 |
| [ ] | C4-39 | Volume/meso/graduação usam referência real | C4-38 | L | S5 | ADR 0050 | Base real; adesão separada | — |
| [ ] | C4-40 | Reparo LEGACY do mesmo dia | C4-39 | M | S13 | ADR 0050 | Confirmação; migrado intacto | — |
| [ ] | C4-41 | Golden `G-VOL-02` + copy | C4-38..40 | M | S13 | ADR 0050 | Golden/copy | — |
| [ ] | C4-42 | Docs `R-PLAN-01 v2`, `R-LVL-03`, ADR | C4-38..41 | S | S13 | ADR 0050 | Docs | — |
| [ ] | C4-43 | Review “planejada/mantida/rebaixada” | C4-39 | S | S13 | ADR 0050 | UI/teste | — |

### E8 — Dados/upgrade/schema

| Status | ID | Task | Depende | Tam. | Sessão | Contexto | Aceite | Evidência |
|---|---|---|---|---|---|---|---|---|
| [ ] | C4-44 | `HealthSyncState` + gate | — | M | S10 | ADR 0048 | Sem plano antes do sync | — |
| [ ] | C4-45 | Schema v18 + migração | C4-14/38 | L | S9 | ADR 0048 | 17→18 testada; sem perda | — |
| [ ] | C4-46 | `generatorVersion` no plano | C4-45 | M | S9 | ADR 0048 | Round-trip; default v6 | — |
| [ ] | C4-47 | `PlanUpgrade` idempotente | C4-44/46 | L | S10 | ADR 0048 | `G-UPGRADE-01`; histórico | — |
| [ ] | C4-48 | Triggers `DATA_READY`/`ENGINE_UPGRADE` | C4-47 | M | S10 | ADR 0048 | Testes; sem trigger de readiness | — |
| [ ] | C4-49 | Docs ADR 0048, ARCHITECTURE, DATA_MODEL | C4-44..48 | S | S10 | ADR 0048 | Docs | — |

### E9 — Explicabilidade/copy/rollout

| Status | ID | Task | Depende | Tam. | Sessão | Contexto | Aceite | Evidência |
|---|---|---|---|---|---|---|---|---|
| [ ] | C4-50 | `QualityBlockReason`/`PlanDecisionReason` + persistência | E1–E8 | L | S11 | ADR 0044/0048 | Serialização testada | — |
| [ ] | C4-51 | Copy por gate + “Por quê?” + dica | C4-50 | M | S11 | FR-412 | UI; catálogo | — |
| [ ] | C4-52 | Catálogo de regras/evidências + teste | C4-50/51 | S | S11 | ADR 0044 | Todos os IDs | — |
| [ ] | C4-53 | Versões de motor (`weekly-v7`, `daily-v6`) | C4-47 | S | S16 | ADR 0048 | Testes | — |
| [ ] | C4-54 | Suíte completa/goldens/Kover/detekt/spotless | C4-01..53 | XL | S16 | TESTING | Gates verdes | — |
| [ ] | C4-55 | Smoke no aparelho (sintético + real read-only) | C4-54 | M | S17 | TESTING | Checklist | — |
| [ ] | C4-56 | Docs `PROGRESS`, `ROADMAP`, `DECISIONS`, `TESTING` | C4-54/55 | S | S17 | AGENTS | Docs | — |

### E10 — Alvos/horizonte/higiene

| Status | ID | Task | Depende | Tam. | Sessão | Contexto | Aceite | Evidência |
|---|---|---|---|---|---|---|---|---|
| [ ] | C4-57 | `TargetKind`/precedência FC×pace | E3/E9 | M | S15 | FR-410 | Testes; copy | — |
| [ ] | C4-58 | Horizonte 5K/base + pace dinâmico | E3/E9 | L | S15 | FR-411 | Golden; sem regressão | — |
| [ ] | C4-59 | Copy contraditória do rationale | E9 | S | S15 | FR-412 | Teste de copy | — |
| [ ] | C4-60 | `createdAt` estável no `saveWeek` | E8 | S | S15 | FR-413 | Teste de repositório | — |

---

## 7. Log de sessões

> **Formato:** copie o bloco, preencha no fim da sessão e não apague as anteriores. A entrada mais recente fica no topo ou no fim? **No fim**, em ordem cronológica; o §0 aponta para a atual.

### S0 — 2026-11-16 — Documentação, PRD e ADRs

- **Status:** DONE
- **Tasks:** PRD v1.1, ADRs 0044–0050, DECISIONS r1.42, este checklist.
- **Arquivos:** `docs/PRD_TREINO_REAL.md`, `docs/adr/0044..0050`, `docs/DECISIONS.md`, `docs/CICLO_4_TREINO_REAL.md`.
- **Testes/gates:** não executados (só docs); revisão manual; `git diff --check`.
- **Commit:** pendente — `docs(c4): approve PRD, ADRs 0044-0050 and execution checklist`.
- **Decisões:** 12/12 aprovadas; iniciante = strides + intro fartlek `EASY`; pace dinâmico.
- **Próximo:** S1 / C4-01.

### S1 — 2026-11-16 — Oportunidade de qualidade + `QualityGate` puro

- **Status:** DONE
- **Tasks:** C4-01, C4-03.
- **Arquivos:** `core/domain/.../prescription/QualityOpportunity.kt` (novo), `QualityGate.kt` (novo), `SessionLibrary.kt`, `WeeklyPlanGenerator.kt`, `QualityOpportunityTest.kt`, `QualityGateTest.kt`.
- **Testes/gates:** `:core:domain:test` 816 verdes (774 + 42 novos); `:core:data:test` 168; `:app:testDebugUnitTest` 881; `:app:assembleDebug`; `spotlessCheck`; `:core:domain:koverVerify`; `:core:domain:detekt`; parser FIT 93,01%.
- **Commit:** `feat(domain): add quality opportunity and quality gate (C4-01, C4-03)`.
- **Notas/bloqueios:** gate ainda não fiado (C4-04); âmbar fica para `qualityEligibleByReadiness` (C4-28/29); confiabilidade de ACWR é provisória até C4-23; micro-dose de taper não é bloqueada pelo taper em si; nenhuma UI/schema/migração.
- **Próximo:** S2 / C4-09 (comparator do long day), TDD sobre `WeeklyPlanGenerator`.

### S2 — 2026-11-16 — Longão de verdade: piso, título honesto e `fitToVolume`

- **Status:** DONE
- **Tasks:** C4-09, C4-10, C4-11, C4-12, C4-13.
- **Arquivos:** `core/domain/.../prescription/WeeklyPlanGenerator.kt`, `SessionLibrary.kt`, `ExplanationCatalog.kt`, `core/domain/.../replan/PlanReplanner.kt`, `LongRunSizingTest.kt` (novo), `WeeklyPlanGeneratorTest.kt`, `PlanReplannerTest.kt`, `TodayViewModelPersistTest.kt`, `docs/SPORTS_SCIENCE.md`, `docs/adr/0045-long-run-sizing.md`, `docs/DECISIONS.md`, `docs/PROGRESS.md`, este checklist.
- **Testes/gates:** `:core:domain:test` 825 verdes (816 + 9 novos); `:core:data:test` 168; `:app:testDebugUnitTest` 881; `:app:assembleDebug`; `spotlessCheck`; `:core:domain:koverVerify`; `:core:domain:detekt`.
- **Commit:** `feat(domain): size the long run with a floor and honest title (C4-09..C4-13)`.
- **Decisões/notas:** o comparador usa disponibilidade > sáb/dom > sexta > dia mais tarde; a tabela do ADR 0045 §2.5 foi corrigida (4×60 com sábado e domingo → domingo). O piso vence o share em semanas pequenas; o share pode passar de 30% e o rationale diz que é alvo, não teto. O replanner mantém a guarda em T1/T2/T3; aceita a correção estrutural do longão legado até o piso e, em T4, a nova disponibilidade que move/cria o longão, sem crescer as demais sessões futuras. Bump `weekly-plan-v7` fica em C4-53; persistência de `planDecision` em C4-45/C4-46; link de disponibilidade na UI em C4-51.
- **Próximo:** S3 / C4-22 (`firstActivityAt`; pré-história como “sem dado”), TDD sobre `TrainingLoadEngine`/`TrainingLoadCalculator`.

### S3 — 2026-11-16 — Carga confiável: janela de 28 dias e ACWR 1,5

- **Status:** DONE
- **Tasks:** C4-22, C4-23, C4-24, C4-25.
- **Arquivos:** `core/domain/.../load/TrainingLoadEngine.kt`, `core/domain/.../prescription/DailyPrescriptionEngine.kt`, `QualityGate.kt`, `WeeklyPlanGenerator.kt`, `VolumeProgression.kt`, `WorkoutRecommender.kt`, `app/.../application/TrainingSignals.kt`, `app/.../ui/today/TodayViewModel.kt`, `LoadReliabilityTest.kt` (novo), `LoadGateV3Test.kt` (renomeado), testes de diário/gerador/volume/recomendador, `TodayViewModelPersistTest.kt`, `docs/SPORTS_SCIENCE.md`, `docs/adr/0046-load-gate-v3-acwr-reliability.md`, `docs/PROGRESS.md`, este checklist.
- **Testes/gates:** `:core:domain:test` 844 verdes (+19 líquidos; `LoadReliabilityTest` 7, `LoadGateV3Test` 10, limites no diário/gerador/recomendador); `:core:data:test` 168; `:app:testDebugUnitTest` 881; `:app:assembleDebug`; `spotlessCheck`; `:core:domain:koverVerify`; FIT 93,01% de branches; `detekt` verde.
- **Commit:** `feat(domain): make ACWR reliable on a full 28-day window (C4-22..C4-25)`.
- **Decisões/notas:** pré-história vira `daysWithoutData` e `daysBeforeHistory`; a fonte pode informar `firstActivityAt` e o engine deriva do registro mais antigo quando ela não informa. `acwrReliable` exige 28 dias cobertos, ≥8 dias de carga objetiva, fonte fresca e zero dia subjetivo; `strainReliable` herda a mesma janela. `permitsQuality` mantém a qualidade em ACWR não confiável; `permitsProgression` exige janela completa e 0,8–1,3. `LoadSnapshot.ACWR_MAX = 1.5` é o único limite de redução; 1,3 fica só como topo da faixa de progressão. `VolumeGrowthBlock.ACWR_UNRELIABLE` separa calibração de “fora da faixa”. O seed de `TodayViewModelPersistTest` passou a cobrir 28 dias para a progressão continuar válida. Golden/copy/UI e bump de versões ficam em C4-26/C4-27/C4-53.
- **Próximo:** S4 / C4-28 (`qualityEligibleByReadiness` + `SUBJECTIVE_MISSING`).

### S4 — 2026-11-16 — Prontidão sem inversão e sono plausível

- **Status:** DONE
- **Tasks:** C4-28, C4-34, C4-35, C4-36.
- **Arquivos:** `core/domain/.../readiness/ReadinessModels.kt`, `ReadinessCalculator.kt`, `core/domain/.../health/SleepNightValidator.kt` (novo), `HealthMetricsAggregator.kt`, `core/data/.../health/HealthConnectDailySource.kt`, `HealthSyncService.kt`, `app/.../health/HealthSyncCoordinator.kt`, `app/.../ui/metrics/HealthConnectViewModel.kt`, `ReadinessEligibilityTest.kt` (novo), `SleepNightValidatorTest.kt` (novo), `SleepPlausibilityTest.kt` (novo), `HealthMetricsAggregatorTest.kt`, `HealthSyncServiceTest.kt`, `HealthConnectViewModelTest.kt`, `ReadinessCalculatorTest.kt`, `fixtures/golden/readiness-no-data/`, `docs/adr/0049-readiness-checkin-sleep.md`, `docs/PROGRESS.md`, este checklist.
- **Testes/gates:** `:core:domain:test` 873 verdes (+29; `ReadinessEligibilityTest` 8, `SleepNightValidatorTest` 12, `SleepPlausibilityTest` 4, agregador 5); `:core:data:test` 171 (+3); `:app:testDebugUnitTest` 882 (+1); `:app:assembleDebug`; `spotlessCheck`; `:core:domain:koverVerify`; FIT 93,01% de branches; `detekt` verde.
- **Commit:** `feat(domain): block quality without check-in and implausible sleep (C4-28, C4-34..C4-36)`.
- **Decisões/notas:** sem check-in, `SUBJECTIVE_MISSING` é explícito e `qualityEligibleByReadiness=false`, mas o score e o nível continuam exibíveis (dados parciais) e `NO_QUALITY` não é ligado. O validador de sono usa a janela local 18:00–12:00, agrupa blocos com gap ≤120 min e soma o sono real (não o span): 4h + 4h com 30 min de intervalo = 8h. Noite fragmentada usa o maior bloco; 36 min vira `null`, nunca `SHORT_SLEEP`. O diagnóstico de noites suspeitas/fragmentadas chega ao estado do sync e à mensagem do Health Connect, sem valor bruto. `SLEEP_DATA_SUSPECT` não é emitido porque a noite é persistida como `null` e não há migração. Check-in UI, reavaliação tardia, `G-READY-03`, `G-DATA-02` e docs de `R-READY-06`/sono ficam em C4-30..C4-33/C4-37.
- **Próximo:** S5 / C4-38 (`VolumeReference` + builder), TDD da projeção determinística.

### S5 — 2026-11-16 — Projeção de volume real (C4-38)

- **Status:** PARTIAL — C4-38 DONE; C4-39 TODO.
- **Tasks:** C4-38.
- **Arquivos:** `core/domain/.../prescription/VolumeReference.kt` (novo), `VolumeProgression.kt`, `MesoCycle.kt`, `app/.../application/VolumeReferenceProjection.kt` (novo), `VolumeReferenceTest.kt` (novo), `VolumeReferenceProjectionTest.kt` (novo), este checklist, `docs/PROGRESS.md`.
- **Testes/gates:** `:core:domain:test` 887 verdes (+14; `VolumeReferenceTest`); `:app:testDebugUnitTest` 890 (+8; `VolumeReferenceProjectionTest`); `:core:data:test` 171; `:app:assembleDebug`; `spotlessCheck`; `:core:domain:koverVerify`; `:core:domain:detekt`; `:app:detekt`; FIT 93,01% de branches. `VolumeReference.kt`: 100% de linhas, 97,2% de branches.
- **Commit:** `feat(domain): add real volume reference projection (C4-38)`.
- **Decisões/notas:** `StrongRun` só aceita fonte importada (MANUAL nunca); `VolumeReference` carrega planejado/executado/forte/sessões/`createdOn`/semana especial. O builder é puro, ignora corridas fora da semana e não decide a usabilidade da referência. A projeção no `:app` usa as atividades já sem lixeira, converte minutos por piso (nunca infla), vincula `plannedSessionId` pela sessão que possui o `activityId` e ordena de forma determinística; uma referência por semana planejada. `SPECIAL_WEEK_RULE_IDS` elimina a duplicação entre `VolumeProgression` e `MesoCycle`. Nenhuma UI, schema ou migração. A inconsistência numérica do `G-VOL-02` (114 + 28 + 33 → 127) fica registrada para C4-41.
- **Próximo:** C4-39 — `VolumeProgression`/`MesoCycle`/`LevelGraduation` usando `VolumeReference`/`StrongRun`, com adesão separada e longão real.

---

## 8. Decisões e ADRs

| # | Decisão | ADR |
|---|---|---|
| Q-01 | Oportunidade de qualidade estrutural; o dia confirma | 0044 |
| Q-02 | Check-in ausente bloqueia qualidade; sem default 3/3/3/3 | 0049 |
| Q-03 | Treino extra conta volume/longão, não adesão | 0050 |
| Q-04 | Longão 45/60; título honesto | 0045 |
| Q-05 | Iniciante: strides + intro fartlek `EASY`; intermediário: intervalado/fartlek/colinas | 0047 |
| Q-06 | ACWR 1,5 + confiabilidade 28 dias | 0046 |
| Q-07 | 8 semanas específicas + qualidade no geral | 0047/0048 |
| Q-08 | Pace atual recalculado; meta é referência; gap | 0047 |
| Q-09 | LEGACY reparável só no mesmo dia | 0050 |
| Q-10 | Schema v18 + PlanUpgrade | 0048 |
| Q-11 | Sem override manual | 0044 |
| Q-12 | 2 qualidades com progressão | 0044/0047 |

**Novas evidências propostas:** `EV-TID-2021`, `EV-INJURY-2015`, `EV-HIIT-2016`, `EV-HIIT-2024` (ADR 0047/PRD §11.5).

---

## 9. Commits

### 9.1 Convenção

```
<type>(<scope>): <resumo> (<task-id>)
```

- **type:** `feat`, `fix`, `test`, `docs`, `refactor`, `chore`.
- **scope:** `domain`, `data`, `app`, `docs`.
- **task-id:** C4-XX; se a sessão fecha duas tasks, cite as duas.
- **Exemplos:**
  - `docs(c4): approve PRD, ADRs 0044-0050 and execution checklist`
  - `feat(domain): add quality opportunity and gate (C4-01, C4-03)`
  - `test(domain): cover quality gate precedence (C4-03)`
  - `feat(data): add schema v18 and plan upgrade (C4-45, C4-47)`

### 9.2 Regras

- Um commit por task concluída; se a sessão concluir duas tasks pequenas, um commit com as duas IDs é aceitável.
- **Não commitar** com gate vermelho. Se a sessão acabar no meio, deixe o estado registrado no §0/§7 e não faça commit (ou um `wip` só se o usuário autorizar).
- **Não fazer push** sem pedido explícito.
- O commit deve conter só os arquivos da task; não misturar refatorações não relacionadas.
- A mensagem cita a task e o ADR quando a decisão for relevante.

---

## 10. Gates e comandos

### 10.1 Por sessão

```bash
./gradlew :core:domain:test --tests "*<Teste>*"
./gradlew spotlessCheck
```

Se a task toca `:app`/`:data`:

```bash
./gradlew :app:testDebugUnitTest --tests "*<Teste>*"
./gradlew :core:data:test
```

### 10.2 Antes de fechar uma fase (S16)

```bash
./gradlew check
./gradlew :app:assembleDebug
./gradlew koverVerify
./gradlew spotlessCheck
```

- `check` pode ser lento; se houver flake de carga, rodar isolado/`--rerun-tasks` e registrar. **Não** ignorar flake.
- `koverVerify`: `core:domain` ≥85% de linhas; os novos algoritmos devem ter ≥95% de branches (meta, não gate global).
- `detekt`/lint sem erros novos; baseline só com dívida real.
- Nenhum `@Ignore` novo.

### 10.3 Contagens de referência (16/11/2026)

| Módulo | Testes | Observação |
|---|---:|---|
| `:app` | 890 | Atualizar conforme novos testes |
| `:core:domain` | 887 | Atualizar conforme novos testes |
| `:core:data` | 171 | Atualizar |
| `:core:model` | 40 | Atualizar |

Os números são referência; o gate é “todos verdes”, não o número exato.

### 10.4 Privacidade

- Nenhum `.fit`, `.tcx`, `.gpx`, `.csv` pessoal, print, export ou banco real no repo/CI/logs.
- Fixtures só sintéticas ou sanitizadas.
- O smoke no aparelho é leitura; não copiar dados para o repo.

---

## 11. Mapa de arquivos

| Área | Arquivos principais |
|---|---|
| Prescrição/plano | `core/domain/src/main/kotlin/com/meucoach/core/domain/prescription/WeeklyPlanGenerator.kt`, `DailyPrescriptionEngine.kt`, `SessionLibrary.kt`, `VolumeProgression.kt`, `MesoCycle.kt`, `LevelGraduationEngine.kt`, `PrescriptionTargets.kt`, `SessionSwapper.kt` |
| Carga | `core/domain/.../load/TrainingLoadEngine.kt`, `TrainingLoadCalculator.kt` |
| Prontidão | `core/domain/.../readiness/ReadinessCalculator.kt`, `DailyMetricsReadinessBuilder.kt`, `ReadinessModels.kt` |
| Saúde/sono | `core/domain/.../health/HealthMetricsAggregator.kt`, `SleepNightValidator.kt` |
| Replanejamento | `core/domain/.../replan/PlanReplanner.kt`, `PlanningFingerprint.kt`, `ReplanModels.kt` |
| Persistência | `core/data/.../db/MeuCoachDatabase.kt`, `db/entity/`, `repository/TrainingPlanRepository.kt`, `prescription/PlanJson.kt`, `PrescriptionJson.kt` |
| App | `app/src/main/kotlin/com/meucoach/app/ui/today/TodayViewModel.kt`, `application/GetTodayPrescription.kt`, `ui/checkin/CheckInViewModel.kt`, `ui/checkin/CheckInScreen.kt`, `health/HealthSyncCoordinator.kt`, `MeuCoachApp.kt` |
| Testes | `core/domain/src/test/...`, `core/data/src/test/...`, `app/src/test/...` |
| Docs | `docs/PRD_TREINO_REAL.md`, `docs/adr/0044..0050`, `docs/CICLO_4_TREINO_REAL.md`, `docs/SPORTS_SCIENCE.md`, `docs/PROGRESS.md`, `docs/ROADMAP.md`, `docs/DECISIONS.md`, `docs/DATA_MODEL.md`, `docs/ARCHITECTURE.md`, `docs/TESTING.md` |

---

## 12. Definition of Done

### 12.1 Task

- [ ] Código compila.
- [ ] Teste que falhava antes passa; regressão coberta.
- [ ] Teste focado e módulo verdes.
- [ ] `spotlessCheck` verde.
- [ ] `koverVerify` verde quando aplicável.
- [ ] Lint/detekt sem erro novo.
- [ ] Regra/evidência/confiança emitidas quando a task prescribe algo.
- [ ] Docs da task atualizados.
- [ ] Nenhum dado real no diff.
- [ ] Nenhum TODO crítico escondido.
- [ ] §0/§6/§7/PROGRESS atualizados.
- [ ] Commit com a task ID.

### 12.2 Fase

- [ ] `./gradlew check` verde.
- [ ] `./gradlew :app:assembleDebug` verde.
- [ ] `./gradlew koverVerify` verde.
- [ ] `spotlessCheck` verde.
- [ ] Goldens da fase verdes.
- [ ] Docs da fase atualizados.
- [ ] Nenhum flake sem registro.
- [ ] §0 aponta para a próxima fase.

### 12.3 Ciclo

- [ ] Todos os P0/P1 concluídos; P2 avaliados.
- [ ] Goldens `G-PER-03/04/05`, `G-LOAD-02`, `G-READY-03`, `G-DATA-02`, `G-VOL-02`, `G-UPGRADE-01` verdes.
- [ ] Migração 17→18 testada.
- [ ] Smoke no aparelho (sintético + leitura real).
- [ ] O usuário consegue explicar a decisão de qualidade no “Por quê?”.
- [ ] `PROGRESS`/`ROADMAP`/`DECISIONS`/`SPORTS_SCIENCE`/`ARCHITECTURE`/`DATA_MODEL`/`TESTING` atualizados.
- [ ] Nenhum “Longão 34” com fácil 40.
- [ ] Nenhuma qualidade vetada por ACWR não confiável.
- [ ] Nenhuma qualidade liberada sem check-in.
- [ ] Nenhuma noite de 36 min tratada como curta.
- [ ] Nenhum dado real no repo.

---

## 13. Riscos e rollback

| Risco | Mitigação |
|---|---|
| Task maior que a sessão | Dividir em `C4-XXa/b`; registrar no §6; não mudar o PRD/ADR |
| Gate vermelho no fim | Não commitar; registrar comando/saída; retomar no próximo |
| Migração falha | Reverter a migração/schema; não deixar banco meio migrado; testar 17→18 |
| Teste legado fixa comportamento antigo | Atualizar com justificativa no commit/ADR; nunca reduzir assert |
| Flake | Isolar, reproduzir, corrigir; se não for da task, registrar e seguir com task paralela |
| Escopo novo | Parar, atualizar PRD/ADR/§8, só depois codar |
| Dado real | Remover antes do commit; usar fixture sintética |
| Rollback de app | v18 não abre em versão antiga sem migração reversa; exportar JSON antes; reinstalar/limpar se necessário |
| `PlanUpgrade` falha | Plano v6 continua válido; tentar na próxima abertura; não deixar transação parcial |

---

## 14. Próxima ação (C4-39, continuação da S5)

1. Abrir `docs/adr/0050-volume-credit-unplanned-activities.md` §2.2–2.3 e o FR-408/§5.6 do PRD.
2. Ler `VolumeProgression`, `WeeklyPlanGenerator`, `MesoCycle`, `LevelGraduationEngine`, `LevelGraduationContextBuilder`, `TodayViewModel`/`ReplanContextBuilder` e os testes de volume existentes.
3. Escrever os testes que falham de C4-39: base = `max(strongMinutes)`; semana sem plano/especial não é referência; adesão continua `strongSessions`/`plannedSessions` planejada; meso usa `strongSessions`; longão real 60/90 na graduação.
4. Implementar C4-39 com TDD, rodar `:core:domain:test` + `:core:data:test` + `:app:testDebugUnitTest` + `spotlessCheck`, atualizar §0/§6/§7/PROGRESS, commitar.

> **Regra final:** se você (agente) está em dúvida entre “seguir o plano” e “inventar”, siga o plano e registre a dúvida no §7. O trabalho final depende de continuidade, não de velocidade.
