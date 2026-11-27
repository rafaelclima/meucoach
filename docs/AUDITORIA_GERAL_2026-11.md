# AUDITORIA_GERAL_2026-11 — MeuCoach

**Data:** 26/11/2026 · **Escopo:** motor de geração de treinos, variações de nível (iniciante/intermediário/avançado), Story Studio, Coach IA, documentação de produto e UX geral.
**Método:** leitura de código + execução de experimentos determinísticos com perfis sintéticos + auditoria de documentação × implementação.
**Propósito:** documento auditável por outro modelo LLM. Ele foi estruturado para ser **confrontado**, não apenas concordado.

---

## 1. Como ler e auditar este documento

### 1.1 Camadas de cada achado

Todo achado é decomposto em quatro camadas separadas, para que o revisor possa derrubar uma sem derrubar as outras:

| Camada | O que é | Como derrubar |
|---|---|---|
| **Fato** | Observação verificável com evidência citável (`arquivo:linha`, número de teste ou experimento `E#`). | Mostrar que a evidência está errada ou não existe. |
| **Inferência** | Raciônio que liga o fato a um problema. | Mostrar uma leitura alternativa igualmente válida dos mesmos fatos. |
| **Juízo** | Severidade + confiança, com critério explícito (§1.2). | Mostrar que o impacto real é menor/maior que o declarado. |
| **Sugestão** | Ação proposta. **Opcional:** um achado continua válido mesmo que a sugestão seja rejeitada. | Aceitar o achado e rejeitar a sugestão; ou propor melhor alternativa. |

### 1.2 Escalas

**Severidade** (impacto no usuário ou no produto se o achado for real):

- **Crítica** — risco à saúde do usuário, violação de privacidade, ou dado/plano errado servido como certo.
- **Alta** — comportamento divergente do documento de ciência (lei do projeto), função prometida e não entregue, ou perda de confiança auditável no motor.
- **Média** — problema real de borda, cópia enganosa, gap de UX que causa abandono, ou diferenciação de produto insuficiente.
- **Baixa** — incômodo, melhoria cosmética ou edge case raro; consertar quando tocar no arquivo.

**Confiança** do achado (não da sugestão):

- **Alta** — reproduzido por experimento ou por leitura direta do caminho de código, com evidência primária.
- **Média** — leitura direta, mas o impacto depende de comportamento não observado (UI, dados reais, uso do usuário 0).
- **Baixa** — hipótese plausível baseada em padrão; precisa de verificação adicional antes de agir.

### 1.3 Regras de imparcialidade deste documento

1. **Fato ≠ opinião.** Nenhum achado é apresentado como certo sem a evidência que o sustenta.
2. **O doc de ciência é a lei, mas não é infalível.** Onde `docs/SPORTS_SCIENCE.md` e o código divergem, o achado aponta **ambas** as possibilidades ("código errado OU doc desatualizado") e diz qual a regra do próprio projeto (`AGENTS.md §5`: atualizar doc **e** código com justificativa).
3. **Contrabalanço obrigatório.** A Seção 8 lista explicitamente o que foi audado como **bom** — uma auditoria só de problemas é um documento enviesado.
4. **Nada foi simulado.** Experimentos rodam no motor real (`WeeklyPlanGenerator`, `DailyPrescriptionEngine`, `QualityGate`, `SessionLibrary`, `LevelGraduationEngine`) com fixtures sintéticas; nenhum dado de saúde real foi usado (regra `AGENTS.md §4`).
5. **Confiança declarada é para o achado.** Sugestões de produto (UI/UX, features) são rotuladas como sugestão e podem ser rejeitadas sem invalidar a auditoria.

### 1.4 Inventário de IDs

| Prefixo | Seção | Total |
|---|---|---|
| `ALG-xx` | Algoritmo de geração de treinos (experimentos E1–E19) | 9 |
| `SEG-xx` | Variações de corredor (iniciante/intermediário/avançado) | 6 |
| `SCI-xx` | Ciência do esporte / doc × literatura | 4 |
| `STORY-xx` | Story Studio (UI/UX) — 17 achados + lacunas doc×código + fortes | 19 |
| `COACH-xx` | Coach IA — 18 achados + nota de privacidade + fortes | 19 |
| `V-x` / `U-x` | Validador do Coach — over/under-rejection | 13 |
| `DOC-xx` | Documentação × implementação | 8 |
| `GEN-xx` | Geral (produto, processo, UX do app) | 7 |

---

## 2. Método e escopo

### 2.1 O que foi lido integralmente

- `core/domain/.../prescription/`: `WeeklyPlanGenerator.kt` (1.677 linhas), `DailyPrescriptionEngine.kt` (718), `SessionLibrary.kt` (582), `QualityGate.kt` (345), `PrescriptionTargets.kt`, `MesoCycle.kt`, `VolumeProgression.kt`, `VolumeReference.kt`, `WorkoutStructure.kt`, `WorkoutRecommender.kt`, `LevelGraduationEngine.kt`, `RacePlan.kt`, `InitialExperienceSuggester.kt`.
- `core/domain/.../readiness/ReadinessCalculator.kt`; `core/domain/.../load/TrainingLoadEngine.kt` (snapshot/gates); `core/model/AthleteProfile.kt`, `DailyCheckIn.kt`.
- `docs/SPORTS_SCIENCE.md` (702 linhas), `docs/PROGRESS.md` (sessões 23–25/11), `docs/ROADMAP.md`, `docs/HANDOFF_IA_COACH.md`.
- Auditorias delegadas (subagentes de exploração, somente leitura): Story Studio (domínio/app/UI/export/PRDs/ADRs), Coach IA (domínio/data/app/proxy/PRDs/ADRs), documentação × código (PRODUCT/ROADMAP/handoffs/gates Gradle). **Achados dessas três frentes estão nas Seções 5, 6 e 7 com citação `arquivo:linha` originales; três alegações críticas do Coach foram verificadas pessoalmente pelo auditor (COACH-05, COACH-06, COACH-13 — confirmadas).**

### 2.2 Experimentos executados (motor real, fixtures sintéticas)

Harness temporário (código no Apêndice A; apagado do repositório após a coleta para não poluir a suíte). Saída determinística em `/tmp/opencode/audit-experiments.txt`.

| # | Cenário | Resultado resumido |
|---|---|---|
| E1 | Mesma disponibilidade (4 dias) × 3 níveis, tudo verde | Iniciante ≡ Intermediário (byte a byte); Avançado só muda longão 60 vs 45 |
| E2 | Semana de taper de 5K (10 dias) × 3 níveis | Iniciante: 0 estímulos, sem strides; Int/Av: micro-dose 25 min |
| E3 | Disponibilidade 10 min (dia de strides) | 10 min — respeita o teto (caminho `minOf`) |
| E4 | Slot de 20 min na BASE | Strides 20 min — respeita o teto |
| E5 | Iniciante 7 dias × 40 min | 7 sessões, **nenhum descanso, nenhum aviso de frequência** |
| E6 | Iniciante com slot máximo 50 min | Longão 50 < 60 (critério de graduação); título normal, sem hint |
| E7 | Avançado 6 dias + descarga | −25% aplicado; qualidades mantidas (por design); 138 min |
| E8 | Iniciante 5K em 5 sem., slot máx. 55 min | 2 dias, sem qualidade, sem caminho para longão de 60 |
| E9 | Intermediário com apenas 2 dias | Sem qualidade ("menos de 3 dias") — conforme doc |
| E10 | Matriz do gate do dia (6 cenários) | **6/6 conforme doc**: KEEP/DOWNGRADE/REST com motivo nomeado |
| E11 | Progressão de volume com teto de disponibilidade | Capping correto, copy correta |
| E12 | Catálogos BUILD/PEAK por nível | Diferenciação existe (reps/durações; PEAK Av = 2 estímulos) |
| E13 | Catálogo TAPER por nível | `easy=[Rodagem]` para **todos** os níveis |
| E14 | Dois slots de 10 min (um vira fácil simples) | **Quarta planejada 20 min > disponibilidade 10** |
| E15 | BUILD com slot de 20 min | Fácil 20 = teto ok; 2 qualidades na descarga |
| E16 | Crescimento +6% com disponibilidade folgada (verde × âmbar) | Âmbar bloqueia (ok); **verde: copy "+6% alvo 201" entrega 178 < base 190** |
| E17 | ACWR confiável 1,7 | Sem crescimento (ok); copy cita só disponibilidade, bloco ACWR some |
| E18 | BUILD com 1ª qualidade em slot de 20 min | **Terça planejada 25 min > disponibilidade 20** |
| E19 | Longão do domingo anterior + BUILD | **Qualidade cai na segunda, colada no longão da semana anterior** |

### 2.3 O que NÃO foi coberto (limites honestos)

- **Não** foi executado o app em dispositivo/emulador (validações de aparelho pendentes listadas em `GEN-05` continuam pendentes).
- **Não** foi revisada a correção numérica das fórmulas de TRIMP/rTSS/VDOT contra referência externa (há testes e goldens; não reproduzidos aqui).
- **Não** foi feita auditoria de segurança de rede do proxy além da leitura (sem pentest).
- **Não** foi avaliada a qualidade clínica das respostas do LLM em produção (depende de uso real; P1-12).
- Cobertura de branches dos módulos novos (app ≥78%, data ≥85%) foi lida nas configs, não medida nesta sessão.
- Fixtures de teste do gerador (`WeeklyPlanGeneratorTest`) foram lidas, não reescritas.

---

## 3. Sumário executivo

### 3.1 Top 10 dos achados mais relevantes

| # | ID | Achado em uma frase | Sev. | Conf. |
|---|---|---|---|---|
| 1 | `ALG-01` | Disponibilidade diária abaixo do piso (10–24 min) é estourada: o plano agenda mais minutos do que o usuário declarou, contra a regra de "teto rígido" do doc §8.3. | Média | Alta |
| 2 | `ALG-03` | Em semana com encaixe de volume, o rationale anuncia "+6%, alvo de 201 min" e entrega 178 min — **menos** que a base de 190 — sem copy que explique o undershoot. | Média | Alta |
| 3 | `SEG-03` | Iniciante na semana de prova não recebe nem micro-dose nem strides, divergindo de `SPORTS_SCIENCE §8.4` ("strides: Todos"). | Média | Média |
| 4 | `ALG-04` | Iniciante com 7 dias disponíveis recebe 7 corridas sem nenhum aviso de frequência — sem teto de dias por nível em lugar nenhum do motor. | Média | Alta |
| 5 | `SEG-01/02` | Iniciante ≡ Intermediário nas primeiras semanas de BASE e Avançado só difere em reps/piso de longão: a escolha de nível quase não muda o plano nas primeiras 2–4 semanas. | Média | Alta |
| 6 | `ALG-02` | Qualidade pode cair na segunda-feira colada no longão de domingo da semana anterior — a regra de adjacência é aplicada só dentro da semana. | Média | Média |
| 7 | `DOC-01/02/03/06` | PRODUCT.md promete teste guiado Tier C, execução GUIDED e métricas sem instrumentação, e não conhece a aba Coach nem o Story Studio. | Alta | Alta |
| 8 | `STORY-01/02/03` | A feature mais viralizável está a 3 toques + scroll de 8 cards e atrás de GPS; prévia funde loading com erro; `PreparingMap` (até 45 s) não tem cancelar. | Média | Alta |
| 9 | `COACH-10/U-1` | O caminho `explain` do validador não checa percentual/distância/dígito nu — pode passar "85% da semana" ou "10 km" inventados, contra o PRD §5. | Alta | Média |
| 10 | `COACH-01` | Sem multi-turn real: cada pergunta vai ao modelo sem histórico; follow-ups ("e amanhã?") dependem só de regex do parser. | Média | Alta |

### 3.2 Contagem por severidade

| Severidade | Qtd | IDs |
|---|---|---|
| Alta | 3 | `DOC-01/02/03/06` (agrupado), `COACH-10`, `U-1` |
| Média | 21 | `ALG-01..05`, `SEG-01..04`, `SCI-01`, `STORY-01..05,07,08,10`, `COACH-01,04,05,07,09,11`, `GEN-03,05` |
| Baixa | 23 | demais |

> Os achados de **Crítica** foram propositalmente nenhum: nenhum risco imediato à saúde, nenhum vazamento confirmado de privacidade (os riscos de log/consentimento do Coach estão em `COACH-05/13` com severidade Média por serem condicionais).

### 3.3 Veredito geral

O motor é **consistente, determinístico e notavelmente aderente ao seu documento de ciência** — a matriz do gate do dia (E10) acertou 6/6 cenários, os blocos de volume nomeiam a causa (E11/E16 âmbar) e os goldens protegem as regras. Os problemas reais se concentram em: (i) **bordas de disponibilidade** onde o piso por sessão vence o teto declarado (`ALG-01`), (ii) **honestidade da cópia** em encaixes de volume (`ALG-03`), (iii) **diferenciação de nível mais fraca do que o produto promete** (`SEG-01/02`), (iv) **documentação de produto desatualizada** (`DOC-*`), e (v) duas lacunas estruturais no Coach: validador assimétrico entre caminhos (`COACH-10`) e ausência de multi-turn (`COACH-01`).

---

## 4. Seção A — Algoritmo de geração de treinos

### [ALG-01] Piso por sessão vence o teto de disponibilidade declarado

**Severidade:** Média · **Confiança:** Alta · **Categoria:** algoritmo × doc

**Fato.** `plannedDuration` aplica `available.coerceIn(MIN_EASY_MIN, MAX_EASY_MIN)` (`WeeklyPlanGenerator.kt:1246`) e `available.coerceIn(MIN_QUALITY_MIN, MAX_QUALITY_MIN)` (`:1238`). Com disponibilidade 10 min, `coerceIn(20, 60)` devolve **20**; com 20 min em dia de qualidade, devolve **25**. Reproduzido:
- **E14:** segunda 10 / quarta 10 / sábado 90 → quarta planejada **20 min > 10 declarados** (o dia de strides, que usa `minOf(available, MICRO_DOSE_MIN)` em `:1242`, respeitou 10 — os dois caminhos do mesmo método tratam o sub-piso de forma diferente).
- **E18:** terça 20 / qui 40 / sáb 40 / dom 90, BUILD → terça (1ª qualidade) planejada **25 min > 20 declarados**.
A validação de entrada aceita o caso: `WeeklyAvailability` permite `1..1440` min/dia (`AthleteProfile.kt:58-72`) e o onboarding valida `1..MAX` (`OnboardingForm.kt:193`).

**Inferência.** O `AGENTS.md`/doc definem a disponibilidade como teto rígido: `SPORTS_SCIENCE §8.3` — "A disponibilidade continua sendo teto rígido: … quando os mínimos por sessão não couberem a semana **usa menos dias**". O código faz o oposto do que o doc manda: em vez de remover o dia, **estoura o teto** para dentro do piso. A regra interna do próprio gerador ("availability is a hard ceiling", KDoc em `:195-206`) é violada pelo próprio método.

**Impacto.** Usuário com slots muito curtos (caminhada+jogada, agendas de 10–20 min) recebe sessões maiores do que o tempo que declarou ter. Baixa frequência no uso real, mas é entrada válida do sistema e contradição direta com a lei documentada.

**Sugestão.** Em `plannedDuration`, quando `available < MIN`, aplicar a regra do doc: **remover o dia** (ou marcá-lo como indisponível) em vez de inflar a sessão; e unificar o caminho de strides ao mesmo comportamento. Alternativa menos invasiva: limitar o input no onboarding a `≥ MIN_EASY_MIN` com copy explicando o piso.

**Como refutar.** (1) Rode o Apêndice A com E14/E18: se `durationMin ≤ availability` em ambos, o achado cai. (2) Mostre um clamp posterior (ex.: em `fitToVolume`/`applyLongRunHonesty`) que reduza a sessão de volta ao teto — E14 mostra que não há. (3) Mostre no doc uma emenda que autorize piso > teto.

---

### [ALG-02] Regra de adjacência qualidade × longão não cruza a fronteira de semanas

**Severidade:** Média · **Confiança:** Média · **Categoria:** algoritmo × ciência

**Fato.** `selectQualityDays` (`WeeklyPlanGenerator.kt:936-945`) exclui dias adjacentes ao longão **apenas da semana corrente** (`longDay`) e a outras qualidades da mesma semana. O generator recebe `previousWeek`/`recentWeeks` e usa-os só para conclusão/mesociclo/volume (`:307-310, 366, 688-715`), nunca para adjacência. **E19:** fixture com longão de 70 min no **domingo da semana anterior** + BUILD → a qualidade caiu na **segunda-feira**, colada no longão anterior (`quality days=[MONDAY]… qualityRightAfterLong=true`). Dentro da semana, a mesma proximidade seria rejeitada (a regra é simétrica via `adjacentDays`, `:940`).

**Inferência.** O motor trata "qualidade colada ao longão" como indesejável dentro da semana (bloqueia os dois sentidos), mas não aplica o mesmo princípio na virada — inconsistência da própria regra, não apenas lacuna externa. `SPORTS_SCIENCE §8.5` proíbe explicitamente "longão no dia seguinte a qualidade" (direção oposta), então o doc não cobre este caso; o argumento de suporte é a simetria interna do código + prática de treino (qualidade em pernas fatigadas de longão).

**Impacto.** Semana com qualidade na segunda após domingo de longão: estímulo forte em recuperação incompleta. Baixa frequência (depende do padrão de disponibilidade), severidade limitada porque o gate do dia reavalia prontidão — mas prontidão verde não detecta fadiga localizada do longão anterior.

**Sugestão.** Estender `selectQualityDays` para excluir o dia seguinte (e anterior) ao último `WorkoutType.LONG` de `previousWeek` quando houver, mantendo o fallback sem histórico inalterado. Registrar como convenção de produto (`LIMITED`) no doc.

**Como refutar.** (1) Mostrar que algum caminho já considera o longão anterior (grep por uso de `previousWeek.sessions` na seleção de dias — não existe hoje). (2) Argumentar, com base em doc/ADR, que a adjacência é intencionalmente semanal e que E19 é comportamento desejado — nesse caso o doc deve registrar a decisão (hoje não registra).

---

### [ALG-03] Rationale anuncia crescimento que a semana entrega menor que a base

**Severidade:** Média · **Confiança:** Alta · **Categoria:** algoritmo × auditabilidade

**Fato.** **E16 (verde):** base 190 min, disponibilidade 330, todos os gates verdes → `volumeRationale` imprime *"+6% nesta semana, **alvo de 201 min**"* (`:1554-1556`) e, na mesma razão, *"A sua disponibilidade permite mais dias…"* (`:1528-1532`, `droppedSessions > 0`). A semana entregue: **178 min em 3 sessões < base 190** (`weeklyMinutes: 178`). Causa raiz reproduzida: o layout pré-fit soma 210; `fitToVolume` escala para 201; o arredondamento fecha em **202** (1 min acima do alvo); `dropShortestEasy` (`:1451-1466`) larga **uma sessão inteira** (o dia de strides, 24 min — sempre o "easy mais curto") para resolver 1 minuto, e o total cai para 178. `growthPercent` é calculado **antes** do fit real (`:146-155`) e nunca é reavaliado; `minimalWeek` só cobre o caso contrário (acima do alvo, `:1433`).

**Inferência.** Três violações do princípio de auditabilidade do próprio produto: (1) a cópia afirma crescimento quando a semana encolheu 6% vs. base; (2) o hint de `droppedSessions` atribui a causa à **disponibilidade** (330 min — nunca o limitante) quando a causa foi o alvo de volume; (3) largar uma sessão inteira para caber 1 minuto é desproporcional — poderia aparar minutos ou aceitar 202 > 201.

**Impacto.** Usuário lê "+6%" e vê semana menor que a anterior. Erosão exatamente no atributo que o produto vende ("por quê" auditável). Reproduzível com fixtures realistas; não depende de caso exótico (qualquer arredondamento 1 min acima do alvo com strides dispara).

**Sugestão.** (a) Recalcular `growthPercent`/copy **depois** do `fitToVolume`, derivando o percentual real de `weeklyMinutes` vs `baseMinutes`; (b) em `dropShortestEasy`, só largar sessão se o overshoot for maior que uma fração (ex.: >5% do alvo) — caso contrário aparar a sessão mais longa; (c) corrigir `droppedHint` para nomear a causa real (alvo de volume × disponibilidade × pisos).

**Como refutar.** (1) Rode E16 do Apêndice A e confira `weeklyMinutes ≥ 201` — se for ≥, o achado cai. (2) Mostre que `minimalWeek` ou outra branch do `volumeRationale` já cobre o undershoot (a leitura do código em `:1520-1562` não mostra). (3) Argumente que "alvo" ≠ "entrega" e que o usuário entende — nesse caso o achado vira apenas severidade Baixa (a contradição factual "+6% < base" continua).

---

### [ALG-04] Sem teto de frequência por nível — iniciante pode planejar 7 dias

**Severidade:** Média · **Confiança:** Alta · **Categoria:** algoritmo × ciência

**Fato.** O gerador agenda **uma sessão por dia disponível**, sem qualquer limite de dias por experiência: `sessions = (days + postRaceDays).map { … }` (`:502`) e nenhuma checagem de `profile.experience` contra `days.size` além de `days.size >= 3` para qualidade (`:360-365`). **E5:** iniciante com 7 dias × 40 min → **7 corridas, 265 min, nenhum descanso, nenhum aviso** no rationale. Busca no motor por regra de frequência: nenhuma (`grep` por "dias/semana", "frequência", max-days: 0 hits em `core/domain`).

**Inferência.** `SPORTS_SCIENCE §8.2` apresenta a semana padrão **com dois dias de descanso** e o doc cita `EV-INJURY-2015` (17,8 vs 7,7 lesões/1.000 h em novatos) como razão da conservadoria com iniciantes — mas a conservadoria aplica-se só à qualidade, não à frequência. O motor confia cegamente na disponibilidade; um iniciante que marca 7 dias (a UI permite) recebe 7 corridas sem nenhuma fricção. O teto de volume (+6%) não protege aqui: E5 entregou 265 min sem histórico (teto = disponibilidade).

**Impacto.** Risco de overuse em novatos exatamente no perfil do produto (usuário 0: iniciante, 97 kg, retornando). Contradiz o princípio "Segurança primeiro" do doc §2 na dimensão frequência.

**Sugestão.** Regra nova (`R-PLAN-12`?) com evidência: para `BEGINNER`, limitar a **5 dias/semana** (ou avisar no onboarding/gerador: "Iniciantes costumam treinar 4–5 dias; você marcou 7 — quer manter?"). Intermediário/avançado: aviso não bloqueante. Toda regra nova exige evidência + teste (`AGENTS.md §5`).

**Como refutar.** (1) Encontre a regra de frequência no código/doc que eu não encontrei (grep `maxDays`, "frequência" em `core/domain`). (2) Mostre que a UI de disponibilidade já impõe um teto de dias (não impõe: `OnboardingForm` valida minutos/dia, não quantidade de dias). (3) Argumente que disponibilidade é escolha do usuário e o app não deve tutelar — position defensável, mas aí o doc §8.2 deve dizer "o app espelha sua disponibilidade, inclusive 7 dias" (hoje não diz).

---

### [ALG-05] Iniciante com slot < 60 min nunca cumpre o critério de longão da graduação

**Severidade:** Média · **Confiança:** Alta · **Categoria:** algoritmo × produto

**Fato.** `LevelGraduationEngine` exige longão **real ≥ 60 min** (`LONG_RUN_60_MIN`, `LevelGraduationEngine.kt:402, 262-268`) para `BEGINNER → INTERMEDIATE`. O gerador dimensiona o longão por `minOf(maxAvailable, cap, target)` (`WeeklyPlanGenerator.kt:1272-1284`) — ou seja, **nunca agenda acima da disponibilidade do dia**. O hint de disponibilidade ("aumente a disponibilidade de Sábado para X–Y min") só dispara **abaixo do piso 45** (`applyLongRunHonesty`, `:1334-1346`). **E6:** slot máximo 50 min → longão 50, título "Longão" normal, **sem hint**, `MISSING_LONG_RUN` permanente. **E8:** slot máximo 55 → idem.

**Inferência.** O usuário só descobre o bloqueio se abrir o card de graduação ("falta longão real de pelo menos 60 min") — que não conecta a causa (sua disponibilidade de sábado é 50) nem a solução (libere um slot ≥ 60 ou corra um longão extra, que conta: `strongRuns` aceita não-planejados). O app tem todos os dados para fechar o loop e não fecha.

**Impacto.** Iniciante dedicado com agenda curta fica preso no nível sem saber o que falta — trava a porta do intermediário (e da qualidade estruturada) por falta de uma dica de UI.

**Sugestão.** (a) No card de graduação, quando `MISSING_LONG_RUN` e `maxAvailability < 60`: dizer "libere um sábado de 60+ min ou registre um longão extra"; (b) alternativa de domínio: em fases de graduação próxima, o gerador poderia sugerir (não forçar) um slot maior no dia de maior disponibilidade.

**Como refutar.** (1) Verifique se o card de graduação já faz essa ponte (grep em `app/ui/level` por "disponibilidade" — não encontrado na leitura). (2) Mostre que `strongRuns` não aceita corrida extra semanal (aceita: `LevelGraduationInput.strongRuns` documenta "planejadas ou não", `:33-35`) — nesse caso o usuário *poderia* se graduar correndo extra, mas segue sem dica.

---

### [ALG-06] Rationale prioriza "iniciante" sobre a fase em semana de prova/recuperação

**Severidade:** Baixa · **Confiança:** Alta · **Categoria:** copy

**Fato.** No bloco "Sem treino forte" do rationale, o `when` testa `experience == BEGINNER` **primeiro** (`WeeklyPlanGenerator.kt:826`), antes de `RECOVERY` (`:830`) e `TAPER` (`:833`). **E2 (iniciante, taper de 5K):** o plano imprime *"Sem treino forte: **iniciante prioriza base**"* estando em `Fase TAPER` — a razão nomeada não é a que restringe o estímulo naquele contexto (taper + guarda de iniciante).

**Inferência.** Em semana de prova o usuário lê uma frase que não corresponde ao contexto que o próprio rationale acabou de declarar ("Semana de prova em 10 dias"). Contradição interna de copy; o mecanismo está correto.

**Impacto.** Confusão leve de transparência na semana mais sensível do ciclo.

**Sugestão.** Inverter a ordem ou compor: *"semana de prova: micro-dose é só após a graduação (nível iniciante)"*.

**Como refutar.** Rode E2 do Apêndice A e confirme a string exata; se a copy já nomeia taper primeiro, o achado cai.

---

### [ALG-07] Sessão "Acelerações curtas" de 10 min perde a estrutura executável

**Severidade:** Baixa · **Confiança:** Alta · **Categoria:** algoritmo × UX

**Fato.** O template de strides tem `warmup 8 + 6×(20 s+60 s) + cooldown 9 = 25 min` (`SessionLibrary.kt:325-339`). Em dia de strides com disponibilidade 10 (E3/E14), a duração vira 10 min e `structure.fittedTo(10)` lança exceção (`require(fixedSeconds < budget)`, `WorkoutStructure.kt:139`), capturada por `runCatching` em `:648-650` → `structure = null`. O título continua **"Acelerações curtas"** sem forma executável.

**Inferência.** O card/briefing promete strides e não consegue descrever nem aquecer + desaquecer em 10 min. Derivado de `ALG-01` (se o dia sair, o caso morre).

**Impacto.** Usuário faz "10 min de acelerações" sem saber a dose; briefing sem estrutura.

**Sugestão.** Se `fittedTo` falhar, rebaixar o título para o fácil simples (ou remover o dia — ver `ALG-01`).

**Como refutar.** E3 do Apêndice A + inspecionar `plan.sessions[0].structure` (esperado `null`) e o briefing daquele dia.

---

### [ALG-08] Bloco de progressão por ACWR some do rationale quando a disponibilidade também limita

**Severidade:** Baixa · **Confiança:** Média · **Categoria:** copy/auditabilidade

**Fato.** **E17 (ACWR confiável 1,7):** `VolumeDecision.block = ACWR_OUT_OF_RANGE` e `cappedByAvailability = true`. O `volumeRationale` mostra a branch `cappedByAvailability` (`:1550-1552`) e **não** anexa `volumeBlockHint(ACWR_OUT_OF_RANGE)` (que só corre na branch "mantido", `:1559-1560`). Copy final: *"limitado pela sua disponibilidade em 150 min"* — sem mencionar que a carga recente também proibia crescimento. `R-LOAD-03` aparece nos `ruleIds`, mas não na fala.

**Inferência.** Duas causas ativas, uma escondida. O minutos finais estava mesmo limitado pela disponibilidade (150 < 190), então a copy não é falsa — é **incompleta**.

**Impacto.** Baixo: o usuário não fica mal informado sobre o número, mas perde o sinal "carga fora da faixa" na explicação do plano.

**Sugestão.** Concatenar `volumeBlockHint(block)` também na branch de capping quando `block != NONE`.

**Como refutar.** E17 + leitura de `:1541-1561`: se a branch de capping já incluir o hint, o achado cai.

---

### [ALG-09] Duas definições de `loadTrusted` em dois motores vizinhos

**Severidade:** Baixa · **Confiança:** Alta · **Categoria:** consistência

**Fato.** `WorkoutRecommender.kt:100`: `val loadTrusted = loadState == LoadDataState.KNOWN` (usa `dataState`). `DailyPrescriptionEngine.kt:111`: `val loadTrusted = load?.objectiveDataState == LoadDataState.KNOWN` (usa `objectiveDataState`). Com carga **só-subjetiva** (sRPE: `dataState = KNOWN`, `objectiveDataState = INSUFFICIENT` — `TrainingLoadEngine.kt:186-192`), o caminho `NO_PLAN` do recommender trata a carga como confiável (sem downgrade de confiança, sem `R-LOAD-04`, rationale cai no "Base aeróbica…" genérico), enquanto o caminho com plano trata como não-confiável (`:114, 156-159`).

**Inferência.** Mesmo conceito, definições diferentes em arquivos vizinhos — exatamente o tipo de divergência que o ADR 0039 ("sRPE conserva, nunca libera") quis eliminar. O impacto é só de confiança/copy no caminho sem plano (nunca libera progressão — o recommender só prescreve fácil), por isso severidade Baixa.

**Sugestão.** Unificar: `WorkoutRecommender` passa a usar `objectiveDataState` + tratar `subjectiveDaysInWindow > 0` como `R-LOAD-04`, espelhando o engine.

**Como refutar.** `WorkoutRecommender.kt:99-113` vs `DailyPrescriptionEngine.kt:106-159` — se as duas linhas lerem o mesmo campo, o achado cai.

---

### [ALG-10] Pontos fortes do algoritmo (contrabalanço)

- **Matriz do gate 6/6 (E10):** verde mantém; âmbar → `DOWNGRADE/LOW_READINESS`; check-in ausente → `SUBJECTIVE_MISSING`; vermelho → `REST`; sono curto → `SHORT_SLEEP`; sem carga → `LOAD_INSUFFICIENT` — todos com motivo nomeado e duração 28 min (40×0,7), exatamente como `SPORTS_SCIENCE §7.2/§8.11`.
- **Blocos de volume nomeiam a causa (E11, E16 âmbar):** "limitado pela sua disponibilidade", "alvo mantido em 190 (prontidão não está alta)".
- **Descarga respeita o doc (E7/E15):** −25% com intensidade mantida (qualidade continua), longão com título honesto "Corrida mais longa" quando o corte encurta abaixo do piso.
- **Taper/recuperação prevalecem sobre tudo (E2):** micro-dose ≤25 min, longão em 30, sem qualidade principal — aderente a `§8.6`.
- **Conservadorismo real:** E9 (2 dias) explica "menos de 3 dias disponíveis" em vez de inventar qualidade; sem histórico a confiança cai para `LOW` e a copy diz "sem semana concluída".
- **Determinismo e versionamento:** `ENGINE_VERSION` (`weekly-plan-v8`/`daily-prescription-v7`), `inputsHash` completo no `DailyPrescriptionEngine.kt:640-712`, tudo testado (1.338 testes de domínio no último gate registrado).

---

---

## 5. Seção B — Variações de corredor (iniciante, intermediário, avançado)

> Base: experimentos E1, E2, E5–E9, E12, E13, E15, E18 + leitura de `SessionLibrary`, `LevelGraduationEngine`, `OnboardingForm`.

### [SEG-01] Iniciante ≡ Intermediário nas primeiras semanas de BASE

**Severidade:** Média · **Confiança:** Alta · **Categoria:** diferenciação de produto

**Fato.** **E1** (mesma disponibilidade, tudo verde, sem corrida): as semanas de `BEGINNER` e `INTERMEDIATE` saem **idênticas** — mesmas 4 sessões, mesmos títulos ("Acelerações curtas" 25, "Rodagem" 40×2, "Longão" 45), mesmo total 150 min; só o rationale difere ("iniciante prioriza base" × "a primeira metade da base é só fácil…"). `AVANCED` só muda o longão (60 vs 45). A diferenciação real só existe em: `BASE` 2ª metade (fartlek leve só para Int/Av, `SessionLibrary.kt:134-137, 171-175`), `BUILD` (catálogos distintos, E12) e `PEAK` (Av tem 2 estímulos, Int 1 — `:239-257`).

**Inferência.** O onboarding pergunta o nível (`OnboardingScreen.kt:364-366`) e o usuário espera um plano diferente por escolher "Intermediário"; nas primeiras 2–4 semanas (BASE primeira metade + rotação `weekInPhase`) a escolha é **inerte**. É conservadorismo documentado (§8.4: BASE primeira metade é só fácil), mas o produto não comunica "seu nível muda a partir da 2ª metade da base".

**Impacto.** Sensação de "pagou por nada" no intermediário avançado; possível churn no primeiro mês. Também enfraquece o valor percebido do card de graduação.

**Sugestão.** (a) Copy no Hoje/plano: "Você está em BASE — a partir da segunda metade o fartlek leve entra; o nível muda os estímulos de BUILD"; (b) considerar dar ao intermediário calibrado (Tier A/B) o fartlek leve desde a 1ª semana via cold-start (já existe `ColdStartAllowance` para o gate do dia — poderia estender à seleção do catálogo, mantendo o doc atualizado).

**Como refutar.** (1) Rode E1 e compare as listas de sessões (são iguais). (2) Mostre que o cold-start já dá qualidade ao intermediário na 1ª semana sem histórico — só funciona com VDOT recente (ADR 0053) e não muda o catálogo da BASE; sem ele, E1 permanece.

---

### [SEG-02] Avançado sub-diferenciado: mesmo teto de qualidade, mesma grade, 1 sessão/dia

**Severidade:** Média · **Confiança:** Média · **Categoria:** diferenciação × ciência

**Fato.** Em relação a `INTERMEDIATE`, o `AVANCED` recebe: mais reps nos templates (intervalado 6×3′ vs 5×3′, subidas 8 vs 6, fartlek 10 vs 8, contínuo 25′ vs 20′ — `SessionLibrary.kt:221-237`), piso/teto de longão 60/180 vs 45/150 (`WeeklyPlanGenerator.kt:1261-1264, 1674-1677`) e 2º estímulo no `PEAK`. **Não** difere em: máximo de 2 qualidades/semana (idêntico, doc §6.4), progressão de +6% (idêntica), deload (idêntico), grade de 1 sessão por dia (`sessions = days.map`, `:502` — não existe modelagem de duplo/treino duplo), nem em teto de fácil (60 min para todos, `MAX_EASY_MIN`, `:1643`). **E7** mostrou um avançado de 6 dias recebendo a mesma estrutura que um intermediário teria, apenas com longão maior.

**Inferência.** O plano do avançado é o do intermediário "uma dose maior" — não um modelo de treino distinto. Corredores avançados reais (50–80 km/semana, 3+ estímulos, duplos, duplo-limiar) ficam fora do modelo. Parte é decisão conservadora documentada (máx. 2 qualidades é regra do doc); parte é simplesmente não modelado (frequência, duplos). A sugestão de modelo avançado precisa de evidência e de ADR — não é "bug".

**Impacto.** O segmento avançado (pequeno no MVP, usuário 0 não pertence) não é servido; se o produto quiser atrair esse público, o motor precisa de uma fatia própria.

**Sugestão.** Registrar no PRODUCT/ROADMAP o escopo explícito ("MVP otimizado para iniciante/intermediário; avançado: mesma estrutura com dose maior") **ou** abrir ADR para diferenciação (3º estímulo condicional, duplos como disponibilidade AM/PM, teto de fácil por nível). Enquanto não houver evidência, a opção de registrar o escopo é a barata.

**Como refutar.** (1) Aponte qualquer campo de `WeeklyPlanInput`/catálogo que diferencie avançado além de reps/pisos/PEAK (não existe na leitura). (2) Mostre doc que declare o contrário (o doc §8.8 descreve uma grade única para todos os níveis — o achado confirma o doc, e a crítica é de produto, não de implementação).

---

### [SEG-03] Iniciante na semana de prova não recebe nenhum estímulo (nem strides), contra o doc

**Severidade:** Média · **Confiança:** Média · **Categoria:** doc × código

**Fato.** **E2 (iniciante, 5K em 10 dias):** 3 sessões fáceis, `qualityCount = 0`, **sem strides**. **E13:** `easyTemplates(TAPER)` devolve `[Rodagem]` para **todos** os níveis (`SessionLibrary.kt:110`); o micro-dose exige `experience != BEGINNER` (`structuralMicroDoseEligible`, `WeeklyPlanGenerator.kt:384-386`); e `stridesDay` só existe em `BASE/BUILD/PEAK` (`:449-466`). Resultado: o iniciante perde até os strides (que são `EASY` para ele desde a semana 1) durante toda a taper. `SPORTS_SCIENCE §8.4` diz, na coluna "Quem recebe" dos strides: **"Todos como `EASY`; micro-dose ≤25 min em taper/prova"**.

**Inferência.** Duas leituras possíveis do doc: (a) "Todos" inclui iniciante em taper → código diverge; (b) o micro-dose de taper é `QUALITY` (`stridesMicroDose`, `SessionLibrary.kt:313-323`) e cairia na guarda `R-PRESC-04` do dia, então o gerador o exclui para não gerar downgrade automático — decisão defensável mas **não documentada**. Note-se que a exclusão do iniciante do micro-dose é internamente coerente (evita DOWNGRADE diário), mas o iniciante fica sem **nenhum** tipo de estímulo distinto na semana da prova, enquanto `§8.6` promete "nunca zero estímulo".

**Impacto.** Iniciante correndo sua primeira prova recebe uma semana plana e copy "iniciante prioriza base" (ver `ALG-06`) em vez de orientação de taper. Baixo risco fisiológico; média perda de valor/educação.

**Sugestão.** Ou (a) incluir strides `EASY` no taper para iniciante (cumprindo "Todos" e "nunca zero estímulo", sem ferir `R-PRESC-04` porque é `EASY`), ou (b) emendar o doc §8.4/§8.6 explicitando "iniciante em taper: só fácil, por guarda R-PRESC-04" com justificativa. Decisão + ADR/emenda; hoje é silêncio.

**Como refutar.** (1) Rode E13/E2 do Apêndice A (fato confirmado). (2) Encontre em ADR/doc a decisão de excluir strides do iniciante em taper (não localizada na leitura de ADR 0044/0047/0065). (3) Argumente que "Todos" da tabela se refere só à semana normal — nesse caso o doc está ambíguo e merece emenda (o achado de ambiguidade permanece).

---

### [SEG-04] Nível auto-declarado sem validação e sem caminho de rebaixamento

**Severidade:** Média · **Confiança:** Alta · **Categoria:** produto × segurança

**Fato.** O onboarding aceita qualquer `Experience` por escolha direta (`OnboardingScreen.kt:364-366`; default `BEGINNER` em `OnboardingForm.kt:43`). O motor de graduação **só propõe subida**: para `ADVANCED` retorna `Stay` imediato ("já está no nível Avançado", `LevelGraduationEngine.kt:119-124`) e `EvaluateLevelGraduation` declara em KDoc: *"nenhum rebaixamento é proposto em nenhum caminho"* (`EvaluateLevelGraduation.kt:27`). Mas `SPORTS_SCIENCE §8.12`/KDoc do motor (`LevelGraduationEngine.kt:89-90`) diz: *"nunca rebaixa: **rebaixa… regressão só sugere revisão pelo mesmo card**"* — ou seja, o doc prome uma sugestão de revisão na regressão que **nenhum caminho implementa** (busca por sugestão de rebaixamento: 0 hits).

**Inferência.** Dois achados: (1) usuário pode se declarar `ADVANCED` sem nenhuma evidência e receber o catálogo avançado imediatamente (sujeito só aos gates de carga/prontidão do dia — que valem para todos); (2) uma vez `ADVANCED`, nunca mais há sinal de regressão (parou de correr 3 meses → mesmo plano). O primeiro é produto (aceitável com copy), o segundo é **doc × código**: o doc promete revisão que não existe.

**Impacto.** Risco de prescrição intensa para quem exagerou no nível (mitigado pelos gates diários); estagnação silenciosa para quem regride.

**Sugestão.** (a) Emendar o doc para refletir "sem sugestão de rebaixamento no MVP" **ou** implementar `SuggestReview` no motor (card "seu volume caiu X% — manter Avançado?"); (b) no onboarding, pedir uma evidência leve para `ADVANCED` (ex.: melhor 10K recente) ou marcar "auto-declarado" na confiança do plano.

**Como refutar.** (1) Encontre o código da "revisão por regressão" prometida (não encontrado; se existir em outra feature, cite-a). (2) Mostre que algum ADR decide explicitamente não ter rebaixamento nem revisão — nesse caso o doc §8.12 está desatualizado (o achado doc×código permanece).

---

### [SEG-05] Deadlock de graduação do iniciante com agenda curta

*(Comando de `ALG-05` como gap de nível; mesma evidência E6/E8.)* Severidade Média, confiança Alta. Ver `ALG-05`.

### [SEG-06] Idade coletada e ignorada; sexo só no TRIMP

**Severidade:** Baixa · **Confiança:** Alta · **Categoria:** dados × produto

**Fato.** `birthDate` é obrigatório no perfil (`AthleteProfile.kt:90`) e usado **apenas** para exibição (`ProfileLabels.kt:40`); grep em todo o motor não encontra uso em prescrição, prontidão, carga ou recuperação. `sex` entra só nas constantes do TRIMP (`TrainingLoadCalculator.kt:27-30`, com `OTHER/UNSPECIFIED` recebendo a média dos coeficientes — decisão razoável e não documentada no doc §6.1, que só cita homens/mulheres).

**Inferência.** O doc §4.1 lista "idade" como dado de entrada, mas o motor não a consome — não há consideração de masters (>40), de recuperação prolongada nem de ajuste de deload por idade. Para o usuário 0 (38 anos) isso não muda nada hoje; é uma lacuna de produto, não um bug. A ausência é defensável (não há evidência de regra simples de idade no escopo), mas a promessa doc × o uso real divergem.

**Sugestão.** Registrar no doc §4.1 "idade é coletada e exibida; não modifica prescrição no MVP" **ou** abrir ADR com literatura de masters (ex.: ajustes de recuperação/deload). Não inventar regra sem evidência (`AGENTS.md §5`).

**Como refutar.** grep por `birthDate`/`Period.between` em `core/domain` — se houver uso fisiológico, o achado cai.

---

## 6. Seção C — Ciência do esporte: doc × comportamento

### [SCI-01] Prontidão sem dados aparece como "Moderada 50"

**Severidade:** Média · **Confiança:** Média · **Categoria:** ciência × UX

**Fato.** `ReadinessCalculator.calculate` com zero entradas devolve `score = 50, level = AMBER, confidence = LOW, flags = {INSUFFICIENT_DATA, SUBJECTIVE_MISSING}` (`ReadinessCalculator.kt:36-48`). A UI do Hoje renderiza `McGauge(score=50, levelLabel="Moderada")` (`TodayScreen.kt:455-463`, labels em `ReadinessLabels.kt:9-13`) — não encontrei tratamento especial de `INSUFFICIENT_DATA` no Hoje (o flag aparece só em `CoachNextLabels.kt:91,114`).

**Inferência.** `SPORTS_SCIENCE §7.3` afirma *"'Sem dados' não é 'recuperação ruim'"* e §7.2 define âmbar como "reduzir volume/intensidade". Usuário de primeira abertura (antes do primeiro check-in/sincronização) vê um medidor "Moderada" com score 50 — leitura possível: "minha recuperação é mediana", quando na verdade **não há dado**. O badge de confiança `LOW` e os sinais ao lado amortecem o problema, daí confiança Média.

**Impacto.** Má primeira impressão e possível interpretação errada; pode desencorajar o check-in em vez de motivá-lo ("sem dados → mostre o vazio com CTA").

**Sugestão.** Para `INSUFFICIENT_DATA` + `components.isEmpty()`: gauge em estado "Sem dados" (tracejado/`—`) com copy "Faça o primeiro check-in para ver sua prontidão", em vez de 50/Moderada. Alternativa mínima: manter o score mas trocar o label para "Sem dados".

**Como refutar.** (1) Verifique se `TodayViewModel`/`TodayExplanation` já traduz `INSUFFICIENT_DATA` em outro widget (busca não encontrou; confirme com grep amplo por `INSUFFICIENT_DATA` em `app/src/main` — só `CoachNextLabels`). (2) Rode o app sem dados e mostre a UI real (não executada nesta auditoria — ver §2.3).

---

### [SCI-02] Deload com "Progressão liberada" no mesmo rationale (copy mista)

**Severidade:** Baixa · **Confiança:** Alta · **Categoria:** copy × ciência

**Fato.** **E15/E19:** semana de descarga imprime, no mesmo parágrafo, *"Semana de descarga… volume −25%"* e *"Progressão liberada: você concluiu pelo menos 80% da semana anterior"* / *"a semana tem 2 oportunidades"*. Mecanismo: `secondQualityAllowed` (`:376-380`) não exclui `deload`, e de fato o doc §8.1 manda manter tipo/intensidade na descarga — **o comportamento está correto**; a frase "progressão liberada" durante descarga é semanticamente estranha (não houve progressão; houve manutenção de estímulo).

**Inferência.** As duas frases são verdadeiras individualmente; juntas sugerem "descarregando e progredindo ao mesmo tempo".

**Sugestão.** Em semana de descarga, trocar "Progressão liberada" por "Estímulo mantido na descarga (volume −25%)".

**Como refutar.** E15/E19 + `:848-875`: se o branch de descarga já sobrepõe a copy de progressão, o achado cai (a leitura atual mostra ambas).

---

### [SCI-03] Distribuição 80/20 medida só depois, nunca verificada no plano

**Severidade:** Baixa · **Confiança:** Alta · **Categoria:** ciência × produto

**Fato.** `SPORTS_SCIENCE §6.4` fixa alvo "~80% do tempo em fácil". O `IntensityDistributionCalculator` existe e é usado só em **Tendências/Coach** (`TrendsViewModel.kt:66`, `CoachReviewSource.kt:55`, `CoachProgressSource.kt:66`); o `WeeklyPlanGenerator` não calcula nem valida a distribuição planejada (grep no pacote `prescription`: 0 referências). A contenção indireta é o teto de 2 qualidades + pisos de fácil.

**Inferência.** Compor uma semana com 2 qualidades de 45 min + fáceis curtos pode chegar a ~30% de tempo "forte planejado" sem que ninguém avise. Na prática os gates diários derrubam qualidade quando necessário e o `fitToVolume` dilui, então o risco é baixo — daí severidade Baixa.

**Sugestão.** Validação leve no gerador: se `tempoQualidade / total > 25%`, anotar no rationale (não bloquear) — ou apenas medir no golden de aceite do plano.

**Como refutar.** Aponte onde a distribuição planejada é verificada no ato da geração (não encontrada).

---

### [SCI-04] Nota de equilíbrio sobre a ciência do motor

**Fato.** Hierarquia de evidência com `strength` por regra, `EV-*` catalogados (§13), conservadorismo explícito (ACWR só decide quando confiável, Impellizzeri 2020 citado, sRPE nunca libera progressão, VDOT com proveniência e Riegel como checagem, calor no extremo conservador da faixa, "nunca 220−idade"). Os experimentos E10/E11/E16/E17 confirmaram o comportamento documentado nos caminhos de gate/volume/ACWR. **Nenhuma divergência encontrada entre doc e código nas fórmulas de prontidão, zonas, carga e taper.** Este achado é positivo e deve ser lido junto das críticas desta seção.

---

## 7. Seção D — Story Studio (UI/UX)

> Auditoria delegada (subagente, somente leitura) + conferência de referências. Cada achado traz `arquivo:linha` original para conferência direta.

### [STORY-01] Entrada profunda e condicional a GPS — **Média/Alta**

**Fato.** `StoryCard` é o ~10º item do `LazyColumn` do detalhe, depois de Hero, Métricas, Comparação, Leitura do coach, Gráfico, Zonas, Calibração, Trechos e Rota (`ActivityDetailScreen.kt:284-325`), e **só existe se houver lat/lng** (`:312`). Mínimo: 1 toque → scroll de ~8 cards → toque "Story Studio" (`:706-713`) → "Gerar vídeo" (`StoryStudioSheet.kt:540-548`) = **3 toques + 1 scroll**.
**Inferência.** A feature mais viralizável está entre as menos alcançáveis; "Leitura do coach" (privada) fica acima dela.
**Sugestão.** CTA "Criar story" no `HeroCard` pós-importação e/ou deep-link na notificação de import concluído; considerar ordem fixa com o card entre Métricas e Gráfico.
**Como refutar.** Contar os itens do LazyColumn e a condição de GPS no arquivo citado.

### [STORY-02] Prévia sem mapa e loading fundido com erro — **Média/Alta**

**Fato.** `StoryPreviewCard` renderiza sempre `mapBackground = null` (`StoryStudioSheet.kt:633`); o `if (preview == null)` exibe "Prévia indisponível sem rota GPS." tanto no primeiro render quanto com `plan == null` (`:679-696`, tag `story-preview-loading`), sem spinner nem estado `Error`. A aba Arte tem 5 estados distintos (`ArtStudioContent.kt:698-749, 821-876`).
**Inferência.** Escolher Estilo de mapa não altera a prévia (só muda a atribuição, `StoryBackdropRenderer.kt:88-98`) e nos primeiros ~300 ms o usuário lê uma mensagem de erro que não é erro.
**Sugestão.** Separar `Loading` (spinner) / `Empty` (copy GPS) / `Error`; placeholder do estilo na prévia para feedback visível do card de MAPA.
**Como refutar.** Conferir os dois trechos citados; verificar se existe outro estado de loading fora desse `if`.

### [STORY-03] `PreparingMap` até 45 s sem Cancelar — **Média**

**Fato.** `generateStory` fecha o Studio (`ActivityDetailViewModel.kt:587-592`) e entra em `PreparingMap`; o card é só texto ("Baixando o mapa…", `ActivityDetailScreen.kt:715-719`), sem Cancelar, embora `cancelStory()` aceite o estado (`:674-684`); timeout 45 s (`StoryMessages.kt:13`).
**Inferência.** Janela longa sem saída visível; sair da tela cancela em silêncio.
**Sugestão.** Botão Cancelar + indeterminado já em `PreparingMap`; manter o Studio aberto até enfileirar.
**Como refutar.** Conferir a UI do card e a assinatura de `cancelStory`.

### [STORY-04] "Gerar vídeo" fecha o Studio e perde a aba — **Média**

**Fato.** `isOpen=false` em `generateStory` (`ActivityDetailViewModel.kt:589`); `rememberSaveable { tab = VIDEO }` (`StoryStudioSheet.kt:202`) é descartado → reabertura sempre na aba Vídeo; "Gerar outro" (`ActivityDetailScreen.kt:822-829`) repete o problema.
**Sugestão.** Lembrar a última aba no VM e/ou não fechar durante `PreparingMap`.
**Como refutar.** Conferir o `rememberSaveable` e o `isOpen`.

### [STORY-05] Snackbar só na Arte; vídeo confirma fora da tela — **Média**

**Fato.** `SnackbarHost` existe só em `ArtStudioContent` (`:162-176, 626-631`); vídeo usa texto verde no card (`ActivityDetailScreen.kt:755-791`) + notificação (`StoryNotifier.kt:83-89`).
**Sugestão.** Replicar `SnackbarHost` na aba Vídeo para Done/Error (mesma correção da P3-08 para a arte — `PROGRESS.md:544`).
**Como refutar.** grep `SnackbarHost` em `app/src/main`.

### [STORY-06] `McTextButton` fora da varredura de 48 dp — **Média**

**Fato.** Testes de 48 dp cobrem pills/tiles (`StoryStudioContentTest.kt:261-271`, `ArtStudioContentTest.kt:288-297`), mas `McTextButton` não tem `minHeight` e usa padding 8×4 dp (`McButtons.kt:159-191`); usos: "Restaurar padrão" (`StoryStudioComponents.kt:318-322`), "Tentar de novo" (`ArtStudioContent.kt:551-555, 587-591`), "Recarregar prévia" (`:851-855`) — ações de recuperação de erro, as mais críticas.
**Sugestão.** `minHeight = 48.dp` em `McTextButton` + tags nos testes de 48 dp. Viola `AGENTS.md §6`.
**Como refutar.** Medir a altura resultante no componente citado.

### [STORY-07] Fonte 200% validada só na aba Arte — **Média**

**Fato.** Golden `art-studio-tab-narrow-200-dark.png` existe; para Vídeo não há teste `fontScale=2f` (goldens `story-studio-*` sem 200%).
**Sugestão.** Golden `story-studio-narrow-200-dark` + teste análogo ao da arte (`ArtStudioContentTest.kt:539`).
**Como refutar.** Listar goldens `app/src/test/screenshots/story-studio-*`.

### [STORY-08] Offline: arte funciona, vídeo falha sem alternativa — **Média**

**Fato.** Arte é render local; vídeo exige tiles (`StoryMessages.kt:11` MAP_UNAVAILABLE + retry); fallback rota-only não existe mais (`ActivityDetailViewModel.kt:603`).
**Sugestão.** No erro `MAP_UNAVAILABLE`, CTA "Criar a arte (funciona offline)"; concluir MAP-10.
**Como refutar.** Conferir `generateStory` e a copy de erro.

### [STORY-09] Pôster/capa só nascem junto do MP4 — **Média**

**Fato.** `StoryPosterRenderer`/capa rodam dentro do export de vídeo (`StoryVideoExporter.kt:659`); a UI admite: "Pôster automático: opaco, **nasce com o vídeo**" (`ArtStudioContent.kt:208-209`).
**Sugestão.** Botão "Gerar só pôster/capa" (mesmo renderer, sem encoder).
**Como refutar.** Verificar se há caminho de render de pôster sem `StoryExportProcessor`.

### [STORY-10] Descoberta da aba Arte frágil — **Média**

**Fato.** Aba default `VIDEO` (`StoryStudioSheet.kt:202`); o segmento Vídeo|Arte some ao rolar (`:214-270`, ADR 0060 §2.3) e volta só no topo.
**Sugestão.** Segmento fixo para TalkBack (anunciar troca) ou atalho "Arte" no `StoryCard`.
**Como refutar.** Conferir a lógica de `canScrollBackward`.

### [STORY-11] Prévia estática, sem scrubber — **Baixa**

**Fato.** 3 frames em `StoryPreviewFrame` (`StoryStudioSheet.kt:101-122`); o mock previa prévia animada (`STORY_V3_ANALISE §6.4, l.415-418`), o plano reduziu para 3 frames + scrubber (`STORY_V3_PLANO §3.6`, VIS-06) e o scrubber não foi implementado.
**Sugestão.** Scrubber por tempo (`timeSec` já suportado pelo renderer) ou playthrough curto.
**Como refutar.** Conferir `FrameSegmentedControl` e o plano citado.

### [STORY-12] Limites de formato/qualidade não expostos; enum órfão — **Baixa**

**Fato.** Vídeo só 9:16/1080p30 (`StoryVideoExporter.kt:120-121`); `StoryQuality.SMOOTH_1080P60` existe no domínio (`StoryRecipe.kt:68-71`) sem controle na UI (mock previa "QUALIDADE", `STORY_V3_PLANO §3.6 l.288`).
**Sugestão.** Rodapé no card "Formato de saída" listando o que está fora (por que não há música/4:5).
**Como refutar.** grep `SMOOTH_1080P60` na UI.

### [STORY-13] Marca fixa sem toggle — **Baixa** (decisão documentada)

**Fato.** Logo em header/frame final/arte/pôster (`StoryHeaderRenderer.kt:27-36` etc.); "ativar/desligar marca" é pós-MVP (`PRD_ARTE_PNG_ROTA §4`). Mantido como pós-MVP documentado; listado para completude.
**Como refutar.** Conferir o PRD §4.

### [STORY-14] Share 1 arquivo por vez, sem orientação de destino — **Baixa**

**Fato.** `ACTION_SEND` individual (`ActivityDetailScreen.kt:905-913`); 4 CTAs pós-geração (`:793-830`) exigem escolher 9:16 vs 4:5 sem orientação completa (copy parcial existe em `:786-791`).
**Sugestão.** Generalizar copy por destino ("Capa 9:16 → Stories/Reels", "Pôster 4:5 → Feed") para todos os botões.
**Como refutar.** Conferir as copy dos botões.

### [STORY-15] Acessibilidade boa, com lacunas de Role.Tab e anúncio — **Baixa**

**Fato.** `selected`+TalkBack em pills/tabs/frames; lacunas: segmento usa `Surface.clickable` **sem `Role.Tab`** (`StoryStudioSheet.kt:577-595`) e some ao rolar sem anúncio.
**Sugestão.** `Role.Tab` + anúncio do segmento oculto; teste com TalkBack real.
**Como refutar.** Conferir o `clickable` do segmento.

### [STORY-16] Buracos de teste exatamente nos estados achados — **Média**

**Fato.** 0 testes para `PreparingMap` (grep em `app/src/test` = 0); sem teste de loading/empty da prévia (só `disables generation without a gps plan`, `StoryStudioContentTest.kt:274`); o golden usa `story-preview-loading` só para afirmar que some (`StoryStudioScreenshotTest.kt:106,332`).
**Sugestão.** TDD dos 3 estados da prévia + card `PreparingMap` com cancelar.
**Como refutar.** grep `PreparingMap`/`preview loading` nos testes.

### [STORY-17] Flakes da suíte story conhecidos e fora do `TESTING.md` — **Baixa**

**Fato.** `StoryStudioContentTest.describes the preview` (corrigido com `waitUntil`, `PROGRESS.md:846,855`); `StoryStudioScreenshotTest` instável sob carga combinada (`PROGRESS.md:857-858`); `TESTING.md` §10.1 **não** lista flakes de story.
**Sugestão.** Registrar em `TESTING.md`; manter prática de rodar `*StoryStudio*` isolado.
**Como refutar.** Conferir `TESTING.md:476-501`.

### [STORY-18] Lacunas doc × código (consolidado)

| Doc promete | Código | Citação |
|---|---|---|
| Botão "Criar story" | rótulo "Story Studio" | `STORY_V3_PLANO §3.6 l.267` × `ActivityDetailScreen.kt:707` |
| Controle QUALIDADE 1080p30 | inexistente na UI | `STORY_V3_PLANO §3.6 l.288` × `StoryVideoContent` |
| Prévia com scrubber | segmentado de 3 frames | `STORY_V3_PLANO §3.6/VIS-06` × `FrameSegmentedControl` |
| Estilo visível na prévia | estilo não aparece | `STORY_V3_ANALISE §6.4 l.445` × `StoryStudioSheet.kt:633` |
| Prévia animada com playhead | frames estáticos | `STORY_V3_ANALISE §6.4 l.415` |
| `StoryStudioViewModel` | tudo em `ActivityDetailViewModel` | `PRD_FOTO §6.1` (doc corrigido) × `STORY_V3_PLANO §4.1` (stale) |
| `TESTING.md` fonte de teste | não cita nenhum teste story/art | `TESTING.md` × ~65 arquivos de teste |

**Pendências declaradas (não são achado, são dívida registrada):** DOM-09 (recorde sem wiring, `STORY_V3_PLANO l.472`), MAP-10, ENC-05/08/09, QA-05/06, DEV-03/04/05/07, checklist §7 do MVP inteiro `[ ]` (`l.664-682`), DoD do `PRD_ARTE_PNG_ROTA` §12 nunca marcado, 7 itens de validação no aparelho (`PROGRESS.md:654-662`), 8 decisões abertas da `AUDITORIA_ARTE_PNG_UI_UX.md §11`.

### [STORY-19] Pontos fortes (contrabalanço)

Extra sem dado = chip desabilitado **com motivo**, nunca `0` (`StoryStudioComponents.kt:197-211`); estados da aba Arte como referência de UX (loading/empty/error/stale/busy + snackbar + contentDescription); export em background robusto (WorkManager, ETA, notificação, cancelamento entre frames — `StoryExportWorker.kt`); contrato de vídeo tecnicamente correto (BT.709/High@4.1, `StoryVideoExporter.kt:58-83`); privacidade da foto exemplar (PhotoPicker sem permissão, cópia atômica, reset/delete/órfãos); ~500 testes story/art; veredito/plano **nunca** entram no vídeo público (sci-integrity).

---

---

## 8. Seção E — Coach IA

> Auditoria delegada (subagente) com **três alegações críticas verificadas pessoalmente pelo auditor**: `COACH-05` (revoke não limpa exchanges — `CoachViewModel.kt:527-536`, confirmado), `COACH-06` (retry usa `lastFreeText` — `:599-609`, confirmado), `COACH-13` (corpo de erro no log — `CoachApi.kt:116` + `CoachViewModel.kt:821,832`, confirmado).

### [COACH-01] Sem multi-turn real — **Média/Alta**

**Fato.** O payload `free-answer` leva só a pergunta atual + dossiê (`CoachFreeAnswerPrompt.kt:16-38`; montagem `CoachViewModel.kt:764-775`); histórico foi rejeitado como alternativa (ADR 0063 §3) e a memória local **não entra em prompt** (`HANDOFF_IA_COACH.md` §2 linha 56; `PRD_IA_COACH.md` §10 l.154).
**Inferência.** Follow-ups ("e amanhã?", "e o da semana passada?") dependem 100% das regex do `FreeInputParser`; sem slot de dia/tema caem em `ASK_CLARIFY` ou `ASK_COACHING` genérico — sensação de amnésia do treinador.
**Sugestão.** Resolver follow-ups no cliente: anexar ao `engineContext` a última pergunta/replica já validada (sem payload novo) ou hidratar com turnos persistidos (já pós-MVP).
**Como refutar.** Conferir `CoachFreeAnswerPrompt` e o ADR 0063 §3: se houver qualquer forma de contexto conversacional, o achado cai.

### [COACH-02] Sem streaming; até 25 s de "digitando…" — **Média**

**Fato.** `HttpCoachApi.post` lê o corpo inteiro (`CoachApi.kt:111-117`); proxy responde texto único (`Main.kt:107-119`); sem SSE (grep 0). Explain/review podem chegar a 45 s (`CoachApi.kt:82`).
**Sugestão.** Medir p50/p95; se batch, cronômetro/estado honesto no header.
**Como refutar.** grep `text/event-stream` em `infra/coach-proxy`.

### [COACH-03] Sem feedback por mensagem (👍/👎) — **Baixa**

**Fato.** Nenhum componente de feedback (grep 0); `PRD_IA_COACH §1 decisão 32` limita métricas a feedback qualitativo do usuário 0. As descobertas de qualidade chegam só via logcat manual.
**Sugestão.** Feedback local (não enviado) alimentando a auditoria; pós-MVP.
**Como refutar.** grep `ThumbUp|gostei` em `app/src/main`.

### [COACH-04] Orçamento de latência divergente do PRD + status fixo do header — **Média**

**Fato.** `PRD_IA_COACH §1 decisão 8`: "Timeout ~10s, 1 retry, cache". Real: app 45 s explain / 25 s free (`CoachApi.kt:82,88`), proxy 15 s upstream / 18 s por tentativa / 20 s de orçamento (`Main.kt:62-64,317,325`); retry só manual (`CoachChatComponents.kt:281-286`); status do header fixo "motor no comando" mesmo offline/sem IA (`CoachScreen.kt:197`) vs `PRD_COACH_CHAT §6.1` (status dinâmico).
**Sugestão.** Atualizar a decisão 8 do PRD para a realidade (ou baixar explain para 20/25 s) e derivar o status de `Fallback`/consentimento/offline.
**Como refutar.** Conferir PRD §1 decisão 8 e os timeouts citados.

### [COACH-05] Revogação em voo deixa a UI presa; exchanges sobrevivem — **Média** ✅verificado

**Fato.** `revoke()` (`CoachViewModel.kt:527-536`) limpa consentimento + Room, **não** limpa `_exchanges`/`_freeAnswer`; as guards pós-chamada `if (!_consented.value) return` (`:777, 820`) abandonam o exchange em `Loading` (digitando infinito); `CoachScreen.kt:347` renderiza `exchanges` sem checar consentimento.
**Inferência.** Após revogar no meio de uma resposta, a conversa digitada continua visível (contraria "revogar apaga") e um "digitando…" pode nunca resolver.
**Sugestão.** Em `revoke()`, limpar `_exchanges` e `_freeAnswer`; trocar o return por `settle(..., Fallback)` efêmero.
**Como refutar.** Ler `revoke()` — não há limpeza de exchanges; se existir em outro ponto, cite-o.

### [COACH-06] `retryFreeAnswer` pode responder a pergunta errada — **Média** ✅verificado

**Fato.** `retryFreeAnswer` combina `lastFreeText` (última pergunta enviada, `:574, 601`) com `pending` (qualquer exchange em `Fallback`, `:602`) — cada exchange já guarda `exchange.question` (`:197`).
**Inferência.** Q1 falhou → Q2 enviada com sucesso → "Tentar de novo" do card de Q1 reenvia **Q2** naquele slot (e persiste a resposta com o hash de Q2 sobre a pergunta de Q1). Só 1 teste de retry (`CoachViewModelTest.kt:1776`).
**Sugestão.** Usar `pending.question` + teste de regressão.
**Como refutar.** Ler `:599-609`: se `text` derivar de `pending.question`, o achado cai (hoje deriva de `lastFreeText`).

### [COACH-07] Turnos órfãos: pergunta persistida sem resposta — **Média**

**Fato.** `recordYouTurn` rota **antes** do `route` (`:581-584`) e `Fallback` é efêmero (`:177-178, 1064-1069`); recusa `OUT_OF_SCOPE` ou falha de rede deixa `YOU` sem `ANSWER` — mas o KDoc afirma "the archive never holds a question without its answer (ADR 0057 item 3)" (`:175-178`) e `CoachHistoryCard.kt:150` renderiza `YouRow`.
**Sugestão.** Gravar `ANSWER` determinístico de recusa, ou não persistir `YOU` quando terminar em `Fallback`, e ajustar KDoc/ADR.
**Como refutar.** Reproduzir: enviar pergunta fora de escopo e abrir o histórico (ou ler o fluxo `:613-614` + `recordYouTurn`).

### [COACH-08] Feed não reidrata a conversa ao reabrir a aba — **Média**

**Fato.** `_exchanges` nasce vazio (`:238`) e só é populado por `sendFreeText`/`echoQuestion`; o histórico persistido aparece só no bottom sheet (`CoachScreen.kt:430-451`).
**Inferência.** Reabrir a aba zera a timeline visível; ADR 0056 §2.4 justificou persistir `YOU` para o arquivo não "virar morto", mas o feed principal não o usa.
**Sugestão.** Hidratar `_exchanges` de `memory.observeTurns()` (turnos do dia).
**Como refutar.** Construir o `CoachViewModel` e verificar se há hydrate (não há na leitura).

### [COACH-09] Porteira de escopo é beco sem saída (dor/lesão sem CTA) — **Média**

**Fato.** `scopePatterns` (`FreeInputParser.kt:71-78`) manda dor/lesão/doença para `OUT_OF_SCOPE`; copy `FreeAnswerCopy.OUT_OF_SCOPE` (`CoachViewModel.kt:1197-1199`) não encaminha para check-in/Hoje/avaliação; `ILLNESS_OR_INJURY` só nasce do check-in/readiness (grep: só `TodayViewModel.kt:352`, `TodayExplanation.kt:265,314`, `CoachNextLabels.kt:96`). `PRD_IA_COACH §1 decisão 21` ("dor ≥3/lesão → interrompe + sugere avaliação + **registra** `ILLNESS_OR_INJURY`") e ADR 0056 §2.2 (CTA para fluxo canônico) **não têm implementação neste caminho**.
**Inferência.** Quem relata dor no chat recebe recusa seca sem próximo passo — risco de produto num app conservador.
**Sugestão.** Copy de recusa com CTA ("marca no check-in que eu levo isso pra conta") + registro do motivo no check-in.
**Como refutar.** grep `ILLNESS_OR_INJURY` em `app/src/main` (só caminhos de check-in) e ler a copy de `OUT_OF_SCOPE`.

### [COACH-10] Caminho `explain` aceita número/percentual/distance inventado — **Alta**

**Fato.** `invalidReason` só checa percentuais **se** `allowedPercents != null` (`CoachResponseValidator.kt:285-292`); o `explain` passa apenas `allowedMinutes` + `forbiddenCodes` (`CoachViewModel.kt:320-327`) → `allowedPercents = null` → checagem de % **desligada**; não há `inventedDistance`/`uncoveredCount` neste caminho (só em `validateFreeAnswer`).
**Inferência.** Contra `PRD_IA_COACH §5` ("qualquer pace/FC/carga/volume/VDOT fora do input → descartada"), uma resposta de explain/review pode dizer "85% da semana" ou "10 km" e passar.
**Sugestão.** Ligar `allowedPercents`/`allowedDistancesKm` no explain (derivados do plano) e unificar os checks de número entre os dois caminhos.
**Como refutar.** Ler `validate()` vs `validateFreeAnswer()` e o call site do explain: se o explain já passar allow-lists de %, o achado cai.

### [COACH-11] Allow-lists vazam entre dias e escopos — **Média**

**Fato.** `CoachOpenAnswer.allowedMinutes` soma **todas** as durações da semana (`CoachOpenAnswer.kt:154-155`); `CoachSessionAnswer.build` soma `weeklyMinutes` (`:78-82`).
**Inferência.** "Corra 40 min terça" passa se 40 min é duração de quinta; "hoje você tem 120 min" (total semanal) passa como se fosse do dia — número "do plano", semanticamente errado para a pergunta.
**Sugestão.** Escopar allow-lists ao `targetDate` no fluxo de sessão; marcar o total semanal como citável só com o rótulo.
**Como refutar.** Conferir se `allowedMinutes` já filtra por data (na leitura, não filtra).

### [COACH-12] Número informado pelo próprio usuário nunca é citável — **Baixa**

**Fato.** `dossierNumbers` descarta a linha `PERGUNTA DO ATLETA:` (`CoachOpenAnswer.kt:102, 258`) — decisão deliberada; efeito colateral: "corri **15 km** no sábado, faz sentido?" → eco de 15 rejeitado (`uncoveredCount`).
**Sugestão.** Adicionar aos allow-lists apenas números extraídos do texto tipeado (mantendo pace/bpm proibidos).
**Como refutar.** Testar eco de número da pergunta no `CoachOpenAnswerTest`.

### [COACH-13] Corpo de erro do upstream pode ir para o logcat — **Média** ✅verificado

**Fato.** `CoachApi.kt:116`: `IOException("coach upstream $code: ${body.take(200)}")`; a VM loga `e.message` (`CoachViewModel.kt:821,832`). Se um 400 do provedor ecoar o payload, até 200 chars (com `question`/`engineContext`) vão para o log — viola "sem payload em log" (ADR 0051 §2.3; `AUDITORIA_POS_IMPLEMENTACAO_COACH §4.6` problema 4/P-22, **continua aberto**).
**Sugestão.** Logar só status + código estável, sem `body`.
**Como refutar.** Ler `:116` — o `take(200)` está lá; se o `e.message` não for logado em nenhum ponto, o achado cai (a VM loga).

### [COACH-14] Consentimento/memória — sem gap crítico, com ressalvas — **Informativo**

Consentimento v2 com copy explícita do payload (`CoachConsentStore.kt:13-21,33`); TTL 90/180 d com prune lazy; revogação em `appScope`; export/delete cobrem turns (testado). Payload sem nome/GPS/timestamps (`CoachFreeAnswerPrompt.kt:11-14`). Ressalvas: (a) LGPD/região declarada como aceita (ADR 0051 §2.5); (b) `question` pode conter texto de saúde — a porteira local é regex, não garantia (`COACH-09`); (c) a corrida paralela dispara 2 requisições Go por pergunta (trade-off registrado em ADR 0051 §5).

### [COACH-15] Fallback determinístico — bom, com bordas de copy — **Baixa**

Fallback de sessão usa a prescrição em dia ajustado e não duplica prefixo (`CoachSessionAnswer.kt:119-153`); `CoachFallbackLabels` sinaliza `reason` técnico. Bordas: (a) `CLARIFY` pergunta "dia e minutos" mesmo para perguntas sem dia; (b) `OUT_OF_SCOPE` sem CTA (`COACH-09`); (c) `NO_CONNECTION`/`GENERIC` para `ASK_COACHING` prometem "tá aqui embaixo" mas **não há card** no fluxo aberto — a emenda ADR 0063 §6 corrigiu só `freeAnswerReasonCopy` (`CoachFallbackLabels.kt:14-20`), o `FreeAnswerCopy.NO_CONNECTION` (`:1209`) continua com a promessa.
**Sugestão.** Alinhar `NO_CONNECTION`/`GENERIC` à mesma correção da emenda §6.

### [COACH-16] Prompt/cadeia — funcionais, com modos de falha conhecidos — **Informativo**

`SYSTEM_FREE_ANSWER` (`Main.kt:197-226`): pt-BR, responde só com o dossiê, honestidade, ≤3 bolhas ≤160 chars, proíbe siglas/códigos/diagnóstico/treino novo/promessa de memória, schema `{title,body,bullets}`. Cadeia Go (GLM/MiMo/DeepSeek) → OpenRouter free. Modos de falha documentados: timeout 25 s (`PROGRESS.md:231-236`), "número fora do contexto: 4" (`:103`), "sugere treino novo" (ADR 0057 §4), JSON sem escape (ADR 0051 §5, corrigido), `MissingSessionID`, 429. Fragilidades: **persona hardcoded com o nome do usuário 0** no system prompt (multiusuário impossível sem novo prompt); cadeia free instável por natureza; **redeploy manual do Dokploy pendente** (`HANDOFF_IA_COACH §5.4`).
**Sugestão.** Parametrizar a persona; tratar o redeploy como condição de aceite.

### [COACH-17] `CoachViewModel` com 8 dependências e 1.222 linhas — **Baixa**

Construtor com consent/sources/api/memory/timeProvider/appScope/replanFactory (`:200-208`) + composições top-level no mesmo arquivo (`:948-1221`); `LongParameterList` no baseline do detekt. `CoachViewModelTest` ~82 testes num arquivo. **Sugestão.** Extrair `CoachFreeAnswerCoordinator` (meio caminho já existe com `answerSessionContent`/`answerProgressContent`).

### [COACH-18] Acessibilidade/PRD chat parcialmente aberto — **Baixa**

`heading()` só em `CoachHistoryCard.kt:72` e `CoachChatHeader` (`CoachChatComponents.kt:84`); `CoachNextCard`/`Guards`/`Explanation`/`Review`/`Progress` sem heading; **sem timestamps nas bolhas** (`CoachBubble`, `:109-153`) apesar de `PRD_COACH_CHAT §6.2` e critério 6 de §12 pedirem "hora". **Sugestão.** headings + hora por turno.

### [COACH-19] Pontos fortes (contrabalanço)

Domínio 100% puro; motor é autoridade (toda resposta passa por allow-lists; `Invalid` nunca vira memória — `:787-817`); invariante "todo número do dossiê é citável" com teste; fallback com conteúdo de motor; proxy stateless com orçamento real (corrida MiMo/GLM, 20 s < 25 s do app, `UpstreamBudgetTest`/`FreeAnswerRaceTest`, sem log de payload); privacidade estruturada (TTL, revogação, export/delete); conservadorismo explícito (pace/FC no cartão determinístico, nunca em prosa); correções guiadas por evidência do aparelho com teste de regressão; ~458 testes coach.

---

### [VALIDADOR] Casos de borda do `CoachResponseValidator` — over-rejection (resposta legítima cai no fallback)

> Código: `core/domain/.../coach/CoachResponseValidator.kt` e `CoachNumberPatterns.kt`. Severidade Média (custo de UX; já gerou 3 rodadas de correção pós-aparelho) · Confiança Alta (leitura direta das linhas).

| ID | Caso | Mecanismo (linha) | Exemplo concreto | Como refutar |
|---|---|---|---|---|
| `V-1` | Relógio/`h` vira pace | `pacePattern = \d{1,2}:\d{2}(/km)?` (`:26`) casa qualquer `HH:MM` | "o forte às **17:30**" → `Invalid "contém pace"` | Testar `validate` com "17:30" |
| `V-2` | Descanso com verbo + substantivo | `prescribes` = verbo ∈ lista E (estrutura ∨ número) (`:190-194`); "descanse" ∈ verbos (`:57`) e "descanso" ∈ estruturais (`:68`) | "**Hoje é descanso — descanse bem.**" → "sugere treino novo" | Rodar o caso no `CoachResponseValidatorTest` |
| `V-3` | Percentual decimal | `percent = (\d{1,3})\s?%` (`CoachNumberPatterns.kt:14`) casa só o inteiro | fatos com "82%", modelo escreve "**82,5%**" → rejeita "5%" | Testar com "82,5%" |
| `V-4` | Qualquer `%` em week/session | esses fluxos passam `allowedPercents = emptySet()` (`CoachViewModel.kt:701,997`) | modelo explica 80/20 em `ASK_WEEK` → "80%" rejeitado | Conferir os call sites |
| `V-5` | Eco da data ISO | `CoachSessionAnswer.kt:247` imprime `DIA: Amanhã (2026-11-26)`; `bareNumber` casa dígitos | "treino de **26**/11" → "número fora do contexto: 26" | Reproduzir com data futura |
| `V-6` | Número do próprio usuário | `CoachOpenAnswer.kt:102` exclui a pergunta dos allow-lists | "corri **15 km** no sábado" → eco de 15 rejeitado | `CoachOpenAnswerTest` |
| `V-7` | ~~"atleta" ⊃ "atl"~~ | **corrigido** (tokens com `\b`, `:42-43`) | regressão coberta por teste | — |

### [VALIDADOR] Under-rejection (número ruim passa)

| ID | Caso | Mecanismo (linha) | Exemplo concreto | Como refutar |
|---|---|---|---|---|
| `U-1` | Explain sem checagem de %/km/dígito nu | `allowedPercents = null` pula a checagem (`:288-291`); sem `inventedDistance`/`uncoveredCount` neste caminho | explain pode dizer "fechou **85%** da semana" ou "**10 km**" e passar | Ver `COACH-10` |
| `U-2` | Cobertura cega por valor | `uncoveredCount` casa qualquer dígito cujo inteiro está em qualquer allow-list (`:236-242, 269-271`) — sem unidade | `6` ∈ contagens (6 treinos) → "**dormiu 6 horas**" passa | Testar "dormiu 6 horas" |
| `U-3` | Decimais escapam | `raw.toDoubleOrNull()?.takeIf { it == it.toInt().toDouble() }` (`:240`) descarta `12,5` | "carga subiu **12,5**%?" → passa | Testar com decimal |
| `U-4` | Distância sem "km" | `distanceKm` exige literal `km` (`CoachNumberPatterns.kt:15`) | "corra **5 mil metros**" → dígito nu → passa se `5` ∈ allow-list | Testar |
| `U-5` | Troca de treino sem número, sinônimo fora da lista | `prescribes` exige verbo de lista de 11 (`:53-66`) **e** token estrutural de 7 (`:67-69`) | "**Troque o intervalado por uma rodagem leve.**" → passa, prescrevendo troca (viola `TASK_LINE`, `CoachOpenAnswer.kt:404`); "diminua/cancele/pare/remarque" nem são verbos da lista | Testar a frase |
| `U-6` | Promessa de memória com sinônimo | `memoryPromises` fecha 4 frases (`:102-104`) | "**Vou guardar** isso pra próxima semana" → passa | Testar |
| `U-7` | Allow-list cruzando dias | ver `COACH-11` | "corra 40 min terça" com 40 min sendo de quinta | Conferir escopo das listas |

**Leitura equilibrada:** o validador é conservador no caminho livre (rede de segurança eficaz para duração/distância/contagem inteira), mas com duas fendas estruturais — o caminho `explain` é mais frouxo que o PRD promete (`U-1`/`COACH-10`) e a regra de "prescrição" depende de listas de palavras fechadas (`U-5`) — além do custo de UX das rejeições por eco (`V-1..V-6`).

---

## 9. Seção F — Documentação × implementação

### [DOC-01] "Teste guiado (Tier C)" prometido e inexistente — **Alta**

**Fato.** `PRODUCT.md:62` — "Calibração por prova/TT recente (Tier A/B) **ou teste guiado (Tier C)**" listado no MVP. Busca negativa: nenhum fluxo de teste guiado em `app/src/main` (grep "guiado" só retorna copy orientando o usuário a fazer sozinho: `CalibrationScreen.kt:469,478`, `ProfileScreen.kt:385`, `OnboardingScreen.kt:475`; grep `guidedTest` = 0). No código, Tier C é apenas o estado honesto "sem dados" (`LevelGraduationEngine.kt:21`, `SPORTS_SCIENCE §4.2` + `R-CAL-02`).
**Inferência.** Promessa de escopo sem entrega; é também a porta de entrada de calibração para quem não corre prova — o usuário 0 precisa dele.
**Sugestão.** Implementar (cronômetro de TT 30 min com o protocolo do doc §4.2: aquecimento 10 + esforço 30 + desaquecimento 10, com avisos de segurança) **ou** rebaixar a promessa no PRODUCT.md com justificativa.
**Como refutar.** grep `teste guiado|guidedTest|Teste30` em `app/src/main` — se existir fluxo, o achado cai.

### [DOC-02] "Execução guiada (contrato)" no MVP, implementação rejeitada — **Alta**

**Fato.** `PRODUCT.md:68` — conclusão com evidência inclui "execução guiada (contrato)"; `CompleteSession.kt:101-102` devolve `Rejected("A sessão guiada persistente ainda não está disponível…")`; etapa G "Proposta" (`ROADMAP.md:430`), ADR 0027 "segue proposto" (`:425`).
**Sugestão.** Ou implementar no Ciclo 3 conforme ADR 0027, ou tirar do "Dentro" do PRODUCT.md.
**Como refutar.** Conferir `CompleteSession` e o estado da etapa G.

### [DOC-03] Métricas de sucesso sem instrumentação — **Alta**

**Fato.** `PRODUCT.md:100-109` promete "crash-free ≥ 99,5%", "import ≥ 99%", "tempo até primeiro insight < 2 min", "usuário explica a recomendação ≥ 80%", "dados com confiança A/B ≥ 70% após 4 semanas". Busca negativa: 0 SDKs de analytics/crash (só a copy "Sem nuvem, sem conta e sem analytics" em `DataManagementScreen.kt:260`), **0 arquivos `androidTest`** (`TESTING.md:56`), nenhum plano de "teste moderado". O `PRD_TREINO_REAL §?` já rebaixou para métricas locais em testes/goldens, mas o PRODUCT.md não foi reconciliado.
**Sugestão.** Decidir: (a) métricas locais agregadas/opcionais com opt-in explícito (compatível com privacidade) ou (b) reescrever §6 como "métricas de aceite do desenvolvimento" e remover as de produto. Estado atual: promessa impossível de auditar.
**Como refutar.** grep `crashlytics|analytics|firebase` e listar `**/androidTest/**`.

### [DOC-04] "Notificação de import pendente" inexistente — **Baixa**

**Fato.** `PRODUCT.md:73` lista "import pendente" entre notificações; canais existentes: `meucoach-reminders`, `meucoach-imports` (concluída — sucesso/duplicado/falha, `ROADMAP.md:297`), `meucoach-story`; grep `IMPORT_PENDING` = 0.
**Sugestão.** Corrigir a copy do PRODUCT ou implementar.

### [DOC-05] "Ter meus dados comigo para sempre" sem restauração — **Média**

**Fato.** `PRODUCT.md:52,129` prometem arquivos originais/backup; não existe import/restore de backup (grep `restoreBackup|importBackup` = 0; `ROADMAP.md:858` T-06 "ZIP descartado"; declarado pendente em `REVISÃO…:1153` C.9.5).
**Sugestão.** Export + **import** no mesmo ciclo, ou deslocar a promessa explicitamente no PRODUCT.

### [DOC-06] PRODUCT.md desatualizado × features novas — **Alta**

**Fato.** `PRODUCT.md:3` (v1.2, 06/11/2026) não menciona a **aba Coach/IA Coach** nem o **Story Studio/Arte PNG/foto**, implementados depois (`ROADMAP.md:365-397` P1-18; `:753-824` P3-06..P3-11). `DECISIONS.md:179` e `ROADMAP.md:320` ainda descrevem a barra como `Hoje · Corridas · Tendências · Saúde · Perfil` — **stale** (real: `Hoje · Corridas · Coach · Tendências · Saúde`, `MeuCoachApp.kt:76-86`).
**Sugestão.** Reconciliar PRODUCT.md v1.3 com o escopo entregue; corrigir a descrição da barra em DECISIONS/ROADMAP.
**Como refutar.** Conferir as linhas citadas.

### [DOC-07] "Visão de fases" contradiz o entregue — **Média**

**Fato.** `ROADMAP.md:14` marca a Fase 3 (Story, LLM…) como "Planejada", embora P3-05..P3-11 estejam concluídos e o LLM opere no proxy do Coach. Itens realmente não implementados: Zepp (grep só fixtures/strings), widgets (grep `Glance` = 0), backend/sync (5 módulos no Gradle; único backend é `infra/coach-proxy`).
**Sugestão.** Dividir a fase em "Story/LLM (entregue)" × "Zepp/Wear/backend (planejada)".

### [DOC-08] Caixas de aceite do PRD do Ciclo 4 nunca marcadas — **Baixa**

**Fato.** `PRD_TREINO_REAL.md:570-574, 636-639, 697-700, 819-823` seguem `- [ ]` nos aceites FR-401..FR-405, embora `CICLO_4_TREINO_REAL.md` registre C4-01..C4-61 como `[x]`.
**Sugestão.** Higiene: fechar as caixas do PRD de origem.

### [DOC-09] Débito técnico e flakes conhecidos (consolidado)

| Item | Onde | Estado |
|---|---|---|
| REF-01 — 7 achados do Detekt no baseline (inclui `LargeClass` do `WeeklyPlanGenerator`) | `ROADMAP.md:582-604` | Aberto desde 16/11; pronto = sair do baseline sem mudar comportamento |
| 88 entradas no baseline do Detekt | `PROGRESS.md:6754` | Dívida congelada; `maxIssues: 0` p/ código novo |
| DEBT-02 — import lê arquivo inteiro (64 MB), OOM não medido | `REVISÃO…:139` | P2 |
| PERF-01 — agregação no Main não medida | `REVISÃO…:140` | P2 |
| Drift de goldens `activity-comparison-*`, `checkin-*`, `health-*` | `CICLO_4…:1075,1115` | Fora do escopo; **não listado** em `TESTING.md` §10.1 |
| Taper × deload sem ADR | `ROADMAP.md:542-543` | Backlog |
| Flakes: `MetricsImportViewModelTest`, `TutorialSystemBackTest`, `TodayViewModelPersistTest` | `TESTING.md:476-501` | Registrados, com política anti-`@Ignore` |
| **Zero** `TODO/FIXME/HACK/@Ignore` no código Kotlin | grep repo-wide | Forte (débito vive em baseline/backlog visível) |

### [DOC-10] Threads abertas consolidadas (handoffs)

| Handoff | Pendência declarada |
|---|---|
| `HANDOFF_IA_COACH` (25/11) | Revalidar "avalie meu treino" no aparelho; **redeploy do Dokploy**; chave Go/região; depois P1-12 |
| `HANDOFF_POS_CHAT_TURNS` (21/11) | **Desatualizado** — v21/input livre já implementados; pendência real: uso real do resumo |
| `HANDOFF_COACH_V3` (20/11) | F4 (roteiros diários) do `PRD_COACH_CHAT` |
| `HANDOFF_COACH_UPSTREAM_E_BOLHAS` (20/11) | Validação no aparelho + redeploy com chave Go; upstreams free degradados |
| `HANDOFF_COACH_CONSOLIDACAO` (19/11) | Bloco C/F4; validação manual pendente; P-06..P-13 não citados como fechados |

Roadmap consolidado: **P1-12 (4 semanas de uso real) é o único critério de saída do MVP não marcado** (`ROADMAP.md:299,877`); + validações de aparelho P1-14/15/16/18, REF-01, N-05, Fase 2 (P2-01..08), Fase 3 (Zepp/social/Wear), Story fase C/DOM-09/MAP-10/ENC-08, backlog T-01/T-04/T-07..09, ADR 0027/etapa G.

---

---

## 10. Seção G — Geral: produto, processo e UX do app

### [GEN-01] Três promessas do MVP sem implementação nem desbancamento — **Alta** (agrupa `DOC-01/02/03`)

Ver `DOC-01..03`. A ação é uma decisão, não um fix: **implementar** (teste guiado Tier C é o mais valioso para o usuário 0) **ou** rebaixar o PRODUCT.md com justificativa e versão. Enquanto durar, o PRODUCT.md não é auditável — e ele é a promessa pública do produto.

### [GEN-02] PRODUCT.md e barra de abas desatualizados — **Alta** (agrupa `DOC-06/07`)

Reconciliar PRODUCT.md (aba Coach, Story/Arte) e corrigir a descrição da bottom bar em `DECISIONS.md:179`/`ROADMAP.md:320`.

### [GEN-03] Restauração de backup prometida e inexistente — **Média** (agrupa `DOC-05`)

Export sem import é meia porta de privacidade ("levar meus dados" inclui voltar). Priorizar T-06 no mesmo ciclo do export.

### [GEN-04] Calibração Tier C como feature — **Média** (detalhe de `DOC-01`)

**Sugestão (produto):** cronômetro de TT de 30 min no app seguindo o protocolo do `SPORTS_SCIENCE §4.2` (10 facile + 30 forte constante + 10 desaquecimento; bloqueio se doente/sono <6 h), com pré-preenchimento de LTHR/pace de limiar e confirmação explícita. Alavanca: converte usuário 0 (sem prova futura) em perfil calibrado — que é pré-condição de qualidade no cold-start (ADR 0053) e de graduação. Esforço estimado: 1 fatia; exige ADR (é fluxo de segurança: o doc já tem o protocolo).

### [GEN-05] Validação física no aparelho pendente em ~15 itens — **Média**

Story (7 itens em `PROGRESS.md:654-662` + P3-05..P3-11), Coach (revalidação pós-emendas + redeploy Dokploy), P1-14/15/16/18, ADR 0062 (neon pós-reencode), emulador API 31 + low-RAM. **Risco:** o código está verde, mas o produto não está provado no dispositivo — e várias correções desta auditoria (`STORY-02/03`, `COACH-05/06`) são exatamente de interação real.
**Sugestão.** Checklist único de aparelho (uma sessão, ~2 h) com esses itens, antes de qualquer feature nova.

### [GEN-06] P1-12 (4 semanas de uso real) — único critério de saída do MVP não marcado — **Média**

`ROADMAP.md:299,877`. Todas as features do MVP têm caixa marcada; a validação longitudinal não. **Sugestão.** Definir o protocolo mínimo de P1-12 (o que registrar por semana: adesão, downgrades de qualidade, falhas do Coach, uso do Story) para que "4 semanas" termine com evidência e não com impressão.

### [GEN-07] UX de primeira execução e prontidão — **Baixa/Média**

(a) `SCI-01`: gauge "Moderada 50" sem dados; (b) o onboarding são 3 passos (`OnboardingScreen.kt:194-196`) e já pede nível + disponibilidade + rotina — bom, mas não explica que **a disponibilidade é o teto rígido do plano** (ligação direta com `ALG-01`: usuário não sabe que 10 min/dia vira 20); (c) não há explicação de que o nível muda o plano só a partir da BASE 2ª metade (`SEG-01`). **Sugestão.** Micro-copy no onboarding: "Esse é o seu teto — o plano nunca passa disso" + "Seu nível libera estímulos de BUILD em diante".

---

## 11. Seção H — Pontos fortes globais (contrabalanço obrigatório)

Uma auditoria que só lista problemas é enviesada. O que foi verificado como **bom** nesta sessão:

1. **Motor determinístico com rastreabilidade total:** toda recomendação cita `ruleId`/`evidenceId`/`confidence` (`AGENTS.md §3.5` respeitado); `inputsHash` completo; versões de motor persistidas (`weekly-plan-v8`, `daily-prescription-v7`).
2. **Gate do dia 6/6 correto (E10)** e blocos de volume nomeando a causa (E11/E16 âmbar) — a promessa "por quê auditável" se cumpre nos caminhos principais.
3. **Conservadorismo real, não decorativo:** sRPE nunca libera progressão; ACWR só decide quando confiável (com Impellizzeri citado); VDOT com proveniência; calor no extremo conservador; sem dados → confiança baixa, nunca recomendação agressiva.
4. **Separation of concerns exemplar:** `core:model`/`core:domain` puros (verificado: nenhum import Android no domínio), UI sem regra de treino, adapters canônicos para entrada externa.
5. **Qualidade de engenharia:** ~2.400+ testes (domain 1.338 / app ~1.547 / data 243 nos últimos gates), gates de cobertura bloqueantes no `check` (Kover 85% domain, 90% branches FIT), detekt `maxIssues: 0` para código novo, **zero** `TODO/FIXME/HACK/@Ignore` no repo, flakes registrados com política anti-redução de assert.
6. **Rastreabilidade de decisão:** 65 ADRs indexados, revisões que não reescrevem o passado, handoffs com prompt de retomada, `PROGRESS.md` detalhado por sessão.
7. **Privacidade estrutural:** `allowBackup=false`, sem analytics, fixtures sanitizadas, reset com época, consentimento versionado do Coach (v1→v2), TTL e revogação testados, payload do Coach sem nome/GPS/timestamps.
8. **Story Studio:** estados da arte como referência de UX, export em background com ETA/cancelamento, contrato de vídeo correto, ~500 testes, sci-integrity preservada (veredito nunca entra no vídeo público).
9. **Coach:** validador como autoridade (resposta inválida nunca vira memória), proxy stateless com orçamento de latência real e corrida de modelos, fallbacks com conteúdo de motor em vez de erro técnico.
10. **Correção guiada por evidência:** as emendas do ADR 0063, o fix de data local e o ADR 0065 nasceram de logs/reprodução no aparelho, cada uma com teste de regressão — o ciclo de correção funciona.

---

## 12. Seção I — Priorização sugerida (esforço × impacto)

> Sugestões, não ordens. Rejeitar uma linha não invalida o achado correspondente.

| Prioridade | Ação | Achados | Esforço estimado |
|---|---|---|---|
| **P0 — esta semana** | Fix `retryFreeAnswer` para usar `pending.question` + teste | `COACH-06` | XS (1 h) |
| **P0** | Remover `body.take(200)` do `IOException` (log) | `COACH-13` | XS |
| **P0** | `revoke()` limpa `_exchanges`; return vira `Fallback` efêmero | `COACH-05` | XS |
| **P0** | Alinhar `NO_CONNECTION`/`GENERIC` do `FreeAnswerCopy` à emenda ADR 0063 §6 | `COACH-15c` | XS |
| **P1 — próximo ciclo** | `plannedDuration`: respeitar teto (remover dia < piso) + teste E14/E18 | `ALG-01`, `ALG-07` | S |
| **P1** | Recalcular `growthPercent`/copy após `fitToVolume`; suavizar `dropShortestEasy` | `ALG-03` | S |
| **P1** | Ligar allow-lists de %/km no caminho `explain` | `COACH-10`, `U-1` | M |
| **P1** | Reconciliar PRODUCT.md (Coach/Story/barra) + decidir Tier C/GUIDED/métricas | `DOC-01/02/03/06`, `GEN-01/02` | M (doc) |
| **P1** | Checklist único de validação no aparelho (Story+Coach+P1-14..18) | `GEN-05` | M (tempo) |
| **P2** | Teto/aviso de frequência para iniciante (com evidência + ADR) | `ALG-04` | M |
| **P2** | Decisão doc×código: strides/taper do iniciante + copy da fase | `SEG-03`, `ALG-06` | S (emenda+teste) |
| **P2** | Dica de graduação × disponibilidade (card de nível) | `ALG-05`, `SEG-05` | S |
| **P2** | Adjacência qualidade × longão anterior | `ALG-02` | S |
| **P2** | Story: estados da prévia + Cancelar em `PreparingMap` + CTA pós-import | `STORY-01/02/03` | M |
| **P2** | Story: `McTextButton` 48 dp + golden 200% do vídeo | `STORY-06/07` | S |
| **P2** | Coach: follow-up com última pergunta no `engineContext` | `COACH-01` | M |
| **P3** | Multi-turn com turnos persistidos; streaming; feedback por mensagem | `COACH-01/02/03` | L (pós-MVP) |
| **P3** | Diferenciação de avançado (escopo declarado ou ADR) | `SEG-02` | L |
| **P3** | Hidratar feed do Coach; timestamps; headings | `COACH-08/18` | M |
| **P3** | Unificar `loadTrusted`; hint de bloco no capping; label "Sem dados" | `ALG-08/09`, `SCI-01` | S |
| **P3** | Teste guiado Tier C (feature) | `GEN-04` | M–L (ADR) |

---

## 13. Seção J — Checklist de auditoria para o revisor (LLM ou humano)

Use este checklist para **confrontar** o documento. Cada item tem resposta "sim/não/evidência".

1. **Reprodutibilidade:** o `Apêndice A` + `./gradlew :core:domain:test --tests "*AuditExperiment*" reproduz E1–E19? Alguma saída diverge da tabela §2.2? (Se divergir, os achados `ALG/SEG` dependentes precisam ser reavaliados.)
2. **ALG-01:** existe clamp posterior que anule o estouro do teto? O doc §8.3 permite piso > teto em algum trecho?
3. **ALG-03:** confira `weeklyMinutes` de E16: é realmente < `baseMinutes`? A copy realmente contém "+6%" na mesma frase?
4. **ALG-04:** encontre a regra de frequência por nível que o auditor não encontrou (grep `maxDays|frequência` em `core/domain`) — se existir, o achado cai.
5. **SEG-03:** leia `SPORTS_SCIENCE §8.4` e decida se "Todos" cobre o iniciante em taper. Se sim → código errado; se não → doc ambíguo. Nos dois casos há ação; diga qual.
6. **SEG-04:** existe implementação da "revisão por regressão" prometida em `LevelGraduationEngine.kt:89-90`?
7. **SCI-01:** rode o app sem dados e fotografe o gauge — a UI real trata `INSUFFICIENT_DATA` de forma diferente do que a leitura sugere?
8. **COACH-10/U-1:** leia `CoachResponseValidator.validate` (não `validateFreeAnswer`) e confirme se `allowedPercents` chega `null` no explain.
9. **COACH-06:** leia `retryFreeAnswer` — `text` vem de `lastFreeText` ou de `pending.question`?
10. **STORY:** confira os pares `arquivo:linha` de STORY-01/02/03/06; algum estado já tratado em outro componente?
11. **DOC-01/02/03:** execute as buscas negativas (grep) — algum fluxo de teste guiado/GUIDED/analytics existe?
12. **Sesgamento:** o documento algum momento trata inferência como fato? Os achados "Crítica" estão realmente ausentes? As seções de pontos fortes (§4.10, §6.4, §7 STORY-19, §8 COACH-19, §11) estão presentes e substanciais?
13. **Severidade:** você discorda de alguma classificação? Registre com o critério §1.2 — divergência de juízo não derruba o fato.
14. **Escopo:** as limitações §2.3 foram respeitadas (nada alegado sobre o que não foi medido)?

**Respostas esperadas do revisor:** para cada achado, uma de: `CONFIRMADO` / `REFUTADO (evidência)` / `SEVERIDADE AJUSTADA (motivo)` / `NÃO VERIFICÁVEL (escopo)`.

---

## Apêndice A — Reprodução dos experimentos

O harness foi **removido do repositório** após a coleta (não é suíte de regressão; escrevia em `/tmp`). Para reproduzir:

1. Crie `core/domain/src/test/kotlin/com/meucoach/core/domain/prescription/AuditExperimentTest.kt` com o conteúdo abaixo.
2. `rm -f /tmp/opencode/audit-experiments.txt && ./gradlew :core:domain:test --tests "*AuditExperimentTest*"`
3. Compare a saída com a tabela §2.2.
4. Ao terminar, **apague o arquivo** (não deve ir para o repositório: escreve fora do repo e não é teste de regressão).

### A.1 Builders (copiar tal qual)

```kotlin
package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.load.LoadCoverage
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.Pace
import com.meucoach.core.model.ReadinessLevel
import com.meucoach.core.model.Sex
import com.meucoach.core.model.WeeklyAvailability
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

class AuditExperimentTest {
    private val generator = WeeklyPlanGenerator()
    private val monday = LocalDate.of(2026, 11, 2)
    private val out = File("/tmp/opencode/audit-experiments.txt")

    private fun log(line: String) { out.appendText(line + "\n") }
    private fun dump(label: String, plan: WeeklyPlan) { /* imprime rationale/conf/sessões conforme §2.2 */ }

    @Suppress("DEPRECATION")
    private fun input(
        profile: AthleteProfile, readiness: ReadinessResult, load: LoadSnapshot? = null,
        weekStart: LocalDate = monday, today: LocalDate = weekStart,
        previousWeek: WeeklyPlan? = null, recentWeeks: List<WeeklyPlan> = emptyList(),
        race: RacePlan? = null,
    ) = WeeklyPlanInput(profile, readiness, load, weekStart, today,
        previousWeek, recentWeeks, race = race)

    private fun profile(experience: Experience, availability: Map<DayOfWeek, Int>) = AthleteProfile(
        id = "audit-1", birthDate = LocalDate.of(1988, 1, 1), sex = Sex.MALE,
        restingHeartRate = 58, maxHeartRateObserved = 187, lactateThresholdHeartRate = 180,
        thresholdPace = Pace.fromSecondsPerKm(360), experience = experience,
        timezone = "America/Sao_Paulo", weeklyAvailability = WeeklyAvailability(availability),
    )

    private fun readiness(level: ReadinessLevel, flags: Set<ReadinessFlag> = emptySet(),
        components: Map<ReadinessComponent, Int> = mapOf(ReadinessComponent.SUBJECTIVE to 80),
    ) = ReadinessResult(
        score = when (level) { ReadinessLevel.GREEN -> 80; ReadinessLevel.AMBER -> 55; ReadinessLevel.RED -> 20 },
        level = level, confidence = DataConfidence.HIGH, components = components,
        missing = if (components.isEmpty()) setOf(ReadinessComponent.SUBJECTIVE) else emptySet(),
        flags = flags,
    )

    // load KNOWN/objetivo: 12 dias com carga, fonte fresca, monotonia 1,2, strain 400
    private fun load(acwr: Double?) = LoadSnapshot(
        daily = emptyList(), ctlSeries = emptyList(), atlSeries = emptyList(),
        ctl = 0.0, atl = 0.0, tsb = 0.0, acuteAverage7d = 0.0, chronicAverage28d = 0.0,
        acwr = acwr, monotony = 1.2, missingDays = 16,
        coverage = LoadCoverage(28, 12, 16, 0,
            java.time.Instant.parse("2026-10-30T10:00:00Z"), sourceFresh = true),
        subjectiveDaysInWindow = 0, strain = 400.0,
    )

    // Semana concluída com evidência ACTIVITY (ter/qui/sex/dom em 40/40/40/70)
    private fun completedWeek(weekStart: LocalDate, strongMinutes: Int = 150): WeeklyPlan { /* como no harness original */ }
}
```

### A.2 Receita dos experimentos (inputs exatos)

| E | Teste | Inputs |
|---|---|---|
| E1 | `experiment01` | avail `{SEG40, QUA40, SEX40, SÁB90}`; verde; load 1,0; para cada `Experience` |
| E2 | `experiment02` | avail `{TER40, QUI40, DOM90}`; `race = RacePlan(5K, monday+10)`; 3 níveis |
| E3 | `experiment03` | avail `{SEG10, QUA40, SÁB90}`; inter; sem histórico |
| E4 | `experiment04` | avail `{SEG40, QUA20, SEX40, SÁB90}`; inter; +1 semana concluída |
| E5 | `experiment05` | avail 7 dias × 40; iniciante |
| E6 | `experiment06` | avail `{TER50, QUI50, SÁB50}`; iniciante |
| E7 | `experiment07` | avail 6 dias (50–120); avançado; 3 semanas concluídas |
| E8 | `experiment08` | avail `{QUA45, SÁB55}`; iniciante; 5K em 40 dias |
| E9 | `experiment09` | avail `{TER50, SÁB90}`; inter; 1 semana concluída |
| E10 | `experiment10` | `PlannedSession` QUALITY 40 min (`INTERVALS`, estrutura do catálogo BUILD) × 6 cenários de prontidão/carga no `DailyPrescriptionEngine` |
| E11 | `experiment11` | semana concluída 200 min; avail `{SEG60, QUA60, SÁB120}`; âmbar × verde |
| E12 | `experiment13`/`12` | `SessionLibrary.qualityTemplates(BUILD/PEAK, nível, 5K)` |
| E13 | `experiment13` | `easyTemplates/qualityTemplates(TAPER, …)` |
| E14 | `experiment14` | avail `{SEG10, QUA10, SÁB90}`; inter — **esperado: QUA 20 > 10** |
| E15 | `experiment15` | 4 semanas concluídas (força BUILD+descarga); avail `{TER40, QUA20, SEX40, DOM90}` |
| E16 | `experiment16` | semana 200 min; avail `{SEG60, QUA60, SEX60, DOM150}`; verde e âmbar — **esperado verde: copy +6%/201, entrega 178** |
| E17 | `experiment17` | `load(acwr = 1.7)`; avail `{SEG60, QUA60, DOM150}` |
| E18 | `experiment18` | 4 semanas concluídas; avail `{TER20, QUI40, SÁB40, DOM90}` — **esperado: TER qualidade 25 > 20** |
| E19 | `experiment19` | semana anterior com longão no **domingo** + 3 semanas extras (força BUILD); avail `{SEG40, QUA40, SEX40, SÁB90}` — **esperado: qualidade na SEG** |

> Os `dump`/`log` do harness imprimem: rationale, confidence, `weeklyMinutes`, `qualityCount`, longão e cada sessão (`data [tipo] título min kind=… targets=… decision=…`), além das linhas `CHECK …` que evidenciam as violações.

---

**Fim do documento.** Achados: 9 `ALG` (10 com fortes), 6 `SEG`, 4 `SCI`, 19 `STORY`, 19 `COACH` + 13 casos de validador, 10 `DOC`, 7 `GEN`. Nenhum dado de saúde real foi usado; nenhum arquivo fora deste documento e do `PROGRESS.md` foi alterado.
