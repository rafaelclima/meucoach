# ADR 0060 — Arte PNG 9:16, layout de métricas e prévia fixa

**Status:** Aceito — implementação em andamento
**Data:** 2026-11-23
**Responsável:** usuário 0 (decisão de produto) e implementação
**Documentos de origem:** [`PRD_ARTE_PNG_ROTA.md`](../PRD_ARTE_PNG_ROTA.md),
[`ADR 0058`](0058-art-png-route.md), [`AUDITORIA_ARTE_PNG_UI_UX.md`](../AUDITORIA_ARTE_PNG_UI_UX.md), `AGENTS.md`

---

## 1. Contexto

O ADR 0058 fechou a Arte PNG como um **card transparente** em dois formatos,
4:5 e 1:1, com a rota quadrada e um rodapé com tagline. O PRD §4 tratava
“9:16” como escopo proibido para não prometer um overlay full-bleed.

Depois de usar a feature no aparelho, o usuário 0 pediu três mudanças de
produto:

1. Um terceiro formato **9:16** para o card transparente.
2. Um layout mais compacto: distância um pouco menor, ritmo médio e tempo
   total empilhados à direita, extras logo abaixo e **sem o rodapé**.
3. Ver a cor/fonte mudar **sem rolar de volta** para a prévia.

O 9:16 pedido não é o overlay full-bleed opaco rejeitado no 0058: é o mesmo
card transparente, com o mesmo conteúdo e a mesma personalização, apenas
com uma área vertical maior para a rota. A prévia fixa também não muda o
motor: o renderer continua determinístico e o export síncrono.

## 2. Decisão

### 2.1 9:16 aditivo

- `ArtFormat.PORTRAIT_9_16` com **1080×1920**, arquivo `…-arte-9x16.png`.
- Os formatos continuam **aditivos**: 4:5 (default), 1:1 e 9:16.
- O 9:16 é um **card transparente**, sem mapa, sem fundo opaco e sem
  `ROUTE_CROP`; a rota continua em área quadrada para não distorcer a
  projeção normalizada.
- A antiga proibição passa a ser: **sem overlay 9:16 full-bleed opaco**.

### 2.2 Layout comum aos três formatos

- Marca no topo, como hoje.
- Rótulo `DISTÂNCIA` mais próximo do número; a fonte da distância cai
  ~10% (150 → ~135 em 4:5/9:16; 130 → ~117 em 1:1).
- Ritmo médio e tempo total alinhados à **margem direita**, na mesma
  faixa vertical da distância:
  - `RITMO MÉDIO` acima de `6:00 /km`;
  - `TEMPO TOTAL` acima de `30:00`;
  - rótulos e valores mais próximos entre si, um bloco abaixo do outro.
- Métricas extras imediatamente abaixo do bloco de distância/ritmo/tempo,
  no estilo value-only atual.
- **Rodapé removido**: sem “CORRIDA” e sem “Seu treino, com contexto.”.
- A rota ocupa o espaço restante e ganha a área que era do rodapé; em 9:16
  ela é limitada pela largura e centralizada verticalmente.

### 2.3 Prévia fixa na aba Arte

- A prévia grande fica **fixa no topo da aba Arte**; o restante (copy,
  formato, extras, cores, fonte, switch e CTAs) rola abaixo dela.
- Header, resumo e segmento do Studio também ficam fixos; o conteúdo de
  vídeo passa a rolar em uma área interna.
- O toggle de checkerboard e o aviso de contraste saem da prévia fixa e
  entram na área rolável, para não roubar altura. A prévia continua
  refletindo o toggle em tempo real.
- A altura da arte na prévia é adaptativa para preservar uma área mínima
  de controles em 360 dp/200%.
- Isso substitui a âncora “Ver prévia” da auditoria (P2-11) e a ideia de
  mini prévia flutuante: a prévia fixa resolve o mesmo problema sem
  sobrepor controles.

## 3. Alternativas rejeitadas

| Alternativa | Motivo |
|---|---|
| Substituir 4:5/1:1 pelo 9:16 | Perde os formatos de feed já usados e o default documentado. |
| 9:16 full-bleed opaco com mapa | Continua fora de escopo: rede, tiles, peso e promessa de “qualquer story”. |
| Manter o rodapé com tagline | O usuário 0 o considera dispensável; a rota ganha espaço útil. |
| Mini prévia flutuante | O usuário preferiu a prévia fixa com o restante rolando. |
| Prévia fixa com todo o chrome atual | Ocuparia ~500 dp e deixaria pouca área de controles em 200%. A prévia fixa mantém header e arte; checker/aviso rolam. |
| Recalcular o layout por formato com coordenadas fixas | O renderer já é por blocos; duplicar coordenadas do pôster criaria drift. |

## 4. Consequências

### Boas

- O usuário vê cor/fonte mudarem em tempo real, sem rolar.
- O 9:16 atende ao uso em story sem virar overlay opaco.
- O layout fica mais limpo: distância + ritmo/tempo na mesma faixa,
  extras logo abaixo e sem rodapé.
- O codec existente já aceita o novo `ArtFormat` pelo nome; receitas
  antigas continuam válidas e um downgrade cai no default documentado.

### Ruins / riscos

- **Goldens:** 4:5/1:1 mudam de layout; entram 9:16 e a prévia fixa.
- **Renderer:** `drawPaceTimeColumns`/`drawPaceTimeLine`/`drawFooter` saem;
  o novo bloco precisa de testes de posição e de não sobreposição em 1:1.
- **200%:** a prévia fixa reduz a área de controles; a altura adaptativa e
  o golden de 360 dp/200% são o gate.
- **Escopo:** o PRD/ADR 0058 precisam da emenda; sem ela, o 9:16 continua
  tecnicamente proibido.
- **Arte 9:16:** a rota é limitada pela largura (1080); a área vertical
  extra fica transparente e o usuário posiciona sobre a foto. Não é uma
  promessa de preenchimento total.

## 5. Implementação e aceite

- `ArtFormat`: `PORTRAIT_9_16(1080, 1920, "9x16")`; `formatLabel` “9:16”;
  `StoryFileNames` e `ArtPngExporter` sem branch especial.
- `StoryArtRenderer`: novo bloco de distância/ritmo/tempo, extras abaixo,
  remoção do rodapé e branch 9:16 (mesmo bloco de topo; rota centralizada).
- `ArtStudioContent`: 3º chip, prévia fixa, controles em rolagem interna,
  checker/aviso na área rolável; altura da prévia adaptativa.
- `StoryStudioContent`: header/segmento fixos e conteúdo de vídeo em
  rolagem interna.
- Testes: domínio/codec/filename, renderer (alpha, posições, 9:16),
  `ArtStudioContentTest` (prévia fixa, 3 chips, 200%), `ArtPngExportTest`
  (9:16), goldens `art-png-4x5/1x1/9x16`, `art-studio-tab-*` atualizados.
- Docs: PRD §2/§3/§4/§5/§8/§9/§12/§13, ADR 0058 §2.1/§2.2/§2.8/§3,
  `DECISIONS`, `PROGRESS` e `ROADMAP`.

**Aceite:** 9:16 exporta 1080×1920 com alpha preservado; 4:5/1:1/9:16
usam o novo layout sem rodapé; a prévia fica visível enquanto o usuário
rola até cores/fonte e atualiza ao trocar cor/fonte; 360 dp/200% e
TalkBack continuam utilizáveis; gates verdes.
