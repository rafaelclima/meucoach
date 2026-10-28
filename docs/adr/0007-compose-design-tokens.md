# ADR-0007 — Compose + Material 3 + tokens de marca

**Status:** Aceito · **Data:** 27/10/2026

## Contexto

O produto precisa de estética premium, dark-first, com gráficos densos e identidade própria. Material 3 oferece base sólida, mas o visual padrão é genérico. Dynamic color diluiria a marca.

## Decisão

- Jetpack Compose + Material 3 como base.
- Tokens de marca em `design/tokens/meucoach.tokens.json` e implementação em `core:designsystem`.
- **Sem dynamic color**; a paleta é fixa (dark/light).
- Geist (display) + Inter (texto), com numerais tabulares.
- Componentes próprios (`McCard`, `McGauge`, `McZoneBar`, etc.) sobre a base Material.
- Vico para gráficos de linha/barra; Canvas para gauge/heatmap/zone bar.

## Alternativas

- **Material puro:** rápido, mas sem identidade.
- **Design system 100% custom:** controle total, mas reinventa acessibilidade e componentes.
- **Dynamic color:** personalização, mas quebra a marca e a consistência dos gráficos.

## Consequências

- Boa: identidade forte, consistência, acessibilidade herdada.
- Ruim: mais trabalho de componentes e manutenção de tokens.
- Tokens são a fonte de verdade; hex solto é proibido.
