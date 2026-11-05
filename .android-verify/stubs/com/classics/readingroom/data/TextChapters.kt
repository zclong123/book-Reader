package com.classics.readingroom.data

data class Chapter(val title: String, val text: String)

/** 把一本电子书按“第X章 / 第X回 / CHAPTER”拆分章节 */
object TextChapters {
    private val CN_HEADING = Regex(
        "^[ \t\\u3000]*(第[0-9零〇一二三四五六七八九十百千万两]+[章回节卷篇部集](?:[ \t\\u3000:：、.．-]+[^\\n]{0,60})?|序章|序言|前言|楔子|引子|尾声|后记|附录[^\\n]{0,30}|#{1,3}[ \t]+[^\\n]{1,60})[ \t]*$"
    )
    private val EN_SPLIT = Regex(
        "\\n(?=(?:CHAPTER|Chapter|BOOK|Book|PART|Part|ACT|Act)\\s+[IVXLCDM0-9]+)"
    )

    fun split(rawText: String, fallbackTitle: String = "全文"): List<Chapter> {
        val text = rawText.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (text.isEmpty()) return emptyList()

        val matches = CN_HEADING.findAll(text).toList()
        if (matches.size >= 2) {
            val result = ArrayList<Chapter>()
            val preface = text.substring(0, matches.first().range.first).trim()
            if (preface.length > 60) result.add(Chapter("序 / 前言", preface))
            matches.forEachIndexed { i, match ->
                val start = match.range.last + 1
                val end = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
                val title = match.groupValues[1].replace(Regex("^#{1,3}[ \t]+"), "").trim()
                val body = text.substring(start, end).trim()
                if (body.isNotEmpty()) result.add(Chapter(title, body))
            }
            if (result.size >= 2) return result
        }

        if (!Regex("第[0-9零〇一二三四五六七八九十百千万两]+[章回节卷篇部集]").containsMatchIn(text)) {
            val parts = text.split(EN_SPLIT)
            if (parts.size >= 3) {
                val result = parts.mapIndexedNotNull { index, part ->
                    val lines = part.lines().filter { it.isNotBlank() }
                    val title = lines.firstOrNull()?.take(60)?.trim().orEmpty().ifEmpty { "第 ${index + 1} 部分" }
                    val body = lines.drop(1).joinToString("\n").trim()
                    if (body.isEmpty()) null else Chapter(title, body)
                }
                if (result.size >= 2) return result
            }
        }

        val paragraphs = text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
        val chunks = ArrayList<String>()
        val buffer = StringBuilder()
        for (paragraph in paragraphs) {
            if (buffer.length + paragraph.length > 3200) {
                if (buffer.isNotEmpty()) chunks.add(buffer.toString())
                buffer.setLength(0)
                buffer.append(paragraph)
            } else {
                if (buffer.isNotEmpty()) buffer.append("\n\n")
                buffer.append(paragraph)
            }
        }
        if (buffer.isNotEmpty()) chunks.add(buffer.toString())

        if (chunks.size <= 1) return listOf(Chapter(fallbackTitle, text))
        return chunks.mapIndexed { i, chunk -> Chapter("第 ${i + 1} 部分", chunk) }
    }

    fun paragraphsOf(text: String): List<String> =
        text.split(Regex("\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
}
