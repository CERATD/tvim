package com.example.tvim

import android.content.Context
import android.net.Uri
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33])
class SoundLibraryTest {
    private lateinit var context: Context
    private lateinit var library: SoundLibrary
    private lateinit var source: File
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("sound_library", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("alarms_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        library = SoundLibrary(context)
        source = File(context.cacheDir, "my-song.mp3").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
    }
    @Test fun importedCopyRemainsAvailableForAnotherAlarmAfterOriginalDeletion() {
        val sound = library.importSound(Uri.fromFile(source)) {}
        source.delete()
        val reloaded = SoundLibrary(context)
        assertEquals(sound, reloaded.entries().single())
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), reloaded.file(sound.id)!!.readBytes())
        assertEquals("my-song.mp3", AlarmSounds.title(context, sound.id))
        assertEquals(Uri.fromFile(reloaded.file(sound.id)), AlarmSounds.uri(context, sound.id))
        val storage = AlarmStorage(context)
        storage.saveAlarms(listOf(Alarm(hour = 7, minute = 0, ringtoneName = sound.id), Alarm(hour = 8, minute = 0, ringtoneName = sound.id)))
        assertTrue(storage.getAlarms().all { it.ringtoneName == sound.id })
    }
    @Test fun duplicateContentsAreNotAddedTwice() {
        val first = library.importSound(Uri.fromFile(source)) {}
        val other = File(context.cacheDir, "renamed.mp3").apply { writeBytes(source.readBytes()) }
        val second = library.importSound(Uri.fromFile(other)) {}
        assertEquals(first.id, second.id)
        assertEquals(1, library.entries().size)
    }
    @Test fun failedValidationDoesNotSaveEntryOrLeaveTemporaryFile() {
        assertThrows(IllegalArgumentException::class.java) {
            library.importSound(Uri.fromFile(source)) { throw IllegalArgumentException("Invalid audio") }
        }
        assertTrue(library.entries().isEmpty())
        assertFalse(File(context.filesDir, "sounds").listFiles().orEmpty().any { it.extension == "part" })
    }
    @Test fun existingAlarmImportsAreSharedAndPersistAfterAlarmDeletion() {
        val storage = AlarmStorage(context)
        storage.saveAlarms(listOf(Alarm(hour = 7, minute = 0, ringtoneName = "content://music/custom"),
            Alarm(hour = 8, minute = 0, ringtoneName = "content://music/custom")))
        library.rememberLegacySounds(); library.rememberLegacySounds()
        assertEquals("content://music/custom", library.entries().single().id)
        storage.saveAlarms(emptyList())
        assertEquals(1, SoundLibrary(context).entries().size)
    }
    @Test fun invalidLibraryIdsCannotEscapePrivateDirectory() {
        assertNull(library.file("library:../../outside"))
        assertNull(library.file("file:///outside"))
    }
}
