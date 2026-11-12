# ADR 0010 — Disponibilidade como teto e progressão por qualidade

**Status:** aceito em 30/10/2026; **volume substituído pelo ADR 0038 em 12/11/2026** — a disponibilidade continua teto rígido, mas a progressão por volume passou a partir do realizado.

> O ponto de volume deste ADR (aumento só ao atualizar a disponibilidade) deixou de valer
> com o [ADR 0038](0038-volume-progression-from-completed.md), quando passou a existir
> histórico confiável de conclusão. O teto de disponibilidade e a progressão por qualidade
> continuam válidos.

## Contexto

O plano semanal usa a disponibilidade informada no perfil como base de duração.
A ciência recomenda progressão de volume de 5–8% apenas com prontidão verde,
ACWR seguro e boa conclusão da semana anterior. Ao mesmo tempo, aumentar minutos
além do que o usuário declarou como disponível seria uma recomendação irreal.

## Decisão

- A disponibilidade é um **teto rígido** para minutos planejados.
- A progressão não ultrapassa esse teto.
- Quando a semana anterior foi ≥ 80% concluída e os guardrails estão verdes, o
  plano libera **uma segunda sessão de qualidade** (máximo 2), nunca consecutiva
  e nunca adjacente ao longão.
- Aumento de volume só acontece quando o usuário atualiza a disponibilidade.
- A regra é citada como `R-PLAN-02` e a justificativa aparece no plano.

## Alternativas rejeitadas

1. **Aumentar 5–8% acima da disponibilidade:** pode gerar treino que não cabe na
   rotina e reduzir adesão.
2. **Aplicar percentual fixo sobre o volume da semana anterior:** sem histórico
   confiável de conclusão, vira progressão inventada.
3. **Adicionar um dia extra:** viola a disponibilidade e a regra de recuperação.

## Consequências

- O produto permanece conservador e explicável.
- Usuários que querem volume maior precisam atualizar a disponibilidade; o app
  não decide isso por eles.
- A progressão mensurável desta versão é de **dose de qualidade**, não de km.
