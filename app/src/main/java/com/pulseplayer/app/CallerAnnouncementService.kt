package com.pulseplayer.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.PhoneAccount
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Optional caller announcement. Only Android's user-selected call-screening role
 * supplies incoming call details. This service never blocks or silences calls.
 */
class CallerAnnouncementService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) return
        // Respond immediately: the system requires a response within five seconds.
        respondToCall(callDetails, CallResponse.Builder().build())
        if (!getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(ENABLED, false)) return
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return
        val handle = callDetails.handle ?: return
        if (handle.scheme != PhoneAccount.SCHEME_TEL) return
        val number = handle.schemeSpecificPart?.takeIf { it.isNotBlank() } ?: return
        Thread {
            val name = runCatching {
                val lookup = android.net.Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    android.net.Uri.encode(number)
                )
                contentResolver.query(
                    lookup, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: return@Thread
            // No number, contact identity, or announcement text is logged or stored.
            Handler(Looper.getMainLooper()).post {
                CallerSpeech.speak(applicationContext, "Incoming call from $name")
            }
        }.start()
    }

    companion object {
        const val PREFS = "caller_announcement"
        const val ENABLED = "enabled"
    }
}

/** Application-scoped speech instance survives the short-lived screening binding. */
internal object CallerSpeech {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    @Synchronized
    fun speak(context: Context, text: String) {
        if (!context.getSharedPreferences(CallerAnnouncementService.PREFS, Context.MODE_PRIVATE)
                .getBoolean(CallerAnnouncementService.ENABLED, false)) return
        pending = text
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { result ->
                Handler(Looper.getMainLooper()).post {
                    ready = result == TextToSpeech.SUCCESS
                    if (ready) {
                        tts?.language = Locale.getDefault()
                        tts?.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        deliver()
                    } else pending = null
                }
            }
        } else if (ready) deliver()
    }

    private fun deliver() {
        val message = pending ?: return
        pending = null
        // TTS uses Android's selected output route; a third-party app cannot
        // guarantee forcing the loudspeaker while Bluetooth or a call owns audio.
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "pulse_caller")
    }
}
