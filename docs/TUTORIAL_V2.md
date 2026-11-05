# TUTORIAL_V2.md — Ativação pós-perfil

**Status:** Fases A e B implementadas em 05/11/2026; Fases C–D propostas
**Data:** 05/11/2026
**Substitui:** tour de 4 telas em `app/src/main/kotlin/com/meucoach/app/ui/tutorial/`
**Importante:** este documento é um plano. Nenhum código foi alterado nesta sessão.

---

## 0. Resumo executivo

O tutorial atual não parece amador por falta de ícones bonitos. Ele parece amador porque:

1. **Explica a interface, não o resultado.** “AS TELAS”, “SEU PAPEL” e “Quatro abas” são informação de produto, não uma promessa para quem acabou de instalar.
2. **Não mostra o produto.** O app tem gauge, cards, plano, gráficos e uma linguagem visual forte; o tutorial usa um ícone Material dentro de um quadrado e ocupa metade da tela com vazio.
3. **Não leva a uma primeira ação.** O usuário lê quatro páginas, cai na tela Hoje sem dados e não tem um próximo passo claro.
4. **Não personaliza.** Nome, objetivo, experiência e disponibilidade já existem no perfil, mas o tour é idêntico para todo mundo.
5. **Não tem acabamento de produto.** “Pular” em lima compete com o CTA, o botão muda de largura entre páginas, não há tratamento de voltar, os dots não são acessíveis e só a primeira página tem golden.

A proposta é trocar o carrossel por uma **experiência de ativação em 3 cenas**, usando **dados reais e componentes reais**, seguida de um **checklist de primeiro uso** no Hoje e de **dicas contextuais** nas telas vazias. A métrica principal deixa de ser “concluiu o tutorial” e passa a ser “chegou à primeira ação significativa”.

---

## 1. O que existe hoje (evidência)

| Arquivo | Comportamento atual |
|---|---|
| `ui/tutorial/TutorialScreen.kt` | `HorizontalPager` com 4 páginas declarativas (`imageVector`, `overline`, `title`, `description`, `bullets`). Ícone de 72 dp em `accentSoft`, título `headlineLarge`, bullets com ponto lima, dots de 8 dp, botões `Continuar`/`Voltar`/`Tudo pronto` e `Pular`. |
| `ui/tutorial/TutorialViewModel.kt` | Expõe apenas `seen`. Sem dados de perfil, plano, prontidão ou progresso. |
| `ui/tutorial/TutorialStore.kt` | Uma flag booleana em SharedPreferences (`meucoach_tutorial/introDone`). |
| `MeuCoachApp.kt` | O tutorial é um gate fora do `NavHost`: se `!seen`, mostra `TutorialScreen`; senão, `MainContent`. |
| `MainActivity.kt` | Pede `POST_NOTIFICATIONS` imediatamente no `onCreate`, antes de qualquer contexto de valor. |
| `ProfileScreen.kt` | “Rever tutorial” fica em Perfil → Ajuda. |
| Testes | 3 testes de UI, 3 de ViewModel/Store e 2 goldens (apenas a primeira página). |

Conteúdo atual das páginas:

1. **BEM-VINDO / Seu coach de corrida** — parágrafo sobre importar, aprender e prescrever.
2. **AS TELAS / Quatro abas, um coach** — lista Hoje, Corridas, Tendências, Perfil.
3. **SEU PAPEL / O que mantém o coach informado** — check-in, importação, Health Connect, calibração.
4. **VOCÊ NO CONTROLE / Nada sai do aparelho** — privacidade, explicabilidade, exportar/apagar.

---

## 2. Diagnóstico

### 2.1 O problema central

O tutorial atual responde “o que existe no app?”. O usuário que acabou de instalar quer saber:

- “O que este app faz por mim?”
- “O que eu faço agora?”
- “Por que eu confiaria nele?”

O tour não responde nenhuma das três com clareza. Ele é uma **enciclopédia de funcionalidades** colocada antes do primeiro momento de valor.

### 2.2 Dez problemas objetivos

| # | Problema | Consequência |
|---|---|---|
| 1 | Conteúdo centrado em funcionalidades (“AS TELAS”, “SEU PAPEL”) | O usuário não vê benefício, só trabalho. |
| 2 | Nenhum uso de dados do perfil | A experiência é genérica e substituível. |
| 3 | Nenhuma prévia do produto real | O tutorial parece de outro app; não vende o gauge, o plano, o “porquê”. |
| 4 | Nenhuma ação de primeira vitória | O usuário termina o tour e não sabe o que fazer. |
| 5 | Quatro páginas de leitura antes do Hoje | Aumenta o tempo até o valor e a chance de abandono. |
| 6 | “Pular” em lima + CTA em lima | Dois acentos competindo; quebra a regra de um CTA por tela. |
| 7 | Layout top-aligned com metade da tela vazia | Falta de hierarquia e de composição; parece rascunho. |
| 8 | Botão primário muda de largura entre páginas | Salto visual e perda de estabilidade. |
| 9 | Sem `BackHandler`, sem semântica de progresso, sem largura máxima | Voltar fecha o app e o tour reaparece; leitor de tela não entende o passo; tablet estica o texto. |
| 10 | Sem instrumentação local | Não é possível saber onde o usuário desiste nem se o tour melhora ativação. |

### 2.3 Problemas de plataforma e confiança

- **Permissão de notificação no primeiro segundo de app.** O usuário ainda não viu valor e já recebe um diálogo do sistema. Isso tende a gerar negativa permanente e prejudica lembretes de check-in/treino.
- **Back fecha a atividade sem marcar o tutorial como visto.** Na próxima abertura, o tour volta. É um loop indesejado.
- **Nenhuma versão do tutorial.** Se o conteúdo mudar, usuários antigos não são tratados de forma explícita.
- **Strings hardcoded.** Todo o texto está em Kotlin, apesar de a documentação prever externalização para i18n.
- **Golden só da primeira página.** As páginas com bullets (2, 3 e 4) não têm proteção visual.

### 2.4 O que o mercado faz melhor

Padrões consistentes em apps de referência (Runna, Strava, Nike Run Club, Whoop, Oura, Duolingo, Notion):

1. **Começar pelo “aha moment”.** O onboarding existe para levar a pessoa ao primeiro resultado, não para listar recursos.
2. **Personalizar com poucas perguntas e mostrar que elas mudaram algo.** O usuário precisa ver o reflexo das respostas.
3. **Usar o produto real como prova.** Imagens, cards e dados reais valem mais que ícones genéricos.
4. **Divulgação progressiva.** Ensinar no contexto da tela, não tudo antes de usar.
5. **Checklist de primeiros passos.** Dá direção e motivo para voltar, sem gamificação agressiva.
6. **Pedir permissão no contexto.** Pré-permissão com benefício claro; nunca no primeiro frame.
7. **Uma ação principal por tela.** CTA único, “pular” discreto, progresso visível.
8. **Microcopy que reduz ansiedade.** Explicar o porquê de cada pedido e o que acontece se recusar.

Referências de apoio: Runna (carrossel curto + personalização + plano revelado), Duolingo (objetivo + primeira lição), Strava/Nike (primeira atividade), Whoop/Oura (pré-permissão de dados), Notion/Linear (checklist e dicas contextuais). O ponto comum não é “mais telas”; é **menos distância até o valor**.

---

## 3. Objetivo e métricas

### 3.1 Job to be done do tutorial

> “Depois de criar meu perfil, quero entender em menos de um minuto o que o app já fez por mim, por que confiar nele e qual é o meu próximo passo — sem ler um manual.”

### 3.2 Definição de ativação

**Ação significativa inicial (ASI):** primeira ocorrência de uma destas ações, em até 24 h após a criação do perfil:

- concluir o check-in do dia; ou
- importar uma corrida válida; ou
- conectar o Health Connect.

A ASI não é “concluir o tutorial”. O tutorial é meio, não fim.

### 3.3 Métricas

| Tipo | Métrica | Meta inicial (validar) |
|---|---|---|
| Primária | % de novos usuários com ASI em 24 h | ≥ 40% |
| Secundária | Tempo do perfil salvo até a ASI | mediana < 2 min |
| Secundária | % que chega ao Hoje após o perfil | ≥ 70% |
| Secundária | % que conclui ou pula o tutorial em < 90 s | ≥ 80% |
| Secundária | % que conclui o check-in no primeiro dia | ≥ 50% |
| Guardrail | Negativa de notificação | não aumentar; idealmente reduzir |
| Guardrail | Usuário consegue explicar a recomendação | ≥ 80% em teste moderado |
| Guardrail | Retenção D1/D7 | comparar com baseline antes/depois |

Sem analytics de terceiros. A medição é **local e opcional**: timestamps e contadores em SharedPreferences/DataStore, exportáveis no JSON de dados e visíveis em build de debug. Nada sai do aparelho.

### 3.4 Fora de escopo

- Gamificação com streak, chama, ranking ou punição.
- Tour com spotlight sobre cards em tempo real na primeira versão (pode vir depois).
- Central de ajuda completa.
- Novas dependências de analytics, animação ou imagem.
- Conteúdo de musculação, nutrição ou diagnóstico.

---

## 4. Princípios de design

1. **Três cenas, não quatro slides.** Cada cena tem uma pergunta e uma ação.
2. **Mostrar o produto, não ícones.** A prova é o card real, o gauge real, o plano real.
3. **Dados reais ou nada.** Nome, objetivo e plano vêm do perfil/banco. Sem plano, mostrar estado honesto — nunca número inventado.
4. **Uma ação principal por tela.** “Pular” é texto secundário, nunca lima.
5. **Progresso visível e saída sempre disponível.** Barra segmentada + “Agora não” acessível.
6. **Divulgação progressiva.** O tutorial apresenta o essencial; o resto é ensinado no contexto.
7. **Sem culpa.** Nada de “você ainda não fez”, “não perca”, streak ou vermelho de cobrança.
8. **Acessível por construção.** 48 dp, contraste AA, TalkBack, fonte 200%, redução de movimento.
9. **Consistência com o design system.** Reusar `McCard`, `McPill`, `McStat`, `McGauge`, `McPrimaryButton`; criar só o que for específico do tutorial.
10. **Privacidade como argumento, não como rodapé.** A promessa “nada sai do aparelho” aparece uma vez, com peso, e some do caminho.

---

## 5. Nova arquitetura da experiência

```
Onboarding (3 passos, já existe)
        │
        ▼
Cena 1 — “Seu plano está pronto”        ← revelação personalizada
        │
        ├── “Ver meu treino de hoje” ──────────────► Hoje
        │
        └── “Como o coach pensa” ──► Cena 2 ──► Cena 3
                                                   │
                                                   ├── Check-in agora ──► CheckIn
                                                   ├── Importar corrida ─► Corridas/import
                                                   └── Conectar saúde ───► Health Connect
                                                   │
                                                   ▼
Hoje com checklist “Complete seu setup”
        │
        ├── Check-in de hoje
        ├── Importar uma corrida
        └── Conectar Health Connect
        │
        ▼
Dicas contextuais (uma vez por tela)
        ├── Corridas vazias → como importar
        ├── Tendências vazias → o que aparece com 2 semanas
        ├── Check-in concluído → prontidão atualizada
        └── Primeira corrida importada → abra para ver zonas/splits
```

### 5.1 Onde o tutorial deve viver

**Recomendação:** o tutorial deixa de ser um gate antes do app e passa a ser uma **camada de primeiro uso sobre o Hoje**.

- O usuário sai do onboarding direto para o Hoje (com o plano sendo montado).
- A primeira cena aparece como uma **revelação em tela cheia** sobre o Hoje, com o produto real por trás.
- Ao terminar ou pular, a camada some e o usuário já está no lugar certo, com o checklist visível.
- Vantagens: elimina uma tela intermediária, usa o plano real que o `TodayViewModel` já gera, melhora a transição e evita duplicar a geração de plano.
- Alternativa de menor esforço: manter o gate, mas extrair um `TodayOverviewBuilder` compartilhado para o tutorial carregar o plano real. Não duplicar a regra de geração.

### 5.2 Versionamento

- `introVersion = 2` em `TutorialStore`.
- Novos usuários veem a v2.
- Usuários que já viram a v1 **não** são forçados a um novo tour completo; recebem um card discreto em Perfil → Ajuda (“Conheça o novo tour”) ou uma dica contextual.
- O tour é revisível em Perfil → Ajuda.

---

## 6. Especificação tela a tela

### 6.1 Cena 1 — “Seu plano está pronto”

**Pergunta:** “O que o app já fez por mim?”

**Layout (wireframe):**

```
┌──────────────────────────────────────┐
│  ←                          Agora não│
│  ━━━━━━━━━  ─────────  ─────────     │  progresso 1/3
│                                      │
│   ┌──────────────────────────────┐   │
│   │  PRÓXIMO TREINO        Z2 · leve│ │  card real (frente)
│   │  Fácil e conversável          │   │
│   │  40 min · 6,0 km              │   │
│   │  Por que: base aeróbica...    │   │
│   └──────────────────────────────┘   │
│   ┌──────────────────────────────┐   │
│   │  SEMANA · 3 TREINOS          │   │  card real (fundo, deslocado)
│   └──────────────────────────────┘   │
│                                      │
│  SEU PLANO                           │
│  Rafael, sua semana está pronta.     │
│  Montei 3 dias para o seu 5 km. Cada │
│  treino vem com o porquê — e muda se │
│  o seu descanso pedir.               │
│                                      │
│  [ Ver meu treino de hoje ]          │
│  Como o coach pensa                  │
│                                      │
│  🔒 Tudo calculado no seu aparelho.  │
└──────────────────────────────────────┘
```

**Dados:**
- `AthleteProfile.name` → “Rafael”
- `GoalType` + `targetDate`/`targetTime` → “5 km · 14/03/2027”
- Disponibilidade → “3 dias por semana”
- `WeeklyPlan` da semana atual → primeiro treino e resumo da semana
- Se não houver plano: estado de carregamento curto e, depois, fallback honesto.

**Copy inicial:**
- Overline: `SEU PLANO`
- Título: `Rafael, sua semana está pronta.`
- Corpo: `Montei 3 dias para o seu 5 km. Cada treino vem com o porquê — e muda se o seu descanso pedir.`
- CTA primário: `Ver meu treino de hoje`
- CTA secundário: `Como o coach pensa`
- Rodapé de confiança: `Tudo calculado no seu aparelho. Nada é enviado.`
- Sair: `Agora não`

**Estados:**
- **Carregando:** skeleton do card + `Montando sua semana…` (sem números falsos).
- **Sem plano por falta de disponibilidade:** `Ainda não consigo montar sua semana. Ajuste seus dias disponíveis em Perfil.` CTA `Ajustar perfil`.
- **Sem calibração:** mostrar `ritmo por esforço` em vez de pace; não inventar zonas.
- **Erro:** `Não consegui montar sua semana agora.` CTA `Ir para Hoje`.

**Ação:**
- `Ver meu treino de hoje` → marca o tutorial como visto e vai para Hoje.
- `Como o coach pensa` → Cena 2.

---

### 6.2 Cena 2 — “Como o coach pensa”

**Pergunta:** “Por que eu confiaria na recomendação?”

**Layout:**

```
┌──────────────────────────────────────┐
│  ←                          Agora não│
│  ─────────  ━━━━━━━━━  ─────────     │  progresso 2/3
│                                      │
│      [ Você corre ]                  │
│            ↓                         │
│      [ Eu leio esforço e descanso ]  │
│            ↓                         │
│      [ Você recebe o próximo treino ]│
│            e o porquê                │
│                                      │
│  O PORQUÊ                           │
│  Você corre. Eu ajusto.              │
│  Sono, carga e check-in mudam a      │
│  recomendação. Sem dados suficientes,│
│  eu fico conservador — não invento.  │
│                                      │
│  [ Entendi ]                         │
│  Voltar                              │
└──────────────────────────────────────┘
```

**Visual:** um “loop” vertical de 3 cartões pequenos com ícones de 24 dp e uma linha lima conectando. O último cartão pode ser um mini card de treino real. Alternativa mais forte: um comparativo “Sem contexto” vs “Com seus dados”:

```
Sem contexto          Com seus dados
“Corro forte hoje?”   “Hoje é fácil: 40 min Z2”
```

Se usar dados reais de prontidão, mostrar o gauge real e o treino real. Se não houver dados, usar um exemplo rotulado como `EXEMPLO`.

**Copy inicial:**
- Overline: `O PORQUÊ`
- Título: `Você corre. Eu ajusto.`
- Corpo: `Sono, carga e check-in mudam a recomendação. Sem dados suficientes, eu fico conservador — não invento.`
- CTA: `Entendi`
- Voltar: `Voltar`

**Regra:** nada de jargão (`TRIMP`, `ACWR`, `TSB`, `LTHR`). Se precisar citar, traduzir na hora: “carga de treino”, “aumento recente”, “limite de esforço”.

---

### 6.3 Cena 3 — “Comece com 30 segundos”

**Pergunta:** “O que eu faço agora?”

**Layout:**

```
┌──────────────────────────────────────┐
│  ←                          Agora não│
│  ─────────  ─────────  ━━━━━━━━━     │  progresso 3/3
│                                      │
│  PRÓXIMO PASSO                       │
│  Comece com 30 segundos.             │
│  Um check-in rápido já melhora a     │
│  prontidão de hoje.                  │
│                                      │
│  ┌────────────────────────────────┐  │
│  │ ◷  Check-in de hoje      30 s  │  │
│  │    Sono, fadiga, dor e estresse│  │
│  │                             ›  │  │
│  └────────────────────────────────┘  │
│  ┌────────────────────────────────┐  │
│  │ ↑  Importar uma corrida   1 min│  │
│  │    FIT, TCX ou GPX             │  │
│  │                             ›  │  │
│  └────────────────────────────────┘  │
│  ┌────────────────────────────────┐  │
│  │ ♥  Conectar Health Connect 2 min│ │
│  │    Sono, FC de repouso, HRV    │  │
│  │                             ›  │  │
│  └────────────────────────────────┘  │
│                                      │
│  [ Fazer check-in agora ]            │
│  Depois                              │
└──────────────────────────────────────┘
```

**Regras:**
- O check-in é o CTA primário porque é a ação de menor atrito e maior impacto imediato na prontidão.
- Importar e conectar são caminhos alternativos; cada card é tocável e leva direto ao fluxo.
- Se o check-in de hoje já estiver feito, ele aparece como `Feito hoje` e o CTA primário vira `Importar uma corrida`.
- Se o Health Connect já estiver conectado, o card vira `Saúde conectada` e mostra `Ver dados`.
- Se o usuário não tiver arquivo, o card de importação explica `Compartilhe o arquivo pelo botão Compartilhar do seu relógio/app`.

**Copy inicial:**
- Overline: `PRÓXIMO PASSO`
- Título: `Comece com 30 segundos.`
- Corpo: `Um check-in rápido já melhora a prontidão de hoje. Importar uma corrida e conectar o Health Connect deixam o coach mais preciso.`
- CTA: `Fazer check-in agora`
- Secundário: `Depois`
- Rodapé: `Você pode voltar a isto em Hoje.`

**Ação:**
- Marca o tutorial como visto.
- Navega para o fluxo escolhido.
- Ao voltar, o Hoje mostra o checklist com o item concluído.

---

### 6.4 Checklist “Complete seu setup” no Hoje

**Posição:** logo abaixo da saudação, acima da prontidão. É o primeiro card acionável do dia.

```
┌──────────────────────────────────────┐
│  COMPLETE SEU SETUP          1 de 3  │
│  ━━━━━━━━━  ─────────  ─────────     │
│                                      │
│  ✓  Check-in de hoje        Feito    │
│  ○  Importar uma corrida        ›    │
│  ○  Conectar Health Connect     ›    │
│                                      │
│  Agora não                           │
└──────────────────────────────────────┘
```

**Comportamento:**
- Observa check-in de hoje, contagem de atividades e status do Health Connect.
- Cada linha é um alvo de 48 dp com `Role.Button`.
- Item concluído: ícone de check, texto `Feito`, cor secundária; sem confete.
- Progresso: `1 de 3` + barra segmentada; sem gamificação.
- `Agora não` esconde o card por 7 dias; reabre em Perfil → Ajuda.
- Quando todos os itens estão concluídos, o card desaparece e uma mensagem única aparece: `Tudo pronto. Seu coach está atualizado.`
- Se o usuário já tem dados (importou antes de ver o checklist), o item já nasce concluído.

**Por que não é “tarefa”:**
- Não há prazo, punição, streak ou vermelho.
- O texto é “próximo passo”, não “pendência”.
- O card é dismissível e volta apenas se o usuário quiser.

---

### 6.5 Dicas contextuais (uma vez por tela)

| Tela | Gatilho | Copy | Ação | Persistência |
|---|---|---|---|---|
| Corridas | Lista vazia | `Nenhuma corrida ainda. Compartilhe um FIT, TCX ou GPX para ver carga, zonas e splits.` | `Como importar` | `tip_corridas_vazia` |
| Tendências | Sem dados suficientes | `A evolução aparece depois de 2 semanas de corridas. Enquanto isso, o check-in de hoje já ajusta seu treino.` | `Fazer check-in` | `tip_tendencias_vazia` |
| Hoje | Check-in recém-concluído | `Prontidão atualizada. O treino de hoje já considera este check-in.` | — | `tip_checkin_feito` |
| Detalhe da corrida | Primeira corrida importada | `Toque em uma corrida para ver zonas, splits e o porquê do esforço.` | `Ver corrida` | `tip_primeira_corrida` |
| Perfil | Health Connect conectado | `Sono, FC de repouso, HRV e SpO₂ agora entram na prontidão.` | `Ver dados` | `tip_health_conectado` |

**Formato:** card inline no topo da tela (não modal, não overlay). Isso evita empilhamento de diálogos, é acessível e fácil de testar. Spotlight pode ser uma fase posterior.

---

### 6.6 Perfil → Ajuda

Reorganizar a seção atual:

```
AJUDA
┌──────────────────────────────────────┐
│ Rever o tour de boas-vindas          │
│ Seu plano, como o coach pensa e o    │
│ primeiro passo.                      │
│ [ Rever tour ]                       │
└──────────────────────────────────────┘
┌──────────────────────────────────────┐
│ Lembretes                            │
│ Um lembrete por dia, sem cobrança.   │
│ [ Configurar lembretes ]             │
└──────────────────────────────────────┘
┌──────────────────────────────────────┐
│ Saúde e dados                        │
│ [ Conectar Health Connect ]          │
└──────────────────────────────────────┘
```

O “Rever tour” usa a versão curta de 3 cenas, não o carrossel antigo.

---

## 7. Design visual

### 7.1 Conceito: cenas, não slides

Cada cena tem:

- **Foco visual:** 45–55% da altura, com produto real (cards, gauge, mini gráfico).
- **Copy:** overline + título (máx. 2 linhas) + corpo (máx. 3 linhas).
- **Ação:** um CTA primário de largura total + uma saída secundária.
- **Progresso:** barra segmentada no topo.

### 7.2 Grid e layout

- Margem lateral: 20 dp (compacto), 24 dp (médio), 32 dp (tablet).
- Largura máxima de conteúdo: 640 dp, centralizado.
- Área de ação fixa no rodapé, com `navigationBarsPadding`.
- Conteúdo rolável apenas se necessário; o CTA nunca sai da tela.
- Em tablet/landscape, duas colunas: visual à esquerda, copy/ação à direita.

### 7.3 Cores

- Fundo: `bg/base`.
- Glow radial sutil de `accent` atrás do card principal, opacidade ≤ 10%.
- Lima: apenas CTA primário e um destaque de dado.
- Coral: apenas dado fisiológico (FC, esforço), nunca CTA.
- Nenhum hexadecimal solto; tudo via `McTheme.colors`.

### 7.4 Tipografia

- Overline: `McTextStyles.overline`, `textTertiary`.
- Título: `MaterialTheme.typography.headlineLarge` (Geist), `textPrimary`, máx. 2 linhas.
- Corpo: `MaterialTheme.typography.bodyLarge` (Inter), `textSecondary`, máx. 3 linhas.
- Meta de card: `McTextStyles.caption`, `textTertiary`.
- Números: `McTextStyles.metric`, tabular.

### 7.5 Componentes

Criar em `core:designsystem` apenas o necessário:

| Componente | Responsabilidade |
|---|---|
| `TutorialScaffold` | Progresso, voltar, pular, área de conteúdo e ações. |
| `TutorialProgress` | Barra segmentada com semântica de progresso. |
| `TutorialActionCard` | Ícone + título + meta + descrição + chevron; alvo 48 dp. |
| `TutorialScene` | Slot para visual + copy, com espaçamento consistente. |
| `FirstRunChecklistCard` | Checklist no Hoje (pode viver em `:app` se não for reusado). |
| `McSkeleton` | Placeholder de carregamento (já previsto no design system, ainda não implementado). |

Reusar sem alteração: `McCard`, `McCardVariant`, `McPill`, `McStat`, `McGauge`, `McPrimaryButton`, `McSecondaryButton`, `McTextButton`, `McConfidenceBadge`.

### 7.6 Movimento

| Elemento | Movimento | Duração |
|---|---|---|
| Entrada da cena | fade + slide de 12 dp | 220 ms |
| Card principal | escala 0.96 → 1.0 + fade | 220 ms |
| Cards secundários | stagger de 60 ms | 220 ms |
| Gauge | sweep de 0 ao valor | 900 ms (já existe) |
| Troca de cena | shared axis horizontal + fade | 320 ms |
| Conclusão | check + fade, sem confete | 220 ms |

Respeitar `Settings.Global.ANIMATOR_DURATION_SCALE` e redução de movimento. Sem animação infinita.

### 7.7 Acessibilidade

- Barra de progresso com `progressBarRangeInfo` e `contentDescription` “Passo 1 de 3”.
- Título com `heading()`.
- Cada card com `contentDescription` agrupado e `Role.Button`.
- Alvos ≥ 48×48 dp.
- Contraste AA em dark/light.
- Fonte do sistema até 200% sem cortar CTA ou métrica.
- Ordem de foco: voltar → pular → conteúdo → CTA.
- Não usar cor como único indicador de conclusão; usar check + texto `Feito`.

### 7.8 Do / Don't

| Do | Don't |
|---|---|
| Card real do produto como prova | Ícone genérico em quadrado |
| Dados reais do perfil/plano | Números inventados de exemplo |
| Um CTA lima por tela | “Pular” em lima competindo |
| Barra de progresso segmentada | Dots de 8 dp sem semântica |
| Copy de resultado (“sua semana está pronta”) | Copy de interface (“AS TELAS”) |
| “Agora não” discreto | “Pular” como ação principal |
| Uma dica contextual por vez | Vários modais empilhados |

---

## 8. Conteúdo e tom

### 8.1 Regras

- Segunda pessoa, presente, direto.
- Título ≤ 6 palavras; corpo ≤ 2–3 linhas.
- Explicar o benefício antes do mecanismo.
- Traduzir jargão na primeira aparição: `Z2` → “conversável”; `LTHR` → “limite de esforço”.
- Nunca culpar o usuário.
- Não prometer performance não comprovada.
- Evitar “tutorial”, “tour”, “onboarding” na UI.
- Privacidade com peso, mas uma vez só.

### 8.2 Copy deck inicial

| Contexto | Texto |
|---|---|
| Cena 1 overline | `SEU PLANO` |
| Cena 1 título | `{Nome}, sua semana está pronta.` |
| Cena 1 corpo | `Montei {N} dias para o seu {objetivo}. Cada treino vem com o porquê — e muda se o seu descanso pedir.` |
| Cena 1 CTA | `Ver meu treino de hoje` |
| Cena 1 secundário | `Como o coach pensa` |
| Cena 1 confiança | `Tudo calculado no seu aparelho. Nada é enviado.` |
| Cena 2 overline | `O PORQUÊ` |
| Cena 2 título | `Você corre. Eu ajusto.` |
| Cena 2 corpo | `Sono, carga e check-in mudam a recomendação. Sem dados suficientes, eu fico conservador — não invento.` |
| Cena 2 CTA | `Entendi` |
| Cena 3 overline | `PRÓXIMO PASSO` |
| Cena 3 título | `Comece com 30 segundos.` |
| Cena 3 corpo | `Um check-in rápido já melhora a prontidão de hoje. Importar uma corrida e conectar o Health Connect deixam o coach mais preciso.` |
| Cena 3 CTA | `Fazer check-in agora` |
| Cena 3 secundário | `Depois` |
| Checklist título | `Complete seu setup` |
| Checklist subtítulo | `3 passos para o coach ficar mais preciso.` |
| Checklist dismiss | `Agora não` |
| Checklist concluído | `Tudo pronto. Seu coach está atualizado.` |
| Notificação (pré-permissão) | `Quer um lembrete às 18h para o check-in? Um por dia, sem cobrança.` |
| Notificação CTA | `Ativar lembrete` |
| Notificação negativa | `Sem problema. Você pode ativar depois em Perfil → Ajuda.` |

### 8.3 O que evitar

- `BEM-VINDO` / `TUTORIAL` / `AS TELAS` / `SEU PAPEL`.
- `Quatro abas, um coach`.
- `Este tour rápido mostra o essencial`.
- `O que mantém o coach informado` (parece obrigação).
- `Você no controle` como página inteira; virou rodapé de confiança.
- `Tudo pronto` como CTA final sem ação real.

---

## 9. Engenharia

### 9.1 Estado e dados

`TutorialViewModel` deve expor:

```kotlin
data class TutorialUiState(
    val isLoading: Boolean = true,
    val step: Int = 0,
    val name: String? = null,
    val goalLabel: String? = null,
    val availabilityLabel: String? = null,
    val planPreview: PlanPreviewUi? = null,
    val readiness: ReadinessPreviewUi? = null,
    val hasCheckedInToday: Boolean = false,
    val healthConnected: Boolean = false,
    val hasActivities: Boolean = false,
    val isSeen: Boolean = false,
    val error: String? = null,
)
```

- Carregar perfil, plano da semana, check-in de hoje, contagem de atividades e status do Health Connect.
- Reutilizar o mesmo builder do Hoje (extrair `TodayOverviewBuilder` ou um `PlanPreviewBuilder`) para não duplicar regra.
- Se o plano ainda não existe, o tutorial pode aguardar o `TodayViewModel`/bootstrap; não gerar uma segunda versão divergente.
- Sem dados: `planPreview = null` e a UI mostra fallback honesto.

### 9.2 Navegação

- Preferência: camada de primeiro uso sobre o Hoje, não rota separada.
- Se mantiver rota: `BackHandler` marca como visto e sai; não deixar o back fechar o app.
- Ações: `onCheckIn`, `onImport`, `onHealth`, `onToday`.
- Após a ação, remover a camada/rota do back stack.
- `NavigationTest` cobre: perfil → tutorial → check-in → Hoje; perfil → tutorial → pular → Hoje; back.

### 9.3 Persistência

`TutorialStore` (SharedPreferences, sem Room):

```kotlin
introDone: Boolean
introVersion: Int
checklistDismissedUntil: Long?
completedTips: Set<String>
firstActionType: String?
firstActionAt: Long?
```

- `markIntroDone(version)`.
- `isIntroDone(version)`.
- `dismissChecklist(days = 7)`.
- `markTipShown(id)`.
- Exportar no JSON de dados (opcional, para transparência).

### 9.4 Permissões

- **Remover** `requestNotificationPermission()` do `MainActivity.onCreate`.
- Criar `NotificationPrimingCard`:
  - Aparece depois do primeiro check-in ou no checklist.
  - Explica benefício e frequência.
  - Só chama `POST_NOTIFICATIONS` quando o usuário toca em `Ativar lembrete`.
  - Negativa: mensagem calma + caminho para Perfil → Ajuda.
- Health Connect: manter o fluxo de pré-permissão existente (`HealthConnectContent`) e nunca pedir junto com notificação.

### 9.5 Strings

- Mover todo o texto do tutorial para `app/src/main/res/values/strings.xml`.
- Usar `stringResource` e `formatArgs` para nome/objetivo.
- Evitar concatenação de frases; usar placeholders.
- Não é necessário traduzir agora, mas a estrutura fica pronta.

### 9.6 Testes

| Tipo | Casos |
|---|---|
| Unit | `TutorialViewModel`: loading, nome, objetivo, plano real, fallback sem plano, check-in feito, Health conectado, erro, markSeen, versão. |
| Compose | 3 cenas, CTA, voltar, pular, ações, checklist, dismiss, font 200%, dark/light, TalkBack. |
| Screenshot | 3 cenas × dark/light × 393 dp; narrow 360 dp; tablet 640 dp; Hoje com checklist. |
| Navigation | perfil → tutorial → ação → Hoje; back; revisão pelo Perfil. |
| Regression | back não reabre o tutorial; notificação não aparece no launch; checklist persiste. |

- Atualizar `TutorialScreenshotTest` para capturar todas as cenas.
- Goldens com dados sintéticos; nenhum dado real.
- Rodar `./gradlew check`, `spotlessCheck`, `koverVerify` e `verifyRoborazziDebug`.

### 9.7 Performance e privacidade

- Sem imagens pesadas; cenas em Compose.
- Sem nova dependência.
- Sem analytics de terceiros.
- Sem dados de saúde reais em fixtures, screenshots ou logs.
- Medição local e exportável.

### 9.8 Arquivos previstos

```
app/src/main/kotlin/com/meucoach/app/ui/tutorial/
  TutorialScreen.kt          (reescrever)
  TutorialViewModel.kt       (expandir)
  TutorialStore.kt           (versionar/checklist/tips)
  TutorialScenes.kt          (novo: cenas e previews)
  FirstRunChecklist.kt       (novo)
  FirstRunTips.kt            (novo)
  NotificationPriming.kt     (novo)

core/designsystem/src/main/kotlin/com/meucoach/core/designsystem/component/
  TutorialProgress.kt        (novo)
  TutorialActionCard.kt      (novo)
  McSkeleton.kt              (novo, se necessário)

app/src/main/kotlin/com/meucoach/app/
  MeuCoachApp.kt             (gate/camada)
  MainActivity.kt            (remover permissão no launch)

app/src/main/kotlin/com/meucoach/app/ui/today/
  TodayScreen.kt             (checklist)
  TodayViewModel.kt          (estado do checklist, se necessário)

app/src/main/res/values/strings.xml
```

---

## 10. Roadmap por fases

### Fase A — Correções de base (esforço S, impacto médio)

Objetivo: parar de parecer rascunho sem reescrever a narrativa.

- [x] `BackHandler` no tutorial: volta uma página ou marca como visto na primeira.
- [x] Remover permissão de notificação do `MainActivity`; a ativação foi para Perfil → Ajuda, com pré-permissão.
- [x] “Pular” em `textSecondary`, não lima.
- [x] CTA primário de largura fixa; “Voltar” como ícone no topo.
- [x] Barra de progresso segmentada com semântica (`Passo X de Y`).
- [x] Largura máxima 640 dp e padding responsivo.
- [x] Strings em `strings.xml`.
- [x] Screenshots de todas as páginas em dark/light.
- [x] Teste de fonte 200% e TalkBack.
- [x] Corrigir o vazio: visual com peso próprio e texto ancorado.

**Aceite:** o tour atual fica apresentável e sem bugs de plataforma, mas ainda é o carrossel antigo.

**Implementação (05/11/2026):** `TutorialProgress` em `core:designsystem`; `NotificationReminderCard` em Perfil → Ajuda; goldens `tutorial-2/3-{dark,light}.png`; capturas de gráficos Vico estabilizadas com espera explícita (sem mudança de produto).

### Fase B — Redesenho da narrativa (esforço M, impacto alto)

Objetivo: trocar 4 slides por 3 cenas com produto real.

- [x] Cena 1 “Seu plano está pronto” com nome, objetivo e plano real.
- [x] Cena 2 “Como o coach pensa” com loop visual.
- [x] Cena 3 “Comece com 30 segundos” com 3 ações.
- [x] `TutorialProgress`, `TutorialActionCard` e scaffold em `TutorialContent`.
- [x] Movimento e haptics leves na troca de cena.
- [x] Fallbacks honestos (sem plano, sem calibração, carregando, erro).
- [x] Checklist “Complete seu setup” no Hoje.
- [ ] Dicas contextuais nas telas vazias — adiado para a Fase C.
- [x] Goldens de todas as cenas (3 páginas × dark/light).

**Aceite:** um usuário novo entende o valor, vê o plano real e chega ao check-in em ≤ 2 toques.

**Implementação (05/11/2026):** o tutorial virou uma camada sobre o Hoje (`MainContent`), com `TutorialViewModel` lendo perfil, plano, prontidão, check-in, atividades e Health Connect. A cena 1 mostra a semana real, a cena 2 usa a prontidão/treino reais e a cena 3 prioriza check-in → importar → Health Connect. O checklist do Hoje observa os mesmos repositórios, persiste o dismiss por 7 dias e desaparece quando os 3 passos terminam. Goldens: `tutorial-{dark,light}`, `tutorial-2-{dark,light}`, `tutorial-3-{dark,light}` e `today-checklist-dark`.

### Fase C — Ativação e permissões (esforço M, impacto alto)

- [x] Pré-permissão de notificação contextual em Perfil → Ajuda (Fase A).
- [x] Checklist persistente com deep links e dismiss de 7 dias.
- [ ] Primeira vitória: mensagem após check-in/import.
- [ ] Medição local (timestamps/contadores) + export no JSON.
- [ ] Versionamento do tutorial e revisão no Perfil.
- [ ] Dicas contextuais nas telas vazias (check-in, importação, Tendências).
- [ ] Teste com 5 usuários (incluindo o usuário 0).
- [ ] Ajustes de copy e ordem com base no teste.

**Aceite:** ASI em 24 h ≥ 40% na primeira coorte; negativa de notificação não piora.

### Fase D — Experimentos (contínuo)

- [ ] A/B local (debug/coorte) entre “plano primeiro” e “como funciona primeiro”.
- [ ] A/B entre check-in e importar como CTA primário.
- [ ] Spotlight no Hoje (opcional).
- [ ] Dicas contextuais adicionais em Tendências e detalhe da corrida.

---

## 11. Riscos e decisões

| Risco | Mitigação |
|---|---|
| Plano real ainda não existe quando o tutorial abre | Camada sobre o Hoje + builder compartilhado; skeleton honesto; fallback. |
| Mais uma tela aumenta abandono | 3 cenas, puláveis, primeira cena já entrega valor. |
| Checklist parece tarefa/gamificação | Sem streak, sem prazo, sem vermelho; dismissível; copy “próximo passo”. |
| Dados reais de saúde em screenshots | Fixtures sintéticas; revisão do diff. |
| Design system diverge | Reusar componentes; criar só 3–4 específicos. |
| Permissão de notificação continua ruim | Pré-permissão após valor; negativa graciosa. |
| Usuário antigo se sente repetido | Versionar e não forçar tour completo; card opcional no Perfil. |
| Escopo cresce para “central de ajuda” | Fase B entrega só o essencial; ajuda completa fica para depois. |

**Decisões que precisam de aprovação:**

1. Tutorial como camada sobre o Hoje (recomendado) vs. rota separada.
2. Mostrar o tour novo para usuários antigos ou só para novos installs.
3. Check-in como CTA primário (recomendado) vs. importar corrida.
4. Medição local entra agora ou só depois do teste com usuários.

---

## 12. Definition of Done

- [ ] Usuário novo vê a cena 1 com nome, objetivo e plano real em < 1 s após salvar o perfil.
- [ ] Chega ao check-in em ≤ 2 toques e conclui em ≤ 30 s.
- [ ] Check-list no Hoje com 3 itens, progresso, deep links e dismiss.
- [ ] Permissão de notificação nunca aparece antes de uma pré-permissão contextual.
- [ ] Back no tutorial não reabre o tour na próxima abertura.
- [ ] 3 cenas funcionam em dark/light, 200% de fonte, 360/393/412 dp e tablet.
- [ ] TalkBack anuncia passo, título e ações.
- [ ] Nenhum número inventado; fallback honesto quando falta dado.
- [ ] Nenhuma dependência nova; nenhum dado real em fixtures/goldens.
- [ ] `./gradlew check`, `spotlessCheck`, `koverVerify` e screenshots verdes.
- [ ] `DESIGN_SYSTEM.md`, `TESTING.md`, `PROGRESS.md`, `ROADMAP.md` atualizados.
- [ ] ADR-0020 (ativação e permissões) criado, se a decisão de camada sobre o Hoje for aprovada.

---

## 13. Referências

- `docs/PRODUCT.md` — promessa, usuário 0, princípios.
- `docs/DESIGN_SYSTEM.md` — marca, tokens, componentes, movimento, acessibilidade.
- `docs/COMMUNICATION.md` — linguagem simples, benefício antes do mecanismo.
- `docs/GLOSSARY.md` — tradução de jargão.
- `docs/TESTING.md` — testes de UI, screenshots, acessibilidade.
- `docs/STORY_V2.md` — referência de composição visual e narrativa.
- Runna onboarding case study (Growth Dives, 2025) — carrossel curto, personalização, plano revelado, confiança.
- Eleken, “Mobile App Onboarding Best Practices” (2026) — aha moment, 3 telas, personalização, permissões contextuais, checklist.
- NN/g — leitura de telas e microcopy.
- Amplitude — time to value e retenção.

---

## 14. Nota de implementação

As frentes de gráficos, carga e prontidão foram commitadas antes deste plano. Antes de implementar:

1. Criar uma branch/worktree dedicada (`feat/tutorial-v2`).
2. Não misturar com o trabalho de gráficos/saúde já concluído.
3. Começar pela Fase A (correções de base) para reduzir risco.
4. Só então reescrever a narrativa (Fase B).
