package com.qoder.atvremote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qoder.atvremote.model.RemoteKey
import com.qoder.atvremote.ui.components.PairingPinDialog
import com.qoder.atvremote.ui.components.TextInputSheet
import com.qoder.atvremote.ui.theme.Ink
import com.qoder.atvremote.ui.theme.InkRaised

@Composable
fun AtvRemoteRoot(viewModel: RemoteViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val updateViewModel: UpdateViewModel = hiltViewModel()
    val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    LaunchedEffect(updateState.message) {
        val message = updateState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        updateViewModel.consumeMessage()
    }

    BackHandler(enabled = state.activeDevice != null) {
        when {
            state.pinDialog != null -> viewModel.cancelPairing()
            state.isTextSheetOpen -> viewModel.closeTextSheet()
            else -> viewModel.disconnect()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(InkRaised, Ink))),
    ) {
        Crossfade(
            targetState = state.activeDevice != null,
            label = "atv-remote-screen",
        ) { showRemote ->
            if (showRemote) {
                RemoteScreen(
                    state = state,
                    onBack = viewModel::disconnect,
                    onRetry = viewModel::retryConnection,
                    onSendKey = { key -> viewModel.sendKey(key) },
                    onVolumePress = viewModel::onVolumePress,
                    onVolumeRelease = viewModel::onVolumeRelease,
                    onMute = { viewModel.sendKey(RemoteKey.Mute) },
                    onOpenKeyboard = viewModel::openTextSheet,
                )
            } else {
                DiscoveryScreen(
                    state = state,
                    update = updateState,
                    onRefresh = viewModel::refreshDevices,
                    onSelect = viewModel::selectDevice,
                    onReconnect = {
                        state.lastDevice?.let { device ->
                            viewModel.selectDevice(device)
                        }
                    },
                    onUpdate = updateViewModel::startUpdate,
                    onDismissUpdate = updateViewModel::dismiss,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            snackbar = { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(14.dp),
                )
            },
        )
    }

    state.pinDialog?.let { pinState ->
        PairingPinDialog(
            state = pinState,
            onConfirm = viewModel::submitPin,
            onDismiss = viewModel::cancelPairing,
        )
    }

    if (state.isTextSheetOpen) {
        TextInputSheet(
            draft = state.textDraft,
            connection = state.connection.status,
            onTextChanged = viewModel::onTextChanged,
            onClear = viewModel::clearText,
            onDismiss = viewModel::closeTextSheet,
        )
    }
}
