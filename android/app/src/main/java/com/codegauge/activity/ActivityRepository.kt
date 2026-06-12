package com.codegauge.activity

import com.codegauge.pairing.PairingRecord

class ActivityRepository(
    private val api: ActivityApi,
) {
    suspend fun loadEvents(pairing: PairingRecord): List<ActivityEvent> {
        return api.events(pairing)
    }
}

