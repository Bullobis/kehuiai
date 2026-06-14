package comkuaihuiai.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import comkuaihuiai.data.model.DefaultModels
import comkuaihuiai.data.model.ModelCategory
import comkuaihuiai.data.model.SDModel
import comkuaihuiai.data.repository.ModelRepository
import comkuaihuiai.service.ModelDownloadService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelMarketScreen(
    repository: ModelRepository,
    onDownloadClick: (SDModel) -> Unit,
    onNavigateBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf<ModelCategory?>(null) }
    
    val filteredModels = remember(selectedCategory) {
        when (selectedCategory) {
            ModelCategory.ANIME -> DefaultModels.animeModels
            ModelCategory.REALISTIC -> DefaultModels.realisticModels
            ModelCategory.SDXL -> DefaultModels.sdxlModels
            else -> DefaultModels.getAllModels()
        }
    }
    
    val isNpuSupported = remember { repository.isNpuSupported() }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型市场") },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, "返回")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = when (selectedCategory) {
                    null -> 0
                    ModelCategory.ANIME -> 1
                    ModelCategory.REALISTIC -> 2
                    ModelCategory.SDXL -> 3
                    else -> 0
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(selected = selectedCategory == null, onClick = { selectedCategory = null }) {
                    Text("全部")
                }
                Tab(selected = selectedCategory == ModelCategory.ANIME, onClick = { selectedCategory = ModelCategory.ANIME }) {
                    Text("动漫")
                }
                Tab(selected = selectedCategory == ModelCategory.REALISTIC, onClick = { selectedCategory = ModelCategory.REALISTIC }) {
                    Text("写实")
                }
                Tab(selected = selectedCategory == ModelCategory.SDXL, onClick = { selectedCategory = ModelCategory.SDXL }) {
                    Text("SDXL")
                }
            }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isNpuSupported) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isNpuSupported) Icons.Default.Memory else Icons.Default.Smartphone,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isNpuSupported) "NPU加速可用" else "CPU运行模式",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isNpuSupported) "将使用Qualcomm NPU进行推理加速" else "将使用CPU运行",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredModels) { model ->
                    ModelCard(
                        model = model,
                        repository = repository,
                        onDownload = { startDownload(context, model) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: SDModel,
    repository: ModelRepository,
    onDownload: () -> Unit
) {
    val isDownloaded = remember { repository.isModelDownloaded(model.id) }
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                AssistChip(
                    onClick = { },
                    label = { Text(if (model.runOnCpu) "CPU" else "NPU") },
                    leadingIcon = {
                        Icon(
                            if (model.runOnCpu) Icons.Default.Memory else Icons.Default.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AssistChip(
                    onClick = { },
                    label = { Text(model.approximateSize) },
                    leadingIcon = { Icon(Icons.Default.Storage, null, Modifier.size(16.dp)) }
                )
                if (model.isSdxl) {
                    AssistChip(
                        onClick = { },
                        label = { Text("SDXL") },
                        leadingIcon = { Icon(Icons.Default.Star, null, Modifier.size(16.dp)) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (isDownloaded) {
                    OutlinedButton(onClick = { }) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("已下载")
                    }
                } else {
                    Button(onClick = onDownload) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("下载")
                    }
                }
            }
        }
    }
}

private fun startDownload(context: Context, model: SDModel) {
    val baseUrl = "https://huggingface.co/Bullobis/kehuiai-models/resolve/main"
    val url = "$baseUrl/${model.fileUri}"
    
    val intent = Intent(context, ModelDownloadService::class.java).apply {
        action = ModelDownloadService.ACTION_START
        putExtra(ModelDownloadService.EXTRA_MODEL_ID, model.id)
        putExtra(ModelDownloadService.EXTRA_MODEL_NAME, model.name)
        putExtra(ModelDownloadService.EXTRA_DOWNLOAD_URL, url)
        putExtra(ModelDownloadService.EXTRA_IS_ZIP, true)
    }
    context.startForegroundService(intent)
}
