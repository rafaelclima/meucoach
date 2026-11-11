# ADR 0029 — F4: conteúdo educativo de hidratação e alimentação (gate de fontes e copy)

**Status:** Aceito em 2026-11-10 (C3-G2; aprovação do usuário 0). Gate documental cumprido e documentos atualizados na mesma data. A implementação de F4 segue para ciclo futuro (FR-404).
**Data:** 2026-11-10
**Escopo:** F4 do `PRD_ADAPTACAO_E_BRIEFING_v0.2.md` (§4.4, FR-401…FR-404, D-1)
**Relacionados:** ADR 0004 (motor determinístico), ADR 0023 (prescrição diária), `R-SAFE-03` (`SPORTS_SCIENCE.md`), `PRODUCT.md` §4

---

## 1. Contexto e forças

O PRD v0.2 aprovou a **intenção** de dicas gerais de hidratação/alimentação no
briefing, mas bloqueou qualquer implementação até a revisão documentada das fontes
e da copy (C3-G2, ADR 0029). `AGENTS.md` §5/§12 e `PRODUCT.md` §4 proíbem
nutrição; `R-FUEL-01`/`R-FUEL-02` e `EV-ACSM-2007`/`EV-THOMAS-2016`/`EV-NATA-*`
estão marcados como pendentes e não entraram no catálogo.

Fatos verificados em 2026-11-10 (leitura integral dos documentos):

- **ACSM 2007** (Sawka et al., *Med Sci Sports Exerc* 39(2):377–390): reconhece
  variabilidade grande entre indivíduos, atividades e ambientes e recomenda
  "customized fluid replacement programs"; manda **evitar beber mais do que as
  perdas de suor**; o objetivo é iniciar eu-hidratado e evitar déficit > 2% de
  massa corporal; eletrólitos e carboidrato na bebida ajudam apenas "under
  certain circumstances" (contexto-dependente).
- **NATA 2017** (McDermott et al., *Fluid Replacement for the Physically Active*,
  *J Athl Train* 52(9):877–895): recomendação 1 (SOR A) — recomendações de fluidos
  precisam ser **individualizadas**; nº 5/6 (SOR A) — hiponatremia vem de consumo
  excessivo e o praticante **não deve ganhar massa durante o exercício**; nº 3
  (SOR A) — hipohidratação aumenta estresse cardiovascular; nº 7 (SOR A) —
  eu-hidratação ±1% antes, < 2% de perda ao fim; nº 14 (SOR B) — água acessível
  antes/durante/depois; nº 15 (SOR B) — recompor líquidos/energia em até 2 h.
- **Thomas 2016** (AND/DC/ACSM, *Med Sci Sports Exerc* 48(3):543–568): referência
  verificada; necessidades energéticas e de carboidrato são **individualizadas e
  periodizadas pela carga de treino**. Nesta versão, a fonte sustenta apenas a
  recusa em prescrever; nenhuma dica alimentar deriva dela.
- **Correção de citação no PRD:** `EV-NATA-2025` apontava para
  `FluidReplacementsForAthletes.pdf`, que é o position statement de **2000**
  (Casa et al., *J Athl Train* 35(2):212–224), já substituído. O documento vigente
  é o de 2017 (McDermott et al.). Não existe statement NATA de fluid replacement
  de 2025; o "2025" do PRD vinha do caminho de upload do PDF (`/2025-08/`).
  A evidência deve entrar no catálogo como **`EV-NATA-2017`**.

Síntese da revisão: as três fontes têm o mesmo centro de gravidade — **regra
quantitativa só existe com contexto individual** (suor, massa, clima, duração).
Nenhuma sustenta conselho universal de quantidade, produto ou eletrólito. Isso
valida a exceção estreita do F4 e inviabiliza qualquer copy com números.

## 2. Problema

Sem o gate, F4 fica indefinido: nem bloqueado com justificativa, nem aprovado com
conteúdo auditável. Enquanto isso, o briefing não explica nada sobre hidratação em
dias quentes/longos — contexto em que o risco educativo é real e a copy conservadora
tem valor — e o usuário não recebe o encaminhamento honesto de que o app não calcula
ingestão individual.

## 3. Decisão proposta

### 3.1 Exceção estreita aprovada (conteúdo)

F4 v1 é **conteúdo educativo estático**, não saída do motor: não cria regra de
prescrição, não altera `DailyPrescription`, `WeeklyPlan` nem confiança. A copy é
fixa, versionada neste ADR e em `COMMUNICATION.md`.

**Hidratação — texto aprovado (pt-BR):**

| Onde | Texto |
|---|---|
| Seção "Antes de correr" | `Comece o treino já hidratado. Nos minutos antes, beba água com calma.` |
| Seção "Durante" | `Em corridas curtas, beba quando sentir sede. Em treinos longos ou no calor, planeje pausas para beber.` |
| Seção "Depois" | `Nas horas depois do treino, reponha líquidos junto com a alimentação de sempre.` |
| Nota fixa (sempre visível nas seções acima) | `O quanto beber varia de pessoa para pessoa — suor, clima, duração. O MeuCoach não calcula quanto você deve beber.` |

**Alimentação — apenas encaminhamento, sem dicas:**

> `O MeuCoach não prescreve dietas nem suplementos. Para um plano alimentar individual, procure um nutricionista.`

**Proibido permanece (FR-402/403):** ml, g, kcal, macros, dose, % de peso,
horário rígido, produto, eletrólito/sal, “água à vontade”, jejum, emagrecimento,
conselho dependente de diagnóstico.

**Superfície:** sheet "Como correr hoje" (sessão não concluída) e, opcionalmente,
a dica de calor já existente no Hoje. **Ramificação de prova não recebe F4 na v1**
(FR-404): copy de dia de prova exige gate próprio.

### 3.2 Regras e evidências para os catálogos

| ID | Texto | Evidência | Confiança |
|---|---|---|---|
| `R-FUEL-01` | Dicas de hidratação são conteúdo educativo fixo, aprovado por fonte; não derivam de prescrição nem alteram decisão do motor | Este ADR; EV-ACSM-2007, EV-NATA-2017 | `STRONG` como política de produto/copy (as fontes sustentam os princípios, não cada frase) |
| `R-FUEL-02` | Nenhuma recomendação de quantidade, eletrólito, suplemento ou horário sem contexto individual aprovado | EV-ACSM-2007; EV-NATA-2017 (nº 1, 5, 6 — SOR A); EV-THOMAS-2016 | `STRONG` — princípio de individualização com SOR A nas duas fontes de hidratação |

| ID | Referência | Tier | Uso |
|---|---|---|---|
| `EV-ACSM-2007` | Sawka, M. N. et al. ACSM position stand: Exercise and fluid replacement. *Med Sci Sports Exerc* 39(2):377–390, 2007 | A | Individualização; risco de hiperhidratação; eu-hidratação |
| `EV-NATA-2017` | McDermott, B. P. et al. NATA position statement: Fluid Replacement for the Physically Active. *J Athl Train* 52(9):877–895, 2017 | A | Individualização (SOR A); não ganhar massa durante o exercício; hidratação antes/depois |
| `EV-THOMAS-2016` | Thomas, D. T., Erdman, K. A., Burke, L. M. Nutrition and Athletic Performance. *Med Sci Sports Exerc* 48(3):543–568, 2016 | A | Sustenta apenas a recusa de prescrição alimentar; nenhuma dica deriva dela |

Os IDs seguem fora da superfície comum e aparecem só em `Ver fontes e detalhes
técnicos`, conforme `COMMUNICATION.md` §6.5.

### 3.3 Ajuste de escopo nos documentos (após aprovação)

| Documento | Mudança |
|---|---|
| `PRODUCT.md` §4 | Exceção estreita registrada: educação geral de hidratação + encaminhamento a nutricionista; sem prescrição |
| `SPORTS_SCIENCE.md` | `R-SAFE-03` mantém "sem prescrição de peso/nutrição" e cita a exceção; entradas de `R-FUEL-01/02` e das três evidências no §14 |
| `COMMUNICATION.md` | Nova subseção §6.10 com a copy aprovada e a intenção de cada linha |
| `AGENTS.md` §5/§12 | Trocar proibição absoluta por exceção estreita: "nada de prescrição nutricional; copy educativa de hidratação só conforme ADR 0029" |
| `PRD…v0.2.md` §6.2 | `EV-NATA-2025` corrigido para `EV-NATA-2017`; `R-FUEL-01/02` deixam de ser PENDENTE |

## 4. Alternativas consideradas

1. **Manter F4 bloqueado indefinidamente.** Rejeitada: a revisão não achou risco
   na copy proposta (sem números, sem universalização) e a ausência de orientação
   de hidratação em dias quentes é uma lacuna educativa real.
2. **Incluir números (ml, % de peso) como "faixas gerais".** Rejeitada: as fontes
   exigem individualização e a UI não tem dados de suor/massa do usuário; violaria
   FR-403 e o princípio "ausente é null, nunca 0".
3. **Dicas alimentares gerais (energia, carboidrato).** Rejeitada: escopo de
   produto (`PRODUCT.md` §4) e `R-SAFE-03`; Thomas 2016 só sustenta o
   encaminhamento, não frases genéricas.
4. **F4 como regra do motor com confiança variável.** Rejeitada: conteúdo estático
   não decide carga/pace; envolver o motor adicionaria determinismo sem valor.

## 5. Consequências

**Positivas:** preenche lacuna educativa de baixo risco; encaminhamento honesto a
nutricionista; correção da citação NATA antes de contaminar o catálogo; copy
versionada e auditável neste ADR.

**Custos/riscos:** strings fixas exigem teste de regressão (nenhum número/unidade
nas seções F4, nenhuma dica no ramo de prova); risco de leitura de "beba quando
sentir sede" como única instrução — mitigado pela nota fixa de variação
individual; manutenção da copy em `COMMUNICATION.md`.

**Implementação:** fora deste gate. F4 v1 entra em ciclo futuro, não é dependência
do briefing já entregue (FR-404), e a seção só aparece quando houver prescrição
vigente e sessão não concluída.

## 6. Gates antes do código

- ~~Aprovação do usuário 0 desta proposta (status sai de "Proposto").~~ Feito em 10/11/2026.
- Atualização coordenada dos documentos da §3.3.
- Teste de regressão: nenhuma seção F4 exibe número, unidade ou ID de regra;
  ramo de prova não recebe seção F4.
- Copy revisada contra `COMMUNICATION.md` §6.5 (sem jargão, número com unidade e
  consequência — aqui, sem número nenhum).
