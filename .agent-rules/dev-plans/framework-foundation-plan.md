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

- [ ] 项目可在本地稳定编译
- [ ] Automotive 模拟器可安装并打开 App
- [ ] 首页显示 Compose UI
- [ ] 前台服务可手动启动
- [ ] BootReceiver 可手动广播验证
- [ ] 系统信息摘要可显示
- [ ] 基础测试通过

## 不在本期做的事

- CLI 终端
- Agent 对话
- 语音唤醒 / TTS
- 桌面宠物
- Car API 全量扫描
- 第三方应用管理

## 进度

- [ ] Step 1. 升级工程为 Compose + AAOS 基础骨架
- [ ] Step 2. 建立最小 UI 外壳
- [ ] Step 3. 建立系统组件骨架
- [ ] Step 4. 建立最小系统信息读取能力
- [ ] Step 5. 建立测试与验证基线
