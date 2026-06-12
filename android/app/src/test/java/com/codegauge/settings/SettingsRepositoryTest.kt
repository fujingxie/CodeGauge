package com.codegauge.settings

import com.codegauge.pairing.PairingRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SettingsRepositoryTest {
    @Test
    fun loadsSettingsSnapshot() = runBlocking {
        val api = FakeSettingsApi()
        val repository = SettingsRepository(api)
        val pairing = testPairing()

        val snapshot = repository.load(pairing)

        assertEquals(api.settings, snapshot.settings)
        assertEquals(api.devices, snapshot.devices)
        assertEquals(api.diagnostics, snapshot.diagnostics)
        assertEquals(pairing, api.lastSettingsPairing)
        assertEquals(pairing, api.lastDevicesPairing)
        assertEquals(pairing, api.lastDiagnosticsPairing)
    }

    @Test
    fun savesSettingsThroughApi() = runBlocking {
        val api = FakeSettingsApi()
        val repository = SettingsRepository(api)
        val pairing = testPairing()
        val settings = AppSettings(
            notificationsEnabled = false,
            warningThreshold = 70,
            criticalThreshold = 92,
            quotaResetNotifications = true,
            taskDoneNotifications = false,
            collectIntervalSeconds = 120,
        )

        val updated = repository.save(pairing, settings)

        assertEquals(settings, updated)
        assertEquals(pairing, api.lastUpdatePairing)
        assertEquals(SettingsUpdate.from(settings), api.lastUpdate)
    }
}

private class FakeSettingsApi : SettingsApi {
    val settings = AppSettings(
        notificationsEnabled = true,
        warningThreshold = 80,
        criticalThreshold = 95,
        quotaResetNotifications = true,
        taskDoneNotifications = true,
        collectIntervalSeconds = 60,
    )
    val devices = listOf(
        PairedDevice(
            deviceId = "phone-1",
            name = "Pixel",
            pairedAt = Instant.parse("2026-06-12T02:00:00Z"),
            lastSeenAt = Instant.parse("2026-06-12T03:00:00Z"),
        ),
    )
    val diagnostics = CompanionDiagnostics(
        ok = true,
        serverName = "CodeGauge Companion",
        version = "dev",
        serverTime = Instant.parse("2026-06-12T03:10:08Z"),
        providerCount = 2,
        availableProviderCount = 2,
        runningSessionCount = 1,
        waitingSessionCount = 0,
        pairedDeviceCount = 1,
        latestEventAt = null,
    )
    var lastSettingsPairing: PairingRecord? = null
    var lastDevicesPairing: PairingRecord? = null
    var lastDiagnosticsPairing: PairingRecord? = null
    var lastUpdatePairing: PairingRecord? = null
    var lastUpdate: SettingsUpdate? = null

    override suspend fun settings(pairing: PairingRecord): AppSettings {
        lastSettingsPairing = pairing
        return settings
    }

    override suspend fun updateSettings(
        pairing: PairingRecord,
        update: SettingsUpdate,
    ): AppSettings {
        lastUpdatePairing = pairing
        lastUpdate = update
        return AppSettings(
            notificationsEnabled = update.notificationsEnabled ?: settings.notificationsEnabled,
            warningThreshold = update.warningThreshold ?: settings.warningThreshold,
            criticalThreshold = update.criticalThreshold ?: settings.criticalThreshold,
            quotaResetNotifications = update.quotaResetNotifications ?: settings.quotaResetNotifications,
            taskDoneNotifications = update.taskDoneNotifications ?: settings.taskDoneNotifications,
            collectIntervalSeconds = update.collectIntervalSeconds ?: settings.collectIntervalSeconds,
        )
    }

    override suspend fun devices(pairing: PairingRecord): List<PairedDevice> {
        lastDevicesPairing = pairing
        return devices
    }

    override suspend fun diagnostics(pairing: PairingRecord): CompanionDiagnostics {
        lastDiagnosticsPairing = pairing
        return diagnostics
    }
}

private fun testPairing(): PairingRecord {
    return PairingRecord(
        serverUrl = "http://127.0.0.1:18774",
        serverName = "CodeGauge Companion",
        token = "token-test",
        pairedAtMillis = 1_000,
    )
}
