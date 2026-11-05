package com.classics.readingroom

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.classics.readingroom.ai.AiService
import com.classics.readingroom.ai.AiSettings
import com.classics.readingroom.data.Book
import com.classics.readingroom.data.CatalogFilters
import com.classics.readingroom.data.CatalogQuery
import com.classics.readingroom.data.Dossier
import com.classics.readingroom.data.Library
import com.classics.readingroom.speech.TtsController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val library = Library.get(app)
    val tts = TtsController(app)
    val recorder = com.classics.readingroom.speech.RecorderController(app)

    var favorites by mutableStateOf(library.favorites())
        private set
    var customBooks by mutableStateOf(library.customBooks())
        private set
    var indexProgress by mutableStateOf(0f)
        private set
    var dossierCount by mutableStateOf(0)
        private set

    val categories: List<Book> get() = customBooks + library.catalog

    init {
        tts.setRate(library.ttsRate)
        tts.setStyle(library.ttsStyle)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { library.ensureIndex() }
            indexProgress = 1f
            dossierCount = library.countBuiltinDossiers()
        }
    }

    fun refreshShelf() {
        customBooks = library.customBooks()
        favorites = library.favorites()
    }

    fun isFavorite(id: String) = favorites.contains(id)

    fun toggleFavorite(id: String) {
        library.toggleFavorite(id)
        favorites = library.favorites()
    }

    fun ttsRateSetting(): Float = library.ttsRate

    fun ttsStyleSetting(): String = library.ttsStyle

    fun setTtsRate(rate: Float) {
        library.ttsRate = rate
        tts.setRate(rate)
    }

    fun setTtsStyle(style: String) {
        library.ttsStyle = style
        tts.setStyle(style)
    }

    suspend fun loadDetail(id: String): Dossier? = library.detail(id)

    fun fallbackDossier(book: Book): Dossier {
        val tags = book.tags.joinToString("、")
        return Dossier(
            id = book.id,
            title = book.title,
            subtitle = book.summary,
            author = book.author,
            authorIntro = book.authorIntro,
            country = book.country,
            era = book.era,
            category = book.category,
            tags = book.tags,
            readingMinutes = book.readingMinutes,
            summary = book.summary,
            story = listOf(book.summary),
            insights = listOf(
                "《${book.title}》是${book.era}${book.country}${book.category}中极具代表性的作品，常被列入必读经典书单。它值得反复阅读的地方，不只在故事本身，更在于它对人性、社会与时代的长期追问。",
                "阅读时可以抓住${tags}这几个关键词：先看作品如何提出问题，再看人物如何在处境中做出选择，最后把书中的问题带回自己的生活。"
            ),
            quotes = emptyList(),
            lecture = com.classics.readingroom.data.Lecture(
                title = "每天精读一本书：${book.title}",
                durationMinutes = 0,
                tone = "平实、亲切",
                opening = "今天要和大家聊的，是${book.country}${book.era}的《${book.title}》。${book.summary}，下面先带大家从目录、题材和阅读方法三个角度，快速建立对它的整体印象。",
                sections = listOf(
                    com.classics.readingroom.data.LectureSection(
                        "一、先认识这本书",
                        listOf(
                            "《${book.title}》由${book.author}创作，属于${book.category}，预计阅读时间约 ${book.readingMinutes} 分钟。它围绕${tags}展开，是理解${book.era}精神面貌的一扇窗口。",
                            "第一次阅读不必追求记住所有细节。先弄清人物关系与核心冲突，再带着问题读第二遍，收获会明显不同。"
                        )
                    )
                ),
                closing = "这本书的精读精华与演讲书稿可以随时补充生成。你也可以先在“阅读原书”中导入自己的电子书，按章节慢慢读。"
            ),
            chapters = emptyList(),
            isFallback = true
        )
    }

    /* ---------------- AI 读写 ---------------- */

    var aiSettings by mutableStateOf(AiSettings.fromJson(library.aiSettings))
        private set

    fun saveAiSettings(settings: AiSettings) {
        aiSettings = settings
        library.aiSettings = settings.toJson()
    }

    fun applyPreset(key: String) {
        saveAiSettings(AiService.applyPreset(aiSettings, key))
    }

    fun saveDossier(dossier: Dossier) {
        library.saveDossier(dossier)
    }

    fun resetDossier(id: String) {
        library.removeDossier(id)
    }

    fun hasEdited(id: String) = library.hasEdited(id)

    fun hasDetail(id: String) = library.hasDetail(id)

    suspend fun generateForBook(book: Book): Dossier {
        val generated = AiService.generateDetail(book, aiSettings)
        val merged = generated.copy(id = book.id, title = book.title, savedAt = System.currentTimeMillis())
        library.saveDossier(merged)
        return merged
    }

    suspend fun reviseDossier(book: Book, detail: Dossier, instruction: String, scope: String): Dossier {
        val revised = AiService.revise(book.title, detail, instruction, scope, aiSettings)
            .copy(id = book.id, title = book.title)
        library.saveDossier(revised)
        return revised
    }

    suspend fun generateCustomBook(title: String, author: String, category: String): Book {
        val dossier = AiService.generateBook(title, author, category, aiSettings)
        val id = "custom-" + System.currentTimeMillis().toString(36)
        val book = Book(
            id = id,
            title = title,
            author = dossier.author.ifBlank { author.ifBlank { "佚名" } },
            country = dossier.country,
            era = dossier.era,
            category = category,
            level = "自定义",
            readingMinutes = dossier.readingMinutes,
            tags = dossier.tags,
            summary = dossier.summary,
            cat1 = "我的书架",
            cat2 = "自定义",
            cat3 = category,
            custom = true,
            aiGenerated = true,
            model = aiSettings.model,
            authorIntro = dossier.authorIntro,
            subtitle = dossier.subtitle
        )
        val full = dossier.copy(
            id = id,
            title = title,
            author = book.author,
            category = category,
            tags = dossier.tags.ifEmpty { listOf("自定义") },
            savedAt = System.currentTimeMillis()
        )
        library.addCustomBook(book)
        library.saveCustomDossier(full)
        refreshShelf()
        return book
    }

    fun addSimpleBook(title: String, author: String, category: String): Book {
        val id = "custom-" + System.currentTimeMillis().toString(36)
        val book = Book(
            id = id,
            title = title,
            author = author.ifBlank { "佚名" },
            country = "中国",
            era = "当代",
            category = category,
            level = "自定义",
            readingMinutes = 120,
            tags = listOf("自定义", "速览"),
            summary = "《$title》的自定义速览档案，完整内容可以随时生成补充。",
            cat1 = "我的书架",
            cat2 = "自定义",
            cat3 = category,
            custom = true,
            authorIntro = ""
        )
        val dossier = fallbackDossier(book).copy(
            id = id,
            title = title,
            subtitle = "自定义书籍 · 速览",
            author = book.author
        )
        library.addCustomBook(book)
        library.saveCustomDossier(dossier)
        refreshShelf()
        return book
    }

    fun removeCustomBook(id: String) {
        library.removeCustomBook(id)
        refreshShelf()
    }

    fun authorWorks(author: String) = library.authorWorks(author)

    suspend fun similarBooks(book: Book): List<Book> = library.similarBooks(book)

    fun todayBook() = library.todayBook()

    fun textMeta(id: String) = library.texts[id]

    fun saveProgress(id: String, chapter: Int, total: Int) = library.saveReadProgress(id, chapter, total)

    fun progress(id: String) = library.readProgress(id)

    override fun onCleared() {
        tts.release()
        recorder.releaseAll()
        super.onCleared()
    }
}
