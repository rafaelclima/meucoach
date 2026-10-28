# ADR-0001 — Monorepo Android + backend futuro

**Status:** Aceito · **Data:** 27/10/2026

## Contexto

O produto começa como app Android local-first, mas a arquitetura deve permitir sync e backend Ktor/Postgres no futuro. O domínio é puro e reutilizável.

## Decisão

Manter um único repositório com:

- Projeto Gradle Android na raiz (`:app`, `:core:*`, `:feature:*`).
- Backend como build separado em `backend/` a partir da Fase 2, reutilizando `core:model` e `core:domain`.
- Infraestrutura em `infra/`.
- Documentação em `docs/` e ADRs em `docs/adr/`.

## Alternativas

- **Repositórios separados:** isolamento, mas sincronização de modelos/regras custosa para um desenvolvedor.
- **KMP desde já:** compartilharia código, mas adiciona complexidade de build e tooling sem necessidade no MVP.

## Consequências

- Boa: um histórico, uma revisão, modelos compartilhados, CI único.
- Ruim: builds mais pesados; exige disciplina para não acoplar Android ao backend.
- Migração para KMP possível no futuro, mantendo `core:domain` puro.
