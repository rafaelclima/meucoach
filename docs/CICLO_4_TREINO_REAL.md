# Ciclo 4 — Treino real: checklist de execução

**Versão:** 1.0 · **Data:** 16/11/2026
**Status:** S0 a S7 concluídas; **C4-04 (S8) é a próxima task**.
**Fonte de verdade:** `docs/PRD_TREINO_REAL.md` v1.1 + ADRs 0044–0050.
**Uso:** **este é o primeiro arquivo a abrir em cada sessão.** O bloco “Estado atual” manda; se ele divergir do git, o git vence e o bloco deve ser corrigido antes de qualquer código.

---

## 0. Estado atual

> **Última atualização:** 16/11/2026 — S7 (C4-02, C4-07)
> **Último commit:** C4-02/C4-07 — `feat(domain): separate structural and day quality gates (C4-02, C4-07)`
> **Fase:** 2 — Fiação — **em andamento (S7 concluída)**; próxima S8
> **Sessão atual:** **S7 concluída — C4-02 e C4-07 DONE**
> **Task atual:** C4-02, C4-07 (DONE)
> **Próxima task:** C4-04 (S8)
> **Bloqueios:** nenhum
> **Gates da última sessão:** `:core:domain:test` 975 verdes; `:core:data:test` 186; `:app:testDebugUnitTest` 901; `:core:model` 40; `spotlessCheck`; `:core:domain:koverVerify`; `:core:domain:detekt`; `:app:detekt`; `:app:lintDebug`; `:app:assembleDebug`; `./gradlew check`
> **Próxima ação:** abrir C4-04 (Daily engine usa `QualityGate`: mantém/rebaixa com motivo e `R-PRESC-02`), TDD sobre `DailyPrescriptionEngine`.

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

**S5 — volume real**

- [x] `StrongRun` e `VolumeReference` no domínio puro, com validações e `hasPlan`.
- [x] `VolumeReferenceBuilder.build(...)`: agrega as atividades fortes da semana (`strongMinutes`/`strongSessions`), preenche `plannedMinutes`, `plannedSessions`, `createdOn` e `specialWeek`, e ignora corridas fora da semana.
- [x] `VolumeReferenceProjection` na borda (`:app`): converte `ActivitySummary` RUNNING/TREADMILL importadas em `StrongRun`, vincula o `plannedSessionId` quando a atividade está ligada a uma sessão e devolve uma referência por semana planejada; ordem determinística; atividade manual/curta não conta.
- [x] 14 testes em `VolumeReferenceTest` e 8 em `VolumeReferenceProjectionTest`; `SPECIAL_WEEK_RULE_IDS` unifica a lista usada por `VolumeProgression`/`MesoCycle`.
- [x] C4-39: `VolumeProgression` usa `strongMinutes` como base e adesão planejada; `MesoCycle` usa `strongSessions`; `LevelGraduation` usa `StrongRun` real (60/90). `TodayViewModel`/`TutorialViewModel`/`ReplanContextBuilder`/`LevelGraduationContextBuilder` passam a projeção; fallback legado só para `null` em testes/callers antigos.

**S5.1 — reparo LEGACY (C4-40)**

- [x] `SessionCompletionRules.checkLegacyRepair(...)`: só `DONE` + `LEGACY_UNVERIFIED`, sem `activityId`/`executionId`, no mesmo dia, corrida do mesmo dia e confirmação explícita; histórico antigo bloqueado.
- [x] `TrainingPlanDao.repairLegacyEvidence` + `SessionExecutionRepository.repairLegacyWithActivity(...)`: cria a execução ausente, grava `activityId`, troca a evidência para `ACTIVITY`, mantém `DONE` e o `completedAt` original; vínculo 1:1 pelos índices.
- [x] `RepairLegacyCompletion` no `:app` e `ImportedRunPromptViewModel`/diálogo: o prompt de import oferece “Corrigir registro” para `MANUAL` e `LEGACY`; confirmação explícita; o histórico migrado fica intocado.
- [x] 7 testes de regra, 4 de repositório, 7 do caso de uso e 4 de prompt/UI; `OfferImportedRunTest` cobre a fila do legado.

**S6 — estrutura executável (C4-14)**

- [x] `WorkoutStructure`/`WorkoutBlock`/`BlockKind` no domínio puro; `totalMin` nunca menor que aquecimento + blocos + recuperações + volta à calma.
- [x] Recuperação contada depois de toda repetição (inclusive a última), `plannedMinutes` arredondado para cima e validação de bloco/versão/estrutura vazia.
- [x] `PlannedSession.structure: WorkoutStructure? = null`; linhas antigas e sessões sem estrutura continuam válidas.
- [x] `WorkoutStructureJson` versionado em `core:data`; `null`/vazio/JSON malformado/versão futura/kind desconhecido/estrutura inválida → `null`, nunca estrutura parcial.
- [x] 13 testes em `WorkoutStructureTest` e 11 em `WorkoutStructureJsonTest`; fix separado do `ComplexCondition` de C4-40 no `:core:data` (`:core:data:detekt` verde).

**S6.1 — matriz do catálogo (C4-15)**

- [x] `SessionLibrary.qualityTemplates(phase, level, distance)` e `easyTemplates(phase, level, includeIntroFartlek)`; `templates` mantém a projeção main/second do gerador v6 até C4-17.
- [x] `SessionRole.INTRO_FARTLEK`; `SessionTemplate.structure: WorkoutStructure?`; template de qualidade sem estrutura é rejeitado.
- [x] Iniciante sem qualidade estruturada em `BASE`/`BUILD`/`PEAK`; `TAPER` mantém a micro-dose `STRIDES_ONLY`.
- [x] `INTERMEDIATE/BUILD` com contínuo, intervalos 5×3, fartlek 8×(1+1) e colinas 6×45 s; `ADVANCED` com mais volume.
- [x] `BASE` expõe fartlek leve para intermediário/avançado; a segunda metade da fase fica para C4-16.
- [x] `PEAK` específico por distância (`RACE_PACE`, alvos `GOAL_5K`/`GOAL_10K`/`GOAL_HALF`/`GOAL_MARATHON`); avançado soma um segundo estímulo.
- [x] 17 testes em `QualityCatalogTest`; `SessionLibraryTest` atualizado para a nova matriz.

**S6.2 — rotação do catálogo (C4-16)**

- [x] `SessionLibrary.qualityRotation(phase, level, distance, weekInPhase, maxQualities, phaseWeeks)`; a seleção é determinística e não depende do relógio.
- [x] Semanas seguidas não repetem o kind principal quando o catálogo tem mais de um estímulo; a segunda qualidade sempre tem kind diferente.
- [x] O fartlek leve da `BASE` só entra na segunda metade da fase (`weekInPhase * 2 >= phaseWeeks`); sem `phaseWeeks`, a `BASE` fica sem qualidade.
- [x] `rotateQualities` interno separa a rotação do filtro de fase e atribui `QUALITY_MAIN`/`QUALITY_SECOND`; catálogo de um só kind devolve só a principal.
- [x] 14 testes em `QualityRotationTest`; `SessionLibrary` com 100% de linhas e 98,98% de branches.

**S6.3 — integração do catálogo (C4-17)**

- [x] O gerador consome `SessionLibrary.qualityRotation(phase, level, distance, weekInPhase, maxQualities, phaseWeeks)`; `weekInPhase`/`phaseWeeks` vêm do macro-planejador ou dos blocos gerais de 4 semanas.
- [x] `MacroWeekIntent` carrega `weekInPhase`/`phaseWeeks`; `MesoState` propaga a posição da fase; o gerador não lê relógio.
- [x] Sessões de qualidade recebem `qualityKind`, título do template e `structure` executável; a segunda qualidade usa o template de `QUALITY_SECOND` com kind diferente.
- [x] `WorkoutStructure.fittedTo(minutes)` encolhe repetições/blocos contínuos para caber na duração real, cresce `totalMin` quando sobra tempo e nunca promete mais trabalho do que cabe.
- [x] O rationale da qualidade nasce da estrutura final (aquecimento, repetições, recuperação e volta à calma); o texto de taper/descarga só aparece quando a sessão foi mesmo encurtada.
- [x] `PEAK` usa `RACE_PACE` específico da distância (`Pace de prova 5K/10K/meia/maratona`); não é cópia do `BUILD`.
- [x] `BASE` sem qualidade na primeira metade; o fartlek leve aparece na segunda metade. `TAPER` vira micro-dose de strides com kind/estrutura; `RECOVERY` fica sem qualidade; iniciante continua sem `QUALITY`.
- [x] A projeção legada `SessionLibrary.templates(...)` foi removida; o gerador usa `easyTemplates` para os títulos fáceis e a rotação para a qualidade.
- [x] `R-PER-04` entrou no catálogo de explicações; o plano cita `R-PER-02`/`R-PER-04`.
- [x] 7 testes em `QualityIntegrationTest` e 7 em `WorkoutStructureTest` (4 de ajuste + 3 de casos-limite); `SessionLibraryTest`/`QualityCatalogTest` migrados para a API nova.

**S7 — gates estrutural × do dia (C4-02, C4-07)**

- [x] `WeeklyPlanGenerator` separa `structuralQualityEligible` (nível, dias, fase, race week e dia seguro) do gate do dia; prontidão, sono, check-in e carga deixam de decidir a existência da primeira oportunidade.
- [x] A segunda oportunidade continua condicionada a progressão: semana anterior ≥80%, `permitsProgression` (ACWR confiável em 0,8–1,3), 4+ dias e sem taper/race/recovery.
- [x] A micro-dose de taper/race week é estrutural: não depende de prontidão/carga, só de haver dia elegível; recovery e iniciante continuam sem qualidade.
- [x] Sessões de qualidade recebem `planDecision`: `QUALITY_OPPORTUNITY` na primeira/micro-dose e `PROGRESSION_COMPLETED` na segunda; o rationale diz “qualidade planejada para \<dia\>; o dia confirma”.
- [x] Golden `G-PER-03` em `fixtures/golden/per-opportunity`: 3×40, intermediário, duas semanas de carga, sem check-in/carga → 1 oportunidade (fartlek de terça), fácil de sexta 40, “Corrida mais longa” de domingo 40, 120 min e nenhum “Longão”.
- [x] `QualityPlanPropertiesTest`: ≤2 oportunidades, nunca adjacentes ao longão, nunca consecutivas, taper/race só `STRIDES_ONLY` ≤25 min, recovery/iniciante zero, estrutura cabe na duração e determinismo.
- [x] 12 testes novos (7 `StructuralQualityTest`, 4 propriedades, 1 golden); testes legados de “bloqueio na geração” migrados para “oportunidade estrutural + decisão do dia”, com justificativa ADR 0044 §2.1.

### 0.2 O que **não** está pronto

- `QualityGate` ainda **não** está fiado no `DailyPrescriptionEngine`/plano (C4-04/C4-05). O gerador já cria a oportunidade estrutural mesmo com prontidão/carga do dia desfavoráveis; o `DailyPrescriptionEngine` atual já rebaixa por doença/vermelho/âmbar/sono/carga, mas o motivo catalogado do gate só entra em C4-04.
- `VolumeProgression`/`MesoCycle`/`LevelGraduation` já usam a referência real e o longão real; o golden `G-VOL-02`, a copy do treino extra e os docs completos `R-PLAN-01 v2`/`R-LVL-03` ficam para C4-41/C4-42.
- O reparo `LEGACY` está no prompt de import e no caso de uso; a tela Hoje ainda só oferece “Corrigir registro” para `MANUAL`. O review/copy do treino extra fica em C4-41/C4-43.
- `qualityEligibleByReadiness` já existe, mas o diário e o recomendador ainda não o leem; a fiação é C4-04/C4-29.
- A copy/UI de “em calibração” e o golden `G-LOAD-02` ficam para C4-26/C4-27; o domínio já expõe `acwrReliable`, `historyDays` e `baselineDays`.
- Copy/catálogo de `R-PER-03`/`R-READY-06` e os trechos de `SPORTS_SCIENCE` ligados a essas regras ficam para C4-06/C4-08/C4-33/C4-52.
- O check-in UI (C4-30), a reavaliação tardia (C4-31), o golden `G-READY-03`/copy (C4-32) e os docs de prontidão (C4-33) continuam pendentes.
- O golden `G-DATA-02` e os docs de sono (C4-37) continuam pendentes. O diagnóstico de sono é exposto no sync (`suspectSleepNights`/`fragmentedSleepNights`) e na mensagem do Health Connect; não há `ReadinessFlag.SLEEP_DATA_SUSPECT` porque a noite suspeita é persistida como `null` e o ADR 0049 não prevê migração.
- A persistência de `qualityKind`/`planDecision`/`structure` fica para C4-45/C4-46 (schema v18); por enquanto os campos são preenchidos no plano em memória, mas `null` no round-trip. O codec JSON de `structure` já existe (C4-14), mas ainda não está ligado ao `PlanJson`/banco.
- O catálogo, a rotação e a oportunidade estrutural já são consumidos pelo gerador (C4-17/C4-02); `qualityKind`/`structure`/`planDecision` saem preenchidos no plano. A oportunidade ainda não passa pelo `QualityGate` diário (C4-04/C4-05).
- Strides para todos os níveis e intro fartlek do iniciante (C4-18), estrutura no briefing/UI (C4-19), golden `G-PER-05` (C4-20) e docs `R-PER-04`/`SPORTS_SCIENCE` (C4-21) ficam para S12.
- O bump `weekly-plan-v6` → `weekly-plan-v7` e `daily-v6` fica para C4-53; nenhuma migração v18 foi escrita/testada; nenhuma UI de check-in foi alterada.
- `G-PER-05`, `G-LOAD-02`, `G-READY-03`, `G-DATA-02`, `G-VOL-02` e `G-UPGRADE-01` ainda não existem; `G-PER-03` está em `fixtures/golden/per-opportunity`.
- O rationale da primeira oportunidade já diz “o dia confirma”, mas a copy de “sem progressão” ainda não distingue falta de histórico de carga fora da faixa; a revisão fica em C4-06/C4-59.
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
| **S5** | C4-38, C4-39 | `VolumeReference`/`StrongRun`; meso/graduação | testes de volume | C4-38 e C4-39 concluídas (`VolumeReferenceTest`, `VolumeReferenceProjectionTest`, base/meso/graduação reais) |
| **S5.1** | C4-40 | Reparo `LEGACY` do mesmo dia, com confirmação | testes de reparo; migrado intocado | C4-40 concluída (`SessionCompletionRulesTest`, repositório, `RepairLegacyCompletionTest`, prompt) |
| **S6** | C4-14..C4-17 | `WorkoutStructure`, catálogo, rotação, gerador | testes de catálogo/estrutura | C4-14..C4-17 concluídas (`WorkoutStructureTest` 20, `WorkoutStructureJsonTest` 11, `QualityCatalogTest` 16, `QualityRotationTest` 14, `QualityIntegrationTest` 7); gerador consome a rotação e preenche kind/estrutura |
| **S7** | C4-02, C4-07 | Gerador estrutural + `G-PER-03` | `G-PER-03`; propriedades | C4-02/C4-07 concluídas (`StructuralQualityTest` 7, `QualityPlanPropertiesTest` 4, `PlanGoldenTest` 1); `:core:domain:test` 975 |
| **S8** | C4-04, C4-05, C4-29 | `QualityGate` no dia; reavaliação de domínio | testes do daily engine | commit + testes |
| **S9** | C4-45, C4-46 | Schema v18, JSON, `generatorVersion` | migração 17→18; round-trip | commit + migração |
| **S10** | C4-44, C4-47, C4-48, C4-49 | Gate de dados, upgrade, replanner | `G-UPGRADE-01`; idempotência | commit + golden |
| **S11** | C4-06, C4-30, C4-31, C4-50..C4-52, C4-08 | Copy, check-in UI, reavaliação tardia, decisão/catálogo | UI tests; catálogo | commit + testes |
| **S12** | C4-18, C4-19, C4-20, C4-21 | Strides/intro fartlek; estrutura no briefing; `G-PER-05`; docs | testes de iniciante; UI | commit + testes |
| **S13** | C4-41..C4-43 | Crédito do treino extra; UI do review | `G-VOL-02`; testes de reparo | commit + golden |
| **S14** | C4-26, C4-27, C4-32, C4-33, C4-37 | UI/copy de carga, prontidão, sono; docs | goldens; UI/a11y; docs | commit + testes |
| **S15** | C4-57..C4-60 | Alvos pace×FC, horizonte/pace dinâmico, copy, `createdAt` | testes; copy | commit + testes |
| **S16** | C4-53, C4-54 | Versões, suíte completa, Kover, detekt, spotless | `check`/`koverVerify`/`assembleDebug` | commit + logs |
| **S17** | C4-55, C4-56 | Device, docs, fechamento | smoke; `PROGRESS`/`ROADMAP` | commit + checklist final |

---

## 5. Sessão S5 — C4-38 e C4-39 concluídas

> O caderno completo da S4 fica no §7. Esta sessão abriu a projeção de volume real e fiou a decisão de volume/mesociclo/graduação nela.

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

**Task C4-39 — volume/meso/graduação com referência real**

- [x] `VolumeProgression.decide` recebe `List<VolumeReference>` e a base é `max(strongMinutes)`; a adesão continua lida do plano anterior (`strongCompletionRatio`), então treino extra não infla adesão.
- [x] `MesoCycle.isLoadWeek` usa `strongSessions >= 1` + `createdOn == weekStart` + semana não especial; semana sem plano não é carga.
- [x] `LevelGraduationInput.strongRuns` + `hasLongRun` usam o `StrongRun.durationMin` real (60/90); a duração planejada não substitui a corrida real.
- [x] Fiação em `WeeklyPlanInput`/`ReplanInput` e nos builders de `TodayViewModel`, `TutorialViewModel`, `ReplanContextBuilder` e `LevelGraduationContextBuilder`.
- [x] Replanner inclui as referências no `inputsHash` e no mesociclo canônico.
- [x] Testes: base real, treino extra sem adesão, semana sem plano/especial, meso só com treino extra, longão real 60/90 e duração planejada que não conta.

**Aceite da sessão (C4-38 + C4-39):**

- [x] `./gradlew :core:domain:test` verde (899).
- [x] `:app:testDebugUnitTest` 890 verdes; `:core:data:test` 171; `:app:assembleDebug`.
- [x] `spotlessCheck`, `:core:domain:koverVerify`, `:core:domain:detekt` e `:app:detekt` verdes; FIT 93,01%.
- [x] Nenhum dado real; nenhuma migração; nenhuma UI alterada.
- [x] §0/§6/§7/PROGRESS atualizados.
- [x] Commits: `feat(domain): add real volume reference projection (C4-38)` e `feat(domain): use real volume reference in progression, meso and graduation (C4-39)`.

**Notas de implementação:**

- A projeção recebe as atividades já sem lixeira (`listActivitySummaries`/`observeActivitySummaries`); atividade deletada continua fora na origem.
- Uma corrida com menos de 60 s arredonda para 0 min e não conta; o volume nunca é inflado.
- `executedMinutes` e `strongMinutes` são hoje o mesmo número (ADR 0050 §2.1); a distinção fica no modelo para evolução sem quebrar o contrato.
- `VolumeReference.strongSessions` conta todas as corridas fortes; por isso a adesão usa o plano, não esse numerador. O ADR 0050 §2.2 foi revisado para registrar isso.
- O fallback legado (`VolumeReferenceBuilder.fromPlans`) só roda quando `volumeReferences == null` (testes/callers antigos); lista vazia significa “sem referência real”.
- `G-VOL-02`, o rationale “treino extra contou” e os docs completos `R-PLAN-01 v2`/`R-LVL-03` ficam em C4-41/C4-42. Há uma inconsistência numérica no golden (114 + 28 + 33 → 127) a reconciliar antes de escrevê-lo.

**Se a sessão acabar no meio:** registre qual teste está vermelho, o comando exato, a saída e o próximo passo. Não marque `DONE`.

---

## 5.1 Sessão S5.1 — C4-40 concluída

> C4-40 foi antecipada de S13 porque só depende de C4-39; a S6 (catálogo) continua sendo a próxima sessão planejada.

**Objetivo:** permitir que uma corrida importada no mesmo dia repare uma sessão `DONE` + `LEGACY_UNVERIFIED` sem vínculo, com confirmação explícita, sem promover o histórico migrado.

**Contexto obrigatório:**

- ADR 0050 §2.4–2.5 e FR-408.
- `SessionCompletionRules`, `SessionExecutionRepository`/`TrainingPlanDao`, `ReplaceManualCompletion`, `ImportedRunPromptViewModel`/diálogo e `OfferImportedRun`.

**Task C4-40 — reparo `LEGACY_UNVERIFIED` do mesmo dia**

- [x] `SessionCompletionRules.checkLegacyRepair(...)`: exige `DONE` + `LEGACY_UNVERIFIED`, sem `activityId`/`executionId`, sessão e corrida no dia atual e confirmação explícita.
- [x] `TrainingPlanDao.repairLegacyEvidence` só atualiza a linha legada sem vínculo; `SessionExecutionRepository.repairLegacyWithActivity(...)` cria a execução e fecha o vínculo 1:1 na mesma transação.
- [x] `RepairLegacyCompletion` no `:app` revalida plano, corrida, esporte, data, confirmação e estado antes de reparar.
- [x] `ImportedRunPromptViewModel`/diálogo: `AlreadyDone` distingue `MANUAL` e `LEGACY`; o botão “Usar esta corrida” é a confirmação explícita; a cópia explica que só o dia atual é reparado.
- [x] `OfferImportedRun` enfileira o prompt para o legado do mesmo dia; o histórico de dias anteriores continua somente leitura.

**Aceite da sessão (C4-40):**

- [x] `./gradlew :core:domain:test` verde (906).
- [x] `:core:data:test` 175; `:app:testDebugUnitTest` 901; `:app:assembleDebug`.
- [x] `spotlessCheck`, `:core:domain:koverVerify`, `:core:domain:detekt`, `:app:detekt` e `:app:lintDebug` verdes; FIT 93,01%.
- [x] Nenhum dado real; nenhuma migração; nenhuma alteração de schema.
- [x] §0/§6/§7/PROGRESS atualizados.
- [x] Commit: `feat(app): repair same-day legacy completion with confirmation (C4-40)`.

**Notas de implementação:**

- A confirmação é um campo do request (`confirmed`); sem ele o caso de uso rejeita. O botão do diálogo é a confirmação visual.
- O reparo preserva o `completedAt` original quando existe; sem ele, usa o clock atual.
- Um `LEGACY` com `activityId` (evidência suspensa pela lixeira) não é reparável: restaurar a atividade devolve `ACTIVITY`; o reparo é só para o caso sem vínculo.
- O histórico migrado é bloqueado pela data (`session.date == today`) e o repositório só atualiza a linha pelo id, sem backfill.
- O `ImportedRunPromptViewModel` ganhou uma dependência e a entrada de `LongParameterList` do baseline do detekt foi atualizada; a dívida é pré-existente (ViewModel com muitos repositórios), não um achado novo.
- A tela Hoje ainda só oferece “Corrigir registro” para `MANUAL`; o caminho do legado é o prompt de import (ADR 0050 §7).

**Se a sessão acabar no meio:** registre qual teste está vermelho, o comando exato, a saída e o próximo passo. Não marque `DONE`.

---

## 5.2 Sessão S6 — C4-14 concluída

**Objetivo:** estabelecer o contrato executável da sessão (aquecimento, blocos, recuperações, volta à calma) com um orçamento de tempo verificável, pronto para o catálogo (C4-15) e para a persistência (C4-45).

**Contexto obrigatório:**

- ADR 0047 §2.1 e PRD §5.3.
- `PlannedSession`, `PrescriptionJson`/`PlanJson` e os testes de domínio da prescrição.

**Task C4-14 — `WorkoutStructure`/`WorkoutBlock` + JSON**

- [x] Tipos puros no domínio: `BlockKind`, `WorkoutBlock` e `WorkoutStructure`, com validações de versão, durações, repetições e texto.
- [x] `plannedSeconds`/`plannedMinutes`: aquecimento + blocos + recuperações + volta à calma ≤ `totalMin`; recuperação contada após toda repetição.
- [x] `PlannedSession.structure` opcional, default `null`, sem quebrar linhas/callers antigos.
- [x] Codec `WorkoutStructureJson` versionado em `core:data`, com política “tudo ou nada”: payload desconhecido/inválido vira `null`.
- [x] 13 testes de domínio e 11 de codec; `:core:domain:test` 919, `:core:data:test` 186, `:app:testDebugUnitTest` 901, `:core:model` 40.

**Aceite da sessão (C4-14):**

- [x] `:core:domain:test`, `:core:data:test`, `:app:testDebugUnitTest` verdes.
- [x] `:app:assembleDebug`, `spotlessCheck`, `:core:domain:koverVerify`, `:core:data:koverVerify`, `:core:domain:detekt`, `:core:data:detekt` e `:app:detekt` verdes.
- [x] Nenhum dado real; nenhuma migração; nenhuma UI alterada.
- [x] §0/§6/§7/PROGRESS atualizados.
- [x] Commits: `fix(data): simplify legacy repair guard for detekt (C4-40)` e `feat(domain): add executable workout structure and JSON (C4-14)`.

**Notas de implementação:**

- O codec ainda não está ligado ao `PlanJson`/`PlannedSessionEntity`: a coluna `structureJson` é do schema v18 (C4-45), como planejado.
- A recuperação depois da última repetição é intencionalmente conservadora; o gerador de C4-17 deve dimensionar a estrutura dentro do `totalMin`, não contar com uma recuperação “grátis” no fim.
- `SessionTemplate.structure` e a matriz do catálogo ficam em C4-15, junto com `SessionRole.INTRO_FARTLEK`.
- O `ComplexCondition` do reparo `LEGACY` foi corrigido em commit separado para o `:core:data:detekt` voltar a passar sem baseline novo.

**Se a sessão acabar no meio:** registre qual teste está vermelho, o comando exato, a saída e o próximo passo. Não marque `DONE`.

---

## 5.3 Sessão S6.1 — C4-15 concluída

**Objetivo:** transformar a `SessionLibrary` em uma matriz determinística de fase × nível × distância, com `INTRO_FARTLEK`, `SessionTemplate.structure` e PEAK específico, sem ainda fiar o gerador (C4-17).

**Contexto obrigatório:**

- ADR 0047 §2.1–2.5 e FR-403.
- `SessionLibrary`, `SessionTemplate`, `SessionLibraryTest`, `SessionRolesTest` e os testes do gerador que fixam títulos/roles.

**Task C4-15 — matriz de `QualityKind`/`SessionTemplate`**

- [x] `SessionLibrary.qualityTemplates(phase, level, distance)` expõe a matriz; `easyTemplates(phase, level, includeIntroFartlek)` expõe fácil, strides e intro fartlek.
- [x] `SessionRole.INTRO_FARTLEK`; `SessionTemplate.structure`; template de qualidade sem estrutura é rejeitado.
- [x] Iniciante sem qualidade estruturada em `BASE`/`BUILD`/`PEAK`; `TAPER` mantém a micro-dose `STRIDES_ONLY`.
- [x] Intermediário `BUILD`: contínuo, intervalos 5×3, fartlek 8×(1+1) e colinas 6×45 s; avançado com mais volume.
- [x] `BASE` com fartlek leve para intermediário/avançado; a segunda metade da fase fica para C4-16.
- [x] `PEAK` específico por distância (`RACE_PACE` com `GOAL_5K`/`GOAL_10K`/`GOAL_HALF`/`GOAL_MARATHON`); avançado soma um segundo estímulo.
- [x] A projeção legada `templates(...)` mantém main/second e BASE sem qualidade para não mudar o gerador v6 antes de C4-17.
- [x] 17 testes em `QualityCatalogTest`; `SessionLibraryTest` atualizado para a nova matriz.

**Aceite da sessão (C4-15):**

- [x] `./gradlew :core:domain:test` verde (936).
- [x] `:core:data:test` 186; `:app:testDebugUnitTest` 901; `:core:model` 40; `:app:assembleDebug`.
- [x] `spotlessCheck`, `:core:domain:koverVerify`, `:core:domain:detekt`, `:app:detekt` e `:app:lintDebug` verdes; FIT 93,01%.
- [x] Nenhum dado real; nenhuma migração; nenhuma UI alterada.
- [x] §0/§6/§7/PROGRESS atualizados.
- [x] Commit: `feat(domain): add quality catalog matrix and structures (C4-15)`.

**Notas de implementação:**

- A matriz expõe todos os estímulos elegíveis; a rotação escolhe 1–2 por semana (C4-16) e o gerador preenche `qualityKind`/`structure` (C4-17).
- As formas de PEAK são baseadas em tempo (`WorkoutBlock` não carrega metros) e cabem no teto de 45 min; o alvo `GOAL_*` é um rótulo de distância, não a meta de tempo. C4-17 converte a previsão atual em pace real.
- O iniciante continua sem `QUALITY` estruturada; a micro-dose de `TAPER` permanece `STRIDES_ONLY` no catálogo, mas o gerador já bloqueia qualidade para `BEGINNER` (`R-PRESC-04`).
- `structure` ainda não é persistida nem usada pelo gerador; a fiação é C4-17 e o schema v18/C4-45.

**Se a sessão acabar no meio:** registre qual teste está vermelho, o comando exato, a saída e o próximo passo. Não marque `DONE`.

---

## 5.4 Sessão S6.3 — C4-17 concluída

**Objetivo:** fazer o gerador consumir o catálogo e a rotação, preencher `qualityKind`/`structure` dentro da duração real e tornar o `PEAK` específico da distância, sem persistência nem UI nova.

**Contexto obrigatório:**

- ADR 0047 §2.1–2.7; PRD FR-403/FR-404/FR-410.
- `WeeklyPlanGenerator`, `SessionLibrary`, `WorkoutStructure`, `MesoCycle`, `MacroPlan`, `QualityRotationTest` e os testes que fixam títulos/roles/durações.

**Task C4-17 — gerador usa estrutura/kind; PEAK específico**

- [x] `MacroWeekIntent.weekInPhase`/`phaseWeeks`; `MesoState` propaga a posição da fase; `BASE`/`BUILD` sem prova usam blocos de 4 semanas demonstradas.
- [x] O gerador chama `qualityRotation` com fase, nível, distância, posição e `maxQualities`; a segunda qualidade só entra com kind diferente.
- [x] Sessões de qualidade recebem `qualityKind`, título do template e `structure`; a micro-dose de taper também.
- [x] `WorkoutStructure.fittedTo(minutes)` ajusta repetições/blocos contínuos à duração final, preserva aquecimento/volta à calma e nunca excede o tempo.
- [x] Rationale da qualidade deriva da estrutura final; taper/descarga só alega corte quando houve corte.
- [x] `PEAK` usa `RACE_PACE` da distância; `PEAK` e `BUILD` têm título/kind diferentes.
- [x] `BASE` sem qualidade na primeira metade; fartlek leve na segunda; `TAPER` micro-dose; `RECOVERY` sem qualidade; iniciante sem `QUALITY`.
- [x] `R-PER-04` no catálogo de explicações; `SessionLibrary.templates(...)` removida.
- [x] `QualityIntegrationTest` (7), `WorkoutStructureTest` (+7), `SessionLibraryTest`/`QualityCatalogTest` migrados.

**Aceite da sessão (C4-17):**

- [x] `./gradlew :core:domain:test` verde (963).
- [x] `:core:data:test` 186; `:app:testDebugUnitTest` 901; `:core:model` 40; `:app:assembleDebug`.
- [x] `spotlessCheck`, `./gradlew check`, `:core:domain:koverVerify`, `:core:domain:detekt`, `:core:data:detekt`, `:app:detekt` e `:app:lintDebug` verdes; FIT 93,01%.
- [x] Nenhum dado real; nenhuma migração; nenhuma UI alterada.
- [x] §0/§6/§7/PROGRESS atualizados.
- [x] Commit: `feat(domain): wire the quality catalog into the weekly plan (C4-17)`.

**Notas de implementação:**

- `weekInPhase` é 0-based e determinístico: vem da tabela macro quando há prova dentro do horizonte; sem prova, a fase geral alterna `BASE`/`BUILD` a cada 4 semanas demonstradas.
- A rotação é a autoridade da semana: a `BASE` da primeira metade não tem qualidade e o rationale diz isso explicitamente, em vez de culpar a agenda.
- A estrutura é ajustada duas vezes: na criação e depois de volume/taper/deload. O `totalMin` da estrutura é a duração final da sessão; o texto sempre descreve as repetições reais.
- O `PEAK` mantém a especificidade por distância; quando a semana já está na janela de taper, a dose é reduzida como qualquer qualidade, mas o tipo `RACE_PACE` permanece.
- A projeção legada `templates(...)` foi removida; `SessionLibrary` agora expõe só `easyTemplates`, `qualityTemplates` e `qualityRotation`.
- `qualityKind`/`structure` ainda não são persistidos (C4-45); o round-trip do banco continua devolvendo `null` até lá.

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
| [x] | C4-02 | Separar gates estrutural × do dia no gerador | C4-01 | L | S7 | ADR 0044 | 3×40 gera 1 oportunidade; 2ª com progressão | `StructuralQualityTest` (7); `QualityPlanPropertiesTest` (4); `planDecision` no plano; commit S7 |
| [x] | C4-03 | `QualityGate` puro | C4-01 | L | S1 | ADR 0044 | Precedência completa; determinístico | `QualityGateTest` (27); commit S1 |
| [ ] | C4-04 | Daily engine usa `QualityGate` | C4-03 | M | S8 | ADR 0044 | Mantém/rebaixa com motivo; `R-PRESC-02` | — |
| [ ] | C4-05 | Reavaliação com check-in/carga antes do DONE | C4-04 | M | S8 | ADR 0044/0049 | EASY→QUALITY antes do DONE; DONE intacto | — |
| [ ] | C4-06 | Copy/rationale da oportunidade e rebaixamento | C4-03 | M | S11 | ADR 0044/FR-412 | Sem contradição; IDs catalogados | — |
| [x] | C4-07 | Golden `G-PER-03` + propriedades | C4-02/03 | M | S7 | ADR 0044 | Golden e invariantes | `fixtures/golden/per-opportunity`; `PlanGoldenTest` (1); `QualityPlanPropertiesTest` (4); commit S7 |
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
| [x] | C4-14 | `WorkoutStructure`/`WorkoutBlock` + JSON | C4-01 | L | S6 | ADR 0047/0048 | Round-trip; retrocompatível | `WorkoutStructureTest` (13); `WorkoutStructureJsonTest` (11); commit S6 |
| [x] | C4-15 | `QualityKind` + matriz da `SessionLibrary` | C4-14 | XL | S6.1 | ADR 0047 | Matriz fase/nível/distância | `QualityCatalogTest` (17); `SessionLibraryTest` atualizado; commit S6.1 |
| [x] | C4-16 | Rotação determinística e 2 kinds | C4-15 | M | S6.2 | ADR 0047 | Duas semanas não repetem | `QualityRotationTest` (14); commit S6.2 |
| [x] | C4-17 | Gerador usa estrutura/kind; PEAK específico | C4-15/16 | L | S6.3 | ADR 0047 | PEAK ≠ BUILD; testes | `QualityIntegrationTest` (7); `WorkoutStructureTest` (+7); `:core:domain:test` 963; commit S6.3 |
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
| [x] | C4-39 | Volume/meso/graduação usam referência real | C4-38 | L | S5 | ADR 0050 | Base real; adesão separada | `VolumeProgressionTest`/`MesoCycleTest`/`LevelGraduationEngineTest`; fiação app; commit S5 |
| [x] | C4-40 | Reparo LEGACY do mesmo dia | C4-39 | M | S5.1 | ADR 0050 | Confirmação; migrado intacto | `SessionCompletionRulesTest` (7); `SessionExecutionRepositoryTest` (+4); `RepairLegacyCompletionTest` (7); prompt/UI (+4); commit S5.1 |
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

### S5 — 2026-11-16 — Projeção de volume real e fiação da decisão (C4-38, C4-39)

- **Status:** DONE
- **Tasks:** C4-38, C4-39.
- **Arquivos:** `core/domain/.../prescription/VolumeReference.kt` (novo), `VolumeProgression.kt`, `MesoCycle.kt`, `LevelGraduationEngine.kt`, `WeeklyPlanGenerator.kt`, `core/domain/.../replan/PlanReplanner.kt`, `ReplanModels.kt`, `app/.../application/VolumeReferenceProjection.kt` (novo), `ReplanContextBuilder.kt`, `LevelGraduationContextBuilder.kt`, `app/.../ui/today/TodayViewModel.kt`, `app/.../ui/tutorial/TutorialViewModel.kt`, `VolumeReferenceTest.kt` (novo), `VolumeReferenceProjectionTest.kt` (novo), `VolumeProgressionTest.kt`, `MesoCycleTest.kt`, `LevelGraduationEngineTest.kt`, `TodayViewModelPersistTest.kt`, `LevelGraduationViewModelTest.kt`, `EvaluateReplanTest.kt`, `docs/adr/0050-volume-credit-unplanned-activities.md`, `docs/PRD_TREINO_REAL.md`, `docs/SPORTS_SCIENCE.md`, `docs/PROGRESS.md`, este checklist.
- **Testes/gates:** `:core:domain:test` 899 verdes (+12); `:app:testDebugUnitTest` 890; `:core:data:test` 171; `:app:assembleDebug`; `spotlessCheck`; `:core:domain:koverVerify`; `:core:domain:detekt`; `:app:detekt`; FIT 93,01% de branches. `VolumeReference.kt`: 100% de linhas, 97,2% de branches. `./gradlew check` abortou em `:app:lintAnalyzeDebugUnitTest` com falha interna do lint ao analisar `NavigationTest.kt` (resolvedor FIR; arquivo não tocado nesta task); `check -x :app:lintAnalyzeDebugUnitTest` passou e os gates de teste/módulo acima passaram.
- **Commits:** `feat(domain): add real volume reference projection (C4-38)`; `feat(domain): use real volume reference in progression, meso and graduation (C4-39)`.
- **Decisões/notas:** a base é `max(strongMinutes)` das semanas usáveis; a adesão continua no `strongCompletionRatio` do plano, porque `strongSessions` conta treino extra. `MesoCycle` usa `strongSessions >= 1` e semana não especial; a graduação usa `StrongRun` real 60/90 e ignora duração apenas planejada. `volumeReferences` é opcional (`null` = fallback legado derivado do plano, só testes/callers antigos; `emptyList()` = sem referência real). A projeção é montada em `TodayViewModel`, `TutorialViewModel`, `ReplanContextBuilder` e `LevelGraduationContextBuilder`; o replanner inclui as referências no `inputsHash`. `G-VOL-02` (inconsistência 114 + 28 + 33 → 127), reparo `LEGACY`, copy e docs completos ficam em C4-40..C4-43.
- **Próximo:** C4-40 — reparo `LEGACY_UNVERIFIED` do mesmo dia com confirmação, TDD.

### S5.1 — 2026-11-16 — Reparo LEGACY do mesmo dia (C4-40)

- **Status:** DONE
- **Tasks:** C4-40 (antecipada de S13).
- **Arquivos:** `core/domain/.../prescription/SessionCompletionRules.kt`, `SessionCompletionRulesTest.kt`, `core/data/.../db/dao/TrainingPlanDao.kt`, `repository/SessionExecutionRepository.kt`, `SessionExecutionRepositoryTest.kt`, `app/.../application/RepairLegacyCompletion.kt` (novo), `RepairLegacyCompletionTest.kt` (novo), `app/.../ui/importer/ImportedRunPromptViewModel.kt`, `ImportedRunPromptDialog.kt`, `ImportedRunPromptViewModelTest.kt`, `ImportedRunPromptDialogTest.kt`, `OfferImportedRunTest.kt`, `app/config/detekt/baseline.xml`, `docs/adr/0050-volume-credit-unplanned-activities.md`, `docs/TESTING.md`, `docs/PROGRESS.md`, este checklist.
- **Testes/gates:** `:core:domain:test` 906 verdes (+7); `:core:data:test` 175 (+4); `:app:testDebugUnitTest` 901 (+11); `:core:model` 40; `:app:assembleDebug`; `spotlessCheck`; `:core:domain:koverVerify`; `:core:domain:detekt`; `:app:detekt`; FIT 93,01% de branches. Na primeira execução completa, `MetricsImportViewModelTest` estourou o timeout de 30 s (arquivo não tocado); passou isolado e na reexecução da suíte.
- **Commit:** `feat(app): repair same-day legacy completion with confirmation (C4-40)`.
- **Decisões/notas:** a regra só aceita `DONE` + `LEGACY_UNVERIFIED` sem `activityId`/`executionId`, sessão e corrida no dia atual e `confirmed=true`; o repositório cria a execução ausente, preserva `completedAt` e grava o vínculo 1:1. O prompt de import distingue `MANUAL` e `LEGACY` em `AlreadyDone.repairEvidence`; o botão “Usar esta corrida” é a confirmação. A tela Hoje continua só com correção manual; o legado passa pelo prompt. O baseline do detekt foi atualizado para o `LongParameterList` pré-existente do ViewModel; nenhum schema/migração foi alterado.
- **Próximo:** S6 / C4-14 (`WorkoutStructure`/`WorkoutBlock` + JSON), TDD.

### S6 — 2026-11-16 — Estrutura executável da sessão (C4-14)

- **Status:** DONE
- **Tasks:** C4-14.
- **Arquivos:** `core/domain/.../prescription/WorkoutStructure.kt` (novo), `WeeklyPlanGenerator.kt` (`PlannedSession.structure`), `WorkoutStructureTest.kt` (novo), `core/data/.../prescription/WorkoutStructureJson.kt` (novo), `WorkoutStructureJsonTest.kt` (novo), `repository/SessionExecutionRepository.kt` (fix de detekt de C4-40), `docs/adr/0047-quality-catalog-periodization.md`, `docs/DATA_MODEL.md`, `docs/DECISIONS.md`, `docs/PROGRESS.md`, este checklist.
- **Testes/gates:** `:core:domain:test` 919 verdes (+13); `:core:data:test` 186 (+11); `:app:testDebugUnitTest` 901; `:core:model` 40; `:app:assembleDebug`; `spotlessCheck`; `:core:domain:koverVerify`; `:core:data:koverVerify`; `:core:domain:detekt`; `:core:data:detekt`; `:app:detekt`.
- **Commits:** `fix(data): simplify legacy repair guard for detekt (C4-40)`; `feat(domain): add executable workout structure and JSON (C4-14)`.
- **Decisões/notas:** `totalMin` cobre aquecimento + blocos + recuperações + volta à calma; a recuperação após a última repetição é contada de propósito, então a estrutura é conservadora. O codec é versionado e devolve `null` para payload ausente, malformado, de versão futura ou inválido; nunca monta uma estrutura parcial. `PlannedSession.structure` já existe no domínio, mas a coluna `structureJson` e o round-trip no banco ficam para C4-45. `SessionTemplate.structure`/`INTRO_FARTLEK` e a matriz ficam em C4-15. O `ComplexCondition` de C4-40 foi corrigido em commit separado para o `:core:data:detekt` passar sem baseline novo.
- **Próximo:** S6 / C4-15 (matriz de `QualityKind`/`SessionTemplate` por fase, nível e distância; `INTRO_FARTLEK`; `SessionTemplate.structure`), TDD.

### S6.1 — 2026-11-16 — Matriz do catálogo de qualidade (C4-15)

- **Status:** DONE
- **Tasks:** C4-15.
- **Arquivos:** `core/domain/.../prescription/SessionLibrary.kt`, `QualityCatalogTest.kt` (novo), `SessionLibraryTest.kt`, `SessionRolesTest.kt`, `docs/adr/0047-quality-catalog-periodization.md`, `docs/DATA_MODEL.md`, `docs/PROGRESS.md`, este checklist.
- **Testes/gates:** `:core:domain:test` 936 verdes (+17: `QualityCatalogTest`); `:core:data:test` 186; `:app:testDebugUnitTest` 901; `:core:model` 40; `:app:assembleDebug`; `spotlessCheck`; `:core:domain:koverVerify`; `:core:domain:detekt`; `:app:detekt`; `:app:lintDebug`; FIT 93,01% de branches. `SessionLibrary` com 100% de linhas e 98,3% de branches.
- **Commit:** `feat(domain): add quality catalog matrix and structures (C4-15)`.
- **Decisões/notas:** `qualityTemplates` expõe todos os estímulos elegíveis; a rotação escolhe 1–2 por semana (C4-16) e o gerador preenche `qualityKind`/`structure` (C4-17). A projeção legada `templates` mantém main/second e BASE sem qualidade para não mudar o gerador v6 antes de C4-17; a segunda metade da BASE é decisão da rotação. As formas de PEAK são baseadas em tempo (`WorkoutBlock` não carrega metros) e cabem no teto de 45 min; o alvo `GOAL_*` é um rótulo de distância, não a meta de tempo. O iniciante continua sem `QUALITY` estruturada; a micro-dose de `TAPER` permanece `STRIDES_ONLY` no catálogo, mas o gerador já bloqueia qualidade para `BEGINNER` (`R-PRESC-04`). `structure` ainda não é persistida nem usada pelo gerador.
- **Próximo:** S6 / C4-16 (rotação determinística por `weekInPhase`, duas qualidades com kinds diferentes e filtro da segunda metade da BASE), TDD.

### S6.2 — 2026-11-16 — Rotação determinística do catálogo (C4-16)

- **Status:** DONE
- **Tasks:** C4-16.
- **Arquivos:** `core/domain/.../prescription/SessionLibrary.kt`, `QualityRotationTest.kt` (novo), `docs/adr/0047-quality-catalog-periodization.md`, `docs/PROGRESS.md`, este checklist.
- **Testes/gates:** `:core:domain:test` 950 verdes (+14: `QualityRotationTest`); `:core:data:test` 186; `:app:testDebugUnitTest` 901; `:core:model` 40; `:app:assembleDebug`; `spotlessCheck`; `:core:domain:koverVerify`; `:core:domain:detekt`; `:app:detekt`; `:app:lintDebug`; FIT 93,01% de branches. `SessionLibrary` com 100% de linhas e 98,98% de branches.
- **Commit:** `feat(domain): add deterministic quality rotation (C4-16)`.
- **Decisões/notas:** `weekInPhase` é 0-based e vem do plano/mesociclo; a rotação é pura e não lê relógio. `qualityRotation` filtra a `BASE` pela segunda metade quando `phaseWeeks` é conhecido; sem ele, a `BASE` fica conservadoramente sem qualidade. A segunda qualidade só entra se houver kind diferente; catálogo de um kind devolve só a principal. A projeção legada `templates(...)` continua intacta até o gerador consumir a matriz (C4-17). Nenhum schema, migração ou UI alterada.
- **Próximo:** S6 / C4-17 (o gerador consome `qualityTemplates`/`easyTemplates`/`qualityRotation`, preenche `qualityKind`/`structure` e torna o `PEAK` específico), TDD sobre `WeeklyPlanGenerator`.

### S6.3 — 2026-11-16 — Gerador consome o catálogo (C4-17)

- **Status:** DONE
- **Tasks:** C4-17.
- **Arquivos:** `core/domain/.../prescription/WeeklyPlanGenerator.kt`, `WorkoutStructure.kt`, `MesoCycle.kt`, `MacroPlan.kt`, `SessionLibrary.kt`, `ExplanationCatalog.kt`, `QualityIntegrationTest.kt` (novo), `WorkoutStructureTest.kt`, `SessionLibraryTest.kt`, `QualityCatalogTest.kt`, `WeeklyPlanGeneratorTest.kt`, `DailyPrescriptionEngineTest.kt`, `Cycle1RegressionTest.kt`, `LongRunSizingTest.kt`, `SessionRolesTest.kt`, `PlanReplannerTest.kt`, `app/.../TodayViewModelPersistTest.kt`, `docs/adr/0047-quality-catalog-periodization.md`, `docs/DATA_MODEL.md`, `docs/PROGRESS.md`, este checklist.
- **Testes/gates:** `:core:domain:test` 963 verdes (+13 líquidos: `QualityIntegrationTest` 7, `WorkoutStructureTest` +7, `SessionLibraryTest`/`QualityCatalogTest` migrados); `:core:data:test` 186; `:app:testDebugUnitTest` 901; `:core:model` 40; `:app:assembleDebug`; `spotlessCheck`; `./gradlew check`; `:core:domain:koverVerify`; `:core:data:koverVerify`; `:core:domain:detekt`; `:core:data:detekt`; `:app:detekt`; `:app:lintDebug`; FIT 93,01% de branches. Na primeira execução da suíte do app, `MetricsImportViewModelTest` estourou o timeout de 30 s (arquivo não tocado); passou isolado e na reexecução.
- **Commit:** `feat(domain): wire the quality catalog into the weekly plan (C4-17)`.
- **Decisões/notas:** `MesoState`/`MacroWeekIntent` passam a carregar `weekInPhase`/`phaseWeeks`; sem prova, a fase geral usa 4 semanas demonstradas. O gerador consome `qualityRotation` e `easyTemplates`; a projeção legada `templates(...)` foi removida. `WorkoutStructure.fittedTo` encolhe reps/blocos e mantém a invariante de tempo; o rationale nasce da estrutura final. `BASE` primeira metade sem qualidade; taper em micro-dose; `PEAK` com `RACE_PACE` da distância; iniciante/recovery intactos. `R-PER-04` entrou no catálogo de explicações. Persistência de `qualityKind`/`structure` e bump de versões ficam em C4-45/C4-53.
- **Próximo:** S7 / C4-02 (separar gates estrutural × do dia no gerador; primeira oportunidade sem prontidão/carga e segunda só com progressão), TDD.

### S7 — 2026-11-16 — Gates estrutural × do dia e golden G-PER-03 (C4-02, C4-07)

- **Status:** DONE
- **Tasks:** C4-02, C4-07.
- **Arquivos:** `core/domain/.../prescription/WeeklyPlanGenerator.kt`, `StructuralQualityTest.kt` (novo), `QualityPlanPropertiesTest.kt` (novo), `core/domain/.../golden/PlanGoldenTest.kt` (novo), `fixtures/golden/per-opportunity/` (input/expected/README), `WeeklyPlanGeneratorTest.kt`, `Cycle1RegressionTest.kt`, `fixtures/golden/README.md`, `docs/adr/0044-quality-opportunity-day-authority.md`, `docs/TESTING.md`, `docs/PROGRESS.md`, este checklist.
- **Testes/gates:** `:core:domain:test` 975 verdes (+12: `StructuralQualityTest` 7, `QualityPlanPropertiesTest` 4, `PlanGoldenTest` 1); `:core:data:test` 186; `:app:testDebugUnitTest` 901; `:core:model` 40; `:app:assembleDebug`; `spotlessCheck`; `./gradlew check`; `:core:domain:koverVerify`; `:core:domain:detekt`; `:app:detekt`; `:app:lintDebug`; FIT 93,01% de branches.
- **Commit:** `feat(domain): separate structural and day quality gates (C4-02, C4-07)`.
- **Decisões/notas:** o gerador não olha mais prontidão, sono, check-in ou carga para decidir a **primeira** oportunidade; ele olha nível, dias, fase, race week e dia seguro. A segunda continua exigindo `permitsProgression`, semana anterior ≥80%, 4+ dias e ausência de taper/race/recovery. `planDecision` passa a marcar `QUALITY_OPPORTUNITY`/`PROGRESSION_COMPLETED` e o rationale diz “o dia confirma”. A micro-dose do taper/race week também é estrutural. O ADR 0044 §2.3 fala em `days >= 4` para a segunda; o texto da task dizia “5+ dias”. Seguimos o PRD/ADR (4) e registramos a divergência aqui. `DailyPrescriptionEngine` já reduz por doença/vermelho/âmbar/sono/carga, então o estado intermediário é seguro; o motivo catalogado do `QualityGate` entra em C4-04. A copy “Sem progressão até existir histórico de conclusão” ainda não distingue falta de histórico de carga fora da faixa; fica para C4-06/C4-59.
- **Próximo:** S8 / C4-04 (`DailyPrescriptionEngine` usa `QualityGate`; mantém/rebaixa com motivo e `R-PRESC-02`), TDD.

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
| `:app` | 901 | Atualizar conforme novos testes |
| `:core:domain` | 975 | Atualizar conforme novos testes |
| `:core:data` | 186 | Atualizar |
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

## 14. Próxima ação (C4-04, S8)

1. Abrir `docs/adr/0044-quality-opportunity-day-authority.md` §2.4–2.5 e o FR-401/FR-406 do PRD.
2. Ler `QualityGate`, `DailyPrescriptionEngine`, `QualityGateTest` e os testes do diário que fixam downgrade/rebaixamento.
3. Escrever os testes que falham de C4-04: o diário chama `QualityGate` para a sessão `QUALITY` planejada; mantém quando elegível, rebaixa com `qualityBlockReason`/`detail`/`unlockHint` quando bloqueado, cita `R-PRESC-02` e nunca reescreve `DONE`/`CLOSED`; o caso sem check-in (`SUBJECTIVE_MISSING`) rebaixa mesmo com score verde.
4. Implementar C4-04 com TDD, rodar `:core:domain:test` + `:app:testDebugUnitTest` + `spotlessCheck`, atualizar §0/§6/§7/PROGRESS e commitar.

> **Regra final:** se você (agente) está em dúvida entre “seguir o plano” e “inventar”, siga o plano e registre a dúvida no §7. O trabalho final depende de continuidade, não de velocidade.
