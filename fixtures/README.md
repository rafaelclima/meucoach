# Fixtures

Fixtures são arquivos de teste. Elas nunca devem conter dados de saúde reais identificáveis.

## Regras

- Dados reais do usuário 0 ficam em `fixtures/private/` (ignorado pelo git) ou fora do repositório.
- Fixtures commitadas são **sanitizadas** ou **sintéticas**.
- O CI usa apenas fixtures commitadas.
- Scripts atuais:
  - `tools/sanitize_fit.py`: opera no nível de bytes do FIT, invalida posições, limpa serial/IDs de dispositivo, troca o developer field `source` por `example.invalid`, preserva timestamps e recalcula os CRCs.
  - `tools/sanitize_tcx.py`: remove `Position`, `Creator`, `Notes` e `Author`, preserva timestamps e métricas e valida o XML de saída.
- A saída FIT deve abrir com `fitdecode` antes de ser commitada.
- `__pycache__/` e `*.pyc` são ignorados pelo git.

## Estrutura atual

```
fixtures/
  fit/
    Zepp20261022195619.fit   # 4 FIT reais sanitizados (~132 KB)
    Zepp20261024051710.fit
    Zepp20261026202359.fit
    Zepp20261027052000.fit
  tcx/
    Zepp20261008052132.tcx   # 3 TCX reais sanitizados (~1,9 MB)
    Zepp20261013052204.tcx
    Zepp20261016053227.tcx
  gpx/
    synthetic-extensions.gpx     # GPX sintético com hr/cad/speed
    synthetic-no-elevation.gpx   # GPX sintético sem elevação
  private/                   # IGNORADO — dados reais
```

Os TCX reais são corridas de 5 km de outubro/2026. As posições foram removidas, portanto o parser é exercitado sem GPS nesses arquivos; posições e múltiplos laps são cobertos por fixtures sintéticas. GPX real não é commitado: os arquivos originais têm nomes de eventos e localização, e as fixtures sintéticas cobrem as extensões necessárias.

## Como adicionar uma fixture

1. Verifique se o arquivo é realmente necessário.
2. Para FIT, rode `python3 tools/sanitize_fit.py <origem> --out fixtures/fit`.
3. Para TCX, rode `python3 tools/sanitize_tcx.py <origem> --out fixtures/tcx`.
4. Para outros formatos, remova dados pessoais e substitua posições por valores inválidos ou coordenadas sintéticas.
5. Rode o parser e confirme que o resumo esperado bate.
6. Documente a origem e a transformação em um `README.md` ao lado, sem incluir dados identificáveis.
7. Confirme que `git status` não mostra `fixtures/private/` nem arquivos originais.
