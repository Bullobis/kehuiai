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
import com.kehuiai.service.VideoGenerationService

/**
 * 视频生成界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoGenScreen(
    sourceImage: Bitmap? = null,
    onNavigateBack: () -> Unit,
    onVideoGenerated: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val videoService = remember { VideoGenerationService(context) }
    
    var prompt by remember { mutableStateOf("") }
    var negativePrompt by remember { mutableStateOf("") }
    var duration by remember { mutableIntStateOf(5) }
    var resolution by remember { mutableStateOf(VideoGenerationService.VideoResolution.HD_720P) }
    var motion by remember { mutableStateOf(VideoGenerationService.MotionType.NONE) }
    var style by remember { mutableStateOf(VideoGenerationService.VideoStyle.NATURAL) }
    var isGenerating by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var progressStage by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }
    var seed by remember { mutableLongStateOf(-1L) }
    var useRandomSeed by remember { mutableStateOf(true) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎬 视频生成") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showAdvanced = !showAdvanced }) {
                        Icon(
                            if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.Settings,
                            contentDescription = "高级设置"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 源图像预览（如果有）
            if (sourceImage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    Box {
                        Image(
                            bitmap = sourceImage.asImageBitmap(),
                            contentDescription = "源图像",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "图生视频",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
            
            // 提示词输入
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("提示词") },
                        placeholder = { Text("描述你想要生成的视频内容...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = negativePrompt,
                        onValueChange = { negativePrompt = it },
                        label = { Text("反向提示词（可选）") },
                        placeholder = { Text("你不想要的内容...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3
                    )
                }
            }
            
            // 风格选择
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "视频风格",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(VideoGenerationService.VideoStyle.entries) { s ->
                            StyleChip(
                                style = s,
                                isSelected = s == style,
                                onClick = { style = s }
                            )
                        }
                    }
                }
            }
            
            // 基础参数
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "基础参数",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 时长
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("时长", modifier = Modifier.width(60.dp))
                        Slider(
                            value = duration.toFloat(),
                            onValueChange = { duration = it.toInt() },
                            valueRange = 1f..30f,
                            steps = 28,
                            modifier = Modifier.weight(1f)
                        )
                        Text("${duration}秒", modifier = Modifier.width(50.dp))
                    }
                    
                    // 分辨率
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("分辨率", modifier = Modifier.width(60.dp))
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(VideoGenerationService.VideoResolution.entries) { res ->
                                FilterChip(
                                    selected = res == resolution,
                                    onClick = { resolution = res },
                                    label = { Text(res.displayName) }
                                )
                            }
                        }
                    }
                }
            }
            
            // 运动类型
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "运动类型",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(VideoGenerationService.MotionType.entries) { m ->
                            FilterChip(
                                selected = m == motion,
                                onClick = { motion = m },
                                label = { Text(m.displayName) }
                            )
                        }
                    }
                }
            }
            
            // 高级设置
            if (showAdvanced) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "高级设置",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // 种子
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = useRandomSeed,
                                onCheckedChange = { useRandomSeed = it }
                            )
                            Text("随机种子")
                            
                            if (!useRandomSeed) {
                                Spacer(modifier = Modifier.width(16.dp))
                                OutlinedTextField(
                                    value = if (seed < 0) "" else seed.toString(),
                                    onValueChange = {
                                        seed = it.toLongOrNull() ?: -1L
                                    },
                                    label = { Text("种子") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }
            }
            
            // 生成按钮
            Button(
                onClick = {
                    isGenerating = true
                    // 开始生成
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isGenerating && (prompt.isNotBlank() || sourceImage != null)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${progressStage} ${(progress * 100).toInt()}%")
                } else {
                    Icon(Icons.Default.Movie, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("生成视频")
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun StyleChip(
    style: VideoGenerationService.VideoStyle,
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
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = style.emoji,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = style.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
        )
    }
}
