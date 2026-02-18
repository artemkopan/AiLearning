package io.artemkopan.ai.sharedui.ui.component

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedui.state.GenerationResult
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors

@Composable
fun OutputPanel(
    response: GenerationResult,
    modifier: Modifier = Modifier,
) {
    CyberpunkPanel(
        title = "OUTPUT",
        modifier = modifier,
        accentColor = CyberpunkColors.NeonGreen,
    ) {
        val scrollState = rememberScrollState()
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = response.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CyberpunkColors.TextPrimary,
                )

                HorizontalDivider(
                    thickness = 1.dp,
                    color = CyberpunkColors.BorderDark,
                )

                Text(
                    text = "PROVIDER: ${response.provider.uppercase()}  //  MODEL: ${response.model.uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberpunkColors.NeonGreen,
                )

                response.usage?.let { usage ->
                    Text(
                        text = "TOKENS  IN: ${usage.inputTokens}  OUT: ${usage.outputTokens}  TOTAL: ${usage.totalTokens}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyberpunkColors.Cyan,
                    )
                }
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
}
