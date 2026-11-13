# PLANO FR-013 — Carga subjetiva (sRPE) — v0.1

**Data:** 13/11/2026 · **Estado:** desenho proposto, aguardando decisão do usuário 0
**Precedentes:** [PRD §FR-013/BL-022](PRD_COACH_CORRIDA.md) · ADR 0024 (evidência de conclusão) ·
ADR 0026 (limiares de carga) · ADR 0038 (progressão de volume) · ADR 0033 (comparação planejado × executado)
**Código atual:** `TrainingLoadCalculator.srpe()` existe e é testada, mas nenhuma execução alimenta o motor de carga.

> **Não implementar antes do usuário 0 aprovar as decisões D1–D9.** Cada decisão
> traz opções com consequência explícita; a recomendação é a leitura do
> desenho, não a palavra final. O gate de aprovação vale como nas entregas
> anteriores (C3-07, D6-B).

---

## 1. O problema, em uma frase

Hoje o motor de carga só enxerga **corridas importadas** — e, dentro delas, só
usa FC (`TRIMP`) ou pace calibrado (`rTSS`). Tudo o que o usuário registra
manualmente (“fiz sem registro”, 40 min, RPE 6) conta adesão e não conta
**nada** na série de carga: para o PMC, aquele treino não aconteceu.

Consequências para o usuário 0 e para qualquer iniciante sem FC confiável:

1. Dias de treino manual viram `daysWithoutData` na cobertura de 28 dias.
2. `LoadDataState` fica `INSUFFICIENT`/`STALE` → ACWR `null` → treino forte
   bloqueado por falta de dados, não por prontidão ruim.
3. A mensagem “seus dados de carga estão desatualizados” aparece mesmo
   quando o usuário treinou e registrou honestamente.

O sRPE de Foster (`RPE 0–10 × minutos`, `EV-FOSTER-2001`, já no catálogo) é o
método de referência para monitorar carga **sem FC**. O PRD (FR-013) já define
o papel: *usar sRPE como fallback quando TRIMP/rTSS não estiverem disponíveis;
nunca sobrepor FC confiável*.

## 2. Onde o sRPE se encaixa no motor atual

`TrainingLoadEngine.calculate()` seleciona **uma métrica por dia** (R-LOAD-01,
nunca somar métricas no mesmo dia), hoje em dois níveis:

1. `TRIMP` — se o dia tem atividade com FC dentro de repouso..máxima e o perfil tem RHR/FM.
2. `rTSS` — se o dia tem atividade com pace e o perfil tem pace de limiar calibrado.
3. *(ausente)* — dia fica com `load = 0.0`, `metric = null`.

A proposta é um terceiro nível, **sem trocar a ordem**:

3. `SRPE` — se o dia tem execução com RPE registrado e sem FC/pace que já
   satisfaça os níveis 1–2.

Dados que sustentam a proposta de encaixe:

- A série já mistura métricas entre dias (TRIMP num dia, rTSS noutro); sRPE é
  a extensão do mesmo padrão, não uma exceção nova.
- A coluna `session_executions.rpe` já existe no schema v17 (nullable, 1–10);
  `MANUAL` já exige RPE (ADR 0024). **Nenhuma migração.**
- A carga não é persistida em Room; é recomputada a cada leitura. Mudar o
  motor não toca schema.

## 3. Decisões

### D1 — Posição na cadeia de carga

| Opção | Consequência |
|---|---|
| **A (recomendada)** | sRPE como 3º nível: `TRIMP > rTSS > SRPE`. FC/pace sempre vencem. |
| B | sRPE só para exibição; o motor continua ignorando. Não resolve o bloqueio de treino forte por falta de dados. |
| C | sRPE combina com TRIMP/rTSS no mesmo dia. Violaria o R-LOAD-01 e dobraria carga. Descartada. |

**A** segue o PRD (BL-022) e o princípio “nunca inventar”. Consequência direta:
um dia com corrida importada com FC **nunca** muda de métrica por causa de sRPE.

### D2 — Quais execuções alimentam sRPE

| Opção | Consequência |
|---|---|
| **A (recomendada)** | Execuções com `rpe != null` e `durationSec > 0` que não tenham atividade com FC/pace: `MANUAL` (RPE obrigatório hoje) e `ACTIVITY`/`GUIDED` quando o usuário registra RPE e a corrida vinculada não tem FC. |
| B | Só `MANUAL`. Simples, mas deixa fora o caso “corrida de esteira/sem FC vinculada com RPE” — o PRD lista este caso explicitamente. |
| C | Qualquer RPE pós-treino, mesmo com FC no arquivo. Criaria sobrecarga subjetiva sobre carga objetiva. Descartada. |

Em **A**, a regra por execução é: `LoadActivity` da corrida com FC satisfaz
nível 1–2 no dia ⇒ sRPE ignorado naquele dia; corrida sem FC + execução com
RPE ⇒ sRPE. **Nunca os dois no mesmo dia.**

Pergunta para você: **coletar RPE no fechamento com corrida vinculada**
(hoje o diálogo só coleta duração/RPE no registro manual)? É opcional
(“não sei informar”) e sem culpa se ficar vazio. *Recomendo sim.*

### D3 — sRPE conta como “dia com carga” na cobertura de 28 dias

| Opção | Consequência |
|---|---|
| **A (recomendada)** | Conta. `daysWithLoad` inclui dias `SRPE`. O `LoadSnapshot` ganha `subjectiveDaysInWindow` (ou fração) para o motor de prescrição saber quando a carga é subjetiva. |
| B | Não conta. O usuário que registra só manualmente fica eternamente `INSUFFICIENT` — o sRPE viraria número decorativo, contrariando o objetivo do FR-013. |

Em **A**, `MIN_DAYS_WITH_LOAD = 8` passa a poder ser atingido com sRPE, e o
ACWR pode sair com contribuição subjetiva. A defesa está em D5: **sRPE pode
reduzir carga, nunca liberar aumento** — e a fração subjetiva é explicitada.

### D4 — Frescor da fonte (fonte dual)

| Opção | Consequência |
|---|---|
| **A (recomendada)** | `ActivitySourceStatus` passa a considerar **duas fontes**: a última importação **ou** a última execução com RPE, com o mesmo teto de 14 dias. Sem nenhuma das duas em 14 dias, `STALE` como hoje. |
| B | Frescor continua só de importação. Registro manual não “aquece” a fonte: quem registra manual fica `STALE` mesmo treinando todo dia — sRPE inócuo. |

**A** é coerente com o ADR 0026: frescor protege contra “não sei o que
aconteceu”. O usuário que registra RPE está declarando o que aconteceu;
declarar com esforço percebido é menos preciso, mas é declarar.

### D5 — O que sRPE pode e não pode liberar (a trava importante)

| Decisão | Com sRPE |
|---|---|
| Guardrails de redução (descanso por doença, REDUCE, DOWNGRADE) | **Funcionam normalmente** — sRPE alto conta como carga para defender o atleta. |
| ACWR alto → sem aumento de carga | **Funciona** — dado subjetivo alto nunca é permissão de aumentar. |
| Progressão de volume +6% (ADR 0038: “carga conhecida e ACWR 0,8–1,3”) | **Não muda**: a trava de carga da progressão passa a exigir carga **objetiva** (`TRIMP`/`rTSS`) conhecida. sRPE-only não libera aumento de volume. |
| Liberação de 2ª qualidade | **Não muda**: já exige evidência forte (≥ 80% da semana anterior com `ACTIVITY`/`GUIDED`) — regra do ADR 0024/0038, intocada. |

Esta é a assimetria central: **dado subjetivo justifica conservação, nunca
aceleração.** A regra `R-PLAN-01` ganha uma condição adicional documentada no
ADR (`carga objetiva conhecida`), sem mudar o resto do ADR 0038.

### D6 — Regra/evidência no catálogo

Nova regra `R-LOAD-04` — “Carga estimada por esforço percebido quando não há
FC ou ritmo; subjetiva e nunca libera aumento” (LIMITED, `EV-FOSTER-2001`).
Entra no sheet “Por que este treino?” quando o dia usa `SRPE`, e no “Dados
ausentes” a FC/pace do treino aparece quando é o caso. Copy na UI sem jargão:
**“carga estimada pelo esforço que você sentiu”**.

### D7 — O que **não** muda

- **Volume e adesão:** `MANUAL` segue contando adesão e nunca entra na base de
  volume (ADR 0024/0038 intactos). sRPE só toca a série de carga.
- **Comparação planejado × executado (FR-010/ADR 0033):** intocada — já usa RPE.
- **Revisão semanal (FR-011):** a intensidade medida segue vindo dos samples
  objetivos; a revisão pode *citar* que a carga da semana inclui dias subjetivos
  (nota factual), sem recalcular zonas.
- **Prontidão:** sem relação com sRPE (já usa check-in/HRV/RHR).
- **Troca e replanejamento:** guardrails iguais; o que muda é só o valor de
  carga que entra neles.

### D8 — Versionamento do motor

`LoadMetric.SRPE` entra no enum e o comportamento do `TrainingLoadEngine` muda
→ a versão da prescrição sobe (`daily-prescription-v5`). Prescrições persistidas
não são reescritas; a nova versão vale para novos cálculos (mesmo padrão das
migrações de versão anteriores). `weekly-plan` não muda (a progressão usa
`LoadDataState`, que passa a ter a condição objetiva de D5 — documentada no
ADR, sem mudança de `ENGINE_VERSION` do gerador? **a decidir na implementação**
com teste de hash).

### D9 — UI e coleção

1. **Coleção de RPE:** o diálogo de conclusão ganha o campo RPE (opcional) no
   fechamento com corrida vinculada e no registro manual (que já tem).
2. **Card de carga (Hoje):** dias `SRPE` recebem a mesma leitura dos demais
   (Base/Fadiga/Balanço etc.) com a nota “carga estimada pelo esforço que você
   sentiu” quando o dia atual é `SRPE`.
3. **Tendências:** PMC sem mudança estrutural; tooltip do dia pode indicar a
   métrica (já existe unidade por eixo; nada de série separada).
4. **Detalhe da atividade:** RPE registrado aparece no resumo (hoje aparece
   para `MANUAL`); “carga” no insight cita `R-LOAD-04` quando for o caso.

## 4. Plano de implementação em fatias (após aprovação)

| Fatia | Escopo | Gate |
|---|---|---|
| **1 — Motor** | `LoadMetric.SRPE`, entrada de `LoadExecution(rpe, durationSec)` no `TrainingLoadEngine`, fonte dual de frescor, `subjectiveDaysInWindow`, condição objetiva da progressão (D5). TDD em `core:domain`. | `check` + Kover + goldens de carga |
| **2 — Coleção** | RPE opcional no fechamento com corrida vinculada; persistência em `SessionExecution.rpe`. | Testes de ViewModel/Room; sem migração |
| **3 — UI/copy** | `R-LOAD-04` no catálogo, nota no card de carga, RPE no detalhe, explicação com dado ausente. | Goldens + testes de copy |

Cada fatia fecha com `./gradlew check :app:assembleDebug` verde e docs
(`SPORTS_SCIENCE`, `DATA_MODEL` §3.9, `COMMUNICATION`, `DECISIONS`).

## 5. Critérios de aceite (pós-implementação)

1. Dia só-manual com RPE → série de carga sobe, card explica a fonte, e a
   prescrição cita `R-LOAD-04` com confiança baixa.
2. Corrida importada com FC no mesmo dia → métrica continua `TRIMP` (nada muda).
3. Semana toda manual com ACWR subjetivo bom → **não** libera +6% de volume
   (trava objetiva de D5); prontidão vermelha continua vencendo tudo.
4. Sem dados há 14+ dias (nem import, nem execução com RPE) → `STALE` como hoje.
5. Export JSON e reset continuam coerentes (nada novo persistido além de RPE,
   que já é coluna existente).

## 6. Riscos declarados

- **Sensibilidade do RPE:** iniciantes superestimam esforço em treinos fáceis —
  mitigado por não usar sRPE para liberar aumento e por confiança baixa.
- **Mistura de unidades na série:** CTL/ATL misturando TRIMP e sRPE distorce
  tendências quando a proporção muda bruscamente — mitigado por expor a fração
  subjetiva e por documentar a limitação no `SPORTS_SCIENCE` §“Carga”.
- **Gaming involuntário do ACWR:** ninguém ganha nada registrando RPE alto
  (não libera nada) ou baixo (reduz a própria carga) — incentivo alinhado.

---

**Aprovação do usuário 0:** ☐ D1–D9 aprovados como recomendados ☐ com ajustes:
____________
**Próximo passo após aprovação:** ADR (`0039-load-from-subjective-srpe.md`) + fatia 1.
