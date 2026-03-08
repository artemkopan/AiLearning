package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedui.core.session.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Factory

@Factory
class ObserveSessionStateUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke(): StateFlow<SessionState> = controller.sessionState
}

@Factory
class ObserveAgentSliceUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke(agentId: AgentId): Flow<AgentSessionSlice?> =
        controller.sessionState
            .map { current ->
                val agent = current.agents[agentId] ?: return@map null
                AgentSessionSlice(
                    agent = agent,
                    queuedMessages = current.queuedByAgent[agentId].orEmpty(),
                    agentConfig = current.agentConfig,
                )
            }
            .distinctUntilChanged()
}

@Factory
class ObserveActiveAgentIdUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke(): Flow<AgentId?> =
        controller.sessionState.map { it.activeAgentId }.distinctUntilChanged()
}

@Factory
class ObserveErrorUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke(): Flow<ErrorDialogModel?> =
        controller.sessionState.map { it.errorDialog }.distinctUntilChanged()
}

@Factory
class DisposeSessionUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke() = controller.dispose()
}
