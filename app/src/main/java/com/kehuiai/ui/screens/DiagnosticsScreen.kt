package com.kehuiai.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.kehuiai.service.DiagnosticsService
import com.kehuiai.service.DiagnosticsService.LogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 可绘AI v3.6.6 诊断界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    navController: NavController? = null
) {
    val context = LocalContext.current
    val diagnostics = remember { DiagnosticsService.getInstance(context) }
    val logs by diagnostics.logs.collectAsState()
    
    var showLogDetails by remember { mutableStateOf<LogEntry?>(null) }
    var showReportDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("🔧 诊断工具", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        showReportDialog = true 
                    }) {
                        Icon(Icons.Default.Description, "生成报告")
                    }
                    IconButton(onClick = { 
                        diagnostics.clearLogs()
                        Toast.makeText(context, "日志已清除", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Delete, "清除日志")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 诊断信息卡片
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "📊 系统信息",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        SystemInfoRow("Android 版本", android.os.Build.VERSION.RELEASE)
                        SystemInfoRow("SDK 版本", android.os.Build.VERSION.SDK_INT.toString())
                        SystemInfoRow("设备", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                        SystemInfoRow("日志数量", "${logs.size} 条")
                    }
                }
            }
            
            // 快速操作
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "⚡ 快速操作",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { 
                                    val report = diagnostics.generateReport()
                                    showReportDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Article, null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("报告")
                            }
                            
                            Button(
                                onClick = { 
                                    try {
                                        val file = diagnostics.exportLogs()
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "分享诊断报告"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Share, null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("导出")
                            }
                        }
                    }
                }
            }
            
            // 错误列表
            val errors = logs.filter { it.level == DiagnosticsService.LogLevel.ERROR }
            if (errors.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
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
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "⚠️ 错误 (${errors.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            
            // 日志列表
            item {
                Text(
                    "📋 日志记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            items(logs.reversed()) { entry ->
                LogEntryItem(
                    entry = entry,
                    onClick = { showLogDetails = entry }
                )
            }
            
            if (logs.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "暂无日志",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "运行应用后这里会显示日志",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
    
    // 日志详情对话框
    showLogDetails?.let { entry ->
        AlertDialog(
            onDismissRequest = { showLogDetails = null },
            title = { Text("日志详情") },
            text = {
                Column {
                    Text(
                        text = entry.message,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    entry.throwable?.let { t ->
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "异常: ${t.javaClass.simpleName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = t.stackTraceToString().take(500),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogDetails = null }) {
                    Text("关闭")
                }
            }
        )
    }
    
    // 报告对话框
    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("📄 诊断报告") },
            text = {
                LazyColumn {
                    item {
                        Text(
                            text = diagnostics.generateReport(),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

@Composable
private fun SystemInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LogEntryItem(
    entry: LogEntry,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val time = dateFormat.format(Date(entry.timestamp))
    
    val levelColor = when (entry.level) {
        DiagnosticsService.LogLevel.VERBOSE -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        DiagnosticsService.LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        DiagnosticsService.LogLevel.INFO -> MaterialTheme.colorScheme.primary
        DiagnosticsService.LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
        DiagnosticsService.LogLevel.ERROR -> MaterialTheme.colorScheme.error
    }
    
    val levelText = when (entry.level) {
        DiagnosticsService.LogLevel.VERBOSE -> "V"
        DiagnosticsService.LogLevel.DEBUG -> "D"
        DiagnosticsService.LogLevel.INFO -> "I"
        DiagnosticsService.LogLevel.WARN -> "W"
        DiagnosticsService.LogLevel.ERROR -> "E"
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (entry.level) {
                DiagnosticsService.LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                DiagnosticsService.LogLevel.WARN -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = levelColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = levelText,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = levelColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.tag,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = entry.message,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
                if (entry.throwable != null) {
                    Text(
                        text = "⚠️ ${entry.throwable.javaClass.simpleName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
