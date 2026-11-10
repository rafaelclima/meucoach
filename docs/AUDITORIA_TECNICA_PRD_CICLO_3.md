# Auditoria técnica independente — PRD do Ciclo 3

**Documento auditado:** `docs/PRD_ADAPTACAO_E_BRIEFING.md`, versão 0.1, 10/11/2026
**Data da auditoria:** 10/11/2026
**Escopo:** revisão documental, inspeção estática do código e validação externa seletiva de referências esportivas. Nenhuma funcionalidade foi implementada.

> **Integridade do repositório:** o PRD já estava como arquivo não versionado (`?? docs/PRD_ADAPTACAO_E_BRIEFING.md`) antes desta auditoria. Ele não foi editado. Este relatório é o único arquivo criado nesta execução. Não foram executados testes/build; portanto, nenhum resultado de teste é inferido.

## PARTE 1 — Parecer executivo

### Resultado: **REVISÃO NECESSÁRIA**

O PRD tem uma boa direção de produto e parte de decisões arquiteturais acertadas: mantém o motor determinístico, propõe reutilizar `PlanReplanner`, exige aceite para a revisão semanal e preserva a autoridade da prescrição diária. Isso reduz risco de regressão e evita mudanças silenciosas em sessões já realizadas.

Não está, porém, pronto para implementação como está. Há problemas relevantes antes de qualquer código:

- **A prova ainda não é um evento de domínio completo.** `RACE` conflita com a validação de disponibilidade do replanejador, não é aceito pela prescrição diária e não tem semântica definida para conclusão, falta, troca ou carga.
- **O taper é descrito em dias, mas o gerador existente opera por semana e deixa `QUALITY` intacta.** Os limites exatos para a última sessão forte e para a semana da prova não são demonstrados pelas fontes citadas.
- **O fingerprint proposto não fecha o contrato de obsolescência.** Faltam campos de calibração que já alteram alvos; `PlanReplanner.inputsHash` também não incorpora distância/tempo-alvo. Não está definido o que persistir quando há `NoChange`, nem como separar “perfil que gerou o plano” de “perfil já avaliado”.
- **O aceite não é atomicamente protegido contra uma edição concorrente do perfil.** A versão do plano é checada em transação, mas perfil e hash corrente são relidos antes da transação de aceite.
- **O briefing promete blocos e ajustes específicos que não existem na sessão canônica.** O exemplo publicado soma 35 minutos enquanto anuncia 30, e inclui ritmos e alternativas que não derivam da prescrição.
- **F4 conflita diretamente com o escopo aprovado do produto.** Água/sais e alimentação não deixam de ser aconselhamento nutricional por não exibirem gramas ou calorias; o rodapé não substitui limites de segurança nem validação das fontes.
- **Há inconsistência de sequência:** C3-03 exige template de `RACE` antes de C3-06 criar `RACE`.

### Respostas objetivas

| Pergunta | Parecer |
|---|---|
| O PRD está tecnicamente maduro? | **Parcialmente.** A estrutura e o reaproveitamento arquitetural são bons; contratos de domínio, segurança esportiva e cenários de prova ainda estão incompletos. |
| As propostas respeitam a arquitetura atual? | **Em princípio, sim.** `core:domain` para regra pura, `core:data` para persistência e `:app` para orquestração/UI são fronteiras coerentes. A proposta ainda não descreve os efeitos nos vários consumidores de `WorkoutType` nem a atomicidade do aceite com o perfil. |
| As regras de negócio estão suficientemente definidas? | **Não.** Especialmente regra de taper por sessão, prova como evento, transição pós-prova, perfil legado/sem alteração e limites do briefing. |
| Há riscos importantes não contemplados? | **Sim.** Mudança concorrente de perfil, prova fora da disponibilidade, prova hoje/amanhã, ausência de confirmação de execução da prova, alvos aspiracionais tratados como prescrição e conselhos de hidratação/alimentação. |
| Pode seguir para implementação como está? | **Não.** Fechar os itens P0 da Parte 9 e aprovar os gates da sequência revisada antes do primeiro código. |

## PARTE 2 — Verificação das afirmações do PRD

### Fatos confirmados no projeto

| Afirmação | Estado | Evidência e observação |
|---|---|---|
| O plano é persistido por semana e não é reescrito por check-in/prescrição. | **CONFIRMADA** | `TodayViewModel.kt:168-201` observa o plano vigente e só o gera/salva quando não há semana. ADR 0023 §3.1 separa intenção de plano e prescrição diária. |
| A edição de perfil não aciona hoje uma proposta T4. | **CONFIRMADA** | `ReplanTrigger` contém apenas `MISSED_SESSIONS`, `AVAILABILITY_CHANGED` e `USER_REQUEST` (`ReplanModels.kt:16-25`). `ReplanContextBuilder` lê perfil, plano, carga e prontidão, mas não compara fingerprint de perfil (`ReplanContextBuilder.kt:35-84`). |
| O gerador considera `experience`, acesso à esteira e parte do objetivo. | **CONFIRMADA** | `WeeklyPlanGenerator.kt:170-174`, `197-209`, `394-410`, `428-435`. O tipo do objetivo define a janela do taper; a data define sua distância a partir de `weekStart`. |
| A experiência de iniciante bloqueia qualidade em um plano novo. | **CONFIRMADA** | `WeeklyPlanGenerator.kt:170-174` exige `experience != BEGINNER`. O limite de qualidade para iniciante continua descrito como pendente/proposto em `docs/adr/0026-load-coverage-quality-eligibility.md:88-104, 224-225`. |
| A prescrição diária não tem hoje guarda por experiência. | **CONFIRMADA** | `DailyPrescriptionEngine.prescribe()` toma decisões por prontidão, carga, sono e tipo planejado (`DailyPrescriptionEngine.kt:35-198`); `experience` não aparece nessa tabela de decisão. O hash inclui `experience` (`:455-460`), mas isso só detecta mudança de input, não a torna uma regra. |
| Uma sessão `QUALITY` planejada pode permanecer no dia quando a carga e a prontidão permitem. | **CONFIRMADA** | `DailyPrescriptionEngine.kt:75-116` mantém a sessão em `KEEP` se `qualityBlocked` for falso. Assim, um plano antigo gerado antes de rebaixar o nível pode continuar forte. |
| A prova não é um tipo de sessão atual. | **CONFIRMADA** | `WorkoutType` só tem `REST`, `EASY`, `LONG`, `QUALITY` (`WorkoutRecommender.kt:19-24`); `PlannedSessionEntity.type` é `WorkoutType` (`WeeklyPlanEntity.kt:56-65`). |
| `targetDistanceM` e `targetTimeSec` são salvos no objetivo, mas não são lidos pelo gerador atual. | **CONFIRMADA** | `ProfileJson.kt:31-50` persiste/decodifica ambos; `WeeklyPlanGenerator` usa objetivo/tipo/data no taper, sem ler distância/tempo. `PlanReplanner` canonicaliza objetivo como `type@targetDate`, omitindo distância/tempo (`PlanReplanner.kt:307-329`). |
| O taper atual preserva a duração de `QUALITY`. | **CONFIRMADA** | `WeeklyPlanGenerator.kt:267-275` explicitamente não escala `QUALITY`; escala as demais sessões com duração. |
| A semana atual é o limite de geração/replanejamento. | **CONFIRMADA** | `TodayViewModel.kt:118-129, 168-201` obtém apenas a semana de `today`; `PlanReplanner.kt:36-40, 64-80` limita a semana vigente e gera o futuro restante. Não há plano de bloco/múltiplas semanas exposto. O gerador é chamado novamente conforme a semana muda. |
| A estrutura persistida da sessão não contém segmentos executáveis. | **CONFIRMADA** | `PlannedSession` tem data, tipo, título, duração, alvos e rationale (`WeeklyPlanGenerator.kt:28-49`); não tem aquecimento, repetições ou recuperações. O plano do ciclo 2 já advertia que título/rationale não são contrato executável (`PLANO_CICLO_2_GUIADO_E_REPLANEJAMENTO.md:103-108`). |
| O Room está no schema 13, e o schema não possui `profileHash` ou `profileSnapshotJson`. | **CONFIRMADA** | `MeuCoachDatabase.VERSION = 13` (`MeuCoachDatabase.kt:79-81`); `WeeklyPlanEntity.kt:15-37` contém versão e hashes de replanejamento, não de perfil. A entidade do schema 13 está em `core/data/schemas/com.meucoach.core.data.db.MeuCoachDatabase/13.json`. |
| `planned_sessions.type` está representado como `TEXT` no schema. | **CONFIRMADA** | Schema 13, entidade `planned_sessions`, `createSql` na região da linha 943: `type TEXT NOT NULL`, sem restrição `CHECK` de valores. |
| `saveProfile()` e `applyReplan()` são transações distintas. | **CONFIRMADA** | `ProfileRepository.saveProfile()` usa uma transação própria (`ProfileRepository.kt:22-30`); `TrainingPlanRepository.applyReplan()` relê e grava o plano em outra transação (`TrainingPlanRepository.kt:184-297`). |
| As notificações atuais usam sessão/plano, não o briefing ou a prescrição diária. | **CONFIRMADA** | `ReminderWorker.kt:27-32` consulta `plans.getWeek`; `ReminderContent.workout()` usa título/rationale de `PlannedSession` (`ReminderContent.kt:42-52`). |
| O app não tem hoje `SessionBriefingEngine`, `SessionBriefing` ou `RACE`. | **CONFIRMADA** | Busca global no código não encontrou esses símbolos. |

### Afirmações parcialmente confirmadas ou não comprovadas

| Afirmação | Estado | Parecer independente |
|---|---|---|
| “Reutilizar a mecânica existente” de sugestão/aceite/recusa/supressão para T4. | **PARCIALMENTE CONFIRMADA** | `ReplanSuggestionStore` guarda recusa por semana/versão/hash e supressão até a data (`app/replan/ReplanSuggestionStore.kt:15-53`); `ReplanViewModel` reavalia ao abrir. Reutilizar é razoável, mas não resolve por si só persistência/atualização de baseline do perfil, confluência de T1/T2/T4 nem corrida concorrente perfil×aceite. |
| `RACE` “não exige migração”. | **PARCIALMENTE CONFIRMADA** | O campo persistido é `TEXT`, então adicionar um nome de enum não exige necessariamente DDL. Isso **não** prova compatibilidade funcional: JSON usa `WorkoutType.valueOf()` (`PlanJson.kt:98-116`), e diversos `when`/políticas são exaustivos. A migração 13→14 segue obrigatória para os dois campos de perfil propostos. |
| O snapshot é “compacto” e não sensível. | **NÃO VERIFICÁVEL como contrato** | O PRD lista campos a excluir, mas ainda não define o schema JSON, retenção em snapshots de revisão, política de evolução nem semântica do hash. Os campos sugeridos parecem não incluir nome/foto/peso, mas a privacidade depende da allowlist real. |
| Decisões D-1…D-6 estão fechadas com o usuário 0. | **NÃO VERIFICÁVEL independentemente** | Essa afirmação está no próprio PRD (§3), mas não foi encontrada em `DECISIONS.md` nem em ADRs aceitos. O índice em `docs/DECISIONS.md:11-38` ainda não lista ADR 0029/0030; o ADR 0026 mantém os limiares de iniciante pendentes. Não assumo que a decisão não ocorreu; falta registro rastreável. |
| A proposta “acompanha o atleta ao longo das semanas”. | **PARCIALMENTE CONFIRMADA** | O gerador de semana corrente é chamado a cada semana e usa a data da prova, portanto pode produzir semanas sucessivas alteradas conforme a prova se aproxima. Porém, não há bloco de treino futuro, calendário prospectivo nem visão de preparação multissemanal. A UX precisa chamar isso de adaptação semanal progressiva, não de plano completo para prova. |
| O app permite “alterar distância da prova” no Perfil. | **PARCIALMENTE CONFIRMADA** | A distância existe em `TrainingGoal`, mas `OnboardingForm` expõe tipo/data/tempo, não campo explícito de distância (`OnboardingForm.kt:40-60, 119-139`). Para metas conhecidas a distância é default por tipo (`:240-254`); para `CUSTOM` não há entrada exibida de distância no formulário consultado. |

### Motores atuais que o PRD deve tratar como restrições reais

1. `PlanReplanner` mantém hoje/passado e futuras `DONE`/`CLOSED`, regenera apenas `date > today` e valida disponibilidade de toda sessão candidata futura (`PlanReplanner.kt:71-90, 154-190`).
2. `GetTodayPrescription` inclui sessão `DONE` como sessão planejada de referência (`GetTodayPrescription.kt:36-48`). Portanto, a exceção D-6 para `DONE`/`CLOSED` não pode ser implementada somente no `DailyPrescriptionEngine`: o status não faz parte de `DailyPrescriptionInput`.
3. `WorkoutType` é usado em prescrição, alvo, conclusão, troca, replanejamento, Story, Hoje e notificação. Adicionar `RACE` não é uma simples alteração de enum.
4. A prescrição diária é a única autoridade do dia (ADR 0023; `GetTodayPrescription.kt:27-71`). O briefing precisa derivar da **prescrição persistida vigente**, e não recalcular uma intenção concorrente na UI.

## PARTE 3 — Achados da auditoria

### Achados de severidade ALTA

#### AUD-001 — Taper por data não está definido por sessão e as janelas específicas não estão sustentadas

1. **ID:** AUD-001
2. **Severidade:** ALTA
3. **Confiança:** Alta
4. **Seção/requisito do PRD afetado:** §4.2, FR-201/FR-203; `R-PLAN-10/11`; C3-06.
5. **Problema encontrado:** O texto diz que o taper será calculado em dias e que a última sessão forte ficará a 7–10 dias (5K/10K) ou 10–14 dias (meia/maratona), mas não especifica o algoritmo que escolhe/remaneja a última sessão forte, nem como dias restantes, semana parcial, disponibilidade e sessão já protegida interagem. O gerador atual computa dias de `weekStart` até a data-alvo (`WeeklyPlanGenerator.kt:428-435`) e não reduz duração de `QUALITY` (`:267-275`). Portanto, mudar o cálculo do fator por dias não basta para cumprir a regra da última sessão forte. Também há tensão entre “sem treino forte na semana da prova” e preservar intensidade em doses pequenas durante taper.
6. **Evidência no projeto:** `WeeklyPlanGenerator.kt:267-275, 428-435`; `SPORTS_SCIENCE.md §8.6, §8.8`; `PlanReplanner.kt:82-90` protege hoje/passado e sessões futuras concluídas.
7. **Consequência prática:** Uma semana pode receber o fator de taper e ainda manter a sessão `QUALITY` intacta; uma mudança de data no meio da semana pode deixar treino forte perto demais da prova; o usuário pode interpretar “taper aplicado” como preparação coerente quando apenas EASY/LONG foram reduzidos.
8. **Recomendação:** Especificar regra por **data de cada sessão**, com janela e elegibilidade explícitas; definir se uma “ativação” é sessão separada ou estímulo breve dentro de EASY e se conta como qualidade. Identificar cada limite exato (7–10/10–14 dias) como evidência, convenção de treinador ou heurística do produto. Testar o algoritmo em todas as datas disponíveis, prova em cada dia da semana e prova na semana corrente. O gerador semanal progressivo deve ser descrito como tal; não prometer periodização completa.
9. **Impacto:** FR-201/203, `R-PLAN-10/11`, C3-06/07, `SPORTS_SCIENCE.md`, ADR 0030, golden/testes do gerador e copy de semana de prova.

**Validação externa:** Bosquet et al. (meta-análise de 27 estudos em atletas competitivos) favorece em média taper de 2 semanas, redução de volume de 41–60%, com intensidade e frequência mantidas; isso sustenta a estratégia geral, não a data fixa da última sessão forte para cada distância. Um estudo pequeno de corredores de distância mostrou benefício com um taper de 7 dias incluindo intervalos intensos. Logo, as fontes citadas não justificam uma regra universal de “último forte” por distância nem a proibição uniforme de intensidade na semana inteira. Fontes: [Bosquet et al., 2007 — PubMed](https://pubmed.ncbi.nlm.nih.gov/17762369/); [Houmard et al., 1994 — PubMed](https://pubmed.ncbi.nlm.nih.gov/8007812/). Evidência limitada por população e protocolo; não se deve converter o resultado de um estudo em prescrição universal.

#### AUD-002 — `RACE` não tem contrato de evento, e conflita com disponibilidade/replanejamento/prescrição

1. **ID:** AUD-002
2. **Severidade:** ALTA
3. **Confiança:** Alta
4. **Seção/requisito do PRD afetado:** §4.2, FR-202/203/207/208; §8; §11; C3-06/07.
5. **Problema encontrado:** O PRD introduz `RACE` como “tipo de sessão”, mas não define se é evento ou treino, duração/carga, possibilidade de conclusão, falta/cancelamento, troca, matcher, progressão, contagem de adesão ou atualização pós-prova. A prova fora da disponibilidade é exigida por FR-202, mas o replanejador rejeita qualquer candidata futura em dia não disponível (`PlanReplanner.kt:184-189`). `SessionSwapper` só exclui `REST`, podendo trocar qualquer novo tipo (`SessionSwapper.kt:17-40`). A prescrição diária não tem caso `RACE` em `DailyPrescriptionEngine.kt:75-198` nem em `PrescriptionPolicy.targets()` (`PrescriptionTargets.kt:71-83`). Além disso, uma revisão protege hoje (`date <= today`), então mudar uma meta para hoje não converte a intenção de hoje em prova.
6. **Evidência no projeto:** `WorkoutRecommender.kt:19-24`; `PlanReplanner.kt:82-90, 184-189`; `SessionSwapper.kt:17-40`; `GetTodayPrescription.kt:36-63`; `PlanCompletionCalculator.kt:46-84`; `ReminderContent.kt:42-52`; `core/data/schemas/.../13.json`, `planned_sessions.type TEXT`.
7. **Consequência prática:** Uma prova fora dos dias disponíveis pode impedir aceite de T4; pode receber alvos de treino incompatíveis ou cair no ramo de outro tipo; pode aparecer como treino intercambiável, entrar na carga/adesão como sessão comum ou ser removida pela próxima revisão. O comportamento para prova hoje/amanhã não fica assegurado pelo fluxo existente.
8. **Recomendação:** Antes de criar o enum, decidir entre entidade/modelo de evento de prova e sessão de treino. Se continuar como sessão, definir semântica para availability exemption, limite de carga e duração, conclusão, falta/cancelamento, swap, matcher, replanejamento, `DailyPrescription`, notificação e pós-prova. Excluir `RACE` de operações de troca e de limites de treino onde apropriado. Definir como uma data-alvo que cai em `today` pode atualizar a prescrição sem quebrar a regra de plano como intenção/autoridade diária.
9. **Impacto:** `core:model`, `core:domain` (gerador, `PlanReplanner`, prescrição, swap, conclusão, targets), `core:data` (serialização), Hoje, Story, notificações, copy, ADR 0030 e regressões de C1/C2.

#### AUD-003 — A recuperação pós-prova é uma heurística sem evento de conclusão nem base citada para os limites

1. **ID:** AUD-003
2. **Severidade:** ALTA
3. **Confiança:** Alta
4. **Seção/requisito do PRD afetado:** §4.2 Fase 2B, FR-207; `R-PLAN-10`; §6.1; C3-07.
5. **Problema encontrado:** A regra fixa “um dia fácil por 3 km”, mínimo de 3 dias sem qualidade, uma semana após meia e duas após maratona pressupõe que a prova ocorreu. O PRD não define evento de conclusão, prova cancelada/não realizada, recuperação após esforço submáximo, nem como a janela atravessa semanas já geradas. `SPORTS_SCIENCE.md §8.10` contém a heurística 1 dia/3 km e mínimo 3 dias para 5K/10K, mas o catálogo não associa evidência pós-prova específica; as referências `BOSQUET`, `MUJIKA` e `ISSURIN` citadas em `R-PLAN-10` sustentam taper/periodização, não estes intervalos de recuperação pós-evento.
6. **Evidência no projeto:** `SPORTS_SCIENCE.md:348-353, 466-494`; `SessionExecution` e estados atuais não registram entidade de evento/prova concluída (`DATA_MODEL.md §3.8.2`). Não foi encontrado gatilho pós-prova em `PlanReplanner`, `WeeklyPlanGenerator` ou `DailyPrescriptionEngine`.
7. **Consequência prática:** Se o atleta não competir, o app ainda poderá impor recuperação; se competir e não registrar corrida, o plano pode não iniciar recuperação; uma janela fixa pode impedir qualidade por um período sem considerar esforço, sintomas ou prontidão. A copy “sem treino forte por N dias” pode soar como regra científica individual.
8. **Recomendação:** Separar “prova planejada” de “prova realizada”; definir a fonte de confirmação e o que ocorre com evidência `ACTIVITY`/`MANUAL`/ausente. Não codificar as janelas exatas como regra científica sem fontes próprias e revisão. Até validar, manter o sistema de prontidão/segurança existente e, se necessário, uma recomendação conservadora claramente marcada como heurística de produto, com limites e opt-out.
9. **Impacto:** FR-207, `SPORTS_SCIENCE.md`, ADR 0030, modelo/evento, plano por semana, conclusão, importação/matcher, copy e cenários de pós-prova.

#### AUD-004 — Contrato de fingerprint incompleto e sem semântica para baseline, no-change e evolução

1. **ID:** AUD-004
2. **Severidade:** ALTA
3. **Confiança:** Alta
4. **Seção/requisito do PRD afetado:** §4.1, FR-101/102/103/106/108; §7; §8; §11.1.
5. **Problema encontrado:** A lista do `profileHash` inclui experiência/objetivo/data/distância/tempo/esteira, mas omite calibração que já muda alvos (pace de limiar e LTHR; `PrescriptionPolicy.targets()` em `PrescriptionTargets.kt:86-131`). O hash atual usado no replanejamento omite `targetDistanceM` e `targetTimeSec` (`PlanReplanner.kt:307-329`), portanto uma sugestão baseada nesses campos pode não ficar obsoleta no aceite. Também não há versão/namespace do formato canônico, política de arredondamento/normalização, nem separação entre o fingerprint que explica **o plano gerado** e o último fingerprint **avaliado/aceito**. Se `NoChange` não atualizar o baseline, a mesma alteração pode ser reavaliada em todo resume; se atualizar o fingerprint do plano, o histórico pode declarar que a versão foi gerada por um perfil que na realidade não a gerou. A precedência T4>T2>T1 escolhe uma causa, mas a entrada gerada combina todos os inputs.
6. **Evidência no projeto:** `AthleteProfile` inclui calibração e objetivo (`core/model/.../AthleteProfile.kt:50-87`); `PrescriptionTargets.kt:86-131`; hash canônico incompleto em `PlanReplanner.kt:292-329`; `ReplanTrigger` atual é unitário (`ReplanModels.kt:16-25`); `StoredWeeklyPlan` só tem hashes atuais de inputs/disponibilidade (`TrainingPlanRepository.kt:25-33`).
7. **Consequência prática:** Sugestões podem não invalidar após alterar um alvo usado na proposta, repetir indefinidamente, ou perder a proveniência entre perfil atual e versão do plano. T4 pode ocultar uma mudança simultânea de disponibilidade/adesão e apresentar explicação incompleta.
8. **Recomendação:** Definir um contrato versionado e canônico, explicitando campos incluídos/excluídos e sua relação real com o plano. Incluir calibração e todos os campos usados por F2; ter um `generatedFromProfileHash`/snapshot imutável e, se necessário, outro `lastEvaluatedProfileHash` para não repetir `NoChange`. Guardar hashes/snapshots anterior e novo na revisão. Em vez de descartar causas concorrentes, registrar `trigger` principal + `causes`, ou uma lista ordenada. Um legado `NULL` deve ser inicializado sem proposta; a atualização após `NoChange` precisa ser transacional e auditável.
9. **Impacto:** FR-101..108, `ProfileFingerprint`, `ReplanInput`/`ReplanSuggestion`, `ReplanContextBuilder`, `TrainingPlanRepository`, entidade/JSON da revisão, migração, export/reset, testes determinísticos e copy do diff.

#### AUD-005 — O aceite pode aplicar um candidato após alteração concorrente do perfil

1. **ID:** AUD-005
2. **Severidade:** ALTA
3. **Confiança:** Alta
4. **Seção/requisito do PRD afetado:** §4.1 passos 2/4, FR-104/105; §10; §11.2; concorrência/obsolescência.
5. **Problema encontrado:** `ApplyReplan` reconstrói o contexto e calcula `currentInputsHash` antes de chamar `TrainingPlanRepository.applyReplan()` (`ApplyReplan.kt:39-60`). O repositório valida dentro da sua transação a versão do plano e o hash que o chamador **passou**, mas não relê `AthleteProfile`/fingerprint nessa mesma transação (`TrainingPlanRepository.kt:184-217`). Como `saveProfile()` é uma transação independente, o perfil pode mudar entre o cálculo do hash e a aplicação. O PRD prevê reutilizar o fluxo existente, mas não fecha essa janela.
6. **Evidência no projeto:** `ProfileRepository.kt:22-30`; `ApplyReplan.kt:39-60`; `TrainingPlanRepository.kt:184-217`.
7. **Consequência prática:** Uma sugestão criada para o perfil A pode ser aceita depois de o perfil mudar para B, deixando plano e perfil inconsistentes até uma nova avaliação. Isso é particularmente importante para downgrade de nível e para objetivo/data da prova.
8. **Recomendação:** Fazer o compare-and-apply do fingerprint atual no mesmo limite transacional que confirma a versão do plano. Como perfil e plano ficam no mesmo Room, o repositório/caso de uso deve ler a versão/fingerprint persistido do perfil dentro da transação e retornar `Stale` se divergir; alternativamente, coordenar todas as escritas por um mecanismo que efetivamente cubra todos os caminhos de `saveProfile`, com teste concorrente. Não confiar apenas em reavaliação em memória.
9. **Impacto:** FR-104/105, `ApplyReplan`, repositório/DAO, fluxo de edição/calibração e testes de integração concorrente.

#### AUD-006 — A guarda de iniciante não especifica o tipo afetado nem respeita a autoridade/status da sessão

1. **ID:** AUD-006
2. **Severidade:** ALTA
3. **Confiança:** Alta
4. **Seção/requisito do PRD afetado:** D-6, FR-109, `R-PRESC-04`; §11.1.
5. **Problema encontrado:** “Qualquer treino forte” não está definido. Deve significar `QUALITY` apenas, ou inclui LONG acima do limite? Um plano antigo de nível avançado pode conter longão superior ao teto de iniciante (o gerador atual usa 150 min para beginner e 180 para advanced: `WeeklyPlanGenerator.kt:442-448`). FR-109 só fala em “treino forte”; FR-107 fala em cortar longão apenas em dias futuros. Além disso, `GetTodayPrescription` passa sessão `DONE` como intenção (`GetTodayPrescription.kt:36-48`), mas `DailyPrescriptionInput` não carrega status e a regra nova não explica como impedir nova prescrição de uma sessão já concluída/encerrada. O PRD cita ADR-0026/Daniels, mas a elegibilidade de iniciante no ADR 0026 ainda está explicitamente pendente e a fonte não sustenta por si só a guarda categórica.
6. **Evidência no projeto:** `DailyPrescriptionEngine.kt:35-198` não recebe experiência/status em uma regra de decisão; `DailyPrescriptionInput` em `:15-25`; `GetTodayPrescription.kt:36-63`; `WeeklyPlanGenerator.kt:170-174, 442-448`; `ADR 0026 §3.3` permanece proposta.
7. **Consequência prática:** A regra pode rebaixar indevidamente treino fácil/longão, ou não tratar longão incompatível após downgrade; um treino já feito pode gerar nova prescrição histórica quando o perfil muda no mesmo dia. A implementação pode violar a promessa de “não tocar DONE/CLOSED”.
8. **Recomendação:** Especificar `QUALITY → EASY` como o escopo da regra, decidir explicitamente como tratar longões já planejados e quais limites têm base de regra. Incluir status no contexto ou impedir que a política seja aplicada a sessão `DONE`/`CLOSED`; preservar execução e prescrição histórica. Justificar `R-PRESC-04` como política conservadora de produto, sem atribuir suporte científico que a referência não oferece. Testar downgrade antes/depois de concluir no mesmo dia.
9. **Impacto:** FR-109, `DailyPrescriptionInput`/engine, `GetTodayPrescription`, `R-PRESC-04`, `SPORTS_SCIENCE.md`, ADR 0026 e testes de regressão.

#### AUD-007 — O briefing não pode derivar os blocos prometidos da sessão disponível e o exemplo contradiz os próprios requisitos

1. **ID:** AUD-007
2. **Severidade:** ALTA
3. **Confiança:** Alta
4. **Seção/requisito do PRD afetado:** §4.3, FR-301..306; anexo A; §7.2.
5. **Problema encontrado:** A sessão só contém tipo, título, duração total, alvos e rationale; não contém estrutura de intervalos. A regra do PRD “template por tipo/duração” não especifica como obter repetições, recuperação, duração da parte principal ou a intensidade do aquecimento. O plano técnico do ciclo 2 já declara explicitamente que a sessão não tem intervalos e que título/rationale não é contrato executável (`PLANO_CICLO_2_GUIADO_E_REPLANEJAMENTO.md:103-108`). O exemplo “30 min” soma 10 min de aquecimento + 20 min forte + 5 min desaquecendo = **35 min**; o treino não fecha. “Reduza 10 s/km” e “2×8 min/2 min” criam alvos/estrutura ausentes do plano e não são reavaliados pela prescrição. O alvo fixo de 5:30–5:40/km também não é derivado de perfil algum no exemplo.
6. **Evidência no projeto:** `PlannedSession` não tem segmentos (`WeeklyPlanGenerator.kt:28-49`); alvos de QUALITY derivam zona T/LTHR/pace limiar (`PrescriptionTargets.kt:110-131`); exemplo contraditório no próprio PRD §4.3 linhas 225-234; duração de qualidade planejada em `WeeklyPlanGenerator.kt:192-215`.
7. **Consequência prática:** O usuário recebe instruções mais específicas do que a decisão determinística realmente tomou; a duração anunciada pode divergir da duração prescrita; ajuste sugerido pode converter sessão em treino diferente sem registro ou nova versão da prescrição.
8. **Recomendação:** Para v1, escolher entre (a) briefing genérico que só descreve duração/alvo existentes, sem inventar intervalos nem pace, ou (b) enriquecer o modelo canônico com estrutura de treino (incluindo repetições, recuperação e duração total) e fazer plano, prescrição, briefing, snapshot e histórico consumirem a mesma estrutura. Reescrever e validar todos os exemplos por soma exata; reduzir/encurtar somente sem adicionar uma alternativa numérica não recalculada.
9. **Impacto:** F3, `SessionBriefing`, `PlannedSession`/targets, gerador, snapshots/export, tests de domínio, copy e futura sessão guiada.

#### AUD-008 — F4 expande escopo de nutrição e o aviso legal não torna as recomendações seguras

1. **ID:** AUD-008
2. **Severidade:** ALTA
3. **Confiança:** Alta
4. **Seção/requisito do PRD afetado:** O-4, D-1, §4.4, FR-401..404, `R-FUEL-01/02`, §6.3 e §14.
5. **Problema encontrado:** `PRODUCT.md §4` exclui nutrição/alimentação e `SPORTS_SCIENCE.md §1, §9.4, R-SAFE-03` exclui prescrição nutricional. O PRD tenta abrir exceção por decisão atribuída ao usuário, mas ela não está no registro de decisões encontrado. “Água com eletrólitos/sais” é uma recomendação de suplementação/eletrólito e pode ser interpretada como aplicável a qualquer treino “longo ou muito quente”, sem duração, taxa de suor, disponibilidade de água ou tolerância. “Água à vontade” pode ser lida como beber sem limite; posição atual da NATA alerta tanto para hipohidratação quanto para hiper-hidratação e recomenda estratégia individualizada conforme suor, ambiente, aclimatação, duração e intensidade. “Refeição com carboidrato e proteína” após qualquer treino é orientação nutricional, mesmo sem números. O próprio §4.4 permite eletrólitos e proíbe “suplementos”, uma contradição interna. O rodapé de responsabilidade não neutraliza o conteúdo nem delimita as condições de uso.
6. **Evidência no projeto:** `PRODUCT.md:75-80`; `SPORTS_SCIENCE.md:7-10, 385-409, 523-525`; PRD §4.4 linhas 240-250 e §6.1 linhas 323-324. Fontes: [NATA Position Statement, 2025](https://www.nata.org/sites/default/files/2025-08/FluidReplacementsForAthletes.pdf); [Thomas, Erdman & Burke, 2016 — PubMed](https://pubmed.ncbi.nlm.nih.gov/26891166/). A posição da ACSM de 2007 também recomenda reposição individualizada e trata risco de excesso, não “eletrólitos para todo treino quente/longo”: [Sawka et al., 2007 — PubMed](https://pubmed.ncbi.nlm.nih.gov/17277604/).
7. **Consequência prática:** Conteúdo pode sugerir sais/eletrólitos desnecessários, aumentar ingestão excessiva de fluidos ou ser aplicado por pessoa com condição clínica/restrição sem que o app tenha contexto para avaliar. A mudança também enfraquece uma fronteira explícita do produto e as instruções futuras para agentes.
8. **Recomendação:** **Remover F4 do ciclo**, até haver decisão registrada em ADR e revisão de escopo/científica. Se houver exceção posterior, limitar a conteúdo educacional neutro revisado por profissional, sem recomendação universal de sais/eletrólitos/refeição, sem “à vontade”, com condições explícitas e fonte adequada. Não alterar `AGENTS.md` para uma permissão ampla; manter a exceção estreita e rastreável. Não tratar disclaimer como controle de risco.
9. **Impacto:** O-4, FR-401..404, D-1, `PRODUCT.md`, `SPORTS_SCIENCE.md`, `COMMUNICATION.md`, `AGENTS.md`, ADR 0029, catálogo de regra/evidência e validação de conteúdo.

### Achados de severidade MÉDIA

#### AUD-009 — A migração 13→14 é simples no conceito, mas o contrato de persistência/export está subespecificado

1. **ID:** AUD-009
2. **Severidade:** MÉDIA
3. **Confiança:** Alta
4. **Seção/requisito do PRD afetado:** §7.1/7.2; FR-101/103; §11.2; §14.
5. **Problema encontrado:** Os campos novos não podem ficar apenas na tabela e no modelo do PRD: `saveWeek()` constrói `WeeklyPlanEntity`, apaga/reinsere sessões e preserva metadados anteriores (`TrainingPlanRepository.kt:133-158`); `applyReplan()` atualiza a linha e snapshots (`:257-295`); export usa `PlanJson` (`PlanJson.kt:20-41`) e reset apaga revisões/planos. O PRD não diz onde perfil anterior/novo aparece em `previousPlanJson`/revisões nem como hash/snapshot serão preservados em `saveWeek`, aceite, `NoChange`, export e reset. Não existe fluxo de import/restore do JSON implementado no repositório consultado; portanto, não se deve afirmar compatibilidade de importação como capacidade atual.
6. **Evidência no projeto:** `WeeklyPlanEntity.kt:15-37`; `TrainingPlanRepository.kt:133-158, 257-295`; `PlanJson.kt:20-41, 43-64`; `UserDataRepository.kt:49-59, 90-99`; `DATA_MODEL.md §9` diz que o export existe e backup restaurável é futuro.
7. **Consequência prática:** Hash pode ser apagado silenciosamente por caminho de salvamento, snapshots não explicam o perfil que motivou a revisão e export pode conter apenas o estado atual. Migração testada isoladamente pode passar e ainda falhar no fluxo real de escrita/export/reset.
8. **Recomendação:** Descrever como ambos campos atravessam todas as leituras/escritas e snapshots; criar migration `13→14` explícita, preservar plano/sessões/revisões campo a campo, deixar valor legado `NULL`, testar tabela final/schema exportado, export/reset e escrita normal + aceite. Separar testes de migração de testes de persistência. Declarar que import/restore está fora do escopo enquanto não existir.
9. **Impacto:** `WeeklyPlanEntity`, `StoredWeeklyPlan`, `TrainingPlanRepository`, `PlanJson`, migration/schema, export/reset, `DATA_MODEL.md`, testes de `core:data`.

#### AUD-010 — Copy e navegação de prova/briefing contêm promessas incompatíveis com datas e notificações atuais

1. **ID:** AUD-010
2. **Severidade:** MÉDIA
3. **Confiança:** Alta
4. **Seção/requisito do PRD afetado:** FR-202/204/307/310; §9.1/9.2; §10.
5. **Problema encontrado:** O texto de semana de prova fixa “até sábado” e “chegar descansado” (PRD §9.2), mas a prova pode cair em qualquer dia. FR-307 promete abrir o sheet pela notificação; a notificação atual tem deep link apenas para Hoje (`ReminderContent.kt:11-12, 47-52`) e não recebe sessão/briefing. FR-310 manda usar o foco do briefing, enquanto o conteúdo atual deriva de `PlannedSession.rationale`, não da prescrição diária. `FR-208` diz que distância muda, mas o formulário atual não expõe distância direta; datas de prova passadas também são rejeitadas ao salvar (`OnboardingForm.kt:175-185`), exigindo política de remoção/renovação da meta.
6. **Evidência no projeto:** `ReminderContent.kt:42-53`; `OnboardingForm.kt:175-185, 240-254`; PRD §9.2 linhas 406-413 e FR-208.
7. **Consequência prática:** Texto contradiz dia real da prova; alerta pode recomendar sessão planejada não prescrita; toque na notificação não entrega o conteúdo anunciado; requisito de alterar distância não tem caminho de UI para metas customizadas.
8. **Recomendação:** Tornar copy relativa a `targetDate`/dia da semana, validar cenário de prova em cada dia. Definir se notificação abre Hoje ou o briefing diretamente; usar prescrição diária vigente e tratar notificação stale. Delimitar FR-208 ao que a UI permite ou adicionar explicitamente um campo de distância customizada com validação. Especificar como meta expirada pode ser removida/alterada.
9. **Impacto:** `COMMUNICATION.md`, strings/formatters, deep links/notification, perfil/onboarding, FR-204/208/307/310 e testes de UI/worker.

#### AUD-011 — A sequência C3-01…C3-07 cria dependência circular de `RACE` e subestima o trabalho transversal

1. **ID:** AUD-011
2. **Severidade:** MÉDIA
3. **Confiança:** Alta
4. **Seção/requisito do PRD afetado:** §13; C3-01..C3-07; §5 prioridades/esforço.
5. **Problema encontrado:** C3-03 (briefing) precede C3-06 (introdução de `RACE`), mas FR-303 exige template `RACE` desde C3-03. A sequência também implementa F4 antes de validar se o escopo nutricional deve existir; C3-01 combina fingerprint, T4, migration e guarda de prescrição, embora partes tenham dependências e gates diferentes. A estimativa S para alguns requisitos não contabiliza alteração de entidades/DAO/JSON, migração, export/reset e consumidores do enum.
6. **Evidência no projeto:** PRD §5 FR-303, §13 linhas 502-508; `WorkoutType` consumido em diversos `when`; fluxo Room existente com migration 12→13 em `MeuCoachDatabase.kt:399-443` e testes de migração em `MeuCoachDatabaseMigrationTest.kt`.
7. **Consequência prática:** Implementação de briefing precisa antecipar tipo ainda inexistente ou será refeita; teste de enum fica incompleto; F4 pode consumir trabalho antes de aprovação técnica; entregas deixam de ser incrementais.
8. **Recomendação:** Separar primeiro o hotfix da guarda diária; depois fingerprint/migração e T4; então fluxo de perfil. Fechar o modelo/semântica da prova antes de briefing para `RACE`. Adiar F4. Sequência detalhada na Parte 8. Rever esforço após decomposição; C3-01 atual não é plausivelmente S como entrega total.
9. **Impacto:** tabela §13, FR-301/303/401, gates, ADRs 0029/0030, distribuição de tarefas e estimativas.

#### AUD-012 — Os critérios de aceite não cobrem suficientemente obsolescência, datas e a diferença entre briefing atual e histórico

1. **ID:** AUD-012
2. **Severidade:** MÉDIA
3. **Confiança:** Alta
4. **Seção/requisito do PRD afetado:** §10/§11, especialmente §11.1–11.4; FR-301/307.
5. **Problema encontrado:** Há bons casos unitários de fingerprint e migration, mas não gate explícito para alterações repetidas de perfil, T4 simultâneo a T1/T2, reabertura após morte de processo, corrida concorrente perfil/aceite, hash atualizado após `NoChange`, mudança de objetivo para hoje/past date, sessões `DONE`/`CLOSED`, fuso do perfil, notificações com prescrição reduzida nem renderização em fonte 200%. O PRD reconhece que briefing passado pode refletir perfil atual, mas também promete briefing de leitura “como era o plano”; não define qual dado autoriza essa afirmação. `TESTING.md` informa que não há `androidTest` nem E2E automatizado (`TESTING.md:27-30, 55`), portanto “TalkBack” e validação em aparelho são gates manuais sem roteiro verificável.
6. **Evidência no projeto:** cobertura atual de replanejamento lista testes T1/T2/T3 e stale (`TESTING.md:160-163`); ausência de instrumentados (`:27-30`); `DailyPrescription` versionada, mas sem snapshot do perfil (`DATA_MODEL.md §3.8.3`).
7. **Consequência prática:** A suíte pode ficar verde sem provar os riscos reais de sequência, fuso, obsolescência e histórico. A tela pode apresentar briefing atual como se descrevesse intenção antiga.
8. **Recomendação:** Adicionar a matriz concreta da Parte 5 como critérios de bloqueio por entrega. Para briefing histórico, decidir entre não oferecê-lo, reconstruí-lo de snapshot suficiente, ou rotulá-lo claramente como orientação atual; não usar “como era” sem inputs/versionamento históricos. Registrar TalkBack/fonte 200% como checklist manual com dispositivo/versão/resultado, já que não há instrumentação hoje.
9. **Impacto:** §11, testes `core:domain`/`core:data`/`:app`, critérios de acessibilidade e documentação do ciclo.

## PARTE 4 — Análise por funcionalidade

### F1 — Plano que acompanha o perfil

**Correto**

- O aceite explícito é consistente com ADR 0023/0028 e com a semântica atual de plano como intenção.
- Reutilizar `PlanReplanner`, `ReplanSuggestionStore` e o diff é mais coeso que criar outro motor de replanejamento.
- Guardar referência/snapshot evita inferir mudança em plano legado; hashes `NULL` no legado já são uma prática correta no ADR 0028.
- A recusa por proposta e supressão diária existentes são base útil para evitar repetição sem impedir pedido explícito.

**Incompleto / incorreto**

- O fingerprint precisa refletir campos que mudam o plano **e os alvos**. Calibração de pace/FC é relevante; distância/tempo-alvo também terão relevância quando FR-205/206 forem implementados. O hash atual de replanejamento não contém os dois últimos.
- Não há contrato para “sem mudança” e “upgrade só vale na próxima semana”: gravar a versão do perfil no plano como se o plano já tivesse sido gerado por ela conflita com provenance. Separar fingerprint de geração e checkpoint de avaliação.
- A precedência T4>T2>T1 não deve apagar causas simultâneas: um único candidato pode ser calculado com a combinação de perfil, disponibilidade e sessões perdidas. Mostrar causa principal e coocorrências.
- `FR-107` deve ser específico. A regra de iniciante para qualidade pode precisar reduzir um plano antigo; o tratamento de longão acima do teto não está coberto pelo D-6.
- Perfil é salvo não só no fluxo de onboarding/perfil, mas também em `CalibrationViewModel.save()` (`CalibrationViewModel.kt:96-114`). Se calibração mudar targets que entram no hash, esse caminho também precisa avaliar/atualizar proposta.

**O que alterar**

- Especificar o fingerprint, sua versão e o snapshot sem campos pessoais desnecessários.
- Distinguir `generatedFromProfileHash` de `lastEvaluatedProfileHash` (ou equivalente) e decidir quando cada um avança.
- Tornar o compare-and-apply do perfil atômico com a revisão.
- Limitar D-6 a comportamento explícito de prescrição (ver AUD-006), sem tocar execução/prescrição histórica.
- Testar todas as rotas de escrita de perfil, inclusive calibração, e alteração concorrente.

### F2 — Prova com data

**Correto**

- A lacuna existe: o objetivo é persistido, mas distância/tempo não afetam hoje a semana; o taper vigente deixa a qualidade intacta.
- Fazer a prova ser reconhecível e contemplar data fora da disponibilidade é uma decisão de produto razoável, desde que não seja uma sessão de treino comum.
- Distinguir prova próxima/passada de preparação completa evita promessa falsa.

**Incompleto / equivocado**

- O plano atual é rolling: uma semana por vez. Ele pode mudar as semanas de taper quando elas passam a ser a semana atual, mas não oferece um ciclo de prova visível/planejado com antecedência. Essa limitação deve constar na história/copy.
- “Taper por dias até a prova” precisa atuar nas datas de treino, não apenas num fator uniforme baseado em `weekStart`. A janela 14/21 dias existente é por tipo; as regras específicas de última sessão forte exigem outra decisão.
- O texto não define prova de hoje, amanhã, em 8/9/10 dias, prova em dia sem disponibilidade, objetivo sem distância, objetivo sem tempo, `CUSTOM`, mudança do evento para data passada, evento cancelado e atualizações de sessão já protegida.
- `targetTimeSec / targetDistanceM` gera matematicamente um ritmo médio pretendido, mas não uma “faixa” sem regra de banda. Tempo-alvo declarado é aspiração, não prova de capacidade; não deve virar pace fisiológico recomendado. Quando disponível, performance recente/calibração é evidência melhor. Se exibir o objetivo, rotular como “ritmo correspondente à sua meta”, não como pace prescrito/garantido.
- A limitação “<10 dias” não pode significar “nenhum plano útil”: deve ser explicitado o que o app faz com o evento, as sessões antes dele e o usuário sem calibração.
- Longão limitado por fração da distância da prova não tem fração numérica definida nem evidência indicada. É requisito não implementável e pode contradizer a regra vigente de 25–30% do volume semanal.

**Validação esportiva**

- Bosquet/Mujika sustentam taper como redução de volume e manutenção de estímulos, mas não toda a janela de datas proposta pelo PRD. A meta-análise de Bosquet reuniu 27 estudos competitivos e reportou benefício médio de 2 semanas/41–60% de redução, mantendo intensidade/frequência. O estudo de Houmard encontrou benefício em protocolo de 7 dias com intervalos, em amostra pequena. Isso recomenda apresentar janelas como estratégia adotada pelo produto, e não consenso universal para todos os corredores/níveis.
- O PRD deve separar evidência, convenção de treinador e decisão de produto em `SPORTS_SCIENCE.md`; `R-PLAN-10` não pode citar taper como sustentação de recuperação pós-prova.

**O que alterar**

- Fechar o modelo de evento, limites do plano rolling, processo de inclusão de `RACE` e implicações em disponibilidade, conclusão, carga e replanejamento.
- Definir o algoritmo e critérios de teste antes de aprovar os números de taper.
- Tratar o ritmo-alvo como meta declarada ou derivá-lo de performance validada, sem inventar faixa/confiança.
- Remover FR-206 até que a regra de fração e seu rationale sejam especificados.
- Condicionar recuperação a prova efetivamente realizada e não a data planejada apenas.

### F3 — Briefing do treino

**Correto**

- Um modelo puro no domínio é coerente se representar uma explicação determinística da prescrição diária; UI e sheet ficam em `:app`.
- Derivar o briefing da prescrição do dia (KEEP/REDUCE/DOWNGRADE/REST) é a escolha certa para não contradizer Hoje.
- Manter IDs de regra/evidência fora da superfície principal segue `COMMUNICATION.md §6.1-6.5`.
- Não persistir texto derivado pode ser razoável para o briefing de hoje/futuro, desde que não se prometa reconstrução histórica igual.

**Incompleto / equivocado**

- O modelo não tem blocos; templates por tipo não bastam para ensinar intervalos sem introduzir números/estrutura novos.
- O próprio exemplo é aritmeticamente inconsistente (30 anunciados, 35 somados), tem ritmo arbitrário e alternativas não avaliadas pelo motor.
- “O que sentir” precisa variar com tipo/alvo; “se estiver difícil” não deve recomendar mudança numérica que não atualiza `DailyPrescription`.
- O texto de alerta derivado de `R-SAFE-01` não está descrito para o caso em que a sessão é treino, mas o atleta relata sinal de alerta durante execução; evitar criar triagem clínica não existente.
- `briefingEngineVersion` sem persistência não torna saída histórica auditável. A constante só é útil se entrar em resultado/hash ou se briefing versionado for armazenado.
- Se abrir uma sessão concluída, o perfil atual pode não ser o perfil e os alvos que originaram o plano. O PRD reconhece o efeito, mas não compatibiliza isso com “como era o plano”.

**O que alterar**

- Ou remover bloco executável da v1 e limitar conteúdo a alvos/duração já existentes, ou criar estrutura canônica e persistível de treino.
- Exigir igualdade entre duração total da prescrição e soma de blocos (não “≈” sem tolerância), com política explícita de arredondamento.
- Explicar limite de histórico: orientação atual versus snapshot da sessão; não usar `engineVersion` nominal como garantia sem persistir inputs/saída necessários.
- Adicionar `RACE` só depois do modelo e das políticas da prova serem estáveis.

### F4 — Hidratação e alimentação

**Correto**

- O PRD tenta proibir quantidades individualizadas, dietas, emagrecimento, jejum e suplementação e quer deixar claro que não é orientação clínica. Isso demonstra preocupação de escopo.

**Incompleto / equivocado**

- “Informação geral” ainda é orientação sobre fluidos, eletrólitos e alimentação. O aviso não limita aplicabilidade, não detecta condições clínicas e não evita interpretação como recomendação do coach.
- As referências propostas não validam a recomendação simplificada de eletrólitos para treino longo/quente sem duração ou contexto. A NATA 2025 recomenda individualizar por suor, ambiente, aclimatação, duração e intensidade e ressalta riscos de excesso de ingestão.
- Thomas et al. 2016 é uma posição de nutrição esportiva para desempenho e fornece diretrizes contextualizadas; não estabelece que todos devem comer carboidrato+proteína depois de cada treino. “Nada novo no dia da prova” é conselho prático plausível, mas deve ser citado como convenção/estratégia, não como regra universal derivada automaticamente da posição.
- A menção de “sais/eletrólitos” conflita com a proibição de “suplementos” no mesmo documento.

**O que alterar**

- Remover F4 deste ciclo, como recomendação principal desta auditoria.
- Manter o escopo atual do produto e `AGENTS.md` inalterados até decisão registrada e revisão técnica. Se uma exceção for aprovada depois, não ampliar o escopo para prescrição e não reutilizar disclaimer como justificativa suficiente.

## PARTE 5 — Lacunas e cenários não contemplados

### P0 — podem gerar plano errado, stale ou surpreender o atleta

1. **Perfil alterado várias vezes antes do aceite:** beginner→intermediate→beginner; mudança de objetivo no meio; recusa da primeira proposta; processo morto/reaberto; supressão “hoje” ainda ativa. A proposta deve comparar contra o último estado apropriado e sempre invalidar candidato anterior.
2. **T4 com T1 e T2 juntos:** perfil, disponibilidade e sessões perdidas mudam na mesma semana. Testar candidato combinado e copy que enumera as causas, sem uma prioridade ocultar as outras.
3. **Alteração concorrente entre leitura e aceite:** salvar perfil enquanto a transação de plano está sendo aceita; aceitar duas vezes; aceitar stale após perfil ter mudado; aceitar depois da troca manual de sessões.
4. **Baseline legado e `NoChange`:** hash de perfil legado nulo; primeiro registro não deve disparar; novo perfil não muda a projeção nesta semana; upgrade cujo efeito só aparece na semana seguinte; reavaliar no resume não deve gerar prompt repetido nem perder a proveniência.
5. **Prova em todo limite temporal:** ontem, hoje, amanhã, 8/9/10 dias, 13/14/15 dias, 20/21/22 dias; semanas parciais; domingo e segunda; antes/depois da virada semanal; fuso do perfil diferente do fuso do aparelho.
6. **Prova em dia indisponível e replanejamento:** evento fora da disponibilidade, todos os dias futuros indisponíveis, disponibilidade alterada após inserir `RACE`, troca manual de sessão no dia anterior e tentativa de trocar a própria prova.
7. **Data de prova alterada para hoje/passado:** `PlanReplanner` não altera hoje/passado. Definir se o evento fica como aviso, se a prescrição diária muda de forma controlada ou se a alteração é recusada; não criar retrofit invisível.
8. **Prova não ocorrida:** evento cancelado, usuário doente, não compareceu, participou mas não importou atividade, corrida sem relógio, atividade de outra distância. Definir se há recuperação e como é calculada.
9. **Objetivo incompleto:** sem data, sem tempo, sem distância (especialmente `CUSTOM`), target time incompatível com distância, tempo-alvo antigo, performance calibrada mais lenta que a meta declarada e ausência de calibração.
10. **Tipo `RACE` e estado:** sessão `DONE`, `CLOSED`, `USER_SKIPPED`, `MISSED`, import da prova após conclusão, corrida livre, vínculo duplicado, evento fora de disponibilidade e reinício de semana. Definir impacto em `PlanCompletionCalculator`, T1 e carga.
11. **Prescrição do mesmo dia após mudança de perfil:** treinar, concluir como ACTIVITY/MANUAL, depois alterar experiência/calibração; confirmar que não muda a intenção/briefing histórico nem cria um `DOWNGRADE` retroativo.

### P1 — podem produzir comunicação inconsistente ou pouca utilidade

12. **Sessões curtas:** EASY de 20 min, QUALITY no limite mínimo, prova curta. Verificar se aquecimento/parte principal/desaquecimento cabem sem arredondar para além do planejado.
13. **Briefing por estado:** REST, `NO_PLAN`, doença/lesão, readiness baixa, sessão `DONE`/`CLOSED`, DOWNGRADE de QUALITY→EASY, calor autorreportado e sem calibração. Garantir que o título, alvos e “se estiver difícil” correspondam à prescrição vigente.
14. **RACE em meio de semana:** copy não pode dizer “até sábado”; verificar plano dos dias antes/depois e longão da semana de prova.
15. **Notificação já agendada:** mudança para REST/DOWNGRADE depois do agendamento, dia de prova, clique em notificação com app encerrado, sessão ausente e notificação sem permissão.
16. **Acessibilidade real:** fonte em 200%, tela estreita, contraste light/dark, TalkBack lendo ordem e estados de seção expansível; botão “Como correr” deve ter rótulo/semântica, não só texto visual.
17. **Conteúdo de F4, se mantido contra a recomendação:** usuário com restrição clínica/alimentar, não tolera comida, não leva água, treino curto no calor, sessão longa sem calor, treino em esteira. O teste de disclaimer não prova segurança do conselho.

## PARTE 6 — Melhorias recomendadas

### Correções obrigatórias antes de implementação

1. Fechar contrato de fingerprint, baseline, `NoChange`, causas combinadas e aceitação transacional com o perfil.
2. Fechar semântica de prova como evento e do estado pós-prova, ou reduzir o escopo de F2 a “data-alvo/taper na semana vigente” sem prometer um plano de prova completo.
3. Remover ou fundamentar cientificamente os parâmetros de taper e recuperação específicos; separar fonte científica de heurística de produto.
4. Definir se briefing será genérico ou se haverá estrutura canônica de intervalos; corrigir exemplos e garantir a soma do tempo.
5. Remover F4 deste ciclo e manter as proibições de nutrição atuais, salvo decisão posterior formal.
6. Especificar migração, snapshots, export/reset e testes de `RACE` em todos os consumidores.

### Melhorias recomendadas para este ciclo

1. Dividir C3-01 entre proteção imediata da prescrição diária e persistência/T4, permitindo gate independente e rollback menor.
2. Registrar as decisões de produto em `DECISIONS.md`/ADRs antes de marcá-las como fechadas no PRD, com alternativas, consequências e responsáveis.
3. Guardar snapshot de inputs do plano em revisão append-only, suficiente para explicar o diff perfil antigo→novo sem salvar PII desnecessária.
4. Criar copy com data/weekday dinâmicos e explicitar limites do plano rolling: “a semana atual é ajustada; as semanas seguintes serão geradas conforme se aproximam”.
5. Fazer os critérios manuais de TalkBack/aparelho um roteiro reproduzível, já que a suíte atual não é instrumentada.

### Melhorias para ciclos futuros (opcionais)

1. Calendário prospectivo de preparação e periodização multissemanal, caso o produto deseje prometer uma preparação contínua para prova.
2. Modelo estruturado de segmentos reutilizável por briefing, sessão guiada e execução persistida.
3. Revisão pós-prova baseada em evidência de atividade, esforço declarado e sinais de recuperação, sem converter um calendário fixo em regra universal.
4. Validação com mais de um perfil/atleta antes de generalizar defaults que hoje são ajustados ao usuário 0.

## PARTE 7 — Proposta de revisão dos requisitos

### Manter

- **FR-102 (T4), FR-104 (avaliar ao salvar), FR-105 (reusar aceite/recusa), FR-108 (não aumentar carga em upgrade):** manter o objetivo, condicionando-os aos contratos de fingerprint, obsolescência e transação da Parte 3.
- **FR-103:** manter “hash legado desconhecido nunca é inferido como mudança”. Acrescentar que inicializar baseline não é uma revisão e que a mesma política se aplica a `NoChange` após alteração real.
- **FR-301/302:** manter briefing determinístico derivado da prescrição diária e com proveniência; restringir a conteúdo que exista no plano/prescrição ou formalizar estrutura de treino canônica.
- **FR-308/309:** manter requisitos de acessibilidade e ausência de IDs na UI comum, com roteiro testável e strings revisadas.
- **Princípios de replanejamento explícito/proteção de hoje e sessões concluídas:** manter, em conformidade com ADR 0028.

### Corrigir — redação proposta

**Novo FR-101a — fingerprint de perfil**

> “Cada versão de plano guarda o fingerprint versionado dos campos de perfil usados para gerá-la e um snapshot mínimo desses campos. A detecção de alteração usa um checkpoint separado quando o perfil foi avaliado sem revisão. A lista de campos, serialização canônica, política de precisão e exclusões de dados pessoais são definidas no ADR; o fingerprint inclui toda variável usada no plano/alvos e não inclui campos apenas de apresentação.”

**Novo FR-104a — baseline e no-change**

> “Quando a avaliação retornar `NoChange` ou ‘o efeito começa na próxima semana’, o app registra de forma transacional o estado de perfil avaliado sem afirmar que a semana vigente foi gerada por esse perfil. Reabrir o Hoje não reapresenta continuamente o mesmo resultado.”

**FR-107/109 — limite da guarda**

> “Se a experiência vigente for `BEGINNER` e a intenção de hoje for `QUALITY`, a prescrição diária pode rebaixá-la para EASY com rationale explícito. A regra não altera o plano nem a sessão concluída/encerrada, não reescreve prescrição histórica e não prescreve ritmo novo. O tratamento de LONG acima do limite do nível é especificado separadamente.”

**FR-201/203 — taper**

> “O motor determina a ação sobre cada sessão futura com base em `targetDate - session.date`. A tabela de janelas, redução de duração, manutenção/remoção de intensidade e tratamento de ativações é parte do ADR esportivo. A avaliação usa data local do perfil; sessões protegidas não são reescritas. A regra não afirma que há uma preparação multissemanal prospectiva.”

**FR-202 — evento de prova**

> “Uma prova confirmada é representada como evento de competição com semântica própria, não como sessão comum, salvo decisão explícita em contrário. O evento pode existir em dia sem disponibilidade e não é trocável por sessão de treino. Sua inclusão não deve falhar nas validações de disponibilidade nem alterar silenciosamente a prescrição de hoje.”

**FR-205 — ritmo da meta**

> “Quando houver tempo e distância-alvo válidos, mostrar o ritmo matemático correspondente como meta declarada pelo atleta, sem tratá-lo como alvo recomendado, previsão ou garantia. A faixa, se houver, exige fórmula e regra de confiança aprovadas. Sem alvo válido, não exibir pace calculado; usar orientação compatível com calibração/RPE.”

**FR-206 — longão**

> **Remover do ciclo** até informar fração, limite e evidência. Preservar as regras atuais de longão enquanto isso.

**FR-207 — pós-prova**

> “A recuperação pós-prova só é ativada conforme estado de prova realizada explicitamente definido. Nenhuma janela fixa por distância é publicada como regra científica sem fonte; aplicar guardrails de prontidão/doença e mostrar o motivo. Cancelamento/não comparecimento não ativa recuperação de prova.”

**FR-301/303/304 — briefing**

> “O briefing não pode introduzir estrutura, ritmo, RPE ou ajuste que não estejam na prescrição/estrutura canônica. Se não houver estrutura de segmentos, descreve apenas duração e alvos existentes. Quando blocos forem exibidos, a soma é exatamente igual à duração total após arredondamento definido; se isso não for possível, o briefing não exibe blocos temporais.”

**FR-307/310 — notificação**

> “A notificação deve usar a prescrição diária vigente no momento de geração e ter comportamento de toque especificado: abrir Hoje ou abrir o briefing associado. Se a prescrição se tornar stale antes do toque, a tela revalida antes de apresentar ação.”

### Remover ou adiar

- **FR-401..404 / O-4 / F4:** remover do escopo e não alterar o guardrail geral de `AGENTS.md` por este ciclo.
- **FR-206:** remover até haver critério matemático e justificativa esportiva.
- **R-PLAN-10 (como regra composta):** separar “evento RACE”, “sem qualidade na semana de prova” e “recuperação pós-prova”; hoje une três assuntos com evidências diferentes.
- **`briefingEngineVersion` sem persistência:** remover se não for usada no resultado/cache; ou especificar claramente a finalidade e onde é registrada.

### Adicionar

- Requisito de comparação de fingerprint atual dentro da transação de aceite.
- Requisito de modo de compatibilidade para todos os `WorkoutType` consumidores e snapshots JSON.
- Requisito explícito sobre prova fora de disponibilidade, prova no dia de hoje, prova cancelada/não realizada e prova importada após o evento.
- Requisito para editar/remover meta expirada e para inserir distância customizada, ou limitar FR-208 a campos realmente editáveis.
- Requisitos e critérios para `NoChange`, upgrade limitado, edição repetida, stale após morte de processo e concorrência.
- Critério de auditoria histórica do briefing (ou remoção explícita de briefing retrospectivo).

## PARTE 8 — Sequência recomendada de implementação

**Não manteria a sequência atual.** Ela antecipa briefing antes do tipo `RACE` requerido pelo próprio FR-303, e agrupa trabalho de risco diferente em C3-01. Recomendo esta ordem:

| Ordem | Entrega revisada | Escopo | Gate que bloqueia a próxima |
|---|---|---|---|
| 0 | **Gate de produto/ciência** | Registrar decisão sobre D-1; fechar contrato do evento de prova, recuperação e limites do briefing; atualizar ADRs antes de código. F4 fica suspenso. | ADRs aprovados; regra sem fonte/decisão explícita não entra no catálogo. |
| 1 | **Proteção diária de iniciante (subconjunto C3-01)** | D-6 somente para `QUALITY`, com estado/status correto; não alterar sessão concluída, fechada ou execução. | Testes de regressão `QUALITY` em beginner, downgrade no dia, DONE/CLOSED e plano persistido intacto. |
| 2 | **Fingerprint e migração (parte de C3-01)** | Contrato versionado; migration 13→14; campos na leitura, `saveWeek`, `applyReplan`, snapshot/export/reset. | MigrationHelper de v13 preserva plano/sessões/revisões; schema 14; round-trip/export/reset; legado `NULL` sem revisão falsa. |
| 3 | **T4 no domínio + stale atômico (parte de C3-01)** | Causa combinada T1/T2/T4; estado `NoChange`; revalidação de perfil dentro da transação de aceite. | Testes de determinismo, alterações repetidas, recusas/supressão, processo reaberto, concorrência perfil×aceite e aceite stale. |
| 4 | **Fluxo ao salvar perfil (C3-02)** | Integrar onboarding/edição/calibração; resultado claro em Perfil e Hoje; copy dos três desfechos. | Testes de ViewModel/UI; mudança de calibração dispara apenas se campo relevante; validação com usuário 0 dos estados e cópia. |
| 5 | **Domínio da prova e planejamento 2A (C3-06 antecipado)** | Modelo de evento, `RACE`, taper por sessão, disponibilidade, hoje/passado/próximas semanas, constraints e consumidores. | Testes em todos os dias/limites/fusos; aceitação/replanejamento sem conflito; enum em Room/JSON/exports; análise de regressão de todas as operações. |
| 6 | **Briefing de domínio (C3-03 após o modelo)** | Só conteúdo que possa derivar de prescrição/modelo estável; sem F4; especificar histórico e versão. | Duração total coerente, nenhum alvo inventado, branches para EASY/QUALITY/LONG/REST/RACE e downgraded/uncalibrated. |
| 7 | **UI e notificação do briefing (C3-04)** | Sheet, deep link, estados, acessibilidade e visual. | Tema claro/escuro, tela estreita, fonte 200%, TalkBack com checklist, notificação stale e goldens. |
| 8 | **Pós-prova (C3-07)** | Só após estado de prova realizada/cancelada e política de recuperação aprovados. | Testes de atividade confirmada/ausente/manual/cancelada e passagem de semana; validação científica/copy. |

### Avaliação C3-01..C3-07 atuais

- **C3-01:** manter o objetivo, mas dividir proteção diária, persistência e T4; a composição atual é grande demais para o gate único apresentado.
- **C3-02:** manter depois de contrato e migration; deve cobrir todas as escritas do perfil, incluindo calibração.
- **C3-03:** mover para depois da definição de `RACE`; não criar template antecipado fictício.
- **C3-04:** manter após o modelo do briefing estar fechado; incluir deep link e fonte 200%/TalkBack manual verificável.
- **C3-05:** retirar do ciclo; reavaliar apenas após decisão registrada e revisão de segurança de conteúdo.
- **C3-06:** antecipar; é dependência do briefing com RACE e maior risco de motor/dados.
- **C3-07:** manter por último; bloquear até que o sistema saiba se a prova foi realizada e qual referência sustenta a janela.

## PARTE 9 — Plano de correção do PRD

Prioridades abaixo: **P0** bloqueia implementação do requisito; **P1** deve ser corrigido antes de iniciar a entrega correspondente; **P2** pode ser refinado em ciclo futuro.

| Prioridade | Alteração necessária | Seções afetadas | Justificativa |
|---|---|---|---|
| P0 | Decidir e registrar se F4 existe; recomendação é removê-lo deste ciclo e manter os guardrails atuais. | §2, §3 D-1, §4.4, §5 FR-401..404, §6, §9, §13, §14 | Escopo aprovado hoje exclui nutrição; evidência não sustenta as recomendações generalizadas e disclaimer não reduz risco. |
| P0 | Fechar regra de taper por data de sessão e revisar última sessão forte/semana da prova contra evidência real. | §4.2, FR-201/203, §6.1, §13, §14 | Regra atual escala semana e deixa QUALITY intacta; novo requisito não define alocação. |
| P0 | Definir se `RACE` é evento ou sessão e semântica de disponibilidade, prescrição, conclusão, falta, troca, carga e hoje/passado. | §4.2, FR-202/204/207/208, §7/8/10/11/13 | Replanejador valida disponibilidade; prescrição não possui tipo; hoje/passado são protegidos. |
| P0 | Especificar fingerprint versionado, allowlist, canonicalização, campos de calibração, snapshots e checkpoint de `NoChange`. | §4.1, FR-101..108, §7, §8, §11 | Fingerprint incompleto pode deixar proposta antiga válida e repetir sugestões ou quebrar provenance. |
| P0 | Tornar a validação do perfil atual parte do aceite transacional e adicionar teste concorrente. | §4.1, §10/11, §13 | Há janela concreta entre ler hash no app e transacionar plano. |
| P0 | Corrigir D-6 para especificar `QUALITY`, status DONE/CLOSED, efeito no mesmo dia e limite de longão. | §3 D-6, §4.1, FR-107/109, §6/11 | O engine não recebe status e o ADR 0026 deixa critérios de iniciante pendentes. |
| P0 | Reescrever briefing para não inventar segmentos/alvos; corrigir exemplo e soma de duração. | §4.3, FR-301..306, anexo A | Sessão não contém estrutura; exemplo é inconsistente e cria recomendações novas. |
| P1 | Especificar contrato de persistence/export/reset e preservar profile snapshot nos metadados/snapshots. | §7, §8, §11.2, §14 | Entidades e repositórios múltiplos gravam plano; JSON de revisão é outro contrato. |
| P1 | Corrigir FR-206 ou removê-lo até especificar fração de distância e evidência. | §4.2, FR-206, §6 | Hoje não há valor de fração nem regra implementável. |
| P1 | Corrigir FR-207 e `R-PLAN-10`: ligar à prova realizada, separar referências e marcar heurísticas. | §4.2, FR-207, §6, ADR 0030 | Taper evidence não sustenta automaticamente recuperação pós-prova; não há estado de conclusão do evento. |
| P1 | Reordenar entregas: `RACE` antes de briefing com Race; decidir F4 antes de implementação; separar gates de C3-01. | §13 | Sequência atual contém dependência fora de ordem e estimativas subdimensionadas. |
| P1 | Revisar copy e rotas: dia de prova dinâmico, notificação abre destino prometido, target distance editável ou requisito restrito. | §9/10; FR-204/208/307/310 | Copy assume sábado e o formulário/deep link não dão suporte às promessas atuais. |
| P1 | Incluir matriz de testes P0 da Parte 5 com resultados observáveis e gates por entrega. | §11/13 | “Determinismo”, “validação com usuário” e “TalkBack” sem cenários concretos não bloqueiam os riscos. |
| P2 | Limitar promessa de “acompanha o atleta” ao gerador rolling atual, ou abrir requisito futuro de calendário/plano multissemanal. | §1/2, O-2, §4.2, §9 | Não existe plano prospectivo de bloco; o usuário vê semana corrente. |
| P2 | Remover `briefingEngineVersion` se não persistido/consumido, ou definir exatamente como audita/reconstrói saída. | §7.2, FR-301 | Versão nominal não dá rastreabilidade histórica por si só. |

## PARTE 10 — Conclusão e decisão

1. **Maior risco do PRD atual:** introduzir um plano de prova aparentemente completo sem fechar o contrato de `RACE`, taper por sessão e pós-prova. O risco é entregar um evento que não pode ser aceito pelo replanejador em dia indisponível, não tem prescrição apropriada e pode ser confundido com um treino comum.
2. **Melhor decisão técnica do DeepSeek:** manter o replanejamento sugerido, com aceite explícito, e preservar hoje/passado/sessões concluídas; também acertou em derivar o briefing da `DailyPrescription` e não do plano bruto.
3. **Decisão mais questionável:** D-1/F4. Ela contradiz limites atuais de produto e trata “sem números individualizados” + rodapé como salvaguarda suficiente para hidratação, eletrólitos e alimentação. A segunda mais questionável é fixar janelas de pós-prova sem evidência específica nem confirmação de que a prova ocorreu.
4. **O que mudaria antes de escrever código:** remover F4 por ora; fechar ADR de prova com modelo de evento/estados/algoritmo de taper; formalizar fingerprint/baseline/concurrency; limitar D-6 e decidir histórico; reescrever briefing como conteúdo derivado real e coerente; reordenar entregas.
5. **Recomendação final:** **não aprovar o PRD para implementação como está.** Após correções P0, reauditar os requisitos de prova e perfil; liberar trabalho apenas por gates. A arquitetura-base é aproveitável e não exige novos módulos, mas a semântica esportiva e os contratos de dados ainda precisam ser fechados para que o app não prometa mais do que o gerador e a prescrição conseguem entregar.

### Execução e verificação do estado

- Inspeção estática dos documentos citados, ADRs 0023/0024/0026/0028, código dos motores, persistência, perfil, notificações, testes e schema 13.
- Fontes externas pesquisadas: Bosquet et al. (taper), Houmard et al. (taper 5K), NATA 2025 (hidratação), ACSM 2007, Thomas et al. 2016 (nutrição) e estudo de recuperação pós-maratona [Martínez-Navarro et al., PubMed](https://pubmed.ncbi.nlm.nih.gov/33251988/). Referências confirmam princípios gerais/contexto, não os parâmetros exatos propostos no PRD.
- **Comandos de teste/build:** nenhum. Não foi executado `./gradlew`.
- **Comando de inspeção Git antes da auditoria:** `git status --short --untracked-files=all` — mostrou apenas o PRD preexistente como não versionado.
- **Verificação final de Git:** `git status --short --untracked-files=all` mostrou apenas os dois arquivos não versionados já mencionados (PRD preexistente e este relatório); `git diff --name-only` não mostrou arquivos versionados alterados; `git diff --check` não reportou problemas.
