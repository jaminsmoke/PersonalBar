package com.jaminsmoke.personalbar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.toArgb
import com.jaminsmoke.personalbar.ui.ExpoScreen
import com.jaminsmoke.personalbar.ui.theme.PbBackground
import com.jaminsmoke.personalbar.ui.theme.PbSurfaceContainerLowest
import com.jaminsmoke.personalbar.ui.theme.PersonalBarTheme

class MainActivity : ComponentActivity() {

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationsIfNeeded()
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

    /** API 33+: sin este permiso la notificación del FGS no se ve (el nodo corre igual). */
    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
