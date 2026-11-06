# ROTEIRO_TESTE_CICLO_1.md — Validação manual no aparelho

**Versão:** 1.0 · **Data:** 06/11/2026
**Objetivo:** validar no aparelho real o que os testes automatizados não cobrem do ciclo 1 (C1-01 a C1-06).
**Aparelho de referência:** Samsung SM-S928B (Android atual), app instalado via `adb install -r`.
**Dados:** use o seu perfil real ou uma instalação limpa. Não anexe prints/dados de saúde em issues ou no repositório.

## 0. Antes de começar

- [ ] Build instalado: `./gradlew :app:assembleDebug` + `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- [ ] Anotar a versão instalada e a data do teste.
- [ ] Se for testar migração, fazer backup manual do JSON em **Dados → Exportar** antes.
- [ ] Deixar o fuso do aparelho no fuso real (America/Sao_Paulo) e o fuso do perfil igual.

## 1. C1-01 — Tempo, virada de dia e fuso

### 1.1 Virada de dia
1. Ajustar o relógio do aparelho para **23:59**.
2. Abrir o check-in, responder e salvar.
3. Sem fechar o app, avançar o relógio para **00:01** e voltar ao app.
4. **Esperado:** o Hoje mostra o novo dia; o check-in de ontem continua em 23:59; nenhum treino de ontem aparece como “hoje”.

- [ ] Passou · [ ] Falhou — observação:

### 1.2 Virada de semana
1. Ajustar para **domingo 23:59**.
2. Avançar para **segunda 00:01** e reabrir o app.
3. **Esperado:** a semana do plano muda; o plano anterior continua consultável; nenhuma sessão antiga vira “pendente”.

- [ ] Passou · [ ] Falhou — observação:

### 1.3 Fuso do perfil
1. Editar o perfil e trocar o fuso (ex.: America/Sao_Paulo → America/New_York).
2. Salvar e voltar ao Hoje.
3. **Esperado:** data/semana recalculadas; lembrete reagendado; não duplica notificação.

- [ ] Passou · [ ] Falhou — observação:

## 2. C1-02 — Carga desconhecida/desatualizada

### 2.1 Sem histórico de carga
1. Em uma instalação sem atividades (ou após reset), abrir Hoje e o plano.
2. **Esperado:** nenhuma qualidade é oferecida; card de carga diz “dados insuficientes”; confiança reduzida; texto não afirma que você descansou.

- [ ] Passou · [ ] Falhou — observação:

### 2.2 Fonte desatualizada
1. Com atividades antigas, deixar a última importação com mais de 14 dias (ou simular no ambiente de teste).
2. Abrir Hoje.
3. **Esperado:** “dados desatualizados”; qualidade bloqueada; ACWR não aparece como número isolado.

- [ ] Passou · [ ] Falhou — observação:

## 3. C1-03 — Plano como intenção e prescrição única

### 3.1 Dia verde com “Tempo”
1. Check-in verde, carga conhecida e um dia com “Tempo” no plano.
2. **Esperado:** **um** card de treino com “Tempo”, mesma duração e alvos do plano; o plano aparece como contexto, sem CTA de conclusão próprio.

- [ ] Passou · [ ] Falhou — observação:

### 3.2 Doença/prontidão vermelha
1. Marcar doença/lesão no check-in.
2. **Esperado:** prescrição vira descanso; “Iniciar”/“Concluir” desabilitados; explicação cita doença e regra.

- [ ] Passou · [ ] Falhou — observação:

### 3.3 Editar check-in
1. Alterar sono/fadiga depois da primeira prescrição do dia.
2. **Esperado:** nova versão da prescrição; a anterior continua auditável; a UI atualiza sem duplicar card.

- [ ] Passou · [ ] Falhou — observação:

### 3.4 Plano criado no meio da semana
1. Apagar o plano da semana (ou instalar limpo) e gerar na quinta.
2. **Esperado:** só quinta→domingo; nada de segunda–quarta como pendente/pulado.

- [ ] Passou · [ ] Falhou — observação:

## 4. C1-04 — Conclusão com evidência

### 4.1 Sem evidência
1. Tocar em “Concluir treino” sem corrida importada.
2. **Esperado:** não conclui; abre o diálogo com “vincular corrida” e “fiz sem registro”.

- [ ] Passou · [ ] Falhou — observação:

### 4.2 Vincular corrida
1. Importar uma corrida compatível com a sessão do dia.
2. Escolher a sugestão e confirmar.
3. **Esperado:** sessão “Feito · verificado”; vínculo visível; adesão conta como execução comprovada.

- [ ] Passou · [ ] Falhou — observação:

### 4.3 Manual válido/inválido
1. “Fiz sem registro” com duração 0 ou RPE fora de 1–10.
2. **Esperado:** bloqueia com mensagem clara.
3. Repetir com duração > 0 e RPE válido.
4. **Esperado:** “Feito · sem registro”; aviso de que não entra em volume/carga/zonas.

- [ ] Passou · [ ] Falhou — observação:

### 4.4 Duplicidade e descanso/doença
1. Tentar vincular a mesma atividade a duas sessões, ou duas atividades à mesma sessão.
2. **Esperado:** bloqueio.
3. Em dia de descanso/doença, tentar iniciar/concluir.
4. **Esperado:** bloqueado; sessão encerrada com motivo (`REST_PRESCRIBED`/`ILLNESS_OR_INJURY`).

- [ ] Passou · [ ] Falhou — observação:

### 4.5 Legado
1. Em uma instalação antiga, abrir uma sessão concluída antes do vínculo.
2. **Esperado:** “Feito · não verificado”; não libera progressão; copy não pune.

- [ ] Passou · [ ] Falhou — observação:

## 5. C1-05 — Lixeira, expurgo e reset

### 5.1 Lixeira
1. Apagar uma corrida no detalhe.
2. **Esperado:** sai da lista/Hoje/Tendências; aparece em **Dados → Lixeira** com prazo.
3. Restaurar.
4. **Esperado:** volta ao uso e, se estava vinculada, a evidência volta.

- [ ] Passou · [ ] Falhou — observação:

### 5.2 Expurgo
1. Apagar de novo e usar “apagar definitivamente”.
2. **Esperado:** some da lixeira, filhos e jobs removidos; export padrão não contém a atividade.
3. Se testar o prazo: com clock fixo, 6d23h não expurga; 7d e 7d1h expurgam.

- [ ] Passou · [ ] Falhou — observação:

### 5.3 Reset
1. **Dados → Apagar todos os dados**, confirmar.
2. **Esperado:** onboarding volta; perfil, atividades, check-ins, métricas, planos, prescrições, execuções, jobs, staging, avatar, cache e preferências de dados apagados.
3. Esperar o worker de Health Connect e reabrir.
4. **Esperado:** nada é recriado; o Health Connect externo continua intacto.

- [ ] Passou · [ ] Falhou — observação:

### 5.4 Reimportação na lixeira
1. Apagar uma corrida e reimportar o mesmo arquivo.
2. **Esperado:** conflito de lixeira, com caminho para **Dados**; não restaura sozinho.

- [ ] Passou · [ ] Falhou — observação:

## 6. Migração real

1. Instalar uma versão anterior sobre dados reais (ou usar um APK antigo com dados).
2. Atualizar para o build atual.
3. **Esperado:** abre sem crash; planos/sessões preservados; `DONE` antigo vira “não verificado”; `SKIPPED` vira encerrado por pulo; check-in/métricas intactos.

- [ ] Passou · [ ] Falhou — observação:

## 7. Pendências anteriores (não são C1)

- [ ] 5 abas (`Hoje · Corridas · Tendências · Saúde · Perfil`) em fonte 200% e 360 dp.
- [ ] Sync do Health Connect após 6 h e com permissão revogada (sem diálogo automático).
- [ ] Aviso “os dados de hoje ainda não chegaram” e link “Abrir Health Connect”.
- [ ] Mapa vetorial do story em 1080p e exportação MP4 no aparelho.
- [ ] Notificação de treino/check-in com deep link.

## 8. Resultado do teste

| Item | Resultado | Observação |
|---|---|---|
| C1-01 tempo/fuso | | |
| C1-02 carga/qualidade | | |
| C1-03 prescrição única | | |
| C1-04 evidência/vínculo | | |
| C1-05 lixeira/reset | | |
| Migração | | |
| Pendências anteriores | | |

**Critério de aceite:** todos os itens de C1-01..C1-05 passam ou têm desvio documentado com impacto e próximo passo. Qualquer falha vira bug com teste de regressão antes de fechar o ciclo.
