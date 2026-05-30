package com.shepherd.session

import android.content.Context
import java.util.UUID

/** Minimal user profile: a display name and a generated, stable user ID. */
data class Profile(val name: String, val userId: String, val onboarded: Boolean)

object ProfileGen {
    /** Short, human-shareable ID like "SHP-7F3A-21C8". */
    fun newUserId(): String {
        val raw = UUID.randomUUID().toString().replace("-", "").uppercase()
        return "SHP-${raw.substring(0, 4)}-${raw.substring(4, 8)}"
    }
}
