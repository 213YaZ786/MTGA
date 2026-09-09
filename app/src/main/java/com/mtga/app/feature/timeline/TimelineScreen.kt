package com.mtga.app.feature.timeline

import androidx.compose.runtime.Composable
import com.mtga.app.ui.component.Placeholder

@Composable
fun TimelineScreen(onOpenDiagnostics: () -> Unit) {
    Placeholder(
        title = "Timeline",
        subtitle = "The merged timeline lands in step 4. For now, open Accounts, follow a handle, and tap it to read that feed."
    )
}
