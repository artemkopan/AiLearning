package io.artemkopan.ai.sharedui.di

import io.artemkopan.ai.sharedui.core.session.AgentId
import io.artemkopan.ai.sharedui.core.session.AgentSessionController
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
        AgentSessionController(
            gateway = get(),
            mapSnapshotToUiStateUseCase = get(),
            normalizeAgentsForConfigUseCase = get(),
            observeActiveModelSelectionUseCase = get(),
            buildUpdatedConfigWithModelMetadataUseCase = get(),
            enrichRuntimeStateUseCase = get(),
        ).also { it.start() }
    }

    viewModel {
        RootViewModel(
            observeSessionStateUseCase = get(),
            disposeSessionUseCase = get(),
            resolveRootShortcutActionUseCase = get(),
            submitFromActiveAgentActionUseCase = get(),
            createAgentActionUseCase = get(),
            selectNextAgentActionUseCase = get(),
            selectPreviousAgentActionUseCase = get(),
        )
    }
    viewModel {
        AgentsSidePanelViewModel(
            observeSessionStateUseCase = get(),
            createAgentActionUseCase = get(),
            selectAgentActionUseCase = get(),
            closeAgentActionUseCase = get(),
            formatAgentTitleUseCase = get(),
        )
    }
    viewModel {
        ErrorDialogViewModel(
            observeErrorUseCase = get(),
            dismissErrorActionUseCase = get(),
        )
    }
    viewModel { (agentId: AgentId) ->
        ConversationColumnViewModel(
            agentId = agentId,
            observeAgentSliceUseCase = get(),
            observeSessionStateUseCase = get(),
            updateDraftMessageActionUseCase = get(),
            submitMessageActionUseCase = get(),
            stopQueueActionUseCase = get(),
            findSlashTokenBoundsUseCase = get(),
            insertCommandTokenUseCase = get(),
            buildCommandPaletteItemsUseCase = get(),
            buildConversationDisplayMessagesUseCase = get(),
            buildConversationStatusTextUseCase = get(),
            conversationCommandRegistry = get(),
            acceptPlanActionUseCase = get(),
        )
    }
    viewModel { (agentId: AgentId) ->
        SettingsColumnViewModel(
            agentId = agentId,
            observeAgentSliceUseCase = get(),
        )
    }
    viewModel { (agentId: AgentId) ->
        ConfigPanelViewModel(
            agentId = agentId,
            observeAgentSliceUseCase = get(),
            updateModelActionUseCase = get(),
            updateMaxOutputTokensActionUseCase = get(),
            updateTemperatureActionUseCase = get(),
            updateStopSequencesActionUseCase = get(),
        )
    }
}

fun sharedUiFeatureModules(gateway: AgentGateway) = listOf(
    module { single<AgentGateway> { gateway } },
    SharedUiScanModule().module,
    sharedUiFeatureModule,
)
