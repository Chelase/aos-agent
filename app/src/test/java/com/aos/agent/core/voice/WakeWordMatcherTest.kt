package com.aos.agent.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 唤醒说法生成与判定的纯函数测试。
 *
 * 只测判定与语法，不测 Vosk：原生库与音频在 JVM 上跑不了，能跑的部分（名字→说法→语法、
 * JSON 解析、命中规则）全部收在这几个用例里。
 */
class WakeWordMatcherTest {

    private val helloChelsea = listOf("你好Chelsea")

    // ---- 说法生成 ----

    @Test
    fun buildProducesPhrasesInFixedOrder() {
        val phrases = WakeWordPhrases.build(
            "Chelsea",
            setOf(WakeVariant.BARE, WakeVariant.HELLO, WakeVariant.HI),
        )
        assertEquals(listOf("你好Chelsea", "Hi Chelsea", "Chelsea"), phrases)
    }

    @Test
    fun buildDefaultsToHelloOnlyWhenNothingSelected() {
        val phrases = WakeWordPhrases.build("副驾", setOf(WakeVariant.HELLO))
        assertEquals(listOf("你好副驾"), phrases)
    }

    @Test
    fun blankNameProducesNoPhrases() {
        // 名字为空时不能生成"你好"这种半截唤醒词
        assertTrue(WakeWordPhrases.build("   ", WakeVariant.entries.toSet()).isEmpty())
    }

    @Test
    fun buildTrimsNameAndDeduplicates() {
        val phrases = WakeWordPhrases.build("  Chelsea  ", setOf(WakeVariant.HELLO, WakeVariant.BARE))
        assertEquals(listOf("你好Chelsea", "Chelsea"), phrases)
    }

    @Test
    fun grammarIsRestrictedToSelectedPhrasesPlusUnknown() {
        assertEquals(
            """["你好Chelsea", "Chelsea", "[unk]"]""",
            WakeWordPhrases.grammarOf(listOf("你好Chelsea", "Chelsea")),
        )
    }

    @Test
    fun grammarSurvivesQuotesInUserSuppliedName() {
        // 用户能输入任何字符：引号不处理会把整条语法打烂，识别器构造直接失败
        val grammar = WakeWordPhrases.grammarOf(listOf("He\"llo\\"))
        assertFalse(grammar.contains("\\\""))
        assertEquals("""["Hello", "[unk]"]""", grammar)
    }

    @Test
    fun emptyPhrasesStillProduceValidGrammar() {
        assertEquals("""["[unk]"]""", WakeWordPhrases.grammarOf(emptyList()))
    }

    @Test
    fun displayJoinsPhrasesForUiAndNotification() {
        assertEquals("你好Chelsea / Hi Chelsea", WakeWordPhrases.displayOf(listOf("你好Chelsea", "Hi Chelsea")))
    }

    // ---- 命中判定 ----

    @Test
    fun exactPhraseWakes() {
        assertTrue(WakeWordMatcher.isWake("你好Chelsea", helloChelsea))
    }

    @Test
    fun anySelectedPhraseWakes() {
        val phrases = WakeWordPhrases.build("Chelsea", setOf(WakeVariant.HELLO, WakeVariant.HI, WakeVariant.BARE))
        assertTrue(WakeWordMatcher.isWake("Hi Chelsea", phrases))
        assertTrue(WakeWordMatcher.isWake("Chelsea", phrases))
        // 没勾"你好X"之外的说法时，裸名不该算命中——但勾了就该
        assertFalse(WakeWordMatcher.isWake("Chelsea", helloChelsea))
    }

    @Test
    fun spacesPunctuationAndCaseDoNotBreakMatching() {
        assertTrue(WakeWordMatcher.isWake("你好，chelsea", helloChelsea))
        assertTrue(WakeWordMatcher.isWake("你 好 CHELSEA", helloChelsea))
    }

    @Test
    fun phraseEmbeddedInLongerUtteranceWakes() {
        assertTrue(WakeWordMatcher.isWake("嗯那个你好Chelsea帮我看看", helloChelsea))
    }

    @Test
    fun unknownTokenAndUnrelatedSpeechDoNotWake() {
        assertFalse(WakeWordMatcher.isWake("[unk]", helloChelsea))
        assertFalse(WakeWordMatcher.isWake("今天天气不错", helloChelsea))
        assertFalse(WakeWordMatcher.isWake("", helloChelsea))
        assertFalse(WakeWordMatcher.isWake("   ", helloChelsea))
    }

    @Test
    fun noPhrasesNeverWake() {
        // 名字被清空后 KWS 不该变成"什么都算命中"
        assertFalse(WakeWordMatcher.isWake("随便什么", emptyList()))
    }

    @Test
    fun partialPhraseDoesNotWake() {
        assertFalse(WakeWordMatcher.isWake("你好", helloChelsea))
    }

    @Test
    fun unrecognizedCoversBlankAndUnknownToken() {
        assertTrue(WakeWordMatcher.isUnrecognized(""))
        assertTrue(WakeWordMatcher.isUnrecognized(" [unk] "))
        assertFalse(WakeWordMatcher.isUnrecognized("你好Chelsea"))
    }

    @Test
    fun textOfReadsTextField() {
        assertEquals("你好Chelsea", WakeWordMatcher.textOf("""{"text":"你好Chelsea"}"""))
    }

    @Test
    fun textOfToleratesMalformedAndMissingShapes() {
        assertEquals("", WakeWordMatcher.textOf("not json at all"))
        assertEquals("", WakeWordMatcher.textOf(""))
        assertEquals("", WakeWordMatcher.textOf("""{"partial":"你好"}"""))
        assertEquals("", WakeWordMatcher.textOf("[]"))
    }
}
