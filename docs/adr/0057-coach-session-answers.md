# ADR 0057 — Respostas de sessão no Coach (ASK_SESSION / ASK_HOW_TO_RUN)

**Status:** Aceito e implementado em 22/11/2026 (fatia sessão + como correr do mapa Coach).
**Data:** 2026-11-22
**Escopo:** `core:domain/coach` (parser + `CoachSessionAnswer` puro), `CoachViewModel.answerSession`
 (fiação), sem proxy, sem schema, sem motor/ciência.
**Relacionados:** ADR 0004 (motor determinístico), ADR 0023 (plano × prescrição),
ADR 0029 (hidratação), ADR 0051 (proxy stateless), ADR 0056 (input livre).

---

## 1. Contexto

### 1.1 O problema

Com o ADR 0056, perguntas de treino específico caíam em `OUT_OF_SCOPE` ou no
`ASK_WEEK` genérico: "me dá uma dica de como correr amanhã" virava fallback
honesto ("isso eu não faço"), e "o que tem amanhã?" recebia a explicação da
semana inteira. O mapa Coach aprovado pede o contrário: a sessão do dia é o
80% do valor, e a resposta precisa citar a sessão, o motivo do plano e (só
hoje) a prescrição vigente — nunca número novo.

### 1.2 Restrições (herdadas, não renegociadas)

- Motor é autoridade (ADR 0004/0023/0051): LLM só reescreve o contexto da
  sessão; nenhum pace, FC, carga, volume, VDOT ou plano nasce da resposta.
- `core:domain` continua Kotlin puro; UI não calcula regra de treino.
- Payload minimizado (`intent + slots + engineContext`), sem texto digitado,
  sem nome/GPS/timestamps; `reason` técnico nunca na UI (ADR 0051/0056).
- Futuro é **planejado**, nunca prescrito: sem prescrição para dia que não
  chegou, sem semáforo de prontidão futuro (ADR 0023).
- Sem `fallbackToDestructiveMigration`; sem musculação/nutrição/diagnóstico
  (exceção: hidratação estática do ADR 0029, sem números).

## 2. Decisão

### 2.1 Novos intents, mesma porteira

`FreeInputIntent` ganha `ASK_SESSION` (o que tem no dia X) e `ASK_HOW_TO_RUN`
(como executar o dia X). O parser continua determinístico e local, com duas
regras novas de precedência (testadas):

- **Ajuste explícito vence sessão:** "não fiz o treino de ontem" é
  `REQUEST_ADJUST` (vai ao `ReplanController`), não pergunta de sessão.
  Escopo médico/número novo continua vencendo tudo (`OUT_OF_SCOPE`).
- **`como` vence `o quê`:** "como é o treino de hoje?" é `HOW_TO`, não
  `SESSION` — a mesma pergunta, não duas (sem `ASK_CLARIFY` falso).
- `por que ...` continua `ASK_WEEK` (fatia WHY futura); `como foi` continua
  `ASK_REVIEW`; sem dia, o alvo é a próxima sessão relevante (sem adivinhar).

Slots continuam só `dayOfWeek`/`minutes`; ontem/hoje/amanhã/semana resolvem
contra o dia corrente, e dia nomeado resolve para a data dentro da semana
vigente (`resolveDayInWeek`).

### 2.2 `CoachSessionAnswer`: contexto puro por sessão

Novo builder puro em `core:domain/coach` (`SessionTask.WHAT`/`HOW_TO`): dada a
semana persistida e (só hoje) a prescrição persistida, devolve o dia legível,
`engineContext` determinístico e os únicos conjuntos citáveis
(`allowedMinutes` = sessão + prescrição + total semanal;
`allowedDistancesKm` = distâncias do plano, vazias quando o plano não tem).

- Futuro leva o rótulo "Planejado (a confirmação do dia sai no check-in da
  manhã)" e nunca carrega prontidão/sono.
- Hoje com prescrição leva prescrito × planejado, ajuste e sinais do dia
  (sono formatado, check-in feito/pendente).
- `HOW_TO` acrescenta esforço em palavras ("saia fácil e conversável…,
  estrutura com blocos/contínua; se apertar, reduza e feche leve"),
  sem pace/FC/números; descanso vira "descanso é treino".

### 2.3 A VM responde local primeiro, modelo só com sessão válida

`answerSession(parsed, task)`: sem plano → `NO_PLAN`; fora da semana →
`OUTSIDE_WEEK`; passado → `SESSION_PAST`; sem sessão → descanso do dia;
hoje `DONE`/`CLOSED` → `SESSION_DONE` — tudo determinístico, sem rede, sem
latência, sem custo. Só sessão presente/futura `PLANNED` chama `free-answer`
com `contextHash = planHash|targetDate`, `allowed*` da sessão e
`forbiddenCodes` = regras/evidências do plano + sessão. `YOU` sempre gravado;
`ANSWER` só se validado (paridade ADR 0056).

Sem mudança no proxy (o `intent` já viaja; o `engineContext` carrega a
`TAREFA:` WHAT/HOW_TO), sem migração, sem deep-link novo — CTA/atalhos ficam
para a próxima fatia.

## 3. Alternativas avaliadas

| Alternativa | Por que não |
|---|---|
| Reusar `ASK_WEEK` para tudo | Explicação da semana inteira para "o que tem amanhã?" é o genérico que o mapa rejeita; sem contexto da sessão a LLM não tem o que reescrever |
| Prescrever o futuro (`DailyPrescription` adiantada) | Viola ADR 0023: a prescrição é a autoridade do dia; futuro sem check-in/prontidão seria decisão inventada |
| Enviar `structure` com segundos/blocos ao proxy | Números de bloco (`5×3 min`) estourariam o `allowedMinutes` ou forçariam exceção no validador; esforço em palavras + plano na tela é auditável e barrável |
| LLM para passado/descanso/dia feito | Latência/custo sem ganho: o banco já sabe; determinístico é instantâneo e nunca inventa |
| Novo `kind` no proxy por intent | Redeploy + contrato novos sem necessidade: `intent + TAREFA:` no contexto resolve sem tocar o Dokploy |

## 4. Rollout e validação

1. ~~Parser (2 intents + precedência + slots, 27 testes).~~
2. ~~`CoachSessionAnswer` puro (6 testes).~~
3. ~~VM `answerSession` + cópias determinísticas (6 testes novos, 60 na classe).~~
4. Gates: `check`, `spotlessCheck`, `koverVerify`, detekt app/domain verdes.
5. ~~Fix validado no aparelho (22/11/2026): relativo com offset exato + total
   rotulado "planejados".~~ Dia relativo (`hoje`/`amanhã`/`ontem`) resolve por
   offset exato (`+0/+1/−1`) e nunca pela semana vigente — no domingo,
   "amanhã" é segunda 23/11 (fora da semana), não a segunda 16/11 passada.
   Só dia nomeado usa `resolveDayInWeek`. O `engineContext` declara
   "min planejados" para a LLM não ler o total como executado.
6. ~~Item 2-base + item 3 (22/11/2026): `FreeExchange` na VM, envio trava com
   `Loading`, todo `Ready` determinístico gravado como `ANSWER`.~~
7. ~~Item 2 UI (22/11/2026): `exchanges` inline no feed (`FreeExchangeRow`,
   auto-scroll), rodapé sem ecos/resposta, quick replies com `echoQuestion`
   efêmero, composer com `sendEnabled`; tela 201 + VM 67, `check` +
   `spotlessCheck` verdes. Validação no aparelho pendente.~~
8. Próxima: WHY/prontidão/método.

## 5. Exchanges e persistência determinística (itens 2-base e 3)

Desenho validado com o usuário 0 a partir da avaliação no aparelho (22/11):

- **Item 2 UI (nesta fatia):** a tela consome `exchanges` — `FreeExchangeRow`
  inline no fim do feed, auto-scroll, rodapé sem ecos/resposta, quick replies
  com `echoQuestion` efêmero, composer com `sendEnabled`; tela 201 + VM 67,
  `check` + `spotlessCheck` verdes. Validação no aparelho pendente.
- **Item 3 (implementado aqui):** todo `Ready` determinístico
  (sessão local, `CLARIFY`, `CAPABILITIES`, `REPORT`, `ADJUST_ACK`) é gravado
  como `ANSWER` com hash estável `coach-free-v1|local|…`, então o histórico
  nunca tem pergunta órfã. Só `Fallback` segue efêmero (recusa transitória
  ou falha não vira arquivo).
