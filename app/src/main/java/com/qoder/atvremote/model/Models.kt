package com.qoder.atvremote.model

data class TvDevice(
    val name: String,
    val host: String,
    val pairingPort: Int = DEFAULT_PAIRING_PORT,
    val commandPort: Int = DEFAULT_COMMAND_PORT,
) {
    val id: String get() = "$host:$commandPort"

    companion object {
        const val DEFAULT_PAIRING_PORT = 6467
        const val DEFAULT_COMMAND_PORT = 6466
    }
}

enum class ConnectionStatus {
    Disconnected,
    Connecting,
    Pairing,
    Connected,
    Failed,
}

data class ConnectionState(
    val status: ConnectionStatus = ConnectionStatus.Disconnected,
    val device: TvDevice? = null,
    val detail: String? = null,
)

enum class KeyDirection {
    Short,
    StartLong,
    EndLong,
}

/** Android [android.view.KeyEvent] codes understood by the TV remote service. */
enum class RemoteKey(val keyCode: Int) {
    Up(19),
    Down(20),
    Left(21),
    Right(22),
    Center(23),
    Back(4),
    Home(3),
    VolumeUp(24),
    VolumeDown(25),
    Mute(164),
    Power(26),
}

data class VolumeState(
    val level: Int,
    val max: Int,
    val isMuted: Boolean,
) {
    val fraction: Float
        get() = if (max <= 0) 0f else (level.toFloat() / max).coerceIn(0f, 1f)
}
