package com.example.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HorizontalDistribute
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerticalDistribute
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CanvasComponentEntity
import com.example.data.ComponentWidgetType
import com.example.data.ConfigWriteAuditEntity
import com.example.data.StudioProjectEntity
import java.io.File
import kotlin.math.roundToInt

data class SketchwarePaletteEntry(
    val title: String,
    val widgetType: ComponentWidgetType,
    val customWidthDp: Int? = null,
    val customHeightDp: Int? = null,
    val customBgHex: String? = null,
    val customTextHex: String? = null
)

private data class LeftPaletteItemSpec(
    val entry: SketchwarePaletteEntry,
    val icon: ImageVector,
    val iconTint: Color,
    val tagSlug: String
)

@Composable
fun ComponentTrackerBanner(
    summary: ComponentCountSummary,
    components: List<CanvasComponentEntity>,
    selectedComponentId: Long?,
    onSelectComponentForEdit: (Long?) -> Unit,
    onOpenEditFloatingPanel: (() -> Unit)? = null
) {
    if (components.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0E1629))
            .border(BorderStroke(0.5.dp, Color(0xFF1E293B)))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFF17223B),
            border = BorderStroke(1.dp, Color(0xFF283556))
        ) {
            Text(
                text = "${summary.totalCount} Widgets",
                color = Color(0xFF38BDF8),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        components.forEach { comp ->
            val isSelected = comp.id == selectedComponentId
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) Color(0xFF5B46F6) else Color(0xFF151E34),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) Color(0xFF818CF8) else Color(0xFF283556)
                ),
                modifier = Modifier
                    .clickable { onSelectComponentForEdit(comp.id) }
                    .testTag("tracker_chip_${comp.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Color.White else Color(0xFF38BDF8))
                    )
                    Text(
                        text = comp.label,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun SketchwareStudioSplitWorkspace(
    project: StudioProjectEntity,
    components: List<CanvasComponentEntity>,
    selectedComponentId: Long?,
    isLivePreviewMode: Boolean,
    statusToast: String,
    onAddPaletteEntry: (SketchwarePaletteEntry) -> Unit,
    onSelectComponent: (Long?) -> Unit,
    onMoveComponent: (CanvasComponentEntity, Int, Int) -> Unit,
    onResizeComponent: (CanvasComponentEntity, Int, Int) -> Unit,
    onResizeCanvas: (Int, Int) -> Unit,
    onToggleAutoFixSize: () -> Unit,
    onOpenEditFloatingPanel: () -> Unit,
    onOpenChangeBackground: () -> Unit = {},
    onSaveDesign: () -> Unit,
    onTriggerComponentLive: (CanvasComponentEntity, String?) -> Unit,
    onClearCanvas: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxSize()) {
        // LEFT SIDE WIDGET PALETTE (Updated Dark Studio Dock)
        LeftSideWidgetPalette(
            isAutoFixSize = project.autoFixSize,
            onSelectPaletteEntry = onAddPaletteEntry,
            onToggleAutoFixSize = onToggleAutoFixSize
        )

        // RIGHT SIDE INTERACTIVE PHONE FRAME + FLOATING PANEL CANVAS
        InteractiveOverlayCanvas(
            project = project,
            components = components,
            selectedComponentId = selectedComponentId,
            onSelectComponent = onSelectComponent,
            onMoveComponent = { id, newX, newY ->
                components.find { it.id == id }?.let { comp ->
                    onMoveComponent(comp, newX, newY)
                }
            },
            onResizeCanvas = onResizeCanvas,
            onOpenEditFloatingPanel = onOpenEditFloatingPanel,
            onOpenChangeBackground = onOpenChangeBackground,
            onTriggerComponent = { comp, nextVal ->
                onTriggerComponentLive(comp, nextVal)
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun LeftSideWidgetPalette(
    isAutoFixSize: Boolean,
    onSelectPaletteEntry: (SketchwarePaletteEntry) -> Unit,
    onToggleAutoFixSize: () -> Unit
) {
    val layoutItems = remember {
        listOf(
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("Linear (H)", ComponentWidgetType.BUTTON, 196, 38, "#1E293B", "#F8FAFC"),
                icon = Icons.Default.HorizontalDistribute,
                iconTint = Color(0xFF38BDF8),
                tagSlug = "linear_h"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("Linear (V)", ComponentWidgetType.BUTTON, 196, 56, "#1E293B", "#F8FAFC"),
                icon = Icons.Default.VerticalDistribute,
                iconTint = Color(0xFF38BDF8),
                tagSlug = "linear_v"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("Scroll (H)", ComponentWidgetType.BUTTON, 196, 42, "#1E293B", "#F8FAFC"),
                icon = Icons.Default.SwapHoriz,
                iconTint = Color(0xFF38BDF8),
                tagSlug = "scroll_h"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("Scroll (V)", ComponentWidgetType.BUTTON, 196, 64, "#1E293B", "#F8FAFC"),
                icon = Icons.Default.SwapVert,
                iconTint = Color(0xFF38BDF8),
                tagSlug = "scroll_v"
            )
        )
    }

    val androidxItems = remember {
        listOf(
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("CardView", ComponentWidgetType.BUTTON, 196, 48, "#FFFFFF", "#0F172A"),
                icon = Icons.Default.CreditCard,
                iconTint = Color(0xFF818CF8),
                tagSlug = "cardview"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("TextInputLayout", ComponentWidgetType.INPUT, 196, 44, "#FFFFFF", "#0F172A"),
                icon = Icons.Default.Input,
                iconTint = Color(0xFF818CF8),
                tagSlug = "textinputlayout"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("SwipeRefresh", ComponentWidgetType.TOGGLE, 196, 42, "#FFFFFF", "#0F172A"),
                icon = Icons.Default.Refresh,
                iconTint = Color(0xFF818CF8),
                tagSlug = "swiperefresh"
            )
        )
    }

    val widgetItems = remember {
        listOf(
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("TextView", ComponentWidgetType.TEXT),
                icon = Icons.Default.TextFields,
                iconTint = Color(0xFFFB923C),
                tagSlug = "text"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("EditText", ComponentWidgetType.INPUT),
                icon = Icons.Default.Edit,
                iconTint = Color(0xFFFB923C),
                tagSlug = "input"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("Button", ComponentWidgetType.BUTTON),
                icon = Icons.Default.SmartButton,
                iconTint = Color(0xFFFB923C),
                tagSlug = "button"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("ImageView", ComponentWidgetType.IMAGE),
                icon = Icons.Default.Image,
                iconTint = Color(0xFFFB923C),
                tagSlug = "image"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("CheckBox", ComponentWidgetType.TOGGLE),
                icon = Icons.Default.CheckBox,
                iconTint = Color(0xFFFB923C),
                tagSlug = "checkbox"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("Switch", ComponentWidgetType.TOGGLE),
                icon = Icons.Default.ToggleOn,
                iconTint = Color(0xFFFB923C),
                tagSlug = "toggle"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("SeekBar", ComponentWidgetType.SLIDER),
                icon = Icons.Default.Tune,
                iconTint = Color(0xFFFB923C),
                tagSlug = "slider"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("ProgressBar", ComponentWidgetType.SLIDER),
                icon = Icons.Default.LinearScale,
                iconTint = Color(0xFFFB923C),
                tagSlug = "progressbar"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("Link Opener", ComponentWidgetType.LINK),
                icon = Icons.Default.Link,
                iconTint = Color(0xFF38BDF8),
                tagSlug = "link"
            )
        )
    }

    Surface(
        color = Color(0xFF0E1526),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier
            .width(128.dp)
            .fillMaxHeight()
            .testTag("left_widget_palette_sidebar")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Scrollable Categorized Widget List on Left Side
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                PaletteCategoryHeader("Layouts", Color(0xFF38BDF8))
                layoutItems.forEach { item ->
                    LeftPaletteItemRow(
                        item = item,
                        onClick = { onSelectPaletteEntry(item.entry) }
                    )
                }

                HorizontalDivider(
                    color = Color(0xFF1E293B),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                PaletteCategoryHeader("AndroidX", Color(0xFFA78BFA))
                androidxItems.forEach { item ->
                    LeftPaletteItemRow(
                        item = item,
                        onClick = { onSelectPaletteEntry(item.entry) }
                    )
                }

                HorizontalDivider(
                    color = Color(0xFF1E293B),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                PaletteCategoryHeader("Widgets", Color(0xFFFB923C))
                widgetItems.forEach { item ->
                    LeftPaletteItemRow(
                        item = item,
                        onClick = { onSelectPaletteEntry(item.entry) }
                    )
                }
            }

            // Bottom pinned Auto Size button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF090D18))
                    .border(BorderStroke(0.5.dp, Color(0xFF1E293B)))
                    .padding(6.dp)
            ) {
                Button(
                    onClick = onToggleAutoFixSize,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAutoFixSize) Color(0xFF10B981) else Color(0xFF283556)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("left_palette_auto_size_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckBox,
                        contentDescription = "Auto Size",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isAutoFixSize) "Auto Size: ON" else "Auto Size: OFF",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun PaletteCategoryHeader(title: String, tint: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(tint)
        )
        Text(
            text = title.uppercase(),
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp
        )
    }
}

@Composable
private fun LeftPaletteItemRow(
    item: LeftPaletteItemSpec,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF151F36),
        border = BorderStroke(1.dp, Color(0xFF23304E)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("add_widget_${item.tagSlug}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 7.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(item.iconTint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.entry.title,
                    tint = item.iconTint,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = item.entry.title,
                color = Color(0xFFE2E8F0),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun parseHexColorSafe(hex: String, fallback: Color = Color(0xFF2563EB)): Color {
    return try {
        val clean = hex.trim()
        val formatted = if (clean.startsWith("#")) clean else "#$clean"
        Color(android.graphics.Color.parseColor(formatted))
    } catch (_: Exception) {
        fallback
    }
}

@Composable
fun WidgetPaletteStrip(
    onAddWidget: (ComponentWidgetType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ComponentWidgetType.entries.forEach { widgetType ->
            Button(
                onClick = { onAddWidget(widgetType) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("strip_add_widget_${widgetType.name.lowercase()}")
            ) {
                Text(
                    text = widgetType.displayName,
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun InteractiveOverlayCanvas(
    project: StudioProjectEntity,
    components: List<CanvasComponentEntity>,
    selectedComponentId: Long?,
    onSelectComponent: (Long?) -> Unit,
    onMoveComponent: (Long, Int, Int) -> Unit,
    onResizeCanvas: (Int, Int) -> Unit = { _, _ -> },
    onOpenEditFloatingPanel: () -> Unit = {},
    onOpenChangeBackground: () -> Unit = {},
    onTriggerComponent: (CanvasComponentEntity, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val canvasBg = parseHexColorSafe(project.canvasBgColorHex, Color.White)
    var isCollapsedToGoalBubble by remember(project.id) { mutableStateOf(false) }
    var isHiddenFloatingWindow by remember(project.id) { mutableStateOf(false) }
    var isKilledFloatingWindow by remember(project.id) { mutableStateOf(false) }

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

    var dragCanvasWidthDp by remember(project.id, project.canvasWidthDp) {
        mutableFloatStateOf(project.canvasWidthDp.toFloat().coerceIn(180f, 340f))
    }
    var dragCanvasHeightDp by remember(project.id, project.canvasHeightDp) {
        mutableFloatStateOf(project.canvasHeightDp.toFloat().coerceIn(180f, 480f))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
            .clickable { onSelectComponent(null) }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer Android Phone Device Frame
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1528)),
            border = BorderStroke(2.dp, Color(0xFF233152)),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 2.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Phone Status Bar ("9:41" ... size badge ... "main.xml")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0A0F1E))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Text(
                            text = "9:41",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "${dragCanvasWidthDp.roundToInt()}×${dragCanvasHeightDp.roundToInt()} dp",
                        color = Color(0xFF60A5FA),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF15203B),
                        border = BorderStroke(1.dp, Color(0xFF283B66)),
                        modifier = Modifier
                            .clickable { onOpenChangeBackground() }
                            .testTag("phone_status_bg_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(canvasBg)
                                    .border(BorderStroke(0.5.dp, Color.White), CircleShape)
                            )
                            Text(
                                text = if (canvasBgBitmap != null) "BG: IMG" else "BG: ${project.canvasBgColorHex}",
                                color = Color(0xFFCBD5E1),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Simulated Phone Screen Workspace Area
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF131D36), Color(0xFF0D1426))
                            )
                        )
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isKilledFloatingWindow) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(99.dp),
                                color = Color(0xFF2A1215),
                                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                modifier = Modifier
                                    .clickable {
                                        isKilledFloatingWindow = false
                                        isHiddenFloatingWindow = false
                                        isCollapsedToGoalBubble = false
                                    }
                                    .testTag("canvas_killed_restore_button")
                            ) {
                                Text(
                                    text = "✖ Floating Window Closed (Killed) — Tap to Reopen",
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    } else if (isHiddenFloatingWindow) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(99.dp),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                                modifier = Modifier
                                    .clickable {
                                        isHiddenFloatingWindow = false
                                        isCollapsedToGoalBubble = false
                                    }
                                    .testTag("canvas_hidden_restore_button")
                            ) {
                                Text(
                                    text = "Floating Window Hidden — Tap to Show",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    } else if (isCollapsedToGoalBubble) {
                        val isDarkBubbleBg = remember(project.canvasBgColorHex, canvasBgBitmap) {
                            canvasBgBitmap != null ||
                                (canvasBg.red * 0.299f + canvasBg.green * 0.587f + canvasBg.blue * 0.114f) < 0.55f ||
                                canvasBg.alpha < 0.65f
                        }
                        val bubbleContentColor = if (isDarkBubbleBg) Color.White else Color(0xFF0F172A)
                        // Minimized Goal Bubble Preview (with Logo or Panel Name)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(CircleShape)
                                    .background(canvasBg)
                                    .border(BorderStroke(2.dp, bubbleContentColor.copy(alpha = 0.7f)), CircleShape)
                                    .clickable { isCollapsedToGoalBubble = false }
                                    .testTag("canvas_minimized_goal_bubble"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (floatingLogoBitmap != null) {
                                    Image(
                                        bitmap = floatingLogoBitmap,
                                        contentDescription = "Floating Goal Bubble Logo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Text(
                                        text = resolvedPanelTitle,
                                        color = bubbleContentColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(4.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Tap bubble to expand Floating Window",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp
                            )
                        }
                    } else {
                        val isDarkBg = remember(project.canvasBgColorHex, canvasBgBitmap) {
                            canvasBgBitmap != null ||
                                (canvasBg.red * 0.299f + canvasBg.green * 0.587f + canvasBg.blue * 0.114f) < 0.55f ||
                                canvasBg.alpha < 0.65f
                        }
                        val headerTextColor = if (isDarkBg) Color.White else Color(0xFF0F172A)

                        // Active Floating Panel inside Phone Screen — background covers 100% of the floating window area
                        Box(
                            modifier = Modifier
                                .size(
                                    width = dragCanvasWidthDp.dp.coerceIn(180.dp, 340.dp),
                                    height = dragCanvasHeightDp.dp.coerceIn(180.dp, 480.dp)
                                )
                                .clip(RoundedCornerShape(16.dp))
                                .background(canvasBg)
                                .testTag("overlay_canvas_window")
                        ) {
                            if (canvasBgBitmap != null) {
                                Image(
                                    bitmap = canvasBgBitmap,
                                    contentDescription = "Floating Window Background Image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(16.dp))
                                )
                            }

                            Column(modifier = Modifier.fillMaxSize()) {
                                // Floating Window Header Bar (Transparent so background covers whole floating window)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenEditFloatingPanel() }
                                        .padding(horizontal = 8.dp, vertical = 7.dp)
                                        .testTag("floating_panel_header_bar"),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isDarkBg) Color.White.copy(alpha = 0.16f)
                                                    else Color.Black.copy(alpha = 0.08f)
                                                )
                                                .border(BorderStroke(1.dp, headerTextColor.copy(alpha = 0.7f)), CircleShape)
                                                .clickable { onOpenEditFloatingPanel() }
                                                .testTag("floating_panel_header_logo"),
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
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Customize Panel Header",
                                                    tint = headerTextColor,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = resolvedPanelTitle,
                                            color = headerTextColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        // Minimize button (collapses to Floating Bubble)
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isDarkBg) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.08f),
                                            border = BorderStroke(1.dp, headerTextColor.copy(alpha = 0.75f)),
                                            modifier = Modifier
                                                .clickable {
                                                    isKilledFloatingWindow = false
                                                    isHiddenFloatingWindow = false
                                                    isCollapsedToGoalBubble = true
                                                }
                                                .testTag("floating_panel_collapse_button")
                                        ) {
                                            Text(
                                                text = "Minimize",
                                                color = headerTextColor,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.5.dp)
                                            )
                                        }

                                        // Hide button (hides Floating Window)
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isDarkBg) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.08f),
                                            border = BorderStroke(1.dp, headerTextColor.copy(alpha = 0.75f)),
                                            modifier = Modifier
                                                .clickable {
                                                    isKilledFloatingWindow = false
                                                    isCollapsedToGoalBubble = false
                                                    isHiddenFloatingWindow = true
                                                }
                                                .testTag("floating_panel_hide_button")
                                        ) {
                                            Text(
                                                text = "Hide",
                                                color = headerTextColor,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.5.dp)
                                            )
                                        }

                                        // Kill button (completely closes Floating Window)
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFEF4444).copy(alpha = 0.85f),
                                            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                            modifier = Modifier
                                                .clickable {
                                                    isCollapsedToGoalBubble = false
                                                    isHiddenFloatingWindow = false
                                                    isKilledFloatingWindow = true
                                                }
                                                .testTag("floating_panel_kill_button")
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

                                // Widget Canvas Area inside Floating Panel
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (components.isEmpty()) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = "Empty Floating Panel",
                                                    color = if (isDarkBg) Color(0xFFE2E8F0) else Color(0xFF475569),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = "Tap any item on the left palette to add widgets",
                                                    color = if (isDarkBg) Color(0xFF94A3B8) else Color(0xFF64748B),
                                                    fontSize = 10.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                                Spacer(Modifier.height(10.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFF4F46E5).copy(alpha = 0.9f),
                                                    border = BorderStroke(1.dp, Color(0xFF818CF8)),
                                                    modifier = Modifier
                                                        .clickable { onOpenChangeBackground() }
                                                        .testTag("empty_canvas_change_bg_button")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Palette,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Text(
                                                            text = "Change Background",
                                                            color = Color.White,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        components.forEach { comp ->
                                            val isSelected = comp.id == selectedComponentId
                                            var offsetX by remember(comp.id, comp.posXDp) {
                                                mutableFloatStateOf(with(density) { comp.posXDp.dp.toPx() })
                                            }
                                            var offsetY by remember(comp.id, comp.posYDp) {
                                                mutableFloatStateOf(with(density) { comp.posYDp.dp.toPx() })
                                            }

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
                                                isSelected -> Color(0xFF4F46E5)
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
                                                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                                                    .size(comp.widthDp.dp, comp.heightDp.dp)
                                                    .border(
                                                        width = if (isSelected || isToggle) 2.dp else 1.dp,
                                                        color = borderColor,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable {
                                                        onSelectComponent(comp.id)
                                                        val nextVal = if (isToggle) {
                                                            if (isChecked) "0" else "1"
                                                        } else {
                                                            comp.currentValue
                                                        }
                                                        onTriggerComponent(comp, nextVal)
                                                    }
                                                    .pointerInput(comp.id, project.autoFixSize) {
                                                        if (!project.autoFixSize) {
                                                            detectDragGestures(
                                                                onDragStart = { onSelectComponent(comp.id) },
                                                                onDragEnd = {
                                                                    val newXDp = with(density) { offsetX.toDp().value.roundToInt() }.coerceAtLeast(0)
                                                                    val newYDp = with(density) { offsetY.toDp().value.roundToInt() }.coerceAtLeast(0)
                                                                    onMoveComponent(comp.id, newXDp, newYDp)
                                                                },
                                                                onDrag = { change, dragAmount ->
                                                                    change.consume()
                                                                    offsetX = (offsetX + dragAmount.x).coerceAtLeast(0f)
                                                                    offsetY = (offsetY + dragAmount.y).coerceAtLeast(0f)
                                                                }
                                                            )
                                                        }
                                                    }
                                                    .testTag("canvas_widget_${comp.id}")
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
                                                                    onSelectComponent(comp.id)
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

                                // Corner Resize Handle for Floating Panel Window
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(topStart = 8.dp, bottomEnd = 16.dp))
                                        .background(
                                            if (isDarkBg) Color.White.copy(alpha = 0.16f)
                                            else Color.Black.copy(alpha = 0.12f)
                                        )
                                        .pointerInput(project.id) {
                                            detectDragGestures(
                                                onDragEnd = {
                                                    onResizeCanvas(
                                                        dragCanvasWidthDp.roundToInt(),
                                                        dragCanvasHeightDp.roundToInt()
                                                    )
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    val dxDp = with(density) { dragAmount.x.toDp().value }
                                                    val dyDp = with(density) { dragAmount.y.toDp().value }
                                                    dragCanvasWidthDp = (dragCanvasWidthDp + dxDp).coerceIn(180f, 340f)
                                                    dragCanvasHeightDp = (dragCanvasHeightDp + dyDp).coerceIn(180f, 480f)
                                                }
                                            )
                                        }
                                        .testTag("canvas_resize_handle"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "↘",
                                        color = headerTextColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
fun AuditHistoryCard(
    audits: List<ConfigWriteAuditEntity>,
    onClearAudits: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Binary Offset Write Log (${audits.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                if (audits.isNotEmpty()) {
                    TextButton(onClick = onClearAudits) {
                        Text("Clear")
                    }
                }
            }
            audits.take(5).forEach { entry ->
                Text(
                    text = "${entry.parameterKey} @ ${entry.byteOffsetHex} → ${entry.newValue} (CRC32 ${entry.crc32Hex})",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF334155),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun DownloadApkReadyDialog(
    apkFileName: String,
    apkFilePath: String,
    publicDownloadPath: String,
    apkSizeBytes: Long,
    packageName: String,
    onSaveToDeviceStorage: () -> Unit,
    onShareOrInstallApk: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Floating Window APK Built",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("File: $apkFileName", fontWeight = FontWeight.SemiBold)
                Text("Package: $packageName", fontSize = 12.sp)
                Text("Size: ${apkSizeBytes / 1024} KB", fontSize = 12.sp)
                if (publicDownloadPath.isNotBlank()) {
                    Text("Downloads: $publicDownloadPath", fontSize = 11.sp, color = Color(0xFF059669))
                } else {
                    Text("Path: $apkFilePath", fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSaveToDeviceStorage,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                modifier = Modifier.testTag("dialog_save_apk_button")
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Save APK As...")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onShareOrInstallApk,
                    modifier = Modifier.testTag("dialog_install_apk_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Install / Share")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}
