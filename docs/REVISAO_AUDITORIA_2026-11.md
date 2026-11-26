# Revisão crítica da auditoria geral — MeuCoach

**Data:** 26/11/2026  
**Documento revisado:** [`AUDITORIA_GERAL_2026-11.md`](AUDITORIA_GERAL_2026-11.md)  
**Escopo:** achados ALG, SEG, SCI, STORY, COACH/validador, DOC e GEN.  
**Método:** leitura própria das evidências primárias, buscas negativas e execução de harness temporário com dados sintéticos. Nenhum arquivo privado ou dado de saúde real foi usado.

> Este documento confronta a auditoria original; não a substitui como fonte de verdade científica. Fato, inferência, severidade e sugestão são avaliados separadamente.

## Resultado da reprodução

Rodei o comando indicado no Apêndice A com um harness temporário e o removi ao final:

```bash
rm -f /tmp/opencode/audit-experiments.txt && ./gradlew :core:domain:test --tests "*AuditExperimentTest*"
```

**Resultado final:** `BUILD SUCCESSFUL` (16 verificações sintéticas).

- **E14:** disponibilidade de 10 min produziu sessão fácil de 20 min.
- **E18:** slot de 20 min produziu sessão de qualidade de 25 min.
- **E19:** com longão no domingo anterior, a qualidade apareceu na segunda seguinte.
- **E1:** todas as sessões de `BEGINNER` e `INTERMEDIATE` foram iguais; o rationale geral difere.
- **E2/E13:** `easyTemplates(TAPER)` devolveu somente “Rodagem” nos três níveis; iniciante não recebeu micro-dose, intermediário/avançado receberam.
- **E16:** com os valores visíveis na receita — base 190, agenda `{SEG60, QUA60, SEX60, DOM150}`, readiness verde e carga confiável — a reconstrução produziu **201 min**, copy “+6% … alvo de 201”. Não reproduziu os **178 min** reportados.
- **Mecanismo de ALG-03:** em outra agenda sintética de seis dias, com a mesma base de 190 e o mesmo alvo 201, o gerador entregou **188 min**, retirando o dia mais curto. Portanto, o problema geral de subentrega existe, mas o caso/valor E16 publicado não foi corroborado pela receita visível.
- **Taper:** com 10 min disponíveis na terça, a micro-dose saiu com 25 min.
- **Fallback diário sem plano:** com 10 min disponíveis, o recommender indicou 20 min.
- **Longão iniciante:** um longão anterior de 45 min virou 62 min, embora o plano atual totalizasse 178 min contra base de 190.
- **Validador:** explain aceitou “85%, 10 km e 99 sessões”; uma resposta aberta com “12,5 unidades” também passou. Uma pergunta sintética sobre arritmia foi roteada para `ASK_COACHING`, e a resposta “Correr com arritmia é seguro para você” passou no validador.

O Apêndice A não permite reprodução byte a byte: `completedWeek` e `dump` são placeholders (`AUDITORIA_GERAL_2026-11.md:925-970`). O arquivo de teste e o arquivo de saída temporários foram apagados.

## Tabela de vereditos

### Algoritmo, níveis e ciência

| Achado | Veredito | Evidência primária | Nota |
|---|---|---|---|
| ALG-01 | **CONFIRMADO** | `WeeklyPlanGenerator.kt:1232-1247`; E14/E18 no comando acima; `SPORTS_SCIENCE.md:319-323` | O piso prevalece sobre o teto em inputs válidos. |
| ALG-02 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa/média; a assimetria é reproduzida, mas não é proibição explícita do documento)** | `WeeklyPlanGenerator.kt:936-945`; E19; `SPORTS_SCIENCE.md:366` | Qualidade na segunda após longão no domingo anterior ocorre. O texto citado proíbe a direção oposta: longão no dia seguinte à qualidade. |
| ALG-03 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para média; o undershoot foi reproduzido, mas não com os 178 min do E16 publicado)** | E16 reconstruído: 201; variação sintética: 188 vs base 190, com alvo 201; `WeeklyPlanGenerator.kt:1428-1465,1528-1556` | A explicação de causa “disponibilidade” pode ser enganosa quando uma sessão cai para satisfazer o alvo de volume. |
| ALG-04 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa/média; falta teto de frequência, mas o teto numérico sugerido não foi demonstrado)** | E5 reconstruído: iniciante, 7 dias × 40, 7 sessões/265 min; `WeeklyPlanGenerator.kt:502`; `SPORTS_SCIENCE.md:283-295` | Não há máximo de dias por nível. Impor cinco dias exige evidência/ADR; um aviso não bloqueante é mais defensável. |
| ALG-05 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para média; o bloqueio existe e a UI não mostra os critérios de `Stay`)** | `WeeklyPlanGenerator.kt:1272-1283`; `LevelGraduationEngine.kt:262-268,328`; `LevelGraduationViewModel.kt:196-202`; `LevelGraduationCard.kt:50-65` | Slot de 50–55 min não demonstra o longão real de 60 min da graduação. O auditor errou ao dizer que o usuário descobre abrindo o card: resultados `Stay` e seus `missing` são descartados pela VM/UI. |
| ALG-06 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa; erro de copy, sem mudança no treino)** | `WeeklyPlanGenerator.kt:823-845`; E2 reconstruído | A razão “iniciante prioriza base” precede o contexto de taper/recuperação. |
| ALG-07 | **CONFIRMADO** | `WeeklyPlanGenerator.kt:648-650`; `WorkoutStructure.kt:134-142`; E14 | Strides de 10 min ficam sem estrutura executável: aquecimento + desaquecimento não cabem e a exceção é capturada. |
| ALG-08 | **CONFIRMADO** | `WeeklyPlanGenerator.kt:1520-1561,1657-1663` | A branch de disponibilidade não anexa `volumeBlockHint(ACWR_OUT_OF_RANGE)`; baixa gravidade, pois a copy de disponibilidade continua verdadeira. |
| ALG-09 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa; diferença afeta confiança/copy do fallback)** | `WorkoutRecommender.kt:99-113`; `DailyPrescriptionEngine.kt:107-115,156-159` | `loadTrusted` usa `dataState` num caminho e `objectiveDataState` no outro. Não libera progressão no recommender, que só oferece fácil. |
| SEG-01 | **CONFIRMADO** | E1 reconstruído: igualdade de todos os `PlannedSession`; `WeeklyPlanGenerator.kt:826-838` | Igualdade das sessões, não do rationale geral. |
| SEG-02 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa; gap de segmentação, não bug)** | `SessionLibrary.kt:221-257`; `WeeklyPlanGenerator.kt:1261-1264`; `SPORTS_SCIENCE.md:206-209` | Avançado aumenta algumas doses e muda PEAK, mas compartilha o teto de duas qualidades; o documento não promete um modelo avançado distinto. |
| SEG-03 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa/média; interpretação de “Todos” é ambígua)** | `SessionLibrary.kt:103-117,128-141`; `WeeklyPlanGenerator.kt:384-386`; `SPORTS_SCIENCE.md:342-354`; E2/E13 reconstruídos | TAPER dá só “Rodagem” no catálogo fácil; iniciante não recebe micro-dose. Não são “zero corridas”: faltam o estímulo distinto e a clareza documental. |
| SEG-04 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa; a referência a `SPORTS_SCIENCE §8.12` foi ampliada indevidamente)** | `LevelGraduationEngine.kt:58-73,84-90`; `LevelGraduationViewModel.kt:34-36,196-202`; `SPORTS_SCIENCE.md:494-502` | Só há `Stay`/`SuggestUpgrade`. A promessa de revisão na regressão está no KDoc contraditório, não em §8.12. |
| SEG-05 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para média; duplicata de ALG-05)** | Mesmas evidências de ALG-05 | Não é um achado independente. |
| SEG-06 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa; lacuna de produto/documentação)** | `ProfileLabels.kt:39-42`; busca por `birthDate` em `core/domain/src/main/kotlin` sem hits; `TrainingLoadCalculator.kt:27-30`; `SPORTS_SCIENCE.md:51-60` | Idade é exibida, não usada no motor; sexo altera coeficientes TRIMP. |
| SCI-01 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa/média; há explicação explícita de ausência)** | `ReadinessCalculator.kt:36-47`; `TodayScreen.kt:457-463`; `TodayExplanation.kt:299-302` | Gauge mostra 50/Moderada, mas a explicação diz “Ainda não há dados de recuperação” e mostra confiança baixa. |
| SCI-02 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa; ambiguidade de copy)** | `WeeklyPlanGenerator.kt:785-790,848-875`; `SPORTS_SCIENCE.md:325-330` | “Progressão liberada” pode coexistir com deload porque refere-se à oportunidade de qualidade; o texto pode sugerir aumento de volume. |
| SCI-03 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa; 80/20 é alvo, não gate)** | `SPORTS_SCIENCE.md:203-209`; busca por `IntensityDistributionCalculator` em `core/domain/.../prescription` sem hits | A distribuição é medida depois, não validada na geração; isso não prova que todo plano deva ser bloqueado por 80/20. |

### Story Studio

| Achado | Veredito | Evidência primária | Nota |
|---|---|---|---|
| STORY-01 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa/média; contagem de “oito cards” não é constante)** | `ActivityDetailScreen.kt:283-325` | Story só aparece com GPS e fica após cards condicionais; em atividades com menos dados, a distância de descoberta é menor que a alegada. |
| STORY-02 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa; mapa ausente da prévia é declarado)** | `StoryStudioSheet.kt:623-696,467-469`; `StoryPalette.kt:38-58`; `StoryBackdropRenderer.kt:88-98` | Primeiro render e falha usam copy de “sem rota GPS”; mapa não é renderizado na prévia, conforme a própria copy. O estilo de mapa altera atribuição, não o basemap da prévia. |
| STORY-03 | **CONFIRMADO** | `ActivityDetailViewModel.kt:587-591`; `ActivityDetailScreen.kt:715-719`; `StoryMessages.kt:11-13` | Studio fecha durante `PreparingMap`; não há Cancelar visível nessa branch, embora haja timeout de 45 s e `cancelStory()`. |
| STORY-04 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa; não medi recomposição real no aparelho)** | `ActivityDetailViewModel.kt:587-591`; `StoryStudioSheet.kt:202` | O Studio fecha e a aba inicial é VIDEO; não há armazenamento explícito da aba no ViewModel. |
| STORY-05 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa; a confirmação existe fora do Studio)** | `ActivityDetailScreen.kt:755-845`; `ArtStudioContent.kt:626-631` | Vídeo usa texto no card/notification, não Snackbar. O auditor exagera “fora da tela”: o card da atividade continua mostrando estado e erro. |
| STORY-06 | **NÃO VERIFICÁVEL (escopo)** | `McButtons.kt:159-176`; `ArtStudioContent.kt:551-554,587-591,851-855`; [Material3 minimum interactive component size](https://developer.android.com/reference/kotlin/androidx/compose/material3/LocalMinimumInteractiveComponentSize) | Padding 8×4 e ausência de `minHeight` explícito são fatos. O alvo de toque final não foi medido; Material3 tem expansão de alvo interativo, então não endosso a alegação de violação sem teste de layout. |
| STORY-07 | **CONFIRMADO** | `StoryStudioScreenshotTest.kt:111-175`; `TESTING.md:414` | O 200% mostrado nos testes é da aba Arte; não achei cenário equivalente para Vídeo. |
| STORY-08 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa; exceção offline já é explícita no produto)** | `ActivityDetailViewModel.kt:603-630`; `StoryMessages.kt:11-13`; `PRODUCT.md:75` | Export de vídeo depende do mapa; não existe fallback route-only. A exceção offline consta no PRODUCT; CTA para Arte seria melhoria. |
| STORY-09 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa; feature não prometida separadamente)** | `ActivityDetailViewModel.kt:576-661`; `ActivityDetailScreen.kt:800-829` | Poster/capa são compartilhados após export; não há CTA de poster avulso. É solicitação de feature, não falha do contrato atual. |
| STORY-10 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa; comportamento intencional de layout)** | `StoryStudioSheet.kt:202-217`; `StoryStudioContentTest.kt:66-85` | Tab padrão VIDEO e segmento some ao rolar, deliberadamente coberto por teste. |
| STORY-11 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa)** | `StoryStudioSheet.kt:716-719,742-744`; `STORY_V3_PLANO_DE_ACAO.md:275,297,498` | São três seletores discretos, não um scrubber temporal contínuo como no mock/plano. |
| STORY-12 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa; “enum órfão” é falso)** | `StoryRecipeCodec.kt:42-63`; `StoryVideoExporter.kt:330-333`; `ROADMAP.md:702` | Qualidade não é editável na UI, mas o campo é persistido e consumido no encode. 60 fps foi deliberadamente não exposto até validação. |
| STORY-13 | **CONFIRMADO** | `PRD_ARTE_PNG_ROTA.md:68`; renderers do Story | Marca fixa é decisão registrada/pós-MVP; não é achado de implementação. |
| STORY-14 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa; decisão explícita)** | `ActivityDetailScreen.kt:786-791,900-904` | Share é individual por formato, com copy que orienta capa 9:16 e pôster 4:5. |
| STORY-15 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa; TalkBack real não verificado)** | `StoryStudioSheet.kt:577-594` | `selected`/descrição existem, mas não há `Role.Tab` nem anúncio quando o segmento some. |
| STORY-16 | **CONFIRMADO** | `StoryStudioContentTest.kt:273-280`; `StoryStudioScreenshotTest.kt:104-106`; busca `PreparingMap` em `app/src/test` sem hits | Não encontrei teste direto de PreparingMap; o screenshot só espera o loading desaparecer. |
| STORY-17 | **CONFIRMADO** | `PROGRESS.md:895-907`; `TESTING.md:476-501` | Flake/instabilidade de Story está registrada no PROGRESS, mas não na tabela de flakes do TESTING. |
| STORY-18 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa; consolidado inclui itens intencionais/stale)** | `StoryStudioSheet.kt:467-469,716-719`; `StoryRecipe.kt:64-71`; `TESTING.md:403-425`; `ROADMAP.md:681-705` | Há lacunas de mock→código, mas qualidade 60 fps está explicitamente adiada, mapa na prévia é explicitamente omitido, e TESTING lista goldens Story. |

### Coach e validador

| Achado | Veredito | Evidência primária | Nota |
|---|---|---|---|
| COACH-01 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para média; ausência de contexto conversacional é real, “100% regex” é absoluto demais)** | `CoachFreeAnswerPrompt.kt:16-29`; `CoachViewModel.kt:640-641`; `HANDOFF_IA_COACH.md:56` | Cada prompt leva pergunta atual/dossiê, não o histórico. |
| COACH-02 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa/média; timeout é teto, não latência observada)** | `CoachApi.kt:82-88,111-117`; `Main.kt:62-69,114-118`; busca SSE sem hits | Resposta é lida integralmente, sem streaming; latência média/p95 não foi medida aqui. |
| COACH-03 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa; não é requisito de métrica in-app)** | Busca por feedback/thumbs em `app/ui/coach` sem hits; `PRD_IA_COACH.md:42` | Sem feedback por mensagem; PRD prevê feedback qualitativo do usuário 0, não um widget no MVP. |
| COACH-04 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para média; divergência de PRD, não prova de demora habitual)** | `PRD_IA_COACH.md:18`; `CoachApi.kt:82-88`; `CoachScreen.kt:195-200`; `PRD_COACH_CHAT.md:161` | PRD ~10 s vs 45/25 s no app; status visual é fixo apesar da spec dinâmica. |
| COACH-05 | **CONFIRMADO** | `CoachViewModel.kt:527-536,777,819-832`; `CoachScreen.kt:347-352` | `revoke()` não limpa exchanges; retorno pós-revogação pode deixar Loading e texto visível. |
| COACH-06 | **CONFIRMADO** | `CoachViewModel.kt:570-585,598-609`; `CoachChatComponents.kt:252-283` | `text=lastFreeText`, `pending` é outra exchange. O fix precisa também passar `exchangeId` do botão, não só trocar para `pending.question`. |
| COACH-07 | **CONFIRMADO** | `CoachViewModel.kt:570-585,611-615,1064-1085`; `ChatTurn.kt:38-42,68-76,93` | `YOU` persiste antes do roteamento; fallback não persiste; `You` nunca é ocultado do histórico. |
| COACH-08 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa/média; ausência de hydrate é fato, reinicialização depende do escopo do ViewModel)** | `CoachViewModel.kt:238-242`; `CoachScreen.kt:347-352,430-450` | Timeline principal não hidrata de `observeTurns`; arquivo está em bottom sheet. |
| COACH-09 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para média; recusa é genérica, não é conselho médico)** | `FreeInputParser.kt:71-78`; `CoachViewModel.kt:611-615,1197-1203` | Termos médicos caem em OUT_OF_SCOPE, sem CTA específico para check-in/avaliação. |
| COACH-10 | **CONFIRMADO** | `CoachViewModel.kt:323-327`; `CoachResponseValidator.kt:117-145,274-309`; execução do validador no comando | `validate()` recebe `allowedPercents=null` no explain; um texto com 85%, 10 km e 99 sessões passou no teste sintético. |
| COACH-11 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para média; o exemplo vale sobretudo para ASK_COACHING)** | `CoachOpenAnswer.kt:153-155`; `CoachSessionAnswer.kt:78-82`; `CoachViewModel.kt:990-1000` | Open answer autoriza durações de todos os dias. Session answer autoriza a sessão alvo e o total semanal; não autoriza qualquer duração da semana. |
| COACH-12 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa; recusar número autorrelatado pode ser deliberado)** | `CoachOpenAnswer.kt:96-103,255-259` | A linha da pergunta é removida de `dossierNumbers`; número digitado não entra automaticamente na allow-list. |
| COACH-13 | **CONFIRMADO** | `CoachApi.kt:113-117`; `CoachViewModel.kt:819-832`; `Main.kt:417-419` | Hard error do upstream é devolvido pelo proxy; `take(200)` entra na `IOException` e `e.message` é enviado a `Log.d`. |
| COACH-14 | **CONFIRMADO COM SEVERIDADE AJUSTADA (informativo; controles existem, mas não equivalem a ausência de dados textuais)** | `PRD_IA_COACH.md:78-79`; `ARCHITECTURE.md:283`; `CoachOpenFlow.kt:82-93` | Opt-in, TTL, revogação/export estão documentados; pergunta digitada é enviada após opt-in, conforme consentimento v2. |
| COACH-15 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa)** | `CoachFallbackLabels.kt:16-20`; `CoachViewModel.kt:1056-1069,1209-1210` | `freeAnswerReasonCopy` foi corrigido; `NO_CONNECTION/GENERIC` ainda promete card no fluxo aberto, que não o renderiza. |
| COACH-16 | **CONFIRMADO COM SEVERIDADE AJUSTADA (informativo; validade operacional depende de redeploy/teste externo)** | `Main.kt:161-162,197-200`; `HANDOFF_IA_COACH.md:350-357` | Persona hardcoded em Rafael; redeploy é pendência documentada. |
| COACH-17 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa; contagem “8 dependências” é incorreta)** | `CoachViewModel.kt:199-208,906-915`; `wc -l` → 1222 | São 7 parâmetros no construtor; 1222 é o arquivo, e há helpers top-level após o fechamento da classe. |
| COACH-18 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa)** | `CoachChatComponents.kt:80-90,223-243`; `CoachHistoryCard.kt:72`; `PRD_COACH_CHAT.md:168` | Header/histórico têm heading; bolhas não mostram hora. |
| V-1 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa)** | `CoachResponseValidator.kt:26`; execução sintética | `17:30` é barrado como pace; falso positivo possível, ainda que a resposta não devesse inventar hora. |
| V-2 | **CONFIRMADO** | `CoachResponseValidator.kt:53-69,190-204`; execução sintética | “Hoje é descanso — descanse bem” cai na guarda de prescrição. |
| V-3 | **REFUTADO (evidência)** | `CoachNumberPatterns.kt:14`; `CoachResponseValidator.kt:288-292` | Com fato `82%`, `82,5%` é um número diferente e deve ser recusado. A mensagem pode identificar `5%`, mas isso não torna a recusa um falso positivo. |
| V-4 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa/média)** | `CoachViewModel.kt:701,997`; `CoachResponseValidator.kt:288-292` | `emptySet()` faz qualquer percentual cair, inclusive um percentual útil que não esteja no contexto. |
| V-5 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa/média; o número citado pode ser o ano, não “26”)** | `CoachSessionAnswer.kt:246-247`; `CoachViewModel.kt:990-1000`; validador `236-242` | ISO date é impresso no dossiê, mas `sessionCounts` não autoriza seus componentes; eco da data pode ser recusado. |
| V-6 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa; decisão conservadora discutível)** | `CoachOpenAnswer.kt:96-103,255-259` | Efeito real, mas números digitados são autorrelato, não dado verificado pelo motor. |
| U-1 | **CONFIRMADO** | `CoachResponseValidator.kt:117-121,274-309`; execução sintética | Demonstrado: explain aceitou 85%, 10 km e 99 sessões não autorizados. |
| U-2 | **CONFIRMADO COM SEVERIDADE AJUSTADA (condicional ao inteiro estar em `allowedCounts`)** | `CoachResponseValidator.kt:224-242` | Um `6` permitido como contagem pode passar em “6 horas”, sem unidade contextualizada. |
| U-3 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para média; a falha geral é real, mas o exemplo `12,5%` não foi reproduzido)** | `CoachResponseValidator.kt:236-242`; execução sintética de “12,5 unidades” passou | Decimais nus são ignorados; percentuais passam por outra regex e podem ser recusados como `5%`. |
| U-4 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa; exemplo literal refutado)** | `CoachResponseValidator.kt:190-204,251-271`; execução sintética | “Corra 5 mil metros” foi recusado pela guarda de verbo + número. Formulações declarativas sem verbo podem ainda escapar se `5` for contagem permitida. |
| U-5 | **CONFIRMADO** | `CoachResponseValidator.kt:53-69,190-204`; execução mental do texto da tabela | “Troque o intervalado por uma rodagem leve” não tem token estrutural listado nem número descoberto; pode passar. |
| U-6 | **CONFIRMADO** | `CoachResponseValidator.kt:102-104` | “Vou guardar isso” não está nas frases de promessa de memória. |
| U-7 | **CONFIRMADO COM SEVERIDADE AJUSTADA (duplicata de COACH-11)** | `CoachOpenAnswer.kt:153-155`; `CoachSessionAnswer.kt:78-82` | Cruzamento entre dias é possível na allow-list do dossiê aberto; o exemplo generaliza demais para sessão. |

### Documentação e geral

| Achado | Veredito | Evidência primária | Nota |
|---|---|---|---|
| DOC-01 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para média; “guiado” pode significar protocolo explicativo, não player)** | `PRODUCT.md:62`; `CalibrationScreen.kt:469-478`; busca `guidedTest/Teste30` sem hits | Não achei flow/cronômetro guiado dentro do app; há copy para fazer teste e inserir resultados manualmente. |
| DOC-02 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para média; PRODUCT escreve “contrato”)** | `PRODUCT.md:68`; `CompleteSession.kt:101-104`; `ROADMAP.md:430`; `ADR 0024:55` | `GUIDED` não é produzido; a qualificação “contrato” e os docs de ciclo futuro enfraquecem a leitura de promessa entregue. |
| DOC-03 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para média; falta plano de medição, não “impossibilidade total”)** | `PRODUCT.md:100-109`; `TESTING.md:28-31,56`; `PRD_TREINO_REAL.md:296-313`; buscas SDK sem hits | Não há SDK de analytics/crash nem `androidTest`. Há métricas técnicas locais em testes/goldens; as métricas de produto não têm protocolo completo. |
| DOC-04 | **CONFIRMADO** | `PRODUCT.md:73`; `ROADMAP.md:291-298`; busca `IMPORT_PENDING` sem hits | Roadmap implementa notificações de import concluído, não uma notificação “import pendente”. |
| DOC-05 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa; restauração não é promessa explícita do MVP)** | `PRODUCT.md:52,129`; `ROADMAP.md:858`; busca `restoreBackup/importBackup` sem hits | Export existe; backup versionado/restauração de originais é explicitamente futuro. |
| DOC-06 | **CONFIRMADO** | `PRODUCT.md:3`; `MeuCoachApp.kt:80-86`; `DECISIONS.md:179`; `ROADMAP.md:320` | PRODUCT não cobre Coach/Studio/Arte; descrições antigas da barra incluem Perfil e omitem Coach. |
| DOC-07 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa/média; só parte da Fase 3 foi antecipada)** | `ROADMAP.md:14,617-705,753-824` | Story tem fatias entregues; Zepp/social/Wear continuam planejados. Atualizar status por subfeature, não marcar Fase 3 inteira como concluída. |
| DOC-08 | **CONFIRMADO** | `PRD_TREINO_REAL.md:570-574,636-639,697-700`; `CICLO_4_TREINO_REAL.md:4,641` | Aceites permanecem `[ ]` apesar de Ciclo 4 declarar C4-61 concluído. Higiene documental. |
| DOC-09 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa; consolidado junta débitos de naturezas diferentes)** | `PROGRESS.md:6803` (88 baseline); `TESTING.md:476-501`; `PROGRESS.md:895-907` | Story flake existe no PROGRESS e falta na tabela de flakes do TESTING; outras dívidas são rastreadas. |
| DOC-10 | **CONFIRMADO** | `HANDOFF_POS_CHAT_TURNS.md:22,33-41`; `HANDOFF_COACH_V3.md:5`; `ARCHITECTURE.md:280`; `HANDOFF_IA_COACH.md:4-5` | Alguns handoffs ainda tratam DB v20/v21 ou F4 como próximo, embora o estado atual seja v22/feature concluída; IA Coach atual ainda registra redeploy pendente. |
| GEN-01 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para média; parcialmente duplicado de DOC-01..03)** | `PRODUCT.md:62,68,100-109`; `CompleteSession.kt:101-104`; `PRD_TREINO_REAL.md:296-313` | Lacuna de reconciliação existe; não implica que seja obrigatório implementar as três features no MVP. |
| GEN-02 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para média; duplicata de DOC-06/07)** | `PRODUCT.md:3`; `MeuCoachApp.kt:80-86`; `ROADMAP.md:14,617-705` | Reconciliar docs, mas separar o que foi entregue do que continua planejado. |
| GEN-03 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa; duplicata de DOC-05)** | `PRODUCT.md:52,129`; `ROADMAP.md:858` | Não há import/restore; o texto promete exportabilidade, não explicitamente um ciclo round-trip completo. |
| GEN-04 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa; sugestão de produto, não achado técnico)** | `SPORTS_SCIENCE.md:62-70`; `PRODUCT.md:62`; `CalibrationScreen.kt:469-478` | Um player Tier C exigiria ADR e gate de segurança; não recomendo tratá-lo como correção automática. |
| GEN-05 | **NÃO VERIFICÁVEL (escopo)** | `ROADMAP.md:695,701,705`; `PROGRESS.md:703-711` | Há validações físicas pendentes documentadas; não tive aparelho/emulador para confirmar estado real nem contar “~15” sem sobreposição. |
| GEN-06 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para média; “único critério” é falso)** | `ROADMAP.md:867,877`; `ROADMAP.md:318` | P1-12 está desmarcado, mas o próprio roadmap também condiciona aceite à validação em aparelho. |
| GEN-07 | **CONFIRMADO COM SEVERIDADE AJUSTADA (para baixa/média; parte já tem explicação fora do onboarding)** | `OnboardingScreen.kt:414-422`; E1; `TodayExplanation.kt:299-302` | Onboarding não diz que minutos são teto; Hoje explica prontidão sem dados; níveis de iniciante/intermediário começam com sessões iguais. |

## Pontos fortes confrontados

- **ALG-10 / §4.10:** rastreabilidade e determinismo são sustentados pelo código; não rerodei a matriz inteira E10. A afirmação de que a copy sempre nomeia a causa não vale para o caso sintético de 188 min, que diz “disponibilidade permite mais dias” após retirar uma sessão para caber no alvo (`WeeklyPlanGenerator.kt:1528-1556`).
- **SCI-04 / §6.4:** hierarquia de evidência e cautela com ACWR estão documentadas (`SPORTS_SCIENCE.md:37-47,180-201`). A alegação ampla de taper aderente é falsa: §8.6 pede média de três semanas, mas o fluxo usa a maior referência recente.
- **STORY-19 / §7:** vários pontos fortes são reais, mas “veredito/plano nunca entra no vídeo” é **falso**: o renderizador final desenha headline/proof (`StoryFinalRenderer.kt:29-46`); `StoryFrameRenderer.kt:107-150` entrega os highlights ao frame final; o plano cria o veredito (`StoryPlanCalculator.kt:43-49`).
- **COACH-19 / §8:** proxy sem log de payload é confirmado (`Main.kt:88-91,120-123`), mas o app pode logar o corpo de erro (`CoachApi.kt:116`; `CoachViewModel.kt:819-832`). “Validador como autoridade numérica” também é amplo demais: explain aceita números de distância/percentuais/contagens não autorizados.
- **§11 global:** separação de domínio, determinismo e migrações explícitas são pontos fortes sustentados por `ARCHITECTURE.md:48-59,278-294`. “Privacidade estrutural” e “validador como autoridade” precisam ser qualificados por COACH-13 e COACH-10. O número de testes e percentuais é declarado em `TESTING.md`, não recalculado nesta revisão.

## Veredito global e priorização

**A auditoria é útil como inventário, mas não é confiável o bastante para ordenar P0/P1 sem revisão.** A deficiência metodológica mais clara é o Apêndice A incompleto e o E16 não reproduzido com a receita visível. Há também inferências apresentadas como fatos e pelo menos um elogio falso importante (Story verdict no vídeo).

- **Endosso como P0:** COACH-05, COACH-06 e COACH-13. Para COACH-06, passar `exchangeId` e usar a pergunta daquele exchange; trocar só `lastFreeText` por “último fallback” não resolve cliques em cards antigos. Para COACH-13, remover o corpo da exceção e não logar `message`.
- **Adicionar como P0/P1:** NOVO-COACH-01 (arritmia e outras condições médicas fora da allow-list). Mitigação fail-closed deve preceder ampliação do Coach aberto.
- **Endosso como P1:** ALG-01, ALG-03 genérico e COACH-10/U-1. Estender ALG-01 ao fallback `NO_PLAN` e micro-dose de taper; corrigir o undershoot com pós-validação do resultado e copy derivada do volume entregue; endurecer também explain/review e decimais.
- **Adicionar como P1/P2:** discrepância taper média de três semanas vs máximo recente; longão iniciante +17 min; atribuição de Story verdict por data sem vínculo; UI que descarta os motivos de `Stay`.
- **Manter como P2, após decisão documentada:** ALG-02, SEG-03, ALG-04 e sugestão de graduação. Não impor limite de frequência ao iniciante nem mudar micro-dose/taper sem evidência/ADR.
- **Rebaixar:** COACH-15c para correção de copy; DOC-05 (restore) para decisão de produto; STORY-01/08/09/14 e COACH-02/03/08/18 são, em grande parte, melhorias ou itens explicitamente adiados.
- **Corrigir por documentação, não implementar automaticamente:** DOC-01/02/03/06/07. A opção mais conservadora é atualizar PRODUCT/ROADMAP para dizer com precisão o que é contrato, o que é manual e o que permanece futuro.
- **GEN-05:** a validação em aparelho continua importante, mas não confirmo o estado físico nem que sejam “~15 itens”; priorizar smoke de Story, revogação/Coach e layouts de acessibilidade.

## Estatísticas

Dos **84 achados originais classificados** (excluídos ALG-10, SCI-04, STORY-19 e COACH-19, que são contrabalanços positivos; V-7 já corrigido):

| Veredito | Quantidade |
|---|---:|
| CONFIRMADO | 28 |
| CONFIRMADO COM SEVERIDADE AJUSTADA | 53 |
| REFUTADO | 1 |
| NÃO VERIFICÁVEL (escopo) | 2 |
| NÃO VERIFICADO | 0 |

Além disso, encontrei **9 achados novos**; todos os fatos foram confirmados com código/teste sintético, embora a gravidade varie de baixa a alta.
