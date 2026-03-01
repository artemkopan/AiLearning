package io.artemkopan.ai.sharedui.factory

import androidx.compose.runtime.Composable
import io.artemkopan.ai.sharedui.core.session.AgentId
import io.artemkopan.ai.sharedui.feature.agentssidepanel.viewmodel.AgentsSidePanelViewModel
import io.artemkopan.ai.sharedui.feature.configpanel.viewmodel.ConfigPanelViewModel
import io.artemkopan.ai.sharedui.feature.conversationcolumn.viewmodel.ConversationColumnViewModel
import io.artemkopan.ai.sharedui.feature.errordialog.viewmodel.ErrorDialogViewModel
import io.artemkopan.ai.sharedui.feature.root.viewmodel.RootViewModel
import io.artemkopan.ai.sharedui.feature.settingscolumn.viewmodel.SettingsColumnViewModel

interface SharedUiViewModelFactory {
    @Composable
    fun root(): RootViewModel

    @Composable
    fun agentsSidePanel(): AgentsSidePanelViewModel

    @Composable
    fun conversation(agentId: AgentId): ConversationColumnViewModel

    @Composable
    fun settings(agentId: AgentId): SettingsColumnViewModel

    @Composable
    fun config(agentId: AgentId): ConfigPanelViewModel

    @Composable
    fun errorDialog(): ErrorDialogViewModel
}
