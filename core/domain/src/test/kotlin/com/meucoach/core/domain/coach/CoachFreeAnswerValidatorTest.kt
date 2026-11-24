package com.meucoach.core.domain.coach

import org.junit.Assert.assertTrue
import org.junit.Test

class CoachFreeAnswerValidatorTest {
    private val minutes = setOf(30, 40)
    private val percents = setOf(80, 20)
    private val distances = setOf(8.0, 10.5)

    private fun freeAnswer(
        body: String,
        allowedDistancesKm: Set<Double> = distances,
        allowedCounts: Set<Int> = emptySet(),
    ) = CoachResponseValidator.validateFreeAnswer(
        raw = CoachRawResponse(title = "Sobre a semana", body = body),
        allowedMinutes = minutes,
        allowedPercents = percents,
        allowedDistancesKm = allowedDistancesKm,
        allowedCounts = allowedCounts,
    )

    @Test
    fun `aceita distancia do plano`() {
        val result = freeAnswer("Vamos manter os 8 km leves e consistentes")
        assertTrue(result is CoachValidationResult.Valid)
    }

    @Test
    fun `aceita distancia decimal do plano com virgula`() {
        val result = freeAnswer("O longo de 10,5 km segue como planejado")
        assertTrue(result is CoachValidationResult.Valid)
    }

    @Test
    fun `barra distancia fora do plano`() {
        val result = freeAnswer("Vamos fazer 12 km no fim de semana")
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `barra pace no caminho livre`() {
        val result = freeAnswer("Tente manter 6:10 nos trechos fortes")
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `barra minuto inventado no caminho livre`() {
        val result = freeAnswer("Vamos fazer 90 min leves")
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `barra porcentual inventado no caminho livre`() {
        val result = freeAnswer("A semana foi 95 por cento leve".replace(" por cento", "%"))
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `barra verbo de prescricao com numero`() {
        val result = freeAnswer("Faça 6 repetições no treino de hoje")
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `aceita verbo com duracao planejada`() {
        val result = freeAnswer("Faça os 40 min leves de hoje")
        assertTrue(result is CoachValidationResult.Valid)
    }

    @Test
    fun `barra verbo com duracao fora do plano`() {
        val result = freeAnswer("Faça os 50 min de hoje")
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `barra verbo com numero sem unidade`() {
        val result = freeAnswer("Faça os 40 de hoje")
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `barra verbo de prescricao com mudanca de estrutura`() {
        val result = freeAnswer("Troque o treino de hoje por descanso")
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `barra descanso com dias`() {
        val result = freeAnswer("Descanse 3 dias para recuperar")
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `aceita reformulacao de descanso sem numero nem estrutura`() {
        val result = freeAnswer("Hoje é descanso, aproveite para dormir bem")
        assertTrue(result is CoachValidationResult.Valid)
    }

    @Test
    fun `barra sintoma`() {
        val result = freeAnswer("Se a dor persistir depois dos 30 min, me avise")
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `barra sintoma fora do lexico original`() {
        val result = freeAnswer("Se tiver febre ou tontura, pare e procure um médico")
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `barra diagnostico`() {
        val result = freeAnswer("Isso parece tendinite, procure um médico")
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `barra musculacao`() {
        val result = freeAnswer("Inclua musculação duas vezes na semana")
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `barra promessa de memoria`() {
        val result = freeAnswer("Vou lembrar do seu longo para a próxima")
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `aceita copy de hidratacao estatica sem numeros`() {
        val result = freeAnswer("Com esse calor, hidrate-se ao longo do dia")
        assertTrue(result is CoachValidationResult.Valid)
    }

    @Test
    fun `aceita contagens que estao nos fatos`() {
        val result = freeAnswer("Você fez 3 de 4 treinos.", allowedCounts = setOf(3, 4))
        assertTrue(result is CoachValidationResult.Valid)
    }

    @Test
    fun `barra contagem inventada`() {
        val result = freeAnswer("Você fez 9 treinos.", allowedCounts = setOf(3, 4))
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `barra numero de data que nao esta nos fatos`() {
        val result = freeAnswer("Na semana de 16/11 você correu bem.", allowedCounts = setOf(3, 4))
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `caminho legado nao barra sintoma`() {
        val result = CoachResponseValidator.validate(
            raw = CoachRawResponse(title = "Semana", body = "Se a dor persistir, fale com o Hoje"),
            allowedMinutes = minutes,
        )
        assertTrue(result is CoachValidationResult.Valid)
    }
}
