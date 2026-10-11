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
  <img src="https://img.shields.io/badge/Version-0.5.0--alpha.18-F59E0B" alt="Version" />
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

如遇到问题，欢迎通过 GitHub Issues 反馈。

***

## 主要功能

- 扫描、添加和管理本地视觉小说，支持单个添加与批量添加
- 自动识别游戏引擎及启动入口
- 沉浸式封面首页与多种展示样式、效果预设
- 游戏搜索、收藏和资料编辑
- 游戏启动记录与游玩时长统计
- 支持竖屏、横屏和大屏布局
- 支持跟随系统、浅色、深色主题，可选强调色，并支持减少动态效果
- 支持手动添加 Windows PC 游戏，经外置 Winlator（winlator-cn）运行
- 设置页支持搜索，并提供快速设置向导
- KRKR 可为单个游戏设置渲染、性能与字体大小
- ONScripter、Artemis、Tyrano 提供全局引擎设置，可设置游戏屏幕方向
- 封面来源顺序：Bangumi、NextMoe、Hikarinagi、VNDB
- 混合封面匹配：一次查询全部来源、按跨源一致/别名命中排序并标注徽章；后台自动匹配可按置信度自动绑定（可在设置关闭），可限制仅 Wi-Fi、仅标题一致时自动设置
- 支持同步 NextMoe 游玩时长
- 提供封面下载进度、取消和失败重试
- 支持通过包名和 Activity 添加 Android 应用
- 支持应用内检查、下载和验证新版本，自动检查每日最多一次

除封面搜索、元数据获取和版本检查等主动使用的功能外，KireiBox 主要围绕本地游戏库运行。

***

## 支持的启动方式

| 类型 | 自动识别特征 | 启动方式 |
| --- | --- | --- |
| Kirikiri / KRKR | `.xp3`、`startup.tjs`、`config.tjs` | 使用内置 KRKR 引擎启动，可手动选择 XP3 入口 |
| ONScripter | `0.txt`、`nscript.dat`、`onscript.nt*`、`.nsa`、`.sar` | 使用内置 ONScripter 引擎启动 |
| Tyrano | `index.html` 及 Tyrano / Electron 目录特征 | 使用内置 Tyrano 引擎启动 |
| Artemis | `system.ini`、`system/first.iet`、`.pfs` | 使用内置 Artemis 引擎启动 |
| PC（手动添加） | 添加游戏「Winlator 外置启动」选择游戏目录，自动选择启动程序 exe | 外置 Winlator（winlator-cn），首次使用需先创建容器 |
| Android 应用 | 应用包名与 Activity | 通过手动添加的应用条目启动 |

Android 应用目前不参与游戏目录自动扫描，需要通过应用内入口手动添加。

PC 游戏不内置运行环境，需要自行安装含外置启动功能的 [winlator-cn](https://github.com/hostei33/winlator-cn/releases)（v0.4.2.11.2 或更新版本）；启动程序可在游戏详情「启动程序」中切换。

***

## 下载与安装

请前往 [GitHub Releases](https://github.com/Yukin0a/KireiBox/releases) 下载最新测试版本。

### 系统要求

- Android 8.0（API 26）及以上
- 当前发布版本主要面向 `arm64-v8a` 设备
- 扫描游戏目录需要授予相应的文件访问权限
- KRKR 原生引擎在部分系统上可能需要“所有文件访问权限”
- 运行 PC 游戏需要另外安装 winlator-cn
- 不同游戏和设备的兼容情况可能有所差异

***

## 项目结构

```text
KireiBox/
├── app/                  # Compose 界面、应用入口与启动桥接
├── core-yukihub/         # 游戏库、扫描、元数据、设置与游玩记录
├── engine/               # KRKR、ONScripter、Tyrano、Artemis 引擎
├── gradle/               # Gradle Wrapper 与版本配置
├── build.gradle          # 根项目构建配置
└── settings.gradle       # 模块声明
```

KireiBox 使用 Kotlin、Jetpack Compose、Coroutines / Flow 与 MVVM 架构构建应用界面和交互。

### 包职责说明

`app` 模块（`src/main/java/com/kireibox/`）：

- `app/` —— 应用壳：`LauncherActivity` 与 `app/launcher/*` 协调器（存储权限、启动、导入、窗口 chrome、诊断导出）；`app/diagnostics/` 为诊断日志。
- `designsystem/` —— 设计系统：`AppleDesignTheme` / `AppleMotion` / `AppleTypography` 与 `KireiColors`。
- `ui/` —— Compose 界面：`KireiBoxApp` 外壳、各 feature 页面（首页/游戏库/设置/封面/文件选择）与 `KireiViewModel`（Facade）+ `Kirei*Controller` 控制器。
- `cover/`、`update/`、`auth/`、`external/shortcut/`、`krkr/`、`shortcut/` —— 各自领域平台逻辑。
- `com/yuki/yukihub/{launcher,launcherbridge,tyrano}` 与 `YukiHubApp` —— 引擎桥接，命名固定，供 Manifest / JNI / 反射按名查找。

`core-yukihub` 模块（`src/main/java/com/yuki/yukihub/`）：

- `model/`、`scanner/` —— 领域模型与游戏扫描。
- `data/` —— `GameRepository`（门面）+ `data/game/GameQueryRepository`、`data/play/PlaySessionRepository`、`data/play/PlayStatsRepository`、`data/backup/GameBackupCodec`、`data/db/GameIdentityKey`。
- `metadata/` —— 元数据与封面来源客户端、标题匹配与混合匹配置信度（`CoverSourcePriority` / `CoverMatchConfidence`）。
- `settings/` —— 设置模型与 `YukiSettingsStore`。
- `launch/ net/ auth/ storage/ migration/ util/` —— 启动、网络、授权、存储、迁移与工具。

### 结构门禁

CI（`.github/workflows/android-debug.yml`）除构建与测试外，还运行：

- `verifyFileSize`：`app` / `core-yukihub` 的 `src/main` 单文件不得超过 800 行（`engine/` 为第三方冻结区，不纳入）。
- `detekt`：结构复杂度门禁，当前违规以 `config/detekt/*-baseline.xml` 冻结，只对新增违规报错。
- `ktlint`：格式门禁，规则见根目录 `.editorconfig`。

***

## 构建信息

- Application ID：`com.kireibox.app`
- 当前版本：`0.5.0-alpha.18`
- Version Code：`25`
- Min SDK：`26`
- Target SDK：`29`
- Compile SDK：`36`
- 主要发布 ABI：`arm64-v8a`

如需自行构建，请使用 Android Studio 打开项目，并准备 Java 17、Android SDK 和 Gradle 环境。

账号功能使用用户自己的授权。Bangumi 可匿名搜索，也可在 App 内连接个人 Token；NextMoe 和 Hikarinagi 使用系统浏览器与 PKCE。构建时通过环境变量或不提交的 `local.properties` 提供公开的 `KIREIBOX_NEXTMOE_CLIENT_ID`、`KIREIBOX_HIKARINAGI_CLIENT_ID`，不要配置客户端密钥。

- NextMoe：注册公共原生客户端，回调为 `http://127.0.0.1/callback`；App 在运行时选择随机端口。
- Hikarinagi：注册公共移动客户端，回调为 `kireibox://oauth/hikarinagi`，允许 `openid profile catalog:read catalog:full offline_access`。
- 未配置客户端的来源会提示暂不能登录，不影响本地游戏库；配置 ID 本身不代表真实授权测试通过。
- 图片默认直接访问来源地址；来源被网络或 DNS 阻断时，可重试、切换来源或导入本地图片。已保存的封面与资料可离线使用。


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
