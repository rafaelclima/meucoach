# ADR 0066 — Baseline do taper pela média das 3 semanas pré-taper

**Status:** Aceito e implementado em 26/11/2026 (Fatia 1; `weekly-plan-v9`).
**Data:** 2026-11-26
**Escopo:** `WeeklyPlanGenerator` (volume de taper e copy), `VolumeProgression`/`VolumeReference`,
`RacePlan.taperFactor`, `PlanDecision`/rationale, `SPORTS_SCIENCE.md` §8.6/§14.
**Relacionados:** ADR 0038 (progressão de volume), ADR 0041 (mesociclo), ADR 0045 (longão),
ADR 0048 (versões e upgrade), ADR 0050 (volume real), ADRs 0067–0069 (Fatia 0).
**PRD:** `docs/PRD_CORRECOES_AUDITORIA_2026-11.md` — D3, NOVO-SCI-01, §5 (Fatia 0), §6.5.

---

## 1. Contexto

`SPORTS_SCIENCE.md` §8.6 define o taper como redução de **−45 a −55% sobre a média das
3 semanas pré-taper**, com a intensidade mantida em micro-doses (`EV-BOSQUET-2007`,
`EV-MUJIKA-2003`). O código atual usa outra referência: `VolumeProgression` escolhe o
**maior volume forte recente** (`strongMinutes`) das últimas 4 semanas utilizáveis, e o
`WeeklyPlanGenerator` aplica `RacePlan.taperFactor` por sessão (`0,55`; `0,45` nos
últimos 7 dias). A auditoria de 26/11/2026 (`NOVO-SCI-01`) mostrou que essa combinação
pode produzir um corte mais agressivo que a faixa documentada quando a maior semana
recente está acima da média.

A decisão D3 foi aprovada na direção A: o código passa a usar a média das 3 semanas
pré-taper, com fallback explícito e copy que declara a referência. O PRD exige ADR antes
de qualquer mudança de prescrição; esta fatia é **docs-only** e não altera o motor.

## 2. Decisão

### 2.1 Semanas elegíveis e baseline

Para uma semana de taper, a referência é a **média aritmética do volume forte das
3 semanas completas elegíveis imediatamente anteriores** à primeira semana de taper. A
elegibilidade é a mesma da base de volume do §8.3/ADR 0050:

- semana planejada desde a própria segunda-feira (`createdOn == weekStart`);
- semana normal, sem marcação de deload, taper, semana de prova ou recuperação
  (`R-PLAN-04`, `R-PLAN-05`, `R-PLAN-10`, `R-RACE-04`);
- plano existente, com ao menos uma sessão treinável;
- `strongMinutes > 0` provindo de atividade importada (`ACTIVITY`/`GUIDED`), nunca de
  `MANUAL` ou `LEGACY_UNVERIFIED`.

Semanas especiais são excluídas e a janela caminha para trás até reunir 3 semanas
elegíveis ou esgotar o histórico disponível. A média é arredondada para o inteiro mais
próximo (`roundToInt`), mantendo o cálculo determinístico.

O resultado é uma referência pura, com fonte explícita:

| Situação | Baseline | Fonte declarada |
|---|---|---|
| 3 semanas elegíveis | média aritmética das 3 | `AVERAGE_3_WEEKS` |
| 1–2 semanas elegíveis | maior `strongMinutes` entre as disponíveis | `FALLBACK_MAX_RECENT` |
| Nenhuma semana elegível | sem baseline numérica | `NO_HISTORY` |

O fallback para a maior semana recente é deliberadamente conservador: ele reduz menos
que a média quando a maior semana é a referência, e a copy **nomeia** que a média não
foi usada. Sem nenhuma semana elegível, o plano não inventa percentual: o volume segue
a disponibilidade e a confiança é reduzida, como já ocorre sem histórico de volume.

A média do taper é uma referência **própria**. Ela não substitui a base de progressão
semanal do §8.3, que continua sendo o maior volume forte recente para decidir crescimento
fora do taper.

### 2.2 Cálculo do volume

Para semanas de taper, o alvo semanal deixa de ser o `VolumeDecision.minutes` da
progressão normal e passa a ser:

```text
alvo = round(baseline × fator)
fator = 0,55 fora dos últimos 7 dias; 0,45 nos últimos 7 dias
```

Os fatores existentes são mantidos porque produzem a faixa documentada: `0,55` deixa
45% de redução e `0,45` deixa 55%. O alvo é aplicado ao **total semanal** antes da
distribuição final; escalar apenas sessões individuais permitiria que arredondamentos e
pisos saíssem da faixa sem a copy perceber. O encurtamento da qualidade fora do corte
(piso de 25 min) continua sendo uma regra própria: ele não substitui o alvo semanal e
não deve ser aplicado duas vezes.

Regras de borda:

- A disponibilidade continua sendo **teto rígido**: nenhuma sessão pode exceder o slot
  do dia. Se a agenda impedir a faixa, o plano declara o limite da agenda e não afirma
  que cumpriu −45–55%.
- Se os pisos executáveis (fácil, qualidade/micro-dose, longão) impedirem chegar à faixa,
  o plano usa a **semana mínima executável** e declara que o piso impediu o corte
  completo. Ele não remove um estímulo obrigatório nem inventa uma sessão para “fechar”
  o número.
- O taper nunca aumenta o volume em relação à baseline e nunca cria sessão nova; a
  seleção de dias e os pisos continuam os do ADR 0045 e da Fatia 2 (disponibilidade como
  teto).
- Deload, taper e recuperação continuam com precedência sobre a progressão normal; a
  semana de prova usa o mesmo alvo de taper, com o fator profundo.

### 2.3 Copy do fallback

A copy precisa declarar a fonte da baseline, sem chamar de “média” quando o fallback
foi usado:

- **3 semanas:** “Taper: −45–55% sobre a média das 3 semanas anteriores
  (X min/semana).”
- **Fallback:** “Sem 3 semanas completas, o corte usa a maior semana forte recente
  (X min) como referência.”
- **Sem histórico:** “Sem histórico de volume, o taper respeita sua agenda e não
  inventa um corte percentual.”
- **Agenda/piso:** “A agenda limita esta semana a X min” ou “o piso por sessão impede
  o corte completo”, conforme a causa real.

A copy nunca mistura as duas fontes (“média” + “maior semana”) e nunca promete a faixa
quando a agenda ou os pisos a impediram.

### 2.4 Impacto de versão

- `weekly-plan-v9`: a saída do gerador muda para semanas de taper (volume e copy); o
  `PlanUpgrade` (ADR 0048) reavalia planos antigos na abertura, preservando passado,
  `DONE` e `CLOSED`.
- `daily-prescription-v7`: permanece se a prescrição diária apenas consumir o plano
  semanal. Se a implementação passar a recalcular o corte de taper no motor diário, a
  versão também sobe.
- Sem schema, migração, dependência nova ou reescrita de histórico.

## 3. Alternativas avaliadas

| Alternativa | Por que não |
|---|---|
| **B — manter o maior volume recente e atualizar §8.6** | O §8.6 é a fonte de verdade e a média representa melhor a carga pré-taper; a decisão aprovada foi A. Atualizar o documento para justificar o código inverteria a hierarquia. |
| Média das 4 semanas, incluindo deload | Uma semana de descarga puxaria a baseline para baixo e tornaria o taper menos profundo que o documentado. |
| Usar volume planejado em vez de volume forte | O plano é intenção; a base de volume do produto usa evidência forte (`ACTIVITY`/`GUIDED`, ADR 0050). Planejado sem execução não prova dose. |
| Aplicar o percentual só por sessão | Arredondamentos e pisos podem deixar o total fora da faixa; o golden precisa do total semanal. |
| Sem histórico, usar 0 ou um valor fixo | Dado ausente é `null`, nunca `0`; um valor fixo inventaria uma baseline que o atleta não demonstrou. |

## 4. Evidência e força

- `SPORTS_SCIENCE.md` §8.6 e §14 (`R-PLAN-05 v2`): taper de 2 semanas (5k–meia) ou 3
  (maratona), volume −45–55%, intensidade mantida em micro-dose.
- `EV-BOSQUET-2007` (meta-análise de taper) e `EV-MUJIKA-2003` (bases científicas do
  taper): sustentam a redução de volume com manutenção de intensidade.
- A **média de 3 semanas** e o **fallback nomeado** são a operationalização de produto
  aprovada, não uma nova lei fisiológica. A parte de baseline/fallback é
  `LIMITED — convenção de produto`; o princípio de taper é `STRONG`.
- A regra não cria novo `evidenceId`; usa os IDs já catalogados no §13.

## 5. Consequências

- O gerador passa a precisar de uma referência de taper separada da base de progressão;
  a implementação deve manter as duas decisões explícitas para não voltar a confundir
  “maior semana” com “média”.
- `PlanDecision`/rationale ganham motivos nomeados (`TAPER_BASELINE_AVERAGE_3_WEEKS`,
  `TAPER_BASELINE_FALLBACK_MAX`, `TAPER_BASELINE_NO_HISTORY`, `TAPER_FLOOR_LIMITED`) para
  a UI e o Coach explicarem a fonte sem número inventado.
- A copy do plano fica mais longa na primeira semana de taper, mas auditável.
- Usuários com menos de 3 semanas de histórico recebem um corte baseado na maior semana
  recente, com aviso; usuários sem histórico recebem volume por agenda, sem percentual.
- Risco: semanas pequenas com pisos podem não atingir a faixa. Mitigação: declarar a
  semana mínima e nunca sacrificar disponibilidade ou segurança para “fechar” o número.
- Sem schema, sem migração, sem dependência nova.

## 6. Critérios de aceite e golden `G-TAPER-01`

Aceite da futura implementação:

1. Para 3 semanas elegíveis, o volume semanal de taper fica entre 45% e 55% da média
   (antes de eventual teto de agenda), e a copy contém “média das 3 semanas”.
2. Com 1–2 semanas elegíveis, a baseline é a maior semana forte, a copy nomeia o
   fallback e **não** diz “média das 3 semanas”.
3. Sem semana elegível, não há percentual de taper inventado; o volume segue a agenda e
   a copy declara a ausência de histórico.
4. Nenhuma sessão excede a disponibilidade do dia; nenhuma sessão de estímulo fica sem
   estrutura executável.
5. O cenário com pisos impeditivos declara a semana mínima, sem remover estímulo
   obrigatório e sem afirmar a faixa.

### Golden `G-TAPER-01` (definido; executável só na fatia de código)

| Cenário | Entrada sintética | Asserção |
|---|---|---|
| **A — média completa** | Intermediário, 5K em 14 dias; 3 semanas pré-taper com 100, 120 e 140 min fortes (média 120); disponibilidade 5 × 60 min; sem deload/recuperação | Alvo de 66 min fora dos últimos 7 dias e 54 min na semana da prova; a copy contém “média das 3 semanas”, `baseline = 120` e “Semana mínima”; com 5 dias os pisos executáveis entregam 115 min; toda sessão ≤ disponibilidade |
| **B — fallback nomeado** | Mesmo perfil, mas só 2 semanas elegíveis: 100 e 140 min | Baseline = 140 (maior), não 120 (média); taper = 63–77 min; copy contém “maior semana forte recente” e não contém “média das 3 semanas” |
| **C — sem histórico** | Nenhuma semana elegível; disponibilidade 5 × 50 min | Nenhum percentual de taper na copy; volume derivado da agenda; copy contém “sem histórico”; nenhuma sessão > disponibilidade |

O cenário A prova a mudança contra o motor v8: a referência deixa de ser a maior semana
(140) e passa a ser a média (120); o alvo de 66 min fica na copy. Como a agenda de
5 × 60 min tem pisos executáveis, a menor semana possível é 115 min e o plano declara
“Semana mínima” em vez de prometer 54–66 entregues. O cenário B prova o fallback nomeado
(140) sem chamar a referência de média.

**Implementação:** `TaperBaselineResolver` no domínio, alvo semanal em
`WeeklyPlanGenerator`, copy de fonte/mínimo e golden `TaperGoldenTest`; `weekly-plan-v9`.
