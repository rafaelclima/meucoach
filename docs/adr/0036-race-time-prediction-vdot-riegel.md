# ADR 0036 — Previsão de prova por VDOT/Riegel (D6-B)

**Status:** Aceito e implementado em 2026-11-11; decisões P1–P7 aprovadas pelo usuário 0 na mesma data
**Data:** 2026-11-11
**Escopo:** `R-RACE-03`, previsão de tempo de prova nos cards de prova do Perfil e do Hoje; `core:domain` puro; Room 17 para proveniência da calibração
**Relacionados:** ADR 0032 (D6/D6-B, referência aritmética da meta), ADR 0035 (Tier B da calibração), `SPORTS_SCIENCE.md` §8.10, PRD FR-208/FR-210, `EV-DANIELS-2014`, `EV-RIEGEL-1981`

---

## 1. Contexto

O ADR 0032 entregou apenas a **referência aritmética da meta**: pace médio, faixa
±3% e passagens derivados de `targetTimeSec`. A previsão por VDOT/Riegel (D6-B)
ficou explicitamente fora, com `R-RACE-03` documentada como `LIMITED` e sem
implementação (`SPORTS_SCIENCE.md` §8.10).

Hoje o app já tem VDOT confiável em dois níveis:

- **Tier A:** prova ou TT recente (≤ 56 dias), confiança `HIGH`, calculado por
  `CalibrationCalculator`;
- **Tier B:** melhor esforço sustentado de 20–35 min, confiança `MEDIUM`,
  validado em dados reais em 11/11/2026 (~3% acima do VDOT de prova do usuário 0).

O usuário vê a meta no card de prova, mas não vê o que o histórico dele sugere
para aquela distância. A previsão responde “o que é realista hoje?” sem virar
promessa, prescrição ou garantia. O risco é contaminar plano/treino ou exibir um
número com aparência de certeza.

## 2. Decisão proposta

| Decisão | Proposta |
|---|---|
| **P1 — Fonte** | A previsão só existe a partir de **calibração explícita**: Tier A (`HIGH`) ou Tier B (`MEDIUM`). Sem VDOT, sem previsão (`null`, nunca 0). Nada é inferido automaticamente de qualquer corrida. |
| **P2 — Proveniência** | Persistir no perfil a origem e a data da calibração (`vdotSource`: `PERFORMANCE`/`TRAINING_ESTIMATE`, `vdotCalibratedAt`, e a performance de referência quando Tier A). Room 17 com colunas nullable; legado fica `NULL` e não gera previsão até recalibrar. |
| **P3 — Método principal** | **VDOT:** inverter a equação de Daniels para a distância-alvo (busca binária monotônica no tempo), usando o VDOT calibrado. É determinístico e reaproveita as fórmulas já testadas em `CalibrationCalculator`. |
| **P4 — Riegel** | Secundário e opcional: `T2 = T1 · (D2/D1)^1,06`, só quando existe performance de referência Tier A com distância e tempo conhecidos. Nunca substitui o VDOT; serve como **checagem de faixa** e só para 5k–meia. Maratona não é extrapolada por Riegel a partir de referência < 10 km. |
| **P5 — Onde aparece** | Apenas nos cards de prova do **Perfil** e do **Hoje**, como “Previsão de referência”, ao lado da meta. Nunca em `DailyPrescription`, `PlannedSession`, `WeeklyPlanGenerator`, `PlanReplanner`, `SessionSwapper`, briefing ou alvos de treino. |
| **P6 — Faixa e confiança** | Ponto previsto + faixa: ±3% para Tier A e ±5% para Tier B, convenção de produto documentada. A confiança herda a calibração (`HIGH`/`MEDIUM`) e aparece no card; sem intervalo inventado quando não há VDOT. |
| **P7 — Limites** | Previsão só para 5k, 10k, meia e maratona, em evento `PLANNED`. Prova passada/concluída não gera previsão retroativa. Calibração com mais de 56 dias não gera número: o card pede recalibrar. Dado ausente continua ausente. |

### 2.1 Regra e evidência

| ID | Texto | Evidência | Confiança |
|---|---|---|---|
| `R-RACE-03` | Previsão de tempo por VDOT calibrado, com faixa e Riegel como checagem quando há performance de referência | `EV-DANIELS-2014`, `EV-RIEGEL-1981` | `LIMITED` — referência, nunca promessa |

A previsão não altera plano, taper, prescrição ou zonas. Testes de independência
provam que o plano e a prescrição são idênticos com e sem `RacePrediction`.

### 2.2 Copy

- “Previsão de referência a partir da sua calibração.”
- “Faixa estimada; não é promessa nem garantia.”
- Tier B: “Estimativa pelos treinos; confirme com um teste de 30 min.”
- Sem `R-`/`EV-` na superfície comum; fontes sob demanda no padrão do projeto.
- A meta continua sendo a meta; a previsão não a substitui nem sugere mudá-la.

## 3. Alternativas consideradas

| Alternativa | Por que não (ou fica para depois) |
|---|---|
| Inferir VDOT de qualquer corrida recente automaticamente | Repete o risco do BUG-06: um treino forte não é prova. A previsão precisa de calibração explícita e auditável. |
| Só Riegel, sem VDOT | Riegel exige uma prova de referência e extrapola mal para maratonas; sozinho daria falsa precisão. |
| VDOT + Riegel com pesos | Não há base para ponderar; a faixa com os dois extremos já comunica a incerteza. |
| Sem persistência de proveniência | O app não saberia se o VDOT veio de prova, treino ou valor legado; a confiança e a data seriam inventadas. |
| Mostrar a previsão na prescrição/plano | Contaminaria o motor determinístico e a distinção “meta × prescrição” do ADR 0032. |
| Faixa fixa em segundos | Não escala entre 5k e maratona; percentual é a convenção do ADR 0032. |

## 4. Consequências e riscos

**Positivas:** o usuário ganha uma expectativa realista sem promessa; a meta passa
a ter contexto; a confiança e a origem ficam explícitas; nada entra no motor de
treino.

**Riscos e mitigação:**

| Risco | Mitigação |
|---|---|
| Previsão virar promessa | Copy “referência, não promessa”; confiança herdada; sem linguagem de garantia |
| Extrapolação otimista | Faixa ±3/±5%; Riegel só como checagem e limitado a 5k–meia; Tier B com confiança média |
| Calibração velha | > 56 dias sem número; o card pede recalibrar |
| Migração quebrar histórico | Room 17 só colunas nullable; legado `NULL` = sem previsão até recalibrar |
| Previsão contaminar treino | Objeto puro e somente UI; testes de independência do plano/prescrição |
| Riegel divergir do VDOT | A faixa usa o intervalo entre os métodos quando ambos existem; a UI não escolhe um como “verdade” |

## 5. Impactos

- **Domínio:** novo `RaceTimePredictor` puro (entrada: VDOT, confiança, distância-alvo, performance de referência opcional; saída: tempo previsto, faixa, método, confiança, `ruleId`/`evidenceIds`/avisos).
- **Modelo/dados:** `AthleteProfile` ganha proveniência da calibração; Room 17 e migração 16→17 testada. Nenhuma tabela nova.
- **App:** `CalibrationViewModel` grava a proveniência ao salvar; Perfil/Hoje montam a previsão a partir do perfil e do evento `PLANNED`; sem cálculo na UI.
- **Currents:** `SPORTS_SCIENCE.md` §8.10, PRD FR-208/FR-210, catálogo `R-RACE-03`, `ROADMAP` P1-02/P1-xx.
- **Fora:** previsão de trail/ultra, ajuste por clima/altimetria, recomendação de meta, mudança de plano/taper, LLM.

## 6. Testes previstos

- `RaceTimePredictorTest`: VDOT conhecido → tempo previsto; monotonicidade; faixa ±3/±5; sem VDOT → `null`; distâncias não suportadas → `null`; Riegel com referência Tier A; Riegel bloqueado para maratona a partir de 5k; calibração velha → aviso e sem número.
- Migração 16→17: legado `NULL` preservado; nova calibração grava origem/data; reset limpa a proveniência.
- ViewModel/Tela: previsão aparece só em evento `PLANNED` e no card de prova; sem IDs técnicos; estados sem calibração/velha.
- Independência: plano, prescrição, briefing e troca idênticos com e sem previsão.

## 7. Relação com o PRD

| Requisito | Como esta proposta atende |
|---|---|
| **BUG-06/FR-006** | A previsão usa o VDOT do Tier A/B já validado; não refaz a calibração. |
| **FR-208/FR-210** | A meta continua declarada pelo usuário; a previsão é leitura separada, sem alterar o evento. |
| **R-RACE-03** | Sai de “fora da fase” para `LIMITED`, com faixa, confiança e ausência explícita. |

## 8. Decisões aprovadas

1. **Proveniência persistida (P2):** Room 17 com colunas nullable (`vdotSource`, `vdotCalibratedAt`, `vdotReferenceDistanceM`, `vdotReferenceDurationSec`); legado `NULL` não gera previsão.
2. **Riegel (P4):** usado apenas como checagem de faixa para 5k–meia, com performance de referência Tier A.
3. **Faixa (P6):** ±3% para Tier A e ±5% para Tier B, como convenção de produto documentada.

## 9. Implementação

- **Modelo/dados:** `VdotSource` e proveniência em `AthleteProfile`; Room 17 e
  migração 16→17 testada com perfil legado (proveniência `NULL` preservada);
  export JSON inclui os campos novos.
- **Domínio:** `RaceTimePredictor` puro; inversão monotônica do VDOT via busca
  binária, faixas por confiança, Riegel limitado a 5k–meia e ausência explícita
  (`NO_VDOT`, `STALE_CALIBRATION`, `LOW_CONFIDENCE`).
- **App:** `CalibrationViewModel` grava origem/data (e a performance de
  referência no Tier A); Perfil e Hoje montam a previsão a partir do perfil;
  `RacePredictionBlock` mostra tempo, faixa, confiança, fonte e “não garantia”,
  sem IDs técnicos.
- **Catálogo:** `R-RACE-03` e `EV-RIEGEL-1981` registrados.
- **Testes:** `RaceTimePredictionTest` (11 casos), migração 16→17, proveniência
  no `CalibrationViewModelTest`, bloco de previsão no Perfil/Hoje e goldens
  `profile-race-{dark,light}` e `today-race-{dark,light,narrow-dark}`.

## 10. Referências

- ADR 0032: referência aritmética da meta e limites de escopo.
- ADR 0035: VDOT por prova/TT e por treinos, confiança e validação real.
- Daniels, *Daniels' Running Formula* (2014).
- Riegel, *Athletic records and human endurance* (1981).
- `SPORTS_SCIENCE.md` §8.10; PRD §BUG-06/FR-006/FR-208/FR-210.
