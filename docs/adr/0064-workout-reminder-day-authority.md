# ADR 0064 — Lembrete de treino segue a autoridade do dia

**Status:** Aceito e implementado em 24/11/2026.
**Data:** 2026-11-24
**Escopo:** `app/reminder`, `app/application`, `MeuCoachApplication`.
Sem schema, sem motor, sem ciência.
**Relacionados:** ADR 0023 (plano × prescrição), ADR 0030 (prova),
ADR 0037 (recuperação), ADR 0044 (qualidade), ADR 0049 (prontidão).

---

## 1. Contexto

O texto da notificação de treino das 07:00 lia `WeeklyPlan.sessions` e ignorava
`DailyPrescription`. Quando o Hoje rebaixava o treino por prontidão, carga,
calor ou recuperação, a notificação continuava dizendo o plano original. O
usuário 0 recebeu "40 min intervalado" e abriu o app com um treino fácil.

O app já tem uma autoridade única do dia (`GetTodayDecision`/`DailyPrescription`,
ADR 0023); o lembrete era a única superfície que a ignorava.

## 2. Decisão

### 2.1 O worker resolve o dia, não o plano

`ReminderWorker` passou a chamar `WorkoutReminderResolver`, que usa
`RefreshTodayPrescription`/`GetTodayDecision` com perfil, semana, prontidão,
carga, calor e recuperação. A notificação mostra `DailyPrescription.title`,
duração e rationale. Quando o ajuste difere do plano, o corpo começa com
"Ajustado para hoje".

Prova hoje continua vencendo: o resolver devolve a copy de prova e o worker
cancela o lembrete de treino antes de postar.

### 2.2 O app reavalia o dia enquanto está vivo

`TodayPrescriptionSync` observa (em escopo de aplicação) as mesmas fontes do
Hoje: check-in, métricas, atividades, execuções, perfil, semana e provas. Cada
mudança reavalia o dia e persiste uma nova versão append-only quando o
`inputsHash` muda. Hoje, Coach, Tendências e o lembrete passam a ler a mesma
decisão sem depender de qual tela abriu primeiro.

### 2.3 A notificação ativa é reescrita, nunca recriada

`WorkoutReminderSync` observa a última prescrição do dia e a sessão vigente:

- prescrição nova/ajustada → `updateIfActive`, silencioso
  (`setOnlyAlertOnce` + `setSilent`), sem segundo alerta;
- descanso, prova, sessão `DONE`/`CLOSED` → cancela o lembrete de treino;
- notificação já dispensada pelo usuário → não ressuscita;
- sem plano/perfil → não cria nada.

### 2.4 O que não muda

- Nenhuma regra fisiológica, de volume ou de qualidade nasce aqui.
- A notificação não calcula pace/FC/duração: só lê a decisão.
- Sem canal novo, sem permissão nova e sem migração.
- O horário do lembrete (07:00) e o de check-in (18:00) continuam iguais.

## 3. Alternativas avaliadas

| Alternativa | Por que não |
|---|---|
| Ler só a última prescrição persistida | Se o app não foi aberto de manhã, a linha não existe e o lembrete volta ao plano |
| Manter o plano e mandar "abra o app" | Continua mostrando um treino que o app já ajustou |
| Postar uma segunda notificação | Polui a bandeja e alerta de novo pelo mesmo dia |
| Recalcular apenas ao abrir o Hoje | Coach e bandeja ficariam velhos até a tela certa abrir |

## 4. Consequências

- `TodayPrescriptionSync` e `WorkoutReminderSync` rodam no processo do app;
  o worker das 07:00 cobre o caso de app fechado.
- A sincronização pode gerar versões intermediárias durante um sync de saúde;
  `DailyPrescriptionRepository.append` deduplica por
  `(date, engineVersion, inputsHash)` e a UI lê sempre a última.
- Mais testes: `ReminderContent`, `ReminderNotifier`, `ReminderWorker`,
  `WorkoutReminderSync` e `TodayPrescriptionSync`.
- Docs e PROGRESS atualizados; sem dado real no diff.
