package com.offlineassistant.app

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.offlineassistant.app.generatedapp.GeneratedAppStudioRoute
import com.offlineassistant.app.settings.DealStudioSettingsRepository
import com.offlineassistant.app.ui.theme.AssistantTheme

class DealStudioApplication : Application()

class DealStudioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AssistantTheme {
                DealStudioApp()
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DealStudioApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember { DealStudioSettingsRepository(context.applicationContext) }
    var keyConfigured by remember { mutableStateOf(settings.deepSeekApiKeyConfigured) }
    var showKeyDialog by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("DEAL Studio") },
                actions = {
                    IconButton(onClick = { showKeyDialog = true }) {
                        Icon(Icons.Default.Key, contentDescription = "Configure DeepSeek key")
                    }
                }
            )
        }
    ) { padding ->
        GeneratedAppStudioRoute(
            deepSeekApiKeyConfigured = keyConfigured,
            onOpenSettings = { showKeyDialog = true },
            modifier = Modifier.padding(padding)
        )
    }
    if (showKeyDialog) {
        DeepSeekKeyDialog(
            configured = keyConfigured,
            onDismiss = { showKeyDialog = false },
            onSave = { key ->
                settings.saveDeepSeekApiKey(key)
                keyConfigured = true
                showKeyDialog = false
            },
            onClear = {
                settings.clearDeepSeekApiKey()
                keyConfigured = false
                showKeyDialog = false
            }
        )
    }
}

@Composable
private fun DeepSeekKeyDialog(
    configured: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit
) {
    var key by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("DeepSeek API key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (configured) "A key is stored securely." else "Add a key to enable cloud generation.")
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API key") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(key.trim()) }, enabled = key.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            if (configured) {
                OutlinedButton(onClick = onClear) { Text("Remove key") }
            } else {
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
