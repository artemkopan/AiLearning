package io.artemkopan.ai.sharedui.feature.errordialog.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.artemkopan.ai.sharedui.feature.errordialog.viewmodel.ErrorDialogViewModel

@Composable
fun ErrorDialogFeature(
    viewModel: ErrorDialogViewModel,
) {
    val state by viewModel.state.collectAsState()
    state.error?.let { error ->
        ErrorDialog(
            error = error,
            onDismiss = viewModel::dismiss,
        )
    }
}
