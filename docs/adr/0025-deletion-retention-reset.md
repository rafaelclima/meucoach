# ADR 0025 — Exclusão, lixeira de 7 dias, reset imediato e época de dados

**Status:** aceito
**Data:** 2026-11-06
**Responsável:** planejamento com Rafael (usuário 0)
**Implementação:** C1-05 concluída em 06/11/2026.

---

## 1. Contexto

“Apagar” hoje não é uma promessa cumprida por inteiro:

- `ActivityRepository.deleteActivity()` só grava `deletedAt`; não existe expurgo automático;
- não há tela de lixeira nem restauração explícita (a restauração só acontece por reimportação do mesmo SHA);
- `UserDataRepository.deleteAllData()` apaga as tabelas em uma transação e reseta a marca d’água do Health Connect, mas **não** cancela trabalhos, não limpa staging/cache/preferências e não impede que workers em voo recriem dados;
- `HealthSyncWorker`/`ImportWorker` podem começar antes do reset e gravar depois;
- `MeuCoachApplication.onCreate()` reagenda lembretes e sync de saúde mesmo sem perfil;
- o export JSON inclui atividades com `deletedAt` (a lixeira), contrariando a expectativa de que “apagado” não sai no backup;
- não existe época de dados para invalidar trabalho antigo;
- a documentação antiga promete um “recibo anônimo de exclusão por 30 dias”, que contradiz a exclusão local completa.

Ao mesmo tempo, o app lê dados de plataformas externas (Health Connect). Apagar os dados do app não deve apagar registros da plataforma sem autorização específica.

## 2. Problema

Sem uma política completa:

1. o usuário apaga uma atividade e ela continua no banco para sempre;
2. o usuário não consegue desfazer um erro de exclusão;
3. “apagar tudo” deixa arquivos, caches, preferências e trabalhos para trás;
4. um worker em voo pode repovoar o banco depois do reset;
5. o app pode reagendar lembretes/sync sem perfil, criando notificações órfãs;
6. o export pode carregar dados que o usuário considerava excluídos;
7. apagar dados do Health Connect por engano seria uma ação externa irreversível.

## 3. Decisão

### 3.1 Lixeira de 7 dias para atividades individuais

1. Excluir uma atividade a move para a **lixeira** (`deletedAt`), não a apaga imediatamente.
2. A lixeira é visível em “Dados”/“Corridas”, com:
   - data da exclusão;
   - dias restantes;
   - “Restaurar”;
   - “Apagar definitivamente”.
3. O prazo é de **7 dias corridos**, contados por `Clock` injetável no fuso do perfil.
4. Um worker diário (`TrashPurgeWorker`) remove atividades com `deletedAt` anterior a `agora − 7 dias`, em cascata para laps, samples, alternates e vínculos.
5. “Apagar definitivamente” ignora o prazo e remove na hora, com confirmação explícita.
6. A lixeira **não** entra no uso normal:
   - lista, Hoje, Tendências, carga, prontidão e exportação padrão ignoram `deletedAt != null`;
   - o export pode incluir a lixeira apenas com opção explícita (“incluir itens da lixeira”), desmarcada por padrão.
7. Reimportar um arquivo cuja atividade está na lixeira **não restaura silenciosamente**. O app informa que existe um item na lixeira e oferece “Restaurar”; a reimportação cria/retorna conflito até a decisão do usuário.
8. Vínculo com sessão (ADR 0024):
   - enquanto a atividade está na lixeira, a sessão deixa de contar como evidência `ACTIVITY`;
   - ao restaurar, o vínculo volta;
   - ao expurgar, o vínculo é removido e a sessão vira `LEGACY_UNVERIFIED` (ou `CLOSED`, conforme o caso), sem apagar o histórico da sessão.

### 3.2 Reset imediato de todos os dados locais

“Apagar tudo” é **imediato, transacional e sem lixeira**. Ele:

1. grava uma **nova época de dados** antes de qualquer limpeza;
2. cancela trabalhos únicos/periódicos de saúde, importação, lembretes e story;
3. apaga, em uma transação:
   - perfil;
   - atividades, laps, samples, alternates, jobs de importação;
   - check-ins;
   - métricas diárias;
   - planos, sessões, prescrições e execuções;
   - quaisquer novos dados de evidência;
4. limpa fora do banco:
   - staging de importação;
   - arquivos originais copiados para o app;
   - cache de story e mapas;
   - arquivos de avatar/foto;
   - preferências de dados: sync do Health Connect, tutorial, dicas, métricas de primeiro uso, falhas de story e flags equivalentes;
5. reseta a marca d’água do Health Connect (o app volta a ler a janela inicial de 30 dias quando houver um novo perfil);
6. **não** apaga registros do Health Connect nem de qualquer plataforma externa; apenas os dados locais do MeuCoach;
7. **não** mantém recibo anônimo de exclusão. O único metadado preservado é a época de dados, que não contém informação de saúde nem identifica o usuário;
8. devolve o app ao onboarding; nenhum lembrete, sync ou importação é reagendado até existir perfil.

### 3.3 Época de dados (`DataEpochStore`)

- Um valor opaco (UUID v7 ou timestamp monotônico) é gravado em um `SharedPreferences` próprio (`meucoach_data_epoch`), separado das preferências apagadas no reset.
- O reset **sempre** grava uma época nova e única antes de limpar o banco. O arquivo da época não é apagado.
- Todo trabalho assíncrono que pode gravar dados captura a época no início e a relê imediatamente antes de persistir:
  - `HealthSyncService`/`HealthSyncWorker`;
  - `ImportWorker`/`StagedImportProcessor`;
  - `StoryExportWorker` (cache/arquivo);
  - qualquer futura importação de Health Connect.
- Se a época mudou, o trabalho:
  - não grava;
  - limpa o staging/arquivo parcial que produziu;
  - retorna falha/cancelamento silencioso, sem recriar dados e sem notificar como sucesso.
- Além da época, todo worker verifica a existência de perfil. Sem perfil, é no-op.
- A época é a garantia durável; o cancelamento via WorkManager é melhor esforço e limpeza.

### 3.4 Ordem do reset

```text
1. DataResetCoordinator.acquire()          // mutex de processo
2. epoch = gerar nova época e persistir
3. cancelar trabalhos únicos/periódicos
4. transação de limpeza do Room
5. limpar staging, arquivos, cache, avatar e preferências de dados
6. resetar HealthSyncStore
7. liberar o mutex
8. UI volta ao onboarding; nenhum agendamento sem perfil
```

- O `DataResetCoordinator` compartilha um `Mutex` com os pontos de escrita críticos (importação, sync, execução) para reduzir a janela de corrida; a época cobre o que já estava em voo antes do mutex.
- Se a limpeza de arquivos falhar, o reset continua e reporta o que não foi possível remover; a próxima abertura tenta novamente. Nunca se declara sucesso sem verificar o banco e a época.

### 3.5 Exportação

- O export padrão exclui a lixeira.
- O export inclui somente dados locais do app; nunca chama APIs do Health Connect.
- A UI explica o que será apagado e que o reset não pode ser desfeito.
- O formato de backup versionado/restaurável é uma funcionalidade de ciclo posterior (decisão D5 aprovada como direção); este ADR não implementa restore.

## 4. Alternativas consideradas

1. **Exclusão imediata com confirmação, sem lixeira.**
   - Privacidade máxima, mas um toque errado é irreversível; não cobre o erro humano típico.
2. **Lixeira de 30 dias.**
   - Mais recuperação, mas retenção excessiva de dado de saúde e contradiz a marca de privacidade. A recomendação e a decisão aprovada são 7 dias.
3. **Lixeira de 30 dias com expurgo, como no PRD original.**
   - Mantém a contradição entre promessa e prática; o PRD FR-009 é ajustado para 7 dias no ciclo de documentação (C1-06).
4. **Undo de 10 segundos + hard delete.**
   - Simples, mas não cobre erro descoberto no dia seguinte.
5. **Apagar também os dados do Health Connect no reset.**
   - Perigoso e fora do escopo do app; exige autorização específica e APIs próprias. O reset só remove a cópia local.
6. **Guardar a época no Room.**
   - A época precisa sobreviver à limpeza do banco; um `SharedPreferences` separado é mais simples e não cria dependência circular.
7. **Confiar apenas no cancelamento do WorkManager.**
   - Cancelamento é assíncrono e não é garantia de que um worker já em execução não grave; a época é a barreira dura.
8. **Manter recibo anônimo de exclusão por 30 dias.**
   - Contradiz “apagar todos os dados locais” e não é necessário para o produto. Não será mantido.

## 5. Justificativa

Uma lixeira curta cobre o erro humano sem transformar dados de saúde em arquivo morto. Sete dias é o equilíbrio aprovado; 30 dias é retenção demais para o risco.

O reset precisa ser absoluto porque é a promessa de privacidade do produto. Cancelar trabalhos e limpar cache sem época é frágil: um worker já em execução pode gravar depois. A época é um mecanismo pequeno, auditável e testável que impede a ressurreição de dados sem exigir criptografia ou backend.

Não apagar o Health Connect é deliberado: o app não é dono desses registros. Apagar dados de plataforma exige uma ação separada, explícita e com consequências compreendidas.

## 6. Consequências

### 6.1 Positivas

- “Apagar” passa a significar apagar de verdade, com recuperação curta e explícita.
- O reset não é desfeito por workers, imports, sync ou lembretes.
- O export não carrega lixeira por engano.
- A época protege contra corridas sem introduzir um serviço ou dependência nova.
- A UI pode explicar retenção e irreversibilidade com clareza.
- Dados do Health Connect permanecem sob controle da plataforma.

### 6.2 Negativas e custos

- Mais uma tela/estado (lixeira) e um worker de expurgo.
- Reset precisa conhecer todos os diretórios e preferências; se um novo store for criado sem ser registrado, pode sobrar lixo. Um checklist/teste de limpeza é obrigatório.
- A época em `SharedPreferences` não é transacional com o Room; a ordem e o mutex precisam ser bem testados.
- Reimportar um arquivo na lixeira deixa de restaurar automaticamente; é uma mudança de comportamento.
- O expurgo apaga vínculos de sessão; a sessão vira não verificada, o que pode surpreender.
- Nenhuma criptografia em repouso (ADR 0006); o risco continua documentado.

### 6.3 Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Expurgo apagar antes do prazo | Clock fixo, teste de 6d23h/7d/7d1h, confirmação e transação |
| Reset deixar cache/staging/prefs | Checklist de limpeza + teste que varre os diretórios conhecidos |
| Worker gravar após reset | Época + perfil + mutex; teste com worker simulado e latch |
| Reimportação restaurar item da lixeira | Teste explícito de conflito; mensagem “Restaurar” |
| Export vazar lixeira | Teste de export padrão sem `deletedAt`; opção explícita separada |
| Expurgo remover vínculo e distorcer adesão | Regra `LEGACY_UNVERIFIED` e teste de adesão |
| Apagar HC por engano | Reset não chama APIs de exclusão do HC; texto explica que os dados da plataforma permanecem |
| Reset parcial por falha de arquivo | Reportar itens não removidos e tentar novamente; nunca fingir sucesso |

## 7. Impactos arquiteturais

- **`core:data`:**
  - `ActivityDao.purgeDeletedBefore(cutoff)`;
  - consultas de lixeira (`observeTrash`, `restore`, `permanentlyDelete`);
  - `UserDataRepository.resetAll()` com escopo ampliado;
  - `DataEpochStore` (SharedPreferences separado);
  - `DataResetCoordinator` com `Mutex`;
  - `HealthSyncService`/`HealthSyncWorker` e `StagedImportProcessor` com checagem de época/perfil;
  - export com flag `includeTrash = false` por padrão.
- **`:app`:**
  - `TrashPurgeWorker` + agendamento diário;
  - `DataManagementViewModel`/`DataManagementScreen` com lixeira, confirmação e lista de consequências;
  - `MeuCoachApplication` não reagenda lembretes/sync sem perfil;
  - `ReminderScheduler.cancelAll()`;
  - `StoryExportCoordinator.cancelAll()`/limpeza de cache;
  - após reset, voltar ao onboarding.
- **`core:domain`:** regra pura de “expurgável” (`deletedAt + 7 dias`) e de suspensão de evidência; nenhum Android.
- **Room:** a coluna `deletedAt` já existe; não é necessária migração só para a lixeira. Se o expurgo exigir índice, entra como migração `12 → 13` em C1-05, testada e sem `fallbackToDestructiveMigration`.
- **Testes:** clock fixo, `WorkManagerTestInitHelper`, `TestListenableWorkerBuilder`, fake de Health Connect, latch de concorrência, export, migração.
- **Documentação:** `DATA_MODEL.md`, `ARCHITECTURE.md`, `PRODUCT.md` e `TESTING.md` atualizados no C1-06; o PRD FR-009 (30 dias) é reconciliado com este ADR (7 dias).

## 8. Dependências

- C1-01 (clock/fuso) para expurgo e contagem de 7 dias.
- ADR 0024 (evidência) para suspender/retomar/remover vínculo.
- `WorkManager` (já existente) para o expurgo diário.
- Health Connect (já existente) apenas para resetar a marca d’água local; nenhuma API de exclusão externa.
- Nenhuma dependência nova.

## 9. Relação com os requisitos do PRD

| Requisito | Como este ADR atende |
|---|---|
| **FR-009 — Exclusão efetiva, lixeira e retenção** | Lixeira de 7 dias, expurgo, reset imediato e export sem lixeira. O prazo de 30 dias do PRD é substituído por decisão aprovada. |
| **NFR-002 — Privacidade e local-first** | Nada é apagado em plataformas externas; reset é local e explícito. |
| **NFR-003 — Retenção e exclusão** | Dados apagados não reaparecem; época + cancelamento + limpeza. |
| **SEC-01 / SEC-02** | Expurgo e não ressurreição por workers. |
| **BL-003 — Exclusão e retenção efetivas** | Entrega o bloco. |
| **FR-008 — Backup** | O export passa a excluir a lixeira; restore versionado fica para o ciclo posterior. |

## 10. Referências

- `docs/PRD_COACH_CORRIDA.md` §6 (FR-009), §7 (NFR-002, NFR-003), §2.1 (SEC-01, SEC-02)
- `docs/REVISAO_E_DIRECIONAMENTO_PRODUTO.md` §5.D6, §6.4, §7.7
- `docs/DATA_MODEL.md` §1 (soft delete) e §9 (exportação)
- `docs/adr/0002-local-first.md`
- `docs/adr/0006-no-sqlcipher.md`
- `docs/adr/0022-deterministic-time-and-week.md`
- `docs/adr/0024-completion-evidence.md`
- `docs/PLANO_TECNICO_CICLO_1.md` §6.5 e §7
