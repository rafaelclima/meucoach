package com.meucoach.proxy

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression for the 24/11/2026 "sem conexão" report: the app cuts
 * `free-answer` at 25 s, so a slow upstream chain must be abandoned within
 * the proxy budget instead of waiting for an answer that will arrive late.
 */
class UpstreamBudgetTest {
    @Test
    fun `free answer chain stops at the total budget`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            Thread.sleep(3_000)
            exchange.sendResponseHeaders(200, 2)
            exchange.responseBody.use { it.write("{}".toByteArray()) }
        }
        server.start()
        try {
            val client = HttpClient(CIO) { install(HttpTimeout) }
            val target = UpstreamTarget(
                label = "test",
                url = "http://127.0.0.1:${server.address.port}/",
                key = "key",
                models = listOf("first", "second"),
            )
            val started = System.nanoTime()
            val result = tryModels(
                client = client,
                chain = listOf(target),
                kind = "free-answer",
                payload = "{}",
                attemptCapMs = 2_000,
                totalBudgetMs = 1_000,
            )
            val elapsedMs = (System.nanoTime() - started) / 1_000_000

            assertNull(result, "a slow upstream must not win after the budget")
            assertTrue(elapsedMs < 2_500, "chain ignored the budget: ${elapsedMs}ms")
        } finally {
            server.stop(0)
        }
    }
}
