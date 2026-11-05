package com.classics.readingroom.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classics.readingroom.data.Book
import com.classics.readingroom.data.CatalogRepository
import com.classics.readingroom.data.ContentBlocks
import com.classics.readingroom.data.LocalLibrary
import com.classics.readingroom.data.TextTools
import com.classics.readingroom.data.UsageStats
import com.classics.readingroom.speech.SpeechPlayer
import com.classics.readingroom.ui.CardBox
import com.classics.readingroom.ui.ClassicsTheme
import com.classics.readingroom.ui.PillButton
import com.classics.readingroom.ui.SectionTitle
import com.classics.readingroom.ui.widgets.ReadAloudCard
import com.classics.readingroom.ui.widgets.SpeechPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ReaderChapter(val title: String, val paragraphs: List<String>)

@Composable
fun ReaderScreen(repo: CatalogRepository, book: Book, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val speech = remember { SpeechPlayer(context) }
    DisposableEffect(Unit) { onDispose { speech.release() } }

    var imported by remember { mutableStateOf<List<ReaderChapter>?>(null) }
    var importedName by remember { mutableStateOf("") }
    var builtinChapters by remember(book.id) { mutableStateOf<List<ReaderChapter>>(emptyList()) }
    var chapters by remember { mutableStateOf<List<ReaderChapter>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(LocalLibrary.readProgress(context, book.id)) }
    var fontSize by remember { mutableIntStateOf(LocalLibrary.fontSize(context)) }
    var night by remember { mutableStateOf(LocalLibrary.nightMode(context)) }
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var tocOpen by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(book.id) {
        @Suppress("NAME_SHADOWING") val dossier = runCatching { repo.getDossier(book) }.getOrNull()
        builtinChapters = dossier?.chapters.orEmpty().map { chapter ->
            ReaderChapter(
                chapter.title,
                listOf(chapter.guide, chapter.excerpt).filter { it.isNotBlank() }
                    .flatMap { TextTools.paragraphs(it) }
            )
        }
        if (imported == null) chapters = builtinChapters
        currentIndex = currentIndex.coerceIn(0, maxOf(0, chapters.lastIndex))
    }

    val meta = remember(book.id) { repo.textMeta(book.id) }
    val listState = rememberLazyListState()

    val pickFile: androidx.activity.result.ActivityResultLauncher<Array<String>> =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            error = ""
            message = ""
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                }
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "本地原书.txt"
                if (text.isBlank()) {
                    error = "没有从文件中读到正文，请确认是纯文本或 Markdown 文件。"
                    return@launch
                }
                val parsed = withContext(Dispatchers.Default) { TextTools.splitChapters(text, book.title) }
                if (parsed.isEmpty()) {
                    error = "文件中没有可读的正文。"
                    return@launch
                }
                imported = parsed.map { ReaderChapter(it.title, TextTools.paragraphs(it.text)) }
                chapters = imported!!
                importedName = name
                currentIndex = 0
                message = "已导入《$name》，自动拆分 ${parsed.size} 章"
                LocalLibrary.saveImportedText(context, book.id, name, text)
                LocalLibrary.saveReadProgress(context, book.id, 0)
            } catch (e: Exception) {
                error = "读取失败：${e.message ?: "文件无法解析"}"
            }
        }
    }

    fun restoreImported() {
        val saved = LocalLibrary.importedText(context, book.id) ?: return
        scope.launch {
            val parsed = withContext(Dispatchers.Default) { TextTools.splitChapters(saved.second, book.title) }
            if (parsed.isNotEmpty()) {
                imported = parsed.map { ReaderChapter(it.title, TextTools.paragraphs(it.text)) }
                chapters = imported!!
                importedName = saved.first
                currentIndex = LocalLibrary.readProgress(context, book.id).coerceIn(0, maxOf(0, chapters.lastIndex))
                message = "已恢复上次导入的《${saved.first}》"
            }
        }
    }

    LaunchedEffect(book.id) { restoreImported() }

    fun openBuiltinText() {
        val textMeta = meta ?: return
        if (loading) return
        scope.launch {
            loading = true
            error = ""
            message = ""
            try {
                val text = repo.readTextFile(textMeta.file)
                val parsed = withContext(Dispatchers.Default) { TextTools.splitChapters(text, book.title) }
                imported = parsed.map { ReaderChapter(it.title, TextTools.paragraphs(it.text)) }
                chapters = imported!!
                importedName = "《${book.title}》（${textMeta.source}）"
                currentIndex = 0
                message = "已打开原书全文，自动拆分 ${parsed.size} 章"
                speech.stop()
            } catch (e: Exception) {
                error = "读取内置原书失败：${e.message ?: "未知错误"}"
            } finally {
                loading = false
            }
        }
    }

    fun goChapter(index: Int) {
        if (index < 0 || index >= chapters.size) return
        speech.stop()
        currentIndex = index
        LocalLibrary.saveReadProgress(context, book.id, index)
        UsageStats.recordRead(context, book.id, index)
        tocOpen = false
        scope.launch { listState.animateScrollToItem(0) }
    }

    fun playChapter() {
        val chapter = chapters.getOrNull(currentIndex) ?: return
        val segments = chapter.paragraphs.flatMap { TextTools.speechChunks(it) }
        if (segments.isEmpty()) return
        speech.play(segments, segments.indices.toList().toIntArray(), "正在朗读 · ${chapter.title}", 0)
    }

    val chapter = chapters.getOrNull(currentIndex.coerceIn(0, maxOf(0, chapters.lastIndex)))

    ClassicsTheme(nightOverride = night) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item("head") {
                    Column {
                        Text(
                            if (imported != null) "原书全文" else "章节导读",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(book.title, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "${book.author} · ${book.era} · " + (if (imported != null) importedName else "内置章节导读"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                item("tools") {
                    CardBox {
                        Text(
                            if (meta != null) {
                                "已收录 ${meta.source} 的公版全文（${meta.sourceTitle}），可直接打开按章节阅读。"
                            } else {
                                "本书暂未收录全文，可导入你自己合法获得的 .txt / .md 电子书，阅读进度会保存在本机。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (meta != null) {
                                PillButton(if (loading) "正在打开…" else "阅读原书全文", onClick = { openBuiltinText() }, primary = true, enabled = !loading)
                            }
                            PillButton("导入本地原书", onClick = { pickFile.launch(arrayOf("text/plain", "text/markdown", "*/*")) })
                            if (imported != null) {
                                PillButton("恢复导读", onClick = {
                                    imported = null
                                    chapters = builtinChapters
                                    importedName = ""
                                    currentIndex = 0
                                    LocalLibrary.clearImportedText(context, book.id)
                                    message = "已切换回内置章节导读"
                                })
                            }
                        }
                        if (meta != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "来源：${meta.source} · 下载于 ${meta.downloadedAt}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (message.isNotEmpty()) {
                    item("message") {
                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                if (error.isNotEmpty()) {
                    item("error") { Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }

                if (chapters.isEmpty()) {
                    item("empty") {
                        com.classics.readingroom.ui.EmptyState(
                            "还没有可阅读的章节",
                            "导入一本 .txt 或 .md 电子书，系统会自动按“第X章 / 第X回”拆分。"
                        ) {
                            PillButton("选择本地文件", onClick = { pickFile.launch(arrayOf("*/*")) }, primary = true)
                        }
                    }
                } else {
                    item("toolbar") {
                        CardBox(padding = 10.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("字号", style = MaterialTheme.typography.labelMedium)
                                TextButton(onClick = {
                                    fontSize = (fontSize - 1).coerceAtLeast(14)
                                    LocalLibrary.saveFontSize(context, fontSize)
                                }) { Text("A−") }
                                Text("${fontSize}px", style = MaterialTheme.typography.labelMedium)
                                TextButton(onClick = {
                                    fontSize = (fontSize + 1).coerceAtMost(30)
                                    LocalLibrary.saveFontSize(context, fontSize)
                                }) { Text("A+") }
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = {
                                    night = !night
                                    LocalLibrary.saveNightMode(context, night)
                                }) { Text(if (night) "纸质" else "夜间") }
                                TextButton(onClick = { tocOpen = true }) { Text("目录 ${currentIndex + 1}/${chapters.size}") }
                            }
                        }
                    }

                    item("chapter-head") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (imported != null) importedName else "章节导读与节选", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(chapter?.title ?: "", style = MaterialTheme.typography.titleLarge)
                            }
                            PillButton("▶ 朗读本章", onClick = { playChapter() }, primary = true)
                        }
                    }

                    item("speech") { SpeechPanel(speech) }

                    itemsIndexed(chapter?.paragraphs.orEmpty()) { index, paragraph ->
                        val active = speech.speaking && speech.activeIndex >= 0
                        Text(
                            paragraph,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize.sp, lineHeight = (fontSize * 1.75).sp, fontFamily = FontFamily.Serif),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable {
                                    val segments = chapter!!.paragraphs.flatMap { TextTools.speechChunks(it) }
                                    val before = chapter!!.paragraphs.take(index).sumOf { TextTools.speechChunks(it).size }
                                    speech.play(segments, segments.indices.toList().toIntArray(), "正在朗读 · ${chapter!!.title}", before)
                                }
                                .padding(vertical = 4.dp)
                        )
                    }

                    item("pager") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            PillButton("← 上一章", onClick = { goChapter(currentIndex - 1) }, enabled = currentIndex > 0)
                            PillButton("下一章 →", onClick = { goChapter(currentIndex + 1) }, enabled = currentIndex < chapters.lastIndex)
                        }
                    }
                }

                item("record") { ReadAloudCard() }
                item("bottom") { Spacer(Modifier.height(16.dp)) }
            }
        }

        if (tocOpen) {
            AlertDialog(
                onDismissRequest = { tocOpen = false },
                title = { Text("目录 · ${chapters.size} 章") },
                text = {
                    LazyColumn(Modifier.height(420.dp)) {
                        itemsIndexed(chapters) { index, item ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (index == currentIndex) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable { goChapter(index) }
                                    .padding(vertical = 8.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("%02d".format(index + 1), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.width(28.dp))
                                Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { tocOpen = false }) { Text("关闭") } }
            )
        }
    }
}
