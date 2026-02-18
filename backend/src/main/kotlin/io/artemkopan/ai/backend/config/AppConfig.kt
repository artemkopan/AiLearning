package io.artemkopan.ai.backend.config

data class AppConfig(
    val port: Int,
    val geminiApiKey: String,
    val defaultModel: String,
    val corsOrigin: String,
) {
    fun validate() {
        require(geminiApiKey.isNotBlank()) {
            "GEMINI_API_KEY is required but was not provided. Please set it in your .env file or environment variables."
        }
    }

    companion object {
        fun fromEnv(env: Map<String, String> = EnvSource.load()): AppConfig {
            return AppConfig(
                port = env["PORT"]?.toIntOrNull() ?: 8080,
                geminiApiKey = env["GEMINI_API_KEY"].orEmpty(),
                defaultModel = env["GEMINI_MODEL"].orEmpty().ifBlank { "gemini-2.5-flash" },
                corsOrigin = env["CORS_ORIGIN"].orEmpty()
                    .removePrefix("http://")
                    .removePrefix("https://")
                    .ifBlank { "localhost:8081" },
            )
        }
    }
}
