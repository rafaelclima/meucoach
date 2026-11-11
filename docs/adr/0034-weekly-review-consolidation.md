# ADR 0034 — Revisão semanal determinística

**Status:** Aceito em 2026-11-11; fatias 1 e 2 implementadas na mesma data (domínio puro + card em Tendências)
**Data:** 2026-11-11
**Escopo:** FR-011 (`core:domain/review` + card em Tendências); sem migração, sem dependência nova, sem LLM
**Relacionados:** ADR 0023 (prescrição diária), ADR 0024 (conclusão/evidência), ADR 0026 (cobertura de carga), ADR 0028 (replanejamento), ADR 0030 (evento de prova), ADR 0033 (planejado × executado), PRD §6 FR-011 (BL-020)

---

## 1. Contexto

O FR-011 pede que a semana seja consolidada em uma leitura curta e determinística:
adesão com evidência, volume contra o plano, distribuição de intensidade,
prontidão, sono, sinais de alerta e 1–3 observações acionáveis. Hoje Tendências
mostra dados, mas não os interpreta; o FR-010 fez a leitura de uma sessão, não da
semana.

A consolidação pode ser construída sem uma segunda decisão de treino, porque tudo
já existe calculado e persistido:

- `PlanCompletionCalculator` devolve adesão por semana, com evidência forte
  (`ACTIVITY`/`GUIDED`), manual e legada (ADR 0024);
- `IntensityDistributionCalculator` devolve tempo em Z1–Z2, Z3 e Z4–Z5 por
  semana medida (`R-ZONE-02`/`R-PLAN-06`);
- `ReadinessHistoryCalculator` reconstrói a prontidão dia a dia com a confiança
  de cada data (`R-READY-01/02`);
- `DailyMetrics` guarda sono; `DailyCheckIn` guarda doença/lesão;
- o evento de prova já exclui datas da adesão (ADR 0030).

## 2. Decisão

Uma calculadora pura em `core:domain/review` consolida **somente entradas já
calculadas** e devolve fatos e observações, nunca um julgamento de qualidade.

| Decisão | Escolha |
|---|---|
| **Janela** | Semana civil segunda–domingo. O cálculo exige `weekStart` na segunda e `today >= weekStart`; dias depois de `today` são ignorados. No app, a revisão cobre a **última semana fechada**: uma semana em curso viraria julgamento de adesão parcial e o Hoje já cobre o dia atual. |
| **Entradas** | Adesão (`PlanCompletionWeek`), volume planejado/executado em minutos, `WeeklyIntensity` + nº de sessões, prontidão e sono por data, check-ins. Nenhum repositório, alvo ou número é recalculado. |
| **Ausência** | Cada seção é opcional e só existe com dado. `percentOfPlan` e os ratios de adesão são `null` sem denominador; nada vira zero. |
| **Estados** | `AVAILABLE` (há plano), `EMPTY_NO_PLAN` (há corrida/saúde, sem plano) e `EMPTY_NO_DATA` (nada a consolidar). O estado vazio não inventa adesão nem observação. |
| **Adesão** | Reportada como fato: planejadas, feitas, verificadas, manuais, legadas e puladas. Não há nota, streak ou comparação entre semanas. |
| **Volume** | `executado / planejado`, sem tolerância embutida na seção; a observação usa uma banda de produto de 70–120%. |
| **Intensidade** | Só é julgada contra o alvo de 80% fácil quando há ≥ 3 sessões medidas; abaixo disso o split seria ruído. |
| **Sono** | Noite curta = < 390 min (6 h 30), o limiar de bloqueio de progressão de volume de `R-READY-04`; a observação exige ≥ 3 noites curtas na semana. |
| **Prontidão** | A observação exige ≥ 3 dias vermelhos (`R-READY-03`); a média e a cobertura aparecem separadas como contexto. |
| **Segurança** | Doença/lesão em qualquer check-in da semana vem primeiro na fila de observações e cita `R-SAFE-01`. |
| **Observações** | No máximo 3, em ordem determinística: segurança → sono → prontidão → adesão → volume → intensidade. Sem alertas e com plano, uma observação `NO_ALERTS`. |
| **Confiança** | Cobertura, não qualidade: 3+ blocos disponíveis (adesão, intensidade, ≥ 3 dias de prontidão, ≥ 3 noites) = alta; 2 = média; 0–1 = baixa. |
| **Persistência** | Nada é persistido; a revisão é derivada como o briefing e a comparação. |
| **Copy** | O domínio entrega estrutura e IDs; `WeeklyReviewFormat` produz o texto pt-BR (sem `R-`/`EV-` na superfície) e o card só renderiza o que existe. |

Regra de catálogo: `R-REVIEW-01` (política de produto, `STRONG como política de
produto/copy`), registrada em `SPORTS_SCIENCE.md` §14. As observações fisiológicas
reusam regras e evidências existentes (`R-READY-03`, `R-READY-04`, `R-SAFE-01`,
`R-PLAN-06`).

## 3. Por que descritiva e sem LLM

O PRD pede observações “reproduzíveis”. Texto gerado por LLM não é reproduzível e
não pode citar regra/evidência de forma auditável (ADR 0004). As observações são,
portanto, uma seleção determinística de fatos com `ruleId`, `evidenceId` e
confiança; a UI (fatia 2) escolhe a frase para cada tipo.

A revisão também não prescreve: ela não altera a semana seguinte, não repõe carga
perdida e não substitui o `DailyPrescriptionEngine` (ADR 0023). “Prévia da próxima
semana” fica para a fatia 2, quando existir plano futuro persistido — inventar uma
prévia sem plano seria a mesma falha que o FR-010 evitou com o treino livre.

## 4. Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Recalcular adesão/intensidade dentro da revisão | Duplicaria calculadora auditada e poderia divergir de Tendências/Hoje. |
| Gerar observações na UI | Espalharia regra e confiança fora do domínio e quebraria o teste de determinismo. |
| Usar o ACWR de hoje para revisar qualquer semana | O ACWR de hoje não é o da semana revisada; rotular assim seria um número falso. A série por semana fica para uma evolução. |
| Tratar semana sem plano como falha de adesão | Sem denominador não há adesão; o estado `EMPTY_NO_PLAN` é o honesto. |
| Exibir mais de 3 observações | O PRD pede 1–3; uma lista longa vira relatório e perde ação. |
| Persistir a revisão | Duplicaria dado derivado e criaria histórico para o reset limpar. |

## 5. Consequências

- **Domínio:** `WeeklyReview`/`WeeklyAdherence`/`WeeklyVolume`/`WeeklyReadiness`/
  `WeeklySleep`/`WeeklyReviewObservation` e `WeeklyReviewCalculator`, sem
  dependência de Android ou Room.
- **App:** `TrendsViewModel` monta as entradas já carregadas (adesão de 8 semanas,
  distribuição de intensidade, prontidão de 14 dias, métricas e check-ins) e
  expõe `weeklyReview` no `TrendsUiState`. `CheckInRepository.getAll()` foi
  adicionado para a leitura pontual; nenhuma migração.
- **UI:** card “Revisão da semana” em Tendências, abaixo do resumo de carga e antes
  do filtro de período. Mostra adesão, volume, zonas, prontidão, sono e até 3
  observações; estados `AVAILABLE`, `EMPTY_NO_PLAN` e `EMPTY_NO_DATA`; sem ID
  técnico, streak ou CTA. Goldens dark/light/estreito.
- **Dados:** nenhuma migração; export/reset inalterados.
- **Limites:** a revisão é semanal e não cobre análise por split, monotonia de
  carga por semana, prévia da próxima semana nem observações interpretadas de
  “próximo passo”.

## 6. Testes

`WeeklyReviewCalculatorTest` (27 testes) e `WeeklyReviewTest` (8 testes): semana
completa, adesão parcial/nenhuma, semana sem sessões planejadas, ausência de plano,
ausência total de dados, doença primeiro com limite de 3 observações, sono curto
repetido (e o caso de 2 noites), prontidão vermelha repetida (e o caso de 2 dias),
intensidade acima do alvo e o piso de 3 sessões, volume abaixo/acima e dentro da
banda, `NO_ALERTS`, determinismo, filtro de datas fora da semana e depois de hoje,
deduplicação de métricas por data e as invariantes dos modelos (adesão, volume,
prontidão, sono, janela, intensidade, limite de observações e estado vazio).

No app, `WeeklyReviewFormatTest` (9 testes) trava a copy, os números, o
singular/plural e a ausência de IDs; `TrendsViewModelTest` (+3) cobre a última
semana fechada, a consolidação e a semana sem plano; `TrendsScreenTest` (+4) cobre
o card, os dois estados vazios, a ausência de `R-`/`EV-` e a fonte 200%;
`WeeklyReviewScreenshotTest` produz os goldens dark/light/estreito.

## 7. Relação com o PRD

| Requisito | Como este ADR atende |
|---|---|
| **FR-011** | Card semanal com adesão (com evidência), volume vs. plano, intensidade por tempo, prontidão, sono, sinais de alerta e 1–3 observações que citam regras; semana sem plano/dados com estado vazio honesto; observações determinísticas sem LLM; testes de cálculo e de UI com goldens. A prévia da próxima semana e a interpretação de “próximo passo” ficam fora desta entrega. |
| **NFR-001** | Determinístico, sem LLM, com regra, evidência e confiança. |
| **BL-020** | Consolidação da semana em fatos acionáveis, sem culpa. |

## 8. Referências

- `docs/PRD_COACH_CORRIDA.md` §6 (FR-011) e §8 (BL-020)
- `docs/SPORTS_SCIENCE.md` §14 (`R-REVIEW-01`, `R-READY-03`, `R-READY-04`, `R-SAFE-01`, `R-PLAN-06`)
- `docs/adr/0024-completion-evidence.md`
- `docs/adr/0026-load-coverage-quality-eligibility.md`
- `docs/adr/0033-planned-vs-executed-comparison.md`
- `core/domain/src/main/kotlin/com/meucoach/core/domain/review/WeeklyReview.kt`
