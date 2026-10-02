package com.example.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
import com.example.data.isScreen1WidgetType
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

data class Screen1DhancheSlot(
    val widgetType: String,
    val title: String,
    val recX: Int,
    val recY: Int,
    val recW: Int,
    val recH: Int
)

val Screen1RecommendedDhancheSlots = listOf(
    Screen1DhancheSlot("S1_IMAGE", "Image View Frame", 23, 18, 230, 96),
    Screen1DhancheSlot("S1_TEXT", "TextView Frame", 23, 126, 230, 40),
    Screen1DhancheSlot("S1_START", "Start Button Frame", 23, 180, 230, 48),
    Screen1DhancheSlot("S1_STOP", "Stop Button Frame", 23, 240, 230, 48),
    Screen1DhancheSlot("S1_LINK", "Link Open Frame", 23, 304, 230, 44)
)

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
    activePreviewScreen: Int = 2,
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
    val screen1Components = remember(components) {
        components.filter { isScreen1WidgetType(it.type) }
    }
    val screen2Components = remember(components) {
        components.filter { !isScreen1WidgetType(it.type) }
    }

    Row(modifier = modifier.fillMaxSize()) {
        // LEFT SIDE WIDGET PALETTE (Switches between Screen 1 and Screen 2 widgets)
        LeftSideWidgetPalette(
            activePreviewScreen = activePreviewScreen,
            isAutoFixSize = project.autoFixSize,
            onSelectPaletteEntry = onAddPaletteEntry,
            onToggleAutoFixSize = onToggleAutoFixSize
        )

        if (activePreviewScreen == 1) {
            // PREVIEW 1: MAIN SCREEN EDITOR (Start/Stop, TextView, Link Open, ImageView + Fix Dhanche + Corner Resize)
            InteractiveMainScreen1Canvas(
                project = project,
                components = screen1Components,
                selectedComponentId = selectedComponentId,
                onSelectComponent = onSelectComponent,
                onMoveComponent = { id, newX, newY ->
                    screen1Components.find { it.id == id }?.let { comp ->
                        onMoveComponent(comp, newX, newY)
                    }
                },
                onResizeComponent = { id, newW, newH ->
                    screen1Components.find { it.id == id }?.let { comp ->
                        onResizeComponent(comp, newW, newH)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        } else {
            // PREVIEW 2: FLOATING WINDOW EDITOR (Unchanged)
            InteractiveOverlayCanvas(
                project = project,
                components = screen2Components,
                selectedComponentId = selectedComponentId,
                onSelectComponent = onSelectComponent,
                onMoveComponent = { id, newX, newY ->
                    screen2Components.find { it.id == id }?.let { comp ->
                        onMoveComponent(comp, newX, newY)
                    }
                },
                onResizeComponent = { id, newW, newH ->
                    screen2Components.find { it.id == id }?.let { comp ->
                        onResizeComponent(comp, newW, newH)
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
}

@Composable
private fun LeftSideWidgetPalette(
    activePreviewScreen: Int = 2,
    isAutoFixSize: Boolean,
    onSelectPaletteEntry: (SketchwarePaletteEntry) -> Unit,
    onToggleAutoFixSize: () -> Unit
) {
    val screen2WidgetItems = remember {
        listOf(
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("Switch", ComponentWidgetType.TOGGLE, 196, 42),
                icon = Icons.Default.ToggleOn,
                iconTint = Color(0xFF10B981),
                tagSlug = "toggle"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("Button", ComponentWidgetType.BUTTON, 196, 42),
                icon = Icons.Default.SmartButton,
                iconTint = Color(0xFFFB923C),
                tagSlug = "button"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("Link Opener", ComponentWidgetType.LINK, 196, 42),
                icon = Icons.Default.Link,
                iconTint = Color(0xFF38BDF8),
                tagSlug = "link"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("Slider 0 to 100", ComponentWidgetType.SLIDER, 196, 52),
                icon = Icons.Default.Tune,
                iconTint = Color(0xFFA78BFA),
                tagSlug = "slider"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("EditText", ComponentWidgetType.INPUT, 196, 46),
                icon = Icons.Default.Edit,
                iconTint = Color(0xFFFBBF24),
                tagSlug = "input"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("TextView", ComponentWidgetType.TEXT, 196, 36),
                icon = Icons.Default.TextFields,
                iconTint = Color(0xFFF472B6),
                tagSlug = "text"
            )
        )
    }

    val screen1WidgetItems = remember {
        listOf(
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("Floating Panel Start", ComponentWidgetType.S1_START, 210, 46, "#10B981", "#FFFFFF"),
                icon = Icons.Default.PlayArrow,
                iconTint = Color(0xFF10B981),
                tagSlug = "s1_start"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("Floating Panel Stop", ComponentWidgetType.S1_STOP, 210, 46, "#EF4444", "#FFFFFF"),
                icon = Icons.Default.Stop,
                iconTint = Color(0xFFEF4444),
                tagSlug = "s1_stop"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("Text View", ComponentWidgetType.S1_TEXT, 220, 52, "#00000000", "#FFFFFF"),
                icon = Icons.Default.TextFields,
                iconTint = Color(0xFF6366F1),
                tagSlug = "s1_text"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("Link Open", ComponentWidgetType.S1_LINK, 220, 52, "#00000000", "#38BDF8"),
                icon = Icons.Default.Link,
                iconTint = Color(0xFF2563EB),
                tagSlug = "s1_link"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("Image View", ComponentWidgetType.S1_IMAGE, 118, 98, "#00000000", "#FFFFFF"),
                icon = Icons.Default.Image,
                iconTint = Color(0xFFF59E0B),
                tagSlug = "s1_image"
            )
        )
    }

    val activeItems = if (activePreviewScreen == 1) screen1WidgetItems else screen2WidgetItems

    Surface(
        color = Color(0xFF0E1526),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier
            .width(128.dp)
            .fillMaxHeight()
            .testTag("left_widget_palette_sidebar")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (activePreviewScreen == 1) {
                    PaletteCategoryHeader("Widgets", Color(0xFF38BDF8))
                } else {
                    PaletteCategoryHeader("Widgets (6)", Color(0xFFFB923C))
                }
                activeItems.forEach { item ->
                    LeftPaletteItemRow(
                        item = item,
                        onClick = { onSelectPaletteEntry(item.entry) }
                    )
                }
            }

            if (activePreviewScreen == 2) {
                // Bottom pinned Auto Size button for Screen 2
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
            } else {
                // Screen 1 info badge: Dynamic Full-Screen Dhancha
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF090D18))
                        .border(BorderStroke(0.5.dp, Color(0xFF1E293B)))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Dynamic Dhancha\n& Snap Guides ON",
                        color = Color(0xFF38BDF8),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 12.sp
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

private val RgbLightSweepColors = intArrayOf(
    android.graphics.Color.parseColor("#FF0040"),
    android.graphics.Color.parseColor("#FF8000"),
    android.graphics.Color.parseColor("#FFEE00"),
    android.graphics.Color.parseColor("#00FF40"),
    android.graphics.Color.parseColor("#00E5FF"),
    android.graphics.Color.parseColor("#2979FF"),
    android.graphics.Color.parseColor("#AA00FF"),
    android.graphics.Color.parseColor("#FF00AA"),
    android.graphics.Color.parseColor("#FF0040")
)

fun Modifier.drawAnimatedWidgetCornerBorder(
    borderColorHex: String,
    borderStrokePercent: Int,
    borderAnimation: String,
    cornerRadiusDp: Float = 8f,
    rgbSweepAngleDeg: Float = 0f,
    pulseProgress: Float = 1f,
    blinkProgress: Float = 1f,
    isSelectedFallback: Boolean = false
): Modifier = this.drawWithContent {
    drawContent()

    val animMode = borderAnimation.trim().uppercase()
    val hasActiveAnim = animMode != "NONE" && animMode.isNotEmpty()
    val effectivePct = if (hasActiveAnim && borderStrokePercent in 1..24) {
        30
    } else if (hasActiveAnim && borderStrokePercent <= 0) {
        35
    } else {
        borderStrokePercent.coerceIn(0, 100)
    }

    val isTransparentStatic = !hasActiveAnim && (
        effectivePct <= 0 || borderColorHex.trim().equals("#00000000", ignoreCase = true)
    )
    if (isTransparentStatic && !isSelectedFallback) {
        return@drawWithContent
    }

    val baseColor = if (borderColorHex.trim().equals("#00000000", ignoreCase = true)) {
        Color(0xFF38BDF8)
    } else {
        parseHexColorSafe(borderColorHex, Color(0xFF38BDF8))
    }

    val rawStrokePx = if (isTransparentStatic && isSelectedFallback) {
        1.5.dp.toPx()
    } else {
        ((effectivePct / 100f) * 8f).coerceIn(1.2f, 8.5f).dp.toPx()
    }
    val cornerPx = cornerRadiusDp.dp.toPx()
    val nativeCanvas = drawContext.canvas.nativeCanvas

    when (animMode) {
        "RGB_LIGHT" -> {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val matrix = android.graphics.Matrix().apply {
                setRotate(rgbSweepAngleDeg, cx, cy)
            }
            val sweepShader = android.graphics.SweepGradient(cx, cy, RgbLightSweepColors, null).apply {
                setLocalMatrix(matrix)
            }

            // Outer glowing RGB halo
            val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = rawStrokePx * 1.85f
                shader = sweepShader
                alpha = 115
            }
            val glowInset = (rawStrokePx * 1.85f) / 2f
            nativeCanvas.drawRoundRect(
                glowInset,
                glowInset,
                size.width - glowInset,
                size.height - glowInset,
                cornerPx,
                cornerPx,
                glowPaint
            )

            // Crisp rotating RGB core border line
            val corePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = rawStrokePx
                shader = sweepShader
                alpha = 255
            }
            val inset = rawStrokePx / 2f
            nativeCanvas.drawRoundRect(
                inset,
                inset,
                size.width - inset,
                size.height - inset,
                cornerPx,
                cornerPx,
                corePaint
            )
        }

        "RAINBOW" -> {
            val rainbowInt = android.graphics.Color.HSVToColor(
                floatArrayOf(rgbSweepAngleDeg % 360f, 0.92f, 1.0f)
            )
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = rawStrokePx
                color = rainbowInt
            }
            val inset = rawStrokePx / 2f
            nativeCanvas.drawRoundRect(
                inset,
                inset,
                size.width - inset,
                size.height - inset,
                cornerPx,
                cornerPx,
                paint
            )
        }

        "PULSE" -> {
            val animatedStroke = rawStrokePx * (0.65f + 0.55f * pulseProgress)
            val alphaInt = (55 + (pulseProgress * 200f)).roundToInt().coerceIn(45, 255)
            val baseArgb = baseColor.toArgb()
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = animatedStroke
                color = android.graphics.Color.argb(
                    alphaInt,
                    android.graphics.Color.red(baseArgb),
                    android.graphics.Color.green(baseArgb),
                    android.graphics.Color.blue(baseArgb)
                )
            }
            val inset = animatedStroke / 2f
            nativeCanvas.drawRoundRect(
                inset,
                inset,
                size.width - inset,
                size.height - inset,
                cornerPx,
                cornerPx,
                paint
            )
        }

        "NEON_BLINK" -> {
            val isBright = blinkProgress >= 0.45f
            val baseArgb = baseColor.toArgb()
            val alphaInt = if (isBright) 255 else 35
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = if (isBright) rawStrokePx * 1.18f else rawStrokePx * 0.85f
                color = android.graphics.Color.argb(
                    alphaInt,
                    android.graphics.Color.red(baseArgb),
                    android.graphics.Color.green(baseArgb),
                    android.graphics.Color.blue(baseArgb)
                )
            }
            val inset = paint.strokeWidth / 2f
            nativeCanvas.drawRoundRect(
                inset,
                inset,
                size.width - inset,
                size.height - inset,
                cornerPx,
                cornerPx,
                paint
            )
        }

        "GLOW" -> {
            val baseArgb = baseColor.toArgb()
            val outerStroke = rawStrokePx * (1.35f + 0.85f * pulseProgress)
            val outerAlpha = (65 + (pulseProgress * 135f)).roundToInt().coerceIn(50, 210)
            val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = outerStroke
                color = android.graphics.Color.argb(
                    outerAlpha,
                    android.graphics.Color.red(baseArgb),
                    android.graphics.Color.green(baseArgb),
                    android.graphics.Color.blue(baseArgb)
                )
            }
            val outerInset = outerStroke / 2f
            nativeCanvas.drawRoundRect(
                outerInset,
                outerInset,
                size.width - outerInset,
                size.height - outerInset,
                cornerPx,
                cornerPx,
                glowPaint
            )

            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(baseArgb, hsv)
            hsv[1] = (hsv[1] * (1f - 0.45f * pulseProgress)).coerceIn(0f, 1f)
            hsv[2] = 1f
            val corePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = rawStrokePx
                color = android.graphics.Color.HSVToColor(hsv)
            }
            val coreInset = rawStrokePx / 2f
            nativeCanvas.drawRoundRect(
                coreInset,
                coreInset,
                size.width - coreInset,
                size.height - coreInset,
                cornerPx,
                cornerPx,
                corePaint
            )
        }

        else -> {
            val finalColor = if (isTransparentStatic && isSelectedFallback) {
                Color(0xFF38BDF8).copy(alpha = 0.7f)
            } else {
                baseColor
            }
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = rawStrokePx
                color = finalColor.toArgb()
            }
            val inset = rawStrokePx / 2f
            nativeCanvas.drawRoundRect(
                inset,
                inset,
                size.width - inset,
                size.height - inset,
                cornerPx,
                cornerPx,
                paint
            )
        }
    }
}

@Composable
fun WidgetPaletteStrip(
    onAddWidget: (ComponentWidgetType) -> Unit
) {
    val allowedWidgets = remember {
        listOf(
            ComponentWidgetType.TOGGLE,
            ComponentWidgetType.BUTTON,
            ComponentWidgetType.LINK,
            ComponentWidgetType.SLIDER,
            ComponentWidgetType.INPUT,
            ComponentWidgetType.TEXT
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        allowedWidgets.forEach { widgetType ->
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
fun InteractiveMainScreen1Canvas(
    project: StudioProjectEntity,
    components: List<CanvasComponentEntity>,
    selectedComponentId: Long?,
    onSelectComponent: (Long?) -> Unit,
    onMoveComponent: (Long, Int, Int) -> Unit,
    onResizeComponent: (Long, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val selectedComp = remember(components, selectedComponentId) {
        components.find { it.id == selectedComponentId }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF060A14))
            .clickable { onSelectComponent(null) }
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer Phone Device Frame — Full-Screen Preview with Dynamic Layout (Dhancha)
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF090F20)),
            border = BorderStroke(2.dp, Color(0xFF1E325C)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 2.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Phone Status Bar ("9:41" ... status icons)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF070C1A))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "9:41",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "▂▄▆",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "WiFi",
                            color = Color(0xFF94A3B8),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = Color.White,
                            modifier = Modifier.size(width = 14.dp, height = 7.dp)
                        ) {}
                    }
                }

                // 2. Screen Editor Sub-Header inside Phone Screen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0A1124))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "←",
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Screen Editor",
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF15203B),
                            border = BorderStroke(1.dp, Color(0xFF283B66))
                        ) {
                            Text(
                                text = "◉ Preview 1",
                                color = Color(0xFF38BDF8),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // 3. Full-Screen Dynamic Layout (Dhancha) Workspace (NO fixed-size boxes!)
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF0C1832),
                                    Color(0xFF112244),
                                    Color(0xFF0A1328)
                                )
                            )
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("screen1_main_canvas_window")
                ) {
                    val canvasWidthDp = maxWidth.value.coerceAtLeast(220f)
                    val canvasHeightDp = maxHeight.value.coerceAtLeast(300f)

                    // Full-screen Dynamic Grid + Outer Dhancha Frame + Dashed Alignment/Snap Guide Lines
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val gridStepPx = 22.dp.toPx()

                        // Subtle full-screen blueprint grid lines
                        var gx = gridStepPx
                        while (gx < w) {
                            drawLine(
                                color = Color(0xFF38BDF8).copy(alpha = 0.07f),
                                start = Offset(gx, 0f),
                                end = Offset(gx, h),
                                strokeWidth = 1f
                            )
                            gx += gridStepPx
                        }
                        var gy = gridStepPx
                        while (gy < h) {
                            drawLine(
                                color = Color(0xFF38BDF8).copy(alpha = 0.07f),
                                start = Offset(0f, gy),
                                end = Offset(w, gy),
                                strokeWidth = 1f
                            )
                            gy += gridStepPx
                        }

                        // Dashed Center Vertical & Horizontal Responsive Alignment Guides
                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                        val centerX = w / 2f
                        val centerY = h / 2f

                        drawLine(
                            color = Color(0xFF60A5FA).copy(alpha = 0.42f),
                            start = Offset(centerX, 0f),
                            end = Offset(centerX, h),
                            strokeWidth = 1.4.dp.toPx(),
                            pathEffect = dashEffect
                        )
                        drawLine(
                            color = Color(0xFF60A5FA).copy(alpha = 0.25f),
                            start = Offset(0f, centerY),
                            end = Offset(w, centerY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashEffect
                        )

                        // Dynamic Widget Snap Guide Lines for each placed widget
                        components.forEach { item ->
                            val itemTopPx = item.posYDp.dp.toPx().coerceIn(0f, h)
                            val itemBottomPx = (item.posYDp + item.heightDp).dp.toPx().coerceIn(0f, h)
                            val itemLeftPx = item.posXDp.dp.toPx().coerceIn(0f, w)
                            val itemRightPx = (item.posXDp + item.widthDp).dp.toPx().coerceIn(0f, w)
                            val isItemSelected = item.id == selectedComponentId
                            val guideAlpha = if (isItemSelected) 0.55f else 0.22f
                            val guideColor = if (isItemSelected) Color(0xFF38BDF8) else Color(0xFF6366F1)

                            // Horizontal top/bottom alignment guides across the full screen
                            drawLine(
                                color = guideColor.copy(alpha = guideAlpha),
                                start = Offset(0f, itemTopPx),
                                end = Offset(w, itemTopPx),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = dashEffect
                            )
                            drawLine(
                                color = guideColor.copy(alpha = guideAlpha),
                                start = Offset(0f, itemBottomPx),
                                end = Offset(w, itemBottomPx),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = dashEffect
                            )

                            // Vertical left/right alignment guides when selected
                            if (isItemSelected) {
                                drawLine(
                                    color = Color(0xFF38BDF8).copy(alpha = 0.45f),
                                    start = Offset(itemLeftPx, 0f),
                                    end = Offset(itemLeftPx, h),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = dashEffect
                                )
                                drawLine(
                                    color = Color(0xFF38BDF8).copy(alpha = 0.45f),
                                    start = Offset(itemRightPx, 0f),
                                    end = Offset(itemRightPx, h),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = dashEffect
                                )
                            }
                        }

                        // Full-Screen Outer Dynamic Dhancha Border Frame
                        val frameColor = Color(0xFF6366F1)
                        val strokePx = 1.8.dp.toPx()
                        drawRect(
                            color = frameColor.copy(alpha = 0.85f),
                            topLeft = Offset(0f, 0f),
                            size = size,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePx)
                        )

                        // 8 Full-Screen Frame Anchor Squares (Corners + Midpoints, matching screenshot)
                        val handleSize = 7.dp.toPx()
                        val halfH = handleSize / 2f
                        val frameAnchorPoints = listOf(
                            Offset(0f, 0f),
                            Offset(w / 2f, 0f),
                            Offset(w, 0f),
                            Offset(0f, h / 2f),
                            Offset(w, h / 2f),
                            Offset(0f, h),
                            Offset(w / 2f, h),
                            Offset(w, h)
                        )
                        frameAnchorPoints.forEach { pt ->
                            drawRect(
                                color = Color(0xFF818CF8),
                                topLeft = Offset(
                                    (pt.x - halfH).coerceIn(0f, w - handleSize),
                                    (pt.y - halfH).coerceIn(0f, h - handleSize)
                                ),
                                size = androidx.compose.ui.geometry.Size(handleSize, handleSize)
                            )
                        }
                    }

                    // Empty state hint if no widgets placed yet (clean, no fixed boxes)
                    if (components.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF0F172A).copy(alpha = 0.82f),
                                border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.6f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Full-Screen Dynamic Layout (Dhancha)",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Tap Floating Panel Start, Stop, Text View, Link Open, or Image View on the left to place on the responsive guide grid.",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Render Placed Widgets on the Full-Screen Dynamic Dhancha
                    components.forEach { comp ->
                        val isSelected = comp.id == selectedComponentId
                        val matchingSlot = remember(comp.type) {
                            Screen1RecommendedDhancheSlots.find {
                                it.widgetType.equals(comp.type, ignoreCase = true)
                            }
                        }

                        var liveWidthDp by remember(comp.id, comp.widthDp) {
                            mutableFloatStateOf(
                                comp.widthDp.toFloat().coerceIn(60f, (canvasWidthDp - 12f).coerceAtLeast(120f))
                            )
                        }
                        var liveHeightDp by remember(comp.id, comp.heightDp) {
                            mutableFloatStateOf(
                                comp.heightDp.toFloat().coerceIn(32f, (canvasHeightDp - 16f).coerceAtLeast(120f))
                            )
                        }

                        var offsetX by remember(comp.id, comp.posXDp) {
                            mutableFloatStateOf(with(density) { comp.posXDp.dp.toPx() })
                        }
                        var offsetY by remember(comp.id, comp.posYDp) {
                            mutableFloatStateOf(with(density) { comp.posYDp.dp.toPx() })
                        }

                        val typeUpper = comp.type.trim().uppercase()
                        val isStart = typeUpper == "S1_START"
                        val isStop = typeUpper == "S1_STOP"
                        val isText = typeUpper == "S1_TEXT"
                        val isLink = typeUpper == "S1_LINK"
                        val isImage = typeUpper == "S1_IMAGE"
                        val isTextOrLink = isText || isLink

                        // Accent border color per widget type matching the screenshot
                        val accentBorderColor = when {
                            isStart -> Color(0xFF10B981)
                            isStop -> Color(0xFFEF4444)
                            isText -> Color(0xFF818CF8)
                            isLink -> Color(0xFF38BDF8)
                            isImage -> Color(0xFF38BDF8)
                            else -> Color(0xFF38BDF8)
                        }

                        // Background color: S1_TEXT and S1_LINK are always transparent for Screen 1
                        val widgetBgColor = when {
                            isTextOrLink -> Color(0xFF1E293B).copy(alpha = 0.32f)
                            isImage -> Color(0xFF0F172A).copy(alpha = 0.45f)
                            else -> {
                                val parsed = parseHexColorSafe(comp.bgColorHex, if (isStart) Color(0xFF10B981) else Color(0xFFEF4444))
                                if (parsed.alpha == 0f) Color.Transparent else parsed.copy(alpha = 0.24f)
                            }
                        }

                        val widgetTextColor = if (isTextOrLink) {
                            val parsed = parseHexColorSafe(comp.textColorHex, Color.White)
                            if (parsed.alpha == 0f) Color.White else parsed
                        } else {
                            parseHexColorSafe(comp.textColorHex, Color.White)
                        }

                        val bgBitmap = remember(comp.bgImagePath) {
                            if (comp.bgImagePath.isNotBlank()) {
                                val f = File(comp.bgImagePath)
                                if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
                            } else null
                        }

                        val customBitmap = remember(comp.customImagePath, comp.bgImagePath) {
                            val path = comp.customImagePath.ifBlank { comp.bgImagePath }
                            if (path.isNotBlank()) {
                                val f = File(path)
                                if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
                            } else null
                        }

                        val centeredXDp = ((canvasWidthDp - liveWidthDp) / 2f).roundToInt().coerceAtLeast(0)
                        val currentXDp = with(density) { offsetX.toDp().value.roundToInt() }
                        val isCenteredSnap = abs(currentXDp - centeredXDp) <= 6

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                                .size(liveWidthDp.dp, liveHeightDp.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(widgetBgColor)
                                .border(
                                    BorderStroke(
                                        width = if (isSelected) 2.dp else 1.4.dp,
                                        color = if (isSelected) accentBorderColor else accentBorderColor.copy(alpha = 0.8f)
                                    ),
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    onSelectComponent(comp.id)
                                }
                                .pointerInput(comp.id, canvasWidthDp, canvasHeightDp) {
                                    detectDragGestures(
                                        onDragStart = { onSelectComponent(comp.id) },
                                        onDragEnd = {
                                            val maxXDp = (canvasWidthDp - liveWidthDp).roundToInt().coerceAtLeast(0)
                                            val maxYDp = (canvasHeightDp - liveHeightDp).roundToInt().coerceAtLeast(0)
                                            var finalXDp = with(density) { offsetX.toDp().value.roundToInt() }.coerceIn(0, maxXDp)
                                            var finalYDp = with(density) { offsetY.toDp().value.roundToInt() }.coerceIn(0, maxYDp)

                                            // Smooth Snap-to-Center X guide ("thoda sa atke")
                                            val targetCenterX = ((canvasWidthDp - liveWidthDp) / 2f).roundToInt().coerceAtLeast(0)
                                            if (abs(finalXDp - targetCenterX) <= 16) {
                                                finalXDp = targetCenterX
                                            }

                                            // Smooth Snap-to-Grid / Guide Y (22dp grid or recommended vertical rhythm)
                                            val nearestGridY = ((finalYDp / 22f).roundToInt() * 22).coerceIn(0, maxYDp)
                                            if (abs(finalYDp - nearestGridY) <= 8) {
                                                finalYDp = nearestGridY
                                            }
                                            if (matchingSlot != null && abs(finalYDp - matchingSlot.recY) <= 14) {
                                                finalYDp = matchingSlot.recY.coerceIn(0, maxYDp)
                                            }

                                            offsetX = with(density) { finalXDp.dp.toPx() }
                                            offsetY = with(density) { finalYDp.dp.toPx() }
                                            onMoveComponent(comp.id, finalXDp, finalYDp)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val maxXPx = with(density) { (canvasWidthDp - liveWidthDp).coerceAtLeast(0f).dp.toPx() }
                                            val maxYPx = with(density) { (canvasHeightDp - liveHeightDp).coerceAtLeast(0f).dp.toPx() }
                                            var nextX = (offsetX + dragAmount.x).coerceIn(0f, maxXPx)
                                            val nextY = (offsetY + dragAmount.y).coerceIn(0f, maxYPx)

                                            // Live magnetic catch on the center vertical guide line
                                            val centerXPx = with(density) { ((canvasWidthDp - liveWidthDp) / 2f).coerceAtLeast(0f).dp.toPx() }
                                            if (abs(nextX - centerXPx) <= with(density) { 5.dp.toPx() }) {
                                                nextX = centerXPx
                                            }

                                            offsetX = nextX
                                            offsetY = nextY
                                        }
                                    )
                                }
                                .testTag("screen1_canvas_widget_${comp.id}")
                        ) {
                            when (typeUpper) {
                                "S1_START", "S1_STOP" -> {
                                    val solidTint = parseHexColorSafe(
                                        comp.bgColorHex,
                                        if (isStart) Color(0xFF10B981) else Color(0xFFEF4444)
                                    )
                                    if (bgBitmap != null) {
                                        Image(
                                            bitmap = bgBitmap,
                                            contentDescription = "${comp.label} Background",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(14.dp))
                                        )
                                    } else if (solidTint.alpha > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.horizontalGradient(
                                                        colors = listOf(
                                                            solidTint.copy(alpha = 0.38f),
                                                            solidTint.copy(alpha = 0.18f)
                                                        )
                                                    )
                                                )
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(if (isStart) Color(0xFF10B981) else Color(0xFFEF4444)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isStart) Icons.Default.PlayArrow else Icons.Default.Stop,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = comp.label,
                                            color = widgetTextColor,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                "S1_TEXT" -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(Color(0xFF6366F1)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "T",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Text View",
                                                color = Color(0xFFCBD5E1),
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = comp.label,
                                                color = widgetTextColor,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                "S1_LINK" -> {
                                    // Transparent background; shows custom side logo if added, or clean text if not added
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (customBitmap != null) {
                                                Image(
                                                    bitmap = customBitmap,
                                                    contentDescription = "Link Side Logo",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(26.dp)
                                                        .clip(RoundedCornerShape(7.dp))
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = comp.label,
                                                    color = widgetTextColor,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (comp.linkUrl.isNotBlank()) {
                                                    Text(
                                                        text = comp.linkUrl,
                                                        color = Color(0xFF94A3B8),
                                                        fontSize = 9.5.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "›",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                "S1_IMAGE" -> {
                                    if (customBitmap != null) {
                                        Image(
                                            bitmap = customBitmap,
                                            contentDescription = comp.label,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(14.dp))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(10.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(Color(0xFF312E81), Color(0xFF1E3A8A))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Image,
                                                    contentDescription = "Select Image",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = "Image View",
                                                    color = Color(0xFFE2E8F0),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 8 Anchor Dots around Widget Boundary (matching screenshot)
                            val dotColor = accentBorderColor
                            val anchors = listOf(
                                Alignment.TopStart,
                                Alignment.TopCenter,
                                Alignment.TopEnd,
                                Alignment.CenterStart,
                                Alignment.CenterEnd,
                                Alignment.BottomStart,
                                Alignment.BottomCenter
                            )
                            anchors.forEach { align ->
                                Box(
                                    modifier = Modifier
                                        .align(align)
                                        .padding(1.dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                            }

                            // Snap-to-guide feedback pill when selected
                            if (isSelected && isCenteredSnap) {
                                Surface(
                                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                                    color = Color(0xFF059669).copy(alpha = 0.9f),
                                    modifier = Modifier.align(Alignment.TopStart)
                                ) {
                                    Text(
                                        text = "✓ Centered • ${liveWidthDp.roundToInt()}×${liveHeightDp.roundToInt()}",
                                        color = Color.White,
                                        fontSize = 7.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                    )
                                }
                            }

                            // Computer-Tab Corner Resize Handle (Bottom-Right ↘)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(topStart = 6.dp, bottomEnd = 12.dp))
                                    .background(
                                        if (isSelected) accentBorderColor.copy(alpha = 0.92f)
                                        else Color(0xFF0F172A).copy(alpha = 0.7f)
                                    )
                                    .pointerInput(comp.id, matchingSlot, canvasWidthDp, canvasHeightDp) {
                                        detectDragGestures(
                                            onDragStart = { onSelectComponent(comp.id) },
                                            onDragEnd = {
                                                val maxW = (canvasWidthDp - 12f).roundToInt().coerceAtLeast(100)
                                                val maxH = (canvasHeightDp - 16f).roundToInt().coerceAtLeast(100)
                                                var finalW = liveWidthDp.roundToInt().coerceIn(60, maxW)
                                                var finalH = liveHeightDp.roundToInt().coerceIn(32, maxH)

                                                // Magnetic size snap to responsive guide width or recommended height
                                                val recResponsiveW = (canvasWidthDp * 0.82f).roundToInt().coerceIn(140, maxW)
                                                if (abs(finalW - recResponsiveW) <= 14) {
                                                    finalW = recResponsiveW
                                                } else if (matchingSlot != null && abs(finalW - matchingSlot.recW) <= 12) {
                                                    finalW = matchingSlot.recW.coerceIn(60, maxW)
                                                }
                                                if (matchingSlot != null && abs(finalH - matchingSlot.recH) <= 10) {
                                                    finalH = matchingSlot.recH.coerceIn(32, maxH)
                                                }

                                                liveWidthDp = finalW.toFloat()
                                                liveHeightDp = finalH.toFloat()
                                                onResizeComponent(comp.id, finalW, finalH)
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val dxDp = with(density) { dragAmount.x.toDp().value }
                                                val dyDp = with(density) { dragAmount.y.toDp().value }
                                                val maxW = (canvasWidthDp - 12f).coerceAtLeast(100f)
                                                val maxH = (canvasHeightDp - 16f).coerceAtLeast(100f)
                                                var nextW = (liveWidthDp + dxDp).coerceIn(60f, maxW)
                                                var nextH = (liveHeightDp + dyDp).coerceIn(32f, maxH)

                                                if (matchingSlot != null) {
                                                    if (abs(nextW - matchingSlot.recW) <= 5f) {
                                                        nextW = matchingSlot.recW.toFloat()
                                                    }
                                                    if (abs(nextH - matchingSlot.recH) <= 5f) {
                                                        nextH = matchingSlot.recH.toFloat()
                                                    }
                                                }
                                                liveWidthDp = nextW
                                                liveHeightDp = nextH
                                            }
                                        )
                                    }
                                    .testTag("widget_resize_handle_${comp.id}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "↘",
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }

                // 4. Bottom Guide Pill inside Phone Preview (exact match with screenshot)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF070C1A))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color(0xFF283B66))
                    ) {
                        Text(
                            text = "Drag widgets • Snap to guide • Full screen layout",
                            color = Color(0xFFCBD5E1),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
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
    onResizeComponent: (Long, Int, Int) -> Unit = { _, _, _ -> },
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

                                        val infiniteTransition = rememberInfiniteTransition(label = "widgetBorderAnim")
                                        val rgbSweepAngle by infiniteTransition.animateFloat(
                                            initialValue = 0f,
                                            targetValue = 360f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(durationMillis = 1800, easing = LinearEasing),
                                                repeatMode = RepeatMode.Restart
                                            ),
                                            label = "rgbSweepAngle"
                                        )
                                        val pulseAlpha by infiniteTransition.animateFloat(
                                            initialValue = 0.0f,
                                            targetValue = 1.0f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(durationMillis = 700, easing = LinearEasing),
                                                repeatMode = RepeatMode.Reverse
                                            ),
                                            label = "pulseAlpha"
                                        )
                                        val blinkProgress by infiniteTransition.animateFloat(
                                            initialValue = 0.0f,
                                            targetValue = 1.0f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(durationMillis = 380, easing = LinearEasing),
                                                repeatMode = RepeatMode.Reverse
                                            ),
                                            label = "blinkProgress"
                                        )

                                        components.forEach { comp ->
                                            val isSelected = comp.id == selectedComponentId
                                            var offsetX by remember(comp.id, comp.posXDp, project.autoFixSize) {
                                                mutableFloatStateOf(
                                                    with(density) {
                                                        (if (project.autoFixSize) 14.dp else comp.posXDp.dp).toPx()
                                                    }
                                                )
                                            }
                                            var offsetY by remember(comp.id, comp.posYDp) {
                                                mutableFloatStateOf(with(density) { comp.posYDp.dp.toPx() })
                                            }

                                            val effectiveInitWidthDp = if (project.autoFixSize) {
                                                (dragCanvasWidthDp.roundToInt() - 28).coerceAtLeast(100)
                                            } else {
                                                comp.widthDp.coerceIn(60, 320)
                                            }
                                            var liveWidthDp by remember(comp.id, comp.widthDp, project.autoFixSize, dragCanvasWidthDp) {
                                                mutableFloatStateOf(effectiveInitWidthDp.toFloat())
                                            }
                                            var liveHeightDp by remember(comp.id, comp.heightDp) {
                                                mutableFloatStateOf(comp.heightDp.coerceIn(28, 220).toFloat())
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
                                                    .size(liveWidthDp.dp, liveHeightDp.dp)
                                                    .clickable {
                                                        // Preview screen NEVER executes target files or opens links! Only selects widget for editing.
                                                        onSelectComponent(comp.id)
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
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .drawAnimatedWidgetCornerBorder(
                                                            borderColorHex = comp.borderColorHex,
                                                            borderStrokePercent = comp.borderStrokePercent,
                                                            borderAnimation = comp.borderAnimation,
                                                            cornerRadiusDp = 8f,
                                                            rgbSweepAngleDeg = rgbSweepAngle,
                                                            pulseProgress = pulseAlpha,
                                                            blinkProgress = blinkProgress,
                                                            isSelectedFallback = isSelected
                                                        )
                                                ) {
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
                                                                text = if (comp.type == "LINK") "🔗 ${comp.label}" else comp.label,
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
                                                                    onValueChange = {
                                                                        // Only select in studio preview; do not execute target file
                                                                        onSelectComponent(comp.id)
                                                                    },
                                                                    valueRange = 0f..comp.sliderMax.toFloat().coerceAtLeast(1f),
                                                                    modifier = Modifier.height(22.dp)
                                                                )
                                                            } else if (comp.type == "INPUT") {
                                                                Text(
                                                                    text = comp.currentValue.ifBlank { "Enter value..." },
                                                                    color = widgetText.copy(alpha = 0.85f),
                                                                    fontSize = 10.sp,
                                                                    fontFamily = FontFamily.Monospace,
                                                                    maxLines = 1
                                                                )
                                                            } else if (comp.type == "LINK" && comp.linkUrl.isNotBlank()) {
                                                                Text(
                                                                    text = comp.linkUrl,
                                                                    color = widgetText.copy(alpha = 0.8f),
                                                                    fontSize = 9.sp,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                        if (isToggle) {
                                                            Switch(
                                                                checked = isChecked,
                                                                onCheckedChange = {
                                                                    // Preview screen never executes target file changes; only selects widget
                                                                    onSelectComponent(comp.id)
                                                                }
                                                            )
                                                        }
                                                    }

                                                    // Manual Widget Corner Resize Handle when Auto Size is OFF (computer tab style)
                                                    if (!project.autoFixSize) {
                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.BottomEnd)
                                                                .size(18.dp)
                                                                .clip(RoundedCornerShape(topStart = 6.dp, bottomEnd = 8.dp))
                                                                .background(
                                                                    if (isSelected) Color(0xFF38BDF8).copy(alpha = 0.85f)
                                                                    else Color(0xFF0F172A).copy(alpha = 0.55f)
                                                                )
                                                                .pointerInput(comp.id, project.autoFixSize) {
                                                                    detectDragGestures(
                                                                        onDragStart = { onSelectComponent(comp.id) },
                                                                        onDragEnd = {
                                                                            onResizeComponent(
                                                                                comp.id,
                                                                                liveWidthDp.roundToInt().coerceIn(60, 320),
                                                                                liveHeightDp.roundToInt().coerceIn(28, 220)
                                                                            )
                                                                        },
                                                                        onDrag = { change, dragAmount ->
                                                                            change.consume()
                                                                            val dxDp = with(density) { dragAmount.x.toDp().value }
                                                                            val dyDp = with(density) { dragAmount.y.toDp().value }
                                                                            liveWidthDp = (liveWidthDp + dxDp).coerceIn(60f, 320f)
                                                                            liveHeightDp = (liveHeightDp + dyDp).coerceIn(28f, 220f)
                                                                        }
                                                                    )
                                                                }
                                                                .testTag("widget_resize_handle_${comp.id}"),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = "↘",
                                                                color = Color.White,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.ExtraBold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Corner Resize Handle for Floating Panel Window (Works both when Auto Size is ON and OFF)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(topStart = 8.dp, bottomEnd = 16.dp))
                                        .background(
                                            if (isDarkBg) Color(0xFF38BDF8).copy(alpha = 0.35f)
                                            else Color(0xFF2563EB).copy(alpha = 0.25f)
                                        )
                                        .pointerInput(project.id, project.autoFixSize) {
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
                                        fontSize = 12.sp,
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
