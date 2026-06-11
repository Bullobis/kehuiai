package comkuaihuiai.navigation

/**
 * 可绘AI v3.0 路由定义
 */
sealed class Screen(val route: String) {
    object Generation : Screen("generation")
    object VideoGeneration : Screen("video_generation")
    object History : Screen("history")
    object Models : Screen("models")
    object Settings : Screen("settings")
    object Gallery : Screen("gallery")
    
    companion object {
        val bottomNavItems = listOf(Generation, VideoGeneration, History, Models, Settings)
    }
}
