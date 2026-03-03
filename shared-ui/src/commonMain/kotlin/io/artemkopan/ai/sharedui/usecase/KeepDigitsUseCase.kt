package io.artemkopan.ai.sharedui.usecase

import org.koin.core.annotation.Factory

@Factory
class KeepDigitsUseCase {
    operator fun invoke(value: String): String = value.filter { it.isDigit() }
}
