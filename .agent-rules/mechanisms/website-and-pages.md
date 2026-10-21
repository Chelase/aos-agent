# 项目网站与 GitHub Pages

## 结论

项目官网由独立 `site/` 静态站承载，GitHub Actions 构建后部署至 Pages；安装包继续来自 GitHub Releases。网站不运行车机 Agent，也不收集模型密钥。

## 涉及对象

| 层 | 文件/表 | 角色 |
|---|---|---|
| 页面 | `site/index.html`、`styles/site.css`、`scripts/site.js` | 中文官网、响应式布局与场景预览 |
| 资源 | `site/assets/` | 自托管字体、图标及真实模拟器截图 |
| 元数据 | `site/data/release.json` | 最新正式版的已校验下载信息 |
| 构建 | `site/scripts/build.py`、`sync_release.py` | 注入版本、生成纯静态部署产物 |
| 验证 | `site/scripts/check.py`、`site/tests/` | 本地资源和发布元数据契约 |
| CI | `.github/workflows/pages.yml` | 站点检查、构建与 Pages 部署 |
| 设计 | `site/design/`、`site/design-qa.md` | 用户选定的第三版及实现对照 |

## 运行链路

```text
[站点 PR] → 校验 → 构建（不部署）
[main 站点修改 / 手动运行 / Release 成功]
  → 读取官方最新正式 Release
  → 校验版本与 APK 地址 → release.json
  → build.py → site/dist（仅公开静态资源）
  → upload-pages-artifact → deploy-pages
[访客] 官网 → 官方 Release APK / GitHub 文档
```

## 使用点

- 首页以下载 APK 为主，GitHub 与开发文档为辅；截图切换、放大和 FAQ 在浏览器本地完成。
- App 的发布工作流与更新清单保持原机制；网站在发布工作流成功后刷新下载入口。
- 网站与 Android CI 分别按文件路径触发。

## 修改点

| 要做什么 | 改哪里 | 怎么改 |
|---|---|---|
| 改页面内容 | `site/index.html`、`site/scripts/site.js` | 实际能力为准，测试截图保留说明 |
| 改视觉 | `site/styles/site.css`、`site/design/` | 保持第三版构图和已确认蓝白品牌 |
| 更新版本 | 官方 GitHub Release | 自动同步，不手改生产页面的版本文字 |
| 更换截图或依赖资源 | `site/assets/`、`site/README.md` | 核对来源、隐私和许可证，保持图片比例 |
| 调整部署 | `pages.yml`、`build.py` | 仅上传 `site/dist`；保留路径和权限约束 |

## 关键约束

1. 官网与 Android 工程隔离，公开产物仅包含显式选定的站点资源。
2. 版本只接受本仓库正式 Release，APK 仅指向该版本的官方 GitHub 附件。
3. 浏览器不访问需凭据的 API，不接收或保存 API Key；所有字体与图标自托管。
4. 车辆与语音能力按真实权限和实现说明，不展示虚构实车证明或下载量。
5. 网站路径兼容 `/aos-agent/`；动态文本进行转义，源数据错误时停止部署。
6. 响应式、键盘操作和减少动态效果设置须参与验收。

## 维护方式

- 本地：`python site/scripts/build.py` → `python site/scripts/check.py` → 使用本地 HTTP 服务预览 `site/dist`。
- 测试：`python -m unittest discover -s site/tests`；浏览器核对手机与桌面布局、下载链接、场景标签、图片预览和 FAQ。
- 部署状态以 Pages 工作流与公开 URL 实际访问结果为准；视觉对照见 `site/design-qa.md`。

> 更新时间：2026-10-21
