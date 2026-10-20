package com.example.tvim

import android.content.Context

/** The selection belongs to a ringing occurrence, not to an activity or composition. */
class TypingChallengeStore(context: Context) {
    private val appContext = context.applicationContext
    private val runtime = appContext.getSharedPreferences("alarm_runtime", Context.MODE_PRIVATE)
    private val preferences = appContext.getSharedPreferences("typing_preferences", Context.MODE_PRIVATE)

    fun phraseFor(alarmId: String, started: Long, language: String = "en"): BookPhrase {
        if (!TypingPhrases.hasBook) {
            appContext.assets.open("typing-book-ru.tsv").bufferedReader(Charsets.UTF_8).use {
                TypingPhrases.loadBook(it.readLines())
            }
        }
        val occurrence = "$alarmId:$started"
        if (runtime.getString("typing_occurrence", null) == occurrence) {
            TypingPhrases.byId(runtime.getString("typing_phrase", null))?.let { return it }
        }
        val selected = TypingPhrases.choose(preferences.getString("previous_phrase", null), language = language)
        runtime.edit().putString("typing_occurrence", occurrence).putString("typing_phrase", selected.id).apply()
        preferences.edit().putString("previous_phrase", selected.id).apply()
        return selected
    }
}
