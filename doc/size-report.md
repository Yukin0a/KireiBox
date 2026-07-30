# KireiBox 目录体积说明

工作区的大部分占用来自可重新生成的本机编译产物，不是 KireiBox 源码本身：

| 项目 | 说明 | 处理方式 |
|---|---|---|
| `engine/build` | Gradle/Native 编译产物 | 可通过 Gradle clean 清理 |
| `app/build` | APK、中间字节码和资源处理产物 | 可通过 Gradle clean 清理 |
| `.gradle` / `.kotlin` / `.cxx` | 可再次生成的本地缓存 | `.gitignore` 已忽略 |
| `engine/src/main/jniLibs` | 四引擎必需的 Native 库 | 保留 |
| Kotlin/Java/Compose 源码和资源 | 应用源代码 | 保留 |

## 0.5.0-alpha.1 Release

- APK：57,757,863 bytes（55.08 MiB），arm64-v8a only。
- 上一版 0.4.0-alpha.1：57,820,593 bytes。
- 减少 62,730 bytes（约 0.11%）。
- 已移除未引用的 RPGMaker 钩子资源，并排除历史 SoundFont、Bugly 和旧
  KRKR 重复库；三个 KRKR 与三个 Artemis 兼容变体仍为运行游戏所必需。
- 包体下降有限，是因为体积主要由必须保留的 Native 引擎兼容库构成。

删除 `build` 目录不会删除源码、Native 引擎或 `apk` 目录中的交付件；下次
运行 Gradle 时会重新生成编译目录。
