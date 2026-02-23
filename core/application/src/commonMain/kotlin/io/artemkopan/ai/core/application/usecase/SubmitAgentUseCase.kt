package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.application.error.AppError
import io.artemkopan.ai.core.application.model.GenerateCommand
import io.artemkopan.ai.core.application.model.SubmitAgentCommand
import io.artemkopan.ai.core.domain.model.Agent
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentResponse
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.model.AgentStatus
import io.artemkopan.ai.core.domain.model.AgentUsage
import io.artemkopan.ai.core.domain.repository.AgentRepository

class SubmitAgentUseCase(
    private val repository: AgentRepository,
    private val generateTextUseCase: GenerateTextUseCase,
) {
    suspend fun execute(command: SubmitAgentCommand): Result<AgentState> {
        val id = command.agentId.trim()
        if (id.isEmpty()) {
            return Result.failure(AppError.Validation("Agent id must not be blank."))
        }

        val agentId = AgentId(id)
        val state = repository.getState().getOrElse { return Result.failure(it) }
        val agent = state.agents.firstOrNull { it.id == agentId }
            ?: return Result.failure(AppError.Validation("Agent not found: $id"))

        val prompt = resolveReferences(agent.prompt.trim(), state.agents)
        if (prompt.isBlank()) {
            return Result.failure(AppError.Validation("Prompt must not be blank."))
        }

        val result = generateTextUseCase.execute(
            GenerateCommand(
                prompt = prompt,
                model = agent.model.trim().takeIf { it.isNotEmpty() },
                temperature = agent.temperature.toDoubleOrNull(),
                maxOutputTokens = agent.maxOutputTokens.toIntOrNull(),
                stopSequences = agent.stopSequences
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .takeIf { it.isNotEmpty() },
                agentMode = agent.agentMode.takeIf { it != "default" },
            )
        )

        return result.fold(
            onSuccess = { output ->
                repository.saveGenerationResult(
                    agentId = agentId,
                    response = AgentResponse(
                        text = output.text,
                        provider = output.provider,
                        model = output.model,
                        usage = output.usage?.let {
                            AgentUsage(
                                inputTokens = it.inputTokens,
                                outputTokens = it.outputTokens,
                                totalTokens = it.totalTokens,
                            )
                        },
                        latencyMs = 0,
                    ),
                    status = AgentStatus("Done"),
                )
            },
            onFailure = { throwable ->
                repository.updateAgentStatus(
                    agentId = agentId,
                    status = AgentStatus("Error: ${throwable.message ?: "Unexpected error"}"),
                )
                Result.failure(throwable)
            }
        )
    }

    private fun resolveReferences(prompt: String, agents: List<Agent>): String {
        val resolved = StringBuilder(prompt.length)
        var cursor = 0

        while (cursor < prompt.length) {
            val tokenStart = prompt.indexOf("[#", cursor)
            if (tokenStart < 0) {
                resolved.append(prompt, cursor, prompt.length)
                break
            }

            resolved.append(prompt, cursor, tokenStart)

            val tokenEnd = prompt.indexOf(']', tokenStart + 2)
            if (tokenEnd < 0) {
                resolved.append(prompt, tokenStart, prompt.length)
                break
            }

            val token = prompt.substring(tokenStart, tokenEnd + 1)
            val content = prompt.substring(tokenStart + 2, tokenEnd)
            val separator = content.indexOf(' ')
            if (separator <= 0 || separator >= content.lastIndex) {
                resolved.append(token)
                cursor = tokenEnd + 1
                continue
            }

            val agentNumber = content.substring(0, separator)
            val referenceType = content.substring(separator + 1)
            if (!agentNumber.all { it.isDigit() } || (referenceType != "prompt" && referenceType != "output")) {
                resolved.append(token)
                cursor = tokenEnd + 1
                continue
            }

            val agent = agents.firstOrNull { it.id.value == "agent-$agentNumber" }
            val replacement = when (referenceType) {
                "prompt" -> agent?.prompt?.takeIf { it.isNotBlank() } ?: token
                "output" -> agent?.response?.text?.takeIf { it.isNotBlank() } ?: token
                else -> token
            }
            resolved.append(replacement)
            cursor = tokenEnd + 1
        }

        return resolved.toString()
    }
}
