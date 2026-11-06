# 松源情·TV

多源聚合影视点播客户端。一次搜索，多个片源同时找片；同一部影片自动合并，可自由切换片源、线路与剧集。

本项目是原 Android 版「松源情·TV」的全平台重构，基于 **Kotlin Multiplatform + Compose Multiplatform** 共享数据层与界面逻辑，覆盖 Windows、macOS 与 iOS。

- APP 设计开发：陈光明

## 功能

- 多源并发聚合搜索（内置 14 个苹果 CMS V10 采集源，可在设置中开关）
- 归一化片名自动归并，同一影片多源线路一键切换
- 详情页：海报、年份、地区、评分、主演、简介与分集
- 播放：HLS / MP4，支持暂停、续播、拖动进度、音量与自动连播下一集
- 解析接口：直链优先，失败时走自定义解析接口，再回退到页面内嵌地址提取
- 本地缓存：详情与解析结果带 TTL 的 LRU 缓存，减少重复请求

## 平台状态

| 平台 | 形态 | 说明 |
| --- | --- | --- |
| Windows | MSI / EXE / 便携版 | 播放依赖本机安装的 VLC（libVLC） |
| macOS | DMG / .app | 播放依赖本机安装的 VLC |
| iOS | 可构建的 Xcode 工程 | 播放使用系统 AVPlayer；模拟器可直接构建，真机安装需 Apple 开发者证书签名 |

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

## 自动打包

推送到 `main` 或打 `v*` 标签时，GitHub Actions 会分别在 `windows-latest` 与 `macos-latest` 上构建安装包：

- Windows：`.msi`、`.exe`、便携版 zip
- macOS：`.dmg`、`.app`
- iOS：`shared` 框架产物

## 免责声明

本客户端仅提供聚合检索与播放能力，不存储、不制作、不传播任何视频内容。所有内容均来自第三方公开采集接口，请自行判断合法性，并在遵守当地法律法规的前提下使用。
