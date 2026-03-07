package io.artemkopan.ai.backend.agent.ws

import io.artemkopan.ai.backend.agent.ws.usecase.AgentWsMessageContext
import io.artemkopan.ai.backend.agent.ws.usecase.AgentWsMessageUseCase
import io.artemkopan.ai.core.application.usecase.GetAgentStateUseCase
import io.artemkopan.ai.sharedcontract.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AgentWsMessageHandlerTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        classDiscriminator = "type"
    }

    @Test
    fun `dispatches to use case mapped by dto class`() = runBlocking {
        val recordingUseCase = RecordingCreateAgentUseCase()
        val handlers = buildCompleteHandlerMap(
            createAgentUseCase = recordingUseCase,
        )
        val handler = newHandler(handlers)

        val payload = json.encodeToString(
            AgentWsClientMessageDto.serializer(),
            CreateAgentCommandDto(requestId = "req-1"),
        )

        handler.onTextMessage(
            userScope = "user-1",
            session = dummySession(),
            text = payload,
        )

        assertTrue(recordingUseCase.executed)
        assertEquals("user-1", recordingUseCase.lastUserScope)
        assertEquals("req-1", recordingUseCase.lastRequestId)
    }

    @Test
    fun `fails fast when one dto binding is missing`() {
        val handlers = buildCompleteHandlerMap().toMutableMap().apply {
            remove(DeleteBranchCommandDto::class)
        }

        assertFailsWith<IllegalArgumentException> {
            newHandler(handlers)
        }
    }

    private fun newHandler(
        handlers: Map<KClass<out AgentWsClientMessageDto>, AgentWsMessageUseCase<out AgentWsClientMessageDto>>,
    ): AgentWsMessageHandler {
        return AgentWsMessageHandler(
            getAgentStateUseCase = GetAgentStateUseCase(FakeAgentRepository()),
            sessionRegistry = AgentWsSessionRegistry(),
            outboundService = RecordingOutboundService(),
            handlersByMessageType = handlers,
            json = json,
            logger = LoggerFactory.getLogger("AgentWsMessageHandlerTest"),
        )
    }
}

private class RecordingCreateAgentUseCase : AgentWsMessageUseCase<CreateAgentCommandDto> {
    override val messageType: KClass<CreateAgentCommandDto> = CreateAgentCommandDto::class

    var executed: Boolean = false
    var lastUserScope: String? = null
    var lastRequestId: String? = null

    override suspend fun execute(context: AgentWsMessageContext, message: CreateAgentCommandDto): Result<Unit> {
        executed = true
        lastUserScope = context.userScope
        lastRequestId = message.requestId
        return Result.success(Unit)
    }
}

private fun buildCompleteHandlerMap(
    createAgentUseCase: AgentWsMessageUseCase<CreateAgentCommandDto> = NoopUseCase(CreateAgentCommandDto::class),
): Map<KClass<out AgentWsClientMessageDto>, AgentWsMessageUseCase<out AgentWsClientMessageDto>> {
    return listOf<AgentWsMessageUseCase<out AgentWsClientMessageDto>>(
        NoopUseCase(SubscribeAgentsDto::class),
        createAgentUseCase,
        NoopUseCase(SelectAgentCommandDto::class),
        NoopUseCase(UpdateAgentDraftCommandDto::class),
        NoopUseCase(CloseAgentCommandDto::class),
        NoopUseCase(SubmitAgentCommandDto::class),
        NoopUseCase(SendAgentMessageCommandDto::class),
        NoopUseCase(StopAgentMessageCommandDto::class),
        NoopUseCase(CreateBranchCommandDto::class),
        NoopUseCase(SwitchBranchCommandDto::class),
        NoopUseCase(DeleteBranchCommandDto::class),
        NoopUseCase(UpdateUserProfileCommandDto::class),
        NoopUseCase(CreateTaskCommandDto::class),
        NoopUseCase(TransitionTaskPhaseCommandDto::class),
        NoopUseCase(UpdateTaskStepCommandDto::class),
    ).associateBy { it.messageType }
}

private class NoopUseCase<T : AgentWsClientMessageDto>(
    override val messageType: KClass<T>,
) : AgentWsMessageUseCase<T> {
    override suspend fun execute(context: AgentWsMessageContext, message: T): Result<Unit> = Result.success(Unit)
}
