# 第三方许可与来源

核对日期：2026-10-20。项目原创部分的许可证为根目录 [LICENSE](../LICENSE)，版权署名为 Chelase；第三方材料不因该选择而改变授权条款。

本目录记录已核对的主要来源，不代替最终发布包中全部传递依赖与原生组件的完整许可清单。依赖升级、复制源码或更换模型时，需继续核对对应版本的许可证及 NOTICE。

## 直接引入的终端源码

`app/src/main/java/com/aos/agent/terminal/` 下 14 个 Java 文件源自 Termux `terminal-emulator` v0.118.1；Kotlin 会话宿主与 PTY 桥接属于本项目集成。来源见 [Termux 上游模块](https://github.com/termux/termux-app/tree/v0.118.1/terminal-emulator)。

[该版本的上游许可声明](https://github.com/termux/termux-app/blob/v0.118.1/LICENSE.md)说明：Termux 主仓库使用 GPLv3-only，但 `terminal-emulator` / `terminal-view` 中使用的 Terminal Emulator for Android 代码是 Apache-2.0 例外。本项目使用此处的终端仿真代码，不以 Termux 主应用的整体许可证替代模块核对。

- [上游许可声明副本](termux-v0.118.1-license.md)。
- [Terminal Emulator for Android NOTICE](android-terminal-emulator-NOTICE.txt)，保留上游署名和许可文字。
- `WcWidth.java` 注明其宽度表来自 jquast/wcwidth 的 `1b9b6585b0080ea5cb88dc9815796505724793fe`；其 [MIT 许可副本](wcwidth-MIT.txt)单独保留。
- 项目内包名、JNI 库名及集成方式的调整见根目录 [NOTICE](../NOTICE)。

## 主要运行依赖

| 组件 | 项目所用版本 | 许可与核对来源 |
|---|---|---|
| Vosk Android | 0.3.47 | Apache-2.0；Gradle 缓存中的该版本 POM 及 [上游 COPYING](https://github.com/alphacep/vosk-api/blob/master/COPYING) |
| JNA（Vosk 传递依赖） | 5.13.0 | 上游提供双许可，本项目选择 Apache-2.0；[版本许可声明](https://github.com/java-native-access/jna/blob/5.13.0/LICENSE)、[本地副本](JNA-5.13.0-LICENSE.txt) |
| 中文唤醒模型（单独下载） | vosk-model-small-cn-0.22 | [发布方模型列表](https://alphacephei.com/vosk/models)列为 Apache-2.0；不内置于 APK |

AndroidX / Compose、Kotlin、kotlinx 和 OkHttp 等其他依赖的版本集中在 [版本目录](../gradle/libs.versions.toml)，授权以各组件对应版本附带的许可证及声明为准。本次核对没有将整棵传递依赖树宣称为单一许可证。

## 参考资料与图片

`docs/参考文档/` 中收录的外部文章和配图保留原作者版权，不属于 Chelase 的原创软件授权范围；转发、再分发或用于商业材料前需确认原作者授权。

项目图标的创作与生成来源见 [artwork/README.md](../artwork/README.md)。
