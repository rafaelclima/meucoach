# HANDOFF — Upstream Go + F3-bolhas (próxima sessão)

**Atualizado:** 20/11/2026 ~18:10 (BRT) · **Status:** checklist §3 executado; upstreams gratuitos seguem degradados; **F3-bolhas implementada** e verde nos gates; falta instalar/validar no aparelho e redeploy do proxy com a chave Go.
**Commits no ar:** `883f968` (session header + failover) + `45e51e4` (Go primário) + `7f1ed7a` (gemma no lugar do inkling morto)
**F3-bolhas:** implementada nesta sessão (ver §4); o `COACH_GO_URL` default passou para o base URL documentado (`/inference/go/openai/v1/chat/completions`).

---

## 1. O que está provado (evidência, não hipótese)

### 1.1 Dokploy, 20/11 17:26:18 (código novo valendo — linhas com `upstream=`)

```
coach upstream=go model=deepseek-v4.1-flash error=Request timeout [15 s] (trying next)
coach upstream=openrouter model=openrouter/free error=Request timeout [15 s] (trying next)
coach upstream=openrouter model=nvidia/nemotron-3.5-super-vl:free error=Request timeout [15 s] (trying next)
coach upstream=openrouter model=google/gemma-4-31b-it:free status=429 (trying next)
coach kind=review latencyMs=45312
```

Leitura:

- O failover funciona como projetado: tentou Go → router → super-vl → gemma, logou cada passo, esgotou e devolveu erro (app caiu no fallback). **Nenhum bug nosso aqui.**
- `deepseek-v4.1-flash` **existe** no Go (timeout, não 404 — ID certo; o path antigo `…/zen/go/v1/chat/completions` era um alias legado e agora o default é o canônico `…/inference/go/openai/v1/chat/completions`; 404 seria rejeição imediata como foi o inkling).
- Tudo gratuito está degradado agora: 3 timeouts + 1 rate-limit (429 no gemma = OpenRouter sem cota free naquele momento).
- `review latencyMs=45312` ≈ timeout de 45 s do app → review cai em fallback por tempo, não por conteúdo.

### 1.2 Aparelho (logcat `-s Coach`, S928B)

- 14:27:21 `explain ok` — a cadeia nova serviu pelo menos uma vez (qual provedor, só o Dokploy diz; ver §3).
- 14:27:54 `review io: timeout` — confirma a lentidão do upstream, não bug do app.
- Telas: F3 no ar (rodapé fixo, chips horizontais, "Nossa conversa"); fallback com a voz nova ("Me enrolei…") e sem sobreposição (hotfix Box→Column validado visualmente).

### 1.3 Retry de 20/11 14:40 (checklist §3.2 cumprido)

- `review ok hash=coach-review-v1|...` às 14:40:08 — a IA **serviu a revisão** nesse retry.
- `explain io: timeout` às 14:40:12 — a explicação estourou os 45 s do app e caiu no fallback falado; sem crash.
- Tela: fallback com `Resumo da semana` + frase do motor + "Me enrolei na explicação…" **duplicada** (corrigida na F3-bolhas: agora só a linha do `CoachFallbackLabels` + botão "Tentar de novo"; "Desativar IA" saiu do feed e mora no "Como funciona").
- Leitura: o app está correto; a degradação é do upstream. O log do Dokploy segue sem `upstream=... 200` para o Go; a chave/região do Go ainda precisam ser conferidas (ver §3.3).

### 1.4 O que NÃO está provado

- Qual provedor serviu o `explain ok` das 14:27 (Go ou OpenRouter). Só as linhas `coach upstream=... 200` do Dokploy dizem. Pedir ao usuário quando retomar.

---

## 2. Mapa do estado atual

| Peça | Estado | Onde |
|---|---|---|
| Proxy multi-upstream (Go → OpenRouter, session header, failover em 4xx) | ✅ No ar, testado (13/13); default Go agora é `/inference/go/openai/v1/chat/completions` | `infra/coach-proxy/.../Main.kt`, `OpenRouterBodyTest` |
| Cadeia OpenRouter (`openrouter/free` → `super-vl` → `gemma-4-31b-it`) | ✅ No ar; gemma substituiu o inkling morto (404) | `DEFAULT_MODELS` |
| Modelo Go (`glm-5.3-flash` → `deepseek-v4.1-flash`, retenção 0 dias) | ⚠️ Default novo (flash primeiro); log de 19:35 ainda era só DeepSeek timeout + OpenRouter 429 | `COACH_GO_*` (envs só no Dokploy) |
| App | ✅ F3-bolhas implementada (bolhas + "Ver números"); falta instalar no aparelho | `app/ui/coach/*` |
| Voz nova nos prompts | ✅ No ar (telas mostram "Bora?", "me diz se ficou bom") | `SYSTEM_EXPLAIN/REVIEW` |
| Docs | ✅ ADR 0051 (addenda Go + session header), PRD, handoff, PROGRESS | `docs/adr/0051-ia-coach-proxy.md` etc. |

Envs no Dokploy (fonte da verdade operacional): `COACH_PROXY_TOKEN`, `OPENROUTER_API_KEY`, `COACH_GO_API_KEY`, `COACH_GO_MODELS` (opcional), `OPENROUTER_TIMEOUT_MS` (legado) / `COACH_UPSTREAM_TIMEOUT_MS` (novo, default 15 s).

---

## 3. Checklist desta sessão (executado em 20/11)

1. **Dokploy (feito):** deploy verde do `883f968`/`45e51e4`; logs de 17:32 seguem degradados — Go timeout 15 s, `openrouter/free` timeout, `super-vl` timeout, `gemma` 429, `review latencyMs=45297`. **Nenhum `upstream=go ... 200` ainda.** Failover correto; falta a chave Go válida/região Global.
2. **Aparelho (feito):** "Tentar de novo" às 14:40 → `review ok` (IA serviu a revisão) + `explain io: timeout` (fallback falado, sem crash). Logcat acima.
3. **Se ainda lento:** opções, nesta ordem:
   - a) `COACH_UPSTREAM_TIMEOUT_MS=10000` (failover em 30 s em vez de 45 s);
   - b) trocar `COACH_GO_MODELS` para um modelo Go rápido — `glm-5.3-flash` ou `kimi-k3` (lista viva em `/inference/go/v1/models`);
   - c) aceitar fallback (app segue 100% funcional sem IA).
4. **F3-bolhas:** implementada nesta sessão (§4), com TDD e gates verdes. Falta `./install.sh` + validação visual no aparelho.

### 3.1 Chave do Go (dúvida do usuário 20/11)

- A chave **não** é a `COACH_PROXY_TOKEN` (essa é o token que o app manda no `X-Coach-Token`).
- Para Go, o caminho recomendado é **Console → Account → Personal API keys → Add API key**, escolhendo o workspace com assinatura Go; copie na hora (aparece uma vez) e cole em `COACH_GO_API_KEY`.
- O print `opencode_go.png` mostra o fluxo **"Connect a client" → Create API key**, que cria uma **service account**. Ela autentica, mas a doc diz que service accounts não são cobertas pelo Go Teams; para a cota Go, prefira a chave pessoal acima.
- O print usa o base URL pay-as-you-go (`https://opencode.ai/inference/openai/v1/...`). Para Go, o base URL é `https://opencode.ai/inference/go` + `/openai/v1/chat/completions`; o default do proxy já foi corrigido para essa URL canônica.
- Confirme em **Settings › Privacy → Regions** que **Global** está habilitado para DeepSeek V4.1 Flash; sem isso o Go pode recusar/travar esse modelo.
- **Log 19:35:** a URL canônica já está em uso, mas o Go DeepSeek estourou 15 s e o OpenRouter free respondeu 429. O default do proxy agora tenta `glm-5.3-flash` primeiro; falta redeploy. Teste direto com a chave no seu terminal (não colar aqui):
  `curl -m 60 https://opencode.ai/inference/go/openai/v1/chat/completions -H "Authorization: Bearer $COACH_GO_API_KEY" -H "Content-Type: application/json" -H "x-opencode-session: meucoach-diagnose" -d '{"model":"glm-5.3-flash","messages":[{"role":"user","content":"Responda só ok"}]}'`
  Se 200 → modelo/timeout do proxy; se 401/403 → chave/região; se timeout → modelo/região.
- **Nunca** colar a chave em chat, issue, commit ou log.

---

## 4. F3-bolhas — implementada em 20/11 (spec original)

**Problema (validado nas capturas 20/11 14:28):** chrome virou chat, mas o corpo continua relatório — `McCard` com paredes de texto (explicação ~400 chars corridos, modelo ignorou o teto de 140) + tabelas de números ("O que o motor registrou", "3 de 3… 100%", "Planejado 3h48…"). Prompt sozinho não impõe formato; a UI precisa impor.

**Mudança (camada de apresentação; motor, contrato e validador intactos):**

1. `CoachExplanationState.Ready` / `ReviewState.Ready` viram **sequência de bolhas do coach**: título = 1ª bolha; corpo fatiado em ≤140 chars por fronteira de frase; pergunta final sozinha; bullets como bolhas curtas ou colapsadas. Função pura (`CoachBubbles.split(texto)`) + testes (corte em frase, nunca no meio da palavra, teto respeitado).
2. **Tabelas determinísticas colapsam** num único "Ver números" por seção (review: adesão/volume/prontidão/sono; progress: agregados). Nenhum número some — sai do fluxo principal para anexo.
3. **Header:** status encurta para "motor no comando" (estado da IA mora no "Como funciona").
4. **Chips:** affordance de rolagem (fade/end-padding) na linha horizontal.
5. **Histórico:** remover duplicação "Nossa conversa" (overline) × "Conversa" (seção) — manter só um.
6. **Composer:** emagrecer o hint (uma linha, sem bloco).
7. **Regressão:** bolha ≤140 chars renderizada (posição/tamanho), "Ver números" expande, sem sobreposição (padrão do teste do fallback), dark/light, fonte 200%, TalkBack por bolha.
8. **Gates:** `:app:testDebugUnitTest --tests 'com.meucoach.app.ui.coach.*'`, `assembleDebug`, `detekt`, `lintDebug`, `spotlessCheck`; sem migração, sem dado real.

**Resultado (20/11):**

- `CoachBubbles` puro em `app/ui/coach/CoachBubbles.kt`; `CoachBubblesTest` cobre frase, teto, palavra inteira, vazio, pergunta final e palavra gigante.
- `CoachExplanationCard` e `CoachReviewCard` viraram `CoachBubbleSequence` (avatar só na 1ª bolha, ≤140 chars, pergunta final sozinha); bullets da explicação em "Ver detalhes"; bullets da revisão como bolhas curtas.
- Review e progress colapsam números em "Ver números"; as observações determinísticas da revisão agora **sempre** aparecem no anexo (mesmo com `Ready`), corrigindo o risco de esconder sinal de segurança.
- Fallback do explain: só a fala do `CoachFallbackLabels` + "Tentar de novo"; a linha duplicada "Me enrolei aqui…" e o "Desativar IA" saíram do feed (ação destrutiva só no "Como funciona").
- Header status "motor no comando"; estado da IA no sheet; chips com end-padding; histórico sem "Conversa" duplicada; composer em uma linha.
- `CoachBubbleCardsTest` cobre bolhas ≤140, "Ver números", observações, 200% e tema claro; `CoachScreenTest`/`CoachHistoryTest` atualizados.
- Gates: 131 testes do Coach verdes; `assembleDebug`, `detekt`, `lintDebug`, `spotlessCheck` verdes; proxy 13/13. Sem migração, sem dependência nova, sem dado real.

**Fora desta fatia:** input livre digitado; fusão `messages+summaries+eventos` num `ChatTurn` persistido (observar 1 semana de uso primeiro).

---

## 5. Prompt para colar no início da próxima sessão

> Continue o MeuCoach pela feature Coach-chat. Leia `AGENTS.md`, `docs/PROGRESS.md` (topo), `docs/PRD_COACH_CHAT.md` (F3/F4) e este handoff (`docs/HANDOFF_COACH_UPSTREAM_E_BOLHAS.md`). Estado: **F3-bolhas implementada e verde** (bolhas ≤140, "Ver números", fallback sem duplicação); proxy com default Go canônico (`/inference/go/openai/v1/chat/completions`), mas ainda sem `upstream=go ... 200` nos logs; upstreams free degradados. Primeiro: (1) confirmar a chave pessoal do Go + região Global no Console e colar em `COACH_GO_API_KEY`; (2) redeploy do proxy e conferir 10 linhas `coach upstream`; (3) `./install.sh` com o token e validar a F3-bolhas no aparelho (fonte 200%, dark/light, "Ver números", sem sobreposição). Só então seguir para F4 (roteiros diários) do PRD. Antes de codar, confirme `git status` e rode os gates do AGENTS.md.
