# SPORTS_SCIENCE.md — MeuCoach

**Versão:** 1.0 · **Data:** 27/10/2026 · **Modelo:** `engine 1.0.0` · **Evidências:** `evidence 1.0.0`

> Este documento é a fonte de verdade das regras de treinamento. Se o código divergir daqui, o código está errado — ou este documento precisa de uma nova versão com justificativa.

## 1. Escopo e limites

O MeuCoach prescreve **corrida** para adultos saudáveis, com objetivo de performance geral ou provas de rua. Ele **não** diagnostica, não trata lesões, não prescreve nutrição ou emagrecimento e não substitui avaliação médica.

Toda recomendação é uma **hipótese informada por dados**, não uma ordem. O usuário pode ignorar, mover ou substituir um treino; o app registra a decisão e recalibra.

## 2. Princípios

1. **Individualização.** Nenhuma zona ou plano é copiado de outra pessoa.
2. **Consistência > intensidade.** Para iniciantes, o maior ganho vem de volume fácil sustentado.
3. **80/20 é heurística, não dogma.** A distribuição alvo é predominantemente fácil, com estímulos de qualidade doseados.
4. **Carga é contexto, não culpa.** ACWR e TSB são sinais; nenhum decide sozinho.
5. **Prontidão subjetiva é dado de primeira classe.** Sono, fadiga, dor e stress importam tanto quanto HRV.
6. **Quando faltam dados, seja conservador e transparente.** Confiança baixa nunca vira recomendação agressiva.
7. **Explique.** Toda sessão tem regras e evidências rastreáveis.
8. **Segurança primeiro.** Sinais de alerta interrompem a prescrição.

## 3. Hierarquia de evidência

| Nível | Tipo | Uso |
|---|---|---|
| A | Revisão sistemática / meta-análise / consenso | Base de regras estruturais |
| B | Ensaio controlado / estudo prospectivo | Ajustes e parâmetros |
| C | Estudo observacional / série de casos | Hipóteses e heurísticas |
| D | Prática de treinadores / livro técnico | Defaults, quando não há A/B |
| E | Inferência do produto | Só com confiança baixa e monitoramento |

Cada regra declara `strength` (`STRONG`, `MODERATE`, `LIMITED`) e as evidências que a sustentam. Regras com evidência LIMITED não podem gerar aumento de carga; só manutenção ou redução.

## 4. Perfil e calibração

### 4.1 Dados de entrada

- Idade, sexo biológico, altura, peso.
- FC de repouso (medida ou média de 7 dias).
- FC máxima **observada** (nunca 220 − idade).
- LTHR e pace de limiar (se conhecidos; o LTHR pode ser informado manualmente).
- Melhor performance recente (5 km, 10 km, meia, maratona) com data.
- Volume e frequência atuais.
- Disponibilidade semanal e duração máxima por sessão.
- Objetivo, prova-alvo, terreno, clima, sono, histórico de lesões.

### 4.2 Tiers de calibração

| Tier | Condição | Método | Confiança |
|---|---|---|---|
| **A** | Prova ou TT ≤ 8 semanas | VDOT pela performance; LTHR pela FC média do esforço de 30 min | Alta |
| **B** | ≥ 3 treinos com FC + pace, sem prova recente | Pace de limiar pelo melhor esforço sustentado de 20–35 min com FC; VDOT derivado (a 88%); LTHR só na janela 25–35 min | Média; confirmação com teste de 30 min |
| **C** | Iniciante ou < 3 treinos | 2 semanas de base + teste de 30 min guiado | Baixa → Alta após teste |

**TT de 30 min (protocolo):** 10 min de aquecimento fácil + 30 min em esforço forte e constante (não sprint) + 10 min de desaquecimento. LTHR = FC média do esforço (ou dos últimos 20 min). Pace de limiar = pace médio do esforço (ou dos últimos 20 min). Não fazer se doente, lesionado ou com sono < 6 h.

### 4.3 VDOT e paces

- VDOT é derivado da performance (Daniels) e atualiza as paces E/M/T/I/R.
- Fórmula usada: `VO2 = -4,60 + 0,182258·v + 0,000104·v²` (v em m/min) e
  `%VO2max = 0,8 + 0,1894393·e^(−0,012778·t) + 0,2989558·e^(−0,1932605·t)` (t em min).
  `VDOT = VO2 / %VO2max`.
- Pace de limiar = velocidade a 88% do VDOT, obtida invertendo a equação do VO2.
- **Tier B (ADR 0035):** sem prova recente, o melhor esforço sustentado de
  20–35 min com FC (o mais rápido; empate → mais recente) estima o pace de
  limiar (pace médio do treino, que subestima o limiar real — direção
  conservadora). O VDOT é derivado assumindo o esforço como limiar
  (`VDOT = VO2(v) / 0,88`), com os limites 10–90. A LTHR vem da FC média desse
  esforço **apenas** na janela 25–35 min; fora dela não há LTHR. Confiança
  média e confirmação explícita no salvamento. A regressão pace×FC com
  remoção de outliers fica para uma evolução com seleção de treinos por tipo.
- LTHR = FC média de um esforço de 25–35 min (TT de 30 min ou prova curta); fora
  dessa janela não há LTHR estimada.
- O LTHR pode ser informado manualmente no perfil (100–230 bpm, acima da FC de
  repouso e sem passar da FC máxima observada). É o caminho honesto para quem já
  fez o teste e não quer depender de uma prova importada.
- Uma corrida importada só pode **pré-preencher** o formulário de calibração
  quando: tem até 8 semanas, dura 25–35 min, tem ≥ 1 km, FC média presente,
  ≥ 10 amostras de FC cobrindo ≥ 60% dos samples, variação p10–p90 ≤ 30 bpm e
  nenhuma pausa > 2 min.
  Mesmo assim o app exige confirmação explícita de que foi um esforço máximo e
  contínuo; nada é salvo automaticamente. Longões, treinos leves, intervalados e
  arquivos com perda de sinal continuam sendo apenas atividades.
- Se não houver performance válida, o motor não inventa VDOT: usa zonas de FC e RPE até o teste.
- Performance é "recente" por 8 semanas (56 dias). Depois disso, voltar para Tier B/C.
- Recalibrar a cada 6–8 semanas, após prova ou mudança relevante de volume.

### 4.4 FC máxima

- Usar **maxHrObserved** de arquivos reais (pico sustentado, não artefato).
- Se a média de um esforço de 30 min for > 95% da maxHr informada, a maxHr informada é inconsistente: priorizar LTHR e marcar a maxHr como suspeita.
- Zonas de treino são por **LTHR**, não por maxHR.

## 5. Zonas

### 5.1 FC — 5 zonas por LTHR (Friel)

| Zona | % LTHR | Uso |
|---|---|---|
| Z1 | < 81% | Recuperação, aquecimento, caminhada |
| Z2 | 81–89% | Base aeróbica, fácil/conversável |
| Z3 | 90–93% | Marathon pace / tempo leve |
| Z4 | 94–99% | Limiar, tempo forte |
| Z5 | ≥ 100% | VO2max, intervalados curtos, prova |

### 5.2 Pace — 5 zonas (Daniels)

| Zona | Pace | Uso |
|---|---|---|
| E | Easy | 65–78% VO2max; maioria do volume |
| M | Marathon | ritmo de maratona |
| T | Threshold | 20–40 min contínuos ou intervalos |
| I | Interval | 3–5 min por repetição, VO2max |
| R | Repetition | 200–400 m, velocidade/economia |

### 5.3 Classificação polarizada (3 zonas)

- **Fácil:** Z1–Z2 (FC) / E–M (pace).
- **Moderado:** Z3 / T.
- **Forte:** Z4–Z5 / I–R.

O app exibe 5 zonas, mas mede a distribuição em 3 para o alvo 80/20.

### 5.4 Cadência

- Cadência é **individual**; não existe alvo universal de 180 spm.
- Usar como tendência e indicador de fadiga/terreno, não como correção forçada.
- Aumento gradual de cadência só se houver overstriding evidente e sem dor.

### 5.5 Pacing e deriva cardíaca

- Comparar a primeira e a segunda metade da corrida por distância/tempo, não por sensação.
- Ritmo constante ou **negative split** é sinal positivo de controle; queda > 3% na segunda metade sugere fadiga, calor ou largada forte.
- **Deriva cardíaca:** FC sobe ≥ 5% com pace estável (±3%). Pode refletir calor, desidratação ou fadiga. É observação, não diagnóstico.
- Sem laps válidos, não há insight: dado ausente não vira elogio nem alerta.

## 6. Carga de treino

### 6.1 Métricas

| Métrica | Fórmula | Uso |
|---|---|---|
| **TRIMP** (Banister) | `dur(min) × ΔHR × 0,64·e^(1,92·x)` (homens) / `0,86·e^(1,67·x)` (mulheres), `x=(HRavg−HRrest)/(HRmax−HRrest)` | Carga fisiológica por FC |
| **rTSS** | `(seg × NGP / paceLimiar)² × 100 / 3600` | Carga por pace normalizado |
| **sRPE** | `RPE × minutos` | Carga subjetiva; valida FC duvidosa |
| **CTL** | EMA de 42 dias | Fitness/carga crônica |
| **ATL** | EMA de 7 dias | Fadiga/carga aguda |
| **TSB** | `CTL − ATL` | Balanço/frescor |
| **ACWR** | `carga 7d / média 28d` | Sinal de risco; **não** é meta |
| **Monotonia** | `média diária / desvio-padrão` | Semana monótona = risco |
| **Strain** | `carga semanal × monotonia` | Combinação de volume e monotonia |

**Importante:** TRIMP e rTSS medem coisas diferentes e não são somados. O app mostra os dois e usa TRIMP como principal quando a FC é confiável; rTSS quando só o pace é confiável. Na série diária do PMC, cada dia recebe um único métrico: TRIMP se houver FC válida; caso contrário, rTSS; sem nenhum dos dois, o dia fica sem carga (não vira zero silencioso).

Ambos são expressos em **pontos de carga** na mesma ordem de grandeza: 100 pontos ≈ 1 hora no limiar para o rTSS; no TRIMP, uma hora fácil rende menos e uma hora forte rende mais. O número serve para comparar o atleta com ele mesmo ao longo do tempo, nunca entre pessoas diferentes.

**Inicialização do PMC:** CTL e ATL são médias exponenciais e dependem da carga anterior à janela exibida. O app alimenta até 180 dias de histórico antes dos 42 dias mostrados, para não reiniciar a CTL em zero quando existem atividades mais antigas (BUG-03). Sem histórico anterior, a série começa honestamente em zero; nada é inventado. ACWR e monotonia continuam calculados somente sobre a janela de decisão (7 e 28 dias), sem usar o aquecimento.

### 6.2 Normalização de NGP

- Sem elevação confiável: pace bruto.
- Com elevação: ajuste por grade (grade-adjusted pace) em versão futura; no MVP, marcar confiança média.
- Treadmill: pace da esteira + inclinação informada; sem GPS.

### 6.3 ACWR com ressalvas

- Calcular 7/28, mas **não** usar como lei.
- A literatura recente questiona a validade teórica e estatística do ACWR (Impellizzeri et al., 2020).
- Uso no app: **flag** para revisar carga, nunca para liberar aumento.
- Faixa de atenção: < 0,8 ou > 1,3. > 1,5 = alerta; não aumentar volume.
- ACWR só é exposto quando a fonte de atividades está fresca (última importação
  em até 14 dias) e há pelo menos 8 dias com carga nos últimos 28. Caso
  contrário, o valor é `null` e o estado é `INSUFFICIENT` ou `STALE`; `null`
  nunca significa “sem restrição” (`R-DATA-01`, ADR 0026).
- Dia sem atividade só conta como descanso quando a fonte está fresca. Com
  fonte desatualizada, o dia é “sem dado”, não um zero fisiológico.

### 6.4 Distribuição semanal

- Alvo: ~80% do **tempo** em fácil (Z1–Z2), ~20% em moderado/forte.
- Iniciantes: pirâmide (mais fácil, pouco limiar, quase nada de VO2max).
- Intermediários: polarizado/pirâmide conforme objetivo.
- Máximo de 2 sessões de qualidade/semana; nunca em dias consecutivos.
- Longão: 25–30% do volume semanal; iniciantes ≤ 150 min.

## 7. Prontidão

### 7.1 Entradas e pesos

| Entrada | Peso (completo) | Normalização |
|---|---|---|
| Check-in subjetivo (Hooper: sono, fadiga, dor, stress) | 35% | soma invertida, 4–20 → 0–100 |
| Sono (duração + qualidade) | 25% | 7–9 h = 100; 6 h ≈ 40; < 5 h = 0 |
| HRV (ln rMSSD, média 7d vs baseline 60d) | 25% | z-score −2..+2 → 0..100; SWC = 0,5 × SD |
| FC de repouso (7d vs 30d) | 15% | −5 bpm = melhor; +7 bpm = pior |

Quando falta uma entrada, os pesos são **renormalizados** e a confiança cai. Nunca preencher ausência com valor neutro silencioso.

### 7.2 Níveis

| Nível | Score | Ação |
|---|---|---|
| **Verde** (UI: **Alta**) | ≥ 70 | Executar o plano; pode progredir se as outras regras permitirem |
| **Âmbar** (UI: **Moderada**) | 40–69 | Reduzir volume/intensidade em 10–20%; trocar qualidade por fácil |
| **Vermelho** (UI: **Baixa**) | < 40 | Qualidade suspensa; fácil curto ou descanso; checar sinais |

No app, o texto visível é **Alta / Moderada / Baixa**; a cor acompanha o mesmo estado, mas o rótulo nunca depende dela (leitura para daltonismo, TalkBack e telas em preto e branco).

**Overrides de segurança** (independente do score):

- Doença febril, dor aguda, lesão ou tontura ⇒ vermelho.
- RHR +7 bpm por 2 dias consecutivos ⇒ no mínimo âmbar.
- Sono < 6 h na noite anterior ⇒ sem qualidade.
- ACWR > 1,5 ⇒ não aumentar carga.
- HRV abaixo do SWC por 3+ dias + RHR elevada ⇒ âmbar/vermelho.

### 7.3 Confiança

| Confiança | Critério |
|---|---|
| **Alta** | ≥ 3 entradas objetivas + check-in, cobertura ≥ 80% |
| **Média** | 2 entradas ou cobertura 40–80% |
| **Baixa** | 1 entrada ou apenas subjetivo; sem HRV/RHR |

A UI mostra os contribuintes e o que falta. "Sem dados" não é "recuperação ruim".

### 7.4 HRV — regras técnicas

- Usar **ln rMSSD**, não RMSSD bruto.
- Baseline móvel de 60 dias; comparar média de 7 dias.
- z-score = (média de ln rMSSD em 7 dias − média de ln rMSSD em 60 dias) ÷ desvio-padrão de ln rMSSD na baseline. A média bruta fica só para exibição; misturar média aritmética bruta com desvio log desloca o score (BUG-04).
- Só calcular se houver ≥ 4 noites válidas na semana.
- SWC = 0,5 × desvio-padrão do baseline.
- Tendência importa mais que valor absoluto.
- Não comparar HRV entre pessoas.

## 8. Prescrição

### 8.1 Estrutura de ciclo

| Bloco | Duração | Foco |
|---|---|---|
| **Base** | 4–8 semanas | Volume fácil, consistência, economia de corrida |
| **Build** | 4–6 semanas | Limiar, VO2max, pace específico |
| **Peak** | 2–3 semanas | Qualidade específica, volume estável |
| **Taper** | 2 semanas (5k–meia) / 3 (maratona) | Reduzir volume 40–60%, manter intensidade |
| **Recuperação** | 1–2 semanas | Volume baixo, sem qualidade |

Deload a cada 4ª semana (−20 a −30% de volume). Sem progressão após semana de prova.

### 8.2 Semana padrão (iniciante/intermediário)

| Dia | Sessão |
|---|---|
| Seg | Descanso ou caminhada leve |
| Ter | Fácil Z2 + 6×20 s strides |
| Qua | Força (rotina do usuário) / descanso |
| Qui | Qualidade (tempo ou intervalos) — se verde |
| Sex | Fácil Z1–Z2 curto |
| Sáb | Longão Z2 |
| Dom | Descanso |

A musculação é rotina do usuário e **não é prescrita** pelo app; o check-in captura fadiga global. Evitar qualidade no dia seguinte a perna pesada.

### 8.3 Progressão

Aumentar volume semanal em **5–8%** somente se:

- Prontidão verde;
- ACWR entre 0,8 e 1,3;
- Sono médio ≥ 6,5 h;
- Sem dor > 2/10;
- Semana anterior concluída ≥ 80%.

Caso contrário, manter. Nunca aumentar duas semanas seguidas após falha.

**Deload:** a cada 4ª semana consecutiva de plano, reduzir o volume em **20–30%**
(o motor usa −25%) mantendo o tipo/intensidade das sessões (`R-PLAN-04`,
`EV-ISSURIN-2010`). Taper tem precedência sobre o deload. Uma lacuna no histórico
de planos reinicia o bloco (ADR 0011).

### 8.4 Sessões de qualidade

| Tipo | Exemplo | Alvo |
|---|---|---|
| **Strides** | 6×20 s rápido controlado, 60 s trote | Neuromuscular, economia |
| **Tempo** | 20–30 min contínuos em Z3–Z4 | Limiar |
| **Intervalos VO2max** | 5×3 min Z4–Z5, 2 min trote | VO2max |
| **Colinas** | 6×45 s subida forte, caminhada desce | Força específica |
| **Treadmill** | 6×2 min a 5–10% inclinação, Z4 | Controle de calor/impacto |

Aquecimento 10–15 min + mobilidade; desaquecimento 5–10 min. Qualidade nunca em dia de sono < 6 h.

### 8.5 Longão

- 25–30% do volume semanal.
- Iniciantes: 45–90 min; aumentar 5–10 min/semana.
- Z2, com opção de acelerar os últimos 10–20 min em Z3 no build.
- Não fazer longão no dia seguinte a qualidade.

### 8.6 Taper

- Volume −40 a −60%; intensidade mantida em pequenas doses.
- Última sessão intensa 7–10 dias antes da prova (5k/10k) ou 10–14 dias (meia/maratona).
- Sem treino novo, sem tênis novo, sem dieta nova na semana da prova.

### 8.7 Recomendação diária conservadora

Precedência determinística do `WorkoutRecommender`:

1. Doença/lesão ou prontidão vermelha ⇒ **descanso** (`R-SAFE-01`, `R-READY-03`).
2. Dia sem disponibilidade explícita ⇒ **descanso** (`R-PLAN-01`).
3. Sono < 6 h, prontidão âmbar ou `NO_QUALITY` ⇒ **fácil**, sem qualidade (`R-READY-03`, `R-READY-04`).
4. ACWR > 1,5 ⇒ **fácil**, sem aumento de carga (`R-LOAD-03`).
5. Dia verde ⇒ **fácil em Z2**, com pace E e FC Z2 quando houver calibração (`R-ZONE-01`).
6. Sem LTHR/pace de limiar ⇒ fácil por RPE, confiança baixa (`R-CAL-02`).
7. Carga desconhecida (`INSUFFICIENT`) ou desatualizada (`STALE`) ⇒ **fácil**,
   sem aumento e com confiança reduzida (`R-DATA-01`, `EV-IMPELLIZZERI-2020`).
   O texto explica “dados de carga insuficientes/desatualizados”; nunca diz que
   o atleta descansou.

Sem disponibilidade configurada, usar 30 min como base e reduzir a confiança
(`R-DATA-01`). Nenhuma recomendação aumenta volume sem os guardrails do P1-05.

### 8.8 Plano semanal conservador

O `WeeklyPlanGenerator` distribui a disponibilidade do perfil em uma semana
segunda→domingo:

- Dias disponíveis viram sessões; sem disponibilidade, não há plano inventado.
- No máximo **2 qualidades** por semana; a segunda só com progressão e nunca consecutiva/adjacente ao longão.
- Qualidade só em dia verde, não iniciante, com ACWR entre 0,8 e 1,3.
- A qualidade exige `LoadDataState.KNOWN`: pelo menos 8 dias com carga nos
  últimos 28 e última importação de atividades em até 14 dias. Sem cobertura
  ou com fonte velha, o plano não oferece treino forte, reduz a confiança e
  explica “dados de carga insuficientes” ou “desatualizados” (`R-DATA-01`,
  ADR 0026). ACWR desconhecido nunca libera progressão.
- Longão é o dia mais longo disponível, limitado a 25–30% do volume semanal e a 150 min (iniciante).
- Fácil entre 20–60 min; qualidade entre 25–45 min, como "Tempo" (intermediário) ou "Intervalos" (avançado).
- Taper: 2 semanas antes (5k–meia) ou 3 (maratona), volume −45–55% (`R-PLAN-05`, `EV-BOSQUET-2007`, `EV-MUJIKA-2003`).
- Progressão: semana anterior ≥80% concluída + guardrails verdes libera a segunda qualidade; volume nunca ultrapassa a disponibilidade (ADR 0010).
- Deload a cada 4ª semana consecutiva de plano (−25% no volume, intensidade mantida; ADR 0011).
- Troca manual: só entre sessões planejadas futuras da mesma semana, trocando o conteúdo e mantendo data/status; a semana resultante não pode ter duas qualidades consecutivas nem qualidade no dia ou ao lado do longão (ADR 0013, `R-PLAN-02`, `R-PLAN-03`).
- Replanejamento da semana vigente ([ADR 0028](adr/0028-versioned-plan-replanning.md), R-01): regenera apenas os dias estritamente futuros com o mesmo gerador, preserva hoje/passado e sessões `DONE`/`CLOSED` e valida a semana mesclada (máximo de 2 qualidades, um longão, espaçamento e disponibilidade). Sessões passadas `PLANNED` elegíveis são contadas como perdidas sem serem fechadas; o limiar de 2 é heurística de produto (A6), não limiar científico. A revisão não repõe carga nem adiciona treino forte além da intenção futura vigente e não cria `ruleId`/`evidenceId`: apenas reaplica o que o gerador emitiu.
- Confiança do plano é capturada quando a semana é montada e fica no máximo média; ela não muda durante a semana. Novos dados entram no plano seguinte. A UI explica isso em “Por que este plano?” e lista os dados usados e ausentes (`R-DATA-01`).

### 8.9 Calor e umidade

- Natal/RN: calor úmido quase o ano todo.
- Entrada do MVP é **autorreportada** no check-in ("está muito calor?"), não uma temperatura medida; clima automático fica para a Fase 2 (P2-07).
- Ajuste de pace conservador (+3 a +8%) quando temperatura > 26 °C ou ponto de orvalho > 20 °C; usar FC e RPE como guia principal.
- O autorreporte usa o extremo conservador da faixa (**+3%**) e reduz a confiança em um nível. É uma **estimativa**, não uma lei (`R-PLAN-07`, `EV-ELY-2007`).
- Preferir início da manhã; esteira é válida para qualidade no calor (`R-PLAN-08`).
- Com acesso a esteira, a qualidade prescreve velocidade pela zona T quando há pace de limiar calibrado; a inclinação fica em RPE 8/10 para não inventar intensidade (`R-PLAN-08`, `EV-DANIELS-2014`).
- Hidratação e sinais de calor: orientação geral, não prescrição médica.

### 8.10 Prova

- Referência aritmética da meta (ADR 0032): pace médio, faixa ±3% e passagens por
  km derivados de `targetTimeSec`. É referência de leitura, não prescrição de
  treino nem garantia de resultado; a faixa é convenção de produto.
- Previsão por VDOT/Riegel (`R-RACE-03`) **não é implementada nesta fase**. Se
  entrar, usa `EV-DANIELS-2014`/`EV-RIEGEL-1981`, exige calibração explícita,
  confiança e ausência quando não houver dados; Riegel é referência, nunca promessa.
- Plano de prova: negative split conservador ou even pace; nunca largar acima do pace alvo.
- Recuperação: 1 dia fácil por 3 km de prova (heurística), mínimo 3 dias sem qualidade após 5k/10k.

### 8.11 Prescrição do dia e evidência de conclusão

O plano decide a **intenção** da semana; a `DailyPrescription` decide o **dia**. A
prescrição parte da sessão planejada e só pode manter ou reduzir:

- `KEEP` quando o dia está verde e a sessão planejada cabe nos guardrails;
- `REDUCE` quando prontidão/carga pedem menos duração;
- `DOWNGRADE` quando qualidade/longão precisa virar fácil;
- `REST` em doença/lesão ou prontidão vermelha;
- `NO_PLAN` quando não há semana, com fácil conservador e confiança baixa.

Nenhuma decisão aumenta tipo, duração ou intensidade. Sem calibração, pace/FC ficam
`null` e o alvo é RPE. Calor desacelera o pace ou sobe o RPE, sem alongar a sessão.
As regras são `R-PRESC-01` (mantém o plano), `R-PRESC-02` (reduz, nunca aumenta),
`R-PRESC-03` (uma decisão por dia, a mesma em todas as telas) e `R-PRESC-04`
(iniciante não recebe qualidade no dia); a decisão é persistida e versionada
(`ADR 0023`).

`R-PRESC-04` é uma política conservadora de produto (D-6 v0.1), não uma alegação
fisiológica específica: quando o perfil vigente é `BEGINNER` e a sessão de hoje está
`PLANNED` com `QUALITY`, a prescrição faz `DOWNGRADE` para `EASY`. A guarda não cria
sessão, não altera o plano semanal persistido e não pede aceite. Ela não se aplica a
`DONE`/`CLOSED`: esses dias preservam a prescrição, a execução e o histórico já
persistidos, sem recálculo retrospectivo.

Conclusão não é clique (`ADR 0024`):

- `ACTIVITY` — corrida importada e vinculada; conta adesão e execução comprovada.
- `GUIDED` — contrato para a sessão guiada persistente (ciclo 3).
- `MANUAL` — “fiz sem registro”, exige duração > 0 e RPE 1–10; conta adesão com
  confiança baixa, mas **não** cria volume, carga, zonas, distância ou pace.
- `LEGACY_UNVERIFIED` — histórico anterior ao vínculo, preservado sem promoção a
  verdade; não libera progressão.

Descanso, doença e plano substituído encerram a sessão como `CLOSED` com motivo;
não contam como falha de adesão. Doença não impede registrar uma corrida real — ela
fica como treino livre, sem estímulo e sem crédito de progressão.

## 9. Segurança e guardrails

### 9.1 Sinais de parada

- Dor no peito, palpitações, desmaio, falta de ar desproporcional.
- Dor óssea focal, dor que piora ao correr, inchaço, claudicação.
- Febre, infecção, tontura, náusea persistente.
- Em qualquer um: interromper, orientar avaliação médica, registrar no app.

### 9.2 Lesão

- Dor 3/10 ou mais, que altera a passada ou piora durante o treino ⇒ reduzir/cancelar.
- Dor matinal > 30 min ou que piora no dia seguinte ⇒ descanso e avaliação.
- O app não diagnostica; apenas ajusta carga e recomenda procurar profissional.

### 9.3 Sono

- Adultos: 7–9 h (AASM/SRS).
- Média < 6,5 h por 3 noites ⇒ não aumentar volume; < 6 h ⇒ sem qualidade.
- Sono é tratado como **limitador de treino**, não como métrica de culpa.

### 9.4 Peso e impacto

- O app não prescreve emagrecimento nem dieta.
- Com IMC elevado,progressão de volume conservadora e incentivo a cross-training de baixo impacto (caminhada inclinada, esteira, bicicleta) — sem julgamento.
- Força é rotina do usuário; o app não interfere.

## 10. Explicabilidade

Toda recomendação contém:

```json
{
  "sessionType": "EASY",
  "reason": "Prontidão âmbar: sono 5h40 e HRV 0,6 SWC abaixo da baseline.",
  "rules": ["R-READY-03", "R-PLAN-03"],
  "evidence": ["EV-SAW-2016", "EV-PLEWS-2013"],
  "confidence": "MEDIUM",
  "dataUsed": ["sleep", "hrv", "load"],
  "missing": ["restingHr"]
}
```

A UI mostra um resumo no card e o detalhe em "Por que este treino?". O bottom sheet lista o rationale, as regras e evidências catalogadas (mantendo o ID bruto), a confiança e os dados usados/ausentes. Nunca usar texto gerado por LLM para números ou decisões; se houver LLM no futuro, ele apenas reescreve o rationale determinístico.

## 11. Validação

1. **Testes unitários** de fórmulas com casos calculados à mão.
2. **Testes de referência** contra implementações independentes (Python/R) para TRIMP, rTSS, CTL/ATL.
3. **Property-based tests**: monotonicidade, limites, ausência de NaN/negativos, planos que respeitam restrições.
4. **Golden scenarios**: perfil + atividades + métricas → saída esperada versionada.
5. **Regressão com dados reais sanitizados**: 4 FIT + 3 TCX do usuário 0; resumo, zonas e carga batem com o esperado.
6. **Auditoria de regras**: toda recomendação cita regra existente e evidência existente.
7. **Teste de confiança**: remover entradas e verificar que a confiança cai e a agressividade não aumenta.

## 12. Avaliação inicial do usuário 0 (dados reais)

**Dados:** 38 anos, 97 kg, 174 cm, FC repouso 58, 5 km em 30:59 (24/10/2026), FC média 181 / pico 187 na prova, LTHR estimada 180, VDOT ≈ 31.

**Leitura:**

- Zonas de FC: Z1 < 145; Z2 145–160; Z3 161–168; Z4 169–179; Z5 ≥ 180.
- As corridas de rua (6:32–6:53/km, FC média 168–171) estão em **Z4**, não em fácil.
- A prova (6:04/km, FC 181) foi o único esforço realmente forte.
- As sessões de esteira (9:35–9:54/km, FC 106–122) são Z1 — caminhada/slow jog, úteis como volume de baixo impacto.
- Pace fácil provisório: **7:40–8:30/km** (VDOT 31; confirmar com RPE e FC).
- Sono médio de 6 h é um limitador relevante.
- FC máxima informada (185) é incompatível com 31 min a 181 de média; usar LTHR e tratar maxHR como subestimada.

**Plano inicial (2 semanas de baseline):**

- 4 corridas/semana, 20–24 km, 80% em Z1–Z2.
- 2× fáceis de 4–5 km + 6×20 s strides.
- 1 longão de 6–8 km em Z2.
- 1 sessão opcional de esteira em Z1–Z2 (aproveitando a rotina de academia).
- **Sem intervalado estruturado** até completar o baseline e ter sono ≥ 6,5 h.
- Depois: 1 qualidade/semana (ex.: 5×3 min Z4 na esteira, 2 min trote), se verde.
- Recalibrar com TT de 30 min em 6–8 semanas ou nova prova.

**Por que não seguir o padrão atual?** Porque ele concentra quase todo o estímulo em Z4, sem base fácil, com sono curto e pouco tempo de corrida. O caminho de maior retorno é aumentar a base aeróbica em Z2, manter consistência e dosar qualidade — não correr mais forte todos os dias.

## 13. Registro de evidências

| ID | Referência | Força | Usado em |
|---|---|---|---|
| `EV-DANIELS-2014` | Daniels, J. *Daniels' Running Formula* (3rd ed.). Human Kinetics, 2014. | D | VDOT, paces E/M/T/I/R |
| `EV-ABBISS-2008` | Abbiss, C. R., Laursen, P. B. Describing and understanding pacing strategies during athletic competition. *Sports Med* 38(3):239–252, 2008. doi:10.2165/00007256-200838030-00004 | B | Pacing, negative split, distribuição de esforço |
| `EV-PFITZINGER-2008` | Pfitzinger, P., Douglas, S. *Advanced Marathoning* (2nd ed.). Human Kinetics, 2008. | D | Longão, volume e progressão |
| `EV-FRIEL-2018` | Friel, J. *The Triathlete's Training Bible* (5th ed.). VeloPress, 2018. | D | Zonas por LTHR, teste de 30 min |
| `EV-SEILER-2010` | Seiler, S. What is best practice for training intensity and duration distribution in endurance athletes? *IJSPP* 5(3):276–291, 2010. doi:10.1123/ijspp.5.3.276 | A/B | Distribuição 80/20 |
| `EV-BANISTER-1991` | Banister, E. W. Modeling elite athletic performance. In *Physiological Testing of the High-Performance Athlete*, 1991. | D | TRIMP |
| `EV-MORTON-1990` | Morton, R. H., Fitz-Clarke, J. R., Banister, E. W. Modeling human performance in running. *J Appl Physiol* 69(3):1171–1177, 1990. doi:10.1152/jappl.1990.69.3.1171 | B | CTL/ATL/TSB |
| `EV-COGGAN-2010` | Coggan, A. R., Allen, H. *Training and Racing with a Power Meter* (2nd ed.). VeloPress, 2010. | D | TSS/rTSS, PMC |
| `EV-FOSTER-2001` | Foster, C. et al. A new approach to monitoring exercise training. *J Strength Cond Res* 15(1):109–115, 2001. doi:10.1519/00124278-200102000-00019 | B | sRPE, monotonia, strain |
| `EV-GABBETT-2016` | Gabbett, T. J. The training-injury prevention paradox. *Br J Sports Med* 50(5):273–280, 2016. doi:10.1136/bjsports-2015-095445 | B | ACWR (com ressalvas) |
| `EV-IMPELLIZZERI-2020` | Impellizzeri, F. M. et al. What role do chronic workloads play in the acute to chronic workload ratio? Time to dismiss ACWR and its underlying theory. *Sports Med* 50:1567–1580, 2020. doi:10.1007/s40279-020-01288-7 | A | Limitações do ACWR |
| `EV-PLEWS-2013` | Plews, D. J. et al. Training adaptation and heart rate variability in elite endurance athletes. *Sports Med* 43(9):773–781, 2013. doi:10.1007/s40279-013-0071-8 | B | HRV, ln rMSSD, baseline |
| `EV-BUCHHEIT-2014` | Buchheit, M. Monitoring training status with HR measures: do all roads lead to Rome? *Front Physiol* 5:73, 2014. doi:10.3389/fphys.2014.00073 | A | HRV/RHR, interpretação |
| `EV-SAW-2016` | Saw, A. E., Main, L. C., Gastin, P. B. Monitoring the athlete training response: subjective self-reported measures trump commonly used objective measures. *Br J Sports Med* 50(5):281–291, 2016. doi:10.1136/bjsports-2015-094758 | A | Check-in subjetivo |
| `EV-HOOPER-1995` | Hooper, S. L., Mackinnon, L. T. Monitoring overtraining in athletes. *Sports Med* 20(5):321–327, 1995. | D | Hooper index |
| `EV-BOSQUET-2007` | Bosquet, L. et al. Effects of tapering on performance: a meta-analysis. *Med Sci Sports Exerc* 39(8):1358–1365, 2007. doi:10.1249/mss.0b013e31806010e0 | A | Taper |
| `EV-MUJIKA-2003` | Mujika, I., Padilla, S. Scientific bases for precompetition tapering strategies. *Med Sci Sports Exerc* 35(7):1182–1187, 2003. | A | Taper |
| `EV-RIEGEL-1981` | Riegel, P. S. Athletic records and human endurance. *American Scientist* 69(3):285–290, 1981. | C | Predição de prova |
| `EV-ELY-2007` | Ely, M. R. et al. Impact of weather on marathon-running performance. *Med Sci Sports Exerc* 39(3):487–493, 2007. | B | Ajuste por calor |
| `EV-HALSON-2014` | Halson, S. L. Sleep in elite athletes and nutritional interventions to enhance sleep. *Sports Med* 44(Suppl 1):S13–S23, 2014. doi:10.1007/s40279-014-0147-0 | A | Sono e recuperação |
| `EV-WALSH-2021` | Walsh, N. P. et al. Sleep and the athlete: narrative review and 2021 expert consensus recommendations. *Br J Sports Med* 55(7):356–368, 2021. doi:10.1136/bjsports-2020-103708 | A | Sono |
| `EV-WATSON-2015` | Watson, N. F. et al. Recommended amount of sleep for a healthy adult. *Sleep* 38(6):843–844, 2015. doi:10.5665/sleep.4716 | A | 7–9 h |
| `EV-SCHUBERT-2014` | Schubert, A. G., Kempf, J., Heiderscheit, B. C. Influence of stride frequency and length on running mechanics. *Sports Health* 6(3):210–217, 2014. doi:10.1177/1941738113508544 | A | Cadência individual |
| `EV-ISSURIN-2010` | Issurin, V. B. New horizons for the methodology and physiology of training periodization. *Sports Med* 40(3):189–206, 2010. | A | Periodização em blocos |
| `EV-BEATTIE-2014` | Beattie, K. et al. The effect of strength training on performance in endurance athletes. *Sports Med* 44(6):845–865, 2014. | A | Contexto de força (fora do escopo de prescrição) |
| `EV-ACSM-2007` | Sawka, M. N. et al. ACSM position stand: Exercise and fluid replacement. *Med Sci Sports Exerc* 39(2):377–390, 2007. doi:10.1249/00005768-200702000-00016 | A | Individualização da hidratação; risco de hiperhidratação (ADR 0029) |
| `EV-NATA-2017` | McDermott, B. P. et al. NATA position statement: Fluid Replacement for the Physically Active. *J Athl Train* 52(9):877–895, 2017. doi:10.4085/1062-6050-52.9.02 | A | Hidratação individualizada (SOR A); não ganhar massa durante o exercício (ADR 0029) |
| `EV-THOMAS-2016` | Thomas, D. T., Erdman, K. A., Burke, L. M. Nutrition and Athletic Performance. *Med Sci Sports Exerc* 48(3):543–568, 2016. doi:10.1249/MSS.0000000000000852 | A | Sustenta apenas a recusa de prescrição alimentar; nenhuma dica deriva dela (ADR 0029) |

## 14. Catálogo de regras

| ID | Regra | Evidências | Força |
|---|---|---|---|
| `R-CAL-01` | Prova/TT ≤ 8 semanas define VDOT e LTHR (Tier A) | DANIELS, FRIEL | STRONG |
| `R-CAL-02` | Sem prova, ≥ 3 treinos com FC+pace permitem Tier B; senão Tier C. Tier B estima pace de limiar pelo melhor esforço sustentado de 20–35 min (VDOT derivado a 88%; LTHR só na janela 25–35 min), com confiança média e confirmação explícita | DANIELS, FRIEL | LIMITED |
| `R-CAL-03` | Nunca estimar FC máxima por 220−idade | FRIEL, BUCHHEIT | STRONG |
| `R-ZONE-01` | Zonas de FC por LTHR; paces por VDOT | FRIEL, DANIELS | STRONG |
| `R-ZONE-02` | Distribuição medida em 3 zonas; alvo ~80/20 por tempo | SEILER | MODERATE |
| `R-ACT-01` | Comparar metades: ritmo constante/negative split é positivo; queda > 3% é sinal de fadiga | ABBISS, DANIELS | MODERATE |
| `R-ACT-02` | Deriva cardíaca: FC ≥ +5% com pace estável sugere fadiga/calor; não é diagnóstico | FRIEL | LIMITED |
| `R-LOAD-01` | Calcular TRIMP e rTSS separadamente; não somar | BANISTER, COGGAN | STRONG |
| `R-LOAD-02` | CTL 42d / ATL 7d / TSB; exibir tendência, não rótulo | MORTON, COGGAN | MODERATE |
| `R-LOAD-03` | ACWR é flag, nunca meta; fora de 0,8–1,3 bloqueia qualidade/progressão; >1,5 é alerta | GABBETT, IMPELLIZZERI | LIMITED |
| `R-READY-01` | Prontidão combina subjetivo, sono, HRV e RHR com pesos renormalizados | SAW, PLEWS, BUCHHEIT | MODERATE |
| `R-READY-02` | Sem HRV/RHR, confiança cai; ausência não é recuperação ruim | BUCHHEIT, SAW | STRONG |
| `R-READY-03` | Vermelho suspende qualidade; âmbar reduz 10–20% | SAW, HALSON | MODERATE |
| `R-READY-04` | Sono <6 h ⇒ sem qualidade; <6,5 h por 3 dias ⇒ sem aumento de volume | WALSH, WATSON | STRONG |
| `R-READY-05` | RHR +7 bpm por 2 dias ⇒ no mínimo âmbar | BUCHHEIT | MODERATE |
| `R-PLAN-01` | Volume semanal +5–8% só se verde, ACWR 0,8–1,3 e sono ok | SEILER, GABBETT | MODERATE |
| `R-PLAN-02` | Máx. 2 qualidades/semana; nunca consecutivas | SEILER, DANIELS | MODERATE |
| `R-PLAN-03` | Longão 25–30% do volume; iniciante ≤150 min | PFITZINGER, DANIELS | MODERATE |
| `R-PLAN-04` | Deload a cada 4ª semana (−20 a −30%) | ISSURIN | MODERATE |
| `R-PLAN-05` | Taper 2 semanas (5k–meia) / 3 (maratona), volume −40–60% | BOSQUET, MUJIKA | STRONG |
| `R-PLAN-06` | 80% do tempo em Z1–Z2; qualidade doseada | SEILER | MODERATE |
| `R-PLAN-07` | Ajuste por calor +3–8%, subordinado a FC/RPE | ELY | LIMITED |
| `R-PLAN-08` | Esteira válida para qualidade no calor; prescrever por velocidade/inclinação | ELY, DANIELS | LIMITED |
| `R-PLAN-09` | Mudança de inputs de plano (perfil/fuso/objetivo) gera revisão **sugerida** do futuro, nunca automática; proveniência e checkpoint separados | ADR 0031; ADR 0028 | STRONG como regra de produto/arquitetura |
| `R-PLAN-10` | Sem `QUALITY` na semana civil da prova e corte conservador de 10 dias (5K/10K) ou 14 dias (meia/maratona) antes dela; sem ativação | ADR 0030; BOSQUET/MUJIKA só como princípio geral de taper | LIMITED — convenção conservadora de produto, não consenso científico |
| `R-RACE-01` | Corrida importada na mesma data, `RUNNING`/`TREADMILL`, não vinculada, evento `PLANNED` e distância entre 90% e 110% conclui a prova; múltiplas candidatas exigem escolha e o undo é auditável | ADR 0032; convenção de produto | LIMITED — não é alegação fisiológica |
| `R-RACE-02` | Pace médio, faixa ±3% e passagens por km derivados aritmeticamente de `targetTimeSec`; sem meta, nada é inventado | ADR 0032; aritmética declarada | LIMITED — referência, não prescrição |
| `R-FEEDBACK-01` | Comparação planejado × executado é descritiva: usa o alvo prescrito do dia, tolera 10% em duração/distância e 1 ponto de RPE, mostra dado ausente como ausente e não julga qualidade nem prescreve; treino livre não inventa alvo | ADR 0033; DANIELS, FRIEL | STRONG como política de produto/copy |
| `R-REVIEW-01` | A revisão semanal consolida adesão, volume, intensidade, prontidão e sono a partir de valores já calculados; reporta no máximo 3 observações determinísticas que citam regra/evidência, com dado ausente como ausente, sem LLM, sem nota, streak ou prescrição | ADR 0034; política de produto | STRONG como política de produto/copy |
| `R-SAFE-01` | Sinais de alerta interrompem o treino e recomendam avaliação | Consenso clínico | STRONG |
| `R-SAFE-02` | Sem diagnóstico; app não é dispositivo médico | Regulatório | STRONG |
| `R-SAFE-03` | Sem prescrição de peso/nutrição; única exceção é a copy educativa de hidratação do ADR 0029 (sem números) | Escopo; ADR 0029 | STRONG |
| `R-EXPL-01` | Toda recomendação cita regra + evidência + confiança | Produto | STRONG |
| `R-DATA-01` | Dado ausente não preenche valor neutro silencioso; cobertura/frescor de carga são declaradas | Produto | STRONG |
| `R-PRESC-01` | A prescrição do dia parte da sessão planejada e a mantém quando os guardrails permitem | ADR 0023; R-PLAN/R-READY | MODERATE |
| `R-PRESC-02` | Ajuste do dia só reduz (duração/tipo/intensidade); nunca aumenta | ADR 0023; R-READY/R-DATA | STRONG |
| `R-PRESC-03` | Uma decisão por dia, versionada e a mesma em todas as telas | ADR 0023; Produto | STRONG |
| `R-PRESC-04` | Iniciante com sessão de hoje `PLANNED` em `QUALITY` recebe `DOWNGRADE` para `EASY`; `DONE`/`CLOSED` preservam prescrição e histórico | D-6 v0.1; ADR 0023 | LIMITED — política conservadora de produto, sem alegação fisiológica específica |
| `R-FUEL-01` | Dicas de hidratação são conteúdo educativo fixo aprovado por fonte; não derivam de prescrição nem alteram decisão do motor | ADR 0029; ACSM, NATA | STRONG como política de produto/copy |
| `R-FUEL-02` | Sem recomendação de quantidade, eletrólito, suplemento ou horário sem contexto individual aprovado | ADR 0029; ACSM 2007; NATA 2017 (SOR A) | STRONG |

## 15. Limitações conhecidas

- Sem teste de lactato, LTHR é estimada; confiança máxima "média" até um TT válido.
- Sem running dynamics, economia de corrida é inferida por pace/FC/cadência; o gráfico de m/batimento é um proxy e só deve ser comparado sob condições semelhantes.
- Potência de corrida do Zepp é proprietária; usar apenas tendência relativa.
- Ajuste de calor é empírico e conservador; não é prescrição fisiológica individual.
- HRV noturno de wearable de pulso tem ruído; usar tendência, não noite isolada.
- ACWR tem limitações teóricas; nunca é a única justificativa.
- O modelo de prontidão não substitui sintomas subjetivos ou avaliação médica.

## 16. Próximas calibrações

1. Comparar o motor com o export diário real quando disponível.
2. Validar TRIMP/rTSS contra implementações Python/R com os 4 FIT + 3 TCX.
3. Recalibrar paces após o TT de 30 min.
4. Ajustar o modelo de calor com dados locais de Natal (temperatura, umidade, pace/FC).
5. Revisar pesos de prontidão após 8 semanas de dados do usuário 0.
