package com.abe.bud_jet.api

import com.abe.bud_jet.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for the Bud-Jet backend. The address comes from `budjet.apiBaseUrl` in
 * gradle.properties; while it is empty every call returns [ApiResult.NotConfigured] and the app
 * keeps working offline. Endpoint contracts: docs/backend-api.md.
 */
object BudJetApi {

    sealed class ApiResult<out T> {
        data class Success<T>(val value: T) : ApiResult<T>()
        /** No server address in gradle.properties. */
        object NotConfigured : ApiResult<Nothing>()
        data class HttpError(val code: Int, val message: String) : ApiResult<Nothing>()
        data class NetworkError(val cause: Throwable) : ApiResult<Nothing>()
    }

    data class SubscriptionStatus(val active: Boolean, val expiresAt: Long?)

    data class AiInsight(val title: String, val text: String)

    /** Who is calling: sent as headers with every request. */
    data class Caller(
        /** Random per-install id (no personal data), for rate limiting on the server. */
        val installId: String,
        /** Google Play purchase token; the server verifies Premium with it. */
        val purchaseToken: String?,
        /** App language, so the AI answers in it ("en", "ru", ...). */
        val language: String
    )

    val baseUrl: String get() = BuildConfig.API_BASE_URL
    val isConfigured: Boolean get() = baseUrl.isNotBlank()

    /** GET /v1/health */
    suspend fun health(caller: Caller): ApiResult<Boolean> =
        request("GET", "/v1/health", caller, null) { it.optBoolean("ok", false) }

    /** POST /v1/subscription/verify */
    suspend fun verifySubscription(
        caller: Caller,
        packageName: String,
        productId: String,
        purchaseToken: String
    ): ApiResult<SubscriptionStatus> {
        val body = JSONObject()
            .put("packageName", packageName)
            .put("productId", productId)
            .put("purchaseToken", purchaseToken)
        return request("POST", "/v1/subscription/verify", caller, body) { json ->
            SubscriptionStatus(
                active = json.optBoolean("active", false),
                expiresAt = json.optLong("expiresAt", 0L).takeIf { it > 0 }
            )
        }
    }

    /** POST /v1/ai/insights: personal tips for the spending summary. */
    suspend fun aiInsights(caller: Caller, summary: BudgetSummary): ApiResult<List<AiInsight>> {
        val body = JSONObject().put("summary", summary.toJson())
        return request("POST", "/v1/ai/insights", caller, body, readTimeoutMs = AI_TIMEOUT_MS) { json ->
            val items = json.optJSONArray("insights") ?: JSONArray()
            (0 until items.length()).mapNotNull { i ->
                val item = items.optJSONObject(i) ?: return@mapNotNull null
                AiInsight(item.optString("title"), item.optString("text"))
            }
        }
    }

    /** POST /v1/ai/chat: a question about the budget, answered with the summary as context. */
    suspend fun aiChat(caller: Caller, summary: BudgetSummary, message: String): ApiResult<String> {
        val body = JSONObject()
            .put("summary", summary.toJson())
            .put("message", message)
        return request("POST", "/v1/ai/chat", caller, body, readTimeoutMs = AI_TIMEOUT_MS) { json ->
            json.optString("reply")
        }
    }

    private suspend fun <T> request(
        method: String,
        path: String,
        caller: Caller,
        body: JSONObject?,
        readTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
        parse: (JSONObject) -> T
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext ApiResult.NotConfigured
        val connection = try {
            URL(baseUrl + path).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return@withContext ApiResult.NetworkError(e)
        }
        try {
            connection.requestMethod = method
            connection.connectTimeout = DEFAULT_TIMEOUT_MS
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-App-Version", BuildConfig.VERSION_NAME)
            connection.setRequestProperty("X-Install-Id", caller.installId)
            connection.setRequestProperty("Accept-Language", caller.language)
            caller.purchaseToken?.let { connection.setRequestProperty("X-Purchase-Token", it) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) return@withContext ApiResult.HttpError(code, text.take(300))
            ApiResult.Success(parse(if (text.isBlank()) JSONObject() else JSONObject(text)))
        } catch (e: Exception) {
            ApiResult.NetworkError(e)
        } finally {
            connection.disconnect()
        }
    }

    private const val DEFAULT_TIMEOUT_MS = 10_000
    private const val AI_TIMEOUT_MS = 60_000
}

/** JSON sent to the /v1/ai endpoints; field names are part of the API contract (docs/backend-api.md). */
fun BudgetSummary.toJson(): JSONObject = JSONObject()
    .put("currency", currency)
    .put("months", JSONArray().apply {
        months.forEach { month ->
            put(JSONObject()
                .put("month", month.month)
                .put("income", month.income)
                .put("expense", month.expense)
                .put("expenseByCategory", JSONObject().apply {
                    month.expenseByCategory.forEach { (name, amount) -> put(name, amount) }
                }))
        }
    })
    .put("limits", JSONArray().apply {
        limits.forEach { limit ->
            put(JSONObject()
                .put("category", limit.category)
                .put("limit", limit.limit)
                .put("spentThisMonth", limit.spentThisMonth))
        }
    })
    .put("savingGoal", savingGoal?.let { goal ->
        JSONObject()
            .put("target", goal.target)
            .put("saved", goal.saved)
            .put("deadline", goal.deadline ?: JSONObject.NULL)
    } ?: JSONObject.NULL)
