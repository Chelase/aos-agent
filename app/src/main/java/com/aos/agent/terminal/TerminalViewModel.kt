package com.aos.agent.terminal

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 终端页状态。Starting → Running → Exited；Exited 后可 restart 重开一个 shell。 */
sealed interface TerminalUiState {
    data object Starting : TerminalUiState
    data class Running(val session: TerminalSession) : TerminalUiState
    data class Exited(val exitCode: Int) : TerminalUiState
}

/**
 * 终端会话宿主：持有 [TerminalSession]（Termux 模型），把回调折叠成 UI 状态。
 * shell 查找在 IO 线程，session 必须在主线程创建（其 Handler 绑定构造线程）。
 */
class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<TerminalUiState>(TerminalUiState.Starting)
    val state: StateFlow<TerminalUiState> = _state

    /** 渲染帧计数：模拟器缓冲变化时 +1，画布据此重绘。 */
    private val _frameTick = MutableStateFlow(0)
    val frameTick: StateFlow<Int> = _frameTick

    private var session: TerminalSession? = null
    private var pendingCols = 0
    private var pendingRows = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    fun start() {
        if (_state.value is TerminalUiState.Running) return
        _state.value = TerminalUiState.Starting
        viewModelScope.launch(Dispatchers.IO) {
            val shell = findShell()
            if (shell == null) {
                withContext(Dispatchers.Main) { _state.value = TerminalUiState.Exited(EXIT_NO_SHELL) }
                return@launch
            }
            withContext(Dispatchers.Main) { createSession(shell) }
        }
    }

    fun restart() {
        if (session?.isRunning() == true) return
        session = null
        start()
    }

    /** 画布尺寸变化时调用；首次调用同时拉起 shell 进程。PTY 创建失败折进 Exited，不崩 UI。 */
    fun updateSize(cols: Int, rows: Int) {
        if (cols < 2 || rows < 2) return
        pendingCols = cols
        pendingRows = rows
        val current = session ?: return
        try {
            current.updateSize(cols, rows)
        } catch (e: Exception) {
            Log.e(TAG, "start shell failed", e)
            _state.value = TerminalUiState.Exited(EXIT_NO_SHELL)
            return
        }
        if (_state.value is TerminalUiState.Starting) {
            _state.value = TerminalUiState.Running(current)
        }
    }

    fun send(text: String) {
        session?.write(text)
    }

    fun sendControl(byte: Byte) {
        session?.write(byteArrayOf(byte), 0, 1)
    }

    override fun onCleared() {
        session?.finishIfRunning()
    }

    private fun createSession(shell: String) {
        val app = getApplication<Application>()
        val cwd = app.filesDir.absolutePath
        val env = arrayOf(
            "PATH=${System.getenv("PATH") ?: "/system/bin"}",
            "HOME=$cwd",
            "TMPDIR=${app.cacheDir.absolutePath}",
            "TERM=xterm-256color",
            "LANG=utf-8",
        )
        val created = TerminalSession(shell, cwd, arrayOf(shell), env, null, client)
        session = created
        if (pendingCols > 0) {
            created.updateSize(pendingCols, pendingRows)
            _state.value = TerminalUiState.Running(created)
        }
    }

    private fun findShell(): String? = listOf("/system/bin/sh", "/bin/sh", "/system/xbin/sh")
        .firstOrNull { java.io.File(it).exists() }

    private val client = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {
            tick()
        }
        override fun onTitleChanged(changedSession: TerminalSession) {}
        override fun onSessionFinished(finishedSession: TerminalSession) {
            _state.value = TerminalUiState.Exited(finishedSession.exitStatus)
        }
        override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
            val manager = getApplication<Application>().getSystemService(ClipboardManager::class.java)
            manager?.setPrimaryClip(ClipData.newPlainText("terminal", text))
        }
        override fun onPasteTextFromClipboard(session: TerminalSession) {}
        override fun onBell(session: TerminalSession) {}
        override fun onColorsChanged(session: TerminalSession) {
            tick()
        }
        override fun onTerminalCursorStateChange(state: Boolean) {}
        override fun getTerminalCursorStyle(): Int = TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE
        override fun logError(tag: String, message: String) {
            Log.e(tag, message)
        }
        override fun logWarn(tag: String, message: String) {
            Log.w(tag, message)
        }
        override fun logInfo(tag: String, message: String) {
            Log.i(tag, message)
        }
        override fun logDebug(tag: String, message: String) {
            Log.d(tag, message)
        }
        override fun logVerbose(tag: String, message: String) {
            Log.v(tag, message)
        }
        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
            Log.e(tag, message, e)
        }
        override fun logStackTrace(tag: String, e: Exception) {
            Log.e(tag, "", e)
        }
    }

    private fun tick() {
        mainHandler.post { _frameTick.value++ }
    }

    companion object {
        private const val TAG = "AOSTerminal"
        const val EXIT_NO_SHELL = 127
    }
}
