package com.kehuiai.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import com.kehuiai.service.ColorGradingService

/**
 * 色彩分级界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorGradeScreen(
    bitmap: Bitmap?,
    onNavigateBack: () -> Unit,
    onApply: (Bitmap) -> Unit
) {
    val gradingService = remember { ColorGradingService.getInstance() }
    
    var selectedGrade by remember { mutableStateOf(ColorGradingService.ColorGrade.NONE) }
    var previewBitmap by remember { mutableStateOf(bitmap) }
    var strength by remember { mutableFloatStateOf(0.5f) }
    var showCustomPanel by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎨 色彩分级") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { showCustomPanel = !showCustomPanel }) {
                        Icon(Icons.Default.Tune, contentDescription = null)
                        Text("自定义")
                    }
                    TextButton(onClick = { 
                        previewBitmap?.let { onApply(it) }
                    }) {
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
                    Text("请先选择图片", color = Color.White)
                }
            }
            
            // 强度滑块
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
                    Text("强度", style = MaterialTheme.typography.bodyMedium)
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
            
            // 预设选择
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "预设风格",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(ColorGradingService.ColorGrade.entries) { grade ->
                            GradeChip(
                                grade = grade,
                                isSelected = grade == selectedGrade,
                                onClick = { selectedGrade = grade }
                            )
                        }
                    }
                }
            }
            
            // 自定义面板
            if (showCustomPanel) {
                CustomGradePanel(
                    onParamChange = { /* 更新预览 */ }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun GradeChip(
    grade: ColorGradingService.ColorGrade,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .padding(12.dp)
    ) {
        Text(
            text = grade.emoji,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = grade.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun CustomGradePanel(
    onParamChange: () -> Unit
) {
    var shadows by remember { mutableFloatStateOf(0f) }
    var midtones by remember { mutableFloatStateOf(0f) }
    var highlights by remember { mutableFloatStateOf(0f) }
    var temperature by remember { mutableFloatStateOf(0f) }
    var tint by remember { mutableFloatStateOf(0f) }
    var vibrance by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(0f) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "自定义调整",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // 色调曲线
            GradeSlider("阴影", shadows, { shadows = it })
            GradeSlider("中间调", midtones, { midtones = it })
            GradeSlider("高光", highlights, { highlights = it })
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            GradeSlider("色温", temperature, { temperature = it })
            GradeSlider("色调", tint, { tint = it })
            GradeSlider("自然饱和度", vibrance, { vibrance = it })
            GradeSlider("对比度", contrast, { contrast = it })
        }
    }
}

@Composable
fun GradeSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(80.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = -1f..1f,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (value >= 0) "+${(value * 100).toInt()}" else "${(value * 100).toInt()}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(40.dp)
        )
    }
}
