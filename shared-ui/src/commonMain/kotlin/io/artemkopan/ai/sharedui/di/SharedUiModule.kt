package io.artemkopan.ai.sharedui.di

import io.artemkopan.ai.sharedui.gateway.BACKEND_BASE_URL
import io.artemkopan.ai.sharedui.gateway.EventsClient
import io.artemkopan.ai.sharedui.gateway.HttpTerminalGateway
import io.artemkopan.ai.sharedui.gateway.TerminalGateway
import io.artemkopan.ai.sharedui.gateway.createPlatformHttpClient
import io.artemkopan.ai.sharedui.state.AppViewModel
import io.artemkopan.ai.sharedui.usecase.CreateChatUseCase
import io.artemkopan.ai.sharedui.usecase.LoadChatsUseCase
import io.artemkopan.ai.sharedui.usecase.LoadProjectsUseCase
import io.artemkopan.ai.sharedui.usecase.ObserveStatusEventsUseCase
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val sharedModule = module {
    single { createPlatformHttpClient(BACKEND_BASE_URL) }
    single<TerminalGateway> { HttpTerminalGateway(get()) }
    single { EventsClient(get()) }
    factory { LoadProjectsUseCase(get()) }
    factory { CreateChatUseCase(get()) }
    factory { LoadChatsUseCase(get()) }
    factory { ObserveStatusEventsUseCase(get()) }
    viewModel { AppViewModel(get(), get(), get(), get()) }
}
