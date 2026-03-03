package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedui.core.session.AgentId
import org.koin.core.annotation.Factory

@Factory
class FormatAgentTitleUseCase {
    operator fun invoke(agentId: AgentId, title: String): String {
        val agentNumber = agentId.value.substringAfter("agent-", "")
        return if (agentNumber.isNotBlank()) "#$agentNumber: $title" else title
    }
}
