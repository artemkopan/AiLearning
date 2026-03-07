package io.artemkopan.ai.backend.agent.ws

import io.artemkopan.ai.core.application.usecase.task.TransitionTaskPhaseUseCase
import io.artemkopan.ai.core.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransitionTaskPhaseUseCaseTest {

    @Test
    fun `transitions from planning to execution`() = runBlocking {
        val task = AgentTask(
            id = TaskId("task-1"),
            agentId = AgentId("agent-1"),
            title = "Test",
            currentPhase = TaskPhase.PLANNING,
            steps = listOf(
                TaskStep(0, TaskPhase.PLANNING, "Plan", "Plan it", TaskStepStatus.PENDING),
                TaskStep(1, TaskPhase.EXECUTION, "Execute", "Do it", TaskStepStatus.PENDING),
            ),
            currentStepIndex = 0,
            createdAt = 0L,
            updatedAt = 0L,
        )
        val repo = FakeTaskRepository(activeTask = task)
        val useCase = TransitionTaskPhaseUseCase(repository = repo)

        val result = useCase.execute(
            userId = "user-1",
            taskId = "task-1",
            fromPhase = TaskPhase.PLANNING,
            targetPhase = TaskPhase.EXECUTION,
            reason = "Step completed",
        )

        assertTrue(result.isSuccess)
        assertEquals(1, repo.phaseUpdates.size)
        assertEquals(TaskId("task-1"), repo.phaseUpdates.single().first)
        assertEquals(TaskPhase.EXECUTION, repo.phaseUpdates.single().second)
        assertEquals(1, repo.transitions.size)
        assertEquals(TaskPhase.PLANNING, repo.transitions.single().fromPhase)
        assertEquals(TaskPhase.EXECUTION, repo.transitions.single().toPhase)
        assertEquals(TaskPhase.EXECUTION, repo.activeTask?.currentPhase)
    }

    @Test
    fun `transitions through full lifecycle planning to done`() = runBlocking {
        val task = AgentTask(
            id = TaskId("task-1"),
            agentId = AgentId("agent-1"),
            title = "Test",
            currentPhase = TaskPhase.PLANNING,
            steps = emptyList(),
            currentStepIndex = 0,
            createdAt = 0L,
            updatedAt = 0L,
        )
        val repo = FakeTaskRepository(activeTask = task)
        val useCase = TransitionTaskPhaseUseCase(repository = repo)

        useCase.execute("user-1", "task-1", TaskPhase.PLANNING, TaskPhase.EXECUTION, "")
        useCase.execute("user-1", "task-1", TaskPhase.EXECUTION, TaskPhase.VALIDATION, "")
        useCase.execute("user-1", "task-1", TaskPhase.VALIDATION, TaskPhase.DONE, "")

        assertEquals(3, repo.phaseUpdates.size)
        assertEquals(TaskPhase.EXECUTION, repo.phaseUpdates[0].second)
        assertEquals(TaskPhase.VALIDATION, repo.phaseUpdates[1].second)
        assertEquals(TaskPhase.DONE, repo.phaseUpdates[2].second)
        assertEquals(TaskPhase.DONE, repo.activeTask?.currentPhase)
    }
}
