package io.artemkopan.ai.core.application.usecase

object InvariantsPromptBuilder {

    fun buildInvariantsBlock(invariants: List<String>): String {
        if (invariants.isEmpty()) return ""
        val rules = invariants.mapIndexed { i, rule ->
            "${i + 1}. $rule"
        }.joinToString("\n")
        return """

INVARIANTS (mandatory constraints you MUST follow):
$rules

IMPORTANT: If a user request conflicts with any invariant above, you MUST:
1. Refuse to execute the conflicting part
2. Clearly explain which invariant would be violated and why
3. Suggest an alternative approach that respects all invariants"""
    }
}
