package io.artemkopan.ai.backend.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

class Shell {
    suspend fun exec(vararg cmd: String, timeout: Long = 10_000): ShellResult = withContext(Dispatchers.IO) {
        withTimeout(timeout) {
            val process = ProcessBuilder(*cmd)
                .redirectErrorStream(false)
                .start()

            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            ShellResult(exitCode = exitCode, stdout = stdout, stderr = stderr)
        }
    }
}
