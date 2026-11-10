# ADR 0031 — Fingerprint de planejamento e aceite atômico de T4

**Status:** Aceito no C3-G0 em 2026-11-10; implementado no C3-01B em 2026-11-10 (fingerprint, checkpoint, T4, migração 13→14 e compare-and-apply). Restante: telas/rotas de avaliação do C3-02.
**Data:** 2026-11-10
**Escopo:** C3-G0/F1 do `PRD_ADAPTACAO_E_BRIEFING_v0.2.md`
**Relacionados:** ADR 0022 (tempo/fuso), ADR 0023 (prescrição diária), ADR 0028 (replanejamento versionado)

---

## 1. Contexto e forças

O plano semanal é persistido uma vez; ADR 0028 permite revisar apenas o futuro da
semana mediante aceite explícito. O replanejador atual tem gatilhos T1/T2/T3 e hashes
para inputs e disponibilidade, mas não tem checkpoint de perfil.

Fatos verificados no código/documentação:

- Room está no schema 13. `weekly_plans` guarda `inputsHash` e `availabilityHash`;
  `weekly_plan_revisions` é append-only.
- `PlanReplanner.inputsHash` canonicaliza vários campos do perfil, mas representa
  `goal` somente como tipo/data; distância e tempo não entram no hash atual.
- `PrescriptionTargets` usa pace de limiar e LTHR para produzir alvos; `treadmillAccess`
  também altera texto/opções do plano. A disponibilidade tem hash independente.
- `TrainingPlanRepository.saveWeek()` preserva os metadados existentes ao regravar
  a semana. Novos campos devem acompanhar esse caminho, revisão, export e reset.
- `ApplyReplan` calcula o hash no app antes da transação; `applyReplan()` valida o
  valor recebido, mas não relê o perfil na mesma transação. `ProfileRepository.saveProfile()`
  usa outra transação Room.
- Perfil é salvo também pela calibração. Essa rota precisa participar de T4 se mudar
  inputs usados pelo plano/alvos.
- `TimeProvider.currentDate()` e `currentWeekStart()` usam o fuso do perfil; o
  `ProfileTimeProvider` atualiza sua zona ao observar o perfil.

## 2. Problema

Sem contrato de fingerprint/checkpoint, alterações podem não invalidar uma proposta,
repetir o mesmo `NoChange`, ou fazer o plano parecer gerado por um perfil que só foi
avaliado depois. A validação atual do aceite também não fecha a corrida entre a
leitura do perfil e a transação de aplicação.

## 3. Decisão aceita no C3-G0

### 3.1 `PlanningFingerprint` canônico e versionado

Criar um contrato puro no domínio, com namespace e versão explícitos (`planning-v1`)
e serialização determinística. A versão é parte do valor comparado; mudança de formato
gera nova versão e política explícita de migração/baseline.

Allowlist aprovada dos campos que alteram a semana ou seus alvos:

- `experience`;
- `thresholdPace` em s/km e LTHR em bpm, quando usados para alvos;
- `treadmillAccess`;
- projeção de objetivo de prova suportado: tipo/distância e data local.

Disponibilidade mantém hash separado, em conformidade com ADR 0028. Readiness, carga,
data de avaliação e sessões continuam nos inputs do candidato/replanejador, não no
hash compacto do perfil.

Nome, avatar, peso, altura, sexo, cidade e coordenadas ficam fora enquanto não forem
inputs do plano/alvos. `targetTimeSec` é objetivo declarado e não altera a prescrição
nem dispara T4 na v1. Campos ausentes usam representação explícita de `null`; unidades
são SI; mapas são ordenados; não há arredondamento implícito de valores.

#### Contexto temporal — decisão aprovada

ADR 0022/`TimeProvider` confirmam que o fuso do perfil determina `today`, início da
semana e datas locais. A decisão aprovada é manter o fuso fora da identidade do perfil,
mas incluí-lo em um componente separado `temporalContextHash`, comparado na avaliação
T4 e no aceite. Mudança de fuso é registrada como causa `TIMEZONE_CONTEXT_CHANGED`.

O fluxo que salva e avalia deve usar um snapshot coerente: capturar o instante por
`TimeProvider.now()`, ler o fuso do perfil persistido e derivar `today/weekStart` desse
mesmo par. Não depender de a coleta assíncrona de `ProfileTimeProvider.observeProfile()`
já ter publicado o novo fuso antes de avaliar.

### 3.2 Proveniência versus checkpoint de avaliação

Não usar um hash único para as duas funções. Campos conceituais aprovados:

- `generatedFromFingerprintVersion` + `generatedFromFingerprint` e snapshot mínimo:
  inputs que produziram a versão vigente do plano;
- `lastEvaluatedFingerprintVersion` + `lastEvaluatedFingerprint`: último estado
  comparado, inclusive quando o resultado foi `NoChange`.

Regras:

1. Plano legado com ambos os valores desconhecidos: capturar baseline avaliado sem
   sugerir revisão nem inventar proveniência histórica.
2. `NoChange`/“efeito na próxima semana”: atualizar somente o checkpoint de avaliação,
   sem incrementar versão ou dizer que o plano foi gerado pelo perfil novo.
3. Aceite de T4: atualizar proveniência e checkpoint para os inputs do candidato e
   guardar snapshot anterior/novo na revisão append-only.
4. `saveWeek()` não pode apagar silenciosamente esses metadados; snapshots e export
   devem carregar somente a allowlist do plano.

### 3.3 T4, causas combinadas e obsolescência

- Adicionar `PROFILE_CHANGED` como T4, preservando aceite explícito, recusa e
  supressão do ADR 0028.
- O candidato é calculado com todos os inputs vigentes. Se T1/T2/T4 coexistirem, a
  sugestão guarda uma causa principal e uma lista de causas; a copy não deve atribuir
  mudanças a um único fator quando há coocorrências relevantes.
- No aceite, a mesma transação Room que verifica `expectedVersion` relê o perfil e
  calcula a projeção/fingerprint atual. Divergência retorna `Stale`; não aplica o
  candidato recebido.
- `ApplyReplan` continua a validar seus outros hashes. Uma eventual ampliação para
  reler todos os dados de carga/prontidão na mesma transação exige auditoria própria;
  este ADR fecha especificamente o risco perfil × aceite.

### 3.4 Migração e caminhos de persistência

A próxima migração parte de v13, preserva planos/sessões/revisões e deixa metadados
de perfil legados desconhecidos. A versão final da migração deve ser numerada depois
de compatibilizar ADR 0027, se aprovado, e a tabela futura de evento; não iniciar
migrações paralelas.

Cobrir `saveWeek`, captura do baseline, `NoChange`, aceite, snapshot de revisão,
`PlanJson`/export e `UserDataRepository.deleteAllData()`. Import/restore não existe e
fica fora de escopo.

### 3.5 Continuidade com a migração futura para `RaceEvent`

Para manter a sequência do PRD (perfil → briefing → prova), recomenda-se que a v1 do
fingerprint receba uma projeção `PlanningGoal` do `TrainingGoal` atual. Quando a fonte
de evento migrar para `RaceEvent`, os mesmos valores de tipo/distância/data devem
produzir a mesma projeção canônica. Não incluir ID do evento nem status (`CANCELLED`,
`NO_SHOW`, `COMPLETED`) no hash de plano sem decidir que esses estados alteram a semana.

Se essa equivalência não puder ser preservada, pausar a sequência C3 e pedir aprovação
para reordenar; não aceitar um T4 falso como efeito colateral da migração.

## 4. Alternativas consideradas

1. **Um hash para geração e avaliação.** Rejeitada: `NoChange` pode falsificar
   proveniência ou repetir avaliações.
2. **Usar o `inputsHash` atual como fingerprint de perfil.** Rejeitada: mistura
   inputs dinâmicos, contém campos que não alteram o plano e não inclui toda a futura
   projeção de prova.
3. **Comparar o hash somente no app antes da transação.** Rejeitada: mantém a janela
   de corrida concreta entre salvar perfil e aplicar sugestão.
4. **Incluir todos os campos do perfil.** Rejeitada: aumenta stale sem efeito real,
   amplia snapshot e inclui dados pessoais desnecessários.
5. **Excluir fuso sem hash temporal.** Não recomendada: conflita com ADR 0022, que
   usa o fuso para `today`/semana.

## 5. Consequências

**Positivas:** T4 auditável; `NoChange` idempotente; proveniência honesta; aceite
resistente à edição concorrente de perfil; snapshot minimizado.

**Custos/riscos:** migração e serialização de snapshots; atualização de todas as
rotas de salvamento; coordenação do fuso; tratamento sem falso T4 quando o objetivo
migrar de `TrainingGoal` para `RaceEvent`.

## 6. Gates antes do código

- ~~Atualizar PRD v0.2 §4.1/§7.1 para refletir `temporalContextHash` aprovado.~~
  Feito em 10/11/2026.
- Testar que mudar `zoneId` altera só o contexto temporal, invalida proposta stale e
  calcula `today/weekStart` do snapshot coerente.
- Testes de domínio de canonicalização/allowlist e `NoChange` definidos.
- Plano de migração sequencial aprovado, com export/reset e preservação dos legados.
- Compare-and-apply no Room com teste de concorrência perfil × aceite.
- `ReplanSuggestion` pode explicar múltiplas causas sem mudar a regra de aceite.

**Status atual:** decisões C3-G0 aprovadas pelo usuário em 2026-11-10; ADR aceito e implementado no C3-01B. As rotas de UI/avaliação ao salvar Perfil/Calibração ficam no C3-02.
