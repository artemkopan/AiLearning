package io.artemkopan.ai.backend.config

data class AppConfig(
    val port: Int,
    val projectsRoot: String,
    val corsOrigin: String,
) {
    fun validate() {
        require(projectsRoot.isNotBlank()) {
            "PROJECTS_ROOT is required. Please set it in your .env file or environment variables."
        }
    }

    companion object {
        fun fromEnv(env: Map<String, String> = EnvSource.load()): AppConfig {
            return AppConfig(
                port = env["PORT"]?.toIntOrNull() ?: 8080,
                projectsRoot = env["PROJECTS_ROOT"].orEmpty().ifBlank { "/projects" },
                corsOrigin = env["CORS_ORIGIN"].orEmpty().ifBlank { "localhost:8081" },
            )
        }
    }
}
