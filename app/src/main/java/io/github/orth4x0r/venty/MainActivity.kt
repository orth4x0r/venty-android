package io.github.orth4x0r.venty

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.orth4x0r.venty.ui.VentyRoot
import io.github.orth4x0r.venty.ui.theme.VentyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val controller = (application as VentyApp).controller
        setContent {
            VentyTheme {
                VentyRoot(controller)
            }
        }
    }
}
