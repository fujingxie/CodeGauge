package com.codegauge.pairing

class PairingRepository(
    private val api: PairingApi,
    private val store: PairingStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun loadPairing(): PairingRecord? {
        return store.load()
    }

    suspend fun clearPairing() {
        store.clear()
    }

    suspend fun pair(
        endpoint: CompanionEndpoint,
        pairCode: String,
        deviceName: String,
    ): PairingRecord {
        if (!PairCodePattern.matches(pairCode)) {
            throw PairingException("配对码必须是 6 位数字")
        }

        val response = api.pair(
            endpoint = endpoint,
            request = PairRequest(
                pairCode = pairCode,
                deviceName = deviceName.ifBlank { "Android" },
            ),
        )

        val record = PairingRecord(
            serverUrl = endpoint.baseUrl,
            serverName = response.serverName,
            token = response.token,
            pairedAtMillis = clock(),
        )
        store.save(record)
        return record
    }

    companion object {
        private val PairCodePattern = Regex("^\\d{6}$")
    }
}
