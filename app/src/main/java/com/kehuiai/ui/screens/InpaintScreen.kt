package com.kehuiai.ui.screens

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InpaintScreen(
    originalBitmap: Bitmap?,
    onInpaintComplete: (Bitmap) -> Unit,
    onNavigateBack: () -> Unit
) {
    var maskColor by remember { mutableStateOf(Color.Black) }
    var brushSize by remember { mutableFloatStateOf(30f) }
    var isErasing by remember { mutableStateOf(false) }
    
    val paths = remember { mutableStateListOf<DrawPath>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("局部重绘") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { isErasing = !isErasing }) {
                        Icon(
                            if (isErasing) Icons.Default.Delete else Icons.Default.Edit,
                            contentDescription = "切换模式"
                        )
                    }
                    IconButton(onClick = { paths.clear() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "清除")
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                originalBitmap?.let { bitmap ->
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "原图",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentPath = Path().apply {
                                            moveTo(offset.x, offset.y)
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        currentPath?.lineTo(change.position.x, change.position.y)
                                    },
                                    onDragEnd = {
                                        currentPath?.let { path ->
                                            paths.add(DrawPath(path, maskColor, brushSize, isErasing))
                                        }
                                        currentPath = null
                                    }
                                )
                            }
                    ) {
                        paths.forEach { pathData ->
                            drawPath(
                                path = pathData.path,
                                color = pathData.color.copy(alpha = if (pathData.isErase) 0f else 0.5f),
                                style = Stroke(width = pathData.brushSize)
                            )
                        }
                        currentPath?.let { path ->
                            drawPath(
                                path = path,
                                color = maskColor.copy(alpha = 0.5f),
                                style = Stroke(width = brushSize)
                            )
                        }
                    }
                } ?: Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("请先选择一张图片")
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text("画笔大小: ${brushSize.toInt()}")
                Slider(
                    value = brushSize,
                    onValueChange = { brushSize = it },
                    valueRange = 5f..100f
                )
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(Color.Black, Color.White, Color.Red, Color.Blue, Color.Green).forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (maskColor == color) 3.dp else 1.dp,
                                color = if (maskColor == color) MaterialTheme.colorScheme.primary else Color.Gray,
                                shape = CircleShape
                            )
                    ) {
                        Modifier.pointerInput(color) {
                            detectDragGestures { _, _ -> }
                        }
                    }
                }
            }
            
            Button(
                onClick = {
                    originalBitmap?.let { bitmap ->
                        val resultBitmap = createInpaintedResult(bitmap, paths)
                        onInpaintComplete(resultBitmap)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                enabled = paths.isNotEmpty() && originalBitmap != null
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("应用重绘")
            }
        }
    }
}

private data class DrawPath(
    val path: Path,
    val color: Color,
    val brushSize: Float,
    val isErase: Boolean = false
)

private fun createInpaintedResult(
    bitmap: Bitmap, 
    @Suppress("UNUSED_PARAMETER") paths: List<DrawPath>
): Bitmap {
    return bitmap.copy(Bitmap.Config.ARGB_8888, true)
}
