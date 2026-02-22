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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedui.state.ProjectSelectorViewModel
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors

@Composable
fun ProjectSelector(
    viewModel: ProjectSelectorViewModel,
    onNewSession: () -> Unit,
    onProjectSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val focusRequester = remember { FocusRequester() }

    Column(modifier = modifier) {
        // "+ NEW SESSION" button — creates chat immediately with default path
        Text(
            text = "+ NEW SESSION",
            style = MaterialTheme.typography.labelMedium,
            color = CyberpunkColors.NeonGreen,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .background(CyberpunkColors.NeonGreen.copy(alpha = 0.1f))
                .clickable { onNewSession() }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )

        // Toggle for project picker
        Text(
            text = if (state.expanded) "- CANCEL" else "> IN PROJECT...",
            style = MaterialTheme.typography.labelSmall,
            color = CyberpunkColors.Yellow,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .clip(RoundedCornerShape(2.dp))
                .clickable { viewModel.toggleExpanded() }
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )

        // Expandable project list
        AnimatedVisibility(visible = state.expanded) {
            CyberpunkPanel(
                title = "SELECT PROJECT",
                accentColor = CyberpunkColors.Yellow,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                BasicTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    singleLine = true,
                    cursorBrush = SolidColor(CyberpunkColors.Yellow),
                    textStyle = TextStyle(
                        color = CyberpunkColors.TextPrimary,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(CyberpunkColors.CardDark)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .focusRequester(focusRequester),
                    decorationBox = { innerTextField ->
                        if (state.searchQuery.isEmpty()) {
                            Text(
                                text = "Search...",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyberpunkColors.TextMuted,
                            )
                        }
                        innerTextField()
                    },
                )

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                if (state.filteredProjects.isEmpty()) {
                    Text(
                        text = if (state.searchQuery.isBlank()) "No projects found" else "No matches",
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
                        state.filteredProjects.forEach { project ->
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
                                        viewModel.collapse()
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
