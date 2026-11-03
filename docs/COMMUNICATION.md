# Reformulação da comunicação — MeuCoach

**Status:** proposta para Fase 1
**Data:** 2026-11-02
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

## 5. Próximos passos (para entrega de maior valor)

1. **Revisar todos os textos do app** (`app/src/main/kotlin/`) contra os princípios acima.
2. **Criar um catálogo de termos** (`docs/GLOSSARY.md`) com definições simples para cada termo técnico (`TRIMP`, `ACWR`, `LTHR`, etc.).
3. **Atualizar `docs/DESIGN_SYSTEM.md`** com regras de conteúdo (não só visual).
4. **Testar com o usuário 0** (`Rafael`) se os novos textos são compreensíveis (`TESTING.md` §7: "Usuário consegue explicar a recomendação ≥ 80%").
5. **Manter IDs auditáveis** (`ruleId`, `evidenceId`) para o motor determinístico, mas traduzir para linguagem simples na UI.
