package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Reusable Code Box for any widget code / Lua logic.
 *
 * Supports:
 * - "Direct Edit Code: ON" (user types directly into the inline textfield)
 * - "Direct Edit Code: OFF" (inline editing locked/read-only, tapping pencil or box opens full screen)
 * - Prominent ✏️ Pencil Icon on header: clicking it opens Full Screen Code Editor!
 */
@Composable
fun WidgetDirectEditCodeBox(
    title: String,
    code: String,
    onCodeChange: (String) -> Unit,
    placeholder: String = "Enter code here...",
    widgetName: String = "",
    subtitle: String = "Lua Script Logic",
    minLines: Int = 2,
    maxLines: Int = 5,
    titleColor: Color = Color(0xFF6EE7B7),
    borderColor: Color = Color(0xFF10B981).copy(alpha = 0.5f),
    initialDirectEdit: Boolean = false,
    variablesHint: String = "Variables: label, state, value, text",
    bottomActionRow: @Composable (() -> Unit)? = null,
    testTagPrefix: String = "widget_code"
) {
    var isDirectEditOn by rememberSaveable(widgetName, title) { mutableStateOf(initialDirectEdit) }
    var showFullScreen by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF070F1E))
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(12.dp))
            .padding(10.dp)
            .testTag("${testTagPrefix}_container")
    ) {
        // Header with Title + [ Direct Edit: ON/OFF ] Toggle + ✏️ Pencil Fullscreen Icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    tint = titleColor,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = title,
                    color = titleColor,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Direct Edit ON / OFF Switch Pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDirectEditOn) Color(0xFF065F46) else Color(0xFF1E293B),
                    border = BorderStroke(
                        1.dp,
                        if (isDirectEditOn) Color(0xFF34D399) else Color(0xFF475569)
                    ),
                    modifier = Modifier
                        .clickable { isDirectEditOn = !isDirectEditOn }
                        .testTag("${testTagPrefix}_direct_edit_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isDirectEditOn) Color(0xFF34D399) else Color(0xFF94A3B8))
                        )
                        Text(
                            text = if (isDirectEditOn) "Direct Edit: ON" else "Direct Edit: OFF",
                            color = if (isDirectEditOn) Color(0xFF6EE7B7) else Color(0xFFCBD5E1),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // ✏️ Pencil Icon Button: Clicking opens Full Screen Editor
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F2342),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                    modifier = Modifier
                        .clickable { showFullScreen = true }
                        .testTag("${testTagPrefix}_pencil_fullscreen_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit in Full Screen",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Full Screen",
                            color = Color(0xFFE0F2FE),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // Code Editor Body: Inline editable when Direct Edit is ON, or Preview / Tap-to-edit when Direct Edit is OFF
        if (isDirectEditOn) {
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                placeholder = {
                    Text(
                        text = placeholder,
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                },
                minLines = minLines,
                maxLines = maxLines,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF040814),
                    unfocusedContainerColor = Color(0xFF040814),
                    focusedBorderColor = Color(0xFF10B981),
                    unfocusedBorderColor = Color(0xFF1E293B),
                    focusedTextColor = Color(0xFFE2E8F0),
                    unfocusedTextColor = Color(0xFFE2E8F0)
                ),
                textStyle = TextStyle(
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${testTagPrefix}_inline_input")
            )
        } else {
            // Direct Edit is OFF: locked preview box. Clicking pencil or anywhere on this box opens Full Screen!
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF040814))
                    .border(BorderStroke(1.dp, Color(0xFF1E293B)), RoundedCornerShape(10.dp))
                    .clickable { showFullScreen = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("${testTagPrefix}_preview_box")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (code.isNotBlank()) {
                        Text(
                            text = code,
                            color = Color(0xFFE2E8F0),
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp,
                            maxLines = maxLines,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = placeholder,
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Direct edit OFF • Tap ✏️ to edit in Full Screen",
                            color = Color(0xFF38BDF8),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Bottom Variable hint and/or test trigger buttons
        if (variablesHint.isNotBlank() || bottomActionRow != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (variablesHint.isNotBlank()) {
                    Text(
                        text = variablesHint,
                        color = Color(0xFF64748B),
                        fontSize = 9.5.sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                bottomActionRow?.invoke()
            }
        }
    }

    // Full Screen Code Editor Dialog
    if (showFullScreen) {
        FullScreenWidgetCodeEditorDialog(
            widgetName = widgetName,
            title = title,
            subtitle = subtitle,
            initialCode = code,
            onDismiss = { showFullScreen = false },
            onSave = { updatedCode ->
                onCodeChange(updatedCode)
                showFullScreen = false
            }
        )
    }
}

/**
 * Full-screen dedicated code editor screen with:
 * - Line numbers column
 * - Quick Lua snippets (gg.toast, gg.alert, gg.sleep, etc.)
 * - Monospace text editor with large comfortable area
 * - Undo/Redo/Clear/Copy
 * - Prominent [ Save & Done ] action
 */
@Composable
fun FullScreenWidgetCodeEditorDialog(
    widgetName: String,
    title: String,
    subtitle: String = "Lua Script Code",
    initialCode: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var editorCode by remember(initialCode) { mutableStateOf(initialCode) }
    val clipboardManager = LocalClipboardManager.current
    var copyStatus by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
            color = Color(0xFF030712)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Top App Bar
                Surface(
                    color = Color(0xFF0B1324),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("fullscreen_editor_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                            Column {
                                Text(
                                    text = if (widgetName.isNotBlank()) "$widgetName • $title" else title,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = subtitle,
                                    color = Color(0xFF34D399),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Copy button
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(editorCode))
                                    copyStatus = "Copied!"
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy code",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Clear button
                            if (editorCode.isNotBlank()) {
                                IconButton(
                                    onClick = { editorCode = "" },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = Color(0xFFF87171),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Save & Done button
                            Button(
                                onClick = { onSave(editorCode) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("fullscreen_editor_save_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = "Save & Done",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }

                // Quick Lua Snippets bar
                val snippets = listOf(
                    "gg.toast(\"...\")" to "gg.toast(\"Hello\")",
                    "gg.alert(\"...\")" to "gg.alert(\"Title\", \"Message\", \"OK\")",
                    "gg.sleep(100)" to "gg.sleep(100)",
                    "gg.choice({...})" to "local choice = gg.choice({\"Option 1\", \"Option 2\"})\nif choice == 1 then\n    \nend",
                    "if state then ... end" to "if state then\n    gg.toast(\"ON\")\nelse\n    gg.toast(\"OFF\")\nend",
                    "print(...)" to "print(\"DEBUG: \" .. tostring(label))",
                    "gg.clearResults()" to "gg.clearResults()"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quick Snippets:",
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    snippets.forEach { (label, codeSnippet) ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E293B),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.clickable {
                                editorCode = if (editorCode.isBlank()) {
                                    codeSnippet
                                } else {
                                    editorCode + "\n" + codeSnippet
                                }
                            }
                        ) {
                            Text(
                                text = label,
                                color = Color(0xFF38BDF8),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                if (copyStatus != null) {
                    Text(
                        text = copyStatus!!,
                        color = Color(0xFF34D399),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }

                // Main Editor Area with Line Numbers
                val lines = editorCode.lines()
                val lineCount = lines.size.coerceAtLeast(1)

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF020617))
                ) {
                    // Line numbers gutter
                    Column(
                        modifier = Modifier
                            .width(38.dp)
                            .fillMaxHeight()
                            .background(Color(0xFF070F1E))
                            .border(BorderStroke(0.5.dp, Color(0xFF1E293B)))
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        for (i in 1..lineCount) {
                            Text(
                                text = "$i",
                                color = Color(0xFF475569),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 19.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Main Code Field
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        BasicTextField(
                            value = editorCode,
                            onValueChange = {
                                editorCode = it
                                copyStatus = null
                            },
                            textStyle = TextStyle(
                                color = Color(0xFFF1F5F9),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.5.sp,
                                lineHeight = 19.sp
                            ),
                            cursorBrush = SolidColor(Color(0xFF38BDF8)),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("fullscreen_code_editor_field")
                        )
                    }
                }

                // Bottom Status Bar
                Surface(
                    color = Color(0xFF0B1324),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Variables: label, state, value, text | gg.*",
                            color = Color(0xFF64748B),
                            fontSize = 10.5.sp
                        )
                        Text(
                            text = "Lines: $lineCount • Chars: ${editorCode.length}",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
