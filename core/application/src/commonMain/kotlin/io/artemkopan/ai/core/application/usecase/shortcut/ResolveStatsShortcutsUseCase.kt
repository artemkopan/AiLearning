package io.artemkopan.ai.core.application.usecase.shortcut

import io.artemkopan.ai.core.application.error.AppError
import io.artemkopan.ai.core.application.usecase.stats.BuildAgentStatsSnippetUseCase
import io.artemkopan.ai.core.application.usecase.stats.GetAgentStatsUseCase
import io.artemkopan.ai.core.domain.model.UserId

class ResolveStatsShortcutsUseCase(
    private val getAgentStatsUseCase: GetAgentStatsUseCase,
    private val buildAgentStatsSnippetUseCase: BuildAgentStatsSnippetUseCase,
) {
    suspend fun execute(userId: UserId, tokens: List<StatsShortcutToken>): Result<Map<String, String>> {
        if (tokens.isEmpty()) return Result.success(emptyMap())
        val stats = getAgentStatsUseCase.execute(userId.value).getOrElse { return Result.failure(it) }
        val statsByAgentId = stats.associateBy { it.agentId }

        val perAgentTokens = tokens.filter { !it.allAgents }
        val unknown = perAgentTokens.mapNotNull { token ->
            if (statsByAgentId.containsKey(token.agentId)) null else token.raw
        }
        if (unknown.isNotEmpty()) {
            return Result.failure(
                AppError.Validation("Unknown agent stats shortcuts: ${unknown.joinToString(", ")}")
            )
        }

        val resolved = mutableMapOf<String, String>()
        for (token in tokens) {
            if (token.allAgents) {
                val combined = stats.joinToString(separator = "\n---\n") { agentStats ->
                    buildAgentStatsSnippetUseCase.execute(agentStats)
                }
                resolved[token.raw] = combined
            } else {
                resolved[token.raw] = buildAgentStatsSnippetUseCase.execute(
                    checkNotNull(statsByAgentId[token.agentId])
                )
            }
        }
        return Result.success(resolved)
    }
}
