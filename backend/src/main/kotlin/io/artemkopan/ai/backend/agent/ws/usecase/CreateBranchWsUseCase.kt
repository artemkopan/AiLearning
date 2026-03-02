package io.artemkopan.ai.backend.agent.ws.usecase

import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.core.domain.model.AgentBranch
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentMessageId
import io.artemkopan.ai.core.domain.model.UserId
import io.artemkopan.ai.core.domain.repository.AgentRepository
import io.artemkopan.ai.sharedcontract.CreateBranchCommandDto
import org.koin.core.annotation.Factory

@Factory(binds = [AgentWsMessageUseCase::class])
class CreateBranchWsUseCase(
    private val agentRepository: AgentRepository,
    private val outboundService: AgentWsOutboundService,
) : AgentWsMessageUseCase<CreateBranchCommandDto> {
    override val messageType = CreateBranchCommandDto::class

    override suspend fun execute(context: AgentWsMessageContext, message: CreateBranchCommandDto): Result<Unit> {
        val branch = AgentBranch(
            id = "branch-${System.currentTimeMillis()}",
            name = message.branchName,
            checkpointMessageId = AgentMessageId(message.checkpointMessageId),
            createdAt = 0L,
        )
        agentRepository.createBranch(
            userId = UserId(context.userScope),
            agentId = AgentId(message.agentId),
            branch = branch,
        )
            .onSuccess { state -> outboundService.broadcastSnapshot(context.userScope, state) }
            .onFailure { throwable ->
                outboundService.sendOperationFailure(
                    session = context.session,
                    throwable = throwable,
                    requestId = message.requestId,
                )
            }
        return Result.success(Unit)
    }
}
