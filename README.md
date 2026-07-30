<p align="center">
  <img src="./ChisaKi.png" width="160" alt="KireiBox Logo" />
</p>

<h1 align="center">KireiBox</h1>

<p align="center">
  简洁、美观、专注本地的 Android 视觉小说管理与启动工具
</p>

<p align="center">
  <a href="../../releases">
    <img src="https://img.shields.io/badge/Download-Releases-8B5CF6?logo=github&logoColor=white" alt="Download" />
  </a>
  <a href="../../releases">
    <img src="https://img.shields.io/badge/Status-Alpha-F59E0B" alt="Alpha" />
  </a>
  <a href="#系统要求">
    <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0+" />
  </a>
  <a href="./LICENSE">
    <img src="https://img.shields.io/badge/License-GPL--3.0-E11D48" alt="GPL-3.0" />
  </a>
</p>

<p align="center">
  <a href="#主要功能">主要功能</a> ·
  <a href="#项目结构">项目结构</a> ·
  <a href="#交流群">加入交流群</a>
</p>

这是一个基于 [YukiHub](https://gitcode.com/xm486/YukiHub) 重构与二次开发的分支版本。

***

## 当前状态

**⚠️ KireiBox 目前处于较早期的测试阶段。**

部分功能、界面和引擎兼容性仍在持续打磨中。使用过程中如遇到问题，欢迎通过 QQ 群反馈。

***

## 项目简介

KireiBox 是一款面向 Android 的视觉小说管理与启动工具。

项目延续了 YukiHub 的游戏数据、目录扫描、封面元数据、设置管理、游玩记录与引擎启动底层，并在此基础上重新设计应用界面与交互。

KireiBox 使用 Kotlin、Jetpack Compose、Coroutines / Flow 与 MVVM 架构构建更加现代化的 Android 应用层。目前项目正在进行第三次重构，并持续将遗留实现向 Kotlin 迁移。

项目注重简单操作与清晰美观的界面，希望让用户可以方便地整理、搜索和启动本地视觉小说。

除用户主动使用的封面搜索与下载外，KireiBox 不依赖账号、云同步、社交或其他在线服务。

***

## 项目结构

```text
KireiBox/
├── app/                  # Compose 界面、应用入口与启动桥接
├── core-yukihub/         # 游戏库、扫描、元数据、设置与游玩记录
├── engine/               # KRKR、ONScripter、Tyrano、Artemis 内置引擎
├── doc/                  # 开发记录、版本说明与测试文档
├── gradle/               # Gradle Wrapper 与版本配置
├── build.gradle          # 根项目构建配置
└── settings.gradle       # 模块声明
```

KireiBox 使用 YukiHub 作为唯一的数据、扫描、元数据和设置底层；`app` 模块以 MVVM 组织 Compose 界面与交互，四个内置引擎集中在 `engine` 模块。

***

## 主要功能

- 扫描并管理本地视觉小说
- 自动识别游戏引擎和启动入口
- 搜索、收藏和整理游戏
- 获取并管理游戏封面
- 记录游戏启动及游玩时长
- 通过 Android 包名和 Activity 启动已安装应用

***

## 支持的启动方式

| **类型** | **自动扫描特征** | **扫描导入后的状态** |
| --- | --- | --- |
| Kirikiri | `.xp3`、`startup.tjs`、`config.tjs` | 使用内置 KRKR 启动；可从游戏卡片手动选择任意 XP3 入口。 |
| ONScripter | `0.txt`、`nscript.dat`、`onscript.nt*`、`.nsa`、`.sar` | 使用内置 ONScripter 启动。 |
| Tyrano | `index.html` 与 Tyrano / Electron 目录特征 | 使用内置 Tyrano 启动。 |
| Artemis | `system.ini`、`system/first.iet`、`.pfs` | 使用内置 Artemis 启动。 |

Android 应用不参与目录自动扫描，可通过包名和 Activity 手动添加并启动。不同游戏、系统和设备之间可能存在兼容性差异。

***

## 下载

测试版本请前往 [Releases](../../releases) 页面下载。

***

## 系统要求

- Android 8.0（API 26）及以上
- 部分游戏目录需要授予文件访问权限
- KRKR 原生引擎可能需要“所有文件访问权限”

***

## 构建信息

- Application ID：`com.kireibox.app`
- Min SDK：`26`
- Target SDK：`33`
- Compile SDK：`36`
- Version Code：`7`
- 当前版本：`0.5.0-alpha.1`

***

## 项目来源与致谢

KireiBox 是 [YukiHub](https://gitcode.com/xm486/YukiHub) 的重构与二次开发分支。YukiHub 为项目提供核心数据、扫描、元数据、设置与引擎启动基础；KireiBox 在其上构建新的 Kotlin、Compose 与 MVVM 应用层。

[KAMiGAL](https://github.com/ruizhishenri-commits/KamiGAL)、[RinneMobile](https://github.com/Weiss-UltimateSavior/RinneMobile)、[LunaBox](https://github.com/Saramanda9988/LunaBox) 和 [ReinaManager](https://github.com/huoshen80/ReinaManager) 主要作为界面呈现、交互方式与游戏库体验的参考，不作为 KireiBox 的运行时底层。

本项目参考或使用了以下开源项目：

- [YukiHub](https://gitcode.com/xm486/YukiHub)
- [KAMiGAL](https://github.com/ruizhishenri-commits/KamiGAL)
- [RinneMobile](https://github.com/Weiss-UltimateSavior/RinneMobile)
- [LunaBox](https://github.com/Saramanda9988/LunaBox)
- [ReinaManager](https://github.com/huoshen80/ReinaManager)
- Kirikiroid2
- ONScripter
- Tyrano
- Artemis

感谢相关开源项目的作者、贡献者，以及所有参与测试和反馈的用户。

***

## 交流群

<p align="center">
  <a href="https://qun.qq.com/universal-share/share?ac=1&authKey=nZMa0s3mxxG1A0f%2BY0nAWmBYpul7FWTEDI6UWrzqb2IgKC4aDkUhvkV2AekAkW%2F1&busi_data=eyJncm91cENvZGUiOiIxNjM2MDM2MzUiLCJ0b2tlbiI6Im93eFRyY0tqNDdxK3FGQXlVZ0lhMEZGbWZWemphZnpYYW1kWWpPN1ViL3A0SkRUd1dEclMwZkM1bWI0UEYxME4iLCJ1aW4iOiIzMDg2Njc4NzU1In0%3D&data=bwoLG7XAPzqsvtfneNCQUUlu-HpX1yCn-6dkgd8ubDeBJKEPgd7wKYa6ym-EbW07Vapc3xm_o-iy0GbFHhZk5Q&svctype=4&tempid=h5_group_info">
    <img src="https://img.shields.io/badge/QQ-163603635-12B7F5?logo=tencentqq&logoColor=white" alt="QQ Group" />
  </a>
</p>

<p align="center">欢迎加入 QQ 交流群，反馈问题、提建议或一起讨论功能。</p>

***

## 使用前说明

请仅使用本项目管理和启动你**有权使用**的游戏、应用或资源。

本项目不提供：

- 游戏本体
- 破解资源
- 绕过授权的能力
- 任何违规用途的支持

***

## 开源协议

KireiBox 采用 [GNU General Public License v3.0](./LICENSE) 开源。

项目中使用的第三方组件和内置引擎仍遵循各自的开源协议与版权声明。
