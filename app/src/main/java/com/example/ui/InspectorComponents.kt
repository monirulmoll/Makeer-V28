package com.example.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Token
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CanvasComponentEntity
import com.example.engine.ShizukuPrivilegeBridge
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.roundToInt

private val DefaultWidgetBgPresets = listOf(
    "#00000000" to "Transparent",
    "#334155" to "Slate",
    "#1E293B" to "Dark Navy",
    "#0F172A" to "Midnight",
    "#000000" to "Black",
    "#FFFFFF" to "White",
    "#2563EB" to "Royal Blue",
    "#4F46E5" to "Indigo",
    "#7C3AED" to "Purple",
    "#10B981" to "Green",
    "#EF4444" to "Red",
    "#F59E0B" to "Gold",
    "#EC4899" to "Pink",
    "#06B6D4" to "Cyan",
    "#800F172A" to "Glass Dark",
    "#80FFFFFF" to "Glass Light"
)

private val QuickOpacityPresets = listOf(
    0 to "Transparent (0%)",
    25 to "25% Glass",
    50 to "50% Half",
    75 to "75% Soft",
    100 to "100% Solid"
)

private fun extractBaseRgb6(hex: String): String {
    val clean = hex.trim().removePrefix("#").uppercase(Locale.US)
    return when (clean.length) {
        8 -> {
            val rgb = clean.substring(2)
            if (clean == "00000000") "334155" else rgb
        }
        6 -> clean
        else -> "334155"
    }
}

private fun extractAlphaPercent(hex: String): Int {
    val clean = hex.trim().removePrefix("#")
    return if (clean.length == 8) {
        val alphaByte = clean.substring(0, 2).toIntOrNull(16) ?: 255
        ((alphaByte / 255f) * 100f).roundToInt().coerceIn(0, 100)
    } else {
        100
    }
}

private fun buildHexWithAlphaPercent(baseRgb6: String, alphaPercent: Int): String {
    val safeRgb = if (baseRgb6.length == 6) baseRgb6.uppercase(Locale.US) else "334155"
    val pct = alphaPercent.coerceIn(0, 100)
    if (pct >= 100) {
        return "#$safeRgb"
    }
    val alphaByte = ((pct / 100f) * 255f).roundToInt().coerceIn(0, 255)
    return String.format(Locale.US, "#%02X%s", alphaByte, safeRgb)
}

private val DefaultWidgetTextPresets = listOf(
    "#FFFFFF" to "White",
    "#0F172A" to "Dark",
    "#38BDF8" to "Sky Blue",
    "#4ADE80" to "Neon Green",
    "#FACC15" to "Yellow",
    "#F87171" to "Coral Red",
    "#C084FC" to "Violet",
    "#FB923C" to "Orange"
)

private val InspectorDarkBg = Color(0xFF050B18)
private val InspectorFieldBg = Color(0xFF081329)
private val InspectorFieldBorder = Color(0xFF1B325F)
private val InspectorTabInactiveBg = Color(0xFF081226)
private val InspectorTabInactiveBorder = Color(0xFF1A2C4E)
private val InspectorPurpleButton = Color(0xFF5B46F6)
private val InspectorPurpleAccent = Color(0xFF4F46E5)
private val InspectorSubtitleColor = Color(0xFF7B93B8)
private val InspectorPlaceholderColor = Color(0xFF6482AD)

private fun resolveDocumentUriToStoragePath(uri: Uri, fallback: String): String {
    val rawPath = uri.path ?: return fallback
    val primaryIndex = rawPath.indexOf("primary:")
    if (primaryIndex >= 0) {
        val relative = rawPath.substring(primaryIndex + "primary:".length).trimStart('/')
        return "/storage/emulated/0/$relative"
    }
    val colonIndex = rawPath.lastIndexOf(':')
    if (colonIndex >= 0 && colonIndex < rawPath.length - 1) {
        val relative = rawPath.substring(colonIndex + 1).trimStart('/')
        if (relative.isNotEmpty()) {
            return "/storage/emulated/0/$relative"
        }
    }
    return rawPath.ifBlank { fallback }
}

@Composable
fun PropertyInspectorBottomDock(
    component: CanvasComponentEntity,
    hasStoragePermission: Boolean = true,
    isAutoFixSize: Boolean = true,
    onToggleAutoFixSize: () -> Unit = {},
    onOpenEditCode: () -> Unit = {},
    onOpenEditFloatingPanel: () -> Unit = {},
    onUpdateComponent: (CanvasComponentEntity) -> Unit,
    onSaveDesign: (CanvasComponentEntity) -> Unit = {},
    onPickImageUri: (Uri) -> Unit = {},
    onPickSoundUri: (Uri, Boolean) -> Unit = { _, _ -> },
    onDuplicateComponent: () -> Unit = {},
    onDeleteComponent: () -> Unit = {},
    onTestTriggerWrite: (CanvasComponentEntity) -> Unit = {},
    onCloseDock: () -> Unit = {}
) {
    ComponentPropertyInspectorSheet(
        component = component,
        isAutoFixSize = isAutoFixSize,
        onToggleAutoFixSize = onToggleAutoFixSize,
        onPickImageUri = onPickImageUri,
        onOpenEditFloatingPanel = onOpenEditFloatingPanel,
        onSaveComponent = { updated ->
            onUpdateComponent(updated)
        },
        onDuplicateComponent = { onDuplicateComponent() },
        onDeleteComponent = { onDeleteComponent() },
        onTriggerLive = { updated, _ -> onTestTriggerWrite(updated) },
        onOpenCodeEditor = onOpenEditCode,
        onClose = onCloseDock
    )
}

@Composable
fun ComponentPropertyInspectorSheet(
    component: CanvasComponentEntity,
    isAutoFixSize: Boolean = true,
    onToggleAutoFixSize: () -> Unit = {},
    onPickImageUri: (Uri) -> Unit = {},
    onOpenEditFloatingPanel: () -> Unit = {},
    onSaveComponent: (CanvasComponentEntity) -> Unit,
    onDuplicateComponent: (CanvasComponentEntity) -> Unit,
    onDeleteComponent: (CanvasComponentEntity) -> Unit,
    onTriggerLive: (CanvasComponentEntity, String) -> Unit,
    onOpenCodeEditor: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf("General") }
    var label by remember(component.id) { mutableStateOf(component.label) }
    var customSourceFilePath by remember(component.id, component.customImagePath) {
        mutableStateOf(component.customImagePath)
    }
    var bgImagePath by remember(component.id, component.bgImagePath) {
        mutableStateOf(component.bgImagePath)
    }
    var posX by remember(component.id) { mutableStateOf(component.posXDp.toString()) }
    var posY by remember(component.id) { mutableStateOf(component.posYDp.toString()) }
    var widthDp by remember(component.id) { mutableStateOf(component.widthDp.toString()) }
    var heightDp by remember(component.id) { mutableStateOf(component.heightDp.toString()) }
    var byteOffset by remember(component.id) { mutableStateOf(component.byteOffsetHex) }
    var onPayload by remember(component.id) { mutableStateOf(component.onPayloadHex) }
    var offPayload by remember(component.id) { mutableStateOf(component.offPayloadHex) }
    var bgHex by remember(component.id) { mutableStateOf(component.bgColorHex) }
    var textHex by remember(component.id) { mutableStateOf(component.textColorHex) }
    var currentValue by remember(component.id) { mutableStateOf(component.currentValue) }
    var targetFile by remember(component.id) { mutableStateOf(component.targetFilePath) }

    fun buildUpdated(
        overrideBgHex: String = bgHex,
        overrideTextHex: String = textHex,
        overrideBgImagePath: String = bgImagePath
    ): CanvasComponentEntity {
        return component.copy(
            label = label.trim().ifEmpty { component.label },
            customImagePath = customSourceFilePath.trim(),
            bgImagePath = overrideBgImagePath.trim(),
            posXDp = posX.toIntOrNull()?.coerceAtLeast(0) ?: component.posXDp,
            posYDp = posY.toIntOrNull()?.coerceAtLeast(0) ?: component.posYDp,
            widthDp = widthDp.toIntOrNull()?.coerceIn(48, 400) ?: component.widthDp,
            heightDp = heightDp.toIntOrNull()?.coerceIn(32, 300) ?: component.heightDp,
            byteOffsetHex = byteOffset.trim().ifEmpty { "0x04" },
            onPayloadHex = onPayload.trim().ifEmpty { "On" },
            offPayloadHex = offPayload.trim().ifEmpty { "Off" },
            bgColorHex = overrideBgHex.trim().ifEmpty { "#131C33" },
            textColorHex = overrideTextHex.trim().ifEmpty { "#FFFFFF" },
            currentValue = currentValue.trim(),
            targetFilePath = targetFile.trim()
        )
    }

    // Phone gallery image picker for Widget Background Image
    val widgetBgImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val destDir = File(context.filesDir, "widget_bg_images").apply { mkdirs() }
                val destFile = File(destDir, "widget_bg_${component.id}_${System.currentTimeMillis()}.png")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (destFile.exists() && destFile.length() > 0L) {
                    bgImagePath = destFile.absolutePath
                    onSaveComponent(buildUpdated(overrideBgImagePath = destFile.absolutePath))
                }
            } catch (_: Exception) {
            }
        }
    }

    // Directory icon on "Select your main file" selects ANY source file to replace/merge onto Target Path
    val sourceFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onPickImageUri(uri)
        }
    }

    // Directory icon on "Target Path" selects the destination target file path
    val targetDocumentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val resolvedPath = resolveDocumentUriToStoragePath(uri, targetFile)
            targetFile = resolvedPath
            onSaveComponent(buildUpdated().copy(targetFilePath = resolvedPath.trim()))
        }
    }

    val currentOpacityPercent = remember(bgHex) { extractAlphaPercent(bgHex) }

    fun applyWidgetBgOpacityPercent(newOpacityPct: Int) {
        val baseRgb = extractBaseRgb6(bgHex)
        val updatedHex = buildHexWithAlphaPercent(baseRgb, newOpacityPct)
        bgHex = updatedHex
        onSaveComponent(buildUpdated(overrideBgHex = updatedHex))
    }

    fun applyDefaultWidgetBgPreset(presetHex: String) {
        if (presetHex.equals("#00000000", ignoreCase = true)) {
            val baseRgb = extractBaseRgb6(bgHex)
            val transparentHex = buildHexWithAlphaPercent(baseRgb, 0)
            bgHex = transparentHex
            onSaveComponent(buildUpdated(overrideBgHex = transparentHex))
            return
        }
        val cleanPreset = presetHex.trim().removePrefix("#")
        val resolvedHex = if (cleanPreset.length == 6 && currentOpacityPercent in 1..99) {
            buildHexWithAlphaPercent(cleanPreset, currentOpacityPercent)
        } else {
            presetHex
        }
        val isLightBg = presetHex.equals("#FFFFFF", ignoreCase = true) ||
            presetHex.equals("#80FFFFFF", ignoreCase = true) ||
            presetHex.equals("#FACC15", ignoreCase = true)
        val autoTextHex = when {
            isLightBg && textHex.equals("#FFFFFF", ignoreCase = true) -> "#0F172A"
            !isLightBg && textHex.equals("#0F172A", ignoreCase = true) -> "#FFFFFF"
            else -> textHex
        }
        bgHex = resolvedHex
        textHex = autoTextHex
        onSaveComponent(buildUpdated(overrideBgHex = resolvedHex, overrideTextHex = autoTextHex))
    }

    val widgetBgPreviewBitmap = remember(bgImagePath) {
        if (bgImagePath.isNotBlank()) {
            val f = File(bgImagePath)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
        } else null
    }

    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = InspectorDarkBg,
        border = BorderStroke(1.dp, Color(0xFF152342)),
        tonalElevation = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 275.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Compact Pinned Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(listOf(InspectorPurpleAccent, InspectorPurpleButton))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Token,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        val readableType = component.type.lowercase().replaceFirstChar { it.uppercase() }
                        Text(
                            text = "$readableType Widget",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Scroll down for more widget options",
                            color = InspectorSubtitleColor,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = Color(0xFF091328),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E335C))
                    ) {
                        Text(
                            text = label.ifBlank { component.label },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("inspector_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close inspector",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Pinned 3 Tabs: General | Style | Advanced
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("General", "Style", "Advanced").forEach { tab ->
                    val isSelected = activeTab == tab
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) InspectorPurpleButton else InspectorTabInactiveBg,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF60A5FA) else InspectorTabInactiveBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clickable { activeTab = tab }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = tab,
                                color = if (isSelected) Color.White else Color(0xFFE2E8F0),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Vertically Scrollable Options Area (keeps top Floating Window Preview visible!)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (activeTab) {
                    "General" -> {
                        val selectedFileName = remember(customSourceFilePath) {
                            if (customSourceFilePath.isNotBlank()) {
                                File(customSourceFilePath).name.ifBlank { customSourceFilePath }
                            } else {
                                ""
                            }
                        }

                        // 1. Widget Name
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Widget Name",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = label,
                                onValueChange = {
                                    label = it
                                    onSaveComponent(buildUpdated())
                                },
                                placeholder = {
                                    Text("Button #1", color = InspectorPlaceholderColor, fontSize = 12.sp)
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = InspectorFieldBg,
                                    unfocusedContainerColor = InspectorFieldBg,
                                    focusedBorderColor = Color(0xFF3B82F6),
                                    unfocusedBorderColor = InspectorFieldBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("inspector_label_input")
                            )
                        }

                        // 2. Select your main file ("Select file" box + purple folder button on right)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Select your main file",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = InspectorFieldBg,
                                    border = BorderStroke(1.dp, InspectorFieldBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .clickable {
                                            sourceFilePickerLauncher.launch(arrayOf("*/*"))
                                        }
                                        .testTag("inspector_main_file_box")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = if (selectedFileName.isNotBlank()) selectedFileName else "Select file",
                                            color = if (selectedFileName.isNotBlank()) Color.White else InspectorPlaceholderColor,
                                            fontSize = 12.sp,
                                            fontWeight = if (selectedFileName.isNotBlank()) FontWeight.SemiBold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(InspectorPurpleButton)
                                        .clickable {
                                            sourceFilePickerLauncher.launch(arrayOf("*/*"))
                                        }
                                        .testTag("inspector_select_source_file_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = "Select Main File",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // 3. Target Path
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Target Path",
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
                                    value = targetFile,
                                    onValueChange = {
                                        targetFile = it
                                        onSaveComponent(buildUpdated())
                                    },
                                    placeholder = {
                                        Text(
                                            "/storage/emulated/0/app.apk",
                                            color = InspectorPlaceholderColor,
                                            fontSize = 12.sp
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = InspectorFieldBg,
                                        unfocusedContainerColor = InspectorFieldBg,
                                        focusedBorderColor = Color(0xFF3B82F6),
                                        unfocusedBorderColor = InspectorFieldBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("inspector_target_path_input")
                                )

                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(InspectorPurpleButton)
                                        .clickable {
                                            targetDocumentPickerLauncher.launch(arrayOf("*/*"))
                                        }
                                        .testTag("inspector_select_target_path_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = "Select Target Path",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            val isRestrictedAndroidPath = remember(targetFile) {
                                ShizukuPrivilegeBridge.isRestrictedAndroidPath(targetFile)
                            }
                            val shizukuReady = ShizukuPrivilegeBridge.isShizukuReady()
                            val shizukuStatusText = ShizukuPrivilegeBridge.getStatusSummary(context)

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isRestrictedAndroidPath && !shizukuReady) Color(0xFF1F1235) else Color(0xFF09152B),
                                border = BorderStroke(
                                    1.dp,
                                    if (isRestrictedAndroidPath && !shizukuReady) Color(0xFF8B5CF6) else Color(0xFF1E3A5F)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Security,
                                                contentDescription = null,
                                                tint = if (shizukuReady) Color(0xFF10B981) else Color(0xFFA78BFA),
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = if (isRestrictedAndroidPath) {
                                                    "Android 15 Restricted Path (Android/data or obb)"
                                                } else {
                                                    "Target Path Testing & Shizuku Engine"
                                                },
                                                color = Color.White,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = shizukuStatusText,
                                            color = if (shizukuReady) Color(0xFF34D399) else Color(0xFFC4B5FD),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF0EA5E9).copy(alpha = 0.2f),
                                            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(32.dp)
                                                .clickable {
                                                    val updated = buildUpdated()
                                                    onSaveComponent(updated)
                                                    onTriggerLive(updated, updated.currentValue.ifBlank { "1" })
                                                }
                                                .testTag("inspector_test_target_write_button")
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "Test Target File Change",
                                                    color = Color(0xFFE0F2FE),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF7C3AED).copy(alpha = 0.28f),
                                            border = BorderStroke(1.dp, Color(0xFFA78BFA)),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(32.dp)
                                                .clickable {
                                                    ShizukuPrivilegeBridge.probeShizukuBinder(context)
                                                    if (!ShizukuPrivilegeBridge.isShizukuReady()) {
                                                        if (ShizukuPrivilegeBridge.isShizukuRunning()) {
                                                            ShizukuPrivilegeBridge.requestPermission(1401)
                                                        } else {
                                                            ShizukuPrivilegeBridge.openOrDownloadShizukuApp(context)
                                                        }
                                                    } else {
                                                        val updated = buildUpdated()
                                                        onSaveComponent(updated)
                                                        onTriggerLive(updated, updated.currentValue.ifBlank { "1" })
                                                    }
                                                }
                                                .testTag("inspector_shizuku_fix_button")
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = if (shizukuReady) "Shizuku Ready ✓" else "Use Shizuku Fix",
                                                    color = Color(0xFFEDE9FE),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Widget Background Image (From Phone Gallery)
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                text = "Widget Background Image (From Phone)",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = InspectorFieldBg,
                                    border = BorderStroke(1.dp, InspectorFieldBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clickable { widgetBgImagePickerLauncher.launch("image/*") }
                                        .testTag("general_widget_bg_image_pick_button")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (widgetBgPreviewBitmap != null) {
                                            Image(
                                                bitmap = widgetBgPreviewBitmap,
                                                contentDescription = "Selected Widget BG",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Image,
                                                contentDescription = null,
                                                tint = Color(0xFF38BDF8),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Text(
                                            text = if (bgImagePath.isNotBlank()) "Image set (Tap to change)" else "Select background image from phone",
                                            color = if (bgImagePath.isNotBlank()) Color.White else InspectorPlaceholderColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                if (bgImagePath.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFFEF4444).copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                        modifier = Modifier
                                            .height(44.dp)
                                            .clickable {
                                                bgImagePath = ""
                                                onSaveComponent(buildUpdated(overrideBgImagePath = ""))
                                            }
                                            .testTag("general_widget_bg_image_clear_button")
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Remove",
                                                color = Color(0xFFFCA5A5),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 5. Widget Background Default Colors + Transparency Strip
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Widget Background (Colors & Transparent)",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Opacity: $currentOpacityPercent% • Style Tab →",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { activeTab = "Style" }
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DefaultWidgetBgPresets.forEach { (presetHex, presetName) ->
                                    val isTransparentChip = presetHex.equals("#00000000", ignoreCase = true)
                                    val isSelectedBg = if (isTransparentChip) {
                                        currentOpacityPercent == 0
                                    } else {
                                        bgHex.equals(presetHex, ignoreCase = true) ||
                                            (currentOpacityPercent > 0 && presetHex.length == 7 &&
                                                extractBaseRgb6(bgHex).equals(presetHex.removePrefix("#"), ignoreCase = true))
                                    }
                                    val swatchColor = parseHexColorSafe(presetHex, Color(0xFF334155))
                                    val slug = presetHex.removePrefix("#").lowercase(Locale.US)
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelectedBg) Color(0xFF1E293B) else InspectorFieldBg,
                                        border = BorderStroke(
                                            width = if (isSelectedBg) 1.5.dp else 1.dp,
                                            color = if (isSelectedBg) Color(0xFF38BDF8) else InspectorFieldBorder
                                        ),
                                        modifier = Modifier
                                            .clickable { applyDefaultWidgetBgPreset(presetHex) }
                                            .testTag("general_widget_bg_preset_$slug")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clip(CircleShape)
                                                    .background(swatchColor)
                                                    .border(BorderStroke(1.dp, Color(0xFF94A3B8)), CircleShape)
                                            )
                                            Text(
                                                text = presetName,
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelectedBg) FontWeight.ExtraBold else FontWeight.Medium,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            // Quick Transparency / Opacity Selector for Selected Color right in General tab
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                QuickOpacityPresets.forEach { (pct, labelText) ->
                                    val isSelectedOpacity = currentOpacityPercent == pct
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelectedOpacity) Color(0xFF1E293B) else InspectorFieldBg,
                                        border = BorderStroke(
                                            width = if (isSelectedOpacity) 1.5.dp else 1.dp,
                                            color = if (isSelectedOpacity) Color(0xFF38BDF8) else InspectorFieldBorder
                                        ),
                                        modifier = Modifier
                                            .clickable { applyWidgetBgOpacityPercent(pct) }
                                            .testTag("general_widget_opacity_$pct")
                                    ) {
                                        Text(
                                            text = labelText,
                                            color = if (isSelectedOpacity) Color(0xFF38BDF8) else Color(0xFFE2E8F0),
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelectedOpacity) FontWeight.ExtraBold else FontWeight.Medium,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    "Style" -> {
                        val liveBgColor = parseHexColorSafe(bgHex, Color(0xFF334155))
                        val liveTextColor = parseHexColorSafe(textHex, Color.White)

                        // 1. Widget Background Image from Phone Gallery + Live Mini Preview
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                text = "Widget Background Image (From Phone)",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = InspectorFieldBg,
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clickable { widgetBgImagePickerLauncher.launch("image/*") }
                                        .testTag("widget_bg_image_pick_button")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (widgetBgPreviewBitmap != null) {
                                            Image(
                                                bitmap = widgetBgPreviewBitmap,
                                                contentDescription = "Widget BG Preview",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Image,
                                                contentDescription = null,
                                                tint = Color(0xFF38BDF8),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Text(
                                            text = if (bgImagePath.isNotBlank()) "Change Phone Background Image" else "Pick Image from Phone Gallery",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                if (bgImagePath.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFFEF4444).copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                        modifier = Modifier
                                            .height(44.dp)
                                            .clickable {
                                                bgImagePath = ""
                                                onSaveComponent(buildUpdated(overrideBgImagePath = ""))
                                            }
                                            .testTag("widget_bg_image_clear_button")
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Remove",
                                                color = Color(0xFFFCA5A5),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // Live Mini Widget Preview Pill
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = liveBgColor,
                                    border = BorderStroke(1.dp, Color(0xFF64748B)),
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (widgetBgPreviewBitmap != null) {
                                            Image(
                                                bitmap = widgetBgPreviewBitmap,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .matchParentSize()
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                        }
                                        Text(
                                            text = label.ifBlank { component.label },
                                            color = liveTextColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 10.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Comfortable Horizontal Strip for Widget Background — Default Colors & Transparent
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Palette,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Widget Background — Colors & Transparent",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "$bgHex ($currentOpacityPercent%)",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DefaultWidgetBgPresets.forEach { (presetHex, presetName) ->
                                    val isTransparentChip = presetHex.equals("#00000000", ignoreCase = true)
                                    val isSelectedBg = if (isTransparentChip) {
                                        currentOpacityPercent == 0
                                    } else {
                                        bgHex.equals(presetHex, ignoreCase = true) ||
                                            (currentOpacityPercent > 0 && presetHex.length == 7 &&
                                                extractBaseRgb6(bgHex).equals(presetHex.removePrefix("#"), ignoreCase = true))
                                    }
                                    val swatchColor = parseHexColorSafe(presetHex, Color(0xFF334155))
                                    val slug = presetHex.removePrefix("#").lowercase(Locale.US)
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelectedBg) Color(0xFF1E293B) else InspectorFieldBg,
                                        border = BorderStroke(
                                            width = if (isSelectedBg) 1.5.dp else 1.dp,
                                            color = if (isSelectedBg) Color(0xFF38BDF8) else InspectorFieldBorder
                                        ),
                                        modifier = Modifier
                                            .clickable { applyDefaultWidgetBgPreset(presetHex) }
                                            .testTag("widget_bg_preset_$slug")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clip(CircleShape)
                                                    .background(swatchColor)
                                                    .border(BorderStroke(1.dp, Color(0xFF94A3B8)), CircleShape)
                                            )
                                            Text(
                                                text = presetName,
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelectedBg) FontWeight.ExtraBold else FontWeight.Medium,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2B. Background Transparency / Opacity Slider & Presets (Make any selected color transparent!)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Background Transparency / Opacity",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (currentOpacityPercent == 0) "100% Transparent" else "$currentOpacityPercent% Opacity",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                QuickOpacityPresets.forEach { (pct, labelText) ->
                                    val isSelectedOpacity = currentOpacityPercent == pct
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelectedOpacity) Color(0xFF1E293B) else InspectorFieldBg,
                                        border = BorderStroke(
                                            width = if (isSelectedOpacity) 1.5.dp else 1.dp,
                                            color = if (isSelectedOpacity) Color(0xFF38BDF8) else InspectorFieldBorder
                                        ),
                                        modifier = Modifier
                                            .clickable { applyWidgetBgOpacityPercent(pct) }
                                            .testTag("style_widget_opacity_$pct")
                                    ) {
                                        Text(
                                            text = labelText,
                                            color = if (isSelectedOpacity) Color(0xFF38BDF8) else Color(0xFFE2E8F0),
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelectedOpacity) FontWeight.ExtraBold else FontWeight.Medium,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }

                            Slider(
                                value = currentOpacityPercent.toFloat(),
                                onValueChange = { newVal ->
                                    applyWidgetBgOpacityPercent(newVal.roundToInt())
                                },
                                valueRange = 0f..100f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF38BDF8),
                                    activeTrackColor = InspectorPurpleButton,
                                    inactiveTrackColor = InspectorFieldBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .testTag("widget_bg_opacity_slider")
                            )
                        }

                        // 3. Widget Text — Default Colors Strip
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                text = "Widget Text — Default Colors",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DefaultWidgetTextPresets.forEach { (presetHex, presetName) ->
                                    val isSelectedTxt = textHex.equals(presetHex, ignoreCase = true)
                                    val swatchColor = parseHexColorSafe(presetHex, Color.White)
                                    val slug = presetHex.removePrefix("#").lowercase(Locale.US)
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelectedTxt) Color(0xFF1E293B) else InspectorFieldBg,
                                        border = BorderStroke(
                                            width = if (isSelectedTxt) 1.5.dp else 1.dp,
                                            color = if (isSelectedTxt) Color(0xFF38BDF8) else InspectorFieldBorder
                                        ),
                                        modifier = Modifier
                                            .clickable {
                                                textHex = presetHex
                                                onSaveComponent(buildUpdated(overrideTextHex = presetHex))
                                            }
                                            .testTag("widget_text_preset_$slug")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(13.dp)
                                                    .clip(CircleShape)
                                                    .background(swatchColor)
                                                    .border(BorderStroke(1.dp, Color(0xFF94A3B8)), CircleShape)
                                            )
                                            Text(
                                                text = presetName,
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelectedTxt) FontWeight.ExtraBold else FontWeight.Medium,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Custom BG Hex & Text Hex Inputs (Scroll down to view)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("BG Hex", color = Color(0xFFCBD5E1), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = bgHex,
                                    onValueChange = {
                                        bgHex = it
                                        onSaveComponent(buildUpdated(overrideBgHex = it))
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = InspectorFieldBg,
                                        unfocusedContainerColor = InspectorFieldBg,
                                        focusedBorderColor = Color(0xFF3B82F6),
                                        unfocusedBorderColor = InspectorFieldBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("inspector_bg_hex_input")
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Text Hex", color = Color(0xFFCBD5E1), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = textHex,
                                    onValueChange = {
                                        textHex = it
                                        onSaveComponent(buildUpdated(overrideTextHex = it))
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = InspectorFieldBg,
                                        unfocusedContainerColor = InspectorFieldBg,
                                        focusedBorderColor = Color(0xFF3B82F6),
                                        unfocusedBorderColor = InspectorFieldBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("inspector_text_hex_input")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Width (dp)", color = Color(0xFFCBD5E1), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = widthDp,
                                    onValueChange = {
                                        widthDp = it
                                        onSaveComponent(buildUpdated())
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = InspectorFieldBg,
                                        unfocusedContainerColor = InspectorFieldBg,
                                        focusedBorderColor = Color(0xFF3B82F6),
                                        unfocusedBorderColor = InspectorFieldBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("inspector_width_input")
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Height (dp)", color = Color(0xFFCBD5E1), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = heightDp,
                                    onValueChange = {
                                        heightDp = it
                                        onSaveComponent(buildUpdated())
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = InspectorFieldBg,
                                        unfocusedContainerColor = InspectorFieldBg,
                                        focusedBorderColor = Color(0xFF3B82F6),
                                        unfocusedBorderColor = InspectorFieldBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("inspector_height_input")
                                )
                            }
                        }
                    }

                    "Advanced" -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val updated = buildUpdated()
                                    onTriggerLive(updated, updated.currentValue)
                                },
                                border = BorderStroke(1.dp, InspectorFieldBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("inspector_test_live_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Test Write", color = Color.White, fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { onDuplicateComponent(buildUpdated()) },
                                border = BorderStroke(1.dp, InspectorFieldBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Duplicate", color = Color.White, fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { onDeleteComponent(component) },
                                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Delete", color = Color(0xFFF87171), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
