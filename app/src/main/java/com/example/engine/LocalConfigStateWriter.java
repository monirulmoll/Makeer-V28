/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.app.AppOpsManager
 *  android.content.Context
 *  android.content.Intent
 *  android.net.Uri
 *  android.os.Build$VERSION
 *  android.os.Environment
 *  android.os.Handler
 *  android.os.Looper
 *  android.os.Process
 *  androidx.core.content.ContextCompat
 */
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
import com.example.engine.ConfigParameterSpec;
import com.example.service.DynamicOverlayRegistry;
import java.io.BufferedReader;
import java.io.BufferedWriter;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private static Handler createSafeMainHandler() {
        try {
            Looper looper = Looper.getMainLooper();
            return looper != null ? new Handler(looper) : null;
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static LocalConfigStateWriter getInstance() {
        if (instance != null) return instance;
        Class<LocalConfigStateWriter> clazz = LocalConfigStateWriter.class;
        synchronized (LocalConfigStateWriter.class) {
            if (instance != null) return instance;
            instance = new LocalConfigStateWriter();
            // ** MonitorExit[var0] (shouldn't be in output)
            return instance;
        }
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
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            try {
                int mode;
                AppOpsManager appOps = (AppOpsManager)context.getSystemService("appops");
                if (appOps != null && (mode = appOps.unsafeCheckOpNoThrow("android:manage_external_storage", Process.myUid(), context.getPackageName())) == 0) {
                    return true;
                }
            }
            catch (Throwable appOps) {
                // empty catch block
            }
            try {
                if (Environment.isExternalStorageLegacy()) {
                    boolean hasWrite;
                    boolean bl = hasWrite = ContextCompat.checkSelfPermission((Context)context, (String)"android.permission.WRITE_EXTERNAL_STORAGE") == 0;
                    if (hasWrite) {
                        return true;
                    }
                }
            }
            catch (Throwable hasWrite) {
                // empty catch block
            }
            return false;
        }
        try {
            boolean hasRead;
            boolean hasWrite = ContextCompat.checkSelfPermission((Context)context, (String)"android.permission.WRITE_EXTERNAL_STORAGE") == 0;
            boolean bl = hasRead = ContextCompat.checkSelfPermission((Context)context, (String)"android.permission.READ_EXTERNAL_STORAGE") == 0;
            if (hasWrite && hasRead) {
                return true;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return false;
    }

    public static void requestStoragePermission(Context context) {
        if (context == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Intent intent = new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION", Uri.parse((String)("package:" + context.getPackageName())));
                intent.addFlags(0x10000000);
                context.startActivity(intent);
                return;
            }
            catch (Throwable intent) {
                try {
                    Intent fallback = new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION");
                    fallback.addFlags(0x10000000);
                    context.startActivity(fallback);
                    return;
                }
                catch (Throwable fallback) {
                    // empty catch block
                }
            }
        }
        try {
            Intent appDetails = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS", Uri.parse((String)("package:" + context.getPackageName())));
            appDetails.addFlags(0x10000000);
            context.startActivity(appDetails);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    public static void requestOverlayPermission(Context context) {
        if (context == null) {
            return;
        }
        try {
            Intent intent = new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION", Uri.parse((String)("package:" + context.getPackageName())));
            intent.addFlags(0x10000000);
            context.startActivity(intent);
            return;
        }
        catch (Throwable intent) {
            try {
                Intent fallback = new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION");
                fallback.addFlags(0x10000000);
                context.startActivity(fallback);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            return;
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

    public void initializeFileStateAsync(File targetFile, String operatingMode, Runnable onComplete) {
        this.bindTargetFile(targetFile, operatingMode);
        this.fileIoExecutor.execute(() -> {
            long startNs = System.nanoTime();
            try {
                File parent = targetFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                LocalConfigStateWriter localConfigStateWriter = this;
                synchronized (localConfigStateWriter) {
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
            }
            catch (IOException e) {
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
            LocalConfigStateWriter localConfigStateWriter = this;
            synchronized (localConfigStateWriter) {
                if (this.activeFile == null) {
                    this.notifyWriteError(key, "No target configuration file bound.");
                    return;
                }
                oldVal = this.getToggleOldValueLocked(key);
                this.applyToggleToMemoryLocked(key, enabled);
                try {
                    this.patchByteOffsetAndRebuildKeyValueLocked(this.activeFile, byteOffset, new byte[]{(byte)(enabled ? 1 : 0)});
                }
                catch (IOException e) {
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
            LocalConfigStateWriter localConfigStateWriter = this;
            synchronized (localConfigStateWriter) {
                if (this.activeFile == null) {
                    this.notifyWriteError(key, "No target configuration file bound.");
                    return;
                }
                oldVal = this.getSliderOldValueLocked(key);
                this.applySliderToMemoryLocked(key, value);
                byte[] intBytes = ByteBuffer.allocate(4).putInt(value).array();
                try {
                    this.patchByteOffsetAndRebuildKeyValueLocked(this.activeFile, byteOffset, intBytes);
                }
                catch (IOException e) {
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
            String sanitized;
            long startNs = System.nanoTime();
            String string = sanitized = rawInput == null ? "" : rawInput.trim();
            if (sanitized.isEmpty()) {
                sanitized = "DEFAULT";
            }
            if (sanitized.length() > maxSlotLength) {
                sanitized = sanitized.substring(0, maxSlotLength);
            }
            LocalConfigStateWriter localConfigStateWriter = this;
            synchronized (localConfigStateWriter) {
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
                }
                catch (IOException e) {
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

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public boolean applyWidgetPatchSync(File fallbackDir, String widgetKey, String widgetType, String targetFilePath, String byteOffsetHex, String originalValue, String changeValue, String liveValue, boolean isActive, String componentLabel, String customSourceFilePath) {
        ConfigParameterSpec.StateSnapshot snapshot;
        long startNs = System.nanoTime();
        int offset = this.parseOffsetString(byteOffsetHex);
        File target = this.resolveTargetFile(fallbackDir, targetFilePath);
        String safeKey = widgetKey != null && !widgetKey.trim().isEmpty() ? widgetKey.trim() : (componentLabel != null ? componentLabel : "widget");
        String orig = originalValue != null ? originalValue : "";
        String chg = changeValue != null ? changeValue : "";
        String live = liveValue != null ? liveValue : "";
        String type = widgetType != null ? widgetType.toUpperCase(Locale.US) : "BUTTON";
        boolean useTextScriptPatch = this.shouldUseTextOrScriptPatch(target, orig, chg, type);
        String replacementText = this.computeReplacementText(type, orig, chg, live, isActive, useTextScriptPatch);
        String previousVal = this.lastWrittenByWidget.getOrDefault(safeKey, isActive ? orig : chg);
        LocalConfigStateWriter localConfigStateWriter = this;
        synchronized (localConfigStateWriter) {
            try {
                File parent = target.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                String fileReplaceOrMergeSummary = this.tryApplySelectedFileReplaceOrMergeLocked(fallbackDir, target, safeKey, type, live, isActive, customSourceFilePath);
                if (fileReplaceOrMergeSummary != null) {
                    replacementText = fileReplaceOrMergeSummary;
                    this.lastWrittenByWidget.put(safeKey, replacementText);
                } else if (useTextScriptPatch) {
                    this.patchTextOrPythonFileLocked(target, safeKey, orig, chg, replacementText, isActive);
                } else {
                    byte[] payloadBytes = this.parsePayloadBytes(replacementText);
                    try (RandomAccessFile raf = new RandomAccessFile(target, "rw");){
                        if (raf.length() < (long)(offset + payloadBytes.length)) {
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
            }
            catch (IOException e) {
                this.notifyWriteError(componentLabel, "Cannot modify " + target.getAbsolutePath() + " (" + e.getMessage() + "). Grant All Files Access permission.");
                return false;
            }
        }
        long elapsedUs = (System.nanoTime() - startNs) / 1000L;
        this.notifyWriteSuccess(componentLabel, offset, previousVal, replacementText, elapsedUs, snapshot);
        return true;
    }

    private String tryApplySelectedFileReplaceOrMergeLocked(File fallbackDir, File target, String safeKey, String widgetType, String liveValue, boolean isActive, String explicitSourceFilePath) throws IOException {
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
                    spec.currentValue = "SLIDER".equalsIgnoreCase(widgetType) ? (liveValue != null && !liveValue.isEmpty() ? liveValue : (isActive ? "50" : "0")) : (isActive ? "1" : "0");
                    if (explicitSourceFilePath != null && !explicitSourceFilePath.trim().isEmpty()) {
                        spec.customImagePath = explicitSourceFilePath.trim();
                    }
                }
                File specTarget = this.resolveTargetFile(fallbackDir, spec.targetFilePath);
                boolean sameTarget = specTarget.getAbsolutePath().equals(target.getAbsolutePath());
                if (!sameTarget) continue;

                String srcPath = isCurrentTriggeredWidget && explicitSourceFilePath != null && !explicitSourceFilePath.trim().isEmpty() ? explicitSourceFilePath.trim() : (spec.customImagePath != null ? spec.customImagePath.trim() : "");
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
                    }
                    catch (Exception ignored) {
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
                if (target.exists()) {
                    target.delete();
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
        if (target.exists()) {
            target.delete();
        }

        if (activeSourceFiles.size() == 1) {
            File singleSource = activeSourceFiles.get(0);
            this.copyRawFileBytesLocked(singleSource, target);
            if (!markerFile.exists()) {
                try {
                    markerFile.createNewFile();
                }
                catch (Exception ignored) {
                }
            }
            return "Replaced " + target.getName() + " <= " + singleSource.getName();
        }

        // Multiple options ON: merge all selected files into target path with the exact same target filename
        boolean mergeAsText = this.areAllFilesLikelyTextLocked(activeSourceFiles);
        StringBuilder mergedNames = new StringBuilder();
        try (FileOutputStream fos = new FileOutputStream(target, false);){
            byte[] buf = new byte[8192];
            for (int i = 0; i < activeSourceFiles.size(); ++i) {
                File src = activeSourceFiles.get(i);
                if (i > 0) {
                    mergedNames.append(" + ");
                }
                mergedNames.append(src.getName());
                byte lastByte = -1;
                try (FileInputStream fis = new FileInputStream(src);){
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
            }
            catch (Exception ignored) {
            }
        }
        if (!markerFile.exists()) {
            try {
                markerFile.createNewFile();
            }
            catch (Exception ignored) {
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
             FileOutputStream fos = new FileOutputStream(dest, false);){
            int read;
            byte[] buf = new byte[8192];
            while ((read = fis.read(buf)) != -1) {
                fos.write(buf, 0, read);
            }
            fos.flush();
            try {
                fos.getFD().sync();
            }
            catch (Exception ignored) {
            }
        }
    }

    private boolean areAllFilesLikelyTextLocked(List<File> files) {
        for (File f : files) {
            try (FileInputStream fis = new FileInputStream(f);){
                byte[] sample = new byte[512];
                int n = fis.read(sample);
                for (int i = 0; i < n; ++i) {
                    if (sample[i] != 0) continue;
                    return false;
                }
            }
            catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    public String readTargetFilePreview(File fallbackDir, String rawPath) {
        try {
            File target = this.resolveTargetFile(fallbackDir, rawPath);
            if (!target.exists()) {
                return "File not found yet (" + target.getAbsolutePath() + ")";
            }
            if (target.length() == 0L) {
                return "(Empty file: " + target.getName() + ")";
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader((InputStream)new FileInputStream(target), StandardCharsets.UTF_8));){
                char[] buf = new char[4096];
                int read = reader.read(buf);
                if (read > 0) {
                    sb.append(buf, 0, read);
                }
            }
            String text = sb.toString().trim();
            return text.isEmpty() ? "(Empty)" : text;
        }
        catch (Exception e) {
            return "Read blocked (" + e.getMessage() + ")";
        }
    }

    public boolean detectInitialToggleState(File fallbackDir, String rawPath, String originalValue, String changeValue, boolean defaultActive) {
        try {
            String chg;
            File target = this.resolveTargetFile(fallbackDir, rawPath);
            if (!target.exists() || !target.canRead() || target.length() == 0L) {
                return defaultActive;
            }
            String content = this.readEntireTextFileWithBufferedReader(target);
            if (content.isEmpty()) {
                return defaultActive;
            }
            String orig = originalValue != null && !this.isSingleHexOrByte(originalValue.trim()) ? originalValue.trim() : "";
            String string = chg = changeValue != null && !this.isSingleHexOrByte(changeValue.trim()) ? changeValue.trim() : "";
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
        }
        catch (Exception exception) {
            // empty catch block
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
        if (!target.exists() || target.length() == 0L) {
            return "";
        }
        StringBuilder sb = new StringBuilder((int)Math.min(target.length() + 64L, 0x200000L));
        try (FileInputStream fis = new FileInputStream(target);
             InputStreamReader isr = new InputStreamReader((InputStream)fis, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr);){
            int charsRead;
            char[] buffer = new char[8192];
            int totalRead = 0;
            int maxChars = 0x200000;
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
             OutputStreamWriter osw = new OutputStreamWriter((OutputStream)fos, StandardCharsets.UTF_8);
             BufferedWriter writer = new BufferedWriter(osw);){
            writer.write(updatedContent);
            writer.flush();
            try {
                fos.getFD().sync();
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
    }

    private String computeReplacementText(String widgetType, String originalValue, String changeValue, String liveValue, boolean isActive, boolean isTextScriptFile) {
        String live;
        String orig = originalValue != null ? originalValue : "";
        String chg = changeValue != null ? changeValue : "";
        String string = live = liveValue != null ? liveValue : "";
        if ("SLIDER".equals(widgetType)) {
            String valStr;
            String string2 = valStr = live.trim().isEmpty() ? "0" : live.trim();
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
            }
            catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    private void patchTextOrPythonFileLocked(File target, String widgetKey, String originalValue, String changeValue, String replacementText, boolean isActive) throws IOException {
        String lastWritten;
        String chgClean;
        String content = this.readEntireTextFileWithBufferedReader(target);
        ArrayList<String> candidates = new ArrayList<String>();
        String origClean = originalValue != null && !this.isSingleHexOrByte(originalValue.trim()) ? originalValue : "";
        String string = chgClean = changeValue != null && !this.isSingleHexOrByte(changeValue.trim()) ? changeValue : "";
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
                updatedContent = replacementText.contains("=") ? content.substring(0, m.start()) + replacementText + content.substring(m.end()) : content.substring(0, m.start(2)) + replacementText + content.substring(m.end(2));
            }
        }
        if (updatedContent == null) {
            updatedContent = !content.isEmpty() && content.contains(replacementText) ? content : (content.trim().isEmpty() || !content.trim().contains("\n") ? replacementText : (content.endsWith("\n") ? content + replacementText + "\n" : content + "\n" + replacementText + "\n"));
        }
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
        }
        catch (NumberFormatException e) {
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
                return new byte[]{(byte)(v & 0xFF)};
            }
            int v = Integer.parseInt(clean);
            if (v >= 0 && v <= 255) {
                return new byte[]{(byte)v};
            }
            return ByteBuffer.allocate(4).putInt(v).array();
        }
        catch (NumberFormatException e) {
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
                File[] roots;
                for (File root : roots = new File[]{new File("/storage/emulated/0"), Environment.getExternalStorageDirectory(), new File("/sdcard")}) {
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
            if (clean.startsWith("/storage/") || clean.startsWith("/sdcard/") || clean.startsWith("/mnt/")) {
                return candidate;
            }
            File parent = candidate.getParentFile();
            if (parent != null && (parent.exists() || parent.mkdirs())) {
                return candidate;
            }
            String name = candidate.getName().isEmpty() ? "studio_overlay_target.bin" : candidate.getName();
            return new File(fallbackDir, name);
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
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return null;
    }

    private File resolveCaseInsensitivePath(File rootDir, String relativePath) {
        if (rootDir == null || !rootDir.exists() || relativePath == null || relativePath.isEmpty()) {
            return null;
        }
        String[] parts = relativePath.split("/");
        File current = rootDir;
        for (int i = 0; i < parts.length; ++i) {
            String part = parts[i];
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
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw");){
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
            raf.writeInt((int)crcVal);
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
        header[4] = (byte)(this.currentState.hwAccel ? 1 : 0);
        header[5] = (byte)(this.currentState.zeroCopyDma ? 1 : 0);
        header[6] = (byte)(this.currentState.quantInt8 ? 1 : 0);
        header[7] = (byte)(this.currentState.kernelTelemetry ? 1 : 0);
        ByteBuffer.wrap(header, 8, 4).putInt(this.currentState.workerThreads);
        ByteBuffer.wrap(header, 12, 4).putInt(this.currentState.freqGovernorPct);
        ByteBuffer.wrap(header, 16, 4).putInt(this.currentState.vramCeilingMb);
        this.writeFixedAsciiToBuffer(header, 20, 16, this.currentState.executionNodeTag);
        this.writeFixedAsciiToBuffer(header, 36, 10, this.currentState.customRegisterHex);
        this.writeFixedAsciiToBuffer(header, 46, 14, this.currentState.operatingMode);
        CRC32 crc32 = new CRC32();
        crc32.update(header, 0, 60);
        this.currentState.crc32Value = crcVal = crc32.getValue();
        ByteBuffer.wrap(header, 60, 4).putInt((int)crcVal);
        System.arraycopy(header, 0, this.currentState.rawHeaderBytes, 0, 64);
        this.currentState.rawTextContent = kvBlock = this.buildKeyValueBlockLocked();
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw");){
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

    private void notifyWriteError(String key, String message) {
        Runnable task = () -> {
            for (OnStateWriteListener listener : this.listeners) {
                listener.onWriteError(key, message);
            }
        };
        if (this.mainHandler != null) {
            this.mainHandler.post(task);
        } else {
            task.run();
        }
    }

    public static interface OnStateWriteListener {
        public void onWriteSuccess(String var1, int var2, String var3, String var4, long var5, ConfigParameterSpec.StateSnapshot var7);

        public void onWriteError(String var1, String var2);
    }
}
