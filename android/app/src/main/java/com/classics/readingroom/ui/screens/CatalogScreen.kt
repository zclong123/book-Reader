package com.classics.readingroom.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.classics.readingroom.data.Book
import com.classics.readingroom.data.CatalogQuery
import com.classics.readingroom.data.CatalogRepository
import com.classics.readingroom.data.CatalogSearch
import com.classics.readingroom.data.CategoryNode
import com.classics.readingroom.data.LocalLibrary
import com.classics.readingroom.data.UsageStats
import com.classics.readingroom.ui.BookCover
import com.classics.readingroom.ui.CardBox
import com.classics.readingroom.ui.ClassicsTheme
import com.classics.readingroom.ui.EmptyState
import com.classics.readingroom.ui.Muted
import com.classics.readingroom.ui.PillButton
import com.classics.readingroom.ui.SectionTitle
import com.classics.readingroom.ui.Tag
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20

@Composable
fun CatalogScreen(
    repo: CatalogRepository,
    onOpenBook: (Book) -> Unit,
    onOpenCustom: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()

    val tree = remember { repo.buildCategoryTree() }
    val stats = remember { repo.stats() }
    val today = remember { repo.todayBook() }
    val eraOptions = remember { CatalogSearch.eraBuckets(repo.books) }
    // 推荐排序会随使用记录变化，回到首页时重新计算
    var recommendVersion by remember { mutableIntStateOf(0) }
    val recommended = remember(recommendVersion) { repo.recommendedBooks(8) }
    var favorites by remember { mutableStateOf(LocalLibrary.favorites(context)) }
    // 从书架或详情页返回时刷新收藏状态
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                favorites = LocalLibrary.favorites(context)
                recommendVersion++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var keyword by remember { mutableStateOf("") }
    var committedKeyword by remember { mutableStateOf("") }
    var cat1 by remember { mutableStateOf<String?>(null) }
    var cat2 by remember { mutableStateOf<String?>(null) }
    var cat3 by remember { mutableStateOf<String?>(null) }
    var era by remember { mutableStateOf<String?>(null) }
    var level by remember { mutableStateOf<String?>(null) }
    var sortBy by remember { mutableStateOf(CatalogQuery.SORT_CATEGORY) }
    var page by remember { mutableIntStateOf(1) }
    var sortMenu by remember { mutableStateOf(false) }

    val query = remember(committedKeyword, cat1, cat2, cat3, era, level, sortBy) {
        CatalogQuery(committedKeyword, cat1, cat2, cat3, era, level, sortBy)
    }
    val filtered = remember(query, repo.books) { CatalogSearch.filter(repo.books, query) }
    val totalPages = maxOf(1, (filtered.size + PAGE_SIZE - 1) / PAGE_SIZE)
    LaunchedEffect(query) { page = 1 }
    val currentPage = page.coerceIn(1, totalPages)
    val pageBooks = remember(filtered, currentPage) {
        filtered.subList((currentPage - 1) * PAGE_SIZE, minOf(filtered.size, currentPage * PAGE_SIZE))
    }

    val cat2Options = tree.firstOrNull { it.name == cat1 }?.children.orEmpty()
    val cat3Options = cat2Options.firstOrNull { it.name == cat2 }?.children.orEmpty()

    fun resetFilters() {
        keyword = ""
        committedKeyword = ""
        cat1 = null
        cat2 = null
        cat3 = null
        era = null
        level = null
        sortBy = CatalogQuery.SORT_CATEGORY
    }

    fun toggleFavorite(book: Book) {
        val added = LocalLibrary.toggleFavorite(context, book.id)
        UsageStats.recordFavorite(context, book.id, added)
        favorites = LocalLibrary.favorites(context)
    }

    // 记录搜索：关键词与命中的前几本书，用于调整推荐排序
    LaunchedEffect(committedKeyword) {
        val word = committedKeyword.trim()
        if (word.length >= 2) {
            UsageStats.recordSearch(context, word, filtered.take(3))
        }
    }

    fun gotoPage(target: Int) {
        page = target.coerceIn(1, totalPages)
        scope.launch { listState.animateScrollToItem(0) }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "hero") {
            // 首页只留一行引导与两个入口，其他内容收进筛选与目录
            Column(Modifier.padding(top = 2.dp)) {
                Text("${stats.total} 部名著 · ${stats.categories} 个类别", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text("把一部名著，读成自己的东西。", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("今日精读：${today.title}", onClick = { onOpenBook(today) }, primary = true, modifier = Modifier.weight(1f, false))
                    PillButton("＋ 添加", onClick = onOpenCustom)
                }
            }
        }

        item(key = "hot") {
            CardBox {
                SectionTitle("今日推荐", "按重要程度与你的阅读偏好排序")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                    items(recommended, key = { "hot-${it.id}" }) { item ->
                        Column(Modifier.width(84.dp).clickable { onOpenBook(item) }) {
                            BookCover(item, size = "sm")
                            Spacer(Modifier.height(4.dp))
                            Text(item.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.level, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        item(key = "search") {
            OutlinedTextField(
                value = keyword,
                onValueChange = {
                    keyword = it
                    committedKeyword = it
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("搜索书名、作者、朝代、主题", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Text("⌕", style = MaterialTheme.typography.titleMedium) },
                trailingIcon = {
                    if (keyword.isNotEmpty()) TextButton(onClick = { keyword = ""; committedKeyword = "" }) { Text("清除") }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                shape = RoundedCornerShape(12.dp)
            )
        }

        item(key = "filters") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = cat1 == null,
                            onClick = { cat1 = null; cat2 = null; cat3 = null },
                            label = { Text("全部分类 ${repo.books.size}", style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                    items(tree, key = { it.name }) { node ->
                        FilterChip(
                            selected = cat1 == node.name,
                            onClick = {
                                cat1 = if (cat1 == node.name) null else node.name
                                cat2 = null
                                cat3 = null
                            },
                            label = { Text("${node.name} ${node.count}", style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                if (cat1 != null) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = cat2 == null,
                                onClick = { cat2 = null; cat3 = null },
                                label = { Text("全部二级", style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                        items(cat2Options, key = { it.name }) { node ->
                            FilterChip(
                                selected = cat2 == node.name,
                                onClick = {
                                    cat2 = if (cat2 == node.name) null else node.name
                                    cat3 = null
                                },
                                label = { Text("${node.name} ${node.count}", style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                }

                if (cat2 != null) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = cat3 == null,
                                onClick = { cat3 = null },
                                label = { Text("全部三级", style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                        items(cat3Options, key = { it.name }) { node ->
                            FilterChip(
                                selected = cat3 == node.name,
                                onClick = { cat3 = if (cat3 == node.name) null else node.name },
                                label = { Text("${node.name} ${node.count}", style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    item {
                        FilterChip(
                            selected = era != null,
                            onClick = { era = null },
                            label = { Text("全部年代", style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                    items(eraOptions, key = { it }) { option ->
                        FilterChip(
                            selected = era == option,
                            onClick = { era = if (era == option) null else option },
                            label = { Text(option, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    item {
                        FilterChip(
                            selected = level != null,
                            onClick = { level = null },
                            label = { Text("全部等级", style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                    items(listOf("殿堂级", "经典", "重要", "拓展"), key = { it }) { option ->
                        FilterChip(
                            selected = level == option,
                            onClick = { level = if (level == option) null else option },
                            label = { Text(option, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                    item {
                        Box {
                            FilterChip(
                                selected = false,
                                onClick = { sortMenu = true },
                                label = {
                                    Text(
                                        when (sortBy) {
                                            CatalogQuery.SORT_LEVEL -> "排序：推荐"
                                            CatalogQuery.SORT_TIME -> "排序：时长"
                                            CatalogQuery.SORT_TITLE -> "排序：书名"
                                            else -> "排序：类别"
                                        },
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            )
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                listOf(
                                    CatalogQuery.SORT_CATEGORY to "按类别",
                                    CatalogQuery.SORT_LEVEL to "按推荐等级",
                                    CatalogQuery.SORT_TIME to "按阅读时长",
                                    CatalogQuery.SORT_TITLE to "按书名"
                                ).forEach { (value, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            sortBy = value
                                            sortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "共 ${filtered.size} 部作品",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (committedKeyword.isNotEmpty() || cat1 != null || era != null || level != null) {
                        TextButton(onClick = { resetFilters() }) { Text("重置筛选", style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item(key = "empty") {
                EmptyState("没有找到匹配的作品", "换一个关键词，或重置筛选看看全部目录。") {
                    PillButton("重置筛选", onClick = { resetFilters() })
                }
            }
        }

        val grouped = committedKeyword.isBlank() && sortBy == CatalogQuery.SORT_CATEGORY
        if (grouped) {
            val groups = pageBooks.groupBy { it.cat3Label }.entries.sortedByDescending { it.value.size }
            groups.forEach { (name, books) ->
                item(key = "group-$name") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        Text("${books.size} 部", style = MaterialTheme.typography.labelSmall, color = Muted)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    }
                }
                items(books, key = { "b-${it.id}" }) { book ->
                    BookCard(
                        book = book,
                        hasDetail = repo.hasDetail(book.id),
                        favorite = favorites.contains(book.id),
                        onOpen = { onOpenBook(book) },
                        onToggleFavorite = { toggleFavorite(book) }
                    )
                }
            }
        } else {
            items(pageBooks, key = { "b-${it.id}" }) { book ->
                BookCard(
                    book = book,
                    hasDetail = repo.hasDetail(book.id),
                    favorite = favorites.contains(book.id),
                    onOpen = { onOpenBook(book) },
                    onToggleFavorite = { toggleFavorite(book) }
                )
            }
        }

        item(key = "pager") {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("上一页", onClick = { gotoPage(currentPage - 1) }, enabled = currentPage > 1)
                    Text("第 $currentPage / $totalPages 页", style = MaterialTheme.typography.bodySmall)
                    PillButton("下一页", onClick = { gotoPage(currentPage + 1) }, enabled = currentPage < totalPages)
                }
                Spacer(Modifier.height(6.dp))
                Text("每页最多 $PAGE_SIZE 部", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCard(
    book: Book,
    hasDetail: Boolean,
    favorite: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    highlight: Boolean = true
) {
    CardBox(onClick = onOpen, padding = 12.dp) {
        androidx.compose.foundation.layout.Row {
            BookCover(book, size = "sm")
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        book.cat3Label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (hasDetail) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            if (hasDetail) "精读已上线" else "仅简介",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasDetail) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${book.author} · ${book.era} · ${book.country}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(book.summary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    book.tags.take(3).forEach { tag ->
                        Tag(tag)
                        Spacer(Modifier.width(4.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (favorite) "★ 已收藏" else "☆ 收藏",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { onToggleFavorite() }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
                Text(
                    "约 ${book.readingMinutes} 分钟读完",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
