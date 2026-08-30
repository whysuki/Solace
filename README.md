# Solace

Solace 是一款面向本地设备的**多媒体文件浏览应用**，基于 Android（Kotlin + Jetpack Compose）开发，图片和视频完全存储在本地，不上传任何数据，注重隐私与流畅的浏览体验。

## 功能特性

- 文件浏览：图片视频按文件夹分组，网格混排浏览
- 隐私安全：数据本地存储，无账号、不联网

## 技术栈

- 语言：Kotlin 2.0
- UI：Jetpack Compose (Material 3)
- 构建：Gradle 8.13 + AGP 8.13
- 最低系统版本：Android 8.0 (API 26)

## 运行环境

| 组件 | 版本 |
| --- | --- |
| JDK | 17（Android Studio 自带 JBR 亦可） |
| Android Studio | 最新稳定版（Ladybug 及更新） |
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
├── gradle/                   # wrapper 与版本目录
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew
```

## License

[MIT](LICENSE)
