package com.aos.agent.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmProfilesTest {

    private val empty = LlmCliState(profiles = emptyList(), active = null)

    private fun setActive(
        url: String = "https://api.example.com/v1",
        model: String = "gpt-x",
        key: String = "sk-1",
        saveAs: String? = null,
    ) = LlmCommand.SetActive(baseUrl = url, model = model, apiKey = key, saveAs = saveAs)

    // ---- 名称校验 ----

    @Test
    fun nameAllowsLowercaseStartAndDash() {
        assertTrue(LlmProfiles.validName("a"))
        assertTrue(LlmProfiles.validName("openrouter-2_local"))
        assertTrue(LlmProfiles.validName("1bad"))
        assertFalse(LlmProfiles.validName(""))
        assertFalse(LlmProfiles.validName("Upper"))
        assertFalse(LlmProfiles.validName("-bad"))
        assertFalse(LlmProfiles.validName("has space"))
        assertFalse(LlmProfiles.validName("a".repeat(33)))
    }

    // ---- set ----

    @Test
    fun setActivatesConfig() {
        val outcome = LlmProfiles.apply(empty, setActive())
        assertTrue(outcome is LlmCliOutcome.Ok)
        val next = (outcome as LlmCliOutcome.Ok).newState
        assertEquals("gpt-x", next.active?.model)
        assertEquals("https://api.example.com/v1", next.active?.baseUrl)
        assertNull(next.activeProfile)
        assertTrue(outcome.message.contains("gpt-x"))
    }

    @Test
    fun setWithoutUrlOrModelIsRejected() {
        val outcome = LlmProfiles.apply(empty, setActive(url = " "))
        assertTrue(outcome is LlmCliOutcome.Err)
        val outcome2 = LlmProfiles.apply(empty, setActive(model = ""))
        assertTrue(outcome2 is LlmCliOutcome.Err)
    }

    @Test
    fun setWithSaveAsAlsoUpsertsProfile() {
        val outcome = LlmProfiles.apply(empty, setActive(saveAs = "work"))
        val next = (outcome as LlmCliOutcome.Ok).newState
        assertEquals(listOf("work"), next.profiles.map { it.name })
        assertEquals("work", next.activeProfile)
    }

    @Test
    fun setWithIllegalSaveAsNameIsRejected() {
        val outcome = LlmProfiles.apply(empty, setActive(saveAs = "Bad Name"))
        assertTrue(outcome is LlmCliOutcome.Err)
        assertTrue((outcome as LlmCliOutcome.Err).message.contains("档案名不合法"))
    }

    @Test
    fun setWithKeepKeyCarriesOverExistingKey() {
        val first = (LlmProfiles.apply(empty, setActive()) as LlmCliOutcome.Ok).newState
        val outcome = LlmProfiles.apply(
            first,
            LlmCommand.SetActive(baseUrl = "https://other.example/v1", model = "m2", apiKey = "", keepKey = true),
        ) as LlmCliOutcome.Ok
        assertEquals("sk-1", outcome.newState.active?.apiKey)
    }

    @Test
    fun setWithKeepFillsMissingFieldsFromActive() {
        val first = (LlmProfiles.apply(empty, setActive()) as LlmCliOutcome.Ok).newState
        // 只换模型：URL 和 Key 沿用当前配置
        val outcome = LlmProfiles.apply(
            first,
            LlmCommand.SetActive(baseUrl = "", model = "m2", apiKey = "", keepKey = true),
        ) as LlmCliOutcome.Ok
        assertEquals("https://api.example.com/v1", outcome.newState.active?.baseUrl)
        assertEquals("m2", outcome.newState.active?.model)
        assertEquals("sk-1", outcome.newState.active?.apiKey)
    }

    @Test
    fun setWithKeepButNothingActiveStillRequiresFields() {
        val outcome = LlmProfiles.apply(
            empty,
            LlmCommand.SetActive(baseUrl = "", model = "m2", apiKey = "", keepKey = true),
        )
        assertTrue(outcome is LlmCliOutcome.Err)
        assertTrue(outcome.message.contains("必须显式给出"))
    }

    @Test
    fun setWithoutKeepStillRequiresBothFields() {
        val first = (LlmProfiles.apply(empty, setActive()) as LlmCliOutcome.Ok).newState
        val outcome = LlmProfiles.apply(
            first,
            LlmCommand.SetActive(baseUrl = "", model = "m2", apiKey = "k"),
        )
        assertTrue(outcome is LlmCliOutcome.Err)
    }

    // ---- add / use / rm ----

    @Test
    fun addSavesProfileWithoutTouchingActive() {
        val outcome = LlmProfiles.apply(empty, LlmCommand.AddProfile("a", "https://a/v1", "m-a", "k-a"))
        val next = (outcome as LlmCliOutcome.Ok).newState
        assertEquals(listOf("a"), next.profiles.map { it.name })
        assertNull(next.active)
        assertTrue(outcome.message.contains("未变"))
    }

    @Test
    fun addTwiceReplacesInPlace() {
        val s1 = (LlmProfiles.apply(empty, LlmCommand.AddProfile("a", "https://a1/v1", "m1")) as LlmCliOutcome.Ok).newState
        val s2 = (LlmProfiles.apply(s1, LlmCommand.AddProfile("b", "https://b/v1", "m2")) as LlmCliOutcome.Ok).newState
        val s3 = (LlmProfiles.apply(s2, LlmCommand.AddProfile("a", "https://a2/v1", "m3")) as LlmCliOutcome.Ok).newState
        assertEquals(listOf("a", "b"), s3.profiles.map { it.name })
        assertEquals("https://a2/v1", s3.profiles.first { it.name == "a" }.baseUrl)
    }

    @Test
    fun useCopiesProfileIntoActive() {
        val s1 = (LlmProfiles.apply(empty, LlmCommand.AddProfile("a", "https://a/v1", "m-a", "k-a")) as LlmCliOutcome.Ok).newState
        val outcome = LlmProfiles.apply(s1, LlmCommand.UseProfile("a")) as LlmCliOutcome.Ok
        assertEquals("k-a", outcome.newState.active?.apiKey)
        assertEquals("a", outcome.newState.activeProfile)
    }

    @Test
    fun useMissingProfileGivesReason() {
        val outcome = LlmProfiles.apply(empty, LlmCommand.UseProfile("nope"))
        assertTrue(outcome is LlmCliOutcome.Err)
        assertTrue(outcome.message.contains("没有档案"))
    }

    @Test
    fun removeKeepsActiveConfigButDropsHint() {
        val s1 = (LlmProfiles.apply(empty, LlmCommand.AddProfile("a", "https://a/v1", "m-a")) as LlmCliOutcome.Ok).newState
        val s2 = (LlmProfiles.apply(s1, LlmCommand.UseProfile("a")) as LlmCliOutcome.Ok).newState
        val outcome = LlmProfiles.apply(s2, LlmCommand.RemoveProfile("a")) as LlmCliOutcome.Ok
        assertEquals("m-a", outcome.newState.active?.model)
        assertNull(outcome.newState.activeProfile)
        assertEquals(emptyList<String>(), outcome.newState.profiles.map { it.name })
    }

    @Test
    fun removeMissingProfileGivesReason() {
        val outcome = LlmProfiles.apply(empty, LlmCommand.RemoveProfile("nope"))
        assertTrue(outcome is LlmCliOutcome.Err)
    }

    // ---- 编解码 ----

    @Test
    fun encodeDecodeRoundTrip() {
        val profiles = listOf(
            LlmProfile("a", "https://a/v1", "m-a", "k-a"),
            LlmProfile("b-2", "https://b/v1", "m-b"),
        )
        assertEquals(profiles, LlmProfiles.decode(LlmProfiles.encode(profiles)))
    }

    @Test
    fun decodeBadInputYieldsEmptyNotThrow() {
        assertEquals(emptyList<LlmProfile>(), LlmProfiles.decode(null))
        assertEquals(emptyList<LlmProfile>(), LlmProfiles.decode(""))
        assertEquals(emptyList<LlmProfile>(), LlmProfiles.decode("not json"))
        assertEquals(emptyList<LlmProfile>(), LlmProfiles.decode("""[{"name":""}]"""))
    }

    // ---- 广播解析 ----

    @Test
    fun parserMapsActionsToCommands() {
        val cmd = LlmCliParser.parse(
            LlmCliParser.ACTION_SET_ACTIVE,
            name = "work",
            url = "https://a/v1",
            model = "m",
            key = "k",
            keepKey = true,
        )
        assertEquals(LlmCommand.SetActive("https://a/v1", "m", "k", "work", true), cmd)

        assertEquals(LlmCommand.UseProfile("a"), LlmCliParser.parse(LlmCliParser.ACTION_USE_PROFILE, "a", null, null, null, false))
        assertEquals(LlmCommand.RemoveProfile("a"), LlmCliParser.parse(LlmCliParser.ACTION_REMOVE_PROFILE, "a", null, null, null, false))
        assertEquals(
            LlmCommand.AddProfile("a", "https://a/v1", "m", "k"),
            LlmCliParser.parse(LlmCliParser.ACTION_ADD_PROFILE, "a", "https://a/v1", "m", "k", false),
        )
    }

    @Test
    fun parserRejectsUnknownOrIncomplete() {
        assertNull(LlmCliParser.parse("com.aos.agent.llm.WHAT", "a", null, null, null, false))
        assertNull(LlmCliParser.parse(LlmCliParser.ACTION_USE_PROFILE, null, null, null, null, false))
        assertNull(LlmCliParser.parse(LlmCliParser.ACTION_USE_PROFILE, "", null, null, null, false))
        assertNull(LlmCliParser.parse(null, null, null, null, null, false))
    }
}
