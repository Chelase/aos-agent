package com.aos.agent.ui.voice

import com.aos.agent.core.voice.SpeechSynthesizer
import com.aos.agent.core.voice.SpeechTranscriber
import com.aos.agent.core.voice.VoiceCommand
import com.aos.agent.core.voice.VoiceCommandVocabulary
import com.aos.agent.core.voice.VoiceError
import com.aos.agent.core.voice.VoiceFocusHandle
import com.aos.agent.data.store.VoiceSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 状态机整回路测试：灌假通道，不碰 Android。
 * 断言只看阶段、回调与焦点动作，不依赖具体资源 id 的语义；
 * 延迟（防自听静默、无语音超时）用虚拟时间推进，不用 sleep。
 */
class VoiceControllerTest {

    private class FakeTranscriber(override var available: Boolean = true) : SpeechTranscriber {
        var onPartial: ((String) -> Unit)? = null
        var onFinal: ((String) -> Unit)? = null
        var onError: ((VoiceError) -> Unit)? = null
        var startCount = 0
        var stopCount = 0
        var cancelCount = 0
        var releaseCount = 0

        override fun start(
            onPartial: (String) -> Unit,
            onFinal: (String) -> Unit,
            onError: (VoiceError) -> Unit,
        ) {
            startCount++
            this.onPartial = onPartial
            this.onFinal = onFinal
            this.onError = onError
        }

        override fun stop() { stopCount++ }
        override fun cancel() { cancelCount++ }
        override fun release() { releaseCount++ }
    }

    private class FakeSynthesizer(override var available: Boolean = true) : SpeechSynthesizer {
        var spoken = mutableListOf<String>()
        var stopCount = 0
        var pendingDone: (() -> Unit)? = null

        override fun speak(text: String, onDone: () -> Unit) {
            spoken += text
            pendingDone = onDone
        }

        override fun stop() {
            stopCount++
            pendingDone = null
        }

        override fun release() = Unit

        fun finishSpeaking() {
            pendingDone?.invoke()
            pendingDone = null
        }
    }

    private class FakeFocusHandle : VoiceFocusHandle {
        var claims = 0
        var releases = 0
        var granted = true
        private var onLost: (() -> Unit)? = null
        private var onGained: (() -> Unit)? = null

        override fun claim(onLost: () -> Unit, onGained: () -> Unit): Boolean {
            claims++
            this.onLost = onLost
            this.onGained = onGained
            return granted
        }

        override fun release() {
            releases++
            onLost = null
            onGained = null
        }

        fun lose() { onLost?.invoke() }
        fun regain() { onGained?.invoke() }
    }

    private val vocab = VoiceCommandVocabulary(
        newSession = setOf("新开会话"),
        openSettings = setOf("打开设置"),
        useSkillPrefixes = setOf("使用技能"),
    )

    private class Fixture(
        val controller: VoiceController,
        val transcriber: FakeTranscriber,
        val synthesizer: FakeSynthesizer,
        val focus: FakeFocusHandle,
    )

    private fun TestScope.fixture(
        transcriber: FakeTranscriber = FakeTranscriber(),
        synthesizer: FakeSynthesizer = FakeSynthesizer(),
        settings: VoiceSettings = VoiceSettings(ttsEnabled = true, continuous = false),
        granted: Boolean = true,
        focus: FakeFocusHandle = FakeFocusHandle(),
    ): Fixture {
        val controller = VoiceController(
            transcriber = transcriber,
            synthesizer = synthesizer,
            focus = focus,
            vocabulary = vocab,
            settings = MutableStateFlow(settings),
            scope = backgroundScope,
            hasPermission = { granted },
        )
        controller.refreshAvailability()
        return Fixture(controller, transcriber, synthesizer, focus)
    }

    @Test
    fun startsIdleAndUsableWhenServiceAndPermissionPresent() = runTest {
        val f = fixture()
        assertEquals(VoicePhase.IDLE, f.controller.state.value.phase)
        assertTrue(f.controller.state.value.usable)
        assertFalse(f.controller.state.value.needsPermission)
    }

    @Test
    fun toggleStartsListening() = runTest {
        val f = fixture()
        f.controller.toggle()
        assertEquals(VoicePhase.LISTENING, f.controller.state.value.phase)
        assertEquals(1, f.transcriber.startCount)
    }

    @Test
    fun toggleWhileListeningAsksRecognizerToSettle() = runTest {
        val f = fixture()
        f.controller.toggle()
        f.controller.toggle()
        assertEquals(1, f.transcriber.stopCount)
        // 结算由 onFinal 接手，阶段仍停在聆听
        assertEquals(VoicePhase.LISTENING, f.controller.state.value.phase)
    }

    @Test
    fun partialResultsSurfaceImmediately() = runTest {
        val f = fixture()
        f.controller.toggle()
        f.transcriber.onPartial!!.invoke("帮我看看")
        assertEquals("帮我看看", f.controller.state.value.partial)
    }

    @Test
    fun finalQuestionGoesToEngineAndThinkingPhase() = runTest {
        val f = fixture()
        var received: String? = null
        f.controller.onQuery = { received = it }
        f.controller.toggle()
        f.transcriber.onFinal!!.invoke("今天车里多少度")
        assertEquals(VoicePhase.THINKING, f.controller.state.value.phase)
        assertEquals("今天车里多少度", received)
        assertEquals("今天车里多少度", f.controller.state.value.lastHeard)
    }

    @Test
    fun finalCommandExecutesLocallyWithoutQuery() = runTest {
        val f = fixture()
        var query: String? = null
        var command: VoiceCommand? = null
        f.controller.onQuery = { query = it }
        f.controller.onCommand = { command = it }
        f.controller.toggle()
        f.transcriber.onFinal!!.invoke("新开会话")
        assertEquals(VoiceCommand.NewSession, command)
        assertNull(query)
        assertEquals(VoicePhase.IDLE, f.controller.state.value.phase)
    }

    @Test
    fun emptyFinalReturnsIdleWithNotice() = runTest {
        val f = fixture()
        f.controller.toggle()
        f.transcriber.onFinal!!.invoke("   ")
        assertEquals(VoicePhase.IDLE, f.controller.state.value.phase)
        assertTrue(f.controller.state.value.noticeRes != null)
    }

    @Test
    fun answerIsSpokenThenReturnsIdle() = runTest {
        val f = fixture()
        f.controller.onTurnStarted()
        f.controller.onAnswer("车内 24 摄氏度")
        assertEquals(VoicePhase.SPEAKING, f.controller.state.value.phase)
        assertEquals(listOf("车内 24 摄氏度"), f.synthesizer.spoken)
        f.synthesizer.finishSpeaking()
        assertEquals(VoicePhase.IDLE, f.controller.state.value.phase)
    }

    @Test
    fun ttsDisabledSkipsSpeakingPhase() = runTest {
        val f = fixture(settings = VoiceSettings(ttsEnabled = false))
        f.controller.onAnswer("回答")
        assertEquals(VoicePhase.IDLE, f.controller.state.value.phase)
        assertTrue(f.synthesizer.spoken.isEmpty())
    }

    @Test
    fun toggleWhileSpeakingInterruptsAndListensAgain() = runTest {
        val f = fixture()
        f.controller.onAnswer("长回答")
        assertEquals(VoicePhase.SPEAKING, f.controller.state.value.phase)
        f.controller.toggle()
        assertEquals(1, f.synthesizer.stopCount)
        assertEquals(VoicePhase.LISTENING, f.controller.state.value.phase)
        assertEquals(1, f.transcriber.startCount)
    }

    @Test
    fun noServiceRendersDisabledWithReason() = runTest {
        val f = fixture(transcriber = FakeTranscriber(available = false))
        assertFalse(f.controller.state.value.usable)
        assertTrue(f.controller.state.value.blockedReasonRes != null)
        // 禁用态下点按钮不开麦，只给提示
        f.controller.toggle()
        assertEquals(VoicePhase.IDLE, f.controller.state.value.phase)
    }

    @Test
    fun missingPermissionAsksForPermissionInsteadOfListening() = runTest {
        val f = fixture(granted = false)
        assertFalse(f.controller.state.value.usable)
        assertTrue(f.controller.state.value.needsPermission)
        assertEquals(0, f.transcriber.startCount)
    }

    @Test
    fun recognizerErrorsAllReturnToIdleWithNotice() = runTest {
        VoiceError.entries.forEach { error ->
            val f = fixture()
            f.controller.toggle()
            f.transcriber.onError!!.invoke(error)
            assertEquals("phase after $error", VoicePhase.IDLE, f.controller.state.value.phase)
            assertTrue("notice after $error", f.controller.state.value.noticeRes != null)
        }
    }

    @Test
    fun engineFailureLeavesThinkingPhase() = runTest {
        val f = fixture()
        f.controller.onTurnStarted()
        f.controller.onTurnFailed()
        assertEquals(VoicePhase.IDLE, f.controller.state.value.phase)
    }

    @Test
    fun releaseFreesBothChannels() = runTest {
        val f = fixture()
        f.controller.release()
        assertEquals(1, f.transcriber.releaseCount)
    }

    // ---- Phase B1：连续对话回路 ----

    @Test
    fun continuousLoopWaitsOutTheSilenceGapBeforeListeningAgain() = runTest {
        val f = fixture(settings = VoiceSettings(ttsEnabled = true, continuous = true))
        f.controller.toggle()
        f.transcriber.onFinal!!.invoke("第一个问题")
        f.controller.onAnswer("回答")
        f.synthesizer.finishSpeaking()
        // 播报刚结束先进静默，不立刻开麦（防把自己的余音听成指令）
        assertEquals(VoicePhase.WAITING, f.controller.state.value.phase)
        assertEquals(1, f.transcriber.startCount)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(VoicePhase.LISTENING, f.controller.state.value.phase)
        assertEquals(2, f.transcriber.startCount)
    }

    @Test
    fun noSpeechWithinTheTimeoutEndsTheSession() = runTest {
        val f = fixture()
        f.controller.toggle()
        advanceTimeBy(8_000)
        runCurrent()
        assertEquals(VoicePhase.IDLE, f.controller.state.value.phase)
        assertTrue(f.controller.state.value.noticeRes != null)
        assertEquals(1, f.transcriber.cancelCount)
        // 回 IDLE 必须交还焦点，不然媒体一直被压着
        assertEquals(1, f.focus.releases)
    }

    @Test
    fun speechInProgressCancelsTheNoSpeechTimer() = runTest {
        val f = fixture()
        f.controller.toggle()
        f.transcriber.onPartial!!.invoke("我在说话")
        advanceTimeBy(8_000)
        runCurrent()
        assertEquals(VoicePhase.LISTENING, f.controller.state.value.phase)
    }

    @Test
    fun tapDuringSilenceGapEndsTheSession() = runTest {
        val f = fixture(settings = VoiceSettings(ttsEnabled = true, continuous = true))
        f.controller.onAnswer("回答")
        f.synthesizer.finishSpeaking()
        assertEquals(VoicePhase.WAITING, f.controller.state.value.phase)
        f.controller.toggle()
        assertEquals(VoicePhase.IDLE, f.controller.state.value.phase)
        advanceTimeBy(1_000)
        runCurrent()
        // 静默计时已作废：不会过一会儿又自己开麦
        assertEquals(0, f.transcriber.startCount)
        assertEquals(1, f.focus.releases)
    }

    @Test
    fun voiceInterruptsPlaybackWhenBargeInEnabled() = runTest {
        val f = fixture(
            settings = VoiceSettings(ttsEnabled = true, continuous = true, bargeInEnabled = true),
        )
        f.controller.toggle()
        f.transcriber.onFinal!!.invoke("问个问题")
        f.controller.onAnswer("很长的回答")
        assertEquals(VoicePhase.SPEAKING, f.controller.state.value.phase)
        // 播报期间多起一路探针聆听
        assertEquals(2, f.transcriber.startCount)
        f.transcriber.onPartial!!.invoke("停一下")
        assertEquals(1, f.synthesizer.stopCount)
        assertEquals(VoicePhase.LISTENING, f.controller.state.value.phase)
        assertEquals(3, f.transcriber.startCount)
    }

    @Test
    fun probeFinalResultsAreNeverSubmitted() = runTest {
        val f = fixture(
            settings = VoiceSettings(ttsEnabled = true, continuous = true, bargeInEnabled = true),
        )
        var query: String? = null
        f.controller.onQuery = { query = it }
        f.controller.onAnswer("回答")
        f.transcriber.onFinal!!.invoke("探针听到的整句")
        assertNull(query)
        assertEquals(VoicePhase.SPEAKING, f.controller.state.value.phase)
    }

    @Test
    fun bargeInDisabledStartsNoProbe() = runTest {
        val f = fixture(settings = VoiceSettings(ttsEnabled = true, continuous = true))
        f.controller.toggle()
        f.transcriber.onFinal!!.invoke("问题")
        f.controller.onAnswer("回答")
        assertEquals(VoicePhase.SPEAKING, f.controller.state.value.phase)
        // 只有一路聆听（上一轮的），播报期间不再开麦
        assertEquals(1, f.transcriber.startCount)
        // 迟到的残留 partial 也不能把播报打断
        f.transcriber.onPartial!!.invoke("残留结果")
        assertEquals(0, f.synthesizer.stopCount)
        assertEquals(VoicePhase.SPEAKING, f.controller.state.value.phase)
    }

    @Test
    fun focusLossSuspendsAndFocusGainResumesListening() = runTest {
        val f = fixture(settings = VoiceSettings(ttsEnabled = true, continuous = true))
        f.controller.toggle()
        assertEquals(1, f.focus.claims)
        f.focus.lose()
        assertEquals(VoicePhase.IDLE, f.controller.state.value.phase)
        assertTrue(f.controller.state.value.suspendedByFocus)
        assertTrue(f.controller.state.value.noticeRes != null)
        assertEquals(1, f.transcriber.cancelCount)
        // 挂起不是结束：焦点还挂在名下，等系统给回 GAIN
        assertEquals(0, f.focus.releases)
        f.focus.regain()
        assertEquals(VoicePhase.LISTENING, f.controller.state.value.phase)
        assertFalse(f.controller.state.value.suspendedByFocus)
        assertEquals(2, f.transcriber.startCount)
    }

    @Test
    fun answerArrivingWhileSuspendedSkipsPlaybackAndWaitsForFocus() = runTest {
        val f = fixture(settings = VoiceSettings(ttsEnabled = true, continuous = true))
        f.controller.toggle()
        f.transcriber.onFinal!!.invoke("问题")
        f.focus.lose()
        f.controller.onAnswer("回答")
        assertTrue(f.synthesizer.spoken.isEmpty())
        assertEquals(VoicePhase.IDLE, f.controller.state.value.phase)
        f.focus.regain()
        assertEquals(VoicePhase.LISTENING, f.controller.state.value.phase)
    }

    @Test
    fun focusLossBeforeAnySessionDoesNotResurrectIt() = runTest {
        val f = fixture()
        f.focus.lose()
        assertEquals(VoicePhase.IDLE, f.controller.state.value.phase)
        assertFalse(f.controller.state.value.suspendedByFocus)
        f.focus.regain()
        assertEquals(0, f.transcriber.startCount)
    }

    @Test
    fun focusIsClaimedOncePerSessionAndReclaimedAfterIdle() = runTest {
        val f = fixture(settings = VoiceSettings(ttsEnabled = true, continuous = true))
        f.controller.toggle()
        f.transcriber.onFinal!!.invoke("问题")
        f.controller.onAnswer("回答")
        f.synthesizer.finishSpeaking()
        advanceTimeBy(1_000)
        runCurrent()
        // 一轮到下一轮不重复申请（避免自己踢自己）
        assertEquals(1, f.focus.claims)
        assertEquals(0, f.focus.releases)
        f.transcriber.onError!!.invoke(VoiceError.NO_MATCH)
        assertEquals(1, f.focus.releases)
        f.controller.toggle()
        assertEquals(2, f.focus.claims)
    }

    @Test
    fun releaseHandsBackFocusAndChannels() = runTest {
        val f = fixture()
        f.controller.toggle()
        f.controller.release()
        assertEquals(1, f.focus.releases)
        assertEquals(1, f.transcriber.releaseCount)
    }

    @Test
    fun lateFinalAfterLeavingListeningIsIgnored() = runTest {
        val f = fixture()
        var queries = 0
        f.controller.onQuery = { queries++ }
        f.controller.toggle()
        f.transcriber.onFinal!!.invoke("同一个问题")
        f.transcriber.onFinal!!.invoke("同一个问题")
        assertEquals(1, queries)
    }
}
