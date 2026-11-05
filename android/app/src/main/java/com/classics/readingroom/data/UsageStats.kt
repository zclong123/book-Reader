package com.classics.readingroom.data

import android.content.Context
import org.json.JSONObject

/**
 * 使用记录：搜索词、打开过的书、读完的章节、收藏。
 *
 * 记录只保存在本机，用于调整「今日推荐」与「相似的书」的排序：
 * 常被搜索、常被打开、读得深的书会往前排，同类别的偏好也会被放大。
 * 设置页可以导出汇总文件，也可以一键清空。
 */
object UsageStats {
    private const val PREFS = "classics_usage"
    private const val KEY_SEARCH = "searches"
    private const val KEY_OPEN = "opens"
    private const val KEY_READ = "reads"
    private const val KEY_FAVORITE = "favorites"
    private const val KEY_LAST_SEARCH = "last_search"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun readMap(context: Context, key: String): MutableMap<String, Int> {
        val raw = prefs(context).getString(key, null) ?: return mutableMapOf()
        return runCatching {
            val obj = JSONObject(raw)
            val map = mutableMapOf<String, Int>()
            obj.keys().forEach { k -> map[k] = obj.optInt(k, 0) }
            map
        }.getOrElse { mutableMapOf() }
    }

    private fun writeMap(context: Context, key: String, map: Map<String, Int>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs(context).edit().putString(key, obj.toString()).apply()
    }

    private fun bump(context: Context, key: String, id: String, step: Int = 1) {
        if (id.isBlank()) return
        val map = readMap(context, key)
        map[id] = (map[id] ?: 0) + step
        writeMap(context, key, map)
    }

    /** 记录一次搜索（关键词与命中的书） */
    fun recordSearch(context: Context, keyword: String, hits: List<Book>) {
        val word = keyword.trim()
        if (word.length < 2) return
        val map = readMap(context, KEY_SEARCH)
        map[word] = (map[word] ?: 0) + 1
        // 只保留最近 200 个关键词
        val trimmed = map.entries.sortedByDescending { it.value }.take(200).associate { it.key to it.value }
        writeMap(context, KEY_SEARCH, trimmed)
        prefs(context).edit().putString(KEY_LAST_SEARCH, word).apply()
        hits.take(3).forEach { bump(context, KEY_OPEN, it.id) }
    }

    fun recordOpen(context: Context, bookId: String) = bump(context, KEY_OPEN, bookId)

    /** 每翻一章记一次，读得越深热度越高 */
    fun recordRead(context: Context, bookId: String, chapter: Int) {
        if (chapter < 0) return
        bump(context, KEY_READ, bookId)
    }

    fun recordFavorite(context: Context, bookId: String, added: Boolean) {
        if (added) bump(context, KEY_FAVORITE, bookId, 2) else bump(context, KEY_FAVORITE, bookId, -1)
    }

    fun lastSearch(context: Context): String = prefs(context).getString(KEY_LAST_SEARCH, "") ?: ""

    /** 每本书的热度分：打开 1 分、阅读 1 分、收藏 2 分 */
    fun bookScores(context: Context): Map<String, Int> {
        val scores = HashMap<String, Int>()
        readMap(context, KEY_OPEN).forEach { (id, n) -> scores[id] = (scores[id] ?: 0) + n }
        readMap(context, KEY_READ).forEach { (id, n) -> scores[id] = (scores[id] ?: 0) + n }
        readMap(context, KEY_FAVORITE).forEach { (id, n) -> scores[id] = (scores[id] ?: 0) + n }
        return scores
    }

    /** 读者偏好的类别（按打开与阅读次数累计） */
    fun categoryScores(context: Context, books: List<Book>): Map<String, Int> {
        val byId = books.associateBy { it.id }
        val scores = HashMap<String, Int>()
        bookScores(context).forEach { (id, n) ->
            val book = byId[id] ?: return@forEach
            scores[book.cat3Label] = (scores[book.cat3Label] ?: 0) + n
            scores[book.category] = (scores[book.category] ?: 0) + n
        }
        return scores
    }

    fun searchCount(context: Context): Int = readMap(context, KEY_SEARCH).values.sum()

    fun topSearches(context: Context, limit: Int = 8): List<Pair<String, Int>> =
        readMap(context, KEY_SEARCH).entries.sortedByDescending { it.value }.take(limit).map { it.key to it.value }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    /** 导出汇总：可直接用于调整推荐排序 */
    fun export(context: Context, books: List<Book>): String {
        val byId = books.associateBy { it.id }
        val obj = JSONObject()
        obj.put("exportedAt", System.currentTimeMillis())
        obj.put("searches", JSONObject().apply { readMap(context, KEY_SEARCH).forEach { (k, v) -> put(k, v) } })
        obj.put("books", JSONObject().apply {
            bookScores(context).forEach { (id, score) ->
                val book = byId[id] ?: return@forEach
                put(id, JSONObject().apply {
                    put("title", book.title)
                    put("author", book.author)
                    put("category", book.category)
                    put("score", score)
                })
            }
        })
        obj.put("categories", JSONObject().apply { categoryScores(context, books).forEach { (k, v) -> put(k, v) } })
        return obj.toString(2)
    }
}
