# ADR-0009 — Musculação fora do escopo

**Status:** Aceito · **Data:** 27/10/2026

## Contexto

O usuário 0 já segue uma rotina própria de musculação 6×/semana. O app é focado em corrida. Prescrever ou rastrear força aumentaria escopo, complexidade e risco de conflito com a rotina existente.

## Decisão

- O app **não prescreve, não avalia e não rastreia** musculação.
- A fadiga global da musculação entra apenas pelo check-in subjetivo (fadiga/dor/stress).
- O planejador de corrida evita qualidade no dia seguinte a sessões pesadas de perna, quando o usuário sinaliza.
- Nenhuma tela de força, catálogo de exercícios ou periodização de hipertrofia.

## Alternativas

- **Integrar musculação:** mais completo, mas fora do problema central e do desejo do usuário.
- **Ignorar completamente:** risco de prescrição inadequada; por isso o check-in captura fadiga global.

## Consequências

- Boa: foco, escopo menor, respeito à rotina do usuário.
- Ruim: o app não sabe a carga exata da academia; a prontidão subjetiva compensa parcialmente.
- Reavaliar apenas se o produto mudar de posicionamento.
