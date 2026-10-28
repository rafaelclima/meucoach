# Fixtures

Fixtures são arquivos de teste. Elas nunca devem conter dados de saúde reais identificáveis.

## Regras

- Dados reais do usuário 0 ficam em `fixtures/private/` (ignorado pelo git) ou fora do repositório.
- Fixtures commitadas são **sanitizadas** ou **sintéticas**.
- O CI usa apenas fixtures commitadas.
- O script de sanitização atual é `tools/sanitize_fit.py`.
- Ele opera no nível de bytes do FIT: substitui posições pelo sentinela inválido, limpa serial/IDs de dispositivo, troca o developer field `source` por `example.invalid`, preserva timestamps e recalcula os CRCs.
- A saída deve abrir com `fitdecode` antes de ser commitada.
- `__pycache__/` e `*.pyc` são ignorados pelo git.

## Estrutura atual

```
fixtures/
  fit/
    Zepp20261022195619.fit   # 4 FIT reais sanitizados (~132 KB)
    Zepp20261024051710.fit
    Zepp20261026202359.fit
    Zepp20261027052000.fit
  private/                   # IGNORADO — dados reais
```

TCX/GPX/CSV e golden scenarios serão adicionados nas próximas fases. Os 3 TCX antigos do usuário 0 estão fora do repositório e serão sanitizados no P0-06.

## Como adicionar uma fixture

1. Verifique se o arquivo é realmente necessário.
2. Para FIT, rode `python3 tools/sanitize_fit.py <origem> --out fixtures/fit`.
3. Remova dados pessoais de outros formatos e substitua posições por valores inválidos ou coordenadas sintéticas.
4. Rode o parser e confirme que o resumo esperado bate.
5. Documente a origem e a transformação em um `README.md` ao lado, sem incluir dados identificáveis.
6. Confirme que `git status` não mostra `fixtures/private/` nem arquivos originais.
