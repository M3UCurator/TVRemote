package com.qoder.atvremote.data

import android.content.Context
import com.qoder.atvremote.R
import com.qoder.atvremote.data.protocol.PairingClient
import com.qoder.atvremote.data.protocol.PairingRejectedException
import com.qoder.atvremote.data.protocol.PinMismatchException
import com.qoder.atvremote.data.protocol.ProtocolException
import com.qoder.atvremote.data.protocol.RemoteClient
import com.qoder.atvremote.data.protocol.RemoteClientEvent
import com.qoder.atvremote.data.security.CertificateStore
import com.qoder.atvremote.data.security.TlsClientFactory
import com.qoder.atvremote.model.ConnectionState
import com.qoder.atvremote.model.ConnectionStatus
import com.qoder.atvremote.model.KeyDirection
import com.qoder.atvremote.model.RemoteKey
import com.qoder.atvremote.model.TvDevice
import com.qoder.atvremote.model.VolumeState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

sealed interface PinResult {
    data object Paired : PinResult
    data class Invalid(val message: String) : PinResult
    data class Failed(val message: String) : PinResult
}

sealed interface RepositoryEvent {
    data class PinRequired(val device: TvDevice) : RepositoryEvent
    data object Paired : RepositoryEvent
    data class Notice(val message: String) : RepositoryEvent
}

@Singleton
class RemoteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val discovery: DeviceDiscovery,
    private val certificateStore: CertificateStore,
    private val tlsClientFactory: TlsClientFactory,
    private val settings: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connection = MutableStateFlow(ConnectionState())
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _volume = MutableStateFlow<VolumeState?>(null)
    val volume: StateFlow<VolumeState?> = _volume.asStateFlow()

    private val _currentApp = MutableStateFlow<String?>(null)
    val currentApp: StateFlow<String?> = _currentApp.asStateFlow()

    private val _events = MutableSharedFlow<RepositoryEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<RepositoryEvent> = _events.asSharedFlow()

    private var remoteClient: RemoteClient? = null
    private var pairingClient: PairingClient? = null
    private var pairingDevice: TvDevice? = null
    private var eventJob: Job? = null

    @Volatile
    private var manualClose = false

    val lastDevice: TvDevice?
        get() = settings.lastDevice

    fun discoverDevices(): Flow<List<TvDevice>> = discovery.discover()

    /**
     * Connects the command stream. When the TV rejects our certificate we immediately start
     * pairing, which makes the TV display its PIN.
     */
    suspend fun connect(device: TvDevice, allowPairing: Boolean = true): Boolean =
        withContext(Dispatchers.IO) {
            closeSessions()
            manualClose = false
            _connection.value = ConnectionState(ConnectionStatus.Connecting, device)
            _volume.value = null
            _currentApp.value = null

            val client = RemoteClient(device, tlsClientFactory)
            remoteClient = client
            eventJob = scope.launch {
                client.events.collect { event -> handleClientEvent(device, event) }
            }

            try {
                client.connect()
                _connection.value = ConnectionState(ConnectionStatus.Connected, device)
                settings.lastDevice = device
                true
            } catch (e: CancellationException) {
                client.close()
                eventJob?.cancel()
                remoteClient = null
                throw e
            } catch (e: SSLException) {
                client.close()
                eventJob?.cancel()
                remoteClient = null
                if (manualClose) {
                    false
                } else if (allowPairing) {
                    beginPairing(device)
                    false
                } else {
                    _connection.value = ConnectionState(
                        ConnectionStatus.Failed,
                        device,
                        context.getString(R.string.error_tls),
                    )
                    false
                }
            } catch (e: Exception) {
                client.close()
                eventJob?.cancel()
                remoteClient = null
                if (!manualClose) {
                    _connection.value = ConnectionState(ConnectionStatus.Failed, device, e.userMessage())
                }
                false
            }
        }

    suspend fun beginPairing(device: TvDevice): Boolean = withContext(Dispatchers.IO) {
        closeSessions()
        if (manualClose) return@withContext false

        _connection.value = ConnectionState(ConnectionStatus.Pairing, device)
        val client = PairingClient(device, tlsClientFactory, certificateStore)
        pairingClient = client
        pairingDevice = device

        try {
            client.start()
            _events.emit(RepositoryEvent.PinRequired(device))
            true
        } catch (e: CancellationException) {
            client.close()
            pairingClient = null
            pairingDevice = null
            throw e
        } catch (e: Exception) {
            client.close()
            if (!manualClose) {
                pairingClient = null
                pairingDevice = null
                _connection.value = ConnectionState(ConnectionStatus.Failed, device, e.userMessage())
            }
            false
        }
    }

    suspend fun submitPin(pin: String): PinResult = withContext(Dispatchers.IO) {
        val client = pairingClient
            ?: return@withContext PinResult.Failed(context.getString(R.string.error_protocol))
        val device = pairingDevice
            ?: return@withContext PinResult.Failed(context.getString(R.string.error_protocol))

        try {
            client.submitPin(pin)
        } catch (e: CancellationException) {
            throw e
        } catch (e: PinMismatchException) {
            return@withContext PinResult.Invalid(context.getString(R.string.pin_mismatch))
        } catch (e: Exception) {
            client.close()
            pairingClient = null
            pairingDevice = null
            if (!manualClose) {
                _connection.value = ConnectionState(ConnectionStatus.Failed, device, e.userMessage())
            }
            return@withContext PinResult.Failed(e.userMessage())
        }

        pairingClient = null
        pairingDevice = null
        _events.emit(RepositoryEvent.Paired)

        val connected = connect(device, allowPairing = false)
        if (connected) {
            PinResult.Paired
        } else {
            PinResult.Failed(_connection.value.detail ?: context.getString(R.string.pairing_failed))
        }
    }

    fun cancelPairing() {
        manualClose = true
        closeSessions()
        _connection.value = ConnectionState(ConnectionStatus.Disconnected)
    }

    fun disconnect() {
        manualClose = true
        closeSessions()
        _connection.value = ConnectionState(ConnectionStatus.Disconnected)
        _volume.value = null
        _currentApp.value = null
    }

    suspend fun sendKey(key: RemoteKey, direction: KeyDirection = KeyDirection.Short): Boolean {
        val client = remoteClient ?: return false
        return try {
            client.sendKey(key.keyCode, direction)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _events.emit(RepositoryEvent.Notice(context.getString(R.string.error_send_failed)))
            false
        }
    }

    suspend fun sendText(text: String): Boolean {
        val client = remoteClient ?: return false
        return try {
            client.sendText(text)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _events.emit(RepositoryEvent.Notice(context.getString(R.string.error_send_failed)))
            false
        }
    }

    private suspend fun handleClientEvent(device: TvDevice, event: RemoteClientEvent) {
        val current = _connection.value
        if (current.device?.id != device.id) return

        when (event) {
            is RemoteClientEvent.Started -> {
                if (current.status != ConnectionStatus.Connected) {
                    _connection.value = ConnectionState(ConnectionStatus.Connected, device)
                }
            }
            is RemoteClientEvent.Volume -> _volume.value = event.state
            is RemoteClientEvent.CurrentApp -> _currentApp.value = event.packageName
            is RemoteClientEvent.Error -> {
                _events.emit(RepositoryEvent.Notice(context.getString(R.string.error_from_tv)))
            }
            is RemoteClientEvent.Closed -> {
                if (!manualClose && current.status == ConnectionStatus.Connected) {
                    _volume.value = null
                    _currentApp.value = null
                    _connection.value = ConnectionState(
                        ConnectionStatus.Disconnected,
                        device,
                        context.getString(R.string.connection_lost),
                    )
                    _events.emit(RepositoryEvent.Notice(context.getString(R.string.connection_lost)))
                }
            }
        }
    }

    private fun closeSessions() {
        eventJob?.cancel()
        eventJob = null
        remoteClient?.close()
        remoteClient = null
        pairingClient?.close()
        pairingClient = null
        pairingDevice = null
    }

    private fun Throwable.userMessage(): String = when (this) {
        is SocketTimeoutException -> context.getString(R.string.error_timeout)
        is ConnectException, is NoRouteToHostException, is UnknownHostException ->
            context.getString(R.string.error_unreachable)
        is PairingRejectedException -> context.getString(R.string.error_pairing_rejected)
        is SSLException -> context.getString(R.string.error_tls)
        is ProtocolException -> message ?: context.getString(R.string.error_protocol)
        else -> context.getString(R.string.error_generic)
    }
}
