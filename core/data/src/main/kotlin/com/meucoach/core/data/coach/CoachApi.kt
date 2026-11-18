package com.meucoach.core.data.coach

import com.meucoach.core.domain.coach.CoachPrompt
import com.meucoach.core.domain.coach.CoachRawResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

enum class CoachKind(val path: String) {
    EXPLAIN("explain"),
    REVIEW("review"),
    REPLAN_SUGGEST("replan-suggest"),
}

interface CoachApi {
    @Throws(IOException::class)
    suspend fun explain(prompt: CoachPrompt, kind: CoachKind = CoachKind.EXPLAIN): CoachRawResponse
}

class DisabledCoachApi : CoachApi {
    override suspend fun explain(prompt: CoachPrompt, kind: CoachKind): CoachRawResponse =
        throw IOException("coach disabled")
}

class FakeCoachApi(private val response: CoachRawResponse? = null, private val error: IOException? = null) : CoachApi {
    override suspend fun explain(prompt: CoachPrompt, kind: CoachKind): CoachRawResponse {
        error?.let { throw it }
        return response ?: CoachRawResponse(title = "Coach", body = "Resposta de teste.")
    }
}

class HttpCoachApi(private val baseUrl: String, private val token: String, private val timeoutMs: Int = 45_000) :
    CoachApi {
    override suspend fun explain(prompt: CoachPrompt, kind: CoachKind): CoachRawResponse = withContext(Dispatchers.IO) {
        require(baseUrl.isNotBlank()) { "Coach base URL must not be blank" }
        val url = java.net.URL(baseUrl.trimEnd('/') + "/api/v1/coach/" + kind.path)
        val connection = (url.openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = minOf(timeoutMs, 10_000)
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Coach-Token", token)
        }
        try {
            connection.outputStream.use { it.write(payload(prompt).toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()
            if (code !in 200..299) throw IOException("coach upstream $code")
            parse(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun payload(prompt: CoachPrompt): String = JSONObject()
        .put("engineVersion", prompt.engineVersion)
        .put("inputsHash", prompt.inputsHash)
        .put("locale", prompt.locale)
        .put("weeklyMinutes", prompt.weeklyMinutes)
        .put("executedMinutes", prompt.executedMinutes ?: JSONObject.NULL)
        .put("adherenceRatio", prompt.adherenceRatio ?: JSONObject.NULL)
        .put(
            "sessions",
            prompt.sessions.map {
                JSONObject()
                    .put("date", it.date)
                    .put("type", it.type)
                    .put("durationMin", it.durationMin ?: JSONObject.NULL)
                    .put("status", it.status)
            },
        )
        .put("rationale", prompt.rationale)
        .put("ruleIds", prompt.ruleIds)
        .put("confidence", prompt.confidence)
        .toString()

    companion object {
        fun parse(body: String): CoachRawResponse {
            val outer = JSONObject(body)
            val innerText = if (outer.has("choices")) {
                outer.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            } else {
                body
            }
            return try {
                val inner = JSONObject(innerText)
                CoachRawResponse(
                    title = inner.optString("title", "Coach"),
                    body = inner.optString("body", innerText),
                    bullets = inner.optJSONArray("bullets")?.let { arr ->
                        List(arr.length()) { i -> arr.optString(i) }
                    } ?: emptyList(),
                    suggestedAction = inner.optString("suggestedAction", null)?.takeIf { it.isNotBlank() },
                )
            } catch (e: Exception) {
                CoachRawResponse(title = "Coach", body = innerText.take(1000))
            }
        }
    }
}
