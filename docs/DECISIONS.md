# DECISIONS.md — Índice de decisões

**Versão:** 1.7 · **Data:** 10/11/2026

> Revisão 1.2 (06/11/2026): status de implementação dos ADRs 0022–0026 reconciliado com o ciclo 1; nenhuma decisão foi reescrita.
> Revisão 1.3 (08/11/2026): registro resumido do replanejamento implementado (R-01..R-03).
> Revisão 1.4 (08/11/2026): ADR 0028 extraído para `docs/adr/` e marcado como aceito/implementado. O ADR 0027 (sessão guiada) continua proposto. Nenhuma decisão histórica foi reescrita.
> Revisão 1.5 (10/11/2026): propostas C3-G0 `ADR 0030` (evento/taper) e `ADR 0031` (fingerprint/T4) registradas; nenhuma alteração de código.
> Revisão 1.6 (10/11/2026): usuário aprovou as decisões C3-G0; ADRs 0030/0031 aceitos em princípio, com implementação ainda pendente de gates. ADR 0029 permanece reservado para F4.
> Revisão 1.7 (10/11/2026): C3-01A (guarda de iniciante) e C3-01B (fingerprint/checkpoint, T4, migração 13→14 e aceite atômico) implementados; ADR 0031 passa a implementado e ADR 0030 segue pendente de C3-G1/C3-05.

Uma decisão arquitetural relevante vira um ADR. O ADR registra contexto, decisão, alternativas e consequências. Não reescrevemos o passado; se a decisão mudar, criamos outro ADR que a substitui.

| ADR | Decisão | Status | Data |
|---|---|---|---|
| [0001](adr/0001-monorepo.md) | Monorepo Android + backend futuro | Aceito | 27/10/2026 |
| [0002](adr/0002-local-first.md) | Local-first com sync opcional | Aceito | 27/10/2026 |
| [0003](adr/0003-fit-canonical.md) | FIT canônico; TCX/GPX fallback | Aceito | 27/10/2026 |
| [0004](adr/0004-deterministic-engine.md) | Motor determinístico, sem LLM no caminho crítico | Aceito | 27/10/2026 |
| [0005](adr/0005-health-connect.md) | Health Connect + check-in/CSV como fallback | Aceito | 27/10/2026 |
| [0006](adr/0006-no-sqlcipher.md) | Sem SQLCipher/biometria no MVP | Aceito | 27/10/2026 |
| [0007](adr/0007-compose-design-tokens.md) | Compose + Material 3 + tokens de marca | Aceito | 27/10/2026 |
| [0008](adr/0008-backend-dokploy.md) | Ktor + PostgreSQL + Dokploy na Fase 2 | Aceito | 27/10/2026 |
| [0009](adr/0009-strength-out-of-scope.md) | Musculação fora do escopo | Aceito | 27/10/2026 |
| [0010](adr/0010-availability-hard-cap-progression.md) | Disponibilidade como teto e progressão por qualidade | Aceito | 30/10/2026 |
| [0011](adr/0011-deload-block-anchor.md) | Deload ancorado em semanas consecutivas de plano | Aceito | 31/10/2026 |
| [0012](adr/0012-story-video.md) | Story de treino ao ar livre (vídeo com animação do percurso) — planejamento com Canvas+MediaCodec | Substituído parcialmente por ADR-0015 | 31/10/2026 |
| [0013](adr/0013-manual-session-swap.md) | Troca manual de sessões subordinada aos guardrails | Aceito | 31/10/2026 |
| [0014](adr/0014-incremental-health-sync.md) | Sync incremental do Health Connect via WorkManager | Aceito | 31/10/2026 |
| [0015](adr/0015-story-video-map.md) | Mapa real (OSM + MapLibre) e frame final no story de treino | Aceito — implementado; validação em aparelho pendente | 02/11/2026 |
| [0016](adr/0016-daily-metrics-merge.md) | Mescla de métricas diárias por campo, com precedência de origem | Aceito | 03/11/2026 |
| [0018](adr/0018-story-export-background.md) | Geração do story em segundo plano e cancelamento real | Aceito — implementado | 04/11/2026 |
| [0019](adr/0019-story-camera-projection.md) | Câmera 3D real no snapshot do story (port da projeção do MapLibre) | Aceito — implementado | 04/11/2026 |
| [0020](adr/0020-health-top-level-destination.md) | Saúde como 5ª aba e destino principal; Perfil sem o card grande | Aceito — implementado | 05/11/2026 |
| [0021](adr/0021-health-sync-on-resume.md) | Sync automático do Health Connect ao abrir o app (6 h) + worker diário | Aceito — implementado | 05/11/2026 |
| [0022](adr/0022-deterministic-time-and-week.md) | Tempo determinístico, fuso do perfil e virada de dia/semana | Aceito — implementado em C1-01 | 06/11/2026 |
| [0023](adr/0023-weekly-plan-vs-daily-prescription.md) | Plano semanal como intenção e prescrição diária como autoridade | Aceito — implementado em C1-03 | 06/11/2026 |
| [0024](adr/0024-completion-evidence.md) | Conclusão com evidência (`ACTIVITY`/`GUIDED`/`MANUAL`) e estados próprios | Aceito — implementado em C1-04; `GUIDED` no ciclo 3 | 06/11/2026 |
| [0025](adr/0025-deletion-retention-reset.md) | Lixeira de 7 dias, reset imediato e época de dados | Aceito — implementado em C1-05 | 06/11/2026 |
| [0026](adr/0026-load-coverage-quality-eligibility.md) | Cobertura de carga, ACWR desconhecido e elegibilidade de qualidade | Aceito parcialmente — gate implementado em C1-02; limiares de iniciante a validar | 06/11/2026 |
| [0028](adr/0028-versioned-plan-replanning.md) | Replanejamento versionado e sugerido da semana | Aceito — implementado em R-01/R-02/R-03 | 08/11/2026 |
| [0030](adr/0030-race-event-and-weekly-taper.md) | Evento de prova, autoridade diária e taper | Aceito — decisões de produto/arquitetura; migração/código pendentes | 10/11/2026 |
| [0031](adr/0031-profile-planning-fingerprint.md) | Fingerprint de planejamento e aceite atômico de T4 | Aceito — implementado em C3-01B (fingerprint, checkpoint, T4, migração 13→14 e compare-and-apply) | 10/11/2026 |

> ADR 0029 continua reservado pelo PRD v0.2 para F4 (conteúdo geral de hidratação/alimentação); não foi redigido porque fontes e copy ainda precisam de revisão.

## Decisões menores (registro rápido)

- **Nome:** MeuCoach (pt-BR, pessoal, sem marca corporativa).
- **Idioma:** UI em pt-BR; código em inglês.
- **Unidades:** SI interno; exibição métrica (km, min/km).
- **Tema:** dark-first, light completo; sem dynamic color.
- **Fonte:** Geist (display) + Inter (texto), OFL.
- **Acentuação:** lima como única cor de ação; coral para fisiologia.
- **Gráficos:** Vico 3.3.1 para line/bar; Canvas para gauge/zone bar/heatmap; eixos em pt-BR.
- **Permissões:** notificação só no contexto (Perfil → Ajuda/checklist), nunca no launch; o app segue utilizável se o usuário recusar.
- **Saúde:** 5ª aba principal (`Hoje · Corridas · Tendências · Saúde · Perfil`); o Hoje só linka “Ver recuperação” e o Perfil mantém link compacto + backup (ADR-0020).
- **Health Connect:** sync incremental no resume se a última leitura tem mais de 6 h; worker diário como fallback; o botão manual continua sendo override (ADR-0021).
- **Tempo:** dia e semana derivam do fuso do perfil, com `TimeProvider`/`Clock` injetável e virada observável (ADR-0022).
- **Plano × prescrição:** o plano é intenção; a prescrição diária persistida é a única autoridade do dia; adaptações são versionadas e auditáveis (ADR-0023).
- **Replanejamento:** regenera só o futuro da semana com o mesmo gerador, nunca aplica sozinho e versiona o aceite no Room; a recusa é por proposta (semana/versão/hashes) e a supressão vale até o fim do dia (ADR 0028, implementado em R-01..R-03).
- **Fingerprint de planejamento:** proveniência e último estado avaliado são separados; mudanças em input de plano ou no fuso/contexto temporal tornam a sugestão stale; o aceite revalida dentro da transação Room (ADR 0031, implementado em C3-01B).
- **Prova:** é um evento com estado próprio, não um `WorkoutType`; `TodayDecision` mostra treino ou competição, nunca os dois; atividade é vínculo opcional e não marca a prova como realizada sozinha (ADR 0030, direção aceita; implementação pendente).
- **Semana da prova:** sem `QUALITY` na semana local e cutoff de última qualidade de 10/14 dias como política `LIMITED` de produto, não consenso científico (ADR 0030).
- **Conclusão:** `DONE` exige evidência `ACTIVITY`, `GUIDED` ou `MANUAL`; descanso, doença e não realizado têm estados próprios; adesão ≠ execução comprovada (ADR-0024).
- **Exclusão:** lixeira de 7 dias para atividade; “apagar tudo” imediato, sem recibo, com época de dados que invalida workers em voo; Health Connect não é apagado (ADR-0025).
- **Carga/qualidade:** ACWR desconhecido, cobertura insuficiente ou fonte desatualizada bloqueiam qualidade; ACWR é flag, nunca diagnóstico (ADR-0026, implementado em C1-02).
- **Saúde (polimento):** aviso “os dados de hoje ainda não chegaram” quando há histórico e o dia atual falta, mais o link secundário “Abrir Health Connect”; resumo extra no Hoje adiado para não competir com “Iniciar”.
- **Tutorial:** camada sobre o Hoje, 3 cenas com dados reais e checklist dismissível; sem streak, sem dados inventados.
- **Ativação:** métricas locais (timestamps/contadores) no JSON de backup, sem SDK de analytics; dicas contextuais únicas e dismissíveis.
- **Backend:** só na Fase 2; app nunca depende dele.
- **Força:** fora do escopo; fadiga global no check-in.
- **Calor:** ajuste empírico conservador, confiança limitada.
- **ACWR:** flag, nunca meta.
- **TRIMP/rTSS:** calculados separadamente, nunca somados.
- **FC máxima:** observada; nunca 220−idade.
- **Cadência:** individual; sem alvo 180.
- **Mapa:** decidir na Fase 1 (MapLibre vs. polyline).
- **Testes:** JUnit4 + AssertJ + Robolectric/Roborazzi; Kover.
- **CI:** GitHub Actions, JDK 17, SDK 37.

## Como propor uma decisão

1. Descreva o problema e as forças.
2. Liste 2–3 alternativas com prós/contras.
3. Escolha e registre consequências (boas e ruins).
4. Atualize o índice e o documento afetado.
5. Se substituir uma decisão, marque a anterior como `Substituído por ADR-XXXX`.
