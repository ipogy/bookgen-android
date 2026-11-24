package com.bookgen.app.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader

object SseEventParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Reads SSE `data:` payloads line by line from a [BufferedReader], dispatching extracted
     * text deltas from either Gemini (`candidates[0].content.parts[*].text`) or
     * OpenRouter/OpenAI (`choices[0].delta.content`).
     */
    suspend fun consumeSseStream(
        reader: BufferedReader,
        isOpenRouter: Boolean,
        isCancelled: () -> Boolean,
        onDelta: suspend (String) -> Unit,
    ) {
        val dataBuffer = StringBuilder()

        suspend fun flushEvent() {
            if (dataBuffer.isEmpty()) return
            val payload = dataBuffer.toString().trim()
            dataBuffer.setLength(0)
            if (payload.isEmpty() || payload == "[DONE]") return

            val delta = if (isOpenRouter) {
                extractOpenRouterDelta(payload)
            } else {
                extractGeminiText(payload)
            }
            if (!delta.isNullOrEmpty()) {
                onDelta(delta)
            }
        }

        while (!isCancelled()) {
            val line = reader.readLine() ?: break
            when {
                line.isEmpty() -> flushEvent()
                line.startsWith(":") -> Unit // SSE keepalive comment
                line.startsWith("data:") -> {
                    if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
                    dataBuffer.append(line.removePrefix("data:").trimStart())
                }
            }
        }
        flushEvent()
    }

    fun extractGeminiText(jsonPayload: String): String? {
        val root = runCatching { json.parseToJsonElement(jsonPayload).jsonObject }.getOrNull()
            ?: return null
        val candidates = root["candidates"]?.jsonArray ?: return null
        val firstCandidate = candidates.firstOrNull()?.jsonObject ?: return null
        val content = firstCandidate["content"]?.jsonObject ?: return null
        val parts = content["parts"]?.jsonArray ?: return null
        val sb = StringBuilder()
        for (part in parts) {
            val txt = part.jsonObject["text"]?.jsonPrimitive?.contentOrNull
            if (!txt.isNullOrEmpty()) {
                sb.append(txt)
            }
        }
        return sb.toString().takeIf { it.isNotEmpty() }
    }

    fun extractOpenRouterDelta(jsonPayload: String): String? {
        val root = runCatching { json.parseToJsonElement(jsonPayload).jsonObject }.getOrNull()
            ?: return null
        val errorObj = root["error"]?.jsonObject
        if (errorObj != null) {
            val msg = errorObj["message"]?.jsonPrimitive?.contentOrNull ?: "OpenRouter stream error"
            throw LlmHttpException(502, msg)
        }
        val choices = root["choices"]?.jsonArray ?: return null
        val firstChoice = choices.firstOrNull()?.jsonObject ?: return null
        val deltaContent = firstChoice["delta"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
        if (!deltaContent.isNullOrEmpty()) return deltaContent
        return firstChoice["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
    }
}
