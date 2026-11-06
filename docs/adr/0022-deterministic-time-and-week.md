# ADR 0022 — Tempo determinístico, fuso e virada de dia/semana

**Status:** aceito
**Data:** 2026-11-06
**Responsável:** planejamento com Rafael (usuário 0)
**Implementação:** C1-01 concluída em 06/11/2026.

---

## 1. Contexto

O app já depende de “hoje” em quase todo o loop:

- check-in diário por `LocalDate`;
- métricas diárias e prontidão por data local;
- plano semanal segunda→domingo e adesão por semana;
- lembretes de check-in (18h) e de treino (7h);
- janela de carga de 42 dias e ACWR de 7/28;
- sincronização incremental do Health Connect;
- histórico de prontidão e tendências.

Hoje, esse “hoje” é obtido de formas diferentes:

- `LocalDate.now()` / `LocalTime.now()` / `ZonedDateTime.now()` aparecem em **33 pontos** de `app/src/main` e `core`;
- `TodayViewModel` calcula `currentWeekStart` uma vez no construtor e não reage à virada;
- `WeeklyPlanInput.weekStart` tem default `LocalDate.now()`;
- `ReminderScheduler` usa o fuso do sistema, não necessariamente o fuso do perfil;
- `HealthSyncService` já recebe `Clock`, mas o app monta a maior parte das datas fora dele;
- testes dependem de datas reais em alguns fluxos, o que torna a suíte sensível ao dia em que roda.

O perfil já guarda `timezone` (IANA) e esse é o fuso que deve mandar em todas as decisões locais. O `ZoneId` do sistema é apenas fallback antes do onboarding.

## 2. Problema

Sem uma fonte única de tempo:

1. um check-in salvo às 23:59 pode cair no dia errado se a tela recalcular a data no meio do fluxo;
2. o Hoje pode continuar mostrando o plano da semana anterior depois da meia-noite ou na segunda-feira;
3. o lembrete pode disparar no fuso do aparelho, não no fuso do perfil;
4. o mesmo input pode gerar decisões diferentes conforme o dia em que o teste/build roda;
5. a janela de carga e a adesão semanal podem usar limites diferentes (“hoje” de um ViewModel, “hoje” de outro);
6. a auditoria de uma prescrição fica incompleta: não se sabe qual data/fuso gerou a decisão.

O problema não é “faltar `Clock`”; é **não existir uma definição única e observável de dia local**.

## 3. Decisão

1. **Fuso do perfil é a fonte única.** Toda data local deriva de `AthleteProfile.timezone`. Antes do onboarding, o app usa `ZoneId.systemDefault()` como fallback explícito e troca para o fuso do perfil quando ele é salvo.
2. **Tempo é dependência injetável, nunca chamada estática.**
   - `core:domain` e `core:data` recebem `Clock` (java.time) ou um `LocalDate`/`weekStart` explícito.
   - `:app` recebe uma interface `TimeProvider` que encapsula `Clock`, `ZoneId` e a noção de “hoje”.
   - `LocalDate.now()`, `LocalTime.now()` e `ZonedDateTime.now()` não podem aparecer em `ui/`, ViewModels, geradores ou schedulers; a exceção é a implementação única de `SystemTimeProvider`.
3. **Dia atual é um fluxo observável.**
   - `TimeProvider.today(): StateFlow<LocalDate>` emite:
     - na primeira assinatura;
     - na virada da meia-noite no fuso do perfil;
     - quando o app volta do background (`LifecycleResumeEffect`);
     - quando o fuso do perfil muda;
     - quando o sistema muda de fuso e o perfil não tem fuso explícito.
   - O fluxo calcula o próximo instante de meia-noite com `ZonedDateTime` e `Duration`, sem `AlarmManager`; é um sinal de UI/processo, não um agendamento de sistema.
4. **Semana é segunda→domingo no fuso do perfil.** `weekStart` é sempre uma segunda-feira normalizada; o fluxo de dia também emite quando `weekStart` muda.
5. **Domínio não tem default de data.** `WeeklyPlanInput` passa a exigir `weekStart` e `today`; `PlanCompletionCalculator` já recebe `today`. Nenhum gerador chama `LocalDate.now()`.
6. **ViewModels e telas recebem a data do estado.** `TodayViewModel`, `CheckInViewModel`, `TrendsViewModel`, `ReadinessHistoryViewModel`, `CalibrationViewModel`, `MetricsImportViewModel`, `FirstRunChecklistViewModel` e `TutorialViewModel` deixam de calcular a data e passam a observar o `TimeProvider` ou receber a data via estado.
7. **Lembretes usam o fuso do perfil.** O agendamento é recalculado quando o perfil é criado/editado, quando o fuso muda e depois de um reset. O conteúdo do lembrete (“check-in de hoje”, “treino de hoje”) usa a data do `TimeProvider`.
8. **Health Connect usa o mesmo fuso e o mesmo `Clock`.** A janela incremental e a data de “hoje” já usam `Clock`; passam a usar exclusivamente o fuso do perfil, com fallback documentado.
9. **Persistência continua em UTC + `zoneId` + `localDate`.** O ADR não muda o modelo de dados; muda quem calcula a data e como ela é observada.
10. **Testes usam tempo controlado.** Testes de domínio passam `today`/`weekStart`; testes de app usam `FakeTimeProvider`; nenhum teste depende do relógio da máquina.

### 3.1 Contrato do `TimeProvider`

```kotlin
// :app — time/TimeProvider.kt
interface TimeProvider {
    val today: StateFlow<LocalDate>
    val zoneId: StateFlow<ZoneId>

    fun now(): Instant
    fun currentDate(): LocalDate
    fun currentWeekStart(): LocalDate
    fun atStartOfDay(date: LocalDate): ZonedDateTime
    fun nextMidnight(): Instant
}
```

Implementação:

- `SystemTimeProvider` (fallback antes do perfil);
- `ProfileTimeProvider` (lê o perfil e reage a mudanças de `timezone`);
- `FakeTimeProvider` nos testes (permite avançar relógio e trocar fuso).

### 3.2 Regras de borda

- Uma atividade que cruza a meia-noite pertence ao dia de **início** (regra já existente em `DATA_MODEL.md` §5).
- Um check-in salvo às 23:59 pertence ao dia em que a ação foi confirmada, capturado no estado da tela, não recalculado no `save`.
- A virada de semana não deve apagar o plano anterior: ele permanece consultável; o Hoje apenas passa a apontar para a nova semana.
- Se o fuso do perfil mudar e a data local “pular” ou “repetir” um dia, a decisão do dia é recalculada e uma nova versão de prescrição é registrada (ADR 0023), nunca sobrescrita.
- DST: o próximo instante de meia-noite é calculado por `ZonedDateTime`; fusos sem meia-noite inexistente são tratados pelo próprio `java.time`. O app não inventa offset.

## 4. Alternativas consideradas

1. **Manter `LocalDate.now()` e apenas trocar por `Clock` nos testes.**
   - Simples, mas mantém 33 pontos de decisão e não resolve virada de dia/semana nem fuso do perfil.
2. **Passar `today: LocalDate` por parâmetro em todos os componentes, sem fluxo.**
   - Bom para domínio, insuficiente para UI: a tela não reage à meia-noite nem ao resume.
3. **Usar `AlarmManager` para acordar exatamente à meia-noite.**
   - Overkill e sujeito a restrições de bateria/OEM; o app só precisa reagir quando está em uso. O `WorkManager` continua cuidando de lembretes.
4. **Usar UTC em todo o app.**
   - Evita ambiguidade interna, mas o usuário vive em dias locais; check-in, plano e lembretes precisam do fuso do perfil.
5. **Criar um módulo `:core:time`.**
   - Não se justifica para uma interface e dois adaptadores; `:app/time` + `Clock` no domínio é suficiente.
6. **Persistir um “relógio” no perfil.**
   - Fuso não é dado imutável; deve ser lido do perfil atual e observado.

## 5. Justificativa

O loop do produto é diário e semanal. Se “hoje” não for único, o app pode:

- recomendar o treino de ontem;
- salvar o check-in no dia errado;
- calcular adesão com uma janela diferente da usada no plano;
- perder a auditoria de qual data gerou a decisão.

Uma dependência injetável + fluxo de dia resolve o problema na raiz e torna os testes determinísticos. O custo é uma travessia ampla por ViewModels e telas, mas sem nova funcionalidade e sem mudança de schema. É a menor mudança que garante o comportamento prometido por `NFR-005`.

## 6. Consequências

### 6.1 Positivas

- Uma única definição de “hoje” e de “semana”.
- Check-in, plano, lembretes e carga passam a concordar.
- Testes de 23:59/00:01, segunda-feira e mudança de fuso ficam possíveis e rápidos.
- A prescrição diária pode persistir `date` + `zoneId` + `engineVersion` com confiança.
- O `HealthSyncService` e o `WorkManager` deixam de depender do fuso do aparelho.
- Ao mudar o fuso do perfil, o app se corrige sem reiniciar o processo.

### 6.2 Negativas e custos

- Muitos ViewModels e telas serão tocados; o risco de regressão visual/golden é médio.
- O fluxo de dia precisa ser cancelado corretamente para não vazar corrotina.
- Testes existentes que usam `LocalDate.now()` implícito precisarão de data explícita.
- O comportamento em DST precisa de teste com um fuso que tenha DST, embora o usuário 0 esteja em Natal/RN.
- O `TimeProvider` no `:app` não pode ser usado pelo domínio (que continua puro); é preciso manter a fronteira.

### 6.3 Riscos e mitigação

| Risco | Mitigação |
|---|---|
| ViewModel continua chamando `now()` em algum canto | Teste de arquitetura/grep no CI + revisão no gate C1-01 |
| Loop de meia-noite acordando cedo/tarde | Teste com `FakeTimeProvider` em 23:59:59 e 00:00:01; cálculo por `ZonedDateTime` |
| Fuso inválido no perfil | `runCatching { ZoneId.of(...) }` + fallback para sistema, com confiança explícita |
| Lembrete reagendado duas vezes | Scheduler idempotente por nome único; teste de `WorkManager` |
| Golden muda por data | Goldens usam `FakeTimeProvider` fixo |

## 7. Impactos arquiteturais

- **Novo pacote** `app/src/main/kotlin/com/meucoach/app/time/` com `TimeProvider`, `SystemTimeProvider`, `ProfileTimeProvider`, `DayChangeSignal` e `FakeTimeProvider` (este em `src/test`).
- **DI:** `AppModule`/`TimeModule` fornece `TimeProvider`; repositórios e serviços continuam recebendo `Clock` ou data explícita.
- **Domínio:** `WeeklyPlanInput.weekStart` deixa de ter default; `WeeklyPlanInput.today` é adicionado. Nenhum `java.time.now()` no domínio.
- **Dados:** `TrainingPlanRepository`, `UserDataRepository`, `CheckInRepository`, `DailyMetricsRepository` e `HealthSyncService` já aceitam `Clock`; a mudança é garantir que o app injete o mesmo relógio/fuso.
- **UI:** `TodayRoute`/`CheckInRoute` observam `TimeProvider`; `TodayUiState` carrega `today: LocalDate`; nenhuma tela chama `now()`.
- **WorkManager:** `ReminderScheduler.schedule(zoneId, now)`; reagendamento em mudança de perfil/fuso e após reset.
- **Testes:** `FakeTimeProvider` + `MutableClock`; goldens fixam a data.
- **Sem migração de Room** nesta entrega.

## 8. Dependências

- `java.time` (já usado; sem dependência nova).
- `kotlinx.coroutines` (já usado).
- `WorkManager` (já usado) para lembretes.
- Perfil com `timezone` (já existente).
- C1-01 é pré-requisito de C1-02 (janela de carga), C1-03 (data da prescrição), C1-04 (data da execução) e C1-05 (expurgo por clock).
- Nenhuma dependência externa nova.

## 9. Relação com os requisitos do PRD

| Requisito | Como este ADR atende |
|---|---|
| **NFR-005 — Tempo e fuso** | Fonte única, fuso do perfil, virada de dia/semana e testes com clock fixo. |
| **NFR-001 — Determinismo e auditabilidade** | Mesmo input + mesma data/fuso ⇒ mesma decisão; data e fuso persistidos na prescrição. |
| **FR-001 — Prescrição única do dia** | A prescrição é sempre “do dia local”; não pode haver duas datas concorrentes. |
| **FR-009 — Exclusão e retenção** | Expurgo e reset usam clock injetável; testes determinísticos de 7 dias. |
| **BUG-09 / ARCH-03** | Elimina `LocalDate.now()`/`LocalTime.now()` de ViewModels e schedulers. |
| **BL-010 — Tempo/fuso determinístico** | Entrega a base técnica do bloco. |

## 10. Referências

- `docs/PRD_COACH_CORRIDA.md` §7 (NFR-001, NFR-005) e §6 (FR-001, FR-009)
- `docs/ARCHITECTURE.md` §3, §6 e §7
- `docs/DATA_MODEL.md` §5 (timezone e dia local)
- `docs/adr/0004-deterministic-engine.md`
- `docs/adr/0023-weekly-plan-vs-daily-prescription.md`
- `docs/PLANO_TECNICO_CICLO_1.md` §6.1 e §7
