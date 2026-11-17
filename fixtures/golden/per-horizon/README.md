# G-PER-06 — horizonte de 5K e pace dinâmico

Prova `FR-411` (ADR 0043/0047): uma prova de 5K a 26 semanas não deixa o plano
em manutenção, e o `PEAK` usa o pace da previsão atual, nunca a meta.

## Cenário

- Intermediário, 4 dias (seg 40, qua 40, sex 40, dom 90), VDOT 29,65
  (`TRAINING_ESTIMATE` calibrado em 30/10) e 5 semanas de referência de 150 min.
- Prova de 5K em 02/05/2027, 25 semanas depois da semana de 02/11/2026.
- Meta declarada de 29:00; a previsão atual é 30:59.

## O que o golden prova

1. **Modo geral com qualidade:** a semana de 02/11/2026 é `BUILD` (rotação
   geral), com "Intervalos" na quarta (`QUALITY_OPPORTUNITY`), strides `EASY`
   na segunda, fácil na sexta e "Longão" de 45 min no domingo; 150 min.
2. **Bloco específico no fim:** a partir de 08/02/2027 (12 semanas antes da
   prova) o `MacroPlanResolver` usa a tabela de 5K de 12 semanas: `BASE` 5,
   `BUILD` 4, `PEAK` 1, `TAPER` 2.
3. **PEAK dinâmico:** na semana de 12/04/2027 o `PEAK` é "Pace de prova 5K"
   (`RACE_PACE`) com o bloco no pace previsto `6:12/km` (faixa 5:53–6:30/km) e
   RPE 8 como fallback se não houver previsão recente.
4. **Gap explícito:** a meta de 29:00 é 6% mais rápida que a previsão de 30:59;
   o rationale a chama de ambiciosa e mantém o pace previsto. Uma meta mais
   lenta (33:20) só muda o gap — o pace do bloco não muda.
5. **Sem prova:** o modo geral mantém a qualidade, mas não inventa `PEAK` ou
   taper.

Nenhum dado real de saúde. A calibração do cenário de `PEAK` é atualizada para
05/04/2027 no teste para isolar a previsão do `PEAK` da validade de 56 dias.
