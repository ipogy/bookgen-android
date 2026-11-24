package com.bookgen.app.domain.model

data class AiSettings(
    val geminiApiKey: String = "",
    val openRouterApiKey: String = "",
    val embeddingModel: String = DEFAULT_EMBEDDING_MODEL,
    val plannerModel: String = DEFAULT_PLANNER_MODEL,
    val writerModel: String = DEFAULT_WRITER_MODEL,
    val openRouterProviders: String = DEFAULT_OPENROUTER_PROVIDER,
    val maxConcurrency: Int = DEFAULT_CONCURRENCY,
) {
    fun hasGeminiKey(): Boolean = geminiApiKey.isNotBlank()

    fun hasOpenRouterKey(): Boolean = openRouterApiKey.isNotBlank()

    fun hasKeyForModel(modelName: String): Boolean {
        return if ('/' in modelName) {
            hasOpenRouterKey()
        } else {
            hasGeminiKey() || hasOpenRouterKey()
        }
    }

    fun hasAnyApiKey(): Boolean = hasGeminiKey() || hasOpenRouterKey()

    fun providerAllowList(): List<String> = openRouterProviders
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    fun maskedGeminiKey(): String = maskSecret(geminiApiKey)

    fun maskedOpenRouterKey(): String = maskSecret(openRouterApiKey)

    // Never expose plaintext secrets in crash logs or toString dumps.
    override fun toString(): String =
        "AiSettings(gemini=${maskedGeminiKey()}, openRouter=${maskedOpenRouterKey()}, " +
            "embedding=$embeddingModel, planner=$plannerModel, writer=$writerModel, " +
            "providers=$openRouterProviders, concurrency=$maxConcurrency)"

    companion object {
        const val DEFAULT_EMBEDDING_MODEL = "gemini-embedding-2"
        const val DEFAULT_PLANNER_MODEL = "inclusionai/ling-3.0-flash"
        const val DEFAULT_WRITER_MODEL = "inclusionai/ling-3.0-flash"
        const val DEFAULT_OPENROUTER_PROVIDER = "novita"
        const val DEFAULT_CONCURRENCY = 2

        fun maskSecret(secret: String): String {
            val trimmed = secret.trim()
            if (trimmed.isEmpty()) return "未設定"
            if (trimmed.length <= 8) return "****"
            return "${trimmed.take(4)}…${trimmed.takeLast(4)}"
        }
    }
}
