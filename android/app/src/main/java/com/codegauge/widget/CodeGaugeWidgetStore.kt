package com.codegauge.widget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

class CodeGaugeWidgetStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(StoreName, Context.MODE_PRIVATE)

    fun load(): CodeGaugeWidgetState {
        val raw = preferences.getString(KeyState, null) ?: return WidgetFormatter.unpaired()
        return runCatching {
            decode(JSONObject(raw))
        }.getOrElse {
            WidgetFormatter.error("Widget cache invalid")
        }
    }

    fun save(state: CodeGaugeWidgetState) {
        preferences.edit()
            .putString(KeyState, encode(state).toString())
            .apply()
    }

    private fun encode(state: CodeGaugeWidgetState): JSONObject {
        val providers = JSONArray()
        state.providers.forEach { provider ->
            providers.put(
                JSONObject()
                    .put("id", provider.id)
                    .put("name", provider.name)
                    .put("percent_left", provider.percentLeft)
                    .put("percent_text", provider.percentText)
                    .put("window_label", provider.windowLabel)
                    .put("usage_text", provider.usageText)
                    .put("reset_text", provider.resetText)
                    .put("five_hour_text", provider.fiveHourText)
                    .put("weekly_text", provider.weeklyText),
            )
        }

        return JSONObject()
            .put("status_text", state.statusText)
            .put("message", state.message)
            .put("updated_at", state.updatedAt?.toString())
            .put("providers", providers)
    }

    private fun decode(json: JSONObject): CodeGaugeWidgetState {
        val providersJson = json.optJSONArray("providers") ?: JSONArray()
        val providers = buildList {
            for (index in 0 until providersJson.length()) {
                val item = providersJson.getJSONObject(index)
                add(
                    WidgetProviderLine(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        percentLeft = if (item.has("percent_left") && !item.isNull("percent_left")) {
                            item.optInt("percent_left").coerceIn(0, 100)
                        } else {
                            null
                        },
                        percentText = item.optString("percent_text").takeIf { it.isNotBlank() }
                            ?: item.optString("five_hour_text").percentFallback(),
                        windowLabel = item.optString("window_label").takeIf { it.isNotBlank() }
                            ?: item.optString("five_hour_text").substringBefore(' ', missingDelimiterValue = ""),
                        usageText = item.optString("usage_text").takeIf { it.isNotBlank() }
                            ?: item.optString("five_hour_text").substringAfter("· ", missingDelimiterValue = ""),
                        resetText = item.optString("reset_text"),
                        fiveHourText = item.optString("five_hour_text"),
                        weeklyText = item.optString("weekly_text"),
                    ),
                )
            }
        }

        val updatedAt = json.optString("updated_at")
            .takeIf { it.isNotBlank() && it != "null" }
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }

        return CodeGaugeWidgetState(
            statusText = json.optString("status_text"),
            message = json.optString("message").takeIf { it.isNotBlank() && it != "null" },
            providers = providers,
            updatedAt = updatedAt,
        )
    }

    companion object {
        private const val StoreName = "codegauge_widget"
        private const val KeyState = "state"
    }
}

private fun String.percentFallback(): String {
    return split(' ')
        .firstOrNull { it.endsWith('%') }
        ?.removeSuffix("%")
        ?: "-"
}
