# Fixtures

Fixtures são arquivos de teste. Elas nunca devem conter dados de saúde reais identificáveis.

## Regras

- Dados reais do usuário 0 ficam em `fixtures/private/` (ignorado pelo git) ou fora do repositório.
- Fixtures commitadas são **sanitizadas** ou **sintéticas**.
- Sanitização: GPS deslocado em grade fixa, nomes/notas/IDs de dispositivo removidos, timestamps preservados ou deslocados.
- O CI usa apenas fixtures commitadas.
- Script de sanitização: `tools/sanitize_fixtures.py` (Fase 0).

## Estrutura prevista

```
fixtures/
  fit/
    outdoor_run.fit
    treadmill_intervals.fit
    malformed_crc.fit
  tcx/
    outdoor_run.tcx
    no_laps.tcx
  gpx/
    outdoor_run.gpx
    extensions.gpx
  csv/
    daily_metrics.csv
  golden/
    G-001-usuario-0-baseline/
      input.json
      expected.json
      README.md
  private/          # ignorado
```

## Como adicionar uma fixture

1. Verifique se o arquivo é realmente necessário.
2. Remova dados pessoais e desloque o GPS.
3. Rode o parser e confirme que o resumo esperado bate.
4. Documente a origem e a transformação em um `README.md` ao lado.
5. Confirme que `git status` não mostra `fixtures/private/`.
