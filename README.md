# 松源情·TV

多源聚合影视点播客户端。一次搜索，多个片源同时找片；同一部影片自动合并，可自由切换片源、线路与剧集。

本项目是原 Android 版「松源情·TV」的全平台重构，基于 **Kotlin Multiplatform + Compose Multiplatform** 共享数据层与界面逻辑，覆盖 Windows、macOS 与 iOS。

- APP 设计开发：陈光明

## 功能

- 首页：豆瓣热门推荐（可关闭）、继续观看（自动续播上次进度）
- 多源并发聚合搜索（内置 14 个苹果 CMS V10 采集源，可在设置中开关、编辑、删除、新增）
- 分类浏览：电影 / 剧集 / 短剧 / 综艺 / 动漫 / 纪录片，二级分类跨源聚合
- 归一化片名自动归并，同一影片多源线路一键切换
- 详情页：海报、年份、地区、评分、主演、简介、收藏与分集
- 播放：HLS / MP4，暂停、续播、拖动进度、音量、自动连播；桌面端支持沉浸全屏与竖屏短剧画幅
- 解析接口：直链优先，失败时走自定义解析接口，再回退到页面内嵌地址提取
- 我的：收藏与播放历史，可单条删除或清空
- 本地缓存：详情与解析结果带 TTL 的 LRU 缓存，减少重复请求

## 平台状态

| 平台 | 架构 | 形态 | 说明 |
| --- | --- | --- | --- |
| Windows | x86_64 | MSI / EXE / 便携版 | 已内置 libVLC 3.0.21，安装即播，无需另装 VLC；Windows on ARM 设备可经系统 x64 模拟运行 |
| macOS | arm64（Apple 芯片） | DMG | 已内置 libVLC 3.0.21，无需另装 VLC |
| macOS | x86_64（Intel） | DMG | 同上 |
| Linux / 麒麟 | x86_64 | .deb / .rpm / 便携 tar.gz | 银河麒麟 V10（Debian 系）用 deb，中标麒麟（RHEL 系）用 rpm；播放使用系统 VLC |
| Linux / 麒麟 | aarch64（飞腾 / 鲲鹏） | .deb / .rpm / 便携 tar.gz | 同上，使用 aarch64 构建 |
| iOS | arm64 | 已签名 IPA / Xcode 工程 | 播放使用系统 AVPlayer；Ad Hoc 签名，仅描述文件内的设备可安装 |

## 工程结构

```
multiplatform/
├── shared/      # KMP 共享层：模型、采集协议、聚合搜索、播放地址解析、设置
├── desktop/     # Compose Desktop 应用（Windows / macOS）
└── iosApp/      # iOS 工程（Xcode）
```

共享层主要模块：

- `data/remote`：Ktor 网络层与苹果 CMS 采集协议 DTO
- `data/video`：聚合搜索、详情回查、播放地址解析（直链 / 解析接口 / 页面提取）
- `data/source`：内置片源与开关持久化
- `shared/AppGraph`：跨平台依赖容器

## 本地构建

```bash
# Windows 运行
./gradlew :desktop:run

# Windows 打包（需要 WiX，GitHub Actions 已内置）
./gradlew :desktop:packageMsi

# macOS 打包
./gradlew :desktop:packageDmg
```

## Linux / 麒麟 安装

```bash
# 银河麒麟 V10 / Ubuntu（Debian 系，x86_64）
sudo apt install -y vlc
sudo dpkg -i songyuanqing-tv_1.2.3_amd64.deb

# 飞腾 / 鲲鹏（Debian 系，aarch64）
sudo dpkg -i songyuanqing-tv_1.2.3_arm64.deb

# 中标麒麟 / 麒麟信安（RHEL 系，x86_64）
sudo dnf install -y vlc
sudo rpm -ivh songyuanqing-tv-1.2.3-1.x86_64.rpm

# 中标麒麟 / 麒麟信安（RHEL 系，aarch64）
sudo rpm -ivh songyuanqing-tv-1.2.3-1.aarch64.rpm
```

便携包解压后直接运行 `SongyuanqingTV/bin/SongyuanqingTV`；Windows / macOS 便携包同样已内置 libVLC。

## 自动打包

推送到 `main` 或打 `v*` 标签时，GitHub Actions 会在对应架构的托管 runner 上分别构建：

- Windows（`windows-latest`，x86_64）：`.msi`、`.exe`、便携版 zip
- macOS（`macos-latest`，arm64）：`-arm64.dmg`
- macOS（`macos-15-intel`，x86_64）：`-x86_64.dmg`
- Linux / 麒麟（`ubuntu-latest`，x86_64）：`.deb`、`.rpm`、便携 tar.gz
- Linux / 麒麟（`ubuntu-24.04-arm`，aarch64）：`.deb`、`.rpm`、便携 tar.gz
- iOS（标签构建）：已签名 `.ipa`

## 免责声明

本客户端仅提供聚合检索与播放能力，不存储、不制作、不传播任何视频内容。所有内容均来自第三方公开采集接口，请自行判断合法性，并在遵守当地法律法规的前提下使用。
