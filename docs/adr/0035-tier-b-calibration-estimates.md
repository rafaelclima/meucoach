# ADR 0035 — Estimativas de calibração Tier B (BUG-06)

**Status:** Aceito em 2026-11-11; fatias 1 (domínio) e 2 (app) implementadas na mesma data
**Data:** 2026-11-11
**Escopo:** BUG-06/FR-006 (BL-006), `CalibrationCalculator` em `core:domain`; sem migração, sem dependência nova
**Relacionados:** ADR 0025 (contexto de calibração, se aplicável), PRD §BUG-06/§FR-006, `SPORTS_SCIENCE.md` §4.2/§4.3, `R-CAL-02`
**Decisões D1–D7 aprovadas pelo usuário 0 em 2026-11-11**

---

## 1. Contexto

O PRD (`BUG-06`) registra que `CalibrationCalculator` rotula Tier B quando há
≥ 3 treinos recentes com FC + pace, mas devolve `vdot = null`,
`thresholdPace = null` e `lthr = null`. `SPORTS_SCIENCE.md` §4.2 promete
"melhor esforço sustentado + curva pace×FC; LTHR estimada". O usuário sem prova
recente fica preso em prescrição por RPE apesar de ter dados suficientes para
uma estimativa de confiança média.

`REVISAO_E_DIRECIONAMENTO_PRODUTO.md` divide BUG-06 em duas partes: a
"honestidade do rótulo" (barata, feita) e o "algoritmo" (caro e arriscado).
Esta ADR cobre o algoritmo, com escopo deliberadamente conservador.

Entrada disponível hoje: `ActivitySummary` (resumo) com distância, duração,
FC média e data — **sem samples** na superfície da calibração. A contagem atual
(`recentActivitiesWithHeartRateAndPace`) verifica exatamente isso: ≤ 8 semanas,
com FC e pace.

## 2. Decisão

Um caminho determinístico dentro de `CalibrationCalculator` estima os três
valores a partir do **melhor esforço sustentado** da lista de treinos.

| Decisão | Escolha |
|---|---|
| **D1 — Entrada** | `CalibrationInput.trainingEfforts: List<CalibrationPerformance>` (resumos; default vazio). Lista vazia preserva o comportamento atual (rótulo sem números), então nada quebra antes do wiring do app (fatia 2). |
| **D2 — Gate do Tier B** | Sem performance Tier A válida e com ≥ 3 esforços recentes (≤ 56 dias) com FC + pace. Com lista não vazia, o gate deriva da própria lista (esforços com FC, recentes); sem lista, mantém-se o campo de contagem. |
| **D3 — Pace de limiar** | Do melhor esforço sustentado: o mais rápido entre os de 20–35 min. Janela concilia o esboço do PRD (20–30 min) com Daniels (20–40 min) e mantém coerência com a janela de LTHR existente. Empate de pace → esforço mais recente. |
| **D4 — LTHR** | FC média do candidato, **apenas** se ele estiver em 25–35 min (regra de LTHR de §4.3 intacta). Fora da janela, `lthr = null` + aviso `NO_EFFORT_HEART_RATE`; nada é extrapolado. |
| **D5 — VDOT** | Derivado pela inversão da equação de Daniels no pace do esforço assumido como limiar: `VDOT = VO2(v) / 0,88` — coerente com `thresholdPaceForVdot`. Limites `MIN_VDOT..MAX_VDOT` (10–90) e pace de 150–900 s/km (os mesmos coerces existentes). |
| **D6 — Confiança e rótulos** | `MEDIUM`, `ruleId = R-CAL-02`, evidências `EV-DANIELS-2014`/`EV-FRIEL-2018`. Candidato ausente ou reprovado → comportamento atual: Tier B sem números, aviso novo `NO_SUSTAINED_EFFORT` (ou `EFFORT_PACE_OUT_OF_RANGE`) e recomendação do teste de 30 min. |
| **D7 — Escopo negado** | A regressão pace×FC com remoção de outliers fica **deferida** (ver §4). O salvamento continua explícito; a estimativa nunca é aplicada sozinha. |

Avisos novos em `CalibrationWarning`: `NO_SUSTAINED_EFFORT` (nenhum treino
elegível) e `EFFORT_PACE_OUT_OF_RANGE` (candidato existe, mas o pace derivado é
implausível para o modelo de Daniels). A verificação de `MAX_HEART_RATE_INCONSISTENT`
(contra `maxHeartRateObserved`) se aplica como no Tier A.

## 3. Por que conservador

Um treino de resumo não prova esforço máximo: o pace de um *tempo run* bem
executado aproxima o limiar, mas pode subestimá-lo se o treino foi morno. As
consequências direcionam para o lado seguro:

- subestimar o pace de limiar gera alvos mais fáceis, não mais arriscados;
- a confiança fica travada em `MEDIUM` e a copy recomenda confirmar com um
  teste de 30 min (o caminho do Tier A continua disponível);
- a estimativa não entra sozinha no perfil: o salvamento exige ação explícita
  do usuário, como já acontece;
- dado ausente (sem FC, fora da janela, treino velho) nunca é preenchido com
  estimativa — vira aviso, no padrão do projeto.

A regra entra no catálogo como `R-CAL-02` atualizada (`LIMITED`: a evidência
sustenta a aproximação por esforço sustentado; a precisão real depende da
qualidade do treino, que o resumo não mostra).

## 4. Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Regressão pace×FC com outliers agora | Com resumos de treinos heterogêneos (fáceis, intervalados, esteira), a "curva" mistura regimes fisiológicos distintos; remover outliers sem samples seria inventar método. Fica para quando houver seleção de treinos por tipo/estabilidade. |
| VDOT pela fórmula de performance (`vdot()`) no treino | Assumir que um treino de treino é esforço máximo produz VDOT sistematicamente subestimado que se propaga a todas as zonas; a via do limiar (0,88) é mais coerente com o que o esforço representa. |
| Estimar LTHR por regressão FC×pace | Mesmo problema da curva; Friel só sustenta LTHR por esforço de ~30 min. |
| Pedir dados de samples ao `core:data` | Ampliaria o contrato da calibração e a fatia sem necessidade; resumos bastam para o gate prometido. |

## 5. Consequências

- **Domínio:** `CalibrationInput` ganha `trainingEfforts` (default vazio, sem
  quebrar chamadas existentes); dois avisos novos; documentação do objeto
  atualizada. `core:domain` continua puro.
- **App (fatia 2, implementado):** `CalibrationViewModel` mapeia os resumos
  recentes (FC + pace + ≤ 56 dias) para `CalibrationPerformance` e passa a lista
  em `trainingEfforts`, com o contador derivado da mesma lista. A tela oferece
  “Estimar pelos meus treinos” quando há ≥ 3 esforços, mostra a estimativa com
  confiança e a copy honesta (“estimativa; confirme com um teste”) e mantém o
  salvamento explícito; sem candidato, o rótulo continua sem números.
- **Ciência:** `SPORTS_SCIENCE.md` §4.2/§4.3 descreve o método exato do Tier B
  e o que continua sem estimativa.
- **Dados:** nenhuma migração; export/reset inalterados.
- **Limites:** sem samples não há validação de estabilidade do esforço
  (drift de FC, pausas); a confiança média reflete isso. Treino de esteira é
  válido (modalidade aceita pelo projeto).
- **Validação em dados reais (11/11/2026):** com o usuário 0, a estimativa
  ficou em VDOT 30,6 / 6:18/km / 168 bpm contra VDOT 29,63 / 6:28/km da prova
  de 5k (~31 min), ~3% acima e 10 s/km mais rápida. O candidato era um treino
  forte recente, não um teste; a estimativa permaneceu plausível. A calibração
  salva (27,09 / 6:56) coincidia com a fórmula de performance aplicada a um
  treino de 34 min e era conservadora, não uma referência de prova. Nenhuma
  mudança de método foi necessária; o viés residual segue documentado em §4.

## 6. Testes

- `CalibrationCalculatorTest` (novos): estimativa pelo melhor esforço
  sustentado (pace/LTHR/VDOT), escolha do mais rápido em empate de janela,
  esforços velhos fora do gate, janela 20–35 sem candidato → `NO_SUSTAINED_EFFORT`,
  candidato de 22 min sem LTHR, `MAX_HEART_RATE_INCONSISTENT` no Tier B,
  comportamento preservado com lista vazia, e pace implausível →
  `EFFORT_PACE_OUT_OF_RANGE` sem inventar número.
- `CalibrationViewModelTest`/`CalibrationScreenTest` (fatia 2): mapeamento da
  lista, estimativa Tier B, gate honesto abaixo de 3 esforços, salvamento
  explícito e copy sem IDs técnicos; goldens `calibration-tier-b-{dark,light}`.
- Casos existentes continuam verdes sem mudança de assert.

## 7. Relação com o PRD

| Requisito | Como este ADR atende |
|---|---|
| **BUG-06** | Tier B passa a estimar VDOT/pace de limiar/LTHR quando os dados sustentam; sem candidato, permanece honesto com aviso e recomendação de teste. |
| **FR-006** | Alvos de pace disponíveis sem prova recente, com confirmação explícita no salvamento. |
| **BL-006** | Primeira metade do backlog; validação com dados reais fica para depois do wiring (fatia 2). |

Pendente fora desta entrega: regressão pace×FC. Validação com os dados reais
do usuário 0 concluída em 11/11/2026 (ver §5).

## 8. Referências

- `docs/PRD_COACH_CORRIDA.md` §BUG-06 e §FR-006
- `docs/REVISAO_E_DIRECIONAMENTO_PRODUTO.md` (partição honestidade × algoritmo)
- `docs/SPORTS_SCIENCE.md` §4.2/§4.3 e §14 (`R-CAL-02`)
- Friel, *Total Heart Rate Training* (2006/2018): LTHR por esforço de 30 min
- Daniels, *Daniels' Running Formula* (2014): equações VO2/%VO2max e fração de limiar
