package com.kehuiai.service.pro

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.util.*

/**
 * 快绘AI Pro v4.0.0 - 项目管理器
 */
class ProProjectGenerator(private val context: Context) {

    companion object {
        private const val TAG = "ProProjectGenerator"
    }

    // ========== 项目类型 ==========
    enum class ProjectType {
        IMAGE_GENERATION, IMAGE_EDITING, VIDEO_EDITING, BATCH_PROCESSING,
        ARTWORK, SOCIAL_MEDIA, MARKETING, DOCUMENTATION, CUSTOM
    }

    // ========== 项目状态 ==========
    enum class ProjectStatus {
        DRAFT, IN_PROGRESS, COMPLETED, ARCHIVED, FAVORITE
    }

    // ========== 项目 ==========
    data class Project(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val description: String = "",
        val type: ProjectType = ProjectType.IMAGE_GENERATION,
        val status: ProjectStatus = ProjectStatus.DRAFT,
        val tags: List<String> = emptyList(),
        val category: String = "默认",
        val thumbnailPath: String? = null,
        val createdAt: Long = System.currentTimeMillis(),
        val modifiedAt: Long = System.currentTimeMillis(),
        val filePaths: List<String> = emptyList(),
        val isFavorite: Boolean = false
    )

    // ========== 模板 ==========
    data class ProjectTemplate(
        val id: String,
        val name: String,
        val description: String,
        val type: ProjectType,
        val tags: List<String> = emptyList()
    )

    // ========== 分类 ==========
    data class Category(
        val name: String,
        val icon: String = "📁",
        val color: Int = 0xFF6366F1.toInt(),
        val projectCount: Int = 0
    )

    // ========== 统计 ==========
    data class ProjectStats(
        val totalProjects: Int,
        val byType: Map<ProjectType, Int>,
        val byCategory: Map<String, Int>,
        val recentProjects: List<Project>,
        val favoriteProjects: List<Project>
    )

    private val projectCache = mutableMapOf<String, Project>()
    private val templates = mutableListOf<ProjectTemplate>()
    private val categories = mutableListOf<Category>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val imageProcessor = ProImageProcessor(context)

    init {
        initializeDefaults()
    }

    private fun initializeDefaults() {
        // 默认模板
        templates.addAll(listOf(
            ProjectTemplate("t1", "人像摄影", "专业人像摄影模板", ProjectType.IMAGE_GENERATION, listOf("人像", "摄影")),
            ProjectTemplate("t2", "风景摄影", "自然风景模板", ProjectType.IMAGE_GENERATION, listOf("风景", "自然")),
            ProjectTemplate("t3", "社交媒体", "社交内容创作", ProjectType.SOCIAL_MEDIA, listOf("社交", "内容")),
            ProjectTemplate("t4", "营销素材", "营销宣传模板", ProjectType.MARKETING, listOf("营销", "宣传")),
            ProjectTemplate("t5", "数字艺术", "艺术创作模板", ProjectType.ARTWORK, listOf("艺术", "创作")),
            ProjectTemplate("t6", "批量处理", "高效批处理模板", ProjectType.BATCH_PROCESSING, listOf("批量", "效率"))
        ))

        // 默认分类
        categories.addAll(listOf(
            Category("摄影", "📷", 0xFFE91E63.toInt()),
            Category("设计", "🎨", 0xFF9C27B0.toInt()),
            Category("营销", "📢", 0xFFFF9800.toInt()),
            Category("社交", "💬", 0xFF2196F3.toInt()),
            Category("艺术", "🖼️", 0xFF4CAF50.toInt()),
            Category("办公", "📄", 0xFF607D8B.toInt()),
            Category("工具", "🔧", 0xFF795548.toInt())
        ))
    }

    // ========== 项目操作 ==========
    fun createProject(name: String, type: ProjectType, description: String = "", category: String = "默认"): Project {
        val project = Project(name = name, type = type, description = description, category = category)
        projectCache[project.id] = project
        Log.i(TAG, "创建项目: ${project.name}")
        return project
    }

    fun createFromTemplate(templateId: String, name: String): Project? {
        val template = templates.find { it.id == templateId } ?: return null
        return Project(
            name = name,
            description = template.description,
            type = template.type,
            tags = template.tags,
            category = "默认"
        ).also { projectCache[it.id] = it }
    }

    fun getProject(id: String): Project? = projectCache[id]

    fun updateProject(project: Project): Project {
        val updated = project.copy(modifiedAt = System.currentTimeMillis())
        projectCache[updated.id] = updated
        return updated
    }

    fun deleteProject(id: String): Boolean = projectCache.remove(id) != null

    fun addFileToProject(projectId: String, filePath: String): Project? {
        val project = projectCache[projectId] ?: return null
        return updateProject(project.copy(filePaths = project.filePaths + filePath))
    }

    fun removeFileFromProject(projectId: String, filePath: String): Project? {
        val project = projectCache[projectId] ?: return null
        return updateProject(project.copy(filePaths = project.filePaths - filePath))
    }

    fun updateStatus(projectId: String, status: ProjectStatus): Project? {
        val project = projectCache[projectId] ?: return null
        return updateProject(project.copy(status = status))
    }

    fun toggleFavorite(projectId: String): Project? {
        val project = projectCache[projectId] ?: return null
        return updateProject(project.copy(isFavorite = !project.isFavorite))
    }

    fun updateThumbnail(projectId: String, imagePath: String): Project? {
        val project = projectCache[projectId] ?: return null
        
        val thumbDir = File(context.cacheDir, "thumbnails")
        if (!thumbDir.exists()) thumbDir.mkdirs()
        
        val thumbFile = File(thumbDir, "${projectId}_thumb.jpg")
        val bitmap = imageProcessor.loadImage(imagePath)
        val thumb = bitmap?.let { imageProcessor.centerCrop(it, 200, 200) }
        
        if (thumb != null) {
            imageProcessor.saveBitmap(thumb, thumbFile)
        }
        
        return updateProject(project.copy(thumbnailPath = thumbFile.absolutePath))
    }

    // ========== 查询 ==========
    fun searchProjects(query: String, type: ProjectType? = null): List<Project> {
        return projectCache.values.filter { project ->
            val matchesQuery = query.isEmpty() || 
                project.name.contains(query, ignoreCase = true) ||
                project.description.contains(query, ignoreCase = true) ||
                project.tags.any { it.contains(query, ignoreCase = true) }
            val matchesType = type == null || project.type == type
            matchesQuery && matchesType
        }
    }

    fun getRecentProjects(limit: Int = 10): List<Project> {
        return projectCache.values.sortedByDescending { it.modifiedAt }.take(limit)
    }

    fun getFavoriteProjects(): List<Project> {
        return projectCache.values.filter { it.isFavorite }
    }

    fun getProjectsByCategory(category: String): List<Project> {
        return projectCache.values.filter { it.category == category }
    }

    fun getProjectsByType(type: ProjectType): List<Project> {
        return projectCache.values.filter { it.type == type }
    }

    // ========== 统计 ==========
    fun getStats(): ProjectStats {
        val projects = projectCache.values.toList()
        return ProjectStats(
            totalProjects = projects.size,
            byType = projects.groupBy { it.type }.mapValues { it.value.size },
            byCategory = projects.groupBy { it.category }.mapValues { it.value.size },
            recentProjects = getRecentProjects(5),
            favoriteProjects = getFavoriteProjects()
        )
    }

    // ========== 模板 ==========
    fun getAllTemplates(): List<ProjectTemplate> = templates.toList()
    fun getTemplatesByType(type: ProjectType): List<ProjectTemplate> = templates.filter { it.type == type }
    fun searchTemplates(query: String): List<ProjectTemplate> = templates.filter {
        it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true)
    }

    // ========== 分类 ==========
    fun getAllCategories(): List<Category> {
        return categories.map { cat ->
            cat.copy(projectCount = projectCache.values.count { it.category == cat.name })
        }
    }

    fun createCategory(name: String, icon: String = "📁", color: Int = 0xFF6366F1.toInt()): Category {
        val category = Category(name, icon, color)
        categories.add(category)
        return category
    }

    fun deleteCategory(name: String): Boolean = categories.removeIf { it.name == name }

    // ========== 导出 ==========
    suspend fun exportProject(projectId: String, outputPath: String): String? = withContext(Dispatchers.IO) {
        val project = projectCache[projectId] ?: return@withContext null
        val file = File(outputPath)
        file.parentFile?.mkdirs()
        
        val content = buildString {
            appendLine("{")
            appendLine("  \"id\": \"${project.id}\",")
            appendLine("  \"name\": \"${project.name}\",")
            appendLine("  \"type\": \"${project.type}\",")
            appendLine("  \"category\": \"${project.category}\",")
            appendLine("  \"fileCount\": ${project.filePaths.size}")
            appendLine("}")
        }
        
        file.writeText(content)
        file.absolutePath
    }

    // ========== 清理 ==========
    fun release() {
        scope.cancel()
        imageProcessor.release()
    }
}
