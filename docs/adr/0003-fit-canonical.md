# ADR-0003 — FIT canônico; TCX/GPX fallback

**Status:** Aceito · **Data:** 27/10/2026

## Contexto

O Amazfit Balance exporta FIT com maior riqueza (potência, passada, laps, developer fields). TCX e GPX podem existir para a mesma atividade e não devem duplicá-la.

## Decisão

- FIT é o formato preferencial e a fonte canônica.
- TCX e GPX são suportados como fallback.
- Deduplicação por hash do arquivo + chave de atividade (início, esporte, duração, distância).
- Em conflito, FIT > TCX > GPX > Health Connect.
- O arquivo original é preservado; samples são normalizados para o modelo canônico.
- Cadência FIT (strides/min) é normalizada para passos/min (×2).

## Alternativas

- **Converter tudo para GPX:** perde potência, laps e developer fields.
- **Usar só o SDK oficial Garmin:** reduz controle e adiciona dependência; o subconjunto necessário é viável de implementar.

## Consequências

- Boa: dados mais ricos, dedup previsível, independência de fornecedor.
- Ruim: parser FIT próprio exige testes rigorosos e manutenção.
- Fixtures reais sanitizadas são obrigatórias para regressão.
