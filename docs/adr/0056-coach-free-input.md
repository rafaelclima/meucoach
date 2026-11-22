# ADR 0056 — Input livre digitado na aba Coach (parser, validador, retenção)

**Status:** Proposto em 21/11/2026 (sessão só desenho, sem código).
**Data:** 2026-11-21
**Escopo:** `core:domain/coach` (parser + validador), futuro `chat_turns` v22
(`role YOU`), `CoachMemoryRepository`, `CoachViewModel`, proxy (`free-answer`),
`UserDataRepository`/export/delete.
**Relacionados:** ADR 0004 (motor determinístico), ADR 0023 (plano × prescrição),
ADR 0025 (exclusão e época de dados), ADR 0029 (hidratação/nutrição),
ADR 0051 (proxy stateless), ADR 0052 (memória v19), ADR 0054 (timeline v20),
ADR 0055 (tabela única v21), `docs/PRD_COACH_CHAT.md` §6.4/§9.
**PRD:** `docs/PRD_COACH_CHAT.md` §6.4 (composer pós-MVP) + `docs/HANDOFF_POS_CHAT_TURNS.md` §4 item 1.

---

## 1. Contexto

### 1.1 O problema

A aba Coach virou timeline única (`chat_turns`, v21) mas o atleta ainda não
fala: só quick-replies emitem um eco `You` efêmero e o composer segue
desabilitado ("Em breve você poderá digitar"). O handoff pós-ChatTurn (§4
item 1) pede o desenho antes de qualquer código: que intenções o app entende,
o que o validador barra, o que nunca vira prescrição e se o turno `You`
é persistido.

### 1.2 Restrições (herdadas, não renegociadas)

- Motor é autoridade (ADR 0004/0023/0051): LLM só reescreve texto do motor;
  nenhum pace, FC, carga, volume, VDOT ou plano nasce da resposta.
- `core:domain` continua Kotlin puro (sem Android/Room/Hilt/rede); UI não
  calcula regra de treino.
- Payload minimizado, sem texto livre do atleta no proxy (ADR 0051 §2.2,
  ADR 0052 §2.2); sem payload em log (ADR 0051 §2.3).
- Opt-in explícito; revogar bloqueia chamadas e apaga a memória;
  export/delete incluem o histórico (ADR 0025/0052/0055).
- Sem musculação/nutrição/emagrecimento/diagnóstico; única exceção é a copy
  estática de hidratação do ADR 0029, sem números.
- Sem `fallbackToDestructiveMigration`; migração explícita com `MigrationTest`.

## 2. Decisão

### 2.1 Parser de intenção — determinístico, local, sem LLM

O texto digitado **nunca** sai do aparelho como texto cru. Um parser puro em
`core:domain/coach` (`FreeInputIntent`, sem Android/rede) classifica o input
em um enum fechado e extrai só slots validados (dia da semana, minutos,
sessão elegível do T1). A LLM recebe, no máximo, `intent + slots +
contexto do motor` — nunca o `body`.

| Intent | Exemplo | Encaminhamento |
|---|---|---|
| `ASK_WEEK` | "por que fácil hoje?", "o que tem amanhã?" | Resposta curta do contexto vigente (abertura/explicação), sem número novo |
| `ASK_REVIEW` | "como foi semana passada?" | Retrovisor determinístico + narrativa validada, sem nota/streak |
| `REPORT_DONE` | "fiz o longo", "dormi 5h40" | Reconhece + sugere o fluxo canônico ("registra no check-in", "marca no Hoje"); **nunca** escreve check-in/métrica/RPE/sono/execução |
| `REQUEST_ADJUST` | "só tenho 30 min quinta", "perdi o treino", "mover para sexta" | Roteia ao `ReplanController` existente (slots validados → `ReplanSuggestion` → diff → aceite explícito → undo); nunca aplica sozinho |
| `ASK_CAPABILITIES` | "o que você pode fazer?" | Lista fechada do que o Coach faz + o que não faz |
| `ASK_CLARIFY` | Match ambíguo ou parcial | Uma pergunta de volta ("qual dia?", "quantos minutos?"), uma por vez |
| `OUT_OF_SCOPE` | Qualquer resto | Fallback honesto (§2.2), sem inventar |

**Recusado com fallback honesto** (sem culpa, sem código interno, sem `reason`
técnico na UI): diagnóstico e sintoma (dor, lesão, doença, remédio),
pedido de número novo (pace, FC, VDOT, carga, "monta meu treino de amanhã"),
musculação/nutrição/emagrecimento (fora do ADR 0029), comparação médica,
promessa de memória longitudinal. A fala padrão é "Isso eu não faço — …
mas posso …" + anexo/link para o fluxo canônico (Hoje, check-in, prova).
Confiança baixa ou empate de intents cai em `ASK_CLARIFY`, nunca em execução.

### 2.2 O que o `CoachResponseValidator` passa a barrar em texto livre

As travas atuais permanecem (pace `\d:\d\d`, `bpm`, `forbiddenTokens`
vdot/acwr/tss/trimp/ctl/atl/tsb/lthr/vo2, `SCREAMING_SNAKE` +
`forbiddenCodes`, minutos inventados fora de `allowedMinutes`, porcentuais
fora de `allowedPercents`, teto de 2000 chars). O caminho de resposta livre
acrescenta, sem afrouxar o `explain`/`review`:

- **Número de treino fora do plano vigente → `Invalid`.** A resposta só pode
  citar durações/distâncias/percentuais já presentes na prescrição/semana
  vigente (`allowedMinutes`/`allowedPercents` estendidos com distâncias do
  plano; nada fora deles). Qualquer pace, FC, carga, volume, VDOT ou "novo
  treino de X min" → fallback.
- **Prescrição nova → `Invalid`.** Verbo de prescrição ("faça", "corra",
  "troque por", "descanse X dias") acompanhado de número ou mudança de
  estrutura só existe via `ReplanSuggestion` com aceite; na prosa livre é
  fallback.
- **Diagnóstico e escopo proibido → `Invalid`.** Léxico médico/sintoma
  (dor, lesão, tendinite, canelite, inflamado, médico, diagnóstico, remédio,
  suplemento, dieta, emagrecer, musculação, academia) e promessa de memória
  ("vou lembrar", "anotei seu sintoma") → fallback honesto. A hidratação
  estática do ADR 0029 continua permitida como copy fixa, sem números,
  sem LLM.
- **`reason` técnico nunca chega à UI** (reafirma PRD_CHAT §8.2): o fallback
  vira fala do coach ("Ih, me enrolei… O plano segue o mesmo — te mostro
  direto?") + anexo com o rationale do motor.

### 2.3 O que NUNCA vira prescrição, plano ou carga

- A resposta livre **nunca** escreve `DailyPrescription`, `WeeklyPlan`,
  sessões, carga, prontidão ou sono; nunca gera `ruleId`/`evidenceId`/
  confiança; nunca altera `AthleteProfile`/`availabilityHash`.
- O único caminho de ajuste via texto é `REQUEST_ADJUST → slots validados
  → ReplanSuggestion → diff → aceite explícito → undo append-only`
  (máquina do 3.3-A/B/C, intacta). Texto livre sem slot válido não agenda,
  não move e não repõe carga.
- Relato (`REPORT_DONE`) é conversa, não evidência: não conclui sessão
  (`DONE` exige `ACTIVITY`/`GUIDED`/`MANUAL`, ADR 0024), não alimenta
  adesão/volume/80-20 e não muda o semáforo do dia.

### 2.4 Retenção: o turno `You` É persistido em `chat_turns`

**Decisão: persistir**, na primeira migração aditiva após a v21 (v22):

- `chat_turns` aceita `role = 'YOU'` (`origin` nullable; o `Origin`
  EXPLAIN/REVIEW/SUMMARY continua só do Coach). Corpo sanitizado no domínio
  (trim, 1–500 chars, sem HTML/controle); título vazio; `bulletsJson = []`;
  `expiresAt = createdAt + 90 d` (mesmo TTL do EXPLAIN — política de produto).
  Poda preguiçosa por `expiresAt` já existente, sem worker novo.
- Exige opt-in (sem consentimento, nem grava nem chama). Revogar apaga
  `YOU` junto (`clearAll`); "Apagar histórico" e apagar por item reusam o
  mesmo caminho; época/reset do ADR 0025 continuam cobrindo `chat_turns`.
  Export inclui os turns `YOU` (transparência: o usuário vê o que escreveu).
- O `body` do `YOU` **nunca** entra em prompt, payload, log, teste ou repo —
  o proxy recebe só `intent + slots + contexto do motor`. `System`
  (check-in/treino/import) segue **efêmero**: esses eventos já têm fonte
  canônica e duplicá-los criaria segunda fonte de verdade.

Por quê persistir: sem o `YOU` gravado, reabrir a aba mostra respostas sem
perguntas — o arquivo volta a ser morto e os ecos de quick-reply (já `You`
efêmeros) ficam incoerentes com o histórico. O risco LGPD do texto livre é
real, mas fica contido no ciclo de vida já auditado (TTL curto, revogação
que apaga, export/delete, sem texto cru para fora do aparelho).

### 2.5 Contrato com o proxy (quando implementar)

Novo `kind` `free-answer` (não reuso de `explain`/`review`): system prompt de
mensageiro (§PRD_CHAT 8.1), `response_format: json_object`, corpo com
`intent + slots + contexto minimizado do motor`. Sem `body` cru, sem nome,
GPS ou timestamps exatos. Falha/rede/validação → fallback determinístico
falado, que **não** vira memória (paridade ADR 0052 §2.2).

## 3. Alternativas avaliadas

| Alternativa | Por que não |
|---|---|
| LLM classifica a intenção (texto cru no proxy) | Vaza texto livre de saúde para fora do aparelho; contraria ADR 0051/0052 e o risco LGPD aceito só para payload minimizado |
| Parser com regex no app + LLM decide ajuste | Troca quem decide: ajuste passaria a nascer da prosa; quebra ADR 0004/0023 e o aceite explícito do 3.3 |
| `You` efêmero (não persistir) | Arquivo incoerente ao reabrir (resposta sem pergunta); quick-reply efêmero + histórico persistido divergem; economia de LGPD pequena perto do TTL/revogação já existentes |
| `You` com TTL longo ou sem TTL | Retenção excessiva de texto de saúde; 90 d cobre o ciclo de treino corrente sem virar arquivo morto (paridade ADR 0052) |
| Enviar `body` sanitizado ao proxy | Sanitizar não remove sintoma/intenção de saúde; o contrato "só intent+slots" é verificável em teste puro, texto parcial não é |
| `System` persistido junto | Check-in/treino/import já têm tabelas canônicas; duplicar em `chat_turns` cria segunda fonte e briga com a época do ADR 0025 |

## 4. Rollout (fatiado, uma fatia pequena por sessão)

1. ~~ADR (esta sessão, sem código).~~
2. ~~Domínio puro: `FreeInputIntent` + slots + `FreeInputParserTest` (intents,
   ambíguo → `ASK_CLARIFY`, fora-escopo → `OUT_OF_SCOPE`, dia/minutos
   inválidos recusados). Sem Android/rede.~~ (Implementado em 21/11/2026:
   `FreeInputParser` + 20 testes verdes, `check` + `spotlessCheck` verdes.)
3. ~~Validador: léxico médico/escopo + distâncias do plano em
   `CoachResponseValidator` + `CoachResponseValidatorTest`. `explain`/`review`
   intactos.~~ (Implementado em 21/11/2026: `validateFreeAnswer` + 16 testes
   verdes, `check` + `spotlessCheck` verdes.)
4. ~~Proxy: `kind free-answer` + system prompt de mensageiro + contrato
   intent+slots (redeploy Dokploy manual). Sem log de payload.~~
   (Implementado em 21/11/2026: `SYSTEM_FREE_ANSWER` + `CoachKind.FREE_ANSWER` +
   `CoachFreeAnswerPrompt`/`HttpCoachApi`; redeploy pendente.)
5. ~~Repository: v22 aditiva (`role YOU`, `origin` nullable, índice mantido)
   + `MigrationTest` dourado 21→22 + export/delete com turns `YOU`.~~
   (Implementado em 21/11/2026: `recordYouTurn`, `MIGRATION_21_22` por
   rebuild, schema `22.json`, cadeia 1→22, `check` + `spotlessCheck` verdes.)
6. UI: composer habilitado, bolha `YOU` lima, quick-replies mantidos, typing,
   fallback falado, TalkBack/200%/dark-light, apagar por item.
7. Aparelho (usuário 0): pergunta sobre a semana, relato, ajuste com
   diff/confirm/undo, fora-escopo com fallback, revogar apaga, offline
   determinístico. Só então encerrar.
