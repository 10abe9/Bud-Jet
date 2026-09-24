package com.abe.bud_jet.capture

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * On-device journal of the last capture events, shown on the automatic-tracking screen so the
 * user (and developer) can see why a payment was or was not added. Never leaves the device.
 * Notification text is stored only for apps the user tracks.
 */
object CaptureLog {

    enum class Event { CONNECTED, DISCONNECTED, ADDED, TO_CONFIRM, DUPLICATE, IGNORED, NOT_TRACKED }

    data class Entry(val time: Long, val app: String, val event: Event, val detail: String)

    private const val FILE_NAME = "capture_log.jsonl"
    private const val MAX_ENTRIES = 50
    private const val TAG = "BudJetCapture"
    private val lock = Any()

    fun add(context: Context, app: String, event: Event, detail: String = "") {
        val entry = JSONObject()
            .put("t", System.currentTimeMillis())
            .put("a", app)
            .put("e", event.name)
            .put("d", detail)
            .toString()
        synchronized(lock) {
            val file = file(context)
            val lines = runCatching { file.readLines() }.getOrDefault(emptyList())
            runCatching { file.writeText((lines + entry).takeLast(MAX_ENTRIES).joinToString("\n")) }
        }
        // Logcat only in debug builds (adb logcat -s BudJetCapture).
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Log.d(TAG, "$event [$app] $detail")
        }
    }

    /** Newest first. */
    fun read(context: Context): List<Entry> = synchronized(lock) {
        runCatching { file(context).readLines() }.getOrDefault(emptyList())
            .mapNotNull { line ->
                runCatching {
                    val json = JSONObject(line)
                    Entry(
                        time = json.getLong("t"),
                        app = json.optString("a"),
                        event = Event.valueOf(json.getString("e")),
                        detail = json.optString("d")
                    )
                }.getOrNull()
            }
            .reversed()
    }

    fun clear(context: Context) = synchronized(lock) {
        file(context).delete()
        Unit
    }

    /** Language-neutral one-line summary of a parse result, e.g. "50 RUB · + · Т-Банк". */
    fun describe(result: NotificationParser.ParseResult): String = when (result) {
        is NotificationParser.ParseResult.Recognized ->
            summary(result.amount, result.currencyCode, result.isIncome, result.merchant)
        is NotificationParser.ParseResult.Uncertain ->
            summary(result.amount, result.currencyCode, result.isIncome, result.merchant)
        NotificationParser.ParseResult.Ignored -> "-"
    }

    private fun summary(amount: Double, currency: String?, isIncome: Boolean?, merchant: String?): String {
        val direction = when (isIncome) {
            true -> "+"
            false -> "−"
            null -> "?"
        }
        return listOf("$amount ${currency ?: "?"}", direction, merchant ?: "—").joinToString(" · ")
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)
}
