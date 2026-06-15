package com.kehuiai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kehuiai.data.model.HistoryItem
import com.kehuiai.data.model.HistoryStatus
import com.kehuiai.data.model.SchedulerType
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 可绘AI v3.0 历史记录界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📷 历史记录", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    IconButton(onClick = { /* 分享 */ }) {
                        Icon(Icons.Default.Share, "分享")
                    }
                }
            )
        }
    ) { padding ->
        // TODO: 从 Repository 获取真实历史数据
        val historyItems = remember {
            (1..12).map { idx ->
                HistoryItem(
                    id = idx.toString(),
                    timestamp = System.currentTimeMillis() - idx * 3600000L,
                    params = com.kehuiai.data.model.GenerationParams(
                        positivePrompt = "生成的图像 #$idx",
                        negativePrompt = "",
                        baseModel = com.kehuiai.data.model.BaseModelType.SD_1_5,
                        width = 1024,
                        height = 1024,
                        steps = 30,
                        guidanceScale = 7.5f,
                        seed = System.currentTimeMillis(),
                        scheduler = SchedulerType.EULER,
                        batchSize = 1
                    ),
                    outputPaths = emptyList(),
                    thumbnailPath = null,
                    status = if (idx % 4 == 0) HistoryStatus.FAILED else HistoryStatus.COMPLETED
                )
            }
        }
        
        if (historyItems.isEmpty()) {
            EmptyHistoryView(modifier = Modifier.padding(padding))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(historyItems, key = { it.id }) { item ->
                    HistoryCard(item = item, onClick = { /* 打开详情 */ })
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                "暂无历史记录",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "开始生成你的第一张图像吧！",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun HistoryCard(
    item: HistoryItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 缩略图
            if (item.thumbnailPath != null && File(item.thumbnailPath).exists()) {
                AsyncImage(
                    model = File(item.thumbnailPath),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // 占位图
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
            }
            
            // 底部信息栏
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        val promptText = item.params?.positivePrompt ?: "生成的图像"
                        Text(
                            promptText,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                formatTime(item.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            StatusBadge(item.status)
                        }
                    }
                }
            }
            
            // 失败标记
            if (item.status == HistoryStatus.FAILED) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = "生成失败",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(20.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: HistoryStatus) {
    val (color, icon) = when (status) {
        HistoryStatus.COMPLETED -> MaterialTheme.colorScheme.primary to Icons.Default.CheckCircle
        HistoryStatus.FAILED -> MaterialTheme.colorScheme.error to Icons.Default.Error
        HistoryStatus.CANCELLED -> MaterialTheme.colorScheme.outline to Icons.Default.Cancel
    }
    
    Icon(
        icon,
        contentDescription = status.name,
        modifier = Modifier.size(16.dp),
        tint = color
    )
}

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000}分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000}小时前"
        else -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
