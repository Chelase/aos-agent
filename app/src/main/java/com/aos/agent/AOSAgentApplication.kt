package com.aos.agent

import android.app.Application
import com.aos.agent.i18n.AppLocaleController
import com.aos.agent.i18n.localeStoreFor

/**
 * 应用入口。
 *
 * 首启语言兜底放在这里而不是 `MainActivity.onCreate`：
 * `setApplicationLocales` 触发的是应用级配置变更，放在 Application 阶段写入时
 * 尚无 Activity 存在，语言在首个 Activity 创建前就已确定，Activity 的 onCreate
 * 只跑一次。放在 Activity 里则依赖系统的配置变更时序，语义上更绕。
 */
class AOSAgentApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLocaleController(localeStoreFor(this)).ensureDefault()
    }
}
