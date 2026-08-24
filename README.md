<p align="center">
  <img src="./ChisaKi.png" width="160" alt="KireiBox Logo" />
</p>

<h1 align="center">KireiBox</h1>

<p align="center">
  一款整合 KRKR、ONScripter、Tyrano 与 Artemis 等多种视觉小说引擎，兼顾美观界面与本地游戏管理的 Android 启动器
</p>

<p align="center">
  统一整理、展示和启动不同引擎的本地游戏，让视觉小说管理更加简单直观
</p>

<p align="center">
  KireiBox 是基于 YukiHub 的 RinneMobile 分支开发。
</p>

<p align="center">
  <a href="https://github.com/Yukin0a/KireiBox/releases">
    <img src="https://img.shields.io/badge/Download-Releases-8B5CF6?logo=github&logoColor=white" alt="Download" />
  </a>
  <img src="https://img.shields.io/badge/Version-0.5.0--alpha.15-F59E0B" alt="Version" />
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0+" />
  <a href="./LICENSE">
    <img src="https://img.shields.io/badge/License-GPL--3.0-E11D48" alt="GPL-3.0" />
  </a>
</p>

<p align="center">
  好看美观 · 简洁易用 · 本地优先 · 多引擎支持 · 横竖屏适配
</p>

***

## 当前状态

> KireiBox 目前处于 Alpha 测试阶段。

部分功能、界面细节和游戏兼容性仍在持续完善。不同游戏、Android 系统和设备之间可能存在兼容性差异。

如遇到问题，欢迎通过 GitHub Issues 或 QQ 交流群反馈。

***

## 主要功能

- 扫描、添加和管理本地视觉小说
- 自动识别游戏引擎及启动入口
- 沉浸式封面首页与多种展示样式
- 游戏搜索、收藏和资料编辑
- 游戏启动记录与游玩时长统计
- 支持竖屏、横屏和大屏布局
- 支持深色、浅色和减少动态效果
- 支持 VNDB、Bangumi、月幕 Gal、Hikarinagi 等封面来源
- 提供封面下载进度、取消和失败重试
- 支持通过包名和 Activity 添加 Android 应用
- 支持通过 Shizuku 授权导入桌面游戏快捷方式
- 支持应用内检查、下载和验证新版本

除封面搜索、元数据获取和版本检查等主动使用的功能外，KireiBox 主要围绕本地游戏库运行。

***

## 支持的启动方式

| 类型 | 自动识别特征 | 启动方式 |
| --- | --- | --- |
| Kirikiri / KRKR | `.xp3`、`startup.tjs`、`config.tjs` | 使用内置 KRKR 引擎启动，可手动选择 XP3 入口 |
| ONScripter | `0.txt`、`nscript.dat`、`onscript.nt*`、`.nsa`、`.sar` | 使用内置 ONScripter 引擎启动 |
| Tyrano | `index.html` 及 Tyrano / Electron 目录特征 | 使用内置 Tyrano 引擎启动 |
| Artemis | `system.ini`、`system/first.iet`、`.pfs` | 使用内置 Artemis 引擎启动 |
| Android 应用 | 应用包名与 Activity | 通过手动添加的应用条目启动 |
| 桌面游戏快捷方式 | 桌面已固定的游戏快捷方式 | 通过 Shizuku 授权导入为游戏卡片；兼容条目可直接启动，其他条目通过 Shizuku 启动 |

Android 应用和桌面游戏快捷方式目前不参与游戏目录自动扫描，需要通过应用内入口手动添加或导入。

***

## 下载与安装

请前往 [GitHub Releases](https://github.com/Yukin0a/KireiBox/releases) 下载最新测试版本。

### 系统要求

- Android 8.0（API 26）及以上
- 当前发布版本主要面向 `arm64-v8a` 设备
- 扫描游戏目录需要授予相应的文件访问权限
- KRKR 原生引擎在部分系统上可能需要“所有文件访问权限”
- 不同游戏和设备的兼容情况可能有所差异

***

## 项目结构

```text
KireiBox/
├── app/                  # Compose 界面、应用入口与启动桥接
├── core-yukihub/         # 游戏库、扫描、元数据、设置与游玩记录
├── engine/               # KRKR、ONScripter、Tyrano、Artemis 引擎
├── doc/                  # 开发记录、版本说明与测试文档
├── gradle/               # Gradle Wrapper 与版本配置
├── build.gradle          # 根项目构建配置
└── settings.gradle       # 模块声明
```

KireiBox 使用 Kotlin、Jetpack Compose、Coroutines / Flow 与 MVVM 架构构建应用界面和交互。

***

## 构建信息

- Application ID：`com.kireibox.app`
- 当前版本：`0.5.0-alpha.15`
- Version Code：`23`
- Min SDK：`26`
- Target SDK：`29`
- Compile SDK：`36`
- 主要发布 ABI：`arm64-v8a`

如需自行构建，请使用 Android Studio 打开项目，并准备 Java 17、Android SDK 和 Gradle 环境。

***

## 项目来源与致谢

本项目参考、学习或使用了以下开源项目：

- [RinneMobile](https://github.com/Weiss-UltimateSavior/RinneMobile)
- [YukiHub](https://gitcode.com/xm486/YukiHub)
- [KAMiGAL](https://github.com/ruizhishenri-commits/KamiGAL)
- [LunaBox](https://github.com/Saramanda9988/LunaBox)
- [ReinaManager](https://github.com/huoshen80/ReinaManager)
- Kirikiroid2 / Kirikiroid2Yuri
- OnscripterYuri
- Tyrano
- Artemis

感谢相关项目的作者、贡献者，以及所有参与测试和反馈的用户。

第三方源码、组件和原生引擎仍遵循各自的开源协议与版权声明，详细信息请参阅 [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md)。

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

请仅使用本项目管理和启动你有权使用的游戏、应用或资源。

本项目不提供：

- 游戏本体
- 破解资源
- 绕过授权的能力
- 任何违规用途的支持

***

## 反馈与贡献

遇到问题时，建议附上以下信息：

- 设备型号
- Android 版本
- KireiBox 版本
- 游戏引擎类型
- 问题截图
- 复现步骤
- 相关日志

欢迎通过 Issue 或 Pull Request 参与项目改进。

***

## 开源协议

KireiBox 采用 [GNU General Public License v3.0](./LICENSE) 开源。

项目中包含的第三方组件和内置引擎仍遵循各自的许可证及版权声明。
