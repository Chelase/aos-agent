# 框架搭建计划

> 配套机制文档：`.agent-rules/mechanisms/architecture-overview.md`、`.agent-rules/mechanisms/requirements-analysis.md`

## 当前阶段

本计划只解决“项目能不能稳定站起来”的问题，不追求产品功能完整。

目标是把当前 Android Studio 空壳工程改造成一个可在 AAOS 模拟器上编译、安装、启动、调试的基础骨架，为后续所有功能批次提供稳定底座。

## 改动范围

### 模块

- `app/src/main/java/com/aos/agent/`
- `app/src/main/res/`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/src/main/AndroidManifest.xml`

### 重点文件

- `MainActivity.kt`
- `ui/theme/*`
- `service/AgentForegroundService.kt`
- `receiver/BootReceiver.kt`
- `system/SystemInfoProvider.kt`

## 步骤

### Step 1. 升级工程为 Compose + AAOS 基础骨架

**改动文件：**
- `gradle/libs.versions.toml`
- `build.gradle.kts`
- `app/build.gradle.kts`

**内容：**
- 引入 Kotlin Android / Compose Compiler / Material3 / Activity Compose
- 明确 Java 17 / Kotlin / Compose 编译链
- 保持当前单模块结构，不提前拆分子模块

**验收：**
- `./gradlew assembleDebug` 或 `gradlew.bat assembleDebug` 成功

### Step 2. 建立最小 UI 外壳

**改动文件：**
- `MainActivity.kt`
- `ui/theme/*`
- `ui/home/*`
- `res/values/themes.xml`

**内容：**
- 搭建 Compose 入口
- 提供基础首页：应用名、系统状态摘要、工程师模式入口占位
- 暂不接入复杂导航、暂不引入宠物/终端/聊天 UI

**验收：**
- App 能在 Automotive 模拟器打开并显示首页

### Step 3. 建立系统组件骨架

**改动文件：**
- `AndroidManifest.xml`
- `service/AgentForegroundService.kt`
- `receiver/BootReceiver.kt`

**内容：**
- 声明 Automotive feature
- 声明开机广播、前台服务、基础权限
- 服务先只负责启动、通知、生命周期日志
- 广播只做委托，不做耗时逻辑

**验收：**
- 手动启动服务成功
- 手动发送 BOOT_COMPLETED 广播不崩溃

### Step 4. 建立最小系统信息读取能力

**改动文件：**
- `system/SystemInfoProvider.kt`
- `ui/home/*`

**内容：**
- 读取 Android 版本、API Level、厂商、品牌、设备名、网络状态
- 首页展示最小摘要
- 所有信息读取必须支持 fallback

**验收：**
- 首页能展示基础系统信息

### Step 5. 建立测试与验证基线

**改动文件：**
- `app/src/test/...`
- `app/src/androidTest/...`

**内容：**
- 删除模板测试
- 增加最小单元测试 + 启动测试 + 首页展示测试

**验收：**
- 单元测试通过
- 仪器测试通过

## 验收清单

- [x] 项目可在本地稳定编译
- [x] Automotive 模拟器可安装并打开 App
- [x] 首页显示 Compose UI
- [x] 前台服务可手动启动
- [x] BootReceiver 可手动广播验证
- [x] 系统信息摘要可显示
- [x] 基础测试通过

## 不在本期做的事

- CLI 终端
- Agent 对话
- 语音唤醒 / TTS
- 桌面宠物
- Car API 全量扫描
- 第三方应用管理

## 归档

**完成日期：** 2026-07-26

**冒烟结果：**
- `testDebugUnitTest` — 16 用例通过
- `assembleDebug` — 通过
- 仪器测试 — 6 用例通过（Automotive AVD，Android 15 / SDK 35 / 1408x792）
- 真机路径截图验证：首启中文（系统为 en_US）、语言可切英文、工程师模式网络行显示 Wi-Fi

**AAOS 模拟器验证注意事项**（排查耗时较长，后续勿重复踩坑）：

1. **必须先唤醒屏幕**：`mWakefulness=Asleep` 时 Activity 进入 RESUMED 后会被立即 PAUSE，
   Compose 树不存在，测试报 `No compose hierarchies found`。
   运行前执行 `adb shell input keyevent KEYCODE_WAKEUP && adb shell svc power stayon true`。
2. **`./gradlew connectedDebugAndroidTest` 在本镜像上不稳定**：运行途中出现
   `deletePackageX` 卸载应用包，进程被杀，报 `Process crashed.`，整轮中断。
   改用手动路径可稳定通过：
   ```bash
   adb install -r --user 10 app/build/outputs/apk/debug/app-debug.apk
   adb install -r --user 10 app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
   adb shell am instrument --user 10 -w com.aos.agent.test/androidx.test.runner.AndroidJUnitRunner
   ```
3. **多用户**：AAOS 有 user 0 与 user 10，前台是 user 10（`adb shell am get-current-user` 确认）。
   `adb uninstall` 会报 `DELETE_FAILED_INTERNAL_ERROR`，改用 `pm uninstall --user <id>`。
4. **截图需指定显示器**：模拟器有双显示屏，`screencap -p` 直接输出会混入警告文本导致文件损坏，
   需用 `adb shell dumpsys SurfaceFlinger --display-id` 取 ID 后 `screencap -p -d <id>`。

**遗留问题：** 无。Step 4 网络状态与 Step 5 仪器测试均已闭合。

## 进度

- [x] Step 1. 升级工程为 Compose + AAOS 基础骨架 — 2026-07-08（提交 f98c6e9）
- [x] Step 2. 建立最小 UI 外壳 — 2026-07-08（提交 17ff1fa，视觉后由 ui-design-system-plan 重做）
- [x] Step 3. 建立系统组件骨架 — 2026-07-08（提交 17ff1fa）
- [x] Step 4. 建立最小系统信息读取能力 — 2026-07-26（设备信息 17ff1fa，网络状态本日补齐）
- [x] Step 5. 建立测试与验证基线 — 2026-07-26（16 单元测试 + 6 仪器测试全通过）
