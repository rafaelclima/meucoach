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

## Addendum — 04/11/2026: Vico 3.3.1 em produção

Os gráficos cartesianos deixaram de ser Canvas ad hoc. A decisão original já previa Vico; a
implementação fixou a versão e o escopo:

- **Vico 3.3.1** (`com.patrykandpatrick.vico:compose`, Apache 2.0) apenas em `:core:designsystem`.
  `McLineChart` e `McBarChart` encapsulam a API, então nenhuma feature importa Vico diretamente.
- **Line/bar** cobrem PMC (CTL/ATL/TSB), volume, conclusão, prontidão, economia e as quatro métricas
  de saúde. **Canvas** permanece para gauge, zone bar, heatmap e sparkline, onde a marca e o
  comportamento (células, anel, barras empilhadas) são mais específicos do que uma biblioteca
  genérica entrega.
- **Eixos com unidade**, `yStep`/`yLabelCount` para rótulos redondos, marcador de toque, legenda e
  `contentDescription`. Lacunas (`null`) quebram a linha em vez de inventar continuidade.
- **Eixos dinâmicos** (FC de repouso, HRV, economia) vêm acompanhados de uma nota explícita
  (“Escala ampliada (X a Y unidade)…”) para não enganar; sono e SpO₂ usam faixas fixas com alvo visível.
- **Alternativas rejeitadas:** Compose Charts (menos controle de eixos/tooltip), KoalaPlot (API
  mais pesada), YCharts (sem manutenção desde 2023) e MPAndroidChart (baseado em View, não Compose).
  Canvas puro foi mantido só nos gráficos de marca.
- **Custo:** uma dependência pequena e multiplataforma, sem serviço/rede, com goldens de Tendências
  e Saúde cobrindo a regressão visual.
