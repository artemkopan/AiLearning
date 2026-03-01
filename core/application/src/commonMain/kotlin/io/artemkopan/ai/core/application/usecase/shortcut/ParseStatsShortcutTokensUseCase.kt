package io.artemkopan.ai.core.application.usecase.shortcut

class ParseStatsShortcutTokensUseCase {
    fun execute(text: String): List<StatsShortcutToken> {
        if (text.isBlank()) return emptyList()

        val tokens = mutableListOf<StatsShortcutToken>()
        val seen = mutableSetOf<String>()

        if (ALL_AGENTS_TOKEN in text && seen.add(ALL_AGENTS_TOKEN)) {
            tokens += StatsShortcutToken(
                raw = ALL_AGENTS_TOKEN,
                agentId = "",
                allAgents = true,
            )
        }

        TOKEN_REGEX.findAll(text).forEach { match ->
            val raw = match.value
            if (seen.add(raw)) {
                tokens += StatsShortcutToken(
                    raw = raw,
                    agentId = match.groupValues[1],
                )
            }
        }
        return tokens
    }
}

private const val ALL_AGENTS_TOKEN = "/agents-stats"
private val TOKEN_REGEX = Regex("/agent-([a-zA-Z0-9._-]+)-stats")
