package io.artemkopan.ai.core.application

import io.artemkopan.ai.core.application.usecase.stats.AgentStats
import io.artemkopan.ai.core.application.usecase.stats.AgentTokenStats
import io.artemkopan.ai.core.application.usecase.stats.BuildAgentStatsSnippetUseCase
import io.artemkopan.ai.core.domain.model.RollingSummaryAgentContextConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildAgentStatsSnippetUseCaseTest {
    private val useCase = BuildAgentStatsSnippetUseCase()

    @Test
    fun `formats requested stats fields`() {
        val output = useCase.execute(
            AgentStats(
                agentId = "agent-1",
                title = "Planner",
                model = "deepseek-chat",
                agentMode = "default",
                contextConfig = RollingSummaryAgentContextConfig(
                    recentMessagesN = 12,
                    summarizeEveryK = 10,
                ),
                contextSummary = "",
                summarizedUntilCreatedAt = 0,
                contextSummaryUpdatedAt = 0,
                systemInstruction = "",
                latestAssistant = null,
                tokenStats = AgentTokenStats(
                    cumulativeInputTokens = 120,
                    cumulativeOutputTokens = 80,
                    cumulativeTotalTokens = 200,
                ),
                recentTurns = emptyList(),
            )
        )

        assertEquals(
            """
            agent name: Planner
            strategy: rolling_summary
            strategy parameters: recentMessagesN=12, summarizeEveryK=10
            tokens used: total=200, input=120, output=80
            """.trimIndent(),
            output
        )
    }
}
