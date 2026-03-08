package io.artemkopan.ai.sharedui.feature.settingscolumn.view

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedui.feature.configpanel.view.ConfigPanelFeature
import io.artemkopan.ai.sharedui.feature.configpanel.viewmodel.ConfigPanelViewModel
import io.artemkopan.ai.sharedui.feature.settingscolumn.viewmodel.SettingsColumnViewModel
import io.artemkopan.ai.sharedui.ui.component.CyberpunkPanel
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors

@Composable
fun SettingsColumnFeature(
    settingsViewModel: SettingsColumnViewModel,
    configViewModel: ConfigPanelViewModel,
    modifier: Modifier = Modifier,
) {
    val state by settingsViewModel.state.collectAsState()
    val agent = state.agent ?: return
    val scrollState = rememberScrollState()

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RuntimeInfoPanel(
                runtimeOutputTokensLabel = state.runtimeOutputTokensLabel,
                runtimeApiDurationLabel = state.runtimeApiDurationLabel,
                modifier = Modifier.fillMaxWidth(),
            )

            ConfigPanelFeature(
                viewModel = configViewModel,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            style = LocalScrollbarStyle.current.copy(
                unhoverColor = CyberpunkColors.TextPrimary.copy(alpha = 0.5f),
                hoverColor = CyberpunkColors.TextPrimary,
            ),
        )
    }
}

@Composable
private fun RuntimeInfoPanel(
    runtimeOutputTokensLabel: String,
    runtimeApiDurationLabel: String,
    modifier: Modifier = Modifier,
) {
    CyberpunkPanel(
        title = "RUNTIME",
        accentColor = CyberpunkColors.Cyan,
        modifier = modifier,
    ) {
        Text(
            text = "out tokens: $runtimeOutputTokensLabel",
            style = MaterialTheme.typography.bodySmall,
            color = CyberpunkColors.Cyan,
        )
        Text(
            text = "api duration: $runtimeApiDurationLabel",
            style = MaterialTheme.typography.bodySmall,
            color = CyberpunkColors.TextMuted,
        )
    }
}
