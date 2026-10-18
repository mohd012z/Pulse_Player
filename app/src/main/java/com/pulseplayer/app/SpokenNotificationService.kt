package com.pulseplayer.app

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.ComponentName
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import java.util.Locale

/** Opt-in, sender-only announcements. Never reads message bodies or persists identities. */
class SpokenNotificationService : NotificationListenerService() {
    private val recent = LinkedHashMap<String, Long>()
    private var speaker: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Do not announce historical active notifications on connection.
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean(ENABLED, false)) return
        val app = appLabel(sbn.packageName) ?: return
        val n = sbn.notification
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if (n.category == Notification.CATEGORY_CALL || n.category == Notification.CATEGORY_TRANSPORT) return
        if (n.extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false) && n.extras.getCharSequence(Notification.EXTRA_TITLE).isNullOrBlank()) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager?.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return
        val audio = getSystemService(AudioManager::class.java)
        if (audio?.mode != AudioManager.MODE_NORMAL || audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        if (km?.isDeviceLocked == true) return

        // Sender mode uses only the notification title, never EXTRA_TEXT, big text,
        // messaging histories, or contact data. Some providers put message text
        // in the title; suppress titles that resemble content or are too long.
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= 60 && !it.contains('\n') }
        val safeSender = title?.takeIf { !it.contains(':') && !it.contains("http", ignoreCase = true) }
        val announcement = if (safeSender != null) "New $app notification from $safeSender"
            else "New $app notification"

        val now = SystemClock.elapsedRealtime()
        val key = sbn.packageName + "|" + sbn.key + "|" + (safeSender ?: "")
        synchronized(recent) {
            recent.entries.removeAll { now - it.value > 60_000L }
            if (recent[key]?.let { now - it < 15_000L } == true) return
            recent[key] = now
            if (recent.size > 80) recent.remove(recent.keys.first())
        }
        main.post { speak(announcement) }
    }

    private fun appLabel(pkg: String): String? = when (pkg) {
        "com.whatsapp", "com.whatsapp.w4b" -> "WhatsApp"
        "org.telegram.messenger", "org.thunderdog.challegram" -> "Telegram"
        "com.google.android.apps.messaging", "com.samsung.android.messaging" -> "SMS"
        "com.google.android.gm" -> "Gmail"
        "com.google.android.calendar", "com.samsung.android.calendar" -> "Calendar"
        else -> null
    }

    private fun speak(message: String) {
        if (!getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(ENABLED, false)) return
        pending = message
        if (speaker == null) {
            speaker = TextToSpeech(applicationContext) { status ->
                main.post {
                    ready = status == TextToSpeech.SUCCESS
                    if (ready) {
                        speaker?.language = Locale.getDefault()
                        speaker?.setAudioAttributes(AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        deliver()
                    } else pending = null
                }
            }
        } else if (ready) deliver()
    }

    private fun deliver() {
        val text = pending ?: return
        pending = null
        if (!getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(ENABLED, false)) return
        speaker?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pulse_notification")
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        speaker?.stop()
        speaker?.shutdown()
        speaker = null
        super.onDestroy()
    }

    companion object {
        const val PREFS = "spoken_notifications"
        const val ENABLED = "enabled"
        fun hasAccess(context: Context): Boolean {
            val value = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val own = ComponentName(context, SpokenNotificationService::class.java).flattenToString()
            return value.split(':').any { it == own }
        }
    }
}
