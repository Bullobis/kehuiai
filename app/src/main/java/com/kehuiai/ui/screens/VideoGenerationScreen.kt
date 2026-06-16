package com.kehuiai.ui.screens

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kehuiai.service.video.VideoEngine
import com.kehuiai.service.video.VideoProgress
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 可绘AI v3.0 - 视频生成界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoGenerationScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 视频引擎
    val videoEngine = remember { VideoEngine(context) }
    
    // 状态
    var prompt by remember { mutableStateOf("") }
    var negativePrompt by remember { mutableStateOf("") }
    var duration by remember { mutableIntStateOf(4) } // 2-16秒
    var fps by remember { mutableIntStateOf(24) }
    var resolution by remember { mutableIntStateOf(VideoEngine.RESOLUTION_720P) }
    var videoMode by remember { mutableStateOf(VideoMode.TEXT_TO_VIDEO) }
    var isGenerating by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var statusMessage by remember { mutableStateOf("") }
    var completedPath by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val resolutions = listOf(
        VideoEngine.RESOLUTION_480P to "480p",
        VideoEngine.RESOLUTION_720P to "720p",
        VideoEngine.RESOLUTION_1080P to "1080p",
        VideoEngine.RESOLUTION_4K to "4K"
    )
    
    // 清理状态
    LaunchedEffect(isGenerating) {
        if (!isGenerating) {
            progress = 0f
            statusMessage = ""
            errorMessage = null
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎬 视频生成", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    if (isGenerating) {
                        IconButton(onClick = { 
                            videoEngine.cancel()
                            isGenerating = false
                        }) {
                            Icon(Icons.Default.Stop, "停止")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 视频模式选择
            item {
                Text("📽️ 生成模式", fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VideoMode.entries.forEach { mode ->
                        FilterChip(
                            selected = videoMode == mode,
                            onClick = { videoMode = mode },
                            label = { Text(mode.displayName) },
                            leadingIcon = {
                                Icon(
                                    when (mode) {
                                        VideoMode.TEXT_TO_VIDEO -> Icons.Default.TextFields
                                        VideoMode.IMAGE_TO_VIDEO -> Icons.Default.Image
                                        VideoMode.VIDEO_TO_VIDEO -> Icons.Default.Movie
                                        VideoMode.UPSCALE -> Icons.Default.HighQuality
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
            }
            
            // 提示词输入
            item {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("✍️ 提示词") },
                    placeholder = { Text("描述你想要生成的视频内容...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
            }
            
            // 负面提示词
            item {
                OutlinedTextField(
                    value = negativePrompt,
                    onValueChange = { negativePrompt = it },
                    label = { Text("🚫 负面提示词") },
                    placeholder = { Text("不想出现的内容...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )
            }
            
            // 视频参数
            item {
                Text("⚙️ 参数设置", fontWeight = FontWeight.Bold)
            }
            
            // 时长
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⏱️ 时长: ${duration}秒")
                    Slider(
                        value = duration.toFloat(),
                        onValueChange = { duration = it.toInt() },
                        valueRange = 2f..16f,
                        steps = 13,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            // 分辨率
            item {
                Text("📐 分辨率")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    resolutions.forEach { (res, name) ->
                        FilterChip(
                            selected = resolution == res,
                            onClick = { resolution = res },
                            label = { Text(name) }
                        )
                    }
                }
            }
            
            // FPS
            item {
                OutlinedTextField(
                    value = fps.toString(),
                    onValueChange = { fps = it.toIntOrNull() ?: 24 },
                    label = { Text("🎞️ FPS") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    suffix = { Text("帧/秒") }
                )
            }
            
            // 状态信息
            if (statusMessage.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(statusMessage)
                        }
                    }
                }
            }
            
            // 错误信息
            if (errorMessage != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                errorMessage ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            
            // 生成完成
            if (completedPath != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("✅ 视频生成完成！", fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "视频已保存至: $completedPath",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            
            // 生成按钮
            item {
                Button(
                    onClick = {
                        isGenerating = true
                        progress = 0f
                        statusMessage = "准备生成..."
                        completedPath = null
                        errorMessage = null
                        
                        scope.launch {
                            try {
                                val flow = videoEngine.generateText2Video(
                                    prompt = prompt,
                                    negativePrompt = negativePrompt,
                                    duration = duration,
                                    fps = fps,
                                    resolution = resolution,
                                    style = VideoEngine.STYLE_NORMAL,
                                    motion = VideoEngine.MOTION_FLOAT
                                )
                                
                                flow.collectLatest { progressState ->
                                    when (progressState) {
                                        is VideoProgress.Status -> {
                                            statusMessage = progressState.message
                                        }
                                        is VideoProgress.Progress -> {
                                            progress = progressState.progress
                                            statusMessage = "生成中 ${(progressState.progress * 100).toInt()}%"
                                        }
                                        is VideoProgress.Completed -> {
                                            completedPath = progressState.outputPath
                                            isGenerating = false
                                            statusMessage = "完成！"
                                        }
                                        is VideoProgress.Error -> {
                                            errorMessage = progressState.message
                                            isGenerating = false
                                        }
                                        else -> {}
                                    }
                                }
                            } catch (e: Exception) {
                                errorMessage = "生成失败: ${e.message}"
                                isGenerating = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = prompt.isNotBlank() && !isGenerating,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("生成中 ${(progress * 100).toInt()}%")
                    } else {
                        Icon(Icons.Default.AutoAwesome, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("🎬 开始生成")
                    }
                }
            }
            
            // 进度条
            if (isGenerating && progress > 0) {
                item {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            // 提示信息
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("💡 提示", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("• 视频生成需要较长时间，请耐心等待")
                        Text("• 建议使用英文提示词效果更好")
                        Text("• 生成的视频保存在本地")
                    }
                }
            }
        }
    }
    
    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            videoEngine.release()
        }
    }
}

enum class VideoMode(val displayName: String) {
    TEXT_TO_VIDEO("文生视频"),
    IMAGE_TO_VIDEO("图生视频"),
    VIDEO_TO_VIDEO("视频转视频"),
    UPSCALE("视频超分")
}
