
# Visão de Produto — Integração de IA ao Coach de Corrida

**Status:** Proposta conceitual
**Tipo:** Visão de produto e direcionamento funcional
**Objetivo:** Definir a visão de evolução do aplicativo por meio da integração de inteligência artificial ao planejamento semanal de corrida.

---

# 1. Contextualização

O aplicativo já possui uma estrutura de treinamento baseada em informações reais do corredor, utilizando dados importados de dispositivos esportivos, arquivos FIT e TCX, além de informações obtidas por meio da integração com o Google Health Connect.

A partir desses dados, o aplicativo consegue conhecer melhor o atleta, acompanhar sua evolução e gerar planejamentos semanais personalizados.

Atualmente, o planejamento considera informações como:

- Disponibilidade semanal.
- Quantidade de dias disponíveis para correr.
- Duração possível dos treinos.
- Nível de experiência.
- Histórico de atividades.
- Indicadores fisiológicos e de recuperação disponíveis.
- Objetivos individuais do corredor.

A proposta agora é evoluir essa experiência por meio da integração de inteligência artificial, dando ao aplicativo uma característica mais próxima de um treinador pessoal que acompanha, interpreta e contextualiza a jornada do corredor.

O objetivo não é simplesmente adicionar um chatbot ao aplicativo, mas utilizar inteligência artificial para agregar valor ao planejamento de treinamento que já existe.

# 2. Visão central da proposta

A principal ideia é transformar o planejamento semanal em uma experiência inteligente e interativa.

Atualmente, o aplicativo gera uma programação de treinos de acordo com os dados e as regras existentes.

Com a introdução da inteligência artificial, o corredor deverá passar a:

- Compreender melhor o propósito de cada semana.
- Receber análises sobre sua evolução.
- Conversar sobre seu planejamento.
- Solicitar ajustes quando suas circunstâncias mudarem.
- Entender as razões por trás das recomendações.
- Receber acompanhamento contextualizado durante sua jornada.

A inteligência artificial deverá atuar como uma camada complementar ao motor científico existente.

O motor científico continuará responsável pelos cálculos, critérios de progressão, distribuição de intensidade, carga de treinamento e validação das recomendações.

A IA será responsável por interpretar o contexto, explicar decisões, identificar situações que merecem atenção, interagir com o corredor e intermediar solicitações de alterações no planejamento.

Essa separação é fundamental para preservar a confiabilidade científica do aplicativo.

# 3. Funcionalidades propostas

## 3.1. Coach inteligente: explicação e contextualização do planejamento semanal

Uma das principais funcionalidades desejadas é fazer com que o treinador explique ao corredor a estratégia por trás do planejamento semanal.

Em vez de simplesmente apresentar uma lista de treinos, o aplicativo deverá apresentar uma contextualização sobre os objetivos daquela semana.

A IA deverá analisar o planejamento gerado pelo motor científico e produzir uma explicação compreensível, considerando o histórico, o nível de experiência, os objetivos e a evolução do corredor.

**Exemplo:**

"Nesta semana, nosso objetivo será desenvolver sua resistência aeróbica. Vamos manter uma sessão de intensidade controlada e trabalhar gradualmente o volume da corrida mais longa. Essa distribuição busca proporcionar estímulos adequados sem concentrar excessivamente o esforço em dias consecutivos."

A explicação deverá ser individualizada, evitando mensagens genéricas e repetitivas.

O corredor deverá compreender:

- Qual é o principal objetivo da semana.
- Quais capacidades físicas estão sendo trabalhadas.
- Por que determinados treinos foram selecionados.
- Qual é a lógica da distribuição dos treinos.
- Como aquela semana se relaciona com a evolução e os objetivos do atleta.

## 3.2. Revisão inteligente da semana anterior

Outra funcionalidade importante será a realização de uma retrospectiva inteligente antes da apresentação ou início de um novo planejamento semanal.

A IA deverá analisar o que estava planejado e comparar com aquilo que realmente foi executado.

Entre as informações que poderão ser consideradas estão:

- Quantidade de treinos planejados.
- Quantidade de treinos efetivamente realizados.
- Treinos não realizados.
- Diferenças entre volume planejado e executado.
- Evolução de pace e frequência cardíaca.
- Distribuição de intensidade.
- Carga de treinamento.
- Indicadores de recuperação disponíveis.
- Percepção subjetiva de esforço, quando informada pelo corredor.

A partir dessas informações, o treinador deverá produzir uma análise contextualizada da semana.

**Exemplo:**

"Na semana passada, você completou três dos quatro treinos previstos. O treino longo foi realizado com bom controle de intensidade, mas a sessão de qualidade não foi executada. Vamos considerar esse histórico para definir a próxima semana, mantendo uma progressão coerente e evitando tentar compensar automaticamente o treino perdido."

A retrospectiva deverá destacar conquistas, identificar pontos de atenção e fornecer informações relevantes para a construção da próxima semana.

É importante que a IA não interprete automaticamente um treino não realizado como falta de comprometimento. Podem existir questões de disponibilidade, cansaço, desconforto ou outros fatores que precisam ser compreendidos.

## 3.3. Replanejamento inteligente diante de imprevistos

Esta é uma das funcionalidades de maior potencial dentro da proposta.

O corredor deverá ter a possibilidade de conversar com seu treinador e solicitar alterações no planejamento quando surgir algum imprevisto.

**Exemplo:**

O corredor possui quatro sessões programadas para determinada semana, mas não conseguiu realizar o treino de terça-feira.

Ele poderá perguntar:

"Não consegui fazer o treino de ontem. Como podemos reorganizar minha semana?"

A IA deverá compreender a solicitação, consultar o planejamento atual, analisar os próximos treinos e solicitar ao motor científico uma alternativa de reorganização.

A nova distribuição deverá considerar:

- Treinos já realizados.
- Treinos pendentes.
- Intensidade das sessões.
- Intervalos de recuperação.
- Volume semanal planejado e executado.
- Disponibilidade restante.
- Objetivos da semana.
- Indicadores relevantes de recuperação.

O objetivo não é simplesmente transferir um treino perdido para o dia seguinte.

O treinador deverá compreender que a reorganização precisa preservar a coerência e a segurança do planejamento.

A IA deverá explicar quais alterações foram realizadas e os motivos da nova distribuição.

## 3.4. Alteração antecipada da disponibilidade semanal

Além de reagir aos imprevistos, o aplicativo deverá permitir que o corredor informe antecipadamente mudanças na sua disponibilidade.

**Exemplo:**

O corredor sabe que, na próxima semana, não poderá correr na sexta-feira.

Ele poderá conversar com o treinador:

"Na semana que vem não vou conseguir correr na sexta-feira. Podemos reorganizar meus treinos?"

A IA deverá interpretar essa informação e solicitar uma nova simulação do planejamento, considerando a restrição informada.

Outros exemplos:

- "Na próxima semana só conseguirei correr três dias."
- "Na quinta-feira terei apenas 30 minutos disponíveis."
- "Neste sábado não poderei fazer minha corrida longa."
- "Quero alterar meus dias disponíveis para correr."

O aplicativo deverá permitir que essas informações sejam consideradas na geração ou atualização do planejamento semanal.

É importante diferenciar uma indisponibilidade temporária de uma mudança permanente na rotina do corredor.

Uma restrição informada para uma semana não deverá modificar automaticamente sua disponibilidade habitual.

A nova programação deverá passar pelas validações do motor científico antes de ser apresentada como planejamento válido.

## 3.5. Check-in inteligente do corredor

Outra possibilidade é introduzir uma interação periódica entre o treinador e o corredor.

Antes do início de uma nova semana, o treinador poderá realizar algumas perguntas para compreender melhor o estado atual do atleta.

Exemplos:

- Como você se sentiu durante os últimos treinos?
- Como está sua disposição para a próxima semana?
- Está sentindo algum desconforto físico?
- Sua disponibilidade para correr continua a mesma?
- Como está sua recuperação?

Essas perguntas não precisam ser sempre iguais.

A inteligência artificial poderá selecionar perguntas relevantes de acordo com o histórico e as informações disponíveis.

Por exemplo, se os indicadores apresentarem uma tendência desfavorável, o treinador poderá investigar como o corredor está se sentindo.

Se a semana anterior tiver transcorrido normalmente, o check-in poderá ser mais simples.

As respostas deverão complementar os dados objetivos do aplicativo, sem substituir as métricas obtidas dos dispositivos esportivos.

## 3.6. Acompanhamento da aderência ao planejamento

O treinador também poderá acompanhar a execução do planejamento ao longo da semana.

Em vez de apenas apresentar uma porcentagem de conclusão, poderá identificar situações que merecem atenção e conversar com o corredor.

**Exemplo:**

"Percebi que conseguimos realizar apenas um dos dois primeiros treinos desta semana. Está acontecendo alguma dificuldade com os horários ou com a recuperação?"

Dependendo da resposta, o treinador poderá identificar a necessidade de:

- Reorganizar a distribuição dos treinos.
- Rever a disponibilidade semanal.
- Investigar dificuldades relacionadas à recuperação.
- Manter o planejamento atual.
- Solicitar uma nova avaliação do motor científico.

A proposta é fazer com que o acompanhamento seja mais próximo e individualizado.

## 3.7. Justificativa e transparência das alterações

Sempre que houver uma alteração no planejamento, o treinador deverá explicar claramente o que mudou e por quê.

O corredor deverá conseguir compreender:

- Quais treinos foram alterados.
- Quais treinos foram mantidos.
- Por que determinada alteração foi necessária.
- Quais informações motivaram a decisão.
- Como a nova distribuição atende aos objetivos do treinamento.

A transparência é fundamental para construir confiança na inteligência do treinador.

# 4. O conceito de memória esportiva do corredor

Uma evolução interessante será desenvolver uma memória esportiva estruturada.

A ideia é permitir que o treinador tenha conhecimento da trajetória do corredor ao longo do tempo.

Não se trata apenas de armazenar conversas, mas de acompanhar informações relevantes sobre a evolução esportiva.

Exemplos:

- Evolução do pace em períodos comparáveis.
- Mudanças na frequência cardíaca durante esforços semelhantes.
- Evolução do volume semanal.
- Histórico de objetivos alcançados.
- Preferências e disponibilidade.
- Dificuldades recorrentes.
- Percepção de esforço em determinados tipos de treino.

Com essas informações, a IA poderá produzir observações mais contextualizadas.

**Exemplo:**

"Nas últimas semanas, observamos uma evolução favorável. Em treinos de características semelhantes, você conseguiu manter ritmos próximos com menor percepção de esforço. Vamos continuar acompanhando essa tendência."

Essa memória deverá ser baseada em informações estruturadas e verificáveis, e não depender exclusivamente da capacidade de lembrança do modelo de linguagem.

# 5. Iniciativa do treinador

Uma característica importante do produto será permitir que o treinador tenha iniciativa.

O corredor não deverá precisar fazer todas as perguntas.

O aplicativo poderá identificar situações relevantes e apresentar interações contextualizadas.

Exemplos:

- Quando um treino importante não for realizado, perguntar se houve alguma dificuldade.
- Quando uma meta for alcançada, reconhecer a conquista.
- Quando houver uma mudança relevante na evolução, apresentar uma observação.
- Quando uma tendência desfavorável de recuperação for identificada, recomendar atenção.
- Ao finalizar a semana, apresentar uma retrospectiva.
- Ao iniciar uma nova semana, explicar a estratégia do planejamento.

Essa iniciativa deverá ser utilizada com moderação, evitando notificações desnecessárias e mensagens repetitivas.

# 6. Princípios que devem orientar a implementação

A introdução da inteligência artificial deverá respeitar alguns princípios fundamentais.

### 6.1. Preservação do motor científico

O algoritmo existente continua sendo a referência para cálculos, progressões e validações do treinamento.

A IA não deverá inventar métricas, calcular indicadores sem dados suficientes ou substituir arbitrariamente as regras científicas existentes.

### 6.2. Decisões verificáveis

Toda recomendação deverá utilizar informações reais disponibilizadas pelo aplicativo.

Quando não houver dados suficientes, o treinador deverá reconhecer essa limitação.

### 6.3. Replanejamento controlado

A IA poderá interpretar solicitações e propor alterações, mas o planejamento revisado deverá passar pelo motor científico antes de ser aplicado.

### 6.4. Transparência

O corredor deverá compreender as razões das recomendações e das alterações realizadas.

### 6.5. Personalização

As interações deverão considerar o nível, os objetivos, o histórico, as preferências e a evolução individual do corredor.

### 6.6. Segurança

Informações sobre desconfortos físicos, recuperação e indicadores fisiológicos deverão ser tratadas com responsabilidade.

A IA não deverá diagnosticar doenças nem substituir avaliações profissionais.

### 6.7. Privacidade

O envio de informações para provedores externos de IA deverá respeitar os princípios de minimização de dados, proteção de informações pessoais e consentimento adequado.

# 7. Direcionamento de produto

A proposta é evoluir o aplicativo para que o planejamento semanal deixe de ser apenas uma programação de atividades e passe a funcionar como o centro de uma experiência de acompanhamento esportivo inteligente.

O corredor deverá conseguir compreender sua semana, avaliar sua evolução, conversar com seu treinador e solicitar adaptações de acordo com sua realidade.

O treinador deverá conhecer o histórico, compreender o contexto e explicar suas recomendações.

A inteligência artificial não deverá substituir o trabalho do motor científico, mas ampliar a capacidade do aplicativo de transformar dados esportivos em orientações compreensíveis e experiências personalizadas.

# 8. Proposta de evolução

As funcionalidades poderão ser desenvolvidas progressivamente, respeitando a arquitetura atual do aplicativo.

Uma sequência inicial de evolução seria:

1. Coach inteligente para explicar e contextualizar o planejamento semanal.
2. Revisão inteligente da semana anterior, comparando planejamento e execução.
3. Replanejamento inteligente diante de treinos não realizados e imprevistos.
4. Alteração antecipada da disponibilidade semanal por meio de conversação.
5. Check-in inteligente antes do início de uma nova semana.
6. Acompanhamento da aderência ao planejamento.
7. Memória esportiva e acompanhamento longitudinal.
8. Iniciativa do treinador para apresentar observações e interações relevantes.

Essa ordem representa uma proposta inicial de priorização, não uma decisão definitiva de implementação.

# 9. Objetivo desta iniciativa

Este documento apresenta uma visão de produto para a integração de inteligência artificial ao planejamento semanal do aplicativo.

A intenção é utilizar essa visão como ponto de partida para uma análise aprofundada do projeto existente.

A próxima etapa deverá envolver a avaliação da arquitetura atual, do motor científico, da geração dos planejamentos, da persistência dos dados e dos fluxos de execução dos treinos.

A partir dessa análise, deverá ser possível identificar quais funcionalidades já possuem suporte na estrutura atual, quais exigem evolução e quais precisam ser desenvolvidas do zero.

O resultado esperado é construir um treinador de corrida verdadeiramente inteligente, que não apenas prescreva treinos, mas acompanhe a trajetória do corredor, compreenda suas necessidades e participe ativamente da evolução esportiva de cada usuário.

# 10. Infraestrutura

teremos acesso ao endpoint da openrouter com acesso aos modelos grátis deles. deixarei alguns modelos que poderemos usar (nvidia/nemotron-3-ultra-550b-a55b:free, nvidia/nemotron-3.5-super-vl:free, thinkingmachines/inkling-2-small:free), além, é claro, de poder baixar algum modelo e usar local, isso tem que ser estudado. teremos que passar por cima de uma trava na doc do projeto que é a de não enviar dados, agora será necessário.

# 11. Observações importantes

Não leve as informações contidas nesse documento como a fonte da verdade absoluta, o modelo llm que contruiu esse documento não tinha acesso ao projeto e não sabe exatamente como foi feito esse app. As informações contidas aqui vieram de um briefing feito comigo, idealizador do projeto, então, a fonte da verdade absoluta é o código, o que você irá analisar.
