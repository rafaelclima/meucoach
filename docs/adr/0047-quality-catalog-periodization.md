# ADR 0047 — Catálogo de qualidade e periodização por fase, nível e distância

**Status:** Aprovado em 2026-11-16 — C4-14 a C4-17 concluídas; C4-18..C4-21 pendentes.
**Data:** 2026-11-16
**Escopo:** `SessionLibrary`, `QualityKind`, `WorkoutStructure`, `WeeklyPlanGenerator`,
`DailyPrescriptionEngine`, `SessionBriefingEngine`, `SessionSwapper`, UI de Hoje/Plano.
**Relacionados:** ADR 0023 (plano × prescrição), ADR 0024 (evidência), ADR 0038
(progressão), ADR 0041 (mesociclo), ADR 0042 (graduação), ADR 0043 (periodização),
ADR 0044 (oportunidade), ADR 0048 (persistência).
**PRD:** `docs/PRD_TREINO_REAL.md` FR-403, FR-404, FR-410, §11.5 (Q-05, Q-12).
**Decisão do usuário 0:** 16/11/2026.

---

## 1. Contexto

### 1.1 O que existe hoje

- `SessionLibrary` mapeia `INTERMEDIATE` para `QualityKind.CONTINUOUS` em `BUILD`
  e `PEAK`; `ADVANCED` recebe `INTERVALS` (`SessionLibrary.kt:79-105`).
- `QualityKind` só tem `STRIDES_ONLY`, `CONTINUOUS`, `INTERVALS`
  (`SessionLibrary.kt:15-19`).
- Não existe `FARTLEK`, `HILLS` ou `RACE_PACE`.
- O `PEAK` do intermediário é idêntico ao `BUILD` (mesmo título, mesmo estímulo).
- O iniciante só recebe strides como qualidade; o `R-PRESC-04` bloqueia qualquer
  `QUALITY` no dia.
- A sessão não tem estrutura executável: `PlannedSession` tem data, tipo, título,
  duração, alvos e rationale; não tem aquecimento, repetições ou recuperações.
- O `SPORTS_SCIENCE.md` §8.4 lista cinco tipos de qualidade (strides, tempo,
  intervalos VO2max, colinas, esteira); o código implementa três, e o
  intermediário acessa um.

### 1.2 O problema

O usuário é `INTERMEDIATE` e vê, no máximo, um “Ritmo forte” contínuo. Ele não
recebe intervalado, fartlek ou colinas; o `PEAK` não é específico; e a única
forma de destravar intervalado seria virar `ADVANCED`, o que exige um longão de
90 min que o plano não entrega. O ciclo é fechado.

A ciência não sustenta “intermediário só contínuo” nem “iniciante nunca faz
intensidade”. Ela sustenta base fácil + intensidade gradual, com dose e
recuperação. O produto precisa de um catálogo mais rico, com travas por nível e
fase.

### 1.3 Decisão do usuário 0

- Q-05: intermediário recebe intervalado/fartlek/colinas em `BUILD`/`PEAK`;
  iniciante recebe strides + intro fartlek `EASY` após 4 semanas consistentes;
  intervalado estruturado só após a graduação.
- Q-12: intermediário com 5+ dias pode ter 2 qualidades com progressão.

---

## 2. Decisão

### 2.1 `QualityKind` e estrutura

`QualityKind` passa a ter:

| Kind | Uso | Tipo de sessão |
|---|---|---|
| `STRIDES_ONLY` | micro-dose de taper | `QUALITY` (micro-dose) |
| `CONTINUOUS` | tempo/threshold contínuo | `QUALITY` |
| `INTERVALS` | VO2max, reps de 3–5 min | `QUALITY` |
| `FARTLEK` | variação RPE-based | `QUALITY` (intermediário/avançado) |
| `HILLS` | subidas curtas | `QUALITY` |
| `RACE_PACE` | pace específico da prova | `QUALITY` (PEAK) |

> Strides do dia fácil e intro fartlek do iniciante são `SessionRole.STRIDES`/`INTRO_FARTLEK`, tipo `EASY`, `qualityKind=null` e `structure` presente. Eles **não** usam `QualityKind` e **não** contam como oportunidade de qualidade. A invariante `(type == QUALITY) == (qualityKind != null)` continua.

`WorkoutStructure` é o contrato executável:

```kotlin
data class WorkoutStructure(
    val version: Int = 1,
    val warmupMin: Int,
    val blocks: List<WorkoutBlock>,
    val cooldownMin: Int,
    val totalMin: Int,
)

data class WorkoutBlock(
    val kind: BlockKind,          // WORK, RECOVERY, STRIDES, RACE_PACE
    val repetitions: Int?,
    val workSec: Int?,
    val recoverySec: Int?,
    val target: String?,          // E/M/T/I/R/Z2..Z5/GOAL_5K/RPE
    val note: String?,
)
```

Regras:

- `totalMin` = duração da sessão.
- Aquecimento + blocos + recuperações + volta à calma ≤ `totalMin`.
- Nunca prometer mais trabalho do que cabe.
- O alvo do bloco é objetivo quando há calibração; senão RPE.
- A estrutura é serializada em `structureJson` (ADR 0048); linhas antigas `null`.
- `SessionTemplate` ganha `structure: WorkoutStructure?`; o invariante
  `type == QUALITY == (qualityKind != null)` continua.
- `SessionRole` ganha `INTRO_FARTLEK` (tipo `EASY`, `qualityKind=null`).

### 2.2 Iniciante

A política aprovada:

1. **Strides desde a semana 1**: 4–6×20 s no fim de um fácil, recuperação
   completa, RPE 7–8/10, 1–2×/semana. Sessão `EASY` (`STRIDES`), não conta como
   qualidade.
2. **Intro fartlek após 4 semanas consistentes** (4 semanas com ≥80% de conclusão
   forte, sem dor/doença): 6–8×30 s “confortavelmente forte”/1–2 min fácil,
   RPE 7, 1×/semana, como `EASY` (`INTRO_FARTLEK`), não como `QUALITY`. O app pode
   trocar o dia de strides por esse fartlek.
3. **Intervalado estruturado** (`INTERVALS`, `HILLS`, `RACE_PACE`) permanece
   bloqueado para `BEGINNER` por `R-PRESC-04` até a graduação para
   `INTERMEDIATE`. A dose/recuperação é o motivo; não é uma proibição científica
   absoluta.
4. A partir de 4 semanas, o iniciante pode ter 1 intro fartlek `EASY` por semana,
   sem contar como qualidade no plano; o volume fácil continua a prioridade.

Justificativa científica (resumo; detalhe no PRD §11.5):

- ≥70% do volume fácil e ≤30% em limiar/alta é o que melhor sustenta a adaptação
  (revisão de distribuição de intensidade, 2021).
- Novatos têm maior incidência de lesão (17,8 vs. 7,7/1000 h); o risco está na
  progressão/volume, não no estímulo curto isolado.
- HIIT funciona, mas os estudos são majoritariamente com recreacionais; dados de
  lesão em novatos são escassos.
- Strides são o on-ramp de menor risco; fartlek é um veículo RPE-based válido,
  com evidência específica de baixa/moderada qualidade; intervalado estruturado
  tem maior custo de recuperação/impacto.

Evidências novas propostas: `EV-TID-2021`, `EV-INJURY-2015`, `EV-HIIT-2016`,
`EV-HIIT-2024`; a dose exata é convenção de produto (`LIMITED`).

### 2.3 Intermediário

- `BASE`: fácil + longão + strides; a partir da segunda metade, fartlek leve
  opcional (1×/semana, RPE 6–7, dose baixa).
- `BUILD`: 1–2 qualidades, com kinds diferentes:
  - contínuo (20–25 min em T);
  - intervalos VO2max (5×3 min em I, 2 min fácil; 3–5 reps);
  - fartlek (8×(1 min forte/1 min fácil) ou 6×30 s/90 s);
  - colinas (6×45 s subida forte, caminhada/descida).
- `PEAK`: 1 qualidade específica da distância (ver §2.5).
- A segunda qualidade só com `Q-12` (5+ dias, semana anterior ≥80%, ACWR
  confiável 0,8–1,3, sem taper).
- O intermediário **não** precisa virar `ADVANCED` para ter intervalado; a
  graduação controla volume/frequência/complexidade, não o direito ao estímulo.

### 2.4 Avançado

- 2 qualidades; maior volume; `PEAK` específico; `RACE_PACE`; longão 60+.
- A complexidade e a dose aumentam, mas os guardrails continuam.

### 2.5 Matriz por fase/nível/distância

| Fase | Beginner | Intermediate | Advanced |
|---|---|---|---|
| `BASE` | strides (EASY) + intro fartlek (EASY após 4 sem) | strides + fartlek leve (2ª metade) | strides + fartlek leve |
| `BUILD` | strides + intro fartlek (EASY; sem `QUALITY`) | contínuo, intervalos 5×3 min, fartlek 8×(1+1), colinas 6×45 s | 2 qualidades; intervalos 6×3 min/4×5 min; colinas |
| `PEAK` | strides + intro fartlek (EASY; sem `QUALITY`) | 5K: 5×1 km no pace atual; 10K: 3×2 km; meia: 2×3 km; maratona: 2×5 km | 2 específicas; mais volume |
| `TAPER` | strides micro-dose | micro-dose ≤25 min | micro-dose ≤25 min |
| `RECOVERY` | fácil | fácil | fácil |

A tabela é uma convenção de produto (`LIMITED`), derivada de `SPORTS_SCIENCE`
§8.4. A estrutura real é calculada pela duração disponível e nunca promete mais
trabalho do que cabe.

### 2.6 PEAK específico e pace de prova

- `PEAK` de 5K: intervalos curtos e/ou blocos no pace de prova **atual** (VDOT),
  não na meta declarada.
- 10K: limiar + pace de 10K; meia: blocos no pace de meia; maratona: blocos no
  pace de maratona.
- A meta de tempo é referência; a previsão é recalculada a cada calibração; o app
  mostra o gap meta × previsão. Se o atleta evolui, o pace do `PEAK` muda.
- Se a meta é >5% mais rápida que a previsão, o app diz que é ambiciosa e usa a
  previsão atual; não prescreve um pace não demonstrado.

### 2.7 Rotação determinística

```
templates = SessionLibrary.qualityTemplates(phase, level, distance)
eligible = templates.filter { levelAllows && phaseAllows }
selected = eligible[weekInPhase % eligible.size]
if (maxQualities == 2) {
    second = eligible[(weekInPhase + 1) % eligible.size]
    ensure(second.kind != selected.kind)
}
```

`weekInPhase` é 0-based e derivado do plano/mesociclo, não do relógio. A rotação
recebe também `phaseWeeks` (duração da fase) para a segunda metade da `BASE`;
sem essa informação ela não expõe qualidade na `BASE`. Duas semanas seguidas não
repetem o mesmo kind quando há mais de um template; a segunda qualidade sempre
tem kind diferente da principal.

### 2.8 Precedência de alvos (`R-ZONE-01 v2`)

- `EASY`/`LONG`: FC/RPE é o alvo principal; o pace E é referência.
- `QUALITY`: o pace do bloco é principal; a FC é guarda-corpo.
- `PrescriptionTargets.primary: TargetKind` (`HEART_RATE`, `PACE`, `RPE`, `MIXED`).
- O briefing diz “guie pelo esforço e pela FC; o pace é referência” no fácil; “use
  o pace do bloco; a FC confirma” na qualidade.

### 2.9 `SessionSwapper`

A troca manual leva `qualityKind`/`structure` junto com a sessão. A validação
continua: máximo 2 qualidades, sem qualidade adjacente ao longão, sem duas
consecutivas, respeitando disponibilidade e taper/recovery.

### 2.10 Invariantes

- Iniciante nunca recebe `QUALITY` estruturada (`R-PRESC-04`).
- Intermediário recebe intervalado/fartlek/colinas em `BUILD`/`PEAK`.
- `PEAK` não é cópia do `BUILD`.
- Nenhuma estrutura promete mais tempo do que a sessão.
- O pace de prova usa a previsão atual, não a meta.
- A rotação é determinística.
- A troca manual preserva kind/structure.

---

## 3. Dados e APIs

- `QualityKind` + `FARTLEK`, `HILLS`, `RACE_PACE`.
- `WorkoutStructure`/`WorkoutBlock` no domínio; `structureJson` no schema v18
  (ADR 0048). Codec `WorkoutStructureJson` versionado em `core:data` (C4-14),
  com payload inválido/desconhecido decodificando como `null`.
- `SessionTemplate.structure`; `SessionRole.INTRO_FARTLEK`.
- `SessionLibrary.qualityTemplates(phase, level, distance)` e
  `SessionLibrary.easyTemplates(phase, level, includeIntroFartlek)`.
- `PrescriptionTargets.primary`; `TargetKind`.
- `PlannedSession.qualityKind`/`structure`.
- `DailyPrescription.structure`.
- `SessionBriefingEngine` lê a estrutura.
- `SessionSwapper` copia a estrutura.

Não há migração de dados além do `structureJson` do ADR 0048.

---

## 4. Alternativas consideradas

1. **Manter só contínuo para intermediário.**
   Rejeitada: o usuário não recebe o estímulo que a própria documentação lista;
   o `PEAK` continua genérico.
2. **Intervalado só para avançado.**
   Rejeitada: a graduação é inalcançável com o plano atual; o intermediário fica
   meses sem estímulo.
3. **Iniciante só strides, sem fartlek.**
   Rejeitada: a ciência não sustenta uma proibição total; strides + intro fartlek
   `EASY` é um meio-termo seguro.
4. **Iniciante com intervalado estruturado após 4 semanas.**
   Rejeitada: maior custo de recuperação/impacto; a base e a graduação são o
   caminho.
5. **Fartlek com pace fixo.**
   Rejeitada: fartlek é RPE-based; pace fixo o transforma em intervalado.
6. **PEAK igual ao BUILD.**
   Rejeitada: não prepara a distância; o PEAK precisa de especificidade.
7. **Prescrever a meta de 28:00.**
   Rejeitada: pode ser 10% mais rápido que a previsão; usar a previsão atual.

---

## 5. Consequências

### 5.1 Positivas

- O intermediário vê intervalado, fartlek e colinas; o `PEAK` fica específico.
- O iniciante tem um on-ramp seguro (strides + intro fartlek) sem furar a guarda.
- O plano deixa de ser monótono; a rotação evita repetir o mesmo estímulo.
- A estrutura executável melhora o briefing e a futura sessão guiada.

### 5.2 Riscos

- Mais variedade = mais superfície de teste; a matriz precisa ser exaustiva.
- A estrutura pode prometer mais do que cabe; o cálculo de duração é crítico.
- O intro fartlek pode ser confundido com qualidade; a UI deve rotulá-lo como
  “fácil com surtos”, não “treino forte”.
- O pace de prova atual pode ser conservador; o app mostra o gap e a meta como
  referência, sem prometer.

### 5.3 Segurança

- `R-PRESC-04` continua bloqueando `QUALITY` estruturada no iniciante.
- O intro fartlek é `EASY`, RPE 7, dose baixa, após 4 semanas consistentes.
- A qualidade do intermediário continua sujeita ao `QualityGate` (ADR 0044).
- Taper/recovery/race week continuam prevalecendo.

---

## 6. Matriz de testes

| Caso | Entrada | Saída |
|---|---|---|
| Matriz completa | todas as fases/níveis/distâncias | templates com kind/structure/ruleIds/evidenceIds |
| `INTERMEDIATE/BUILD` | intermediário | contém `INTERVALS`/`FARTLEK`/`HILLS` |
| `BEGINNER/BUILD` | iniciante | sem `QUALITY`; intro fartlek `EASY` |
| `PEAK` 5K | intermediário, prova 5K | `RACE_PACE` com previsão atual |
| `PEAK` maratona | avançado | blocos de maratona |
| Rotação | duas semanas | kinds diferentes quando há mais de um |
| Estrutura | 25/40/45 min | soma ≤ duração; reps cabem |
| `R-PRESC-04` | iniciante com `QUALITY` | `DOWNGRADE`/bloqueio |
| Strides/intro fartlek | iniciante após 4 semanas | `EASY`, não `QUALITY` |
| SessionSwapper | troca | kind/structure acompanham |
| Determinismo | mesmos inputs | mesma seleção |

Goldens: `G-PER-05` (5K BUILD/PEAK), matriz de qualidade, estrutura.

---

## 7. Rollout

- `weekly-plan-v7`, `session-library-v2`, `daily-prescription-v6`.
- `structureJson` no schema v18 (ADR 0048); planos antigos `null`.
- `PlanUpgrade` na semana vigente; a oportunidade pode ganhar estrutura/kind
  novo, sem mudar volume.
- UI: títulos “Intervalos”, “Fartlek”, “Colinas”, “Pace de prova”; a estrutura no
  card/“Por quê?”.
- Docs: `SPORTS_SCIENCE` §8.4/§8.8, `R-PER-04`, ADR 0047, `DECISIONS`.

---

## 8. Fora de escopo

- Sessão guiada persistente (ciclo 3).
- Prescrever a meta de tempo como pace.
- Novos esportes (ciclismo/natação).
- Nutrição/musculação.
- UI de biblioteca de treinos fora do plano.

---

## 9. Referências

- `docs/PRD_TREINO_REAL.md` FR-403/FR-404/FR-410, §11.5 (Q-05, Q-12).
- `docs/SPORTS_SCIENCE.md` §6.4, §8.4, §8.8.
- ADR 0023, 0024, 0038, 0041, 0042, 0043, 0044.
- `EV-DANIELS-2014`, `EV-SEILER-2010`, `EV-FOSTER-2001`, `EV-ISSURIN-2010`,
  `EV-TID-2021`, `EV-INJURY-2015`, `EV-HIIT-2016`, `EV-HIIT-2024`.

---

## 10. Registro de implementação

- **C4-14 (S6):** `WorkoutStructure`/`WorkoutBlock`/`BlockKind` puros e codec
  `WorkoutStructureJson` versionado em `core:data`; a estrutura nunca promete
  mais tempo do que `totalMin`.
- **C4-15 (S6.1):** matriz em
  `SessionLibrary.qualityTemplates(phase, level, distance)` e
  `easyTemplates(phase, level, includeIntroFartlek)`; `SessionRole.INTRO_FARTLEK`;
  `SessionTemplate.structure` obrigatória em qualidade. A projeção
  `templates(...)` mantém o formato main/second usado pelo gerador v6 até
  C4-17. As formas de `PEAK` são baseadas em tempo porque `WorkoutBlock` não
  carrega metros; C4-17 converte a previsão atual em pace real e ajusta a
  estrutura à duração disponível.
- **C4-16 (S6.2):** `SessionLibrary.qualityRotation(phase, level, distance,
  weekInPhase, maxQualities, phaseWeeks)` seleciona 1–2 templates com rotação
  determinística por `weekInPhase` (0-based); semanas seguidas não repetem o
  kind principal e a segunda qualidade só entra com kind diferente. O fartlek
  leve da `BASE` fica restrito à segunda metade via `phaseWeeks`; sem essa
  informação, a `BASE` fica sem qualidade (conservador). 14 testes em
  `QualityRotationTest`.
- **C4-17 (S6.3):** o `WeeklyPlanGenerator` consome `qualityRotation` e
  `easyTemplates`; `MacroWeekIntent`/`MesoState` carregam `weekInPhase` e
  `phaseWeeks` (macro ou blocos gerais de 4 semanas). Sessões de qualidade
  recebem `qualityKind`, título do template e `structure`; a segunda qualidade
  usa o template `QUALITY_SECOND`. `WorkoutStructure.fittedTo(minutes)` encolhe
  repetições/blocos contínuos e ajusta `totalMin` sem prometer mais trabalho do
  que cabe; o rationale nasce da estrutura final. `PEAK` usa `RACE_PACE` da
  distância e não é cópia do `BUILD`; `BASE` fica sem qualidade na primeira
  metade; `TAPER` vira micro-dose com kind/estrutura; `RECOVERY` e iniciante
  continuam sem `QUALITY`. A projeção legada `templates(...)` foi removida e
  `R-PER-04` entrou no catálogo de explicações. 7 testes em
  `QualityIntegrationTest` e 7 em `WorkoutStructureTest`; `:core:domain:test`
  963 verdes. Persistência de `qualityKind`/`structure` fica em C4-45/C4-46.
