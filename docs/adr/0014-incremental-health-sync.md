# ADR 0014 — Sync incremental do Health Connect via WorkManager

**Status:** aceito em 31/10/2026

## Contexto

A leitura do Health Connect existia apenas por ação manual no perfil e sempre
cobria 30 dias. Isso reabria o mesmo intervalo a cada toque, aumentava a chance
de sobrescrever um dia que também tinha CSV e não atualizava os dados quando o
usuário não abria a tela.

O app já usa WorkManager para lembretes e importação, então o sync periódico
pode reutilizar a mesma infraestrutura e o `HiltWorkerFactory`.

## Decisão

- `HealthSyncService` centraliza o sync para a tela de perfil e para o worker.
- O primeiro sync lê **30 dias**; os seguintes leem de `último sucesso − 1 dia`
  até hoje, capturando registros que chegaram atrasados.
- O último sync bem-sucedido fica em `SharedPreferences`
  (`meucoach_health_sync`). É apenas uma data, não um dado de saúde.
- Falta de permissão, erro de leitura ou Health Connect indisponível **não**
  avançam a data; o próximo sync repete a janela.
- `HealthSyncWorker` roda a cada 24 h via `PeriodicWorkRequest`, com atraso
  inicial de 30 min e backoff exponencial de 1 h para erro de leitura.
- Permissões ausentes e indisponibilidade retornam sucesso para não gastar
  bateria; o usuário resolve a permissão na tela de perfil.
- Nenhuma tabela nova no Room e nenhuma dependência nova.

## Alternativas rejeitadas

1. **Nova tabela `sync_state` no Room:** exigiria schema v10 e migração para
   armazenar uma única data.
2. **DataStore:** adicionaria uma dependência para um único campo; o
   `SharedPreferences` já resolve.
3. **WorkManager sem serviço compartilhado:** duplicaria a regra de janela entre
   a tela e o worker.
4. **Sempre ler 30 dias:** mais leitura e mais sobrescrita de dias existentes.
5. **Retry quando falta permissão:** repetiria sem chance de sucesso até o
   usuário agir; o ciclo diário já tenta de novo.

## Consequências

- Menos leitura e menos sobrescrita no uso diário.
- O usuário recebe atualização mesmo sem abrir o app.
- CSV e Health Connect são mesclados por campo desde o `ADR-0016`.
- Apagar todos os dados zera a marca d'água (`T-06`), então um perfil novo relê
  a janela inicial de 30 dias.
- O worker é best-effort: falha de WorkManager não bloqueia o app.
