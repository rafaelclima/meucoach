# ADR 0013 — Troca manual de sessões subordinada aos guardrails

**Status:** aceito em 31/10/2026

## Contexto

O plano semanal é persistido e o usuário pode querer mover uma sessão de dia
(por agenda, chuva, esteira ou recuperação). Sem uma ação explícita, a única
saída seria pular a sessão ou editar a disponibilidade, o que não reflete a
intenção de manter o treino e apenas mudar o dia.

A troca não pode criar uma semana inválida: duas qualidades consecutivas ou
qualidade no dia do longão/ao lado dele violam `R-PLAN-02`/`R-PLAN-03`.

## Decisão

- A troca ocorre entre **duas sessões planejadas da mesma semana**, com datas
  iguais ou futuras em relação a hoje.
- O **conteúdo** (tipo, título, duração, rationale, regras e evidências) é
  trocado; **data e status permanecem** com o dia.
- Sessões concluídas, puladas ou passadas não entram na troca; descanso também
  não.
- A semana resultante é validada com as mesmas regras de espaçamento do
  gerador (máx. 2 qualidades, sem qualidades consecutivas, qualidade nem no dia
  nem ao lado do longão).
- A persistência atualiza somente as colunas de conteúdo das duas sessões,
  preservando `status`, `skipReason` e `completedAt` das demais.
- O volume semanal, `longRunMinutes` e `qualityCount` são recalculados.
- A UI mostra os dias candidatos e o motivo quando uma troca é recusada.

## Alternativas rejeitadas

1. **Regenerar a semana após a troca:** descartaria conclusões e o histórico do
   plano, além de tornar a ação não determinística.
2. **Permitir qualquer troca:** poderia criar duas qualidades seguidas ou
   qualidade colada no longão.
3. **Trocar as datas em vez do conteúdo:** moveria `DONE`/`SKIPPED` e o
   `completedAt` para outro dia, falseando o histórico.
4. **Editar a disponibilidade para forçar o dia:** muda o teto de volume e afeta
   semanas futuras, não apenas a sessão atual.

## Consequências

- Nenhuma migração nova; a troca usa as colunas existentes.
- O usuário ganha flexibilidade sem furar os guardrails.
- Algumas trocas são recusadas com explicação, o que é intencional.
- A sessão guiada é local e não rastreia GPS; concluir marca a sessão do dia
  como feita, sem criar carga inventada.
