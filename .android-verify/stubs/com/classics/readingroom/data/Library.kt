package com.classics.readingroom.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

/**
 * 内置馆藏：总目录、精读档案、公版全文都打包在安装包里，断网也能用。
 *
 * 精读档案是一个条目分隔的大文件（dossiers.json）。首次启动时把它复制到应用私有目录，
 * 再并行建立「id → 字符偏移」索引；打开某本书时只读取它所在的那一段，所以启动快、内存省。
 * 若索引尚未完成（冷启动后立刻点开一本书），会退回到逐段流式查找。
 */
class Library private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("classics-store", Context.MODE_PRIVATE)

    val catalog: List<Book>
    private val catalogById: Map<String, Book>
    private val builtinIds: Set<String>

    private val dossierOffsets = HashMap<String, LongArray>()
    @Volatile
    private var indexReady = false
    @Volatile
    private var indexingStarted = false

    val texts: Map<String, TextMeta>

    init {
        catalog = runCatching {
            val raw = appContext.assets.open("catalog.json").bufferedReader().use { it.readText() }
            val array = JSONArray(raw)
            ArrayList<Book>(array.length()).apply {
                for (i in 0 until array.length()) add(Book.fromJson(array.getJSONObject(i)))
            }
        }.getOrElse { emptyList() }
        catalogById = catalog.associateBy { it.id }
        builtinIds = catalogById.keys

        texts = runCatching {
            val raw = appContext.assets.open("text-manifest.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(raw)
            val map = HashMap<String, TextMeta>()
            obj.keys().forEach { id ->
                val meta = obj.optJSONObject(id) ?: return@forEach
                map[id] = TextMeta(
                    id = id,
                    title = meta.optString("title"),
                    source = meta.optString("source"),
                    sourceUrl = meta.optString("sourceUrl"),
                    sourceTitle = meta.optString("sourceTitle"),
                    file = meta.optString("file"),
                    chars = meta.optInt("chars", 0),
                    downloadedAt = meta.optString("downloadedAt")
                )
            }
            map
        }.getOrElse { emptyMap() }
    }

    /* ---------------- 精读档案：复制 + 索引 ---------------- */

    suspend fun ensureIndex() = withContext(Dispatchers.IO) {
        if (indexReady) return@withContext
        synchronized(this@Library) {
            if (indexingStarted) return@withContext
            indexingStarted = true
        }
        try {
            val file = dossierFile()
            file.inputStream().buffered(DEFAULT_BUFFER_SIZE).use { stream ->
                val separator = SEPARATOR.toByteArray()
                val window = ByteArray(separator.size)
                var filled = 0
                var position = 0L
                var entryStart = 0L
                var collectingId = false
                var idStart = 0L
                val idBuilder = StringBuilder()
                while (true) {
                    val next = stream.read()
                    if (next < 0) break
                    val c = next.toChar()

                    if (collectingId) {
                        when (c) {
                            '"' -> {
                                collectingId = false
                                dossierOffsets[idBuilder.toString()] = longArrayOf(idStart, position)
                                idBuilder.setLength(0)
                            }
                            '\\' -> {
                                val escaped = stream.read()
                                if (escaped < 0) break
                                if (escaped != -1) idBuilder.append(escaped.toChar())
                                position += 2
                                continue
                            }
                            else -> idBuilder.append(c)
                        }
                        position += 1
                        continue
                    }

                    window[filled % window.size] = next.toByte()
                    filled += 1
                    if (filled >= window.size) {
                        var matched = true
                        for (k in 0 until window.size) {
                            if (window[(filled - window.size + k) % window.size] != separator[k]) {
                                matched = false
                                break
                            }
                        }
                        if (matched) {
                            entryStart = position + 1
                        }
                    }

                    if (position == entryStart) {
                        // 条目以 `{"id":"` 开头，进入 id 收集
                        collectingId = true
                        idStart = entryStart + ID_PREFIX.length
                        idBuilder.setLength(0)
                    }
                    position += 1
                }
            }
            indexReady = true
        } catch (_: Exception) {
            indexReady = false
        }
    }

    private fun dossierFile(): File {
        val target = File(appContext.filesDir, DOSSIER_COPY)
        if (target.exists() && target.length() > 0L) return target
        val temp = File(appContext.filesDir, "$DOSSIER_COPY.tmp")
        appContext.assets.open(DOSSIER_ASSET).use { input ->
            temp.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        return target
    }

    fun hasBuiltin(id: String) = builtinIds.contains(id)

    fun hasDetail(id: String): Boolean {
        if (prefs.contains(KEY_EDITED_PREFIX + id)) return true
        if (hasCustomDossier(id)) return true
        return hasBuiltin(id) && (indexReady || dossierFile().exists())
    }

    /** 读取精读档案：本机编辑版本 > 自定义书 > 馆内档案 */
    suspend fun detail(id: String): Dossier? = withContext(Dispatchers.IO) {
        editedDossier(id)?.let { return@withContext it }
        customDossier(id)?.let { return@withContext it }
        ensureIndex()
        readBuiltin(id) ?: scanForEntry(id)
    }

    private fun readBuiltin(id: String): Dossier? {
        val range = dossierOffsets[id] ?: return null
        val file = dossierFile()
        if (!file.exists()) return null
        return runCatching {
            val length = (range[1] - range[0]).toInt()
            if (length <= 0) return@runCatching null
            val buffer = java.nio.ByteBuffer.allocate(length)
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(range[0])
                var remaining = length
                while (remaining > 0) {
                    val read = raf.channel.read(buffer)
                    if (read <= 0) break
                    remaining -= read
                }
            }
            buffer.flip()
            Dossier.fromJson(JSONObject(Charsets.UTF_8.decode(buffer).toString()))
        }.getOrNull()
    }

    /** 索引尚未完成时的兜底：流式切分查找 */
    private fun scanForEntry(id: String): Dossier? {
        val file = dossierFile()
        if (!file.exists()) return null
        val needle = "\"id\":\"$id\""
        val builder = StringBuilder()
        var inEntry = false
        var found: Dossier? = null
        try {
            file.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line == SEPARATOR_TRIMMED) {
                        if (inEntry && builder.contains(needle)) {
                            found = Dossier.fromJson(JSONObject(builder.toString()))
                            return@useLines
                        }
                        inEntry = false
                        builder.setLength(0)
                        continue
                    }
                    if (!inEntry) {
                        if (!line.contains(needle)) continue
                        inEntry = true
                    }
                    if (builder.isNotEmpty()) builder.append('\n')
                    builder.append(line)
                }
                if (inEntry && builder.contains(needle)) {
                    found = Dossier.fromJson(JSONObject(builder.toString()))
                }
            }
        } catch (_: Exception) {
            return null
        }
        return found
    }

    /* ---------------- 自定义书架（AI 生成 / 手动加入） ---------------- */

    fun customBooks(): List<Book> = runCatching {
        val raw = prefs.getString(KEY_CUSTOM, null) ?: return emptyList()
        val array = JSONArray(raw)
        ArrayList<Book>(array.length()).apply {
            for (i in 0 until array.length()) {
                val book = Book.fromJson(array.getJSONObject(i))
                if (book.id.isNotBlank() && book.title.isNotBlank()) add(book)
            }
        }
    }.getOrElse { emptyList() }

    private fun saveCustomBooks(list: List<Book>) {
        prefs.edit().putString(KEY_CUSTOM, booksToJson(list)).apply()
    }

    fun addCustomBook(book: Book) {
        saveCustomBooks(listOf(book) + customBooks().filter { it.id != book.id })
    }

    fun removeCustomBook(id: String) {
        saveCustomBooks(customBooks().filter { it.id != id })
        removeDossier(id)
        val array = runCatching { JSONArray(prefs.getString(KEY_CUSTOM_DOSSIERS, "[]") ?: "[]") }
            .getOrElse { JSONArray() }
        val kept = JSONArray()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optString("id") != id) kept.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_DOSSIERS, kept.toString()).apply()
    }

    fun saveCustomDossier(dossier: Dossier) {
        val array = runCatching { JSONArray(prefs.getString(KEY_CUSTOM_DOSSIERS, "[]") ?: "[]") }
            .getOrElse { JSONArray() }
        val kept = JSONArray()
        kept.put(dossier.toJson())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optString("id") != dossier.id) kept.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_DOSSIERS, kept.toString()).apply()
    }

    private fun hasCustomDossier(id: String): Boolean = runCatching {
        val array = JSONArray(prefs.getString(KEY_CUSTOM_DOSSIERS, "[]") ?: "[]")
        for (i in 0 until array.length()) {
            if (array.optJSONObject(i)?.optString("id") == id) return true
        }
        false
    }.getOrElse { false }

    private fun customDossier(id: String): Dossier? = runCatching {
        val array = JSONArray(prefs.getString(KEY_CUSTOM_DOSSIERS, "[]") ?: "[]")
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optString("id") == id) return Dossier.fromJson(obj)
        }
        null
    }.getOrNull()

    /* ---------------- 读者编辑的覆盖层 ---------------- */

    private fun editedDossier(id: String): Dossier? = runCatching {
        val raw = prefs.getString(KEY_EDITED_PREFIX + id, null) ?: return null
        Dossier.fromJson(JSONObject(raw))
    }.getOrNull()

    fun saveDossier(dossier: Dossier) {
        prefs.edit().putString(KEY_EDITED_PREFIX + dossier.id, dossier.toJson().toString()).apply()
    }

    fun removeDossier(id: String) {
        prefs.edit().remove(KEY_EDITED_PREFIX + id).apply()
    }

    fun hasEdited(id: String) = prefs.contains(KEY_EDITED_PREFIX + id)

    /* ---------------- 公版原书全文 ---------------- */

    suspend fun text(meta: TextMeta): String = withContext(Dispatchers.IO) {
        appContext.assets.open(meta.file).bufferedReader().use { it.readText() }
    }

    /* ---------------- 检索与推荐 ---------------- */

    fun allBooks(): List<Book> = customBooks() + catalog

    fun book(id: String): Book? = allBooks().firstOrNull { it.id == id }

    fun authorWorks(author: String): List<Book> {
        if (author.isBlank() || UNKNOWN_AUTHORS.contains(author)) return emptyList()
        return allBooks().filter { it.author == author }
    }

    suspend fun similarBooks(book: Book, limit: Int = 4): List<Book> {
        val baseTags = book.tags.toSet()
        return allBooks()
            .asSequence()
            .filter { it.id != book.id }
            .map { candidate ->
                var score = 0.0
                if (candidate.category == book.category) score += 3
                if (candidate.country == book.country) score += 1.2
                if (candidate.era == book.era) score += 0.8
                if (candidate.level == book.level) score += 0.3
                score += candidate.tags.count { baseTags.contains(it) } * 2.0
                candidate to score
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
            .toList()
    }

    fun todayBook(): Book? {
        val list = catalog
        if (list.isEmpty()) return null
        val day = System.currentTimeMillis() / 86_400_000L
        return list[(day % list.size).toInt()]
    }

    /* ---------------- 收藏 / 阅读进度 / 偏好设置 ---------------- */

    fun favorites(): Set<String> = runCatching {
        val array = JSONArray(prefs.getString(KEY_FAVORITES, "[]") ?: "[]")
        (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }.toSet()
    }.getOrElse { emptySet() }

    fun toggleFavorite(id: String): Boolean {
        val current = favorites().toMutableSet()
        val added = if (current.contains(id)) {
            current.remove(id)
            false
        } else {
            current.add(id)
            true
        }
        prefs.edit().putString(KEY_FAVORITES, JSONArray(current.toList()).toString()).apply()
        return added
    }

    fun readProgress(id: String): Pair<Int, Int> {
        val chapter = prefs.getInt(KEY_PROGRESS_PREFIX + id + ":chapter", 0)
        val total = prefs.getInt(KEY_PROGRESS_PREFIX + id + ":total", 0)
        return chapter to total
    }

    fun saveReadProgress(id: String, chapter: Int, total: Int) {
        prefs.edit()
            .putInt(KEY_PROGRESS_PREFIX + id + ":chapter", chapter)
            .putInt(KEY_PROGRESS_PREFIX + id + ":total", total)
            .apply()
    }

    var readerFontSize: Int
        get() = prefs.getInt(KEY_FONT_SIZE, 18).coerceIn(14, 30)
        set(value) = prefs.edit().putInt(KEY_FONT_SIZE, value.coerceIn(14, 30)).apply()

    var readerNightMode: Boolean
        get() = prefs.getBoolean(KEY_NIGHT, false)
        set(value) = prefs.edit().putBoolean(KEY_NIGHT, value).apply()

    var ttsRate: Float
        get() = prefs.getFloat(KEY_TTS_RATE, 1f)
        set(value) = prefs.edit().putFloat(KEY_TTS_RATE, value.coerceIn(0.5f, 2f)).apply()

    var ttsStyle: String
        get() = prefs.getString(KEY_TTS_STYLE, "anchor") ?: "anchor"
        set(value) = prefs.edit().putString(KEY_TTS_STYLE, value).apply()

    var aiSettings: JSONObject
        get() = runCatching { JSONObject(prefs.getString(KEY_AI, "{}") ?: "{}") }.getOrElse { JSONObject() }
        set(value) = prefs.edit().putString(KEY_AI, value.toString()).apply()

    /* ---------------- 内置精读档案的数量统计（后台预热用） ---------------- */

    suspend fun countBuiltinDossiers(): Int = withContext(Dispatchers.IO) {
        ensureIndex()
        dossierOffsets.size
    }

    suspend fun warmUp() = coroutineScope {
        val indexing = async(Dispatchers.IO) { ensureIndex() }
        indexing.await()
    }

    companion object {
        private const val DOSSIER_ASSET = "dossiers.json"
        private const val DOSSIER_COPY = "dossiers.bin"
        private const val SEPARATOR = "\n@@DOSSIER@@\n"
        private const val SEPARATOR_TRIMMED = "@@DOSSIER@@"
        private const val ID_PREFIX = "{\"id\":\""
        private const val KEY_CUSTOM = "custom_books"
        private const val KEY_CUSTOM_DOSSIERS = "custom_dossiers"
        private const val KEY_EDITED_PREFIX = "edited:"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_PROGRESS_PREFIX = "progress:"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_NIGHT = "night_mode"
        private const val KEY_TTS_RATE = "tts_rate"
        private const val KEY_TTS_STYLE = "tts_style"
        private const val KEY_AI = "ai_settings"

        val UNKNOWN_AUTHORS = setOf("佚名", "不详", "未知", "")

        fun booksToJson(list: List<Book>): String {
            val array = JSONArray()
            list.forEach { array.put(it.toJson()) }
            return array.toString()
        }

        @Volatile
        private var instance: Library? = null

        fun get(context: Context): Library =
            instance ?: synchronized(this) {
                instance ?: Library(context).also { instance = it }
            }
    }
}
