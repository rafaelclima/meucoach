# ADR 0030 — Evento de prova e taper semanal

**Status:** Aceito em princípios de produto/autoridade diária em 2026-11-10; desenho técnico C3-G1 concluído e implementação C3-05 concluída em 2026-11-10
**Data:** 2026-11-10
**Escopo:** C3-G1/F2 do `PRD_ADAPTACAO_E_BRIEFING_v0.2.md`
**Relacionados:** ADR 0022 (tempo/fuso), ADR 0023 (plano × prescrição), ADR 0024 (conclusão/evidência), ADR 0028 (replanejamento), ADR 0031 (fingerprint)
**Desenho técnico:** [PLANO_TECNICO_CICLO_3_EVENTO.md](PLANO_TECNICO_CICLO_3_EVENTO.md)

---

## 1. Contexto

O perfil persiste um `TrainingGoal` em JSON. O formulário oferece quatro distâncias
de prova (5 km, 10 km, meia maratona, maratona), mas não uma distância customizada.
O `WeeklyPlanGenerator` usa a data-alvo para taper calculado a partir de `weekStart`,
reduz EASY/LONG e mantém `QUALITY` inalterada. Não existe entidade de prova nem
estados de realização/cancelamento/não comparecimento.

`PlannedSession` é um treino com disponibilidade, conclusão e evidência. `RACE` não
deve ser adicionado como outro `WorkoutType`: uma competição tem ciclo de vida e
associação de atividade distintos. Porém, ADR 0023 torna `DailyPrescription` a
autoridade diária única, e sua forma atual é centrada numa sessão/`WorkoutType`.
Assim, separar o evento do enum não resolve sozinho qual decisão o Hoje apresenta no
dia da prova.

## 2. Restrições de produto aprovadas

- A prova é um evento, não uma sessão `QUALITY` nem um `WorkoutType`.
- Estados conceituais: `PLANNED`, `COMPLETED`, `CANCELLED`, `NO_SHOW`.
- É possível associar o evento a uma atividade registrada; associação não automática.
- Distâncias v1 são as quatro opções de prova existentes. `CUSTOM` não cria evento.
- `targetTimeSec` é meta declarada, nunca pace prescrito, faixa de ritmo ou previsão.
- Não planejar `QUALITY` na semana local da prova; sem sessão de ativação 24–48 h.
- Não automatizar recuperação pós-prova no Ciclo 3 v0.2.

## 3. Direção aceita; detalhes de persistência permanecem como gate

### 3.1 Agregado `RaceEvent`

Criar modelo de domínio independente de sessão e `WorkoutType`, com dados conceituais:

- `id`;
- distância enum fixa suportada;
- data local no fuso do perfil;
- `targetTimeSec?` como objetivo declarado;
- estado `PLANNED`, `COMPLETED`, `CANCELLED` ou `NO_SHOW`;
- `activityId?` opcional;
- `createdAt`/`updatedAt`.

Direção de persistência aprovada: tabela `race_events` em Room, não ampliar o JSON de
perfil para conter histórico de estado/vínculo. Manter no máximo um evento `PLANNED`
ativo, consistente com a decisão existente de um objetivo ativo; eventos encerrados
permanecem para histórico/export. A forma de migrar `TrainingGoal` atual para o novo
agregado é parte do gate de implementação.

Transições aprovadas, sempre explícitas pelo usuário:

```text
PLANNED → COMPLETED
PLANNED → CANCELLED
PLANNED → NO_SHOW
```

Um evento com data passada continua `PLANNED` até ação do usuário; não inferir que
foi realizado. `COMPLETED` requer confirmação explícita; atividade é opcional e pode
ser associada na confirmação ou depois. Importar atividade nunca marca o evento como
realizado automaticamente; pode oferecer vínculo explícito. `CANCELLED`/`NO_SHOW` não
iniciam recuperação.

### 3.2 Relação com `Activity`, adesão e disponibilidade

- Evento não é `PlannedSession`; não entra em volume/minutos/contagem de qualidade
  nem no denominador de adesão.
- Evento planejado pode existir em dia sem disponibilidade e não passa pelo
  validador de sessões do `PlanReplanner`.
- A atividade vinculada continua uma `Activity` normal e participa da carga pelos
  mecanismos existentes. O evento não inventa volume/carga adicional.
- O vínculo deve ser explícito e transacional. O ADR precisa decidir unicidade e a
  relação com `session_executions.activityId`/lixeira/exclusão permanente. Proposta:
  remover apenas o vínculo quando a atividade for eliminada; preservar o evento.
- `WeeklyPlanGenerator`/`PlanReplanner` não devem gerar uma sessão normal no dia do
  evento ativo. Um plano legado com sessão nesse dia exige tratamento event-aware;
  não apagar hoje/passado nem mudar `DONE`/`CLOSED`.

### 3.3 Autoridade do dia — decisão aprovada

Não criar UI em que o atleta receba uma sessão normal e um evento concorrente para a
mesma data. Duas opções de arquitetura:

**A. Decisão diária discriminada (aprovada).** Introduzir
um resultado canônico de Hoje (`Training(DailyPrescription)` ou `Competition(RaceEvent)`)
consumido por todas as telas. A prova não é `WorkoutType`; no dia do evento, o ramo
de competição suprime ações de iniciar/trocar/concluir sessão ordinária. A fonte do
ramo de competição é o `RaceEvent` persistido. Requer revisar o contrato ADR 0023,
`GetTodayPrescription`/Hoje, notificação, conclusão e cálculo de adesão.

**B. Evento paralelo sem decisão discriminada.** Manter a prescrição diária existente
e apresentar evento à parte. **Rejeitada:** pode exibir duas instruções e contraria a
regra vigente de uma decisão do dia.

O usuário aprovou A em 2026-11-10. O ADR 0030 complementa ADR 0023: todas as telas
consomem uma única `TodayDecision`, que retorna treino ou evento. Para novas semanas,
o gerador não cria `PlannedSession` no dia do evento; evento não entra no denominador
de adesão. Em planos legados com sessão na mesma data, `Competition` prevalece na
decisão do dia e a sessão sobreposta é excluída da adesão. Não introduzir `RACE` nem
um `REST` artificial para representar competição.

### 3.4 Taper 2A: janela e ações por sessão

O cálculo usa `daysToRace = targetDate - session.date` em datas locais do perfil.
As sessões de hoje/passadas e `DONE`/`CLOSED` continuam protegidas pelo ADR 0028.

| Distância suportada | Janela geral de taper documentada hoje | Cutoff aprovado da última `QUALITY` |
|---|---:|---:|
| 5K / 10K | 14 dias | no máximo até `targetDate - 10 dias` |
| Meia maratona | 14 dias | no máximo até `targetDate - 14 dias` |
| Maratona | 21 dias | no máximo até `targetDate - 14 dias` |

Regras da tabela:

1. Não gerar `QUALITY` na semana civil local que contém a prova; não criar ativação.
2. O cutoff significa que nenhuma `QUALITY` planejada pode ocorrer dentro dos
   últimos 10/14 dias, inclusive na semana da prova. Para um empate exato, a última
   `QUALITY` pode estar em `targetDate - N`; isso precisa ficar fixo nos testes.
3. Os valores 10/14 foram aprovados pelo usuário como extremo distante dos intervalos
   em `SPORTS_SCIENCE.md` §8.6, classificados aqui como **LIMITED / convenção
   conservadora de produto**.
   Bosquet 2007 e Wang et al. 2023 apoiam princípios gerais de taper (reduzir volume,
   manter estímulos/frequência em estudos), mas não demonstram os cutoffs por
   distância nem a regra “sem QUALITY na semana”. Não citar essas fontes como prova
   direta do cutoff.
4. Reduzir progressivamente o volume durante a janela e também a duração de
   `QUALITY` que ainda seja elegível antes do cutoff; não criar sessões/intensidade.
   O algoritmo e a tabela de fatores devem ser definidos após avaliar o piso atual
   de 25 min para QUALITY: aplicar `0,55`/`0,45` pode não reduzir todas as sessões.
5. O total semanal no plano rolling deve ser testado contra a redução aprovada; não
   alegar uma preparação multissemanal prospectiva.
6. Os números 10/14 estão aprovados como política `LIMITED`, não como resultado
   científico. A documentação deve preservar essa classificação; qualquer mudança
   dos valores exige nova aprovação. Não substituí-los por números novos.

Fontes consultadas:

- Bosquet et al. 2007, meta-análise: taper médio de duas semanas e redução de volume
  de 41–60%, com intensidade/frequência mantidas em média. [PubMed](https://pubmed.ncbi.nlm.nih.gov/17762369/)
- Wang et al. 2023, revisão sistemática/meta-análise de 14 estudos (174 atletas):
  taper de até 21 dias com redução de volume de 41–60% e manutenção de intensidade/
  frequência teve benefício agregado; não determina o último treino por distância.
  [PMC](https://pmc.ncbi.nlm.nih.gov/articles/PMC10171681/)

### 3.5 Limites temporais e metas

- Data no passado: sem taper retroativo e sem estado automático; pedir que o usuário
  resolva o evento conforme os estados aceitos.
- Prova a menos de 10 dias: evento permanece visível, mas não há promessa de
  preparação completa. A regra diária conservadora atual continua valendo.
- Distância não customizada; apenas quatro tipos do formulário.
- Tempo-alvo pode ser mostrado como “meta informada”, sem conversão em pace,
  intervalo, previsão ou promessa.
- FR-206 (limitar longão por fração da prova) removido; FR-207 (recuperação)
  adiado.

## 4. Alternativas consideradas

1. **`RACE` dentro de `WorkoutType`.** Rejeitada: mistura competição e treino, afeta
   `DailyPrescription`, troca, matcher, conclusão, adesão e alvos.
2. **Prova em `TrainingGoal` JSON apenas.** Menos migração, mas não representa com
   clareza estado, atividade associada e histórico de eventos.
3. **`RaceEvent` separado + ramo de decisão diária.** Escolhida: mantém o evento
   fora da semântica de sessão e preserva uma única decisão visível no Hoje, ao custo
   de ampliar ADR 0023 e vários consumidores.
4. **Evento como cartão paralelo à prescrição.** Rejeitada na proposta por poder
   produzir instruções concorrentes.
5. **Recuperação calculada só pela data planejada.** Rejeitada: pode iniciar sem prova
   realizada; recuperação fica fora do C3 v0.2.

## 5. Impactos e gates

**Camadas:** `core:model`/`core:domain` para `RaceEvent` e regras; `core:data` para
`race_events`, FK, migração, export/reset; `:app` para edição, TodayDecision, UI,
notificação e associação explícita com atividade.

**Consumidores a revisar:** `WeeklyPlanGenerator`, `PlanReplanner`,
`PlanCompletionCalculator`, `SessionSwapper`, `SessionMatcher`, `GetTodayPrescription`,
`CompleteSession`, `ReminderContent` e superfícies Today/Plano. `WorkoutType` continua
sem `RACE`.

**Gates antes de código:**

- implementar `TodayDecision` com o ramo de competição aprovado e adicionar a
  compatibilidade necessária ao contrato ADR 0023;
- `COMPLETED` exige ação explícita; vínculo a atividade é opcional e nunca inferido;
- manter uma única prova `PLANNED` ativa; preservar eventos encerrados;
- cutoff 10/14 permanece classificado como `LIMITED`; revisão de fonte não pode
  apresentá-lo como consenso nem como conclusão direta das meta-análises;
- definir migração/backfill de `TrainingGoal`, política de FK/unicidade de atividade
  e sequência de migrações Room;
- manter compatibilidade com ADR 0031 e a projeção canônica de objetivo.

**Testes previstos:** eventos em cada dia/fuso, dia indisponível, evento hoje/amanhã/
passado/iminente; quatro distâncias; transições de estado; cancelamento/não
comparecimento sem recuperação; vínculo/atividade duplicada e exclusão; semana sem
QUALITY, cutoff inclusivo, taper por sessão, sessão protegida, agregados, export/reset
e regressões de adesão/notificação.

## 6. Decisões do usuário e pendências de implementação

Em 2026-11-10, o usuário aprovou: `TodayDecision` discriminada (opção A),
`COMPLETED` por confirmação explícita com vínculo opcional, uma prova planejada ativa
com histórico preservado e cutoff de 10/14 dias como política `LIMITED`.

O desenho técnico do C3-G1 foi concluído em 2026-11-10 e resolve os detalhes que
estavam em aberto, sem implementar código:

- **Backfill:** a migração `14→15` cria `race_events` vazia; nenhum evento é inferido
  de `TrainingGoal`. A projeção de planejamento usa evento `PLANNED` quando existir,
  senão a meta legada; depois do primeiro evento, a meta não volta a planejar.
- **Room:** `race_events` com `activeKey` único (`"ACTIVE"` só em `PLANNED`),
  `activityId` único com FK `ON DELETE SET NULL`, índices por data e estado.
- **Vínculo:** atividade na lixeira suspende a exibição e restaurar devolve; expurgo
  zera o vínculo. Unicidade cruzada com `session_executions` é transacional.
- **Adesão:** sessões em datas de evento (qualquer estado) saem do denominador.
- **Taper:** por `targetDate - session.date`, sem `QUALITY` na semana da prova,
  cutoff inclusivo 10/14 e nova regra `R-PLAN-10` classificada `LIMITED`.
- **Sequência:** model/domínio → Room 15 → casos de uso → gerador/replanejamento/
  notificação → UI/goldens → documentação.

A implementação foi concluída em C3-05 conforme a ordem do plano técnico: modelo,
domínio, Room 15, casos de uso, gerador/replanejamento/notificação, UI de
Perfil/Hoje/Plano e documentação. Recuperação pós-prova (FR-207) e limite de longão
por fração da prova (FR-206) permanecem fora do escopo.
