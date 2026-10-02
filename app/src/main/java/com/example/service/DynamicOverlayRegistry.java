/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  org.json.JSONArray
 *  org.json.JSONObject
 */
package com.example.service;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class DynamicOverlayRegistry {
    private static volatile String activeProjectName = "";
    private static volatile String activePackageName = "com.aistudio.floatconfig.app001";
    private static volatile String activeOverlayTitle = "";
    private static volatile String activeFloatingLogoPath = "";
    private static volatile String activeAppLogoPath = "";
    private static volatile int activeCanvasWidthDp = 310;
    private static volatile int activeCanvasHeightDp = 380;
    private static volatile String activeCanvasBgHex = "#FFFFFF";
    private static volatile String activeCanvasBgImagePath = "";
    private static volatile boolean activeAutoFixSize = false;
    private static volatile boolean bundledStandaloneLoaded = false;
    private static final List<OverlayItemSpec> activeItems = Collections.synchronizedList(new ArrayList());

    public static synchronized void updateActiveOverlay(String title, int widthDp, int heightDp, String bgHex, List<OverlayItemSpec> items) {
        DynamicOverlayRegistry.updateActiveOverlay(title, widthDp, heightDp, bgHex, false, items);
    }

    public static synchronized void updateActiveOverlay(String title, int widthDp, int heightDp, String bgHex, boolean autoFixSize, List<OverlayItemSpec> items) {
        DynamicOverlayRegistry.updateActiveOverlay(title, activeFloatingLogoPath, widthDp, heightDp, bgHex, autoFixSize, items);
    }

    public static synchronized void updateActiveOverlay(String title, String floatingLogoPath, int widthDp, int heightDp, String bgHex, boolean autoFixSize, List<OverlayItemSpec> items) {
        DynamicOverlayRegistry.updateActiveOverlay(title, floatingLogoPath, widthDp, heightDp, bgHex, activeCanvasBgImagePath, autoFixSize, items);
    }

    public static synchronized void updateActiveOverlay(String title, String floatingLogoPath, int widthDp, int heightDp, String bgHex, String bgImagePath, boolean autoFixSize, List<OverlayItemSpec> items) {
        activeOverlayTitle = title != null ? title : "";
        activeFloatingLogoPath = floatingLogoPath != null ? floatingLogoPath : "";
        activeCanvasWidthDp = Math.max(130, widthDp);
        activeCanvasHeightDp = Math.max(130, heightDp);
        activeCanvasBgHex = bgHex != null && !bgHex.trim().isEmpty() ? bgHex : "#FFFFFF";
        activeCanvasBgImagePath = bgImagePath != null ? bgImagePath.trim() : "";
        activeAutoFixSize = autoFixSize;
        if (items != null) {
            for (OverlayItemSpec incoming : items) {
                for (OverlayItemSpec existing : activeItems) {
                    if (existing.id != incoming.id) continue;
                    existing.type = incoming.type;
                    existing.label = incoming.label;
                    existing.posXDp = incoming.posXDp;
                    existing.posYDp = incoming.posYDp;
                    existing.widthDp = incoming.widthDp;
                    existing.heightDp = incoming.heightDp;
                    existing.bgColorHex = incoming.bgColorHex;
                    existing.textColorHex = incoming.textColorHex;
                    existing.bgImagePath = incoming.bgImagePath;
                    existing.customImagePath = incoming.customImagePath;
                    existing.soundTrigger = incoming.soundTrigger;
                    existing.customSoundPath = incoming.customSoundPath;
                    existing.offSoundTrigger = incoming.offSoundTrigger;
                    existing.offCustomSoundPath = incoming.offCustomSoundPath;
                    existing.targetFilePath = incoming.targetFilePath;
                    existing.byteOffsetHex = incoming.byteOffsetHex;
                    existing.onPayloadHex = incoming.onPayloadHex;
                    existing.offPayloadHex = incoming.offPayloadHex;
                    existing.sliderMax = incoming.sliderMax;
                    existing.currentValue = incoming.currentValue;
                    existing.linkUrl = incoming.linkUrl;
                    existing.borderColorHex = incoming.borderColorHex;
                    existing.borderStrokePercent = incoming.borderStrokePercent;
                    existing.borderAnimation = incoming.borderAnimation;
                    existing.parentWidgetIdsCsv = incoming.parentWidgetIdsCsv;
                    existing.isLuaScript = incoming.isLuaScript;
                }
            }
        }
        activeItems.clear();
        if (items != null) {
            activeItems.addAll(items);
        }
    }

    public static synchronized OverlayItemSpec getSpecById(long id, OverlayItemSpec fallback) {
        for (OverlayItemSpec item : activeItems) {
            if (item.id != id) continue;
            return item;
        }
        return fallback;
    }

    public static synchronized String getActiveProjectName() {
        return activeProjectName;
    }

    public static synchronized String getActivePackageName() {
        return activePackageName;
    }

    public static synchronized boolean isActiveAutoFixSize() {
        return activeAutoFixSize;
    }

    public static synchronized String getActiveOverlayTitle() {
        return activeOverlayTitle;
    }

    public static synchronized String getActiveFloatingLogoPath() {
        return activeFloatingLogoPath;
    }

    public static synchronized String getActiveAppLogoPath() {
        return activeAppLogoPath;
    }

    public static synchronized int getActiveCanvasWidthDp() {
        return activeCanvasWidthDp;
    }

    public static synchronized int getActiveCanvasHeightDp() {
        return activeCanvasHeightDp;
    }

    public static synchronized void setActiveCanvasSizeDp(int widthDp, int heightDp) {
        activeCanvasWidthDp = Math.max(130, widthDp);
        activeCanvasHeightDp = Math.max(130, heightDp);
    }

    public static synchronized String getActiveCanvasBgHex() {
        return activeCanvasBgHex;
    }

    public static synchronized String getActiveCanvasBgImagePath() {
        return activeCanvasBgImagePath;
    }

    public static synchronized void setActiveCanvasBgImagePath(String bgImagePath) {
        activeCanvasBgImagePath = bgImagePath != null ? bgImagePath.trim() : "";
    }

    public static synchronized List<OverlayItemSpec> getActiveItems() {
        return new ArrayList<OverlayItemSpec>(activeItems);
    }

    public static synchronized boolean isBundledStandaloneApk(Context context) {
        if (context == null) {
            return false;
        }
        try (InputStream is = context.getAssets().open("overlay_config.json")) {
            return is != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static synchronized boolean loadFromBundledAssetsIfEmpty(Context context) {
        if (!activeItems.isEmpty() || context == null) {
            return bundledStandaloneLoaded || !activeItems.isEmpty();
        }
        try (InputStream is = context.getAssets().open("overlay_config.json")) {
            int len;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            while ((len = is.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
            String jsonStr = new String(baos.toByteArray(), StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(jsonStr);
            activeProjectName = root.optString("projectName", "");
            activePackageName = root.optString("packageName", context.getPackageName());
            String title = root.optString("overlayTitle", "");
            if (title == null || title.trim().isEmpty()) {
                title = activeProjectName;
            }
            activeAppLogoPath = DynamicOverlayRegistry.extractBundledAssetIfPresent(context, root.optString("appLogoAsset", ""), "app_logo.png");
            String floatingLogo = DynamicOverlayRegistry.extractBundledAssetIfPresent(context, root.optString("floatingLogoAsset", ""), "floating_logo.png");
            if (floatingLogo == null || floatingLogo.trim().isEmpty()) {
                floatingLogo = activeAppLogoPath;
            }
            int widthDp = root.optInt("canvasWidthDp", 216);
            int heightDp = root.optInt("canvasHeightDp", 290);
            String bgHex = root.optString("canvasBgColorHex", "#FFFFFF");
            String bgImage = DynamicOverlayRegistry.extractBundledAssetIfPresent(context, root.optString("canvasBgImageAsset", ""), "canvas_bg.png");
            boolean autoFix = root.optBoolean("autoFixSize", false);
            JSONArray arr = root.optJSONArray("components");
            ArrayList<OverlayItemSpec> parsed = new ArrayList<OverlayItemSpec>();
            if (arr != null) {
                for (int i = 0; i < arr.length(); ++i) {
                    JSONObject c = arr.getJSONObject(i);
                    OverlayItemSpec spec = new OverlayItemSpec();
                    spec.id = c.optLong("id", (long)i + 1L);
                    spec.type = c.optString("type", "TOGGLE");
                    spec.label = c.optString("label", "Widget #" + (i + 1));
                    spec.posXDp = c.optInt("x", 10);
                    spec.posYDp = c.optInt("y", 10 + i * 48);
                    spec.widthDp = c.optInt("width", 180);
                    spec.heightDp = c.optInt("height", 44);
                    spec.bgColorHex = c.optString("bgHex", "#FFFFFF");
                    spec.textColorHex = c.optString("textHex", "#0F172A");
                    spec.bgImagePath = DynamicOverlayRegistry.extractBundledAssetIfPresent(context, c.optString("bgImageAsset", ""), "widget_bg_" + spec.id + ".png");
                    spec.soundTrigger = c.optString("onSound", "NONE");
                    spec.offSoundTrigger = c.optString("offSound", "NONE");
                    String customFileName = c.optString("customImageFileName", "");
                    if (customFileName == null || customFileName.trim().isEmpty()) {
                        String rawAsset = c.optString("customImageAsset", "");
                        int slashIdx = rawAsset.lastIndexOf('/');
                        customFileName = slashIdx >= 0 ? rawAsset.substring(slashIdx + 1) : ("img_" + spec.id + ".bin");
                    }
                    spec.customImagePath = DynamicOverlayRegistry.extractBundledAssetIfPresent(context, c.optString("customImageAsset", ""), customFileName);
                    spec.customSoundPath = DynamicOverlayRegistry.extractBundledAssetIfPresent(context, c.optString("onSoundAsset", ""), "on_snd_" + spec.id + ".mp3");
                    spec.offCustomSoundPath = DynamicOverlayRegistry.extractBundledAssetIfPresent(context, c.optString("offSoundAsset", ""), "off_snd_" + spec.id + ".mp3");
                    spec.targetFilePath = c.optString("targetFile", "");
                    spec.byteOffsetHex = c.optString("offsetHex", "0x04");
                    spec.onPayloadHex = c.optString("onHex", "0x01");
                    spec.offPayloadHex = c.optString("offHex", "0x00");
                    spec.sliderMax = c.optInt("sliderMax", 100);
                    spec.currentValue = c.optString("currentValue", "0");
                    spec.linkUrl = c.optString("linkUrl", "");
                    spec.borderColorHex = c.optString("borderColorHex", "#38BDF8");
                    spec.borderStrokePercent = c.optInt("borderStrokePercent", 25);
                    spec.borderAnimation = c.optString("borderAnimation", "NONE");
                    parsed.add(spec);
                }
            }
            DynamicOverlayRegistry.updateActiveOverlay(title, floatingLogo, widthDp, heightDp, bgHex, bgImage, autoFix, parsed);
            bundledStandaloneLoaded = true;
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String extractBundledAssetIfPresent(Context context, String assetRelPath, String localFileName) {
        if (assetRelPath == null || assetRelPath.trim().isEmpty()) {
            return "";
        }
        try (InputStream is = context.getAssets().open(assetRelPath.trim())) {
            File dir = new File(context.getFilesDir(), "bundled_assets");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File outFile = new File(dir, localFileName);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                int r;
                byte[] buf = new byte[4096];
                while ((r = is.read(buf)) != -1) {
                    fos.write(buf, 0, r);
                }
            }
            return outFile.getAbsolutePath();
        } catch (Exception e) {
            return "";
        }
    }

    public static class OverlayItemSpec {
        public long id;
        public String type;
        public String label;
        public int posXDp;
        public int posYDp;
        public int widthDp;
        public int heightDp;
        public String bgColorHex;
        public String textColorHex;
        public String bgImagePath = "";
        public String customImagePath;
        public String soundTrigger;
        public String customSoundPath;
        public String offSoundTrigger;
        public String offCustomSoundPath;
        public String targetFilePath;
        public String byteOffsetHex;
        public String onPayloadHex;
        public String offPayloadHex;
        public int sliderMax;
        public String currentValue;
        public String linkUrl;
        public String borderColorHex = "#38BDF8";
        public int borderStrokePercent = 25;
        public String borderAnimation = "NONE";
        public String parentWidgetIdsCsv = "";
        public boolean isLuaScript = false;
    }
}
