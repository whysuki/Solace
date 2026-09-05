# AGENTS.md

面向本仓库的智能体开发指引。新会话先读本文档，再动代码。

## 文档维护规则

本文档是**活规范**，与实现同步维护——代码改动涉及功能、约束或结构时，必须同步更新对应小节。约定：

- 功能变化（新增/删除/变更功能）→ 更新「功能特性」与对应「实现要点」小节。
- 约束变化（权限、依赖、API level、分页方式等）→ 更新「代码规范」及相应小节。
- 结构变化（文件/包增删改）→ 更新「目录结构」。
- 只记意图、约束与坑（平台行为、资源管理、交互规则等）；实现机制、状态机细节与 UI 明细参数（dp 值、列数、间距等）以代码为准。
- 同一规则只保留一个权威位置，其余处用「见 §n」交叉引用，避免只改一处导致互相矛盾。
- 文档描述**当前态**，不是变更日志：失效条目随变化删除或改写，不留"历史存档"式内容——过时的指引比没有指引更有害。

## 项目简介

Solace 是一款面向本地设备的图片/视频浏览应用（Android，Kotlin + Jetpack Compose）。媒体文件全部存于本地，不上传数据。已实现功能见「功能特性」。

## 技术栈与版本

- Kotlin 2.0.21 · Compose + Material 3（BOM 2024.12.01）· Gradle 8.13 / AGP 8.13.2（JDK 17）
- minSdk 26 · targetSdk / compileSdk 37
- 依赖（含 Coil 2.7.0、media3 1.5.1）集中在 `gradle/libs.versions.toml`（Version Catalog）。

## 功能特性

当前能力边界；**未列出的功能视为不存在**，新增功能后须按「文档维护规则」更新本节。

- 文件夹浏览：MediaStore 汇总按文件夹分组；列表/网格两种视图切换。
- 内容网格查看：分页加载；排序（时间/名称/大小）与展示内容（全部/图片/视频），按文件夹独立记忆。
- 全屏大图/视频播放：横向分页滑动切换；视频支持时间轴 / 倍速 / 音量，进入不自动播放。
- 媒体权限引导：首次授权 + 设置页「允许全部 / 选择更多」。

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
    │   ├── FullScreenPlayer.kt      # 全屏大图/视频播放器（横向分页 + ExoPlayer 控制）
    │   └── SettingsScreen.kt        # 设置页（当前仅"媒体权限"）
    └── util/
        └── MediaThumbnailFetcher.kt # Coil 自定义 Fetcher：接系统缩略图缓存
```

## 主要功能与实现要点

### 1. 按文件夹浏览
- `loadFolders()`：`MediaStore.Files` 单次轻量查询，按 `BUCKET_ID` 分组统计图片/视频数，每组优先取最新一张图片作缩略图，无图片才取最新视频。SQL 按 `DATE_ADDED DESC` 排序的目的就是挑最新缩略图，列表最终在 Kotlin 内按名称 `sortedBy`——不要随意改动这条排序。不把全部媒体对象载入内存。
- `loadFolderItems(bucketId, offset, limit, sort, filter)`：按 `BUCKET_ID` 过滤，**Bundle 查询参数分页**（`QUERY_ARG_SORT_COLUMNS/SORT_DIRECTION` 按当前排序、默认时间降序，加 `QUERY_ARG_LIMIT/OFFSET`；排序选项见 §3），单查询覆盖图片+视频（`MEDIA_TYPE` 1/3）。
- `MediaViewModel`：分页大小见 `PAGE_SIZE` 常量；滚动到底触发 `loadMore()`（`derivedStateOf` 检测 `lastVisible` 接近总数）。
- 首次进入/权限级别变化：`SolaceApp` 的 `LaunchedEffect(access) { viewModel.load() }` 触发加载；**不要删除**，否则首屏永久转圈。从设置页返回（`access` 未变）不会重跑 load。
- **Android 15 平台行为**：MediaProvider 校验 `sortOrder` token，内联 `LIMIT/OFFSET` 抛 `Invalid token LIMIT`（异常被 `runCatching` 吞掉后表现为"文件夹是空的"）。分页必须走 Bundle 参数，禁止写入 sortOrder 字符串。

### 2. 文件夹列表样式切换
- `MediaUiState.folderGridView`（默认 `false` = 列表）持有状态，`toggleFolderLayout()` 切换；按钮在文件夹页 TopAppBar 动作区。
- 列表用 `FolderRow`；网格用 `FolderCard`：缩略图 + 底部叠加文字。
- 网格卡片文字压在缩略图上：叠加文字样式见「代码规范 · Compose」。
- 文件夹名/数量文案统一走 `mediaCountText(folder)`（"x 张图片 · y 个视频"）。

### 3. 文件夹内容网格
- `LazyVerticalGrid` 紧凑网格，`MediaCell` = `Box` + `MediaThumbnail`；缩略图请求尺寸由布局约束自动解析，**不要手动测量传宽**。
- 视频缩略图左下角角标：白色圆环 + 播放三角 + 时长；角标行背景透明（不用色块压底），文字叠加样式见「代码规范 · Compose」。
- 右上角"更多"（`MoreVert`）两级下拉菜单：第一级显示"排序/展示内容"，进入子级显示标题 + 选项（无返回箭头，点菜单外收起）。排序（时间/名称/大小）与展示内容（全部/图片/视频，改 `MEDIA_TYPE` 条件）均走 Bundle 查询参数（见 §1）；切换后重置分页重查，在途旧查询按 `loadGeneration` 丢弃不串页。
- 排序/展示内容**按文件夹独立记忆**（`MediaViewModel.folderPrefs` 以 `bucketId` 为 key，默认"时间 + 全部"）：每个文件夹一份配置，切换文件夹互不影响、返回再进该文件夹仍保留；仅运行期记忆，不做持久化（进程重建回到默认）。

### 4. 缩略图加载（统一入口）
- 一律用 `Screens.kt` 的 `MediaThumbnail`（`SubcomposeAsyncImage`，加载中/失败显示占位图标）。
- 数据源：`MediaThumbnailFetcher` 拦截 MediaStore 的 `content://` Uri → `ContentResolver.loadThumbnail()`（API 29+，系统磁盘缩略图缓存；旧版回退 `Thumbnails.getThumbnail(MINI_KIND)`）。
- **缩略图命中门控**：Factory 仅当请求目标尺寸两端都 ≤ `MAX_THUMBNAIL`(1024px) 才走系统缩略图缓存；`Size.ORIGINAL` 或任一维度不可解析为像素时，返回 null 交回 Coil 默认管道。这样网格缩略图走缓存，而**全屏查看**的大图请求落到 Coil 默认解码（按屏幕目标尺寸降采样，不整图解码，内存可控）。
- **禁止**让 Coil 默认解码原图（直接 `AsyncImage(content://...)` 会解码原图，违背性能原则）；非 MediaStore Uri 在 Factory 返回 null 交回 Coil 默认管道。
- `SolaceApplication` 构建 ImageLoader：注册自定义 Fetcher + `diskCache(null)`（系统已有磁盘缩略图缓存），内存缓存用默认（自带 `onTrimMemory` 收缩与取消）。

### 5. 权限模型
- Manifest 声明：`READ_MEDIA_IMAGES`、`READ_MEDIA_VIDEO`、`READ_MEDIA_VISUAL_USER_SELECTED`（API 34+ 才有"选择照片/视频"，必须声明）、`READ_EXTERNAL_STORAGE`（maxSdk 32）。
- `MediaAccess` 三态：`FULL`（images+videos）/ `PARTIAL`（任一或 VISUAL_USER_SELECTED）/ `NONE`。**PARTIAL 仅 API 34+ 存在**（有 `READ_MEDIA_VISUAL_USER_SELECTED` 才有"部分授权"）；API 33 只有 FULL/NONE 两态，images+videos 未齐授即 `NONE`，没有"选择更多"入口。
- 首次请求 `fullAccessPermissions()`（三权限）；升级全量 `upgradePermissions()`（仅 images+videos）。
- **平台行为（不是 bug，不要"修复"）**：系统对话框选项由 OS 决定——声明了 VISUAL_USER_SELECTED 后，升级时仍可能给出"选择部分"；应用无法静默拿全部权限。
- "选择更多"必须重新请求完整三权限（仅请求已授予的 VISUAL_USER_SELECTED 是 no-op）。
- 权限逻辑集中在 `MediaAccess.kt`，UI 层禁止直接 `checkSelfPermission`。

### 6. 导航
- 无 navigation 库，状态驱动：`PermissionGate` 持有 `showSettings` / `showDialog`。
- 设置页以**覆盖层**叠加在 `SolaceApp` 之上（`Box` 内 `if (showSettings)`；仅 FULL/PARTIAL 有 `SolaceApp`，NONE 只显示权限引导页），`SolaceApp` 保持常驻组合：从设置返回不重建主界面、不重扫 MediaStore、滚动位置不变。
- 权限级别变化（`access` 改变）时触发重扫刷新，从设置页返回（`access` 未变）不重跑——机制见 §1。
- `loadingFolders` 仅在无数据时显示全屏 Loading；已有数据时在后台静默刷新。

### 7. 全屏大图/视频播放器
- 图片页：`SubcomposeAsyncImage` + `ContentScale.Fit`，走 Coil 默认解码（见 §4 门控）按屏幕尺寸显示；`HorizontalPager` 横向滑动切换当前文件夹内已加载对象，预载相邻页；滑到接近末尾时触发 `onLoadMore()`（复用网格分页），实现一直往后滑。
- 视频页：每页一个 `ExoPlayer`（依赖见「代码规范 · 依赖与配置」），`PlayerView(useController = false)` 仅作画面；**进入不自动播放**——点海报或播放键才开始；仅离开当前页时 `pause()`，跟随 `Lifecycle`（ON_STOP 暂停 / ON_START 不自动恢复），页面 `onDispose` 释放播放器；仅当前页及相邻页各持有一个播放器，资源可控。
- 顶部栏：左/中/右为返回、文件名胶囊、位置（`当前/总数`）；文件名胶囊展示去扩展名文件名（仅按最后一个点截断，`.nomedia` 之类隐藏文件保留），点击弹出「文件详情」浮层（文件名含后缀/路径/大小/拍摄时间，不可用显示"未知"），翻页自动收起。
- 视频控制条：单行**胶囊形浮动面板**（白底 + 深色控件，贴近 iOS 风格），行内为播放/时间轴/倍速/音量；时间轴为自绘 `Scrubber`，**仅拖动时**上方弹出 `当前 / 总时长`，松开即收起；倍速/音量/进度展开面板**各自是独立卡片**，避免胶囊被拉高后圆角过度裁剪内容。
- 面板交互：倍速/音量两面板**互斥**展开（点已展开的按钮再收起）；点击倍速面板**任一**选项即收起（含当前倍速）；音量在拖动**结束后**才收起——不能中途收起，否则滑杆消失、音量无法继续调节；进度数字不属于展开面板、仅拖动时间轴时显示；控件显隐由点击画面空白处切换（未开始前点击=播放，控件显示时白底、隐藏时黑色沉浸），**无自动隐藏**；`BackHandler` 覆盖系统返回。

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
- 视频播放用 `androidx.media3:media3-exoplayer` + `media3-ui`（`ExoPlayer` / `PlayerView`）；版本在 `libs.versions.toml` 的 `media3`。
- 保持 JVM 17、`compileSdk/targetSdk = 37`、`minSdk = 26`。

### 修改注意
- 改权限、manifest 后跑一次 build；MediaStore 查询注意 API level 差异：`RELATIVE_PATH`、`loadThumbnail`、`Files.getContentUri(volumeName)`/`VOLUME_EXTERNAL` 等仅 API 29+。列读取与缩略图已做旧版回退，但查询入口（`getContentUri(VOLUME_EXTERNAL)`）目前**未**做 API 26-28 回退——若要支持 Android 10 以下设备，这是整体改动，不要顺手补半边。
- 分页禁用 sortOrder 内联 `LIMIT/OFFSET`，一律用 Bundle 参数（原因与表现见 §1）。

## 真机调试（adb）

真机：Android 15 / API 35，1080x2400 / 440dpi，MIUI/HyperOS。

- 装机验证：`./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk` → `am force-stop com.solace.app && am start -n com.solace.app/.MainActivity` → `adb exec-out screencap -p > shot.png`。
- **MIUI 默认禁止 `adb shell input tap`**（INJECT_EVENTS），需开系统设置"USB 调试（安全设置）"才能模拟点击。
- 无法点击时的复现：临时在 `SolaceApp` 加 `LaunchedEffect` 自动打开目标页（如 `openFolder(first)`）→ 复现/验证 → **完成后务必还原临时代码**并重新装机。
- 复现 MediaStore 查询：`adb shell "content query --uri content://media/external/file --projection _id:media_type:bucket_id --where 'media_type=1 OR media_type=3'"`——整条命令用双引号包住，否则 bash 吃掉括号/单引号；`--sort` 同样不接受 LIMIT。
- 权限诊断：`adb shell dumpsys package com.solace.app | grep -E "READ_MEDIA|granted="`。
- 截屏是缩放图（物理 1080x2400），做坐标换算按比例缩放。
