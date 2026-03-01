package io.artemkopan.ai.core.application.usecase.context

import io.artemkopan.ai.core.domain.model.Agent
import io.artemkopan.ai.core.domain.model.StickyFactsAgentContextConfig
import io.artemkopan.ai.core.domain.model.UserId
import io.artemkopan.ai.core.domain.repository.AgentRepository

class StickyFactsContextPreparationStrategy(
    private val repository: AgentRepository,
) : ContextPreparationStrategy {

    override suspend fun prepare(userId: UserId, agent: Agent): Result<PreparedContextWindow> {
        val config = agent.contextConfig as? StickyFactsAgentContextConfig
            ?: return Result.failure(IllegalArgumentException("Invalid context config for sticky facts strategy."))

        val facts = repository.getAgentFacts(userId, agent.id)
            .getOrElse { return Result.failure(it) }
        val factsFormatted = formatFacts(facts?.factsJson.orEmpty())

        val recentMessages = agent.messages
            .filter { !it.status.equals(STATUS_STOPPED, ignoreCase = true) }
            .takeLast(config.recentMessagesN)

        return Result.success(
            PreparedContextWindow(
                summaryText = factsFormatted,
                recentMessages = recentMessages,
            )
        )
    }

    private fun formatFacts(factsJson: String): String {
        if (factsJson.isBlank()) return ""
        val trimmed = factsJson.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return ""
        val inner = trimmed.substring(1, trimmed.length - 1).trim()
        if (inner.isEmpty()) return ""
        return try {
            val pairs = parseSimpleJsonObject(inner)
            if (pairs.isEmpty()) return ""
            buildString {
                appendLine("KEY FACTS:")
                for (pair in pairs) {
                    appendLine("- ${pair.first}: ${pair.second}")
                }
            }.trimEnd()
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseSimpleJsonObject(inner: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        var i = 0
        while (i < inner.length) {
            i = skipWhitespace(inner, i)
            if (i >= inner.length) break
            if (inner[i] == ',') { i++; continue }
            val key = readString(inner, i)
            i = key.second
            i = skipWhitespace(inner, i)
            if (i < inner.length && inner[i] == ':') i++
            i = skipWhitespace(inner, i)
            val value = readString(inner, i)
            i = value.second
            result.add(key.first to value.first)
        }
        return result
    }

    private fun readString(s: String, start: Int): Pair<String, Int> {
        var i = start
        if (i >= s.length) return "" to i
        if (s[i] == '"') {
            i++
            val sb = StringBuilder()
            while (i < s.length && s[i] != '"') {
                if (s[i] == '\\' && i + 1 < s.length) { sb.append(s[i + 1]); i += 2 }
                else { sb.append(s[i]); i++ }
            }
            if (i < s.length) i++ // skip closing quote
            return sb.toString() to i
        }
        val sb = StringBuilder()
        while (i < s.length && s[i] != ',' && s[i] != '}') { sb.append(s[i]); i++ }
        return sb.toString().trim() to i
    }

    private fun skipWhitespace(s: String, start: Int): Int {
        var i = start
        while (i < s.length && s[i].isWhitespace()) i++
        return i
    }
}

private const val STATUS_STOPPED = "stopped"
