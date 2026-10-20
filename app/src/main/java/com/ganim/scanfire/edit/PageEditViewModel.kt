package com.ganim.scanfire.edit

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ganim.scanfire.data.PageRepository
import com.ganim.scanfire.data.db.PageEntity
import com.ganim.scanfire.imaging.Bitmaps
import com.ganim.scanfire.imaging.Filters
import com.ganim.scanfire.imaging.PageFilter
import com.ganim.scanfire.imaging.PageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditState(
    val page: PageEntity? = null,
    val rotation: Int = 0,
    val crop: RectF = RectF(0f, 0f, 1f, 1f),
    val filter: String = Filters.ORIGINAL,
    /** Rotated, uncropped, unfiltered: what the crop frame is drawn over. */
    val rotatedSource: Bitmap? = null,
    /** Fully rendered preview of the current settings. */
    val preview: Bitmap? = null,
    val thumbnails: Map<PageFilter, Bitmap> = emptyMap(),
    val saving: Boolean = false,
) {
    val changed: Boolean
        get() = page != null && (rotation != page.rotation || filter != page.filter ||
            crop != RectF(page.cropLeft, page.cropTop, page.cropRight, page.cropBottom))
}

class PageEditViewModel(
    private val pageId: Long,
    private val pages: PageRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(EditState())
    val state: StateFlow<EditState> = _state

    /** Downscaled original, so previews render in tens of milliseconds. Saving re-renders at full size. */
    private var previewOriginal: Bitmap? = null
    private var renderJob: Job? = null

    init {
        viewModelScope.launch {
            val page = pages.page(pageId).filterNotNull().first()
            val original = withContext(Dispatchers.IO) { Bitmaps.decode(pages.originalFile(page), maxEdge = 1600) }
            previewOriginal = original
            _state.value = EditState(
                page = page,
                rotation = page.rotation,
                crop = RectF(page.cropLeft, page.cropTop, page.cropRight, page.cropBottom),
                filter = page.filter,
            )
            rerender(thumbnails = true)
        }
    }

    fun setFilter(filter: String) {
        _state.update { it.copy(filter = filter) }
        rerender()
    }

    fun rotate(delta: Int) {
        // Rotating invalidates the crop frame (it is relative to the rotated image), so rotate it with the image.
        _state.update { s -> s.copy(rotation = ((s.rotation + delta) % 360 + 360) % 360, crop = rotateCrop(s.crop, delta)) }
        rerender(thumbnails = true)
    }

    fun setCrop(crop: RectF) {
        _state.update { it.copy(crop = crop) }
    }

    /** Called when a crop drag ends, so the filtered preview isn't recomputed on every pointer move. */
    fun commitCrop() = rerender(thumbnails = true)

    fun resetCrop() {
        _state.update { it.copy(crop = RectF(0f, 0f, 1f, 1f)) }
        rerender(thumbnails = true)
    }

    fun save(applyFilterToAll: Boolean, onDone: () -> Unit) {
        val s = _state.value
        val page = s.page ?: return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            pages.edit(page.id, s.rotation, s.crop, s.filter)
            if (applyFilterToAll) {
                pages.pagesNow(page.documentId).filter { it.id != page.id && it.filter != s.filter }.forEach {
                    pages.edit(it.id, filter = s.filter)
                }
            }
            onDone()
        }
    }

    private fun rerender(thumbnails: Boolean = false) {
        val source = previewOriginal ?: return
        val s = _state.value
        renderJob?.cancel()
        renderJob = viewModelScope.launch(Dispatchers.Default) {
            val rotated = Bitmaps.rotate(source, s.rotation)
            val cropped = PageRenderer.crop(rotated, s.crop)
            val preview = Filters.apply(cropped, s.filter)
            _state.update { it.copy(rotatedSource = rotated, preview = preview) }
            if (thumbnails || _state.value.thumbnails.isEmpty()) {
                val small = Bitmaps.scaleDown(cropped, 220)
                val thumbs = PageFilter.entries.associateWith { Filters.apply(small, it.key) }
                _state.update { it.copy(thumbnails = thumbs) }
            }
        }
    }

    companion object {
        /** Maps a normalised crop rect through a rotation of [delta] degrees (multiples of 90). */
        fun rotateCrop(c: RectF, delta: Int): RectF = when (((delta % 360) + 360) % 360) {
            90 -> RectF(1 - c.bottom, c.left, 1 - c.top, c.right)
            180 -> RectF(1 - c.right, 1 - c.bottom, 1 - c.left, 1 - c.top)
            270 -> RectF(c.top, 1 - c.right, c.bottom, 1 - c.left)
            else -> RectF(c)
        }
    }
}
