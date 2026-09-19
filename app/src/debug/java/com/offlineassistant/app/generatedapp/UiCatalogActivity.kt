package com.offlineassistant.app.generatedapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.offlineassistant.app.ui.theme.DealStudioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class UiCatalogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val caseId = intent.getStringExtra(EXTRA_CASE_ID) ?: DEFAULT_CASE_ID
        val style = intent.getStringExtra(EXTRA_STYLE) ?: DEFAULT_STYLE
        setContent {
            DealStudioTheme(darkTheme = false) {
                UiCatalogScreen(caseId, style)
            }
        }
    }

    companion object {
        const val EXTRA_CASE_ID = "ui_catalog_case_id"
        const val EXTRA_STYLE = "ui_catalog_style"
        const val DEFAULT_CASE_ID = "shadcn.Button.default"
        const val DEFAULT_STYLE = "clean"
    }
}

private data class CatalogFixture(
    val id: String,
    val origin: String,
    val component: String,
    val dealPath: String,
    val dealUiPath: String
)

private data class LoadedCatalogFixture(
    val fixture: CatalogFixture,
    val program: CanonicalDealUiProgram,
    val runtime: CanonicalDealRuntimeSession,
    val initialState: JsonObject
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun UiCatalogScreen(caseId: String, style: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var loaded by remember(caseId, style) { mutableStateOf<LoadedCatalogFixture?>(null) }
    var state by remember(caseId, style) { mutableStateOf<JsonObject?>(null) }
    var failure by remember(caseId, style) { mutableStateOf<String?>(null) }
    var eventLog by remember(caseId, style) { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(caseId, style) {
        runCatching { withContext(Dispatchers.IO) { loadFixture(context, caseId, style) } }
            .onSuccess {
                loaded = it
                state = it.initialState
            }
            .onFailure { failure = it.message ?: "UI catalog fixture failed" }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("UI Catalog · $caseId · $style") })
        }
    ) { padding ->
        when {
            failure != null -> Text(
                text = requireNotNull(failure),
                modifier = Modifier.padding(padding).padding(20.dp)
                    .semantics { contentDescription = "ui-catalog-failed:$caseId" },
                color = MaterialTheme.colorScheme.error
            )

            loaded == null || state == null -> CircularProgressIndicator(
                modifier = Modifier.padding(padding).padding(20.dp)
                    .semantics { contentDescription = "ui-catalog-loading:$caseId" }
            )

            else -> Column(
                Modifier.fillMaxSize().padding(padding)
                    .semantics { contentDescription = "ui-catalog-ready:$caseId" }
            ) {
                val fixture = requireNotNull(loaded)
                CanonicalDealUiRenderer(
                    program = fixture.program,
                    state = requireNotNull(state),
                    modifier = Modifier.weight(1f),
                    onAction = { action ->
                        eventLog = eventLog + "${action.type}(${action.fields})"
                        state = fixture.runtime.dispatch(
                            handler = requireNotNull(fixture.program.updates[action.type]),
                            actionType = action.type,
                            fields = action.fields
                        )
                    }
                )
                Text(
                    text = "${fixture.fixture.origin}.${fixture.fixture.component} · events: ${eventLog.size}",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

private fun loadFixture(context: android.content.Context, caseId: String, style: String): LoadedCatalogFixture {
    val styleToken = requireNotNull(STYLE_TOKENS[style]) { "Unsupported UI catalog style: $style" }
    val manifest = Json.parseToJsonElement(
        context.assets.open("ui-catalog/manifest.json").bufferedReader().use { it.readText() }
    ).jsonObject
    require(manifest.getValue("schemaVersion").jsonPrimitive.content == "deal-studio-ui-catalog-fixtures-v1") {
        "Unsupported UI catalog fixture manifest"
    }
    val entry = manifest.getValue("cases").jsonArray
        .map { it.jsonObject }
        .singleOrNull { it.getValue("id").jsonPrimitive.content == caseId }
        ?: error("Unknown UI catalog case: $caseId")
    val fixture = CatalogFixture(
        id = caseId,
        origin = entry.getValue("origin").jsonPrimitive.content,
        component = entry.getValue("component").jsonPrimitive.content,
        dealPath = entry.getValue("deal").jsonPrimitive.content,
        dealUiPath = entry.getValue("dealUi").jsonPrimitive.content
    )
    fun readAsset(path: String) = context.assets.open(path).bufferedReader().use { it.readText() }
    val deal = readAsset(fixture.dealPath)
    val dealUiTemplate = readAsset(fixture.dealUiPath)
    require(dealUiTemplate.split("ui.__STYLE__").size == 2) { "Fixture must contain exactly one style slot" }
    val dealUi = dealUiTemplate.replace("ui.__STYLE__", "ui.$styleToken")
    val toolchain = CanonicalDealToolchain(context)
    val program = CanonicalDealUiParser.parse(toolchain.compilePortable(deal, dealUi, CanonicalDealUiPack.source))
    val runtime = toolchain.createRuntime(deal)
    return LoadedCatalogFixture(fixture, program, runtime, runtime.snapshot())
}

private val STYLE_TOKENS = mapOf(
    "clean" to "themeClean",
    "soft" to "themeSoft",
    "expressive" to "themeExpressive",
    "editorial" to "themeEditorial",
    "technical" to "themeTechnical",
    "playful" to "themePlayful"
)
