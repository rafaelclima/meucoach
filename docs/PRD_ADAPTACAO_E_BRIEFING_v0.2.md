# PRD — Plano que acompanha o perfil e briefing do treino

**Versão:** 0.2 (proposta consolidada) · **Data:** 10/11/2026
**Produto:** MeuCoach — coach de corrida Android, local-first e determinístico.
**Ciclo proposto:** Ciclo 3 — “acompanhar o atleta” (C3).
**Status:** escopo v0.2 aprovado em 2026-11-10; ADRs C3-G0 aceitos. C3-01A (guarda de iniciante), C3-01B (fingerprint/T4/migração 13→14) e C3-02 (avaliação ao salvar Perfil/Calibração e card no Perfil) implementados; as demais entregas aguardam seus gates.

---

## 0. Ficha e convenções

### 0.1 O que este PRD cobre

Três temas levantados pelo usuário 0, que compartilham o mesmo alvo — fazer o app
se comportar como um treinador que acompanha o atleta:

1. **Mudar o nível em “Editar perfil” precisa mudar os treinos.**
2. **Cadastrar uma prova (ex.: 10 km, com data) precisa fazer o app planejar em volta dela.**
3. **Os textos do treino precisam ajudar a executar a prescrição**, sem acrescentar estruturas ou alvos que o motor não produziu. Dicas gerais de hidratação e alimentação ficam condicionadas à revisão definida em F4.

### 0.2 Convenções

- **[FATO]** verificado no código ou nos documentos citados.
- **[PROPOSTA]** decisão deste PRD, ainda não implementada.
- **[DECISÃO ABERTA]** precisa de resposta do usuário 0 antes da implementação.
- **[GATE ADR]** exige decisão arquitetural/técnico-esportiva registrada e aceita antes de implementar o requisito dependente.
- Prioridade: **P0** crítico · **P1** alta · **P2** média.
- Esforço: **S** (1–2 dias) · **M** (3–5 dias) · **L** (1–2 semanas), para um desenvolvedor assistido por agente.

### 0.3 Documentos consultados

`AGENTS.md`, `docs/PRODUCT.md`, `docs/ARCHITECTURE.md`, `docs/SPORTS_SCIENCE.md`,
`docs/COMMUNICATION.md`, `docs/DESIGN_SYSTEM.md`, `docs/DATA_MODEL.md`,
`docs/ROADMAP.md`, `docs/PROGRESS.md`, `docs/DECISIONS.md`,
`docs/adr/0023-weekly-plan-vs-daily-prescription.md`,
`docs/adr/0028-versioned-plan-replanning.md`,
`docs/PLANO_CICLO_2_GUIADO_E_REPLANEJAMENTO.md`.

Esta versão também incorpora a auditoria independente `docs/AUDITORIA_TECNICA_PRD_CICLO_3.md` e a conciliação da Etapa 2. O código foi reinspecionado para os contratos citados; nenhum código ou banco foi alterado nesta revisão documental.

---

## 1. Contexto e problema

### 1.1 O que o app já faz **[FATO]**

- O plano semanal é gerado uma vez e persistido em `weekly_plans`; a semana só é
  recriada quando não existe plano salvo (`TodayViewModel`).
- `AthleteProfile.experience` e `TrainingGoal` (tipo, data, distância, tempo) já são
  editáveis e persistidos.
- O formulário oferece objetivos de prova de 5 km, 10 km, meia maratona e maratona; não oferece distância customizada. `targetTimeSec` é salvo como objetivo declarado.
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
| 8 | Não existe entidade de evento de prova nem ciclo de vida de prova | Não há estados de realização/cancelamento/não comparecimento nem vínculo específico do evento com atividade |
| 9 | `PlannedSession` não tem blocos (aquecimento/principal/recuperação/desaquecimento) | Não dá para dizer “como correr” com precisão |
| 10 | Não existe guia pré/durante/pós no app | O usuário só vê o porquê, nunca o como |
| 11 | O `rationale` é reaproveitado na notificação e no card | Texto curto, técnico e igual em toda parte |
| 12 | Nutrição está fora de escopo em `PRODUCT.md` §4 e `SPORTS_SCIENCE.md` §1/`R-SAFE-03` | A intenção de dicas gerais precisa de exceção estreita e revisão de conteúdo/evidência antes de F4 |

### 1.3 Problema em uma frase

> O app já sabe calcular, mas ainda não **reage** ao que o atleta muda no perfil e
> não **ensina** como executar o treino que prescreveu.

---

## 2. Objetivos e não-objetivos

### 2.1 Objetivos

| ID | Objetivo | Como saberemos |
|---|---|---|
| O-1 | Mudança de experiência ou de prova leva a uma sugestão de replanejamento clara, com aceite do usuário | Teste de integração + validação com o usuário 0 |
| O-2 | O evento de prova e a semana de prova respeitam a data local: sem `QUALITY` na semana do evento, sem contar a prova como sessão de qualidade | Contrato de evento aprovado em ADR + testes por data; a política do dia do evento é gate arquitetural |
| O-3 | O briefing é curto, determinístico e fiel à prescrição vigente | Testes de conteúdo, determinismo e ausência de alvos/segmentos inventados |
| O-4 | Manter a intenção de dicas gerais de hidratação/alimentação, sem individualização | F4 só pode ser implementado após revisão documentada das fontes e do conteúdo |
| O-5 | Nada disso aumenta carga sozinho, inventa número ou usa LLM | `./gradlew check` + revisão dos ADRs |

### 2.2 Não-objetivos (explícitos)

- Áudio, TTS e sessão guiada persistente — continuam no `ADR 0027`, adiados por decisão do usuário 0.
- Periodização completa em blocos base/construção/pico (só o necessário para a prova).
- Recuperação pós-prova automatizada (FR-207); não existe política aprovada nem estado de conclusão de evento implementado.
- Ritmo de prova prescrito, previsão de desempenho ou suporte a distância customizada.
- Prescrição nutricional: quantidades individualizadas, calorias, macros, plano alimentar, recomendação universal de sais/eletrólitos, suplementos, emagrecimento.
- Novos gráficos, novas abas, backend, sync, widgets.
- Segmentos estruturados/persistidos de treino; ficam para ciclo futuro.
- Mudar a stack, introduzir LLM ou qualquer decisão de treino fora do motor determinístico.

---

## 3. Decisões consolidadas

### 3.1 Decisões preservadas do PRD v0.1

| ID de origem | Tema | Decisão preservada | Efeito nesta versão |
|---|---|---|---|
| D-2 v0.1 | Replanejamento por perfil | Sempre sugerir e esperar aceite; nunca aplicar revisão automaticamente | T4 segue o fluxo de sugestão do ADR 0028 |
| D-3 v0.1 | Ordem | Perfil primeiro, briefing depois, prova em seguida | Mantida na sequência C3; a investigação/ADR de prova pode ser preparada, mas sua implementação vem depois do briefing v1 |
| D-4 v0.1 | Briefing | Overlay/bottom sheet, sem tela própria | Reutilizar a superfície existente, condicionado a acessibilidade e design |
| D-5 v0.1 | Áudio | Fica para depois | Sem TTS, sessão guiada ou novos segmentos neste ciclo |
| D-6 v0.1 | Segurança de iniciante | A prescrição do dia rebaixa qualidade para fácil sem aceite, sem reescrever plano ou histórico | Especificado como `QUALITY → EASY`; política conservadora de produto, não regra científica nova |

### 3.2 Decisões aprovadas para a revisão v0.2

| ID | Tema | Decisão | Efeito neste PRD |
|---|---|---|---|
| D-1 | Hidratação e alimentação | Manter a intenção de oferecer dicas gerais e não individualizadas; restringir recomendações sobre sais, eletrólitos e ingestão que dependam de contexto individual; revisar fontes e conteúdo antes da implementação de F4 | F4 permanece condicional; não entram dicas universais, “água à vontade” ou recomendações de sais antes do gate científico/editorial |
| D-2 | Representação da prova | Prova é evento esportivo, não `WorkoutType`; estados conceituais `PLANNED`, `COMPLETED`, `CANCELLED`, `NO_SHOW`; associação opcional com atividade | `RaceEvent` separado; `TodayDecision` discriminada treino/evento aprovada em C3-G0, sem sessão `RACE` |
| D-3 | Semana da prova | Não programar `QUALITY` na semana da prova; não criar sessão de ativação 24–48 h antes; cutoff 10/14 dias aprovado como política `LIMITED` | Regra semanal é decisão de produto; fontes apoiam taper geral, não provam cutoff por distância |
| D-4 | Briefing v1 | Briefing genérico, determinístico e fiel à prescrição vigente; só duração, alvos e informações existentes; sem inventar intervalos, ritmos, blocos ou alternativas | Segmentos estruturados ficam para ciclo futuro |
| D-5 | Histórico | Não reconstruir briefing concluído usando perfil atual; mostrar histórico da execução, sem instruções retrospectivas | Briefing gerado somente para prescrição atual de sessão não concluída; concluídas exibem `SessionExecution` |
| D-6 | Metas personalizadas | Não adicionar distâncias customizadas; usar as quatro distâncias que o formulário trata como prova. Tempo-alvo é objetivo declarado, não ritmo prescrito nem previsão | `CUSTOM` sem distância não cria evento de prova; editar tempo não produz alvo fisiológico nem previsão |

---

## 4. Escopo funcional

### 4.1 F1 — Plano que acompanha o perfil

**História:** como atleta, quando eu mudo meu nível ou meu objetivo, quero que o app
me diga **o que muda no meu treino** e me deixe aceitar, em vez de ignorar a mudança.

**Como funciona [PROPOSTA]**

1. Ao salvar perfil ou evento, o domínio calcula `planningFingerprint` versionado e
   canônico. A allowlist inclui experiência, calibração que altera alvos (pace de
   limiar/LTHR), acesso à esteira e, após o ADR 0030, distância/data do evento ativo.
   Disponibilidade e contexto temporal do fuso mantêm hashes separados. Tempo-alvo,
   nome e campos sem efeito no plano ficam fora. Para preservar a ordem D-3, a primeira entrega pode receber
   uma projeção canônica do `TrainingGoal` atual; quando C3-05 mover a fonte para
   `RaceEvent`, a projeção precisa permanecer semanticamente idêntica. Se isso não
   puder ser garantido sem falso T4, a sequência volta para aprovação antes do código.
2. O plano guarda fingerprint/snapshot de proveniência (`generatedFrom`), checkpoint
   (`lastEvaluated`) e `temporalContextHash` separadamente. T4 reavalia quando mudar o
   fingerprint de planejamento ou o fuso que define data/semana local. Isso não afirma
   que um plano antigo foi gerado pelo perfil atual.
3. Mudança relevante avalia replanejamento com T4 `PROFILE_CHANGED`. Se o candidato
   for `NoChange`, ou o upgrade só tiver efeito na próxima semana, o checkpoint
   avança transacionalmente sem criar revisão nem repetir o mesmo aviso.
4. O resultado aparece na tela de perfil e continua disponível no Hoje até aceitar,
   recusar ou suprimir segundo a semântica do `ReplanSuggestionStore`.
5. O aceite valida fingerprint de planejamento e contexto temporal atuais no mesmo
   limite transacional que a versão do plano; divergência retorna `Stale`. Perfil,
   fuso, `today` e semana usada na avaliação devem vir de um snapshot coerente. Só o
   futuro `PLANNED` muda. Hoje, passado e `DONE`/`CLOSED` ficam intactos.
6. Três desfechos possíveis, sempre explicitados:
   - **Sugestão** com diff “o que muda”;
   - **Nada a mudar** — “o resto da semana já cabe no seu novo nível”;
   - **Limite honesto** — quando o motor não pode adicionar carga (subir de nível),
     o texto diz que o ajuste vale a partir da próxima semana, sem prometer o que não faz.

**Regras de segurança [PROPOSTA]**

- O teto do `PlanReplanner` é mantido: nenhuma revisão aumenta volume ou adiciona
  `QUALITY`. `ADVANCED`/`INTERMEDIATE` para `BEGINNER` pode remover qualidade
  futura; longão acima do teto é tratado por FR-108, sem tocar sessão protegida.
- `BEGINNER` → `ADVANCED` normalmente resulta em “nada a mudar nesta semana”, e o
  texto explica isso. Não é falha: é o mesmo princípio conservador do `R-PLAN-01`.
- **Guarda de experiência no dia (D-6 v0.1):** a `DailyPrescriptionEngine` passa a
  rebaixar `QUALITY` para `EASY` quando o nível vigente é `BEGINNER`, mesmo que o
  plano salvo tenha qualidade naquele dia. É `DOWNGRADE`, que já é uma operação
  permitida (`R-PRESC-02`): só reduz, não cria sessão, não muda o plano persistido e
  não pede aceite. Vale só para hoje — os dias seguintes continuam sendo assunto da
  sugestão de replanejamento. A decisão cita `R-PRESC-04`, explica o motivo no
  `rationale` e não se aplica a sessões `DONE`/`CLOSED` (o passado não é reescrito).

### 4.2 F2 — Evento de prova e adaptação semanal

**História:** como atleta, quando cadastro uma prova de distância suportada e data,
quero que o app reconheça o evento e adapte a semana corrente sem tratar a prova
como um treino comum nem prometer uma periodização multissemanal.

#### Modelo de domínio aprovado; persistência/migração como gate técnico

- A prova é um `RaceEvent` separado de `PlannedSession` e não adiciona `RACE` a
  `WorkoutType`.
- Distâncias de evento v1: `FIVE_K`, `TEN_K`, `HALF_MARATHON`, `MARATHON`, que
  correspondem às opções de prova do formulário atual. `CUSTOM` não cria evento.
- Dados conceituais: identificador, distância suportada, data local no fuso do
  perfil, tempo-alvo declarado opcional, estado, vínculo opcional a `activityId` e
  timestamps de auditoria.
- Estados: `PLANNED`, `COMPLETED`, `CANCELLED`, `NO_SHOW`. Um evento planejado não
  muda de estado por inferência apenas porque a data passou.
- Vínculo com atividade é sempre explícito pelo usuário; nunca automático. O evento
  não é contado como sessão `QUALITY` nem como sessão de treino para adesão.
- O usuário aprovou um evento `PLANNED` ativo e a retenção de estados encerrados.
  `COMPLETED` exige confirmação explícita; a associação a uma atividade é opcional e
  nunca inferida automaticamente. Unicidade/FK e comportamento de lixeira/exclusão
  são detalhes técnicos a fechar no desenho da migração.

**Persistência recomendada [DIREÇÃO ACEITA; DETALHES DE MIGRAÇÃO PENDENTES]**

- A direção aprovada é uma entidade/tabela `race_events` em `core:data`, com FK
  opcional para `activities` e exclusão do vínculo sem apagar o evento. O perfil atual
  é singleton e guarda `TrainingGoal` em JSON; guardar estados e histórico de evento
  nesse JSON misturaria perfil e ciclo de vida. A migração deve evitar duas fontes de
  verdade entre `AthleteProfile.goal` e o evento e preservar metas antigas sem inferir
  realização.
- Export/reset devem incluir eventos e remover a tabela/vínculos na ordem correta.
  Não há import/restore de export JSON no projeto atual; não faz parte deste ciclo.

#### Planejamento 2A — regras aprovadas

1. O taper é calculado por `targetDate - session.date` no fuso do perfil, não por
   `weekStart`. Só sessões futuras `PLANNED` podem mudar; hoje, passado e sessões
   futuras `DONE`/`CLOSED` permanecem protegidos pelo ADR 0028.
2. Não há sessões `QUALITY` na semana civil local que contém a prova. Não há sessão
   de ativação 24–48 h antes nesta versão.
3. `SPORTS_SCIENCE.md` §8.6 registra os intervalos atuais para última sessão intensa
   (7–10 dias para 5K/10K; 10–14 dias para meia/maratona), mas as referências
   `EV-BOSQUET-2007`/`EV-MUJIKA-2003` sustentam princípios gerais de taper — não
   demonstram esses cutoffs por distância nem uma proibição de qualidade na semana
   da prova. Para teste determinístico, a regra conservadora aprovada é usar o extremo
   distante do intervalo documentado: nenhuma `QUALITY` nos últimos 10 dias para
   5K/10K e 14 dias para meia/maratona. Classificação aprovada pelo usuário:
   **LIMITED / convenção de produto**, não consenso científico. As fontes revisadas
   não sustentam esses números diretamente; manter essa ressalva no catálogo/copy e
   não apresentá-los como evidência específica.
4. Nas janelas de taper já documentadas (2 semanas para 5K–meia; 3 para maratona),
   volume é reduzido progressivamente segundo a tabela de fatores validada nos testes.
   A redução de duração também se aplica a `QUALITY` que ainda esteja antes do
   cutoff; nenhum intervalo/ativação pode ser criado. Os fatores atuais `0,55` e
   `0,45` podem ser reaproveitados apenas se os testes mostrarem que a semana
   completa respeita o corte de volume; o PRD não os declara uma lei fisiológica.
5. O plano continua sendo semanal/rolling: não prevê nem exibe um calendário de
   preparação para semanas futuras ainda não geradas.
6. Prova passada ou a menos de 10 dias não recebe taper retroativo nem promessa de
   preparação completa. O evento permanece `PLANNED` até ação explícita. A decisão
   diária continua seguindo `TodayDecision`; não aumenta carga nem infere realização.
7. `targetTimeSec` é o objetivo declarado do atleta. Não produz faixa de pace,
   alvo de execução ou previsão. Alterá-lo atualiza a informação do evento, mas não
   dispara replanejamento do treino enquanto não influenciar uma regra de plano.
   Alterar distância suportada ou data pode disparar T4.

#### Autoridade do dia — decisão C3-G0 aprovada

O ADR 0030 aceito adota uma única `TodayDecision` discriminada:
`Training(DailyPrescription)` ou `Competition(RaceEvent)`. A prova não é um
`WorkoutType`; no dia do evento, o ramo `Competition` prevalece, suprime ações da
sessão ordinária sobreposta e a exclui da adesão. Novos planos não criam
`PlannedSession` na data do evento. Migração, persistência e integração dos
consumidores listados no ADR continuam como gates de implementação; não criar uma
segunda instrução paralela no Hoje.

#### Pós-prova

Recuperação pós-prova fica **fora do escopo implementável do Ciclo 3 v0.2**. FR-207
é adiado até haver confirmação explícita de realização, política para atividade
ausente/manual, evidência específica para a janela e decisão sobre interação com
prontidão/semana seguinte. `SPORTS_SCIENCE.md` §8.10 registra a regra 1 dia/3 km
como heurística para 5K/10K; não autoriza estendê-la a meia/maratona como regra
científica. `COMPLETED` não ativa recuperação automaticamente nesta versão.

### 4.3 F3 — Briefing genérico do treino

**História:** como atleta, quero abrir o treino de hoje e entender, em linguagem
simples, o que a prescrição vigente me pede — sem receber um treino diferente do que
foi planejado/prescrito.

**Contrato [PROPOSTA]** — `SessionBriefing` imutável em `core:domain`, derivado da
`DailyPrescription` persistida vigente e da sessão planejada correspondente apenas
para contexto. O resultado não cria uma nova decisão.

| Seção | Conteúdo permitido na v1 |
|---|---|
| Foco | Título/tipo e duração da prescrição vigente |
| Alvo | Pace, FC, zona, distância ou RPE somente quando já constarem na prescrição |
| Por que hoje | Rationale, confiança e motivo já persistidos na prescrição |
| Como correr | Orientação textual genérica compatível com o tipo/alvo existente; sem blocos, repetições ou duração de aquecimento/desaquecimento inventados |
| Se estiver difícil | Orientação conservadora sem valor numérico nem alternativa de treino não avaliada pelo motor; sinais de parada remetem a `R-SAFE-01` |
| Antes/Depois | Só conteúdo geral que já tenha fonte e aprovação em F4; se F4 estiver bloqueado, as seções não são exibidas |

**Regras**

- Determinístico: mesma prescrição persistida e versão do engine ⇒ mesmo briefing.
  Sem LLM, aleatoriedade ou clima.
- Não ler o perfil atual para reinterpretar uma execução passada. Alvos e duração
  são os da prescrição diária vigente.
- Sem calibração, mostrar o RPE que já estiver na prescrição; não calcular pace.
- Se a sessão estiver `DONE`, não gerar briefing. Hoje/Histórico mostra somente os
  dados de `SessionExecution` e, quando houver, a atividade vinculada.
- O briefing comum não mostra `R-`/`EV-`; as fontes continuam disponíveis sob
  demanda conforme `COMMUNICATION.md` §6.
- `briefingEngineVersion` identifica a versão em runtime/testes; não é promessa de
  reconstrução histórica, pois o texto não é persistido.
- Segmentos estruturados (aquecimento, intervalos, recuperação e desaquecimento)
  exigem um modelo canônico futuro compartilhado por plano, prescrição, briefing e
  execução; não são derivados de título ou rationale nesta versão.

**Exemplo fiel (qualquer número depende do dado da prescrição)**

> **Hoje: Fácil e conversável · 30 min.**
> **Alvo:** RPE 4/10. Não há pace calibrado para mostrar.
> **Como correr:** mantenha esforço fácil e conversável durante a duração prescrita.
> **Se estiver difícil:** reduza o esforço; interrompa diante de um sinal de alerta.

Este exemplo é ilustrativo, não um default de prescrição. O engine só pode exibir
valores presentes na `DailyPrescription`.

### 4.4 F4 — Dicas gerais de hidratação e alimentação (condicional)

**Intenção aprovada:** oferecer conteúdo educativo geral, não individualizado. A
intenção não autoriza recomendações de ingestão que dependam de suor, clima,
duração, condição clínica, tolerância ou acesso a água.

**Permitido somente após revisão de conteúdo e fontes:** frases educativas neutras
que não prescrevam quantidade, horário, produto, eletrólito/sal ou refeição universal.
Qualquer orientação específica sobre sais/eletrólitos ou ingestão individualizada
fica fora do escopo desta versão.

**Proibido:** “água à vontade”; recomendação universal de eletrólitos/sais; dose,
volume, gramas, calorias, macros ou horário rígido; suplemento; jejum;
emagrecimento; substituição de refeição; conselho dependente de diagnóstico.

**Gate de implementação:** `ADR 0029`, revisão das referências na fonte original,
copy aprovada e atualização coordenada de `PRODUCT.md`, `SPORTS_SCIENCE.md`,
`COMMUNICATION.md` e `AGENTS.md`. Até esse gate, F4 não é código implementável e
nenhuma dica de alimentação/hidratação aparece no briefing.
**Gate cumprido em 10/11/2026:** `ADR 0029` aceito, fontes revisadas
(ACSM 2007, NATA 2017, Thomas 2016) e copy aprovada em `COMMUNICATION.md` §6.10.
F4 segue condicional à implementação em ciclo futuro; o ramo de prova continua sem
dicas (FR-404).

---

## 5. Requisitos funcionais

### F1 — Perfil

| ID | Requisito | Prioridade | Esforço |
|---|---|---|---|
| FR-101 | Definir `planningFingerprint` versionado/canônico para os campos que efetivamente alteram a semana/alvos; disponibilidade e contexto temporal (`zoneId`) permanecem em hashes separados | P0 | M |
| FR-102 | Guardar separadamente a proveniência (`generatedFromFingerprint` + snapshot) e o último fingerprint avaliado; `NoChange` atualiza somente o checkpoint avaliado | P0 | M |
| FR-103 | Baseline legado nulo nunca é inferido como alteração; capturá-lo sem alegar que o plano foi gerado com esse perfil | P0 | S |
| FR-104 | T4 reavalia mudança do fingerprint de planejamento ou do `temporalContextHash`; inclui experiência, calibração relevante, esteira e campos de evento usados pelo plano; tempo-alvo não altera treino nesta versão | P0 | M |
| FR-105 | Avaliar após todas as rotas de escrita relevantes do perfil, incluindo edição/onboarding e calibração; persistir resultado para o Hoje | P0 | M |
| FR-106 | Reutilizar `ReplanSuggestionStore`; aceite explícito e semântica `Agora não` / `Não sugerir hoje` preservados | P0 | S |
| FR-107 | Diff identifica campos/causas que mudam, inclusive causas concorrentes T1/T2/T4 sem afirmar causa única indevida | P1 | M |
| FR-108 | Downgrade pode remover `QUALITY` futura; corte de longão segue teto já aprovado para o nível, sem alterar sessão protegida | P0 | M |
| FR-109 | Upgrade não aumenta carga nesta semana; `NoChange` registra checkpoint sem declarar que o plano foi gerado pelo novo perfil | P0 | S |
| FR-110 | Aceite verifica fingerprint atual dentro da mesma transação Room que valida versão e grava a revisão; divergência retorna `Stale` | P0 | M |
| FR-111 | Guarda `BEGINNER` transforma apenas sessão de hoje `PLANNED/QUALITY → EASY`; para `DONE/CLOSED`, não aplicar a guarda nem recalcular retrospectivamente a prescrição; preservar prescrição/execução persistidas | P0 | M |
| FR-112 | Salvar perfil/fuso e avaliar T4 usam o mesmo snapshot de `profile`, `zoneId`, `today` e `weekStart`; não depender de atualização assíncrona do `ProfileTimeProvider` entre gravação e avaliação | P0 | M |

### F2 — Prova

| ID | Requisito | Prioridade | Esforço |
|---|---|---|---|
| FR-201 | Especificar `RaceEvent` conceitual em vez de `WorkoutType`; estados `PLANNED/COMPLETED/CANCELLED/NO_SHOW`, associação opcional explícita a `Activity` | P0 / gate de implementação | L |
| FR-202 | Evento é independente de disponibilidade e não é trocável/contado como sessão `QUALITY`; evento no dia não cria `PlannedSession` `RACE` | P0 / gate de implementação | L |
| FR-203 | Não gerar `QUALITY` na semana local que contém o evento; não criar ativação 24–48 h antes | P0 | M |
| FR-204 | Taper e cutoff de qualidade são calculados por data de cada sessão; respeitam sessões protegidas e usam janela `LIMITED` aprovada no `ADR 0030` | P0 / gate de implementação | L |
| FR-205 | Meta passada ou a menos de 10 dias não produz taper retroativo nem promessa de preparação completa; evento não muda de estado por inferência | P0 | M |
| FR-206 | Limitar o longão do pico por fração da distância da prova | **Removido do ciclo** até existir regra e justificativa esportiva aprovadas | — |
| FR-207 | Recuperação pós-prova e janelas por distância | **Fora do escopo implementável** até confirmação de realização e política aprovada | — |
| FR-208 | Limitar eventos v1 às quatro distâncias do formulário; mudança de data/distância invalida T4; mudança de tempo-alvo só atualiza o objetivo declarado, sem pace/previsão | P0 | M |
| FR-209 | Um `TodayDecision` retorna exatamente um ramo: prescrição de treino ou evento de prova; evento no dia prevalece sobre sessão ordinária, não cria `WorkoutType` e não conta adesão | P0 / contrato aceito; implementação gate | L |
| FR-210 | Exibir data/distância/estado do evento e permitir transição explícita e vínculo opcional a atividade, segundo ADR 0030 | P0 / gate de implementação | M |

### F3 — Briefing

| ID | Requisito | Prioridade | Esforço |
|---|---|---|---|
| FR-301 | `SessionBriefing` imutável em `core:domain`, derivado da `DailyPrescription` persistida vigente, com confiança e IDs internos de regra/evidência | P0 | M |
| FR-302 | Foco, duração, alvos e rationale vêm somente da prescrição vigente; não recomputar decisão na UI | P0 | M |
| FR-303 | Não gerar blocos/intervalos/ritmos/alternativas que não existam na prescrição; segmentos estruturados ficam para ciclo futuro | P0 | S |
| FR-304 | Cada número exibido deve ser idêntico ao valor persistido na prescrição; sem soma/arredondamento de blocos na v1 | P0 | S |
| FR-305 | Sinais de parada usam `R-SAFE-01` e copy já aprovada; sem triagem/diagnóstico novo | P0 | S |
| FR-306 | “Se estiver difícil” não prescreve mudança numérica nem um treino alternativo; só orientação conservadora compatível com `R-PRESC-02` | P0 | S |
| FR-307 | Sheet acessível pelo CTA do Hoje; notificação abre Hoje, conforme navegação existente, e mostra foco derivado da prescrição vigente no momento do agendamento | P0 | M |
| FR-308 | Acessibilidade: TalkBack na ordem das seções, alvos de 48 dp, contraste AA, tema claro e escuro | P0 | M |
| FR-309 | Nenhum `R-`/`EV-` na superfície comum; fontes sob demanda | P0 | S |
| FR-310 | Sessão `DONE` não recebe briefing retrospectivo; Hoje/Histórico apresenta `SessionExecution` e atividade vinculada se houver | P0 | M |
| FR-311 | Briefing é atualizado apenas quando a prescrição vigente mudar; conteúdo de execução concluída não é reinterpretado com perfil atual | P0 | M |

### F4 — Hidratação e alimentação

| ID | Requisito | Prioridade | Esforço |
|---|---|---|---|
| FR-401 | F4 só pode ser implementado após revisão das fontes e copy por gate documentado/ADR 0029 | P1 / GATE | — |
| FR-402 | Conteúdo aprovado não individualiza ingestão nem sugere universalmente sais/eletrólitos, quantidade, produto ou horário | P1 / GATE | — |
| FR-403 | Não exibir `ml`, `g`, kcal, macros, dose ou qualquer cálculo por peso na UI de dicas | P1 / GATE | — |
| FR-404 | Não exibir dicas de prova até evento e copy de prova estarem aprovados; F4 não é dependência do briefing v1 | P1 / GATE | — |

---

## 6. Regras, evidências e confiança

### 6.1 Regras novas propostas

| Regra | Texto | Evidência | Confiança |
|---|---|---|---|
| `R-PLAN-09` | Mudança de inputs de plano gera revisão sugerida do futuro, nunca automática | ADR 0028 + ADR 0031 aceito | Regra de produto; não é regra fisiológica |
| `R-PLAN-10` | O evento de prova tem ciclo de vida separado de uma sessão de treino; não conta como `QUALITY` | Decisão D-2 v0.2; ADR 0030 aceito | Regra de produto; implementação requer migração/testes |
| `R-PLAN-11` | Semana local da prova não contém `QUALITY`; taper atua por data de sessão | D-3 v0.2; Bosquet 2007 / Wang et al. 2023 sustentam taper geral, não a proibição de `QUALITY` na semana nem os cutoffs por distância | `LIMITED` para o cutoff proposto; restrição semanal é política de produto |
| `R-PRESC-04` | Se iniciante e sessão de hoje `QUALITY`, prescrição pode fazer `DOWNGRADE` para `EASY`; não altera histórico | ADR 0023; D-6 v0.1 aprovado | Política conservadora de produto; evidência fisiológica específica não alegada |
| `R-BRIEF-01` | Briefing deriva da prescrição persistida e não decide nem inventa alvo | ADR 0023; D-4/D-5 v0.2 | `STRONG` como contrato arquitetural/produto |
| `R-BRIEF-02` | Briefing v1 só descreve duração/alvos existentes; não cria segmentos | ADR 0023; ADR 0027 §3.3; D-4 v0.2 | `STRONG` como restrição de proveniência, não alegação fisiológica |
| `R-BRIEF-03` | Sinais de parada usam `R-SAFE-01`; não criam diagnóstico/triagem | `R-SAFE-01` | `STRONG` conforme catálogo vigente |
| `R-FUEL-01` | Dicas de hidratação são conteúdo educativo fixo aprovado por fonte; não derivam de prescrição nem alteram decisão do motor | ADR 0029; EV-ACSM-2007; EV-NATA-2017 | `STRONG` como política de produto/copy |
| `R-FUEL-02` | Sem recomendação de quantidade, eletrólito, suplemento ou horário sem contexto individual aprovado | ADR 0029; EV-ACSM-2007; EV-NATA-2017 (nº 1, 5, 6 — SOR A) | `STRONG` |

### 6.2 Evidências novas propostas (a validar antes de entrar no catálogo)

| ID | Referência | Uso |
|---|---|---|
| `EV-WANG-2023` [proposta] | Wang et al. *Effects of tapering on performance in endurance athletes: a systematic review and meta-analysis*. PLoS One 18(5):e0282838, 2023. [PMC](https://pmc.ncbi.nlm.nih.gov/articles/PMC10171681/) | Taper geral: redução de volume e manutenção de intensidade/frequência, janelas estudadas até 21 dias; não define última sessão forte por distância nem justifica proibição de qualidade na semana da prova |
| `EV-ACSM-2007` [aceita] | Sawka, M. N. et al. ACSM position stand: exercise and fluid replacement. *Med Sci Sports Exerc* 39(2):377–390, 2007. | Individualização da hidratação; risco de hiperhidratação (ADR 0029) |
| `EV-THOMAS-2016` [aceita] | Thomas, D. T., Erdman, K. A., Burke, L. M. Nutrition and athletic performance. *Med Sci Sports Exerc* 48(3):543–568, 2016. | Sustenta apenas a recusa de prescrição alimentar; nenhuma dica deriva dela (ADR 0029) |
| `EV-NATA-2017` [aceita] | McDermott, B. P. et al. NATA position statement: Fluid Replacement for the Physically Active. *J Athl Train* 52(9):877–895, 2017. [PDF](https://www.nata.org/sites/default/files/2025-08/fluid_replacement_for_the_physically_active.pdf) | Individualização (SOR A); não ganhar massa durante o exercício; hidratação antes/depois. Corrige a citação `EV-NATA-2025`, que apontava para o statement de 2000 (Casa et al.) |

> Revisão de F4 concluída em 10/11/2026 (ADR 0029, gate C3-G2): fontes revisadas e
> aceitas, copy aprovada em `COMMUNICATION.md` §6.10; a implementação de F4 segue
> para ciclo futuro. As fontes de taper continuam sustentando princípios gerais,
> não os cutoffs exatos de última `QUALITY` propostos nesta versão.

### 6.3 Mudanças de escopo exigidas

| Documento | Mudança |
|---|---|
| `docs/PRODUCT.md` §4 | Só alterar o limite de nutrição após o gate de F4; nenhuma permissão ampla entra agora |
| `docs/SPORTS_SCIENCE.md` §8.6/§8.8/§8.10/§14 | Separar taper geral, cutoff de última `QUALITY` como política limitada, evento de prova e pós-prova adiado; só inserir regras/evidências aprovadas |
| `AGENTS.md` §5 e §12 | Manter a proibição atual até ADR 0029 e revisão de F4; não registrar permissão genérica antecipadamente |
| `docs/COMMUNICATION.md` | Briefing v1 fiel à prescrição; F4 só recebe copy após revisão |

---

## 7. Modelo de dados e migração

### 7.1 Fingerprint e proveniência do plano [PROPOSTA]

O banco atual é Room v13. A migração incrementa a versão disponível após a decisão
do ADR e deve ser compatibilizada com qualquer outra migração aprovada; este PRD não
assume que ADR 0027 (sessão guiada) já foi implementado.

| Tabela | Campo conceitual | Regra |
|---|---|---|
| `weekly_plans` | `generatedFromFingerprintVersion` + `generatedFromFingerprint` | Proveniência da versão vigente; não avança em `NoChange` |
| `weekly_plans` | `generatedFromProfileSnapshotJson` | Allowlist mínima que explica qual perfil/evento gerou a versão |
| `weekly_plans` | `lastEvaluatedFingerprintVersion` + `lastEvaluatedFingerprint` | Checkpoint de avaliação para não repetir `NoChange`; pode avançar sem revisão |
| `weekly_plans` | `temporalContextHash` | Hash separado do `zoneId` usado para calcular data/semana local; muda stale/T4 sem misturar fuso à identidade do atleta |
| `weekly_plan_revisions` | snapshots/fingerprints anteriores e novos | Append-only; registra a transição aceita, incluindo causas e snapshot mínimo |

**Contrato do fingerprint:** namespace/version explícito; canonicalização estável,
ordem de campos fixa, unidades SI e `null` distinto de zero. Inclui somente campos
que alteram plano/alvos (experiência, pace de limiar/LTHR quando usados, acesso à
esteira e evento ativo suportado: tipo/distância/data). Disponibilidade mantém hash
separado. O `zoneId` IANA canônico fica em `temporalContextHash`, porque ADR 0022 usa
o fuso para determinar `today`/semana; uma mudança invalida a avaliação temporal e
requer T4/reavaliação, mas não altera a identidade do perfil. Nome, avatar, peso,
altura, cidade e campos apenas de apresentação ficam fora até haver regra que os
use. `targetTimeSec` não altera o plano na v1 e não dispara T4.

- Migração de v13 preserva planos, sessões e revisões; campos novos ficam `NULL`
  para legado, sem revisão falsa.
- Primeiro encontro com fingerprint legado desconhecido captura somente o
  checkpoint avaliado; não declara a proveniência histórica.
- `NoChange` e “efeito só na semana seguinte” atualizam checkpoint de modo
  transacional sem afirmar que o plano vigente foi gerado pelo novo perfil.
- `saveWeek`, aceite T4, snapshots append-only, `PlanJson`/export e reset devem
  preservar os campos. Import/restore de export não existe e fica fora do escopo.
- O snapshot usa allowlist e não inclui nome, foto, peso, coordenadas ou outros
  campos pessoais sem efeito no plano.

### 7.2 Evento de prova [DIREÇÃO ADR 0030 ACEITA; GATE DE IMPLEMENTAÇÃO]

Usar um `RaceEvent` puro em `core:model`/`core:domain` e uma tabela Room
`race_events` em `core:data`, não um novo `WorkoutType`. A tabela deve preservar o
evento após cancelamento/não comparecimento e export/reset devem incluí-lo. A
migração de `TrainingGoal` existente para evento precisa preservar todos os valores
e nunca inferir `COMPLETED` a partir de data passada. O desenho de implementação deve
fechar:

- como migrar os campos de prova de `AthleteProfile.goal` para evitar duas fontes de
  verdade, preservando metas antigas sem inferir realização;
- cardinalidade/concorrência do vínculo opcional com `Activity`, inclusive lixeira,
  exclusão permanente e atividade já usada por uma sessão;
- FK e limpeza do vínculo quando uma atividade for apagada; export/reset incluem o evento.

Como D-3 pede Perfil antes da entrega de prova, a implementação usa uma
projeção canônica `PlanningGoal` (distância/data, sem tempo-alvo) que possa ser lida
primeiro do `TrainingGoal` atual e depois de `RaceEvent`. A troca da fonte não pode
alterar silenciosamente o fingerprint nem produzir T4 falso. Se for impossível
preservar a semântica e a proveniência, pausar a sequência e solicitar nova aprovação.

Não armazenar a prova em `planned_sessions` nem em `session_executions` como sessão
de treino. `TodayDecision` seleciona o evento como ramo diário separado.

### 7.3 Briefing e histórico

- `SessionBriefing` é derivado em memória da `DailyPrescription` vigente; não é
  persistido nesta versão.
- A versão do engine serve a determinismo/testes, não reconstrói texto histórico.
- Para `DONE`, a UI mostra execução/atividade já registrada, sem gerar briefing
  retrospectivo com perfil atual.

---

## 8. Arquitetura e fronteiras

| Camada | O que entra |
|---|---|
| `core:model` | Tipos existentes de perfil e `RaceEvent`/distância/status como modelo puro |
| `core:domain` | `PlanningFingerprint`, T4, taper/cutoff por data, `SessionBriefingEngine` alimentado pela prescrição |
| `core:data` | Próxima migração após desenho técnico; metadados/snapshots de fingerprint e `race_events`, vínculo, export/reset |
| `app` | Orquestração de todas as escritas de perfil, compare-and-apply, UI de evento, sheet e notificação baseada na prescrição |

- O fingerprint é calculado no domínio (puro); proveniência (`generatedFrom`) e checkpoint (`lastEvaluated`) têm semânticas distintas.
- A UI não calcula regra: só coleta estado e apresenta.
- `PlanReplanner` continua sendo a única autoridade de replanejamento; T4 é só mais
  um gatilho de entrada.
- `TodayDecision` é a única autoridade exposta pelas superfícies do Hoje: ramo de
  treino consome `DailyPrescription`; ramo de competição consome `RaceEvent` e não
  apresenta uma segunda prescrição de treino.
- A guarda de iniciante precisa conhecer `SessionStatus`: `GetTodayPrescription` hoje
  passa `DONE` como intenção e `DailyPrescriptionInput` não contém status. O ADR/
  contrato deve definir um gate status-aware na orquestração (ou contexto explícito
  equivalente); a UI não pode filtrar a regra nem recalcular histórico.
- A implementação de `RaceEvent`/`TodayDecision` segue o ADR 0030 aceito; não
  introduzir `RACE` em `WorkoutType` como atalho.
- O aceite lê o fingerprint atual dentro da transação Room que valida versão e grava
  a revisão, não apenas o hash calculado anteriormente no app.

---

## 9. UX, copy e design

### 9.1 Onde cada coisa aparece

| Superfície | F1 | F2 | F3/F4 |
|---|---|---|---|
| Editar perfil | Ao salvar: resultado da avaliação (sugestão / nada a mudar / limite) | — | — |
| Hoje — card do treino | Card de sugestão com versão e diff | `TodayDecision` mostra evento em vez de sessão sobreposta | Botão “Como correr hoje” para sessão não concluída |
| Hoje — sheet expandido | “Ver o que mudou” | Detalhes do evento, sem tratá-lo como sessão | Briefing da prescrição vigente |
| Plano da semana | — | Marca visual por data; evento não entra como sessão/qualidade | — |
| Notificação | — | Texto relativo à data; destino Hoje | Foco derivado da prescrição no agendamento |
| Perfil | Campos relevantes de perfil | Quatro distâncias suportadas e data | — |

### 9.2 Copy de referência (a validar com o usuário 0)

| Situação | Texto proposto |
|---|---|
| Sugestão após mudar nível | **“Seu nível mudou. Ajusto o resto da semana?”** · “Você passou de iniciante para intermediário. Posso reorganizar só os próximos dias. O treino de hoje e o que já passou não mudam.” |
| Nada a mudar | **“Nada a mudar nesta semana.”** · “O resto da semana já cabe no seu novo nível. O ajuste aparece na semana que vem.” |
| Upgrade limitado | **“Seu plano já está no limite seguro.”** · “Subir de nível não aumenta carga sozinho. Na próxima semana o plano já sai no novo nível.” |
| Downgrade aceito | **“Semana ajustada.”** · “Tirei o treino forte dos próximos dias e deixei o resto leve. Hoje não mudou.” |
| Prova muito próxima | **“Faltam {n} dias para a prova.”** · “O evento continua cadastrado, mas não há tempo para montar uma preparação completa. Não vamos criar taper retroativo.” |
| Semana da prova | Copy usa dia/data reais, sem “até sábado”; informa que não há `QUALITY` naquela semana, sem promessa fisiológica individual |
| Prova muito próxima/passada | Explica ausência de taper retroativo/preparação completa; não presume que o evento foi realizado |
| Pós-prova | Sem copy de recuperação automática nesta versão |
| F4 | Copy final só após revisão de fontes e aprovação documental |

### 9.3 Design

- Reaproveitar o bottom sheet existente do “Por que este treino?” em versão expandida;
  o conteúdo é a prescrição atual e não inclui blocos executáveis.
- Tokens do `core:designsystem`; lima só na ação; coral só em fisiologia/alerta.
- Números tabulares nos alvos; nada de hex solto.
- O card ganha um CTA “Como correr hoje” para sessão não concluída; “Por que este
  treino?” continua secundário. Sessão concluída não exibe esse CTA.

---

## 10. Casos de erro e estados vazios

| Situação | Comportamento |
|---|---|
| Perfil salvo sem plano na semana | Nenhuma sugestão; próximo plano nasce dos inputs atuais |
| Plano legado com fingerprint desconhecido | Captura checkpoint sem sugestão nem alegação de proveniência histórica |
| `NoChange` após edição real | Avança checkpoint avaliado, não `generatedFrom`; não repete o resultado no resume |
| Sugestão recusada | Semântica do ADR 0028; nova versão/inputs pode reaparecer |
| Aceite obsoleto por perfil/inputs/versão | Transação retorna `Stale`, reavalia e não aplica candidato antigo |
| Calibração alterada | Essa rota também avalia fingerprint e só sugere se houver efeito no plano/alvos |
| Prova no passado | Não cria taper retroativo nem assume realização; requer ação explícita sobre o evento |
| Prova a menos de 10 dias | Não promete preparação completa nem aumenta carga; estado do evento segue ADR 0030 |
| Sem calibração | Briefing mostra apenas RPE existente; nunca calcula pace |
| Doença/lesão | Prescrição segue `REST`; briefing descreve descanso e rationale existentes |
| Sessão `DONE` | Sem briefing retrospectivo; exibir `SessionExecution`/atividade existente |
| Evento cancelado/não comparecimento | Não ativa recuperação nem altera plano automaticamente |
| Atividade vinculada vai para lixeira/é apagada | Evento permanece; vínculo segue política de retenção aprovada no ADR |

---

## 11. Testes e critérios de aceite

### 11.1 Domínio (`core:domain`, cobertura ≥ 85% mantida perto do atual)

- Fingerprint/contexto temporal: versões e canonicalização estáveis; ordem irrelevante;
  cada campo incluído altera seu hash; nome/avatar/peso/altura não alteram; `null`
  difere de zero; mudar fuso altera apenas o contexto temporal.
- Proveniência: `generatedFrom` não avança em `NoChange`; `lastEvaluated` avança uma
  vez; edição repetida não sugere no resume; legado nulo não cria revisão falsa.
- T4: hash alterado sugere; causas T1/T2/T4 simultâneas permanecem explicáveis;
  calibração é testada por fluxo de edição e por fluxo de calibração.
- Fuso: mudança de `zoneId` recalcula `today`/`weekStart` a partir de um snapshot
  consistente e invalida uma proposta temporalmente obsoleta, sem criar plano em
  data calculada no fuso antigo.
- Concorrência: perfil alterado entre proposta e aceite retorna `Stale`; nenhum plano
  candidato antigo é aplicado.
- Downgrade remove somente `QUALITY` futura elegível e trata longão conforme FR-108;
  hoje/passado/`DONE`/`CLOSED` continuam idênticos.
- Upgrade não adiciona volume nem qualidade; `NoChange`/limite não falsifica
  proveniência.
- D-6: `QUALITY` de hoje + perfil `BEGINNER` ⇒ `DOWNGRADE` `QUALITY → EASY`, sem
  alterar plano; `DONE/CLOSED` não recebe a guarda nem uma prescrição retrospectiva
  recalculada pelo novo perfil; execução e prescrição persistidas permanecem intactas.
- Taper: para cada sessão, ação calculada por `targetDate - session.date`; usar a
  tabela aprovada em ADR; provar semana parcial, prova em cada dia, virada
  domingo/segunda, fuso de perfil, disponibilidade e sessões protegidas.
- Evento: transições explícitas entre quatro estados; `TodayDecision` escolhe apenas
  `Competition` no dia do evento, suprime sessão ordinária sobreposta, exclui a
  sobreposição da adesão e não dispara recuperação nesta versão.
- Evento/UI: estados, edição/cancelamento, ação explícita de conclusão/não
  comparecimento, vínculo opcional com atividade e data local renderizados sem erro.
- Briefing: só lê prescrição persistida; números iguais aos persistidos; nenhum bloco,
  intervalo, pace ou alternativa inventados; determinismo e ramo sem calibração.
- Sessão `DONE`: briefing indisponível; apresentação é somente a execução registrada.

### 11.2 Dados (`core:data`)

- Migração a partir de v13 preserva planos, sessões e revisões e inicializa fingerprints
  legados como desconhecidos; versão final da migração depende da integração de ADRs.
- `saveWeek`, captura de baseline, T4, `NoChange`, aceite e snapshots preservam cada
  campo com semântica correta.
- `applyReplan` compara fingerprint atual na transação e continua protegendo
  hoje/passado/`DONE`/`CLOSED`.
- Export/reset incluem fingerprints, snapshots e evento de prova;
  não testar restore/import como capacidade existente.
- Migração preserva `TrainingGoal` e não infere eventos concluídos; FK/vínculo e
  comportamento de lixeira/exclusão do evento são testados.

### 11.3 App/UI

- Salvar perfil e salvar calibração avaliam o desfecho correto (sugestão, `NoChange`,
  limite/efeito futuro); recusa/supressão seguem ADR 0028.
- Sheet: loading/empty/error/stale, prescrição downgrades, tema claro/escuro, tela
  estreita, fonte 200%, 48 dp e semântica/ordem de TalkBack.
- Notificação usa prescrição vigente no momento da geração; toque abre Hoje e revalida
  antes de apresentar briefing/ação.
- Sessão concluída mostra execução e não renderiza briefing calculado com perfil atual.
- Fluxo de evento, estado terminal, associação de atividade e copy dinâmica são
  implementados conforme ADR 0030 e verificados no Hoje/Plano/notificação.
- Nenhum `R-`/`EV-` na superfície comum (teste de regressão existente).
- Goldens: card de sugestão de perfil, sheet do briefing (dark/light) e `TodayDecision`
  de evento/dia da prova conforme ADR 0030.

### 11.4 Validação manual de acessibilidade

O CI não tem `androidTest`/TalkBack instrumentado. Antes da entrega de UI, registrar
checklist reproduzível com aparelho/versão Android, escala de fonte 200%, claro/escuro,
TalkBack na ordem das seções, alvos de 48 dp e resultado observado. A suíte Compose/
Robolectric cobre estados e semântica; não substitui essa verificação manual.

### 11.5 Critérios de aceite do ciclo

- [ ] `./gradlew check` e `./gradlew :app:assembleDebug` verdes.
- [ ] `spotlessCheck` e `koverVerify` verdes.
- [ ] Migração sequencial a partir de schema 13 aprovada e testada por `MigrationTestHelper`;
  versão final compatibilizada com ADRs aceitos.
- [ ] Nenhum dado real no diff.
- [ ] Documentos alterados somente para regras/escopo efetivamente aprovados; `AGENTS.md`
      não libera nutrição antes do gate F4.
- [x] Direções ADR 0030/0031 aprovadas em 10/11/2026; detalhes de migração e gates
      técnicos seguem obrigatórios antes do código. ADR 0029/revisão de conteúdo só
      antes de qualquer implementação de F4.
- [ ] Validação no aparelho dos fluxos de perfil e briefing; fluxo de prova após
      concluir os gates de implementação registrados nos ADRs aceitos.

---

## 12. Riscos e mitigação

| Risco | Impacto | Mitigação |
|---|---|---|
| F4 ser interpretado como prescrição individual | Alto (escopo e segurança) | Sem conteúdo em UI até revisão de fonte/copy; sem recomendação universal de eletrólitos, quantidade ou ingestão |
| Fingerprint incompleto ou falso stale | Plano errado ou repetição de sugestão | Allowlist versionada, checkpoint distinto da proveniência e testes de canonicalização |
| Aceite concorrer com edição de perfil | Plano baseado em perfil antigo | Compare-and-apply do fingerprint dentro da transação do mesmo Room |
| Evento duplicar ou contradizer sessão/prescrição | Duas decisões para o mesmo dia | Evento separado; ADR bloqueia código até definir a autoridade do dia e a supressão de sessão |
| Taper aplicar-se por semana errada ou mexer em sessão protegida | Qualidade próxima da prova ou histórico reescrito | Cálculo por data local de cada sessão; testar todos os dias; preservar hoje/passado/DONE/CLOSED |
| Cutoff da última QUALITY parecer consenso | Atribuição científica indevida | Rotular o cutoff como heurística limitada de produto e validar no ADR antes do catálogo/código |
| Briefing reinterpretar sessão concluída | História falsa após edição de perfil | Não gerar briefing em DONE; mostrar somente execução/atividade persistida |
| Notificação stale | Texto diverge da prescrição atual | Gerar de DailyPrescription no agendamento; ao tocar abre Hoje e revalida |

---

## 13. Entregas e sequência C3 v0.2

As estimativas são preliminares. Reavaliar esforço somente depois dos ADRs e da
decomposição dos caminhos de persistência; não assumir que cada linha cabe em S/M.

| Ordem | ID | Entrega | Depende de | Gate |
|---|---|---|---|---|
| 0 | **C3-G0** | Design documental do fingerprint/proveniência, contexto temporal, compare-and-apply e projeção canônica de objetivo (`TrainingGoal` → futuro `RaceEvent`) | Nenhum código | Direções ADR 0030/0031 aceitas; provar que migração de fonte/fuso não cria T4 falso |
| 1 | **C3-01A** | Guarda diária de iniciante: somente `QUALITY → EASY`; proteger `DONE/CLOSED` e execução | D-6 v0.1; contrato status-aware | Testes de domínio/integração: plano intacto, prescrição/histórico intactos e rationale `R-PRESC-04` |
| 2 | **C3-01B** | Fingerprint versionado, proveniência/checkpoint, T4, migração e compare-and-apply atômico | C3-G0; C3-01A pode ser independente | MigrationHelper v13; `saveWeek`/`NoChange`/aceite/export/reset; corrida perfil×aceite retorna `Stale` |
| 3 | **C3-02** | Avaliar ao salvar Perfil e Calibração; mostrar sugestão/`NoChange`/limite no Perfil e Hoje | C3-01B | Todas as rotas de escrita cobertas; alterações repetidas/reabertura do processo; usuário 0 valida copy |
| 4 | **C3-03** | `SessionBriefingEngine` genérico, só a partir da prescrição vigente | C3-01A; nenhum `RaceEvent` necessário | Testes de fidelidade dos campos, determinismo, alvos nulos/RPE, downgrade e ausência de segmentos |
| 5 | **C3-04** | Sheet de briefing, estado histórico e notificação baseada na prescrição | C3-03 | Compose/Robolectric, goldens, stale, execução concluída sem briefing; checklist manual de TalkBack/fonte 200% |
| 6 | **C3-G1** | Detalhar contrato Room/migração, `TodayDecision`, vínculo com atividade e testes de compatibilidade do evento | C3-04 e ADR 0030 aceito; sem implementação ainda | Backfill sem inferência; sessão sobreposta/evento no dia; regra de exclusão da adesão; FK/export/reset definidos |
| 7 | **C3-05** | Implementar evento de prova e taper 2A; sem `WorkoutType.RACE` | C3-G1 | Migração/export/reset, estados, link, disponibilidade, evento no dia e cutoff/taper por data testados |
| 8 | **C3-G2** | Revisão de fontes e copy de F4 (gate documental; não implica código) | D-1 v0.2 | **Concluído em 10/11/2026:** ADR 0029 aceito, fontes revisadas e copy aprovada; implementação decidida para ciclo futuro |

**Fora da sequência implementável:** recuperação pós-prova (FR-207), previsão/faixa
de pace e limite de longão por fração da distância (FR-206). Não criar código para
esses itens neste ciclo.

**Racional:** preserva a ordem aprovada: perfil antes do briefing; briefing genérico
antes da prova. O design/ADR de evento é um gate documental prévio, sem antecipar
implementação. F4 é revisão condicional, não conteúdo acoplado ao briefing v1.

---

## 14. Documentos a atualizar na implementação

| Documento | O que muda |
|---|---|
| `docs/PRODUCT.md` | Registrar capacidade de evento/briefing quando os gates técnicos forem implementados; exceção F4 somente após ADR 0029 |
| `docs/SPORTS_SCIENCE.md` | Distinguir taper geral, cutoff limitado, regra de produto da semana sem QUALITY e recuperação adiada; F4 somente após revisão |
| `docs/COMMUNICATION.md` | Copy genérica do briefing, datas dinâmicas e estado do evento; F4 após revisão |
| `docs/ARCHITECTURE.md` | Atualizado em 10/11/2026 com a direção dos ADRs 0030/0031; detalhes de implementação entram com o código |
| `docs/DATA_MODEL.md` | Fingerprints/snapshots, entidade/evento, migração, vínculo e export/reset |
| `docs/DECISIONS.md` | Atualizado com ADRs 0030/0031 aceitos; ADR 0029 continua condicional para F4 |
| `docs/ROADMAP.md` | Atualizado com os gates do Ciclo 3; FR-207 adiado |
| `docs/PROGRESS.md` | Diário somente de entregas implementadas |
| `AGENTS.md` | Sem permissão nutricional ampla antes do gate F4 |

---

## 15. Gates técnicos pendentes

As decisões de produto de C3-G0 foram aprovadas pelo usuário em 10/11/2026. Estes
itens são gates de especificação/implementação, não perguntas reabertas:

- **G-01 — `TodayDecision`:** implementar o ramo `Competition(RaceEvent)`, suprimir
  ações de treino no dia e excluir sessões sobrepostas do cálculo de adesão, inclusive
  em planos legados.
- **G-02 — vínculo de atividade:** definir unicidade/FK, conflito com atividade já
  usada por sessão e limpeza do vínculo em lixeira/exclusão; evento sobrevive sem o
  arquivo vinculado.
- **G-03 — backfill `TrainingGoal`:** migrar a projeção normalizada sem inferir
  realização de eventos com data passada e sem produzir T4 falso.
- **G-04 — cutoff `QUALITY`:** 10/14 dias está aprovado como política `LIMITED`; código
  e catálogo devem manter explícito que as fontes não validam diretamente esse
  intervalo por distância.
- **G-05 — temporal context:** persistir/comparar `temporalContextHash` e calcular
  `today/weekStart` do mesmo snapshot de instante/fuso em salvar e avaliar.
- **G-06 — F4:** a intenção geral está aprovada; conteúdo e fontes ainda precisam de
  revisão. Até esse gate, F4 não gera regra de motor nem strings exibidas.

O nome do ciclo permanece “Ciclo 3 — acompanhar o atleta” como rótulo de trabalho.

---

## Anexo A — Exemplo estrutural de briefing v1

Este é um molde, não uma prescrição com valores default. Cada campo só aparece se
existir na `DailyPrescription` vigente:

> **Hoje: {título prescrito} · {duração prescrita}.**
> **Alvo:** {pace/FC/zona/RPE persistido; omitir o que for `null`}.
> **Por que hoje:** {rationale persistido}.
> **Como correr:** {orientação genérica compatível com o tipo e o alvo, sem blocos}.
> **Se estiver difícil:** {copy conservadora aprovada, sem números ou treino alternativo}.

Sessões `DONE` não usam este molde: mostram somente a execução registrada. F4 não
aparece até seu gate de conteúdo/fontes ser concluído.
