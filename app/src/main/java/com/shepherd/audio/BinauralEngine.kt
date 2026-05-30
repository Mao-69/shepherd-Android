package com.shepherd.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Real-time binaural induction engine — native port of crv/audio.py.
 *
 * Per active Focus level it layers, continuously:
 *   1. a pink-noise bed (Voss-McCartney 1/f)
 *   2. a stereo carrier stack: left ear at `carrier`, right ear at `carrier + deltaF`,
 *      so the perceived binaural beat is `deltaF`; plus 2nd/3rd/5th harmonics
 *   3. amplitude modulation at `deltaF` for stronger entrainment
 *
 * Generated on a background thread into a streaming AudioTrack at 44.1 kHz stereo.
 * Smoothly crossfades between Focus levels (target amplitudes are ramped).
 *
 * HEADPHONES REQUIRED — the binaural effect depends on independent ears.
 */
class BinauralEngine {

    companion object {
        private const val SR = 44100
        private const val FADE_SEC = 6.0   // crossfade time between focus levels
    }

    @Volatile private var running = false
    @Volatile private var masterTarget = 0.0
    @Volatile private var master = 0.0
    @Volatile private var current: FocusProfile = FocusProfiles.get("off")
    @Volatile private var target: FocusProfile = FocusProfiles.get("off")
    @Volatile private var blend = 1.0  // 0..1 crossfade progress toward `target`

    private var track: AudioTrack? = null
    private var genThread: Thread? = null

    // Phase accumulators (kept continuous to avoid clicks)
    private var phaseL = 0.0
    private var phaseR = 0.0
    private var amPhase = 0.0

    // Pink-noise state (Voss-McCartney)
    private val pinkRows = DoubleArray(16) { Random.nextDouble(-1.0, 1.0) }
    private var pinkCounter = 0
    private var pinkRunning = pinkRows.sum()

    val isRunning: Boolean get() = running

    fun start(initialVolume: Double = 0.35): Boolean {
        if (running) return true
        val minBuf = AudioTrack.getMinBufferSize(
            SR, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT
        )
        if (minBuf <= 0) return false
        val bufBytes = (minBuf * 2).coerceAtLeast(SR / 2 * 2 * 4)
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SR)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        masterTarget = initialVolume
        running = true
        track?.play()
        genThread = thread(name = "binaural-gen", isDaemon = true) { generate() }
        return true
    }

    fun stop() {
        running = false
        genThread?.join(500)
        genThread = null
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
    }

    fun setVolume(v: Double) { masterTarget = v.coerceIn(0.0, 1.0) }
    fun volume(): Double = master

    /** Crossfade to a new Focus level. */
    fun setFocus(key: String) {
        val p = FocusProfiles.get(key)
        if (p.key == target.key) return
        // Begin a fresh crossfade from whatever is currently sounding.
        current = effectiveProfileSnapshot()
        target = p
        blend = 0.0
    }

    fun currentFocusName(): String = target.name
    fun currentBeat(): Double = target.deltaF

    private fun effectiveProfileSnapshot(): FocusProfile = target

    private fun generate() {
        val frames = 1024
        val buf = FloatArray(frames * 2)
        val fadeStep = 1.0 / (FADE_SEC * SR)
        val masterStep = 1.0 / (1.5 * SR)

        while (running) {
            for (i in 0 until frames) {
                // Ramp master volume
                master += ((masterTarget - master)).coerceIn(-masterStep, masterStep)
                // Advance crossfade
                if (blend < 1.0) blend = (blend + fadeStep).coerceAtMost(1.0)

                val a = current
                val b = target
                val w = blend

                // Interpolated parameters
                val carrier = a.carrier * (1 - w) + b.carrier * w
                val deltaF = a.deltaF * (1 - w) + b.deltaF * w
                val h = Triple(
                    a.harmonics.first * (1 - w) + b.harmonics.first * w,
                    a.harmonics.second * (1 - w) + b.harmonics.second * w,
                    a.harmonics.third * (1 - w) + b.harmonics.third * w,
                )
                val pinkLevel = a.pinkLevel * (1 - w) + b.pinkLevel * w
                val amDepth = a.amDepth * (1 - w) + b.amDepth * w

                var left = 0.0
                var right = 0.0

                if (carrier > 0.0) {
                    val fL = carrier
                    val fR = carrier + deltaF
                    phaseL += 2 * PI * fL / SR
                    phaseR += 2 * PI * fR / SR
                    if (phaseL > 2 * PI) phaseL -= 2 * PI
                    if (phaseR > 2 * PI) phaseR -= 2 * PI

                    // Root + harmonics (harmonics share the same ear offset structure)
                    left  += sin(phaseL)
                    right += sin(phaseR)
                    left  += h.first  * sin(2 * phaseL) + h.second * sin(3 * phaseL) + h.third * sin(5 * phaseL)
                    right += h.first  * sin(2 * phaseR) + h.second * sin(3 * phaseR) + h.third * sin(5 * phaseR)

                    // Normalize carrier stack roughly into -1..1
                    val norm = 1.0 + h.first + h.second + h.third
                    left /= norm
                    right /= norm

                    // Amplitude modulation at deltaF
                    if (amDepth > 0.0 && deltaF > 0.0) {
                        amPhase += 2 * PI * deltaF / SR
                        if (amPhase > 2 * PI) amPhase -= 2 * PI
                        val am = 1.0 - amDepth * (0.5 - 0.5 * sin(amPhase))
                        left *= am
                        right *= am
                    }
                }

                // Pink-noise bed
                if (pinkLevel > 0.0) {
                    val pink = nextPink() * pinkLevel
                    left += pink
                    right += pink
                }

                val gain = master * 0.9
                buf[i * 2] = (left * gain).toFloat().coerceIn(-1f, 1f)
                buf[i * 2 + 1] = (right * gain).toFloat().coerceIn(-1f, 1f)
            }
            track?.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
        }
    }

    /** Voss-McCartney pink noise, ~1/f, output roughly -1..1. */
    private fun nextPink(): Double {
        pinkCounter++
        var n = pinkCounter
        var idx = 0
        while (idx < pinkRows.size && (n and 1) == 0) { n = n shr 1; idx++ }
        if (idx < pinkRows.size) {
            pinkRunning -= pinkRows[idx]
            pinkRows[idx] = Random.nextDouble(-1.0, 1.0)
            pinkRunning += pinkRows[idx]
        }
        return pinkRunning / pinkRows.size
    }
}
