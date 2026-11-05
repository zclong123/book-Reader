package com.classics.readingroom.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.classics.readingroom.data.AiService
import com.classics.readingroom.data.AiSettings
import com.classics.readingroom.data.Book
import com.classics.readingroom.data.CatalogRepository
import com.classics.readingroom.data.LocalLibrary
import com.classics.readingroom.ui.BookCover
import com.classics.readingroom.ui.CardBox
import com.classics.readingroom.ui.PillButton
import com.classics.readingroom.ui.SectionTitle
import com.classics.readingroom.ui.Tag
import kotlinx.coroutines.launch


@Composable
fun CustomBookScreen(
    repo: CatalogRepository,
    onOpenBook: (Book) -> Unit,
    onCreated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("文史哲") }
    var busy by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Book?>(null) }
    var books by remember { mutableStateOf(LocalLibrary.loadCustomBooks(context)) }
    var showSettings by remember { mutableStateOf(false) }
    var settingsVersion by remember { mutableIntStateOf(0) }

    val categories = listOf("古典小说", "文史哲", "诗词曲赋", "近现代文学", "外国文学", "科普新知")
    val settings = remember(settingsVersion) { AiSettings.fromJson(LocalLibrary.aiSettings(context)) }
    val preset = AiService.PRESETS.firstOrNull { it.key == settings.preset } ?: AiService.PRESETS.first()

    fun refresh() {
        books = LocalLibrary.loadCustomBooks(context)
        onCreated()
    }

    fun createLocal() {
        val name = title.trim()
        if (name.isEmpty()) {
            error = "请先输入书名"
            return
        }
        error = ""
        val book = Book(
            id = LocalLibrary.newCustomId(),
            title = name,
            author = author.trim().ifBlank { "佚名" },
            country = "中国",
            era = "当代",
            category = category,
            level = "自定义",
            readingMinutes = 120,
            tags = listOf("自定义", "速览"),
            summary = "《$name》的自定义速览档案，可使用模型生成完整内容。",
            cat1 = "我的书架",
            cat2 = "自定义",
            cat3 = "自定义书",
            custom = true
        )
        LocalLibrary.addCustomBook(context, book)
        result = book
        refresh()
    }

    fun generateWithAi() {
        val name = title.trim()
        if (name.isEmpty()) {
            error = "请先输入书名"
            return
        }
        if (settings.baseURL.isBlank() || settings.model.isBlank()) {
            error = "请先在“模型服务设置”中填写服务地址与模型名称"
            showSettings = true
            return
        }
        error = ""
        busy = "生成精读档案"
        scope.launch {
            try {
                val id = LocalLibrary.newCustomId()
                val draft = Book(
                    id = id,
                    title = name,
                    author = author.trim().ifBlank { "佚名" },
                    country = "中国",
                    era = "当代",
                    category = category,
                    level = "自定义",
                    readingMinutes = 120,
                    tags = listOf("自定义"),
                    summary = "",
                    cat1 = "我的书架",
                    cat2 = "自定义",
                    cat3 = "自定义书",
                    custom = true
                )
                val (system, user) = AiService.detailPrompt(draft)
                val raw = AiService.extractJson(AiService.chat(settings, system, user, maxTokens = 8000))
                val normalized = AiService.normalize(raw, draft)
                val book = draft.copy(
                    author = normalized.author,
                    country = raw.optString("country", "中国"),
                    era = raw.optString("era", "当代"),
                    category = raw.optString("category", category),
                    tags = normalized.tags,
                    readingMinutes = normalized.readingMinutes,
                    summary = normalized.summary,
                    aiGenerated = true,
                    model = settings.model,
                    authorIntro = normalized.authorIntro,
                    subtitle = normalized.subtitle
                )
                LocalLibrary.addCustomBook(context, book)
                repo.saveDossier(normalized.copy(id = book.id))
                repo.reload()
                result = book
                refresh()
            } catch (e: Exception) {
                error = e.message ?: "生成失败，请稍后重试"
            } finally {
                busy = ""
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item("hero") {
            Column {
                Text("自定义书架 · 助读", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text("输入书名，生成一份精读档案", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "可以先加入书架看速览，也可以配置模型服务，一次生成故事梗概、阅读所获、章句赏析、演讲书稿与章节导读。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item("form") {
            CardBox {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("书名") },
                    placeholder = { Text("例如：百年孤独") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("作者（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("类别", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    categories.take(3).forEach { item ->
                        Chip(item, category == item) { category = item }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    categories.drop(3).forEach { item ->
                        Chip(item, category == item) { category = item }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(if (busy.isNotEmpty()) "正在生成…" else "模型生成精读档案", onClick = { generateWithAi() }, primary = true, enabled = busy.isEmpty())
                    PillButton("仅加入书架", onClick = { createLocal() }, enabled = busy.isEmpty())
                }
                if (busy.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("正在$busy，请保持页面打开。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
                Text("密钥只保存在本机；生成内容由模型产出，建议结合原书核对后再引用。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item("settings") {
            CardBox(onClick = { showSettings = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("模型服务设置", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${preset.label} · ${settings.model.ifBlank { "未填写模型" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("设置 ›", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        result?.let { book ->
            item("result") {
                CardBox {
                    Text("已加入我的书架", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(6.dp))
                    Row {
                        BookCover(book, size = "md")
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(book.title, style = MaterialTheme.typography.titleLarge)
                            Text("${book.author} · ${book.category}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text(book.summary, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                book.tags.take(3).forEach { Tag(it) }
                                Tag(if (book.aiGenerated) "模型生成" else "速览")
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("查看详情", onClick = { onOpenBook(book) }, primary = true)
                        PillButton("继续添加", onClick = { result = null })
                    }
                }
            }
        }

        item("list-head") { SectionTitle("我的书架", "${books.size} 部自定义书") }

        if (books.isEmpty()) {
            item("empty") {
                Text("还没有自定义书。输入书名，加入第一本。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        books.forEach { book ->
            item("custom-${book.id}") {
                CardBox(onClick = { onOpenBook(book) }, padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "${book.author} · ${book.category}" + if (book.aiGenerated) " · 模型生成" else " · 本地速览",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = {
                            LocalLibrary.removeCustomBook(context, book.id)
                            repo.reload()
                            if (result?.id == book.id) result = null
                            refresh()
                        }) { Text("删除") }
                    }
                }
            }
        }

        item("bottom") { Spacer(Modifier.height(24.dp)) }
    }

    if (showSettings) {
        AiSettingsDialog(
            onDismiss = { showSettings = false },
            onSaved = { settingsVersion++ }
        )
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}
