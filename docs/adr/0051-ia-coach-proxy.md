# ADR 0051 — Proxy do IA Coach (OpenRouter atrás de proxy stateless)

**Status:** Aceito e implementado em 2026-11-18 (IA Coach F1/F4 + 3.2; memória local no ADR 0052).
**Data:** 2026-11-18
**Escopo:** `infra/coach-proxy/`, `core/data/coach/CoachApi`, `AppModule`,
`CoachConsentStore`, `CoachViewModel`, `docs/PRD_IA_COACH.md`.
**Relacionados:** ADR 0002 (local-first), ADR 0020 (backup/export), ADR 0023
(plano × prescrição).
**PRD:** `docs/PRD_IA_COACH.md` decisões 6, 9, 10, 11, 14, 15, 33.
**Decisão do usuário 0:** 18/11/2026 (risco LGPD de retenção/região aceito para o MVP).

---

## 1. Contexto

### 1.1 O problema

O IA Coach precisa de um modelo de linguagem para reescrever o texto que o motor
já decidiu. Colocar a chave da OpenRouter no APK entrega a credencial a qualquer
pessoa que descompile o app e não permite trocar de modelo sem publicar uma nova
versão. Chamar o provedor direto do aparelho também expõe o provedor a um cliente
não autenticado e deixa o app refém de um único modelo.

### 1.2 Restrições

- O motor é a autoridade: a IA só reescreve texto (PRD decisão 14); nenhum
  caminho do proxy decide carga, pace ou plano.
- Dado ausente é `null`; a resposta é validada no app antes de aparecer.
- O opt-in é explícito e revogar bloqueia a chamada (PRD decisão 10).
- Sem analytics/ads; sem log de payload de saúde.
- Free tier é instável e muda de disponibilidade sem aviso (PRD §7).

### 1.3 Alternativas avaliadas

| Alternativa | Por que não |
|---|---|
| Chave OpenRouter no APK | Segredo distribuído, impossível revogar por usuário, troca de modelo exige release. |
| BYOK (usuário traz a chave) | Fora do MVP; aumenta atrito e superfície de erro. |
| Modelo local | Fora do MVP; custo de tamanho, bateria e qualidade em pt-BR. |
| Backend completo com contas | Fora do MVP; o produto é local-first e o usuário 0 é único. |

---

## 2. Decisão

### 2.1 Topologia

```
App Android ──HTTPS + X-Coach-Token──▶ proxy Ktor (Dokploy)
                                        └──HTTPS + Bearer──▶ OpenRouter
```

O proxy é **stateless**: não guarda conversa, resumo, memória nem payload. A
única persistência do MVP é local (prefs do opt-in e Room para o que existir no
futuro). O proxy existe para esconder a chave e permitir trocar a cadeia de
modelos sem publicar o app.

### 2.2 Contrato

- `GET /health` → `ok` (sem autenticação, para o health check do Dokploy).
- `POST /api/v1/coach/{kind}` com `X-Coach-Token`.
  - `kind`: `explain`, `review`, `replan-suggest`.
  - Corpo: JSON minimizado montado no domínio (sem nome, GPS, timestamps exatos
    ou texto livre do atleta).
  - Resposta: repasse do provedor (envelope OpenRouter); o app extrai
    `choices[0].message.content` e valida o JSON interno.
- System prompt por `kind` no proxy (`SYSTEM_EXPLAIN`, `SYSTEM_REVIEW`,
  `SYSTEM_REPLAN`), com as proibições e o formato JSON fechado.
- `response_format: json_object` na chamada ao provedor.

### 2.3 Cadeia de modelos e falhas

- `OPENROUTER_MODELS` (CSV) define a ordem; `OPENROUTER_MODEL` é o fallback de
  configuração antiga; sem env vale a lista padrão.
- 429/5xx/timeout/403/404 passam para o próximo modelo.
- 400/401/402/422 param na hora: o problema é do pedido/conta, não do modelo.
- Sem modelo disponível → `502 {"error":"all_models_unavailable"}`; o app cai no
  resumo determinístico.
- Latência e status são logados; **payload nunca**.

### 2.4 Configuração no app

- `COACH_PROXY_URL`/`COACH_PROXY_TOKEN` chegam por
  `-PcoachProxyUrl/-PcoachProxyToken` → `BuildConfig` (vazio = desativado).
- Desativado = `DisabledCoachApi`, que lança `IOException` e alimenta o fallback.
- `HttpCoachApi` usa `HttpURLConnection` (sem dependência nova), timeout de
  conexão de 10 s e leitura de 45 s (o proxy já limita o upstream).
- O `CoachViewModel` decide chamar **somente** com opt-in; revogar apaga o
  consentimento e bloqueia novas chamadas na hora.

### 2.5 Privacidade e segurança mínima

- Segredo do provedor só em variável de ambiente da VPS; nunca no repositório,
  no APK ou em log.
- `X-Coach-Token` compartilhado entre app e proxy; rotação = trocar a env no
  Dokploy e o token no build/prefs.
- TLS terminado pelo Dokploy; proxy expõe apenas `/health` e
  `/api/v1/coach/{kind}`.
- App sem backup Android; export/delete continuam explícitos.
- Risco aceito no MVP: sem declaração de retenção/região do provedor (PRD
  decisão 11). Revisitar antes de qualquer release além do usuário 0.

---

## 3. Consequências

### 3.1 Positivas

- A chave não viaja no APK e pode ser rotacionada sem release.
- Trocar modelo/cadeia é uma variável de ambiente.
- O app não depende de um modelo específico: qualquer falha vira fallback
  determinístico, e o motor segue sendo a autoridade.
- A borda de rede fica isolada em `core:data/coach`, com domínio puro e testável.

### 3.2 Negativas / trade-offs

- O proxy é um novo ponto de falha e de custo operacional.
- O free tier pode mudar sem aviso; a cadeia com fallback reduz, mas não elimina,
  indisponibilidade.
- Sem rate limit dedicado no MVP: o controle atual é o token compartilhado e os
  limites do provedor.
- A latência do proxy soma à do provedor; por isso o timeout do app é maior que o
  do upstream.

### 3.3 Não objetivos

- Contas, multiusuário, billing ou rate limit por usuário.
- Guardar conversa/memória no servidor (qualquer memória é local).
- Decidir treino, prescrever número ou emitir `ruleId` novo.
- Suportar modelo local, BYOK ou outros provedores no MVP.

---

## 4. Rollout

- `coach-proxy` publicado no Dokploy com `OPENROUTER_API_KEY` e
  `COACH_PROXY_TOKEN`; `/health` verde.
- Build do app com `-PcoachProxyUrl/-PcoachProxyToken`; sem as flags, o Coach
  fica desativado e o app segue 100% funcional.
- Validação manual no aparelho do usuário 0 (401 sem token, cadeia via curl,
  explicação real na tela).
- O opt-in aparece na primeira abertura da aba Coach; revogar em Dados/Coach.

---

## 5. Registro de implementação

- **F1 (18/11/2026):** proxy Ktor 3.1.2 stateless, `X-Coach-Token`, `/health`,
  sem log de payload, TLS pelo Dokploy; contrato validado com curl.
- **F4 (18/11/2026):** `CoachApi` (`Http`/`Fake`/`Disabled`) em `core:data`,
  `BuildConfig` via flags, opt-in explícito, aba Coach com explicação real e
  fallback; correções de retry, 403 na cadeia e validação de duração.
- **3.2 (18/11/2026):** endpoint `review` com payload de agregados (adesão,
  volume, qualidade, prontidão, sono, intensidade e códigos de observação),
  validação opt-in de porcentagem e cartão determinístico + narrativa na aba.
- **Testes:** prompt/validator puros, `CoachApi` com servidor local, consent
  store, ViewModel (explicação e revisão), source da semana fechada e tela. Sem
  dados reais no CI; o teste real é manual contra a VPS.
- **Correção 20/11/2026 (Go exige `x-opencode-session`):** sem o header o Go
  devolve 400 `MissingSessionID` e nada funciona. Proxy agora envia
  `x-opencode-session = meucoach-coach-{kind}-{inputsHash}` (estável por
  conversa, só hash, bom pro cache deles) + `User-Agent: MeuCoach-Coach/1.0`
  (exigências de roteamento do Go). De quebra, 4xx não abortam mais a cadeia:
  guardam o primeiro erro duro e seguem para o próximo provedor (chave ruim
  num provedor não veta o outro); no esgotamento, o erro duro vence o 502
  genérico. Teste `sessionIdFor` (hash/ausência/JSON inválido).
- **Correção 20/11/2026 (upstream duplo: OpenCode Go + OpenRouter):**
  assinatura Go do usuário 0 alimenta o Coach via endpoint OpenAI-compatible
  (`COACH_GO_URL`, default `https://opencode.ai/zen/go/v1/chat/completions`)
  com `COACH_GO_API_KEY` e `COACH_GO_MODELS` (default
  `deepseek-v4.1-flash`: maior cota do Go, $100/mês). A cadeia tenta o Go
  primeiro e cai no OpenRouter sozinha (`UpstreamTarget` + `buildChain`
  puros, com teste-tripwire); sem chave do Go, o comportamento é o de antes.
  Envs só no Dokploy, nunca no APK; `COACH_UPSTREAM_TIMEOUT_MS` opcional
  (cai para o legado `OPENROUTER_TIMEOUT_MS`, default 15 s).
  **Exige redeploy + `COACH_GO_API_KEY` no Dokploy.** Ressalvas aceitas:
  Go é off-label para tráfego não-agente (monitorado contra abuso),
  cota compartilhada com o uso no coding, DeepSeek exige região Global no
  console, e o Go bloqueia por padrão endpoints que treinam com os dados
  (manter assim). Confirmar o id exato em
  `GET opencode.ai/zen/go/v1/models` com a chave (404 cai no fallback
  sozinho, sem quebrar a aba).
- **Correção 19/11/2026 (JSON inválido no explain):** `buildOpenRouterBody`
  montava o corpo por interpolação sem escapar o system prompt; `SYSTEM_EXPLAIN`
  e `SYSTEM_REPLAN` contêm `{"title":...}` literal e geravam JSON inválido →
  OpenRouter 400 em todo `explain`/`replan-suggest`, enquanto `review` (sem
  aspas internas) passava. Corpo agora via `buildJsonObject`
  (`kotlinx-serialization-json`), com `OpenRouterBodyTest` de regressão.
  **Exige redeploy no Dokploy.** App passa a incluir os primeiros 200 chars do
  erro do provedor no `IOException` para diagnóstico via `logcat -s Coach`.

---

## 6. Referências

- `docs/PRD_IA_COACH.md` §1 (decisões 6/9/10/11/14/15/33), §3, §5, §7 e §9.
- `docs/PRODUCT.md` §8 (sync opcional e VPS).
- `AGENTS.md` §4 (privacidade) e §12 (o que não fazer).
- `infra/coach-proxy/src/main/kotlin/com/meucoach/proxy/Main.kt`.
- `core/data/src/main/kotlin/com/meucoach/core/data/coach/CoachApi.kt`.
