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
class StopwatchStoreTest {
    private lateinit var context: Context
    private lateinit var store: StopwatchStore
    private var now = 1000L
    private var boot = 1
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("stopwatch", Context.MODE_PRIVATE).edit().clear().commit()
        store = StopwatchStore(context, { now }, { boot })
    }
    @Test fun recreationAndRepeatedStartPreserveRunningTime() {
        store.start(); now += 12345; store.start()
        assertEquals(12345L, StopwatchStore(context, { now }, { boot }).snapshot().elapsed)
        assertTrue(store.snapshot().running)
    }
    @Test fun pauseExcludesPausedTimeAndResetClearsLaps() {
        store.start(); now += 1234; store.lap(); store.pause(); now += 9000
        assertEquals(1234L, store.snapshot().elapsed)
        store.start(); now += 500
        assertEquals(1734L, store.snapshot().elapsed)
        store.reset()
        assertEquals(1734L, store.snapshot().elapsed)
        store.pause(); store.reset()
        assertEquals(0L, store.snapshot().elapsed)
        assertTrue(store.snapshot().laps.isEmpty())
    }
    @Test fun lapsAreCumulativeAndCannotBeAddedWhilePaused() {
        store.start(); now += 1000; store.lap(); store.lap(); now += 2500; store.lap()
        store.pause(); now += 100; store.lap()
        assertEquals(listOf(1000L, 3500L), store.snapshot().laps)
    }
    @Test fun rebootPausesAtLastCheckpointWithoutLosingLastLap() {
        store.start(); now += 1500; store.snapshot(); now += 200; store.lap()
        boot++; now = 100
        val restored = store.snapshot()
        assertFalse(restored.running)
        assertEquals(1700L, restored.elapsed)
        assertEquals(listOf(1700L), restored.laps)
        store.start(); now += 300
        assertEquals(2000L, store.snapshot().elapsed)
    }
    @Test fun lapLimitDoesNotStopTiming() {
        store.start()
        repeat(505) { now += 20; store.lap() }
        assertEquals(500, store.snapshot().laps.size)
        assertEquals(10100L, store.snapshot().elapsed)
    }
}
