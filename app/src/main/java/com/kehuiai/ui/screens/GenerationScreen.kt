package com.kehuiai.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.kehuiai.data.model.*
import com.kehuiai.data.model.GenerationProgress
import com.kehuiai.data.repository.GenerationRepository

/**
 * Image Generation Screen with full controls
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerationScreen(
    repository: GenerationRepository
) {
    var positivePrompt by remember { mutableStateOf("") }
    var negativePrompt by remember { mutableStateOf("") }
    var selectedWidth by remember { mutableStateOf(512) }
    var selectedHeight by remember { mutableStateOf(512) }
    var selectedAspectRatio by remember { mutableStateOf(AspectRatio.SQUARE) }
    var steps by remember { mutableStateOf(20) }
    var guidanceScale by remember { mutableStateOf(7.5f) }
    var seed by remember { mutableStateOf("") }
    var selectedScheduler by remember { mutableStateOf(SchedulerType.EULER) }
    var strength by remember { mutableStateOf(0.75f) }
    var batchSize by remember { mutableStateOf(1) }
    var clipSkip by remember { mutableStateOf(0) }
    
    var isGenerating by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var generatedImage by remember { mutableStateOf<Bitmap?>(null) }
    
    var showAdvancedSettings by remember { mutableStateOf(false) }
    var showSchedulerSheet by remember { mutableStateOf(false) }
    var showResolutionSheet by remember { mutableStateOf(false) }
    var showRatioSheet by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "AI 图像生成",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { showAdvancedSettings = !showAdvancedSettings }) {
                        Icon(
                            if (showAdvancedSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
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
        ) {
            // Preview Area
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isGenerating) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "生成中... ${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "步 ${(progress * steps).toInt()} / $steps",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    } else if (generatedImage != null) {
                        Image(
                            bitmap = generatedImage!!.asImageBitmap(),
                            contentDescription = "生成的图像",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "输入提示词开始生成",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
            
            // Positive Prompt
            OutlinedTextField(
                value = positivePrompt,
                onValueChange = { positivePrompt = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                label = { Text("正向提示词") },
                placeholder = { Text("描述你想要的图像...") },
                minLines = 2,
                maxLines = 4,
                leadingIcon = {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF4CAF50))
                },
                trailingIcon = {
                    if (positivePrompt.isNotEmpty()) {
                        IconButton(onClick = { positivePrompt = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除")
                        }
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Negative Prompt
            OutlinedTextField(
                value = negativePrompt,
                onValueChange = { negativePrompt = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                label = { Text("负向提示词") },
                placeholder = { Text("你不想要的内容...") },
                minLines = 1,
                maxLines = 2,
                leadingIcon = {
                    Icon(Icons.Default.Remove, contentDescription = null, tint = Color(0xFFF44336))
                },
                trailingIcon = {
                    if (negativePrompt.isNotEmpty()) {
                        IconButton(onClick = { negativePrompt = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除")
                        }
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Quick Settings Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Resolution
                QuickSettingCard(
                    title = "分辨率",
                    value = "${selectedWidth}x$selectedHeight",
                    icon = Icons.Outlined.AspectRatio,
                    onClick = { showResolutionSheet = true },
                    modifier = Modifier.weight(1f)
                )
                
                // Aspect Ratio
                QuickSettingCard(
                    title = "比例",
                    value = selectedAspectRatio.displayName,
                    icon = Icons.Outlined.Crop,
                    onClick = { showRatioSheet = true },
                    modifier = Modifier.weight(1f)
                )
                
                // Scheduler
                QuickSettingCard(
                    title = "调度器",
                    value = selectedScheduler.displayName,
                    icon = Icons.Outlined.SettingsSuggest,
                    onClick = { showSchedulerSheet = true },
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Steps Slider
            SettingSliderRow(
                title = "采样步数",
                value = steps,
                valueRange = 1..50,
                icon = Icons.Outlined.Timeline,
                onValueChange = { steps = it }
            )
            
            // Guidance Scale Slider
            SettingSliderRow(
                title = "引导强度",
                value = guidanceScale,
                valueRange = 1f..20f,
                icon = Icons.Outlined.Balance,
                formatValue = { String.format("%.1f", it) },
                onValueChange = { guidanceScale = it }
            )
            
            // Advanced Settings
            if (showAdvancedSettings) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "高级设置",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Seed
                        OutlinedTextField(
                            value = seed,
                            onValueChange = { seed = it },
                            label = { Text("随机种子 (-1 为随机)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Strength (for img2img)
                        SettingSliderRow(
                            title = "重绘强度",
                            value = strength,
                            valueRange = 0.1f..1f,
                            icon = Icons.Outlined.Tune,
                            formatValue = { String.format("%.2f", it) },
                            onValueChange = { strength = it }
                        )
                        
                        // Clip Skip
                        SettingSliderRow(
                            title = "Clip Skip",
                            value = clipSkip,
                            valueRange = 0..3,
                            icon = Icons.Outlined.SkipNext,
                            onValueChange = { clipSkip = it }
                        )
                        
                        // Batch Size
                        SettingSliderRow(
                            title = "批量数量",
                            value = batchSize,
                            valueRange = 1..4,
                            icon = Icons.Outlined.Layers,
                            onValueChange = { batchSize = it }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Generate Button
            Button(
                onClick = {
                    if (!isGenerating && positivePrompt.isNotBlank()) {
                        isGenerating = true
                        progress = 0f
                        
                        // Use repository for actual generation
                        kotlinx.coroutines.MainScope().launch {
                            repository.generateImage(GenerationParams(
                                positivePrompt = positivePrompt,
                                negativePrompt = negativePrompt,
                                width = selectedWidth,
                                height = selectedHeight,
                                steps = steps,
                                guidanceScale = guidanceScale,
                                seed = if (seed.isNotBlank()) seed.toLongOrNull() ?: -1L else -1L,
                                scheduler = selectedScheduler)
                            ).collect { progressState ->
                                when (val s = progressState) {
                                    is GenerationProgress.Status -> {
                                        // Show status message
                                    }
                                    is GenerationProgress.Progress -> {
                                        progress = s.percent / 100f
                                    }
                                    is GenerationProgress.Completed -> {
                                        generatedImage = null // TODO: Load bitmap from path: ${s.imagePath}
                                        isGenerating = false
                                    }
                                    is GenerationProgress.Error -> {
                                        isGenerating = false
                                        // Show error
                                    }
                                    is GenerationProgress.BatchProgress -> {
                                        // Batch progress
                                    }
                                    is GenerationProgress.ControlNetProgress -> {
                                        // ControlNet progress
                                    }
                                    is GenerationProgress.HiresFixProgress -> {
                                        // Hires.fix progress
                                    }
                                    is GenerationProgress.RefinerProgress -> {
                                        // Refiner progress
                                    }
                                    else -> { /* Unknown progress type */ }
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(56.dp),
                enabled = positivePrompt.isNotBlank() && !isGenerating,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    if (isGenerating) Icons.Default.HourglassTop else Icons.Default.AutoAwesome,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isGenerating) "生成中..." else "开始生成",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Quick Prompt Suggestions
            Text(
                "快速提示",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val quickPrompts = listOf(
                    "风景画", "肖像", "动漫", "写实", 
                    "抽象", "城市", "自然", "科技"
                )
                items(quickPrompts) { prompt ->
                    SuggestionChip(
                        onClick = { 
                            positivePrompt = if (positivePrompt.isBlank()) prompt 
                            else "$positivePrompt, $prompt" 
                        },
                        label = { Text(prompt) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
        
        // Scheduler Bottom Sheet
        if (showSchedulerSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSchedulerSheet = false }
            ) {
                SchedulerSelectionContent(
                    selectedScheduler = selectedScheduler,
                    onSelect = { 
                        selectedScheduler = it
                        showSchedulerSheet = false
                    }
                )
            }
        }
        
        // Resolution Bottom Sheet
        if (showResolutionSheet) {
            ModalBottomSheet(
                onDismissRequest = { showResolutionSheet = false }
            ) {
                ResolutionSelectionContent(
                    selectedWidth = selectedWidth,
                    selectedHeight = selectedHeight,
                    onSelect = { w, h ->
                        selectedWidth = w
                        selectedHeight = h
                        showResolutionSheet = false
                    }
                )
            }
        }
        
        // Aspect Ratio Bottom Sheet
        if (showRatioSheet) {
            ModalBottomSheet(
                onDismissRequest = { showRatioSheet = false }
            ) {
                AspectRatioSelectionContent(
                    selectedRatio = selectedAspectRatio,
                    onSelect = { ratio ->
                        selectedAspectRatio = ratio
                        selectedWidth = ratio.width
                        selectedHeight = ratio.height
                        showRatioSheet = false
                    }
                )
            }
        }
    }
}

@Composable
private fun QuickSettingCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun SettingSliderRow(
    title: String,
    value: Int,
    valueRange: IntRange,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(80.dp)
        )
        
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = valueRange.last - valueRange.first - 1,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            "$value",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun SettingSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    formatValue: (Float) -> String = { String.format("%.1f", it) },
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(80.dp)
        )
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            formatValue(value),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun SchedulerSelectionContent(
    selectedScheduler: SchedulerType,
    onSelect: (SchedulerType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            "选择调度器",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        SchedulerType.entries.toList().forEach { scheduler ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSelect(scheduler) },
                colors = CardDefaults.cardColors(
                    containerColor = if (scheduler == selectedScheduler) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        scheduler.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (scheduler == selectedScheduler) FontWeight.Bold else FontWeight.Normal
                    )
                    
                    if (scheduler == selectedScheduler) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ResolutionSelectionContent(
    selectedWidth: Int,
    selectedHeight: Int,
    onSelect: (Int, Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            "选择分辨率",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Resolution.entries.toList().chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { res ->
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelect(res.width, res.height) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (res.width == selectedWidth && res.height == selectedHeight)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                res.displayName,
                                fontWeight = if (res.width == selectedWidth && res.height == selectedHeight) 
                                    FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun AspectRatioSelectionContent(
    selectedRatio: AspectRatio,
    onSelect: (AspectRatio) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            "选择比例",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        AspectRatio.entries.toList().forEach { ratio ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSelect(ratio) },
                colors = CardDefaults.cardColors(
                    containerColor = if (ratio == selectedRatio)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            ratio.displayName,
                            fontWeight = if (ratio == selectedRatio) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            "${ratio.width}x${ratio.height}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    
                    if (ratio == selectedRatio) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}
