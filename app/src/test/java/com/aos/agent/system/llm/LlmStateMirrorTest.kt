package com.aos.agent.system.llm

import com.aos.agent.core.llm.LlmConfig
import com.aos.agent.core.llm.LlmProfile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LlmStateMirrorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun writesSourceAbleStateWithoutPlaintextKey() {
        val dir = tmp.newFolder("llm")
        LlmStateMirror.write(
            dir = dir,
            active = LlmConfig("https://api.example.com/v1", "gpt-x", "sk-secret-123"),
            activeProfile = "work",
            profiles = listOf(LlmProfile("work", "https://api.example.com/v1", "gpt-x", "sk-secret-123")),
        )
        val content = File(dir, LlmStateMirror.STATE_FILE_NAME).readText()
        assertTrue(content.contains("AOS_LLM_URL='https://api.example.com/v1'"))
        assertTrue(content.contains("AOS_LLM_MODEL='gpt-x'"))
        assertTrue(content.contains("AOS_LLM_KEY='set(len=13)'"))
        assertTrue(content.contains("AOS_LLM_ACTIVE_PROFILE='work'"))
        assertTrue(content.contains("AOS_LLM_PROFILE_work_MODEL='gpt-x'"))
        assertFalse(content.contains("sk-secret-123"))
    }

    @Test
    fun emptyStateIsStillSourceAble() {
        val dir = tmp.newFolder("llm2")
        LlmStateMirror.write(dir, active = null, activeProfile = null, profiles = emptyList())
        val content = File(dir, LlmStateMirror.STATE_FILE_NAME).readText()
        assertTrue(content.contains("AOS_LLM_URL=''"))
        assertTrue(content.contains("AOS_LLM_KEY='-'"))
        assertTrue(content.contains("AOS_LLM_PROFILES=''"))
    }

    @Test
    fun valuesWithSingleQuotesStayShellSafe() {
        val dir = tmp.newFolder("llm3")
        LlmStateMirror.write(
            dir = dir,
            active = LlmConfig("https://a/v1", "mod'el", ""),
            activeProfile = null,
            profiles = emptyList(),
        )
        val content = File(dir, LlmStateMirror.STATE_FILE_NAME).readText()
        assertTrue(content.contains("AOS_LLM_MODEL='mod'\\''el'"))
        // 转义后按 sh 语法 source 不报错（Git Bash sh 与 Android sh 同为 POSIX 家族）
        val shell = ProcessBuilder("sh", "-c", ". '${File(dir, LlmStateMirror.STATE_FILE_NAME).absolutePath}' && echo \$AOS_LLM_MODEL")
            .start()
        assertEquals("mod'el", shell.inputStream.bufferedReader().readText().trim())
    }

    @Test
    fun overwriteReplacesContentAtomically() {
        val dir = tmp.newFolder("llm4")
        LlmStateMirror.write(dir, active = LlmConfig("https://a/v1", "m1", ""), activeProfile = null, profiles = emptyList())
        LlmStateMirror.write(dir, active = LlmConfig("https://b/v1", "m2", ""), activeProfile = null, profiles = emptyList())
        val content = File(dir, LlmStateMirror.STATE_FILE_NAME).readText()
        assertTrue(content.contains("AOS_LLM_URL='https://b/v1'"))
        assertFalse(content.contains("https://a/v1"))
        assertFalse(File(dir, "state.sh.tmp").exists())
    }
}
