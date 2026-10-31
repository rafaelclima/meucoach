# ADR 0011 — Deload ancorado em semanas consecutivas de plano

**Status:** aceito em 31/10/2026

## Contexto

`R-PLAN-04` pede deload a cada 4ª semana (−20 a −30%), com evidência em
periodização por blocos (`EV-ISSURIN-2010`). O `WeeklyPlanGenerator` é puro e
recebe apenas a semana atual e a anterior. Sem uma âncora explícita, "a cada 4ª
semana" poderia virar uma regra de calendário arbitrária ou depender de um
contador não persistido.

O app já persiste um `WeeklyPlan` por semana. A forma mais simples de saber a
posição no bloco é contar quantas semanas consecutivas existem imediatamente
antes da semana atual.

## Decisão

- O bloco começa na primeira semana planejada e é contado por **semanas
  consecutivas persistidas**.
- `TrainingPlanRepository.consecutiveWeeksBefore(weekStart)` conta a cadeia
  imediatamente anterior; uma lacuna reinicia o bloco.
- O `TodayViewModel` passa `weekNumberInBlock = consecutivas + 1` para
  `WeeklyPlanInput`. A geração só ocorre depois de o histórico estar carregado,
  evitando salvar um plano com posição errada.
- A 4ª, 8ª, 12ª… semana recebe `DELOAD_FACTOR = 0.75` (**−25%**) em todas as
  sessões, preservando o tipo/intensidade (qualidade continua qualidade, com
  duração menor).
- Taper tem precedência: quando os dois coincidem, vale a redução de taper e
  `R-PLAN-04` não é citada.
- O plano cita `R-PLAN-04` e `EV-ISSURIN-2010` e explica o deload no rationale.

## Alternativas rejeitadas

1. **Semana do calendário (ISO) módulo 4:** gera deload na primeira semana de uso
   e não representa o bloco do atleta.
2. **Nova coluna `blockWeek` no Room:** exigiria schema v10 e migração para um
   dado que pode ser derivado do histórico existente.
3. **Contador em memória:** reinicia a cada processo e torna o plano
   não determinístico entre aberturas do app.
4. **Aplicar deload e taper juntos:** empilharia reduções e produziria semanas
   artificialmente curtas antes da prova.

## Consequências

- Nenhuma migração nova; a regra usa o histórico que já é persistido.
- Um usuário que pula semanas sem plano reinicia o bloco de 4 semanas, o que é
  conservador e previsível.
- A redução de volume é verificável no domínio e no plano persistido.
- Se no futuro houver periodização explícita, o contador pode ser substituído
  por um bloco nomeado, com novo ADR.
