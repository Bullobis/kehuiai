package com.kehuiai.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kehuiai.service.ImageStitchingService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StitchScreen(
    bitmaps: List<Bitmap>,
    onNavigateBack: () -> Unit,
    onStitch: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val stitchService = remember { ImageStitchingService(context) }
    
    var selectedMode by remember { mutableStateOf(ImageStitchingService.StitchMode.HORIZONTAL_PANORAMA) }
    var isProcessing by remember { mutableStateOf(false) }
    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var gridColumns by remember { mutableIntStateOf(3) }
    
    val modes = remember { stitchService.getStitchModes() }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🖼️ 图片拼接") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { resultBitmap?.let { onStitch(it) } },
                        enabled = resultBitmap != null && !isProcessing
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
            // 已选图片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "已选择 ${bitmaps.size} 张图片",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (bitmaps.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(bitmaps) { bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    } else {
                        Text(
                            "请从相册选择多张图片",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // 拼接模式
            Text(
                "选择拼接模式",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(modes) { mode ->
                    ModeChip(
                        mode = mode,
                        isSelected = mode == selectedMode,
                        onClick = { selectedMode = mode }
                    )
                }
            }
            
            // 模式说明
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                val (name, desc) = stitchService.getModeInfo(selectedMode)
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 网格设置
            if (selectedMode == ImageStitchingService.StitchMode.GRID_COLLAGE) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("网格列数: $gridColumns", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = gridColumns.toFloat(),
                            onValueChange = { gridColumns = it.toInt() },
                            valueRange = 2f..5f,
                            steps = 2
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 预览/结果
            resultBitmap?.let { result ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("拼接预览", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Image(
                            bitmap = result.asImageBitmap(),
                            contentDescription = "拼接结果",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
            
            // 预览按钮
            Button(
                onClick = {
                    if (bitmaps.size >= 2) {
                        isProcessing = true
                        // 实际应用中应该调用stitchService
                        resultBitmap = bitmaps.firstOrNull()
                        isProcessing = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                enabled = bitmaps.size >= 2 && !isProcessing
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Preview, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isProcessing) "处理中..." else "开始拼接")
            }
        }
    }
}

@Composable
fun ModeChip(
    mode: ImageStitchingService.StitchMode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val emoji = when (mode) {
        ImageStitchingService.StitchMode.HORIZONTAL_PANORAMA -> "🌅"
        ImageStitchingService.StitchMode.VERTICAL_PANORAMA -> "⬇️"
        ImageStitchingService.StitchMode.GRID_COLLAGE -> "🟦"
        ImageStitchingService.StitchMode.FREE_COLLAGE -> "🎨"
        ImageStitchingService.StitchMode.DOCUMENT_SCAN -> "📄"
        ImageStitchingService.StitchMode.HDR_MERGE -> "🌈"
        ImageStitchingService.StitchMode.FOCUS_STACK -> "🎯"
    }
    
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(emoji + " " + mode.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }) }
    )
}
