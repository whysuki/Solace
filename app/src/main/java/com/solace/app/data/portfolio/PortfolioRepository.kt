package com.solace.app.data.portfolio

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.solace.app.data.MediaItem
import com.solace.app.data.MediaType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PortfolioSummary(
    val id: Long,
    val name: String,
    val workCount: Int,
    val itemCount: Int,
    val cover: MediaItem?,
)

data class WorkSummary(
    val id: Long,
    val title: String,
    val itemCount: Int,
    val cover: MediaItem?,
)

data class WorkItemsResult(
    val items: List<MediaItem>,
    val missingCount: Int,
)

/**
 * 作品集仓库：Room 为结构权威（名称/顺序/封面/路径），物理目录
 * `getExternalFilesDir(null)/Portfolio/<portfolioId>/<workId>/` 为私有镜像（图片/视频混放）。
 * 文件操作纯 java.io：无 MediaStore 行、无需存储权限；见 docs/portfolio-design.md §4-§6。
 */
class PortfolioRepository(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver
    private val dao: PortfolioDao = PortfolioDatabase.get(context).portfolioDao()

    // ---------- 作品集 ----------

    suspend fun loadPortfolios(): List<PortfolioSummary> = withContext(Dispatchers.IO) {
        val portfolios = dao.portfolios()
        if (portfolios.isEmpty()) return@withContext emptyList()

        val workCounts = dao.workCounts().associate { it.portfolioId to it.count }
        val worksByPortfolio = dao.worksOf(portfolios.map { it.id }).groupBy { it.portfolioId }
        val itemsByWork = portfolios.flatMap { dao.portfolioItems(it.id) }.groupBy { it.workId }

        portfolios.map { p ->
            val works = worksByPortfolio[p.id].orEmpty()
            val firstWork = works.firstOrNull()
            val coverWork = works.firstOrNull { it.id == p.coverWorkId } ?: firstWork
            PortfolioSummary(
                id = p.id,
                name = p.name,
                workCount = workCounts[p.id] ?: 0,
                itemCount = works.sumOf { itemsByWork[it.id]?.size ?: 0 },
                cover = coverWork?.let { coverItemOf(it.id, itemsByWork[it.id].orEmpty()) },
            )
        }
    }

    suspend fun createPortfolio(name: String): Long = withContext(Dispatchers.IO) {
        dao.insertPortfolio(
            PortfolioEntity(
                name = name,
                sortOrder = (dao.maxPortfolioSort() ?: -1L) + 1L,
            )
        )
    }

    suspend fun renamePortfolio(id: Long, newName: String) = withContext(Dispatchers.IO) {
        val entity = dao.portfolio(id) ?: return@withContext
        // 目录按 DB id 命名，重命名仅改 DB、不动磁盘。
        dao.updatePortfolio(entity.copy(name = sanitizeName(newName)))
    }

    suspend fun deletePortfolio(id: Long) = withContext(Dispatchers.IO) {
        if (dao.portfolio(id) == null) return@withContext
        dao.deletePortfolio(id) // 级联删除 works / work_items
        runCatching { portfolioDir(id).deleteRecursively() }
    }

    // ---------- 作品 ----------

    suspend fun loadWorks(portfolioId: Long): List<WorkSummary> = withContext(Dispatchers.IO) {
        val works = dao.works(portfolioId)
        val itemsByWork = dao.portfolioItems(portfolioId).groupBy { it.workId }
        works.map { w ->
            val its = itemsByWork[w.id].orEmpty()
            WorkSummary(
                id = w.id,
                title = w.title,
                itemCount = its.size,
                cover = coverItemOf(w.id, its),
            )
        }
    }

    suspend fun createWork(portfolioId: Long, title: String): Long = withContext(Dispatchers.IO) {
        dao.insertWork(
            WorkEntity(
                portfolioId = portfolioId,
                title = sanitizeName(title),
                sortOrder = (dao.maxWorkSort(portfolioId) ?: -1L) + 1L,
            )
        )
    }

    suspend fun renameWork(id: Long, newTitle: String) = withContext(Dispatchers.IO) {
        val entity = dao.work(id) ?: return@withContext
        // 目录按 DB id 命名，重命名仅改 DB、不动磁盘。
        dao.updateWork(entity.copy(title = sanitizeName(newTitle)))
    }

    suspend fun deleteWork(id: Long) = withContext(Dispatchers.IO) {
        val entity = dao.work(id) ?: return@withContext
        dao.deleteWork(id) // 级联删除 work_items
        runCatching { workDir(entity.portfolioId, entity.id).deleteRecursively() }
        // 作品集目录可能已空，顺手清理（非空则保留，失败无妨）。
        runCatching { portfolioDir(entity.portfolioId).delete() }
    }

    // ---------- 素材 ----------

    suspend fun loadWorkItems(workId: Long): WorkItemsResult = withContext(Dispatchers.IO) {
        val refs = dao.items(workId)
        if (refs.isEmpty()) return@withContext WorkItemsResult(emptyList(), 0)

        // 素材由存储列 + 文件存在性直接构造，不回查 MediaStore。
        var missing = 0
        val items = refs.mapNotNull { ref ->
            val type = ref.type.toMediaType()
            val file = File(ref.relativePath)
            if (type == null || !file.exists()) {
                missing++
                null
            } else {
                MediaItem(
                    id = ref.id, // work_items.id 为素材标识（MediaItem.key = "type_id" 唯一）
                    name = ref.displayName,
                    uri = Uri.fromFile(file),
                    type = type,
                    mimeType = ref.mimeType,
                    durationMs = ref.durationMs,
                    dateAdded = 0,
                    dateTaken = ref.dateTaken,
                    size = ref.size,
                    path = file.absolutePath,
                )
            }
        }
        WorkItemsResult(items, missing)
    }

    /**
     * 复制一个媒体文件到指定作品（源 MediaStore 行 → 私有目录文件拷贝），返回结果消息
     * （成功 null 之外的字符串）。素材仅经「新建作品」流程进入（作品恒新建、选择器单选唯一，
     * 无重复添加场景）。私有目录读写无需存储权限。
     */
    suspend fun addMedia(workId: Long, source: MediaItem): String? = withContext(Dispatchers.IO) {
        val work = dao.work(workId) ?: return@withContext "作品不存在"
        val portfolio = dao.portfolio(work.portfolioId) ?: return@withContext "作品集不存在"

        val dir = workDir(portfolio.id, work.id)
        try {
            if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("创建目录失败")
            val name = uniqueName(source.name, existingNamesInDir(dir))
            val file = copyFile(source, dir, name)
            dao.insertItem(
                WorkItemEntity(
                    workId = workId,
                    type = source.type.name,
                    sortOrder = (dao.maxItemSort(workId) ?: -1L) + 1L,
                    displayName = name,
                    relativePath = file.absolutePath,
                    mimeType = source.mimeType,
                    size = file.length(),
                    durationMs = source.durationMs,
                    dateTaken = source.dateTaken,
                )
            )
            null
        } catch (e: Exception) {
            "复制失败：${e.message ?: "未知错误"}"
        }
    }

    // ---------- 私有目录文件操作 ----------

    /** 作品集私有根：应用专属外部目录，不可用时回退内部存储；与 MediaStore 无关。 */
    private val privateRoot: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "Portfolio")

    private fun portfolioDir(portfolioId: Long): File = File(privateRoot, portfolioId.toString())

    private fun workDir(portfolioId: Long, workId: Long): File =
        File(portfolioDir(portfolioId), workId.toString())

    /** 流式复制到私有目录：先写 `.tmp` 再原子改名落位；失败清理残留并抛出。 */
    private fun copyFile(source: MediaItem, dir: File, displayName: String): File {
        val target = File(dir, displayName)
        val tmp = File(dir, "$displayName.tmp")
        try {
            resolver.openInputStream(source.uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("源文件不可读")
            if (!tmp.renameTo(target)) throw IllegalStateException("文件落位失败")
        } catch (e: Exception) {
            runCatching { tmp.delete() }
            throw e
        }
        return target
    }

    /** 目标目录已有的显示名（文件系统直接列举）。 */
    private fun existingNamesInDir(dir: File): Set<String> =
        dir.listFiles()?.mapTo(HashSet()) { it.name } ?: emptySet()

    private fun uniqueName(original: String, existing: Set<String>): String {
        if (original !in existing) return original
        val dot = original.lastIndexOf('.')
        val base = if (dot > 0) original.substring(0, dot) else original
        val ext = if (dot > 0) original.substring(dot) else ""
        var i = 1
        while (true) {
            val candidate = "$base ($i)$ext"
            if (candidate !in existing) return candidate
            i++
        }
    }

    private fun coverItemOf(workId: Long, items: List<WorkItemEntity>): MediaItem? {
        val first = items.minByOrNull { it.sortOrder } ?: return null
        val type = first.type.toMediaType() ?: return null
        val file = File(first.relativePath)
        if (!file.exists()) return null
        return MediaItem(
            id = first.id,
            name = first.displayName,
            uri = Uri.fromFile(file),
            type = type,
            mimeType = first.mimeType,
            path = file.absolutePath,
        )
    }

    private fun String.toMediaType(): MediaType? = runCatching { MediaType.valueOf(this) }.getOrNull()

    private fun sanitizeName(name: String): String =
        name
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
            .trim()
            .take(100)
            .ifBlank { "未命名" }
}
