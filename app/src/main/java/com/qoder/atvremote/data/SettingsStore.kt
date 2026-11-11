package com.qoder.atvremote.data

import android.content.Context
import com.qoder.atvremote.model.TvDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var lastDevice: TvDevice?
        get() {
            val host = preferences.getString(KEY_HOST, null) ?: return null
            return TvDevice(
                name = preferences.getString(KEY_NAME, null) ?: host,
                host = host,
                pairingPort = preferences.getInt(KEY_PAIR_PORT, TvDevice.DEFAULT_PAIRING_PORT),
                commandPort = preferences.getInt(KEY_COMMAND_PORT, TvDevice.DEFAULT_COMMAND_PORT),
            )
        }
        set(value) {
            preferences.edit().apply {
                if (value == null) {
                    remove(KEY_HOST)
                    remove(KEY_NAME)
                    remove(KEY_PAIR_PORT)
                    remove(KEY_COMMAND_PORT)
                } else {
                    putString(KEY_NAME, value.name)
                    putString(KEY_HOST, value.host)
                    putInt(KEY_PAIR_PORT, value.pairingPort)
                    putInt(KEY_COMMAND_PORT, value.commandPort)
                }
            }.apply()
        }

    var dismissedUpdateVersion: String?
        get() = preferences.getString(KEY_DISMISSED_UPDATE, null)
        set(value) {
            preferences.edit().apply {
                if (value == null) remove(KEY_DISMISSED_UPDATE) else putString(KEY_DISMISSED_UPDATE, value)
            }.apply()
        }

    private companion object {
        const val PREFS_NAME = "atv_remote_settings"
        const val KEY_NAME = "device_name"
        const val KEY_HOST = "device_host"
        const val KEY_PAIR_PORT = "pair_port"
        const val KEY_COMMAND_PORT = "command_port"
        const val KEY_DISMISSED_UPDATE = "dismissed_update_version"
    }
}
