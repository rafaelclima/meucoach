# DECISIONS.md — Índice de decisões

**Versão:** 1.0 · **Data:** 27/10/2026

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

## Decisões menores (registro rápido)

- **Nome:** MeuCoach (pt-BR, pessoal, sem marca corporativa).
- **Idioma:** UI em pt-BR; código em inglês.
- **Unidades:** SI interno; exibição métrica (km, min/km).
- **Tema:** dark-first, light completo; sem dynamic color.
- **Fonte:** Geist (display) + Inter (texto), OFL.
- **Acentuação:** lima como única cor de ação; coral para fisiologia.
- **Gráficos:** Vico 3.3.1 para line/bar; Canvas para gauge/zone bar/heatmap; eixos em pt-BR.
- **Permissões:** notificação só no contexto (Perfil → Ajuda/checklist), nunca no launch; o app segue utilizável se o usuário recusar.
- **Tutorial:** camada sobre o Hoje, 3 cenas com dados reais e checklist dismissível; sem streak, sem dados inventados.
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
