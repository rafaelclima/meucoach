# Glossário — MeuCoach (linguagem simples)

> **Propósito:** explicar cada termo técnico que aparece no app (`TRIMP`, `ACWR`, `LTHR`, `Z2`, etc.) de forma que o usuário 0 (`Rafael`, iniciante/intermediário) consiga entender sem precisar ser especialista.

> **Regra:** todo texto na UI que usa um termo deste glossário deve citar (ou já citar) a definição simples. Se o termo não tem contexto, não deve aparecer sozinho (`AGENTS.md` §4, `COMMUNICATION.md` §2).

---

## Termos de carga e treino

| Termo | Nome simples | Definição para o usuário | Onde aparece |
|---|---|---|---|
| `TRIMP` | Carga de treino (baseada em FC) | Quanto esforço o treino exigiu, calculado com a frequência cardíaca e o tempo. Quanto maior, mais intenso. | `core:domain` (motor de carga) |
| `rTSS` | Carga de treino (baseada em ritmo) | Mesmo que `TRIMP`, mas calculado pelo ritmo (pace) e pelo limite calibrado (`LTHR`), sem precisar de FC. | `core:domain` (motor de carga) |
| `CTL` (Carga crônica) | Carga acumulada (42 dias) | A média da carga dos últimos 42 dias. Mostra se você está construindo base. Quando sobe devagar, é bom. Quando desce rápido, é sinal de descanso. | Tela `Hoje` / `Tendências` |
| `ATL` (Carga aguda) | Carga recente (7 dias) | A carga dos últimos 7 dias. Se sobe muito rápido, é alerta (`ACWR`). | Tela `Hoje` / `Tendências` |
| `TSB` (Balanço de treino) | Recuperação | A diferença entre a carga acumulada (`CTL`) e a recente (`ATL`). Positivo = você está recuperado. Negativo = precisa descansar. | Tela `Hoje` / `Tendências` |
| `ACWR` | Proporção de aumento (7d vs 28d) | Compara a carga dos últimos 7 dias com a dos últimos 28 dias. Se passa de 1,3, é atenção. Se passa de 1,5, é alerta. Se é menor que 0,8, você está descansando demais. | Tela `Hoje` / `Tendências` (`ACWR 1,1`) |
| `Monotonia` | Variedade de treino | Quanto os treinos são repetidos. Se você corre só no mesmo ritmo, a monotonia sobe. Isso aumenta risco de lesão. | Tela `Tendências` (`Monotonia`) |

---

## Termos de calibração e zonas

| Termo | Nome simples | Definição para o usuário | Onde aparece |
|---|---|---|---|
| `LTHR` (Limiar de lactato) | Limite de esforço (FC) | A frequência cardíaca média que você consegue manter por 25-35 minutos sem quebrar. É o limite entre treino fácil e forte. Usado para calcular zonas (`Z1`-`Z5`). | `ProfileScreen` (`FC no limite`) / `Onboarding` |
| `VDOT` | Índice de aptidão (Daniels) | Um número que resume sua performance recente. Quanto maior, melhor a forma. Derivado de uma prova (`5k`, `10k`, etc.). | `ProfileScreen` (`Índice de aptidão`) |
| `Pace de limiar` | Ritmo no limite (`LTHR`) | O ritmo (`min/km`) que você consegue manter por 25-35 minutos sem quebrar. Usado para prescrever qualidade. | `ProfileScreen` (`Ritmo no limite`) |
| `Tier A/B/C` | Nível de calibração | `A` = prova/prova recente (`≤ 56 dias`). `B` = treino com FC+pace (`≥ 3 treinos`, `≤ 8 semanas`). `C` = base inicial (sem dados recentes). | `ProfileScreen` (não aparece diretamente, mas é usado no motor) |

---

## Termos de zona de FC (Z1-Z5)

| Termo | Nome simples | Definição para o usuário | Onde aparece |
|---|---|---|---|
| `Z1` (Recuperação) | Muito leve | Ritmo muito lento, conversa fácil. Serve para recuperação ativa. | `ActivityDetailScreen` (`Zonas`) |
| `Z2` (Base aeróbica) | Fácil | Conversa sem esforço. A maioria do treino deve ser aqui (`~80%` do volume). | `TodayScreen` (`Fácil Z2`) / `ActivityDetailScreen` |
| `Z3` (Moderado) | Esforço moderado | Conversa curta. Não é leve nem forte. | `ActivityDetailScreen` (`Zonas`) |
| `Z4` (Limiar) | Forte / próximo do limite | Conversa difícil ou impossível. Próximo do `LTHR`. | `ActivityDetailScreen` (`Zonas`) / `StoryFrameRenderer` (`FC`) |
| `Z5` (VO2max) | Máximo esforço | Só em intervalos curtos. Não é sustentável. | `ActivityDetailScreen` (`Zonas`) |

---

## Termos de prontidão (`Readiness`)

| Termo | Nome simples | Definição para o usuário | Onde aparece |
|---|---|---|---|
| `Readiness` | Prontidão | Um score (`0-100`) que resume sono, fadiga, dor, estresse, FC de repouso e `HRV`. `≥ 70` = verde (`bom para treinar`). `50-69` = amarelo (`cuidado`). `< 50` = vermelho (`descanso`). | `TodayScreen` (`Hoje`) |
| `Hooper` | Índice de recuperação | Soma das notas de sono (`1-5`), fadiga (`1-5`), dor (`1-5`) e estresse (`1-5`). Quanto menor, melhor (`≤ 6` é bom). | `CheckInScreen` / `TodayScreen` |

---

## Termos de explicabilidade (`ADR-0010` / `P1-10`)

| Termo | Nome simples | Definição para o usuário | Onde aparece |
|---|---|---|---|
| `RuleId` (`R-ACT-01`, `R-PLAN-01`, etc.) | Regra do motor | A regra que o app usou para decidir (`ex.: descanso por doença`). Mantida como ID bruto na UI para auditabilidade. | `TodayScreen` (`Por que este treino?`) / `Bottom Sheet` |
| `EvidenceId` (`EV-...`) | Evidência científica | A fonte científica citada (`ex.: Daniels-2014`). Na UI, traduzida para `Fonte: estudo de 2014`. | `TodayScreen` / `Bottom Sheet` |
| `Confidence` (`HIGH`, `MEDIUM`, `LOW`) | Confiança | Quão confiável é a recomendação, baseada na quantidade de dados disponíveis. `HIGH` = muitos dados. `LOW` = pouco ou nenhum dado. | `TodayScreen` / `Bottom Sheet` / `McConfidenceBadge` |

---

## Princípios de uso deste glossário

1. **Nunca citar ID bruto sozinho.** Sempre traduzir (`R-ACT-01` → `Regra: ritmo constante`).
2. **Sempre explicar o impacto.** (`ACWR` → `Proporção carga (ACWR): se passar de 1,5, cuidado.`)
3. **Nunca inventar.** Se o termo não tem contexto, não aparecer na UI (`AGENTS.md` §5).
4. **Manter IDs auditáveis.** Os `ruleId` e `evidenceId` continuam no código (`core:domain`) para rastreabilidade (`ADR-0004`).

---

## Próximos passos para completar a reformulação

- [ ] Atualizar todos os textos críticos restantes (`ProfileScreen` — `Calibração` já feito; `TrendsScreen` — `Recordes`, `Distribuição 80/20`, `Pace @ FC` ainda pendentes)
- [ ] Atualizar `docs/DESIGN_SYSTEM.md` com princípios de conteúdo (`§2` ou nova seção `§15`)
- [ ] Testar com usuário 0 (`TESTING.md` §7: compreensão ≥ 80%)
- [ ] Atualizar `docs/PROGRESS.md` após validação
