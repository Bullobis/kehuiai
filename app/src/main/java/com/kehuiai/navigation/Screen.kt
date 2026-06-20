package com.kehuiai.navigation

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
    object AISettings : Screen("ai_settings")
    object Chat : Screen("chat")
    object PromptAssistant : Screen("prompt_assistant")
    object Inpaint : Screen("inpaint")
    object ModelMarket : Screen("model_market")
    object ModelSelection : Screen("model_selection")
    object About : Screen("about")
    
    companion object {
        val bottomNavItems = listOf(Generation, VideoGeneration, History, Models, Settings)
    }
}
