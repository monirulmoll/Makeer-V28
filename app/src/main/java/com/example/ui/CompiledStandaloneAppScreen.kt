package com.example.ui

import android.Manifest
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.CanvasComponentEntity
import com.example.data.StudioProjectEntity
import com.example.engine.LocalConfigStateWriter
import com.example.service.DynamicOverlayRegistry
import com.example.service.FloatingDashboardService
import java.io.File
import kotlin.math.roundToInt

@Composable
fun CompiledStandaloneAppScreen(
    project: StudioProjectEntity,
    initialComponents: List<CanvasComponentEntity>,
    compiledPackageName: String,
    isStandaloneInstalledApk: Boolean,
    isOverlayRunning: Boolean = false,
    hasStoragePermission: Boolean = false,
    hasOverlayPermission: Boolean = false,
    statusMessage: String = "",
    onStartOverlay: () -> Unit = {},
    onStopOverlay: () -> Unit = {},
    onRefreshPermissions: () -> Unit = {},
    onTriggerComponentLive: (CanvasComponentEntity, String?) -> Unit = { _, _ -> },
    onBackToStudioEditor: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val legacyStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshPermissions()
        if (!LocalConfigStateWriter.hasStoragePermissionGranted(context)) {
            LocalConfigStateWriter.requestStoragePermission(context)
        }
    }

    val requestStorageAction = {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            legacyStorageLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        } else {
            LocalConfigStateWriter.requestStoragePermission(context)
        }
    }

    val requestFloatPermissionAction = {
        LocalConfigStateWriter.requestOverlayPermission(context)
    }

    val appLogoPath = project.appLogoPath.ifBlank { project.floatingLogoPath }
    val appLogoBitmap = remember(appLogoPath) {
        if (appLogoPath.isNotBlank()) {
            val f = File(appLogoPath)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
        } else null
    }

    val effectiveFloatingRunning = isOverlayRunning || FloatingDashboardService.isRunning()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0B1120)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!isStandaloneInstalledApk && onBackToStudioEditor != null) {
                    OutlinedButton(
                        onClick = onBackToStudioEditor,
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.testTag("back_to_studio_editor_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Back to Studio Editor", color = Color.White)
                    }
                }

                // App Header Card
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151F34)),
                    border = BorderStroke(1.dp, Color(0xFF263554)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF4F46E5), Color(0xFF2563EB))
                                    )
                                )
                                .border(BorderStroke(1.5.dp, Color(0xFF60A5FA)), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (appLogoBitmap != null) {
                                Image(
                                    bitmap = appLogoBitmap,
                                    contentDescription = "App Logo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(14.dp))
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = project.name.ifBlank { "Standalone App" },
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = compiledPackageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = project.overlayTitle.ifBlank { project.name },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = if (effectiveFloatingRunning) Color(0xFF065F46) else Color(0xFF1E293B),
                            border = BorderStroke(
                                1.dp,
                                if (effectiveFloatingRunning) Color(0xFF10B981) else Color(0xFF475569)
                            )
                        ) {
                            Text(
                                text = if (effectiveFloatingRunning) "FLOATING" else "STOPPED",
                                color = if (effectiveFloatingRunning) Color(0xFF6EE7B7) else Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Storage Permission & Float Window Permission Card
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151F34)),
                    border = BorderStroke(1.dp, Color(0xFF263554)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Storage Permission Row
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (hasStoragePermission) Color(0xFF0D2824) else Color(0xFF1E293B),
                            border = BorderStroke(
                                1.dp,
                                if (hasStoragePermission) Color(0xFF10B981) else Color(0xFF38BDF8)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { requestStorageAction() }
                                .testTag("standalone_storage_permission_button")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (hasStoragePermission) Icons.Default.CheckCircle else Icons.Default.Folder,
                                        contentDescription = "Storage Permission",
                                        tint = if (hasStoragePermission) Color(0xFF10B981) else Color(0xFF38BDF8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Storage Permission",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (hasStoragePermission) "All Files Access Granted" else "Tap to allow Storage permission",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (hasStoragePermission) Color(0xFF10B981) else Color(0xFF2563EB)
                                ) {
                                    Text(
                                        text = if (hasStoragePermission) "ALLOWED ✓" else "ALLOW",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        // 2. Float Window Permission Row
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (hasOverlayPermission) Color(0xFF0D2824) else Color(0xFF1E293B),
                            border = BorderStroke(
                                1.dp,
                                if (hasOverlayPermission) Color(0xFF10B981) else Color(0xFF38BDF8)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { requestFloatPermissionAction() }
                                .testTag("standalone_overlay_permission_button")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (hasOverlayPermission) Icons.Default.CheckCircle else Icons.Default.Layers,
                                        contentDescription = "Float Window Permission",
                                        tint = if (hasOverlayPermission) Color(0xFF10B981) else Color(0xFF38BDF8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Float Window Permission",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (hasOverlayPermission) "Display Over Other Apps Granted" else "Tap to allow Float Window permission",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (hasOverlayPermission) Color(0xFF10B981) else Color(0xFF2563EB)
                                ) {
                                    Text(
                                        text = if (hasOverlayPermission) "ALLOWED ✓" else "ALLOW",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ONLY 2 BUTTONS: START & STOP
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151F34)),
                    border = BorderStroke(1.dp, Color(0xFF263554)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // START BUTTON
                            Button(
                                onClick = {
                                    if (!hasStoragePermission) {
                                        requestStorageAction()
                                    } else if (!hasOverlayPermission) {
                                        requestFloatPermissionAction()
                                    } else {
                                        onStartOverlay()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10B981),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .testTag("standalone_start_overlay_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Start Floating Window",
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "START",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            // STOP BUTTON
                            Button(
                                onClick = {
                                    onStopOverlay()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEF4444),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .testTag("standalone_stop_overlay_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop Floating Window",
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "STOP",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        if (statusMessage.isNotBlank()) {
                            Text(
                                text = statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StandaloneDraggableFloatingWindow(
    project: StudioProjectEntity,
    components: List<CanvasComponentEntity>,
    onTriggerComponent: (CanvasComponentEntity, String?) -> Unit,
    onKillOverlay: () -> Unit = {}
) {
    val density = LocalDensity.current
    val canvasBg = parseHexColorSafe(project.canvasBgColorHex, Color.White)
    var isCollapsedToGoalBubble by remember(project.id) { mutableStateOf(false) }
    var isHiddenFloatingWindow by remember(project.id) { mutableStateOf(false) }
    var isKilledFloatingWindow by remember(project.id) { mutableStateOf(false) }

    if (isKilledFloatingWindow) return

    var windowOffsetX by remember { mutableFloatStateOf(with(density) { 24.dp.toPx() }) }
    var windowOffsetY by remember { mutableFloatStateOf(with(density) { 140.dp.toPx() }) }

    val activeLogoPath = project.floatingLogoPath.ifBlank { project.appLogoPath }
    val floatingLogoBitmap = remember(activeLogoPath) {
        if (activeLogoPath.isNotBlank()) {
            val file = File(activeLogoPath)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() else null
        } else null
    }

    val canvasBgBitmap = remember(project.canvasBgImagePath) {
        if (project.canvasBgImagePath.isNotBlank()) {
            val file = File(project.canvasBgImagePath)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() else null
        } else null
    }

    val resolvedPanelTitle = project.overlayTitle.trim().ifEmpty {
        project.name.trim().ifEmpty { "Floating Panel" }
    }

    val isDarkCanvasBg = remember(project.canvasBgColorHex, canvasBgBitmap) {
        canvasBgBitmap != null ||
            (canvasBg.red * 0.299f + canvasBg.green * 0.587f + canvasBg.blue * 0.114f) < 0.55f
    }
    val headerContentColor = if (isDarkCanvasBg) Color.White else Color(0xFF0F172A)

    Box(
        modifier = Modifier
            .offset { IntOffset(windowOffsetX.roundToInt(), windowOffsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    windowOffsetX = (windowOffsetX + dragAmount.x).coerceAtLeast(0f)
                    windowOffsetY = (windowOffsetY + dragAmount.y).coerceAtLeast(0f)
                }
            }
            .testTag("standalone_live_floating_window")
    ) {
        if (isHiddenFloatingWindow) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.72f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                modifier = Modifier
                    .clickable {
                        isHiddenFloatingWindow = false
                        isCollapsedToGoalBubble = false
                    }
                    .testTag("standalone_hidden_restore_chip")
            ) {
                Text(
                    text = "👁 Show Panel",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        } else if (isCollapsedToGoalBubble) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF4F46E5), Color(0xFF2563EB))
                        )
                    )
                    .border(BorderStroke(2.5.dp, Color.White), CircleShape)
                    .clickable { isCollapsedToGoalBubble = false }
                    .testTag("standalone_minimized_goal_bubble"),
                contentAlignment = Alignment.Center
            ) {
                if (floatingLogoBitmap != null) {
                    Image(
                        bitmap = floatingLogoBitmap,
                        contentDescription = "Floating Bubble Logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Text(
                        text = resolvedPanelTitle,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
        } else {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = canvasBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
                modifier = Modifier.size(
                    width = project.canvasWidthDp.dp.coerceIn(180.dp, 340.dp),
                    height = project.canvasHeightDp.dp.coerceIn(180.dp, 480.dp)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(canvasBg)
                ) {
                    if (canvasBgBitmap != null) {
                        Image(
                            bitmap = canvasBgBitmap,
                            contentDescription = "Floating Window Background",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header Bar matching Studio Preview with Minimize & Hide buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(headerContentColor.copy(alpha = 0.15f))
                                        .border(BorderStroke(1.dp, headerContentColor.copy(alpha = 0.5f)), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (floatingLogoBitmap != null) {
                                        Image(
                                            bitmap = floatingLogoBitmap,
                                            contentDescription = "Floating Panel Logo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Text(
                                            text = "✦",
                                            color = headerContentColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = resolvedPanelTitle,
                                    color = headerContentColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = headerContentColor.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, headerContentColor.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .clickable { isCollapsedToGoalBubble = true }
                                        .testTag("standalone_floating_window_minimize_btn")
                                ) {
                                    Text(
                                        text = "Minimize",
                                        color = headerContentColor,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.5.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = headerContentColor.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, headerContentColor.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .clickable { isHiddenFloatingWindow = true }
                                        .testTag("standalone_floating_window_hide_btn")
                                ) {
                                    Text(
                                        text = "Hide",
                                        color = headerContentColor,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.5.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFEF4444).copy(alpha = 0.85f),
                                    border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                    modifier = Modifier
                                        .clickable {
                                            isKilledFloatingWindow = true
                                            onKillOverlay()
                                        }
                                        .testTag("standalone_floating_window_kill_btn")
                                ) {
                                    Text(
                                        text = "Kill",
                                        color = Color.White,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.5.dp)
                                    )
                                }
                            }
                        }

                        // Canvas Body matching Studio Preview
                        Box(modifier = Modifier.fillMaxSize()) {
                            components.forEach { comp ->
                            val isToggle = comp.type == "TOGGLE"
                            val isChecked = comp.currentValue.equals("true", ignoreCase = true) || comp.currentValue == "1"
                            val isDefaultWhite = comp.bgColorHex.isBlank() || comp.bgColorHex.equals("#FFFFFF", ignoreCase = true)
                            val widgetBg = if (isToggle && isChecked && isDefaultWhite) {
                                Color(0xFFECFDF5)
                            } else {
                                parseHexColorSafe(comp.bgColorHex, Color(0xFF2563EB))
                            }
                            val widgetText = parseHexColorSafe(comp.textColorHex, Color(0xFF0F172A))
                            val borderColor = when {
                                isToggle && isChecked -> Color(0xFF10B981)
                                isToggle -> Color(0xFF64748B)
                                else -> Color(0xFFCBD5E1)
                            }

                            val widgetBgBitmap = remember(comp.bgImagePath) {
                                if (comp.bgImagePath.isNotBlank()) {
                                    val f = File(comp.bgImagePath)
                                    if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
                                } else null
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = widgetBg,
                                modifier = Modifier
                                    .offset(comp.posXDp.dp, comp.posYDp.dp)
                                    .size(comp.widthDp.dp, comp.heightDp.dp)
                                    .border(
                                        width = if (isToggle) 2.dp else 1.dp,
                                        color = borderColor,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        val nextVal = if (isToggle) {
                                            if (isChecked) "0" else "1"
                                        } else {
                                            null
                                        }
                                        onTriggerComponent(comp, nextVal)
                                    }
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (widgetBgBitmap != null) {
                                        Image(
                                            bitmap = widgetBgBitmap,
                                            contentDescription = "Widget Background Image",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = comp.label,
                                                color = widgetText,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (comp.type == "SLIDER") {
                                                val sliderVal = (comp.currentValue.toFloatOrNull() ?: 50f)
                                                    .coerceIn(0f, comp.sliderMax.toFloat().coerceAtLeast(1f))
                                                Slider(
                                                    value = sliderVal,
                                                    onValueChange = { v ->
                                                        onTriggerComponent(comp, v.roundToInt().toString())
                                                    },
                                                    valueRange = 0f..comp.sliderMax.toFloat().coerceAtLeast(1f),
                                                    modifier = Modifier.height(22.dp)
                                                )
                                            } else if (comp.type == "INPUT" || comp.type == "TEXT") {
                                                Text(
                                                    text = comp.currentValue,
                                                    color = widgetText.copy(alpha = 0.85f),
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        if (isToggle) {
                                            Switch(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    onTriggerComponent(comp, if (checked) "1" else "0")
                                                }
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
        }
    }
}

@Composable
fun CompiledStandaloneAppScreen(
    isOverlayRunning: Boolean,
    statusMessage: String,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit
) {
    val context = LocalContext.current
    val title = DynamicOverlayRegistry.getActiveOverlayTitle().ifBlank { "Floating Overlay Panel" }
    val pkg = DynamicOverlayRegistry.getActivePackageName()
    val project = StudioProjectEntity(
        id = 1L,
        name = DynamicOverlayRegistry.getActiveProjectName().ifBlank { title },
        packageName = pkg,
        overlayTitle = title,
        appLogoPath = DynamicOverlayRegistry.getActiveAppLogoPath(),
        floatingLogoPath = DynamicOverlayRegistry.getActiveFloatingLogoPath(),
        canvasWidthDp = DynamicOverlayRegistry.getActiveCanvasWidthDp(),
        canvasHeightDp = DynamicOverlayRegistry.getActiveCanvasHeightDp(),
        canvasBgColorHex = DynamicOverlayRegistry.getActiveCanvasBgHex(),
        canvasBgImagePath = DynamicOverlayRegistry.getActiveCanvasBgImagePath(),
        autoFixSize = DynamicOverlayRegistry.isActiveAutoFixSize()
    )
    CompiledStandaloneAppScreen(
        project = project,
        initialComponents = emptyList(),
        compiledPackageName = pkg,
        isStandaloneInstalledApk = true,
        isOverlayRunning = isOverlayRunning,
        hasStoragePermission = LocalConfigStateWriter.hasStoragePermissionGranted(context),
        hasOverlayPermission = android.provider.Settings.canDrawOverlays(context),
        statusMessage = statusMessage,
        onStartOverlay = onStartOverlay,
        onStopOverlay = onStopOverlay
    )
}
