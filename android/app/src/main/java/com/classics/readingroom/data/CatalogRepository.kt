package com.classics.readingroom.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** 三级分类树节点 */
data class CategoryNode(
    val name: String,
    val count: Int,
    val children: List<CategoryNode> = emptyList()
)

/**
 * 书籍数据源：总目录、精读档案、公版全文全部来自内置数据，自定义书与本地修改追加在其上。
 */
class CatalogRepository(private val context: Context) {

    private val lock = Mutex()
    private var catalog: List<Book> = emptyList()
    private val catalogById = HashMap<String, Book>()
    private val authorIndex = HashMap<String, MutableList<Book>>()

    private val dossierOffsets = HashMap<String, Long>()
    private val dossierLengths = HashMap<String, Int>()
    private val dossierCache = object : LinkedHashMap<String, Dossier>(48, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Dossier>) = size > 80
    }
    private val dossierFile: java.io.File by lazy { java.io.File(context.filesDir, "dossiers.idx") }

    private var textManifest: Map<String, TextMeta> = emptyMap()
    @Volatile
    private var loaded = false
    @Volatile
    private var indexReady = false

    val books: List<Book> get() = catalog

    /** 只加载目录（很快），档案索引在后台慢慢建，不阻塞首页 */
    suspend fun ensureLoaded() {
        if (loaded) return
        lock.withLock {
            if (loaded) return
            withContext(Dispatchers.IO) { loadCatalog() }
            loaded = true
        }
    }

    /** 后台建立精读档案索引；首页不等待它 */
    suspend fun ensureIndex() {
        if (indexReady) return
        withContext(Dispatchers.IO) {
            synchronized(this@CatalogRepository) {
                if (indexReady) return@withContext
                runCatching { buildDossierIndex() }
                indexReady = true
            }
        }
    }

    private fun loadCatalog() {
        val builtin = JSONArray(readAsset("catalog.json")).let { array ->
            buildList { for (i in 0 until array.length()) add(Book.fromJson(array.getJSONObject(i))) }
        }
        val custom = LocalLibrary.loadCustomBooks(context)
        catalog = custom + builtin
        catalogById.clear()
        authorIndex.clear()
        for (book in catalog) {
            catalogById[book.id] = book
            authorIndex.getOrPut(book.author) { mutableListOf() }.add(book)
        }
        textManifest = runCatching {
            val obj = JSONObject(readAsset("text-manifest.json"))
            buildMap {
                obj.keys().forEach { key ->
                    val item = obj.getJSONObject(key)
                    put(
                        key,
                        TextMeta(
                            id = key,
                            title = item.optString("title"),
                            source = item.optString("source"),
                            sourceUrl = item.optString("sourceUrl"),
                            sourceTitle = item.optString("sourceTitle"),
                            file = item.optString("file"),
                            chars = item.optInt("chars"),
                            downloadedAt = item.optString("downloadedAt")
                        )
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    /**
     * 档案总量约 40MB，首次启动时把内置档案搬到可随机读取的位置，
     * 再按条目建立「id -> 偏移量」索引，阅读时只读取需要的那一条。
     */
    private fun buildDossierIndex() {
        val meta = java.io.File(context.filesDir, "dossiers.meta")
        if (!dossierFile.exists() || !meta.exists() || meta.lastModified() < ASSET_STAMP) {
            copyDossierAsset()
            meta.writeText("ok")
            meta.setLastModified(ASSET_STAMP)
        }
        val offsets = HashMap<String, Long>()
        val lengths = HashMap<String, Int>()
        scanDossier(dossierFile, offsets, lengths)
        synchronized(this) {
            dossierOffsets.clear()
            dossierOffsets.putAll(offsets)
            dossierLengths.clear()
            dossierLengths.putAll(lengths)
        }
    }

    private fun copyDossierAsset() {
        context.assets.open("dossiers.json").use { input ->
            dossierFile.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
        }
    }

    private fun scanDossier(
        file: java.io.File,
        offsets: MutableMap<String, Long>,
        lengths: MutableMap<String, Int>
    ) {
        val sep = SEPARATOR.toByteArray(Charsets.UTF_8)
        file.inputStream().buffered(256 * 1024).use { stream ->
            val chunk = ByteArray(256 * 1024)
            var absolute = 0L
            var entryStart = 0L
            var matched = 0
            var head = ByteArray(0)
            var headLength = 0
            var inHead = true
            var read: Int
            while (stream.read(chunk).also { read = it } > 0) {
                for (i in 0 until read) {
                    val byte = chunk[i]
                    val pos = absolute + i
                    if (inHead && headLength < HEAD_SIZE) {
                        val next = ByteArray(headLength + 1)
                        System.arraycopy(head, 0, next, 0, headLength)
                        next[headLength] = byte
                        head = next
                        headLength++
                    }
                    if (byte == sep[matched]) {
                        matched++
                        if (matched == sep.size) {
                            val entryEnd = pos - sep.size + 1
                            addEntry(head, entryStart, entryEnd, offsets, lengths)
                            entryStart = pos + 1
                            matched = 0
                            head = ByteArray(0)
                            headLength = 0
                            inHead = true
                        }
                    } else {
                        matched = if (byte == sep[0]) 1 else 0
                    }
                }
                absolute += read
            }
            if (entryStart < file.length()) addEntry(head, entryStart, file.length(), offsets, lengths)
        }
    }

    private fun addEntry(
        head: ByteArray,
        start: Long,
        end: Long,
        offsets: MutableMap<String, Long>,
        lengths: MutableMap<String, Int>
    ) {
        val length = (end - start).toInt()
        if (length <= 0) return
        val text = String(head, 0, minOf(head.size, 1024), Charsets.UTF_8)
        val id = ID_REGEX.find(text)?.groupValues?.get(1) ?: return
        offsets[id] = start
        lengths[id] = length
    }

    private fun readAsset(name: String): String =
        context.assets.open(name).use { it.readBytes() }.toString(Charsets.UTF_8).removePrefix("\uFEFF")

    fun getBook(id: String): Book? = catalogById[id]

    fun authorWorks(book: Book): List<Book> {
        if (book.author.isBlank() || book.author in IGNORED_AUTHORS) return emptyList()
        return authorIndex[book.author]?.filter { it.id != book.id } ?: emptyList()
    }

    suspend fun similarBooks(book: Book, limit: Int = 6): List<Book> {
        ensureLoaded()
        val baseTags = book.tags.toSet()
        val usage = UsageStats.bookScores(context)
        return catalog.asSequence()
            .filter { it.id != book.id }
            .map { candidate ->
                var score = 0f
                if (candidate.category == book.category) score += 3f
                if (candidate.cat3.isNotBlank() && candidate.cat3 == book.cat3) score += 2f
                if (candidate.country == book.country) score += 1.2f
                if (candidate.era == book.era) score += 0.8f
                if (candidate.level == book.level) score += 0.3f
                if (candidate.author == book.author) score += 2.5f
                score += candidate.tags.count { baseTags.contains(it) } * 2f
                // 读者自己读得多、搜得多的书，相似推荐里也往前排
                score += (usage[candidate.id] ?: 0).coerceAtMost(10) * 0.4f
                candidate to score
            }
            .filter { it.second > 0f }
            .sortedWith(compareByDescending<Pair<Book, Float>> { it.second }.thenBy { levelRank(it.first.level) })
            .take(limit)
            .map { it.first }
            .toList()
    }

    /**
     * 今日推荐：重要程度 + 读者偏好（常读的类别、常搜的书）综合排序。
     * 没有使用记录时，就是按推荐等级排的经典书单。
     */
    fun recommendedBooks(limit: Int = 8): List<Book> {
        val usage = UsageStats.bookScores(context)
        val categoryScores = UsageStats.categoryScores(context, catalog)
        val maxCategory = (categoryScores.values.maxOrNull() ?: 0).coerceAtLeast(1)
        return catalog.asSequence()
            .filter { it.level == "殿堂级" || it.level == "经典" || (usage[it.id] ?: 0) > 0 }
            .map { book ->
                var score = (4 - levelRank(book.level)) * 2.0f
                score += (usage[book.id] ?: 0).coerceAtMost(20) * 0.6f
                val preference = ((categoryScores[book.cat3Label] ?: 0) + (categoryScores[book.category] ?: 0)) / 2f
                score += preference / maxCategory * 3f
                book to score
            }
            .sortedWith(compareByDescending<Pair<Book, Float>> { it.second }.thenBy { it.first.title })
            .take(limit)
            .map { it.first }
            .toList()
    }

    fun buildCategoryTree(): List<CategoryNode> {
        val tree = LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, Int>>>()
        for (book in catalog) {
            val c1 = book.cat1.ifBlank { "其他" }
            val c2 = book.cat2.ifBlank { "综合" }
            val c3 = book.cat3.ifBlank { book.category }
            val level2 = tree.getOrPut(c1) { LinkedHashMap() }
            val level3 = level2.getOrPut(c2) { LinkedHashMap() }
            level3[c3] = (level3[c3] ?: 0) + 1
        }
        return tree.map { (name, level2) ->
            CategoryNode(
                name = name,
                count = level2.values.sumOf { it.values.sum() },
                children = level2.map { (name2, level3) ->
                    CategoryNode(name2, level3.values.sum(), level3.map { (name3, count) -> CategoryNode(name3, count) })
                }
            )
        }
    }

    fun hasDetail(id: String): Boolean = LocalLibrary.hasDossier(context, id) || (indexReady && dossierOffsets.containsKey(id))

    fun detailCount(): Int = dossierOffsets.size + LocalLibrary.dossierIds(context).count { !dossierOffsets.containsKey(it) }

    fun textMeta(id: String): TextMeta? = textManifest[id]

    fun hasText(id: String): Boolean = textManifest.containsKey(id)

    suspend fun readTextFile(name: String): String = withContext(Dispatchers.IO) {
        context.assets.open(name).use { it.readBytes() }.toString(Charsets.UTF_8)
    }

    /** 读取精读档案：本地修改优先，其后是内置档案，最后是目录速览 */
    suspend fun getDossier(book: Book): Dossier {
        ensureLoaded()
        LocalLibrary.getDossier(context, book.id)?.let { return it }
        ensureIndex()
        builtinDossier(book.id)?.let { return it }
        return fallbackDossier(book)
    }

    fun dossierSource(book: Book): String = when {
        LocalLibrary.hasDossier(context, book.id) -> "local"
        indexReady && dossierOffsets.containsKey(book.id) -> "builtin"
        else -> "none"
    }

    private suspend fun builtinDossier(id: String): Dossier? {
        synchronized(dossierCache) { dossierCache[id]?.let { return it } }
        val offset = dossierOffsets[id] ?: return null
        val length = dossierLengths[id] ?: return null
        return withContext(Dispatchers.IO) {
            val raf = java.io.RandomAccessFile(dossierFile, "r")
            try {
                raf.seek(offset)
                val buffer = ByteArray(length)
                raf.readFully(buffer)
                runCatching { Dossier.fromJson(JSONObject(String(buffer, Charsets.UTF_8))) }.getOrNull()
            } finally {
                raf.close()
            }?.also { synchronized(dossierCache) { dossierCache[id] = it } }
        }
    }

    fun fallbackDossier(book: Book): Dossier {
        val tags = book.tags.joinToString("、")
        return Dossier(
            id = book.id,
            title = book.title,
            subtitle = book.subtitle.ifBlank { book.summary },
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
                "《${book.title}》是${book.era}${book.country}${book.category}中极具代表性的作品，常被列入“必读经典”书单。它值得反复阅读的地方，不只在故事本身，更在于它对人性、社会与时代的长期追问。",
                "阅读时可以抓住${tags}这几个关键词：先看作品如何提出问题，再看人物如何在处境中做出选择，最后把书中的问题带回自己的生活。"
            ),
            quotes = emptyList(),
            lecture = Lecture(
                title = "每天精读一本书：${book.title}",
                durationMinutes = 0,
                tone = "平实、亲切",
                opening = "今天要和大家聊的，是${book.country}${book.era}的《${book.title}》。${book.summary}这本书的精读讲稿正在整理中，下面先带大家从目录、题材和阅读方法三个角度，快速建立对它的整体认识。",
                sections = listOf(
                    LectureSection(
                        "一、先认识这本书",
                        listOf(
                            "《${book.title}》由${book.author}创作，属于${book.category}，预计阅读时间约 ${book.readingMinutes} 分钟。它围绕${tags}展开，是理解${book.era}精神面貌的一扇窗口。",
                            "第一次阅读不必追求记住所有细节。先弄清人物关系与核心冲突，再带着问题读第二遍，收获会明显不同。"
                        )
                    )
                ),
                closing = "完整的精读精华与二十五到三十分钟的演讲书稿正在补充中。你也可以先在“阅读原书”中导入自己的电子书，按章节慢慢读。"
            ),
            chapters = emptyList(),
            isFallback = true
        )
    }

    fun saveDossier(dossier: Dossier): Dossier {
        val saved = LocalLibrary.saveDossier(context, dossier)
        synchronized(dossierCache) { dossierCache.remove(dossier.id) }
        return saved
    }

    fun removeDossier(id: String) {
        LocalLibrary.removeDossier(context, id)
        synchronized(dossierCache) { dossierCache.remove(id) }
    }

    /** 新增、更新或删除自定义书后刷新内存目录 */
    fun reload() {
        val custom = LocalLibrary.loadCustomBooks(context)
        val builtin = catalog.filter { !it.custom && custom.none { c -> c.id == it.id } }
        catalog = custom + builtin
        catalogById.clear()
        authorIndex.clear()
        for (book in catalog) {
            catalogById[book.id] = book
            authorIndex.getOrPut(book.author) { mutableListOf() }.add(book)
        }
    }

    fun stats(): Stats {
        val china = catalog.count { it.country == "中国" }
        return Stats(
            total = catalog.size,
            categories = catalog.map { it.category }.toSet().size,
            china = china,
            covered = detailCount()
        )
    }

    fun todayBook(): Book {
        if (catalog.isEmpty()) return Book("", "", "", "", "", "", "", 0, emptyList(), "", "", "", "")
        return catalog[((System.currentTimeMillis() / 86_400_000L).toInt()) % catalog.size]
    }

    companion object {
        const val SEPARATOR = "\n@@DOSSIER@@\n"
        private const val HEAD_SIZE = 1024
        private val ASSET_STAMP = 1_700_000_000_000L
        private val IGNORED_AUTHORS = setOf("佚名", "不详", "未知", "")
        private val ID_REGEX = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")

        fun levelRank(level: String): Int = when (level) {
            "殿堂级" -> 0
            "经典" -> 1
            "重要" -> 2
            "拓展" -> 3
            else -> 4
        }
    }
}

data class Stats(val total: Int, val categories: Int, val china: Int, val covered: Int)
