package com.kehuiai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import androidx.navigation.NavController

/**
 * 画廊/社区界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    navController: NavController? = null,
    onNavigateBack: (() -> Unit)? = null
) {
    var selectedTab by remember { mutableStateOf(0) }
    
    val tabs = listOf("热门", "最新", "关注", "收藏")
    
    // 示例画廊数据
    val galleryItems = remember {
        listOf(
            GalleryItem("1", "动漫少女", "作者A", "动漫风格", 1280),
            GalleryItem("2", "风景摄影", "作者B", "摄影", 856),
            GalleryItem("3", "未来城市", "作者C", "科幻", 2341),
            GalleryItem("4", "人像艺术", "作者D", "艺术", 567),
            GalleryItem("5", "自然风光", "作者E", "风景", 1892),
            GalleryItem("6", "赛博朋克", "作者F", "科幻", 3214),
            GalleryItem("7", "油画风格", "作者G", "艺术", 445),
            GalleryItem("8", "建筑摄影", "作者H", "摄影", 678)
        )
    }
    
    fun goBack() {
        onNavigateBack?.invoke() ?: navController?.popBackStack()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎨 画廊", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { goBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { /* 搜索 */ }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = { /* 筛选 */ }) {
                        Icon(Icons.Default.FilterList, contentDescription = "筛选")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 标签栏
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    when (index) {
                                        0 -> Icons.Default.LocalFireDepartment
                                        1 -> Icons.Default.NewReleases
                                        2 -> Icons.Default.Star
                                        else -> Icons.Default.Favorite
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(title)
                            }
                        }
                    )
                }
            }
            
            // 画廊网格
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(galleryItems) { item ->
                    GalleryCard(
                        item = item,
                        onClick = { /* 打开详情 */ }
                    )
                }
            }
        }
    }
}

@Composable
fun GalleryCard(
    item: GalleryItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 图像占位符
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        when (item.category) {
                            "动漫风格" -> Color(0xFFFFB6C1)
                            "摄影" -> Color(0xFF87CEEB)
                            "科幻" -> Color(0xFF9370DB)
                            "艺术" -> Color(0xFF98FB98)
                            "风景" -> Color(0xFFFFD700)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // 底部信息栏
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(8.dp)
            ) {
                Column {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            item.author,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            item.likes.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

data class GalleryItem(
    val id: String,
    val title: String,
    val author: String,
    val category: String,
    val likes: Int
)
