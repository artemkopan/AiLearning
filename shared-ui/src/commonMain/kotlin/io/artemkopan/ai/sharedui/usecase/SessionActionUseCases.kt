package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedcontract.AgentMode
import io.artemkopan.ai.sharedui.core.session.AgentId
import io.artemkopan.ai.sharedui.core.session.AgentSessionStore
import org.koin.core.annotation.Factory

@Factory
class CreateAgentActionUseCase(
    private val sessionStore: AgentSessionStore,
) {
    operator fun invoke() {
        sessionStore.createAgent()
    }
}

@Factory
class SelectAgentActionUseCase(
    private val sessionStore: AgentSessionStore,
) {
    operator fun invoke(agentId: AgentId) {
        sessionStore.selectAgent(agentId)
    }
}

@Factory
class CloseAgentActionUseCase(
    private val sessionStore: AgentSessionStore,
) {
    operator fun invoke(agentId: AgentId) {
        sessionStore.closeAgent(agentId)
    }
}

@Factory
class SubmitFromActiveAgentActionUseCase(
    private val sessionStore: AgentSessionStore,
) {
    operator fun invoke() {
        sessionStore.submitFromActiveAgent()
    }
}

@Factory
class SelectNextAgentActionUseCase(
    private val sessionStore: AgentSessionStore,
) {
    operator fun invoke() {
        sessionStore.selectNextAgent()
    }
}

@Factory
class SelectPreviousAgentActionUseCase(
    private val sessionStore: AgentSessionStore,
) {
    operator fun invoke() {
        sessionStore.selectPreviousAgent()
    }
}

@Factory
class DismissErrorActionUseCase(
    private val sessionStore: AgentSessionStore,
) {
    operator fun invoke() {
        sessionStore.dismissError()
    }
}

@Factory
class UpdateDraftMessageActionUseCase(
    private val sessionStore: AgentSessionStore,
) {
    operator fun invoke(agentId: AgentId, value: String) {
        sessionStore.updateDraftMessage(agentId, value)
    }
}

@Factory
class SubmitMessageActionUseCase(
    private val sessionStore: AgentSessionStore,
) {
    operator fun invoke(agentId: AgentId) {
        sessionStore.submitMessage(agentId)
    }
}

@Factory
class StopQueueActionUseCase(
    private val sessionStore: AgentSessionStore,
) {
    operator fun invoke(agentId: AgentId) {
        sessionStore.stopQueue(agentId)
    }
}

@Factory
class CreateBranchActionUseCase(
    private val sessionStore: AgentSessionStore,
) {
    operator fun invoke(agentId: AgentId, checkpointMessageId: String, name: String) {
        sessionStore.createBranch(
            agentId = agentId,
            checkpointMessageId = checkpointMessageId,
            name = name,
        )
    }
}

@Factory
class UpdateModelActionUseCase(
    private val sessionStore: AgentSessionStore,
) {
    operator fun invoke(agentId: AgentId, value: String) {
        sessionStore.updateModel(agentId, value)
    }
}

@Factory
class UpdateMaxOutputTokensActionUseCase(
    private val sessionStore: AgentSessionStore,
) {
    operator fun invoke(agentId: AgentId, value: String) {
        sessionStore.updateMaxOutputTokens(agentId, value)
    }
}

@Factory
class UpdateTemperatureActionUseCase(
    private val sessionStore: AgentSessionStore,
) {
    operator fun invoke(agentId: AgentId, value: String) {
        sessionStore.updateTemperature(agentId, value)
    }
}

@Factory
class UpdateStopSequencesActionUseCase(
    private val sessionStore: AgentSessionStore,
) {
    operator fun invoke(agentId: AgentId, value: String) {
        sessionStore.updateStopSequences(agentId, value)
    }
}

@Factory
class UpdateAgentModeActionUseCase(
    private val sessionStore: AgentSessionStore,
) {
    operator fun invoke(agentId: AgentId, value: AgentMode) {
        sessionStore.updateAgentMode(agentId, value)
    }
}

@Factory
class UpdateContextStrategyActionUseCase(
    private val sessionStore: AgentSessionStore,
) {
    operator fun invoke(agentId: AgentId, value: String) {
        sessionStore.updateContextStrategy(agentId, value)
    }
}

@Factory
class UpdateContextRecentMessagesActionUseCase(
    private val sessionStore: AgentSessionStore,
) {
    operator fun invoke(agentId: AgentId, value: String) {
        sessionStore.updateContextRecentMessages(agentId, value)
    }
}

@Factory
class UpdateContextSummarizeEveryActionUseCase(
    private val sessionStore: AgentSessionStore,
) {
    operator fun invoke(agentId: AgentId, value: String) {
        sessionStore.updateContextSummarizeEvery(agentId, value)
    }
}

@Factory
class UpdateContextWindowSizeActionUseCase(
    private val sessionStore: AgentSessionStore,
) {
    operator fun invoke(agentId: AgentId, value: String) {
        sessionStore.updateContextWindowSize(agentId, value)
    }
}

@Factory
class SwitchBranchActionUseCase(
    private val sessionStore: AgentSessionStore,
) {
    operator fun invoke(agentId: AgentId, branchId: String) {
        sessionStore.switchBranch(agentId, branchId)
    }
}

@Factory
class DeleteBranchActionUseCase(
    private val sessionStore: AgentSessionStore,
) {
    operator fun invoke(agentId: AgentId, branchId: String) {
        sessionStore.deleteBranch(agentId, branchId)
    }
}
