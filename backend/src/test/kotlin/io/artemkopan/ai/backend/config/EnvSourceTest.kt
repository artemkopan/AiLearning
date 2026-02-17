package io.artemkopan.ai.backend.config

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class EnvSourceTest {
    @Test
    fun `parseDotEnv parses supported entries`() {
        val parsed = EnvSource.parseDotEnv(
            listOf(
                "# comment",
                "GEMINI_API_KEY=abc123",
                "PORT=8080 # inline comment",
                "export GEMINI_MODEL=gemini-2.5-flash",
                "CORS_ORIGIN=\"http://localhost:8082\"",
                "RAW_SINGLE='a b c'",
                "ESCAPED=\"line1\\nline2\"",
            )
        )

        assertEquals("abc123", parsed["GEMINI_API_KEY"])
        assertEquals("8080", parsed["PORT"])
        assertEquals("gemini-2.5-flash", parsed["GEMINI_MODEL"])
        assertEquals("http://localhost:8082", parsed["CORS_ORIGIN"])
        assertEquals("a b c", parsed["RAW_SINGLE"])
        assertEquals("line1\nline2", parsed["ESCAPED"])
    }

    @Test
    fun `load merges dotenv files and system env with system precedence`() {
        val rootDir = Files.createTempDirectory("env-source-root-")
        val backendDir = rootDir.resolve("backend")
        Files.createDirectories(backendDir)

        Files.writeString(
            rootDir.resolve(".env"),
            """
            GEMINI_API_KEY=file-key
            PORT=8100
            """.trimIndent()
        )
        Files.writeString(
            backendDir.resolve(".env"),
            """
            PORT=8200
            CORS_ORIGIN=http://localhost:8082
            """.trimIndent()
        )

        val merged = EnvSource.load(
            systemEnv = mapOf(
                "PORT" to "8300",
                "EXTRA_FLAG" to "enabled",
            ),
            envFileCandidates = EnvSource.defaultEnvFileCandidates(backendDir),
        )

        assertEquals("file-key", merged["GEMINI_API_KEY"])
        assertEquals("http://localhost:8082", merged["CORS_ORIGIN"])
        assertEquals("8300", merged["PORT"])
        assertEquals("enabled", merged["EXTRA_FLAG"])
    }
}
