# Solace

Solace 是一款面向本地设备的**多媒体文件浏览应用**，基于 Android（Kotlin + Jetpack Compose）开发，图片和视频完全存储在本地，不上传任何数据，注重隐私与流畅的浏览体验。

## 功能特性

- 图库：本地媒体文件按文件夹分组，文件全屏左右滑动查看
- 作品集：「作品集 → 作品 → 素材」三层管理，作品上下滑动浏览
- 隐私安全：数据本地存储，无账号、不联网

## 技术栈

- 语言：Kotlin 2.0
- UI：Jetpack Compose (Material 3)
- 数据：MediaStore（媒体库）+ Room（作品集元数据）
- 媒体：Coil（图片/缩略图）、Media3 ExoPlayer（视频）
- 构建：Gradle 8.13 + AGP 8.13
- 最低系统版本：Android 8.0 (API 26)

## 运行环境

| 组件 | 版本 |
| --- | --- |
| JDK | 17（Android Studio 自带 JBR 亦可） |
| Android Studio | 最新稳定版（Otter 2025.2.2 及更新；AGP 8.13 要求） |
| Android SDK | platform 37、build-tools 36.0.0 |
| 真机 / 模拟器 | Android 8.0 (API 26) 及以上 |
| Gradle | 8.13（使用项目自带 wrapper，无需全局安装） |
| AGP | 8.13.2 |
| Kotlin | 2.0.21 |

## 目录结构

```
Solace/
├── app/                      # 应用主模块
│   └── src/main/
│       ├── java/com/solace/app/
│       └── res/
├── docs/                     # 项目文档
├── gradle/                   # wrapper 与版本目录
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew
```

## License

[MIT](LICENSE)
