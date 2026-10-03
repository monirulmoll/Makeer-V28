package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.LuaCustomWidgetEngine

/**
 * Dedicated Full Screen Settings Screen.
 *
 * Replaces the previous window/dialog with a full, dedicated settings screen.
 * Contains:
 * 1. Lua Script Pre-Execution Code (runs first before Lua script starts; blank if user leaves empty)
 * 2. Default Button Logic (for new buttons in Lua Script mode)
 * 3. AI Studio / Termux Server config shortcut
 * 4. System permissions status
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioSettingsScreenContent(
    defaultButtonLogic: String,
    onSaveDefaultButtonLogic: (String) -> Unit,
    luaPreExecutionCode: String,
    onSaveLuaPreExecutionCode: (String) -> Unit,
    hasStoragePermission: Boolean,
    hasOverlayPermission: Boolean,
    onRefreshPermissions: () -> Unit,
    onOpenAiStudio: () -> Unit,
    onClose: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Studio Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Global Studio & Lua Script Settings",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("settings_screen_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Home",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0B1220),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF070B14)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Info Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.15f))
                            .border(BorderStroke(1.dp, Color(0xFF10B981)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Studio Configuration",
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Lua script startup code, default button actions, and system runtime options.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // SECTION 1: LUA SCRIPT ENGINE SETTINGS (Dedicated for Lua Script mode)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0D1527),
                border = BorderStroke(1.5.dp, Color(0xFF10B981).copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Lua Script Engine Settings",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, Color(0xFF10B981))
                        ) {
                            Text(
                                text = "Lua Script Mode Only",
                                color = Color(0xFF6EE7B7),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1E2D48))

                    // 1. PRE-EXECUTION LUA CODE (Sabse pehle execute hone wala code)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "1. Pre-Execution Lua Code",
                                color = Color(0xFF38BDF8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "(Runs First Before Script)",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            text = "Ye code generated Lua script run hone ke sabse pehle execute hoga (before gg.setVisible & event loop). Agar yahan kuch nahi likha to ye blank rahega.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )

                        WidgetDirectEditCodeBox(
                            title = "Pre-Execution Startup Code",
                            subtitle = "Executes before floating panel & event loop",
                            code = luaPreExecutionCode,
                            onCodeChange = onSaveLuaPreExecutionCode,
                            placeholder = "-- Enter startup code here (runs first)\n-- e.g. gg.clearResults()\n-- gg.toast(\"Initializing...\")\n-- If blank, no code is injected.",
                            widgetName = "Pre-Execution",
                            minLines = 4,
                            maxLines = 8,
                            initialDirectEdit = true,
                            variablesHint = "Runs first at the top of script. Blank = none.",
                            testTagPrefix = "settings_lua_pre_execution_code"
                        )
                    }

                    HorizontalDivider(color = Color(0xFF1E2D48))

                    // 2. DEFAULT BUTTON LOGIC
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "2. Default Button Logic",
                                color = Color(0xFF6EE7B7),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(
                                onClick = {
                                    onSaveDefaultButtonLogic(LuaCustomWidgetEngine.FALLBACK_DEFAULT_BUTTON_LOGIC)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("settings_reset_default_button_logic")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = null,
                                    tint = Color(0xFF93C5FD),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Reset Default",
                                    color = Color(0xFF93C5FD),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = "Har naya Button widget (ya custom button jisme explicit code na ho) automatically is Lua logic ke sath pre-fill hoga.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )

                        WidgetDirectEditCodeBox(
                            title = "Default Button Click Logic",
                            subtitle = "Default click action for new buttons",
                            code = defaultButtonLogic,
                            onCodeChange = onSaveDefaultButtonLogic,
                            placeholder = LuaCustomWidgetEngine.FALLBACK_DEFAULT_BUTTON_LOGIC,
                            widgetName = "Default Button",
                            minLines = 3,
                            maxLines = 6,
                            initialDirectEdit = true,
                            variablesHint = "gg.toast(...) or any Lua GameGuardian code",
                            testTagPrefix = "settings_default_button_logic"
                        )
                    }
                }
            }

            // SECTION 2: AI STUDIO (ONLINE MODE) SETTINGS
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "AI Studio & Termux Server",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Configure Termux Ollama/GGUF model server host, port, and AI prompt blueprint generation.",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp
                    )
                    Button(
                        onClick = onOpenAiStudio,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4338CA)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_open_ai_workspace_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Open AI Mode Server Settings",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // SECTION 3: PERMISSIONS & PRIVILEGES
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "System Permissions Status",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Storage Permission",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp
                        )
                        Text(
                            text = if (hasStoragePermission) "Granted ✓" else "Not Granted ✕",
                            color = if (hasStoragePermission) Color(0xFF34D399) else Color(0xFFF87171),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Overlay Permission",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp
                        )
                        Text(
                            text = if (hasOverlayPermission) "Granted ✓" else "Not Granted ✕",
                            color = if (hasOverlayPermission) Color(0xFF34D399) else Color(0xFFF87171),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
