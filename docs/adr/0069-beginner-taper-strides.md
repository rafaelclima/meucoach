# ADR 0069 — Strides `EASY` no taper do iniciante

**Status:** Aceito em 26/11/2026; implementação pendente (Fatia 0 docs-only).
**Data:** 2026-11-26
**Escopo:** `SessionLibrary.easyTemplates(TAPER)`, `WeeklyPlanGenerator` (seleção da
micro-dose/estrutura), `DailyPrescriptionEngine` (preservação da sessão `EASY`),
`PlanDecision`/rationale, `SPORTS_SCIENCE.md` §8.4/§8.6/§14.
**Relacionados:** ADR 0043 (catálogo/periodização), ADR 0044 (autoridade do dia),
ADR 0047 (catálogo de qualidade), ADR 0048 (versões), ADR 0053 (rampa), ADRs
0066/0067/0068 (Fatia 0).
**PRD:** `docs/PRD_CORRECOES_AUDITORIA_2026-11.md` — D6, SEG-03, ALG-06, §5 (Fatia 0),
§6.5.

---

## 1. Contexto

`SPORTS_SCIENCE.md` §8.4 diz que os strides são “Todos como `EASY`; micro-dose ≤25 min
em taper/prova”. §8.6 reforça que, dentro do corte e na semana da prova, não há
`QUALITY` longa nem `LONG`, mas **nunca zero estímulo**: mantém-se 1 micro-dose ≤25 min
(8×20 s strides ou 3×3 min T leve + trote).

No código, a micro-dose de taper é um template `QUALITY` (`stridesMicroDose`), e o
iniciante nunca recebe `QUALITY` (`R-PRESC-04`). O catálogo de fácil do taper só oferece
“Rodagem”. A auditoria de 26/11/2026 (`SEG-03`, E2/E13) reproduziu o iniciante em taper
sem strides nem micro-dose: a semana de prova fica plana e sem estímulo distinto,
apesar do “Todos” do §8.4.

A decisão D6 foi aprovada na direção A: incluir **1 strides `EASY` ≤25 min** no taper do
iniciante, sem criar `QUALITY`. O PRD exige ADR e teste antes do código; esta fatia é
docs-only e não altera o motor.

## 2. Decisão

### 2.1 Uma sessão `EASY` com strides

No taper e na semana da prova, o iniciante passa a receber **no máximo 1** sessão de
strides:

- tipo `EASY`, nunca `QUALITY`;
- `qualityKind = null` (não é qualidade estruturada);
- duração entre 20 e 25 min, com teto de 25 e respeito à disponibilidade do dia;
- estrutura executável obrigatória (`stridesBase`, 6×20 s controlados com trote de
  recuperação, ou a estrutura equivalente já catalogada);
- título e copy que não chamam a sessão de “treino forte” e não prometem intensidade
  de qualidade;
- `R-PRESC-04` permanece intacto: o iniciante continua sem `QUALITY`; a sessão é
  `EASY` e não abre a porteira para intervalado/contínuo.

A sessão não entra na contagem de qualidades da semana (`qualityCount` continua 0) e não
autoriza a segunda qualidade.

### 2.2 Posição na janela

A posição segue §8.6 e as regras de adjacência. Um **dia seguro** é um dia com 20–25
min disponíveis, sem longão/qualidade no próprio dia ou no dia anterior e que não é a
véspera nem o dia da prova (convenção conservadora de produto):

- preferência por um dia seguro **7–10 dias antes** da prova de 5K/10K, ou **10–14
  dias** antes de meia/maratona;
- se a semana gerada contém essa janela, a sessão é colocada no dia seguro mais próximo
  do fim da janela (mantendo recuperação suficiente), respeitando a disponibilidade;
- se a janela preferencial já passou, a semana da prova ainda pode manter 1 strides
  `EASY` ≤25 min em um dia seguro, como a micro-dose de §8.6 permite; a copy a nomeia
  como “dose curta de semana de prova”;
- a sessão nunca fica no dia do longão/qualidade nem no dia seguinte a eles; em taper
  não há `QUALITY` longa nem `LONG`, mas a regra de adjacência continua valendo para
  qualquer sessão forte remanescente.

Não há uma nova janela fisiológica inventada: os intervalos 7–10/10–14 dias são os do
§8.6. A escolha do dia é convenção de produto `LIMITED`.

### 2.3 Quando a agenda não cabe

O piso executável da sessão é **20 min**: abaixo disso, a estrutura de strides não cabe
e o gerador não cria uma sessão de estímulo sem estrutura (ALG-07). O comportamento é:

1. se há um dia seguro com 20–25 min na janela, a sessão é criada com a duração
   disponível (20–25);
2. se não há nenhum dia seguro com 20–25 min, a sessão é **omitida**;
3. a copy declara a omissão: “Sem um dia de 20–25 min na janela do taper, esta semana
   fica sem acelerações; nenhum treino forte foi inventado.”
4. o plano não cria `QUALITY` para compensar, não alonga outra sessão acima da agenda e
   não deixa uma sessão de estímulo com `structure == null`.

A frase “nunca zero estímulo” do §8.6 continua sendo o alvo quando há agenda; a omissão
honesta é a exceção `LIMITED` quando a agenda não permite o mínimo executável. É melhor
declarar zero do que inventar uma dose que o usuário não tem.

### 2.4 Copy

- **Sessão criada:** “Taper: 1 sessão curta de acelerações em ritmo fácil, 20–25 min,
  para manter o estímulo sem fadiga.”
- **Semana da prova:** “Dose curta de acelerações na semana da prova, em ritmo fácil;
  não é treino forte.”
- **Omissão:** “Sem um dia de 20–25 min na janela do taper, esta semana fica sem
  acelerações; nenhum treino forte foi inventado.”
- A copy nunca chama a sessão de “qualidade”, “intervalado” ou “treino forte”.

### 2.5 Impacto de versão

- `weekly-plan-v8`: a composição do taper do iniciante muda (uma sessão `EASY` a mais,
  com estrutura); a fatia de código deve subir a versão do motor e o `PlanUpgrade`
  (ADR 0048) reavalia planos antigos na abertura, preservando passado, `DONE` e
  `CLOSED`.
- `daily-prescription-v7`: permanece se o motor diário apenas consumir a sessão `EASY`
  do plano. Se ele rebaixar/remover a sessão ou mudar a copy do dia, a versão também
  sobe.
- Sem schema, migração, dependência nova ou reescrita de histórico.

## 3. Alternativas avaliadas

| Alternativa | Por que não |
|---|---|
| **B — documentar “iniciante em taper: só fácil”** | Contraria o “Todos” do §8.4 e o “nunca zero estímulo” do §8.6; a semana de prova fica plana sem necessidade. |
| Criar uma micro-dose `QUALITY` para o iniciante | Viola `R-PRESC-04` e a trava de iniciante sustentada por `EV-INJURY-2015`/`EV-HIIT-2016/2024`. |
| Usar intro fartlek no taper | É uma sessão mais longa/estruturada, não uma micro-dose de manutenção; mudaria o estímulo e a duração. |
| Encurtar a estrutura para menos de 20 min | Não cabe aquecimento + strides + desaquecimento; criaria sessão sem estrutura executável (ALG-07). |
| Omitir sempre que a agenda for apertada, sem copy | Zero estímulo silencioso é pior que a omissão declarada; a copy é o critério de honestidade. |

## 4. Evidência e força

- `SPORTS_SCIENCE.md` §8.4 (strides `EASY`, micro-dose ≤25 em taper/prova), §8.6
  (intensidade mantida em micro-dose; nunca zero estímulo) e §8.8 (taper).
- `EV-BOSQUET-2007`/`EV-MUJIKA-2003`: manter intensidade no taper, com dose curta, sem
  fadiga.
- `EV-SEILER-2010`/`EV-DANIELS-2014`: strides como estímulo neuromuscular curto,
  compatível com fácil.
- `EV-INJURY-2015`, `EV-HIIT-2016` e `EV-HIIT-2024`: sustentam a trava de `QUALITY`
  estruturada no iniciante; a decisão preserva `R-PRESC-04`.
- **Força:** `LIMITED — convenção de produto` para a dose/janela do iniciante; o
  princípio de manter intensidade no taper é `STRONG`, e a trava de qualidade do
  iniciante é `LIMITED`.
- A regra não cria novo `evidenceId`.

## 5. Consequências

- `SessionLibrary.easyTemplates(TAPER)` passa a oferecer o template de strides `EASY`
  para iniciante, além da rodagem; o template de micro-dose `QUALITY` continua para
  intermediário/avançado.
- O gerador escolhe no máximo uma sessão de strides no taper; a contagem de qualidade
  continua 0 e a segunda qualidade continua bloqueada.
- `PlanDecision`/rationale ganham motivos nomeados (`BEGINNER_TAPER_STRIDES`,
  `BEGINNER_TAPER_STRIDES_OMITTED`) para a UI e o Coach explicarem a presença/omissão.
- A semana de prova do iniciante deixa de ser totalmente plana quando há agenda,
  sem abrir a porteira de `QUALITY`.
- Risco: a sessão ser confundida com qualidade. Mitigação: tipo `EASY`, `qualityKind =
  null`, copy explícita e teste que conta `QUALITY == 0`.
- Sem schema, sem migração, sem dependência nova.

## 6. Critérios de aceite e golden `G-BEGINNER-TAPER-01`

Aceite da futura implementação:

1. Iniciante com prova em 10 dias e um dia seguro de 20–25 min na janela recebe
   **exatamente 1** strides `EASY`, com duração ≤25, `qualityKind == null` e
   `structure != null`.
2. Nenhuma sessão `QUALITY` na semana; `R-PRESC-04` continua valendo.
3. Sem dia seguro de 20–25 min, a sessão é omitida, a copy explica e nenhuma sessão
   fica com `structure == null` ou acima da agenda.
4. A sessão não é adjacente ao longão/qualidade e não entra na contagem de qualidades.
5. A copy não chama a sessão de “treino forte” nem promete intensidade de qualidade.

### Golden `G-BEGINNER-TAPER-01` (definido; executável só na fatia de código)

| Cenário | Entrada sintética | Asserção |
|---|---|---|
| **A — taper com strides** | Iniciante, prova de 5K em 10 dias; disponibilidade com um dia de 25 min na janela 7–10 dias antes e demais dias fáceis; sem longão/qualidade no taper | Exatamente 1 sessão `EASY` de strides, 20–25 min, `qualityKind == null`, `structure != null`; zero `QUALITY`; copy contém “acelerações” e “sem treino forte”; toda sessão ≤ agenda |
| **B — agenda sem mínimo** | Mesmo perfil, mas nenhum dia com 20–25 min na janela (slots de 15 min) | Zero strides e zero `QUALITY`; copy contém “sem ... acelerações” e “nenhum treino forte foi inventado”; nenhuma sessão >15; nenhum estímulo com `structure == null` |
| **C — semana da prova** | Iniciante, prova em 4 dias; um dia seguro com 20 min na semana da prova | Exatamente 1 strides `EASY` ≤25 min, `qualityKind == null`, `structure != null`; zero `QUALITY`; copy contém “semana da prova” |

O cenário A falha no motor atual (o iniciante em taper não recebe strides) e passa
depois da implementação. O cenário B prova a omissão honesta; o C prova a dose de semana
da prova sem criar qualidade.

**Fora de escopo desta fatia:** implementar o template, alterar `easyTemplates(TAPER)`,
mudar `ENGINE_VERSION`, criar fixture executável ou abrir `QUALITY` para iniciante. Este
ADR apenas fixa a decisão e o critério.
