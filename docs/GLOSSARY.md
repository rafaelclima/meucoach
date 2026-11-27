# Glossário canônico — MeuCoach

> **Fonte de verdade dos textos de usuário.** Define, para cada termo técnico, o
> **nome canônico** (o que aparece na tela), a **leitura** (o que o usuário
> entende) e a **regra de primeiro uso**.
>
> **Regra (§15 do `DESIGN_SYSTEM.md`):** nenhum termo técnico aparece sozinho.
> Sempre `Nome simples` + (na 1ª menção) `o termo entre parênteses` ou a
> explicação em uma frase.
>
> **ADR:** `docs/adr/0071-content-vocabulary-and-voice.md` define a lista negra,
> o mapa de enums → rótulos e a voz por contexto. Este arquivo é o catálogo que
> o ADR referencia.
>
> **Inventário dos textos atuais:** `docs/AUDITORIA_UX_WRITING_2026-11.md`.
> **Histórico de copy por módulo:** `docs/COMMUNICATION.md`.

---

## 1. Como usar este glossário

1. **Nome canônico** — é o texto que o usuário lê. Substitui o termo técnico.
2. **Leitura** — o que o app deve dizer para o termo fazer sentido sem glossário.
3. **Primeiro uso** — como introduzir o termo na primeira ocorrência da sessão.

Nenhum texto de usuário pode conter um termo da **lista negra** do ADR 0071 §2.1
(`ACWR`, `CTL`, `VDOT`, `LTHR`, `taper`, `micro-dose`, `monótona`,
`qualidade` como tipo de sessão, `pace` fora de formato numérico, entre outros)
sem o nome canônico antes.

---

## 2. Carga e treino

| Termo | Nome canônico | Leitura (o que o usuário deve entender) | Onde aparece | Primeiro uso |
|---|---|---|---|---|
| `TRIMP` / `rTSS` | **Carga de treino** | Quanto esforço o treino exigiu, em pontos. Só compare você com você — não existe valor universal de “bom”. | `Hoje` (card Carga da semana), `Tendências` | “Carga de treino” |
| `CTL` | **Base** | Média da sua carga nos últimos 42 dias. É o seu condicionamento: sobe devagar com treino consistente, cai com pausas longas. | `Hoje`, `Tendências` | “Base” |
| `ATL` | **Fadiga** | Média da sua carga nos últimos 7 dias. Sobe depois de treino forte e cai com descanso. | `Hoje`, `Tendências` | “Fadiga” |
| `TSB` | **Balanço** | Base menos fadiga. Negativo = fadiga recente acima da base (normal em semana de treino). Positivo = mais descansado que o normal. | `Hoje`, `Tendências` | “Balanço (recuperação)” |
| `ACWR` | **Aumento** | Carga dos últimos 7 dias ÷ média das últimas 4 semanas. Entre **0,8 e 1,3** é a faixa segura; acima de **1,5** é sinal de subida rápida. É alerta, nunca meta. | `Hoje`, `Tendências` | “Aumento” |
| `Monotonia` | **Variedade** | Se todos os dias têm esforço parecido. Menor é melhor: semana monótona aumenta risco de lesão. | `Tendências` | “Variedade (menor é melhor)” |
| `Strain` | **Carga concentrada** | O esforço de um dia comparado com a sua base. | `QualityGate` (interno) | “Carga concentrada” |
| `Guardrails` | **Travas de segurança** | As regras que impedem o app de aumentar a carga. | `ExplanationCatalog` (interno) | “Travas de segurança” |

> **Por que “Aumento” e não “Proporção”:** o número só é interpretável com a
> referência “7 dias vs. 4 semanas”. O rótulo curto é `Aumento`; a linha
> secundária carrega a fórmula (`7d ÷ 28d`) e a faixa segura.

---

## 3. Treinos (nomes de sessão)

| Conceito | Nome canônico | Blurb (leitura) | Nunca usar |
|---|---|---|---|
| Sessão leve | **Corrida leve** (lista compacta) / **Rodagem** (com descrição visível) | “Base aeróbica conversável; sem treino forte neste dia.” | “Fácil*” |
| Sessão longa | **Longão** | “Longão em ritmo fácil; a duração vem do piso e da disponibilidade.” | — |
| Estímulo neuromuscular | **Acelerações curtas** | “Rápido e relaxado, com recuperação completa.” | “Strides” |
| Sessão de intensidade | **Treino forte** | “Ritmo forte sustentado com aquecimento e volta à calma.” | **qualidade** |
| Variação de ritmo | **Fartlek** | “Variação por esforço percebido, sem ritmo fixo.” | — |
| Treino em repetições | **Intervalado** | “Repetições fortes com recuperação leve entre elas.” | “VO2max”, “trote” |
| Subidas | **Subidas** | “Subidas curtas e fortes, com recuperação caminhando.” | “Colinas” |
| Ritmo de prova | **Pace de prova** | “Blocos no pace que você corre hoje, não na meta; a previsão manda.” | — |
| Recuperação pós-prova | **Recuperação: fácil e curto** | “Sessão curta e leve, sem treino forte.” | — |
| Descanso | **Descanso** | “Descansar também faz parte do treino.” | — |

> **Regra de uso de “Rodagem”:** só quando a descrição (`blurb`) aparece junto
> (card do dia, briefing). Em lista compacta (grade da semana, revisão da
> semana), usar **“Corrida leve”**.
>
> **Os títulos de sessão são fala de corrida** e foram decididos pelo usuário 0
> em 21/11/2026 (`DECISIONS.md` §Decisões de produto): `Rodagem`, `Rodagem leve`,
> `Rodagem com estímulos`, `Intervalado`, `Subidas`, `Acelerações curtas`,
> `dose pequena`, `Ritmo forte`, `Fartlek`, `Longão`, `Pace de prova`,
> `Descanso`, `Recuperação` e `Corrida mais longa`. Banidos: `taper`,
> `micro-dose`, `monótona`, `Strides`, `Colinas`, `Fácil*`.
>
> Em 27/11/2026 o usuário 0 confirmou que **“Longão” permanece em tudo** — é
> gíria de corrida amplamente conhecida e de fácil compreensão. “Corrida mais
> longa” e “corrida longa” continuam válidos em prosa explicativa.

---

## 4. Fases e macros

| Enum | Nome canônico | Leitura | Onde aparece |
|---|---|---|---|
| `MesoPhase.BASE` | **Base** | Fase de construção de base: corrida leve, corrida longa e, no fim, uma variação. | “Por que este plano?” |
| `MesoPhase.BUILD` | **Construção** | Fase em que a carga sobe com mais estímulo. | idem |
| `MesoPhase.PEAK` | **Pico** | Fase final antes da prova: volume estável, mais qualidade. | idem |
| `MesoPhase.TAPER` | **Semana de prova** | Semana em que o volume cai de propósito. | idem |
| `MesoPhase.RECOVERY` | **Recuperação** | Semana curta e leve depois de um bloco forte. | idem |
| `Taper` (redução pré-prova) | **Semana de prova** | Volume reduzido 45–55% sobre a média das 3 semanas anteriores. | `Por que este plano?` |
| `Deload` | **Semana de descarga** | Volume −25% a cada 4ª semana de carga demonstrada; intensidade mantida. | idem |
| `Mesociclo` | **Fase do treino** | Bloco de semanas em que a carga sobe, desce e se recupera. | idem |
| `Bloco` (macro) | **Etapa** | Etapa da preparação para a prova. | idem |
| `CycleOverview` | **Caminho até a prova** | Leitura das próximas 6 semanas ou até a prova, com fases, marcos e direção de volume. Não é um plano fechado. | Sheet no `Hoje`/`Perfil` |
| Descarga futura | **Descarga a confirmar** | Cenário condicional: só cai depois de 3 semanas de carga concluídas; uma semana perdida empurra a data. | idem |
| `PREVIEW` | **Prévia** | Semana seguinte persistida no domingo; pode mudar até segunda. | Hoje, revisão, caminho |
| Intenção futura | **A confirmar** | Semana/distância/volume que ainda depende do que acontecer; sem minutos ou pace exatos. | Caminho até a prova |

---

## 5. Calibração e zonas

| Termo | Nome canônico | Leitura | Onde aparece |
|---|---|---|---|
| `LTHR` | **FC no limite** | A FC média que você aguenta em esforço forte e contínuo por 25–35 min. | `Perfil`, `Calibração`, `Hoje` |
| `VDOT` | **Índice de aptidão** (ou **Aptidão**) | Número que resume sua forma recente. Quanto maior, melhor a forma. | `Perfil`, `Calibração`, `Coach` |
| `Pace de limiar` | **Ritmo no limite** | O ritmo (min/km) que você aguenta nesse esforço. | `Perfil`, `Calibração` |
| `Tier A` | **Calibração por prova recente** | Origem: prova ou teste de 30 min com até 56 dias. | `Calibração`, `Level graduation` |
| `Tier B` | **Estimativa pelos seus treinos** | Origem: 3+ treinos de 20–35 min com FC e ritmo, até 8 semanas. | idem |
| `Tier C` | **Base inicial** | Sem dado recente: o app não inventa. | idem |
| `RPE` | **Esforço percebido** | De 1 (parado) a 10 (máximo do que aguenta). É o que você sente, não um número de máquina. | `Check-in`, `Hoje`, `Briefing` |
| `TT` | **Teste de 30 min** | Teste de tempo de 30 minutos em esforço máximo. | `Level graduation`, `Previsão` |

---

## 6. Zonas de frequência cardíaca

| Zona | Nome canônico | Leitura | Onde aparece |
|---|---|---|---|
| `Z1` | **Muito leve** | Ritmo muito lento, conversa fácil. Serve para recuperação ativa. | `Detalhe da atividade`, `Tendências` |
| `Z2` | **Fácil** | Conversa sem esforço. **A maioria do treino deve ser aqui** (cerca de 80% do volume). | idem |
| `Z3` | **Moderado** | Conversa curta. Nem leve nem forte. | idem |
| `Z4` | **Forte** | Conversa difícil ou impossível. Perto do limite de esforço. | idem |
| `Z5` | **Máximo** | Só em repetições curtas. Não é sustentável. | idem |

> **Regra:** o código (`Z2`, `Z3–Z4`) **nunca** aparece sozinho. Onde o espaço
> é curto (pill de intensidade), usar `Fácil (Z2)` ou `Forte (Z4–Z5)`. Onde há
> espaço (briefing, legendas de gráfico), usar só o nome e manter o código no
> `contentDescription` / auditoria.

---

## 7. Prontidão e recuperação

| Termo | Nome canônico | Leitura | Onde aparece |
|---|---|---|---|
| `Readiness` | **Prontidão** | Número de 0 a 100 que resume sono, cansaço, dor, estresse, FC de repouso e HRV. | `Hoje`, `Coach`, `Tendências` |
| `GREEN` | **Alta** | 70–100: dá para treinar forte. | idem |
| `AMBER` | **Moderada** | 40–69: o corpo pede cuidado — hoje fica leve. | idem |
| `RED` | **Baixa** | 0–39: o corpo pede descanso. | idem |
| `Hooper` | **Recuperação (X/20)** | Soma das 4 notas do check-in (sono, fadiga, dor, estresse). **Menor é melhor.** | `Check-in`, `Hoje` |
| `HRV` | **Variabilidade cardíaca** | Variação entre batimentos por noite. A tendência importa mais que um valor isolado. | `Saúde`, `Hoje` |
| `SpO₂` | **Oxigenação** | Percentual médio de oxigênio no sangue durante o sono. | `Saúde` |
| `RHR` | **FC de repouso** | Batimentos por minuto em repouso. Compare com a sua própria base. | `Saúde`, `Hoje` |

> **Estado de prontidão tem um nome só.** “Alta / Moderada / Baixa” é a única
> forma de falar de prontidão no app. “Verde”, “vermelho” e “amarelo” não
> aparecem em texto de usuário.

---

## 8. Prova e previsão

| Termo | Nome canônico | Leitura | Onde aparece |
|---|---|---|---|
| `Previsão de tempo` | **Previsão de referência** | Tempo que sua forma atual sugere, com faixa. Não é prescrição nem garantia. | `Perfil`, `Previsão` |
| `Meta` | **Meta informada** | O tempo que você pretende completar. É sua; o app não transforma em ritmo nem previsão. | `Perfil` |
| `Riegel` | — (omitido) | Não aparece: é um método de checagem interna, não uma informação para o corredor. | — |
| `Race pace` | **Ritmo de prova** | O ritmo da prova, de referência. | `Coach`, `Briefing` |

---

## 9. Explicabilidade

| Termo | Nome canônico | Leitura | Onde aparece |
|---|---|---|---|
| `RuleId` (`R-ACT-01`, …) | **Regra do motor** | A regra que o app usou para decidir. | Só sob “Ver fontes e detalhes técnicos”, colapsado |
| `EvidenceId` (`EV-…`) | **Evidência científica** | O estudo que sustenta a regra. | idem |
| `Confidence` | **Confiança** | Quão confiável é a recomendação, dada a quantidade de dados. Alta / Média / Baixa. | `Hoje`, `Coach`, `Calibração` |
| `Engine version` | **Versão do plano** | `v3` — qual versão do motor montou o plano. | `Hoje`, `Ajuste da semana` |

> **Princípio (§15 do `DESIGN_SYSTEM.md`):** fonte e ID são auditoria. A
> recomendação vem primeiro, em linguagem de treinador; o ID só aparece sob
> demanda, no máximo em um lugar.

---

## 10. Glossário de saída rápida (para tooltip / `contentDescription`)

Texto curto (≤ 12 palavras) para os termos que ainda aparecem em rótulos
compactos:

| Termo | Texto curto |
|---|---|
| Ritmo | “Minutos por quilômetro. Menor = mais rápido.” |
| FC no limite | “Sua FC máxima em esforço forte e contínuo.” |
| Esforço (1–10) | “1 é fácil, 10 é o máximo que você aguenta.” |
| Intensidade / zona | “Quão forte foi: muito leve, fácil, moderado, forte, máximo.” |
| Prontidão | “Sono, cansaço e FC de repouso em um número de 0 a 100.” |
| Carga | “Esforço do treino em pontos. Só compare com você mesmo.” |
| Variedade | “Se todos os dias tiveram esforço parecido.” |
| Aumento | “Carga de 7 dias comparada com a média de 4 semanas.” |

---

## 11. Estado do glossário

| Data | Mudança |
|---|---|
| 03/11/2026 | Versão inicial (`docs/GLOSSARY.md`), com carga, calibração, zonas e prontidão. |
| 27/11/2026 | **Catálogo canônico (ADR 0071):** lista negra, mapa de enums → rótulos, nomes de sessão, fases, glossário de saída rápida e regras de primeiro uso. |
| 27/11/2026 | **Ajuste pelas decisões do usuário 0 (ADR 0071 §2.10):** “Longão” sai da lista negra e passa a ser o rótulo canônico da sessão longa; o registro coloquial do Coach é mantido (a gíria é desejada). |

**Pendência:** validar com o usuário 0 (`TESTING.md` §7 — “o usuário consegue
explicar a recomendação em ≥ 80% dos casos”). O glossário hoje é derivado de
auditoria de código, não de teste com usuário.
