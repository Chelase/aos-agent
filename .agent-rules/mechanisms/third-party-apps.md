# 第三方应用管理

## 结论

提供车机第三方应用管理能力：通过本地上传 APK 安装、搜索网络可用车机软件、应用列表管理，解决车机应用生态匮乏的问题。

## 涉及对象

| 层 | 文件/模块 | 角色 |
|---|---|---|
| UI | AppMarketActivity.kt | 应用市场/管理入口 |
| UI | AppListViewModel.kt | 已安装应用列表管理 |
| UI | AppSearchViewModel.kt | 网络搜索应用 |
| UI | ApkInstaller.kt | APK 安装与管理 |
| Core | ApkRepository.kt | 应用数据仓库 |
| Core | AppSearchEngine.kt | 网络应用搜索引擎 |
| System | PackageManager | 系统包管理（安装/卸载/查询） |
| System | FileProvider | APK 文件共享给系统安装器 |

## 运行链路

### 本地上传安装

```
[用户选择 APK 文件]
    | 文件选择器（支持 U 盘/下载目录）
    v
[ApkInstaller 接收文件]
    | 复制到 App 私有目录（安全沙箱）
    v
[调用 PackageManager.installPackage()]
    | 或调用系统安装器 Intent
    v
[系统安装流程]
    | 用户确认权限
    v
[安装完成 -> 刷新已安装列表]
```

### 网络搜索安装

```
[用户在搜索框输入关键词]
    v
[AppSearchEngine]
    | 搜索多个源：
    | - APKPure / APKMirror / APKCombo 等镜像站
    | - 车机应用社区（车机应用市场）
    | - 自建应用仓库
    v
[返回搜索结果列表]
    | 用户选择 -> 下载 APK
    v
[ApkInstaller.installFromUrl(url)]
    | 下载 -> 校验签名 -> 安装
```

### 应用管理

```
[应用列表]
    |
    +-- 已安装应用（PackageManager.getInstalledPackages）
    |     显示名称/图标/版本/包名
    |     操作：打开 / 卸载 / 查看权限
    |
    +-- 可更新应用（检测版本差异）
    |
    +-- 收藏应用（本地数据库）
```

### 应用来源

| 来源 | 方式 | 可靠性 |
|---|---|---|
| 本地上传（U 盘/下载） | 文件选择器 + FileProvider | 用户自行负责 |
| APKPure / APKMirror | 网页抓取搜索结果 | 中（需校验签名） |
| 车机专用应用市场 | 自建或第三方 API | 高 |
| 开发者自建仓库 | 自建服务器 | 最高 |

## 使用点

- 用户缺少车机应用 -> 搜索安装导航/音乐/视频工具
- 安装我们的 Debug APK -> 通过本地上传 sideload
- 后续 Agent 可自动安装必要工具包

## 修改点

| 要做什么 | 改哪里 | 怎么改 |
|---|---|---|
| APK 本地上传安装 | ApkInstaller.kt | 文件选择 + PackageManager 调用 |
| 网络搜索 | AppSearchEngine.kt | 多源聚合搜索 + 下载 |
| 应用列表 | AppListViewModel.kt | PackageManager 查询 + UI |
| 权限处理 | AndroidManifest.xml | REQUEST_INSTALL_PACKAGES 等 |

## 关键约束

1. Android 8+ 安装未知来源应用需要用户手动授权"安装未知应用"权限。
2. 网络下载 APK 必须校验签名，防止恶意替换。
3. 不上架非法/盗版应用，仅提供搜索工具，用户自行决定。
4. 搜索源可配置，默认只启用可信源。

## 维护方式

- **新增搜索源**：在 AppSearchEngine 的源配置中追加 URL 和解析规则。
- **验证**：上传 APK 安装 -> 成功；搜索应用 -> 返回结果 -> 下载安装。

> 更新时间：2026-06-28
