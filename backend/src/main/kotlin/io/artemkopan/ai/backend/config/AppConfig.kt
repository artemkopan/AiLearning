package io.artemkopan.ai.backend.config

data class AppConfig(
    val port: Int,
    val geminiApiKey: String,
    val defaultModel: String,
    val corsOrigin: String,
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
