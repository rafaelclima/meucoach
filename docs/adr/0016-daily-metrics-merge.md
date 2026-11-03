# ADR 0016 — Mescla de métricas diárias por campo

**Status:** aceito em 03/11/2026

## Contexto

Métricas diárias chegam de duas fontes que podem cobrir o mesmo dia:

- **Health Connect** (sync automático de sono, FC de repouso, HRV e SpO₂);
- **CSV** (importação manual de uma exportação do vestível).

`DailyMetricsRepository.saveAll` fazia upsert da linha inteira por data. Como
o `HealthMetricsAggregator` emite `null` para os campos que aquele dia não
trouxe, um sync do Health Connect apagava campos que só existiam no CSV — por
exemplo, HRV e SpO₂ — e trocava a `source` do dia. O caminho inverso também
acontecia: importar um CSV sobrescrevia valores do Health Connect.

O `ADR-0014` já registrava a mescla por fonte como evolução futura, e o
`DATA_MODEL.md` §6 já pedia "priorizar Health Connect e manter o CSV como
fallback". Faltava uma regra explícita e testada.

## Decisão

- `DailyMetricsMerger` (puro, `core:domain`) mescla um dia existente com um dia
  recebido, **campo a campo**.
- Precedência por campo: **`MANUAL` > `HEALTH_CONNECT` > `CSV`**.
- Uma fonte de menor precedência **nunca apaga** um valor já preenchido; ela só
  preenche campos nulos.
- Fontes de mesma precedência atualizam o dia, para que reimportar um arquivo
  corrija valores.
- `DailyMetricsRepository.saveAll` lê a linha existente dentro da mesma
  transação e grava o resultado da mescla, preservando `createdAt`.
- `source` guarda a **fonte dominante** (a de maior precedência com dados).
  Proveniência por campo não é persistida nesta versão.
- Nenhuma tabela ou coluna nova; nenhuma migração de Room.

## Alternativas rejeitadas

1. **Manter o upsert da linha inteira:** é a causa do problema; qualquer fonte
   com campos nulos apaga o dia.
2. **União sem precedência (último valor não nulo vence):** um CSV importado
   depois sobrescreveria o Health Connect, contrariando o `DATA_MODEL.md` §6.
3. **Tabela por fonte/campo:** permitiria proveniência exata, mas exige schema
   v10 e não muda a prontidão, que só consome os valores.
4. **Primeira escrita vence:** um CSV antigo bloquearia correções do Health
   Connect para sempre.
5. **Mesclar só quando a origem é diferente:** perderia a correção de um dia
   reimportado pela mesma fonte.

## Consequências

- Sync do Health Connect e importação de CSV deixam de disputar o dia: cada um
  contribui com os campos que tem.
- Correções do Health Connect continuam vencendo o CSV, e valores manuais
  continuam intocáveis.
- A `source` é aproximada quando o dia mistura fontes; nenhuma tela a exibe hoje.
  Se a proveniência por campo for necessária, entra em um ADR futuro com
  migração dedicada.
- O custo é uma leitura extra por dia dentro da transação; lotes de sync são de
  no máximo 30 dias.
- Apagar um dia continua removendo tudo; reimportar restaura o que a fonte
  trouxer.
