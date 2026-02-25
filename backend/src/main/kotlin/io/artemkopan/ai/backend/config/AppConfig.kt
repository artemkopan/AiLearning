package io.artemkopan.ai.backend.config

data class AppConfig(
    val port: Int,
    val geminiApiKey: String,
    val defaultModel: String,
    val defaultContextWindowTokens: Int = 1_000_000,
    val corsOrigin: String,
    val contextSummaryTriggerTokens: Int = 3_000,
    val contextRecentMaxMessages: Int = 12,
    val contextSummaryMaxOutputTokens: Int = 300,
    val contextSummaryModel: String? = null,
    val contextEmbeddingModel: String = "gemini-embedding-001",
    val contextEmbeddingChunkChars: Int = 1_200,
    val contextRetrievalTopK: Int = 6,
    val contextRetrievalMinScore: Double = 0.55,
    val dbHost: String,
    val dbPort: Int,
    val dbName: String,
    val dbUser: String,
    val dbPassword: String,
    val dbSsl: Boolean,
) {
    val jdbcUrl: String
        get() = "jdbc:postgresql://$dbHost:$dbPort/$dbName?ssl=$dbSsl"

    fun validate() {
        require(geminiApiKey.isNotBlank()) {
            "GEMINI_API_KEY is required but was not provided. Please set it in your .env file or environment variables."
        }
        require(defaultContextWindowTokens > 0) {
            "DEFAULT_CONTEXT_WINDOW_TOKENS must be greater than 0."
        }
        require(contextSummaryTriggerTokens > 0) {
            "CONTEXT_SUMMARY_TRIGGER_TOKENS must be greater than 0."
        }
        require(contextRecentMaxMessages > 0) {
            "CONTEXT_RECENT_MAX_MESSAGES must be greater than 0."
        }
        require(contextSummaryMaxOutputTokens > 0) {
            "CONTEXT_SUMMARY_MAX_OUTPUT_TOKENS must be greater than 0."
        }
        require(contextEmbeddingModel.isNotBlank()) {
            "CONTEXT_EMBEDDING_MODEL must not be blank."
        }
        require(contextEmbeddingChunkChars > 0) {
            "CONTEXT_EMBEDDING_CHUNK_CHARS must be greater than 0."
        }
        require(contextRetrievalTopK > 0) {
            "CONTEXT_RETRIEVAL_TOP_K must be greater than 0."
        }
        require(contextRetrievalMinScore in 0.0..1.0) {
            "CONTEXT_RETRIEVAL_MIN_SCORE must be between 0.0 and 1.0."
        }
    }

    companion object {
        fun fromEnv(env: Map<String, String> = EnvSource.load()): AppConfig {
            return AppConfig(
                port = env["PORT"]?.toIntOrNull() ?: 8080,
                geminiApiKey = env["GEMINI_API_KEY"].orEmpty(),
                defaultModel = env["GEMINI_MODEL"].orEmpty().ifBlank { "gemini-2.5-flash" },
                defaultContextWindowTokens = env["DEFAULT_CONTEXT_WINDOW_TOKENS"]?.toIntOrNull()
                    ?: 1_000_000,
                corsOrigin = env["CORS_ORIGIN"].orEmpty()
                    .removePrefix("http://")
                    .removePrefix("https://")
                    .ifBlank { "localhost:8081" },
                contextSummaryTriggerTokens = env["CONTEXT_SUMMARY_TRIGGER_TOKENS"]?.toIntOrNull()
                    ?: 3_000,
                contextRecentMaxMessages = env["CONTEXT_RECENT_MAX_MESSAGES"]?.toIntOrNull()
                    ?: 12,
                contextSummaryMaxOutputTokens = env["CONTEXT_SUMMARY_MAX_OUTPUT_TOKENS"]?.toIntOrNull()
                    ?: 300,
                contextSummaryModel = env["CONTEXT_SUMMARY_MODEL"]?.trim()?.takeIf { it.isNotEmpty() },
                contextEmbeddingModel = env["CONTEXT_EMBEDDING_MODEL"].orEmpty().ifBlank { "gemini-embedding-001" },
                contextEmbeddingChunkChars = env["CONTEXT_EMBEDDING_CHUNK_CHARS"]?.toIntOrNull() ?: 1_200,
                contextRetrievalTopK = env["CONTEXT_RETRIEVAL_TOP_K"]?.toIntOrNull() ?: 6,
                contextRetrievalMinScore = env["CONTEXT_RETRIEVAL_MIN_SCORE"]?.toDoubleOrNull() ?: 0.55,
                dbHost = env["DB_HOST"].orEmpty().ifBlank { "localhost" },
                dbPort = env["DB_PORT"]?.toIntOrNull() ?: 5432,
                dbName = env["DB_NAME"].orEmpty().ifBlank { "ai_learning" },
                dbUser = env["DB_USER"].orEmpty().ifBlank { "postgres" },
                dbPassword = env["DB_PASSWORD"].orEmpty().ifBlank { "postgres" },
                dbSsl = env["DB_SSL"]?.toBooleanStrictOrNull() ?: false,
            )
        }
    }
}
