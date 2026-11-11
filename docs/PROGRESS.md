# PROGRESS.md — Diário de progresso

**Atualizado:** 11/11/2026 (FR-010 concluído: comparação planejado × executado no detalhe da atividade; U-13, C3-06, C3-05, C3-G2/F4, C3-G1, C3-03/04, C3-02 e C3-01A/B incluídos)

## Estado geral

- **Fase:** 1 — Coach (em andamento)
- **Build:** verde (`./gradlew check :app:assembleDebug`)
- **Testes:** 1.342 testes no total (663 em `:app`, 160 em `core:data`, 480 em `core:domain`, 39 em `core:model`), 0 falhas e 0 `@Ignore`.
- **Cobertura `core:domain`:** 97,52% de linhas (4.208/4.315), 81,27% de branches (3.059/3.764), 96,20% de instruções (29.263/30.420)
- **Parser FIT:** 93,01% de branches (572/615); gate próprio exige ≥ 90% de branches
- **Lint:** 0 erros; avisos não bloqueantes de versão, KTX, naming, ícone e dependências
- **Dados reais:** 4 FIT e 3 TCX sanitizados; GPX apenas sintético
- **Persistência:** Room 2.8.5, schema v16 (prescrição diária, execução/evidência de sessão, vínculo com atividade, revisões versionadas do plano, fingerprint/checkpoint de planejamento, evento de prova e origem da conclusão), migrações 1→2 até 15→16 testadas.
- **Prescrição diária:** `DailyPrescriptionEngine` é a autoridade do dia (`KEEP`/`REDUCE`/`DOWNGRADE`/`REST`/`NO_PLAN`), persistida e versionada em `daily_prescriptions`; `TodayViewModel` só coleta o estado e não chama mais o recomendador. A guarda `R-PRESC-04` rebaixa `QUALITY → EASY` para perfil `BEGINNER` apenas na sessão de hoje `PLANNED`; `DONE`/`CLOSED` preservam prescrição, execução e histórico.
- **Conclusão com evidência:** `DONE` só existe com `ACTIVITY`, `GUIDED` (contrato) ou `MANUAL`; `LEGACY_UNVERIFIED` preserva o passado sem reescrevê-lo. A UI vincula corrida ou registra “fiz sem registro”, e descanso/doença encerram a sessão como `CLOSED` com motivo.
- **Vínculo 1:1:** `session_executions` e índices únicos impedem duas atividades na mesma sessão ou uma atividade em duas sessões; a lixeira suspende a evidência, restaurar devolve e expurgar remove o vínculo.
- **Plano como intenção:** a semana gerada no meio da semana começa em hoje (N-02), a adesão ignora dias anteriores à criação e as sessões carregam zona/pace/FC/distância/RPE (N-01).
- **Replanejamento (R-01/R-02/R-03):** `PlanReplanner` puro no `core:domain` regenera somente o futuro da semana, preserva hoje/passado e sessões `DONE`/`CLOSED`, valida a semana mesclada (2 qualidades, um longão, espaçamento e disponibilidade) e não repõe carga perdida. A revisão é sugestão (`Suggest`) ou `NoChange`; R-02 persiste o aceite de forma versionada e transacional em `weekly_plans` + `weekly_plan_revisions`. R-03 expõe `EvaluateReplan`/`ApplyReplan` no app, sugere por dia com aceitar/recusar/suprimir, reavalia se versão/hash mudarem, mostra selo de versão e diff “o que mudou” e mantém a prescrição de hoje e as sessões protegidas intactas. C3-01B acrescenta T4 `PROFILE_CHANGED`, fingerprint/checkpoint e compare-and-apply do perfil dentro da transação.
- **Perfil e plano (C3-02):** salvar Perfil/onboarding ou Calibração avalia T4 e publica o resultado; o Perfil mostra sugestão com aceitar/recusar/suprimir, `NoChange` ou limite seguro. O resultado é consumido uma vez; o Hoje continua avaliando e exibindo a sugestão, e o reset limpa o resultado pendente.
- **Briefing do dia (C3-03/04):** `SessionBriefingEngine` puro em `core:domain` deriva foco, alvo, rationale/confiança e orientação de “como correr”/“se estiver difícil” somente da `DailyPrescription` persistida. Nenhum número/alvo/segmento é recalculado ou inventado; sem calibração vale o RPE existente. `DONE`/`CLOSED` não geram briefing retrospectivo. O Hoje ganha o CTA “Como correr hoje” no card do treino (sessão não concluída) e um sheet acessível com as seções na ordem de leitura, sem `R-`/`EV-` na superfície comum.
- **Evento de prova (C3-G1):** desenho técnico fechado em `docs/PLANO_TECNICO_CICLO_3_EVENTO.md`, sem código/migração. Define `RaceEvent`/`RaceDistance`/`RaceStatus` em `core:model`, `TodayDecision` discriminada, Room 15 (`race_events` vazia, `activeKey` único, FK `SET NULL`), projeção `PlanningGoal` sem backfill, taper/cutoff por data com `R-PLAN-10` LIMITED, exclusão de datas de evento da adesão e ordem C3-05.
- **Evento de prova (C3-05, concluído):** `RaceEvent`, `TodayDecision`, `PlanningGoalResolver` e Room 15 completos. O app resolve `GetTodayDecision` antes de renderizar: prova no dia vence sem calcular/persistir prescrição, `Competition` suprime treino/briefing e `Training` continua igual. O gerador/replanejador recebem `race`/`eventDates`, a adesão exclui datas de evento, a troca e a conclusão de treino são bloqueadas no dia, e a notificação usa copy de prova com deep link para o Hoje. Perfil, Hoje e Plano têm card/marcador do evento, ações explícitas e goldens dark/light/estreito.
- **F4 — hidratação (C3-G2, concluído):** gate documental fechado e implementado no mesmo dia ([ADR 0029](adr/0029-f4-hydration-nutrition-copy.md)): copy educativa estática de hidratação aprovada e exibida no sheet "Como correr hoje" (sem números, produto, eletrólito ou horário), alimentação vira encaminhamento a nutricionista, `R-FUEL-01/02` e evidências aceitas nos catálogos, e `EV-NATA-2017` corrige a citação `EV-NATA-2025` do PRD. Ramo de prova continua sem dicas (FR-404).
- **Auto-conclusão da prova (C3-06, concluído):** corrida importada na mesma data, `RUNNING`/`TREADMILL`, não vinculada, evento `PLANNED` e distância entre 90% e 110% conclui a prova automaticamente; múltiplas candidatas exigem escolha explícita. `completionSource` (`USER` × `AUTO_IMPORT`) audita a origem, o undo volta a `PLANNED` e grava a recusa para o mesmo arquivo, e a corrida continua uma atividade normal (sem `SessionExecution`). Room 16 e migração 15→16 preservam o histórico.
- **Referência da meta (C3-06, concluído):** `RaceTargetProjector` puro deriva pace médio, faixa ±3% e passagens por km de `RaceEvent.distance`/`targetTimeSec`; sem meta, nada é inventado. A referência aparece só nos cards de prova do Perfil/Hoje, nunca em prescrição, plano, briefing ou troca, e é rotulada “referência da meta, não prescrição nem garantia”.
- **Fonte de fontes:** `ActivitySummary.sources` expõe todos os formatos que contribuíram (ex.: `FIT · TCX · GPX`).
- **Importação real:** `ImportStaging`, `StagedImportProcessor`, `ImportWorker` (`@HiltWorker`), `ImportCoordinator` e `ShareIntentReader`. WorkManager 2.12.0 com `HiltWorkerFactory` (manifest sem `WorkManagerInitializer`).
- **UI de atividades:** `ActivitiesScreen`, `ActivitiesViewModel`, `ActivityFormatters` (`sourcesLabel`) e `ImportJobCard`.
- **Detalhe da atividade:** hero, métricas, gráfico pace × FC (Canvas), splits, rota offline e insights com `ruleId`/`evidenceId`/confiança.
- **Perfil e onboarding:** formulário em 3 passos (dados, objetivo/disponibilidade, rotina), validação, persistência e edição.
- **Calibração:** VDOT/LTHR/pace de limiar por prova ou TT ≤ 8 semanas, com tiers A/B/C e tela de resultado.
- **LTHR manual:** campo no perfil (100–230 bpm, acima da FC de repouso e abaixo da FC máxima vista) para quem já conhece o valor; o app não estima a partir de qualquer corrida.
- **Calibrar por uma corrida:** corridas de 25–35 min com FC sustentada podem pré-preencher a calibração; o botão só aparece quando o arquivo passa nos critérios e o salvamento exige confirmação explícita.
- **Zonas:** distribuição de tempo por zona de FC no detalhe, usando o LTHR calibrado.
- **Carga:** série diária TRIMP/rTSS, CTL/ATL/TSB, ACWR, monotonia e tela de Tendências com PMC; TRIMP em minutos e card explicando Base, Fadiga, Balanço, Carga, Aumento e Variedade. ACWR só existe com fonte fresca (≤14 dias) e ≥8 dias com carga em 28; ausência vira `INSUFFICIENT`/`STALE`, nunca permissão.
- **Check-in:** 4 perguntas (sono, fadiga, dor, estresse) + sinais de doença/lesão e calor autorreportado, persistidos por data, usados na prontidão/prescrição e resumidos em um histórico dos últimos 7 check-ins com Hooper.
- **CSV diário:** parser de sono/RHR/HRV/SpO2, persistência, importação pelo perfil e agregação de janelas 7/30/60 dias na prontidão.
- **Métricas diárias:** CSV e Health Connect são mesclados por campo, com precedência `MANUAL` > `Health Connect` > `CSV`; um sync não apaga mais o que só o CSV tinha (ADR 0016).
- **Health Connect:** leitura read-only de sono/RHR/HRV/SpO2, agregação por dia, permissões, sincronização incremental (primeira execução 30 dias, depois desde o último sucesso) e sync periódico diário via WorkManager.
- **Diagnóstico:** status do Health Connect, base local de métricas e confiança por componente na tela Hoje.
- **Histórico de prontidão:** últimos 14 dias em Tendências, reconstruídos com os dados disponíveis em cada data.
- **Recomendação diária:** guardrails (doença/vermelho → descanso, sono curto/âmbar → fácil, ACWR alto → sem aumento), fácil em Z2 com pace/FC quando calibrado e RPE quando não; calor informado desacelera o pace em 3% e reduz a confiança.
- **Plano semanal:** distribuição por disponibilidade, até 2 qualidades com progressão, longão 25–30%, taper por prova e deload a cada 4ª semana; com acesso a esteira, a qualidade inclui a alternativa por velocidade/inclinação.
- **Plano persistido:** a semana é gerada uma vez e salva em Room v7; check-ins não a reescrevem.
- **Taper e qualidade:** redução de 45–55% nas semanas de prova e qualidade nomeada por experiência (tempo/intervalos).
- **Progressão:** com semana anterior ≥80% concluída e guardrails verdes, libera uma segunda qualidade; volume segue limitado pela disponibilidade (ADR 0010).
- **Deload:** a 4ª semana consecutiva de plano reduz o volume em 25%, mantendo o tipo/intensidade das sessões e citando `R-PLAN-04`/`EV-ISSURIN-2010`; taper tem precedência (ADR 0011).
- **Conclusão do plano:** `PlanCompletionCalculator` resume as últimas 8 semanas (feitas, puladas e pendentes passadas) e a tela Tendências mostra a adesão semanal sem streak ou culpa.
- **Tendências de atividades:** volume semanal, consistência com heatmap, relação pace×FC com gráfico de eficiência (m/batimento), filtro de período (6/12/26 semanas) e recordes pessoais (maior distância, melhor pace ≥ 3 km e maior tempo) no `ActivityTrendsCalculator`.
- **80/20 por tempo:** `IntensityDistributionCalculator` soma tempo em Z1–Z2, Z3 e Z4–Z5 a partir dos samples, com quebra semanal, confiança por cobertura e card próprio em Tendências.
- **Ações do plano:** concluir com evidência (corrida vinculada ou registro manual) ou pular sessão com motivo, persistido por sessão; prévia de cronômetro que não substitui a evidência; trocar o conteúdo de duas sessões planejadas da semana com validação de guardrails.
- **Explicabilidade:** bottom sheet “Por que este treino?” com rationale, regras, evidências, confiança e dados usados/ausentes; o catálogo de rótulos mantém o `ruleId`/`evidenceId` bruto auditável.
- **Story de treino (P3-05, protótipo):** planejador de rota/splits/timeline, renderer Canvas 9:16 com mapa vetorial (`MapLibre` + `OpenFreeMap`, dados OSM) e frame final de 3 s (distância, tempo, ritmo médio, FC média, kcal e logo), export MP4 1080×1920 via MediaCodec, com galeria e compartilhamento; validado em aparelho real (mapa vetorial pendente de validação).
- **Mapa do story:** snapshot 1080×1920 gerado no preview e reaproveitado no vídeo, com estilo vetorial OpenFreeMap (`liberty`) filtrado em tempo de execução (POIs, transporte e aeroportos ocultos) e labels ampliados; fallback para raster OSM; câmera calculada localmente a partir do `BoundingBox`, halo de foco na rota, ticks por km, atribuição OSM no frame e permissões de localização do SDK removidas. `MapLibre` 12.3.1 (última linha OpenGL), ABIs `arm64-v8a`/`x86_64`; snapshot em 2,25× (2430×4320), vídeo 1080p a 10 Mbps e 30 fps.
- **Story v2 — Fase A:** área segura do Instagram, câmera com zoom/rotação alinhada à rota, grão/vinheta/grade de cor, 30 fps, snapshot supersampleado e capa PNG do frame final.
- **Story v2 — Fase B:** veredito determinístico por km e no encerramento (`StoryVerdictCalculator`), rota colorida por zona de FC, barra de splits e contador no card; sem plano/calibração/FC o story não inventa alvo.
- **Story em segundo plano (ADR 0018):** exportação via `WorkManager` (`StoryExportWorker`) com nome único por atividade, plano reconstruído dos repositórios e mapa da prévia reaproveitado em cache; cancelamento cooperativo no encoder (MP4 parcial apagado), progresso a cada 0,6 s com ETA suavizado, notificação de concluído/falha e estado restaurado ao reabrir a tela.
- **Perfil com nome e foto:** `AthleteProfile.name`/`avatarPath` (Room v10), nome obrigatório no onboarding, foto importada pelo seletor do Android e copiada para `filesDir/profile` (512 px); avatar no lugar do “M” na Home, no Perfil e como pin do story.
- **Story com a foto:** o pin do percurso é a foto do perfil (desenhada fora da câmera, sempre em pé, ~114 px de diâmetro em 1080p); a leitura do coach saiu da prévia e virou card privado na tela da atividade.
- **Story v2 — câmera:** flyover que segue o corredor (overview 1,18×/8° → follow 1,72×/32°), rotação de até 22° pela direção da rota, zoom extra no card e pullback no encerramento; homografia aplicada ao mapa **e** à rota, com espessura da rota compensada pela escala.
- **Backup e reset:** tela Dados exporta todo o conteúdo em JSON e apaga perfil, corridas, check-ins, métricas, planos, prescrições e execuções em uma transação, com confirmação.
- **Notificações locais:** lembrete de check-in às 18h e do treino às 7h, com deep link para Hoje/check-in e permissão no Android 13+; importação concluída (sucesso, duplicado ou falha) em canal próprio, sem som, com atalho para o treino.
- **Tela Hoje:** prontidão, treino recomendado, plano da semana, carga e atividade recente puxados do banco; sem nome/weather falsos e sem números de demonstração quando falta carga.
- **Prontidão legível sem cor:** níveis aparecem como **Alta/Moderada/Baixa** no gauge e no histórico, com a cor como reforço; a foto do cabeçalho abre o Perfil.
- **Share intent:** `ACTION_SEND`/`SEND_MULTIPLE` no manifest e no `MainActivity`; `ImportMessages` unifica o texto de fila.
- **Comunicação:** IDs de regra/evidência fora das telas comuns; fontes sob demanda em `Ver fontes e detalhes técnicos`; jargão traduzido em Hoje, Tendências, Atividade, Calibração, Check-in, Perfil e onboarding.
- **Saúde como destino principal:** a tela “Saúde e dados” virou a 5ª aba (`Hoje · Corridas · Tendências · Saúde · Perfil`), sem seta de voltar. O Hoje ganhou “Ver recuperação” no card de prontidão; o Perfil mantém só um link compacto com estado e o card de backup. Checklist e tutorial apontam para a aba (ADR-0020). A aba avisa quando os dados de hoje ainda não chegaram e oferece “Abrir Health Connect” para diagnóstico.
- **Sync automático do Health Connect:** o app sincroniza no resume quando a última leitura tem mais de 6 h, com backoff de 15 min em erro, `Mutex` contra concorrência e worker diário como fallback. `HealthSyncStore` guarda `lastSyncedAt`/`lastAttemptAt`; a UI mostra “Atualizado há X” e erro passivo; o botão manual continua sendo override (ADR-0021).
- **Tutorial de boas-vindas:** 3 cenas sobre o Hoje na primeira abertura com perfil criado: plano real da semana, como o coach lê esforço/descanso e o primeiro passo (check-in, importar corrida ou Health Connect). Revisível em Perfil → Ajuda; flag e versão em SharedPreferences (`meucoach_tutorial`), sem migração de Room. O checklist “Complete seu setup” continua no Hoje e desaparece ao concluir ou após “Agora não”.
- **Importação reativa:** ao terminar um import de corrida/esteira do próprio dia, um overlay global pergunta se a corrida deve virar o treino de hoje (vínculo `ACTIVITY`). Se o treino do dia já foi concluído, o overlay apenas avisa que a corrida fica como treino livre e não será usada pelo coach. A pendência é persistida em SharedPreferences (`meucoach_import_prompts`), sobrevive à morte do processo e é limpa no reset.
- **Treino de hoje concluído:** o card principal mostra o selo “Concluído”, o resumo do que foi feito (duração, distância, pace e RPE quando existem), mantém o planejado como contexto, esconde iniciar/trocar/concluir e oferece “Ver corrida” quando há vínculo. “Feito · sem registro” ganhou explicação no card e no plano: conta adesão, mas não entra em volume, carga ou zonas.
- **Comparação planejado × executado (FR-010):** `SessionComparisonCalculator` puro compara a corrida com a prescrição do dia (fallback para a sessão da semana) apenas quando ela é a evidência do treino. O detalhe mostra “planejado → feito” por duração, distância, pace, FC e RPE, notas factuais de diferença, confiança e “treino livre” sem alvo; dado ausente fica ausente. Nada é persistido e nenhum ID técnico aparece na tela.

## Sessão 2026-11-11 — FR-010: comparação planejado × executado

**Objetivo:** fechar o “aprender” do loop mostrando, no detalhe da atividade, o alvo que o atleta recebeu no dia e o que a corrida entregou — sem criar uma segunda decisão de treino, sem migração e sem inventar alvo para treino livre.

### Concluído

- **Domínio:** `SessionComparison`/`ComparisonPlan`/`SessionComparisonCalculator` em `core:domain/feedback`. Compara duração, distância, pace, FC média e RPE; cada métrica só existe com os dois lados, com `WITHIN`/`ABOVE`/`BELOW` como direção. Tolerâncias de 10% (duração/distância) e 1 ponto de RPE. Confiança alta com ≥3 métricas comparadas, média com 2, baixa com 0–1; treino livre é sempre baixa.
- **Fonte do alvo:** `DailyPrescription` da data (o que o atleta viu), com a sessão da semana como fallback para dia vinculado sem prescrição. Prescrição `REST` não gera comparação.
- **Vínculo obrigatório:** `GetSessionComparison` só compara quando a atividade é a evidência do treino (`SessionExecution` ou `planned_session.activityId`); sem vínculo, a corrida é `FREE_RUN` e o card não inventa alvo.
- **App:** caso de uso só coleta estado; `ActivityDetailViewModel` transporta `comparison` e a UI não calcula.
- **UI:** card “Planejado × executado” após as métricas, com selo de confiança, linhas `planejado → feito` (números tabulares), notas de diferença (“Ritmo mais rápido que o alvo”, “FC média acima da faixa-alvo”), “Tudo dentro do planejado.”, estado “Sem alvos suficientes para comparar.” e “Treino livre / Sem plano vinculado a esta corrida.”.
- **Catálogo:** `R-FEEDBACK-01` (política de produto/copy) em `SPORTS_SCIENCE.md` §14 e `ExplanationCatalog`; `EV-DANIELS-2014`/`EV-FRIEL-2018` como origem das faixas. Nenhuma regra fisiológica nova.
- **Estabilização de teste:** `captureStableScreenshot` espera dois quadros idênticos antes de gravar o golden e foi aplicado a `HealthMetricsScreenshotTest` e `TrendsFullScreenshotTest`; o flake de gráficos Vico sob carga deixou de falhar a suíte, sem regravar nenhum golden existente.
- **Testes:** +13 no domínio (`SessionComparisonCalculatorTest`), +6 em `:app` (`GetSessionComparisonTest`), +7 em `ActivityComparisonFormatTest`, +2 em `ActivityDetailScreenTest` e +2 no ViewModel; 3 goldens novos.

### Evidências

- `./gradlew check :app:assembleDebug` — verde (testes, `spotlessCheck`, lint, Kover e gate FIT).
- `./gradlew :app:verifyRoborazziDebug` — verde (60 goldens).
- **1.342 testes**, 0 falhas e 0 `@Ignore` (663 `:app`, 160 `core:data`, 480 `core:domain`, 39 `core:model`).
- `core:domain`: 97,52% de linhas (4.208/4.315), 81,27% de branches (3.059/3.764), 96,20% de instruções (29.263/30.420); parser FIT 93,01% de branches.
- Nenhuma migração, dependência nova ou dado real no diff. Schema permanece v16.

### Decisões e observações

- **Alvo prescrito, não intenção:** comparar com a sessão original puniria o atleta por uma redução/`DOWNGRADE` que o próprio app aplicou (ADR 0023).
- **Descritivo por escolha:** o card mostra direção e diferença, nunca “bom/ruim”, erro ou próximo treino; a decisão do dia seguinte continua no motor.
- **Vínculo é a chave:** uma corrida no mesmo dia do plano não vira comparação se não for a evidência do treino; é treino livre.
- **Sem persistência:** a comparação é derivada como o briefing; reset/export não mudam.
- **ADR 0033 documenta** as tolerâncias como convenção de produto (não consenso científico) e os limites (média de sessão, não splits).

### Próximo passo

1. Validar o card em aparelho real com TalkBack, fonte 200% e tela estreita (360 dp).
2. Usar a base da FR-010 para a revisão semanal (FR-011), que segue pendente.
3. Comparação por splits e “próximo passo” interpretado continuam fora até terem regra/evidência.

## Sessão 2026-11-11 — U-13: campos de data e duração no padrão da §7.3

**Objetivo:** fechar a pendência U-13 do backlog de apresentação: uma única máscara de data em `ui/common`, tempo alvo do onboarding no seletor de unidade e data/tempo da Calibração no mesmo padrão, sem tocar em domínio, schema, motor ou dependências.

### Concluído

- **Máscara única (`ui/common/DateMask.kt`):** `DateMask` reúne `digits`, `format`, `parse` (aceita `ddMMuuuu` e `dd/MM/uuuu`), `toDigits` e o mapeamento de cursor; `DateVisualTransformation` é a única transformação visual usada por onboarding, card de prova e Calibração. A implementação duplicada em `ui/onboarding/DateMask.kt` e `RaceDateVisualTransformation` em `RaceEventLabels.kt` foram removidas.
- **Campo de tempo compartilhado (`ui/common/TargetTimeField.kt`):** chips `Minutos`/`Horas`, máscara vigente (`mm:ss` × `h:mm:ss`) e segmentos com tags próprias; o card de prova passou a reutilizá-lo (mesmas tags `race-time-*`) e `targetTimeUnitFor` centraliza a unidade inicial.
- **Onboarding:** `targetTime: String` virou `TargetTimeParts` + `TargetTimeUnit`; o tempo é convertido ao trocar a unidade, `0:00` é rejeitado e a mensagem de erro informa o limite (0 a 59) da unidade escolhida. `parseDuration`/`formatDuration` locais foram removidos.
- **Calibração:** `time` virou segmentos com unidade; a data guarda dígitos crus e usa a máscara comum; `prefillForm` passou a derivar a unidade do tempo da corrida e a gravar a data em dígitos.
- **Testes:** +7 em `DateMaskTest` (formatação, dígitos, parse, `toDigits`, mapeamento de cursor, transformação e colagem com barras), +2 em `OnboardingScreenTest` (edição no meio sem reordenar e 200% em 360 dp), +2 em `CalibrationScreenTest` (digitação de tempo/data e 200% em 360 dp), +1 golden novo `onboarding-goal-dark.png`, goldens `calibration-dark/light` regravados. Nenhum assert existente foi reduzido.
- **Design system:** §7.3 atualizada (versão 1.3) com o inventário todo “Conforme” e o checklist de migração marcado; `ROADMAP` U-13 concluído.

### Evidências

- `./gradlew check :app:assembleDebug` — verde (testes, `spotlessCheck`, lint, Kover e gate FIT).
- `./gradlew :app:verifyRoborazziDebug` — verde.
- **1.309 testes**, 0 falhas e 0 `@Ignore` (643 `:app`, 160 `core:data`, 467 `core:domain`, 39 `core:model`).
- `core:domain`: 97,62% de linhas (4.065/4.164), 81,47% de branches (2.951/3.622), 96,25% de instruções (28.451/29.558); parser FIT 93,01% de branches.
- Nenhuma migração, dependência nova ou dado real no diff. Build executado com JDK 17 via mise (`JAVA_HOME`), pois o ambiente não tem JDK 17 no caminho padrão do Gradle.

### Decisões e observações

- **Estado guarda o dado:** data continua como dígitos crus e tempo como segmentos; barras e dois-pontos existem só na camada visual. `parse` aceita valor formatado apenas para leitura de testes/legado, nunca como formato persistido.
- **Opcional ≠ zero:** tempo vazio continua `null`; `0:00` agora é inválido no onboarding, alinhado à §7.3.
- **Reuso sem mudança de comportamento:** o card de prova mantém as mesmas tags, conversão e mensagens; a extração só removeu a duplicação.
- **Escopo respeitado:** domínio, plano, prescrição, calibração (cálculo) e prova não foram alterados; nenhuma decisão científica ou de produto mudou.

### Próximo passo

1. Validar no aparelho o teclado numérico, a edição no meio, a troca de unidade e a fonte 200% em onboarding e Calibração.
2. Escolher a próxima entrega: FR-011 (revisão semanal), ADR de D6-B (VDOT/Riegel) ou estabilização da suíte/goldens sob carga.

## Sessão 2026-11-11 — C3-06: auto-conclusão da prova e referência da meta

**Objetivo:** fechar o ciclo da prova quando a corrida importada é compatível e permitir que a meta declarada gere uma referência aritmética (pace, faixa e passagens), sem contaminar o motor de treino. As decisões D1–D6 foram aprovadas pelo usuário 0 e registradas no [ADR 0032](adr/0032-race-autocompletion-and-target-projection.md).

### Concluído

- **Auto-conclusão:** `RaceAutoCompletionRule` puro no domínio (mesma data, `RUNNING`/`TREADMILL`, não vinculada, `PLANNED`, 90–110%, uma única candidata, recusa anterior) e `AutoCompleteRaceFromActivity` no app. `StagedImportProcessor` chama a auto-conclusão para `Imported`/`Duplicate` antes do prompt de treino; falha nunca derruba a importação.
- **Auditoria e undo:** `RaceCompletionSource` (`USER`/`AUTO_IMPORT`) em `RaceEvent`, Room 16 e migração `15→16` (coluna nullable; legado `NULL` = conclusão explícita anterior). `reopenAutoCompleted` volta a `PLANNED`, desvincula e limpa a origem; `RaceAutoCompletionStore` grava a recusa por `activityId` para o mesmo arquivo não reconcluir. Reset limpa a recusa.
- **Sem treino concorrente:** auto-conclusão não cria `SessionExecution`; a corrida continua em volume/carga/zonas e o evento não adiciona carga. `CompleteSession`/`SessionExecutionRepository` recusam atividade já vinculada a uma prova.
- **Referência da meta:** `RaceTargetProjection`/`RaceTargetProjector` puros; pace médio arredondado, faixa ±3% e passagens acumuladas (chegada exata), `null` sem meta. Exibida em `RaceTargetProjectionBlock` no card do Perfil e no card de prova do Hoje, com sheet “Ver passagens por km”, números tabulares e nota “não é prescrição nem garantia”.
- **Catálogo:** `R-RACE-01` (auto-conclusão, LIMITED) e `R-RACE-02` (referência aritmética, LIMITED) no catálogo; `R-RACE-03` (VDOT/Riegel) documentada como fora desta fase.
- **Testes:** +27 no domínio (projeção, regra e independência do plano), +8 em `core:data` (migração 15→16, `completionSource`, undo, vínculo cruzado e export) e +21 em `:app` (caso de uso, importação, reset, UI e goldens).

### Evidências

- `./gradlew check :app:assembleDebug` — verde (testes, `spotlessCheck`, lint, Kover e gate FIT).
- `./gradlew :app:verifyRoborazziDebug` — verde.
- **1.301 testes**, 0 falhas e 0 `@Ignore` (635 `:app`, 160 `core:data`, 467 `core:domain`, 39 `core:model`).
- `core:domain`: 97,62% de linhas (4.065/4.164), 81,47% de branches (2.951/3.622), 96,25% de instruções (28.451/29.558); parser FIT 93,01% de branches.
- Goldens atualizados: `profile-race-{dark,light}` e `today-race-{dark,light,narrow-dark}`; nenhum dado real, dependência nova ou LLM.

### Decisões e observações

- **Inferência conservadora:** a regra exige data local exata, esporte de corrida, vínculo único, `PLANNED` e uma única candidata na banda; qualquer ambiguidade cai no fluxo explícito já existente.
- **Undo com memória:** o `activityId` recusado não volta a auto-concluir; o fluxo explícito “Concluir prova” continua disponível.
- **Auditoria sem reescrever o passado:** a migração adiciona uma coluna nullable; conclusões anteriores ficam `NULL` e são lidas como explícitas.
- **Referência não é prescrição:** a projeção é pura, não persistida e só consumida pelos cards da prova. `RacePlan.from(event)` ignora `targetTimeSec` e há teste de independência do plano.
- **F4 preservado:** o ramo de prova continua sem briefing/F4 (FR-404); nenhuma copy de hidratação/alimentação foi alterada.

### Próximo passo

1. Validar em aparelho real o fluxo de auto-conclusão, o undo e o sheet de passagens com TalkBack, 200% e tela estreita.
2. Avaliar D6-B (previsão VDOT/Riegel) em ADR próprio, com evidência, janela de calibração e confiança explícita.
3. Manter FR-206 (longão), FR-207 (recuperação pós-prova) e a revisão de copy de F4 fora deste ciclo.

## Sessão 2026-11-10 — F4 na UI: hidratação e alimentação no briefing

**Objetivo:** implementar o conteúdo aprovado na ADR 0029 no sheet "Como correr hoje", com copy estática, testes de regressão e goldens.

### Concluído

- **`BriefingFuelContent`:** copy do F4 como constantes estáticas no app (`ui/today`), fora do motor e de `SessionBriefing`; nada deriva da prescrição nem altera confiança. `const` com textos exatamente iguais ao aprovado em `COMMUNICATION.md` §6.10.
- **Sheet:** novas seções `HIDRATAÇÃO` (Antes de correr / Durante / Depois + nota fixa de variação individual) e `ALIMENTAÇÃO` (encaminhamento a nutricionista), após "SE ESTIVER DIFÍCIL" e antes do botão Fechar; títulos com semântica de heading e reuso de `BriefingSection`. Só aparece em `Ready` (prescrição vigente, sessão não concluída); o ramo de prova não renderiza o sheet (FR-404).
- **Testes de regressão:** `BriefingFuelContentTest` trava a copy aprovada e garante ausência de dígitos, unidades (ml/kcal/macro/%), "eletrólito", `R-`/`EV-`; `TodayBriefingTest` ganhou teste de ordem (difícil < hidratação < alimentação < Fechar), exibição dos textos e nota rolagável; o teste de fonte 200% em tela estreita agora verifica a seção de alimentação.
- **Goldens:** `today-briefing-{dark,light}.png` regravados com as novas seções; nenhum outro golden mudou.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug` — verde (testes, `spotlessCheck`, lint, Kover e gate FIT 93,01%).
- **1.234 testes**, 0 falhas e 0 `@Ignore` (606 `:app`, 152 `core:data`, 440 `core:domain`, 36 `core:model`).

### Decisões e observações

- **Copy fora do motor:** o conteúdo educativo não entra em `core:domain` nem em `SessionBriefing`; é conteúdo de produto estático auditado pela ADR 0029, não uma decisão do coach.
- **Nota fixa sempre visível** dentro da seção de hidratação, evitando a leitura de "beba quando sentir sede" como instrução universal.

### Próximo passo

1. Coletar feedback do usuário 0 sobre a copy exibida no aparelho (validação real do F4 e do fluxo de prova já feito).

## Sessão 2026-11-10 — C3-G2: gate de fontes e copy do F4

**Objetivo:** revisar as fontes do F4 (hidratação/alimentação), aprovar copy educativa e registrar o gate na ADR 0029, sem código.

### Concluído

- **Revisão das fontes:** ACSM 2007 (Sawka), NATA (statement vigente é o de **2017**, McDermott — o PDF citado no PRD como `EV-NATA-2025` era o statement de 2000, Casa, já substituído) e Thomas 2016. Consenso: toda recomendação quantitativa exige contexto individual (suor, massa, clima, duração); nenhuma fonte sustenta conselho universal de quantidade/produto/eletrólito.
- **ADR 0029 aceito:** F4 liberado apenas como copy educativa estática (não sai do motor, não altera prescrição/confiança), com nota fixa de variação individual; alimentação vira encaminhamento a nutricionista, nunca dica; ramo de prova sem F4 na v1 (FR-404).
- **Copy aprovada** em `COMMUNICATION.md` §6.10: "Antes de correr", "Durante", "Depois" e nota fixa — sem ml, g, kcal, macros, dose, % de peso, horário, produto ou eletrólito.
- **Catálogos atualizados:** `R-FUEL-01/02` (STRONG como política de produto) e `EV-ACSM-2007`/`EV-NATA-2017`/`EV-THOMAS-2016` (tier A) em `SPORTS_SCIENCE.md` §13/§14; `R-SAFE-03` cita a única exceção.
- **Documentos coordenados:** `PRODUCT.md` §4, `AGENTS.md` §5/§12, PRD v0.2 (§4.4, §6.1, §6.2, plano de execução), `DECISIONS.md` 1.8 e `ROADMAP.md` 1.18.

### Evidências

- Gate documental: sem mudança de código; nenhum build/teste novo necessário.
- Referências verificadas por leitura integral dos PDFs em 10/11/2026 (ACSM 2007 e NATA 2000/2017); citação corrigida antes de entrar no catálogo.

### Decisões e observações

- **"2025" da NATA era o caminho de upload do PDF**, não o ano do documento; o catálogo usa `EV-NATA-2017` com o link do statement vigente.
- Thomas 2016 entra no catálogo sustentando **apenas a recusa** de prescrição alimentar; nenhuma dica deriva dela.

### Próximo passo

1. Implementar F4 (seções no sheet de briefing + teste de regressão de "nenhum número/ID nas seções F4") em ciclo futuro, após priorização com o usuário 0.
2. Validação do fluxo de prova em aparelho real (C3-05) e revisão de cópia continuam abertos.

## Sessão 2026-11-10 — C3-05 (parte 3): wiring do app, notificação e UI do evento

**Objetivo:** consumir o evento no app sem criar uma segunda decisão de treino e fechar a superfície de Perfil/Hoje/Plano.

### Concluído

- **`GetTodayDecision`:** lê o evento do dia e devolve `Competition` sem chamar `GetTodayPrescription`; sem evento, devolve `Training` com a prescrição persistida e, sem perfil/evento, `null`. `TodayViewModel` passou a consumir essa decisão e mantém `prescription`/`briefing` nulos no ramo de competição.
- **Plano no app:** `WeeklyPlanInput`/`ReplanInput` recebem `race` (evento `PLANNED`) e todas as datas de evento. Depois do primeiro evento, o fallback da meta legada deixa de planejar; nenhuma sessão é criada no dia da prova.
- **Adesão:** `TrendsViewModel` passa as datas de evento da janela de 8 semanas ao `PlanCompletionCalculator`; sessões legadas sobrepostas saem do numerador/denominador.
- **Conclusão e troca:** `CompleteSession` rejeita concluir sessão legada em data de evento; `TodayViewModel`/`SessionSwapper` recebem `raceDates` e a UI de troca não oferece a data da prova.
- **Notificação:** `ReminderWorker` lê o evento do dia, usa copy de prova (`Prova hoje: 10 km`), `DEEP_LINK_TODAY` e não repete o lembrete de treino; o Hoje revalida a decisão ao abrir.
- **Perfil:** card “Prova” com as quatro distâncias, data, meta declarada opcional, estado, criar/editar, concluir com corrida opcional, cancelar e não compareceu; histórico de eventos encerrados; nenhum pace/faixa/previsão derivado da meta.
- **Hoje:** ramo `Competition` mostra distância/data/estado/meta e ações do evento, e esconde treino, briefing, iniciar/trocar/concluir e sugestões de treino.
- **Plano da semana:** dia de evento aparece como “Prova · {distância}” e a sessão legada sobreposta é ocultada; nenhuma ação de pular é oferecida no dia.
- **Testes:** `GetTodayDecision`, ViewModel do Hoje (prova vence, geração sem sessão no dia, troca bloqueada), conclusão em data de evento, adesão com evento, notificação, UI do Perfil/Hoje, parsers de data/tempo e goldens.
- **Acessibilidade:** alvos ≥ 48 dp nos chips/ações, textos com unidade/contexto, testes em tema claro/escuro e tela estreita.
- **Padrão de entrada:** `DESIGN_SYSTEM.md` §7.3 normatiza máscara visual de data e seletor de unidade para duração; inventário e migração de onboarding/Calibração em `ROADMAP` U-13.

### Evidências

- `./gradlew check :app:assembleDebug` — verde (testes, `spotlessCheck`, lint, Kover e gate FIT).
- `./gradlew :app:verifyRoborazziDebug` — verde.
- **1.239 testes**, 0 falhas e 0 `@Ignore` (611 `:app`, 152 `core:data`, 440 `core:domain`, 36 `core:model`).
- `core:domain`: 97,63% de linhas, 81,85% de branches, 96,21% de instruções; parser FIT 93,01% de branches.
- Goldens novos: `today-race-{dark,light,narrow-dark}.png` e `profile-race-{dark,light}.png`; Perfil dark/light e saúde atualizados pela nova seção.

### Decisões e observações

- **Uma decisão só:** a UI recebe `TodayDecision` pronto; o card de prova substitui o card de treino em vez de coexistir com ele.
- **Evento encerrado ainda vence o dia:** `CANCELLED`/`NO_SHOW` continuam `Competition`, sem recuperação automática nem liberação de treino.
- **Meta declarada:** `targetTimeSec` aparece como “meta informada” e nunca vira pace, faixa ou previsão.
- **Legado protegido:** plano antigo com sessão no dia da prova não é reescrito; a sessão é ocultada, excluída da adesão e não pode ser concluída/trocada.
- **Primeiro evento encerra a fonte legada:** depois de qualquer evento persistido, `TrainingGoal` não volta a alimentar taper/plano; não há backfill.
- **Máscara de data do card de prova:** o campo guarda `TextFieldValue` com dígitos crus e usa `VisualTransformation` para exibir `dd/mm/aaaa`; editar no meio do texto não reordena os dígitos e o cursor acompanha as barras.
- **Tempo alvo do card de prova:** o usuário escolhe `Minutos` (`mm:ss`, aceitando mais de 59 minutos) ou `Horas` (`h:mm:ss`) e digita apenas números em campos separados; a troca de unidade converte o valor atual e a validação rejeita minutos/segundos acima de 59.

### Próximo passo

1. C3-G2 (revisão de fontes/copy de F4) permanece fora deste ciclo.
2. Validar o fluxo de prova em aparelho real e revisar cópia com o usuário 0, se desejado.

## Sessão 2026-11-10 — C3-05 (parte 2): taper por data, adesão e troca

**Objetivo:** aplicar as regras aprovadas do evento no domínio, mantendo o app atual intacto até a etapa de UI.

### Concluído

- **`RacePlan`:** janela de taper 14/21 dias e cutoff de qualidade 10/14 dias por distância; semana civil da prova sem `QUALITY`; cutoff inclusivo; prova passada não bloqueia mais; fator profundo nos últimos 7 dias.
- **`WeeklyPlanGenerator`:** usa `race`/`eventDates` (fallback para a meta legada), remove sessões na data de evento e depois dela na mesma semana, não cria `QUALITY` na semana/cutoff, aplica taper por data da sessão (inclusive `QUALITY` antes do cutoff) e cita `R-PLAN-05`/`R-PLAN-10`.
- **`PlanReplanner`:** recebe `race`/`eventDates`, descarta candidatos em datas de evento, não conta sessão passada em data de evento como perdida, valida ausência de `QUALITY` no cutoff e inclui corrida/datas no `inputsHash`.
- **Adesão:** `PlanCompletionCalculator` exclui datas de evento do numerador e denominador, inclusive sessões `DONE` legadas.
- **Troca:** `SessionSwapper` rejeita qualquer troca que envolva data de evento.
- **Testes:** +6 `RacePlanTest`, +5 `WeeklyPlanGeneratorTest`, +4 `PlanReplannerTest`, +2 `PlanCompletionCalculatorTest` e +1 `SessionSwapperTest`.

### Evidências

- `./gradlew check :app:assembleDebug` — verde (testes, `spotlessCheck`, lint, Kover e gate FIT).
- **1.200 testes**, 0 falhas e 0 `@Ignore` (573 `:app`, 152 `core:data`, 439 `core:domain`, 36 `core:model`).
- `core:domain`: 97,65% de linhas, 81,84% de branches, 96,24% de instruções; parser FIT 93,01% de branches.
- Nenhuma dependência nova, nenhum golden alterado e nenhum dado real no diff.

### Decisões e observações

- **Taper por data da sessão:** a mesma semana pode ter dias dentro e fora da janela; o fator é calculado por `targetDate - session.date`, não por `weekStart`.
- **Sem treino depois da prova na mesma semana:** recuperação pós-prova continua fora do escopo; o app não inventa sessão de recuperação nem repõe o dia.
- **Fallback legado:** `RacePlan.from(profile.goal)` preserva o comportamento atual até o app passar o evento persistido.
- **Ainda não visível:** nenhuma tela consome `race`/`eventDates`; o Hoje, a notificação e a UI de prova seguem pendentes.

### Próximo passo

1. Wiring do app: `GetTodayDecision`, `ReplanContextBuilder`/`TodayViewModel` com `RaceEventRepository`, adesão e troca cientes de eventos.
2. Notificação e UI Perfil/Hoje/Plano + goldens.

## Sessão 2026-11-10 — C3-05 (parte 1): modelo, TodayDecision e Room 15

**Objetivo:** iniciar a implementação do evento de prova pelo núcleo puro e pela persistência, mantendo o comportamento atual das telas inalterado.

### Concluído

- **`core:model`:** `RaceDistance` (4 distâncias), `RaceStatus` e `RaceEvent` com invariantes de estado, `resolvedAt`, vínculo e meta declarada. `PLANNED` continua válido no passado.
- **`core:domain`:** `TodayDecision.Training`/`Competition` e `TodayDecisionResolver` (evento vence, `CANCELLED`/`NO_SHOW` não iniciam recuperação, data divergente é rejeitada); `PlanningGoalResolver` com evento `PLANNED` tendo precedência, sem backfill e com projeção equivalente à meta legada.
- **Room 15:** entidade `RaceEventEntity`, DAO e `RaceEventRepository` com criação/edição/conclusão/cancelamento/não comparecimento, um único `PLANNED` via `activeKey`, vínculo único com atividade, bloqueio cruzado com `session_executions` e lote por data.
- **Migração `14→15`:** cria `race_events` vazia, índices e FK `SET NULL`; planos, sessões, revisões, prescrições, execuções e atividades permanecem intactos. Nenhum evento é inferido de `TrainingGoal`.
- **Export/reset:** `raceEvents` sai no JSON e entra em `UserDataDeletion`; reset remove eventos antes das atividades.
- **Testes:** +9 em `core:model`, +13 em `core:domain` e +10 em `core:data` (migração, repositório, lixeira/expurgo e export/reset).

### Evidências

- `./gradlew check :app:assembleDebug` — verde (testes, `spotlessCheck`, lint, Kover e gate FIT).
- **1.181 testes**, 0 falhas e 0 `@Ignore` (573 `:app`, 152 `core:data`, 420 `core:domain`, 36 `core:model`).
- `core:domain`: 97,69% de linhas, 81,80% de branches, 96,24% de instruções; parser FIT 93,01% de branches.
- Schema `15.json` exportado; migração `14→15` validada com atividade legada preservada e `race_events` vazia.
- Nenhuma dependência nova e nenhum dado real no diff.

### Decisões e observações

- **Sem backfill:** a migração não materializa evento a partir da meta; o fallback legado continua até o usuário criar a primeira prova.
- **Persistência antes da UI:** a tabela/repositório entram primeiro para que C3-05 possa evoluir gerador, adesão e telas sem uma segunda migração.
- **Telas inalteradas:** `TodayDecision`/`RaceEvent` ainda não são consumidos pelo Hoje; nenhum golden mudou.
- **Escopo respeitado:** taper/cutoff, replanejamento, regra de adesão, notificação e UI de prova permanecem pendentes.

### Próximo passo

1. Aplicar taper/cutoff por data e exclusão de eventos em gerador/replanejador/adesão/swapper.
2. Depois, casos de uso, notificação e UI de Perfil/Hoje/Plano com goldens.

## Sessão 2026-11-10 — C3-G1: desenho técnico do evento de prova

**Objetivo:** fechar os detalhes técnicos pendentes do ADR 0030 antes de qualquer código, sem implementar `RaceEvent`/migração ou alterar o comportamento atual.

### Concluído

- **Plano técnico:** criado `docs/PLANO_TECNICO_CICLO_3_EVENTO.md` com contrato de domínio, `TodayDecision`, schema Room 15, migração, vínculo com atividade, taper/cutoff, adesão, notificação, export/reset, matriz de testes e ordem do C3-05.
- **Decisões fechadas:** `RaceEvent` em `core:model`; `TodayDecision` em `core:domain`; nenhum backfill de `TrainingGoal`; `race_events` com `activeKey` único e `activityId` único/FK `SET NULL`; lixeira suspende e restaurar devolve o vínculo; adesão exclui datas de evento; taper por `targetDate - session.date`; cutoff 10/14 como `R-PLAN-10` LIMITED.
- **Transição de fonte:** evento `PLANNED` tem precedência sobre a meta legada; depois do primeiro evento, `TrainingGoal` não volta a planejar. `targetTimeSec` fica fora do fingerprint.
- **ADR 0030:** status atualizado para “desenho técnico C3-G1 concluído; implementação pendente” e §6 reconciliada com o plano.
- **Roadmap/arquitetura:** C3-G1 marcado como concluído (documentação) e C3-05 como próximo, aguardando execução.

### Evidências

- Nenhum arquivo de código, schema, migração, dependência ou golden foi alterado nesta sessão.
- Nenhum dado real no diff; working tree apenas documental.
- `./gradlew check :app:assembleDebug` permanece verde do commit anterior (`df4cdb6`).

### Decisões e observações

- **Sem backfill é a decisão conservadora:** `TrainingGoal` é objetivo declarado, não prova; materializar evento na migração poderia criar histórico e T4 falsos. O fallback legado cobre quem ainda não criou evento.
- **Evento no dia sempre vence:** inclusive `CANCELLED`/`NO_SHOW`; nenhum deles inicia recuperação ou libera treino automático, preservando a autoridade única.
- **Cutoff não é consenso:** `R-PLAN-10` será LIMITED e a copy não pode apresentar 10/14 como resultado direto de Bosquet/Mujika.
- **Escopo respeitado:** nenhuma linha de C3-05, F4 ou recuperação pós-prova foi implementada.

### Próximo passo

1. Executar C3-05 na ordem do plano técnico: model/domínio → Room 15 → casos de uso → gerador/replanejamento/notificação → UI/goldens.
2. Manter F4 (C3-G2) bloqueado até revisão de fontes/copy.

## Sessão 2026-11-10 — C3-03/04: briefing genérico e sheet no Hoje

**Objetivo:** transformar a prescrição persistida em um briefing determinístico e legível no Hoje, sem criar uma segunda decisão nem recalcular números com o perfil atual.

### Concluído

- **Domínio:** `SessionBriefing` imutável e `SessionBriefingEngine` puro em `core:domain/briefing`. O contrato copia título, tipo, duração, alvos, rationale, confiança e IDs da `DailyPrescription`; `briefingEngineVersion` (`session-briefing-v1`) identifica o template.
- **Fidelidade:** nenhum alvo é derivado no briefing; pace/FC/distância/zona/RPE são os persistidos. Sem calibração, o texto usa o RPE existente e não calcula pace/FC.
- **Copy genérica:** “Como correr” descreve esforço fácil/forte e repete apenas os alvos existentes; “Se estiver difícil” reduz/interrompe, não propõe treino alternativo e cita `R-SAFE-01` no contrato de auditoria.
- **Sem segmentos inventados:** a v1 não cria aquecimento, intervalos, séries, blocos ou alternativas; testes varrem a copy contra esses termos.
- **Histórico protegido:** `DONE`/`CLOSED` retornam `null`; o card continua mostrando `SessionExecution`, atividade vinculada ou motivo de encerramento persistidos. O gate status-aware do C3-01A permanece no `GetTodayPrescription`.
- **Hoje:** `TodayUiState` carrega `briefing` derivado da prescrição vigente em `todayState`. O card do treino ganha o CTA “Como correr hoje” apenas quando a sessão não está concluída; “Ver explicação” continua secundário.
- **Sheet:** reutiliza `ModalBottomSheet` e componentes/tokens do `core:designsystem` com as seções Foco → Alvo → Por que hoje → Como correr → Se estiver difícil, cabeçalho de confiança e fechar. Sem `R-`/`EV-` na superfície; estados loading, vazio e erro cobertos.
- **Acessibilidade:** headings semânticos, ordem de leitura verificada na árvore de semântica, alvos ≥ 48 dp, tema claro/escuro, tela estreita e fonte 200%.
- **Notificação:** permanece abrindo o Hoje (`meucoach://today`); ao abrir, o fluxo do Hoje revalida a prescrição vigente via `GetTodayPrescription` antes de oferecer o briefing.

### Evidências

- `./gradlew check :app:assembleDebug` — verde (testes, `spotlessCheck`, lint, Kover e gate do parser FIT).
- `./gradlew :app:verifyRoborazziDebug` — verde; goldens do Hoje atualizados pelo novo CTA e novos goldens `today-briefing-dark/light`.
- **1.149 testes**, 0 falhas e 0 `@Ignore` (573 `:app`, 142 `core:data`, 407 `core:domain`, 27 `core:model`).
- `core:domain`: 97,70% de linhas, 81,74% de branches, 96,23% de instruções; parser FIT 93,01% de branches.
- Nenhuma migração, dependência nova ou dado real no diff.

### Decisões e observações

- **Briefing não decide:** é projeção read-only da prescrição; não lê perfil, clima ou carga e não pode produzir alvo diferente do persistido.
- **`R-SAFE-01` no contrato:** a seção “Se estiver difícil” usa a copy conservadora já aprovada e mantém o ID apenas na auditoria, nunca no sheet comum.
- **Sem persistência de texto:** como o PRD define, a versão do engine serve a determinismo/testes; não há reconstrução histórica.
- **Escopo respeitado:** evento de prova/F2, F4, recuperação pós-prova, segmentos estruturados, LLM e áudio continuam fora.

### Próximo passo

1. Validar o sheet em aparelho real com TalkBack, fonte 200%, claro/escuro e tela estreita (checklist manual do PRD §11.4).
2. Seguir para C3-G1/C3-05 somente após autorização e desenho técnico do evento de prova.

## Sessão 2026-11-10 — C3-02: avaliação ao salvar Perfil/Calibração

**Objetivo:** fechar o ciclo entre edição de perfil/calibração e o plano: avaliar T4 depois de toda escrita relevante e mostrar sugestão, `NoChange` ou limite seguro no Perfil, mantendo o Hoje como superfície da sugestão.

### Concluído

- **Rotas de escrita cobertas:** `OnboardingViewModel` (onboarding e edição de perfil) e `CalibrationViewModel` chamam `EvaluateReplan` após `saveProfile`. Falha na avaliação não transforma um salvamento válido em erro; onboarding sem plano apenas limpa o resultado.
- **Resultado compartilhado:** `ProfilePlanEvaluationStore` guarda em memória o resultado da avaliação disparada pelo salvamento. O `ReplanViewModel` o consome uma vez no Perfil, com validação de versão para descartar sugestão obsoleta, e cai numa avaliação automática quando não há resultado pendente.
- **Perfil:** `ProfileScreen` ganhou o card de sugestão com aceitar/recusar/suprimir, o card de `NoChange`/limite e o botão de dispensar; a mensagem é apresentada uma vez, sem repetir o mesmo aviso a cada abertura.
- **Hoje:** segue avaliando e exibindo a sugestão; o aceite no Perfil passa pelo mesmo `ApplyReplan` e revalida versão/inputs/fingerprint.
- **Reset:** `DataResetCoordinator` limpa o resultado pendente junto das preferências de replanejamento.
- **Testes:** +5 em `:app` (salvar perfil avalia e publica T4, salvar calibração avalia e publica, `refreshForProfile` mostra `NoChange` armazenado uma vez, card de sugestão no Perfil e mensagem dispensável). Nenhum assert existente foi reduzido.

### Evidências

- `./gradlew check :app:assembleDebug` — verde (testes, `spotlessCheck`, lint, Kover e gate do parser FIT).
- `./gradlew :app:verifyRoborazziDebug --tests "*ProfileScreenshotTest" --tests "*ReplanScreenshotTest" --tests "*TodayScreenshotTest"` — verde; goldens de Perfil/Hoje/Replan sem alteração.
- **1.127 testes**, 0 falhas e 0 `@Ignore` (562 `:app`, 142 `core:data`, 396 `core:domain`, 27 `core:model`).
- `core:domain`: 97,77% de linhas, 81,82% de branches, 96,23% de instruções; parser FIT 93,01% de branches.
- Nenhuma migração, dependência nova ou dado real no diff.

### Decisões e observações

- **Resultado de UI não é decisão:** o store só transporta o resultado já calculado por `EvaluateReplan`; nenhuma regra de treino vive na tela.
- **Um aviso, não um nag:** `NoChange`/limite aparece após o salvamento e é consumido; aberturas seguintes não repetem a mesma mensagem. Sugestões continuam disponíveis porque o checkpoint não avança em `Suggest`.
- **Sugestão obsoleta é descartada:** se o plano já mudou de versão antes de o Perfil abrir, o resultado armazenado não é exibido; uma avaliação nova é feita.
- **Escopo respeitado:** briefing (C3-03/04), evento de prova/F2 e F4 continuam pendentes.

### Próximo passo

1. Validar a copy do card de Perfil com o usuário 0 e em aparelho real, incluindo fontes 200% e TalkBack.
2. Seguir para C3-03/C3-04 (briefing genérico e sheet) quando autorizado.



**Objetivo:** implementar o segundo item do Ciclo 3 com testes primeiro: fingerprint canônico/versionado do perfil, proveniência × checkpoint, T4 `PROFILE_CHANGED`, migração `13→14` e compare-and-apply do perfil dentro da mesma transação Room que valida a versão e grava a revisão.

### Concluído

- **Domínio (`PlanningFingerprint`):** `PlanningGoal` (somente as quatro distâncias suportadas), `PlanningProfileSnapshot` (experiência, pace de limiar, LTHR, esteira e objetivo), `PlanningFingerprint` (`planning-v1`) e `PlanningFingerprints.temporalContextHash` (`planning-context-v1`). Nome, avatar, peso, altura, sexo, cidade, coordenadas e `targetTimeSec` ficam fora; `null` é distinto de valor presente e disponibilidade continua em hash separado.
- **T4 no `PlanReplanner`:** `ReplanTrigger.PROFILE_CHANGED` e `ReplanCause` (`PROFILE_CHANGED`, `TIMEZONE_CONTEXT_CHANGED`, `MISSED_SESSIONS`, `AVAILABILITY_CHANGED`, `USER_REQUEST`). A sugestão guarda causa principal + lista completa; o rationale não atribui a mudança a um único fator quando há concorrência e cita `R-PLAN-09`.
- **Checkpoint × proveniência:** `ReplanResult.NoChange` carrega `PlanEvaluationCheckpoint`; `NoChange` e o primeiro encontro com plano legado avançam somente `lastEvaluated*`/`temporalContextHash`, sem incrementar versão nem inventar `generatedFrom*`.
- **Orquestração:** `ReplanContextBuilder` usa um único instante + fuso do perfil persistido para derivar `today`/`weekStart` e o hash temporal, sem depender do `ProfileTimeProvider` assíncrono. `EvaluateReplan` persiste o checkpoint em `NoChange`; `ApplyReplan`/`TrainingPlanRepository` releem o perfil na mesma transação e devolvem `STALE_PROFILE` se fingerprint ou contexto temporal divergirem.
- **Persistência (schema 14):** `weekly_plans` ganhou `generatedFromFingerprintVersion`, `generatedFromFingerprint`, `generatedFromProfileSnapshotJson`, `lastEvaluatedFingerprintVersion`, `lastEvaluatedFingerprint` e `temporalContextHash`; `weekly_plan_revisions` ganhou os pares anterior/novo de fingerprint, snapshot e contexto. Migração `13→14` preserva planos, sessões e revisões e deixa os campos novos `NULL` no legado. `saveWeek`, snapshots/`PlanJson`, export e reset preservam os campos.
- **Serialização e mapeamento:** `PlanningSnapshotJson` guarda só a allowlist; `ProfileMapper` centraliza o mapeamento perfil persistido ↔ domínio para a transação de T4.
- **App/UI:** `ReplanUi` entende `PROFILE_CHANGED`, fuso e múltiplas causas sem copy que atribua um único motivo. `ReplanViewModel` enfileira um pedido explícito que chegue enquanto uma avaliação automática termina, em vez de descartá-lo.
- **Testes:** +13 no domínio (fingerprint/allowlist/fuso/objetivo e T4/checkpoint/causas), +5 em `core:data` (migração `13→14`, checkpoint, preservação no `saveWeek`, `STALE_PROFILE` por perfil e por fuso) e +3 em `:app` (T4 via perfil, snapshot coerente de fuso/dia e reavaliação quando o perfil muda antes do aceite). O teste de supressão do `ReplanViewModel` cobre o enfileiramento do pedido explícito.

### Evidências

- `./gradlew check :app:assembleDebug` — verde (testes, `spotlessCheck`, lint, Kover e gate do parser FIT).
- `./gradlew :app:verifyRoborazziDebug` — verde na reexecução isolada; nenhum golden foi alterado. `TrendsFullScreenshotTest`/`TrendsScreenshotTest` saíram com o gráfico Vico em branco sob carga, a mesma flakiness visual já registrada e sem relação com o diff.
- **1.122 testes**, 0 falhas e 0 `@Ignore` (557 `:app`, 142 `core:data`, 396 `core:domain`, 27 `core:model`).
- `core:domain`: 97,77% de linhas (3.806/3.893), 81,82% de branches (2.764/3.378), 96,23% de instruções (26.709/27.756).
- Parser FIT: 93,01% de branches (572/615).
- Schema `14.json` exportado; migração `13→14` coberta com plano, sessão e revisão legados preservados e campos novos `NULL`.
- Nenhuma dependência nova, dado real no diff ou migração destrutiva.

### Decisões e observações

- **Allowlist, não hash de tudo:** o fingerprint só inclui campos que alteram plano/alvos; `inputsHash` passou a usar o fingerprint + contexto temporal + disponibilidade, então mudanças de peso/cidade não invalidam uma sugestão.
- **Legado honesto:** plano v12/v13 não recebe fingerprint retroativo. O primeiro encontro grava só o checkpoint avaliado; a proveniência só nasce no aceite de uma revisão.
- **Aceite atômico:** a checagem de perfil/fuso acontece dentro do `withTransaction` de `applyReplan`, junto de versão, inputs, disponibilidade e sessões protegidas; nenhum candidato obsoleto é aplicado.
- **Escopo respeitado:** C3-02 (avaliar ao salvar Perfil/Calibração e UI dedicada), briefing (C3-03/04), evento de prova/F2 e recuperação pós-prova não foram iniciados.

### Próximo passo

1. Autorizar C3-02: avaliar após salvar Perfil/Calibração, mostrar sugestão/`NoChange`/limite no Perfil e no Hoje e validar a copy com o usuário 0.
2. Validar em aparelho o T4 completo (editar experiência/objetivo/fuso, ver sugestão, aceitar e conferir o diff) com dados locais privados.
3. Manter evento de prova, briefing e F4 fora do escopo até os gates definidos.

## Sessão 2026-11-10 — C3-01A: guarda diária de iniciante

**Objetivo:** implementar o primeiro item do Ciclo 3 com testes primeiro: `QUALITY → EASY` quando o perfil vigente é `BEGINNER` e a sessão de hoje está `PLANNED`, citando `R-PRESC-04`, sem alterar o plano persistido, sem criar sessão, sem pedir aceite e sem reescrever `DONE`/`CLOSED`.

### Concluído

- **Domínio (`DailyPrescriptionEngine`):** `DailyPrescriptionInput` passou a carregar `sessionStatus` (default derivado da sessão planejada). A guarda `beginnerGuard` só ativa com `QUALITY` + `Experience.BEGINNER` + `SessionStatus.PLANNED`; o resultado usa o caminho `DOWNGRADE` já existente, com motivo “seu nível é iniciante e o treino forte fica para depois” no `rationale`, `R-PRESC-04` + `R-PRESC-02` nas regras e evidências do plano preservadas.
- **Confiança preservada:** a guarda é política de produto e não reduz a confiança dos dados; a decisão mantém a confiança calculada por prontidão, plano, carga e calor.
- **Versionamento:** `PrescriptionPolicy.ENGINE_VERSION` subiu para `daily-prescription-v2` e `sessionStatus` entrou no `inputsHash`, para a nova decisão não ser confundida com a versão anterior.
- **Orquestração status-aware (`GetTodayPrescription`):** o caso de uso lê a sessão de hoje e passa o status ao domínio. Dia `DONE`/`CLOSED` com prescrição persistida devolve a versão persistida sem recalcular; sem versão persistida, a decisão é apenas exibida e não é gravada. O caminho `PLANNED` continua persistindo/versionando como antes.
- **Catálogo:** `R-PRESC-04` entrou no `ExplanationCatalog` com título curto, sem jargão na superfície comum.
- **Testes:** +4 no domínio (guarda com iniciante, não-iniciante mantém, `DONE` e `CLOSED` não recebem a guarda) e +6 em `:app` (guarda com plano persistido intacto, não-iniciante, `DONE` preservando a versão anterior, `CLOSED` por doença/lesão, `CLOSED` por pulo e dia concluído sem prescrição persistida não sendo gravado). O teste `TodayViewModelPersistTest` ficou determinístico com `FakeTimeProvider` na segunda-feira, corrigindo dependência da data real (terça sem disponibilidade).

### Evidências

- `./gradlew check :app:assembleDebug` — verde (testes, `spotlessCheck`, lint, Kover e gate do parser FIT).
- `./gradlew :app:verifyRoborazziDebug` — verde na repetição; nenhum golden foi alterado. `TrendsFullScreenshotTest.volumeDark` saiu com o gráfico Vico em branco uma vez sob carga, passou isolado e na suíte completa (mesma família de flakiness já registrada), sem reduzir assert.
- **1.101 testes**, 0 falhas e 0 `@Ignore` (554 `:app`, 137 `core:data`, 383 `core:domain`, 27 `core:model`).
- `core:domain`: 97,79% de linhas (3.680/3.763), 81,96% de branches (2.703/3.298), 96,11% de instruções (26.015/27.069).
- Parser FIT: 93,01% de branches (572/615).
- Nenhuma migração, dependência nova, schema alterado ou dado real no diff.

### Decisões e observações

- **Regra no domínio, status na orquestração:** a UI continua sem calcular treino; `GetTodayPrescription` escolhe o contexto e o `DailyPrescriptionEngine` aplica a guarda.
- **Histórico não é recalculado:** `DONE`/`CLOSED` com prescrição persistida retornam exatamente a versão anterior; mesmo que o perfil mude depois, não nasce uma prescrição retroativa.
- **Plano intacto:** a guarda só produz uma nova `DailyPrescription`; nenhum caminho escreve em `weekly_plans`/`planned_sessions`.
- **Flakiness pré-existente:** em uma execução completa, `MetricsImportViewModelTest`, `TutorialSystemBackTest` e `TodayViewModelPersistTest` falharam por `Dispatchers.Main` concorrente/exceção não capturada antes do teste; todos passaram isolados e na reexecução dedicada de `:app:testDebugUnitTest`, e o `check` final ficou verde. Nenhum assert foi reduzido.
- **`Spotless`:** a execução de `spotlessApply` também removeu espaços finais de `docs/AUDITORIA_TECNICA_PRD_CICLO_3.md`, arquivo não versionado que já existia e bloqueava o gate; nenhuma alteração de conteúdo.
- **Escopo respeitado:** C3-01B (fingerprint/T4/migração), briefing, evento de prova/F2 e recuperação pós-prova não foram iniciados.

### Próximo passo

1. Autorizar C3-01B (fingerprint versionado, proveniência/checkpoint, T4, migração e compare-and-apply atômico).
2. Validar em aparelho o card de hoje com perfil iniciante e uma qualidade planejada, incluindo o sheet “Por que este treino?”.
3. Manter FR-207, FR-206 e F4 fora do escopo até os gates definidos no PRD v0.2.

## Sessão 2026-11-08 — ADR 0028 extraído e aceito

**Objetivo:** formalizar a decisão do replanejamento versionado depois de R-01/R-02/R-03, sem alterar código ou comportamento.

### Concluído

- **Novo ADR:** `docs/adr/0028-versioned-plan-replanning.md` com contexto, problema, decisão (geração do futuro, proteção de hoje/passado/`DONE`/`CLOSED`, versionamento, gatilhos T1/T2/T3, sugestão, aceite transacional, `PLAN_REPLACED`), alternativas, justificativa, consequências, riscos, impactos, dependências, PRD e notas de implementação.
- **Decisões registradas:** A1/A2/A5/A6 aplicadas; `inputsHash` é auditoria da versão vigente, não guarda de obsolescência; recusa por proposta (`semana + versão + hash`) e supressão até o fim do dia; prescrição de hoje intacta.
- **`DECISIONS.md` 1.4:** ADR 0028 na tabela como “Aceito — implementado em R-01/R-02/R-03”; ADR 0027 continua proposto.
- **`ARCHITECTURE.md` 1.7:** ADR 0028 na lista e §6.4 linkado; reconciliação atualizada.
- **`ROADMAP.md` 1.8 e plano do ciclo 2 v0.7:** status e referências atualizados; 0028 extraído, 0027 pendente.
- **Copy:** par `Agora não` × `Não sugerir hoje` registrado como decisão do usuário 0.

### Evidências

- Nenhuma alteração de código, schema, dependência ou teste; apenas documentação.
- `./gradlew spotlessCheck` — verde.
- Nenhum dado real no diff.

### Decisões e observações

- **ADR 0028 aceito, não reescrito:** o conteúdo do §4 do plano foi consolidado no arquivo definitivo; o plano permanece como registro histórico e aponta para o ADR.
- **ADR 0027 segue proposto:** A3/A4/A7 continuam bloqueando G-03 e a extração do ADR da sessão guiada.
- **Sem mudança de comportamento:** a extração não altera `PlanReplanner`, schema, UI ou prescrição.

### Próximo passo

1. Fechar A3/A4/A7 com o usuário 0.
2. Iniciar G-01 (`GuidedCueEngine` puro) e G-02 (persistência) quando as decisões de G estiverem claras.
3. Manter FR-011, N-03, N-05 e P1-12 no backlog.

## Sessão 2026-11-08 — R-03: replanejamento no app/UI

**Objetivo:** implementar R-03 do ciclo 2: casos de uso `EvaluateReplan`/`ApplyReplan` sem repetir a regra do `PlanReplanner`, sugestão por dia com aceitar/recusar/suprimir, reavaliação quando versão/hash mudam, selo de versão, diff “o que mudou”, botão explícito e copy sem culpa — sem tocar na prescrição de hoje nem em sessões passadas/hoje ou futuras `DONE`/`CLOSED`.

### Concluído

- **Contexto compartilhado:** `ReplanContextBuilder` lê o plano versionado, perfil, prontidão, carga, semana anterior, posição no bloco e datas de doença do check-in; captura o baseline de disponibilidade quando ele é desconhecido, sem inferir mudança histórica. `readinessResult`/`buildLoadSnapshot` saíram da UI para `:app/application`, para o caso de uso não depender de Compose.
- **`EvaluateReplan`:** monta o `ReplanInput` e delega ao `PlanReplanner`; devolve `Suggestion` com o candidato mesclado e o diff, `NoChange` com o motivo ou `Unavailable` quando não há semana. Avaliação automática usa os gatilhos T1/T2; o pedido explícito usa T3.
- **`ApplyReplan`:** relê o estado, exige a `expectedVersion`, recalcula o `inputsHash` e chama `applyReplan` transacional com esse hash. Versão, data ou inputs diferentes devolvem `Stale` com uma avaliação nova; conflito protegido/candidato inválido devolve `Rejected`. Nenhum candidato obsoleto é aplicado.
- **Diff de apresentação:** `ReplanDiff` compara somente sessões estritamente futuras `PLANNED` (adicionada, removida ou alterada por tipo/título/duração/alvos/regras/evidências). É apresentação, não regra de treino.
- **Sugestão por dia:** `ReplanSuggestionStore` guarda em SharedPreferences a recusa daquela proposta (semana + versão + hash dos inputs) e a supressão até o fim do dia. Recusar não impede uma proposta de versão/inputs diferentes; suprimir esconde só as automáticas, e o botão explícito continua funcionando. O reset limpa a preferência.
- **`ReplanViewModel`:** avalia no resume do Hoje, oferece aceitar/recusar/suprimir, reavalia no aceite obsoleto e recarrega versão e último diff. Mensagens passivas explicam sucesso, obsolescência, `NoChange` e falha sem culpa.
- **UI do Hoje:** o card do plano ganhou selo `v{n}`, “Ver o que mudou” (quando existe revisão) e “Replanejar minha semana” (quando há futuro). A sugestão aparece como card com versão `v1 → v2`, o que muda, confiança e ações; “Ver o que mudou” abre um sheet com a revisão aceita. A prescrição de hoje não é recalculada pelo aceite.
- **Leitura da revisão:** `TrainingPlanRepository.getLatestRevision` + `PlanJson.sessionsFromJson` reconstroem as sessões substituídas do snapshot para o diff persistido, sem novo schema.
- **Testes:** +27 em `:app` — 7 de `EvaluateReplan`, 4 de `ApplyReplan` (preservação de hoje/passado/`DONE`, prescrição intacta, versão obsoleta, inputs alterados e semana removida), 6 de `ReplanViewModel` (sugestão, supressão, recusa, aceite, stale e ausência de semana), 3 de `ReplanSuggestionStore`, 3 de UI do Hoje, 2 de screenshot do card de replanejamento e 2 capturas novas do card do plano no Hoje (`today-plan-dark`, `today-plan-light`), além de `replan-dark`/`replan-light`. `TrainingPlanRepositoryTest` ganhou asserções do snapshot reconstruído. Nenhum assert foi reduzido.

### Evidências

- `./gradlew check :app:assembleDebug` — verde (testes, `spotlessCheck`, lint, Kover e gate do parser FIT).
- `./gradlew :app:verifyRoborazziDebug` — verde após gravar os 4 goldens novos; os goldens existentes do Hoje não mudaram (o card do plano fica abaixo da dobra na captura principal).
- **1091 testes**, 0 falhas e 0 `@Ignore` (548 `:app`, 137 `core:data`, 379 `core:domain`, 27 `core:model`).
- `core:domain`: 97,79% de linhas (3.668/3.751), 81,91% de branches (2.690/3.284), 96,10% de instruções (25.941/26.995); sem alteração de domínio nesta entrega.
- Parser FIT: 93,01% de branches (572/615).
- Nenhuma migração, dependência nova ou dado real no diff.

### Decisões e observações

- **Sem regra nova:** o app só coleta estado e apresenta; `PlanReplanner` e os guardrails existentes continuam sendo a única autoridade.
- **Obsolescência em duas camadas:** a `expectedVersion` é checada antes da transação e o `currentInputsHash` é recalculado no aceite; o repositório repete a checagem dentro do `withTransaction`. Quando algo muda, a UI recebe uma sugestão nova em vez de aplicar a antiga.
- **`inputsHash` continua auditoria:** não é comparado com a versão vigente; o aceite usa versão + inputs recalculados + baseline de disponibilidade, como registrado no R-02.
- **Prescrição de hoje intacta:** a revisão só toca `date > today`; o teste chama `GetTodayPrescription` antes/depois e confirma o mesmo `id` e uma única versão persistida quando a confiança do plano não muda.
- **Proteção preservada de ponta a ponta:** a UI nunca mostra sessões protegidas no diff, o `PlanReplanner` as copia e o repositório aborta se o conteúdo protegido mudar.
- **Copy revisada:** “Dá para ajustar o resto da semana”, “Agora não” e “Não sugerir hoje” evitam culpa e prometem somente o futuro. O par `Agora não` × `Não sugerir hoje` foi fechado com o usuário 0 para distinguir “esta proposta” de “o resto do dia” sem o verbo ambíguo “mostrar”; o texto está em `docs/COMMUNICATION.md` §6.7.
- **Flakiness observada:** `HealthMetricsScreenshotTest.healthRecoveryChartsDark` saiu com o gráfico em branco numa execução completa de `verifyRoborazziDebug` sob carga e passou isolado; é a mesma família de flakiness visual já tratada com frames/pausa, não tem relação com R-03 e nenhum assert foi alterado. `MetricsImportViewModelTest`/`TutorialSystemBackTest` seguem como item separado de estabilização sob concorrência com lint.

### Próximo passo

1. Copy de recusa revisada com o usuário 0: `Agora não` (esta proposta) × `Não sugerir hoje` (sugestões automáticas até amanhã). Validar em aparelho real a leitura dessa diferença.
2. Validar em aparelho real o fluxo completo (sugestão automática, aceite, versão e diff) com dados locais privados.
3. Manter A3/A4/A7 pendentes; G-03 continua bloqueado. Considerar um item separado para estabilizar a suíte `:app`/goldens sob concorrência com lint.

## Sessão 2026-11-08 — R-02: persistência versionada do replanejamento (`core:data`)

**Objetivo:** implementar R-02 do ciclo 2: migração `12→13`, snapshot append-only das revisões, aceite condicional e transacional do candidato do `PlanReplanner`, captura explícita do baseline de disponibilidade e inclusão no export/reset.

### Concluído

- **Schema 13:** `weekly_plans` ganhou `version` (default 1), `revisionReason`, `revisedAt`, `inputsHash` e `availabilityHash`; nova tabela append-only `weekly_plan_revisions` com FK `CASCADE` para a semana, versões/hashes anterior e novo, versão do gerador, snapshots JSON do plano e das sessões futuras substituídas, trigger, motivo, regras/evidências, confiança e `createdAt`. Migração `12→13` preserva planos/sessões campo a campo e deixa hashes legados `NULL` (desconhecidos), sem inventar histórico.
- **Aceite transacional (`TrainingPlanRepository.applyReplan`):** valida `expectedVersion`, o baseline de disponibilidade e, quando fornecido, o `currentInputsHash` recalculado; insere o snapshot, apaga apenas sessões futuras `PLANNED`, insere candidatas com conflito `ABORT` (nunca `REPLACE`), recalcula os agregados a partir do candidato, incrementa `version` e mantém `createdOn`. Sessões de hoje/passadas e futuras `DONE`/`CLOSED` são preservadas; qualquer conflito aborta e reverte a transação.
- **Baseline de disponibilidade:** `captureAvailabilityBaseline` grava o hash apenas quando ele é desconhecido (planos migrados ou primeira versão), sem sobrescrever um baseline conhecido e sem criar revisão falsa.
- **Leitura versionada:** `StoredWeeklyPlan` + `getWeekRecord` expõem plano, versão, motivo/data da revisão e hashes para o R-03 consumir sem recalcular nada.
- **Export/reset:** o JSON ganhou `weeklyPlanRevisions` e os novos campos do plano; o reset apaga as revisões antes dos planos e reporta `planRevisions` em `UserDataDeletion`.
- **Serialização compartilhada:** `PlanJson` unifica o formato do export e dos snapshots de revisão (plano, sessões e revisão), evitando duas representações divergentes.
- **Versão do gerador:** `WeeklyPlanGenerator.ENGINE_VERSION = "weekly-plan-v1"` registrada em cada revisão.

### Evidências

- `./gradlew check :app:assembleDebug` — verde (testes, `spotlessCheck`, lint, Kover e gate do parser FIT).
- `./gradlew :app:verifyRoborazziDebug` — verde (nenhum golden de UI mudou).
- **1064 testes**, 0 falhas e 0 `@Ignore` (521 `:app`, 137 `core:data`, 379 `core:domain`, 27 `core:model`).
- `core:domain`: 97,79% de linhas (3.668/3.751), 81,91% de branches (2.690/3.284), 96,10% de instruções (25.941/26.995).
- `core:data`: +10 testes — migração `12→13` (campos novos, hashes nulos, FK/cascade) e 9 casos de `applyReplan`/baseline (aceite, versão obsoleta, inputs recalculados, disponibilidade obsoleta, nova revisão após mudança de inputs, `DONE`/`CLOSED` protegidos, rollback integral, recálculo de agregados e captura de baseline). As asserções de export/reset foram estendidas nos testes existentes de `UserDataRepository`.
- Schema `13.json` exportado em `core/data/schemas/`; nenhum dado real no diff.

### Decisões e observações

- **`inputsHash` é auditoria, não guarda de obsolescência:** ele registra os inputs que produziram a versão vigente (o `expectedInputsHash` aceito). Comparar esse valor com uma sugestão nova rejeitaria revisões legítimas sempre que a prontidão/carga mudasse; por isso o aceite usa `expectedVersion` + baseline de disponibilidade + `currentInputsHash` recalculado (opcional, para o R-03). A decisão está registrada no plano do ciclo 2 (§5.4).
- **Validação dentro da transação:** a checagem de versão/hash e a escrita acontecem no mesmo `withTransaction`; um caso de uso que lesse antes poderia aceitar um candidato obsoleto numa corrida.
- **R-02 não toca em UI nem em regra de treino:** o `ApplyReplan` app-facing (reavaliar e chamar a transação) e a sugestão por dia ficam no R-03, junto com o selo de versão/diff.
- **`saveWeek` preserva metadados de revisão** ao re-salvar a mesma semana, para não zerar versão/hashes silenciosamente; o caminho de revisão continua sendo `applyReplan`.
- **Flakiness de `:app`** observada em execuções concorrentes com lint (`MetricsImportViewModelTest`/`TutorialSystemBackTest`) não se reproduziu nesta execução única de `check`; permanece como item separado de estabilização, sem relação com o diff de R-02.

### Próximo passo

1. R-03: `EvaluateReplan`/`ApplyReplan` app-facing, sugestão por dia com aceitar/recusar e invalidação se versão/hash mudar, selo de versão + diff, goldens e copy revisada.
2. Manter A3/A4/A7 pendentes; G-03 continua bloqueado.
3. Considerar um item separado para estabilizar a suíte `:app` sob concorrência com lint.

## Sessão 2026-11-08 — R-01: replanejamento puro da semana (`core:domain`)

**Objetivo:** implementar R-01 do ciclo 2 com testes primeiro, escopo restrito a `core:domain`: `PlanReplanner` determinístico que avalia desvios, regenera apenas o futuro da semana e devolve sugestão auditável ou `NoChange`.

### Concluído

- **Contratos:** `ReplanTrigger` (T1/T2/T3), `ReplanSuggestion`, `ReplanResult` (`Suggest`/`NoChange`) e `ReplanInput` em `core/domain/replan`.
- **T1 (adesão):** conta sessões `PLANNED` passadas, a partir de `createdOn`, treináveis e fora de `nonTrainablePastDates`; não fecha sessões automaticamente (A5). O limiar é configurável, com default 2 como heurística de produto (A6), sem tratamento científico.
- **T2 (disponibilidade):** hash SHA-256 determinístico e independente da ordem do mapa; hash igual não dispara. Baseline desconhecido (`null`) nunca é inferido como mudança: o resultado é `NoChange` com o hash atual para ser persistido como referência.
- **T3 (pedido explícito):** força a avaliação mesmo sem T1/T2.
- **Geração e merge:** `WeeklyPlanGenerator` recebe `today`, gera só o futuro, e o replanejador preserva hoje/passado e sessões futuras `DONE`/`CLOSED` campo a campo. `createdOn` original é mantido (A1).
- **Validação da semana mesclada:** máximo de 2 qualidades, um único longão, espaçamento qualidade↔qualidade e qualidade↔longão, e disponibilidade dos dias futuros. Sessões concluídas contam para os limites.
- **Teto conservador:** a revisão não pode aumentar volume/duração futura nem adicionar qualidade além da intenção futura vigente; quando não há como validar, retorna `NoChange` (nada é aplicado silenciosamente).
- **`NoChange` semântico:** compara data, tipo, título, duração, alvos, regras e evidências — uma mudança só de alvos/regras já produz sugestão.
- **Auditoria:** `expectedVersion`, `expectedInputsHash`, `expectedAvailabilityHash` e `availabilityHash` na sugestão; sem `R-PLAN-09` e sem citar `R-PLAN-01`; os IDs vêm do plano efetivamente regenerado.
- **Testes:** 28 novos em `PlanReplannerTest`, cobrindo limiar 1/2, `createdOn`, REST/doença, T2 igual/diferente/desconhecido, T3, troca manual, teto de volume/qualidade, qualidade/longão já concluídos, estados protegidos, semana/futuro ausentes, disponibilidade vazia e determinismo/hashes.

### Evidências

- `./gradlew :core:domain:test --tests "*PlanReplannerTest"` — 28 testes, verde.
- `./gradlew check` — verde (inclui `spotlessCheck`, lint, Kover e gate do parser FIT).
- `core:domain`: 379 testes, 0 falhas; 97,76% de linhas, 81,91% de branches e 96,09% de instruções.
- Nenhuma migração, dependência, UI ou alteração de schema; nenhum dado real no diff.

### Decisões e observações

- **A1/A5/A6 aplicadas:** `createdOn` preservado na semana vigente, contagem sem fechamento e limiar 2 como produto. A2 descreve a persistência de R-02, ainda não implementada.
- **Conservador por desenho:** os cenários em que a regeneração aumentaria carga, moveria uma qualidade perdida para o futuro ou violaria espaçamento retornam `NoChange`; a sugestão é sempre explícita.
- **Escopo respeitado:** apenas `core:domain` foi alterado. R-02 (migração/snapshot/`ApplyReplan`), R-03 (UI/caso de uso) e toda a sessão guiada permanecem pendentes; A3/A4/A7 continuam bloqueando G-03.
- **Observação de suíte:** em execuções de `check`/build concorrentes com lint, dois testes de `:app` falharam por timeout ou exceção vazada (`MetricsImportViewModelTest` e `TutorialSystemBackTest`); ambos passam isolados e na execução dedicada de `:app`, sem relação com este diff.

### Próximo passo

1. R-02: migração `12→13`, tabela append-only de revisões, `ApplyReplan` transacional com `expectedVersion`/hashes e inclusão no export/reset.
2. R-03: caso de uso e UI da sugestão (aceitar/recusar), selo de versão e diff, com goldens.
3. Fechar A3/A4/A7 antes de iniciar G-03.

## Sessão 2026-11-08 — Auditoria técnica do plano do ciclo 2

**Objetivo:** auditar a viabilidade do plano proposto para sessão guiada e replanejamento antes de qualquer código. A feature 2 (captura automática via Health Connect) permanece fora do escopo desta proposta.

### Concluído

- **Documento revisado:** `docs/PLANO_CICLO_2_GUIADO_E_REPLANEJAMENTO.md` v0.3; decisões do replanejamento registradas e handoff de R-01 preparado. ADRs 0027/0028 ainda não foram extraídos nem aprovados.
- **Decisões de replanejamento confirmadas por Rafael:** manter `createdOn` na semana vigente; uma linha vigente + snapshots completos append-only para persistência; contar sessões passadas `PLANNED` elegíveis sem fechá-las automaticamente; T1 = 2 sessões elegíveis (heurística de produto, não ciência).
- **Base factual verificada no código** antes de escrever (§2 do documento): cronômetro em memória em `TodayScreen` (FR-002), rejeição de `GUIDED` em `CompleteSession`, ausência de `recordGuidedExecution`/TTS/foreground service para sessão, PK `weekStart` em `weekly_plans` sem colunas de versão, ID determinístico de sessão, `createdOn`/N-02, `srpe()` existente e não coletado, schema v12.
- **Especificação R-01 (ainda não implementada):** o plano exige validar a semana mesclada com sessões concluídas, não repor carga perdida, recalcular agregados, preservar estados protegidos e rejeitar sugestões obsoletas. Snapshots devem guardar hashes/metadados; hashes legados ficam desconhecidos. FR-011 continua pendente.
- **Proposta de sessão guiada (ainda não implementada):** cues temporais sem estrutura de intervalos; coleta de RPE sem sRPE na carga nesta entrega; pausas, reinício, TTS, notificação/FGS e reset com época têm gates documentados.
- **Plano técnico:** migrações propostas 12→13 e 13→14; as etapas são sequenciais. As 7 entregas R-01..R-03/G-01..G-04 mantêm gates, testes e documentação.
- **Checklist (§10):** 7 decisões (A1–A7), 8 verificações de código (V1–V8) e 4 verificações de documento/ciência (O1–O4). A1/A5/A6 governam R-01; A2 define a persistência de R-02. A3/A4/A7 e decisões de G seguem pendentes.

### Evidências

- Nenhuma alteração de código, schema, dependência ou teste; somente documentação.
- Nenhum dado real no diff. Documentação Android atual consultada para tipos de foreground service, TTS e permissão de notificações.

### Decisões e observações

- **Sem implementação nesta sessão:** a aprovação do escopo R-01 não aprova os ADRs 0027/0028 por inteiro. R-01 pode avançar na próxima sessão; A3/A4/A7 continuam bloqueando G-03.
- **Ordem proposta:** replanejamento primeiro, sessão guiada depois. Migrações 12→13→14 são sequenciais; o documento não propõe trabalho paralelo nelas.
- **Guardas mantidas:** sem LLM, sem GPS/BLE, sem dependência nova; replanejamento não inventa regras fisiológicas e sessão guiada não produz sRPE nesta etapa.

### Próximo passo

1. Passar `docs/PLANO_CICLO_2_GUIADO_E_REPLANEJAMENTO.md` v0.3 ao próximo modelo como instrução de handoff.
2. O próximo modelo implementa R-01 com testes primeiro, limitado a `core:domain`; nenhuma implementação ou teste foi feito nesta sessão.
3. Manter R-02/R-03 e G-01..G-04 sujeitos aos gates do plano; G-03 permanece bloqueada até fechar A3/A4/A7.

## Sessão 2026-11-08 — Reatividade no import e clareza do treino concluído

**Objetivo:** fazer o app guiar o usuário no momento em que ele importa uma corrida e deixar explícito, na tela Hoje, que o treino do dia já foi feito — incluindo o que significa “feito sem registro”.

### Concluído

- **Oferta reativa no import:** `ImportedRunPromptStore` (fila durável em SharedPreferences, `StateFlow` e `commit()` para sobreviver ao worker), `OfferImportedRun` (avalia corrida/esteira do dia local, plano existente, sessão `PLANNED` ou `DONE`, doença, vínculo existente) e gancho em `StagedImportProcessor` após `Imported`/`Duplicate`, sem transformar import bem-sucedido em job falho.
- **Overlay global:** `ImportedRunPromptViewModel` + `ImportedRunPromptHost` no `MainContent`, com três modos — **oferta** (“Usar como treino de hoje” / “Só salvar no app”), **aviso** (sessão já concluída com evidência forte) e **correção** (sessão `MANUAL`: “Usar esta corrida” / “Manter sem registro”). O vínculo usa `CompleteSession` com evidência `ACTIVITY`; erros aparecem no próprio diálogo; o sucesso limpa a fila. O prompt aparece independentemente da aba e também no share intent.
- **Correção do “fiz sem registro”:** `ReplaceManualCompletion` + `SessionExecutionRepository.replaceManualWithActivity` trocam um `DONE`/`MANUAL` pela corrida do mesmo dia, de forma transacional: a execução existente vira `ACTIVITY` com duração/distância reais (RPE manual descartado) e a sessão mantém data e vínculo 1:1. O card do Hoje ganhou “Corrigir registro” quando há corrida compatível; `ACTIVITY`, `GUIDED` e `LEGACY_UNVERIFIED` continuam somente aviso.
- **Card do treino de hoje:** sessão `DONE` mostra selo “Concluído”, nota de evidência, bloco “FEITO” com duração/distância/pace/RPE reais e bloco “PLANEJADO” com os alvos; botões iniciar/trocar/concluir somem e “Ver corrida” abre o detalhe. Sessão `CLOSED` mostra o motivo e também não oferece ações.
- **Explicação do “feito sem registro”:** o card do Hoje diz que o treino foi concluído sem vincular corrida e não entra em volume/carga/zonas; o plano da semana repete a explicação no dia com evidência `MANUAL`.
- **Reset:** `DataResetCoordinator` limpa a fila de ofertas junto com as demais preferências de UI.
- **Testes:** +24 em `:app` (avaliação da oferta, ViewModel/diálogo do overlay, correção de `MANUAL`, selo/resumo do Hoje, nota manual e resumo de execução no `TodayViewModel`) e +3 em `core:data` (substituição transacional, rejeição de sessão não manual e de corrida já vinculada). `TutorialViewModelTest` deixou de depender da data real (falhava aos domingos, quando a disponibilidade Mon/Wed/Sat não gera sessões) e os testes Robolectric passaram a usar `android.app.Application` (`robolectric.properties`), eliminando o vazamento de coletores do `MeuCoachApplication` entre testes.

### Evidências

- `./gradlew check :app:assembleDebug` — verde.
- `./gradlew :app:verifyRoborazziDebug` — verde; goldens de Hoje sem alteração (o estado planejado não mudou).
- **1026 testes**, 0 falhas e 0 `@Ignore` (521 `:app`, 127 `core:data`, 351 `core:domain`, 27 `core:model`).
- Nenhuma migração de Room, dependência nova ou alteração de schema.

### Decisões e observações

- **Nunca vincular sozinho:** a oferta é uma pergunta; rejeitar mantém a sessão `PLANNED` e a corrida como treino livre.
- **Convite só no dia:** corrida de outro dia, dia sem plano, dia de descanso, doença, sessão encerrada ou corrida já vinculada não geram prompt.
- **Já concluído com evidência forte vira aviso:** `ACTIVITY`, `GUIDED` e `LEGACY_UNVERIFIED` não são reescritos; a corrida importada fica como treino livre. A exceção é `MANUAL`, que pode ser corrigido com confirmação explícita.
- **Correção de “fiz sem registro”:** `ReplaceManualCompletion` + `replaceManualWithActivity` atualizam a execução no lugar e trocam a evidência para `ACTIVITY`, mantendo a data e o vínculo 1:1. O RPE manual é descartado junto com a declaração; a oferta aparece no overlay do import e no botão “Corrigir registro” do card concluído.
- **Persistência de UI, não de treino:** a fila de ofertas é preferência de interface em SharedPreferences; nenhum dado de saúde novo é persistido.
- **Flakiness tratado:** `MetricsImportViewModelTest` estourava timeout e `TutorialSystemBackTest` recebia exceção de banco de outro teste (`no such table: athlete_profile`) em execuções completas. A causa provável era o `MeuCoachApplication` real iniciando coletores de escopo de aplicação em cada teste Robolectric; os testes passaram a usar `android.app.Application` via `robolectric.properties`. Os goldens de gráficos (`HealthMetricsScreenshotTest` e `TrendsFullScreenshotTest`) ganharam mais frames e uma pausa real antes da captura, porque o Vico podia sair em branco sob carga. Nenhum assert foi reduzido.

### Próximo passo

1. Validar no aparelho o overlay após importação pelo seletor e pelo share intent, com o app aberto e após morte do processo.
2. Revisar a copy do aviso de treino já concluído e da correção de “fiz sem registro” com o usuário 0.
3. Cobrir também a correção pelo detalhe da atividade no futuro (hoje ela parte do overlay ou do card do Hoje).

## Sessão 2026-11-06 — C1-06: verdade documental, ADRs e regressões finais

**Objetivo:** impedir que a documentação descreva um app que não existe, reconciliando arquitetura, dados, testes, produto, PRD e roadmap com o schema v12 e as regras de evidência.

### Concluído

- **`ARCHITECTURE.md` 1.4:** módulos reais (5) separados dos planejados (`:core:testing`, features); fluxo `plano → prescrição → execução`; `TimeProvider`, prescrição/evidência, lixeira/época; persistência no schema v12 com as 12 entidades e migrações 1→12; export padrão sem lixeira; ADRs 0022–0026 linkados.
- **`DATA_MODEL.md` 1.3:** seção explícita de persistido × derivado; `DailyPrescription` documentada; campos reais de perfil (`name`/`avatarPath`/`treadmillAccess`) e check-in (`illnessOrInjury`/`heatLevel`); `Readiness`, `Recommendation`, `RecommendationRule`, `SessionFeedback` e `evidence.json` deixaram de ser descritos como existentes; status `TRASH_CONFLICT`; 9→10 documentada.
- **`TESTING.md` 1.4:** 46 goldens reais agrupados, ausência de instrumentados/E2E automatizado, gates reais de cobertura (só `core:domain` e parser FIT), Kotest/MockK/Turbine removidos da stack, 9 cenários de `Cycle1RegressionTest` e subseção dos testes do ciclo 1.
- **`PRODUCT.md` 1.2:** revisão registrada, escopo com conclusão por evidência e lixeira de 7 dias, correção de “4 módulos” e da nota de reconciliação.
- **`ROADMAP.md` 1.3:** ciclo 1 concluído com status por entrega, até 2 qualidades no plano, “Iniciar” como prévia (não sessão guiada persistente) e critérios de MVP separando capacidade entregue de aceitação (P1-12).
- **`PRD_COACH_CORRIDA.md`:** §0.4 “Reconciliação pós-auditoria” mapeia BUG-01/02, SEC-01/02, BUG-09, N-04 e ARCH-01/04 para as entregas; FR-001/003/009 marcados como implementados; FR-009 passa de 30 para 7 dias; FR-004/005 registram o que ainda não foi feito.
- **`DECISIONS.md` 1.2:** status de implementação dos ADRs 0022–0026; 0026 permanece “aceito parcialmente”.
- **`README.md`:** 999 testes, Room v12, módulos reais, stack de testes sem dependências inexistentes, Detekt como planejado.
- **`SPORTS_SCIENCE.md`:** §8.11 formaliza a prescrição do dia (`KEEP`/`REDUCE`/`DOWNGRADE`/`REST`/`NO_PLAN`) e a evidência de conclusão; `R-PRESC-01/02/03` entraram no catálogo de regras.
- **`PLANO_TECNICO_CICLO_1.md` 1.2:** C1-06 executado, matriz U-TIME/U-DEL atualizada para implementado, 9 cenários, checklist do ciclo fechado.
- **Correção factual:** o índice único de `daily_prescriptions` (`date + inputsHash + engineVersion`) **não existe** no schema; a deduplicação é transacional no repositório. A afirmação foi corrigida em `PROGRESS.md`, `DATA_MODEL.md`, `ARCHITECTURE.md` e no plano técnico.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug` — verde.
- **999 testes**, 0 falhas e 0 `@Ignore` (497 `:app`, 124 `core:data`, 351 `core:domain`, 27 `core:model`), conferidos pelos XML de resultado.
- `core:domain`: 97,69% de linhas (3.380/3.460), 82,09% de branches (2.544/3.099), 96,04% de instruções (23.890/24.876).
- Parser FIT: 93,01% de branches (572/615); os 46 goldens de `app/src/test/screenshots/` passam.
- Lint sem erros; `spotlessCheck` verde.
- Nenhum dado real no diff; nenhuma alteração de código de produção, schema, dependência ou migração.

### Decisões e observações

- **Doc = código, não doc = intenção:** o que ainda não existe (GUIDED persistente, progressão por volume, iniciante, backfill, backup versionado) aparece como pendente, não como capacidade.
- **Histórico preservado:** `PROGRESS.md` mantém as sessões anteriores; a única correção retroativa foi factual (índice único), registrada aqui.
- **ADR 0026 segue parcial:** o gate de C1-02 está implementado; os limiares de iniciante/cobertura continuam propostos e precisam de validação com dados reais.
- **Gate documental é manual:** um teste que compare prosa a banco seria frágil; a revisão usa `MeuCoachDatabase.VERSION`, a lista de entidades do schema 12 e o grafo de módulos.
- **Sem código novo:** o C1-06 não muda comportamento; qualquer divergência restante vira tarefa, não “ajuste silencioso”.

### Próximo passo

1. Revisão humana da copy de `LEGACY_UNVERIFIED`, das tolerâncias de vínculo e dos limiares do ADR 0026.
2. **P1-12:** validação E2E com 4 semanas de uso real e ajuste de pesos/paces.
3. Ciclo seguinte: `GUIDED` persistente, N-03 (semana de prova/recuperação), N-05 (backfill Health Connect), backup versionado e progressão de volume (ADR próprio).

## Sessão 2026-11-06 — C1-04: conclusão com evidência e vínculo atividade ↔ sessão

**Objetivo:** tornar `DONE` consequência de uma execução/atividade, não de um clique, e fechar o vínculo planejado × executado com migração testada.

### Concluído

- **Modelo (`core:model`):** `CompletionEvidence` (`ACTIVITY`, `GUIDED`, `MANUAL`, `LEGACY_UNVERIFIED`), `ExecutionSource` e `SessionExecution` (início/fim, pausas, duração, distância, RPE). `MANUAL` não aceita `activityId` nem distância e exige RPE 1–10.
- **Domínio:** `SessionStatus` passou a `PLANNED`/`DONE`/`CLOSED`; `PlannedSession` ganhou evidência, `executionId`, `activityId`, `closureReason` e `closedAt`. O construtor rejeita `DONE` sem evidência e `CLOSED` sem motivo.
  - `SessionCompletionRules` bloqueia futuro, semana anterior, sessão já concluída/encerrada, descanso prescrito e doença; dia anterior da mesma semana é permitido com aviso.
  - `SessionMatcher` sugere corridas compatíveis por data local, esporte e tolerâncias explícitas (duração ±20% ou ±10 min; distância ±15% ou ±1 km). A sugestão nunca vincula sozinha.
  - `PlanCompletionCalculator` separa adesão (`done`) de execução comprovada (`verified`), conta `MANUAL` e `LEGACY_UNVERIFIED` à parte e remove descanso/doença/plano substituído do denominador.
  - A progressão do `WeeklyPlanGenerator` só considera evidência forte (`ACTIVITY`/`GUIDED`); `LEGACY_UNVERIFIED` e `MANUAL` não liberam progressão.
- **Dados:** `session_executions` (Room 12), colunas de evidência/vínculo/encerramento em `planned_sessions`, índices únicos para as regras 1:1 e `SessionExecutionRepository` transacional (`recordActivityExecution`, `recordManualExecution`, `closeSession`, consultas por sessão/atividade).
- **Migração 11→12:** cria `session_executions`, adiciona as colunas e faz backfill explícito (`DONE → LEGACY_UNVERIFIED`, `SKIPPED → CLOSED/USER_SKIPPED`), preservando status, `completedAt` e planos.
- **Caso de uso (`CompleteSession`):** valida sessão/data/prescrição, exige evidência, bloqueia `REST`/doença e encerra a sessão como `CLOSED`; `GUIDED` novo continua indisponível até o ciclo 3, sem botão falso.
- **App/UI:** “Concluir treino” abre um diálogo com sugestões de corrida e “fiz sem registro” (duração + RPE); o aviso diz que o registro manual não entra em volume, carga ou zonas. O plano mostra “Feito · verificado”, “Feito · sem registro”, “Feito · não verificado”, “Descanso prescrito”, “Doença/lesão” ou “Pulado”. Descanso/doença fecham a sessão automaticamente e desabilitam início/conclusão.
- **Tendências:** adesão e “feito com evidência” aparecem separados, com nota de que conclusões antigas não liberam progressão.
- **Lixeira (C1-05):** apagar uma atividade vinculada suspende a evidência, restaurar devolve e expurgar remove o vínculo mantendo `LEGACY_UNVERIFIED`.
- **Export/reset:** execuções entram no JSON; execuções de atividades na lixeira ficam fora do export padrão; o reset apaga a tabela e conta as execuções removidas.
- **Testes:** +11 no domínio (`SessionCompletionRulesTest`, matcher, adesão/progressão e gate de `DONE` sem evidência), +7 em `core:data` (repositório 1:1, manual, encerramento, lixeira/restauração/expurgo e migração 11→12) e +9 em `:app` (caso de uso, diálogo de evidência, vínculo, manual e status do plano).

### Evidências

- `./gradlew check` — verde (testes, Spotless, lint, Kover e parser FIT).
- `./gradlew :app:assembleDebug` — verde.
- `./gradlew :app:recordRoborazziDebug` e `:app:verifyRoborazziDebug` — verdes após regravar `trends-efficiency-dark` e `trends-readiness-dark` com a linha de execução comprovada.
- **999 testes**, 0 falhas (497 `:app`, 124 `core:data`, 351 `core:domain`, 27 `core:model`); 0 `@Ignore`.
- `core:domain`: 97,69% de linhas, 82,09% de branches, 96,04% de instruções; parser FIT 93,01% de branches.
- Schema `12.json` exportado; migração 11→12 coberta com banco v11 e backfill de `DONE`/`SKIPPED`.

### Decisões e observações

- **`DONE` não é clique:** a validação existe no modelo e no caso de uso, e o banco reforça o vínculo 1:1 com índices únicos.
- **Descanso/doença não ficam pendentes:** o dia é encerrado com motivo; a prescrição continua sendo a autoridade do dia e não é reescrita ao concluir.
- **`MANUAL` é honesto:** conta adesão com confiança baixa, mas não inventa distância, pace, FC, zonas ou carga.
- **`LEGACY_UNVERIFIED` é leitura:** o passado não é promovido a verdade; a UI explica por que não conta para progressão.
- **Doença não impede registrar a verdade:** a corrida continua registrada como treino livre; o vínculo com a sessão do dia não é automático.
- **GUÍDADO continua contrato:** nenhuma execução `GUIDED` nova é produzida neste ciclo; “Iniciar” é uma prévia e exige evidência para concluir.
- **Flaky observado:** `MetricsImportViewModelTest` estourou timeout uma vez em execução completa, passou isolado e na execução seguinte; nenhum assert foi reduzido.

### Próximo passo

1. Revisar C1-04 com o usuário 0, especialmente as tolerâncias de vínculo e a copy de `LEGACY_UNVERIFIED`.
2. Seguir para **C1-06**: verdade documental, ADRs e regressões finais.
3. Reconciliar `DATA_MODEL.md`, `ARCHITECTURE.md`, `TESTING.md`, `PRODUCT.md`, `ROADMAP.md` e `PRD` com o schema v12 e as regras de evidência.

## Sessão 2026-11-06 — C1-03: plano como intenção + prescrição única do dia (N-01/N-02)

**Objetivo:** ter uma única decisão diária, derivada da sessão planejada, persistida e auditável; o plano deixa de competir com o card do Hoje.

### Concluído

- **Domínio (`DailyPrescriptionEngine`):** contrato `DailyPrescription` com `PrescriptionAdjustment` (`KEEP`, `REDUCE`, `DOWNGRADE`, `REST`, `NO_PLAN`), `PrescriptionTargets` (pace, FC, zona, distância e RPE), `engineVersion` e `inputsHash` determinístico.
  - Doença/lesão ou prontidão vermelha → `REST`; plano `REST` → `REST`; semanas sem plano → `NO_PLAN` conservador com confiança baixa.
  - Qualidade só é mantida com carga `KNOWN`, ACWR 0,8–1,3, prontidão verde e espaçamento; caso contrário, `DOWNGRADE` para fácil reduzido.
  - Nenhuma decisão aumenta tipo, duração ou intensidade; sem calibração, pace/FC ficam `null` e o alvo vira RPE de 4 (fácil) ou 7 (forte). Calor desacelera o pace em 3% ou aumenta o RPE em 1, sem aumentar a duração.
  - Explicação sempre cita ≥ 1 regra e ≥ 1 evidência; novas regras `R-PRESC-01/02/03` entraram no catálogo.
- **Plano (`WeeklyPlanGenerator`/`PlanCompletionCalculator`):** `WeeklyPlanInput.today` sem default; sessões anteriores a hoje não entram (N-02); `WeeklyPlan.createdOn` é persistido e a adesão ignora dias anteriores à criação.
- **Sessão planejada (N-01):** `PlannedSession.targets` guarda zona/pace/FC/distância/RPE; a prescrição de um “Tempo” em dia verde usa exatamente os alvos do plano.
- **Dados:** `DailyPrescriptionEntity`, `DailyPrescriptionDao` e `DailyPrescriptionRepository` (`observeLatest`, `getVersions`, `append`); a versão vigente é a última do dia e uma edição acrescenta `supersedesId`, sem sobrescrever. A duplicata da mesma `date + inputsHash + engineVersion` é ignorada dentro da transação (não há índice único composto; correção documental do C1-06).
- **Migração 10→11:** cria `daily_prescriptions`, adiciona `weekly_plans.createdOn` (backfill legado = `weekStart`) e `planned_sessions.targetsJson`; planos, sessões e status preservados.
- **Caso de uso (`GetTodayPrescription`):** lê a última versão e só persiste quando o hash/versão mudam; ignora sessão `SKIPPED` como plano do dia.
- **UI:** `TodayViewModel` consome a prescrição persistida; o card “Treino de hoje” é o único com iniciar/concluir. O plano da semana vira contexto, ganha o selo “Ajustado hoje” quando a decisão difere da intenção e não tem mais o CTA de conclusão. A explicação mostra “planejado → hoje → por quê” e mantém regra/evidência/confiança auditáveis. Ações de iniciar/concluir ficam desabilitadas em `REST`.
- **Export/reset:** prescrições entram no JSON de backup; o reset apaga a tabela e a contagem.
- **Testes:** +20 no domínio (tabela de decisão, N-01/N-02, calor, sem calibração, dados ausentes, descanso planejado, hash e catálogo), +8 em `core:data` (repositório e migração 10→11) e +9 em `:app` (5 do caso de uso e 4 de UI). `Cycle1RegressionTest` perdeu o último `@Ignore`.

### Evidências

- `./gradlew check` — verde (testes, Spotless, lint, Kover e parser FIT).
- `./gradlew :app:assembleDebug` — verde.
- `./gradlew :app:recordRoborazziDebug` e `:app:verifyRoborazziDebug` — verdes após regravar `today-dark`, `today-light` e `today-narrow-dark` com o card único.
- **961 testes**, 0 falhas (487 `:app`, 117 `core:data`, 330 `core:domain`, 27 `core:model`).
- `core:domain`: 97,91% de linhas, 82,45% de branches, 96,54% de instruções; parser FIT 93,01% de branches.
- Schema `11.json` exportado; migração 10→11 coberta com banco v10 e status `DONE` preservado.

### Decisões e observações

- **Uma decisão por dia:** `TodayViewModel` não chama mais `WorkoutRecommender`; o recomendador continua apenas como fallback conservador de `NO_PLAN` e tem cobertura própria.
- **Versão não é reescrita:** editar o check-in gera nova linha; a anterior permanece auditável. O hash inclui perfil, prontidão, carga, calor, confiança do plano e conteúdo da sessão.
- **N-02 é conservador:** plano criado na quinta não inventa segunda–quarta; a semana pode ter menos dias e a progressão não é liberada sem histórico suficiente.
- **Alvos compartilhados:** plano e prescrição usam o mesmo `PrescriptionPolicy`, então não divergem por construção.
- **Sem migration destrutiva, sem dependência nova.**
- **Flaky observado:** `TutorialSystemBackTest` falhou uma vez em execução completa por exceção de banco de outro teste (`no such table: athlete_profile`), passou isolado e nas execuções seguintes; `MetricsImportViewModelTest` estourou timeout uma vez e passou isolado. Nenhum foi mascarado, removido ou teve assert reduzido.

### Próximo passo

1. Revisar C1-03 com o usuário 0, especialmente o `DOWNGRADE` de longão/qualidade em âmbar e o piso de 20 min.
2. Seguir para **C1-04**: conclusão com evidência e vínculo atividade ↔ sessão (migração 11→12).
3. Manter `docs/PLANO_TECNICO_CICLO_1.md` e o ADR 0023 atualizados a cada gate.

## Sessão 2026-11-06 — C1-02: cobertura de carga, ACWR desconhecido e gate de qualidade

**Objetivo:** impedir que ausência ou desatualização de dados vire permissão para treino forte ou progressão.

### Concluído

- **Domínio (`TrainingLoadEngine`):**
  - `ActivitySourceStatus` com última importação e limite de 14 dias.
  - `LoadCoverage` dos últimos 28 dias: dias com carga, dias de descanso observado e dias sem dado.
  - `LoadDataState` (`KNOWN`, `INSUFFICIENT`, `STALE`) no `LoadSnapshot`.
  - `acwr` só é exposto com fonte fresca e ≥ 8 dias com carga; dias sem atividade só são descanso com fonte fresca.
- **Plano (`WeeklyPlanGenerator`):**
  - Qualidade exige `KNOWN`, ACWR 0,8–1,3, prontidão verde e espaçamento; desconhecido, insuficiente ou velho bloqueia.
  - Rationale diferencia “faltam dados de carga”, “dados desatualizados”, ACWR alto e ACWR baixo.
  - Confiança cai um nível quando a carga não é confiável; `R-DATA-01`/`EV-IMPELLIZZERI-2020` entram no plano.
- **Recomendador (`WorkoutRecommender`):** removeu `acwr ?: 0.0`; carga desconhecida/velha mantém o dia fácil, reduz a confiança e explica a lacuna.
- **Dados:** `ActivityRepository.lastImportedAt()`/`observeLastImportedAt()` via `MAX(importedAt)`; sem migração de Room.
- **App:** Hoje e Tendências passam a frescor da fonte; card de carga mostra “dados de carga insuficientes/desatualizados”; explicação lista a lacuna; catálogo ganhou `EV-IMPELLIZZERI-2020`.
- **Testes:** os dois gates de C1-02 em `Cycle1RegressionTest` saíram de `@Ignore`; novos casos de cobertura, frescor, 7/8 dias, limites 0,79/0,8/1,3/1,31, carga constante, ausência no recomendador, repositório e UI.
- **Flaky:** o golden `health-light.png` variava 3 pixels na borda inferior entre execuções; o teste de screenshot de Saúde ganhou tolerância de 0,001% com justificativa, sem alterar a imagem.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug` — verde.
- **924 testes**, 0 falhas (478 `:app`, 109 `core:data`, 310 `core:domain`, 27 `core:model`); 1 `@Ignore` de C1-03.
- `core:domain`: 98,05% de linhas, 83,35% de branches, 96,87% de instruções.
- Parser FIT: 93,01% de branches (572/615).
- Golden `G-LOAD-02` atualizado para 12 dias com carga, TRIMP + rTSS e cobertura suficiente; valores conferidos por implementação independente em Python.

### Decisões e observações

- ACWR continua sendo **flag**, nunca meta ou diagnóstico; `EV-IMPELLIZZERI-2020` documenta as limitações.
- O gate usa `dataState`, não `missingDays`; `missingDays` segue como contagem bruta para a série do PMC.
- Fonte fresca = última importação de atividade em até 14 dias; um período sem corrida planejado pode ser descanso, mas ausência de dados não é.
- A elegibilidade de iniciante (4 semanas, 80% com evidência, 3 corridas/semana, volume mínimo) continua proposta e depende de C1-04.
- Sem migração de schema e sem dependência nova.

### Próximo passo

1. Revisar C1-02 com o usuário 0, especialmente os limiares de 8 dias/14 dias.
2. Seguir para **C1-03**: plano como intenção + prescrição única do dia (N-01/N-02).
3. Manter o `@Ignore` de C1-03 até o gate correspondente.

## Sessão 2026-11-06 — C1-05: lixeira, reset efetivo e época de dados

**Objetivo:** fazer “apagar” significar apagar de verdade, com lixeira curta, expurgo determinístico, reset completo e proteção contra workers que recriam dados.

### Concluído

- **Lixeira de 7 dias:** `TrashPolicy` (domínio puro), `observeTrash`, `restoreActivity`, `purgeActivity`, `purgeTrashBefore` e `TrashedActivity`/`PurgedActivity` no repositório. O expurgo recebe `now` e calcula o corte internamente.
- **UI:** a tela Dados lista a lixeira com data, tempo restante, restaurar e “apagar definitivamente” com confirmação. O backup tem opção explícita “incluir itens da lixeira”, desmarcada por padrão.
- **Expurgo:** `TrashPurgeWorker` diário, agendado apenas com perfil; sem perfil é no-op. Remove a atividade, filhos, jobs de importação e arquivo bruto.
- **Reimportação:** um arquivo cuja atividade está na lixeira retorna `ImportResult.TrashConflict` e job `TRASH_CONFLICT`; a UI/notificação aponta para Dados e não restaura silenciosamente.
- **Export:** padrão exclui atividades com `deletedAt` e os jobs de importação ligados a elas; `includeTrash = true` é explícito.
- **Época de dados:** `DataEpochStore` em `SharedPreferences` próprio, preservado pelo reset; `DataWriteMutex` compartilha a janela crítica entre reset, importação e Health Connect.
- **Reset:** `DataResetCoordinator` rotaciona a época, cancela trabalhos de importação, saúde, lembretes, story e expurgo, limpa Room, staging, avatar, cache de story, preferências de tutorial/first-run/story, notificações e estado do Health Connect. Health Connect externo não é tocado.
- **Workers:** `HealthSyncService` não lê nem grava sem perfil e descarta a leitura se a época mudar; `ImportService`/`StagedImportProcessor` fazem o mesmo e limpam o staging. `MeuCoachApplication` só agenda trabalho quando existe perfil.
- **Testes:** política de 7 dias, 6d23h/7d/7d1h, lixeira/restauração/expurgo, export com e sem lixeira, conflito de reimportação, época, reset de banco/arquivos/preferências, sync sem perfil e época invalidada, além dos testes existentes atualizados.

### Evidências

- `./gradlew check :app:assembleDebug` — verde.
- `./gradlew :app:verifyRoborazziDebug` — verde (goldens de Dados regravados para a opção de lixeira).
- Suíte total: **909 testes**, 0 falhas (476 `:app`, 108 `core:data`, 298 `core:domain`, 27 `core:model`); 3 `@Ignore` esperados de C1-02/C1-03.
- Nenhuma migração de Room, dependência nova ou alteração de schema.

### Decisões e observações

- **Sem migration:** a coluna `deletedAt` já existia; o expurgo usa índice implícito e não exigiu schema novo.
- **Evento de evidência:** o aceite 8 do plano depende de C1-04 (`session_executions`/`completionEvidence`), que ainda não existe. A lixeira já remove a atividade de todo uso normal; o vínculo será suspenso/removido quando C1-04 entrar.
- **Epoch é a barreira durável:** o cancelamento do WorkManager é melhor esforço; importação e saúde releem a época antes de gravar.
- **Health Connect:** o reset só limpa a cópia local e a marca d’água; nenhuma API de exclusão externa é chamada.

### Próximo passo

1. Revisão humana de C1-05 e dos goldens de Dados.
2. Após aprovação, seguir para C1-02 ou C1-03 conforme prioridade.
3. Implementar o vínculo de evidência de C1-05/C1-04 quando a sessão executada existir.

## Sessão 2026-11-06 — C1-01: tempo determinístico, fuso e virada de dia/semana

**Objetivo:** consolidar uma única noção de “hoje” e “semana”, derivada do fuso do perfil, observável na virada de dia e no resume, sem `now()` nas regras de negócio ou ViewModels.

### Concluído

- **`TimeProvider`:** criado em `app/time` com `today`, `zoneId`, `now()`, `currentDate()`, `currentWeekStart()`, `atStartOfDay()`, `nextMidnight()` e `refresh()`.
- **Implementações:** `SystemTimeProvider` (relógio + sinal de meia-noite) e `ProfileTimeProvider` (fuso do perfil com fallback explícito para o fuso do sistema antes do onboarding). Hilt fornece um único `Clock` e o `TimeProvider`; o mesmo relógio é injetado nos repositórios, `HealthSyncService`, `TutorialStore` e `FirstRunMetricsStore`.
- **Virada observável:** `today` emite na meia-noite calculada por `ZonedDateTime`, na troca de fuso do perfil e em `MainActivity.onResume()` via `TimeProvider.refresh()`. A troca de fuso também reagenda os lembretes.
- **ViewModels:** `Today`, `CheckIn`, `Trends`, `ReadinessHistory`, `MetricsImport`, `HealthConnect`, `Profile`, `FirstRunChecklist`, `Tutorial`, `Calibration`, `ActivityDetail`, `Onboarding` e `DataManagement` deixaram de chamar o relógio do sistema. `TodayViewModel` passou a recriar o plano quando a semana muda; `CheckInViewModel` conserva a data capturada às 23:59 e recarrega o formulário no novo dia.
- **Telas/formatters:** `TodayScreen`, `CheckInScreen`, `HealthContent`, `ProfileDetails` e `DataManagementScreen` recebem a data do estado/provider. `OnboardingValidator`, `ProfileLabels` e `HealthConnectCopy` não têm mais defaults de data/hora.
- **Planejamento:** `WeeklyPlanInput.weekStart` não tem mais default; `ReminderScheduler`/`ReminderWorker` usam fuso e dia do perfil. Atividades importadas gravam `zoneId`/`localDate` com o fuso vigente do perfil, preservando o instante absoluto.
- **Testes novos:** `TimeProviderTest` (relógio fixo, 23:59→00:01, domingo→segunda, mês/ano, fuso e DST) e `TimeDrivenStateTest` (save às 23:59, recarga às 00:01, fuso do perfil como fonte única, virada de semana no `TodayViewModel` e consistência plano × data local). Testes existentes de ViewModels receberam `FakeTimeProvider`.

### Evidências

- `./gradlew check` — verde (testes, Spotless, lint, Kover e parser FIT).
- `./gradlew :app:assembleDebug` — verde.
- `./gradlew :app:testDebugUnitTest` — 467 testes, 0 falhas.
- Nenhuma migração de Room, dependência nova ou alteração de schema.

### Decisões e observações

- **Domínio continua puro:** o `TimeProvider` vive no `:app`; `core:domain` recebe `today`/`weekStart` explícitos. Nenhuma data default no gerador semanal.
- **Instante × data local:** atividades continuam persistidas com instante UTC + `zoneId` + `localDate`; o instante não é reinterpretado. A data local do planejamento vem do fuso do perfil.
- **Fallback documentado:** antes do onboarding, o fuso do sistema é usado; ao salvar/editar o perfil, o fluxo muda para o fuso informado.
- **Pendência fora do escopo:** `StoryVideoExporter` ainda usa `System.currentTimeMillis()` apenas no nome do arquivo de vídeo; não participa de planejamento, carga, check-in ou virada de dia.

### Próximo passo

1. Revisão humana de C1-01 e do diff.
2. Após aprovação, iniciar C1-02 (cobertura de carga e gate de qualidade).
3. Manter os `@Ignore` de C1-02/C1-03 até o gate correspondente.

## Sessão 2026-11-06 — Etapa 3: formalização arquitetural e preparação dos testes

**Objetivo:** transformar as decisões aprovadas (D2, D3, D6 e direcionamentos do MVP) em ADRs, plano técnico e estratégia de regressão, sem implementar produção.

### Concluído

- **ADRs 0022–0026:** tempo/fuso (0022), plano × prescrição (0023), evidência de conclusão (0024), exclusão/retenção (0025) e cobertura de carga/qualidade (0026). 0022–0025 **Accepted**; 0026 **Proposed** (limiares de iniciante/cobertura ainda a validar). Nenhum ADR anterior foi alterado.
- **Plano técnico:** `docs/PLANO_TECNICO_CICLO_1.md` com arquitetura alvo, modelo planejado, N-01..N-05, sequência C1-01..C1-06, matriz de testes por tipo, dependências, riscos, rastreabilidade PRD → ADR → C1 e roteiro manual.
- **Testes preparados:** `Cycle1RegressionTest` em `core:domain` com 7 cenários: 4 verdes (disponibilidade, espaçamento de qualidade, vermelho sem qualidade, adesão ignora futuro/REST) e 3 **falhas esperadas** (`@Ignore`) para C1-02/C1-03 (ACWR nulo libera qualidade, cobertura insuficiente não bloqueia, plano no meio da semana inclui passado).
- **Documentos atualizados:** `DECISIONS.md` (índice e decisões menores), `REVISAO_E_DIRECIONAMENTO_PRODUTO.md` (Anexo C de aprovação, preservando o histórico), `PRODUCT.md` (escopo aprovado, revisão 1.1), `ROADMAP.md` (Ciclo 1 planejado, N-03 e N-05 no backlog), `PROGRESS.md`.

### Evidências

- `./gradlew :core:domain:test --tests "*Cycle1RegressionTest"` — verde; 7 cenários, 4 executados, 3 ignorados (falhas esperadas), 0 falhas.
- `./gradlew check` — verde (testes, Spotless, lint e Kover; parser FIT 93,01% de branches).
- `./gradlew :app:assembleDebug` — verde.
- Nenhuma alteração de código de produção, schema, dependência, migração ou refatoração nesta etapa.

### Decisões e observações

- **D2/D3/D6 aprovadas com condições** e formalizadas; o detalhamento de implementação fica no plano, não no ADR.
- **0026 proposto:** a direção (dado ausente não libera qualidade; ACWR é flag) está aprovada, mas os limiares (8 dias/28, frescor 14 dias, 4 semanas/80%, 15–20 km/120 min) precisam de validação científica e de C1-04.
- **N-01/N-02 entram em C1-03**; **N-03** vai para o ciclo 2; **N-04** entra em C1-02; **N-05** fica no ciclo de integração Health Connect.
- **`GUIDED`** tem contrato no ADR 0024, mas a sessão guiada persistente completa continua no ciclo 3; nenhum botão falso será criado.
- **Sem teste artificial:** cenários que dependem de classes inexistentes ficaram documentados na matriz; não foram criadas estruturas de produção só para compilar testes antecipados.

### Próximo passo

1. Aguardar autorização explícita para iniciar **C1-01** (tempo determinístico).
2. Manter os `@Ignore` de C1-02/C1-03 até que cada correção entre; no commit do gate correspondente, removê-los e habilitar o teste.
3. Revisar os limiares do ADR 0026 com dados reais antes de promover o status para Accepted.

## Sessão 2026-11-05 — Ícone do app: cronômetro + M + corredor

**Objetivo:** substituir o ícone de corredor + arco por uma marca mais lúdica e reconhecível, mantendo a linguagem escura e precisa do app.

### Concluído

- **Direção aprovada:** cronômetro como moldura, M/rota dentro do mostrador e corredor sobre o topo do M. O SVG do Gemini foi preservado; a camada lima foi separada da máscara de lima do JPG para não vazar na rota, nos pontilhados brancos ou na coroa.
- **Correções de cor:** coroa inteira lima; pontilhado direito branco; rota branca sem halo; corredor com a silhueta exata do JPG.
- **Assets de design:** `design/icon/icon_meucoach_gemini-color.svg`, `meucoach-icon-color-1024.png`, `meucoach-icon-playstore-512.png`, `meucoach-icon-adaptive-foreground.svg` e `meucoach-icon-monochrome.svg`.
- **Android:** `ic_launcher_foreground` regenerado em todas as densidades com fundo transparente e escala de 65%; `ic_launcher_monochrome` separado; `ic_launcher.xml` e `ic_launcher_round.xml` passaram a apontar o monochrome para o asset correto.
- **Design system:** seção 6.1 documenta conceito, cores, arquivos, safe zone e testes do ícone.

### Evidências

- `./gradlew check spotlessCheck :app:assembleDebug :app:verifyRoborazziDebug` — verde.
- Foreground/monochrome conferidos como RGBA transparente; Play Store 512 com fundo `#08090B`.
- Preview em máscara circular, squircle e quadrado arredondado sem corte do aro.

### Decisões e observações

- **Não redesenhar o SVG aprovado:** a base branca usa as formas do SVG do Gemini; a lima vem da separação de cor do JPG. O corredor não usa recorte retangular.
- **Safe zone:** o foreground do adaptive icon é escalado a 65%; o ícone de 512 continua full-bleed.
- **Monochrome separado:** corrige o ícone temático que antes reutilizava o foreground colorido.

### Próximo passo

1. Validar no aparelho o ícone no launcher, a máscara circular e o ícone temático do Android 13+.
2. Publicar o PNG 512 na ficha da Play Store quando a ficha existir.
3. Revisar o ícone em 24/48 px no aparelho; se o corredor sumir, preparar variante óptica simplificada.

## Sessão 2026-11-05 — Clareza de confiança e escala

**Objetivo:** tirar duas informações órfãs da UI: a confiança do plano da semana e a nota do eixo de economia em Tendências.

### Concluído

- **“Por que este plano?”:** o badge solto de confiança saiu do cabeçalho do plano. O card mantém só um aviso curto quando os dados são parciais (“Dados de recuperação parciais: a semana segue conservadora.”) e ganhou o botão que abre um sheet com rationale, confiança, dados usados/ausentes, regras e evidências.
- **Confiança explicada:** o sheet diz que a confiança é capturada quando a semana é montada, fica no máximo média e não muda durante a semana; novos dados entram no plano seguinte.
- **Economia com escala clara:** a nota do eixo mudou para “Escala ampliada (X a Y m/batimento) para destacar diferenças pequenas entre corridas” e passou para logo abaixo do gráfico, antes das estatísticas. O eixo agora carrega a unidade (`m/b`) e o `contentDescription` inclui o intervalo.
- **Catálogo:** `R-PLAN-03`, `R-PLAN-04` e `R-PLAN-05` ganharam títulos legíveis, evitando IDs brutos no sheet do plano.
- **Testes:** +1 em `core:domain` (confiança baixa preservada) e +3 em `:app` (sheet do plano, aviso de dados parciais e ausência do aviso com confiança média). Total: **872 testes**.
- **Goldens:** `today-*` e `trends-efficiency-dark` regravados; `trends-readiness-dark` mudou apenas pelo deslocamento causado pela nota acima.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug` — verde.
- Lint: 0 erros; 38 avisos não bloqueantes.
- Nenhuma mudança de schema, dependência ou regra de treino.

### Decisões e observações

- **Confiança não é decisão:** como no sheet do treino, a referência fica a um toque e não compete com o plano.
- **Valor histórico:** a confiança do plano continua sendo a da criação da semana; a UI agora diz isso em vez de sugerir que ela acompanha os dados de hoje.
- **Escala honesta:** o aviso continua existindo, mas agora explica o efeito (diferenças pequenas ficam visíveis) e fica junto do gráfico que ele descreve.

### Próximo passo

1. Validar o sheet do plano e a nota do eixo em aparelho, incluindo fonte 200%.
2. `P1-12`: validação E2E com 4 semanas de uso real.
3. Avaliar no futuro uma confiança de plano baseada em disponibilidade/calibração/histórico, separada da prontidão (exige ADR).

## Sessão 2026-11-05 — Saúde Fase 3: frescor e fontes

**Objetivo:** fechar a leitura de “os dados de hoje ainda não chegaram” e dar um caminho de diagnóstico para o Health Connect.

### Concluído

- **Aviso de frescor:** quando o Health Connect está conectado, existe histórico e a última métrica é anterior a hoje, a aba Saúde mostra “Os dados de hoje ainda não chegaram”, explicando que o app já tenta atualizar sozinho e que o relógio pode não ter sincronizado ainda. O card desaparece sozinho quando o dado do dia chega.
- **Abrir Health Connect:** o card de fontes ganhou o link “Abrir Health Connect”, que usa `HealthConnectClient.getHealthConnectManageDataIntent` e cai para a loja se o provedor não estiver disponível.
- **Testes:** +5 em `:app` (hint presente/ausente, link de configurações e dois goldens novos). Total: **868 testes**.
- **Goldens:** `health-today-hint-dark` e `health-sources-dark`; `health-dark/light/narrow` passaram a 760 dp para não cortar gráfico e os goldens de gráficos viraram capturas focadas/mais altas para evitar a corrida do Vico no Robolectric.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug` — verde.
- Lint: 0 erros; 38 avisos não bloqueantes.
- Nenhuma dependência nova, nenhuma migração de Room.

### Decisões e observações

- **Sem resumo extra no Hoje:** o link “Ver recuperação” já resolve a ponte; um card de métricas duplicaria a prontidão e competiria com “Iniciar”.
- **Hint honesto:** ele não afirma que houve falha de sync; só diz que o dado do dia não está no Health Connect ainda.
- **Screenshot estável:** a captura do card de fontes usa o nó do card, e a de HRV usa o card da métrica, evitando gráficos parcialmente visíveis que o Vico às vezes não desenha no Robolectric.
- **Proveniência por campo:** continua como decisão futura; a `source` do dia é a dominante.

### Próximo passo

1. Validar no aparelho o hint após um dia sem sync e o link “Abrir Health Connect”.
2. `P1-12`: validação E2E com 4 semanas de uso real.
3. Avaliar proveniência por campo só se o usuário precisar auditar cada métrica.

## Sessão 2026-11-05 — Saúde na barra e sync automático (implementação)

**Objetivo:** executar as Fases 1 e 2 do plano, tirando a Saúde do Perfil e eliminando a dependência do botão manual de sync.

### Concluído

- **5ª aba Saúde:** `AppDestination.Health` (`health`) entrou na barra inferior entre Tendências e Perfil; a rota `profile/metrics-import` saiu. A tela virou `HealthScreen`/`HealthContent`, sem seta de voltar.
- **Hoje:** o card de prontidão ganhou “Ver recuperação”, que abre a aba; o CTA principal do treino continua intacto.
- **Perfil:** o card grande de Health Connect virou um resumo compacto com pill de estado, última leitura e “Abrir”; o card de backup/privacidade ficou separado.
- **Deep links:** checklist e tutorial agora navegam para a aba Saúde pelo mesmo caminho da barra, preservando estado.
- **Store:** `HealthSyncStore` ganhou `lastSyncedAt`/`lastAttemptAt` e `reset()` limpa as três chaves; a data incremental continua compatível com instalações antigas.
- **Serviço:** `HealthSyncService` ganhou `Clock`, `Mutex`, `syncIfStale()` (frescor de 6 h) e backoff de 15 min; erro grava `lastAttemptAt`, falta de permissão não grava.
- **Coordinator:** `HealthSyncCoordinator` singleton dispara o sync no `LifecycleResumeEffect` do `MainContent`, sem abrir diálogo de permissão; `HealthConnectViewModel` passou a observar o estado compartilhado.
- **Worker:** `HealthSyncWorker` continua como fallback diário; `ExistingPeriodicWorkPolicy.KEEP` preserva o ciclo.
- **UI:** “Atualizado há X” (agora/min/h/ontem/data), “Sincronizando…” no botão e erro automático como caption passiva; a explicação passou a dizer que o app sincroniza ao abrir e uma vez por dia.
- **Testes:** +11 em `core:data` (store e serviço) e +12 em `:app` (coordinator, tela sem seta, link do Hoje, erro passivo e dois goldens do card de saúde no Perfil). Total: **863 testes**.
- **Goldens:** `health-*`, `today-*` e `profile-health-dark/light` regravados; o Perfil acima da dobra não mudou.

### Evidências

- `./gradlew check` — verde (testes, spotless, lint, Kover e parser FIT).
- `./gradlew :app:assembleDebug` — verde.
- `./gradlew :app:verifyRoborazziDebug` — verde.
- Lint: 0 erros; 38 avisos não bloqueantes.
- Nenhuma dependência nova, nenhuma migração de Room; tudo em `SharedPreferences`.

### Decisões e observações

- **5 abas é o limite:** “Tendências” é o rótulo mais longo. Os goldens cobrem 393/360 dp; falta a validação em aparelho com fonte 200%.
- **Saúde não duplica o Hoje:** o painel completo mora na aba; o Hoje só linka. Isso preserva “uma decisão por tela”.
- **Frescura de 6 h:** leitura local e barata; o worker cobre quem não abre o app. O sync manual ignora o frescor de propósito.
- **Erro passivo:** falha automática não vira toast/dialog; o app tenta de novo após o backoff.
- **Limite do Health Connect:** o app só lê o que o app de origem já escreveu. Se o relógio ainda não sincronizou, não há dado novo; a UI não inventa sucesso.
- **Screenshot de gráficos:** a captura de recuperação passou a esperar os gráficos visíveis (`mc-chart-ready` + ausência de `mc-chart-loading`) e a avançar frame a frame; o FC de repouso não sai mais em branco por corrida de composição.

### Próximo passo

1. Validar no aparelho: 5 abas em fonte 200%, link do Hoje, permissão revogada e sync após 6 h.
2. Fase 3 opcional: caption “os dados de hoje ainda não chegaram”, link para abrir o Health Connect e resumo compacto no Hoje.
3. `P1-12`: validação E2E com 4 semanas de uso real.

## Sessão 2026-11-05 — Planejamento: Saúde no topo e sync automático

**Objetivo:** tirar a tela de Saúde do Perfil e reduzir a dependência do botão manual de sync.

### Decidido

- **ADR 0020:** Saúde vira a 5ª aba (`Hoje · Corridas · Tendências · Saúde · Perfil`), com rota top-level `health` e sem seta de voltar. O Hoje ganha “Ver recuperação” no card de prontidão; o Perfil mantém só link compacto + backup.
- **ADR 0021:** o app sincroniza o Health Connect no resume quando a última leitura tem mais de 6 h, com backoff de 15 min em erro e worker diário como fallback. `HealthSyncStore` passa a guardar `lastSyncedAt`/`lastAttemptAt`; o botão manual continua sendo override.
- **Plano:** `docs/HEALTH_TOP_LEVEL_PLAN.md` com fases, arquivos, testes, goldens e aceite.
- **Fora desta sessão:** nenhuma alteração de código, nenhuma migração de Room e nenhuma dependência nova.

### Próximo passo

1. Implementar Fase 1 (IA/navegação) e regravar os goldens de Hoje, Perfil e Saúde.
2. Implementar Fase 2 (coordinator, timestamps e sync no resume).
3. Validar no aparelho: 5 abas em 360 dp/fonte 200%, sync após 6 h, permissão revogada e worker fallback.

## Sessão 2026-11-05 — Tutorial Fase C: ativação mensurável

**Objetivo:** fechar o ciclo do primeiro uso com primeira vitória, dicas contextuais, medição local exportável e versionamento.

### Concluído

- **Primeira vitória:** depois do primeiro check-in, o Hoje mostra “Prontidão atualizada — o treino de hoje já considera este check-in”. Depois da primeira importação, a lista de Corridas mostra “Corrida importada — toque para ver zonas, splits, o porquê do esforço e o story para compartilhar”.
- **Dicas contextuais únicas:** `FirstRunTipCard` + `FirstRunTipStore` mostram uma dica por tela, uma única vez: Corridas vazia, Tendências sem carga, primeira importação e primeiro check-in. O dismiss é permanente por dica.
- **Medição local:** `FirstRunMetricsStore` registra início, conclusão, pulo, maior passo, primeira ação, dismiss e conclusão do checklist. Nada sai do aparelho; os dados entram no JSON de backup como `firstRun`.
- **Versionamento:** `TutorialStore` grava `introVersion = 2` sem forçar quem já viu a v1 a repetir o tour; a revisão continua em Perfil → Ajuda.
- **Export:** `UserDataRepository.exportJson(firstRun)` aceita o objeto local e o inclui no backup; `DataManagementViewModel` injeta o store.
- **Testes:** +6 em `:app` (store de métricas, store de dicas e card de dica), além dos testes de tutorial/checklist já existentes. Total: **840 testes**.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug` — verde.
- `today-checklist-dark.png` regravado com o card de primeira vitória.
- Nenhuma dependência nova; nenhum dado sai do aparelho.

### Decisões e observações

- **Métrica local, não analytics:** o app não adiciona SDK nem envia eventos. O usuário vê e exporta os próprios dados.
- **Primeira vitória acoplada ao primeiro uso:** o card de check-in só aparece enquanto o checklist de setup está ativo; depois disso, o Hoje fica limpo.
- **Dica única, sem insistência:** cada dica é dismissível e não volta. Não há streak, prazo ou cobrança.
- **Versão sem retrabalho:** usuários antigos não são forçados a rever o tour; a versão fica registrada para futuras migrações de conteúdo.
- **Ajuste pós-teste no aparelho:** o CTA primário da cena 1 virou `Continuar`; `Ver meu treino de hoje` saiu dali para o usuário não pular as cenas 2 e 3 sem querer. A cena 2 perdeu o `Voltar` duplicado (o ícone do topo resolve) e o `Depois` da cena 3 virou botão secundário.
- **Screenshot de gráfico:** `McLineChart`/`McBarChart` expõem `mc-chart-ready` quando o modelo Vico foi aplicado; os testes de screenshot esperam essa tag em vez de tempo fixo.

### Próximo passo

1. Validação no aparelho real dos cards, do checklist e do export `firstRun`.
2. Teste moderado com 5 usuários para ajustar copy/ordem.
3. Fase D: experimentos de ordem/CTA e spotlight opcional no Hoje.

## Sessão 2026-11-05 — Tutorial Fase B: 3 cenas com dados reais e checklist

**Objetivo:** trocar o carrossel de 4 páginas por uma revelação personalizada, explicar o motor em uma cena e levar o usuário a uma primeira ação sem sair do produto.

### Concluído

- **Camada sobre o Hoje:** o tutorial deixou de ser um gate antes do app. `MainContent` mostra o Hoje e, na primeira execução, uma camada em tela cheia por cima; ao terminar ou pular, o usuário já está no lugar certo.
- **Cena 1 — plano real:** `TutorialViewModel` lê perfil, check-in, métricas, atividades e plano; se a semana ainda não existe, gera com o mesmo `WeeklyPlanGenerator` e salva. A cena mostra nome, objetivo, disponibilidade, 3 sessões reais e o próximo treino. Sem plano, mostra fallback honesto; com erro, oferece “Tentar de novo”.
- **Cena 2 — como o coach pensa:** loop “Você corre → Eu leio esforço e descanso → Você recebe o próximo treino”, com prontidão real e o título do treino recomendado.
- **Cena 3 — primeiro passo:** três ações (check-in 30 s, importar corrida, Health Connect). O CTA primário é dinâmico: check-in → importar → saúde → “Ver meu treino de hoje”, conforme o que já foi feito.
- **Checklist no Hoje:** `FirstRunChecklistViewModel` + `FirstRunChecklistCard` mostram 3 passos com progresso, deep links e “Agora não”. O dismiss persiste por 7 dias; o card desaparece quando os 3 passos terminam. Sem streak, prazo ou cobrança.
- **Design system:** `TutorialActionCard` e `TutorialProgress`; painel visual com rota/glow; haptic leve na troca de cena; largura máxima 640 dp.
- **Testes:** +19 em `:app` (cenas, CTAs dinâmicos, fallbacks, fonte 200%, back, ViewModel com plano real, checklist/ViewModel, store e goldens). Total: **834 testes**.
- **Goldens:** `tutorial-{dark,light}`, `tutorial-2-{dark,light}`, `tutorial-3-{dark,light}` e `today-checklist-dark`.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug` — verde.
- Nenhuma dependência nova; nenhum dado real em fixtures/goldens.
- O teste de importação CSV e as capturas de gráficos Vico voltaram a ser estáveis após ajuste de espera.

### Decisões e observações

- **Dados reais, não mock:** a cena 1 usa o plano que o motor já gera. Se não há dados, o tutorial não inventa números; mostra o que falta.
- **Checklist como continuação do tutorial:** a cena 3 apresenta as ações; o card no Hoje mantém o caminho visível sem prender o usuário.
- **Dicas contextuais adiadas:** as telas vazias já têm CTAs claros; a dica contextual única entra na Fase C junto com a primeira vitória e a medição local.
- **Tutorial revisível:** a rota de Perfil → Ajuda agora executa as ações da cena 3 e volta para o fluxo normal.

### Próximo passo

1. Validar no aparelho real o overlay sobre o Hoje, o back, a fonte 200% e o checklist.
2. Fase C: mensagem de primeira vitória, dicas contextuais e medição local.
3. Teste moderado com 5 usuários para ajustar copy/ordem.

## Sessão 2026-11-05 — Tutorial Fase A: base de produto, não rascunho

**Objetivo:** corrigir os problemas de plataforma e de composição do tour de 4 telas antes de reescrever a narrativa (Fase B).

### Concluído

- **Back nativo:** `BackHandler` volta uma página; na primeira, marca o tutorial como visto e sai. O tour não reaparece na próxima abertura.
- **Progresso legível:** dots de 8 dp viraram `TutorialProgress`, uma barra segmentada em `core:designsystem` com `progressBarRangeInfo` e descrição “Passo X de 4” para TalkBack.
- **Ação estável:** o CTA primário ocupa a largura toda em todas as páginas; “Voltar” virou ícone de 48 dp no topo, eliminando o salto de layout. “Pular” passou para `textSecondary`.
- **Composição:** o visual ganhou peso próprio (`weight`) com painel, rota da marca, glow sutil e pill de contexto; o texto fica ancorado logo abaixo, sem o vazio central da versão anterior.
- **Responsivo:** conteúdo limitado a 640 dp e centralizado em tablets; o texto rola quando a fonte cresce.
- **Strings:** todo o texto do tutorial saiu do Kotlin para `strings.xml`, com `tutorial_step_a11y` para leitura por voz.
- **Permissão no contexto:** `MainActivity` não pede mais `POST_NOTIFICATIONS` no primeiro frame. Perfil → Ajuda ganhou `NotificationReminderCard`, que explica frequência e só abre o diálogo do sistema quando o usuário toca em “Ativar lembretes”; negativa mantém o caminho para as configurações.
- **Testes:** +2 no `TutorialScreenTest` (progresso e fonte 200%), +1 `TutorialSystemBackTest`, +6 goldens (páginas 2–4 dark/light) e +1 no `ProfileScreenTest` (pré-permissão). Total: **815 testes**.
- **Estabilidade de screenshots:** as capturas de gráficos Vico passaram a esperar o modelo/layout antes do `captureRoboImage`; `MetricsImportViewModelTest` voltou a esperar o primeiro estado com `message != null` via Flow, eliminando timeout intermitente.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug` — verde.
- Goldens novos: `tutorial-2-dark`, `tutorial-2-light`, `tutorial-3-dark`, `tutorial-3-light`, `tutorial-4-dark`, `tutorial-4-light`; `tutorial-dark`/`tutorial-light` regravados.
- Nenhuma dependência nova; nenhum dado real em fixtures/goldens.

### Decisões e observações

- **Fase A sem reescrever a narrativa:** o conteúdo ainda é o tour de 4 telas; a Fase B troca por 3 cenas com plano real.
- **Pré-permissão em Perfil → Ajuda:** é o caminho durável; a Fase C leva o convite para o momento pós-check-in/checklist.
- **Espera explícita em screenshot:** o Vico desenha depois do primeiro layout; `waitForIdle` sozinho não garantia o frame. A espera é só de teste, não de produção.

### Próximo passo

1. Validar no aparelho real: back, fonte 200%, TalkBack e leitura do painel em dark/light.
2. Iniciar a Fase B: cena 1 com nome/objetivo/plano real, cena 2 “como o coach pensa” e cena 3 “primeiro passo”.
3. Levar o checklist “Complete seu setup” para o Hoje e as dicas contextuais.

## Sessão 2026-11-04 — Gráficos de verdade: Vico, Tendências e painel de saúde
**Objetivo:** elevar a leitura dos gráficos e transformar a tela de saúde em um painel de recuperação, sem tirar o motor determinístico do centro.

### Concluído

- **Vico 3.3.1:** adicionado `com.patrykandpatrick.vico:compose` (Apache 2.0) ao `:core:designsystem`. Nenhuma feature importa Vico diretamente; `McLineChart` e `McBarChart` encapsulam eixos, unidade, tooltip, legenda, `contentDescription`, lacunas e estado vazio.
- **Tendências redesenhada:** volume com eixos em km e destaque da semana atual; conclusão do plano em %; PMC com **CTL/ATL/TSB** na mesma escala e eixo em pontos; prontidão em barras coloridas por nível (Baixa/Moderada/Alta); economia com média de 7 dias, variação e eixo ampliado com aviso explícito; heatmap com células inativas visíveis.
- **Saúde diária (nova visão):** a tela `profile/metrics-import` virou um painel antes das fontes: hero com sono, FC de repouso, HRV e SpO₂, cobertura de dias e seleção 7/30/60 dias. Cada métrica tem gráfico de linha com média móvel de 7 dias, base individual ou faixa-alvo, resumo numérico e “Ver tabela” com os últimos 7 dias.
- **Domínio puro:** `DailyMetricsTrendsCalculator` (core:domain/health) filtra a janela, preserva lacunas, calcula média móvel de 7 dias, base do período, variação, cobertura e faixas de sono (7–9 h) e SpO₂ (95–100%). RHR e HRV usam a base individual, nunca um alvo inventado.
- **Sem zero inventado:** dia sem medição continua ausente; a linha quebra em vez de ligar dois pontos distantes. O eixo dinâmico de RHR/HRV/economia vem acompanhado da nota de escala ampliada.
- **Acessibilidade:** todo gráfico novo tem `contentDescription` com resumo e fonte; a tabela por métrica é a alternativa textual; cores de prontidão continuam acompanhadas do rótulo.
- **Testes:** 8 do calculador de tendências, 4 da visão de saúde, 5 goldens (saúde dark/light/estreito, gráficos de saúde e recuperação), 3 goldens de Tendências com dados completos e 1 teste de ViewModel para janela/trends. Total: **805 testes**.
- **Docs:** `DESIGN_SYSTEM` §7/§8/§13, `TESTING` §3/§4, `ROADMAP` P1-07/P1-09 e addendum do `ADR-0007` com Vico 3.3.1 e alternativas rejeitadas.

### Evidências

- `./gradlew check` — testes, spotless, lint e cobertura verdes.
- `./gradlew koverVerify` — `core:domain` 98,16% de linhas (2.723/2.774), 83,39% de branches (2.098/2.516), 96,88% de instruções (20.088/20.736); parser FIT 93,01% de branches.
- Goldens novos: `health-dark`, `health-light`, `health-narrow-dark`, `health-charts-dark`, `health-recovery-dark`, `trends-charts-dark`, `trends-volume-dark`, `trends-efficiency-dark`, `trends-readiness-dark`; `trends-dark`/`trends-light` regravados.
- Lint: 0 erros.

### Decisões e observações

- **Vico, não várias bibliotecas:** Vico 3.3.1 ficou como único motor cartesiano por ser mantido, multiplataforma, Apache 2.0 e já previsto no ADR-0007. Compose Charts, KoalaPlot, YCharts e MPAndroidChart foram avaliados e rejeitados (controle de eixos, manutenção ou base View).
- **Canvas continua onde a marca manda:** gauge de prontidão, barra de zonas, heatmap e sparkline não foram reescritos; uma biblioteca genérica não entrega o comportamento específico desses componentes.
- **Eixo dinâmico é transparência, não truque:** RHR/HRV/economia usam zoom no intervalo dos dados e dizem isso na tela; sono e SpO₂ mantêm faixa fixa com alvo visível.
- **Painel reusa a tela de importação:** evita uma nova rota e mantém Health Connect/CSV como fontes explícitas, mas agora abaixo do que já foi medido. O empty state ainda leva a conectar/importar.
- **Locale:** eixos e tooltips formatam números em pt-BR (vírgula decimal), alinhados ao restante do app.
- **Sem LLM:** os gráficos só descrevem dados existentes; nenhum texto de recomendação é gerado por modelo.

### Próximo passo

1. Validar no aparelho real o toque (tooltip), a legibilidade em fonte 200% e o comportamento do gráfico de 26 semanas em tela estreita.
2. Revisar Tendências e Saúde com uma importação real (4 FIT/TCX sanitizados) e conferir se a escala dos dados reais não gera eixos apertados.
3. `P1-12`: validação E2E com 4 semanas de uso real.

## Sessão 2026-11-04 — Carga de treino explicada e TRIMP em minutos
**Objetivo:** fazer o usuário entender o primeiro card de Tendências e corrigir a escala dos pontos de carga.

- **Card reescrito:** a primeira carta agora diz que a carga é medida em **pontos de esforço**, calculados pela FC ou pelo ritmo, e que a comparação é com o próprio histórico. “Acumulada/Recente” virou **Base (média diária · 42d)** e **Fadiga (média diária · 7d)**; “Balanço” mostra a conta `base − fadiga`; “Carga” e “Aumento” ganharam hints `soma · 7 dias` e `7d ÷ 28d`.
- **Ajuda sob demanda:** “Entenda cada número” abre um bottom sheet com definições curtas de Base, Fadiga, Balanço (incluindo que negativo não é erro), Carga, Aumento (faixa 0,8–1,3) e Variedade.
- **Home alinhada:** o card de carga usa os mesmos rótulos/hints e o pill de ACWR passou de “vs. mês” para “vs. 4 sem” (a janela real é de 28 dias).
- **Bug de escala no TRIMP:** `TrainingLoadCalculator.trimp` multiplicava a duração em **segundos**, embora a fórmula de Banister use **minutos**. Isso inflava a carga em 60× (ex.: 30 min ≈ 3.231 em vez de ≈ 53,8 pontos) e produzia CTL/ATL/TSB e “Aumento” fora de escala. Corrigido para minutos; rTSS já estava correto.
- **Impacto:** a carga é recalculada a partir das atividades, sem migração; ACWR/monotonia são razões e não mudam de significado. Goldens `G-LOAD-01` e `G-LOAD-02` atualizados.
- **Testes:** `TrainingLoadCalculatorTest` fixa o TRIMP de 30 min em 53,85; `TrendsScreenTest` cobre o sheet de ajuda e os novos rótulos; goldens de Hoje/Tendências regravados.
- **Docs:** `SPORTS_SCIENCE` §6.1 explicita `dur(min)` e a escala de pontos de carga.

## Sessão 2026-11-04 — Prontidão sem nome de cor, volta para Hoje e atalho de perfil
**Objetivo:** tirar o nome da cor dos títulos de prontidão, consertar a volta para Hoje e abrir o Perfil pela foto.

- **Rótulos semânticos:** o gauge da Hoje e o histórico de Tendências passam de “Verde/Âmbar/Vermelho” para **Alta/Moderada/Baixa** (`readinessLabel`). A cor continua sendo o sinal visual, mas o texto se explica sozinho; o rationale de novos planos e treinos também deixou de dizer “prontidão não está verde”, “Prontidão âmbar” e “Prontidão vermelha” (planos já persistidos mantêm o texto até a próxima geração).
- **Bug da volta para Hoje:** a barra inferior usava `restoreState` também para a tela inicial. O Navigation guarda o estado da aba que foi deixada sob o id do `popUpTo`, então, depois de abrir Tendências pelo card “Ver tendências” e tocar em Hoje, ele reabria Tendências. `navigateToTab` agora nunca restaura a tela inicial (pop sem `saveState`); as demais abas continuam preservando estado.
- **Foto abre o Perfil:** o avatar da Home ganhou alvo de 44 dp com `Role.Button`, rótulo “Abrir perfil” e navega para a aba Perfil pelo mesmo caminho da barra inferior.
- **Testes:** `NavigationTest` reproduz a sequência Tendências → Hoje (inclusive entrando pelo card e saindo por outra aba) e falha com o código antigo; `TodayPlanTest` cobre o clique no avatar; racional do plano e do treino atualizados.
- **Docs:** `SPORTS_SCIENCE` §7.2 e `DESIGN_SYSTEM` §3.4 registram Alta/Moderada/Baixa como rótulo de UI.

## Sessão 2026-11-04 — Roteiro de câmera: overview → follow
**Objetivo:** animar o pitch real sem multiplicar snapshots na memória.

- **Script:** `StoryMapScript` define overview 32° e follow 42°; a troca acontece nos primeiros **0,6 s**, escondida por um fade-in a partir da cor de fundo, com reveal radial centrado no pin. O worker renderiza o overview em 1,3× (best-effort) e o follow continua vindo do staging (2,25×).
- **Render:** o overview fica sob o fade; o follow é revelado radialmente. Depois de 0,6 s só existe o follow — sem corte, sem dupla exposição e sem “pop” de nitidez.
- **Custo:** +1 snapshot e ~1–3 s de render no worker; nenhuma mudança no staging/UI. Sem rede, o overview é omitido e o follow cobre o vídeo.
- **Validação no S24 Ultra:** frames em 0,3 s (fade/overview), 0,9 s (follow) e 8 s; sem transição visível no meio do vídeo.
- **Docs:** addendum no ADR 0019 (§4) e STORY_V2 §19. 778 testes.

## Sessão 2026-11-04 — Story com câmera 3D real (projeção MapLibre portada)
**Objetivo:** trocar o "tilt falso" por perspectiva real sem perder o alinhamento da rota.

- **Projeção pura:** `StoryMapProjection` porta a cadeia de matrizes do MapLibre (FOV, perspectiva, pitch/bearing, Mercator 512); o teste top-down prova que ela reproduz o enquadramento antigo (route square, ±2 px) e o teste inclinado garante o bbox dentro da moldura.
- **Snapshot inclinado:** `MapBackgroundRenderer` usa `tilt(38°)` + bearing; `StoryPlan.geoPoints` (até 400) alimentam a rota projetada pela mesma câmera.
- **Câmera cinematográfica 2D:** `StoryPerspective` ganhou `nativeTilt`; o warp sintético é pulado no story, mantendo zoom/pan/rotação sem dupla perspectiva.
- **Pin:** foto desenhada fora da matriz da câmera, em pé, ~114 px.
- **Validação:** paridade top-down nos testes, frame real em Clássico e Satélite no S24 Ultra com a rota sobre as ruas; ADR 0019 registra o port e os limites (tilt fixo, sem terreno 3D).
- **Testes:** `StoryMapProjectionTest` (paridade, enquadramento inclinado, bearing); 778 testes.

## Sessão 2026-11-04 — Perfil com nome/foto, pin no story e leitura do coach na atividade
**Objetivo:** personalizar o app e o vídeo, e tirar o julgamento do plano de dentro do story.

- **Nome e foto:** `AthleteProfile` ganhou `name` e `avatarPath` (Room v10, migração 9→10); onboarding pede o nome (obrigatório, até 40 caracteres) e permite importar foto via `PickVisualMedia`, copiada para `filesDir/profile/avatar.jpg` (512 px, JPEG 90). O avatar substitui o “M” na Home e aparece no Perfil.
- **Pin do story:** `StoryFrameRenderer` desenha a foto como pin circular com anel lima e ponteiro; ele é desenhado **fora** da matriz da câmera (não gira com o mapa) e com ~114 px de diâmetro em 1080p. O exportador carrega a foto do perfil e a usa também na capa.
- **Leitura do coach:** saiu da prévia do story e virou um card privado na tela da atividade (“LEITURA DO COACH”, headline/prova/frase), usando `StoryVerdictCalculator` na carga da atividade.
- **Privacidade:** a foto fica em armazenamento privado do app; apagar todos os dados também remove o arquivo.
- **Testes:** pin no golden `story-avatar-dark.png`, leitura do coach no VM, nome obrigatório no onboarding, migração 9→10 e ajustes de perfis de teste; 775 testes.
- **Verificação:** `check`, `assembleDebug`, `verifyRoborazziDebug` e validação no S24 Ultra (home “Rafael”, foto e pin no vídeo).

## Sessão 2026-11-04 — Story público: destaques objetivos, mapa Terreno e aviso
**Objetivo:** o vídeo passa a fazer sentido para quem nunca viu o plano; o mapa ganha relevo e a primeira geração é explicada.

- **Sem julgamento de plano no vídeo:** saíram “longão forte demais”, “no alvo”, “leve demais” e “amanhã: 40 min leve”. O card por km agora mostra só ritmo, FC e calorias (com selo “KM MAIS RÁPIDO” quando faz sentido) e o encerramento usa `StoryHighlightsCalculator`: “2ª METADE MAIS RÁPIDA”, “RITMO CONSTANTE”, “KM MAIS RÁPIDO” ou “CORRIDA CONCLUÍDA”, com prova objetiva (ex.: “km 3 em 6:12”).
- **Leitura do coach continua privada:** o `StoryVerdictCalculator` segue no plano e aparece no card de prévia como “Leitura do coach (só você vê)”, fora do vídeo.
- **Mapa Terreno:** `StoryMapStyle.TERRAIN` adiciona `raster-dem` + `hillshade` (tiles públicos Mapzen/AWS) ao estilo `liberty`, com atribuição “Elevation Mapzen” no frame. O estilo Clássico continua o padrão.
- **Mapa Satélite:** `StoryMapStyle.SATELLITE` usa World Imagery (Esri/Maxar) com véu escuro e as mesmas labels vetoriais por cima; atribuição “Imagery © Esri · Data © OpenStreetMap”. É o estilo recomendado para cidades planas (hillshade praticamente não aparece em Natal).
- **Aviso de primeira vez:** `StoryIntroStore` mostra um cartão explicando o download de tiles (e satélite/relevo conforme o estilo), que fica em cache e não envia coordenadas exatas; “Entendi” dispensa e persiste.
- **Botão “Criar story”:** largura total e 48 dp de altura (`McPrimaryButton` ganhou `minHeight`).
- **Testes:** `StoryHighlightsCalculatorTest` (5), `StoryIntroStoreTest`, casos de estilo no VM, `OpenFreeMapStyleTest` (hillshade) e codec da paleta; goldens do story refeitos.
- **Verificação:** `check`, `assembleDebug` e `verifyRoborazziDebug` verdes.

## Sessão 2026-11-04 — Story: progresso real, foreground e encoder na GPU
**Objetivo:** destravar a geração no aparelho (0% eterno, cancelar simbólico, 3 min de espera).

- **Progresso em 0%:** `lastProgressMs = Long.MIN_VALUE` fazia `now - lastProgressMs` estourar para negativo; `setProgress` nunca era chamado. A notificação escapava pelo caso `fraction <= 0`, o que mascarava o bug.
- **Foreground service:** `StoryExportWorker` sobe como `dataSync` antes do trabalho pesado e atualiza a notificação via `setForeground` (nunca com `notify` no mesmo id — isso substituía a notificação do FGS e o sistema derrubava o job).
- **Estado restaurado:** o `init` do `ActivityDetailViewModel` sobrescrevia o estado com `ActivityDetailUiState()` novo, apagando o `Generating/Done` restaurado do WorkManager; agora usa `update {}`. `FAILED` sem `outputData` passa a ler a mensagem persistida por `StoryExportFailureStore` (o WorkManager não persiste outputData em falha).
- **Encoder na GPU:** medição no S24 Ultra: ~190 ms/frame (fill YUV 113 ms + render 75 ms). O `MediaCodec` passou a receber o frame pelo Surface (`COLOR_FormatSurface`, EGL/GLES via `StorySurfaceEncoder`) em thread única; `fill` caiu para ~1,5 ms. Filtro de cor do mapa por frame foi removido (~13 ms). Total ~42–52 ms/frame; export de 30 s caiu de ~3 min para ~50 s.
- **Validação no aparelho:** MP4 1080×1920, 30 fps, 900 frames, 30,000 s; capa correta; export concluiu com o app em segundo plano.
- **Testes:** `StoryExportFailureStoreTest` (2), `StoryNotificationTest` (1); 760 testes no total.
- **Docs:** `ADR-0018` §2.4 (GPU/foreground) e `STORY_V2` §15 (números medidos).

## Sessão 2026-11-04 — Story: mapa mais limpo e nítido (e o limite do tilt)
**Objetivo:** elevar a leitura do mapa sem quebrar o alinhamento rota/ruas.

- **Estilo outdoor:** `OpenFreeMapStyle` agora remove camadas de POI, transporte e aeroporto do `liberty` antes do snapshot; labels de cidade/rua seguem ampliados com halo. Mapa limpo, rota como único elemento colorido.
- **Nitidez:** supersampling do snapshot de 1,95× para 2,25× (2430×4320); custo estimado de ~42 MB de ARGB — validar em aparelho de RAM baixa.
- **Foco na rota:** halo radial lima acompanhando o marcador e ticks discretos por km percorrido, ambos no `Canvas` (determinísticos, sem rede).
- **Tilt nativo — bloqueio registrado:** o `MapSnapshotter` 12.3.1 aceita `tilt`/`bearing`, mas não expõe `Projection`/`getMapAsync` (confirmado no AAR). Sem projeção, um snapshot inclinado desalinharia a rota animada. Saídas mapeadas em `STORY_V2` §14 (`ADR-0015` addendum): portar a matriz de câmera do MapLibre ou assar a rota no snapshot com revelação por keyframes. Fica como spike com validação em aparelho.
- **Golden:** 7 snapshots do story regravados (halo + ticks); nenhuma mudança em tutorial/perfil.
- **Testes:** +1 em `OpenFreeMapStyleTest` (clutter removido); verificação visual do story verde.
- **Verificação:** `check`, `:app:assembleDebug` e `koverVerify` verdes.

## Sessão 2026-11-04 — Story: geração em segundo plano e cancelamento real
**Objetivo:** tirar o story do fluxo "prende a tela e o cancelar é simbólico", sem tocar no motor determinístico.

- **WorkManager (`ADR 0018`):** `exportStory()` virou `StoryExportCoordinator` → `StoryExportWorker` (`@HiltWorker`), com nome único `story-export-<activityId>` e `ExistingWorkPolicy.REPLACE`. O app pode ser fechado; o worker continuar e notificar.
- **Plano reconstruído:** `StoryPlanLoader` + `StorySessionResolver` recarregam atividade, LTHR e sessões planejadas dentro do worker; o mapa da prévia viaja como PNG em `cacheDir/stories/story-map-<id>.png` e só é re-renderizado se o cache sumir (com `Result.retry()` para falha transitória de rede).
- **Cancelamento de verdade:** `CanvasStoryVideoExporter` (novo nome da implementação; interface `StoryVideoExporter`) recebe `isCancelled` derivado do `Job`, checa antes de cada frame e antes do `END_OF_STREAM`, libera codec/muxer e apaga o MP4 parcial. `cancelStory()` chama `cancelUniqueWork` e o worker trata `CancellationException` como cancelamento.
- **Progresso e ETA:** `setProgress` a cada 600 ms com fração e `etaSec`; `StoryEtaEstimator` (puro) suaviza a taxa e faz clamp; a UI mostra “Cerca de N s restantes”, aviso “Pode sair da tela” e botão Cancelar no estado de geração.
- **Estado restaurado:** o card observa o `WorkInfo` por nome único; reabrir a tela adota `Generating`/`Done`/`Error` de um export em andamento ou recém-concluído, ignorando saídas obsoletas quando o usuário já mudou de estado.
- **Notificação:** `StoryNotifier` em canal `meucoach-story`, com deep link para o treino e respeito a `POST_NOTIFICATIONS`.
- **Testes:** 28 novos — `StoryEtaEstimatorTest` (8), `StoryPaletteCodecTest` (2), `StoryExportProcessorTest` (6), `StoryExportCoordinatorTest` (4), `ActivityDetailViewModelTest` (7 de fluxo/estado) e `ActivityDetailScreenTest` (1 de geração); o encoder real continua validado só em aparelho.
- **Verificação:** `./gradlew check`, `:app:assembleDebug` e `koverVerify` verdes; lint 0 erros.

## Sessão 2026-11-03 — Tutorial de boas-vindas (primeira abertura)

### Concluído

- **Tour de 4 telas** (`ui/tutorial`): boas-vindas (“Seu coach de corrida”), as telas do app (Hoje/Corridas/Tendências/Perfil), o que mantém o coach informado (check-in das 4 perguntas com lembrete às 18h, corridas importadas via FIT/TCX/GPX compartilhados, Health Connect, calibração por prova/teste de 30 min) e “Você no controle” (nada sai do aparelho, explicação do treino sem jargão, registrar conclusão/adiamento/pulo, exportar ou apagar dados). Ícone de página em `accentSoft`, bullets com ponto lima, dots de progresso, botões `Continuar`/`Voltar`/`Tudo pronto` e `Pular` (finaliza e marca como visto).
- **Gate de primeira execução:** quando existe perfil e o tutorial nunca foi concluído, o app abre o tour em tela cheia (sem bottom bar) antes de Hoje. Usuários antigos veem o tour uma única vez após atualizar. `TutorialViewModel` (`@HiltViewModel`) expõe `seen`; `TutorialStore` persiste a flag em SharedPreferences (`meucoach_tutorial`), no padrão do `HealthSyncStore` — sem migração de Room.
- **Rever o tutorial:** nova seção “Ajuda” no Perfil (antes de “Editar perfil”) com card “Rever o tour de boas-vindas” e botão “Rever tutorial”; rota `tutorial` esconde a bottom bar e sempre finaliza com `popBackStack`.
- **Texto alinhado ao produto:** sem jargão interno (uma menção genérica a FC/HRV/SpO₂ já traduzidas em outras telas), tom sem culpa (“o app fica conservador” quando falta dado), “Por que este treino?” como porta de explicação.
- **Testes:** 10 novos do tutorial (`TutorialStoreTest` 2, `TutorialViewModelTest` 3, `TutorialScreenTest` 3, `TutorialScreenshotTest` 2 com goldens dark/light) + 1 no perfil (card abre o callback de revisão). Call sites de `ProfileContent` atualizados; goldens de Perfil regravados.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug koverVerify` — sucesso.
- 726 testes, 0 falhas (27 `core:model`, 279 `core:domain`, 89 `core:data`, 331 `:app`).
- Lint: 0 erros; 23 avisos não bloqueantes (aviso `UseKtx` do tutorial corrigido com `prefs.edit { }`).
- Goldens novos: `tutorial-dark.png` e `tutorial-light.png`.

### Decisões e observações

- **SharedPreferences, não Room:** a flag é preferência de UI, não dado do atleta; acompanha o padrão do `HealthSyncStore` e nunca exige migração de schema.
- **Depois do onboarding, não antes:** o tour assume que o usuário já tem perfil, então explica “Calibrar” e “Health Connect” com contexto real; o onboarding continua sendo a única coisa na primeira execução sem perfil.
- **Pular = visto:** “Pular” também marca o tutorial como concluído; a revisão fica no Perfil, sem insistência.
- **Conteúdo declarativo, não interativo:** seguiram o padrão do próprio onboarding (steps declarativos) em vez de spotlight sobre cards reais — mais barato de manter e testável em CI.

### Próximo passo

1. Validar o tour no aparelho real (fluxo pós-onboarding e o acesso pelo Perfil).
2. `P1-12`: validação E2E com 4 semanas de uso real.

## Sessão 2026-11-03 — Câmera flyover do story

### Concluído

- **Câmera que segue o corredor:** `StoryMotion` agora mantém o ponto atual da rota em 43% da altura, começa no overview (1,18×/8°), mergulha em 1,8 s para 1,72×/32° e puxa de volta no primeiro 1,2 s do frame final.
- **Rotação com direção:** a rotação acompanha o heading suavizado da rota, limitado a ±22° para o snapshot ainda cobrir o 9:16; o pitch sobe até 32° e o yaw dá um roll de até 2,5°.
- **Perspectiva mais 3D:** `StoryPerspective` passou a derrubar a borda superior em direção ao horizonte, além de estreitar o topo e alargar a base. O mapa lê como plano inclinado, não apenas como trapézio.
- **Cards e encerramento:** `+0,06` de escala e `+3°` de pitch durante o card; o pullback do encerramento revela a rota inteira atrás do frame final.
- **Rota estável no zoom:** linha, glow e marcadores são compensados por `scale / cameraScale` para manter o mesmo tamanho de tela durante o voo.
- **Mapa nítido:** snapshot de 1,75× (1890×3360), escala de overview no layout e rota projetada no mesmo quadrado.
- **Testes:** `StoryMotionTest` reescrito para overview/mergulho/follow/heading/cards/pullback/teto de escala; `StoryPerspectiveTest` ganhou o caso do horizonte. Goldens do story refeitos.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug` — sucesso.
- 714 testes, 0 falhas (27 `core:model`, 279 `core:domain`, 89 `core:data`, 319 `:app`).
- Cobertura `core:domain`: 98,17% de linhas (2.622/2.671), 83,16% de branches (2.049/2.464), 96,90% de instruções (19.475/20.099).
- Lint: 0 erros; 23 avisos não bloqueantes.
- Goldens atualizados: `story-frame-dark`, `story-frame-logo-dark`, `story-map-dark`, `story-card-verdict-dark`, `story-final-dark`, `story-final-map-dark` e `story-final-verdict-dark`.

### Decisões e observações

- **Rotação limitada é intencional:** sem limite, o retângulo do snapshot gira para fora do frame e abre buracos pretos. Com 22° e 1,72× a cobertura é segura e o movimento continua evidente.
- **Zoom alto exige snapshot maior:** 1,75× mantém o mapa praticamente 1:1 no follow. Se o export no aparelho ficar lento, o primeiro ajuste é cair para 1,5×/1,55×.
- **Ainda não é terreno 3D:** a homografia inclina o plano do mapa; um flyover com relevo exigiria DEM e re-renderização do mapa, fora do escopo desta rodada.
- **Próximo passo:** gerar um vídeo real no aparelho e avaliar ritmo, legibilidade dos labels e tempo de export.

## Sessão 2026-11-03 — LTHR manual, calibrar por corrida e câmera do story

### Concluído

- **`CalibrationCandidateDetector`** (`core:domain`): aceita apenas corrida/esteira recente (≤ 8 semanas), de 25–35 min, ≥ 1 km, FC média presente, ≥ 10 amostras de FC cobrindo ≥ 60% dos samples, variação p10–p90 ≤ 30 bpm e sem pausa > 2 min. Fora disso, nenhuma oferta de calibração.
- **LTHR manual no perfil:** campo “FC no limite” no passo Rotina, validado contra 100–230 bpm, FC de repouso e FC máxima observada. Editar o perfil preserva VDOT/pace de limiar.
- **“Usar esta corrida para calibrar”:** card no detalhe da atividade quando o detector aprova; abre a calibração com distância, tempo, data e FC preenchidos e um aviso para confirmar que foi esforço máximo e contínuo. Nada é calculado nem salvo sem o toque em Calcular/Salvar.
- **Navegação:** rota `profile/calibration?activityId={activityId}`; o perfil continua abrindo a calibração sem atividade.
- **Câmera do story:** `StoryMotion` ganhou foco de zoom por card (+0,04), `pitch` de 0 → 4° → 0 e `yaw` de 0 → 2° → 0 → −2° → 0. `StoryPerspective` aplica uma homografia rasa ao mapa **e** à rota com a mesma matriz; `MAX_CAMERA_SCALE` subiu para 1,16 e o layout reserva esse push.
- **Testes:** 11 no detector, 2 no `OnboardingValidator`, 1 no `OnboardingScreen`, 2 no `CalibrationViewModel`, 2 no `ActivityDetailViewModel`, 2 no `ActivityDetailScreen`, 1 no `CalibrationScreen`, 2 no `StoryMotion` e 3 no `StoryPerspective`.

### Evidências

- `./gradlew check` — sucesso (inclui testes, spotless, koverVerify, lint e parser FIT).
- 709 testes, 0 falhas (27 `core:model`, 279 `core:domain`, 89 `core:data`, 314 `:app`).
- Cobertura `core:domain`: 98,17% de linhas (2.622/2.671), 83,16% de branches (2.049/2.464), 96,90% de instruções (19.475/20.099).
- Lint: 0 erros; 23 avisos não bloqueantes; nenhum no código novo.
- Goldens atualizados: `onboarding-routine-dark`, `profile-dark`, `profile-light`, `story-frame-dark`, `story-frame-logo-dark`, `story-map-dark`, `story-card-verdict-dark`, `story-final-verdict-dark`, `story-final-dark` e `story-final-map-dark`.
- `./gradlew :app:verifyRoborazziDebug` — sucesso.

### Decisões e observações

- **LTHR manual não é inferência:** o valor é do usuário, então não recebe `ruleId`; as recomendações continuam citando `R-CAL-01`/`R-CAL-02` quando usam a calibração.
- **Corrida elegível ainda pede confirmação:** o detector reduz o risco, mas não consegue provar que o esforço foi máximo. O app nunca grava um LTHR silenciosamente.
- **Câmera é uma aproximação consciente:** a homografia inclina o snapshot inteiro; um pitch real de MapLibre exigiria re-renderizar o mapa por frame e uma matriz de projeção própria. O alinhamento rota/ruas foi preservado e o limite está em `docs/STORY_V2.md` §12.
- **Próximo passo:** validar em aparelho real o fluxo de calibração por corrida e o movimento da câmera; se o tilt não convencer, avaliar `MapSnapshotter` com pitch na Fase D.

## Sessão 2026-11-03 — Story v2 (Fase B): veredito do coach e rota por zona

### Concluído

- **`StoryVerdictCalculator`** (`core:domain`): transforma sessão planejada + zonas de FC em manchete, prova (`% no alvo`), frase do coach, selos por km e metadados de auditoria. Alvos: fácil Z2, longão Z1–Z2, qualidade Z4–Z5; 70% do tempo medido dentro da zona conta como “no alvo”.
- **Fallbacks honestos e voltados ao público do story:** sem plano, sem LHTR ou sem FC, o veredito fica nulo e o encerramento usa “Corrida feita” + “Cada corrida conta.”, sem expor plano, calibração ou jargão interno.
- **Card por km:** selo `NO ALVO`/`FORTE DEMAIS`/`LEVE DEMAIS`, barra de splits com o km atual aceso e contador de ritmo/FC/kcal nos primeiros 0,45 s.
- **Frame final:** `TREINO CONCLUÍDO` vira overline, a manchete do veredito ganha destaque, o percentual entra abaixo e a frase do coach fecha com o próximo treino da semana.
- **Rota por zona:** `StoryZoneRun` pinta o trecho percorrido com as cores de Z1–Z5; sem LTHR, a rota continua no lima da marca.
- **Fluxo:** `ActivityDetailViewModel` carrega a sessão planejada da data local da atividade e a próxima sessão da semana antes de montar o story.
- **Testes:** 14 testes novos no domínio (alvo, acima, abaixo, longão, qualidade, descanso, sem plano, sem calibração, sem FC, selos e texto sem `ruleId`), 2 do planner, 2 do ViewModel e 2 goldens novos.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug koverVerify` — sucesso.
- 683 testes, 0 falhas (27 `core:model`, 268 `core:domain`, 89 `core:data`, 299 `:app`).
- Cobertura `core:domain`: 98,14% de linhas (2.590/2.639), 83,10% de branches (2.016/2.426), 96,89% de instruções (19.248/19.866).
- Goldens novos `story-card-verdict-dark.png` e `story-final-verdict-dark.png`; goldens finais existentes atualizados com o fallback “Corrida feita”.

### Decisões e observações

- **Veredito é leitura, não nota:** o selo descreve o esforço do dia (“forte demais hoje”), nunca culpa ou “errado” (`PRODUCT.md` §5.5).
- **70% é o piso:** abaixo disso, o lado com mais tempo decide o tom; empate escolhe a leitura mais gentil.
- **A próxima sessão só vira “amanhã” quando é o dia seguinte:** caso contrário, “Próximo”.
- **Sem dados, sem selo:** ausência de plano/calibração/FC reduz o veredito a esforço, sem inventar alvo.

## Sessão 2026-11-03 — Story v2 (Fase A): área segura, câmera e film look

### Concluído

- **Decisão de tom:** veredito de treinador com o número como prova — o selo grande usa “Fácil bem feito” e o percentual entra menor. Registrado em `docs/STORY_V2.md` §9.
- **Área segura do Instagram:** `StoryLayout` centraliza a geometria 9:16 e reserva 13% no topo e na base. Logo no topo, métricas em linha, atribuição OSM com fundo próprio, card até 84% da altura e assinatura “Seu treino, com contexto.” no frame final.
- **Câmera:** `StoryMotion` aplica zoom (1,08→1,13), rotação (0→2°→0) e deriva mínima ao mapa **e** à rota com a mesma matriz; o alinhamento às ruas continua exato. O fator fixo de 1,2× do mapa foi substituído pela moldura da rota.
- **Nitidez:** snapshot do mapa a 1,15× (1242×2208) desenhado reduzido; push e rotação sem borrar labels.
- **Film look:** grade de cor, grão determinístico, vinheta e sombra nas métricas para leitura sobre o mapa claro.
- **Formato e capa:** 30 fps, 10 Mbps, capa PNG 1080×1920 em `Pictures/MeuCoach` e mensagem “Vídeo e capa salvos na galeria”.
- **Performance:** buffer de pixels do encoder reutilizado (antes ~8 MB por frame) e grão aplicado pelo exporter (`StoryFilm`), fora dos goldens.
- **Testes:** `StoryLayoutTest`, `StoryMotionTest`, `StoryFilmTest`, câmera e goldens do story atualizados; teste de UI para a capa.

### Evidências

- `./gradlew spotlessCheck :app:testDebugUnitTest` — sucesso.
- Goldens `story-frame-dark`, `story-frame-logo-dark`, `story-map-dark`, `story-final-dark` e `story-final-map-dark` atualizados.

### Decisões e observações

- **A marca não pode morar no rodapé:** o rodapé é da UI do Instagram. O logo foi para o topo e a assinatura do encerramento foi para dentro do card.
- **A câmera move mapa e rota juntos:** qualquer transformação aplicada só à rota quebraria a correspondência com as ruas.
- **O card cobre a cauda da rota:** é intencional; o painel opaco evita que a linha cruze os números e mantém a leitura limpa.
- **Fase B é a que muda o patamar:** veredito por km, rota colorida por zona e HUD cinético ainda não estão implementados.

## Sessão 2026-11-03 — Perfil → Saúde e dados: explicar o Health Connect

### Concluído

- **Perfil:** a seção “Dados” virou “Saúde e dados”. O card “Alimente o coach com seu descanso” diz o benefício (prontidão e treino do dia mais certos), mostra o Health Connect com um pill de estado real (`Conectado · N dias`, `Ainda não conectado`, `Indisponível neste aparelho`) e explica em uma frase o que é o app de saúde do Android. O backup (“Exportar ou apagar dados”) saiu do mesmo bloco e ganhou um card próprio, “Seus dados, sob seu controle”.
- **Estado real no perfil:** `ProfileViewModel` combina perfil, métricas diárias e status do Health Connect (`HealthDailySource` + `HealthSyncStore`), com refresh no `onResume`; o perfil passa a provar que o coach já usa os dados, em vez de só anunciar a integração.
- **Tela de dados:** “Métricas diárias” virou “Seus dados de saúde”, com o Health Connect em primeiro lugar, um bloco “O que isso muda no treino” (prontidão, risco de exagero e um exemplo com os mesmos guardrails do motor), o CSV como alternativa explícita (“Sem Health Connect? Use um CSV”) e o diagnóstico renomeado para “O que já está salvo”.
- **Quando o Health Connect não está disponível:** a tela oferece “Instalar o Health Connect” (Play Store, com fallback web) e diz que, no Android 14+, ele já vem no sistema.
- **Vocabulário único:** `healthConnectStatusLabel` e `healthConnectLastSyncLabel` (em `ui/common`) são compartilhados pelo perfil e pela tela de dados; “variabilidade cardíaca (HRV)” e “oxigenação (SpO₂)” aparecem por extenso na primeira menção.
- **Testes:** 6 novos testes do vocabulário e casos de UI para conectar, sincronizar, instalar e exibir dias salvos; goldens de perfil atualizados com o estado conectado.

### Evidências

- `./gradlew spotlessCheck :app:testDebugUnitTest` — sucesso.
- `:app:verifyRoborazziDebug` — goldens de perfil e dados verificados.

### Decisões e observações

- **Benefício antes do mecanismo:** o usuário não precisa saber o que é Health Connect para entender que sono e recuperação mudam o treino de hoje; a definição vem logo depois, curta e sem jargão.
- **Status é prova:** um pill com “47 dias” comunica mais do que “Gerenciar métricas”; números ausentes continuam ausentes (sem `0 dias`).
- **Backup não é saúde:** misturar importar métricas com apagar tudo fazia o usuário hesitar; agora são duas intenções separadas.
- **Sem permissão não é erro:** “Ainda não conectado” convida; “Indisponível neste aparelho” explica, e o CSV permanece como caminho válido.

## Sessão 2026-11-03 — Fase 2: plano, carga, métricas e perfil

### Concluído

- **Plano da semana:** cada sessão em uma linha (dia · título · duração); pill só para “Feito”/”Pulado”. O rationale aparece em duas linhas e ganha “Ver mais/Ver menos” quando passa do preview.
- **Carga vazia:** quando não há corrida nem carga (`ctl`/`atl`/`tsb` zerados e 0 km), o card mostra “Nenhuma corrida nesta semana” e o atalho para Tendências, em vez de 0,0 km + 0/0/0.
- **Métricas diárias:** subtítulo curto, Health Connect sem pill lateral, formato CSV atrás de “Ver formato do CSV” e “Escolher CSV” como secundário — sobra um único botão lima (Sincronizar).
- **Perfil:** card “Métricas diárias” com subtítulo “Sono, FC de repouso, HRV e SpO₂ · Health Connect ou CSV” e ação “Gerenciar métricas”.
- **Testes:** formato CSV colapsado, carga vazia e rationale expansível cobertos; goldens verificados.

### Evidências

- `./gradlew check spotlessCheck koverVerify :app:assembleDebug :app:verifyRoborazziDebug` — sucesso.
- 645 testes, 0 falhas (27 `core:model`, 254 `core:domain`, 89 `core:data`, 275 `:app`).
- Cobertura `core:domain` inalterada: 98,40% de linhas, 83,65% de branches.
- Lint sem erros.

### Decisões e observações

- **Um primário por tela:** o sync é a ação lima; o CSV é a alternativa secundária.
- **Formato não é conteúdo de primeira tela:** o cabeçalho técnico do CSV fica sob demanda, mas continua acessível.
- **Zero não é estado vazio:** 0 km e 0/0/0 liam como fracasso; a mensagem explica que falta uma corrida, não que a carga é zero.
- **Rationale recolhível:** duas linhas preservam o resumo; “Ver mais” evita esconder a progressão e o deload.

### Próximo passo

1. Reimportar uma corrida e revisar os cards de Tendências e do detalhe da atividade com dados reais.
2. `P1-12`: validação E2E com 4 semanas de uso real.
3. Fase 3 do plano de apresentação: `ROADMAP.md` U-01 a U-04 (termos, check-in em uma linha, confiança do plano e revisão com corrida real).

## Sessão 2026-11-03 — Hoje: cards compactos e prontidão explicada

### Concluído

- **Sinais de prontidão sem quebra no meio da palavra:** cada sinal agora usa duas linhas (rótulo e valor + hint), com `maxLines`/ellipsis; “Variabilidade cardíaca / acima” deixou de virar “aci/ma” em 360 dp.
- **Menos ruído:** o hint “ok” do sono saiu; HRV mostra “média · 7 noites”; FC de repouso mostra “estável” quando o delta é zero (fim do “-0 bpm”) e “média mensal” quando há variação.
- **Sheet por contexto:** “Por que esta prontidão?” abre um rationale próprio, construído a partir de sono, HRV, FC de repouso e check-in; “Por que este treino?” continua com o rationale da recomendação.
- **Catálogo:** `R-READY-01`, `EV-PLEWS-2013`, `EV-BUCHHEIT-2014` e `EV-WALSH-2021` entraram no `ExplanationCatalog`, mantendo a auditoria.
- **Plano da semana:** a pill de tipo repetia o título (“Longão” ao lado de “Longão”); agora só aparece para sessões concluídas ou puladas (“Feito”/“Pulado”).
- **Golden novo:** `today-narrow-dark.png` em 360 dp, além de `today-dark`/`today-light`, para travar a regressão de largura.
- **Testes:** +4 em `:app` (título do sheet, rationale da prontidão, delta zero e pill única) e catálogo atualizado no domínio.

### Evidências

- `./gradlew check spotlessCheck koverVerify :app:assembleDebug :app:verifyRoborazziDebug` — sucesso.
- 644 testes, 0 falhas (27 `core:model`, 254 `core:domain`, 89 `core:data`, 274 `:app`).
- Cobertura `core:domain`: 98,40% de linhas, 83,65% de branches.
- Lint sem erros.

### Decisões e observações

- **Duas linhas por sinal:** o valor à direita espremia o rótulo em 360 dp; empilhar rótulo + valor/hint preserva a leitura e mantém a altura parecida.
- **“Estável” em vez de “0 bpm”:** zero é um delta válido, mas “estável” comunica melhor e evita o sinal negativo.
- **Sheet da prontidão é determinístico:** os textos vêm dos mesmos componentes do motor, sem LLM; regras e evidências continuam sob demanda.
- **Plano ainda tem rationale de 3 linhas:** reduzir isso e o card de carga vazio ficam para a Fase 2 do plano de apresentação.

### Próximo passo

1. Fase 2 do plano: compactar o plano da semana, o card de carga vazio e a tela de Métricas (um primário por tela, CSV colapsável).
2. Reimportar uma corrida e revisar os cards de Tendências e do detalhe da atividade com dados reais.
3. `P1-12`: validação E2E com 4 semanas de uso real.

## Sessão 2026-11-03 — Apagar dados zera a janela do Health Connect

### Concluído

- **Bug:** “Exportar ou apagar dados” apagava o banco, mas não a marca d'água do sync do Health Connect (`meucoach_health_sync`). Um perfil novo herdava a data do sync anterior e lia só o intervalo incremental (2 dias), em vez dos 30 dias iniciais.
- **`HealthSyncStore.reset()`** remove a data do último sync; `UserDataRepository.deleteAllData` chama o reset depois da transação.
- **Injeção:** `UserDataRepository` recebe o `HealthSyncStore` (mesmo singleton do `HealthSyncService`), garantindo que a tela e o worker usem a mesma marca.
- **Recuperação no aparelho:** com a marca antiga removida, o sync voltou a importar 30 dias (05/10 a 03/11), com 29 dias de sono, 30 de FC de repouso, 28 de HRV e 28 de SpO₂.
- **Testes:** `UserDataRepositoryTest` cobre o reset; constructores de `ActivityRepositoryTest` e `DataManagementViewModelTest` atualizados.

### Evidências

- `./gradlew check spotlessCheck koverVerify :app:assembleDebug` — sucesso.
- 640 testes, 0 falhas (27 `core:model`, 254 `core:domain`, 89 `core:data`, 270 `:app`).
- Cobertura `core:domain` inalterada: 98,39% de linhas, 83,65% de branches.
- No aparelho: “30 dias importados do Health Connect” e “30 dias salvos · última em 03/11/2026”.

### Decisões e observações

- **Reset fora da transação:** o `SharedPreferences` não participa da transação do Room; o reset acontece só depois de a exclusão do banco concluir.
- **Impacto:** apagar os dados e criar um perfil novo volta a ler a janela completa; apagar apenas um dia (`DailyMetricsRepository.delete`) não altera a marca.
- **Dias recentes incompletos são esperados:** o Health Connect ainda pode não ter sono/HRV/SpO₂ do dia corrente; ausência continua `null`.
- **Treinos não foram reimportados:** não havia `import_jobs`, arquivos em `files/imports` nem atividade após o reset. Se uma importação de FIT/TCX/GPX falhar, o próximo passo é capturar a mensagem da tela e o log do `ImportWorker`.

### Próximo passo

1. Reimportar um FIT/TCX/GPX e confirmar que a atividade volta à lista.
2. `P1-12`: validação E2E com 4 semanas de uso real.

## Sessão 2026-11-03 — Mescla de métricas diárias por fonte

### Concluído

- **`DailyMetricsMerger`** (puro, `core:domain`) mescla um dia existente com um dia recebido campo a campo.
- **Precedência:** `MANUAL` > `HEALTH_CONNECT` > `CSV`; fonte de menor precedência só preenche campos nulos e nunca apaga valores. Mesma fonte atualiza o dia, para que reimportar corrija.
- **`DailyMetricsRepository.saveAll`** lê a linha existente dentro da transação e grava a mescla, preservando `createdAt`.
- **`source`:** guarda a fonte dominante do dia. Proveniência por campo fica para um ADR futuro (exigiria schema v10).
- **Docs:** `ADR-0016`, índice de decisões, `DATA_MODEL.md` §3.5/§6 e `ROADMAP.md` (P1-09); corrigidos também dois itens desatualizados do roadmap (histórico de conclusão e “check-in com streak”, que contrariava o princípio sem culpa).
- **Testes:** 8 do merger no domínio, 2 no repositório e 1 de integração no `HealthSyncService`.

### Evidências

- `./gradlew check spotlessCheck koverVerify :app:assembleDebug` — sucesso.
- 639 testes, 0 falhas (27 `core:model`, 254 `core:domain`, 88 `core:data`, 270 `:app`).
- Cobertura `core:domain`: 98,39% de linhas, 83,65% de branches.
- Lint sem erros.

### Decisões e observações

- **Health Connect continua vencendo o CSV** em campos compartilhados, como o `DATA_MODEL.md` §6 já pedia; o CSV entra como fallback nos campos que o dia do Health Connect não trouxe.
- **Valores manuais são intocáveis** por fontes automáticas.
- **Sem mudança de schema:** a mescla acontece na leitura/gravação; nenhuma migração nova.
- **Custo:** uma leitura extra por dia dentro da transação; lotes de sync são de no máximo 30 dias.
- **Limite conhecido:** quando o dia mistura fontes, `source` registra só a dominante. Nenhuma tela exibe esse campo hoje.

### Próximo passo

1. Validar no aparelho um dia que receba CSV e sync do Health Connect.
2. `P1-12`: validação E2E com 4 semanas de uso real.
3. Se a proveniência por campo for necessária, abrir ADR com migração dedicada.

## Sessão 2026-11-03 — Apagar uma atividade pelo detalhe

### Concluído

- **Menu no detalhe:** o cabeçalho da atividade ganhou `Mais opções` → `Apagar atividade`, em coral de perigo.
- **Confirmação explícita:** dialog “Apagar esta atividade?” explica que ela sai do histórico, das tendências e do cálculo de carga, e que a ação não pode ser desfeita.
- **Soft delete:** `ActivityDetailViewModel.deleteActivity()` usa `ActivityRepository.deleteActivity` (marca `deletedAt`), mantendo a política do Room e permitindo restaurar ao reimportar o mesmo arquivo.
- **Feedback:** ao concluir, o detalhe volta para a lista; falha mostra um dialog “Não foi possível apagar”. Lista, Hoje e Tendências reagem sozinhos porque observam os Flows do repositório.
- **Testes:** `ActivityDetailViewModelTest` cobre a exclusão e o sumiço em `openActivity`; `ActivityDetailScreenTest` cobre menu, confirmação e cancelamento implícito. Goldens do detalhe atualizados com o novo menu.

### Evidências

- `./gradlew check koverVerify :app:assembleDebug` — sucesso.
- `./gradlew :app:recordRoborazziDebug :app:verifyRoborazziDebug` — sucesso.
- 628 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 85 `core:data`, 270 `:app`).

### Decisões e observações

- **Soft delete em vez de apagar em cascata:** consistente com `ActivityRepository` e com a importação (arquivo repetido pode restaurar). O export continua incluindo itens com `deletedAt`.
- **Sem “desfazer” na UI:** a confirmação é o único ponto de segurança; para recuperar, é preciso reimportar o arquivo original.
- **Ação no detalhe, não na lista:** evita exclusão acidental e mantém o menu de longo prazo para um gesto de long-press, se necessário.

### Próximo passo

1. Validar no aparelho o menu, o dialog e o retorno à lista.
2. Se a exclusão for frequente na lista, avaliar long-press com a mesma confirmação.

## Sessão 2026-11-03 — Comunicação: menos jargão, referência sob demanda

### Concluído

- **Referências científicas fora do fluxo principal:** o sheet “Por que este treino?” agora mostra `O que usei`, `O que faltou` e `Como decidi` (só títulos legíveis). Fontes e IDs brutos ficaram atrás de `Ver fontes e detalhes técnicos`, colapsado. `ActivityInsights`, `CalibrationScreen` e `TrendsScreen` não exibem mais `R-...`/`EV-...`.
- **Jargão traduzido:** `ACWR` → `Aumento`/`vs. mês`; `LTHR` → `FC no limite`; `VDOT` → `Aptidão`; `RPE` → `esforço percebido`; `Taper` → `semana de prova`; `Deload` → `semana de descarga`; `TT` → `teste de 30 min`; `Qualidade` → `treino forte`; `Pace` → `ritmo`; `Split` → `trecho`; `Hooper` → `Recuperação`; `SWC`/`baseline` → `acima`/`abaixo da média`.
- **Zonas com contexto:** `Z2` virou `Z2 · leve` na pill do treino; o detalhe da atividade mostra `Z1 · Muito leve` … `Z5 · Máximo`; as legendas de 80/20 mantêm `Fácil · Z1–Z2`.
- **Rationales do motor:** textos de prescrição e plano reescritos em linguagem de treinador (calor, carga, taper/deload, sono curto e treino forte).
- **Erros de borda:** falha de importação, séries temporais corrompidas e leitura do Health Connect deixaram de vazar mensagem técnica em inglês; agora dizem o que aconteceu e o que fazer.
- **Fim do texto de demonstração:** o rodapé “Fase 0 · dados de demonstração” saiu da tela Hoje. “Ver tendências” e o card da última corrida agora navegam de verdade (Tendências e detalhe da atividade), em vez de mostrar um toast de fase.
- **Testes:** `TodayPlanTest` cobre a divulgação progressiva (ID ausente antes do toggle, presente depois); `ActivityDetailScreenTest` e `CalibrationScreenTest` garantem ausência de IDs; goldens de Today, Trends, Atividade, Calibração, Check-in e Onboarding atualizados.

### Evidências

- `./gradlew :core:model:test :core:domain:test :core:data:test :app:testDebugUnitTest` — sucesso.
- `./gradlew spotlessCheck` — sucesso.
- `./gradlew :app:recordRoborazziDebug :app:verifyRoborazziDebug` — sucesso.
- 626 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 85 `core:data`, 268 `:app`).

### Decisões e observações

- **Auditoria preservada:** `ruleId`, `evidenceIds` e `confidence` continuam no domínio e no Room; a UI só os mostra sob demanda. Isso mantém `ADR-0004` e `PRODUCT.md` §1 sem entregar bibliografia para quem só quer treinar.
- **Confiança continua visível:** `McConfidenceBadge` permanece no card de prontidão e no plano; o que saiu foi o ID, não o nível de confiança.
- **Fonte única de explicação:** só o sheet de recomendação oferece detalhes técnicos; evita repetir referência em cinco telas.
- **Copy do domínio é copy de usuário:** rationales de `WorkoutRecommender`/`WeeklyPlanGenerator` alimentam Hoje, plano e notificação, então também passaram pela varredura.

### Próximo passo

1. Validar a compreensão com o usuário 0 (`TESTING.md` §7, meta ≥ 80%) e ajustar termos que ainda exijam explicação.
2. Verificar no aparelho o sheet com a divulgação progressiva e o novo detalhe de zonas.

## Sessão 2026-11-03 — Story: mapa vetorial em 1080p

### Concluído

- **Saída do vídeo:** 720×1280 → **1080×1920** (H.264, 24 fps, 8 Mbps), mantendo o layout 9:16 escalável.
- **Mapa vetorial:** troca do raster OSM pelo **OpenFreeMap** (`styles/liberty`, dados OSM) — sem chave, sem limite de visualizações. O `OpenFreeMapStyle` amplia e clareia os labels de lugar/rua/água, para dar para reconhecer o bairro/região em 1080p.
- **Fallback:** se o style do OpenFreeMap não baixar, o renderer usa o style raster OSM inline; o mapa claro mantém o mesmo casing da rota.
- **Contraste:** saturação 85% (antes 60%), sem scrim na animação e 80 no frame final; gradientes de topo/rodapé continuam protegendo o texto. A rota ganhou **casing escuro** e base cinza para o lima ficar legível sobre o mapa claro.
- **Timeout** do mapa: 30 s → 45 s (style + tiles vetoriais + glyphs).
- **Testes:** `OpenFreeMapStyleTest` cobre tamanho/cor dos labels, criação de `paint` e preservação de camadas não simbólicas.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug koverVerify` — sucesso.
- 625 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 85 `core:data`, 267 `:app`).
- Lint sem erros; APK de debug inalterado (~49,7 MB; o mapa é download em runtime).
- Saída do vídeo: ~30 MB para 30 s a 8 Mbps (antes ~15 MB a 4 Mbps).

### Decisões e observações

- **OpenFreeMap em vez de raster:** tiles vetoriais são nítidos em qualquer resolução e os labels podem ser ampliados, o que resolve o “não dá para distinguir a região”. O provedor é gratuito, sem chave e sem limite de requests; os dados continuam sendo OSM.
- **Custo aceito:** 1080p processa ~2,25× mais pixels por frame; o export fica mais lento e o arquivo maior, sem impacto no APK.
- **Fallback raster** é claro e também funciona com o casing; a atribuição `© OpenStreetMap contributors` cobre os dois.
- **Validação pendente no aparelho:** conferir labels reais, tempo de export e nitidez do mapa.

### Próximo passo

1. Instalar este APK e gerar um story real; avaliar o mapa vetorial e o tempo de export.
2. Se ainda faltar detalhe, aumentar o zoom (`MAP_ZOOM_FACTOR`) ou reduzir a área de labels.

## Sessão 2026-11-03 — Ajustes de UX: data, seletores, última corrida e mapa

### Concluído

- **Data de nascimento/prova:** o estado guarda só os 8 dígitos e `DateVisualTransformation` insere as barras apenas na tela (`DateMask` com `OffsetMapping`). Digitar `01111988` produz `01/11/1988`; o cursor acompanha os dígitos e apagar sobre a barra não trava mais.
- **Fuso horário e cidade:** os chips foram substituídos por campos de seleção compactos que abrem um **bottom sheet pesquisável** (busca sem acento), com lista de 56 dp, check na opção atual e “Não informar” para a cidade. O perfil mostra o rótulo amigável em vez do ID IANA.
- **Dados compartilhados:** `TimezoneOption`, `AVAILABLE_TIMEZONES`, `AVAILABLE_CITIES` e `timezoneLabel` foram para `ui/common/LocationOptions.kt`, usados pelo onboarding e pelo perfil (sem dependência entre features).
- **“Última corrida”:** o card só aparece quando existe atividade real. `TodayUiState.recentActivity` virou nullable e o fallback para os dados de demonstração foi removido — perfil novo não mostra mais corrida inventada.
- **Mapa do story:** enquadramento 20% mais próximo (`MAP_ZOOM_FACTOR = 1.2`), saturação de 60% e scrim leve (alpha 50 na animação, 90 no final), com gradientes no topo/rodapé para preservar a leitura do cabeçalho. A rota é ampliada pelo mesmo fator, mantendo o alinhamento com as ruas.
- **Testes:** digitação da data, seleção de fuso pela busca, ausência de última corrida, mapeamento do cursor e atualização dos goldens de onboarding/perfil/story com mapa.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug koverVerify` — sucesso.
- 622 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 85 `core:data`, 264 `:app`).
- Cobertura `core:domain` inalterada.

### Decisões e observações

- **Barras fora do valor:** manter a máscara no estado era a causa do cursor pulando e do backspace preso; o campo agora edita dígitos e a transformação só desenha.
- **Lista em vez de chips:** 15 fusos e 19 cidades em `FlowRow` poluíam a rotina; o sheet reduz a tela a dois campos e ainda permite buscar.
- **“Não informar”:** a cidade é opcional; antes não havia como limpar uma cidade escolhida.
- **Mapa menos apagado:** o cabeçalho ganhou um gradiente próprio em vez de escurecer o mapa inteiro; o centro do mapa e a rota ficam visíveis.
- **Dados de demonstração** continuam existindo só para previews/testes, nunca para o estado real.

### Próximo passo

1. Reinstalar o APK no aparelho e validar a digitação da data, os seletores e o story com o novo enquadramento.
2. Validação E2E com 4 semanas de uso real (P1-12).

## Sessão 2026-11-03 — P3-05 (entrega 2): mapa real no story

### Concluído

- **`MapLibre` 12.3.1 + OSM raster** (estilo inline, sem chave de API): `MapLibreBackgroundRenderer` gera um snapshot 720×1280 via `MapSnapshotter`; a linha 13.x foi descartada porque exige Vulkan (`uses-feature` obrigatório).
- **`StoryMapCameraCalculator`** (puro) alinha o `BoundingBox` ao mesmo quadrado usado pela rota, inclusive o offset vertical do cabeçalho. 5 testes cobrem centro, encaixe, zoom e geometria de preview.
- **Fluxo:** `createStory` baixa o mapa uma vez, mostra `Baixando o mapa do OpenStreetMap…` e guarda a `Bitmap` no estado `Preview`; o export reaproveita a imagem. Sem rede ou após 30 s, o story mostra erro explícito e não gera vídeo.
- **Renderer:** mapa full-bleed dessaturado a 60%, com scrim leve (alpha 50 na animação, 90 no final) e gradientes no topo/rodapé; zoom de 1,2× sobre o quadrado da rota para o mapa não parecer distante; rota base clara e atribuição `© OpenStreetMap contributors` em todo frame com mapa.
- **Manifest:** `INTERNET`/`ACCESS_NETWORK_STATE`; permissões de localização herdadas do MapLibre removidas com `tools:node="remove"`.
- **APK:** `abiFilters` em `arm64-v8a`/`x86_64`; o debug passou de ~24 MB para ~49 MB (~13 MB por ABI).
- **UI:** prévia agora mostra o mapa real; o texto do card explica que tiles do OSM são baixados e que coordenadas exatas não saem do aparelho.
- **Ícone do launcher:** o asset `design/icon/icon_mc.jpg` virou foreground adaptativo (PNGs por densidade sobre o fundo `#08090B`), substituindo o vetor “M” provisório; o `monochrome` usa a mesma silhueta.
- **Testes:** 5 da câmera, 2 novos do ViewModel (mapa no preview e falha de rede) e 2 goldens sintéticos com grade de ruas (`story-map-dark.png`, `story-final-map-dark.png`), sem dados reais.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug koverVerify` — sucesso.
- 618 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 85 `core:data`, 260 `:app`).
- Cobertura `core:domain` inalterada (98,37% de linhas).
- APK debug: ~49 MB com duas ABIs (antes ~24 MB).

### Decisões e observações

- **Versão:** `12.3.1` é a última linha OpenGL; `13.x` adiciona Vulkan obrigatório e reduziria a compatibilidade de aparelhos.
- **Tamanho acima do previsto:** o ADR estimava 3–5 MB; a biblioteca adiciona ~13 MB por ABI. Os filtros de ABI limitam o impacto, e 32 bits fica fora do MVP.
- **Mapa na prévia, não no export:** evita baixar tiles duas vezes e permite ver o enquadramento antes de gerar o vídeo.
- **Sem fallback offline silencioso:** o `ADR-0012` prometia vídeo 100% offline; o `ADR-0015` substitui isso por rede obrigatória, com erro claro. Timeout de 30 s evita travar a tela.
- **Atribuição OSM** é requisito de licença, não decoração; permanece visível mesmo com a logo no rodapé.
- **Pendente de validação real:** geração do snapshot, zoom/qualidade do OSM, alinhamento da rota, tempo de export e APK de release. O ponto de partida em emoji (§2.5) também segue pendente.

### Próximo passo

1. Validar o story com mapa em aparelho real (rede, enquadramento, export e tamanho do APK).
2. Validação E2E com 4 semanas de uso real (P1-12).

## Sessão 2026-11-03 — P3-05 (entrega 1): frame final de 3 s e bounding box

### Concluído

- **`StorySegmentType.FINAL`** encerra o vídeo com 3 s reservados **dentro** da duração alvo: com 30 s, a rota e os cards ocupam os 27 s iniciais. Alvos menores que 3 s geram só o encerramento.
- O encerramento também aparece em treinos sem km completo (sem cards), para o vídeo sempre terminar com o resumo.
- **`StoryFrameRenderer.drawFinal`** desenha o painel escuro com `TREINO CONCLUÍDO`, ritmo médio, FC média e calorias. Distância e tempo continuam no cabeçalho (sem salto de layout na transição) e a logo `MeuCoach` permanece no rodapé.
- **`StoryPlan`** ganhou `averagePace`, `averageHeartRate` e `boundingBox`. Pace e FC preferem os campos canônicos de `ImportedActivity`; quando ausentes, derivam de `durationSec / distância` e da média dos samples. Sem dado, o frame mostra `—`.
- **`BoundingBox`** calculado com todos os pontos GPS (não só a rota amostrada) para o mapa da entrega 2 não cortar extremos do traçado.
- Novo golden `story-final-dark.png` e 6 testes novos do planner (frame final, duração mínima, valores canônicos, ausência de FC e bounding box).

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug koverVerify` — sucesso.
- 609 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 85 `core:data`, 251 `:app`).
- Cobertura `core:domain` inalterada (98,37% de linhas).
- Lint sem erros.

### Decisões e observações

- **Sem dependência nova nesta entrega:** só `Canvas`. `MapLibre` e a `Bitmap` estática do mapa ficam para a entrega 2, como combinado.
- **`FINAL` sempre presente:** o §2.6 do ADR condicionava o encerramento a `splits` não vazio; o vídeo com menos de 1 km também merece fechamento. Divergência registrada na nota de implementação do ADR.
- **Cabeçalho preservado no frame final:** evita que a transição para o resumo pareça um corte de tela; o painel cobre o restante dos dados.
- **Campos canônicos primeiro:** a FC média do arquivo é mais confiável que a média dos samples; a derivação por GPS cobre fontes que não trazem o resumo.
- O ponto de partida em emoji e o fundo escurecido sobre o mapa continuam pendentes na entrega 2.

### Próximo passo

1. `P3-05` entrega 2: `MapLibre` + `Bitmap` estática do mapa a partir do `BoundingBox` + rota animada por cima.
2. Validação E2E com 4 semanas de uso real (P1-12).

## Sessão 2026-11-03 — Hoje: carga honesta quando não há dados

### Concluído

- `TodayUiState.ctl/atl/tsb` e `weeklyLoadKm` agora são **nulos** quando não existe `LoadSnapshot` (antes caíam em 24/31/−7 e 17,9 km de demonstração).
- O card **Carga da semana** mostra "Sem carga ainda" + atalho para Tendências, sem barras, km ou métricas inventadas.
- Barras semanais só aparecem quando há km real em algum dia; a linha de CTL/ATL/TSB só quando há carga calculada.
- `todayState` deixou de reutilizar `days`/`weeklyLoadKm` do estado de demonstração.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug koverVerify` — sucesso.
- 602 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 85 `core:data`, 244 `:app`).
- Golden da tela Hoje passou a usar um `LoadSnapshot` fixo, exercitando o card completo.

### Decisões e observações

- **Ausência é `null`** (`AGENTS.md` §3.7): sem carga não existe fitness, fadiga ou balanço; mostrar zero também seria mentira.
- O estado de demonstração continua existindo para previews e testes, mas não vaza mais para o estado real.
- O atalho "Ver tendências" permanece no card vazio para o usuário entender onde o dado vai aparecer.

### Próximo passo

1. `P3-05`: mapa real (OSM + MapLibre) e frame final de 3 s (ADR 0015).
2. Validação E2E com 4 semanas de uso real (P1-12).

## Sessão 2026-11-03 — P1-11: notificação de importação concluída

### Concluído

- **`ImportNotifier`** posta o resultado do worker em um canal próprio (`meucoach-imports`, importância baixa, sem som/badge).
- **Conteúdo por resultado** em `ImportNotification` (puro): sucesso, treino restaurado, duplicado e falha, com nome do arquivo, substituição, avisos e motivo do erro.
- **Deep link:** sucesso/duplicado abrem o detalhe do treino (`meucoach://activity/<id>`); falha abre a lista (`meucoach://activities`). `NotificationNavigation` reconhece os dois.
- **Worker:** `ImportWorker` notifica depois do processamento; retries de I/O continuam sem notificar.
- **Um id por vez:** importações em lote mantêm o shade limpo e o último resultado vence.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug koverVerify` — sucesso.
- 600 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 85 `core:data`, 242 `:app`).
- Novos testes: `ImportNotificationTest`, `ImportNotifierTest` e casos de rota em `NotificationNavigationTest`.

### Decisões e observações

- **Canal separado dos lembretes:** o usuário pode silenciar importação sem perder o lembrete de treino.
- **Importância baixa:** importar é uma ação que o usuário pediu; não deve interromper nem tocar.
- **Permissão:** já era pedida no primeiro launch (`MainActivity`); sem ela, o notifier simplesmente não posta.
- **Falha abre a lista**, porque ainda não existe uma tela de detalhe do job com o motivo completo.

### Próximo passo

1. `P3-05`: mapa real (OSM + MapLibre) e frame final de 3 s (ADR 0015).
2. Revisar o card de carga da tela Hoje, que ainda usa números de demonstração quando não há carga.
3. Validação E2E com 4 semanas de uso real (P1-12).

## Sessão 2026-11-03 — T-06: exportar e apagar todos os dados

### Concluído

- **Tela Dados** (`Perfil → Exportar ou apagar dados`):
  - Exporta um JSON com perfil, corridas, check-ins, métricas diárias, planos e importações.
  - Usa o seletor de documentos do Android (`CreateDocument`), sem permissão de armazenamento.
  - Apaga perfil, corridas, check-ins, métricas e planos em uma única transação, com diálogo de confirmação.
  - Mostra contagem do que foi removido e deixa claro que Health Connect e arquivos originais não são alterados.
- **`UserDataRepository`** (`core:data`) centraliza export/delete de todas as tabelas; `ActivityRepository` voltou a cuidar só de atividades.
- **DAO:** `getAll` de check-ins, `getAllPlans`/`getAllSessions`/`deleteAllPlans`/`deleteAllSessions` de planos.
- **Design system:** `McDangerButton` (borda e texto `danger`, só para ação destrutiva).
- **Testes:**
  - `UserDataRepositoryTest` cobre export de todas as tabelas e delete completo.
  - `DataManagementViewModelTest` cobre escrita do arquivo, falha de escrita e resumo do delete.
  - `DataManagementScreenTest` cobre confirmação, cancelamento e estado ocupado.
  - Goldens novos: `data-management-dark.png` e `data-management-light.png`.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug koverVerify` — sucesso.
- 591 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 85 `core:data`, 233 `:app`).
- Cobertura `core:domain`: 98,37% de linhas e 83,55% de branches (inalterada).

### Decisões e observações

- **JSON, sem ZIP:** os arquivos brutos são apagados depois da importação (`StagedImportProcessor`), então não há o que empacotar; as amostras continuam representadas pela contagem.
- **Exporta também itens com soft delete** (`getAllIncludingDeleted`), para o backup refletir o histórico real.
- **Delete é transacional:** se uma tabela falhar, nada é apagado.
- **Golden da tela Hoje** dependia da hora do dia (saudação "Bom dia"/"Boa tarde") e quebrava a cada regravação; o teste agora fixa `now = 08:00`.
- Apagar o perfil devolve o app ao onboarding, como esperado.

### Próximo passo

1. `P3-05`: mapa real (OSM + MapLibre) e frame final de 3 s (ADR 0015).
2. `P1-11`: notificação de importação concluída.
3. Revisar o card de carga da tela Hoje, que ainda usa números de demonstração quando não há carga.

## Sessão 2026-11-02 — P1-13: linguagem simples, logo no story e carga honesta

### Concluído

- **Linguagem (Hoje e Tendências):**
  - `CTL`/`ATL`/`TSB` viraram `Acumulada` (42 dias), `Recente` (7 dias) e `Balanço` (recuperação), com o mesmo rótulo nas duas telas.
  - `PMC` virou "Evolução da carga · 42 dias"; a legenda do gráfico usa "Acumulada"/"Recente".
  - `ACWR` aparece como "Proporção" com hint `ACWR` e o texto explica que é sinal de atenção, nunca meta.
  - `Monotonia` virou "Variedade"; recordes viraram "Seus melhores trechos"; consistência virou "Hábito · Dias ativos".
- **Story (P3-05):**
  - `StoryBranding` centraliza a marca em `core:designsystem` (`drawable-nodpi/logo_mc.png`).
  - O rodapé desenha a logo centralizada, com proporção preservada (3,8:1), na prévia e no vídeo.
  - Sem logo, o rodapé de texto "Gerado no aparelho · MeuCoach" continua (ADR 0015 §2.8).
  - Removidos os `println` de depuração e o `getIdentifier` reflexivo.
- **Bug corrigido:** o pill "ACWR 1,1" da tela Hoje era **hardcoded**. Agora vem de `LoadSnapshot.acwr` e desaparece quando não há histórico.
- **Onboarding:**
  - Máscara `dd/mm/aaaa` nos campos de data (nascimento e prova).
  - Data da prova passou a ser opcional (sem data não há taper; o plano continua conservador).
  - Fuso e cidade por chips, com rótulos legíveis ("Recife", "Brasília · Sudeste") mantendo o ID IANA como valor.
- **Docs:** `COMMUNICATION.md`, `GLOSSARY.md`, seções 15–17 do `DESIGN_SYSTEM.md` (tabela Do/Don't restaurada), ADR 0015 registrado em `DECISIONS.md`.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug koverVerify` — sucesso.
- 580 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 84 `core:data`, 223 `:app`).
- Cobertura `core:domain`: 98,37% de linhas (2.416/2.456), 83,55% de branches (1.895/2.268).
- Dois goldens novos: `story-frame-logo-dark.png` e `onboarding-routine-dark.png`.
- Nove goldens regravados pelas mudanças de texto.

### Decisões e observações

- Rótulo curto + `hint` substituiu descrições longas: em `McStat`, texto longo quebrava no meio da palavra ("RECUPERAÇÃ/O") e desalinhava os valores.
- Dado ausente continua `null`: sem histórico de carga não existe pill de proporção, e não há número de reserva.
- A logo fica em `drawable-nodpi` e em uma única cópia recortada; isso evita a escala automática de densidade e 40 KB duplicados no APK.
- `summarizes plan completion` falhava **só às segundas**: duas sessões na mesma data colidem na chave `(weekStart, date)`. O teste passou a ancorar na semana anterior.
- Fuso e cidade seguem em lista brasileira fixa (MVP). Usuário fora do Brasil não conclui o onboarding; registrado como T-09 no backlog.
- O card de carga da tela Hoje ainda cai nos valores de demonstração quando não há carga (`ctl`/`atl`/`tsb`); revisar junto com o T-06.

### Próximo passo

1. Exportar e apagar todos os dados na UI (T-06, critério de saída do MVP).
2. `P3-05`: mapa real (OSM + MapLibre) e frame final de 3 s (ADR 0015).
3. `P1-11`: notificação de importação concluída.

## Sessão 2026-11-02 — P3-05: evolução do story com mapa real (`ADR-0015`)

### Concluído

- `ADR-0015` criado e aceito (`docs/adr/0015-story-video-map.md`): mapa real (`OSM` + `MapLibre`) como background estático, frame final (`FINAL`, 3 s) e branding `MeuCoach`.
- `docs/DECISIONS.md` atualizado (`ADR-0012` marcado como `Substituído parcialmente`).

### Decisões e observações

- **Fonte do mapa:** `OpenStreetMap` (`tile.openstreetmap.org`) — zero custo e usado pelo mercado (`Komoot`, `AllTrails`, `Strava`). O `bounding box` é calculado localmente; só tiles da região aproximada são baixados.
- **Biblioteca:** `org.maplibre.gl:android-sdk` (`BSD-2-Clause`, `~3–5 MB` no APK), justificada pela regra 8 (`AGENTS.md`). **Ainda não foi adicionada**: a evolução não está implementada.
- **Renderização:** `Bitmap` estática do mapa gerada uma vez, antes da exportação, usada como background em todos os frames; a rota continua animada por cima (`drawPath` + `PathMeasure`), preservando o desempenho já validado.
- **Frame final:** novo `StorySegmentType.FINAL` com `distância`, `tempo`, `pace médio`, `FC média`, `kcal` e `MeuCoach`.
- **Privacidade:** a exportação passa a exigir rede para os tiles `OSM`, mas não envia coordenadas exatas (aceito pelo usuário 0, `resposta 2`).
- **Validação:** o protótipo (`P3-05`) está validado no aparelho (`resposta 8`); a evolução do `ADR-0015` ainda precisa ser implementada e validada.

### Próximos passos

1. `BoundingBox` no `StoryPlan` e no `StoryPlanCalculator`.
2. `StoryMapRenderer` para gerar a `Bitmap` estática via `MapLibre`.
3. `StoryFrameRenderer`/`StoryVideoExporter` recebendo `mapBackground` e o segmento `FINAL`.
4. Dependência `org.maplibre.gl:android-sdk` em `gradle/libs.versions.toml` e `:app`.
5. Validar no aparelho real (mapa preciso, desempenho, frame final, branding).

## Sessão 2026-10-31 — P3-05: prévia do story antes do export

### Concluído

- `StoryExportUiState.Preview(plan)` separa preparação de exportação.
- `ActivityDetailViewModel`:
  - `createStory()` calcula o plano e mostra a prévia.
  - `exportStory(palette)` inicia o MediaCodec somente após confirmação.
  - `cancelStory()` volta ao estado inicial.
- UI:
  - Card mostra um frame 9:16 da prévia, com “Gerar vídeo” e “Cancelar”.
  - O frame é renderizado com as cores/fonte do app, no ponto do primeiro card.
- Testes:
  - ViewModel: atividade com GPS prepara prévia e cancelamento volta a Idle.
  - UI: prévia visível e “Gerar vídeo” aciona o callback.
- Screenshot do frame segue válido.

### Evidências

- `./gradlew check :app:verifyRoborazziDebug` — sucesso.
- 570 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 84 `core:data`, 213 `:app`).
- Cobertura `core:domain`: 98,37% linhas / 83,55% branches.

### Decisões e observações

- O usuário vê um frame representativo antes de esperar a codificação.
- A prévia usa 360×640 para ser leve; o vídeo final continua 720×1280.
- O export ainda exige validação em aparelho real; o preview não depende do MediaCodec.

### Próximo passo

1. Testar o protótipo em aparelho real (tempo, tamanho, qualidade).
2. Avaliar 1080×1920.
3. Seguir para `P1-11`/`P1-12`.

## Sessão 2026-10-31 — P3-05: protótipo de story de treino

### Concluído

- `StoryPlanCalculator` (app):
  - Projeta a rota com `routeData` e calcula distância acumulada por Haversine.
  - Deriva splits por km com pace, FC média e kcal proporcional.
  - Monta timeline de 30 s: movimento proporcional + pausa de 2 s por card, no máximo 8 cards.
- `StoryFrameRenderer`:
  - Frame 720×1280 com cabeçalho, rota base, trecho percorrido, marcador e card do km.
  - Usa as cores do design system via `McColors.toStoryPalette()`.
- `StoryVideoExporter`:
  - MediaCodec H.264 (`COLOR_FormatYUV420Flexible`) + MediaMuxer, 24 fps.
  - Converte ARGB para YUV420 por frame, sem EGL nem dependência nova.
  - Android 10+ salva em `Movies/MeuCoach` via MediaStore; anteriores compartilham via FileProvider.
- UI:
  - Card “Story do treino” no detalhe, só quando existem amostras com GPS.
  - Estados ocioso/gerando/concluído/erro, com compartilhar e tentar novamente.
  - Provider de arquivos e intent `ACTION_SEND` com `video/mp4`.
- Testes:
  - Planner: splits, timeline, caso sem GPS e caso sem km completo.
  - ViewModel: erro claro sem rota.
  - UI: card e compartilhamento.
  - Screenshot do frame do story em dark.

### Evidências

- `./gradlew check :app:verifyRoborazziDebug` — sucesso.
- 568 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 84 `core:data`, 211 `:app`).
- Cobertura `core:domain`: 98,37% linhas / 83,55% branches.
- `story-frame-dark.png` gerado e verificado no repositório.

### Decisões e observações

- É um **protótipo**: a codificação compila e a lógica está testada, mas a qualidade/desempenho do MediaCodec precisa ser validada em aparelho real.
- Sem dependência nova; se o encoder não expõe YUV flexível, a UI mostra erro em vez de quebrar.
- A rota usa apenas amostras sanitizadas; o vídeo é gerado e compartilhado localmente.
- Próximo incremento: preview antes de exportar e, se necessário, 1080×1920.

### Próximo passo

1. Validar o protótipo em aparelho real e ajustar bitrate/tamanho.
2. Adicionar preview do story antes do export.
3. Seguir para `P1-11`/`P1-12`.

## Sessão 2026-10-31 — P1-09: sync incremental do Health Connect

### Concluído

- `HealthSyncService` compartilhado:
  - Primeira execução lê 30 dias; depois parte do último sucesso menos 1 dia.
  - Sucesso salva a data; falta de permissão, erro e indisponibilidade não avançam.
- `HealthSyncStore` em `SharedPreferences` guarda apenas a última data de sync.
- `HealthConnectViewModel` passou a usar o serviço, com mensagem de importação
  inicial vs. atualização incremental.
- `HealthSyncWorker` (`@HiltWorker`) roda diariamente via WorkManager, com
  atraso inicial de 30 min, backoff exponencial e tag própria.
- `MeuCoachApplication` agenda o worker de forma best-effort.
- Testes:
  - Serviço: janela inicial, incremental, permissões, erro e indisponibilidade.
  - ViewModel: sync manual via serviço.
  - Scheduler: work único, tag e estado enqueued.

### Evidências

- `./gradlew check :app:verifyRoborazziDebug` — sucesso.
- 562 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 84 `core:data`, 205 `:app`).
- Cobertura `core:domain`: 98,37% linhas / 83,55% branches.
- Screenshots inalterados (mudança é de background/sync).

### Decisões e observações

- A data de sync é metadado local, não dado de saúde; fica em SharedPreferences.
- Ler 1 dia antes do último sucesso captura registros atrasados do wearable.
- Sem permissão ou Health Connect indisponível, o worker não insiste; o ciclo
  diário e o botão manual continuam disponíveis.
- Dias com CSV e Health Connect ainda podem ser sobrescritos pelo sync; mesclar
  por fonte fica para uma evolução futura (ADR 0014).

### Próximo passo

1. Revisar o planejamento do P3-05 com o usuário 0.
2. `P1-11` (notificações) ou `P1-12` (E2E) conforme prioridade.
3. Mescla por fonte para dias com CSV + Health Connect, se necessário.

## Sessão 2026-10-31 — P1-10: explicabilidade do treino

### Concluído

- Domínio:
  - `ExplanationCatalog` com rótulos curtos para as regras e evidências citadas pelo motor.
  - O ID bruto é preservado; IDs não catalogados caem no fallback auditável.
- App:
  - `TodayExplanationUi` com rationale, confiança, regras, evidências, dados usados e ausentes.
  - `buildExplanation` deriva os dados usados/ausentes da prontidão, carga, calibração, calor e esteira.
  - Bottom sheet “Por que este treino?” aberto pela prontidão e pelo card do treino.
  - Botão “Ver explicação” no card do próximo treino; texto determinístico, sem LLM.
- Testes:
  - Catálogo: regras/evidências usadas pelo recomendador e fallback.
  - ViewModel: regras, evidências, confiança e lacunas de dados.
  - UI: abertura do sheet, dados usados, regras e ID auditável.
- Screenshots de Hoje regravados (novo botão “Ver explicação”).

### Evidências

- `./gradlew check :app:verifyRoborazziDebug` — sucesso.
- 556 testes, 0 falhas (27 `core:model`, 246 `core:domain`, 79 `core:data`, 204 `:app`).
- Cobertura `core:domain`: 98,37% linhas / 83,55% branches.
- `today-dark.png` e `today-light.png` atualizados e verificados.

### Decisões e observações

- O sheet mostra o ID bruto junto do rótulo; a explicação continua auditável mesmo se o catálogo evoluir.
- Dados ausentes são listados, não preenchidos com zero; isso mantém a confiança honesta.
- O mesmo sheet serve prontidão e treino porque a recomendação depende dos dois.
- Nenhum texto é gerado por LLM; o rationale é o mesmo do motor determinístico.

### Próximo passo

1. `P1-09`: sync periódico/incremental do Health Connect via WorkManager.
2. Revisar o planejamento do P3-05 com o usuário 0.
3. `P1-11`/`P1-12` conforme prioridade.

## Sessão 2026-10-31 — P1-08: histórico de check-ins

### Concluído

- ViewModel:
  - `CheckInUiState.history` com os últimos 7 check-ins, ordenados do mais recente.
  - `observeAll()` mantém o histórico atualizado após salvar ou editar.
  - O formulário de hoje continua carregado separadamente, sem duplicar a entrada.
- UI:
  - Seção “Últimos check-ins” abaixo do formulário.
  - Cada linha mostra data, as quatro notas, Hooper `/20` e pills de doença/lesão ou calor.
  - Texto explica que Hooper menor é melhor e alimenta a prontidão do dia.
  - A seção só aparece quando há histórico, preservando o estado vazio/screenshot.
- Testes:
  - ViewModel: ordem do histórico e atualização após salvar.
  - UI: histórico, Hooper, sinais de doença e calor.

### Evidências

- `./gradlew check :app:verifyRoborazziDebug` — sucesso.
- 551 testes, 0 falhas (27 `core:model`, 243 `core:domain`, 79 `core:data`, 202 `:app`).
- Cobertura `core:domain`: 98,33% linhas / 83,53% branches.
- Screenshot de check-in inalterado (histórico vazio no golden).

### Decisões e observações

- Histórico é read-only: editar continua sendo o formulário de hoje, para não reescrever dias passados.
- O check-in de hoje aparece no histórico assim que é salvo; ao editar, a mesma linha é atualizada.
- O histórico não calcula prontidão retroativa; isso continua em Tendências/Hoje com os dados disponíveis.
- Sem streak ou julgamento: é um registro, não uma cobrança.

### Próximo passo

1. Revisar o planejamento do P3-05 com o usuário 0.
2. Avaliar `P1-09` (Health Connect) e `P1-10` (explicabilidade).
3. Expandir o histórico para uma tela dedicada se o volume crescer.

## Sessão 2026-10-31 — P1-06: iniciar e trocar treino

### Concluído

- Domínio (`SessionSwapper`):
  - Troca o conteúdo de duas sessões planejadas da mesma semana.
  - Só aceita datas de hoje em diante; recusa concluídas, puladas, passadas e descanso.
  - Revalida a semana: máx. 2 qualidades, sem qualidades consecutivas e qualidade longe do longão.
  - Recalcula `weeklyMinutes`, `longRunMinutes` e `qualityCount`.
- Dados:
  - `TrainingPlanDao.updateSessionContent` e `TrainingPlanRepository.updateSessions`.
  - Atualiza somente conteúdo, preservando `status`, `skipReason` e `completedAt`.
- UI:
  - “Iniciar” abre a sessão guiada com alvos, rationale, cronômetro e “Concluir treino”.
  - “Trocar” lista os dias candidatos; trocas inválidas aparecem desabilitadas com o motivo.
  - A ação de concluir a sessão guiada marca a sessão do dia como feita.
  - `TodayScreen` recebe `today` injetável para testes determinísticos.
- Testes:
  - Domínio: troca válida, datas passadas, status, descanso, qualidade perto do longão e qualidades consecutivas.
  - Dados: atualização de conteúdo sem perder status.
  - App: sessão guiada conclui o dia; troca chama o callback com as datas corretas; ViewModel persiste a troca.

### Evidências

- `./gradlew check :app:verifyRoborazziDebug` — sucesso.
- 548 testes, 0 falhas (27 `core:model`, 243 `core:domain`, 79 `core:data`, 199 `:app`).
- Cobertura `core:domain`: 98,33% linhas / 83,53% branches.
- Screenshots de Hoje inalterados (fluxo em diálogo, sem mudança de layout).

### Decisões e observações

- A troca preserva o histórico: data e status ficam no dia, só o conteúdo muda (ADR 0013).
- A sessão guiada é local e sem GPS; concluir não inventa distância, pace ou carga.
- Se não há sessão planejada hoje, a sessão guiada informa e não marca conclusão.
- A validação no domínio é a fonte de verdade; a UI pré-valida para explicar recusas.

### Próximo passo

1. Revisar o planejamento do P3-05 com o usuário 0.
2. Expandir o histórico de conclusão para uma tela própria.
3. Próximos itens do `P1-08`/`P1-09` conforme prioridade.

## Sessão 2026-10-31 — P1-07: heatmap de consistência

### Concluído

- Domínio:
  - `ConsistencyDay` (data, km e corridas) e `ActivityTrends.heatmap`.
  - Heatmap alinhado à segunda-feira e terminando hoje, com uma coluna por semana.
  - Usa a mesma janela do filtro: 6/12/26 semanas (42/84/182 dias).
- App:
  - Mapa de consistência dentro do card de Consistência.
  - Intensidade da célula proporcional ao volume do dia; dia sem corrida fica no fundo neutro.
  - Legenda “Menos/Mais” e contagem de dias ativos.
  - Descrição de acessibilidade com janela e dias ativos.
  - Filtro de período passou a alinhar consistência e heatmap (42/84/182 dias).
- Testes:
  - Domínio: alinhamento semanal, fim em hoje, soma de km por dia e dias vazios.
  - ViewModel: heatmap não vazio e janela de 42 dias no período padrão.
  - UI: mapa, legenda, contagem e descrição de acessibilidade.

### Evidências

- `./gradlew check :app:verifyRoborazziDebug` — sucesso.
- 539 testes, 0 falhas (27 `core:model`, 238 `core:domain`, 78 `core:data`, 196 `:app`).
- Cobertura `core:domain`: 98,37% linhas / 83,60% branches.
- Screenshots existentes inalterados (golden de Tendências não inclui atividades).

### Decisões e observações

- O heatmap é descritivo: mostra hábito, não pontua, não pune e não cria streak.
- A janela de consistência padrão passou de 28 para 42 dias para casar com o filtro de 6 semanas.
- Células usam volume relativo ao maior dia da janela; dias sem corrida permanecem neutros.
- O heatmap não altera carga, prontidão ou prescrição.

### Próximo passo

1. Iniciar/trocar treino a partir do plano.
2. Revisar o planejamento do P3-05 com o usuário 0.
3. Ampliar o histórico de conclusão para uma tela própria, se necessário.

## Sessão 2026-10-31 — P1-07: eficiência pace@FC e filtros de período

### Concluído

- Domínio:
  - `PaceHeartRatePoint.efficiencyMetersPerBeat` calcula metros por batimento (velocidade média ÷ FC).
  - `ActivityTrendsCalculator` aceita janelas de consistência de 7 a 365 dias.
- App:
  - Filtro de período das atividades: **6, 12 ou 26 semanas** (`TrendsPeriod`).
  - O filtro recalcula volume, consistência e pace@FC; carga e 80/20 mantêm janelas próprias, explicadas na UI.
  - Gráfico de eficiência no card “Pace @ FC”, com média em m/batimento e eixo temporal implícito.
  - Lista das últimas corridas preservada abaixo do gráfico.
  - `TrendsViewModel.selectPeriod` cancela a carga anterior e recarrega de forma determinística.
- Testes:
  - Domínio: cálculo de eficiência, janela de 6 meses e limites de validação.
  - ViewModel: troca de período recarrega 26 semanas e consistência de 182 dias.
  - UI: chips de período, gráfico e textos de eficiência; ajustes de rolagem nos testes existentes.

### Evidências

- `./gradlew check :app:verifyRoborazziDebug` — sucesso.
- 537 testes, 0 falhas (27 `core:model`, 237 `core:domain`, 78 `core:data`, 195 `:app`).
- Cobertura `core:domain`: 98,36% linhas / 83,57% branches.
- Screenshots existentes inalterados (golden de Tendências não inclui atividades).

### Decisões e observações

- Eficiência é um **proxy descritivo** (m/batimento), não diagnóstico de economia; calor e fadiga confundem a leitura.
- O filtro é escopado às atividades; PMC (42 dias) e 80/20 (28 dias) continuam com janelas próprias e rotuladas.
- Recordes continuam considerando todo o histórico, independentemente do filtro.
- Períodos longos (26 semanas) exigem mais dados, mas não mudam nenhuma regra de prescrição.

### Próximo passo

1. Heatmap de consistência.
2. Iniciar/trocar treino a partir do plano.
3. Revisar o planejamento do P3-05 (story de treino) com o usuário 0.

## Sessão 2026-10-31 — P1-06: conclusão do plano em Tendências

### Concluído

- Domínio (`PlanCompletionCalculator`):
  - Janela de 8 semanas (segunda-domingo), com `planned`, `done`, `skipped` por semana.
  - Conta apenas sessões até hoje; sessões futuras não reduzem a semana atual.
  - Ignora descanso; semana sem plano fica com razão `null`, não 0%.
- Dados:
  - `TrainingPlanRepository.getWeeks(weekStarts)` carrega as semanas existentes em lote.
- App:
  - `TrendsViewModel` injeta o repositório de planos e expõe `planCompletion`.
  - Card “Adesão ao plano” em Tendências, com barras por semana, total concluído e puladas.
  - Texto explica que semanas sem plano ficam vazias e a semana atual conta só até hoje.
  - Sem streak, chama ou vermelho de culpa.
- Testes:
  - Domínio: janela, sessões futuras, descanso, semanas sem plano, totais e validação.
  - Dados: carregamento de múltiplas semanas e lacunas.
  - ViewModel e UI: conclusão a partir do banco e card renderizado.

### Evidências

- `./gradlew check :app:verifyRoborazziDebug` — sucesso.
- 533 testes, 0 falhas (27 `core:model`, 235 `core:domain`, 78 `core:data`, 193 `:app`).
- Cobertura `core:domain`: 98,36% linhas / 83,53% branches.
- Screenshots de Tendências inalterados (o golden não inclui plano).

### Decisões e observações

- A adesão é descritiva e serve para transparência da progressão (`R-PLAN-02`), não para punir.
- Sessões passadas ainda `PLANNED` contam como não concluídas; é o que a regra de progressão já faz.
- Sem plano na semana, a razão é `null`; a barra fica vazia em vez de sugerir 0% de adesão.

### Próximo passo

1. Gráfico de eficiência pace@FC e filtros de período.
2. Heatmap de consistência.
3. Iniciar/trocar treino a partir do plano.

## Sessão 2026-10-31 — P1-07: recordes pessoais em Tendências

### Concluído

- Domínio (`ActivityTrendsCalculator`):
  - `PersonalRecords` com maior distância, melhor pace e maior tempo.
  - Considera apenas corridas/esteira; caminhada e outros esportes ficam fora.
  - Melhor pace exige ≥ 3 km para não transformar strides ou imports parciais em recorde.
  - Usa `averagePace` e, quando ausente, `calculatedPace`; datas no fuso do perfil.
  - Recordes usam todo o histórico importado, não só a janela de consistência.
- UI (`TrendsScreen`):
  - Seção “Recordes” depois de Consistência, com rótulo, valor e data/distância.
  - Texto explícito: recorde descritivo, não prescrição, e regra dos 3 km.
  - Sem chama, streak ou gamificação.
- Testes:
  - Domínio: maior distância, melhor pace, maior tempo, exclusão de caminhada e esforço < 3 km, histórico vazio.
  - UI: card visível com valores formatados em pt-BR.

### Evidências

- `./gradlew check :app:verifyRoborazziDebug` — sucesso.
- 526 testes, 0 falhas (27 `core:model`, 231 `core:domain`, 77 `core:data`, 191 `:app`).
- Cobertura `core:domain`: 98,38% linhas / 83,33% branches.
- Screenshots de Tendências inalterados (o golden não inclui atividades).

### Decisões e observações

- Recordes são descritivos: não geram recomendação, meta ou comparação com outros usuários.
- O limite de 3 km evita que um stride ou uma atividade truncada vire “melhor pace”.
- Não há recorde de 5k/10k ainda: isso exigirá melhores esforços a partir dos samples, não da média da atividade.

### Próximo passo

1. Gráfico de eficiência pace@FC e filtros de período.
2. Heatmap de consistência.
3. Histórico de conclusão de treinos (`P1-06`).

## Sessão 2026-10-31 — P1-05: deload a cada 4ª semana

### Concluído

- Domínio (`WeeklyPlanGenerator`):
  - `WeeklyPlanInput.weekNumberInBlock` (1-based) define a posição no bloco.
  - A 4ª semana consecutiva aplica `DELOAD_FACTOR = 0.75` (−25%) em todas as sessões.
  - O tipo/intensidade é preservado: qualidade continua qualidade, com duração menor.
  - Taper tem precedência e o deload não acumula com ele.
  - Rationale cita a semana do bloco; regra `R-PLAN-04` e evidência `EV-ISSURIN-2010`.
- Dados:
  - `TrainingPlanDao.getPlanWeeksBefore` e `TrainingPlanRepository.consecutiveWeeksBefore`.
  - O bloco é ancorado em semanas consecutivas persistidas; lacuna reinicia a contagem.
  - Sem mudança de schema (continua v9); nenhuma migração nova.
- App:
  - `TodayViewModel` carrega a anterior + a posição no bloco e só gera o plano quando o histórico está pronto, evitando persistir uma semana com posição errada.
- Testes:
  - Domínio: deload na 4ª semana, volume −25%, qualidade preservada e precedência do taper.
  - Dados: contagem de semanas consecutivas com lacuna.
  - App: plano real de deload após 3 semanas persistidas.

### Evidências

- `./gradlew check :app:verifyRoborazziDebug` — sucesso.
- 524 testes, 0 falhas (27 `core:model`, 230 `core:domain`, 77 `core:data`, 190 `:app`).
- Cobertura `core:domain`: 98,36% linhas / 83,63% branches.
- Screenshots existentes seguem válidos (mudança é de domínio/plano, não de layout).

### Decisões e observações

- O bloco usa o histórico de planos como fonte de verdade, em vez de uma coluna nova no Room (ADR 0011).
- Um intervalo sem plano reinicia o bloco; é conservador e determinístico.
- Deload reduz volume, não troca a sessão de qualidade por fácil.
- A geração agora espera o histórico carregar; isso também remove a corrida que podia salvar a semana antes de ler a anterior.

### Próximo passo

1. Histórico de conclusão e streak de check-in (`P1-06`).
2. Gráfico de eficiência pace@FC e recordes em Tendências.
3. Blocos/periodização explícitos, se necessários.

## Sessão 2026-10-31 — P1-05: calor e esteira na prescrição

### Concluído

- Modelo:
  - `HeatLevel` (`NONE`/`HOT`) no `DailyCheckIn` como sinal autorreportado, não temperatura medida.
  - `AthleteProfile.treadmillAccess` como preferência durável de rotina.
- Persistência (Room v9, `MIGRATION_8_9`):
  - `daily_check_ins.heatLevel` com default `NONE`.
  - `athlete_profile.treadmillAccess` com default `0`.
  - Teste de migração 8→9 e round-trip nos repositórios.
- Prescrição diária (`WorkoutRecommender`):
  - Calor usa o extremo conservador da faixa (+3%), subordinado a FC/RPE (`R-PLAN-07`, `EV-ELY-2007`).
  - Confiança cai um nível quando há calor informado.
  - Com esteira, a justificativa lembra que a modalidade é válida no calor (`R-PLAN-08`).
- Plano semanal (`WeeklyPlanGenerator`):
  - Qualidade com acesso a esteira cita inclinação de 5–10% em RPE 8/10 (`R-PLAN-08`, `EV-DANIELS-2014`).
  - Quando há pace de limiar, prescreve também velocidade pela zona T (ex.: 9,7–10,0 km/h).
- UI:
  - Switch “Está muito calor?” no check-in, mapeado para `heatLevel`.
  - Switch “Tenho acesso a esteira” no passo de rotina do onboarding/perfil.
  - `TodayViewModel` passa o calor do check-in para a prescrição.

### Evidências

- `./gradlew check` — sucesso (testes, spotless, lint e Kover).
- `./gradlew :app:verifyRoborazziDebug` — sucesso; screenshots existentes seguem válidos.
- 519 testes, 0 falhas (27 `core:model`, 227 `core:domain`, 76 `core:data`, 189 `:app`).
- Testes novos cobrem +3% no pace, downgrade de confiança, regras/evidências, esteira com e sem pace de limiar, migração 8→9 e round-trip de persistência.

### Decisões e observações

- Calor é autorreportado no MVP; clima automático continua na Fase 2 (P2-07).
- O ajuste não reescreve o plano semanal persistido: calor muda a recomendação do dia; a qualidade guardada sempre oferece a alternativa de esteira quando há acesso.
- Inclinação fica em RPE para não inventar intensidade sem dados; velocidade só aparece com limiar calibrado.
- Dados ausentes continuam `null`; calor ausente não vira recuperação ruim.

### Próximo passo

1. Deload por blocos (`P1-05`).
2. Tela de histórico de conclusão e streak de check-in.
3. Gráfico de eficiência pace@FC e recordes em Tendências.

## Sessão 2026-10-31 — P1-07: Distribuição 80/20 por tempo

### Concluído

- `IntensityDistributionCalculator` em `core/domain/.../trends/`:
  - Mede tempo em Z1–Z2 (fácil), Z3 (moderado) e Z4–Z5 (forte), conforme `R-ZONE-02`.
  - Reaproveita `HrZoneDistributionCalculator`: intervalo sem FC fica fora do total.
  - Janela de 28 dias, quebra por semana (segunda-domingo) e semanas vazias omitidas.
  - Confiança por cobertura: < 3 sessões baixa, 3–5 média, 6+ alta.
  - `meetsEasyTarget` apenas reporta a heurística de ~80% (`R-PLAN-06`); não altera o plano.
- `TrendsViewModel`:
  - Abre os samples das corridas (`Sport.RUNNING`/`TREADMILL`) dentro da janela, somente quando há LTHR calibrado.
  - Expõe `intensity` no `TrendsUiState`.
- Tela Tendências:
  - Seção “Distribuição · 80/20” depois de Pace @ FC.
  - Barra empilhada por tempo, legenda com duração e percentual, e linhas por semana.
  - `McConfidenceBadge`, descrição de acessibilidade da barra e citação `R-ZONE-02 · EV-SEILER-2010`.
  - Estados explícitos: sem limiar calibrado e sem corrida com FC no período.
- Testes:
  - Domínio: buckets, janela, lacunas de FC, agregação semanal, confiança e validações.
  - ViewModel: distribuição a partir do blob, ausência de LTHR e ausência de carga.
  - UI: card completo, percentuais, linha semanal, confiança e estados vazios.

### Evidências

- `./gradlew check :app:assembleDebug :app:verifyRoborazziDebug` — sucesso.
- 507 testes, 0 falhas (27 `core:model`, 219 `core:domain`, 73 `core:data`, 188 `:app`).
- `core:domain`: 98,37% de linhas, 83,38% de branches, 97,08% de instruções.
- `IntensityDistributionCalculator`: 100% de linhas e branches.
- `verifyParserBranchCoverage`: 93,01% (572/615).
- Screenshots de Tendências inalterados (estado sem atividades no golden).

### Decisões e observações

- A distribuição é medida, não prescrita: o card mostra tempo por bucket e cita a heurística, sem reprovar a semana.
- A confiança descreve cobertura de dados, não qualidade do treino; é derivada do número de sessões com FC.
- Corridas e esteira entram no cálculo; outros esportes ficam fora para não misturar modalidades.
- Sem LTHR não se estima zona por FC máxima (`R-ZONE-01`); a tela pede calibração.
- Abrir blobs de 28 dias é aceitável nesta fase; se a base crescer, o próximo passo é agregar zonas na importação.

### Próximo passo

1. Calor/esteira na prescrição (`R-PLAN-07`/`R-PLAN-08`).
2. Tela de histórico de conclusão e streak de check-in.
3. Gráfico de eficiência pace@FC, recordes e filtros de período em Tendências.

## Sessão 2026-10-30 — P1-07: Tendências de atividades (parcial)

### Concluído

- `ActivityTrendsCalculator`:
  - Volume por semana (6 semanas) em km e corridas.
  - Consistência de 28 dias: dias ativos, corridas e dias desde a última.
  - Pontos pace×FC para corridas com ambos.
  - Médias de pace e FC no período.
- Tela Tendências:
  - Barras de volume semanal em Canvas.
  - Card de consistência com km e pace médio.
  - Lista “Pace @ FC” com data, pace e bpm.
- Testes de domínio, ViewModel e UI.

### Pendente

- Distribuição 80/20 por tempo — concluída em 31/10/2026.
- Pace@FC como gráfico de eficiência e recordes.
- Heatmap de consistência e filtros de período.
- Calor/esteira e deload por blocos no `P1-05`.

### Próximo passo

1. Recordes e gráfico de eficiência em Tendências.
2. Calor/esteira na prescrição.
3. Tela de histórico de conclusão e streak de check-in.

## Sessão 2026-10-30 — P1-06: Hoje e notificações (parcial)

### Concluído

- Notificações locais via WorkManager:
  - Check-in diário às 18h, apenas se ainda não feito.
  - Treino do dia às 7h, apenas se a sessão estiver `PLANNED` e não for descanso.
  - Canal "Lembretes de treino" e permissão `POST_NOTIFICATIONS` no Android 13+.
  - Toque abre Hoje ou check-in via `meucoach://today` e `meucoach://check-in`.
  - Agendamento idempotente (`enqueueUniquePeriodicWork` com `UPDATE`).
- Testes de conteúdo, delay, navegação, canal/notificação e agendamento.
- Tela Hoje com dados reais e ações de concluir/pular.

### Pendente

- Tela de histórico de conclusão.
- Check-in com streak.
- Ações Iniciar/Trocar treino.

### Evidências

- `./gradlew check :app:assembleDebug spotlessCheck :app:verifyRoborazziDebug` — sucesso.
- 488 testes, 0 falhas (27 `core:model`, 206 `core:domain`, 73 `core:data`, 182 `:app`).

## Sessão 2026-10-30 — P1-05: recomendação diária (parcial)

### Concluído

- `WorkoutRecommender` em `core/domain/.../prescription/`:
  - Doença/lesão ou prontidão vermelha ⇒ descanso.
  - Dia sem disponibilidade explícita ⇒ descanso.
  - Sono < 6 h, âmbar ou `NO_QUALITY` ⇒ fácil sem qualidade.
  - ACWR > 1,5 ⇒ fácil, sem aumento de carga.
  - Verde + calibração ⇒ fácil Z2 com pace E e FC Z2.
  - Sem calibração ⇒ fácil por RPE, confiança baixa.
  - Disponibilidade ausente ⇒ 30 min e confiança reduzida.
  - Toda recomendação cita regras, evidências e confiança.
- `WeeklyPlanGenerator`:
  - Semana segunda→domingo a partir da disponibilidade.
  - No máximo 1 qualidade, nunca adjacente ao longão.
  - Longão 25–30% do volume e ≤150 min para iniciante.
  - Sem progressão até existir histórico de conclusão.
  - Sem disponibilidade ⇒ plano vazio explícito (`R-DATA-01`).
- Plano persistido em Room v7 (`weekly_plans`/`planned_sessions`):
  - Gerado uma vez por semana e reutilizado; check-ins não reescrevem o plano.
  - `TrainingPlanRepository` com leitura observável, substituição de sessões e exclusão.
  - Migração 6→7 e testes 1→7 até 6→7.
- Status por sessão em Room v8:
  - `PLANNED`/`DONE`/`SKIPPED` + motivo e `completedAt`.
  - Ações "Concluir" e "Pular" no card do plano, com diálogo de motivo.
  - Migração 7→8 e testes de migração.
- Card "Plano da semana" na tela Hoje com dias, tipo, duração, confiança e rationale.
- Tela Hoje passa a usar a recomendação real no card de treino.
- CTL/ATL/TSB da tela Hoje vêm do motor de carga quando há atividades.
- Testes dos guardrails, alvos calibrados, RPE e limites de duração.

### Pendente

- Blocos/periodização e deload.
- Calor e esteira.
- Tela de histórico de conclusão e limite visível de 2 qualidades/semana.

### Evidências

- `./gradlew check :app:assembleDebug spotlessCheck :app:verifyRoborazziDebug` — sucesso.
- 447 testes, 0 falhas (27 `core:model`, 194 `core:domain`, 66 `core:data`, 160 `:app`).
- `core:domain`: 98,23% linhas, 82,84% branches, 96,85% instruções.

### Próximos passos

1. Plano semanal e periodização (`P1-05`).
2. Histórico de prontidão e validação do Health Connect em aparelho.
3. `P1-06`: consolidar a tela Hoje com dados reais.

## Sessão 2026-10-30 — P1-04: check-in, CSV e prontidão

### Concluído

- `DailyCheckIn` em `core:model` com validação 1–5 e Hooper sum.
- Room v5:
  - `DailyCheckInEntity`/`DailyCheckInDao` com chave por data.
  - `CheckInRepository` com observação por data, upsert transacional e `createdAt` preservado.
  - `MIGRATION_4_5` e testes 1→5, 2→5, 3→5 e 4→5.
- Prontidão a partir do check-in:
  - `toSubjectiveCheckIn`, `toReadinessSignals`, `toReadinessResult`.
  - Sinal de doença/lesão força vermelho e `NO_QUALITY`.
- Tela de check-in:
  - Quatro escalas 1–5, switch de doença/lesão, edição e salvamento.
  - Rota própria, sem barra inferior.
- Tela Hoje:
  - Passa a ler o check-in real para score/nível/confiança.
  - Sem check-in: 50/âmbar/baixa confiança e sinal "Pendente".
  - Card muda para "Check-in feito hoje" e "Revisar".
- CSV diário:
  - `DailyMetricsCsvParser` com `date`, sono, RHR, HRV e SpO2; datas ISO/BR, aliases e erros por linha.
  - Room v6 (`daily_metrics`) e `DailyMetricsRepository`.
  - `DailyMetricsReadinessBuilder`: sono recente, HRV 7d/60d (≥4 noites), RHR 7d/30d e sinal de RHR elevada por 2 dias.
  - Tela de importação no perfil, com contagem de dias, duplicatas e linhas ignoradas.
  - Fixture `fixtures/csv/daily-metrics.csv`.
- Health Connect:
  - `HealthDailySource` + `HealthConnectDailySource` (read-only, 30 dias, paginação).
  - `HealthMetricsAggregator` puro: sono por data de término, sessões sobrepostas mescladas, médias diárias.
  - Permissões de sono/RHR/HRV/SpO2 no manifest e rationale.
  - Card de status, autorização e sincronização no perfil.
  - `HealthConnectViewModel` com fake source nos testes.
- Histórico de prontidão:
  - `ReadinessHistoryCalculator` recompõe cada dia com dados até aquela data.
  - Card "Prontidão · 14 dias" em Tendências.
- Screenshots dark/light do check-in.

### Pendente

- Validação do Health Connect em aparelho real (a leitura Android compila e a agregação tem testes, mas não há instrumentado no CI).
- Hoje ainda usa treino e atividade de demonstração; a prescrição real vem no P1-05.

### Evidências

- `./gradlew check :app:assembleDebug spotlessCheck :app:verifyRoborazziDebug` — sucesso.
- 425 testes, 0 falhas (27 `core:model`, 176 `core:domain`, 66 `core:data`, 156 `:app`).
- `core:domain`: 98,17% linhas, 83,69% branches, 96,82% instruções.
### Próximos passos

1. Conectar Health Connect e CSV ao `ReadinessCalculator`.
2. Tela de diagnóstico e histórico de prontidão.
3. `P1-05`: prescrição com guardrails e explicação.

## Sessão 2026-10-30 — P1-03: motor de carga e PMC

### Concluído

- `TrainingLoadEngine` em `core/domain/.../load/`:
  - Série diária de 42 dias com carga por dia.
  - TRIMP quando FC + repouso + máxima estão disponíveis; rTSS quando só há pace + limiar; nunca soma os dois no mesmo dia (`R-LOAD-01`).
  - CTL/ATL por EWMA, TSB, médias 7/28, ACWR e monotonia.
  - Dias sem métrica ficam com carga 0 e `missingDays` conta a lacuna.
- Tela Tendências:
  - Cards de CTL, ATL, TSB, carga 7d, ACWR e monotonia.
  - Flag de ACWR (>1,5 alerta; >1,3 atenção; <0,8 cautela) com texto explícito de que não é meta.
  - Gráfico PMC com CTL e ATL em Canvas e descrição de acessibilidade.
  - Estados loading, sem perfil e sem carga.
- Golden `G-LOAD-02` cobre motor de 28 dias com TRIMP + rTSS e valores de PMC.

### Evidências

- `./gradlew check :app:assembleDebug spotlessCheck :app:verifyRoborazziDebug` — sucesso.
- 360 testes, 0 falhas (22 `core:model`, 154 `core:domain`, 57 `core:data`, 127 `:app`).
- `core:domain`: 98,47% linhas, 84,57% branches, 96,73% instruções.

### Decisões e observações

- O motor usa um métrico por dia; não há mistura de TRIMP e rTSS.
- ACWR aparece como flag; a UI não sugere aumentar carga por estar em faixa.
- `Days without load` é explícito para não confundir ausência com recuperação.
- A tela ainda não tem filtros de período nem 80/20; ficam para a evolução de Tendências.

### Próximos passos

1. `P1-04`: prontidão com Health Connect, check-in e CSV.
2. Adicionar zonas de pace e distribuição 80/20 em Tendências.
3. `P1-05`: prescrição com guardrails.

## Sessão 2026-10-30 — P1-02: calibração e zonas

### Concluído

- `CalibrationCalculator` em `core:domain/.../calibration/`:
  - VDOT pelas equações de Daniels (VO2 e %VO2max).
  - Pace de limiar pela velocidade a 88% do VDOT (equação inversa).
  - LTHR = FC média de esforço entre 25 e 35 min; fora da janela fica `null`.
  - Tiers A/B/C: performance ≤ 56 dias, ≥ 3 treinos com FC+pace, ou base inicial.
  - Avisos: maxHR inconsistente (> 95% da FC média), performance antiga/ inválida, sem FC.
  - Toda saída cita `R-CAL-01`/`R-CAL-02` e `EV-DANIELS-2014`/`EV-FRIEL-2018`.
- Tela de calibração no app:
  - Entrada de distância, tempo, data e FC média.
  - Resultado com VDOT, LTHR, pace de limiar, tier, confiança e avisos.
  - Salvamento no perfil sem apagar FC máxima nem outros dados.
- Resumo de calibração no perfil, com CTA para calibrar/recalibrar.
- `HrZoneDistributionCalculator` e card de zonas no detalhe:
  - Tempo por Z1–Z5 por LTHR, intervalos sem FC ficam fora do total.
  - Barra empilhada + legenda com tempo e percentual.
- Testes de motor, formulário, ViewModel, tela e distribuição de zonas.

### Evidências

- `./gradlew check :app:assembleDebug spotlessCheck :app:verifyRoborazziDebug` — sucesso.
- 343 testes, 0 falhas (22 `core:model`, 146 `core:domain`, 57 `core:data`, 118 `:app`).
- `core:domain`: 98,58% linhas, 84,70% branches, 96,70% instruções.

### Decisões e observações

- A confiança da calibração é alta, mas o LTHR continua sendo estimativa; não há teste de lactato.
- Performance fora de 25–35 min não gera LTHR; o app não extrapola.
- Zonas de pace por VDOT existem no domínio, mas a UI do detalhe mostra apenas FC nesta fase.
- A calibração é manual; importar uma prova não a aplica automaticamente.

### Próximos passos

1. `P1-03`: motor de carga integrado às atividades e PMC.
2. Exibir zonas de pace no detalhe e na prescrição.
3. `P1-04`: prontidão com Health Connect e check-in.

## Sessão 2026-10-30 — P1-01: onboarding e perfil

### Concluído

- `AthleteProfile` estendido com `timezone`, `weeklyAvailability`, `goal`, `city`, `latitude`, `longitude` e `units`, com validação de IANA, faixas e minutos por dia.
- Persistência Room v4:
  - `AthleteProfileEntity` + `AthleteProfileDao` (singleton).
  - `ProfileRepository` com fluxo observável, upsert transacional e preservação de `createdAt`.
  - JSON para disponibilidade e objetivo (`ProfileJson`), com tolerância a dados corrompidos.
  - `MIGRATION_3_4` e migrações 1→4/2→4/3→4 testadas.
- Onboarding em 3 passos:
  - Dados pessoais, sexo, altura, peso e experiência.
  - Objetivo, data/tempo alvo e disponibilidade semanal.
  - FC de repouso/máxima, fuso e cidade.
  - Validação por passo, erros em pt-BR, edição preservando calibração existente.
- Gate de primeiro uso: sem perfil, o app mostra o onboarding; com perfil, o shell normal.
- Tela de perfil com resumo, disponibilidade, objetivo e edição.
- Testes: validador, ViewModel, labels, telas e screenshots dark/light.

### Evidências

- `./gradlew check :app:assembleDebug spotlessCheck :app:verifyRoborazziDebug` — sucesso.
- 309 testes, 0 falhas (22 `core:model`, 131 `core:domain`, 57 `core:data`, 99 `:app`).
- `core:domain`: 98,82% linhas, 85,90% branches, 96,88% instruções.

### Decisões e observações

- O perfil é singleton local; multi-perfil continua fora do MVP.
- `units` só tem `METRIC` por enquanto; a UI não oferece milhas.
- Zonas continuam bloqueadas: o perfil coleta FC máxima observada, mas zonas exigem LTHR de calibração (P1-02).
- O onboarding não pede nome para não criar dado que o motor ainda não usa.

### Próximos passos

1. `P1-02`: calibração por prova/TT, VDOT/LTHR e tela de resultado.
2. Ligar zonas de FC e pace ao detalhe da atividade após a calibração.
3. `P1-03`: motor de carga integrado às atividades.

## Sessão 2026-10-30 — P0-10: harness, golden e CI

### Concluído

- Golden scenarios em `fixtures/golden/` com `input.json`, `expected.json` e README:
  - `G-LOAD-01`: TRIMP, rTSS, sRPE, CTL/ATL/TSB, ACWR e monotonia.
  - `G-READY-01`…`04`: dia verde, ausência total, doença/lesão e sono curto.
  - Doubles com tolerância `1e-6`; enums e inteiros exatos.
- Harness `LoadGoldenTest`/`ReadinessGoldenTest` lê os JSON via test resources; `org.json` é dependência **de teste** do `core:domain`.
- Roborazzi 1.77.0 com Robolectric Native Graphics:
  - `ActivityDetailScreenshotTest` grava dark/light em `app/src/test/screenshots/`.
  - `recordRoborazziDebug` atualiza; `verifyRoborazziDebug` bloqueia regressão.
- CI publica testes, Kover, lint e diff de screenshots em toda execução (`if: always()`), além de rodar `verifyRoborazziDebug`.

### Evidências

- `./gradlew check :app:assembleDebug spotlessCheck :app:verifyRoborazziDebug` — sucesso.
- 260 testes, 0 falhas (17 `core:model`, 131 `core:domain`, 46 `core:data`, 66 `:app`).
- `core:domain`: 98,82% linhas, 85,90% branches, 96,88% instruções.
- `verifyParserBranchCoverage`: 93,01% (572/615).

### Decisões e observações

- Cenários `G-001`…`G-006` (plano, taper, calor) ficam para quando `PlanEngine`/`PrescriptionEngine` existirem; não faz sentido golden de motor inexistente.
- Goldens de screenshot usam fixture sintética; nenhuma imagem contém dado real.
- O harness roda no JVM; instrumentado continua reservado para fluxos que dependem de dispositivo.

### Próximos passos

1. `P1-01`: onboarding/perfil com persistência local.
2. `P1-02`: calibração (VDOT/LTHR) para liberar zonas e prescrição.
3. Manter goldens e screenshots atualizados a cada mudança visual/regra.

## Sessão 2026-10-30 — P0-09: detalhe da atividade

### Concluído

- Navegação lista → detalhe (`activity/{activityId}`) com barra inferior oculta e `SavedStateHandle`.
- `ActivityDetailViewModel` abre o agregado no repositório e expõe loading, não encontrado e dados.
- Hero: distância, tempo, pace, FC, cadência, potência, elevação, calorias e tempo em movimento.
- Card de fontes: `FIT · TCX · GPX` a partir do canônico + alternates.
- Gráfico `Pace × FC × tempo` em Canvas, com downsampling, legenda de faixas e descrição de acessibilidade.
- Splits por lap com pace do arquivo ou derivado de tempo/distância e FC.
- Rota offline em Canvas (equiretangular), sem tiles, rede ou nova dependência.
- Insights determinísticos:
  - `R-ACT-01`: ritmo constante/negative split vs queda > 3% na segunda metade.
  - `R-ACT-02`: deriva cardíaca ≥ 5% com pace estável.
  - Cada insight cita evidência (`EV-ABBISS-2008`, `EV-DANIELS-2014`, `EV-FRIEL-2018`) e confiança.
- Testes: ViewModel, formatadores, gráfico, rota, insights e Compose UI (loading, não encontrado, conteúdo e tema claro).
- `RealFixturesDetailTest`: importa e abre os 7 treinos reais sanitizados; TCX confere distância/duração/cadência do README.

### Evidências

- `./gradlew check :app:assembleDebug spotlessCheck` — sucesso.
- 256 testes, 0 falhas (17 `core:model`, 129 `core:domain`, 46 `core:data`, 64 `:app`).
- `core:domain`: 98,82% linhas, 85,90% branches, 96,88% instruções.
- `./gradlew :core:domain:verifyParserBranchCoverage` — 93,01% (572/615).

### Decisões e observações

- Zonas ficam para depois da calibração: LTHR ainda não existe no app, e estimar por maxHR violaria `R-ZONE-01`.
- A rota é um traçado local, não um mapa; mapas com tiles entram quando houver decisão de dependência/offline.
- O gráfico usa duas escalas independentes e declara as faixas na legenda para não sugerir uma unidade única.
- Insights são observações, não prescrição; não alteram carga nem plano.

### Próximos passos

1. `P0-10`: harness restante (goldens e relatórios) — concluído na sessão seguinte.
2. `P1-01`/`P1-02`: onboarding e calibração para liberar zonas e prescrição.
3. Export ZIP com arquivos originais e séries completas (Fase 1).

## Sessão 2026-10-30 — P0-07: persistência Room

### Concluído

- Módulo `:core:data` como Android library, com Room 2.8.5, KSP e schemas exportados em `core/data/schemas/`.
- Entidades e DAOs:
  - `ActivityEntity`: proveniência, esporte, tempo, distância, FC, cadência, potência, elevação, parser, `deletedAt` e índice único de `sourceFileSha256` quando presente.
  - `LapEntity`: chave composta `(activityId, lapIndex)` e FK com `CASCADE`.
  - `SampleBlobEntity`: blob comprimido, versão, encoding, contagem e início.
  - `ImportJobEntity`: status, hash, erro, timestamps e FK `SET NULL` para a atividade.
  - `ActivityDao`, `LapDao`, `SampleBlobDao` e `ImportJobDao`; consultas de lista observável e lixeira.
- `MeuCoachDatabase` v2:
  - `MIGRATION_1_2` adiciona `deletedAt` sem recriar a tabela.
  - Schemas 1 e 2 exportados; teste de migração com `MigrationTestHelper` em Robolectric.
  - Sem `fallbackToDestructiveMigration`.
- `SampleBlobCodec` (`gzip-columnar-v1`):
  - Colunas de offset, posição, altitude, velocidade, FC, cadência, potência e passada.
  - Developer fields preservados em coluna binária própria.
  - Timestamps reconstruídos a partir do início da atividade + offset.
  - Limites de tamanho/contagem, validação de presença, gzip inválido, versão desconhecida e truncamento.
- `ActivityRepository`:
  - Importação transacional de atividade + laps + blob.
  - Dedup por SHA-256 do arquivo; retorna `Duplicate` sem inserir.
  - Listagem/observação como `ActivitySummary`.
  - Abertura como `StoredActivity`, decodificando samples sob demanda.
  - Blob corrompido ou de versão desconhecida mantém a atividade, descarta samples e retorna `sampleWarning`.
  - Soft delete por padrão; exclusão permanente com cascata; `deleteAllData`.
  - Export JSON básico com atividades (incluindo lixeira), laps, contagem de samples e jobs.
  - UUID v7 local e ordenável por tempo.
- Backup Android permanece desativado no manifest (`allowBackup=false`, `fullBackupContent=false`).
- Testes novos em `core:data`:
  - `MeuCoachDatabaseTest`: CRUD, soft delete, cascata, `SET NULL` e unicidade de SHA.
  - `MeuCoachDatabaseMigrationTest`: migração 1→2 preserva dados.
  - `SampleBlobCodecTest`: round-trip, compressão, corrupção, versão, contagem absurda e ordem.
  - `ActivityRepositoryTest`: salvar, listar, abrir, duplicata, blob corrompido, delete, export.
  - `UuidV7Test`: versão, variante e determinismo.

### Evidências

- `./gradlew check :app:assembleDebug` — sucesso.
- `./gradlew :core:data:testDebugUnitTest` — 26 testes, 0 falhas.
- `./gradlew :core:domain:verifyParserBranchCoverage` — `93,01% (572/615)`.
- 160 testes, 0 falhas; `core:domain` com 98,91% de linhas e 86,02% de branches.
- Android Lint: 0 erros; 4 avisos de versão/target. `core:data` sem issues.
- APK debug gerado em `app/build/outputs/apk/debug/app-debug.apk`.

### Decisões e observações

- A dedup do repositório cobre apenas hash exato do arquivo. A dedup por chave de atividade (início ±1 s, duração ±2 s, distância ±1%) e os `alternates` ficam para o fluxo de importação do P0-08.
- Samples não guardam timestamp absoluto por ponto; o início da atividade + offset é a fonte canônica. Isso reduz o blob e evita duplicar informação.
- Developer fields são preservados, mas não entram no JSON de export básico; o blob completo continua no banco.
- A lixeira é lógica: filhos permanecem até a exclusão permanente. O export inclui atividades com `deletedAt`.
- `zoneId` é capturado na criação do repositório e `localDate` é derivada do início da atividade. Mudança de fuso do perfil será tratada no onboarding/perfil.
- `:core:data` ainda não está ligado ao app nem a um módulo Hilt; isso entra junto com a importação real no P0-08.

### Próximos passos

1. `P0-08`: file picker (SAF) + share intent, fila persistente com WorkManager e tela de importação.
2. Ligar `:core:data` ao app, com Hilt e `ActivityRepository`.
3. Dedup por chave de atividade, alternates e substituição FIT > TCX > GPX no fluxo real.
4. `P0-09`: detalhe da atividade com gráficos, splits e rota.
5. Fechar `P0-10`: Robolectric/Roborazzi, golden scenarios e relatórios no CI.

### Riscos/observações

- O codec limita o payload descomprimido a 128 MB e a 5 milhões de samples; arquivos muito maiores falham com erro claro.
- O export básico não inclui séries completas nem arquivos originais; export ZIP/arquivos fica para a Fase 1.
- A migração 1→2 é pequena por design; mudanças futuras de schema exigem novo teste e schema exportado.
- Nenhum dado de saúde real pode entrar no git; revisar `git status` antes de cada commit.

## Sessão 2026-10-29 — P0-06: parsers TCX/GPX e dedup

### Concluído

- Parser TCX em `core/domain/src/main/kotlin/com/meucoach/core/domain/parser/tcx/`:
  - Activity, laps, trackpoints, FC, cadência, posição, altitude, velocidade e potência por extensão.
  - Fallback de início (Activity/Id → Lap/StartTime → primeiro Trackpoint), duração por lap ou intervalo dos pontos e distância declarada → GPS → integração de velocidade.
  - Infere um lap quando o TCX não tem `Lap`; lap e atividade recebem resumo calculado.
  - Cadência de corrida/esteira é multiplicada por 2 (passadas/min → passos/min); walking mantém o valor.
  - Dados fora de faixa física viram `null`; o parser não inventa zero.
- Parser GPX em `core/domain/src/main/kotlin/com/meucoach/core/domain/parser/gpx/`:
  - GPX 1.0/1.1 por nome local, `trkpt`, `ele`, `time` e extensões `hr`, `cad`, `speed`, `power`.
  - Distância por haversine, fallback por integração de velocidade; sem elevação permanece `null`.
  - Sem laps; esporte inferido de `type` e padrão running; `creator` vira `deviceName`.
  - Cadência de corrida/esteira normalizada como no TCX.
- `SecureXml` compartilhado em `core/domain/.../parser/xml/`:
  - `DocumentBuilderFactory` namespace-aware, entidades externas bloqueadas, DOCTYPE desabilitado quando a feature é suportada, `EntityResolver` vazio e XInclude desativado.
  - Helpers DOM por nome local para TCX/GPX.
- Deduplicação em `core/domain/.../importer/ActivityDeduplicator.kt`:
  - Tolerância de ±1 s no início, ±2 s na duração e ±1% (mínimo 1 m) na distância.
  - Preferência FIT > TCX > GPX > Health Connect > Manual; em empate, mais samples e depois versão do parser.
- Sanitização e fixtures:
  - `tools/sanitize_tcx.py` remove `Position`, `Creator`, `Notes` e `Author`; valida o XML de saída.
  - 3 TCX reais sanitizados em `fixtures/tcx/` (~1,9 MB), com README e métricas esperadas.
  - 2 GPX sintéticos em `fixtures/gpx/` (extensões e sem elevação), com README.
- Testes novos:
  - `TcxParserRealFixturesTest`: 3 TCX reais + determinismo.
  - `TcxParserSyntheticTest`: laps, posição, cadência, valores inválidos, lap ausente, encoding, XML malformado e XXE.
  - `GpxParserTest`: extensões, sem elevação, potência, coordenadas inválidas, XML malformado, XXE e determinismo.
  - `ActivityDeduplicatorTest`: prioridade, tolerância, samples e chave.

### Evidências

- `./gradlew check :app:assembleDebug` — sucesso.
- `./gradlew :core:domain:verifyParserBranchCoverage` — `93,01% (572/615)` no pacote FIT.
- `./gradlew :core:domain:koverVerify` — sucesso.
- 134 testes, 0 falhas; `core:domain` com 98,91% de linhas e 86,02% de branches.
- Android Lint: 0 erros; 2 avisos intencionais.

### Decisões e observações

- TCX/GPX são adapters de fallback; FIT continua canônico.
- A remoção de posições dos TCX reais é intencional: GPS e múltiplos laps são cobertos por fixtures sintéticas, sem risco de localização.
- O dedup usa tolerância conforme `DATA_MODEL.md`; a chave exata (`keyOf`) fica disponível para diagnóstico e jobs de importação.
- Alternates e confiança de lap inferido serão ligados ao fluxo de importação no `P0-08`.
- O parser XML é JVM puro (`javax.xml`), sem dependência Android.

### Próximos passos

1. `P0-07`: `core:data` com Room, entidades, DAOs, blob comprimido e migração de teste.
2. `P0-08`: file picker/share intent, fila persistente, progresso/erro e dedup no fluxo real.
3. Integrar TCX/GPX ao detalhe da atividade e validar os 7 treinos reais.
4. Fechar `P0-10`: Robolectric/Roborazzi, golden scenarios e relatórios no CI.

### Riscos/observações

- TCX de outros fabricantes pode exportar cadência já em passos/min; o parser segue o padrão dos arquivos Zepp e a documentação atual. Revisar com uma fixture nova antes de mudar a heurística.
- GPX não tem esporte nem laps; a importação deve tratar distância calculada por GPS como menor confiança que FIT.
- O CI precisa manter platform 37 e build-tools 37.
- Nenhum dado de saúde real pode entrar no git; revisar `git status` antes de cada commit.

## Sessão 2026-10-28 — P0-05: parser FIT

### Concluído

- Modelos canônicos de importação em `core:model`:
  - `ImportedActivity`, `ActivityLap` e `ActivitySample`.
  - Dados ausentes permanecem `null`; nada é convertido em zero artificial.
- Sanitizador determinístico `tools/sanitize_fit.py`:
  - Trabalha no nível de bytes, preservando a estrutura FIT, escalas e developer fields.
  - Substitui todas as posições pelo sentinela inválido do FIT, em vez de deslocar GPS.
  - Limpa serial e identificadores de aplicação/dispositivo.
  - Substitui o developer field `source` por `example.invalid`.
  - Mantém timestamps, recalcula os CRCs e valida a abertura com `fitdecode`.
- Fixtures sanitizadas:
  - 4 arquivos reais do Amazfit Balance em `fixtures/fit/` (~132 KB no total).
  - Nenhum arquivo original ou dado identificável foi commitado.
  - Os 3 TCX antigos continuam em `~/Documents/Rafa/` e serão usados no `P0-06`.
- Parser FIT em `core:domain/src/main/kotlin/com/meucoach/core/domain/parser/fit/`:
  - `FitReader`: framing de 12/14 bytes, CRC de header e arquivo, little/big endian, definições, compressed timestamps, tipos base, developer fields e warnings.
  - `FitParser`: file_id, session, record, lap, event, activity e device_info; fallbacks de início/duração/distância; normalização de cadência; validação de faixas físicas.
  - Cadência de `record.cadence` e `session.avg/max_running_cadence` é strides/min e é multiplicada por 2; `lap.avg_cadence` já é passos/min.
  - Warnings para arquivo sem file_id/sessão, múltiplas sessões, esporte desconhecido, sample sem timestamp/offset inválido, CRC ausente, developer field desconhecido e dados extras.
- Testes:
  - 50 testes do parser, incluindo 4 fixtures reais, casos sintéticos, erros de framing/CRC e cobertura de branches.
  - `FitParserRealFixturesTest` valida resumo e samples dos 4 FIT sanitizados.
  - `FitParserSyntheticTest`, `FitReaderErrorsTest`, `FitParserCoverageTest`, `FitParserBranchTest` e `FitReaderCoverageTest` cobrem os caminhos de fallback e erro.
- Gate de cobertura:
  - Task `:core:domain:verifyParserBranchCoverage` lê o XML do Kover e falha abaixo de 90% de branches no pacote `parser.fit`.
  - Ligada a `check` e `koverVerify`; saída atual: `FIT parser branch coverage: 93,01% (572/615)`.
- Higiene:
  - `__pycache__/` e `*.pyc` passaram a ser ignorados pelo Git.

### Evidências

- `./gradlew check :app:assembleDebug` — sucesso.
- `./gradlew :core:domain:koverVerify` — sucesso, incluindo o gate do parser.
- `./gradlew :core:domain:verifyParserBranchCoverage` — 93,01% (572/615).
- Testes: 105 total, 0 falhas.
- Kover `core:domain`: 99,88% de linhas, 93,37% de branches, 99,04% de instruções.
- Android Lint: 0 erros, 2 avisos (`OldTargetApi` para targetSdk 36 e `NewerVersionAvailable` do Kover 0.9.11).
- APK debug: `app/build/outputs/apk/debug/app-debug.apk` (~20 MB).

### Decisões e observações

- O parser é determinístico, sem I/O e sem dependência de Android.
- O FIT continua canônico; TCX/GPX serão fallbacks com preferência FIT > TCX > GPX.
- Fixtures reais só entram após sanitização no nível de bytes; o CI usa apenas fixtures commitadas.
- A comparação com `fitdecode` foi feita localmente; não é uma dependência de runtime nem de CI.
- A cobertura de branches do parser é medida no pacote inteiro, não apenas em `FitParser`; isso evita esconder branches no leitor de bytes.

### Próximos passos

1. Iniciar `P0-06`: parser TCX com laps, trackpoints, FC, cadência e posição, usando os 3 TCX antigos fora do repo.
2. Implementar GPX com extensões comuns e fallback sem elevação.
3. Adicionar deduplicação entre formatos com preferência FIT > TCX > GPX.
4. Depois, `P0-07`: persistência Room com blob comprimido e migração de teste.
5. Fechar `P0-10`: Robolectric/Roborazzi, golden scenarios e relatórios no CI.

### Riscos/observações

- O parser FIT é o maior risco técnico; a suíte cobre o subconjunto do Amazfit, mas novos dispositivos podem expor campos/ordenações diferentes.
- O CI precisa manter platform 37 e build-tools 37.
- Nenhum dado de saúde real pode entrar no git; revisar `git status` antes de cada commit.
- O build local usa JDK 17; Gradle 9.8 também roda em 27, mas o CI fixa 17.

## Sessão 2026-10-27/28 — Planejamento e fundação

### Concluído

- Entrevista de produto, ciência e design.
- Análise inicial das atividades reais do Amazfit Balance; depois separadas em 4 FIT e 3 TCX.
- Health Connect validado para sono no dispositivo do usuário 0.
- Stack Android validada em projeto limpo:
  - AGP 9.4.1, Gradle 9.8.2, Kotlin 2.4.21, KSP 2.3.12, JDK 17.
  - Compose BOM 2026.09.00, Room 2.8.5, Hilt 2.60.1.
  - `compileSdk 37` necessário para `core-ktx 1.19.1`.
- Documentação criada:
  - `README.md`, `docs/PRODUCT.md`, `docs/ARCHITECTURE.md`, `docs/DATA_MODEL.md`.
  - `docs/SPORTS_SCIENCE.md`, `docs/DESIGN_SYSTEM.md`, `docs/TESTING.md`, `docs/ROADMAP.md`.
  - `docs/RUNBOOK.md`, `docs/DECISIONS.md` + 9 ADRs, `AGENTS.md`.
- Tokens DTCG em `design/tokens/meucoach.tokens.json`.
- Bootstrap Gradle completo:
  - Wrapper 9.8.2, version catalog, módulos `:app`, `:core:model`, `:core:domain`, `:core:designsystem`.
  - Spotless/ktlint, Android Lint, Kover, Makefile e CI GitHub Actions.
- Modelo e domínio iniciais:
  - `Distance`, `Pace`, `ActivitySummary`, `AthleteProfile`, enums e testes.
  - Zonas de FC por LTHR e zonas de pace por limiar.
  - TRIMP, rTSS, sRPE, EWMA, CTL/ATL/TSB, ACWR e monotonia.
  - Prontidão v1 com Hooper invertido, pesos renormalizados, confiança e flags.
- Design system:
  - Tema dark/light, Geist + Inter variáveis, shapes e paleta alinhada aos tokens.
  - `McCard`, botões, pills, stats, badge de confiança, gauge, section header, bottom bar e empty state.
  - Ajustes de contraste AA no claro (`accentText`, `textTertiary`, coral e âmbar) refletidos em tokens e docs.
- App:
  - Shell com 4 abas, navegação, edge-to-edge e ícone adaptativo com camada monochrome.
  - Tela Hoje premium com prontidão, próximo treino, carga semanal, check-in e última corrida.
  - Placeholders de Corridas, Tendências e Perfil com empty states distintos.
  - Backup/device-transfer desativados por regras de extração de dados.
- Verificação visual:
  - APK instalado em emulador API 35 (Pixel 7).
  - Tela Hoje verificada em dark e light; placeholder de Corridas verificado em dark.
  - Splash claro/escuro corrigido via `values-night`.

### Evidências da época

- `./gradlew check :app:assembleDebug` — sucesso.
- `./gradlew :core:domain:koverVerify` — sucesso.
- Kover `core:domain`: 216/216 linhas, 245/260 branches.
- Android Lint: 0 erros, 2 avisos (`OldTargetApi` e `NewerVersionAvailable` do Kover).
- APK debug: `app/build/outputs/apk/debug/app-debug.apk` (~20 MB).

### Decisões-chave

- FIT canônico; parser próprio com validação cruzada.
- Health Connect + check-in + CSV; Zepp direto só na Fase 3.
- Motor determinístico; LLM só para texto.
- Musculação fora do escopo; fadiga global no check-in.
- Sem SQLCipher/biometria no MVP.
- Dark-first com lima como único accent; lima como texto no claro usa `#4F6600`.
- Backend Ktor/Postgres/Dokploy na Fase 2.

## Histórico

| Data | Marco |
|---|---|
| 27/10/2026 | Planejamento concluído; documentação e ADRs; stack validada |
| 28/10/2026 | Fundação verde: build, testes, Kover, lint e tela Hoje em dark/light |
| 28/10/2026 | P0-05 concluído: sanitizador, 4 fixtures FIT, parser, 105 testes e gate de branches |
| 29/10/2026 | P0-06 concluído: parsers TCX/GPX, 3 TCX sanitizados, GPX sintético, dedup e 134 testes |
| 30/10/2026 | P0-07 concluído: Room v2, migração testada, blob comprimido, repositório, export/delete e 160 testes |
| 31/10/2026 | P1-07 80/20 por tempo concluído: calculadora de zonas, card em Tendências e 507 testes |
| 31/10/2026 | P1-05 parcial: calor autorreportado (+3%) e alternativa de esteira na qualidade; Room v9 e 519 testes |
| 31/10/2026 | P1-05 parcial: deload de −25% a cada 4ª semana consecutiva, ancorado no histórico de planos; 524 testes |
| 31/10/2026 | P1-07 parcial: recordes pessoais em Tendências (distância, pace ≥ 3 km e tempo); 526 testes |
| 31/10/2026 | P1-06 parcial: conclusão do plano em 8 semanas em Tendências, sem streak; 533 testes |
| 31/10/2026 | P1-07 parcial: eficiência pace@FC (m/batimento) e filtros de 6/12/26 semanas; 537 testes |
| 31/10/2026 | P1-07 parcial: heatmap de consistência alinhado por semana e por volume; 539 testes |
| 31/10/2026 | P1-06 parcial: sessão guiada com cronômetro e troca de dia com guardrails; 548 testes |
| 31/10/2026 | P1-08 parcial: histórico dos últimos 7 check-ins com Hooper e sinais; 551 testes |
| 31/10/2026 | P1-10 concluído: bottom sheet de explicação com regras, evidências, confiança e lacunas; 556 testes |
| 31/10/2026 | P1-09 concluído: sync incremental do Health Connect + worker diário via WorkManager; 562 testes |
| 31/10/2026 | P3-05 protótipo: planner, renderer Canvas 9:16 e export MP4 via MediaCodec; 568 testes |
| 31/10/2026 | P3-05: prévia antes do export + confirmação/cancelamento; 570 testes |
| 02/11/2026 | P3-05: validação em aparelho real confirmada (`resposta 8`); `ADR-0015` aceito — mapa real (`OSM` + `MapLibre`), frame final (3 s) e logo `MeuCoach` |
| 02/11/2026 | P1-13 concluído: linguagem simples em Hoje/Tendências, logo no story, proporção de carga real e onboarding mais robusto; 580 testes |
| 03/11/2026 | T-06 concluído: tela Dados exporta JSON completo e apaga todos os dados em transação, com confirmação; 591 testes |
| 03/11/2026 | P1-11 concluído: notificação de importação (sucesso/duplicado/falha) com deep link para o treino; 600 testes |
| 03/11/2026 | Hoje: card de carga deixa de usar números de demonstração sem dados; estado vazio honesto; 602 testes |
| 04/11/2026 | Story: geração em segundo plano via WorkManager, cancelamento real no encoder, ETA e notificação (ADR 0018); 754 testes |
| 04/11/2026 | Story: mapa outdoor sem POIs, snapshot 2,25×, foco na rota e ticks por km; bloqueio do tilt nativo documentado (ADR 0015 addendum); 755 testes |
| 04/11/2026 | Story: progresso/ETA reais, foreground service, estado restaurado e encoder na GPU (190→42 ms/frame, export ~50 s); 760 testes |
| 04/11/2026 | Story público: destaques objetivos sem julgamento de plano, mapa Terreno com hillshade e aviso de download; botão full-width; 771 testes |
| 04/11/2026 | Story: estilo Satélite (Esri + labels + véu), recomendado para cidades planas; 772 testes |
| 04/11/2026 | Perfil com nome/foto (Room v10), avatar na Home/Perfil e pin do story; leitura do coach na tela da atividade; 775 testes |
| 04/11/2026 | Story com câmera 3D real: projeção do MapLibre portada para Kotlin, snapshot inclinado e rota alinhada (ADR 0019); 778 testes |
| 04/11/2026 | Story: abertura com overview 32° → follow 42° sob fade-in de 0,6 s (sem corte visível); 778 testes |
| 06/11/2026 | C1-02 concluído: cobertura/frescor de carga, ACWR desconhecido não libera qualidade, gate no plano/recomendador e UI explícita; 924 testes |
