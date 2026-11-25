package com.meucoach.proxy

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The two preferred free-answer models must run together: a slow MiMo cannot
 * hold the response while a fast GLM already has a 2xx. Sequential behavior
 * (MiMo first, 1.5 s) would miss the <1 s assertion.
 */
class FreeAnswerRaceTest {
    @Test
    fun `free-answer races mimo and glm and keeps the first 2xx`() = runBlocking {
        val executor = Executors.newFixedThreadPool(4) { runnable ->
            Thread(runnable, "coach-test-http").apply { isDaemon = true }
        }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = executor
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.readBytes().decodeToString()
            val mimo = body.contains("mimo-v2.6-flash")
            Thread.sleep(if (mimo) 1_500 else 50)
            val payload = if (mimo) """{"model":"mimo"}""" else """{"model":"glm"}"""
            val bytes = payload.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client = HttpClient(CIO) { install(HttpTimeout) }
            val target = UpstreamTarget(
                label = "go",
                url = "http://127.0.0.1:${server.address.port}/",
                key = "key",
                models = listOf("mimo-v2.6-flash", "glm-5.3-flash"),
            )
            val started = System.nanoTime()
            val result = tryModels(
                client = client,
                chain = listOf(target),
                kind = "free-answer",
                payload = "{}",
                attemptCapMs = 5_000,
                totalBudgetMs = 10_000,
            )
            val elapsedMs = (System.nanoTime() - started) / 1_000_000

            assertNotNull(result, "the fast model must produce a 2xx")
            assertEquals(200, result.second.value)
            assertTrue(result.first.contains("glm"), "the fast model must win: ${result.first}")
            assertTrue(elapsedMs < 1_000, "models did not run in parallel: ${elapsedMs}ms")
        } finally {
            server.stop(0)
            executor.shutdownNow()
        }
    }
}
