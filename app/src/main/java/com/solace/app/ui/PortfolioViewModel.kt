package com.solace.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.solace.app.data.MediaFilter
import com.solace.app.data.MediaItem
import com.solace.app.data.MediaRepository
import com.solace.app.data.portfolio.PortfolioRepository
import com.solace.app.data.portfolio.PortfolioSummary
import com.solace.app.data.portfolio.WorkSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 单个作品的素材加载状态（作品详情 feed 按需缓存 + 邻位预载）。 */
data class WorkItemsState(
    val items: List<MediaItem> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val missingCount: Int = 0,
)

data class PortfolioUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val portfolios: List<PortfolioSummary> = emptyList(),
    val selectedPortfolio: PortfolioSummary? = null,
    val works: List<WorkSummary> = emptyList(),
    val selectedWork: WorkSummary? = null,
    val workItems: Map<Long, WorkItemsState> = emptyMap(),
    // 「新建作品」流程（选素材 → 命名发布）的素材选择器数据。
    val pickerItems: List<MediaItem> = emptyList(),
    val pickerLoading: Boolean = false,
    val pickerEndReached: Boolean = false,
    val pickerFilter: MediaFilter = MediaFilter.ALL,
    val busy: Boolean = false,
    val message: String? = null,
)

/**
 * 作品集 ViewModel：作品集/作品 CRUD + 作品详情 feed 素材按需缓存 +
 * 「新建作品」两步流程（选素材 → 命名发布）的素材选择器状态。
 */
class PortfolioViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PortfolioRepository(application)
    private val mediaRepository = MediaRepository(application)
    private val _state = MutableStateFlow(PortfolioUiState())
    val state: StateFlow<PortfolioUiState> = _state.asStateFlow()

    /** 选择器翻页代数：切换筛选/重开流程后丢弃在途旧页，避免串页。 */
    private var pickerGeneration = 0

    /** 作品素材缓存代数：失效（增删素材等）后丢弃在途旧查询，避免旧数据重新入缓存。 */
    private val workItemsGeneration = mutableMapOf<Long, Int>()

    /** 在途加载中的作品素材集合，避免重复发起查询。 */
    private val loadingWorkIds = mutableSetOf<Long>()

    fun load() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { repository.loadPortfolios() }
            }.fold(
                onSuccess = { portfolios ->
                    _state.update {
                        it.copy(loading = false, error = null, portfolios = portfolios)
                    }
                    refreshOpened()
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(loading = false, error = e.message ?: "加载失败")
                    }
                },
            )
        }
    }

    // ---------- 作品集 ----------

    fun createPortfolio(name: String) {
        launchBusy {
            repository.createPortfolio(name)
            null
        }
    }

    fun renamePortfolio(id: Long, name: String) {
        launchBusy {
            repository.renamePortfolio(id, name)
            null
        }
    }

    fun deletePortfolio(id: Long) {
        launchBusy {
            repository.deletePortfolio(id)
            null
        }
    }

    fun openPortfolio(summary: PortfolioSummary) {
        _state.update { it.copy(selectedPortfolio = summary, selectedWork = null, workItems = emptyMap()) }
        workItemsGeneration.clear()
        loadWorks()
    }

    fun backFromPortfolio() {
        _state.update {
            it.copy(selectedPortfolio = null, selectedWork = null, works = emptyList(), workItems = emptyMap())
        }
        workItemsGeneration.clear()
        load()
    }

    // ---------- 作品 ----------

    private fun loadWorks() {
        val portfolioId = _state.value.selectedPortfolio?.id ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { repository.loadWorks(portfolioId) }
            }.fold(
                onSuccess = { works ->
                    _state.update { it.copy(works = works) }
                },
                onFailure = { e ->
                    _state.update { it.copy(error = e.message ?: "加载失败") }
                },
            )
        }
    }

    fun renameWork(id: Long, title: String) {
        launchBusy {
            repository.renameWork(id, title)
            null
        }
    }

    fun deleteWork(id: Long) {
        launchBusy {
            repository.deleteWork(id)
            invalidateWorkItems(id)
            null
        }
    }

    // ---------- 「新建作品」流程（选素材 → 命名发布） ----------

    fun openCreateFlow() {
        pickerGeneration++
        _state.update {
            it.copy(
                pickerItems = emptyList(),
                pickerLoading = true,
                pickerEndReached = false,
                pickerFilter = MediaFilter.ALL,
            )
        }
        loadPickerPage()
    }

    fun closeCreateFlow() {
        pickerGeneration++
        _state.update {
            it.copy(pickerItems = emptyList(), pickerLoading = false, pickerEndReached = false)
        }
    }

    fun setPickerFilter(filter: MediaFilter) {
        if (_state.value.pickerFilter == filter) return
        pickerGeneration++
        _state.update {
            it.copy(pickerFilter = filter, pickerItems = emptyList(), pickerLoading = true, pickerEndReached = false)
        }
        loadPickerPage()
    }

    fun loadMorePicker() {
        val state = _state.value
        if (state.pickerLoading || state.pickerEndReached) return
        loadPickerPage()
    }

    private fun loadPickerPage() {
        val generation = pickerGeneration
        val filter = _state.value.pickerFilter
        _state.update { it.copy(pickerLoading = true) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { mediaRepository.loadRecentItems(_state.value.pickerItems.size, PICKER_PAGE_SIZE, filter) }
            }.fold(
                onSuccess = { page ->
                    if (generation != pickerGeneration) return@fold
                    _state.update {
                        it.copy(
                            pickerItems = it.pickerItems + page,
                            pickerLoading = false,
                            pickerEndReached = page.size < PICKER_PAGE_SIZE,
                        )
                    }
                },
                onFailure = {
                    if (generation != pickerGeneration) return@fold
                    _state.update { it.copy(pickerLoading = false, pickerEndReached = true) }
                },
            )
        }
    }

    /** 抖音式「发布」：创建作品并按选择顺序逐个复制素材，汇总复制失败数提示。 */
    fun createWorkWithItems(title: String, items: List<MediaItem>) {
        val portfolioId = _state.value.selectedPortfolio?.id ?: return
        if (items.isEmpty()) return
        launchBusy {
            val workId = repository.createWork(portfolioId, title)
            var failed = 0
            items.forEach { if (repository.addMedia(workId, it) != null) failed++ }
            "作品「$title」已创建" + if (failed > 0) "，$failed 个素材复制失败" else ""
        }
    }

    /**
     * 点击作品集网格中的作品：进入作品详情 feed（见 [WorkFeedPlayer]）并预载邻位作品素材。
     * 素材按需缓存（workItems），不做全量装载。
     */
    fun openWork(summary: WorkSummary) {
        _state.update { it.copy(selectedWork = summary) }
        preloadAround(summary.id)
    }

    /** 作品详情 feed 纵向滑动落定：更新锚点作品并预载前后相邻作品。 */
    fun onFeedSettled(workId: Long) {
        val work = _state.value.works.firstOrNull { it.id == workId }
        if (work == null) {
            // works 里已找不到该作品（被删除等），关掉 feed。
            _state.update { it.copy(selectedWork = null) }
            return
        }
        _state.update { it.copy(selectedWork = work) }
        preloadAround(workId)
    }

    fun backFromWork() {
        // 保留 workItems 缓存，重进同一作品免重新加载；清空发生在 openPortfolio/backFromPortfolio。
        _state.update { it.copy(selectedWork = null) }
        loadWorks()
    }

    /** 确保指定作品的素材已加载（或加载中）；已加载/在途则直接返回。 */
    fun ensureWorkItems(workId: Long) {
        val cached = _state.value.workItems[workId]
        if (cached != null && (cached.loading || cached.loaded)) return
        if (!loadingWorkIds.add(workId)) return
        val generation = workItemsGeneration[workId] ?: 0
        _state.update {
            it.copy(workItems = it.workItems + (workId to WorkItemsState(loading = true)))
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.loadWorkItems(workId) }
            }
            loadingWorkIds.remove(workId)
            if (generation != (workItemsGeneration[workId] ?: 0)) return@launch
            _state.update { st ->
                val entry = result.fold(
                    onSuccess = { r -> WorkItemsState(items = r.items, loaded = true, missingCount = r.missingCount) },
                    onFailure = { WorkItemsState(loaded = true) },
                )
                st.copy(workItems = st.workItems + (workId to entry))
            }
        }
    }

    /** 素材变化后使指定作品的缓存失效：下次 [ensureWorkItems] 重新查询。 */
    private fun invalidateWorkItems(workId: Long) {
        workItemsGeneration[workId] = (workItemsGeneration[workId] ?: 0) + 1
        _state.update { it.copy(workItems = it.workItems - workId) }
    }

    /** 预载锚点作品及其前后相邻作品（feed 当前页 + 邻位，保证滑动到即渲染）。 */
    private fun preloadAround(anchorWorkId: Long) {
        val works = _state.value.works
        val index = works.indexOfFirst { it.id == anchorWorkId }
        if (index < 0) return
        for (i in index - 1..index + 1) {
            works.getOrNull(i)?.let { ensureWorkItems(it.id) }
        }
    }

    // ---------- 内部 ----------

    /**
     * 忙态 + 消息统一入口：block 返回 null 表示成功，否则为提示消息。
     * 完成后刷新打开中的作品集/作品列表（refreshOpened）。
     */
    private fun launchBusy(block: suspend () -> String?) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val message = runCatching { block() }
                .getOrElse { "操作失败：${it.message ?: "未知错误"}" }
            refreshAll()
            _state.update { it.copy(busy = false, message = message) }
        }
    }

    private fun refreshAll() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { repository.loadPortfolios() }
            }.onSuccess { portfolios ->
                _state.update {
                    it.copy(portfolios = portfolios, loading = false, error = null)
                }
            }
            refreshOpened()
        }
    }

    private fun refreshOpened() {
        val portfolio = _state.value.selectedPortfolio ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val works = runCatching { repository.loadWorks(portfolio.id) }.getOrNull().orEmpty()
                val anchorId = _state.value.selectedWork?.id
                val anchorResult = anchorId
                    ?.let { runCatching { repository.loadWorkItems(it) }.getOrNull() }
                _state.update { st ->
                    val anchor = st.selectedWork
                    // 锚点作品已不在作品集里（被删除等）→ 关掉 feed。
                    val stillExists = anchor != null && works.any { it.id == anchor.id }
                    val validIds = works.map { it.id }.toSet()
                    when {
                        anchor != null && !stillExists ->
                            st.copy(works = works, selectedWork = null, workItems = st.workItems.filterKeys { it in validIds })
                        anchorResult != null && anchor != null ->
                            st.copy(
                                works = works,
                                workItems = st.workItems
                                    .filterKeys { it in validIds }
                                    .let { it + (anchor.id to WorkItemsState(items = anchorResult.items, loaded = true, missingCount = anchorResult.missingCount)) },
                            )
                        else -> st.copy(works = works, workItems = st.workItems.filterKeys { it in validIds })
                    }
                }
            }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    companion object {
        private const val PICKER_PAGE_SIZE = 120
    }
}
