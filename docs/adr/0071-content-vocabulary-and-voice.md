# ADR 0071 — Vocabulário, voz e estados padronizados no texto de usuário

**Status:** Aceito — implementação em fatias a partir de 27/11/2026.
**Data:** 2026-11-27
**Escopo:** todo texto exibido ao usuário final (UI em `app`, copy gerada pelo motor
em `core:domain`, `res/values/strings.xml`, notificações) e o gate automatizado que
o mantém.
**Relacionados:** ADR 0004 (motor determinístico), ADR 0023 (plano × prescrição),
ADR 0028 (replanejamento versionado), ADR 0033 (planejado × executado),
ADR 0034 (revisão semanal), ADR 0041 (mesociclo), ADR 0044 (autoridade do dia),
ADR 0047 (catálogo de qualidade), ADR 0066 (taper por média de 3 semanas),
ADR 0070 (janela de planejamento). Documentos: `docs/GLOSSARY.md` (canônico),
`docs/COMMUNICATION.md` (histórico de copy), `docs/DESIGN_SYSTEM.md` §15,
`docs/AUDITORIA_UX_WRITING_2026-11.md` (inventário).

---

## 1. Contexto

A varredura de 27/11/2026 (`docs/AUDITORIA_UX_WRITING_2026-11.md`) percorreu todo o
texto visível do app e encontrou 39 linhas críticas e 80 de melhoria, agrupadas
por arquivo. A causa não é
falta de cuidado na camada de apresentação: `COMMUNICATION.md` existe desde
03/11/2026, o glossário está mapeado e os IDs `R-`/`EV-` já saíram das telas
comuns. **O jargão volta por baixo da camada que foi limpa**, porque quem produz
o texto final do dia é o motor, não a UI.

Sete problemas concretos:

1. **Enum e sigla crus vazam em texto corrido.** `WeeklyPlanGenerator.kt:954`
   emite `"Fase ${meso.phase}."`; o usuário lê literalmente “Fase TAPER.” ou
   “Fase PEAK.”. `VDOT`, `LTHR`, `ACWR`, `CTL`, `Tier A/B`, `TT`, `monotonia`,
   `strain` e `Riegel` vazam em `ExplanationCatalog`, `QualityGate`, `RacePace`,
   `LevelGraduationEngine`, `CoachProgressLabels` e `RacePredictionBlock`.

2. **Três vocabulários para o mesmo conceito.** O mesmo treino é “Rodagem” no
   card (`SessionLibrary.kt:267`), “Corrida mais longa” no rationale
   (`WeeklyPlanGenerator.kt:1655`) e “longão” na revisão
   (`WeeklyReviewCard.kt:172`). “Longão” é o rótulo aprovado; o que falha é
   haver três grafias para ele — e o mesmo se repete em “qualidade” × “treino
   forte” e em “pace” × “ritmo”. `COMMUNICATION.md` §3.1 promete “vocabulário
   único no produto” e não é cumprido.

3. **“Taper” aparece em cinco pontos** (`WeeklyPlanGenerator.kt:725`, `:1317`,
   `:1959`, `:1967`, `:2110`), violando a proibição explícita de
   `COMMUNICATION.md` §6.3 (“Nunca usar ‘taper’ na UI”).

4. **O motor julga o usuário.** `StoryVerdictCalculator.kt:187-197` emite
   “Forte demais hoje”, “Leve demais hoje”, “Longão forte demais” — proibido por
   `COMMUNICATION.md` §6.12. Esse texto é o que sai do app e vai para o feed
   público.

5. **Três nomes para o estado de prontidão.** “Alta / Moderada / Baixa”
   (`ReadinessLabels.kt:10-12`), “vermelha / verde” (`QualityGate.kt:97`, `:153`)
   e “dia em vermelho” (`CoachProgressLabels.kt:48`, `:50`).

6. **O Coach expõe nome interno e às vezes perde o sujeito.** O registro
   coloquial do Coach (“me enrolei”, “Opa!”, “Pra eu montar”) é **intencional** —
   é a persona e aproxima o app do dia a dia (decisão do usuário 0 em 27/11/2026,
   já previsto em `DECISIONS.md` §Decisões de produto, 21/11/2026: “Hoje/Tendências/
   replan/notificações/story falam igual ao Coach, sem camada de tradução”).
   O que é defeito: (a) a palavra “motor”, nome interno de artefato, aparece em
   tela (`CoachFallbackLabels.kt:12,27,30,33,39,54,64,67,70,74,77,80,85`;
   `CoachScreen.kt:197`; `CoachNextCard.kt:53`; `CoachViewModel.kt:366,373`;
   `CoachHistoryCard.kt:75`); (b) quatro frases ficam sem sujeito — “só o motor
   prescreve” (`CoachFallbackLabels.kt:30,33,67,70`); (c) a mesma tela abre com
   duas saudações diferentes (“Oi, bora ver o que importa?” em
   `CoachScreen.kt:568` e “Bora ver o que importa?” em
   `CoachOpeningLabels.kt:28,42,45`).

7. **O gate existente é cego.** `fixtures/golden/per-horizon/expected.json`
   já declara `forbiddenRationale: ["Fase PEAK", "Fase TAPER"]`, mas
   `PlanHorizonGoldenTest.kt:154-155` só o aplica ao cenário `noRace` — onde a
   fase é `BASE`/`BUILD`, então “Fase BASE.” passa sem ser notado. O gate
   existe e não pega.

Impacto adicional de escopo: `rationale`, `plannedTitle`, `title`,
`decisionDetail` e `unlockHint` são **strings persistidas** em
`daily_prescriptions` e `weekly_plans` (`DailyPrescriptionEntity.kt:23,26,30,46,48`).
Corrigir o motor muda o texto novo; o histórico já gravado continua com o texto
antigo, a menos que haja política explícita.

## 2. Decisão

Adotamos **um vocabulário canônico, uma voz por contexto e um gate automatizado**
para o texto de usuário. A decisão é de conteúdo e governança; ela não muda
nenhuma regra fisiológica, nenhum limiar, nenhum alvo, nenhum volume e nenhum
critério de qualidade.

### 2.1 Lista negra de jargão

Os termos abaixo **não podem aparecer sozinhos** em texto de usuário. Quando o
termo for insubstituível (rótulo curto de pill, código de zona), ele aparece
**depois** do nome simples, no formato `Nome simples (código)`.

| Termo proibido | Nome canônico | Exceções |
|---|---|---|
| `ACWR` | Aumento | nenhum |
| `CTL` / `ATL` / `TSB` | Base / Fadiga / Balanço | nenhum |
| `TRIMP` / `rTSS` | Carga de treino | nenhum |
| `sRPE` | Esforço percebido | nenhum |
| `LTHR` | FC no limite | nenhum |
| `VDOT` | Índice de aptidão | nenhum |
| `Tier A` / `Tier B` / `Tier C` | Calibração por prova recente / por treinos recentes / Base inicial | nenhum |
| `monotonia` / `monótona` | Variedade | nenhum |
| `strain` | Carga concentrada | nenhum |
| `guardrails` | Travas de segurança | nenhum |
| `Strides` / `strides` | Acelerações curtas | nenhum |
| `Colinas` | Subidas | nenhum |
| `Fácil` (como **nome de sessão**) | Corrida leve / Rodagem | “Fácil” continua válido como rótulo de zona (Z2) e como adjetivo (“dia fácil”, “sessão fácil”) |
| `taper` | Semana de prova | nenhum |
| `deload` | Semana de descarga | nenhum |
| `mesociclo` / `bloco` | Fase do treino / Etapa | nenhum |
| `qualidade` (como tipo de sessão) | Treino forte | nenhum |
| `pace` (em prosa) | ritmo | o **título** “Pace de prova” é mantido (decisão de 21/11/2026); em texto corrido, escreve “ritmo” |
| `splits` | trechos | nenhum |
| `micro-dose` | dose pequena | “dose pequena” é mantido (decisão de 21/11/2026) |
| `blob` / `tiles` | dados brutos / mapa | nenhum |
| `analytics` | rastreamento de uso | nenhum |
| `especificidade` | estilo | nenhum |
| `performance` | resultado | nenhum |
| `Riegel` | (omitido) | nenhum |
| `TT` | Teste de 30 min | nenhum |
| `PEAK` / `BUILD` / `TAPER` / `BASE` / `RECOVERY` (enum) | Pico / Construção / Semana de prova / Base / Recuperação | nenhum |
| `R-`, `EV-` (IDs de regra/evidência) | Regra do motor / Evidência científica | apenas sob “Ver fontes e detalhes técnicos”, colapsado |

O enum `MesoPhase` **nunca** é interpolado em string: passa por um mapper
explícito.

### 2.2 Mapa de enums → rótulos canônicos

| Enum | Rótulo canônico | Onde vive o mapper |
|---|---|---|
| `MesoPhase.BASE` | Base | `core:domain` (novo) |
| `MesoPhase.BUILD` | Construção | idem |
| `MesoPhase.PEAK` | Pico | idem |
| `MesoPhase.TAPER` | Semana de prova | idem |
| `MesoPhase.RECOVERY` | Recuperação | idem |
| `WorkoutType.EASY` | Corrida leve | `app` (`TodayExplanation.kt`) |
| `WorkoutType.LONG` | Longão | idem |
| `WorkoutType.QUALITY` | Treino forte | idem |
| `WorkoutType.REST` | Descanso | idem |
| `ReadinessLevel.GREEN/AMBER/RED` | Alta / Moderada / Baixa | `app/ui/common/ReadinessLabels.kt` (já existe) |
| `CalibrationTier.A/B/C` | Calibração por prova recente / Estimativa pelos seus treinos / Base inicial | `app/ui/calibration/CalibrationScreen.kt` (já existe) |

`ReadinessLevel` passa a ser a **única** fonte de rótulo de prontidão: “vermelha”,
“verde” e “dia em vermelho” saem de `QualityGate`, `CoachProgressLabels` e
`LevelGraduationViewModel`.

### 2.3 Um vocabulário por conceito

| Conceito | Nome canônico | Nunca usar |
|---|---|---|
| Sessão leve | **Corrida leve** (lista compacta) / **Rodagem** (com descrição visível) | “Fácil*” |
| Sessão longa | **Longão** | — |
| Estímulo neuromuscular | **Acelerações curtas** | “Strides” |
| Sessão de intensidade | **Treino forte** | “qualidade” |
| Variação de ritmo | **Fartlek** | — |
| Treino em repetições | **Intervalado** | “VO2max”, “trote” |
| Subidas | **Subidas** | “Colinas” |
| Ritmo de prova | **Pace de prova** | — |
| Semana de redução pré-prova | **Semana de prova** | “taper” |
| Semana de alívio | **Semana de descarga** | “deload” |
| Fase do treino | **Base / Construção / Pico** | “fase BUILD” |

> **Os títulos de sessão em fala de corredor foram decididos pelo usuário 0 em
> 21/11/2026** (`DECISIONS.md` §Decisões de produto): `Rodagem`, `Rodagem leve`,
> `Rodagem com estímulos`, `Intervalado`, `Subidas`, `Acelerações curtas`,
> `dose pequena`, `Ritmo forte`, `Fartlek`, `Longão`, `Pace de prova`,
> `Descanso`, `Recuperação` e `Corrida mais longa` **mantidos**;
> `taper`, `micro-dose`, `monótona`, `Strides`, `Colinas` e `Fácil*` **banidos**.
> Em 27/11/2026 o usuário 0 confirmou que **“Longão” permanece em tudo** — é
> gíria de corrida amplamente conhecida e de fácil compreensão.
>
> **Consequência:** este ADR **não** renomeia títulos de sessão. O que ele faz
> é remover os termos que **não são** fala de corredor e nunca foram aprovados
> (`VDOT`, `LTHR`, `ACWR`, `CTL`, `Tier A/B`, `TT`, “Riegel”, “monotonia”,
> “strain”, “guardrails”, “mesociclo”, “taper”, “micro-dose”) e padronizar o que
> já estava certo mas aparecia em três versões (`Longão` × `Corrida mais longa`
> × `corrida longa`).

### 2.4 Voz por contexto

**Um só grau de informalidade em todo o produto** — a fala de corredor. Não
existe “tela formal” e “Coach informal”: o app fala do mesmo jeito em todas as
superfícies, sem camada de tradução entre elas (`DECISIONS.md`
§Decisões de produto, 21/11/2026). O que muda é **quem está falando**, não o
nível de linguagem.

| Contexto | Quem fala | Registro | Regras |
|---|---|---|---|
| Motor, explicação, dados, telas | o app | **“você”, fala de corredor** | Gíria liberada (`Opa`, `bora`, `pra`, `tá`). Sem “motor” como substantivo de tela (usar “app” ou “plano”). Sem auto-comentário do motor (“não inventei”, “não é inventado”, “não inflamos”). Sujeito sempre presente. |
| Coach (chat) | o treinador | **1ª pessoa, fala de corredor** | Mesma gíria, mesma proximidade. Fala em 1ª pessoa (“me explica”, “me diz”) e trata o usuário por “você”. Nunca deixa frase sem sujeito. |
| Notificação | o app | **1 frase, com o próximo passo** | Mesma informalidade. Sem explicação de algoritmo. |

> **O registro coloquial do Coach é intencional** (decisão do usuário 0 em
> 27/11/2026): o treinador usa um pouco de gíria para se aproximar mais da
> realidade do dia a dia. Uniformidade de registro **não** é o objetivo —
> proximidade é.
>
> **O que se corrige é só o defeito dentro do registro:** “motor” visível,
> frase sem sujeito e duas saudações para a mesma tela.
>
> **Regra de consistência:** a mesma palavra não pode aparecer traduzida em um
> lugar e crua em outro. Se o card do dia diz “Longão”, o rationale diz
> “Longão” — e “VDOT” não aparece em lugar nenhum.

### 2.5 Proibições de conteúdo

1. **Sem julgamento.** Proibido “bom/ruim”, “falhou”, “treino perdido”, “forte
   demais”, “leve demais”, “não fez o que devia”. O texto descreve **direção**
   (“acima do planejado”), nunca qualidade.
2. **Sem auto-comentário do motor.** O texto não comenta o próprio
   comportamento (“não é inventado”, “não inflamos a sessão”, “o app não
   prescreve…”). Ele afirma o fato.
3. **Sem diagnóstico.** “Dor no peito, tontura ou falta de ar anormal são
   sinais para parar e procurar avaliação” permanece — é sinal de alerta, não
   diagnóstico.
4. **Sem promessa.** Sem “garantia”, “vai melhorar”, “resultado garantido”.

### 2.6 Limites de forma

- **Primeiro uso de sigla por extenso**, sempre: `variabilidade cardíaca (HRV)`,
  `oxigenação (SpO₂)`, `esforço percebido (1 a 10)`. Depois, a forma curta.
- **Número sempre com unidade e consequência**: “carga dos últimos 7 dias 1,62×,
  acima de 1,50×” — não “ACWR 1,62 > 1,50”.
- **Nenhum parágrafo visível acima de 30 palavras.** Acima disso: quebrar em
  dois blocos ou mover para “Ver mais”.
- **Nenhum texto de tela com placeholder não resolvido** (`dia(s)`,
  `1.440`, `…`).

### 2.7 Texto persistido: versionamento por `engineVersion`

`rationale`, `plannedTitle`, `title`, `decisionDetail` e `unlockHint` são
persistidos (`DailyPrescriptionEntity.kt:23,26,30,46,48`;
`WeeklyPlanEntity.kt:21`). A política é:

1. A correção **não** exige migração de schema nem reescrita de histórico.
2. Linhas antigas mantêm o texto com que foram gravadas — coerente com ADR 0028
   (revisão versionada) e com o fato de que a decisão histórica foi tomada sob
   aquele motor.
3. `WeeklyPlanGenerator.ENGINE_VERSION` e o `engineVersion` da prescrição
   **mudam** na fatia que altera o texto, para que a auditoria distinga texto
   antigo de texto novo.
4. O texto do dia **recalculado** (nova versão de prescrição após check-in) usa
   sempre o texto atual.

### 2.8 Gate automatizado

O gate reaproveita o mecanismo já existente nos goldens
(`forbiddenTitles` / `forbiddenRationale` em `fixtures/golden/*/expected.json`) e
o **amplia**:

1. **Domínio** — `fixtures/golden/**` passam a declarar, além dos arrays
   existentes, `forbiddenVocabulary` (lista negra da §2.1) checada contra
   `plan.rationale` e todos os `session.title`/`session.blurb`.
2. **Domínio** — novo teste unitário sobre os catálogos que os goldens não
   cobrem: `ExplanationCatalog` (títulos de regra e evidência), `QualityGate`
   (`detail` e `unlockHint`), `SessionLibrary` (títulos e blurbs),
   `SessionBriefingEngine`, `StoryVerdictCalculator` e `RacePace`.
3. **App** — novo teste sobre os literais de UI: `res/values/strings.xml`,
   `ReadinessLabels`, `ProfileLabels`, `CoachNextLabels`, `RacePredictionBlock`,
   `WeeklyReviewFormat` e os labels do Coach.
4. O gate entra **com baseline explícito** dos violadores conhecidos, no padrão
   do baseline do Detekt (`config/detekt/baseline.xml`): cada item removido do
   baseline é uma fatia concluída. A lista negra nunca cresce.
5. O gate **não** pode ser afrouxado para fazer o build passar: o baseline só
   encolhe.

### 2.9 Glossário como fonte de verdade

`docs/GLOSSARY.md` passa a ser o **catálogo canônico** (termo → nome na UI →
o que o usuário lê → onde aparece → regra de primeiro uso) e passa a incluir o
mapa de enums da §2.2. `docs/COMMUNICATION.md` mantém o histórico de copy por
módulo; `docs/DESIGN_SYSTEM.md` §15 mantém os princípios de conteúdo.

### 2.10 Decisões do usuário 0

Cinco decisões de produto tomadas pelo usuário 0 sobre a proposta de vocabulário
e voz (27/11/2026). Precedem e limitam este ADR:

| # | Pergunta | Decisão | Efeito neste ADR |
|---|---|---|---|
| 1 | “Longão” pode manter em tudo? | **Sim** — é gíria de corrida amplamente conhecida e de fácil compreensão. | Sai da lista negra (§2.1). “Longão” passa a ser o rótulo canônico de `WorkoutType.LONG` (§2.2, §2.3). |
| 2 | Um nome só para prontidão? | **Sim.** | `ReadinessLevel` é a única fonte de rótulo (§2.2). |
| 3 | “Motor” pode aparecer em tela? | **Não.** | Proibido em toda a UI, inclusive no Coach (§2.4). |
| 4 | O Coach deve usar gíria? | **Sim** — o treinador deve usar um pouco de gíria para se aproximar mais da realidade do dia a dia. | A informalidade é **intencional** e vale para o produto inteiro, não só o Coach (§2.4). O que se corrige é só o defeito: “motor” visível, frase sem sujeito e saudações duplicadas. |
| 5 | Texto já persistido é reescrito? | **Não** — sem migração. | Política de versionamento por `engineVersion` (§2.7). |

**Decisão anterior que este ADR não pode contradizer** (21/11/2026,
`DECISIONS.md` §Decisões de produto):

> **Voz única em todo o produto:** títulos do motor já nascem em fala de
> corredor (`Rodagem`, `Rodagem leve`, `Rodagem com estímulos`, `Intervalado`,
> `Subidas`, `Acelerações curtas`, `dose pequena`; `Ritmo forte`, `Fartlek`,
> `Longão`, `Pace de prova`, `Descanso`, `Recuperação` e `Corrida mais longa`
> mantidos; `taper`/`micro-dose`/`monótona`/`Strides`/`Colinas`/`Fácil*` banidos);
> Hoje/Tendências/replan/notificações/story falam igual ao Coach, sem camada de
> tradução.

Consequência prática: **este ADR não renomeia título de sessão**. Ele remove o
que nunca foi aprovado (`VDOT`, `LTHR`, `ACWR`, `CTL`, `Tier A/B`, `TT`,
“Riegel”, “monotonia”, “strain”, “guardrails”, “mesociclo”) e padroniza o que já
estava certo mas aparecia em três versões (`Longão` × `Corrida mais longa` ×
“corrida longa”).

## 3. Alternativas avaliadas

| Alternativa | Por que não |
|---|---|
| Só traduzir na UI (sem mexer no motor) | O texto do dia vem do motor; traduzir na UI deixa o jargão persistido e cria dois vocabulários. Não resolve. |
| Reescrever o histórico persistido em migração de schema | Alto risco, ganho pequeno: o texto antigo era correto para a decisão antiga.(versionar por `engineVersion` resolve). |
| Guardar dados estruturados e renderizar na leitura | Arquiteturalmente melhor, mas muda a forma do dado persistido e o motor de versão; escopo grande para um problema de copy. Fica como evolução. |
| Um único termo técnico, com tutorial | O usuário não pediu um tutorial; pediu não precisar pesquisar no Google. A lista negra + glossário resolve sem carga cognitiva. |
| Manter “Rodagem” e “Longão” como jargão aceito | **Aceito** — decisão do usuário 0 (§2.10). “Rodagem” fica **com** descrição visível; em lista compacta, “Corrida leve”. “Longão” fica em todo o app. |
| Banir a gíria do Coach para uniformizar a voz | **Rejeitado** — decisão do usuário 0 (§2.10): o treinador deve usar um pouco de gíria para se aproximar do dia a dia. Uniformidade não vale mais que proximidade. |
| Gate de jargão sem baseline | O build quebraria em ~39 linhas de uma vez e bloquearia a entrega. O baseline do Detekt já é o padrão do projeto. |

## 4. Evidência e força

- **Sem nova alegação fisiológica.** A decisão é de conteúdo, governança e
  consistência. Nenhuma regra, limiar, alvo, volume ou critério muda.
- A terminologia atual já tem fonte interna: `COMMUNICATION.md` §6.3 (traduções
  decididas), `docs/GLOSSARY.md` (nomes canônicos de carga, zonas e prontidão) e
  `docs/DESIGN_SYSTEM.md` §15 (regras de conteúdo). O ADR consolida e fecha as
  lacunas, sem inventar vocabulário novo.
- **Força:** `STRONG` para a lista negra, o mapa de enums e a voz (são
  decisões de produto, verificáveis por teste); `LIMITED` para a escolha de
  “Longão” como rótulo canônico (é vocabulário popular de corrida, não
  ciência); `LIMITED` para a voz do Coach com gíria (é decisão de marca do
  usuário 0).

## 5. Consequências

- `WeeklyPlanGenerator.ENGINE_VERSION` e o `engineVersion` da prescrição mudam
  na fatia de texto do motor.
- Goldens de `fixtures/golden/**` mudam: os títulos esperados (`Rodagem`,
  `Fartlek`, `Subidas`, `Pace de prova 5K`) e os `forbidden*` são reescritos
  conforme o vocabulário canônico. **`Longão` não muda** — ele sai do
  `forbiddenTitles` dos goldens que hoje o proíbem por cenário
  (`per-opportunity`, `per-quality`, `plan-upgrade`).
- Testes de UI que affirmam strings literais precisam ser atualizados junto
  (`CalibrationScreenTest`, `LevelGraduationCardTest`, `WeeklyReviewFormatTest`,
  testes de snapshot do Hoje e do briefing).
- O histórico persistido mantém o texto antigo (ADR §2.7). A UI mostra o texto
  persistido da decisão, sem reescrever o passado.
- `docs/GLOSSARY.md` vira catálogo canônico e passa a ser conferido em review
  de copy.
- O gate automatizado passa a ser obrigatório no `check`.
- Refinamento de F1 (sem mudar a decisão): o gate cobre a lista negra da
  §2.1 **mais** “motor” (§2.4, decisão do usuário 0 §2.10) na mesma fonte
  única, porque todo “motor em tela” da auditoria também é frase sem sujeito;
  o baseline fixa `motor` como termo de voz até a F6.

## 6. Plano de implementação (fatias)

| Fatia | Escopo | Arquivos principais |
|---|---|---|
| **F1** | Gate: baseline + teste de vocabulário no domínio | `core/domain/src/test/.../ContentVocabularyTest.kt`, `fixtures/golden/**` |
| **F2** | Motor — rationale do plano semanal | `WeeklyPlanGenerator.kt`, `WeeklyPlanGenerator` goldens |
| **F3** | Motor — gate de qualidade e regras | `QualityGate.kt`, `ExplanationCatalog.kt` |
| **F4** | Motor — títulos, blurbs e briefing | `SessionLibrary.kt`, `SessionBriefingEngine.kt`, `WorkoutStructure.kt` |
| **F5** | Motor — prova, level e story | `RacePace.kt`, `LevelGraduationEngine.kt`, `InitialExperienceSuggester.kt`, `StoryVerdictCalculator.kt` |
| **F6** | UI — Coach (voz e rótulos) | `CoachFallbackLabels.kt`, `CoachViewModel.kt`, `CoachOpeningLabels.kt`, `CoachNextLabels.kt`, `CoachProgressLabels.kt` |
| **F7** | UI — Tendências, Perfil, Previsão de prova, Atividades | `TrendsScreen.kt`, `ProfileScreen.kt`, `RacePredictionBlock.kt`, `ActivitiesScreen.kt` |
| **F8** | UI — bugs literais e muros de texto | `DataManagementScreen.kt`, `TodayExplanation.kt`, `CoachReviewCard.kt`, `CoachDoneLabels.kt`, `RaceEventCard.kt` |
| **F9** | Gate sem baseline | remove o baseline; `check` verde com a lista negra cheia ativa |

Cada fatia é pequena, verificável e **não** mexe na camada de UI sem o motor
correspondente: a UI e o motor precisam mudar juntos, ou o texto fica incoerente
entre o card do dia e o “Por que este treino?”.
