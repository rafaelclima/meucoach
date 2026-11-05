# ADR 0021 — Sync automático do Health Connect ao abrir o app

**Status:** aceito e implementado em 05/11/2026
**Data:** 2026-11-05
**Responsável:** planejamento com Rafael (usuário 0)

---

## 1. Contexto

O Health Connect já tem:

- leitura read-only de sono, FC de repouso, HRV e SpO₂ (`HealthDailySource`);
- sync incremental: a primeira execução lê 30 dias; as seguintes partem do
  último sucesso menos 1 dia (`HealthSyncService`, `ADR-0014`);
- worker diário via WorkManager (`HealthSyncWorker`);
- botão manual “Atualizar agora” na tela de Saúde.

O buraco é de **frequência de atualização**, não de capacidade:

- o app não sincroniza quando é aberto ou volta do background;
- o WorkManager é best-effort e não garante execução no momento em que o
  usuário abre o app;
- sem tocar no botão, o Hoje pode usar dados de ontem mesmo com o relógio já
  tendo sincronizado com o Health Connect;
- o usuário precisa lembrar de uma ação que o app pode fazer sozinho.

O Health Connect é local e a leitura é barata: quatro tipos de registro em uma
janela de 1–2 dias no uso incremental. O custo real é a consulta ao provedor,
não rede.

## 2. Decisão

1. **Sync no resume do app.** Um `HealthSyncCoordinator` singleton expõe
   `syncOnResume()`; o `MainContent` o chama em `LifecycleResumeEffect`, uma vez
   por retorno ao app.
2. **Janela de frescor de 6 horas.** Se a última leitura bem-sucedida tem menos
   de 6 h, não lê de novo. Se passou, lê a janela incremental normal.
3. **Só com permissão e provedor disponível.** O resume não abre diálogo de
   permissão. Sem autorização, a UI mostra CTA passivo (“Conectar Health
   Connect” / “Revise as permissões”). Com o provedor indisponível, mostra o
   aviso existente.
4. **Timestamp real.** `HealthSyncStore` passa a guardar, além de
   `lastSyncedDate` (marca d’água do incremental), `lastSyncedAt` e
   `lastAttemptAt` em epoch millis.
5. **Backoff de falha.** Uma leitura com erro grava `lastAttemptAt` e evita nova
   tentativa automática por 15 min. Falta de permissão não grava backoff (o
   usuário pode conceder em seguida).
6. **Manual bypassa o frescor.** O botão “Atualizar agora” continua chamando
   `sync()` diretamente; é o override explícito do usuário.
7. **Worker diário como fallback.** O `HealthSyncWorker` continua existindo para
   quando o app não é aberto. A política do agendamento deve preservar a
   execução já enfileirada (`ExistingPeriodicWorkPolicy.KEEP`) em vez de
   recriar o ciclo a cada abertura.
8. **Concorrência protegida.** `HealthSyncService` ganha um `Mutex`: worker,
   resume e botão manual não leem/gravam ao mesmo tempo. `syncIfStale` recheca o
   frescor dentro do lock para não repetir uma leitura que o worker acabou de
   fazer.
9. **UI silenciosa.** Sucesso automático não mostra toast. A tela de Saúde mostra
   “Sincronizando…” discreto e “Atualizado há X”. Erro automático fica como
   aviso passivo, sem interromper o uso.
10. **Sem Room e sem dependência nova.** Tudo em `SharedPreferences`, no padrão
    do `HealthSyncStore` atual.

### 2.1 Contrato do serviço

```kotlin
// core:data — HealthSyncService
suspend fun sync(): HealthSyncResult
suspend fun syncIfStale(
    maxAge: Duration = STALE_AFTER,      // 6 h
    retryBackoff: Duration = RETRY_BACKOFF, // 15 min
): HealthSyncResult?                      // null = pulado por frescor/backoff
```

- `sync()` é o caminho manual e do worker.
- `syncIfStale()` é o caminho automático.
- `HealthSyncResult` existente continua igual; o `null` comunica “nada a fazer”.
- `HealthSyncService` recebe `Clock` com default, para testes determinísticos.

### 2.2 Contrato do coordenador

```kotlin
// :app — health/HealthSyncCoordinator
data class HealthSyncState(
    val isSyncing: Boolean = false,
    val lastSyncedAt: Instant? = null,
    val lastAttemptAt: Instant? = null,
    val needsPermission: Boolean = false,
    val unavailable: Boolean = false,
    val lastError: String? = null,
)

fun syncOnResume()          // dispara em scope de aplicação; não bloqueia a UI
suspend fun syncNow(): HealthSyncResult // manual
```

- `syncOnResume()` usa `Mutex.tryLock` para não empilhar chamadas.
- `syncNow()` é chamado pelo `HealthConnectViewModel` e devolve o resultado para
  a mensagem existente (“N dias importados”, “Autorize…”, etc.).
- O estado é um `StateFlow` compartilhado entre a ponte do Hoje (se necessário)
  e a tela de Saúde.

## 3. Alternativas rejeitadas

1. **Sincronizar em todo resume, sem frescor.**
   Atualiza mais, mas repete leituras em sequência (abrir a aba, voltar, abrir de
   novo) sem ganho proporcional.
2. **Só o worker diário (status quo).**
   É exatamente o problema: o WorkManager não garante execução na abertura e o
   usuário precisa apertar o botão.
3. **Sincronizar no `Application.onCreate`.**
   Não tem o ciclo de resume, pode rodar antes de o perfil existir e não cobre
   voltas do background. O coordenador no `MainContent` é o ponto certo.
4. **`AlarmManager` exato.**
   Overkill para uma leitura local e barata; adiciona permissão/complexidade e
   não melhora a experiência no momento em que o usuário abre o app.
5. **DataStore.**
   Adicionaria dependência para três campos; `SharedPreferences` já resolve.
6. **Guardar a data em Room.**
   Exigiria schema novo e migração para metadados de sync; não muda a prontidão.

## 4. Consequências

**Positivas**

- Os dados ficam atualizados sem ação do usuário na maioria dos dias.
- O Hoje reflete o que o relógio escreveu no Health Connect, com atraso de no
  máximo uma abertura.
- O botão manual vira override, não obrigação.
- Nenhuma migração de Room; nenhuma dependência nova.
- O worker diário continua cobrindo quem não abre o app.

**Riscos e limites**

- O app só lê o que já está no Health Connect. Se o app do relógio ainda não
  escreveu, não há dado novo; a UI deve dizer “os dados de hoje ainda não
  chegaram” em vez de fingir sucesso.
- O WorkManager pode atrasar por bateria/OEM; o resume cobre o uso ativo.
- `lastSyncedAt` não existe em instalações antigas. A primeira abertura após a
  atualização trata como stale e faz um sync incremental normal.
- O resume e o worker podem disputar a mesma janela; o `Mutex` e a rechecagem de
  frescor evitam leitura duplicada.
- `ExistingPeriodicWorkPolicy.KEEP` preserva o ciclo, mas mudanças futuras de
  intervalo exigem um nome único versionado ou uma migração de agendamento.

## 5. Implementação

O plano detalhado (store, serviço, coordenador, UI, testes e aceite) está em
[`docs/HEALTH_TOP_LEVEL_PLAN.md`](../HEALTH_TOP_LEVEL_PLAN.md), Fase 2.

## 6. Referências

- `docs/adr/0005-health-connect.md`
- `docs/adr/0014-incremental-health-sync.md`
- `docs/adr/0016-daily-metrics-merge.md`
- `docs/ARCHITECTURE.md` §8
- `docs/PRODUCT.md` §5.2 e §5.4
