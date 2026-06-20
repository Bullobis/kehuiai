package com.kehuiai.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.kehuiai.service.ImageQualityAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 可绘AI v3.6.6 图像质量评估界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityAnalysisScreen(
    imagePath: String? = null,
    navController: NavController? = null
) {
    val context = LocalContext.current
    val analyzer = remember { ImageQualityAnalyzer.getInstance(context) }
    val isAnalyzing by analyzer.isAnalyzing.collectAsState()
    
    var qualityResult by remember { mutableStateOf<ImageQualityAnalyzer.QualityResult?>(null) }
    var currentImagePath by remember { mutableStateOf<String?>(imagePath) }
    
    // 加载图像
    val bitmap = remember(currentImagePath) {
        currentImagePath?.let { path ->
            try {
                BitmapFactory.decodeFile(path)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📊 质量评估", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 图像预览
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "🖼️ 图像预览",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (bitmap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                coil.compose.AsyncImage(
                                    model = currentImagePath,
                                    contentDescription = "待分析图像",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                "分辨率: ${bitmap.width} x ${bitmap.height}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.Image,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                    Text(
                                        "未选择图像",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // 分析按钮
            item {
                Button(
                    onClick = {
                        bitmap?.let { bmp ->
                            CoroutineScope(Dispatchers.Main).launch {
                                qualityResult = analyzer.analyze(bmp)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = bitmap != null && !isAnalyzing,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("分析中...")
                    } else {
                        Icon(Icons.Default.Analytics, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("开始分析")
                    }
                }
            }
            
            // 分析结果
            qualityResult?.let { result ->
                // 总体评分
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "📊 总体评分",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // 圆形评分
                            QualityScoreCircle(score = result.overallScore)
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // 质量等级
                            Text(
                                analyzer.getQualityGrade(result.overallScore),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = getScoreColor(result.overallScore)
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // 建议
                            Text(
                                result.recommendation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                
                // 详细指标
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                "📈 详细指标",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            QualityMetricRow(
                                label = "清晰度",
                                value = result.sharpness,
                                icon = Icons.Default.CenterFocusStrong,
                                description = "图像边缘清晰程度"
                            )
                            
                            QualityMetricRow(
                                label = "亮度均衡",
                                value = result.brightness,
                                icon = Icons.Default.Brightness6,
                                description = "图像整体亮度是否适中"
                            )
                            
                            QualityMetricRow(
                                label = "对比度",
                                value = result.contrast,
                                icon = Icons.Default.Contrast,
                                description = "明暗区域差异程度"
                            )
                            
                            QualityMetricRow(
                                label = "噪点水平",
                                value = result.noiseLevel,
                                icon = Icons.Default.Grain,
                                description = "图像噪点多少 (越高越好)"
                            )
                            
                            QualityMetricRow(
                                label = "色彩饱和",
                                value = result.colorfulness,
                                icon = Icons.Default.Palette,
                                description = "颜色鲜艳程度"
                            )
                            
                            QualityMetricRow(
                                label = "压缩质量",
                                value = result.compressionArtifacts,
                                icon = Icons.Default.Compress,
                                description = "压缩伪影程度 (越高越好)"
                            )
                        }
                    }
                }
                
                // 优化建议
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "💡 优化建议",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            generateSuggestions(result).forEach { suggestion ->
                                Row(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text("• ", style = MaterialTheme.typography.bodyMedium)
                                    Text(suggestion, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
            
            // 底部说明
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "ℹ️ 关于评分说明",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            """
                            • 评分基于多种图像质量指标综合计算
                            • 清晰度：使用 Laplacian 方差评估边缘清晰程度
                            • 亮度：检测是否存在过曝或欠曝
                            • 对比度：评估明暗区域差异是否适中
                            • 噪点：检测图像噪点水平
                            • 色彩：评估颜色饱和度是否自然
                            • 压缩：检测 JPEG 压缩伪影程度
                            """.trimIndent(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
    
    // 释放 bitmap
    DisposableEffect(Unit) {
        onDispose {
            bitmap?.recycle()
        }
    }
}

@Composable
private fun QualityScoreCircle(score: Float) {
    val animatedScore by animateFloatAsState(
        targetValue = score,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "score"
    )
    
    val primaryColor = getScoreColor(score)
    
    Box(
        modifier = Modifier.size(150.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(150.dp)) {
            val strokeWidth = 16.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            
            // 背景圆
            drawCircle(
                color = Color.Gray.copy(alpha = 0.2f),
                radius = radius,
                style = Stroke(width = strokeWidth)
            )
            
            // 进度圆
            drawArc(
                color = primaryColor,
                startAngle = -90f,
                sweepAngle = animatedScore * 3.6f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(size.width - strokeWidth, size.height - strokeWidth)
            )
        }
        
        Text(
            text = "${animatedScore.toInt()}",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = primaryColor
        )
    }
}

@Composable
private fun QualityMetricRow(
    label: String,
    value: Float,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String
) {
    val animatedValue by animateFloatAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = 800),
        label = "value"
    )
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            
            Text(
                text = "${animatedValue.toInt()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = getScoreColor(value)
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // 进度条
        LinearProgressIndicator(
            progress = { animatedValue / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = getScoreColor(value),
            trackColor = Color.Gray.copy(alpha = 0.2f)
        )
    }
}

@Composable
private fun getScoreColor(score: Float): Color {
    return when {
        score >= 80 -> Color(0xFF22C55E)  // 绿色
        score >= 60 -> Color(0xFF10B981)   // 青绿
        score >= 40 -> Color(0xFFF59E0B)  // 黄色
        else -> Color(0xFFEF4444)          // 红色
    }
}

private fun generateSuggestions(result: ImageQualityAnalyzer.QualityResult): List<String> {
    val suggestions = mutableListOf<String>()
    
    if (result.overallScore < 60) {
        suggestions.add("建议重新生成图像或调整提示词")
    }
    
    if (result.sharpness < 50) {
        suggestions.add("提高清晰度：增加 Steps 或使用超分辨率处理")
    }
    
    if (result.brightness < 50) {
        suggestions.add("调整亮度：提示词中添加 \"well-lit\" 或 \"bright\"")
    }
    
    if (result.contrast < 50) {
        suggestions.add("增强对比度：提示词中添加 \"high contrast\" 或 \"dramatic lighting\"")
    }
    
    if (result.noiseLevel < 40) {
        suggestions.add("减少噪点：增加 Steps 或降低 CFG Scale")
    }
    
    if (result.colorfulness < 50) {
        suggestions.add("提升色彩：提示词中添加 \"vibrant colors\" 或 \"saturated\"")
    }
    
    if (result.compressionArtifacts < 50) {
        suggestions.add("保存为 PNG 格式以避免 JPEG 压缩伪影")
    }
    
    if (suggestions.isEmpty()) {
        suggestions.add("图像质量良好，无需特殊优化！")
    }
    
    return suggestions
}

// kotlinx.coroutines 需要导入
