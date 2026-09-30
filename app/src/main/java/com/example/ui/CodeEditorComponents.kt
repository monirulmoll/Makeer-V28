package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CanvasComponentEntity
import com.example.data.StudioProjectEntity
import java.util.LinkedHashMap

data class ParsedVisualKotlinState(
    val updatedProject: StudioProjectEntity,
    val updatedComponents: List<CanvasComponentEntity>
)

object KotlinProjectCodeEngine {

    @JvmStatic
    fun generateKotlinFilesForProject(
        project: StudioProjectEntity,
        components: List<CanvasComponentEntity>
    ): Map<String, String> {
        val files = LinkedHashMap<String, String>()
        val pkg = project.packageName.ifBlank { "com.generated.overlay.panel" }

        val widgetsCode = components.joinToString("\n") { c ->
            """        // Widget #${c.id}: ${c.type} "${c.label}" (${c.widthDp}x${c.heightDp}dp @ ${c.posXDp},${c.posYDp}) offset=${c.byteOffsetHex} on=${c.onPayloadHex} off=${c.offPayloadHex}"""
        }

        files["src/main/java/com/example/ui/CanvasWorkspaceComponents.kt"] = """
package $pkg

import android.content.Context

/**
 * Generated Floating Overlay Panel Specification for ${project.name}
 * Overlay Title: ${project.overlayTitle}
 * Canvas Size: ${project.canvasWidthDp}x${project.canvasHeightDp}dp
 */
object GeneratedOverlayDescriptor {
    const val APP_NAME = "${project.name}"
    const val PACKAGE_NAME = "$pkg"
    const val OVERLAY_TITLE = "${project.overlayTitle}"
    const val CANVAS_WIDTH_DP = ${project.canvasWidthDp}
    const val CANVAS_HEIGHT_DP = ${project.canvasHeightDp}
    const val CANVAS_BG_COLOR_HEX = "${project.canvasBgColorHex}"
    const val TARGET_FILE_PATH = "${project.defaultTargetFilePath}"

    fun describeWidgets(context: Context): List<String> = listOf(
${components.joinToString(",\n") { "        \"${it.type}|${it.label}|${it.posXDp}|${it.posYDp}|${it.widthDp}|${it.heightDp}|${it.byteOffsetHex}|${it.onPayloadHex}|${it.offPayloadHex}|${it.bgColorHex}|${it.textColorHex}\"" }}
    )
$widgetsCode
}
""".trimIndent()

        files["src/main/java/com/example/service/FloatingOverlayService.kt"] = """
package $pkg.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

class FloatingOverlayService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
""".trimIndent()

        return files
    }

    @JvmStatic
    fun parseEditedKotlinToVisualState(
        originalProject: StudioProjectEntity,
        originalComponents: List<CanvasComponentEntity>,
        editedFiles: Map<String, String>
    ): ParsedVisualKotlinState {
        val canvasSource = editedFiles["src/main/java/com/example/ui/CanvasWorkspaceComponents.kt"].orEmpty()
        if (canvasSource.isBlank()) {
            return ParsedVisualKotlinState(originalProject, originalComponents)
        }

        val appName = Regex("""const\s+val\s+APP_NAME\s*=\s*"([^"]*)"""")
            .find(canvasSource)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotEmpty() } ?: originalProject.name

        val pkgName = Regex("""const\s+val\s+PACKAGE_NAME\s*=\s*"([^"]*)"""")
            .find(canvasSource)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotEmpty() } ?: originalProject.packageName

        val overlayTitle = Regex("""const\s+val\s+OVERLAY_TITLE\s*=\s*"([^"]*)"""")
            .find(canvasSource)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotEmpty() } ?: originalProject.overlayTitle

        val widthDp = Regex("""const\s+val\s+CANVAS_WIDTH_DP\s*=\s*(\d+)""")
            .find(canvasSource)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: originalProject.canvasWidthDp

        val heightDp = Regex("""const\s+val\s+CANVAS_HEIGHT_DP\s*=\s*(\d+)""")
            .find(canvasSource)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: originalProject.canvasHeightDp

        val bgHex = Regex("""const\s+val\s+CANVAS_BG_COLOR_HEX\s*=\s*"([^"]*)"""")
            .find(canvasSource)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotEmpty() } ?: originalProject.canvasBgColorHex

        val targetPath = Regex("""const\s+val\s+TARGET_FILE_PATH\s*=\s*"([^"]*)"""")
            .find(canvasSource)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotEmpty() } ?: originalProject.defaultTargetFilePath

        val updatedProject = originalProject.copy(
            name = appName,
            packageName = pkgName,
            overlayTitle = overlayTitle,
            canvasWidthDp = widthDp.coerceIn(170, 420),
            canvasHeightDp = heightDp.coerceIn(160, 620),
            canvasBgColorHex = bgHex,
            defaultTargetFilePath = targetPath,
            updatedAt = System.currentTimeMillis()
        )

        val pipeSpecRegex = Regex(""""([A-Z]+)\|([^"|]+)\|(-?\d+)\|(-?\d+)\|(\d+)\|(\d+)\|([^"|]+)\|([^"|]*)\|([^"|]*)\|([^"|]+)\|([^"|]+)"""")
        val matches = pipeSpecRegex.findAll(canvasSource).toList()
        if (matches.isEmpty()) {
            return ParsedVisualKotlinState(updatedProject, originalComponents)
        }

        val parsedComponents = matches.mapIndexed { idx, match ->
            val existing = originalComponents.getOrNull(idx)
            CanvasComponentEntity(
                id = existing?.id ?: (idx + 1L),
                projectId = originalProject.id,
                type = match.groupValues[1],
                label = match.groupValues[2],
                posXDp = match.groupValues[3].toIntOrNull() ?: 10,
                posYDp = match.groupValues[4].toIntOrNull() ?: (10 + idx * 48),
                widthDp = match.groupValues[5].toIntOrNull() ?: 176,
                heightDp = match.groupValues[6].toIntOrNull() ?: 44,
                byteOffsetHex = match.groupValues[7],
                onPayloadHex = match.groupValues[8],
                offPayloadHex = match.groupValues[9],
                bgColorHex = match.groupValues[10],
                textColorHex = match.groupValues[11],
                targetFilePath = existing?.targetFilePath ?: targetPath,
                currentValue = existing?.currentValue ?: "0",
                sliderMax = existing?.sliderMax ?: 100
            )
        }

        return ParsedVisualKotlinState(updatedProject, parsedComponents)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioEditCodeDialog(
    project: StudioProjectEntity,
    components: List<CanvasComponentEntity>,
    initialFiles: Map<String, String>,
    onDismiss: () -> Unit,
    onCompileCodeToVisualScreen: (Map<String, String>) -> Unit,
    onCompileCodeToApk: (Map<String, String>) -> Unit
) {
    CodeEditorScreen(
        project = project,
        files = initialFiles,
        onApplyCodeToVisual = { updated, buildApk ->
            if (buildApk) {
                onCompileCodeToApk(updated)
            } else {
                onCompileCodeToVisualScreen(updated)
            }
        },
        onClose = onDismiss
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditorScreen(
    project: StudioProjectEntity,
    files: Map<String, String>,
    onApplyCodeToVisual: (Map<String, String>, Boolean) -> Unit,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    var workingFiles by remember(project.id) {
        mutableStateOf(files.toMutableMap())
    }
    val fileKeys = remember(project.id) { files.keys.toList() }
    var activeKey by remember(project.id) {
        mutableStateOf(fileKeys.firstOrNull().orEmpty())
    }
    var editorText by remember(activeKey) {
        mutableStateOf(workingFiles[activeKey].orEmpty())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "${project.name} • Kotlin Code Editor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = activeKey.ifEmpty { "No file selected" },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("code_editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val updated = workingFiles.toMutableMap().apply {
                                if (activeKey.isNotEmpty()) put(activeKey, editorText)
                            }
                            onApplyCodeToVisual(updated, false)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .testTag("save_file_buffer_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Sync to Canvas", fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            val updated = workingFiles.toMutableMap().apply {
                                if (activeKey.isNotEmpty()) put(activeKey, editorText)
                            }
                            onApplyCodeToVisual(updated, true)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("code_editor_build_apk_button")
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Compile APK", fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0B1120)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                fileKeys.forEach { filePath ->
                    val isSelected = filePath == activeKey
                    val shortName = filePath.substringAfterLast('/')
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) Color(0xFF2563EB) else Color(0xFF0F172A),
                        modifier = Modifier
                            .clickable {
                                if (activeKey.isNotEmpty()) {
                                    workingFiles = workingFiles.toMutableMap().apply {
                                        put(activeKey, editorText)
                                    }
                                }
                                activeKey = filePath
                                editorText = workingFiles[filePath].orEmpty()
                            }
                            .testTag("file_tab_$shortName")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = shortName,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .background(Color(0xFF020617), RoundedCornerShape(10.dp))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                BasicTextField(
                    value = editorText,
                    onValueChange = {
                        editorText = it
                    },
                    textStyle = TextStyle(
                        color = Color(0xFFE2E8F0),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    ),
                    cursorBrush = SolidColor(Color(0xFF38BDF8)),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("code_editor_text_field")
                )
            }
        }
    }
}
