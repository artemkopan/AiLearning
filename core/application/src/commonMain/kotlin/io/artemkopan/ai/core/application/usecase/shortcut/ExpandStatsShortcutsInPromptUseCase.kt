package io.artemkopan.ai.core.application.usecase.shortcut

import io.artemkopan.ai.core.domain.model.UserId

class ExpandStatsShortcutsInPromptUseCase(
    private val parseStatsShortcutTokensUseCase: ParseStatsShortcutTokensUseCase,
    private val resolveStatsShortcutsUseCase: ResolveStatsShortcutsUseCase,
) {
    suspend fun execute(userId: UserId, prompt: String): Result<String> {
        val tokens = parseStatsShortcutTokensUseCase.execute(prompt)
        if (tokens.isEmpty()) return Result.success(prompt)

        val resolved = resolveStatsShortcutsUseCase.execute(userId, tokens).getOrElse {
            return Result.failure(it)
        }
        var expanded = prompt
        resolved.forEach { (token, snippet) ->
            expanded = expanded.replace(token, snippet)
        }
        return Result.success(expanded)
    }
}
