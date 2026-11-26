# HANDOFF — Coach v3 (rodapé, retrovisor e acentos; histórico)

**Atualizado:** 26/11/2026 (v3 registrada em 20/11/2026)
**Status atual:** a v3 foi validada no aparelho em 20/11/2026 e o Coach evoluiu depois disso: input livre (ADRs 0056/0057), conversa aberta (ADR 0063), lembrete fiel (ADR 0064), dossiê do executado (emenda ADR 0063) e Fatia 7 de documentação. **Pendente:** redeploy do proxy com os prompts novos e validação no aparelho com token; ver `HANDOFF_IA_COACH.md` §5.4. O restante deste documento (§1–§7) é histórico da v3.
**Commits à época:** `59b09d6` (lapidação v2), `d0d65b5` (GLM-5.3-Flash primeiro), `017a23a`
(prompts com acentuação — **redeploy feito em 20/11, verificado no aparelho**), `01f67b1` (v3).

---

## 1. Evidência do estado atual (20/11 19:12)

- Print do aparelho: feed enxuto, “Ver como correr hoje”, “Um cuidado: Base de carga…”,
  explicação em 3 bolhas e rodapé com chips. A LLM respondeu em 1ª pessoa
  (“Rafa…”, “Seu perfil de planejamento mudou…”), sem fallback.
- Logcat:
  `19:00:11.280 Coach: explain ok hash=4ca691…`
  `19:00:11.319 Coach: review ok hash=coach-review-v1|…`
- O proxy em `/health` responde 200; o OpenRouter free continua 429, mas o Go está servindo.
- **Ainda sem acentos** no texto (“voce”, “entao”, “facil”). O system prompt do proxy
  estava em ASCII e o modelo copiava o estilo; `017a23a` corrigiu o prompt, mas o
  container atual ainda não tem esse commit.

## 2. O que a v2 já entregou

- Feed: header + divisor “Hoje” + 1 bolha de abertura + anexo compacto do dia.
- Travas só quando existem, em bolhas curtas (“Um cuidado: …”).
- `CoachReplanCard` removido do feed; replan vive nos quick replies/sheets.
- Explicação longa recolhida em 3 bolhas (2 + pergunta final) + “Ver detalhes”.
- Retrovisor sob demanda (“Quer olhar pra trás?” + Semana passada/Evolução/Histórico);
  histórico em bottom sheet.
- Sem avatares repetidos no feed; anexo com borda sutil; composer de uma linha.

## 3. Problemas que a v3 resolve (em ordem)

1. **Ecos duplicados:** tocar “Explica a semana” duas vezes gera duas bolhas iguais
   (“Pode explicar minha semana?”) no rodapé. Precisa deduplicar e manter no máximo 2.
2. **Retrovisor pouco descobrível:** o “Quer olhar pra trás?” fica abaixo da dobra e
   não há atalho no rodapé (removemos “Semana passada” para não duplicar). O usuário
   não vê como abrir revisão/evolução/histórico sem rolar.
3. **Fade do último chip:** o gradiente em cima de “Só 30 min” parece desabilitar o
   chip. Suavizar/não cobrir o texto.
4. **Acentuação da IA:** redeployar `017a23a` e verificar; se o GLM continuar sem
   acentos, testar `deepseek-v4.1-flash` ou reforçar o prompt.
5. *(Opcional, se sobrar espaço)* o anexo do dia ainda é o bloco mais pesado; dá para
   deixar só headline + 1 linha + CTA e mover o resto para “Ver detalhes”.

## 4. Spec da fatia v3 (pequena e verificável)

### 4.1 Ecos sem duplicata
- `CoachScreen`: antes de `userEchoes.add(texto)`, ignorar se o último eco for igual.
- Limitar a 2 ecos visíveis (comportamento atual) e garantir que o **mesmo** chip
  tocado duas vezes não crie duas bolhas.
- Teste de regressão em `CoachScreenTest`: tocar `coach-quick-explain` duas vezes →
  exatamente 1 `coach-user-echo` com “Pode explicar minha semana?”.

### 4.2 Retrovisor sempre alcançável
- Adicionar um chip/ação **“Retrovisor”** no rodapé (`coach-quick-retro`), com o mesmo
  padrão dos outros quick replies.
- Tocar rola até o `coach-retro-prompt` (capturar `retroY` com `onGloballyPositioned`
  e `animateScrollTo`, como o `explainY` já faz).
- Manter o prompt inline; não criar sheet novo nesta fatia.
- Teste: tocar `coach-quick-retro` deixa `coach-retro-prompt` visível.

### 4.3 Fade discreto
- Ajustar o gradiente de `0.86f` para `0.94f–1f` e aumentar o spacer final para 40dp;
  o texto do último chip deve continuar legível.
- Sem teste de pixel; validar no print do aparelho.

### 4.4 Acentuação
- Redeploy do proxy com `017a23a`.
- Disparar um explain e conferir “você”, “então”, “fácil”, “sessões”.
- Se o GLM ignorar, trocar `COACH_GO_MODELS` para `deepseek-v4.1-flash` e comparar.

### Fora da fatia
- Input livre digitado.
- Fusão `messages+summaries+eventos` num `ChatTurn` persistido.
- Novo sheet de retrovisor, timestamps e animação de 3 pontos.
- Qualquer mudança de motor, ciência ou schema.

## 5. Gates

- `./gradlew :app:testDebugUnitTest --tests 'com.meucoach.app.ui.coach.*'`
- `./gradlew check` e `spotlessCheck`
- Proxy: `./gradlew -p infra/coach-proxy test`
- `./install.sh` no aparelho + print final (dark/light, fonte 200% se der)
- Sem migração, sem dependência nova, sem dado real no diff.

## 6. Prompt para a próxima sessão à época (histórico)

> Continue o MeuCoach pela feature Coach-chat, fatia **pós-v3 (redeploy + aparelho)**.
> Leia `AGENTS.md`, `docs/PROGRESS.md` (topo) e `docs/HANDOFF_COACH_V3.md`. Estado: v3
> implementada e com gates verdes (dedup de ecos, chip “Retrovisor”, fade suavizado);
> o proxy precisa de redeploy (leva `017a23a` + requebra de linha) e o aparelho precisa
> de `./install.sh` + print da aba Coach. Valide no print: eco único, Retrovisor rolando
> até o prompt, fade legível e acentos (“você”, “então”, “fácil”, “sessões”). Se o GLM
> ignorar os acentos, teste `deepseek-v4.1-flash`. Se validada: F4 (roteiros diários) do
> `PRD_COACH_CHAT`. Não mexa em motor, schema ou prompt para decidir treino; LLM só reescreve.

## 7. Resultado da sessão v3 (20/11 noite)

- `CoachScreen.kt`: `addEcho` com dedup (5 chamadas migradas), `retroY` capturado com
  `onGloballyPositioned` e `onRetro = { scrollTo(retroY) }`.
- `CoachChatComponents.kt`: chip “Retrovisor” (`coach-quick-retro`) após “Explica a semana”;
  fade `0.86f → 0.94f`, spacer `32 → 40 dp`.
- `CoachScreenTest.kt`: 2 regressões novas (eco único; retrovisor rola até o prompt).
- Proxy `Main.kt`: só requebra de linha do `SYSTEM_REPLAN` (spotless; conteúdo idêntico).
- Gates: `ui.coach.*` (135, `--rerun-tasks`), `check`, `spotlessCheck`, proxy `test` — verdes.
- **Validação no aparelho (22:37, via adb):** redeploy feito; explicação regenerada com
  acentos (“Você já fez o longo de 34 min…”, “Fácil e conversável”); duplo toque real no
  “Explica a semana” → 1 eco; chip Retrovisor rola até “Semana passada / Evolução /
  Histórico”; fade legível. Logcat: `explain ok` + `review ok` a cada retry.
