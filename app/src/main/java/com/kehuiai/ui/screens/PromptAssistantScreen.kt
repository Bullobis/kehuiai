package com.kehuiai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * 提示词助手界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptAssistantScreen(
    onNavigateBack: () -> Unit,
    onPromptGenerated: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var generatedPrompt by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableStateOf<String?>(null) }
    var selectedQuality by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    
    val styles = listOf(
        "真实照片", "动漫风格", "油画", "水彩", "素描",
        "赛博朋克", "幻想风格", "极简主义", "复古风格", "未来主义"
    )
    
    val qualities = listOf(
        "4K超清", "8K超清", "电影级", "专业摄影", "工作室级别"
    )
    
    val templates = listOf(
        PromptTemplate("人像摄影", "一位精致的肖像照，柔和的光线，背景虚化，专业摄影风格"),
        PromptTemplate("风景摄影", "壮丽的自然风景，Golden Hour光线，细节丰富，8K分辨率"),
        PromptTemplate("建筑摄影", "现代建筑摄影，干净线条，完美构图，建筑摄影风格"),
        PromptTemplate("动漫女孩", "精美的动漫角色，柔和的色彩，细腻的细节，动漫风格"),
        PromptTemplate("科幻场景", "未来科技感场景，赛博朋克风格，霓虹灯光，震撼视角")
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("提示词助手", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 输入描述
            item {
                Text(
                    "📝 描述你想要的内容",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            item {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    placeholder = { Text("例如：一位穿着汉服的少女站在樱花树下...") },
                    maxLines = 5
                )
            }
            
            // 风格选择
            item {
                Text(
                    "🎨 选择风格",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    styles.forEach { style ->
                        FilterChip(
                            selected = selectedStyle == style,
                            onClick = { selectedStyle = if (selectedStyle == style) null else style },
                            label = { Text(style) },
                            leadingIcon = if (selectedStyle == style) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }
            
            // 质量选择
            item {
                Text(
                    "✨ 选择质量",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    qualities.forEach { quality ->
                        FilterChip(
                            selected = selectedQuality == quality,
                            onClick = { selectedQuality = if (selectedQuality == quality) null else quality },
                            label = { Text(quality) },
                            leadingIcon = if (selectedQuality == quality) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }
            
            // 生成按钮
            item {
                Button(
                    onClick = {
                        isGenerating = true
                        // 简单的提示词生成逻辑
                        generatedPrompt = buildString {
                            append(inputText)
                            if (selectedStyle != null) {
                                append("，")
                                append(selectedStyle)
                                append("风格")
                            }
                            if (selectedQuality != null) {
                                append("，")
                                append(selectedQuality)
                            }
                            append("，细节丰富，光影效果好，高质量")
                        }
                        isGenerating = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = inputText.isNotBlank() && !isGenerating
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("生成提示词")
                }
            }
            
            // 生成的提示词
            if (generatedPrompt.isNotBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "📋 生成的提示词",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = {
                                    // 复制到剪贴板
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = generatedPrompt,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = { onPromptGenerated(generatedPrompt) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("使用此提示词")
                            }
                        }
                    }
                }
            }
            
            // 模板
            item {
                Text(
                    "📚 提示词模板",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            items(templates) { template ->
                PromptTemplateCard(
                    template = template,
                    onClick = {
                        inputText = template.description.split("，")[0]
                    }
                )
            }
        }
    }
}

@Composable
fun PromptTemplateCard(
    template: PromptTemplate,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = template.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

data class PromptTemplate(
    val name: String,
    val description: String
)
