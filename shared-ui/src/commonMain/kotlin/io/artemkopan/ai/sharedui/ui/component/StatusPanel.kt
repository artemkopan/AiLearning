package io.artemkopan.ai.sharedui.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors

@Composable
fun StatusPanel(
    status: String,
    modifier: Modifier = Modifier,
    showSpinner: Boolean = true,
) {
    CyberpunkPanel(
        title = "STATUS",
        modifier = modifier,
        accentColor = CyberpunkColors.Cyan,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showSpinner) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = CyberpunkColors.Cyan,
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                status.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = CyberpunkColors.Cyan,
            )
        }
    }
}
