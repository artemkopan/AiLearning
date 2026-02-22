package io.artemkopan.ai.sharedui.di

import io.artemkopan.ai.sharedui.gateway.EventsClient
import io.artemkopan.ai.sharedui.gateway.HttpTerminalGateway
import io.artemkopan.ai.sharedui.gateway.TerminalGateway
import io.artemkopan.ai.sharedui.gateway.createPlatformHttpClient
import io.artemkopan.ai.sharedui.gateway.resolveBackendBaseUrl
import io.artemkopan.ai.sharedui.state.AppViewModel
import io.artemkopan.ai.sharedui.state.ProjectSelectorViewModel
import io.artemkopan.ai.sharedui.usecase.CloseChatUseCase
import io.artemkopan.ai.sharedui.usecase.CreateChatUseCase
import io.artemkopan.ai.sharedui.usecase.LoadChatsUseCase
import io.artemkopan.ai.sharedui.usecase.LoadProjectsUseCase
import io.artemkopan.ai.sharedui.usecase.ObserveStatusEventsUseCase
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val sharedModule = module {
    single { createPlatformHttpClient(resolveBackendBaseUrl()) }
    single<TerminalGateway> { HttpTerminalGateway(get()) }
    single { EventsClient(get()) }
    factory { LoadProjectsUseCase(get()) }
    factory { CreateChatUseCase(get()) }
    factory { CloseChatUseCase(get()) }
    factory { LoadChatsUseCase(get()) }
    factory { ObserveStatusEventsUseCase(get()) }
    viewModel { AppViewModel(get(), get(), get(), get()) }
    viewModel { ProjectSelectorViewModel(get()) }
}
