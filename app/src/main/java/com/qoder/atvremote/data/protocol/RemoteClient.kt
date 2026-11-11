package com.qoder.atvremote.data.protocol

import com.qoder.atvremote.model.KeyDirection
import com.qoder.atvremote.model.TvDevice
import com.qoder.atvremote.model.VolumeState
import com.qoder.atvremote.data.security.TlsClientFactory
import com.qoder.atvremote.proto.remote.RemoteMessageProto.RemoteConfigure
import com.qoder.atvremote.proto.remote.RemoteMessageProto.RemoteDeviceInfo
import com.qoder.atvremote.proto.remote.RemoteMessageProto.RemoteDirection
import com.qoder.atvremote.proto.remote.RemoteMessageProto.RemoteEditInfo
import com.qoder.atvremote.proto.remote.RemoteMessageProto.RemoteImeBatchEdit
import com.qoder.atvremote.proto.remote.RemoteMessageProto.RemoteImeObject
import com.qoder.atvremote.proto.remote.RemoteMessageProto.RemoteKeyCode
import com.qoder.atvremote.proto.remote.RemoteMessageProto.RemoteKeyInject
import com.qoder.atvremote.proto.remote.RemoteMessageProto.RemoteMessage
import com.qoder.atvremote.proto.remote.RemoteMessageProto.RemotePingResponse
import com.qoder.atvremote.proto.remote.RemoteMessageProto.RemoteSetActive
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.net.ssl.SSLSocket

sealed interface RemoteClientEvent {
    data class Started(val isOn: Boolean) : RemoteClientEvent
    data class Volume(val state: VolumeState) : RemoteClientEvent
    data class CurrentApp(val packageName: String) : RemoteClientEvent
    data class Error(val message: String?) : RemoteClientEvent
    data class Closed(val reason: String?) : RemoteClientEvent
}

/**
 * Android TV Remote v2 command stream (port 6466).
 *
 * After the TLS handshake the TV drives negotiation: it sends RemoteConfigure, expects a reply
 * advertising our feature set, sends RemoteSetActive, then RemoteStart. It then pings every few
 * seconds; unanswered pings or an idle socket cause the TV to drop the session.
 */
class RemoteClient(
    private val device: TvDevice,
    private val tlsClientFactory: TlsClientFactory,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private val _events = MutableSharedFlow<RemoteClientEvent>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<RemoteClientEvent> = _events.asSharedFlow()

    @Volatile
    private var socket: SSLSocket? = null

    @Volatile
    private var closed = false

    @Volatile
    private var lastInboundAt = 0L

    @Volatile
    private var imeCounter = 0

    @Volatile
    private var fieldCounter = 0

    private var activeFeatures = DEFAULT_FEATURES

    suspend fun connect(timeoutMillis: Long = CONNECT_TIMEOUT_MS) = withContext(Dispatchers.IO) {
        val session = tlsClientFactory.connect(
            host = device.host,
            port = device.commandPort,
            readTimeoutMillis = 0,
        )
        socket = session
        lastInboundAt = System.currentTimeMillis()
        scope.launch { readLoop(session) }
        scope.launch { watchdog(session) }

        try {
            withTimeout(timeoutMillis) { ready.await() }
        } catch (t: TimeoutCancellationException) {
            shutdown()
            throw ProtocolException("The TV did not finish the remote handshake in time.", t)
        } catch (t: Throwable) {
            shutdown()
            throw t
        }
    }

    suspend fun sendKey(keyCode: Int, direction: KeyDirection = KeyDirection.Short) {
        if (activeFeatures and FEATURE_KEY == 0) {
            throw ProtocolException("This TV does not accept remote key commands.")
        }
        val code = RemoteKeyCode.forNumber(keyCode)
            ?: throw ProtocolException("Unsupported key code $keyCode.")
        val message = RemoteMessage.newBuilder()
            .setRemoteKeyInject(
                RemoteKeyInject.newBuilder()
                    .setKeyCode(code)
                    .setDirection(direction.toProtocol()),
            )
            .build()
        write(message)
    }

    /**
     * Protocol v2 injects text through the remote IME: a batch edit carries the current buffer
     * for the active text field. The counters are supplied by the TV in RemoteImeBatchEdit.
     */
    suspend fun sendText(text: String) {
        if (text.isEmpty()) return
        val cursor = text.length - 1
        val message = RemoteMessage.newBuilder()
            .setRemoteImeBatchEdit(
                RemoteImeBatchEdit.newBuilder()
                    .setImeCounter(imeCounter)
                    .setFieldCounter(fieldCounter)
                    .addEditInfo(
                        RemoteEditInfo.newBuilder()
                            .setInsert(1)
                            .setTextFieldStatus(
                                RemoteImeObject.newBuilder()
                                    .setStart(cursor)
                                    .setEnd(cursor)
                                    .setValue(text),
                            ),
                    ),
            )
            .build()
        write(message)
    }

    fun close() = shutdown()

    private suspend fun readLoop(session: SSLSocket) {
        var failure: Throwable? = null
        try {
            while (!closed) {
                val payload = session.inputStream.readDelimited() ?: break
                lastInboundAt = System.currentTimeMillis()
                handle(RemoteMessage.parseFrom(payload))
            }
            if (!closed) failure = ProtocolException("The TV closed the remote connection.")
        } catch (t: Throwable) {
            if (!closed) failure = t
        } finally {
            if (!ready.isCompleted) {
                ready.completeExceptionally(
                    failure ?: ProtocolException("The remote connection ended before it was ready."),
                )
            }
            if (!closed) {
                _events.tryEmit(RemoteClientEvent.Closed(failure?.message))
            }
            shutdown()
        }
    }

    private suspend fun handle(message: RemoteMessage) {
        when {
            message.hasRemoteConfigure() -> handleConfigure(message.remoteConfigure)
            message.hasRemoteSetActive() -> {
                write(
                    RemoteMessage.newBuilder()
                        .setRemoteSetActive(RemoteSetActive.newBuilder().setActive(activeFeatures))
                        .build(),
                )
            }
            message.hasRemotePingRequest() -> {
                write(
                    RemoteMessage.newBuilder()
                        .setRemotePingResponse(
                            RemotePingResponse.newBuilder().setVal1(message.remotePingRequest.val1),
                        )
                        .build(),
                )
            }
            message.hasRemoteStart() -> {
                _events.tryEmit(RemoteClientEvent.Started(message.remoteStart.started))
                ready.complete(Unit)
            }
            message.hasRemoteSetVolumeLevel() -> {
                val volume = message.remoteSetVolumeLevel
                _events.tryEmit(
                    RemoteClientEvent.Volume(
                        VolumeState(
                            level = volume.volumeLevel,
                            max = volume.volumeMax,
                            isMuted = volume.volumeMuted,
                        ),
                    ),
                )
            }
            message.hasRemoteImeBatchEdit() -> {
                imeCounter = message.remoteImeBatchEdit.imeCounter
                fieldCounter = message.remoteImeBatchEdit.fieldCounter
            }
            message.hasRemoteImeKeyInject() -> {
                val packageName = message.remoteImeKeyInject.appInfo.appPackage
                if (packageName.isNotBlank()) {
                    _events.tryEmit(RemoteClientEvent.CurrentApp(packageName))
                }
            }
            message.hasRemoteError() -> _events.tryEmit(RemoteClientEvent.Error(null))
        }
    }

    private suspend fun handleConfigure(configure: RemoteConfigure) {
        activeFeatures = activeFeatures and configure.code1
        val reply = RemoteMessage.newBuilder()
            .setRemoteConfigure(
                RemoteConfigure.newBuilder()
                    .setCode1(activeFeatures)
                    .setDeviceInfo(
                        RemoteDeviceInfo.newBuilder()
                            .setUnknown1(1)
                            .setUnknown2("1")
                            .setPackageName(CLIENT_PACKAGE)
                            .setAppVersion(CLIENT_VERSION),
                    ),
            )
            .build()
        write(reply)
    }

    private suspend fun write(message: RemoteMessage) = withContext(Dispatchers.IO) {
        val session = socket ?: throw ProtocolException("The remote connection is closed.")
        writeMutex.withLock {
            session.outputStream.writeDelimited(message)
        }
    }

    private suspend fun watchdog(session: SSLSocket) {
        while (!closed && scope.isActive) {
            delay(WATCHDOG_INTERVAL_MS)
            if (System.currentTimeMillis() - lastInboundAt > IDLE_TIMEOUT_MS) {
                if (!closed) {
                    _events.tryEmit(RemoteClientEvent.Closed("The TV stopped responding."))
                }
                runCatching { session.close() }
                shutdown()
                return
            }
        }
    }

    private fun shutdown() {
        if (closed) {
            socket = null
            return
        }
        closed = true
        val session = socket
        socket = null
        runCatching { session?.close() }
        scope.cancel()
    }

    private fun KeyDirection.toProtocol(): RemoteDirection = when (this) {
        KeyDirection.Short -> RemoteDirection.SHORT
        KeyDirection.StartLong -> RemoteDirection.START_LONG
        KeyDirection.EndLong -> RemoteDirection.END_LONG
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 12_000L
        const val WATCHDOG_INTERVAL_MS = 10_000L
        const val IDLE_TIMEOUT_MS = 45_000L
        const val CLIENT_PACKAGE = "atvremote"
        const val CLIENT_VERSION = "1.0.0"

        const val FEATURE_PING = 1 shl 0
        const val FEATURE_KEY = 1 shl 1
        const val FEATURE_IME = 1 shl 2
        const val FEATURE_POWER = 1 shl 5
        const val FEATURE_VOLUME = 1 shl 6
        const val FEATURE_APP_LINK = 1 shl 9
        const val DEFAULT_FEATURES =
            FEATURE_PING or FEATURE_KEY or FEATURE_IME or FEATURE_POWER or FEATURE_VOLUME or FEATURE_APP_LINK
    }
}
