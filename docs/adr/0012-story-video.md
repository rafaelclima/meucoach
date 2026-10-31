# ADR 0012 — Story de treino ao ar livre (vídeo com animação do percurso)

**Status:** aceito para planejamento — **implementação condicionada a resposta do usuário 0 e build verde**.
**Data:** 2026-10-31
**Responsável:** planejamento com Rafael (usuário 0)

## Contexto

O usuário 0 (Rafael) quer uma feature em que cada treino ao ar livre com GPS possa ser transformado em um vídeo curto no formato de stories do Instagram (vertical, ~30s). O vídeo deve mostrar o percurso animado no mapa, com pausa a cada km para exibir pace, FC média e calorias do km, por ~2 segundos, e depois seguir. A feature deve ser chamativa o suficiente para que o usuário tenha vontade real de postar.

As respostas do planejamento (questionário realizado em 31/10/2026) foram:

- **Fonte de dados:** importação direta de arquivo GPS (GPX/TCX/FIT) no momento; no futuro, treino iniciado diretamente pelo app com localização salva.
- **Acionamento:** manual, no detalhe da atividade (`P0-09`).
- **Design:** card de pausa com pace + FC + kcal; cores do design system (lima/coral); fonte grande; overlay elegante.
- **Formato:** salvar na galeria + permitir postar diretamente nas redes sociais desejadas (via share intent).
- **Duração:** padrão 30 segundos (compatível com stories do Instagram).
- **Integridade do motor:** apenas visual/exportação; não altera carga, prontidão, prescrição ou qualquer cálculo determinístico; LLM não decide percurso.

## Decisão

1. **Fonte de dados GPS:** a feature opera sobre arquivos importados diretamente (`GPX > TCX > FIT`) no momento da criação. Quando o app tiver um modo de treino ao vivo que armazene localização (futuro), o vídeo poderá ser gerado a partir desses dados no banco (`core:data`). Treinos sem posição (esteira) não geram vídeo — a UI mostra uma mensagem explicativa e não bloqueia.

2. **Acionamento:** botão manual no detalhe da atividade (`P0-09`), apenas quando a atividade possui dados de posição. Não é automático após importação, para respeitar a privacidade e evitar surpresas.

3. **Design do vídeo:** card de pausa a cada km com `pace`, `FC média` e `kcal`. Cores do design system (`McCard` escuro, `accentText` lima para destaque, `coral` para fisiologia). Animação do ponto percorrendo a rota com transição suave. Sem texto inventado — todos os números vêm do `StoredActivity` canônico.

4. **Renderização:** solução **Canvas + MediaCodec** (nativo do Android SDK, sem dependência externa grande). Isso atende ao requisito de qualidade (vídeo com animação fluida, overlay de texto, exportação direta) sem violar a regra de arquitetura 8 (dependências justificadas). Se a qualidade do Canvas não for suficiente para "postar com vontade", um ADR futuro avaliará uma biblioteca leve (`FFmpeg` ou `Media3`) com justificativa de peso e licença.

5. **Rota:** traçado real sanitizado (sem nomes, serials ou pontos exatos de residência), mas com a forma do percurso. A animação é totalmente offline — sem chamadas de rede para tiles ou APIs de mapa. Se o usuário quiser uma versão estilizada/abstrata no futuro, isso será uma opção no design, não uma mudança de arquitetura.

6. **Formato e destino:** vídeo MP4 (H.264, 9:16, ~30s, 1080×1920 ou menor conforme capacidade do dispositivo), exportado para `MediaStore` (galeria) e disponível via `ACTION_SEND` para qualquer app (Instagram, WhatsApp, etc.). Nenhum upload obrigatório para a nuvem.

7. **Privacidade:** posições nunca são enviadas para servidores; o vídeo é gerado 100% no dispositivo. A rota no vídeo usa apenas as coordenadas sanitizadas já existentes no parser (`P0-05`/`P0-06`). Nenhum dado real de localização é exposto no output final além do traçado estilizado.

## Alternativas rejeitadas

1. **Canvas puro sem MediaCodec:** não produz arquivo de vídeo diretamente; exigiria uma solução complexa de captura de frames e montagem manual, que na prática seria mais frágil que usar `MediaCodec` nativo.
2. **Biblioteca pesada de vídeo (FFmpeg, etc.) no MVP:** adicionaria peso significativo ao APK (~10–20 MB) antes de provar que a feature é usada. A regra de arquitetura 8 exige justificativa; como ainda não temos evidência de uso, rejeitamos para o MVP.
3. **LLM para decidir o percurso ou criar texto do overlay:** viola `R-ENGINE-01` (motor determinístico). O texto do overlay é fixo por template (ex.: "Pace médio", "FC média", "Calorias"), nunca gerado por modelo.
4. **Aba separada "Stories" no shell:** aumenta a complexidade de navegação antes de provar adesão. A opção manual no detalhe (`P0-09`) é mais simples e respeita a regra "uma decisão por tela".

## Consequências

- Nenhuma dependência nova no `gradle/libs.versions.toml` para o MVP (MediaCodec é nativo).
- A feature fica restrita a treinos com posição; treinos de esteira recebem uma mensagem explicativa, não um erro.
- O design premium (cores, animação, overlay) deve ser validado com screenshots dark/light (`Roborazzi`) antes de ser considerado aceito.
- Se a qualidade visual do Canvas + MediaCodec não atender ao objetivo de "postar com vontade", abriremos um ADR futuro (`0013`?) para avaliar uma biblioteca externa com análise de peso/licença.
- Nenhum dado real de GPS é exposto no repositório (`gitignore` já protege `fixtures/private/`); o vídeo exportado pelo usuário é responsabilidade dele, não do app.

## Nota de implementação — protótipo em 31/10/2026

- `StoryPlanCalculator` (app) projeta a rota, deriva splits por km e monta uma timeline de 30 s com pausas de 2 s por card.
- `StoryFrameRenderer` desenha 720×1280 com rota, progresso, distância/tempo e card de pace/FC/kcal.
- `StoryVideoExporter` usa `MediaCodec` com `COLOR_FormatYUV420Flexible` + `MediaMuxer`, sem EGL e sem dependência nova.
- Android 10+ salva em `Movies/MeuCoach` via MediaStore; versões anteriores compartilham o arquivo de cache via FileProvider.
- A ação fica no detalhe da atividade, apenas quando há amostras com latitude/longitude.
- Pendente: validar qualidade/desempenho em aparelho real, preview antes de exportar e, se necessário, 1080×1920.
