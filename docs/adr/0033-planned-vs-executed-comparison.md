# ADR 0033 — Comparação planejado × executado

**Status:** Aceito em 2026-11-11; implementado na mesma data (FR-010 no detalhe da atividade)
**Data:** 2026-11-11
**Escopo:** FR-010, detalhe da atividade; sem migração, sem dependência nova
**Relacionados:** ADR 0023 (prescrição diária), ADR 0024 (conclusão/evidência), ADR 0026 (cobertura), ADR 0028 (replanejamento), PRD §6 FR-010/FR-011 (BL-019)

---

## 1. Contexto

O loop do produto prescreve, executa e registra, mas não fecha o “aprender”. Uma
corrida vinculada a um treino aparece no detalhe apenas com métricas da atividade:
o usuário não vê o alvo que recebeu nem a diferença entre o planejado e o feito.
O `PRD_COACH_CORRIDA.md` §6 (FR-010) pede essa comparação e a coloca como
dependência da revisão semanal (FR-011).

O app já tem tudo o que a comparação precisa, sem uma segunda decisão de treino:

- `daily_prescriptions` guarda a prescrição do dia, única e versionada (ADR 0023);
- `session_executions` e `planned_session.activityId` dizem qual corrida é a
  evidência do treino (ADR 0024);
- as metas de pace, FC, distância e RPE já vêm persistidas em `PrescriptionTargets`;
- o vínculo é 1:1 e a lixeira suspende/restaura a evidência.

## 2. Decisão

Uma calculadora pura em `core:domain/feedback` compara a corrida com o **alvo
prescrito do dia** e devolve números e direção, nunca qualidade.

| Decisão | Escolha |
|---|---|
| **Fonte do alvo** | `DailyPrescription` da data (o que o atleta recebeu). Sem prescrição persistida, a sessão da semana é fallback — apenas para um dia já vinculado. |
| **Vínculo** | A comparação só existe quando a atividade é a evidência do treino (`SessionExecution` ou `planned_session.activityId`). Fora disso, a corrida é **treino livre** e nenhum alvo é inventado. |
| **Métrica** | Duração, distância, pace, FC média e RPE. Cada linha só aparece quando **os dois lados existem**; dado ausente não vira zero. |
| **Tolerância** | 10% para duração e distância; faixa de pace/FC é a prescrita; RPE tolera 1 ponto. |
| **Direção** | `WITHIN`, `ABOVE`, `BELOW` — “acima” significa mais duração/distância, pace mais rápido, FC/RPE mais altos. Direção não é elogio nem alerta. |
| **Confiança** | Alta com ≥ 3 métricas comparadas, média com 2, baixa com 0–1. Treino livre é sempre baixa (não há alvo). |
| **Copy** | Só diferenças aparecem; métricas dentro do alvo são resumidas em uma linha. Nenhum `R-`/`EV-` na superfície comum. |
| **Persistência** | Nada é persistido. A comparação é derivada a cada leitura, como o briefing (C3-03/04). |

Regra de catálogo: `R-FEEDBACK-01` (produto/copy), com `EV-DANIELS-2014` e
`EV-FRIEL-2018` como origem das faixas de pace e FC. A comparação não cria regra
fisiológica nova e não altera prescrição, plano, briefing ou confiança do motor.

## 3. Por que descritiva

O PRD fala em “destacar diferenças relevantes e sugerir próximo passo”. Nesta
entrega, o “próximo passo” não virou recomendação de treino: o app não pode
prescrever fora do `DailyPrescriptionEngine` (ADR 0023). A comparação descreve o
que aconteceu e deixa a decisão do próximo dia com o motor, que já recebe
prontidão, carga e check-in. Interpretar diferenças como erro, sucesso ou
“treino perdido” seria uma regra fisiológica sem evidência.

Consequências diretas:

- uma corrida mais rápida que a faixa é “ritmo mais rápido que o alvo”, não
  “treino forte demais”;
- uma corrida mais curta é “duração abaixo do planejado”, não “falha”;
- sem alvo (treino livre, dia sem plano, prescrição `REST`), aparecem apenas os
  valores reais e a frase “sem plano vinculado”;
- não há streak, culpa ou comparação entre semanas.

## 4. Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Recalcular a prescrição no detalhe | Duplicaria a autoridade do dia e poderia divergir do que o atleta viu. |
| Comparar com a intenção da semana, ignorando redução/`DOWNGRADE` | Puniria o atleta por um ajuste que o próprio app fez (ADR 0023). |
| Julgar qualidade com “bom/ruim” | Exigiria regra e evidência novas para uma leitura que o motor já faz. |
| Persistir a comparação | Duplicaria dados derivados e criaria histórico que o reset teria de limpar; a versão do engine já garante determinismo. |
| Comparar toda corrida com o plano da semana | Uma corrida não vinculada não é evidência de nada; o PRD pede “treino livre” sem alvo. |

## 5. Consequências

- **Domínio:** `SessionComparison`/`ComparisonPlan`/`SessionComparisonCalculator`,
  sem dependência de Android ou Room.
- **App:** `GetSessionComparison` lê vínculo, prescrição e fallback da semana;
  `ActivityDetailViewModel` só transporta o resultado; a tela não calcula.
- **UI:** card “Planejado × executado” após as métricas, com confiança, linhas
  `planejado → feito`, notas de diferença e estado de treino livre. Nenhum ID
  técnico na superfície.
- **Dados:** nenhuma migração; export/reset inalterados.
- **Limites:** a média de FC/pace pode esconder trechos fora do alvo; a leitura é
  de sessão, não de splits. Comparar splits fica para uma evolução com evidência.

## 6. Testes

- `SessionComparisonCalculatorTest`: tolerância inclusiva de 10%, direções de
  pace/FC/RPE, FC sem teto, ausência de um dos lados, pace derivado de
  distância/tempo, treino livre, plano a partir de prescrição/sessão e `REST`.
- `GetSessionComparisonTest`: prescrição preferida à sessão, fallback da semana,
  vínculo por execução, corrida não vinculada em dia com plano e corrida sem plano.
- `ActivityComparisonFormatTest`: linhas, omissão de dado ausente, notas factuais,
  resumo “tudo dentro” e treino livre com “—”.
- `ActivityDetailScreenTest`: card no detalhe, ausência de IDs e estado de treino
  livre sem alvo.
- Goldens: `activity-comparison-{dark,light}` e `activity-comparison-free-dark`.

## 7. Relação com o PRD

| Requisito | Como este ADR atende |
|---|---|
| **FR-010** | Atividade vinculada mostra alvo/realizado e diferenças; sem plano mostra “treino livre”; dado ausente aparece como ausente; testes de cálculo e UI. |
| **FR-011 (relacionado)** | Entrega a base de “planejado × executado” que a revisão semanal vai consolidar; não entrega o card semanal. |
| **NFR-001** | Determinístico, com regra, evidência e confiança; sem LLM. |
| **BL-019** | Feedback pós-treino com conclusão acionável, sem culpa. |

Pendente fora desta entrega: sugestão interpretada de próximo passo, comparação
por splits e o card semanal da FR-011.

## 8. Referências

- `docs/PRD_COACH_CORRIDA.md` §6 (FR-010, FR-011) e §8 (BL-019/BL-020)
- `docs/SPORTS_SCIENCE.md` §14 (`R-FEEDBACK-01`)
- `docs/COMMUNICATION.md` §6.12
- `docs/adr/0023-weekly-plan-vs-daily-prescription.md`
- `docs/adr/0024-completion-evidence.md`
