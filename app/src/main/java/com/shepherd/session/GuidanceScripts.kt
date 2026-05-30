package com.shepherd.session

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * A "guidance script" (a.k.a. mode) supplies per-stage spoken text that
 * REPLACES the built-in stage descriptions. The short cue and the Focus-level
 * announcement still play; only the longer description is swapped for the
 * script's text for that stage.
 *
 * Text is keyed by stage code (see Protocol). Any stage left blank falls back
 * to the built-in description, so a script can cover all stages or just a few.
 *
 * Four scripts ship built-in (passive intel / active application / ARV /
 * beginner coaching); users can create and save their own, and a custom
 * target can carry its own saved script id.
 */
data class GuidanceScript(
    val id: String,
    val name: String,
    val description: String,
    val builtIn: Boolean,
    val perStage: Map<String, String>, // stageCode -> spoken text
) {
    fun textFor(stageCode: String): String? = perStage[stageCode]?.takeIf { it.isNotBlank() }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("description", description)
        put("builtIn", builtIn)
        put("perStage", JSONObject(perStage))
    }

    companion object {
        fun fromJson(o: JSONObject): GuidanceScript {
            val ps = o.optJSONObject("perStage") ?: JSONObject()
            val map = mutableMapOf<String, String>()
            ps.keys().forEach { k -> map[k] = ps.optString(k) }
            return GuidanceScript(
                id = o.getString("id"), name = o.getString("name"),
                description = o.optString("description"), builtIn = o.optBoolean("builtIn"),
                perStage = map,
            )
        }
    }
}

object GuidanceScripts {

    /** The implicit "use built-in descriptions" choice. */
    val DEFAULT = GuidanceScript(
        id = "default", name = "Default protocol",
        description = "The standard CRV stage guidance.",
        builtIn = true, perStage = emptyMap(),
    )

    private val PASSIVE = GuidanceScript(
        id = "passive_intel", name = "Passive intelligence gathering",
        description = "Observe and report. Take no action, form no intent — let the site describe itself.",
        builtIn = true,
        perStage = mapOf(
            "stage1" to "You are an observer only. The coordinate designates a site that exists " +
                "independently of you. Make your reflexive mark and let the site begin to register. " +
                "Do not reach for it; let it come.",
            "stage2" to "Collect sensory data passively, as a sensor would. Colors, textures, sounds, " +
                "temperatures. You are recording, not interpreting. Let each impression arrive and pass.",
            "stage3" to "Map the space without entering it. Note dimensions, structures, distances. " +
                "Remain at a neutral remove — a surveyor, not a participant.",
            "stage4" to "Register the ambient character of the site. What is its function, its mood, " +
                "its activity? Label your analytic overlays and set them aside. Stay neutral.",
            "stage5" to "Probe for concrete intelligence. What is present here? What is its purpose? " +
                "Who or what uses it? Gather specifics without speculation.",
            "stage6" to "Assemble the collected data into a single coherent picture of the site. " +
                "Report what is there. Add nothing that you did not perceive.",
        ),
    )

    private val ACTIVE = GuidanceScript(
        id = "active_application", name = "Active application",
        description = "Engage with intention. Hold a desired outcome and direct attention toward it.",
        builtIn = true,
        perStage = mapOf(
            "stage1" to "Receive the coordinate and bring your intention forward with it. You are not " +
                "only observing — you are engaging. Make your mark and let your purpose anchor to the target.",
            "stage2" to "As impressions arise, hold your intended outcome steady alongside them. Notice " +
                "where the target and your intention meet. Record both the data and the points of contact.",
            "stage3" to "Within the target's space, locate the place where change is possible. Sense " +
                "the structure and find the leverage point your intention can rest upon.",
            "stage4" to "Feel the dynamic character of the target and direct a calm, definite intention " +
                "into it. Do not strain. Hold the outcome as already settled.",
            "stage5" to "Probe the mechanism of the intended effect. How does the change express itself? " +
                "What does the target look like once your intention has taken hold?",
            "stage6" to "Build the model of the target in its desired state. Hold it whole and complete. " +
                "Release the intention into it and let the model stand.",
        ),
    )

    private val ARV = GuidanceScript(
        id = "arv_predictive", name = "Associative / ARV (predictive)",
        description = "Associative Remote Viewing — describe the feedback image tied to a future outcome.",
        builtIn = true,
        perStage = mapOf(
            "stage1" to "The coordinate points to the image you will be shown later — the one bound to " +
                "the outcome. Do not think about the event. Make your reflexive mark toward the future image.",
            "stage2" to "Describe the future feedback image through your senses. Colors, textures, " +
                "shapes, sounds. You are perceiving a picture you have not yet seen. Record it plainly.",
            "stage3" to "Sketch the dimensional qualities of that future image. Its layout, its forms, " +
                "its scale. Let the picture take shape before you.",
            "stage4" to "Sense the aesthetic of the feedback image — its tone, its impression. Note any " +
                "analytic guesses about the outcome and set them firmly aside. Stay with the image only.",
            "stage5" to "Probe the distinguishing features of the future image. What makes it unmistakable? " +
                "What detail will let you recognize it when it is revealed?",
            "stage6" to "Assemble the future feedback image into one clear model. This is what you expect " +
                "to see. Commit to it before any outcome is known.",
        ),
    )

    private val BEGINNER = GuidanceScript(
        id = "beginner", name = "Beginner coaching",
        description = "Plain-language guidance for first sessions, with gentle reassurance.",
        builtIn = true,
        perStage = mapOf(
            "stage1" to "This is the first step and it is meant to be quick. When you hear the " +
                "coordinate, just let your pen make a small spontaneous mark. There is no wrong answer. " +
                "Then describe how that mark felt.",
            "stage2" to "Now go sense by sense. What colors come to mind? Any textures, sounds, smells? " +
                "Write whatever shows up, even if it seems random. Random is fine. Trust the first thing.",
            "stage3" to "Think about size and shape. Is the thing big or small? Tall or flat? Near or far? " +
                "A rough sketch is perfect. Don't worry about being right.",
            "stage4" to "What's the feeling of this place? Busy, calm, natural, built by people? If your " +
                "mind shouts a guess like 'it's a bridge', just write the guess down and keep going.",
            "stage5" to "Now look a little closer. Any details? What might it be used for? Take your time. " +
                "The longer you stay relaxed, the more tends to come.",
            "stage6" to "Last step. Put it all together in your mind like building it from clay. What's " +
                "your best overall picture? Say it simply. You did the work — let the summary form.",
        ),
    )

    val builtins: List<GuidanceScript> = listOf(DEFAULT, PASSIVE, ACTIVE, ARV, BEGINNER)
}

/** Stores user-created scripts on disk and serves builtins + user scripts together. */
class ScriptLibrary(context: Context) {
    private val file = File(context.filesDir, "guidance_scripts.json")

    fun userScripts(): List<GuidanceScript> {
        if (!file.exists()) return emptyList()
        val arr = runCatching { JSONArray(file.readText()) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).map { GuidanceScript.fromJson(arr.getJSONObject(it)) }
    }

    fun all(): List<GuidanceScript> = GuidanceScripts.builtins + userScripts()

    fun byId(id: String): GuidanceScript =
        all().firstOrNull { it.id == id } ?: GuidanceScripts.DEFAULT

    fun save(script: GuidanceScript) {
        val list = userScripts().filter { it.id != script.id } + script.copy(builtIn = false)
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        file.writeText(arr.toString(2))
    }

    fun delete(id: String) {
        val list = userScripts().filter { it.id != id }
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        file.writeText(arr.toString(2))
    }

    fun newId(): String = "user_" + System.currentTimeMillis().toString(36)
}
