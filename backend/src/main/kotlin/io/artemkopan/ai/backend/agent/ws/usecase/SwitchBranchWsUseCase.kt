package io.artemkopan.ai.backend.agent.ws.usecase

import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.UserId
import io.artemkopan.ai.core.domain.repository.AgentRepository
import io.artemkopan.ai.sharedcontract.SwitchBranchCommandDto

class SwitchBranchWsUseCase(
    private val agentRepository: AgentRepository,
    private val outboundService: AgentWsOutboundService,
) : AgentWsMessageUseCase<SwitchBranchCommandDto> {
    override val messageType = SwitchBranchCommandDto::class

    override suspend fun execute(context: AgentWsMessageContext, message: SwitchBranchCommandDto): Result<Unit> {
        agentRepository.switchBranch(
            userId = UserId(context.userScope),
            agentId = AgentId(message.agentId),
            branchId = message.branchId,
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
