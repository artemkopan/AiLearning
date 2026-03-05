package io.artemkopan.ai.core.domain.model

data class UserProfile(
    val userId: UserId,
    val communicationStyle: CommunicationStyle,
    val responseFormat: ResponseFormat,
    val restrictions: List<String>,
    val customInstructions: String,
    val updatedAt: Long,
)

enum class CommunicationStyle {
    CONCISE,
    DETAILED,
    SOCRATIC,
    CASUAL,
    FORMAL,
}

enum class ResponseFormat {
    PLAIN_TEXT,
    MARKDOWN,
    STRUCTURED,
    CODE_FOCUSED,
}
