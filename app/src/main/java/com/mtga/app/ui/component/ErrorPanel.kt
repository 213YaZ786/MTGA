package com.mtga.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.ShapeDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mtga.app.core.common.AppError
import com.mtga.app.core.common.ErrorAction
import com.mtga.app.core.common.present

/**
 * The visible half of the error taxonomy. Every failure gets a name, an
 * explanation and the one action that helps, instead of a shrug.
 */
@Composable
fun ErrorPanel(
    error: AppError,
    onRetry: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    onVerify: ((AppError.ChallengeRequired) -> Unit)? = null
) {
    val presentation = error.present()

    ZoneSurface(
        modifier = modifier.fillMaxWidth(),
        shape = ShapeDefaults.Large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        // Centred like EmptyZone (SMS ui/component/EmptyZone.kt:36-76).
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(presentation.headline, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                presentation.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            when (presentation.action) {
                ErrorAction.RETRY -> BoldButton(onClick = onRetry) { Text("Try again") }
                ErrorAction.OPEN_DIAGNOSTICS,
                ErrorAction.CHANGE_INSTANCE -> BoldButton(onClick = onOpenDiagnostics) {
                    Text("Check connection")
                }
                ErrorAction.OPEN_FALLBACK_VIEWER -> {
                    val check = error as? AppError.ChallengeRequired
                    if (check != null && onVerify != null) {
                        BoldButton(onClick = { onVerify(check) }) { Text("Do the check") }
                    } else {
                        BoldButton(onClick = onOpenDiagnostics) { Text("Check connection") }
                    }
                }
                ErrorAction.NONE -> Unit
            }
        }
    }
}
