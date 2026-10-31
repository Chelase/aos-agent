# 第三方应用管理

## 结论

**本机制尚未实现。** 早期规划过"车机第三方应用管理"（应用市场、本地上传 APK、网络搜索安装），
但代码中从未落地：规划里提到的 `AppMarketActivity` / `AppListViewModel` / `AppSearchEngine` / `ApkRepository`
全部不存在，现有 `system/update/ApkInstaller.kt` 只服务于**应用自身升级通道**（下载 + 校验 + 拉起系统安装器）。

本文档保留此条，是为了避免后续开发者按"已实现"来理解；真正落地时请整体重写本文档。

## 涉及对象

| 层 | 文件/表 | 角色 |
|---|---|---|
| System | `system/update/ApkInstaller.kt` | **仅**升级通道：下载 APK、逐位校验 SHA-256、拉起系统安装器 |

> 注意：原规划中的 `AppMarketActivity.kt` / `AppListViewModel.kt` / `AppSearchEngine.kt` / `ApkRepository.kt` 均不存在。

## 运行链路

### 实际存在：应用自身升级

```
[设置页「版本与更新」→ 检查更新]
    v
[UpdateChecker]  GET releases/latest/download/update.json
    v
[UpdateDecision.decide]  版本比较 + 官方主机白名单 + 校验和格式
    v
[ApkInstaller.download]
    ├─ 地址不在官方白名单 → UNTRUSTED_URL（不发起下载）
    ├─ 下载 → 逐位比 SHA-256 → 不过 → 删文件 → CHECKSUM_MISMATCH
    └─ 过 → FileProvider 授权 → ACTION_VIEW 拉起系统安装器（用户确认）
```

详见 [`release-and-update.md`](./release-and-update.md)。

## 使用点

- 设置页「版本与更新」卡：检查更新 / 下载并安装
- `AgentRuntime`：不涉及第三方应用安装

## 修改点

| 要做什么 | 改哪里 | 怎么改 |
|---|---|---|
| 实现应用市场 | 新建 `ui/market/` + `core/appmarket/` | 全新模块，需先写机制文档并整体重写本文档 |
| 实现网络搜索应用 | 新建搜索来源适配 | 见 [`web-access.md`](./web-access.md) 的搜索层，可复用 |
| 复用 APK 安装能力 | `system/update/ApkInstaller.kt` | 当前绑死升级语义（官方白名单 + update.json 契约），复用前需解耦 |

## 关键约束

1. **现有 APK 安装能力只为"升级自己"设计**：白名单与 `update.json` 契约都绑定版本比较语义，
   不能直接拿来当通用侧载通道——否则等于开出任意 URL 安装入口。
2. **网络下载的 APK 必须校验签名**，防止恶意替换。
3. **不做静默安装**：最终确认权在系统安装器与用户手里。
4. **上架合法性由运营方负责**，本仓只提供工具能力。

## 维护方式

- **排查**：想找"应用市场"代码时注意它不存在；目前只有升级通道。
- **验证**：`./gradlew :app:testDebugUnitTest`；完整升级链路需真机或已有 Release。

> 更新时间：2026-10-31（修正：原文描述的功能从未实现，标注为未实现并列出实际存在对象）
