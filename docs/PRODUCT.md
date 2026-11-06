# PRODUCT.md — MeuCoach

**Versão:** 1.1 · **Data:** 06/11/2026 · **Status:** aprovado para a Fase 0; revisão 1.1 registra as decisões aprovadas da Etapa 3

**Histórico de revisão**

| Versão | Data | Mudança |
|---|---|---|
| 1.0 | 27/10/2026 | Visão, usuário 0, escopo do MVP e princípios iniciais. |
| 1.1 | 06/11/2026 | Registro das decisões aprovadas de captura, perfil, objetivo, backend, backup e determinismo. |

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
4. **Não me lesionar** nem acumular fadiga sem perceber.
5. **Confiar** no que o app diz: ver a fonte e o nível de confiança.
6. **Ter meus dados comigo** para sempre, exportáveis.

## 4. Escopo do MVP

### Dentro

- Importação manual de **FIT, TCX e GPX** (file picker + share intent).
- Deduplicação de atividades importadas em mais de um formato.
- Detalhe do treino: resumo, laps/splits, FC, pace, cadência, potência (quando houver), elevação, rota, zonas.
- Perfil e onboarding (dados antropométricos, objetivo, disponibilidade).
- Calibração por prova/TT recente (Tier A/B) ou teste guiado (Tier C).
- Motor de zonas (LTHR + pace), carga (TRIMP, rTSS, CTL/ATL/TSB, ACWR), prontidão e prescrição.
- Check-in diário de 15 s (sono, fadiga, dor, stress).
- Leitura de **Health Connect** (sono, RHR, HRV, SpO2, exercícios) com diagnóstico de sincronização e atualização automática ao abrir o app.
- Tela **Hoje** com prontidão, treino prescrito e "por quê", com um link direto para a recuperação.
- Tela **Saúde** como destino principal: painel de sono, FC de repouso, HRV e SpO₂, com período, fontes e aviso quando o dia atual ainda não chegou.
- Tela **Tendências** com gráficos de evolução.
- Notificações locais: treino do dia, lembrete de check-in, import pendente.
- Exportar/apagar todos os dados.
- Funcionar offline — exceto o mapa do story, que baixa tiles do `OpenStreetMap` no momento da geração (`ADR-0015`).

### Fora (explícito)

- Prescrição ou acompanhamento de musculação — o usuário tem rotina própria; o app só captura fadiga global no check-in.
- Nutrição, emagrecimento e prescrição alimentar.
- Diagnóstico, reabilitação ou tratamento de lesões.
- Integração direta com a API do Zepp (Fase 3, experimental).
- Social, ranking, feed e desafios (Fase 3).
- iOS, Wear OS e widgets (widgets na Fase 2; iOS não planejado).
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

## 6. Métricas de sucesso

| Métrica | Meta |
|---|---|
| Import de arquivo válido | ≥ 99% sem erro |
| Tempo até o primeiro insight após importar | < 2 min |
| Deduplicação FIT/TCX/GPX da mesma atividade | 100% |
| Cobertura do motor (`core:domain`) | ≥ 85% |
| Crash-free sessions | ≥ 99,5% |
| Check-in concluído em | ≤ 20 s |
| Usuário consegue explicar a recomendação | ≥ 80% em teste moderado |
| Dados diários com confiança A/B | ≥ 70% dos dias após 4 semanas |

## 7. Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Zepp não entrega dados diários | Health Connect + check-in + CSV; motor com confiança parcial |
| Dados de wearables imprecisos | Proveniência, validação cruzada, confiança explícita, nunca decisão crítica isolada |
| Overtraining / lesão | Guardrails, ACWR como sinal, sono, RPE, sinais vermelhos, disclaimer |
| Excesso de escopo | Fora de escopo explícito; fases com critérios de aceite |
| Complexidade de modularização | Começar com 4 módulos e crescer por feature |
| LGPD / dados nos EUA | Local-first; sync só na Fase 2 com consentimento e região documentada |
| "Mais um app de corrida" | Diferencial: explicabilidade + ciência + dados reais + design premium |

## 8. Privacidade e LGPD

- Por padrão, os dados ficam no dispositivo.
- Sync futuro é opt-in, com TLS, conta e possibilidade de apagar tudo.
- A VPS de produção está nos EUA (IONOS/Dokploy). Isso será declarado no app e no consentimento antes do sync.
- Sem venda de dados, sem ads, sem SDK de analytics por padrão.
- Exportação completa em formato aberto (JSON + arquivos originais).
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
| **Tier A/B/C** | Níveis de calibração por dados disponíveis |

## 10. Decisões aprovadas do MVP (06/11/2026)

Estas decisões complementam o escopo acima e orientam o primeiro ciclo. O detalhamento técnico está em `docs/PLANO_TECNICO_CICLO_1.md`.

| Tema | Decisão aprovada |
|---|---|
| Captura | **Import-first**: FIT/TCX/GPX + Health Connect + entrada manual; sem GPS nativo no MVP. |
| Perfil | **Um perfil por instalação**; sem `profileId` especulativo nem multi-perfil nesta fase. |
| Objetivo | **Um objetivo ativo por vez**; objetivo geral ou prova (5K/10K/meia/maratona). |
| Backend | **Sem backend obrigatório**; o app é útil offline e sem conta. Integração externa futura não é antecipada. |
| Backup | **Versionado e restaurável** como funcionalidade de ciclo posterior; não bloqueia o ciclo 1. |
| Motor | **Determinístico**; regra, evidência e confiança em toda recomendação. LLM não decide treino. |
| Conclusão | `ACTIVITY` > `GUIDED` > `MANUAL`; descanso, doença e não realizado têm estados próprios (ADR 0024). |
| Plano × prescrição | Plano é intenção; prescrição diária persistida é a única autoridade do dia (ADR 0023). |
| Exclusão | Lixeira de 7 dias para atividade; reset imediato, com época de dados e sem apagar Health Connect (ADR 0025). |
| Carga | ACWR desconhecido/cobertura insuficiente bloqueiam qualidade; ACWR é flag, nunca diagnóstico (ADR 0026). |

> O prazo de 30 dias de lixeira citado no PRD original (FR-009) foi substituído por **7 dias** na decisão aprovada; a reconciliação dos documentos entra no C1-06.
