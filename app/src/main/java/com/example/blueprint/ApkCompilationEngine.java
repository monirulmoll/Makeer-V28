/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  android.graphics.Bitmap
 *  android.graphics.Bitmap$CompressFormat
 *  android.graphics.Bitmap$Config
 *  android.graphics.BitmapFactory
 *  androidx.annotation.NonNull
 *  androidx.annotation.Nullable
 *  com.android.apksig.ApkSigner
 *  com.android.apksig.ApkSigner$Builder
 *  com.android.apksig.ApkSigner$SignerConfig
 *  com.android.apksig.ApkSigner$SignerConfig$Builder
 *  com.example.data.CanvasComponentEntity
 *  com.example.data.StudioProjectEntity
 *  com.example.ui.KotlinProjectCodeEngine
 */
package com.example.blueprint;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.android.apksig.ApkSigner;
import com.example.data.CanvasComponentEntity;
import com.example.data.StudioProjectEntity;
import com.example.ui.KotlinProjectCodeEngine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class ApkCompilationEngine {
    private static final String STUDIO_BASE_PACKAGE = "com.aistudio.floatconfig.vqxkpl";
    private static final String STUDIO_BASE_LABEL = "Studio Error";

    private ApkCompilationEngine() {
    }

    @NonNull
    public static String getCompiledAppPackageName(@NonNull StudioProjectEntity project) {
        String customPkg;
        String string = customPkg = project.getPackageName() != null ? project.getPackageName().trim().toLowerCase(Locale.US) : "";
        if (!customPkg.isEmpty()) {
            String[] rawParts = customPkg.split("\\.+");
            StringBuilder cleanPkg = new StringBuilder();
            int validSegments = 0;
            for (String part : rawParts) {
                String cleaned = part.replaceAll("[^a-z0-9_]", "");
                if (cleaned.isEmpty()) continue;
                if (!Character.isLetter(cleaned.charAt(0))) {
                    cleaned = "app_" + cleaned;
                }
                if (validSegments > 0) {
                    cleanPkg.append('.');
                }
                cleanPkg.append(cleaned);
                ++validSegments;
            }
            if (validSegments == 1) {
                cleanPkg.insert(0, "com.app.");
                validSegments = 3;
            }
            String candidate = cleanPkg.toString();
            if (validSegments >= 2 && !STUDIO_BASE_PACKAGE.equals(candidate)) {
                return candidate;
            }
        }
        String rawName = project.getName() != null ? project.getName().trim().toLowerCase(Locale.US) : "";
        String lettersDigits = rawName.replaceAll("[^a-z0-9]", "");
        int hash = Math.abs((rawName + "#" + project.getId()).hashCode());
        String hexSuffix = String.format(Locale.US, "%04x", hash & 0xFFFF);
        String prefix2 = lettersDigits.length() >= 2 && Character.isLetter(lettersDigits.charAt(0)) ? lettersDigits.substring(0, 2) : "ap";
        String sixCharCode = (prefix2 + hexSuffix).substring(0, 6);
        if ("vqxkpl".equals(sixCharCode)) {
            sixCharCode = "app001";
        }
        return "com.aistudio.floatconfig." + sixCharCode;
    }

    @NonNull
    public static Map<String, String> generateProjectBlueprintFiles(@NonNull StudioProjectEntity project, @NonNull List<CanvasComponentEntity> components) {
        LinkedHashMap<String, String> files = new LinkedHashMap<String, String>();
        files.put("AndroidManifest.xml", ApkCompilationEngine.generateManifestXml(project));
        files.put("build.gradle", ApkCompilationEngine.generateAppBuildGradle(project));
        files.put("settings.gradle", ApkCompilationEngine.generateSettingsGradle(project));
        files.putAll(KotlinProjectCodeEngine.generateKotlinFilesForProject((StudioProjectEntity)project, components));
        files.put("src/main/java/com/floating/modmenu/MainActivity.java", ApkCompilationEngine.generateMainActivityJava(project, components));
        files.put("src/main/java/com/floating/modmenu/ProjectLauncherActivity.java", ApkCompilationEngine.generateProjectLauncherJava(project));
        files.put("src/main/java/com/floating/modmenu/EmptyCanvasWorkspaceView.java", ApkCompilationEngine.generateEmptyCanvasWorkspaceJava(project, components));
        files.put("src/main/java/com/floating/modmenu/BottomPropertyInspectorDock.java", ApkCompilationEngine.generateBottomInspectorJava(components));
        files.put("src/main/java/com/floating/modmenu/FloatingModMenuService.java", ApkCompilationEngine.generateFloatingServiceJava(project, components));
        files.put("src/main/java/com/floating/modmenu/BinaryOffsetPatcher.java", ApkCompilationEngine.generateBinaryOffsetPatcherJava(project, components));
        files.put("src/main/res/layout/activity_main.xml", ApkCompilationEngine.generateActivityMainXml(project));
        files.put("src/main/res/layout/inspector_dock.xml", ApkCompilationEngine.generateInspectorDockXml());
        files.put("src/main/res/layout/floating_view.xml", ApkCompilationEngine.generateFloatingViewXml(project, components));
        files.put("assets/overlay_config.json", ApkCompilationEngine.generateOverlayConfigJson(project, components));
        return files;
    }

    @NonNull
    public static CompilationResult compileAndSignProjectApk(@NonNull Context context, @NonNull StudioProjectEntity project, @NonNull List<CanvasComponentEntity> components, @NonNull File outputApkFile) throws Exception {
        return ApkCompilationEngine.compileAndSignProjectApk(context, project, components, outputApkFile, null);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @NonNull
    public static CompilationResult compileAndSignProjectApk(@NonNull Context context, @NonNull StudioProjectEntity project, @NonNull List<CanvasComponentEntity> components, @NonNull File outputApkFile, @Nullable Map<String, String> customEditedFiles) throws Exception {
        String customFloatingLogoPath;
        File baseApkFile;
        File workDir;
        String serviceJavaPreview;
        Map<String, String> blueprintFiles = ApkCompilationEngine.generateProjectBlueprintFiles(project, components);
        if (customEditedFiles != null && !customEditedFiles.isEmpty()) {
            blueprintFiles.putAll(customEditedFiles);
        }
        if ((serviceJavaPreview = blueprintFiles.get("src/main/java/com/example/ui/CanvasWorkspaceComponents.kt")) == null || serviceJavaPreview.isEmpty()) {
            serviceJavaPreview = blueprintFiles.get("src/main/java/com/floating/modmenu/FloatingModMenuService.java");
        }
        if (serviceJavaPreview == null) {
            serviceJavaPreview = "";
        }
        if (!(workDir = new File(context.getCacheDir(), "apk_compiler_staging")).exists()) {
            workDir.mkdirs();
        }
        File blueprintZipFile = new File(outputApkFile.getParentFile(), outputApkFile.getName().replace(".apk", "_project_blueprint.zip"));
        ApkCompilationEngine.writeBlueprintSourceZip(blueprintZipFile, blueprintFiles, components);
        String sourceApkPath = context.getApplicationInfo().sourceDir;
        File file = baseApkFile = sourceApkPath != null ? new File(sourceApkPath) : null;
        if (baseApkFile == null || !baseApkFile.exists() || baseApkFile.length() <= 100000L || !ApkCompilationEngine.containsClassesDex(baseApkFile)) {
            File[] candidates;
            for (File candidate : candidates = new File[]{new File(".build-outputs/app-debug.apk"), new File("../.build-outputs/app-debug.apk"), new File("build/outputs/apk/debug/app-debug.apk"), new File("app/build/outputs/apk/debug/app-debug.apk"), new File("APK_DOWNLOAD/app-debug.apk"), new File("../APK_DOWNLOAD/app-debug.apk")}) {
                if (!candidate.exists() || !candidate.isFile() || candidate.length() <= 100000L || !ApkCompilationEngine.containsClassesDex(candidate)) continue;
                baseApkFile = candidate;
                break;
            }
        }
        String compiledPackageName = ApkCompilationEngine.getCompiledAppPackageName(project);
        String compiledAppName = project.getName() != null && !project.getName().trim().isEmpty() ? project.getName().trim() : " ";
        String customAppLogoPath = project.getAppLogoPath() != null ? project.getAppLogoPath().trim() : "";
        String string = customFloatingLogoPath = project.getFloatingLogoPath() != null ? project.getFloatingLogoPath().trim() : "";
        String customCanvasBgImagePath = project.getCanvasBgImagePath() != null ? project.getCanvasBgImagePath().trim() : "";
        if (baseApkFile != null && baseApkFile.exists() && baseApkFile.length() > 100000L) {
            File unsignedMergedApk = new File(workDir, "unsigned_merged.apk");
            File signedTempApk = new File(workDir, "signed_output.apk");
            try {
                ApkCompilationEngine.buildAlignedUnsignedApkFromBase(baseApkFile, unsignedMergedApk, blueprintFiles, components, compiledPackageName, compiledAppName, customAppLogoPath, customFloatingLogoPath, customCanvasBgImagePath, project.getCanvasWidthDp(), project.getCanvasHeightDp());
                boolean signedOk = ApkCompilationEngine.signApkWithDebugKey(context, unsignedMergedApk, signedTempApk);
                if (signedOk && signedTempApk.exists() && signedTempApk.length() > 100000L) {
                    ApkCompilationEngine.copyFile(signedTempApk, outputApkFile);
                } else if (unsignedMergedApk.exists() && unsignedMergedApk.length() > 100000L) {
                    ApkCompilationEngine.copyFile(unsignedMergedApk, outputApkFile);
                } else {
                    ApkCompilationEngine.copyFile(baseApkFile, outputApkFile);
                }
            }
            catch (Exception e) {
                ApkCompilationEngine.copyFile(baseApkFile, outputApkFile);
            }
            finally {
                if (unsignedMergedApk.exists()) {
                    unsignedMergedApk.delete();
                }
                if (signedTempApk.exists()) {
                    signedTempApk.delete();
                }
            }
        } else {
            ApkCompilationEngine.copyFile(blueprintZipFile, outputApkFile);
        }
        return new CompilationResult(outputApkFile, blueprintZipFile, serviceJavaPreview, blueprintFiles, outputApkFile.length(), compiledPackageName, compiledAppName.trim());
    }

    private static void buildAlignedUnsignedApkFromBase(@NonNull File baseApkFile, @NonNull File outUnsignedApk, @NonNull Map<String, String> blueprintFiles, @NonNull List<CanvasComponentEntity> components, @NonNull String compiledPackageName, @NonNull String compiledAppName, @NonNull String customAppLogoPath, @NonNull String customFloatingLogoPath, @NonNull String customCanvasBgImagePath, int canvasWidthDp, int canvasHeightDp) throws IOException {
        byte[] replacementIconPng = ApkCompilationEngine.buildLauncherIconPngBytes(customAppLogoPath);
        HashSet<String> writtenEntries = new HashSet<String>();
        try (ZipFile baseZip = new ZipFile(baseApkFile);
             FileOutputStream fos = new FileOutputStream(outUnsignedApk);
             CountingOutputStream cos = new CountingOutputStream(fos);
             ZipOutputStream zos = new ZipOutputStream(cos);){
            ZipEntry arscEntry = baseZip.getEntry("resources.arsc");
            if (arscEntry != null) {
                byte[] arscBytes;
                try (InputStream is = baseZip.getInputStream(arscEntry);){
                    arscBytes = ApkCompilationEngine.readAllBytes(is);
                }
                ApkCompilationEngine.writeAlignedStoredEntry(zos, cos, "resources.arsc", arscBytes, 4);
                writtenEntries.add("resources.arsc");
            }
            Enumeration<? extends ZipEntry> storedPassEntries = baseZip.entries();
            while (storedPassEntries.hasMoreElements()) {
                byte[] soBytes;
                ZipEntry entry = storedPassEntries.nextElement();
                String name = entry.getName();
                if (writtenEntries.contains(name) || ApkCompilationEngine.isSignatureFile(name) || !name.startsWith("lib/") || !name.endsWith(".so")) continue;
                try (InputStream is = baseZip.getInputStream(entry);){
                    soBytes = ApkCompilationEngine.readAllBytes(is);
                }
                ApkCompilationEngine.writeAlignedStoredEntry(zos, cos, name, soBytes, 4096);
                writtenEntries.add(name);
            }
            Enumeration<? extends ZipEntry> entries = baseZip.entries();
            while (entries.hasMoreElements()) {
                byte[] entryBytes;
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (writtenEntries.contains(name) || ApkCompilationEngine.isSignatureFile(name) || "assets/overlay_config.json".equals(name) || "assets/app_logo.png".equals(name) || "assets/floating_logo.png".equals(name) || "assets/canvas_bg.png".equals(name) || name.startsWith("assets/generated_project/") || name.startsWith("assets/images/widget_bg_") || name.startsWith("assets/images/img_") || name.startsWith("assets/sounds/on_snd_") || name.startsWith("assets/sounds/off_snd_") || name.startsWith("src/")) continue;
                try (InputStream is = baseZip.getInputStream(entry);){
                    entryBytes = ApkCompilationEngine.readAllBytes(is);
                }
                if ("AndroidManifest.xml".equals(name)) {
                    entryBytes = ApkCompilationEngine.patchBinaryAndroidManifest(entryBytes, compiledPackageName, compiledAppName);
                } else if (replacementIconPng != null && name.startsWith("res/") && name.endsWith(".png") && name.contains("ic_launcher")) {
                    entryBytes = replacementIconPng;
                }
                ZipEntry newEntry = new ZipEntry(name);
                newEntry.setMethod(8);
                zos.putNextEntry(newEntry);
                zos.write(entryBytes);
                zos.closeEntry();
                writtenEntries.add(name);
            }
            String overlayConfigJson = blueprintFiles.get("assets/overlay_config.json");
            if (overlayConfigJson != null && writtenEntries.add("assets/overlay_config.json")) {
                ApkCompilationEngine.writeDeflatedUtf8Entry(zos, "assets/overlay_config.json", overlayConfigJson);
            }
            if (!customAppLogoPath.isEmpty() && writtenEntries.add("assets/app_logo.png")) {
                ApkCompilationEngine.injectCustomFileIfPresent(zos, customAppLogoPath, "assets/app_logo.png");
            }
            if (!customFloatingLogoPath.isEmpty() && writtenEntries.add("assets/floating_logo.png")) {
                ApkCompilationEngine.injectCustomFileIfPresent(zos, customFloatingLogoPath, "assets/floating_logo.png");
            }
            if (!customCanvasBgImagePath.isEmpty() && writtenEntries.add("assets/canvas_bg.png")) {
                ApkCompilationEngine.injectCroppedCanvasBgIfPresent(zos, customCanvasBgImagePath, "assets/canvas_bg.png", canvasWidthDp, canvasHeightDp);
            }
            for (Map.Entry<String, String> fileEntry : blueprintFiles.entrySet()) {
                String relPath = fileEntry.getKey();
                String content = fileEntry.getValue();
                String genPath = "assets/generated_project/" + relPath;
                if (writtenEntries.add(genPath)) {
                    ApkCompilationEngine.writeDeflatedUtf8Entry(zos, genPath, content);
                }
                if (!relPath.startsWith("src/") || !writtenEntries.add(relPath)) continue;
                ApkCompilationEngine.writeDeflatedUtf8Entry(zos, relPath, content);
            }
            for (CanvasComponentEntity comp : components) {
                String offSndEntry;
                String onSndEntry;
                String bgImgEntry = "assets/images/widget_bg_" + comp.getId() + ".png";
                if (writtenEntries.add(bgImgEntry)) {
                    ApkCompilationEngine.injectCustomFileIfPresent(zos, comp.getBgImagePath(), bgImgEntry);
                }
                String imgEntry = "assets/images/img_" + comp.getId() + ".jpg";
                if (writtenEntries.add(imgEntry)) {
                    ApkCompilationEngine.injectCustomFileIfPresent(zos, comp.getCustomImagePath(), imgEntry);
                }
                if (writtenEntries.add(onSndEntry = "assets/sounds/on_snd_" + comp.getId() + ".mp3")) {
                    ApkCompilationEngine.injectCustomFileIfPresent(zos, comp.getCustomSoundPath(), onSndEntry);
                }
                if (!writtenEntries.add(offSndEntry = "assets/sounds/off_snd_" + comp.getId() + ".mp3")) continue;
                ApkCompilationEngine.injectCustomFileIfPresent(zos, comp.getOffCustomSoundPath(), offSndEntry);
            }
        }
    }

    @NonNull
    public static byte[] patchBinaryAndroidManifest(@NonNull byte[] axml, @NonNull String newPackageName, @NonNull String newAppLabel) {
        try {
            int i;
            if (axml.length < 36) {
                return ApkCompilationEngine.fallbackInPlacePackagePatch(axml, newPackageName);
            }
            int xmlType = ApkCompilationEngine.readU16LE(axml, 0);
            int xmlHeaderSize = ApkCompilationEngine.readU16LE(axml, 2);
            if (xmlType != 3 || xmlHeaderSize < 8) {
                return ApkCompilationEngine.fallbackInPlacePackagePatch(axml, newPackageName);
            }
            int spStart = xmlHeaderSize;
            int spType = ApkCompilationEngine.readU16LE(axml, spStart);
            int spHeaderSize = ApkCompilationEngine.readU16LE(axml, spStart + 2);
            int spChunkSize = ApkCompilationEngine.readIntLE(axml, spStart + 4);
            int stringCount = ApkCompilationEngine.readIntLE(axml, spStart + 8);
            int styleCount = ApkCompilationEngine.readIntLE(axml, spStart + 12);
            int flags = ApkCompilationEngine.readIntLE(axml, spStart + 16);
            int stringsStart = ApkCompilationEngine.readIntLE(axml, spStart + 20);
            if (spType != 1 || spHeaderSize != 28 || styleCount != 0 || stringCount <= 0 || spStart + spChunkSize > axml.length) {
                return ApkCompilationEngine.fallbackInPlacePackagePatch(axml, newPackageName);
            }
            boolean isUtf8 = (flags & 0x100) != 0;
            String[] strings = new String[stringCount];
            for (i = 0; i < stringCount; ++i) {
                int b1;
                int relOffset = ApkCompilationEngine.readIntLE(axml, spStart + spHeaderSize + i * 4);
                int absPos = spStart + stringsStart + relOffset;
                if (!isUtf8) {
                    int u16len = ApkCompilationEngine.readU16LE(axml, absPos);
                    int hdrBytes = 2;
                    if ((u16len & 0x8000) != 0) {
                        u16len = (u16len & Short.MAX_VALUE) << 16 | ApkCompilationEngine.readU16LE(axml, absPos + 2);
                        hdrBytes = 4;
                    }
                    strings[i] = new String(axml, absPos + hdrBytes, u16len * 2, StandardCharsets.UTF_16LE);
                    continue;
                }
                int p = absPos;
                if (((b1 = axml[p++] & 0xFF) & 0x80) != 0) {
                    // empty if block
                }
                int n = ++p;
                ++p;
                int u8len = axml[n] & 0xFF;
                if ((u8len & 0x80) != 0) {
                    u8len = (u8len & 0x7F) << 8 | axml[p++] & 0xFF;
                }
                strings[i] = new String(axml, p, u8len, StandardCharsets.UTF_8);
            }
            for (i = 0; i < stringCount; ++i) {
                String s = strings[i];
                if (s == null) continue;
                if (s.contains(STUDIO_BASE_PACKAGE)) {
                    strings[i] = s.replace(STUDIO_BASE_PACKAGE, newPackageName);
                    continue;
                }
                if (!s.equals(STUDIO_BASE_LABEL) && !s.equalsIgnoreCase("STUDIO ERROR")) continue;
                strings[i] = newAppLabel;
            }
            ByteArrayOutputStream stringDataBaos = new ByteArrayOutputStream(spChunkSize);
            int[] newOffsets = new int[stringCount];
            for (int i2 = 0; i2 < stringCount; ++i2) {
                String s;
                newOffsets[i2] = stringDataBaos.size();
                String string = s = strings[i2] != null ? strings[i2] : "";
                if (!isUtf8) {
                    byte[] utf16Bytes = s.getBytes(StandardCharsets.UTF_16LE);
                    int charLen = s.length();
                    ApkCompilationEngine.writeU16LE(stringDataBaos, charLen);
                    stringDataBaos.write(utf16Bytes);
                    ApkCompilationEngine.writeU16LE(stringDataBaos, 0);
                    continue;
                }
                byte[] utf8Bytes = s.getBytes(StandardCharsets.UTF_8);
                int u16len = s.length();
                int u8len = utf8Bytes.length;
                if (u16len >= 128) {
                    stringDataBaos.write(0x80 | u16len >> 8 & 0x7F);
                    stringDataBaos.write(u16len & 0xFF);
                } else {
                    stringDataBaos.write(u16len & 0x7F);
                }
                if (u8len >= 128) {
                    stringDataBaos.write(0x80 | u8len >> 8 & 0x7F);
                    stringDataBaos.write(u8len & 0xFF);
                } else {
                    stringDataBaos.write(u8len & 0x7F);
                }
                stringDataBaos.write(utf8Bytes);
                stringDataBaos.write(0);
            }
            while (stringDataBaos.size() % 4 != 0) {
                stringDataBaos.write(0);
            }
            byte[] newStringData = stringDataBaos.toByteArray();
            int newStringsStart = spHeaderSize + stringCount * 4;
            int newSpChunkSize = newStringsStart + newStringData.length;
            int newFlags = flags & 0xFFFFFFFE;
            int tailOffset = spStart + spChunkSize;
            int tailLen = axml.length - tailOffset;
            int newTotalSize = xmlHeaderSize + newSpChunkSize + tailLen;
            ByteArrayOutputStream out = new ByteArrayOutputStream(newTotalSize);
            ApkCompilationEngine.writeU16LE(out, 3);
            ApkCompilationEngine.writeU16LE(out, xmlHeaderSize);
            ApkCompilationEngine.writeIntLE(out, newTotalSize);
            if (xmlHeaderSize > 8) {
                out.write(axml, 8, xmlHeaderSize - 8);
            }
            ApkCompilationEngine.writeU16LE(out, 1);
            ApkCompilationEngine.writeU16LE(out, spHeaderSize);
            ApkCompilationEngine.writeIntLE(out, newSpChunkSize);
            ApkCompilationEngine.writeIntLE(out, stringCount);
            ApkCompilationEngine.writeIntLE(out, 0);
            ApkCompilationEngine.writeIntLE(out, newFlags);
            ApkCompilationEngine.writeIntLE(out, newStringsStart);
            ApkCompilationEngine.writeIntLE(out, 0);
            for (int i3 = 0; i3 < stringCount; ++i3) {
                ApkCompilationEngine.writeIntLE(out, newOffsets[i3]);
            }
            out.write(newStringData);
            out.write(axml, tailOffset, tailLen);
            return out.toByteArray();
        }
        catch (Exception e) {
            return ApkCompilationEngine.fallbackInPlacePackagePatch(axml, newPackageName);
        }
    }

    @NonNull
    private static byte[] fallbackInPlacePackagePatch(@NonNull byte[] axml, @NonNull String newPackageName) {
        if (newPackageName.length() != STUDIO_BASE_PACKAGE.length()) {
            return axml;
        }
        byte[] result = (byte[])axml.clone();
        ApkCompilationEngine.replaceBytesInPlace(result, STUDIO_BASE_PACKAGE.getBytes(StandardCharsets.UTF_16LE), newPackageName.getBytes(StandardCharsets.UTF_16LE));
        ApkCompilationEngine.replaceBytesInPlace(result, STUDIO_BASE_PACKAGE.getBytes(StandardCharsets.UTF_8), newPackageName.getBytes(StandardCharsets.UTF_8));
        return result;
    }

    private static void replaceBytesInPlace(byte[] buffer, byte[] target, byte[] replacement) {
        if (target.length == 0 || target.length != replacement.length) {
            return;
        }
        for (int i = 0; i <= buffer.length - target.length; ++i) {
            boolean match = true;
            for (int j = 0; j < target.length; ++j) {
                if (buffer[i + j] == target[j]) continue;
                match = false;
                break;
            }
            if (!match) continue;
            System.arraycopy(replacement, 0, buffer, i, replacement.length);
            i += target.length - 1;
        }
    }

    private static int readU16LE(byte[] b, int offset) {
        return b[offset] & 0xFF | (b[offset + 1] & 0xFF) << 8;
    }

    private static int readIntLE(byte[] b, int offset) {
        return b[offset] & 0xFF | (b[offset + 1] & 0xFF) << 8 | (b[offset + 2] & 0xFF) << 16 | (b[offset + 3] & 0xFF) << 24;
    }

    private static void writeU16LE(OutputStream out, int val) throws IOException {
        out.write(val & 0xFF);
        out.write(val >> 8 & 0xFF);
    }

    private static void writeIntLE(OutputStream out, int val) throws IOException {
        out.write(val & 0xFF);
        out.write(val >> 8 & 0xFF);
        out.write(val >> 16 & 0xFF);
        out.write(val >> 24 & 0xFF);
    }

    private static void writeAlignedStoredEntry(@NonNull ZipOutputStream zos, @NonNull CountingOutputStream cos, @NonNull String name, @NonNull byte[] data, int alignment) throws IOException {
        CRC32 crc = new CRC32();
        crc.update(data);
        ZipEntry storedEntry = new ZipEntry(name);
        storedEntry.setMethod(0);
        storedEntry.setSize(data.length);
        storedEntry.setCompressedSize(data.length);
        storedEntry.setCrc(crc.getValue());
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        long dataStartWithoutExtra = cos.getBytesWritten() + 30L + (long)nameBytes.length;
        int remainder = (int)(dataStartWithoutExtra % (long)alignment);
        if (remainder != 0) {
            int padBytes = alignment - remainder;
            storedEntry.setExtra(new byte[padBytes]);
        }
        zos.putNextEntry(storedEntry);
        zos.write(data);
        zos.closeEntry();
    }

    private static boolean signApkWithDebugKey(@NonNull Context context, @NonNull File inputUnsignedApk, @NonNull File outputSignedApk) {
        try {
            byte[] pk8Bytes = ApkCompilationEngine.loadSigningAssetBytes(context, "signing/debug_key.pk8");
            byte[] certBytes = ApkCompilationEngine.loadSigningAssetBytes(context, "signing/debug_cert.x509.der");
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(pk8Bytes));
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate)cf.generateCertificate(new ByteArrayInputStream(certBytes));
            ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder("ANDROID", privateKey, Collections.singletonList(cert)).build();
            ApkSigner signer = new ApkSigner.Builder(Collections.singletonList(signerConfig)).setInputApk(inputUnsignedApk).setOutputApk(outputSignedApk).setMinSdkVersion(24).setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(true).build();
            signer.sign();
            return outputSignedApk.exists() && outputSignedApk.length() > 100000L;
        }
        catch (Exception e) {
            return false;
        }
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    @NonNull
    private static byte[] loadSigningAssetBytes(@NonNull Context context, @NonNull String assetRelPath) throws IOException {
        try (InputStream is = context.getAssets().open(assetRelPath);){
            byte[] byArray = ApkCompilationEngine.readAllBytes(is);
            return byArray;
        }
        catch (Exception ignored) {
            File[] fallbacks;
            File[] fileArray = fallbacks = new File[]{new File("src/main/assets/" + assetRelPath), new File("app/src/main/assets/" + assetRelPath)};
            int n = fileArray.length;
            int n2 = 0;
            while (n2 < n) {
                File f = fileArray[n2];
                if (f.exists() && f.isFile()) {
                    try (FileInputStream fis = new FileInputStream(f);){
                        byte[] byArray = ApkCompilationEngine.readAllBytes(fis);
                        return byArray;
                    }
                }
                ++n2;
            }
            throw new IOException("Signing asset not found: " + assetRelPath);
        }
    }

    private static void writeBlueprintSourceZip(@NonNull File destZip, @NonNull Map<String, String> blueprintFiles, @NonNull List<CanvasComponentEntity> components) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(destZip);
             ZipOutputStream zos = new ZipOutputStream(fos);){
            for (Map.Entry<String, String> entry : blueprintFiles.entrySet()) {
                ApkCompilationEngine.writeDeflatedUtf8Entry(zos, entry.getKey(), entry.getValue());
            }
            for (CanvasComponentEntity comp : components) {
                ApkCompilationEngine.injectCustomFileIfPresent(zos, comp.getBgImagePath(), "assets/images/widget_bg_" + comp.getId() + ".png");
                ApkCompilationEngine.injectCustomFileIfPresent(zos, comp.getCustomImagePath(), "assets/images/img_" + comp.getId() + ".jpg");
                ApkCompilationEngine.injectCustomFileIfPresent(zos, comp.getCustomSoundPath(), "assets/sounds/on_snd_" + comp.getId() + ".mp3");
                ApkCompilationEngine.injectCustomFileIfPresent(zos, comp.getOffCustomSoundPath(), "assets/sounds/off_snd_" + comp.getId() + ".mp3");
            }
        }
    }

    private static void writeDeflatedUtf8Entry(@NonNull ZipOutputStream zos, @NonNull String entryName, @NonNull String content) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        entry.setMethod(8);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static void injectCroppedCanvasBgIfPresent(@NonNull ZipOutputStream zos, String filePath, @NonNull String entryName, int canvasWidthDp, int canvasHeightDp) throws IOException {
        if (filePath == null || filePath.trim().isEmpty()) {
            return;
        }
        File f = new File(filePath.trim());
        if (!f.exists() || !f.isFile()) {
            return;
        }
        try {
            Bitmap decoded = BitmapFactory.decodeFile((String)f.getAbsolutePath());
            if (decoded != null && decoded.getWidth() > 0 && decoded.getHeight() > 0) {
                int clampedW = Math.max(180, Math.min(340, canvasWidthDp));
                int clampedH = Math.max(160, Math.min(480, canvasHeightDp));
                int targetW = clampedW * 3;
                int targetH = clampedH * 3;
                float scale = Math.max((float)targetW / (float)decoded.getWidth(), (float)targetH / (float)decoded.getHeight());
                int scaledW = Math.max(targetW, Math.round((float)decoded.getWidth() * scale));
                int scaledH = Math.max(targetH, Math.round((float)decoded.getHeight() * scale));
                Bitmap scaled = Bitmap.createScaledBitmap((Bitmap)decoded, (int)scaledW, (int)scaledH, (boolean)true);
                int cropX = Math.max(0, (scaledW - targetW) / 2);
                int cropY = Math.max(0, (scaledH - targetH) / 2);
                Bitmap cropped = Bitmap.createBitmap((Bitmap)scaled, (int)cropX, (int)cropY, (int)targetW, (int)targetH);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                cropped.compress(Bitmap.CompressFormat.PNG, 100, (OutputStream)baos);
                byte[] pngBytes = baos.toByteArray();
                ZipEntry entry = new ZipEntry(entryName);
                entry.setMethod(8);
                zos.putNextEntry(entry);
                zos.write(pngBytes);
                zos.closeEntry();
                return;
            }
        }
        catch (Throwable ignored) {
        }
        ApkCompilationEngine.injectCustomFileIfPresent(zos, filePath, entryName);
    }

    private static void injectCustomFileIfPresent(@NonNull ZipOutputStream zos, String filePath, @NonNull String entryName) throws IOException {
        if (filePath == null || filePath.trim().isEmpty()) {
            return;
        }
        File f = new File(filePath.trim());
        if (f.exists() && f.isFile()) {
            ZipEntry entry = new ZipEntry(entryName);
            entry.setMethod(8);
            zos.putNextEntry(entry);
            try (FileInputStream fis = new FileInputStream(f);){
                int read;
                byte[] buf = new byte[8192];
                while ((read = fis.read(buf)) != -1) {
                    zos.write(buf, 0, read);
                }
            }
            zos.closeEntry();
        }
    }

    @Nullable
    private static byte[] buildLauncherIconPngBytes(@NonNull String customAppLogoPath) {
        try {
            Bitmap decoded;
            File f;
            Bitmap iconBitmap = null;
            if (!customAppLogoPath.trim().isEmpty() && (f = new File(customAppLogoPath.trim())).exists() && f.isFile() && (decoded = BitmapFactory.decodeFile((String)f.getAbsolutePath())) != null) {
                iconBitmap = Bitmap.createScaledBitmap((Bitmap)decoded, (int)192, (int)192, (boolean)true);
            }
            if (iconBitmap == null) {
                iconBitmap = Bitmap.createBitmap((int)192, (int)192, (Bitmap.Config)Bitmap.Config.ARGB_8888);
                iconBitmap.eraseColor(0);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            iconBitmap.compress(Bitmap.CompressFormat.PNG, 100, (OutputStream)baos);
            return baos.toByteArray();
        }
        catch (Exception e) {
            return null;
        }
    }

    private static boolean containsClassesDex(@NonNull File file) {
        try (ZipFile zf = new ZipFile(file)) {
            return zf.getEntry("classes.dex") != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isSignatureFile(@NonNull String name) {
        if (!name.startsWith("META-INF/")) {
            return false;
        }
        String upper = name.toUpperCase(Locale.US);
        return upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC") || upper.equals("META-INF/MANIFEST.MF") || upper.startsWith("META-INF/SIG-");
    }

    @NonNull
    private static byte[] readAllBytes(@NonNull InputStream is) throws IOException {
        int read;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        while ((read = is.read(buf)) != -1) {
            baos.write(buf, 0, read);
        }
        return baos.toByteArray();
    }

    private static void copyFile(@NonNull File src, @NonNull File dst) throws IOException {
        if (dst.getParentFile() != null && !dst.getParentFile().exists()) {
            dst.getParentFile().mkdirs();
        }
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst);){
            int len;
            byte[] buf = new byte[16384];
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
        }
    }

    @NonNull
    private static String generateManifestXml(@NonNull StudioProjectEntity project) {
        String pkg = ApkCompilationEngine.getCompiledAppPackageName(project);
        int vCode = Math.max(1, project.getVersionCode());
        String vName = project.getVersionName() != null && !project.getVersionName().trim().isEmpty() ? project.getVersionName().trim() : "1.0";
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n    package=\"" + ApkCompilationEngine.escapeXml(pkg) + "\"\n    android:versionCode=\"" + vCode + "\"\n    android:versionName=\"" + ApkCompilationEngine.escapeXml(vName) + "\">\n\n    <uses-permission android:name=\"android.permission.SYSTEM_ALERT_WINDOW\" />\n    <uses-permission android:name=\"android.permission.READ_EXTERNAL_STORAGE\" />\n    <uses-permission android:name=\"android.permission.WRITE_EXTERNAL_STORAGE\" />\n    <uses-permission android:name=\"android.permission.MANAGE_EXTERNAL_STORAGE\" />\n    <uses-permission android:name=\"android.permission.READ_MEDIA_IMAGES\" />\n    <uses-permission android:name=\"android.permission.READ_MEDIA_VIDEO\" />\n    <uses-permission android:name=\"android.permission.READ_MEDIA_AUDIO\" />\n    <uses-permission android:name=\"moe.shizuku.manager.permission.API_V23\" />\n    <uses-permission android:name=\"android.permission.FOREGROUND_SERVICE\" />\n    <uses-permission android:name=\"android.permission.FOREGROUND_SERVICE_SPECIAL_USE\" />\n    <uses-permission android:name=\"android.permission.VIBRATE\" />\n    <uses-permission android:name=\"android.permission.POST_NOTIFICATIONS\" />\n\n    <queries>\n        <package android:name=\"moe.shizuku.privileged.api\" />\n    </queries>\n\n    <application\n        android:allowBackup=\"true\"\n        android:requestLegacyExternalStorage=\"true\"\n        android:preserveLegacyExternalStorage=\"true\"\n        android:label=\"" + ApkCompilationEngine.escapeXml(project.getName()) + "\"\n        android:supportsRtl=\"true\"\n        android:theme=\"@android:style/Theme.DeviceDefault.Light.NoActionBar\">\n\n        <activity\n            android:name=\".MainActivity\"\n            android:exported=\"true\"\n            android:windowSoftInputMode=\"adjustResize\">\n            <intent-filter>\n                <action android:name=\"android.intent.action.MAIN\" />\n                <category android:name=\"android.intent.category.LAUNCHER\" />\n            </intent-filter>\n        </activity>\n\n        <activity\n            android:name=\".ProjectLauncherActivity\"\n            android:exported=\"false\" />\n\n        <service\n            android:name=\".FloatingModMenuService\"\n            android:enabled=\"true\"\n            android:exported=\"false\"\n            android:foregroundServiceType=\"specialUse\" />\n\n        <provider\n            android:name=\"com.example.engine.ShizukuBinderProvider\"\n            android:authorities=\"" + ApkCompilationEngine.escapeXml(pkg) + ".shizuku\"\n            android:enabled=\"true\"\n            android:exported=\"true\"\n            android:multiprocess=\"false\"\n            android:permission=\"android.permission.INTERACT_ACROSS_USERS_FULL\" />\n    </application>\n</manifest>\n";
    }

    @NonNull
    private static String generateAppBuildGradle(@NonNull StudioProjectEntity project) {
        String pkg = ApkCompilationEngine.getCompiledAppPackageName(project);
        int minSdk = Math.max(21, project.getMinSdk());
        int targetSdk = Math.max(minSdk, project.getTargetSdk());
        int vCode = Math.max(1, project.getVersionCode());
        String vName = project.getVersionName() != null && !project.getVersionName().trim().isEmpty() ? project.getVersionName().trim() : "1.0";
        return "plugins {\n    id 'com.android.application'\n}\n\nandroid {\n    namespace '" + ApkCompilationEngine.escapeJava(pkg) + "'\n    compileSdk " + targetSdk + "\n\n    defaultConfig {\n        applicationId \"" + ApkCompilationEngine.escapeJava(pkg) + "\"\n        minSdk " + minSdk + "\n        targetSdk " + targetSdk + "\n        versionCode " + vCode + "\n        versionName \"" + ApkCompilationEngine.escapeJava(vName) + "\"\n    }\n\n    signingConfigs {\n        debug {\n            storeFile file('debug.keystore')\n            storePassword 'android'\n            keyAlias 'androiddebugkey'\n            keyPassword 'android'\n            v1SigningEnabled true\n            v2SigningEnabled true\n        }\n    }\n\n    buildTypes {\n        debug {\n            signingConfig signingConfigs.debug\n        }\n        release {\n            minifyEnabled false\n            signingConfig signingConfigs.debug\n        }\n    }\n\n    compileOptions {\n        sourceCompatibility JavaVersion.VERSION_11\n        targetCompatibility JavaVersion.VERSION_11\n    }\n}\n\ndependencies {\n    implementation 'androidx.core:core:1.13.1'\n}\n";
    }

    @NonNull
    private static String generateSettingsGradle(@NonNull StudioProjectEntity project) {
        return "rootProject.name = \"" + ApkCompilationEngine.escapeJava(project.getName()) + "\"\ninclude ':app'\n";
    }

    @NonNull
    private static String generateMainActivityJava(@NonNull StudioProjectEntity project, @NonNull List<CanvasComponentEntity> components) {
        StringBuilder sb = new StringBuilder();
        sb.append("package com.floating.modmenu;\n\n");
        sb.append("import android.Manifest;\n");
        sb.append("import android.app.Activity;\n");
        sb.append("import android.content.Intent;\n");
        sb.append("import android.content.pm.PackageManager;\n");
        sb.append("import android.net.Uri;\n");
        sb.append("import android.os.Build;\n");
        sb.append("import android.os.Bundle;\n");
        sb.append("import android.os.Environment;\n");
        sb.append("import android.provider.Settings;\n");
        sb.append("import android.widget.Button;\n");
        sb.append("import android.widget.FrameLayout;\n");
        sb.append("import android.widget.Toast;\n\n");
        sb.append("public class MainActivity extends Activity {\n\n");
        sb.append("    private EmptyCanvasWorkspaceView canvasWorkspaceView;\n");
        sb.append("    private BottomPropertyInspectorDock inspectorDock;\n\n");
        sb.append("    @Override\n");
        sb.append("    protected void onCreate(Bundle savedInstanceState) {\n");
        sb.append("        super.onCreate(savedInstanceState);\n");
        sb.append("        setContentView(R.layout.activity_main);\n\n");
        sb.append("        ensureStorageAndOverlayPermissions();\n\n");
        sb.append("        FrameLayout canvasHost = findViewById(R.id.empty_canvas_workspace_host);\n");
        sb.append("        FrameLayout inspectorHost = findViewById(R.id.bottom_inspector_dock_host);\n");
        sb.append("        canvasWorkspaceView = new EmptyCanvasWorkspaceView(this);\n");
        sb.append("        inspectorDock = new BottomPropertyInspectorDock(this);\n");
        sb.append("        canvasHost.addView(canvasWorkspaceView);\n");
        sb.append("        inspectorHost.addView(inspectorDock);\n\n");
        sb.append("        Button floatBtn = findViewById(R.id.btn_float_overlay);\n");
        sb.append("        floatBtn.setOnClickListener(v -> launchFloatingModMenu());\n");
        sb.append("    }\n\n");
        sb.append("    private boolean hasStoragePermission() {\n");
        sb.append("        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {\n");
        sb.append("            return Environment.isExternalStorageManager();\n");
        sb.append("        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {\n");
        sb.append("            return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED\n");
        sb.append("                    && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;\n");
        sb.append("        }\n");
        sb.append("        return true;\n");
        sb.append("    }\n\n");
        sb.append("    private void ensureStorageAndOverlayPermissions() {\n");
        sb.append("        if (!hasStoragePermission()) {\n");
        sb.append("            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {\n");
        sb.append("                try {\n");
        sb.append("                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,\n");
        sb.append("                            Uri.parse(\"package:\" + getPackageName()));\n");
        sb.append("                    startActivity(intent);\n");
        sb.append("                } catch (Exception e) {\n");
        sb.append("                    startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));\n");
        sb.append("                }\n");
        sb.append("            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {\n");
        sb.append("                requestPermissions(new String[]{\n");
        sb.append("                        Manifest.permission.READ_EXTERNAL_STORAGE,\n");
        sb.append("                        Manifest.permission.WRITE_EXTERNAL_STORAGE\n");
        sb.append("                }, 1001);\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("    }\n\n");
        sb.append("    private void launchFloatingModMenu() {\n");
        sb.append("        boolean hasOverlay = Settings.canDrawOverlays(this);\n");
        sb.append("        boolean hasStorage = hasStoragePermission();\n");
        sb.append("        if (!hasOverlay || !hasStorage) {\n");
        sb.append("            Toast.makeText(this, \"Permission Required: Please enable Storage and Overlay (SYSTEM_ALERT_WINDOW) permissions first.\", Toast.LENGTH_LONG).show();\n");
        sb.append("            if (!hasStorage) {\n");
        sb.append("                ensureStorageAndOverlayPermissions();\n");
        sb.append("            } else {\n");
        sb.append("                Intent perm = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,\n");
        sb.append("                        Uri.parse(\"package:\" + getPackageName()));\n");
        sb.append("                startActivity(perm);\n");
        sb.append("            }\n");
        sb.append("            return;\n");
        sb.append("        }\n");
        sb.append("        Intent svc = new Intent(this, FloatingModMenuService.class);\n");
        sb.append("        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {\n");
        sb.append("            startForegroundService(svc);\n");
        sb.append("        } else {\n");
        sb.append("            startService(svc);\n");
        sb.append("        }\n");
        sb.append("        Toast.makeText(this, \"Launched " + ApkCompilationEngine.escapeJava(project.getOverlayTitle()) + " (" + components.size() + " items)\", Toast.LENGTH_SHORT).show();\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    @NonNull
    private static String generateProjectLauncherJava(@NonNull StudioProjectEntity project) {
        return "package com.floating.modmenu;\n\nimport android.app.Activity;\nimport android.content.Intent;\nimport android.os.Bundle;\nimport android.widget.Button;\nimport android.widget.LinearLayout;\nimport android.widget.TextView;\n\npublic class ProjectLauncherActivity extends Activity {\n    @Override\n    protected void onCreate(Bundle savedInstanceState) {\n        super.onCreate(savedInstanceState);\n        LinearLayout root = new LinearLayout(this);\n        root.setOrientation(LinearLayout.VERTICAL);\n        TextView title = new TextView(this);\n        title.setText(\"Project: " + ApkCompilationEngine.escapeJava(project.getName()) + "\");\n        Button openBtn = new Button(this);\n        openBtn.setText(\"Open Workspace\");\n        openBtn.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));\n        root.addView(title);\n        root.addView(openBtn);\n        setContentView(root);\n    }\n}\n";
    }

    @NonNull
    private static String generateEmptyCanvasWorkspaceJava(@NonNull StudioProjectEntity project, @NonNull List<CanvasComponentEntity> components) {
        StringBuilder sb = new StringBuilder();
        sb.append("package com.floating.modmenu;\n\n");
        sb.append("import android.content.Context;\n");
        sb.append("import android.graphics.Canvas;\n");
        sb.append("import android.graphics.Color;\n");
        sb.append("import android.graphics.Paint;\n");
        sb.append("import android.widget.FrameLayout;\n\n");
        sb.append("public class EmptyCanvasWorkspaceView extends FrameLayout {\n");
        sb.append("    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);\n");
        sb.append("    public static final int CANVAS_WIDTH_DP = ").append(project.getCanvasWidthDp()).append(";\n");
        sb.append("    public static final int CANVAS_HEIGHT_DP = ").append(project.getCanvasHeightDp()).append(";\n");
        sb.append("    public static final boolean AUTO_FIX_SIZE = ").append(project.getAutoFixSize()).append(";\n");
        sb.append("    public static final int CONFIGURED_WIDGET_COUNT = ").append(components.size()).append(";\n\n");
        sb.append("    public EmptyCanvasWorkspaceView(Context context) {\n");
        sb.append("        super(context);\n");
        sb.append("        setWillNotDraw(false);\n");
        sb.append("        setBackgroundColor(Color.parseColor(\"").append(ApkCompilationEngine.escapeJava(project.getCanvasBgColorHex())).append("\"));\n");
        sb.append("        gridPaint.setColor(0x2E94A3B8);\n");
        sb.append("        gridPaint.setStrokeWidth(2f);\n");
        sb.append("    }\n\n");
        sb.append("    @Override\n");
        sb.append("    protected void onDraw(Canvas canvas) {\n");
        sb.append("        super.onDraw(canvas);\n");
        sb.append("        for (int x = 0; x < getWidth(); x += 36) canvas.drawLine(x, 0, x, getHeight(), gridPaint);\n");
        sb.append("        for (int y = 0; y < getHeight(); y += 36) canvas.drawLine(0, y, getWidth(), y, gridPaint);\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    @NonNull
    private static String generateBottomInspectorJava(@NonNull List<CanvasComponentEntity> components) {
        StringBuilder sb = new StringBuilder();
        sb.append("package com.floating.modmenu;\n\n");
        sb.append("import android.content.Context;\n");
        sb.append("import android.view.LayoutInflater;\n");
        sb.append("import android.widget.LinearLayout;\n\n");
        sb.append("public class BottomPropertyInspectorDock extends LinearLayout {\n");
        sb.append("    public BottomPropertyInspectorDock(Context context) {\n");
        sb.append("        super(context);\n");
        sb.append("        setOrientation(VERTICAL);\n");
        sb.append("        LayoutInflater.from(context).inflate(R.layout.inspector_dock, this, true);\n");
        sb.append("    }\n\n");
        sb.append("    public void applyComponentPatch(int index, boolean turnOn) {\n");
        sb.append("        BinaryOffsetPatcher.executeConfiguredComponentWrite(getContext().getFilesDir(), index, turnOn);\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    @NonNull
    private static String generateBinaryOffsetPatcherJava(@NonNull StudioProjectEntity project, @NonNull List<CanvasComponentEntity> components) {
        StringBuilder sb = new StringBuilder();
        sb.append("package com.floating.modmenu;\n\n");
        sb.append("import java.io.BufferedReader;\n");
        sb.append("import java.io.BufferedWriter;\n");
        sb.append("import java.io.File;\n");
        sb.append("import java.io.FileInputStream;\n");
        sb.append("import java.io.FileOutputStream;\n");
        sb.append("import java.io.InputStreamReader;\n");
        sb.append("import java.io.OutputStreamWriter;\n");
        sb.append("import java.io.RandomAccessFile;\n");
        sb.append("import java.nio.charset.StandardCharsets;\n\n");
        sb.append("/**\n");
        sb.append(" * Dynamically generated Real-Time File String Replacer & Binary Offset Patcher for project: ").append(ApkCompilationEngine.escapeJava(project.getName())).append("\n");
        sb.append(" */\n");
        sb.append("public final class BinaryOffsetPatcher {\n\n");
        sb.append("    public static final class ComponentPatchSpec {\n");
        sb.append("        public final long id;\n");
        sb.append("        public final String label;\n");
        sb.append("        public final String targetFilePath;\n");
        sb.append("        public final String byteOffsetHex;\n");
        sb.append("        public final String onPayloadHex;\n");
        sb.append("        public final String offPayloadHex;\n\n");
        sb.append("        public ComponentPatchSpec(long id, String label, String targetFilePath, String byteOffsetHex, String onPayloadHex, String offPayloadHex) {\n");
        sb.append("            this.id = id;\n");
        sb.append("            this.label = label;\n");
        sb.append("            this.targetFilePath = targetFilePath;\n");
        sb.append("            this.byteOffsetHex = byteOffsetHex;\n");
        sb.append("            this.onPayloadHex = onPayloadHex;\n");
        sb.append("            this.offPayloadHex = offPayloadHex;\n");
        sb.append("        }\n");
        sb.append("    }\n\n");
        sb.append("    public static final ComponentPatchSpec[] SPECS = new ComponentPatchSpec[] {\n");
        for (int i = 0; i < components.size(); ++i) {
            CanvasComponentEntity c = components.get(i);
            sb.append("        new ComponentPatchSpec(").append(c.getId()).append("L, \"").append(ApkCompilationEngine.escapeJava(c.getLabel())).append("\", \"").append(ApkCompilationEngine.escapeJava(c.getTargetFilePath())).append("\", \"").append(ApkCompilationEngine.escapeJava(c.getByteOffsetHex())).append("\", \"").append(ApkCompilationEngine.escapeJava(c.getOnPayloadHex())).append("\", \"").append(ApkCompilationEngine.escapeJava(c.getOffPayloadHex())).append("\")").append(i < components.size() - 1 ? ",\n" : "\n");
        }
        sb.append("    };\n\n");
        sb.append("    public static void executeConfiguredComponentWrite(File fallbackDir, int index, boolean isOn) {\n");
        sb.append("        if (index < 0 || index >= SPECS.length) return;\n");
        sb.append("        ComponentPatchSpec spec = SPECS[index];\n");
        sb.append("        String searchText = isOn ? spec.offPayloadHex : spec.onPayloadHex;\n");
        sb.append("        String replaceText = isOn ? spec.onPayloadHex : spec.offPayloadHex;\n");
        sb.append("        replaceStringInTargetFile(fallbackDir, spec.targetFilePath, searchText, replaceText);\n");
        sb.append("    }\n\n");
        sb.append("    public static boolean replaceStringInTargetFile(File fallbackDir, String targetPath, String searchStr, String replaceStr) {\n");
        sb.append("        try {\n");
        sb.append("            String cleanPath = targetPath == null ? \"\" : targetPath.trim();\n");
        sb.append("            if (cleanPath.startsWith(\"/sdcard/\")) {\n");
        sb.append("                cleanPath = \"/storage/emulated/0/\" + cleanPath.substring(\"/sdcard/\".length());\n");
        sb.append("            }\n");
        sb.append("            File target = cleanPath.isEmpty()\n");
        sb.append("                    ? new File(fallbackDir, \"overlay_state.py\")\n");
        sb.append("                    : new File(cleanPath);\n");
        sb.append("            if (target.getParentFile() != null && !target.getParentFile().exists()) {\n");
        sb.append("                target.getParentFile().mkdirs();\n");
        sb.append("            }\n");
        sb.append("            StringBuilder sb = new StringBuilder();\n");
        sb.append("            if (target.exists()) {\n");
        sb.append("                try (BufferedReader reader = new BufferedReader(\n");
        sb.append("                        new InputStreamReader(new FileInputStream(target), StandardCharsets.UTF_8))) {\n");
        sb.append("                    char[] buf = new char[4096];\n");
        sb.append("                    int read;\n");
        sb.append("                    while ((read = reader.read(buf)) != -1) {\n");
        sb.append("                        sb.append(buf, 0, read);\n");
        sb.append("                    }\n");
        sb.append("                }\n");
        sb.append("            }\n");
        sb.append("            String content = sb.toString();\n");
        sb.append("            String updated;\n");
        sb.append("            if (searchStr != null && !searchStr.isEmpty() && content.contains(searchStr)) {\n");
        sb.append("                updated = content.replace(searchStr, replaceStr);\n");
        sb.append("            } else if (!content.isEmpty()) {\n");
        sb.append("                updated = content.replaceAll(\"(?i)\" + java.util.regex.Pattern.quote(searchStr != null ? searchStr : \"\"),\n");
        sb.append("                        java.util.regex.Matcher.quoteReplacement(replaceStr != null ? replaceStr : \"\"));\n");
        sb.append("            } else {\n");
        sb.append("                updated = replaceStr != null ? replaceStr : \"\";\n");
        sb.append("            }\n");
        sb.append("            try (FileOutputStream fos = new FileOutputStream(target, false);\n");
        sb.append("                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {\n");
        sb.append("                writer.write(updated);\n");
        sb.append("                writer.flush();\n");
        sb.append("                fos.getFD().sync();\n");
        sb.append("            }\n");
        sb.append("            return true;\n");
        sb.append("        } catch (Exception e) {\n");
        sb.append("            return false;\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    @NonNull
    private static String generateFloatingServiceJava(@NonNull StudioProjectEntity project, @NonNull List<CanvasComponentEntity> components) {
        StringBuilder sb = new StringBuilder();
        sb.append("package com.floating.modmenu;\n\n");
        sb.append("import android.app.Service;\n");
        sb.append("import android.content.Context;\n");
        sb.append("import android.content.Intent;\n");
        sb.append("import android.graphics.Color;\n");
        sb.append("import android.graphics.PixelFormat;\n");
        sb.append("import android.os.Build;\n");
        sb.append("import android.os.IBinder;\n");
        sb.append("import android.view.Gravity;\n");
        sb.append("import android.view.MotionEvent;\n");
        sb.append("import android.view.View;\n");
        sb.append("import android.view.WindowManager;\n");
        sb.append("import android.widget.Button;\n");
        sb.append("import android.widget.EditText;\n");
        sb.append("import android.widget.FrameLayout;\n");
        sb.append("import android.widget.LinearLayout;\n");
        sb.append("import android.widget.SeekBar;\n");
        sb.append("import android.widget.Switch;\n");
        sb.append("import android.widget.TextView;\n\n");
        sb.append("// Raw Java Floating Mod Menu Service generated by STUDIO ERROR\n");
        sb.append("public class FloatingModMenuService extends Service {\n");
        sb.append("    public static final String TITLE = \"").append(ApkCompilationEngine.escapeJava(project.getOverlayTitle())).append("\";\n");
        sb.append("    public static final int WIDTH_DP = ").append(project.getCanvasWidthDp()).append(";\n");
        sb.append("    public static final int HEIGHT_DP = ").append(project.getCanvasHeightDp()).append(";\n");
        sb.append("    public static final boolean AUTO_FIX_SIZE = ").append(project.getAutoFixSize()).append(";\n\n");
        sb.append("    private WindowManager windowManager;\n");
        sb.append("    private View floatingRoot;\n\n");
        sb.append("    @Override\n");
        sb.append("    public void onCreate() {\n");
        sb.append("        super.onCreate();\n");
        sb.append("        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);\n");
        sb.append("        showFloatingOverlay();\n");
        sb.append("    }\n\n");
        sb.append("    private void showFloatingOverlay() {\n");
        sb.append("        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O\n");
        sb.append("                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY\n");
        sb.append("                : WindowManager.LayoutParams.TYPE_PHONE;\n");
        sb.append("        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(\n");
        sb.append("                WindowManager.LayoutParams.WRAP_CONTENT,\n");
        sb.append("                WindowManager.LayoutParams.WRAP_CONTENT,\n");
        sb.append("                type,\n");
        sb.append("                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,\n");
        sb.append("                PixelFormat.TRANSLUCENT\n");
        sb.append("        );\n");
        sb.append("        lp.gravity = Gravity.TOP | Gravity.START;\n");
        sb.append("        lp.x = 32;\n");
        sb.append("        lp.y = 160;\n\n");
        sb.append("        LinearLayout root = new LinearLayout(this);\n");
        sb.append("        root.setOrientation(LinearLayout.VERTICAL);\n");
        sb.append("        root.setBackgroundColor(Color.parseColor(\"").append(ApkCompilationEngine.escapeJava(project.getCanvasBgColorHex())).append("\"));\n\n");
        for (int i = 0; i < components.size(); ++i) {
            CanvasComponentEntity c = components.get(i);
            sb.append("        // Widget #").append(i + 1).append(": ").append(c.getLabel()).append(" [").append(c.getType()).append("]\n");
            sb.append("        // Target: ").append(c.getTargetFilePath()).append(" | Offset: ").append(c.getByteOffsetHex()).append(" | ON=").append(c.getOnPayloadHex()).append(" | OFF=").append(c.getOffPayloadHex()).append("\n");
            sb.append("        // ON Sound: ").append(c.getSoundTrigger()).append(" | OFF Sound: ").append(c.getOffSoundTrigger()).append("\n");
            if ("TOGGLE".equals(c.getType())) {
                sb.append("        Switch widget_").append(i).append(" = new Switch(this);\n");
                sb.append("        widget_").append(i).append(".setText(\"").append(ApkCompilationEngine.escapeJava(c.getLabel())).append("\");\n");
                sb.append("        widget_").append(i).append(".setOnCheckedChangeListener((btn, isChecked) -> {\n");
                sb.append("            BinaryOffsetPatcher.executeConfiguredComponentWrite(getFilesDir(), ").append(i).append(", isChecked);\n");
                sb.append("        });\n");
                sb.append("        root.addView(widget_").append(i).append(");\n\n");
                continue;
            }
            sb.append("        Button widget_").append(i).append(" = new Button(this);\n");
            sb.append("        widget_").append(i).append(".setText(\"").append(ApkCompilationEngine.escapeJava(c.getLabel())).append("\");\n");
            sb.append("        final boolean[] state_").append(i).append(" = new boolean[]{false};\n");
            sb.append("        widget_").append(i).append(".setOnClickListener(v -> {\n");
            sb.append("            state_").append(i).append("[0] = !state_").append(i).append("[0];\n");
            sb.append("            BinaryOffsetPatcher.executeConfiguredComponentWrite(getFilesDir(), ").append(i).append(", state_").append(i).append("[0]);\n");
            sb.append("        });\n");
            sb.append("        root.addView(widget_").append(i).append(");\n\n");
        }
        sb.append("        floatingRoot = root;\n");
        sb.append("        if (windowManager != null) {\n");
        sb.append("            windowManager.addView(floatingRoot, lp);\n");
        sb.append("        }\n");
        sb.append("    }\n\n");
        sb.append("    @Override\n");
        sb.append("    public void onDestroy() {\n");
        sb.append("        if (floatingRoot != null && windowManager != null) {\n");
        sb.append("            windowManager.removeView(floatingRoot);\n");
        sb.append("            floatingRoot = null;\n");
        sb.append("        }\n");
        sb.append("        super.onDestroy();\n");
        sb.append("    }\n\n");
        sb.append("    @Override\n");
        sb.append("    public IBinder onBind(Intent intent) {\n");
        sb.append("        return null;\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    @NonNull
    private static String generateActivityMainXml(@NonNull StudioProjectEntity project) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\"\n    android:orientation=\"vertical\"\n    android:background=\"#E2E8F0\">\n    <LinearLayout\n        android:layout_width=\"match_parent\"\n        android:layout_height=\"56dp\"\n        android:background=\"#0288D1\"\n        android:gravity=\"center_vertical\"\n        android:orientation=\"horizontal\"\n        android:padding=\"8dp\">\n        <TextView\n            android:layout_width=\"0dp\"\n            android:layout_height=\"wrap_content\"\n            android:layout_weight=\"1\"\n            android:text=\"" + ApkCompilationEngine.escapeXml(project.getName()) + "\"\n            android:textColor=\"#FFFFFF\"\n            android:textSize=\"16sp\"\n            android:textStyle=\"bold\" />\n        <Button\n            android:id=\"@+id/btn_float_overlay\"\n            android:layout_width=\"wrap_content\"\n            android:layout_height=\"38dp\"\n            android:text=\"Float\" />\n    </LinearLayout>\n    <FrameLayout\n        android:id=\"@+id/empty_canvas_workspace_host\"\n        android:layout_width=\"match_parent\"\n        android:layout_height=\"0dp\"\n        android:layout_weight=\"1\" />\n    <FrameLayout\n        android:id=\"@+id/bottom_inspector_dock_host\"\n        android:layout_width=\"match_parent\"\n        android:layout_height=\"wrap_content\" />\n</LinearLayout>\n";
    }

    @NonNull
    private static String generateInspectorDockXml() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\"\n    android:orientation=\"vertical\"\n    android:background=\"#FFFFFF\"\n    android:padding=\"10dp\">\n    <EditText\n        android:id=\"@+id/et_component_label\"\n        android:layout_width=\"match_parent\"\n        android:layout_height=\"wrap_content\"\n        android:hint=\"Component Label\" />\n    <EditText\n        android:id=\"@+id/et_target_file_path\"\n        android:layout_width=\"match_parent\"\n        android:layout_height=\"wrap_content\"\n        android:hint=\"Target File Path\" />\n    <EditText\n        android:id=\"@+id/et_byte_offset_hex\"\n        android:layout_width=\"match_parent\"\n        android:layout_height=\"wrap_content\"\n        android:hint=\"Byte Offset Hex (0x04)\" />\n    <EditText\n        android:id=\"@+id/et_on_payload_hex\"\n        android:layout_width=\"match_parent\"\n        android:layout_height=\"wrap_content\"\n        android:hint=\"ON Value (0x01)\" />\n    <EditText\n        android:id=\"@+id/et_off_payload_hex\"\n        android:layout_width=\"match_parent\"\n        android:layout_height=\"wrap_content\"\n        android:hint=\"OFF Value (0x00)\" />\n</LinearLayout>\n";
    }

    @NonNull
    private static String generateFloatingViewXml(@NonNull StudioProjectEntity project, @NonNull List<CanvasComponentEntity> components) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<!-- Dynamically generated Floating View XML for ").append(ApkCompilationEngine.escapeXml(project.getOverlayTitle())).append(" (").append(project.getCanvasWidthDp()).append("dp x ").append(project.getCanvasHeightDp()).append("dp) -->\n");
        sb.append("<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
        sb.append("    android:id=\"@+id/floating_canvas_frame\"\n");
        sb.append("    android:layout_width=\"").append(project.getCanvasWidthDp()).append("dp\"\n");
        sb.append("    android:layout_height=\"").append(project.getCanvasHeightDp()).append("dp\"\n");
        sb.append("    android:background=\"").append(ApkCompilationEngine.escapeXml(project.getCanvasBgColorHex())).append("\">\n");
        for (CanvasComponentEntity comp : components) {
            String xmlTag;
            switch (comp.getType()) {
                case "TOGGLE": {
                    xmlTag = "Switch";
                    break;
                }
                case "BUTTON": {
                    xmlTag = "Button";
                    break;
                }
                case "SLIDER": {
                    xmlTag = "SeekBar";
                    break;
                }
                case "INPUT": {
                    xmlTag = "EditText";
                    break;
                }
                case "IMAGE": {
                    xmlTag = "ImageView";
                    break;
                }
                default: {
                    xmlTag = "TextView";
                }
            }
            sb.append("    <").append(xmlTag).append("\n");
            sb.append("        android:id=\"@+id/widget_").append(comp.getId()).append("\"\n");
            sb.append("        android:layout_width=\"").append(comp.getWidthDp()).append("dp\"\n");
            sb.append("        android:layout_height=\"").append(comp.getHeightDp()).append("dp\"\n");
            sb.append("        android:layout_marginStart=\"").append(comp.getPosXDp()).append("dp\"\n");
            sb.append("        android:layout_marginTop=\"").append(comp.getPosYDp()).append("dp\"\n");
            if (!"IMAGE".equals(comp.getType()) && !"SLIDER".equals(comp.getType())) {
                sb.append("        android:text=\"").append(ApkCompilationEngine.escapeXml(comp.getLabel())).append("\"\n");
                sb.append("        android:textColor=\"").append(ApkCompilationEngine.escapeXml(comp.getTextColorHex())).append("\"\n");
            }
            sb.append("        android:background=\"").append(ApkCompilationEngine.escapeXml(comp.getBgColorHex())).append("\"\n");
            sb.append("        android:tag=\"").append(ApkCompilationEngine.escapeXml(comp.getByteOffsetHex())).append("|").append(ApkCompilationEngine.escapeXml(comp.getOnPayloadHex())).append("|").append(ApkCompilationEngine.escapeXml(comp.getOffPayloadHex())).append("\" />\n");
        }
        sb.append("</FrameLayout>\n");
        return sb.toString();
    }

    @NonNull
    private static String generateOverlayConfigJson(@NonNull StudioProjectEntity project, @NonNull List<CanvasComponentEntity> components) {
        boolean hasLogo = project.getAppLogoPath() != null && !project.getAppLogoPath().trim().isEmpty() && new File(project.getAppLogoPath().trim()).exists();
        boolean hasFloatingLogo = project.getFloatingLogoPath() != null && !project.getFloatingLogoPath().trim().isEmpty() && new File(project.getFloatingLogoPath().trim()).exists();
        boolean hasCanvasBgImg = project.getCanvasBgImagePath() != null && !project.getCanvasBgImagePath().trim().isEmpty() && new File(project.getCanvasBgImagePath().trim()).exists();
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"standaloneCompiledApp\": true,\n");
        sb.append("  \"packageName\": \"").append(ApkCompilationEngine.escapeJava(ApkCompilationEngine.getCompiledAppPackageName(project))).append("\",\n");
        sb.append("  \"projectName\": \"").append(ApkCompilationEngine.escapeJava(project.getName())).append("\",\n");
        sb.append("  \"workspaceProjectName\": \"").append(ApkCompilationEngine.escapeJava(project.getProjectName())).append("\",\n");
        sb.append("  \"versionCode\": ").append(project.getVersionCode()).append(",\n");
        sb.append("  \"versionName\": \"").append(ApkCompilationEngine.escapeJava(project.getVersionName())).append("\",\n");
        sb.append("  \"minSdk\": ").append(project.getMinSdk()).append(",\n");
        sb.append("  \"targetSdk\": ").append(project.getTargetSdk()).append(",\n");
        sb.append("  \"overlayTitle\": \"").append(ApkCompilationEngine.escapeJava(project.getOverlayTitle())).append("\",\n");
        sb.append("  \"appLogoAsset\": \"").append(hasLogo ? "app_logo.png" : "").append("\",\n");
        sb.append("  \"floatingLogoAsset\": \"").append(hasFloatingLogo ? "floating_logo.png" : "").append("\",\n");
        sb.append("  \"canvasBgImageAsset\": \"").append(hasCanvasBgImg ? "canvas_bg.png" : "").append("\",\n");
        sb.append("  \"canvasWidthDp\": ").append(project.getCanvasWidthDp()).append(",\n");
        sb.append("  \"canvasHeightDp\": ").append(project.getCanvasHeightDp()).append(",\n");
        sb.append("  \"canvasBgColorHex\": \"").append(ApkCompilationEngine.escapeJava(project.getCanvasBgColorHex())).append("\",\n");
        sb.append("  \"autoFixSize\": ").append(project.getAutoFixSize()).append(",\n");
        sb.append("  \"components\": [\n");
        for (int i = 0; i < components.size(); ++i) {
            CanvasComponentEntity c = components.get(i);
            boolean hasBgImg = c.getBgImagePath() != null && !c.getBgImagePath().trim().isEmpty() && new File(c.getBgImagePath().trim()).exists();
            boolean hasCustomImg = c.getCustomImagePath() != null && !c.getCustomImagePath().trim().isEmpty() && new File(c.getCustomImagePath().trim()).exists();
            String customImgFileName = hasCustomImg ? new File(c.getCustomImagePath().trim()).getName() : "";
            boolean hasOnSnd = c.getCustomSoundPath() != null && !c.getCustomSoundPath().trim().isEmpty() && new File(c.getCustomSoundPath().trim()).exists();
            boolean hasOffSnd = c.getOffCustomSoundPath() != null && !c.getOffCustomSoundPath().trim().isEmpty() && new File(c.getOffCustomSoundPath().trim()).exists();
            sb.append("    {").append("\"id\": ").append(c.getId()).append(", ").append("\"type\": \"").append(ApkCompilationEngine.escapeJava(c.getType())).append("\", ").append("\"label\": \"").append(ApkCompilationEngine.escapeJava(c.getLabel())).append("\", ").append("\"x\": ").append(c.getPosXDp()).append(", ").append("\"y\": ").append(c.getPosYDp()).append(", ").append("\"width\": ").append(c.getWidthDp()).append(", ").append("\"height\": ").append(c.getHeightDp()).append(", ").append("\"bgHex\": \"").append(ApkCompilationEngine.escapeJava(c.getBgColorHex())).append("\", ").append("\"textHex\": \"").append(ApkCompilationEngine.escapeJava(c.getTextColorHex())).append("\", ").append("\"bgImageAsset\": \"").append(hasBgImg ? "images/widget_bg_" + c.getId() + ".png" : "").append("\", ").append("\"onSound\": \"").append(ApkCompilationEngine.escapeJava(c.getSoundTrigger())).append("\", ").append("\"offSound\": \"").append(ApkCompilationEngine.escapeJava(c.getOffSoundTrigger())).append("\", ").append("\"customImageAsset\": \"").append(hasCustomImg ? "images/img_" + c.getId() + ".jpg" : "").append("\", ").append("\"customImageFileName\": \"").append(ApkCompilationEngine.escapeJava(customImgFileName)).append("\", ").append("\"onSoundAsset\": \"").append(hasOnSnd ? "sounds/on_snd_" + c.getId() + ".mp3" : "").append("\", ").append("\"offSoundAsset\": \"").append(hasOffSnd ? "sounds/off_snd_" + c.getId() + ".mp3" : "").append("\", ").append("\"targetFile\": \"").append(ApkCompilationEngine.escapeJava(c.getTargetFilePath())).append("\", ").append("\"offsetHex\": \"").append(ApkCompilationEngine.escapeJava(c.getByteOffsetHex())).append("\", ").append("\"onHex\": \"").append(ApkCompilationEngine.escapeJava(c.getOnPayloadHex())).append("\", ").append("\"offHex\": \"").append(ApkCompilationEngine.escapeJava(c.getOffPayloadHex())).append("\", ").append("\"sliderMax\": ").append(c.getSliderMax()).append(", ").append("\"currentValue\": \"").append(ApkCompilationEngine.escapeJava(c.getCurrentValue())).append("\", ").append("\"linkUrl\": \"").append(ApkCompilationEngine.escapeJava(c.getLinkUrl() != null ? c.getLinkUrl() : "")).append("\", ").append("\"borderColorHex\": \"").append(ApkCompilationEngine.escapeJava(c.getBorderColorHex() != null ? c.getBorderColorHex() : "#38BDF8")).append("\", ").append("\"borderStrokePercent\": ").append(c.getBorderStrokePercent()).append(", ").append("\"borderAnimation\": \"").append(ApkCompilationEngine.escapeJava(c.getBorderAnimation() != null ? c.getBorderAnimation() : "NONE")).append("\"").append("}").append(i < components.size() - 1 ? ",\n" : "\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    @NonNull
    private static String sanitizeId(@NonNull String raw) {
        String s = raw.trim().toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (s.isEmpty() || Character.isDigit(s.charAt(0))) {
            return "mod_" + s;
        }
        return s;
    }

    @NonNull
    private static String escapeXml(@NonNull String input) {
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    @NonNull
    private static String escapeJava(@NonNull String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    public static final class CompilationResult {
        public final File signedApkFile;
        public final File blueprintSourceZipFile;
        public final String rawJavaPreview;
        public final Map<String, String> generatedTextFiles;
        public final long apkSizeBytes;
        public final String compiledPackageName;
        public final String compiledAppName;

        public CompilationResult(@NonNull File signedApkFile, @NonNull File blueprintSourceZipFile, @NonNull String rawJavaPreview, @NonNull Map<String, String> generatedTextFiles, long apkSizeBytes) {
            this(signedApkFile, blueprintSourceZipFile, rawJavaPreview, generatedTextFiles, apkSizeBytes, "com.aistudio.floatconfig.app001", "Compiled App");
        }

        public CompilationResult(@NonNull File signedApkFile, @NonNull File blueprintSourceZipFile, @NonNull String rawJavaPreview, @NonNull Map<String, String> generatedTextFiles, long apkSizeBytes, @NonNull String compiledPackageName, @NonNull String compiledAppName) {
            this.signedApkFile = signedApkFile;
            this.blueprintSourceZipFile = blueprintSourceZipFile;
            this.rawJavaPreview = rawJavaPreview;
            this.generatedTextFiles = generatedTextFiles;
            this.apkSizeBytes = apkSizeBytes;
            this.compiledPackageName = compiledPackageName;
            this.compiledAppName = compiledAppName;
        }
    }

    private static final class CountingOutputStream
    extends FilterOutputStream {
        private long bytesWritten = 0L;

        CountingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            this.out.write(b);
            ++this.bytesWritten;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            this.out.write(b, off, len);
            this.bytesWritten += (long)len;
        }

        long getBytesWritten() {
            return this.bytesWritten;
        }
    }
}
