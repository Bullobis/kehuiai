package com.kehuiai.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kehuiai.service.ImageCompositionService
import com.kehuiai.service.ImageCompositionService.*

/**
 * 图像合成界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    onNavigateBack: () -> Unit
) {
    var selectedLayout by remember { mutableStateOf(CollageLayout.GRID_2X2) }
    var selectedImages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var blendMode by remember { mutableStateOf(BlendMode.NORMAL) }
    var showBlendOptions by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎨 图像合成") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            // 布局选择
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "选择布局",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(CollageLayout.entries) { layout ->
                            LayoutChip(
                                layout = layout,
                                isSelected = layout == selectedLayout,
                                onClick = { selectedLayout = layout }
                            )
                        }
                    }
                }
            }
            
            // 混合模式选择
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "混合模式",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { showBlendOptions = !showBlendOptions }) {
                            Text(blendMode.displayName)
                            Icon(
                                if (showBlendOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }
                    }
                    
                    if (showBlendOptions) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(BlendMode.entries) { mode ->
                                FilterChip(
                                    selected = mode == blendMode,
                                    onClick = {
                                        blendMode = mode
                                        showBlendOptions = false
                                    },
                                    label = { Text(mode.displayName) }
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 功能按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { /* 添加图片 */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("添加图片")
                }
                
                Button(
                    onClick = { /* 生成拼图 */ },
                    modifier = Modifier.weight(1f),
                    enabled = selectedImages.size >= 2
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("生成")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 快捷功能
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "快捷功能",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickActionButton(
                            icon = Icons.Default.ContentCut,
                            label = "抠图",
                            modifier = Modifier.weight(1f),
                            onClick = { /* 抠图 */ }
                        )
                        QuickActionButton(
                            icon = Icons.Default.Layers,
                            label = "图层混合",
                            modifier = Modifier.weight(1f),
                            onClick = { /* 图层混合 */ }
                        )
                        QuickActionButton(
                            icon = Icons.Default.WaterDrop,
                            label = "添加水印",
                            modifier = Modifier.weight(1f),
                            onClick = { /* 添加水印 */ }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LayoutChip(
    layout: CollageLayout,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val layoutPreview = remember(layout) {
        when (layout) {
            CollageLayout.GRID_2X2 -> listOf(2, 2)
            CollageLayout.GRID_3X3 -> listOf(3, 3)
            CollageLayout.GRID_2X3 -> listOf(2, 3)
            CollageLayout.GRID_3X2 -> listOf(3, 2)
            CollageLayout.GRID_4X4 -> listOf(4, 4)
            CollageLayout.HORIZONTAL_2 -> listOf(1, 2)
            CollageLayout.VERTICAL_2 -> listOf(2, 1)
            CollageLayout.HORIZONTAL_3 -> listOf(1, 3)
            CollageLayout.VERTICAL_3 -> listOf(3, 1)
            else -> listOf(2, 2)
        }
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        // 简化的布局预览
        BoxWithConstraints(
            modifier = Modifier.size(48.dp)
        ) {
            val cols = layoutPreview.getOrElse(1) { 2 }
            val rows = layoutPreview.getOrElse(0) { 2 }
            val cellWidth = maxWidth / cols
            val cellHeight = maxHeight / rows
            
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    Box(
                        modifier = Modifier
                            .offset(x = (c * cellWidth.value).dp, y = (r * cellHeight.value).dp)
                            .size(cellWidth, cellHeight)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = layout.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

