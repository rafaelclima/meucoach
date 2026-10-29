# TCX fixtures

Estes três arquivos são corridas reais de 5 km de outubro/2026, sanitizadas com
`tools/sanitize_tcx.py`. O script remove `Position`, `Creator`, `Notes` e
`Author`; timestamps e métricas de treino são mantidos para os testes de
regressão. Nenhuma coordenada, nome de dispositivo ou nota pessoal permanece.

| Arquivo | Início (UTC) | Duração | Distância | FC média/máx | Cadência média/máx | Calorias | Samples | Ganho/perda de elevação |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| `Zepp20261008052132.tcx` | 2026-10-08 08:21:32 | 2.064 s | 5.016 m | 171/185 bpm | 185/194 spm | 528 | 2.065 | +81,84/−82,91 m |
| `Zepp20261013052204.tcx` | 2026-10-13 08:22:04 | 1.976 s | 5.037 m | 170/184 bpm | 186/196 spm | 501 | 1.977 | +81,12/−83,12 m |
| `Zepp20261016053227.tcx` | 2026-10-16 08:32:27 | 2.008 s | 5.119 m | 170/185 bpm | 188/196 spm | 510 | 2.009 | +75,12/−76,32 m |

A cadência do TCX do Zepp vem em passadas/min e é multiplicada por 2 pelo
parser para virar passos/min. Como as posições foram removidas, estes arquivos
não exercitam GPS; `TcxParserSyntheticTest` cobre posições, múltiplos laps,
laps ausentes, XML inválido, XXE e encoding.

Os arquivos originais ficam fora do repositório. Não adicione TCX sem rodar o
sanitizador e revisar o diff.
