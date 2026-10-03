package com.aos.agent.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 唤醒观测缓冲测试。
 *
 * 这块是"真车上喊五次醒几次"的测量工具，测的是它自己别骗人：
 * 关着不记、超上限丢最旧、命中与未命中分开算、离开页面必须清场。
 */
class WakeDebugTest {

    @Before
    fun setUp() {
        WakeDebug.close()
    }

    @Test
    fun recordsNothingWhileClosed() {
        WakeDebug.record("你好Chelsea", true, 1_000L)
        assertTrue(WakeDebug.state.value.observations.isEmpty())
        assertEquals(0, WakeDebug.state.value.hits)
        assertEquals(0, WakeDebug.state.value.misses)
    }

    @Test
    fun countsHitsAndMissesSeparately() {
        WakeDebug.open()
        WakeDebug.record("你好Chelsea", true, 1_000L)
        WakeDebug.record("[unk]", false, 2_000L)
        WakeDebug.record("Chelsea", true, 3_000L)
        val state = WakeDebug.state.value
        assertEquals(2, state.hits)
        assertEquals(1, state.misses)
        assertEquals(3, state.observations.size)
    }

    @Test
    fun timestampsAreRelativeToFirstRecordedEntry() {
        WakeDebug.open()
        WakeDebug.record("你好Chelsea", true, 10_000L)
        WakeDebug.record("[unk]", false, 12_500L)
        val entries = WakeDebug.state.value.observations
        assertEquals(0L, entries.first().sinceOpenMillis)
        assertEquals(2_500L, entries.last().sinceOpenMillis)
    }

    @Test
    fun bufferDropsOldestBeyondCap() {
        WakeDebug.open()
        repeat(WakeDebug.MAX_ENTRIES + 5) { index ->
            WakeDebug.record("你好Chelsea", true, index * 100L)
        }
        val entries = WakeDebug.state.value.observations
        assertEquals(WakeDebug.MAX_ENTRIES, entries.size)
        // 保留的是最近的，不是最开始的
        assertEquals((WakeDebug.MAX_ENTRIES + 4) * 100L, entries.last().sinceOpenMillis)
        assertEquals(5L * 100L, entries.first().sinceOpenMillis)
        // 计数按全部发生算，不因截断而少
        assertEquals(WakeDebug.MAX_ENTRIES + 5, WakeDebug.state.value.hits)
    }

    @Test
    fun resetClearsEntriesAndCountersButKeepsPanelOpen() {
        WakeDebug.open()
        WakeDebug.record("你好Chelsea", true, 0L)
        WakeDebug.resetCounters()
        val state = WakeDebug.state.value
        assertTrue(state.enabled)
        assertTrue(state.observations.isEmpty())
        assertEquals(0, state.hits)
    }

    @Test
    fun closeLeavesNoResidualState() {
        WakeDebug.open()
        WakeDebug.record("你好Chelsea", true, 0L)
        WakeDebug.close()
        val state = WakeDebug.state.value
        assertTrue(!state.enabled)
        assertTrue(state.observations.isEmpty())
        assertEquals(0, state.hits)
    }

    @Test
    fun reopeningStartsClockOver() {
        WakeDebug.open()
        WakeDebug.record("你好Chelsea", true, 50_000L)
        WakeDebug.close()
        WakeDebug.open()
        WakeDebug.record("你好Chelsea", true, 90_000L)
        assertEquals(0L, WakeDebug.state.value.observations.single().sinceOpenMillis)
    }
}
