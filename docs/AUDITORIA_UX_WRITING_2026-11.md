# Auditoria de UX Writing — MeuCoach (27/11/2026)

**Escopo:** todo texto exibido ao usuário final — literais em Compose
(`app/src/main/kotlin`), copy gerada pelo motor determinístico
(`core/domain/src/main/kotlin`), `res/values/strings.xml` e notificações.
**Método:** extração exata com linha; avaliação por clareza, tradução de jargão,
tom de voz e concisão para mobile.
**Base:** `docs/GLOSSARY.md` (canônico), `docs/COMMUNICATION.md` §2 e §6,
`docs/DESIGN_SYSTEM.md` §15.
**Decisões:** `docs/adr/0071-content-vocabulary-and-voice.md` (ADR 0071).

> **Este arquivo é a lista de trabalho.** Cada linha é um item a corrigir na
> ordem da coluna `Fatia`. A coluna `Status` serve para marcar o andamento:
> `pendente` → `corrigido` → `verificado` (com teste).
>
> **Prioridade:** `[C]` crítico (impede o entendimento do treino ou da métrica) ·
> `[M]` melhoria (mais fluido, mais humano) · `[OK]` já está no padrão e não deve
> ser tocado.

**Resumo:** 42 linhas críticas, 80 de melhoria e 31 listadas como já no padrão.
As linhas são **grupadas por arquivo** — uma linha pode cobrir vários
arquivos/linhas do mesmo defeito. Três linhas críticas vieram do gate da
F1 (duas em F4, uma em F6) e estão marcadas como achado do gate.
**Nenhum arquivo de código foi alterado nesta auditoria.**

---

## Fatia F1 — Gate automatizado (sem texto)

| Item | Onde | Observação | Status |
|---|---|---|---|
| Ampliar o gate de goldens | `fixtures/golden/per-horizon/expected.json:92-95` | Hoje `forbiddenRationale: ["Fase PEAK", "Fase TAPER"]` só é aplicado ao cenário `noRace` (`PlanHorizonGoldenTest.kt:154-155`), onde a fase é `BASE`/`BUILD` — **o vazamento real (“Fase BASE.”) passa**. | verificado |
| Declarar `forbiddenVocabulary` nos goldens | `fixtures/golden/*/expected.json` | Checar contra `plan.rationale`, `session.title` e `session.blurb`. | verificado |
| Novo teste de catálogo | `core/domain/src/test/.../` | `ExplanationCatalog` (títulos de regra/evidência), `QualityGate` (`detail`/`unlockHint`), `SessionLibrary`, `SessionBriefingEngine`, `StoryVerdictCalculator`, `RacePace`. | verificado |
| Novo teste de UI | `app/src/test/.../` | `strings.xml`, `ReadinessLabels`, `ProfileLabels`, `CoachNextLabels`, `RacePredictionBlock`, `WeeklyReviewFormat`, labels do Coach. | verificado |
| Baseline explícito | `core/domain/src/test/resources/content-vocabulary-baseline.txt` (91 linhas), `app/src/test/resources/content-vocabulary-baseline.txt` (39 linhas) | Só encolhe; nunca cresce. Inclui “motor” (§2.4) na mesma fonte única. | verificado |

> **F1 concluída em 27/11/2026:** o gate achou 91 violações no domínio
> (incluindo todos os blocos dos 5 goldens de plano) e 39 na UI, todas
> baselinadas por fatia (F2: 32, F3: 19, F4: 18+2 novas, F5: 22, F6: 32+1
> nova, F7: 7). Três violações não estavam na lista original e ganharam linha
> própria em F4 (×2) e F6 (×1). Nenhum texto de usuário mudou.

---

## Fatia F2 — Motor: rationale do plano semanal

| Prio | Arquivo:linha | Texto atual | Sugestão | Status |
|---|---|---|---|---|
| [C] | `WeeklyPlanGenerator.kt:954` | `"Fase ${meso.phase}. "` | `"Etapa: $faseLabel. "` com mapper de `MesoPhase` | corrigido |
| [C] | `WeeklyPlanGenerator.kt:725-726` | `"Taper: 1 sessão curta de acelerações em ritmo fácil, 20–25 min, ..."` | `"Semana de prova: 1 sessão curta de acelerações em ritmo fácil, 20–25 min, ..."` | corrigido |
| [C] | `WeeklyPlanGenerator.kt:1959-1960` | `"Taper: −45–55% sobre a média das 3 semanas anteriores (...)"` | `"Semana de prova: volume 45 a 55% abaixo da média das 3 semanas anteriores (...)"` | corrigido |
| [C] | `WeeklyPlanGenerator.kt:1967-1968` | `"Taper sem histórico de volume: o corte percentual não é inventado; ..."` | `"Semana de prova sem histórico de volume: o app não chuteia o quanto cortar; ..."` | corrigido |
| [C] | `WeeklyPlanGenerator.kt:2110-2111` | `"Sem um dia de 20–25 min na janela do taper, ..."` | `"Sem um dia de 20–25 min na semana da prova, ..."` | corrigido |
| [C] | `WeeklyPlanGenerator.kt:1317` | `"Sem progressão: semana de prova/taper tem 1 estímulo; ..."` | `"Sem progressão: a semana da prova tem 1 estímulo; ..."` | corrigido |
| [C] | `WeeklyPlanGenerator.kt:1054-1055` | `"a primeira metade da base é só fácil, longão e strides; o fartlek leve entra na segunda metade."` | `"a primeira metade da base é só corrida leve, longão e Fartlek leve; o Fartlek entra na segunda metade."` | corrigido |
| [C] | `WeeklyPlanGenerator.kt:1368-1369` | `"Sem progressão: ACWR $value fora da faixa 0,8–1,3; ..."` | `"Sem progressão: a carga dos últimos 7 dias está fora da faixa segura (0,8 a 1,3); ..."` | corrigido |
| [C] | `WeeklyPlanGenerator.kt:1353-1354` | `"Sem progressão: a semana variou pouco (monotonia ≥2,0); ..."` | `"Sem progressão: a semana variou pouco (todos os dias com esforço parecido); ..."` | corrigido |
| [C] | `WeeklyPlanGenerator.kt:1363-1364` | `"Sem progressão: ACWR em calibração; ..."` | `"Sem progressão: a carga ainda está sendo medida; ..."` | corrigido |
| [C] | `WeeklyPlanGenerator.kt:956` | `"Sem histórico suficiente para mesociclo; usando posição legada. "` | `"Ainda não há histórico para posicionar esta semana; o app usa a sequência padrão."` | corrigido |
| [M] | `WeeklyPlanGenerator.kt:960` | `"Bloco ${macroIntent.blockIndex} de $totalBlocks. "` | `"Etapa ${macroIntent.blockIndex} de $totalBlocks para a prova."` | corrigido |
| [M] | `WeeklyPlanGenerator.kt:936-937` | `"Qualidade movida de segunda para ...: longão de domingo ..."` | `"O treino forte foi de segunda para ...: o longão de domingo ainda pesa na segunda."` | corrigido |
| [M] | `WeeklyPlanGenerator.kt:946-948` | `"Qualidade: sem dia seguro nesta semana porque o longão de domingo ..."` | `"Sem dia seguro para o treino forte nesta semana: o longão de domingo ainda pesa na segunda."` | corrigido |
| [M] | `WeeklyPlanGenerator.kt:1648-1649` | `"Longão em ritmo fácil. Piso de $floor min preservado; 25–30% do volume é um alvo, não um teto. Teto de ..."` | `"Longão em ritmo fácil: entre $floor e ${teto} min, mirando 25–30% do volume da semana."` | corrigido |
| [M] | `WeeklyPlanGenerator.kt:2013-2028` | `"Volume: base de $base min concluídos na semana de $week; ..."` | `"Na semana de $week você fez $base min. Esta semana: $minutes min."` | corrigido |
| [M] | `WeeklyPlanGenerator.kt:1026-1027` | `"Dias com menos de $MIN_EASY_MIN min (mínimo executável) ficam de fora: não inflamos a sessão para caber. "` | `"Dias com menos de $MIN_EASY_MIN min ficam de fora: o app não estica a sessão para caber."` | corrigido |
| [OK] | `WeeklyPlanGenerator.kt:1655`, `:1662`, `:1675`, `:1708`, `:1711`, `:1715`, `:1719`, `:1723` | `"Longão ..."`, `"... o piso para um longão ..."` | manter | — |
| [OK] | `WeeklyPlanGenerator.kt:2104` | `"Longão"` | manter | — |
| [M] | `WeeklyPlanGenerator.kt:2110` | `"na janela do taper"` | `"na semana da prova"` | corrigido |
| [M] | `WeeklyPlanGenerator.kt:2061-2085` | `" (semana anterior concluída)"`, `" (descarga)"`, `" (pico: volume estável)"` | rótulos de fase em português minúsculo | corrigido |
| [OK] | `WeeklyPlanGenerator.kt:1017-1018`, `:1033`, `:1038`, `:1088`, `:1106-1107`, `:1402-1403`, `:1411`, `:1417` | copy de meta, volume e progressão | manter | — |

---

## Fatia F3 — Motor: gate de qualidade e catálogo de regras

| Prio | Arquivo:linha | Texto atual | Sugestão | Status |
|---|---|---|---|---|
| [C] | `QualityGate.kt:284` | `"ACWR ${decimal(acwr)} > ${decimal(LoadSnapshot.ACWR_MAX)}"` | `"carga dos últimos 7 dias ${decimal(acwr)}×, acima de ${decimal(LoadSnapshot.ACWR_MAX)}×"` | corrigido |
| [C] | `QualityGate.kt:272` | `"strain semanal acima de 2× a média crônica"` | `"carga desta semana mais que o dobro da sua base"` | corrigido |
| [C] | `QualityGate.kt:260-261` | `"monotonia ${decimal(it)} ≥ ${decimal(LoadSnapshot.MONOTONY_MAX)}"` | `"semana muito repetitiva (variedade ${decimal(it)})"` | corrigido |
| [C] | `QualityGate.kt:303` | `"rampa de entrada: prova recente com VDOT, 1 qualidade curta sem progressão"` | `"primeira semana depois da prova: 1 treino forte curto, sem aumentar o volume"` | corrigido |
| [M] | `QualityGate.kt:97`, `:150` | `"prontidão vermelha (score N)"`, `"prontidão moderada (score N)"` | `"prontidão baixa (N/100)"`, `"prontidão moderada (N/100)"` | corrigido |
| [M] | `QualityGate.kt:113` | `"nível iniciante: o treino forte fica para depois da graduação"` | `"nível iniciante: o treino forte vem depois de você subir de nível"` | corrigido |
| [M] | `QualityGate.kt:124` | `"check-in subjetivo de hoje ainda não foi respondido"` | `"você ainda não respondeu o check-in de hoje"` | corrigido |
| [M] | `QualityGate.kt:173`, `:195` | `"semana de prova: sem qualidade principal"`, `"... apenas a dose pequena estrutural"` | `"semana de prova: sem o treino forte principal"`, `"... só a dose pequena de sempre"` | corrigido |
| [M] | `QualityGate.kt:232` | `"carga objetiva insuficiente: N de 28 dias"` | `"faltam dados de carga: N de 28 dias com FC ou ritmo"` | corrigido |
| [OK] | `QualityGate.kt:102`, `:104`, `:116`, `:127`, `:138`, `:153`, `:165`, `:176`, `:187`, `:198`, `:223`, `:264`, `:275`, `:287`, `:343` | 15 `unlockHint` em 2ª pessoa, verbo de ação, zero jargão | manter (padrão a replicar) | — |

### Catálogo de explicações (`ExplanationCatalog.kt`)

| Prio | Linha | Texto atual | Sugestão | Status |
|---|---|---|---|---|
| [C] | `:41` | `"Prova ou teste recente define VDOT e LTHR"` | `"Prova ou teste recente define seu índice de aptidão e sua FC no limite"` | corrigido |
| [C] | `:79` | `"Previsão de tempo por VDOT, com faixa e checagem de Riegel"` | `"Previsão de tempo a partir da sua forma recente, com faixa de segurança"` | corrigido |
| [C] | `:107` | `"Catálogo de qualidade por fase, nível e distância, com PEAK específico"` | `"Escolha do estímulo por etapa do treino, nível e distância da prova"` | corrigido |
| [C] | `:32` | `"Longão é a sessão mais longa; piso 45/60 e alvo de 25–30%"` | `"O longão é a sessão mais longa da semana; 45 a 60 min, mirando 25–30% do volume"` | corrigido |
| [M] | `:87`, `:91` | `"Graduação para intermediário por consistência demonstrada, com aceite"` | `"Você já tem consistência para subir para intermediário. Você aceita?"` | corrigido |
| [M] | `:54` | `"Volume cresce no máximo 6% e só com semana concluída e guardrails verdes"` | `"O volume cresce no máximo 6%, e só depois de uma semana cumprida com todas as travas liberadas"` | corrigido |
| [M] | `:103` | `"Oportunidade de qualidade: o plano propõe e o dia confirma"` | `"Oportunidade de treino forte: a semana propõe e o dia confirma"` | corrigido |
| [M] | `:111` | `"Rampa de entrada: prova recente libera 1 qualidade curta, sem progressão"` | `"Entrada: com prova recente, o app libera 1 treino forte curto, sem aumentar o volume"` | corrigido |
| [M] | `:67` | `"Uma decisão por dia, a mesma em todas as telas"` | `"Cada dia tem uma decisão só, igual em todas as telas"` | corrigido |
| [M] | `:26` | `"Carga subindo rápido é alerta, não meta"` | `"Carga subindo rápido é um sinal de alerta, não uma meta de treino"` | corrigido |
| [M] | `:15` | `"Dado ausente não vira zero"` | `"Falta de dado nunca vira zero"` | corrigido |
| [M] | `:119` | `"A semana é lida como fato, com dado ausente como ausente"` | `"A semana é um retrato do que aconteceu; o que falta de dado aparece como falta"` | corrigido |
| [M] | `:56` | `"Mudança de perfil gera revisão sugerida, nunca automática"` | `"Mudar o perfil gera uma revisão sugerida — nunca aplicada sozinha"` | corrigido |
| [M] | `:24` | `"Sem check-in subjetivo o treino forte não é liberado"` | `"Sem o check-in de hoje, o treino forte não é liberado"` | corrigido |
| [OK] | `:14`, `:18`, `:20`, `:21`, `:31`, `:33`–`:38`, `:45`, `:49`, `:51`, `:63`, `:65`, `:66`, `:68`, `:71`, `:75`, `:83`, `:95`, `:99`, `:115`, `:126`–`:217` | títulos restantes e fontes | manter | — |

---

## Fatia F4 — Motor: títulos, blurbs, briefing e estrutura

| Prio | Arquivo:linha | Texto atual | Sugestão | Status |
|---|---|---|---|---|
| [C] | `SessionBriefingEngine.kt:91` | `"Use o pace do bloco: fique entre X e Y por km"` | `"Siga o ritmo da etapa: entre X e Y por km"` | corrigido |
| [C] | `SessionBriefingEngine.kt:119` | `"; o pace é referência (entre X e Y por km)."` | `"; o ritmo é só referência (entre X e Y por km)."` | corrigido |
| [C] | `SessionBriefingSheet.kt:258` + `PrescriptionTargets.kt:167,213,243` | `label = "Zona"` com valor `"Z2"`/`"Z3–Z4"`/`"Z5"` | `"Intensidade"` + `"Fácil (Z2)"` / `"Forte (Z4–Z5)"` / `"Moderado a forte (Z3–Z4)"` | corrigido |
| [M] | `SessionLibrary.kt:430` | `"Variação por esforço percebido, sem pace fixo."` | `"Variação de ritmo, guiado pelo quanto você se sente cansado — sem ritmo marcado."` | corrigido |
| [OK] | `SessionLibrary.kt:485` | `title = "Pace de prova ${distance.raceLabel()}"` | manter — título aprovado em 21/11/2026 | — |
| [M] | `SessionLibrary.kt:488-489` | `"Blocos no pace atual da prova (...), não na meta; a previsão manda."` | `"Blocos no ritmo que você corre hoje, não na meta: a previsão do app manda."` | corrigido |
| [M] | `SessionLibrary.kt:516` | `"Repetições curtas para a especificidade de 5K."` | `"Repetições curtas, no estilo dos 5 km."` | corrigido |
| [M] | `SessionLibrary.kt:285` | `"Estímulo neuromuscular curto no fim de um fácil; não conta como treino forte."` | `"Acelerações curtas no fim da corrida leve — não é o treino forte da semana."` | corrigido |
| [M] | `SessionLibrary.kt:297-298` | `"Surtos curtos e controlados no fácil, após 4 semanas consistentes; não conta como qualidade."` | `"Surtos curtos e controlados no fim da corrida leve, depois de 4 semanas consistentes."` | corrigido |
| [M] | `SessionLibrary.kt:352` | `"Dose baixa de variação na segunda metade da base; não é treino forte."` | `"Variação leve no meio do treino, na segunda metade da fase de base. Não é o treino forte."` | corrigido |
| [M] | `SessionLibrary.kt:267` | `title = "Rodagem"` | manter “Rodagem” **com** descrição visível; “Corrida leve” em lista compacta | corrigido |
| [M] | `SessionLibrary.kt:387`, `:413`, `:527`, `:309`, `:338`, `:441`, `:470` | `target = "T"`, `"I"`, `"RPE 7"`, `"RPE 8"` | garantir que nenhum caminho de UI renderize o código cru (hoje só `steps()` traduz) | corrigido |
| [M] | `WorkoutStructure.kt:218-221` | `"pace atual de 5K"` etc. | `"ritmo atual de 5 km"` etc. | corrigido |
| [M] | `SessionBriefingSheet.kt:270`, `:272`, `:277` | `"Principal: FC e esforço; o pace é referência."` | `"Principal: FC e esforço; o ritmo é referência."` | corrigido |
| [C] | `WorkoutRecommender.kt:145` | `"Faça o check-in para confirmar a qualidade de hoje."` (achado do gate F1; não estava na lista) | `"Faça o check-in para confirmar o treino forte de hoje."` | corrigido |
| [C] | `fixtures/golden/plan-upgrade/input.json:18,25,33` (títulos preservados em `expected.json`) | `"Fácil de segunda"`, `"Fácil de terça"`, `"Fácil de hoje"` (achado do gate F1; título legado preservado pelo `PlanUpgrade`) | `"Rodagem ..."` pela regra de retitulação da F4, ou regenerar o fixture | corrigido |
| [OK] | `WorkoutStructure.kt:172`, `:176`, `:180`, `:191`, `:198`, `:200`, `:203`, `:215` | passo a passo do treino | manter | — |
| [OK] | `SessionBriefingEngine.kt:47`, `:51`, `:59`, `:63`, `:81`, `:83`, `:107`, `:109`, `:114`, `:123`, `:125`, `:129-134` | orientação e sinais de alerta | manter | — |

---

## Fatia F5 — Motor: prova, level e story

| Prio | Arquivo:linha | Texto atual | Sugestão | Status |
|---|---|---|---|---|
| [C] | `StoryVerdictCalculator.kt:187-190` | `"Forte demais hoje"`, `"Longão forte demais"`, `"Qualidade acima do alvo"` | `"Ritmo acima do planejado"`, `"Longão mais forte que o planejado"`, `"Treino forte acima do alvo"` | corrigido |
| [C] | `StoryVerdictCalculator.kt:194-197` | `"Leve demais hoje"`, `"Longão leve demais"`, `"Qualidade abaixo do alvo"` | `"Ritmo abaixo do planejado"`, `"Longão mais leve que o planejado"` | corrigido |
| [C] | `RacePace.kt:161` | `"O pace do PEAK usa a previsão atual de ${distance.label()} ($pace), nunca a meta."` | `"O ritmo do treino forte usa a sua previsão atual de ${distance.label()} ($pace), nunca a meta."` | corrigido |
| [C] | `RacePace.kt:195-196` | `"Sem previsão recente de ...: o PEAK usa o esforço $R de 10; o app não prescreve um pace não demonstrado."` | `"... o treino forte vai pelo esforço $R de 10, sem inventar ritmo."` | corrigido |
| [C] | `RacePace.kt:191` | `"sem VDOT calibrado"` | `"sem índice de aptidão calibrado"` | corrigido |
| [C] | `LevelGraduationEngine.kt:196`, `:330` | `"CTL estável ou crescente nas últimas 4 semanas com carga objetiva"` | `"Carga das últimas 4 semanas estável ou subindo, com dados de FC ou ritmo"` | corrigido |
| [C] | `LevelGraduationEngine.kt:199`, `:336` | `"Monotonia média das últimas 4 semanas abaixo de 2,0"` | `"Variedade média das últimas 4 semanas abaixo de 2,0 (dias com esforço muito parecido)"` | corrigido |
| [C] | `LevelGraduationEngine.kt:297-298`, `:322` | `"Calibração válida: Tier A de ..."`, `"Tier B confirmada"` | `"Calibração por prova recente de ..."`, `"Calibração por treinos recentes"` | corrigido |
| [C] | `LevelGraduationEngine.kt:334` | `"prova ou TT recente (até N dias) ou TT de 30 min válido"` | `"prova ou teste de 30 min recente (até N dias)"` | corrigido |
| [C] | `InitialExperienceSuggester.kt:21-22` | `"Com prova ou estimativa recente com VDOT, considere Intermediário para liberar 1 treino forte curto por semana."` | `"Com prova ou estimativa recente, você já pode para intermediário e ganhar 1 treino forte curto por semana."` | corrigido |
| [M] | `StoryVerdictCalculator.kt:180`, `:182`, `:189`, `:196` | `"Rodagem bem feito"` (concordância), `"Qualidade completa"`, `"Qualidade acima do alvo"`, `"Qualidade abaixo do alvo"` | `"Corrida leve bem feita"`, `"Treino forte completo"`, `"Treino forte acima do alvo"`, `"Treino forte abaixo do alvo"` | corrigido |
| [M] | `StoryVerdictCalculator.kt:207-212` | `"longão de $it min"`, `"qualidade de $it min"`, `"$it min leve"` | `"longão de $it min"` (manter), `"treino forte de $it min"`, `"corrida leve de $it min"` | corrigido |
| [M] | `RacePace.kt:168-185` | `"ela é ambiciosa"` / `"ela é referência"` | ok; trocar "pace" por "ritmo" nos textos fixos | corrigido |
| [M] | `LevelGraduationEngine.kt:179-183` | `"...concluídos com evidência forte"` | `"...confirmados por uma corrida"` | corrigido |
| [OK] | `LevelGraduationEngine.kt:183`, `:328` | `"Longão real de pelo menos 60 minutos concluído"` | manter | — |
| [M] | `CalibrationCalculator.kt:262` | `"...e seu ritmo de limiar."` | `"...e o ritmo que você segura nesse esforço."` | corrigido |
| [OK] | `StoryHighlightsCalculator.kt:31-70`, `StoryMetrics.kt:110-121` | headlines e rótulos de arte | manter | — |
| [OK] | `ActivityInsightsCalculator.kt:72-105` | as três leituras pós-corrida | manter | — |
| [OK] | `CalibrationCalculator.kt:131-132`, `:206-207`, `:248-249` | recomendações de calibração | manter | — |

---

## Fatia F6 — UI: Coach (voz e rótulos)

> **O registro coloquial é intencional** (decisão do usuário 0 em 27/11/2026,
> já prevista em 21/11/2026): o treinador usa um pouco de gíria para se
> aproximar do dia a dia, e **o produto inteiro fala do mesmo jeito** — sem
> camada de tradução entre telas e Coach. Não se corrige gíria. Corrigem-se
> só os defeitos: frase sem sujeito, nome interno “motor” em tela e duas
> saudações para a mesma tela.

| Prio | Arquivo:linha | Texto atual | Sugestão | Status |
|---|---|---|---|---|
| [C] | `CoachFallbackLabels.kt:30`, `:33`, `:67`, `:70` | `"...só o motor prescreve."` — frase **sem sujeito** e com nome interno | `"...— ritmo é o app que prescreve. Me pergunta de novo."` | corrigido |
| [M] | `CoachFallbackLabels.kt:12`, `:27`, `:39`, `:54` | `"...direto do motor"`, `"...só com o que tá no motor"`, `"...não conferi no motor"` | `"...direto do app"`, `"...só com o que tá no app"`, `"...não conferi no app"` — **a gíria fica**; só “motor” sai | corrigido |
| [M] | `CoachOpeningLabels.kt:28`, `:42`, `:45` + `CoachScreen.kt:568` | `"Bora ver o que importa?"` / `"Oi, bora ver o que importa?"` — **duas saudações para a mesma tela** | unificar em `"Oi! Bora ver o que importa?"` (a gíria fica) | corrigido |
| [OK] | `CoachViewModel.kt:1209` | `"Boa! Marca lá no Hoje pra entrar na conta — por aqui eu sigo o plano."` | manter — registro do Coach | — |
| [OK] | `CoachViewModel.kt:1214`, `:1215` | `"...O plano segue o mesmo — tenta de novo."` | manter — registro do Coach | — |
| [M] | `CoachScreen.kt:197` | `"motor no comando"` | `"tudo certo"` | corrigido |
| [M] | `CoachViewModel.kt:366`, `:373` | `"Mostrando o resumo do motor."` | `"Mostrando o resumo do seu plano."` | corrigido |
| [M] | `CoachHistoryCard.kt:75` | `"...continuam vindo do motor."` | `"...continuam vindo do app."` | corrigido |
| [M] | `CoachNextCard.kt:53` | `"Consultando o motor…"` | `"Consultando seu plano…"` | corrigido |
| [M] | `CoachDoneLabels.kt:20` | `"Boa! ${done.title} feito."` (concordância) | `"Boa! Treino concluído."` | corrigido |
| [M] | `CoachReviewCard.kt:144` | `"Volume: $planned planejados · $executed feitos"` (concordância) | `"Volume: ${planned} min planejados · ${executed} min feitos"` | corrigido |
| [M] | `CoachNextLabels.kt:138`, `:141`, `:142`, `:143`, `:145`, `:146`, `:147` | `"A qualidade volta..."` | `"O treino forte volta..."` | corrigido |
| [M] | `CoachNextLabels.kt:145` | `"...quando a base de carga fechar a calibração."` | `"...quando você tiver mais semanas com carga registrada."` | corrigido |
| [M] | `CoachNextLabels.kt:107` | `"Prontidão $level · N/100"` | `"Prontidão alta · N/100 — sono curto."` | corrigido |
| [C] | `CoachNextLabels.kt:158` | `"Fácil conservador."` (achado do gate F1; `Fácil` como nome de sessão, `trainingLine` com `NO_PLAN`) | `"Leve conservador."` | corrigido |
| [M] | `CoachProgressLabels.kt:71` | `"Sem VDOT recente — correndo por RPE."` | `"Sem índice de aptidão recente — você corre pelo esforço percebido."` | corrigido |
| [M] | `CoachProgressLabels.kt:82` | `"VDOT $value ($origin em $data)."` | `"Índice de aptidão $value ($origin em $data)."` | corrigido |
| [M] | `CoachProgressLabels.kt:42` | `"$easy% fácil nos últimos 28 dias (alvo ~80%)."` | `"$easy% fácil nos últimos 28 dias. O ideal é 80%."` | corrigido |
| [M] | `CoachProgressLabels.kt:48`, `:50` | `"sem dia em vermelho"`, `"N dias em vermelho"` | `"nenhum dia com prontidão baixa"`, `"N dias com prontidão baixa"` | corrigido |
| [OK] | `CoachViewModel.kt:1200`, `:1206`, `:1222`, `:1225`, `:1209`, `:1214`, `:1215` | escopo, menu de capacidades, descanso, "pra", "tenta de novo" | manter — **a gíria do Coach é intencional** (decisão do usuário 0) | — |
| [OK] | `CoachChatComponents.kt:451-453`, `:457`, `:459`, `:493` | explicação da IA | manter | — |
| [OK] | `CoachIntentSheets.kt:75`, `:155`, `:219` | escopo do ajuste | manter | — |
| [OK] | `CoachHistoryCard.kt:75`, `:113`, `:191` | histórico e deleção | manter | — |
| [OK] | `CoachProgressCard.kt:77`, `CoachGuardsCard.kt:35`, `:66` | estados vazios | manter | — |

---

## Fatia F7 — UI: Tendências, Perfil, Previsão, Atividades, Level

| Prio | Arquivo:linha | Texto atual | Sugestão | Status |
|---|---|---|---|---|
| [C] | `LevelGraduationViewModel.kt:260` | `"CTL estável ou crescente nas últimas 4 semanas"` | `"Carga das últimas 4 semanas estável ou subindo"` | corrigido |
| [C] | `LevelGraduationViewModel.kt:266` | `"Monotonia abaixo de 2,0 nas últimas 4 semanas"` | `"Variedade acima de 2,0 nas últimas 4 semanas"` | corrigido |
| [C] | `LevelGraduationViewModel.kt:256` | `"Prontidão atual fora do vermelho"` | `"Prontidão atual não está baixa"` | corrigido |
| [C] | `RacePredictionBlock.kt:117` | `"O pace do plano usa a previsão, não a meta."` | `"O ritmo do plano usa a previsão, não a meta."` | corrigido |
| [C] | `RacePredictionBlock.kt:136` | `"A faixa também considera a checagem de Riegel."` | `"A faixa também considera a sua forma recente, não só a meta."` | corrigido |
| [C] | `RaceEventCard.kt:370` | `"...não transforma isso em pace, faixa ou previsão."` | `"...em ritmo, faixa ou previsão."` | corrigido |
| [C] | `RaceEventCard.kt:396` | `"Use horas, minutos (0–59) e segundos (0–59), ou deixe vazio."` | `"Use horas, minutos e segundos (0 a 59), ou deixe vazio."` | corrigido |
| [C] | `LevelGraduationCard.kt:205`, `:214` | `"${rule.id} · ${rule.title}"`, `"${evidence.id} · $label"` | título legível + “Ver fontes e detalhes técnicos” colapsado | corrigido |
| [M] | `LevelGraduationViewModel.kt:248`, `:264` | `"...com evidência"`, `"Prova recente com evidência"` | `"...confirmados por uma corrida"`, `"Prova recente confirmada por uma corrida"` | corrigido |
| [M] | `LevelGraduationViewModel.kt:252` | `"Calibração de limiar válida"` | `"Calibração recente válida"` | corrigido |
| [OK] | `LevelGraduationViewModel.kt:230`, `:234` | `"...um longão real de 60 min..."` | manter | — |
| [M] | `RacePredictionBlock.kt:171`, `:172`, `:173` | `"Sem origem de calibração confiável."` | `"Sem calibração recente."` | corrigido |
| [M] | `RacePredictionBlock.kt:158`–`:165` | `"meta alinhada"` | `"meta dentro da sua previsão atual"` | corrigido |
| [M] | `TodayScreen.kt:1015` | `"Aumento ${x}× vs. 4 sem"` | `"Aumento 1,12× · últimos 7 d vs. 4 semanas"` | corrigido |
| [M] | `TodayScreen.kt:1035-1051` | `"42d"`, `"7d"`, `"base − fadiga"` | `"42 dias"`, `"7 dias"`, `"recuperação (base − fadiga)"` | corrigido |
| [M] | `TodayScreen.kt:773-775` | `"ZONA"` + `"esforço"` | `"INTENSIDADE"` + `"Fácil"` | corrigido |
| [OK] | `TodayExplanation.kt:152` | `WorkoutType.LONG -> "longão"` | manter | — |
| [M] | `TodayExplanation.kt:300-302` | `"A prontidão fica em 50 (moderada)..."` (valor neutro) | `"Ainda não há dados de recuperação, então a prontidão não foi medida."` | corrigido |
| [M] | `TodayExplanation.kt:337-338` | `"FC de repouso $delta bpm acima da média"` (sinal duplo) | `"FC de repouso ${abs(delta)} bpm acima/abaixo da sua média"` | corrigido |
| [M] | `TodayExplanation.kt:96` | `"Esforço percebido (RPE)"` | `"Esforço percebido (1 a 10)"` | corrigido |
| [M] | `TodayExplanation.kt:226-237` | parágrafo de 45 palavras | quebrar em dois blocos | corrigido |
| [M] | `TodayScreen.kt:1545`, `:897-907` | muros de 32 palavras | reduzir | corrigido |
| [M] | `TrendsScreen.kt:604-623`, `:631` | `"com evidência"`, `"não liberam progressão"` | `"confirmadas por corrida"`, `"não contam para aumentar o volume"` | corrigido |
| [M] | `WeeklyReviewFormat.kt:54`, `:58`, `:63` | `"Qualidade: N planejada"`, `"N rebaixada(s)"` | `"Treino forte: N planejado(s)"`, `"N ajustado(s) para fácil"` | corrigido |
| [OK] | `WeeklyReviewCard.kt:172-174` | `"Longão"` | manter | — |
| [M] | `ProfileScreen.kt:321`, `:323`, `:324` | `"Meta"`, `"Data"`, `"Tempo alvo"` | `"Meta de tempo"`, `"Data da prova"`, `"Tempo que você quer completar"` | corrigido |
| [M] | `ProfileScreen.kt:494` | `"Rever o tour de boas-vindas"` | `"Rever o tutorial de boas-vindas"` | corrigido |
| [M] | `ProfileScreen.kt:432` | `"...com o que o coach usa..."` | `"...com o que o Coach usa..."` | corrigido |
| [M] | `ProfileScreen.kt:267` | `"Dados usados para individualizar zonas, carga e metas."` | `"Esses dados ajustam suas zonas, sua carga e suas metas."` | corrigido |
| [M] | `ProfileLabels.kt:70` | `"$days · $duration/semana"` | `"$days · $duration por semana"` | corrigido |
| [M] | `ActivitiesScreen.kt:111-113` | `"...ver zonas, splits, o porquê..."` | `"...ver zonas, trechos por km, o porquê..."` | corrigido |
| [M] | `ActivitiesScreen.kt:142`, `:145` + `MeuCoachApp.kt:71` | `"ATIVIDADES"` / `"Suas corridas"` / aba `"Corridas"` | unificar em `"Corridas"` | corrigido |
| [M] | `ActivitiesScreen.kt:229` | `"Cadência $it spm"` | `"Cadência $it passos/min"` | corrigido |
| [M] | `ActivityDetailScreen.kt:740-742` | `"O mapa baixa tiles do OpenStreetMap via OpenFreeMap"` | `"O mapa é baixado de uma fonte pública de mapas"` | corrigido |
| [M] | `StoryStudioSheet.kt:472` | `"Relevo do terreno via Mapzen"` | `"Relevo do terreno: é baixado na primeira vez."` | corrigido |
| [M] | `OnboardingScreen.kt:369-370` | `"O nível muda os estímulos a partir da fase BUILD."` | `"...a partir da fase de construção."` | corrigido |
| [M] | `OnboardingForm.kt:199` | `"Minutos por dia entre 1 e 1.440."` | `"Minutos por dia entre 1 e 1.440 (24 h)."` | corrigido |
| [M] | `TutorialScreen.kt:565` | `"O CICLO"` | `"SEU DIA A DIA"` | corrigido |
| [M] | `TutorialScreen.kt:573` | `"Importa FIT, TCX ou GPX"` | `"Você importa FIT, TCX ou GPX"` | corrigido |
| [M] | `strings.xml:44-45` | `"Sono, dor e energia registrados."` | `"Sono, fadiga, dor e estresse registrados."` | corrigido |
| [M] | `strings.xml:59` | `"Sono, FC de repouso, HRV e SpO₂"` | `"Sono, FC de repouso, variabilidade cardíaca (HRV) e oxigenação (SpO₂)"` | corrigido |
| [OK] | `TrendsScreen.kt:375`, `:383`, `:388`, `:392`, `:398`, `:402`, `:408`, `:413`, `:222`, `:281`, `:806`, `:1005`, `:1065`, `:857-863`, `:1335-1339`, `:144-145` | seção "Como ler a carga" | manter (padrão de referência) | — |
| [OK] | `CheckInScreen.kt:139`, `:150-180`, `:194`, `:200`, `:228`, `:234`, `:265` | check-in | manter | — |
| [OK] | `CalibrationScreen.kt` (tela inteira) | calibração | manter | — |
| [OK] | `HealthScreen.kt:388-409`, `:475`; `HealthMetricsContent.kt:509`, `:535`; `HealthConnectCopy.kt` | Health Connect | manter | — |
| [OK] | `ProfileScreen.kt:344-345`, `:388-389`, `:469-476`, `:380` | perfil | manter | — |
| [OK] | `WeeklyReviewFormat.kt:109`, `:120-140`; `WeeklyReviewCard.kt:115` | revisão da semana | manter | — |
| [OK] | `ActivityZones.kt:106-110`, `:44`; `ActivityComparisonFormat.kt:80-101` | zonas e comparação | manter | — |
| [OK] | `OnboardingScreen.kt:258`, `:259`, `:426-427`, `:468`, `:482-483`, `:551`, `:556`; `OnboardingForm.kt:147-245` | onboarding | manter | — |
| [OK] | `strings.xml:20-24`, `:44`, `:72`, `:75`, `:79` | tutorial | manter | — |
| [OK] | `DataManagementScreen.kt:131-132`; `ReminderContent.kt`; `StoryNotifier.kt` | dados e notificações | manter | — |

---

## Fatia F8 — UI: bugs literais e muros de texto

| Prio | Arquivo:linha | Texto atual | Sugestão | Status |
|---|---|---|---|---|
| [C] | `DataManagementScreen.kt:357` | `"Expira em ${item.daysRemaining} dia(s)."` | `"Expira em N dia."` / `"Expira em N dias."` | corrigido |
| [C] | `TodayViewModel.kt:1062` + `CheckInScreen.kt:338`, `:343` | `"${it.hooperSum}/20"` (sem rótulo no Hoje; sem sentido no check-in) | `"Recuperação 12/20 — menor é melhor"` | corrigido |
| [M] | `DataManagementScreen.kt:216-220` | 47 palavras | ~30 palavras + Health Connect em linha separada | corrigido |
| [M] | `DataManagementScreen.kt:147-149` | `"o blob comprimido não sai do app"` | `"os dados brutos de cada corrida não saem do app"` | corrigido |
| [M] | `DataManagementScreen.kt:260-261` | `"Sem nuvem, sem conta e sem analytics."` | `"... sem rastreamento de uso."` | corrigido |
| [M] | `HealthMetricsContent.kt:98-101` | 35 palavras + 2 siglas | quebrar em duas frases | corrigido |
| [M] | `TodayViewModel.kt:1067` | `"menor é melhor"` solto | `"Recuperação: 12/20 — menor é melhor"` | corrigido |
| [M] | `CheckInScreen.kt:311` | `"Dor ${entry.soreness}"` | `"Dor muscular ${entry.soreness}"` | corrigido |
| [M] | `HealthScreen.kt:286` | `"Linha ${error.line}: ${error.message}"` | garantir mensagem do parser em pt-BR | corrigido |

---

## Fatia F9 — Fechar o gate

| Item | Onde | Observação |
|---|---|---|
| Remover o baseline de jargão | `core/domain/src/test/...`, `app/src/test/...` | lista negra cheia ativa; `check` verde |
| Atualizar goldens | `fixtures/golden/**` | títulos e `forbidden*` conforme o vocabulário canônico |
| Atualizar testes de string literal | `CalibrationScreenTest`, `LevelGraduationCardTest`, `WeeklyReviewFormatTest`, snapshots do Hoje e do briefing | junto com a fatia correspondente |

---

## Dependências fora do texto (levantadas na auditoria)

| Item | Onde | Impacto na correção |
|---|---|---|
| Títulos persistidos em goldens | `fixtures/golden/per-horizon/expected.json:19,28,36` (`Intervalado`, `Rodagem`, `Longão`) | trocar o vocabulário exige **re-gravar** os goldens esperados — **`Longão` permanece**, então esse campo não muda |
| `forbiddenTitles` existentes | `per-horizon:47-51`, `per-opportunity:24`, `per-quality:62`, `plan-upgrade:15` | são listas **por cenário** (o título não pode aparecer naquele cenário), não uma lista negra global. Não confundir com a lista negra do ADR 0071. |
| Texto persistido em Room | `DailyPrescriptionEntity.kt:23,26,30,46,48`; `WeeklyPlanEntity.kt:21` | `rationale`, `plannedTitle`, `title`, `decisionDetail`, `unlockHint` — política ADR 0071 §2.7: histórico mantém texto antigo, sem migração |
| Versão do motor | `WeeklyPlanGenerator.ENGINE_VERSION`, `DailyPrescription.engineVersion` | **mudam** na fatia F2 para distinguir texto novo de antigo |
| Schema Room | `MeuCoachDatabase.VERSION = 23` | **não muda** — nenhuma migração é necessária |
| Testes com string literal | `app/src/test/.../CalibrationScreenTest.kt:68,97,149` (`CALIBRAÇÃO POR PROVA RECENTE`, `BASE INICIAL`, `ESTIMATIVA PELOS SEUS TREINOS`) | a forma canônica do `CalibrationTier` já está correta e **não muda** |
