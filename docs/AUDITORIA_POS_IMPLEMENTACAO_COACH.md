# Auditoria pós-implementação — Tela Coach

**Data:** 19/11/2026
**Commit auditado:** `56bff84` (`fix: progresso do Coach reage a perfil e provas (F4-4)`)
**PRD de referência:** `docs/PRD_EVOLUCAO_COACH.md` v0.1, com a base `docs/PRD_IA_COACH.md`
**Natureza:** auditoria independente, somente leitura. Nenhum código-fonte, teste, PRD ou documento existente foi alterado.
**Entregável:** este relatório. O único arquivo criado foi `docs/AUDITORIA_POS_IMPLEMENTACAO_COACH.md`.

---

## 1. Resumo executivo

### 1.1 Veredito

A evolução da aba **Coach** entrega a **estrutura** e parte da **substância** do PRD, mas **não está consolidada**. A tela agora tem os cards na ordem prevista, reutiliza o motor determinístico, não cria migração, não adiciona dependência e preserva a memória e o replanejamento existentes. Isso é relevante e deve ser preservado.

O problema é que os dois cards de maior valor — **F1 (próximo treino + prontidão)** e **F4 (evolução em 4 semanas)** — não entregam a pergunta que prometem:

- **F1 mostra o treino planejado, não o treino prescrito.** O card pode exibir “Ritmo forte · 35 min” enquanto a linha de status diz “Ajustado para fácil”. O motor pode ter rebaixado o dia para fácil/descanso. O dado mais visível da aba pode induzir o corredor a fazer o treino errado.
- **F1 não tem semáforo de prontidão.** Não há `ReadinessLevel`, score, sono ou contribuintes. A pergunta “amanhã posso forçar?” não é respondida; no máximo, o card repete o motivo de bloqueio de uma prescrição já persistida.
- **O estado “Dia de prova” é praticamente inalcançável em produção.** O gerador remove sessões na data do evento, mas o resolvedor só procura prova na data da sessão escolhida. Na prática, o Coach mostra “Semana concluída” ou o treino seguinte, não a prova. Os testes que “protegem” esse estado usam uma sessão artificial na data da prova, violando o invariante do gerador.
- **F2 e F4 congelam.** As fontes observam apenas `today`, perfil e provas. Importar uma corrida, fazer check-in, concluir treino ou aplicar um replanejamento não reemite guardas nem evolução. O usuário vê dados velhos até a virada do dia, mudança de perfil, mudança de prova ou reabertura do processo.
- **F4 não responde “estou evoluindo?”.** Não usa `strongMinutes`, `VolumeReference`, base real, alvo de +6%, `specialWeek`, exclusão de deload/taper/recuperação, nem o golden `G-VOL-02`. Mostra “planejado × feito” em 4 semanas. Além disso, conta `LEGACY_UNVERIFIED` como adesão, descarta semanas sem plano (mesmo com corridas reais) e calcula prontidão só com 14 dias.
- **O fallback ainda vaza código interno.** O `reason` do validador é exibido cru no card. Se a IA ecoar `ADHERENCE_PARTIAL` ou `R-PLAN-06`, o usuário lê “Resposta contém código interno: …”. O commit `F-LEAK-01` barrou o código na narrativa, mas não no motivo do fallback. Pior: o validador rejeita qualquer texto com a palavra **“atleta”**, porque `"atl"` é substring de `"atleta"`.
- **A build de qualidade está vermelha.** `./gradlew check` falha em `:app:detekt` com dois achados introduzidos por esta evolução: `ComplexCondition` em `CoachProgressLabels.kt:69` e `LongParameterList` em `CoachViewModel.kt:155` (10 parâmetros, limite 8).
- **A validação no aparelho continua pendente.** O próprio `docs/PROGRESS.md` registra “reinstalar no aparelho e conferir” e “validar no aparelho” como pendências, embora o PRD de evolução esteja declarado “encerrado”.

O motor de treino continua sendo a autoridade e a aba **não altera prescrição nem dados**. Isso limita o dano: a prescrição persistida e a aba Hoje seguem corretas. O risco é de **decisão errada na superfície Coach**, **perda de confiança** e **falsa sensação de conclusão**.

### 1.2 Avaliação geral

| Dimensão | Avaliação | Comentário curto |
|---|---|---|
| Aderência ao PRD | **Parcial, com divergências relevantes** | F3 quase completo; F1, F2 e F4 parciais; critérios de aceitação centrais não comprovados |
| Arquitetura | **Boa base, com tensões** | Domínio puro, fontes read-only e reuso corretos; `CoachViewModel` com 10 dependências, fontes sem reatividade e cálculo pesado no coletor |
| Regras de negócio | **Insuficiente para a promessa** | Motor correto, mas a composição de F1/F4 não reflete prescrição, prontidão, volume real e exclusões |
| UX e confiança | **Inconsistente** | Hierarquia correta, mas estados contraditórios, ações ausentes e dados velhos |
| Testes | **Verdes, porém não protetores** | 141 testes focados passam; vários testes codificam premissas falsas; `check` vermelho; sem validação manual |
| Maturidade | **MVP parcial / beta interno (2 de 4)** | Não pronto para ser a porta de entrada diária do usuário 0 |

### 1.3 O que está bom e deve ser preservado

1. **Domínio puro.** `CoachNextResolver`, `CoachGuards` e `CoachProgress` não importam Android, Room, Hilt ou rede.
2. **Fontes somente leitura.** `RoomCoachNextSource`, `RoomCoachGuardsSource` e `RoomCoachProgressSource` não escrevem prescrição, plano ou perfil.
3. **Reuso correto do motor.** F1 usa `DailyPrescription` persistida; F2 usa `RecoveryWindow`/`LoadSnapshot`; F4 usa o mesmo `WeeklyReviewAssembler` de Tendências; o replanejamento continua em `ReplanController`.
4. **Sem migração e sem dependência nova.** O schema não mudou; nenhuma biblioteca foi adicionada.
5. **Memória e privacidade preservadas.** Só texto `Valid` entra em `coach_messages`/`coach_summaries`; revogação apaga em escopo de aplicação; TTL 90/180 mantido.
6. **Design system respeitado.** Tokens de `core:designsystem`, sem hex solto; `McCard`, `McPrimaryButton`, `McSecondaryButton`; ordem vertical do PRD.
7. **Estados e retry.** As telas têm `Loading`, `Ready`, `Error` e `retry`; o card de F1 não bloqueia o restante.
8. **Histórico.** `CoachHistory.view` continua removendo a explicação corrente e o resumo da revisão visível; apagar item a item e em bloco permanece.

---

## 2. Método, escopo e limitações

### 2.1 O que foi lido

- `docs/PRD_EVOLUCAO_COACH.md` (F1–F4, NFRs, fases, testes e decisões registradas).
- `docs/PRD_IA_COACH.md` (contrato de IA, privacidade, decisões 17/22/23/25).
- `docs/PROGRESS.md` (estado declarado, pendências e histórico das fatias).
- Código de `app/src/main/kotlin/com/meucoach/app/coach/*`, `app/src/main/kotlin/com/meucoach/app/ui/coach/*`, `core/domain/.../coach/*`, fontes relacionadas (`DailyPrescription`, `QualityGate`, `RecoveryWindow`, `WeeklyReviewAssembler`, `PlanCompletionCalculator`, `VolumeReference`, `TrainingSignals`, `HttpCoachApi`).
- Testes de `core/domain` e `app` ligados ao Coach.

### 2.2 O que foi executado

| Comando | Resultado |
|---|---|
| `./gradlew :app:testDebugUnitTest --tests 'com.meucoach.app.ui.coach.*' --tests 'com.meucoach.app.coach.*'` | **Verde**. 102 testes focados de UI/fonte (87 UI + 15 fontes), 0 falhas |
| `./gradlew :core:domain:test` | **Verde** (task up-to-date; 39 testes de domínio do Coach nos relatórios) |
| `./gradlew :app:assembleDebug` | **Verde** |
| `./gradlew spotlessCheck koverVerify` | **Verde** |
| `./gradlew :app:lintDebug` | **Verde** |
| `./gradlew check` | **VERMELHO** em `:app:detekt` — 2 achados (detalhados na seção 7) |
| `./gradlew :app:koverXmlReport` | Executado para inspecionar a cobertura atual; relatório em `app/build/reports/kover/report.xml` |

### 2.3 Limitações

- Não houve emulador/aparelho disponível nesta auditoria; a validação visual, TalkBack e comportamento real de rede não foram executados.
- Não havia token do proxy nem dados reais; os testes usam `FakeCoachApi` e banco em memória.
- As conclusões de performance são **análise estática** (quantidade de consultas, decodificação de samples, dispatcher), não medições no aparelho. Estão marcadas como hipótese quando for o caso.
- Não foi feita comparação com um build real de produção; o julgamento é sobre o código e os testes deste repositório.

---

## 3. Matriz de conformidade com o PRD

**Legenda:** `OK` implementado; `PARCIAL` parte do requisito existe; `FALTA` não implementado; `PROBLEMA` implementado com defeito/inconsistência; `DIVERGE` comportamento diferente do especificado; `N/A` fora de escopo por decisão registrada.

### 3.1 F1 — Próximo treino + prontidão

| Requisito | Status | Evidência | Comentário |
|---|---|---|---|
| `CoachNextCard` no topo, com overline `PRÓXIMO TREINO`, dia relativo, tipo e duração | **PROBLEMA** | `CoachNextCard.kt:89-147`; `CoachNextLabels.kt:23-28` | O card existe e está no topo, mas usa `session.title`/`session.durationMin` (planejado), não a prescrição. Ver P-01. |
| Título “Hoje”/“Amanhã · QUI” + tipo + duração | **PARCIAL** | `CoachNextLabels.kt:17-28` | Rótulo relativo correto; a duração/tipo não seguem a prescrição persistida. Em prova, o overline continua “PRÓXIMO TREINO”. |
| Semáforo `Alta/Moderada/Baixa` (mesmo vocabulário do Hoje; nunca só cor) | **FALTA** | `CoachNextContext` não tem `ReadinessResult` (`CoachNextSource.kt:31-37`); `CoachNextCard.kt` não importa `ReadinessLevel`/`readinessLabel` | Não existe semáforo, score, cor semântica nem rótulo. O maior buraco funcional da F1. |
| Motivo em 1 frase determinística (ex.: “Prontidão moderada — sono 5h40”) | **PARCIAL** | `CoachNextLabels.kt:30-44` | Mostra ajuste + `blockCopy` + `unlockHint`; não mostra prontidão, sono, score nem contribuintes. |
| Ação de desbloqueio quando reversível; sem ação quando irreversível, mostrando prazo | **PARCIAL** | `CoachNextCard.kt:110-140` | “Fazer check-in” só aparece se a prescrição já tiver `SUBJECTIVE_MISSING`. Não há ação para sono/recuperação/importação; não há prazo no card. O “Ver no Hoje” é sempre secundário. |
| Nota fixa “Motor decide; IA só reescreve” | **OK** | `CoachNextCard.kt:141-145` | Presente. |
| Próximo = hoje `PLANNED` pendente; senão futura `PLANNED` da semana | **OK/PARCIAL** | `CoachNextResolver.kt:19-26`; teste `CoachNextResolverTest.kt` | Regra implementada para a semana vigente. Não olha a próxima semana; no domingo à noite mostra vazio mesmo com plano da semana seguinte. |
| Dia de prova tem precedência e mostra “Dia de prova” sem prescrição | **PROBLEMA** | `CoachNextSource.kt:54-70`; `WeeklyPlanGenerator.kt:247-262`; testes `CoachNextSourceTest.kt:111-119` e `CoachScreenTest.kt:371-402` | O source só procura `raceEvent` na data da sessão escolhida. O gerador exclui a data da prova de sessões comuns. Na prática, a prova quase nunca aparece. Os testes criam sessão na data da prova, violando o gerador. Ver P-02. |
| Sem plano vigente → não mostrar F1; cair em UX-4 | **PROBLEMA** | `CoachNextSource.kt:55-58`; `CoachScreen.kt:185-231` | Sem plano, o source devolve `null` → `CoachNextUiState.Empty`, que renderiza “Semana concluída”; abaixo aparece “Monte seu plano”. Contradição. |
| Estado `Stale` (fonte >14d) com selo “dados desatualizados” | **FALTA** | Nenhum uso de `LoadDataState`/`lastImportedAt` em `CoachNextSource`/`CoachNextCard`; só F2 tem guarda | F1 não sela dados velhos. |
| RN-F1-01: nunca aumentar; se prescrição divergir, vale a prescrição | **PROBLEMA** | `CoachNextLabels.headline()` usa `session`; `DailyPrescription` tem `title`, `prescribedType`, `prescribedDurationMin` | O card viola a regra central. Se o dia foi rebaixado, o título continua o treino forte planejado. |
| RN-F1-02: sem LTHR/pace, alvo por RPE e confiança baixa | **PARCIAL/N/A** | F1 não mostra alvo algum | Não inventa pace; também não entrega alvo/confiança. |
| RN-F1-03: sono <6h, prontidão âmbar/vermelha, `NO_QUALITY`, doença/lesão, recovery/taper/race → sem qualidade com motivo e `unlockHint` | **PARCIAL** | `CoachNextLabels.blockCopy` cobre 13 motivos; `CoachNextCard` só reage a `SUBJECTIVE_MISSING` | A copy existe, mas depende de uma prescrição já persistida; não há verificação de prontidão/sono no Coach. Sem prescrição, não há bloqueio nem ação. |
| RN-F1-04: ACWR não confiável não veta; não exibir número instável | **PARCIAL/PROBLEMA** | `CoachNextLabels.kt:42` expõe `unlockHint` cru; `QualityGate.kt:287` contém “ACWR … 1,50” | O número instável não é exibido; porém o hint cru de ACWR alto mostra “ACWR” e “1,50” no card, contra a decisão de copy e o NFR de evitar sigla. |
| RN-F1-05: ausência é `null`, nunca `0` | **OK/PARCIAL** | Sem números inventados; sem readiness | Não inventa zero; também não representa a ausência de prontidão. |
| RN-F1-06: autoridade do `RaceEvent`; nenhuma prescrição/briefing retrospectivo | **FALTA** | Ver linha de prova acima | O resolvedor não consulta prova antes da sessão. |
| RN-F1-07: IA opcional; determinístico sem IA; nenhum número novo via IA | **OK** | F1 é 100% determinístico; decisão de não persistir em `coach_messages` foi respeitada | A IA não é usada em F1; isso é compatível com a decisão “só exibir”. |
| CA-F1-01: dia/tipo/duração idênticos à sessão/prescrição | **PROBLEMA** | `CoachNextLabels.kt:23-28`; teste `CoachNextLabelsTest.kt:34-39` só testa sessão planejada | O teste não cobre prescrição divergente; a implementação usa a sessão planejada. |
| CA-F1-02: sem check-in, qualidade bloqueada com ação; após check-in verde, nova versão libera | **PARCIAL** | `CoachNextCard.kt:110-131`; teste `CoachScreenTest.kt:431-456` | Só funciona se já existir uma prescrição com `SUBJECTIVE_MISSING`. Não há teste de ponta a ponta check-in → nova prescrição → card. O Coach não chama `GetTodayDecision`. |
| CA-F1-03: sono <6h → sem qualidade + motivo, sem culpa | **PARCIAL** | `blockCopy(SHORT_SLEEP)` existe; sem readiness/valor de sono no card | A copy existe, mas não há valor nem teste de sono no F1. |
| CA-F1-04: dia de prova mostra evento, sem prescrição/treino | **PROBLEMA** | Ver P-02 | O teste passa com uma sessão artificial na data da prova; produção não tem essa sessão. |
| CA-F1-05: sem plano → `Empty` com CTA, sem crash/número inventado | **PROBLEMA** | `CoachNextCard.kt:49-67` mostra “Semana concluída”; `CoachScreen.kt:211-231` não tem CTA | O caminho existe em texto, mas o card de F1 dá mensagem errada e não há ação. |
| CA-F1-06: build sem token continua determinístico; IA ausente não é erro | **PARCIAL** | F1 é determinístico; `DisabledCoachApi` cai em `Fallback` com “Sem conexão” | Determinismo OK, mas só depois do opt-in; sem consentimento o F1 inteiro desaparece. Ver P-03. |

### 3.2 F2 — Guardas de carga e janela

| Requisito | Status | Evidência | Comentário |
|---|---|---|---|
| Card abaixo de F1, visível só com ≥1 guarda; senão linha “Nenhum bloqueio ativo” | **OK/PARCIAL** | `CoachScreen.kt:192`; `CoachGuardsCard.kt:68-107` | Card sempre renderizado; lista vazia vira linha única, aceitável. Não há “colapsado” fora do card. |
| Cada guarda com ícone + título + detalhe + prazo/progresso + ação | **PARCIAL** | `CoachGuards.kt:46-128`; `CoachGuardsCard.kt:110-128` | Título/detalhe e progresso (calibração/data) existem; **não há ícone, ação nem “por que isso protege você”**. |
| Máximo 4 itens + “ver mais” local | **OK** | `CoachGuardsCard.kt:87-102`; teste `CoachScreenTest.kt:569-583` | Implementado e testado. |
| Calibração “X de 28 dias”, sem ACWR instável | **OK** | `CoachGuards.kt:131-135`; teste `CoachGuardsTest.kt:31-59` | Copy correta, sem ACWR. |
| Fonte >14d “dados desatualizados” | **PARCIAL** | `CoachGuards.kt:83-90` | Copy fixa “mais de 14 dias”; não mostra N dias, data da última importação nem ação de importar. |
| Recuperação pós-prova com teto/fim | **OK** | `CoachGuards.kt:47-58`; `RecoveryWindowTest`/`CoachGuardsTest` | Implementado; sem ação. |
| Taper/semana de prova | **PARCIAL** | `CoachGuards.kt:60-79` | Taper por proximidade e “micro-dose”; não mostra volume −45–55% nem ação/plano. |
| Monotonia/strain | **OK** | `CoachGuards.kt:94-117`; teste `CoachGuardsTest.kt:114-129` | Implementado; copy sem número no título. |
| Sono/check-in do dia | **FALTA** | Comentário em `CoachGuardsSource.kt:22-26` diz que é “F1”; F1 não tem readiness/sono | O requisito foi deslocado para uma tela que não o implementa. |
| Disponibilidade | **OK** | `CoachGuards.kt:120-128` | Implementado. |
| RN-F2-01: só exibe o que o motor bloqueou/manteve | **PARCIAL** | `CoachGuards` deriva de `LoadSnapshot`/provas/disponibilidade | Estrutural OK; guardas de sono/check-in e ações não existem. |
| RN-F2-02: calibração nunca reduz/veta; copy `G-LOAD-03` | **OK** | `CoachGuards.kt:92-96,131-135` | Sem ACWR. |
| RN-F2-03: recovery/taper prevalecem; prova nunca é descarga | **PARCIAL** | Ordem de `CoachGuards` (recovery, race, load) | Não modela deload explicitamente; a decisão está no motor, não no card. |
| RN-F2-04: sono suspeito → ausente, não `SHORT_SLEEP` | **FALTA** | Não há sono no F2 | A regra existe no motor, mas o card não a representa. |
| RN-F2-05: sem culpa/diagnóstico/nutrição/musculação | **OK** | Copy estática; sem números médicos | Compatível com ADR 0029. |
| CA-F2-01: 8/28 em calibração, sem `acwr=1,6` | **OK (domínio)** | `CoachGuardsTest.kt:46-59` | O domínio cobre; não há teste de tela com esse dado real. |
| CA-F2-02: fonte >14d com confiança reduzida | **PARCIAL** | `CoachGuards.kt:83-90` | Não mostra confiança, N dias nem ação. |
| CA-F2-03: janela pós-prova some após o fim | **OK (domínio)** | `RecoveryWindow.active`; `CoachGuardsTest` | Depende de `today`; sem teste de virada no app. |
| CA-F2-04: semana verde → linha única | **OK** | `CoachGuardsCard.kt:80-85`; teste `CoachScreenTest.kt:585-591` | Implementado. |
| CA-F2-05: offline/avião continua | **PARCIAL** | Cálculo local | Sem consentimento, a tela inteira é ocultada; com consentimento, funciona local. |

### 3.3 F3 — Reordenação + fallback acionável

| Requisito | Status | Evidência | Comentário |
|---|---|---|---|
| Ordem F1 → F2 → replan → explicação → revisão → F4 → histórico → Desativar | **OK** | `CoachScreen.kt:185-326`; teste `CoachScreenTest.kt:332-341` | Ordem implementada. O teste só compara replan × explicação, não a ordem completa. |
| Fallback mantém `planRationale` + 1–3 bullets determinísticos do porquê + ação útil | **PROBLEMA** | `CoachScreen.kt:259-301`; teste `CoachScreenTest.kt:316-329` | Mostra `explanation.reason` cru, uma frase fixa e “Tentar de novo”. Não há bullets determinísticos, nem ação de check-in/importar. O reason pode conter código interno. |
| Não gravar fallback em memória | **OK** | `CoachViewModel.recordSafely` só é chamado em `Valid`; teste `CoachViewModelTest.kt:485-493` | Preservado. |
| `Unavailable` sem plano troca “Em breve” por caminho com CTAs | **PARCIAL** | `CoachScreen.kt:211-231` | Copy existe, mas **não há botões/rotas**. O usuário lê “complete perfil/importe/calibre” e não consegue agir dali. |
| Manter replan, sheets, diff/selo, undo e confirmações | **OK** | `CoachReplanCard`, `ReplanSuggestionSheet`, `ReplanMessageCard`; testes de VM/tela | Nenhuma regressão observada. |
| CA-F3-01: sem token abre sem spinner infinito, JSON cru ou duplicação | **OK (estrutural)** | `DisabledCoachApi` → `IOException` → `Fallback`; sem memória | Não há teste de build sem token; a lógica cai em fallback. |
| CA-F3-02: explicação corrente e resumo visível não repetem no histórico | **OK** | `CoachHistory.kt:42-62`; `CoachViewModelTest.kt:615-651` | Preservado. |
| CA-F3-03: TalkBack topo→ação, fonte 200%, dark/light | **PARCIAL** | Só `CoachHistoryCard` tem `heading()`; `CoachHistoryTest` é o único teste de fonte 200%; novos cards sem teste de tema/fonte | Não há `heading()` em `CoachNextCard`, `CoachGuardsCard`, `CoachProgressCard`, `CoachExplanationCard` ou `CoachReviewCard`. |

### 3.4 F4 — Síntese de 4 semanas

**Decisão registrada:** F4 sai determinístico primeiro; a reescrita por IA fica como fast-follow. Portanto, **não** penalizo a ausência de IA, `coach-progress-v1` ou extensão de `allowedMinutes`. O que o PRD exige deterministicamente, porém, continua valendo.

| Requisito | Status | Evidência | Comentário |
|---|---|---|---|
| `CoachProgressCard` após a revisão | **OK** | `CoachScreen.kt:303-304`; `CoachProgressCard.kt` | Posição correta. |
| 4–6 linhas determinísticas + 1 frase-síntese | **PROBLEMA** | `CoachProgressCard.kt:99-110` | Há 6 linhas base + linha extra opcional = até 7; não há síntese. A linha “extra” é condicional. |
| Adesão “X/Y treinos do plano”, com extra fora da adesão | **PARCIAL** | `CoachProgressLabels.kt:26-29`; `PlanCompletionCalculator.kt:63-90` | Adeção usa `done`; extra não existe no numerador, mas `LEGACY_UNVERIFIED` conta como `done`. |
| Volume “base real forte N min → alvo M min (+6%) ou mantido + motivo” | **FALTA** | `CoachProgressSummary` não tem `strongMinutes`/`target`; `CoachProgress.kt:33-61` | Mostra `plannedMin` × `executedMin`, não a base real nem a progressão. |
| Intensidade “X% fácil (alvo ~80%) em N sessões com FC” | **PARCIAL** | `CoachProgressSource.kt:102-117`; `CoachProgressLabels.kt:40-43` | Mostra 28 dias, não as 4 semanas fechadas; não mostra N; inclui a semana corrente parcial. |
| Prontidão/sono “Prontidão média N, M noites curtas” | **PROBLEMA** | `WeeklyReviewAssembler.kt:46,117-119`; `CoachProgressLabels.kt:45-63` | Readiness só tem janela de 14 dias; semanas antigas ficam sem prontidão. Médias semanais não são ponderadas por dias medidos. |
| Calibração/VDOT “N (data) → paces E/T” ou “sem VDOT — RPE” | **PARCIAL** | `CoachProgressLabels.kt:65-80` | VDOT com fonte/data e corte de 56 dias; **não mostra paces E/T**. |
| Sem nota/streak/comparação entre pessoas | **OK** | `CoachProgressCard.kt:117-121` | Presente. |
| “Ver detalhes” → Tendências, sem duplicar gráfico | **OK** | `CoachProgressCard.kt:112-116`; `MeuCoachApp.kt:248` | Botão “Ver Tendências” navega; não há gráfico no Coach. |
| Excluir semana parcial corrente | **OK** | `closedStarts` em `CoachProgressSource.kt:141-145` | As 4 semanas são fechadas. |
| Excluir plano criado no meio da semana | **OK (parcial)** | `PlanCompletionCalculator.kt:63-65`; `WeeklyReviewAssembler.kt:67` | A adesão corta antes de `createdOn`; o volume planejado também. |
| Excluir deload/taper/recovery da base | **FALTA** | Nenhum uso de `VolumeReference.specialWeek` (`VolumeReference.kt:82-104`) no Coach | F4 agrega todas as semanas disponíveis. |
| RN-F4-01: progressão só com base + guardrails | **FALTA** | `CoachProgress.kt` não calcula progressão | Não há “+6%”, “mantido + motivo” nem checagem de ACWR/monotonia/strain/prontidão. |
| RN-F4-02: extra conta volume, não adesão; `LEGACY_UNVERIFIED` fora da base | **PROBLEMA** | `PlanCompletionCalculator.kt:74-90`; teste `CoachReviewSourceTest.kt:148-151,113-117` | `done` inclui legado. O teste espera `done=1` com uma sessão `LEGACY_UNVERIFIED`. |
| RN-F4-03: IA só cita minutos/percentuais permitidos | **N/A** | Decisão de F4 determinístico | Não há IA em F4. |
| RN-F4-04: estimativa só textual, nunca número | **N/A** | Sem IA/estimativa | Preservado. |
| RN-F4-05: sem 4 semanas → “X de 4; parcial”, sem extrapolar | **PARCIAL** | `CoachProgressLabels.kt:23-24`; `CoachProgressSource.kt:78-117` | O aviso existe, mas `availableWeeks` só conta `AVAILABLE` (com plano); semanas com corridas e sem plano somem. |
| CA-F4-01: caso `G-VOL-02` (base 142, alvo 151, adesão 3/3) | **PROBLEMA** | Não há golden; `CoachProgressTest.kt:17-37` usa valores sintéticos 114/129 | O card mostraria “Planejado 1h54 · feito 2h09”, não base 142/alvo 151. |
| CA-F4-02: `LEGACY_UNVERIFIED` não infla base nem adesão | **PROBLEMA** | Ver RN-F4-02 | O teste atual codifica o comportamento contrário. |
| CA-F4-03: percentual inventado na IA é descartado | **N/A** | Sem IA em F4 | Decisão registrada. |
| CA-F4-04: <2 semanas → “parcial” sem prometer tendência | **PARCIAL** | `CoachProgressCard.kt:77-88`; `CoachProgressLabelsTest.kt:20-24` | 0 semanas → “Pouco histórico”; 1 semana → “1 de 4 · parcial”. Se as corridas existem sem plano, o card diz “Pouco histórico” incorretamente. |

### 3.5 Requisitos não funcionais

| Tema | Status | Evidência | Comentário |
|---|---|---|---|
| Usabilidade: uma decisão por scroll, F1 em ≤10s, linguagem de treinador, números com unidade, sem sigla no título | **PARCIAL/PROBLEMA** | F1 sem semáforo; hint de ACWR em `CoachNextLabels.kt:42` + `QualityGate.kt:287`; estados contraditórios | A ordem ajuda, mas o topo não responde à pergunta; há sigla técnica no card. |
| Desempenho: F1/F2/F4 <500ms, derivação sob demanda, sem worker | **NÃO COMPROVADO / RISCO** | `CoachProgressSource.kt:63-134`; `WeeklyReviewAssembler.kt:42-94`; `CoachViewModel.kt:208-397` | Cálculos pesados e consultas repetidas no coletor; sem `flowOn`; sem benchmark. Ver P-20. |
| Confiabilidade: falha de rede/schema/número → fallback; aba nunca quebra; memória só `Valid`; `recordSafely` | **PARCIAL** | Fallback existe; `recordSafely`; porém F2/F4 congelam e estados vazios mentem | Não quebra, mas entrega estado incorreto. |
| Privacidade/LGPD: opt-in, payload minimizado, sem nome/GPS/timestamps, TTL/revogação/export/delete, sem payload em log | **PARCIAL** | `CoachConsentStore`; `CoachPromptBuilder`; `CoachMemoryPolicy`; `HttpCoachApi.kt:81` + `CoachViewModel.kt:312-325` | Payload e retenção OK; o corpo de erro do proxy é logado (até 200 chars), potencialmente com dados do prompt. |
| Acessibilidade: `heading()`, linhas agrupadas, `contentDescription`, contraste AA, 48dp, fonte 200%, dark/light, sem depender só de cor | **PARCIAL** | `heading()` só em `CoachHistoryCard.kt:75,283`; testes de 200% só em `CoachHistoryTest.kt:159`; sem teste light dos novos cards | Os novos cards não têm heading; sem evidência de 200%/light. |
| Consistência: tokens, lima só ação, sem hex, `McCard/McPill/McStat`, mesmo semáforo do Hoje, selo `v{n}` | **OK/PARCIAL** | Sem hex nos arquivos do Coach; componentes do design system; selo do replan intacto | Semáforo não existe, então “mesmo semáforo do Hoje” não se aplica. |
| Testes/qualidade: `assembleDebug`, `check`, `spotlessCheck`, `koverVerify`; sem migração destrutiva; sem dado real | **PROBLEMA** | `check` falha em `:app:detekt`; demais verdes | Ver seção 7. |

---

## 4. Auditoria técnica

### 4.1 Arquitetura e separação de responsabilidades

**Pontos fortes**

- `core:domain/coach/*` é puro: `CoachNextResolver`, `CoachGuards`, `CoachProgress`, `CoachResponseValidator` não importam Android/Room/Hilt/rede.
- As fontes em `app/coach/*` são read-only e espelham `CoachPlanSource`/`CoachReviewSource`.
- F4 reutiliza `WeeklyReviewAssembler`, que é a montagem única de semana fechada (bom para não divergir de Tendências).
- F2 reutiliza `TrainingSignalSource`, `RecoveryWindow` e `LoadSnapshot`; não reimplementa o motor.
- O replanejamento continua centralizado em `ReplanController`; a aba não aplica plano sem confirmação.
- Não há migração, schema novo, worker novo ou dependência nova.

**Problemas**

1. **`CoachViewModel` com 10 dependências de construtor** (`CoachViewModel.kt:155`). O detekt reprova (`LongParameterList`, limite 8). Além do gate, isso indica que a ViewModel está acumulando responsabilidades: consentimento, explicação, revisão, próximo, guardas, evolução, memória, replanejamento, API e escopo. Um `CoachSources`/`CoachDependencies` ou um caso de uso de composição reduziria o acoplamento sem reescrever a tela.
2. **Fontes com semântica misturada.** `RoomCoachGuardsSource` combina perfil, provas, sinais e resolução; `RoomCoachProgressSource` combina perfil, planos, atividades, provas, assembler e intensidade. Funciona, mas cada fonte virou um “mini use case” sem testes de reatividade.
3. **Duplicação de cálculo com Tendências.** `RoomCoachProgressSource` repete a lógica de intensidade de `TrendsViewModel.kt:162-176` (28 dias, `openActivity`, `IntensityDistributionCalculator`). Se a regra mudar, os dois podem divergir. O PRD queria reutilização; aqui há duplicação de composição, embora o `WeeklyReviewAssembler` seja compartilhado.
4. **`CoachPlanSource` é o único elo não reativo da explicação.** Ele só emite em mudança de semana (`RoomCoachPlanSource.kt:26-40`), não observa `observeWeek`. Depois de um replanejamento aceito na mesma semana, a explicação continua descrevendo o plano antigo. O `ReplanMessageCard` diz “semana ajustada” e o card de explicação abaixo pode dizer outra coisa. Isso é uma incoerência de produto, não só de código.
5. **`CoachReviewSource` também só muda com o dia** (`RoomCoachReviewSource.kt:50-55`). Importar uma corrida da semana fechada depois de abrir a aba não atualiza a revisão; Tendências recalcula ao abrir, o Coach não.

### 4.2 Gerenciamento de estado

**O que está bom**

- `sealed interface` para `CoachExplanationState`, `CoachReviewState`, `CoachNextUiState`, `CoachGuardsUiState`, `CoachProgressUiState` e `CoachHistoryState`; estados explícitos e `retry` separado.
- `flatMapLatest` para consentimento; revogar oculta as fontes; `retry()` reinicia os coletores.
- Memória e histórico têm estado `Hidden` sem opt-in; não vazam para a tela.

**Problemas**

1. **`CoachExplanationState.Unavailable` significa três coisas:** sem consentimento, sem plano e “ainda não carregou”. O estado inicial é `Unavailable` (`CoachViewModel.kt:178-180`). Com consentimento e plano, a tela pode mostrar por um instante “Monte seu plano para começar” antes de o plano chegar. Falta um estado `LoadingPlan`/`NoPlan` distinto.
2. **`CoachNextUiState.Empty` significa “sem plano” e “sem sessão futura”.** O card mostra “Semana concluída” nos dois casos (`CoachNextCard.kt:49-67`). Sem plano, isso é falso. Falta `NoPlan` ou um `CoachNextContext` com `hasPlan`.
3. **`CoachGuardsUiState.Ready(emptyList())` significa “sem perfil” e “sem bloqueios”.** `RoomCoachGuardsSource` devolve lista vazia quando `profile == null` (`CoachGuardsSource.kt:47-49`), e a UI mostra “Nenhum bloqueio ativo — siga o plano.” (`CoachGuardsCard.kt:80-85`). Para um usuário sem perfil/plano, é uma falsa tranquilidade.
4. **`Hidden` é usado como “sem consentimento”.** Isso é correto para privacidade da IA, mas foi aplicado também aos cards determinísticos. Ver P-03.
5. **`retry()` é global.** O retry de F1/F2/F4 zera `lastHash`/`lastReviewHash` e reinicia a explicação/revisão. Um erro local de banco em “próximo treino” pode disparar duas chamadas de IA desnecessárias. Não é grave, mas mistura recuperação local com custo de rede.

### 4.3 Reatividade e ciclo de vida dos dados

Esta é a falha técnica mais sistemática da evolução.

| Fonte | Gatilhos observados | O que falta observar | Consequência |
|---|---|---|---|
| `RoomCoachNextSource` | `time.today`, `plans.observeWeek`, `prescriptions.observeLatest` | `raceEvents.observeAll` (a prova é lida uma vez em `flow { emit(...) }`, `CoachNextSource.kt:59-62`) | Adicionar/editar prova não atualiza F1 até troca de dia/semana ou retry |
| `RoomCoachGuardsSource` | `time.today`, `profiles.observeProfile`, `raceEvents.observeAll` | atividades, check-ins, métricas, execuções (lidas em `snapshot`, `CoachGuardsSource.kt:50`) | Importar corrida, fazer check-in ou registrar RPE não recalcula calibração/fonte/monotonia |
| `RoomCoachProgressSource` | `time.today`, `profiles.observeProfile`, `raceEvents.observeAll` | planos, atividades, check-ins, métricas (lidas em `load`, `CoachProgressSource.kt:72-118`) | Importar corrida da semana fechada ou concluir sessão não atualiza a evolução |
| `RoomCoachPlanSource` | `time.today` (semana) | revisões do plano (`observeWeek`) | Explicação fica velha após replanejamento aceito na mesma semana |
| `RoomCoachReviewSource` | `time.today` (semana fechada) | atividades/planos da semana fechada | Revisão fica velha após importação retroativa |

**Impacto:** o PRD exige “informações atualizadas corretamente” e “F1 reobserva e atualiza sem reload manual”. A implementação não tem esse contrato. Os testes de fonte usam `first()`/`first { ... }`, que não exercitam uma segunda emissão após escrita; o único teste de reatividade é `CoachProgressSourceTest.kt:139-152` (“reemite quando o perfil muda”), que cobre apenas perfil.

**Direção de correção:** observar as fontes Room relevantes (`activities.observeActivitySummaries()`, `checkIns.observe...`, `metrics.observe...`, `executions.observe...`, `plans.observeWeek(...)`) ou expor um `CoachSignalsSource` que combine esses flows e recalcule com `debounce`/`distinctUntilChanged`. Para F1, `raceEvents.observeAll` com filtro da data. Para o histórico, manter o comportamento atual.

### 4.4 Persistência e integridade

**Pontos fortes**

- Nenhuma fonte nova escreve em `daily_prescriptions`, `weekly_plans`, perfil ou check-in.
- F1 não persiste em `coach_messages` (decisão registrada).
- F4 não persiste nada; é agregação em memória.
- Memória do Coach continua só com `Valid`; fallback/timeout/schema inválido não é gravado (`CoachViewModel.kt:282-310, 353-385`).
- Revogação apaga `coach_messages`/`coach_summaries` em `appScope` (`CoachViewModel.kt:496-505`).
- Não há `fallbackToDestructiveMigration`.

**Problemas de integridade de leitura**

1. `WeeklyReviewAssembler` lê `allCheckIns()`/`allMetrics()` uma vez por semana montada (`WeeklyReviewAssembler.kt:42-46`). Em F4, isso ocorre 4 vezes, com possibilidade de leituras inconsistentes se houver escrita entre elas. É baixo risco em um app local, mas é uma montagem “quase snapshot” que poderia ser feita uma vez.
2. `PlanCompletionCalculator` conta `LEGACY_UNVERIFIED` como `done` (`PlanCompletionCalculator.kt:74-90`). Isso é documentado como “adesão não é prova”, mas o PRD F4/CA-F4-02 pede legado fora da base e da adesão. O teste `CoachReviewSourceTest.kt:148-151` e `CoachProgressSourceTest.kt:164-167` usa `LEGACY_UNVERIFIED` e espera `done=1`, então o teste **protege o comportamento divergente**.
3. O volume de F4 usa `WeeklyReview.volume`, que inclui qualquer `ActivitySummary` de corrida/esteira (`WeeklyReviewAssembler.kt:47-63`), inclusive `ActivitySource.MANUAL`, se existir. `VolumeReferenceProjection` exclui `MANUAL` (`VolumeReferenceProjection.kt:39-42`). A regra de “volume forte” não é aplicada no F4.
4. `extraMin` também não filtra `MANUAL`; a linha “treino fora do plano” pode creditar uma entrada manual.

### 4.5 Desempenho e recursos

**Fatos**

- `CoachViewModel.init` dispara, em paralelo: histórico, explicação, revisão, próximo, guardas e progresso (`CoachViewModel.kt:208-397`). F2 e F4 leem atividades/perfil/fontes mesmo que o usuário só queira ver o próximo treino.
- `RoomCoachProgressSource` monta 4 semanas com `WeeklyReviewAssembler`. Cada `assemble` chama `getWeek`, `allCheckIns`, `allMetrics` e `readinessHistory` (que por sua vez lê check-ins e métricas de novo). São dezenas de consultas por abertura, sem cache.
- `intensitySessions` é calculado **antes** de verificar se há LTHR (`CoachProgressSource.kt:102-110`): sem LTHR, ainda abre e decodifica samples de até 28 dias para depois descartar.
- `openActivity` decodifica o blob de samples (`ActivityRepository.kt:325-355`); para 28 corridas, isso pode ser caro.
- Não há `flowOn(Dispatchers.Default/IO)` nas fontes; a montagem de listas, cálculo de prontidão, `IntensityDistributionCalculator` e o `WeeklyReviewCalculator` rodam no contexto do coletor (ViewModel `Main`), depois que as consultas suspend retornam.
- A tela é uma `Column` com `verticalScroll`, não `LazyColumn`; o histórico completo é composto de uma vez.

**Hipótese:** em um aparelho médio, com 4 semanas de corridas com samples, a abertura da aba pode passar de 500 ms e competir com as duas chamadas de IA. Não foi medida. O PRD exige <500 ms; não há benchmark nem teste de performance. Ver P-20.

### 4.6 Erros, segurança e privacidade

**O que está bom**

- `catch` por fonte; erro não derruba a tela.
- `recordSafely` impede falha de memória de quebrar a UI.
- `CoachResponseValidator` barra números, siglas, códigos e schema inválido; fallback não persiste.
- Prompt sem nome/GPS/timestamps exatos; memória com TTL; revogação apaga.
- `CoachScreen` não exibe `decisionDetail` cru; apenas `blockCopy` + `unlockHint` (e o problema do hint está abaixo).

**Problemas**

1. **Vazamento de código interno no fallback.** `CoachScreen.kt:275-280` exibe `explanation.reason`; `CoachResponseValidator.kt:95` retorna “Resposta contém código interno: $internalCode”. O teste `CoachScreenTest.kt:316-329` até valida a exibição de um reason técnico. Isso contraria a decisão 22 e o `F-LEAK-01`.
2. **Falso positivo grave do validador.** `forbiddenTokens` contém `"atl"` (`CoachResponseValidator.kt:26`) e a checagem é substring (`CoachResponseValidator.kt:75`). A palavra portuguesa **“atleta”** contém `"atl"`. Qualquer resposta que use “atleta” vira `Invalid` com reason “Resposta contém métrica proibida: atl”. Isso reduz a taxa de respostas válidas e, com o fallback cru, mostra um motivo absurdo ao usuário.
3. **Hint cru de ACWR.** `CoachNextLabels.kt:42` anexa `prescription.unlockHint`; para `ACWR_HIGH`, o hint é “Mantenha a carga estável até o ACWR voltar a 1,50 ou menos.” (`QualityGate.kt:287`). O card exibe sigla técnica e número no topo, contra a decisão de “camada de copy” e o NFR de sigla só em detalhe.
4. **Log do corpo de erro do proxy.** `HttpCoachApi.kt:81` inclui `body.take(200)` na `IOException`; `CoachViewModel` loga `e.message` (`CoachViewModel.kt:314,322`). Se o proxy devolver o payload/prompt em um 400, o log pode conter dados de treino. O PRD diz “sem payload em log”. O risco é limitado (200 chars, sem nome/GPS), mas é uma violação de contrato.
5. **Timeout de 45 s e sem retry no app.** `HttpCoachApi.kt:57` usa `timeoutMs = 45_000`; o PRD de evolução pede 10 s + 1 retry. O proxy tenta múltiplos modelos; a aba pode ficar 45 s em `Loading`. O fallback só aparece depois.
6. **API desabilitada vira “Sem conexão”.** `DisabledCoachApi` lança `IOException("coach disabled")`; o `catch (IOException)` exibe “Sem conexão com o Coach” (`CoachViewModel.kt:312-318`). Em build sem token, o motivo é falso. Melhor um estado “IA não configurada” separado.
7. **Review esconde observações determinísticas quando a IA responde.** `CoachReviewCard.kt:168-183` só mostra `review.observations` quando `state !is Ready`. Se a IA gerar um texto genérico e não mencionar doença/lesão, sono curto ou adesão, o fato determinístico desaparece. A IA só deveria reescrever, não apagar sinais de segurança.

### 4.7 Compatibilidade e risco de regressão

- A ordem da tela mudou, mas as tags `coach-*-card` foram mantidas; os testes de histórico e replan continuam passando.
- `CoachViewModel` ganhou 3 fontes e 10 parâmetros; os testes foram atualizados, mas o detekt reprova.
- Nenhuma migração; `MigrationTest` não é afetado.
- `CoachScreen` novos parâmetros têm defaults, reduzindo quebra de chamadas.
- O maior risco de regressão não é técnico, é de **produto**: ocultar F1/F2/F4 sem consentimento e mostrar estados vazios contraditórios muda a percepção da aba inteira.
- `CoachPlanSource`/`CoachReviewSource` não foram alterados; a falta de reatividade é pré-existente, mas a evolução depende deles e não a corrigiu.

---

## 5. Auditoria funcional e de experiência

### 5.1 A pergunta “o que eu faço agora/amanhã?”

**Não é respondida com segurança.**

- O topo mostra o **planejado**, não o **prescrito**. Se o dia foi rebaixado, o título ainda diz “Ritmo forte”; se foi descanso, ainda diz o treino. A linha de status tenta corrigir, mas o título é o elemento de maior hierarquia.
- Não há semáforo. O corredor não vê “Alta/Moderada/Baixa”, nem sono, nem score. O mesmo vocabulário do Hoje não existe no Coach.
- Se não houver prescrição para hoje, o card diz “A decisão do dia sai no Hoje” e oferece “Ver no Hoje”. Isso transforma o Coach em uma tela que manda o usuário para outra tela para decidir; o PRD queria o contrário (Coach como entrada diária).
- Para amanhã, o card também oferece “Ver no Hoje”; a aba Hoje mostra o treino de hoje, não o de amanhã. A ação é contextualmente errada.

### 5.2 Coerência entre dados e orientação

| Situação | O que o Coach mostra | O que o motor/plano diz | Problema |
|---|---|---|---|
| Qualidade rebaixada para fácil | “Hoje · Ritmo forte · 35 min” + “Ajustado para fácil” | Prescrição fácil, 25 min | Contradição no elemento principal |
| Dia de descanso | “Hoje · Fácil/Longão · X min” + “Descanso.” | Prescrição `REST` | Contradição |
| Prova hoje/amanhã | “Semana concluída” ou próximo treino | Evento de prova; Hoje mostra competição | Estado de prova perdido |
| Sem plano | “Semana concluída” + “Nenhum bloqueio ativo” + “Monte seu plano” | Sem plano | Três mensagens incompatíveis |
| Sem perfil | “Semana concluída” + “Nenhum bloqueio ativo” + “Pouco histórico” | Sem perfil | Falsa normalidade |
| Semana com corridas, sem plano | “Pouco histórico ainda. Importe treinos e monte o plano.” | Corridas reais existem | F4 descarta o volume real |
| Após replanejamento | Explicação antiga + mensagem “semana ajustada” | Plano novo | Coerência quebrada |
| Após importar corrida | Guardas/evolução antigos | Atividade nova | Dados velhos |

### 5.3 Atualização da informação

O usuário não tem como **atualizar** F2/F4 quando estão `Ready`. O único retry visível é o de erro; o `retry()` global não tem botão no estado feliz. Importar, fazer check-in ou concluir treino não reemite. Isso é particularmente ruim no Coach, cuja proposta é ser consultado diariamente.

### 5.4 Navegação e ações

- **F1:** “Fazer check-in” existe apenas em um caso; “Ver no Hoje” é a única ação nos demais. Não há “Ver como correr” (briefing), “Importar”, “Ver por quê” ou ação para sono/recuperação.
- **F2:** nenhuma ação. O card descreve a trava, mas não leva a check-in, importação, Hoje ou plano. O PRD pedia ação quando existir.
- **F3:** o card “Monte seu plano” não tem CTA, apesar de o texto mandar completar perfil/calibração/importar.
- **F4:** “Ver Tendências” funciona; é a única navegação nova bem resolvida.
- **Consentimento:** “Depois” esconde a tela inteira e não deixa um botão “Ativar Coach” no lugar. O usuário só recupera o card se a ViewModel for recriada/reabrir o processo; não há controle em Dados/Perfil.
- **Duplicação:** “Desativar” aparece no fallback e de novo no fim; em uma falha de IA, o usuário é convidado a desativar a aba inteira.

### 5.5 Hierarquia e densidade

A ordem está boa: decisão → travas → ajuste → explicação → revisão → evolução → arquivo → desativar. A explicação longa deixou o topo. O histórico continua embaixo. Isso atende ao objetivo de hierarquia.

Porém, em estados vazios a tela fica com **5 cards contraditórios** (F1 vazio, F2 vazio, replan, explicação indisponível, evolução vazia). Em uma primeira abertura sem perfil, o usuário vê “Semana concluída”, “Nenhum bloqueio ativo”, “Ajustar a semana”, “Monte seu plano” e “Pouco histórico”. Falta um estado de onboarding único, com um caminho claro.

### 5.6 Copy, confiança e credibilidade

- “Semana concluída” sem plano é falso.
- “Nenhum bloqueio ativo — siga o plano” sem plano é falso.
- “A decisão do dia sai no Hoje” para amanhã é enganoso.
- “Resposta contém código interno: …” e “Resposta contém métrica proibida: atl” quebram a ilusão de linguagem de treinador.
- “ACWR … 1,50” no topo é tecnicês.
- “Sono médio 6h40 · sem curta.” é gramaticalmente estranho e ambíguo; o correto seria “sem noites curtas” ou “2 noites curtas”.
- O subtítulo “Sua semana, a revisão anterior e os ajustes” não reflete a nova tela (próximo treino, travas, evolução). O PRD pede “O que fazer, o que trava, como você evolui”.
- “PRÓXIMO TREINO” sobre “Dia de prova” é uma overline que contradiz o conteúdo.

### 5.7 Acessibilidade

- Só o histórico usa `heading()`. Os títulos “PRÓXIMO TREINO”, “TRAVAS DA SEMANA”, “EXPLICAÇÃO DA SEMANA”, “REVISÃO DA SEMANA PASSADA”, “EVOLUÇÃO · 4 SEMANAS” não são headings para TalkBack.
- O título “Coach” também não é heading.
- Os testes de fonte 200% e tema claro cobrem apenas `CoachHistoryCard`; os cards novos não têm teste de 200%, light/dark, corte de texto ou ordem de foco.
- Não há teste de TalkBack para o caminho topo→ação; a ausência de headings tende a piorar a navegação por seções.
- Os botões usam componentes com altura mínima de 56 dp; isso é bom. O `TextButton` “Desativar” depende do mínimo interativo do Material; não foi medido.

### 5.8 O que a experiência entrega bem

- A aba abre com o próximo treino antes do texto longo.
- O replan continua com diff, confirmação e undo, sem mudança.
- O histórico é somente leitura e some sem opt-in.
- A revisão combina narrativa com fatos determinísticos (adesão, volume, prontidão, sono) e não transforma ausência em zero.
- A evolução não usa nota/streak e tem uma linha de VDOT com proveniência.
- O visual usa o design system, sem hex solto; a identidade se mantém.

---

## 6. Regras de negócio e inteligência do Coach

### 6.1 F1 — dados insuficientes para a conclusão que apresenta

O card afirma coisas sobre o dia sem os insumos que o PRD exige:

| Pergunta | Dado necessário | O que a implementação usa | Suficiente? |
|---|---|---|---|
| “Posso forçar?” | `ReadinessLevel`, score, sono, check-in, `SUBJECTIVE_MISSING` | `DailyPrescription.qualityBlockReason`/`adjustment` (se existir) | **Não**: não há prontidão; só o veredito já persistido |
| “Qual treino?” | `prescribedType`, `title`, `prescribedDurationMin`, alvos | `PlannedSession` | **Não**: mostra o planejado, não o prescrito |
| “O que desbloqueia?” | `unlockHint` mapeado por motivo + ação | `unlockHint` cru + ação só para check-in | **Parcial**: texto sim, ação quase nunca |
| “É dia de prova?” | `RaceEvent` na data relevante, com precedência | `RaceEvent` na data da sessão escolhida | **Não**: prova não encontrada se não houver sessão |
| “Os dados estão velhos?” | `LoadDataState.STALE`/última importação | Nada | **Não** |

A conclusão “Mantido/Reduzido/Ajustado” é determinística, mas o **título** não é. O usuário precisa reconciliar duas afirmações contraditórias. Em um app que se posiciona como conservador, isso é grave.

### 6.2 Precedência de prova e recuperação

O PRD é explícito: “Dia de prova tem precedência e mostra ‘Dia de prova’ sem prescrição (seguir `GetTodayDecision`: prova antes de prescrição)”. A implementação usa `CoachNextResolver` sobre `plan.sessions` **antes** de olhar provas. O `RaceEvent` só é consultado para `next.session.date` (`CoachNextSource.kt:59-62`).

O gerador, por sua vez, remove a data do evento das sessões comuns (`WeeklyPlanGenerator.kt:247-262`): “A race day never receives an ordinary session”. Logo:

- Prova no domingo, sem sessão no domingo → `CoachNextResolver` não encontra sessão futura na semana → `Empty` “Semana concluída”.
- Prova na quinta, com sessões na sexta/domingo → `CoachNextResolver` escolhe sexta e consulta prova de sexta → sem prova; o Coach mostra treino normal.
- Se uma prova for adicionada depois do plano, o plano pode ter uma sessão na data (legado), e aí o card mostra “Dia de prova”; é o caso que os testes constroem.

Isso não é um detalhe de borda: é o estado de maior consequência do calendário. A conclusão “Dia de prova” está implementada, mas **quase nunca é acionada**.

### 6.3 F2 — guardas corretas no domínio, incompletas no produto

O `CoachGuards` é determinístico e conservador: recovery, race week, taper, stale, calibration, monotony, strain, availability. A copy evita diagnóstico e não inventa ACWR. Isso é bom.

Mas o PRD pedia **ação**, **prazo/progresso** e **“por que isso protege você”**. O card é uma lista de textos. O corredor entende a trava, mas não consegue agir. Em uma semana com “Dados desatualizados”, ele não tem botão para importar; com “Semana monótona”, não vê sugestão; com “Base em calibração”, não vê o que falta além do número.

Além disso, o PRD listava sono/check-in como guardas; o código as removeu com a justificativa de que F1 cuidaria disso. F1 não cuida. O resultado é uma lacuna, não uma deduplicação.

### 6.4 F4 — a “evolução” não é evolução

O card tem números reais e não gamifica. Mas ele não responde à pergunta do PRD:

- **Adesão:** é `done/planned`; não é uma tendência.
- **Volume:** é `executed/planned`; não é a base real (`strongMinutes`) nem o alvo de progressão.
- **Intensidade:** 28 dias corridos, incluindo a semana parcial; não são as 4 semanas fechadas.
- **Prontidão:** só as últimas 2 semanas (janela de 14 dias do assembler), e a média das médias semanais não pondera dias medidos.
- **Sono:** 4 semanas, mas sem cobertura por semana.
- **VDOT:** valor e data, sem paces E/T.

Não há uma frase que diga “você está evoluindo” ou “manteve”. A conclusão fica por conta do usuário, olhando seis números. O PRD queria 4–6 linhas + síntese; a implementação entrega 6–7 linhas sem síntese.

O caso canônico `G-VOL-02` (3×38 concluídas + extra 28 → base 142, alvo 151, adesão 3/3) não é reproduzido: o card mostraria “Planejado 1h54 · feito 2h09 (113%)”. Os testes não usam esse golden; usam `WeeklyAdherence(3,3,3,0,0,0)` e `WeeklyVolume(114,129,113,28)`.

### 6.5 Ausência de dados vs. zero

O motor e o `WeeklyReview` fazem bem: ausência vira `null`, não zero. Mas a **apresentação** de F4 quebra essa regra em dois pontos:

1. `CoachProgress.summarize` converte `volume?.executedMin ?: 0` em zero (`CoachProgress.kt:37-38`). Se existe plano e **nenhuma** corrida, `executedMinutes` vira `0` e `volumePercent` vira `0`; a linha mostra “feito 0 min (0%)” (`CoachProgressLabels.kt:31-35`). Pode ser lido como execução medida, não como ausência de registro. O rótulo deveria distinguir “nenhum treino registrado” de “0 min medidos”, sobretudo no produto que adota “ausente é null, nunca 0”.
2. Sem plano, mas com corridas, o `WeeklyReview` tem status `EMPTY_NO_PLAN` e volume executado; `CoachProgress.availableWeeks` filtra só `AVAILABLE` e descarta a semana. O card diz “Pouco histórico ainda” mesmo com corridas reais. O usuário novo é justamente quem mais precisa ver volume.

### 6.6 Regras conflitantes / critérios inconsistentes

| Conflito | Onde | Efeito |
|---|---|---|
| Planejado vs. prescrito | `CoachNextLabels` vs `DailyPrescriptionEngine` | Título pode recomendar treino que o motor rebaixou |
| F1 vs. Hoje em dia de prova | `CoachNextResolver` vs `GetTodayDecision` | Coach e Hoje podem discordar no dia mais importante |
| F2 “qualidade pausada” vs. cold-start | `CoachGuards.kt:83-90` vs `QualityGate.coldStartApplies` | Em cold-start, a fonte stale pode manter uma qualidade curta; a guarda afirma pausa total |
| F4 “adesão” vs. `LEGACY_UNVERIFIED` | `CoachProgress`/`PlanCompletionCalculator` | Legado conta como concluído, contra CA-F4-02 |
| F4 “4 semanas” vs. “28 dias” | `CoachProgressSource` vs `WeeklyReview` | A intensidade inclui a semana parcial e exclui dias da semana mais antiga |
| Review determinística vs. IA | `CoachReviewCard.kt:168-183` | Observações de segurança desaparecem quando a IA responde |
| Copy layer vs. `unlockHint` cru | `CoachNextLabels.kt:42` | ACWR e números técnicos no topo |
| “Sem bloqueio” vs. sem perfil | `CoachGuardsSource`/`CoachGuardsCard` | Falsa tranquilidade |

### 6.7 Segurança e progressão

- **O motor não é contornado:** nenhuma fonte nova prescreve, aumenta carga ou altera plano. O risco de progressão inadequada por parte do motor não aumentou.
- **A apresentação pode induzir:** o título planejado em F1 pode levar o corredor a fazer qualidade em um dia rebaixado. Isso é o risco de segurança mais concreto.
- **Sinais de fadiga podem sumir:** a revisão esconde observações determinísticas quando a IA responde; sono curto/doença podem não aparecer se o texto da IA for genérico.
- **Conservadorismo visível:** F2 e F4 não incentivam progressão; F4, na verdade, não fala de progressão. Não há “streak”, “meta” ou cobrança. Isso é bom.
- **Dados ausentes:** F1 não inventa; F2 não inventa ACWR; F4 não inventa médias quando não há semanas. Os problemas são de **cobertura** e **rótulo**, não de número inventado.

### 6.8 Repetição, genericidade e relevância

- F2 tem 8 tipos de guarda, mas o texto é sempre título + detalhe; sem ação, a leitura é passiva.
- F4 tem sempre as mesmas linhas, mudem ou não os números; não há foco semanal (“esta semana o objetivo é X”) nem comparação.
- O fallback é genérico (“O Coach não conseguiu explicar desta vez”) e técnico no reason.
- A explicação semanal e a revisão continuam genéricas por natureza; a IA pode ajudar, mas o validador e o fallback precisam ser confiáveis.

---

## 7. Testes e validação

### 7.1 O que passou

- **141 testes focados do Coach**: 87 de UI, 15 de fontes, 39 de domínio; 0 falhas.
- `assembleDebug`, `spotlessCheck`, `koverVerify`, `lintDebug`: verdes.
- Cobertura de linha do domínio do Coach é alta: `CoachGuards` 98,5%, `CoachProgress` 100%, `CoachNextResolver` 100%, `CoachResponseValidator` 97,7%, `CoachReviewPromptBuilder` 100%.
- Cobertura de linha das classes de app do Coach é razoável: `CoachViewModel` 86,9%, `CoachNextCard` 79,1%, `CoachProgressCard` 87,7%, `RoomCoachProgressSource` 89,8%.
- **Cobertura de branch é fraca nos pontos de decisão**: `CoachScreenKt` 38,4%, `CoachViewModel` 50%, `CoachNextCard` 61,5%, `CoachProgressCard` 57,7%, `RoomCoachProgressSource` 42,9%. O gate do app é de linha (78%), não de branch/estado.

### 7.2 O que está vermelho

`./gradlew check` falha em `:app:detekt`:

```text
CoachProgressLabels.kt:69:13: This condition is too complex (4).
  Defined complexity threshold for conditions is set to '4' [ComplexCondition]

CoachViewModel.kt:155:41: The constructor(...) has too many parameters.
  The current threshold is set to 8. [LongParameterList]
```

Isso contraria o Definition of Done do `AGENTS.md` e o critério de fase do PRD (“`check`, `spotlessCheck`, `koverVerify` verdes”). O commit mais recente rodou apenas `assembleDebug`/`spotlessCheck`/suítes focadas, não `check`.

### 7.3 Testes que protegem premissas erradas

1. **Prova com sessão artificial.**
   - `CoachNextSourceTest.kt:111-119` cria uma prova em `monday.plusDays(2)` e também uma `PlannedSession` na mesma data.
   - `CoachScreenTest.kt:371-402` monta `CoachNextContext` com sessão na data da prova.
   - O gerador remove sessões na data do evento (`WeeklyPlanGenerator.kt:247-262`). Os testes passam em um cenário que a produção não gera. Não há teste “prova hoje/amanhã sem sessão”.

2. **Legado contado como adesão.**
   - `CoachReviewSourceTest.kt:148-151` usa `LEGACY_UNVERIFIED`; `:113-117` espera `done=1`.
   - `CoachProgressSourceTest.kt:164-167` faz o mesmo; `:131-134` espera `done=1`.
   - CA-F4-02 diz que legado não infla adesão. O teste atual **exige** o comportamento divergente.

3. **Teste de progresso que não testa o PRD.**
   - `CoachProgressTest.kt:17-37` usa `WeeklyAdherence(3,3,3,0,0,0)` e `WeeklyVolume(114,129,113,28)`.
   - Não há `strongMinutes`, base 142/alvo 151, golden `G-VOL-02`, exclusão de deload/taper/recovery, semana sem plano com corridas, legado, manual ou cobertura de prontidão.

4. **Teste de “sem plano” que não testa o estado completo.**
   - `CoachScreenTest.kt:304-314` passa `explanation = Unavailable`, mas deixa `next = Hidden`, `guards = Hidden`, `progress = Hidden`. Não verifica “Semana concluída”, “Nenhum bloqueio ativo”, “Monte seu plano” juntos, nem a ausência de CTAs.

5. **Teste de fallback que valida o vazamento.**
   - `CoachScreenTest.kt:316-329` espera ver “Resposta contém pace, que só o motor pode prescrever” na tela. Isso é um reason técnico; o PRD queria copy de corredor.

6. **Sem teste de reatividade.**
   - Nenhum teste importa uma corrida/check-in e espera segunda emissão de F2/F4.
   - `CoachProgressSourceTest.kt:139-152` só testa mudança de perfil.
   - `CoachGuardsSourceTest` não testa nova atividade, check-in ou execução.
   - `RoomCoachPlanSource` está com **0% de cobertura** no relatório atual.

7. **Sem teste de `atleta`, `unlockHint` cru ou ACWR no card.**
   - O validador não tem caso com a palavra “atleta”.
   - `CoachNextLabelsTest.kt:79-87` testa `blockCopy`, não `statusLine` com `unlockHint` de ACWR.
   - O teste de tela de F1 não verifica ausência de sigla no conjunto inteiro.

8. **Sem teste de F1 com prescrição divergente.**
   - `CoachNextLabelsTest.kt:34-39` testa sessão sem prescrição; `:66-76` testa bloqueio, mas não afirma que o headline vira o `prescription.title`/`prescribedDurationMin`.

9. **Sem teste de acessibilidade dos novos cards.**
   - Apenas `CoachHistoryTest.kt:159` testa `fontScale=2f`; apenas o histórico testa light (`:149-156`).
   - Não há `heading()` nos novos cards; não há teste de TalkBack/ordem.

### 7.4 Cenários extremos não cobertos

- Virada de meia-noite/fuso (`TimeProvider.refresh`), semana virada, plano criado no meio da semana.
- Treino que cruza meia-noite (pertence ao dia de início).
- `zoneId` inválido no perfil (o fallback de `CoachProgressSource` existe, mas não é testado).
- Prova hoje/amanhã sem sessão; prova adicionada depois do plano; prova removida/concluída.
- Deload/taper/recovery em uma das 4 semanas; semana sem plano com corridas; semana com apenas `LEGACY_UNVERIFIED`; semana com atividade manual.
- Fim da janela de recuperação; mudança de fase no meio da semana.
- Offline total, proxy 400/401/timeout, `choices` ausente, `title/body` vazios, bullets inválidos, revogação durante chamada.
- Fonte desatualizada + cold-start; ACWR instável + qualidade.
- 200% de fonte nos cards novos; tema claro; TalkBack.

### 7.5 Validação manual

O `docs/PROGRESS.md` registra:

- linha 17: “Reinstalar no aparelho e conferir ‘VDOT 31 …’” como pendente;
- linha 32: “validar no aparelho (build com token, conferir os 3 cards novos + revisão sem códigos)” como próximo passo.

O PRD declara “encerrado” antes dessa validação. Os critérios de aceitação de produto (“explicar em voz alta por que amanhã é fácil/forte”, meta ≥80%) não foram executados nesta auditoria nem há evidência de execução no repositório.

---

## 8. Problemas identificados, por severidade e categoria

### 8.1 Índice rápido

| Severidade | IDs |
|---|---|
| **Crítico** | P-01, P-02 |
| **Alto** | P-03, P-04, P-05, P-06, P-07, P-08, P-09, P-10, P-11, P-12, P-13 |
| **Médio** | P-14, P-15, P-16, P-17, P-18, P-19, P-20, P-21, P-22, P-23 |
| **Baixo** | P-24, P-25, P-26, P-27 |

### 8.2 Crítico

#### P-01 — F1 mostra o treino planejado, não o prescrito

**Categoria:** bug de implementação + divergência de PRD + risco de segurança de treino.
**Evidência:** `CoachNextLabels.kt:23-28` usa `context.session.title`/`durationMin`; `CoachNextCard.kt:100-109` renderiza esse headline. A prescrição tem `title`, `prescribedType` e `prescribedDurationMin` (`DailyPrescriptionEngine.kt:199-235`), mas o card só usa `adjustment`, `qualityBlockReason` e `unlockHint` (`CoachNextLabels.kt:30-44`).
**Impacto:** se o motor rebaixou qualidade para fácil ou descanso, o topo do Coach ainda diz “Ritmo forte · 35 min”. O corredor pode seguir o título. Isso compromete a função essencial da aba e o contrato “o motor decide”.
**Direção:** quando houver `DailyPrescription`, o headline deve ser a prescrição (tipo, título e duração prescritos); o planejado pode aparecer como “Planejado: …” em linha secundária. Se não houver prescrição, não mostrar um treino como se fosse decisão.

#### P-02 — Precedência de prova não funciona no fluxo real

**Categoria:** bug de implementação + divergência de PRD.
**Evidência:** `CoachNextSource.kt:54-70` escolhe `next` via `CoachNextResolver` e só então consulta `raceEvents.getForDate(next.session.date)`. O gerador remove a data do evento das sessões (`WeeklyPlanGenerator.kt:247-262`). Testes `CoachNextSourceTest.kt:111-119` e `CoachScreenTest.kt:371-402` criam sessão na data da prova, cenário que o gerador não produz.
**Impacto:** em prova no domingo, o Coach pode mostrar “Semana concluída” ou o treino seguinte; na véspera, pode mostrar treino normal e não “Amanhã é prova”. O Hoje mostra a competição; o Coach contradiz.
**Direção:** resolver a prova antes da sessão: se houver `RaceEvent` na data relevante (hoje ou amanhã), renderizar o estado de prova sem sessão. Se a prova for hoje e não houver sessão, não cair em `Empty`; se for amanhã, o “next” deve ser o evento. Adicionar testes sem sessão na data.

### 8.3 Alto

#### P-03 — O determinístico é escondido atrás do consentimento de IA

**Categoria:** UX + arquitetura/produto + divergência de intenção do PRD.
**Evidência:** `CoachScreen.kt:55-57` (`showConsent = !consented && !dismissed`); `:185` só renderiza F1/F2/F4/replan dentro de `else if (consented)`. As fontes F1/F2/F4 são locais e não chamam IA. `grant()` só existe no card de consentimento (`CoachScreen.kt:66`); depois de “Depois” (`onDismiss`), a tela mostra apenas um texto (`:327-334`) e não há outro `grant()` no app.
**Impacto:** quem não quer enviar dados à IA perde o próximo treino, as travas e a evolução — justamente o valor determinístico. “Depois” vira um beco sem saída até a ViewModel/processo ser recriado. Um novo usuário sem perfil não vê o caminho de onboarding.
**Direção:** separar “IA” de “Coach local”; renderizar F1/F2/F4 sempre e pedir consentimento apenas para explicação/revisão. Se o produto insistir no gate, oferecer “Ativar Coach” persistente e explicar o que é local vs. IA. O texto atual (“Para explicar sua semana, o app envia…”) sugere que só a explicação depende da IA, o que não é verdade na tela.

#### P-04 — F1 não responde “posso forçar?” por falta de prontidão/semáforo

**Categoria:** divergência de PRD + UX.
**Evidência:** `CoachNextContext` (`CoachNextSource.kt:31-37`) não tem `ReadinessResult`, `ReadinessLevel`, sono ou check-in. `CoachNextCard` não importa `readinessLabel`; não há score nem cor. O único sinal é `prescription.qualityBlockReason`, quando existe.
**Impacto:** o card de maior hierarquia não entrega a pergunta central do PRD. O usuário vê “Planejado. A decisão do dia sai no Hoje.” e precisa sair da aba. Sem prescrição, não há ação de check-in.
**Direção:** enriquecer o contexto com `ReadinessResult`/`ReadinessFlag` e o check-in do dia; mostrar “Alta/Moderada/Baixa” com rótulo (nunca só cor), motivo com sono/score e ação de check-in quando `SUBJECTIVE_MISSING` ou check-in ausente. Para dias futuros, deixar explícito que a prontidão será confirmada no dia; não fingir uma decisão.

#### P-05 — F2/F4 (e explicação/revisão) não reagem a dados novos

**Categoria:** bug de implementação + arquitetura.
**Evidência:** `RoomCoachGuardsSource.kt:41-45` combina apenas `time.today`, `profiles.observeProfile` e `raceEvents.observeAll`; `signals.snapshot` lê atividades/check-ins uma vez. `RoomCoachProgressSource.kt:63-70` combina os mesmos três; `load` lê planos/atividades/check-ins. `RoomCoachPlanSource.kt:26-40` só muda com a semana. `RoomCoachReviewSource.kt:50-55` só muda com o dia/semana.
**Impacto:** importar uma corrida, fazer check-in, concluir uma sessão ou aplicar um replanejamento não atualiza F2/F4/explicação/revisão. O usuário vê dados velhos e pode concluir que o app “não registrou” o treino.
**Direção:** observar as tabelas relevantes; centralizar um `CoachSignalsSource` reativo; invalidar cache de explicação/revisão quando o plano mudar; testar segunda emissão após escrita. Ver 4.3.

#### P-06 — F4 não implementa a evolução que promete

**Categoria:** divergência de PRD + regra de negócio.
**Evidência:** `CoachProgressSummary` (`CoachProgress.kt:16-30`) não tem `strongMinutes`, `strongSessions`, base real ou alvo. `CoachProgress.summarize` (`:33-61`) soma `plannedMin`/`executedMin` de `WeeklyReview`. Não usa `VolumeReferenceProjection`/`VolumeReference` (`VolumeReference.kt:82-104`), nem `VolumeProgression`, nem o golden `G-VOL-02`.
**Impacto:** o card “EVOLUÇÃO · 4 SEMANAS” não responde se o atleta evoluiu; mostra execução do plano. Não há “base 142 → alvo 151”, “+6% liberado” ou “mantido + motivo”. É uma regressão de valor em relação ao PRD.
**Direção:** usar a projeção de volume real (`VolumeReferenceProjection`), calcular `strongMinutes`/`strongSessions`, comparar com o alvo do plano e aplicar `R-PLAN-01 v2`; mostrar “base real → alvo” e “mantido + motivo”; adicionar delta entre semanas (ver S2).

#### P-07 — F4 tem erros de dados e de janela

**Categoria:** bug de implementação + regra de negócio.
**Evidência:**
- `WeeklyReviewAssembler.kt:46` passa `readinessHistory(today, 14)`; o comentário em `:117-119` diz que a janela cobre “the last closed week”. Para 4 semanas, as duas mais antigas ficam sem prontidão.
- `CoachProgress.kt:56-59` faz média de médias semanais sem ponderar por `measuredDays`; uma semana com 1 dia pesa igual a uma com 7.
- `CoachProgress.kt:34` filtra só `WeeklyReviewStatus.AVAILABLE`; semanas `EMPTY_NO_PLAN` com corridas reais são descartadas.
- `PlanCompletionCalculator.kt:74-90` conta `LEGACY_UNVERIFIED` como `done`; `CoachProgress` usa `done`.
- `WeeklyReviewAssembler.kt:47-63` não exclui `ActivitySource.MANUAL`; `VolumeReferenceProjection` exclui.
- `CoachProgressSource.kt:102-117` calcula 28 dias corridos, incluindo a semana corrente parcial; o título fala de 4 semanas fechadas.
- Nenhum uso de `VolumeReference.specialWeek` para excluir deload/taper/recovery.
**Impacto:** números que parecem confiáveis, mas misturam janelas, pesos e definições. O usuário pode ver “Prontidão média” de 2 semanas como se fossem 4; pode ver “Pouco histórico” com 3 semanas de corridas; pode ver legado como adesão.
**Direção:** uma única projeção de 4 semanas fechadas com cobertura explícita por seção; `strongMinutes`; exclusão de `specialWeek`; `done` separado de `legacy`; `manual` fora do volume forte; média ponderada; janela de intensidade das 4 semanas fechadas; testes de `G-VOL-02`, legado, deload, semana sem plano e cobertura parcial.

#### P-08 — F2 é uma lista passiva; faltam ações e guardas do dia

**Categoria:** divergência de PRD + UX.
**Evidência:** `CoachGuards` só tem `kind/title/detail` (`CoachGuards.kt:14-25`); `CoachGuardsCard.GuardRow` só renderiza dois `Text` (`CoachGuardsCard.kt:110-128`); não há callback de ação. O PRD pedia ícone, ação e “por que isso protege você”. O comentário em `CoachGuardsSource.kt:22-26` delega sono/check-in a F1, que não os tem.
**Impacto:** o corredor entende a trava, mas não consegue desbloquear. Em “dados desatualizados”, não há botão de importar; em “sem check-in”, não há botão; em “monotonia”, não há sugestão. A aba não cumpre o papel de “o que está me travando e o que fazer”.
**Direção:** adicionar `action` (rota + label) por guarda, detalhe expansível com “por que protege”, e guardas de check-in/sono; para sono, mostrar ausência com confiança, nunca `SHORT_SLEEP` inventado.

#### P-09 — `check` está vermelho

**Categoria:** qualidade/gate.
**Evidência:** comando e saída na seção 7.2; `CoachProgressLabels.kt:69` e `CoachViewModel.kt:155`.
**Impacto:** o projeto não cumpre o Definition of Done; o PRD de evolução não pode ser considerado “encerrado” com o gate vermelho.
**Direção:** reduzir a condição de VDOT a uma função/`when` ou `runCatching` legível; agrupar as fontes do Coach em um objeto de dependências ou dividir a ViewModel. Rodar `./gradlew check` antes de fechar a fase.

#### P-10 — Fallback vaza código interno e o validador rejeita “atleta”

**Categoria:** bug de implementação + UX/privacidade de produto.
**Evidência:** `CoachScreen.kt:275-280` mostra `explanation.reason`; `CoachResponseValidator.kt:95` inclui o código no reason; `:26` tem `"atl"` na lista de tokens e `:75` faz match por substring. `"atleta".lowercase()` contém `"atl"`. `CoachNextLabels.kt:42` anexa `unlockHint` cru; `QualityGate.kt:287` contém “ACWR … 1,50”.
**Impacto:** o usuário vê “Resposta contém código interno: ADHERENCE_PARTIAL” ou “Resposta contém métrica proibida: atl”. Além de feio, reduz a taxa de respostas válidas: qualquer texto com “atleta” cai em fallback. O ACWR aparece no topo, contra a decisão de copy.
**Direção:** mapear reasons para copy de corredor; nunca exibir reason técnico; trocar `forbiddenTokens` por tokens com limite de palavra (`\batl\b`) ou remover “atl”/“ctl” da lista de prosa; validar no contexto de métricas, não substring; mapear `unlockHint` em `CoachNextLabels` e testar o card inteiro.

#### P-11 — Explicação fica velha após replanejamento

**Categoria:** bug/arquitetura + UX.
**Evidência:** `RoomCoachPlanSource.kt:26-40` emite apenas na troca de semana; não observa revisões do plano. `CoachViewModel` cacheia por `lastHash` e não recebe novo contexto após o aceite.
**Impacto:** o usuário aceita “Ajustar a semana”, vê a mensagem “semana ajustada”, mas a explicação semanal abaixo continua descrevendo o plano anterior. O mesmo ocorre com o resumo da revisão após importação retroativa.
**Direção:** `observeWeek(weekStart)` no `CoachPlanSource`; invalidar `lastHash` quando o `inputsHash`/versão mudar; testar replan → explicação nova (ou `Loading` → `Fallback` determinístico coerente).

#### P-12 — Estados vazios se contradizem

**Categoria:** bug de UX + divergência de PRD.
**Evidência:** sem plano: `CoachNextUiState.Empty` → “Semana concluída.” (`CoachNextCard.kt:49-67`); `CoachGuardsUiState.Ready(empty)` → “Nenhum bloqueio ativo — siga o plano.” (`CoachGuardsCard.kt:80-85`); `CoachExplanationState.Unavailable` → “Monte seu plano para começar” (`CoachScreen.kt:211-231`); `CoachProgressUiState.Ready(0)` → “Pouco histórico” (`CoachProgressCard.kt:77-88`).
**Impacto:** o usuário novo vê cinco cards que dizem coisas incompatíveis. “Semana concluída” e “siga o plano” soam como se houvesse um plano; “Monte seu plano” é o único correto.
**Direção:** um único estado de onboarding quando não há perfil/plano: esconder F1/F2/F4 ou renderizar um card único com CTAs de perfil/calibração/importação. Só mostrar “Semana concluída” quando existir plano e todas as sessões futuras estiverem concluídas.

#### P-13 — Revisão esconde observações determinísticas quando a IA responde

**Categoria:** segurança/qualidade + UX.
**Evidência:** `CoachReviewCard.kt:168-183` condiciona `review.observations` a `state !is CoachReviewState.Ready`. Quando `Ready`, só a narrativa da IA é exibida (além dos números de adesão/volume).
**Impacto:** se a IA não mencionar doença/lesão, sono curto ou adesão parcial, o sinal determinístico desaparece. A IA “só reescreve”, mas nesse fluxo ela substitui a leitura de segurança.
**Direção:** sempre renderizar as observações determinísticas, mesmo em `Ready` (talvez em seção “O que o motor registrou”); a IA pode reescrever acima, mas não apagar. Testar `Ready` com observação de doença e garantir que ela continue visível.

### 8.4 Médio

#### P-14 — F3: `Unavailable` sem CTAs e fallback não acionável

**Evidência:** `CoachScreen.kt:211-231` só textos; `CoachRoute` não tem callbacks de perfil/calibração/importação; `:259-301` tem apenas “Tentar de novo”/“Desativar”.
**Impacto:** o usuário recebe instruções que não pode executar. O fallback não ajuda a resolver a causa (check-in/importação).
**Direção:** CTAs para Perfil, Calibração, Importação; no fallback, bullets determinísticos (“sem conexão”, “resposta fora do contrato”) e ação correspondente; nunca reason técnico.

#### P-15 — Sem validação manual/aparelho

**Evidência:** `PROGRESS.md:17,32`; não há teste instrumentado/relatório de aparelho.
**Impacto:** o PRD é declarado encerrado sem o critério de produto (explicar em voz alta, ≥80%) e sem confirmar visualmente os cards com token.
**Direção:** rodar o roteiro manual do PRD (com/sem token, offline, prova, check-in, F2, F4, 200%, TalkBack) antes de chamar de consolidado.

#### P-16 — F1 sem estado `Stale`; ações de futuro erradas

**Evidência:** F1 não consulta `LoadDataState`; `statusLine` para futuro diz “A decisão do dia sai no Hoje” (`CoachNextLabels.kt:33`), mas Hoje só mostra hoje; “Ver no Hoje” é sempre exibido (`CoachNextCard.kt:133-140`).
**Impacto:** o usuário de amanhã é mandado para uma tela que não tem a decisão de amanhã; dados velhos não são sinalizados.
**Direção:** estado `Stale` no F1; para futuro, “Planejado; a confirmação sai no dia” e ação “Ver plano de amanhã”/“Ver semana”, não “Ver no Hoje”.

#### P-17 — F3/TalkBack: headings e testes de acessibilidade ausentes

**Evidência:** `heading()` só em `CoachHistoryCard.kt:75,283`; os cards novos não têm; `CoachHistoryTest` é o único com `fontScale=2f`; não há teste light dos novos cards.
**Impacto:** TalkBack lê uma sequência longa sem seções; 200% e light podem quebrar sem detecção.
**Direção:** `Modifier.semantics { heading() }` nos títulos/overlines; testes de 200%, light/dark, ordem e conteúdo dos novos cards.

#### P-18 — F2: copy de fonte desatualizada sem N dias e sem ação

**Evidência:** `CoachGuards.kt:83-90` diz “mais de 14 dias”; não há data/N nem botão.
**Impacto:** o usuário sabe que está velho, mas não quanto nem o que fazer.
**Direção:** passar `lastImportedAt`/dias; ação “Importar corridas”/“Abrir Dados”.

#### P-19 — F4: detalhes de apresentação e lacunas de dados

**Evidência:** até 7 linhas (`CoachProgressCard.kt:99-110`); `CoachProgress.summarize` transforma `executedMin == null` em `0` (`CoachProgress.kt:37-38`), então uma semana planejada e sem corrida aparece como “feito 0 min (0%)”; `intensityLine` não mostra N sessões; `vdotLine` não mostra paces E/T.
**Impacto:** o card não cumpre a especificação visual e pode tratar ausência de execução como zero medido.
**Direção:** limitar a 6 linhas + síntese; distinguir “nenhum treino registrado” de “0 min”; “X% fácil em N sessões”; paces E/T ou remover a promessa.

#### P-20 — Desempenho não comprovado e risco de Main thread

**Evidência:** `CoachProgressSource.kt:63-134`; `WeeklyReviewAssembler.kt:42-94`; `CoachViewModel.kt:208-397`; sem `flowOn`; `intensitySessions` antes de LTHR.
**Impacto:** abertura da aba pode passar de 500 ms, competir com IA e gastar bateria; não há benchmark.
**Direção:** medir; `flowOn(Dispatchers.Default/IO)`; uma montagem de 4 semanas em vez de 4 `assemble`; pular samples sem LTHR; cachear por dia/hash; carregar seções sob demanda se necessário.

#### P-21 — Acessibilidade e testes visuais dos novos cards

**Evidência:** apenas histórico tem heading/200%/light; ver P-17.
**Impacto:** requisito NFR explícito não comprovado.
**Direção:** headings, testes de 200% e light/dark para F1/F2/F4; teste de corte de botões em duas colunas.

#### P-22 — Privacidade e timeout

**Evidência:** `HttpCoachApi.kt:81` loga corpo de erro; `CoachViewModel.kt:314,322`; `HttpCoachApi.kt:57` timeout 45 s; PRD pede 10 s + 1 retry.
**Impacto:** possível dado de prompt em log; espera longa e sem retry no app.
**Direção:** não incluir corpo no log (ou sanitizar); timeout 10 s; retry único com backoff; estado “IA indisponível” separado de “sem conexão”.

#### P-23 — Flash de “Monte seu plano” e “Desativar” duplicado

**Evidência:** `_explanation` inicia `Unavailable` (`CoachViewModel.kt:178-180`); `CoachScreen.kt:211-231`; “Desativar” no fallback (`:293-300`) e no fim (`:314-326`).
**Impacto:** quem tem plano pode ver o vazio por um frame; a falha de IA oferece desativar a aba inteira.
**Direção:** estado inicial `Loading` quando consentido; “Desativar” apenas no fim (ou renomear a ação do fallback para “Desligar IA”).

### 8.5 Baixo

#### P-24 — Copy e hierarquia menores

- Subtítulo desatualizado (`CoachScreen.kt:153-157`) vs. PRD E.
- Overline “PRÓXIMO TREINO” em “Dia de prova” (`CoachNextCard.kt:95-99`).
- “Sono médio 6h40 · sem curta.” (`CoachProgressLabels.kt:55-63`).
- “1 de 4 semanas · parcial.” está bom; não há “ver menos”.
- “Semana concluída” sem plano já é P-12.

#### P-25 — F2 sem ícones e sem detalhe expansível

**Evidência:** `GuardRow` só textos; sem `icon`, sem “por que protege”.
**Impacto:** lista mais difícil de escanear; não é bloqueante.
**Direção:** ícone por `CoachGuardKind` (usando o design system) e expansão local.

#### P-26 — F4 tem 6–7 linhas, não 4–6 + síntese

**Evidência:** `CoachProgressCard.kt:99-110`.
**Impacto:** densidade maior que o previsto; falta a frase-síntese.
**Direção:** consolidar volume/intensidade ou remover a linha de VDOT para o detalhe; adicionar síntese determinística (“Semana mantida: volume estável, adesão 3/3”).

#### P-27 — Histórico esconde a explicação corrente quando a IA cai

**Evidência:** `currentExplainHash` é setado antes da chamada (`CoachViewModel.kt:268`); `CoachHistory.view` filtra por hash (`CoachHistory.kt:50-56`); teste `CoachViewModelTest.kt:626-636` espera `Empty`.
**Impacto:** em fallback, o usuário perde o acesso à última explicação válida da mesma semana no histórico, embora ela não esteja visível no card.
**Direção:** filtrar apenas quando `_explanation is Ready`; em fallback, manter a última explicação válida no arquivo.

### 8.6 Agrupamento por categoria

1. **Bugs e defeitos de implementação:** P-01, P-02, P-05, P-10, P-11, P-12, P-13, P-16, P-17, P-19, P-23, P-27.
2. **Divergências em relação ao PRD:** P-01, P-02, P-04, P-06, P-07, P-08, P-14, P-16, P-17, P-19, P-20, P-21, P-22, P-26.
3. **Problemas arquiteturais/técnicos:** P-05, P-09, P-11, P-20, P-22, P-25; acoplamento da `CoachViewModel`; duplicação de cálculo com Tendências.
4. **Problemas de experiência:** P-03, P-04, P-08, P-12, P-13, P-14, P-16, P-17, P-18, P-19, P-21, P-23, P-24, P-25, P-26, P-27.
5. **Oportunidades de melhoria funcional:** P-06/P-07 (transformar F4 em evolução real), P-08 (travas acionáveis), P-14 (fallback/onboarding), P-04 (F1 com prontidão) — detalhadas em S1–S5.
6. **Novas funcionalidades recomendadas:** S1–S5 na seção 9.

### 8.7 Fatos, hipóteses e recomendações

| Tipo | Itens |
|---|---|
| **Fato comprovado no código/teste** | P-01, P-02 (regra do gerador + source), P-05, P-06, P-07 (campos/janelas), P-08, P-09, P-10, P-11, P-12, P-13, P-14, P-16, P-17, P-19, P-22, P-23, P-27 |
| **Hipótese provável (requer medição/validação)** | P-20 (tempo >500 ms/Main), P-15 (impacto visual real no aparelho), P-24/P-25 (percepção de hierarquia), P-03 (quantos usuários recusam a IA) |
| **Recomendação de produto** | S1–S5; copy layer; consentimento separado; estados de onboarding; teste manual |

---

## 9. Melhorias adicionais recomendadas

Estas sugestões vão além do PRD atual e usam dados já existentes. Não são “features para aumentar contagem”; são formas de transformar informação já calculada em decisão e confiança.

### S1 — Faixa dos próximos 7 dias (“o que vem aí?”)

**Problema/oportunidade.** O F1 responde só um dia; o corredor não vê a semana sem abrir Hoje/Plano. O PRD evita duplicar Hoje, mas uma faixa compacta de 7 dias (não um gráfico) ajudaria a planejar a semana e a entender por que hoje é leve.

**Solução.** Uma linha horizontal/vertical de 7 chips: dia, tipo, duração e status (`PLANNED`, `DONE`, `CLOSED`, prova, recovery). Tocar em um dia abre o detalhe do Hoje/Tendências; não cria prescrição.

**Como funciona.** Usa `WeeklyPlan.sessions`, `DailyPrescription` já persistida (quando houver), `RaceEvent` e `RecoveryWindow`; o status vem do plano. Sem dados, mostra “sem plano” em vez de inventar.

**Benefício.** Responde “o que faço esta semana?” em um olhar; reduz idas a Hoje; ajuda a aceitar um dia leve porque mostra o contexto.

**Reuso.** `TrainingPlanRepository.observeWeek`, `DailyPrescriptionRepository`, `RaceEventRepository`, tokens existentes; nenhum schema novo.

**Complexidade:** baixa-média. **Impacto:** alto. **Justificativa:** é a extensão natural do F1, usa dados que já existem e não interfere na autoridade do motor. Deve vir depois de corrigir F1 (senão multiplica a contradição planejado × prescrito).

### S2 — Evolução com delta, não só foto

**Problema/oportunidade.** F4 mostra totais de 4 semanas; não mostra direção. “Estou evoluindo?” exige comparar a semana mais recente com a média/primeira semana, não apenas ver números.

**Solução.** Além das linhas atuais, uma linha de delta determinística: “Volume forte: 120 → 142 min (+18%)”, “Adesão: 75% → 100%”, “Fácil: 68% → 78%”, “Sono: estável”. Sem seta verde/vermelha gamificada; usar texto e um marcador neutro.

**Como funciona.** `VolumeReference` + `PlanCompletionCalculator` + `WeeklyReview` das 4 semanas; comparar a última semana fechada com a média das 3 anteriores e com a primeira; se faltar base, “sem base para comparar”. Nenhuma projeção.

**Benefício.** Responde a pergunta central de F4 com um número interpretável; ajuda a manter consistência sem cobrança.

**Reuso.** `VolumeReferenceProjection`, `strongMinutes`, `WeeklyReview`; mesmo `assemble`; sem IA.

**Complexidade:** média. **Impacto:** alto. **Justificativa:** é o menor acréscimo que transforma “foto” em “evolução”; não precisa de IA nem de gráfico.

### S3 — Travas com “o que destrava e quando”

**Problema/oportunidade.** F2 lista travas, mas não diz o que fazer nem quando elas somem. O corredor entende o “não”, não o “como”.

**Solução.** Cada guarda ganha uma linha “Para destravar: …” e, quando aplicável, um prazo/contagem: “faltam 2 noites ≥6h”, “importe uma corrida até 14 dias”, “janela termina em 2 dias”, “volte ao check-in”. Ação de uma toque: “Fazer check-in”, “Importar”, “Ver plano”.

**Como funciona.** `RecoveryWindow.endDate`, `ReadinessHistory`/check-in, `LoadDataState`/`lastImportedAt`, `CoachGuardKind`; copy determinística por tipo; navegação existente (check-in, importação, Hoje).

**Benefício.** Transforma bloqueio em plano de ação; aumenta confiança no conservadorismo; reduz suporte.

**Reuso.** `CoachGuards` + rotas existentes; sem schema/IA.

**Complexidade:** baixa-média. **Impacto:** alto. **Justificativa:** é exatamente o que o PRD pedia e ficou de fora; o dado já existe, só não é composto.

### S4 — “Como foi vs. prescrito” depois do treino

**Problema/oportunidade.** O Coach decide antes; depois do treino, o usuário não vê no Coach se fez o que foi prescrito e o que isso muda amanhã. A aba “Corridas” e o detalhe têm números, mas não a leitura de coach.

**Solução.** Um card pós-treino: “Feito: 32 min fácil · prescrito: 30 min fácil · dentro do plano” ou “feito 20 min de qualidade em dia fácil — o próximo dia será ajustado”. Uma frase, sem nota.

**Como funciona.** `SessionComparison`/`GetSessionComparison`, `DailyPrescription`, `PlannedSession`, `ActivitySummary`; comparar duração/tipo/estímulo; se não houver atividade vinculada, “sem registro”.

**Benefício.** Fecha o ciclo decisão → execução → consequência; aumenta confiança e aprendizado; reforça o motor.

**Reuso.** `GetSessionComparison` e `SessionComparison` já existem; UI nova pequena.

**Complexidade:** média. **Impacto:** alto. **Justificativa:** é a pergunta natural depois do treino e usa dados já calculados; não cria prescrição.

### S5 — “Plano B” de um toque

**Problema/oportunidade.** O replan atual exige “Ajustar a semana/Perdi treino/Menos tempo” e um sheet. No dia a dia, o corredor quer uma decisão rápida (“hoje não dá 40 min, e agora?”).

**Solução.** Quando o dia está `PLANNED`, oferecer 3 alternativas já avaliadas pelo motor: “fazer 20 min fácil”, “mover para amanhã”, “descansar”. Cada uma mostra o efeito no plano e exige confirmação/diff, reusando `EvaluateReplan`/`ApplyReplan`/undo.

**Como funciona.** `PlanReplanner` com intents existentes; o Coach só apresenta opções; o motor valida; o aceite continua explícito. Sem novos slots/magias.

**Benefício.** Aumenta adesão real em dias imperfeitos; reduz abandono; mantém a autoridade e o undo.

**Reuso.** `ReplanController`, `EvaluateReplan`, `ApplyReplan`, `ReplanSuggestionStore`; nenhum schema.

**Complexidade:** média. **Impacto:** alto. **Justificativa:** o replan já existe, mas a interação é “formulário”; um “plano B” contextual é a menor camada de UX que gera valor diário. Requer cuidado para não parecer que o app está “flexibilizando” qualidade; as opções devem ser conservadoras e explicadas.

### S6 (opcional, baixo esforço) — Faixa de confiança e cobertura de dados

**Problema/oportunidade.** O usuário não sabe em quantos dias/semanas o card se baseia; F4 mistura 4 semanas, 2 semanas de prontidão e 28 dias de intensidade.

**Solução.** Uma linha “Base: 3 semanas · 2 noites · 4 check-ins” com link “Ver dados”; se cobertura baixa, “confiança baixa” qualitativa.

**Reuso.** `WeeklyReadiness.measuredDays`, `WeeklySleep.measuredNights`, `WeeklyAdherence`, `VolumeReference`; sem IA.

**Complexidade:** baixa. **Impacto:** médio. **Justificativa:** transparência barata que aumenta a confiança e explica por que um número pode faltar.

---

## 10. Plano de ação priorizado

### Grupo 1 — Correções necessárias (antes de considerar consolidado)

| # | Ação | Endereça | Esforço | Impacto |
|---|---|---|---|---|
| 1 | Corrigir `check`: `ComplexCondition` em `CoachProgressLabels.kt:69` e `LongParameterList` em `CoachViewModel.kt:155` | P-09 | Baixo | Alto (gate) |
| 2 | F1 usar `DailyPrescription` como fonte do título/tipo/duração; planejado só como linha secundária | P-01 | Baixo-médio | Crítico |
| 3 | Resolver prova antes da sessão; testar prova hoje/amanhã sem sessão | P-02 | Médio | Crítico |
| 4 | Adicionar prontidão/semáforo/sono/check-in ao F1; ação de check-in quando ausente; copy layer para `unlockHint` | P-04, P-10 | Médio | Alto |
| 5 | Tornar F2/F4/explicação/revisão reativos a atividades/check-ins/planos; testar segunda emissão | P-05, P-11 | Médio | Alto |
| 6 | Refazer F4 com `strongMinutes`/`VolumeReference`, base real → alvo, exclusões, legado/manual, médias ponderadas, janela de 4 semanas | P-06, P-07 | Alto | Alto |
| 7 | Corrigir consentimento: F1/F2/F4 sempre locais; “Ativar Coach” persistente; não esconder o determinístico atrás da IA | P-03 | Médio | Alto |
| 8 | Unificar estados vazios/onboarding; remover “Semana concluída”/“Nenhum bloqueio” sem plano/perfil | P-12 | Baixo-médio | Alto |
| 9 | Fallback sem reason técnico; bullets de causa; CTAs de check-in/importar/perfil; `Unavailable` com botões | P-10, P-14 | Baixo | Alto |
| 10 | Mostrar observações determinísticas da revisão também em `Ready` | P-13 | Baixo | Alto |

### Grupo 2 — Melhorias de qualidade

| # | Ação | Endereça | Esforço | Impacto |
|---|---|---|---|---|
| 11 | Corrigir `forbiddenTokens` (“atl”/“atleta”) e adicionar casos de validador (atleta, ACWR, reason no fallback) | P-10 | Baixo | Médio-alto |
| 12 | F2 com ações, “por que protege”, N dias de atraso e guardas de sono/check-in | P-08, P-18 | Médio | Alto |
| 13 | Performance: `flowOn`, uma montagem de 4 semanas, pular samples sem LTHR, cache por dia/hash, benchmark | P-20 | Médio | Médio-alto |
| 14 | Acessibilidade: `heading()` nos cards, testes de 200% e light/dark, teste de ordem/foco | P-17, P-21 | Baixo-médio | Médio |
| 15 | Privacidade: não logar corpo de erro; timeout 10 s + 1 retry; estado “IA não configurada” | P-22 | Baixo | Médio |
| 16 | F4: corrigir “Sem volume planejado” com plano sem execução, mostrar N de sessões, paces E/T ou remover a promessa; 4–6 linhas + síntese | P-19, P-26 | Baixo-médio | Médio |
| 17 | F1: estado `Stale`, ação contextual para amanhã, “Planejado; confirmação no dia”, sem “Ver no Hoje” para futuro | P-16 | Baixo-médio | Médio |
| 18 | Revisar `CoachPlanSource`/`CoachReviewSource` com testes de reatividade | P-11, P-05 | Baixo | Médio |
| 19 | Remover “Desativar” duplicado do fallback; estado inicial `Loading` em vez de `Unavailable` | P-23 | Baixo | Baixo-médio |
| 20 | Completar a matriz de testes do PRD: prova sem sessão, prescrição divergente, check-in → nova prescrição, legado, deload, semana sem plano, golden `G-VOL-02`, 200% e TalkBack | P-15, P-24 | Médio | Alto |

### Grupo 3 — Evolução do produto

| # | Ação | Endereça | Esforço | Impacto |
|---|---|---|---|---|
| 21 | S1 — faixa dos próximos 7 dias | Valor diário | Baixa-média | Alto |
| 22 | S2 — evolução com delta | F4 de verdade | Média | Alto |
| 23 | S3 — travas acionáveis com “o que destrava e quando” | F2 de verdade | Baixa-média | Alto |
| 24 | S4 — “como foi vs. prescrito” pós-treino | Fechar o ciclo | Média | Alto |
| 25 | S5 — “plano B” de um toque com o motor | Adesão real | Média | Alto |
| 26 | S6 — faixa de cobertura/confiança dos dados | Transparência | Baixa | Médio |

**Sequência recomendada:** 1 → 2/3/4 → 5/7/8 → 6 → 9/10 → 11–20 → 21–26. Corrigir F1 e a reatividade antes de adicionar S1/S2; caso contrário, a faixa de 7 dias e o delta vão amplificar dados errados.

---

## 11. Conclusão sobre o nível de maturidade

**Nível atual: MVP parcial / beta interno (2 de 4).**

Critérios usados:

| Nível | Definição | Situação |
|---|---|---|
| 1 — Protótipo visual | Cards e navegação existem; regra não confiável | Superado |
| 2 — MVP parcial | Fluxo principal existe, mas lacunas de dados/estado e gates | **Atual** |
| 3 — Consolidado | PRD cumprido, estados coerentes, dados atualizados, gates verdes, validação manual | Não atingido |
| 4 — Pronto para release além do usuário 0 | Nível 3 + validação real, acessibilidade e privacidade revisadas | Não atingido |

**Por que 2 e não 3:**

1. O card de maior hierarquia pode recomendar o treino errado (P-01) e o estado de prova não funciona (P-02).
2. A tela não responde “posso forçar?” (P-04) e esconde o determinístico atrás da IA (P-03).
3. F2/F4 mostram dados velhos (P-05) e F4 não é evolução (P-06/P-07).
4. `check` está vermelho (P-09) e não há validação no aparelho (P-15).
5. Vários testes protegem premissas erradas (seção 7.3), então o verde não é evidência suficiente.

**O que impede chamar de “falha total”:** a base arquitetural é boa, o motor continua correto, nenhuma prescrição é alterada, a memória/privacidade foram preservadas, e F3/histórico/replan estão funcionais. O trabalho não precisa ser reescrito; precisa ser **corrigido e provado**.

**Condição para subir a 3:** fechar o Grupo 1, rodar `./gradlew check` verde, regenerar e revisar a matriz de testes, e executar o roteiro manual do PRD no aparelho (com/sem token, prova, check-in, offline, 200%, TalkBack).

---

## Apêndice A — Evidências principais por arquivo

| Arquivo | O que evidencia |
|---|---|
| `app/src/main/kotlin/com/meucoach/app/ui/coach/CoachScreen.kt` | Gate de consentimento (55-57, 185), ordem, fallback cru (259-301), `Unavailable` sem CTA (211-231), Desativar duplicado |
| `app/src/main/kotlin/com/meucoach/app/ui/coach/CoachNextCard.kt` | Headline planejado, status, ação só para `SUBJECTIVE_MISSING`, nota fixa |
| `app/src/main/kotlin/com/meucoach/app/ui/coach/CoachNextLabels.kt` | `headline` usa `session`; `statusLine` anexa `unlockHint` cru; `blockCopy` |
| `app/src/main/kotlin/com/meucoach/app/coach/CoachNextSource.kt` | Resolver antes da prova; `raceEvents.getForDate` não reativo |
| `core/domain/.../coach/CoachNextResolver.kt` | Regra hoje/futuro; não considera prova |
| `core/domain/.../prescription/WeeklyPlanGenerator.kt` | Remove sessões na data do evento (247-262) |
| `app/src/main/kotlin/com/meucoach/app/coach/CoachGuardsSource.kt` | Gatilhos só today/perfil/provas; snapshot lido uma vez |
| `core/domain/.../coach/CoachGuards.kt` | Guardas estruturais; sem ação/ícone/sono/check-in |
| `app/src/main/kotlin/com/meucoach/app/ui/coach/CoachGuardsCard.kt` | Lista, 4+ver mais, linha vazia; sem ação |
| `app/src/main/kotlin/com/meucoach/app/coach/CoachProgressSource.kt` | 4 semanas; 14d readiness; intensidade 28d; samples antes de LTHR; sem reatividade |
| `core/domain/.../coach/CoachProgress.kt` | Só `AVAILABLE`; somas/médias; sem `strongMinutes`/target |
| `app/src/main/kotlin/com/meucoach/app/ui/coach/CoachProgressCard.kt` | Linhas; `availableWeeks==0`; até 7 linhas |
| `app/src/main/kotlin/com/meucoach/app/ui/coach/CoachProgressLabels.kt` | `volumeLine`, `readinessLine`, `sleepLine`, `vdotLine`; condição detekt (69) |
| `app/src/main/kotlin/com/meucoach/app/application/WeeklyReviewAssembler.kt` | Volume/extra sem filtro manual; readiness 14d |
| `core/domain/.../prescription/PlanCompletionCalculator.kt` | `done` inclui `LEGACY_UNVERIFIED` |
| `core/domain/.../prescription/VolumeReference.kt` | `strongMinutes`, `specialWeek` disponíveis e não usados |
| `app/src/main/kotlin/com/meucoach/app/ui/coach/CoachReviewCard.kt` | Observações escondidas quando `Ready` (168-183) |
| `core/domain/.../coach/CoachResponseValidator.kt` | `atl` substring (26/75); reason com código interno (95) |
| `app/src/main/kotlin/com/meucoach/app/ui/coach/CoachViewModel.kt` | 10 parâmetros (155); reason cru; `currentExplainHash` antes da chamada |
| `app/src/main/kotlin/com/meucoach/app/coach/CoachPlanSource.kt` | Só reage à semana, não ao plano |
| `app/src/main/kotlin/com/meucoach/app/coach/CoachReviewSource.kt` | Só reage ao dia/semana |
| `core/data/.../coach/CoachApi.kt` | Timeout 45 s; corpo de erro no log |
| `core/domain/.../prescription/QualityGate.kt` | Hint de ACWR 1,50 (287); cold-start |
| `docs/PROGRESS.md` | Pendências de aparelho (17, 32); PRD “encerrado” |
| `app/src/test/.../CoachNextSourceTest.kt` | Prova com sessão artificial (111-119) |
| `app/src/test/.../CoachScreenTest.kt` | Prova com sessão artificial (371-402); fallback técnico (316-329) |
| `app/src/test/.../CoachReviewSourceTest.kt` | Legado esperado como `done=1` (113-117, 148-151) |
| `app/src/test/.../CoachProgressSourceTest.kt` | Legado esperado como `done=1` (131-134, 164-167) |
| `app/src/test/.../CoachProgressTest.kt` | Agregado sintético, sem golden |
| `app/src/test/.../CoachHistoryTest.kt` | Único teste de 200%/light |

## Apêndice B — Comandos e resultados

```text
$ ./gradlew :app:testDebugUnitTest --tests 'com.meucoach.app.ui.coach.*' \
    --tests 'com.meucoach.app.coach.*'
BUILD SUCCESSFUL

$ ./gradlew :app:assembleDebug
BUILD SUCCESSFUL

$ ./gradlew spotlessCheck koverVerify
BUILD SUCCESSFUL
FIT parser branch coverage: 93,01% (572/615)

$ ./gradlew :app:lintDebug
BUILD SUCCESSFUL

$ ./gradlew check
> Task :app:detekt FAILED
CoachProgressLabels.kt:69:13: This condition is too complex (4). [ComplexCondition]
CoachViewModel.kt:155:41: The constructor(...) has too many parameters. [LongParameterList]
BUILD FAILED
```

**Contagem dos testes focados:** 87 UI + 15 fontes + 39 domínio = **141 testes, 0 falhas**.

## Apêndice C — Decisões do PRD que foram respeitadas

- F1 só exibe; não grava em `coach_messages`, não cria `kind` novo, não muda TTL/schema.
- F4 determinístico primeiro; sem IA nesta fatia.
- Sem migração, sem dependência nova, sem deep link novo.
- IA só reescreve; motor permanece autoridade; replan com `ReplanController`.
- `WeeklyReviewAssembler` como montagem única; histórico como arquivo.
- Opt-in, payload minimizado, TTL, revogação, export/delete.
- Sem gamificação.

## Apêndice D — O que eu não afirmaria sem nova evidência

- Que a aba quebra em produção: não há crash observado; os problemas são de estado/dados.
- Que a performance é necessariamente >500 ms: a análise é estática; falta medir.
- Que todos os usuários recusam a IA: o impacto de P-03 depende do comportamento real; a existência do beco sem saída é comprovada no código.
- Que a IA sempre usará “atleta”: a frequência é desconhecida; o falso positivo é comprovado na lógica.
- Que o visual em 200% quebra: não foi medido; a ausência de teste é o achado.

---

**Fim da auditoria.** Nenhum código foi alterado. A implementação tem uma base promissora, mas a tela Coach ainda não entrega, com confiança, as três perguntas que o PRD lhe atribui: “posso forçar?”, “o que me trava?” e “estou evoluindo?”.
