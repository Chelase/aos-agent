package com.aos.agent.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLocaleControllerTest {

    private class FakeLocaleStore(private var tag: String? = null) : LocaleStore {
        var applyCount = 0
            private set

        override fun currentTag(): String? = tag

        override fun apply(tag: String) {
            this.tag = tag
            applyCount++
        }
    }

    @Test
    fun ensureDefault_writesChineseWhenNothingSet() {
        val store = FakeLocaleStore(tag = null)
        val controller = AppLocaleController(store)

        val language = controller.ensureDefault()

        assertEquals(AppLanguage.CHINESE, language)
        assertEquals("zh-CN", store.currentTag())
        assertEquals(1, store.applyCount)
    }

    @Test
    fun ensureDefault_keepsExistingUserChoice() {
        val store = FakeLocaleStore(tag = "en")
        val controller = AppLocaleController(store)

        val language = controller.ensureDefault()

        assertEquals(AppLanguage.ENGLISH, language)
        assertEquals(0, store.applyCount)
    }

    @Test
    fun toggle_switchesBetweenChineseAndEnglish() {
        val store = FakeLocaleStore(tag = "zh-CN")
        val controller = AppLocaleController(store)

        assertEquals(AppLanguage.ENGLISH, controller.toggle())
        assertEquals("en", store.currentTag())

        assertEquals(AppLanguage.CHINESE, controller.toggle())
        assertEquals("zh-CN", store.currentTag())
    }

    @Test
    fun current_fallsBackToChineseForUnsupportedTag() {
        val controller = AppLocaleController(FakeLocaleStore(tag = "ja-JP"))

        assertEquals(AppLanguage.CHINESE, controller.current())
    }

    @Test
    fun fromTag_matchesOnPrimarySubtagOnly() {
        assertEquals(AppLanguage.CHINESE, AppLanguage.fromTag("zh-Hans-CN"))
        assertEquals(AppLanguage.CHINESE, AppLanguage.fromTag("zh"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en-US"))
        assertNull(AppLanguage.fromTag(""))
        assertNull(AppLanguage.fromTag(null))
    }

    @Test
    fun switchTo_appliesRequestedLanguage() {
        val store = FakeLocaleStore(tag = "zh-CN")
        val controller = AppLocaleController(store)

        controller.switchTo(AppLanguage.ENGLISH)

        assertEquals("en", store.currentTag())
        assertEquals(AppLanguage.ENGLISH, controller.current())
    }
}
