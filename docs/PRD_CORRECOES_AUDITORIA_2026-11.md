# PRD — Correções da auditoria geral 2026-11

**Versão:** 0.1 (proposta para aprovação)  
**Data:** 26/11/2026  
**Status:** aguardando aprovação explícita do usuário 0. Nenhum código foi alterado nesta fase.  
**Fontes primárias:** `docs/AUDITORIA_GERAL_2026-11.md`, `docs/REVISAO_AUDITORIA_2026-11.md`, código em `core/domain`, `core/data`, `:app` e `infra/coach-proxy`, `docs/SPORTS_SCIENCE.md`, `docs/PRODUCT.md`, `docs/ARCHITECTURE.md`, `docs/TESTING.md`, `docs/ROADMAP.md`, `docs/PROGRESS.md`, `docs/HANDOFF_IA_COACH.md` e os PRDs/ADRs citados.  
**Regra desta fase:** transformar achados em decisões e critérios de aceite. Não implementar, não atualizar `PROGRESS.md`/`ROADMAP.md` e não alterar os dois documentos de auditoria.

> Este PRD não trata a auditoria original como especificação. Cada achado recebe um veredito próprio, uma severidade reavaliada e, quando aplicável, uma decisão de produto/ciência separada da correção de código. Achados refutados ou não verificáveis **não** viram requisito por aparecerem no documento original.

---

## 0. Resumo executivo

### 0.1 O que a revisão encontrou

Dos **84 achados originais classificados** (excluídos os quatro contrapesos positivos `ALG-10`, `SCI-04`, `STORY-19` e `COACH-19`):

| Veredito | Quantidade |
|---|---:|
| Confirmado | 28 |
| Fato confirmado com inferência/severidade ajustada | 53 |
| Refutado por evidência | 1 (`V-3`) |
| Não verificável por falta de aparelho/escopo | 2 (`STORY-06`, `GEN-05`) |

A revisão também encontrou **9 achados novos**, todos com fato confirmado por leitura de código ou execução sintética. Este PRD lhes dá IDs `NOVO-*` para rastreabilidade.

### 0.2 Decisão executiva proposta

A prioridade **não** é a mesma da auditoria original. A proposta é:

1. **P0 — segurança, privacidade e escopo médico (implementar primeiro):**
   - `COACH-13`: corpo de erro do upstream não pode chegar ao `IOException`/logcat.
   - `COACH-05`: revogar o Coach limpa o estado visível e não deixa “digitando…” eterno.
   - `COACH-06`: retry precisa mirar o `exchangeId` acionado, não a última pergunta.
   - `NOVO-COACH-01`: pergunta sobre arritmia/condição médica não pode receber “é seguro”; falhar fechado.
   - `COACH-09`: recusa de dor/lesão/doença com próximo passo útil, sem diagnóstico.

2. **P1 — correção do motor e do validador:**
   - `ALG-01`, `NOVO-ALG-01`, `NOVO-ALG-02`, `ALG-07`: disponibilidade é teto em **todos** os caminhos, inclusive micro-dose de taper, strides e fallback `NO_PLAN`.
   - `ALG-03`: progressão de volume e copy derivadas do volume **entregue**, sem largar uma sessão inteira para caber 1 minuto.
   - `COACH-10`/`U-1`/`U-3`: allow-lists numéricas explícitas no explain/review, incluindo percentuais, distâncias e decimais.

3. **P1/P2 — decisões científicas/documentais, com ADR antes de código:**
   - `NOVO-SCI-01`: taper sobre o maior volume recente × média de três semanas de `SPORTS_SCIENCE §8.6`.
   - `NOVO-SCI-02`: longão de iniciante subindo 45 → 62 min contra `§8.5` (5–10 min/semana).
   - `ALG-02`: qualidade na segunda após longão no domingo anterior; o documento proíbe a direção oposta, não esta.
   - `SEG-03`: iniciante sem strides/micro-dose no taper; “Todos” de `§8.4` é ambíguo.
   - `NOVO-LVL-01`: o app descarta `Stay` e os critérios faltantes da graduação.

4. **P2 — documentação e UX selecionada:** reconciliar `PRODUCT.md` v1.3, status da Fase 3, critérios de saída, handoffs, prontidão sem dados, estados da prévia do Story, cancelamento em `PreparingMap`, testes de 200% e vínculo do veredito do Story à atividade.

5. **P3 / explicitamente fora desta rodada:** multi-turn real, streaming, feedback por mensagem, hidratação do feed do Coach, refatoração do `CoachViewModel`, diferenciação estrutural de avançado, player Tier C, restauração de backup, pôster avulso, scrubber do Story, CTA pós-importação e demais sugestões de produto. Não são bugs e não entram automaticamente.

### 0.3 O que não será feito

- Não implementar todas as sugestões da auditoria.
- Não transformar `ALG-02`, `NOVO-SCI-01`, `NOVO-SCI-02` ou `SEG-03` em nova regra fisiológica sem ADR, evidência e atualização de `SPORTS_SCIENCE.md`.
- Não declarar violação de 48 dp em `McTextButton` sem medir o alvo final; o componente usa Material 3.
- Não usar o valor publicado do E16 (178 min) como teste, porque a entrada exata não foi reconstruída.
- Não usar “Corra 5 mil metros” como teste que deveria passar; esse exemplo já é barrado pela guarda de verbo.
- Não usar dados de saúde reais em testes, fixtures, prints ou logs.

---

## 1. Método, evidência e limites

### 1.1 O que foi reproduzido

A revisão executou um harness temporário (removido ao final) com dados sintéticos e o motor real. Foram reproduzidos:

- `ALG-01`: `E14` (10 min → sessão fácil de 20 min) e `E18` (20 min → qualidade de 25 min).
- `NOVO-ALG-01`: slot de 10 min em taper → micro-dose de 25 min.
- `NOVO-ALG-02`: fallback diário sem plano com 10 min → 20 min.
- `ALG-03` genérico: em cenário sintético diferente do E16, a semana entregou **188 min** com base 190 e alvo 201; o E16 publicado (178 min, base 190, alvo 201) **não foi reproduzido** com os inputs visíveis.
- `ALG-07`: strides com 10 min → `structure = null`.
- `ALG-09`: `loadTrusted` diferente entre `WorkoutRecommender` e `DailyPrescriptionEngine` com carga só-subjetiva.
- `SEG-01`: iniciante ≡ intermediário nas primeiras semanas de BASE; avançado muda o longão.
- `SEG-03`: iniciante em taper sem strides/micro-dose; `easyTemplates(TAPER)` só “Rodagem”.
- `COACH-10`/`U-1`: explain aceitou “85%”, “10 km” e “99 sessões” não autorizados.
- `NOVO-COACH-01`: pergunta sintética sobre arritmia foi roteada para `ASK_COACHING`, e “Correr com arritmia é seguro para você” passou no validador.
- `U-3`: “12,5 unidades” passou; o exemplo “12,5%” não foi reproduzido como under-rejection.
- `U-5`: “Troque o intervalado por uma rodagem leve” pode passar.
- `COACH-05`, `COACH-06`, `COACH-13`: confirmação direta no código.
- `STORY-03`, `STORY-04`, `STORY-16`, `STORY-17`: confirmação por leitura de código/testes.
- `NOVO-STORY-01`: `StorySessionResolver` associa a sessão planejada apenas pela data, sem verificar vínculo da atividade.
- `NOVO-LVL-01`: `LevelGraduationViewModel.present` converte `Stay` em “nada a mostrar”, descartando `missing`.
- `NOVO-SCI-01`: `VolumeProgression` usa o maior volume forte recente; `§8.6` descreve média de três semanas.
- `NOVO-SCI-02`: longão de iniciante 45 → 62 min em cenário sintético.

### 1.2 O que não foi reproduzido

- **E16 exato (178 min).** A receita visível produziu 201 min. O problema de subentrega foi reproduzido em outro cenário (188 vs 190), mas o valor publicado não deve ser congelado em teste.
- **“12,5%” como under-rejection.** O texto “12,5 unidades” passou; percentuais fracionários seguem outra regex e podem ser recusados como `5%`. O requisito é tratar decimais de forma explícita, não reproduzir o exemplo original.
- **“Corra 5 mil metros” como under-rejection.** A guarda de verbo + número já barra. O requisito é cobrir formulações declarativas sem verbo, se a decisão de endurecimento for aprovada.
- **48 dp do `McTextButton`.** Padding 8×4 e ausência de `minHeight` explícito são fatos; o alvo final não foi medido. Material 3 tem expansão de alvo interativo; sem medição, não há violação declarada.
- **Validação física no aparelho.** Não houve aparelho/emulador nesta revisão. `GEN-05` permanece não verificável.

### 1.3 Regra de evidência para os testes de regressão

- Todo teste novo usa **dados sintéticos**, relógio fixo e `TimeProvider`/`Clock` injetado.
- Nenhum teste depende de rede, aparelho, arquivo pessoal ou `fixtures/private/`.
- Testes de motor ficam em `core:domain`; testes de API/privacidade em `core:data`; testes de ViewModel/UI em `:app`.
- Teste de bug deve falhar antes da correção e passar depois.
- Onde a causa é uma **propriedade** (ex.: “nenhuma sessão excede a disponibilidade”), o teste é uma matriz/property, não um único valor publicado.
- Golden novo só entra se provar algo que os testes de contrato não provam; goldens existentes não são regravados sem justificativa e revisão visual.

---

## 2. Classes de veredito e prioridade

| Classe | Definição | Consequência neste PRD |
|---|---|---|
| **BUG CONFIRMADO** | Comportamento observado contraria contrato documentado ou teste reproduzível. | Vira requisito de correção com teste de regressão. |
| **FATO CONFIRMADO; SEVERIDADE AJUSTADA** | O fato existe, mas o impacto ou a inferência original é menor/maior. | Pode virar requisito, mas com escopo/severidade revisados. |
| **DIVERGÊNCIA DOCUMENTAL** | Documento promete ou descreve algo diferente do código/entrega. | Correção pode ser só de documentação; implementar feature exige decisão separada. |
| **DECISÃO CIENTÍFICA/PRODUTO** | Há duas leituras defensáveis; mudar código altera prescrição. | ADR + evidência + aprovação antes de código. |
| **MELHORIA/SUGESTÃO DE PRODUTO** | Não é bug; é valor percebido, descoberta ou feature. | Não entra automaticamente; decisão de escopo. |
| **REFUTADO** | A evidência não sustenta o achado. | Não vira requisito; registrar o motivo. |
| **NÃO VERIFICÁVEL** | Falta aparelho, medição ou escopo para concluir. | Vira checklist de medição, não correção declarada. |
| **DUPLICATA** | Mesmo fato de outro ID. | Um único item de implementação; ambos os IDs rastreados. |
| **SEM ACHADO (contrapeso)** | Ponto forte ou elogio; não é problema. | Nenhuma ação, salvo correção de afirmativa falsa. |

**Prioridades:**

- **P0:** risco de privacidade, segurança ou aconselhamento médico fora do escopo. Corrigir antes de qualquer feature.
- **P1:** quebra de contrato do motor/validador, plano acima do tempo declarado, copy de progressão incorreta.
- **P2:** UX/copy/documentação que causa confusão ou perda de confiança, sem risco imediato.
- **P3:** backlog, melhoria de produto ou refatoração sem impacto imediato.
- **—:** não é requisito.

---

## 3. Inventário completo de achados e decisões

### 3.1 Algoritmo, níveis e ciência

| ID | Veredito | Evidência primária | Problema observado | Impacto / severidade | Pri. | Escopo proposto | Critério de aceite |
|---|---|---|---|---|---|---|---|
| **ALG-01** | BUG CONFIRMADO | `WeeklyPlanGenerator.plannedDuration` (`:1232-1246`); `WorkoutRecommender:115`; E14/E18; `SPORTS_SCIENCE §8.3` (`:319-323`); ADR 0010 | Piso por sessão (`20` fácil, `25` qualidade) vence o teto declarado; disponibilidade 10 → 20, 20 → 25. | Média: plano pede mais tempo do que o usuário tem. | **P1** | Corrigir todos os caminhos; decidir D1 (remover dia × encurtar × validar onboarding). Inclui `NOVO-ALG-01/02` e `ALG-07`. | Nenhuma `PlannedSession.durationMin` excede a disponibilidade do dia; dias abaixo do mínimo não viram sessão inflada; testes E14, E18, taper e `NO_PLAN`. |
| **ALG-02** | FATO CONFIRMADO; DECISÃO CIENTÍFICA/PRODUTO | `selectQualityDays` (`:936-945`); E19; `SPORTS_SCIENCE §8.5` (`:366`) e `§8.2` (`:295`) | Qualidade pode cair na segunda após longão no domingo anterior; a regra só considera o longão da semana corrente. O documento proíbe a direção oposta (longão depois da qualidade), não esta. | Média (ajustada): estímulo forte em recuperação incompleta; não é violação documental estabelecida. | **P2** | **Não** tratar como bug automático. ADR/decisão D5: estender simetria ou documentar que a virada de semana não é coberta. | ADR registra a decisão; se estender, teste com longão anterior no domingo e qualidade sem dia seguro; se não, `SPORTS_SCIENCE` explicita a limitação. |
| **ALG-03** | BUG CONFIRMADO (genérico); E16 exato NÃO reproduzido | `fitToVolume` (`:1428-1465`), `dropShortestEasy` (`:1451`), `volumeRationale` (`:1520-1556`); reconstrução do E16 (201) e cenário 188 × 190 | Copy anuncia “+6%, alvo 201” e a semana pode entregar menos que a base; largar uma sessão inteira para resolver 1 min; hint culpa a disponibilidade quando a causa é o alvo. | Média: quebra a promessa de “por quê” auditável. | **P1** | Pós-validação do volume entregue, copy derivada do entregue, não largar sessão por overshoot pequeno (D2). Não criar teste que presuma 178. | Se `target >= base`, `weeklyMinutes >= base`; percentual exibido = entregue/base; overshoot ≤5% não remove sessão inteira; hint nomeia a causa real; matriz sintética cobre o caso 188 × 190 e o caso de arredondamento. |
| **ALG-04** | FATO CONFIRMADO; SEVERIDADE AJUSTADA; PRODUTO | E5; `WeeklyPlanGenerator:502`; `SPORTS_SCIENCE §8.2` (`:283-295`) | Iniciante com 7 dias × 40 min recebe 7 corridas, sem aviso de frequência. Não há teto de dias por nível. | Média (ajustada): risco de overuse em novatos; o teto numérico de 5 dias não foi demonstrado. | **P2** | Não impor teto duro sem evidência/ADR. Opção recomendada: aviso não bloqueante no rationale/onboarding e registrar a escolha; D13. | Aviso aparece quando iniciante tem >5 dias e o plano não muda; ou documento declara que o app espelha a disponibilidade. Teste de copy, não de bloqueio. |
| **ALG-05** | FATO CONFIRMADO; SEVERIDADE AJUSTADA | `LevelGraduationEngine` (`:262-268,328`); `LevelGraduationViewModel.present` (`:196-202`); `LevelGraduationSection`; E6/E8 | O usuário não descobre o critério faltante: resultados `Stay` e `missing` são descartados pela VM/UI; não há ponte com a disponibilidade. | Média (ajustada): trava a graduação sem explicar o próximo passo. | **P2** | Agrupar com `NOVO-LVL-01`/`SEG-05`; mostrar estado e critérios no Perfil; D7. | `Stay` vira estado visível com `missing` em pt-BR; quando `MISSING_LONG_RUN` e agenda máxima <60, a copy sugere liberar slot ou registrar longão extra; teste de VM/UI. |
| **ALG-06** | BUG DE COPY CONFIRMADO | `WeeklyPlanGenerator:826-838`; E2 | Em taper, a copy “Sem treino forte: iniciante prioriza base” precede taper/recuperação. | Baixa: confusão na semana mais sensível. | **P2** | Reordenar ou compor a razão: taper/recuperação primeiro; iniciante como qualificador. | Teste E2 verifica que a string nomeia taper/recuperação antes de “iniciante”. |
| **ALG-07** | BUG CONFIRMADO | `WeeklyPlanGenerator:648-650`; `WorkoutStructure.fittedTo` (`:134-142`); E14 | Sessão “Acelerações curtas” de 10 min fica sem estrutura executável (`structure = null`). | Baixa: título promete estímulo sem dose. | **P1** (com ALG-01) | Não criar sessão de estímulo sem estrutura; remover o dia ou rebaixar para fácil simples (D1). Fácil/longão podem não ter estrutura por design. | Toda sessão de estímulo (strides, micro-dose, qualidade) tem `structure != null`; teste com 10 min. |
| **ALG-08** | BUG DE COPY CONFIRMADO | `volumeRationale` (`:1520-1561`); E17 | Quando disponibilidade limita, o hint de ACWR não é anexado; o rationale cita só disponibilidade. | Baixa: causa ativa escondida. | **P2** | Anexar `volumeBlockHint(ACWR_OUT_OF_RANGE)` também na branch de capping quando `block != NONE`. | Teste E17 verifica as duas causas na copy. |
| **ALG-09** | FATO CONFIRMADO; SEVERIDADE AJUSTADA | `WorkoutRecommender:100`; `DailyPrescriptionEngine:111`; ADR 0039 | `loadTrusted` usa `dataState` em um motor e `objectiveDataState` no outro; carga só-subjetiva é tratada de formas diferentes. | Baixa: afeta confiança/copy do fallback; não libera progressão no recommender. | **P2** | Unificar em `objectiveDataState` + declarar fração subjetiva, espelhando o engine. | Teste com carga só-sRPE: `NO_PLAN` reduz confiança e cita `R-LOAD-04`; nenhum caminho libera progressão. |
| **SEG-01** | FATO CONFIRMADO; PRODUTO | E1; `SessionLibrary` (BASE); `WeeklyPlanGenerator:826-838`; `SPORTS_SCIENCE §8.4` | Iniciante e intermediário são idênticos nas primeiras semanas de BASE; avançado só muda o longão. | Média (produto): escolha de nível parece inerte. | **P2** | Copy/documento explicando quando o nível muda o catálogo; **não** mudar o motor sem ADR. | Onboarding/Hoje/Plano explicam “BASE inicial é só fácil; estímulos entram na 2ª metade/BUILD”; teste de copy; nenhuma sessão nova. |
| **SEG-02** | FATO CONFIRMADO; PRODUTO | `SessionLibrary.kt:221-257`; `WeeklyPlanGenerator:1261-1264`; `SPORTS_SCIENCE §6.4/§8.8` | Avançado compartilha teto de 2 qualidades, grade de 1 sessão/dia e progressão de +6%; não há modelo distinto. | Baixa/Média (produto): segmento avançado não é servido por modelo próprio. | **P2** | Declarar escopo no PRODUCT/ROADMAP ou abrir ADR de diferenciação; não implementar sem evidência. | Documento diz “MVP otimizado para iniciante/intermediário; avançado = mesma estrutura com dose maior” **ou** ADR aprovado com nova regra. |
| **SEG-03** | FATO CONFIRMADO; AMBIGUIDADE DOCUMENTAL | E2/E13; `SessionLibrary.easyTemplates` (`:103-117`); `WeeklyPlanGenerator:384-386,449-466`; `SPORTS_SCIENCE §8.4` (`:346`), `§8.6` | Iniciante em taper não recebe strides nem micro-dose; `§8.4` diz “Todos como EASY; micro-dose ≤25 em taper/prova”, mas o micro-dose é `QUALITY` e a guarda `R-PRESC-04` o exclui. | Média: semana de prova plana e sem estímulo distinto. | **P2** | Decisão D6 + ADR/emenda: (A) incluir strides `EASY` no taper do iniciante ou (B) documentar “iniciante em taper: só fácil, por R-PRESC-04”. Não mudar sem decisão. | ADR registra a escolha; se A, teste de taper de iniciante com 1 strides `EASY` ≤25 min e nenhum `QUALITY`; se B, `SPORTS_SCIENCE` explícito e teste de copy. |
| **SEG-04** | FATO CONFIRMADO; DIVERGÊNCIA DOCUMENTAL | `LevelGraduationEngine:58-73,84-90`; `SPORTS_SCIENCE §8.12` (`:494-502`); `EvaluateLevelGraduation` | KDoc/documento prometem “revisão na regressão”; o motor só devolve `Stay`/`SuggestUpgrade` e não propõe rebaixamento/revisão. | Baixa (ajustada): promessa documental sem implementação. | **P2** | Corrigir KDoc e `§8.12` para “sem downgrade/revisão automática no MVP”; eventual `SuggestReview` fica para ADR futuro. | Documentos e KDoc consistentes; teste de contrato confirma que nenhum caminho rebaixa sozinho. |
| **SEG-05** | DUPLICATA DE ALG-05 | Mesmas evidências de ALG-05 | Mesmo problema de graduação/agenda curta. | Média. | **P2** | Um único item de implementação com ALG-05/NOVO-LVL-01. | Vinculado ao aceite de ALG-05. |
| **SEG-06** | FATO CONFIRMADO; LACUNA DOCUMENTAL | `ProfileLabels:39-42`; busca por `birthDate` em `core/domain` sem uso; `TrainingLoadCalculator:27-30`; `SPORTS_SCIENCE §4.1` | Idade é coletada/exibida, mas não usada no motor; sexo só altera coeficientes TRIMP. | Baixa: promessa de dado de entrada sem efeito. | **P2** | Documentar “idade não modifica prescrição no MVP; sexo entra só no TRIMP”; não inventar regra de masters. | `SPORTS_SCIENCE §4.1` e PRODUCT dizem o uso real; nenhuma regra nova sem evidência. |
| **SCI-01** | FATO CONFIRMADO; SEVERIDADE AJUSTADA | `ReadinessCalculator:36-47`; `TodayScreen:455-463`; `TodayExplanation:299-302` | Sem nenhum dado, o gauge mostra 50/“Moderada”, embora a explicação diga “Ainda não há dados de recuperação”. | Baixa/Média: leitura de “recuperação mediana” onde não há dado. | **P2** | Estado visual “Sem dados” + CTA de check-in quando `INSUFFICIENT_DATA` e `components.isEmpty()`; manter `LOW` e a explicação. | Teste de UI/screenshot: sem dados não exibe “Moderada 50”; exibe “Sem dados” e CTA; com dados, comportamento atual preservado. |
| **SCI-02** | FATO CONFIRMADO; COPY | `WeeklyPlanGenerator:785-790,848-875`; E15/E19 | Semana de descarga imprime “Progressão liberada” junto de “volume −25%”. O mecanismo está correto; a copy mistura conceitos. | Baixa: interpretação de progressão durante descarga. | **P2** | Em deload, trocar por “Estímulo mantido na descarga (volume −25%)”. | Teste de rationale em deload não contém “Progressão liberada”; contém “estímulo mantido”. |
| **SCI-03** | FATO CONFIRMADO; NÃO É GATE | `SPORTS_SCIENCE §6.4`; busca por `IntensityDistributionCalculator` no gerador sem hits | O gerador não mede a distribuição planejada 80/20; a distribuição é calculada depois em Tendências/Coach. | Baixa: risco teórico; gates diários e `fitToVolume` reduzem. | **P2/P3** | Não criar bloqueio 80/20. Opcional: medir no rationale/teste de aceite; documentar que é alvo, não gate. | Documento/teste registram a distribuição planejada; nenhum plano é bloqueado só por 80/20 sem ADR. |
| **SCI-04** | SEM ACHADO (contrapeso) | `SPORTS_SCIENCE §6.4`, hierarquia de evidência, ACWR/taper | Pontos fortes reais; a revisão qualificou a alegação ampla de taper (ver NOVO-SCI-01). | — | — | Nenhuma ação de produto; usar como guardrail. | — |
| **ALG-10** | SEM ACHADO (contrapeso) | E10/E11/E16 âmbar/E17; determinismo | Gate do dia 6/6 e blocos de volume nomeiam causa nos caminhos principais. A copy de 188 min é exceção (ALG-03). | — | — | Nenhuma ação; não usar como prova de que todo caminho nomeia a causa. | — |

### 3.2 Story Studio

| ID | Veredito | Evidência primária | Problema observado | Impacto / severidade | Pri. | Escopo proposto | Critério de aceite |
|---|---|---|---|---|---|---|---|
| **STORY-01** | FATO CONFIRMADO; SEVERIDADE AJUSTADA; PRODUTO | `ActivityDetailScreen.kt:283-325` | Story fica após cards condicionais e só aparece com GPS; “8 cards” não é constante. | Baixa/Média (descoberta). | **P3** | Sugestão de CTA no Hero/pós-importação; não é bug. Decidir depois. | Se aprovado, teste de CTA; se não, documentar a entrada atual. |
| **STORY-02** | FATO CONFIRMADO; SEVERIDADE AJUSTADA | `StoryStudioSheet.kt:623-696,467-469`; `StoryBackdropRenderer:88-98` | Prévia usa `mapBackground = null` e o mesmo texto “Prévia indisponível sem rota GPS” para loading e ausência de plano; não há spinner/estado de erro. O mapa na prévia é omitido por decisão. | Baixa: erro falso nos primeiros quadros. | **P2** | Separar `Loading`, `Empty` (sem GPS) e `Error`; manter mapa fora da prévia conforme copy. | Testes de UI para os três estados; não exibir copy de erro durante loading; mapa continua explicitamente omitido. |
| **STORY-03** | BUG CONFIRMADO | `ActivityDetailViewModel.kt:587-591`; `ActivityDetailScreen.kt:715-719`; `StoryMessages.kt:11-13` | `PreparingMap` pode durar até 45 s e o card só tem texto; não há “Cancelar”, embora `cancelStory()` aceite o estado. | Média: janela sem saída visível. | **P2** | Adicionar “Cancelar” em `PreparingMap`; manter `cancelStory()` e o timeout. | Teste Compose: `PreparingMap` mostra Cancelar; clique volta a `Idle` e não agenda export; nenhuma notificação é postada. |
| **STORY-04** | FATO CONFIRMADO; SEVERIDADE AJUSTADA | `ActivityDetailViewModel.kt:587-591`; `StoryStudioSheet.kt:202` | “Gerar vídeo” fecha o Studio; reabrir sempre volta à aba Vídeo. | Baixa: perda de contexto, não de dado. | **P3** | Opcional: preservar última aba no VM; não bloquear a geração. | Se aprovado, teste de reabertura na última aba; sem mudança de export. |
| **STORY-05** | FATO CONFIRMADO; SEVERIDADE AJUSTADA | `ActivityDetailScreen.kt:755-845`; `ArtStudioContent.kt:626-631` | Vídeo confirma no card/notificação, não em Snackbar; a Arte tem Snackbar. | Baixa: confirmação fora do campo de visão. | **P3** | Opcional: Snackbar na aba Vídeo para Done/Error; não é bug de contrato. | Se aprovado, teste de Snackbar; card/notificação continuam. |
| **STORY-06** | NÃO VERIFICÁVEL | `McButtons.kt:159-176`; `ArtStudioContent.kt:551-554,587-591,851-855`; Material3 `LocalMinimumInteractiveComponentSize` | `McTextButton` usa padding 8×4 e não declara `minHeight`; o alvo final não foi medido. | Desconhecido; não declarar violação. | **P3** | Medir o alvo final em teste de layout; se <48 dp, corrigir o componente. | Teste mede a área clicável final; se ≥48 dp, documentar; se <48 dp, `minHeight = 48.dp` com teste. |
| **STORY-07** | FATO CONFIRMADO; TESTE | `StoryStudioScreenshotTest.kt:111-175`; `TESTING.md:414` | Golden de 200% existe para Arte, não para Vídeo. | Baixa: lacuna de acessibilidade visual. | **P2** | Golden/teste `story-studio-narrow-200-dark` equivalente ao da Arte. | Golden novo ou teste de fonte 200% na aba Vídeo; nenhum golden existente alterado sem revisão visual. |
| **STORY-08** | FATO CONFIRMADO; SEVERIDADE AJUSTADA | `ActivityDetailViewModel.kt:603-630`; `StoryMessages.kt:11-13`; `PRODUCT.md:75` | Export de vídeo depende do mapa; offline falha; não há CTA para a Arte, que funciona localmente. | Baixa: exceção offline já documentada. | **P3** | Opcional: CTA “Criar a arte (funciona offline)” no erro `MAP_UNAVAILABLE`; não criar fallback route-only. | Se aprovado, teste de CTA; copy não promete vídeo offline. |
| **STORY-09** | FATO CONFIRMADO; SEVERIDADE AJUSTADA; PRODUTO | `StoryVideoExporter.kt:659`; `ArtStudioContent.kt:208-209` | Pôster/capa só nascem junto do MP4; não há botão avulso. | Baixa: feature não prometida separadamente. | **P3** | Feature futura; não implementar agora. | Documento diz “pôster nasce com o vídeo”; nenhum botão novo. |
| **STORY-10** | FATO CONFIRMADO; SEVERIDADE AJUSTADA; INTENCIONAL | `StoryStudioSheet.kt:202-217`; `StoryStudioContentTest.kt:66-85` | Aba padrão Vídeo; segmento some ao rolar, com teste. | Baixa: descoberta frágil, comportamento deliberado. | **P3** | Nenhuma mudança; documentar a decisão. | Teste existente continua verde; doc registra o comportamento. |
| **STORY-11** | FATO CONFIRMADO; SEVERIDADE AJUSTADA | `StoryStudioSheet.kt:716-719,742-744`; `STORY_V3_PLANO_DE_ACAO.md:275,297,498` | Prévia tem 3 frames discretos; o scrubber previsto não foi implementado. | Baixa: divergência de plano, não bug. | **P3** | Backlog; não implementar agora. | Documento marca scrubber como não entregue. |
| **STORY-12** | FATO CONFIRMADO; SEVERIDADE AJUSTADA | `StoryRecipeCodec.kt:42-63`; `StoryVideoExporter.kt:330-333`; `ROADMAP.md:702` | Qualidade/60 fps não é editável na UI; o campo é persistido/consumido; 60 fps foi adiado. “Enum órfão” é falso. | Baixa: escopo deliberado. | **P3** | Nenhuma mudança; corrigir eventual doc que trate como bug. | Doc diz “60 fps preparado, não exposto”; teste de codec existente. |
| **STORY-13** | DECISÃO DOCUMENTADA | `PRD_ARTE_PNG_ROTA.md:68`; renderers | Marca fixa sem toggle é decisão pós-MVP. | Baixa. | **—** | Nenhuma ação. | — |
| **STORY-14** | FATO CONFIRMADO; SEVERIDADE AJUSTADA | `ActivityDetailScreen.kt:786-791,900-904` | Share individual por formato; copy de destino parcial. | Baixa: conveniência. | **P3** | Generalizar copy por destino se tocar na tela. | Copy “Capa 9:16 → Stories/Reels” e “Pôster 4:5 → Feed” sem mudar o share. |
| **STORY-15** | FATO CONFIRMADO; SEVERIDADE AJUSTADA; A11Y | `StoryStudioSheet.kt:577-594` | Segmento usa `Surface.clickable` sem `Role.Tab`; some ao rolar sem anúncio. | Baixa: TalkBack não anuncia a aba. | **P2** | Adicionar `Role.Tab` e teste de semântica; anúncio do segmento oculto é opcional. | Teste de semântica encontra `Role.Tab`; seleção/descrição preservadas. |
| **STORY-16** | BUG DE TESTE CONFIRMADO | `StoryStudioContentTest.kt:273-280`; `StoryStudioScreenshotTest.kt:104-106`; busca `PreparingMap` em testes sem hits | Faltam testes dos estados da prévia e do card `PreparingMap`. | Média (risco de regressão). | **P2** | Testes TDD junto de STORY-02/03; não criar golden sem estado estabilizado. | Testes cobrem loading/empty/error da prévia e PreparingMap com cancelar; falham antes da correção. |
| **STORY-17** | FATO CONFIRMADO; DOCUMENTAÇÃO | `PROGRESS.md:895-907`; `TESTING.md:476-501` | Flakes de Story são conhecidos no PROGRESS, mas não estão na tabela de flakes do TESTING. | Baixa: dívida invisível no doc de testes. | **P2** | Registrar sintoma/contexto/comando; manter política anti-`@Ignore`. | `TESTING.md §10.1` lista os flakes de Story com o comando isolado que fica verde. |
| **STORY-18** | FATO CONFIRMADO; SEVERIDADE AJUSTADA | Docs Story × `StoryStudioSheet.kt:467-469,716-719`; `StoryRecipe.kt:64-71`; `TESTING.md:403-425`; `ROADMAP.md:681-705` | Lacunas mock→código; algumas intencionais (60 fps adiado, mapa fora da prévia) e algumas stale. | Baixa: documentação, não feature. | **P2/P3** | Reconciliar docs por subfeature; não implementar scrubber/qualidade só porque o mock previa. | Docs marcam entregue, adiado e não previsto; nenhum item vira feature automática. |
| **STORY-19** | SEM ACHADO (contrapeso), com uma afirmação refutada | `StoryFinalRenderer.kt:29-46`; `StoryPlanCalculator.kt:43-49`; `REVISAO...:144` | Vários pontos fortes são reais, mas “veredito/plano nunca entram no vídeo” é falso: o frame final desenha headline/proof. | Baixa (documentação). | **P3** | Corrigir a afirmação no PRD/auditoria; o veredito no vídeo é produto intencional (NOVO-STORY-02). | Nenhuma mudança de renderer; docs deixam de afirmar “nunca entra”. |

### 3.3 Coach IA e validador

| ID | Veredito | Evidência primária | Problema observado | Impacto / severidade | Pri. | Escopo proposto | Critério de aceite |
|---|---|---|---|---|---|---|---|
| **COACH-01** | FATO CONFIRMADO; SEVERIDADE AJUSTADA | `CoachFreeAnswerPrompt.kt:16-29`; `CoachViewModel.kt:640-641`; `HANDOFF_IA_COACH.md:56` | Cada prompt leva pergunta atual + dossiê, não histórico; follow-ups dependem de regex. “100% regex” é absoluto demais. | Média: sensação de amnésia. | **P3** | Não implementar multi-turn agora; documentar. Opcional futuro: última pergunta/replica validada no `engineContext`, sem payload novo. | Documento diz “sem histórico no MVP”; nenhum dado novo no payload. |
| **COACH-02** | FATO CONFIRMADO; SEVERIDADE AJUSTADA | `CoachApi.kt:82-88,111-117`; `Main.kt:62-69,114-118`; busca SSE sem hits | Resposta é lida inteira, sem streaming; 45/25 s são teto, não latência medida. | Baixa/Média: espera percebida. | **P3** | Medir p50/p95; se batch, mostrar estado honesto no header; streaming fica pós-MVP. | Medição registrada; nenhuma promessa de streaming. |
| **COACH-03** | FATO CONFIRMADO; SEVERIDADE AJUSTADA; PRODUTO | busca por feedback/thumbs em `app/ui/coach` sem hits; `PRD_IA_COACH.md:42` | Sem feedback por mensagem; PRD prevê feedback qualitativo do usuário 0, não widget. | Baixa. | **P3** | Não implementar widget no MVP; decisão de escopo. | Documento diz “feedback qualitativo manual”; nenhum widget novo. |
| **COACH-04** | FATO CONFIRMADO; DIVERGÊNCIA DOCUMENTAL | `PRD_IA_COACH.md:18`; `CoachApi.kt:82-88`; `CoachScreen.kt:195-200`; `PRD_COACH_CHAT.md:161` | PRD ~10 s vs 45/25 s no app; status do header é fixo “motor no comando” mesmo offline/sem IA. | Média: expectativa e status enganosos. | **P2** | Atualizar decisão 8 do PRD e derivar status de `Fallback`/consentimento/offline. | PRD e copy refletem timeouts reais; status muda com estado; teste de UI. |
| **COACH-05** | BUG CONFIRMADO | `CoachViewModel.revoke` (`:527-536`); guards (`:777,820`); `CoachScreen.kt:347`; teste `revogar...` | `revoke()` limpa consentimento/Room, mas não `_exchanges`/`_freeAnswer`; retorno em voo pode deixar Loading/texto visível. | Média (privacidade/UX). | **P0** | Limpar `_exchanges` e `_freeAnswer` no revoke; resposta em voo não repopula; UI sem conversa após revogar. | Após `revoke()`, `exchanges` vazio, `freeAnswer = Idle`, nenhum texto do Coach visível; teste com API bloqueada em voo. |
| **COACH-06** | BUG CONFIRMADO | `retryFreeAnswer` (`:599-609`); `FreeExchange` (`:197`); `CoachChatComponents.kt:252-283` | Retry combina `lastFreeText` com qualquer exchange em fallback; com duas falhas, o card antigo reenvia a pergunta mais recente. Trocar só para `pending.question` ainda erra o alvo se houver mais de uma falha. | Média (correção/resposta errada). | **P0** | Passar `exchangeId` do botão; usar a pergunta **daquele** exchange; UI `onRetryFreeAnswer(exchangeId)`. | Teste Q1 falha, Q2 sucesso, retry do card Q1 usa Q1; hash/persistência de Q1; nenhuma duplicação de `YOU`. |
| **COACH-07** | FATO CONFIRMADO | `sendFreeText` (`:570-585`); `recordYouTurn`; fallback efêmero; KDoc “archive never holds a question without its answer” | `YOU` é persistido antes do roteamento; recusa/fallback não persiste `ANSWER`; histórico pode ficar com pergunta órfã. | Média: arquivo inconsistente. | **P2** | Decidir D9: persistir `ANSWER` determinístico de recusa/fallback ou só persistir `YOU` quando houver resposta terminal. | Teste de recusa e de falha de rede: nenhum `YOU` sem `ANSWER`; histórico renderiza par completo. |
| **COACH-08** | FATO CONFIRMADO; SEVERIDADE AJUSTADA | `CoachViewModel:238-242`; `CoachScreen.kt:347-352,430-450` | Timeline principal nasce vazia e não hidrata de `observeTurns`; histórico fica no bottom sheet. | Baixa/Média: reabrir a aba parece apagar a conversa. | **P3** | Opcional: hidratar exchanges do dia; não confundir com memória em prompt. | Se aprovado, reabrir a aba mostra turnos do dia; nenhum turno entra em prompt. |
| **COACH-09** | FATO CONFIRMADO; SEVERIDADE AJUSTADA | `FreeInputParser.kt:71-78`; `FreeAnswerCopy.OUT_OF_SCOPE` (`CoachViewModel.kt:1197-1203`); `PRD_IA_COACH.md:75`; ADR 0056 | Dor/lesão/doença caem em recusa genérica sem CTA para check-in/avaliação; `ILLNESS_OR_INJURY` só nasce do check-in. | Média: beco sem saída em segurança. | **P1** (com NOVO-COACH-01) | Copy de recusa com CTA “registre no check-in e procure avaliação”; não diagnosticar, não prescrever. | Teste de parser/copy: pergunta de dor/lesão recebe recusa com CTA; nenhuma chamada de rede; nenhum conselho médico. |
| **COACH-10** | BUG CONFIRMADO | `CoachViewModel.kt:323-327`; `CoachResponseValidator.kt:117-145,274-309`; execução sintética | Explain passa `allowedPercents = null` e não checa distância/contagem; “85%”, “10 km” e “99 sessões” passaram. | Alta (under-rejection). | **P1** | Allow-lists explícitas no explain/review; unificar checagem de %, distância, contagem e decimais; U-1/U-3. | Teste explain rejeita 85%, 10 km, 99 sessões e 12,5 não autorizados; aceita números do plano; review mantém seus allow-lists. |
| **COACH-11** | FATO CONFIRMADO; SEVERIDADE AJUSTADA | `CoachOpenAnswer.kt:153-155`; `CoachSessionAnswer.kt:78-82`; `CoachViewModel.kt:990-1000` | `allowedMinutes` soma todos os dias; “corra 40 min terça” passa se 40 é de quinta; total semanal pode ser lido como do dia. | Média: número do plano em contexto errado. | **P2** | Escopar allow-lists ao `targetDate` no fluxo de sessão; total semanal só com rótulo. | Teste: 40 de quinta não autoriza “40 min terça”; duração do dia e total rotulado continuam aceitos. |
| **COACH-12** | FATO CONFIRMADO; SEVERIDADE AJUSTADA | `CoachOpenAnswer.kt:96-103,255-259` | Número digitado pelo usuário não entra na allow-list; eco de “15 km” é recusado. | Baixa: recusa de autorrelato pode ser deliberada. | **P3** | Não mudar sem decisão; documentar que número digitado é autorrelato e não dado verificado. | Documento registra a decisão; nenhum número do usuário vira fato do motor. |
| **COACH-13** | BUG CONFIRMADO | `CoachApi.kt:113-117`; `CoachViewModel.kt:819-832`; `Main.kt:417-419` | `IOException("coach upstream $code: ${body.take(200)}")`; a VM loga `e.message`; corpo do upstream pode conter payload. | Média (privacidade). | **P0** | Remover corpo da exceção; logar só status/código estável; não logar payload. | Exceção/teste com corpo “SEGREDO” não contém “SEGREDO”; log da VM contém só status; nenhuma resposta/HTTP body no logcat. |
| **COACH-14** | INFORMATIVO; SEM GAP CRÍTICO | `PRD_IA_COACH.md:78-79`; `ARCHITECTURE.md:283`; `CoachOpenFlow.kt:82-93` | Opt-in, TTL, revogação/export existem; pergunta digitada é enviada após opt-in. Ressalvas LGPD/região e regex da porteira. | Baixa/informativo. | **—** | Nenhuma correção; manter controles. | — |
| **COACH-15** | FATO CONFIRMADO; SEVERIDADE AJUSTADA; COPY | `CoachFallbackLabels.kt:16-20`; `CoachViewModel.kt:1056-1069,1209-1210` | `freeAnswerReasonCopy` foi corrigido, mas `NO_CONNECTION`/`GENERIC` do fluxo aberto prometem card “aqui embaixo” que não é renderizado. | Baixa: promessa visual falsa. | **P2** | Alinhar copy à emenda ADR 0063 §6; sem “aqui embaixo” no fluxo aberto. | Teste de copy/UI: fallback aberto não promete card; retry é o próximo passo. |
| **COACH-16** | FATO CONFIRMADO; INFORMATIVO | `Main.kt:161-162,197-200`; `HANDOFF_IA_COACH.md:350-357` | Persona hardcoded em “Rafael”; redeploy do Dokploy é pendência. | Baixa (operacional). | **P2** | Parametrizar persona no futuro; tratar redeploy como condição de aceite documentada. | HANDOFF/README registram persona e redeploy; nenhuma alegação de multiusuário. |
| **COACH-17** | FATO CONFIRMADO; SEVERIDADE AJUSTADA | `CoachViewModel.kt:199-208,906-915`; `wc -l` 1222 | Construtor com 7 dependências e arquivo grande; “8 dependências” é incorreto. | Baixa (manutenção). | **P3** | Refatoração futura (`CoachFreeAnswerCoordinator`), sem mudança de comportamento. | Testes existentes verdes; nenhuma regra nova. |
| **COACH-18** | FATO CONFIRMADO; SEVERIDADE AJUSTADA | `CoachChatComponents.kt:80-90,223-243`; `CoachHistoryCard.kt:72`; `PRD_COACH_CHAT.md:168` | Headings só em header/histórico; bolhas não mostram hora. | Baixa (a11y/PRD). | **P3** | Adicionar headings/hora quando tocar na tela; não é P0. | Teste de semântica/timestamp se aprovado. |
| **COACH-19** | SEM ACHADO (contrapeso), qualificado | `PRD_IA_COACH.md:78-79`; `ARCHITECTURE.md:283` | Pontos fortes reais, mas “validador como autoridade” é amplo demais (COACH-10/U-1) e “sem payload em log” não cobre o corpo do erro (COACH-13). | — | — | Nenhuma ação; usar como guardrail. | — |
| **V-1** | FATO CONFIRMADO; SEVERIDADE AJUSTADA | `CoachResponseValidator.kt:26`; execução sintética | `pacePattern` casa qualquer `HH:MM`; “17:30” é recusado como pace. | Baixa: falso positivo. | **P2** | Exigir contexto de pace/unidade (`/km`, “pace”, “ritmo”) para `HH:MM`; teste 17:30. | “17:30” não é recusado só por ser hora; “5:30/km” continua barrado. |
| **V-2** | BUG CONFIRMADO | `CoachResponseValidator.kt:53-69,190-204` | “Hoje é descanso — descanse bem” cai na guarda de prescrição (verbo + estrutural). | Baixa: resposta legítima vira fallback. | **P2** | Exemptar afirmação de descanso sem mudança estrutural; teste. | “Hoje é descanso — descanse bem” passa; “troque o treino por descanso” continua barrado. |
| **V-3** | REFUTADO | `CoachNumberPatterns.kt:14`; `CoachResponseValidator.kt:288-292` | Com fato “82%”, “82,5%” é um número diferente e deve ser recusado; a mensagem pode citar `5%`, mas a recusa é correta. | — | **—** | Não vira requisito. Registrar no PRD e não “corrigir” o validador para aceitar 82,5%. | Teste existente de recusa permanece. |
| **V-4** | FATO CONFIRMADO; SEVERIDADE AJUSTADA | `CoachViewModel.kt:701,997`; `CoachResponseValidator.kt:288-292` | Week/session passam `allowedPercents = emptySet()`; qualquer percentual é recusado, inclusive um percentual útil/computado. | Baixa/Média: over-rejection. | **P2** | Passar percentuais computados quando existirem; manter recusa de % inventado; testar 80/20. | Percentual computado aceito; percentual não computado recusado; teste “80%” no contexto certo. |
| **V-5** | FATO CONFIRMADO; SEVERIDADE AJUSTADA | `CoachSessionAnswer.kt:246-247`; `CoachResponseValidator.kt:236-242` | Data ISO do dossiê é impressa, mas seus componentes não são autorizados; eco da data pode ser recusado. | Baixa/Média: over-rejection. | **P2** | Autorizar a data-alvo (ISO e componentes rotulados) sem liberar números arbitrários. | “treino de 26/11” passa quando a data é a do dossiê; “26 treinos” inventado continua barrado. |
| **V-6** | FATO CONFIRMADO; SEVERIDADE AJUSTADA | `CoachOpenAnswer.kt:96-103,255-259` | Número digitado pelo usuário (ex.: 15 km) não é citável. | Baixa. | **P3** | Mesma decisão de COACH-12; não implementar sem aprovação. | Documento registra a decisão. |
| **U-1** | BUG CONFIRMADO | `CoachResponseValidator.kt:117-121,274-309`; execução sintética | Explain aceita 85%, 10 km e 99 sessões não autorizados. | Alta. | **P1** | Mesmo item de COACH-10; allow-lists explícitas. | Vinculado a COACH-10. |
| **U-2** | FATO CONFIRMADO; SEVERIDADE AJUSTADA | `CoachResponseValidator.kt:224-242` | `uncoveredCount` aceita qualquer dígito cujo inteiro esteja em qualquer allow-list; “dormiu 6 horas” passa se 6 é contagem. | Média. | **P2** | Unidades tipadas: `min` só em minutos, `%` só em percentuais, `km` só em distâncias, dígito nu só em contagens; testar “6 horas”. | “dormiu 6 horas” é recusado quando 6 não é contagem; “6 treinos” passa se 6 for contagem. |
| **U-3** | FATO CONFIRMADO; SEVERIDADE AJUSTADA | `CoachResponseValidator.kt:236-242`; execução sintética | `raw.toDoubleOrNull()?.takeIf { it == it.toInt().toDouble() }` descarta decimais; “12,5 unidades” passou. O exemplo “12,5%” não foi reproduzido. | Média. | **P1/P2** | Tratar decimais com unidade e percentuais fracionários; não congelar “12,5%” como único caso. | “12,5 unidades” é recusado; decimal autorizado (se houver) passa; teste com vírgula e ponto. |
| **U-4** | FATO CONFIRMADO; SEVERIDADE AJUSTADA; EXEMPLO REFUTADO | `CoachResponseValidator.kt:190-204,251-271` | “Corra 5 mil metros” já é barrado pela guarda de verbo + número; formulações declarativas sem verbo podem escapar se o número for contagem permitida. | Baixa. | **P2** | Endurecer formulações declarativas/prescrições sem verbo, **sem** usar o exemplo literal como teste que deveria passar. | “Corra 5 mil metros” continua barrado; “5 km hoje” sem contexto de prescrição é avaliado pela regra tipada; teste não exige que o exemplo passe. |
| **U-5** | BUG CONFIRMADO | `CoachResponseValidator.kt:53-66,67-69`; execução sintética | “Troque o intervalado por uma rodagem leve” não tem token estrutural listado nem número descoberto; pode passar e prescrever troca. | Média. | **P1/P2** | Ampliar verbos/estruturas de troca/cancelamento, com testes de falso positivo; manter “só o motor prescreve”. | Frase de troca é recusada; “descanse bem” e “faça os 40 min” continuam aceitos. |
| **U-6** | FATO CONFIRMADO | `CoachResponseValidator.kt:102-104` | “Vou guardar isso” não está nas frases de promessa de memória. | Baixa. | **P2** | Ampliar frases de promessa de memória (guardar, anotar, lembrar na próxima). | “Vou guardar isso” é recusado; frase sem promessa passa. |
| **U-7** | DUPLICATA DE COACH-11 | `CoachOpenAnswer.kt:153-155`; `CoachSessionAnswer.kt:78-82` | Allow-list cruzando dias. | Média. | **P2** | Um único item com COACH-11. | Vinculado a COACH-11. |

### 3.4 Documentação e geral

| ID | Veredito | Evidência primária | Problema observado | Impacto / severidade | Pri. | Escopo proposto | Critério de aceite |
|---|---|---|---|---|---|---|---|
| **DOC-01** | DIVERGÊNCIA DOCUMENTAL | `PRODUCT.md:62`; `CalibrationScreen.kt:469-478`; busca `guidedTest/Teste30` sem hits | PRODUCT promete “teste guiado (Tier C)”; o app só orienta o teste e permite inserir resultados manualmente. | Média (ajustada): promessa de escopo. | **P1/P2** | Doc-only: trocar por “teste de 30 min feito por conta própria + inserção manual”; player Tier C fica para ADR futuro (GEN-04). | PRODUCT v1.3 não promete player; texto descreve o fluxo manual real; busca por “guiado” não encontra promessa. |
| **DOC-02** | DIVERGÊNCIA DOCUMENTAL | `PRODUCT.md:68`; `CompleteSession.kt:101-104`; `ROADMAP.md:430`; ADR 0027 | “Execução guiada (contrato)” está no MVP, mas `CompleteSession` devolve `Rejected` e a etapa G segue proposta. | Média. | **P2** | Remover do “Dentro” ou marcar “futuro”; não implementar o contrato sem decisão. | PRODUCT/ROADMAP dizem “GUIDED futuro”; `CompleteSession` continua recusando; teste de copy. |
| **DOC-03** | DIVERGÊNCIA DOCUMENTAL | `PRODUCT.md:100-109`; `TESTING.md:28-31,56`; `PRD_TREINO_REAL.md:296-313`; buscas de SDK sem hits | Métricas de produto (crash-free, import ≥99%, tempo até insight) não têm instrumentação nem `androidTest`. | Média. | **P2** | Separar “métricas de aceite de desenvolvimento” (testes/goldens/manual) de “métricas de produto” (não instrumentadas); não adicionar analytics. | PRODUCT §6 não afirma medição inexistente; cada métrica tem método/fonte ou é marcada “não medida”. |
| **DOC-04** | DIVERGÊNCIA DOCUMENTAL | `PRODUCT.md:73`; `ROADMAP.md:291-298`; busca `IMPORT_PENDING` sem hits | PRODUCT lista “import pendente”; o app notifica import concluído (sucesso/duplicado/falha). | Baixa. | **P2** | Trocar por “import concluído”. | PRODUCT e ROADMAP usam o nome real do canal; nenhum `IMPORT_PENDING`. |
| **DOC-05** | DIVERGÊNCIA DOCUMENTAL/PRODUTO | `PRODUCT.md:52,129`; `ROADMAP.md:858`; busca `restoreBackup/importBackup` sem hits | “Ter meus dados para sempre” e backup versionado não incluem restauração; T-06 foi ZIP descartado. | Baixa (ajustada): restauração não é promessa explícita do MVP. | **P2** | Decisão D10: doc-only (recomendado) ou feature futura. Não implementar restore agora. | PRODUCT diz “exportação JSON; restauração de backup não está no MVP”; nenhuma promessa de round-trip. |
| **DOC-06** | DIVERGÊNCIA DOCUMENTAL | `PRODUCT.md:3`; `MeuCoachApp.kt:80-86`; `DECISIONS.md:179`; `ROADMAP.md:320` | PRODUCT v1.2 não cobre Coach/Story/Arte; descrições antigas da barra incluem Perfil e omitem Coach. | Alta (orig.) / Média (ajustada). | **P1** | PRODUCT v1.3: adicionar Coach/Story/Arte ao escopo entregue; corrigir barra para Hoje · Corridas · Coach · Tendências · Saúde. | PRODUCT e docs de navegação descrevem os 5 destinos reais; Perfil via avatar/engrenagem; teste de doc/grep. |
| **DOC-07** | DIVERGÊNCIA DOCUMENTAL | `ROADMAP.md:14,617-705,753-824` | Fase 3 inteira aparece como “Planejada” embora Story/LLM tenham fatias entregues; Zepp/social/Wear/backend continuam futuros. | Média. | **P2** | Dividir “Story/LLM entregue” × “Zepp/social/Wear/backend planejado”; atualizar por subfeature. | ROADMAP não marca Fase 3 inteira concluída nem inteira planejada; itens têm status individual. |
| **DOC-08** | DIVERGÊNCIA DOCUMENTAL | `PRD_TREINO_REAL.md:570-574,636-639,697-700`; `CICLO_4_TREINO_REAL.md:4,641` | Aceites do PRD seguem `[ ]` apesar de C4-61 concluído. | Baixa (higiene). | **P2** | Marcar apenas aceites realmente cobertos, citando teste/golden; não marcar o que não foi validado no aparelho. | Caixas refletem evidência; itens de aparelho continuam abertos. |
| **DOC-09** | DÍVIDA/DOCUMENTAÇÃO | `PROGRESS.md:6803`; `TESTING.md:476-501`; `PROGRESS.md:895-907` | Consolidado junta débitos de naturezas diferentes; flakes de Story faltam no TESTING. | Baixa. | **P2** | Adicionar flakes de Story; manter REF-01/debt rastreados; não “fechar” dívida sem remover baseline. | `TESTING.md` lista flakes com contexto; baseline/REF-01 continuam visíveis. |
| **DOC-10** | DIVERGÊNCIA DOCUMENTAL | `HANDOFF_POS_CHAT_TURNS.md:22,33-41`; `HANDOFF_COACH_V3.md:5`; `ARCHITECTURE.md:280`; `HANDOFF_IA_COACH.md:4-5` | Handoffs ainda tratam DB v20/v21 ou F4 como próximo; estado atual é v22/feature concluída; IA Coach mantém redeploy pendente. | Média. | **P2** | Atualizar status/next slice; preservar histórico, não reescrever. | Handoffs têm data/estado atual; pendência de redeploy e validação no aparelho explícita. |
| **GEN-01** | DUPLICATA DE DOC-01/02/03 | `PRODUCT.md:62,68,100-109`; `CompleteSession.kt:101-104`; `PRD_TREINO_REAL.md:296-313` | Agrupa três promessas sem implementação. | Média. | **P2** | Um único item de reconciliação com DOC-01/02/03. | Vinculado a DOC-01/02/03. |
| **GEN-02** | DUPLICATA DE DOC-06/07 | `PRODUCT.md:3`; `MeuCoachApp.kt:80-86`; `ROADMAP.md:14,617-705` | PRODUCT/barra/roadmap stale. | Média. | **P1/P2** | Um único item com DOC-06/07. | Vinculado a DOC-06/07. |
| **GEN-03** | DUPLICATA DE DOC-05 | `PRODUCT.md:52,129`; `ROADMAP.md:858` | Export sem restore. | Baixa. | **P2** | Um único item com DOC-05. | Vinculado a DOC-05. |
| **GEN-04** | MELHORIA/PRODUTO | `SPORTS_SCIENCE.md:62-70`; `PRODUCT.md:62`; `CalibrationScreen.kt:469-478` | Player Tier C de 30 min seria útil (usuário 0 sem prova), mas exige ADR/gate de segurança. | Média (produto). | **P3** | Não implementar agora; decisão de escopo/ADR futuro. | Nenhum player novo; DOC-01 resolvido por doc. |
| **GEN-05** | NÃO VERIFICÁVEL | `ROADMAP.md:695,701,705`; `PROGRESS.md:703-711` | Validações físicas pendentes; “~15 itens” não confirmado. | Média (processo). | **P1/P2** | Checklist único de aparelho (Story, revogação/Coach, 200%, low-RAM); não declarar validação sem executar. | Checklist com data, aparelho, cenário, resultado; itens não executados ficam abertos. |
| **GEN-06** | FATO CONFIRMADO; SEVERIDADE AJUSTADA | `ROADMAP.md:867,877`; `ROADMAP.md:318` | “P1-12 é o único critério não marcado” é falso: a validação em aparelho também aparece no roadmap. | Média. | **P2** | Atualizar critérios de saída com **dois** itens: P1-12 e validação em aparelho; D17. | ROADMAP lista os dois critérios separados e desmarcados; nenhum é declarado concluído. |
| **GEN-07** | FATO CONFIRMADO; SEVERIDADE AJUSTADA | `OnboardingScreen.kt:414-422`; E1; `TodayExplanation.kt:299-302` | Onboarding não diz que minutos são teto; Hoje explica prontidão sem dados; nível não explica quando muda o catálogo. | Baixa/Média (UX). | **P2** | Micro-copy: “esse é seu teto”; “o nível muda estímulos a partir de BUILD”; combinar com SCI-01/SEG-01. | Testes de copy/UI; usuário entende teto e timing do nível. |

### 3.5 Achados novos da revisão (IDs NOVO-*)

| ID | Veredito | Evidência primária | Problema observado | Impacto / severidade | Pri. | Escopo proposto | Critério de aceite |
|---|---|---|---|---|---|---|---|
| **NOVO-ALG-01** | BUG CONFIRMADO | `WeeklyPlanGenerator.plannedDuration` (`:1240`); revisão: taper 10 min → micro-dose 25; `SPORTS_SCIENCE §8.6` | Micro-dose de taper usa `MICRO_DOSE_MIN` fixo e ignora a disponibilidade do dia. | Média: sessão de 25 min em slot de 10. | **P1** | Incluir no item de ALG-01; selecionar micro-dose só se o slot couber (ou remover o dia). | Nenhuma micro-dose excede a disponibilidade; teste 10/20 min em taper. |
| **NOVO-ALG-02** | BUG CONFIRMADO | `WorkoutRecommender:115`; revisão: fallback sem plano 10 min → 20 | `NO_PLAN` usa `coerceIn(20,60)` e infla o slot de 10 min. | Média: fallback recomenda mais tempo do que existe. | **P1** | Incluir no item de ALG-01; fallback deve respeitar o teto ou devolver `REST` com motivo. | Recomendação `NO_PLAN` tem `durationMin <=` disponibilidade; abaixo do mínimo, `REST` explicativo; teste 10/20/30. |
| **NOVO-COACH-01** | BUG DE SEGURANÇA CONFIRMADO | `FreeInputParser.scopePatterns` (`:71-78`); `CoachResponseValidator.medicalTokens` (`:71-99`); execução sintética com “arritmia” | “Tenho arritmia, posso correr?” foi para `ASK_COACHING`; “Correr com arritmia é seguro para você” passou no validador. | Alta (segurança; não afirmar ocorrência em produção). | **P0** | Fail-closed: ampliar porteira e validador para condições cardíacas/sintomas de alerta; recusa com encaminhamento e CTA de check-in; nunca diagnosticar. | Pergunta sintética não faz chamada de rede; resposta que afirma segurança é `Invalid`; copy encaminha avaliação; testes de parser e validador. |
| **NOVO-STORY-01** | FATO CONFIRMADO; PRODUTO | `StorySessionResolver.kt:29-37`; `StoryPlanCalculator.kt:43-49`; `ActivityDetailViewModel.kt:539-550` | O veredito do Story é associado à sessão planejada **pela data**, sem verificar se a atividade é a evidência daquela sessão; treino livre pode receber alvo/veredito de um plano que não executou. | Média (correção de conteúdo). | **P2** | Decisão D8: exigir vínculo atividade↔sessão; sem vínculo, “treino livre” sem alvo/veredito. | Teste: corrida livre em dia planejado não recebe veredito; corrida vinculada recebe; worker e prévia usam a mesma regra. |
| **NOVO-LVL-01** | FATO CONFIRMADO | `LevelGraduationViewModel.present` (`:196-202`); `LevelGraduationSection`; `LevelGraduationEngine.Stay.missing` | `Stay` é convertido em “nada a mostrar”; o usuário não vê o estado nem os critérios faltantes. | Média (UX). | **P2** | Decisão D7: mostrar “Próximo nível” com `reason`/`missing`; agrupar ALG-05/SEG-05. | Perfil mostra critérios quando `Stay`; sugestão continua com aceite; teste de VM/UI; nenhuma mudança automática. |
| **NOVO-SCI-01** | FATO CONFIRMADO; DECISÃO CIENTÍFICA | `VolumeProgression` (`base = max strongMinutes`); `WeeklyPlanGenerator` taper factor; `SPORTS_SCIENCE §8.6` (média de 3 semanas) | O taper usa o maior volume forte recente como referência, enquanto o doc descreve a média das 3 semanas pré-taper. | Média: redução pode ser mais agressiva que a faixa documentada. | **P2** | **Não** alterar agora. ADR/decisão D3: código para média (com fallback) ou documento para maior recente. | ADR aprovado; se código, golden de taper com média e fallback explícito; se doc, versão justificada. |
| **NOVO-SCI-02** | FATO CONFIRMADO; DECISÃO CIENTÍFICA | `WeeklyPlanGenerator.longRunTarget` (`:1272-1284`); `SPORTS_SCIENCE §8.5` (`:364`); cenário 45 → 62 | Longão de iniciante pode subir 17 min em uma semana, embora o doc cite 5–10 min/semana. | Média: progressão abrupta. | **P2** | **Não** alterar agora. ADR/decisão D4: limitar +10 min/semana ou documentar exceção. | ADR aprovado; se código, teste 45 → ≤55 mantendo piso/agenda; se doc, justificativa. |
| **NOVO-DOC-01** | DIVERGÊNCIA DOCUMENTAL | `ROADMAP.md:867,877`; `ROADMAP.md:318`; `GEN-06` | “P1-12 é o único critério” é falso; a validação em aparelho também está pendente. | Média (rastreabilidade). | **P2** | Atualizar critérios de saída com P1-12 **e** validação em aparelho. | Dois critérios separados, ambos desmarcados; nenhum “único” no doc. |
| **NOVO-STORY-02** | FATO CONFIRMADO; DOCUMENTAÇÃO/ERRATA | `StoryFinalRenderer.kt:29-46`; `StoryPlanCalculator.kt:43-49`; `AUDITORIA_GERAL:561,834`; `REVISAO:144` | A auditoria afirma que “veredito/plano nunca entram no vídeo público”; o renderer final desenha headline/proof. O veredito no vídeo é produto intencional do Story V2/V3. | Baixa (documentação). | **P3** | Corrigir a afirmação no PRD/auditoria; **não** remover o veredito do renderer sem decisão de produto. | Docs dizem “veredito aparece no frame final”; nenhuma mudança de renderer; se o produto quiser remover, ADR separado. |

---

## 4. Decisões abertas que precisam de aprovação

Estas decisões não podem ser resolvidas só por código; algumas mudam prescrição. A recomendação está indicada, mas a aprovação é explícita.

| ID | Decisão | Opções | Recomendação | IDs afetados |
|---|---|---|---|---|
| **D1** | Disponibilidade abaixo do piso | (A) Remover o dia/`REST` com copy “slot abaixo do mínimo”; (B) encurtar a sessão abaixo do piso; (C) validar mínimo no onboarding. | **A**, compatível com `SPORTS_SCIENCE §8.3`; para `NO_PLAN`, `REST` explicativo. | ALG-01, ALG-07, NOVO-ALG-01, NOVO-ALG-02 |
| **D2** | Overshoot de volume e `dropShortestEasy` | (A) Aparar até 5% sem largar sessão inteira; (B) aceitar 202 > 201; (C) manter drop. | **A**; se pisos impedirem, aceitar acima do alvo e declarar “semana mínima”, nunca abaixo da base. | ALG-03 |
| **D3** | Baseline do taper (média de 3 semanas × maior recente) | (A) Código para média de 3 semanas pré-taper, com fallback e copy; (B) Atualizar `§8.6` para maior recente com justificativa. | **ADR primeiro; sem código nesta rodada.** Recomendo A se houver 3 semanas completas; fallback explícito. | NOVO-SCI-01 |
| **D4** | Rampa do longão iniciante | (A) Limitar +10 min/semana sobre o último longão real/planejado; (B) Manter e documentar exceção. | **ADR primeiro; recomendo A**, com piso 45 e respeito à agenda. | NOVO-SCI-02 |
| **D5** | Adjacência qualidade × longão entre semanas | (A) Estender a simetria (sem qualidade no dia seguinte ao longão anterior); (B) Documentar que a virada não é coberta. | **A** como convenção `LIMITED` de produto, via ADR; não é violação já estabelecida. | ALG-02 |
| **D6** | Estímulo do iniciante no taper | (A) Strides `EASY` ≤25 min no taper; (B) Documentar “só fácil” por `R-PRESC-04`. | **A**, pois cumpre “Todos”/“nunca zero estímulo” sem criar `QUALITY`; exige ADR e teste. | SEG-03, ALG-06 |
| **D7** | Estado de graduação no Perfil | (A) Card “Próximo nível” com critérios faltantes; (B) Só mostrar quando há sugestão. | **A**; mostrar `Stay` de forma compacta, sem punir. | NOVO-LVL-01, ALG-05, SEG-05 |
| **D8** | Veredito do Story em treino livre | (A) Exigir vínculo atividade↔sessão; sem vínculo, “treino livre” sem alvo; (B) Manter associação por data e documentar limitação. | **A**; evita veredito errado em corrida não vinculada. | NOVO-STORY-01 |
| **D9** | Turno `YOU` órfão | (A) Persistir `ANSWER` determinístico de recusa/fallback; (B) Persistir `YOU` só depois da resposta; (C) Manter. | **A**; mantém o arquivo como Q&A, inclusive em falha de rede, sem LLM. | COACH-07 |
| **D10** | Tier C, GUIDED, restore e métricas | (A) Doc-only agora; (B) Implementar features. | **A**; features exigem ADR/escopo separado. | DOC-01/02/03/05, GEN-01/03/04 |
| **D11** | Escopo Story P2 | (A) Cancelar `PreparingMap`, estados da prévia, testes, 200% vídeo, `Role.Tab`, flakes, vínculo do veredito; (B) Tudo; (C) Nada. | **A**; o resto fica P3. | STORY-02/03/07/15/16/17, NOVO-STORY-01 |
| **D12** | Escopo do validador | (A) P0/P1: COACH-10/U-1/U-3; (B) também COACH-11/U-2/U-5; (C) também over-rejection V-1/V-2/V-4/V-5. | **A+B em rodadas separadas**; over-rejection depois, com testes de falso positivo. | COACH-10/11, U-1/2/3/5, V-1/2/4/5 |
| **D13** | Frequência do iniciante | (A) Aviso não bloqueante; (B) Teto duro de 5 dias; (C) Nada. | **A**; teto duro exigiria evidência/ADR. | ALG-04 |
| **D14** | Diferenciação de nível | (A) Documentar escopo atual; (B) Abrir ADR de modelo avançado. | **A** agora; B só se o produto quiser atrair avançado. | SEG-01/02 |
| **D15** | 48 dp do `McTextButton` | (A) Medir e corrigir se <48; (B) Declarar violação. | **A**; sem medição não há violação. | STORY-06 |
| **D16** | Validação em aparelho | (A) Checklist único, sem código; (B) Declarar concluída por testes JVM. | **A**; testes não substituem aparelho. | GEN-05, GEN-06 |
| **D17** | Critérios de saída | (A) P1-12 + validação em aparelho; (B) Só P1-12. | **A**. | GEN-06, NOVO-DOC-01 |
| **D18** | Veredito no frame final do Story | (A) Intencional, corrigir só a afirmação da auditoria; (B) Remover do vídeo. | **A**; o produto Story V2/V3 prevê veredito. | NOVO-STORY-02, STORY-19 |
| **D19** | COACH-01/02/03/08/17/18 | (A) Pós-MVP; (B) Agora. | **A**; não são segurança. | COACH-01/02/03/08/17/18 |
| **D20** | `COACH-12`/`V-6` número do usuário | (A) Manter recusa deliberada; (B) Autorizar números digitados. | **A**; autorrelato não vira fato do motor sem validação. | COACH-12, V-6 |
| **D21** | `SCI-03` 80/20 | (A) Alvo documentado/medido; (B) Bloqueio de plano. | **A**; bloqueio não tem evidência. | SCI-03 |
| **D22** | `ALG-09` `loadTrusted` | (A) Unificar em `objectiveDataState`; (B) Manter. | **A**; consistência com ADR 0039, sem liberar progressão. | ALG-09 |

**Critério de aceite das decisões:** D1/D2/D7/D8/D9/D11 só valem quando o teste da fatia correspondente falhar antes e passar depois; D3–D6 só valem com ADR aprovado e golden/teste definido antes do código; D10/D13–D22 só valem com documento atualizado e nenhuma promessa falsa; D15/D16 só valem com medição/checklist. Nenhuma decisão é considerada aprovada por silêncio.

---

## 5. Fatias propostas para a Fase 2

Cada fatia é pequena, tem teste próprio e pode ser revertida. A ordem abaixo é recomendada; a aprovação pode selecionar apenas algumas.

### Fatia 0 — ADRs e decisões científicas (sem código)

**Objetivo:** resolver D3, D4, D5 e D6 antes de tocar em prescrição.

- Criar/atualizar ADR para cada decisão aprovada, citando `SPORTS_SCIENCE`, evidência e impacto em `weekly-plan-v8`/`daily-prescription-v7`.
- Atualizar `SPORTS_SCIENCE.md` **somente** depois da decisão; se a decisão for doc-only, justificar a versão.
- Definir goldens/testes antes do código: `G-TAPER-01`, `G-LONG-RUN-RAMP-01`, `G-QUALITY-ADJACENCY-01`, `G-BEGINNER-TAPER-01`.
- **Aceite:** ADR aprovado; nenhum código de produção alterado nesta fatia.

**Dependência:** aprovação do usuário. Bloqueia qualquer mudança de taper/longão/adjacência/strides.

### Fatia 1 — P0 Coach: privacidade, revogação, retry e escopo médico

**IDs:** `COACH-13`, `COACH-05`, `COACH-06`, `NOVO-COACH-01`, `COACH-09`.

1. `COACH-13`: `core/data/.../CoachApi.kt` — mensagem de erro sem corpo; `CoachViewModel` loga só status. Teste com corpo secreto.
2. `COACH-05`: `CoachViewModel.revoke` — limpar `_exchanges`/`_freeAnswer`; guardas em voo não repopulam. Teste com API bloqueada.
3. `COACH-06`: `retryFreeAnswer(exchangeId)`; `FreeExchangeRow` passa `exchange.id`; usar a pergunta do exchange. Teste Q1 falha/Q2 sucesso/retry Q1.
4. `NOVO-COACH-01` + `COACH-09`: ampliar `FreeInputParser.scopePatterns` e `CoachResponseValidator.medicalTokens` para condições cardíacas/sintomas de alerta; recusa com CTA de check-in/avaliação; teste pergunta e resposta.
5. `COACH-15` pode entrar junto (copy), mas não é P0.

**Dependência:** nenhuma. É a primeira fatia recomendada.

**Risco:** aumentar demais a lista médica e recusar perguntas educativas legítimas. Mitigação: lista focada em sintomas/condições de alerta; testes de não-regressão para “o que é fartlek?” e “como correr no calor?”.

### Fatia 2 — P1 disponibilidade como teto em todos os caminhos

**IDs:** `ALG-01`, `ALG-07`, `NOVO-ALG-01`, `NOVO-ALG-02`.

- `WeeklyPlanGenerator`: filtrar dias de qualidade/micro-dose por `available >=` mínimo executável; remover dias abaixo do mínimo; `plannedDuration` não usa `coerceIn` que ultrapassa o teto; strides sem estrutura não vira sessão.
- `WorkoutRecommender`: `NO_PLAN` respeita a disponibilidade; abaixo do mínimo, `REST` com motivo.
- Testes: E14, E18, taper 10, `NO_PLAN` 10/20, strides 10; propriedade “toda sessão <= disponibilidade”.
- **Aceite:** nenhuma sessão/recomendação acima do slot; nenhuma estrutura nula; copy explica o dia removido.
- **Dependência:** D1 aprovado.

### Fatia 3 — P1 honestidade do volume

**IDs:** `ALG-03`; `ALG-08` (copy) pode entrar junto.

- `fitToVolume`: pós-validar o total; não largar sessão para overshoot ≤5%; se não couber, manter acima do alvo e declarar `minimalWeek`; `volumeRationale` usa o total entregue e o percentual real; `droppedHint` separa causa (volume × disponibilidade).
- Testes: invariante `target >= base ⇒ delivered >= base`; caso 188 × 190; arredondamento 202 × 201; copy E17 com ACWR.
- **Aceite:** copy nunca diz “+6%” quando entrega < base; nenhum teste presume 178.
- **Dependência:** Fatia 2 (a mudança de drop/fit interage com availability).

### Fatia 4 — P1 validador numérico (explain/review)

**IDs:** `COACH-10`, `U-1`, `U-3`; `COACH-11`/`U-2`/`U-5` podem ser uma segunda rodada (D12).

- Introduzir allow-list tipada (minutos, percentuais, distâncias, contagens, decimais) e usá-la em `validate`/`validateFreeAnswer`; explain/review passam conjuntos explícitos (vazios quando não há número computado).
- Testes: explain rejeita 85%, 10 km, 99 sessões, 12,5 não autorizado; aceita números do plano; free-answer não regride.
- **Aceite:** nenhum número fora do dossiê/plano passa no explain/review; resposta legítima com números do plano continua aceita.
- **Dependência:** Fatia 1 (mesmo `CoachViewModel`); pode ser feita em paralelo ao motor se o merge for cuidadoso.

### Fatia 5 — P2 UX selecionada

**IDs:** `NOVO-LVL-01`/`ALG-05`/`SEG-05`, `SCI-01`, `COACH-15`, `COACH-07` (se D9), `GEN-07`.

- Perfil: estado “Próximo nível” com `Stay.missing`; dica de disponibilidade para `MISSING_LONG_RUN`.
- Hoje: “Sem dados” no gauge quando `INSUFFICIENT_DATA` + `components.isEmpty()`.
- Copy de fallback do Coach sem “aqui embaixo”.
- Se D9: persistir `ANSWER` determinístico para recusa/fallback.
- Onboarding: “minutos são teto”; nível muda a partir de BUILD.
- **Aceite:** testes de VM/UI e screenshots; nenhuma regra de treino na UI.
- **Dependência:** D7/D9 aprovados.

### Fatia 6 — P2 Story selecionado

**IDs:** `STORY-02`, `STORY-03`, `STORY-07`, `STORY-15`, `STORY-16`, `STORY-17`, `NOVO-STORY-01`.

- Prévia: `Loading`/`Empty`/`Error`; mapa continua omitido.
- `PreparingMap`: botão Cancelar + teste.
- `Role.Tab` no segmento + teste de semântica.
- Golden/teste de 200% na aba Vídeo.
- Testes de `PreparingMap`/prévia; flakes no `TESTING.md`.
- Veredito do Story só com vínculo atividade↔sessão; treino livre sem alvo.
- **Aceite:** testes novos falham antes/passam depois; golden novo revisado; nenhuma feature nova (scrubber, pôster avulso, CTA pós-importação).
- **Dependência:** D8/D11.

### Fatia 7 — P1/P2 documentação

**IDs:** `DOC-01/02/03/04/05/06/07/08/09/10`, `GEN-01/02/03/06/07`, `NOVO-DOC-01`.

- `PRODUCT.md` v1.3: Coach/Story/Arte, barra, Tier C manual, GUIDED futuro, métricas, notificação de import, backup.
- `ROADMAP.md`: Fase 3 por subfeature; critérios P1-12 + aparelho; barra.
- `TESTING.md`: flakes de Story; contagens atualizadas.
- `DECISIONS.md`/`ARCHITECTURE.md`: barra e estado atual.
- Handoffs: status atual e próxima fatia; preservar histórico.
- PRD IA Coach: decisão 8 (timeouts), decisão 21 (CTA), escopo médico.
- **Aceite:** greps de “import pendente”, “Tier C guiado”, “P1-12 único”, barra antiga não encontram promessa falsa; doc aponta para código/teste.
- **Dependência:** pode rodar em paralelo, mas a versão final reflete as fatias concluídas.

### Fatia 8 — Validação em aparelho (manual)

**IDs:** `GEN-05`, `GEN-06`, `STORY-06` (medição).

- Checklist: revogação em voo, retry de exchange antigo, pergunta médica, E14/E18 no aparelho, Story `PreparingMap` com cancelar, prévia loading/empty/error, fonte 200%, TalkBack do segmento, `McTextButton`.
- Registrar aparelho, data, cenário e resultado; não marcar item não executado.
- **Aceite:** checklist preenchido; falhas viram bug com teste.

---

## 6. Critérios de aceite e testes de regressão

### 6.1 P0 Coach

| Teste proposto | Cenário sintético | Asserção |
|---|---|---|
| `CoachApiTest.erro não copia corpo para IOException` | Servidor local responde 400 com body `{"secret":"SEGREDO"}` | `IOException.message` não contém `SEGREDO`; log da VM contém status, não body. |
| `CoachViewModelTest.revogar limpa exchanges em voo` | `GatedCoachApi` em `freeAnswer`; `revoke()` antes de completar | `exchanges` vazio; `freeAnswer == Idle`; completar a API não repopula; `consented == false`. |
| `CoachViewModelTest.retry mira o exchange que falhou` | Q1 falha, Q2 sucesso, retry do card Q1 | Prompt do retry tem `question == Q1`; hash/persistência de Q1; `YOU` de Q1 não duplica. |
| `CoachMedicalScopeTest.arritmia não vai para resposta aberta` | “Tenho arritmia, posso correr?” | `FreeInputParser` devolve `OUT_OF_SCOPE` (ou fallback seguro); `api.freeAnswerCalls == 0`; copy com CTA. |
| `CoachResponseValidatorTest.recusa “arritmia é seguro”` | Resposta aberta “Correr com arritmia é seguro para você” | `Invalid` por escopo médico; nenhuma memória gravada. |
| `CoachViewModelTest.recusa de dor tem CTA` | “Estou com dor no joelho” | Fallback `OUT_OF_SCOPE` com CTA de check-in/avaliação; sem diagnóstico. |

### 6.2 Disponibilidade e estrutura

| Teste proposto | Cenário sintético | Asserção |
|---|---|---|
| `WeeklyPlanAvailabilityTest.E14` | 10/10/90, intermediário | Nenhuma sessão >10; dia de 10 removido (não inflado para fácil de 20); nenhuma sessão com estímulo e `structure == null`. |
| `WeeklyPlanAvailabilityTest.E18` | 20/40/40/90, BUILD | Qualidade não tem 25 min em slot de 20; dia vira fácil ≤20 ou é removido. |
| `WeeklyPlanAvailabilityTest.taper micro-dose` | 10 min na terça, prova | Nenhuma micro-dose >10; sem sessão de 25. |
| `WorkoutRecommenderTest.NO_PLAN 10 min` | Sem plano, disponibilidade 10 | `durationMin <=10` ou `REST` com motivo; nunca 20. |
| `WeeklyPlanAvailabilityTest.strides 10 min` | Strides em slot de 10 | Sem sessão “Acelerações curtas” sem estrutura; dia removido/rebaixado. |
| `WeeklyPlanAvailabilityTest.property` | Matriz de níveis/fases/agendas 1..90 min | Para toda sessão, `durationMin <= availability[day]`; toda sessão com estímulo (qualidade, micro-dose, strides) tem estrutura executável; fácil/longão podem não ter estrutura por design. |

### 6.3 Volume

| Teste proposto | Cenário sintético | Asserção |
|---|---|---|
| `VolumeHonestyTest.target não entrega menos que a base` | Matriz base 120/190/200, alvos 6%, agendas 3–6 dias | Se `target >= base`, `weeklyMinutes >= base`; se não, copy não diz “+%”. |
| `VolumeHonestyTest.overshoot de 1 min não larga sessão` | `fitToVolume` com total 202, alvo 201 | Nenhuma sessão removida; total 201 ou 202 declarado; copy não culpa disponibilidade. |
| `VolumeHonestyTest.caso 188 vs 190` | Cenário sintético da revisão | `weeklyMinutes >= 190` ou copy declara o undershoot e a causa; não usar 178. |
| `WeeklyPlanGeneratorTest.E17 copy` | ACWR 1,7 + disponibilidade limitando | Copy cita disponibilidade **e** ACWR. |

### 6.4 Validador

| Teste proposto | Cenário | Asserção |
|---|---|---|
| `CoachExplainAllowListTest.rejeita números inventados` | Explain com “85%”, “10 km” e “99 sessões” | `Invalid`; fallback determinístico. |
| `CoachExplainAllowListTest.aceita números do plano` | Explain cita 40 min e total semanal | `Valid`. |
| `CoachNumberUnitsTest.decimais` | “12,5 unidades” e “12.5 unidades” | `Invalid` se não autorizado; decimal autorizado passa. |
| `CoachNumberUnitsTest.unidades` | “dormiu 6 horas” com 6 em contagem | `Invalid`; “6 treinos” passa se 6 é contagem. |
| `CoachStructuralChangeTest.troca` | “Troque o intervalado por uma rodagem leve” | `Invalid` como prescrição. |
| `CoachResponseValidatorTest.V-2` | “Hoje é descanso — descanse bem” | `Valid`. |

### 6.5 Decisões científicas (somente após ADR)

| Teste/golden proposto | Cenário | Asserção |
|---|---|---|
| `G-TAPER-01` | 3 semanas pré-taper conhecidas | Se D3=A, volume de taper = −45–55% da média das 3 semanas; fallback nomeado. |
| `G-LONG-RUN-RAMP-01` | Iniciante, longão anterior 45, agenda 90 | Se D4=A, longão ≤55; piso 45; sem sessão acima da agenda. |
| `G-QUALITY-ADJACENCY-01` | Longão no domingo anterior, BUILD | Se D5=A, qualidade não cai na segunda; se não houver dia seguro, copy explica. |
| `G-BEGINNER-TAPER-01` | Iniciante, prova em 10 dias | Se D6=A, 1 strides `EASY` ≤25 min; nenhum `QUALITY`; se B, só fácil e doc. |

### 6.6 Story e UX

| Teste proposto | Cenário | Asserção |
|---|---|---|
| `StoryPreviewStatesTest` | `plan == null`, loading, erro | `Loading` com spinner; `Empty` com copy de GPS; `Error` distinto; nenhum erro falso no primeiro frame. |
| `StoryPreparingMapTest` | `PreparingMap` | “Cancelar” visível; clique volta `Idle`; sem notificação/worker. |
| `StoryStudioScreenshotTest.video 200%` | Aba Vídeo, 360 dp, fontScale 2 | Golden/teste sem corte; contraste AA. |
| `StoryStudioContentTest.Role.Tab` | Segmento | `Role.Tab` presente; seleção/descrição preservadas; anúncio do segmento oculto é opcional. |
| `StoryVerdictLinkTest` | Corrida livre em dia planejado × corrida vinculada | Livre: sem alvo/veredito; vinculada: veredito; worker e prévia iguais. |
| `LevelGraduationStayTest` | `Stay` com `MISSING_LONG_RUN` e agenda 50 | Perfil mostra “longão real ≥60” + dica de slot/extra; sem aceite automático. |
| `TodayReadinessNoDataTest` | Sem check-in/métricas | Gauge “Sem dados” + CTA; não “Moderada 50”. |

---

## 7. Dependências entre itens

```text
D1 (disponibilidade) ──► ALG-01/NOVO-ALG-01/NOVO-ALG-02/ALG-07 ──► ALG-03
D3/D4/D5/D6 (ADRs) ──► NOVO-SCI-01/NOVO-SCI-02/ALG-02/SEG-03
COACH-13 ──► COACH-05 ──► COACH-06   (mesmo ViewModel/API; ordem evita conflito)
NOVO-COACH-01/COACH-09 ──► COACH-15 (copy de fallback)
COACH-10/U-1/U-3 ──► COACH-11/U-2/U-5 (segunda rodada do validador)
NOVO-LVL-01/ALG-05/SEG-05 ──► GEN-07 (copy de onboarding/nível)
NOVO-STORY-01 ──► STORY-02/03/16 (mesma tela/worker; D8)
DOC-06/07/10 ──► DOC-01/02/03/04/05/08/09 (versão final reflete o entregue)
GEN-05/GEN-06 ──► validação em aparelho (não substituível por teste JVM)
```

**Regras de sequência:**

1. Não implementar taper/longão/adjacência/strides antes do ADR da Fatia 0.
2. Não implementar `ALG-03` antes de `ALG-01`, porque o `fitToVolume` e o drop interagem com a disponibilidade.
3. Não implementar `COACH-11` antes da allow-list tipada de `COACH-10`, para não duplicar validação.
4. Não atualizar `PROGRESS.md`/`ROADMAP.md` com itens “concluídos” antes de testes e, quando aplicável, aparelho.
5. Nenhuma migração de schema é esperada nestas fatias; se surgir, exige ADR e teste de migração sem `fallbackToDestructiveMigration`.

---

## 8. Não requisitos, refutados e não verificáveis

### 8.1 Refutado

- **V-3 (percentual decimal 82,5%).** Com fato “82%”, “82,5%” é um número diferente; a recusa é correta. A mensagem pode citar `5%`, mas isso não transforma a recusa em falso positivo. **Não** alterar o validador para aceitar 82,5%.
- **U-4 no exemplo literal.** “Corra 5 mil metros” é barrado pela guarda de verbo + número. O teste de U-4 não deve exigir que essa frase passe; o requisito, se aprovado, é cobrir formulações declarativas sem verbo.

### 8.2 Não verificável

- **STORY-06 (48 dp).** O padding 8×4 é fato; o alvo final não foi medido. Material 3 pode expandir o alvo. A ação é medir; não declarar violação.
- **GEN-05 (aparelho).** Não houve aparelho/emulador. A ação é checklist manual; não marcar validação como concluída.

### 8.3 Não são requisitos automáticos

- **DOC-01/02/03/05/06/07/08/10 e GEN-01/02/03/06/07:** reconciliação documental. Implementar Tier C/GUIDED/restore/métricas é decisão de produto separada (D10).
- **STORY-01/04/05/08/09/10/11/12/14/18:** melhorias, decisões intencionais ou itens já adiados. Entram em P3 somente se aprovados.
- **COACH-01/02/03/08/17/18:** multi-turn, streaming, feedback, hidratação, refatoração e a11y são pós-MVP/P3; não são segurança.
- **SEG-01/02/04/06:** diferenciação de nível, rebaixamento e uso de idade são produto/documento; não mudar o motor sem ADR.
- **ALG-04:** teto duro de frequência não tem evidência demonstrada; a opção recomendada é aviso não bloqueante.
- **SCI-03:** 80/20 é alvo, não gate; não bloquear planos por distribuição sem ADR.
- **STORY-19 / NOVO-STORY-02:** o veredito no frame final é intencional; corrigir a afirmação da auditoria, não o renderer.

---

## 9. Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Correção de disponibilidade remover dias demais e esvaziar a semana | Copy explícita “slot abaixo do mínimo”; manter pelo menos um dia se houver slot válido; testes de matriz. |
| `ALG-03` aceitar overshoot e violar o alvo | Tolerância ≤5%; se pisos impedirem, declarar “semana mínima” acima do alvo; nunca abaixo da base. |
| Validador mais estrito rejeitar respostas legítimas | Allow-list tipada + testes de falso positivo; over-rejection separada (V-1/V-2/V-4/V-5) em rodada própria. |
| Lista médica ampliar demais e recusar educação | Lista focada em sintomas/condições de alerta; testes “fartlek”, “calor”, “como correr” continuam passando. |
| ADR científico atrasar P1 | P0/P1 de segurança e disponibilidade não dependem de ADR; ciência fica em fatia separada. |
| Mudança de prescrição quebrar goldens | Fatia 0 define golden antes; versão do motor sobe só quando a regra mudar. |
| Documentação prometer feature não entregue | Doc-only explícito; “futuro” e “não medido” marcados. |
| Teste de Story flaky | Tests isolados e `waitUntil`; registrar flakes no TESTING; não usar `@Ignore`. |

---

## 10. Definition of Done da Fase 2

Para cada fatia aprovada:

- [ ] Teste de regressão sintético escrito antes da correção (TDD quando possível).
- [ ] `./gradlew :core:domain:test`, `:core:data:testDebugUnitTest` e/ou `:app:testDebugUnitTest` relevantes verdes.
- [ ] `./gradlew check` verde.
- [ ] `./gradlew spotlessCheck` verde.
- [ ] `./gradlew :app:assembleDebug` verde quando houver UI.
- [ ] `koverVerify` verde quando `core:domain` mudar.
- [ ] Nenhuma dependência nova sem justificativa.
- [ ] Nenhuma migração destrutiva.
- [ ] Nenhum dado de saúde real no diff/log/fixture.
- [ ] `SPORTS_SCIENCE.md` + ADR atualizados se a regra científica mudar.
- [ ] `PROGRESS.md`/`ROADMAP.md` atualizados **somente** para o que foi realmente concluído.
- [ ] Nenhuma validação de aparelho marcada sem execução.
- [ ] Achados refutados/não implementados registrados com motivo.

---

## 11. Aprovação solicitada

Peço aprovação explícita para:

1. **P0 (recomendado começar por aqui):** `COACH-13`, `COACH-05`, `COACH-06`, `NOVO-COACH-01`, `COACH-09`.
2. **P1:** `ALG-01` + `NOVO-ALG-01` + `NOVO-ALG-02` + `ALG-07`; `ALG-03`; `COACH-10`/`U-1`/`U-3`.
3. **Decisões científicas:** D3, D4, D5, D6 — aprovar **ADR primeiro**, sem código nesta rodada, ou escolher explicitamente a opção.
4. **P2 de UX/Story:** D7, D8, D9, D11 (lista recomendada; o resto fica P3).
5. **Documentação:** D10 e Fatia 7, com `PRODUCT.md` v1.3 e critérios de saída corrigidos.
6. **Fora de escopo:** confirmar que P3 (multi-turn, streaming, feedback, Tier C player, restore, pôster avulso, scrubber, CTA pós-importação, refatoração do `CoachViewModel`) **não** entra agora.

Se aprovado, começo pela Fatia 1 (P0), com TDD e sem tocar nas alterações preexistentes de `docs/PROGRESS.md` e dos documentos de auditoria.

---

## 12. Registro de aprovação interativa

| # | Tema | Resposta | Data |
|---|---|---|---|
| 1 | Fatia 1 — P0 completo (`COACH-13`, `COACH-05`, `COACH-06`, `NOVO-COACH-01`, `COACH-09`) | **Aprovado** | 26/11/2026 |
| 2 | Fatia 2/3/4 — P1 completo (disponibilidade, volume e validador) | **Aprovado** | 26/11/2026 |
| 3 | D3–D6 — processo científico | **ADR-first aprovado**, direção inicial A (média de 3 semanas; +10 min/semana; estender adjacência; strides `EASY` no taper) | 26/11/2026 |
| 4 | D1 — disponibilidade abaixo do piso | **Aprovado A**: remover o dia/`REST`; `NO_PLAN` devolve `REST` com motivo | 26/11/2026 |
| 5 | D2 — overshoot de volume | **Aprovado A**: aparar até 5% sem largar sessão; se pisos impedirem, aceitar acima do alvo e declarar “semana mínima” | 26/11/2026 |
| 6 | D7 — graduação (`Stay`) | **Aprovado A**: card “Próximo nível” com critérios faltantes e dica de disponibilidade | 26/11/2026 |
| 7 | D8 — veredito do Story | **Aprovado A**: exigir vínculo atividade↔sessão; treino livre sem alvo/veredito | 26/11/2026 |
| 8 | D9 — turno `YOU` órfão | **Aprovado A**: persistir `ANSWER` determinístico de recusa/fallback, sem LLM | 26/11/2026 |
| 9 | D10 — Tier C/GUIDED/restore/métricas | **Aprovado A**: doc-only agora; features exigem ADR/escopo separado | 26/11/2026 |
| 10 | D11 — pacote Story P2 | **Aprovado A**: STORY-02/03/07/15/16/17 + NOVO-STORY-01 | 26/11/2026 |
| 11 | D12 — validador além do P1 | **Aprovado B**: incluir COACH-11, U-2 e U-5; over-rejection V-1/V-2/V-4/V-5 depois | 26/11/2026 |
| 12 | D13–D22 — decisões restantes | **Aprovadas as recomendações**: aviso de frequência, escopo de nível, medir 48 dp, checklist de aparelho, dois critérios de saída, veredito intencional, Coach avançado em P3, recusa de número do usuário, 80/20 como alvo e `loadTrusted` unificado | 26/11/2026 |
| 13 | Início da Fase 2 | **Adiado para sessão nova**; handoff gerado com escopo P0/P1 aprovado e ordem de fatias | 26/11/2026 |
