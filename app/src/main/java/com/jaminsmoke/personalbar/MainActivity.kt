package com.jaminsmoke.personalbar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
                BarApp()
            }
        }
    }
}

/**
 * Pantalla de arranque mínima del bootstrap. Las colas reales (Bebida/Comida)
 * y el estado «Sala activa» se construyen en el ítem de Expo de barra.
 */
@Composable
fun BarApp() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.home_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BarAppPreview() {
    PersonalBarTheme {
        BarApp()
    }
}
