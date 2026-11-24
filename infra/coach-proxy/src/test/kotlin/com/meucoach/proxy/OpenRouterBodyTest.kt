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
                system.contains("códigos internos"),
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
            assertTrue(system.contains("160"), "$kind must cap each message at 160 chars")
            assertTrue(system.contains("pergunta ou ação"), "$kind must close with a question or action")
            assertTrue(system.contains("acentuação correta"), "$kind must ask for correct pt-BR accents")
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
    fun `default go chain tries the stable model first with mimo as backup`() {
        assertEquals(listOf("glm-5.3-flash", "mimo-v2.6-flash", "deepseek-v4.1-flash"), DEFAULT_GO_MODELS)
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
    fun `free-answer body is valid JSON`() {
        val body = buildOpenRouterBody("some-model:free", "free-answer", "{\"intent\":\"ASK_WEEK\"}")
        val parsed = Json.parseToJsonElement(body).jsonObject

        assertEquals(2, parsed["messages"]!!.jsonArray.size)
        assertEquals(
            "json_object",
            parsed["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `free-answer has its own system prompt driven by intent and question`() {
        val free = buildOpenRouterBody("some-model:free", "free-answer", "{}")
        val explain = buildOpenRouterBody("some-model:free", "explain", "{}")
        fun system(body: String) = Json.parseToJsonElement(body)
            .jsonObject["messages"]!!.jsonArray[0]
            .jsonObject["content"]!!.jsonPrimitive.content

        assertTrue(system(free).contains("intent"), "free-answer must read intent + slots")
        assertTrue(system(free).contains("`question`"), "free-answer must answer the typed question")
        assertTrue(system(free).contains("dossiê"), "free-answer must answer only from engine facts")
        assertTrue(system(free) != system(explain), "free-answer must not reuse the explain prompt")
    }

    @Test
    fun `free-answer speaks as a chat coach`() {
        val body = buildOpenRouterBody("some-model:free", "free-answer", "{}")
        val system = Json.parseToJsonElement(body)
            .jsonObject["messages"]!!.jsonArray[0]
            .jsonObject["content"]!!.jsonPrimitive.content

        assertTrue(system.contains("WhatsApp"), "free-answer must frame the voice as a WhatsApp chat")
        assertTrue(system.contains("160"), "free-answer must cap each message at 160 chars")
        assertTrue(system.contains("pergunta ou ação"), "free-answer must close with a question or action")
        assertTrue(system.contains("códigos internos"), "free-answer must forbid echoing engine codes")
    }

    @Test
    fun `free-answer speaks like a street coach, not a spreadsheet`() {
        val body = buildOpenRouterBody("some-model:free", "free-answer", "{}")
        val system = Json.parseToJsonElement(body)
            .jsonObject["messages"]!!.jsonArray[0]
            .jsonObject["content"]!!.jsonPrimitive.content

        assertTrue(system.contains("conversável"), "free-answer must ban the robotic easy-talk word")
        assertTrue(system.contains("3ª pessoa"), "free-answer must talk to the athlete, not about him")
        assertTrue(system.contains("Combinado?"), "free-answer must vary the closing question")
        assertTrue(system.contains("sono"), "free-answer must explain sleep instead of quoting it")
    }

    @Test
    fun `free-answer forbids diagnosis and prescription`() {
        val body = buildOpenRouterBody("some-model:free", "free-answer", "{}")
        val system = Json.parseToJsonElement(body)
            .jsonObject["messages"]!!.jsonArray[0]
            .jsonObject["content"]!!.jsonPrimitive.content

        assertTrue(system.contains("diagnostique"), "free-answer must never diagnose")
        assertTrue(system.contains("prescreva"), "free-answer must never prescribe a new workout")
    }

    @Test
    fun `system prompts keep runner talk and forbid taper`() {
        for (kind in listOf("explain", "review", "free-answer")) {
            val body = buildOpenRouterBody("some-model:free", kind, "{}")
            val system = Json.parseToJsonElement(body)
                .jsonObject["messages"]!!.jsonArray[0]
                .jsonObject["content"]!!.jsonPrimitive.content

            assertTrue(system.contains("rodagem"), "$kind must keep runner talk for easy runs")
            assertTrue(system.contains("micro-dose"), "$kind must forbid micro-dose")
        }
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
