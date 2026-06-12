package com.codegauge.settings

import com.codegauge.pairing.PairingRecord

class SettingsRepository(
    private val api: SettingsApi,
) {
    suspend fun load(pairing: PairingRecord): SettingsSnapshot {
        return SettingsSnapshot(
            settings = api.settings(pairing),
            devices = api.devices(pairing),
            diagnostics = api.diagnostics(pairing),
        )
    }

    suspend fun save(
        pairing: PairingRecord,
        settings: AppSettings,
    ): AppSettings {
        return api.updateSettings(pairing, SettingsUpdate.from(settings))
    }
}
