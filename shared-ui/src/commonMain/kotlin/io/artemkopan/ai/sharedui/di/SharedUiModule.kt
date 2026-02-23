package io.artemkopan.ai.sharedui.di

import io.artemkopan.ai.sharedui.gateway.AgentGateway
import io.artemkopan.ai.sharedui.state.AppViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val sharedUiModule = module {
    viewModel { AppViewModel(gateway = get()) }
}

fun sharedUiModules(gateway: AgentGateway) = listOf(
    module { single<AgentGateway> { gateway } },
    sharedUiModule,
)
