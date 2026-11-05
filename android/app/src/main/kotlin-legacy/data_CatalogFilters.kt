package com.classics.readingroom.data

/** 三级分类节点（带作品数量） */
data class CategoryNode(
    val name: String,
    val count: Int,
    val children: List<CategoryNode> = emptyList()
)

data class CatalogQuery(
    val keyword: String = "",
    val cat1: String = ALL,
    val cat2: String = ALL,
    val cat3: String = ALL,
    val eraBucket: String = ALL,
    val level: String = ALL,
    val sort: Sort = Sort.CATEGORY
) {
    companion object {
        const val ALL = "全部"
    }
}

enum class Sort(val label: String) {
    CATEGORY("按类别"),
    LEVEL("按推荐等级"),
    TIME("按阅读时长"),
    TITLE("按书名")
}

object CatalogFilters {
    private val LEVEL_RANK = mapOf("殿堂级" to 0, "经典" to 1, "重要" to 2, "拓展" to 3, "自定义" to 4)

    private val ERA_ORDER = listOf(
        "先秦", "秦汉", "魏晋南北朝", "隋唐五代", "宋元", "明代", "清代及以前",
        "晚清民初", "民国时期", "新中国初期", "当代", "其他"
    )

    /** 从目录提取实际存在的三级分类树 */
    fun buildTree(books: List<Book>): List<CategoryNode> {
        val tree = LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, Int>>>()
        for (book in books) {
            val level1 = tree.getOrPut(book.cat1.ifBlank { "其他" }) { LinkedHashMap() }
            val level2 = level1.getOrPut(book.cat2.ifBlank { "综合" }) { LinkedHashMap() }
            val level3 = book.cat3.ifBlank { book.category }.ifBlank { "未分类" }
            level2[level3] = (level2[level3] ?: 0) + 1
        }
        return tree.map { (name1, level2) ->
            val children2 = level2.map { (name2, level3) ->
                CategoryNode(
                    name = name2,
                    count = level3.values.sum(),
                    children = level3.map { (name3, count) -> CategoryNode(name3, count) }
                )
            }
            CategoryNode(name = name1, count = children2.sumOf { it.count }, children = children2)
        }
    }

    fun eraBuckets(books: List<Book>): List<String> {
        val present = books.map { eraBucket(it.era) }.toSet()
        val ordered = ERA_ORDER.filter { present.contains(it) }
        val rest = present.filterNot { ERA_ORDER.contains(it) }.sorted()
        return ordered + rest
    }

    fun eraBucket(era: String): String {
        val yearMatch = Regex("\\d{3,4}").find(era)?.value?.toIntOrNull()
        if (yearMatch != null) {
            return when {
                yearMatch < 1840 -> "清代及以前"
                yearMatch < 1911 -> "晚清民初"
                yearMatch < 1949 -> "民国时期"
                yearMatch < 1978 -> "新中国初期"
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
            Regex("[0-9]0年代").containsMatchIn(era) -> "当代"
            era.isBlank() -> "其他"
            else -> era
        }
    }

    /**
     * 组合筛选。三级分类严格按层级匹配 + 按 id 去重，
     * 保证同时选择多个条件时不会出现重复作品，也不会串到别的分类。
     */
    fun filter(books: List<Book>, query: CatalogQuery): List<Book> {
        val keyword = query.keyword.trim().lowercase()
        val seen = HashSet<String>()
        val list = books.filter { book ->
            if (!seen.add(book.id)) return@filter false
            if (query.cat1 != CatalogQuery.ALL && book.cat1 != query.cat1) return@filter false
            if (query.cat2 != CatalogQuery.ALL && book.cat2 != query.cat2) return@filter false
            if (query.cat3 != CatalogQuery.ALL && book.cat3 != query.cat3) return@filter false
            if (query.eraBucket != CatalogQuery.ALL && eraBucket(book.era) != query.eraBucket) return@filter false
            if (query.level != CatalogQuery.ALL && book.level != query.level) return@filter false
            if (keyword.isNotEmpty()) {
                val haystack = buildString {
                    append(book.title).append(' ')
                    append(book.author).append(' ')
                    append(book.era).append(' ')
                    append(book.country).append(' ')
                    append(book.category).append(' ')
                    append(book.cat2).append(' ')
                    append(book.cat3).append(' ')
                    append(book.summary).append(' ')
                    book.tags.forEach { append(it).append(' ') }
                }.lowercase()
                if (!haystack.contains(keyword)) return@filter false
            }
            true
        }

        return when (query.sort) {
            Sort.TITLE -> list.sortedBy { it.title }
            Sort.LEVEL -> list.sortedWith(compareBy({ LEVEL_RANK[it.level] ?: 9 }, { it.title }))
            Sort.TIME -> list.sortedByDescending { it.readingMinutes }
            Sort.CATEGORY -> list.sortedWith(
                compareBy<Book> { it.custom }.thenBy { it.cat1 }.thenBy { it.cat2 }.thenBy { it.cat3 }.thenBy { it.title }
            )
        }
    }

    /** 搜索建议：按书名 / 作者匹配，用于目录页快捷搜索 */
    fun suggestions(books: List<Book>, keyword: String, limit: Int = 8): List<Book> {
        val key = keyword.trim()
        if (key.isEmpty()) return emptyList()
        return books.asSequence()
            .filter { it.title.contains(key) || it.author.contains(key) }
            .sortedBy { if (it.title.startsWith(key)) 0 else 1 }
            .take(limit)
            .toList()
    }
}
