package com.kehuiai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kehuiai.service.AIChatService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import androidx.navigation.NavController

/**
 * AI 对话设置界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISettingsScreen(
    navController: NavController? = null,
    onNavigateBack: (() -> Unit)? = null,
    chatService: AIChatService? = null
) {
    var selectedProvider by remember { mutableStateOf(AIChatService.PROVIDER_QWEN) }
    var apiKey by remember { mutableStateOf("") }
    var customUrl by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf("qwen2.5-7b-instruct") }
    var showApiKey by remember { mutableStateOf(false) }
    
    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    fun goBack() {
        onNavigateBack?.invoke() ?: navController?.popBackStack()
    }
    
    val providers = listOf(
        ProviderInfo(AIChatService.PROVIDER_QWEN, "通义千问", "阿里云", Icons.Default.Cloud),
        ProviderInfo(AIChatService.PROVIDER_DEEPSEEK, "DeepSeek", "深度求索", Icons.Default.Psychology),
        ProviderInfo(AIChatService.PROVIDER_OPENAI, "OpenAI", "ChatGPT", Icons.Default.SmartToy),
        ProviderInfo(AIChatService.PROVIDER_ANTHROPIC, "Anthropic", "Claude", Icons.Default.Psychology),
        ProviderInfo(AIChatService.PROVIDER_GEMINI, "Gemini", "Google", Icons.Default.AutoAwesome),
        ProviderInfo(AIChatService.PROVIDER_CUSTOM, "自定义", "自定义 API", Icons.Default.Settings)
    )
    
    val models = chatService?.getAvailableModels() ?: emptyList()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 对话设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { goBack() }) {
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
            // 提供商选择
            item {
                Text(
                    "选择 API 提供商",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            items(providers) { provider ->
                ProviderCard(
                    provider = provider,
                    isSelected = selectedProvider == provider.id,
                    onClick = { selectedProvider = provider.id }
                )
            }
            
            // API Key 输入
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "API Key",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            item {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("输入 API Key") },
                    placeholder = { Text("sk-...") },
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showApiKey) "隐藏" else "显示"
                            )
                        }
                    },
                    singleLine = true
                )
            }
            
            // 自定义 URL（仅自定义提供商）
            if (selectedProvider == AIChatService.PROVIDER_CUSTOM) {
                item {
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("自定义 API 地址") },
                        placeholder = { Text("https://api.example.com/v1") },
                        singleLine = true
                    )
                }
            }
            
            // 模型选择
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "选择模型",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            items(models) { model ->
                ModelCard(
                    model = model,
                    isSelected = selectedModel == model.name,
                    onClick = { selectedModel = model.name }
                )
            }
            
            // 测试按钮
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        chatService?.setProvider(selectedProvider)
                        chatService?.setApiKey(apiKey)
                        if (selectedProvider == AIChatService.PROVIDER_CUSTOM) {
                            chatService?.setBaseUrl(customUrl)
                        }
                        chatService?.setModel(selectedModel)
                        
                        isTesting = true
                        testStatus = null
                        
                        // 测试发送
                        coroutineScope.launch {
                            chatService?.sendMessage("你好，测试消息")?.collectLatest { response ->
                                when (response) {
                                    is com.kehuiai.service.ChatResponse.Status -> {
                                        testStatus = response.message
                                    }
                                    is com.kehuiai.service.ChatResponse.Completed -> {
                                        testStatus = "✓ 测试成功！"
                                        isTesting = false
                                    }
                                    is com.kehuiai.service.ChatResponse.Error -> {
                                        testStatus = "✗ ${response.message}"
                                        isTesting = false
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = apiKey.isNotBlank() && !isTesting
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isTesting) "测试中..." else "测试连接")
                }
            }
            
            // 测试结果
            testStatus?.let { status ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (status.startsWith("✓"))
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = status,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            // 保存按钮
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        // 保存配置
                        onNavigateBack?.invoke()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("保存设置")
                }
            }
        }
    }
}

@Composable
fun ProviderCard(
    provider: ProviderInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) null else CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    provider.icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = provider.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "已选择",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ModelCard(
    model: com.kehuiai.service.ModelInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
        }
    }
}

data class ProviderInfo(
    val id: String,
    val name: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
