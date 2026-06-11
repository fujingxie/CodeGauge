package com.codegauge.pairing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingRepositoryTest {
    @Test
    fun pairsAndPersistsToken() = runBlocking {
        val store = InMemoryPairingStore()
        val api = FakePairingApi(
            response = PairResponse(
                token = "token-test",
                serverName = "CodeGauge Companion",
            ),
        )
        val repository = PairingRepository(api, store, clock = { 1_000L })

        val record = repository.pair(
            endpoint = CompanionEndpoint("Dev Mac", "192.168.1.20", 18768),
            pairCode = "123456",
            deviceName = "Pixel",
        )

        assertEquals("http://192.168.1.20:18768", record.serverUrl)
        assertEquals("CodeGauge Companion", record.serverName)
        assertEquals("token-test", record.token)
        assertEquals(record, store.load())
        assertEquals("123456", api.lastRequest?.pairCode)
        assertEquals("Pixel", api.lastRequest?.deviceName)
    }

    @Test
    fun rejectsInvalidPairCodeBeforeCallingApi() = runBlocking {
        val store = InMemoryPairingStore()
        val api = FakePairingApi(PairResponse("token-test", "CodeGauge Companion"))
        val repository = PairingRepository(api, store)

        val result = runCatching {
            repository.pair(
                endpoint = CompanionEndpoint("Dev Mac", "192.168.1.20", 18768),
                pairCode = "12345",
                deviceName = "Pixel",
            )
        }

        assertTrue(result.exceptionOrNull() is PairingException)
        assertEquals(null, api.lastRequest)
        assertEquals(null, store.load())
    }
}

private class FakePairingApi(
    private val response: PairResponse,
) : PairingApi {
    var lastRequest: PairRequest? = null

    override suspend fun pair(endpoint: CompanionEndpoint, request: PairRequest): PairResponse {
        lastRequest = request
        return response
    }
}
