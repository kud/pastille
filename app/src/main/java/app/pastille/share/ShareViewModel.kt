package app.pastille.share

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pastille.data.SnippetRepository
import app.pastille.images.ImageImportException
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
        if (allIds.isEmpty() || uiState.saving) return
        uiState = uiState.copy(selectedCategoryId = id)
        viewModelScope.launch(Dispatchers.IO) {
            repository.setCategory(allIds, id)
        }
    }

    fun undo() {
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
        val streams = input.streams.take(MAX_IMAGES)
        val single = streams.size == 1
        uiState = uiState.copy(
            kind = if (single) ShareKind.SINGLE_IMAGE else ShareKind.MULTI_IMAGE,
            totalImages = total,
        )
        val fallbackDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .format(LocalDate.now())
        val caption = if (total == 1) input.text else null
        val created = mutableListOf<Long>()
        val createdNames = mutableListOf<String>()
        val all = mutableListOf<Long>()
        val files = mutableListOf<String>()
        val titles = mutableListOf<String>()
        var aspect: Float? = null
        val failures = mutableListOf<String>()
        streams.forEachIndexed { index, uri ->
            val displayName = input.displayNames.getOrNull(index)
            val stored = try {
                store.import(uri)
            } catch (error: ImageImportException) {
                failures.add(error.reason)
                return@forEachIndexed
            } catch (_: Exception) {
                failures.add("Unreadable image")
                return@forEachIndexed
            }
            val title = imageTitle(caption, displayName, fallbackDate)
            val existing = repository.findByImageFile(stored.fileName)
            if (existing != null) {
                all.add(existing.id)
                titles.add(existing.title.ifEmpty { title })
            } else {
                val id = repository.insertImage(
                    title = title,
                    imageFile = stored.fileName,
                    width = stored.width,
                    height = stored.height,
                    categoryId = initialCategory,
                )
                created.add(id)
                createdNames.add(stored.fileName)
                all.add(id)
                titles.add(title)
            }
            files.add(stored.fileName)
            if (single && stored.height > 0) {
                aspect = stored.width.toFloat() / stored.height.toFloat()
            }
        }
        if (files.isEmpty()) {
            uiState = uiState.copy(
                saving = false,
                status = ShareStatus.FAILED,
                failureReason = failures.firstOrNull().orEmpty(),
            )
            return
        }
        createdIds = created.toList()
        createdFiles = createdNames.toList()
        allIds = all.toList()
        val first = all.first()
        val currentCategory = if (all.size == 1) {
            repository.get(first)?.categoryId ?: initialCategory
        } else {
            initialCategory
        }
        uiState = uiState.copy(
            saving = false,
            status = if (created.isEmpty()) ShareStatus.ALREADY else ShareStatus.SAVED,
            imageFiles = files.toList(),
            imageTitles = titles.toList(),
            imageAspect = aspect,
            selectedCategoryId = currentCategory,
            canUndo = created.isNotEmpty(),
            firstId = first,
        )
    }
}
