# ROADMAP.md — MeuCoach

**Versão:** 1.1 · **Data:** 30/10/2026

> Sem prazo. O critério é qualidade e confiança, não velocidade. Cada fase termina com build verde, testes e documentação atualizada.

## Visão de fases

| Fase | Objetivo | Status |
|---|---|---|
| **0 — Fundação** | Repositório, build, CI, design system, modelo, domínio inicial, import e detalhe de atividade | **Concluída em 30/10/2026** |
| **1 — Coach** | Perfil, calibração, carga, prontidão, prescrição, Hoje, Tendências, Health Connect | **Em andamento** |
| **2 — Nuvem e extensões** | Backend Ktor/Postgres, sync, Dokploy, widgets, mapas, release | Planejada |
| **3 — Experimental** | Social, conector Zepp, explicações com LLM | Planejada |

---

## Fase 0 — Fundação

### P0-01 · Documentação e decisões

**Status:** concluído em 27/10/2026

- [x] Produto, arquitetura, dados, ciência, design, testes, roadmap, runbook.
- [x] ADRs iniciais.
- [x] Tokens DTCG.
- [x] Avaliação inicial do usuário 0 com dados reais.

**Aceite:** documentação revisável, decisões rastreáveis, nenhum dado real no repo.

### P0-02 · Bootstrap do repositório

**Status:** concluído em 28/10/2026

- [x] Stack validada em projeto limpo (AGP 9.4.1, Gradle 9.8.2, Kotlin 2.4.21, Room, Hilt, KSP).
- [x] Version catalog, wrapper, `.gitignore`, `.editorconfig`.
- [x] Módulos `:app`, `:core:model`, `:core:domain`, `:core:designsystem`.
- [x] `spotless`, lint Android, Kover.
- [x] CI GitHub Actions.
- [x] Makefile.

**Aceite:** `./gradlew check assembleDebug` verde em clone limpo; CI configurado.

### P0-03 · Design system base

**Status:** concluído em 28/10/2026

- [x] Tokens em Kotlin (dark/light), tipografia Geist/Inter, shapes.
- [x] Componentes: `McCard`, `McPrimaryButton`, `McSecondaryButton`, `McStat`, `McConfidenceBadge`, `McGauge`, `McSectionHeader`, `McPill`, `McEmptyState`.
- [x] Previews dark/light da tela Hoje.
- [x] Tela Hoje de demonstração com dados estáticos.
- [x] Navegação inferior e shell do app.
- [x] Instalação e verificação visual em emulador API 35.

**Aceite:** app instala e mostra Hoje premium em dark/light; componentes sem hex solto.

### P0-04 · Modelo e domínio inicial

**Status:** concluído em 28/10/2026

- [x] `Pace`, `Distance`, `ActivitySummary`, `AthleteProfile`, enums.
- [x] Zonas de FC por LTHR + testes de limite.
- [x] Zonas de pace por limiar + testes.
- [x] TRIMP, rTSS, sRPE, CTL/ATL/TSB, ACWR + testes.
- [x] Prontidão v1 com pesos renormalizados, confiança e flags + testes.
- [x] Cobertura `core:domain` ≥ 85% de linhas (atual: 99,88%).

**Aceite:** cálculos conferem com casos à mão; nenhuma exceção inesperada; Kover verde.

### P0-05 · Parser FIT

**Status:** concluído em 28/10/2026

- [x] Decodificador do subconjunto FIT (file_id, record, lap, session, event, device_info, developer fields).
- [x] CRC, endianness, compressed timestamps.
- [x] Normalização de cadência (strides/min → passos/min).
- [x] Testes com as 4 fixtures FIT sanitizadas dos arquivos reais.
- [x] Comparação local com `fitdecode`.
- [x] Testes de erro (truncado, CRC, definição inválida).
- [x] Gate de cobertura de branches ≥ 90% para o pacote do parser.

**Aceite:** resumo e samples dos 4 FIT batem com a referência; parser não quebra com lixo. Os 3 TCX antigos ficam para o P0-06.

### P0-06 · Parser TCX e GPX

**Status:** concluído em 29/10/2026

- [x] TCX com laps, trackpoints, FC, cadência, posição (usar os 3 TCX antigos em `~/Documents/Rafa/`).
- [x] GPX com extensões comuns e fallback sem elevação.
- [x] Testes de XML malformado, namespaces, encoding.
- [x] Dedup entre formatos.

**Aceite:** FIT > TCX > GPX; mesma atividade não duplica; os 4 FIT e os 3 TCX reais abrem sem erro. Os 3 TCX foram sanitizados para `fixtures/tcx/`; GPX real não entrou no repo, e as extensões são cobertas por fixtures sintéticas.

### P0-07 · Persistência (Room)

**Status:** concluído em 30/10/2026

- [x] `core:data` com entidades `Activity`, `Lap`, `SampleBlob`, `ImportJob`.
- [x] DAOs + repositório.
- [x] Migração 1→2 de teste.
- [x] Blob comprimido com round-trip.
- [x] Backup desativado; export/delete básico.

**Aceite:** importar, listar, abrir e apagar atividade sem perder consistência. Entregue com Room v2, migração testada, `SampleBlobCodec` (`gzip-columnar-v1`), `ActivityRepository`, export JSON básico e 26 testes em `core:data`.

**Exclusão por atividade (UI):** concluída em 03/11/2026 — menu `Mais opções` no detalhe, confirmação e soft delete; lista, Hoje e Tendências reagem via Flow.

### P0-08 · Importação — CONCLUÍDO

- [x] File picker (SAF) + share intent.
- [x] Fila persistente (`WorkManager` 2.12.0, `HiltWorkerFactory`).
- [x] Progresso, erro, dedup, substituição `FIT > TCX > GPX`, alternates, restauração.
- [x] Tela `ActivitiesScreen`, `sourcesLabel` (`FIT · TCX · GPX`), histórico de import.
- [x] Testes de `ViewModel`, `Staging`, `Coordinator`, `ShareIntentReader`.

**Aceite verificado:** `./gradlew check :app:assembleDebug spotlessCheck` — 214 testes, 0 falhas; `core:domain` 98,91% / 86,12% branches; `core:data` 44 testes; share + picker funcionando.

**Aceite:** arrastar/selecionar 3 formatos da mesma corrida → 1 atividade, mensagem clara.

### P0-09 · Detalhe da atividade — CONCLUÍDO

- [x] Hero (distância, tempo, pace).
- [x] Gráfico pace × FC × tempo.
- [x] Splits, cadência, potência, elevação.
- [x] Rota (polyline estática offline).
- [x] "O que aprendemos" com `R-ACT-01`/`R-ACT-02`, evidência e confiança.
- [x] Zonas de FC por LTHR (dependem da calibração do P1-02).

**Aceite verificado:** `RealFixturesDetailTest` importa e abre os 7 treinos reais sanitizados sem erro; TCX confere distância, duração e cadência com o README; suíte verde.

### P0-10 · Harness de testes

**Status:** concluído em 30/10/2026

- [x] Fixtures sanitizadas + scripts (`tools/sanitize_fit.py`, 4 FIT; `tools/sanitize_tcx.py`, 3 TCX) e GPX sintético.
- [x] Golden scenarios iniciais (`G-LOAD-01`, `G-READY-01`…`04`).
- [x] Robolectric + Compose UI e Roborazzi (dark/light do detalhe).
- [x] CI com relatórios e artefatos.
- [x] `docs/PROGRESS.md` atualizado.

**Aceite:** suíte roda em < 2 min local; CI bloqueia regressão.

---

## Fase 1 — Coach

### P1-01 · Onboarding e perfil

**Status:** concluído em 30/10/2026

- [x] Formulário em 3 passos: dados pessoais, objetivo/disponibilidade e rotina.
- [x] Persistência local em Room v4 e edição preservando calibração.
- [x] Gate de primeiro uso e tela de resumo do perfil.
- [x] Validação por passo, mensagens em pt-BR e testes de ViewModel/UI.

**Aceite:** perfil completo é salvo e reaberto; ausência de dados não bloqueia o app depois do onboarding.

### P1-02 · Calibração

**Status:** concluído em 30/10/2026

- [x] Tier A/B/C por performance recente e treinos com FC+pace.
- [x] VDOT, pace de limiar e LTHR com fórmulas documentadas.
- [x] Tela de entrada/resultado, confiança e avisos.
- [x] Salvamento no perfil e resumo de calibração.
- [x] Zonas de FC no detalhe da atividade após calibrar.

**Aceite:** performance recente gera VDOT/LTHR/pace; ausência de dados não inventa valores; perfil mantém a calibração.

### P1-03 · Motor de carga

**Status:** concluído em 30/10/2026

- [x] Integração com atividades importadas e perfil calibrado.
- [x] TRIMP/rTSS sem soma no mesmo dia; CTL/ATL/TSB.
- [x] ACWR e monotonia como flags, com dias sem carga explícitos.
- [x] Tela Tendências com PMC em Canvas.
- [x] Golden `G-LOAD-02` do motor de 28 dias.

**Aceite:** série de carga bate com o golden; ausência de dados não gera ACWR falso; a UI não trata ACWR como meta.

### P1-04 · Prontidão

**Status:** concluído em 30/10/2026 (validação instrumentada do Health Connect pendente)

- [x] Check-in diário de 15 s (sono, fadiga, dor, estresse + doença/lesão).
- [x] Persistência por data e prontidão real na tela Hoje.
- [x] CSV diário (sono, RHR, HRV, SpO2) com importação no perfil.
- [x] Health Connect read-only (sono, RHR, HRV, SpO2) com permissões e sync de 30 dias.
- [x] Diagnóstico básico de fontes, base local e confiança na tela Hoje.
- [x] Histórico de prontidão dos últimos 14 dias em Tendências.

### P1-05 · Prescrição

**Status:** em andamento — recomendação diária concluída em 30/10/2026

- [x] Guardrails: doença/vermelho, sono curto/âmbar, ACWR > 1,5.
- [x] Fácil Z2 com pace E e FC Z2 quando calibrado; RPE sem calibração.
- [x] Recomendação na tela Hoje com regra/evidência/confiança.
- [x] Plano semanal conservador por disponibilidade, no máximo 1 qualidade.
- [x] Plano persistido por semana (Room v7), sem reescrever com check-ins.
- [x] Taper por prova-alvo e qualidade nomeada por experiência.
- [x] Progressão por dose de qualidade, com disponibilidade como teto (ADR 0010).
- [ ] Blocos/periodização.
- [x] Deload a cada 4ª semana consecutiva de plano (−25% no volume, intensidade mantida).
- [x] Calor e esteira (autorreporte +3% subordinado a FC/RPE; qualidade na esteira por velocidade/inclinação).
- [ ] Exibir no plano o limite de 2 treinos fortes por semana (regra implementada; o histórico de conclusão já está em Tendências).

**Aceite parcial:** cenário vermelho/doente nunca gera qualidade; dia verde calibrado gera fácil com alvos; sem calibração não inventa pace.

### P1-06 · Tela Hoje

**Status:** em andamento — dados reais concluídos em 30/10/2026

- [x] Prontidão, treino recomendado e plano semanal reais.
- [x] Carga (CTL/ATL/TSB), km por dia e última atividade do banco.
- [x] Ações de concluir/pular sessão com motivo persistido.
- [x] Notificações locais de check-in e treino com deep link.
- [x] Iniciar/trocar treino (sessão guiada e troca de dia com guardrails).
- [x] Histórico de conclusão do plano em Tendências (8 semanas, sem streak).
- [x] Cards compactos: prontidão em duas linhas por sinal e plano sem pill de tipo redundante.
- [x] Plano em uma linha por sessão, rationale recolhível e carga vazia sem zeros.
- [x] Sem streak: dias sem check-in não geram punição nem cobrança (princípio "sem culpa").

### P1-07 · Tendências

**Status:** concluído em 31/10/2026

- [x] Volume semanal.
- [x] Consistência em 42/84/182 dias, conforme o filtro de período.
- [x] Relação pace×FC com gráfico de eficiência em m/batimento.
- [x] Distribuição 80/20 por tempo a partir dos samples, com quebra semanal e confiança.
- [x] Recordes pessoais: maior distância, melhor pace em corridas ≥ 3 km e maior tempo.
- [x] Filtros de período das atividades (6/12/26 semanas).
- [x] Heatmap de consistência por dia, alinhado por semana e colorido por volume.

### P1-08 · Check-in

**Status:** em andamento — histórico dos últimos 7 check-ins em 31/10/2026

- [x] Check-in de 15 s com Hooper (sono, fadiga, dor, estresse).
- [x] Calor autorreportado e sinal de doença/lesão.
- [x] Impacto na prontidão e na prescrição do dia.
- [x] Histórico dos últimos 7 check-ins com Hooper e sinais, na tela de check-in.
- [ ] Lembrete de check-in com histórico no widget/notificação (ver P1-11).

### P1-09 · Health Connect

**Status:** concluído em 31/10/2026

- [x] Permissões read-only de sono, FC de repouso, HRV e SpO2.
- [x] Leitura com paginação e agregação por dia local.
- [x] Diagnóstico de status, autorização, base local e fallback para check-in/CSV.
- [x] Sync incremental: primeira execução lê 30 dias; depois parte do último sucesso menos 1 dia.
- [x] Sync periódico diário via WorkManager, com retry exponencial em erro de leitura.
- [x] O mesmo `HealthSyncService` atende a tela de perfil e o worker.
- [x] Mescla por campo entre CSV e Health Connect, sem apagar dados do dia (`ADR-0016`).
- [x] Tela de métricas com um primário (sync), CSV secundário e formato sob demanda.

### P1-10 · Explicabilidade

**Status:** concluído em 31/10/2026

- [x] Bottom sheet “Por que este treino?” com rationale, regras, evidências e confiança.
- [x] Dados usados e dados ausentes explícitos, sem preencher lacunas com zero.
- [x] Catálogo de rótulos para `ruleId`/`evidenceId`, mantendo o ID bruto.
- [x] Mesmo sheet acessível pela prontidão e pelo treino, sem texto gerado por LLM.
- [x] Sheet da prontidão com rationale próprio e título por contexto.

### P1-11 · Notificações
Treino do dia, check-in, import concluído. Sem culpa/streak.

**Status:** concluído em 03/11/2026

- [x] Lembrete de check-in (18h) e do treino (7h) com deep link.
- [x] Notificação de importação concluída (sucesso, duplicado ou falha), com atalho para o treino importado.

### P1-12 · E2E e validação
Fluxo completo com dados reais; 4 semanas de uso; ajuste de pesos e paces.

### P1-13 · Linguagem simples

**Status:** concluído em 02/11/2026

- [x] Termos técnicos traduzidos em Hoje e Tendências (carga, proporção, variedade, hábito).
- [x] `COMMUNICATION.md` e `GLOSSARY.md` como fonte de verdade dos textos.
- [x] Seções de conteúdo e acessibilidade no `DESIGN_SYSTEM.md`.
- [x] Rótulo curto + `hint` nos `McStat`, sem quebra de palavra.
- [x] Proporção de carga vinda do motor (nunca hardcoded).
- [x] Onboarding com máscara de data, data de prova opcional e chips legíveis.
- [x] Perfil com card “Métricas diárias” e ação “Gerenciar métricas”.

---

## Fase 2 — Nuvem e extensões

- P2-01 Backend Ktor + Postgres + Flyway.
- P2-02 Auth + sync delta + conflitos.
- P2-03 Docker Compose local + Dokploy (TLS, backup, healthcheck).
- P2-04 Widgets (Glance).
- P2-05 Mapa interativo (MapLibre/OSM) se necessário.
- P2-06 Release APK assinado + GitHub Releases.
- P2-07 Open-Meteo (clima) com cache e fallback.
- P2-08 Macrobenchmark e baseline profiles.

## Fase 3 — Experimental

- P3-01 Conector Zepp não oficial (avaliar riscos e termos).
- P3-02 Social (opt-in, privacidade).
- P3-03 LLM apenas para reescrever explicações determinísticas, com validação.
- P3-04 Wear OS / companion.
- P3-05 Story de treino ao ar livre (vídeo com animação do percurso) — **mapa vetorial 1080p implementado; validação em aparelho pendente**.

### P3-05 · Story de treino ao ar livre (protótipo)

**Status:** protótipo validado em 02/11/2026 (`resposta 8`) — desempenho bom no aparelho real. `ADR-0015` implementado em 03/11/2026: frame final de 3 s + `BoundingBox` + mapa vetorial (`OpenFreeMap`/OSM + `MapLibre`) em 1080p, com validação pendente em aparelho real.

- [x] Planejador de rota, splits por km e timeline de 30 s.
- [x] Renderer 9:16 com rota, progresso e card por km (pace, FC, kcal).
- [x] Exportação MP4 720×1280/24 fps via MediaCodec, sem EGL nem dependência nova.
- [x] Galeria no Android 10+ e compartilhamento via `ACTION_SEND`/FileProvider.
- [x] Preview do vídeo antes de exportar.
- [x] Validação de desempenho e qualidade em aparelho real (`resposta 8`).
- [x] Exportar em 1080×1920 (8 Mbps); validar tempo de export no aparelho.
- [x] Mapa vetorial (`OpenFreeMap`/OSM + `MapLibre`) como background estático no vídeo (`ADR-0015`).
- [x] Frame final (3 s) com dados do treino (`distância`, `tempo`, `pace`, `FC média`, `kcal`) e branding `MeuCoach` (`ADR-0015`, entrega 1).
- [x] `BoundingBox` geográfico no `StoryPlan` para o mapa (`ADR-0015`).
- [ ] Validar mapa, alinhamento e exportação em aparelho real (`ADR-0015`).
- [x] Logo `MeuCoach` no rodapé do story (asset recortado em `core:designsystem`, prévia e vídeo).
- [ ] Refinar a logo com prompt estruturado (`resposta 3`).

**Descrição da feature (proposta inicial):**

Em cada treino ao ar livre realizado (com dados de GPS — GPX, TCX, FIT com posição), o usuário poderá importar o arquivo GPS e o app criará um vídeo curto, no formato de stories do Instagram (vertical, 9:16, curto), com uma animação mostrando o percurso feito pelo usuário no mapa. A cada km percorrido, a animação pausará automaticamente por aproximadamente 2 segundos e mostrará detalhes por km (pace, FC média, elevação, etc.), depois segue até o próximo km.

**O que ainda precisa ser definido com o usuário (planejamento maior):**

Antes de implementar qualquer linha de código ou decidir arquitetura, faremos uma sessão de planejamento com o Rafael para responder perguntas como:

1. **Formato e destino do vídeo:** o vídeo será exportado para a galeria do dispositivo, compartilhado diretamente no Instagram (via `ACTION_SEND`), salvo como rascunho no app, ou todos? Qual a duração máxima ideal (15s, 30s, 60s)?
2. **Fonte dos dados GPS:** a feature usará apenas arquivos importados (GPX, TCX, FIT), ou também poderá usar os dados já salvos no banco (Room) das atividades importadas anteriormente? Se usar do banco, como lidar com dados sem posição (corridas de esteira)?
3. **Design do vídeo:** quais detalhes devem aparecer no card de pausa por km? Pace, FC, elevação, cadência, calorias, ou apenas alguns? Há preferência por cores, fonte, posição do mapa no frame, legenda, ou overlay de texto?
4. **Renderização:** a animação será feita com Canvas Android (como os gráficos atuais), com uma biblioteca de vídeo (MediaCodec, FFmpeg, ou uma dependência externa), ou com um componente Compose que exporta frames? Há restrição de tamanho da dependência (AGENTS.md regra 8)?
5. **Performance e privacidade:** o vídeo nunca deve conter dados de localização real (regras de privacidade do projeto). A sanitização das posições já existe nos parsers, mas como tratar a rota no vídeo — mostrar o traçado real (mesmo sanitizado) ou uma versão estilizada/abstrata? O usuário quer que o vídeo seja totalmente offline?
6. **Quando acionar:** a tela de criação do vídeo apareceria automaticamente após importar uma atividade com GPS, como uma ação manual no detalhe da atividade (`P0-09`), ou em uma aba separada (ex.: "Stories" no shell)?
7. **Integração com o motor determinístico:** a feature não altera carga, prontidão ou prescrição; é apenas visualização/exportação. Confirmamos que nunca usará LLM para decidir o percurso, apenas para reescrever textos do overlay, se aplicável?

**Status atual:** proposta aceita para planejamento — **respostas consolidadas em 31/10/2026** (`ADR 0012`); **evolução aceita em 02/11/2026** (`ADR 0015` — mapa real `OSM` + `MapLibre`, frame final 3 s, logo `MeuCoach`); implementação condicionada a build verde e validação visual (`Roborazzi`).

**Decisões consolidadas (baseado nas respostas do usuário 0):**

- **Fonte de dados:** importação direta de arquivo GPS (`GPX`, `TCX`, `FIT`) no momento; futuro: treino iniciado pelo app com localização salva no banco.
- **Acionamento:** manual, no detalhe da atividade (`P0-09`), apenas quando há posição.
- **Design do vídeo:** card de pausa por km com `pace` + `FC média` + `kcal`; cores do design system (`lima` para destaque, `coral` para fisiologia); fonte grande; overlay elegante.
- **Duração:** padrão 30 segundos (compatível com stories); exportação para galeria + `ACTION_SEND` direto.
- **Renderização:** `Canvas` + `MediaCodec` (nativo Android SDK) com `Bitmap` de mapa estático (`ADR-0015`); nenhuma dependência externa grande hoje. `MapLibre` (`org.maplibre.gl:android-sdk`) entra no APK quando o mapa for implementado (`~3–5 MB`, `BSD-2-Clause`, justificado pela regra 8). Se a qualidade não atender ao objetivo de "postar com vontade", `ADR` futuro (`0016`) avaliará ajustes.
- **Rota:** mapa real (`OSM` via `MapLibre`) como background estático no vídeo (`ADR-0015`); traçado da rota animado por cima (`Canvas` + `PathMeasure`); local de partida marcado com emoji (`🏃`). A região é precisa porque usa o `bounding box` das coordenadas GPS do treino. Quando implementado, não será totalmente offline na exportação (exige rede para tiles `tile.openstreetmap.org`), mas não enviará dados pessoais para servidores.
- **Frame final:** novo segmento (`FINAL`, 3 s) após o percurso, com dados do treino (`distância`, `tempo`, `pace médio`, `FC média`, `kcal`) e branding `MeuCoach` (`nome` centralizado + logo opcional, já disponível como asset) (`ADR-0015`).
- **Integridade:** apenas visual/exportação; motor determinístico (`core:domain`) não afetado; LLM não decide percurso.
- **Privacidade:** posições nunca enviadas para servidores (o `bounding box` é calculado localmente; quando o mapa existir, só tiles `OSM` de uma região aproximada serão baixados, sem identificação). Sanitização já existente nos parsers (`P0-05`/`P0-06`) aplicada.

**Perguntas respondidas pelo usuário:**

1. Formato/destino: galeria + share direto (Instagram, etc.).
2. Fonte GPS: importação direta (futuro: treino pelo app).
3. Design: pace + FC + kcal.
4. Renderização: precisa de qualidade alta para motivar a postar → `Canvas` + `MediaCodec` escolhido.
5. Privacidade/rota: traçado real sanitizado, offline, design premium para chamar atenção.
6. Quando acionar: manual no detalhe (`P0-09`).
7. Integração: confirma apenas visual/export.

**Próximos passos antes de implementar:**
1. Validar se `Canvas` + `MediaCodec` atende à qualidade desejada com um protótipo simples (sem impacto no `core:domain`).
2. Se o protótipo não atender, criar ADR `0013` para avaliar dependência externa.
3. Definir testes de UI (`Roborazzi`) para o card de pausa e animação.
4. Adicionar ao `docs/PRODUCT.md` se for confirmado para a Fase 3.


---

## Backlog técnico

| ID | Item | Quando |
|---|---|---|
| T-01 | Convention plugins Gradle | quando houver > 6 módulos |
| T-02 | Detekt + baseline | Fase 1 |
| T-03 | Kover para Android modules | Fase 1 |
| T-04 | Property-based tests (Kotest) | Fase 1 |
| T-05 | Fuzz leve do parser FIT | Fase 0/1 |
| T-06 | Export JSON e "apagar todos os dados" na UI — **concluído em 03/11/2026**; ZIP descartado (os arquivos brutos não ficam guardados); o reset também zera a marca d'água do Health Connect | Fase 1 |
| T-07 | Migração para MapLibre | Fase 2 |
| T-08 | i18n (pt-BR base, en futuro) | Fase 2 |
| T-09 | Fuso e cidade em lista fixa do Brasil: avaliar campo livre ou lista completa | Fase 2 |

## Critérios de saída do MVP

- [ ] Importa FIT/TCX/GPX e deduplica.
- [ ] Abre detalhe com gráficos e splits.
- [ ] Calcula carga e prontidão com confiança.
- [ ] Prescreve a semana e o treino do dia com rationale.
- [ ] Funciona offline e sem conta.
- [x] Exporta e apaga todos os dados (T-06, 03/11/2026).
- [ ] Testes verdes, cobertura ≥ 85% no domínio.
- [ ] Nenhum dado real no repositório.
