# ADR 0067 — Rampa do longão do iniciante (+10 min/semana)

**Status:** Aceito e implementado em 26/11/2026 (Fatia 2; `weekly-plan-v9`).
**Data:** 2026-11-26
**Escopo:** `WeeklyPlanGenerator.longRunTarget`/`applyLongRunHonesty`, referência do último
longão no input do gerador, `PlanDecision`/rationale, `SPORTS_SCIENCE.md` §8.5/§8.8/§14.
**Relacionados:** ADR 0038 (progressão), ADR 0045 (longão), ADR 0048 (versões/upgrade),
ADR 0050 (volume real), ADRs 0066/0068/0069 (Fatia 0).
**PRD:** `docs/PRD_CORRECOES_AUDITORIA_2026-11.md` — D4, NOVO-SCI-02, §5 (Fatia 0), §6.5.

---

## 1. Contexto

`SPORTS_SCIENCE.md` §8.5 define que o iniciante corre longões de 45–90 min quando a
agenda permite e **aumenta 5–10 min/semana**. O `WeeklyPlanGenerator.longRunTarget`
atual calcula o longão por piso (45 min para iniciante/intermediário), alvo de 25–30%
do volume (o motor usa 28%), margem de 5 min sobre a sessão mais longa e teto de
disponibilidade. Ele **não conhece o longão da semana anterior**: a auditoria de
26/11/2026 (`NOVO-SCI-02`) reproduziu um salto de 45 para 62 min (+17) em uma semana.

A decisão D4 foi aprovada na direção A: limitar o aumento a **+10 min/semana** sobre o
último longão real/planejado, com piso 45 e respeito à agenda. O PRD exige ADR antes de
qualquer mudança de prescrição; esta fatia é **docs-only** e não altera o motor.

## 2. Decisão

### 2.1 Referência do último longão

O gerador passa a receber uma referência pura do último longão **normal** do iniciante,
com a seguinte precedência:

1. o longão mais recente de uma semana **normal** anterior (mesma janela de 4 semanas
   do ADR 0050), com duração real quando houver atividade vinculada forte
   (`ACTIVITY`/`GUIDED`);
2. se não houver duração real, a duração planejada da sessão `LONG` mais recente em uma
   semana normal anterior;
3. se não houver nenhuma das duas, **sem referência**: o piso de 45 min é a base.

Semanas marcadas como deload, taper, semana de prova ou recuperação (`R-PLAN-04`,
`R-PLAN-05`, `R-PLAN-10`, `R-RACE-04`) são excluídas da referência: um longão de 30 min
de taper não é a base de rampa para a volta à semana normal. `MANUAL` e
`LEGACY_UNVERIFIED` não contam como duração real; valem apenas como sessão planejada
quando não há atividade forte.

A referência é do **iniciante** nesta decisão. Intermediário e avançado mantêm o
comportamento atual até um ADR próprio; o piso de 45 min já é o piso do iniciante no
§8.5.

### 2.2 Fórmula do alvo

O alvo continua sendo o maior entre piso, sessão mais longa + 5 min e alvo de 25–30%,
mas passa a ser limitado pela rampa:

```text
candidato = max(piso, mais_longa_outra + 5, alvo_25_30%)
rampa = ultimo_longao_normal + 10        # quando há referência
rampa = piso                             # sem referência: o primeiro longão é o piso
alvo = min(disponibilidade_do_dia, teto_do_nivel, max(piso, min(candidato, rampa)))
```

Propriedades:

- **Nunca aumenta mais de 10 min** sobre o último longão normal quando a referência
  existe.
- **Nunca fica abaixo do piso** de 45 min quando a agenda permite o piso.
- **Nunca excede a disponibilidade** do dia nem o teto do nível.
- **Pode manter ou reduzir** o longão quando a agenda/volume da semana é menor; a rampa
  limita o aumento, não obriga progressão. A copy não apresenta uma redução como
  “aumento”.
- A regra não muda a seleção do dia do longão (ADR 0045) nem o critério de graduação
  que exige longão real de 60/90 min (§8.12).

### 2.3 Quando o piso ou a agenda impedem o aumento

- **Agenda abaixo do alvo:** o longão fica no teto disponível. Se ainda estiver abaixo
  do piso, mantém o título honesto **“Corrida mais longa”** e a copy explica que o dia
  tem X min e que o piso é 45; nenhuma sessão excede o slot.
- **Agenda entre o piso e o alvo:** o longão fica na agenda (ex.: 50 min) e a copy diz
  que o aumento de até 10 min fica para quando houver mais tempo. Não há promessa de
  +10 que a agenda não permite.
- **Piso acima da rampa:** se o último longão normal ficou tão abaixo do piso que o
  +10 min ainda não o alcança (ex.: 30 min por agenda), o piso vence e o aumento pode ser
  maior que 10 min naquela volta. A copy nomeia a exceção (“o piso de 45 min vence; o
  último longão foi 30 min”).
- **Sem referência:** o primeiro longão do ciclo usa o piso de 45 min, com copy
  “primeiro longão do ciclo”. Não se inventa uma rampa a partir de zero.
- **Semana especial:** a referência é a última semana normal; a volta de taper/recuperação
  não é tratada como salto de rampa.
- **Teto do nível:** se o candidato ultrapassar o teto do nível, o teto vence; a copy
  não promete um longão que o nível não suporta.

### 2.4 Copy

A copy deve declarar a referência e o limite, sem números inventados:

- **Aumento normal:** “Último longão de 45 min; nesta semana até 55 min (+10 min).”
- **Agenda limitando:** “Agenda limita o longão a 50 min; o aumento de até 10 min fica
  para quando houver mais tempo.”
- **Piso vencendo:** “Piso de 45 min para iniciante; o último longão foi 30 min, então
  o piso vence nesta volta.”
- **Sem referência:** “Primeiro longão do ciclo: piso de 45 min.”
- **Manutenção/redução:** “Longão de X min; nesta semana o volume/agenda pede Y min,
  sem aumento.”

### 2.5 Impacto de versão

- `weekly-plan-v9`: a saída muda para longões de iniciante; o `PlanUpgrade` (ADR 0048)
  reavalia planos antigos na abertura, preservando passado, `DONE` e `CLOSED`.
- `daily-prescription-v7`: permanece se o motor diário apenas consumir o plano. Se o
  fallback diário sem plano passar a aplicar a rampa, a versão também sobe; hoje ele não
  conhece o último longão e não deve inventar um.
- Sem schema, migração, dependência nova ou reescrita de histórico.

## 3. Alternativas avaliadas

| Alternativa | Por que não |
|---|---|
| **B — manter o salto e documentar a exceção** | Contraria o §8.5 e a decisão aprovada; um iniciante pode saltar 17 min em uma semana. |
| Limitar a +5 min/semana | É o limite inferior da faixa documentada; mais conservador, mas a decisão aprovada adotou +10 como teto. |
| Rampa percentual (ex.: +10%) | Em longões pequenos rende pouco e em longões grandes rende demais; a faixa documentada é em minutos absolutos. |
| Usar só o longão real | Sem atividade importada, o plano ficaria sem referência mesmo tendo um longão planejado; o fallback planejado é necessário. |
| Usar só o longão planejado | Ignora a duração real quando ela existe e pode subestimar ou superestimar a dose. |
| Aplicar a rampa a todos os níveis | O §8.5 e a decisão D4 são específicos do iniciante; intermediário/avançado exigem ADR próprio. |

## 4. Evidência e força

- `SPORTS_SCIENCE.md` §8.5: iniciante 45–90 min, aumento de 5–10 min/semana, piso 45,
  disponibilidade como teto, longão nunca abaixo do fácil mais longo.
- `EV-PFITZINGER-2008` e `EV-DANIELS-2014`: progressão conservadora de longão e
  distribuição de volume.
- `R-PLAN-03 v3` no §14: a estrutura do longão é `MODERATE`; o limite exato de +10
  min/semana e o fallback real/planejado são `LIMITED — convenção de produto`, porque a
  faixa 5–10 é diretriz prática, não um ensaio clínico de rampa semanal.
- A regra não cria novo `evidenceId`.

## 5. Consequências

- O input do gerador ganha uma referência explícita do último longão normal (duração,
  data e origem real/planejada). Isso é um DTO/domínio puro; não exige schema nem
  migração.
- `PlanDecision`/rationale ganham motivos nomeados (`LONG_RUN_RAMP_CAPPED`,
  `LONG_RUN_AGENDA_LIMITED`, `LONG_RUN_FLOOR_WINS`, `LONG_RUN_FIRST`) para a UI e o
  Coach explicarem o limite.
- A seleção do dia e o título “Corrida mais longa” continuam os do ADR 0045; a rampa só
  muda a duração.
- O plano fica mais conservador para quem voltou de uma semana curta ou tem agenda
  apertada; a copy explica por que o longão não subiu.
- Risco: uma referência real muito longa (treino extra) pode manter o longão alto por
  semanas. Mitigação: a referência é de semana normal e o teto do nível continua; a
  rampa limita o aumento, não impede redução por agenda/volume.
- Sem schema, sem migração, sem dependência nova.

## 6. Critérios de aceite e golden `G-LONG-RUN-RAMP-01`

Aceite da futura implementação:

1. Iniciante com último longão normal de 45 min e agenda de 90 min recebe longão
   **≤55 min** e **≥45 min**; nenhuma sessão excede a disponibilidade.
2. A copy declara o último longão e o limite de +10 min; não promete um salto maior.
3. Com agenda de 50 min, o longão fica em 50 min, a copy nomeia a agenda e nenhuma
   sessão excede 50.
4. Sem referência, o longão é 45 min (piso) e a copy diz “primeiro longão do ciclo”.
5. Com último longão normal tão abaixo do piso que o +10 não o alcança (ex.: 30), o
   piso 45 vence e a copy nomeia a exceção.
6. O longão continua sendo a sessão mais longa ou empatada com a mais longa; a regra de
   `applyLongRunHonesty` do ADR 0045 permanece.

### Golden `G-LONG-RUN-RAMP-01` (definido; executável só na fatia de código)

| Cenário | Entrada sintética | Asserção |
|---|---|---|
| **A — rampa normal** | Iniciante, último longão normal 45 min, agenda do dia 90 min, demais sessões somando volume suficiente para o alvo de 28% passar de 55; semana normal, sem taper/deload | Longão = 45–55 min (ideal 55); copy contém “último longão de 45” e “+10 min”; toda sessão ≤ agenda |
| **B — agenda limita** | Mesmo cenário, agenda do dia 50 min | Longão = 50 min; copy contém “agenda” e não promete 55; toda sessão ≤50 |
| **C — sem referência** | Iniciante sem longão normal anterior; agenda 90 min | Longão = 45 min; copy contém “primeiro longão” ou “piso de 45”; nenhuma sessão >90 |
| **D — piso vence** | Último longão normal 30 min (agenda curta anterior), agenda 90 min | Longão = 45 min; copy nomeia o piso e a exceção ao +10; nenhuma sessão >90 |

O cenário A falha no motor v8 (o alvo por 28%/margem pode chegar a 62–69 min) e passa
depois da rampa. Os cenários B–D provam as decisões de borda e a honestidade da copy;
não congelam um valor publicado da auditoria.

**Implementação:** `LongRunReferenceResolver` (real forte > planejado, janela de 4
semanas, semanas especiais e encerramentos não treináveis fora), `longRunRamp` no
`WeeklyPlanGenerator`, copy de rampa/agenda/piso e golden `LongRunRampGoldenTest`;
`weekly-plan-v9`.
