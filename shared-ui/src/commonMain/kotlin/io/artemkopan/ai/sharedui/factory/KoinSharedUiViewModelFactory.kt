package io.artemkopan.ai.sharedui.factory

import androidx.compose.runtime.Composable
import io.artemkopan.ai.sharedui.core.session.AgentId
import io.artemkopan.ai.sharedui.feature.agentssidepanel.viewmodel.AgentsSidePanelViewModel
import io.artemkopan.ai.sharedui.feature.configpanel.viewmodel.ConfigPanelViewModel
import io.artemkopan.ai.sharedui.feature.conversationcolumn.viewmodel.ConversationColumnViewModel
import io.artemkopan.ai.sharedui.feature.errordialog.viewmodel.ErrorDialogViewModel
import io.artemkopan.ai.sharedui.feature.root.viewmodel.RootViewModel
import io.artemkopan.ai.sharedui.feature.settingscolumn.viewmodel.SettingsColumnViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.Single
import org.koin.core.parameter.parametersOf

@Single(binds = [SharedUiViewModelFactory::class])
class KoinSharedUiViewModelFactory : SharedUiViewModelFactory {
    @Composable
    override fun root(): RootViewModel = koinViewModel()

    @Composable
    override fun agentsSidePanel(): AgentsSidePanelViewModel = koinViewModel()

    @Composable
    override fun conversation(agentId: AgentId): ConversationColumnViewModel {
        return koinViewModel(
            key = "conversation-${agentId.value}",
            parameters = { parametersOf(agentId) },
        )
    }

    @Composable
    override fun settings(agentId: AgentId): SettingsColumnViewModel {
        return koinViewModel(
            key = "settings-${agentId.value}",
            parameters = { parametersOf(agentId) },
        )
    }

    @Composable
    override fun config(agentId: AgentId): ConfigPanelViewModel {
        return koinViewModel(
            key = "config-${agentId.value}",
            parameters = { parametersOf(agentId) },
        )
    }

    @Composable
    override fun errorDialog(): ErrorDialogViewModel = koinViewModel()
}
