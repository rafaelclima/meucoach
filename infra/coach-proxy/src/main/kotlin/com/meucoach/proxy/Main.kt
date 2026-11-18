package com.meucoach.proxy

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val openRouterKey = System.getenv("OPENROUTER_API_KEY").orEmpty()
    val proxyToken = System.getenv("COACH_PROXY_TOKEN").orEmpty()
    val models = System.getenv("OPENROUTER_MODELS")?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.takeIf { it.isNotEmpty() }
        ?: System.getenv("OPENROUTER_MODEL")?.takeIf { it.isNotBlank() }?.let(::listOf)
        ?: DEFAULT_MODELS
    require(openRouterKey.isNotBlank()) { "OPENROUTER_API_KEY required" }
    require(proxyToken.isNotBlank()) { "COACH_PROXY_TOKEN required" }

    val upstream = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            requestTimeoutMillis = System.getenv("OPENROUTER_TIMEOUT_MS")?.toLongOrNull() ?: 15000
        }
    }

    embeddedServer(Netty, port = port) {
        routing {
            get("/health") { call.respondText("ok", ContentType.Text.Plain) }
            post("/api/v1/coach/{kind}") {
                if (call.request.headers["X-Coach-Token"] != proxyToken) {
                    call.respondText("unauthorized", status = HttpStatusCode.Unauthorized)
                    return@post
                }
                val kind = call.parameters["kind"] ?: "explain"
                val payload = call.receiveText()
                // F1: repasse mínimo com system prompt de guardrails.
                // Sem log de payload (só status/latência no Dokploy).
                val started = System.currentTimeMillis()
                try {
                    val upstream = tryModels(upstream, models, openRouterKey, kind, payload)
                    if (upstream == null) {
                        call.respondText(
                            """{"error":"all_models_unavailable"}""",
                            ContentType.Application.Json,
                            HttpStatusCode.BadGateway,
                        )
                    } else {
                        call.respondText(
                            upstream.first,
                            ContentType.Application.Json,
                            upstream.second,
                        )
                    }
                } finally {
                    // Intencional: sem log de payload.
                    println("coach kind=$kind latencyMs=${System.currentTimeMillis() - started}")
                }
            }
        }
    }.start(wait = true)
}

private fun buildOpenRouterBody(model: String, kind: String, payload: String): String {
    val escaped = payload.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    val system = when (kind) {
        "review" -> SYSTEM_REVIEW
        "replan-suggest" -> SYSTEM_REPLAN
        else -> SYSTEM_EXPLAIN
    }
    val body = """{"model":"$model","messages":""" +
        """[{"role":"system","content":"$system"},{"role":"user","content":"$escaped"}],""" +
        """"response_format":{"type":"json_object"}}"""
    return body
}

private const val SYSTEM_EXPLAIN =
    "Voce e o Coach MeuCoach, pt-BR, tom de treinador sem culpa. " +
        "Reescreva apenas o rationale do motor. Nunca invente pace, FC, carga, volume, VDOT ou ACWR. " +
        "Nunca cite nomes ou siglas de metricas (ACWR, VDOT, TSB, CTL, ATL, TRIMP, rTSS, LTHR); " +
        "fale de carga e recuperacao em linguagem simples. " +
        "Dado ausente e null: diga que faltam dados. Estimativa permitida so textual e marcada como estimativa. " +
        "Sem diagnostico, sem musculacao, sem nutricao (exceto hidratacao estatica). Responda em JSON {title, body, bullets[]}."
private const val SYSTEM_REVIEW =
    "Voce resume a semana anterior a partir do planejado x executado. Mesmas regras do explain, sem numeros novos."
private const val SYSTEM_REPLAN =
    "Voce sugere slots de replanejo a partir do plano vigente. Nunca crie treino forte novo; so reorganize o futuro. " +
        "Responda em JSON {title, body, bullets[], slots{}} para confirmacao humana."

private val DEFAULT_MODELS = listOf(
    "nvidia/nemotron-3-ultra-550b-a55b:free",
    "nvidia/nemotron-3.5-super-vl:free",
    "thinkingmachines/inkling-2-small:free",
)

/**
 * Tenta os modelos em ordem e devolve o primeiro 2xx.
 * 429/5xx/timeout passam para o próximo; outro 4xx retorna na hora
 * (erro do pedido, não do modelo). Sem log de payload.
 */
private suspend fun tryModels(
    client: HttpClient,
    models: List<String>,
    openRouterKey: String,
    kind: String,
    payload: String,
): Pair<String, HttpStatusCode>? {
    var last: Pair<String, HttpStatusCode>? = null
    for (model in models) {
        try {
            val res = client.post("https://openrouter.ai/api/v1/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $openRouterKey")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("HTTP-Referer", "https://meucoach.app")
                header("X-Title", "MeuCoach Coach")
                setBody(buildOpenRouterBody(model, kind, payload))
            }
            if (res.status.value in 200..299) return res.bodyAsText() to res.status
            last = res.bodyAsText() to res.status
            println("coach model=$model status=${res.status.value} (trying next)")
            if (res.status != HttpStatusCode.TooManyRequests && res.status.value < 500) return last
        } catch (e: Exception) {
            println("coach model=$model error=${e.message} (trying next)")
        }
    }
    return last?.takeIf { it.second != HttpStatusCode.TooManyRequests && it.second.value < 500 }
}
