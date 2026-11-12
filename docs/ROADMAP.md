# ROADMAP.md — MeuCoach

**Versão:** 1.33 · **Data:** 12/11/2026 · **Mudança:** gate visual estabilizado — gráficos Vico passam a usar `CartesianChartModel` síncrono, com `ChartRenderTest` e 5 execuções completas verdes sob carga; C3-07 (FR-207), D6-B, BUG-06, BUG-03/04 e FR-011 seguem concluídos.

> Sem prazo. O critério é qualidade e confiança, não velocidade. Cada fase termina com build verde, testes e documentação atualizada.

## Visão de fases

| Fase | Objetivo | Status |
|---|---|---|
| **0 — Fundação** | Repositório, build, CI, design system, modelo, domínio inicial, import e detalhe de atividade | **Concluída em 30/10/2026** |
| **1 — Coach** | Perfil, calibração, carga, prontidão, prescrição, Hoje, Tendências, Health Connect | **Em andamento — ciclo 1 concluído** |
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
- [x] Oferta reativa ao importar: corrida do dia com sessão pendente abre overlay para vincular ao treino; se o dia já foi concluído, aviso de que fica como treino livre; `MANUAL` pode ser corrigido pela corrida real (08/11/2026).
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

**Status:** concluído em 30/10/2026; ampliado em 03/11/2026; Tier B estima e chega à tela em 11/11/2026 (ADR 0035, fatias 1–2); validado em dados reais no mesmo dia

- [x] Tier A/B/C por performance recente e treinos com FC+pace.
- [x] VDOT, pace de limiar e LTHR com fórmulas documentadas.
- [x] Tela de entrada/resultado, confiança e avisos.
- [x] Salvamento no perfil e resumo de calibração.
- [x] Zonas de FC no detalhe da atividade após calibrar.
- [x] LTHR manual no perfil, com validação contra FC de repouso e máxima vista.
- [x] “Usar esta corrida para calibrar” apenas em esforço elegível, com formulário preenchido e confirmação explícita.
- [x] Tier B (BUG-06/FR-006, fatia 1): sem prova recente, pace de limiar pelo melhor esforço sustentado de 20–35 min com FC, VDOT derivado a 88% e LTHR na janela 25–35 min, confiança média, avisos e confirmação explícita (`ADR 0035`).
- [x] Tier B fatia 2: `CalibrationViewModel` alimenta `trainingEfforts` com os treinos do contador, a tela oferece “Estimar pelos meus treinos” (≥ 3 esforços), mostra a estimativa com copy honesta, sem IDs técnicos, e mantém goldens dark/light e de Tier B.
- [x] Validação em dados reais (11/11/2026): estimativa de VDOT 30,6 e pace 6:18/km contra 29,63 e 6:28/km do VDOT de prova (5k ~31 min), ~3% acima; a calibração salva (27,09) era conservadora.

**Aceite:** performance recente gera VDOT/LTHR/pace; ausência de dados não inventa valores; perfil mantém a calibração; a estimativa por treinos só chega ao perfil após confirmação explícita e ficou dentro da tolerância da prova no usuário 0.

### P1-03 · Motor de carga

**Status:** concluído em 30/10/2026; viés do PMC corrigido em 11/11/2026

- [x] Integração com atividades importadas e perfil calibrado.
- [x] TRIMP/rTSS sem soma no mesmo dia; CTL/ATL/TSB.
- [x] ACWR e monotonia como flags, com dias sem carga explícitos.
- [x] Tela Tendências com PMC em Canvas.
- [x] Golden `G-LOAD-02` do motor de 28 dias.
- [x] Burn-in de 180 dias das EMAs do PMC (BUG-03): histórico anterior à janela de 42 dias alimenta CTL/ATL; sem histórico, a série continua começando em zero; cobertura e `lastActivityAt` não usam o aquecimento.

**Aceite:** série de carga bate com o golden; ausência de dados não gera ACWR falso; a UI não trata ACWR como meta; carga constante converge para o valor real em vez de 63% dele.

### P1-04 · Prontidão

**Status:** concluído em 30/10/2026; z-score de HRV corrigido em 11/11/2026 (validação instrumentada do Health Connect pendente)

- [x] Check-in diário de 15 s (sono, fadiga, dor, estresse + doença/lesão).
- [x] Persistência por data e prontidão real na tela Hoje.
- [x] CSV diário (sono, RHR, HRV, SpO2) com importação no perfil.
- [x] Health Connect read-only (sono, RHR, HRV, SpO2) com permissões e sync de 30 dias.
- [x] Diagnóstico básico de fontes, base local e confiança na tela Hoje.
- [x] Histórico de prontidão dos últimos 14 dias em Tendências.
- [x] HRV em espaço logarítmico (BUG-04): média e desvio de `ln(RMSSD)`; `HrvMetrics.zScore` é a única fonte do score, e a prescrição sobe para `daily-prescription-v3`.

### P1-05 · Prescrição

**Status:** em andamento — recomendação diária concluída em 30/10/2026

- [x] Guardrails: doença/vermelho, sono curto/âmbar, ACWR > 1,5.
- [x] Fácil Z2 com pace E e FC Z2 quando calibrado; RPE sem calibração.
- [x] Recomendação na tela Hoje com regra/evidência/confiança.
- [x] Plano semanal conservador por disponibilidade, com até 2 qualidades (a segunda só com progressão).
- [x] Plano persistido por semana (Room v7), sem reescrever com check-ins.
- [x] Taper por prova-alvo e qualidade nomeada por experiência.
- [x] Progressão por dose de qualidade, com disponibilidade como teto (ADR 0010).
- [ ] Blocos/periodização.
- [x] Deload a cada 4ª semana consecutiva de plano (−25% no volume, intensidade mantida).
- [x] Calor e esteira (autorreporte +3% subordinado a FC/RPE; qualidade na esteira por velocidade/inclinação).
- [x] Prescrição única do dia persistida e versionada; plano como intenção (C1-03, ADR 0023).
- [ ] Exibir no plano o limite de 2 treinos fortes por semana (regra implementada; o histórico de conclusão já está em Tendências).

**Aceite parcial:** cenário vermelho/doente nunca gera qualidade; dia verde calibrado gera fácil com alvos; sem calibração não inventa pace.

### P1-06 · Tela Hoje

**Status:** em andamento — dados reais concluídos em 30/10/2026

- [x] Prontidão, treino recomendado e plano semanal reais.
- [x] Carga (CTL/ATL/TSB), km por dia e última atividade do banco.
- [x] Ações de concluir/pular sessão com motivo persistido.
- [x] Notificações locais de check-in e treino com deep link.
- [x] Iniciar (prévia de cronômetro) e trocar treino (com guardrails); sessão guiada persistente constava no ciclo 3 e foi proposta para o ciclo 2, ainda sem aprovação (`docs/PLANO_CICLO_2_GUIADO_E_REPLANEJAMENTO.md` v0.2).
- [x] Histórico de conclusão do plano em Tendências (8 semanas, sem streak).
- [x] Cards compactos: prontidão em duas linhas por sinal e plano sem pill de tipo redundante.
- [x] Plano em uma linha por sessão, rationale recolhível e carga vazia sem zeros.
- [x] Um card principal; plano como contexto com selo “Ajustado hoje” e sem CTA de conclusão próprio (C1-03).
- [x] Conclusão com evidência (corrida vinculada ou “fiz sem registro”) e vínculo 1:1; descanso/doença encerram o dia (C1-04).
- [x] Treino concluído: selo “Concluído”, resumo do que foi feito, plano como contexto e ações escondidas; “feito sem registro” explicado no card e no plano (08/11/2026).
- [x] Sem streak: dias sem check-in não geram punição nem cobrança (princípio "sem culpa").

### P1-07 · Tendências

**Status:** concluído em 31/10/2026 — gráficos Vico em 04/11/2026

- [x] Volume semanal.
- [x] Consistência em 42/84/182 dias, conforme o filtro de período.
- [x] Relação pace×FC com gráfico de eficiência em m/batimento.
- [x] Distribuição 80/20 por tempo a partir dos samples, com quebra semanal e confiança.
- [x] Recordes pessoais: maior distância, melhor pace em corridas ≥ 3 km e maior tempo.
- [x] Filtros de período das atividades (6/12/26 semanas).
- [x] Heatmap de consistência por dia, alinhado por semana e colorido por volume.
- [x] Gráficos cartesianos migrados para Vico 3.3.1 (`McLineChart`/`McBarChart`) com eixos, unidade, tooltip, legenda e lacunas sem linha inventada.
- [x] PMC com base, fadiga e balanço (CTL/ATL/TSB) na mesma escala e eixo em pontos.
- [x] Prontidão em barras coloridas por nível e economia com eixo ampliado e aviso explícito.

### P1-08 · Check-in

**Status:** em andamento — histórico dos últimos 7 check-ins em 31/10/2026

- [x] Check-in de 15 s com Hooper (sono, fadiga, dor, estresse).
- [x] Calor autorreportado e sinal de doença/lesão.
- [x] Impacto na prontidão e na prescrição do dia.
- [x] Histórico dos últimos 7 check-ins com Hooper e sinais, na tela de check-in.
- [ ] Lembrete de check-in com histórico no widget/notificação (ver P1-11).

### P1-09 · Health Connect

**Status:** concluído em 31/10/2026 — painel de saúde em 04/11/2026

- [x] Permissões read-only de sono, FC de repouso, HRV e SpO2.
- [x] Leitura com paginação e agregação por dia local.
- [x] Diagnóstico de status, autorização, base local e fallback para check-in/CSV.
- [x] Sync incremental: primeira execução lê 30 dias; depois parte do último sucesso menos 1 dia.
- [x] Sync periódico diário via WorkManager, com retry exponencial em erro de leitura.
- [x] Sync automático no resume quando a última leitura tem mais de 6 h, com backoff de 15 min e worker como fallback (`ADR-0021`).
- [x] O mesmo `HealthSyncService` atende o resume, a aba Saúde e o worker.
- [x] Mescla por campo entre CSV e Health Connect, sem apagar dados do dia (`ADR-0016`).
- [x] Tela de métricas com um primário (sync), CSV secundário e formato sob demanda.
- [x] Painel de saúde com sono, FC de repouso, HRV e SpO₂: média móvel de 7 dias, base individual, faixa-alvo, janela 7/30/60 dias e tabela por métrica.
- [x] `DailyMetricsTrendsCalculator` puro no domínio, com lacunas preservadas e cobertura de dias.

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
- [x] Plano da semana explica a confiança em “Por que este plano?” e avisa quando os dados são parciais.
- [x] Economia mostra a escala ampliada abaixo do gráfico e a unidade no eixo.

### P1-14 · Saúde como destino principal

**Status:** concluído em 05/11/2026 — validação em aparelho pendente

- [x] 5ª aba `Saúde` (`Hoje · Corridas · Tendências · Saúde · Perfil`), rota top-level `health`, sem seta de voltar.
- [x] Hoje com link “Ver recuperação” no card de prontidão, sem duplicar gráficos.
- [x] Perfil sem o card grande de Health Connect; link compacto + backup/privacidade.
- [x] Checklist e tutorial apontam para a aba Saúde.
- [x] Testes e goldens em 393/360 dp; falta validar fonte 200% no aparelho.

**Aceite:** a Saúde é descoberta sem passar pelo Perfil e o Hoje continua decidindo o treino.

### P1-15 · Sync automático do Health Connect

**Status:** concluído em 05/11/2026 — validação em aparelho pendente

- [x] `HealthSyncStore` com `lastSyncedAt`/`lastAttemptAt` além da data incremental.
- [x] `HealthSyncService.syncIfStale()` com frescor de 6 h, backoff de 15 min e `Mutex`.
- [x] `HealthSyncCoordinator` + `LifecycleResumeEffect` no `MainContent`.
- [x] Worker diário como fallback (`ExistingPeriodicWorkPolicy.KEEP`).
- [x] UI com “Sincronizando…”/“Atualizado há X” e erro passivo; manual continua override.
- [x] Testes de store, serviço, coordenador e cópia.
- [ ] Validar no aparelho: sync após 6 h, permissão revogada e worker fallback.

**Aceite:** abrir o app após 6 h atualiza o Health Connect sem tocar em botão; sem permissão, nenhum diálogo automático.

### P1-16 · Polimento da aba Saúde

**Status:** concluído em 05/11/2026 — validação em aparelho pendente

- [x] Aviso “Os dados de hoje ainda não chegaram” quando há histórico, HC conectado e a última métrica é anterior a hoje.
- [x] Link “Abrir Health Connect” para diagnóstico, com fallback para a loja.
- [x] Testes e goldens do aviso e do card de fontes.
- [ ] Resumo compacto no Hoje — avaliado e adiado; o link “Ver recuperação” já resolve.
- [ ] Proveniência por campo — decisão futura, se houver necessidade de auditoria por métrica.

**Aceite:** o usuário entende por que o dia atual ainda não aparece e consegue abrir o Health Connect sem sair da aba.

### P1-17 · Previsão de prova por VDOT/Riegel (D6-B)

**Status:** concluído em 11/11/2026 ([ADR 0036](adr/0036-race-time-prediction-vdot-riegel.md))

- [x] `RaceTimePredictor` puro: tempo previsto por inversão de Daniels, faixa ±3%/±5% e Riegel como checagem 5k–meia.
- [x] Proveniência da calibração (`PERFORMANCE`/`TRAINING_ESTIMATE`, data e performance de referência) em Room 17, com migração 16→17 testada.
- [x] Calibração grava origem/data; Perfil e Hoje mostram “Previsão de referência” com confiança, fonte e ausência explícita.
- [x] `R-RACE-03`/`EV-RIEGEL-1981` no catálogo, testes de domínio/dados/app e goldens de prova regravados.

**Aceite:** a previsão não inventa número sem VDOT/calibração recente, nunca entra em plano/prescrição e é rotulada como referência, não promessa.

---

## Ciclo 1 — Correções críticas (concluído em 06/11/2026)

**Status:** concluído — C1-01 a C1-06 implementados e documentados.
**Plano técnico:** [`docs/PLANO_TECNICO_CICLO_1.md`](PLANO_TECNICO_CICLO_1.md) · **ADRs:** 0022–0026.

| Ordem | ID | Iniciativa | Depende de | Gate | Status |
|---|---|---|---|---|---|
| 1 | **C1-01** | Tempo determinístico e virada de dia/semana | — | Clock fixo 23:59/00:01, segunda-feira, fuso e DST | Concluído |
| 2 | **C1-02** | Cobertura de carga, ACWR desconhecido e gate de qualidade | C1-01 | Sem dado/sem frescor ⇒ sem qualidade e explicação | Concluído |
| 3 | **C1-03** | Plano como intenção + prescrição única do dia (N-01/N-02) | C1-01, C1-02 | Um card; prescrição versionada; plano no meio da semana sem passado | Concluído |
| 4 | **C1-04** | Conclusão com evidência e vínculo atividade ↔ sessão | C1-03 | Impossível `DONE` sem evidência; migração testada | Concluído |
| 5 | **C1-05** | Lixeira de 7 dias, reset efetivo e época de dados | C1-01 | Reset sem ressurreição; expurgo por clock | Concluído |
| 6 | **C1-06** | Verdade documental, ADRs e regressões finais | C1-01..C1-05 | Docs = código; suíte e goldens verdes | Concluído |

**Entregas:** C1-01 (tempo), C1-02 (carga/qualidade), C1-03 (prescrição única + N-01/N-02), C1-04 (conclusão com evidência e vínculo), C1-05 (lixeira/reset) e C1-06 (documentação reconciliada com o schema v12 e as regras de evidência) implementados em 06/11/2026.

**Pendências herdadas do ciclo (não bloqueiam o fechamento):**

- Revisão humana das tolerâncias de vínculo e da copy de `LEGACY_UNVERIFIED`.
- Validação dos limiares do ADR 0026 (8 dias/28, frescor de 14 dias, critérios de iniciante) com dados reais.
- Na conclusão do ciclo 1, `GUIDED` persistente, N-03 e N-05 ficaram para ciclos posteriores; `GUIDED` foi depois proposto para o ciclo 2 (ainda não aprovado). N-03 e N-05 permanecem pendências separadas.

### Ciclo 2 — proposta auditada; R-01, R-02 e R-03 implementados (08/11/2026)

**Plano:** [`docs/PLANO_CICLO_2_GUIADO_E_REPLANEJAMENTO.md`](PLANO_CICLO_2_GUIADO_E_REPLANEJAMENTO.md) v0.7. A auditoria técnica foi incorporada; decisões do replanejamento foram registradas. R-01 foi implementado em `core:domain`, R-02 em `core:data` (schema v13) e R-03 no app/UI. O [ADR 0028](adr/0028-versioned-plan-replanning.md) foi extraído e aceito; o ADR 0027 segue proposto e a sessão guiada (G) não foi iniciada.

| Etapa proposta | Escopo | Gate antes de implementar | Estado |
|---|---|---|---|
| **R — Replanejamento** | R-01: `PlanReplanner` puro; preservar sessões feitas e validar a semana mesclada. R-02: migração `12→13`, snapshots versionados e `applyReplan` transacional. R-03: casos de uso, sugestão por dia, invalidação por versão/hash, selo e diff | Testes primeiro em `core:domain`; R-03 depende dos gates do plano | **R-01 concluído em 08/11/2026** (28 testes); **R-02 concluído em 08/11/2026** (migração/revisões/export-reset); **R-03 concluído em 08/11/2026** (Evaluate/ApplyReplan, sugestão/recusa/supressão, versão e diff, goldens) |
| **G — Sessão guiada** | Player persistente, cues temporais, TTS opcional e evidência `GUIDED`; RPE sem carga sRPE nesta etapa | Fechar tipo de FGS/distribuição e comportamento diante de doença durante execução; validar em aparelho | Proposto |

FR-011 (revisão semanal com métricas e observações) foi concluído em 11/11/2026 (consolidação determinística no domínio e card em Tendências, ADR 0034); replanejamento é relacionado, mas não o substitui. FR-010 foi concluído em 11/11/2026 e passa a ser a base da revisão semanal. N-03 e N-05 permanecem pendências separadas. R foi concluído antes de G; a migração `13→14` da sessão guiada continua isolada. A3/A4/A7 continuam bloqueando G-03.

**Regras adotadas no ciclo 1:**

- Cada entrega termina com gate próprio; a próxima só começa com o gate anterior verde.
- Nenhuma superfície nova além de execução/evidência (parte da correção).
- Sem refatoração abrangente, sem novos módulos e sem mudança de stack.
- Toda regra nova cita `ruleId`, `evidenceId` e confiança.
- Dados reais nunca entram no diff.

### Ciclo 3 — C3-01A/B, C3-02, C3-03/04, C3-05, C3-06 e C3-G2 implementados; C3-G1 detalhado (11/11/2026)

**Documentos:** [`docs/PRD_ADAPTACAO_E_BRIEFING_v0.2.md`](PRD_ADAPTACAO_E_BRIEFING_v0.2.md),
[`docs/RELATORIO_ALTERACOES_PRD_CICLO_3_v0.2.md`](RELATORIO_ALTERACOES_PRD_CICLO_3_v0.2.md),
[`PLANO_TECNICO_CICLO_3_C3-06.md`](PLANO_TECNICO_CICLO_3_C3-06.md),
[ADR 0029](adr/0029-f4-hydration-nutrition-copy.md),
[ADR 0030](adr/0030-race-event-and-weekly-taper.md),
[ADR 0031](adr/0031-profile-planning-fingerprint.md) e
[ADR 0032](adr/0032-race-autocompletion-and-target-projection.md).

| Gate | Escopo | Estado |
|---|---|---|
| **C3-G0** | Fingerprint/proveniência, contexto temporal, compare-and-apply e projeção canônica de objetivo; proposta de evento | **Decisões aprovadas**; detalhes técnicos/migração pendentes |
| **C3-01A** | `QUALITY → EASY` para iniciante, status-aware e sem reescrever histórico | **Implementado em 10/11/2026**; testes de domínio/app verdes |
| **C3-01B** | Fingerprint/T4, migração e aceite atômico | **Implementado em 10/11/2026**; schema 14, T4 e compare-and-apply testados |
| **C3-02** | Avaliação ao salvar Perfil/Calibração e UI da sugestão | **Implementado em 10/11/2026**; card no Perfil e resultado consumido uma vez |
| **C3-03/04** | Briefing genérico a partir da prescrição e sheet acessível no Hoje | **Implementado em 10/11/2026**; fidelidade, determinismo, RPE sem calibração, downgrade, `DONE`/`CLOSED` sem briefing, goldens e estados de UI testados |
| **C3-G1** | Contrato Room/migração, `TodayDecision`, vínculo com atividade, taper por data, adesão e testes do evento | **Desenho técnico concluído em 10/11/2026** em [`PLANO_TECNICO_CICLO_3_EVENTO.md`](PLANO_TECNICO_CICLO_3_EVENTO.md); nenhum código/migração implementado |
| **C3-05** | Implementação do evento de prova e taper 2A, sem `WorkoutType.RACE` | **Concluído em 10/11/2026:** modelo/domínio, `TodayDecision`, Room 15, repositório, taper/cutoff, adesão, troca, `GetTodayDecision`, notificação e UI de Perfil/Hoje/Plano com goldens |
| **C3-G2** | Revisão de fontes/copy de F4 | **Concluído e implementado em 10/11/2026:** ADR 0029 aceito (fontes ACSM 2007/NATA 2017/Thomas 2016 revisadas; `EV-NATA-2017` corrige a citação do PRD), copy aprovada em `COMMUNICATION.md` §6.10 e seções de hidratação/alimentação no sheet de briefing com testes de regressão e goldens |
| **C3-06** | Auto-conclusão da prova e referência da meta (pace/faixa/passagens) | **Concluído em 11/11/2026:** ADR 0032, regra 90–110% com uma única candidata, `completionSource`/Room 16, undo com recusa persistida, referência aritmética no Perfil/Hoje, testes de domínio/dados/app/UI e goldens de prova atualizados |
| **C3-07** | Recuperação pós-prova (FR-207) | **Concluído em 12/11/2026:** ADR 0037, janela 2/3/5/10 dias por distância, sessões fáceis com teto, sem qualidade/longão, `R-RACE-04` LIMITED, sugestão de revisão `RACE_COMPLETED` com recusa lembrada por semana+evento, Hoje/Plano/Perfil; sem migração |

Fora do escopo implementável: limite de longão por fração da distância (FR-206).
A recuperação pós-prova (FR-207) foi concluída em 12/11/2026 pelo ADR 0037.
D6-B foi implementado pelo ADR 0036 em 11/11/2026.
N-03 continua sem entrega; o PRD v0.2 apenas definiu a direção de evento/taper que
a implementação C3-05/C3-06 seguiu.

### FR-010 — Comparação planejado × executado (concluído em 11/11/2026)

**Status:** concluído — calculadora pura em `core:domain/feedback`, caso de uso no
app e card no detalhe da atividade. Compara a corrida com a prescrição do dia só
quando ela é a evidência do treino; tolerâncias de 10% (duração/distância) e 1
ponto de RPE; treino livre não inventa alvo; dado ausente aparece como ausente.
Sem migração nem dependência nova. ADR 0033, `R-FEEDBACK-01`, 3 goldens novos.
Interpretação de “próximo passo” e comparação por splits seguem pendentes.

### FR-011 — Revisão semanal concluída em 11/11/2026

**Status:** concluído — `WeeklyReviewCalculator` puro em `core:domain/review`
consolida adesão, volume planejado × executado, intensidade medida, prontidão e
sono da última semana fechada, em até 3 observações determinísticas com
regra/evidência/confiança. Estados `AVAILABLE`, `EMPTY_NO_PLAN` e `EMPTY_NO_DATA`;
dado ausente continua ausente. O card “Revisão da semana” em Tendências mostra
adesão, volume, zonas, prontidão, sono e observações, com confiança, estados vazios
e goldens dark/light/estreito. Sem LLM, sem migração e sem dependência nova.
`R-REVIEW-01`, ADR 0034, 35 testes de domínio + 16 de app.

**Fora desta entrega:** “prévia da próxima semana” só quando existir plano futuro
persistido; interpretação de “próximo passo”, monotonia/ACWR por semana e
comparação por splits seguem pendentes.

### N-03 — Semana de prova e recuperação (backlog do ciclo 2)

**Status:** recuperação pós-prova concluída em 12/11/2026 ([ADR 0037](adr/0037-post-race-recovery.md)); os demais itens continuam no backlog.

- [x] Adicionar recuperação pós-prova (1–2 semanas conforme distância, sem qualidade) — implementada como janela determinística 2/3/5/10 dias com sessões curtas e sem longão.
- [ ] Reduzir também a duração/intensidade da qualidade no taper, não só fácil/longão.
- [ ] Rever a relação entre taper e deload.
- [ ] Depende de decisão de objetivo/prova (D7) e de periodização; exige ADR próprio.

### N-05 — Backfill do Health Connect (backlog de integração)

**Status:** registrado em 06/11/2026; fora do ciclo 1.

- A primeira sincronização continua lendo 30 dias.
- Avaliar backfill de histórico anterior quando `ExerciseSession` e importação de exercícios entrarem (ciclo 2), após validar o comportamento do Zepp no aparelho.
- Não prometer histórico “desde sempre” antes de medir o que o Health Connect realmente entrega.

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

**Proposta de evolução:** `docs/STORY_V2.md` — “O Veredito”. Fases A (área segura, câmera, film look, 30 fps e capa), B (veredito por km, rota por zona) e câmera v2 (zoom por card + pitch/yaw) concluídas em 03/11/2026; fase C (áudio/morph) pendente.

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
- [x] Story v2 — Fase A: área segura do Instagram, câmera com zoom/rotação, film look, 30 fps e capa PNG (`docs/STORY_V2.md`).
- [x] Story v2 — Fase B: rota colorida por zona e veredito do coach por km.
- [x] Story v2 — câmera v2: flyover com follow do corredor, zoom por card, rotação pela direção da rota e pitch/yaw via homografia no mapa e na rota (`docs/STORY_V2.md` §12).
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

## Backlog de apresentação (Fase 3)

Itens levantados na verificação visual de 03/11/2026. Não bloqueiam o MVP e ficam para a próxima sessão de UI.

| ID | Item | Status |
|---|---|---|
| U-01 | Consistência de termos: “HRV” vs “variabilidade”, “SpO2” vs “SpO₂” e rótulo de fuso (“Fortaleza”) quando a cidade é “Natal”. | Pendente |
| U-02 | Check-in em uma linha: “Check-in feito hoje” com resumo curto, sem repetir “Você pode revisar”. | Pendente |
| U-03 | Contextualizar a confiança do plano (“Plano · confiança baixa”) para não competir visualmente com a confiança da prontidão. | Pendente |
| U-04 | Revisar Tendências e o detalhe da atividade com uma corrida importada (cards, gráficos, splits e zonas). | Pendente — depende de uma importação real |
| U-05 | Perfil → Saúde e dados: explicar o Health Connect, mostrar o estado real da conexão e separar backup de importação de métricas. | Concluído em 03/11/2026 |
| U-06 | Prontidão sem nome de cor: títulos **Alta/Moderada/Baixa** no gauge e no histórico. | Concluído em 04/11/2026 |
| U-07 | Voltar para Hoje depois de Tendências (inclusive entrando pelo card) sem reabrir a aba anterior. | Concluído em 04/11/2026 |
| U-08 | Foto do cabeçalho da Hoje abre o Perfil. | Concluído em 04/11/2026 |
| U-09 | Explicar a carga de treino (Base, Fadiga, Balanço, Carga, Aumento e Variedade) e corrigir o TRIMP para minutos. | Concluído em 04/11/2026 |
| U-10 | Tutorial Fase A: back nativo, progresso acessível, CTA estável, layout ancorado, strings e permissão de notificação contextual. | Concluído em 05/11/2026 |
| U-11 | Tutorial Fase B: 3 cenas com plano/prontidão reais, primeiro passo dinâmico e checklist “Complete seu setup” no Hoje. | Concluído em 05/11/2026 |
| U-12 | Tutorial Fase C: primeira vitória, dicas contextuais únicas, medição local exportável e versionamento do tutorial. | Concluído em 05/11/2026 |
| U-13 | Aplicar o padrão de `DESIGN_SYSTEM.md` §7.3 a todos os campos de data/duração: unificar a máscara de data, migrar tempo alvo do onboarding e data/tempo da Calibração. | Concluído em 11/11/2026 |

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
| T-10 | Gate de goldens estável: `CartesianChartModel` síncrono no Vico e `ChartRenderTest` com asserção de pixels; sem flake em 5 execuções sob carga | **Concluído em 12/11/2026** |

## Critérios de saída do MVP

Capacidade entregue; a aceitação final depende de P1-12 (4 semanas de uso real) e da validação em aparelho.

- [x] Importa FIT/TCX/GPX e deduplica.
- [x] Abre detalhe com gráficos e splits.
- [x] Calcula carga e prontidão com confiança.
- [x] Prescreve a semana e o treino do dia com rationale.
- [x] Funciona offline e sem conta (exceto tiles do mapa do story, opt-in).
- [x] Exporta e apaga todos os dados (T-06; lixeira de 7 dias e reset com época).
- [x] Testes verdes, cobertura ≥ 85% no domínio (97,56% de linhas).
- [x] Nenhum dado real no repositório (fixtures sanitizadas/sintéticas).
- [ ] P1-12: validação E2E com 4 semanas de uso real.
