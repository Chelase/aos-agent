# AOS-Agent 官网

中文静态落地页，部署到 <https://chelase.github.io/aos-agent/>。源代码与 Android 工程隔离，无 npm 依赖、后端或在线 Agent。

## 本地开发

需要 Python 3.13+，在仓库根目录运行：

```bash
python -m unittest discover -s site/tests -v
python site/scripts/build.py
python site/scripts/check.py
python -m http.server 4173 --directory site/dist
```

打开 <http://localhost:4173/>。编辑 `index.html`、`styles/site.css` 或 `scripts/site.js` 后重新构建并刷新。`dist/` 不提交。构建通过相对资源路径兼容 Pages 项目子路径；`SITE_URL` 可覆盖 canonical、分享图片和 sitemap 的 HTTPS 地址。

## 版本与部署

- 本地和 PR 使用 `data/release.json` 中已验证的正式版本。
- main 部署时，工作流读取本仓库最新正式 Release，校验 tag、官方 APK 地址、大小和 SHA-256 后再构建；错误会停止部署。
- `.github/workflows/pages.yml` 处理网站 PR 检查、main 发布、手动部署，以及 `Release` 成功后的版本刷新。
- APK 保留在 GitHub Releases。网页不直接调用 GitHub API，不包含凭据、遥测或 API Key 输入。
- 仅 `dist/` 上传 Pages；设计稿、QA、测试及 Python 构建工具不进入部署产物。

## 设计与资源

用户选定的第三版见 `design/reference.png`；[网站设计规范](design/DESIGN.md)记录实际 token 和组件，[验收记录](design-qa.md)保留视觉对照。第三版为白底、黑色首行与蓝色次行标题、真实宽幅界面、场景切换和功能介绍。品牌蓝 `#0B57D0`，正文 `#191C22`，次要文字 `#626B7A`，浅底 `#F4F6FA`，圆角 14 px。字号与间距由响应式 CSS 统一管理，支持减少动态效果设置。

| 资源 | 来源及许可 |
|---|---|
| `assets/brand.svg` | 已确认的项目图标，来自 `artwork/aos-agent-icon.svg` |
| `screens/home.png` | `docs/images/home-dark.png`，AAOS 模拟器开发构建 |
| `screens/terminal.png`、`panel.png`、`voice.png` | `.impeccable/review/round2-1920-dark/`；模拟器，语音图展示权限缺失状态 |
| `screens/chat.png` | `.impeccable/review/round1-1920-fixture/chat-fixture.png`；明确标注的 UI 测试示例 |
| `assets/icons/` | Tabler Icons，MIT；保留 `assets/licenses/tabler-MIT.txt` |
| `assets/fonts/aos-sans-sc.woff2` | Noto Sans SC 可变字体的页面字符子集，OFL-1.1；保留 `assets/licenses/noto-sans-sc-OFL.txt` |

固定上游版本与来源记录在 `assets/sources.json`。截图中的系统状态、设备和开发版本不代表实时实车；不得移除说明。

字体仅在新增字符时重新生成。下载 `assets/sources.json` 固定版本的 `NotoSansSC[wght].ttf`，安装 `fonttools[woff]`，使用 fontTools subset，以 `index.html` 与 `scripts/site.js` 的全部 Unicode 字符加 ASCII U+0020–007E 为字符集，输出 WOFF2，保留可变字重和 OpenType 布局。新字符必须加入子集，避免字体回退。

原生 App 的视觉规范仍以项目 `.agent-rules/` 中现有文档为准，本目录仅维护网站。
