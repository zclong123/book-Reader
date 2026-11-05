package com.classics.readingroom.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.classics.readingroom.MainViewModel
import com.classics.readingroom.ui.screens.AboutScreen
import com.classics.readingroom.ui.screens.BookDetailScreen
import com.classics.readingroom.ui.screens.CatalogScreen
import com.classics.readingroom.ui.screens.CustomBookScreen
import com.classics.readingroom.ui.screens.ReadScreen
import com.classics.readingroom.ui.screens.ShelfScreen
import kotlinx.coroutines.launch

/** 页面路由（自带返回栈，不依赖额外组件） */
sealed class Route {
    data object Catalog : Route()
    data class Detail(val id: String) : Route()
    data class Read(val id: String) : Route()
    data object Custom : Route()
    data object Shelf : Route()
    data object About : Route()
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Catalog("总目录", Icons.Filled.List),
    Search("搜索", Icons.Filled.Search),
    Custom("添加", Icons.Filled.AddCircle),
    Shelf("书架", Icons.Filled.Menu),
    About("关于", Icons.Filled.AccountCircle)
}

@Composable
fun ClassicsApp(vm: MainViewModel, onToggleRecord: () -> Unit) {
    val stack = remember { mutableStateListOf<Route>(Route.Catalog) }
    val current = stack.last()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var catalogTab by remember { mutableStateOf(Tab.Catalog) }

    fun push(route: Route) {
        vm.tts.stop()
        stack.add(route)
    }

    fun replaceRoot(route: Route) {
        vm.tts.stop()
        stack.clear()
        stack.add(route)
    }

    fun notify(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    // 总目录与搜索共用目录页，通过 tab 切换
    fun switchTab(tab: Tab) {
        catalogTab = tab
        vm.tts.stop()
        stack.clear()
        stack.add(Route.Catalog)
    }

    BackHandler(enabled = stack.size > 1) {
        vm.tts.stop()
        stack.removeAt(stack.lastIndex)
    }

    LaunchedEffect(vm.indexProgress) {
        // 索引完成后提示一次，避免用户误以为仍有内容未加载
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (current is Route.Catalog || current is Route.Custom || current is Route.About) {
                val selectedTab = when (current) {
                    is Route.Custom -> Tab.Custom
                    is Route.About -> Tab.About
                    else -> catalogTab
                }
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { switchTab(tab) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(pageBackground())
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            when (val route = current) {
                is Route.Catalog -> CatalogScreen(
                    vm = vm,
                    initialSearch = catalogTab == Tab.Search || catalogTab == Tab.Shelf,
                    shelfOnly = catalogTab == Tab.Shelf,
                    onOpenBook = { push(Route.Detail(it)) },
                    onAddBook = { push(Route.Custom) },
                    onShowShelf = { switchTab(Tab.Shelf) }
                )

                is Route.Detail -> BookDetailScreen(
                    vm = vm,
                    bookId = route.id,
                    onBack = { stack.removeAt(stack.lastIndex) },
                    onOpenBook = { push(Route.Detail(it)) },
                    onRead = { push(Route.Read(it)) },
                    onToggleRecord = onToggleRecord,
                    onNotify = ::notify
                )

                is Route.Read -> ReadScreen(
                    vm = vm,
                    bookId = route.id,
                    onBack = { stack.removeAt(stack.lastIndex) },
                    onToggleRecord = onToggleRecord
                )

                is Route.Custom -> CustomBookScreen(
                    vm = vm,
                    onOpenBook = { push(Route.Detail(it)) },
                    onNotify = ::notify
                )

                is Route.Shelf -> replaceRoot(Route.Catalog)

                is Route.About -> AboutScreen(vm = vm, onOpenBook = { push(Route.Detail(it)) })
            }
        }
    }
}
