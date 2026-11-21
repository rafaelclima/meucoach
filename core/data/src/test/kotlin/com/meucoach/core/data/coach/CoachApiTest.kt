package com.meucoach.core.data.coach

import com.meucoach.core.domain.coach.CoachFreeAnswerPrompt
import com.meucoach.core.domain.coach.CoachPrompt
import com.meucoach.core.domain.coach.CoachReviewAdherence
import com.meucoach.core.domain.coach.CoachReviewPrompt
import com.meucoach.core.domain.coach.CoachReviewVolume
import com.meucoach.core.domain.coach.CoachSessionSummary
import com.meucoach.core.domain.coach.FreeInputIntent
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.io.IOException
import java.time.DayOfWeek

class CoachApiTest {
    private fun prompt() = CoachPrompt(
        engineVersion = "weekly-plan-v6",
        inputsHash = "abc",
        locale = "pt-BR",
        weeklyMinutes = 30,
        executedMinutes = null,
        adherenceRatio = null,
        sessions = listOf(
            CoachSessionSummary(date = "2026-11-17", type = "EASY", durationMin = 30, status = "PLANNED"),
        ),
        rationale = "Semana base",
        ruleIds = listOf("R-PLAN-02"),
        confidence = "MEDIUM",
    )

    @Test
    fun `parse extrai content do envelope OpenRouter`() {
        val inner = """{"title":"Foco em base","body":"Vamos manter os 30 min.","bullets":[]}"""
        val escaped = inner.replace("\\", "\\\\").replace("\"", "\\\"")
        val body = """{"choices":[{"message":{"content":"$escaped"}}]}"""

        val parsed = HttpCoachApi.parse(body)

        assertThat(parsed.title).isEqualTo("Foco em base")
        assertThat(parsed.body).contains("30 min")
    }

    @Test
    fun `parse aceita JSON direto`() {
        val parsed = HttpCoachApi.parse("""{"title":"T","body":"Vamos manter os 30 min."}""")

        assertThat(parsed.title).isEqualTo("T")
    }

    @Test
    fun `round-trip contra servidor local`() {
        val inner = """{"title":"Foco","body":"Vamos manter os 30 min."}"""
        val escaped = inner.replace("\\", "\\\\").replace("\"", "\\\"")
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(0), 0)
        server.createContext("/api/v1/coach/explain") { exchange ->
            val res = """{"choices":[{"message":{"content":"$escaped"}}]}""".toByteArray()
            exchange.sendResponseHeaders(200, res.size.toLong())
            exchange.responseBody.use { it.write(res) }
        }
        server.start()
        try {
            val api = HttpCoachApi(
                baseUrl = "http://localhost:${server.address.port}",
                token = "t",
            )
            val out = runBlocking { api.explain(prompt()) }

            assertThat(out.title).isEqualTo("Foco")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `erro 500 vira IOException`() {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(0), 0)
        server.createContext("/api/v1/coach/explain") { exchange ->
            val res = """{"error":"x"}""".toByteArray()
            exchange.sendResponseHeaders(500, res.size.toLong())
            exchange.responseBody.use { it.write(res) }
        }
        server.start()
        try {
            val api = HttpCoachApi(
                baseUrl = "http://localhost:${server.address.port}",
                token = "t",
            )
            assertThatThrownBy {
                kotlinx.coroutines.runBlocking { api.explain(prompt()) }
            }.isInstanceOf(IOException::class.java)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `review usa o endpoint review com payload da semana`() {
        val inner = """{"title":"Semana passada","body":"3 de 4 sessões, 75% do volume."}"""
        val escaped = inner.replace("\\", "\\\\").replace("\"", "\\\"")
        var received = ""
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(0), 0)
        server.createContext("/api/v1/coach/review") { exchange ->
            received = exchange.requestBody.bufferedReader().readText()
            val res = """{"choices":[{"message":{"content":"$escaped"}}]}""".toByteArray()
            exchange.sendResponseHeaders(200, res.size.toLong())
            exchange.responseBody.use { it.write(res) }
        }
        server.start()
        try {
            val api = HttpCoachApi(baseUrl = "http://localhost:${server.address.port}", token = "t")
            val out = runBlocking { api.review(reviewPrompt()) }

            assertThat(out.title).isEqualTo("Semana passada")
            assertThat(received).contains("\"weekStart\":\"2026-11-09\"")
            assertThat(received).contains("\"planned\":4")
            assertThat(received).contains("\"percentOfPlan\":75")
            assertThat(received).doesNotContain("Rafael")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `freeAnswer usa o endpoint free-answer com intent e slots, sem texto digitado`() {
        val inner = """{"title":"Entendido","body":"Fechado, mexo só quinta e sexta."}"""
        val escaped = inner.replace("\\", "\\\\").replace("\"", "\\\"")
        var received = ""
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(0), 0)
        server.createContext("/api/v1/coach/free-answer") { exchange ->
            received = exchange.requestBody.bufferedReader().readText()
            val res = """{"choices":[{"message":{"content":"$escaped"}}]}""".toByteArray()
            exchange.sendResponseHeaders(200, res.size.toLong())
            exchange.responseBody.use { it.write(res) }
        }
        server.start()
        try {
            val api = HttpCoachApi(baseUrl = "http://localhost:${server.address.port}", token = "t")
            val out = runBlocking {
                api.freeAnswer(
                    CoachFreeAnswerPrompt(
                        inputsHash = "coach-free-v1|hash",
                        intent = FreeInputIntent.REQUEST_ADJUST,
                        dayOfWeek = DayOfWeek.THURSDAY,
                        minutes = 30,
                        allowedMinutes = setOf(30, 40),
                        allowedPercents = setOf(80, 20),
                        allowedDistancesKm = setOf(8.0),
                        engineContext = "Semana base com 3 treinos",
                    ),
                )
            }

            assertThat(out.title).isEqualTo("Entendido")
            assertThat(received).contains("\"intent\":\"REQUEST_ADJUST\"")
            assertThat(received).contains("\"dayOfWeek\":\"THURSDAY\"")
            assertThat(received).contains("\"minutes\":30")
            assertThat(received).contains("\"inputsHash\":\"coach-free-v1|hash\"")
            assertThat(received).doesNotContain("so tenho")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `parse rejeita body que nao e texto`() {
        assertThatThrownBy {
            HttpCoachApi.parse("""{"title":"Coach","body":{"":": "Semana de 09/11 a 15/11."}}""")
        }.isInstanceOf(IOException::class.java)
    }

    @Test
    fun `parse rejeita payload sem title ou body`() {
        assertThatThrownBy {
            HttpCoachApi.parse("""{"body":"Semana de 09/11 a 15/11."}""")
        }.isInstanceOf(IOException::class.java)
        assertThatThrownBy {
            HttpCoachApi.parse("""{"title":"Coach","body":"   "}""")
        }.isInstanceOf(IOException::class.java)
    }

    @Test
    fun `parse rejeita json invalido em vez de exibir texto cru`() {
        assertThatThrownBy {
            HttpCoachApi.parse("isso não é json")
        }.isInstanceOf(IOException::class.java)
    }

    private fun reviewPrompt() = CoachReviewPrompt(
        inputsHash = "coach-review-v1|hash",
        locale = "pt-BR",
        weekStart = "2026-11-09",
        weekEnd = "2026-11-15",
        status = "AVAILABLE",
        adherence = CoachReviewAdherence(
            planned = 4,
            done = 3,
            verified = 2,
            manual = 1,
            legacy = 0,
            skipped = 1,
            percent = 75,
        ),
        volume = CoachReviewVolume(plannedMin = 120, executedMin = 90, extraMin = 10, percentOfPlan = 75),
        quality = null,
        readiness = null,
        sleep = null,
        intensity = null,
        observations = listOf("ADHERENCE_PARTIAL"),
        ruleIds = listOf("R-REVIEW-01"),
        confidence = "MEDIUM",
    )
}
