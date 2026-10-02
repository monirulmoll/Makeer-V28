package com.example.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SyncAlt
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

private val BuiltInVoiceAndSoundPresets = listOf(
    "VOICE_ON" to "🗣️ Voice: Option Activated",
    "VOICE_OFF" to "🗣️ Voice: Option Deactivated",
    "VOICE_HACK_ON" to "🗣️ Voice: Hack Activated",
    "VOICE_HACK_OFF" to "🗣️ Voice: Hack Deactivated",
    "VOICE_MOD_ON" to "🗣️ Voice: Mod Enabled",
    "VOICE_MOD_OFF" to "🗣️ Voice: Mod Disabled",
    "ACTIVATE" to "🗣️ Voice: Target File Patched",
    "DEACTIVATE" to "🗣️ Voice: Original Restored",
    "SYSTEM_CLICK" to "👆 Click Sound",
    "SWITCH_POP" to "🔘 Switch Pop",
    "CONFIRM_TONE" to "✅ Single Beep",
    "DIGITAL_BEEP" to "🔔 Double Beep",
    "NONE" to "🔇 Silent (None)",
    "CUSTOM_FILE" to "🎵 Custom Voice"
)

private val DefaultWidgetBorderColorPresets = listOf(
    "#00000000" to "Transparent",
    "#38BDF8" to "Neon Cyan",
    "#10B981" to "Emerald",
    "#8B5CF6" to "Electric Purple",
    "#EF4444" to "Crimson Red",
    "#FACC15" to "Cyber Gold",
    "#EC4899" to "Neon Pink",
    "#FFFFFF" to "Pure White",
    "#0F172A" to "Dark Slate"
)

private val WidgetBorderAnimationPresets = listOf(
    "NONE" to "Static (No Anim)",
    "RGB_LIGHT" to "🌈 RGB Light",
    "RAINBOW" to "🎨 Rainbow RGB",
    "PULSE" to "💓 Pulse Breath",
    "NEON_BLINK" to "⚡ Neon Blink",
    "GLOW" to "✨ Soft Glow"
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
        onPickSoundUri = onPickSoundUri,
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
    onPickSoundUri: (Uri, Boolean) -> Unit = { _, _ -> },
    onOpenEditFloatingPanel: () -> Unit = {},
    onSaveComponent: (CanvasComponentEntity) -> Unit,
    onDuplicateComponent: (CanvasComponentEntity) -> Unit,
    onDeleteComponent: (CanvasComponentEntity) -> Unit,
    onTriggerLive: (CanvasComponentEntity, String) -> Unit,
    onOpenCodeEditor: () -> Unit,
    onClose: () -> Unit
) {
    if (com.example.data.isScreen1WidgetType(component.type)) {
        Screen1WidgetPropertyInspectorSheet(
            component = component,
            onSaveComponent = onSaveComponent,
            onDuplicateComponent = onDuplicateComponent,
            onDeleteComponent = onDeleteComponent,
            onClose = onClose
        )
        return
    }

    val context = LocalContext.current
    var activeTab by remember { mutableStateOf("Main") }
    var label by remember(component.id) { mutableStateOf(component.label) }
    var customSourceFilePath by remember(component.id, component.customImagePath) {
        mutableStateOf(component.customImagePath)
    }
    var bgImagePath by remember(component.id, component.bgImagePath) {
        mutableStateOf(component.bgImagePath)
    }
    var posX by remember(component.id, component.posXDp) { mutableStateOf(component.posXDp.toString()) }
    var posY by remember(component.id, component.posYDp) { mutableStateOf(component.posYDp.toString()) }
    var widthDp by remember(component.id, component.widthDp) { mutableStateOf(component.widthDp.toString()) }
    var heightDp by remember(component.id, component.heightDp) { mutableStateOf(component.heightDp.toString()) }
    var byteOffset by remember(component.id) { mutableStateOf(component.byteOffsetHex) }
    var onPayload by remember(component.id) { mutableStateOf(component.onPayloadHex) }
    var offPayload by remember(component.id) { mutableStateOf(component.offPayloadHex) }
    var bgHex by remember(component.id) { mutableStateOf(component.bgColorHex) }
    var textHex by remember(component.id) { mutableStateOf(component.textColorHex) }
    var currentValue by remember(component.id) { mutableStateOf(component.currentValue) }
    var targetFile by remember(component.id) { mutableStateOf(component.targetFilePath) }
    var linkUrl by remember(component.id, component.linkUrl) { mutableStateOf(component.linkUrl) }
    var soundTrigger by remember(component.id, component.soundTrigger) { mutableStateOf(component.soundTrigger) }
    var customSoundPath by remember(component.id, component.customSoundPath) { mutableStateOf(component.customSoundPath) }
    var offSoundTrigger by remember(component.id, component.offSoundTrigger) { mutableStateOf(component.offSoundTrigger) }
    var offCustomSoundPath by remember(component.id, component.offCustomSoundPath) { mutableStateOf(component.offCustomSoundPath) }
    var borderColorHex by remember(component.id, component.borderColorHex) { mutableStateOf(component.borderColorHex) }
    var borderStrokePercent by remember(component.id, component.borderStrokePercent) { mutableStateOf(component.borderStrokePercent) }
    var borderAnimation by remember(component.id, component.borderAnimation) { mutableStateOf(component.borderAnimation) }

    val isTextViewWidget = component.type == "TEXT"
    val isLinkOpenerWidget = component.type == "LINK" || component.type == "IMAGE"
    val isExecutableFileWidget = !isTextViewWidget && !isLinkOpenerWidget

    fun buildUpdated(
        overrideBgHex: String = bgHex,
        overrideTextHex: String = textHex,
        overrideBgImagePath: String = bgImagePath,
        overrideBorderColorHex: String = borderColorHex,
        overrideBorderStrokePercent: Int = borderStrokePercent,
        overrideBorderAnimation: String = borderAnimation,
        overrideSoundTrigger: String = soundTrigger,
        overrideCustomSoundPath: String = customSoundPath,
        overrideOffSoundTrigger: String = offSoundTrigger,
        overrideOffCustomSoundPath: String = offCustomSoundPath,
        overrideLinkUrl: String = linkUrl,
        overrideByteOffsetHex: String = byteOffset
    ): CanvasComponentEntity {
        return component.copy(
            label = label.trim().ifEmpty { component.label },
            customImagePath = customSourceFilePath.trim(),
            bgImagePath = overrideBgImagePath.trim(),
            posXDp = posX.toIntOrNull()?.coerceAtLeast(0) ?: component.posXDp,
            posYDp = posY.toIntOrNull()?.coerceAtLeast(0) ?: component.posYDp,
            widthDp = widthDp.toIntOrNull()?.coerceIn(36, 400) ?: component.widthDp,
            heightDp = heightDp.toIntOrNull()?.coerceIn(28, 300) ?: component.heightDp,
            byteOffsetHex = overrideByteOffsetHex.trim().ifEmpty { "REPLACE" },
            onPayloadHex = onPayload.trim().ifEmpty { "On" },
            offPayloadHex = offPayload.trim().ifEmpty { "Off" },
            bgColorHex = overrideBgHex.trim().ifEmpty { "#131C33" },
            textColorHex = overrideTextHex.trim().ifEmpty { "#FFFFFF" },
            currentValue = currentValue.trim(),
            targetFilePath = targetFile.trim(),
            linkUrl = overrideLinkUrl.trim(),
            soundTrigger = overrideSoundTrigger.trim().ifEmpty { "NONE" },
            customSoundPath = overrideCustomSoundPath.trim(),
            offSoundTrigger = overrideOffSoundTrigger.trim().ifEmpty { "NONE" },
            offCustomSoundPath = overrideOffCustomSoundPath.trim(),
            borderColorHex = overrideBorderColorHex.trim().ifEmpty { "#38BDF8" },
            borderStrokePercent = overrideBorderStrokePercent.coerceIn(0, 100),
            borderAnimation = overrideBorderAnimation.trim().ifEmpty { "NONE" }
        )
    }

    fun copyPickedAudioUriToLocalFile(uri: Uri, isOffSound: Boolean): File? {
        return try {
            val soundDir = File(context.filesDir, "component_sounds").apply { mkdirs() }
            val tag = if (isOffSound) "off" else "on"
            var rawName = ""
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && cursor.moveToFirst()) {
                        rawName = cursor.getString(idx) ?: ""
                    }
                }
            } catch (_: Exception) {
            }
            if (rawName.isBlank()) {
                rawName = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: ""
            }
            val cleanName = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .ifBlank { "voice_${tag}_${component.id}_${System.currentTimeMillis()}.mp3" }
            val destFile = File(soundDir, "${tag}_${component.id}_$cleanName")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (destFile.exists() && destFile.length() > 0L) destFile else null
        } catch (_: Exception) {
            null
        }
    }

    val onVoiceFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val copied = copyPickedAudioUriToLocalFile(uri, isOffSound = false)
            if (copied != null) {
                soundTrigger = "CUSTOM_FILE"
                customSoundPath = copied.absolutePath
                onSaveComponent(
                    buildUpdated(
                        overrideSoundTrigger = "CUSTOM_FILE",
                        overrideCustomSoundPath = copied.absolutePath
                    )
                )
                com.example.engine.SoundTriggerPlayer.playSoundTrigger(
                    context,
                    null,
                    "CUSTOM_FILE",
                    copied.absolutePath
                )
            } else {
                soundTrigger = "CUSTOM_FILE"
                onPickSoundUri(uri, false)
            }
        }
    }

    val offVoiceFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val copied = copyPickedAudioUriToLocalFile(uri, isOffSound = true)
            if (copied != null) {
                offSoundTrigger = "CUSTOM_FILE"
                offCustomSoundPath = copied.absolutePath
                onSaveComponent(
                    buildUpdated(
                        overrideOffSoundTrigger = "CUSTOM_FILE",
                        overrideOffCustomSoundPath = copied.absolutePath
                    )
                )
                com.example.engine.SoundTriggerPlayer.playSoundTrigger(
                    context,
                    null,
                    "CUSTOM_FILE",
                    copied.absolutePath
                )
            } else {
                offSoundTrigger = "CUSTOM_FILE"
                onPickSoundUri(uri, true)
            }
        }
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

    // Folder picker for Copy mode so users can directly pick a Target Folder
    val targetFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
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

            // Pinned 3 Tabs: Main | Style | Advanced
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Main", "Style", "Advanced").forEach { tab ->
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
                            .testTag("inspector_tab_${tab.lowercase(Locale.US)}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = tab,
                                color = if (isSelected) Color.White else Color(0xFFE2E8F0),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            @Composable
            fun renderVoiceSection() {
                val onCustomSoundName = remember(customSoundPath) {
                    if (customSoundPath.isNotBlank()) File(customSoundPath).name else ""
                }
                val offCustomSoundName = remember(offCustomSoundPath) {
                    if (offCustomSoundPath.isNotBlank()) File(offCustomSoundPath).name else ""
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF09152B))
                        .border(BorderStroke(1.dp, Color(0xFF1E3A5F)), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Voice",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    // 1. ON VOICE SECTION
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ON Voice",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, Color(0xFF10B981)),
                            modifier = Modifier
                                .clickable {
                                    com.example.engine.SoundTriggerPlayer.playSoundTrigger(
                                        context,
                                        null,
                                        soundTrigger,
                                        customSoundPath
                                    )
                                }
                                .testTag("inspector_preview_on_sound_button")
                        ) {
                            Text(
                                text = "▶ Test ON Voice",
                                color = Color(0xFF6EE7B7),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Select ON Voice Box + Purple Folder Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = InspectorFieldBg,
                            border = BorderStroke(
                                1.dp,
                                if (onCustomSoundName.isNotBlank()) Color(0xFF10B981) else InspectorFieldBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clickable {
                                    onVoiceFilePickerLauncher.launch(arrayOf("audio/*", "*/*"))
                                }
                                .testTag("inspector_select_on_voice_file_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = if (onCustomSoundName.isNotBlank()) {
                                        "🎵 Voice: $onCustomSoundName"
                                    } else {
                                        "Select ON Voice"
                                    },
                                    color = if (onCustomSoundName.isNotBlank()) Color(0xFF6EE7B7) else Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(InspectorPurpleButton)
                                .clickable {
                                    onVoiceFilePickerLauncher.launch(arrayOf("audio/*", "*/*"))
                                }
                                .testTag("inspector_on_voice_folder_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Select ON Voice",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Text(
                        text = "Default ON Voice:",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Horizontal strip of Built-in Default Voices for ON
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BuiltInVoiceAndSoundPresets.forEach { (code, title) ->
                            val selected = soundTrigger.equals(code, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) InspectorPurpleButton else InspectorFieldBg,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) Color(0xFF60A5FA) else InspectorFieldBorder
                                ),
                                modifier = Modifier
                                    .clickable {
                                        soundTrigger = code
                                        onSaveComponent(buildUpdated(overrideSoundTrigger = code))
                                        com.example.engine.SoundTriggerPlayer.playSoundTrigger(
                                            context,
                                            null,
                                            code,
                                            customSoundPath
                                        )
                                    }
                                    .testTag("on_sound_preset_${code.lowercase(Locale.US)}")
                            ) {
                                Text(
                                    text = title,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    // 2. OFF VOICE SECTION
                    Spacer(Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "OFF Voice",
                            color = Color(0xFFF87171),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, Color(0xFFEF4444)),
                            modifier = Modifier
                                .clickable {
                                    com.example.engine.SoundTriggerPlayer.playSoundTrigger(
                                        context,
                                        null,
                                        offSoundTrigger,
                                        offCustomSoundPath
                                    )
                                }
                                .testTag("inspector_preview_off_sound_button")
                        ) {
                            Text(
                                text = "▶ Test OFF Voice",
                                color = Color(0xFFFCA5A5),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Select OFF Voice Box + Red Folder Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = InspectorFieldBg,
                            border = BorderStroke(
                                1.dp,
                                if (offCustomSoundName.isNotBlank()) Color(0xFFEF4444) else InspectorFieldBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clickable {
                                    offVoiceFilePickerLauncher.launch(arrayOf("audio/*", "*/*"))
                                }
                                .testTag("inspector_select_off_voice_file_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = if (offCustomSoundName.isNotBlank()) {
                                        "🎵 Voice: $offCustomSoundName"
                                    } else {
                                        "Select OFF Voice"
                                    },
                                    color = if (offCustomSoundName.isNotBlank()) Color(0xFFFCA5A5) else Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFDC2626))
                                .clickable {
                                    offVoiceFilePickerLauncher.launch(arrayOf("audio/*", "*/*"))
                                }
                                .testTag("inspector_off_voice_folder_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Select OFF Voice",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Text(
                        text = "Default OFF Voice:",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BuiltInVoiceAndSoundPresets.forEach { (code, title) ->
                            val selected = offSoundTrigger.equals(code, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) Color(0xFFDC2626) else InspectorFieldBg,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) Color(0xFFFCA5A5) else InspectorFieldBorder
                                ),
                                modifier = Modifier
                                    .clickable {
                                        offSoundTrigger = code
                                        onSaveComponent(buildUpdated(overrideOffSoundTrigger = code))
                                        com.example.engine.SoundTriggerPlayer.playSoundTrigger(
                                            context,
                                            null,
                                            code,
                                            offCustomSoundPath
                                        )
                                    }
                                    .testTag("off_sound_preset_${code.lowercase(Locale.US)}")
                            ) {
                                Text(
                                    text = title,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
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
                    "Main" -> {
                        val selectedFileName = remember(customSourceFilePath) {
                            if (customSourceFilePath.isNotBlank()) {
                                File(customSourceFilePath).name.ifBlank { customSourceFilePath }
                            } else {
                                ""
                            }
                        }

                        // 1. Widget Name / Text
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (isTextViewWidget) "Display Text (Text View)" else "Widget Name",
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
                                    Text(
                                        if (isTextViewWidget) "Enter text to show..." else "Widget #1",
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
                                    .fillMaxWidth()
                                    .testTag("inspector_label_input")
                            )
                        }

                        // LINK OPENER ONLY: Link URL input (no Main File or Target Path!)
                        if (isLinkOpenerWidget) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Link URL (Floating Window me click krte hi open hoga)",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedTextField(
                                    value = linkUrl,
                                    onValueChange = {
                                        linkUrl = it
                                        onSaveComponent(buildUpdated(overrideLinkUrl = it))
                                    },
                                    placeholder = {
                                        Text(
                                            "https://youtube.com or https://t.me/yourchannel",
                                            color = InspectorPlaceholderColor,
                                            fontSize = 12.sp
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = InspectorFieldBg,
                                        unfocusedContainerColor = InspectorFieldBg,
                                        focusedBorderColor = Color(0xFF38BDF8),
                                        unfocusedBorderColor = InspectorFieldBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("inspector_link_url_input")
                                )
                                if (linkUrl.isBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF28111B),
                                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "⚠️ Link URL Nhi Dala Hua: Floating window me Link Opener par click karne par link open karne ke liye upar URL dalein.",
                                            color = Color(0xFFFCA5A5),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // TEXT VIEW ONLY: Info banner (no Main File or Target Path!)
                        if (isTextViewWidget) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF09152B),
                                border = BorderStroke(1.dp, Color(0xFF1E3A5F)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "ℹ️ Text View srif text dikhane ke liye hai (isme Main File ya Target Path ki zaroorat nhi hai).",
                                    color = Color(0xFFBAE6FD),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        // EXECUTABLE WIDGETS ONLY (SWITCH, BUTTON, SLIDER 0-100, EDIT TEXT):
                        // Action Mode (Replace / Copy) + 2. Select your main file & 3. Target Path + Live Validation Error Box
                        if (isExecutableFileWidget) {
                            val isCopyActionMode = byteOffset.trim().equals("COPY", ignoreCase = true)

                            // Action Mode Selector: Replace vs Copy
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Action Mode (Replace / Copy)",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Option 1: Replace
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (!isCopyActionMode) InspectorPurpleButton else InspectorFieldBg,
                                        border = BorderStroke(
                                            1.5.dp,
                                            if (!isCopyActionMode) Color(0xFF60A5FA) else InspectorFieldBorder
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                byteOffset = "REPLACE"
                                                onSaveComponent(buildUpdated(overrideByteOffsetHex = "REPLACE"))
                                            }
                                            .testTag("inspector_mode_replace_button")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.SyncAlt,
                                                    contentDescription = "Replace",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Text(
                                                    text = "Replace",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                            Text(
                                                text = "Target file ko Main file se replace karega",
                                                color = if (!isCopyActionMode) Color(0xFFE0E7FF) else Color(0xFF94A3B8),
                                                fontSize = 9.5.sp,
                                                lineHeight = 12.sp
                                            )
                                        }
                                    }

                                    // Option 2: Copy
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isCopyActionMode) InspectorPurpleButton else InspectorFieldBg,
                                        border = BorderStroke(
                                            1.5.dp,
                                            if (isCopyActionMode) Color(0xFF60A5FA) else InspectorFieldBorder
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                byteOffset = "COPY"
                                                onSaveComponent(buildUpdated(overrideByteOffsetHex = "COPY"))
                                            }
                                            .testTag("inspector_mode_copy_button")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Copy",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Text(
                                                    text = "Copy",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                            Text(
                                                text = "Main file ko Target path par copy karega",
                                                color = if (isCopyActionMode) Color(0xFFE0E7FF) else Color(0xFF94A3B8),
                                                fontSize = 9.5.sp,
                                                lineHeight = 12.sp
                                            )
                                        }
                                    }
                                }
                            }

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
                                    text = if (isCopyActionMode) "Target Path (Folder or File Path to Copy Into)" else "Target Path (Target File to Replace)",
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
                                                if (isCopyActionMode) "/storage/emulated/0/Download/..." else "/storage/emulated/0/Android/data/...",
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

                                    if (isCopyActionMode) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF0284C7))
                                                .clickable {
                                                    targetFolderPickerLauncher.launch(null)
                                                }
                                                .testTag("inspector_select_target_folder_button"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CreateNewFolder,
                                                contentDescription = "Select Target Folder",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

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

                                // Live Target File & Main File Validation Error / Explanation Box
                                val cleanTarget = targetFile.trim()
                                val cleanMain = customSourceFilePath.trim()
                                val isRestrictedAndroidPath = remember(cleanTarget) {
                                    ShizukuPrivilegeBridge.isRestrictedAndroidPath(cleanTarget)
                                }
                                val shizukuReady = ShizukuPrivilegeBridge.isShizukuReady()
                                val shizukuStatusText = ShizukuPrivilegeBridge.getStatusSummary(context)

                                val validationErrorMessage: String? = remember(cleanTarget, cleanMain, component.type, isCopyActionMode) {
                                    val needsMainFile = component.type == "TOGGLE" || component.type == "BUTTON"
                                    val mainMissing = needsMainFile && (cleanMain.isEmpty() || !File(cleanMain).exists())
                                    val targetMissing = cleanTarget.isEmpty()
                                    when {
                                        mainMissing && targetMissing ->
                                            "⚠️ Main File & Target Path Dono Missing Hain: Aapne na Main File select ki hai aur na hi Target Path dala hai. Floating window me ye widget ON karne par fail hoga."
                                        mainMissing ->
                                            if (isCopyActionMode) {
                                                "⚠️ Main File Select Nhi Ki Gayi: Upar 'Select your main file' me wo file select karein jo Target Path par copy hogi."
                                            } else {
                                                "⚠️ Main File Select Nhi Ki Gayi: Upar 'Select your main file' me wo file select karein jo Target Path wali file ko replace karegi."
                                            }
                                        targetMissing ->
                                            "⚠️ Target Path Nhi Dala Hua: Target Path khali hai. Jis path par file ${if (isCopyActionMode) "copy" else "replace"} karni hai wo path dalein."
                                        !cleanTarget.startsWith("/") ->
                                            "⚠️ Galat Target Path: '$cleanTarget' sahi path nhi hai. Path '/' se shuru hona chahiye (jaise /storage/emulated/0/...)."
                                        !isRestrictedAndroidPath && !isCopyActionMode -> {
                                            val tf = File(cleanTarget)
                                            val parent = tf.parentFile
                                            if (parent == null || !parent.exists()) {
                                                "⚠️ Target Folder Exist Nhi Karta: '${parent?.absolutePath ?: cleanTarget}' aapke device me mojood nhi hai."
                                            } else if (!tf.exists()) {
                                                "⚠️ Target File Exist Nhi Karta: '$cleanTarget' is path par abhi koi file mojood nhi hai (Agar nayi file copy karni hai toh upar 'Copy' select karein)."
                                            } else if (tf.isDirectory) {
                                                "⚠️ Target Path Ek Folder Hai: '$cleanTarget' folder hai. Folder me file copy karne ke liye upar 'Copy' option select karein, ya Replace ke liye file ka pura path dalein."
                                            } else {
                                                null
                                            }
                                        }
                                        else -> null
                                    }
                                }

                                if (validationErrorMessage != null) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF28111B),
                                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("inspector_target_validation_error_card")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                text = "ERROR: AKHIR KYU KAAM NHI KAREGA",
                                                color = Color(0xFFFCA5A5),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            Text(
                                                text = validationErrorMessage,
                                                color = Color(0xFFFEE2E2),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

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
                                                        "Android 15 Restricted Path (Shizuku Required)"
                                                    } else if (isCopyActionMode) {
                                                        "Executes Only in Floating Window (ON=Copy to Target, OFF=Remove/Restore)"
                                                    } else {
                                                        "Executes Only in Floating Window (ON=Replace Target, OFF=Restore)"
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

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF7C3AED).copy(alpha = 0.28f),
                                            border = BorderStroke(1.dp, Color(0xFFA78BFA)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(32.dp)
                                                .clickable {
                                                    ShizukuPrivilegeBridge.probeShizukuBinder(context)
                                                    if (!ShizukuPrivilegeBridge.isShizukuReady()) {
                                                        if (ShizukuPrivilegeBridge.isShizukuRunning()) {
                                                            ShizukuPrivilegeBridge.requestPermission(1401)
                                                        } else {
                                                            ShizukuPrivilegeBridge.openOrDownloadShizukuApp(context)
                                                        }
                                                    }
                                                }
                                                .testTag("inspector_shizuku_fix_button")
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = if (shizukuReady) "Shizuku Ready ✓ (Restricted Paths Unlocked)" else "Connect Shizuku for Android 15 Restricted Paths",
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
                    }

                    "Style" -> {
                        val liveBgColor = parseHexColorSafe(bgHex, Color(0xFF334155))
                        val liveTextColor = parseHexColorSafe(textHex, Color.White)

                        val inspectorAnimTransition = rememberInfiniteTransition(label = "inspectorBorderAnim")
                        val inspectorRgbAngle by inspectorAnimTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 1800, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "inspectorRgbAngle"
                        )
                        val inspectorPulse by inspectorAnimTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 700, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "inspectorPulse"
                        )
                        val inspectorBlink by inspectorAnimTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 380, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "inspectorBlink"
                        )

                        // 0. Voice Section (Exclusively in Style tab)
                        renderVoiceSection()

                        // 0B. Widget Corner Border Line Customization (Exclusively in Style tab)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF09152B))
                                .drawAnimatedWidgetCornerBorder(
                                    borderColorHex = borderColorHex,
                                    borderStrokePercent = borderStrokePercent,
                                    borderAnimation = borderAnimation,
                                    cornerRadiusDp = 12f,
                                    rgbSweepAngleDeg = inspectorRgbAngle,
                                    pulseProgress = inspectorPulse,
                                    blinkProgress = inspectorBlink,
                                    isSelectedFallback = true
                                )
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Widget Corner Border Line (0 to 100)",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = if (borderAnimation == "NONE" && (borderStrokePercent == 0 || borderColorHex.equals("#00000000", ignoreCase = true))) {
                                        "Transparent (0)"
                                    } else {
                                        "Size: $borderStrokePercent / 100 • $borderAnimation"
                                    },
                                    color = Color(0xFF6EE7B7),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Slider(
                                value = borderStrokePercent.toFloat(),
                                onValueChange = { newVal ->
                                    val nextPct = newVal.roundToInt().coerceIn(0, 100)
                                    borderStrokePercent = nextPct
                                    val resolvedColor = if (nextPct > 0 && borderColorHex.equals("#00000000", ignoreCase = true)) {
                                        "#38BDF8".also { borderColorHex = it }
                                    } else {
                                        borderColorHex
                                    }
                                    onSaveComponent(
                                        buildUpdated(
                                            overrideBorderColorHex = resolvedColor,
                                            overrideBorderStrokePercent = nextPct
                                        )
                                    )
                                },
                                valueRange = 0f..100f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF38BDF8),
                                    activeTrackColor = InspectorPurpleButton,
                                    inactiveTrackColor = InspectorFieldBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(26.dp)
                                    .testTag("inspector_border_width_slider")
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DefaultWidgetBorderColorPresets.forEach { (presetHex, presetName) ->
                                    val isSelectedBorder = borderColorHex.equals(presetHex, ignoreCase = true)
                                    val swatchColor = parseHexColorSafe(presetHex, Color.Transparent)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelectedBorder) Color(0xFF1E293B) else InspectorFieldBg,
                                        border = BorderStroke(
                                            width = if (isSelectedBorder) 1.5.dp else 1.dp,
                                            color = if (isSelectedBorder) Color(0xFF38BDF8) else InspectorFieldBorder
                                        ),
                                        modifier = Modifier
                                            .clickable {
                                                borderColorHex = presetHex
                                                val nextStroke = if (presetHex == "#00000000") 0 else borderStrokePercent.coerceAtLeast(25)
                                                borderStrokePercent = nextStroke
                                                val nextAnim = if (presetHex == "#00000000") "NONE" else borderAnimation
                                                borderAnimation = nextAnim
                                                onSaveComponent(
                                                    buildUpdated(
                                                        overrideBorderColorHex = presetHex,
                                                        overrideBorderStrokePercent = nextStroke,
                                                        overrideBorderAnimation = nextAnim
                                                    )
                                                )
                                            }
                                            .testTag("border_color_preset_${presetHex.removePrefix("#").lowercase(Locale.US)}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(swatchColor)
                                                    .border(BorderStroke(1.dp, Color(0xFF94A3B8)), CircleShape)
                                            )
                                            Text(
                                                text = presetName,
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelectedBorder) FontWeight.ExtraBold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                WidgetBorderAnimationPresets.forEach { (animCode, animLabel) ->
                                    val isSelectedAnim = borderAnimation.equals(animCode, ignoreCase = true)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelectedAnim) InspectorPurpleButton else InspectorFieldBg,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelectedAnim) Color(0xFF60A5FA) else InspectorFieldBorder
                                        ),
                                        modifier = Modifier
                                            .clickable {
                                                borderAnimation = animCode
                                                val nextStroke = if (animCode != "NONE" && borderStrokePercent < 30) {
                                                    35
                                                } else {
                                                    borderStrokePercent
                                                }
                                                val nextBorderColor = if (animCode != "NONE" && borderColorHex.equals("#00000000", ignoreCase = true)) {
                                                    "#38BDF8"
                                                } else {
                                                    borderColorHex
                                                }
                                                borderStrokePercent = nextStroke
                                                borderColorHex = nextBorderColor
                                                onSaveComponent(
                                                    buildUpdated(
                                                        overrideBorderColorHex = nextBorderColor,
                                                        overrideBorderStrokePercent = nextStroke,
                                                        overrideBorderAnimation = animCode
                                                    )
                                                )
                                            }
                                            .testTag("border_anim_preset_${animCode.lowercase(Locale.US)}")
                                    ) {
                                        Text(
                                            text = animLabel,
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelectedAnim) FontWeight.ExtraBold else FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }

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
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .drawAnimatedWidgetCornerBorder(
                                                borderColorHex = borderColorHex,
                                                borderStrokePercent = borderStrokePercent,
                                                borderAnimation = borderAnimation,
                                                cornerRadiusDp = 8f,
                                                rgbSweepAngleDeg = inspectorRgbAngle,
                                                pulseProgress = inspectorPulse,
                                                blinkProgress = inspectorBlink,
                                                isSelectedFallback = true
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
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
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0C1E38),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🛡️ Safe Studio Preview Mode: Widgets never execute or replace target files inside the Studio preview. File replacement and link opening happen strictly after launching the Floating Window (FLOAT).",
                                color = Color(0xFFBAE6FD),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Position X (dp)", color = Color(0xFFCBD5E1), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = posX,
                                    onValueChange = {
                                        posX = it
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
                                        .testTag("inspector_pos_x_input")
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Position Y (dp)", color = Color(0xFFCBD5E1), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = posY,
                                    onValueChange = {
                                        posY = it
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
                                        .testTag("inspector_pos_y_input")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onDuplicateComponent(buildUpdated()) },
                                border = BorderStroke(1.dp, InspectorFieldBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("inspector_duplicate_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Duplicate Widget", color = Color.White, fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { onDeleteComponent(component) },
                                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("inspector_delete_button")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Delete Widget", color = Color(0xFFF87171), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Screen1WidgetPropertyInspectorSheet(
    component: CanvasComponentEntity,
    onSaveComponent: (CanvasComponentEntity) -> Unit,
    onDuplicateComponent: (CanvasComponentEntity) -> Unit,
    onDeleteComponent: (CanvasComponentEntity) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val typeUpper = component.type.trim().uppercase()
    val isStartOrStop = typeUpper == "S1_START" || typeUpper == "S1_STOP"
    val isTextView = typeUpper == "S1_TEXT"
    val isLinkOpen = typeUpper == "S1_LINK"
    val isImageView = typeUpper == "S1_IMAGE"

    var label by remember(component.id) { mutableStateOf(component.label) }
    var bgHex by remember(component.id) {
        mutableStateOf(
            if (isTextView || isLinkOpen || isImageView) "#00000000" else component.bgColorHex
        )
    }
    var textHex by remember(component.id) {
        mutableStateOf(
            if ((isTextView || isLinkOpen) && component.textColorHex.equals("#00000000", ignoreCase = true)) {
                "#FFFFFF"
            } else {
                component.textColorHex
            }
        )
    }
    var bgImagePath by remember(component.id, component.bgImagePath) {
        mutableStateOf(component.bgImagePath)
    }
    var customImagePath by remember(component.id, component.customImagePath) {
        mutableStateOf(component.customImagePath)
    }
    var linkUrl by remember(component.id, component.linkUrl) {
        mutableStateOf(component.linkUrl)
    }
    var posX by remember(component.id, component.posXDp) { mutableStateOf(component.posXDp) }
    var posY by remember(component.id, component.posYDp) { mutableStateOf(component.posYDp) }
    var widthDp by remember(component.id, component.widthDp) { mutableStateOf(component.widthDp) }
    var heightDp by remember(component.id, component.heightDp) { mutableStateOf(component.heightDp) }

    val matchingDhanche = remember(typeUpper) {
        Screen1RecommendedDhancheSlots.find { it.widgetType.equals(typeUpper, ignoreCase = true) }
    }

    fun buildScreen1Updated(
        overrideLabel: String = label,
        overrideBgHex: String = bgHex,
        overrideTextHex: String = textHex,
        overrideBgImagePath: String = bgImagePath,
        overrideCustomImagePath: String = customImagePath,
        overrideLinkUrl: String = linkUrl,
        overridePosX: Int = posX,
        overridePosY: Int = posY,
        overrideWidthDp: Int = widthDp,
        overrideHeightDp: Int = heightDp
    ): CanvasComponentEntity {
        val enforcedBgHex = if (isTextView || isLinkOpen || isImageView) {
            "#00000000"
        } else {
            overrideBgHex.trim().ifEmpty { "#10B981" }
        }
        val enforcedTextHex = if (isTextView || isLinkOpen) {
            val cleaned = overrideTextHex.trim().ifEmpty { "#FFFFFF" }
            if (cleaned.equals("#00000000", ignoreCase = true)) "#FFFFFF" else cleaned
        } else {
            overrideTextHex.trim().ifEmpty { "#FFFFFF" }
        }
        return component.copy(
            label = overrideLabel.trim().ifEmpty { component.label },
            bgColorHex = enforcedBgHex,
            textColorHex = enforcedTextHex,
            bgImagePath = overrideBgImagePath.trim(),
            customImagePath = overrideCustomImagePath.trim(),
            linkUrl = overrideLinkUrl.trim(),
            posXDp = overridePosX,
            posYDp = overridePosY,
            widthDp = overrideWidthDp,
            heightDp = overrideHeightDp
        )
    }

    // Image picker for Start/Stop background image, Link Open side logo, or ImageView main image
    val screen1ImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val destDir = File(context.filesDir, "widget_bg_images").apply { mkdirs() }
                val destFile = File(destDir, "s1_img_${component.id}_${System.currentTimeMillis()}.png")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (destFile.exists() && destFile.length() > 0L) {
                    val savedPath = destFile.absolutePath
                    if (isStartOrStop) {
                        bgImagePath = savedPath
                        onSaveComponent(buildScreen1Updated(overrideBgImagePath = savedPath))
                    } else {
                        // For S1_LINK (side logo) and S1_IMAGE (image view), save in both customImagePath and bgImagePath so APK compiler bundles it too
                        customImagePath = savedPath
                        bgImagePath = savedPath
                        onSaveComponent(
                            buildScreen1Updated(
                                overrideCustomImagePath = savedPath,
                                overrideBgImagePath = savedPath
                            )
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    val activePreviewPath = if (isStartOrStop) bgImagePath else customImagePath.ifBlank { bgImagePath }
    val previewBitmap = remember(activePreviewPath) {
        if (activePreviewPath.isNotBlank()) {
            val f = File(activePreviewPath)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
        } else null
    }

    val widgetTitle = when (typeUpper) {
        "S1_START" -> "Start Button (Screen 1)"
        "S1_STOP" -> "Stop Button (Screen 1)"
        "S1_TEXT" -> "Text View (Screen 1)"
        "S1_LINK" -> "Link Open (Screen 1)"
        "S1_IMAGE" -> "Image View (Screen 1)"
        else -> "Screen 1 Widget"
    }

    val s1CardBg = Color(0xFF0A1428)
    val solidPresetColors: List<Pair<String, String>> = remember {
        listOf(
            "Green" to "#10B981",
            "Red" to "#EF4444",
            "Royal Blue" to "#2563EB",
            "Indigo" to "#4F46E5",
            "Purple" to "#7C3AED",
            "Cyan" to "#06B6D4",
            "Gold" to "#F59E0B",
            "Pink" to "#EC4899",
            "Dark Navy" to "#1E293B",
            "Midnight" to "#0F172A",
            "Black" to "#000000",
            "White" to "#FFFFFF"
        )
    }
    val solidTextColors: List<Pair<String, String>> = remember {
        listOf(
            "White" to "#FFFFFF",
            "Cyan" to "#38BDF8",
            "Green" to "#10B981",
            "Yellow" to "#FACC15",
            "Orange" to "#FB923C",
            "Pink" to "#F472B6",
            "Purple" to "#A78BFA",
            "Red" to "#EF4444",
            "Dark" to "#0F172A",
            "Black" to "#000000"
        )
    }

    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = InspectorDarkBg,
        border = BorderStroke(1.dp, Color(0xFF152342)),
        tonalElevation = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 285.dp)
            .testTag("screen1_widget_inspector_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row
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

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = widgetTitle,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when (typeUpper) {
                                "S1_START" -> "Starts & opens the Floating Window"
                                "S1_STOP" -> "Stops & closes the Floating Window"
                                "S1_TEXT" -> "Always transparent background • Custom text color"
                                "S1_LINK" -> "Always transparent background • Opens link on tap"
                                "S1_IMAGE" -> "Simple Image View • Corner resizable"
                                else -> "Screen 1 Main Screen Widget"
                            },
                            color = Color(0xFF38BDF8),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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

            // Scrollable Inspector Body tailored to the exact Screen 1 widget type
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1) IMAGE VIEW: Pure & Simple — ONLY image selection option!
                if (isImageView) {
                    Surface(
                        color = s1CardBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, InspectorFieldBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "IMAGE VIEW SOURCE",
                                color = Color(0xFFFBBF24),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    onClick = { screen1ImagePickerLauncher.launch("image/*") },
                                    color = InspectorPurpleButton,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("s1_image_pick_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (previewBitmap != null) "Change Image" else "Add Image from Gallery",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (previewBitmap != null) {
                                    OutlinedButton(
                                        onClick = {
                                            customImagePath = ""
                                            bgImagePath = ""
                                            onSaveComponent(
                                                buildScreen1Updated(
                                                    overrideCustomImagePath = "",
                                                    overrideBgImagePath = ""
                                                )
                                            )
                                        },
                                        border = BorderStroke(1.dp, Color(0xFFEF4444))
                                    ) {
                                        Text("Remove", color = Color(0xFFF87171), fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // 2) START / STOP / TEXT VIEW / LINK OPEN: Editable Display Text
                if (!isImageView) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = {
                            label = it
                            onSaveComponent(buildScreen1Updated(overrideLabel = it))
                        },
                        label = {
                            Text(
                                text = when {
                                    isLinkOpen -> "Display Text (Click to Open Link)"
                                    isTextView -> "Text View Content"
                                    else -> "Button Text"
                                },
                                color = InspectorSubtitleColor,
                                fontSize = 11.sp
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = InspectorFieldBg,
                            unfocusedContainerColor = InspectorFieldBg,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = InspectorFieldBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("s1_label_input")
                    )
                }

                // 3) LINK OPEN: Link URL + Optional Side Logo
                if (isLinkOpen) {
                    OutlinedTextField(
                        value = linkUrl,
                        onValueChange = {
                            linkUrl = it
                            onSaveComponent(buildScreen1Updated(overrideLinkUrl = it))
                        },
                        label = {
                            Text(
                                text = "Link URL (e.g. https://instagram.com/...)",
                                color = InspectorSubtitleColor,
                                fontSize = 11.sp
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = InspectorFieldBg,
                            unfocusedContainerColor = InspectorFieldBg,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = InspectorFieldBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("s1_link_url_input")
                    )

                    // Optional Side Logo for Link Open
                    Surface(
                        color = s1CardBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, InspectorFieldBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (previewBitmap != null) {
                                    "SIDE LOGO: ACTIVE (Shown next to link text)"
                                } else {
                                    "SIDE LOGO: NONE (No empty logo box shown; text auto-fits)"
                                },
                                color = Color(0xFF38BDF8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (previewBitmap != null) {
                                    Image(
                                        bitmap = previewBitmap,
                                        contentDescription = "Side Logo Preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                }
                                Surface(
                                    onClick = { screen1ImagePickerLauncher.launch("image/*") },
                                    color = InspectorPurpleButton,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("s1_link_logo_pick_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            text = if (previewBitmap != null) "Change Side Logo" else "Add Side Logo",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                if (previewBitmap != null) {
                                    OutlinedButton(
                                        onClick = {
                                            customImagePath = ""
                                            bgImagePath = ""
                                            onSaveComponent(
                                                buildScreen1Updated(
                                                    overrideCustomImagePath = "",
                                                    overrideBgImagePath = ""
                                                )
                                            )
                                        },
                                        border = BorderStroke(1.dp, Color(0xFFEF4444))
                                    ) {
                                        Text("No Logo", color = Color(0xFFF87171), fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // 4) START & STOP ONLY: Background Image + Transparent Background + Default Colors
                if (isStartOrStop) {
                    Surface(
                        color = s1CardBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, InspectorFieldBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "BACKGROUND (IMAGE / TRANSPARENT / DEFAULT COLORS)",
                                    color = InspectorSubtitleColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Surface(
                                    onClick = {
                                        bgHex = "#00000000"
                                        onSaveComponent(buildScreen1Updated(overrideBgHex = "#00000000"))
                                    },
                                    color = if (bgHex.equals("#00000000", ignoreCase = true)) Color(0xFF10B981) else Color(0xFF1E293B),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.testTag("s1_bg_transparent_button")
                                ) {
                                    Text(
                                        text = "Transparent BG",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            // Background Image Picker Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    onClick = { screen1ImagePickerLauncher.launch("image/*") },
                                    color = Color(0xFF1E293B),
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("s1_bg_image_pick_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            text = if (previewBitmap != null) "Change Background Image" else "Background Image Change",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                if (previewBitmap != null) {
                                    OutlinedButton(
                                        onClick = {
                                            bgImagePath = ""
                                            onSaveComponent(buildScreen1Updated(overrideBgImagePath = ""))
                                        },
                                        border = BorderStroke(1.dp, Color(0xFFEF4444))
                                    ) {
                                        Text("Clear Img", color = Color(0xFFF87171), fontSize = 10.sp)
                                    }
                                }
                            }

                            // Default Background Color Swatches
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                solidPresetColors.forEach { (name, hex) ->
                                    val isSelectedColor = bgHex.equals(hex, ignoreCase = true)
                                    val parsed = parseHexColorSafe(hex, Color(0xFF10B981))
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(parsed)
                                            .border(
                                                BorderStroke(
                                                    if (isSelectedColor) 2.5.dp else 1.dp,
                                                    if (isSelectedColor) Color.White else Color.White.copy(alpha = 0.4f)
                                                ),
                                                CircleShape
                                            )
                                            .clickable {
                                                bgHex = hex
                                                onSaveComponent(buildScreen1Updated(overrideBgHex = hex))
                                            }
                                            .testTag("s1_bg_color_${name.lowercase().replace(" ", "_")}")
                                    )
                                }
                            }
                        }
                    }
                }

                // 5) TEXT COLOR OPTIONS:
                // - Start/Stop: Text Color Change + Text Transparent
                // - TextView & Link Open: Text Color Change ONLY (NO Text Transparent option, and background is locked to Transparent)
                if (!isImageView) {
                    Surface(
                        color = s1CardBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, InspectorFieldBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isTextView || isLinkOpen) {
                                        "TEXT COLOR (Background is Always Transparent)"
                                    } else {
                                        "TEXT COLOR & TEXT TRANSPARENT"
                                    },
                                    color = InspectorSubtitleColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )

                                // Text Transparent option ONLY for Start and Stop (explicitly excluded for TextView & Link Open)
                                if (isStartOrStop) {
                                    Surface(
                                        onClick = {
                                            textHex = "#00000000"
                                            onSaveComponent(buildScreen1Updated(overrideTextHex = "#00000000"))
                                        },
                                        color = if (textHex.equals("#00000000", ignoreCase = true)) Color(0xFF10B981) else Color(0xFF1E293B),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.testTag("s1_text_transparent_button")
                                    ) {
                                        Text(
                                            text = "Text Transparent",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                solidTextColors.forEach { (name, hex) ->
                                    val isSelectedText = textHex.equals(hex, ignoreCase = true)
                                    val parsed = parseHexColorSafe(hex, Color.White)
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(parsed)
                                            .border(
                                                BorderStroke(
                                                    if (isSelectedText) 2.5.dp else 1.dp,
                                                    if (isSelectedText) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.4f)
                                                ),
                                                CircleShape
                                            )
                                            .clickable {
                                                textHex = hex
                                                onSaveComponent(buildScreen1Updated(overrideTextHex = hex))
                                            }
                                            .testTag("s1_text_color_${name.lowercase().replace(" ", "_")}")
                                    )
                                }
                            }
                        }
                    }
                }

                // 6) Recommended Dhanche Snap + Delete / Duplicate Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (matchingDhanche != null) {
                        OutlinedButton(
                            onClick = {
                                posX = matchingDhanche.recX
                                posY = matchingDhanche.recY
                                widthDp = matchingDhanche.recW
                                heightDp = matchingDhanche.recH
                                onSaveComponent(
                                    buildScreen1Updated(
                                        overridePosX = matchingDhanche.recX,
                                        overridePosY = matchingDhanche.recY,
                                        overrideWidthDp = matchingDhanche.recW,
                                        overrideHeightDp = matchingDhanche.recH
                                    )
                                )
                            },
                            border = BorderStroke(1.dp, Color(0xFF10B981)),
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("s1_snap_best_dhanche_button")
                        ) {
                            Text(
                                text = "Best Fit (${matchingDhanche.recW}×${matchingDhanche.recH})",
                                color = Color(0xFF10B981),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { onDuplicateComponent(buildScreen1Updated()) },
                        border = BorderStroke(1.dp, InspectorFieldBorder),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("inspector_duplicate_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy", color = Color.White, fontSize = 10.5.sp)
                    }

                    OutlinedButton(
                        onClick = { onDeleteComponent(component) },
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("inspector_delete_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", color = Color(0xFFF87171), fontSize = 10.5.sp)
                    }
                }
            }
        }
    }
}

