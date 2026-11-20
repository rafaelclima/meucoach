# PRD — Coach como Conversa (chat com treinador)

**Versão:** 0.1 · **Data:** 20/11/2026 · **Status:** proposta para execução
**Base:** `docs/PRD_IA_COACH.md` (contrato IA) + `docs/PRD_EVOLUCAO_COACH.md` (F1–F4) + `docs/VISAO_PRODUTO_IA_COACH_CORRIDA.md` (visão original) + código
**Fonte verdade:** código + `docs/SPORTS_SCIENCE.md`. Este PRD não muda ciência, motor ou schema. Ele muda **a cara da aba Coach**.
**Objetivo:** sair de "dashboard que repete o app" para **"conversa no WhatsApp com meu treinador"** — o feeling planejado desde o início.

---

## 1. Visão em um parágrafo

Abrir o Coach tem que parecer abrir a conversa com o treinador: ele te chama pelo nome, lembra o que você fez, diz o que importa hoje em 2–3 mensagens curtas, pergunta uma coisa de volta e deixa os números/tabelas como anexo expansível. Você responde tocando em uma sugestão (`Perdi o treino`, `Só tenho 30 min`) e isso vira **sua mensagem** na timeline. Nada de overline burocrático, nada de 7 cards iguais, nada de "o motor decide; a IA só reescreve" no corpo. O motor continua decidindo tudo — ele só passa a **falar como gente**.

Exemplo alvo (Rafael, 38, 5k ~31min, Natal, sono curto):

```
[avatar Coach] 07:12
> Bom dia, Rafael. Vi que ontem o longo saiu — 3 de 4 na semana. Boa.
> Hoje era ritmo forte, mas você dormiu 5h40. Bora fazer fácil hoje e guardar o forte?
> [Ver como correr hoje]

[Você 07:14] Só tenho 30 min quinta
[avatar Coach] 07:14
> Fechado. Reorganizo só quinta e sexta, sem mexer no que já passou. Confere aqui?
> [Ver o que muda] [Confirmar] [Agora não]
```

Isso é o teste do feeling: se um print da tela não for distinguível de um chat, falhamos. Se parecer relatório, falhamos.

---

## 2. Diagnóstico — por que hoje não parece conversa

Auditoria completa em 20/11/2026 (`CoachScreen.kt`, 7 cards, `CoachViewModel.kt`, prompts do proxy, labels). Três causas:

### 2.1 Estrutura: dashboard, não timeline

`CoachScreen.kt:149-307` é `Column` + `verticalScroll` com:

`CoachNextCard` → `CoachGuardsCard` → `CoachReplanCard` → explicação → `CoachReviewCard` → `CoachProgressCard` → `CoachHistoryCard` → Desativar IA.

Cada um é `McCard` full-width com overline em caixa alta: `PRÓXIMO TREINO`, `TRAVAS DA SEMANA`, `AJUSTAR A SEMANA`, `EXPLICAÇÃO DA SEMANA`, `REVISÃO DA SEMANA PASSADA`, `EVOLUÇÃO · 4 SEMANAS`, `HISTÓRICO DO COACH`. Nenhum chat no mundo usa overline. Chat usa **turnos alternados, avatar, hora, bolha**.

`CoachHistoryCard.kt:141-167` divide em `Resumos por semana` + `Conversa`, sem a mensagem atual (excluída em `CoachViewModel.kt:224-230`). Arquivo morto, não conversa contínua.

### 2.2 Voz: sistema, não treinador

Header: "Sua semana, a revisão anterior e os ajustes — com o motor no comando." Terceira pessoa, institucional.

Disclaimers repetidos 3x na tela:

- `CoachNextCard.kt:248` "O motor decide; a IA só reescreve."
- `CoachExplanationCard.kt:85` "A IA só reescreve o plano do motor; nenhum número nasce daqui."
- `CoachReviewCard.kt:185` "Resumo da semana fechada. Sem nota, streak ou cobrança."

`SYSTEM_EXPLAIN` (`infra/coach-proxy/.../Main.kt:118`): "Voce e o Coach MeuCoach, pt-BR, tom de treinador sem culpa. Reescreva apenas o rationale... Responda em JSON {title, body, bullets[]}". Isso força dissertação (`title` + `body` longo + `bullets`), não mensagem. Sem regra de brevidade, 1ª/2ª pessoa, pergunta final, nome do corredor.

Fallbacks (`CoachFallbackLabels.kt` + `CoachScreen.kt:229-256`): "Mostrando o resumo do motor." / "A explicação citou uma duração fora do plano." Log de validador, não fala.

### 2.3 Conteúdo: repetição literal de Hoje/Tendências

- Next = mesma decisão do Hoje, mesmos `blockCopy/unlockCopy` (`CoachNextLabels.kt`).
- Guards = travas que o Hoje já explica em "Por que este treino?".
- Progress = adesão/volume/80-20/sono/VDOT que o Tendências já mostra (`CoachProgressLabels.kt`).
- Review = `WeeklyReview` completo re-renderizado (`CoachReviewCard.kt:94-166`).

E você não fala: sem input livre (fora do MVP por decisão), sem quick-reply que vire bolha sua. `Perdi treino` / `Menos tempo` vivem em `CoachReplanCard.kt` como formulário, não como "você mandou isso".

**Conclusão:** mesma forma (McCard), mesma voz (sistema), mesmo conteúdo (espelho). Por isso "é mais do mesmo".

---

## 3. Princípios inegociáveis (o que NÃO muda)

1. **Motor decide, IA só reescreve.** Nenhum pace, FC, carga, volume, VDOT ou plano nasce da resposta. `CoachResponseValidator` continua porteira; `Invalid` → fallback. Sem exceção para "parecer mais humano".
2. **`core:domain` puro.** Novo código de conversa é puro (`CoachChatMapper`, `CoachPersona`), sem Android/Room/Hilt/rede. UI não calcula regra de treino.
3. **Sem migração, sem dependência nova.** Reuso de `TrainingPlanRepository`, `DailyPrescription`, `WeeklyReviewAssembler`, `PlanCompletionCalculator`, `ReplanController`, `coach_messages/coach_summaries` (v19). Sem `fallbackToDestructiveMigration`.
4. **Dado ausente é `null`, nunca `0`.** Ausência reduz confiança, não vira cobrança.
5. **Sem culpa, sem diagnóstico, sem prescrição fora do escopo.** Sem musculação/nutrição/emagrecimento/diagnóstico. Exceção única: hidratação estática do ADR 0029, sem números.
6. **Privacidade.** Opt-in explícito, payload minimizado (sem nome/GPS/timestamps/texto livre), TTL 90/180, revogar apaga, sem payload em log/teste/repo.
7. **Design system.** Dark-first + light completo, lima `#D8FF3E` só para ação sua/CTA, tokens de `core:designsystem`, sem hex solto, números tabulares, contraste AA, alvos 48dp, TalkBack, fonte 200%.

---

## 4. Persona — quem fala com você

### 4.1 Identidade

- **Nome:** Coach. Avatar: marca MeuCoach (não foto genérica, não emoji).
- **Relação:** seu treinador que te acompanha todo dia. Conhece seu nome (Rafael), seu nível, seu calor em Natal, seu sono curto, sua rotina de esteira.
- **Papel:** traduz o motor para linguagem de corredor, protege você do exagero, celebra sem bajular, cobra sem culpar.

### 4.2 Tom (normativo)

1. **2ª pessoa, 1ª pessoa do plural.** "você", "a gente", "bora". Proibido 3ª pessoa institucional ("o motor", "a revisão", "o resumo").
2. **Curto.** Cada bolha ≤ 140 caracteres, 1 ideia por bolha, máx. 3 bolhas seguidas antes de uma ação/pergunta. Texto longo vira anexo expansível, nunca bolha gigante.
3. **Uma pergunta por vez.** Toda sequência do coach termina em pergunta ou ação tocável ("Bora fazer fácil hoje?" / "Confere aqui?" / "Como foi o sono?").
4. **Fato + motivo + convite.** "Dormiu 5h40 (fato), por isso sem forte hoje (motivo), bora no fácil? (convite)". Nunca só fato, nunca só ordem.
5. **Nunca culpa.** "Perdi treino" → "Acontece. A gente ajusta." Proibido: falhou, perdeu, atrasado, deveria, streak, cobrança, "você precisa".
6. **Números com unidade e consequência.** "30 min", "3 de 4 treinos", "5h40 de sono — abaixo de 6h, por isso leve hoje". Proibido sigla nua (ACWR, VDOT, CTL) no corpo; detalhe técnico fica no anexo com explicação (`docs/COMMUNICATION.md` §6.5, `docs/GLOSSARY.md`).
7. **Calor de Natal é contexto, não desculpa.** Pode citar ("com esse calor, hidrata..."), nunca como prescrição médica/número (seguir §6.10 do COMMUNICATION + ADR 0029).
8. **Emoji: máx. 1 por sequência, só em celebração.** Proibido em alerta, erro, prova, doença.

### 4.3 Antes / depois (contrato de copy)

| Antes (sistema) | Depois (treinador) |
|---|---|
| PRÓXIMO TREINO / Hoje · Ritmo forte · 35 min / Mantido como planejado. Qualidade bloqueada: sono curto. | Bom dia, Rafael. Hoje era forte, mas dormiu 5h40. Bora no fácil hoje? [Ver como correr hoje] |
| TRAVAS DA SEMANA / Nenhum bloqueio ativo — siga o plano. | Semana limpa, nada te travando. Bora seguir o plano? |
| AJUSTAR A SEMANA / Se algo mudou, o Coach reorganiza... Nada muda sem você confirmar. [Ajustar] [Perdi treino] [Menos tempo] | (vira suas mensagens) Você: "Perdi o treino de ontem" / "Só tenho 30 min quinta" |
| EXPLICAÇÃO DA SEMANA / [title] + [body 5 linhas] + A IA só reescreve... | Essa semana é base: 3 fáceis + 1 forte curto. O longo cresce devagar por causa do calor. Fechado? [Detalhes do plano] |
| REVISÃO DA SEMANA PASSADA / Volume: X planejados · Y feitos... O que o motor registrou... | Semana passada: 3 de 4. O longo saiu bem controlado, o forte ficou pra trás. Acontece — a gente mantém, sem tentar compensar. [Ver números] |
| EVOLUÇÃO · 4 SEMANAS / Planejado X · feito Y (Z%)... Sem nota, streak ou cobrança. | De 4 semanas, deu 3 boas. Volume subindo devagar, 82% fácil. Tá no caminho. [Ver tendências] |
| A explicação não passou na revisão do motor. Mostrando o resumo do motor. | Ih, me enrolei pra explicar. O plano segue o mesmo — te mostro direto? [Ver plano] |
| O motor decide; a IA só reescreve. | (some do corpo; vira info "ℹ Como funciona o Coach" no header, uma vez) |

---

## 5. Modelo de conversa (puro, testável)

### 5.1 `ChatTurn` — a unidade da tela

```kotlin
sealed interface ChatTurn {
  data class Coach(val id: String, val bubbles: List<String>, val time: Instant, val attachment: EngineAttachment?) : ChatTurn
  data class You(val id: String, val text: String, val time: Instant) : ChatTurn
  data class System(val id: String, val text: String) : ChatTurn // "Semana ajustada", "Check-in feito"
}
sealed interface EngineAttachment {
  data class Next(val headline: String, val status: String, val cta: Cta) : EngineAttachment
  data class PlanDetail(val lines: List<String>, val rationale: String) : EngineAttachment
  data class Numbers(val rows: List<String>) : EngineAttachment // adesão/volume/sono — expansível
  data class Diff(val change: ReplanChange) : EngineAttachment
}
```

`CoachChatMapper.map(next, guards, explanation, review, progress, replan, history) -> List<ChatTurn>`: puro, em `core:domain/coach/`. Ordena por tempo, agrupa em sequências (máx. 3 bolhas), decide o que é bolha vs anexo. Nenhum cálculo de treino aqui — só composição de texto já validado.

### 5.2 O que vira bolha vs anexo

- **Bolha:** só texto validado (`Valid`) ou copy determinística curta do mapper. É a voz.
- **Anexo expansível:** todo número/tabela/diff/rationale completo. É a prova. Texto do botão sempre honesto: "Ver números", "Detalhes do plano", "Ver o que muda". Nunca "Ler explicação completa" (tom de relatório).

Regra: se precisa de unidade para entender, é anexo. Se dá para decidir sem abrir, é bolha.

### 5.3 Ordem narrativa (não mais ordem de cards)

1. **Abertura do dia** (sempre primeiro): saudação + o que importa hoje + 1 ação. Deriva de F1 (prescrição vigente ou planejado rotulado) + prontidão/sono/check-in.
2. **Sua última ação** (quando houver): "Você: Perdi o treino de ontem" + resposta do coach + diff/confirmar.
3. **Contexto da semana** (colapsado por padrão): 2 bolhas da explicação + anexo "Detalhes do plano".
4. **Retrovisor** (1x/semana ou sob demanda): revisão em 2 bolhas + anexo "Ver números". Nunca junto com abertura — só após scroll ou via quick-reply "Como foi semana passada?".
5. **Evolução** (sob demanda): 1 bolha síntese + anexo + link Tendências. Nunca empilhada com revisão.
6. **Arquivo:** histórico fundido (mensagens + resumos + eventos seus) como continuação da timeline, não seção separada.

---

## 6. UX — spec da tela chat

### 6.1 Header de conversa (não mais título de relatório)

- Avatar Coach + "Coach" + status: "motor no comando · responde na hora" (online local) / "sem conexão — mostro o plano" (offline/fallback). Sem "Sua semana, a revisão anterior e os ajustes".
- Ações: `ℹ` abre sheet "Como funciona o Coach" (motor decide, IA reescreve, o que vai pra IA, TTL, apagar) — **lugar único** dos 3 disclaimers atuais. Engrenagem global mantém comportamento.
- TalkBack: header é `heading()`, status é anunciado.

### 6.2 Timeline

- `LazyColumn` reversa ou normal com auto-scroll para o novo? **Normal, top-down, com âncora na abertura do dia** (WhatsApp abre no fim; aqui abre no "hoje" e histórico sobe). Nova mensagem do coach anima entrada (`motion/base` 220ms, respeita reduced motion).
- Bolha coach: `surface/elevated`, raio 20dp (cantos desiguais estilo chat), texto `body/md`, hora `label/sm tertiary` dentro da bolha. Bolha sua: `accent/lime` com texto `inverse`, alinhada à direita, máx. 80% largura.
- "Digitando…" (`coach-typing`): 3 pontos animados quando `Loading`. Substitui os 4 skeletons atuais.
- Data divider: "Hoje", "Ontem", "12/11" — agrupa turnos, evita repetir hora em tudo.
- Prova tem precedência visual: dia de prova vira bolha própria com ícone de prova, sem treino junto ("Hoje é dia de prova. Sem treino — boa prova!"), seguindo `GetTodayDecision`.

### 6.3 Quick-replies (sua voz, sem digitar ainda)

Chips horizontais acima do composer, sempre visíveis:

`Explica minha semana` · `Como foi semana passada?` · `Perdi um treino` · `Só tenho 30 min` · `Ajustar a semana`

Tocar vira imediatamente `ChatTurn.You` ("Perdi o treino de ontem") + dispara o fluxo existente (`requestMissedSession`, `replanLessTime`, etc.). Reuso total de `ReplanController`, pickers (`MissedSessionPickerSheet`, `LessTimeSheet`), diff (`ReplanSuggestionSheet`), undo. Nenhum motor novo.

### 6.4 Composer

- MVP deste PRD: campo desabilitado com hint "Em breve você poderá digitar — use as sugestões acima". Alvo 48dp, TalkBack explica. Não fake-ativa.
- Pós-MVP (fora deste PRD): texto livre vira só `intent` (dia/minutos/treino perdido) para `PlanReplanner`. Nunca número novo. Validador continua barrando.

### 6.5 Estados

| Estado | Chat mostra |
|---|---|
| Loading inicial | "Digitando…" + abertura do dia em skeleton de bolha |
| Sem plano/perfil (onboarding) | 1 sequência: "Opa, Rafael. Pra eu montar seus treinos, preciso de 3 coisas..." + [Abrir perfil] [Calibrar] [Importar] como botões dentro da bolha. Uma única sequência, não 5 cards contraditórios. |
| Semana concluída | "Fechamos a semana. Descansa que segunda eu trago a próxima." — nunca "Semana concluída." seco. |
| Erro local (banco) | Bolha sistema: "Não consegui carregar aqui. [Tentar de novo]" — não derruba a timeline. |
| Sem IA / offline / 4xx / schema inválido | Coach continua falando determinístico; sem "Sem conexão com o Coach" como erro. Fallback vira fala: "Ih, me enrolei... O plano segue o mesmo." + anexo com rationale. Nada de `reason` técnico na UI (mata o vazamento `atl`/`código interno`). |
| Revogar | Timeline some (Hidden), volta para onboarding. Apagar em `appScope` (preservar). "Desativar IA" sai do fim da tela e vira ação no `ℹ` + fallback neutro. |
| Apagar histórico | Sheet confirma; por item vira long-press na bolha (padrão chat) + "Apagar" no detalhe. Mantém 48dp, `contentDescription` próprio. Nada de lixeira vermelha competindo com conteúdo. |

### 6.6 Visual (design system)

- Bolhas usam `surface/elevated` (coach) e `accent/lime` (você). Borda 1dp `border/subtle`. Sem hex solto, sem dynamic color.
- Tipografia: bolha `body/md` (14/20), hora/meta `label/sm`, números em anexo com `tnum`.
- Lima: só sua bolha + CTA primário + quick-reply selecionado. Coral: só dado fisiológico em anexo, nunca em botão.
- Dark-first, light completo. Fonte 200% sem corte (bolha quebra, não corta). Contraste AA. TalkBack: cada sequência é um grupo, hora lida por último, anexo anunciado como "detalhe expansível".

---

## 7. Conteúdo por momento (roteiros)

### 7.1 Abertura diária (o 80% do valor)

Deriva de F1 + prontidão + check-in + sono. Exemplos:

- Verde + check-in ok: "Bom dia, Rafael. Hoje tem fácil 40 min. Sono 7h10, prontidão alta. Bora? [Ver como correr hoje]"
- Âmbar + sono curto: "Hoje era forte, mas dormiu 5h40. Bora no fácil e guardar o forte? [Ver como correr hoje]"
- Sem check-in: "Falta seu check-in de 15s pra eu liberar o forte. [Fazer check-in]"
- Prova hoje: "Hoje é dia de prova. Sem treino — boa prova! [Ver prova]"
- Amanhã (véspera): "Amanhã tem ritmo forte 35 min. Dorme bem hoje que conta. [Ver plano]"

Proibido: mostrar planejado como se fosse prescrito. Sem prescrição, rotula "Planejado:" e diz "a confirmação sai no check-in da manhã".

### 7.2 Pós-check-in / pós-treino / pós-import

Vira `System` + resposta curta do coach (local, sem IA nova):

- Check-in feito: System "Check-in feito ✓" + Coach "Valeu. Com sono 7h, o forte de hoje tá liberado. [Ver como correr]"
- Treino concluído: "Longo feito. Boa, manteve fácil o tempo todo. [Ver atividade]"
- Import: "Recebi sua corrida. Já entrou na carga. [Ver tendências]"

### 7.3 Revisão semanal (retrovisor, não topo)

Dispara segunda-feira ou via "Como foi semana passada?". 2 bolhas + anexo:

"Semana passada: 3 de 4. O longo saiu bem, o forte ficou pra trás. Acontece."
"A gente mantém o volume, sem tentar compensar. Fechado? [Ver números]"

Anexo tem adesão/volume/prontidão/sono — mesmos números do `WeeklyReview`, sem recitar na bolha. Se IA responder genérica e omitir sinal de segurança (doença/sono/adesão), o anexo determinístico continua visível (corrige `CoachReviewCard.kt:168-183` que hoje esconde observações quando há Ready).

### 7.4 Replanejo (sua mensagem, resposta com diff)

Você: "Só tenho 30 min quinta" → Coach: "Fechado. Mexo só quinta e sexta, sem repor carga. [Ver o que muda] [Confirmar] [Agora não]" → System: "Semana ajustada. O que já passou não mudou. [Desfazer] [Ver o que mudou]"

Copy preserva `COMMUNICATION.md` §6.7 (convite, não correção; hoje/passado intactos; sem "falhou/perdeu").

---

## 8. Prompts da IA — mesma porteira, nova voz

### 8.1 O que muda

`SYSTEM_EXPLAIN` e `SYSTEM_REVIEW` passam a exigir **fala de mensageiro**, não redação. Contrato técnico (`title/body/bullets` + validador + `allowedMinutes/allowedPercents` + `forbiddenCodes` + `SCREAMING_SNAKE`) permanece. Só a instrução de estilo muda.

Rascunho (proxy, sem log de payload):

```
Voce e o Coach do Rafael, treinador de corrida, pt-BR, sem culpa.
Reescreva APENAS o rationale do motor. Nunca invente pace, FC, carga, volume, VDOT, ACWR.
Fale em 2a pessoa ("voce"), curto, como WhatsApp: max 3 mensagens, cada uma <= 140 chars.
Primeira mensagem: o que importa (fato + motivo). Segunda: detalhe se precisar. Ultima: pergunta ou acao ("Bora?", "Fechado?", "Confere aqui?").
Nunca cite siglas (ACWR, VDOT, TSB...), codigos (R-..., EV-..., CAIXA_ALTA), nem diga "o motor/o resumo/a revisao".
Dado ausente: diga que faltam dados, sem estimar numero.
Responda em JSON {"title":"...","body":"...","bullets":[]}: title = primeira mensagem, body = continuacao em ate 2 paragrafos curtos, bullets = [] ou ate 3 anexos curtos.
title/body strings, nunca objeto.
```

`SYSTEM_REVIEW` igual + "comece pelo feito ('3 de 4'), cite o longo/qualidade sem culpar, feche sem prometer compensação".

### 8.2 Validador

- Mantém `allowedMinutes/allowedPercents/forbiddenTokens/forbiddenCodes`. Adiciona limite de tamanho (bolha ≤ 280 chars após split; se passar, `Invalid` → fallback fala curta determinística).
- `reason` técnico **nunca vai para UI**. `CoachFallbackLabels` vira fala do coach (tabela §4.3). Mata o vazamento `atl` ⊂ "atleta" na superfície (o fix real do token continua necessário no domínio).

---

## 9. Dados e integração (sem migração)

Reuso total, só composição nova:

- `CoachNextSource` → abertura do dia (prescrição vigente + `RaceEvent` com precedência, `ReadinessResult`, check-in, sono).
- `CoachGuardsSource` → anexo "Por que hoje é fácil?" (não mais card fixo).
- `CoachPlanSource` → contexto da semana (explicação) + anexo plano.
- `CoachReviewSource` + `WeeklyReviewAssembler` → retrovisor.
- `CoachProgressSource` → síntese sob demanda + link Tendências.
- `ReplanController` → aceite/diff/undo/stale (mesma máquina de Hoje/Perfil).
- `coach_messages/coach_summaries` → bolhas antigas; novo `ChatTurn.You` é **efêmero local** (ação do usuário) + `System` para eventos — sem coluna/tabela nova nesta fatia. Se persistir input no futuro, vira ADR + migração aditiva.

Novo: `CoachChatMapper` (puro) + `CoachPersona` (copy) + `CoachChatLabels` (fala curta por motivo, espelhando `CoachNextLabels`/`CoachGuards` sem expor `unlockHint` cru nem sigla). `CoachViewModel` ganha `chat: StateFlow<List<ChatTurn>>` derivado dos mesmos flows (com `combine`), sem nova dependência de rede. `retry()` continua global; erro de bolha não dispara IA nova.

---

## 10. Fases (uma fatia pequena e verificável por sessão)

### F0 — PRD + persona travados (esta fatia)

- Entrega: este doc + exemplos de copy aprovados pelo usuário 0.
- Gate: sem código, sem teste quebrado.

### F1 — Timeline visual (maior impacto no feeling)

- Troca `Column` de `McCard` por `LazyColumn` de bolhas + header de conversa + typing + data dividers + quick-replies que emitem `ChatTurn.You` e chamam fluxos existentes. Conteúdo ainda é o mesmo (next/guards/explain/review/progress), só re-embalado. Disclaimers somem do corpo e vão para `ℹ`.
- Preserva tags antigas como alias ou atualiza `CoachScreenTest` junto. Dark/light + 200% + TalkBack.
- Concluída quando: print parece chat; build sem token abre; `assembleDebug` + `CoachScreenTest` verdes.

### F2 — Voz nova (persona + fallback falado)

- `CoachPersona` + `CoachChatLabels` + `CoachFallbackLabels` como fala + `SYSTEM_EXPLAIN/REVIEW` reescritos (redeploy proxy manual). Validador com limite de tamanho + `reason` nunca na UI.
- Concluída quando: explicação/revisão chegam em 2–3 bolhas curtas com pergunta final; fallback soa humano; nenhum código interno na tela.

### F3 — Timeline consistente (revisada após auditoria no aparelho, 20/11/2026)

**Status 20/11/2026:** F3 e F3-bolhas implementadas; falta `./install.sh` + validação visual no aparelho (fonte 200%, dark/light, "Ver números").

**Achado da auditoria:** a ordem do feed já era temporalmente coerente
(agora → semana → passada → 4 semanas → arquivo). As 4 quebras reais eram
a camada interativa, não a ordem: ecos órfãos no meio do feed, chips que
somem ao rolar, "Desativar IA" no meio da conversa e histórico pesado.
A F3 como construída:

- **Feed rolável** (mesma ordem F1/F2, sem Desativar): abertura → próximo →
  travas → replan (+mensagem) → explicação → revisão → progresso →
  histórico. Header rola junto (cromo fixo mínimo = rodapé).
- **Rodapé fixo:** último(s) eco(s) seu + `digitando…` + quick replies em
  linha rolável + composer. Sempre visíveis, em qualquer altura.
- **Eco-ponte:** tocar chip mostra sua mensagem no rodapé e rola até o
  destino (`bring-into-view` na explicação/revisão) ou abre o fluxo
  (pickers do replan). Pergunta e resposta adjacentes de verdade.
- **"Desativar IA"** sai do feed e vira ação destrutiva dentro do sheet
  "Como funciona" (com confirmação implícita pelo contexto do sheet).
- **Histórico colapsado:** mostra 1 resumo + 2 turnos recentes +
  "Ver conversa completa (N)" / "Mostrar menos". Mesmas exclusões
  (sem repetir explicação corrente nem revisão visível) e mesmo
  apagar em bloco/item.
- **Robustez:** cromo fixo enxuto (só rodapé) para nunca espremer o feed
  a zero em fonte 200%/telas pequenas (regressão coberta por teste).

Fora desta fatia (observar 1 semana de uso): unificar card replan ×
quick replies (duplicação "Perdi treino"/"Perdi um treino"); fundir
`messages+summaries+eventos` num `ChatTurn` único persistido.

### F3-bolhas — implementada em 20/11/2026 (pós-captura no aparelho)

O chrome já era chat, mas o corpo continuava relatório: `McCard` com ~400 chars
corridos e tabelas de números. A UI passou a impor o formato (prompt não basta):

- **Bolhas:** `CoachBubbles.split` puro (frases ≤140, quebra em palavra inteira,
  pergunta final sozinha, whitespace normalizado). Título/corpo de explicação e
  revisão viram `CoachBubbleSequence` (avatar só na primeira bolha).
- **Anexos:** review e progresso colapsam adesão/volume/prontidão/sono/agregados
  em um "Ver números" por seção; nenhum número some. Observações da revisão
  aparecem no anexo mesmo com `Ready` (sinal de segurança nunca é escondido).
- **Fallback:** só a fala do `CoachFallbackLabels` + "Tentar de novo"; a linha
  duplicada e o "Desativar IA" saem do feed (ação fica no "Como funciona").
- **Chrome:** status "motor no comando" (estado da IA no sheet), chips com
  end-padding, histórico sem "Conversa" duplicada, composer em uma linha.
- **Testes:** `CoachBubblesTest` + `CoachBubbleCardsTest` (≤140, "Ver números",
  observações, 200%, light) e regressões de tela/histórico.

Fora desta fatia: input livre digitado; fusão `messages+summaries+eventos` num
`ChatTurn` persistido.

### F4 — Roteiros diários (abertura/pós-evento)

- Abertura do dia como primeira sequência + system turns (check-in/treino/import) + prova com precedência + véspera de amanhã. Deep-links: Ver como correr → briefing, Ver Tendências, Fazer check-in.
- Concluída quando: usuário 0 abre 5 dias seguidos e decide sem ir ao Hoje; teste moderado "explique por que hoje é fácil" ≥80%.

Fora deste PRD: input livre digitado, memória como contexto de prompt, "quero mais tempo", iniciativa 1/dia, simulador "e se?". Só após F1–F4 validados no aparelho.

---

## 11. Testes (TDD, sem dado real)

- **Domínio puro:** `CoachChatMapperTest` (agrupa 3 bolhas, corta longo para anexo, ordena por tempo, prova vence treino, sem plano → onboarding, dado ausente → null), `CoachPersonaTest` (≤140 chars, termina em pergunta/ação, sem sigla/código/3ª pessoa).
- **Validador:** texto longo → `Invalid`; sigla/código/número fora do permitido → `Invalid`; `reason` nunca contém dado de treino.
- **VM/UI:** `CoachChatViewModelTest` (quick-reply emite `You` + chama controller, retry não duplica IA, revogar esconde), `CoachChatScreenTest` (bolhas alternadas, typing, quick-reply, anexo expansível, 200%, dark/light, TalkBack ordem topo→ação).
- **Integração:** sources existentes com banco em memória; `MigrationTest` segue v19; sem nova migração.
- **Gates:** `./gradlew :app:assembleDebug`, `check`, `spotlessCheck`, `koverVerify`, `lintDebug`, `verifyRoborazziDebug` para o novo chat. Sem `fallbackToDestructiveMigration`, sem dado real.

---

## 12. Critérios de aceite (usuário 0, no aparelho)

1. Abrir o Coach e em ≤10s saber o que fazer hoje/amanhã e por quê, sem abrir o Hoje.
2. Explicar em voz alta por que hoje virou fácil (sono/check-in/carga) — meta ≥80% (`PRODUCT.md` §6).
3. Tocar "Só tenho 30 min", ver sua mensagem na timeline, conferir diff, confirmar e desfazer.
4. Com token vazio/offline: conversa continua determinística, sem JSON, sem código interno, sem spinner infinito.
5. Revogar: histórico some, onboarding em 1 sequência, nenhum treino/perfil apagado.
6. Print da tela é indistinguível de um chat: bolhas, avatar, hora, pergunta final. Sem overline, sem "mostrando o resumo do motor".

---

## 13. Riscos

| Risco | Mitigação |
|---|---|
| Bolha curta esconder nuance do motor | Anexo expansível sempre com números/links; bolha nunca contradiz anexo |
| IA verbosa quebrar limite | Validador barra + fallback falado; redeploy do proxy manual no Dokploy |
| Falsa sensação de "ele lembra tudo" | `ℹ` declara TTL/memória local; sem prometer memória longitudinal |
| Duplicar Hoje/Tendências de novo | Regra: Coach conta história + linka; número mora no anexo ou na aba de origem |
| Quebrar 1100+ testes/tags | Manter tags como alias na F1, migrar testes juntos, uma fatia por sessão |
| LGPD retenção/região | Risco já aceito no PRD IA Coach; revisitar antes de release além do usuário 0; `You` efêmero não vai para prompt nesta fatia |

---

## 14. Decisões registradas (20/11/2026)

- **Formato:** PRD único (`docs/PRD_COACH_CHAT.md`), não pasta. Referencia `PRD_IA_COACH`, `PRD_EVOLUCAO_COACH`, `COMMUNICATION`, `DESIGN_SYSTEM`; não os reescreve.
- **Feeling alvo:** "Estou conversando com um treinador" (WhatsApp), pedido do idealizador desde o início. Complexidade/tempo não são limite; qualidade da conversa é.
- **Autoridade:** motor decide, IA só reescreve. Nenhum número novo via IA, mesmo para soar humano.
- **Próxima fatia:** F1 timeline visual atrás do conteúdo atual, sem mudar prompts/proxy. Voz nova (F2) só após F1 validada no aparelho.
