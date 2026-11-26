# ADR 0068 — Adjacência qualidade × longão entre semanas

**Status:** Aceito em 26/11/2026 como convenção `LIMITED`; implementação pendente
(Fatia 0 docs-only).
**Data:** 2026-11-26
**Escopo:** `WeeklyPlanGenerator.selectQualityDays`/`qualityPlanDecision`, uso de
`previousWeek`/`recentWeeks`, copy do plano, `SPORTS_SCIENCE.md` §8.2/§8.5/§8.8/§14.
**Relacionados:** ADR 0023 (plano × prescrição), ADR 0044 (oportunidade e autoridade do
dia), ADR 0045 (longão), ADR 0048 (versões), ADRs 0066/0067/0069 (Fatia 0).
**PRD:** `docs/PRD_CORRECOES_AUDITORIA_2026-11.md` — D5, ALG-02, §5 (Fatia 0), §6.5.

---

## 1. Contexto

`WeeklyPlanGenerator.selectQualityDays` protege a qualidade contra o longão **da mesma
semana**: o dia do longão e seus dias adjacentes não recebem `QUALITY`, e duas
qualidades não ficam consecutivas. A regra não olha a semana anterior. Com um longão no
domingo, a segunda-feira seguinte pode receber qualidade; a auditoria de 26/11/2026
reproduziu o caso (`ALG-02`, E19).

`SPORTS_SCIENCE.md` §8.2 orienta evitar qualidade no dia seguinte a perna pesada e §8.5
proíbe o longão no dia seguinte à qualidade. O documento **não** declara explicitamente
que a virada de semana está coberta. A decisão D5 foi aprovada na direção A: estender a
adjacência para a qualidade não cair no dia seguinte ao longão anterior, inclusive
quando o longão é o domingo da semana anterior.

O PRD classifica a mudança como **convenção `LIMITED` de produto**, não como violação
científica já estabelecida. Esta fatia é docs-only e não altera o motor.

## 2. Decisão

### 2.1 Regra

Ao selecionar os dias de qualidade da semana, o gerador verifica o dia imediatamente
anterior a cada candidato. Se esse dia, na semana anterior, contém uma sessão `LONG`
treinável, o candidato é descartado. O caso canônico é o longão de domingo bloqueando a
qualidade de segunda.

A verificação usa o plano persistido da semana anterior (`previousWeek` ou a semana
correspondente em `recentWeeks`):

- `LONG` planejado e não encerrado como não treinável bloqueia;
- `LONG` encerrado como descanso, doença ou outra razão não treinável **não** bloqueia,
  porque não houve estímulo;
- se a semana anterior não está disponível, o gerador **não inventa** um longão e não
  bloqueia. A ausência de dados é ausência, não recuperação ruim.

A regra vale para as duas oportunidades de qualidade (a estrutural e a que exige
progressão). Se a primeira candidata é bloqueada, o gerador tenta o próximo dia seguro;
se não houver nenhum, a semana fica sem `QUALITY`.

As regras existentes continuam:

- no máximo 2 qualidades por semana;
- qualidades nunca consecutivas;
- qualidade nunca no dia do longão nem no dia seguinte/próximo ao longão da mesma
  semana;
- longão nunca no dia seguinte a uma qualidade dentro da mesma semana.

Esta decisão implementa a direção aprovada na D5 (qualidade depois de longão). A direção
inversa (longão depois de qualidade) continua coberta dentro da semana; a extensão
inversa entre semanas não é ampliada nesta fatia para não remover um longão sem dia
alternativo sem decisão própria. A limitação fica registrada no §8.8.

### 2.2 Sem dia seguro

Se todos os dias com disponibilidade para qualidade estiverem bloqueados pela adjacência
(ou por outra regra), o plano:

1. não cria uma qualidade “extra” em dia inseguro;
2. não transforma o longão em qualidade;
3. mantém os dias como fácil/descanso, com a agenda respeitada;
4. explica na copy: “Sem dia seguro para qualidade: o longão de domingo (X min) ainda
   pesa na segunda; esta semana fica sem treino forte.”

A copy nomeia o longão anterior e a razão. Ela não promete uma qualidade que não existe
e não sugere que o usuário “compense” com outro treino forte.

### 2.3 Copy e transparência

- Quando a qualidade é **movida** de segunda para outro dia seguro, o rationale/plan
  decision registra: “Qualidade movida de segunda para terça: longão de domingo ainda
  pesa na segunda.”
- Quando a qualidade é **omitida**, a copy usa a frase de “sem dia seguro” acima.
- Quando não há plano anterior conhecido, a copy não menciona adjacência; a ausência de
  dado não vira justificativa inventada.

### 2.4 Impacto de versão

- `weekly-plan-v8`: a seleção de dias e a copy mudam para semanas após um longão;
  a fatia de código deve subir a versão do motor e o `PlanUpgrade` (ADR 0048) reavalia
  planos antigos na abertura, preservando passado, `DONE` e `CLOSED`.
- `daily-prescription-v7`: permanece se o motor diário apenas consumir a qualidade já
  escolhida pelo plano. Se ele passar a recalcular a oportunidade olhando o dia anterior,
  a versão também sobe.
- Sem schema, migração, dependência nova ou reescrita de histórico.

## 3. Alternativas avaliadas

| Alternativa | Por que não |
|---|---|
| **B — documentar que a virada não é coberta** | Deixa uma qualidade forte em recuperação incompleta logo após o longão; a convenção conservadora é barata e alinhada ao §8.2. |
| Bloquear qualquer sessão na segunda após longão | O fácil de recuperação é adequado; o problema é o estímulo forte, não a corrida leve. |
| Mover o longão para sábado em todas as semanas | A agenda e a seleção do dia (ADR 0045) não permitem; mudaria o plano inteiro para resolver um caso. |
| Criar uma terceira qualidade para “compensar” | Viola o máximo de 2 qualidades e aumenta a carga; não é compensação, é mais estímulo forte. |
| Usar a atividade real em vez do plano anterior | O plano é a intenção persistida e está disponível; a atividade pode não estar importada. Quando o plano anterior não existe, não se inventa. |

## 4. Evidência e força

- `SPORTS_SCIENCE.md` §8.2 (evitar qualidade no dia seguinte a perna pesada), §8.5
  (longão não no dia seguinte à qualidade) e §8.8 (espaçamento de qualidade).
- `R-PER-03`/ADR 0044: a oportunidade de qualidade é estrutural, mas o dia confirma com
  prontidão/carga; a adjacência é uma trava estrutural adicional.
- `EV-SEILER-2010` e `EV-DANIELS-2014`: separação de estímulos fortes e recuperação
  entre sessões de maior carga.
- **Força:** `MODERATE` para o espaçamento dentro da semana; `LIMITED — convenção de
  produto` para a extensão à virada de semana. Não há ensaio clínico que isole
  “qualidade na segunda após longão no domingo”; o ADR não afirma que há.
- A regra não cria novo `evidenceId`.

## 5. Consequências

- `selectQualityDays` passa a receber o contexto do dia anterior (ou o plano da semana
  anterior) e a checar `LONG` antes de aceitar a segunda-feira. A função continua pura;
  o input já carrega `previousWeek`/`recentWeeks`.
- `PlanDecision`/rationale ganham motivos nomeados
  (`QUALITY_MOVED_AFTER_LONG_RUN`, `QUALITY_NO_SAFE_DAY_AFTER_LONG_RUN`) para a UI e o
  Coach explicarem a mudança.
- A semana pode ficar com 1 ou 0 qualidades em vez de 2; é uma redução conservadora, não
  uma perda de adesão. O máximo de 2 continua.
- A copy fica mais explícita quando a qualidade é movida ou omitida.
- Risco: bloquear qualidade em uma segunda que o usuário considera “descansada”. A regra
  é conservadora e `LIMITED`; a copy explica e o usuário pode ajustar a agenda.
- Sem schema, sem migração, sem dependência nova.

## 6. Critérios de aceite e golden `G-QUALITY-ADJACENCY-01`

Aceite da futura implementação:

1. Com longão no domingo anterior, nenhuma `QUALITY` cai na segunda seguinte.
2. Se houver outro dia seguro, a qualidade é movida para ele e a copy nomeia o longão
   anterior; no máximo 2 qualidades e nenhuma consecutiva.
3. Se não houver dia seguro, a semana fica sem `QUALITY` e a copy explica “sem dia
   seguro”, nomeando o longão anterior.
4. Nenhuma sessão excede a disponibilidade; nenhum estímulo fica com `structure == null`.
5. Sem plano anterior conhecido, a qualidade de segunda não é bloqueada por um longão
   inventado; a copy não menciona adjacência.
6. O comportamento existente dentro da semana (longão × qualidade, 2 qualidades máximo,
   não consecutivas) permanece.

### Golden `G-QUALITY-ADJACENCY-01` (definido; executável só na fatia de código)

| Cenário | Entrada sintética | Asserção |
|---|---|---|
| **A — qualidade movida** | Intermediário, BUILD, sem taper; semana anterior com longão de 60 min no domingo; semana atual com disponibilidade segunda 40, terça 40, quinta 40 e sábado 90 | Nenhuma `QUALITY` na segunda; se houver qualidade, ela está em terça/quinta e não é adjacente ao longão de sábado; copy contém “longão de domingo”; toda sessão ≤ agenda |
| **B — sem dia seguro** | Mesmo perfil, mas a única disponibilidade de qualidade é segunda 40 (sábado 90 é o longão) | Zero `QUALITY`; copy contém “sem dia seguro” e “longão de domingo”; nenhuma sessão > agenda; nenhum estímulo sem estrutura |
| **C — sem plano anterior** | Sem `previousWeek`/`recentWeeks`; disponibilidade segunda 40 e sábado 90 | A segunda pode receber qualidade (não há longão conhecido); copy não inventa adjacência; regras da mesma semana continuam |

O cenário A falha no motor atual (a qualidade pode cair na segunda) e passa depois da
verificação entre semanas. O cenário B prova a copy de omissão. O cenário C protege a
regra “dado ausente é ausente”.

**Fora de escopo desta fatia:** implementar a checagem, alterar `selectQualityDays`,
mudar `ENGINE_VERSION`, criar fixture executável ou ampliar a direção inversa entre
semanas. Este ADR apenas fixa a decisão e o critério.
