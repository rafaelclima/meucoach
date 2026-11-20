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

    @Test
    fun `system prompts speak as a chat coach`() {
        for (kind in listOf("explain", "review")) {
            val body = buildOpenRouterBody("some-model:free", kind, "{}")
            val system = Json.parseToJsonElement(body)
                .jsonObject["messages"]!!.jsonArray[0]
                .jsonObject["content"]!!.jsonPrimitive.content

            assertTrue(system.contains("WhatsApp"), "$kind must frame the voice as a WhatsApp chat")
            assertTrue(system.contains("140"), "$kind must cap each message at 140 chars")
            assertTrue(system.contains("pergunta ou acao"), "$kind must close with a question or action")
        }
    }

    @Test
    fun `default chain routes free first with live fallbacks`() {
        assertEquals("openrouter/free", DEFAULT_MODELS.first())
        assertTrue(DEFAULT_MODELS.size >= 2, "chain needs fallbacks when the router stalls")
        assertTrue(
            DEFAULT_MODELS.none { it.contains("inkling") },
            "inkling-2-small was retired by OpenRouter (404); pin a live :free id",
        )
        assertTrue(DEFAULT_MODELS.all { it.endsWith(":free") || it == "openrouter/free" })
    }

    @Test
    fun `default go url follows the console docs`() {
        assertEquals(
            "https://opencode.ai/inference/go/openai/v1/chat/completions",
            DEFAULT_GO_URL,
        )
    }

    @Test
    fun `go goes first when its key is present`() {
        val chain = buildChain(
            openRouterKey = "or-key",
            openRouterModels = listOf("openrouter/free"),
            goUrl = DEFAULT_GO_URL,
            goKey = "go-key",
            goModels = listOf("deepseek-v4.1-flash"),
        )

        assertEquals(listOf("go", "openrouter"), chain.map { it.label })
        assertEquals("deepseek-v4.1-flash", chain.first().models.first())
    }

    @Test
    fun `chain degrades to openrouter only without a go key`() {
        val chain = buildChain(
            openRouterKey = "or-key",
            openRouterModels = listOf("openrouter/free"),
            goUrl = DEFAULT_GO_URL,
            goKey = "",
            goModels = listOf("deepseek-v4.1-flash"),
        )

        assertEquals(listOf("openrouter"), chain.map { it.label })
    }

    @Test
    fun `chain can run on go only`() {
        val chain = buildChain(
            openRouterKey = "",
            openRouterModels = emptyList(),
            goUrl = DEFAULT_GO_URL,
            goKey = "go-key",
            goModels = listOf("deepseek-v4.1-flash"),
        )

        assertEquals(listOf("go"), chain.map { it.label })
    }

    @Test
    fun `chain is empty without any key`() {
        val chain = buildChain(
            openRouterKey = "",
            openRouterModels = listOf("openrouter/free"),
            goUrl = DEFAULT_GO_URL,
            goKey = "",
            goModels = listOf("deepseek-v4.1-flash"),
        )

        assertTrue(chain.isEmpty())
    }

    @Test
    fun `session id derives from the prompt hash`() {
        val id = sessionIdFor("explain", """{"inputsHash":"abc123","foo":1}""")

        assertEquals("meucoach-coach-explain-abc123", id)
    }

    @Test
    fun `session id falls back without a hash`() {
        assertEquals("meucoach-coach-review-unknown", sessionIdFor("review", "{}"))
        assertEquals("meucoach-coach-review-unknown", sessionIdFor("review", "not-json"))
    }
}
