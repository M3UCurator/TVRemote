package com.qoder.atvremote.ui.components

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qoder.atvremote.R
import com.qoder.atvremote.ui.UpdatePhase
import com.qoder.atvremote.ui.UpdateUiState
import kotlin.math.roundToInt

@Composable
fun UpdateBanner(
    state: UpdateUiState,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val release = state.release ?: return
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val downloading = state.phase == UpdatePhase.Downloading
    val sizeLabel = if (release.sizeBytes > 0) {
        Formatter.formatShortFileSize(context, release.sizeBytes)
    } else {
        null
    }

    val subtitle = when {
        downloading -> state.progress?.let {
            stringResource(R.string.update_downloading, (it * 100).roundToInt())
        } ?: stringResource(R.string.update_starting)

        state.phase == UpdatePhase.Failed -> stringResource(R.string.update_failed)
        sizeLabel != null -> stringResource(R.string.update_version, release.version, sizeLabel)
        else -> stringResource(R.string.update_version_only, release.version)
    }

    val shape = RoundedCornerShape(20.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, primary.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape),
    ) {
        Column(Modifier.padding(start = 14.dp, top = 12.dp, end = 6.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.update_available),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.phase == UpdatePhase.Failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (!downloading) {
                    TextButton(onClick = onUpdate) {
                        Text(
                            if (state.phase == UpdatePhase.Failed) {
                                stringResource(R.string.retry)
                            } else {
                                stringResource(R.string.update_action)
                            },
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.update_dismiss),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (downloading) {
                Spacer(Modifier.height(10.dp))
                val progress = state.progress
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp),
                        color = primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp),
                        color = primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}
