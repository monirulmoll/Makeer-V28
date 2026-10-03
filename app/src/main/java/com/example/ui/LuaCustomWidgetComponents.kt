package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CanvasComponentEntity
import com.example.engine.LuaCustomWidgetEngine
import com.example.engine.LuaCustomWidgetEvent
import com.example.engine.LuaCustomWidgetProperty
import com.example.engine.LuaCustomWidgetSpec
import com.example.engine.LuaScriptEngine
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DefaultButtonLogicSettingsDialog(
    currentDefaultLogic: String,
    onDismiss: () -> Unit,
    onSaveDefaultLogic: (String) -> Unit,
    onOpenAiSettings: (() -> Unit)? = null
) {
    var logicInput by remember(currentDefaultLogic) {
        mutableStateOf(currentDefaultLogic.ifBlank { LuaCustomWidgetEngine.FALLBACK_DEFAULT_BUTTON_LOGIC })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0E1526),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = "Studio Settings",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Configure Default Button Logic for Lua Script Mode",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF091326),
                    border = BorderStroke(1.dp, Color(0xFF1E3A5F)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Default Button Logic",
                                color = Color(0xFF34D399),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            TextButton(
                                onClick = {
                                    logicInput = LuaCustomWidgetEngine.FALLBACK_DEFAULT_BUTTON_LOGIC
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("settings_reset_default_button_logic")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = "Reset",
                                    tint = Color(0xFF93C5FD),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Reset",
                                    color = Color(0xFF93C5FD),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            text = "Every new Button widget (or custom Button widget without explicit code logic) will automatically pre-fill with this Lua logic:",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        WidgetDirectEditCodeBox(
                            title = "Default Button Logic Code",
                            subtitle = "Default Button Click Lua Logic",
                            code = logicInput,
                            onCodeChange = { logicInput = it },
                            placeholder = LuaCustomWidgetEngine.FALLBACK_DEFAULT_BUTTON_LOGIC,
                            widgetName = "Default Setting",
                            minLines = 3,
                            maxLines = 6,
                            initialDirectEdit = true,
                            variablesHint = "gg.toast(...) or any Lua GameGuardian code",
                            testTagPrefix = "settings_default_button_logic"
                        )
                    }
                }

                if (onOpenAiSettings != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF131C33),
                        border = BorderStroke(1.dp, Color(0xFF283B66)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                onOpenAiSettings()
                            }
                            .testTag("settings_open_ai_mode_row")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "AI GGUF / Backend Settings",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "Open →",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveDefaultLogic(logicInput)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("settings_save_default_button_logic_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Save",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Save",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateNewLuaWidgetScreen(
    defaultButtonLogic: String,
    onBack: () -> Unit,
    onCreateWidget: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    var widgetCode by remember {
        mutableStateOf(
            """
            type = "button"
            name = "My Custom Button"
            """.trimIndent()
        )
    }

    val liveSpec = remember(widgetCode, defaultButtonLogic) {
        if (widgetCode.isBlank()) {
            null
        } else {
            LuaCustomWidgetEngine.analyzeWidgetCode(
                rawCode = widgetCode,
                defaultButtonLogic = defaultButtonLogic
            )
        }
    }

    val codeTemplates = remember {
        listOf(
            "Button" to """
                type = "button"
                name = "Action Button"
            """.trimIndent(),
            "Button + Custom Logic" to """
                type = "button"
                name = "Boost Action"
                function onClick()
                    gg.toast("Custom Boost Executed!")
                end
            """.trimIndent(),
            "Path Input" to """
                type = "path_input"
                name = "Config File Path"
                path = "/storage/emulated/0/Download/config.cfg"
            """.trimIndent(),
            "Text Input" to """
                type = "text_input"
                label = "Message"
                value = "Hello"
            """.trimIndent(),
            "Toggle Switch" to """
                type = "toggle"
                name = "Custom Shield"
                state = false
                function onToggleOn()
                    gg.toast("Custom Shield: ON")
                end
                function onToggleOff()
                    gg.toast("Custom Shield: OFF")
                end
            """.trimIndent(),
            "Slider" to """
                type = "slider"
                name = "Speed Multiplier"
                value = 50
                min = 1
                max = 200
            """.trimIndent(),
            "Select / Option" to """
                type = "select"
                name = "Aim Mode"
                mode = "Head"
                options = {"Head", "Chest", "Auto"}
            """.trimIndent()
        )
    }

    Surface(
        color = Color(0xFF070B14),
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("create_new_lua_widget_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0E1526))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E293B))
                            .testTag("create_custom_widget_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Create New Widget",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Write widget definition code • Auto-detects Type, Properties & Events",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }

                Button(
                    onClick = {
                        if (widgetCode.isNotBlank()) {
                            onCreateWidget(widgetCode)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("create_custom_widget_top_submit_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircleOutline,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Analyze / Create Widget",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Quick Definition Snippets Bar (Optional helper presets — user can edit anything freely)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Quick Code Examples (Tap to load or write your own below):",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        codeTemplates.forEach { (title, snippet) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF15203B),
                                border = BorderStroke(1.dp, Color(0xFF283B66)),
                                modifier = Modifier
                                    .clickable { widgetCode = snippet }
                                    .testTag("custom_widget_preset_${title.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "_")}")
                            ) {
                                Text(
                                    text = title,
                                    color = Color(0xFFBAE6FD),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Widget Code Input Box
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    WidgetDirectEditCodeBox(
                        title = "Widget Code",
                        subtitle = "Lua Widget Definition",
                        code = widgetCode,
                        onCodeChange = { widgetCode = it },
                        placeholder = "type = \"path_input\"\nname = \"Target File\"\npath = \"/storage/emulated/0/Download/file.bin\"",
                        widgetName = "Create Widget",
                        minLines = 7,
                        maxLines = 14,
                        initialDirectEdit = true,
                        variablesHint = "Lua / Property Definition Code",
                        testTagPrefix = "create_custom_widget_code"
                    )
                }

                // Main CTA Button: [ Analyze / Create Widget ]
                Button(
                    onClick = {
                        if (widgetCode.isNotBlank()) {
                            onCreateWidget(widgetCode)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 13.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("analyze_and_create_custom_widget_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "[ Analyze / Create Widget ]",
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Live Code Analyzer Breakdown (shows detected Widget Type, Properties, and Events)
                if (liveSpec != null) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E172A)),
                        border = BorderStroke(1.dp, Color(0xFF1E3A5F)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_widget_analysis_preview_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Detected Widget Blueprint",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF065F46),
                                    border = BorderStroke(1.dp, Color(0xFF10B981))
                                ) {
                                    Text(
                                        text = "type = \"${liveSpec.widgetType}\"",
                                        color = Color(0xFF6EE7B7),
                                        fontSize = 10.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Widget Name: ${liveSpec.widgetName}  •  Category: ${liveSpec.displayTypeBadge()}",
                                color = Color(0xFFBAE6FD),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            // Detected Properties
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Detected Editable Properties (${liveSpec.properties.size}):",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (liveSpec.properties.isEmpty()) {
                                    Text(
                                        text = "• No extra properties (Standard ${liveSpec.displayTypeBadge()} control)",
                                        color = Color(0xFF64748B),
                                        fontSize = 10.5.sp
                                    )
                                } else {
                                    liveSpec.properties.forEach { prop ->
                                        Text(
                                            text = "• ${prop.label} (${prop.key}) → [${prop.type.uppercase(Locale.US)}] = ${prop.value}",
                                            color = Color(0xFF34D399),
                                            fontSize = 10.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            // Detected Events & Default Button Logic resolution
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Detected Event / Action Logic (${liveSpec.events.size}):",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                liveSpec.events.forEach { ev ->
                                    Text(
                                        text = "• ${ev.title}: ${ev.logic.lines().firstOrNull().orEmpty()}",
                                        color = Color(0xFFFBBF24),
                                        fontSize = 10.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Metadata-driven Dynamic Custom Widget Editor rendered inside ComponentInspectorSheet
 * when a custom Lua widget is selected.
 * Automatically generates editor controls from `LuaCustomWidgetSpec`:
 * 1. [Widget Type] & Widget Name
 * 2. Detected Properties (text, number, boolean, path, file, color, slider, select)
 * 3. Event / Action Logic (On Button Click Logic, On Change / Toggle Logic, On Change / On Submit Logic, etc.)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DynamicCustomWidgetEditorSection(
    component: CanvasComponentEntity,
    spec: LuaCustomWidgetSpec,
    defaultButtonLogic: String,
    onUpdateComponentWithSpec: (CanvasComponentEntity, LuaCustomWidgetSpec) -> Unit,
    onTriggerLive: (CanvasComponentEntity, String) -> Unit
) {
    val context = LocalContext.current
    var activePathPropertyKey by remember(component.id) { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val propKey = activePathPropertyKey
        if (uri != null && propKey != null) {
            val pickedName = uri.lastPathSegment?.substringAfterLast(':') ?: "picked_file"
            val resolvedPath = if (pickedName.startsWith("/")) pickedName else "/storage/emulated/0/$pickedName"
            val updatedProps = spec.properties.map { p ->
                if (p.key == propKey) p.copy(value = resolvedPath) else p
            }
            val nextSpec = spec.copy(properties = updatedProps)
            val nextComp = LuaCustomWidgetEngine.applySpecToComponent(component, nextSpec)
            onUpdateComponentWithSpec(nextComp, nextSpec)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dynamic_custom_widget_editor_section")
    ) {
        // 1. [Widget Type] Header Badge
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF0A192F),
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Custom Widget: ${spec.displayTypeBadge()}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF065F46),
                    border = BorderStroke(1.dp, Color(0xFF10B981))
                ) {
                    Text(
                        text = "type = \"${spec.widgetType}\"",
                        color = Color(0xFF6EE7B7),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // 2. DETECTED PROPERTIES SECTION (Dynamically generated per property type!)
        if (spec.properties.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF09152B))
                    .border(BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f)), RoundedCornerShape(12.dp))
                    .padding(10.dp)
                    .testTag("custom_widget_detected_properties_card")
            ) {
                Text(
                    text = "Detected Properties (${spec.properties.size})",
                    color = Color(0xFF38BDF8),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                spec.properties.forEach { prop ->
                    val updatePropValue: (String) -> Unit = { newVal ->
                        val updatedProps = spec.properties.map { p ->
                            if (p.key == prop.key) p.copy(value = newVal) else p
                        }
                        val nextSpec = spec.copy(properties = updatedProps)
                        val nextComp = LuaCustomWidgetEngine.applySpecToComponent(component, nextSpec)
                        onUpdateComponentWithSpec(nextComp, nextSpec)
                    }

                    when (prop.type.lowercase(Locale.US)) {
                        "path", "file" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "${prop.label} (${prop.type})",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = prop.value,
                                        onValueChange = updatePropValue,
                                        placeholder = {
                                            Text(
                                                "/storage/emulated/0/...",
                                                color = Color(0xFF64748B),
                                                fontSize = 11.5.sp
                                            )
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color(0xFF060C1A),
                                            unfocusedContainerColor = Color(0xFF060C1A),
                                            focusedBorderColor = Color(0xFF38BDF8),
                                            unfocusedBorderColor = Color(0xFF1F2E4D),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        textStyle = TextStyle(
                                            fontSize = 11.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("custom_prop_path_${prop.key}")
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF3B82F6))
                                            .clickable {
                                                activePathPropertyKey = prop.key
                                                filePickerLauncher.launch(arrayOf("*/*"))
                                            }
                                            .testTag("custom_prop_file_picker_${prop.key}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = "Pick File/Path",
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }
                        }

                        "boolean" -> {
                            val isBoolOn = prop.value.equals("true", ignoreCase = true) || prop.value == "1"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF060C1A))
                                    .border(BorderStroke(1.dp, Color(0xFF1F2E4D)), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = prop.label,
                                        color = Color.White,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${prop.key} = ${if (isBoolOn) "true (ON)" else "false (OFF)"}",
                                        color = if (isBoolOn) Color(0xFF34D399) else Color(0xFF94A3B8),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Switch(
                                    checked = isBoolOn,
                                    onCheckedChange = { checked ->
                                        updatePropValue(if (checked) "true" else "false")
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF10B981),
                                        uncheckedThumbColor = Color(0xFF94A3B8),
                                        uncheckedTrackColor = Color(0xFF1E293B)
                                    ),
                                    modifier = Modifier.testTag("custom_prop_bool_${prop.key}")
                                )
                            }
                        }

                        "slider" -> {
                            val numVal = prop.value.toFloatOrNull() ?: prop.min
                            val safeMin = prop.min
                            val safeMax = if (prop.max > prop.min) prop.max else (prop.min + 100f)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${prop.label} (${safeMin.roundToInt()} .. ${safeMax.roundToInt()})",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${prop.key} = ${numVal.roundToInt()}",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Slider(
                                    value = numVal.coerceIn(safeMin, safeMax),
                                    onValueChange = { v ->
                                        updatePropValue(v.roundToInt().toString())
                                    },
                                    valueRange = safeMin..safeMax,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF38BDF8),
                                        activeTrackColor = Color(0xFF3B82F6),
                                        inactiveTrackColor = Color(0xFF1E293B)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("custom_prop_slider_${prop.key}")
                                )
                            }
                        }

                        "select" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "${prop.label} (Select Option)",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    prop.options.forEach { optionItem ->
                                        val isSelected = prop.value == optionItem
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) Color(0xFF2563EB) else Color(0xFF060C1A),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) Color(0xFF93C5FD) else Color(0xFF1F2E4D)
                                            ),
                                            modifier = Modifier
                                                .clickable { updatePropValue(optionItem) }
                                                .testTag("custom_prop_select_${prop.key}_$optionItem")
                                        ) {
                                            Text(
                                                text = optionItem,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        "color" -> {
                            val colorPresets = listOf("#10B981", "#3B82F6", "#8B5CF6", "#EF4444", "#F59E0B", "#EC4899")
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "${prop.label} (Color Hex)",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = prop.value,
                                        onValueChange = updatePropValue,
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color(0xFF060C1A),
                                            unfocusedContainerColor = Color(0xFF060C1A),
                                            focusedBorderColor = Color(0xFF38BDF8),
                                            unfocusedBorderColor = Color(0xFF1F2E4D),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        textStyle = TextStyle(fontSize = 11.5.sp, fontFamily = FontFamily.Monospace),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("custom_prop_color_${prop.key}")
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        colorPresets.forEach { hex ->
                                            val c = try {
                                                Color(android.graphics.Color.parseColor(hex))
                                            } catch (_: Throwable) {
                                                Color.Cyan
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(c)
                                                    .border(
                                                        BorderStroke(
                                                            if (prop.value.equals(hex, ignoreCase = true)) 2.dp else 0.5.dp,
                                                            Color.White
                                                        ),
                                                        CircleShape
                                                    )
                                                    .clickable { updatePropValue(hex) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        "number" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "${prop.label} (Number)",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedTextField(
                                    value = prop.value,
                                    onValueChange = updatePropValue,
                                    placeholder = {
                                        Text("0", color = Color(0xFF64748B), fontSize = 11.5.sp)
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF060C1A),
                                        unfocusedContainerColor = Color(0xFF060C1A),
                                        focusedBorderColor = Color(0xFF38BDF8),
                                        unfocusedBorderColor = Color(0xFF1F2E4D),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    textStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("custom_prop_number_${prop.key}")
                                )
                            }
                        }

                        else -> {
                            // Default: "text" input box (e.g. Default Text / Message / Value)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = if (spec.widgetType == "text_input" && (prop.key == "value" || prop.key == "text")) {
                                        "Default Text (${prop.label})"
                                    } else {
                                        prop.label
                                    },
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedTextField(
                                    value = prop.value,
                                    onValueChange = updatePropValue,
                                    placeholder = {
                                        Text(
                                            "Enter ${prop.label.lowercase(Locale.US)}...",
                                            color = Color(0xFF64748B),
                                            fontSize = 11.5.sp
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF060C1A),
                                        unfocusedContainerColor = Color(0xFF060C1A),
                                        focusedBorderColor = Color(0xFF38BDF8),
                                        unfocusedBorderColor = Color(0xFF1F2E4D),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    textStyle = TextStyle(fontSize = 12.sp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("custom_prop_text_${prop.key}")
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. EVENT / ACTION LOGIC SECTION (Dynamically generated from spec.events)
        if (spec.events.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF09152B))
                    .border(BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.55f)), RoundedCornerShape(12.dp))
                    .padding(10.dp)
                    .testTag("custom_widget_events_logic_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "Event Logic",
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = when (spec.widgetType) {
                                "button" -> "On Button Click Logic"
                                "toggle" -> "On Change / Toggle Logic"
                                "path_input", "text_input", "number_input" -> "On Change / On Submit Logic"
                                else -> "Event / Action Logic"
                            },
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, Color(0xFF10B981)),
                        modifier = Modifier
                            .clickable {
                                val resetEvents = spec.events.mapIndexed { idx, ev ->
                                    if (idx == 0 && spec.widgetType == "button") {
                                        ev.copy(logic = defaultButtonLogic)
                                    } else if (idx == 0) {
                                        ev.copy(
                                            logic = LuaScriptEngine.defaultButtonLogicForWidget(
                                                label = spec.widgetName,
                                                type = component.type,
                                                linkUrl = component.linkUrl,
                                                customDefaultButtonLogic = defaultButtonLogic
                                            )
                                        )
                                    } else if (idx == 1) {
                                        ev.copy(
                                            logic = LuaScriptEngine.defaultOffLogicForWidget(
                                                label = spec.widgetName,
                                                type = component.type
                                            )
                                        )
                                    } else {
                                        ev
                                    }
                                }
                                val nextSpec = spec.copy(events = resetEvents)
                                val nextComp = LuaCustomWidgetEngine.applySpecToComponent(component, nextSpec)
                                onUpdateComponentWithSpec(nextComp, nextSpec)
                            }
                            .testTag("custom_widget_reset_default_logic_button")
                    ) {
                        Text(
                            text = "Use Default Logic",
                            color = Color(0xFF6EE7B7),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                spec.events.forEachIndexed { index, eventItem ->
                    val propNames = spec.properties.joinToString(", ") { it.key }
                        .ifBlank { "label, state, value, text" }

                    WidgetDirectEditCodeBox(
                        title = eventItem.title,
                        subtitle = "${spec.widgetName} • ${eventItem.title}",
                        code = eventItem.logic,
                        onCodeChange = { updatedLogic ->
                            val newEvents = spec.events.mapIndexed { i, ev ->
                                if (i == index) ev.copy(logic = updatedLogic) else ev
                            }
                            val nextSpec = spec.copy(events = newEvents)
                            val nextComp = LuaCustomWidgetEngine.applySpecToComponent(component, nextSpec)
                            onUpdateComponentWithSpec(nextComp, nextSpec)
                        },
                        placeholder = defaultButtonLogic,
                        widgetName = spec.widgetName,
                        titleColor = if (index == 0) Color(0xFF6EE7B7) else Color(0xFFCBD5E1),
                        borderColor = if (index == 0) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFF38BDF8).copy(alpha = 0.4f),
                        variablesHint = "Variables: $propNames",
                        bottomActionRow = {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF059669),
                                modifier = Modifier
                                    .clickable {
                                        val testComp = LuaCustomWidgetEngine.applySpecToComponent(component, spec)
                                        onTriggerLive(testComp, if (index == 1) "0" else "1")
                                    }
                                    .testTag("custom_widget_test_event_${eventItem.key}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Test ${eventItem.key}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        },
                        testTagPrefix = if (index == 0) "inspector_lua_button_logic" else "inspector_lua_event_${eventItem.key}"
                    )
                }
            }
        }
    }
}
