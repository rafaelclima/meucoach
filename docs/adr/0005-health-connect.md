# ADR-0005 — Health Connect + check-in/CSV como fallback

**Status:** Aceito · **Data:** 27/10/2026

## Contexto

O Zepp não oferece export diário confiável de sono/HRV/RHR via arquivo. O Health Connect já recebeu dados de sono do Amazfit no dispositivo do usuário 0. Ainda assim, a sincronização pode falhar e alguns tipos podem não existir.

## Decisão

- Health Connect é a fonte primária de dados diários (sono, RHR, HRV rMSSD, SpO2, exercício, passos).
- Check-in de 15 s é sempre disponível e alimenta a prontidão mesmo sem wearable.
- Importação CSV é fallback manual.
- Conector Zepp não oficial fica para a Fase 3.
- Ausência de dado reduz confiança; nunca é interpretada como recuperação ruim.
- Tela de diagnóstico mostra tipo, último registro, fonte e status.

## Alternativas

- **Depender do Zepp:** bloqueado pela ausência de export diário.
- **Só check-in manual:** simples, mas perde objetividade e histórico.
- **API não oficial do Zepp agora:** risco de quebra e termos de uso.

## Consequências

- Boa: funciona hoje, é privacy-friendly e cobre falhas.
- Ruim: Health Connect pode variar por fabricante/Android; exige diagnóstico claro.
- O motor precisa ser robusto a dados parciais desde o dia 1.
