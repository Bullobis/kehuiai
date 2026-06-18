package com.kehuiai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 工具箱界面
 * 集合各种实用工具
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onNavigateBack: () -> Unit,
    onNavigateTo: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("🛠️ 工具箱", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // AI 工具
            item {
                ToolCategory(
                    title = "🤖 AI 工具",
                    description = "人工智能驱动的工具"
                )
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ToolCard(
                        title = "AI 对话",
                        description = "与 AI 助手聊天",
                        icon = Icons.Default.Chat,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateTo("chat") }
                    )
                    ToolCard(
                        title = "提示词助手",
                        description = "优化提示词",
                        icon = Icons.Default.AutoFixHigh,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateTo("prompt_assistant") }
                    )
                }
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ToolCard(
                        title = "图像增强",
                        description = "AI 提升画质",
                        icon = Icons.Default.AutoAwesome,
                        modifier = Modifier.weight(1f),
                        onClick = { /* TODO */ }
                    )
                    ToolCard(
                        title = "风格迁移",
                        description = "艺术风格转换",
                        icon = Icons.Default.Palette,
                        modifier = Modifier.weight(1f),
                        onClick = { /* TODO */ }
                    )
                }
            }
            
            // 图像处理
            item {
                Spacer(modifier = Modifier.height(8.dp))
                ToolCategory(
                    title = "🎨 图像处理",
                    description = "基础的图像编辑工具"
                )
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ToolCard(
                        title = "图像调整",
                        description = "亮度/对比度/饱和度",
                        icon = Icons.Default.Tune,
                        modifier = Modifier.weight(1f),
                        onClick = { /* TODO */ }
                    )
                    ToolCard(
                        title = "滤镜预设",
                        description = "丰富滤镜效果",
                        icon = Icons.Default.FilterVintage,
                        modifier = Modifier.weight(1f),
                        onClick = { /* TODO */ }
                    )
                }
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ToolCard(
                        title = "局部重绘",
                        description = "智能局部修改",
                        icon = Icons.Default.Brush,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateTo("inpaint") }
                    )
                    ToolCard(
                        title = "图像放大",
                        description = "AI 超分辨率",
                        icon = Icons.Default.ZoomIn,
                        modifier = Modifier.weight(1f),
                        onClick = { /* TODO */ }
                    )
                }
            }
            
            // 视频处理
            item {
                Spacer(modifier = Modifier.height(8.dp))
                ToolCategory(
                    title = "🎬 视频处理",
                    description = "视频生成和编辑工具"
                )
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ToolCard(
                        title = "文生视频",
                        description = "文字生成视频",
                        icon = Icons.Default.Movie,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateTo("video_generation") }
                    )
                    ToolCard(
                        title = "图生视频",
                        description = "图像生成视频",
                        icon = Icons.Default.VideoLibrary,
                        modifier = Modifier.weight(1f),
                        onClick = { /* TODO */ }
                    )
                }
            }
            
            // 资源
            item {
                Spacer(modifier = Modifier.height(8.dp))
                ToolCategory(
                    title = "📦 资源中心",
                    description = "模型和素材管理"
                )
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ToolCard(
                        title = "模型市场",
                        description = "下载 AI 模型",
                        icon = Icons.Default.ModelTraining,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateTo("model_market") }
                    )
                    ToolCard(
                        title = "画廊",
                        description = "查看作品展示",
                        icon = Icons.Default.Collections,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateTo("gallery") }
                    )
                }
            }
            
            // 其他
            item {
                Spacer(modifier = Modifier.height(8.dp))
                ToolCategory(
                    title = "⚡ 快捷功能",
                    description = "常用快捷操作"
                )
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ToolCard(
                        title = "历史记录",
                        description = "生成历史",
                        icon = Icons.Default.History,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateTo("history") }
                    )
                    ToolCard(
                        title = "收藏夹",
                        description = "收藏的作品",
                        icon = Icons.Default.Favorite,
                        modifier = Modifier.weight(1f),
                        onClick = { /* TODO */ }
                    )
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun ToolCategory(title: String, description: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolCard(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
