package io.artemkopan.ai.sharedui.di

import io.artemkopan.ai.sharedui.core.session.AgentId
import io.artemkopan.ai.sharedui.core.session.AgentSessionStore
import io.artemkopan.ai.sharedui.factory.KoinSharedUiViewModelFactory
import io.artemkopan.ai.sharedui.factory.SharedUiViewModelFactory
import io.artemkopan.ai.sharedui.feature.agentssidepanel.viewmodel.AgentsSidePanelViewModel
import io.artemkopan.ai.sharedui.feature.configpanel.viewmodel.ConfigPanelViewModel
import io.artemkopan.ai.sharedui.feature.conversationcolumn.viewmodel.ConversationColumnViewModel
import io.artemkopan.ai.sharedui.feature.errordialog.viewmodel.ErrorDialogViewModel
import io.artemkopan.ai.sharedui.feature.root.viewmodel.RootViewModel
import io.artemkopan.ai.sharedui.feature.settingscolumn.viewmodel.SettingsColumnViewModel
import io.artemkopan.ai.sharedui.gateway.AgentGateway
import io.artemkopan.ai.sharedui.usecase.*
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val sharedUiFeatureModule = module {
    single { NormalizeModelUseCase() }
    single { FilterTemperatureInputUseCase() }
    single { NormalizeAgentsForConfigUseCase(normalizeModelUseCase = get()) }
    single { MapSnapshotToUiStateUseCase(normalizeModelUseCase = get()) }
    single { ObserveActiveModelSelectionUseCase(normalizeModelUseCase = get()) }
    single { BuildUpdatedConfigWithModelMetadataUseCase() }
    single { ComputeContextLeftLabelUseCase() }
    single { EnrichRuntimeStateUseCase(computeContextLeftLabelUseCase = get()) }

    single {
        AgentSessionStore(
            gateway = get(),
            normalizeModelUseCase = get(),
            filterTemperatureInputUseCase = get(),
            normalizeAgentsForConfigUseCase = get(),
            mapSnapshotToUiStateUseCase = get(),
            observeActiveModelSelectionUseCase = get(),
            buildUpdatedConfigWithModelMetadataUseCase = get(),
            enrichRuntimeStateUseCase = get(),
        ).also { it.start() }
    }

    viewModel {
        RootViewModel(sessionStore = get())
    }
    viewModel {
        AgentsSidePanelViewModel(sessionStore = get())
    }
    viewModel {
        ErrorDialogViewModel(sessionStore = get())
    }
    viewModel { (agentId: AgentId) ->
        ConversationColumnViewModel(
            agentId = agentId,
            sessionStore = get(),
        )
    }
    viewModel { (agentId: AgentId) ->
        SettingsColumnViewModel(
            agentId = agentId,
            sessionStore = get(),
        )
    }
    viewModel { (agentId: AgentId) ->
        ConfigPanelViewModel(
            agentId = agentId,
            sessionStore = get(),
        )
    }

    single<SharedUiViewModelFactory> { KoinSharedUiViewModelFactory() }
}

fun sharedUiFeatureModules(gateway: AgentGateway) = listOf(
    module { single<AgentGateway> { gateway } },
    sharedUiFeatureModule,
)
