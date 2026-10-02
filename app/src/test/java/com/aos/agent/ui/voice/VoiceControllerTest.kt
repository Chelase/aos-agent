package com.aos.agent.ui.voice

import com.aos.agent.core.voice.SpeechSynthesizer
import com.aos.agent.core.voice.SpeechTranscriber
import com.aos.agent.core.voice.VoiceCommand
import com.aos.agent.core.voice.VoiceCommandVocabulary
import com.aos.agent.core.voice.VoiceError
import com.aos.agent.data.store.VoiceSettings
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 状态机整回路测试：灌假通道，不碰 Android。
 * 断言只看阶段与回调，不依赖具体资源 id 的语义。
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

    private val vocab = VoiceCommandVocabulary(
        newSession = setOf("新开会话"),
        openSettings = setOf("打开设置"),
        useSkillPrefixes = setOf("使用技能"),
    )

    private fun controller(
        transcriber: FakeTranscriber = FakeTranscriber(),
        synthesizer: FakeSynthesizer = FakeSynthesizer(),
        settings: VoiceSettings = VoiceSettings(ttsEnabled = true, continuous = false),
        granted: Boolean = true,
    ): Triple<VoiceController, FakeTranscriber, FakeSynthesizer> {
        val controller = VoiceController(
            transcriber = transcriber,
            synthesizer = synthesizer,
            vocabulary = vocab,
            settings = MutableStateFlow(settings),
            hasPermission = { granted },
        )
        controller.refreshAvailability()
        return Triple(controller, transcriber, synthesizer)
    }

    @Test
    fun startsIdleAndUsableWhenServiceAndPermissionPresent() {
        val (controller, _, _) = controller()
        assertEquals(VoicePhase.IDLE, controller.state.value.phase)
        assertTrue(controller.state.value.usable)
        assertFalse(controller.state.value.needsPermission)
    }

    @Test
    fun toggleStartsListening() {
        val (controller, transcriber, _) = controller()
        controller.toggle()
        assertEquals(VoicePhase.LISTENING, controller.state.value.phase)
        assertEquals(1, transcriber.startCount)
    }

    @Test
    fun toggleWhileListeningAsksRecognizerToSettle() {
        val (controller, transcriber, _) = controller()
        controller.toggle()
        controller.toggle()
        assertEquals(1, transcriber.stopCount)
        // 结算由 onFinal 接手，阶段仍停在聆听
        assertEquals(VoicePhase.LISTENING, controller.state.value.phase)
    }

    @Test
    fun partialResultsSurfaceImmediately() {
        val (controller, transcriber, _) = controller()
        controller.toggle()
        transcriber.onPartial!!.invoke("帮我看看")
        assertEquals("帮我看看", controller.state.value.partial)
    }

    @Test
    fun finalQuestionGoesToEngineAndThinkingPhase() {
        val (controller, transcriber, _) = controller()
        var received: String? = null
        controller.onQuery = { received = it }
        controller.toggle()
        transcriber.onFinal!!.invoke("今天车里多少度")
        assertEquals(VoicePhase.THINKING, controller.state.value.phase)
        assertEquals("今天车里多少度", received)
        assertEquals("今天车里多少度", controller.state.value.lastHeard)
    }

    @Test
    fun finalCommandExecutesLocallyWithoutQuery() {
        val (controller, transcriber, _) = controller()
        var query: String? = null
        var command: VoiceCommand? = null
        controller.onQuery = { query = it }
        controller.onCommand = { command = it }
        controller.toggle()
        transcriber.onFinal!!.invoke("新开会话")
        assertEquals(VoiceCommand.NewSession, command)
        assertNull(query)
        assertEquals(VoicePhase.IDLE, controller.state.value.phase)
    }

    @Test
    fun emptyFinalReturnsIdleWithNotice() {
        val (controller, transcriber, _) = controller()
        controller.toggle()
        transcriber.onFinal!!.invoke("   ")
        assertEquals(VoicePhase.IDLE, controller.state.value.phase)
        assertTrue(controller.state.value.noticeRes != null)
    }

    @Test
    fun answerIsSpokenThenReturnsIdle() {
        val (controller, _, synthesizer) = controller()
        controller.onTurnStarted()
        controller.onAnswer("车内 24 摄氏度")
        assertEquals(VoicePhase.SPEAKING, controller.state.value.phase)
        assertEquals(listOf("车内 24 摄氏度"), synthesizer.spoken)
        synthesizer.finishSpeaking()
        assertEquals(VoicePhase.IDLE, controller.state.value.phase)
    }

    @Test
    fun continuousModeGoesBackToListeningAfterSpeaking() {
        val (controller, transcriber, synthesizer) =
            controller(settings = VoiceSettings(ttsEnabled = true, continuous = true))
        controller.onAnswer("回答")
        assertEquals(VoicePhase.SPEAKING, controller.state.value.phase)
        synthesizer.finishSpeaking()
        assertEquals(VoicePhase.LISTENING, controller.state.value.phase)
        assertEquals(1, transcriber.startCount)
    }

    @Test
    fun ttsDisabledSkipsSpeakingPhase() {
        val (controller, _, synthesizer) = controller(settings = VoiceSettings(ttsEnabled = false))
        controller.onAnswer("回答")
        assertEquals(VoicePhase.IDLE, controller.state.value.phase)
        assertTrue(synthesizer.spoken.isEmpty())
    }

    @Test
    fun toggleWhileSpeakingInterruptsAndListensAgain() {
        val (controller, transcriber, synthesizer) = controller()
        controller.onAnswer("长回答")
        assertEquals(VoicePhase.SPEAKING, controller.state.value.phase)
        controller.toggle()
        assertEquals(1, synthesizer.stopCount)
        assertEquals(VoicePhase.LISTENING, controller.state.value.phase)
        assertEquals(1, transcriber.startCount)
    }

    @Test
    fun noServiceRendersDisabledWithReason() {
        val (controller, _, _) = controller(transcriber = FakeTranscriber(available = false))
        assertFalse(controller.state.value.usable)
        assertTrue(controller.state.value.blockedReasonRes != null)
        // 禁用态下点按钮不开麦，只给提示
        controller.toggle()
        assertEquals(VoicePhase.IDLE, controller.state.value.phase)
    }

    @Test
    fun missingPermissionAsksForPermissionInsteadOfListening() {
        val (controller, transcriber, _) = controller(granted = false)
        assertFalse(controller.state.value.usable)
        assertTrue(controller.state.value.needsPermission)
        assertEquals(0, transcriber.startCount)
    }

    @Test
    fun recognizerErrorsAllReturnToIdleWithNotice() {
        VoiceError.entries.forEach { error ->
            val (controller, transcriber, _) = controller()
            controller.toggle()
            transcriber.onError!!.invoke(error)
            assertEquals("phase after $error", VoicePhase.IDLE, controller.state.value.phase)
            assertTrue("notice after $error", controller.state.value.noticeRes != null)
        }
    }

    @Test
    fun engineFailureLeavesThinkingPhase() {
        val (controller, _, _) = controller()
        controller.onTurnStarted()
        controller.onTurnFailed()
        assertEquals(VoicePhase.IDLE, controller.state.value.phase)
    }

    @Test
    fun releaseFreesBothChannels() {
        val (controller, transcriber, _) = controller()
        controller.release()
        assertEquals(1, transcriber.releaseCount)
    }
}
