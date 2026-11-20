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
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

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
    val goUrl = System.getenv("COACH_GO_URL")?.takeIf { it.isNotBlank() }
        ?: "https://opencode.ai/zen/go/v1/chat/completions"
    val goKey = System.getenv("COACH_GO_API_KEY").orEmpty()
    val goModels = System.getenv("COACH_GO_MODELS")?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.takeIf { it.isNotEmpty() }
        ?: DEFAULT_GO_MODELS
    require(openRouterKey.isNotBlank() || goKey.isNotBlank()) {
        "OPENROUTER_API_KEY or COACH_GO_API_KEY required"
    }
    require(proxyToken.isNotBlank()) { "COACH_PROXY_TOKEN required" }
    val chain = buildChain(openRouterKey, models, goUrl, goKey, goModels)

    val upstream = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            requestTimeoutMillis = System.getenv("COACH_UPSTREAM_TIMEOUT_MS")?.toLongOrNull()
                ?: System.getenv("OPENROUTER_TIMEOUT_MS")?.toLongOrNull()
                ?: 15000
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
                    val upstream = tryModels(upstream, chain, kind, payload)
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

/**
 * OpenRouter request body built with kotlinx.serialization so system prompts
 * and payloads containing quotes/newlines can never break the JSON.
 * Manual string interpolation used to emit the inner `{"title":...}` of the
 * system prompts unescaped, which OpenRouter rejected with 400 for explain
 * while review (no inner quotes) kept working.
 */
internal fun buildOpenRouterBody(model: String, kind: String, payload: String): String {
    val system = when (kind) {
        "review" -> SYSTEM_REVIEW
        "replan-suggest" -> SYSTEM_REPLAN
        else -> SYSTEM_EXPLAIN
    }
    return buildJsonObject {
        put("model", model)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "system")
                put("content", system)
            }
            addJsonObject {
                put("role", "user")
                put("content", payload)
            }
        }
        putJsonObject("response_format") {
            put("type", "json_object")
        }
    }.toString()
}

private const val SYSTEM_EXPLAIN =
    "Voce e o Coach do Rafael, treinador de corrida, falando no WhatsApp com ele, pt-BR, sem culpa. " +
        "Reescreva apenas o rationale do motor. Nunca invente pace, FC, carga, volume, VDOT ou ACWR. " +
        "Fale em 2a pessoa (voce), curto como mensagem de WhatsApp: no maximo 3 mensagens, cada uma com " +
        "no maximo 140 caracteres. Primeira mensagem: o que importa (fato + motivo). Segunda: detalhe, " +
        "so se precisar. Ultima: sempre uma pergunta ou acao tocavel (Bora? Fechado? Confere aqui?). " +
        "Nunca cite nomes ou siglas de metricas (ACWR, VDOT, TSB, CTL, ATL, TRIMP, rTSS, LTHR); " +
        "fale de carga e recuperacao em linguagem simples. " +
        "Nunca mostre codigos internos do motor (tipos de observacao em CAIXA_ALTA com underline, " +
        "ids como R-REVIEW-01 ou EV-SEILER-2010); use sempre texto corrido em pt-BR. " +
        "Nunca diga o motor, o resumo ou a revisao em 3a pessoa: voce e o Coach falando com o Rafael. " +
        "Dado ausente e null: diga que faltam dados. Estimativa permitida so textual e marcada como estimativa. " +
        "Sem diagnostico, sem musculacao, sem nutricao (exceto hidratacao estatica). " +
        "Responda em JSON {\"title\":\"string curta\",\"body\":\"string em pt-BR\",\"bullets\":[\"string\"]}. " +
        "title e a primeira mensagem, body a continuacao em ate 2 paragrafos curtos, bullets ate 3 anexos curtos. " +
        "title e body devem ser strings; body nunca pode ser objeto ou JSON serializado."
private const val SYSTEM_REVIEW =
    "Voce e o Coach do Rafael, treinador de corrida, falando no WhatsApp com ele, pt-BR, sem culpa. " +
        "Voce resume a semana anterior a partir do planejado x executado. " +
        "Mesmas regras do explain, sem numeros novos. " +
        "Comece pelo feito (ex.: 3 de 4), cite o longo e a qualidade sem culpar por treino perdido, e feche " +
        "sem prometer compensacao. No maximo 3 mensagens curtas (140 caracteres cada), ultima sempre com " +
        "pergunta ou acao. Nunca mostre codigos internos do motor (tipos de observacao em CAIXA_ALTA " +
        "com underline, ids como R-REVIEW-01 ou EV-SEILER-2010); use sempre texto corrido em pt-BR. " +
        "Use o mesmo schema: title e body como strings; body nunca como objeto."
private const val SYSTEM_REPLAN =
    "Voce sugere slots de replanejo a partir do plano vigente. Nunca crie treino forte novo; so reorganize o futuro. " +
        "Responda em JSON {\"title\":\"string\",\"body\":\"string\",\"bullets\":[\"string\"],\"slots\":{}} " +
        "para confirmacao humana; title e body sao strings, nunca objetos."

/**
 * Upstream targets in failover order. Go (assinatura) primeiro; OpenRouter
 * free como fallback. Cada alvo só entra na cadeia com chave presente, então
 * sem `COACH_GO_API_KEY` o comportamento é o de antes (só OpenRouter).
 */
data class UpstreamTarget(
    val label: String,
    val url: String,
    val key: String,
    val models: List<String>,
    val userAgent: String? = null,
)

internal fun buildChain(
    openRouterKey: String,
    openRouterModels: List<String>,
    goUrl: String,
    goKey: String,
    goModels: List<String>,
): List<UpstreamTarget> = buildList {
    if (goKey.isNotBlank() && goModels.isNotEmpty()) {
        add(UpstreamTarget("go", goUrl, goKey, goModels, userAgent = "MeuCoach-Coach/1.0"))
    }
    if (openRouterKey.isNotBlank() && openRouterModels.isNotEmpty()) {
        add(UpstreamTarget("openrouter", OPENROUTER_URL, openRouterKey, openRouterModels))
    }
}

/**
 * Sessão estável por conversa, exigida pelo roteamento do Go
 * (`x-opencode-session`; sem ela o Go devolve 400 MissingSessionID).
 * Deriva do `inputsHash` do prompt — mesmo conteúdo, mesma sessão
 * (bom para o cache deles); nunca carrega dado de saúde, só o hash.
 */
internal fun sessionIdFor(kind: String, payload: String): String {
    val hash = runCatching {
        Json.parseToJsonElement(payload).jsonObject["inputsHash"]
            ?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    }.getOrNull()
    return "meucoach-coach-$kind-${hash ?: "unknown"}"
}

private const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"

/**
 * Default OpenRouter chain. `openrouter/free` is the unified free router; the
 * pinned fallbacks must be live `:free` ids (see https://openrouter.ai/api/v1/models —
 * free ids rotate and dead ones 404, e.g. inkling-2-small was retired).
 */
internal val DEFAULT_MODELS = listOf(
    "openrouter/free",
    "nvidia/nemotron-3.5-super-vl:free",
    "google/gemma-4-31b-it:free",
)

/** Modelo padrão do Go: maior cota ($100/mês) e menor preço entre os viáveis. */
internal val DEFAULT_GO_MODELS = listOf("deepseek-v4.1-flash")

/**
 * Tenta os alvos em ordem (Go, depois OpenRouter) e dentro de cada alvo os
 * modelos em ordem; devolve o primeiro 2xx.
 * 429/5xx/timeout e 4xx passam para o próximo alvo/modelo; se a cadeia
 * esgotar, devolve o primeiro erro "duro" (400/401/402/422) para diagnóstico
 * — uma chave ruim num provedor não pode vetar o outro. Sem log de payload.
 */
private suspend fun tryModels(
    client: HttpClient,
    chain: List<UpstreamTarget>,
    kind: String,
    payload: String,
): Pair<String, HttpStatusCode>? {
    var hardError: Pair<String, HttpStatusCode>? = null
    val session = sessionIdFor(kind, payload)
    for (target in chain) {
        for (model in target.models) {
            try {
                val res = client.post(target.url) {
                    header(HttpHeaders.Authorization, "Bearer ${target.key}")
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("HTTP-Referer", "https://meucoach.app")
                    header("X-Title", "MeuCoach Coach")
                    header("x-opencode-session", session)
                    target.userAgent?.let { header(HttpHeaders.UserAgent, it) }
                    setBody(buildOpenRouterBody(model, kind, payload))
                }
                if (res.status.value in 200..299) return res.bodyAsText() to res.status
                println("coach upstream=${target.label} model=$model status=${res.status.value} (trying next)")
                // 400/401/402/422 são problema do pedido ou da conta naquele
                // provedor: guarda o primeiro para diagnóstico e segue a cadeia.
                if (hardError == null && res.status.value in listOf(400, 401, 402, 422)) {
                    hardError = res.bodyAsText() to res.status
                }
            } catch (e: Exception) {
                println("coach upstream=${target.label} model=$model error=${e.message} (trying next)")
            }
        }
    }
    // Cadeia esgotada: prefere o erro duro (diagnóstico real) ao 502 genérico.
    return hardError
}
