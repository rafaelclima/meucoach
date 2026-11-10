# Relatório de alterações — PRD Ciclo 3 v0.1 → v0.2

**Data:** 10/11/2026
**Origem:** `docs/PRD_ADAPTACAO_E_BRIEFING.md` (v0.1), auditoria técnica e decisões da Etapa 3
**Novo documento:** `docs/PRD_ADAPTACAO_E_BRIEFING_v0.2.md`
**Status:** revisão documental; nenhuma implementação iniciada.

## 1. Resumo

A v0.2 mantém o objetivo de replanejamento por perfil e briefing do dia, incorpora
os riscos aceitos da auditoria e separa funcionalidades implementáveis de gates
arquiteturais/editoriais. A decisão de produto de manter dicas gerais de
hidratação/alimentação foi preservada, mas F4 não pode aparecer na UI até que fontes
e conteúdo sejam revisados. A prova foi reformulada como evento de domínio, sem
adicionar `WorkoutType.RACE`; o contrato de evento e sua relação com a autoridade
diária seguem bloqueadores explícitos do ADR 0030.

## 2. Alterações por seção do PRD

| Seção | Consolidação v0.2 |
|---|---|
| Ficha e contexto | Versão/status atualizados; registradas as fontes de auditoria/conciliação e a nova rodada de inspeção do código. Fatos sobre formulário e distâncias limitados ao que existe hoje. |
| Objetivos e não-objetivos | “Prova” passou a evento com adaptação semanal; briefing limitado à prescrição persistida; recuperação pós-prova, previsão/pace e distância customizada saíram do escopo implementável. |
| Decisões | Separadas decisões preservadas do v0.1 e D-1…D-6 aprovadas para v0.2, evitando colisão dos IDs antigos com os novos. |
| F1 — Perfil | Fingerprint versionado, allowlist/canonicalização, disponibilidade em hash separado, proveniência `generatedFrom` distinta de `lastEvaluated`, tratamento de `NoChange`, calibração e compare-and-apply dentro da transação. D-6 foi delimitada a `QUALITY → EASY` e a sessão `PLANNED`. |
| F2 — Prova | `RaceEvent` conceitual com estados e vínculo opcional a atividade; sem `WorkoutType.RACE`. Persistência separada é a recomendação sujeita a ADR. Taper é por data de sessão, sem `QUALITY` na semana local da prova e sem ativação. O evento no dia não tem política presumida: requer ADR para preservar a autoridade de `DailyPrescription`. |
| Taper e evidências | Mantidos os intervalos já documentados em `SPORTS_SCIENCE.md` como referência; 10/14 dias são apresentados como extremos conservadores **LIMITED / política de produto**, não como consenso científico. As fontes citadas sustentam taper geral, não os cutoffs exatos nem a proibição de qualidade na semana da prova. |
| F3 — Briefing | Substituídos blocos executáveis por briefing genérico vindo exclusivamente da prescrição vigente. Removidos ritmos, intervalos e alternativas inventados. Sessões concluídas mostram execução, sem briefing retrospectivo com perfil atual. |
| F4 — Conteúdo | Intenção geral preservada; eletrólitos, sais, ingestão dependente de contexto e copy alimentar universal ficam bloqueados até revisão de fontes/conteúdo e ADR 0029. `AGENTS.md` não recebe permissão ampla antecipada. |
| Requisitos | FR-101…FR-111 refinam hash, baseline, stale transacional, caminhos de salvamento e guarda de iniciante. F2 separa evento, taper, cutoff, tempo-alvo declarado, itens removidos/adiados e gate do dia da prova. F3 não exige soma de blocos. F4 torna-se condicional. |
| Dados/arquitetura | Descritos campos conceituais de proveniência/checkpoint, snapshots, export/reset e migração; schema v13 confirmado. A versão final da migração não é fixada antes de harmonizar ADRs pendentes. É recomendada tabela `race_events`, mas ainda não aprovada como contrato final. |
| UX/estados | Copy usa data/dia dinâmicos; notificação abre Hoje e deve partir da prescrição vigente; estados cobrem perfil legado, concorrência, prova passada/iminente, cancelamento, vínculo e execução concluída. |
| Testes e sequência | Incluídos casos de fingerprint/concorrência, migração e persistência, taper por data, estado do evento, briefing sem alvos inventados, histórico, notificação stale e checklist manual de TalkBack/fonte 200%. Entregas foram divididas em gates independentes. |

## 3. Conciliação dos achados da auditoria

| Achado | Tratamento na v0.2 | Estado |
|---|---|---|
| **AUD-001 — Taper** | Regra por data de sessão, sessão protegida, ausência de `QUALITY` na semana da prova e cutoff explicitamente limitado; `ADR 0030` precisa validar a tabela/copy. | Incorporado; cutoff numérico é gate, não afirmação científica. |
| **AUD-002 — `RACE`** | `RaceEvent` separado de sessão e `WorkoutType`; estados definidos conceitualmente; disponibilidade, atividade e dia da prova exigem ADR. | Incorporado; bloqueia implementação de F2 até resolver autoridade diária. |
| **AUD-003 — Pós-prova** | FR-207 fora do escopo implementável; realização explícita e política de recuperação ainda não definidas. | Adiado. |
| **AUD-004 — Fingerprint** | Versão/canonicalização, allowlist, `generatedFrom` versus `lastEvaluated`, legado, `NoChange` e causas múltiplas especificados. | Incorporado. |
| **AUD-005 — Concorrência** | Fingerprint atual deve ser comparado dentro da transação Room de aceite; caso contrário `Stale`. | Incorporado. |
| **AUD-006 — Iniciante** | `QUALITY → EASY`; regra só para `PLANNED`; `DONE/CLOSED`, prescrição persistida e execução histórica protegidas. | Incorporado; status-aware gate arquitetural precisa ser implementado/testado. |
| **AUD-007 — Briefing** | Derivação exclusiva da prescrição; sem segmentos, números ou alternativas não existentes; sem briefing retrospectivo. | Incorporado. |
| **AUD-008 — F4** | D-1 preservada; conteúdo e fontes passam por revisão antes do ADR 0029/código; sem conselho universal de eletrólitos/ingestão. | Incorporado parcialmente; implementação adiada. |
| **AUD-009 — Persistência/export** | Caminhos `saveWeek`, aceite, `NoChange`, revisões, export/reset e legado especificados; restore não é alegado. | Incorporado; depende de migração aprovada e testes de integração. |
| **AUD-010 — Copy/notificação** | Copy relativa à data, sem sábado fixo; notificação usa prescrição e abre Hoje; distância customizada continua fora. | Incorporado. |
| **AUD-011 — Sequência** | Separados guardas, hash/T4, fluxo de perfil, briefing, gate de prova e revisão F4; removida a dependência de template RACE no briefing v1. | Incorporado. |
| **AUD-012 — Testes** | Acrescentados stale concorrente, `NoChange`, edição repetida, fuso/datas, histórico, fonte 200%, TalkBack manual e notificação. | Incorporado; compatível com a ausência atual de `androidTest`/E2E no CI. |

## 4. Dependências arquiteturais novas ou confirmadas

1. **Perfil/T4:** os campos de fingerprint devem ser produzidos no domínio, persistidos
   com proveniência e checkpoint separados e propagados por todos os caminhos de
   escrita/export/reset. A transação de aceite precisa ler o perfil corrente no Room.
2. **`QUALITY → EASY`:** `GetTodayPrescription` atualmente inclui sessão `DONE` e
   `DailyPrescriptionInput` não contém status. A regra precisa de um gate status-aware
   na orquestração ou contexto equivalente; a UI não pode corrigir isso.
3. **Evento de prova:** código atual tem `TrainingGoal` dentro do perfil, não tem
   entidade de evento, e a prescrição é centrada em `PlannedSession`/`WorkoutType`.
   Uma tabela `race_events` é recomendada para ciclo de vida, estado e vínculo, mas
   duplicação/migração dos campos de objetivo exige ADR.
4. **Autoridade diária:** evento em dia com sessão existente não pode gerar duas
   instruções concorrentes. O comportamento de supressão/representação da prova deve
   ser decidido no ADR 0030, sem introduzir `RACE` por conveniência.
5. **Room/export/reset:** nova entidade ou colunas exigem migração explícita, snapshots
   compatíveis, export/reset e política de vínculo com atividade excluída. O app não
   possui restore/import de JSON hoje.
6. **Briefing:** não exige persistência de segmentos; depende da prescrição já
   persistida. Sessões concluídas não são reconstruídas com dados/profile atuais.
7. **Sequência Perfil → prova:** como o Ciclo 3 entrega T4 antes de extrair o objetivo
   para `RaceEvent`, o PRD exige uma projeção `PlanningGoal` normalizada. Ela deve
   preservar a mesma representação de distância/data quando a fonte migrar de
   `TrainingGoal` para o evento; se não for possível evitar T4 falso, a sequência
   precisa ser reaprovada antes do código.

## 5. Sequência C3 consolidada

1. **C3-G0:** fechar fingerprint/proveniência/concorrência documentalmente e definir
   a projeção canônica de objetivo; pesquisar evento sem código.
2. **C3-01A:** guarda diária `QUALITY → EASY`, status-aware e sem tocar histórico.
3. **C3-01B:** fingerprint, T4, migração/persistência e aceite atômico.
4. **C3-02:** avaliação ao salvar Perfil e Calibração, com sugestão, `NoChange` e limite.
5. **C3-03:** briefing de domínio genérico a partir da prescrição persistida.
6. **C3-04:** sheet, histórico de execução, acessibilidade, goldens e notificação.
7. **C3-G1:** ADR 0030 para `RaceEvent`, evento no dia, estados, cutoff e evidências.
8. **C3-05:** prova/taper 2A somente após ADR aceito.
9. **C3-G2:** revisão de fontes/copy de F4; não implica código. Uma implementação futura
   depende do resultado e do ADR 0029.

Recuperação pós-prova, pace/faixa prevista e limite de longão por fração da distância
não são entregas implementáveis desta versão.

## 6. Pendências que bloqueiam especificação final de F2/F4

- Qual resultado diário é mostrado no dia da prova sem transformar o evento em sessão
  nem competir com `DailyPrescription`.
- Se `COMPLETED` exige confirmação manual, atividade associada ou ambos; política de
  exclusividade e exclusão/lixeira do vínculo.
- Se e como migrar objetivos de prova já gravados no `AthleteProfile.goal`, inclusive
  metas antigas com data passada, sem inferir estado.
- Validar a janela de última qualidade. As referências consultadas sustentam taper
  geral, não o cutoff exato de 10/14 dias. Se a revisão não sustentar o cutoff, F2 não
  pode implementar uma substituição inventada.
- Revisar fontes e copy de F4 antes de criar regra/catálogo ou exibir dicas.

## 7. Verificação e preservação

- O código foi reinspecionado em `WeeklyPlanGenerator`, `PlanReplanner`,
  `DailyPrescriptionEngine`, `GetTodayPrescription`, `ApplyReplan`,
  `ReplanContextBuilder`, repositórios Room/perfil, `PlanJson`, export/reset,
  formulário e notificações.
- Foi confirmado Room v13, ausência de `RaceEvent`/`SessionBriefing`, limite atual de
  quatro distâncias no formulário de prova e ausência de estado de prova realizada.
- Fontes consultadas sobre taper: Bosquet et al. 2007 e revisão/meta-análise Wang et
  al. 2023. Elas não sustentam o cutoff exato por distância nem a proibição de
  intensidade na semana da prova.
- **Não foram executados testes/build**, pois esta é uma consolidação documental e
  nenhum código foi alterado.
- `docs/PRD_ADAPTACAO_E_BRIEFING.md` foi preservado integralmente. Esta revisão e este
  relatório são arquivos separados.

## 8. Adendo C3-G0 — decisões aprovadas em 10/11/2026

Após aprovação do PRD v0.2, o usuário aprovou as propostas C3-G0:

- `ADR 0030`: `TodayDecision` discrimina treino e evento; prova concluída exige ação
  explícita; vínculo com atividade é opcional; uma prova planejada ativa; cutoff
  10/14 dias aceito como política `LIMITED`.
- `ADR 0031`: fingerprint/versionamento e compare-and-apply; fuso separado em
  `temporalContextHash`, com data/semana calculadas do mesmo snapshot.

Os ADRs foram atualizados para status **Aceito em princípios** e incluídos em
`docs/DECISIONS.md`. `docs/ARCHITECTURE.md` e `docs/ROADMAP.md` receberam a direção
aprovada, marcada como não implementada. O PRD v0.2 foi atualizado para refletir as
decisões e deixar apenas migração, unicidade/FK e compatibilidade com planos legados
como gates de implementação. Nenhum código, migração ou teste foi alterado/executado.
