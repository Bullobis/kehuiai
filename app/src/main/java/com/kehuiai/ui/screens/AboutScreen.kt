package com.kehuiai.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.kehuiai.navigation.Screen

/**
 * 可绘AI v3.6.6 关于页面
 * 包含作者信息、更新日志、用户协议
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController? = null
) {
    val context = LocalContext.current
    
    var showUpdateLog by remember { mutableStateOf(false) }
    var showUserAgreement by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ℹ️ 关于", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 应用信息卡片
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
                        // 应用图标 (使用 emoji 代替)
                        Text(
                            text = "🎨",
                            fontSize = 64.sp
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "可绘AI",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = "KehuiAI",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 版本信息
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "版本 3.6.5",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "内置高性能图像生成引擎",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        
                        Text(
                            text = "支持 Stable Diffusion / SDXL / Flux",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            
            // 作者信息
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "👤 作者信息",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        InfoRow("开发者", "陆浩铭")
                        InfoRow("开发者邮箱", "2671369836@qq.com")
                        InfoRow("GitHub", "github.com/Bullobis/kehuiai")
                    }
                }
            }
            
            // 开发信息
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "🛠️ 开发信息",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        InfoRow("开发日期", "2026年6月")
                        InfoRow("技术栈", "Kotlin + Jetpack Compose")
                        InfoRow("NDK", "C++ 原生推理引擎")
                        InfoRow("许可证", "Apache License 2.0")
                    }
                }
            }
            
            // 项目地址
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "🔗 项目地址",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // GitHub 仓库
                        LinkItem(
                            title = "GitHub 仓库",
                            url = "https://github.com/Bullobis/kehuiai",
                            icon = Icons.Default.Source,
                            context = context
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // GitHub 仓库
                        LinkItem(
                            title = "GitHub 仓库",
                            url = "https://github.com/Bullobis/kehuiai",
                            icon = Icons.Default.Code,
                            context = context
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // GitHub Issues
                        LinkItem(
                            title = "问题反馈",
                            url = "https://github.com/Bullobis/kehuiai/issues",
                            icon = Icons.Default.BugReport,
                            context = context
                        )
                    }
                }
            }
            
            // 功能特性
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "⭐ 功能特性",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        val features = listOf(
                            "🖼️ 图像生成 (SD/SDXL/Flux)",
                            "🎬 视频生成 (文生视频/图生视频)",
                            "📦 模型管理 (下载/切换/收藏)",
                            "⚡ NPU 加速 (骁龙芯片)",
                            "🔧 本地推理 (无需联网)",
                            "🎨 提示词助手 (优化/翻译)",
                            "📚 提示词画廊 (灵感广场)",
                            "🛡️ 内容安全过滤"
                        )
                        
                        features.chunked(2).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { feature ->
                                    Text(
                                        text = feature,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (row.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
            
            // 更新日志
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showUpdateLog = !showUpdateLog },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Update,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "📋 更新日志",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            if (showUpdateLog) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }
                }
            }
            
            if (showUpdateLog) {
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
                            UpdateLogItem(
                                version = "v3.6.5",
                                date = "2026-06-19",
                                changes = listOf(
                                    "✅ 代码质量优化，消除所有编译警告",
                                    "➕ 新增 AnimationService (10种动画效果)",
                                    "➕ 新增 BackgroundRemovalService (6种背景移除)",
                                    "➕ 新增 BatchProcessingService (8种批量处理)",
                                    "➕ 新增 ImageStitchingService (7种拼接模式)"
                                )
                            )
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            
                            UpdateLogItem(
                                version = "v3.6.4",
                                date = "2026-06-19",
                                changes = listOf(
                                    "➕ AI对话服务 (通义千问/DeepSeek/OpenAI等)",
                                    "➕ ColorGradingService 色彩分级 (12种预设)",
                                    "➕ ImageCompositionService 图像合成",
                                    "➕ VideoGenerationService 视频生成",
                                    "➕ StyleTransferService 30+种艺术风格"
                                )
                            )
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            
                            UpdateLogItem(
                                version = "v3.6.3",
                                date = "2026-06-18",
                                changes = listOf(
                                    "🔧 修复 JNI 函数包名错误导致闪退",
                                    "🔧 修复 AIChatService 云端API调用",
                                    "🔧 修复 ImageToolbox 图像处理"
                                )
                            )
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            
                            UpdateLogItem(
                                version = "v3.6.1",
                                date = "2026-06-17",
                                changes = listOf(
                                    "🔧 修复 AudioEngine 资源泄漏",
                                    "🔧 修复 UI 图标准确引用",
                                    "🔧 修复未使用参数/变量警告"
                                )
                            )
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            
                            UpdateLogItem(
                                version = "v3.6.0",
                                date = "2026-06-14",
                                changes = listOf(
                                    "🆕 热模型切换引擎 (HotSwapEngine)",
                                    "🆕 智能缓存系统 (SmartCacheManager)",
                                    "🆕 实时性能监控 (PerformanceMonitor)",
                                    "🆕 安全过滤系统 (SafetyFilter)",
                                    "🆕 历史风格学习 (StyleLearningManager)"
                                )
                            )
                        }
                    }
                }
            }
            
            // 用户协议
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showUserAgreement = !showUserAgreement },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "📜 用户协议",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            if (showUserAgreement) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }
                }
            }
            
            if (showUserAgreement) {
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
                                "可绘AI 用户服务协议",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                """
                                【可绘AI 用户服务协议】
                                
                                欢迎您使用可绘AI应用！
                                
                                一、服务说明
                                1.1 可绘AI（以下简称"本应用"）是一款本地AI图像生成应用。
                                1.2 本应用利用设备本地算力进行AI推理，无需联网即可使用。
                                1.3 本应用尊重用户隐私，不会上传用户数据到任何服务器。
                                
                                二、知识产权
                                2.1 本应用的代码、界面设计、图标等知识产权归开发者所有。
                                2.2 开发者信息：陆浩铭，GitHub: Bullobis
                                2.3 用户使用本应用生成的图像版权归用户所有。
                                2.4 请勿将本应用用于任何非法用途。
                                
                                三、使用规范
                                3.1 您同意不使用本应用生成任何违法、有害、侵权的内容。
                                3.2 禁止使用本应用生成色情、暴力、歧视性内容。
                                3.3 禁止反向工程、破解本应用或侵犯开发者权益。
                                3.4 用户应对其生成的内容负完全责任。
                                
                                四、免责声明
                                4.1 本应用按"现状"提供，不提供任何明示或暗示的保证。
                                4.2 开发者不对使用本应用造成的任何直接或间接损失负责。
                                4.3 开发者有权随时修改或终止本应用服务。
                                
                                五、隐私保护
                                5.1 本应用不会收集、存储或传输您的个人数据。
                                5.2 您在本应用中创建的图像存储在本地设备上。
                                5.3 本应用不会访问您设备上的其他数据。
                                
                                六、服务变更
                                6.1 开发者可随时修改本应用的任何功能。
                                6.2 如本应用停止维护，将提前通知用户。
                                6.3 本协议如有更新，将通过应用内公告通知。
                                
                                七、联系开发者
                                7.1 邮箱：2671369836@qq.com
                                7.2 GitHub：https://github.com/Bullobis/kehuiai
                                7.3 问题反馈：https://github.com/Bullobis/kehuiai/issues
                                
                                最后更新：2026年6月19日
                                """.trimIndent(),
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
            
            // 隐私政策
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPrivacyPolicy = !showPrivacyPolicy },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "🔒 隐私政策",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            if (showPrivacyPolicy) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }
                }
            }
            
            if (showPrivacyPolicy) {
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
                                "可绘AI 隐私政策",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                """
                                【可绘AI 隐私政策】
                                
                                我们非常重视您的隐私保护。
                                
                                一、数据收集
                                1.1 本应用不会收集任何个人信息。
                                1.2 本应用不会追踪您的使用行为。
                                1.3 本应用不会向任何第三方分享数据。
                                
                                二、本地存储
                                2.1 所有生成的数据都存储在您的设备本地。
                                2.2 应用缓存可以在设置中手动清除。
                                2.3 卸载应用会删除所有相关数据。
                                
                                三、权限使用
                                3.1 存储权限：用于保存生成的图像
                                3.2 网络权限：用于下载AI模型（可选）
                                3.3 相机权限：用于扫描二维码（可选）
                                
                                四、安全性
                                4.1 本应用使用行业标准的安全实践。
                                4.2 您的数据不会通过不安全的渠道传输。
                                4.3 我们持续更新应用以修复安全漏洞。
                                
                                五、联系我们
                                如您对隐私政策有任何疑问，请联系：
                                邮箱：2671369836@qq.com
                                
                                最后更新：2026年6月19日
                                """.trimIndent(),
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
            
            // 致谢
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "💖 致谢",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            """
                            本项目的开发离不开以下开源项目的支持：
                            
                            • Stable Diffusion - Stability AI
                            • MNN - Alibaba (推理引擎)
                            • Jetpack Compose - Google
                            • Material Design 3
                            • OpenCV - Intel
                            • TensorFlow Lite - Google
                            
                            同时感谢所有参与测试和反馈的用户！
                            """.trimIndent(),
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
            
            // 底部信息
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "可绘AI v3.6.5",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        "Made with ❤️ by 陆浩铭",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        "© 2026 All Rights Reserved",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LinkItem(
    title: String,
    url: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    context: Context
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                openUrl(context, url)
            }
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = "在新窗口打开",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun UpdateLogItem(
    version: String,
    date: String,
    changes: List<String>
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = version,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        changes.forEach { change ->
            Text(
                text = change,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 2.dp),
                lineHeight = 16.sp
            )
        }
    }
}

/**
 * 打开 URL - 支持内嵌浏览器和外部浏览器选择
 */
private fun openUrl(context: Context, url: String) {
    // 使用外部浏览器打开
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "无法打开链接: ${e.message}",
            Toast.LENGTH_SHORT
        ).show()
    }
}
