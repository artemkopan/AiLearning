package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.domain.model.UserProfile

class BuildUserProfilePromptSnippetUseCase {
    fun execute(profile: UserProfile?): String {
        if (profile == null) return ""
        return buildString {
            appendLine("Communication style: ${profile.communicationStyle.name.lowercase().replace('_', ' ')}")
            appendLine("Response format: ${profile.responseFormat.name.lowercase().replace('_', ' ')}")
            if (profile.restrictions.isNotEmpty()) {
                appendLine("Restrictions: ${profile.restrictions.joinToString("; ")}")
            }
            if (profile.customInstructions.isNotBlank()) {
                appendLine("Custom instructions: ${profile.customInstructions}")
            }
        }
    }
}
