# ADR 0065 — Alvo da qualidade segue o tipo de estímulo

**Status:** Aceito e implementado em 25/11/2026.
**Data:** 2026-11-25
**Escopo:** `PrescriptionTargets`, `WeeklyPlanGenerator`, `DailyPrescriptionEngine`,
`TodayViewModel`, `TodayScreen`, `DailyPrescriptionDao/Repository`.
**Relacionados:** ADR 0023 (plano × prescrição), ADR 0044 (oportunidade),
ADR 0047 (catálogo de qualidade), ADR 0048 (versões/upgrade),
ADR 0063/0064 (transparência plano × prescrição).
**PRD:** `docs/PRD_TREINO_REAL.md` FR-410, `docs/SPORTS_SCIENCE.md` §5.2/§8.4.

---

## 1. Contexto

O plano do usuário 0 para 23–29/11/2026 tinha um `INTERVALS` de 40 min
(5 × 3 min, alvo de bloco **I**), mas os alvos persistidos eram
`6:18–6:29/km` (T) e `FC 152–167` (Z3–Z4). O passo a passo dizia “I”; o cartão
de alvos dizia “T/Z3–Z4”. A sessão foi rebaixada para fácil no dia por
prontidão moderada, então o erro não foi executado; ele continuaria latente
no próximo intervalado/fartlek/subida do BUILD.

Causa: `PrescriptionPolicy.qualityTargets` usava a faixa T e Z3–Z4 para **todo**
`QUALITY`, sem conhecer o `QualityKind`. A doc já distingue os estímulos:

- contínuo: T, Z3–Z4;
- intervalado: I, VO2max;
- fartlek/subidas: RPE;
- strides de taper: RPE;
- pace de prova: previsão atual (nunca a meta).

O segundo achado era de transparência: o card do plano mostrava
“Intervalado 40 min — Feito · verificado” mesmo quando a prescrição do dia foi
“Hoje é fácil, 28 min”. O Coach já comparava plano × prescrição; o card do
plano lia apenas o `WeeklyPlan`.

## 2. Decisão

### 2.1 Alvo por tipo de qualidade

`PrescriptionPolicy.targets` passa a receber `qualityKind` (o gerador e o
motor diário já o carregam no `PlannedSession`). A resolução fica:

| `QualityKind` | Pace | FC | RPE | Papel |
|---|---|---|---|---|
| `CONTINUOUS` | T | Z3–Z4 | — | pace principal |
| `INTERVALS` | I | Z5 (piso, sem teto) | — | pace principal |
| `FARTLEK` | — | — | 8 | RPE principal |
| `HILLS` | — | — | 8 | RPE principal |
| `STRIDES_ONLY` | — | — | 8 | RPE principal |
| `RACE_PACE` | previsão atual | — | 8 só sem previsão | pace principal |
| `null` (legado) | T | Z3–Z4 | — | compatibilidade |

Sem calibração, o alvo não inventa pace/FC: contínuo cai em RPE 7 (o valor
histórico) e intervalado/fartlek/subidas em RPE 8. A FC de Z5 é um **piso**,
não um teto: uma repetição de 3 min pode não estabilizar a FC.

### 2.2 UI não fixa “Z3–Z4”

O cartão do Hoje deriva a zona de `targets.zone` (ou de `targets.rpe`) em vez
de escrever “Forte · Z3–Z4” para qualquer qualidade. Assim o intervalado
mostra “Forte · Z5”, o fartlek “Forte · RPE 8” e o contínuo continua
“Forte · Z3–Z4”.

### 2.3 Card do plano mostra a decisão do dia

`TodayUiState` ganha `weekPrescriptions: Map<LocalDate, DailyPrescription>`.
O `TodayViewModel` carrega as últimas decisões da semana por
`DailyPrescriptionRepository.getLatestForWeek` (nova consulta de intervalo no
DAO, sem schema). O card:

- usa a prescrição do dia quando ela difere do plano;
- mostra o título/duração prescritos e a linha
  “Ajustado de <plano> · <duração>”;
- marca `DONE` como “Feito · ajustado” em vez de “Feito · verificado”;
- mantém o plano original visível como contexto.

Para hoje, a prescrição da decisão viva tem prioridade sobre a cópia
persistida, evitando corrida entre a geração e a leitura da semana.

### 2.4 Versões

- `weekly-plan-v8`: os alvos gerados mudaram; um plano v7 é reavaliado pelo
  `PlanUpgrade` na abertura (passado/`DONE` preservado).
- `daily-prescription-v7`: a prescrição do dia e o fallback sem alvo usam a
  mesma tabela.

O histórico persistido não é reescrito: o intervalado de 24/11 continua com
os alvos T que foram gerados e a prescrição fácil de 28 min que foi executada.
Planos novos usam a tabela correta.

## 3. Alternativas avaliadas

1. **Manter T para tudo e mudar o rótulo para “limiar”.** Rejeitada: a doc e o
   catálogo prometem VO2max; o título “Intervalado” e o alvo I ficariam
   mentindo.
2. **Usar RPE para todo intervalado.** Rejeitada quando há calibração: a doc
   pede pace I e a FC como guarda-corpo; RPE fica para a ausência de calibração.
3. **Só corrigir o card e deixar o alvo.** Rejeitada: o produto continuaria
   prescrevendo o estímulo errado, só que de forma transparente.
4. **Recalcular o passado.** Rejeitada: a prescrição e a execução são
   história; `R-PRESC-03` e ADR 0024 não permitem reescrever.

## 4. Consequências

- O intervalado passa a prescrever I (`5:48–5:59/km` no perfil atual) e Z5 como
  piso; o fartlek/subida/strides passam a ser RPE 8; o contínuo permanece T.
- O card do plano deixa de esconder o rebaixamento por prontidão/carga.
- A sessão executada em 24/11 continua registrada como fácil de 28 min; o card
  passa a dizer “Ajustado de Intervalado · 40 min” e “Feito · ajustado”.
- Sem migração, sem dependência nova, sem mudança de volume, prontidão ou
  guardrails de segurança.
- O upgrade v7→v8 pode exibir “Plano atualizado: nova versão do coach” na
  próxima abertura; é o caminho previsto no ADR 0048.

## 5. Testes e gates

- `TargetPrecedenceTest`: T para contínuo, I/Z5 para intervalado, RPE 8 para
  fartlek/subida/strides, RPE 8 para pace de prova sem previsão e fallback sem
  calibração.
- `WeeklyPlanGeneratorTest`: micro-dose de taper é RPE (não T), versão v8.
- `DailyPrescriptionEngineTest`: versão v7.
- `DailyPrescriptionRepositoryTest`: última versão por dia na janela semanal.
- `TodayPlanTest`: card mostra “Hoje é fácil” 28 min, “Ajustado de Intervalado
  · 40 min” e “Feito · ajustado” no lugar de “Intervalado 40 min”.
- Gates: `:core:domain:test`, `:core:data:testDebugUnitTest`,
  `:app:testDebugUnitTest`, `spotlessCheck`, `assembleDebug`, `koverVerify`.
