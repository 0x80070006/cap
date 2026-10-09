package org.capnav.app.navigation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * On-device TextToSpeech only. Prefers voices that do not require network so no instruction text
 * is ever sent to a cloud voice. Other audio is ducked while speaking.
 */
class Voice(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs)
        .build()
    private val pending = AtomicInteger()
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) configure()
    }

    var enabled: Boolean = true

    private fun configure() {
        tts.setAudioAttributes(attrs)
        runCatching { tts.language = Locale.getDefault() }
        runCatching {
            tts.voices?.filter { it.locale.language == Locale.getDefault().language && !it.isNetworkConnectionRequired }
                ?.maxByOrNull { it.quality }
                ?.let { tts.voice = it }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = release()
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = release()
        })
    }

    private fun release() {
        if (pending.decrementAndGet() <= 0) {
            pending.set(0)
            audio.abandonAudioFocusRequest(focus)
        }
    }

    fun speak(text: String, flush: Boolean = false) {
        if (!enabled || !ready || text.isBlank()) return
        pending.incrementAndGet()
        audio.requestAudioFocus(focus)
        tts.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, Bundle.EMPTY, "cap-${System.nanoTime()}")
    }

    fun stop() {
        tts.stop()
        pending.set(0)
        audio.abandonAudioFocusRequest(focus)
    }
}
