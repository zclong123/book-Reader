package com.classics.readingroom.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.classics.readingroom.MainViewModel
import com.classics.readingroom.data.Book
import com.classics.readingroom.ui.BookCover
import com.classics.readingroom.ui.EmptyState
import com.classics.readingroom.ui.SectionTitle

/** 我的书架：收藏 + 自定义书 + 最近阅读 */
@Composable
fun ShelfScreen(vm: MainViewModel, onOpenBook: (String) -> Unit, onAddBook: () -> Unit) {
    val all = vm.categories
    val favorites = all.filter { vm.favorites.contains(it.id) }
    val custom = vm.customBooks
    val recent = all
        .mapNotNull { book ->
            val (chapter, total) = vm.progress(book.id)
            if (chapter > 0 || total > 0) book to chapter else null
        }
        .take(6)

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item("head") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("我的书架", style = MaterialTheme.typography.displaySmall)
                Text(
                    "收藏、最近读过的章节，以及自己添加的书",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (recent.isNotEmpty()) {
            item("recent-title") { SectionTitle("继续阅读", modifier = Modifier.padding(horizontal = 16.dp)) }
            items(recent, key = { "recent-${it.first.id}" }) { (book, chapter) ->
                ShelfRow(book, "上次读到第 ${chapter + 1} 章") { onOpenBook(book.id) }
            }
        }
        item("fav-title") { SectionTitle("收藏的作品", hint = "${favorites.size} 部", modifier = Modifier.padding(horizontal = 16.dp)) }
        if (favorites.isEmpty()) {
            item("fav-empty") {
                EmptyState("还没有收藏", "在目录里点“☆ 收藏”，作品会出现在这里")
            }
        } else {
            items(favorites, key = { "fav-${it.id}" }) { book ->
                ShelfRow(book, "${book.author} · ${book.cat3Label}") { onOpenBook(book.id) }
            }
        }
        item("custom-title") {
            SectionTitle("自定义书", hint = "${custom.size} 部", modifier = Modifier.padding(horizontal = 16.dp))
        }
        items(custom, key = { "custom-${it.id}" }) { book ->
            ShelfRow(book, "${book.author} · ${book.category}${if (book.aiGenerated) " · 已生成精读" else ""}") {
                onOpenBook(book.id)
            }
        }
        item("add") {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onAddBook) { Text("＋ 添加自定义书") }
                OutlinedButton(onClick = { vm.refreshShelf() }) { Text("刷新书架") }
            }
        }
    }
}

@Composable
private fun ShelfRow(book: Book, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(book, width = 50.dp, height = 72.dp)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
