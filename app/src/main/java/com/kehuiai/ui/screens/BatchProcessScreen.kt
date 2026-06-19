package com.kehuiai.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.kehuiai.service.BatchProcessingService
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchProcessScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val batchService = remember { BatchProcessingService(context) }
    
    var selectedOperation by remember { mutableStateOf(BatchProcessingService.BatchOperation.RESIZE) }
    var targetWidth by remember { mutableIntStateOf(1024) }
    var targetHeight by remember { mutableIntStateOf(1024) }
    var quality by remember { mutableFloatStateOf(90f) }
    var selectedFormat by remember { mutableStateOf("png") }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    
    val operations = remember { BatchProcessingService.BatchOperation.entries }
    val formats = listOf("png", "jpg", "webp")
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📦 批量处理") },
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
                .padding(16.dp)
        ) {
            // 操作选择
            Text(
                "选择批量操作",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(
                modifier = Modifier.weight(0.3f)
            ) {
                items(operations) { op ->
                    OperationItem(
                        operation = op,
                        isSelected = op == selectedOperation,
                        onClick = { selectedOperation = op }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 参数设置
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // 操作说明
                    Text(
                        getOperationDescription(selectedOperation),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    when (selectedOperation) {
                        BatchProcessingService.BatchOperation.RESIZE -> {
                            Text("目标尺寸", style = MaterialTheme.typography.bodySmall)
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = targetWidth.toString(),
                                    onValueChange = { targetWidth = it.toIntOrNull() ?: 1024 },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("宽度") },
                                    suffix = { Text("px") }
                                )
                                Text(" × ", style = MaterialTheme.typography.titleLarge)
                                OutlinedTextField(
                                    value = targetHeight.toString(),
                                    onValueChange = { targetHeight = it.toIntOrNull() ?: 1024 },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("高度") },
                                    suffix = { Text("px") }
                                )
                            }
                        }
                        
                        BatchProcessingService.BatchOperation.FORMAT_CONVERT -> {
                            Text("输出格式", style = MaterialTheme.typography.bodySmall)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                formats.forEach { fmt ->
                                    FilterChip(
                                        selected = fmt == selectedFormat,
                                        onClick = { selectedFormat = fmt },
                                        label = { Text(fmt.uppercase()) }
                                    )
                                }
                            }
                        }
                        
                        BatchProcessingService.BatchOperation.COMPRESS -> {
                            Text("压缩质量: ${quality.toInt()}%", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = quality,
                                onValueChange = { quality = it },
                                valueRange = 10f..100f
                            )
                        }
                        
                        else -> {
                            Text("此操作使用默认参数", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 进度条
            if (isProcessing) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("处理进度: ${(progress * 100).toInt()}%")
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 开始按钮
            Button(
                onClick = { isProcessing = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isProcessing) "处理中..." else "开始批量处理")
            }
        }
    }
}

@Composable
fun OperationItem(
    operation: BatchProcessingService.BatchOperation,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getOperationIcon(operation),
                contentDescription = null,
                tint = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = getOperationName(operation),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = getOperationDescription(operation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun getOperationName(op: BatchProcessingService.BatchOperation): String {
    return when (op) {
        BatchProcessingService.BatchOperation.RESIZE -> "调整尺寸"
        BatchProcessingService.BatchOperation.FORMAT_CONVERT -> "格式转换"
        BatchProcessingService.BatchOperation.WATERMARK -> "添加水印"
        BatchProcessingService.BatchOperation.COMPRESS -> "压缩图片"
        BatchProcessingService.BatchOperation.THUMBNAIL -> "生成缩略图"
        BatchProcessingService.BatchOperation.FILTER_APPLY -> "应用滤镜"
        BatchProcessingService.BatchOperation.QUALITY_ENHANCE -> "质量增强"
        BatchProcessingService.BatchOperation.BATCH_RENAME -> "批量重命名"
    }
}

fun getOperationDescription(op: BatchProcessingService.BatchOperation): String {
    return when (op) {
        BatchProcessingService.BatchOperation.RESIZE -> "批量调整图片到指定尺寸"
        BatchProcessingService.BatchOperation.FORMAT_CONVERT -> "批量转换图片格式"
        BatchProcessingService.BatchOperation.WATERMARK -> "批量添加水印"
        BatchProcessingService.BatchOperation.COMPRESS -> "批量压缩图片减小体积"
        BatchProcessingService.BatchOperation.THUMBNAIL -> "批量生成缩略图"
        BatchProcessingService.BatchOperation.FILTER_APPLY -> "批量应用滤镜效果"
        BatchProcessingService.BatchOperation.QUALITY_ENHANCE -> "批量提升图片质量"
        BatchProcessingService.BatchOperation.BATCH_RENAME -> "批量重命名文件"
    }
}

fun getOperationIcon(op: BatchProcessingService.BatchOperation) = when (op) {
    BatchProcessingService.BatchOperation.RESIZE -> Icons.Default.AspectRatio
    BatchProcessingService.BatchOperation.FORMAT_CONVERT -> Icons.Default.SwapHoriz
    BatchProcessingService.BatchOperation.WATERMARK -> Icons.Default.WaterDrop
    BatchProcessingService.BatchOperation.COMPRESS -> Icons.Default.Compress
    BatchProcessingService.BatchOperation.THUMBNAIL -> Icons.Default.GridView
    BatchProcessingService.BatchOperation.FILTER_APPLY -> Icons.Default.Filter
    BatchProcessingService.BatchOperation.QUALITY_ENHANCE -> Icons.Default.HighQuality
    BatchProcessingService.BatchOperation.BATCH_RENAME -> Icons.Default.DriveFileRenameOutline
}
