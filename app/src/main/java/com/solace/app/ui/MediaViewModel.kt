package com.solace.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.solace.app.data.FolderSort
import com.solace.app.data.MediaFilter
import com.solace.app.data.MediaFolder
import com.solace.app.data.MediaItem
import com.solace.app.data.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MediaUiState(
    val loadingFolders: Boolean = true,
    val foldersError: String? = null,
    val folders: List<MediaFolder> = emptyList(),
    val currentFolder: MediaFolder? = null,
    val folderItems: List<MediaItem> = emptyList(),
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val folderGridView: Boolean = false,
    val sort: FolderSort = FolderSort.TIME,
    val filter: MediaFilter = MediaFilter.ALL,
)

// 单个文件夹独立的展示配置（排序 + 展示内容）。
data class FolderViewPrefs(
    val sort: FolderSort = FolderSort.TIME,
    val filter: MediaFilter = MediaFilter.ALL,
)

class MediaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)
    private val _state = MutableStateFlow(MediaUiState())
    val state: StateFlow<MediaUiState> = _state.asStateFlow()

    private var currentBucketId: Long? = null
    private var loadedOffset = 0

    // 排序/展示内容按文件夹独立记忆（bucketId 维度），切换文件夹互不影响；首次进入取默认。
    private val folderPrefs = mutableMapOf<Long, FolderViewPrefs>()

    // 排序/过滤/进文件夹都会使在途查询失效；结果按代际丢弃，避免旧数据串进新列表。
    private var loadGeneration = 0

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loadingFolders = true, foldersError = null) }
            withContext(Dispatchers.IO) {
                runCatching { repository.loadFolders() }
            }.fold(
                onSuccess = { folders ->
                    _state.update { it.copy(loadingFolders = false, folders = folders) }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(loadingFolders = false, foldersError = e.message ?: "加载失败")
                    }
                },
            )
        }
    }

    fun openFolder(folder: MediaFolder) {
        currentBucketId = folder.bucketId
        loadedOffset = 0
        loadGeneration++
        val prefs = folderPrefs[folder.bucketId] ?: FolderViewPrefs()
        _state.update {
            it.copy(
                currentFolder = folder,
                folderItems = emptyList(),
                endReached = false,
                sort = prefs.sort,
                filter = prefs.filter,
            )
        }
        loadMore()
    }

    fun setSort(sort: FolderSort) {
        if (sort == _state.value.sort) return
        val bucketId = currentBucketId ?: return
        folderPrefs[bucketId] = (folderPrefs[bucketId] ?: FolderViewPrefs()).copy(sort = sort)
        restartFolderItems { it.copy(sort = sort) }
    }

    fun setFilter(filter: MediaFilter) {
        if (filter == _state.value.filter) return
        val bucketId = currentBucketId ?: return
        folderPrefs[bucketId] = (folderPrefs[bucketId] ?: FolderViewPrefs()).copy(filter = filter)
        restartFolderItems { it.copy(filter = filter) }
    }

    private fun restartFolderItems(transform: (MediaUiState) -> MediaUiState) {
        if (currentBucketId == null) {
            _state.update(transform)
            return
        }
        loadedOffset = 0
        loadGeneration++
        _state.update {
            transform(it).copy(folderItems = emptyList(), endReached = false, loadingMore = false)
        }
        loadMore()
    }

    fun loadMore() {
        val bucketId = currentBucketId ?: return
        val state = _state.value
        if (state.loadingMore || state.endReached) return
        val generation = loadGeneration

        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    repository.loadFolderItems(bucketId, loadedOffset, PAGE_SIZE, state.sort, state.filter)
                }
            }.fold(
                onSuccess = { page ->
                    if (generation != loadGeneration) return@fold
                    loadedOffset += page.size
                    _state.update {
                        it.copy(
                            loadingMore = false,
                            endReached = page.size < PAGE_SIZE,
                            folderItems = it.folderItems + page,
                        )
                    }
                },
                onFailure = {
                    if (generation != loadGeneration) return@fold
                    _state.update { it.copy(loadingMore = false, endReached = true) }
                },
            )
        }
    }

    fun toggleFolderLayout() {
        _state.update { it.copy(folderGridView = !it.folderGridView) }
    }

    fun back() {
        currentBucketId = null
        loadedOffset = 0
        loadGeneration++
        _state.update {
            it.copy(currentFolder = null, folderItems = emptyList(), endReached = false)
        }
    }

    private companion object {
        const val PAGE_SIZE = 120
    }
}
