package com.abe.bud_jet.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.database.preferences.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

/**
 * Reads payment notifications on the device and turns them into transactions.
 *
 * The system binds this service while notification access is granted, so it does not need
 * to run as a foreground service. Only apps the user enabled as sources are parsed; for other
 * apps it only notes that they send payment-like notifications (package name, no text).
 */
class NotificationCaptureService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processing = Mutex()
    private val preferences by lazy { PreferenceManager.getInstance(applicationContext) }
    private val repository by lazy { FinanceRepositoryProvider.capture(applicationContext) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        preferences.setCaptureListenerAliveAt(System.currentTimeMillis())
        CaptureLog.add(applicationContext, "Bud-Jet", CaptureLog.Event.CONNECTED)
        scope.launch { repository.cleanupOldPending() }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        CaptureLog.add(applicationContext, "Bud-Jet", CaptureLog.Event.DISCONNECTED)
        CaptureAccess.requestRebind(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val posted = sbn ?: return
        if (posted.packageName == packageName) return
        val notification = posted.notification ?: return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (posted.isOngoing) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        if (title.isNullOrBlank() && text.isNullOrBlank()) return

        val now = System.currentTimeMillis()
        // Throttled heartbeat for the "last activity" indicator in the app.
        if (now - preferences.getCaptureListenerAliveAt() > HEARTBEAT_INTERVAL_MILLIS) {
            preferences.setCaptureListenerAliveAt(now)
        }

        val captured = CaptureRepository.CapturedNotification(
            packageName = posted.packageName,
            appLabel = appLabel(posted.packageName),
            title = title,
            text = text,
            postedAt = posted.postTime,
            externalId = externalId(posted, title, text)
        )
        scope.launch {
            val result = processing.withLock {
                repository.process(captured, preferences.getCurrencyCode())
            }
            log(captured, result)
            when (result.outcome) {
                CaptureRepository.Outcome.ADDED -> preferences.setCaptureLastCapturedAt(now)
                CaptureRepository.Outcome.NEEDS_CONFIRMATION -> {
                    preferences.setCaptureLastCapturedAt(now)
                    CaptureNotifier.showPendingReminder(applicationContext, repository.countPending())
                }
                CaptureRepository.Outcome.SOURCE_SUGGESTED -> if (result.newSourceDetected) {
                    CaptureNotifier.showSourceSuggestion(applicationContext, captured.appLabel)
                }
                else -> Unit
            }
        }
    }

    /**
     * Journal entry for the automatic-tracking screen. Notifications without an amount from
     * untracked apps (chats etc.) are not logged at all; untracked apps are logged without text.
     */
    private fun log(captured: CaptureRepository.CapturedNotification, result: CaptureRepository.ProcessResult) {
        val summary = CaptureLog.describe(result.parsed)
        val preview = listOfNotNull(captured.title, captured.text).joinToString(" | ").take(160)
        val (event, detail) = when (result.outcome) {
            CaptureRepository.Outcome.ADDED -> CaptureLog.Event.ADDED to "$summary\n$preview"
            CaptureRepository.Outcome.NEEDS_CONFIRMATION -> CaptureLog.Event.TO_CONFIRM to "$summary\n$preview"
            CaptureRepository.Outcome.DUPLICATE -> CaptureLog.Event.DUPLICATE to summary
            CaptureRepository.Outcome.IGNORED -> CaptureLog.Event.IGNORED to preview
            CaptureRepository.Outcome.SOURCE_SUGGESTED -> CaptureLog.Event.NOT_TRACKED to summary
        }
        if (result.outcome == CaptureRepository.Outcome.IGNORED && !result.trackedSource) return
        CaptureLog.add(applicationContext, captured.appLabel, event, detail)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun appLabel(packageName: String): String = runCatching {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

    /**
     * Same content from the same app within a minute is one payment (apps re-post and update
     * notifications); identical purchases a few minutes apart stay separate.
     */
    private fun externalId(sbn: StatusBarNotification, title: String?, text: String?): String {
        val raw = "${sbn.packageName}|${title.orEmpty()}|${text.orEmpty()}|${sbn.postTime / 60_000}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val HEARTBEAT_INTERVAL_MILLIS = 10 * 60 * 1000L
    }
}
