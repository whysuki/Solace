# AGENTS.md

面向本仓库的智能体开发指引。新会话先读本文档，再动代码。本文档与实现同步维护：改动功能或约束后，必须更新对应小节。

## 项目简介

Solace 是一款面向本地设备的图片/视频浏览应用（Android，Kotlin + Jetpack Compose）。媒体文件全部存于本地，不上传数据。当前能力边界：文件夹浏览 + 内容网格查看；暂无全屏大图/视频播放器。

- Kotlin 2.0.21 · Compose + Material 3（BOM 2024.12.01）· Gradle 8.13 / AGP 8.13.2（JDK 17）
- minSdk 26 · targetSdk / compileSdk 37

## 构建与验证

```bash
./gradlew :app:assembleDebug
```

未配置 lint / detekt / 单测，日常以 `assembleDebug` 编译通过为准。改代码后必须跑一次。

## 目录结构

```
app/src/main/
├── AndroidManifest.xml              # 权限声明见"权限模型"
├── res/values/                      # themes / strings / colors
└── java/com/solace/app/
    ├── SolaceApplication.kt         # 提供 Coil ImageLoader（自定义 Fetcher + 禁磁盘缓存）
    ├── MainActivity.kt              # 入口；PermissionGate 权限门 + 设置页/主界面切换
    ├── data/
    │   ├── MediaModels.kt           # MediaItem / MediaFolder / MediaType / FolderSort / MediaFilter
    │   └── MediaRepository.kt       # MediaStore 查询：文件夹汇总 + 分页取内容
    ├── ui/
    │   ├── MediaAccess.kt           # MediaAccess 枚举 + 权限检测与权限数组
    │   ├── MediaViewModel.kt        # StateFlow 状态；loadFolders / 分页 loadMore
    │   ├── Screens.kt               # 根组合 + 文件夹列表(行/卡片) / 媒体网格 / 缩略图
    │   └── SettingsScreen.kt        # 设置页（当前仅"媒体权限"）
    └── util/
        └── MediaThumbnailFetcher.kt # Coil 自定义 Fetcher：接系统缩略图缓存
```

依赖版本集中在 `gradle/libs.versions.toml`（Version Catalog）。

## 主要功能与实现要点

### 1. 按文件夹浏览
- `loadFolders()`：`MediaStore.Files` 单次轻量查询（投影 ID/类型/桶/显示名/日期），按 `BUCKET_ID` 分组统计图片/视频数，每组优先取最新一张图片作缩略图，无图片才取最新视频。不把全部媒体对象载入内存。
- `loadFolderItems(bucketId, offset, limit, sort, filter)`：按 `BUCKET_ID` 过滤，**Bundle 查询参数分页**（`QUERY_ARG_SORT_COLUMNS = [DATE_ADDED, _ID]` 降序 + `QUERY_ARG_LIMIT/OFFSET`），单查询覆盖图片+视频（`MEDIA_TYPE` 1/3）。
- `MediaViewModel`：`PAGE_SIZE = 120`，滚动到底触发 `loadMore()`（`derivedStateOf` 检测 `lastVisible >= total - 6`）。
- 首次进入/权限级别变化：`SolaceApp` 的 `LaunchedEffect(access) { viewModel.load() }` 触发加载；**不要删除**，否则首屏永久转圈。从设置页返回（`access` 未变）不会重跑 load。
- **Android 15 平台行为**：MediaProvider 校验 `sortOrder` token，内联 `LIMIT/OFFSET` 抛 `Invalid token LIMIT`（异常被 `runCatching` 吞掉后表现为"文件夹是空的"）。分页必须走 Bundle 参数，禁止写入 sortOrder 字符串。

### 2. 文件夹列表样式切换
- `MediaUiState.folderGridView`（默认 `false` = 列表）持有状态，`toggleFolderLayout()` 切换；按钮在文件夹页 TopAppBar 动作区（`Icons.AutoMirrored.Filled.ViewList` / `Icons.Filled.GridView`）。
- 列表用 `FolderRow`；网格用 `FolderCard`：缩略图 + 底部叠加文字。
- 网格卡片文字压在缩略图上：白色文字 + `overlayTextShadow` 投影，不用背景色块。
- 文件夹名/数量文案统一走 `mediaCountText(folder)`（"x 张图片 · y 个视频"）。

### 3. 文件夹内容网格
- `LazyVerticalGrid` 紧凑网格，`MediaCell` = `Box` + `MediaThumbnail`（`SubcomposeAsyncImage` 的请求尺寸由布局约束自动解析，不要手动测量传宽）。
- 视频缩略图左下角角标：白色圆环 + 播放三角 + 时长；角标行背景透明（不用色块压底），文字带 `overlayTextShadow`。
- 右上角"更多"（`MoreVert`）两级下拉菜单：第一级显示"排序/展示内容"，进入子级显示标题 + 选项（无返回箭头，点菜单外收起）。排序（时间 = `[DATE_ADDED,_ID]` 降序 / 名称 = `[DISPLAY_NAME,_ID]` 升序 / 大小 = `[SIZE,_ID]` 降序）与展示内容（全部/图片/视频，改 `MEDIA_TYPE` 条件），均走 Bundle 查询参数（见 §1）；切换后重置分页重查，在途旧查询按 `loadGeneration` 丢弃不串页。
- 排序/展示内容**按文件夹独立记忆**（`MediaViewModel.folderPrefs` 以 `bucketId` 为 key，默认"时间 + 全部"）：每个文件夹一份配置，切换文件夹互不影响、返回再进该文件夹仍保留；仅运行期记忆，不做持久化（进程重建回到默认）。

### 4. 缩略图加载（统一入口）
- 一律用 `Screens.kt` 的 `MediaThumbnail`（`SubcomposeAsyncImage`，加载中/失败显示占位图标）。
- 数据源：`MediaThumbnailFetcher` 拦截 MediaStore 的 `content://` Uri → `ContentResolver.loadThumbnail()`（API 29+，系统磁盘缩略图缓存；旧版回退 `Thumbnails.getThumbnail(MINI_KIND)`）。
- **禁止**让 Coil 默认解码原图（直接 `AsyncImage(content://...)` 会解码原图，违背性能原则）；非 MediaStore Uri 在 Factory 返回 null 交回 Coil 默认管道。
- `SolaceApplication` 构建 ImageLoader：注册自定义 Fetcher + `diskCache(null)`（系统已有磁盘缩略图缓存），内存缓存用默认（自带 `onTrimMemory` 收缩与取消）。

### 5. 权限模型
- Manifest 声明：`READ_MEDIA_IMAGES`、`READ_MEDIA_VIDEO`、`READ_MEDIA_VISUAL_USER_SELECTED`（API 34+ 才有"选择照片/视频"，必须声明）、`READ_EXTERNAL_STORAGE`（maxSdk 32）。
- `MediaAccess` 三态：`FULL`（images+videos）/ `PARTIAL`（任一或 VISUAL_USER_SELECTED）/ `NONE`。
- 首次请求 `fullAccessPermissions()`（三权限）；升级全量 `upgradePermissions()`（仅 images+videos）。
- **平台行为（不是 bug，不要"修复"）**：系统对话框选项由 OS 决定——声明了 VISUAL_USER_SELECTED 后，升级时仍可能给出"选择部分"；应用无法静默拿全部权限。
- "选择更多"必须重新请求完整三权限（仅请求已授予的 VISUAL_USER_SELECTED 是 no-op）。
- 权限逻辑集中在 `MediaAccess.kt`，UI 层禁止直接 `checkSelfPermission`。

### 6. 导航
- 无 navigation 库，状态驱动：`PermissionGate` 持有 `showSettings` / `showDialog`。
- 设置页以**覆盖层**叠加在 `SolaceApp` 之上（`Box` 内 `if (showSettings)`），`SolaceApp` 保持常驻组合：从设置返回不重建主界面、不重扫 MediaStore、滚动位置不变。
- 权限级别变化（`access` 改变）时触发重扫刷新，从设置页返回（`access` 未变）不重跑——机制见 §1。
- `loadingFolders` 仅在无数据时显示全屏 Loading；已有数据时在后台静默刷新。

## 代码规范

### 架构
- 分层 `data/` → `ui/` → `util/`。
- **Repository 负责所有 MediaStore/SQLite 访问**；ViewModel 经 Repository 取数，不直接操作 ContentResolver。
- **Composable 只做渲染与事件回调**，不做 IO/业务逻辑；状态收口在 ViewModel 的 `StateFlow`。
- 页面切换用状态布尔/密封类，沿用 `PermissionGate` 模式，不引入 navigation（除非确有必要）。

### Compose
- 可复用入口（`SolaceApp`、`SettingsScreen`）用 `public`；内部组件一律 `private`。
- 用 Material 3；`TopAppBar` 等实验 API 用 `@OptIn(ExperimentalMaterial3Api::class)`。
- 列表/网格用 `LazyColumn` / `LazyVerticalGrid`，含 `key`（网格用 `MediaItem.key = "type_id"`，注意图片/视频 id 命名空间不同会冲突）。
- 图片上的叠加文字统一白色 + `overlayTextShadow`，不用背景色块/遮罩。
- UI 文案直接硬编码中文，不抽 `strings.xml`（当前资源仅 `app_name`）：单语言本地应用，不做 i18n。
- 复用已有实现（缩略图、时长格式化），不重复造轮子。

### 并发
- 查询等 IO 在 `Dispatchers.IO`（`viewModelScope.launch { withContext(Dispatchers.IO) { ... } }`）。

### 依赖与配置
- 新增依赖进 `libs.versions.toml`（Version Catalog），用别名引用，禁止在 `build.gradle.kts` 写裸版本号。
- 保持 JVM 17、`compileSdk/targetSdk = 37`、`minSdk = 26`。

### 修改注意
- 改权限、manifest 后跑一次 build；MediaStore 查询注意 API level 差异（`RELATIVE_PATH`、`loadThumbnail` 等仅 API 29+，旧版用 `getColumnIndex` 判空或回退）。
- 分页禁用 sortOrder 内联 `LIMIT/OFFSET`，一律用 Bundle 参数（原因与表现见 §1）。

## 真机调试（adb）

真机：Android 15 / API 35，1080x2400 / 440dpi，MIUI/HyperOS。

- 装机验证：`./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk` → `am force-stop com.solace.app && am start -n com.solace.app/.MainActivity` → `adb exec-out screencap -p > shot.png`。
- **MIUI 默认禁止 `adb shell input tap`**（INJECT_EVENTS），需开系统设置"USB 调试（安全设置）"才能模拟点击。
- 无法点击时的复现：临时在 `SolaceApp` 加 `LaunchedEffect` 自动打开目标页（如 `openFolder(first)`）→ 复现/验证 → **完成后务必还原临时代码**并重新装机。
- 复现 MediaStore 查询：`adb shell "content query --uri content://media/external/file --projection _id:media_type:bucket_id --where 'media_type=1 OR media_type=3'"`——整条命令用双引号包住，否则 bash 吃掉括号/单引号；`--sort` 同样不接受 LIMIT。
- 权限诊断：`adb shell dumpsys package com.solace.app | grep -E "READ_MEDIA|granted="`。
- 截屏是缩放图（物理 1080x2400），做坐标换算按比例缩放。
