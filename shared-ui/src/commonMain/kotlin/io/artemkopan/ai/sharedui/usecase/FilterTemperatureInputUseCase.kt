package io.artemkopan.ai.sharedui.usecase

class FilterTemperatureInputUseCase {
    operator fun invoke(value: String): String {
        return buildString {
            var hasDot = false
            for (ch in value) {
                when {
                    ch.isDigit() -> append(ch)
                    ch == '.' && !hasDot -> {
                        append(ch)
                        hasDot = true
                    }
                }
            }
        }
    }
}
