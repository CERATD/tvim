package com.example.tvim

import android.content.Context

/** The selection belongs to a ringing occurrence, not to an activity or composition. */
class TypingChallengeStore(context: Context) {
    private val runtime = context.getSharedPreferences("alarm_runtime", Context.MODE_PRIVATE)
    private val preferences = context.getSharedPreferences("typing_preferences", Context.MODE_PRIVATE)

    fun phraseFor(alarmId: String, started: Long): BookPhrase {
        val occurrence = "$alarmId:$started"
        if (runtime.getString("typing_occurrence", null) == occurrence) {
            TypingPhrases.byId(runtime.getString("typing_phrase", null))?.let { return it }
        }
        val selected = TypingPhrases.choose(preferences.getString("previous_phrase", null))
        runtime.edit().putString("typing_occurrence", occurrence).putString("typing_phrase", selected.id).apply()
        preferences.edit().putString("previous_phrase", selected.id).apply()
        return selected
    }
}
