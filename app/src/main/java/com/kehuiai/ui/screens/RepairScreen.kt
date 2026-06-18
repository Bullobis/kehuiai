package com.kehuiai.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import com.kehuiai.service.ImageRepairService

/**
 * 图像修复界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepairScreen(
    bitmap: Bitmap?,
    onNavigateBack: () -> Unit,
    onApply: (Bitmap) -> Unit
) {
    var selectedRepair by remember { mutableStateOf<ImageRepairService.RepairType?>(null) }
    var strength by remember { mutableFloatStateOf(0.5f) }
    var isProcessing by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf(bitmap) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔧 图像修复") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (selectedRepair != null) {
                        TextButton(
                            onClick = { /* 预览 */ },
                            enabled = !isProcessing
                        ) {
                            Icon(Icons.Default.Preview, contentDescription = null)
                            Text("预览")
                        }
                        TextButton(
                            onClick = { previewBitmap?.let { onApply(it) } },
                            enabled = !isProcessing
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Text("应用")
                        }
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
                    .weight(1f)
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.White.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "请先选择一张图片",
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
                
                // 处理中遮罩
                if (isProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
            
            // 强度滑块
            if (selectedRepair != null) {
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
                        Text("修复强度", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.width(16.dp))
                        Slider(
                            value = strength,
                            onValueChange = { strength = it },
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(strength * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(48.dp)
                        )
                    }
                }
            }
            
            // 修复类型选择
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "修复类型",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(getRepairTypes()) { repair ->
                            RepairTypeCard(
                                type = repair,
                                isSelected = repair.type == selectedRepair,
                                onClick = { selectedRepair = repair.type }
                            )
                        }
                    }
                }
                
                // 详细说明
                selectedRepair?.let { type ->
                    item {
                        RepairDetailCard(type)
                    }
                }
            }
        }
    }
}

data class RepairTypeInfo(
    val type: ImageRepairService.RepairType,
    val name: String,
    val description: String,
    val icon: String
)

fun getRepairTypes(): List<RepairTypeInfo> = listOf(
    RepairTypeInfo(
        ImageRepairService.RepairType.SHARPEN,
        "锐化",
        "增强图像细节和边缘清晰度",
        "🔍"
    ),
    RepairTypeInfo(
        ImageRepairService.RepairType.DENOISE,
        "降噪",
        "去除图像噪点和颗粒",
        "🌫️"
    ),
    RepairTypeInfo(
        ImageRepairService.RepairType.DEBLUR,
        "去模糊",
        "修复运动模糊或失焦",
        "✨"
    ),
    RepairTypeInfo(
        ImageRepairService.RepairType.RESTORE_OLD,
        "老照片修复",
        "修复破损、褪色的老照片",
        "📜"
    ),
    RepairTypeInfo(
        ImageRepairService.RepairType.REMOVE_SCRATCHES,
        "去除划痕",
        "消除照片上的划痕和瑕疵",
        "🧹"
    ),
    RepairTypeInfo(
        ImageRepairService.RepairType.COLORIZE,
        "黑白上色",
        "为黑白照片智能上色",
        "🎨"
    ),
    RepairTypeInfo(
        ImageRepairService.RepairType.ENHANCE_FACE,
        "人像增强",
        "优化面部细节和肤色",
        "👤"
    ),
    RepairTypeInfo(
        ImageRepairService.RepairType.LOW_LIGHT_ENHANCE,
        "低光增强",
        "提亮暗光环境拍摄的照片",
        "🌙"
    )
)

@Composable
fun RepairTypeCard(
    type: RepairTypeInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(100.dp)
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
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = type.icon,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = type.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun RepairDetailCard(type: ImageRepairService.RepairType) {
    val info = getRepairTypes().find { it.type == type }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = info?.name ?: "详情",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = info?.description ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
