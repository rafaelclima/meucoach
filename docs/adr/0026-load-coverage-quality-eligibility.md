# ADR 0026 — Cobertura de carga, ACWR desconhecido e elegibilidade de qualidade

**Status:** proposto — decisão de direção aprovada; limiares ainda precisam de especificação e validação científica
**Data:** 2026-11-06
**Responsável:** planejamento com Rafael (usuário 0)
**Implementação:** parte de C1-02 (bloqueio por dados insuficientes/desatualizados); elegibilidade de iniciante depende de C1-04 e de validação no ciclo seguinte.

---

## 1. Contexto

O motor de carga atual (`TrainingLoadEngine`) produz uma série diária de 42 dias:

- dias sem atividade recebem `DailyLoad(load = 0.0, metric = null)`;
- `acwr` é `null` quando a média crônica de 28 dias é zero;
- `missingDays` existe, mas não distingue “descanso” de “sem dado”;
- não há frescor da fonte de atividades;
- o plano semanal trata `acwr == null` como **permissão** para qualidade:

```kotlin
val qualityAllowed = !safetyBlock &&
    input.profile.experience != Experience.BEGINNER &&
    (acwr == null || acwr in ACWR_MIN..ACWR_MAX) &&
    days.size >= 3
```

Isso contraria o princípio “sem dados, não aumente carga” (`AGENTS.md` §3/§5, `PRODUCT.md` §5.4) e o próprio `SPORTS_SCIENCE.md` (§6.3: ACWR é flag, nunca lei).

Além disso, a elegibilidade de qualidade para iniciantes hoje é apenas `experience != BEGINNER`, um rótulo estático. O iniciante nunca progride, mesmo com histórico consistente. A decisão aprovada exige critérios documentados e justificáveis, não uma regra universal arbitrária.

O ACWR tem limitações teóricas e estatísticas reconhecidas (Impellizzeri et al., 2020). Ele não pode ser usado isoladamente como indicador de segurança, diagnóstico de risco de lesão ou “semáforo” universal.

## 2. Problema

1. ACWR ausente libera qualidade; o app aumenta estímulo sem base.
2. Dias sem dado entram como carga zero, reduzindo artificialmente a média crônica e distorcendo o ACWR.
3. Não há como distinguir “o atleta descansou” de “o app não recebeu dados”.
4. Uma fonte de atividades desatualizada (relógio sem sync, arquivo não importado) parece recuperação.
5. A decisão de qualidade não considera cobertura, consistência, experiência, recuperação e informação subjetiva de forma conjunta.
6. A progressão pode ser liberada por um único número, sem evidência de execução.
7. Iniciantes ficam eternamente no fácil, ou podem ser liberados cedo demais se a regra for só “4 semanas”.

## 3. Decisão

### 3.1 Distinguir três estados de carga

`LoadSnapshot` passa a expor, além de `acwr`:

```kotlin
enum class LoadDataState { KNOWN, INSUFFICIENT, STALE }

data class LoadCoverage(
    val daysInWindow: Int,        // 28
    val daysWithLoad: Int,
    val daysWithoutActivity: Int, // ausência de atividade em dia com fonte fresca
    val daysWithoutData: Int,     // ausência de dado/fonte
    val lastActivityAt: Instant?,
    val sourceFresh: Boolean,
)
```

Regras:

- **Dia sem atividade só é descanso quando a fonte está fresca.** Se não há atividade recente e a fonte está desatualizada, o dia é “sem dado”, não zero.
- **ACWR é `null` quando:** não há base crônica suficiente, a cobertura é insuficiente ou a fonte está desatualizada. `null` nunca significa “sem restrição”.
- **`missingDays` não é mais tratado como zero fisiológico.** O cálculo pode manter zero para a série matemática, mas a decisão usa `LoadDataState`/`LoadCoverage` e declara a ausência.
- **Frescor:** a fonte de atividades é considerada atualizada se a última importação/sincronização de atividades ocorreu nos últimos **14 dias** (valor proposto, a validar). Um período sem corrida planejado pode ser descanso; um período sem *dados* não é.

### 3.2 Gate de qualidade

Qualidade só é elegível quando **todos** os critérios abaixo são verdadeiros:

1. prontidão verde no dia, sem `ILLNESS_OR_INJURY`, dor ou `NO_QUALITY`;
2. `LoadDataState.KNOWN`;
3. ACWR conhecido e dentro de **0,8–1,3**;
4. pelo menos **8 dias com carga** nas últimas 4 semanas (valor proposto; cobre ~2×/semana);
5. fonte de atividades atualizada (frescor ≤ 14 dias, valor proposto);
6. espaçamento: máximo 2 qualidades/semana, nunca consecutivas, nunca no dia ou ao lado do longão;
7. experiência/elegibilidade: ver §3.3;
8. nenhuma decisão de qualidade pode ser liberada apenas por ACWR; o rationale cita também prontidão, sono, dor e histórico.

Se qualquer critério falhar, a decisão é conservadora:

- `INSUFFICIENT` ou `STALE` ⇒ sem qualidade e sem progressão;
- o texto diz “dados de carga insuficientes” ou “dados desatualizados”, nunca “você descansou”;
- a confiança é reduzida e a UI mostra o que falta para liberar.

### 3.3 Elegibilidade de qualidade para iniciantes (proposta)

Um iniciante pode receber **uma** qualidade introdutória quando:

- completou **4 semanas consecutivas** de plano;
- ≥ **80%** de conclusão **com evidência forte** (`ACTIVITY`/`GUIDED`, ADR 0024); `MANUAL` e `LEGACY_UNVERIFIED` não contam;
- ≥ **3 corridas/semana** no período;
- volume semanal ≥ **15–20 km** ou ≥ **120 min** (o que fizer sentido para o histórico; valor a validar);
- prontidão verde no dia, sem dor/doença;
- ACWR conhecido dentro de 0,8–1,3 e cobertura adequada;
- 1 qualidade/semana, nunca consecutiva, nunca adjacente ao longão;
- a qualidade inicial é **introdução** (fartlek/strides ou tempo curto em RPE 6–7), não VO2max;
- máximo ~10–15% do volume semanal.

Os números são **proposta para validação científica**, não requisito definitivo. A decisão de aprovar os critérios finos é do responsável do produto; a calibração deve ser revisada contra `SPORTS_SCIENCE.md` e, se possível, com profissional de educação física.

Enquanto a entrada manual não criar uma atividade (ciclo 2), execuções `MANUAL` contam adesão, mas não volume; a elegibilidade deve declarar essa limitação em vez de fingir que o volume existe.

### 3.4 O que não muda neste ADR

- `ACWR` continua sendo **flag**, nunca meta ou diagnóstico.
- A janela de 42 dias/EMA e o viés de inicialização (BUG-03) ficam para o ciclo 2.
- A normalização de HRV (BUG-04) fica para o ciclo 2.
- A mistura TRIMP/rTSS (N-09) fica para o ciclo 2.
- A disponibilidade continua sendo teto rígido (ADR 0010). A progressão de volume a partir do realizado é uma decisão separada, que exigirá ADR substituto do 0010; não entra em C1-02.
- A semana de prova/recovery (N-03) fica no backlog do ciclo 2.
- O app não prescreve treino com base em LLM.

## 4. Alternativas consideradas

1. **Manter o estado atual (`acwr == null` libera qualidade).**
   - Simples, mas contraria “sem dados, não aumentar carga” e pode liberar progressão sem base.
2. **Bloquear qualidade sempre que `acwr == null`, sem distinguir cobertura/frescor.**
   - Mais seguro, mas trata “descanso real” e “fonte desconectada” como iguais e pode nunca liberar qualidade para quem treina sem relógio.
3. **Exigir apenas tempo/experiência (4 semanas).**
   - Fácil de explicar, mas pode liberar qualidade sem volume, consistência ou recuperação.
4. **Usar ACWR como critério único e diagnóstico.**
   - Contraria a literatura e a própria documentação; ACWR é sinal, não lei.
5. **Nunca liberar qualidade para iniciantes.**
   - Seguro, mas nega o valor do coach e mantém o iniciante estagnado.
6. **Critérios conjuntos de carga + consistência + recuperação + evidência (decisão adotada).**
   - Mais complexo, mas defensável e auditável; exige `LoadCoverage`, evidência e explicação.
7. **Progressão automática por “semana anterior ≥80%” sem evidência.**
   - Repete o problema da adesão auto-declarada; precisa de C1-04.
8. **Usar um modelo de risco de lesão por ML.**
   - Fora de escopo, sem dados, não determinístico e não auditável.

## 5. Justificativa

A ausência de dados é informação, não permissão. O app deve ser conservador quando não sabe e explicar o que falta. Ao mesmo tempo, não pode punir quem tem uma semana de descanso real ou quem não usa relógio: por isso a distinção entre `INSUFFICIENT` e `STALE`, e entre “sem atividade” e “sem dado”.

ACWR é útil como sinal de revisão, mas não deve ser a única justificativa de segurança. A decisão de qualidade combina prontidão, sono, dor, cobertura, consistência, experiência e evidência de execução. Isso está alinhado com `SPORTS_SCIENCE.md` e com a diretriz de que decisões de progressão considerem qualidade e quantidade dos dados.

Para iniciantes, a elegibilidade conquistada é o meio-termo entre “nunca” e “libera cedo demais”. Os limiares são propostos e explicitamente sujeitos a validação; o importante é que sejam documentados, testáveis e explicáveis.

## 6. Consequências

### 6.1 Positivas

- Fim da permissão automática por ACWR desconhecido.
- Dias sem dado deixam de ser zero fisiológico.
- O plano explica “dados insuficientes”/“desatualizados” em vez de fingir recuperação.
- Qualidade e progressão passam a exigir base, cobertura e evidência.
- Iniciante tem um caminho documentado de progressão, não um rótulo eterno.
- ACWR permanece flag, com ressalva explícita da literatura.

### 6.2 Negativas e custos

- Usuários com dados parciais podem perder qualidade temporariamente; é o comportamento desejado, mas exige copy clara.
- `LoadSnapshot` ganha campos e o cálculo precisa distinguir ausência de zero; mais testes.
- A elegibilidade de iniciante depende de C1-04; até lá, o iniciante continua sem qualidade.
- Os limiares de 8 dias/14 dias/15–20 km/120 min são propostas e podem mudar após validação.
- A decisão não corrige EMA/HRV/PMC; a carga exibida pode continuar enviesada no ciclo 1.
- A progressão de volume continua limitada pelo ADR 0010; o usuário pode perceber que a qualidade melhora, mas o volume não.

### 6.3 Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Bloqueio excessivo para quem não usa relógio | Distinguir `STALE` de `INSUFFICIENT`; permitir registro manual de atividade no ciclo 2; explicar o que falta |
| ACWR usado como diagnóstico | Copy e catálogo citam `EV-IMPELLIZZERI-2020`; nunca mostrar “risco de lesão” como certeza |
| Cobertura de 8 dias muito rígida/frouxa | Constante testada e documentada; revisão com dados reais |
| Frescor de 14 dias mascarar fonte quebrada | UI mostra a última atividade/importação; teste de fonte velha |
| Iniciante liberado cedo demais | Exigir evidência forte, 4 semanas, 3 corridas/semana e espaçamento; teste de limites |
| Golden de plano mudar em massa | Atualizar por cenário; comparar planos antes/depois com dados sanitizados |
| Decisão virar regra universal | ADR proposto; limiares marcados como proposta; revisão científica antes de implementar |

## 7. Impactos arquiteturais

- **`core:domain/load`:** `LoadSnapshot` com `LoadCoverage`, `LoadDataState`, `daysWithLoad`, `lastActivityAt`, `sourceFresh`; `TrainingLoadEngine` recebe frescor da fonte (ou um `ActivitySourceStatus`).
- **`core:domain/prescription`:** `WeeklyPlanGenerator` deixa de tratar `acwr == null` como permissão; `qualityAllowed` exige `LoadDataState.KNOWN`, cobertura e espaçamento; `QualityEligibility` (novo) para iniciantes, dependente de evidência.
- **`core:domain/prescription/WorkoutRecommender`:** remove `acwr ?: 0.0`; ACWR desconhecido é dado ausente, não “sem alerta”.
- **`core:data`:** o repositório de atividades expõe a data da última importação/sync para o cálculo de frescor; nenhum campo novo obrigatório no Room para C1-02 (pode ser derivado de `importedAt`/`HealthSyncStore`).
- **`:app`:** a tela Hoje/plano passa a mostrar “dados insuficientes/desatualizados” e o que falta; o catálogo ganha textos para `LoadDataState`.
- **Explicabilidade:** `R-LOAD-03`, `R-DATA-01`, `R-PLAN-02`/`R-PLAN-03`, `EV-GABBETT-2016`, `EV-IMPELLIZZERI-2020`, `EV-SEILER-2010`.
- **Testes:** cobertura, zero, constante, ausência, frescor, limites de ACWR, iniciante (limites), golden de plano, explicador.
- **Sem migração de Room** para C1-02; a elegibilidade de iniciante persistida pode exigir campo no plano em ciclo posterior.

## 8. Dependências

- C1-01 (data/fuso) para a janela de 28/42 dias e “hoje”.
- C1-04 (evidência) para a elegibilidade de iniciante e para distinguir adesão de execução comprovada.
- `SPORTS_SCIENCE.md` §6.3, §8.3–8.4.
- ADR 0010 (disponibilidade como teto) continua válido; a progressão de volume por histórico exigirá ADR futuro.
- Nenhuma dependência externa nova.
- Validação científica dos limiares antes de os tornar definitivos.

## 9. Relação com os requisitos do PRD

| Requisito | Como este ADR atende |
|---|---|
| **N-04** | ACWR desconhecido/cobertura insuficiente bloqueiam qualidade e progressão. |
| **FR-004 — Progressão de volume com guardrails** | Prepara a base de dados/frescor; a mudança de volume fica para ADR futuro. |
| **FR-005 — Progressão de qualidade para iniciantes** | Documenta critérios conjuntos e testáveis, dependentes de evidência. |
| **NFR-001 — Determinismo e auditabilidade** | Estado de carga, cobertura e frescor explícitos; regra/evidência/confiança. |
| **BUG-03 / BUG-04** | Fora do escopo imediato; registrados para o ciclo 2. |
| **N-07** | `R-PLAN-01` deixa de ser promessa não executada; a decisão de volume será formalizada em ADR futuro. |
| **BL-004 / BL-005** | Base para progressão segura e qualidade de iniciante. |

## 10. Referências

- `docs/PRD_COACH_CORRIDA.md` §6 (FR-004, FR-005), §2.1 (BUG-03, BUG-04), §2.3 (N-04, N-07)
- `docs/REVISAO_E_DIRECIONAMENTO_PRODUTO.md` §5.D4, §7.4, §7.9
- `docs/SPORTS_SCIENCE.md` §6.3, §8.3, §8.4
- `docs/adr/0010-availability-hard-cap-progression.md`
- `docs/adr/0022-deterministic-time-and-week.md`
- `docs/adr/0023-weekly-plan-vs-daily-prescription.md`
- `docs/adr/0024-completion-evidence.md`
- `docs/PLANO_TECNICO_CICLO_1.md` §6.2 e §7
