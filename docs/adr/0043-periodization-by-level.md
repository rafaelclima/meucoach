# ADR 0043 — Periodização por nível e plano de prova (FR-012)

**Status:** Aprovado em 2026-11-15; fatias 1–3 implementadas em 15/11/2026 (`MacroPlanResolver` + `SessionLibrary` + fiação no gerador, `R-PER-01/02`, golden `G-PER-02`).
**Data:** 2026-11-15
**Escopo:** Motor do coach; `WeeklyPlanGenerator`, catálogo de sessões, `PlanningFingerprint`.
**Relacionados:** ADR 0010 (teto de disponibilidade), ADR 0011 (deload ancorado),
ADR 0023 (plano × prescrição), ADR 0028 (replanejamento), ADR 0030 (evento de prova),
ADR 0031 (fingerprint/T4), ADR 0032 (auto-conclusão), ADR 0036 (previsão VDOT/Riegel),
ADR 0037 (recuperação pós-prova), ADR 0041 (mesociclos), ADR 0042 (graduação);
FR-012; D7 aprovada (um objetivo ativo); `EV-ISSURIN-2010`.

---

## 1. Contexto

### 1.1 O que já existe

- `MesoPhase {BASE, BUILD, PEAK, TAPER, RECOVERY}` derivada de `recentWeeks` reais
  (ADR 0041, `weekly-plan-v6`); sem prova, alterna `BASE`/`BUILD` em blocos de 4, sem `PEAK`.
- Graduação de nível como chave de entrada determinística com aceite explícito
  (ADR 0042, `R-LVL-01/02`); guarda de iniciante (`R-PRESC-04`) preservada.
- Taper 2 semanas (5k–meia) / 3 (maratona) com micro-dose (`R-PLAN-05/10`),
  recuperação pós-prova 2/3/5/10d (`R-RACE-04`), evento de prova com data e
  distância (ADR 0030), auto-conclusão conservadora (ADR 0032) e previsão de
  referência por VDOT/Riegel (ADR 0036, nunca alvo de prescrição).
- Deload por carga demonstrada (`R-PLAN-04 v2`), progressão +6% do realizado
  (`R-PLAN-01`), teto de 2 qualidades (`R-PLAN-02`), disponibilidade como teto (ADR 0010).
- D7 aprovada: um objetivo ativo por vez (geral + 5k/10k/meia/maratona, data no modo prova).

### 1.2 O problema

1. Com prova-alvo, não há distribuição de blocos até a data: os aceites 1–2 do
   FR-012 (5k em 8 semanas → blocos + taper; maratona → taper de 3 semanas) não
   são atendidos; o taper só comprime a semana quando ela chega.
2. Sem data, nada impede inventar plano de prova (aceite 3 do FR-012); hoje isso
   só não acontece porque não há caminho de prova sem evento — a regra precisa
   existir por escrito.
3. Prova no meio da semana (aceite 4): a semana da prova já é sem `QUALITY`, mas
   o permitido após a prova não é regra explícita — decisão de revisão: só EASY
   curto regenerativo, sem qualidade/longão.
4. Sessões não diferem por nível: só o gate de qualidade distingue `BEGINNER`;
   a biblioteca de sessões por nível ficou fora de escopo dos ADR 0041/0042.

### 1.3 Por que agora

Os ADR 0041 e 0042 fecharam as duas pré-condições (fase derivada do histórico +
chave de nível com aceite). FR-012 é o próximo passo natural e o último item
grande do motor antes de N-05; propor o ADR agora mantém o ciclo auditável.

---

## 2. Restrições

1. Motor determinístico e puro, sem Android/Room/rede/LLM (ADR 0004).
2. Plano é intenção, prescrição diária é autoridade (ADR 0023): periodização nunca
   reescreve `DONE`/`CLOSED`/hoje/passado.
3. Disponibilidade continua teto rígido (ADR 0010); guardrails de carga, prontidão
   e doença/lesão intactos; dado ausente é `null` (R-DATA-01).
4. Sem data de prova válida, só plano geral: sem `PEAK`, sem taper, sem blocos de
   prova — nunca inventar preparação (aceite 3 do FR-012).
5. Sem migração Room nesta decisão: intenções semanais são derivadas; o
   `PlanningFingerprint` ganha versão/hash novo, sem coluna nova.
6. Sem culpa/streak/nota; sem nutrição, força, diagnóstico ou LLM decidindo carga.

---

## 3. Decisão (proposta)

### 3.1 Macro-planejador puro

Nova função pura `resolveMacroPlan(perfil, objetivo/evento, recentWeeks, calibração, today)`
que devolve intenções semanais (fase + papel das sessões) até a prova, ou ciclo
geral aberto. Distribuição proposta por distância (convenção de produto, `LIMITED`):

| Prova | Horizonte | BASE | BUILD | PEAK | TAPER |
|---|---|---|---|---|---|
| 5k/10k | 8 sem | 3 | 2 | 1 | 2 |
| Meia | 12 sem | 4 | 4 | 2 | 2 |
| Maratona | 16 sem | 6 | 5 | 2 | 3 |
| Sem data | aberto | alterna `BASE`/`BUILD` em blocos de 4 (ADR 0041), sem `PEAK` | | | |

Taper e recuperação reutilizam `R-PLAN-05/10` e `R-RACE-04` sem duplicar regra.
`PEAK` mantém volume estável (base × 1,06, ADR 0041) e só especifica intensidade.

### 3.2 Biblioteca de sessões por nível (proposta)

Catálogo puro `(fase, nível) → estruturas de sessão`, sem pace inventado (paces
derivam da calibração vigente; sem calibração, RPE — nunca número solto):

- `BEGINNER`: máx. 1 qualidade; `LONG` com teto; velocidade só como strides;
  guarda `R-PRESC-04` continua valendo mesmo em `BUILD`.
- `INTERMEDIATE`: até 2 qualidades (não consecutivas, `R-PLAN-02`); 1 específica
  por semana em `BUILD`/`PEAK`.
- `ADVANCED`: 2 qualidades; `PEAK` específico; `LONG` ≥ 90 min quando a base permite.

### 3.3 Onde entra

| Camada | Mudança proposta |
|---|---|
| `WeeklyPlanGenerator` | consome a intenção da semana (fase) em vez da alternância cega sem prova; rationale declara `Bloco X de Y` + contagem regressiva; papéis (estruturas) ficam para a fatia 4 |
| `MesoCycle` | com prova dentro do horizonte, a fase macro prevalece sobre o ramo de prova (decisão do usuário 0 em 15/11/2026); sem prova, a alternância segue; descarga continua pela contagem de semanas fortes; recuperação suprema |
| `PlanReplanner` | sem mudança: `race` + `today` já estão no `inputsHash`, então qualquer mudança de intenção macro invalida como `STALE` (padrão T4, ADR 0031/0028), nunca auto-aplica |
| `PlanningFingerprint` | sem mudança (objetivo/evento já cobertos); recusa/supressão seguem o padrão T4 |
| Semana da prova | EASY curto regenerativo permitido após a prova (sem qualidade/longão, rationale explícito); semana seguinte é recuperação (`R-RACE-04`) |

### 3.4 Regras e catálogo (propostas)

| ID | Texto | Evidências | Força |
|---|---|---|---|
| `R-PER-01` | Blocos até a prova pela tabela do §3.1; sem data, só plano geral sem `PEAK`/taper | ISSURIN, BOSQUET, MUJIKA; ADR 0043 | LIMITED — convenção de produto |
| `R-PER-02` | Sessões por (fase, nível) conforme §3.2; `BEGINNER` nunca recebe 2ª qualidade | DANIELS, SEILER, FOSTER; ADR 0042/0043 | LIMITED — convenção de produto |

Confiança máxima `MEDIUM`, como no ADR 0042 (heurística de produto, não consenso).

---

## 4. Alternativas consideradas

1. **Persistir 16 semanas de plano pronto.** Rejeitada: viola plano-como-intenção
   (ADR 0023); qualquer check-in invalida o futuro e o mecanismo `STALE` já existe.
2. **Blocos fixos de calendário.** Rejeitada: ignora adesão e prova-alvo (mesmo
   argumento do ADR 0041 §4.3).
3. **LLM para montar a preparação.** Rejeitada: viola ADR 0004; blocos são regra.
4. **Gerar tudo no app sem domínio puro.** Rejeitada: UI não calcula regra de
   treino (AGENTS.md regra 2).

---

## 5. Matriz de testes prevista

- `MacroPlan`: 5k em 8 semanas → `BASE×3, BUILD×2, PEAK, TAPER×2`; maratona →
  taper de 3; sem data → geral sem `PEAK`/taper; determinismo + hash estável.
- `WeeklyPlanGenerator`: `BEGINNER` nunca recebe 2ª qualidade mesmo em `BUILD`
  (trava confirmada pelo usuário 0 em 15/11/2026);
  prova na 4ª feira → só EASY curto após, nada de qualidade/longão;
- Golden `G-PER-02`: 8 semanas sintéticas até 5k → blocos + taper declarados.

---

## 6. Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Objetivo vazio/desatualizado | Caminho geral como fallback + confiança `LOW` + rationale explícito |
| Histórico curto no rollout | `BASE` sem `PEAK`; progressão `R-PLAN-01` já é conservadora sem base |
| Preparação agressiva para iniciante | §3.2 + guarda `R-PRESC-04`; confiança máxima `MEDIUM` |
| Doc × código divergirem | Regras emitidas e testadas; `SPORTS_SCIENCE` §8 atualizado no commit de implementação |

---

## 7. Fora de escopo

- Migração, dependência nova, mudança de UI além de rationale/fase no plano.
- Pace-alvo de prova como prescrição (previsão do ADR 0036 segue referência).
- Múltiplos objetivos simultâneos; trail/terreno; nutrição/força; LLM.

---

## 8. Consequências

- Fecha o FR-012 com o motor existente (taper, recovery, evento, previsão,
  mesociclo e graduação viram peças de uma preparação, não peças soltas).
- O plano ganha horizonte sem perder determinismo, auditabilidade ou autonomia
  (intenções derivadas + aceite T4, sem auto-aplicar).
- A guarda de iniciante continua como piso: graduação (ADR 0042) segue sendo a
  única chave de saída do `BEGINNER`.
