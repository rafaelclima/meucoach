package com.meucoach.core.domain.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class FreeInputParserTest {
    @Test
    fun `pergunta sobre o dia vira ASK_SESSION`() {
        val parsed = FreeInputParser.parse("por que o treino de hoje é fácil?", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.ASK_SESSION, parsed.intent)
    }

    @Test
    fun `pergunta sobre amanha vira ASK_SESSION`() {
        val parsed = FreeInputParser.parse("o que tem amanhã?", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.ASK_SESSION, parsed.intent)
        assertEquals(FreeInputSlots(dayOfWeek = DayOfWeek.TUESDAY, dayOffset = 1), parsed.slots)
    }

    @Test
    fun `amanha no domingo carrega offset exato`() {
        val parsed = FreeInputParser.parse("o que tem amanhã?", DayOfWeek.SUNDAY)
        assertEquals(FreeInputIntent.ASK_SESSION, parsed.intent)
        assertEquals(FreeInputSlots(dayOfWeek = DayOfWeek.MONDAY, dayOffset = 1), parsed.slots)
    }

    @Test
    fun `hoje carrega offset zero`() {
        val parsed = FreeInputParser.parse("o que tem hoje?", DayOfWeek.SUNDAY)
        assertEquals(FreeInputIntent.ASK_SESSION, parsed.intent)
        assertEquals(FreeInputSlots(dayOfWeek = DayOfWeek.SUNDAY, dayOffset = 0), parsed.slots)
    }

    @Test
    fun `dia nomeado nao tem offset`() {
        val parsed = FreeInputParser.parse("qual o treino de domingo?", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.ASK_SESSION, parsed.intent)
        assertEquals(FreeInputSlots(dayOfWeek = DayOfWeek.SUNDAY), parsed.slots)
    }

    @Test
    fun `como foi semana passada vira ASK_REVIEW`() {
        val parsed = FreeInputParser.parse("como foi semana passada?", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.ASK_REVIEW, parsed.intent)
    }

    @Test
    fun `relato de treino feito vira REPORT_DONE`() {
        val parsed = FreeInputParser.parse("fiz o longo de hoje", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.REPORT_DONE, parsed.intent)
        assertEquals("fiz o longo de hoje", parsed.sanitized)
    }

    @Test
    fun `relato de sono vira REPORT_DONE`() {
        val parsed = FreeInputParser.parse("dormi 5h40 essa noite", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.REPORT_DONE, parsed.intent)
    }

    @Test
    fun `só tenho 30 min quinta vira REQUEST_ADJUST com slots`() {
        val parsed = FreeInputParser.parse("só tenho 30 min quinta", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.REQUEST_ADJUST, parsed.intent)
        assertEquals(FreeInputSlots(dayOfWeek = DayOfWeek.THURSDAY, minutes = 30), parsed.slots)
    }

    @Test
    fun `parser ignora caixa e acento`() {
        val parsed = FreeInputParser.parse("SO TENHO 30 MIN QUINTA", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.REQUEST_ADJUST, parsed.intent)
        assertEquals(FreeInputSlots(dayOfWeek = DayOfWeek.THURSDAY, minutes = 30), parsed.slots)
    }

    @Test
    fun `amanha resolve contra o dia informado`() {
        val parsed = FreeInputParser.parse("só tenho 20 min amanhã", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.REQUEST_ADJUST, parsed.intent)
        assertEquals(FreeInputSlots(dayOfWeek = DayOfWeek.TUESDAY, minutes = 20, dayOffset = 1), parsed.slots)
    }

    @Test
    fun `perdi o treino vira REQUEST_ADJUST sem slots`() {
        val parsed = FreeInputParser.parse("perdi o treino", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.REQUEST_ADJUST, parsed.intent)
        assertEquals(FreeInputSlots(), parsed.slots)
    }

    @Test
    fun `nao fiz o treino de ontem vira REQUEST_ADJUST com ontem resolvido`() {
        val parsed = FreeInputParser.parse("não fiz o treino de ontem", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.REQUEST_ADJUST, parsed.intent)
        assertEquals(FreeInputSlots(dayOfWeek = DayOfWeek.SUNDAY, dayOffset = -1), parsed.slots)
    }

    @Test
    fun `pergunta de capacidade vira ASK_CAPABILITIES`() {
        val parsed = FreeInputParser.parse("o que você pode fazer?", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.ASK_CAPABILITIES, parsed.intent)
    }

    @Test
    fun `dor com pedido de numero continua OUT_OF_SCOPE`() {
        val parsed = FreeInputParser.parse(
            "estou com dor no joelho, posso correr 30 min?",
            DayOfWeek.MONDAY,
        )
        assertEquals(FreeInputIntent.OUT_OF_SCOPE, parsed.intent)
    }

    @Test
    fun `musculacao vira OUT_OF_SCOPE`() {
        val parsed = FreeInputParser.parse("me passa um treino de musculação", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.OUT_OF_SCOPE, parsed.intent)
    }

    @Test
    fun `pergunta de pace vira pergunta aberta`() {
        val parsed = FreeInputParser.parse("qual meu pace de limiar?", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.ASK_COACHING, parsed.intent)
    }

    @Test
    fun `pedido de plano novo vira OUT_OF_SCOPE`() {
        val parsed = FreeInputParser.parse("monta meu treino de amanhã", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.OUT_OF_SCOPE, parsed.intent)
    }

    @Test
    fun `falta de numero no pedido de tempo curto vira ASK_CLARIFY`() {
        val parsed = FreeInputParser.parse("só tenho pouco tempo quinta", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.ASK_CLARIFY, parsed.intent)
    }

    @Test
    fun `texto em branco vira ASK_CLARIFY`() {
        val parsed = FreeInputParser.parse("   ", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.ASK_CLARIFY, parsed.intent)
    }

    @Test
    fun `intencoes misturadas viram ASK_CLARIFY`() {
        val parsed = FreeInputParser.parse(
            "fiz o longo e perdi o forte, como foi a semana?",
            DayOfWeek.MONDAY,
        )
        assertEquals(FreeInputIntent.ASK_CLARIFY, parsed.intent)
    }

    @Test
    fun `nonsense vira OUT_OF_SCOPE`() {
        val parsed = FreeInputParser.parse("abacaxi amarelo voa", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.OUT_OF_SCOPE, parsed.intent)
    }

    @Test
    fun `sanitized limita a 500 caracteres`() {
        val parsed = FreeInputParser.parse("treino " + "x".repeat(600), DayOfWeek.MONDAY)
        assertTrue(parsed.sanitized.length <= FreeInputParser.MAX_CHARS)
    }

    @Test
    fun `como anda minha evolucao vira ASK_PROGRESS`() {
        val parsed = FreeInputParser.parse("e como anda minha evolução?", DayOfWeek.SUNDAY)
        assertEquals(FreeInputIntent.ASK_PROGRESS, parsed.intent)
        assertEquals(FreeInputSlots(), parsed.slots)
    }

    @Test
    fun `como estou evoluindo vira ASK_PROGRESS`() {
        val parsed = FreeInputParser.parse("como estou evoluindo?", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.ASK_PROGRESS, parsed.intent)
    }

    @Test
    fun `progresso vira ASK_PROGRESS`() {
        val parsed = FreeInputParser.parse("mostra meu progresso", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.ASK_PROGRESS, parsed.intent)
    }

    @Test
    fun `revisao e evolucao juntas viram ASK_CLARIFY`() {
        val parsed = FreeInputParser.parse("como foi minha evolução na semana passada?", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.ASK_CLARIFY, parsed.intent)
    }

    @Test
    fun `dica no fim da frase com dia antes vira ASK_HOW_TO_RUN`() {
        val parsed = FreeInputParser.parse("e pra hoje, qual a dica?", DayOfWeek.SUNDAY)
        assertEquals(FreeInputIntent.ASK_HOW_TO_RUN, parsed.intent)
        assertEquals(FreeInputSlots(dayOfWeek = DayOfWeek.SUNDAY, dayOffset = 0), parsed.slots)
    }

    @Test
    fun `dica sem ancora de treino ou dia continua fora de escopo`() {
        val parsed = FreeInputParser.parse("me dá uma dica", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.OUT_OF_SCOPE, parsed.intent)
    }

    @Test
    fun `dica para dia nomeado vira ASK_HOW_TO_RUN`() {
        val parsed = FreeInputParser.parse("qual a dica pra domingo?", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.ASK_HOW_TO_RUN, parsed.intent)
        assertEquals(FreeInputSlots(dayOfWeek = DayOfWeek.SUNDAY), parsed.slots)
    }

    @Test
    fun `dica de como correr amanha vira ASK_HOW_TO_RUN`() {
        val parsed = FreeInputParser.parse("me dá uma dica de como correr amanhã", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.ASK_HOW_TO_RUN, parsed.intent)
        assertEquals(FreeInputSlots(dayOfWeek = DayOfWeek.TUESDAY, dayOffset = 1), parsed.slots)
    }

    @Test
    fun `como e o treino de hoje vira ASK_HOW_TO_RUN`() {
        val parsed = FreeInputParser.parse("como é o treino de hoje?", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.ASK_HOW_TO_RUN, parsed.intent)
        assertEquals(FreeInputSlots(dayOfWeek = DayOfWeek.MONDAY, dayOffset = 0), parsed.slots)
    }

    @Test
    fun `qual treino de domingo vira ASK_SESSION`() {
        val parsed = FreeInputParser.parse("qual o treino de domingo?", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.ASK_SESSION, parsed.intent)
        assertEquals(FreeInputSlots(dayOfWeek = DayOfWeek.SUNDAY), parsed.slots)
    }

    @Test
    fun `como correr o longao vira ASK_HOW_TO_RUN sem dia`() {
        val parsed = FreeInputParser.parse("como corro o longão?", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.ASK_HOW_TO_RUN, parsed.intent)
        assertEquals(FreeInputSlots(), parsed.slots)
    }

    @Test
    fun `pergunta de pace com dia vira ASK_HOW_TO_RUN com slot`() {
        val parsed = FreeInputParser.parse("qual meu pace pra amanhã?", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.ASK_HOW_TO_RUN, parsed.intent)
        assertEquals(FreeInputSlots(dayOfWeek = DayOfWeek.TUESDAY, dayOffset = 1), parsed.slots)
    }

    @Test
    fun `sessao mais relato vira pergunta de sessao`() {
        val parsed = FreeInputParser.parse("fiz o longo e o que tem amanhã?", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.ASK_SESSION, parsed.intent)
    }

    @Test
    fun `duvida de corrida sem intent fixa vira ASK_COACHING`() {
        assertEquals(
            FreeInputIntent.ASK_COACHING,
            FreeInputParser.parse("o que é fartlek?", DayOfWeek.MONDAY).intent,
        )
        assertEquals(
            FreeInputIntent.ASK_COACHING,
            FreeInputParser.parse("como está minha prontidão?", DayOfWeek.MONDAY).intent,
        )
        assertEquals(
            FreeInputIntent.ASK_COACHING,
            FreeInputParser.parse("posso correr na chuva?", DayOfWeek.MONDAY).intent,
        )
    }

    @Test
    fun `afirmacao sem tema de corrida continua fora de escopo`() {
        val parsed = FreeInputParser.parse("abacaxi amarelo voa", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.OUT_OF_SCOPE, parsed.intent)
    }

    @Test
    fun `pergunta fora do tema ainda pode ser recusada pelo modelo`() {
        val parsed = FreeInputParser.parse("qual a capital da França?", DayOfWeek.MONDAY)
        assertEquals(FreeInputIntent.ASK_COACHING, parsed.intent)
    }
}
