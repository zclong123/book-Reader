package com.classics.readingroom.ui.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.classics.readingroom.data.Dossier
import com.classics.readingroom.data.Quote

/**
 * 编辑精读详情：故事脉络、阅读所获、章句与演讲书稿。
 * 每行一段 / 每条，保存时自动整理。
 */
@Composable
fun DossierEditorDialog(
    dossier: Dossier,
    onDismiss: () -> Unit,
    onSave: (Dossier) -> Unit
) {
    var subtitle by remember { mutableStateOf(dossier.subtitle) }
    var authorIntro by remember { mutableStateOf(dossier.authorIntro) }
    var summary by remember { mutableStateOf(dossier.summary) }
    var story by remember { mutableStateOf(dossier.story.joinToString("\n")) }
    var insights by remember { mutableStateOf(dossier.insights.joinToString("\n")) }
    var quotes by remember { mutableStateOf(dossier.quotes.joinToString("\n") { it.text }) }
    var lectureTitle by remember { mutableStateOf(dossier.lecture.title) }
    var opening by remember { mutableStateOf(dossier.lecture.opening) }
    var sections by remember {
        mutableStateOf(
            dossier.lecture.sections.joinToString("\n\n#\n\n") { section ->
                section.title + "\n" + section.paragraphs.joinToString("\n")
            }
        )
    }
    var closing by remember { mutableStateOf(dossier.lecture.closing) }

    fun lines(text: String) = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑《${dossier.title}》的精读详情") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Field("副标题", subtitle) { subtitle = it }
                Field("作者小传", authorIntro, 3) { authorIntro = it }
                Field("一句话简介", summary) { summary = it }
                Field("故事情节（每行一段）", story, 6) { story = it }
                Field("阅读所获（每行一条）", insights, 6) { insights = it }
                Field("必背章句（每行一句）", quotes, 4) { quotes = it }
                Field("讲稿标题", lectureTitle) { lectureTitle = it }
                Field("开场白", opening, 4) { opening = it }
                Field(
                    "讲稿段落（小节标题占一行，用 # 单独一行分隔小节）",
                    sections,
                    10
                ) { sections = it }
                Field("收束", closing, 3) { closing = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsedSections = sections
                    .split(Regex("\\n\\s*#\\s*\\n"))
                    .map { block -> lines(block) }
                    .filter { it.isNotEmpty() }
                    .map { blockLines ->
                        com.classics.readingroom.data.LectureSection(
                            title = blockLines.first(),
                            paragraphs = blockLines.drop(1)
                        )
                    }
                    .filter { it.paragraphs.isNotEmpty() }
                onSave(
                    dossier.copy(
                        subtitle = subtitle.trim(),
                        authorIntro = authorIntro.trim(),
                        summary = summary.trim(),
                        story = lines(story),
                        insights = lines(insights),
                        quotes = lines(quotes).map { Quote(it, "书中", "") },
                        lecture = dossier.lecture.copy(
                            title = lectureTitle.trim(),
                            opening = opening.trim(),
                            sections = parsedSections,
                            closing = closing.trim()
                        ),
                        isFallback = false
                    )
                )
            }) { Text("保存修改") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun Field(label: String, value: String, lines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (lines == 1) 56.dp else 90.dp)
            .padding(vertical = 4.dp),
        minLines = lines,
        textStyle = MaterialTheme.typography.bodySmall
    )
}


