package com.example.tvim

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class LibrarySound(val id: String, val title: String)

/** Private copies remain playable when the original document is moved or removed. */
class SoundLibrary(private val context: Context) {
    private val prefs = context.getSharedPreferences("sound_library", Context.MODE_PRIVATE)
    private val directory get() = File(context.filesDir, "sounds").apply { mkdirs() }

    fun entries(): List<LibrarySound> = runCatching {
        val array = JSONArray(prefs.getString("entries", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            LibrarySound(item.getString("id"), item.getString("title"))
        }.distinctBy { it.id }
    }.getOrDefault(emptyList())

    @Synchronized
    fun rememberLegacySounds() {
        val saved = entries().toMutableList()
        AlarmStorage(context).getAlarms().map { it.ringtoneName }.distinct()
            .filter { it.startsWith("content://") && saved.none { entry -> entry.id == it } }
            .forEach { id -> saved.add(LibrarySound(id,
                runCatching { getFileName(context, Uri.parse(id)) }.getOrNull() ?: "Audio")) }
        save(saved)
    }

    private fun save(entries: List<LibrarySound>) {
        val array = JSONArray()
        entries.forEach { array.put(JSONObject().put("id", it.id).put("title", it.title)) }
        check(prefs.edit().putString("entries", array.toString()).commit())
    }

    fun file(id: String): File? {
        val hash = id.removePrefix("library:")
        return if (id.startsWith("library:") && hash.matches(Regex("[a-f0-9]{64}"))) File(directory, "$hash.audio") else null
    }

    fun title(id: String): String? = entries().find { it.id == id }?.title

    @Synchronized
    fun importSound(uri: Uri, validate: (File) -> Unit = ::validateAudio): LibrarySound {
        val temporary = File.createTempFile("import-", ".part", directory)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input)
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        size += read
                        require(size <= 50L * 1024 * 1024) { "Audio is larger than 50 MB" }
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                    }
                }
            }
            require(size > 0) { "Empty audio" }
            validate(temporary)
            val id = "library:" + digest.digest().joinToString("") { "%02x".format(it) }
            val existing = entries().find { it.id == id }
            val entry = existing ?: LibrarySound(id,
                (runCatching { getFileName(context, uri) }.getOrNull() ?: "Audio").replace('\n', ' ').take(120))
            val destination = requireNotNull(file(id))
            if (!destination.exists()) check(temporary.renameTo(destination)) { "Cannot save audio" }
            if (existing == null) save(entries() + entry)
            return entry
        } finally { temporary.delete() }
    }

    private fun validateAudio(file: File) {
        val player = MediaPlayer()
        try { player.setDataSource(file.absolutePath); player.prepare() }
        finally { player.release() }
    }
}
