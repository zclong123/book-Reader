package com.classics.readingroom.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 收藏、阅读进度、偏好、自定义书与本机修改过的精读详情 */
object LocalLibrary {
    private const val PREFS = "classics_room"
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_CUSTOM_BOOKS = "custom_books"
    private const val KEY_DOSSIERS = "dossiers"
    private const val KEY_SETTINGS = "ai_settings"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /* ---------------- 收藏 ---------------- */

    fun favorites(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_FAVORITES, "[]") ?: "[]"
        return runCatching { JSONArray(raw).toStringList() }.getOrDefault(emptyList())
    }

    fun isFavorite(context: Context, id: String) = favorites(context).contains(id)

    fun toggleFavorite(context: Context, id: String): Boolean {
        val list = favorites(context).toMutableList()
        val nowFavorite = if (list.contains(id)) {
            list.remove(id)
            false
        } else {
            list.add(0, id)
            true
        }
        prefs(context).edit().putString(KEY_FAVORITES, JSONArray(list).toString()).apply()
        return nowFavorite
    }

    /* ---------------- 阅读进度与偏好 ---------------- */

    fun readProgress(context: Context, bookId: String): Int =
        prefs(context).getInt("progress_$bookId", 0)

    fun saveReadProgress(context: Context, bookId: String, chapter: Int) {
        prefs(context).edit().putInt("progress_$bookId", chapter).apply()
    }

    fun importedText(context: Context, bookId: String): Pair<String, String>? {
        val name = prefs(context).getString("import_name_$bookId", null) ?: return null
        val text = prefs(context).getString("import_text_$bookId", null) ?: return null
        return name to text
    }

    fun saveImportedText(context: Context, bookId: String, name: String, text: String) {
        prefs(context).edit().putString("import_name_$bookId", name).putString("import_text_$bookId", text).apply()
    }

    fun clearImportedText(context: Context, bookId: String) {
        prefs(context).edit().remove("import_name_$bookId").remove("import_text_$bookId").apply()
    }

    fun fontSize(context: Context): Int = prefs(context).getInt("reader_font", 18)

    fun saveFontSize(context: Context, size: Int) {
        prefs(context).edit().putInt("reader_font", size).apply()
    }

    fun nightMode(context: Context): Boolean = prefs(context).getBoolean("reader_night", false)

    fun saveNightMode(context: Context, night: Boolean) {
        prefs(context).edit().putBoolean("reader_night", night).apply()
    }

    /* ---------------- 自定义书 ---------------- */

    fun loadCustomBooks(context: Context): List<Book> {
        val raw = prefs(context).getString(KEY_CUSTOM_BOOKS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList { for (i in 0 until array.length()) add(Book.fromJson(array.getJSONObject(i))) }
        }.getOrDefault(emptyList())
    }

    fun saveCustomBooks(context: Context, books: List<Book>) {
        prefs(context).edit().putString(KEY_CUSTOM_BOOKS, JSONArray().apply { books.forEach { put(it.toJson()) } }.toString()).apply()
    }

    fun addCustomBook(context: Context, book: Book) {
        val next = listOf(book) + loadCustomBooks(context).filter { it.id != book.id }
        saveCustomBooks(context, next)
    }

    fun removeCustomBook(context: Context, id: String) {
        saveCustomBooks(context, loadCustomBooks(context).filter { it.id != id })
        removeDossier(context, id)
    }

    fun newCustomId(): String = "custom-${System.currentTimeMillis().toString(36)}"

    /* ---------------- 本机精读档案 ---------------- */

    fun dossierIds(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_DOSSIERS, "{}") ?: "{}"
        return runCatching { JSONObject(raw).keys().asSequence().toList() }.getOrDefault(emptyList())
    }

    fun hasDossier(context: Context, id: String): Boolean {
        val raw = prefs(context).getString(KEY_DOSSIERS, "{}") ?: "{}"
        return runCatching { JSONObject(raw).has(id) }.getOrDefault(false)
    }

    fun getDossier(context: Context, id: String): Dossier? {
        val raw = prefs(context).getString(KEY_DOSSIERS, "{}") ?: "{}"
        return runCatching {
            val obj = JSONObject(raw)
            obj.optJSONObject(id)?.let { Dossier.fromJson(it) }
        }.getOrNull()
    }

    fun saveDossier(context: Context, dossier: Dossier): Dossier {
        val saved = dossier.copy(savedAt = System.currentTimeMillis())
        val raw = prefs(context).getString(KEY_DOSSIERS, "{}") ?: "{}"
        val obj = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        obj.put(saved.id, saved.toJson())
        prefs(context).edit().putString(KEY_DOSSIERS, obj.toString()).apply()
        return saved
    }

    fun removeDossier(context: Context, id: String) {
        val raw = prefs(context).getString(KEY_DOSSIERS, "{}") ?: "{}"
        val obj = runCatching { JSONObject(raw) }.getOrElse { return }
        obj.remove(id)
        prefs(context).edit().putString(KEY_DOSSIERS, obj.toString()).apply()
    }

    /* ---------------- 模型服务设置 ---------------- */

    fun aiSettings(context: Context): JSONObject {
        val raw = prefs(context).getString(KEY_SETTINGS, null) ?: return JSONObject()
        return runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
    }

    fun saveAiSettings(context: Context, settings: JSONObject) {
        prefs(context).edit().putString(KEY_SETTINGS, settings.toString()).apply()
    }
}
