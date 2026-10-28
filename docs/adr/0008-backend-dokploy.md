# ADR-0008 — Ktor + PostgreSQL + Dokploy na Fase 2

**Status:** Aceito · **Data:** 27/10/2026

## Contexto

O MVP é local-first. Quando houver sync/backup, precisamos de um backend simples, barato e reproduzível, deployável em VPS IONOS com Dokploy. O domínio já é Kotlin puro, o que favorece reuso.

## Decisão

- Backend em **Kotlin/Ktor** (JVM), reutilizando `core:model` e `core:domain`.
- **PostgreSQL 17+** com migrações Flyway.
- **Docker Compose** para dev local; **Dokploy** para produção.
- TLS via Traefik/Let's Encrypt; backup diário; healthcheck.
- Auth: e-mail + senha (Argon2id), JWT curto + refresh rotativo; passkeys depois.
- Sync: delta por `updatedAt`, UUIDs no cliente, atividades imutáveis, conflitos de plano versionados.
- Arquivos em storage S3-compatible (MinIO local).

## Alternativas

- **Firebase/Supabase:** rápido, mas menos controle e possível lock-in.
- **Node/Go:** viável, mas não reutiliza o domínio Kotlin.
- **Sem backend:** não atende backup/multi-dispositivo futuro.

## Consequências

- Boa: reuso de código, deploy reproduzível, controle de custos.
- Ruim: mais infraestrutura para manter; exige disciplina de segurança/backup.
- Nada disso entra no caminho crítico do MVP.
