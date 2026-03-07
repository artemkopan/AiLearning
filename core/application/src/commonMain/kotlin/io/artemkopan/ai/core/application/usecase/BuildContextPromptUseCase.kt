package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.application.model.AssistantMemoryModel
import io.artemkopan.ai.core.application.model.LongTermMemoryLayer
import io.artemkopan.ai.core.application.model.ShortTermMemoryLayer
import io.artemkopan.ai.core.application.model.WorkingMemoryLayer
import io.artemkopan.ai.core.domain.model.AgentMessage
import io.artemkopan.ai.core.domain.model.AgentMessageRole
import io.artemkopan.ai.core.domain.model.RetrievedContextChunk

class BuildContextPromptUseCase {
    fun execute(
        summary: String?,
        messages: List<AgentMessage>,
        retrievedMemory: List<RetrievedContextChunk> = emptyList(),
    ): String {
        return execute(
            AssistantMemoryModel(
                shortTerm = ShortTermMemoryLayer(
                    dialogueTurns = messages.filter { !it.status.equals(STATUS_STOPPED, ignoreCase = true) },
                ),
                working = WorkingMemoryLayer(
                    taskDataSummary = summary?.trim().orEmpty(),
                ),
                longTerm = LongTermMemoryLayer(
                    profileAndDecisions = "",
                    retrievedKnowledge = retrievedMemory,
                ),
            )
        )
    }

    fun execute(memory: AssistantMemoryModel): String {
        val shortTerm = memory.shortTerm.dialogueTurns
            .filter { !it.status.equals(STATUS_STOPPED, ignoreCase = true) }
        val workingSummary = memory.working.taskDataSummary.trim()
        val taskState = memory.working.taskStateSnippet.trim()
        val longTermProfile = memory.longTerm.profileAndDecisions.trim()
        val retrievedMemory = memory.longTerm.retrievedKnowledge

        val userProfileText = memory.longTerm.userProfileSnippet.trim()

        if (workingSummary.isBlank() && taskState.isBlank() && shortTerm.isEmpty() && longTermProfile.isBlank()
            && retrievedMemory.isEmpty() && userProfileText.isBlank()) return ""

        return buildString {
            appendLine("Continue the conversation as ASSISTANT.")
            appendLine("Keep response concise and relevant to the latest USER message.")
            appendLine("Use memory layers intentionally:")
            appendLine("- SHORT-TERM for immediate dialogue context.")
            appendLine("- WORKING for current task summary/data.")
            appendLine("- LONG-TERM for persistent profile, decisions, and retrieved knowledge.")

            if (workingSummary.isNotBlank()) {
                appendLine()
                appendLine("WORKING MEMORY (CURRENT TASK DATA):")
                appendLine(workingSummary)
            }

            if (taskState.isNotBlank()) {
                appendLine()
                appendLine("WORKING MEMORY (TASK STATE MACHINE):")
                appendLine(taskState)
            }

            if (longTermProfile.isNotBlank()) {
                appendLine()
                appendLine("LONG-TERM MEMORY (PROFILE / DECISIONS):")
                appendLine(longTermProfile)
            }

            if (userProfileText.isNotBlank()) {
                appendLine()
                appendLine("LONG-TERM MEMORY (USER PROFILE PREFERENCES):")
                appendLine(userProfileText)
            }

            if (retrievedMemory.isNotEmpty()) {
                appendLine()
                appendLine("LONG-TERM MEMORY (RETRIEVED KNOWLEDGE):")
                retrievedMemory.forEachIndexed { index, chunk ->
                    appendLine("${index + 1}. ${chunk.text}")
                }
            }

            if (shortTerm.isNotEmpty()) {
                appendLine()
                appendLine("SHORT-TERM MEMORY (CURRENT DIALOGUE):")
                shortTerm.forEach { message ->
                    val prefix = when (message.role) {
                        AgentMessageRole.USER -> "USER"
                        AgentMessageRole.ASSISTANT -> "ASSISTANT"
                    }
                    appendLine("$prefix: ${message.text}")
                }
            }
        }
    }
}

private const val STATUS_STOPPED = "stopped"
