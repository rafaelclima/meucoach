# ADR-0002 — Local-first com sync opcional

**Status:** Aceito · **Data:** 27/10/2026

## Contexto

Dados de saúde são sensíveis, o usuário treina em locais sem rede e não quer depender de conta. Ao mesmo tempo, backup e múltiplos dispositivos são desejáveis no futuro.

## Decisão

- Room/SQLite no dispositivo é a fonte de verdade do MVP.
- Nenhuma feature do MVP depende de rede.
- Sync será opt-in na Fase 2, com fila, IDs UUID e resolução de conflito explícita.
- Exportação completa (JSON + arquivos originais) é parte do produto.

## Alternativas

- **Cloud-first:** experiência multi-dispositivo imediata, mas quebra offline, privacidade e simplicidade.
- **Somente local para sempre:** simples, mas perde backup e continuidade.

## Consequências

- Boa: privacidade, velocidade, confiabilidade, uso sem conta.
- Ruim: sincronização futura exige desenho cuidadoso de conflitos e migrações.
- O backend nunca será pré-requisito para o app funcionar.
