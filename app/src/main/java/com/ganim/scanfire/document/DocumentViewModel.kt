package com.ganim.scanfire.document

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ganim.scanfire.data.LibraryRepository
import com.ganim.scanfire.data.PageRepository
import com.ganim.scanfire.data.db.DocumentEntity
import com.ganim.scanfire.data.db.PageEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DocumentState(
    val document: DocumentEntity? = null,
    val pages: List<PageEntity> = emptyList(),
    val selection: Set<Long> = emptySet(),
    val loaded: Boolean = false,
)

class DocumentViewModel(
    val docId: Long,
    private val library: LibraryRepository,
    val pageRepo: PageRepository,
) : ViewModel() {
    private val selection = MutableStateFlow<Set<Long>>(emptySet())

    val state: StateFlow<DocumentState> = combine(library.document(docId), pageRepo.pages(docId), selection) { doc, pages, sel ->
        DocumentState(doc, pages, sel.filterTo(mutableSetOf()) { id -> pages.any { it.id == id } }, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentState())

    fun rename(name: String) = viewModelScope.launch { library.renameDocument(docId, name) }

    fun deleteDocument(onDone: () -> Unit) = viewModelScope.launch {
        library.deleteDocument(docId)
        onDone()
    }

    fun toggleSelect(id: Long) = selection.update { if (id in it) it - id else it + id }
    fun selectAll() = selection.update { state.value.pages.map { it.id }.toSet() }
    fun clearSelection() = selection.update { emptySet() }

    fun deletePages(ids: Collection<Long>) = viewModelScope.launch {
        pageRepo.delete(ids)
        clearSelection()
    }

    fun reorder(orderedIds: List<Long>) = viewModelScope.launch { pageRepo.reorder(docId, orderedIds) }
}
