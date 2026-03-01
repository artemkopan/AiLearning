package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.PostgresDbRuntime
import io.artemkopan.ai.backend.agent.persistence.helper.PostgresStateHelpers
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.model.UserId

internal class GetStateOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
    private val stateHelpers: Lazy<PostgresStateHelpers>,
) {
    suspend fun execute(userId: Lazy<UserId>): Result<AgentState> = runtime.value.runDb {
        stateHelpers.value.readStateTx(userId.value)
    }
}
