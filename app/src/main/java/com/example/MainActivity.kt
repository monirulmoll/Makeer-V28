package com.example

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CanvasComponentEntity
import com.example.data.isLuaScriptProject
import com.example.data.isScreen1WidgetType
import com.example.engine.LocalConfigStateWriter
import com.example.engine.ShizukuPrivilegeBridge
import com.example.ui.CompiledStandaloneAppScreen
import com.example.ui.ComponentCountSummary
import com.example.ui.ComponentTrackerBanner
import com.example.ui.CreateNewLuaWidgetScreen
import com.example.ui.KotlinProjectCodeEngine
import com.example.ui.MainViewModel
import com.example.ui.PropertyInspectorBottomDock
import com.example.ui.SketchwarePaletteEntry
import com.example.ui.SketchwareStudioSplitWorkspace
import com.example.ui.StudioAiGgufGateScreen
import com.example.ui.StudioAiWorkspaceScreen
import com.example.ui.StudioDestination
import com.example.ui.StudioEditCodeDialog
import com.example.ui.StudioProjectLauncherScreen
import com.example.ui.StudioUiState
import com.example.ui.StudioWelcomeModeScreen
import com.example.ui.TargetWriteErrorShizukuDialog
import com.example.ui.parseHexColorSafe
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        viewModel.refreshOverlayPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val projects by viewModel.allProjects.collectAsStateWithLifecycle()
                val components by viewModel.activeComponents.collectAsStateWithLifecycle()
                val bundledStandaloneComponents by viewModel.bundledStandaloneComponents.collectAsStateWithLifecycle()

                when (uiState.destination) {
                    StudioDestination.WELCOME_SCREEN -> {
                        StudioWelcomeModeScreen(
                            hasStoragePermission = uiState.hasStoragePermission,
                            hasOverlayPermission = uiState.hasOverlayPermission,
                            onRefreshPermissions = viewModel::refreshOverlayPermission,
                            onSelectOfflineMode = viewModel::openOfflineManualMode,
                            onSelectOnlineAiMode = viewModel::openOnlineAiMode
                        )
                    }

                    StudioDestination.PROJECT_LAUNCHER -> {
                        StudioProjectLauncherScreen(
                            uiState = uiState,
                            projects = projects,
                            defaultPathProvider = viewModel::getDefaultTargetFilePath,
                            onOpenCreateDialog = { viewModel.openCreateProjectDialog(true) },
                            onDismissCreateDialog = { viewModel.openCreateProjectDialog(false) },
                            onCreateProject = viewModel::createNewBlankProject,
                            onOpenExistingPicker = { viewModel.openExistingProjectsPicker(true) },
                            onDismissExistingPicker = { viewModel.openExistingProjectsPicker(false) },
                            onSelectProject = viewModel::openExistingProject,
                            onDuplicateProject = viewModel::duplicateProject,
                            onDeleteProject = viewModel::deleteProject,
                            onOpenEditProjectDialog = { proj -> viewModel.openEditProjectDialog(proj) },
                            onDismissEditProjectDialog = { viewModel.openEditProjectDialog(null) },
                            onSaveProjectConfiguration = viewModel::updateProjectNameAndLogo,
                            onImportLogoUri = viewModel::importProjectLogoUri,
                            onSyncErrorStudioFolder = viewModel::syncAndImportProjectsFromErrorStudioFolder,
                            onImportProjectUri = viewModel::importProjectFromUri,
                            onRefreshPermissions = viewModel::refreshOverlayPermission,
                            onOpenOnlineAiMode = viewModel::openOnlineAiMode,
                            onBackToWelcome = viewModel::navigateBackToWelcome,
                            onSaveDefaultButtonLogic = viewModel::saveDefaultButtonLogic
                        )
                    }

                    StudioDestination.AI_GGUF_GATE,
                    StudioDestination.AI_STUDIO_WORKSPACE -> {
                        BackHandler {
                            viewModel.openOfflineManualMode()
                        }
                        StudioAiWorkspaceScreen(
                            termuxServerConfig = uiState.termuxServerConfig,
                            onUpdateTermuxHost = viewModel::updateTermuxHost,
                            onUpdateTermuxPort = viewModel::updateTermuxPort,
                            onUpdateTermuxUrl = viewModel::updateTermuxUrl,
                            onSaveTermuxServerConfig = viewModel::saveTermuxServerConfig,
                            onTestTermuxConnection = viewModel::testTermuxServerConnection,
                            onToggleServerConfigPanel = viewModel::toggleTermuxServerConfigPanel,
                            hasStoragePermission = uiState.hasStoragePermission,
                            hasOverlayPermission = uiState.hasOverlayPermission,
                            isAiBuilding = uiState.isGeneratingAiBlueprint,
                            liveBuildSteps = uiState.aiLiveBuildSteps,
                            chatHistory = uiState.aiChatHistory,
                            aiBuiltProject = uiState.aiBuiltProject,
                            aiBuiltComponents = uiState.aiBuiltComponents,
                            isAiFloatingOverlayRunning = uiState.isAiFloatingOverlayRunning,
                            aiCompiledApkSummary = uiState.aiCompiledApkSummary,
                            onRefreshPermissions = viewModel::refreshOverlayPermission,
                            onSendPromptToAi = viewModel::sendPromptInAiMode,
                            onToggleAiFloatOverlay = viewModel::toggleAiModeFloatingOverlay,
                            onTriggerAiWidgetTest = viewModel::triggerAiWidgetTest,
                            onDownloadAiApk = viewModel::compileAndDownloadAiApk,
                            onInstallAiApk = { viewModel.installCompiledApk(this@MainActivity) },
                            onChangeGgufModel = viewModel::toggleTermuxServerConfigPanel,
                            onSwitchToManualOfflineMode = viewModel::openOfflineManualMode
                        )
                    }

                    StudioDestination.COMPILED_STANDALONE_APP -> {
                        val activeProj = uiState.activeProject
                        val standaloneItems = if (uiState.isBundledStandaloneApk) {
                            bundledStandaloneComponents
                        } else {
                            components
                        }
                        if (!uiState.isBundledStandaloneApk) {
                            BackHandler {
                                viewModel.closeCompiledAppPreview()
                            }
                        }
                        if (activeProj != null) {
                            CompiledStandaloneAppScreen(
                                project = activeProj,
                                initialComponents = standaloneItems,
                                compiledPackageName = uiState.compiledAppPackageName.ifBlank { contextPackageName() },
                                isStandaloneInstalledApk = uiState.isBundledStandaloneApk,
                                isOverlayRunning = uiState.isSystemOverlayRunning,
                                hasStoragePermission = uiState.hasStoragePermission,
                                hasOverlayPermission = uiState.hasOverlayPermission,
                                shizukuStatusSummary = uiState.shizukuStatusSummary,
                                isShizukuReady = uiState.isShizukuReady,
                                isShizukuRunning = uiState.isShizukuRunning,
                                isShizukuInstalled = uiState.isShizukuInstalled,
                                statusMessage = uiState.statusToast,
                                onStartOverlay = viewModel::launchSystemFloatingOverlay,
                                onStopOverlay = viewModel::stopSystemFloatingOverlay,
                                onRefreshPermissions = viewModel::refreshOverlayPermission,
                                onRequestOrLaunchShizuku = viewModel::requestOrLaunchShizuku,
                                onTestAllTargetPaths = viewModel::testAllActiveTargetPathsNow,
                                onTriggerComponentLive = viewModel::triggerComponentAction,
                                onBackToStudioEditor = if (uiState.isBundledStandaloneApk) null else {
                                    { viewModel.closeCompiledAppPreview() }
                                }
                            )
                        }
                    }

                    StudioDestination.CANVAS_WORKSPACE -> {
                        if (uiState.showCreateCustomWidgetScreen) {
                            CreateNewLuaWidgetScreen(
                                defaultButtonLogic = uiState.defaultButtonLogic,
                                onBack = { viewModel.openCreateCustomWidgetScreen(false) },
                                onCreateWidget = viewModel::createAndAddCustomLuaWidget
                            )
                            return@MyApplicationTheme
                        }

                        BackHandler {
                            if (uiState.showCreateCustomWidgetScreen) {
                                viewModel.openCreateCustomWidgetScreen(false)
                            } else if (uiState.showChangeBackgroundDialog) {
                                viewModel.openChangeBackgroundDialog(false)
                            } else if (uiState.showEditFloatingPanelDialog) {
                                viewModel.openEditFloatingPanelDialog(false)
                            } else if (uiState.showEditCodeDialog) {
                                viewModel.openEditCodeDialog(false)
                            } else if (uiState.selectedComponentId != null) {
                                viewModel.selectComponent(null)
                            } else {
                                viewModel.navigateBackToLauncher()
                            }
                        }

                        val activeScreenComponents = remember(components, uiState.activePreviewScreen) {
                            if (uiState.activePreviewScreen == 1) {
                                components.filter { isScreen1WidgetType(it.type) }
                            } else {
                                components.filter { !isScreen1WidgetType(it.type) }
                            }
                        }
                        val trackerSummary = remember(activeScreenComponents) {
                            viewModel.computeComponentTrackerSummary(activeScreenComponents)
                        }

                        StudioCanvasBuilderScreen(
                            uiState = uiState,
                            components = components,
                            trackerSummary = trackerSummary,
                            onBackToLauncher = viewModel::navigateBackToLauncher,
                            onSelectPreviewScreen = viewModel::selectPreviewScreen,
                            onAddPaletteEntry = { entry ->
                                viewModel.addPaletteItemToCanvas(
                                    widgetType = entry.widgetType,
                                    customPrefix = entry.title,
                                    customWidthDp = entry.customWidthDp,
                                    customHeightDp = entry.customHeightDp,
                                    customBgHex = entry.customBgHex,
                                    customTextHex = entry.customTextHex
                                )
                            },
                            onSelectComponent = viewModel::selectComponent,
                            onUpdateComponent = viewModel::updateComponent,
                            onSaveProjectDesign = viewModel::saveCurrentProjectDesign,
                            onMoveComponent = viewModel::updateComponentPosition,
                            onResizeComponent = viewModel::resizeComponent,
                            onResizeCanvas = viewModel::resizeActiveProjectCanvas,
                            onToggleAutoFixSize = viewModel::toggleAutoFixSize,
                            onOpenEditFloatingPanel = { viewModel.openEditFloatingPanelDialog(true) },
                            onDismissEditFloatingPanel = { viewModel.openEditFloatingPanelDialog(false) },
                            onSaveFloatingPanelConfig = viewModel::updateFloatingPanelNameAndLogo,
                            onImportFloatingLogoUri = viewModel::importProjectLogoUri,
                            onOpenChangeBackground = { viewModel.openChangeBackgroundDialog(true) },
                            onDismissChangeBackground = { viewModel.openChangeBackgroundDialog(false) },
                            onSaveFloatingBackground = viewModel::updateFloatingWindowBackground,
                            onImportFloatingBackgroundUri = viewModel::importFloatingBackgroundUri,
                            onOpenEditCode = { viewModel.openEditCodeDialog(true) },
                            onDismissEditCode = { viewModel.openEditCodeDialog(false) },
                            onCompileCodeToVisual = { edited ->
                                viewModel.applyEditedKotlinCodeToVisualScreen(edited, andCompileApk = false)
                            },
                            onCompileCodeToApk = { edited ->
                                viewModel.applyEditedKotlinCodeToVisualScreen(edited, andCompileApk = true)
                            },
                            onDuplicateComponent = viewModel::duplicateSelectedComponent,
                            onDeleteComponent = viewModel::deleteComponent,
                            onClearCanvas = viewModel::clearEntireCanvas,
                            onPickImageForComponent = viewModel::assignPickedImageToComponent,
                            onPickSoundForComponent = viewModel::assignPickedSoundToComponent,
                            onTriggerComponentLive = viewModel::triggerComponentAction,
                            onDownloadFloatingWindow = { viewModel.downloadFloatingWindowToAndroid(null) },
                            onInstallCompiledApk = { viewModel.installCompiledApk(this@MainActivity) },
                            onRunCompiledAppPreview = { viewModel.openCompiledAppPreview() },
                            onSaveFloatingWindowToUri = viewModel::saveFloatingWindowToCustomUri,
                            onDismissDownloadDialog = viewModel::dismissDownloadSummaryDialog,
                            onLaunchSystemOverlay = viewModel::launchSystemFloatingOverlay,
                            onStopSystemOverlay = viewModel::stopSystemFloatingOverlay,
                            onRefreshPermissions = viewModel::refreshOverlayPermission,
                            onRequestOrLaunchShizuku = viewModel::requestOrLaunchShizuku,
                            onTestAllTargetPaths = viewModel::testAllActiveTargetPathsNow,
                            onSetDefaultForAllWidgets = viewModel::setDefaultButtonLogicForAllWidgets,
                            onClearLuaRuntimeLogs = viewModel::clearLuaRuntimeLogs,
                            onOpenCreateCustomWidget = { viewModel.openCreateCustomWidgetScreen(true) },
                            onAddSavedCustomWidget = viewModel::addCustomWidgetSpecToCanvas
                        )
                    }
                }
            }
        }
    }

    private fun contextPackageName(): String = packageName
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioCanvasBuilderScreen(
    uiState: StudioUiState,
    components: List<CanvasComponentEntity>,
    trackerSummary: ComponentCountSummary,
    onBackToLauncher: () -> Unit,
    onSelectPreviewScreen: (Int) -> Unit = {},
    onAddPaletteEntry: (SketchwarePaletteEntry) -> Unit,
    onSelectComponent: (Long?) -> Unit,
    onUpdateComponent: (CanvasComponentEntity) -> Unit,
    onSaveProjectDesign: (CanvasComponentEntity?) -> Unit = {},
    onMoveComponent: (CanvasComponentEntity, Int, Int) -> Unit,
    onResizeComponent: (CanvasComponentEntity, Int, Int) -> Unit,
    onResizeCanvas: (Int, Int) -> Unit,
    onToggleAutoFixSize: () -> Unit,
    onOpenEditFloatingPanel: () -> Unit = {},
    onDismissEditFloatingPanel: () -> Unit = {},
    onSaveFloatingPanelConfig: (String, String) -> Unit = { _, _ -> },
    onImportFloatingLogoUri: (Uri, (String) -> Unit) -> Unit = { _, _ -> },
    onOpenChangeBackground: () -> Unit = {},
    onDismissChangeBackground: () -> Unit = {},
    onSaveFloatingBackground: (String, String) -> Unit = { _, _ -> },
    onImportFloatingBackgroundUri: (Uri, (String) -> Unit) -> Unit = { _, _ -> },
    onOpenEditCode: () -> Unit = {},
    onDismissEditCode: () -> Unit = {},
    onCompileCodeToVisual: (Map<String, String>) -> Unit = {},
    onCompileCodeToApk: (Map<String, String>) -> Unit = {},
    onDuplicateComponent: (CanvasComponentEntity) -> Unit,
    onDeleteComponent: (Long) -> Unit,
    onClearCanvas: () -> Unit,
    onPickImageForComponent: (CanvasComponentEntity, Uri) -> Unit,
    onPickSoundForComponent: (CanvasComponentEntity, Uri, Boolean) -> Unit,
    onTriggerComponentLive: (CanvasComponentEntity, String?) -> Unit,
    onDownloadFloatingWindow: () -> Unit,
    onInstallCompiledApk: () -> Unit = {},
    onRunCompiledAppPreview: () -> Unit = {},
    onSaveFloatingWindowToUri: (Uri) -> Unit,
    onDismissDownloadDialog: () -> Unit,
    onLaunchSystemOverlay: () -> Unit,
    onStopSystemOverlay: () -> Unit,
    onRefreshPermissions: () -> Unit = {},
    onRequestOrLaunchShizuku: () -> Unit = {},
    onTestAllTargetPaths: () -> Unit = {},
    onSetDefaultForAllWidgets: () -> Unit = {},
    onClearLuaRuntimeLogs: () -> Unit = {},
    onOpenCreateCustomWidget: () -> Unit = {},
    onAddSavedCustomWidget: (com.example.engine.LuaCustomWidgetSpec) -> Unit = {}
) {
    val context = LocalContext.current
    val project = uiState.activeProject ?: return
    val isLuaScriptMode = project.isLuaScriptProject()
    val effectivePreviewScreen = if (isLuaScriptMode) 2 else uiState.activePreviewScreen
    val selectedComponent = remember(components, uiState.selectedComponentId) {
        components.find { it.id == uiState.selectedComponentId }
    }

    val runtimeStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshPermissions()
        if (!LocalConfigStateWriter.hasStoragePermissionGranted(context)) {
            LocalConfigStateWriter.requestStoragePermission(context)
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri ->
        if (uri != null) {
            onSaveFloatingWindowToUri(uri)
        }
    }

    if (uiState.showEditFloatingPanelDialog) {
        var editedPanelTitle by remember(project.id) {
            mutableStateOf(project.overlayTitle.ifBlank { project.name })
        }
        var editedFloatingLogoPath by remember(project.id) {
            mutableStateOf(project.floatingLogoPath.ifBlank { project.appLogoPath })
        }
        val currentPanelBgColor = remember(project.canvasBgColorHex) {
            parseHexColorSafe(project.canvasBgColorHex, Color.White)
        }
        val isDarkPanelBg = remember(project.canvasBgColorHex, project.canvasBgImagePath) {
            project.canvasBgImagePath.isNotBlank() ||
                (currentPanelBgColor.red * 0.299f + currentPanelBgColor.green * 0.587f + currentPanelBgColor.blue * 0.114f) < 0.55f
        }
        val panelHeaderTextColor = if (isDarkPanelBg) Color.White else Color(0xFF0F172A)

        val floatingLogoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                onImportFloatingLogoUri(uri) { savedPath ->
                    editedFloatingLogoPath = savedPath
                    onSaveFloatingPanelConfig(editedPanelTitle, savedPath)
                }
            }
        }

        val previewGoalBitmap = remember(editedFloatingLogoPath) {
            if (editedFloatingLogoPath.isNotBlank()) {
                val f = File(editedFloatingLogoPath)
                if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
            } else null
        }

        AlertDialog(
            onDismissRequest = onDismissEditFloatingPanel,
            containerColor = Color(0xFF0E1528),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFE2E8F0),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Edit Floating Window Name & Image",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Customize the Floating Window header name and circular logo image. This image and name appear in the floating window header bar and inside the minimized round ('Goal') floating bubble.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )

                    // Live Floating Header Preview (using actual floating window background, no permanent blue)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = currentPanelBgColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(panelHeaderTextColor.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (previewGoalBitmap != null) {
                                        Image(
                                            bitmap = previewGoalBitmap,
                                            contentDescription = "Header Logo Preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = null,
                                            tint = panelHeaderTextColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = editedPanelTitle.trim().ifEmpty {
                                        project.name.trim().ifEmpty { "Floating Window" }
                                    },
                                    color = panelHeaderTextColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "✕",
                                color = panelHeaderTextColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2563EB))
                                .border(BorderStroke(2.dp, Color(0xFF38BDF8)), CircleShape)
                                .clickable {
                                    floatingLogoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .testTag("floating_panel_goal_logo_preview"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (previewGoalBitmap != null) {
                                Image(
                                    bitmap = previewGoalBitmap,
                                    contentDescription = "Selected Floating Goal Logo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            } else {
                                Text(
                                    text = editedPanelTitle.trim().ifEmpty {
                                        project.name.trim().ifEmpty { "Float" }
                                    },
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

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Button(
                                onClick = {
                                    floatingLogoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B46F6)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pick_floating_goal_logo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "Choose Floating Window Image",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (editedFloatingLogoPath.isNotBlank()) "Change Window Image" else "Select Window Image",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (editedFloatingLogoPath.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        editedFloatingLogoPath = ""
                                        onSaveFloatingPanelConfig(editedPanelTitle, "")
                                    },
                                    modifier = Modifier.testTag("remove_floating_goal_logo_button")
                                ) {
                                    Text(
                                        text = "Remove Image (Show Text Only)",
                                        color = Color(0xFFF87171),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editedPanelTitle,
                        onValueChange = {
                            editedPanelTitle = it
                            onSaveFloatingPanelConfig(it, editedFloatingLogoPath)
                        },
                        label = { Text("Floating Window Name (Header Title)", color = Color(0xFF94A3B8)) },
                        placeholder = { Text("Enter floating window name...", color = Color(0xFF64748B)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF081329),
                            unfocusedContainerColor = Color(0xFF081329),
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF38BDF8)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_floating_panel_name_input")
                    )

                    OutlinedButton(
                        onClick = {
                            onSaveFloatingPanelConfig(editedPanelTitle, editedFloatingLogoPath)
                            onDismissEditFloatingPanel()
                            onOpenChangeBackground()
                        },
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_panel_open_bg_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Change Floating Window Background",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Change Floating Window Background",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveFloatingPanelConfig(editedPanelTitle, editedFloatingLogoPath)
                        onDismissEditFloatingPanel()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("close_floating_panel_config_button")
                ) {
                    Text("Save & Close", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissEditFloatingPanel) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    if (uiState.showChangeBackgroundDialog) {
        var editedBgHex by remember(project.id) {
            mutableStateOf(project.canvasBgColorHex.ifBlank { "#FFFFFF" })
        }
        var editedBgImagePath by remember(project.id) {
            mutableStateOf(project.canvasBgImagePath)
        }

        // Helper to extract 6-char RGB and initial opacity percentage from #RRGGBB or #AARRGGBB
        fun extractRgb6(hex: String): String {
            val clean = hex.trim().removePrefix("#").uppercase(Locale.US)
            return when (clean.length) {
                8 -> clean.substring(2)
                6 -> clean
                else -> "FFFFFF"
            }
        }

        fun extractAlphaPercent(hex: String): Float {
            val clean = hex.trim().removePrefix("#").uppercase(Locale.US)
            if (clean.length == 8) {
                val alphaInt = clean.substring(0, 2).toIntOrNull(16) ?: 255
                return ((alphaInt / 255f) * 100f).coerceIn(10f, 100f)
            }
            return 100f
        }

        fun combineAlphaAndRgb(alphaPercent: Float, currentHex: String): String {
            val rgb6 = extractRgb6(currentHex)
            val pct = alphaPercent.roundToInt().coerceIn(10, 100)
            if (pct >= 100) return "#$rgb6"
            val alphaByte = ((pct / 100f) * 255f).roundToInt().coerceIn(25, 255)
            val alphaHex = String.format(Locale.US, "%02X", alphaByte)
            return "#$alphaHex$rgb6"
        }

        var bgOpacityPercent by remember(project.id) {
            mutableFloatStateOf(extractAlphaPercent(project.canvasBgColorHex))
        }

        val bgImagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                onImportFloatingBackgroundUri(uri) { savedPath ->
                    editedBgImagePath = savedPath
                    onSaveFloatingBackground(editedBgHex, savedPath)
                }
            }
        }

        val previewBgBitmap = remember(editedBgImagePath) {
            if (editedBgImagePath.isNotBlank()) {
                val f = File(editedBgImagePath)
                if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
            } else null
        }

        val previewBgColor = remember(editedBgHex) {
            parseHexColorSafe(editedBgHex, Color.White)
        }

        val bgPresets = remember {
            listOf(
                "#FFFFFF" to "Pure White",
                "#0F172A" to "Midnight Dark",
                "#0A1224" to "Studio Navy",
                "#050811" to "OLED Black",
                "#1E293B" to "Slate Steel",
                "#1E1B4B" to "Deep Indigo",
                "#2E1065" to "Cyber Violet",
                "#062E22" to "Emerald Matrix",
                "#2A0A18" to "Crimson Abyss",
                "#0C2D48" to "Ocean Blue",
                "#CC0F172A" to "Dark Glass",
                "#F1F5F9" to "Soft Ice"
            )
        }

        AlertDialog(
            onDismissRequest = onDismissChangeBackground,
            containerColor = Color(0xFF0E1528),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFE2E8F0),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF7C3AED), Color(0xFF2563EB))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Floating Window Background",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Customize solid colors, glass opacity, or wallpaper image",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8)
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
                    // 1. Live Floating Window Preview Box
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF070B14),
                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("floating_bg_live_preview")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val isDarkPreviewBg = remember(editedBgHex, previewBgBitmap) {
                                previewBgBitmap != null ||
                                    (previewBgColor.red * 0.299f + previewBgColor.green * 0.587f + previewBgColor.blue * 0.114f) < 0.55f
                            }
                            val previewHeaderTextColor = if (isDarkPreviewBg) Color.White else Color(0xFF0F172A)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = previewBgColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(108.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (previewBgBitmap != null) {
                                        Image(
                                            bitmap = previewBgBitmap,
                                            contentDescription = "Live Background Image Preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 5.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = project.overlayTitle.ifBlank { project.name.ifBlank { "Floating Window" } },
                                                color = previewHeaderTextColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = previewHeaderTextColor.copy(alpha = 0.16f),
                                                    border = BorderStroke(1.dp, previewHeaderTextColor.copy(alpha = 0.38f))
                                                ) {
                                                    Text(
                                                        text = "Minimize",
                                                        color = previewHeaderTextColor,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = previewHeaderTextColor.copy(alpha = 0.16f),
                                                    border = BorderStroke(1.dp, previewHeaderTextColor.copy(alpha = 0.38f))
                                                ) {
                                                    Text(
                                                        text = "Hide",
                                                        color = previewHeaderTextColor,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFFEF4444).copy(alpha = 0.85f),
                                                    border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                                                ) {
                                                    Text(
                                                        text = "Kill",
                                                        color = Color.White,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF1E293B).copy(alpha = 0.88f),
                                                border = BorderStroke(1.dp, Color(0xFF10B981)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(26.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(horizontal = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("Sample Toggle #1", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    Text("ON", color = Color(0xFF10B981), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                                                }
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF2563EB).copy(alpha = 0.9f),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(24.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text("Sample Action Button", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Preset Background Color Swatches
                    Text(
                        text = "Preset Background Colors",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        bgPresets.chunked(3).forEach { rowPresets ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowPresets.forEach { (presetHex, presetName) ->
                                    val isSelectedPreset = editedBgHex.equals(presetHex, ignoreCase = true) ||
                                        extractRgb6(editedBgHex).equals(extractRgb6(presetHex), ignoreCase = true)
                                    val swatchColor = parseHexColorSafe(presetHex, Color.White)
                                    val slug = presetHex.removePrefix("#").lowercase(Locale.US)
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelectedPreset) Color(0xFF1E293B) else Color(0xFF0A1020),
                                        border = BorderStroke(
                                            width = if (isSelectedPreset) 1.5.dp else 1.dp,
                                            color = if (isSelectedPreset) Color(0xFF38BDF8) else Color(0xFF233152)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                editedBgHex = presetHex
                                                bgOpacityPercent = extractAlphaPercent(presetHex)
                                                onSaveFloatingBackground(presetHex, editedBgImagePath)
                                            }
                                            .testTag("bg_preset_$slug")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clip(CircleShape)
                                                    .background(swatchColor)
                                                    .border(BorderStroke(1.dp, Color(0xFF64748B)), CircleShape)
                                            )
                                            Text(
                                                text = presetName,
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = if (isSelectedPreset) FontWeight.ExtraBold else FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Opacity / Glass Transparency Slider & Quick Chips
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Window Opacity / Transparency",
                                color = Color(0xFFCBD5E1),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${bgOpacityPercent.roundToInt()}%",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Slider(
                            value = bgOpacityPercent,
                            onValueChange = { newPct ->
                                bgOpacityPercent = newPct
                                val nextHex = combineAlphaAndRgb(newPct, editedBgHex)
                                editedBgHex = nextHex
                                onSaveFloatingBackground(nextHex, editedBgImagePath)
                            },
                            valueRange = 10f..100f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("floating_bg_opacity_slider")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(100, 85, 70, 50, 25).forEach { pct ->
                                val isActivePct = bgOpacityPercent.roundToInt() == pct
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isActivePct) Color(0xFF5B46F6) else Color(0xFF151F36),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isActivePct) Color(0xFF818CF8) else Color(0xFF283556)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            bgOpacityPercent = pct.toFloat()
                                            val nextHex = combineAlphaAndRgb(pct.toFloat(), editedBgHex)
                                            editedBgHex = nextHex
                                            onSaveFloatingBackground(nextHex, editedBgImagePath)
                                        }
                                        .testTag("bg_opacity_chip_$pct")
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$pct%",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Custom Background Image (Gallery Wallpaper)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Custom Background Image (Wallpaper)",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    bgImagePickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B46F6)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pick_floating_bg_image_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "Select Background Image",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (editedBgImagePath.isNotBlank()) "Change BG Image" else "Select BG Image",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (editedBgImagePath.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        editedBgImagePath = ""
                                        onSaveFloatingBackground(editedBgHex, "")
                                    },
                                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("remove_floating_bg_image_button")
                                ) {
                                    Text(
                                        text = "Remove Image",
                                        color = Color(0xFFF87171),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // 5. Custom Hex Color Input & Reset Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editedBgHex,
                            onValueChange = { next ->
                                editedBgHex = next
                                val trimmed = next.trim()
                                val candidate = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
                                if (candidate.length == 7 || candidate.length == 9) {
                                    onSaveFloatingBackground(candidate, editedBgImagePath)
                                }
                            },
                            label = { Text("Custom Background Hex (#RRGGBB / #AARRGGBB)", color = Color(0xFF94A3B8), fontSize = 11.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF081329),
                                unfocusedContainerColor = Color(0xFF081329),
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF1E293B),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(0xFF38BDF8)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("floating_bg_hex_input")
                        )

                        TextButton(
                            onClick = {
                                editedBgHex = "#FFFFFF"
                                editedBgImagePath = ""
                                bgOpacityPercent = 100f
                                onSaveFloatingBackground("#FFFFFF", "")
                            },
                            modifier = Modifier.testTag("reset_floating_bg_button")
                        ) {
                            Text("Reset", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveFloatingBackground(editedBgHex, editedBgImagePath)
                        onDismissChangeBackground()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("save_floating_bg_button")
                ) {
                    Text("Save & Apply", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissChangeBackground) {
                    Text("Close", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    if (uiState.showEditCodeDialog) {
        val initialFiles = remember(project, components, uiState.customEditedKotlinFiles) {
            if (uiState.customEditedKotlinFiles.isNotEmpty()) {
                uiState.customEditedKotlinFiles
            } else {
                KotlinProjectCodeEngine.generateKotlinFilesForProject(project, components)
            }
        }
        StudioEditCodeDialog(
            project = project,
            components = components,
            initialFiles = initialFiles,
            onDismiss = onDismissEditCode,
            onCompileCodeToVisualScreen = onCompileCodeToVisual,
            onCompileCodeToApk = onCompileCodeToApk
        )
    }

    if (uiState.isBuildingApk) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    text = "Compiling '${project.name}' APK...",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = uiState.buildProgressStepText.ifBlank {
                            "Compiling your visual screen widgets and signing standalone Android APK..."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF0288D1),
                        fontWeight = FontWeight.SemiBold
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = uiState.rawJavaBuildPreview,
                                color = Color(0xFF38BDF8),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (uiState.downloadedFileSummary != null) {
        AlertDialog(
            onDismissRequest = onDismissDownloadDialog,
            title = {
                Text(
                    text = if (isLuaScriptMode) "Saved '${project.name}' Lua Script" else "Compiled '${project.name}' APK Ready",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = Color(0xFFF0FDF4),
                        border = BorderStroke(1.dp, Color(0xFF22C55E)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.downloadedFileSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF065F46),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    if (uiState.rawJavaBuildPreview.isNotBlank()) {
                        Text(
                            text = if (isLuaScriptMode) "Generated Lua Script (${uiState.downloadedFileName}):" else "Compiled Kotlin Widget Code (${components.size} widget(s)):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 130.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = uiState.rawJavaBuildPreview,
                                    color = Color(0xFF4ADE80),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                    if (isLuaScriptMode) {
                        Button(
                            onClick = {
                                onDismissDownloadDialog()
                                onLaunchSystemOverlay()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("run_saved_lua_script_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Run Lua",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Run Lua Script", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = onInstallCompiledApk,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("install_compiled_apk_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.InstallMobile,
                                    contentDescription = "Install Compiled APK",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Install '${project.name}' APK", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = onRunCompiledAppPreview,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("run_compiled_app_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Run Compiled App",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Run Compiled '${project.name}' App", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    createDocumentLauncher.launch(uiState.downloadedFileName)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("save_compiled_apk_folder_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Save APK to Custom Folder",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save APK to Folder...", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismissDownloadDialog) {
                    Text("Close")
                }
            }
        )
    }

    val topBarLogoPath = project.floatingLogoPath.ifBlank { project.appLogoPath }
    val topBarLogoBitmap = remember(topBarLogoPath) {
        if (topBarLogoPath.isNotBlank()) {
            val f = File(topBarLogoPath)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
        } else null
    }

    var isPreviewFullScreen by remember { mutableStateOf(false) }

    BackHandler(enabled = isPreviewFullScreen && selectedComponent == null) {
        isPreviewFullScreen = false
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = Color(0xFF060B16),
        topBar = {
            if (!isPreviewFullScreen) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A1224))
            ) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = onBackToLauncher,
                            modifier = Modifier.testTag("back_to_launcher_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Project Launcher",
                                tint = Color.White
                            )
                        }
                    },
                    title = {
                        Row(
                            modifier = Modifier
                                .clickable { onOpenEditFloatingPanel() }
                                .testTag("top_bar_edit_floating_panel_title"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Circular Floating Window Logo in TopAppBar
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFF7C3AED), Color(0xFF4F46E5))
                                        )
                                    )
                                    .border(BorderStroke(1.5.dp, Color(0xFF818CF8)), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (topBarLogoBitmap != null) {
                                    Image(
                                        bitmap = topBarLogoBitmap,
                                        contentDescription = "Floating Window Logo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Layers,
                                        contentDescription = "Edit Floating Window Image",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = project.overlayTitle.ifBlank { project.name },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isLuaScriptMode) {
                                        "Lua Script • ${project.canvasWidthDp}×${project.canvasHeightDp} dp • ${components.size} widgets"
                                    } else {
                                        "App Studio • ${project.canvasWidthDp}×${project.canvasHeightDp} dp • ${components.size} widgets"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF7B93B8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        Button(
                            onClick = onDownloadFloatingWindow,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .testTag("download_floating_window_button")
                        ) {
                            Icon(
                                imageVector = if (isLuaScriptMode) Icons.Default.Save else Icons.Default.Build,
                                contentDescription = if (isLuaScriptMode) "Save Lua" else "Build APK from Raw Java",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isLuaScriptMode) "Save Lua" else "Build",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val isLuaOrOverlayRunning = if (isLuaScriptMode) {
                            uiState.isLivePreviewMode || uiState.isSystemOverlayRunning
                        } else {
                            uiState.isSystemOverlayRunning
                        }

                        Button(
                            onClick = {
                                if (isLuaOrOverlayRunning) onStopSystemOverlay()
                                else onLaunchSystemOverlay()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isLuaOrOverlayRunning)
                                    Color(0xFFEF4444)
                                else Color(0xFF5B4DFF)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .testTag("run_floating_overlay_button")
                        ) {
                            Icon(
                                imageVector = if (isLuaOrOverlayRunning) Icons.Default.Stop else if (isLuaScriptMode) Icons.Default.PlayArrow else Icons.Default.Layers,
                                contentDescription = if (isLuaScriptMode) "Run Lua" else "Launch Floating Overlay",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isLuaScriptMode) {
                                    if (isLuaOrOverlayRunning) "Stop Lua" else "Run Lua"
                                } else {
                                    if (uiState.isSystemOverlayRunning) "Stop" else "Float"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0A1224)
                    )
                )

                // Quick Studio Toolbar Action Strip (hidden while typing on keyboard to give extra space)
                val topBarDensity = LocalDensity.current
                val isTopKeyboardVisible = WindowInsets.ime.getBottom(topBarDensity) > 0
                if (!isTopKeyboardVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF080F1E))
                        .border(BorderStroke(0.5.dp, Color(0xFF1A2B4C)))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF111C35),
                        border = BorderStroke(1.dp, Color(0xFF233863)),
                        modifier = Modifier.clickable { onOpenEditFloatingPanel() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Panel",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Edit Panel",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF111C35),
                        border = BorderStroke(1.dp, Color(0xFF3B82F6)),
                        modifier = Modifier
                            .clickable { onOpenChangeBackground() }
                            .testTag("studio_change_background_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColorSafe(project.canvasBgColorHex, Color.White))
                                    .border(BorderStroke(1.dp, Color.White), CircleShape)
                            )
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Change Floating Window Background",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Background",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF111C35),
                        border = BorderStroke(1.dp, Color(0xFF233863)),
                        modifier = Modifier
                            .clickable { onOpenEditCode() }
                            .testTag("studio_open_code_editor_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "Edit Code",
                                tint = Color(0xFFA78BFA),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Code Editor",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF111C35),
                        border = BorderStroke(1.dp, Color(0xFF233863)),
                        modifier = Modifier
                            .clickable { onSaveProjectDesign(selectedComponent) }
                            .testTag("studio_save_design_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save Design",
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Save",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (uiState.isShizukuReady) Color(0xFF064E3B) else Color(0xFF1E1338),
                        border = BorderStroke(
                            1.dp,
                            if (uiState.isShizukuReady) Color(0xFF10B981) else Color(0xFF8B5CF6)
                        ),
                        modifier = Modifier
                            .clickable { onRequestOrLaunchShizuku() }
                            .testTag("studio_shizuku_status_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Shizuku Android 15 Fix",
                                tint = if (uiState.isShizukuReady) Color(0xFF34D399) else Color(0xFFC4B5FD),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (uiState.isShizukuReady) "Shizuku ✓" else "Shizuku Fix",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF111C35),
                        border = BorderStroke(1.dp, Color(0xFF233863)),
                        modifier = Modifier.clickable { onRunCompiledAppPreview() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Preview App",
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Preview",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (components.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1E1328),
                            border = BorderStroke(1.dp, Color(0xFF4C1D4B)),
                            modifier = Modifier.clickable { onClearCanvas() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Clear Canvas",
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Clear",
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                val activeScreenComponents = remember(components, effectivePreviewScreen) {
                    if (effectivePreviewScreen == 1) {
                        components.filter { isScreen1WidgetType(it.type) }
                    } else {
                        components.filter { !isScreen1WidgetType(it.type) }
                    }
                }

                ComponentTrackerBanner(
                    summary = trackerSummary,
                    components = activeScreenComponents,
                    selectedComponentId = uiState.selectedComponentId,
                    onSelectComponentForEdit = onSelectComponent,
                    onOpenEditFloatingPanel = onOpenEditFloatingPanel
                )
                }
            }
            }
        },
        bottomBar = {
            val density = LocalDensity.current
            val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
            val isEditingAnyDialog = uiState.showEditFloatingPanelDialog ||
                uiState.showChangeBackgroundDialog ||
                uiState.showEditCodeDialog

            Column(modifier = Modifier.fillMaxWidth()) {
                AnimatedVisibility(
                    visible = selectedComponent != null,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    if (selectedComponent != null) {
                        PropertyInspectorBottomDock(
                            component = selectedComponent,
                            isLuaScriptMode = isLuaScriptMode,
                            onSetDefaultForAllWidgets = onSetDefaultForAllWidgets,
                            hasStoragePermission = uiState.hasStoragePermission,
                            isAutoFixSize = project.autoFixSize,
                            onToggleAutoFixSize = onToggleAutoFixSize,
                            onOpenEditCode = onOpenEditCode,
                            onOpenEditFloatingPanel = onOpenEditFloatingPanel,
                            onUpdateComponent = onUpdateComponent,
                            onSaveDesign = { edited -> onSaveProjectDesign(edited) },
                            onPickImageUri = { uri -> onPickImageForComponent(selectedComponent, uri) },
                            onPickSoundUri = { uri, isOff -> onPickSoundForComponent(selectedComponent, uri, isOff) },
                            onDuplicateComponent = { onDuplicateComponent(selectedComponent) },
                            onDeleteComponent = { onDeleteComponent(selectedComponent.id) },
                            onTestTriggerWrite = { editedComp -> onTriggerComponentLive(editedComp, null) },
                            onCloseDock = { onSelectComponent(null) }
                        )
                    }
                }

                // Lua Runtime Output Console Strip when running Lua Script
                AnimatedVisibility(
                    visible = isLuaScriptMode && (uiState.isLivePreviewMode || uiState.luaRuntimeLogs.isNotEmpty()) && selectedComponent == null,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    Surface(
                        color = Color(0xFF071318),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .testTag("lua_runtime_console_bar")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (uiState.isLivePreviewMode) Color(0xFF10B981) else Color(0xFF38BDF8))
                                    )
                                    Text(
                                        text = if (uiState.isLivePreviewMode) "Lua Runtime Active — Tap widgets to execute button logic" else "Lua Runtime Output",
                                        color = Color(0xFF6EE7B7),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                if (uiState.luaRuntimeLogs.isNotEmpty()) {
                                    Text(
                                        text = "Clear",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { onClearLuaRuntimeLogs() }
                                    )
                                }
                            }
                            if (uiState.luaRuntimeLogs.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 72.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    uiState.luaRuntimeLogs.takeLast(6).forEach { logLine ->
                                        Text(
                                            text = "> $logLine",
                                            color = Color(0xFFE2E8F0),
                                            fontSize = 10.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Select Preview Screen bar (Screen 1 vs Screen 2)
                // Do NOT show Preview Screen 1 in Lua mode!
                AnimatedVisibility(
                    visible = !isLuaScriptMode && !isPreviewFullScreen && selectedComponent == null && !isKeyboardVisible && !isEditingAnyDialog,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    Surface(
                        color = Color(0xFF0A1224),
                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                        tonalElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .testTag("select_preview_screen_bottom_bar")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isScreen1 = uiState.activePreviewScreen == 1
                            val isScreen2 = uiState.activePreviewScreen == 2

                            // Preview 1 (Main Screen) Button
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isScreen1) Color(0xFF4F46E5) else Color(0xFF151F36),
                                border = BorderStroke(
                                    width = if (isScreen1) 1.5.dp else 1.dp,
                                    color = if (isScreen1) Color(0xFF818CF8) else Color(0xFF283556)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSelectPreviewScreen(1) }
                                    .testTag("select_preview_screen_1_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isScreen1) Color(0xFF38BDF8) else Color(0xFF64748B))
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Preview 1 (Main Screen)",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = if (isScreen1) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                            }

                            // Preview 2 (Floating Window) Button
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isScreen2) Color(0xFF4F46E5) else Color(0xFF151F36),
                                border = BorderStroke(
                                    width = if (isScreen2) 1.5.dp else 1.dp,
                                    color = if (isScreen2) Color(0xFF818CF8) else Color(0xFF283556)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSelectPreviewScreen(2) }
                                    .testTag("select_preview_screen_2_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isScreen2) Color(0xFF38BDF8) else Color(0xFF64748B))
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Preview 2 (Floating Window)",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = if (isScreen2) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // SKETCHWARE SPLIT IDE WORKSPACE (Left Vertical Palette + Right Android Phone Frame)
            SketchwareStudioSplitWorkspace(
                project = project,
                components = components,
                selectedComponentId = uiState.selectedComponentId,
                isLivePreviewMode = uiState.isLivePreviewMode,
                statusToast = uiState.statusToast,
                activePreviewScreen = effectivePreviewScreen,
                isPreviewFullScreen = isPreviewFullScreen,
                onTogglePreviewFullScreen = { isPreviewFullScreen = !isPreviewFullScreen },
                onAddPaletteEntry = onAddPaletteEntry,
                onSelectComponent = onSelectComponent,
                onMoveComponent = onMoveComponent,
                onResizeComponent = onResizeComponent,
                onResizeCanvas = onResizeCanvas,
                onToggleAutoFixSize = onToggleAutoFixSize,
                onOpenEditFloatingPanel = onOpenEditFloatingPanel,
                onOpenChangeBackground = onOpenChangeBackground,
                onSaveDesign = { onSaveProjectDesign(selectedComponent) },
                onTriggerComponentLive = onTriggerComponentLive,
                onClearCanvas = onClearCanvas,
                savedCustomWidgets = uiState.savedCustomWidgets,
                onOpenCreateCustomWidget = onOpenCreateCustomWidget,
                onAddSavedCustomWidget = onAddSavedCustomWidget,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
