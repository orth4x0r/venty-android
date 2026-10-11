package io.github.orth4x0r.venty

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.orth4x0r.venty.ui.VentyRoot
import io.github.orth4x0r.venty.ui.theme.VentyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val app = application as VentyApp
        val controller = app.controller
        setContent {
            val prefs by app.prefs.app.collectAsStateWithLifecycle()
            VentyTheme(dynamicColor = prefs.dynamicColor) {
                VentyRoot(controller)
            }
        }
    }
}
