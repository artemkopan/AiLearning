package io.artemkopan.ai.sharedui.di

import io.artemkopan.ai.sharedui.core.session.AgentId
import io.artemkopan.ai.sharedui.core.session.AgentSessionStore
import io.artemkopan.ai.sharedui.feature.agentssidepanel.viewmodel.AgentsSidePanelViewModel
import io.artemkopan.ai.sharedui.feature.configpanel.viewmodel.ConfigPanelViewModel
import io.artemkopan.ai.sharedui.feature.conversationcolumn.viewmodel.ConversationColumnViewModel
import io.artemkopan.ai.sharedui.feature.errordialog.viewmodel.ErrorDialogViewModel
import io.artemkopan.ai.sharedui.feature.root.viewmodel.RootViewModel
import io.artemkopan.ai.sharedui.feature.settingscolumn.viewmodel.SettingsColumnViewModel
import io.artemkopan.ai.sharedui.feature.userprofile.viewmodel.UserProfileViewModel
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
        RootViewModel(
            sessionStore = get(),
            resolveRootShortcutActionUseCase = get(),
            submitFromActiveAgentActionUseCase = get(),
            createAgentActionUseCase = get(),
            selectNextAgentActionUseCase = get(),
            selectPreviousAgentActionUseCase = get(),
        )
    }
    viewModel {
        AgentsSidePanelViewModel(
            sessionStore = get(),
            createAgentActionUseCase = get(),
            selectAgentActionUseCase = get(),
            closeAgentActionUseCase = get(),
            formatAgentTitleUseCase = get(),
        )
    }
    viewModel {
        ErrorDialogViewModel(
            sessionStore = get(),
            dismissErrorActionUseCase = get(),
        )
    }
    viewModel { (agentId: AgentId) ->
        ConversationColumnViewModel(
            agentId = agentId,
            sessionStore = get(),
            updateDraftMessageActionUseCase = get(),
            submitMessageActionUseCase = get(),
            stopQueueActionUseCase = get(),
            createBranchActionUseCase = get(),
            findSlashTokenBoundsUseCase = get(),
            insertCommandTokenUseCase = get(),
            buildCommandPaletteItemsUseCase = get(),
            buildConversationDisplayMessagesUseCase = get(),
            buildConversationStatusTextUseCase = get(),
            conversationCommandRegistry = get(),
        )
    }
    viewModel { (agentId: AgentId) ->
        SettingsColumnViewModel(
            agentId = agentId,
            sessionStore = get(),
            updateAgentModeActionUseCase = get(),
            updateContextStrategyActionUseCase = get(),
            updateContextRecentMessagesActionUseCase = get(),
            updateContextSummarizeEveryActionUseCase = get(),
            updateContextWindowSizeActionUseCase = get(),
            switchBranchActionUseCase = get(),
            deleteBranchActionUseCase = get(),
            keepDigitsUseCase = get(),
        )
    }
    viewModel {
        UserProfileViewModel(
            sessionStore = get(),
            updateUserProfileActionUseCase = get(),
        )
    }
    viewModel { (agentId: AgentId) ->
        ConfigPanelViewModel(
            agentId = agentId,
            sessionStore = get(),
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
