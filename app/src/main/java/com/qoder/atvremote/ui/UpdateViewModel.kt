package com.qoder.atvremote.ui

import android.content.Context
import com.qoder.atvremote.BuildConfig
import com.qoder.atvremote.R
import com.qoder.atvremote.data.SettingsStore
import com.qoder.atvremote.data.update.ReleaseVersion
import com.qoder.atvremote.data.update.UpdateChecker
import com.qoder.atvremote.data.update.UpdateInstaller
import com.qoder.atvremote.data.update.UpdateRelease
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class UpdatePhase {
    Idle,
    Downloading,
    Ready,
    Failed,
}

data class UpdateUiState(
    val release: UpdateRelease? = null,
    val phase: UpdatePhase = UpdatePhase.Idle,
    val progress: Float? = null,
    val message: String? = null,
)

@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val checker: UpdateChecker,
    private val installer: UpdateInstaller,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private var updateJob: Job? = null

    init {
        checkForUpdate()
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            val release = runCatching { checker.fetchLatest() }.getOrNull() ?: return@launch
            if (release.version == settings.dismissedUpdateVersion) return@launch
            if (!ReleaseVersion.isNewer(release.version, BuildConfig.VERSION_NAME)) return@launch
            _uiState.update { it.copy(release = release) }
        }
    }

    fun dismiss() {
        val release = _uiState.value.release ?: return
        updateJob?.cancel()
        settings.dismissedUpdateVersion = release.version
        _uiState.update { it.copy(release = null, phase = UpdatePhase.Idle, progress = null, message = null) }
    }

    fun startUpdate() {
        val release = _uiState.value.release ?: return
        if (updateJob?.isActive == true) return

        if (!installer.canInstallPackages()) {
            installer.openInstallPermissionSettings()
            _uiState.update { it.copy(message = context.getString(R.string.update_allow_installs)) }
            return
        }

        _uiState.update { it.copy(phase = UpdatePhase.Downloading, progress = null, message = null) }
        updateJob = viewModelScope.launch {
            try {
                val apk = installer.download(release) { fraction ->
                    _uiState.update { state -> state.copy(progress = fraction) }
                }
                _uiState.update { it.copy(phase = UpdatePhase.Ready, progress = 1f) }
                installer.openInstaller(apk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(phase = UpdatePhase.Failed, progress = null, message = context.getString(R.string.update_failed))
                }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
