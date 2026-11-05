package com.classics.readingroom.data

import org.json.JSONArray
import org.json.JSONObject

/** 总目录中的一部作品 */
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val country: String,
    val era: String,
    val category: String,
    val level: String,
    val readingMinutes: Int,
    val tags: List<String>,
    val summary: String,
    val cat1: String,
    val cat2: String,
    val cat3: String,
    val custom: Boolean = false,
    val aiGenerated: Boolean = false,
    val model: String = "",
    val authorIntro: String = "",
    val subtitle: String = ""
) {
    val cat3Label: String get() = cat3.ifBlank { category }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("author", author)
        put("country", country)
        put("era", era)
        put("category", category)
        put("level", level)
        put("readingMinutes", readingMinutes)
        put("tags", JSONArray(tags))
        put("summary", summary)
        put("cat1", cat1)
        put("cat2", cat2)
        put("cat3", cat3)
        put("custom", custom)
        put("aiGenerated", aiGenerated)
        put("model", model)
        put("authorIntro", authorIntro)
        put("subtitle", subtitle)
    }

    companion object {
        fun fromJson(obj: JSONObject): Book = Book(
            id = obj.optString("id"),
            title = obj.optString("title"),
            author = obj.optString("author", "佚名"),
            country = obj.optString("country", "中国"),
            era = obj.optString("era", ""),
            category = obj.optString("category", "文史类"),
            level = obj.optString("level", "经典"),
            readingMinutes = obj.optInt("readingMinutes", 120),
            tags = obj.optJSONArray("tags").toStringList(),
            summary = obj.optString("summary"),
            cat1 = obj.optString("cat1", "其他"),
            cat2 = obj.optString("cat2", "综合"),
            cat3 = obj.optString("cat3", obj.optString("category", "未分类")),
            custom = obj.optBoolean("custom", false),
            aiGenerated = obj.optBoolean("aiGenerated", false),
            model = obj.optString("model"),
            authorIntro = obj.optString("authorIntro"),
            subtitle = obj.optString("subtitle")
        )
    }
}

data class Quote(val text: String, val source: String, val note: String) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("text", text)
        put("source", source)
        put("note", note)
    }

    companion object {
        fun fromJson(obj: JSONObject) = Quote(
            text = obj.optString("text"),
            source = obj.optString("source", "书中"),
            note = obj.optString("note")
        )
    }
}

data class LectureSection(val title: String, val paragraphs: List<String>) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("title", title)
        put("paragraphs", JSONArray(paragraphs))
    }

    companion object {
        fun fromJson(obj: JSONObject) = LectureSection(
            title = obj.optString("title", "精读段落"),
            paragraphs = obj.optJSONArray("paragraphs").toStringList()
        )
    }
}

data class ChapterGuide(val title: String, val guide: String, val excerpt: String) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("title", title)
        put("guide", guide)
        put("excerpt", excerpt)
    }

    companion object {
        fun fromJson(obj: JSONObject) = ChapterGuide(
            title = obj.optString("title"),
            guide = obj.optString("guide"),
            excerpt = obj.optString("excerpt")
        )
    }
}

data class Lecture(
    val title: String,
    val durationMinutes: Int,
    val tone: String,
    val opening: String,
    val sections: List<LectureSection>,
    val closing: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("title", title)
        put("durationMinutes", durationMinutes)
        put("tone", tone)
        put("opening", opening)
        put("sections", JSONArray().apply { sections.forEach { put(it.toJson()) } })
        put("closing", closing)
    }

    companion object {
        fun fromJson(obj: JSONObject?): Lecture {
            if (obj == null) return Lecture("", 0, "", "", emptyList(), "")
            return Lecture(
                title = obj.optString("title"),
                durationMinutes = obj.optInt("durationMinutes", 0),
                tone = obj.optString("tone"),
                opening = obj.optString("opening"),
                sections = obj.optJSONArray("sections").toObjectList { LectureSection.fromJson(it) },
                closing = obj.optString("closing")
            )
        }
    }
}

/** 一部作品的精读档案：阅读精华 + 演讲书稿 + 章节导读 */
data class Dossier(
    val id: String,
    val title: String,
    val subtitle: String,
    val author: String,
    val authorIntro: String,
    val country: String,
    val era: String,
    val category: String,
    val tags: List<String>,
    val readingMinutes: Int,
    val summary: String,
    val story: List<String>,
    val insights: List<String>,
    val quotes: List<Quote>,
    val lecture: Lecture,
    val chapters: List<ChapterGuide>,
    val isFallback: Boolean = false,
    val savedAt: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("subtitle", subtitle)
        put("author", author)
        put("authorIntro", authorIntro)
        put("country", country)
        put("era", era)
        put("category", category)
        put("tags", JSONArray(tags))
        put("readingMinutes", readingMinutes)
        put("summary", summary)
        put("essence", JSONObject().apply {
            put("story", JSONArray(story))
            put("insights", JSONArray(insights))
            put("quotes", JSONArray().apply { quotes.forEach { put(it.toJson()) } })
        })
        put("lecture", lecture.toJson())
        put("chapters", JSONArray().apply { chapters.forEach { put(it.toJson()) } })
        put("isFallback", isFallback)
        put("savedAt", savedAt)
    }

    companion object {
        fun fromJson(obj: JSONObject): Dossier {
            val essence = obj.optJSONObject("essence") ?: JSONObject()
            return Dossier(
                id = obj.optString("id"),
                title = obj.optString("title"),
                subtitle = obj.optString("subtitle"),
                author = obj.optString("author"),
                authorIntro = obj.optString("authorIntro"),
                country = obj.optString("country"),
                era = obj.optString("era"),
                category = obj.optString("category"),
                tags = obj.optJSONArray("tags").toStringList(),
                readingMinutes = obj.optInt("readingMinutes", 0),
                summary = obj.optString("summary"),
                story = essence.optJSONArray("story").toStringList(),
                insights = essence.optJSONArray("insights").toStringList(),
                quotes = essence.optJSONArray("quotes").toObjectList { Quote.fromJson(it) },
                lecture = Lecture.fromJson(obj.optJSONObject("lecture")),
                chapters = obj.optJSONArray("chapters").toObjectList { ChapterGuide.fromJson(it) },
                isFallback = obj.optBoolean("isFallback", false),
                savedAt = obj.optLong("savedAt", 0L)
            )
        }
    }
}

/** 公版原书清单条目 */
data class TextMeta(
    val id: String,
    val title: String,
    val source: String,
    val sourceUrl: String,
    val sourceTitle: String,
    val file: String,
    val chars: Int,
    val downloadedAt: String
)

/** 朗读片段：一段文字 + 在整篇中的序号 */
data class Segment(val text: String, val index: Int)

internal fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    val list = ArrayList<String>(length())
    for (i in 0 until length()) {
        val value = optString(i, "")
        if (value.isNotBlank()) list.add(value)
    }
    return list
}

internal fun <T> JSONArray?.toObjectList(mapper: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    val list = ArrayList<T>(length())
    for (i in 0 until length()) {
        val obj = optJSONObject(i) ?: continue
        list.add(mapper(obj))
    }
    return list
}
