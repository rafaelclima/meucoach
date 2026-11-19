package com.meucoach.core.data.coach

import com.meucoach.core.domain.coach.CoachPrompt
import com.meucoach.core.domain.coach.CoachRawResponse
import com.meucoach.core.domain.coach.CoachReviewAdherence
import com.meucoach.core.domain.coach.CoachReviewIntensity
import com.meucoach.core.domain.coach.CoachReviewPrompt
import com.meucoach.core.domain.coach.CoachReviewQuality
import com.meucoach.core.domain.coach.CoachReviewReadiness
import com.meucoach.core.domain.coach.CoachReviewSleep
import com.meucoach.core.domain.coach.CoachReviewVolume
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

    @Throws(IOException::class)
    suspend fun review(prompt: CoachReviewPrompt): CoachRawResponse
}

class DisabledCoachApi : CoachApi {
    override suspend fun explain(prompt: CoachPrompt, kind: CoachKind): CoachRawResponse =
        throw IOException("coach disabled")

    override suspend fun review(prompt: CoachReviewPrompt): CoachRawResponse = throw IOException("coach disabled")
}

class FakeCoachApi(var response: CoachRawResponse? = null, var error: IOException? = null) : CoachApi {
    var calls = 0
        private set
    var reviewCalls = 0
        private set

    override suspend fun explain(prompt: CoachPrompt, kind: CoachKind): CoachRawResponse {
        calls += 1
        error?.let { throw it }
        return response ?: CoachRawResponse(title = "Coach", body = "Resposta de teste.")
    }

    override suspend fun review(prompt: CoachReviewPrompt): CoachRawResponse {
        reviewCalls += 1
        error?.let { throw it }
        return response ?: CoachRawResponse(title = "Coach", body = "Resposta de teste.")
    }
}

class HttpCoachApi(private val baseUrl: String, private val token: String, private val timeoutMs: Int = 45_000) :
    CoachApi {
    override suspend fun explain(prompt: CoachPrompt, kind: CoachKind): CoachRawResponse =
        post(kind.path, payload(prompt))

    override suspend fun review(prompt: CoachReviewPrompt): CoachRawResponse =
        post(CoachKind.REVIEW.path, payload(prompt))

    private suspend fun post(path: String, json: String): CoachRawResponse = withContext(Dispatchers.IO) {
        require(baseUrl.isNotBlank()) { "Coach base URL must not be blank" }
        val url = java.net.URL(baseUrl.trimEnd('/') + "/api/v1/coach/" + path)
        val connection = (url.openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = minOf(timeoutMs, 10_000)
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Coach-Token", token)
        }
        try {
            connection.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()
            if (code !in 200..299) throw IOException("coach upstream $code: ${body.take(200)}")
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

    private fun payload(prompt: CoachReviewPrompt): String = JSONObject()
        .put("inputsHash", prompt.inputsHash)
        .put("locale", prompt.locale)
        .put("weekStart", prompt.weekStart)
        .put("weekEnd", prompt.weekEnd)
        .put("status", prompt.status)
        .put("adherence", prompt.adherence?.toJson() ?: JSONObject.NULL)
        .put("volume", prompt.volume?.toJson() ?: JSONObject.NULL)
        .put("quality", prompt.quality?.toJson() ?: JSONObject.NULL)
        .put("readiness", prompt.readiness?.toJson() ?: JSONObject.NULL)
        .put("sleep", prompt.sleep?.toJson() ?: JSONObject.NULL)
        .put("intensity", prompt.intensity?.toJson() ?: JSONObject.NULL)
        .put("observations", prompt.observations)
        .put("ruleIds", prompt.ruleIds)
        .put("confidence", prompt.confidence)
        .toString()

    private fun CoachReviewAdherence.toJson(): JSONObject = JSONObject()
        .put("planned", planned)
        .put("done", done)
        .put("verified", verified)
        .put("manual", manual)
        .put("legacy", legacy)
        .put("skipped", skipped)
        .put("percent", percent ?: JSONObject.NULL)

    private fun CoachReviewVolume.toJson(): JSONObject = JSONObject()
        .put("plannedMin", plannedMin ?: JSONObject.NULL)
        .put("executedMin", executedMin ?: JSONObject.NULL)
        .put("extraMin", extraMin ?: JSONObject.NULL)
        .put("percentOfPlan", percentOfPlan ?: JSONObject.NULL)

    private fun CoachReviewQuality.toJson(): JSONObject = JSONObject()
        .put("planned", planned)
        .put("kept", kept)
        .put("downgraded", downgraded)

    private fun CoachReviewReadiness.toJson(): JSONObject = JSONObject()
        .put("measuredDays", measuredDays)
        .put("averageScore", averageScore ?: JSONObject.NULL)
        .put("redDays", redDays)

    private fun CoachReviewSleep.toJson(): JSONObject = JSONObject()
        .put("measuredNights", measuredNights)
        .put("averageMinutes", averageMinutes ?: JSONObject.NULL)
        .put("shortNights", shortNights)

    private fun CoachReviewIntensity.toJson(): JSONObject = JSONObject()
        .put("easyPercent", easyPercent)
        .put("sessions", sessions)

    companion object {
        /**
         * Contract of the proxy: `{title, body, bullets[]}` with `title`/`body`
         * as non-blank strings. Anything else is a schema violation and falls
         * back to the deterministic engine copy instead of being persisted.
         */
        fun parse(body: String): CoachRawResponse = try {
            val outer = JSONObject(body)
            val innerText = if (outer.has("choices")) {
                outer.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            } else {
                body
            }
            val inner = JSONObject(innerText)
            val title = inner.opt("title") as? String
            val text = inner.opt("body") as? String
            if (title.isNullOrBlank() || text.isNullOrBlank()) {
                throw IOException("coach response missing title/body")
            }
            val bullets = inner.optJSONArray("bullets")?.let { array ->
                List(array.length()) { index -> array.opt(index) as? String }
                    .filterNotNull()
                    .filter { it.isNotBlank() }
            } ?: emptyList()
            val action = inner.opt("suggestedAction") as? String
            CoachRawResponse(
                title = title,
                body = text,
                bullets = bullets,
                suggestedAction = action?.takeIf { it.isNotBlank() },
            )
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("coach invalid response", e)
        }
    }
}
