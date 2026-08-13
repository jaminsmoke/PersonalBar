package com.jaminsmoke.personalbar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import com.jaminsmoke.personalbar.ui.ExpoScreen
import com.jaminsmoke.personalbar.ui.theme.PbBackground
import com.jaminsmoke.personalbar.ui.theme.PbSurfaceContainerLowest
import com.jaminsmoke.personalbar.ui.theme.PersonalBarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val darkScrim = PbBackground.toArgb()
        val navScrim = PbSurfaceContainerLowest.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(darkScrim),
            navigationBarStyle = SystemBarStyle.dark(navScrim),
        )
        setContent {
            PersonalBarTheme {
                ExpoScreen()
            }
        }
    }
}
