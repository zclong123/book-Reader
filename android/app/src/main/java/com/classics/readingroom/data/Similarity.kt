package com.classics.readingroom.data

/** 正文拆分：章节识别、朗读分段 */
object TextTools {

    private val CHINESE_HEADING = Regex(
        "^[ \\t]*(第[0-9零〇一二三四五六七八九十百千万两]+[章回节卷篇部集](?:[ \\t\\u3000:：、.．-]+[^\\n]{0,60})?|序章|序言|前言|楔子|引子|尾声|后记|附录[^\\n]{0,30}|#{1,3}[ \\t]+[^\\n]{1,60})[ \\t]*$",
        RegexOption.MULTILINE
    )
    private val ENGLISH_SPLIT = Regex("\\n(?=(?:CHAPTER|Chapter|BOOK|Book|PART|Part|ACT|Act)\\s+[IVXLCDM0-9]+)")
    private val ENGLISH_CHAPTER = Regex("第[0-9零〇一二三四五六七八九十百千万两]+[章回节卷篇部集]")

    data class Chapter(val title: String, val text: String)

    fun splitChapters(raw: String, fallbackTitle: String = "全文"): List<Chapter> {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (text.isEmpty()) return emptyList()

        val matches = CHINESE_HEADING.findAll(text).toList()
        if (matches.size >= 2) {
            val result = ArrayList<Chapter>()
            val preface = text.substring(0, matches.first().range.first).trim()
            if (preface.length > 60) result.add(Chapter("序 / 前言", preface))
            matches.forEachIndexed { i, match ->
                val start = match.range.last + 1
                val end = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
                val title = match.groupValues[1].replace(Regex("^#{1,3}[ \\t]+"), "").trim()
                val body = text.substring(start, end).trim()
                if (body.isNotEmpty()) result.add(Chapter(title, body))
            }
            if (result.size >= 2) return result
        }

        if (!ENGLISH_CHAPTER.containsMatchIn(text)) {
            val parts = text.split(ENGLISH_SPLIT)
            if (parts.size >= 3) {
                val result = parts.mapIndexedNotNull { index, part ->
                    val lines = part.split('\n').filter { it.isNotBlank() }
                    val title = lines.firstOrNull()?.trim()?.take(60).takeUnless { it.isNullOrBlank() } ?: "第 ${index + 1} 部分"
                    val body = lines.drop(1).joinToString("\n").trim()
                    if (body.isBlank()) null else Chapter(title, body)
                }
                if (result.size >= 2) return result
            }
        }

        val paragraphs = text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
        val chunks = ArrayList<String>()
        val buffer = StringBuilder()
        for (paragraph in paragraphs) {
            if ((buffer.length + paragraph.length) > 3200 && buffer.isNotEmpty()) {
                chunks.add(buffer.toString())
                buffer.setLength(0)
            }
            if (buffer.isNotEmpty()) buffer.append("\n\n")
            buffer.append(paragraph)
        }
        if (buffer.isNotEmpty()) chunks.add(buffer.toString())
        if (chunks.size <= 1) return listOf(Chapter(fallbackTitle, text))
        return chunks.mapIndexed { index, chunk -> Chapter("第 ${index + 1} 部分", chunk) }
    }

    fun paragraphs(text: String): List<String> =
        text.split(Regex("\\n+")).map { it.trim() }.filter { it.isNotEmpty() }

    /** 长文按标点切成朗读片段，避免系统语音引擎单条过长 */
    fun speechChunks(text: String, limit: Int = 160): List<String> {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (normalized.isEmpty()) return emptyList()
        if (normalized.length <= limit) return listOf(normalized)
        val result = ArrayList<String>()
        val current = StringBuilder()
        val punctuation = "。！？；…”’!?"
        for (ch in normalized) {
            current.append(ch)
            val cut = current.length >= limit && ch in punctuation
            if (cut || current.length >= limit * 2) {
                result.add(current.toString().trim())
                current.setLength(0)
            }
        }
        if (current.isNotBlank()) result.add(current.toString().trim())
        return result
    }
}
