package com.classics.readingroom.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.classics.readingroom.data.Book
import com.classics.readingroom.data.CatalogRepository
import com.classics.readingroom.data.UsageStats
import com.classics.readingroom.ui.screens.AboutScreen
import com.classics.readingroom.ui.screens.BookDetailScreen
import com.classics.readingroom.ui.screens.BookshelfScreen
import com.classics.readingroom.ui.screens.CatalogScreen
import com.classics.readingroom.ui.screens.CustomBookScreen
import com.classics.readingroom.ui.screens.ReaderScreen

sealed class Screen {
    object Catalog : Screen()
    object Shelf : Screen()
    object Custom : Screen()
    object About : Screen()
    data class Detail(val bookId: String) : Screen()
    data class Reader(val bookId: String) : Screen()
}

// 进程被系统回收后恢复页面栈
private fun screenKey(screen: Screen): String = when (screen) {
    is Screen.Catalog -> "catalog"
    is Screen.Shelf -> "shelf"
    is Screen.Custom -> "custom"
    is Screen.About -> "about"
    is Screen.Detail -> "detail:${screen.bookId}"
    is Screen.Reader -> "reader:${screen.bookId}"
}

private fun screenFromKey(key: String): Screen = when {
    key == "shelf" -> Screen.Shelf
    key == "custom" -> Screen.Custom
    key == "about" -> Screen.About
    key.startsWith("detail:") -> Screen.Detail(key.removePrefix("detail:"))
    key.startsWith("reader:") -> Screen.Reader(key.removePrefix("reader:"))
    else -> Screen.Catalog
}

private val TABS = listOf(
    Screen.Catalog to ("总目录" to "册"),
    Screen.Shelf to ("我的书架" to "藏"),
    Screen.Custom to ("添加书籍" to "＋"),
    Screen.About to ("关于" to "≡")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val repo = remember { CatalogRepository(context) }
    var loaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf("") }
    var dataVersion by remember { mutableStateOf(0) }

    LaunchedEffect(dataVersion) {
        runCatching { repo.ensureLoaded() }
            .onSuccess {
                if (dataVersion > 0) repo.reload()
                loaded = true
            }
            .onFailure { loadError = it.message ?: "书籍数据读取失败" }
    }

    // 精读档案索引在后台建立，不阻塞首页
    LaunchedEffect(loaded) {
        if (loaded) runCatching { repo.ensureIndex() }
    }

    val stack = rememberSaveable(saver = listSaver(
        save = { list -> list.map { screen -> screenKey(screen) } },
        restore = { keys -> keys.map { key -> screenFromKey(key) }.toMutableStateList() }
    )) { mutableStateListOf<Screen>(Screen.Catalog) }
    val current = stack.last()
    val tabIndex = TABS.indexOfFirst { it.first == current }.coerceAtLeast(0)

    val openBook: (Book) -> Unit = { book ->
        UsageStats.recordOpen(context, book.id)
        stack.add(Screen.Detail(book.id))
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun switchTab(index: Int) {
        stack.clear()
        stack.add(TABS[index].first)
    }

    BackHandler(enabled = stack.size > 1) { back() }

    val showBottomBar = current is Screen.Catalog || current is Screen.Shelf || current is Screen.Custom || current is Screen.About

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("古今名著精读馆", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            TABS.getOrNull(tabIndex)?.second?.first ?: "读一部 · 懂一部",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                    TABS.forEachIndexed { index, tab ->
                        val labelAndIcon = tab.second
                        NavigationBarItem(
                            selected = tabIndex == index && stack.size == 1,
                            onClick = { switchTab(index) },
                            icon = { Text(labelAndIcon.second, style = MaterialTheme.typography.titleMedium) },
                            label = { Text(labelAndIcon.first, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loadError.isNotEmpty() -> EmptyState("数据读取失败", loadError)
                !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("正在整理馆藏…", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> when (val screen = current) {
                    is Screen.Catalog -> CatalogScreen(
                        repo = repo,
                        onOpenBook = openBook,
                        onOpenCustom = { switchTab(2) }
                    )
                    is Screen.Shelf -> BookshelfScreen(
                        repo = repo,
                        onOpenBook = openBook,
                        onOpenCustom = { switchTab(2) },
                        onDataChanged = { dataVersion += 1 }
                    )
                    is Screen.Custom -> CustomBookScreen(
                        repo = repo,
                        onOpenBook = openBook,
                        onCreated = { dataVersion += 1 }
                    )
                    is Screen.About -> AboutScreen(repo)
                    is Screen.Detail -> {
                        val book = repo.getBook(screen.bookId)
                        if (book == null) {
                            EmptyState("没有找到这部作品", "它可能还未收录，返回总目录看看其它名著。")
                        } else {
                            BookDetailScreen(
                                repo = repo,
                                book = book,
                                onBack = { back() },
                                onOpenBook = { id -> stack.add(Screen.Detail(id)) },
                                onOpenReader = { stack.add(Screen.Reader(it.id)) }
                            )
                        }
                    }
                    is Screen.Reader -> {
                        val book = repo.getBook(screen.bookId)
                        if (book == null) {
                            EmptyState("没有找到这部作品", "返回目录重新选择。")
                        } else {
                            ReaderScreen(repo = repo, book = book, onBack = { back() })
                        }
                    }
                }
            }
        }
    }
}
