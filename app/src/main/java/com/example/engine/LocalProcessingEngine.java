/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 */
package com.example.engine;

import android.content.Context;
import com.example.engine.LocalConfigStateWriter;
import java.io.File;

public class LocalProcessingEngine {
    public static final String DEFAULT_ENGINE_FILENAME = "engine_runtime.conf";
    public static final String DEFAULT_MANUAL_FILENAME = "manual_override_state.conf";
    private final Context appContext;
    private final LocalConfigStateWriter stateWriter;

    public LocalProcessingEngine(Context context) {
        this.appContext = context.getApplicationContext();
        this.stateWriter = LocalConfigStateWriter.getInstance();
    }

    public String getDefaultConfigPath() {
        return new File(this.appContext.getFilesDir(), DEFAULT_ENGINE_FILENAME).getAbsolutePath();
    }

    public String getPresetPath(String presetName) {
        return new File(this.appContext.getFilesDir(), presetName).getAbsolutePath();
    }

    public void initializeEngine(String rawPathInput, EngineInitCallback callback) {
        if (rawPathInput == null || rawPathInput.trim().isEmpty()) {
            if (callback != null) {
                callback.onEngineFailure("Configuration path cannot be empty. Provide a valid path or select Manual Mode.");
            }
            return;
        }
        File targetFile = this.resolveWritableFile(rawPathInput.trim(), DEFAULT_ENGINE_FILENAME);
        this.stateWriter.initializeFileStateAsync(targetFile, "ENGINE_INIT", () -> {
            if (callback != null) {
                callback.onEngineInitialized(targetFile, "ENGINE_INIT", "Local Processing Engine initialized [64B Header + KV Block @ " + targetFile.getName() + "]");
            }
        });
    }

    public void launchManualModeBypass(String optionalPathInput, EngineInitCallback callback) {
        String candidate = optionalPathInput == null || optionalPathInput.trim().isEmpty() ? new File(this.appContext.getFilesDir(), DEFAULT_MANUAL_FILENAME).getAbsolutePath() : optionalPathInput.trim();
        File targetFile = this.resolveWritableFile(candidate, DEFAULT_MANUAL_FILENAME);
        this.stateWriter.initializeFileStateAsync(targetFile, "MANUAL_MODE", () -> {
            if (callback != null) {
                callback.onEngineInitialized(targetFile, "MANUAL_MODE", "Manual Mode active (Engine validation bypassed) -> " + targetFile.getName());
            }
        });
    }

    private File resolveWritableFile(String inputPath, String fallbackName) {
        File candidate = new File(inputPath);
        if (!candidate.isAbsolute()) {
            return new File(this.appContext.getFilesDir(), inputPath);
        }
        File parent = candidate.getParentFile();
        if (parent != null && (parent.canWrite() || parent.mkdirs() || parent.exists())) {
            try {
                if (!candidate.exists()) {
                    candidate.createNewFile();
                }
                if (candidate.canWrite()) {
                    return candidate;
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        String safeName = candidate.getName().isEmpty() ? fallbackName : candidate.getName();
        return new File(this.appContext.getFilesDir(), safeName);
    }

    public static interface EngineInitCallback {
        public void onEngineInitialized(File var1, String var2, String var3);

        public void onEngineFailure(String var1);
    }
}
