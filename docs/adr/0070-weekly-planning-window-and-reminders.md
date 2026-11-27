# ADR 0070 — Janela de planejamento semanal e lembretes ancorados no treino

**Status:** Aceito e implementado em 27/11/2026.
**Data:** 2026-11-27
**Escopo:** `AthleteProfile.preferredTrainingTimeMinutes`, `ReminderScheduler`,
`ReminderWorker`, `TodayViewModel`, `TodayPreviewGenerator`, `TodayScreen`,
`WeeklyReviewCard`, `MesoCycle`/`VolumeProgression`/`TaperBaseline`, schema v23.
**Relacionados:** ADR 0023 (plano × prescrição), ADR 0028 (replanejamento
versionado), ADR 0031 (fingerprint), ADR 0044 (autoridade do dia), ADR 0048
(gate/upgrade), ADR 0049 (check-in/prontidão).

---

## 1. Contexto

O usuário 0 corre às 4h. O app gerava a semana apenas na primeira abertura da
semana, com `today` já na segunda-feira; quem corria antes de abrir o app não
tinha plano, e o lembrete fixo das 07h chegava depois do treino. O check-in
lembrava às 18h, então o dia seguinte amanhecia sem o dado subjetivo que o
`QualityGate` exige para confirmar um treino forte.

Além disso:

- a semana seguinte não existia em lugar nenhum até a segunda-feira;
- o card da semana não distinguia treino forte planejado de treino forte
  confirmado;
- a revisão semanal olhava só para a semana fechada;
- uma primeira semana criada no meio da semana parecia “quebrada” sem
  explicação.

O motor já tinha fase, mesociclo, taper, descarga e plano macro de prova. O que
faltava era uma janela de planejamento e uma comunicação coerente com a rotina
do atleta.

## 2. Decisão

### 2.1 Horário habitual no perfil

`AthleteProfile` ganha `preferredTrainingTimeMinutes: Int?` (minutos desde a
meia-noite, `null` = não informado), persistido em `athlete_profile` (schema
v23). O onboarding/perfil pergunta o horário de forma opcional.

Os lembretes passam a ser relativos ao treino:

- check-in: **30 min antes** do horário habitual;
- treino: **15 min antes** do horário habitual;
- sem horário informado, permanecem 18h e 07h.

O check-in continua sendo do dia corrente: fazer o check-in antes do treino
alimenta a prontidão e o gate daquele dia. Não há check-in noturno valendo para
o dia seguinte nesta entrega.

### 2.2 Prévia da próxima semana

Na primeira abertura do Hoje no domingo, o app gera a semana seguinte com
`weekStart = próxima segunda` e `today = domingo`, e a persiste. A prévia é uma
**intenção**, não uma promessa:

- é exibida no card “Próxima semana · Prévia” com a nota de que pode mudar até
  segunda;
- não entra em adesão, volume, mesociclo, descarga, revisão semanal ou
  referência de carga antes de sua própria semana começar;
- na segunda, o plano persistido é usado como plano da semana; o motor diário
  continua sendo a autoridade do dia e pode manter, reduzir ou rebaixar a
  sessão.

Para que uma semana criada no domingo continue sendo uma semana “inteira” na
contagem de carga, `createdOn` passa a ser considerado a tempo quando **não é
posterior** ao `weekStart` (antes era exigida igualdade). Isso vale para
`MesoCycle.isLoadWeek`, `VolumeProgression` e `TaperBaseline`.

### 2.3 Aviso de semana pronta

Um worker semanal de domingo às 18h lê a prévia persistida e notifica “Próxima
semana pronta” com dias, minutos e quantidade de treinos fortes. Se a prévia
não existe (o usuário não abriu o app no domingo), não há notificação e nenhum
plano é inventado. Mudanças de plano não geram um segundo push: o app já
atualiza o lembrete de treino ativo e mostra versão/“Ajustado” na tela.

### 2.4 Transparência na UI

- Treino forte futuro no card da semana: “Condicionado ao check-in”.
- Treino forte de hoje mantido pelo gate: “Confirmado pelo check-in de hoje”.
- Prévia da próxima semana: card próprio, sem ações de pular/replanejar.
- Primeira semana criada no meio da semana: “Sua primeira semana começa hoje; a
  próxima já vem completa.”
- Revisão semanal: bloco “Próxima semana” com volume previsto, quantidade de
  treinos fortes e longão, sempre descritivo e condicionado ao check-in.

### 2.5 O que não muda

- Nenhuma regra fisiológica, limiar, pace, volume ou critério de qualidade novo.
- A prescrição diária continua sendo a única autoridade do dia (ADR 0023).
- Replanejamento continua versionado, auditável e com aceite (ADR 0028).
- A prévia não é confirmada automaticamente por um novo motor; ela é o plano
  persistido e o dia a ajusta. Uma reavaliação automática de segunda fica para
  uma evolução com trigger próprio.
- Sem LLM em nenhuma dessas decisões.

## 3. Alternativas avaliadas

| Alternativa | Por que não |
|---|---|
| Gerar a semana só na segunda, como antes | Mantém o usuário das 4h sem plano e sem margem de planejamento. |
| Gerar um plano **final** no domingo | Usa a semana ainda aberta e o longão de domingo como se estivessem fechados; aumentaria o risco de plano nascido incompleto e churn. |
| Prévia sem persistir | Não permite aviso no domingo, revisão com “próxima semana” nem reuso na segunda; a prévia poderia contradizer o plano real. |
| Plano multi-semana persistido | Aumenta o custo de validação e a chance de prometer semanas distantes; a fase/mesociclo já são derivados. Fica para o gap 7 (visão de ciclo/prova). |
| Check-in noturno valendo para o dia seguinte | Exigiria mudar a semântica de data do check-in e a leitura de sono; a manhã resolve o cenário das 4h com o dado mais próximo do treino. |
| Push a cada mudança de plano | Ruído alto e pouca ação; o lembrete ativo já é atualizado e a tela mostra versão/ajuste. |

## 4. Evidência e força

- Não há nova alegação fisiológica. A decisão é de produto/UX e de
  arquitetura de planejamento.
- A necessidade de check-in para confirmar qualidade vem do ADR 0049; a
  autoridade diária, do ADR 0023; o gate de dados e upgrade, do ADR 0048.
- A prévia não altera o motor: usa `WeeklyPlanGenerator` com os mesmos inputs e
  as mesmas regras já citadas.
- **Força:** convenção de produto `LIMITED` para a janela de prévia e os
  horários de lembrete; `STRONG` para “plano é intenção, dia é autoridade”.

## 5. Consequências

- Schema v23 e migração `22→23` (`preferredTrainingTimeMinutes`, legado `NULL`).
- `createdOn` anterior ao `weekStart` passa a contar como semana inteira; testes
  cobrem a prévia de domingo em mesociclo e progressão.
- A prévia pode ficar desatualizada se o domingo mudar (longão, doença, prova);
  a segunda usa o plano persistido e o dia ajusta. A reavaliação automática
  fica registrada como evolução.
- O worker de prévia só notifica se o plano já existe; não gera plano em
  segundo plano.
- O card de prévia e o bloco da revisão não criam uma segunda prescrição.
- Goldens novos/atualizados: onboarding com horário, perfil com rotina, revisão
  com próxima semana e Hoje com prévia.
