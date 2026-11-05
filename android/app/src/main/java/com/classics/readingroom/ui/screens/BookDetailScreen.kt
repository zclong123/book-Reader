package com.classics.readingroom.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.classics.readingroom.data.AiService
import com.classics.readingroom.data.AiSettings
import com.classics.readingroom.data.Book
import com.classics.readingroom.data.CatalogRepository
import com.classics.readingroom.data.ContentBlocks
import com.classics.readingroom.data.Dossier
import com.classics.readingroom.data.LocalLibrary
import com.classics.readingroom.data.UsageStats
import com.classics.readingroom.speech.SpeechPlayer
import com.classics.readingroom.ui.BookCover
import com.classics.readingroom.ui.CardBox
import com.classics.readingroom.ui.HorizontalDivider
import com.classics.readingroom.ui.InfoRow
import com.classics.readingroom.ui.PillButton
import com.classics.readingroom.ui.SectionTitle
import com.classics.readingroom.ui.Tag
import com.classics.readingroom.ui.widgets.DossierEditorDialog
import com.classics.readingroom.ui.widgets.ReadAloudCard
import com.classics.readingroom.ui.widgets.SpeechPanel
import kotlinx.coroutines.launch

// 详情页各部分共用同一个朗读器与回调
private interface DetailSectionHost {
    val speech: SpeechPlayer
    fun playEssence(index: Int)
    fun playLecture(index: Int)
    fun playChapters()
    fun openReader()
}

/** 详情页行动作读到的可变状态 */
private class DetailAction {
    var doc: Dossier? = null
}

typealias DetailScope = androidx.compose.foundation.lazy.LazyListScope

/** 当前标签对应的正文：精华 / 讲稿 / 章节导读 */
@Composable
private fun DetailTabContent(host: DetailSectionHost, tab: Int, doc: Dossier) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (tab) {
            0 -> EssenceContent(doc, host)
            1 -> LectureContent(doc, host)
            else -> ChapterContent(doc, host)
        }
    }
}

@Composable
fun BookDetailScreen(
    repo: CatalogRepository,
    book: Book,
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenReader: (Book) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val speech = remember { SpeechPlayer(context) }
    DisposableEffect(Unit) { onDispose { speech.release() } }

    var revision by remember { mutableIntStateOf(0) }
    var dossier: Dossier? by remember { mutableStateOf<Dossier?>(null) }
    var similar by remember { mutableStateOf<List<Book>>(emptyList()) }
    var favorite by remember(book.id) { mutableStateOf(LocalLibrary.isFavorite(context, book.id)) }
    var source by remember { mutableStateOf("none") }
    var tab by remember(book.id) { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var showEdit by remember { mutableStateOf(false) }
    var showRevise by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var reviseInstruction by remember { mutableStateOf("") }
    var reviseScope by remember { mutableStateOf("all") }
    var showAuthorWorks by remember { mutableStateOf(false) }

    var settingsRevision by remember { mutableIntStateOf(0) }
    // 用可变的 Action 持有最新状态，避免回调读到旧快照
    val Action = remember { DetailAction() }
    LaunchedEffect(book.id, revision) {
        dossier = repo.getDossier(book)
        Action.doc = dossier
        similar = repo.similarBooks(book, 6)
        source = repo.dossierSource(book)
    }

    val authorWorks = remember(book.id) { repo.authorWorks(book) }

    fun playEssence(start: Int = 0) {
        val doc = Action.doc ?: return
        val blocks = ContentBlocks.essence(doc)
        val segments = ContentBlocks.essenceSegments(blocks)
        if (segments.isEmpty()) {
            error = "暂无可朗读的内容"
            return
        }
        val target = segments.indexOfFirst { it.index == start }.coerceAtLeast(0)
        speech.play(
            segments.map { if (it.text.length > 400) it.text.take(400) + "……" else it.text },
            segments.map { it.index }.toIntArray(),
            "正在朗读 · 阅读精华",
            target
        )
    }

    fun playLecture(start: Int = 0) {
        val doc = Action.doc ?: return
        val segments = ContentBlocks.lectureSegments(ContentBlocks.lecture(doc))
        if (segments.isEmpty()) return
        val target = segments.indexOfFirst { it.index == start }.coerceAtLeast(0)
        speech.play(segments.map { it.text }, segments.map { it.index }.toIntArray(), "正在朗读 · 每日演讲书稿", target)
    }

    fun playChapters() {
        val doc = Action.doc ?: return
        val segments = ContentBlocks.chapterSegments(doc)
        if (segments.isEmpty()) return
        speech.play(segments.map { it.text }, segments.map { it.index }.toIntArray(), "正在朗读 · 章节导读", 0)
    }

    // 详情页各部分共用的朗读与跳转回调
    val sectionHost: DetailSectionHost = remember(speech) {
        object : DetailSectionHost {
            override val speech: SpeechPlayer = speech
            override fun playEssence(index: Int) = playEssence(index)
            override fun playLecture(index: Int) = playLecture(index)
            override fun playChapters() = playChapters()
            override fun openReader() = onOpenReader(book)
        }
    }

    fun generateDetail() {
        if (busy.isNotEmpty()) return
        val ai = AiSettings.fromJson(LocalLibrary.aiSettings(context))
        if (ai.baseURL.isBlank() || ai.model.isBlank()) {
            showSettings = true
            error = "请先填写模型服务地址与模型名称"
            return
        }
        busy = "生成精读详情"
        error = ""
        notice = ""
        scope.launch {
            try {
                val (system, user) = AiService.detailPrompt(book)
                val raw = AiService.extractJson(AiService.chat(ai, system, user, maxTokens = 8000))
                val normalized = AiService.normalize(raw, book)
                repo.saveDossier(normalized)
                revision++
                notice = "精读详情已生成并保存在本机。"
            } catch (e: Exception) {
                error = e.message ?: "生成失败，请稍后重试"
            } finally {
                busy = ""
            }
        }
    }

    fun reviseWithAI() {
        val doc = Action.doc ?: return
        if (busy.isNotEmpty()) return
        val ai = AiSettings.fromJson(LocalLibrary.aiSettings(context))
        if (ai.baseURL.isBlank() || ai.model.isBlank()) {
            showSettings = true
            error = "请先填写模型服务地址与模型名称"
            return
        }
        busy = "修改精读详情"
        error = ""
        scope.launch {
            try {
                val (system, user) = AiService.revisePrompt(book, doc, reviseInstruction, reviseScope)
                val raw = AiService.extractJson(AiService.chat(ai, system, user, maxTokens = 8000))
                val merged = AiService.merge(doc, raw, reviseScope)
                repo.saveDossier(merged)
                revision++
                showRevise = false
                reviseInstruction = ""
                notice = "已按要求完成修改。"
            } catch (e: Exception) {
                error = e.message ?: "修改失败，请稍后重试"
            } finally {
                busy = ""
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item("hero") {
            Row(verticalAlignment = Alignment.Top) {
                BookCover(book, size = "lg")
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("${book.country} · ${book.era} · ${book.category}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(book.title, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(book.author, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        dossier?.subtitle?.ifBlank { book.summary } ?: book.summary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton(
                            if (speech.speaking) "重新朗读精华" else "▶ 朗读精华",
                            onClick = { playEssence() },
                            primary = true
                        )
                        PillButton(if (favorite) "★ 已收藏" else "☆ 收藏", onClick = {
                            val added = LocalLibrary.toggleFavorite(context, book.id)
                            UsageStats.recordFavorite(context, book.id, added)
                            favorite = added
                        })
                    }
                    Spacer(Modifier.height(8.dp))
                    PillButton("阅读原书 / 导入本地书", onClick = { onOpenReader(book) })
                }
            }
        }

        item("actions") {
            CardBox(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when (source) {
                            "local" -> "本机版本（已生成/编辑）"
                            "builtin" -> "馆内精读档案"
                            else -> "尚无精读详情"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (source == "none") {
                        PillButton(if (busy.isNotEmpty()) busy + "…" else "生成精读详情", onClick = { generateDetail() }, primary = true, enabled = busy.isEmpty())
                    } else {
                        PillButton("编辑精读详情", onClick = { showEdit = true }, enabled = busy.isEmpty())
                        PillButton("模型修改", onClick = { showRevise = !showRevise }, enabled = busy.isEmpty())
                    }
                    if (source == "local") {
                        PillButton("恢复默认", onClick = {
                            repo.removeDossier(book.id)
                            revision++
                            notice = "已恢复默认内容。"
                        })
                    }
                }
                if (busy.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("正在$busy，请保持页面打开。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (notice.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        if (showRevise && source != "none") {
            item("revise") {
                CardBox(padding = 12.dp) {
                    Text("按你的要求修改这份精读", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("all" to "全部", "essence" to "阅读精华", "lecture" to "演讲书稿").forEach { (value, label) ->
                            val selected = reviseScope == value
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { reviseScope = value }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reviseInstruction,
                        onValueChange = { reviseInstruction = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例如：把演讲书稿改得更口语化；补充关于女性角色的分析") },
                        minLines = 2
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("提交修改", onClick = { reviseWithAI() }, primary = true, enabled = busy.isEmpty())
                        TextButton(onClick = { showSettings = true }) { Text("模型设置") }
                    }
                }
            }
        }

        item("speech") {
            SpeechPanel(speech)
        }

        item("tabs") {
            val tabs = listOf(
                "阅读精华" to "story",
                "每日演讲书稿" to "lecture",
                "章节导读" to "chapters"
            )
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { index, (label, _) ->
                    Tab(
                        selected = tab == index,
                        onClick = {
                            if (tab != index) {
                                speech.stop()
                                tab = index
                            }
                        },
                        text = { Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
                    )
                }
            }
        }

        val doc = dossier
        if (doc == null) {
            item("loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("正在加载精读详情…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (doc != null) {
            item { DetailTabContent(sectionHost, tab, doc) }
        }

        item("read-aloud") { ReadAloudCard() }

        item("author") {
            CardBox {
                SectionTitle("作者介绍")
                Text(
                    doc?.authorIntro?.ifBlank { null } ?: "作者简介正在整理中。",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (authorWorks.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("馆藏作品集（${authorWorks.size} 部）", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (authorWorks.size > 3) {
                            TextButton(onClick = { showAuthorWorks = !showAuthorWorks }) {
                                Text(if (showAuthorWorks) "收起" else "查看全部")
                            }
                        }
                    }
                    val shown = if (showAuthorWorks) authorWorks else authorWorks.take(3)
                    shown.forEach { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenBook(item.id) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("《${item.title}》", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text(item.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        item("facts") {
            CardBox {
                SectionTitle("作品档案")
                InfoRow("作者", book.author)
                InfoRow("年代", book.era)
                InfoRow("国别", book.country)
                InfoRow("类别", "${book.cat1} / ${book.cat2} / ${book.cat3Label}")
                InfoRow("推荐", book.level)
                InfoRow("篇幅", "约 ${book.readingMinutes} 分钟读完")
                val minutes = doc?.let { ContentBlocks.estimateLectureMinutes(it) } ?: 0
                if (minutes > 0) InfoRow("讲稿", "约 $minutes 分钟")
                if (book.custom) InfoRow("来源", "我的书架 · 自定义")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    book.tags.take(5).forEach { Tag(it, highlighted = true) }
                }
            }
        }

        item("similar") {
            CardBox {
                SectionTitle("相似的书", "读完还能读什么")
                if (similar.isEmpty()) {
                    Text("暂未找到相似的书籍。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                        items(similar, key = { it.id }) { item ->
                            Column(
                                Modifier
                                    .width(96.dp)
                                    .clickable { onOpenBook(item.id) }
                            ) {
                                BookCover(item, size = "md")
                                Spacer(Modifier.height(6.dp))
                                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                Text(item.author, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        item("tips") {
            CardBox {
                SectionTitle("朗读提示")
                Text(
                    "语音由设备内置的语音引擎合成，不产生流量。若没有中文音色，可在系统设置 → 语音与语言中安装中文语音包；讲稿与精华都可以边听边看。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item("bottom") { Spacer(Modifier.height(24.dp)) }
    }

    val editable = Action.doc
    if (showEdit && editable != null) {
        DossierEditorDialog(
            dossier = editable,
            onDismiss = { showEdit = false },
            onSave = { edited ->
                repo.saveDossier(edited)
                revision++
                showEdit = false
                notice = "修改已保存到本机。"
            }
        )
    }

    if (showSettings) {
        AiSettingsDialog(onDismiss = { showSettings = false }, onSaved = { settingsRevision++ })
    }


}

@Composable
private fun EssenceContent(doc: Dossier, host: DetailSectionHost) {
    val speech = host.speech
    val onPlay: (Int) -> Unit = { host.playEssence(it) }
    val blocks = ContentBlocks.essence(doc)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("阅读精华", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        PillButton("▶ 朗读本页", onClick = { onPlay(0) }, primary = true)
    }
    if (doc.isFallback) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "这本作品的精读档案还在整理中，下面是目录速览。你也可以先按章节读原书，或按需生成精读详情。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    SectionTitle("壹 · 故事情节", "先抓住骨架，细节才不会散")
    if (blocks.story.isEmpty()) {
        Text("情节整理中，可先阅读总目录简介。", style = MaterialTheme.typography.bodySmall)
    }
    blocks.story.forEach { block ->
        ParagraphCard(
            text = block.text,
            active = speech.speaking && speech.activeIndex == block.index,
            onPlay = { onPlay(block.index) }
        )
    }
    SectionTitle("贰 · 阅读所获", "读完能带走什么")
    blocks.insights.forEach { block ->
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { onPlay(block.index) }
                .padding(12.dp)
        ) {
            Text(
                "%02d".format(block.index - blocks.story.size + 1),
                style = MaterialTheme.typography.titleMedium,
                color = if (speech.speaking && speech.activeIndex == block.index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.width(32.dp)
            )
            Text(block.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
    SectionTitle("叁 · 必背精华章句", "值得抄写、背诵的句子")
    if (blocks.quotes.isEmpty()) {
        Text("本作品的金句仍在整理中。", style = MaterialTheme.typography.bodySmall)
    }
    doc.quotes.forEachIndexed { index, quote ->
        val blockIndex = blocks.story.size + blocks.insights.size + index
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { onPlay(blockIndex) }
                .padding(14.dp)
        ) {
            Text("“${quote.text}”", style = MaterialTheme.typography.bodyLarge, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
            Spacer(Modifier.height(6.dp))
            Row {
                Text("—— ${quote.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (quote.note.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(quote.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ParagraphCard(text: String, active: Boolean, onPlay: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surface)
            .clickable { onPlay() }
            .padding(12.dp)
    )
}

@Composable
private fun LectureContent(doc: Dossier, host: DetailSectionHost) {
    val speech = host.speech
    val onPlay: (Int) -> Unit = { host.playLecture(it) }
    val blocks = ContentBlocks.lecture(doc)
    val minutes = ContentBlocks.estimateLectureMinutes(doc)
    run {
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("每日精读 · 演讲书稿", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(doc.lecture.title.ifBlank { "每天精读一本书：${doc.title}" }, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        buildString {
                            if (doc.lecture.tone.isNotBlank()) append("语气：${doc.lecture.tone} · ")
                            append(if (minutes > 0) "预计 $minutes 分钟" else "讲稿整理中")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                PillButton("▶ 从头播放", onClick = { onPlay(0) }, primary = true, enabled = blocks.isNotEmpty())
            }
        }
    }
    if (blocks.isEmpty()) {
        Text("这本作品的演讲书稿正在整理中。你可以先阅读原书，或在详情页按需生成完整讲稿。", style = MaterialTheme.typography.bodyMedium)
    }
    blocks.forEach { block ->
        val active = speech.speaking && speech.activeIndex == block.index
        if (block.type == "heading") {
            Text(
                block.text,
                style = MaterialTheme.typography.titleLarge,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlay(block.index) }
                    .padding(top = 6.dp, bottom = 2.dp)
            )
        } else {
            ParagraphCard(block.text, active) { onPlay(block.index) }
        }
    }
}

@Composable
private fun ChapterContent(doc: Dossier, host: DetailSectionHost) {
    val speech = host.speech
    val onPlayAll: () -> Unit = { host.playChapters() }
    val onOpenReader: () -> Unit = { host.openReader() }
    run {
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("章节导读 · 原书入口", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text("按章节进入原书", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text("这里提供关键章节的导读与节选，全文可以在阅读器中打开。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("打开阅读器", onClick = onOpenReader, primary = true)
                PillButton("▶ 朗读导读", onClick = onPlayAll, enabled = doc.chapters.isNotEmpty())
            }
        }
    }
    if (doc.chapters.isEmpty()) {
        Text(
            "章节导读整理中。你可以直接在阅读器中导入本地原书，系统会自动按“第X章 / 第X回”拆分章节。",
            style = MaterialTheme.typography.bodyMedium
        )
    }
    doc.chapters.forEachIndexed { index, chapter ->
        val active = speech.speaking && speech.activeIndex == index
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("%02d".format(index + 1), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.width(10.dp))
                Text(chapter.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val text = listOf(chapter.title, chapter.guide, chapter.excerpt).filter { it.isNotBlank() }.joinToString("。")
                    speech.play(listOf(text), intArrayOf(index), "正在朗读 · 章节导读", 0)
                }) { Text("▶") }
            }
            if (chapter.guide.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    chapter.guide,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else androidx.compose.ui.graphics.Color.Transparent)
                        .padding(4.dp)
                )
            }
            if (chapter.excerpt.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "「${chapter.excerpt}」",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }
    }
}

/** 模型服务设置对话框（自定义书页也会复用） */
@Composable
fun AiSettingsDialog(onDismiss: () -> Unit, onSaved: (AiSettings) -> Unit = {}) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(AiSettings.fromJson(LocalLibrary.aiSettings(context))) }
    var presetMenu by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val preset = AiService.PRESETS.firstOrNull { it.key == settings.preset } ?: AiService.PRESETS.first()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("模型服务设置") },
        text = {
            Column {
                Box {
                    Surface(
                        onClick = { presetMenu = true },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("预设：${preset.label}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("▾")
                        }
                    }
                    DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                        AiService.PRESETS.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.label) },
                                onClick = {
                                    settings = settings.copy(
                                        preset = item.key,
                                        baseURL = item.baseURL.ifBlank { settings.baseURL },
                                        model = item.model.ifBlank { settings.model }
                                    )
                                    presetMenu = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = settings.baseURL,
                    onValueChange = { settings = settings.copy(baseURL = it) },
                    label = { Text("服务地址") },
                    placeholder = { Text("http://10.0.2.2:11434/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = settings.model,
                    onValueChange = { settings = settings.copy(model = it) },
                    label = { Text("模型名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = settings.apiKey,
                    onValueChange = { settings = settings.copy(apiKey = it) },
                    label = { Text("API Key（本地服务可留空）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(preset.hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PillButton(
                        if (testing) "测试中…" else "测试连接",
                        onClick = {
                            if (testing) return@PillButton
                            testing = true
                            testResult = ""
                            LocalLibrary.saveAiSettings(context, settings.toJson())
                            scope.launch {
                                testResult = runCatching { AiService.testConnection(settings) }
                                    .fold({ "连接成功：模型回复 ${it.ifBlank { "已就绪" }}" }, { "失败：${it.message}" })
                                testing = false
                            }
                        },
                        enabled = !testing
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        testResult,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (testResult.startsWith("失败")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                LocalLibrary.saveAiSettings(context, settings.toJson())
                onSaved(settings)
                onDismiss()
            }) { Text("保存设置") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
