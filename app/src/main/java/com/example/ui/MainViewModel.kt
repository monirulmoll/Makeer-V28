package com.example.ui

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.blueprint.ApkCompilationEngine
import com.example.data.AppDatabase
import com.example.data.CanvasComponentEntity
import com.example.data.ComponentWidgetType
import com.example.data.ConfigAuditRepository
import com.example.data.ConfigWriteAuditEntity
import com.example.data.StudioProjectEntity
import com.example.engine.AiBuildStepStatus
import com.example.engine.AiChatTurn
import com.example.engine.ConfigParameterSpec
import com.example.engine.GgufBlueprintEngine
import com.example.engine.GgufModelState
import com.example.engine.LocalConfigStateWriter
import com.example.engine.ShizukuPrivilegeBridge
import com.example.engine.SoundTriggerPlayer
import com.example.engine.StudioGenerationMode
import com.example.engine.TermuxServerClient
import com.example.engine.TermuxServerConfig
import com.example.service.DynamicOverlayRegistry
import com.example.service.FloatingDashboardService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

enum class StudioDestination {
    WELCOME_SCREEN,
    PROJECT_LAUNCHER,
    CANVAS_WORKSPACE,
    AI_GGUF_GATE,
    AI_STUDIO_WORKSPACE,
    COMPILED_STANDALONE_APP
}

data class ComponentCountSummary(
    val totalCount: Int = 0,
    val buttonCount: Int = 0,
    val toggleCount: Int = 0,
    val sliderCount: Int = 0,
    val textCount: Int = 0,
    val inputCount: Int = 0,
    val imageCount: Int = 0
)

data class StudioUiState(
    val destination: StudioDestination = StudioDestination.WELCOME_SCREEN,
    val isBundledStandaloneApk: Boolean = false,
    val activeProject: StudioProjectEntity? = null,
    val selectedComponentId: Long? = null,
    val isLivePreviewMode: Boolean = false,
    val isSystemOverlayRunning: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasStoragePermission: Boolean = false,
    val showExistingProjectsPicker: Boolean = false,
    val showCreateProjectDialog: Boolean = false,
    val editingProject: StudioProjectEntity? = null,
    val showEditFloatingPanelDialog: Boolean = false,
    val showChangeBackgroundDialog: Boolean = false,
    val showEditCodeDialog: Boolean = false,
    val customEditedKotlinFiles: Map<String, String> = emptyMap(),
    val isBuildingApk: Boolean = false,
    val buildProgressStepText: String = "",
    val rawJavaBuildPreview: String = "",
    val compiledApkFilePath: String? = null,
    val compiledAppPackageName: String = "",
    val compiledAppName: String = "",
    val downloadedFileSummary: String? = null,
    val downloadedFileName: String = "floating_window.apk",
    val ggufModelState: GgufModelState = GgufModelState(),
    val termuxServerConfig: TermuxServerConfig = TermuxServerConfig(),
    val isGeneratingAiBlueprint: Boolean = false,
    val aiLiveBuildSteps: List<AiBuildStepStatus> = emptyList(),
    val aiChatHistory: List<AiChatTurn> = emptyList(),
    val aiBuiltProject: StudioProjectEntity? = null,
    val aiBuiltComponents: List<CanvasComponentEntity> = emptyList(),
    val aiBuiltIsFloatingOverlay: Boolean = false,
    val aiBuiltAppCategory: String = "STANDALONE_ANDROID_APP",
    val aiBuiltFileArtifacts: List<com.example.engine.GeneratedFileArtifact> = emptyList(),
    val isAiFloatingOverlayRunning: Boolean = false,
    val aiCompiledApkFilePath: String? = null,
    val aiCompiledPublicApkPath: String? = null,
    val aiCompiledApkSummary: String? = null,
    val shizukuStatusSummary: String = "Not Connected",
    val isShizukuReady: Boolean = false,
    val isShizukuRunning: Boolean = false,
    val isShizukuInstalled: Boolean = false,
    val activeWriteErrorReport: LocalConfigStateWriter.WriteDiagnosticReport? = null,
    val statusToast: String = "Welcome to Studio Error — Choose Offline Mode or Online (AI) Mode."
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val onboardingPrefs = appContext.getSharedPreferences("studio_error_onboarding_prefs", Context.MODE_PRIVATE)
    private val db = AppDatabase.getInstance(appContext)
    private val studioDao = db.studioDao()
    private val auditRepo = ConfigAuditRepository(db.configAuditDao())
    private val stateWriter = LocalConfigStateWriter.getInstance()

    private fun isWelcomeAlreadySeen(): Boolean {
        return onboardingPrefs.getBoolean("has_completed_welcome_onboarding", false)
    }

    private fun markWelcomeSeen() {
        onboardingPrefs.edit().putBoolean("has_completed_welcome_onboarding", true).apply()
    }

    private val _uiState = MutableStateFlow(
        StudioUiState(
            destination = if (onboardingPrefs.getBoolean("has_completed_welcome_onboarding", false)) {
                StudioDestination.PROJECT_LAUNCHER
            } else {
                StudioDestination.WELCOME_SCREEN
            },
            hasOverlayPermission = Settings.canDrawOverlays(appContext),
            hasStoragePermission = LocalConfigStateWriter.hasStoragePermissionGranted(appContext),
            shizukuStatusSummary = ShizukuPrivilegeBridge.getStatusSummary(appContext),
            isShizukuReady = ShizukuPrivilegeBridge.isShizukuReady(appContext),
            isShizukuRunning = ShizukuPrivilegeBridge.isShizukuRunning(appContext),
            isShizukuInstalled = ShizukuPrivilegeBridge.isShizukuInstalled(appContext),
            ggufModelState = GgufBlueprintEngine.loadOrFallbackToSample(
                appContext,
                null,
                StudioGenerationMode.OFFLINE_MANUAL
            ),
            termuxServerConfig = TermuxServerClient.loadConfig(appContext)
        )
    )
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private val activeProjectIdFlow = MutableStateFlow<Long?>(null)

    val allProjects: StateFlow<List<StudioProjectEntity>> = studioDao.observeAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeComponents: StateFlow<List<CanvasComponentEntity>> = activeProjectIdFlow
        .flatMapLatest { projectId ->
            if (projectId == null) flowOf(emptyList())
            else studioDao.observeComponentsForProject(projectId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentAuditLogs: StateFlow<List<ConfigWriteAuditEntity>> = auditRepo.recentAudits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val writeListener = object : LocalConfigStateWriter.OnStateWriteListener {
        override fun onWriteSuccess(
            parameterKey: String,
            byteOffset: Int,
            previousValue: String,
            newValue: String,
            durationMicros: Long,
            updatedSnapshot: ConfigParameterSpec.StateSnapshot
        ) {
            val offsetHex = String.format(Locale.US, "0x%02X", byteOffset)
            val fileName = File(updatedSnapshot.targetFilePath).name
            val stateLabel = when {
                newValue.equals("0x00", ignoreCase = true) || newValue == "0" || newValue.equals("false", ignoreCase = true) -> "OFF (0)"
                newValue.equals("0x01", ignoreCase = true) || newValue == "1" || newValue.equals("true", ignoreCase = true) -> "ON (1)"
                else -> newValue
            }
            _uiState.update {
                it.copy(
                    activeWriteErrorReport = null,
                    shizukuStatusSummary = ShizukuPrivilegeBridge.getStatusSummary(appContext),
                    isShizukuReady = ShizukuPrivilegeBridge.isShizukuReady(appContext),
                    isShizukuRunning = ShizukuPrivilegeBridge.isShizukuRunning(appContext),
                    isShizukuInstalled = ShizukuPrivilegeBridge.isShizukuInstalled(appContext),
                    statusToast = "$parameterKey → $stateLabel [Wrote $newValue @$offsetHex → $fileName]"
                )
            }
            viewModelScope.launch {
                auditRepo.recordWrite(
                    ConfigWriteAuditEntity(
                        parameterKey = parameterKey,
                        byteOffsetHex = offsetHex,
                        previousValue = previousValue,
                        newValue = newValue,
                        durationMicros = durationMicros,
                        crc32Hex = String.format(Locale.US, "0x%08X", updatedSnapshot.crc32Value),
                        targetFilePath = updatedSnapshot.targetFilePath
                    )
                )
            }
        }

        override fun onWriteError(parameterKey: String, errorMessage: String) {
            _uiState.update {
                it.copy(statusToast = "Write Error ($parameterKey): $errorMessage")
            }
        }

        override fun onWriteDiagnosticError(report: LocalConfigStateWriter.WriteDiagnosticReport) {
            _uiState.update {
                it.copy(
                    activeWriteErrorReport = report,
                    shizukuStatusSummary = ShizukuPrivilegeBridge.getStatusSummary(appContext),
                    isShizukuReady = ShizukuPrivilegeBridge.isShizukuReady(appContext),
                    isShizukuRunning = ShizukuPrivilegeBridge.isShizukuRunning(appContext),
                    isShizukuInstalled = ShizukuPrivilegeBridge.isShizukuInstalled(appContext),
                    statusToast = "⚠️ ${report.whyFailedTitle}: ${report.targetFilePath}"
                )
            }
        }
    }

    private val shizukuListener = ShizukuPrivilegeBridge.OnShizukuStateChangeListener { _, _, _ ->
        _uiState.update {
            it.copy(
                shizukuStatusSummary = ShizukuPrivilegeBridge.getStatusSummary(appContext),
                isShizukuReady = ShizukuPrivilegeBridge.isShizukuReady(appContext),
                isShizukuRunning = ShizukuPrivilegeBridge.isShizukuRunning(appContext),
                isShizukuInstalled = ShizukuPrivilegeBridge.isShizukuInstalled(appContext)
            )
        }
    }

    private val _bundledStandaloneComponents = MutableStateFlow<List<CanvasComponentEntity>>(emptyList())
    val bundledStandaloneComponents: StateFlow<List<CanvasComponentEntity>> = _bundledStandaloneComponents.asStateFlow()

    init {
        stateWriter.bindAppContext(appContext)
        stateWriter.addListener(writeListener)
        ShizukuPrivilegeBridge.addListener(shizukuListener)
        ShizukuPrivilegeBridge.probeShizukuBinder(appContext)
        if (DynamicOverlayRegistry.isBundledStandaloneApk(appContext)) {
            DynamicOverlayRegistry.loadFromBundledAssetsIfEmpty(appContext)
            val projName = DynamicOverlayRegistry.getActiveProjectName()
            val pkgName = DynamicOverlayRegistry.getActivePackageName()
            val standaloneProject = StudioProjectEntity(
                id = 1L,
                name = projName,
                packageName = pkgName,
                overlayTitle = DynamicOverlayRegistry.getActiveOverlayTitle(),
                appLogoPath = DynamicOverlayRegistry.getActiveAppLogoPath(),
                floatingLogoPath = DynamicOverlayRegistry.getActiveFloatingLogoPath(),
                canvasWidthDp = DynamicOverlayRegistry.getActiveCanvasWidthDp(),
                canvasHeightDp = DynamicOverlayRegistry.getActiveCanvasHeightDp(),
                canvasBgColorHex = DynamicOverlayRegistry.getActiveCanvasBgHex(),
                canvasBgImagePath = DynamicOverlayRegistry.getActiveCanvasBgImagePath(),
                autoFixSize = DynamicOverlayRegistry.isActiveAutoFixSize(),
                defaultTargetFilePath = getDefaultTargetFilePath(projName)
            )
            val standaloneItems = DynamicOverlayRegistry.getActiveItems().map { spec ->
                CanvasComponentEntity(
                    id = spec.id,
                    projectId = 1L,
                    type = spec.type ?: ComponentWidgetType.BUTTON.name,
                    label = spec.label ?: "Widget",
                    posXDp = spec.posXDp,
                    posYDp = spec.posYDp,
                    widthDp = spec.widthDp,
                    heightDp = spec.heightDp,
                    bgColorHex = spec.bgColorHex ?: "#FFFFFF",
                    textColorHex = spec.textColorHex ?: "#0F172A",
                    bgImagePath = spec.bgImagePath ?: "",
                    customImagePath = spec.customImagePath ?: "",
                    soundTrigger = spec.soundTrigger ?: "NONE",
                    customSoundPath = spec.customSoundPath ?: "",
                    offSoundTrigger = spec.offSoundTrigger ?: "NONE",
                    offCustomSoundPath = spec.offCustomSoundPath ?: "",
                    targetFilePath = spec.targetFilePath ?: "",
                    byteOffsetHex = spec.byteOffsetHex ?: "0x04",
                    onPayloadHex = spec.onPayloadHex ?: "0x01",
                    offPayloadHex = spec.offPayloadHex ?: "0x00",
                    sliderMax = spec.sliderMax,
                    currentValue = spec.currentValue ?: "0",
                    linkUrl = spec.linkUrl ?: ""
                )
            }
            _bundledStandaloneComponents.value = standaloneItems
            _uiState.update {
                it.copy(
                    destination = StudioDestination.COMPILED_STANDALONE_APP,
                    isBundledStandaloneApk = true,
                    activeProject = standaloneProject,
                    compiledAppPackageName = pkgName,
                    compiledAppName = projName,
                    statusToast = "Running compiled app '$projName' (${standaloneItems.size} widgets)"
                )
            }
        } else if (!isWelcomeAlreadySeen()) {
            // Mark first-time welcome shown so subsequent app launches go directly to App Studio Home
            markWelcomeSeen()
        }
    }

    override fun onCleared() {
        stateWriter.removeListener(writeListener)
        ShizukuPrivilegeBridge.removeListener(shizukuListener)
        super.onCleared()
    }

    fun getDefaultTargetFilePath(projectName: String): String {
        val safeSlug = projectName.trim().lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifEmpty { "custom_overlay" }
        return File(appContext.filesDir, "${safeSlug}_state.bin").absolutePath
    }

    fun openCreateProjectDialog(show: Boolean) {
        _uiState.update { it.copy(showCreateProjectDialog = show) }
    }

    fun openExistingProjectsPicker(show: Boolean) {
        _uiState.update { it.copy(showExistingProjectsPicker = show) }
    }

    fun openEditProjectDialog(project: StudioProjectEntity?) {
        _uiState.update { it.copy(editingProject = project) }
    }

    fun openEditFloatingPanelDialog(show: Boolean) {
        _uiState.update { it.copy(showEditFloatingPanelDialog = show) }
    }

    fun openChangeBackgroundDialog(show: Boolean) {
        _uiState.update { it.copy(showChangeBackgroundDialog = show) }
    }

    /**
     * Updates the active project's Floating Window Background (solid/glass hex color and/or custom background image)
     * in Room and live-syncs the overlay registry and running floating window service.
     */
    fun updateFloatingWindowBackground(
        newBgColorHex: String,
        newBgImagePath: String = _uiState.value.activeProject?.canvasBgImagePath.orEmpty()
    ) {
        val currentProject = _uiState.value.activeProject ?: return
        val rawHex = newBgColorHex.trim().ifEmpty { "#FFFFFF" }
        val formattedHex = if (rawHex.startsWith("#")) rawHex.uppercase(Locale.US) else "#${rawHex.uppercase(Locale.US)}"
        val safeHex = try {
            android.graphics.Color.parseColor(formattedHex)
            formattedHex
        } catch (_: Exception) {
            currentProject.canvasBgColorHex.ifBlank { "#FFFFFF" }
        }
        val cleanBgImage = newBgImagePath.trim()

        viewModelScope.launch {
            val updated = currentProject.copy(
                canvasBgColorHex = safeHex,
                canvasBgImagePath = cleanBgImage,
                updatedAt = System.currentTimeMillis()
            )
            studioDao.updateProject(updated)
            _uiState.update { state ->
                state.copy(
                    activeProject = updated,
                    customEditedKotlinFiles = emptyMap(),
                    statusToast = if (cleanBgImage.isNotEmpty()) {
                        "Updated Floating Window Background Image & Color ($safeHex)."
                    } else {
                        "Updated Floating Window Background ($safeHex)."
                    }
                )
            }
            DynamicOverlayRegistry.updateActiveOverlay(
                updated.overlayTitle.ifBlank { updated.name },
                updated.floatingLogoPath.ifBlank { updated.appLogoPath },
                updated.canvasWidthDp,
                updated.canvasHeightDp,
                updated.canvasBgColorHex,
                updated.canvasBgImagePath,
                updated.autoFixSize,
                DynamicOverlayRegistry.getActiveItems()
            )
            syncOverlayRegistryInBackground(updated)
            if (_uiState.value.isSystemOverlayRunning && Settings.canDrawOverlays(appContext)) {
                val refreshIntent = Intent(appContext, FloatingDashboardService::class.java).apply {
                    action = FloatingDashboardService.ACTION_START_OVERLAY
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(refreshIntent)
                } else {
                    appContext.startService(refreshIntent)
                }
            }
        }
    }

    /**
     * Copies a user-picked Floating Window Background image from the Android Photo Picker into local app storage,
     * center-cropped to the active Floating Window dimensions so it fits the exact floating window size,
     * and invokes [onResult] with its absolute file path.
     */
    fun importFloatingBackgroundUri(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val activeProj = _uiState.value.activeProject
                val density = appContext.resources.displayMetrics.density.coerceAtLeast(2f)
                val targetWidthDp = (activeProj?.canvasWidthDp ?: 216).coerceIn(180, 340)
                val targetHeightDp = (activeProj?.canvasHeightDp ?: 290).coerceIn(160, 480)
                val targetWidthPx = (targetWidthDp * density).toInt().coerceAtLeast(360)
                val targetHeightPx = (targetHeightDp * density).toInt().coerceAtLeast(320)

                val destPath = withContext(Dispatchers.IO) {
                    val bgDir = File(appContext.filesDir, "project_backgrounds").apply { mkdirs() }
                    val destFile = File(bgDir, "canvas_bg_${System.currentTimeMillis()}.png")
                    val rawBytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    val decoded = if (rawBytes != null && rawBytes.isNotEmpty()) {
                        android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                    } else {
                        null
                    }
                    if (decoded != null && decoded.width > 0 && decoded.height > 0) {
                        val scale = maxOf(
                            targetWidthPx.toFloat() / decoded.width.toFloat(),
                            targetHeightPx.toFloat() / decoded.height.toFloat()
                        )
                        val scaledW = maxOf(targetWidthPx, Math.round(decoded.width * scale))
                        val scaledH = maxOf(targetHeightPx, Math.round(decoded.height * scale))
                        val scaled = android.graphics.Bitmap.createScaledBitmap(decoded, scaledW, scaledH, true)
                        val cropX = maxOf(0, (scaledW - targetWidthPx) / 2)
                        val cropY = maxOf(0, (scaledH - targetHeightPx) / 2)
                        val cropped = android.graphics.Bitmap.createBitmap(
                            scaled,
                            cropX,
                            cropY,
                            targetWidthPx,
                            targetHeightPx
                        )
                        FileOutputStream(destFile).use { output ->
                            cropped.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
                        }
                    } else if (rawBytes != null) {
                        FileOutputStream(destFile).use { output ->
                            output.write(rawBytes)
                        }
                    }
                    destFile.absolutePath
                }
                onResult(destPath)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(statusToast = "Could not load background image: ${e.message}")
                }
            }
        }
    }

    /**
     * Updates the active project's Floating Panel Name (overlayTitle) and Floating Goal Logo (floatingLogoPath)
     * directly from the Studio Workspace editor.
     */
    fun updateFloatingPanelNameAndLogo(
        newOverlayTitle: String,
        newFloatingLogoPath: String
    ) {
        val currentProject = _uiState.value.activeProject ?: return
        val cleanTitle = newOverlayTitle.trim()
        val cleanLogo = newFloatingLogoPath.trim()
        viewModelScope.launch {
            val updated = currentProject.copy(
                overlayTitle = cleanTitle,
                floatingLogoPath = cleanLogo,
                appLogoPath = cleanLogo,
                updatedAt = System.currentTimeMillis()
            )
            studioDao.updateProject(updated)
            _uiState.update { state ->
                state.copy(
                    activeProject = updated,
                    customEditedKotlinFiles = emptyMap(),
                    statusToast = "Saved Floating Window Name & Image."
                )
            }
            DynamicOverlayRegistry.updateActiveOverlay(
                updated.overlayTitle.ifBlank { updated.name },
                updated.floatingLogoPath.ifBlank { updated.appLogoPath },
                updated.canvasWidthDp,
                updated.canvasHeightDp,
                updated.canvasBgColorHex,
                updated.canvasBgImagePath,
                updated.autoFixSize,
                DynamicOverlayRegistry.getActiveItems()
            )
            syncOverlayRegistryInBackground(updated)
            if (_uiState.value.isSystemOverlayRunning && Settings.canDrawOverlays(appContext)) {
                val refreshIntent = Intent(appContext, FloatingDashboardService::class.java).apply {
                    action = FloatingDashboardService.ACTION_START_OVERLAY
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(refreshIntent)
                } else {
                    appContext.startService(refreshIntent)
                }
            }
        }
    }

    /**
     * Copies a user-picked App Logo image from the Android Photo Picker into local app storage
     * and invokes [onResult] with its absolute file path.
     */
    fun importProjectLogoUri(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val destPath = withContext(Dispatchers.IO) {
                    val logoDir = File(appContext.filesDir, "project_logos").apply { mkdirs() }
                    val destFile = File(logoDir, "app_logo_${System.currentTimeMillis()}.png")
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    destFile.absolutePath
                }
                onResult(destPath)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(statusToast = "Could not load selected logo: ${e.message}")
                }
            }
        }
    }

    /**
     * Updates a saved project's complete Android Studio / Sketchware configuration
     * (App Name, Package ID, Project Name, App Logo, Version Code, Version Name, Min/Target SDK, Window Title)
     * from the Home Screen Pencil Icon dialog.
     */
    fun updateProjectNameAndLogo(
        project: StudioProjectEntity,
        newName: String,
        newPackageName: String,
        newProjectName: String,
        newOverlayTitle: String,
        newLogoPath: String,
        newVersionCode: Int = project.versionCode,
        newVersionName: String = project.versionName,
        newMinSdk: Int = project.minSdk,
        newTargetSdk: Int = project.targetSdk,
        newFloatingLogoPath: String = project.floatingLogoPath
    ) {
        val cleanName = newName.trim()
        val cleanPkg = newPackageName.trim().lowercase(Locale.US)
        val cleanProjName = newProjectName.trim()
        val cleanTitle = newOverlayTitle.trim()
        val cleanLogo = newLogoPath.trim()
        val cleanFloatLogo = newFloatingLogoPath.trim()
        val safeMinSdk = newMinSdk.coerceIn(21, 36)
        val safeTargetSdk = newTargetSdk.coerceIn(safeMinSdk, 36)
        val safeVerCode = newVersionCode.coerceAtLeast(1)
        val safeVerName = newVersionName.trim().ifEmpty { "1.0" }

        viewModelScope.launch {
            val updated = project.copy(
                name = cleanName,
                packageName = cleanPkg,
                projectName = cleanProjName,
                overlayTitle = cleanTitle,
                appLogoPath = cleanLogo,
                floatingLogoPath = cleanFloatLogo,
                versionCode = safeVerCode,
                versionName = safeVerName,
                minSdk = safeMinSdk,
                targetSdk = safeTargetSdk,
                updatedAt = System.currentTimeMillis()
            )
            studioDao.updateProject(updated)
            _uiState.update { state ->
                state.copy(
                    editingProject = null,
                    activeProject = if (state.activeProject?.id == updated.id) updated else state.activeProject,
                    statusToast = "Saved App Config (Name, Package ID, Logo, Version & SDK)."
                )
            }
        }
    }

    /**
     * Creates a new project with NO pre-filled App Name, NO pre-filled Logo, and a 100% empty background,
     * while supporting full Android Studio & Sketchware configuration (Package ID, Project Name, Version Code/Name, SDK).
     */
    fun createNewBlankProject(
        name: String,
        packageName: String,
        projectName: String,
        overlayTitle: String,
        canvasWidthDp: Int,
        canvasHeightDp: Int,
        targetFilePath: String,
        appLogoPath: String = "",
        versionCode: Int = 1,
        versionName: String = "1.0",
        minSdk: Int = 24,
        targetSdk: Int = 36
    ) {
        val cleanName = name.trim()
        val cleanPkg = packageName.trim().lowercase(Locale.US)
        val cleanProjName = projectName.trim()
        val cleanTitle = overlayTitle.trim()
        val cleanLogo = appLogoPath.trim()
        val safeMinSdk = minSdk.coerceIn(21, 36)
        val safeTargetSdk = targetSdk.coerceIn(safeMinSdk, 36)
        val safeVerCode = versionCode.coerceAtLeast(1)
        val safeVerName = versionName.trim().ifEmpty { "1.0" }
        val resolvedTarget = targetFilePath.trim().ifEmpty {
            getDefaultTargetFilePath(cleanName.ifEmpty { cleanProjName.ifEmpty { cleanPkg } })
        }

        viewModelScope.launch {
            val newProject = StudioProjectEntity(
                name = cleanName,
                packageName = cleanPkg,
                projectName = cleanProjName,
                overlayTitle = cleanTitle,
                appLogoPath = cleanLogo,
                floatingLogoPath = cleanLogo,
                versionCode = safeVerCode,
                versionName = safeVerName,
                minSdk = safeMinSdk,
                targetSdk = safeTargetSdk,
                canvasWidthDp = canvasWidthDp.coerceIn(180, 420),
                canvasHeightDp = canvasHeightDp.coerceIn(180, 560),
                canvasBgColorHex = "#FFFFFF",
                defaultTargetFilePath = resolvedTarget
            )
            val newId = studioDao.insertProject(newProject)
            val inserted = studioDao.getProjectById(newId) ?: newProject.copy(id = newId)
            activeProjectIdFlow.value = newId
            _uiState.update {
                it.copy(
                    destination = StudioDestination.CANVAS_WORKSPACE,
                    activeProject = inserted,
                    selectedComponentId = null,
                    isLivePreviewMode = false,
                    showCreateProjectDialog = false,
                    showExistingProjectsPicker = false,
                    statusToast = "100% Empty Workspace ready."
                )
            }
        }
    }

    /**
     * Requirement 1: Select Existing Project and load its saved canvas workspace.
     */
    fun openExistingProject(project: StudioProjectEntity) {
        activeProjectIdFlow.value = project.id
        _uiState.update {
            it.copy(
                destination = StudioDestination.CANVAS_WORKSPACE,
                activeProject = project,
                selectedComponentId = null,
                isLivePreviewMode = false,
                showExistingProjectsPicker = false,
                statusToast = "Loaded project '${project.name}'."
            )
        }
    }

    fun duplicateProject(project: StudioProjectEntity) {
        viewModelScope.launch {
            val copyName = "${project.name} Copy"
            val copySlug = copyName.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "").ifEmpty { "copy" }
            val duplicatedProject = project.copy(
                id = 0L,
                name = copyName,
                packageName = "${project.packageName}.$copySlug",
                projectName = copyName,
                overlayTitle = project.overlayTitle.ifBlank { copyName },
                updatedAt = System.currentTimeMillis()
            )
            val newProjectId = studioDao.insertProject(duplicatedProject)
            val originalComponents = studioDao.getComponentsForProjectSync(project.id)
            for (comp in originalComponents) {
                studioDao.insertComponent(
                    comp.copy(
                        id = 0L,
                        projectId = newProjectId
                    )
                )
            }
            _uiState.update {
                it.copy(statusToast = "Duplicated project '${project.name}' → '$copyName'.")
            }
        }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            studioDao.deleteAllComponentsForProject(projectId)
            studioDao.deleteProjectById(projectId)
            if (activeProjectIdFlow.value == projectId) {
                activeProjectIdFlow.value = null
                _uiState.update {
                    it.copy(
                        destination = StudioDestination.PROJECT_LAUNCHER,
                        activeProject = null,
                        selectedComponentId = null
                    )
                }
            }
        }
    }

    fun navigateBackToLauncher() {
        stopSystemFloatingOverlay()
        _uiState.update {
            it.copy(
                destination = StudioDestination.PROJECT_LAUNCHER,
                selectedComponentId = null,
                isLivePreviewMode = false
            )
        }
    }

    /**
     * Manually adds a single user-chosen widget to the canvas and selects it immediately
     * so the bottom Property Inspector Dock opens for customization.
     */
    fun addComponentToCanvas(widgetType: ComponentWidgetType) {
        addPaletteItemToCanvas(
            widgetType = widgetType,
            customPrefix = widgetType.displayName,
            customWidthDp = null,
            customHeightDp = null,
            customBgHex = null,
            customTextHex = null
        )
    }

    /**
     * Adds a specific Sketchware palette item (Layouts, AndroidX, or Widgets) onto the blank canvas.
     */
    fun addPaletteItemToCanvas(
        widgetType: ComponentWidgetType,
        customPrefix: String,
        customWidthDp: Int? = null,
        customHeightDp: Int? = null,
        customBgHex: String? = null,
        customTextHex: String? = null
    ) {
        val project = _uiState.value.activeProject ?: return
        val existingComponents = activeComponents.value
        val existingCount = existingComponents.size
        val staggerX = (10 + (existingCount * 6) % 24).coerceAtMost((project.canvasWidthDp - 180).coerceAtLeast(8))
        val staggerY = if (existingComponents.isEmpty()) {
            10
        } else {
            existingComponents.maxOf { it.posYDp + it.heightDp } + 8
        }
        val defaultOffset = String.format(Locale.US, "0x%02X", 4 + (existingCount * 4))

        val (defaultW, defaultH, defaultBg) = when (widgetType) {
            ComponentWidgetType.BUTTON -> Triple(176, 44, "#334155")
            ComponentWidgetType.TOGGLE -> Triple(196, 44, "#FFFFFF")
            ComponentWidgetType.SLIDER -> Triple(196, 54, "#FFFFFF")
            ComponentWidgetType.TEXT -> Triple(150, 34, "#EEF2FF")
            ComponentWidgetType.INPUT -> Triple(186, 42, "#FFFFFF")
            ComponentWidgetType.IMAGE -> Triple(64, 64, "#1E293B")
            ComponentWidgetType.LINK -> Triple(190, 42, "#0F172A")
        }

        val resolvedTextHex = customTextHex ?: when (widgetType) {
            ComponentWidgetType.TOGGLE,
            ComponentWidgetType.SLIDER,
            ComponentWidgetType.INPUT -> "#0F172A"
            ComponentWidgetType.TEXT -> "#1E293B"
            ComponentWidgetType.LINK -> "#38BDF8"
            else -> "#FFFFFF"
        }

        val defaultLabel = "$customPrefix #${existingCount + 1}"

        viewModelScope.launch {
            val entity = CanvasComponentEntity(
                projectId = project.id,
                type = widgetType.name,
                label = defaultLabel,
                posXDp = staggerX,
                posYDp = staggerY,
                widthDp = customWidthDp ?: defaultW,
                heightDp = customHeightDp ?: defaultH,
                bgColorHex = customBgHex ?: defaultBg,
                textColorHex = resolvedTextHex,
                customImagePath = "",
                soundTrigger = SoundTriggerPlayer.SOUND_CLICK,
                customSoundPath = "",
                offSoundTrigger = SoundTriggerPlayer.SOUND_POP,
                offCustomSoundPath = "",
                targetFilePath = project.defaultTargetFilePath.ifEmpty { getDefaultTargetFilePath(project.name) },
                byteOffsetHex = defaultOffset,
                onPayloadHex = "On",
                offPayloadHex = "Off",
                sliderMax = 100,
                currentValue = if (widgetType == ComponentWidgetType.SLIDER) "50" else "0",
                linkUrl = if (widgetType == ComponentWidgetType.LINK) "https://google.com" else ""
            )
            val newId = studioDao.insertComponent(entity)
            if (project.autoFixSize) {
                relayoutComponentsForAutoFix(project.id, project.canvasWidthDp)
            }
            studioDao.updateProject(project.copy(updatedAt = System.currentTimeMillis()))
            _uiState.update {
                it.copy(
                    selectedComponentId = newId,
                    customEditedKotlinFiles = emptyMap(),
                    statusToast = if (project.autoFixSize)
                        "Added '$defaultLabel' (Auto-fitted & auto-saved)."
                    else
                        "Added '$defaultLabel' — Customize below (auto-saves in real time)."
                )
            }
        }
    }

    /**
     * Toggles Auto Fix Size ON or OFF.
     * When ON:
     * 1. Sets the Floating Window to screen-perfection proportions (260dp x 320dp).
     * 2. Stretches every widget horizontally to cover left-to-right with a clean 10dp side gap.
     * 3. Stacks widgets vertically one below another with an 8dp gap and enables vertical scrolling.
     * 4. Hides and disables all manual edge/corner crop handles.
     * When OFF:
     * Restores manual crop handles and free drag/resize on the Floating Window and widgets.
     */
    fun toggleAutoFixSize() {
        val project = _uiState.value.activeProject ?: return
        val nextAutoFix = !project.autoFixSize
        val updatedProject = if (nextAutoFix) {
            project.copy(
                autoFixSize = true,
                canvasWidthDp = 216,
                canvasHeightDp = 290,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            project.copy(
                autoFixSize = false,
                updatedAt = System.currentTimeMillis()
            )
        }

        _uiState.update {
            it.copy(
                activeProject = updatedProject,
                customEditedKotlinFiles = emptyMap(),
                statusToast = if (nextAutoFix)
                    "Auto Fix Size ON: Full-width stacked widgets + scroll active."
                else
                    "Auto Fix Size OFF: Manual crop handles & free resize restored."
            )
        }

        viewModelScope.launch {
            studioDao.updateProject(updatedProject)
            if (nextAutoFix) {
                relayoutComponentsForAutoFix(updatedProject.id, updatedProject.canvasWidthDp)
            }
        }
    }

    private suspend fun relayoutComponentsForAutoFix(projectId: Long, canvasWidthDp: Int) {
        val list = studioDao.getComponentsForProjectSync(projectId)
        val sideGapDp = 8
        val verticalGapDp = 6
        val fullItemWidthDp = (canvasWidthDp - (sideGapDp * 2)).coerceAtLeast(100)
        var currentYDp = 8

        for (comp in list) {
            val cleanHeightDp = when (comp.type) {
                ComponentWidgetType.SLIDER.name -> 54
                ComponentWidgetType.IMAGE.name -> 58
                ComponentWidgetType.TEXT.name -> 36
                else -> 42
            }
            val updatedComp = comp.copy(
                posXDp = sideGapDp,
                posYDp = currentYDp,
                widthDp = fullItemWidthDp,
                heightDp = cleanHeightDp
            )
            studioDao.updateComponent(updatedComp)
            currentYDp += cleanHeightDp + verticalGapDp
        }
    }

    /**
     * Resizes the Floating Mod Menu window body (width & height in dp) like an image crop box
     * when the user drags any corner or edge endpoint handle (disabled when Auto Fix Size is ON).
     */
    fun resizeActiveProjectCanvas(deltaWidthDp: Int, deltaHeightDp: Int) {
        val project = _uiState.value.activeProject ?: return
        if (project.autoFixSize) return
        val newW = (project.canvasWidthDp + deltaWidthDp).coerceIn(170, 420)
        val newH = (project.canvasHeightDp + deltaHeightDp).coerceIn(160, 620)
        if (newW == project.canvasWidthDp && newH == project.canvasHeightDp) return
        val updatedProject = project.copy(
            canvasWidthDp = newW,
            canvasHeightDp = newH,
            updatedAt = System.currentTimeMillis()
        )
        _uiState.update {
            it.copy(
                activeProject = updatedProject,
                customEditedKotlinFiles = emptyMap(),
                statusToast = "Floating Mod Menu Size: ${newW}dp × ${newH}dp"
            )
        }
        viewModelScope.launch {
            studioDao.updateProject(updatedProject)
        }
    }

    /**
     * Requirement 3: Interactive touch selection listener on canvas elements.
     * Selecting an element opens the Bottom Property Editing Dock.
     */
    fun selectComponent(componentId: Long?) {
        _uiState.update {
            it.copy(
                selectedComponentId = componentId,
                isLivePreviewMode = false
            )
        }
    }

    private suspend fun syncOverlayRegistryInBackground(projectOverride: StudioProjectEntity? = null) {
        val project = projectOverride ?: _uiState.value.activeProject ?: return
        val list = if (_uiState.value.isBundledStandaloneApk) {
            _bundledStandaloneComponents.value
        } else {
            studioDao.getComponentsForProjectSync(project.id)
        }
        val specs = list.map { comp ->
            DynamicOverlayRegistry.OverlayItemSpec().apply {
                id = comp.id
                type = comp.type
                label = comp.label
                posXDp = comp.posXDp
                posYDp = comp.posYDp
                widthDp = comp.widthDp
                heightDp = comp.heightDp
                bgColorHex = comp.bgColorHex
                textColorHex = comp.textColorHex
                bgImagePath = comp.bgImagePath
                customImagePath = comp.customImagePath
                soundTrigger = comp.soundTrigger
                customSoundPath = comp.customSoundPath
                offSoundTrigger = comp.offSoundTrigger
                offCustomSoundPath = comp.offCustomSoundPath
                targetFilePath = comp.targetFilePath
                byteOffsetHex = comp.byteOffsetHex
                onPayloadHex = comp.onPayloadHex
                offPayloadHex = comp.offPayloadHex
                sliderMax = comp.sliderMax
                currentValue = comp.currentValue
                linkUrl = comp.linkUrl
            }
        }
        DynamicOverlayRegistry.updateActiveOverlay(
            project.overlayTitle.ifBlank { project.name },
            project.floatingLogoPath.ifBlank { project.appLogoPath },
            project.canvasWidthDp,
            project.canvasHeightDp,
            project.canvasBgColorHex,
            project.canvasBgImagePath,
            project.autoFixSize,
            specs
        )
    }

    fun updateComponent(updated: CanvasComponentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            studioDao.updateComponent(updated)
            syncOverlayRegistryInBackground()
        }
    }

    /**
     * Explicitly saves the current visual design (project dimensions, panel name/logo, and all widgets)
     * to Room Database and syncs the build blueprint so Building the APK always uses this exact design.
     */
    fun saveCurrentProjectDesign(editedComponent: CanvasComponentEntity? = null) {
        val currentProject = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            if (editedComponent != null) {
                studioDao.updateComponent(editedComponent)
            }
            val updatedProj = currentProject.copy(
                defaultTargetFilePath = editedComponent?.targetFilePath?.takeIf { it.isNotBlank() } ?: currentProject.defaultTargetFilePath,
                updatedAt = System.currentTimeMillis()
            )
            studioDao.updateProject(updatedProj)
            val currentList = studioDao.getComponentsForProjectSync(updatedProj.id)
            for (comp in currentList) {
                studioDao.updateComponent(comp)
            }
            syncOverlayRegistryInBackground(updatedProj)
            if (editedComponent != null && editedComponent.customImagePath.isNotBlank()) {
                val isCurrentlyActive = if (editedComponent.type == "SLIDER") {
                    (editedComponent.currentValue.toIntOrNull() ?: 0) > 0
                } else {
                    editedComponent.currentValue == "1" || editedComponent.currentValue.equals("true", ignoreCase = true)
                }
                withContext(Dispatchers.IO) {
                    stateWriter.applyWidgetPatchSync(
                        appContext.filesDir,
                        "widget_${editedComponent.id}",
                        editedComponent.type,
                        editedComponent.targetFilePath,
                        editedComponent.byteOffsetHex,
                        editedComponent.offPayloadHex,
                        editedComponent.onPayloadHex,
                        editedComponent.currentValue,
                        isCurrentlyActive,
                        editedComponent.label,
                        editedComponent.customImagePath
                    )
                }
            }
            _uiState.update {
                it.copy(
                    activeProject = updatedProj,
                    customEditedKotlinFiles = emptyMap(),
                    statusToast = "✅ Design Saved! (${currentList.size} widget(s) locked in for Build)"
                )
            }
        }
    }

    fun updateComponentPosition(component: CanvasComponentEntity, newXDp: Int, newYDp: Int) {
        val project = _uiState.value.activeProject
        if (project?.autoFixSize == true) return
        val maxW = project?.canvasWidthDp ?: 310
        val clampedX = newXDp.coerceIn(0, (maxW - 36).coerceAtLeast(0))
        val clampedY = newYDp.coerceIn(-300, 2500)
        updateComponent(component.copy(posXDp = clampedX, posYDp = clampedY))
    }

    /**
     * Resizes a widget on the canvas (widthDp & heightDp) like an image crop box
     * when the user drags its edge or corner crop handles (disabled when Auto Fix Size is ON).
     */
    fun resizeComponent(component: CanvasComponentEntity, deltaWidthDp: Int, deltaHeightDp: Int) {
        val project = _uiState.value.activeProject
        if (project?.autoFixSize == true) return
        val maxW = (project?.canvasWidthDp ?: 380).coerceAtLeast(100)
        val maxH = (project?.canvasHeightDp ?: 500).coerceAtLeast(80)
        val newW = (component.widthDp + deltaWidthDp).coerceIn(44, maxW)
        val newH = (component.heightDp + deltaHeightDp).coerceIn(28, maxH)
        if (newW == component.widthDp && newH == component.heightDp) return
        val updated = component.copy(widthDp = newW, heightDp = newH)
        updateComponent(updated)
        _uiState.update {
            it.copy(
                selectedComponentId = component.id,
                statusToast = "Widget '${component.label}' Crop Size: ${newW}dp × ${newH}dp"
            )
        }
    }

    fun duplicateSelectedComponent(component: CanvasComponentEntity) {
        val project = _uiState.value.activeProject
        viewModelScope.launch {
            val copy = component.copy(
                id = 0,
                label = "${component.label} Copy",
                posXDp = (component.posXDp + 16).coerceAtMost(240),
                posYDp = (component.posYDp + 16).coerceAtMost(320)
            )
            val newId = studioDao.insertComponent(copy)
            if (project?.autoFixSize == true) {
                relayoutComponentsForAutoFix(project.id, project.canvasWidthDp)
            }
            _uiState.update {
                it.copy(
                    selectedComponentId = newId,
                    customEditedKotlinFiles = emptyMap(),
                    statusToast = "Duplicated '${component.label}'."
                )
            }
        }
    }

    fun deleteComponent(componentId: Long) {
        val project = _uiState.value.activeProject
        viewModelScope.launch {
            studioDao.deleteComponentById(componentId)
            if (project?.autoFixSize == true) {
                relayoutComponentsForAutoFix(project.id, project.canvasWidthDp)
            }
            _uiState.update { state ->
                state.copy(
                    selectedComponentId = if (state.selectedComponentId == componentId) null else state.selectedComponentId,
                    customEditedKotlinFiles = emptyMap(),
                    statusToast = "Component removed from canvas."
                )
            }
        }
    }

    fun clearEntireCanvas() {
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            studioDao.deleteAllComponentsForProject(project.id)
            _uiState.update {
                it.copy(
                    selectedComponentId = null,
                    customEditedKotlinFiles = emptyMap(),
                    statusToast = "Canvas cleared to 100% Blank state."
                )
            }
        }
    }

    private fun resolvePickedUriDisplayName(uri: Uri, fallbackName: String): String {
        try {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    val displayName = cursor.getString(nameIndex)
                    if (!displayName.isNullOrBlank()) {
                        return displayName.trim()
                    }
                }
            }
        } catch (_: Exception) {
        }
        val rawSeg = uri.lastPathSegment ?: uri.path ?: fallbackName
        val afterColon = rawSeg.substringAfterLast(':')
        val afterSlash = afterColon.substringAfterLast('/')
        return afterSlash.trim().ifEmpty { fallbackName }
    }

    /**
     * Copies a user-picked file from the Android Document/File Picker into local app storage
     * with its original filename, updates the widget's label to the file's name, and immediately
     * configures/replaces or merges onto the target path when active.
     */
    fun assignPickedImageToComponent(component: CanvasComponentEntity, uri: Uri) {
        viewModelScope.launch {
            try {
                val pickedFileName = withContext(Dispatchers.IO) {
                    resolvePickedUriDisplayName(uri, "selected_file_${component.id}.bin")
                }
                val cleanFileName = pickedFileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank {
                    "selected_file_${component.id}.bin"
                }
                val compFileDir = File(appContext.filesDir, "component_files/comp_${component.id}").apply {
                    if (exists()) {
                        listFiles()?.forEach { it.delete() }
                    } else {
                        mkdirs()
                    }
                }
                val destFile = File(compFileDir, cleanFileName)
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                val activeVal = if (component.type == ComponentWidgetType.SLIDER.name) {
                    val cur = component.currentValue.toIntOrNull() ?: 0
                    if (cur > 0) cur.toString() else "50"
                } else {
                    "1"
                }
                val updatedComp = component.copy(
                    label = component.label.ifBlank { cleanFileName },
                    customImagePath = destFile.absolutePath,
                    currentValue = activeVal
                )
                studioDao.updateComponent(updatedComp)
                syncOverlayRegistryInBackground(_uiState.value.activeProject)
                withContext(Dispatchers.IO) {
                    stateWriter.applyWidgetPatchSync(
                        appContext.filesDir,
                        "widget_${updatedComp.id}",
                        updatedComp.type,
                        updatedComp.targetFilePath,
                        updatedComp.byteOffsetHex,
                        updatedComp.offPayloadHex,
                        updatedComp.onPayloadHex,
                        activeVal,
                        true,
                        updatedComp.label,
                        updatedComp.customImagePath
                    )
                }
                _uiState.update {
                    it.copy(
                        customEditedKotlinFiles = emptyMap(),
                        statusToast = "✅ File Selected: '$cleanFileName' → Configured for Target Path"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(statusToast = "Could not import file: ${e.message}")
                }
            }
        }
    }

    /**
     * Copies a user-picked audio file into local app storage and assigns it to either
     * the ON sound trigger or the OFF sound trigger of the component.
     */
    fun assignPickedSoundToComponent(component: CanvasComponentEntity, uri: Uri, isOffSound: Boolean) {
        viewModelScope.launch {
            try {
                val soundDir = File(appContext.filesDir, "component_sounds").apply { mkdirs() }
                val tag = if (isOffSound) "off" else "on"
                val destFile = File(soundDir, "snd_${tag}_${component.id}_${System.currentTimeMillis()}.mp3")
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val updated = if (isOffSound) {
                    component.copy(
                        offSoundTrigger = SoundTriggerPlayer.SOUND_CUSTOM_FILE,
                        offCustomSoundPath = destFile.absolutePath
                    )
                } else {
                    component.copy(
                        soundTrigger = SoundTriggerPlayer.SOUND_CUSTOM_FILE,
                        customSoundPath = destFile.absolutePath
                    )
                }
                updateComponent(updated)
                _uiState.update {
                    it.copy(
                        statusToast = if (isOffSound)
                            "Custom OFF sound bound to '${component.label}'."
                        else
                            "Custom ON sound bound to '${component.label}'."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(statusToast = "Could not import audio: ${e.message}")
                }
            }
        }
    }

    /**
     * Executes a component's interactive action (ON/OFF sound trigger + background file byte-offset write)
     * during Live Test Mode or from the Inspector Test button.
     */
    fun triggerComponentAction(component: CanvasComponentEntity, newValueOverride: String? = null) {
        val payloadToWrite: String
        val nextCurrentVal: String
        val isTurningOn: Boolean

        when (component.type) {
            ComponentWidgetType.LINK.name -> {
                val rawUrl = component.linkUrl.trim().ifEmpty { component.onPayloadHex.trim() }
                if (rawUrl.isNotEmpty()) {
                    val formatted = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) rawUrl else "https://$rawUrl"
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formatted)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        appContext.startActivity(intent)
                    } catch (_: Exception) {
                    }
                }
                SoundTriggerPlayer.playSoundTrigger(
                    appContext,
                    null,
                    component.soundTrigger,
                    component.customSoundPath
                )
                _uiState.update {
                    it.copy(statusToast = "🌐 Opening Link: ${rawUrl.ifEmpty { "https://google.com" }}")
                }
                return
            }
            ComponentWidgetType.SLIDER.name -> {
                nextCurrentVal = newValueOverride ?: component.currentValue
                payloadToWrite = nextCurrentVal
                val numericVal = nextCurrentVal.toIntOrNull() ?: 0
                isTurningOn = numericVal > 0
            }
            ComponentWidgetType.INPUT.name -> {
                if (newValueOverride != null) {
                    nextCurrentVal = newValueOverride
                    payloadToWrite = newValueOverride
                    isTurningOn = newValueOverride.isNotBlank() && newValueOverride != "0" && !newValueOverride.equals("false", ignoreCase = true)
                } else {
                    val currentlyOn = component.currentValue == "1" || component.currentValue.equals("true", ignoreCase = true)
                    isTurningOn = !currentlyOn
                    nextCurrentVal = if (isTurningOn) "1" else "0"
                    payloadToWrite = if (isTurningOn) component.onPayloadHex else component.offPayloadHex
                }
            }
            else -> {
                val currentlyOn = component.currentValue == "1" || component.currentValue.equals("true", ignoreCase = true)
                val nextOn = if (newValueOverride != null) {
                    newValueOverride == "1" || newValueOverride.equals("true", ignoreCase = true)
                } else {
                    !currentlyOn
                }
                isTurningOn = nextOn
                nextCurrentVal = if (nextOn) "1" else "0"
                payloadToWrite = if (nextOn) component.onPayloadHex else component.offPayloadHex
                _uiState.update {
                    it.copy(
                        statusToast = if (nextOn) {
                            "✅ ${component.label} → CHANGE (${component.onPayloadHex})"
                        } else {
                            "⛔ ${component.label} → ORIGINAL (${component.offPayloadHex})"
                        }
                    )
                }
            }
        }

        if (isTurningOn) {
            SoundTriggerPlayer.playSoundTrigger(
                appContext,
                null,
                component.soundTrigger,
                component.customSoundPath
            )
        } else {
            SoundTriggerPlayer.playSoundTrigger(
                appContext,
                null,
                component.offSoundTrigger,
                component.offCustomSoundPath
            )
        }

        val updatedComp = component.copy(currentValue = nextCurrentVal)
        if (_uiState.value.isBundledStandaloneApk) {
            _bundledStandaloneComponents.update { list ->
                list.map { if (it.id == updatedComp.id) updatedComp else it }
            }
        } else {
            updateComponent(updatedComp)
        }

        viewModelScope.launch(Dispatchers.IO) {
            studioDao.updateComponent(updatedComp)
            syncOverlayRegistryInBackground(_uiState.value.activeProject)
            val ok = stateWriter.applyWidgetPatchSync(
                appContext.filesDir,
                "widget_${updatedComp.id}",
                updatedComp.type,
                updatedComp.targetFilePath,
                updatedComp.byteOffsetHex,
                updatedComp.offPayloadHex,
                updatedComp.onPayloadHex,
                payloadToWrite,
                isTurningOn,
                updatedComp.label,
                updatedComp.customImagePath
            )
            val resolvedFile = stateWriter.resolveTargetFile(appContext.filesDir, updatedComp.targetFilePath)
            val preview = stateWriter.readTargetFilePreview(appContext.filesDir, updatedComp.targetFilePath)
            _uiState.update {
                it.copy(
                    statusToast = if (ok) {
                        "✅ Modified '${resolvedFile.name}' → $preview"
                    } else {
                        "⚠️ Cannot write '${resolvedFile.absolutePath}'. Tap 'Grant All Files Access' in Path settings!"
                    }
                )
            }
        }
    }

    fun toggleLivePreviewMode() {
        val nextMode = !_uiState.value.isLivePreviewMode
        _uiState.update {
            it.copy(
                isLivePreviewMode = nextMode,
                selectedComponentId = if (nextMode) null else it.selectedComponentId,
                statusToast = if (nextMode)
                    "Live Interactive Mode: Tap your components to fire sound triggers & file offset writes."
                else
                    "Edit Mode: Tap any component on the canvas to open the Bottom Property Dock."
            )
        }
    }

    fun hasStoragePermission(): Boolean {
        return LocalConfigStateWriter.hasStoragePermissionGranted(appContext)
    }

    fun refreshOverlayPermission() {
        ShizukuPrivilegeBridge.probeShizukuBinder(appContext)
        _uiState.update {
            it.copy(
                hasOverlayPermission = Settings.canDrawOverlays(appContext),
                hasStoragePermission = LocalConfigStateWriter.hasStoragePermissionGranted(appContext),
                isSystemOverlayRunning = FloatingDashboardService.isRunning(),
                shizukuStatusSummary = ShizukuPrivilegeBridge.getStatusSummary(appContext),
                isShizukuReady = ShizukuPrivilegeBridge.isShizukuReady(appContext),
                isShizukuRunning = ShizukuPrivilegeBridge.isShizukuRunning(appContext),
                isShizukuInstalled = ShizukuPrivilegeBridge.isShizukuInstalled(appContext)
            )
        }
    }

    fun dismissWriteErrorDialog() {
        stateWriter.clearLastDiagnosticReport()
        _uiState.update { it.copy(activeWriteErrorReport = null) }
    }

    fun requestOrLaunchShizuku() {
        connectOrAuthorizeShizuku(andRetryFailedWrite = true)
    }

    fun testAllActiveTargetPathsNow() {
        runTargetPathTestForCurrentProject()
    }

    fun connectOrAuthorizeShizuku(andRetryFailedWrite: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            ShizukuPrivilegeBridge.probeShizukuBinder(appContext)
            val ready = ShizukuPrivilegeBridge.isShizukuReady(appContext)
            val running = ShizukuPrivilegeBridge.isShizukuRunning(appContext)
            val installed = ShizukuPrivilegeBridge.isShizukuInstalled(appContext)
            val summary = ShizukuPrivilegeBridge.getStatusSummary(appContext)
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        shizukuStatusSummary = summary,
                        isShizukuReady = ready,
                        isShizukuRunning = running,
                        isShizukuInstalled = installed
                    )
                }
                if (ready) {
                    val pendingReport = _uiState.value.activeWriteErrorReport
                    if (andRetryFailedWrite && pendingReport?.retryAction != null) {
                        dismissWriteErrorDialog()
                        pendingReport.retryAction.run()
                    } else {
                        dismissWriteErrorDialog()
                        runTargetPathTestForCurrentProject()
                    }
                    _uiState.update {
                        it.copy(statusToast = "✅ Shizuku Connected ($summary) — Restricted Paths Unlocked!")
                    }
                } else if (running) {
                    ShizukuPrivilegeBridge.requestShizukuPermission(appContext, 9501)
                    _uiState.update {
                        it.copy(statusToast = "🛡️ Allow Shizuku permission popup to unlock Android 15 restricted paths.")
                    }
                } else if (installed) {
                    ShizukuPrivilegeBridge.openOrLaunchShizukuManager(appContext)
                    _uiState.update {
                        it.copy(statusToast = "🚀 Start Shizuku service in the Shizuku app, then return here.")
                    }
                } else {
                    ShizukuPrivilegeBridge.openOrLaunchShizukuManager(appContext)
                    _uiState.update {
                        it.copy(statusToast = "⚠️ Shizuku not installed. Install Shizuku (moe.shizuku.privileged.api) & start via Wireless Debugging.")
                    }
                }
            }
        }
    }

    fun retryFailedTargetWrite() {
        val report = _uiState.value.activeWriteErrorReport
        dismissWriteErrorDialog()
        ShizukuPrivilegeBridge.probeShizukuBinder(appContext)
        if (report?.retryAction != null) {
            report.retryAction.run()
        } else {
            runTargetPathTestForCurrentProject()
        }
    }

    fun runTargetPathTestForCurrentProject() {
        val components = if (_uiState.value.isBundledStandaloneApk) {
            _bundledStandaloneComponents.value
        } else {
            activeComponents.value
        }
        val specs = components.map { comp ->
            DynamicOverlayRegistry.OverlayItemSpec().apply {
                id = comp.id
                type = comp.type
                label = comp.label
                posXDp = comp.posXDp
                posYDp = comp.posYDp
                widthDp = comp.widthDp
                heightDp = comp.heightDp
                bgColorHex = comp.bgColorHex
                textColorHex = comp.textColorHex
                bgImagePath = comp.bgImagePath
                customImagePath = comp.customImagePath
                soundTrigger = comp.soundTrigger
                customSoundPath = comp.customSoundPath
                offSoundTrigger = comp.offSoundTrigger
                offCustomSoundPath = comp.offCustomSoundPath
                targetFilePath = comp.targetFilePath
                byteOffsetHex = comp.byteOffsetHex
                onPayloadHex = comp.onPayloadHex
                offPayloadHex = comp.offPayloadHex
                sliderMax = comp.sliderMax
                currentValue = comp.currentValue
                linkUrl = comp.linkUrl
            }
        }
        stateWriter.runFloatStartupTargetTestAndApplyAsync(appContext, appContext.filesDir, specs)
    }

    fun testSingleTargetPathFileChange(
        label: String,
        targetFilePath: String,
        byteOffsetHex: String = "0x04",
        offPayloadHex: String = "0x00",
        onPayloadHex: String = "0x01",
        customSourceFilePath: String? = null
    ) {
        stateWriter.testTargetPathFileChangeAsync(
            appContext,
            appContext.filesDir,
            label,
            targetFilePath,
            byteOffsetHex,
            offPayloadHex,
            onPayloadHex,
            customSourceFilePath
        )
    }

    fun launchSystemFloatingOverlay() {
        val project = _uiState.value.activeProject ?: return
        val hasOverlay = Settings.canDrawOverlays(appContext)
        val hasStorage = hasStoragePermission()

        if (!hasOverlay || !hasStorage) {
            try {
                appContext.stopService(Intent(appContext, FloatingDashboardService::class.java))
            } catch (_: Exception) {
            }
            val missing = mutableListOf<String>()
            if (!hasStorage) missing.add("Storage / All Files Access Permission")
            if (!hasOverlay) missing.add("Overlay Permission (SYSTEM_ALERT_WINDOW)")
            _uiState.update {
                it.copy(
                    isSystemOverlayRunning = false,
                    isLivePreviewMode = false,
                    hasOverlayPermission = hasOverlay,
                    hasStoragePermission = hasStorage,
                    statusToast = "⚠️ Permission Required: Please grant ${missing.joinToString(" & ")} before opening the floating panel."
                )
            }
            if (!hasStorage) {
                LocalConfigStateWriter.requestStoragePermission(appContext)
            } else if (!hasOverlay) {
                LocalConfigStateWriter.requestOverlayPermission(appContext)
            }
            return
        }

        val components = if (_uiState.value.isBundledStandaloneApk) {
            _bundledStandaloneComponents.value
        } else {
            activeComponents.value
        }
        val specs = components.map { comp ->
            DynamicOverlayRegistry.OverlayItemSpec().apply {
                id = comp.id
                type = comp.type
                label = comp.label
                posXDp = comp.posXDp
                posYDp = comp.posYDp
                widthDp = comp.widthDp
                heightDp = comp.heightDp
                bgColorHex = comp.bgColorHex
                textColorHex = comp.textColorHex
                bgImagePath = comp.bgImagePath
                customImagePath = comp.customImagePath
                soundTrigger = comp.soundTrigger
                customSoundPath = comp.customSoundPath
                offSoundTrigger = comp.offSoundTrigger
                offCustomSoundPath = comp.offCustomSoundPath
                targetFilePath = comp.targetFilePath
                byteOffsetHex = comp.byteOffsetHex
                onPayloadHex = comp.onPayloadHex
                offPayloadHex = comp.offPayloadHex
                sliderMax = comp.sliderMax
                currentValue = comp.currentValue
                linkUrl = comp.linkUrl
            }
        }

        DynamicOverlayRegistry.updateActiveOverlay(
            project.overlayTitle.ifBlank { project.name },
            project.floatingLogoPath.ifBlank { project.appLogoPath },
            project.canvasWidthDp,
            project.canvasHeightDp,
            project.canvasBgColorHex,
            project.canvasBgImagePath,
            project.autoFixSize,
            specs
        )

        try {
            val intent = Intent(appContext, FloatingDashboardService::class.java).apply {
                action = FloatingDashboardService.ACTION_START_OVERLAY
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
            _uiState.update {
                it.copy(
                    isSystemOverlayRunning = true,
                    statusToast = "System Floating Overlay launched with ${specs.size} custom component(s)."
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isSystemOverlayRunning = false,
                    statusToast = "Could not launch floating overlay: ${e.message}"
                )
            }
        }
    }

    fun stopSystemFloatingOverlay() {
        try {
            val intent = Intent(appContext, FloatingDashboardService::class.java).apply {
                action = FloatingDashboardService.ACTION_STOP_OVERLAY
            }
            appContext.stopService(intent)
        } catch (_: Exception) {
        }
        _uiState.update {
            it.copy(
                isSystemOverlayRunning = false,
                isLivePreviewMode = false,
                statusToast = "Floating overlay stopped. Tap any widget to edit."
            )
        }
    }

    fun computeComponentTrackerSummary(components: List<CanvasComponentEntity>): ComponentCountSummary {
        var buttons = 0
        var toggles = 0
        var sliders = 0
        var texts = 0
        var inputs = 0
        var images = 0
        for (c in components) {
            when (c.type) {
                ComponentWidgetType.BUTTON.name -> buttons++
                ComponentWidgetType.TOGGLE.name -> toggles++
                ComponentWidgetType.SLIDER.name -> sliders++
                ComponentWidgetType.TEXT.name -> texts++
                ComponentWidgetType.INPUT.name -> inputs++
                ComponentWidgetType.IMAGE.name -> images++
            }
        }
        return ComponentCountSummary(
            totalCount = components.size,
            buttonCount = buttons,
            toggleCount = toggles,
            sliderCount = sliders,
            textCount = texts,
            inputCount = inputs,
            imageCount = images
        )
    }

    fun dismissDownloadSummaryDialog() {
        _uiState.update { it.copy(downloadedFileSummary = null) }
    }

    fun openEditCodeDialog(show: Boolean) {
        if (show) {
            val project = _uiState.value.activeProject ?: return
            val generated = KotlinProjectCodeEngine.generateKotlinFilesForProject(project, activeComponents.value)
            _uiState.update {
                it.copy(
                    showEditCodeDialog = true,
                    customEditedKotlinFiles = generated,
                    statusToast = "Opened Edit Code (${generated.size} Kotlin files synced with Visual Screen)."
                )
            }
        } else {
            _uiState.update { it.copy(showEditCodeDialog = false) }
        }
    }

    /**
     * Parses the user's edited Kotlin files from the "Edit Code" window, updates the visual screen
     * project & widget database records so the visual screen immediately reflects the edited Kotlin code,
     * and optionally triggers full APK compilation & signing.
     */
    fun applyEditedKotlinCodeToVisualScreen(
        editedFiles: Map<String, String>,
        andCompileApk: Boolean = false
    ) {
        val project = _uiState.value.activeProject ?: return
        val currentComponents = activeComponents.value
        viewModelScope.launch {
            val parsed = KotlinProjectCodeEngine.parseEditedKotlinToVisualState(
                originalProject = project,
                originalComponents = currentComponents,
                editedFiles = editedFiles
            )
            studioDao.updateProject(parsed.updatedProject)
            studioDao.deleteAllComponentsForProject(project.id)
            for (comp in parsed.updatedComponents) {
                studioDao.insertComponent(comp.copy(id = 0L, projectId = project.id))
            }
            val reloadedComponents = studioDao.getComponentsForProjectSync(project.id)
            val refreshedKotlinFiles = KotlinProjectCodeEngine.generateKotlinFilesForProject(
                parsed.updatedProject,
                reloadedComponents
            ).toMutableMap().apply {
                // Preserve any custom edits in secondary Kotlin tabs while keeping CanvasWorkspaceComponents synced
                editedFiles.forEach { (k, v) ->
                    if (k != "src/main/java/com/example/ui/CanvasWorkspaceComponents.kt") {
                        put(k, v)
                    }
                }
            }

            _uiState.update {
                it.copy(
                    activeProject = parsed.updatedProject,
                    selectedComponentId = null,
                    showEditCodeDialog = false,
                    customEditedKotlinFiles = refreshedKotlinFiles,
                    statusToast = "✅ Compiled Kotlin Code → Visual Screen (${reloadedComponents.size} widgets synced)!"
                )
            }

            if (andCompileApk) {
                downloadFloatingWindowToAndroid(refreshedKotlinFiles)
            }
        }
    }

    /**
     * Compiles the app created by the user INSIDE Studio Error into a complete, standalone,
     * installable Android APK with its own unique package name, its own App Name (project.name),
     * 4-byte-aligned resources.arsc, 4096-byte-aligned native libraries, V1/V2/V3 signatures,
     * and bundled visual screen & floating window widgets.
     */
    fun downloadFloatingWindowToAndroid(customKotlinFiles: Map<String, String>? = null) {
        val initialProject = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            try {
                // Always read the latest project and visual components from Room DB / active visual state.
                // Only parse customKotlinFiles if explicitly passed from the "Edit Code -> Compile" action;
                // never overwrite the user's visual design with an older cached code snapshot!
                var project = studioDao.getProjectById(initialProject.id) ?: initialProject
                var components = studioDao.getComponentsForProjectSync(project.id)
                    .ifEmpty { activeComponents.value }

                if (customKotlinFiles != null &&
                    customKotlinFiles.containsKey("src/main/java/com/example/ui/CanvasWorkspaceComponents.kt")
                ) {
                    val parsed = KotlinProjectCodeEngine.parseEditedKotlinToVisualState(
                        originalProject = project,
                        originalComponents = components,
                        editedFiles = customKotlinFiles
                    )
                    project = parsed.updatedProject
                    studioDao.updateProject(project)
                    studioDao.deleteAllComponentsForProject(project.id)
                    for (comp in parsed.updatedComponents) {
                        studioDao.insertComponent(comp.copy(id = 0L, projectId = project.id))
                    }
                    components = studioDao.getComponentsForProjectSync(project.id)
                    _uiState.update { it.copy(activeProject = project) }
                }

                // Also update DynamicOverlayRegistry immediately so in-app preview & floating service have the exact latest design
                val latestSpecs = components.map { comp ->
                    DynamicOverlayRegistry.OverlayItemSpec().apply {
                        id = comp.id
                        type = comp.type
                        label = comp.label
                        posXDp = comp.posXDp
                        posYDp = comp.posYDp
                        widthDp = comp.widthDp
                        heightDp = comp.heightDp
                        bgColorHex = comp.bgColorHex
                        textColorHex = comp.textColorHex
                        bgImagePath = comp.bgImagePath
                        customImagePath = comp.customImagePath
                        soundTrigger = comp.soundTrigger
                        customSoundPath = comp.customSoundPath
                        offSoundTrigger = comp.offSoundTrigger
                        offCustomSoundPath = comp.offCustomSoundPath
                        targetFilePath = comp.targetFilePath
                        byteOffsetHex = comp.byteOffsetHex
                        onPayloadHex = comp.onPayloadHex
                        offPayloadHex = comp.offPayloadHex
                        sliderMax = comp.sliderMax
                        currentValue = comp.currentValue
                        linkUrl = comp.linkUrl
                    }
                }
                DynamicOverlayRegistry.updateActiveOverlay(
                    project.overlayTitle.ifBlank { project.name },
                    project.floatingLogoPath.ifBlank { project.appLogoPath },
                    project.canvasWidthDp,
                    project.canvasHeightDp,
                    project.canvasBgColorHex,
                    project.canvasBgImagePath,
                    project.autoFixSize,
                    latestSpecs
                )

                val safeSlug = project.name.trim().lowercase(Locale.US)
                    .replace(Regex("[^a-z0-9]+"), "_")
                    .trim('_')
                    .ifEmpty { "compiled_app" }
                val fileName = "${safeSlug}.apk"
                val targetPkg = ApkCompilationEngine.getCompiledAppPackageName(project)

                val blueprintFiles = ApkCompilationEngine.generateProjectBlueprintFiles(project, components).toMutableMap()
                val activeCustomFiles = customKotlinFiles ?: _uiState.value.customEditedKotlinFiles
                if (activeCustomFiles.isNotEmpty()) {
                    blueprintFiles.putAll(activeCustomFiles)
                    // Ensure overlay_config.json and CanvasWorkspaceComponents.kt ALWAYS reflect the current visual widgets
                    blueprintFiles["src/main/java/com/example/ui/CanvasWorkspaceComponents.kt"] =
                        KotlinProjectCodeEngine.generateKotlinFilesForProject(project, components)[
                            "src/main/java/com/example/ui/CanvasWorkspaceComponents.kt"
                        ].orEmpty()
                }

                val rawJavaSource = buildString {
                    appendLine("// === COMPILED APP: ${project.name} ($targetPkg) ===")
                    appendLine("// === KOTLIN WIDGET SOURCE: CanvasWorkspaceComponents.kt ===")
                    appendLine(blueprintFiles["src/main/java/com/example/ui/CanvasWorkspaceComponents.kt"].orEmpty())
                    appendLine()
                    appendLine("// === KOTLIN SERVICE SOURCE: FloatingOverlayService.kt ===")
                    appendLine(blueprintFiles["src/main/java/com/example/service/FloatingOverlayService.kt"].orEmpty())
                }

                _uiState.update {
                    it.copy(
                        isBuildingApk = true,
                        buildProgressStepText = "Step 1/3: Compiling '${project.name}' Kotlin Widget Code (${components.size} widgets)...",
                        rawJavaBuildPreview = rawJavaSource,
                        statusToast = "⚙ Compiling '${project.name}' ($targetPkg) into standalone APK..."
                    )
                }
                delay(180)

                _uiState.update {
                    it.copy(
                        buildProgressStepText = "Step 2/3: Patching Binary AndroidManifest.xml (App: '${project.name}', ID: $targetPkg) & Aligning APK..."
                    )
                }
                delay(180)

                val localDownloadsDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: File(appContext.filesDir, "downloads").apply { mkdirs() }
                if (!localDownloadsDir.exists()) localDownloadsDir.mkdirs()
                val localFile = File(localDownloadsDir, fileName)

                val compilationResult = withContext(Dispatchers.IO) {
                    ApkCompilationEngine.compileAndSignProjectApk(
                        appContext,
                        project,
                        components,
                        localFile,
                        blueprintFiles
                    )
                }

                _uiState.update {
                    it.copy(
                        buildProgressStepText = "Step 3/3: Signing V1 + V2 + V3 APK & Exporting '$fileName' to Android Downloads..."
                    )
                }
                delay(120)

                var savedDisplayLocation = compilationResult.signedApkFile.absolutePath

                // Save compiled & signed APK directly to public Android Downloads via MediaStore on Android 10+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    withContext(Dispatchers.IO) {
                        try {
                            val resolver = appContext.contentResolver
                            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                            try {
                                resolver.delete(
                                    collection,
                                    "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                                    arrayOf(fileName)
                                )
                            } catch (_: Exception) {
                            }

                            val contentValues = ContentValues().apply {
                                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                                put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                                put(MediaStore.Downloads.IS_PENDING, 1)
                            }
                            val itemUri = resolver.insert(collection, contentValues)
                            if (itemUri != null) {
                                resolver.openOutputStream(itemUri)?.use { out ->
                                    FileInputStream(compilationResult.signedApkFile).use { input ->
                                        input.copyTo(out, bufferSize = 32768)
                                    }
                                }
                                contentValues.clear()
                                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                                resolver.update(itemUri, contentValues, null, null)
                                savedDisplayLocation = "Internal Storage/Download/$fileName"
                            }
                        } catch (_: Exception) {
                        }
                    }
                }

                val sizeBytes = compilationResult.apkSizeBytes
                val formattedSize = if (sizeBytes >= 1024L * 1024L) {
                    String.format(Locale.US, "%.2f MB", sizeBytes / (1024.0 * 1024.0))
                } else {
                    String.format(Locale.US, "%.1f KB", (sizeBytes / 1024.0).coerceAtLeast(1.0))
                }

                SoundTriggerPlayer.playSoundTrigger(appContext, null, SoundTriggerPlayer.SOUND_CONFIRM, "")
                _uiState.update {
                    it.copy(
                        isBuildingApk = false,
                        buildProgressStepText = "",
                        rawJavaBuildPreview = rawJavaSource,
                        compiledApkFilePath = compilationResult.signedApkFile.absolutePath,
                        compiledAppPackageName = compilationResult.compiledPackageName,
                        compiledAppName = compilationResult.compiledAppName,
                        downloadedFileName = fileName,
                        downloadedFileSummary = buildString {
                            appendLine("App Compiled: ${compilationResult.compiledAppName.ifBlank { "(No Name Set)" }}")
                            appendLine("Package ID: ${compilationResult.compiledPackageName}")
                            appendLine("Version: v${project.versionName} (Code ${project.versionCode}) • SDK ${project.minSdk}–${project.targetSdk}")
                            appendLine("Widgets Compiled: ${components.size} interactive widget(s)")
                            appendLine("APK Size: $formattedSize (Signed V1 + V2 + V3)")
                            append("Saved to: $savedDisplayLocation")
                        },
                        statusToast = "✅ Compiled '${compilationResult.compiledAppName}' APK ($formattedSize)!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isBuildingApk = false,
                        buildProgressStepText = "",
                        statusToast = "Build failed: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Launches the Android system Package Installer for the newly compiled standalone APK
     * so the user can install the app they created inside Studio Error with one tap.
     */
    fun installCompiledApk(context: Context) {
        // Stop any running floating overlay first so Android PackageInstaller & Settings never block permission clicks!
        stopSystemFloatingOverlay()
        val apkPath = _uiState.value.compiledApkFilePath
        val apkFile = if (!apkPath.isNullOrBlank()) File(apkPath) else null
        if (apkFile == null || !apkFile.exists()) {
            _uiState.update { it.copy(statusToast = "Please tap Build first to compile your APK.") }
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                val permIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(permIntent)
                _uiState.update {
                    it.copy(statusToast = "Allow 'Install unknown apps' then tap Install Compiled APK again.")
                }
                return
            }

            val authority = "${context.packageName}.fileprovider"
            val apkUri = FileProvider.getUriForFile(context, authority, apkFile)
            @Suppress("DEPRECATION")
            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = apkUri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.packageName)
            }
            context.startActivity(installIntent)
            _uiState.update {
                it.copy(
                    statusToast = "📲 Opening Android Installer for '${_uiState.value.compiledAppName}'..."
                )
            }
        } catch (e: Exception) {
            try {
                val authority = "${context.packageName}.fileprovider"
                val apkUri = FileProvider.getUriForFile(context, authority, apkFile)
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {
                openCompiledAppPreview()
            }
        }
    }

    /**
     * Opens the compiled standalone app screen directly so the user can immediately run and test
     * the exact app they built inside Studio Error.
     */
    fun openCompiledAppPreview() {
        val project = _uiState.value.activeProject ?: return
        val pkg = _uiState.value.compiledAppPackageName.ifBlank {
            ApkCompilationEngine.getCompiledAppPackageName(project)
        }
        _uiState.update {
            it.copy(
                destination = StudioDestination.COMPILED_STANDALONE_APP,
                downloadedFileSummary = null,
                compiledAppPackageName = pkg,
                compiledAppName = project.name,
                statusToast = "▶ Running Compiled App '${project.name}' ($pkg)"
            )
        }
    }

    fun closeCompiledAppPreview() {
        if (_uiState.value.isBundledStandaloneApk) return
        _uiState.update {
            it.copy(
                destination = StudioDestination.CANVAS_WORKSPACE,
                statusToast = "Returned to Studio Error Editor for '${it.activeProject?.name ?: "Project"}'."
            )
        }
    }

    fun saveFloatingWindowToCustomUri(destUri: Uri) {
        val project = _uiState.value.activeProject ?: return
        val components = activeComponents.value
        viewModelScope.launch {
            try {
                val safeSlug = project.name.trim().lowercase(Locale.US)
                    .replace(Regex("[^a-z0-9]+"), "_")
                    .trim('_')
                    .ifEmpty { "floating_mod_menu" }
                val tempOutFile = File(appContext.cacheDir, "${safeSlug}_custom_export.apk")
                withContext(Dispatchers.IO) {
                    val result = ApkCompilationEngine.compileAndSignProjectApk(
                        appContext,
                        project,
                        components,
                        tempOutFile
                    )
                    appContext.contentResolver.openOutputStream(destUri)?.use { out ->
                        FileInputStream(result.signedApkFile).use { input ->
                            input.copyTo(out, bufferSize = 32768)
                        }
                    }
                    if (tempOutFile.exists()) tempOutFile.delete()
                }
                _uiState.update {
                    it.copy(
                        downloadedFileSummary = null,
                        statusToast = "✅ Saved compiled & signed APK to selected Android folder!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(statusToast = "Could not save APK: ${e.message}")
                }
            }
        }
    }

    fun navigateBackToWelcome() {
        markWelcomeSeen()
        _uiState.update {
            it.copy(
                destination = StudioDestination.PROJECT_LAUNCHER,
                statusToast = "App Studio Home"
            )
        }
    }

    /**
     * Opens Offline Manual Mode (StudioProjectLauncherScreen).
     */
    fun openOfflineManualMode() {
        markWelcomeSeen()
        _uiState.update {
            it.copy(
                destination = StudioDestination.PROJECT_LAUNCHER,
                ggufModelState = it.ggufModelState.copy(
                    mode = StudioGenerationMode.OFFLINE_MANUAL,
                    importErrorMessage = null
                ),
                statusToast = "Offline Manual Mode Active."
            )
        }
    }

    /**
     * Opens Online / AI Mode directly (no GGUF file selection screen).
     * Uses the in-app configurable Termux Server (Host, Port, URL).
     */
    fun openOnlineAiMode() {
        markWelcomeSeen()
        val currentServer = _uiState.value.termuxServerConfig
        _uiState.update {
            it.copy(
                destination = StudioDestination.AI_STUDIO_WORKSPACE,
                ggufModelState = it.ggufModelState.copy(
                    mode = StudioGenerationMode.AI_GGUF_MODE,
                    importErrorMessage = null
                ),
                statusToast = "Termux AI Server Mode Active (${currentServer.url})"
            )
        }
    }

    fun openGgufGateForChange() {
        toggleTermuxServerConfigPanel()
    }

    fun toggleTermuxServerConfigPanel() {
        _uiState.update {
            val current = it.termuxServerConfig
            it.copy(
                termuxServerConfig = current.copy(isConfigExpanded = !current.isConfigExpanded)
            )
        }
    }

    fun updateTermuxHost(newHost: String) {
        val current = _uiState.value.termuxServerConfig
        val syncedUrl = TermuxServerClient.buildUrlFromHostPort(newHost, current.port, current.url)
        val saved = TermuxServerClient.saveConfig(
            appContext,
            host = newHost,
            port = current.port,
            url = syncedUrl,
            isExpanded = current.isConfigExpanded
        )
        _uiState.update {
            it.copy(
                termuxServerConfig = saved.copy(
                    host = newHost,
                    url = syncedUrl
                )
            )
        }
    }

    fun updateTermuxPort(newPort: String) {
        val current = _uiState.value.termuxServerConfig
        val syncedUrl = TermuxServerClient.buildUrlFromHostPort(current.host, newPort, current.url)
        val saved = TermuxServerClient.saveConfig(
            appContext,
            host = current.host,
            port = newPort,
            url = syncedUrl,
            isExpanded = current.isConfigExpanded
        )
        _uiState.update {
            it.copy(
                termuxServerConfig = saved.copy(
                    port = newPort,
                    url = syncedUrl
                )
            )
        }
    }

    fun updateTermuxUrl(newUrl: String) {
        val current = _uiState.value.termuxServerConfig
        val (extractedHost, extractedPort) = TermuxServerClient.extractHostAndPortFromUrl(
            newUrl,
            current.host,
            current.port
        )
        val saved = TermuxServerClient.saveConfig(
            appContext,
            host = extractedHost,
            port = extractedPort,
            url = newUrl,
            isExpanded = current.isConfigExpanded
        )
        _uiState.update {
            it.copy(
                termuxServerConfig = saved.copy(
                    host = extractedHost,
                    port = extractedPort,
                    url = newUrl
                )
            )
        }
    }

    fun saveTermuxServerConfig(host: String, port: String, url: String) {
        val current = _uiState.value.termuxServerConfig
        val saved = TermuxServerClient.saveConfig(
            appContext,
            host = host,
            port = port,
            url = url,
            isExpanded = current.isConfigExpanded
        )
        _uiState.update {
            it.copy(
                termuxServerConfig = saved,
                statusToast = "✅ Termux Server Saved: ${saved.url} (Host: ${saved.host}, Port: ${saved.port})"
            )
        }
    }

    fun testTermuxServerConnection() {
        val config = _uiState.value.termuxServerConfig
        _uiState.update {
            it.copy(
                termuxServerConfig = config.copy(
                    isTestingConnection = true,
                    connectionStatus = "Pinging Termux Server at ${config.url}..."
                )
            )
        }
        viewModelScope.launch {
            val response = withContext(Dispatchers.IO) {
                TermuxServerClient.pingServer(config)
            }
            _uiState.update {
                val msg = if (response.isSuccess) {
                    "✅ Connected to Termux Server ${response.resolvedEndpoint} (HTTP ${response.httpCode}, ${response.durationMs}ms)"
                } else {
                    "❌ Unreachable: ${config.url} (${response.errorMessage ?: "Connection failed"})"
                }
                it.copy(
                    termuxServerConfig = it.termuxServerConfig.copy(
                        isTestingConnection = false,
                        lastTestSuccess = response.isSuccess,
                        connectionStatus = msg
                    ),
                    statusToast = msg
                )
            }
        }
    }

    fun importGgufModelUri(uri: Uri) {
        openOnlineAiMode()
    }

    fun loadGgufModelFromPath(filePath: String) {
        openOnlineAiMode()
    }

    /**
     * Sends the user's message directly to the configured Termux Server (Host / Port / URL).
     * Shows a real-time single Live Status inside the chat turn (Sending prompt ->
     * AI is analysing user prompt -> Creating/Editing file status + path + name ->
     * Build / Error / Answer status) and only builds when Termux indicates a build.
     */
    fun sendPromptInAiMode(prompt: String) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty() || _uiState.value.isGeneratingAiBlueprint) return

        val currentState = _uiState.value
        val serverConfig = currentState.termuxServerConfig
        val existingAiProject = currentState.aiBuiltProject
        val turnId = System.currentTimeMillis()
        val historySteps = mutableListOf<AiBuildStepStatus>()

        fun recordStatus(
            liveText: String,
            stepTitle: String,
            stepDetail: String,
            hasError: Boolean = false,
            inProgress: Boolean = true
        ) {
            val nextNum = historySteps.size + 1
            historySteps.add(
                AiBuildStepStatus(
                    stepNumber = nextNum,
                    totalSteps = nextNum,
                    title = stepTitle,
                    detail = stepDetail,
                    isCompleted = true,
                    hasError = hasError
                )
            )
            val snapshotSteps = historySteps.mapIndexed { idx, item ->
                item.copy(stepNumber = idx + 1, totalSteps = historySteps.size)
            }
            _uiState.update { state ->
                val updatedHistory = state.aiChatHistory.map { turn ->
                    if (turn.id == turnId) {
                        turn.copy(
                            currentLiveStatus = liveText,
                            isInProgress = inProgress,
                            hasError = turn.hasError || hasError,
                            steps = snapshotSteps
                        )
                    } else {
                        turn
                    }
                }
                state.copy(
                    aiLiveBuildSteps = snapshotSteps,
                    aiChatHistory = updatedHistory
                )
            }
        }

        val initialStep = AiBuildStepStatus(
            stepNumber = 1,
            totalSteps = 1,
            title = "Sending prompt",
            detail = "POST ${serverConfig.url} (Host: ${serverConfig.host}, Port: ${serverConfig.port})",
            isCompleted = true
        )
        historySteps.add(initialStep)

        val pendingTurn = AiChatTurn(
            id = turnId,
            userPrompt = cleanPrompt,
            aiResponseText = "",
            currentLiveStatus = "Sending prompt...",
            isInProgress = true,
            hasError = false,
            steps = historySteps.toList(),
            isAppReady = false,
            isConversationalReply = true
        )

        _uiState.update {
            it.copy(
                isGeneratingAiBlueprint = true,
                aiLiveBuildSteps = historySteps.toList(),
                aiChatHistory = it.aiChatHistory + pendingTurn,
                statusToast = "Sending prompt to ${serverConfig.url}..."
            )
        }

        viewModelScope.launch {
            try {
                delay(180)
                recordStatus(
                    liveText = "AI is analysing user prompt...",
                    stepTitle = "AI is analysing user prompt",
                    stepDetail = "Termux Server (${serverConfig.host}:${serverConfig.port}) is analysing prompt..."
                )

                // Send message directly to Termux Server over HTTP
                val serverResponse = withContext(Dispatchers.IO) {
                    TermuxServerClient.sendPromptToServer(
                        config = serverConfig,
                        prompt = cleanPrompt,
                        existingProjectName = existingAiProject?.name
                    )
                }

                if (!serverResponse.isSuccess) {
                    val errDetail = serverResponse.errorMessage ?: "Connection refused / timed out"
                    recordStatus(
                        liveText = "Error: $errDetail",
                        stepTitle = "Error: Termux Server Unreachable",
                        stepDetail = "Host: ${serverConfig.host} | Port: ${serverConfig.port} | URL: ${serverConfig.url} — $errDetail",
                        hasError = true,
                        inProgress = false
                    )
                    val errText = buildString {
                        appendLine("❌ Termux Server Error")
                        appendLine("Host: ${serverConfig.host} | Port: ${serverConfig.port}")
                        appendLine("URL: ${serverConfig.url}")
                        appendLine("Reason: $errDetail")
                        append("Check that your Termux server is running or update Host / Port / URL above.")
                    }
                    _uiState.update { state ->
                        state.copy(
                            isGeneratingAiBlueprint = false,
                            aiLiveBuildSteps = emptyList(),
                            aiChatHistory = state.aiChatHistory.map { turn ->
                                if (turn.id == turnId) {
                                    turn.copy(
                                        aiResponseText = errText,
                                        currentLiveStatus = "Error: $errDetail",
                                        isInProgress = false,
                                        hasError = true,
                                        isAppReady = false,
                                        isConversationalReply = false
                                    )
                                } else turn
                            },
                            termuxServerConfig = state.termuxServerConfig.copy(
                                lastTestSuccess = false,
                                connectionStatus = "❌ Unreachable: ${serverConfig.url} ($errDetail)"
                            ),
                            statusToast = "Error: $errDetail"
                        )
                    }
                    return@launch
                }

                // Termux Server responded with HTTP 200..299
                _uiState.update {
                    it.copy(
                        termuxServerConfig = it.termuxServerConfig.copy(
                            lastTestSuccess = true,
                            connectionStatus = "✅ Connected: ${serverResponse.resolvedEndpoint} (HTTP ${serverResponse.httpCode}, ${serverResponse.durationMs}ms)"
                        )
                    )
                }

                // Surface any build/runtime errors reported by Termux
                if (serverResponse.parsedBuildErrors.isNotEmpty()) {
                    for (errItem in serverResponse.parsedBuildErrors) {
                        recordStatus(
                            liveText = "Error: $errItem",
                            stepTitle = "Error reported by Termux",
                            stepDetail = errItem,
                            hasError = true,
                            inProgress = true
                        )
                        delay(140)
                    }
                }

                // Only build an app if Termux response actually indicates a build or returns source files!
                // If Termux is just returning an answer/chat response, show the answer status directly without fake app generation.
                val shouldBuildFromTermux = serverResponse.isBuildFromTermux

                if (!shouldBuildFromTermux) {
                    recordStatus(
                        liveText = "Receiving answer from Termux AI...",
                        stepTitle = "Receiving answer from Termux AI",
                        stepDetail = "Endpoint ${serverResponse.resolvedEndpoint} returned answer in ${serverResponse.durationMs}ms",
                        hasError = false,
                        inProgress = true
                    )
                    delay(120)
                    val finalReply = serverResponse.parsedReplyText
                        .ifBlank { serverResponse.rawResponseText }
                        .ifBlank { "Termux Server responded (HTTP ${serverResponse.httpCode})." }

                    val hasTermuxErr = serverResponse.parsedBuildErrors.isNotEmpty()
                    val finalStatusLabel = if (hasTermuxErr) {
                        "Error in response: ${serverResponse.parsedBuildErrors.first()}"
                    } else {
                        "Answer received from Termux AI (${serverResponse.durationMs}ms)"
                    }
                    recordStatus(
                        liveText = finalStatusLabel,
                        stepTitle = "Answer completed",
                        stepDetail = "Reply received from ${serverResponse.resolvedEndpoint}",
                        hasError = hasTermuxErr,
                        inProgress = false
                    )

                    _uiState.update { state ->
                        state.copy(
                            isGeneratingAiBlueprint = false,
                            aiLiveBuildSteps = emptyList(),
                            aiChatHistory = state.aiChatHistory.map { turn ->
                                if (turn.id == turnId) {
                                    turn.copy(
                                        aiResponseText = finalReply,
                                        currentLiveStatus = finalStatusLabel,
                                        isInProgress = false,
                                        hasError = hasTermuxErr,
                                        isAppReady = false,
                                        isConversationalReply = true,
                                        generatedCodePreview = "",
                                        generatedScratchFiles = emptyMap()
                                    )
                                } else turn
                            },
                            statusToast = "Answer received from Termux Server"
                        )
                    }
                    return@launch
                }

                // Termux indicated a build / returned files to create or edit!
                recordStatus(
                    liveText = "Termux build signal received — preparing workspace...",
                    stepTitle = "Termux build started",
                    stepDetail = "Server ${serverResponse.resolvedEndpoint} initiated app build",
                    inProgress = true
                )
                delay(120)

                val defaultTarget = getDefaultTargetFilePath("ai_generated_app")
                val combinedPromptForEngine = if (serverResponse.parsedReplyText.isNotBlank()) {
                    "$cleanPrompt\n${serverResponse.parsedReplyText}"
                } else {
                    cleanPrompt
                }
                val baseSpec = withContext(Dispatchers.Default) {
                    GgufBlueprintEngine.generateBlueprintFromPrompt(
                        prompt = combinedPromptForEngine,
                        projectId = -999L,
                        defaultTargetFilePath = defaultTarget,
                        modelState = _uiState.value.ggufModelState.copy(
                            modelFileName = "TermuxServer(${serverConfig.host}:${serverConfig.port})",
                            modelArchitecture = serverConfig.url
                        ),
                        existingProjectName = serverResponse.parsedAppName ?: _uiState.value.aiBuiltProject?.name,
                        existingComponents = _uiState.value.aiBuiltComponents
                    )
                }

                val mergedScratchFiles = baseSpec.generatedScratchFiles.toMutableMap().apply {
                    putAll(serverResponse.parsedScratchFiles)
                }
                val resolvedAppName = serverResponse.parsedAppName ?: baseSpec.suggestedAppName
                val resolvedPkgName = serverResponse.parsedPackageName ?: baseSpec.suggestedPackageName
                val spec = baseSpec.copy(
                    suggestedAppName = resolvedAppName,
                    suggestedPackageName = resolvedPkgName,
                    generatedScratchFiles = mergedScratchFiles
                )

                // Persist files and emit live status for every file created or edited (with name & path)
                val targetFileForAi = spec.suggestedTargetFilePath.ifBlank { defaultTarget }
                val scratchRoot = File(appContext.filesDir, "ai_scratch_workspace/${spec.suggestedPackageName}")
                val isUpdatingExistingApp = existingAiProject != null

                for ((relPath, codeContent) in spec.generatedScratchFiles.entries) {
                    val outSource = File(scratchRoot, relPath)
                    val existedBefore = outSource.exists() ||
                        serverResponse.parsedEditedFiles.contains(relPath) ||
                        (isUpdatingExistingApp && !serverResponse.parsedScratchFiles.containsKey(relPath))
                    val fileName = relPath.substringAfterLast('/')
                    val fullDisplayPath = "${spec.projectRootPath}/$relPath"

                    if (existedBefore) {
                        recordStatus(
                            liveText = "Editing file: $fileName ($relPath)",
                            stepTitle = "Editing file: $fileName",
                            stepDetail = "Path: $fullDisplayPath",
                            inProgress = true
                        )
                    } else {
                        recordStatus(
                            liveText = "Creating file: $fileName ($relPath)",
                            stepTitle = "Creating file: $fileName",
                            stepDetail = "Path: $fullDisplayPath",
                            inProgress = true
                        )
                    }

                    withContext(Dispatchers.IO) {
                        try {
                            stateWriter.resolveTargetFile(appContext.filesDir, targetFileForAi)
                            outSource.parentFile?.mkdirs()
                            outSource.writeText(codeContent, Charsets.UTF_8)
                        } catch (_: Exception) {
                        }
                    }
                    delay(110)
                }

                // Report any compiler / reference fixes or errors
                if (spec.autoPatchedFixes.isNotEmpty()) {
                    for (fix in spec.autoPatchedFixes) {
                        recordStatus(
                            liveText = "Compiler Auto-Fix: $fix",
                            stepTitle = "Compiler Diagnostic & Fix",
                            stepDetail = fix,
                            hasError = false,
                            inProgress = true
                        )
                        delay(100)
                    }
                }

                // Live status during APK build ("Build kr dauran build")
                val now = System.currentTimeMillis()
                val isolatedAiProject = StudioProjectEntity(
                    id = -999L,
                    name = spec.suggestedAppName,
                    packageName = spec.suggestedPackageName,
                    projectName = spec.suggestedAppName,
                    overlayTitle = spec.suggestedOverlayTitle,
                    canvasWidthDp = 260,
                    canvasHeightDp = (spec.components.size * 58 + 40).coerceIn(280, 520),
                    defaultTargetFilePath = targetFileForAi,
                    createdAt = now,
                    updatedAt = now
                )

                val outDir = File(appContext.filesDir, "compiled_apks").apply { mkdirs() }
                val apkFileName = spec.apkFileName.ifBlank {
                    val safeSlug = isolatedAiProject.name.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "ai_app" }
                    "${safeSlug}.apk"
                }
                val outFile = File(outDir, apkFileName)

                recordStatus(
                    liveText = "Building APK: $apkFileName...",
                    stepTitle = "Building APK: $apkFileName",
                    stepDetail = "Compiling & signing ${spec.suggestedPackageName} -> ${outFile.absolutePath}",
                    inProgress = true
                )

                val apkResult = withContext(Dispatchers.IO) {
                    ApkCompilationEngine.compileAndSignProjectApk(
                        appContext,
                        isolatedAiProject,
                        spec.components,
                        outFile,
                        spec.generatedScratchFiles
                    )
                }

                var publicApkSavedPath = spec.publicDownloadApkPath.ifBlank { "/storage/emulated/0/Download/$apkFileName" }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    withContext(Dispatchers.IO) {
                        try {
                            val resolver = appContext.contentResolver
                            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                            try {
                                resolver.delete(
                                    collection,
                                    "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                                    arrayOf(apkFileName)
                                )
                            } catch (_: Exception) {
                            }
                            val contentValues = ContentValues().apply {
                                put(MediaStore.Downloads.DISPLAY_NAME, apkFileName)
                                put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                                put(MediaStore.Downloads.IS_PENDING, 1)
                            }
                            val itemUri = resolver.insert(collection, contentValues)
                            if (itemUri != null) {
                                resolver.openOutputStream(itemUri)?.use { out ->
                                    FileInputStream(apkResult.signedApkFile).use { input ->
                                        input.copyTo(out, bufferSize = 32768)
                                    }
                                }
                                contentValues.clear()
                                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                                resolver.update(itemUri, contentValues, null, null)
                                publicApkSavedPath = "/storage/emulated/0/Download/$apkFileName"
                            }
                        } catch (_: Exception) {
                        }
                    }
                }

                val sizeKb = String.format(Locale.US, "%.1f KB", (apkResult.apkSizeBytes / 1024.0).coerceAtLeast(1.0))
                val completedStatusText = "Build complete: $apkFileName ($sizeKb)"
                recordStatus(
                    liveText = completedStatusText,
                    stepTitle = "Build complete: $apkFileName",
                    stepDetail = "APK Path: ${apkResult.signedApkFile.absolutePath} ($sizeKb)",
                    hasError = false,
                    inProgress = false
                )

                val finalSteps = historySteps.mapIndexed { idx, item ->
                    item.copy(stepNumber = idx + 1, totalSteps = historySteps.size)
                }

                val serverReplyHeader = if (serverResponse.parsedReplyText.isNotBlank()) {
                    "${serverResponse.parsedReplyText}\n\n"
                } else {
                    ""
                }

                val aiTurn = AiChatTurn(
                    id = turnId,
                    userPrompt = cleanPrompt,
                    aiResponseText = serverReplyHeader + spec.structuredBuildOutput,
                    currentLiveStatus = completedStatusText,
                    isInProgress = false,
                    hasError = serverResponse.parsedBuildErrors.isNotEmpty(),
                    steps = finalSteps,
                    isAppReady = true,
                    isConversationalReply = false,
                    generatedCodePreview = spec.kotlinJavaSummary,
                    generatedScratchFiles = spec.generatedScratchFiles,
                    appName = spec.suggestedAppName,
                    packageName = spec.suggestedPackageName,
                    apkFileName = apkFileName,
                    apkFilePath = apkResult.signedApkFile.absolutePath,
                    publicDownloadApkPath = publicApkSavedPath,
                    projectRootPath = spec.projectRootPath,
                    targetDataFileName = File(targetFileForAi).name,
                    targetDataFilePath = targetFileForAi,
                    isFloatingOverlayApp = spec.isFloatingOverlayApp,
                    appCategory = spec.appCategory,
                    fileArtifacts = spec.fileArtifacts
                )

                val apkSummaryReport = buildString {
                    appendLine("APP_NAME: ${apkResult.compiledAppName} | PACKAGE_NAME: ${apkResult.compiledPackageName}")
                    appendLine("APK_NAME: $apkFileName ($sizeKb • Signed V1+V2+V3)")
                    appendLine("APK_PATH: ${apkResult.signedApkFile.absolutePath}")
                    append("PUBLIC_DOWNLOAD_PATH: $publicApkSavedPath")
                }

                _uiState.update { state ->
                    state.copy(
                        isGeneratingAiBlueprint = false,
                        aiLiveBuildSteps = emptyList(),
                        aiChatHistory = state.aiChatHistory.map { existing ->
                            if (existing.id == turnId) aiTurn else existing
                        },
                        aiBuiltProject = isolatedAiProject,
                        aiBuiltComponents = spec.components,
                        aiBuiltIsFloatingOverlay = spec.isFloatingOverlayApp,
                        aiBuiltAppCategory = spec.appCategory,
                        aiBuiltFileArtifacts = spec.fileArtifacts,
                        compiledApkFilePath = apkResult.signedApkFile.absolutePath,
                        compiledAppName = apkResult.compiledAppName,
                        compiledAppPackageName = apkResult.compiledPackageName,
                        aiCompiledApkFilePath = apkResult.signedApkFile.absolutePath,
                        aiCompiledPublicApkPath = publicApkSavedPath,
                        aiCompiledApkSummary = apkSummaryReport,
                        statusToast = "BUILD_SUCCESS: $apkFileName -> ${apkResult.signedApkFile.absolutePath}"
                    )
                }
            } catch (e: Exception) {
                val errMsg = e.message ?: "Unexpected error during build"
                recordStatus(
                    liveText = "Build Error: $errMsg",
                    stepTitle = "Build Error",
                    stepDetail = errMsg,
                    hasError = true,
                    inProgress = false
                )
                _uiState.update { state ->
                    state.copy(
                        isGeneratingAiBlueprint = false,
                        aiLiveBuildSteps = emptyList(),
                        aiChatHistory = state.aiChatHistory.map { turn ->
                            if (turn.id == turnId) {
                                turn.copy(
                                    aiResponseText = "❌ Error detected during build: $errMsg",
                                    currentLiveStatus = "Build Error: $errMsg",
                                    isInProgress = false,
                                    hasError = true,
                                    isAppReady = false,
                                    isConversationalReply = false
                                )
                            } else turn
                        }
                    )
                }
            }
        }
    }

    /**
     * Toggles the live Android Floating Overlay specifically for the AI-generated app in AI Mode.
     */
    fun toggleAiModeFloatingOverlay() {
        val aiProject = _uiState.value.aiBuiltProject ?: return
        val aiComponents = _uiState.value.aiBuiltComponents
        if (_uiState.value.isAiFloatingOverlayRunning) {
            stopSystemFloatingOverlay()
            _uiState.update { it.copy(isAiFloatingOverlayRunning = false) }
            return
        }

        val hasOverlay = Settings.canDrawOverlays(appContext)
        val hasStorage = LocalConfigStateWriter.hasStoragePermissionGranted(appContext)
        _uiState.update {
            it.copy(
                hasOverlayPermission = hasOverlay,
                hasStoragePermission = hasStorage
            )
        }
        if (!hasStorage) {
            LocalConfigStateWriter.requestStoragePermission(appContext)
            return
        }
        if (!hasOverlay) {
            LocalConfigStateWriter.requestOverlayPermission(appContext)
            return
        }

        val specs = aiComponents.map { comp ->
            DynamicOverlayRegistry.OverlayItemSpec().apply {
                id = comp.id
                type = comp.type
                label = comp.label
                posXDp = comp.posXDp
                posYDp = comp.posYDp
                widthDp = comp.widthDp
                heightDp = comp.heightDp
                bgColorHex = comp.bgColorHex
                textColorHex = comp.textColorHex
                customImagePath = comp.customImagePath
                soundTrigger = comp.soundTrigger
                customSoundPath = comp.customSoundPath
                offSoundTrigger = comp.offSoundTrigger
                offCustomSoundPath = comp.offCustomSoundPath
                targetFilePath = comp.targetFilePath
                byteOffsetHex = comp.byteOffsetHex
                onPayloadHex = comp.onPayloadHex
                offPayloadHex = comp.offPayloadHex
                sliderMax = comp.sliderMax
                currentValue = comp.currentValue
                linkUrl = comp.linkUrl
            }
        }
        DynamicOverlayRegistry.updateActiveOverlay(
            aiProject.overlayTitle,
            aiProject.floatingLogoPath,
            aiProject.canvasWidthDp,
            aiProject.canvasHeightDp,
            aiProject.canvasBgColorHex,
            aiProject.autoFixSize,
            specs
        )
        try {
            val intent = Intent(appContext, FloatingDashboardService::class.java).apply {
                action = FloatingDashboardService.ACTION_START_OVERLAY
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
            _uiState.update {
                it.copy(
                    isSystemOverlayRunning = true,
                    isAiFloatingOverlayRunning = true,
                    statusToast = "Floating '${aiProject.overlayTitle}' active!"
                )
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Interactive Test handler for widgets inside AI Mode (updates AI widget state, calculator/counter/timer/notes display & writes to target file).
     */
    fun triggerAiWidgetTest(comp: CanvasComponentEntity, overrideVal: String?) {
        val currentList = _uiState.value.aiBuiltComponents.toMutableList()
        val currentlyOn = comp.currentValue == "1" || comp.currentValue.equals("true", ignoreCase = true)
        val nextVal = overrideVal ?: if (currentlyOn) "0" else "1"
        val isTurningOn = nextVal == "1" || nextVal.equals("true", ignoreCase = true) || ((nextVal.toIntOrNull() ?: 0) > 0)
        val payloadToWrite = when (comp.type) {
            ComponentWidgetType.SLIDER.name, ComponentWidgetType.INPUT.name -> nextVal
            else -> if (isTurningOn) comp.onPayloadHex else comp.offPayloadHex
        }

        // Update clicked/edited widget
        val compIndex = currentList.indexOfFirst { it.id == comp.id }
        if (compIndex >= 0) {
            currentList[compIndex] = currentList[compIndex].copy(currentValue = nextVal)
        }

        // Dynamically update primary display widget (header TEXT component at index 0) for interactive apps
        if (currentList.isNotEmpty() && currentList[0].type == ComponentWidgetType.TEXT.name && comp.id != currentList[0].id) {
            val header = currentList[0]
            val token = comp.onPayloadHex.trim()
            val calcTokens = setOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "+", "-", "*", "/", ".")
            when {
                token == "C" || token.equals("Clear", ignoreCase = true) -> {
                    currentList[0] = header.copy(label = "0", currentValue = "0")
                }
                token == "=" && header.onPayloadHex == "CALC_DISPLAY" -> {
                    val expr = header.currentValue.ifBlank { header.label }
                    val result = evaluateSimpleMathExpression(expr)
                    currentList[0] = header.copy(label = result, currentValue = result)
                }
                token in calcTokens && (header.onPayloadHex == "CALC_DISPLAY" || _uiState.value.aiBuiltAppCategory == "CALCULATOR_APP") -> {
                    val base = if (header.currentValue == "0" && token !in setOf("+", "-", "*", "/", ".")) "" else header.currentValue
                    val updatedExpr = base + token
                    currentList[0] = header.copy(label = updatedExpr, currentValue = updatedExpr)
                }
                comp.label.contains("(+") || token == "+1" -> {
                    val curr = header.currentValue.toIntOrNull() ?: 0
                    val nextCount = curr + 1
                    currentList[0] = header.copy(label = "Count: $nextCount", currentValue = nextCount.toString())
                }
                comp.label.contains("(-") || token == "-1" -> {
                    val curr = header.currentValue.toIntOrNull() ?: 0
                    val nextCount = curr - 1
                    currentList[0] = header.copy(label = "Count: $nextCount", currentValue = nextCount.toString())
                }
                token.equals("Reset", ignoreCase = true) -> {
                    val resetLabel = if (_uiState.value.aiBuiltAppCategory == "TIMER_APP") "00:00.00" else "0"
                    currentList[0] = header.copy(label = resetLabel, currentValue = "0")
                }
                token.equals("Saved", ignoreCase = true) -> {
                    val noteInput = currentList.firstOrNull { it.type == ComponentWidgetType.INPUT.name }?.currentValue.orEmpty()
                    currentList[0] = header.copy(
                        label = if (noteInput.isNotBlank()) "Saved: $noteInput" else "Note Saved",
                        currentValue = noteInput
                    )
                }
                token == "=" -> {
                    val numInput = currentList.firstOrNull { it.type == ComponentWidgetType.INPUT.name }?.currentValue?.toDoubleOrNull() ?: 1.0
                    val converted = String.format(Locale.US, "%.2f", numInput * 2.54)
                    currentList[0] = header.copy(label = "Result: $converted", currentValue = converted)
                }
            }
        }

        _uiState.update { it.copy(aiBuiltComponents = currentList) }

        if (isTurningOn) {
            SoundTriggerPlayer.playSoundTrigger(appContext, null, comp.soundTrigger, comp.customSoundPath)
        } else {
            SoundTriggerPlayer.playSoundTrigger(appContext, null, comp.offSoundTrigger, comp.offCustomSoundPath)
        }

        stateWriter.applyWidgetPatchAsync(
            appContext.filesDir,
            "ai_widget_${comp.id}",
            comp.type,
            comp.targetFilePath,
            comp.byteOffsetHex,
            comp.offPayloadHex,
            comp.onPayloadHex,
            payloadToWrite,
            isTurningOn,
            comp.label
        )
    }

    private fun evaluateSimpleMathExpression(rawExpr: String): String {
        val clean = rawExpr.replace(Regex("[^0-9.+\\-*/]"), "")
        if (clean.isEmpty()) return "0"
        return try {
            var result = 0.0
            var op = '+'
            val token = StringBuilder()
            for (i in 0..clean.length) {
                val c = if (i < clean.length) clean[i] else '+'
                if (c in '0'..'9' || c == '.') {
                    token.append(c)
                } else if (token.isNotEmpty()) {
                    val v = token.toString().toDoubleOrNull() ?: 0.0
                    when (op) {
                        '+' -> result += v
                        '-' -> result -= v
                        '*' -> result *= v
                        '/' -> result = if (v != 0.0) result / v else 0.0
                    }
                    op = c
                    token.setLength(0)
                }
            }
            if (result == kotlin.math.floor(result)) result.toLong().toString() else String.format(Locale.US, "%.4f", result).trimEnd('0').trimEnd('.')
        } catch (_: Exception) {
            "0"
        }
    }

    /**
     * Compiles and signs the standalone APK for the AI-generated app in AI Mode,
     * embedding the scratch-generated Kotlin/Java/XML files and exporting to public Downloads.
     */
    fun compileAndDownloadAiApk() {
        val aiProject = _uiState.value.aiBuiltProject ?: return
        val aiComponents = _uiState.value.aiBuiltComponents
        val latestScratchFiles = _uiState.value.aiChatHistory
            .lastOrNull { it.generatedScratchFiles.isNotEmpty() }
            ?.generatedScratchFiles
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(aiCompiledApkSummary = "Compiling & signing standalone APK for '${aiProject.name}'...")
                }
                val safeSlug = aiProject.name.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "ai_app" }
                val apkFileName = "${safeSlug}.apk"
                val outDir = File(appContext.filesDir, "compiled_apks").apply { mkdirs() }
                val outFile = File(outDir, apkFileName)

                val result = withContext(Dispatchers.IO) {
                    ApkCompilationEngine.compileAndSignProjectApk(
                        appContext,
                        aiProject,
                        aiComponents,
                        outFile,
                        latestScratchFiles
                    )
                }

                var publicApkSavedPath = "/storage/emulated/0/Download/$apkFileName"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    withContext(Dispatchers.IO) {
                        try {
                            val resolver = appContext.contentResolver
                            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                            try {
                                resolver.delete(
                                    collection,
                                    "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                                    arrayOf(apkFileName)
                                )
                            } catch (_: Exception) {
                            }
                            val contentValues = ContentValues().apply {
                                put(MediaStore.Downloads.DISPLAY_NAME, apkFileName)
                                put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                                put(MediaStore.Downloads.IS_PENDING, 1)
                            }
                            val itemUri = resolver.insert(collection, contentValues)
                            if (itemUri != null) {
                                resolver.openOutputStream(itemUri)?.use { out ->
                                    FileInputStream(result.signedApkFile).use { input ->
                                        input.copyTo(out, bufferSize = 32768)
                                    }
                                }
                                contentValues.clear()
                                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                                resolver.update(itemUri, contentValues, null, null)
                            }
                        } catch (_: Exception) {
                        }
                    }
                }

                val sizeKb = String.format(Locale.US, "%.1f KB", (result.apkSizeBytes / 1024.0).coerceAtLeast(1.0))
                val summaryReport = buildString {
                    appendLine("APP_NAME: ${result.compiledAppName} | PACKAGE_NAME: ${result.compiledPackageName}")
                    appendLine("APK_NAME: $apkFileName ($sizeKb • Signed V1+V2+V3)")
                    appendLine("APK_PATH: ${result.signedApkFile.absolutePath}")
                    append("PUBLIC_DOWNLOAD_PATH: $publicApkSavedPath")
                }
                _uiState.update {
                    it.copy(
                        compiledApkFilePath = result.signedApkFile.absolutePath,
                        compiledAppName = result.compiledAppName,
                        compiledAppPackageName = result.compiledPackageName,
                        aiCompiledApkFilePath = result.signedApkFile.absolutePath,
                        aiCompiledPublicApkPath = publicApkSavedPath,
                        aiCompiledApkSummary = summaryReport,
                        statusToast = "APK Compiled: $apkFileName -> $publicApkSavedPath"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(aiCompiledApkSummary = "APK Build Error: ${e.message}")
                }
            }
        }
    }
}
