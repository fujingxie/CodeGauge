package com.codegauge.pairing

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EncryptedPairingStore(context: Context) : PairingStore {
    private val preferences = createPreferences(context.applicationContext)

    override suspend fun load(): PairingRecord? {
        return withContext(Dispatchers.IO) {
            val serverUrl = preferences.getString(KeyServerUrl, null) ?: return@withContext null
            val token = preferences.getString(KeyToken, null) ?: return@withContext null

            PairingRecord(
                serverUrl = serverUrl,
                serverName = preferences.getString(KeyServerName, null)
                    ?: "CodeGauge Companion",
                token = token,
                pairedAtMillis = preferences.getLong(KeyPairedAt, 0L),
            )
        }
    }

    override suspend fun save(record: PairingRecord) {
        withContext(Dispatchers.IO) {
            preferences.edit()
                .putString(KeyServerUrl, record.serverUrl)
                .putString(KeyServerName, record.serverName)
                .putString(KeyToken, record.token)
                .putLong(KeyPairedAt, record.pairedAtMillis)
                .apply()
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            preferences.edit().clear().apply()
        }
    }

    private fun createPreferences(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            StoreName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    companion object {
        private const val StoreName = "codegauge_pairing"
        private const val KeyServerUrl = "server_url"
        private const val KeyServerName = "server_name"
        private const val KeyToken = "token"
        private const val KeyPairedAt = "paired_at"
    }
}

