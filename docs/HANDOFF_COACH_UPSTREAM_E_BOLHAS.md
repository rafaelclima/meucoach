# HANDOFF — Upstream Go + F3-bolhas (próxima sessão)

**Atualizado:** 20/11/2026 ~17:30 (BRT) · **Status:** cadeia nova no ar e correta; upstreams gratuitos degradados; app em fallback determinístico (correto)
**Commits no ar:** `883f968` (session header + failover) + `45e51e4` (Go primário) + `7f1ed7a` (gemma no lugar do inkling morto)
**Não implementar agora** (decisão do usuário 20/11): F3-bolhas fica para a próxima sessão. Este doc é o contexto novinho.

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
- `deepseek-v4.1-flash` **existe** no Go (timeout, não 404 — ID e endpoint `…/zen/go/v1/chat/completions` estão certos; 404 seria rejeição imediata como foi o inkling).
- Tudo gratuito está degradado agora: 3 timeouts + 1 rate-limit (429 no gemma = OpenRouter sem cota free naquele momento).
- `review latencyMs=45312` ≈ timeout de 45 s do app → review cai em fallback por tempo, não por conteúdo.

### 1.2 Aparelho (logcat `-s Coach`, S928B)

- 14:27:21 `explain ok` — a cadeia nova serviu pelo menos uma vez (qual provedor, só o Dokploy diz; ver §3).
- 14:27:54 `review io: timeout` — confirma a lentidão do upstream, não bug do app.
- Telas: F3 no ar (rodapé fixo, chips horizontais, "Nossa conversa"); fallback com a voz nova ("Me enrolei…") e sem sobreposição (hotfix Box→Column validado visualmente).

### 1.3 O que NÃO está provado

- Qual provedor serviu o `explain ok` das 14:27 (Go ou OpenRouter). Só as linhas `coach upstream=... 200` do Dokploy dizem. Pedir ao usuário quando retomar.

---

## 2. Mapa do estado atual

| Peça | Estado | Onde |
|---|---|---|
| Proxy multi-upstream (Go → OpenRouter, session header, failover em 4xx) | ✅ No ar, testado (12/12) | `infra/coach-proxy/.../Main.kt`, `OpenRouterBodyTest` |
| Cadeia OpenRouter (`openrouter/free` → `super-vl` → `gemma-4-31b-it`) | ✅ No ar; gemma substituiu o inkling morto (404) | `DEFAULT_MODELS` |
| Modelo Go (`deepseek-v4.1-flash`, $100/mês de cota, retenção 0 dias) | ✅ Configurado; ID validado indiretamente (timeout, não 404) | `COACH_GO_*` (envs só no Dokploy) |
| App | ✅ Sem mudança necessária (mesmo contrato); F3 + hotfix instalados no aparelho | `app/ui/coach/*` |
| Voz nova nos prompts | ✅ No ar (telas mostram "Bora?", "me diz se ficou bom") | `SYSTEM_EXPLAIN/REVIEW` |
| Docs | ✅ ADR 0051 (addenda Go + session header), PRD, handoff, PROGRESS | `docs/adr/0051-ia-coach-proxy.md` etc. |

Envs no Dokploy (fonte da verdade operacional): `COACH_PROXY_TOKEN`, `OPENROUTER_API_KEY`, `COACH_GO_API_KEY`, `COACH_GO_MODELS` (opcional), `OPENROUTER_TIMEOUT_MS` (legado) / `COACH_UPSTREAM_TIMEOUT_MS` (novo, default 15 s).

---

## 3. Checklist da próxima sessão (nesta ordem)

1. **Dokploy:** confirmar deploy verde do `883f968`/`45e51e4` e colar 10 linhas `coach upstream` recentes.
   - `upstream=go ... 200` → Go serve; fim do incidente.
   - Timeout/429 persistindo em tudo → degradação free-tier; ir ao passo 3.
2. **Aparelho:** "Tentar de novo" → `logcat -s Coach` deve mostrar `explain ok` / `review ok`.
3. **Se ainda lento (review estourando 45 s):** opções, nesta ordem:
   - a) `COACH_UPSTREAM_TIMEOUT_MS=10000` (failover em 30 s em vez de 45 s);
   - b) trocar `COACH_GO_MODELS` para um modelo Go sabidamente rápido (ver `/zen/go/v1/models` com a chave);
   - c) aceitar fallback (app segue 100% funcional sem IA).
4. **Só então** implementar a F3-bolhas (§4).

---

## 4. F3-bolhas — spec para implementar (adiada, não iniciada)

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

**Fora desta fatia:** input livre digitado; fusão `messages+summaries+eventos` num `ChatTurn` persistido (observar 1 semana de uso primeiro).

---

## 5. Prompt para colar no início da próxima sessão

> Continue o MeuCoach pela feature Coach-chat. Leia `AGENTS.md`, `docs/PROGRESS.md` (topo), `docs/PRD_COACH_CHAT.md` (F3) e este handoff (`docs/HANDOFF_COACH_UPSTREAM_E_BOLHAS.md`). Estado: proxy multi-upstream (Go+OpenRouter) no ar com failover testado; upstreams free estavam degradados em 20/11 (timeouts+429, evidência no §1); app em F3 com hotfix do fallback. Primeiro execute o checklist do §3 (Dokploy + aparelho); só então implemente a F3-bolhas do §4 com TDD. Antes de codar, confirme `git status` e rode os gates do AGENTS.md.
