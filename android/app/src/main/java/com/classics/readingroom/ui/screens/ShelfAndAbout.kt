package com.classics.readingroom.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.classics.readingroom.data.Book
import com.classics.readingroom.data.CatalogRepository
import com.classics.readingroom.data.LocalLibrary
import com.classics.readingroom.data.UsageStats
import com.classics.readingroom.ui.BookCover
import com.classics.readingroom.ui.CardBox
import com.classics.readingroom.ui.EmptyState
import com.classics.readingroom.ui.PillButton
import com.classics.readingroom.ui.SectionTitle
import kotlinx.coroutines.launch

@Composable
fun BookshelfScreen(
    repo: CatalogRepository,
    onOpenBook: (Book) -> Unit,
    onOpenCustom: () -> Unit,
    onDataChanged: () -> Unit
) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val favorites = remember(revision, repo.books) { LocalLibrary.favorites(context).mapNotNull { repo.getBook(it) } }
    val custom = remember(revision, repo.books) { LocalLibrary.loadCustomBooks(context) }
    val readingProgress = remember(revision, repo.books) {
        repo.books.filter { LocalLibrary.readProgress(context, it.id) > 0 }.take(8)
    }

    // 进入书架时重新读取一次（从详情页收藏、添加书后返回）
    LaunchedEffect(Unit) { revision++ }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item("head") {
            Column {
                Text("我的书架", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "收藏、自己添加的书与正在读的书，都记在这台设备上。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item("fav-head") { SectionTitle("收藏的书", "${favorites.size} 部") }
        if (favorites.isEmpty()) {
            item("fav-empty") {
                Text("还没有收藏。在总目录中点“☆ 收藏”，书就会出现在这里。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        favorites.forEach { book ->
            item("fav-${book.id}") {
                CardBox(onClick = { onOpenBook(book) }, padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BookCover(book, size = "xs")
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(book.title, style = MaterialTheme.typography.titleMedium)
                            Text("${book.author} · ${book.cat3Label}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = {
                            LocalLibrary.toggleFavorite(context, book.id)
                            revision++
                        }) { Text("移除") }
                    }
                }
            }
        }

        item("reading-head") { SectionTitle("正在读", "按上次的进度继续") }
        if (readingProgress.isEmpty()) {
            item("reading-empty") {
                Text("还没有阅读记录。打开任意一本书的“阅读原书”，进度会自动保存。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        readingProgress.forEach { book ->
            item("reading-${book.id}") {
                CardBox(onClick = { onOpenBook(book) }, padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(book.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "读到第 ${LocalLibrary.readProgress(context, book.id) + 1} 章",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("继续 ›", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        item("custom-head") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("自定义书", "${custom.size} 部", Modifier.weight(1f))
                TextButton(onClick = onOpenCustom) { Text("添加＋") }
            }
        }
        if (custom.isEmpty()) {
            item("custom-empty") {
                EmptyState("还没有自定义书", "输入书名即可加入书架，也可以用模型生成一份完整精读档案。") {
                    PillButton("去添加", onClick = onOpenCustom, primary = true)
                }
            }
        }
        custom.forEach { book ->
            item("custom-${book.id}") {
                CardBox(onClick = { onOpenBook(book) }, padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BookCover(book, size = "xs")
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(book.title, style = MaterialTheme.typography.titleMedium)
                            Text("${book.author} · ${book.category}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = {
                            LocalLibrary.removeCustomBook(context, book.id)
                            repo.reload()
                            revision++
                        }) { Text("删除") }
                    }
                }
            }
        }

        item("bottom") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun AboutScreen(repo: CatalogRepository) {
    val stats = remember { repo.stats() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item("hero") {
            Column {
                Text("关于本馆", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text("一个只属于你的名著阅读空间", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "汇集古今中外作品，提供精读内容、作者介绍、演讲书稿与原书阅读。你可以按兴趣探索，也可以把自己的书加入书架。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val cards = listOf(
            "馆藏目录" to "目前收录 ${stats.total} 部作品，分为 ${stats.categories} 个类别，其中 ${stats.covered} 部配有精读内容。可按三级分类、年代与推荐等级浏览，也可搜索书名和作者。",
            "精读内容" to "精选作品提供故事情节、阅读所获、必背章句、章节导读与二十五到三十分钟的每日演讲书稿，帮你更轻松地进入作品。",
            "作者与作品" to "作品详情页收录作者简介，并列出馆内同一作者的其他作品，可直接前往阅读。",
            "朗读与跟读" to "支持主持人式朗读与朗诵式朗读，可调语速与音色；也可以录下自己的朗读并回放，对比停顿与语气。",
            "原书阅读" to "可阅读馆内收录的原书全文，也可以导入设备中的电子书，按章节阅读并自动保存进度。",
            "自定义与修改" to "输入书名即可加入书架；配置模型服务后可以生成完整精读档案，也可以自己编辑，或让模型按要求改写精华与讲稿。",
            "隐私与记录" to "收藏、阅读进度、自定义书与修改都保存在这台设备上；录音只在本地回放，不会自动上传。"
        )
        cards.forEachIndexed { index, (title, text) ->
            item("card-$index") {
                CardBox {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item("usage") {
            CardBox {
                SectionTitle("开始阅读")
                listOf(
                    "1. 探索目录" to "按三级类别或年代筛选，也可以搜索作者、朝代与主题。",
                    "2. 认识作者与作品" to "打开作品详情，阅读作者介绍与馆藏作品列表。",
                    "3. 听读与跟读" to "收听讲稿或阅读精华，也可以录下自己的朗读并回放。",
                    "4. 阅读原书" to "选择馆内全文，或导入电子书继续按章节阅读。"
                ).forEach { (title, text) ->
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        item("usage") { ReadingPreferenceCard(repo) }

        item("bottom") {
            Text(
                "古今名著精读馆 · 读一部，懂一部",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/**
 * 阅读偏好：展示本机累计的搜索与阅读记录，用来解释推荐排序的变化。
 * 记录只在本机，可导出汇总，也可以一键清空。
 */
@Composable
private fun ReadingPreferenceCard(repo: CatalogRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    val searches = remember(version) { UsageStats.topSearches(context, 6) }
    val scores = remember(version) { UsageStats.bookScores(context) }
    val topBooks = remember(version, repo.books) {
        scores.entries
            .sortedByDescending { it.value }
            .mapNotNull { entry -> repo.getBook(entry.key)?.let { it to entry.value } }
            .take(5)
    }
    val categories = remember(version, repo.books) {
        UsageStats.categoryScores(context, repo.books).entries.sortedByDescending { it.value }.take(3)
    }

    CardBox {
        SectionTitle("阅读偏好与推荐", "记录只保存在本机")
        Text(
            "你搜索过的书名、打开过的书和读完的章节都会累计成偏好，用来把「今日推荐」和「相似的书」排得更贴近你的口味。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (searches.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("常搜的关键词", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            com.classics.readingroom.ui.QuickRow {
                searches.take(4).forEach { (word, _) ->
                    com.classics.readingroom.ui.Tag(word, highlighted = true)
                }
            }
        }
        if (categories.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("偏好的类别", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            com.classics.readingroom.ui.QuickRow {
                categories.forEach { (name, _) -> com.classics.readingroom.ui.Tag(name) }
            }
        }
        if (topBooks.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("读得最多的书", style = MaterialTheme.typography.titleSmall)
            topBooks.forEach { (book, _) ->
                Text(
                    "《${book.title}》",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row {
            PillButton("导出汇总", onClick = {
                val json = UsageStats.export(context, repo.books)
                scope.launch {
                    val ok = runCatching { saveTextFile(context, "阅读偏好汇总.json", json) }.getOrDefault(false)
                    message = if (ok) "汇总已导出，可用于分析推荐权重。" else "导出失败，请重试。"
                }
            })
            Spacer(Modifier.width(8.dp))
            PillButton("清空记录", onClick = {
                UsageStats.clear(context)
                version++
                message = "已清空本机的搜索与阅读记录。"
            })
        }
        if (message.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 通过系统分享面板保存 / 发送文本文件 */
private fun saveTextFile(context: android.content.Context, name: String, content: String): Boolean {
    val file = java.io.File(context.cacheDir, name)
    file.writeText(content)
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "保存或分享汇总"))
    return true
}
