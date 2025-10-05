package com.example.stempeluhr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import com.example.stempeluhr.ui.screens.AuswertungScreen
import com.example.stempeluhr.ui.screens.SettingsScreen
import com.example.stempeluhr.ui.screens.StempelUhrScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    var page by remember { mutableStateOf("home") }
                    when (page) {
                        "home" -> StempelUhrScreen(
                            context = this,
                            onSettingsClick = { page = "settings" },
                            onAuswertungClick = { page = "report" }
                        )
                        "settings" -> SettingsScreen(
                            context = this,
                            onBackClick = { page = "home" }
                        )
                        "report" -> AuswertungScreen(
                            context = this,
                            onBackClick = { page = "home" }
                        )
                    }
                }
            }
        }
    }
}
