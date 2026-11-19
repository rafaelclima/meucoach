# PRD — Evolução do Coach (pós-MVP IA Coach)

**Versão:** 0.1 · **Data:** 19/11/2026 · **Status:** proposta para decisão
**Base:** análise técnica da tela Coach (19/11/2026) · **Fonte verdade continua:** `docs/PRD_IA_COACH.md` + código
**Escopo:** evoluir a aba Coach sem reescrevê-la, sem duplicar Hoje/Tendências, sem nova dependência externa.

> Este PRD não substitui o `PRD_IA_COACH.md`. Ele propõe a próxima camada de valor sobre o MVP já entregue (explicação semanal + revisão anterior + replanejo com diff/undo + histórico + Room v19).

---

## A. Visão e objetivos

### A.1 Contexto atual

A aba Coach hoje (`app/src/main/kotlin/com/meucoach/app/ui/coach/CoachScreen.kt`) é uma pilha rolável com 4 blocos:

1. Explicação da semana (reescrita de IA do `WeeklyPlan.rationale`, recolhida em 5 linhas).
2. `CoachReplanCard` (“Ajustar a semana” / “Perdi treino” / “Menos tempo”) via `ReplanController` compartilhado.
3. `CoachReviewCard` da última semana fechada (determinístico + narrativa IA).
4. `CoachHistoryCard` (arquivo somente-leitura, sem repetir o que está na tela).

Fundação sólida e a preservar: motor determinístico como autoridade, `CoachResponseValidator` que barra número inventado, cache por `inputsHash`, fallback gracioso, opt-in com payload minimizado, TTL e revogação que apaga `coach_*`, `WeeklyReviewAssembler` único para Tendências e Coach, aceite com diff/stale/undo append-only.

### A.2 Problemas que pretendemos resolver

| # | Problema | Evidência no código |
|---|---|---|
| P1 | Nenhum motivo para abrir todo dia. Conteúdo muda 1×/semana (`lastHash` + cache em `CoachViewModel.kt:176`). | `RoomCoachPlanSource` só observa `getWeek(weekStart)` (`app/coach/CoachPlanSource.kt:21`); prompt só tem `weeklyMinutes/sessions/rationale` (`CoachPromptBuilder.kt:9`). |
| P2 | Não responde “o que eu faço agora/amanhã?”. Não consome prescrição do dia, prontidão de hoje, carga, sono, check-in, prova. | `CoachPrompt` não tem readiness/load/VDOT/próxima sessão. |
| P3 | Duplica Hoje/Tendências. Diferencial percebido hoje é só “reescrita simpática”. | Rationale já existe no Hoje (“Por quê?”); revisão já existe em Tendências via mesmo assembler. |
| P4 | Estados sem IA são fracos. Sem token/proxy a aba vira rationale cru + “Tentar de novo”. | `CoachExplanationState.Fallback/Unavailable` em `CoachScreen.kt:161,195`; `PROGRESS.md` registra `explain 400` no aparelho. |
| P5 | Rótulos de bloqueio não viram ação (“qualidade bloqueada”, “em calibração”, “volume mantido”). | `qualityBlockReason/decisionDetail/unlockHint` já persistidos no schema v18 (`DailyPrescription.kt:52-56`, `QualityGate.kt:101-138`) mas não surfaciados no Coach. |
| P6 | Sem resposta para “estou evoluindo?”. Tendências tem pontos e gráficos; Coach não sintetiza. | `PlanCompletionCalculator`, `IntensityDistributionCalculator`, calibração/VDOT existem; Coach usa só 1 semana fechada. |

### A.3 Visão pós-evolução

O Coach passa a responder, em ordem, três perguntas do corredor:

1. **“Amanhã posso forçar?”** — próximo treino + semáforo de prontidão + 1 ação de desbloqueio.
2. **“O que está me travando?”** — carga, calibração, recuperação, taper, fonte de dados, em linguagem de corredor.
3. **“Estou evoluindo?”** — síntese determinística de 4 semanas (adesão, volume, 80/20, sono, prontidão, VDOT).

A explicação semanal e a revisão anterior continuam, mas deixam o topo. O topo vira decisão (hoje/amanhã). O arquivo continua embaixo. A IA continua só reescrevendo; todo número e todo bloqueio nascem do motor.

### A.4 Objetivos e resultados esperados

| Objetivo | Meta observável (usuário 0, manual) |
|---|---|
| O1. Criar hábito diário | Abrir o Coach na véspera/manhã para decidir o treino, não só 1×/semana. |
| O2. Reduzir decisão errada | Não forçar em dia âmbar/vermelho; entender por que virou fácil sem ir ao “Por quê?” do Hoje. |
| O3. Aumentar confiança no conservadorismo | Entender “em calibração / volume mantido / sem qualidade” como proteção, não como bug. |
| O4. Mostrar evolução sem gamificação | Responder “4 semanas: adesão X, volume Y, % fácil Z” sem nota, streak ou cobrança. |
| O5. Manter custo baixo | Zero migração nova, zero dependência nova, zero número nascido de LLM. |

---

## B. Diagnóstico e oportunidades

### B.1 Problemas (reafirmados da análise)

P1–P6 acima. Todos verificáveis no código citado. Nenhum exige reinterpretar ciência: são lacunas de composição e hierarquia, não de motor.

### B.2 Melhorias necessárias na experiência atual (sem feature nova)

| ID | Melhoria | Onde |
|---|---|---|
| UX-1 | Reordenar: próximo treino → travas → ajustar → explicação semanal → revisão → arquivo → Desativar. Hoje explicação longa vem antes da ação. | `CoachScreen.kt:114-281` |
| UX-2 | Fallback acionável: em vez de só `planRationale` + “Tentar de novo”, mostrar checklist determinístico (check-in pendente? sono <6h? fonte desatualizada há N dias?). | `CoachScreen.kt:195-231` |
| UX-3 | Deep-links: “Ver como correr” → briefing do Hoje; “Ver tendência” → Tendências; “Fazer check-in” → check-in. Sem duplicar telas. | novo, só navegação |
| UX-4 | `Unavailable` (sem plano) deve explicar o caminho (completar onboarding/calibração/importar), não “Em breve”. | `CoachScreen.kt:161-167` |
| UX-5 | Manter padrão validado: overline, `McCard`, `textTertiary` para meta, expansão sob demanda, TalkBack com `heading()`, 48 dp, dark/light, fonte 200%. | `CoachExplanationCard.kt`, `CoachHistoryCard.kt` |

### B.3 O que deve ser preservado (contrato)

- Domínio puro: `core:domain/coach/*` sem Android/Room/Hilt/rede.
- IA só reescreve; `CoachResponseValidator` como porteira (pace, bpm, siglas, minutos e percentuais fora do permitido → `Invalid` → fallback).
- Cache por `inputsHash`; mesma semana = mesmo card; retry funcional.
- `ReplanController` como única máquina de aceite; `intent` no `inputsHash`, nunca em `AthleteProfile`/`availabilityHash`; undo como revisão nova `UNDO`.
- `WeeklyReviewAssembler` como única montagem da semana fechada.
- Opt-in, payload minimizado, TTL 90/180, revogação apaga `coach_*` em escopo de aplicação, export/delete incluem memória, sem GPS/nome/texto livre no prompt.
- Histórico como arquivo: não repetir explicação corrente nem resumo da semana visível (`CoachHistory.view`).

### B.4 Oportunidades selecionadas

| ID | Oportunidade | Por que selecionada | Complexidade |
|---|---|---|---|
| F1 | Próximo treino + prontidão (“Amanhã posso forçar?”) | Uso diário; reusa gate já calculado; sem número novo | Baixa-média |
| F2 | Guarda de carga e janela (“O que está me travando?”) | Confiança; só expõe estado existente em linguagem simples | Baixa |
| F3 | Reordenação + fallback acionável (UX-1..UX-4) | Alto impacto sem arquitetura | Baixa |
| F4 | Síntese de 4 semanas (“Estou evoluindo?”) | Retenção; reusa revisão/completion/intensidade/calibração | Média |

Excluídos deliberadamente: chat livre com input do usuário, simulador “e se?”, previsão de prova com número novo via IA, memória semântica/embeddings, widgets/notificações do Coach, social. Motivo: exigiriam prompt com números, servidor com estado, nova dependência ou violariam “IA só reescreve”; custo alto para valor incerto no perfil atual.

---

## C. Requisitos funcionais

### F1. Próximo treino + prontidão

**Objetivo e justificativa.** Dar o motivo diário de abertura: dizer qual é o próximo treino relevante (hoje se pendente, senão amanhã), se pode forçar e o que desbloqueia a qualidade. Resolve P1/P2/P5. É o card de maior hierarquia após a evolução.

**Comportamento esperado.**

- Card `CoachNextCard` no topo quando houver plano vigente, com:
  - Linha 1 (overline): `PRÓXIMO TREINO`.
  - Título: dia relativo (“Hoje” / “Amanhã · QUI”) + tipo + duração (ex.: “Amanhã · Fácil 30 min”).
  - Semáforo: `Alta / Moderada / Baixa` (mesmo vocabulário do Hoje; nunca só cor).
  - Motivo em 1 frase determinística (ex.: “Prontidão moderada — sono 5h40.”).
  - Ação de desbloqueio quando reversível (ex.: “Fazer check-in”, “Ver como correr”, “Ver por quê no Hoje”). Sem ação quando irreversível (doença, janela pós-prova, taper) — mostrar prazo.
  - Nota fixa: “Motor decide; IA só reescreve.” (mesmo padrão do `CoachExplanationCard.kt:85`).
- Regra de “próximo”: se sessão de hoje está `PLANNED` e pendente → hoje; senão, próxima sessão futura `PLANNED` da semana vigente. Dia de prova (`RaceEvent` na data) tem precedência e mostra “Dia de prova” sem prescrição (seguir `GetTodayDecision`: prova antes de prescrição, `ARCHITECTURE.md:6.3`).
- Sem plano vigente → não mostrar F1; cair em UX-4.

**Dados necessários (todos existentes, só leitura).**

- `TrainingPlanRepository.getWeek(weekStart)` + sessões futuras (`date > today`, `PLANNED`).
- `DailyPrescription` do dia relevante (`qualityBlockReason/decisionDetail/unlockHint`), via mesmo caminho do Hoje (`GetTodayPrescription` / `daily_prescriptions` por `inputsHash`/`engineVersion`).
- Prontidão do dia (`ReadinessCalculator` / explicação já usada no Hoje) — score, nível, contribuintes, `SUBJECTIVE_MISSING`.
- `RaceEventRepository` para data de prova; `RecoveryWindow` para teto/bloqueio; `TrainingLoadEngine` para `LoadDataState`/calibração (só rótulo, não número instável).
- Reuso de copy: `QualityGate` (`QualityGate.kt:38`), `unlockHint` por motivo (`QualityGate.kt:101-138`: doença, graduação, check-in, sono, prontidão, carga).

**Regras de negócio.**

- RN-F1-01. Nunca aumentar tipo/duração/intensidade. F1 só espelha prescrição/gate; se divergirem, vale a prescrição persistida.
- RN-F1-02. Sem LTHR/pace de limiar → alvo por RPE, confiança baixa (`R-CAL-02`). Não inventar pace.
- RN-F1-03. Sono válido <6h, prontidão âmbar/vermelha, `NO_QUALITY`, doença/lesão, recovery/taper/race → sem qualidade; mostrar motivo + `unlockHint` quando existir.
- RN-F1-04. ACWR desconhecido/em calibração/fora de faixa sozinho nunca veta; apenas mantém (seguir `R-LOAD-03 v3`). Não exibir número de ACWR instável; usar “em calibração (X de 28 dias)” quando `acwrReliable=false`.
- RN-F1-05. Dado ausente é `null`, nunca `0`; ausência reduz confiança, nunca vira “recuperação ruim”.
- RN-F1-06. Dia de prova: autoridade do `RaceEvent`; nenhuma prescrição/briefing retrospectivo.
- RN-F1-07. IA opcional: se `consented` e proxy responder texto válido dentro de `allowedMinutes` do dia (só durações do plano), exibir reescrita; senão, exibir copy determinística. Nenhum número novo via IA.

**Fluxos.**

1. Abrir Coach → `Loading` (skeleton, máx. 1 spinner) → `Ready` (card) / `Empty` (sem plano) / `Error` com retry.
2. Tocar ação primária → navegar (check-in / Hoje-briefing / explicação do Hoje). F1 nunca aplica replanejo; replanejo continua em F-replan existente com confirmação.
3. Check-in concluído antes do `DONE` pode gerar nova versão da prescrição (`R-PRESC-02`); F1 reobserva e atualiza sem reload manual.

**Estados.**

| Estado | Conteúdo |
|---|---|
| Loading | `McCard` com spinner + “Consultando o motor…” (não “Consultando o Coach”, para não prometer IA). |
| Ready | Próximo treino + semáforo + motivo + ação (quando houver). |
| Empty | Sem plano: “Monte seu plano para ver o próximo treino.” + CTA perfil/calibração (UX-4). |
| Error | “Não foi possível carregar o próximo treino.” + “Tentar de novo”. Nunca esconder Hoje. |
| Stale | Fonte de atividades desatualizada >14d → selo “dados desatualizados” + `R-DATA-01`, sem vetar além do motor. |

**Critérios de aceitação.**

- CA-F1-01. Com plano e sessão futura, o card mostra dia relativo, tipo e duração idênticos à sessão/prescrição (teste com plano sintético: TER fácil 30, QUI qualidade 35 → véspera de QUI mostra “Amanhã · …”).
- CA-F1-02. Sem check-in, qualidade planejada aparece bloqueada com `R-READY-06` e ação “Fazer check-in”; após check-in verde, nova versão libera (cobrir `SUBJECTIVE_MISSING`).
- CA-F1-03. Sono <6h válido → sem qualidade + motivo sono (`R-READY-04`); texto não culpa o usuário.
- CA-F1-04. Dia de prova mostra evento, sem prescrição/treino.
- CA-F1-05. Sem plano → `Empty` com CTA; nenhum crash, nenhum número inventado.
- CA-F1-06. Com proxy desligado (build sem token), F1 continua determinístico; IA ausente não vira erro.

---

### F2. Guarda de carga e janela (“O que está me travando?”)

**Objetivo e justificativa.** Traduzir travas do motor em linguagem de corredor com prazo e ação. Resolve P5 e parte de P4. É o card de confiança: explica por que o app é conservador.

**Comportamento esperado.**

- Card `CoachGuardsCard` abaixo de F1, visível só quando houver ≥1 guarda ativa; senão, colapsado em linha “Nenhum bloqueio ativo — siga o plano.” (evita ruído em semana verde).
- Cada guarda: ícone + título + detalhe + (prazo ou progresso) + ação quando existir. Máximo 4 itens; restante em “ver mais” (expansão local, sem navegação).
- Guardas (todas determinísticas, só exibir se ativas):
  1. Calibração de carga: “ACWR em calibração — X de 28 dias com carga objetiva.” (nunca exibir `acwr` instável).
  2. Fonte desatualizada/insuficiente: “Última importação há N dias — qualidade pausada até atualizar.” (`R-DATA-01`).
  3. Recuperação pós-prova: “Recuperação até DD/MM — teto 30/45/60 min, sem qualidade/longão.” (`R-RACE-04`, `RecoveryWindow`).
  4. Taper/semana de prova: “Taper — volume −45–55%, micro-dose mantida.” (`R-PLAN-05/10`).
  5. Monotonia/strain: “Semana monótona — varie estímulos.” (só se `monotonia 7d ≥2,0` ou spike confiável; sem número técnico no título).
  6. Sono/check-in: “Sem check-in de hoje” / “Sono <6h — sem qualidade hoje.” (link para check-in).
  7. Disponibilidade: “Sem dias configurados — usando base 30 min, confiança baixa.” (`R-DATA-01`).
- Nenhuma guarda cria `ruleId/evidenceId` novo; só reaproveita os emitidos pelo gerador/prescrição.

**Dados necessários.**

- `TrainingLoadEngine`: `LoadDataState` (`KNOWN/INSUFFICIENT/STALE`), `acwrReliable`, dias com carga objetiva, monotonia 7d, strain/spike confiável, frescor da fonte (última importação ≤14d).
- `RecoveryWindow` + `RaceEventRepository` (janela ativa, teto por distância, fim em DD/MM).
- `WeeklyPlan` (fase `BASE/BUILD/PEAK/TAPER/RECOVERY`, deload) + `DailyPrescription` (`qualityBlockReason`).
- Check-in de hoje + sono da última noite (via mesmos repositórios do Hoje; sono plausível já tratado por `R-DATA-02`).

**Regras de negócio.**

- RN-F2-01. Lista é derivada, não decidida: se o motor não bloqueou/manteve, a guarda não aparece. Sem “alerta” inventado.
- RN-F2-02. ACWR em calibração nunca reduz nem veta; copy segue `G-LOAD-03` (“X de 28 dias de histórico; ACWR em calibração”).
- RN-F2-03. Recuperação/taper prevalecem sobre deload; semana de prova nunca é descarga (`R-PLAN-04 v2`).
- RN-F2-04. Sono suspeito (`SUSPECT`→`null`) é “ausente”, nunca `SHORT_SLEEP` (`R-DATA-02`).
- RN-F2-05. Texto sem culpa, sem diagnóstico, sem prescrição médica/nutricional/musculação (seguir ADR 0029: só hidratação estática se já usada no briefing; sem número).

**Fluxos.** Abrir Coach → guardas calculadas localmente (sem rede) → cada item expansível para detalhe + “por que isso protege você” (1 frase) → ação (importar, check-in, ver plano).

**Estados.** Loading (skeleton curto) / Ready (0–N guardas) / Empty-colapsado (“Nenhum bloqueio”) / Error com retry que não derruba F1.

**Critérios de aceitação.**

- CA-F2-01. Cold-start com 8 dias objetivos + pré-história parcial → “em calibração (8 de 28 dias)”, qualidade mantida por `R-PER-03`, volume mantido, sem exibir `acwr=1,6` (espelhar `G-LOAD-03`).
- CA-F2-02. Fonte >14d → “dados desatualizados”, qualidade pausada, confiança reduzida (`R-DATA-01`).
- CA-F2-03. Prova 5k há 1 dia → janela ativa até D+3, teto 30 min, sem qualidade/longão; após janela, some sem ação manual.
- CA-F2-04. Semana verde sem travas → linha única “Nenhum bloqueio ativo”, sem lista vazia confusa.
- CA-F2-05. Avião/offline: guardas continuam (cálculo local); só a reescrita de IA cai em fallback.

---

### F3. Reordenação + fallback acionável (rework, sem feature nova)

**Objetivo.** Fazer a tela atual render valor mesmo sem IA e priorizar decisão sobre arquivo.

**Comportamento.**

- Ordem: F1 (próximo) → F2 (travas) → Ajustar a semana (existente) → Explicação da semana (existente, recolhida) → Revisão passada (existente) → Histórico (existente) → Desativar (existente, neutro).
- `Fallback` da explicação: manter `planRationale` + adicionar 1–3 bullets determinísticos do porquê do fallback (“sem conexão”, “resposta fora do contrato”) + ação útil (check-in/importar/tentar de novo). Não gravar fallback em memória (preservar).
- `Unavailable` sem plano: trocar “Em breve” por caminho (“Complete perfil + disponibilidade + 1 importação ou calibração”) com CTAs.
- Manter `ReplanCard`, sheets, diff/selo `v{n}→v{n+1}`, undo e confirmações inalterados.

**Critérios de aceitação.**

- CA-F3-01. Com token vazio, a aba abre sem spinner infinito, sem JSON cru, sem duplicação (regressão da auditoria 18/11).
- CA-F3-02. Explicação corrente e resumo da semana visível não se repetem no histórico (`CoachHistory.view` preservado + teste).
- CA-F3-03. TalkBack percorre topo→ação sem armadilha; fonte 200% e dark/light sem corte (testes de tela existentes estendidos).

---

### F4. Síntese de 4 semanas (“Estou evoluindo?”)

**Objetivo e justificativa.** Responder evolução sem gamificação. Resolve P6. Frequência 1–2×/semana; é o segundo hábito após F1.

**Comportamento esperado.**

- Card `CoachProgressCard` após revisão semanal: “Últimas 4 semanas” com 4–6 linhas determinísticas + 1 frase-síntese (IA opcional, só reescrita):
  - Adesão: “X/Y treinos do plano (treino extra conta para volume, não para adesão).”
  - Volume: “Base real forte N min → alvo M min (+6% quando liberado) ou mantido + motivo.”
  - Intensidade: “X% fácil (alvo ~80%) em N sessões com FC.”
  - Prontidão/sono: “Prontidão média N, M noites curtas.”
  - Calibração: “VDOT N (data) → paces E/T;” ou “Sem VDOT recente — por RPE.”
- Sem nota, sem streak, sem comparação entre pessoas, sem promessa de resultado.
- Toque em “Ver detalhes” → Tendências (sem duplicar gráfico no Coach).

**Dados necessários.**

- Últimas 4 semanas completas: `TrainingPlanRepository.getWeeks`, `PlanCompletionCalculator` (adesão com `ACTIVITY/GUIDED/MANUAL`, `LEGACY_UNVERIFIED` fora da base), `strongMinutes/strongSessions` (ADR 0050), `IntensityDistributionCalculator` (easy% em sessões com samples), prontidão/sono agregados (mesmos do `WeeklyReview`), perfil/calibração (VDOT, origem, `calibratedAt`).
- Excluir: semana parcial corrente, deload/taper/recovery da base quando o motor excluir, plano criado no meio da semana.

**Regras de negócio.**

- RN-F4-01. Progressão só com base demonstrada + guardrails verdes (prontidão verde, carga objetiva, monotonia <2,0, strain sem spike, ACWR 0,8–1,3 confiável); senão, “mantido + motivo”. Seguir `R-PLAN-01 v2`, `R-LOAD-03/04`.
- RN-F4-02. Extra conta para volume, nunca para adesão; `LEGACY_UNVERIFIED` sem atividade não entra na base (seguir `G-VOL-02`).
- RN-F4-03. IA só pode citar minutos/percentuais do conjunto permitido (estender `allowedMinutes/allowedPercents` do `CoachReviewPromptBuilder` para 4 semanas; qualquer outro número → `Invalid` → fallback).
- RN-F4-04. Estimativa só textual qualitativa com marcador, nunca número (decisão 23 do PRD IA Coach).
- RN-F4-05. Sem 4 semanas completas → mostrar o disponível com aviso “X de 4 semanas; parcial”, sem extrapolar.

**Fluxos.** Abrir Coach → cálculo local das 4 semanas → `Ready` com linhas + síntese IA opcional (cache por fingerprint das 4 semanas) → “Ver Tendências” navega.

**Estados.** Loading / Ready / Parcial (“X de 4 semanas”) / Vazio (“Importe 2–3 semanas para ver evolução”) / Error com retry.

**Critérios de aceitação.**

- CA-F4-01. Caso `G-VOL-02` (3×38 concluídas + extra 28 → base 142, alvo 151, adesão 3/3) aparece com copy de crédito do extra e adesão 3/3.
- CA-F4-02. Semana com `LEGACY_UNVERIFIED` sem atividade não infla base nem adesão.
- CA-F4-03. Resposta IA com percentual fora do calculado (ex.: 95% inventado) é descartada e o determinístico permanece.
- CA-F4-04. Com <2 semanas de histórico, mostrar “parcial” sem prometer tendência.

---

## D. Requisitos não funcionais

| Tema | Requisito |
|---|---|
| Usabilidade | Uma decisão por scroll: F1 responde em ≤10 s; linguagem pt-BR de treinador sem culpa; números só com unidade e fonte (“30 min”, “3/4 treinos”); sem sigla técnica no título (VDOT/ACWR só em detalhe com explicação). |
| Desempenho | F1/F2/F4 calculados localmente em <500 ms após dados carregados (derivação sob demanda, sem worker novo); listas com chaves estáveis; samples decodificados sob demanda como hoje. IA com timeout 10 s + 1 retry + cache por hash (manter). |
| Confiabilidade | Qualquer falha de rede/schema/número inventado → fallback determinístico; aba nunca quebra; memória só recebe texto `Valid`; `recordSafely` nunca derruba a tela. |
| Privacidade/LGPD | Manter: opt-in, sem nome/GPS/timestamps exatos/texto livre no prompt, TTL, revogação apaga, export/delete incluem, sem analytics/ads, sem dado real em log/teste (`logcat -s Coach` sem payload). Risco retenção/região da IA segue aceito e declarado; revisitar antes de release além do usuário 0. |
| Acessibilidade | TalkBack com títulos como `heading()`, linhas agrupadas, `contentDescription` em ações; contraste AA; alvos 48 dp; fonte 200% sem corte; dark-first + light completo; sem depender só de cor (rótulo Alta/Moderada/Baixa). |
| Consistência | Tokens de `core:designsystem` (lima `#D8FF3E` só para ação); sem hex solto; `McCard/McPill/McStat/McDivider`; mesma copy de semáforo do Hoje; selo `v{n}` do replanejo inalterado. |
| Testes/qualidade | `assembleDebug`, `check`, `spotlessCheck`, `koverVerify` verdes; sem `fallbackToDestructiveMigration`; sem dado real no repo. |

---

## E. Experiência e interface

Ordem vertical (sem abas novas, sem top-bar nova nesta fatia):

1. Header curto “Coach” + subtítulo “O que fazer, o que trava, como você evolui — motor no comando.”
2. `CoachNextCard` (F1) — único primário no topo.
3. `CoachGuardsCard` (F2) — lista máx. 4, resto expansível.
4. `CoachReplanCard` (existente) — “Ajustar a semana / Perdi treino / Menos tempo”.
5. `ReplanMessageCard` quando houver (existente).
6. `CoachExplanationCard` (existente, recolhido em 5 linhas + “Ler completa”).
7. `CoachReviewCard` (existente) — última semana fechada.
8. `CoachProgressCard` (F4) — “Últimas 4 semanas”.
9. `CoachHistoryCard` (existente) — arquivo, com apagar em bloco/item.
10. “Desativar Coach” neutro no fim (existente).

Regras visuais: 1 pergunta por card; overline para seção; número tabular com unidade; eixos com unidade (se houver mini-barra de adesão, sem pizza); bullets só ao expandir; “A IA só reescreve…” como caption fixo nos cards com IA; CTA primário só para ação de treino/check-in, secundário para navegação, texto neutro para Desativar/Apagar.

Navegação: F1/F2/F4 linkam para Hoje (briefing/“Por quê?”), check-in, Tendências, importação e Perfil/calibração via rotas existentes; nenhum deep-link novo (`meucoach://profile` não existe; manter).

---

## F. Dados e integrações

**Reuso (nenhuma migração nova).**

- Plano/semana: `TrainingPlanRepository.getWeek/getWeeks`, `StoredWeeklyPlan` (`inputsHash`, `generatorVersion`), `PlannedSession` (`date/type/durationMin/status`).
- Dia: `DailyPrescription` (`prescribedType`, `qualityBlockReason`, `decisionDetail`, `unlockHint`, `ruleIds/evidenceIds`, `confidence`, `engineVersion`, `inputsHash`, `supersedesId`) — schema v18 já tem os campos.
- Portas: `QualityGate`, `DailyPrescriptionEngine` + `PrescriptionPolicy`, `GetTodayPrescription`/`GetTodayDecision`, `PlanReplanner` + `EvaluateReplan`/`ApplyReplan` + `ReplanController`.
- Carga: `TrainingLoadEngine` (TRIMP/rTSS/sRPE, CTL/ATL, ACWR + `acwrReliable`, monotonia, strain, `LoadDataState`, `objectiveDataState`, frescor ≤14d, 8 dias objetivos).
- Revisão/progresso: `WeeklyReviewAssembler`, `PlanCompletionCalculator`, `IntensityDistributionCalculator`, `WeeklyReviewCalculator.SHORT_SLEEP_MINUTES`.
- Recuperação/prova: `RaceEventRepository`, `RecoveryWindow`, `RaceTargetProjection` (só leitura; sem entrar em prescrição).
- Calibração: `AthleteProfile` (VDOT, LTHR, origem, `vdotCalibratedAt`, 56 dias), `CalibrationCalculator`.
- Memória: `coach_messages/coach_summaries` (Room v19) só para texto `Valid`; F1/F4 podem gravar reescrita validada com novo `kind`? **Não nesta fatia** — ver G/H.
- Tempo: `TimeProvider`/`Clock`; sem `now()` em regra.

**Adaptação (sem schema novo).**

- Novo `CoachNextSource` (observar prescrição do dia relevante + sessão futura) e `CoachProgressSource` (4 semanas via `getWeeks` + completion/intensidade), ambos em `app/coach/`, espelhando `CoachPlanSource`/`CoachReviewSource`.
- Estender `CoachReviewPromptBuilder.allowedMinutes/allowedPercents` para agregado de 4 semanas (funções puras, testáveis), ou criar `CoachProgressPromptBuilder` espelhado com `SCHEMA=coach-progress-v1` + fingerprint. Preferir espelho para não mudar contrato da revisão.
- Validador: reusar `CoachResponseValidator.validate` com os conjuntos estendidos; sem mudar regra do explain.

**Nada a criar:** nenhuma tabela, coluna, worker, endpoint novo no MVP desta evolução (F1/F2/F4 funcionam determinísticos sem IA; IA usa `explain/review` existentes ou novo `kind` só se o proxy já suportar — ver pendências).

---

## G. Regras de segurança e confiabilidade

- Motor decide; LLM só reescreve. Nenhum pace/FC/carga/volume/VDOT nasce da resposta (validador + `Invalid` → fallback).
- Conservador por padrão: sem dados, não aumentar carga; sem janela completa, volume mantido.
- Proibições mantidas: sem diagnóstico, sem lesão/doença por cima (dor ≥3/lesão/doença → interrompe + registra + encaminha, sem replanejar por cima), sem musculação/nutrição/emagrecimento (exceção só hidratação estática do ADR 0029, sem número), sem quente/frio como prescrição médica.
- Estimativa só textual qualitativa com marcador; nunca número (decisão 23).
- Calor: autorrelato no check-in usa extremo conservador +3% e reduz confiança (`R-PLAN-07`); esteira válida para qualidade no calor (`R-PLAN-08`).
- Sono plausível (`R-DATA-02`): fora de 180–840 min ou fragmentado → `null`/maior bloco, nunca soma; suspeito não vira `SHORT_SLEEP`.
- Privacidade: payload sem nome/GPS/timestamps exatos/texto livre; sem payload em log; sem dado real em teste/fixture/repo.

---

## H. Priorização e fases de implementação

### Fase 0 — Rework sem risco (1 fatia)

- **Contempla:** F3 (reordenar, fallback acionável, `Unavailable` com caminho, UX-1..UX-4) + instrumentação manual (roteiro do usuário 0).
- **Benefício:** valor imediato mesmo sem IA; remove duplicação percebida.
- **Dependências:** nenhuma além do código atual.
- **Complexidade:** baixa.
- **Concluída quando:** build sem token abre sem spinner infinito/JSON/duplicação; TalkBack + 200% + dark/light verdes; `CoachScreenTest` estendido.

### Fase 1 — Decisão diária (1–2 fatias)

- **Contempla:** F1 (`CoachNextCard` + `CoachNextSource`) + F2 (`CoachGuardsCard` local, sem IA nova).
- **Benefício:** cria hábito diário e explica travas; maior impacto por esforço.
- **Dependências:** Fase 0; leitura de `DailyPrescription`/`QualityGate`/`LoadDataState`/`RecoveryWindow`/`RaceEvent` (só compor, sem mudar motor).
- **Complexidade:** baixa-média.
- **Concluída quando:** CA-F1-01..06 + CA-F2-01..05 verdes; nenhuma regra fisiológica alterada; `koverVerify`/`spotlessCheck`/`lint` verdes.

### Fase 2 — Evolução (1 fatia)

- **Contempla:** F4 (`CoachProgressCard` + `CoachProgressSource` + prompt-builder espelhado com fingerprint próprio).
- **Benefício:** retenção semanal; responde “estou evoluindo?”.
- **Dependências:** Fase 1 (hierarquia e fallback prontos); `allowedMinutes/allowedPercents` estendidos.
- **Complexidade:** média.
- **Concluída quando:** CA-F4-01..04 verdes + golden do caso `G-VOL-02` reutilizado; sem migração; sem `fallbackToDestructiveMigration`.

Pós-fases (fora deste PRD): input livre, memória como contexto de copy em prompt, “quero mais tempo”, iniciativa “até 1/dia”. Só após validar F1/F2/F4 com usuário 0.

---

## I. Estratégia de testes

**Unitários (domínio puro, determinísticos).**

- `CoachNextResolverTest`: hoje pendente vs amanhã; dia de prova vence prescrição; sem plano → `Empty`; sessão `DONE/CLOSED` ignorada.
- `CoachGuardsTest`: calibração (8/28 → “em calibração”, sem exibir ACWR), fonte >14d → `STALE`, janela pós-prova por distância (3/5/10/16d + tetos), taper, monotonia ≥2,0, sono suspeito vs curto, sem disponibilidade → base 30 min + confiança baixa.
- `CoachProgressBuilderTest`: 4 semanas com extra (base 142/alvo 151/adesão 3/3), `LEGACY_UNVERIFIED` fora da base, parcial (2/4 semanas) sem extrapolar, fingerprint muda se qualquer fato muda.
- Validador: minuto/percentual fora do permitido → `Invalid`; pace/bpm/sigla → `Invalid`; texto válido → `Valid` com bullets ≤5.

**ViewModel/UI (`:app`).**

- `CoachNextViewModelTest`: Loading→Ready/Empty/Error, retry, sem crash sem consentimento, sem gravar fallback em memória.
- `CoachGuardsTest` (tela): 0 guardas → linha única; 5 guardas → 4 + “ver mais”; cada ação navega sem aplicar replanejo.
- `CoachProgressTest`: parcial/vazio/erro com retry; link “Ver Tendências” não duplica gráfico.
- Regressão da auditoria: explicação corrente e resumo visível não repetem no histórico; body objeto/JSON inválido → fallback sem persistir.

**Integração.**

- `RoomCoachNextSource`/`RoomCoachProgressSource` com banco em memória: semana vigente + prescrições + completion de 2–4 semanas + evento de prova; fuso do perfil.
- Migração: nenhuma nova; `MigrationTest` continua validando schema v19.

**Casos extremos.**

- Troca de fuso/meia-noite (`TimeProvider.refresh`), semana virada, plano criado no meio da semana, deload/taper/recovery, treino que cruza meia-noite (pertence ao dia de início), `zoneId` inválido → fallback para sistema.
- Offline total, proxy 400/401/timeout, `choices` ausente, `title/body` vazios/não-string, bullets inválidos → fallback determinístico, sem persistir.
- Revogação durante chamada: não persistir após `!consented`; histórico volta a `Hidden`.
- Flakes conhecidos: isolar `TodayViewModelPersistTest`/`TutorialSystemBackTest` se aparecerem; não silenciar.

**Validação com usuário 0 (manual, sem dado real no repo).**

- Com token: F1 mostra “Amanhã” correto, trava de check-in/sono explicada, desbloqueio após check-in; F2 lista janela/taper/calibração; F4 bate com Tendências (mesmos números).
- Sem token: F1/F2/F4 determinísticos legíveis; explicação em fallback sem JSON; histórico vazio com copy útil.
- Critério de aceite de produto: explicar em voz alta por que amanhã é fácil/forte e o que mudaria isso (meta ≥80% em teste moderado, seguindo `PRODUCT.md:6`).

---

## Validação contra a implementação (confronto prévio)

- **Compatível:** `DailyPrescription` já persiste `qualityBlockReason/decisionDetail/unlockHint` (v18); `QualityGate` já emite hints por motivo; `RecoveryWindow`, `LoadDataState`, `PlanCompletionCalculator`, `IntensityDistributionCalculator`, `WeeklyReviewAssembler`, `ReplanController`, `CoachResponseValidator` e `CoachHistory.view` existem e são reusáveis sem mudar motor.
- **Sem conflito de autoridade:** F1/F2/F4 só leem e apresentam; `WeeklyPlanGenerator`, `DailyPrescriptionEngine`, `PlanReplanner`, `ApplyReplan` e versionamento (`weekly-plan-v7`/`daily-prescription-v6`) não mudam.
- **Sem migração:** nenhuma coluna/tabela nova necessária; progresso de 4 semanas é agregação em memória de semanas já persistidas.
- **Risco de regressão:** mexer na ordem da `CoachScreen` pode quebrar `CoachScreenTest`/`CoachHistoryTest` (tags `coach-*-card`); mitigar mantendo tags e atualizando testes juntos. Novo `Source` não deve chamar `now()` — usar `TimeProvider`.
- **Superdimensionado?** F4 com IA nova (`replan-suggest`-like para progresso) foi reduzido a reescrita opcional com conjuntos permitidos; se o proxy não suportar novo `kind`, F4 sai só determinístico + síntese textual existente — alternativa mais simples já prevista.
- **Dependência não mapeada:** F1 precisa do “dia relevante” (hoje vs amanhã) consistente com `GetTodayDecision` (prova primeiro); confirmar `TodayViewModel` como referência de precedência antes de codar.

---

## Decisões registradas (19/11/2026, interativo no chat)

- **Decidido neste PRD:** ordem F1→F2→replan→explicação→revisão→F4→arquivo; IA só reescreve; sem migração; sem dependência nova; sem gamificação.
- **F1 persistência — DECIDIDO: só exibir.** Sem gravar em `coach_messages`, sem `kind` novo, sem mudança de TTL/schema. Motivo: menor complexidade; persistência diária geraria ~90 linhas/90d + filtro novo no `CoachHistory.view` + chamadas diárias de IA. Revisitar só se o uso real pedir continuidade.
- **F4 IA — DECIDIDO: determinístico primeiro.** Fase 2 sai sem IA; reescrita vira fast-follow com `coach-progress-v1` espelhado. Motivo: não bloquear valor no proxy.
- **Copy de desbloqueio — DECIDIDO: camada de copy.** Não expor `qualityBlockReason/decisionDetail/unlockHint` literais; mapear cada motivo para frase de corredor com teste. Motivo: risco de tecnicês confirmado na análise.
- **Fora de escopo confirmado:** musculação, nutrição prescritiva, diagnóstico, Zepp direto, social, widgets, iOS, mapa turn-by-turn.
