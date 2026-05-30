package com.shepherd.session

/**
 * CRV stage definitions and timing tiers — a Kotlin port of crv/protocol.py.
 *
 * Six-stage Controlled Remote Viewing protocol (Ingo Swann / SRI, per the
 * partially-declassified DIA manual) plus the Monroe Gateway Focus 21–27
 * journey extension. Three timing tiers × extended-or-not = six protocols.
 *
 * Durations and stage text mirror the research tool so the guided session
 * behaves identically.
 */

enum class Tier(val label: String, val approxMinutes: String) {
    SHORT("Short", "~58 min"),
    STANDARD("Standard", "~103 min"),
    LONG("Long", "~152 min")
}

data class Stage(
    val code: String,
    val name: String,
    val durationSec: Int,
    val focusLevel: String,
    val description: String,
    val prompts: List<String> = emptyList(),
    val cueMessage: String? = null,
)

object Protocol {

    private val durations: Map<Tier, Map<String, Int>> = mapOf(
        Tier.SHORT to mapOf(
            "baseline" to 180, "induction" to 300, "stage1" to 120, "stage2" to 300,
            "stage3" to 600, "stage4" to 600, "stage5" to 600, "stage6" to 600,
            "cooldown" to 180, "journey_21" to 420, "journey_22" to 300,
            "journey_23_to_26" to 600, "journey_27" to 420,
        ),
        Tier.STANDARD to mapOf(
            "baseline" to 300, "induction" to 1200, "stage1" to 90, "stage2" to 420,
            "stage3" to 900, "stage4" to 720, "stage5" to 1080, "stage6" to 1200,
            "cooldown" to 300, "journey_21" to 540, "journey_22" to 420,
            "journey_23_to_26" to 900, "journey_27" to 600,
        ),
        Tier.LONG to mapOf(
            "baseline" to 300, "induction" to 1800, "stage1" to 120, "stage2" to 600,
            "stage3" to 1200, "stage4" to 900, "stage5" to 1800, "stage6" to 1800,
            "cooldown" to 600, "journey_21" to 900, "journey_22" to 600,
            "journey_23_to_26" to 1500, "journey_27" to 900,
        ),
    )

    private val baseStages = listOf(
        Stage("baseline", "Pre-Session Baseline", 0, "off",
            "Sit comfortably with the watch snug on your wrist. Eyes open or closed, breathe naturally. " +
                "The program is recording your resting heart-rate baseline. Don't speak or move much.",
            cueMessage = "Establishing physiological baseline."),
        Stage("induction", "Induction — Focus 10", 0, "focus_10",
            "Put your headphones on. Eyes closed. Allow your body to relax completely while keeping your " +
                "mind awake. This is Focus 10: mind awake, body asleep. Breathe slowly. Don't rush this — " +
                "give yourself time to actually drop into the state. Notice the relaxation moving through " +
                "your body from feet to head.",
            cueMessage = "Beginning Focus 10 induction. Headphones on, eyes closed."),
        Stage("stage1", "Stage I — Ideogram", 0, "focus_10",
            "The target coordinate has been presented. The instant you received it, make a quick reflexive " +
                "pencil mark — the ideogram. Don't think; just let your hand respond. Then decode what your " +
                "hand felt: primary motion, primary substance, primary descriptor. This stage is fast — stay " +
                "reflexive, don't analyze.",
            prompts = listOf(
                "Target identifier:", "Ideogram (describe the mark you drew):",
                "Motion / feel of the ideogram:", "Primary descriptor (one word):"),
            cueMessage = "Stage I: Ideogram. Mark, then decode."),
        Stage("stage2", "Stage II — Sensory Data", 0, "focus_12",
            "Probe each sense individually. Don't analyze — just record raw impressions as they arise. " +
                "If your mind tries to label the target — an A-O-L, analytical overlay — write it down and " +
                "let it pass. Probe vision, then sound, then smell, then touch, then taste, then ambient feel.",
            prompts = listOf(
                "Colors / visual impressions:", "Textures / surfaces:", "Temperatures / ambient feel:",
                "Smells:", "Sounds:", "Tastes:", "AOLs noticed:"),
            cueMessage = "Stage II: Sensory data. Probe each sense in turn."),
        Stage("stage3", "Stage III — Dimensions", 0, "focus_12",
            "Sketch the dimensional aspects of the target. Size relative to your body? Shape? Mass? Distance? " +
                "Height? Is anything moving? Capture quick spatial sketches in your notes. Take your time — " +
                "let the spatial impressions deepen.",
            prompts = listOf(
                "Dimensional sketch / spatial structure:", "Size and shape:",
                "Movement / dynamics:", "Distance or scale impressions:"),
            cueMessage = "Stage III: Dimensional aspects. Sketch the space."),
        Stage("stage4", "Stage IV — Aesthetic Impact / AOL", 0, "focus_12",
            "What's the emotional flavor of the target? Awe? Calm? Industrial? Sacred? Mundane? Record each " +
                "Analytic Overlay — your mind's labeling guess — as it appears, then move past it. Don't fight " +
                "A-O-Ls; just name them and continue.",
            prompts = listOf(
                "Aesthetic impact / emotional content:", "AOLs (analytical labels your mind tried):",
                "Energetic / dynamic descriptors:"),
            cueMessage = "Stage IV: Aesthetic impact. Catch the AOLs."),
        Stage("stage5", "Stage V — Probing", 0, "focus_15",
            "Probe specific aspects of the target. Concrete details, function, materials, who uses it. Ask the " +
                "target directly: What is this? What is its purpose? Allow concrete answers. This is a long " +
                "stage. Take your time with it — the deepest data often comes after the first ten minutes.",
            prompts = listOf(
                "Concrete details / specific features:", "Function or purpose:",
                "Notable identifying characteristics:", "Materials / textures up close:"),
            cueMessage = "Stage V: Probing for concrete details. Take your time."),
        Stage("stage6", "Stage VI — Modeling", 0, "focus_15",
            "Synthesize everything into a model. If you had clay, what would you sculpt? Build a " +
                "three-dimensional mental model. Describe spatial relationships between elements. This is the " +
                "final integration — the place where the most coherent picture emerges. Don't rush; let it form.",
            prompts = listOf(
                "3D synthesis / model description:", "Spatial relationships:",
                "Overall best-guess summary of the target:"),
            cueMessage = "Stage VI: Build the model. Let it form."),
        Stage("cooldown", "Cool-down", 0, "focus_10",
            "Return slowly to ordinary consciousness. Take a few deep breaths. Feel your body in the chair, " +
                "the watch on your wrist. When you're ready, open your eyes. Add any final notes or " +
                "observations.",
            prompts = listOf(
                "Final observations or anything you want to add:",
                "Confidence (1-10) in primary impressions:"),
            cueMessage = "Cool-down. Return to ordinary consciousness."),
    )

    private val journeyStages = listOf(
        Stage("journey_21", "Focus 21 — Bridge to other-energy systems", 0, "focus_21",
            "Allow the awareness to bridge beyond the time-space construct. Notice any sense of expansion, of " +
                "moving between states. Do not steer — observe what arises.",
            prompts = listOf("Sense of bridging / transition:", "Energetic impressions:",
                "Any visual or auditory phenomena:"),
            cueMessage = "Entering Focus 21 — the bridge."),
        Stage("journey_22", "Focus 22 — Liminal states", 0, "focus_22",
            "Liminal awareness. Some practitioners report encountering entities or impressions of partial " +
                "consciousness here. Record without analysis.",
            prompts = listOf("Any encounters or contacts:", "Quality of awareness here:"),
            cueMessage = "Focus 22 — liminal."),
        Stage("journey_23_to_26", "Focus 23-26 — Belief System Territories", 0, "focus_25",
            "Per Monroe's literature, these levels are sometimes described as zones organized around shared " +
                "beliefs. Move through them with curiosity. The audio will keep you at Focus 25 as a central " +
                "anchor.",
            prompts = listOf("Impressions of any structured environments:",
                "Beliefs or themes that feel present here:"),
            cueMessage = "Moving through Focus 23 to 26."),
        Stage("journey_27", "Focus 27 — The park", 0, "focus_27",
            "Monroe described Focus 27 as a reception center — a constructed gathering place. Rest here. " +
                "Notice what is spontaneous versus what is constructed by expectation.",
            prompts = listOf("Description of the environment:", "Anyone or anything encountered:",
                "Final observations from the journey:"),
            cueMessage = "Focus 27 — the park."),
    )

    /** Stage codes that get periodic coordinate re-vocalization (active CRV stages). */
    val coordActiveStages = setOf("stage1", "stage2", "stage3", "stage4", "stage5", "stage6")

    fun build(tier: Tier, extended: Boolean): List<Stage> {
        val durs = durations.getValue(tier)
        fun patch(list: List<Stage>) = list.map { it.copy(durationSec = durs[it.code] ?: it.durationSec) }
        val base = patch(baseStages)
        if (!extended) return base
        val journey = patch(journeyStages)
        val out = mutableListOf<Stage>()
        for (s in base) {
            if (s.code == "cooldown") out.addAll(journey)
            out.add(s)
        }
        return out
    }

    fun totalSeconds(stages: List<Stage>): Int = stages.sumOf { it.durationSec }

    /** SRI-style 8-digit coordinate, formatted XXX-XXXX X. */
    fun generateCoordinate(): String {
        val d = (1..8).map { (0..9).random() }.joinToString("")
        return "${d.substring(0, 3)}-${d.substring(3, 7)} ${d.substring(7)}"
    }
}
