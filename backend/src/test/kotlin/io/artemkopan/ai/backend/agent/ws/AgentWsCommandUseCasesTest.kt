package io.artemkopan.ai.backend.agent.ws

import io.artemkopan.ai.backend.agent.ws.usecase.*
import io.artemkopan.ai.backend.config.AppConfig
import io.artemkopan.ai.backend.di.appModules
import io.artemkopan.ai.core.application.usecase.*
import io.artemkopan.ai.core.application.usecase.shortcut.ParseMemoryLayerShortcutTokenUseCase
import io.artemkopan.ai.core.application.usecase.shortcut.ParseStatsShortcutTokensUseCase
import io.artemkopan.ai.core.application.usecase.shortcut.ResolveStatsShortcutsUseCase
import io.artemkopan.ai.core.domain.model.AgentMessage
import io.artemkopan.ai.core.domain.model.AgentMessageId
import io.artemkopan.ai.core.domain.model.AgentMessageRole
import io.artemkopan.ai.sharedcontract.CreateAgentCommandDto
import io.artemkopan.ai.sharedcontract.SendAgentMessageCommandDto
import io.artemkopan.ai.sharedcontract.StopAgentMessageCommandDto
import io.artemkopan.ai.sharedcontract.SubmitAgentCommandDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentWsCommandUseCasesTest {
    private val context = AgentWsMessageContext(
        userScope = "user-1",
        session = dummySession(),
    )

    @Test
    fun `create agent command broadcasts snapshot`() = runBlocking {
        val repository = FakeAgentRepository(currentState = testAgentState(agentId = "agent-1", version = 1))
        repository.createAgentResult = testAgentState(agentId = "agent-2", version = 2)
        val outbound = RecordingOutboundService()
        val useCase = CreateAgentWsUseCase(
            createAgentUseCase = CreateAgentUseCase(repository = repository),
            outboundService = outbound,
        )

        val result = useCase.execute(context, CreateAgentCommandDto(requestId = "create-1"))

        assertTrue(result.isSuccess)
        assertEquals(1, outbound.broadcasts.size)
        assertEquals("user-1", outbound.broadcasts.single().first)
        assertEquals(2, outbound.broadcasts.single().second.version)
    }

    @Test
    fun `send agent message busy guard sends error`() = runBlocking {
        val processingRegistry = AgentWsProcessingRegistry()
        val existingJob = Job()
        processingRegistry.registerProcessing(
            userScope = context.userScope,
            agentId = "agent-1",
            messageId = "message-1",
            job = existingJob,
        )
        val outbound = RecordingOutboundService()

        val config = AppConfig(
            port = 8080,
            deepseekApiKey = "test-key",
            defaultModel = "deepseek-chat",
            corsOrigin = "localhost:8081",
            dbHost = "localhost",
            dbPort = 5432,
            dbName = "ai_learning_test",
            dbUser = "postgres",
            dbPassword = "postgres",
            dbSsl = false,
        )
        val koinApp = koinApplication {
            modules(appModules(config))
        }

        try {
            val useCase = SendAgentMessageWsUseCase(
                processingRegistry = processingRegistry,
                startAgentMessageUseCase = koinApp.koin.get<StartAgentMessageUseCase>(),
                completeAgentMessageUseCase = koinApp.koin.get<CompleteAgentMessageUseCase>(),
                failAgentMessageUseCase = koinApp.koin.get<FailAgentMessageUseCase>(),
                generateTextUseCase = koinApp.koin.get<GenerateTextUseCase>(),
                parseStatsShortcutTokensUseCase = koinApp.koin.get<ParseStatsShortcutTokensUseCase>(),
                parseMemoryLayerShortcutTokenUseCase = koinApp.koin.get<ParseMemoryLayerShortcutTokenUseCase>(),
                resolveStatsShortcutsUseCase = koinApp.koin.get<ResolveStatsShortcutsUseCase>(),
                switchAgentMemoryLayerUseCase = koinApp.koin.get<SwitchAgentMemoryLayerUseCase>(),
                agentRepository = koinApp.koin.get(),
                getActiveTaskUseCase = koinApp.koin.get(),
                createTaskUseCase = koinApp.koin.get(),
                transitionTaskPhaseUseCase = koinApp.koin.get(),
                taskRepository = koinApp.koin.get(),
                parsePhaseResponseUseCase = koinApp.koin.get(),
                mapper = koinApp.koin.get(),
                outboundService = outbound,
            )

            val result = useCase.execute(
                context,
                SendAgentMessageCommandDto(
                    agentId = "agent-1",
                    text = "hello",
                    requestId = "send-1",
                )
            )

            assertTrue(result.isSuccess)
            assertEquals(1, outbound.errors.size)
            assertEquals("send-1", outbound.errors.single().first)
            assertEquals("This agent already has a processing message.", outbound.errors.single().second)
        } finally {
            existingJob.cancel()
            processingRegistry.close()
            koinApp.close()
        }
    }

    @Test
    fun `stop agent message command sets stop and broadcasts updated state`() = runBlocking {
        val repository = FakeAgentRepository(currentState = testAgentState(agentId = "agent-1", version = 5))
        repository.findMessageResult = AgentMessage(
            id = AgentMessageId("message-1"),
            role = AgentMessageRole.ASSISTANT,
            text = "processing",
            status = "processing",
            createdAt = 1L,
        )
        val processingRegistry = AgentWsProcessingRegistry()
        val outbound = RecordingOutboundService()
        val useCase = StopAgentMessageWsUseCase(
            processingRegistry = processingRegistry,
            stopAgentMessageUseCase = StopAgentMessageUseCase(repository = repository),
            outboundService = outbound,
        )
        val activeJob = Job()
        processingRegistry.registerProcessing(
            userScope = context.userScope,
            agentId = "agent-1",
            messageId = "message-1",
            job = activeJob,
        )

        val result = useCase.execute(
            context,
            StopAgentMessageCommandDto(
                agentId = "agent-1",
                messageId = "message-1",
                requestId = "stop-1",
            )
        )

        assertTrue(result.isSuccess)
        assertTrue(activeJob.isCancelled)
        assertTrue(processingRegistry.isStopRequested(context.userScope, "agent-1", "message-1"))
        assertEquals("stopped", repository.updatedMessageStatuses.last())
        assertEquals("stopped", repository.updatedStatuses.last().value)
        assertEquals(1, outbound.broadcasts.size)

        processingRegistry.close()
    }

    @Test
    fun `submit agent command returns deprecation error`() = runBlocking {
        val outbound = RecordingOutboundService()
        val useCase = SubmitAgentDeprecatedWsUseCase(outboundService = outbound)

        val result = useCase.execute(context, SubmitAgentCommandDto(agentId = "agent-1", requestId = "submit-1"))

        assertTrue(result.isSuccess)
        assertEquals(1, outbound.errors.size)
        assertEquals("submit-1", outbound.errors.single().first)
        assertEquals("submit_agent is deprecated. Use send_agent_message.", outbound.errors.single().second)
    }
}
