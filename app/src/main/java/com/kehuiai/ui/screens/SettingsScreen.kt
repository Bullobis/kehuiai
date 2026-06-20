package com.kehuiai.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.kehuiai.navigation.Screen
import com.kehuiai.service.SettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    navController: NavController? = null
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    
    val safeMode by settingsManager.safeMode.collectAsState()
    val dynamicColor by settingsManager.dynamicColor.collectAsState()
    val darkMode by settingsManager.darkMode.collectAsState()
    val listenOnAll by settingsManager.listenOnAllAddresses.collectAsState()
    
    var storageSize by remember { mutableLongStateOf(0L) }
    var cacheSize by remember { mutableLongStateOf(0L) }
    
    LaunchedEffect(Unit) {
        storageSize = settingsManager.getStorageSize(context)
        cacheSize = settingsManager.getCacheSize(context)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚙️ 设置", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 安全模式
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🛡️ 安全模式", fontWeight = FontWeight.Bold)
                            Text(
                                "启用内容安全检查，防止生成不当内容",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = safeMode,
                            onCheckedChange = { settingsManager.setSafeMode(it) }
                        )
                    }
                }
            }
            
            // 外观设置
            item {
                Text(
                    "🎨 外观",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("动态颜色")
                            Switch(
                                checked = dynamicColor,
                                onCheckedChange = { settingsManager.setDynamicColor(it) }
                            )
                        }
                        
                        HorizontalDivider()
                        
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("深色模式", fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    0 to "跟随系统",
                                    1 to "浅色",
                                    2 to "深色"
                                ).forEach { (mode, label) ->
                                    FilterChip(
                                        selected = darkMode == mode,
                                        onClick = { settingsManager.setDarkMode(mode) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // 快捷入口
            item {
                Text(
                    "🚀 快捷入口",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        // AI 对话
                        ListItem(
                            headlineContent = { Text("💬 AI 对话") },
                            supportingContent = { Text("与 AI 助手聊天，获取创作灵感") },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.Chat, null) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        HorizontalDivider()
                        
                        // 提示词助手
                        ListItem(
                            headlineContent = { Text("✨ 提示词助手") },
                            supportingContent = { Text("优化和翻译提示词") },
                            leadingContent = { Icon(Icons.Default.AutoFixHigh, null) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        HorizontalDivider()
                        
                        // 画廊
                        ListItem(
                            headlineContent = { Text("🖼️ 提示词画廊") },
                            supportingContent = { Text("浏览和收藏优秀提示词") },
                            leadingContent = { Icon(Icons.Default.PhotoLibrary, null) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        HorizontalDivider()
                        
                        // 使用统计
                        ListItem(
                            headlineContent = { Text("📊 使用统计") },
                            supportingContent = { Text("查看生成记录和成就") },
                            leadingContent = { Icon(Icons.Default.BarChart, null) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            // AI 设置
            item {
                Text(
                    "🤖 AI 对话设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("AI 提供商", fontWeight = FontWeight.Bold)
                        Text(
                            "当前: 通义千问 (qwen2.5-7b-instruct)",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { navController?.navigate(Screen.AISettings.route) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Settings, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("配置 AI 设置")
                        }
                    }
                }
            }
            
            // 网络设置
            item {
                Text(
                    "🌐 网络",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("允许局域网访问", fontWeight = FontWeight.Bold)
                            Text(
                                "允许同一网络下的其他设备访问",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = listenOnAll,
                            onCheckedChange = { settingsManager.setListenOnAllAddresses(it) }
                        )
                    }
                }
            }
            
            // 存储
            item {
                Text(
                    "💾 存储",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("应用存储")
                            Text(formatSize(storageSize))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("缓存")
                            Text(formatSize(cacheSize))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                settingsManager.clearCache(context)
                                cacheSize = 0
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Delete, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("清除缓存")
                        }
                    }
                }
            }
            
            // 诊断工具
            item {
                Text(
                    "🔧 诊断工具",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController?.navigate(Screen.Diagnostics.route) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "🔧 诊断工具",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "查看日志、导出报告、排查问题",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            
            // 关于
            item {
                Text(
                    "ℹ️ 关于",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController?.navigate(Screen.About.route) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🎨 可绘AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("版本 3.6.5", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "内置高性能图像生成引擎",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "开源地址: github.com/Bullobis/kehuiai",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "👆 点击查看完整关于页面",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024))
        bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
