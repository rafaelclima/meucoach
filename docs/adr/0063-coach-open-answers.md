# ADR 0063 — Conversa aberta do Coach com fatos fechados

**Status:** Aceito e implementado em 24/11/2026.
**Data:** 2026-11-24
**Escopo:** `core:domain/coach` (parser + validador + `CoachOpenAnswer`),
`CoachViewModel`, `HttpCoachApi`, consentimento local, prompts do proxy.
Sem schema, sem motor, sem ciência.
**Relacionados:** ADR 0004 (motor determinístico), ADR 0023 (plano ×
prescrição), ADR 0029 (hidratação), ADR 0051 (proxy stateless), ADR 0052
(memória), ADR 0056 (input livre), ADR 0057 (respostas de sessão).

---

## 1. Contexto

O input livre do Coach (ADR 0056/0057) classifica o texto em um enum fechado e
envia ao proxy apenas `intent + slots + contexto do motor`. Isso protege o
motor, mas cria duas limitações visíveis no aparelho:

- perguntas legítimas que não caem em nenhuma regex ("o que é fartlek?",
  "posso correr na chuva?", "como está minha prontidão?") viram `OUT_OF_SCOPE`
  e recebem uma recusa genérica;
- temas como pace/FC eram bloqueados no parser, então nem uma pergunta
  educativa sobre ritmo chegava ao Coach.

O usuário 0 pediu um meio termo: mais liberdade de conversa, sem números,
treino ou diagnóstico inventados.

## 2. Decisão

### 2.1 Conversa aberta, com porteira local

O parser local continua sendo a primeira barreira. Ele agora:

- bloqueia diagnóstico/sintoma, nutrição, musculação e pedido de plano/treino
  novo (`OUT_OF_SCOPE`) antes de qualquer rede;
- aceita perguntas de corrida que não caibam em uma intent fixa como
  `ASK_COACHING`;
- deixa de bloquear perguntas sobre pace/FC/ritmo; a resposta continua proibida
  de escrever dígitos de pace/FC, mas pode explicar o conceito e apontar o
  cartão determinístico de referência;
- mantém `ASK_SESSION`/`ASK_HOW_TO_RUN` para perguntas ancoradas no treino do
  dia e `ASK_WEEK`/`ASK_REVIEW`/`ASK_PROGRESS` para os recortes existentes.

A pergunta sanitizada (trim, espaços colapsados, teto de 500 caracteres) passa
a viajar no campo `question` do `free-answer`. O app nunca adiciona nome, GPS
ou timestamps exatos; o texto é o que o atleta digitou e passou pela porteira
local.

### 2.2 Fatos fechados: o motor entrega o dossiê

Para `ASK_COACHING`, o app monta `CoachOpenContext` com o que já está
persistido/decidido:

- semana atual: sessões, títulos, tipo, duração, status e rationale;
- prescrição de hoje (quando existe): título, duração, ajuste e motivo;
- prontidão, sono, check-in e a última semana fechada;
- rollup das últimas 4 semanas (adesão, volume, % fácil).

A resposta só pode citar durações, distâncias, percentuais e contagens
presentes nos allow-lists. Conceitos gerais de corrida são permitidos; dados
pessoais, números, pace, FC, carga, VDOT e ACWR não. Se o fato não existe, o
Coach deve dizer o que falta e apontar Hoje/Tendências/check-in.

A referência determinística de pace/FC (`CoachSessionReference`) só acompanha
respostas de sessão/como correr ou respostas abertas que citem o treino de
hoje/alvos; uma definição conceitual ("o que é intervalado?") não leva o
cartão, para não sugerir que o pace do dia é o pace do conceito.

O fallback determinístico de sessão segue a mesma regra: num dia ajustado, usa
o rationale da prescrição (`Hoje: fácil, 28 min. Motivo: ...`), nunca a
estrutura do plano original (ex.: 5 × 3 min), e não duplica prefixos como
"Hoje tem Hoje é fácil".

### 2.3 O validador continua sendo a autoridade final

`CoachResponseValidator.validateFreeAnswer` ganhou `allowedCounts`. Além das
regras antigas (pace, bpm, minutos/percentuais distantes, código interno,
léxico médico, promessa de memória, verbo de prescrição com número/estrutura),
qualquer dígito sem unidade fora das contagens calculadas invalida a resposta.
Resposta inválida vira fallback falado e **não** é persistida.

### 2.4 Consentimento v2 explícito

O consentimento sobe para a versão 2. A copy passa a dizer que a IA escreve a
explicação/revisão **e responde as perguntas digitadas**, enviando um resumo do
treino, da prontidão e o texto digitado, sem nome e sem mapa. Um consentimento
antigo (só resumo) não cobre o novo payload: o usuário precisa aceitar de novo.

O proxy continua stateless, sem log de payload. Revogar/”Apagar tudo” continua
apagando a memória local; a pergunta `YOU` segue com TTL de 90 dias.

### 2.5 O que nunca nasce da resposta

- Treino, plano, carga, pace, FC, VDOT, prontidão ou check-in;
- diagnóstico, nutrição, musculação ou suplemento;
- memória longitudinal prometida;
- ajuste automático: `REQUEST_ADJUST` continua indo ao `ReplanController` com
  diff, aceite explícito e undo.

## 3. Alternativas avaliadas

| Alternativa | Por que não |
|---|---|
| Manter só `intent + slots` | Foi a causa direta do "engessado"; nenhuma pergunta específica tem resposta específica |
| Enviar o histórico inteiro da conversa | Aumenta muito o payload e a exposição; o dossiê do dia cobre o caso de uso |
| LLM classifica a intenção antes da porteira local | Texto de saúde sairia antes de qualquer recusa; contraria ADR 0056 |
| Permitir pace/FC escritos pela LLM | Risco de número inventado/invertido; o cartão determinístico já resolve a referência |
| Resposta livre sem validador | "Mais liberdade" não pode virar perda da garantia de não inventar |

## 4. Consequências

- O prompt `SYSTEM_FREE_ANSWER` do proxy precisa de redeploy manual; o app já
  envia `question` e o dossiê mesmo no proxy antigo.
- `ASK_COACHING`, `CoachOpenAnswer`, `allowedCounts` e os testes de parser,
  validador, API e ViewModel entram no gate.
- Sem migração, sem dependência nova, sem mudança de motor/ciência.
- A memória continua sendo copy validada; a pergunta é arquivada como `YOU`.

---

## 5. Emenda 25/11/2026 — dossiê de avaliação com o executado

O log do aparelho mostrou o modelo respondendo a “avalie meu treino” com
números fora do dossiê (`28`, `4`) porque o `CoachOpenContext` não recebia o
executado e, quando a sessão do dia já estava `DONE`, a prescrição usada era a
do **próximo** treino (sexta), não a de hoje. O validador barrava com razão,
mas o Coach não tinha fatos para avaliar.

A decisão continua a mesma: o motor entrega fatos fechados e a IA só narra.
O dossiê agora inclui:

- `CoachExecution`: duração, distância, RPE, evidência de conclusão e o
  `SessionComparison` determinístico (duração/distância/ritmo/FC/esforço com
  veredictos `WITHIN`/`ABOVE`/`BELOW`);
- `todayPrescription` no `CoachNextContext`, independente de o próximo
  treino ser outro dia;
- fatos de comparação sem os dígitos de pace/FC: o texto pode dizer “ritmo
  dentro do alvo”, mas os números exatos continuam no cartão determinístico e
  no Hoje.

Allow-lists ganham a duração, a distância e o RPE executados e os percentuais
de adesão da comparação. Pace/FC continuam proibidos em prosa pelo validador.
A fonte continua local e sem GPS/arquivo bruto: só o resumo persistido da
atividade/execução. Sem schema, sem motor, sem ciência e sem dependência nova.

---

## 6. Emenda 25/11/2026 — último executado e allow-list do próprio dossiê

A validação no aparelho (log `free answer rejected: Resposta contém número
fora do contexto: 4`) mostrou dois furos que a emenda §5 ainda não cobria:

- o dossiê só olhava `todaySession`/`todayExecution`. Numa quarta de descanso,
  “avalie meu treino” não recebia a execução de terça — justamente a sessão que
  o Coach acabara de dizer que fechou;
- o allow-list era menor que o próprio dossiê: `CoachOpenAnswer` imprime
  “ÚLTIMAS 4 SEMANAS” e “últimos 28 dias”, mas o `4` e o `28` não entravam em
  `allowedCounts`. O modelo ecoava o fato e o validador barrava a resposta.

Decisão:

- `CoachExecution` carrega a data. `CoachNextContext` carrega a última sessão
  concluída (`DONE`/`CLOSED`) até hoje, olhando a semana atual e a anterior,
  com a atividade/execução e a prescrição daquele dia. O fluxo aberto usa
  `todayExecution ?: lastExecution` e o dossiê rotula “EXECUTADO HOJE” vs.
  “EXECUTADO NO ÚLTIMO TREINO (terça)” — nunca apresenta ontem como hoje.
- `CoachOpenAnswer` deriva os allow-lists de **todos** os números do dossiê,
  usando os mesmos padrões do validador (`CoachNumberPatterns`), além dos
  conjuntos tipados. A garantia vira: todo número impresso pelo motor é
  citável; nada fora do dossiê passa.
- O fallback do `free-answer` não promete mais um card que não é renderizado
  (“aqui embaixo”); ele convida o atleta a repetir a pergunta.

Pace/FC continuam fora da prosa e no cartão determinístico. Sem schema, sem
motor, sem ciência e sem dependência nova.

---

## 7. Emenda 25/11/2026 — o dia executado carrega a prescrição que ajustou o plano

O print e o banco local mostraram um dia cujo plano original era uma sessão de
qualidade e cuja prescrição do dia era fácil, com `DOWNGRADE` por prontidão
moderada. A resposta usou o título/duração do plano original e os veredictos
da prescrição ao mesmo tempo (duração dentro, distância/ritmo acima, FC
dentro).

O dossiê impresso tinha o plano original e os veredictos, mas não o título,
a duração e o motivo da prescrição do dia executado. Decisão:

- `CoachOpenContext` ganhou `executedSession` e `executionPrescription`;
- a linha da semana do dia executado passa a mostrar a prescrição como
  autoridade do dia e o plano original como nota:
  `Terça: <título prescrito> · <duração prescrita> · <tipo prescrito> · <status> · ajustado do plano: <título original> · <duração original> · <tipo original>`;
- `MOTIVO DO DIA (terça)` traz o rationale da prescrição (ex.: prontidão
  moderada);
- a comparação passa a se chamar `COMPARAÇÃO COM A PRESCRIÇÃO` quando há
  prescrição; sem prescrição, continua `COMPARAÇÃO COM O PLANO`;
- o `TASK_LINE` do dossiê instrui a usar a prescrição como alvo da avaliação
  e o plano original apenas como contexto;
- o cartão determinístico já usava os alvos da prescrição; agora a narrativa
  usa a mesma autoridade.

A IA continua só narrando: não decide ajuste, não prescreve e não inventa
número. Sem schema, motor, ciência ou dependência nova.
