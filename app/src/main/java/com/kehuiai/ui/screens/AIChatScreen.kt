package com.kehuiai.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kehuiai.service.AIModelManager
import kotlinx.coroutines.launch

/**
 * AI 对话界面 - 完整版
 * 支持多模型切换、流式输出、图像识别
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatScreen(
    onNavigateBack: () -> Unit,
    modelManager: AIModelManager? = null
) {
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf<AIModelManager.ModelInfo?>(null) }
    var showModelSelector by remember { mutableStateOf(false) }
    var streamingText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<Message>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    // 可用模型
    val availableModels = remember { modelManager?.getAvailableModels() ?: emptyList() }
    
    // 默认选择第一个模型
    LaunchedEffect(availableModels) {
        if (selectedModel == null && availableModels.isNotEmpty()) {
            selectedModel = availableModels.first()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            selectedModel?.name ?: "AI 对话",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            selectedModel?.provider ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 模型选择
                    IconButton(onClick = { showModelSelector = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "选择模型")
                    }
                    // 清空对话
                    IconButton(onClick = { 
                        messages.clear()
                        modelManager?.clearHistory()
                    }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "清空")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 消息列表
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 欢迎消息
                if (messages.isEmpty()) {
                    item {
                        WelcomeCard(
                            models = availableModels,
                            onModelSelect = { model ->
                                selectedModel = model
                                showModelSelector = false
                            }
                        )
                    }
                }
                
                // 消息列表
                items(messages) { message ->
                    MessageBubble(message = message)
                }
                
                // 流式输出
                if (streamingText.isNotEmpty()) {
                    item {
                        MessageBubble(
                            message = Message(
                                role = "assistant",
                                content = streamingText,
                                isStreaming = true
                            )
                        )
                    }
                }
                
                // 加载指示器
                if (isLoading && streamingText.isEmpty()) {
                    item {
                        ChatLoadingIndicator()
                    }
                }
            }
            
            // 输入区域
            ChatInputBar(
                inputText = inputText,
                onInputChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank() && !isLoading) {
                        val userMessage = Message(
                            role = "user",
                            content = inputText
                        )
                        messages.add(userMessage)
                        
                        isLoading = true
                        streamingText = ""
                        val currentInput = inputText
                        inputText = ""
                        focusManager.clearFocus()
                        
                        scope.launch {
                            modelManager?.sendMessageStream(
                                message = currentInput,
                                onChunk = { chunk ->
                                    streamingText += chunk
                                }
                            )?.collect { result ->
                                isLoading = false
                                result.onSuccess { response ->
                                    if (streamingText.isNotEmpty()) {
                                        messages.add(
                                            Message(
                                                role = "assistant",
                                                content = streamingText
                                            )
                                        )
                                    } else {
                                        messages.add(
                                            Message(
                                                role = "assistant",
                                                content = response.content
                                            )
                                        )
                                    }
                                    streamingText = ""
                                }.onFailure { error ->
                                    messages.add(
                                        Message(
                                            role = "assistant",
                                            content = "抱歉，发生了错误: ${error.message}",
                                            isError = true
                                        )
                                    )
                                }
                            } ?: run {
                                isLoading = false
                                // 模拟响应（无API时）
                                messages.add(
                                    Message(
                                        role = "assistant",
                                        content = "请先在设置中配置 API Key"
                                    )
                                )
                            }
                        }
                    }
                },
                isLoading = isLoading,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
    
    // 模型选择器
    if (showModelSelector) {
        ModelSelectorDialog(
            models = availableModels,
            selectedModel = selectedModel,
            onModelSelect = { model ->
                selectedModel = model
                showModelSelector = false
            },
            onDismiss = { showModelSelector = false }
        )
    }
    
    // 自动滚动
    LaunchedEffect(messages.size, streamingText) {
        if (messages.isNotEmpty() || streamingText.isNotEmpty()) {
            listState.animateScrollToItem(
                (messages.size + if (streamingText.isNotEmpty()) 1 else 0) - 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectorDialog(
    models: List<AIModelManager.ModelInfo>,
    selectedModel: AIModelManager.ModelInfo?,
    onModelSelect: (AIModelManager.ModelInfo) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    
    val tabs = listOf("全部", "对话", "视觉", "代码")
    
    val filteredModels = remember(models, searchQuery, selectedTab) {
        models.filter { model ->
            val matchesSearch = searchQuery.isEmpty() ||
                model.name.contains(searchQuery, ignoreCase = true) ||
                model.id.contains(searchQuery, ignoreCase = true)
            
            val matchesTab = when (selectedTab) {
                0 -> true
                1 -> !model.supportsVision && !model.id.contains("coder", ignoreCase = true)
                2 -> model.supportsVision
                3 -> model.id.contains("coder", ignoreCase = true)
                else -> true
            }
            
            matchesSearch && matchesTab
        }.groupBy { it.provider }
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "选择模型",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // 搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索模型...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 标签页
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 模型列表
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                filteredModels.forEach { (provider, providerModels) ->
                    item {
                        Text(
                            provider.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    items(providerModels) { model ->
                        ModelItem(
                            model = model,
                            isSelected = selectedModel?.id == model.id,
                            onClick = { onModelSelect(model) }
                        )
                    }
                    
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
fun ModelItem(
    model: AIModelManager.ModelInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    model.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    if (model.supportsVision) {
                        AssistChip(
                            onClick = {},
                            label = { Text("视觉", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                    if (model.id.contains("coder", ignoreCase = true)) {
                        AssistChip(
                            onClick = {},
                            label = { Text("代码", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
            }
            
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "已选择",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
fun WelcomeCard(
    models: List<AIModelManager.ModelInfo>,
    onModelSelect: (AIModelManager.ModelInfo) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.SmartToy,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                "👋 你好！我是可绘AI助手",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "我可以帮你：\n" +
                "• 回答各种问题\n" +
                "• 帮你写代码\n" +
                "• 分析图片内容\n" +
                "• 提供创作灵感",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                "已有 ${models.size} 个模型可用",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    val isUser = message.role == "user"
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser)
                    MaterialTheme.colorScheme.primary
                else if (message.isError)
                    MaterialTheme.colorScheme.errorContainer
                else
                    MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser)
                        MaterialTheme.colorScheme.onPrimary
                    else if (message.isError)
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer
                )
                
                if (message.isStreaming) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        repeat(3) { _ ->
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isUser)
                                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                                        else
                                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                                    )
                            )
                        }
                    }
                }
            }
        }
        
        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
fun ChatLoadingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Spacer(modifier = Modifier.width(44.dp))
        
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { _ ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                singleLine = false,
                maxLines = 4,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box {
                        if (inputText.isEmpty()) {
                            Text(
                                "输入消息...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                }
            )
            
            FilledIconButton(
                onClick = onSend,
                enabled = inputText.isNotBlank() && !isLoading,
                modifier = Modifier.size(48.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送"
                    )
                }
            }
        }
    }
}

// 数据类
data class Message(
    val role: String,
    val content: String,
    val isStreaming: Boolean = false,
    val isError: Boolean = false
)
