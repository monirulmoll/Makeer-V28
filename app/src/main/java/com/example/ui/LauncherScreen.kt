package com.example.ui

import android.Manifest
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Token
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.StudioProjectEntity
import com.example.engine.LocalConfigStateWriter
import java.io.File

enum class AppStudioSubScreen {
    HOME,
    CREATE_NEW_HUB,
    CREATE_NEW_APP,
    IMPORT_PROJECT,
    SAVED_PROJECTS,
    TEMPLATES,
    SETTINGS
}

private val StudioDarkBg = Color(0xFF090D18)
private val StudioCardBg = Color(0xFF13192B)
private val StudioCardBorder = Color(0xFF222B45)
private val StudioIndigoPrimary = Color(0xFF4338CA)
private val StudioIndigoAccent = Color(0xFF6366F1)
private val StudioTextSecondary = Color(0xFF94A3B8)

@Composable
fun StudioProjectLauncherScreen(
    uiState: StudioUiState,
    projects: List<StudioProjectEntity>,
    defaultPathProvider: (String) -> String,
    onOpenCreateDialog: () -> Unit,
    onDismissCreateDialog: () -> Unit,
    onCreateProject: (
        name: String,
        packageName: String,
        projectName: String,
        overlayTitle: String,
        canvasWidthDp: Int,
        canvasHeightDp: Int,
        targetFilePath: String,
        appLogoPath: String,
        versionCode: Int,
        versionName: String,
        minSdk: Int,
        targetSdk: Int
    ) -> Unit,
    onOpenExistingPicker: () -> Unit,
    onDismissExistingPicker: () -> Unit,
    onSelectProject: (StudioProjectEntity) -> Unit,
    onDuplicateProject: (StudioProjectEntity) -> Unit = {},
    onDeleteProject: (Long) -> Unit,
    onOpenEditProjectDialog: (StudioProjectEntity) -> Unit,
    onDismissEditProjectDialog: () -> Unit,
    onSaveProjectConfiguration: (
        project: StudioProjectEntity,
        newName: String,
        newPackageName: String,
        newProjectName: String,
        newOverlayTitle: String,
        newLogoPath: String,
        newVersionCode: Int,
        newVersionName: String,
        newMinSdk: Int,
        newTargetSdk: Int,
        newFloatingLogoPath: String
    ) -> Unit,
    onImportLogoUri: (Uri, (String) -> Unit) -> Unit,
    onSyncErrorStudioFolder: () -> Unit = {},
    onImportProjectUri: (Uri) -> Unit = {},
    onRefreshPermissions: () -> Unit,
    onOpenOnlineAiMode: () -> Unit,
    onBackToWelcome: () -> Unit = onRefreshPermissions,
    onSaveDefaultButtonLogic: (String) -> Unit = {},
    onSaveLuaPreExecutionCode: (String) -> Unit = {}
) {
    val editingProject = uiState.editingProject
    if (editingProject != null) {
        EditProjectNameAndLogoDialog(
            editingProject = editingProject,
            onDismiss = onDismissEditProjectDialog,
            onImportLogoUri = onImportLogoUri,
            onSave = { name, pkg, overlayTitle, appLogo, floatLogo ->
                onSaveProjectConfiguration(
                    editingProject,
                    name,
                    pkg,
                    name,
                    overlayTitle,
                    appLogo,
                    editingProject.versionCode,
                    editingProject.versionName,
                    editingProject.minSdk,
                    editingProject.targetSdk,
                    floatLogo
                )
            }
        )
    }

    LauncherScreen(
        projects = projects,
        statusMessage = uiState.statusToast,
        isGgufLoaded = uiState.ggufModelState.isValidGgufLoaded,
        ggufModelName = uiState.ggufModelState.modelFileName,
        hasStoragePermission = uiState.hasStoragePermission,
        hasOverlayPermission = uiState.hasOverlayPermission,
        onRefreshPermissions = onRefreshPermissions,
        onImportLogoUri = onImportLogoUri,
        onSyncErrorStudioFolder = onSyncErrorStudioFolder,
        onImportProjectUri = onImportProjectUri,
        onCreateProjectWithLogo = { name, pkg, overlayTitle, logoPath ->
            val resolvedTargetPath = if (pkg.trim().startsWith("lua.script", ignoreCase = true)) {
                com.example.engine.LuaScriptEngine.defaultLuaPathForProject(name)
            } else {
                defaultPathProvider(name)
            }
            onCreateProject(
                name,
                pkg,
                name,
                overlayTitle,
                216,
                290,
                resolvedTargetPath,
                logoPath,
                1,
                "1.0",
                24,
                36
            )
        },
        onOpenProject = onSelectProject,
        onEditProject = onOpenEditProjectDialog,
        onDuplicateProject = onDuplicateProject,
        onDeleteProject = onDeleteProject,
        onOpenAiStudio = onOpenOnlineAiMode,
        onBackToWelcome = onBackToWelcome,
        defaultButtonLogic = uiState.defaultButtonLogic,
        onSaveDefaultButtonLogic = onSaveDefaultButtonLogic,
        luaPreExecutionCode = uiState.luaPreExecutionCode,
        onSaveLuaPreExecutionCode = onSaveLuaPreExecutionCode
    )
}

@Composable
private fun EditProjectNameAndLogoDialog(
    editingProject: StudioProjectEntity,
    onDismiss: () -> Unit,
    onImportLogoUri: (Uri, (String) -> Unit) -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var editedName by remember(editingProject.id) { mutableStateOf(editingProject.name) }
    var editedPackage by remember(editingProject.id) { mutableStateOf(editingProject.packageName) }
    var editedOverlayTitle by remember(editingProject.id) { mutableStateOf(editingProject.overlayTitle) }
    var editedAppLogoPath by remember(editingProject.id) { mutableStateOf(editingProject.appLogoPath) }
    var editedFloatingLogoPath by remember(editingProject.id) { mutableStateOf(editingProject.floatingLogoPath) }

    val floatingLogoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onImportLogoUri(uri) { savedPath ->
                editedFloatingLogoPath = savedPath
                editedAppLogoPath = savedPath
            }
        }
    }

    val previewLogoBitmap = remember(editedAppLogoPath, editedFloatingLogoPath) {
        val pathToLoad = editedAppLogoPath.ifBlank { editedFloatingLogoPath }
        if (pathToLoad.isNotBlank()) {
            val f = File(pathToLoad)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
        } else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioCardBg,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Text(
                text = "Edit Floating Panel Name & Logo",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(StudioIndigoPrimary)
                            .border(BorderStroke(2.dp, StudioIndigoAccent), RoundedCornerShape(16.dp))
                            .clickable {
                                floatingLogoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .testTag("launcher_edit_project_logo_preview"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewLogoBitmap != null) {
                            Image(
                                bitmap = previewLogoBitmap,
                                contentDescription = "Floating Panel Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Add Logo",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Button(
                            onClick = {
                                floatingLogoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StudioIndigoPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("launcher_pick_floating_logo_button")
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Select Panel Logo", fontSize = 12.sp)
                        }
                        if (editedFloatingLogoPath.isNotBlank() || editedAppLogoPath.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    editedFloatingLogoPath = ""
                                    editedAppLogoPath = ""
                                }
                            ) {
                                Text("Remove Logo", color = Color(0xFFF87171), fontSize = 11.sp)
                            }
                        }
                    }
                }

                DarkStudioTextField(
                    value = editedOverlayTitle,
                    onValueChange = { editedOverlayTitle = it },
                    label = "Floating Panel Name (Header Title)",
                    placeholder = "Enter floating panel name",
                    testTag = "launcher_edit_overlay_title_input"
                )

                DarkStudioTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = "App Name",
                    placeholder = "Enter app name",
                    testTag = "launcher_edit_project_name_input"
                )

                DarkStudioTextField(
                    value = editedPackage,
                    onValueChange = { editedPackage = it },
                    label = "Package Name",
                    placeholder = "com.example.myapp",
                    testTag = "launcher_edit_package_input"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        editedName.ifBlank { editingProject.name },
                        editedPackage,
                        editedOverlayTitle.ifBlank { editedName },
                        editedAppLogoPath,
                        editedFloatingLogoPath
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = StudioIndigoPrimary),
                modifier = Modifier.testTag("launcher_save_project_config_button")
            ) {
                Text("Save Changes", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = StudioTextSecondary)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(
    projects: List<StudioProjectEntity>,
    statusMessage: String,
    isGgufLoaded: Boolean,
    ggufModelName: String,
    hasStoragePermission: Boolean = true,
    hasOverlayPermission: Boolean = true,
    onRefreshPermissions: () -> Unit = {},
    onImportLogoUri: (Uri, (String) -> Unit) -> Unit = { _, _ -> },
    onSyncErrorStudioFolder: () -> Unit = {},
    onImportProjectUri: (Uri) -> Unit = {},
    onCreateProjectWithLogo: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onCreateProject: (String, String, String) -> Unit = { name, pkg, title ->
        onCreateProjectWithLogo(name, pkg, title, "")
    },
    onOpenProject: (StudioProjectEntity) -> Unit,
    onEditProject: (StudioProjectEntity) -> Unit = {},
    onDuplicateProject: (StudioProjectEntity) -> Unit = {},
    onDeleteProject: (Long) -> Unit,
    onOpenAiStudio: () -> Unit,
    onBackToWelcome: () -> Unit,
    defaultButtonLogic: String = com.example.engine.LuaCustomWidgetEngine.FALLBACK_DEFAULT_BUTTON_LOGIC,
    onSaveDefaultButtonLogic: (String) -> Unit = {},
    luaPreExecutionCode: String = "",
    onSaveLuaPreExecutionCode: (String) -> Unit = {},
    initialSubScreen: AppStudioSubScreen = AppStudioSubScreen.HOME
) {
    val context = LocalContext.current
    var currentSubScreen by remember { mutableStateOf(initialSubScreen) }
    var initialCreateMode by remember { mutableStateOf("MTDP") }
    var showImportUrlDialog by remember { mutableStateOf<String?>(null) }
    var importRepoInput by remember { mutableStateOf("") }

    val storagePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshPermissions()
        if (!LocalConfigStateWriter.hasStoragePermissionGranted(context)) {
            LocalConfigStateWriter.requestStoragePermission(context)
        }
    }

    val importDeviceFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportProjectUri(uri)
        }
    }

    BackHandler(enabled = currentSubScreen != AppStudioSubScreen.HOME) {
        currentSubScreen = AppStudioSubScreen.HOME
    }

    if (showImportUrlDialog != null) {
        val sourceTitle = showImportUrlDialog ?: "GitHub"
        AlertDialog(
            onDismissRequest = { showImportUrlDialog = null },
            containerColor = StudioCardBg,
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = {
                Text("Import Project from $sourceTitle", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter a repository URL or project identifier to import into App Studio:",
                        color = StudioTextSecondary,
                        fontSize = 12.sp
                    )
                    DarkStudioTextField(
                        value = importRepoInput,
                        onValueChange = { importRepoInput = it },
                        label = "$sourceTitle URL or Repo",
                        placeholder = "https://github.com/user/my-floating-app",
                        testTag = "import_repo_url_input"
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val raw = importRepoInput.trim().substringAfterLast('/').substringBeforeLast(".git").ifBlank { "GitHubProject" }
                        val cleanPkg = "com.appstudio.${raw.lowercase().replace(Regex("[^a-z0-9]"), "").ifEmpty { "githubapp" }}"
                        showImportUrlDialog = null
                        importRepoInput = ""
                        onCreateProjectWithLogo(raw, cleanPkg, "$raw Panel", "")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StudioIndigoPrimary)
                ) {
                    Text("Import & Open", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportUrlDialog = null }) {
                    Text("Cancel", color = StudioTextSecondary)
                }
            }
        )
    }

    val showBottomBar = currentSubScreen == AppStudioSubScreen.HOME ||
        currentSubScreen == AppStudioSubScreen.SAVED_PROJECTS ||
        currentSubScreen == AppStudioSubScreen.TEMPLATES

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("welcome_screen_root"),
        containerColor = StudioDarkBg,
        bottomBar = {
            if (showBottomBar) {
                AppStudioBottomNavigationBar(
                    currentSubScreen = currentSubScreen,
                    onSelectSubScreen = { target -> currentSubScreen = target },
                    onOpenAiStudio = onOpenAiStudio
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentSubScreen) {
                AppStudioSubScreen.HOME -> {
                    AppStudioHomeScreenContent(
                        projects = projects,
                        statusMessage = statusMessage,
                        hasStoragePermission = hasStoragePermission,
                        hasOverlayPermission = hasOverlayPermission,
                        onRequestStoragePermission = {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                storagePermLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_EXTERNAL_STORAGE,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    )
                                )
                            } else {
                                LocalConfigStateWriter.requestStoragePermission(context)
                            }
                        },
                        onRequestOverlayPermission = {
                            LocalConfigStateWriter.requestOverlayPermission(context)
                        },
                        onOpenCreateNewHub = { currentSubScreen = AppStudioSubScreen.CREATE_NEW_HUB },
                        onOpenCreateNewAppForm = {
                            initialCreateMode = "MTDP"
                            currentSubScreen = AppStudioSubScreen.CREATE_NEW_APP
                        },
                        onOpenAiMode = onOpenAiStudio,
                        onOpenSettings = { currentSubScreen = AppStudioSubScreen.SETTINGS },
                        onOpenSavedProjects = { currentSubScreen = AppStudioSubScreen.SAVED_PROJECTS },
                        onOpenImportProject = { currentSubScreen = AppStudioSubScreen.IMPORT_PROJECT },
                        onOpenTemplates = { currentSubScreen = AppStudioSubScreen.TEMPLATES },
                        onOpenProject = onOpenProject,
                        onEditProject = onEditProject,
                        onDuplicateProject = onDuplicateProject,
                        onDeleteProject = onDeleteProject,
                        onCreateStarterProject = { name, pkg, panelTitle ->
                            onCreateProjectWithLogo(name, pkg, panelTitle, "")
                        }
                    )
                }

                AppStudioSubScreen.CREATE_NEW_HUB -> {
                    CreateNewHubScreenContent(
                        onClose = { currentSubScreen = AppStudioSubScreen.HOME },
                        onSelectCreateNewApp = {
                            initialCreateMode = "MTDP"
                            currentSubScreen = AppStudioSubScreen.CREATE_NEW_APP
                        },
                        onSelectCreateLuaScript = {
                            initialCreateMode = "LUA_SCRIPT"
                            currentSubScreen = AppStudioSubScreen.CREATE_NEW_APP
                        },
                        onSelectUseTemplate = { currentSubScreen = AppStudioSubScreen.TEMPLATES },
                        onSelectImportProject = { currentSubScreen = AppStudioSubScreen.IMPORT_PROJECT },
                        onSelectAiMode = onOpenAiStudio,
                        onSelectMyApps = { currentSubScreen = AppStudioSubScreen.SAVED_PROJECTS }
                    )
                }

                AppStudioSubScreen.CREATE_NEW_APP -> {
                    CreateNewAppWizardScreenContent(
                        initialCreateMode = initialCreateMode,
                        hasStoragePermission = hasStoragePermission,
                        hasOverlayPermission = hasOverlayPermission,
                        onRequestStoragePermission = {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                storagePermLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_EXTERNAL_STORAGE,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    )
                                )
                            } else {
                                LocalConfigStateWriter.requestStoragePermission(context)
                            }
                        },
                        onRequestOverlayPermission = {
                            LocalConfigStateWriter.requestOverlayPermission(context)
                        },
                        onBack = { currentSubScreen = AppStudioSubScreen.HOME },
                        onImportLogoUri = onImportLogoUri,
                        onCreateApp = { name, pkg, overlayTitle, logoPath ->
                            onCreateProjectWithLogo(name, pkg, overlayTitle, logoPath)
                        }
                    )
                }

                AppStudioSubScreen.IMPORT_PROJECT -> {
                    ImportProjectScreenContent(
                        onBack = { currentSubScreen = AppStudioSubScreen.HOME },
                        onSyncErrorStudioFolder = {
                            onSyncErrorStudioFolder()
                            currentSubScreen = AppStudioSubScreen.SAVED_PROJECTS
                        },
                        onImportFromDevice = {
                            importDeviceFileLauncher.launch(arrayOf("*/*"))
                        },
                        onImportFromGitHub = {
                            showImportUrlDialog = "GitHub"
                        },
                        onImportFromUrl = {
                            showImportUrlDialog = "Public URL"
                        },
                        onImportFromCloud = {
                            importDeviceFileLauncher.launch(arrayOf("*/*"))
                        }
                    )
                }

                AppStudioSubScreen.SAVED_PROJECTS -> {
                    SavedProjectsScreenContent(
                        projects = projects,
                        onBack = { currentSubScreen = AppStudioSubScreen.HOME },
                        onCreateNewApp = { currentSubScreen = AppStudioSubScreen.CREATE_NEW_APP },
                        onOpenProject = onOpenProject,
                        onEditProject = onEditProject,
                        onDuplicateProject = onDuplicateProject,
                        onDeleteProject = onDeleteProject,
                        onCreateStarterProject = { name, pkg, panelTitle ->
                            onCreateProjectWithLogo(name, pkg, panelTitle, "")
                        }
                    )
                }

                AppStudioSubScreen.TEMPLATES -> {
                    TemplatesScreenContent(
                        onBack = { currentSubScreen = AppStudioSubScreen.HOME },
                        onUseTemplate = { name, pkg, panelTitle ->
                            onCreateProjectWithLogo(name, pkg, panelTitle, "")
                        }
                    )
                }

                AppStudioSubScreen.SETTINGS -> {
                    StudioSettingsScreenContent(
                        defaultButtonLogic = defaultButtonLogic,
                        onSaveDefaultButtonLogic = onSaveDefaultButtonLogic,
                        luaPreExecutionCode = luaPreExecutionCode,
                        onSaveLuaPreExecutionCode = onSaveLuaPreExecutionCode,
                        hasStoragePermission = hasStoragePermission,
                        hasOverlayPermission = hasOverlayPermission,
                        onRefreshPermissions = onRefreshPermissions,
                        onOpenAiStudio = onOpenAiStudio,
                        onClose = { currentSubScreen = AppStudioSubScreen.HOME }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppStudioHomeScreenContent(
    projects: List<StudioProjectEntity>,
    statusMessage: String,
    hasStoragePermission: Boolean,
    hasOverlayPermission: Boolean,
    onRequestStoragePermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onOpenCreateNewHub: () -> Unit,
    onOpenCreateNewAppForm: () -> Unit,
    onOpenAiMode: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenSavedProjects: () -> Unit,
    onOpenImportProject: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenProject: (StudioProjectEntity) -> Unit,
    onEditProject: (StudioProjectEntity) -> Unit,
    onDuplicateProject: (StudioProjectEntity) -> Unit = {},
    onDeleteProject: (Long) -> Unit,
    onCreateStarterProject: (String, String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. TOP APP BAR ("App Studio — Build • Create • Innovate")
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_app_icon_1790519873461),
                        contentDescription = "App Studio Icon",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, Color(0xFF6366F1)), RoundedCornerShape(12.dp))
                    )
                    Column {
                        Text(
                            text = "App Studio",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Build • Create • Innovate",
                            color = StudioTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onOpenSavedProjects) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Projects",
                            tint = Color(0xFFCBD5E1)
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("home_top_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color(0xFFCBD5E1)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                            .border(BorderStroke(1.dp, Color(0xFF475569)), CircleShape)
                            .clickable { onOpenSavedProjects() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 2. HERO BANNER ("Turn Your Ideas Into Real Apps 🚀")
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color(0xFF1E3A8A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF0E2154),
                                    Color(0xFF0C193E),
                                    Color(0xFF1A1446)
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Turn Your Ideas\nInto Real Apps",
                                    color = Color.White,
                                    fontSize = 21.sp,
                                    lineHeight = 26.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Icon(
                                    imageVector = Icons.Default.RocketLaunch,
                                    contentDescription = null,
                                    tint = Color(0xFFF97316),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Text(
                                text = "Build Android apps with ease.\nNo coding knowledge required.",
                                color = Color(0xFF93C5FD),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = onOpenCreateNewAppForm,
                                colors = ButtonDefaults.buttonColors(containerColor = StudioIndigoPrimary),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                modifier = Modifier.testTag("welcome_select_offline_mode_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Create New App",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        // Stylistic Phone Mockup Graphic on right side of Hero Card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF090D1A),
                            border = BorderStroke(2.dp, Color(0xFF3B82F6)),
                            modifier = Modifier
                                .width(92.dp)
                                .height(132.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(28.dp)
                                        .height(4.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF334155))
                                        .align(Alignment.CenterHorizontally)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        for (c in listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFF10B981))) {
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(c)
                                            )
                                        }
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        for (c in listOf(Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF06B6D4))) {
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(c)
                                            )
                                        }
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        for (c in listOf(Color(0xFF6366F1), Color(0xFF14B8A6), Color(0xFFF43F5E))) {
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(c)
                                            )
                                        }
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(StudioIndigoPrimary)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. 4 ACTION TILES ROW (Create New App, AI Mode, Saved Projects, Import Project)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HomeFeatureTile(
                    title = "Create\nNew App",
                    icon = Icons.Default.Add,
                    iconBgColor = Color(0xFF2563EB),
                    isHighlighted = true,
                    onClick = onOpenCreateNewHub,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("welcome_select_offline_mode_card")
                )
                HomeFeatureTile(
                    title = "AI Mode",
                    icon = Icons.Default.AutoAwesome,
                    iconBgColor = Color(0xFF0D9488),
                    isHighlighted = false,
                    onClick = onOpenAiMode,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("welcome_select_online_ai_mode_card")
                )
                HomeFeatureTile(
                    title = "Saved\nProjects",
                    icon = Icons.Default.Folder,
                    iconBgColor = Color(0xFF16A34A),
                    isHighlighted = false,
                    onClick = onOpenSavedProjects,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_saved_projects_tile")
                )
                HomeFeatureTile(
                    title = "Import\nProject",
                    icon = Icons.Default.FileDownload,
                    iconBgColor = Color(0xFF059669),
                    isHighlighted = false,
                    onClick = onOpenImportProject,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_import_project_tile")
                )
            }
        }

        // PERMISSIONS CARDS SECTION (Overlay Permission, Storage Permission & Grant Permissions button)
        if (!hasOverlayPermission || !hasStoragePermission) {
            item {
                StudioPermissionsSectionCard(
                    hasOverlayPermission = hasOverlayPermission,
                    hasStoragePermission = hasStoragePermission,
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onRequestStoragePermission = onRequestStoragePermission
                )
            }
        }

        // 4. QUICK ACTIONS SECTION
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Quick Actions",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionPill(
                        icon = Icons.Default.History,
                        label = "Open Recent",
                        onClick = onOpenSavedProjects
                    )
                    QuickActionPill(
                        icon = Icons.Default.DashboardCustomize,
                        label = "Templates",
                        onClick = onOpenTemplates
                    )
                    QuickActionPill(
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        label = "Documentation",
                        onClick = onOpenCreateNewHub
                    )
                    QuickActionPill(
                        icon = Icons.Default.Settings,
                        label = "Settings",
                        onClick = onOpenSettings,
                        testTag = "home_quick_action_settings_button"
                    )
                    QuickActionPill(
                        icon = Icons.Default.AutoAwesome,
                        label = "AI Studio",
                        onClick = onOpenAiMode,
                        testTag = "welcome_select_online_ai_mode_button"
                    )
                }
            }
        }

        // 5. RECENT PROJECTS SECTION
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Projects",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "View All",
                    color = Color(0xFF6366F1),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onOpenSavedProjects() }
                )
            }
        }

        if (projects.isNotEmpty()) {
            items(projects.take(5), key = { it.id }) { project ->
                DarkProjectListCard(
                    project = project,
                    onOpen = { onOpenProject(project) },
                    onEdit = { onEditProject(project) },
                    onDuplicate = { onDuplicateProject(project) },
                    onDelete = { onDeleteProject(project.id) }
                )
            }
        }
    }
}

@Composable
private fun HomeFeatureTile(
    title: String,
    icon: ImageVector,
    iconBgColor: Color,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = StudioCardBg,
        border = BorderStroke(
            width = if (isHighlighted) 1.5.dp else 1.dp,
            color = if (isHighlighted) StudioIndigoAccent else StudioCardBorder
        ),
        modifier = modifier
            .height(104.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(iconBgColor.copy(alpha = 0.22f))
                    .border(BorderStroke(1.dp, iconBgColor), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun QuickActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String? = null
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = StudioCardBg,
        border = BorderStroke(1.dp, StudioCardBorder),
        modifier = Modifier
            .clickable(onClick = onClick)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = StudioTextSecondary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                color = Color(0xFFE2E8F0),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun DarkProjectListCard(
    project: StudioProjectEntity,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit = {},
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val itemLogoPath = project.appLogoPath.ifBlank { project.floatingLogoPath }
    val itemLogoBitmap = remember(itemLogoPath) {
        if (itemLogoPath.isNotBlank()) {
            val f = File(itemLogoPath)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
        } else null
    }

    val badgeColors = listOf(
        Color(0xFF00C896),
        Color(0xFF2563EB),
        Color(0xFF6366F1),
        Color(0xFF10B981),
        Color(0xFFEC4899)
    )
    val badgeColor = badgeColors[(project.id.toInt().coerceAtLeast(0)) % badgeColors.size]

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF081226),
        border = BorderStroke(1.dp, Color(0xFF1A2D52)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("project_card_${project.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(badgeColor)
                        .clickable { onEdit() },
                    contentAlignment = Alignment.Center
                ) {
                    if (itemLogoBitmap != null) {
                        Image(
                            bitmap = itemLogoBitmap,
                            contentDescription = project.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Panel: ${project.overlayTitle.ifBlank { project.name }} • ${project.canvasWidthDp}×${project.canvasHeightDp}dp",
                        color = Color(0xFF7B93B8),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("edit_project_${project.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Project options",
                        tint = Color(0xFF94A3B8)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    shape = RoundedCornerShape(14.dp),
                    containerColor = Color(0xFF0B1326),
                    border = BorderStroke(1.dp, Color(0xFF233559))
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Open",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onOpen()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Edit",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Duplicate",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Duplicate",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDuplicate()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Delete",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StarterProjectListCard(
    title: String,
    subtitle: String,
    badgeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = StudioCardBg,
        border = BorderStroke(1.dp, StudioCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(badgeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = title,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        color = StudioTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = null,
                tint = StudioTextSecondary
            )
        }
    }
}

/**
 * IMAGE 2, COLUMN 2: "Create New — What do you want to build today?"
 */
@Composable
private fun CreateNewHubScreenContent(
    onClose: () -> Unit,
    onSelectCreateNewApp: () -> Unit,
    onSelectCreateLuaScript: () -> Unit = {},
    onSelectUseTemplate: () -> Unit,
    onSelectImportProject: () -> Unit,
    onSelectAiMode: () -> Unit,
    onSelectMyApps: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Create New",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "What do you want to build today?",
                    color = StudioTextSecondary,
                    fontSize = 13.sp
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        HubOptionCard(
            title = "Create New App",
            subtitle = "Start building a new application from scratch",
            icon = Icons.Default.Add,
            iconBgColor = Color(0xFF2563EB),
            onClick = onSelectCreateNewApp,
            testTag = "hub_create_new_app_card"
        )

        HubOptionCard(
            title = "Lua Script",
            subtitle = "Create and run a .lua script with custom button logic (Saved in Download/lua/)",
            icon = Icons.Default.Code,
            iconBgColor = Color(0xFF10B981),
            onClick = onSelectCreateLuaScript,
            testTag = "hub_create_lua_script_card"
        )

        HubOptionCard(
            title = "Use Template",
            subtitle = "Choose from pre-built templates",
            icon = Icons.Default.DashboardCustomize,
            iconBgColor = Color(0xFF7C3AED),
            onClick = onSelectUseTemplate,
            testTag = "hub_use_template_card"
        )

        HubOptionCard(
            title = "Import Project",
            subtitle = "Import existing project from storage or Git",
            icon = Icons.Default.FileDownload,
            iconBgColor = Color(0xFF0D9488),
            onClick = onSelectImportProject,
            testTag = "hub_import_project_card"
        )

        HubOptionCard(
            title = "AI Mode",
            subtitle = "Let AI build your app with natural language",
            icon = Icons.Default.AutoAwesome,
            iconBgColor = Color(0xFFEA580C),
            onClick = onSelectAiMode,
            testTag = "hub_ai_mode_card"
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Quick Start",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        HubOptionCard(
            title = "My Apps",
            subtitle = "View all your created apps",
            icon = Icons.Default.Apps,
            iconBgColor = Color(0xFF0284C7),
            onClick = onSelectMyApps
        )

        HubOptionCard(
            title = "Community Templates",
            subtitle = "Explore community made templates",
            icon = Icons.Default.Token,
            iconBgColor = Color(0xFF7C3AED),
            onClick = onSelectUseTemplate
        )

        HubOptionCard(
            title = "Documentation",
            subtitle = "Learn and get help",
            icon = Icons.Default.Description,
            iconBgColor = Color(0xFF2563EB),
            onClick = onSelectCreateNewApp
        )
    }
}

@Composable
private fun HubOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBgColor: Color,
    onClick: () -> Unit,
    testTag: String? = null
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = StudioCardBg,
        border = BorderStroke(1.dp, StudioCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = StudioTextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Streamlined "Create New App" Screen + Permission Cards (Overlay Permission, Storage Permission & Grant Permissions)
 */
@Composable
private fun CreateNewAppWizardScreenContent(
    initialCreateMode: String = "MTDP",
    hasStoragePermission: Boolean = true,
    hasOverlayPermission: Boolean = true,
    onRequestStoragePermission: () -> Unit = {},
    onRequestOverlayPermission: () -> Unit = {},
    onBack: () -> Unit,
    onImportLogoUri: (Uri, (String) -> Unit) -> Unit,
    onCreateApp: (String, String, String, String) -> Unit
) {
    var createMode by remember(initialCreateMode) { mutableStateOf(initialCreateMode) }
    val isLuaMode = createMode == "LUA_SCRIPT"
    var appName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var selectedLogoPath by remember { mutableStateOf("") }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onImportLogoUri(uri) { savedPath ->
                selectedLogoPath = savedPath
            }
        }
    }

    val logoBitmap = remember(selectedLogoPath) {
        if (selectedLogoPath.isNotBlank()) {
            val f = File(selectedLogoPath)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
        } else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("launcher_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Column {
                Text(
                    text = "Create New App",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Fill in the details to create your new application",
                    color = StudioTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // Scrollable Form Body
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Mode Selector: MTDP vs Lua Script
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Select Project Mode",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (!isLuaMode) StudioIndigoPrimary.copy(alpha = 0.22f) else StudioCardBg,
                        border = BorderStroke(
                            width = if (!isLuaMode) 1.5.dp else 1.dp,
                            color = if (!isLuaMode) StudioIndigoAccent else StudioCardBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                createMode = "MTDP"
                                if (packageName.startsWith("lua.script.")) {
                                    val slug = appName.lowercase().replace(Regex("[^a-z0-9]"), "")
                                    packageName = if (slug.isNotEmpty()) "com.example.$slug" else ""
                                }
                            }
                            .testTag("create_mode_mtdp_option")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Android,
                                contentDescription = "MTDP",
                                tint = if (!isLuaMode) Color(0xFF818CF8) else StudioTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "MTDP",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "App / APK Builder",
                                    color = StudioTextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isLuaMode) Color(0xFF064E3B).copy(alpha = 0.45f) else StudioCardBg,
                        border = BorderStroke(
                            width = if (isLuaMode) 1.5.dp else 1.dp,
                            color = if (isLuaMode) Color(0xFF10B981) else StudioCardBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                createMode = "LUA_SCRIPT"
                                val slug = appName.lowercase().replace(Regex("[^a-z0-9]"), "").ifEmpty { "myscript" }
                                if (packageName.isBlank() || packageName.startsWith("com.example.")) {
                                    packageName = "lua.script.$slug"
                                }
                            }
                            .testTag("create_mode_lua_script_option")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "Lua Script",
                                tint = if (isLuaMode) Color(0xFF34D399) else StudioTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Lua Script",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Create & Run .lua",
                                    color = StudioTextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "1. Basic Information",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            // App Name / Script Name *
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Text(
                        if (isLuaMode) "Script Name " else "App Name ",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("*", color = Color(0xFFEF4444), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                DarkStudioTextField(
                    value = appName,
                    onValueChange = {
                        appName = it
                        val slug = it.lowercase().replace(Regex("[^a-z0-9]"), "")
                        if (isLuaMode) {
                            if (packageName.isBlank() || packageName.startsWith("lua.script.") || packageName.startsWith("com.example.")) {
                                packageName = if (slug.isNotEmpty()) "lua.script.$slug" else "lua.script.custom"
                            }
                        } else {
                            if (packageName.isBlank() || packageName.startsWith("com.example.") || packageName.startsWith("lua.script.")) {
                                packageName = if (slug.isNotEmpty()) "com.example.$slug" else ""
                            }
                        }
                    },
                    label = "",
                    placeholder = if (isLuaMode) "Enter your Lua script name" else "Enter your app name",
                    testTag = "new_project_name_input"
                )
                Text(
                    text = if (isLuaMode) "Saves inside Download/lua/<name>.lua" else "e.g. My Awesome App",
                    color = StudioTextSecondary,
                    fontSize = 11.sp
                )
            }

            // Package Name / Script Identifier *
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Text(
                        if (isLuaMode) "Script ID / Namespace " else "Package Name ",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("*", color = Color(0xFFEF4444), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                DarkStudioTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = "",
                    placeholder = if (isLuaMode) "lua.script.myscript" else "com.example.myapp",
                    testTag = "new_project_package_input"
                )
                Text(
                    text = if (isLuaMode) "Lua Script mode creates & runs a .lua script (not an APK)" else "Use lowercase letters, numbers and dots",
                    color = StudioTextSecondary,
                    fontSize = 11.sp
                )
            }

            // App Icon & Floating Panel Logo
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "App Icon",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            logoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .testTag("new_project_pick_logo_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF6366F1), Color(0xFF4338CA))
                                )
                            )
                            .border(BorderStroke(1.5.dp, Color(0xFF818CF8)), RoundedCornerShape(16.dp))
                            .testTag("new_project_logo_picker_box"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (logoBitmap != null) {
                            Image(
                                bitmap = logoBitmap,
                                contentDescription = "Selected App Icon",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Token,
                                contentDescription = "Default Cube Icon",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = if (selectedLogoPath.isBlank()) "Tap to change icon" else "Custom Icon Selected ✓",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Recommended size: 512×512",
                            color = StudioTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Bottom Cancel & Next -> Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, StudioCardBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("Cancel", color = Color.White, fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = {
                    val finalName = appName.trim().ifEmpty { if (isLuaMode) "My Lua Script" else "My App" }
                    val slug = finalName.lowercase().replace(Regex("[^a-z0-9]"), "").ifEmpty { if (isLuaMode) "luascript" else "myapp" }
                    val finalPkg = if (isLuaMode) {
                        val rawPkg = packageName.trim()
                        if (rawPkg.startsWith("lua.script", ignoreCase = true)) rawPkg else "lua.script.$slug"
                    } else {
                        packageName.trim().ifEmpty { "com.example.$slug" }
                    }
                    onCreateApp(finalName, finalPkg, finalName, selectedLogoPath)
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (isLuaMode) Color(0xFF10B981) else StudioIndigoPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("create_project_button")
            ) {
                Text("Next", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun StudioPermissionsSectionCard(
    hasOverlayPermission: Boolean,
    hasStoragePermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestStoragePermission: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card 1: Overlay Permission
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0A1328),
            border = BorderStroke(1.dp, Color(0xFF1E2F52)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRequestOverlayPermission)
                .testTag("welcome_grant_overlay_button")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF7C3AED), Color(0xFF4F46E5))
                                )
                            )
                            .border(BorderStroke(1.dp, Color(0xFF818CF8)), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Overlay Permission",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Overlay Permission",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Display floating window over other apps",
                            color = Color(0xFF7B93B8),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (hasOverlayPermission) Color(0xFF064E3B) else Color(0xFF2E1B5B),
                        border = BorderStroke(
                            1.dp,
                            if (hasOverlayPermission) Color(0xFF10B981) else Color(0xFF7C3AED)
                        )
                    ) {
                        Text(
                            text = if (hasOverlayPermission) "Granted" else "Required",
                            color = if (hasOverlayPermission) Color(0xFF34D399) else Color(0xFFC4B5FD),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color(0xFF7B93B8),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Card 2: Storage Permission
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0A1328),
            border = BorderStroke(1.dp, Color(0xFF1E2F52)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRequestStoragePermission)
                .testTag("welcome_grant_storage_button")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF06B6D4), Color(0xFF2563EB))
                                )
                            )
                            .border(BorderStroke(1.dp, Color(0xFF38BDF8)), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Storage Permission",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Storage Permission",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Read and write files on device storage",
                            color = Color(0xFF7B93B8),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (hasStoragePermission) Color(0xFF064E3B) else Color(0xFF0C2D57),
                        border = BorderStroke(
                            1.dp,
                            if (hasStoragePermission) Color(0xFF10B981) else Color(0xFF3B82F6)
                        )
                    ) {
                        Text(
                            text = if (hasStoragePermission) "Granted" else "Required",
                            color = if (hasStoragePermission) Color(0xFF34D399) else Color(0xFF93C5FD),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color(0xFF7B93B8),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Grant Permissions Button
        Button(
            onClick = {
                if (!hasOverlayPermission) {
                    onRequestOverlayPermission()
                } else if (!hasStoragePermission) {
                    onRequestStoragePermission()
                } else {
                    onRequestOverlayPermission()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF5B4DFF),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("grant_permissions_button")
        ) {
            Text(
                text = "Grant Permissions",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * IMAGE 1 (`IMG_20260929_230819.png`): "Import Project — Bring your existing project into App Studio"
 */
@Composable
private fun ImportProjectScreenContent(
    onBack: () -> Unit,
    onSyncErrorStudioFolder: () -> Unit = {},
    onImportFromDevice: () -> Unit,
    onImportFromGitHub: () -> Unit,
    onImportFromUrl: () -> Unit,
    onImportFromCloud: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Column {
                Text(
                    text = "Import Project",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "All projects are saved in Download/ERROR STUDIO",
                    color = StudioTextSecondary,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ImportSourceCard(
                title = "Download/ERROR STUDIO Folder",
                subtitle = "Auto-scan & restore all projects from Download/ERROR STUDIO",
                icon = Icons.Default.FolderOpen,
                iconBgColor = Color(0xFF10B981),
                onClick = onSyncErrorStudioFolder,
                testTag = "import_sync_error_studio_folder_card"
            )

            ImportSourceCard(
                title = "From Device (Pick Project File)",
                subtitle = "Select a project .json file from Download/ERROR STUDIO",
                icon = Icons.Default.FileDownload,
                iconBgColor = Color(0xFF0F766E),
                onClick = onImportFromDevice,
                testTag = "import_from_device_card"
            )

            ImportSourceCard(
                title = "From GitHub",
                subtitle = "Import from Git repositories",
                icon = Icons.Default.Code,
                iconBgColor = Color(0xFF581C87),
                onClick = onImportFromGitHub,
                testTag = "import_from_github_card"
            )

            ImportSourceCard(
                title = "From URL",
                subtitle = "Import from a public repository",
                icon = Icons.Default.Explore,
                iconBgColor = Color(0xFF1D4ED8),
                onClick = onImportFromUrl,
                testTag = "import_from_url_card"
            )

            ImportSourceCard(
                title = "From Cloud",
                subtitle = "Import from cloud storage",
                icon = Icons.Default.CloudDownload,
                iconBgColor = Color(0xFF2563EB),
                onClick = onImportFromCloud,
                testTag = "import_from_cloud_card"
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Supported Formats",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SupportedFormatBox(
                    label = "ZIP",
                    icon = Icons.Default.Description,
                    iconTint = Color(0xFF38BDF8),
                    onClick = onImportFromDevice,
                    modifier = Modifier.weight(1f)
                )
                SupportedFormatBox(
                    label = "Git",
                    icon = Icons.Default.AccountTree,
                    iconTint = Color(0xFFEF4444),
                    onClick = onImportFromGitHub,
                    modifier = Modifier.weight(1f)
                )
                SupportedFormatBox(
                    label = "GitHub",
                    icon = Icons.Default.Code,
                    iconTint = Color.White,
                    onClick = onImportFromGitHub,
                    modifier = Modifier.weight(1f)
                )
                SupportedFormatBox(
                    label = "Folder",
                    icon = Icons.Default.Folder,
                    iconTint = Color(0xFFFBBF24),
                    onClick = onImportFromDevice,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onImportFromDevice,
            colors = ButtonDefaults.buttonColors(containerColor = StudioIndigoPrimary),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("import_choose_source_button")
        ) {
            Icon(
                imageVector = Icons.Default.CreateNewFolder,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Choose Source",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ImportSourceCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBgColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = StudioCardBg,
        border = BorderStroke(1.dp, StudioCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = StudioTextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun SupportedFormatBox(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = StudioCardBg,
        border = BorderStroke(1.dp, StudioCardBorder),
        modifier = modifier
            .height(78.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                color = StudioTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * IMAGE 2, COLUMN 5: "Saved Projects" Screen with All / Recent / Starred & Search
 */
@Composable
private fun SavedProjectsScreenContent(
    projects: List<StudioProjectEntity>,
    onBack: () -> Unit,
    onCreateNewApp: () -> Unit,
    onOpenProject: (StudioProjectEntity) -> Unit,
    onEditProject: (StudioProjectEntity) -> Unit,
    onDuplicateProject: (StudioProjectEntity) -> Unit = {},
    onDeleteProject: (Long) -> Unit,
    onCreateStarterProject: (String, String, String) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    val filteredProjects = remember(projects, searchQuery, selectedFilter) {
        val base = if (searchQuery.isBlank()) {
            projects
        } else {
            projects.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                    it.overlayTitle.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
        when (selectedFilter) {
            "Recent" -> base.take(5)
            "Starred" -> base.filter { it.autoFixSize }
            else -> base
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Saved Projects",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            IconButton(onClick = onCreateNewApp) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create New App",
                    tint = Color.White
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Filter Chips: All | Recent | Starred
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Recent", "Starred").forEach { tab ->
                val isSelected = selectedFilter == tab
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) Color(0xFF4F46E5) else Color(0xFF0A1328),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF818CF8) else Color(0xFF1E2F52)),
                    modifier = Modifier.clickable { selectedFilter = tab }
                ) {
                    Text(
                        text = tab,
                        color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Search Bar ("Search projects...")
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text("Search projects...", color = Color(0xFF7B93B8), fontSize = 13.sp)
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF7B93B8))
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF0A1328),
                unfocusedContainerColor = Color(0xFF0A1328),
                focusedBorderColor = Color(0xFF3B82F6),
                unfocusedBorderColor = Color(0xFF1E2F52),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (filteredProjects.isNotEmpty()) {
                items(filteredProjects, key = { it.id }) { project ->
                    DarkProjectListCard(
                        project = project,
                        onOpen = { onOpenProject(project) },
                        onEdit = { onEditProject(project) },
                        onDuplicate = { onDuplicateProject(project) },
                        onDelete = { onDeleteProject(project.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplatesScreenContent(
    onBack: () -> Unit,
    onUseTemplate: (String, String, String) -> Unit
) {
    val templates = listOf(
        Triple("Floating Mod Menu Pro", "com.appstudio.modmenu", "Mod Menu Panel"),
        Triple("FPS Overlay HUD", "com.appstudio.fpshud", "FPS Overlay Panel"),
        Triple("Game Macro Controller", "com.appstudio.macro", "Macro Control Panel"),
        Triple("Material Notes App", "com.appstudio.notes", "Quick Notes Panel"),
        Triple("Audio Equalizer Float", "com.appstudio.equalizer", "EQ Boost Panel")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = "App Studio Templates",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(templates) { (title, pkg, panelTitle) ->
                HubOptionCard(
                    title = title,
                    subtitle = "Package: $pkg • Panel: $panelTitle",
                    icon = Icons.Default.DashboardCustomize,
                    iconBgColor = StudioIndigoPrimary,
                    onClick = { onUseTemplate(title, pkg, panelTitle) }
                )
            }
        }
    }
}

@Composable
private fun AppStudioBottomNavigationBar(
    currentSubScreen: AppStudioSubScreen,
    onSelectSubScreen: (AppStudioSubScreen) -> Unit,
    onOpenAiStudio: () -> Unit
) {
    NavigationBar(
        containerColor = Color(0xFF0C1020),
        contentColor = Color.White,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = currentSubScreen == AppStudioSubScreen.HOME,
            onClick = { onSelectSubScreen(AppStudioSubScreen.HOME) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF6366F1),
                selectedTextColor = Color(0xFF6366F1),
                unselectedIconColor = StudioTextSecondary,
                unselectedTextColor = StudioTextSecondary,
                indicatorColor = Color(0xFF1E293B)
            )
        )
        NavigationBarItem(
            selected = currentSubScreen == AppStudioSubScreen.SAVED_PROJECTS,
            onClick = { onSelectSubScreen(AppStudioSubScreen.SAVED_PROJECTS) },
            icon = { Icon(Icons.Default.Folder, contentDescription = "Projects") },
            label = { Text("Projects", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF6366F1),
                selectedTextColor = Color(0xFF6366F1),
                unselectedIconColor = StudioTextSecondary,
                unselectedTextColor = StudioTextSecondary,
                indicatorColor = Color(0xFF1E293B)
            )
        )
        NavigationBarItem(
            selected = false,
            onClick = onOpenAiStudio,
            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI Mode") },
            label = { Text("AI Mode", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                unselectedIconColor = StudioTextSecondary,
                unselectedTextColor = StudioTextSecondary
            ),
            modifier = Modifier.testTag("launcher_open_ai_studio_button")
        )
        NavigationBarItem(
            selected = currentSubScreen == AppStudioSubScreen.TEMPLATES,
            onClick = { onSelectSubScreen(AppStudioSubScreen.TEMPLATES) },
            icon = { Icon(Icons.Default.DashboardCustomize, contentDescription = "Templates") },
            label = { Text("Templates", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF6366F1),
                selectedTextColor = Color(0xFF6366F1),
                unselectedIconColor = StudioTextSecondary,
                unselectedTextColor = StudioTextSecondary,
                indicatorColor = Color(0xFF1E293B)
            )
        )
        NavigationBarItem(
            selected = currentSubScreen == AppStudioSubScreen.IMPORT_PROJECT,
            onClick = { onSelectSubScreen(AppStudioSubScreen.IMPORT_PROJECT) },
            icon = { Icon(Icons.Default.MoreHoriz, contentDescription = "More") },
            label = { Text("More", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF6366F1),
                selectedTextColor = Color(0xFF6366F1),
                unselectedIconColor = StudioTextSecondary,
                unselectedTextColor = StudioTextSecondary,
                indicatorColor = Color(0xFF1E293B)
            )
        )
    }
}

@Composable
private fun DarkStudioTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    testTag: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = if (label.isNotBlank()) {
            { Text(label, color = StudioTextSecondary, fontSize = 12.sp) }
        } else null,
        placeholder = {
            Text(placeholder, color = StudioTextSecondary, fontSize = 13.sp)
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = StudioCardBg,
            unfocusedContainerColor = StudioCardBg,
            focusedBorderColor = StudioIndigoAccent,
            unfocusedBorderColor = StudioCardBorder,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}
