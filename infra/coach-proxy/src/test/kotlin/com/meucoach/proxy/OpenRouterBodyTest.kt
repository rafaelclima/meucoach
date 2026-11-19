package com.meucoach.proxy

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression test: the OpenRouter body must stay valid JSON even though the
 * system prompts contain inner `{"title":...}` literals. Manual string
 * interpolation used to emit them unescaped, so OpenRouter rejected every
 * explain/replan-suggest with 400 while review (no inner quotes) worked.
 */
class OpenRouterBodyTest {
    @Test
    fun `explain body is valid JSON with quotes preserved`() {
        val payload = "{\"rationale\":\"Semana com \\\"aspas\\\" e quebra\"}"

        val body = buildOpenRouterBody("some-model:free", "explain", payload)
        val parsed = Json.parseToJsonElement(body).jsonObject

        assertEquals("some-model:free", parsed["model"]!!.jsonPrimitive.content)
        val messages = parsed["messages"]!!.jsonArray
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        val system = messages[0].jsonObject["content"]!!.jsonPrimitive.content
        assertTrue(system.contains("\"title\""), "system prompt must survive the round-trip")
        assertEquals(payload, messages[1].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals(
            "json_object",
            parsed["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `replan body is valid JSON`() {
        val body = buildOpenRouterBody("some-model:free", "replan-suggest", "{\"days\":3}")
        val parsed = Json.parseToJsonElement(body).jsonObject

        assertEquals(2, parsed["messages"]!!.jsonArray.size)
    }

    @Test
    fun `review body is valid JSON`() {
        val body = buildOpenRouterBody("some-model:free", "review", "{\"weekStart\":\"2026-11-09\"}")
        Json.parseToJsonElement(body)
    }

    @Test
    fun `system prompts forbid internal codes`() {
        for (kind in listOf("explain", "review")) {
            val body = buildOpenRouterBody("some-model:free", kind, "{}")
            val system = Json.parseToJsonElement(body)
                .jsonObject["messages"]!!.jsonArray[0]
                .jsonObject["content"]!!.jsonPrimitive.content

            assertTrue(
                system.contains("codigos internos"),
                "$kind system prompt must forbid echoing engine codes",
            )
        }
    }
}
