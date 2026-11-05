package com.classics.readingroom.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.classics.readingroom.MainViewModel

@Composable
fun AboutScreen(vm: MainViewModel, onOpenBook: (String) -> Unit) {
    val books = vm.categories
    val today = vm.todayBook()
    val textCount = vm.library.texts.size
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item("hero") {
            Column(Modifier.padding(16.dp)) {
                Text("关于本馆", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text("一个只属于你的名著阅读空间", style = MaterialTheme.typography.displaySmall)
                Text(
                    "汇集古今中外作品，提供精读内容、作者介绍、演讲书稿与原书阅读。内容全部随应用内置，不需要联网也能检索与阅读；语速、字号、录音与收藏都保存在本机。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        item("stats") {
            Card(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatRow("馆藏作品", "${books.size} 部")
                    StatRow("精读档案", if (vm.indexProgress >= 1f) "${vm.dossierCount} 部" else "正在统计…")
                    StatRow("公版全文", "${textCount} 部")
                    StatRow("我的收藏", "${vm.favorites.size} 部")
                    StatRow("自定义书", "${vm.customBooks.size} 部")
                    if (vm.indexProgress < 1f) {
                        LinearProgressIndicator(progress = { vm.indexProgress }, modifier = Modifier.fillMaxWidth())
                        Text("正在准备馆藏内容，首次启动需要几秒", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        item("cards") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AboutCard("检索与发现", "按三级分类、年代与推荐等级浏览，或搜索书名、作者、朝代与主题；每页最多二十部，翻页浏览。")
                AboutCard("精读内容", "精选作品提供故事脉络、阅读所获、章句赏析、章节导读与每日演讲书稿，篇幅按作品分量来定。")
                AboutCard("作者与作品", "作品详情页收录作者小传，并列出馆中同一作者的其他作品，可直接前往阅读。")
                AboutCard("朗读与跟读", "系统中文语音朗读，可切换主持人／朗诵语气与语速；也可以录下自己的朗读并回放。")
                AboutCard("原书阅读", "可阅读馆藏公版全文，按章节阅读并记住进度；支持字号与夜间模式。")
                AboutCard("自定义书架", "添加感兴趣的作品，创建速览或连接兼容模型补充精读内容。生成内容建议结合原书核对。")
                AboutCard("隐私与记录", "收藏、阅读进度与自定义书都由设备记住；录音可回放或清除，不会自动上传。")
            }
        }

        if (today != null) {
            item("today") {
                Row(Modifier.padding(16.dp)) {
                    OutlinedButton(onClick = { onOpenBook(today.id) }, modifier = Modifier.weight(1f)) {
                        Text("今日精读：${today.title}")
                    }
                }
            }
        }

        item("usage") {
            Column(Modifier.padding(16.dp)) {
                Text("开始阅读", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "1. 在总目录按分类或年代筛选，搜索作者与主题。\n" +
                        "2. 打开作品详情，读故事脉络与阅读所获，听每日讲稿。\n" +
                        "3. 点某一段即可从该段开始朗读，也可以录下自己的声音比较。\n" +
                        "4. 进入阅读原书，按章节慢慢读，进度会自动保存。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun AboutCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
