package com.qoder.atvremote.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardBackspace
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qoder.atvremote.R
import com.qoder.atvremote.model.ConnectionState
import com.qoder.atvremote.model.ConnectionStatus
import com.qoder.atvremote.model.RemoteKey
import com.qoder.atvremote.ui.components.CircleIconButton
import com.qoder.atvremote.ui.components.DPad
import com.qoder.atvremote.ui.components.StatusPill
import com.qoder.atvremote.ui.components.SystemPill
import com.qoder.atvremote.ui.components.TextEntryButton
import com.qoder.atvremote.ui.components.VolumeCard
import com.qoder.atvremote.ui.theme.DangerRed
import com.qoder.atvremote.ui.theme.WarningAmber

@Composable
fun RemoteScreen(
    state: RemoteUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSendKey: (RemoteKey) -> Unit,
    onVolumePress: (RemoteKey) -> Unit,
    onVolumeRelease: (RemoteKey) -> Unit,
    onMute: () -> Unit,
    onOpenKeyboard: () -> Unit,
) {
    val connection = state.connection
    val connected = connection.status == ConnectionStatus.Connected
    val showError = connection.status == ConnectionStatus.Failed ||
        (connection.status == ConnectionStatus.Disconnected && connection.detail != null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(14.dp))

        Header(
            state = state,
            onBack = onBack,
            onPower = { onSendKey(RemoteKey.Power) },
            powerEnabled = connected,
        )

        if (state.isDemoMode) {
            Spacer(Modifier.height(12.dp))
            DemoBanner()
        }

        if (showError) {
            Spacer(Modifier.height(12.dp))
            ErrorBanner(connection = connection, onRetry = onRetry)
        }

        Spacer(Modifier.height(18.dp))

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val dpadSize = (maxWidth - 32.dp).coerceAtMost(330.dp)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DPad(
                    enabled = connected,
                    onKey = onSendKey,
                    size = dpadSize,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SystemPill(
                icon = Icons.AutoMirrored.Rounded.KeyboardBackspace,
                label = stringResource(R.string.key_back),
                enabled = connected,
                onClick = { onSendKey(RemoteKey.Back) },
                modifier = Modifier.weight(1f),
            )
            SystemPill(
                icon = Icons.Rounded.Home,
                label = stringResource(R.string.key_home),
                enabled = connected,
                onClick = { onSendKey(RemoteKey.Home) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(14.dp))

        VolumeCard(
            volume = state.volume,
            enabled = connected,
            onPress = onVolumePress,
            onRelease = onVolumeRelease,
            onMute = onMute,
        )

        Spacer(Modifier.height(14.dp))

        TextEntryButton(onClick = onOpenKeyboard, enabled = connected)

        Spacer(Modifier.height(18.dp))

        Text(
            text = stringResource(R.string.protocol_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp),
        )
    }
}

@Composable
private fun Header(
    state: RemoteUiState,
    onBack: () -> Unit,
    onPower: () -> Unit,
    powerEnabled: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircleIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.back_to_devices),
            onClick = onBack,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            size = 44.dp,
        )

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = state.activeDevice?.name ?: stringResource(R.string.unknown_device),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(state.connection.status)
                state.activeDevice?.let { device ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = device.host,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        CircleIconButton(
            icon = Icons.Rounded.PowerSettingsNew,
            contentDescription = stringResource(R.string.key_power),
            onClick = onPower,
            containerColor = DangerRed.copy(alpha = 0.12f),
            contentColor = DangerRed,
            enabled = powerEnabled,
            size = 46.dp,
        )
    }
}

@Composable
private fun DemoBanner() {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = WarningAmber.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.28f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.demo_mode_banner),
            style = MaterialTheme.typography.labelMedium,
            color = WarningAmber,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun ErrorBanner(connection: ConnectionState, onRetry: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = connection.detail ?: stringResource(R.string.error_generic),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}
