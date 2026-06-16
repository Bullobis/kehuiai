package com.kehuiai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kehuiai.data.model.*
import com.kehuiai.data.repository.GenerationRepository
import java.io.File

/**
 * Models Management Screen v2.3.0
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    @Suppress("UNUSED_PARAMETER") repository: GenerationRepository
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Checkpoint", "LoRA", "VAE", "Embedding", "ControlNet")
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📦 模型管理", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { /* 下载模型 */ }) {
                        Icon(Icons.Default.Download, "下载")
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
            ScrollableTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
            
            // 模型列表
            when (selectedTab) {
                0 -> CheckpointModelList()
                1 -> LoraModelList()
                2 -> VAEModelList()
                3 -> EmbeddingModelList()
                4 -> ControlNetModelList()
            }
        }
    }
}

@Composable
private fun CheckpointModelList() {
    val models = remember {
        listOf(
            ModelInfo("sd_1_5", "SD 1.5", "models/sd1.5.safetensors", ModelType.CHECKPOINT, 4L * 1024 * 1024 * 1024, BaseModelType.SD_1_5, true),
            ModelInfo("sdxl_1_0", "SDXL 1.0", "models/sdxl.safetensors", ModelType.CHECKPOINT, 6L * 1024 * 1024 * 1024, BaseModelType.SD_XL, true),
            ModelInfo("sd_xl_turbo", "SDXL Turbo", "models/sdxl_turbo.safetensors", ModelType.CHECKPOINT, 6L * 1024 * 1024 * 1024, BaseModelType.SD_XL_TURBO, true)
        )
    }
    
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(models) { model ->
            ModelCard(model)
        }
    }
}

@Composable
private fun LoraModelList() {
    val loras = remember {
        listOf(
            ModelInfo("anime_style", "动漫风格 LoRA", "models/lora/anime.safetensors", ModelType.LORA, 100L * 1024 * 1024),
            ModelInfo("realistic", "写实风格 LoRA", "models/lora/realistic.safetensors", ModelType.LORA, 120L * 1024 * 1024)
        )
    }
    
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(loras) { model ->
            ModelCard(model)
        }
    }
}

@Composable
private fun VAEModelList() {
    val vaes = remember {
        listOf(
            ModelInfo("vae_ft_mae", "VAE-FT-MAE", "builtin", ModelType.VAE, 0, isBuiltIn = true),
            ModelInfo("vae_ft_mse", "VAE-FT-MSE", "builtin", ModelType.VAE, 0, isBuiltIn = true),
            ModelInfo("kl_f8", "KL-F8 (SDXL)", "builtin", ModelType.VAE, 0, isBuiltIn = true)
        )
    }
    
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(vaes) { model ->
            ModelCard(model)
        }
    }
}

@Composable
private fun EmbeddingModelList() {
    val embeddings = remember {
        listOf(
            ModelInfo("masterpiece", "masterpiece", "builtin", ModelType.EMBEDDING, 0, isBuiltIn = true),
            ModelInfo("best_quality", "best quality", "builtin", ModelType.EMBEDDING, 0, isBuiltIn = true),
            ModelInfo("anime", "anime style", "builtin", ModelType.EMBEDDING, 0, isBuiltIn = true)
        )
    }
    
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(embeddings) { model ->
            ModelCard(model)
        }
    }
}

@Composable
private fun ControlNetModelList() {
    val controlNets = remember {
        listOf(
            ModelInfo("canny", "Canny Edge", "models/controlnet/canny.safetensors", ModelType.CONTROLNET, 300L * 1024 * 1024),
            ModelInfo("depth", "Depth Map", "models/controlnet/depth.safetensors", ModelType.CONTROLNET, 300L * 1024 * 1024),
            ModelInfo("pose", "OpenPose", "models/controlnet/pose.safetensors", ModelType.CONTROLNET, 300L * 1024 * 1024)
        )
    }
    
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(controlNets) { model ->
            ModelCard(model)
        }
    }
}

@Composable
private fun ModelCard(model: ModelInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(model.name, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    if (model.isBuiltIn) {
                        SuggestionChip(
                            onClick = { },
                            label = { Text("内置") },
                            modifier = Modifier.height(24.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        formatModelSize(model.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (model.isDownloaded) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "已下载",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                IconButton(onClick = { /* 下载 */ }) {
                    Icon(Icons.Default.Download, "下载")
                }
            }
        }
    }
}

private fun formatModelSize(bytes: Long): String {
    return when {
        bytes == 0L -> "内置"
        bytes < 1024 * 1024 -> "$bytes B"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
    }
}
