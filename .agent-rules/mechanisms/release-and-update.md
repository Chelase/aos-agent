# 发布与更新机制（签名 / CI / 检查更新 / 下载安装）

## 结论

GitHub Actions 出签名 release 与一份 `update.json` 清单，车机端主动拉这份静态清单判断有没有新版，
下载后逐位校验 SHA-256 才允许拉起系统安装器；装不了的情况一律按能力缺失说明原因，不做静默自更新。

## 涉及对象

| 层 | 文件/表 | 角色 |
|---|---|---|
| 构建 | `app/build.gradle.kts` | 读 `keystore.properties` 或 `AOS_SIGNING_*`；缺签名时 release 直接失败；版本号由 `-PVERSION_CODE/-PVERSION_NAME` 注入 |
| 构建 | `keystore/`、`keystore.properties` | 私钥与口令，**都在 `.gitignore` 里**，仓库不存 |
| CI | `.github/workflows/ci.yml` | PR/主干：单测 + `assembleDebug` + 反向验证"没签名必须失败" |
| CI | `.github/workflows/release.yml` | tag `v*`：还原 keystore → 签名构建 → `apksigner verify` → 生成 `update.json` → 建 Release |
| Core | `core/update/UpdateDecision.kt` | 清单数据类 + 手工 JSON 解析 + 版本比较 + 主机白名单（纯函数，可单测） |
| Core | `core/update/UpdateChecker.kt` | `UpdateChecker` 接口 + OkHttp 实现（404 与断网分开报） |
| Core | `core/update/Checksum.kt` | SHA-256 逐位比对；格式不合格直接算不通过 |
| Core | `core/update/InstallGates.kt` | 安装前两道门的判定（先权限、后有无安装器） |
| System | `system/update/ApkInstaller.kt` | 下载到 `cacheDir/update` → 校验 → FileProvider 授权 → 拉起安装器 |
| System | `system/update/AppVersionReader.kt` | 读自身 `versionName/versionCode` |
| UI | `ui/settings/SettingsScreen.kt` | 「版本与更新」卡：当前版本、检查、结果三态、下载并安装、禁用原因 |
| 清单 | `update.json`（Release 附件） | `{versionCode, versionName, apkUrl, sha256, notes, publishedAt}` |

## 运行链路

```
[发布] 打 tag v1.2.3
   release.yml
     ├─ 解析版本：name=1.2.3，code=1*10000+2*100+3=10203
     ├─ secrets → keystore/aos-release.jks + keystore.properties（只在构建机工作目录，用完随机器销毁）
     ├─ ./gradlew :app:assembleRelease -PVERSION_CODE=10203 -PVERSION_NAME=1.2.3
     │     └─ build.gradle.kts：缺签名材料 → 配置期抛错，不产未签名包
     ├─ apksigner verify --print-certs（验不过就不出版）
     ├─ 重命名 aos-agent-1.2.3.apk → sha256sum → 生成 update.json（apkUrl 由 tag 拼出）
     └─ gh release create/upload：APK + update.json 两个附件

[检查] 设置页「版本与更新」点"检查更新"
   HttpUpdateChecker.check()
     GET https://github.com/<repo>/releases/latest/download/update.json   // 别名，不走有速率限制的 API
       ├─ 404            → BAD_MANIFEST（"还没发版本"，不是"连不上"）
       ├─ 其它非 2xx/异常 → NETWORK
       └─ 200 → UpdateDecision.parse（手工解析，缺字段即坏清单）
                  → UpdateDecision.decide(localVersionCode, manifest)
                       ├─ versionCode<=0            → BAD_MANIFEST
                       ├─ 非 HTTPS 或主机不在白名单  → UNTRUSTED_SOURCE
                       ├─ sha256 不是 64 位十六进制  → UNTRUSTED_SOURCE
                       ├─ 远端 <= 本地               → UpToDate
                       └─ 远端 >  本地               → Available(manifest) → 界面给"下载并安装"

[安装] 点"下载并安装"
   InstallGates.decide(canRequestPackageInstalls(), installerPresent())
     ├─ PERMISSION_NEEDED → 说明原因 + "去系统设置允许"；ON_RESUME 时重探（回来就能点）
     ├─ NO_INSTALLER      → 说明这台车没有安装程序，走商店/厂商升级
     └─ READY → ApkInstaller.download(apkUrl, sha256)
                   ├─ 地址不在白名单 → UNTRUSTED_URL（根本不发起下载）
                   ├─ 流式写 cacheDir/update/<净化后的文件名>，按百分点回调进度
                   ├─ 下完重开流逐位比 SHA-256：不过 → 删文件 → CHECKSUM_MISMATCH
                   └─ 过 → FileProvider.getUriForFile(<pkg>.fileprovider) → ACTION_VIEW + 读授权
                            → startActivity 拉起系统安装器（用户在系统弹窗里确认）
```

## 使用点

- 设置页「版本与更新」卡：唯一入口，手动检查（没有后台轮询）。
- CI：`ci.yml` 每次 PR/主干；`release.yml` 只在打 tag 或手动触发时出版本。
- 系统安装器：最终确认权在系统与用户手里，本应用只负责把校验过的包交出去。

## 修改点

| 要做什么 | 改哪里 | 怎么改 |
|---|---|---|
| 发新版本 | 打 tag `vX.Y.Z` 推上去 | 版本号从 tag 推导，`update.json` 由 CI 生成，不要手改仓库里的清单 |
| 换更新源 | `UpdateChecker.MANIFEST_URL` + `UpdateDecision.ALLOWED_HOSTS` | 两处必须一起改；白名单是精确主机匹配，别改成后缀匹配 |
| 加清单字段 | `release.yml` 生成段 + `UpdateManifest` + `parse` | 手工解析按工程约定（不引序列化插件），缺字段要当坏清单而不是猜默认值 |
| 换签名密钥 | 本机重生成 + 同步三个 secrets | 旧包与新包签名不同就互相覆盖不了，换 key 等于换升级链路，先想清 |
| 改安装能力探测 | `ApkInstaller.canRequestInstalls()/installerPresent()` | 顺序不能反：没授权时先讲授权，别让用户去够一个不存在的安装器 |
| 要后台定时检查 | 新增调度 + 网络策略决策 | **当前刻意不做**：车机流量与网络策略未定，先要一个决策再动手 |

## 关键约束

1. **私钥与口令不进仓库、不进日志**：只存在于本机 `keystore/`（已 gitignore）与 Actions secrets；
   文档、日志、提交信息里都不出现口令值。
2. **未签名的 release 不允许存在**：缺签名材料时构建必须在配置期失败，并留一条 CI 反向验证
   （"没签名却构建成功"要判失败）——历史上正是"构建成功但装不上"白跑了一整轮验证。
3. **更新通道不是侧载通道**：只允许 HTTPS + 精确匹配的官方主机白名单，校验和格式不合格就当不可信；
   不做"用户可填任意更新地址"的入口。下载文件名来自远端清单，必须剥掉路径成分防 `../`。
4. **校验不过就绝不让安装器看见这个文件**：先删再报错；缓存里不留一个对不上号的 APK。
5. **做不到静默更新，就别装作做得到**：装不了分"没授权"和"没有安装器"两种，各自给原因与下一步；
   回页面要重探能力，否则按钮会永远停在禁用态。
6. **清单字段与 CI 生成端是一对契约**：改一边必须同时改另一边；
   `apkUrl` 由 tag 拼出，改产物命名规则要连 `release.yml` 的重命名一起改。

## 维护方式

- **本地出版本**：`./gradlew :app:assembleRelease -PVERSION_CODE=… -PVERSION_NAME=…`，
  用 `apksigner verify --print-certs` 确认签名；缺 `keystore.properties` 时应立刻失败并说明原因。
- **排查**：检查更新报"清单读不懂"→ 先看是不是还没发 Release（404 走的就是这条文案）；
  报"连不上"→ 车机出网问题（模拟器无路由时用 `adb reverse tcp:7897 tcp:7897` +
  `settings put global http_proxy 127.0.0.1:7897` 借宿主代理）；
  报"来源不在白名单"→ 清单里的 `apkUrl` 被改到了非官方主机，属安全问题不是网络问题。
- **验证**：`./gradlew :app:testDebugUnitTest`（清单解析/版本比较/白名单/校验和/门控共 20 例，
  网络层用 OkHttp 拦截器灌假响应）。模拟器可验到"检查更新真发请求 + 四种回音文案 +
  FileProvider 与安装权限已注册"（`dumpsys package com.aos.agent`）。
- **必须真机或首发版本才能验**：完整"下载→校验→系统安装器→升级成功"这一趟，
  要有第一个真实 Release 才跑得通；在那之前这条路径的口径是"仅单测与配置验证"。

> 更新时间：2026-10-03
