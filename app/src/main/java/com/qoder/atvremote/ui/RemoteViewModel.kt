package com.qoder.atvremote.ui

import android.content.Context
import com.qoder.atvremote.R
import com.qoder.atvremote.data.PinResult
import com.qoder.atvremote.data.RemoteRepository
import com.qoder.atvremote.data.RepositoryEvent
import com.qoder.atvremote.data.protocol.PairingCrypto
import com.qoder.atvremote.model.ConnectionState
import com.qoder.atvremote.model.ConnectionStatus
import com.qoder.atvremote.model.KeyDirection
import com.qoder.atvremote.model.RemoteKey
import com.qoder.atvremote.model.TvDevice
import com.qoder.atvremote.model.VolumeState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PinDialogState(
    val deviceName: String,
    val error: String? = null,
    val isSubmitting: Boolean = false,
)

data class RemoteUiState(
    val devices: List<TvDevice> = emptyList(),
    val isScanning: Boolean = false,
    val lastDevice: TvDevice? = null,
    val activeDevice: TvDevice? = null,
    val connection: ConnectionState = ConnectionState(),
    val volume: VolumeState? = null,
    val pinDialog: PinDialogState? = null,
    val isTextSheetOpen: Boolean = false,
    val textDraft: String = "",
    val isDemoMode: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class RemoteViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: RemoteRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RemoteUiState(lastDevice = repository.lastDevice))
    val uiState: StateFlow<RemoteUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private var connectJob: Job? = null
    private var textJob: Job? = null

    init {
        viewModelScope.launch {
            repository.connection.collect { connection ->
                _uiState.update { state ->
                    val lastDevice = if (connection.status == ConnectionStatus.Connected) {
                        connection.device ?: state.lastDevice
                    } else {
                        state.lastDevice
                    }
                    state.copy(connection = connection, lastDevice = lastDevice)
                }
            }
        }

        viewModelScope.launch {
            repository.volume.collect { volume ->
                _uiState.update { it.copy(volume = volume) }
            }
        }

        viewModelScope.launch {
            repository.events.collect { event ->
                when (event) {
                    is RepositoryEvent.PinRequired -> _uiState.update {
                        it.copy(pinDialog = PinDialogState(event.device.name))
                    }
                    is RepositoryEvent.Paired -> _uiState.update {
                        it.copy(pinDialog = null, message = context.getString(R.string.paired_success))
                    }
                    is RepositoryEvent.Notice -> _uiState.update { it.copy(message = event.message) }
                }
            }
        }

        refreshDevices()
    }

    fun refreshDevices() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, devices = emptyList()) }
            try {
                repository.discoverDevices().collect { devices ->
                    _uiState.update { it.copy(devices = devices) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(message = context.getString(R.string.error_discovery)) }
            } finally {
                _uiState.update { it.copy(isScanning = false) }
            }
        }
    }

    fun selectDevice(device: TvDevice) {
        if (_uiState.value.isDemoMode) return
        scanJob?.cancel()
        connectJob?.cancel()
        _uiState.update {
            it.copy(
                activeDevice = device,
                isScanning = false,
                pinDialog = null,
                textDraft = "",
                connection = ConnectionState(ConnectionStatus.Connecting, device),
            )
        }
        connectJob = viewModelScope.launch { repository.connect(device) }
    }

    fun retryConnection() {
        val device = _uiState.value.activeDevice ?: return
        if (_uiState.value.isDemoMode) return
        connectJob?.cancel()
        connectJob = viewModelScope.launch { repository.connect(device) }
    }

    fun disconnect() {
        repository.disconnect()
        _uiState.update {
            it.copy(
                activeDevice = null,
                pinDialog = null,
                isTextSheetOpen = false,
                textDraft = "",
                volume = null,
                isDemoMode = false,
            )
        }
        refreshDevices()
    }

    fun submitPin(pin: String) {
        val dialog = _uiState.value.pinDialog ?: return
        if (dialog.isSubmitting) return
        if (!PairingCrypto.isValidPin(pin)) {
            _uiState.update {
                it.copy(pinDialog = dialog.copy(error = context.getString(R.string.pin_invalid)))
            }
            return
        }

        _uiState.update {
            it.copy(pinDialog = dialog.copy(isSubmitting = true, error = null))
        }

        viewModelScope.launch {
            when (val result = repository.submitPin(pin)) {
                is PinResult.Paired -> _uiState.update { it.copy(pinDialog = null) }
                is PinResult.Invalid -> _uiState.update { state ->
                    state.copy(
                        pinDialog = state.pinDialog?.copy(isSubmitting = false, error = result.message),
                    )
                }
                is PinResult.Failed -> _uiState.update {
                    it.copy(pinDialog = null, message = result.message)
                }
            }
        }
    }

    fun cancelPairing() {
        repository.cancelPairing()
        _uiState.update {
            it.copy(
                activeDevice = null,
                pinDialog = null,
                isTextSheetOpen = false,
                textDraft = "",
            )
        }
        refreshDevices()
    }

    fun sendKey(key: RemoteKey, direction: KeyDirection = KeyDirection.Short) {
        val state = _uiState.value
        if (state.isDemoMode) {
            updateDemoVolume(key)
            return
        }
        if (state.connection.status != ConnectionStatus.Connected) return

        viewModelScope.launch { repository.sendKey(key, direction) }
    }

    fun onVolumePress(key: RemoteKey) = sendKey(key, KeyDirection.StartLong)

    fun onVolumeRelease(key: RemoteKey) = sendKey(key, KeyDirection.EndLong)

    fun openTextSheet() {
        _uiState.update { it.copy(isTextSheetOpen = true) }
    }

    fun closeTextSheet() {
        textJob?.cancel()
        _uiState.update { it.copy(isTextSheetOpen = false) }
    }

    fun onTextChanged(text: String) {
        val cleaned = text.take(MAX_TEXT_LENGTH)
        _uiState.update { it.copy(textDraft = cleaned) }
        textJob?.cancel()
        if (cleaned.isEmpty() || _uiState.value.isDemoMode) return
        textJob = viewModelScope.launch {
            delay(TEXT_DEBOUNCE_MS)
            repository.sendText(cleaned)
        }
    }

    fun clearText() = onTextChanged("")

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /** Debug-only showcase used to verify the remote UI without a physical TV. */
    fun startDemo() {
        scanJob?.cancel()
        val device = TvDevice(name = "Living Room TV", host = "192.168.1.42")
        _uiState.update {
            it.copy(
                activeDevice = device,
                isDemoMode = true,
                isScanning = false,
                connection = ConnectionState(ConnectionStatus.Connected, device),
                volume = VolumeState(level = 12, max = 30, isMuted = false),
                message = context.getString(R.string.demo_mode_banner),
            )
        }
    }

    private fun updateDemoVolume(key: RemoteKey) {
        val volume = _uiState.value.volume ?: return
        val next = when (key) {
            RemoteKey.VolumeUp -> volume.copy(level = (volume.level + 1).coerceAtMost(volume.max), isMuted = false)
            RemoteKey.VolumeDown -> volume.copy(level = (volume.level - 1).coerceAtLeast(0), isMuted = false)
            RemoteKey.Mute -> volume.copy(isMuted = !volume.isMuted)
            else -> return
        }
        _uiState.update { it.copy(volume = next) }
    }

    private companion object {
        const val TEXT_DEBOUNCE_MS = 140L
        const val MAX_TEXT_LENGTH = 500
    }
}
