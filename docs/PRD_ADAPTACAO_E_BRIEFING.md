# PRD — Plano que acompanha o perfil e briefing do treino

**Versão:** 0.1 (proposta para revisão) · **Data:** 10/11/2026
**Produto:** MeuCoach — coach de corrida Android, local-first e determinístico.
**Ciclo proposto:** Ciclo 3 — “acompanhar o atleta” (C3).
**Status:** aguardando aprovação do usuário 0 antes de qualquer código.

---

## 0. Ficha e convenções

### 0.1 O que este PRD cobre

Três temas levantados pelo usuário 0, que compartilham o mesmo alvo — fazer o app
se comportar como um treinador que acompanha o atleta:

1. **Mudar o nível em “Editar perfil” precisa mudar os treinos.**
2. **Cadastrar uma prova (ex.: 10 km, com data) precisa fazer o app planejar em volta dela.**
3. **Os textos do treino precisam parecer um coach de verdade**, com como correr, o que observar, o que fazer antes e depois, e dicas gerais de água/sais e alimentação.

### 0.2 Convenções

- **[FATO]** verificado no código ou nos documentos citados.
- **[PROPOSTA]** decisão deste PRD, ainda não implementada.
- **[DECISÃO ABERTA]** precisa de resposta do usuário 0 antes da implementação.
- Prioridade: **P0** crítico · **P1** alta · **P2** média.
- Esforço: **S** (1–2 dias) · **M** (3–5 dias) · **L** (1–2 semanas), para um desenvolvedor assistido por agente.

### 0.3 Documentos consultados

`AGENTS.md`, `docs/PRODUCT.md`, `docs/ARCHITECTURE.md`, `docs/SPORTS_SCIENCE.md`,
`docs/COMMUNICATION.md`, `docs/DESIGN_SYSTEM.md`, `docs/DATA_MODEL.md`,
`docs/ROADMAP.md`, `docs/PROGRESS.md`, `docs/DECISIONS.md`,
`docs/adr/0023-weekly-plan-vs-daily-prescription.md`,
`docs/adr/0028-versioned-plan-replanning.md`,
`docs/PLANO_CICLO_2_GUIADO_E_REPLANEJAMENTO.md`.

---

## 1. Contexto e problema

### 1.1 O que o app já faz **[FATO]**

- O plano semanal é gerado uma vez e persistido em `weekly_plans`; a semana só é
  recriada quando não existe plano salvo (`TodayViewModel`).
- `AthleteProfile.experience` e `TrainingGoal` (tipo, data, distância, tempo) já são
  editáveis e persistidos.
- O gerador usa experiência (qualidade liberada ou não, título do treino forte,
  teto de longão de 150 min) e usa a data da prova apenas para um taper de volume.
- O replanejamento versionado (`ADR 0028`) já existe: `PlanReplanner` regenera só o
  futuro, preserva hoje/passado e sessões concluídas, e a revisão é sempre uma
  sugestão com aceite explícito. Os gatilhos são T1 (sessões perdidas), T2
  (disponibilidade mudou) e T3 (botão do usuário).
- A prescrição do dia (`DailyPrescriptionEngine`) pode manter, reduzir,
  rebaixar ou mandar descansar — nunca aumentar (`R-PRESC-02`).
- A explicação atual é o `rationale` curto da sessão + bottom sheet “Por que este treino?”.

### 1.2 O que não acontece **[FATO]**

| # | Lacuna | Efeito para o usuário |
|---|---|---|
| 1 | Mudar a experiência em “Editar perfil” não invalida o plano salvo | O app “aceita” a mudança e continua prescrevendo o mesmo |
| 2 | Não existe gatilho de perfil no `ReplanTrigger` (só T1/T2/T3) | Nenhuma sugestão aparece após editar o perfil |
| 3 | `PlanReplanner` tem teto: não adiciona volume/qualidade futura | Subir de nível não consegue injetar treino forte na semana corrente, sem aviso |
| 4 | A prescrição do dia não considera experiência como guarda | Um `BEGINNER` pode receber a qualidade que já estava planejada |
| 5 | A troca de prova (distância ou data) não muda o plano salvo | O objetivo é decorativo |
| 6 | `targetDistanceM` e `targetTimeSec` são persistidos e exibidos, mas **nenhum motor os lê** | Distância-alvo não muda longão, volume nem ritmo |
| 7 | O taper é só corte de volume em EASY/LONG; `QUALITY` fica intacta (`WeeklyPlanGenerator` 267–286, 428–435) | A semana da prova ainda tem treino forte |
| 8 | Não existe prova como sessão, nem recuperação pós-prova | O dia da prova pode ser prescrito como treino comum, ou como descanso |
| 9 | `PlannedSession` não tem blocos (aquecimento/principal/recuperação/desaquecimento) | Não dá para dizer “como correr” com precisão |
| 10 | Não existe guia pré/durante/pós no app | O usuário só vê o porquê, nunca o como |
| 11 | O `rationale` é reaproveitado na notificação e no card | Texto curto, técnico e igual em toda parte |
| 12 | Nutrição está fora de escopo em `PRODUCT.md` §4 e `SPORTS_SCIENCE.md` §1/`R-SAFE-03` | Dicas de água/sais e pré/pós não têm base documental para existir |

### 1.3 Problema em uma frase

> O app já sabe calcular, mas ainda não **reage** ao que o atleta muda no perfil e
> não **ensina** como executar o treino que prescreveu.

---

## 2. Objetivos e não-objetivos

### 2.1 Objetivos

| ID | Objetivo | Como saberemos |
|---|---|---|
| O-1 | Mudança de experiência ou de prova leva a uma sugestão de replanejamento clara, com aceite do usuário | Teste de integração + validação com o usuário 0 |
| O-2 | A semana da prova respeita a data real: sem treino forte, prova como evento, recuperação depois | Testes de domínio com datas fixas |
| O-3 | Todo treino tem um briefing de execução legível, curto e específico do dia | Briefing determinístico + revisão de copy |
| O-4 | O briefing inclui orientação geral de água/sais e alimentação pré/pós, sem prescrição nutricional | Copy aprovada + regras e evidências no catálogo |
| O-5 | Nada disso aumenta carga sozinho, inventa número ou usa LLM | `./gradlew check` + revisão dos ADRs |

### 2.2 Não-objetivos (explícitos)

- Áudio, TTS e sessão guiada persistente — continuam no `ADR 0027`, adiados por decisão do usuário 0.
- Periodização completa em blocos base/construção/pico (só o necessário para a prova).
- Prescrição nutricional: calorias, macros, plano alimentar, suplementação, emagrecimento.
- Novos gráficos, novas abas, backend, sync, widgets.
- Segmentos persistidos no modelo de dados (o briefing v1 é derivado, não persistido).
- Mudar a stack, introduzir LLM ou qualquer decisão de treino fora do motor determinístico.

---

## 3. Decisões já fechadas com o usuário 0

| ID | Tema | Decisão | Efeito neste PRD |
|---|---|---|---|
| D-1 | Alimentação | Dicas gerais de água/sais e de alimentos pré/pós, no nível do conhecimento da comunidade de corrida. **Não** é orientação nutricional individualizada. | Cria F4 e exige `ADR 0029` + revisão de escopo em `PRODUCT.md`/`SPORTS_SCIENCE.md`/`AGENTS.md` |
| D-2 | Replanejamento por perfil | **Sempre sugerir e esperar aceite**, com texto inequívoco | Nenhum replanejamento automático; o card de sugestão nasce no ato de salvar e persiste no Hoje |
| D-3 | Ordem | O PRD define a sequência (§13) | C3-01/02 primeiro, depois briefing, depois prova |
| D-4 | Briefing | Overlay/bottom sheet expandido, sem tela própria, em harmonia com o app | Reaproveita o padrão de sheet e os tokens existentes |
| D-5 | Áudio | Fica para depois | Fora do ciclo 3 |
| D-6 | Segurança do nível iniciante | A prescrição do dia rebaixa treino forte quando o nível é iniciante, **sem aceite** — é decisão do dia, dentro de `R-PRESC-02`, não replanejamento | Fecha a antiga AD-1; cria `FR-109` e `R-PRESC-04` |

---

## 4. Escopo funcional

### 4.1 F1 — Plano que acompanha o perfil

**História:** como atleta, quando eu mudo meu nível ou meu objetivo, quero que o app
me diga **o que muda no meu treino** e me deixe aceitar, em vez de ignorar a mudança.

**Como funciona [PROPOSTA]**

1. Ao salvar o perfil, o app calcula uma **impressão digital do plano** (_profile hash_)
   a partir dos campos que realmente mudam treino:
   experiência; tipo de objetivo; data-alvo; distância-alvo; tempo-alvo; acesso a esteira.
   Disponibilidade tem hash próprio desde o `ADR 0028` e não entra aqui.
2. Se a impressão digital salva com a semana for diferente da atual, o app avalia um
   replanejamento com o gatilho novo **T4 `PROFILE_CHANGED`**.
3. O resultado aparece **na hora**, na tela de perfil, e continua disponível no Hoje
   enquanto não for aceito, recusado ou suprimido até o fim do dia — com a mesma
   mecânica de `ReplanSuggestionStore` já existente.
4. O aceite continua transacional e versionado (`applyReplan`): só o futuro muda;
   hoje, o passado e sessões `DONE`/`CLOSED` ficam intactos.
5. Três desfechos possíveis, sempre explicitados:
   - **Sugestão** com diff “o que muda”;
   - **Nada a mudar** — “o resto da semana já cabe no seu novo nível”;
   - **Limite honesto** — quando o motor não pode adicionar carga (subir de nível),
     o texto diz que o ajuste vale a partir da próxima semana, sem prometer o que não faz.

**Regras de segurança [PROPOSTA]**

- O teto do `PlanReplanner` é mantido: nenhuma revisão aumenta volume ou adiciona
  treino forte. `ADVANCED`/`INTERMEDIATE` para `BEGINNER` consegue **reduzir**
  (remover qualidade futura, cortar longão acima de 150 min).
- `BEGINNER` → `ADVANCED` normalmente resulta em “nada a mudar nesta semana”, e o
  texto explica isso. Não é falha: é o mesmo princípio conservador do `R-PLAN-01`.
- **Guarda de experiência no dia (D-6):** a `DailyPrescriptionEngine` passa a rebaixar
  qualquer treino forte para fácil quando o nível vigente é `BEGINNER`, mesmo que o
  plano salvo tenha qualidade naquele dia. É `DOWNGRADE`, que já é uma operação
  permitida (`R-PRESC-02`): só reduz, não cria sessão, não muda o plano persistido e
  não pede aceite. Vale só para hoje — os dias seguintes continuam sendo assunto da
  sugestão de replanejamento. A decisão cita `R-PRESC-04`, explica o motivo no
  `rationale` e não se aplica a sessões `DONE`/`CLOSED` (o passado não é reescrito).

### 4.2 F2 — Prova com data

**História:** como atleta, quando eu marco “10 km no dia 12/04”, quero que o app
organize as semanas em volta dessa data, e não só reduzir volume na última semana.

**Fase 2A — mecânica (P0, sem periodização nova)**

1. O taper passa a ser contado **em dias até a prova**, não a partir da segunda-feira
   da semana gerada, e passa a valer também para `QUALITY` (duração e volume
   reduzidos; a última sessão forte fica a 7–10 dias da prova para 5k/10k,
   conforme `SPORTS_SCIENCE` §8.6).
2. A prova vira uma sessão de tipo **`RACE`** no dia da data-alvo, mesmo que o dia
   não esteja na disponibilidade semanal; ela não conta como qualidade.
3. A semana da prova não recebe treino forte; os dias anteriores são curtos e leves,
   com opção de “ativar” 24–48 h antes (curto, sem fadiga).
4. Se a data-alvo estiver no passado ou a menos de 10 dias, o app **explica o limite**
   e oferece o que é seguro (sem taper inventado). Não cria plano retroativo.
5. Trocar a prova dispara T4 pela impressão digital e cai no fluxo de F1.

**Fase 2B — pós-prova (P1, depende do `ADR 0030`)**

6. Recuperação determinística pelo que já está em `SPORTS_SCIENCE` §8.10:
   1 dia fácil por 3 km, mínimo 3 dias sem treino forte após 5k/10k; 1 semana após
   meia; 2 semanas após maratona.
7. Semana pós-prova: só fácil/descanso, sem treino forte, com texto explicando por quê.

**Uso da distância e do tempo-alvo [PROPOSTA]**

- `targetTimeSec` + `targetDistanceM` definem uma **faixa de ritmo de prova** exibida
  no briefing e no dia da prova, com confiança limitada (é referência, não garantia).
- A distância-alvo limita o longão da semana de pico (fração da distância de prova,
  dentro do teto de 150/180 min).
- Nada disso é usado para prever resultado nem para prometer tempo.

### 4.3 F3 — Briefing do treino

**História:** como atleta, quero abrir o treino do dia e ler, em linguagem de
treinador, o que é o treino, como correr, o que sentir e o que fazer se estiver difícil.

**Formato [PROPOSTA]** — um objeto determinístico `SessionBriefing` em `core:domain`,
com seções fixas e curtas:

| Seção | Pergunta que responde | Conteúdo |
|---|---|---|
| Foco | “O que é hoje?” | 1 frase + alvo principal (ritmo, FC ou esforço) |
| Por que hoje | “Por que este treino?” | 1–2 frases ligadas à prontidão/carga, sem IDs |
| Como correr | “Como eu executo?” | Blocos: aquecimento, parte principal, recuperações, desaquecimento, com duração e alvo |
| O que sentir | “Estou no ritmo certo?” | Sinais de esforço esperado e sinais de alerta |
| Se estiver difícil | “O que eu faço?” | Reduzir, encurtar ou parar — sempre conservador |
| Antes de sair | “O que faço antes?” | Aquecimento, água, expectativa do dia |
| Depois | “O que faço agora?” | Caminhada, água, registrar a corrida |
| Prova | “E no dia da prova?” | Só em `RACE` e na semana da prova |

**Regras**

- Determinístico: mesma prescrição + mesmo perfil + mesma data ⇒ mesmo texto. Sem
  LLM, sem aleatoriedade, sem clima.
- Não cria número novo: todo alvo vem do plano/prescrição. Quando não há calibração,
  o alvo é esforço percebido (RPE), como já acontece.
- Blocos são derivados por **template** (tipo + duração + alvos), sem persistir
  segmentos no schema nesta versão.
- Cada seção cita `ruleId`/`evidenceId` internamente; a UI comum mostra só o texto e
  “Ver fontes e detalhes técnicos”, como em `COMMUNICATION.md` §6.1.
- O briefing de hoje é derivado da **prescrição do dia**, não da sessão bruta: se o
  dia foi rebaixado para fácil, o “como correr” acompanha.

**Exemplo (Tempo, dia verde, com calibração)**

> **Hoje é dia de ritmo sustentado.** 30 min, sendo 20 min em ritmo de limiar.
> **Como correr:** 10 min bem leve para aquecer · 20 min em 5:30–5:40/km, conversa
> difícil mas controlada · 5 min soltando.
> **O que sentir:** na parte forte, respiração curta e constante; se precisar parar
> de falar em frases, está forte demais.
> **Se estiver difícil:** reduza 10 s/km ou faça 2 blocos de 8 min com 2 min leve.
> **Antes:** 5 min de trote + mobilidade; água à vontade.
> **Depois:** 5 min de caminhada, água e registrar a corrida.

### 4.4 F4 — Dicas de hidratação e alimentação

**Escopo permitido (D-1)**

- Água antes de sair; em calor, beber ao longo do dia e levar água em treinos longos.
- Em treinos longos ou muito quentes: água com eletrólitos/sais é uma opção comum.
- Antes de treino longo ou prova: algo leve que a pessoa **já tolera** (nada novo no dia da prova).
- Depois: água e uma refeição com carboidrato e proteína.
- Sempre com o rodapé: “Dica geral, não é orientação nutricional. Com restrição
  alimentar ou condição clínica, siga orientação profissional.”

**Proibido por este PRD**

- Gramas, mililitros, calorias, macros, horários rígidos, suplementos, jejum,
  emagrecimento, substituição de refeição, qualquer cálculo por peso.
- Conteúdo que dependa de diagnóstico ou condição clínica.

---

## 5. Requisitos funcionais

### F1 — Perfil

| ID | Requisito | Prioridade | Esforço |
|---|---|---|---|
| FR-101 | Persistir `profileHash` e um snapshot compacto dos campos de plano junto da semana gerada | P0 | S |
| FR-102 | Novo gatilho T4 `PROFILE_CHANGED`, avaliado quando `profileHash` difere | P0 | S |
| FR-103 | `profileHash` desconhecido (planos legados) **nunca** é inferido como mudança; só registra a referência | P0 | S |
| FR-104 | Avaliar imediatamente ao salvar o perfil e apresentar sugestão, “nada a mudar” ou “vale a partir da próxima semana” | P0 | M |
| FR-105 | Reutilizar `ReplanSuggestionStore` com a mesma semântica `Agora não` / `Não sugerir hoje` | P0 | S |
| FR-106 | Diff “o que muda” deve citar nível/objetivo antigo → novo quando for a causa | P1 | S |
| FR-107 | Downgrade remove qualidade futura e corta longão acima do teto do novo nível | P0 | S |
| FR-108 | Upgrade nunca adiciona carga; o texto explica o limite | P0 | S |
| FR-109 | Prescrição do dia rebaixa treino forte para fácil quando o nível é `BEGINNER`, sem aceite, sem alterar o plano persistido e sem tocar em `DONE`/`CLOSED` | P0 | S |

### F2 — Prova

| ID | Requisito | Prioridade | Esforço |
|---|---|---|---|
| FR-201 | Taper calculado em dias até `targetDate`, com corte também em `QUALITY` | P0 | M |
| FR-202 | Novo tipo de sessão `RACE` no dia da prova, mesmo fora da disponibilidade | P0 | M |
| FR-203 | Semana da prova sem treino forte e com dias curtos e leves | P0 | S |
| FR-204 | Data no passado ou a < 10 dias: explicar o limite e não inventar taper | P0 | S |
| FR-205 | Faixa de ritmo de prova derivada de `targetTimeSec`/`targetDistanceM`, confiança limitada | P1 | M |
| FR-206 | Longão do pico limitado pela distância-alvo, dentro do teto de minutos | P1 | S |
| FR-207 | Recuperação pós-prova (1 dia fácil por 3 km, mínimo 3 dias sem forte; 1 semana meia; 2 maratona) | P1 | M |
| FR-208 | Trocar distância/data/tempo-alvo dispara T4 | P0 | S |

### F3 — Briefing

| ID | Requisito | Prioridade | Esforço |
|---|---|---|---|
| FR-301 | `SessionBriefing` imutável em `core:domain`, com `version`, seções, regras, evidências e confiança | P0 | M |
| FR-302 | Seções de §4.3 montadas a partir da prescrição do dia + plano + perfil | P0 | M |
| FR-303 | Blocos por template para EASY, QUALITY (tempo/intervalos), LONG, RACE e esteira | P0 | M |
| FR-304 | Soma dos blocos coerente com a duração planejada (arredondamento explícito) | P0 | S |
| FR-305 | Sinais de alerta derivados de `R-SAFE-01` e do nível de prontidão do dia | P0 | S |
| FR-306 | “Se estiver difícil” só reduz/encurta/para; nunca aumenta | P0 | S |
| FR-307 | UI: sheet expandido com seções, aberto pelo card do Hoje e pela notificação | P0 | M |
| FR-308 | Acessibilidade: TalkBack na ordem das seções, alvos de 48 dp, contraste AA, tema claro e escuro | P0 | M |
| FR-309 | Nenhum `R-`/`EV-` na superfície comum; fontes sob demanda | P0 | S |
| FR-310 | Notificação do treino passa a usar o foco do briefing, não o rationale bruto | P2 | S |

### F4 — Hidratação e alimentação

| ID | Requisito | Prioridade | Esforço |
|---|---|---|---|
| FR-401 | Seções “Antes” e “Depois” do briefing incluem as dicas de §4.4 | P1 | S |
| FR-402 | Rodapé de não prescrição em toda superfície com dica de alimentação | P1 | S |
| FR-403 | Nenhum número individualizado (ml, g, kcal) na UI | P1 | S |
| FR-404 | Dicas de prova aparecem só em `RACE` e na semana da prova | P1 | S |

---

## 6. Regras, evidências e confiança

### 6.1 Regras novas propostas

| Regra | Texto | Evidência | Confiança |
|---|---|---|---|
| `R-PLAN-09` | Mudança de perfil/objetivo gera revisão sugerida do futuro, nunca automática | ADR 0028, ADR 0030 | STRONG |
| `R-PLAN-10` | Prova é evento próprio; semana da prova sem treino forte; recuperação proporcional à distância | BOSQUET, MUJIKA, ISSURIN | MODERATE |
| `R-PLAN-11` | Taper conta dias até a prova e corta volume **e** duração da qualidade | BOSQUET, MUJIKA | MODERATE |
| `R-PRESC-04` | Nível iniciante rebaixa treino forte no dia, mesmo que o plano o tenha; só reduz | ADR 0026, DANIELS | MODERATE |
| `R-BRIEF-01` | O briefing deriva da prescrição do dia e não cria alvo novo | ADR 0023, DANIELS | STRONG |
| `R-BRIEF-02` | Instrução de execução coerente com duração/alvos (soma dos blocos ≈ planejado) | DANIELS | MODERATE |
| `R-BRIEF-03` | Sinais de alerta interrompem a sessão e recomendam avaliação | `R-SAFE-01` | STRONG |
| `R-FUEL-01` | Orientação geral de água/sais e pré/pós, sem quantidade individualizada | ACSM 2007, THOMAS 2016 | LIMITED/MODERATE |
| `R-FUEL-02` | Nada novo no dia da prova; sem prescrição nutricional | THOMAS 2016, `R-SAFE-03` | STRONG |

### 6.2 Evidências novas propostas (a validar antes de entrar no catálogo)

| ID | Referência | Uso |
|---|---|---|
| `EV-ACSM-2007` | Sawka, M. N. et al. ACSM position stand: exercise and fluid replacement. *Med Sci Sports Exerc* 39(2):377–390, 2007. | Hidratação e eletrólitos |
| `EV-THOMAS-2016` | Thomas, D. T., Erdman, K. A., Burke, L. M. Nutrition and athletic performance. *Med Sci Sports Exerc* 48(3):543–568, 2016. | Pré/pós e “nada novo na prova” |
| `EV-JEUUKENDRUP-2011` | Jeukendrup, A. E. Nutrition for endurance sports. *J Sports Sci* 29(Suppl 1):S91–S99, 2011. | Carboidrato antes/durante em provas longas |

> Estas referências precisam ser conferidas na fonte antes de entrar em
> `SPORTS_SCIENCE.md` §13. Enquanto isso, o texto de UI deve permanecer no nível
> mais genérico possível.

### 6.3 Mudanças de escopo exigidas

| Documento | Mudança |
|---|---|
| `docs/PRODUCT.md` §4 | “Nutrição, emagrecimento e prescrição alimentar” passa a ter a ressalva: “dicas gerais de hidratação e alimentação pré/pós são permitidas, sem individualização” |
| `docs/SPORTS_SCIENCE.md` §1, §8.9, §8.10, §14 | Nova subseção de hidratação/combustível; `R-SAFE-03` delimitado (“sem prescrição nutricional individualizada”); `R-PRESC-04` e §8.11 com a guarda de iniciante |
| `AGENTS.md` §5 e §12 | “Não prescrever nutrição” → “não prescrever nutrição individualizada; dicas gerais permitidas por `ADR 0029`” |
| `docs/COMMUNICATION.md` | Nova subseção com o tom e as proibições das dicas de alimentação |

---

## 7. Modelo de dados e migração

### 7.1 Room — migração `13 → 14` **[PROPOSTA]**

| Tabela | Campo | Tipo | Regra |
|---|---|---|---|
| `weekly_plans` | `profileHash` | `TEXT NULL` | NULL para planos legados = referência desconhecida |
| `weekly_plans` | `profileSnapshotJson` | `TEXT NULL` | Só os campos que mudam treino, para o texto do diff |

- Migração testada com banco v13 e um plano existente (hash nulo, sem revisão falsa).
- Export/reset incluídos; nenhum dado sensível novo (o snapshot não tem nome, foto, peso nem coordenadas).
- `RACE` é valor de enum já serializado como texto: **não** exige migração.

### 7.2 Sem persistência nova no briefing

- `SessionBriefing` é derivado em memória a partir de prescrição + plano + perfil.
- `briefingEngineVersion` constante para testes e auditoria.
- Consequência conhecida: abrir o briefing de um treino **passado** depois de mudar o
  perfil pode refletir o perfil atual. O briefing de hoje e do futuro é o que importa;
  o registro do que foi feito continua sendo a execução.

---

## 8. Arquitetura e fronteiras

| Camada | O que entra |
|---|---|
| `core:model` | Nenhuma dependência nova. `GoalType`/`Experience` já existem. |
| `core:domain` | `ProfileFingerprint`, `T4`, avaliação do briefing (`SessionBriefingEngine`), regra de taper/prova, pós-prova |
| `core:data` | Migração `13→14`, persistência de hash/snapshot, leitura |
| `app` | Caso de uso `SaveProfileAndEvaluate`, UI do sheet, copy, notificação |

- O hash é calculado no domínio (puro) e apenas persistido em `core:data`.
- A UI não calcula regra: só coleta estado e apresenta.
- `PlanReplanner` continua sendo a única autoridade de replanejamento; T4 é só mais
  um gatilho de entrada.
- `DailyPrescriptionEngine` continua a única autoridade do dia.

---

## 9. UX, copy e design

### 9.1 Onde cada coisa aparece

| Superfície | F1 | F2 | F3/F4 |
|---|---|---|---|
| Editar perfil | Ao salvar: resultado da avaliação (sugestão / nada a mudar / limite) | — | — |
| Hoje — card do treino | Card de sugestão com versão e diff | Selo “Prova” e contagem de dias | Botão “Como correr hoje” |
| Hoje — sheet expandido | “Ver o que mudou” | — | Seções do briefing |
| Plano da semana | — | Dia da prova marcado | — |
| Notificação | — | Lembrete da prova | Foco do dia |
| Perfil | Campo de objetivo com data/distância | Mesma tela | — |

### 9.2 Copy de referência (a validar com o usuário 0)

| Situação | Texto proposto |
|---|---|
| Sugestão após mudar nível | **“Seu nível mudou. Ajusto o resto da semana?”** · “Você passou de iniciante para intermediário. Posso reorganizar só os próximos dias. O treino de hoje e o que já passou não mudam.” |
| Nada a mudar | **“Nada a mudar nesta semana.”** · “O resto da semana já cabe no seu novo nível. O ajuste aparece na semana que vem.” |
| Upgrade limitado | **“Seu plano já está no limite seguro.”** · “Subir de nível não aumenta carga sozinho. Na próxima semana o plano já sai no novo nível.” |
| Downgrade aceito | **“Semana ajustada.”** · “Tirei o treino forte dos próximos dias e deixei o resto leve. Hoje não mudou.” |
| Prova muito próxima | **“Faltam 8 dias para a prova.”** · “Não dá para montar uma preparação completa em tão pouco tempo. Posso deixar a semana leve e marcar o dia da prova.” |
| Semana da prova | **“Semana da prova.”** · “Sem treino forte até sábado. Só trote curto para chegar descansado.” |
| Pós-prova | **“Semana de recuperação.”** · “Sem treino forte por N dias. Caminhadas e trote leve ajudam a recuperar.” |
| Rodapé de alimentação | “Dica geral, não é orientação nutricional. Com restrição alimentar ou condição clínica, siga orientação profissional.” |

### 9.3 Design

- Reaproveitar o bottom sheet existente (mesmo componente do “Por que este treino?”),
  agora em versão expandida com seções colapsáveis.
- Tokens do `core:designsystem`; lima só na ação; coral só em fisiologia/alerta.
- Números tabulares nos alvos; nada de hex solto.
- O card do Hoje ganha **um** botão novo (“Como correr hoje”); “Por que este treino?”
  continua como link secundário para não competir com “Iniciar”.

---

## 10. Casos de erro e estados vazios

| Situação | Comportamento |
|---|---|
| Perfil salvo sem plano na semana | Nenhuma sugestão; o próximo plano já nasce com o novo perfil |
| Plano legado com hash desconhecido | Registra a referência, sem sugerir nada na primeira vez |
| Sugestão recusada | Some até nova versão/hash; o botão explícito continua |
| Sugestão obsoleta no aceite | Reavalia e mostra “os dados mudaram enquanto você decidia” |
| Prova no passado | Avisa e não cria taper retroativo; oferece atualizar a data |
| Sem calibração | Briefing usa RPE; nunca inventa pace |
| Doença/lesão no check-in | Briefing não é gerado como treino; mostra descanso e o motivo |
| Treino já concluído | Briefing vira leitura (“como era o plano”), sem CTA de iniciar |

---

## 11. Testes e critérios de aceite

### 11.1 Domínio (`core:domain`, cobertura ≥ 85% mantida perto do atual)

- Fingerprint: determinístico, independente da ordem, muda com cada campo relevante,
  **não** muda com nome/foto/peso/altura/fuso.
- T4: hash diferente ⇒ sugestão; hash igual ⇒ `NoChange`; hash nulo ⇒ registra e não sugere.
- Downgrade remove `QUALITY` futura e corta longão acima do teto do nível.
- Upgrade não adiciona volume nem qualidade (teto preservado).
- Guarda de iniciante: plano com `QUALITY` hoje + perfil virando `BEGINNER` ⇒
  prescrição `DOWNGRADE` com `R-PRESC-04`; plano persistido intacto; sessão
  `DONE`/`CLOSED` do mesmo dia não é tocada; confiança e rationale coerentes.
- Taper por dias até a prova, incluindo prova no meio da semana e prova no domingo.
- `RACE` no dia certo mesmo sem disponibilidade; semana da prova sem `QUALITY`.
- Pós-prova: 5k/10k, meia e maratona com janelas diferentes.
- Briefing: determinístico, soma dos blocos ≈ duração, sem número novo, seções
  presentes por tipo, “se estiver difícil” nunca aumenta, e RPE quando não há calibração.

### 11.2 Dados (`core:data`)

- Migração 13→14 preserva planos e sessões e deixa hashes nulos.
- `applyReplan` continua protegendo hoje/passado/`DONE`/`CLOSED` quando o gatilho é T4.
- Export/reset incluem os campos novos.

### 11.3 App/UI

- Salvar perfil avalia e mostra o desfecho correto (3 cenários).
- Aceitar/recusar/suprimir seguem as regras já existentes do `ADR 0028`.
- Sheet do briefing: seções, acessibilidade, TalkBack, claro/escuro, 48 dp.
- Nenhum `R-`/`EV-` na superfície comum (teste de regressão existente).
- Goldens novos: card de sugestão de perfil, sheet do briefing (dark/light), dia da prova.

### 11.4 Critérios de aceite do ciclo

- [ ] `./gradlew check` e `./gradlew :app:assembleDebug` verdes.
- [ ] `spotlessCheck` e `koverVerify` verdes.
- [ ] Migração 13→14 com teste de banco real.
- [ ] Nenhum dado real no diff.
- [ ] `PRODUCT.md`, `SPORTS_SCIENCE.md`, `COMMUNICATION.md`, `DECISIONS.md`, `ROADMAP.md` e `PROGRESS.md` atualizados.
- [ ] ADRs 0029 e 0030 escritos e aceitos antes das entregas correspondentes.
- [ ] Validação no aparelho com o usuário 0 nos três fluxos.

---

## 12. Riscos e mitigação

| Risco | Impacto | Mitigação |
|---|---|---|
| Dica de alimentação virar prescrição | Alto (escopo e risco clínico) | `R-FUEL-01/02`, rodapé obrigatório, proibição de números, revisão de copy |
| Excesso de sugestões ao editar perfil várias vezes | Fadiga | Uma sugestão por versão/hash; supressão até o fim do dia já existente |
| T4 competir com T1/T2 | Confusão | Precedência única: T4 > T2 > T1; uma sugestão por vez, com causa explícita |
| `RACE` quebrar prescrição, adesão ou matcher | Regressão | Listar todos os `when` sobre `WorkoutType`; testes de regressão por ponto de uso |
| Briefing longo demais | Baixa leitura | Limite de linhas por seção; seções colapsáveis; teste de comprimento na cópia |
| Prometer resultado em prova | Expectativa errada | Faixa de ritmo com confiança limitada e texto “referência, não garantia” |

---

## 13. Entregas e sequência

| Ordem | ID | Entrega | Depende de | Gate |
|---|---|---|---|---|
| 1 | **C3-01** | Fingerprint + T4 + guarda de segurança (domínio e dados) | — | Testes de domínio, guarda de iniciante e migração 13→14 verdes |
| 2 | **C3-02** | Avaliação ao salvar perfil + UI da sugestão com copy clara | C3-01 | 3 desfechos validados com o usuário 0 |
| 3 | **C3-03** | Briefing determinístico (`SessionBriefingEngine`) + catálogo de textos | C3-01 | Testes de determinismo e de conteúdo |
| 4 | **C3-04** | Sheet expandido do briefing + acessibilidade + goldens | C3-03 | `verifyRoborazziDebug` e TalkBack |
| 5 | **C3-05** | Dicas de hidratação/alimentação + `ADR 0029` + docs de escopo | C3-04 | Copy aprovada; `R-FUEL` no catálogo |
| 6 | **C3-06** | Taper por data + `RACE` + semana da prova (2A) | C3-01 | 2A coberto por testes de data |
| 7 | **C3-07** | Pós-prova + ritmo de prova + `ADR 0030` (2B) | C3-06 | Recuperação validada por distância |

**Por que esta ordem:** C3-01/02 resolvem a expectativa quebrada do usuário e são a
base da prova; C3-03/04 entregam o maior ganho percebido com risco baixo e sem
migração; C3-05 entra junto porque é uma seção do briefing; a prova vem depois
porque exige o ADR de periodização e toca mais pontos do motor.

---

## 14. Documentos a atualizar quando aprovado

| Documento | O que muda |
|---|---|
| `docs/PRODUCT.md` | Escopo de nutrição com ressalva; objetivo/prova como capacidade real |
| `docs/SPORTS_SCIENCE.md` | §8.9 hidratação, §8.10 prova, taper e pós-prova; `R-PLAN-09..11`, `R-BRIEF-01..03`, `R-FUEL-01..02`; evidências novas |
| `docs/COMMUNICATION.md` | Tom do briefing, proibições de alimentação, copy de perfil/prova |
| `docs/ARCHITECTURE.md` | `SessionBriefingEngine`, T4, fluxo perfil → sugestão |
| `docs/DATA_MODEL.md` | Schema v14 (`profileHash`, `profileSnapshotJson`) |
| `docs/DECISIONS.md` | ADRs 0029 e 0030; revisão do status do ADR 0026 com a guarda de iniciante |
| `docs/ROADMAP.md` | Ciclo 3 com C3-01..C3-07; N-03 fechado por C3-06/07 |
| `docs/PROGRESS.md` | Diário de cada entrega |
| `AGENTS.md` | §5 e §12 com a redação de nutrição delimitada |

---

## 15. Perguntas abertas

**AD-1 — Guarda de experiência na prescrição do dia. FECHADA em 10/11/2026.**
Opção **A**, escolhida pelo usuário 0: a prescrição do dia rebaixa treino forte para
fácil quando o nível é iniciante, sem aceite (é decisão do dia, não replanejamento).
Registrada como D-6, `FR-109` e `R-PRESC-04`.

**AD-2 — Nome do ciclo.** “Ciclo 3 — acompanhar o atleta” ou outro nome?

**AD-3 — Rodapé de alimentação.** Texto de §9.2 é suficiente ou o usuário 0 quer uma
frase mais curta?

---

## Anexo A — Exemplo de briefing em dia de prova

> **Hoje é dia de prova: 10 km.**
> **Como correr:** 10–15 min de trote leve + mobilidade · prova em 5:55–6:05/km,
> começando 5 s mais devagar no primeiro km · sem arrancada no fim.
> **O que sentir:** o primeiro km deve parecer fácil; se já estiver difícil, você saiu rápido.
> **Se estiver difícil:** mantenha o ritmo e reduza a expectativa, não o esforço.
> **Antes:** nada novo para comer ou beber; use o que você já testou nos treinos.
> **Depois:** caminhe 10 min, beba água e registre a corrida.
> _Dica geral, não é orientação nutricional._
