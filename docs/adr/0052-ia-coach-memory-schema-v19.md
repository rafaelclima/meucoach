# ADR 0052 — Memória local do IA Coach (schema v19, TTL e revogação)

**Status:** Aceito e implementado em 2026-11-18 (IA Coach F3/Room v19).
**Data:** 2026-11-18
**Escopo:** `core/data/db/entity/Coach*`, `CoachMemoryRepository`,
`MeuCoachDatabase.MIGRATION_18_19`, `CoachViewModel`, `UserDataRepository`,
`DataManagementScreen`, `docs/PRD_IA_COACH.md`.
**Relacionados:** ADR 0002 (local-first), ADR 0020 (backup/export), ADR 0025
(exclusão e época de dados), ADR 0051 (proxy stateless).
**PRD:** `docs/PRD_IA_COACH.md` decisões 10, 14, 16, 17, 24 e 25.

---

## 1. Contexto

### 1.1 O problema

O Coach já explica a semana e revisa a semana fechada, mas não lembra nada entre
aberturas da aba. O PRD pede histórico da conversa e um resumo persistido
(decisões 16, 17 e 24), com controle do usuário (decisão 25) e exclusão na
revogação do opt-in (decisão 10). A memória precisa existir **sem** virar uma
segunda autoridade de treino: o motor continua decidindo carga, pace e plano
(decisão 14).

### 1.2 Restrições

- O proxy é stateless (ADR 0051): qualquer memória é local.
- Nenhuma chamada de IA nova nesta fatia; só persistência e ciclo de vida.
- Qualquer texto exibido já passou pelo `CoachResponseValidator`.
- Revogar o opt-in bloqueia chamadas e apaga a memória; export/delete a incluem.
- Sem `fallbackToDestructiveMigration`; migração explícita e testada.
- Dado sensível não pode ser reconstruído a partir de prompt bruto, GPS ou nome.

### 1.3 Alternativas avaliadas

| Alternativa | Por que não |
|---|---|
| Memória no proxy | Contradiz o proxy stateless e amplia o risco LGPD do MVP. |
| Preferências/JSON em arquivo | Sem transação, TTL, export nem exclusão consistentes com o resto do app. |
| Só cache em memória | Não sobrevive à morte do processo e não atende histórico/resumo do PRD. |
| Tabela única para tudo | Mistura conversa e resumo, impede uma linha por semana e complica o TTL. |

---

## 2. Decisão

### 2.1 Schema v19

`MIGRATION_18_19` é aditiva e cria duas tabelas vazias; nenhuma tabela existente
é reescrita.

`coach_messages`:

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID v7 | PK |
| `kind` | `EXPLAIN` \| `REVIEW` | turno do Coach (não há texto livre do atleta no MVP) |
| `inputsHash` | String | contexto do motor que originou a resposta |
| `title` / `body` | String | texto validado |
| `bulletsJson` | JSON | bullets validados |
| `createdAt` | Long | epoch ms |

Índice `createdAt` para leitura cronológica e índice único
`(kind, inputsHash)`: reabrir a aba ou repetir a mesma avaliação não duplica a
conversa.

`coach_summaries`:

| Campo | Tipo | Notas |
|---|---|---|
| `weekStart` | LocalDate (TEXT) | PK; no máximo um resumo por semana fechada |
| `inputsHash` | String | hash do payload de revisão que originou o texto |
| `title` / `body` | String | narrativa validada |
| `bulletsJson` | JSON | bullets validados |
| `createdAt` | Long | epoch ms |

### 2.2 O que é persistido

- Somente texto que passou pelo `CoachResponseValidator`; resposta inválida,
  timeout ou fallback determinístico **não** viram memória.
- Nenhum prompt bruto, resposta crua do provedor, nome, GPS, timestamp exato de
  atividade ou texto livre do atleta.
- No MVP, o resumo é a narrativa LLM validada da semana fechada (decisão 24),
  ligada ao `inputsHash` da revisão; não é uma regra nem um número novo.
- `CoachViewModel` grava a explicação (`EXPLAIN`), a revisão (`REVIEW`) e o
  resumo da semana (`coach_summaries`) apenas com opt-in ativo.
- A memória **não** entra em prompt nesta fatia. Quando entrar, continuará sendo
  contexto de copy e todo texto voltará a passar pelo validador.

### 2.3 TTL

- `coach_messages`: **90 dias** desde `createdAt`.
- `coach_summaries`: **180 dias** desde `createdAt`.
- A expiração é preguiçosa: `CoachMemoryRepository.pruneExpired()` roda na
  abertura da aba e antes de cada escrita. Não há worker novo.
- O limite é inclusivo: uma linha exatamente no corte ainda é válida; só o que é
  estritamente mais antigo é apagado (`CoachMemoryPolicy`).

O TTL é política de produto, não regra fisiológica, e existe para limitar o
acúmulo de texto sensível sem perder o ciclo de treino corrente.

### 2.4 Consentimento, revogação e exclusão

- Gravar memória exige opt-in; sem consentimento, nem a chamada nem a escrita
  acontecem.
- Revogar bloqueia chamadas imediatamente, limpa `coach_messages` e
  `coach_summaries` em escopo de aplicação (a exclusão termina mesmo se a aba
  fechar) e não toca em treinos, perfil, check-ins ou métricas.
- `UserDataRepository.exportJson()` inclui `coachMessages` e `coachSummaries`.
- “Apagar tudo” apaga as duas tabelas na mesma transação das demais e a UI
  informa quantas mensagens/resumos do Coach foram removidos.
- O proxy continua sem qualquer cópia; revogar no aparelho encerra a memória
  local, não um histórico remoto.

### 2.5 Autoridade do motor

- A memória é **contexto de copy**, nunca fonte de carga, pace, FC, volume,
  plano ou recomendação.
- O resumo guarda o `inputsHash` da revisão, o que permite auditar a origem sem
  reconstruir uma decisão a partir do texto.
- Nenhum `ruleId`/`evidenceId` nasce da memória.

---

## 3. Consequências

### 3.1 Positivas

- Histórico e resumo sobrevivem à morte do processo sem sair do aparelho.
- A migração é aditiva, testada e reversível por backup/export; o app abre com
  banco v18 existente.
- O TTL e a revogação reduzem o acúmulo de texto sensível.
- O índice único evita conversa duplicada por reabertura/retry.
- A narrativa persistida é sempre a que o validador aceitou.

### 3.2 Negativas / trade-offs

- Duas tabelas novas aumentam o schema e o export.
- A expiração é preguiçosa: uma instalação sem abrir a aba mantém linhas até a
  próxima abertura/escrita.
- O resumo depende de uma revisão validada; sem IA não há resumo novo (o
  determinístico continua na tela, mas não vira memória).
- Ainda não existe UI para listar/apagar mensagem individual; a decisão 25 é
  atendida por revogação/apagar tudo nesta fatia, e a lista visual fica para a
  próxima.

### 3.3 Não objetivos

- Busca semântica, embeddings ou resumo por LLM que decida treino.
- Memória remota, sincronização ou multiusuário.
- Edição de mensagem/resumo.
- Migração destrutiva ou `fallbackToDestructiveMigration`.

---

## 4. Rollout

- Schema v19 gerado por KSP (`19.json`), migração explícita registrada em
  `MeuCoachDatabase.create`.
- Fluxo manual do usuário 0: abrir a aba com opt-in, ver a explicação, revogar,
  confirmar que o Coach volta ao opt-in e que os treinos continuam.
- Exportar backup e conferir `coachMessages`/`coachSummaries`; “apagar tudo” e
  conferir a mensagem de exclusão.
- Sem chamada real de IA no CI; testes usam fake/in-memory.

---

## 5. Registro de implementação

- **18/11/2026:** entidades/DAOs `CoachMessage*`/`CoachSummary*`, schema v19 e
  `MIGRATION_18_19`; `CoachMemoryPolicy` (90/180 dias) puro no domínio;
  `CoachMemoryRepository` com gravação validada, TTL preguiçoso e `clearAll`;
  `CoachViewModel` grava somente com opt-in e revoga em escopo de aplicação;
  export/delete e copy da tela Dados incluem o histórico.
- **Testes:** política de TTL (3), repositório (6), migração 18→19 (1),
  export/delete (2 asserts novos), ViewModel (4 novos) e mensagem de exclusão
  (1 novo). Sem dados reais.

---

## 6. Referências

- `docs/PRD_IA_COACH.md` §1 (decisões 10/14/16/17/24/25), §5 e §9.
- `docs/adr/0051-ia-coach-proxy.md` (proxy stateless).
- `core/domain/src/main/kotlin/com/meucoach/core/domain/coach/CoachMemoryPolicy.kt`.
- `core/data/src/main/kotlin/com/meucoach/core/data/coach/CoachMemoryRepository.kt`.
- `core/data/src/main/kotlin/com/meucoach/core/data/db/MeuCoachDatabase.kt`.
