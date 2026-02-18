package io.artemkopan.ai.sharedui.di

import io.artemkopan.ai.sharedui.gateway.PromptGateway
import io.artemkopan.ai.sharedui.state.AppViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val sharedUiModule = module {
    viewModel { AppViewModel(gateway = get()) }
}

fun sharedUiModules(gateway: PromptGateway) = listOf(
    module { single<PromptGateway> { gateway } },
    sharedUiModule,
)
