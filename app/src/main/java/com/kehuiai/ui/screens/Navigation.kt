package com.kehuiai.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kehuiai.data.repository.GenerationRepository
import com.kehuiai.navigation.Screen

/**
 * 可绘AI v3.0 导航组件
 */
@Composable
fun MainNavigation(repository: GenerationRepository) {
    val navController = rememberNavController()
    
    val items = listOf(
        BottomNavItem("生成", Icons.Default.AutoAwesome, Screen.Generation.route),
        BottomNavItem("视频", Icons.Default.MovieCreation, Screen.VideoGeneration.route),
        BottomNavItem("历史", Icons.Default.History, Screen.History.route),
        BottomNavItem("模型", Icons.Default.Storage, Screen.Models.route),
        BottomNavItem("设置", Icons.Default.Settings, Screen.Settings.route)
    )
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Generation.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Generation.route) {
                GenerationScreen(repository = repository)
            }
            composable(Screen.VideoGeneration.route) {
                VideoGenerationScreen()
            }
            composable(Screen.History.route) {
                HistoryScreen(repository = repository)
            }
            composable(Screen.Models.route) {
                ModelsScreen(repository = repository)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(navController = navController)
            }
        }
    }
}

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)
