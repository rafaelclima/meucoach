# ADR 0053 — Rampa de entrada do cold-start (micro-dose para intermediário + sugestão de nível)

**Status:** Proposto em 2026-11-19 — implementado nesta fatia (domínio + wiring Hoje + sugestão na calibração).
**Data:** 2026-11-19
**Escopo:** `core:domain/prescription/` (`QualityGate`, `ColdStartAllowance`, `InitialExperienceSuggester`, `DailyPrescriptionEngine`), `GetTodayPrescription`, `TodayViewModel`, `CalibrationViewModel/Screen`, `ExplanationCatalog`, `SPORTS_SCIENCE.md` §8.8/§14.
**Relacionados:** ADR 0026 (cobertura de carga), ADR 0035 (Tier B), ADR 0036 (VDOT/proveniência), ADR 0042 (graduação), ADR 0044 (oportunidade/QualityGate), ADR 0046 (load gate v3), ADR 0048 (versões).
**PRD:** resposta do usuário 0 em 19/11/2026 — cold-start conservador atrasa a evolução.

## 1. Contexto

O gate atual exige `LoadDataState.KNOWN` para qualquer `QUALITY`:

* `objectiveDaysWithLoad >= 8` em 28 dias + fonte fresca ≤14 dias (`TrainingLoadEngine.kt:279`, `LoadSnapshot`);
* `BEGINNER` nunca recebe `QUALITY` (`QualityGate.beginnerBlock`, `R-PRESC-04`);
* `SUBJECTIVE_MISSING`/âmbar/sono <6h bloqueiam antes da carga.

Consequência medida no código:

* `BEGINNER` sem histórico: 4-6 semanas só `EASY` (graduação exige 4 semanas ≥80% + 3 esforços + longão 60min + calibração válida — `LevelGraduationEngine.kt:163-186`).
* `INTERMEDIATE` novo com prova recente: plano já tem 1 oportunidade estrutural (`WeeklyPlanGenerator.kt:348`), mas o dia rebaixa para `EASY` por `LOAD_INSUFFICIENT` até completar 8 dias objetivos.

O usuário 0 (5k ~31min, VDOT válido) sente que o app "não treina" nas primeiras semanas. O conservadorismo protege, mas ignora a prova recente que já demonstra tolerância a esforço forte curto.

## 2. Decisão

### 2.1 Rampa mínima, sem afrouxar segurança geral

* **Só `INTERMEDIATE/ADVANCED`.** `BEGINNER` continua bloqueado por `R-PRESC-04`. Sem exceção.
* **Só com VDOT recente com proveniência.** `ColdStartAllowance.isValid()` = `vdot != null + vdotSource != null + vdotCalibratedAt ≤56 dias`. Legado sem proveniência não vale. Tier A (`PERFORMANCE`) e Tier B confirmado (`TRAINING_ESTIMATE`) valem porque ambos gravam `vdotSource + calibratedAt` (`CalibrationViewModel.save()`).
* **Só dose curta.** Sessão planejada `≤30 min`. Acima disso continua `LOAD_INSUFFICIENT`. O teto 30 min cobre micro-dose (≤25) + contínuo curto, sem liberar intervalado longo de 40-45 min.
* **Só com dia verde completo.** Doença/vermelho, sono <6h, sem check-in, âmbar, recovery/taper/race continuam bloqueando antes (ordem do `QualityGate` preservada). A rampa só substitui o `loadBlock` (`INSUFFICIENT/STALE/sem dados`), nunca segurança/prontidão.
* **Sem progressão.** `VolumeProgression` não muda: sem base completa o volume é mantido, nunca +6%. A rampa libera **1 qualidade curta**, nunca 2ª qualidade (`permitsProgression` continua exigindo ACWR confiável).
* **Sem pular monotonia/strain/ACWR quando há dados.** Quando há `load` com `KNOWN`, o caminho antigo vale. Quando a rampa aplica (sem `KNOWN`), monotonia/strain/ACWR são pulados por ausência de baseline — já documentado como "em calibração não veta" (`R-LOAD-03 v3`).

### 2.2 Auditoria

* Nova regra `R-COLD-01` ("Rampa de entrada: prova recente libera 1 qualidade curta, sem progressão"), evidências `EV-DANIELS-2014` + `EV-SEILER-2010`, confiança máxima `LOW` (o `DailyPrescriptionEngine.confidence` já rebaixa quando `!loadTrusted`).
* `inputsHash` inclui `coldStartValid + vdotSource + calibratedAt`; mudar a calibração invalida a prescrição (nova versão, sem reescrever histórico — ADR 0023).
* `QualityGate` continua puro e determinístico; `ColdStartAllowance.fromProfile()` é puro.

### 2.3 Sugestão de nível inicial (sem auto-aplicar)

* `InitialExperienceSuggester.suggest(profile, allowanceValid)`: `BEGINNER + allowance válida → sugere INTERMEDIATE` com motivo "prova/estimativa recente". Nunca auto-aplica; a troca continua explícita no Perfil/Onboarding e participa de T4 (`EvaluateReplan`).
* Superfície: hint na `CalibrationScreen` quando o resultado tem VDOT e o perfil é `BEGINNER` ("Com este resultado, considere Intermediário"), + CTA para o Perfil. Nenhum schema/migração.

## 3. Alternativas rejeitadas

| Alternativa | Por que não |
|---|---|
| Reduzir `MIN_DAYS_WITH_LOAD` 8→3 para todos | Afrouxa para `BEGINNER` sem prova, aumenta risco de lesão; quebra `G-LOAD-03` e `R-LOAD-03 v3` |
| Seed de volume auto-declarado no onboarding ("corro 3x 40min") | Auto-declaração infla base sem evidência; exigiria `LOW` + auditoria nova, sem ganho sobre a prova real |
| Liberar `QUALITY` longa (40-45min) no cold-start | Dose alta sem baseline contradiz `EV-SEILER-2010`/`EV-INJURY-2015`; o teto 30 min é o compromisso |
| Auto-subir `BEGINNER→INTERMEDIATE` ao calibrar | Viola ADR 0042 (aceite explícito T4); sugestão preserva autonomia |

## 4. Consequências

* Positivas: intermediário com prova recente treina forte curto desde a semana 1, com copy honesta; iniciante real continua protegido; sem migração.
* Negativas: 1 exceção a mais no `QualityGate` para manter; `R-COLD-01` para catalogar em `SPORTS_SCIENCE`/`ExplanationCatalog`.
* Riscos: calibração manual com tempo inventado liberaria a rampa — mitigado por exigir `vdotSource` + data real do formulário + confiança `LOW` + dose ≤30min + dia verde.

## 5. Rollout

1. Domínio: `ColdStartAllowance` + `InitialExperienceSuggester` + `QualityGate` + `DailyPrescriptionInput/Hash` + `R-COLD-01` no catálogo.
2. App: `GetTodayPrescription` + `TodayViewModel.buildPreviewPrescription` constroem via `fromProfile`; `CalibrationScreen` mostra sugestão.
3. Testes: allowance (limites 56d/fonte/dose), gate (permite/bloqueia), suggester, hash muda com calibração, goldens existentes verdes.
4. Docs: `SPORTS_SCIENCE` §8.8/§14, `ARCHITECTURE` §6.1, `DECISIONS` r1.63, `ROADMAP` P1-05, `PROGRESS`.
