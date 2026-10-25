package com.aos.agent.system.llm

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 把 assets 里的 CLI 脚本模板渲染后装到 `filesDir/bin/aos-llm`。
 *
 * 幂等：内容一致就跳过。占位符在安装期替换，脚本自身不感知应用包名与路径。
 * 调用点：前台服务创建、终端建会话前（双保险），失败只记日志不阻塞主流程。
 */
class LlmCliInstaller(private val context: Context) {

    fun ensureInstalled() {
        runCatching {
            val binDir = File(context.filesDir, BIN_DIR_NAME)
            val raw = context.assets.open(ASSET_PATH).use { it.readBytes().decodeToString() }
            val rendered = raw
                .replace("@PKG@", context.packageName)
                .replace("@BIN_DIR@", binDir.absolutePath)
            binDir.mkdirs()
            // 主名 + 无连字符别名：车载拼音键盘常把半角 "-" 转成全角，别名让 aosllm 也能敲
            NAMES.forEach { name ->
                val target = File(binDir, name)
                if (!target.exists() || target.readText() != rendered) {
                    target.writeText(rendered)
                }
                target.setReadable(true, true)
                target.setExecutable(true, true)
            }
        }.onFailure { Log.w(TAG, "aos-llm 安装失败", it) }
    }

    private companion object {
        const val TAG = "AOSAgent.LlmCli"
        const val ASSET_PATH = "bin/aos-llm.sh"
        const val BIN_DIR_NAME = "bin"
        val NAMES = listOf("aos-llm", "aosllm")
    }
}
