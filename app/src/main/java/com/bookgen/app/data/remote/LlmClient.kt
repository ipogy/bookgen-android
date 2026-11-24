package com.bookgen.app.data.remote

import com.bookgen.app.domain.model.AiSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

interface LlmClient {
    suspend fun embedText(
        model: String,
        text: String,
        settings: AiSettings,
    ): List<Float>

    suspend fun generateText(
        model: String,
        systemInstruction: String,
        prompt: String,
        temperature: Double,
        settings: AiSettings,
    ): String

    fun streamText(
        model: String,
        systemInstruction: String,
        prompt: String,
        temperature: Double,
        settings: AiSettings,
    ): Flow<String>
}

class UnifiedLlmHttpClient(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val geminiBaseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
    private val openRouterUrl: String = "https://openrouter.ai/api/v1/chat/completions",
) : LlmClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun embedText(
        model: String,
        text: String,
        settings: AiSettings,
    ): List<Float> = withContext(ioDispatcher) {
        val apiKey = settings.geminiApiKey.trim()
        if (apiKey.isEmpty()) {
            throw IllegalStateException("Embedding には Gemini API キーが必要です")
        }
        val cleanModel = model.trim().removePrefix("models/").ifEmpty { AiSettings.DEFAULT_EMBEDDING_MODEL }
        val endpoint = "$geminiBaseUrl/models/$cleanModel:embedContent"
        val bodyJson = buildJsonObject {
            putJsonObject("content") {
                putJsonArray("parts") {
                    addJsonObject { put("text", text) }
                }
            }
        }

        HttpRetryPolicy.executeWithBackoff {
            val responseText = postJsonSync(
                urlString = endpoint,
                body = bodyJson.toString(),
                headers = mapOf("x-goog-api-key" to apiKey),
                connectTimeoutMs = 15_000,
                readTimeoutMs = 30_000,
            )
            val root = json.parseToJsonElement(responseText).jsonObject
            val values = root["embedding"]?.jsonObject?.get("values")?.jsonArray
                ?: throw EmptyLlmResponseException("Gemini Embedding の結果が空です")
            values.mapNotNull { it.jsonPrimitive.floatOrNull }.also { vec ->
                if (vec.isEmpty()) throw EmptyLlmResponseException("Gemini Embedding ベクトルが空です")
            }
        }
    }

    override suspend fun generateText(
        model: String,
        systemInstruction: String,
        prompt: String,
        temperature: Double,
        settings: AiSettings,
    ): String = withContext(ioDispatcher) {
        val useOpenRouter = shouldRouteOpenRouter(model, settings)
        HttpRetryPolicy.executeWithBackoff {
            val raw = if (useOpenRouter) {
                callOpenRouterSync(model, systemInstruction, prompt, temperature, settings)
            } else {
                callGeminiSync(model, systemInstruction, prompt, temperature, settings)
            }
            if (raw.isBlank()) {
                throw EmptyLlmResponseException("LLM から空の応答が返されました ($model)")
            }
            raw
        }
    }

    override fun streamText(
        model: String,
        systemInstruction: String,
        prompt: String,
        temperature: Double,
        settings: AiSettings,
    ): Flow<String> = channelFlow {
        val useOpenRouter = shouldRouteOpenRouter(model, settings)
        var emittedChars = 0

        HttpRetryPolicy.executeWithBackoff { attempt ->
            // Only retry stream before any token has been delivered OR restart cleanly if empty
            val collectedThisAttempt = StringBuilder()
            if (useOpenRouter) {
                streamOpenRouter(model, systemInstruction, prompt, temperature, settings) { chunk ->
                    collectedThisAttempt.append(chunk)
                    emittedChars += chunk.length
                    send(chunk)
                }
            } else {
                streamGemini(model, systemInstruction, prompt, temperature, settings) { chunk ->
                    collectedThisAttempt.append(chunk)
                    emittedChars += chunk.length
                    send(chunk)
                }
            }
            if (collectedThisAttempt.isEmpty() && emittedChars == 0) {
                throw EmptyLlmResponseException("SSE ストリーム (attempt=${attempt + 1}) が空のまま終了しました")
            }
        }
    }.flowOn(ioDispatcher)

    internal fun shouldRouteOpenRouter(model: String, settings: AiSettings): Boolean {
        val isSlashed = '/' in model
        return when {
            isSlashed -> true
            !settings.hasGeminiKey() && settings.hasOpenRouterKey() -> true
            else -> false
        }
    }

    private suspend fun callGeminiSync(
        model: String,
        systemInstruction: String,
        prompt: String,
        temperature: Double,
        settings: AiSettings,
    ): String {
        val apiKey = settings.geminiApiKey.trim()
        if (apiKey.isEmpty()) throw IllegalStateException("Gemini API キーが未設定です")
        val cleanModel = model.trim().removePrefix("models/")
        val url = "$geminiBaseUrl/models/$cleanModel:generateContent"
        val payload = buildGeminiPayload(systemInstruction, prompt, temperature)
        val rawJson = postJsonSync(
            urlString = url,
            body = payload.toString(),
            headers = mapOf("x-goog-api-key" to apiKey),
            connectTimeoutMs = 20_000,
            readTimeoutMs = 120_000,
        )
        return SseEventParser.extractGeminiText(rawJson).orEmpty()
    }

    private suspend fun streamGemini(
        model: String,
        systemInstruction: String,
        prompt: String,
        temperature: Double,
        settings: AiSettings,
        onChunk: suspend (String) -> Unit,
    ) {
        val apiKey = settings.geminiApiKey.trim()
        if (apiKey.isEmpty()) throw IllegalStateException("Gemini API キーが未設定です")
        val cleanModel = model.trim().removePrefix("models/")
        val url = "$geminiBaseUrl/models/$cleanModel:streamGenerateContent?alt=sse"
        val payload = buildGeminiPayload(systemInstruction, prompt, temperature)
        postSseSync(
            urlString = url,
            body = payload.toString(),
            headers = mapOf("x-goog-api-key" to apiKey),
            isOpenRouter = false,
            onChunk = onChunk,
        )
    }

    private suspend fun callOpenRouterSync(
        model: String,
        systemInstruction: String,
        prompt: String,
        temperature: Double,
        settings: AiSettings,
    ): String {
        val apiKey = settings.openRouterApiKey.trim()
        if (apiKey.isEmpty()) throw IllegalStateException("OpenRouter API キーが未設定です")
        val payload = buildOpenRouterPayload(
            model = model,
            systemInstruction = systemInstruction,
            prompt = prompt,
            temperature = temperature,
            providers = settings.providerAllowList(),
            stream = false,
        )
        val rawJson = postJsonSync(
            urlString = openRouterUrl,
            body = payload.toString(),
            headers = openRouterHeaders(apiKey),
            connectTimeoutMs = 20_000,
            readTimeoutMs = 180_000,
        )
        return SseEventParser.extractOpenRouterDelta(rawJson).orEmpty()
    }

    private suspend fun streamOpenRouter(
        model: String,
        systemInstruction: String,
        prompt: String,
        temperature: Double,
        settings: AiSettings,
        onChunk: suspend (String) -> Unit,
    ) {
        val apiKey = settings.openRouterApiKey.trim()
        if (apiKey.isEmpty()) throw IllegalStateException("OpenRouter API キーが未設定です")
        val payload = buildOpenRouterPayload(
            model = model,
            systemInstruction = systemInstruction,
            prompt = prompt,
            temperature = temperature,
            providers = settings.providerAllowList(),
            stream = true,
        )
        postSseSync(
            urlString = openRouterUrl,
            body = payload.toString(),
            headers = openRouterHeaders(apiKey),
            isOpenRouter = true,
            onChunk = onChunk,
        )
    }

    private fun openRouterHeaders(apiKey: String): Map<String, String> = mapOf(
        "Authorization" to "Bearer $apiKey",
        "HTTP-Referer" to "https://github.com/ipogy/bookgen-android",
        "X-Title" to "Co-Author Studio Mobile",
    )

    private fun buildGeminiPayload(
        systemInstruction: String,
        prompt: String,
        temperature: Double,
    ): JsonObject = buildJsonObject {
        if (systemInstruction.isNotBlank()) {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") {
                    addJsonObject { put("text", systemInstruction) }
                }
            }
        }
        putJsonArray("contents") {
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    addJsonObject { put("text", prompt) }
                }
            }
        }
        putJsonObject("generationConfig") {
            put("temperature", temperature)
        }
    }

    private fun buildOpenRouterPayload(
        model: String,
        systemInstruction: String,
        prompt: String,
        temperature: Double,
        providers: List<String>,
        stream: Boolean,
    ): JsonObject = buildJsonObject {
        put("model", model.trim())
        putJsonArray("messages") {
            if (systemInstruction.isNotBlank()) {
                addJsonObject {
                    put("role", "system")
                    put("content", systemInstruction)
                }
            }
            addJsonObject {
                put("role", "user")
                put("content", prompt)
            }
        }
        put("temperature", temperature)
        put("stream", stream)
        // Per legacy api.php notes, omit `response_format` for providers like Novita and rely on prompt structure.
        if (providers.isNotEmpty()) {
            putJsonObject("provider") {
                putJsonArray("only") {
                    providers.forEach { add(it) }
                }
                put("allow_fallbacks", false)
            }
        }
    }

    private suspend fun postJsonSync(
        urlString: String,
        body: String,
        headers: Map<String, String>,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): String {
        val ctx = currentCoroutineContext()
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        val cancelHandle = ctx.job.invokeOnCompletion { conn.disconnect() }
        try {
            conn.outputStream.use { os ->
                os.write(body.toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (code !in 200..299) {
                throw LlmHttpException(
                    statusCode = code,
                    message = "LLM API HTTP $code: ${text.take(280)}",
                    retryAfterSeconds = HttpRetryPolicy.parseRetryAfterSeconds(conn.getHeaderField("Retry-After")),
                )
            }
            return text
        } finally {
            cancelHandle.dispose()
            conn.disconnect()
        }
    }

    private suspend fun postSseSync(
        urlString: String,
        body: String,
        headers: Map<String, String>,
        isOpenRouter: Boolean,
        onChunk: suspend (String) -> Unit,
    ) {
        val ctx = currentCoroutineContext()
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 180_000
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "text/event-stream")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        val cancelHandle = ctx.job.invokeOnCompletion { conn.disconnect() }
        try {
            conn.outputStream.use { os ->
                os.write(body.toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val errText = (conn.errorStream ?: conn.inputStream)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
                throw LlmHttpException(
                    statusCode = code,
                    message = "LLM Stream HTTP $code: ${errText.take(280)}",
                    retryAfterSeconds = HttpRetryPolicy.parseRetryAfterSeconds(conn.getHeaderField("Retry-After")),
                )
            }
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                SseEventParser.consumeSseStream(
                    reader = reader,
                    isOpenRouter = isOpenRouter,
                    isCancelled = { !ctx.isActive },
                    onDelta = onChunk,
                )
            }
            if (!ctx.isActive) {
                throw CancellationException("Streaming cancelled")
            }
        } finally {
            cancelHandle.dispose()
            conn.disconnect()
        }
    }
}
