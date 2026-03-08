package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedui.core.session.SessionState
import org.koin.core.annotation.Factory

@Factory
class EnrichRuntimeStateUseCase {
    operator fun invoke(state: SessionState): SessionState = state
}
