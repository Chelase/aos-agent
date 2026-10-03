# 发布与更新计划（签名 / CI / 检查更新 / 下载安装）

> **统一愿景对齐**：与 `../docs/unified-ecosystem-vision.md` 一致。本计划只解决 aos-agent 自身的交付与升级，
> 不新增跨设备协议、不改变 AOC 单一人格出口；更新通道是"车机主动拉官方发布物"，不开放任何远程下发能力。
>
> 配套机制文档：`../mechanisms/release-and-update.md`（实施后生成）
> 触发背景：语音 Phase B 交付后需要真机调命中率，而 release 构建当前**未配签名**（`assembleRelease` 出未签名 APK，装不上）。

## 当前状态

- `app/build.gradle.kts` 的 `release` 只有 `optimization { enable = false }`，**没有 signingConfig** → 生产包根本装不了。
- `versionCode = 1`、`versionName = "1.0"` 写死，没有版本注入通道。
- 无 `.github/workflows/`，没有 CI 也没有发布流水线。
- 工程里已有 OkHttp 与 kotlinx.serialization（模型下载、LLM、MCP 都在用），网络与 JSON 解析不缺件。
- 没有任何"版本/更新"界面入口。

## 期望管理（先讲清做不到什么）

| 目标 | 车机上的现实 |
|---|---|
| 静默自更新 | 做不到。需要系统签名、设备策略或厂商 OTA；第三方 App 无路径 |
| 应用内下载并安装 | 半自动：下载 + 校验 + **拉起系统安装器**，要 `REQUEST_INSTALL_PACKAGES` 且车机允许侧载；不少量产镜像根本没有安装器界面 |
| 检测有没有新版 | 做得到，且不受车机限制（只要能有 HTTPS 出网） |
| 后台定时轮询更新 | **本期不做**。车机网络策略与流量成本要先定，属独立决策，不在这里顺手加 |

所以本期交付口径是：**检测 + 提示 + 给下载入口**（第一步，任何车机都能用）与
**应用内下载 + 校验 + 拉起安装器**（第二步，能力不足时按能力缺失规则禁用并写明原因）。

## 关键技术决策

1. **更新清单走 Release 附件 `update.json`，不走 GitHub Releases API**：
   API 匿名限 60 次/小时且要处理分页与认证，附件就是一个静态 HTTPS 文件，最省事也最不容易坏。
   内容由 CI 生成：`{"versionCode":..,"versionName":"..","apkUrl":"..","sha256":"..","notes":".."}`。
2. **签名密钥只在本机与 Actions secrets 里**：`keystore/*.jks` 与 `keystore.properties` 一律进 `.gitignore`，
   仓库里不留任何私钥与口令。CI 用 base64 secret 还原。
3. **release 缺签名就明确失败**，不产未签名 APK——"看着构建成功却装不上"是这次要修的原 bug，不能留个静默降级。
4. **下载必须校验 SHA-256 且只认官方主机**：`github.com` / `raw.githubusercontent.com` /
   `release-assets.githubusercontent.com` / `objects.githubusercontent.com` 白名单，非 HTTPS 或非白名单直接拒；
   不做"用户可填任意更新地址"的入口（那等于开一条侧载通道）。
5. **APK 下到 `cacheDir`，用 FileProvider 授权给安装器**：安装器是另一个 uid，直接给 `file://` 在 Android 7+ 会崩。

## 改动范围

| 文件 | 动作 |
|---|---|
| `.gitignore` | 忽略 `keystore/`、`keystore.properties` |
| `app/build.gradle.kts` | release 读签名（env/属性）；`versionCode/versionName` 支持 env 注入；缺签名时 `assembleRelease` 报错退出 |
| `keystore/` + 本机 `keystore.properties` | 新生成（不入库），交付时说明备份与轮换 |
| `.github/workflows/ci.yml` | 新增 — PR/push 跑 `testDebugUnitTest` + `assembleDebug` |
| `.github/workflows/release.yml` | 新增 — tag `v*` 或手动触发：还原 keystore → `assembleRelease` → `apksigner verify` → 生成 `update.json` → 建 Release 上传两个产物 |
| `core/update/UpdateManifest.kt` | 新增 — 数据类 + 纯函数 `decide(local, remote)`（无网络无 Android，可单测） |
| `core/update/UpdateChecker.kt` | 新增 — 接口 + OkHttp 实现 + 主机/协议白名单校验 |
| `system/update/AndroidAppVersion.kt` | 新增 — 读 `PackageInfo` 的当前版本 |
| `system/update/ApkInstaller.kt` | 新增 — 下载到 cacheDir → SHA-256 校验 → FileProvider → 拉起安装器；能力探测 |
| `ui/update/UpdateCard.kt`（或并入设置页） | 新增 — 当前版本 / 检查 / 三态结果 / 下载与安装 / 禁用原因 |
| `AndroidManifest.xml` | `REQUEST_INSTALL_PACKAGES` + FileProvider |
| `res/xml/file_paths.xml` | 新增 — cacheDir 暴露给安装器 |
| `res/values*/strings.xml` | 中英成对新增 |
| `app/src/test/.../update/*` | 新增 — 清单解析、版本比较、白名单拒绝用例 |

## 步骤

### S1 签名与 CI（先把"能装的生产包"造出来）
本机生成 keystore 并接进 gradle；两个 workflow 落地；CI 产出 `update.json`。
**验收**：本地 `assembleRelease` 在缺密钥时明确失败、有密钥时产出可 `apksigner verify` 通过的 APK；
打 tag 后 Actions 出 Release 且附件含 `update.json`（字段与真实 asset 地址一致）。

### S2 检查更新
`UpdateManifest` + `decide()` 纯函数 + `UpdateChecker`；设置页卡片显示三态。
**验收**：单测覆盖"更新/相同/更低/脏 JSON/非白名单主机/非 HTTPS"；模拟器点一次检查能拿到真实清单并显示结果，
断网时给可重试原因而不是转圈。

### S3 下载与安装
下载进度 + SHA-256 校验 + 拉起安装器 + 能力探测。
**验收**：`canRequestPackageInstalls()` 为 false 时按钮禁用且写明原因、能跳授权页；
篡改校验值时拒绝安装；模拟器上安装器存在则走通一次真实升级（同签名 debug→release 不行，用同 key 的两个版本验）。

## 验收清单

- [ ] release 产物可安装（签名验证通过），CI 一键出版
- [ ] 应用内能查出"有无新版"，失败有原因
- [ ] 下载校验不过绝不拉起安装
- [ ] 车机不允许侧载 / 没有安装器时，界面给出原因与替代路径，不给死按钮
- [ ] 仓库无密钥与口令；`.gitignore` 覆盖到位
- [ ] 中英成对文案；`testDebugUnitTest` + `assembleDebug` 全绿

## 进度

- [ ] S1 签名与 CI
- [ ] S2 检查更新
- [ ] S3 下载与安装
- [ ] 验证与文档回写

## 不在本期做的事

| 项目 | 原因 |
|---|---|
| 后台定时检查更新 | 车机网络与流量策略未定，需单独决策 |
| 静默安装 / 免确认升级 | 第三方 App 无路径，要系统签名或厂商 OTA |
| 增量差量更新（Android App Bundle / Play） | 不走商店就没意义，先不做 |
| 更新通道可配置地址 | 等于开侧载通道，明确不做 |
| 回滚与 A/B 灰度 | 需要服务端配合，另案 |
