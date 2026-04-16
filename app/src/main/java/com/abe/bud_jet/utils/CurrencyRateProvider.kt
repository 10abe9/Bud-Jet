package com.abe.bud_jet.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object CurrencyRateProvider {

    private val fallbackRates = mapOf(
        // Base against USD
        "USD_EUR" to 0.92,
        "USD_PLN" to 4.00,
        "USD_MXN" to 17.0,
        "USD_BRL" to 5.00,
        "USD_INR" to 83.0,
        "USD_RUB" to 90.0,
        "USD_KZT" to 470.0,
        "EUR_USD" to 1.09,
        "PLN_USD" to 0.25,
        "MXN_USD" to 0.0588,
        "BRL_USD" to 0.20,
        "INR_USD" to 0.0120,
        "EUR_RUB" to 98.0,
        "EUR_KZT" to 510.0,
        "RUB_USD" to 0.011,
        "RUB_EUR" to 0.010,
        "RUB_KZT" to 5.2,
        "KZT_USD" to 0.0021,
        "KZT_EUR" to 0.0020,
        "KZT_RUB" to 0.19
    )

    fun fallbackRate(from: String, to: String): Double {
        if (from.equals(to, ignoreCase = true)) return 1.0

        val fromU = from.uppercase()
        val toU = to.uppercase()

        // Prefer direct fallback if we have it.
        fallbackRates["${fromU}_${toU}"]?.let { return it }

        // Otherwise try via USD using known pairs:
        // 1 FROM = (FROM_USD) USD
        // 1 USD = (USD_TO) TO
        val fromToUsd = fallbackRates["${fromU}_USD"] ?: return 1.0
        val usdToTo = fallbackRates["USD_${toU}"] ?: return 1.0
        return fromToUsd * usdToTo
    }

    suspend fun fetchRateOrNull(from: String, to: String): Double? = withContext(Dispatchers.IO) {
        if (from.equals(to, ignoreCase = true)) return@withContext 1.0

        val url = URL("https://open.er-api.com/v6/latest/${from.uppercase()}")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3000
            readTimeout = 3000
        }

        return@withContext runCatching {
            connection.inputStream.bufferedReader().use { reader ->
                val payload = reader.readText()
                val root = JSONObject(payload)
                if (!root.optString("result").equals("success", ignoreCase = true)) return@use null
                val rates = root.optJSONObject("rates") ?: return@use null
                rates.optDouble(to.uppercase(), Double.NaN).takeIf { it.isFinite() && it > 0 }
            }
        }.getOrNull().also {
            connection.disconnect()
        }
    }
}
