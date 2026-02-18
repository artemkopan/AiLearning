package io.artemkopan.ai.sharedui.ui.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedui.state.ErrorPopupState
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors

@Composable
fun ErrorDialog(
    error: ErrorPopupState,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberpunkColors.Red,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    "DISMISS",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        title = {
            Text(
                text = error.title.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = CyberpunkColors.Red,
            )
        },
        text = {
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyMedium,
                color = CyberpunkColors.TextPrimary,
            )
        },
        containerColor = CyberpunkColors.SurfaceDark,
        shape = RoundedCornerShape(2.dp),
    )
}
