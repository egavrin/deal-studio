package com.offlineassistant.app

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.offlineassistant.app.generatedapp.GeneratedAppStudioRoute
import com.offlineassistant.app.settings.DealStudioSettingsRepository
import com.offlineassistant.app.ui.theme.DealStudioTheme

class DealStudioApplication : Application()

class DealStudioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent {
            DealStudioTheme(darkTheme = false) {
                DealStudioApp(intent.getStringExtra(EXTRA_OPEN_APP_ID))
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_APP_ID = "open_generated_app_id"
    }
}

@Composable
private fun DealStudioApp(initialAppId: String?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember { DealStudioSettingsRepository(context.applicationContext) }
    var keyConfigured by remember { mutableStateOf(settings.deepSeekApiKeyConfigured) }
    var showSettings by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column {
                Box(Modifier.fillMaxWidth().height(80.dp)) {
                    Text(
                        text = "DEAL Studio",
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 14.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(
                        onClick = { showSettings = true },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 4.dp)
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Studio settings")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        GeneratedAppStudioRoute(
            initialAppId = initialAppId,
            deepSeekApiKeyConfigured = keyConfigured,
            settingsOpen = showSettings,
            onOpenSettings = { showSettings = true },
            onDismissSettings = { showSettings = false },
            onSaveApiKey = { key ->
                settings.saveDeepSeekApiKey(key)
                keyConfigured = true
                showSettings = false
            },
            onClearApiKey = {
                settings.clearDeepSeekApiKey()
                keyConfigured = false
            },
            modifier = Modifier.padding(padding)
        )
    }
}
