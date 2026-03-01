package io.artemkopan.ai.sharedui.ui.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors

@Composable
fun SubmitButton(
    isLoading: Boolean,
    queuedCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showBusyState = isLoading || queuedCount > 0
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(2.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = CyberpunkColors.Red,
            contentColor = Color.White,
            disabledContainerColor = CyberpunkColors.Red.copy(alpha = 0.3f),
            disabledContentColor = Color.White.copy(alpha = 0.5f),
        ),
    ) {
        if (showBusyState) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = CyberpunkColors.Yellow,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                when {
                    queuedCount > 0 -> "QUEUE ($queuedCount)"
                    else -> "PROCESSING..."
                },
                style = MaterialTheme.typography.labelLarge,
            )
        } else {
            Text(
                "EXECUTE",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
