package io.artemkopan.ai.sharedui.ui.component

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedui.state.GenerationResult
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OutputPanel(
    response: GenerationResult,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    CyberpunkPanel(
        title = "OUTPUT",
        modifier = modifier,
        accentColor = CyberpunkColors.NeonGreen,
        headerAction = {
            val tint = if (copied) CyberpunkColors.NeonGreen else CyberpunkColors.TextSecondary
            Surface(
                onClick = {
                    copyToClipboard(response.text)
                    copied = true
                    scope.launch {
                        delay(1500)
                        copied = false
                    }
                },
                modifier = Modifier.size(20.dp),
                shape = RoundedCornerShape(2.dp),
                color = androidx.compose.ui.graphics.Color.Transparent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = CopyIcon,
                        contentDescription = "Copy",
                        tint = tint,
                    )
                }
            }
        },
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

private val CopyIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Copy",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 352.804f,
        viewportHeight = 352.804f,
    ).apply {
        path(fill = SolidColor(androidx.compose.ui.graphics.Color.Black)) {
            moveTo(318.54f, 57.282f)
            horizontalLineToRelative(-47.652f)
            verticalLineTo(15f)
            curveToRelative(0f, -8.284f, -6.716f, -15f, -15f, -15f)
            horizontalLineTo(34.264f)
            curveToRelative(-8.284f, 0f, -15f, 6.716f, -15f, 15f)
            verticalLineToRelative(265.522f)
            curveToRelative(0f, 8.284f, 6.716f, 15f, 15f, 15f)
            horizontalLineToRelative(47.651f)
            verticalLineToRelative(42.281f)
            curveToRelative(0f, 8.284f, 6.716f, 15f, 15f, 15f)
            horizontalLineTo(318.54f)
            curveToRelative(8.284f, 0f, 15f, -6.716f, 15f, -15f)
            verticalLineTo(72.282f)
            curveTo(333.54f, 63.998f, 326.824f, 57.282f, 318.54f, 57.282f)
            close()
            moveTo(49.264f, 265.522f)
            verticalLineTo(30f)
            horizontalLineToRelative(191.623f)
            verticalLineToRelative(27.282f)
            horizontalLineTo(96.916f)
            curveToRelative(-8.284f, 0f, -15f, 6.716f, -15f, 15f)
            verticalLineToRelative(193.24f)
            horizontalLineTo(49.264f)
            close()
            moveTo(303.54f, 322.804f)
            horizontalLineTo(111.916f)
            verticalLineTo(87.282f)
            horizontalLineTo(303.54f)
            verticalLineTo(322.804f)
            close()
        }
    }.build()
}
