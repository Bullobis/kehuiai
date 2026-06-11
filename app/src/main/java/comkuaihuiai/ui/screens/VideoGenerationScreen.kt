package comkuaihuiai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import comkuaihuiai.service.video.VideoGenerationService

/**
 * 可绘AI v3.0 - 视频生成界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoGenerationScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    
    // 状态
    var prompt by remember { mutableStateOf("") }
    var negativePrompt by remember { mutableStateOf("") }
    var duration by remember { mutableIntStateOf(4) }
    var seed by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    
    val modes = listOf("文生视频", "图生视频", "视频转视频")
    var selectedMode by remember { mutableIntStateOf(0) }
    
    val styles = listOf("真实", "动漫", "电影", "艺术")
    var selectedStyle by remember { mutableIntStateOf(0) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎬 视频生成", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
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
            // 模式选择
            item {
                Text("生成模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    modes.forEachIndexed { idx, name ->
                        FilterChip(
                            selected = selectedMode == idx,
                            onClick = { selectedMode = idx },
                            label = { Text(name) }
                        )
                    }
                }
            }
            
            // 提示词
            item {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("提示词") },
                    placeholder = { Text("描述你想要生成的视频内容...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3, maxLines = 5
                )
            }
            
            // 负向提示词
            item {
                OutlinedTextField(
                    value = negativePrompt,
                    onValueChange = { negativePrompt = it },
                    label = { Text("负向提示词") },
                    placeholder = { Text("不想出现在视频中的内容...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2, maxLines = 3
                )
            }
            
            // 风格选择
            item {
                Text("视频风格", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    styles.forEachIndexed { idx, name ->
                        FilterChip(
                            selected = selectedStyle == idx,
                            onClick = { selectedStyle = idx },
                            label = { Text(name) }
                        )
                    }
                }
            }
            
            // 时长
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("视频时长", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${duration}秒", color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = duration.toFloat(),
                    onValueChange = { duration = it.toInt() },
                    valueRange = 2f..16f,
                    steps = 13
                )
            }
            
            // Seed
            item {
                OutlinedTextField(
                    value = seed,
                    onValueChange = { seed = it.filter { c -> c.isDigit() } },
                    label = { Text("随机种子（可选）") },
                    placeholder = { Text("留空则随机") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            
            // 进度条
            if (isGenerating) {
                item {
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "生成中: ${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            
            // 生成按钮
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        isGenerating = true
                        // TODO: 调用视频生成服务
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = prompt.isNotBlank() && !isGenerating
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("生成中...")
                    } else {
                        Text("🎬 开始生成视频")
                    }
                }
            }
        }
    }
}
