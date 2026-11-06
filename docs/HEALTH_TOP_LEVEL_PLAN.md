# Plano — Saúde no topo e sync automático do Health Connect

**Status:** implementado em 05/11/2026 — Fases 1–3 concluídas
**ADRs:** [0020](adr/0020-health-top-level-destination.md) · [0021](adr/0021-health-sync-on-resume.md)
**Fase do roadmap:** 1 — Coach (P1-14, P1-15 e P1-16)

> Evidências da implementação: `./gradlew check`, `:app:assembleDebug` e
> `:app:verifyRoborazziDebug` verdes; 868 testes; goldens de Hoje, Saúde e do
> card de saúde no Perfil regravados. O resumo compacto no Hoje foi avaliado e
> adiado para não competir com “Iniciar”; a proveniência por campo continua
> como decisão futura.

---

## 1. Objetivo

1. Tirar a tela “Saúde e dados” do Perfil e colocá-la em evidência como destino
   principal, sem transformar o Hoje em um segundo painel de saúde.
2. Fazer o Health Connect atualizar sozinho quando o usuário abre o app, com
   worker diário como fallback, mantendo o botão manual como override.

## 2. Fora de escopo

- Novo modelo de dados, tabela ou migração de Room.
- Novas permissões do Health Connect (a lista continua: sono, FC de repouso,
  HRV e SpO₂).
- Leitura de exercícios/passos ou escrita no Health Connect.
- LLM, backend, sync em nuvem ou analytics.
- Redesenho dos gráficos: o painel atual é reaproveitado.
- Tela separada de “Gerenciar fontes” nesta fase (só se a seção crescer).

## 3. Estado atual

```
Barra inferior: Hoje · Corridas · Tendências · Perfil
Perfil
  └── card “Saúde e dados” ──► rota profile/metrics-import (com seta de voltar)
Hoje
  └── checklist (primeiro uso) ──► mesma rota
Tutorial
  └── cena 3 (Health Connect) ──► mesma rota
```

- `MetricsImportScreen` mistura dashboard de recuperação, fontes (Health
  Connect/CSV) e resumo do que está salvo.
- `ProfileViewModel` mantém um `ProfileHealthSummary` só para o card do Perfil.
- `HealthSyncService` é chamado pelo botão manual e pelo `HealthSyncWorker`.
- `HealthSyncStore` guarda apenas `lastSyncedDate`.
- `MainContent` não dispara nenhum sync no resume.

## 4. Estado alvo

```
Barra inferior: Hoje · Corridas · Tendências · Saúde · Perfil
Hoje
  └── card de prontidão ── “Ver recuperação” ──► aba Saúde
Saúde (top-level, sem seta de voltar)
  ├── hero de recuperação (sono, FC repouso, HRV, SpO₂)
  ├── período 7/30/60 dias
  ├── gráficos por métrica
  └── fontes de dados (Health Connect + CSV)
Perfil
  ├── link compacto “Saúde e dados” ──► aba Saúde
  └── backup/privacidade (sem duplicar o card grande)

App abre/resume
  └── HealthSyncCoordinator.syncOnResume()
        ├── última leitura < 6 h? não faz nada
        ├── sem permissão? CTA passivo, sem diálogo
        └── senão lê a janela incremental e atualiza o Room
```

---

# Fase 1 — IA e navegação

## 5. Rotas e barra inferior

### 5.1 Destino novo

Em `app/src/main/kotlin/com/meucoach/app/MeuCoachApp.kt`:

- Adicionar `AppDestination.Health(route = "health", label = "Saúde", icon = Icons.Outlined.MonitorHeart)`.
- Ordem de `destinations`: `Today`, `Activities`, `Trends`, `Health`, `Profile`.
- Trocar `METRICS_IMPORT_ROUTE` por `HEALTH_ROUTE = "health"`.
- Remover `METRICS_IMPORT_ROUTE` de `showBottomBar` (a aba usa a barra).
- `composable(HEALTH_ROUTE) { HealthScreen() }`.
- `onHealthClick` do Hoje, `onHealth` do tutorial e o link do Perfil usam
  `navController.navigateToTab(AppDestination.Health.route)`, preservando o
  comportamento de abas.
- O antigo `profile/metrics-import` deixa de existir. Não há deep link externo
  para ele hoje; se for necessário, criar um composable de redirecionamento com
  `LaunchedEffect` e `popUpTo`.

### 5.2 Tela

- Renomear `MetricsImportScreen`/`MetricsImportContent` para
  `HealthScreen`/`HealthContent` (ou manter o conteúdo e só trocar a rota; a
  renomeação é preferível para o vocabulário do produto).
- `onBack` passa a ser opcional (`(() -> Unit)? = null`): com valor, mostra a
  seta; sem valor, a tela é a aba.
- Header da aba: overline `SAÚDE`, título “Seus dados de saúde”, sem seta.
- Manter o `LazyColumn` e os test tags atuais (`metrics-import-list` pode virar
  `health-list`, mas isso mexe nos testes; decidir na implementação).
- A seção “Fontes de dados” continua no fim da tela. Se o conteúdo crescer,
  extrair “Gerenciar fontes” em uma rota secundária.
- A tela usa `statusBarsPadding` + `innerPadding` do Scaffold; com a barra
  inferior visível, o padding já vem do `Scaffold`.

### 5.3 Hoje

- `ReadinessCard` ganha `onHealthClick: () -> Unit`.
- Adicionar o link **“Ver recuperação”** depois de “Por que esta prontidão?”.
  Preferir uma linha/coluna que não dispute largura com o CTA principal; testar
  em 360 dp e fonte 200%.
- Não duplicar os quatro gráficos no Hoje. O card de prontidão já mostra sinais
  derivados; a ponte leva ao detalhe.
- O `TodayRoute` já recebe `onHealthClick` (usado pelo checklist); passar também
  ao `ReadinessCard`.
- Ação de acessibilidade: `contentDescription = "Abrir Saúde e recuperação"` ou
  texto visível suficiente; alvo de 48 dp.

### 5.4 Perfil

- Remover o bloco grande “Saúde e dados” (título, explicação, pill, botão).
- Manter um card/linha compacta:
  - título “Saúde e dados”;
  - uma linha de estado (`Conectado · N dias`, `Ainda não conectado`,
    `Indisponível neste aparelho`);
  - ação “Abrir”/seta para a aba Saúde.
- Manter o card “Seus dados, sob seu controle” (exportar/apagar) intacto.
- Renomear `onImportMetrics` para `onOpenHealth` (ou manter o nome e documentar)
  para refletir que não é mais uma importação.
- `ProfileViewModel` pode continuar com `ProfileHealthSummary`; se o card
  compacto só precisar de status, simplificar depois. Não é obrigatório nesta
  fase.

### 5.5 Deep links internos

Atualizar todos os call sites:

- `MeuCoachApp` → `TodayRoute.onHealthClick`.
- `MeuCoachApp` → `TutorialScreen.onHealth`.
- `MeuCoachApp` → `ProfileScreen.onOpenHealth`.
- `FirstRunChecklistCard` não muda: só invoca o callback.

O tutorial já marca `seen` antes de chamar `onHealth`; a navegação para a aba
funciona sem overlay.

### 5.6 Arquivos afetados (Fase 1)

| Arquivo | Mudança |
|---|---|
| `app/.../MeuCoachApp.kt` | destino, ordem, rota, `showBottomBar`, call sites |
| `app/.../ui/metrics/MetricsImportScreen.kt` | header sem seta, rename para `HealthScreen`, rota |
| `app/.../ui/metrics/MetricsImportViewModel.kt` | opcional: rename para `HealthViewModel` (sem mudança de lógica) |
| `app/.../ui/profile/ProfileScreen.kt` | card grande → link compacto |
| `app/.../ui/profile/ProfileViewModel.kt` | opcional: simplificar `ProfileHealthSummary` |
| `app/.../ui/today/TodayScreen.kt` | link “Ver recuperação” no card de prontidão |
| `app/.../ui/today/TodayViewModel.kt` | passar `onHealthClick` ao `ReadinessCard` |
| `app/.../ui/tutorial/TutorialScreen.kt` | nenhum, se o callback for ajustado no `MeuCoachApp` |
| `core/designsystem/.../McBottomBar.kt` | nenhum; 5 itens já são suportados |

### 5.7 Testes e goldens (Fase 1)

**Testes de UI/comportamento**

- `TodayScreenTest`/`TodayPlanTest`: o link “Ver recuperação” chama
  `onHealthClick`.
- `ProfileScreenTest`: o Perfil não mostra mais o card grande; o link compacto
  abre a Saúde; o backup continua funcionando.
- `MetricsImportScreenTest` (ou `HealthScreenTest`): header sem seta na aba;
  versão com `onBack` mostra a seta; CSV e estados continuam.
- `NavigationTest`: se possível, um teste do app real ou um teste de destino
  garantindo que a aba Saúde mantém estado e que o link do Hoje chega nela.
- `HealthConnectContentTest`: sem mudança de lógica; só ajustar textos/tags se
  o rename mexer.
- `HealthConnectCopyTest`: sem mudança nesta fase.

**Goldens**

- Regravar `health-dark`, `health-light`, `health-narrow-dark`,
  `health-charts-dark`, `health-recovery-dark` (header sem seta).
- Regravar `today-dark`, `today-light`, `today-narrow-dark`,
  `today-checklist-dark` (link novo).
- Regravar `profile-dark`, `profile-light` (card compacto, sem card grande).
- Conferir 360 dp e fonte 200% nos goldens/testes de UI.

**Acessibilidade**

- 5 abas em 360 dp: “Tendências” é o rótulo mais longo; garantir uma linha.
- TalkBack: a aba tem rótulo “Saúde”; o link do Hoje tem descrição/ação clara.
- Contraste e alvos de 48 dp já vêm do `McBottomBar`/`McTextButton`.

### 5.8 Aceite (Fase 1)

- [ ] A barra inferior tem 5 destinos, com Saúde selecionável.
- [ ] A tela de Saúde abre sem seta de voltar e mantém o dashboard e as fontes.
- [ ] O Perfil não duplica mais o card de Health Connect.
- [ ] O card de prontidão do Hoje tem “Ver recuperação” funcional.
- [ ] Checklist e tutorial chegam à aba Saúde.
- [ ] Nenhum gráfico novo no Hoje.
- [ ] `./gradlew check` e `verifyRoborazziDebug` verdes.
- [ ] Goldens atualizados; sem dados reais.

---

# Fase 2 — Sync automático

## 6. Store

`core/data/src/main/kotlin/com/meucoach/core/data/health/HealthSyncStore.kt`

Adicionar, mantendo `lastSyncedDate()`:

```kotlin
fun lastSyncedAt(): Instant?      // KEY_LAST_SYNC_AT
fun lastAttemptAt(): Instant?     // KEY_LAST_ATTEMPT_AT
fun markSynced(at: Instant, date: LocalDate)
fun markAttempt(at: Instant)
fun reset()                       // clear() de todas as chaves
```

- `lastSyncedDate` continua sendo a marca d’água do incremental
  (`último sucesso − 1 dia`).
- `lastSyncedAt` é para frescor/UI (“Atualizado há 3 h”).
- `lastAttemptAt` é para backoff de falha (15 min).
- Instalações antigas não têm os timestamps: `null` → trata como stale e faz um
  sync normal na primeira abertura.
- `reset()` deve limpar as três chaves (o reset de dados já chama
  `HealthSyncStore.reset()`).

## 7. Serviço

`core/data/src/main/kotlin/com/meucoach/core/data/health/HealthSyncService.kt`

```kotlin
class HealthSyncService(
    private val source: HealthDailySource,
    private val metricsRepository: DailyMetricsRepository,
    private val profileRepository: ProfileRepository,
    private val syncStore: HealthSyncStore,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val mutex = Mutex()

    suspend fun sync(): HealthSyncResult = mutex.withLock { syncLocked() }

    suspend fun syncIfStale(
        maxAge: Duration = STALE_AFTER,
        retryBackoff: Duration = RETRY_BACKOFF,
    ): HealthSyncResult? = mutex.withLock {
        val now = clock.instant()
        val lastSyncedAt = syncStore.lastSyncedAt()
        if (lastSyncedAt != null && Duration.between(lastSyncedAt, now) < maxAge) {
            return@withLock null
        }
        val lastAttemptAt = syncStore.lastAttemptAt()
        if (lastAttemptAt != null && Duration.between(lastAttemptAt, now) < retryBackoff) {
            return@withLock null
        }
        syncLocked(now)
    }

    private suspend fun syncLocked(now: Instant = clock.instant()): HealthSyncResult { ... }

    companion object {
        const val WINDOW_DAYS = 30L
        val STALE_AFTER: Duration = Duration.ofHours(6)
        val RETRY_BACKOFF: Duration = Duration.ofMinutes(15)
    }
}
```

Regras:

- `syncLocked` usa `now.atZone(zone).toLocalDate()` (não `LocalDate.now`) para
  ser testável. Evitar `LocalDate.ofInstant` por compatibilidade com API 26.
- `syncStore.markAttempt(now)` somente quando `result.error != null`.
- Falta de permissão **não** grava backoff.
- Sucesso grava `markSynced(now, today)` e mantém a janela incremental atual
  (`lastSyncedDate - 1`).
- Status indisponível não grava nada; o próximo resume tenta de novo (é barato).
- A mensagem/`HealthSyncResult` existente não muda.

## 8. Coordenador

Novo arquivo `app/src/main/kotlin/com/meucoach/app/health/HealthSyncCoordinator.kt`.

- `@Singleton`, injeta `HealthDailySource`, `HealthSyncService`,
  `HealthSyncStore` e um `CoroutineScope` de aplicação.
- Estado:

```kotlin
data class HealthSyncState(
    val isSyncing: Boolean = false,
    val lastSyncedAt: Instant? = null,
    val lastAttemptAt: Instant? = null,
    val needsPermission: Boolean = false,
    val unavailable: Boolean = false,
    val lastError: String? = null,
)
```

- `syncOnResume()`:
  - `scope.launch`;
  - `Mutex.tryLock` para não empilhar;
  - lê `source.status()`;
  - se indisponível → `unavailable = true`, sem sync;
  - se faltam permissões → `needsPermission = true`, sem diálogo;
  - senão `service.syncIfStale()`;
  - atualiza `lastSyncedAt`/`lastAttemptAt`/`lastError` a partir do store/resultado.
- `suspend fun syncNow(): HealthSyncResult`:
  - chamado pelo `HealthConnectViewModel` (manual);
  - usa `service.sync()` (bypassa frescor);
  - atualiza o estado e devolve o resultado para a mensagem.
- Escopo de aplicação: criar um `@ApplicationScope` qualifier + provider Hilt
  (ou o coordenador cria `CoroutineScope(SupervisorJob() + Dispatchers.IO)`; o
  provider é preferível para teste/desligamento).

### 8.1 ViewModel de aplicação

Novo `app/src/main/kotlin/com/meucoach/app/health/HealthSyncViewModel.kt`:

```kotlin
@HiltViewModel
class HealthSyncViewModel @Inject constructor(
    private val coordinator: HealthSyncCoordinator,
) : ViewModel() {
    val state = coordinator.state
    fun syncOnResume() = coordinator.syncOnResume()
}
```

Em `MainContent`:

```kotlin
val healthSync: HealthSyncViewModel = hiltViewModel()
LifecycleResumeEffect(Unit) {
    healthSync.syncOnResume()
    onPauseOrDispose { }
}
```

`MainContent` só é composto com perfil existente, então não sincroniza durante
o onboarding.

## 9. Worker e agendamento

- `HealthSyncWorker` continua chamando `syncService.sync()`; o `Mutex` protege
  contra concorrência com o resume.
- `WorkManagerHealthSyncScheduler` troca
  `ExistingPeriodicWorkPolicy.UPDATE` por `KEEP` para preservar o ciclo já
  enfileirado. Se o intervalo mudar no futuro, usar um nome único versionado.
- O atraso inicial de 30 min continua; ele é fallback, não o caminho principal.
- Worker continua retornando sucesso em falta de permissão/indisponibilidade e
  `retry()` em erro de leitura.

## 10. UI

### 10.1 Tela de Saúde

- `HealthConnectUiState` ganha `lastSyncedAt: Instant?` (substitui ou acompanha
  `lastSyncDate`).
- “Sincronizando…” discreto quando `state.isSyncing` (automático ou manual).
- “Atualizado há X” via `healthConnectLastSyncLabel(lastSyncedAt, now)`.
- O botão “Atualizar agora” continua; a mensagem de sucesso/erro manual não
  muda.
- Erro automático: texto passivo no card do Health Connect, sem toast/dialog.
- Falta de permissão: CTA “Conectar Health Connect” / “Revise as permissões”.
- Se `lastSyncedAt` é recente mas a última métrica é anterior a hoje, mostrar um
  caption: “Os dados de hoje ainda não chegaram do seu relógio.” (Fase 3).

### 10.2 Hoje

- O Hoje não precisa exibir “Sincronizando…” nesta fase. O `TodayViewModel` já
  observa `metricsRepository.observeAll()`; quando o sync grava, a prontidão se
  atualiza sozinha.
- Opcional: um caption discreto no card de prontidão com a última leitura. Se
  entrar, usar o mesmo vocabulário da Saúde.

### 10.3 Perfil

- O link compacto “Saúde e dados” pode mostrar o estado
  (`Conectado · N dias` / `Ainda não conectado`) e a última leitura relativa.
- Sem duplicar a explicação longa do Health Connect: essa mora na aba Saúde.

## 11. Arquivos afetados (Fase 2)

| Arquivo | Mudança |
|---|---|
| `core/data/.../health/HealthSyncStore.kt` | timestamps + reset completo |
| `core/data/.../health/HealthSyncService.kt` | `Clock`, `Mutex`, `syncIfStale` |
| `app/.../health/HealthSyncCoordinator.kt` | novo |
| `app/.../health/HealthSyncViewModel.kt` | novo |
| `app/.../di/AppModule.kt` (ou `DataModule`) | `Clock` e `@ApplicationScope` |
| `app/.../MeuCoachApp.kt` | `LifecycleResumeEffect` no `MainContent` |
| `app/.../health/HealthSyncScheduler.kt` | `KEEP` |
| `app/.../ui/metrics/HealthConnectViewModel.kt` | coordenação, `lastSyncedAt` |
| `app/.../ui/metrics/MetricsImportScreen.kt` | “Sincronizando…”, label relativo |
| `app/.../ui/common/HealthConnectCopy.kt` | label por `Instant` |
| `app/.../ui/profile/ProfileViewModel.kt` | `lastSyncedAt` no resumo |
| `app/.../ui/profile/ProfileScreen.kt` | estado no link compacto |

## 12. Testes (Fase 2)

**`core:data`**

- `HealthSyncStoreTest` (novo):
  - persiste `lastSyncedAt`/`lastAttemptAt`;
  - `lastSyncedDate` continua funcionando;
  - `reset()` limpa tudo;
  - leitura de prefs antigas (só data) retorna timestamp nulo.
- `HealthSyncServiceTest` (ampliar):
  - `syncIfStale` com `lastSyncedAt` recente → `null` e nenhuma leitura;
  - `syncIfStale` com `lastSyncedAt` antigo → lê e grava;
  - erro grava `lastAttemptAt` e bloqueia por 15 min;
  - falta de permissão não grava backoff;
  - status indisponível não grava nada;
  - `sync()` manual ignora frescor/backoff;
  - duas chamadas concorrentes não duplicam leitura (usar `async`/`Mutex`).

**`:app`**

- `HealthSyncCoordinatorTest` (novo):
  - resume com permissão e stale → chama o serviço;
  - resume fresco → não chama;
  - sem permissão → estado `needsPermission`, sem diálogo;
  - indisponível → estado `unavailable`;
  - erro → `lastError` passivo;
  - `syncNow()` devolve resultado e atualiza estado.
- `HealthConnectViewModelTest` (ajustar):
  - sync manual continua com as mensagens atuais;
  - estado reflete `lastSyncedAt`/`isSyncing`.
- `HealthSyncSchedulerTest` (ajustar):
  - continua único e enfileirado; se possível, verificar que duas chamadas
    preservam o mesmo `WorkInfo.id`.
- `HealthConnectCopyTest` (ampliar):
  - “Atualizado agora”, “há 12 min”, “há 3 h”, “ontem”, data antiga.
- `MainContent`/resume: teste de ViewModel/coordenador é suficiente; um teste
  de UI do app real pode ser caro.

**Critérios de aceite (Fase 2)**

- [ ] Abrir o app com permissão e última leitura > 6 h dispara um sync
      incremental, sem tocar em botão.
- [ ] Abrir o app com última leitura < 6 h não dispara leitura.
- [ ] Voltar do background após 6 h dispara de novo.
- [ ] Sem permissão, o app não abre diálogo e mostra CTA.
- [ ] Erro de leitura não vira loop: backoff de 15 min.
- [ ] Worker diário continua funcionando e não duplica leitura.
- [ ] “Atualizado há X” reflete o timestamp real.
- [ ] Nenhuma migração de Room; nenhuma dependência nova.
- [ ] `./gradlew check`, `koverVerify`, `spotlessCheck` e
      `verifyRoborazziDebug` verdes.

---

# Fase 3 — Polimento (opcional)

1. **Dados de hoje ainda não chegaram:** se a última leitura é recente mas a
   última métrica é anterior a hoje, mostrar um caption no hero de recuperação.
   Não é erro: é latência do app de origem.
2. **Abrir Health Connect:** link de troubleshooting quando o provedor está
   disponível mas não há dados.
3. **Resumo no Hoje:** avaliar uma linha compacta (sono/FC/HRV) no card de
   prontidão, sem gráficos. Só entra se não competir com “Iniciar” e se o teste
   com 5 usuários mostrar que ajuda.
4. **Proveniência por campo:** se o usuário precisar saber qual fonte preencheu
   cada métrica, avaliar em ADR futuro (hoje `source` é a dominante do dia).

---

## 13. Riscos e mitigações

| Risco | Mitigação |
|---|---|
| 5 abas apertadas em 360 dp / fonte 200% | Testar labels; “Saúde” é curto; plano B é “Evolução” com segmentos |
| Remover a rota antiga quebrar deep link | Não há deep link externo hoje; se necessário, redirect para `health` |
| WorkManager atrasar | Resume é o caminho principal; worker é fallback |
| Health Connect sem dados novos | UI diz “os dados de hoje ainda não chegaram”; não finge sucesso |
| Sync concorrente (worker + resume + botão) | `Mutex` no serviço + rechecagem de frescor |
| Instalações antigas sem `lastSyncedAt` | Tratar como stale; primeiro sync normal |
| `KEEP` impedir mudança futura de intervalo | Nome único versionado ou cancelamento explícito |
| Card do Perfil ficar sem descoberta | Link compacto com estado + ação “Abrir” |

## 14. Validação no aparelho (S24 Ultra)

1. **Navegação:** 5 abas, estado preservado, link do Hoje, link do Perfil,
   checklist e tutorial.
2. **Fonte 200% e 360 dp:** nenhum label cortado; “Ver recuperação” não quebra.
3. **Permissão já concedida:** abrir o app após 6 h e conferir que o sono do dia
   aparece sem tocar no botão.
4. **Permissão revogada:** abrir o app e conferir CTA passivo, sem diálogo.
5. **Falha de leitura:** simular erro/offline e conferir backoff e mensagem
   passiva.
6. **Worker:** enfileirar o worker no WorkManager e conferir que ele não duplica
   a leitura do resume.
7. **Privacidade:** nenhum dado sai do aparelho; export/reset continua limpando
   a marca d’água.

## 15. Documentação a atualizar na implementação

- `docs/PROGRESS.md`: sessão com testes, goldens e evidências.
- `docs/ROADMAP.md`: P1-14/P1-15 marcados como concluídos.
- `docs/DESIGN_SYSTEM.md` §11: blueprint do Hoje com o link e blueprint da aba
  Saúde; §8.2 já cobre os gráficos.
- `docs/PRODUCT.md` §4: Saúde como destino principal do MVP.
- `docs/ARCHITECTURE.md` §8: sync no resume + worker fallback.
- `docs/adr/0014-incremental-health-sync.md`: addendum apontando para o
  ADR-0021 (não reescrever a decisão original).
- `docs/DECISIONS.md`: índice com 0018–0021 (0017 não existe no repositório).
