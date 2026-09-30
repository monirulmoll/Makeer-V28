package com.example.ui

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.LocalConfigStateWriter

@Composable
fun TargetWriteErrorShizukuDialog(
    report: LocalConfigStateWriter.WriteDiagnosticReport,
    isShizukuReady: Boolean,
    isShizukuRunning: Boolean,
    shizukuStatusSummary: String,
    onConnectOrFixWithShizuku: () -> Unit,
    onGrantStoragePermission: () -> Unit,
    onRetryWrite: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFE2E8F0),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFDC2626)
                ) {
                    Text(
                        text = if (report.isRestrictedAndroidPath) {
                            "ANDROID ${Build.VERSION.SDK_INT} RESTRICTED PATH ERROR"
                        } else {
                            "TARGET FILE CHANGE FAILED"
                        },
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = "${report.whyFailedTitle} (${report.componentLabel})",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Target File Path:",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = report.targetFilePath,
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Text(
                    text = report.whyFailedDetail,
                    color = Color(0xFFE2E8F0),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF172033),
                    border = BorderStroke(1.dp, Color(0xFF263554)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "OS Kernel Reason: ${report.rawKernelError}",
                            color = Color(0xFFFCA5A5),
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Shizuku Bridge: $shizukuStatusSummary",
                            color = if (isShizukuReady) Color(0xFF6EE7B7) else Color(0xFFFCD34D),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Button(
                    onClick = onConnectOrFixWithShizuku,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2563EB),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("error_dialog_shizuku_fix_button")
                ) {
                    Text(
                        text = when {
                            isShizukuReady -> "⚡ Fix & Retry Target File Change with Shizuku"
                            isShizukuRunning -> "🛡️ Authorize Shizuku to Fix Restricted Path"
                            else -> "🚀 Use Shizuku to Fix Restricted Path Error"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                if (!report.hasAllFilesPermission) {
                    Button(
                        onClick = onGrantStoragePermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF059669),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("error_dialog_grant_storage_button")
                    ) {
                        Text(
                            text = "Grant All Files Access Permission",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onRetryWrite,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF334155),
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("error_dialog_retry_write_button")
            ) {
                Text("Retry Test Write", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("error_dialog_dismiss_button")
            ) {
                Text("Close", color = Color(0xFF94A3B8))
            }
        }
    )
}

@Composable
fun ShizukuAndTargetTestSection(
    isShizukuReady: Boolean,
    isShizukuRunning: Boolean,
    shizukuStatusSummary: String,
    onConnectShizuku: () -> Unit,
    onTestTargetPathFileChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = when {
                isShizukuReady -> Color(0xFF0D2824)
                isShizukuRunning -> Color(0xFF292012)
                else -> Color(0xFF1E293B)
            },
            border = BorderStroke(
                1.dp,
                when {
                    isShizukuReady -> Color(0xFF10B981)
                    isShizukuRunning -> Color(0xFFF59E0B)
                    else -> Color(0xFF6366F1)
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onConnectShizuku() }
                .testTag("shizuku_permission_row_button")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Shizuku (Android 15 Restricted Path Fix)",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isShizukuReady) {
                            "Active ($shizukuStatusSummary) — /Android/data & /obb unlocked"
                        } else if (isShizukuRunning) {
                            "Shizuku running — Tap to Authorize permission"
                        } else {
                            "Status: $shizukuStatusSummary — Tap to use Shizuku for restricted paths"
                        },
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isShizukuReady -> Color(0xFF10B981)
                        isShizukuRunning -> Color(0xFFD97706)
                        else -> Color(0xFF4F46E5)
                    }
                ) {
                    Text(
                        text = when {
                            isShizukuReady -> "SHIZUKU ✓"
                            isShizukuRunning -> "AUTHORIZE"
                            else -> "USE SHIZUKU"
                        },
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        OutlinedButton(
            onClick = onTestTargetPathFileChange,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("test_target_path_file_change_button")
        ) {
            Text(
                text = "⚡ Test Target Path File Change System",
                color = Color(0xFF38BDF8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
