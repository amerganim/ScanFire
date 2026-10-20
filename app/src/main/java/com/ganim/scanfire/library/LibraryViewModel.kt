package com.ganim.scanfire.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ganim.scanfire.data.LibraryRepository
import com.ganim.scanfire.data.Settings
import com.ganim.scanfire.data.SettingsRepository
import com.ganim.scanfire.data.SortOrder
import com.ganim.scanfire.data.db.DocumentSummary
import com.ganim.scanfire.data.db.FolderEntity
import com.ganim.scanfire.data.db.FolderSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryState(
    val path: List<FolderEntity> = emptyList(),
    val folders: List<FolderSummary> = emptyList(),
    val documents: List<DocumentSummary> = emptyList(),
    val searching: Boolean = false,
    val query: String = "",
    val selection: Set<Long> = emptySet(),
    val settings: Settings = Settings(),
    val loaded: Boolean = false,
) {
    val currentFolderId: Long? get() = path.lastOrNull()?.id
    val isEmpty: Boolean get() = folders.isEmpty() && documents.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LibraryViewModel(
    private val library: LibraryRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {
    private val path = MutableStateFlow<List<FolderEntity>>(emptyList())
    private val searching = MutableStateFlow(false)
    private val query = MutableStateFlow("")
    private val selection = MutableStateFlow<Set<Long>>(emptySet())

    val allFolders: StateFlow<List<FolderEntity>> =
        library.allFolders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val folderId = MutableStateFlow<Long?>(null)

    private val content = combine(searching, query.debounce(150), folderId) { s, q, f -> Triple(s, q, f) }
        .flatMapLatest { (s, q, f) ->
            if (s && q.isNotBlank()) {
                combine(flowOf(emptyList<FolderSummary>()), library.search(q.trim())) { a, b -> a to b }
            } else {
                combine(library.folders(f), library.documents(f)) { a, b -> a to b }
            }
        }

    val state: StateFlow<LibraryState> = combine(
        path, content, combine(searching, query, selection) { s, q, sel -> Triple(s, q, sel) }, settingsRepo.settings,
    ) { p, (folders, docs), (s, q, sel), settings ->
        LibraryState(
            path = p,
            folders = folders,
            documents = docs.sortedWith(settings.sortOrder.comparator()),
            searching = s,
            query = q,
            selection = sel.filterTo(mutableSetOf()) { id -> docs.any { it.id == id } },
            settings = settings,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    fun openFolder(folder: FolderSummary) {
        clearSelection()
        val entity = FolderEntity(folder.id, folder.name, folder.parentId, folder.createdAt)
        path.update { it + entity }
        folderId.value = folder.id
    }

    /** Returns false when already at the top level, so the caller can let Back leave the app. */
    fun goUp(): Boolean {
        if (path.value.isEmpty()) return false
        path.update { it.dropLast(1) }
        folderId.value = path.value.lastOrNull()?.id
        return true
    }

    fun goToDepth(depth: Int) {
        path.update { it.take(depth) }
        folderId.value = path.value.lastOrNull()?.id
    }

    fun setSearching(on: Boolean) {
        searching.value = on
        if (!on) query.value = ""
    }

    fun setQuery(q: String) {
        query.value = q
    }

    fun toggleSelect(id: Long) = selection.update { if (id in it) it - id else it + id }
    fun selectAll() = selection.update { state.value.documents.map { it.id }.toSet() }
    fun clearSelection() = selection.update { emptySet() }

    fun setSort(order: SortOrder) = viewModelScope.launch { settingsRepo.setSort(order) }
    fun toggleGrid() = viewModelScope.launch { settingsRepo.setGrid(!state.value.settings.gridView) }

    fun createFolder(name: String) = viewModelScope.launch { library.createFolder(name, state.value.currentFolderId) }
    fun renameFolder(id: Long, name: String) = viewModelScope.launch {
        library.renameFolder(id, name)
        path.update { list -> list.map { if (it.id == id) it.copy(name = name) else it } }
    }
    fun moveFolder(id: Long, parent: Long?) = viewModelScope.launch { library.moveFolder(id, parent) }
    fun deleteFolder(id: Long) = viewModelScope.launch { library.deleteFolder(id) }

    fun renameDocument(id: Long, name: String) = viewModelScope.launch { library.renameDocument(id, name) }
    fun moveDocuments(ids: Collection<Long>, folder: Long?) = viewModelScope.launch {
        ids.forEach { library.moveDocument(it, folder) }
        clearSelection()
    }
    fun deleteDocuments(ids: Collection<Long>) = viewModelScope.launch {
        library.deleteDocuments(ids)
        clearSelection()
    }
}

fun SortOrder.comparator(): Comparator<DocumentSummary> = when (this) {
    SortOrder.Updated -> compareByDescending { it.updatedAt }
    SortOrder.Created -> compareByDescending { it.createdAt }
    SortOrder.Name -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
}
