package io.artemkopan.ai.backend.config

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object EnvSource {
    data class LoadedEnv(
        val values: Map<String, String>,
        val loadedFiles: List<Path>,
    )

    fun load(
        systemEnv: Map<String, String> = System.getenv(),
        envFileCandidates: List<Path> = defaultEnvFileCandidates(),
    ): Map<String, String> {
        return loadDetailed(systemEnv, envFileCandidates).values
    }

    fun loadDetailed(
        systemEnv: Map<String, String> = System.getenv(),
        envFileCandidates: List<Path> = defaultEnvFileCandidates(),
    ): LoadedEnv {
        val fileEnv = mutableMapOf<String, String>()
        val loadedFiles = mutableListOf<Path>()

        envFileCandidates
            .asSequence()
            .map { it.normalize() }
            .filter { Files.exists(it) && Files.isRegularFile(it) }
            .forEach { path ->
                fileEnv.putAll(parseDotEnv(Files.readAllLines(path)))
                loadedFiles.add(path)
            }

        // System env has higher precedence than local .env
        return LoadedEnv(values = fileEnv + systemEnv, loadedFiles = loadedFiles)
    }

    internal fun defaultEnvFileCandidates(workingDir: Path = Paths.get("").toAbsolutePath()): List<Path> {
        val normalized = workingDir.normalize()
        val parent = normalized.parent

        return buildList {
            if (parent != null) add(parent.resolve(".env"))
            add(normalized.resolve(".env"))
        }.distinct()
    }

    internal fun parseDotEnv(lines: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()

        lines.forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach

            val line = if (trimmed.startsWith("export ")) {
                trimmed.removePrefix("export ").trim()
            } else {
                trimmed
            }

            val separator = line.indexOf('=')
            if (separator <= 0) return@forEach

            val key = line.substring(0, separator).trim()
            if (key.isBlank()) return@forEach

            val rawValue = line.substring(separator + 1).trim()
            result[key] = parseValue(rawValue)
        }

        return result
    }

    private fun parseValue(value: String): String {
        if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
            return unescapeDoubleQuoted(value.substring(1, value.length - 1))
        }

        if (value.length >= 2 && value.startsWith('\'') && value.endsWith('\'')) {
            return value.substring(1, value.length - 1)
        }

        return stripInlineComment(value).trim()
    }

    private fun stripInlineComment(value: String): String {
        val out = StringBuilder()
        for (index in value.indices) {
            val ch = value[index]
            if (ch == '#') {
                val isCommentStart = index == 0 || value[index - 1].isWhitespace()
                if (isCommentStart) break
            }
            out.append(ch)
        }
        return out.toString()
    }

    private fun unescapeDoubleQuoted(value: String): String {
        val out = StringBuilder()
        var index = 0
        while (index < value.length) {
            val ch = value[index]
            if (ch == '\\' && index + 1 < value.length) {
                val next = value[index + 1]
                val escaped = when (next) {
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    '"' -> '"'
                    '\\' -> '\\'
                    else -> next
                }
                out.append(escaped)
                index += 2
                continue
            }
            out.append(ch)
            index += 1
        }
        return out.toString()
    }
}
