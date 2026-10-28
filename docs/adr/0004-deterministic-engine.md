# ADR-0004 — Motor determinístico, sem LLM no caminho crítico

**Status:** Aceito · **Data:** 27/10/2026

## Contexto

O diferencial do produto é confiança: toda recomendação deve ser explicável, reproduzível e auditável. LLMs podem alucinar números e não são apropriados para decisões de saúde.

## Decisão

- Zonas, carga, prontidão e prescrição são funções determinísticas em `core:domain`.
- Toda recomendação persiste `engineVersion`, regras, evidências e confiança.
- LLM, se introduzido, só reescreve o texto de um rationale já calculado; nunca escolhe treino, pace ou carga.
- Mudanças de regra exigem nova versão do motor e testes golden.

## Alternativas

- **LLM como coach:** flexível, mas imprevisível, difícil de validar e arriscado.
- **Somente regras fixas sem versionamento:** simples, mas impossível auditar mudanças.

## Consequências

- Boa: reprodutibilidade, testes, confiança, segurança.
- Ruim: menos "magia"; exige catálogo de regras e evidências bem mantido.
- A explicação pode ser mais rígida, mas é honesta.
