# AOS-Agent 图标来源

2026-10-18 最终修订：按用户要求保留第一版环形灯组造型，使用蓝底白色，并将白色图案适度放大、加粗。保留六个环灯、中心灯、强调的顶灯与上方感知弧线的布局；以 `(54,54)` 为中心整体放大 7.5%，弧线基础宽度由 1.8dp 增至 2.16dp，顶灯半径由 3.8dp 增至 4.1dp，其余灯点由 3dp 增至 3.25dp。背景为蓝色 `#0B57D0`，全部图案为白色 `#FFFFFF`。图标为项目直接编写的原生矢量，不依赖字体、图库或 AI 栅格素材。

| 文件 | 用途与来源 |
|---|---|
| `aos-agent-icon.svg` | 108 × 108 坐标系的可编辑母版，声明导出尺寸 1024 × 1024 |
| `aos-agent-icon.png` | 由同目录 SVG 经 ImageMagick `magick` 导出的 1024 × 1024 预览 |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | 第一版环形灯组与感知弧线的白色自适应图标前景 |
| `app/src/main/res/drawable/ic_brand_mark.xml` | 同源 108dp 单色 UI 资源；`AOSLogo` 读取并使用主题 primary 着色 |
| `app/src/main/res/drawable/ic_launcher_background.xml` | 引用 `aos_icon_background` 的蓝色自适应背景 |
| `app/src/main/res/drawable/ic_launcher_monochrome.xml` | 同源灯组和弧线的单色版本，供平台主题图标使用 |
| `app/src/main/res/mipmap-anydpi*/ic_launcher*.xml` | Android launcher 对应的自适应资源入口 |

PNG 再导出：在仓库根运行 `magick -background none artwork/aos-agent-icon.svg artwork/aos-agent-icon.png`。SVG 的圆角背景用于预览，launcher 的最终外轮廓由平台 mask 决定。主体使用 108dp 坐标系，SVG 与三份 Android vector 使用相同路径和中心缩放；实际弧线宽度约 2.322dp，主体仍在中心直径 66dp 安全区。修改品牌图形时同步 SVG、foreground、monochrome 和 brand_mark，并重导出 PNG；实际 UI 不依赖 PNG。

视觉 token 与适配约束见 [视觉规范](../.agent-rules/docs/原型/design.md)。

设计过程与首版存档见 [图标方案记录](concepts/2026-10-18-blue-white/README.md)：`00` 为首版深色矢量，`05` 为白底蓝色尝试，`06` 为蓝底白色基础版，`07` 为已接入的放大加粗版；`01`—`04` 为 imagegen 生成的未采用候选，完整提示词随图留存。
