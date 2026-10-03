package com.aos.agent.core.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 唤醒判定纯函数测试。
 *
 * 只测判定，不测 Vosk：原生库与音频在 JVM 上跑不了，能跑的部分（JSON 解析与命中规则）
 * 全部收在这几个用例里。
 */
class WakeWordMatcherTest {

    private val keyword = "你好副驾"

    @Test
    fun exactKeywordWakes() {
        assertTrue(WakeWordMatcher.isWake("你好副驾", keyword))
    }

    @Test
    fun keywordWithSpacesOrNewlinesStillWakes() {
        assertTrue(WakeWordMatcher.isWake("你 好\n副驾 ", keyword))
    }

    @Test
    fun keywordEmbeddedInLongerUtteranceWakes() {
        assertTrue(WakeWordMatcher.isWake("嗯那个你好副驾帮我看看", keyword))
    }

    @Test
    fun unknownTokenDoesNotWake() {
        assertFalse(WakeWordMatcher.isWake("[unk]", keyword))
    }

    @Test
    fun unrelatedSpeechDoesNotWake() {
        assertFalse(WakeWordMatcher.isWake("今天天气不错", keyword))
    }

    @Test
    fun emptyAndBlankDoNotWake() {
        assertFalse(WakeWordMatcher.isWake("", keyword))
        assertFalse(WakeWordMatcher.isWake("   ", keyword))
    }

    @Test
    fun emptyKeywordNeverWakes() {
        // 词表资源缺失时不能变成"什么都算命中"
        assertFalse(WakeWordMatcher.isWake("随便什么", ""))
    }

    @Test
    fun latinKeywordIsCaseInsensitive() {
        assertTrue(WakeWordMatcher.isWake("Hello Car", "hello car"))
    }

    @Test
    fun partialKeywordDoesNotWake() {
        assertFalse(WakeWordMatcher.isWake("你好", keyword))
    }

    @Test
    fun unrecognizedCoversBlankAndUnknownToken() {
        assertTrue(WakeWordMatcher.isUnrecognized(""))
        assertTrue(WakeWordMatcher.isUnrecognized(" [unk] "))
        assertFalse(WakeWordMatcher.isUnrecognized(keyword))
    }

    @Test
    fun textOfReadsTextField() {
        assertEquals("你好副驾", WakeWordMatcher.textOf("""{"text":"你好副驾"}"""))
    }

    @Test
    fun textOfToleratesMalformedAndMissingShapes() {
        assertEquals("", WakeWordMatcher.textOf("not json at all"))
        assertEquals("", WakeWordMatcher.textOf(""))
        assertEquals("", WakeWordMatcher.textOf("""{"partial":"你好"}"""))
        assertEquals("", WakeWordMatcher.textOf("[]"))
    }
}
