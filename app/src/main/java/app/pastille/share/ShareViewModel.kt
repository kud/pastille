package app.pastille.share

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pastille.data.ImportResult
import app.pastille.data.SnippetRepository
import app.pastille.images.ImageStore
import app.pastille.model.CategoryRecord
import app.pastille.model.SnippetRecord
import app.pastille.settings.PastilleSettings
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShareInput(
    val text: String?,
    val subject: String?,
    val streams: List<Uri>,
    val displayNames: List<String?>,
)

enum class ShareStatus {
    SAVING,
    SAVED,
    ALREADY,
    FAILED,
    REMOVED,
}

enum class ShareKind {
    TEXT,
    LINK,
    SINGLE_IMAGE,
    MULTI_IMAGE,
}

data class ShareUiState(
    val saving: Boolean = true,
    val status: ShareStatus = ShareStatus.SAVING,
    val kind: ShareKind = ShareKind.TEXT,
    val textTitle: String = "",
    val textBody: String = "",
    val truncated: Boolean = false,
    val linkUrl: String = "",
    val linkHost: String = "",
    val imageFiles: List<String> = emptyList(),
    val imageTitles: List<String> = emptyList(),
    val imageAspect: Float? = null,
    val totalImages: Int = 0,
    val failureReason: String = "",
    val selectedCategoryId: Long? = null,
    val canUndo: Boolean = false,
    val firstId: Long? = null,
    val shouldFinish: Boolean = false,
    val saveAsSticker: Boolean = false,
    val savedCount: Int = 0,
)

class ShareViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SnippetRepository.forContext(application)
    private val store = ImageStore.forContext(application)
    private val settings = PastilleSettings.forContext(application)

    val categories: StateFlow<List<CategoryRecord>> =
        repository.observeCategories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var uiState by mutableStateOf(ShareUiState())
        private set

    private var started = false
    private var finishRequested = false
    private var createdIds: List<Long> = emptyList()
    private var createdFiles: List<String> = emptyList()
    private var allIds: List<Long> = emptyList()
    private var lastImport: ImportResult? = null

    fun start(input: ShareInput) {
        if (started) return
        started = true
        viewModelScope.launch(Dispatchers.IO) {
            val initialCategory = resolveInitialCategory()
            uiState = uiState.copy(selectedCategoryId = initialCategory)
            if (input.streams.isEmpty()) {
                saveText(input, initialCategory)
            } else {
                saveImages(input, initialCategory)
            }
            if (finishRequested) {
                uiState = uiState.copy(shouldFinish = true)
            }
        }
    }

    fun selectCategory(id: Long?) {
        if (allIds.isEmpty() || uiState.saving || uiState.saveAsSticker) return
        uiState = uiState.copy(selectedCategoryId = id)
        viewModelScope.launch(Dispatchers.IO) {
            repository.setCategory(allIds, id)
        }
    }

    // "Save as" applies to the whole batch, like the folder choice.
    fun selectSaveAs(sticker: Boolean) {
        if (uiState.saving || uiState.saveAsSticker == sticker) return
        settings.saveImagesAsStickers = sticker
        uiState = uiState.copy(saveAsSticker = sticker)
        val result = lastImport ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val updated = if (sticker) {
                result.created.forEach { repository.setSticker(it.id, true) }
                val nowConverted = result.alreadyThere.filter { !it.sticker }
                nowConverted.forEach { repository.setSticker(it.id, true) }
                result.copy(converted = result.converted + nowConverted, alreadyThere = result.alreadyThere - nowConverted.toSet())
            } else {
                result.created.forEach { repository.setSticker(it.id, false) }
                repository.setCategory(result.created.map { it.id }, uiState.selectedCategoryId)
                result.converted.forEach { repository.restore(it) }
                result.copy(converted = emptyList(), alreadyThere = result.alreadyThere + result.converted)
            }
            lastImport = updated
            uiState = uiState.copy(
                savedCount = updated.savedCount,
                status = if (updated.savedCount == 0) ShareStatus.ALREADY else ShareStatus.SAVED,
                canUndo = updated.savedCount > 0,
            )
        }
    }

    fun undo() {
        val imported = lastImport
        if (imported != null) {
            if (imported.savedCount == 0 || uiState.saving) return
            viewModelScope.launch(Dispatchers.IO) {
                repository.undoImport(imported, store::delete)
                lastImport = imported.copy(created = emptyList(), converted = emptyList())
                allIds = emptyList()
                uiState = uiState.copy(status = ShareStatus.REMOVED, canUndo = false)
            }
            return
        }
        val ids = createdIds
        if (ids.isEmpty() || uiState.saving) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMany(ids)
            createdFiles.forEach { name ->
                if (repository.findByImageFile(name) == null) {
                    store.delete(name)
                }
            }
            createdIds = emptyList()
            createdFiles = emptyList()
            allIds = allIds - ids.toSet()
            uiState = uiState.copy(status = ShareStatus.REMOVED, canUndo = false)
        }
    }

    fun requestFinish() {
        finishRequested = true
        if (!uiState.saving) {
            uiState = uiState.copy(shouldFinish = true)
        }
    }

    private suspend fun resolveInitialCategory(): Long? {
        val wanted = settings.keyboardCategoryId ?: return null
        return if (repository.getCategories().any { it.id == wanted }) wanted else null
    }

    private suspend fun saveText(input: ShareInput, initialCategory: Long?) {
        val (capped, wasTruncated) = capText(input.text.orEmpty())
        if (capped.isBlank()) {
            uiState = uiState.copy(saving = false, status = ShareStatus.FAILED, failureReason = "Nothing to save")
            return
        }
        val url = findSingleUrl(capped)
        val title = if (url != null) {
            linkTitle(input.subject, capped, url)
        } else {
            textTitle(input.subject, capped)
        }
        uiState = uiState.copy(
            kind = if (url != null) ShareKind.LINK else ShareKind.TEXT,
            textTitle = title,
            textBody = capped,
            truncated = wasTruncated,
            linkUrl = url.orEmpty(),
            linkHost = url?.let { linkHost(it) }.orEmpty(),
        )
        val duplicate = repository.findTextDuplicate(capped)
        if (duplicate != null) {
            allIds = listOf(duplicate.id)
            uiState = uiState.copy(
                saving = false,
                status = ShareStatus.ALREADY,
                selectedCategoryId = duplicate.categoryId,
                firstId = duplicate.id,
            )
            return
        }
        val id = repository.upsert(
            SnippetRecord(title = title, text = capped, categoryId = initialCategory),
        )
        createdIds = listOf(id)
        allIds = listOf(id)
        uiState = uiState.copy(
            saving = false,
            status = ShareStatus.SAVED,
            canUndo = true,
            firstId = id,
        )
    }

    private suspend fun saveImages(input: ShareInput, initialCategory: Long?) {
        val total = input.streams.size
        val single = total == 1
        val sticker = settings.saveImagesAsStickers
        uiState = uiState.copy(
            kind = if (single) ShareKind.SINGLE_IMAGE else ShareKind.MULTI_IMAGE,
            totalImages = total,
            saveAsSticker = sticker,
        )
        val fallbackDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .format(LocalDate.now())
        val caption = if (total == 1) input.text else null
        val result = repository.importImages(
            uris = input.streams,
            categoryId = initialCategory,
            sticker = sticker,
            importer = store,
            titleFor = { index -> imageTitle(caption, input.displayNames.getOrNull(index), fallbackDate) },
        )
        if (result.images.isEmpty()) {
            uiState = uiState.copy(
                saving = false,
                status = ShareStatus.FAILED,
                failureReason = result.failed.firstOrNull().orEmpty(),
            )
            return
        }
        lastImport = result
        allIds = result.images.map { it.id }
        val first = result.images.first()
        val currentCategory = if (allIds.size == 1) {
            repository.get(first.id)?.categoryId ?: initialCategory
        } else {
            initialCategory
        }
        uiState = uiState.copy(
            saving = false,
            status = if (result.savedCount == 0) ShareStatus.ALREADY else ShareStatus.SAVED,
            imageFiles = result.images.map { it.fileName },
            imageTitles = result.images.map { it.title },
            imageAspect = if (single && first.height > 0) first.width.toFloat() / first.height.toFloat() else null,
            selectedCategoryId = currentCategory,
            canUndo = result.savedCount > 0,
            firstId = first.id,
            savedCount = result.savedCount,
        )
    }
}
