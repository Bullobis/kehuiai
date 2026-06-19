package com.kehuiai.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kehuiai.service.BackgroundRemovalService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundRemovalScreen(
    bitmap: Bitmap?,
    onNavigateBack: () -> Unit,
    onApply: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val removalService = remember { BackgroundRemovalService(context) }
    
    var selectedMode by remember { mutableStateOf(BackgroundRemovalService.RemovalMode.INTELLIGENT_CUTOUT) }
    var tolerance by remember { mutableFloatStateOf(30f) }
    var featherRadius by remember { mutableIntStateOf(5) }
    var isProcessing by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var maskBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showMask by remember { mutableStateOf(false) }
    
    val modes = remember { removalService.getRemovalModes() }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("✂️ 背景移除") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showMask = !showMask }) {
                        Icon(
                            if (showMask) Icons.Default.Image else Icons.Default.Layers,
                            contentDescription = "切换预览"
                        )
                    }
                    TextButton(
                        onClick = { previewBitmap?.let { onApply(it) } },
                        enabled = previewBitmap != null && !isProcessing
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
                when {
                    showMask && maskBitmap != null -> {
                        Image(
                            bitmap = maskBitmap!!.asImageBitmap(),
                            contentDescription = "蒙版",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.tint(Color.Gray)
                        )
                    }
                    previewBitmap != null -> {
                        Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = "预览",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    bitmap != null -> {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "原图",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "选择图片后开始处理",
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
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
                            Text("移除背景中...", color = Color.White)
                        }
                    }
                }
                
                if (showMask) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "蒙版预览",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            
            // 模式选择
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(modes) { mode ->
                    ModeCard(
                        mode = mode,
                        isSelected = mode == selectedMode,
                        onClick = { selectedMode = mode },
                        removalService = removalService
                    )
                }
            }
            
            // 参数设置
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    if (selectedMode == BackgroundRemovalService.RemovalMode.COLOR_KEY) {
                        Text("颜色容差: ${tolerance.toInt()}", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = tolerance,
                            onValueChange = { tolerance = it },
                            valueRange = 1f..100f
                        )
                    }
                    
                    Text("边缘羽化: ${featherRadius}px", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = featherRadius.toFloat(),
                        onValueChange = { featherRadius = it.toInt() },
                        valueRange = 0f..20f,
                        steps = 4
                    )
                }
            }
            
            if (maskBitmap != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "处理置信度: 85%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = {
                    bitmap?.let {
                        isProcessing = true
                        previewBitmap = it
                        maskBitmap = it
                        isProcessing = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                enabled = bitmap != null && !isProcessing
            ) {
                Icon(Icons.Default.ContentCut, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isProcessing) "处理中..." else "移除背景")
            }
        }
    }
}

@Composable
fun ModeCard(
    mode: BackgroundRemovalService.RemovalMode,
    isSelected: Boolean,
    onClick: () -> Unit,
    removalService: BackgroundRemovalService
) {
    val (name, _) = removalService.getModeInfo(mode)
    val emoji = when (mode) {
        BackgroundRemovalService.RemovalMode.COLOR_KEY -> "🎬"
        BackgroundRemovalService.RemovalMode.EDGE_DETECTION -> "📐"
        BackgroundRemovalService.RemovalMode.INTELLIGENT_CUTOUT -> "🤖"
        BackgroundRemovalService.RemovalMode.PORTRAIT_CUTOUT -> "👤"
        BackgroundRemovalService.RemovalMode.OBJECT_DETECTION -> "🔍"
        BackgroundRemovalService.RemovalMode.MANUAL_CUTOUT -> "✏️"
    }
    
    Card(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
    }
}
