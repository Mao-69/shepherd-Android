package com.shepherd.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/**
 * Voice cue engine with selectable, natural-sounding voices.
 *
 * Android exposes whatever TTS voices are installed on the device. The most
 * natural ones come from Google's neural ("network") voices via the Google
 * Speech Services engine; this class enumerates them, lets the UI pick one,
 * previews it, and persists the choice (persistence handled by the caller).
 *
 * We prefer, in order: an explicitly chosen voice → a high-quality non-network
 * en-US voice → the best network en-US voice → the engine default. Quality and
 * latency flags are surfaced so the UI can label voices ("Neural", "Enhanced").
 */
class VoiceEngine(context: Context) {

    data class VoiceOption(
        val id: String,           // Voice.name, stable identifier
        val label: String,        // human-friendly
        val locale: String,
        val neural: Boolean,      // network/neural = most natural
        val enhanced: Boolean,    // high quality (>= QUALITY_HIGH), on-device
        val needsNetwork: Boolean,
    )

    private var tts: TextToSpeech? = null
    @Volatile var ready = false; private set
    @Volatile var speaking = false; private set
    private var counter = 0
    private var pendingVoiceId: String? = null

    // Voice tuning (caller-adjustable)
    @Volatile var rate = 0.9f
    @Volatile var pitch = 0.95f

    private var onReady: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                applyRatePitch()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { speaking = true }
                    override fun onDone(utteranceId: String?) { speaking = false }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { speaking = false }
                })
                // Apply a saved/pending selection, else auto-pick the most natural.
                pendingVoiceId?.let { selectVoice(it) } ?: autoSelectBest()
                ready = true
                onReady?.invoke()
            }
        }
    }

    fun setOnReady(cb: () -> Unit) { onReady = cb; if (ready) cb() }

    private fun applyRatePitch() {
        tts?.setSpeechRate(rate)
        tts?.setPitch(pitch)
    }

    fun setRatePitch(newRate: Float, newPitch: Float) {
        rate = newRate; pitch = newPitch; applyRatePitch()
    }

    /** All usable voices, most-natural first. */
    fun availableVoices(): List<VoiceOption> {
        val voices = runCatching { tts?.voices }.getOrNull() ?: return emptyList()
        return voices
            .filter { it.name != null && it.locale != null && it.locale.language == "en" }
            .filter { it.quality >= Voice.QUALITY_VERY_LOW }
            .map { v ->
                val neural = v.isNetworkConnectionRequired ||
                    v.name.contains("network", ignoreCase = true) ||
                    v.name.contains("neural", ignoreCase = true)
                val enhanced = v.quality >= Voice.QUALITY_HIGH
                VoiceOption(
                    id = v.name,
                    label = prettyLabel(v),
                    locale = v.locale.toString(),
                    neural = neural,
                    enhanced = enhanced,
                    needsNetwork = v.isNetworkConnectionRequired,
                )
            }
            // Most natural first: network/neural, then enhanced, then by quality.
            .sortedWith(compareByDescending<VoiceOption> { it.neural }
                .thenByDescending { it.enhanced }
                .thenBy { it.label })
            .distinctBy { it.id }
    }

    private fun prettyLabel(v: Voice): String {
        // Voice.name is like "en-us-x-sfg-network" or "en-US-language".
        val region = v.locale.country.ifBlank { v.locale.language.uppercase() }
        val tag = when {
            v.isNetworkConnectionRequired -> "Neural"
            v.quality >= Voice.QUALITY_HIGH -> "Enhanced"
            v.quality >= Voice.QUALITY_NORMAL -> "Standard"
            else -> "Basic"
        }
        // Try to surface a short variant token (e.g. "sfg", "iol") for differentiation.
        val variant = Regex("-x-([a-z0-9]+)").find(v.name)?.groupValues?.getOrNull(1)?.uppercase()
        return if (variant != null) "$region · $tag ($variant)" else "$region · $tag"
    }

    fun currentVoiceId(): String? = runCatching { tts?.voice?.name }.getOrNull()

    fun selectVoice(id: String): Boolean {
        val t = tts ?: run { pendingVoiceId = id; return false }
        val match = runCatching { t.voices }.getOrNull()?.firstOrNull { it.name == id }
            ?: run { pendingVoiceId = id; return false }
        val r = t.setVoice(match)
        pendingVoiceId = null
        return r == TextToSpeech.SUCCESS
    }

    /** Pick the most natural available voice automatically. */
    private fun autoSelectBest() {
        availableVoices().firstOrNull()?.let { selectVoice(it.id) }
    }

    fun listEngines(): List<String> =
        runCatching { tts?.engines?.map { it.name } }.getOrNull() ?: emptyList()

    fun preview(id: String) {
        selectVoice(id)
        speak("This is the guiding voice for your session.", flush = true)
    }

    fun speak(text: String, flush: Boolean = false) {
        if (!ready || text.isBlank()) return
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        tts?.speak(text, mode, params, "u${counter++}")
    }

    /** Speak a coordinate digit-by-digit, as SRI viewers received them. */
    fun speakCoordinate(coord: String) {
        val spoken = coord.filter { it.isDigit() }.toCharArray().joinToString(", ")
        speak("Target coordinate. $spoken.")
    }

    fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
    }
}
