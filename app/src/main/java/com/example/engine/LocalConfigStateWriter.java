package com.example.engine;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import androidx.core.content.ContextCompat;
import com.example.service.DynamicOverlayRegistry;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

public class LocalConfigStateWriter {
    private static volatile LocalConfigStateWriter instance;
    private final ExecutorService fileIoExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FloatConfig-FileWriter-IO");
        t.setPriority(5);
        return t;
    });
    private final Handler mainHandler = LocalConfigStateWriter.createSafeMainHandler();
    private final List<OnStateWriteListener> listeners = new CopyOnWriteArrayList<OnStateWriteListener>();
    private final ConfigParameterSpec.StateSnapshot currentState = new ConfigParameterSpec.StateSnapshot();
    private final Map<String, String> lastWrittenByWidget = new ConcurrentHashMap<String, String>();
    private volatile File activeFile;
    private volatile Context appContext;
    private volatile WriteDiagnosticReport lastDiagnosticReport;

    public static final class WriteDiagnosticReport {
        public final long timestampMs;
        public final boolean success;
        public final String componentLabel;
        public final String targetFilePath;
        public final String failureCategory;
        public final String whyFailedTitle;
        public final String whyFailedDetail;
        public final String rawKernelError;
        public final boolean isRestrictedAndroidPath;
        public final boolean requiresShizuku;
        public final boolean hasAllFilesPermission;
        public final boolean shizukuInstalled;
        public final boolean shizukuRunning;
        public final boolean shizukuAuthorized;
        public final boolean usedShizuku;
        public final Runnable retryAction;

        public WriteDiagnosticReport(
                boolean success,
                String componentLabel,
                String targetFilePath,
                String failureCategory,
                String whyFailedTitle,
                String whyFailedDetail,
                String rawKernelError,
                boolean isRestrictedAndroidPath,
                boolean requiresShizuku,
                boolean hasAllFilesPermission,
                boolean shizukuInstalled,
                boolean shizukuRunning,
                boolean shizukuAuthorized,
                boolean usedShizuku,
                Runnable retryAction
        ) {
            this.timestampMs = System.currentTimeMillis();
            this.success = success;
            this.componentLabel = componentLabel != null ? componentLabel : "Float Option";
            this.targetFilePath = targetFilePath != null ? targetFilePath : "";
            this.failureCategory = failureCategory != null ? failureCategory : "UNKNOWN";
            this.whyFailedTitle = whyFailedTitle != null ? whyFailedTitle : "";
            this.whyFailedDetail = whyFailedDetail != null ? whyFailedDetail : "";
            this.rawKernelError = rawKernelError != null ? rawKernelError : "";
            this.isRestrictedAndroidPath = isRestrictedAndroidPath;
            this.requiresShizuku = requiresShizuku;
            this.hasAllFilesPermission = hasAllFilesPermission;
            this.shizukuInstalled = shizukuInstalled;
            this.shizukuRunning = shizukuRunning;
            this.shizukuAuthorized = shizukuAuthorized;
            this.usedShizuku = usedShizuku;
            this.retryAction = retryAction;
        }
    }

    private static Handler createSafeMainHandler() {
        try {
            Looper looper = Looper.getMainLooper();
            return looper != null ? new Handler(looper) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static LocalConfigStateWriter getInstance() {
        if (instance != null) return instance;
        synchronized (LocalConfigStateWriter.class) {
            if (instance != null) return instance;
            instance = new LocalConfigStateWriter();
            return instance;
        }
    }

    public void bindAppContext(Context context) {
        if (context != null) {
            this.appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        }
    }

    public Context getBoundAppContext() {
        return this.appContext;
    }

    public WriteDiagnosticReport getLastDiagnosticReport() {
        return this.lastDiagnosticReport;
    }

    public void clearLastDiagnosticReport() {
        this.lastDiagnosticReport = null;
    }

    public static boolean hasStoragePermissionGranted(Context context) {
        if (context == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                if (Environment.isExternalStorageManager()) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
            try {
                int mode;
                AppOpsManager appOps = (AppOpsManager) context.getSystemService("appops");
                if (appOps != null && (mode = appOps.unsafeCheckOpNoThrow("android:manage_external_storage", Process.myUid(), context.getPackageName())) == 0) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
            try {
                if (Environment.isExternalStorageLegacy()) {
                    boolean hasWrite = ContextCompat.checkSelfPermission(context, "android.permission.WRITE_EXTERNAL_STORAGE") == 0;
                    if (hasWrite) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {
            }
            return false;
        }
        try {
            boolean hasWrite = ContextCompat.checkSelfPermission(context, "android.permission.WRITE_EXTERNAL_STORAGE") == 0;
            boolean hasRead = ContextCompat.checkSelfPermission(context, "android.permission.READ_EXTERNAL_STORAGE") == 0;
            if (hasWrite && hasRead) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static void requestStoragePermission(Context context) {
        if (context == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Intent intent = new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION", Uri.parse("package:" + context.getPackageName()));
                intent.addFlags(0x10000000);
                context.startActivity(intent);
                return;
            } catch (Throwable intent) {
                try {
                    Intent fallback = new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION");
                    fallback.addFlags(0x10000000);
                    context.startActivity(fallback);
                    return;
                } catch (Throwable ignored) {
                }
            }
        }
        try {
            Intent appDetails = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS", Uri.parse("package:" + context.getPackageName()));
            appDetails.addFlags(0x10000000);
            context.startActivity(appDetails);
        } catch (Throwable ignored) {
        }
    }

    public static void requestOverlayPermission(Context context) {
        if (context == null) {
            return;
        }
        try {
            Intent intent = new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION", Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(0x10000000);
            context.startActivity(intent);
            return;
        } catch (Throwable intent) {
            try {
                Intent fallback = new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION");
                fallback.addFlags(0x10000000);
                context.startActivity(fallback);
            } catch (Throwable ignored) {
            }
        }
    }

    private LocalConfigStateWriter() {
    }

    public void addListener(OnStateWriteListener listener) {
        if (listener != null && !this.listeners.contains(listener)) {
            this.listeners.add(listener);
        }
    }

    public void removeListener(OnStateWriteListener listener) {
        this.listeners.remove(listener);
    }

    public synchronized void bindTargetFile(File targetFile, String operatingMode) {
        this.activeFile = targetFile;
        this.currentState.targetFilePath = targetFile.getAbsolutePath();
        this.currentState.operatingMode = operatingMode;
    }

    public synchronized File getActiveFile() {
        return this.activeFile;
    }

    public synchronized ConfigParameterSpec.StateSnapshot getCurrentSnapshot() {
        return this.currentState.copy();
    }

    /**
     * Triggered when the Float option is turned ON (both in Studio Error and in the compiled APK).
     * Runs the target path file-change testing & initialization system across all configured overlay widgets.
     * - If a widget is currently ON, applies its file change to the target path immediately.
     * - For every configured target path, verifies that the file on the target path can be modified/created
     *   (automatically using Shizuku if the path is restricted on Android 13/14/15+).
     * - If any target path fails to change, dispatches a full WriteDiagnosticReport to show the Error Dialog
     *   explaining why it failed and how to fix it with Shizuku.
     */
    public void runFloatStartupTargetTestAndApplyAsync(
            final Context context,
            final File fallbackDir,
            final List<DynamicOverlayRegistry.OverlayItemSpec> specs
    ) {
        if (context != null) {
            this.bindAppContext(context);
        }
        this.fileIoExecutor.execute(() -> {
            Context ctx = context != null ? context : this.appContext;
            if (ctx != null) {
                ShizukuPrivilegeBridge.probeShizukuBinder(ctx);
            }
            List<DynamicOverlayRegistry.OverlayItemSpec> items = specs != null ? specs : DynamicOverlayRegistry.getActiveItems();
            if (items == null || items.isEmpty()) {
                return;
            }
            Set<String> testedPaths = new LinkedHashSet<String>();
            for (DynamicOverlayRegistry.OverlayItemSpec spec : items) {
                if (spec == null || "LINK".equalsIgnoreCase(spec.type)) continue;
                String rawTarget = spec.targetFilePath != null ? spec.targetFilePath.trim() : "";
                if (rawTarget.isEmpty()) continue;

                boolean isActive;
                if ("SLIDER".equalsIgnoreCase(spec.type)) {
                    int v = 0;
                    try {
                        v = Integer.parseInt(spec.currentValue != null ? spec.currentValue.trim() : "0");
                    } catch (Exception ignored) {
                    }
                    isActive = v > 0;
                } else {
                    isActive = "1".equals(spec.currentValue) || "true".equalsIgnoreCase(spec.currentValue);
                }

                File resolvedTarget = this.resolveTargetFile(fallbackDir, rawTarget);
                String absPath = resolvedTarget.getAbsolutePath();

                if (isActive) {
                    String payload = isActive ? spec.onPayloadHex : spec.offPayloadHex;
                    this.applyWidgetPatchSync(
                            fallbackDir,
                            "widget_" + spec.id,
                            spec.type,
                            spec.targetFilePath,
                            spec.byteOffsetHex,
                            spec.offPayloadHex,
                            spec.onPayloadHex,
                            payload,
                            true,
                            spec.label != null ? spec.label : "Float Option",
                            spec.customImagePath
                    );
                    testedPaths.add(absPath);
                } else if (!testedPaths.contains(absPath)) {
                    testedPaths.add(absPath);
                    this.verifyOrTestTargetPathWritableInternal(
                            ctx,
                            fallbackDir,
                            spec.label != null && !spec.label.trim().isEmpty() ? spec.label + " (Float Test)" : "Float Target Test",
                            spec.targetFilePath,
                            spec.byteOffsetHex,
                            spec.offPayloadHex,
                            spec.onPayloadHex,
                            spec.customImagePath
                    );
                }
            }
        });
    }

    /**
     * Explicitly tests changing/writing the target path file and triggers either success telemetry
     * or a detailed Error Dialog report explaining why it failed and how to use Shizuku.
     */
    public void testTargetPathFileChangeAsync(
            final Context context,
            final File fallbackDir,
            final String componentLabel,
            final String targetFilePath,
            final String byteOffsetHex,
            final String originalValue,
            final String changeValue,
            final String customSourceFilePath
    ) {
        if (context != null) {
            this.bindAppContext(context);
        }
        this.fileIoExecutor.execute(() -> {
            Context ctx = context != null ? context : this.appContext;
            if (ctx != null) {
                ShizukuPrivilegeBridge.probeShizukuBinder(ctx);
            }
            this.applyWidgetPatchSync(
                    fallbackDir,
                    "test_" + (componentLabel != null ? componentLabel : "target"),
                    "TOGGLE",
                    targetFilePath,
                    byteOffsetHex,
                    originalValue,
                    changeValue,
                    changeValue != null && !changeValue.isEmpty() ? changeValue : "0x01",
                    true,
                    componentLabel != null ? componentLabel : "Target Path Test",
                    customSourceFilePath
            );
        });
    }

    private boolean verifyOrTestTargetPathWritableInternal(
            final Context ctx,
            final File fallbackDir,
            final String label,
            final String rawTargetPath,
            final String byteOffsetHex,
            final String offPayloadHex,
            final String onPayloadHex,
            final String customSourcePath
    ) {
        long startNs = System.nanoTime();
        File target = this.resolveTargetFile(fallbackDir, rawTargetPath);
        String absPath = target.getAbsolutePath();
        boolean isRestricted = ShizukuPrivilegeBridge.isRestrictedAndroidPath(absPath);
        Runnable retryTask = () -> this.testTargetPathFileChangeAsync(
                ctx, fallbackDir, label, rawTargetPath, byteOffsetHex, offPayloadHex, onPayloadHex, customSourcePath
        );

        // 1. Try direct non-destructive write check or initial state write
        try {
            if (!isRestricted || (target.exists() && target.canWrite())) {
                File parent = target.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                if (target.exists() && target.isFile()) {
                    try (RandomAccessFile raf = new RandomAccessFile(target, "rw")) {
                        long len = raf.length();
                        if (len > 0L) {
                            raf.seek(0L);
                            int b = raf.read();
                            raf.seek(0L);
                            raf.write(b);
                        }
                    }
                    long elapsedUs = (System.nanoTime() - startNs) / 1000L;
                    this.currentState.targetFilePath = absPath;
                    this.currentState.rawTextContent = "Verified writable (" + target.getName() + ")";
                    ConfigParameterSpec.StateSnapshot snap = this.currentState.copy();
                    this.notifyWriteSuccess(label, this.parseOffsetString(byteOffsetHex), "READY", "TEST_OK (" + target.getName() + ")", elapsedUs, snap);
                    return true;
                } else {
                    // Target file does not exist yet: create initial test target file with OFF/default state
                    boolean ok = this.applyWidgetPatchSync(
                            fallbackDir,
                            "init_" + label,
                            "TOGGLE",
                            rawTargetPath,
                            byteOffsetHex,
                            offPayloadHex,
                            onPayloadHex,
                            offPayloadHex != null && !offPayloadHex.isEmpty() ? offPayloadHex : "0x00",
                            false,
                            label,
                            null
                    );
                    return ok;
                }
            }
        } catch (IOException directErr) {
            // Fall through to Shizuku check or error diagnostic below
            if (ShizukuPrivilegeBridge.isShizukuReady(ctx)) {
                ShizukuPrivilegeBridge.ShellExecResult shizukuRes = ShizukuPrivilegeBridge.testTargetPathWritableViaShizuku(absPath);
                if (shizukuRes.isSuccess()) {
                    long elapsedUs = (System.nanoTime() - startNs) / 1000L;
                    this.currentState.targetFilePath = absPath;
                    this.currentState.rawTextContent = "Verified via Shizuku (" + target.getName() + ")";
                    ConfigParameterSpec.StateSnapshot snap = this.currentState.copy();
                    this.notifyWriteSuccess(label, this.parseOffsetString(byteOffsetHex), "RESTRICTED", "SHIZUKU_OK (" + target.getName() + ")", elapsedUs, snap);
                    return true;
                }
                WriteDiagnosticReport report = this.buildFailureDiagnosticReport(ctx, label, absPath, directErr.getMessage() + " | Shizuku: " + shizukuRes.getCombinedError(), retryTask);
                this.notifyDiagnosticWriteError(report);
                return false;
            } else {
                WriteDiagnosticReport report = this.buildFailureDiagnosticReport(ctx, label, absPath, directErr.getMessage(), retryTask);
                this.notifyDiagnosticWriteError(report);
                return false;
            }
        }

        // If path is restricted (e.g. Android 14/15 /Android/data or /Android/obb) and direct access couldn't open it:
        if (ShizukuPrivilegeBridge.isShizukuReady(ctx)) {
            ShizukuPrivilegeBridge.ShellExecResult shizukuRes = ShizukuPrivilegeBridge.testTargetPathWritableViaShizuku(absPath);
            if (shizukuRes.isSuccess()) {
                long elapsedUs = (System.nanoTime() - startNs) / 1000L;
                this.currentState.targetFilePath = absPath;
                this.currentState.rawTextContent = "Verified via Shizuku (" + target.getName() + ")";
                ConfigParameterSpec.StateSnapshot snap = this.currentState.copy();
                this.notifyWriteSuccess(label, this.parseOffsetString(byteOffsetHex), "RESTRICTED", "SHIZUKU_OK (" + target.getName() + ")", elapsedUs, snap);
                return true;
            }
            WriteDiagnosticReport report = this.buildFailureDiagnosticReport(ctx, label, absPath, shizukuRes.getCombinedError(), retryTask);
            this.notifyDiagnosticWriteError(report);
            return false;
        }

        WriteDiagnosticReport report = this.buildFailureDiagnosticReport(
                ctx,
                label,
                absPath,
                "open failed: EACCES (Permission denied) - Android " + Build.VERSION.SDK_INT + " Scoped Storage Restricted Path",
                retryTask
        );
        this.notifyDiagnosticWriteError(report);
        return false;
    }

    public void initializeFileStateAsync(File targetFile, String operatingMode, Runnable onComplete) {
        this.bindTargetFile(targetFile, operatingMode);
        this.fileIoExecutor.execute(() -> {
            long startNs = System.nanoTime();
            try {
                File parent = targetFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                synchronized (this) {
                    this.writeFullStateToFileLocked(targetFile);
                }
                long elapsedUs = (System.nanoTime() - startNs) / 1000L;
                ConfigParameterSpec.StateSnapshot snapshot = this.getCurrentSnapshot();
                this.notifyWriteSuccess("operating_mode", 46, "UNINITIALIZED", operatingMode, elapsedUs, snapshot);
                if (onComplete != null) {
                    if (this.mainHandler != null) {
                        this.mainHandler.post(onComplete);
                    } else {
                        onComplete.run();
                    }
                }
            } catch (IOException e) {
                this.notifyWriteError("operating_mode", e.getMessage());
            }
        });
    }

    public void writeToggleStateAsync(String key, int byteOffset, boolean enabled) {
        this.fileIoExecutor.execute(() -> {
            ConfigParameterSpec.StateSnapshot snapshot;
            String oldVal;
            long startNs = System.nanoTime();
            String newVal = enabled ? "1 (0x01)" : "0 (0x00)";
            synchronized (this) {
                if (this.activeFile == null) {
                    this.notifyWriteError(key, "No target configuration file bound.");
                    return;
                }
                oldVal = this.getToggleOldValueLocked(key);
                this.applyToggleToMemoryLocked(key, enabled);
                try {
                    this.patchByteOffsetAndRebuildKeyValueLocked(this.activeFile, byteOffset, new byte[]{(byte) (enabled ? 1 : 0)});
                } catch (IOException e) {
                    this.notifyWriteError(key, "IO Failed at offset 0x" + Integer.toHexString(byteOffset) + ": " + e.getMessage());
                    return;
                }
                snapshot = this.currentState.copy();
            }
            long elapsedUs = (System.nanoTime() - startNs) / 1000L;
            this.notifyWriteSuccess(key, byteOffset, oldVal, newVal, elapsedUs, snapshot);
        });
    }

    public void writeSliderValueAsync(String key, int byteOffset, int value) {
        this.fileIoExecutor.execute(() -> {
            ConfigParameterSpec.StateSnapshot snapshot;
            String oldVal;
            long startNs = System.nanoTime();
            String newVal = String.valueOf(value);
            synchronized (this) {
                if (this.activeFile == null) {
                    this.notifyWriteError(key, "No target configuration file bound.");
                    return;
                }
                oldVal = this.getSliderOldValueLocked(key);
                this.applySliderToMemoryLocked(key, value);
                byte[] intBytes = ByteBuffer.allocate(4).putInt(value).array();
                try {
                    this.patchByteOffsetAndRebuildKeyValueLocked(this.activeFile, byteOffset, intBytes);
                } catch (IOException e) {
                    this.notifyWriteError(key, "IO Failed at offset 0x" + Integer.toHexString(byteOffset) + ": " + e.getMessage());
                    return;
                }
                snapshot = this.currentState.copy();
            }
            long elapsedUs = (System.nanoTime() - startNs) / 1000L;
            this.notifyWriteSuccess(key, byteOffset, oldVal, newVal, elapsedUs, snapshot);
        });
    }

    public void writeTextParameterAsync(String key, int byteOffset, int maxSlotLength, String rawInput) {
        this.fileIoExecutor.execute(() -> {
            ConfigParameterSpec.StateSnapshot snapshot;
            String oldVal;
            long startNs = System.nanoTime();
            String sanitized = rawInput == null ? "" : rawInput.trim();
            if (sanitized.isEmpty()) {
                sanitized = "DEFAULT";
            }
            if (sanitized.length() > maxSlotLength) {
                sanitized = sanitized.substring(0, maxSlotLength);
            }
            synchronized (this) {
                if (this.activeFile == null) {
                    this.notifyWriteError(key, "No target configuration file bound.");
                    return;
                }
                if ("execution_node_tag".equals(key)) {
                    oldVal = this.currentState.executionNodeTag;
                    this.currentState.executionNodeTag = sanitized;
                } else {
                    oldVal = this.currentState.customRegisterHex;
                    this.currentState.customRegisterHex = sanitized;
                }
                byte[] slotBytes = new byte[maxSlotLength];
                byte[] utfBytes = sanitized.getBytes(StandardCharsets.US_ASCII);
                System.arraycopy(utfBytes, 0, slotBytes, 0, Math.min(utfBytes.length, maxSlotLength));
                try {
                    this.patchByteOffsetAndRebuildKeyValueLocked(this.activeFile, byteOffset, slotBytes);
                } catch (IOException e) {
                    this.notifyWriteError(key, "IO Failed at offset 0x" + Integer.toHexString(byteOffset) + ": " + e.getMessage());
                    return;
                }
                snapshot = this.currentState.copy();
            }
            long elapsedUs = (System.nanoTime() - startNs) / 1000L;
            this.notifyWriteSuccess(key, byteOffset, oldVal, sanitized, elapsedUs, snapshot);
        });
    }

    public void writeCustomComponentOffsetAsync(File fallbackDir, String targetFilePath, String byteOffsetHex, String payloadValue, String componentLabel) {
        this.applyWidgetPatchAsync(fallbackDir, componentLabel != null ? componentLabel : "widget", "BUTTON", targetFilePath, byteOffsetHex, "", payloadValue, payloadValue, true, componentLabel);
    }

    public void applyWidgetPatchAsync(File fallbackDir, String widgetKey, String widgetType, String targetFilePath, String byteOffsetHex, String originalValue, String changeValue, String liveValue, boolean isActive, String componentLabel) {
        this.fileIoExecutor.execute(() -> this.applyWidgetPatchSync(fallbackDir, widgetKey, widgetType, targetFilePath, byteOffsetHex, originalValue, changeValue, liveValue, isActive, componentLabel, null));
    }

    public void applyWidgetPatchAsync(File fallbackDir, String widgetKey, String widgetType, String targetFilePath, String byteOffsetHex, String originalValue, String changeValue, String liveValue, boolean isActive, String componentLabel, String customSourceFilePath) {
        this.fileIoExecutor.execute(() -> this.applyWidgetPatchSync(fallbackDir, widgetKey, widgetType, targetFilePath, byteOffsetHex, originalValue, changeValue, liveValue, isActive, componentLabel, customSourceFilePath));
    }

    public boolean applyWidgetPatchSync(File fallbackDir, String widgetKey, String widgetType, String targetFilePath, String byteOffsetHex, String originalValue, String changeValue, String liveValue, boolean isActive, String componentLabel) {
        return this.applyWidgetPatchSync(fallbackDir, widgetKey, widgetType, targetFilePath, byteOffsetHex, originalValue, changeValue, liveValue, isActive, componentLabel, null);
    }

    public boolean applyWidgetPatchSync(
            final File fallbackDir,
            final String widgetKey,
            final String widgetType,
            final String targetFilePath,
            final String byteOffsetHex,
            final String originalValue,
            final String changeValue,
            final String liveValue,
            final boolean isActive,
            final String componentLabel,
            final String customSourceFilePath
    ) {
        ConfigParameterSpec.StateSnapshot snapshot;
        long startNs = System.nanoTime();
        int offset = this.parseOffsetString(byteOffsetHex);
        File target = this.resolveTargetFile(fallbackDir, targetFilePath);
        final String safeKey = widgetKey != null && !widgetKey.trim().isEmpty() ? widgetKey.trim() : (componentLabel != null ? componentLabel : "widget");
        final String orig = originalValue != null ? originalValue : "";
        final String chg = changeValue != null ? changeValue : "";
        final String live = liveValue != null ? liveValue : "";
        final String type = widgetType != null ? widgetType.toUpperCase(Locale.US) : "BUTTON";
        boolean useTextScriptPatch = this.shouldUseTextOrScriptPatch(target, orig, chg, type);
        String replacementText = this.computeReplacementText(type, orig, chg, live, isActive, useTextScriptPatch);
        String previousVal = this.lastWrittenByWidget.getOrDefault(safeKey, isActive ? orig : chg);
        final String resolvedLabel = componentLabel != null && !componentLabel.trim().isEmpty() ? componentLabel : safeKey;

        Runnable retryAction = () -> this.applyWidgetPatchAsync(
                fallbackDir, safeKey, type, targetFilePath, byteOffsetHex, orig, chg, live, isActive, resolvedLabel, customSourceFilePath
        );

        synchronized (this) {
            try {
                File parent = target.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                String fileReplaceOrMergeSummary = this.tryApplySelectedFileReplaceOrMergeLocked(
                        fallbackDir, target, safeKey, type, live, isActive, customSourceFilePath
                );
                if (fileReplaceOrMergeSummary != null) {
                    replacementText = fileReplaceOrMergeSummary;
                    this.lastWrittenByWidget.put(safeKey, replacementText);
                } else if (useTextScriptPatch) {
                    this.patchTextOrPythonFileLocked(target, safeKey, orig, chg, replacementText, isActive);
                } else {
                    byte[] payloadBytes = this.parsePayloadBytes(replacementText);
                    try (RandomAccessFile raf = new RandomAccessFile(target, "rw")) {
                        if (raf.length() < (long) (offset + payloadBytes.length)) {
                            raf.setLength(Math.max(64, offset + payloadBytes.length));
                        }
                        raf.seek(offset);
                        raf.write(payloadBytes);
                    }
                    this.lastWrittenByWidget.put(safeKey, replacementText);
                }
                this.activeFile = target;
                this.currentState.targetFilePath = target.getAbsolutePath();
                this.currentState.rawTextContent = replacementText;
                snapshot = this.currentState.copy();
            } catch (IOException directIoError) {
                // Direct file change failed! Try Shizuku Privileged Bridge automatically if available & authorized
                Context ctx = this.appContext;
                if (ctx != null) {
                    ShizukuPrivilegeBridge.probeShizukuBinder(ctx);
                }
                if (ShizukuPrivilegeBridge.isShizukuReady(ctx)) {
                    try {
                        String shizukuResultSummary = this.applyWidgetPatchViaShizukuLocked(
                                ctx,
                                fallbackDir,
                                target,
                                safeKey,
                                type,
                                offset,
                                orig,
                                chg,
                                live,
                                replacementText,
                                isActive,
                                useTextScriptPatch,
                                customSourceFilePath
                        );
                        replacementText = shizukuResultSummary;
                        this.lastWrittenByWidget.put(safeKey, replacementText);
                        this.activeFile = target;
                        this.currentState.targetFilePath = target.getAbsolutePath();
                        this.currentState.rawTextContent = replacementText;
                        snapshot = this.currentState.copy();
                        long elapsedUs = (System.nanoTime() - startNs) / 1000L;
                        this.clearLastDiagnosticReport();
                        this.notifyWriteSuccess(resolvedLabel, offset, previousVal, replacementText, elapsedUs, snapshot);
                        return true;
                    } catch (IOException shizukuIoError) {
                        WriteDiagnosticReport report = this.buildFailureDiagnosticReport(
                                ctx,
                                resolvedLabel,
                                target.getAbsolutePath(),
                                "Direct IO: " + directIoError.getMessage() + " | Shizuku IO: " + shizukuIoError.getMessage(),
                                retryAction
                        );
                        this.notifyDiagnosticWriteError(report);
                        return false;
                    }
                }

                WriteDiagnosticReport report = this.buildFailureDiagnosticReport(
                        ctx,
                        resolvedLabel,
                        target.getAbsolutePath(),
                        directIoError.getMessage(),
                        retryAction
                );
                this.notifyDiagnosticWriteError(report);
                return false;
            }
        }
        long elapsedUs = (System.nanoTime() - startNs) / 1000L;
        this.clearLastDiagnosticReport();
        this.notifyWriteSuccess(resolvedLabel, offset, previousVal, replacementText, elapsedUs, snapshot);
        return true;
    }

    /**
     * Performs the file replacement/merge, text script patch, or binary offset patch via Shizuku
     * when direct file access is blocked by Android 13/14/15+ restricted directories (/Android/data, /Android/obb, etc.).
     */
    private String applyWidgetPatchViaShizukuLocked(
            Context ctx,
            File fallbackDir,
            File target,
            String safeKey,
            String widgetType,
            int offset,
            String orig,
            String chg,
            String live,
            String computedReplacementText,
            boolean isActive,
            boolean useTextScriptPatch,
            String customSourceFilePath
    ) throws IOException {
        String absTarget = target.getAbsolutePath();

        // 1. Check if widget(s) have a selected source file for replace/merge
        List<File> activeSourceFiles = this.collectActiveSourceFilesForTargetLocked(
                fallbackDir, target, safeKey, widgetType, live, isActive, customSourceFilePath
        );
        if (activeSourceFiles != null) {
            File backupDir = new File(fallbackDir, "original_target_backups");
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }
            String targetHashKey = Integer.toHexString(absTarget.hashCode()) + "_" + target.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
            File backupFile = new File(backupDir, targetHashKey + ".orig_backup");
            File markerFile = new File(backupDir, targetHashKey + ".replaced_marker");

            if (!markerFile.exists() && ShizukuPrivilegeBridge.remoteFileExistsViaShizuku(absTarget)) {
                ShizukuPrivilegeBridge.copyFileViaShizuku(absTarget, backupFile.getAbsolutePath());
            }

            if (activeSourceFiles.isEmpty()) {
                if (markerFile.exists()) {
                    if (backupFile.exists()) {
                        ShizukuPrivilegeBridge.ShellExecResult restoreRes = ShizukuPrivilegeBridge.copyFileViaShizuku(backupFile.getAbsolutePath(), absTarget);
                        if (!restoreRes.isSuccess()) {
                            throw new IOException(restoreRes.getCombinedError());
                        }
                        backupFile.delete();
                    } else {
                        ShizukuPrivilegeBridge.deleteFileViaShizuku(absTarget);
                    }
                    markerFile.delete();
                    return "Restored Original via Shizuku (" + target.getName() + ")";
                }
                return "Original Kept via Shizuku (" + target.getName() + ")";
            }

            if (activeSourceFiles.size() == 1) {
                File singleSource = activeSourceFiles.get(0);
                ShizukuPrivilegeBridge.ShellExecResult copyRes = ShizukuPrivilegeBridge.copyFileViaShizuku(singleSource.getAbsolutePath(), absTarget);
                if (!copyRes.isSuccess()) {
                    throw new IOException(copyRes.getCombinedError());
                }
                if (!markerFile.exists()) {
                    try {
                        markerFile.createNewFile();
                    } catch (Exception ignored) {
                    }
                }
                return "Replaced via Shizuku: " + target.getName() + " <= " + singleSource.getName();
            }

            boolean mergeAsText = this.areAllFilesLikelyTextLocked(activeSourceFiles);
            StringBuilder mergedNames = new StringBuilder();
            for (int i = 0; i < activeSourceFiles.size(); ++i) {
                if (i > 0) mergedNames.append(" + ");
                mergedNames.append(activeSourceFiles.get(i).getName());
            }
            ShizukuPrivilegeBridge.ShellExecResult mergeRes = ShizukuPrivilegeBridge.mergeFilesViaShizuku(ctx, activeSourceFiles, absTarget, mergeAsText);
            if (!mergeRes.isSuccess()) {
                throw new IOException(mergeRes.getCombinedError());
            }
            if (!markerFile.exists()) {
                try {
                    markerFile.createNewFile();
                } catch (Exception ignored) {
                }
            }
            return "Merged via Shizuku (" + activeSourceFiles.size() + " files: " + mergedNames + ") => " + target.getName();
        }

        // 2. Text / Script / Config file patch via Shizuku
        if (useTextScriptPatch) {
            String existingContent = "";
            if (ShizukuPrivilegeBridge.remoteFileExistsViaShizuku(absTarget)) {
                String readViaShizuku = ShizukuPrivilegeBridge.readTextFileViaShizuku(absTarget, 0x200000);
                if (readViaShizuku != null) {
                    existingContent = readViaShizuku;
                }
            }
            String updatedContent = this.computePatchedTextContentLocked(existingContent, safeKey, orig, chg, computedReplacementText, isActive);
            ShizukuPrivilegeBridge.ShellExecResult writeRes = ShizukuPrivilegeBridge.writeBytesViaShizuku(
                    ctx, absTarget, updatedContent.getBytes(StandardCharsets.UTF_8)
            );
            if (!writeRes.isSuccess()) {
                throw new IOException(writeRes.getCombinedError());
            }
            return computedReplacementText + " [Shizuku]";
        }

        // 3. Binary offset patch via Shizuku
        byte[] payloadBytes = this.parsePayloadBytes(computedReplacementText);
        ShizukuPrivilegeBridge.ShellExecResult patchRes = ShizukuPrivilegeBridge.patchBytesAtOffsetViaShizuku(
                ctx, absTarget, offset, payloadBytes, 64
        );
        if (!patchRes.isSuccess()) {
            throw new IOException(patchRes.getCombinedError());
        }
        return computedReplacementText + " [Shizuku]";
    }

    /**
     * Builds a comprehensive diagnostic report explaining WHY the file modification failed
     * and how to fix Android 13/14/15 restricted path errors using Shizuku.
     */
    public WriteDiagnosticReport buildFailureDiagnosticReport(
            Context context,
            String componentLabel,
            String targetFilePath,
            String rawErrorMessage,
            Runnable retryAction
    ) {
        Context ctx = context != null ? context : this.appContext;
        boolean hasStoragePerm = ctx == null || LocalConfigStateWriter.hasStoragePermissionGranted(ctx);
        boolean isRestricted = ShizukuPrivilegeBridge.isRestrictedAndroidPath(targetFilePath);
        boolean shizukuInstalled = ShizukuPrivilegeBridge.isShizukuInstalled(ctx);
        boolean shizukuRunning = ShizukuPrivilegeBridge.isShizukuRunning(ctx);
        boolean shizukuAuthorized = ShizukuPrivilegeBridge.hasShizukuPermission(ctx);
        String rawErr = rawErrorMessage != null && !rawErrorMessage.trim().isEmpty()
                ? rawErrorMessage.trim()
                : "IOException: EACCES (Permission denied)";

        String category;
        String title;
        String detail;
        boolean requiresShizuku = false;

        if (isRestricted) {
            requiresShizuku = true;
            category = "ANDROID_15_RESTRICTED_PATH";
            title = "Android " + Build.VERSION.SDK_INT + " Restricted Path Blocked";
            StringBuilder sb = new StringBuilder();
            sb.append("Target file path (").append(targetFilePath).append(") falls inside a protected Android system/scoped directory (/Android/data, /Android/obb, or /data).\n\n");
            sb.append("Why it failed (Akhir kyu fail hua):\n");
            sb.append("On Android 13, 14, and Android 15, the OS kernel & FUSE storage daemon strictly block normal apps from directly modifying files inside /Android/data and /Android/obb — even when 'All Files Access' (MANAGE_EXTERNAL_STORAGE) is turned ON.\n\n");
            sb.append("How to fix with Shizuku (Shizuku se kaise solve karein):\n");
            if (!shizukuInstalled) {
                sb.append("1. Install the Shizuku app on your device and start it via Wireless Debugging (ADB).\n");
                sb.append("2. Tap 'Connect Shizuku & Retry' below — both Studio Error and the compiled APK use Shizuku's privileged shell (UID 2000) to bypass Android 15 path restrictions.");
            } else if (!shizukuRunning) {
                sb.append("1. Shizuku is installed on your device, but its background service is NOT running.\n");
                sb.append("2. Tap 'Open Shizuku App' below, start the Shizuku service via Wireless Debugging, then tap 'Retry File Change'.");
            } else if (!shizukuAuthorized) {
                sb.append("1. Shizuku is running! However, this app has not been granted Shizuku permission yet.\n");
                sb.append("2. Tap 'Authorize Shizuku & Fix Now' below and press 'Allow' on the Shizuku prompt to immediately unlock this restricted path.");
            } else {
                sb.append("1. Shizuku is connected, but the target directory or file returned an OS error.\n");
                sb.append("2. Verify that the folder/package exists on your device and tap 'Retry with Shizuku'.");
            }
            detail = sb.toString();
        } else if (!hasStoragePerm) {
            category = "MISSING_ALL_FILES_PERMISSION";
            title = "All Files Access Permission Missing";
            detail = "Target file (" + targetFilePath + ") could not be modified because 'All Files Access' (MANAGE_EXTERNAL_STORAGE) permission is not granted.\n\n"
                    + "Why it failed (Akhir kyu fail hua):\n"
                    + "Android blocked write access to shared storage (" + rawErr + ").\n\n"
                    + "How to fix:\n"
                    + "Tap 'Grant All Files Access' below to allow storage modification, or use Shizuku for elevated file access.";
        } else if (rawErr.toLowerCase(Locale.US).contains("eacces") || rawErr.toLowerCase(Locale.US).contains("permission denied") || rawErr.toLowerCase(Locale.US).contains("operation not permitted")) {
            requiresShizuku = true;
            category = "ANDROID_15_RESTRICTED_PATH";
            title = "Restricted File Access Denied (EACCES)";
            detail = "Android OS kernel blocked direct modification of:\n" + targetFilePath + "\n\n"
                    + "Why it failed (Akhir kyu fail hua):\n"
                    + "Even with storage permission enabled, Android " + Build.VERSION.SDK_INT + " enforces SELinux / Scoped Storage protection on this path (" + rawErr + ").\n\n"
                    + "How to fix with Shizuku:\n"
                    + "Use Shizuku (ADB / Root privilege bridge) to modify this file directly. Tap the Shizuku button below to authorize and retry.";
        } else {
            category = "IO_WRITE_ERROR";
            title = "Target File Modification Failed";
            detail = "Could not modify target file at:\n" + targetFilePath + "\n\n"
                    + "Why it failed (Akhir kyu fail hua):\n"
                    + rawErr + "\n\n"
                    + "If this path is protected by Android 14/15 restrictions, enable Shizuku below to perform the file change with elevated privileges.";
        }

        return new WriteDiagnosticReport(
                false,
                componentLabel,
                targetFilePath,
                category,
                title,
                detail,
                rawErr,
                isRestricted,
                requiresShizuku,
                hasStoragePerm,
                shizukuInstalled,
                shizukuRunning,
                shizukuAuthorized,
                false,
                retryAction
        );
    }

    /**
     * Returns null if no widget has a custom source file configured for this target;
     * otherwise returns the list of currently active source files (empty list if all widgets targeting this file are OFF).
     */
    private List<File> collectActiveSourceFilesForTargetLocked(
            File fallbackDir,
            File target,
            String safeKey,
            String widgetType,
            String liveValue,
            boolean isActive,
            String explicitSourceFilePath
    ) {
        List<DynamicOverlayRegistry.OverlayItemSpec> registrySpecs = DynamicOverlayRegistry.getActiveItems();
        ArrayList<File> activeSourceFiles = new ArrayList<File>();
        boolean anyWidgetHasSourceFileForTarget = false;
        boolean currentWidgetFoundInRegistry = false;

        if (registrySpecs != null) {
            for (DynamicOverlayRegistry.OverlayItemSpec spec : registrySpecs) {
                if (spec == null) continue;
                String specKey = "widget_" + spec.id;
                boolean isCurrentTriggeredWidget = specKey.equals(safeKey);
                if (isCurrentTriggeredWidget) {
                    currentWidgetFoundInRegistry = true;
                    spec.currentValue = "SLIDER".equalsIgnoreCase(widgetType)
                            ? (liveValue != null && !liveValue.isEmpty() ? liveValue : (isActive ? "50" : "0"))
                            : (isActive ? "1" : "0");
                    if (explicitSourceFilePath != null && !explicitSourceFilePath.trim().isEmpty()) {
                        spec.customImagePath = explicitSourceFilePath.trim();
                    }
                }
                File specTarget = this.resolveTargetFile(fallbackDir, spec.targetFilePath);
                boolean sameTarget = specTarget.getAbsolutePath().equals(target.getAbsolutePath());
                if (!sameTarget) continue;

                String srcPath = isCurrentTriggeredWidget && explicitSourceFilePath != null && !explicitSourceFilePath.trim().isEmpty()
                        ? explicitSourceFilePath.trim()
                        : (spec.customImagePath != null ? spec.customImagePath.trim() : "");
                if (srcPath.isEmpty()) continue;
                File srcFile = new File(srcPath);
                if (!srcFile.exists() || !srcFile.isFile()) continue;

                anyWidgetHasSourceFileForTarget = true;
                boolean specActive;
                if (isCurrentTriggeredWidget) {
                    specActive = isActive;
                } else if ("SLIDER".equalsIgnoreCase(spec.type)) {
                    int v = 0;
                    try {
                        v = Integer.parseInt(spec.currentValue != null ? spec.currentValue.trim() : "0");
                    } catch (Exception ignored) {
                    }
                    specActive = v > 0;
                } else {
                    specActive = "1".equals(spec.currentValue) || "true".equalsIgnoreCase(spec.currentValue);
                }

                if (specActive) {
                    activeSourceFiles.add(srcFile);
                }
            }
        }

        if (!currentWidgetFoundInRegistry && explicitSourceFilePath != null && !explicitSourceFilePath.trim().isEmpty()) {
            File explicitSrc = new File(explicitSourceFilePath.trim());
            if (explicitSrc.exists() && explicitSrc.isFile()) {
                anyWidgetHasSourceFileForTarget = true;
                if (isActive) {
                    activeSourceFiles.add(explicitSrc);
                }
            }
        }

        if (!anyWidgetHasSourceFileForTarget) {
            return null;
        }
        return activeSourceFiles;
    }

    private String tryApplySelectedFileReplaceOrMergeLocked(File fallbackDir, File target, String safeKey, String widgetType, String liveValue, boolean isActive, String explicitSourceFilePath) throws IOException {
        List<File> activeSourceFiles = this.collectActiveSourceFilesForTargetLocked(
                fallbackDir, target, safeKey, widgetType, liveValue, isActive, explicitSourceFilePath
        );
        if (activeSourceFiles == null) {
            return null;
        }

        // If target is inside an Android 13/14/15 restricted path (/Android/data or /Android/obb) and cannot be written directly,
        // throw IOException immediately so the caller routes through ShizukuPrivilegeBridge or triggers the Error Dialog.
        if (ShizukuPrivilegeBridge.isRestrictedAndroidPath(target.getAbsolutePath())) {
            File parent = target.getParentFile();
            if (parent == null || !parent.canWrite()) {
                throw new IOException("open failed: EACCES (Permission denied) on restricted path " + target.getAbsolutePath());
            }
        }

        File backupDir = new File(fallbackDir, "original_target_backups");
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }
        String targetHashKey = Integer.toHexString(target.getAbsolutePath().hashCode()) + "_" + target.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
        File backupFile = new File(backupDir, targetHashKey + ".orig_backup");
        File markerFile = new File(backupDir, targetHashKey + ".replaced_marker");

        // Back up the original file at target path before replacing/merging it for the first time
        if (target.exists() && target.isFile() && !markerFile.exists()) {
            this.copyRawFileBytesLocked(target, backupFile);
        }

        if (activeSourceFiles.isEmpty()) {
            // All options turned OFF: restore original target file if backed up
            if (markerFile.exists()) {
                if (target.exists() && !target.delete()) {
                    throw new IOException("Cannot delete modified target file at " + target.getAbsolutePath());
                }
                if (backupFile.exists()) {
                    this.copyRawFileBytesLocked(backupFile, target);
                    backupFile.delete();
                }
                markerFile.delete();
                return "Restored Original (" + target.getName() + ")";
            }
            return "Original Kept (" + target.getName() + ")";
        }

        // Remove the original file at the target path so selected file(s) replace/merge with the exact same target name
        if (target.exists() && !target.delete()) {
            throw new IOException("open failed: EACCES (Permission denied) deleting " + target.getAbsolutePath());
        }

        if (activeSourceFiles.size() == 1) {
            File singleSource = activeSourceFiles.get(0);
            this.copyRawFileBytesLocked(singleSource, target);
            if (!markerFile.exists()) {
                try {
                    markerFile.createNewFile();
                } catch (Exception ignored) {
                }
            }
            return "Replaced " + target.getName() + " <= " + singleSource.getName();
        }

        // Multiple options ON: merge all selected files into target path with the exact same target filename
        boolean mergeAsText = this.areAllFilesLikelyTextLocked(activeSourceFiles);
        StringBuilder mergedNames = new StringBuilder();
        try (FileOutputStream fos = new FileOutputStream(target, false)) {
            byte[] buf = new byte[8192];
            for (int i = 0; i < activeSourceFiles.size(); ++i) {
                File src = activeSourceFiles.get(i);
                if (i > 0) {
                    mergedNames.append(" + ");
                }
                mergedNames.append(src.getName());
                byte lastByte = -1;
                try (FileInputStream fis = new FileInputStream(src)) {
                    int read;
                    while ((read = fis.read(buf)) != -1) {
                        fos.write(buf, 0, read);
                        if (read > 0) {
                            lastByte = buf[read - 1];
                        }
                    }
                }
                if (mergeAsText && i < activeSourceFiles.size() - 1 && lastByte != -1 && lastByte != 10) {
                    fos.write(10);
                }
            }
            fos.flush();
            try {
                fos.getFD().sync();
            } catch (Exception ignored) {
            }
        }
        if (!markerFile.exists()) {
            try {
                markerFile.createNewFile();
            } catch (Exception ignored) {
            }
        }
        return "Merged (" + activeSourceFiles.size() + " files: " + mergedNames + ") => " + target.getName();
    }

    private void copyRawFileBytesLocked(File source, File dest) throws IOException {
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileInputStream fis = new FileInputStream(source);
             FileOutputStream fos = new FileOutputStream(dest, false)) {
            int read;
            byte[] buf = new byte[8192];
            while ((read = fis.read(buf)) != -1) {
                fos.write(buf, 0, read);
            }
            fos.flush();
            try {
                fos.getFD().sync();
            } catch (Exception ignored) {
            }
        }
    }

    private boolean areAllFilesLikelyTextLocked(List<File> files) {
        for (File f : files) {
            try (FileInputStream fis = new FileInputStream(f)) {
                byte[] sample = new byte[512];
                int n = fis.read(sample);
                for (int i = 0; i < n; ++i) {
                    if (sample[i] != 0) continue;
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    public String readTargetFilePreview(File fallbackDir, String rawPath) {
        try {
            File target = this.resolveTargetFile(fallbackDir, rawPath);
            if (!target.exists()) {
                if (ShizukuPrivilegeBridge.isRestrictedAndroidPath(target.getAbsolutePath())
                        && ShizukuPrivilegeBridge.isShizukuReady(this.appContext)
                        && ShizukuPrivilegeBridge.remoteFileExistsViaShizuku(target.getAbsolutePath())) {
                    String shizukuText = ShizukuPrivilegeBridge.readTextFileViaShizuku(target.getAbsolutePath(), 4096);
                    if (shizukuText != null) {
                        return shizukuText.trim().isEmpty() ? "(Empty file via Shizuku: " + target.getName() + ")" : shizukuText.trim();
                    }
                }
                return "File not found yet (" + target.getAbsolutePath() + ")";
            }
            if (target.length() == 0L) {
                return "(Empty file: " + target.getName() + ")";
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader((InputStream) new FileInputStream(target), StandardCharsets.UTF_8))) {
                char[] buf = new char[4096];
                int read = reader.read(buf);
                if (read > 0) {
                    sb.append(buf, 0, read);
                }
            }
            String text = sb.toString().trim();
            return text.isEmpty() ? "(Empty)" : text;
        } catch (Exception e) {
            try {
                File target = this.resolveTargetFile(fallbackDir, rawPath);
                if (ShizukuPrivilegeBridge.isShizukuReady(this.appContext)) {
                    String shizukuText = ShizukuPrivilegeBridge.readTextFileViaShizuku(target.getAbsolutePath(), 4096);
                    if (shizukuText != null) {
                        return shizukuText.trim().isEmpty() ? "(Empty via Shizuku)" : shizukuText.trim();
                    }
                }
            } catch (Throwable ignored) {
            }
            return "Read blocked (" + e.getMessage() + ")";
        }
    }

    public boolean detectInitialToggleState(File fallbackDir, String rawPath, String originalValue, String changeValue, boolean defaultActive) {
        try {
            String chg;
            File target = this.resolveTargetFile(fallbackDir, rawPath);
            String content = "";
            if (target.exists() && target.canRead() && target.length() > 0L) {
                content = this.readEntireTextFileWithBufferedReader(target);
            } else if (ShizukuPrivilegeBridge.isRestrictedAndroidPath(target.getAbsolutePath())
                    && ShizukuPrivilegeBridge.isShizukuReady(this.appContext)
                    && ShizukuPrivilegeBridge.remoteFileExistsViaShizuku(target.getAbsolutePath())) {
                String shizukuContent = ShizukuPrivilegeBridge.readTextFileViaShizuku(target.getAbsolutePath(), 65536);
                if (shizukuContent != null) {
                    content = shizukuContent;
                }
            }
            if (content.isEmpty()) {
                return defaultActive;
            }
            String orig = originalValue != null && !this.isSingleHexOrByte(originalValue.trim()) ? originalValue.trim() : "";
            chg = changeValue != null && !this.isSingleHexOrByte(changeValue.trim()) ? changeValue.trim() : "";
            if (!(chg.isEmpty() || orig.isEmpty() || chg.equalsIgnoreCase(orig))) {
                boolean hasChg = this.containsTokenOrSubstring(content, chg);
                boolean hasOrig = this.containsTokenOrSubstring(content, orig);
                if (hasChg && !hasOrig) {
                    return true;
                }
                if (hasOrig && !hasChg) {
                    return false;
                }
            }
        } catch (Exception ignored) {
        }
        return defaultActive;
    }

    private boolean containsTokenOrSubstring(String content, String token) {
        if (content == null || token == null || token.isEmpty()) {
            return false;
        }
        if (content.contains(token)) {
            return true;
        }
        Matcher m = Pattern.compile(Pattern.quote(token), 66).matcher(content);
        return m.find();
    }

    private String readEntireTextFileWithBufferedReader(File target) throws IOException {
        if (!target.exists()) {
            if (ShizukuPrivilegeBridge.isRestrictedAndroidPath(target.getAbsolutePath())) {
                throw new IOException("open failed: EACCES (Permission denied) reading restricted path " + target.getAbsolutePath());
            }
            return "";
        }
        if (target.length() == 0L) {
            return "";
        }
        StringBuilder sb = new StringBuilder((int) Math.min(target.length() + 64L, 0x200000L));
        try (FileInputStream fis = new FileInputStream(target);
             InputStreamReader isr = new InputStreamReader((InputStream) fis, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {
            int charsRead;
            char[] buffer = new char[8192];
            int totalRead = 0;
            while ((charsRead = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, charsRead);
                if ((totalRead += charsRead) < 0x200000) continue;
                break;
            }
        }
        return sb.toString();
    }

    private void writeEntireTextFileWithBufferedWriter(File target, String updatedContent) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(target, false);
             OutputStreamWriter osw = new OutputStreamWriter((OutputStream) fos, StandardCharsets.UTF_8);
             BufferedWriter writer = new BufferedWriter(osw)) {
            writer.write(updatedContent);
            writer.flush();
            try {
                fos.getFD().sync();
            } catch (Exception ignored) {
            }
        }
    }

    private String computeReplacementText(String widgetType, String originalValue, String changeValue, String liveValue, boolean isActive, boolean isTextScriptFile) {
        String orig = originalValue != null ? originalValue : "";
        String chg = changeValue != null ? changeValue : "";
        String live = liveValue != null ? liveValue : "";
        if ("SLIDER".equals(widgetType)) {
            String valStr = live.trim().isEmpty() ? "0" : live.trim();
            if ("0".equals(valStr) && !orig.trim().isEmpty() && !"0x00".equalsIgnoreCase(orig.trim()) && orig.matches(".*[-+]?\\d+(\\.\\d+)?.*") && !orig.trim().matches("[-+]?\\d+(\\.\\d+)?")) {
                return this.replaceLastNumber(orig, valStr);
            }
            if (chg.contains("{value}") || chg.contains("{val}") || chg.contains("$value") || chg.contains("%d") || chg.contains("%s")) {
                return chg.replace("{value}", valStr).replace("{val}", valStr).replace("$value", valStr).replace("%d", valStr).replace("%s", valStr);
            }
            String chgTrim = chg.trim();
            if (chgTrim.endsWith("=") || chgTrim.endsWith(":")) {
                return chg + (chg.endsWith(" ") ? "" : " ") + valStr;
            }
            if (!chgTrim.isEmpty() && !"0x01".equalsIgnoreCase(chgTrim) && chgTrim.matches(".*[-+]?\\d+(\\.\\d+)?.*") && !chgTrim.matches("[-+]?\\d+(\\.\\d+)?")) {
                return this.replaceLastNumber(chg, valStr);
            }
            String origTrim = orig.trim();
            if (!origTrim.isEmpty() && !"0x00".equalsIgnoreCase(origTrim) && origTrim.matches(".*[-+]?\\d+(\\.\\d+)?.*") && !origTrim.matches("[-+]?\\d+(\\.\\d+)?")) {
                return this.replaceLastNumber(orig, valStr);
            }
            return valStr;
        }
        if ("INPUT".equals(widgetType)) {
            if (live.trim().isEmpty() && !orig.trim().isEmpty() && !"0x00".equalsIgnoreCase(orig.trim())) {
                return orig;
            }
            if (chg.contains("{value}") || chg.contains("{val}") || chg.contains("$value") || chg.contains("%s")) {
                return chg.replace("{value}", live).replace("{val}", live).replace("$value", live).replace("%s", live);
            }
            String chgTrim = chg.trim();
            if (chgTrim.endsWith("=") || chgTrim.endsWith(":")) {
                return chg + (chg.endsWith(" ") ? "" : " ") + live;
            }
            if (!live.isEmpty()) {
                return live;
            }
            return isActive ? chg : orig;
        }
        if (isActive) {
            if (!(chg.isEmpty() || isTextScriptFile && "0x01".equalsIgnoreCase(chg.trim()))) {
                return chg;
            }
            if (!(live.isEmpty() || isTextScriptFile && "0x01".equalsIgnoreCase(live.trim()))) {
                return live;
            }
            return isTextScriptFile ? "On" : "0x01";
        }
        if (!(orig.isEmpty() || isTextScriptFile && "0x00".equalsIgnoreCase(orig.trim()))) {
            return orig;
        }
        if (!(live.isEmpty() || isTextScriptFile && "0x00".equalsIgnoreCase(live.trim()))) {
            return live;
        }
        return isTextScriptFile ? "Off" : "0x00";
    }

    private String replaceLastNumber(String input, String newNumber) {
        Matcher m = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?").matcher(input);
        int start = -1;
        int end = -1;
        while (m.find()) {
            start = m.start();
            end = m.end();
        }
        if (start >= 0 && end >= start) {
            return input.substring(0, start) + newNumber + input.substring(end);
        }
        return input + " " + newNumber;
    }

    private boolean shouldUseTextOrScriptPatch(File target, String orig, String chg, String type) {
        String name = target.getName().toLowerCase(Locale.US);
        if (name.endsWith(".py") || name.endsWith(".txt") || name.endsWith(".sh") || name.endsWith(".lua") || name.endsWith(".js") || name.endsWith(".json") || name.endsWith(".cfg") || name.endsWith(".ini") || name.endsWith(".conf") || name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".xml") || name.endsWith(".prop") || name.endsWith(".csv")) {
            return true;
        }
        boolean origIsDefaultHex = orig == null || orig.trim().isEmpty() || this.isSingleHexOrByte(orig.trim());
        boolean chgIsDefaultHex = chg == null || chg.trim().isEmpty() || this.isSingleHexOrByte(chg.trim());
        return !origIsDefaultHex || !chgIsDefaultHex;
    }

    private boolean isSingleHexOrByte(String s) {
        if (s.startsWith("0x") || s.startsWith("0X")) {
            try {
                int v = Integer.parseInt(s.substring(2), 16);
                return v >= 0 && v <= 255;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    private String computePatchedTextContentLocked(String content, String widgetKey, String originalValue, String changeValue, String replacementText, boolean isActive) {
        String lastWritten;
        ArrayList<String> candidates = new ArrayList<String>();
        String origClean = originalValue != null && !this.isSingleHexOrByte(originalValue.trim()) ? originalValue : "";
        String chgClean = changeValue != null && !this.isSingleHexOrByte(changeValue.trim()) ? changeValue : "";
        if (isActive) {
            this.addCandidateVariants(candidates, origClean);
            lastWritten = this.lastWrittenByWidget.get(widgetKey);
            if (lastWritten != null && !lastWritten.equals(replacementText)) {
                this.addCandidateVariants(candidates, lastWritten);
            }
            this.addCandidateVariants(candidates, chgClean);
            this.addCandidateVariants(candidates, "Off");
            this.addCandidateVariants(candidates, "False");
        } else {
            this.addCandidateVariants(candidates, chgClean);
            lastWritten = this.lastWrittenByWidget.get(widgetKey);
            if (lastWritten != null && !lastWritten.equals(replacementText)) {
                this.addCandidateVariants(candidates, lastWritten);
            }
            this.addCandidateVariants(candidates, origClean);
            this.addCandidateVariants(candidates, "On");
            this.addCandidateVariants(candidates, "True");
        }
        String updatedContent = null;
        if (!content.isEmpty()) {
            for (String candidate : candidates) {
                if (candidate == null || candidate.isEmpty() || candidate.equals(replacementText) || !content.contains(candidate)) continue;
                updatedContent = content.replace(candidate, replacementText);
                break;
            }
        }
        if (updatedContent == null && !content.isEmpty()) {
            for (String candidate : candidates) {
                String rep;
                if (candidate == null || candidate.trim().isEmpty()) continue;
                String cTrim = candidate.trim();
                String rTrim = replacementText.trim();
                String doubleQuotedCand = "\"" + cTrim + "\"";
                String singleQuotedCand = "'" + cTrim + "'";
                if (content.contains(doubleQuotedCand)) {
                    rep = rTrim.startsWith("\"") && rTrim.endsWith("\"") ? rTrim : "\"" + rTrim + "\"";
                    updatedContent = content.replace(doubleQuotedCand, rep);
                    break;
                }
                if (!content.contains(singleQuotedCand)) continue;
                rep = rTrim.startsWith("'") && rTrim.endsWith("'") ? rTrim : "'" + rTrim + "'";
                updatedContent = content.replace(singleQuotedCand, rep);
                break;
            }
        }
        if (updatedContent == null && !content.isEmpty()) {
            for (String candidate : candidates) {
                Matcher ciMatcher;
                if (candidate == null || candidate.trim().isEmpty() || candidate.trim().equalsIgnoreCase(replacementText.trim()) || !(ciMatcher = Pattern.compile(Pattern.quote(candidate.trim()), 66).matcher(content)).find()) continue;
                updatedContent = ciMatcher.replaceAll(Matcher.quoteReplacement(replacementText));
                break;
            }
        }
        if (updatedContent == null && !content.isEmpty()) {
            Pattern linePattern;
            Matcher m;
            String varName = this.extractAssignmentVarName(originalValue);
            if (varName == null) {
                varName = this.extractAssignmentVarName(changeValue);
            }
            if (varName != null && (m = (linePattern = Pattern.compile("(?m)^([ \\t]*" + Pattern.quote(varName) + "[ \\t]*=[ \\t]*)([^\\r\\n#]+)")).matcher(content)).find()) {
                updatedContent = replacementText.contains("=")
                        ? content.substring(0, m.start()) + replacementText + content.substring(m.end())
                        : content.substring(0, m.start(2)) + replacementText + content.substring(m.end(2));
            }
        }
        if (updatedContent == null) {
            updatedContent = !content.isEmpty() && content.contains(replacementText)
                    ? content
                    : (content.trim().isEmpty() || !content.trim().contains("\n")
                    ? replacementText
                    : (content.endsWith("\n") ? content + replacementText + "\n" : content + "\n" + replacementText + "\n"));
        }
        return updatedContent;
    }

    private void patchTextOrPythonFileLocked(File target, String widgetKey, String originalValue, String changeValue, String replacementText, boolean isActive) throws IOException {
        String content = this.readEntireTextFileWithBufferedReader(target);
        String updatedContent = this.computePatchedTextContentLocked(content, widgetKey, originalValue, changeValue, replacementText, isActive);
        this.writeEntireTextFileWithBufferedWriter(target, updatedContent);
        this.lastWrittenByWidget.put(widgetKey, replacementText);
    }

    private void addCandidateVariants(List<String> list, String raw) {
        String trimmed;
        if (raw == null || raw.isEmpty()) {
            return;
        }
        if (!list.contains(raw)) {
            list.add(raw);
        }
        if (!(trimmed = raw.trim()).isEmpty() && !list.contains(trimmed)) {
            list.add(trimmed);
        }
    }

    private String extractAssignmentVarName(String expr) {
        if (expr == null) {
            return null;
        }
        Matcher m = Pattern.compile("^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*=").matcher(expr);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private int parseOffsetString(String rawOffset) {
        if (rawOffset == null || rawOffset.trim().isEmpty()) {
            return 0;
        }
        String clean = rawOffset.trim().toLowerCase();
        try {
            if (clean.startsWith("0x")) {
                return Math.max(0, Integer.parseInt(clean.substring(2), 16));
            }
            return Math.max(0, Integer.parseInt(clean));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private byte[] parsePayloadBytes(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return new byte[]{1};
        }
        String clean = payload.trim();
        try {
            if (clean.startsWith("0x") || clean.startsWith("0X")) {
                int v = Integer.parseInt(clean.substring(2), 16);
                return new byte[]{(byte) (v & 0xFF)};
            }
            int v = Integer.parseInt(clean);
            if (v >= 0 && v <= 255) {
                return new byte[]{(byte) v};
            }
            return ByteBuffer.allocate(4).putInt(v).array();
        } catch (NumberFormatException e) {
            return clean.getBytes(StandardCharsets.UTF_8);
        }
    }

    public File resolveTargetFile(File fallbackDir, String rawPath) {
        File foundByName;
        if (rawPath == null || rawPath.trim().isEmpty()) {
            return new File(fallbackDir, "studio_overlay_target.bin");
        }
        String clean = rawPath.trim();
        if (clean.startsWith("\"") && clean.endsWith("\"") || clean.startsWith("'") && clean.endsWith("'")) {
            clean = clean.substring(1, clean.length() - 1).trim();
        }
        if (clean.startsWith("file://")) {
            clean = clean.substring("file://".length());
        }
        int primaryIdx = clean.indexOf("primary:");
        if (primaryIdx >= 0) {
            clean = "/storage/emulated/0/" + clean.substring(primaryIdx + "primary:".length());
        }
        if (clean.isEmpty() || "studio_overlay_target.bin".equalsIgnoreCase(clean)) {
            return new File(fallbackDir, "studio_overlay_target.bin");
        }
        File candidate = new File(clean);
        if (candidate.isAbsolute()) {
            File realExt;
            if (clean.contains("/target_scripts/") && (realExt = this.findExternalFileByName(candidate.getName())) != null && realExt.exists()) {
                return realExt;
            }
            if (candidate.exists()) {
                return candidate;
            }
            String relFromExt = null;
            if (clean.startsWith("/sdcard/")) {
                relFromExt = clean.substring("/sdcard/".length());
            } else if (clean.startsWith("/storage/emulated/0/")) {
                relFromExt = clean.substring("/storage/emulated/0/".length());
            } else if (clean.startsWith("/mnt/sdcard/")) {
                relFromExt = clean.substring("/mnt/sdcard/".length());
            }
            if (relFromExt != null) {
                File[] roots = new File[]{new File("/storage/emulated/0"), Environment.getExternalStorageDirectory(), new File("/sdcard")};
                for (File root : roots) {
                    if (root == null) continue;
                    File alias = new File(root, relFromExt);
                    if (alias.exists()) {
                        return alias;
                    }
                    File ciMatch = this.resolveCaseInsensitivePath(root, relFromExt);
                    if (ciMatch == null || !ciMatch.exists()) continue;
                    return ciMatch;
                }
                return new File("/storage/emulated/0", relFromExt);
            }
            // Keep exact absolute path (including /data/..., /system/..., /storage/..., /sdcard/..., /mnt/...)
            // so restricted paths are never silently redirected to internal fallbackDir!
            return candidate;
        }
        File extRoot = new File("/storage/emulated/0");
        File extCandidate = new File(extRoot, clean);
        if (extCandidate.exists()) {
            return extCandidate;
        }
        File ciExt = this.resolveCaseInsensitivePath(extRoot, clean);
        if (ciExt != null && ciExt.exists()) {
            return ciExt;
        }
        if (!clean.contains("/") && (foundByName = this.findExternalFileByName(clean)) != null && foundByName.exists()) {
            return foundByName;
        }
        if (extCandidate.getParentFile() != null && extCandidate.getParentFile().exists() || clean.contains("/") || clean.toLowerCase(Locale.US).endsWith(".py")) {
            return extCandidate;
        }
        return new File(fallbackDir, clean);
    }

    private File findExternalFileByName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return null;
        }
        String targetName = fileName.trim();
        try {
            File extRoot = new File("/storage/emulated/0");
            if (!extRoot.exists()) {
                extRoot = Environment.getExternalStorageDirectory();
            }
            if (extRoot == null || !extRoot.exists()) {
                return null;
            }
            File direct = new File(extRoot, targetName);
            if (direct.exists() && direct.isFile()) {
                return direct;
            }
            File[] topDirs = extRoot.listFiles();
            if (topDirs != null) {
                for (File dir : topDirs) {
                    File sub;
                    if (dir == null || !dir.isDirectory() || dir.getName().startsWith(".") || !(sub = new File(dir, targetName)).exists() || !sub.isFile()) continue;
                    return sub;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private File resolveCaseInsensitivePath(File rootDir, String relativePath) {
        if (rootDir == null || !rootDir.exists() || relativePath == null || relativePath.isEmpty()) {
            return null;
        }
        String[] parts = relativePath.split("/");
        File current = rootDir;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            File exact = new File(current, part);
            if (exact.exists()) {
                current = exact;
                continue;
            }
            File[] children = current.listFiles();
            File matched = null;
            if (children != null) {
                for (File child : children) {
                    if (!child.getName().equalsIgnoreCase(part)) continue;
                    matched = child;
                    break;
                }
            }
            current = matched != null ? matched : exact;
        }
        return current;
    }

    private void patchByteOffsetAndRebuildKeyValueLocked(File file, int offset, byte[] payload) throws IOException {
        if (!file.exists() || file.length() < 64L) {
            this.writeFullStateToFileLocked(file);
            return;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            long crcVal;
            raf.seek(offset);
            raf.write(payload);
            byte[] headerPrefix = new byte[60];
            raf.seek(0L);
            raf.readFully(headerPrefix);
            CRC32 crc32 = new CRC32();
            crc32.update(headerPrefix);
            this.currentState.crc32Value = crcVal = crc32.getValue();
            raf.seek(60L);
            raf.writeInt((int) crcVal);
            raf.seek(0L);
            raf.readFully(this.currentState.rawHeaderBytes);
            String kvBlock = this.buildKeyValueBlockLocked();
            byte[] kvBytes = kvBlock.getBytes(StandardCharsets.UTF_8);
            raf.seek(64L);
            raf.write(kvBytes);
            raf.setLength(64 + kvBytes.length);
            this.currentState.rawTextContent = kvBlock;
        }
    }

    private void writeFullStateToFileLocked(File file) throws IOException {
        String kvBlock;
        long crcVal;
        byte[] header = new byte[64];
        System.arraycopy(ConfigParameterSpec.MAGIC_BYTES, 0, header, 0, 4);
        header[4] = (byte) (this.currentState.hwAccel ? 1 : 0);
        header[5] = (byte) (this.currentState.zeroCopyDma ? 1 : 0);
        header[6] = (byte) (this.currentState.quantInt8 ? 1 : 0);
        header[7] = (byte) (this.currentState.kernelTelemetry ? 1 : 0);
        ByteBuffer.wrap(header, 8, 4).putInt(this.currentState.workerThreads);
        ByteBuffer.wrap(header, 12, 4).putInt(this.currentState.freqGovernorPct);
        ByteBuffer.wrap(header, 16, 4).putInt(this.currentState.vramCeilingMb);
        this.writeFixedAsciiToBuffer(header, 20, 16, this.currentState.executionNodeTag);
        this.writeFixedAsciiToBuffer(header, 36, 10, this.currentState.customRegisterHex);
        this.writeFixedAsciiToBuffer(header, 46, 14, this.currentState.operatingMode);
        CRC32 crc32 = new CRC32();
        crc32.update(header, 0, 60);
        this.currentState.crc32Value = crcVal = crc32.getValue();
        ByteBuffer.wrap(header, 60, 4).putInt((int) crcVal);
        System.arraycopy(header, 0, this.currentState.rawHeaderBytes, 0, 64);
        this.currentState.rawTextContent = kvBlock = this.buildKeyValueBlockLocked();
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(0L);
            raf.write(header);
            byte[] kvBytes = kvBlock.getBytes(StandardCharsets.UTF_8);
            raf.write(kvBytes);
            raf.setLength(64 + kvBytes.length);
        }
    }

    private void writeFixedAsciiToBuffer(byte[] buffer, int offset, int maxLen, String text) {
        byte[] bytes = (text == null ? "" : text).getBytes(StandardCharsets.US_ASCII);
        int copyLen = Math.min(bytes.length, maxLen);
        System.arraycopy(bytes, 0, buffer, offset, copyLen);
    }

    private String buildKeyValueBlockLocked() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n# --- FLOATCONFIG LOCAL RUNTIME PARAMETERS ---\n");
        sb.append("# Binary Header: 64 bytes [0x00..0x3F] | CRC32: 0x").append(String.format("%08X", this.currentState.crc32Value)).append("\n");
        sb.append("operating_mode").append("=").append(this.currentState.operatingMode).append("\n");
        sb.append("hw_tensor_accel").append("=").append(this.currentState.hwAccel ? "1" : "0").append(" # @0x04\n");
        sb.append("zero_copy_dma").append("=").append(this.currentState.zeroCopyDma ? "1" : "0").append(" # @0x05\n");
        sb.append("quant_int8_mode").append("=").append(this.currentState.quantInt8 ? "1" : "0").append(" # @0x06\n");
        sb.append("kernel_telemetry").append("=").append(this.currentState.kernelTelemetry ? "1" : "0").append(" # @0x07\n");
        sb.append("worker_thread_count").append("=").append(this.currentState.workerThreads).append(" # @0x08\n");
        sb.append("freq_governor_pct").append("=").append(this.currentState.freqGovernorPct).append(" # @0x0C\n");
        sb.append("vram_ceiling_mb").append("=").append(this.currentState.vramCeilingMb).append(" # @0x10\n");
        sb.append("execution_node_tag").append("=").append(this.currentState.executionNodeTag).append(" # @0x14\n");
        sb.append("custom_register_hex").append("=").append(this.currentState.customRegisterHex).append(" # @0x24\n");
        return sb.toString();
    }

    private String getToggleOldValueLocked(String key) {
        switch (key) {
            case "hw_tensor_accel": {
                return this.currentState.hwAccel ? "1 (0x01)" : "0 (0x00)";
            }
            case "zero_copy_dma": {
                return this.currentState.zeroCopyDma ? "1 (0x01)" : "0 (0x00)";
            }
            case "quant_int8_mode": {
                return this.currentState.quantInt8 ? "1 (0x01)" : "0 (0x00)";
            }
            case "kernel_telemetry": {
                return this.currentState.kernelTelemetry ? "1 (0x01)" : "0 (0x00)";
            }
        }
        return "0";
    }

    private void applyToggleToMemoryLocked(String key, boolean enabled) {
        switch (key) {
            case "hw_tensor_accel": {
                this.currentState.hwAccel = enabled;
                break;
            }
            case "zero_copy_dma": {
                this.currentState.zeroCopyDma = enabled;
                break;
            }
            case "quant_int8_mode": {
                this.currentState.quantInt8 = enabled;
                break;
            }
            case "kernel_telemetry": {
                this.currentState.kernelTelemetry = enabled;
            }
        }
    }

    private String getSliderOldValueLocked(String key) {
        switch (key) {
            case "worker_thread_count": {
                return String.valueOf(this.currentState.workerThreads);
            }
            case "freq_governor_pct": {
                return String.valueOf(this.currentState.freqGovernorPct);
            }
            case "vram_ceiling_mb": {
                return String.valueOf(this.currentState.vramCeilingMb);
            }
        }
        return "0";
    }

    private void applySliderToMemoryLocked(String key, int value) {
        switch (key) {
            case "worker_thread_count": {
                this.currentState.workerThreads = Math.max(1, Math.min(16, value));
                break;
            }
            case "freq_governor_pct": {
                this.currentState.freqGovernorPct = Math.max(25, Math.min(100, value));
                break;
            }
            case "vram_ceiling_mb": {
                this.currentState.vramCeilingMb = Math.max(64, Math.min(2048, value));
            }
        }
    }

    private void notifyWriteSuccess(String key, int offset, String oldVal, String newVal, long durationMicros, ConfigParameterSpec.StateSnapshot snapshot) {
        Runnable task = () -> {
            for (OnStateWriteListener listener : this.listeners) {
                listener.onWriteSuccess(key, offset, oldVal, newVal, durationMicros, snapshot);
            }
        };
        if (this.mainHandler != null) {
            this.mainHandler.post(task);
        } else {
            task.run();
        }
    }

    private void notifyDiagnosticWriteError(WriteDiagnosticReport report) {
        this.lastDiagnosticReport = report;
        String summaryMessage = report.whyFailedTitle + " — Path: " + report.targetFilePath
                + " (" + report.rawKernelError + "). "
                + (report.requiresShizuku ? "Use Shizuku to fix Android 15 restricted path." : "Check storage permissions.");
        Runnable task = () -> {
            for (OnStateWriteListener listener : this.listeners) {
                listener.onWriteError(report.componentLabel, summaryMessage);
                listener.onWriteDiagnosticError(report);
            }
        };
        if (this.mainHandler != null) {
            this.mainHandler.post(task);
        } else {
            task.run();
        }
    }

    private void notifyWriteError(String key, String message) {
        WriteDiagnosticReport report = this.buildFailureDiagnosticReport(
                this.appContext,
                key,
                this.activeFile != null ? this.activeFile.getAbsolutePath() : "studio_overlay_target.bin",
                message,
                null
        );
        this.notifyDiagnosticWriteError(report);
    }

    public static interface OnStateWriteListener {
        public void onWriteSuccess(String var1, int var2, String var3, String var4, long var5, ConfigParameterSpec.StateSnapshot var7);

        public void onWriteError(String var1, String var2);

        default public void onWriteDiagnosticError(WriteDiagnosticReport report) {
        }
    }
}
