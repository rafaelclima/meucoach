# Prompt para criação da logo — MeuCoach

**Objetivo:** gerar uma logo (marca gráfica + tipografia) que represente o app **MeuCoach**, alinhada ao `docs/DESIGN_SYSTEM.md` (`v1.0`, 27/10/2026) e ao `ADR-0015` (branding no vídeo do story).

**Modelo recomendado:** `ChatGPT` (DALL-E 3 / o1-preview) ou `Gemini` (`gemini-2.5-pro-image-preview`), com saída em `PNG` (fundo transparente) e `SVG` (vetor editável).

---

## 1. Contexto do produto

- **Nome:** `MeuCoach` (pt-BR, pessoal, sem marca corporativa).
- **Categoria:** app Android nativo de coach de corrida (`P3-05` — story de treino ao ar livre).
- **Promessa:** "Você não precisa adivinhar se hoje é dia de correr forte. O app mostra o que seus dados dizem, o que ele recomenda e por quê." (`docs/PRODUCT.md` §1).
- **Tom:** preciso, silencioso, denso na medida certa (`DESIGN_SYSTEM.md` §1).
- **Marca:** "Precisão que orienta." (`DESIGN_SYSTEM.md` §1).

---

## 2. Referências visuais e inspiração

- **Strava** (mapa + rota + dados) — para o contexto do story (`ADR-0015`), mas com uma estética totalmente diferente.
- **Nike Run Club** — tipografia grande, números tabulares, movimento funcional.
- **Komoot / AllTrails** — minimalismo de mapa, sem excesso de cor.

**Diferença do MeuCoach:** não é uma marca de ginástica vibrante (`DESIGN_SYSTEM.md` §1). É um **instrumento pessoal de treino** — escuro, silencioso, com um único gesto de cor (`lima`) que indica "faça isto".

---

## 3. Diretrizes de design (fonte de verdade: `DESIGN_SYSTEM.md`)

| Elemento | Especificação |
|---|---|
| **Tema** | `dark-first` (`bg/base`: `#08090B`). A logo deve funcionar sobre fundo escuro e claro. |
| **Accent primário** | `lima` (`#D8FF3E`) — único gesto de cor. Pode ser usado no ícone ou no ponto da marca. |
| **Accent fisiológico** | `coral` (`#FF6B5E`) — reservado a FC/esforço; **não usar na logo**, apenas como referência de contexto. |
| **Tipografia** | `Geist` (display/números) + `Inter` (texto/UI). A tipografia da logo deve ser `Geist`, peso `600`, `tracking` apertado (`-0.03em` para display, conforme `DESIGN_SYSTEM.md` §4.2). |
| **Formas** | Bordas arredondadas (`radius/lg`: 28 dp, `radius/xl`: 36 dp), sem sombras pesadas, sem gradientes enganosos (`DESIGN_SYSTEM.md` §8.1). |
| **Movimento** | Nada decorativo; feedback funcional (`DESIGN_SYSTEM.md` §9). A logo pode sugerir movimento (rota, progresso) mas sem animação exagerada. |
| **Ícones** | Estilo `outlined`, linha `1.5–2 dp`, grade `24 dp` (`DESIGN_SYSTEM.md` §6). Se a logo incluir um símbolo gráfico, deve seguir esse estilo. |
| **Contraste** | AA (`4.5:1`) para texto, `3:1` para linhas (`DESIGN_SYSTEM.md` §10). `lima` (`#D8FF3E`) sobre `#08090B` atende. |

---

## 4. Especificações técnicas

- **Formato de entrega:** `PNG` (`1080×1080`, fundo transparente) + `SVG` (vetor editável, `viewBox="0 0 1080 1080"`).
- **Resolução mínima:** `512×512` (para launcher/icon) e `720×1280` (para o vídeo do story, parte inferior, centralizado).
- **Fundo:** transparente (`alpha` total). A logo deve funcionar sobre o mapa do `P3-05` (`bitmap` do `OpenStreetMap` + rota animada) e sobre o fundo `bg/base` (`#08090B`) do app.
- **Cores:** usar apenas `lima` (`#D8FF3E`), `text/primary` (`#F4F6F7`) e `bg/base` (`#08090B`). Sem cores extras.
- **Tipografia:** `Geist` (`font-family: Geist; font-weight: 600; letter-spacing: -0.03em`). Se `Geist` não estiver disponível para o modelo de imagem, usar uma fonte `sans-serif` geométrica equivalente (`Inter`, `Satoshi`, `General Sans`) — mas o arquivo final (`SVG`) deve usar `Geist` e ser editável.

---

## 5. Conceito da marca (o que a logo deve comunicar)

- **Movimento com propósito:** uma rota, um percurso, uma linha que avança. Não é um círculo estático.
- **Precisão:** linhas retas ou curvas suaves, sem ruído. A rota é sanitizada e precisa (`P0-05`/`P0-06`).
- **Local de partida:** o símbolo deve sugerir que há um ponto de início (`🏃` ou um ponto/linha estilizado) e um destino (`🏁` ou um ponto de chegada sutil).
- **Dados:** a marca pode incluir uma referência sutil a números/tabulares (como o `McStat` ou o `McGauge`), mas sem ser um dashboard.

---

## 6. Propostas de composição (escolher uma direção principal)

### Direção A — Rota estilizada (recomendada)
- **Ícone:** uma linha (`stroke`) estilizada que representa uma rota de corrida, com um ponto (`dot`) no início e um ponto menor no final. A linha pode ser `lima` (`#D8FF3E`) sobre `#08090B`, ou `text/primary` com um destaque `lima` no ponto de partida.
- **Tipografia:** `"MEUCOACH"` em `Geist`, `600`, `tracking` apertado, centralizado abaixo ou ao lado do ícone.
- **Layout:** horizontal (`logo` à esquerda + `MEUCOACH` à direita) para uso no vídeo e no app; quadrado (`logo` acima + `MEUCOACH` abaixo) para launcher/icon.
- **Tamanho no vídeo (`P3-05`):** `logoBitmap` desenhado no `Canvas` do `StoryFrameRenderer`, parte inferior (`drawFooter`), centralizado, tamanho `32f * scale` (`DESIGN_SYSTEM.md` §14).

### Direção B — Símbolo abstrato + tipografia
- **Ícone:** uma forma geométrica simples (ex.: um triângulo ou um losango estilizado) que sugere direção/progresso, sem ser uma rota literal. Pode ser `lima` (`#D8FF3E`) sobre `bg/base`.
- **Tipografia:** `"MEUCOACH"` grande, `Geist`, `600`, `tracking` apertado.
- **Justificativa:** mais minimalista, mas pode perder o contexto de corrida se não incluir uma referência à rota. Usar apenas se a Direção A não atender.

---

## 7. Do / Don't (baseado em `DESIGN_SYSTEM.md` e `ADR-0012`/`0015`)

| Do | Don't |
|---|---|
| Usar `lima` (`#D8FF3E`) como único accent (`DESIGN_SYSTEM.md` §2). | Adicionar `coral`, `lavanda`, `ciano` ou outras cores (`DESIGN_SYSTEM.md` §2). |
| Manter tipografia `Geist`, `600`, `tracking` apertado (`DESIGN_SYSTEM.md` §4.2). | Usar fontes decorativas, itálicas exageradas ou `bold` pesado. |
| Criar símbolo que sugere rota/progresso (`ADR-0015` §2.4). | Criar símbolo genérico (ex.: coração, raio) que não remete a corrida ou treino. |
| Entregar `PNG` (fundo transparente) + `SVG` editável. | Entregar apenas `JPG` ou `PNG` com fundo branco. |
| Garantir que a logo funciona sobre `bg/base` (`#08090B`) e sobre o mapa (`OSM` tiles coloridos). | Criar logo que some sobre o mapa ou que exija fundo específico para ser legível. |
| Usar `emoji` `🏃` como referência para o ponto de partida (`ADR-0015` §2.4), mas não como logo. | Substituir a logo por apenas um emoji (`🏃`) — a logo precisa ser uma marca própria. |

---

## 8. Referência final (para o designer/LLM)

```
Marca: MeuCoach
Categoria: App de corrida / coach de treino
Tema: Dark-first, minimalista, preciso
Accent: Lima #D8FF3E
Fonte: Geist 600, tracking -0.03em
Estilo: Linha de rota estilizada (stroke) + tipografia grande
Formato: PNG 1080x1080 (transparente) + SVG editável
Uso: Story de treino (parte inferior, centralizado, 32dp) e launcher/app
```

---

## 9. Observação para a implementação (`ADR-0015` §2.8)

- A logo (`logoBitmap`) é opcional no `StoryFrameRenderer` (`Bitmap?`). Se não existir no momento da implementação, o `drawFooter` desenha apenas `"MeuCoach"` (`DESIGN_SYSTEM.md` §14).
- Quando o arquivo `logo.png`/`logo.svg` for criado, ele deve ser colocado em `app/src/main/res/drawable-xxxhdpi/` (ou `drawable/`) com o nome `logo_meucoach`.
- O `StoryVideoExporter` deve aceitar `logoBitmap: Bitmap?` no construtor do `StoryFrameRenderer`.
