# 作品集（Portfolio）功能设计方案

> 状态：**已实现（2026-09-06）**。本文档记录作品集功能的具体设计；代码实现细节与约束以 AGENTS.md 为准。

## 1. 背景与目标

Solace 当前是"按文件夹浏览本地媒体"的应用（AGENTS.md §功能特性）。本次引入**作品集**概念，作为应用的核心独立特性：

- 作品集（Portfolio）是用户自定义的管理单位，近似文件夹，但由用户创建和编排；
- 作品（Work）是作品集下的一条"作品"：一个视频、一张图片，或多张图片/多个短视频的组合；
- 素材（Item）是作品内的实际媒体文件（图片/视频），从本地文件复制入库；
- 作品集是**应用私有的创作空间**：素材副本保存在应用专属私有目录（`Android/data/<包名>/files/Portfolio`），仅本应用可见，**系统相册与第三方应用不可见**；`allowBackup=false`，不参与云备份；数据不上传；
- 「本地文件」只读浏览不组织，「作品集」是应用的管理特性，两者相互独立。

设计参考抖音作品管理语义：抖音主页的"作品"= 一条独立动态（单视频/单图/图文合集），"合集"= 自定义分组。对应关系见 §2。

## 2. 概念模型

| 层级 | 中文 | 代码术语 | 说明 |
|---|---|---|---|
| 一层 | 作品集 | `Portfolio` | 用户自定义容器，含名称、作品顺序、封面 |
| 二层 | 作品 | `Work` | 一条作品：单视频 / 单图 / 多图、多短视频组合；含标题、素材顺序、封面 |
| 三层 | 素材 | `WorkItem` | 作品内的私有目录文件（图片/视频），元数据入库 |

抖音语义映射：

| 抖音 | Solace |
|---|---|
| 你的主页/作品列表 | 作品集列表（默认按最近创建排序） |
| 一条作品（动态） | 作品 = 一个子文件夹（即使单媒体也是文件夹） |
| 合集 | 作品集 |
| 作品封面 | 默认 = 第一个素材（`coverItemId` 自定义未实现，见 §9） |
| 合集封面 | 默认 = 第一个作品的封面（`coverWorkId` 自定义未实现，见 §9） |

## 3. 总体架构

```
MainActivity
└─ PermissionGate（权限门不变，两个 Tab 都位于门内）
   └─ SolaceApp（Material 3 NavigationBar，两个目的地）
       ├─ Tab「图库」= 现有本地文件浏览（文件夹 → 网格 → 全屏查看器，只读）
       └─ Tab「作品集」= 作品集列表 → 作品网格 → 作品详情 feed
```

- 权限门不动：素材选取来源在本地媒体（MediaStore），Tab 共用一套媒体读权限。
- 切换 Tab 保持各自状态（滚动位置、层级）：`rememberSaveableStateHolder()` 包裹两个 Tab；作品详情 feed 与「新建作品」流程为 `SolaceApp` 层全屏覆盖层，超出底部导航。
- 与现有「设置页覆盖层」模式共存：覆盖层语义不变。

## 4. 物理目录布局（app 私有，project 式）

```
/storage/emulated/0/Android/data/com.solace.app/files/Portfolio/   ← 私有根
│  （getExternalFilesDir(null)；外置存储不可用时回退 filesDir/Portfolio）
├── 1/                          ← portfolioId（DB id，与名称解耦）
│   ├── 2/                      ← workId
│   │   ├── IMG_0001.jpg          （多图作品：目录内多个文件）
│   │   └── IMG_0002.jpg
│   ├── 3/                      ← 单视频作品
│   │   └── VID_0001.mp4
└── 4/
```

- **私有目录由 OS 隔离**（Android 11+ 强制），系统相册/第三方应用不可见——产品意图；读写无需任何存储权限（全 API 级别）；MediaStore 中无作品集行，「本地文件」/素材选择器天然看不到作品集内容。
- **目录按 DB id 命名，与名称解耦**：重命名仅改 DB、不动磁盘；同名作品集/作品结构上不可能合并目录；删除按 id 稳定定位。
- 素材文件保持源名、**不加序数前缀**；重名时追加 " (n)" 后缀（`uniqueName`，基于目录列举）；顺序完全由 DB `sortOrder` 决定，文件名只做载体。
- **每个作品永远一个子文件夹**（单媒体作品也是文件夹内放一个文件）：DB 与目录 1:1，导入/校验/清理逻辑统一。
- 布局以 DB 为权威，目录为物理镜像；作品集/作品名称仅入库，经 `sanitizeName()` 清理（替换非法字符/截断 100 字符/空回退"未命名"），不参与路径拼装。

## 5. 数据模型（Room）

为什么需要数据库：作品集/作品/素材的**用户顺序、自定义命名、素材分组关系**无法可靠地由目录/文件名编码（文件名前缀编码顺序是脆弱方案；私有目录更是无索引可言）。Room 三张表为结构权威：

```sql
portfolios (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  name          TEXT NOT NULL,
  coverWorkId   INTEGER NULL,     -- 自定义封面；NULL = 取第一个作品封面（未实现自定义）
  sortOrder     INTEGER NOT NULL,
  createdAt     INTEGER NOT NULL
)

works (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  portfolioId   INTEGER NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
  title         TEXT NOT NULL,
  coverItemId   INTEGER NULL,     -- 自定义封面；NULL = 取第一个素材（未实现自定义）
  sortOrder     INTEGER NOT NULL,
  createdAt     INTEGER NOT NULL
)

work_items (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  workId        INTEGER NOT NULL REFERENCES works(id) ON DELETE CASCADE,
  type          TEXT NOT NULL,        -- IMAGE / VIDEO
  sortOrder     INTEGER NOT NULL,
  displayName   TEXT NOT NULL,
  relativePath  TEXT NOT NULL,        -- 素材绝对文件路径
  mimeType      TEXT,                 -- 写入时冗余：展示/封面免回查
  size          INTEGER,
  durationMs    INTEGER,
  dateTaken     INTEGER
)
```

> 注：Room 列名即实体驼峰字段（无 `@ColumnInfo`），与上表一致。

要点：

- `work_items` **自包含**：展示所需（名称/类型/尺寸/时长/拍摄时间/路径）全部入库，不回查 MediaStore；素材存在性由 `File.exists()` 判定（私有目录仅应用可改，外部删除竞态基本消除）。
- 删除作品/作品集：DB 外键级联 + 目录 `deleteRecursively`（尽力而为，失败无妨——DB 权威）。
- 依赖：`androidx.room` runtime + KSP compiler，版本入 `gradle/libs.versions.toml`。

## 6. 移动 / 复制实现

统一走"复制"管道（**无 MediaStore 行、无系统确认、无额外权限**，全程 `Dispatchers.IO`）：

1. 确保目标目录 `<私有根>/<portfolioId>/<workId>/` 存在（`mkdirs`）；
2. 源 Uri（MediaStore `content://`）`openInputStream` 流式写入 `<源文件名>.tmp`；
3. 同目录 `renameTo` **原子落位**；失败清理 `.tmp` 并经 snackbar 提示"复制失败"；
4. 插入 `work_items` 行（displayName + 绝对路径 + 冗余列 + `sortOrder` 追加）。

- 重名去重：目录列举 + `uniqueName`（" (1)" 后缀）；同一作品内文件名唯一。
- 重命名（作品集/作品）= **仅改 DB**；删除 = DB 级联 + 目录删除（空父目录顺手清理，失败无妨）。
- 「移动」（删原件）未实现，当前仅复制——素材永远留存原件，删原件属未实现边界（§9）。

## 7. 导航决策

**不引入 navigation 库**，沿用 AGENTS.md「页面切换用状态布尔/密封类」约定：

- 两个 Tab 各持自己的导航状态：本地文件 Tab 沿用 `MediaViewModel`（文件夹/网格/全屏为状态切换）；作品集 Tab 为「作品集列表 → 作品网格 → 作品详情 feed」状态驱动（`selectedPortfolio` / `selectedWork` + 全屏覆盖层，`BackHandler` 覆盖系统返回）。
- 理由：底部导航 + 每 Tab 独立栈恰是 navigation-compose 的难点（嵌套 NavHost/子图 + 状态恢复 + popUpTo 语义），库并不简化该设计；且现有覆盖层（设置页、文件详情、倍速/音量面板、新建作品流程）是「层级」而非「目的地」，不适合进 NavHost；当前无深链需求、进程重建恢复导航位置暂可接受。
- 重新评估引入的条件：出现深链需求；明确要求进程重建后恢复导航位置；界面数量显著膨胀（每 Tab 十几层）。

## 8. UI 接入

- **主界面**：底部 NavigationBar 两个目的地（图库 / 作品集），`SolaceApp` 为根；切换用 `rememberSaveableStateHolder` 保持状态。
- **作品集页**：作品集列表（名称 + 封面 + 数量）→ 作品网格（封面 + 标题 + "更多"菜单，用户顺序）→ 作品详情 feed。
- **新建作品**：作品网格「+」→ 两步全屏流程——① 选素材（`MediaRepository.loadRecentItems` 按时间倒序 Bundle 分页、全部/图片/视频筛选、多选按点击顺序编号、点已选项取消、header「取消选中」清空）→ ② 命名发布（名称非空可发布；名称下方横滑预览：图片按比例显示、视频解码帧 + 播放角标 + 时长，点击播放（单播放器 `playingKey`）、翻页即卸载释放；黑底圆角统一裁剪在预览容器上；页码「n / N」）；发布 = 创建作品 + 按选择顺序批量复制（失败计数 + snackbar）。
- **作品浏览**：独立抖音式双轴 feed（`WorkFeedPlayer`：纵向切作品 × 横向切素材，视频自动循环播放，底部常驻作品名 + 进度条/位置横线）——`FullScreenPlayer` 仅泛化为 `items + onLoadMore` 后仍专用于本地文件，两者不可混用，交互规则见 AGENTS.md §8。
- 素材加载：uri 为 `file://`（`Uri.fromFile`）。Coil 自定义 Fetcher 只拦 MediaStore `content://`，`file://` 自动放行**默认管道**：图片按目标尺寸降采样（内存缓存兜底）、视频帧走已注册的 `VideoFrameDecoder`（coil-video）；**无系统缩略图磁盘缓存**，网格滚动性能略逊于本地文件页——接受现状（file:// 专用 Fetcher 列为后续可选优化）。
- 缩略图显示统一走 `MediaThumbnail` + ≤1024px 门控，无需改动。
- 结构：`data/portfolio/`（Room DAO + `PortfolioRepository` + 文件操作）；组合在 `ui/` 根部（`PortfolioScreens.kt`、`WorkFeedPlayer.kt`）+ `PortfolioViewModel`（素材按需缓存、选择器状态）。

## 9. 边界与待验证项

**设计边界（未实现，勿误以为存在）**：作品/作品集拖拽排序（sortOrder 为追加式）、自定义封面（默认第一个素材）、「移动」模式、向已有作品追加素材（素材仅经新建作品流程进入）、作品内素材单删/重排、作品集导出/备份入口（私有存储后用户无法直接拷贝文件）、作品 feed 内文件详情弹层。

| 项 | 说明与状态 |
|---|---|
| 外部删除文件 | 私有目录仅应用可改 → 几乎不会发生；仍保留 `File.exists()` 缺失判定 → feed 显示缺失/空态，不崩溃 ✅ |
| 缩略图性能 | 默认解码 + 内存缓存（无系统磁盘缓存），滚动性能接受现状；file:// 专用 Fetcher 列为后续可选优化 ⚠️ |
| 备份策略 | `android:allowBackup="false"`：作品集不参与云备份、无网络上传；卸载 = 作品集数据整体清除（与"私有创作空间"意图一致） |
| 私有根回退 | `getExternalFilesDir(null)` 为 null（外置存储移除等）→ 回退 `filesDir`；内部存储对大视频有空间上限 ⚠️ |
| 动图（GIF） | `MEDIA_TYPE_IMAGE`；动画支持未验证 |
