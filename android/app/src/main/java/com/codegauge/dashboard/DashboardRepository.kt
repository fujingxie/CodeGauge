package com.codegauge.dashboard

import com.codegauge.pairing.PairingRecord

class DashboardRepository(
    private val api: DashboardApi,
) {
    suspend fun loadStatus(pairing: PairingRecord): DashboardSnapshot {
        return api.status(pairing)
    }
}

