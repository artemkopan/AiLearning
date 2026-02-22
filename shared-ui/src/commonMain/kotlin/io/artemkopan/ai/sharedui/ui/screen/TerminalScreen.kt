package io.artemkopan.ai.sharedui.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedui.state.AppViewModel
import io.artemkopan.ai.sharedui.state.ProjectSelectorViewModel
import io.artemkopan.ai.sharedui.state.UiAction
import io.artemkopan.ai.sharedui.ui.component.ChatSidePanel
import io.artemkopan.ai.sharedui.ui.component.ErrorDialog
import io.artemkopan.ai.sharedui.ui.component.ProjectSelector
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkTheme

@Composable
fun TerminalScreen(
    viewModel: AppViewModel,
    projectSelectorViewModel: ProjectSelectorViewModel,
    onActiveChatChanged: (String?) -> Unit,
) {
    val uiState by viewModel.state.collectAsState()

    LaunchedEffect(uiState.activeChatId) {
        onActiveChatChanged(uiState.activeChatId)
    }

    CyberpunkTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = CyberpunkColors.DarkBackground,
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left sidebar — 180dp
                Column(
                    modifier = Modifier
                        .width(180.dp)
                        .fillMaxHeight()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ScreenHeader()

                    ProjectSelector(
                        viewModel = projectSelectorViewModel,
                        onNewSession = {
                            viewModel.onAction(UiAction.CreateChat())
                        },
                        onProjectSelected = { path ->
                            viewModel.onAction(UiAction.CreateChat(path))
                        },
                    )

                    ChatSidePanel(
                        chats = uiState.chats,
                        activeChatId = uiState.activeChatId,
                        onChatSelected = { viewModel.onAction(UiAction.SelectChat(it)) },
                        onChatClosed = { viewModel.onAction(UiAction.CloseChat(it)) },
                        modifier = Modifier.weight(1f),
                    )
                }

                // Right area — dark placeholder for terminal DOM overlay
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(CyberpunkColors.DarkBackground),
                )
            }
        }

        uiState.error?.let { error ->
            ErrorDialog(
                error = error,
                onDismiss = { viewModel.onAction(UiAction.DismissError) },
            )
        }
    }
}

@Composable
private fun ScreenHeader() {
    Column {
        Text(
            text = "TERMINAL",
            style = MaterialTheme.typography.headlineSmall,
            color = CyberpunkColors.Yellow,
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 4.dp),
            thickness = 2.dp,
            color = CyberpunkColors.Yellow.copy(alpha = 0.4f),
        )
    }
}
