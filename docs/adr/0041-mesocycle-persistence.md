# ADR 0041 — Persistência de mesociclos por histórico real

**Status:** Implementado em 2026-11-15 (`weekly-plan-v6`, sem migração).
**Data:** 2026-11-15
**Escopo:** Ciclo 2 do motor do coach; `WeeklyPlanGenerator`, `PlanReplanner`, `VolumeProgression`.
**Relacionados:** ADR 0010 (teto de disponibilidade), ADR 0011 (deload ancorado), ADR 0023 (plano × prescrição), ADR 0028 (replanejamento), ADR 0031 (fingerprint), ADR 0038 (progressão por concluído).

---

## 1. Contexto

### 1.1 O que já existe

`WeeklyPlanGenerator` recebe `weekNumberInBlock: Int` calculado pelo app a partir da sequência ininterrupta de planos. A cada 4ª semana consecutiva aplica deload −25% (`R-PLAN-04`, `EV-ISSURIN-2010`). `VolumeProgression` já lê `recentWeeks` (até 4 semanas) para a base de volume (ADR 0038), mas a posição no bloco não usa esse histórico.

### 1.2 O problema

1. Atleta inconsistente (buraco de 1–2 semanas sem plano) reinicia o bloco e nunca recebe deload, acumulando 6–8 semanas de carga sem descarga.
2. Atleta consistente recebe deload fixo a cada 4 semanas mesmo em Build inicial ou em semana de prova, onde taper já cobre a descarga.
3. Não existe conceito de Base/Build/Peak/Taper. `EV-ISSURIN-2010` é citado mas nunca implementado além do deload. Ciclo de 12–16 semanas para prova-alvo é impossível.

Verificado em `WeeklyPlanGenerator.kt` (`deload = weekNumberInBlock % 4 == 0`) e `volumeWeekBlock()`.

### 1.3 Por que agora

O Ciclo 2 precisa de periodização por nível sem quebrar determinismo. O histórico auditável (`weekly_plans` + `weekly_plan_revisions`, schema v17) já existe desde ADR 0028/0031/0038. Falta apenas derivar o mesociclo dele em vez de um contador volátil.

---

## 2. Restrições

1. Motor determinístico, puro, sem Android/Room/rede/LLM (ADR 0004). Mesmos inputs + mesma versão = mesma semana.
2. Plano é intenção, prescrição diária é autoridade (ADR 0023). Mesociclo nunca reescreve `DONE`/`CLOSED`/hoje/passado.
3. Disponibilidade continua teto rígido (ADR 0010). Dado ausente é `null`, nunca `0` (`R-DATA-01`).
4. Sem migração nesta decisão. `recentWeeks` já trafega; posição no bloco é derivada, não persistida como nova coluna.
5. Sem culpa/streak/nota. Deload/taper não viram punição.

---

## 3. Decisão

### 3.1 Derivar bloco do histórico real

`WeeklyPlanGenerator` passa a calcular a posição no bloco a partir de `recentWeeks` (ordenadas por `weekStart`, filtradas por `createdOn == weekStart` e fora de semanas especiais interrompidas):

- Conta semanas consecutivas com ao menos 1 sessão `DONE` com evidência forte para trás a partir de `targetWeekStart - 1 semana`. Buraco (sem plano ou sem conclusão forte) zera a contagem para efeito de deload, mas preserva a fase do macrociclo.
- Deload vale quando houver 3 semanas de carga seguidas + 4ª de descarga, não quando um contador externo diz 4.
- Taper (`R-PLAN-05`/`R-PLAN-10`) e recuperação (`R-RACE-04`) têm precedência sobre deload. Semana de prova nunca é deload.

### 3.2 Macrociclo 12–16 semanas

Novo enum puro `MesoPhase { BASE, BUILD, PEAK, TAPER, RECOVERY }` resolvido por distância até a prova e semanas de carga acumulada:

- Sem prova: alterna `BASE` (fácil + strides) e `BUILD` (1–2 qualidades) em blocos de 4, sem `PEAK`.
- Com prova 12–16 semanas à frente: `BASE` até −8 semanas, `BUILD` até −3 semanas, `PEAK` 2–3 semanas (volume estável, 2 qualidades específicas), `TAPER` conforme §8.6.
- `PEAK` nunca aumenta volume além de base × 1,06; apenas especifica intensidade. Aumentar tipo/duração continua proibido na prescrição do dia.

### 3.3 Onde entra

| Camada | Mudança |
|---|---|
| `WeeklyPlanInput` | `recentWeeks` passa a incluir até 12 semanas; `weekNumberInBlock` deprecated, mantido 1 release como fallback quando histórico vazio |
| `WeeklyPlanGenerator` | resolve `MesoPhase` + deload do histórico; cita `R-PLAN-04` ou `R-PLAN-05/10` ou `R-RACE-04`, nunca dois cortes juntos; rationale declara fase e base |
| `PlanReplanner` | repassa histórico e inclui fase no `inputsHash`; não repõe carga, não muda fase no meio da semana |
| `PlanningFingerprint` | inclui `mesoPhase` e contagem de semanas fortes; mudança de fase por novo histórico gera `STALE` via sugestão (ADR 0031/0028) |

Versões: `weekly-plan-v6`. Planos `v5` viram referência não-especial apenas se sem `R-PLAN-04/05/10/R-RACE-04` (precedente ADR 0038).

### 3.4 Regra e catálogo

`R-PLAN-04 v2`: deload a cada 4ª semana de carga demonstrada (−20 a −30%), derivado de `recentWeeks` reais; taper/recuperação prevalecem. `EV-ISSURIN-2010`, `MODERATE`.

---

## 4. Alternativas consideradas

1. **Manter contador de planos consecutivos.** Rejeitada: causa os dois bugs do §1.2.
2. **Persistir `mesoPhase` em nova coluna Room.** Rejeitada nesta fatia: cria migração e segunda fonte de verdade; fase é derivável.
3. **Blocos fixos de calendário (mês = mesociclo).** Rejeitada: ignora adesão e prova-alvo.
4. **LLM para classificar fase.** Rejeitada: viola ADR 0004; fase é regra, não texto.

---

## 5. Matriz de testes prevista

- `WeeklyPlanGenerator`: buraco de 2 semanas não conta para deload; 3 fortes seguidas + 4ª = deload; taper sobrepõe deload; sem histórico = `BASE` sem deload; determinismo com mesma `recentWeeks`.
- `PlanReplanner`: fase no hash; sugestão `STALE` quando histórico muda; futuro só, sem tocar `DONE`/`CLOSED`.
- Golden `G-PER-01`: 8 semanas sintéticas com buraco → deload na semana correta.

---

## 6. Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Histórico curto no rollout | Fallback para contador legado + confiança `LOW` + rationale explícito |
| Deload nunca chegar para irregular | Contagem por semanas fortes, não por calendário; buraco não pune, só adia |
| Doc × código divergirem | `R-PLAN-04 v2` emitida e testada; `SPORTS_SCIENCE` §8.1/§8.3 atualizados no mesmo commit |

---

## 7. Fora de escopo

- Biblioteca de sessões por nível (ADR 0042).
- Volume em km, treino livre como base, FR-206.
- Migração, dependência nova, mudança de UI além de rationale/fase.

---

## 8. Consequências

- Uma decisão local, determinística e auditável: mesmos `recentWeeks` + perfil + data = mesma fase e mesma semana, com `ruleId`/`evidenceId`/confiança.
- Atleta inconsistente recebe descarga quando acumula carga real, não quando o calendário manda.
- `ENGINE_VERSION` → `weekly-plan-v6`; schema permanece v17.
