package io.artemkopan.ai.sharedui.di

import io.artemkopan.ai.sharedui.core.session.AgentId
import io.artemkopan.ai.sharedui.core.session.AgentSessionStore
import io.artemkopan.ai.sharedui.feature.agentssidepanel.viewmodel.AgentsSidePanelViewModel
import io.artemkopan.ai.sharedui.feature.configpanel.viewmodel.ConfigPanelViewModel
import io.artemkopan.ai.sharedui.feature.conversationcolumn.viewmodel.ConversationColumnViewModel
import io.artemkopan.ai.sharedui.feature.errordialog.viewmodel.ErrorDialogViewModel
import io.artemkopan.ai.sharedui.feature.root.viewmodel.RootViewModel
import io.artemkopan.ai.sharedui.feature.settingscolumn.viewmodel.SettingsColumnViewModel
import io.artemkopan.ai.sharedui.gateway.AgentGateway
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.koin.ksp.generated.module

val sharedUiFeatureModule = module {
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
}

fun sharedUiFeatureModules(gateway: AgentGateway) = listOf(
    module { single<AgentGateway> { gateway } },
    SharedUiScanModule().module,
    sharedUiFeatureModule,
)
