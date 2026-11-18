package com.meucoach.core.data.coach

import com.meucoach.core.domain.coach.CoachPrompt
import com.meucoach.core.domain.coach.CoachSessionSummary
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.io.IOException

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
}
