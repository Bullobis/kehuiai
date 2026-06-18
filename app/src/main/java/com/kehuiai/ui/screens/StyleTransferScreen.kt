package com.kehuiai.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.kehuiai.service.StyleTransferService

/**
 * 风格迁移界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleTransferScreen(
    bitmap: Bitmap?,
    onNavigateBack: () -> Unit,
    onApply: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val styleService = remember { StyleTransferService(context) }
    
    var selectedStyle by remember { mutableStateOf(StyleTransferService.ArtStyle.OIL_PAINTING) }
    var strength by remember { mutableFloatStateOf(0.8f) }
    var preserveColor by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf(bitmap) }
    var selectedCategory by remember { mutableStateOf("all") }
    
    val categories = listOf(
        "all" to "全部",
        "painting" to "绘画",
        "art" to "艺术",
        "digital" to "数字",
        "photo" to "照片",
        "cultural" to "文化"
    )
    
    val styles = remember(selectedCategory) {
        if (selectedCategory == "all") {
            styleService.getAllStyles()
        } else {
            styleService.getStyleByCategory(selectedCategory)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎨 风格迁移") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { /* 预览 */ },
                        enabled = !isProcessing && bitmap != null
                    ) {
                        Icon(Icons.Default.Preview, contentDescription = null)
                        Text("预览")
                    }
                    TextButton(
                        onClick = { previewBitmap?.let { onApply(it) } },
                        enabled = !isProcessing && previewBitmap != null
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Text("应用")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 预览区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f)
                    .background(Color.Black)
            ) {
                previewBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "预览",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } ?: Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("选择图片后预览效果", color = Color.White.copy(alpha = 0.5f))
                }
                
                if (isProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("处理中...", color = Color.White)
                        }
                    }
                }
            }
            
            // 分类选择
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(categories) { (id, name) ->
                        FilterChip(
                            selected = id == selectedCategory,
                            onClick = { selectedCategory = id },
                            label = { Text(name, style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            // 强度滑块
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("强度", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = strength,
                        onValueChange = { strength = it },
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${(strength * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(40.dp)
                    )
                }
            }
            
            // 风格选择
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.35f)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(styles) { style ->
                    StyleCard(
                        style = style,
                        isSelected = style == selectedStyle,
                        onClick = { selectedStyle = style }
                    )
                }
            }
            
            // 选项
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = preserveColor,
                        onCheckedChange = { preserveColor = it }
                    )
                    Text("保留原色")
                }
            }
        }
    }
}

@Composable
fun StyleCard(
    style: StyleTransferService.ArtStyle,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected)
            CardDefaults.outlinedCardBorder()
        else
            null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = style.emoji,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = style.displayName,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}
