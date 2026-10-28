# ADR-0006 — Sem SQLCipher/biometria no MVP

**Status:** Aceito · **Data:** 27/10/2026

## Contexto

O banco contém dados de saúde. A proteção ideal incluiria criptografia em repouso e biometria. Porém, o app é pessoal, local-first, sem conta no MVP, e o custo de complexidade (migrações, performance, testes, UX) é alto.

## Decisão

- Não usar SQLCipher nem biometria no MVP.
- Mitigações mínimas:
  - Backup Android desativado (`allowBackup=false`).
  - Arquivos no storage privado do app.
  - Export/delete explícitos.
  - Sem analytics/ads.
  - TLS obrigatório quando houver sync (Fase 2).
  - Tokens no Android Keystore (Fase 2).
- Reavaliar SQLCipher se o app ganhar multi-usuário, sync com dados sensíveis ou requisito regulatório.

## Alternativas

- **SQLCipher + biometria agora:** mais seguro, mas adiciona dependência nativa, migrações e fricção de uso.
- **Sem nenhuma mitigação:** inaceitável.

## Consequências

- Boa: simplicidade, performance, menos bugs, foco no produto.
- Ruim: um dispositivo desbloqueado com acesso físico ao app pode ler os dados.
- A decisão é explícita e reversível com ADR futuro.
