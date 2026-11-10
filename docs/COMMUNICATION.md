# Reformulação da comunicação — MeuCoach

**Status:** implementado na Fase 1 — varredura concluída em 2026-11-03
**Data:** 2026-11-02 (atualizado em 2026-11-03)
**Responsável:** planejamento com Rafael (usuário 0)

---

## 1. Problema observado

O usuário 0 (Rafael, iniciante/intermediário, < 1 ano de corrida, 5k ≈ 31 min) não entende boa parte dos textos do app. Termos como:

- `TRIMP`, `rTSS`, `ACWR`, `TSB`, `CTL`, `ATL`, `monotonia`
- `Pace @ FC`, `eficiência (m/batimento)`, `distribuição 80/20`
- `R-ACT-01`, `EV-ABBISS-2008`, `EV-DANIELS-2014`
- `LTHR`, `VDOT`, `Tier A/B/C`, `Deload`, `Taper`
- `Heatmap de consistência`, `Recordes pessoais`

São técnicos demais para alguém que está começando. O app promete "explicar o porquê" (`PRODUCT.md` §1), mas a linguagem atual é de especialista para especialista.

---

## 2. Princípios da nova comunicação

1. **Uma pergunta por texto.** Cada texto deve responder a uma pergunta clara do usuário (ex.: "Como foi o treino?", "O que isso significa para mim?", "O que fazer agora?").
2. **Contexto antes do termo.** Nunca usar um termo técnico sem explicar, em uma frase simples, o que ele significa para o treino do usuário.
3. **Números com unidade e contexto.** Sempre explicar o que o número diz (ex.: "Sua carga subiu 20% — isso é um sinal de alerta, não uma meta").
4. **Sem jargão sem explicação.** Se o termo técnico for essencial (ex.: `ACWR`), citar a regra e a evidência em linguagem simples.
5. **Conservador por padrão.** A comunicação deve reforçar que "sem dados, não aumente carga" (`AGENTS.md` §5), e que números grandes sem contexto são ruído (`PRODUCT.md` §5).
6. **Acessível.** Contraste AA, fontes escaláveis, TalkBack (`DESIGN_SYSTEM.md` §10). Os textos devem ser curtos, claros e sem abreviações obscuras.

---

## 3. Mapeamento de textos críticos (prioridade alta)

### 3.1 Tela Hoje (`TodayScreen` / `TodayViewModel`)

| Texto atual | Problema | Proposta |
|---|---|---|
| `CTL / ATL / TSB` | Siglas sem contexto. | Manter os valores, mas adicionar legenda: `Carga de treino (7 dias)` / `Carga acumulada (42 dias)` / `Balanço de recuperação`. |
| `ACWR > 1,5` | Alerta técnico sem explicação. | `Carga aumentou rápido (7 dias vs 28 dias) — cuidado, não é dia de forçar.` |
| `Monotonia alta` | Termo técnico. | `Treinos repetidos demais — variedade baixa. Isso aumenta risco de lesão.` |
| `Prontidão 72 · Verde` | Sem contexto para o usuário. | `Você está bem para correr (sono, fadiga, FC de repouso).` |
| `Fácil Z2 · 40 min · 6,5 km` | `Z2` é técnico. | `Fácil (conversável) · 40 min · 6,5 km` — já está bom, mas explicar `Z2` no sheet. |

### 3.2 Tela Tendências (`TrendsScreen`)

| Texto atual | Problema | Proposta |
|---|---|---|
| `Distribuição 80/20` | Heurística sem explicação. | `Tempo fácil vs forte — você passou 65% do tempo em intensidade moderada/alta. O ideal é ~80% fácil.` |
| `Pace @ FC (eficiência)` | Termo técnico. | `Seu ritmo melhorou: você corre mais rápido com a mesma frequência cardíaca.` |
| `Consistência (heatmap)` | Sem contexto. | `Dias que você correu — veja se o hábito está firme.` |
| `Recordes pessoais` | Pode criar pressão. | `Seus melhores trechos — apenas para acompanhar evolução.` (sem streak, sem culpa). |

### 3.3 Detalhe da atividade (`ActivityDetailScreen`)

| Texto atual | Problema | Proposta |
|---|---|---|
| `O que aprendemos: "Ritmo de prova forte; sua média ficou 97% da LTHR."` | `LTHR` é técnico. | `O ritmo foi forte (97% do seu limite de esforço). Isso é bom para prova, mas não para treino leve.` |
| `Zonas empilhadas` | Sem explicação de zonas. | `Onde você passou o tempo: leve (Z1-Z2) / moderado (Z3) / forte (Z4-Z5).` |
| `R-ACT-01` / `EV-ABBISS-2008` | IDs de regra/evidência. | Manter no código (auditável), mas na UI mostrar apenas: `Regra: ritmo constante` + `Evidência: estudos mostram que queda > 3% na 2ª metade indica fadiga.` |

### 3.4 Story (`StoryFrameRenderer` / `P3-05`)

| Texto atual | Problema | Proposta |
|---|---|---|
| `MEUCOACH` (header) | Sem contexto. | Manter (`MEUCOACH` é a marca), mas no footer: `Seu treino, com contexto.` |
| `KM 1` / `PACE` / `FC` / `KCAL` | Sem explicação do que é `PACE`. | Manter, mas adicionar tooltip/descritivo na prévia: `Pace = ritmo por km.` |
| `Gerado no aparelho · MeuCoach` | Sem contexto de privacidade. | Manter, mas adicionar no `ADR-0015`: `Seu treino, gerado no aparelho. Nenhum dado enviado.` |

### 3.5 Perfil / Onboarding (`OnboardingForm` / `OnboardingScreen`)

| Texto atual | Problema | Proposta |
|---|---|---|
| `FC máxima é a maior observada em treino ou prova, nunca 220 − idade.` | Já está bom, mas pode ser mais direto. | `A maior FC que você já viu em treino ou prova. Não use a fórmula 220 − idade.` |
| `Tempo alvo (opcional)` | Já está bom (`ADR-0015` atualizou). | Manter (`opcional`). |
| `Fuso horário` / `Cidade` | Agora é seleção (`ChoiceChips`). | Manter (`ChoiceChips` com `AVAILABLE_TIMEZONES` e `AVAILABLE_CITIES`). |

---

## 4. Reformulação imediata (mudanças no código)

Vou implementar as mudanças nos arquivos críticos, começando com:

1. Atualizar os textos no `OnboardingScreen` (já feito: `ChoiceChips` para fuso e cidade, `keyboardType.Number` para datas).
2. Atualizar textos no `StoryFrameRenderer`.
3. Atualizar textos no `OnboardingForm` (validação).
4. Criar uma função de formatação de data (`applyDateMask`) — já feito.

Mas para a reformulação da comunicação, vou criar um arquivo de referência (`docs/COMMUNICATION.md`) e fazer algumas mudanças imediatas nos textos mais críticos.

---

## 5. Escopo original da proposta

> Itens 1, 2, 3 e 5 concluídos na varredura de 2026-11-03 (§6). O item 4 segue como validação com o usuário 0.

1. **Revisar todos os textos do app** (`app/src/main/kotlin/`) contra os princípios acima.
2. **Criar um catálogo de termos** (`docs/GLOSSARY.md`) com definições simples para cada termo técnico (`TRIMP`, `ACWR`, `LTHR`, etc.).
3. **Atualizar `docs/DESIGN_SYSTEM.md`** com regras de conteúdo (não só visual).
4. **Testar com o usuário 0** (`Rafael`) se os novos textos são compreensíveis (`TESTING.md` §7: "Usuário consegue explicar a recomendação ≥ 80%").
5. **Manter IDs auditáveis** (`ruleId`, `evidenceId`) para o motor determinístico, mas traduzir para linguagem simples na UI.

---

## 6. Varredura de 2026-11-03 (implementado)

### 6.1 Princípio adotado

**A referência não compete com a decisão.** O usuário vê primeiro o que fazer e por quê, em linguagem de treinador. Fonte, ID de regra e ID de evidência são auditoria: ficam no código, nos dados persistidos e em uma divulgação progressiva opcional — nunca no fluxo principal.

### 6.2 O que saiu da UI comum

| Onde | Antes | Depois |
|---|---|---|
| Hoje — sheet "Por que este treino?" | Listas `Regras` e `Evidências` com `R-...`/`EV-...` | `Como decidi` só com títulos legíveis + `Ver fontes e detalhes técnicos` (colapsado) |
| Detalhe da atividade — "O que aprendemos" | `R-ACT-01 · EV-... · confiança média` | Apenas a observação em linguagem simples |
| Calibração — resultado | `R-CAL-01 · EV-DANIELS-2014, EV-FRIEL-2018` | Removido; ficam os números, a confiança e a recomendação |
| Hoje — Plano da semana | Badge “Confiança baixa” sem contexto no cabeçalho | Confiança movida para “Por que este plano?”, com dados usados/ausentes e aviso de que o valor é da criação da semana |
| Tendências — economia | “Eixo de X a Y para mostrar a variação.” | “Escala ampliada (X a Y m/batimento) para destacar diferenças pequenas entre corridas.”, logo abaixo do gráfico |
| Tendências — 80/20 | `Heurística R-ZONE-02 · EV-SEILER-2010` | `O ideal é passar cerca de 80% do tempo em ritmo fácil.` |

### 6.3 Termos traduzidos na varredura

| Termo técnico | Texto de usuário |
|---|---|
| `ACWR` / "Proporção" | `Aumento` / `vs. mês` / "sua carga subiu rápido" |
| `CTL` / `ATL` / `TSB` | `Acumulada` / `Recente` / `Balanço` (com os dias ao lado) |
| `LTHR` / `Pace de limiar` | `FC no limite` / `Ritmo no limite` / `limite de esforço` |
| `VDOT` | `Aptidão` / `Índice de aptidão` |
| `RPE` | `esforço percebido` / `esforço 8 de 10` |
| `Taper` / `Deload` | `semana de prova` / `semana de descarga` |
| `TT de 30 min` | `teste de 30 min` |
| `Qualidade` | `treino forte` |
| `Pace` / `Split` | `ritmo` / `trecho` |
| `Hooper` | `Recuperação` |
| `SWC` / `baseline` | `acima`/`abaixo da média` / `comparada à sua média` |
| `Z2` sozinho | `Z2 · leve` na pill; `Fácil · Z1–Z2` nas legendas; rótulos por zona no detalhe |
| `HRV` / `SpO2` | `variabilidade cardíaca (HRV)` / `oxigenação (SpO2)` na primeira menção |

### 6.4 Onde a auditoria continua

- `ruleId`, `evidenceIds` e `confidence` continuam em `core:domain`, no `WeeklyPlan`/`WorkoutRecommendation` e no Room (`ADR-0004`).
- No sheet "Por que este treino?", `Ver fontes e detalhes técnicos` mostra as fontes em linguagem simples e, no fim, os IDs brutos para conferência.
- Nenhuma tela comum (Hoje, Atividade, Calibração, Tendências) exibe `R-` ou `EV-`; há teste de regressão para isso.

### 6.5 Regra para novos textos

1. Se o texto precisa de um artigo científico para fazer sentido, ele ainda não está pronto para a UI.
2. Toda sigla aparece com o nome simples antes (`FC no limite (LTHR)` só em contexto técnico; na UI, `FC no limite`).
3. Confiança pode aparecer; ID de regra/evidência, não.
4. Fonte é opcional e sob demanda, no máximo em um lugar por recomendação.
5. Números sempre com unidade e consequência ("carga subiu rápido: não aumente").

### 6.6 Health Connect e dados (03/11/2026)

O usuário 0 não sabia o que era o Health Connect e não entendia que a seção “Dados” do perfil alimentava o coach. A comunicação passou a seguir esta ordem:

1. **Benefício primeiro:** “Alimente o coach com seu descanso” — sono e recuperação mudam a prontidão e o treino do dia.
2. **Definição curta:** “Health Connect é o app de saúde do Android: ele reúne o que seu relógio e outros apps registram.”
3. **Limite explícito:** “O MeuCoach só lê o que você autorizar — nunca escreve.”
4. **Prova, não promessa:** um pill mostra `Conectado · N dias` ou `Última leitura hoje`; sem dados, o texto diz “sem dados ainda”, nunca `0`.
5. **Alternativa visível:** “Sem Health Connect? Use um CSV”, com o formato sob demanda.
6. **Backup separado:** exportar/apagar ganha card próprio (“Seus dados, sob seu controle”), porque não é a mesma intenção de importar métricas.

Termos padronizados: `variabilidade cardíaca (HRV)` e `oxigenação (SpO₂)` por extenso na primeira menção; “FC de repouso” em vez de RHR.

### 6.7 Replanejamento da semana (08/11/2026)

A revisão é um convite, não uma correção do usuário. O texto informa o que muda no futuro e promete explicitamente que hoje e o passado ficam intactos; não usa “falhou”, “perdeu” ou “atrasado”.

| Onde | Texto | Intenção |
|---|---|---|
| Card do plano | `Replanejar minha semana` | Ação explícita (T3), sempre disponível quando há dias futuros. |
| Tela Perfil | card `AJUSTE DA SEMANA` | Resultado da avaliação após salvar perfil ou calibrar; mesmas ações e limites do Hoje. |
| Tela Perfil | `Seu plano já está no limite seguro.` | Explica upgrade sem carga extra; aparece uma vez após o salvamento. |
| Card do plano | `Ver o que mudou` | Diff persistido da última revisão aceita. |
| Selo | `v1`, `v2`… | Versão vigente, sem prometer que a revisão é melhor. |
| Título (T1) | `Dá para ajustar o resto da semana` | Convite, não cobrança. |
| Corpo (T1) | `2 treinos desta semana não aconteceram. Posso reorganizar só os próximos dias, sem repor carga e sem mexer no que já passou.` | Fato + limite conservador + proteção do passado. |
| Título (T2) | `Sua disponibilidade mudou` | Mudança de rotina não é falha. |
| Título (T4) | `Seu perfil mudou` | Mudança de nível/objetivo não é cobrança nem promessa de ganho. |
| Título (T4, só fuso) | `Seu fuso mudou` | Explica a mudança de data local sem tratar como alteração de desempenho. |
| Título (causas múltiplas) | `Vale ajustar o resto da semana?` | Evita atribuir a revisão a um único fator. |
| Corpo (T4) | `Seu perfil de planejamento mudou. Posso reorganizar só os próximos dias, sem aumentar a carga e sem mexer no que já passou.` | Mudança de input vira convite; hoje/passado protegidos. |
| Título (T3) | `Reorganizar o resto da semana?` | Devolve a intenção ao usuário. |
| Nota fixa | `O treino de hoje não muda. Sessões já feitas também ficam intactas.` | Preserva a autoridade do dia e a evidência já registrada. |
| Ação primária | `Ajustar a semana` | Só o futuro muda. |
| Ação secundária | `Agora não` | Recusa só esta proposta; uma nova versão/inputs pode reaparecer. |
| Ação terciária | `Não sugerir hoje` | Silencia sugestões automáticas até amanhã; o botão explícito continua. |
| Sucesso | `Semana ajustada. O que já passou e o treino de hoje não mudaram.` | Confirma o limite aplicado. |
| Obsolescência | `Os dados mudaram enquanto você decidia. Atualizei a sugestão; confira o que mudou.` | Explica por que o plano mostrado não foi aplicado. |
| `NoChange` | `O resto da semana já está como deveria; nada a sugerir.` | Evita revisão cosmética. |

O par `Agora não` × `Não sugerir hoje` foi escolhido com o usuário 0: mantém a distinção temporal (esta proposta × o resto do dia) e evita “mostrar”, que poderia ser lido como esconder o plano. O diff usa rótulos `Novo`, `Sai` e `Muda`, com dia, título e duração. IDs de regra/evidência continuam fora da UI comum e ficam no snapshot auditável.
