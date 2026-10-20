package com.example.tvim

import java.text.Normalizer
import java.util.Locale
import kotlin.random.Random

data class BookPhrase(val id: String, val russian: String, val english: String = "") {
    fun text(language: String): String = if (language == "ru" || english.isBlank()) russian else english
}

/** Short excerpts from the user's supplied FB2. English strings are our translations. */
object TypingPhrases {
    private val originalPhrases = listOf(
        BookPhrase("got-01", "Сир Уэймар Ройс поглядел на небо без всякого интереса.", "Ser Waymar Royce looked at the sky without any interest."),
        BookPhrase("got-02", "Сир Уэймар Ройс не потрудился ответить.", "Ser Waymar Royce did not bother to answer."),
        BookPhrase("got-03", "Ройс принял это за подтверждение и отвернулся.", "Royce took this as confirmation and turned away."),
        BookPhrase("got-04", "Сир Уэймар глядел на него с открытым неодобрением.", "Ser Waymar looked at him with open disapproval."),
        BookPhrase("got-05", "Уилл услышал неуверенность в его голосе и замер, прислушиваясь и наблюдая.", "Will heard uncertainty in his voice and froze, listening and watching."),
        BookPhrase("got-06", "Бледный меч холодом пронзил воздух.", "The pale sword pierced the air with cold."),
        BookPhrase("got-07", "К сиру Уэймару вновь вернулась ярость.", "Ser Waymar was overcome by fury once more."),
        BookPhrase("got-08", "Клинки соприкоснулись, и сталь разбилась.", "The blades met, and the steel shattered."),
        BookPhrase("got-09", "Бастард Джон Сноу подвинулся ближе к Брану.", "The bastard Jon Snow moved closer to Bran."),
        BookPhrase("got-10", "Бран удержал пони на месте и не отвернулся.", "Bran kept the pony still and did not turn away."),
        BookPhrase("got-11", "Робб явно не был заинтересован в разговоре.", "Robb was clearly not interested in the conversation."),
        BookPhrase("got-12", "Отец отбросил рог в сторону и вытер руки.", "His father tossed the antler aside and wiped his hands."),
        BookPhrase("got-13", "Кроха прижалась к нему, словно могла услышать и понять слова.", "The little creature nestled against him as if it could hear and understand the words."),
        BookPhrase("got-14", "Лорд Старк внимательно поглядел на своих сыновей.", "Lord Stark looked carefully at his sons."),
        BookPhrase("got-15", "Джон Сноу одарил воспитанника своего отца долгим холодным взглядом.", "Jon Snow gave his father's ward a long, cold look."),
        BookPhrase("got-16", "Здешняя богороща никогда не нравилась Кэтлин.", "Catelyn had never liked the godswood here.")
    )

    @Volatile private var bookPhrases: List<BookPhrase> = emptyList()
    val phrases: List<BookPhrase> get() = originalPhrases + bookPhrases
    val hasBook: Boolean get() = bookPhrases.isNotEmpty()

    /** Load short Russian passages from the user-provided book bundled with the app. */
    @Synchronized
    fun loadBook(lines: List<String>) {
        if (hasBook) return
        bookPhrases = lines.mapNotNull { line ->
            val split = line.indexOf('\t')
            if (split <= 0 || split == line.lastIndex) return@mapNotNull null
            val id = line.substring(0, split).trim()
            val sentence = line.substring(split + 1).trim()
            if (id.isBlank() || sentence.isBlank()) null else BookPhrase(id, sentence)
        }.distinctBy { it.id }
    }

    fun choose(previousId: String? = null, random: Random = Random.Default, language: String = "en"): BookPhrase {
        val available = if (language == "ru" && bookPhrases.isNotEmpty()) bookPhrases else originalPhrases
        return available.filter { it.id != previousId }.random(random)
    }

    fun byId(id: String?): BookPhrase? = id?.let { key ->
        bookPhrases.find { it.id == key } ?: originalPhrases.find { it.id == key }
    }

    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)
        .lowercase(Locale.ROOT).replace('ё', 'е')
        .replace(Regex("[\\p{P}\\p{Z}\\s]+"), " ").trim()

    fun matches(typed: String, target: String): Boolean =
        normalize(target).isNotEmpty() && normalize(typed) == normalize(target)
}
