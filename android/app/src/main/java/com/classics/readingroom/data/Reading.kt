package com.classics.readingroom.data

/** 精读内容切分成段落块（带序号，便于朗读定位） */
data class ContentBlock(val text: String, val index: Int)

data class EssenceBlocks(
    val story: List<ContentBlock>,
    val insights: List<ContentBlock>,
    val quotes: List<ContentBlock>
)

data class LectureBlock(val type: String, val text: String, val index: Int)

object ContentBlocks {

    fun essence(dossier: Dossier): EssenceBlocks {
        var index = 0
        val story = dossier.story.map { ContentBlock(it, index++) }
        val insights = dossier.insights.map { ContentBlock(it, index++) }
        val quotes = dossier.quotes.map { ContentBlock("“${it.text}”${if (it.source.isNotBlank()) "——${it.source}" else ""}", index++) }
        return EssenceBlocks(story, insights, quotes)
    }

    fun essenceSegments(blocks: EssenceBlocks): List<Segment> =
        (blocks.story + blocks.insights + blocks.quotes).map { Segment(it.text, it.index) }

    fun lecture(dossier: Dossier): List<LectureBlock> {
        val blocks = ArrayList<LectureBlock>()
        var index = 0
        val lecture = dossier.lecture
        if (lecture.opening.isNotBlank()) blocks.add(LectureBlock("opening", lecture.opening, index++))
        lecture.sections.forEach { section ->
            blocks.add(LectureBlock("heading", section.title, index++))
            section.paragraphs.forEach { paragraph ->
                blocks.add(LectureBlock("paragraph", paragraph, index++))
            }
        }
        if (lecture.closing.isNotBlank()) blocks.add(LectureBlock("closing", lecture.closing, index++))
        return blocks
    }

    fun lectureSegments(blocks: List<LectureBlock>): List<Segment> = blocks.map { Segment(it.text, it.index) }

    fun chapterSegments(dossier: Dossier): List<Segment> {
        val segments = ArrayList<Segment>()
        var index = 0
        dossier.chapters.forEach { chapter ->
            val text = listOf(chapter.title, chapter.guide, chapter.excerpt).filter { it.isNotBlank() }.joinToString("。")
            if (text.isNotBlank()) segments.add(Segment(text, index++))
        }
        return segments
    }

    val LECTURE_CHAR_PER_MINUTE = 260

    fun estimateLectureMinutes(dossier: Dossier): Int {
        val lecture = dossier.lecture
        val chars = lecture.opening.length +
            lecture.sections.sumOf { section -> section.paragraphs.sumOf { it.length } } +
            lecture.closing.length
        if (chars == 0) return 0
        return maxOf(1, chars / LECTURE_CHAR_PER_MINUTE)
    }
}
