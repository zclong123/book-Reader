package com.classics.readingroom.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classics.readingroom.MainViewModel
import com.classics.readingroom.data.Chapter
import com.classics.readingroom.data.TextChapters
import com.classics.readingroom.ui.BookCover
import com.classics.readingroom.ui.EmptyState
import com.classics.readingroom.ui.Pill
import com.classics.readingroom.ui.SectionTitle
import com.classics.readingroom.ui.components.AppTopBar
import com.classics.readingroom.ui.components.RecorderCard
import com.classics.readingroom.ui.components.SpeechBar
import com.classics.readingroom.ui.components.SpeakingParagraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReadScreen(
    vm: MainViewModel,
    bookId: String,
    onBack: () -> Unit,
    onToggleRecord: () -> Unit
) {
    val book = remember(bookId, vm.customBooks) { vm.categories.firstOrNull { it.id == bookId } }
    if (book == null) {
        EmptyState("没有找到这部作品", action = { OutlinedButton(onClick = onBack) { Text("返回") } })
        return
    }

    val meta = remember(bookId) { vm.textMeta(bookId) }
    val scope = rememberCoroutineScope()
    var chapters by remember(bookId) { mutableStateOf<List<Chapter>>(emptyList()) }
    var current by remember(bookId) { mutableStateOf(0) }
    var sourceLabel by remember(bookId) { mutableStateOf("章节导读") }
    var loading by remember(bookId) { mutableStateOf(false) }
    var error by remember(bookId) { mutableStateOf("") }
    var showToc by remember(bookId) { mutableStateOf(false) }
    var fontSize by remember(bookId) { mutableStateOf(vm.library.readerFontSize) }
    var night by remember(bookId) { mutableStateOf(vm.library.readerNightMode) }
    val listState = rememberLazyListState()

    // 先载入目录：有公版全文用全文，否则用精读档案里的章节导读
    LaunchedEffect(bookId) {
        val saved = vm.progress(bookId)
        if (meta != null) {
            loading = true
            try {
                val text = withContext(Dispatchers.IO) { vm.library.text(meta) }
                val parsed = withContext(Dispatchers.Default) { TextChapters.split(text, book.title) }
                chapters = parsed
                sourceLabel = "公版原书 · ${meta.source}"
                current = if (parsed.isNotEmpty()) saved.first.coerceIn(0, parsed.lastIndex) else 0
            } catch (e: Exception) {
                error = "原书读取失败：${e.message ?: "未知错误"}"
                chapters = withContext(Dispatchers.IO) { guideChapters(vm, bookId) }
            } finally {
                loading = false
            }
        } else {
            loading = true
            chapters = withContext(Dispatchers.IO) { guideChapters(vm, bookId) }
            loading = false
            if (chapters.isNotEmpty()) current = saved.first.coerceIn(0, chapters.lastIndex)
        }
    }

    val chapter = chapters.getOrNull(current)
    val paragraphs = remember(chapter) { chapter?.let { TextChapters.paragraphsOf(it.text) }.orEmpty() }

    LaunchedEffect(current, chapters.size) {
        if (chapters.isNotEmpty()) vm.saveProgress(bookId, current, chapters.size)
        vm.tts.stop()
        listState.scrollToItem(0)
    }

    val fg = if (night) androidx.compose.ui.graphics.Color(0xFFEDE4D6) else androidx.compose.ui.graphics.Color(0xFF2B2620)
    val paperColor = if (night) androidx.compose.ui.graphics.Color(0xFF1C1A18) else androidx.compose.ui.graphics.Color(0xFFFFFDF7)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp)
    ) {
        item("top") {
            AppTopBar(
                title = book.title,
                subtitle = "${book.author} · 第 ${if (chapters.isEmpty()) 0 else current + 1}/${chapters.size} 章",
                onBack = onBack
            ) {
                Text(
                    "目录",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { showToc = !showToc }
                        .padding(horizontal = 14.dp)
                )
            }
        }

        if (showToc) {
            item("toc-head") { SectionTitle("章节目录", "${chapters.size} 章", Modifier.padding(horizontal = 16.dp)) }
            itemsIndexed(chapters) { index, item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            current = index
                            showToc = false
                        }
                        .background(
                            if (index == current) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            else androidx.compose.ui.graphics.Color.Transparent
                        )
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        String.format("%02d", index + 1),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }
            item("toc-gap") { Spacer(Modifier.height(8.dp)) }
        }

        item("source") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BookCover(book, width = 40.dp, height = 58.dp)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill(if (meta != null) "馆藏原书" else "章节导读")
                        Text(sourceLabel, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 6.dp))
                    }
                    Text(
                        if (meta != null) "${meta.sourceTitle} · 约 ${meta.chars / 1000} 千字符" else "按章节阅读关键段落与导读",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item("tools") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolChip("A−") {
                    fontSize = (fontSize - 1).coerceAtLeast(14)
                    vm.library.readerFontSize = fontSize
                }
                Text("$fontSize", style = MaterialTheme.typography.labelMedium)
                ToolChip("A+") {
                    fontSize = (fontSize + 1).coerceAtMost(30)
                    vm.library.readerFontSize = fontSize
                }
                ToolChip(if (night) "日间" else "夜间") {
                    night = !night
                    vm.library.readerNightMode = night
                }
                Spacer(Modifier.weight(1f))
                ToolChip("▶ 朗读本章") {
                    if (chapter != null) vm.tts.play(paragraphs, label = "正在朗读 · ${chapter.title}")
                }
            }
        }

        item("speech") { Box(Modifier.padding(horizontal = 16.dp)) { SpeechBar(vm) } }

        if (error.isNotBlank()) item("error") {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(16.dp))
        }

        if (loading) {
            item("loading") {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (chapters.isEmpty()) {
            item("empty") {
                EmptyState("还没有可阅读的章节", "这本书暂无收录的全文，可以先读精读精华，或返回目录浏览其它作品")
            }
        } else if (chapter != null) {
            item("chapter-head") {
                Column(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(paperColor)
                        .padding(14.dp)
                ) {
                    Text(
                        chapter.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (night) fg else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${paragraphs.size} 段 · 约 ${chapter.text.length} 字",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            itemsIndexed(paragraphs) { index, paragraph ->
                Box(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(paperColor)
                        .clickable {
                            vm.tts.play(paragraphs, startAt = index, label = "正在朗读 · ${chapter.title}")
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        paragraph,
                        fontFamily = FontFamily.Serif,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.8f).sp,
                        color = if (vm.tts.activeIndex == index && vm.tts.speaking) MaterialTheme.colorScheme.primary else fg,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
            item("pager") {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { if (current > 0) current -= 1 },
                        enabled = current > 0,
                        modifier = Modifier.weight(1f)
                    ) { Text("← 上一章") }
                    OutlinedButton(
                        onClick = { if (current < chapters.lastIndex) current += 1 },
                        enabled = current < chapters.lastIndex,
                        modifier = Modifier.weight(1f)
                    ) { Text("下一章 →") }
                }
            }
        }

        item("recorder") {
            Box(Modifier.padding(horizontal = 16.dp)) { RecorderCard(vm.recorder, onToggleRecord) }
        }

        item("note") {
            Card(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
            ) {
                Text(
                    if (meta != null) {
                        "本页全文取自 ${meta.source}（${meta.sourceTitle}），为原文语言的公版文本，不含中文译本。阅读进度会自动记录在本机。"
                    } else {
                        "本书尚未附全文。这里展示的是关键章节导读与节选；阅读器会自动按“第X章 / 第X回”拆分收录的原书。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}

@Composable
private fun ToolChip(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

private suspend fun guideChapters(vm: MainViewModel, bookId: String): List<Chapter> {
    val dossier = vm.loadDetail(bookId) ?: return emptyList()
    return dossier.chapters.map {
        Chapter(it.title, listOf(it.guide, it.excerpt).filter { text -> text.isNotBlank() }.joinToString("\n\n"))
    }
}
