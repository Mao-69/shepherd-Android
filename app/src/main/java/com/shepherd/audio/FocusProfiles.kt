package com.shepherd.audio

/**
 * Per-Focus-level audio profiles, ported verbatim from crv/audio.py FOCUS_PROFILES.
 *
 * delta_f       — binaural beat frequency (Hz)
 * carrier       — root carrier (Hz); higher Focus = brighter tone
 * harmonics     — amplitudes of the 2nd, 3rd, 5th carrier overtones
 * pinkLevel     — pink-noise bed volume (0..1 of master)
 * amDepth       — amplitude-modulation depth at delta_f (0..1)
 * announce      — short spoken cue at transition
 */
data class FocusProfile(
    val key: String,
    val name: String,
    val deltaF: Double,
    val carrier: Double,
    val harmonics: Triple<Double, Double, Double>,
    val pinkLevel: Double,
    val amDepth: Double,
    val announce: String,
)

object FocusProfiles {
    val map: Map<String, FocusProfile> = listOf(
        FocusProfile("off", "Off (silent)", 0.0, 0.0, Triple(0.0, 0.0, 0.0), 0.0, 0.0, ""),
        FocusProfile("c1", "C-1 — Ordinary waking", 0.0, 80.0, Triple(0.10, 0.05, 0.02), 0.10, 0.0,
            "C one. Ordinary waking awareness."),
        FocusProfile("focus_10", "Focus 10 — Mind awake, body asleep", 4.0, 100.0,
            Triple(0.20, 0.10, 0.05), 0.18, 0.30, "Beginning Focus ten. Mind awake. Body asleep."),
        FocusProfile("focus_12", "Focus 12 — Expanded awareness", 7.0, 120.0,
            Triple(0.20, 0.10, 0.05), 0.16, 0.35, "Moving to Focus twelve. Expanded awareness."),
        FocusProfile("focus_15", "Focus 15 — No time", 10.0, 150.0,
            Triple(0.18, 0.10, 0.04), 0.14, 0.35, "Focus fifteen. The state of no time."),
        FocusProfile("focus_21", "Focus 21 — Bridge", 16.0, 200.0,
            Triple(0.15, 0.08, 0.04), 0.13, 0.30, "Focus twenty-one. The bridge."),
        FocusProfile("focus_22", "Focus 22 — Liminal", 14.0, 220.0,
            Triple(0.15, 0.08, 0.04), 0.13, 0.30, "Focus twenty-two."),
        FocusProfile("focus_23", "Focus 23", 12.0, 230.0,
            Triple(0.13, 0.07, 0.03), 0.12, 0.28, "Focus twenty-three."),
        FocusProfile("focus_24", "Focus 24 — Belief territories", 10.0, 240.0,
            Triple(0.13, 0.07, 0.03), 0.12, 0.28, "Focus twenty-four."),
        FocusProfile("focus_25", "Focus 25", 9.0, 250.0,
            Triple(0.12, 0.06, 0.03), 0.12, 0.28, "Focus twenty-five."),
        FocusProfile("focus_26", "Focus 26", 8.0, 260.0,
            Triple(0.12, 0.06, 0.03), 0.11, 0.28, "Focus twenty-six."),
        FocusProfile("focus_27", "Focus 27 — The park", 6.0, 270.0,
            Triple(0.10, 0.05, 0.02), 0.10, 0.25, "Focus twenty-seven. The park."),
    ).associateBy { it.key }

    fun get(key: String): FocusProfile = map[key] ?: map.getValue("off")
}
