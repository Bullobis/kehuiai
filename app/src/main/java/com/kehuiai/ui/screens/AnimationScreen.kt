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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kehuiai.service.AnimationService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimationScreen(
    bitmap: Bitmap?,
    onNavigateBack: () -> Unit,
    onApply: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val animationService = remember { AnimationService(context) }
    
    var selectedAnimation by remember { mutableStateOf(AnimationService.AnimationType.KEN_BURNS) }
    var duration by remember { mutableFloatStateOf(3f) }
    var frameCount by remember { mutableIntStateOf(30) }
    var isProcessing by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf(bitmap) }
    
    val animations = remember { animationService.getAnimationTypes() }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎬 动画效果") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
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
                    .weight(0.35f)
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
                    Text("选择图片后预览动画效果", color = Color.White.copy(alpha = 0.5f))
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
                            Text("生成动画中...", color = Color.White)
                        }
                    }
                }
            }
            
            // 动画选择
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.25f)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(animations) { anim ->
                    AnimationCard(
                        name = anim.name,
                        emoji = getAnimationEmoji(anim),
                        isSelected = anim == selectedAnimation,
                        onClick = { selectedAnimation = anim }
                    )
                }
            }
            
            // 参数设置
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.35f)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 时长
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "时长: ${duration.toInt()}秒",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = duration,
                            onValueChange = { duration = it },
                            valueRange = 1f..10f,
                            steps = 8
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 帧数
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "帧数: $frameCount",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = frameCount.toFloat(),
                            onValueChange = { frameCount = it.toInt() },
                            valueRange = 10f..60f,
                            steps = 9
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 预览按钮
                Button(
                    onClick = {
                        bitmap?.let {
                            isProcessing = true
                            // 实际应用中应该调用动画服务生成动画帧
                            previewBitmap = it
                            isProcessing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing && bitmap != null
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("生成预览")
                }
            }
        }
    }
}

@Composable
fun AnimationCard(
    name: String,
    emoji: String,
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
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = emoji,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = name.replace("_", "\n"),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )
        }
    }
}

fun getAnimationEmoji(type: AnimationService.AnimationType): String {
    return when (type) {
        AnimationService.AnimationType.KEN_BURNS -> "📷"
        AnimationService.AnimationType.ZOOM_PULSE -> "🔍"
        AnimationService.AnimationType.FADE_TRANSITION -> "🌫️"
        AnimationService.AnimationType.SLIDE_TRANSITION -> "➡️"
        AnimationService.AnimationType.ROTATION_SPIN -> "🔄"
        AnimationService.AnimationType.SHAKE -> "💫"
        AnimationService.AnimationType.RAINBOW_SHIFT -> "🌈"
        AnimationService.AnimationType.GLITCH_EFFECT -> "⚡"
        AnimationService.AnimationType.PARTICLE_EXPLODE -> "💥"
        AnimationService.AnimationType.WAVE_DISTORT -> "🌊"
    }
}
