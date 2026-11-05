package com.classics.readingroom.data

/** 多级分类筛选 + 关键词 + 年代 + 推荐等级 */
data class CatalogQuery(
    val keyword: String = "",
    val cat1: String? = null,
    val cat2: String? = null,
    val cat3: String? = null,
    val eraBucket: String? = null,
    val level: String? = null,
    val sortBy: String = SORT_CATEGORY
) {
    companion object {
        const val SORT_CATEGORY = "category"
        const val SORT_LEVEL = "level"
        const val SORT_TIME = "time"
        const val SORT_TITLE = "title"
    }
}

object CatalogSearch {
    private val ERA_ORDER = listOf("先秦", "秦汉", "魏晋南北朝", "隋唐五代", "宋元", "明代", "清代及以前", "晚清民初", "民国时期", "新中国初期", "当代")

    /** 同一本书只出现一次；各层筛选用“且”的关系，避免重复条目 */
    fun filter(books: List<Book>, query: CatalogQuery): List<Book> {
        val keyword = query.keyword.trim().lowercase()
        val seen = HashSet<String>(books.size)
        val result = ArrayList<Book>()
        for (book in books) {
            if (!seen.add(book.id)) continue
            if (query.cat1 != null && book.cat1 != query.cat1) continue
            if (query.cat2 != null && book.cat2 != query.cat2) continue
            if (query.cat3 != null && book.cat3Label != query.cat3) continue
            if (query.eraBucket != null && eraBucket(book.era) != query.eraBucket) continue
            if (query.level != null && book.level != query.level) continue
            if (keyword.isNotEmpty()) {
                val haystack = buildString {
                    append(book.title).append(' ')
                    append(book.author).append(' ')
                    append(book.era).append(' ')
                    append(book.country).append(' ')
                    append(book.category).append(' ')
                    append(book.summary).append(' ')
                    append(book.tags.joinToString(" "))
                }.lowercase()
                if (!haystack.contains(keyword)) continue
            }
            result.add(book)
        }
        return when (query.sortBy) {
            CatalogQuery.SORT_TITLE -> result.sortedBy { it.title }
            CatalogQuery.SORT_TIME -> result.sortedWith(compareByDescending<Book> { it.readingMinutes }.thenBy { it.title })
            CatalogQuery.SORT_LEVEL -> result.sortedWith(
                compareBy<Book> { CatalogRepository.levelRank(it.level) }.thenBy { it.title }
            )
            else -> result.sortedWith(
                compareBy<Book> { it.cat1 }.thenBy { it.cat2 }.thenBy { it.cat3Label }.thenBy { it.title }
            )
        }
    }

    fun eraBuckets(books: List<Book>): List<String> =
        books.map { eraBucket(it.era) }.toSet().sortedBy { ERA_ORDER.indexOf(it).let { index -> if (index < 0) 99 else index } }

    fun eraBucket(era: String): String {
        val year = Regex("\\d{3,4}").find(era)?.value?.toIntOrNull()
        if (year != null) {
            return when {
                year < 1840 -> "清代及以前"
                year < 1911 -> "晚清民初"
                year < 1949 -> "民国时期"
                year < 1978 -> "新中国初期"
                else -> "当代"
            }
        }
        return when {
            era.contains("先秦") -> "先秦"
            era.contains("秦") || era.contains("汉") -> "秦汉"
            era.contains("三国") || era.contains("晋") || era.contains("南") || era.contains("北") -> "魏晋南北朝"
            era.contains("隋") || era.contains("唐") || era.contains("五代") -> "隋唐五代"
            era.contains("宋") || era.contains("元") -> "宋元"
            era.contains("明") -> "明代"
            era.contains("清") -> "清代及以前"
            era.contains("民国") -> "民国时期"
            Regex("\\d0年代").containsMatchIn(era) -> "当代"
            else -> era.ifBlank { "其他" }
        }
    }
}
