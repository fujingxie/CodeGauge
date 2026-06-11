package com.codegauge.pairing

data class PairingRecord(
    val serverUrl: String,
    val serverName: String,
    val token: String,
    val pairedAtMillis: Long,
)

interface PairingStore {
    suspend fun load(): PairingRecord?

    suspend fun save(record: PairingRecord)

    suspend fun clear()
}

class InMemoryPairingStore : PairingStore {
    private var record: PairingRecord? = null

    override suspend fun load(): PairingRecord? {
        return record
    }

    override suspend fun save(record: PairingRecord) {
        this.record = record
    }

    override suspend fun clear() {
        record = null
    }
}

