package com.shepherd.session

import com.shepherd.audio.BinauralEngine
import com.shepherd.audio.FocusProfiles
import com.shepherd.audio.VoiceEngine
import com.shepherd.ble.P58Manager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SessionPhase { IDLE, RUNNING, PAUSED, FINISHED }

data class SessionUi(
    val phase: SessionPhase = SessionPhase.IDLE,
    val stageIndex: Int = 0,
    val stageCount: Int = 0,
    val stage: Stage? = null,
    val stageElapsedSec: Int = 0,
    val stageDurationSec: Int = 1,
    val totalElapsedSec: Int = 0,
    val totalDurationSec: Int = 1,
    val focusName: String = "Off",
    val beatHz: Double = 0.0,
    val coordinate: String = "",
    val statusMessage: String = "",
    val coherentSeconds: Int = 0,
    val stateLabel: String = "baseline",
    val stageDescription: String = "",
)

class SessionEngine(
    private val scope: CoroutineScope,
    private val audio: BinauralEngine,
    private val voice: VoiceEngine,
    private val watch: P58Manager?,
) {
    private val _ui = MutableStateFlow(SessionUi())
    val ui: StateFlow<SessionUi> = _ui.asStateFlow()

    private var job: Job? = null
    private var supportJobs = mutableListOf<Job>()
    @Volatile private var paused = false
    @Volatile private var advance = false
    @Volatile private var aborted = false
    @Volatile private var vitalsInProgress = false
    private var lastCoordSpokenAt = 0L

    private lateinit var stages: List<Stage>
    private var coordInterval = 180_000L // ms
    private var activeScript: GuidanceScript = GuidanceScripts.DEFAULT
    private val notes = StringBuilder()
    private val transitions = mutableListOf<String>()
    private var startWall = 0L

    fun start(tier: Tier, extended: Boolean, coordinate: String, intention: String,
              initialVolume: Double, coordIntervalSec: Int, script: GuidanceScript = GuidanceScripts.DEFAULT) {
        if (job != null) return
        stages = Protocol.build(tier, extended)
        coordInterval = coordIntervalSec * 1000L
        activeScript = script
        aborted = false; paused = false; advance = false
        startWall = System.currentTimeMillis()

        notes.clear()
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        notes.append("# Shepherd CRV Session\n\n")
        notes.append("- Date: $ts\n- Coordinate: $coordinate\n- Intention: ${intention.ifBlank { "(none stated)" }}\n")
        notes.append("- Protocol: ${tier.label}${if (extended) " + extended" else ""} (${stages.size} stages)\n")
        notes.append("- Guidance: ${activeScript.name}\n\n")

        audio.start(initialVolume)

        _ui.value = SessionUi(
            phase = SessionPhase.RUNNING,
            stageCount = stages.size,
            coordinate = coordinate,
            totalDurationSec = Protocol.totalSeconds(stages),
        )

        job = scope.launch { runStages(coordinate) }
        supportJobs += scope.launch { coordSpeaker(coordinate) }
        if (watch != null) supportJobs += scope.launch { hrPoker() }
    }

    fun togglePause() {
        paused = !paused
        _ui.value = _ui.value.copy(
            phase = if (paused) SessionPhase.PAUSED else SessionPhase.RUNNING,
            statusMessage = if (paused) "Paused" else "Resumed"
        )
    }

    fun advanceStage() { advance = true }

    fun markEvent(note: String) {
        val t = elapsedClock(_ui.value.totalElapsedSec)
        notes.append("\n> [$t] $note\n")
        _ui.value = _ui.value.copy(statusMessage = "Event marked")
    }

    fun appendNote(stageCode: String, text: String) {
        notes.append("\n$text\n")
    }

    fun remeasureVitals() {
        if (watch == null || vitalsInProgress) return
        supportJobs += scope.launch { vitalsSequence("Manual re-measure") }
    }

    fun abort() {
        aborted = true
        finish()
    }

    fun exportNotes(): String = notes.toString()

    private suspend fun runStages(coordinate: String) {
        if (watch != null) vitalsSequence("Initial vitals")

        for ((idx, stage) in stages.withIndex()) {
            if (aborted) break
            // Transition
            audio.setFocus(stage.focusLevel)
            val prof = FocusProfiles.get(stage.focusLevel)
            transitions.add("${elapsedClock(_ui.value.totalElapsedSec)}  ${stage.name}")
            notes.append("\n## ${stage.name}\n")
            for (p in stage.prompts) notes.append("- $p\n")

            // Voice: focus announcement, then cue, then description.
            // An active guidance script REPLACES the built-in description for that stage.
            if (prof.announce.isNotBlank()) voice.speak(prof.announce, flush = true)
            stage.cueMessage?.let { voice.speak(it) }
            val spokenDescription = activeScript.textFor(stage.code) ?: stage.description
            voice.speak(spokenDescription)

            if (stage.code == "stage1") {
                voice.speakCoordinate(coordinate)
                lastCoordSpokenAt = System.currentTimeMillis()
            }

            _ui.value = _ui.value.copy(
                stageIndex = idx,
                stage = stage,
                stageDurationSec = stage.durationSec,
                stageElapsedSec = 0,
                stageDescription = spokenDescription,
                focusName = audio.currentFocusName(),
                beatHz = audio.currentBeat(),
                statusMessage = stage.cueMessage ?: "",
            )

            advance = false
            val stageStart = System.currentTimeMillis()
            var pausedAccum = 0L
            var pauseStart = 0L

            while (isActiveStage()) {
                if (aborted) break
                if (paused) {
                    if (pauseStart == 0L) pauseStart = System.currentTimeMillis()
                } else if (pauseStart != 0L) {
                    pausedAccum += System.currentTimeMillis() - pauseStart
                    pauseStart = 0L
                }
                // While paused, freeze elapsed at the moment pause began.
                val nowPauseGap = if (pauseStart != 0L) System.currentTimeMillis() - pauseStart else 0L
                val effElapsedMs = System.currentTimeMillis() - stageStart - pausedAccum - nowPauseGap
                val stageElapsed = (effElapsedMs / 1000).toInt().coerceAtLeast(0)
                val totalElapsed = ((System.currentTimeMillis() - startWall) / 1000).toInt()
                _ui.value = _ui.value.copy(
                    stageElapsedSec = stageElapsed.coerceAtMost(stage.durationSec),
                    totalElapsedSec = totalElapsed,
                    focusName = audio.currentFocusName(),
                    beatHz = audio.currentBeat(),
                )
                if (!paused && stageElapsed >= stage.durationSec) break
                if (advance) { advance = false; break }
                delay(250)
            }
        }
        finish()
    }

    private fun isActiveStage(): Boolean = !aborted

    private suspend fun coordSpeaker(coordinate: String) {
        while (scope.isActive && !aborted) {
            delay(5000)
            val s = _ui.value.stage ?: continue
            if (s.code !in Protocol.coordActiveStages) continue
            if (vitalsInProgress) continue
            val now = System.currentTimeMillis()
            if (now - lastCoordSpokenAt >= coordInterval) {
                lastCoordSpokenAt = now
                _ui.value = _ui.value.copy(statusMessage = "Coordinate cue: $coordinate")
                voice.speakCoordinate(coordinate)
            }
        }
    }

    /** The P58 doesn't auto-stream HR — poke every 5s, skipping while BP/SpO2 run. */
    private suspend fun hrPoker() {
        val w = watch ?: return
        while (scope.isActive && !aborted) {
            delay(5000)
            if (vitalsInProgress) continue
            runCatching { w.measureHeartRate() }
        }
    }

    private suspend fun vitalsSequence(label: String) {
        val w = watch ?: return
        vitalsInProgress = true
        try {
            _ui.value = _ui.value.copy(statusMessage = "$label: BP (~45s)…")
            w.measureBloodPressure(); delay(45_000)
            _ui.value = _ui.value.copy(statusMessage = "$label: SpO₂ (~30s)…")
            w.measureSpo2(); delay(30_000)
            _ui.value = _ui.value.copy(statusMessage = "$label: complete")
        } finally {
            vitalsInProgress = false
        }
    }

    private fun finish() {
        if (_ui.value.phase == SessionPhase.FINISHED) return
        aborted = true
        supportJobs.forEach { it.cancel() }
        supportJobs.clear()
        job?.cancel(); job = null
        audio.setFocus("off")
        notes.append("\n---\n\n## Stage transitions\n")
        transitions.forEach { notes.append("- $it\n") }
        _ui.value = _ui.value.copy(phase = SessionPhase.FINISHED, statusMessage = "Session complete")
    }

    private fun elapsedClock(sec: Int): String {
        val m = sec / 60; val s = sec % 60
        return "%02d:%02d".format(m, s)
    }
}
