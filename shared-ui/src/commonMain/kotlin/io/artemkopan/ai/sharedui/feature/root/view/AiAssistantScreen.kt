package io.artemkopan.ai.sharedui.feature.root.view

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedui.factory.SharedUiViewModelFactory
import io.artemkopan.ai.sharedui.feature.agentssidepanel.view.AgentsSidePanelFeature
import io.artemkopan.ai.sharedui.feature.conversationcolumn.view.ConversationColumnFeature
import io.artemkopan.ai.sharedui.feature.errordialog.view.ErrorDialogFeature
import io.artemkopan.ai.sharedui.feature.root.model.RootShortcutEvent
import io.artemkopan.ai.sharedui.feature.root.model.RootShortcutKey
import io.artemkopan.ai.sharedui.feature.root.viewmodel.RootViewModel
import io.artemkopan.ai.sharedui.feature.settingscolumn.view.SettingsColumnFeature
import io.artemkopan.ai.sharedui.feature.userprofile.viewmodel.UserProfileViewModel
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkTheme

@Composable
fun AiAssistantScreen(
    factory: SharedUiViewModelFactory,
) {
    val rootViewModel: RootViewModel = factory.root()
    val rootState by rootViewModel.state.collectAsState()

    val agentsViewModel = factory.agentsSidePanel()
    val errorDialogViewModel = factory.errorDialog()
    val userProfileViewModel: UserProfileViewModel = factory.userProfile()

    val activeAgentId = rootState.activeAgentId
    val conversationViewModel = activeAgentId?.let { factory.conversation(it) }
    val taskStateManagerViewModel = activeAgentId?.let { factory.taskStateManager(it) }
    val settingsViewModel = activeAgentId?.let { factory.settings(it) }
    val configViewModel = activeAgentId?.let { factory.config(it) }

    val rootFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        rootFocusRequester.requestFocus()
    }

    CyberpunkTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(rootFocusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    rootViewModel.onShortcut(event.toRootShortcutEvent())
                },
            color = CyberpunkColors.DarkBackground,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ScreenHeader()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AgentsSidePanelFeature(
                        viewModel = agentsViewModel,
                        modifier = Modifier
                            .width(180.dp)
                            .fillMaxHeight(),
                    )

                    if (conversationViewModel != null) {
                        ConversationColumnFeature(
                            viewModel = conversationViewModel,
                            taskStateManagerViewModel = taskStateManagerViewModel,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }

                    if (settingsViewModel != null && configViewModel != null) {
                        SettingsColumnFeature(
                            settingsViewModel = settingsViewModel,
                            configViewModel = configViewModel,
                            userProfileViewModel = userProfileViewModel,
                            modifier = Modifier
                                .width(220.dp)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }

        ErrorDialogFeature(viewModel = errorDialogViewModel)
    }
}

private fun KeyEvent.toRootShortcutEvent(): RootShortcutEvent {
    return RootShortcutEvent(
        key = when (key) {
            Key.Enter -> RootShortcutKey.ENTER
            Key.DirectionDown -> RootShortcutKey.DIRECTION_DOWN
            Key.DirectionUp -> RootShortcutKey.DIRECTION_UP
            Key.N -> RootShortcutKey.N
            else -> RootShortcutKey.OTHER
        },
        isCtrlPressed = isCtrlPressed,
        isAltPressed = isAltPressed,
    )
}

@Composable
private fun ScreenHeader() {
    Column {
        Text(
            text = "AI assistant",
            style = MaterialTheme.typography.headlineSmall,
            color = CyberpunkColors.Yellow,
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 6.dp),
            thickness = 2.dp,
            color = CyberpunkColors.Yellow.copy(alpha = 0.4f),
        )
    }
}
