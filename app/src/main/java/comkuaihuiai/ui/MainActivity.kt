package comkuaihuiai.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import comkuaihuiai.data.repository.GenerationRepository
import comkuaihuiai.ui.screens.MainNavigation
import comkuaihuiai.ui.theme.KehuiAITheme

/**
 * 可绘AI v3.0 主界面
 */
class MainActivity : ComponentActivity() {
    
    private lateinit var repository: GenerationRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        repository = GenerationRepository(this)
        
        setContent {
            KehuiAITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(repository = repository)
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        repository.release()
    }
}
