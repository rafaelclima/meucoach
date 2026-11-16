# ADR 0045 — Dimensionamento do longão e estrutura semanal

**Status:** Aprovado em 2026-11-16 — implementação pendente (C4-09..C4-13).
**Data:** 2026-11-16
**Escopo:** `WeeklyPlanGenerator` (seleção do dia, duração, `fitToVolume`, títulos), `WeeklyPlan`, `PlanReplanner`.
**Relacionados:** ADR 0010 (teto de disponibilidade), ADR 0011 (deload), ADR 0038
(progressão), ADR 0041 (mesociclo), ADR 0043 (periodização), ADR 0044 (qualidade),
ADR 0048 (schema/decisão).
**PRD:** `docs/PRD_TREINO_REAL.md` FR-402, §11.5 (Q-04).
**Decisão do usuário 0:** 16/11/2026.

---

## 1. Contexto

### 1.1 O que existe hoje

- O dia do longão é `days.maxByOrNull { availability[it] }`
  (`WeeklyPlanGenerator.kt:214-219`). Em empate, vence o primeiro dia da lista
  ordenada por `DayOfWeek`; com terça, sexta e domingo iguais, o longão cai na
  **terça**.
- A duração inicial é `available.coerceIn(30, cap)` (`:798-805`).
- O longão é cortado por `maxLong = maxOf(30, easyMinutes * 3/7)`
  (`:473-483`), com `LONG_SHARE_RATIO = 3/7` (`:992`).
- Com dois fáceis de 40, `easyMinutes = 80`, `maxLong = 34`: o “Longão” tem
  **34 min**, menor que o fácil de **40**.
- O teste `builds a conservative beginner week` fixa um longão de **30 min**
  com sábado de **90 min** disponíveis (`WeeklyPlanGeneratorTest.kt:45`).
- `fitToVolume` escala todas as sessões proporcionalmente, com piso de 30 para o
  longão (`:827-854`).

### 1.2 O problema

A regra de 25–30% é um **alvo de distribuição**, mas o código a trata como um
**teto rígido**. Em semanas pequenas, o teto fica abaixo do fácil e o longão
deixa de ser o estímulo mais longo. O app chama de “Longão” uma corrida que não
desenvolve resistência e contradiz o próprio `SPORTS_SCIENCE.md`:

- §8.5: “Iniciantes: 45–90 min; aumentar 5–10 min/semana.”
- §8.8: “Longão é o dia mais longo disponível, limitado a 25–30% do volume
  semanal e a 150 min (iniciante).”

O `ADR 0038` reconhece “em semanas muito pequenas o piso vence”, mas o piso é 30,
não 45. A suíte protege o comportamento errado.

### 1.3 Decisão do usuário 0

Q-04: piso de **45 min** para iniciante/intermediário e **60 min** para avançado;
abaixo do piso, “Corrida mais longa” + ajuste de agenda. O longão nunca deve ser
menor que o fácil mais longo.

---

## 2. Decisão

### 2.1 Seleção do dia

O dia do longão é escolhido por um comparador determinístico:

```
longDay = days
  .filter { recovery.allowsLongRun(date(it)) }
  .maxWithOrNull(
    compareBy(
      { availability[it] },     // 1. maior disponibilidade
      { weekendPriority(it) },  // 2. sáb/dom > sex > outros
      { it.value }              // 3. mais tarde na semana
    )
  )
```

`weekendPriority`: Sábado/Domingo = 2, Sexta = 1, demais = 0. O objetivo é não
colocar o longão na terça quando todos os dias têm a mesma duração.

### 2.2 Duração

```
maxAvailable = availability[longDay]
floor = when (experience) {
    BEGINNER, INTERMEDIATE -> 45
    ADVANCED -> 60
}
longestOther = max(duration of non-long sessions) ?: 0
shareTarget = (0.28 / 0.72) * otherMinutes
target = min(maxAvailable, max(floor, longestOther + 5, shareTarget))
```

Precedência:

1. **Disponibilidade é teto rígido.** Nunca prescrever mais do que o dia permite.
2. **Piso vence o alvo de 25–30%.** Em semana pequena, o longão pode passar de
   30% do total; isso é aceitável porque é uma dose mínima, não uma progressão.
3. **O longão deve ser o mais longo (ou empatado) da semana.** `longestOther + 5`
   é a preferência; se não couber, o empate é aceitável.
4. **Deload/taper** podem reduzir o longão; o piso cai para 30 e o título é
   honesto. **Recovery** não tem longão.

### 2.3 Título honesto

```
title = if (target >= floor && target >= longestOther) "Longão" else "Corrida mais longa"
```

Quando `target < floor` ou `target < longestOther`, o plano usa
**“Corrida mais longa”** e registra `LONG_DAY_TOO_SHORT` com:

- detalhe: “Seu dia mais longo tem X min; o piso para um longão é 45/60.”
- `unlockHint`: “Aumente a disponibilidade de [dia] para 45–60 min.”
- ação na UI: link para o Perfil/Disponibilidade.

O app não pode inventar tempo; ele pode parar de esconder o limite.

### 2.4 `fitToVolume`

Quando o alvo de volume é menor que a soma das sessões:

1. Escalar fácil/qualidade até os pisos (fácil 20, qualidade 25).
2. Nunca escalar o longão abaixo do piso (45/60; 30 em deload/taper).
3. Se ainda não couber, remover os dias fáceis mais curtos, nunca qualidade/longão.
4. Se o alvo for menor que a soma dos pisos, a semana é “mínima”: preservar os
   pisos e declarar no rationale que o volume está acima do alvo para manter o
   estímulo. Isso não é progressão; é dose mínima.
5. O longão nunca fica abaixo do fácil mais longo.

### 2.5 Exemplos

| Disponibilidade | Fáceis | Long day | Alvo | Título | Share |
|---|---|---|---:|---|---:|
| Ter 40, Sex 40, Dom 40 | 40 + 40 | Dom 40 | 40 | Corrida mais longa | 33% (piso não cabe) |
| Ter 40, Sex 40, Sáb 90 | 40 + 40 | Sáb 90 | 45 | Longão | 36% (piso vence) |
| Ter 60, Qui 60, Sáb 60, Dom 60 | 60 + 60 + 60 | Sáb 60 | 60 | Longão | 25% |
| Ter 60, Qui 60, Dom 60 | 60 + 60 | Dom 60 | 60 | Longão (empate) | 33% |
| Deload 3×40 | 30 + 30 | Dom 30 | 30 | Corrida mais longa | 33% |

O share é calculado sobre o total final; o piso pode deixá-lo acima de 30% em
semanas pequenas. O rationale deve dizer “piso de 45 min preservado; o share de
25–30% é um alvo, não um teto”.

### 2.6 Interação com a qualidade

O long day pode mudar de terça para domingo. A seleção de qualidade
(`selectQualityDays`) roda depois e deve:

- nunca colocar qualidade no dia do longão;
- nunca colocar qualidade adjacente ao longão;
- manter o espaçamento entre qualidades;
- preferir terça/quinta/sexta quando o longão é domingo.

Os testes que assumiam terça como long day precisam ser atualizados com
justificativa.

### 2.7 Invariantes

- O longão é a sessão mais longa ou empatada com a mais longa.
- O longão nunca é menor que o fácil mais longo.
- O longão respeita a disponibilidade.
- O longão tem piso 45/60 quando a agenda permite; abaixo disso, título honesto.
- Deload/taper preservam o longão como o mais longo, com piso 30; recovery não tem.
- `fitToVolume` nunca dropa qualidade/longão.

---

## 3. Dados e APIs

- `WeeklyPlan.longRunMinutes` continua; o título vem de `PlannedSession.title`.
- `PlanDecisionReason.LONG_DAY_TOO_SHORT` (ADR 0044/0048).
- `WeeklyPlan` pode expor `longDay: DayOfWeek?`? Não é necessário; o rationale e
  a sessão bastam. Se for útil para a UI, derivar da sessão `LONG`.
- O `PlanJson`/`weekly_plans` não precisam de coluna nova; o título e o
  `longRunMinutes` já existem. A decisão/detalhe usa as colunas do ADR 0048.
- O `PlanReplanner` usa o mesmo gerador; a validação de semana deve aceitar o
  novo título e o share acima de 30% em semana mínima.

---

## 4. Alternativas consideradas

1. **Manter o teto de 25–30% sem piso.**
   Rejeitada: o longão continua menor que o fácil em semanas pequenas e
   contradiz a finalidade de resistência.
2. **Usar só a disponibilidade, sem share.**
   Rejeitada: em semanas grandes, o longão pode dominar o volume e desequilibrar
   a distribuição.
3. **Piso único de 60 para todos.**
   Rejeitada: pode não caber em agendas curtas e é agressivo para iniciante.
4. **Não chamar de longão nunca; chamar de “corrida longa” sempre.**
   Rejeitada: perde a distinção de dose; o título honesto por piso é melhor.
5. **Aumentar a disponibilidade automaticamente.**
   Rejeitada: o app não pode inventar tempo; só declarar e sugerir.

---

## 5. Consequências

### 5.1 Positivas

- O longão vira o estímulo mais longo da semana; o usuário vê progresso real.
- O app para de chamar 34 min de “Longão”.
- O limite de agenda fica explícito, com ação para ajustar.
- O volume pode crescer sobre uma base demonstrável, não sobre um teto que
  colapsa o longão.

### 5.2 Riscos

- Em semanas pequenas, o longão pode passar de 30% do total; a literatura trata
  25–30% como alvo, não lei. O rationale deve explicar.
- O piso de 45 pode aumentar o volume de um iniciante; o teto de disponibilidade
  e os gates de progressão continuam. O piso é dose mínima, não progressão.
- A mudança do long day para domingo pode alterar a qualidade de terça/sexta;
  testes e UI precisam acompanhar.

### 5.3 Segurança

- O longão continua em ritmo fácil, com teto de 150/180 min.
- Recovery não tem longão; taper/deload reduzem.
- Nenhum piso autoriza ignorar dor, doença, prontidão ou disponibilidade.

---

## 6. Matriz de testes

| Caso | Entrada | Saída |
|---|---|---|
| `G-PER-04` | 2×40 + sábado 90 | longão 45, título “Longão”, volume 125 |
| 3×40 | Ter/Fri/Dom 40 | longão domingo 40, “Corrida mais longa”, sem “Longão 34” |
| 4×60 | 4 dias 60 | longão 60, é o mais longo; share 25% |
| 3×60 | 3 dias 60 | longão 60, empate, título “Longão” |
| Empate | 3×40 | domingo, não terça |
| `fitToVolume` | alvo baixo | remove fácil, mantém qualidade/longão |
| Deload | 3×40, deload | longão 30, título “Corrida mais longa” |
| Taper | semana de prova | longão não existe ou é micro-dose; sem “Longão” |
| Recovery | janela pós-prova | sem longão |
| Propriedade | qualquer agenda | longão ≥ fácil mais longo; ≤ disponibilidade |

Atualizar o teste legado `builds a conservative beginner week` de 30 para 45
(com sábado 90) e justificar no commit.

---

## 7. Rollout

- `weekly-plan-v7`; `PlanUpgrade` na semana vigente (ADR 0048).
- Histórico v6 preservado; o novo título/duration só vale para planos novos ou
  atualizados.
- A UI do Plano/Hoje mostra “Corrida mais longa” e o link de disponibilidade
  quando `LONG_DAY_TOO_SHORT`.
- O `PROGRESS.md`/`ROADMAP.md` registram a mudança.

---

## 8. Fora de escopo

- Aumentar a disponibilidade do usuário.
- Alterar a regra de 150/180 min.
- Criar um “longão progressivo” com blocos de qualidade; o longão continua fácil.
- Reescrever o `VolumeProgression` além do necessário para o piso.

---

## 9. Referências

- `docs/PRD_TREINO_REAL.md` FR-402, §11.5 (Q-04).
- `docs/SPORTS_SCIENCE.md` §8.5, §8.8.
- ADR 0010, 0011, 0038, 0041, 0043, 0044.
- `EV-PFITZINGER-2008`, `EV-DANIELS-2014`.
