# ADR 0042 — Motor de graduação de nível (LevelGraduationEngine)

**Status:** Parcialmente implementado em 2026-11-15 (domínio: `LevelGraduationEngine` + `R-LVL-01/02` + golden `G-LVL-01`; card de aceite no app fica para a fatia 4b).
**Data:** 2026-11-15
**Escopo:** Ciclo 2 do motor do coach; `core:domain` puro; `AthleteProfile.experience`.
**Relacionados:** ADR 0028 (replanejamento sugerido), ADR 0031 (fingerprint/T4), ADR 0035 (Tier B), ADR 0038 (progressão), `R-PRESC-04` (guarda de iniciante).

---

## 1. Contexto

### 1.1 O que já existe

`AthleteProfile.experience ∈ {BEGINNER, INTERMEDIATE, ADVANCED}` é campo manual do onboarding/perfil. `WeeklyPlanGenerator` libera qualidade apenas se `experience != BEGINNER` com carga objetiva e guardrails; `DailyPrescriptionEngine` aplica `R-PRESC-04` (`QUALITY PLANNED → EASY DOWNGRADE`) para todo `BEGINNER`.

### 1.2 O problema

1. Iniciante consistente fica travado em `BEGINNER` para sempre: `qualityAllowed=false` eternamente, sem strides, sem tempo, sem caminho de saída.
2. Autosseleção no dia 1 libera qualidade sem base: basta marcar `INTERMEDIATE` para furar a guarda.
3. Nível manual não é dado auditável: plano muda sem `ruleId`/`evidenceId`, sem fingerprint, sem aceite.

### 1.3 Por que agora

Sem graduação por regra, a periodização por nível não tem chave de entrada. A graduação precisa ser determinística, sugerida e aceita — padrão T4/ADR 0031 — nunca silenciosa.

---

## 2. Restrições

1. Puro e determinístico: `LevelGraduationEngine.evaluate(profile, recentWeeks, calibration, today)` devolve `Stay | SuggestUpgrade`. Nada de rede/sensor/LLM.
2. Nunca auto-aplica. Sugestão vira card de aceite (aceitar/recusar/suprimir) estilo T4/`ReplanSuggestionStore`. Recusa é lembrada por `profile.id + engineVersion + inputsHash`.
3. Guarda de iniciante preservada: sem aceite, `R-PRESC-04` continua valendo. Graduação não reescreve `DONE`/`CLOSED`/prescrição vigente.
4. Dado ausente não aprova. Sem histórico ou sem calibração válida, resposta é `Stay` com motivo declarado.
5. Sem diagnóstico médico, sem prescrição de musculação/nutrição. Nível é capacidade de corrida demonstrada, não rótulo de valor.

---

## 3. Decisão

### 3.1 Critérios (todos obrigatórios, janela 6 semanas)

**`BEGINNER → INTERMEDIATE` (`R-LVL-01`):**

- ≥4 semanas em 6 com `strongCompletionRatio ≥ 0,80` (evidência `ACTIVITY`/`GUIDED`, ADR 0024);
- ≥3 esforços 20–35 min com FC + pace no período (mesmos do contador Tier B);
- Calibração válida: Tier A ≤56d ou Tier B com confirmação explícita;
- Sem `ILLNESS_OR_INJURY` nos últimos 14d; prontidão atual não `RED`;
- Longão ≥60 min concluído ao menos 1×.

**`INTERMEDIATE → ADVANCED` (`R-LVL-02`):**

- Critérios acima + CTL estável ou crescente em 4 semanas (carga objetiva);
- Longão ≥90 min concluído + 1 prova/TT ≤8 semanas ou TT 30 min válido;
- Monotonia média 4 semanas <2,0 (capacidade de absorver 2 qualidades).

Downgrade nunca é automático. Regressão (lesão/buraco >4 semanas) apenas sugere revisão de nível via mesmo card, sem rebaixar sozinho.

### 3.2 Contrato

- Entrada: `profile`, `recentWeeks` (até 6, com `createdOn`/status/evidência), `calibration` (tier/data/origem), `readiness` atual, `today`.
- Saída: `Stay(reason, missing[])` ou `SuggestUpgrade(from, to, reasons[], ruleIds, evidenceIds, confidence, inputsHash)`.
- Confiança máxima `MEDIUM` (heurística de produto, `LIMITED`). Toda sugestão cita `R-LVL-01/02` + `EV-SEILER-2010`/`EV-DANIELS-2014`/`EV-FOSTER-2001` conforme base.
- Aceite: app persiste `experience` novo via `ProfileRepository` + reavalia plano por `EvaluateReplan` (T4), com `fingerprint` incluindo `experience`. Prescrição do dia vigente intacta até o próximo `today`.

### 3.3 Superfícies

- Perfil: card “Pronto para subir de nível?” com motivos, dados usados/ausentes e aceitar/recusar. Hoje: sem bloqueio; plano seguinte usa o novo nível.
- Copy sem IDs na superfície, IDs no “Por quê?” (`R-LVL-01/02`).

### 3.4 Regras e catálogo

| ID | Texto | Evidências | Força |
|---|---|---|---|
| `R-LVL-01` | `BEGINNER → INTERMEDIATE` só com 4/6 semanas ≥80% forte + 3 esforços 20–35 min + calibração válida + sem doença + longão 60 min; sugestão com aceite, nunca automática | DANIELS, FRIEL, SEILER; ADR 0042 | LIMITED — convenção de produto |
| `R-LVL-02` | `INTERMEDIATE → ADVANCED` exige `R-LVL-01` + CTL estável 4 sem + longão 90 min + prova/TT + monotonia <2,0; sugestão com aceite | DANIELS, SEILER, FOSTER; ADR 0042 | LIMITED — convenção de produto |

---

## 4. Alternativas consideradas

1. **Auto-elevar silenciosamente.** Rejeitada: viola autonomia e ADR 0028/0031; plano mudaria sem aceite.
2. **Manter campo manual puro.** Rejeitada: causa o travamento/furo do §1.2.
3. **Graduar por km total ou VDOT isolado.** Rejeitada: km sem evidência forte infla; VDOT sem consistência não prova absorção de carga.
4. **Downgrade automático pós-lesão.** Rejeitado: punição sem clínica; apenas sugere revisão.

---

## 5. Matriz de testes prevista

- `LevelGraduationEngine`: 3/6 semanas → `Stay`; 4/6 + critérios → `Suggest`; sem calibração → `Stay`; doença recente → `Stay`; determinismo e `inputsHash` estável; `ADVANCED` exige monotonia.
- App: card com aceitar/recusar/suprimir; aceite persiste + reavalia plano (T4); recusa lembrada por hash; `DONE`/`CLOSED`/prescrição do dia intactos.
- Golden `G-LVL-01`: 6 semanas sintéticas → sugestão `BEGINNER → INTERMEDIATE` com motivos.

---

## 6. Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Sugestão precoce lesionar | Todos os critérios obrigatórios + carga objetiva + sem doença; confiança `MEDIUM` máx |
| Usuário ignorar e estagnar | Guarda `R-PRESC-04` continua; card reaparece na próxima janela de 6 semanas |
| Gaming (marcar fácil como forte) | Só evidência forte conta; `MANUAL` sem FC/pace não conta para esforços nem CTL objetiva |
| Divergência doc × código | Regras emitidas e testadas; `SPORTS_SCIENCE` §4.2/§14 atualizados no mesmo commit |

---

## 7. Fora de escopo

- Biblioteca de sessões por nível, mesociclo Base/Build/Peak (ADR 0041).
- VDOT automático, regressão pace×FC, previsão de prova (ADR 0035/0036 intactos).
- Migração, dependência nova, streak/nota.

---

## 8. Consequências

- Nível deixa de ser opinião digitada e vira estado sugerido por evidência, com aceite explícito — preserva autonomia, determinismo e auditabilidade (`ruleId`/`evidenceId`/`inputsHash`/`engineVersion`).
- Desbloqueia periodização por nível sem abrir qualidade para iniciante sem base.
- Nenhuma prescrição vigente é reescrita; `R-PRESC-04` segue como piso de segurança.
