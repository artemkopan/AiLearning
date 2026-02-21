package io.artemkopan.ai.sharedui.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedcontract.ProjectInfo
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors

@Composable
fun ProjectSelector(
    projects: List<ProjectInfo>,
    onProjectSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // "+ NEW SESSION" button
        Text(
            text = if (expanded) "- CANCEL" else "+ NEW SESSION",
            style = MaterialTheme.typography.labelMedium,
            color = CyberpunkColors.NeonGreen,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .background(CyberpunkColors.NeonGreen.copy(alpha = 0.1f))
                .clickable { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )

        // Expandable project list
        AnimatedVisibility(visible = expanded) {
            CyberpunkPanel(
                title = "SELECT PROJECT",
                accentColor = CyberpunkColors.Yellow,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                if (projects.isEmpty()) {
                    Text(
                        text = "No projects found",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyberpunkColors.TextMuted,
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        projects.forEach { project ->
                            Text(
                                text = project.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = CyberpunkColors.NeonGreen,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(2.dp))
                                    .clickable {
                                        onProjectSelected(project.path)
                                        expanded = false
                                    }
                                    .background(CyberpunkColors.CardDark)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
