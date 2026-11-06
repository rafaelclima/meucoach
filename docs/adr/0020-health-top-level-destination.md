# ADR 0020 — Saúde como destino principal do app

**Status:** aceito e implementado em 05/11/2026
**Data:** 2026-11-05
**Responsável:** planejamento com Rafael (usuário 0)

---

## 1. Contexto

A tela “Saúde e dados” foi criada como detalhe do Perfil
(`profile/metrics-import`), com seta de voltar. Ela reúne:

- o painel de recuperação com sono, FC de repouso, HRV e SpO₂;
- a janela de 7/30/60 dias e os gráficos por métrica;
- o estado do Health Connect e a importação de CSV;
- o resumo do que já está salvo.

O painel ficou forte, mas a posição não corresponde ao valor:

- o usuário precisa lembrar que ele existe dentro do Perfil;
- o Perfil repete um card de Health Connect, criando duas fontes de verdade;
- a tela responde **“como estou recuperando?”**, enquanto o Hoje responde
  **“o que faço agora?”** (`PRODUCT.md` §5.2);
- o Hoje já tem muitos cards; incorporar o painel inteiro quebraria a decisão
  única e enterraria o treino do dia.

O `DESIGN_SYSTEM.md` §8.2 já trata “Saúde” como uma tela com perguntas
próprias (“Como estou recuperando?”, “Como evoluí nos últimos dias?”), mas a
navegação não refletia isso.

## 2. Decisão

1. **Saúde vira o 5º destino da barra inferior:**
   `Hoje · Corridas · Tendências · Saúde · Perfil`.
2. A rota deixa de ser filha de Perfil e passa a ser top-level (`health`),
   **sem seta de voltar**.
3. O conteúdo da tela é preservado: hero de recuperação, período, gráficos por
   métrica e fontes (Health Connect/CSV).
4. O Hoje ganha uma ponte no card de prontidão: **“Ver recuperação”**. O Hoje
   não duplica gráficos nem vira um segundo painel de saúde.
5. O Perfil perde o card grande de Health Connect; mantém um link compacto
   “Saúde e dados” e o card de backup/privacidade.
6. Checklist e tutorial apontam para a aba Saúde.
7. O rótulo da aba é **Saúde** (curto e claro). O ícone sugerido é
   `Icons.Outlined.MonitorHeart`, já disponível em `material-icons-extended`
   (sem dependência nova).
8. A ordem dos destinos é Hoje, Corridas, Tendências, Saúde, Perfil. A barra
   usa `McBottomBar`, que já distribui os itens com peso igual.

## 3. Alternativas rejeitadas

1. **Colocar o painel inteiro dentro do Hoje.**
   O Hoje responde “o que faço agora”; quatro gráficos + fontes transformariam
   a tela principal em duas telas. O usuário teria que rolar por saúde para
   chegar ao treino.
2. **Manter no Perfil e só melhorar o card.**
   O diagnóstico do usuário está certo: a tela é boa demais para ficar como
   detalhe de configuração. Um card melhor não resolve a hierarquia.
3. **Transformar Tendências em “Evolução” com segmentos Treino/Saúde.**
   Mantém 4 abas e agrupa análises, mas a Saúde fica um toque menos visível e
   introduz um padrão de segmentos que o app ainda não usa. Fica como plano B
   se 5 abas não funcionarem em fonte 200% ou tela estreita.
4. **Card no Hoje + detalhe, sem nova aba.**
   Boa contextualização, mas a tela completa continua sendo um drill-down e o
   usuário só a encontra se rolar o Hoje. Não resolve “estar em evidência”.

## 4. Consequências

**Positivas**

- A Saúde passa a ser descoberta sem depender do Perfil.
- O Perfil volta a ser identidade, calibração, backup e ajuda.
- O Hoje continua decidindo o treino, com uma ponte explícita para o detalhe.
- Nenhuma dependência nova e nenhuma migração de Room.
- O painel existente é reaproveitado; o custo principal é navegação, header,
  testes e goldens.

**Custos e riscos**

- 5 abas exigem validação em 360 dp e fonte 200%; “Tendências” é o rótulo mais
  longo e precisa continuar em uma linha.
- Remover a rota antiga exige atualizar os call sites de Perfil, checklist e
  tutorial. Não há deep link externo para `profile/metrics-import` hoje.
- A tela de Saúde mistura dashboard com fontes/CSV. Nesta fase, a seção
  “Fontes de dados” continua no fim da mesma tela; se crescer, vira uma tela
  secundária “Gerenciar fontes”.
- O Hoje ganha mais um alvo de toque; o link precisa de `contentDescription` e
  não pode competir com “Iniciar”.

## 5. Implementação

O plano detalhado (rotas, arquivos, testes, goldens e critérios de aceite) está
em [`docs/HEALTH_TOP_LEVEL_PLAN.md`](../HEALTH_TOP_LEVEL_PLAN.md), Fase 1.

## 6. Referências

- `docs/PRODUCT.md` §5.2 (uma decisão por tela)
- `docs/DESIGN_SYSTEM.md` §8.2 e §11 (gráficos e blueprints)
- `docs/adr/0005-health-connect.md`
- `docs/adr/0014-incremental-health-sync.md`
- `docs/adr/0016-daily-metrics-merge.md`
