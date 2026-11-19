# Handoff — Consolidação da tela Coach

**Data:** 19/11/2026
**Commit de referência:** `56bff84` (auditoria) + Bloco A em `PROGRESS.md`
**Auditoria detalhada:** `docs/AUDITORIA_POS_IMPLEMENTACAO_COACH.md`
**Status:** Bloco A concluído; PRD de evolução **não encerrado**. Próxima fatia é o **Bloco B — reatividade**, não uma feature nova.

> Este documento é o bastão curto para a próxima sessão. Leia primeiro `AGENTS.md`, depois este handoff, depois a seção 1 e a matriz da auditoria. Não é necessário ler a auditoria inteira antes de começar a fatia.

---

## 1. Onde estamos

A implementação entregou:

- `CoachNextCard` no topo, `CoachGuardsCard`, `CoachProgressCard` e a reordenação do replan antes da explicação.
- Fontes read-only: `RoomCoachNextSource`, `RoomCoachGuardsSource`, `RoomCoachProgressSource`.
- Domínio puro: `CoachNextResolver`, `CoachGuards`, `CoachProgress`.
- Nenhuma migração, nenhuma dependência nova, memória/privacidade e replanejamento preservados.

Mas a auditoria encontrou problemas que mudam a **verdade da tela**:

1. **P-01 (crítico):** F1 mostra `PlannedSession`, não a `DailyPrescription`. Pode exibir “Ritmo forte · 35 min” com o dia rebaixado para fácil/descanso.
2. **P-02 (crítico):** prova não tem precedência no fluxo real. O gerador remove sessões na data do evento; o source só procura prova na data da sessão escolhida.
3. **P-03 (alto):** F1/F2/F4 ficam escondidos atrás do opt-in de IA; “Depois” não tem caminho de reativação.
4. **P-04 (alto):** F1 não tem semáforo/prontidão/sono; não responde “posso forçar?”.
5. **P-05 (alto):** F2/F4 e explicação/revisão não reagem a atividades, check-ins, planos ou replanejamento. Mostram dados velhos.
6. **P-06/P-07 (alto):** F4 não é evolução: não usa `strongMinutes`/base real/alvo, conta `LEGACY_UNVERIFIED` como adesão, descarta semanas sem plano e lê prontidão só de 14 dias.
7. **P-08 (alto):** F2 é lista passiva; sem ações, sem “por que protege”, sem guardas de sono/check-in.
8. **P-09 (alto):** `./gradlew check` está **vermelho** no detekt.
9. **P-10 (alto):** fallback exibe reason técnico (código interno); `"atl"` rejeita “atleta”; `unlockHint` cru mostra ACWR 1,50.
10. **P-11/P-12/P-13 (alto):** explicação fica velha após replan; estados vazios se contradizem; revisão esconde observações determinísticas quando a IA responde.

**O motor continua correto e nada é escrito por essas fontes.** O risco é de decisão errada na superfície Coach, dados velhos e perda de confiança.

**Bloco A entregue em 19/11/2026.** F1 agora usa a `DailyPrescription` como headline, prova vence sem sessão artificial, prontidão/sono aparecem só para hoje, F1/F2/F4 não dependem do opt-in de IA, estados vazios convergiram em `Onboarding` e o gate do detekt está verde. P-01, P-02, P-03, P-04, P-09, P-10 e P-12 fechados no código e nos testes; validação manual segue pendente.

---

## 2. Baseline de validação

| Item | Estado |
|---|---|
| `./gradlew :app:assembleDebug` | Verde |
| `./gradlew check` | **Verde** (detekt corrigido; lint incluso) |
| `./gradlew spotlessCheck` | Verde |
| `./gradlew koverVerify` | Verde |
| `:core:domain:test` / `:app:testDebugUnitTest` | Verde (1178 / 1105; 0 falhas) |
| `./gradlew :app:lintDebug` | Verde |
| Validação manual no aparelho | **Pendente** (`PROGRESS.md`) |

Suíte do repositório: **2.555 testes** (1178 domain / 1105 app / 232 data / 40 model), 0 falhas e 0 `@Ignore`.

Cobertura do app segue no gate de linha (78%); o Bloco B deve cobrir `RoomCoachPlanSource` (hoje 0% de branch) e os caminhos de segunda emissão.

---

## 3. Decisões a confirmar no início da sessão

Estas decisões são **recomendações da auditoria**; confirme com o usuário 0 antes de codar. Se mudarem, registre em ADR ou no `DECISIONS.md`.

1. **Consentimento separado da IA** — ✅ implementado no Bloco A
   - F1/F2/F4 são locais e aparecem sem opt-in; o opt-in vale só para explicação/revisão.
   - “Depois” mantém o CTA persistente “Ativar IA” com a distinção explícita entre motor local e IA.

2. **F1 amanhã**
   - Não existe prontidão de amanhã.
   - Recomendação: “Hoje” usa a prontidão/prescrição reais; “Amanhã” mostra o planejado + “a confirmação sai no check-in da manhã”. Não inventar semáforo para amanhã.

3. **F4**
   - Recomendação: corrigir para volume real/base/alvo, exclusões e delta.
   - Se não couber agora, **renomear** para “Resumo das últimas 4 semanas” e retirar a promessa de evolução; ou ocultar o card. Não manter o rótulo “evolução” mostrando outra coisa.

4. **F2**
   - Recomendação: entregar ação (“o que destrava e quando”) e guardas de sono/check-in.
   - Se ficar só informativa, o PRD precisa ser corrigido explicitamente.

5. **S1–S6**
   - Ficam fora desta fase. Não iniciar faixa de 7 dias, delta, plano B, pós-treino etc. antes de F1/F4 confiáveis.

---

## 4. Bloco A concluído — “F1 verdadeiro”

**Objetivo:** fazer o card de maior hierarquia dizer a verdade e não depender da IA.

**Problemas fechados:** P-01, P-02, P-03, P-04, P-10, P-12 e P-09 (gate).
**Fora do escopo (mantido):** F4 completa, S1–S6, refatoração ampla, IA em F4, mudança de motor.

### Escopo funcional

1. **Prescrição manda no título**
   - Quando existir `DailyPrescription` para a data, headline = tipo/título/duração prescritos; planejado vira linha secundária (“Planejado: …”).
   - `REST` mostra descanso, sem duração de treino.
   - Se não houver prescrição, não apresentar um treino como decisão.

2. **Prova antes da sessão**
   - Se houver `RaceEvent` hoje/amanhã, o card mostra “Dia de prova” mesmo sem sessão na data.
   - Prova hoje sem sessão não pode cair em “Semana concluída”.
   - Teste obrigatório: prova no domingo + plano sem sessão no domingo.

3. **Prontidão/semáforo no F1**
   - Para hoje: `Alta/Moderada/Baixa` com rótulo (nunca só cor), score quando existir, motivo com sono/check-in e ação de check-in quando ausente.
   - Para amanhã: “Planejado; confirmação no check-in da manhã”, sem semáforo inventado.
   - Fonte: `ReadinessResult`/`ReadinessFlag` + check-in do dia; reutilizar `readinessLabel`/`ReadinessLabels`.

4. **Consentimento local vs. IA**
   - F1/F2/F4 sempre renderizados; o opt-in controla apenas explicação/revisão.
   - “Depois” não pode deixar a tela sem caminho: manter “Ativar Coach”/“Ativar IA” persistente.
   - Se a decisão for manter o gate, documentar em ADR e criar CTA de reativação; mesmo assim, F1 não pode depender de rede.

5. **Estados vazios**
   - Sem perfil/plano: um único estado de onboarding com CTAs para Perfil, Calibração e Importação.
   - Não mostrar “Semana concluída”, “Nenhum bloqueio ativo” e “Monte seu plano” juntos.
   - “Semana concluída” só com plano e todas as sessões futuras concluídas.

6. **Fallback/validator**
   - Nunca renderizar `explanation.reason` cru; mapear para copy de corredor.
   - Corrigir `forbiddenTokens`: `"atl"` não pode casar “atleta” (usar limite de palavra ou remover).
   - Mapear `unlockHint` em camada de copy; não exibir “ACWR 1,50” no card.
   - Teste de regressão: reason com código interno não aparece na tela.

7. **Gate**
   - Corrigir os dois achados do detekt.
   - Rodar `./gradlew check` até verde.

### Arquivos prováveis

- `app/src/main/kotlin/com/meucoach/app/coach/CoachNextSource.kt`
- `app/src/main/kotlin/com/meucoach/app/ui/coach/CoachNextLabels.kt`
- `app/src/main/kotlin/com/meucoach/app/ui/coach/CoachNextCard.kt`
- `app/src/main/kotlin/com/meucoach/app/ui/coach/CoachScreen.kt`
- `app/src/main/kotlin/com/meucoach/app/ui/coach/CoachViewModel.kt`
- `core/domain/src/main/kotlin/com/meucoach/core/domain/coach/CoachResponseValidator.kt`
- `core/domain/src/main/kotlin/com/meucoach/core/domain/coach/CoachNextResolver.kt` (se a prova entrar no resolvedor)
- Testes: `CoachNextSourceTest`, `CoachNextLabelsTest`, `CoachScreenTest`, `CoachViewModelTest`, `CoachResponseValidatorTest`; adicionar `CoachNextSourceReactivityTest` se necessário.
- `app/src/main/kotlin/com/meucoach/app/ui/coach/CoachProgressLabels.kt` (detekt).

### Critérios de aceite do Bloco A

- [x] Com prescrição `DOWNGRADE`/`REST`, o headline do F1 é o prescrito; o planejado só aparece como secundário.
- [x] Prova hoje/amanhã sem sessão mostra “Dia de prova”; não cai em “Semana concluída”.
- [x] Hoje com check-in ausente oferece “Fazer check-in”; com prontidão verde/âmbar/vermelha mostra o rótulo correto.
- [x] Sem perfil/plano há um único caminho de onboarding com CTAs; não há mensagens contraditórias.
- [x] Fallback não exibe código interno, “atl” ou ACWR cru.
- [x] `./gradlew check`, `spotlessCheck`, `koverVerify`, `assembleDebug` verdes.
- [x] Testes novos cobrem: prescrição divergente, prova sem sessão, consentimento local, estados vazios, fallback sem código, “atleta”.
- [x] Nenhum dado real no diff; nenhuma migração.

### Como ficou

- **Domínio:** `CoachNextSubject` separa treino de prova; `CoachNextResolver` aplica a precedência (hoje/amanhã, antes da próxima sessão ou sem sessão restante). `CoachResponseValidator` usa `\b` nos tokens de métrica.
- **Source:** `RoomCoachNextSource` combina plano, perfil, check-in, métricas e provas; `CoachNextContext` carrega sujeito, prescrição, prontidão, check-in e sono. Prova não consulta prescrição.
- **Card:** `CoachNextLabels` decide headline/linhas (prescrição manda; REST sem duração; “Planejado:” quando não há decisão; prova sem sessão) e `CoachNextCard` mostra o semáforo só para hoje + CTAs.
- **Consentimento:** `CoachScreen` renderiza F1/F2/F4/replan sempre; `CoachAiInviteCard` mantém “Ativar IA” depois de “Depois”; o histórico continua oculto sem opt-in.
- **Estados vazios:** `CoachNextUiState.Onboarding` substitui o “Semana concluída” sem plano e esconde os cards contraditórios.
- **Gate:** `CoachSources` agrupa as fontes (fim do `LongParameterList`); `vdotLine` foi simplificada.

### Dívidas conhecidas (não bloqueiam o Bloco A)

- Validar no aparelho o card com prova, check-in e build sem token (roteiro do Bloco D).
- F1 ainda olha só a semana vigente (domingo à noite não antecipa a semana seguinte).
- `TodayExplanation` ainda usa `unlockHint` cru na aba Hoje (fora do card F1).

---

## 5. Depois do Bloco A

**Bloco B — Reatividade (P-05/P-11)**
- F2/F4/explicação/revisão reagindo a atividades, check-ins, execuções e planos.
- Testes de segunda emissão após escrita.
- Incluir `RoomCoachPlanSource` (hoje 0% de cobertura).

**Bloco C — F4 correto (P-06/P-07)**
- `strongMinutes`/`VolumeReference`, base real → alvo, `R-PLAN-01 v2`.
- Excluir deload/taper/recovery (`specialWeek`), legado da adesão, manual do volume forte.
- Prontidão de 4 semanas, médias ponderadas, cobertura por seção.
- Golden `G-VOL-02` (base 142, alvo 151, adesão 3/3).

**Bloco D — Qualidade**
- Acessibilidade (`heading()`, 200%, light/dark, TalkBack).
- Performance (`flowOn`, uma montagem de 4 semanas, pular samples sem LTHR, benchmark).
- Privacidade (não logar corpo de erro), timeout 10 s + retry.
- Roteiro manual no aparelho com/sem token, prova, check-in, offline, 200%, TalkBack.

---

## 6. O que não fazer

- Não iniciar S1–S6.
- Não reescrever a arquitetura inteira; a base read-only/domínio puro está correta.
- Não adicionar IA ao F4 para compensar falta de conteúdo determinístico.
- Não alterar o motor, a prescrição ou o `ApplyReplan` para “fazer o Coach ficar bonito”.
- Não ajustar teste para o comportamento atual (prova com sessão artificial, legado como adesão).
- Não chamar o PRD de encerrado sem o roteiro manual no aparelho.
- Não commitar dados de saúde, prints, `.fit/.tcx/.gpx/.csv` pessoais, token ou `local.properties`.

---

## 7. Prompt pronto para a nova sessão

> Leia `AGENTS.md`, `docs/PROGRESS.md`, `docs/HANDOFF_COACH_CONSOLIDACAO.md` e a seção 1 + matriz da `docs/AUDITORIA_POS_IMPLEMENTACAO_COACH.md`. O Bloco A já foi entregue; implemente **apenas o Bloco B — reatividade**: F2/F4, explicação e revisão devem reagir a atividades, check-ins, execuções e replanejamento, com teste de segunda emissão após escrita, incluindo `RoomCoachPlanSource`. Não implemente F4 correta (Bloco C), S1–S6, acessibilidade ampla ou mudança de motor. Rode `./gradlew check`, `spotlessCheck`, `koverVerify`, `assembleDebug`. Atualize `PROGRESS.md` e este handoff ao final.

---

## 8. Onde está cada detalhe

| Assunto | Seção da auditoria |
|---|---|
| Veredito e o que preservar | §1 |
| Matriz F1–F4 e NFRs | §3 |
| Arquitetura, estado, reatividade, performance | §4 |
| UX, coerência, consentimento, acessibilidade | §5 |
| Regras de negócio e inteligência | §6 |
| Testes, cobertura e falha do `check` | §7 |
| Problemas P-01…P-27 com evidência | §8 |
| Sugestões S1–S6 | §9 |
| Plano de ação em 3 grupos | §10 |
| Maturidade (2 de 4) | §11 |
