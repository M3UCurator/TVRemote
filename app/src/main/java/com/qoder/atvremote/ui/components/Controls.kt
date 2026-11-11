package com.qoder.atvremote.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qoder.atvremote.R
import com.qoder.atvremote.model.ConnectionStatus
import com.qoder.atvremote.model.RemoteKey
import com.qoder.atvremote.model.VolumeState
import com.qoder.atvremote.ui.theme.DangerRed
import com.qoder.atvremote.ui.theme.SuccessGreen
import com.qoder.atvremote.ui.theme.TextSecondary
import com.qoder.atvremote.ui.theme.WarningAmber

@Composable
fun StatusPill(status: ConnectionStatus, modifier: Modifier = Modifier) {
    val labelRes: Int
    val color: Color
    when (status) {
        ConnectionStatus.Connected -> {
            labelRes = R.string.status_connected
            color = SuccessGreen
        }
        ConnectionStatus.Connecting -> {
            labelRes = R.string.status_connecting
            color = WarningAmber
        }
        ConnectionStatus.Pairing -> {
            labelRes = R.string.status_pairing
            color = WarningAmber
        }
        ConnectionStatus.Failed -> {
            labelRes = R.string.status_failed
            color = DangerRed
        }
        ConnectionStatus.Disconnected -> {
            labelRes = R.string.status_disconnected
            color = TextSecondary
        }
    }

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(
            color = color,
            pulse = status == ConnectionStatus.Connecting || status == ConnectionStatus.Pairing,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusDot(color: Color, pulse: Boolean) {
    val transition = rememberInfiniteTransition(label = "status-dot")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "status-dot-alpha",
    )
    Box(
        Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = if (pulse) pulseAlpha else 1f)),
    )
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    size: Dp = 46.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor.copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

@Composable
fun DPad(
    enabled: Boolean,
    onKey: (RemoteKey) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 300.dp,
) {
    val buttonSize = size * 0.28f
    val orbit = (size - buttonSize) / 2
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = this.size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primary.copy(alpha = 0.10f), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
            )
            drawCircle(
                color = outline.copy(alpha = 0.38f),
                radius = radius - 1.dp.toPx(),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
            )
            drawCircle(
                color = outline.copy(alpha = 0.16f),
                radius = radius * 0.82f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
            )
        }

        DpadKey(
            icon = Icons.Rounded.KeyboardArrowUp,
            description = stringResource(R.string.key_up),
            enabled = enabled,
            onClick = { onKey(RemoteKey.Up) },
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = -orbit),
            size = buttonSize,
        )
        DpadKey(
            icon = Icons.Rounded.KeyboardArrowDown,
            description = stringResource(R.string.key_down),
            enabled = enabled,
            onClick = { onKey(RemoteKey.Down) },
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = orbit),
            size = buttonSize,
        )
        DpadKey(
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
            description = stringResource(R.string.key_left),
            enabled = enabled,
            onClick = { onKey(RemoteKey.Left) },
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = -orbit),
            size = buttonSize,
        )
        DpadKey(
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            description = stringResource(R.string.key_right),
            enabled = enabled,
            onClick = { onKey(RemoteKey.Right) },
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = orbit),
            size = buttonSize,
        )

        Box(
            modifier = Modifier
                .size(buttonSize * 1.34f)
                .border(1.dp, primary.copy(alpha = 0.35f), CircleShape)
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            OkButton(
                enabled = enabled,
                onClick = { onKey(RemoteKey.Center) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun DpadKey(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    size: Dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "dpad-press")
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

@Composable
private fun OkButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "ok-press")
    val haptics = LocalHapticFeedback.current
    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (enabled) primary else primary.copy(alpha = 0.35f))
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.key_ok),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
fun SystemPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .height(54.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f),
            maxLines = 1,
        )
    }
}

@Composable
fun VolumeCard(
    volume: VolumeState?,
    enabled: Boolean,
    onPress: (RemoteKey) -> Unit,
    onRelease: (RemoteKey) -> Unit,
    onMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    val muted = volume?.isMuted == true
    val accent = if (muted) WarningAmber else MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HoldKeyButton(
            icon = Icons.AutoMirrored.Rounded.VolumeDown,
            description = stringResource(R.string.key_volume_down),
            enabled = enabled,
            onPress = { onPress(RemoteKey.VolumeDown) },
            onRelease = { onRelease(RemoteKey.VolumeDown) },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (volume == null) {
                        stringResource(R.string.volume)
                    } else {
                        stringResource(R.string.volume_level, volume.level)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                if (muted) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeOff,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.muted),
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningAmber,
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
            LinearProgressIndicator(
                progress = { volume?.fraction ?: 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = accent,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
            )
        }

        HoldKeyButton(
            icon = Icons.AutoMirrored.Rounded.VolumeUp,
            description = stringResource(R.string.key_volume_up),
            enabled = enabled,
            onPress = { onPress(RemoteKey.VolumeUp) },
            onRelease = { onRelease(RemoteKey.VolumeUp) },
        )

        Spacer(Modifier.width(6.dp))

        CircleIconButton(
            icon = Icons.AutoMirrored.Rounded.VolumeOff,
            contentDescription = stringResource(R.string.key_mute),
            onClick = onMute,
            containerColor = if (muted) WarningAmber.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
            contentColor = if (muted) WarningAmber else MaterialTheme.colorScheme.onSurface,
            enabled = enabled,
            size = 44.dp,
        )
    }
}

@Composable
private fun HoldKeyButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPress()
                        tryAwaitRelease()
                        onRelease()
                    },
                )
            }
            .semantics {
                contentDescription = description
                role = Role.Button
                if (enabled) {
                    onClick(label = description) {
                        onPress()
                        onRelease()
                        true
                    }
                } else {
                    disabled()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun TextEntryButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    val primary = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(shape)
            .background(primary.copy(alpha = 0.10f))
            .border(1.dp, primary.copy(alpha = 0.32f), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Keyboard,
            contentDescription = null,
            tint = primary.copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.keyboard),
                style = MaterialTheme.typography.labelLarge,
                color = primary.copy(alpha = if (enabled) 1f else 0.4f),
            )
            Text(
                text = stringResource(R.string.keyboard_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = primary.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
    }
}
