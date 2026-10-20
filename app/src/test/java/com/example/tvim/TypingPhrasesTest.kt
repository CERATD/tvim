package com.example.tvim

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class TypingPhrasesTest {
    @Test fun everyPhraseHasBothSupportedLanguagesAndAUniqueId() {
        assertTrue(TypingPhrases.phrases.size >= 16)
        assertEquals(TypingPhrases.phrases.size, TypingPhrases.phrases.map { it.id }.distinct().size)
        TypingPhrases.phrases.forEach {
            assertTrue(it.text("ru").isNotBlank())
            assertTrue(it.text("en").isNotBlank())
            assertEquals(it.english.ifBlank { it.russian }, it.text("unknown"))
            assertTrue(TypingPhrases.matches(it.russian, it.russian))
            assertTrue(TypingPhrases.matches(it.text("en"), it.text("en")))
        }
    }
    @Test fun consecutiveOccurrencesDoNotRepeatButSelectionVaries() {
        var previous: String? = null
        val seen = mutableSetOf<String>()
        val random = Random(1234)
        repeat(100) {
            val chosen = TypingPhrases.choose(previous, random)
            assertNotEquals(previous, chosen.id)
            seen.add(chosen.id)
            previous = chosen.id
        }
        assertTrue(seen.size > 10)
    }
    @Test fun casePunctuationAndExtraWhitespaceDoNotBlockDismissal() {
        assertTrue(TypingPhrases.matches("  THE BLADES MET and the steel shattered  ", "The blades met, and the steel shattered."))
        assertTrue(TypingPhrases.matches("клинки\n СОПРИКОСНУЛИСЬ и сталь разбилась", "Клинки соприкоснулись, и сталь разбилась."))
        assertTrue(TypingPhrases.matches("Елка", "Ёлка."))
    }
    @Test fun incompleteOrWrongTextDoesNotDismiss() {
        assertFalse(TypingPhrases.matches("", "The blades met."))
        assertFalse(TypingPhrases.matches("The blades", "The blades met."))
        assertFalse(TypingPhrases.matches("The blades melt.", "The blades met."))
        assertFalse(TypingPhrases.matches("Thebladesmet", "The blades met."))
        assertFalse(TypingPhrases.matches("", "..."))
    }
}
