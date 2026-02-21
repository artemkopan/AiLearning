package io.artemkopan.ai.backend.di

import io.artemkopan.ai.backend.config.AppConfig
import io.artemkopan.ai.backend.terminal.ChatManager
import io.artemkopan.ai.backend.terminal.EventBus
import io.artemkopan.ai.backend.terminal.PtyBridge
import io.artemkopan.ai.backend.terminal.Shell
import io.artemkopan.ai.backend.terminal.StatusManager
import org.koin.dsl.module

val terminalModule = module {
    single { Shell() }
    single { StatusManager() }
    single { EventBus() }
    single { ChatManager(get(), get(), get()) }
    single { PtyBridge(get()) }
}

fun appModules(config: AppConfig) = listOf(
    module { single { config } },
    terminalModule,
)
