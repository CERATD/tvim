package com.example.tvim

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33])
class TypingChallengeStoreTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    @Before fun setup() {
        listOf("alarm_runtime", "typing_preferences").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
    @Test fun activityRecreationAndLanguageChangeKeepTheSameSentence() {
        val first = TypingChallengeStore(context).phraseFor("alarm", 1000)
        val restored = TypingChallengeStore(context).phraseFor("alarm", 1000)
        assertEquals(first.id, restored.id)
        assertEquals(first.english, restored.text("en"))
        assertEquals(first.russian, restored.text("ru"))
    }
    @Test fun nextRingingOccurrenceChoosesAnotherSentence() {
        val first = TypingChallengeStore(context).phraseFor("alarm", 1000)
        val next = TypingChallengeStore(context).phraseFor("alarm", 2000)
        assertNotEquals(first.id, next.id)
        val simultaneous = TypingChallengeStore(context).phraseFor("other-alarm", 2000)
        assertNotEquals(next.id, simultaneous.id)
    }
    @Test fun lastSentenceIsRememberedAfterRingingRuntimeIsCleared() {
        val first = TypingChallengeStore(context).phraseFor("alarm", 1000)
        context.getSharedPreferences("alarm_runtime", Context.MODE_PRIVATE).edit().clear().commit()
        assertNotEquals(first.id, TypingChallengeStore(context).phraseFor("alarm", 2000).id)
    }
}
