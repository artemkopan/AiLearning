package io.artemkopan.ai.backend.agent.ws.usecase

import io.artemkopan.ai.core.application.model.UpdateUserProfileCommand
import io.artemkopan.ai.core.application.usecase.GetUserProfileUseCase
import io.artemkopan.ai.core.application.usecase.UpdateUserProfileUseCase
import io.artemkopan.ai.core.domain.model.CommunicationStyle
import io.artemkopan.ai.core.domain.model.ResponseFormat
import io.artemkopan.ai.sharedcontract.AgentWsServerMessageDto
import io.artemkopan.ai.sharedcontract.UpdateUserProfileCommandDto
import io.artemkopan.ai.sharedcontract.UserProfileSnapshotDto
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Factory
import kotlin.reflect.KClass

@Factory(binds = [AgentWsMessageUseCase::class])
class UpdateUserProfileWsUseCase(
    private val updateUserProfileUseCase: UpdateUserProfileUseCase,
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val json: Json,
) : AgentWsMessageUseCase<UpdateUserProfileCommandDto> {

    override val messageType: KClass<UpdateUserProfileCommandDto> = UpdateUserProfileCommandDto::class

    override suspend fun execute(
        context: AgentWsMessageContext,
        message: UpdateUserProfileCommandDto,
    ): Result<Unit> {
        val command = UpdateUserProfileCommand(
            communicationStyle = CommunicationStyle.valueOf(message.communicationStyle.uppercase()),
            responseFormat = ResponseFormat.valueOf(message.responseFormat.uppercase()),
            restrictions = message.restrictions,
            customInstructions = message.customInstructions,
        )
        updateUserProfileUseCase.execute(context.userScope, command)
            .getOrElse { return Result.failure(it) }

        val profile = getUserProfileUseCase.execute(context.userScope).getOrNull()
        if (profile != null) {
            val payload = UserProfileSnapshotDto(
                communicationStyle = profile.communicationStyle.name.lowercase(),
                responseFormat = profile.responseFormat.name.lowercase(),
                restrictions = profile.restrictions,
                customInstructions = profile.customInstructions,
            )
            context.session.send(
                Frame.Text(json.encodeToString(AgentWsServerMessageDto.serializer(), payload))
            )
        }
        return Result.success(Unit)
    }
}
