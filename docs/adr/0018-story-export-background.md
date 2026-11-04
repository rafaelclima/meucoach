# ADR 0018 — Geração do story em segundo plano e cancelamento real

**Status:** aceito e implementado
**Data:** 2026-11-04
**Responsável:** implementação com Rafael (usuário 0)

---

## 1. Contexto

O story do treino (`ADR-0012`, `ADR-0015`) é gerado no `ActivityDetailViewModel`:

- `exportStory()` chamava o `StoryVideoExporter` dentro do `viewModelScope`;
- `cancelStory()` apenas voltava o estado para `Idle` — o loop de `MediaCodec`
  continuava até o fim, porque nunca suspende e ignorava o `Job` cancelado;
- ao sair da tela o `viewModelScope` era cancelado, mas o encoder (síncrono)
  seguia rodando no processo; ao matar o app, o vídeo era perdido sem aviso;
- o progresso chegava à UI a cada 12 frames, sem estimativa de tempo restante.

O usuário 0 pediu três coisas: **cancelar de verdade**, **poder distrair**
(geração em segundo plano com notificação) e **progresso mais granular com
tempo restante**. Nada disso muda o motor determinístico nem a privacidade.

---

## 2. Decisão

### 2.1 WorkManager para a exportação

A geração passa a ser um `OneTimeWorkRequest` (`StoryExportWorker`,
`@HiltWorker`) com nome único por atividade (`story-export-<activityId>`).

- **Processo morto não perde o trabalho:** o worker é replanejado pelo
  WorkManager.
- **Entrada pequena e serializável:** `activityId`, caminho do mapa em cache e
  a paleta empacotada em `IntArray` (`StoryPaletteCodec`) — `Data` não carrega
  tipos Android.
- **Nada de estado da UI no worker:** `StoryPlanLoader` reconstrói o
  `StoryPlan` a partir dos repositórios (atividade, LTHR, sessão planejada da
  semana) com o mesmo `StoryPlanCalculator`, mantendo o veredito idêntico ao
  da prévia.
- **Mapa sem re-download:** a prévia já renderizou o snapshot; ele é gravado
  como PNG em `cacheDir/stories/story-map-<id>.png` antes do enqueue e o
  worker só decodifica. Se o cache for limpo, o worker renderiza de novo
  (`MapBackgroundRenderer`); falha de rede nesse caminho vira `Result.retry()`
  (2 tentativas, backoff exponencial de 10 s).
- **Resultado:** URI do vídeo, flag de galeria e URI da capa voltam em
  `outputData`; o card lê isso do `WorkInfo`.
- **Notificação:** canal próprio `meucoach-story` (importância baixa),
  sucesso e falha, com deep link para o detalhe da atividade. Sem
  `POST_NOTIFICATIONS` a notificação é omitida, nunca o vídeo.

### 2.2 Cancelamento cooperativo no encoder

`MediaCodec`/`Canvas` não respondem a cancelamento de corrotina: o loop é
síncrono. `CanvasStoryVideoExporter` recebe `isCancelled: () -> Boolean`
(derivado do `Job` capturado com `currentCoroutineContext()`) e verifica antes
de cada frame e antes do `END_OF_STREAM`.

- Cancelar → `CancellationException` → `finally` libera codec e muxer; o MP4
  parcial é apagado no `catch` do `export()`.
- `cancelStory()` chama `cancelUniqueWork` e volta o card para `Idle`; o worker
  trata `CancellationException` como cancelamento (não como falha), para o
  WorkInfo terminar em `CANCELLED`.
- Trocar de geração usa `ExistingWorkPolicy.REPLACE`, então um export antigo
  nunca compete com o novo.

### 2.3 Progresso granular e ETA

- O exporter reporta progresso a cada 12 frames (custo desprezível).
- O worker persiste em `setProgress` no máximo a cada 600 ms (WorkManager grava
  em disco; 30 fps inviabiliza) e inclui `etaSec` calculado por
  `StoryEtaEstimator` — média exponencial da taxa de conclusão, com clamp para
  impedir saltos irreais. Sem amostra suficiente, `etaSec` fica nulo e a UI
  omite o texto.
- O ViewModel mapeia `WorkInfo` → `StoryExportUiState`:
  `ENQUEUED`/`RUNNING` → `Generating(progress, etaSec)`, `SUCCEEDED` → `Done`,
  `FAILED` → `Error` (mensagem no `outputData`), `CANCELLED` → `Idle`.
- **Recuperação:** como o `observe` é por nome único, reabrir a tela com um
  export em andamento volta a mostrar `Generating` e um export concluído
  mostra `Done` mesmo após morte do processo.
- `applyWorkInfo` é `internal` para o teste exercitar a máquina de estados sem
  depender do agendador do WorkManager.

### 2.4 Testes

- `StoryEtaEstimatorTest` (JVM puro): primeira amostra nula, taxa suavizada,
  stalled, burst clampado, teto, amostra final = 0.
- `StoryPaletteCodecTest`: round-trip dos 17 campos e payload inválido.
- `StoryExportProcessorTest` (Robolectric): GPS ausente → falha; mapa staged
  decodificado sem render; cache ausente → render de fallback; erro de mapa →
  retry; ETA encaminhado pelo loop do codec.
- `StoryExportCoordinatorTest` (WorkManager de teste): nome único, input puro
  com paleta/caminho e staging do PNG + tag do worker.
- `ActivityDetailViewModelTest`: envio da prévia/paleta ao coordinator,
  cancelamento real, adoção de `RUNNING`/`SUCCEEDED`/`FAILED`, `CANCELLED` e
  saída obsoleta ignorada.
- `ActivityDetailScreenTest`: estimativa, aviso de segundo plano e botão
  cancelar.
- O encoder real continua validado só em aparelho (`MediaCodec` não roda em
  Robolectric/JVM), como já ocorria.

---

## 3. Alternativas rejeitadas

- **Coroutine em escopo de `Application`:** simples, mas morre junto com o
  processo e não dá retry/notificação automática; não atende "pode distrair".
- **Foreground service manual:** mais código de plataforma, notificação
  persistente obrigatória e permissões extras; WorkManager com `setProgress`
  cobre o caso sem serviço na maior parte do tempo.
- **Serializar o `StoryPlan`/bitmap no input do worker:** `Data` tem limite de
  ~10 KB e não aceita `Bitmap`; reconstruir o plano é determinístico e barato,
  e o mapa viaja como arquivo em cache.
- **Manter o exporter no ViewModel e só esconder a tela:** é exatamente o
  problema atual (cancelamento falso e morte do processo).

---

## 4. Consequências

- **Positivas:** cancelamento imediato; exportação sobrevive a sair da tela,
  trocar de app e morte do processo (com retry); progresso com ETA; notificação
  com deep link; estado restaurado ao reabrir a tela.
- **Custos:** um PNG extra em `cacheDir` (~2106×3744) por prévia; até 2
  downloads de mapa no caminho de retry; UI atualiza a ~1,6 Hz.
- **Limites:** sem áudio (Fase C); uma exportação por atividade por vez
  (REPLACE); notificação depende de `POST_NOTIFICATIONS`; o formato do vídeo
  (1080×1920/30 fps/10 Mbps) não mudou; o worker é comum (sem foreground
  service), e o Android interrompe trabalhos muito longos (~10 min) — um story
  de 30 s fica bem abaixo, mas vale medir em aparelho lento.
- Nenhuma dependência nova: `androidx.work` e `androidx.hilt:hilt-work` já
  faziam parte do projeto (importação e lembretes).
