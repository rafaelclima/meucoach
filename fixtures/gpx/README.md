# GPX fixtures

As duas fixtures são sintéticas; nenhum GPX real é commitado. Os arquivos
originais do Zepp contêm nomes de eventos e coordenadas, então o parser é
testado com dados neutros e determinísticos.

## `synthetic-extensions.gpx`

- GPX 1.1 com namespace `gpxtpx`.
- Três `trkpt`s, 120 s, cerca de 22,24 m.
- Extensões `hr`, `cad` e `speed`; `cad` em passadas/min é normalizada para
  passos/min.
- FC média/máx: 150/160 bpm; cadência média/máx: 164/168 spm.
- Elevação: +2,0/−1,0 m.
- `creator="example.invalid"` no elemento raiz.

## `synthetic-no-elevation.gpx`

- GPX 1.0, dois pontos, 60 s, cerca de 11,12 m.
- Sem `ele`, sem FC e sem cadência: esses campos permanecem `null`, não zero.

Os testes também cobrem XML malformado, XXE, coordenadas inválidas, ausência de
tempo e extensão de potência em `GpxParserTest`.
