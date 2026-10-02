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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
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
import com.example.data.isLuaScriptProject
import com.example.data.isScreen1WidgetType
import com.example.engine.LuaScriptEngine
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
    Screen1DhancheSlot("S1_START", "Start Button Frame", 18, 18, 220, 52),
    Screen1DhancheSlot("S1_STOP", "Stop Button Frame", 18, 82, 220, 52),
    Screen1DhancheSlot("S1_TEXT", "TextView Frame", 18, 146, 220, 52),
    Screen1DhancheSlot("S1_LINK", "Link Open Frame", 18, 210, 220, 52),
    Screen1DhancheSlot("S1_IMAGE", "Image View Frame", 18, 274, 220, 88)
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
fun AutoFitText(
    text: String,
    color: Color,
    maxFontSizeSp: Float,
    minFontSizeSp: Float = 4.5f,
    availableWidthDp: Float = 160f,
    availableHeightDp: Float = 42f,
    fontWeight: FontWeight = FontWeight.Bold,
    fontFamily: FontFamily? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
    modifier: Modifier = Modifier
) {
    val safeLen = text.length.coerceAtLeast(1)
    val charWidthFactor = if (maxLines > 1) 0.34f else 0.58f
    val estByWidth = (availableWidthDp / (safeLen * charWidthFactor)).coerceIn(minFontSizeSp, maxFontSizeSp)
    val estByHeight = (availableHeightDp * (if (maxLines > 1) 0.32f else 0.48f)).coerceIn(minFontSizeSp, maxFontSizeSp)
    val initialSp = minOf(maxFontSizeSp, estByWidth, estByHeight).coerceIn(minFontSizeSp, maxFontSizeSp)

    var fittedSp by remember(text, availableWidthDp.roundToInt(), availableHeightDp.roundToInt(), maxFontSizeSp) {
        mutableFloatStateOf(initialSp)
    }

    Text(
        text = text,
        color = color,
        fontSize = fittedSp.sp,
        lineHeight = (fittedSp * 1.12f).sp,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        textAlign = textAlign,
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = TextOverflow.Clip,
        onTextLayout = { result ->
            if ((result.didOverflowWidth || result.didOverflowHeight) && fittedSp > minFontSizeSp) {
                fittedSp = (fittedSp * 0.88f).coerceAtLeast(minFontSizeSp)
            }
        },
        modifier = modifier
    )
}

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
    isPreviewFullScreen: Boolean = false,
    onTogglePreviewFullScreen: () -> Unit = {},
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
    val latestScreen1Components by rememberUpdatedState(screen1Components)
    val latestScreen2Components by rememberUpdatedState(screen2Components)
    val latestOnMoveComponent by rememberUpdatedState(onMoveComponent)
    val latestOnResizeComponent by rememberUpdatedState(onResizeComponent)

    val isLuaScriptMode = project.isLuaScriptProject()

    Row(modifier = modifier.fillMaxSize()) {
        // LEFT SIDE WIDGET PALETTE (Hidden when Preview is in Full Screen mode so Preview fills the screen)
        if (!isPreviewFullScreen) {
            LeftSideWidgetPalette(
                activePreviewScreen = activePreviewScreen,
                isLuaScriptMode = isLuaScriptMode,
                isAutoFixSize = project.autoFixSize,
                onSelectPaletteEntry = onAddPaletteEntry,
                onToggleAutoFixSize = onToggleAutoFixSize
            )
        }

        if (isLuaScriptMode) {
            LuaChoiceMenuPreviewCanvas(
                project = project,
                components = screen2Components,
                selectedComponentId = selectedComponentId,
                isLivePreviewMode = isLivePreviewMode,
                isPreviewFullScreen = isPreviewFullScreen,
                onTogglePreviewFullScreen = onTogglePreviewFullScreen,
                onSelectComponent = onSelectComponent,
                onOpenEditFloatingPanel = onOpenEditFloatingPanel,
                onTriggerComponent = { comp ->
                    onTriggerComponentLive(comp, null)
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        } else if (activePreviewScreen == 1) {
            // PREVIEW 1: MAIN SCREEN EDITOR (Start/Stop, TextView, Link Open, ImageView + Fix Dhanche + Corner Resize)
            InteractiveMainScreen1Canvas(
                project = project,
                components = screen1Components,
                selectedComponentId = selectedComponentId,
                isPreviewFullScreen = isPreviewFullScreen,
                onTogglePreviewFullScreen = onTogglePreviewFullScreen,
                onSelectComponent = onSelectComponent,
                onMoveComponent = { id, newX, newY ->
                    latestScreen1Components.find { it.id == id }?.let { comp ->
                        latestOnMoveComponent(comp, newX, newY)
                    }
                },
                onResizeComponent = { id, newW, newH ->
                    latestScreen1Components.find { it.id == id }?.let { comp ->
                        latestOnResizeComponent(comp, newW, newH)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        } else {
            // PREVIEW 2: FLOATING WINDOW EDITOR
            InteractiveOverlayCanvas(
                project = project,
                components = screen2Components,
                selectedComponentId = selectedComponentId,
                isPreviewFullScreen = isPreviewFullScreen,
                onTogglePreviewFullScreen = onTogglePreviewFullScreen,
                onSelectComponent = onSelectComponent,
                onMoveComponent = { id, newX, newY ->
                    latestScreen2Components.find { it.id == id }?.let { comp ->
                        latestOnMoveComponent(comp, newX, newY)
                    }
                },
                onResizeComponent = { id, newW, newH ->
                    latestScreen2Components.find { it.id == id }?.let { comp ->
                        latestOnResizeComponent(comp, newW, newH)
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
    isLuaScriptMode: Boolean = false,
    isAutoFixSize: Boolean,
    onSelectPaletteEntry: (SketchwarePaletteEntry) -> Unit,
    onToggleAutoFixSize: () -> Unit
) {
    val luaChoiceItems = remember {
        listOf(
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("🟢  START", ComponentWidgetType.BUTTON, 196, 42),
                icon = Icons.Default.PlayArrow,
                iconTint = Color(0xFF10B981),
                tagSlug = "lua_start"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("🔴  STOP", ComponentWidgetType.TOGGLE, 196, 42),
                icon = Icons.Default.Stop,
                iconTint = Color(0xFFEF4444),
                tagSlug = "lua_stop"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("📝  TEXT VIEW", ComponentWidgetType.TEXT, 196, 36),
                icon = Icons.Default.TextFields,
                iconTint = Color(0xFFF472B6),
                tagSlug = "lua_text"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("🔗  LINK OPEN", ComponentWidgetType.LINK, 196, 42),
                icon = Icons.Default.Link,
                iconTint = Color(0xFF38BDF8),
                tagSlug = "lua_link"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("🖼️  IMAGE VIEW", ComponentWidgetType.IMAGE, 196, 42),
                icon = Icons.Default.Image,
                iconTint = Color(0xFFF59E0B),
                tagSlug = "lua_image"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("🎚️  SLIDER", ComponentWidgetType.SLIDER, 196, 52),
                icon = Icons.Default.Tune,
                iconTint = Color(0xFFA78BFA),
                tagSlug = "lua_slider"
            ),
            LeftPaletteItemSpec(
                entry = SketchwarePaletteEntry("✏️  INPUT", ComponentWidgetType.INPUT, 196, 46),
                icon = Icons.Default.Edit,
                iconTint = Color(0xFFFBBF24),
                tagSlug = "lua_input"
            )
        )
    }

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
                entry = SketchwarePaletteEntry("Start / Stop", ComponentWidgetType.S1_START, 210, 46, "#10B981", "#FFFFFF"),
                icon = Icons.Default.PlayArrow,
                iconTint = Color(0xFF10B981),
                tagSlug = "s1_start"
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

    val activeItems = when {
        isLuaScriptMode -> luaChoiceItems
        activePreviewScreen == 1 -> screen1WidgetItems
        else -> screen2WidgetItems
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (isLuaScriptMode) {
                    PaletteCategoryHeader("gg.choice Items", Color(0xFF10B981))
                } else if (activePreviewScreen == 1) {
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

            if (isLuaScriptMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF090D18))
                        .border(BorderStroke(0.5.dp, Color(0xFF1E293B)))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "gg.choice Menu\n+ Sx UI Button",
                        color = Color(0xFF10B981),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        lineHeight = 12.sp
                    )
                }
            } else if (activePreviewScreen == 2) {
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
    isPreviewFullScreen: Boolean = false,
    onTogglePreviewFullScreen: () -> Unit = {},
    onSelectComponent: (Long?) -> Unit,
    onMoveComponent: (Long, Int, Int) -> Unit,
    onResizeComponent: (Long, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val currentOnSelectComponent by rememberUpdatedState(onSelectComponent)
    val currentOnMoveComponent by rememberUpdatedState(onMoveComponent)
    val currentOnResizeComponent by rememberUpdatedState(onResizeComponent)

    var focusedCanvasWidgetId by remember { mutableStateOf<Long?>(selectedComponentId) }
    val activeHighlightId = selectedComponentId ?: focusedCanvasWidgetId
    var stableCanvasWidthDp by remember { mutableFloatStateOf(290f) }
    var stableCanvasHeightDp by remember { mutableFloatStateOf(460f) }
    var isPreviewStarted by remember { mutableStateOf(false) }

    val displayComponents = remember(components) {
        val hasStart = components.any { it.type.equals("S1_START", ignoreCase = true) }
        if (hasStart) {
            components.filterNot { it.type.equals("S1_STOP", ignoreCase = true) }
        } else {
            components
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF060A14))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        focusedCanvasWidgetId = null
                        currentOnSelectComponent(null)
                    }
                )
            }
            .padding(if (isPreviewFullScreen) 0.dp else 6.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer Phone Device Frame — Full-Screen Preview with Dynamic Layout (Dhancha)
        Card(
            shape = RoundedCornerShape(if (isPreviewFullScreen) 0.dp else 26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF090F20)),
            border = if (isPreviewFullScreen) null else BorderStroke(2.dp, Color(0xFF1E325C)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isPreviewFullScreen) 0.dp else 2.dp, vertical = if (isPreviewFullScreen) 0.dp else 2.dp)
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

                        // Icon-only Full Screen Toggle Button inside Preview Screen
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = if (isPreviewFullScreen) Color(0xFF4F46E5) else Color(0xFF15203B),
                            border = BorderStroke(
                                1.dp,
                                if (isPreviewFullScreen) Color(0xFF818CF8) else Color(0xFF38BDF8).copy(alpha = 0.7f)
                            ),
                            modifier = Modifier
                                .size(26.dp)
                                .clickable { onTogglePreviewFullScreen() }
                                .testTag("preview_fullscreen_toggle_button")
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = if (isPreviewFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = if (isPreviewFullScreen) "Exit Full Screen Preview" else "Full Screen Preview",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
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
                    if (maxWidth.value > 150f) {
                        stableCanvasWidthDp = maxWidth.value
                    }
                    if (maxHeight.value > 250f) {
                        stableCanvasHeightDp = maxHeight.value
                    }
                    val canvasWidthDp = maxWidth.value.coerceAtLeast(stableCanvasWidthDp).coerceAtLeast(220f)
                    val canvasHeightDp = maxHeight.value.coerceAtLeast(stableCanvasHeightDp).coerceAtLeast(300f)

                    // Full-screen Dynamic Grid + Outer Dhancha Frame + Dashed Alignment/Snap Guide Lines
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        if (w <= 8f || h <= 8f) return@Canvas
                        val safeW = w.coerceAtLeast(1f)
                        val safeH = h.coerceAtLeast(1f)
                        val gridStepPx = 22.dp.toPx().coerceAtLeast(8f)

                        // Subtle full-screen blueprint grid lines
                        var gx = gridStepPx
                        while (gx < safeW) {
                            drawLine(
                                color = Color(0xFF38BDF8).copy(alpha = 0.07f),
                                start = Offset(gx, 0f),
                                end = Offset(gx, safeH),
                                strokeWidth = 1f
                            )
                            gx += gridStepPx
                        }
                        var gy = gridStepPx
                        while (gy < safeH) {
                            drawLine(
                                color = Color(0xFF38BDF8).copy(alpha = 0.07f),
                                start = Offset(0f, gy),
                                end = Offset(safeW, gy),
                                strokeWidth = 1f
                            )
                            gy += gridStepPx
                        }

                        // Dashed Center Vertical & Horizontal Responsive Alignment Guides
                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                        val centerX = safeW / 2f
                        val centerY = safeH / 2f

                        drawLine(
                            color = Color(0xFF60A5FA).copy(alpha = 0.42f),
                            start = Offset(centerX, 0f),
                            end = Offset(centerX, safeH),
                            strokeWidth = 1.4.dp.toPx(),
                            pathEffect = dashEffect
                        )
                        drawLine(
                            color = Color(0xFF60A5FA).copy(alpha = 0.25f),
                            start = Offset(0f, centerY),
                            end = Offset(safeW, centerY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashEffect
                        )

                        // Dynamic Widget Snap Guide Lines for each placed widget
                        components.forEach { item ->
                            val itemTopPx = item.posYDp.dp.toPx().coerceIn(0f, safeH)
                            val itemBottomPx = (item.posYDp + item.heightDp).dp.toPx().coerceIn(0f, safeH)
                            val itemLeftPx = item.posXDp.dp.toPx().coerceIn(0f, safeW)
                            val itemRightPx = (item.posXDp + item.widthDp).dp.toPx().coerceIn(0f, safeW)
                            val isItemSelected = item.id == activeHighlightId
                            val guideAlpha = if (isItemSelected) 0.55f else 0.22f
                            val guideColor = if (isItemSelected) Color(0xFF38BDF8) else Color(0xFF6366F1)

                            // Horizontal top/bottom alignment guides across the full screen
                            drawLine(
                                color = guideColor.copy(alpha = guideAlpha),
                                start = Offset(0f, itemTopPx),
                                end = Offset(safeW, itemTopPx),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = dashEffect
                            )
                            drawLine(
                                color = guideColor.copy(alpha = guideAlpha),
                                start = Offset(0f, itemBottomPx),
                                end = Offset(safeW, itemBottomPx),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = dashEffect
                            )

                            // Vertical left/right alignment guides when selected
                            if (isItemSelected) {
                                drawLine(
                                    color = Color(0xFF38BDF8).copy(alpha = 0.45f),
                                    start = Offset(itemLeftPx, 0f),
                                    end = Offset(itemLeftPx, safeH),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = dashEffect
                                )
                                drawLine(
                                    color = Color(0xFF38BDF8).copy(alpha = 0.45f),
                                    start = Offset(itemRightPx, 0f),
                                    end = Offset(itemRightPx, safeH),
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
                        val safeMaxX = (safeW - handleSize).coerceAtLeast(0f)
                        val safeMaxY = (safeH - handleSize).coerceAtLeast(0f)
                        val frameAnchorPoints = listOf(
                            Offset(0f, 0f),
                            Offset(safeW / 2f, 0f),
                            Offset(safeW, 0f),
                            Offset(0f, safeH / 2f),
                            Offset(safeW, safeH / 2f),
                            Offset(0f, safeH),
                            Offset(safeW / 2f, safeH),
                            Offset(safeW, safeH)
                        )
                        frameAnchorPoints.forEach { pt ->
                            drawRect(
                                color = Color(0xFF818CF8),
                                topLeft = Offset(
                                    (pt.x - halfH).coerceIn(0f, safeMaxX),
                                    (pt.y - halfH).coerceIn(0f, safeMaxY)
                                ),
                                size = androidx.compose.ui.geometry.Size(handleSize, handleSize)
                            )
                        }
                    }

                    // Empty state hint if no widgets placed yet (clean, no fixed boxes)
                    if (displayComponents.isEmpty()) {
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
                                        text = "Tap Start / Stop, Text View, Link Open, or Image View on the left to place on the screen.",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Render Placed Widgets on the Full-Screen Dynamic Dhancha
                    displayComponents.forEach { comp ->
                        key(comp.id) {
                        val isSelected = comp.id == activeHighlightId

                        var isGestureActive by remember(comp.id) { mutableStateOf(false) }
                        var activeDragMode by remember(comp.id) { mutableStateOf("MOVE") }

                        var liveWidthDp by remember(comp.id) {
                            mutableFloatStateOf(comp.widthDp.toFloat().coerceAtLeast(48f))
                        }
                        var liveHeightDp by remember(comp.id) {
                            mutableFloatStateOf(comp.heightDp.toFloat().coerceAtLeast(28f))
                        }
                        LaunchedEffect(comp.widthDp, comp.heightDp) {
                            if (!isGestureActive) {
                                liveWidthDp = comp.widthDp.toFloat().coerceAtLeast(48f)
                                liveHeightDp = comp.heightDp.toFloat().coerceAtLeast(28f)
                            }
                        }
                        val currentLiveWidthDp by rememberUpdatedState(liveWidthDp)
                        val currentLiveHeightDp by rememberUpdatedState(liveHeightDp)

                        var offsetX by remember(comp.id) {
                            mutableFloatStateOf(with(density) { comp.posXDp.dp.toPx() })
                        }
                        var offsetY by remember(comp.id) {
                            mutableFloatStateOf(with(density) { comp.posYDp.dp.toPx() })
                        }
                        LaunchedEffect(comp.posXDp, comp.posYDp) {
                            if (!isGestureActive) {
                                offsetX = with(density) { comp.posXDp.dp.toPx() }
                                offsetY = with(density) { comp.posYDp.dp.toPx() }
                            }
                        }
                        var rawDragX by remember(comp.id) {
                            mutableFloatStateOf(with(density) { comp.posXDp.dp.toPx() })
                        }
                        var rawDragY by remember(comp.id) {
                            mutableFloatStateOf(with(density) { comp.posYDp.dp.toPx() })
                        }
                        var rawResizeW by remember(comp.id) {
                            mutableFloatStateOf(liveWidthDp)
                        }
                        var rawResizeH by remember(comp.id) {
                            mutableFloatStateOf(liveHeightDp)
                        }

                        val typeUpper = comp.type.trim().uppercase()
                        val isStart = typeUpper == "S1_START"
                        val isStop = typeUpper == "S1_STOP"
                        val isStartOrStopToggle = isStart || isStop
                        val isText = typeUpper == "S1_TEXT"
                        val isLink = typeUpper == "S1_LINK"
                        val isImage = typeUpper == "S1_IMAGE"
                        val isTextOrLink = isText || isLink

                        // Accent border color per widget type matching the screenshot
                        val accentBorderColor = when {
                            isStartOrStopToggle -> if (isPreviewStarted) Color(0xFFEF4444) else Color(0xFF10B981)
                            isText -> Color(0xFF818CF8)
                            isLink -> Color(0xFF38BDF8)
                            isImage -> Color(0xFF38BDF8)
                            else -> Color(0xFF38BDF8)
                        }

                        // Background color: S1_TEXT and S1_LINK are always transparent for Screen 1
                        val widgetBgColor = when {
                            isTextOrLink -> Color(0xFF1E293B).copy(alpha = 0.32f)
                            isImage -> Color(0xFF0F172A).copy(alpha = 0.45f)
                            isStartOrStopToggle && isPreviewStarted -> Color(0xFFEF4444).copy(alpha = 0.28f)
                            else -> {
                                val parsed = parseHexColorSafe(comp.bgColorHex, Color(0xFF10B981))
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
                                .pointerInput(comp.id) {
                                    detectTapGestures(
                                        onTap = {
                                            // Tap focuses widget on canvas without opening bottom options;
                                            // for Start/Stop button, tapping toggles between Start and Stop preview state!
                                            focusedCanvasWidgetId = comp.id
                                            if (isStartOrStopToggle) {
                                                isPreviewStarted = !isPreviewStarted
                                            }
                                        },
                                        onLongPress = { pressOffset ->
                                            val wPx = with(density) { currentLiveWidthDp.dp.toPx() }
                                            val hPx = with(density) { currentLiveHeightDp.dp.toPx() }
                                            val cornerZonePx = with(density) { 26.dp.toPx() }
                                            val isCornerTouch = pressOffset.x >= (wPx - cornerZonePx) && pressOffset.y >= (hPx - cornerZonePx)
                                            focusedCanvasWidgetId = comp.id
                                            if (!isCornerTouch) {
                                                // Hold (long-press) on widget body opens the bottom options sheet
                                                currentOnSelectComponent(comp.id)
                                            }
                                        }
                                    )
                                }
                                .pointerInput(comp.id) {
                                    detectDragGestures(
                                        onDragStart = { startOffset ->
                                            isGestureActive = true
                                            focusedCanvasWidgetId = comp.id
                                            if (selectedComponentId != null) {
                                                currentOnSelectComponent(null)
                                            }
                                            val wPx = with(density) { currentLiveWidthDp.dp.toPx() }
                                            val hPx = with(density) { currentLiveHeightDp.dp.toPx() }
                                            val cornerZonePx = with(density) { 28.dp.toPx() }
                                            val edgeZonePx = with(density) { 16.dp.toPx() }

                                            activeDragMode = when {
                                                startOffset.x >= (wPx - cornerZonePx) && startOffset.y >= (hPx - cornerZonePx) -> "RESIZE_CORNER"
                                                startOffset.x >= (wPx - edgeZonePx) -> "RESIZE_RIGHT"
                                                startOffset.y >= (hPx - edgeZonePx) -> "RESIZE_BOTTOM"
                                                else -> "MOVE"
                                            }
                                            rawDragX = offsetX
                                            rawDragY = offsetY
                                            rawResizeW = currentLiveWidthDp
                                            rawResizeH = currentLiveHeightDp
                                        },
                                        onDragCancel = {
                                            isGestureActive = false
                                        },
                                        onDragEnd = {
                                            val maxW = (canvasWidthDp - 8f).coerceAtLeast(100f)
                                            val maxH = (canvasHeightDp - 8f).coerceAtLeast(100f)
                                            if (activeDragMode == "MOVE") {
                                                val wDp = currentLiveWidthDp
                                                val hDp = currentLiveHeightDp
                                                val maxXDp = (canvasWidthDp - wDp).roundToInt().coerceAtLeast(0)
                                                val maxYDp = (canvasHeightDp - hDp).roundToInt().coerceAtLeast(0)
                                                val finalXDp = with(density) { offsetX.toDp().value.roundToInt() }.coerceIn(0, maxXDp)
                                                val finalYDp = with(density) { offsetY.toDp().value.roundToInt() }.coerceIn(0, maxYDp)

                                                val finalXPx = with(density) { finalXDp.dp.toPx() }
                                                val finalYPx = with(density) { finalYDp.dp.toPx() }
                                                offsetX = finalXPx
                                                offsetY = finalYPx
                                                rawDragX = finalXPx
                                                rawDragY = finalYPx
                                                currentOnMoveComponent(comp.id, finalXDp, finalYDp)
                                            } else {
                                                val finalW = currentLiveWidthDp.roundToInt().coerceIn(48, maxW.roundToInt().coerceAtLeast(48))
                                                val finalH = currentLiveHeightDp.roundToInt().coerceIn(28, maxH.coerceAtLeast(28f).roundToInt())
                                                liveWidthDp = finalW.toFloat()
                                                liveHeightDp = finalH.toFloat()
                                                rawResizeW = finalW.toFloat()
                                                rawResizeH = finalH.toFloat()
                                                currentOnResizeComponent(comp.id, finalW, finalH)
                                            }
                                            isGestureActive = false
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val maxW = (canvasWidthDp - 8f).coerceAtLeast(100f)
                                            val maxH = (canvasHeightDp - 8f).coerceAtLeast(100f)
                                            when (activeDragMode) {
                                                "RESIZE_CORNER" -> {
                                                    val dxDp = with(density) { dragAmount.x.toDp().value }
                                                    val dyDp = with(density) { dragAmount.y.toDp().value }
                                                    rawResizeW = (rawResizeW + dxDp).coerceIn(48f, maxW)
                                                    rawResizeH = (rawResizeH + dyDp).coerceIn(28f, maxH)
                                                    liveWidthDp = rawResizeW
                                                    liveHeightDp = rawResizeH
                                                }
                                                "RESIZE_RIGHT" -> {
                                                    val dxDp = with(density) { dragAmount.x.toDp().value }
                                                    rawResizeW = (rawResizeW + dxDp).coerceIn(48f, maxW)
                                                    liveWidthDp = rawResizeW
                                                }
                                                "RESIZE_BOTTOM" -> {
                                                    val dyDp = with(density) { dragAmount.y.toDp().value }
                                                    rawResizeH = (rawResizeH + dyDp).coerceIn(28f, maxH)
                                                    liveHeightDp = rawResizeH
                                                }
                                                else -> {
                                                    // 100% free smooth movement — never sticks or snaps on the dhancha!
                                                    val wDp = currentLiveWidthDp
                                                    val hDp = currentLiveHeightDp
                                                    val maxXPx = with(density) { (canvasWidthDp - wDp).coerceAtLeast(0f).dp.toPx() }
                                                    val maxYPx = with(density) { (canvasHeightDp - hDp).coerceAtLeast(0f).dp.toPx() }
                                                    rawDragX = (rawDragX + dragAmount.x).coerceIn(0f, maxXPx)
                                                    rawDragY = (rawDragY + dragAmount.y).coerceIn(0f, maxYPx)
                                                    offsetX = rawDragX
                                                    offsetY = rawDragY
                                                }
                                            }
                                        }
                                    )
                                }
                                .testTag("screen1_canvas_widget_${comp.id}")
                        ) {
                            when (typeUpper) {
                                "S1_START", "S1_STOP" -> {
                                    val solidTint = if (isPreviewStarted) {
                                        Color(0xFFEF4444)
                                    } else {
                                        parseHexColorSafe(comp.bgColorHex, Color(0xFF10B981))
                                    }
                                    val activeToggleText = if (isPreviewStarted) {
                                        comp.offPayloadHex.ifBlank { "STOP SERVICE" }
                                    } else {
                                        comp.label.ifBlank { "START SERVICE" }
                                    }
                                    if (bgBitmap != null) {
                                        Image(
                                            bitmap = bgBitmap,
                                            contentDescription = "$activeToggleText Background",
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
                                                            solidTint.copy(alpha = 0.42f),
                                                            solidTint.copy(alpha = 0.22f)
                                                        )
                                                    )
                                                )
                                        )
                                    }
                                    val compactPad = (liveWidthDp * 0.05f).coerceIn(4f, 12f).dp
                                    val iconBoxDp = (minOf(liveHeightDp * 0.52f, liveWidthDp * 0.22f)).coerceIn(14f, 26f)
                                    val textAvailW = (liveWidthDp - iconBoxDp - 18f).coerceAtLeast(24f)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = compactPad),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(iconBoxDp.dp)
                                                .clip(CircleShape)
                                                .background(if (isPreviewStarted) Color(0xFFEF4444) else Color(0xFF10B981)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isPreviewStarted) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size((iconBoxDp * 0.58f).dp)
                                            )
                                        }
                                        Spacer(Modifier.width((liveWidthDp * 0.04f).coerceIn(3f, 10f).dp))
                                        AutoFitText(
                                            text = activeToggleText,
                                            color = widgetTextColor,
                                            maxFontSizeSp = 12.5f,
                                            minFontSizeSp = 4.5f,
                                            availableWidthDp = textAvailW,
                                            availableHeightDp = liveHeightDp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }

                                "S1_TEXT" -> {
                                    val compactPad = (liveWidthDp * 0.05f).coerceIn(4f, 12f).dp
                                    val showBadge = liveWidthDp >= 78f && liveHeightDp >= 30f
                                    val badgeDp = (minOf(liveHeightDp * 0.5f, liveWidthDp * 0.22f)).coerceIn(14f, 26f)
                                    val showSubLabel = liveHeightDp >= 40f && liveWidthDp >= 90f
                                    val textAvailW = (liveWidthDp - (if (showBadge) badgeDp + 16f else 8f)).coerceAtLeast(24f)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = compactPad),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy((liveWidthDp * 0.04f).coerceIn(4f, 10f).dp)
                                    ) {
                                        if (showBadge) {
                                            Box(
                                                modifier = Modifier
                                                    .size(badgeDp.dp)
                                                    .clip(RoundedCornerShape(7.dp))
                                                    .background(Color(0xFF6366F1)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                AutoFitText(
                                                    text = "T",
                                                    color = Color.White,
                                                    maxFontSizeSp = 13f,
                                                    minFontSizeSp = 6f,
                                                    availableWidthDp = badgeDp,
                                                    availableHeightDp = badgeDp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                        }
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            if (showSubLabel) {
                                                AutoFitText(
                                                    text = "Text View",
                                                    color = Color(0xFFCBD5E1),
                                                    maxFontSizeSp = 9.5f,
                                                    minFontSizeSp = 4.5f,
                                                    availableWidthDp = textAvailW,
                                                    availableHeightDp = liveHeightDp * 0.4f,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            AutoFitText(
                                                text = comp.label,
                                                color = widgetTextColor,
                                                maxFontSizeSp = 12f,
                                                minFontSizeSp = 4.5f,
                                                availableWidthDp = textAvailW,
                                                availableHeightDp = if (showSubLabel) liveHeightDp * 0.58f else liveHeightDp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                "S1_LINK" -> {
                                    // Transparent background; shows custom side logo if added, or clean text if not added
                                    val compactPad = (liveWidthDp * 0.05f).coerceIn(4f, 12f).dp
                                    val logoDp = (minOf(liveHeightDp * 0.52f, liveWidthDp * 0.22f)).coerceIn(14f, 26f)
                                    val showSubUrl = comp.linkUrl.isNotBlank() && liveHeightDp >= 38f
                                    val textAvailW = (liveWidthDp - (if (customBitmap != null) logoDp + 22f else 18f)).coerceAtLeast(24f)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = compactPad),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy((liveWidthDp * 0.04f).coerceIn(4f, 10f).dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (customBitmap != null) {
                                                Image(
                                                    bitmap = customBitmap,
                                                    contentDescription = "Link Side Logo",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(logoDp.dp)
                                                        .clip(RoundedCornerShape(7.dp))
                                                )
                                            }
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                AutoFitText(
                                                    text = comp.label,
                                                    color = widgetTextColor,
                                                    maxFontSizeSp = 12f,
                                                    minFontSizeSp = 4.5f,
                                                    availableWidthDp = textAvailW,
                                                    availableHeightDp = if (showSubUrl) liveHeightDp * 0.56f else liveHeightDp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (showSubUrl) {
                                                    AutoFitText(
                                                        text = comp.linkUrl,
                                                        color = Color(0xFF94A3B8),
                                                        maxFontSizeSp = 9.5f,
                                                        minFontSizeSp = 4.5f,
                                                        availableWidthDp = textAvailW,
                                                        availableHeightDp = liveHeightDp * 0.42f,
                                                        fontWeight = FontWeight.Normal
                                                    )
                                                }
                                            }
                                        }
                                        if (liveWidthDp >= 75f) {
                                            Text(
                                                text = "›",
                                                color = Color(0xFF94A3B8),
                                                fontSize = (liveHeightDp * 0.34f).coerceIn(10f, 16f).sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
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

                            // Live dimensions pill when focused or resizing
                            if (isSelected || isGestureActive) {
                                Surface(
                                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                                    color = Color(0xFF0F172A).copy(alpha = 0.88f),
                                    modifier = Modifier.align(Alignment.TopStart)
                                ) {
                                    Text(
                                        text = "${liveWidthDp.roundToInt()}×${liveHeightDp.roundToInt()} dp",
                                        color = accentBorderColor,
                                        fontSize = 7.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                    )
                                }
                            }

                            // Always-Active Corner Resize Handle (Bottom-Right ↘ — works even when bottom options are not open!)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(topStart = 8.dp, bottomEnd = 12.dp))
                                    .background(accentBorderColor.copy(alpha = 0.92f))
                                    .pointerInput(comp.id) {
                                        detectDragGestures(
                                            onDragStart = {
                                                isGestureActive = true
                                                focusedCanvasWidgetId = comp.id
                                                rawResizeW = currentLiveWidthDp
                                                rawResizeH = currentLiveHeightDp
                                                if (selectedComponentId != null) {
                                                    currentOnSelectComponent(null)
                                                }
                                            },
                                            onDragCancel = {
                                                isGestureActive = false
                                            },
                                            onDragEnd = {
                                                val maxW = (canvasWidthDp - 8f).roundToInt().coerceAtLeast(100)
                                                val maxH = (canvasHeightDp - 8f).roundToInt().coerceAtLeast(100)
                                                val finalW = currentLiveWidthDp.roundToInt().coerceIn(48, maxW.coerceAtLeast(48))
                                                val finalH = currentLiveHeightDp.roundToInt().coerceIn(28, maxH.coerceAtLeast(28))

                                                liveWidthDp = finalW.toFloat()
                                                liveHeightDp = finalH.toFloat()
                                                rawResizeW = finalW.toFloat()
                                                rawResizeH = finalH.toFloat()
                                                currentOnResizeComponent(comp.id, finalW, finalH)
                                                isGestureActive = false
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val dxDp = with(density) { dragAmount.x.toDp().value }
                                                val dyDp = with(density) { dragAmount.y.toDp().value }
                                                val maxW = (canvasWidthDp - 8f).coerceAtLeast(100f)
                                                val maxH = (canvasHeightDp - 8f).coerceAtLeast(100f)
                                                rawResizeW = (rawResizeW + dxDp).coerceIn(48f, maxW)
                                                rawResizeH = (rawResizeH + dyDp).coerceIn(28f, maxH)
                                                liveWidthDp = rawResizeW
                                                liveHeightDp = rawResizeH
                                            }
                                        )
                                    }
                                    .testTag("widget_resize_handle_${comp.id}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "↘",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
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
fun LuaChoiceMenuPreviewCanvas(
    project: StudioProjectEntity,
    components: List<CanvasComponentEntity>,
    selectedComponentId: Long?,
    isLivePreviewMode: Boolean = false,
    isPreviewFullScreen: Boolean = false,
    onTogglePreviewFullScreen: () -> Unit = {},
    onSelectComponent: (Long?) -> Unit,
    onOpenEditFloatingPanel: () -> Unit = {},
    onTriggerComponent: (CanvasComponentEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedTitle = remember(project.overlayTitle, project.name) {
        val raw = project.overlayTitle.trim().ifEmpty { project.name.trim().ifEmpty { "PC PANEL" } }
        LuaScriptEngine.formatLuaPanelHeaderTitle(raw)
    }

    var isLuaHidden by remember(project.id) { mutableStateOf(false) }
    var isLuaRunning by remember(project.id) { mutableStateOf(true) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
            .clickable { onSelectComponent(null) }
            .padding(if (isPreviewFullScreen) 0.dp else 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(if (isPreviewFullScreen) 0.dp else 26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0E14)),
            border = if (isPreviewFullScreen) null else BorderStroke(2.dp, Color(0xFF233152)),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isPreviewFullScreen) 0.dp else 2.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Status Bar (No BG color chip or custom dp size in Lua mode)
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
                            text = "gg.choice()",
                            color = Color(0xFF34D399),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = if (isLivePreviewMode) "RUN MODE (Tap item to run)" else "EDIT MODE (Tap item to edit)",
                        color = if (isLivePreviewMode) Color(0xFF34D399) else Color(0xFF94A3B8),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = if (isPreviewFullScreen) Color(0xFF4F46E5) else Color(0xFF15203B),
                        border = BorderStroke(
                            1.dp,
                            if (isPreviewFullScreen) Color(0xFF818CF8) else Color(0xFF38BDF8).copy(alpha = 0.7f)
                        ),
                        modifier = Modifier
                            .size(26.dp)
                            .clickable { onTogglePreviewFullScreen() }
                            .testTag("preview_fullscreen_toggle_button")
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = if (isPreviewFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (isPreviewFullScreen) "Exit Full Screen Preview" else "Full Screen Preview",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Simulated GameGuardian Screen Area
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0B0E14))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isLuaRunning) {
                        // GameGuardian "Script ended:" dialog when ❌ KILL is clicked
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF3E4147),
                            border = BorderStroke(1.dp, Color(0xFF555960)),
                            modifier = Modifier.fillMaxWidth(0.92f)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Script ended:\nos.exit() called from ❌  KILL",
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            isLuaRunning = true
                                            isLuaHidden = false
                                        }
                                    ) {
                                        Text("RESTART", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    TextButton(
                                        onClick = {
                                            isLuaRunning = true
                                            isLuaHidden = false
                                        }
                                    ) {
                                        Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else if (isLuaHidden) {
                        // GameGuardian gg.showUiButton() floating "Sx" button when ➖ MINIMIZE or 🙈 HIDE is clicked
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xCC26292E),
                                border = BorderStroke(1.5.dp, Color(0xFF8B5CF6)),
                                modifier = Modifier
                                    .size(52.dp)
                                    .clickable { isLuaHidden = false }
                                    .testTag("lua_sx_ui_button")
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = "Sx",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                            Text(
                                text = "gg.showUiButton() active — Tap 'Sx' to open panel()",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp
                            )
                        }
                    } else {
                        // Authentic GameGuardian gg.choice({...}, nil, "◈  PC PANEL  ◈") Dialog
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF303338),
                            border = BorderStroke(1.dp, Color(0xFF4B5563)),
                            tonalElevation = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth(0.95f)
                                .testTag("lua_gg_choice_dialog")
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // gg.choice Header Bar: "◈  PC PANEL  ◈"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF24272B))
                                        .clickable { onOpenEditFloatingPanel() }
                                        .padding(horizontal = 14.dp, vertical = 11.dp)
                                        .testTag("floating_panel_header_bar"),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = formattedTitle,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Panel Title",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                HorizontalDivider(color = Color(0xFF4B5563), thickness = 1.dp)

                                // Scrollable gg.choice Items List
                                Column(
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    if (components.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 18.dp, horizontal = 14.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Tap any item on the left palette (🟢 START, 🔴 STOP, etc.) to add to gg.choice()",
                                                color = Color(0xFF9CA3AF),
                                                fontSize = 11.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }

                                    components.forEachIndexed { index, comp ->
                                        val isSelected = comp.id == selectedComponentId
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    if (isSelected) Color(0xFF1E3A8A).copy(alpha = 0.55f)
                                                    else Color.Transparent
                                                )
                                                .clickable {
                                                    if (isLivePreviewMode) {
                                                        onTriggerComponent(comp)
                                                    } else {
                                                        onSelectComponent(comp.id)
                                                    }
                                                }
                                                .padding(horizontal = 14.dp, vertical = 11.dp)
                                                .testTag("lua_choice_item_${comp.id}"),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            // Radio circle like gg.choice
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .border(
                                                        BorderStroke(
                                                            2.dp,
                                                            if (isSelected) Color(0xFF38BDF8) else Color(0xFF9CA3AF)
                                                        ),
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF38BDF8))
                                                    )
                                                }
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = comp.label,
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "c == ${index + 1}  •  ${LuaScriptEngine.resolveWidgetButtonLogic(comp).lines().firstOrNull().orEmpty()}",
                                                    color = Color(0xFF9CA3AF),
                                                    fontSize = 9.5.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            // Quick test button on the right
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF065F46),
                                                modifier = Modifier.clickable { onTriggerComponent(comp) }
                                            ) {
                                                Text(
                                                    text = "▶ Test",
                                                    color = Color(0xFF6EE7B7),
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        HorizontalDivider(color = Color(0xFF3F434A), thickness = 0.5.dp)
                                    }

                                    // Built-in 3 bottom items from the user's gg.choice script:
                                    // "➖  MINIMIZE", "🙈  HIDE", "❌  KILL"
                                    val builtInControls = listOf(
                                        Triple("➖  MINIMIZE", "hidden = true; gg.setVisible(false)", components.size + 1),
                                        Triple("🙈  HIDE", "hidden = true; gg.setVisible(false)", components.size + 2),
                                        Triple("❌  KILL", "running = false; os.exit()", components.size + 3)
                                    )
                                    builtInControls.forEach { (ctrlLabel, ctrlDesc, ctrlIdx) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (ctrlLabel.contains("KILL")) {
                                                        isLuaRunning = false
                                                    } else {
                                                        isLuaHidden = true
                                                    }
                                                }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .border(BorderStroke(2.dp, Color(0xFF6B7280)), CircleShape)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = ctrlLabel,
                                                    color = Color(0xFFE5E7EB),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "c == $ctrlIdx  •  $ctrlDesc",
                                                    color = Color(0xFF9CA3AF),
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        HorizontalDivider(color = Color(0xFF3F434A), thickness = 0.5.dp)
                                    }
                                }

                                // GameGuardian Dialog Footer (CANCEL / OK)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF24272B))
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { isLuaHidden = true }) {
                                        Text(
                                            text = "CANCEL",
                                            color = Color(0xFF9CA3AF),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    TextButton(
                                        onClick = {
                                            val targetComp = components.find { it.id == selectedComponentId } ?: components.firstOrNull()
                                            if (targetComp != null) {
                                                onTriggerComponent(targetComp)
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = "OK",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
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

@Composable
fun InteractiveOverlayCanvas(
    project: StudioProjectEntity,
    components: List<CanvasComponentEntity>,
    selectedComponentId: Long?,
    isPreviewFullScreen: Boolean = false,
    onTogglePreviewFullScreen: () -> Unit = {},
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

    val currentOnResizeCanvas by rememberUpdatedState(onResizeCanvas)
    var isPanelResizing by remember(project.id) { mutableStateOf(false) }
    var dragCanvasWidthDp by remember(project.id) {
        mutableFloatStateOf(project.canvasWidthDp.toFloat().coerceIn(140f, 360f))
    }
    var dragCanvasHeightDp by remember(project.id) {
        mutableFloatStateOf(project.canvasHeightDp.toFloat().coerceIn(140f, 520f))
    }
    LaunchedEffect(project.id, project.canvasWidthDp, project.canvasHeightDp) {
        if (!isPanelResizing) {
            dragCanvasWidthDp = project.canvasWidthDp.toFloat().coerceIn(140f, 360f)
            dragCanvasHeightDp = project.canvasHeightDp.toFloat().coerceIn(140f, 520f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
            .clickable { onSelectComponent(null) }
            .padding(if (isPreviewFullScreen) 0.dp else 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer Android Phone Device Frame
        Card(
            shape = RoundedCornerShape(if (isPreviewFullScreen) 0.dp else 26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1528)),
            border = if (isPreviewFullScreen) null else BorderStroke(2.dp, Color(0xFF233152)),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isPreviewFullScreen) 0.dp else 2.dp, vertical = if (isPreviewFullScreen) 0.dp else 2.dp)
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
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

                        // Icon-only Full Screen Toggle Button inside Preview Screen
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = if (isPreviewFullScreen) Color(0xFF4F46E5) else Color(0xFF15203B),
                            border = BorderStroke(
                                1.dp,
                                if (isPreviewFullScreen) Color(0xFF818CF8) else Color(0xFF38BDF8).copy(alpha = 0.7f)
                            ),
                            modifier = Modifier
                                .size(26.dp)
                                .clickable { onTogglePreviewFullScreen() }
                                .testTag("preview_fullscreen_toggle_button")
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = if (isPreviewFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = if (isPreviewFullScreen) "Exit Full Screen Preview" else "Full Screen Preview",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
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
                                    AutoFitText(
                                        text = resolvedPanelTitle,
                                        color = bubbleContentColor,
                                        maxFontSizeSp = 10f,
                                        minFontSizeSp = 4.5f,
                                        availableWidthDp = 58f,
                                        availableHeightDp = 42f,
                                        fontWeight = FontWeight.ExtraBold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
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
                                    width = dragCanvasWidthDp.dp.coerceIn(140.dp, 360.dp),
                                    height = dragCanvasHeightDp.dp.coerceIn(140.dp, 520.dp)
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
                                val panelScaleFactor = (dragCanvasWidthDp / 260f).coerceIn(0.54f, 1.0f)
                                val headerLogoDp = (22f * panelScaleFactor).coerceIn(13f, 22f)
                                val headerBtnFontSp = (8.5f * panelScaleFactor).coerceIn(5.5f, 8.5f)
                                val headerBtnHPad = (5f * panelScaleFactor).coerceIn(2.5f, 5f).dp
                                val headerBtnVPad = (2.5f * panelScaleFactor).coerceIn(1.5f, 2.5f).dp
                                val titleAvailWidthDp = (dragCanvasWidthDp - 115f * panelScaleFactor - headerLogoDp).coerceAtLeast(32f)

                                // Floating Window Header Bar (Transparent so background covers whole floating window)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenEditFloatingPanel() }
                                        .padding(
                                            horizontal = (8f * panelScaleFactor).coerceIn(4f, 8f).dp,
                                            vertical = (7f * panelScaleFactor).coerceIn(4f, 7f).dp
                                        )
                                        .testTag("floating_panel_header_bar"),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy((5f * panelScaleFactor).coerceIn(2.5f, 5f).dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(headerLogoDp.dp)
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
                                                    modifier = Modifier.size((headerLogoDp * 0.5f).dp)
                                                )
                                            }
                                        }

                                        AutoFitText(
                                            text = resolvedPanelTitle,
                                            color = headerTextColor,
                                            maxFontSizeSp = 11f,
                                            minFontSizeSp = 4.5f,
                                            availableWidthDp = titleAvailWidthDp,
                                            availableHeightDp = 24f,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy((3f * panelScaleFactor).coerceIn(1.5f, 3f).dp)
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
                                                fontSize = headerBtnFontSp.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = headerBtnHPad, vertical = headerBtnVPad)
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
                                                fontSize = headerBtnFontSp.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = headerBtnHPad, vertical = headerBtnVPad)
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
                                                fontSize = headerBtnFontSp.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = headerBtnHPad, vertical = headerBtnVPad)
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
                                                    val widgetHPad = (liveWidthDp * 0.05f).coerceIn(3f, 8f).dp
                                                    val switchScale = (minOf(liveWidthDp / 150f, liveHeightDp / 38f)).coerceIn(0.52f, 0.9f)
                                                    val textAvailW = (liveWidthDp - (if (isToggle) 44f * switchScale else 10f)).coerceAtLeast(22f)
                                                    val hasSecondLine = comp.type == "SLIDER" || comp.type == "INPUT" || (comp.type == "LINK" && comp.linkUrl.isNotBlank() && liveHeightDp >= 34f)
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .padding(horizontal = widgetHPad),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.weight(1f),
                                                            verticalArrangement = Arrangement.Center
                                                        ) {
                                                            AutoFitText(
                                                                text = if (comp.type == "LINK") "🔗 ${comp.label}" else comp.label,
                                                                color = widgetText,
                                                                maxFontSizeSp = 11f,
                                                                minFontSizeSp = 4.5f,
                                                                availableWidthDp = textAvailW,
                                                                availableHeightDp = if (hasSecondLine) liveHeightDp * 0.5f else liveHeightDp,
                                                                fontWeight = FontWeight.Bold
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
                                                                    modifier = Modifier.height((liveHeightDp * 0.45f).coerceIn(14f, 22f).dp)
                                                                )
                                                            } else if (comp.type == "INPUT") {
                                                                AutoFitText(
                                                                    text = comp.currentValue.ifBlank { "Enter value..." },
                                                                    color = widgetText.copy(alpha = 0.85f),
                                                                    maxFontSizeSp = 10f,
                                                                    minFontSizeSp = 4.5f,
                                                                    availableWidthDp = textAvailW,
                                                                    availableHeightDp = liveHeightDp * 0.45f,
                                                                    fontWeight = FontWeight.Medium,
                                                                    fontFamily = FontFamily.Monospace
                                                                )
                                                            } else if (comp.type == "LINK" && comp.linkUrl.isNotBlank() && liveHeightDp >= 34f) {
                                                                AutoFitText(
                                                                    text = comp.linkUrl,
                                                                    color = widgetText.copy(alpha = 0.8f),
                                                                    maxFontSizeSp = 9f,
                                                                    minFontSizeSp = 4.5f,
                                                                    availableWidthDp = textAvailW,
                                                                    availableHeightDp = liveHeightDp * 0.42f,
                                                                    fontWeight = FontWeight.Normal
                                                                )
                                                            }
                                                        }
                                                        if (isToggle) {
                                                            Box(
                                                                modifier = Modifier.scale(switchScale),
                                                                contentAlignment = Alignment.CenterEnd
                                                            ) {
                                                                Switch(
                                                                    checked = isChecked,
                                                                    onCheckedChange = {
                                                                        // Preview screen never executes target file changes; only selects widget
                                                                        onSelectComponent(comp.id)
                                                                    }
                                                                )
                                                            }
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

                                // Right Edge Resize Strip for Floating Panel Window
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .width(12.dp)
                                        .fillMaxHeight()
                                        .pointerInput(project.id) {
                                            detectDragGestures(
                                                onDragStart = { isPanelResizing = true },
                                                onDragCancel = {
                                                    isPanelResizing = false
                                                    currentOnResizeCanvas(
                                                        dragCanvasWidthDp.roundToInt(),
                                                        dragCanvasHeightDp.roundToInt()
                                                    )
                                                },
                                                onDragEnd = {
                                                    isPanelResizing = false
                                                    currentOnResizeCanvas(
                                                        dragCanvasWidthDp.roundToInt(),
                                                        dragCanvasHeightDp.roundToInt()
                                                    )
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    val dxDp = with(density) { dragAmount.x.toDp().value }
                                                    dragCanvasWidthDp = (dragCanvasWidthDp + dxDp).coerceIn(140f, 360f)
                                                    val wInt = dragCanvasWidthDp.roundToInt()
                                                    val hInt = dragCanvasHeightDp.roundToInt()
                                                    com.example.service.DynamicOverlayRegistry.setActiveCanvasSizeDp(wInt, hInt)
                                                    currentOnResizeCanvas(wInt, hInt)
                                                }
                                            )
                                        }
                                )

                                // Bottom Edge Resize Strip for Floating Panel Window
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .height(12.dp)
                                        .fillMaxWidth()
                                        .pointerInput(project.id) {
                                            detectDragGestures(
                                                onDragStart = { isPanelResizing = true },
                                                onDragCancel = {
                                                    isPanelResizing = false
                                                    currentOnResizeCanvas(
                                                        dragCanvasWidthDp.roundToInt(),
                                                        dragCanvasHeightDp.roundToInt()
                                                    )
                                                },
                                                onDragEnd = {
                                                    isPanelResizing = false
                                                    currentOnResizeCanvas(
                                                        dragCanvasWidthDp.roundToInt(),
                                                        dragCanvasHeightDp.roundToInt()
                                                    )
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    val dyDp = with(density) { dragAmount.y.toDp().value }
                                                    dragCanvasHeightDp = (dragCanvasHeightDp + dyDp).coerceIn(140f, 520f)
                                                    val wInt = dragCanvasWidthDp.roundToInt()
                                                    val hInt = dragCanvasHeightDp.roundToInt()
                                                    com.example.service.DynamicOverlayRegistry.setActiveCanvasSizeDp(wInt, hInt)
                                                    currentOnResizeCanvas(wInt, hInt)
                                                }
                                            )
                                        }
                                )

                                // Corner Resize Handle for Floating Panel Window (Works both when Auto Size is ON and OFF)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(topStart = 9.dp, bottomEnd = 16.dp))
                                        .background(
                                            if (isDarkBg) Color(0xFF38BDF8).copy(alpha = 0.55f)
                                            else Color(0xFF2563EB).copy(alpha = 0.40f)
                                        )
                                        .pointerInput(project.id) {
                                            detectDragGestures(
                                                onDragStart = { isPanelResizing = true },
                                                onDragCancel = {
                                                    isPanelResizing = false
                                                    currentOnResizeCanvas(
                                                        dragCanvasWidthDp.roundToInt(),
                                                        dragCanvasHeightDp.roundToInt()
                                                    )
                                                },
                                                onDragEnd = {
                                                    isPanelResizing = false
                                                    currentOnResizeCanvas(
                                                        dragCanvasWidthDp.roundToInt(),
                                                        dragCanvasHeightDp.roundToInt()
                                                    )
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    val dxDp = with(density) { dragAmount.x.toDp().value }
                                                    val dyDp = with(density) { dragAmount.y.toDp().value }
                                                    dragCanvasWidthDp = (dragCanvasWidthDp + dxDp).coerceIn(140f, 360f)
                                                    dragCanvasHeightDp = (dragCanvasHeightDp + dyDp).coerceIn(140f, 520f)
                                                    val wInt = dragCanvasWidthDp.roundToInt()
                                                    val hInt = dragCanvasHeightDp.roundToInt()
                                                    com.example.service.DynamicOverlayRegistry.setActiveCanvasSizeDp(wInt, hInt)
                                                    currentOnResizeCanvas(wInt, hInt)
                                                }
                                            )
                                        }
                                        .testTag("canvas_resize_handle"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "↘",
                                        color = headerTextColor,
                                        fontSize = 13.sp,
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
