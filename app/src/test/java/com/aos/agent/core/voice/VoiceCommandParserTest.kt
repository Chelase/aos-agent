package com.aos.agent.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandParserTest {

    private val vocab = VoiceCommandVocabulary(
        newSession = setOf("新开会话", "重新开始", "new session", "clear chat"),
        openSettings = setOf("打开设置", "open settings"),
        closeSettings = setOf("关闭设置", "close settings"),
        goHome = setOf("返回首页", "go home"),
        useMcp = setOf("使用MCP", "use mcp"),
        useSkillPrefixes = setOf("使用技能", "用技能", "use skill"),
    )

    @Test
    fun parsesChineseFixedCommands() {
        assertEquals(VoiceCommand.NewSession, VoiceCommandParser.parse("新开会话", vocab))
        assertEquals(VoiceCommand.OpenSettings, VoiceCommandParser.parse("打开设置", vocab))
        assertEquals(VoiceCommand.GoHome, VoiceCommandParser.parse("返回首页", vocab))
        assertEquals(VoiceCommand.UseMcp, VoiceCommandParser.parse("使用MCP", vocab))
    }

    @Test
    fun parsesEnglishCommandsIgnoringCase() {
        assertEquals(VoiceCommand.NewSession, VoiceCommandParser.parse("New Session", vocab))
        assertEquals(VoiceCommand.GoHome, VoiceCommandParser.parse("GO HOME", vocab))
    }

    @Test
    fun ignoresPunctuationAndSpaces() {
        assertEquals(VoiceCommand.NewSession, VoiceCommandParser.parse("新开会话。", vocab))
        assertEquals(VoiceCommand.NewSession, VoiceCommandParser.parse("  新开会话！", vocab))
        assertEquals(VoiceCommand.OpenSettings, VoiceCommandParser.parse("open  settings?", vocab))
    }

    @Test
    fun doesNotMatchCommandsEmbeddedInLongerSentences() {
        // 宁可漏判也不误判：整句全等才算命令
        assertNull(VoiceCommandParser.parse("帮我新开会话吧", vocab))
        assertNull(VoiceCommandParser.parse("打开设置然后改模型", vocab))
    }

    @Test
    fun parsesSkillCommandWithNamedSkill() {
        assertEquals(
            VoiceCommand.UseSkill("车辆状态"),
            VoiceCommandParser.parse("使用技能车辆状态", vocab),
        )
        assertEquals(
            VoiceCommand.UseSkill("vehicle report"),
            VoiceCommandParser.parse("use skill vehicle report", vocab),
        )
    }

    @Test
    fun skillCommandWithoutNameIsNotACommand() {
        assertNull(VoiceCommandParser.parse("使用技能", vocab))
        assertNull(VoiceCommandParser.parse("用技能 ", vocab))
    }

    @Test
    fun skillCommandKeepsOriginalNameCase() {
        assertEquals(
            VoiceCommand.UseSkill("VehicleReport"),
            VoiceCommandParser.parse("使用技能 VehicleReport", vocab),
        )
    }

    @Test
    fun emptyOrUnknownTextIsNotACommand() {
        assertNull(VoiceCommandParser.parse("", vocab))
        assertNull(VoiceCommandParser.parse("   ", vocab))
        assertNull(VoiceCommandParser.parse("今天天气怎么样", vocab))
        assertNull(VoiceCommandParser.parse("。。。", vocab))
    }

    @Test
    fun normalizeKeepsLettersAndDigitsOnly() {
        assertEquals("新开会话", VoiceCommandParser.normalize("新开会话。"))
        assertEquals("abc123", VoiceCommandParser.normalize(" ABC 1-2-3! "))
    }

    @Test
    fun emptyVocabularyMatchesNothing() {
        val empty = VoiceCommandVocabulary()
        assertNull(VoiceCommandParser.parse("新开会话", empty))
        assertNull(VoiceCommandParser.parse("使用技能 X", empty))
    }
}
