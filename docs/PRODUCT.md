# PRODUCT.md — MeuCoach

**Versão:** 1.5 · **Data:** 27/11/2026 · **Status:** aprovado para a Fase 1; revisão 1.5 registra a visão de ciclo e prova (ADR 0072): projeção somente leitura de 6 semanas, marcos de redução/prova, descarga confirmada ou condicional e entrada no Hoje/Perfil. A revisão 1.4 registra a janela de planejamento semanal (ADR 0070): horário habitual, lembretes antes do treino, prévia da próxima semana, treino forte condicionado ao check-in e revisão com “o que muda”.

**Histórico de revisão**

| Versão | Data | Mudança |
|---|---|---|
| 1.0 | 27/10/2026 | Visão, usuário 0, escopo do MVP e princípios iniciais. |
| 1.1 | 06/11/2026 | Registro das decisões aprovadas de captura, perfil, objetivo, backend, backup e determinismo. |
| 1.2 | 06/11/2026 | C1-01..C1-05 implementados; lixeira de 7 dias e conclusão com evidência refletidas no escopo; divergências do PRD reconciliadas. |
| 1.3 | 26/11/2026 | Coach, Story e Arte no escopo entregue; barra Hoje · Corridas · Coach · Tendências · Saúde com Perfil via avatar/engrenagem; Tier C manual e GUIDED futuro; métricas de desenvolvimento separadas das não instrumentadas; importação concluída e exportação JSON explícita sem restauração no MVP (DOC-01..DOC-07, GEN-01/02/03, D10). |
| 1.4 | 27/11/2026 | Janela de planejamento semanal (ADR 0070): horário habitual no perfil, check-in/treino lembrados antes do treino, prévia persistida da próxima semana com aviso no domingo, treino forte futuro condicionado ao check-in e bloco “próxima semana” na revisão. |
| 1.5 | 27/11/2026 | Visão de ciclo e prova: projeção somente leitura de 6 semanas ou até a prova, marcos de preparação/redução/prova, descarga confirmada ou condicional, volume por direção e entrada no card do plano (Hoje) e no card da prova (Perfil); sem plano multi-semana persistido. |

## 1. Visão

Um treinador de corrida que **explica o porquê**. O MeuCoach importa o que o vestível já registrou, transforma em carga, prontidão e desempenho, e prescreve o próximo treino com base em ciência do esporte — não em achismo, nem em médias genéricas.

A promessa central:

> "Você não precisa adivinhar se hoje é dia de correr forte. O app mostra o que seus dados dizem, o que ele recomenda e por quê."

## 2. Usuário 0

| Campo | Valor |
|---|---|
| Idade / sexo | 38 anos / masculino |
| Altura / peso | 174 cm / 97 kg |
| Experiência | < 1 ano de corrida |
| 5 km | 30:59 (24/10/2026, prova) — VDOT ≈ 31 |
| Volume atual | 2 corridas na rua + 4 sessões de esteira/semana; academia 6x/semana (rotina própria) |
| Disponibilidade | 6 dias/semana |
| Objetivo | Performance geral + provas de rua ocasionais |
| Local | Natal/RN — calor e umidade altos, nível do mar |
| Sono | ~6 h/noite, dorme com o relógio |
| Lesões | Nenhuma relatada |
| Dispositivos | Amazfit Balance + cinta Xoss X2 Pro; app Zepp; Health Connect ativo (sono validado) |

**Dores observadas nos dados reais:**

- Corridas de rua em Z4 (FC média 168–171) que deveriam ser leves.
- Praticamente nenhum volume em Z2.
- Pace de prova (6:04/km) muito próximo do pace de treino (6:32–6:53/km).
- Sono abaixo do recomendado para recuperação.
- FC máxima informada (185) inconsistente com a média de 181 por 31 min; usar LTHR, não maxHR estimada.

## 3. Jobs to be done

1. **Importar** meus treinos sem trabalho manual e sem depender de um único vestível.
2. **Entender** se estou evoluindo: carga, ritmo, FC, cadência, consistência.
3. **Saber o que fazer hoje** — e por que — sem montar planilha.
4. **Planejar a semana antes dela começar**, inclusive quando treino cedo e o app ainda não foi aberto.
5. **Não me lesionar** nem acumular fadiga sem perceber.
6. **Confiar** no que o app diz: ver a fonte e o nível de confiança.
7. **Ter meus dados comigo**, com exportação JSON explícita e exclusão sob meu controle.

## 4. Escopo do MVP

### Dentro

- Importação manual de **FIT, TCX e GPX** (file picker + share intent).
- Deduplicação de atividades importadas em mais de um formato.
- **Navegação:** barra inferior **Hoje · Corridas · Coach · Tendências · Saúde**; Perfil fica fora da barra, acessível pelo avatar no Hoje e pela engrenagem global, sem quebrar as rotas existentes.
- Detalhe do treino: resumo, laps/splits, FC, pace, cadência, potência (quando houver), elevação, rota, zonas.
- Perfil e onboarding (dados antropométricos, objetivo, disponibilidade e **horário habitual de treino** opcional; o horário ancora os lembretes antes do treino).
- Calibração por prova/TT recente (Tier A/B) ou **teste de 30 min feito por conta própria**, com inserção manual do resultado (Tier C). O MVP orienta o teste e aceita os dados informados; **não há player guiado** nem promessa de execução assistida.
- Motor de zonas (LTHR + pace), carga (TRIMP, rTSS, CTL/ATL/TSB, ACWR), prontidão e prescrição.
- Check-in diário de 15 s (sono, fadiga, dor, stress), lembrado **antes do treino** quando o horário habitual está informado.
- **Prévia da próxima semana:** na primeira abertura de domingo, o app persiste a semana seguinte como intenção e a mostra com o selo “Prévia — pode mudar até segunda”. O plano da semana seguinte já existe antes da segunda; o dia continua sendo a autoridade final.
- **Visão de ciclo e prova:** o card do plano no Hoje (e o card da prova no Perfil) abre um sheet “Caminho até a prova” com a semana atual e as próximas 5 (ou até a prova), fases, marcos de preparação/redução/prova e descarga. É leitura: não cria plano, não prescreve sessão futura e não promete minutos/paces além da semana atual e da prévia de domingo. A descarga futura aparece como cenário condicional (“se você completar N semanas de carga”) ou confirmada pelo plano; sem histórico, fica só a regra, sem data.
- Leitura de **Health Connect** (sono, RHR, HRV, SpO2) com diagnóstico de sincronização e atualização automática ao abrir o app. `ExerciseSession`/corridas do Health Connect ficam para o ciclo 2 (FR-007).
- **Coach (IA):** aba central com explicação da semana, revisão da semana fechada, replanejamento com confirmação/diff/undo, input livre e conversa aberta. A IA só reescreve texto do motor; opt-in explícito, memória local com TTL, revogação apaga e qualquer falha/validação cai no determinístico. Números, carga, pace e plano nunca nascem da IA.
- **Story e Arte:** Story Studio no detalhe da atividade, com vídeo 9:16 (mapa via tiles OSM; exige rede), capa/pôster e Arte PNG transparente ou com foto nos formatos 4:5, 1:1 e 9:16. A Arte funciona offline; Story e Arte são visuais e não alteram treino, carga ou prescrição.
- Tela **Hoje** com prontidão, treino prescrito e "por quê", com um link direto para a recuperação; um único card de treino, com o plano semanal como contexto. Quando o treino do dia já foi concluído, o card vira um resumo com selo “Concluído” e esconde as ações. Treinos fortes futuros aparecem como “Condicionado ao check-in”; a primeira semana criada no meio da semana explica que começa hoje.
- **Revisão semanal com próxima semana:** além do resumo da semana fechada, um bloco curto mostra o intervalo, volume, treinos fortes e longão da semana seguinte, sempre condicionado ao check-in. O bloco continua sendo a semana seguinte à revisada; a prévia de domingo é uma superfície separada e não é duplicada.
- **Briefing do treino** (“Como correr hoje”): foco, alvo, por que hoje, como correr e o que fazer se estiver difícil, sempre copiados da prescrição vigente. Sessão concluída não recebe briefing retrospectivo; o histórico mostra a execução registrada.
- Conclusão com evidência: corrida vinculada ou “fiz sem registro” com duração e RPE; descanso/doença encerram o dia com motivo. **Execução guiada (`GUIDED`) é futuro**, não contrato do MVP: `CompleteSession` continua recusando esse caminho até decisão/ADR próprio.
- Evento de prova (5K/10K/meia/maratona) com taper por data e dia de prova como autoridade única; a corrida importada compatível pode concluir a prova automaticamente, com origem auditável e correção, e a meta declarada gera apenas uma referência aritmética de pace/faixa/passagens (ADR 0030/0032).
- Oferta reativa ao importar: uma corrida do próprio dia, em dia de treino ainda pendente, pergunta na hora se deve virar o treino de hoje; se o dia já foi concluído, o app avisa que ela fica apenas como treino livre. Um “fiz sem registro” pode ser corrigido pela corrida real, com confirmação explícita.
- Tela **Saúde** como destino principal: painel de sono, FC de repouso, HRV e SpO₂, com período, fontes e aviso quando o dia atual ainda não chegou.
- Tela **Tendências** com gráficos de evolução.
- Notificações locais: treino do dia, lembrete de check-in, **prévia da próxima semana no domingo** e **import concluído** (sucesso, duplicado ou falha). O horário de treino/check-in segue o horário habitual; sem ele, 07h e 18h.
- Exportação JSON explícita e exclusão de todos os dados, com lixeira de 7 dias para atividades e reset imediato. **Restauração de backup não está no MVP**; o JSON é uma exportação, não um round-trip prometido.
- Funcionar offline — exceto o mapa do story, que baixa tiles do `OpenStreetMap` no momento da geração (`ADR-0015`).

### Fora (explícito)

- Prescrição ou acompanhamento de musculação — o usuário tem rotina própria; o app só captura fadiga global no check-in.
- Nutrição, emagrecimento e prescrição alimentar. Única exceção (ADR 0029): copy educativa de hidratação, estática e sem números, no briefing; alimentação vira encaminhamento a nutricionista, nunca dica.
- Diagnóstico, reabilitação ou tratamento de lesões.
- Integração direta com a API do Zepp (Fase 3, experimental).
- Social, ranking, feed e desafios (Fase 3).
- iOS, Wear OS e widgets (widgets na Fase 2; iOS não planejado).
- Player guiado do Tier C, execução guiada (`GUIDED`), restauração de backup e métricas de produto instrumentadas. São decisões de escopo separadas (D10) e exigem ADR antes de código.
- Mapa com navegação turn-by-turn.
- Treinos de ciclismo, natação ou triathlon no MVP (modelo fica preparado, mas UI focada em corrida).

## 5. Princípios de produto

1. **Explicar, não impressionar.** Números grandes sem contexto são ruído.
2. **Uma decisão por tela.** Hoje = "o que faço agora".
3. **Confiança visível.** Todo número derivado mostra fonte, data e confiança.
4. **Conservador por padrão.** Sem dados, não aumente carga.
5. **Sem culpa.** Perder um treino não gera punição, streak ou notificação passivo-agressiva.
6. **Privacidade como padrão.** Local-first, sem ads, sem analytics de terceiros.
7. **Acessível.** Contraste AA, fontes escaláveis, TalkBack, alvos de 48 dp.

## 6. Métricas: aceite de desenvolvimento × produto

### 6.1 Critérios de aceite de desenvolvimento (verificáveis hoje)

| Critério | Como é verificado |
|---|---|
| Importação FIT/TCX/GPX e deduplicação | Testes de parser, `ImportServiceTest`, `ActivityDeduplicatorTest` e fixtures sanitizadas/sintéticas. |
| Notificação de import concluído | `ImportNotificationTest`/testes do importador para sucesso, duplicado e falha. |
| Carga, prontidão e prescrição | Goldens `G-LOAD-01/02/03`, `G-READY-01..05`, `G-PER-03/05/06`, `G-VOL-02`, `G-UPGRADE-01`, `G-TAPER-01`, `G-LONG-RUN-RAMP-01`, `G-QUALITY-ADJACENCY-01` e `G-BEGINNER-TAPER-01`, além dos testes de contrato. |
| Cobertura do motor (`core:domain`) | Gate Kover ≥ 85% de linhas (medição atual no `TESTING.md`). |
| Cobertura do parser FIT | Gate de ≥ 90% de branches + golden com fixtures sanitizadas. |
| Determinismo e offline | Testes com relógio/fuso injetados e suíte JVM sem rede. |
| Check-in curto | Meta de UX de ≤ 20 s, validada manualmente pelo usuário 0; não há telemetria de tempo por tela. |
| Usuário entende a recomendação | Avaliação qualitativa manual do usuário 0; não é um painel de produto. |

### 6.2 Métricas de produto (não instrumentadas no MVP)

Estas metas continuam como objetivo de produto, mas **não são medidas** hoje: não há SDK de analytics, telemetria de crash ou funil de importação. Nenhuma promessa de “≥ 99%” ou “< 2 min” deve ser lida como resultado medido.

| Métrica de produto | Estado no MVP |
|---|---|
| Import de arquivo válido ≥ 99% sem erro | Não medida em produção; coberta por testes e uso manual. |
| Tempo até o primeiro insight após importar < 2 min | Não medido; sem instrumentação de funil. |
| Crash-free sessions ≥ 99,5% | Não medida; sem crash reporting/analytics. |
| Dados diários com confiança A/B ≥ 70% após 4 semanas | Não medido; a confiança é exibida ao usuário, mas não agregada. |
| Usuário consegue explicar a recomendação ≥ 80% | Só avaliação qualitativa do usuário 0; sem teste moderado instrumentado. |

> Medir qualquer métrica de produto exige decisão de escopo/ADR (D10), privacidade e instrumentação; não é efeito colateral desta reconciliação documental.

## 7. Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Zepp não entrega dados diários | Health Connect + check-in + CSV; motor com confiança parcial |
| Dados de wearables imprecisos | Proveniência, validação cruzada, confiança explícita, nunca decisão crítica isolada |
| Overtraining / lesão | Guardrails, ACWR como sinal, sono, RPE, sinais vermelhos, disclaimer |
| Excesso de escopo | Fora de escopo explícito; fases com critérios de aceite |
| Complexidade de modularização | Começar com 5 módulos reais (`:app` + 4 `core`) e só extrair features quando houver gatilho (ADR/arquitetura) |
| LGPD / dados nos EUA | Local-first; sync só na Fase 2 com consentimento e região documentada |
| "Mais um app de corrida" | Diferencial: explicabilidade + ciência + dados reais + design premium |

## 8. Privacidade e LGPD

- Por padrão, os dados ficam no dispositivo.
- Sync futuro é opt-in, com TLS, conta e possibilidade de apagar tudo.
- A VPS de produção está nos EUA (IONOS/Dokploy). Isso será declarado no app e no consentimento antes do sync.
- Sem venda de dados, sem ads, sem SDK de analytics por padrão.
- Exportação em formato aberto (JSON completo de perfil, atividades, laps, métricas, check-ins, planos, prescrições, execuções e turnos do Coach). É uma exportação explícita; **não há importação/restauração de backup no MVP**. Arquivos originais e séries completas ficam para um ciclo de backup versionado, se aprovado.
- Dados de saúde nunca entram no repositório git.

## 9. Glossário

| Termo | Definição |
|---|---|
| **LTHR** | FC de limiar de lactato, informada manualmente ou estimada por TT de 30 min/prova |
| **VDOT** | Índice de aptidão de Daniels, derivado de performance |
| **TRIMP** | Training Impulse — carga baseada em FC e tempo |
| **rTSS** | Running Training Stress Score — carga baseada em pace/limiar |
| **CTL / ATL / TSB** | Carga crônica (42d) / aguda (7d) / balanço |
| **ACWR** | Acute:Chronic Workload Ratio (7/28) — sinal, não lei |
| **Z2** | Zona 2 de FC, intensidade fácil/conversável |
| **RPE** | Esforço percebido (1–10) |
| **Tier A/B/C** | Níveis de calibração por dados disponíveis; Tier C usa um teste de 30 min feito por conta própria e inserido manualmente, sem player guiado |

## 10. Decisões aprovadas do MVP (06/11/2026)

Estas decisões complementam o escopo acima e orientam o primeiro ciclo. O detalhamento técnico está em `docs/PLANO_TECNICO_CICLO_1.md`.

| Tema | Decisão aprovada |
|---|---|
| Captura | **Import-first**: FIT/TCX/GPX + Health Connect + entrada manual; sem GPS nativo no MVP. |
| Perfil | **Um perfil por instalação**; sem `profileId` especulativo nem multi-perfil nesta fase. |
| Objetivo | **Um objetivo ativo por vez**; objetivo geral ou prova (5K/10K/meia/maratona). |
| Backend | **Sem backend obrigatório**; o app é útil offline e sem conta. Integração externa futura não é antecipada. |
| Backup | **Exportação JSON explícita**; restauração de backup não está no MVP e exige decisão/ADR separado (D10). |
| Motor | **Determinístico**; regra, evidência e confiança em toda recomendação. LLM não decide treino. |
| Conclusão | `ACTIVITY` > `MANUAL` no MVP; `GUIDED` é futuro e continua recusado por `CompleteSession`; descanso, doença e não realizado têm estados próprios (ADR 0024). |
| Prova | Evento separado, uma prova `PLANNED` ativa; auto-conclusão conservadora (mesma data, 90–110%, uma única candidata) com `completionSource` e undo; meta declarada gera referência aritmética, nunca prescrição (ADR 0030/0032). |
| Plano × prescrição | Plano é intenção; prescrição diária persistida é a única autoridade do dia (ADR 0023). |
| Exclusão | Lixeira de 7 dias para atividade; reset imediato, com época de dados e sem apagar Health Connect (ADR 0025). |
| Carga | ACWR desconhecido/cobertura insuficiente bloqueiam qualidade; ACWR é flag, nunca diagnóstico (ADR 0026). |

> O prazo de 30 dias de lixeira citado no PRD original (FR-009) foi substituído por **7 dias** na decisão aprovada; o PRD foi anotado na revisão do C1-06 e mantém o registro histórico da auditoria.
