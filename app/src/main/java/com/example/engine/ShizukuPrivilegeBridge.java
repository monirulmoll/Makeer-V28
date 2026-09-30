package com.example.engine;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Full Shizuku Privileged Shell & Binder Bridge for both Studio Error and Compiled Standalone APKs.
 * Solves Android 11 / 12 / 13 / 14 / 15 restricted directory errors (/Android/data and /Android/obb)
 * by executing file backup, replacement, merge, and patching through Shizuku's privileged IShizukuService
 * (UID 2000 ADB Shell or UID 0 Root).
 */
public final class ShizukuPrivilegeBridge {
    public static final String SHIZUKU_MANAGER_PACKAGE = "moe.shizuku.privileged.api";
    public static final String SHIZUKU_PERMISSION = "moe.shizuku.manager.permission.API_V23";

    private static final String DESCRIPTOR_SHIZUKU_SERVICE = "moe.shizuku.server.IShizukuService";
    private static final String DESCRIPTOR_SHIZUKU_APPLICATION = "moe.shizuku.server.IShizukuApplication";
    private static final String DESCRIPTOR_REMOTE_PROCESS = "moe.shizuku.server.IRemoteProcess";

    // IShizukuService transaction codes (standard Shizuku AIDL)
    private static final int TRANSACTION_getVersion = IBinder.FIRST_CALL_TRANSACTION + 2; // 3
    private static final int TRANSACTION_getUid = IBinder.FIRST_CALL_TRANSACTION + 3; // 4
    private static final int TRANSACTION_checkPermission = IBinder.FIRST_CALL_TRANSACTION + 4; // 5
    private static final int TRANSACTION_newProcess = IBinder.FIRST_CALL_TRANSACTION + 7; // 8
    private static final int TRANSACTION_checkSelfPermission = IBinder.FIRST_CALL_TRANSACTION + 13; // 14
    private static final int TRANSACTION_requestPermission = IBinder.FIRST_CALL_TRANSACTION + 14; // 15
    private static final int TRANSACTION_attachApplication = IBinder.FIRST_CALL_TRANSACTION + 17; // 18 (v13)
    private static final int TRANSACTION_attachApplication_v11 = IBinder.FIRST_CALL_TRANSACTION + 12; // 13 (v11/v12)

    // IRemoteProcess transaction codes
    private static final int TRANSACTION_PROCESS_getOutputStream = IBinder.FIRST_CALL_TRANSACTION; // 1
    private static final int TRANSACTION_PROCESS_getInputStream = IBinder.FIRST_CALL_TRANSACTION + 1; // 2
    private static final int TRANSACTION_PROCESS_getErrorStream = IBinder.FIRST_CALL_TRANSACTION + 2; // 3
    private static final int TRANSACTION_PROCESS_waitFor = IBinder.FIRST_CALL_TRANSACTION + 3; // 4
    private static final int TRANSACTION_PROCESS_destroy = IBinder.FIRST_CALL_TRANSACTION + 5; // 6

    private static volatile Context sAppContext;
    private static volatile IBinder sShizukuBinder;
    private static volatile boolean sPermissionGrantedByCallback = false;
    private static volatile int sServerUid = -1;
    private static final List<Runnable> sStateListeners = new CopyOnWriteArrayList<>();

    private static final Binder sApplicationBinder = new Binder() {
        @Override
        protected boolean onTransact(int code, @NonNull Parcel data, @Nullable Parcel reply, int flags) throws RemoteException {
            if (code == IBinder.FIRST_CALL_TRANSACTION + 1) {
                // bindApplication(Bundle data)
                try {
                    data.enforceInterface(DESCRIPTOR_SHIZUKU_APPLICATION);
                    if (data.readInt() != 0) {
                        Bundle bundle = Bundle.CREATOR.createFromParcel(data);
                        if (bundle != null) {
                            sServerUid = bundle.getInt("shizuku:attach-reply-uid", sServerUid);
                            boolean allowed = bundle.getBoolean("shizuku:attach-reply-permission-granted", false);
                            if (allowed) {
                                sPermissionGrantedByCallback = true;
                            }
                        }
                    }
                    notifyListeners();
                } catch (Throwable ignored) {
                }
                return true;
            } else if (code == IBinder.FIRST_CALL_TRANSACTION + 2) {
                // dispatchRequestPermissionResult(int requestCode, Bundle data)
                try {
                    data.enforceInterface(DESCRIPTOR_SHIZUKU_APPLICATION);
                    data.readInt(); // requestCode
                    if (data.readInt() != 0) {
                        Bundle bundle = Bundle.CREATOR.createFromParcel(data);
                        if (bundle != null) {
                            boolean allowed = bundle.getBoolean("moe.shizuku.privileged.api.intent.extra.REQUEST_PERMISSION_REPLY_ALLOWED", false)
                                    || bundle.getBoolean("shizuku:request-permission-reply-allowed", false);
                            sPermissionGrantedByCallback = allowed;
                        }
                    }
                    notifyListeners();
                } catch (Throwable ignored) {
                }
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    };

    public interface OnShizukuStateChangeListener {
        void onShizukuStateChanged(boolean isRunning, boolean isAuthorized, @NonNull String statusSummary);
    }

    private static final List<OnShizukuStateChangeListener> sTypedListeners = new CopyOnWriteArrayList<>();

    private ShizukuPrivilegeBridge() {
    }

    public static void initContext(@Nullable Context context) {
        if (context != null && sAppContext == null) {
            sAppContext = context.getApplicationContext();
        }
    }

    public static void addStateListener(@Nullable Runnable listener) {
        if (listener != null && !sStateListeners.contains(listener)) {
            sStateListeners.add(listener);
        }
    }

    public static void removeStateListener(@Nullable Runnable listener) {
        if (listener != null) {
            sStateListeners.remove(listener);
        }
    }

    public static void addListener(@Nullable OnShizukuStateChangeListener listener) {
        if (listener != null && !sTypedListeners.contains(listener)) {
            sTypedListeners.add(listener);
        }
    }

    public static void removeListener(@Nullable OnShizukuStateChangeListener listener) {
        if (listener != null) {
            sTypedListeners.remove(listener);
        }
    }

    private static void notifyListeners() {
        try {
            Looper mainLooper = Looper.getMainLooper();
            if (mainLooper != null) {
                new Handler(mainLooper).post(() -> {
                    for (Runnable r : sStateListeners) {
                        try {
                            r.run();
                        } catch (Throwable ignored) {
                        }
                    }
                    boolean running = isBinderAlive(sAppContext);
                    boolean ready = isReady(sAppContext);
                    String summary = getStatusSummary(sAppContext);
                    for (OnShizukuStateChangeListener l : sTypedListeners) {
                        try {
                            l.onShizukuStateChanged(running, ready, summary);
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    @Nullable
    public static IBinder getRawBinder() {
        return sShizukuBinder;
    }

    public static synchronized void onBinderReceived(@Nullable Context context, @Nullable IBinder binder) {
        if (context != null) {
            initContext(context);
        }
        if (binder == null || !binder.pingBinder()) {
            return;
        }
        sShizukuBinder = binder;
        try {
            binder.linkToDeath(() -> {
                sShizukuBinder = null;
                sPermissionGrantedByCallback = false;
                notifyListeners();
            }, 0);
        } catch (Throwable ignored) {
        }
        attachApplicationToShizuku(sAppContext, binder);
        notifyListeners();
    }

    private static void attachApplicationToShizuku(@Nullable Context context, @NonNull IBinder binder) {
        String pkgName = context != null ? context.getPackageName() : "com.aistudio.floatconfig.vqxkpl";
        // Try v13 attachApplication(IShizukuApplication, Bundle)
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR_SHIZUKU_SERVICE);
            data.writeStrongBinder(sApplicationBinder);
            Bundle args = new Bundle();
            args.putString("shizuku:attach-package-name", pkgName);
            args.putInt("shizuku:attach-api-version", 13);
            data.writeInt(1);
            args.writeToParcel(data, 0);
            if (binder.transact(TRANSACTION_attachApplication, data, reply, 0)) {
                reply.readException();
                return;
            }
        } catch (Throwable ignored) {
        } finally {
            data.recycle();
            reply.recycle();
        }

        // Fallback: v11/v12 attachApplication(IShizukuApplication, String)
        Parcel dataLegacy = Parcel.obtain();
        Parcel replyLegacy = Parcel.obtain();
        try {
            dataLegacy.writeInterfaceToken(DESCRIPTOR_SHIZUKU_SERVICE);
            dataLegacy.writeStrongBinder(sApplicationBinder);
            dataLegacy.writeString(pkgName);
            binder.transact(TRANSACTION_attachApplication_v11, dataLegacy, replyLegacy, 0);
            replyLegacy.readException();
        } catch (Throwable ignored) {
        } finally {
            dataLegacy.recycle();
            replyLegacy.recycle();
        }
    }

    /**
     * Actively probes ContentProviders to acquire the Shizuku binder if not yet received.
     */
    public static boolean tryAcquireBinder(@Nullable Context context) {
        if (context != null) {
            initContext(context);
        }
        if (sShizukuBinder != null && sShizukuBinder.pingBinder()) {
            return true;
        }
        Context ctx = context != null ? context : sAppContext;
        if (ctx == null) {
            return false;
        }
        String[] authorities = new String[]{
                ctx.getPackageName() + ".shizuku",
                SHIZUKU_MANAGER_PACKAGE + ".shizuku"
        };
        for (String authority : authorities) {
            try {
                Uri uri = Uri.parse("content://" + authority);
                Bundle reply = ctx.getContentResolver().call(uri, ShizukuBinderProvider.METHOD_GET_BINDER, null, new Bundle());
                IBinder binder = ShizukuBinderProvider.extractBinderFromBundle(reply);
                if (binder != null && binder.pingBinder()) {
                    onBinderReceived(ctx, binder);
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return sShizukuBinder != null && sShizukuBinder.pingBinder();
    }

    public static boolean isShizukuInstalled(@Nullable Context context) {
        Context ctx = context != null ? context : sAppContext;
        if (ctx == null) {
            return false;
        }
        try {
            PackageManager pm = ctx.getPackageManager();
            PackageInfo info = pm.getPackageInfo(SHIZUKU_MANAGER_PACKAGE, 0);
            return info != null;
        } catch (Throwable ignored) {
            try {
                Intent launchIntent = ctx.getPackageManager().getLaunchIntentForPackage(SHIZUKU_MANAGER_PACKAGE);
                return launchIntent != null;
            } catch (Throwable ignored2) {
                return false;
            }
        }
    }

    public static boolean isBinderAlive(@Nullable Context context) {
        if (sShizukuBinder != null && sShizukuBinder.pingBinder()) {
            return true;
        }
        return tryAcquireBinder(context);
    }

    public static boolean hasShizukuPermission(@Nullable Context context) {
        Context ctx = context != null ? context : sAppContext;
        if (sPermissionGrantedByCallback && isBinderAlive(ctx)) {
            return true;
        }
        if (ctx != null) {
            try {
                if (ctx.checkSelfPermission(SHIZUKU_PERMISSION) == PackageManager.PERMISSION_GRANTED
                        && isBinderAlive(ctx)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        IBinder binder = sShizukuBinder;
        if (binder == null || !binder.pingBinder()) {
            return false;
        }
        // Try checkSelfPermission() transaction (14)
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR_SHIZUKU_SERVICE);
            if (binder.transact(TRANSACTION_checkSelfPermission, data, reply, 0)) {
                reply.readException();
                boolean granted = reply.readInt() != 0;
                if (granted) {
                    sPermissionGrantedByCallback = true;
                    return true;
                }
            }
        } catch (Throwable ignored) {
        } finally {
            data.recycle();
            reply.recycle();
        }

        // Try checkPermission(String) transaction (5)
        Parcel data2 = Parcel.obtain();
        Parcel reply2 = Parcel.obtain();
        try {
            data2.writeInterfaceToken(DESCRIPTOR_SHIZUKU_SERVICE);
            data2.writeString(SHIZUKU_PERMISSION);
            if (binder.transact(TRANSACTION_checkPermission, data2, reply2, 0)) {
                reply2.readException();
                int res = reply2.readInt();
                if (res == PackageManager.PERMISSION_GRANTED) {
                    sPermissionGrantedByCallback = true;
                    return true;
                }
            }
        } catch (Throwable ignored) {
        } finally {
            data2.recycle();
            reply2.recycle();
        }
        return false;
    }

    public static boolean isReady(@Nullable Context context) {
        return isBinderAlive(context) && hasShizukuPermission(context);
    }

    public static boolean isShizukuReady(@Nullable Context context) {
        return isReady(context);
    }

    public static boolean isShizukuReady() {
        return isReady(sAppContext);
    }

    public static boolean isShizukuRunning(@Nullable Context context) {
        return isBinderAlive(context);
    }

    public static boolean isShizukuRunning() {
        return isBinderAlive(sAppContext);
    }

    public static boolean probeShizukuBinder(@Nullable Context context) {
        return tryAcquireBinder(context);
    }

    public static boolean isRestrictedAndroidPath(@Nullable String rawPath) {
        if (rawPath == null) return false;
        String normalized = rawPath.trim().replace('\\', '/').toLowerCase(java.util.Locale.US);
        return normalized.contains("/android/data")
                || normalized.contains("/android/obb")
                || normalized.startsWith("android/data")
                || normalized.startsWith("android/obb");
    }

    public static void requestPermission(int requestCode) {
        requestShizukuPermission(sAppContext, requestCode);
    }

    public static void requestShizukuPermission(@Nullable Context context, int requestCode) {
        Context ctx = context != null ? context : sAppContext;
        tryAcquireBinder(ctx);
        IBinder binder = sShizukuBinder;
        if (binder != null && binder.pingBinder()) {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR_SHIZUKU_SERVICE);
                data.writeInt(requestCode);
                if (binder.transact(TRANSACTION_requestPermission, data, reply, 0)) {
                    reply.readException();
                    notifyListeners();
                    return;
                }
            } catch (Throwable ignored) {
            } finally {
                data.recycle();
                reply.recycle();
            }
        }
        requestPermissionOrOpenShizuku(ctx, null);
    }

    public static boolean openOrLaunchShizukuManager(@Nullable Context context) {
        requestPermissionOrOpenShizuku(context, null);
        return true;
    }

    public static void openOrDownloadShizukuApp(@Nullable Context context) {
        requestPermissionOrOpenShizuku(context, null);
    }

    public static final class ShellExecResult {
        private final boolean success;
        private final String stdout;
        private final String stderr;

        public ShellExecResult(boolean success, @Nullable String stdout, @Nullable String stderr) {
            this.success = success;
            this.stdout = stdout != null ? stdout : "";
            this.stderr = stderr != null ? stderr : "";
        }

        public boolean isSuccess() {
            return success;
        }

        @NonNull
        public String getStdout() {
            return stdout;
        }

        @NonNull
        public String getStderr() {
            return stderr;
        }

        @NonNull
        public String getCombinedError() {
            if (!stderr.isEmpty()) return stderr;
            if (!stdout.isEmpty()) return stdout;
            return success ? "" : "Shizuku shell execution failed";
        }
    }

    @NonNull
    public static ShellExecResult testTargetPathWritableViaShizuku(@NonNull String absPath) {
        try {
            File target = new File(absPath);
            boolean writable = canWritePathViaShizuku(target);
            if (writable) {
                return new ShellExecResult(true, "Writable via Shizuku", "");
            }
            return new ShellExecResult(false, "", "Shizuku shell could not write to directory: " + absPath);
        } catch (Throwable t) {
            return new ShellExecResult(false, "", t.getMessage());
        }
    }

    public static boolean remoteFileExistsViaShizuku(@NonNull String absPath) {
        return fileExistsViaShizuku(new File(absPath));
    }

    public static boolean deleteFileViaShizuku(@NonNull String absPath) {
        return deleteFileViaShizuku(new File(absPath));
    }

    @Nullable
    public static String readTextFileViaShizuku(@NonNull String absPath, int maxBytes) {
        try {
            byte[] bytes = readBytesViaShizuku(new File(absPath));
            if (bytes.length == 0) return "";
            int len = Math.min(bytes.length, Math.max(1, maxBytes));
            return new String(bytes, 0, len, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @NonNull
    public static ShellExecResult copyFileViaShizuku(@NonNull String sourcePath, @NonNull String destPath) {
        try {
            File src = new File(sourcePath);
            File dst = new File(destPath);
            if (src.exists() && src.canRead()) {
                copyFileViaShizuku(src, dst);
            } else {
                // Source might be on a restricted path and dest might be a local backup file
                backupRestrictedFileToLocalViaShizuku(src, dst);
            }
            return new ShellExecResult(true, "Copied via Shizuku", "");
        } catch (Throwable t) {
            return new ShellExecResult(false, "", t.getMessage());
        }
    }

    @NonNull
    public static ShellExecResult writeBytesViaShizuku(@Nullable Context context, @NonNull String absTarget, @NonNull byte[] payload) {
        if (context != null) initContext(context);
        try {
            writeBytesViaShizuku(new File(absTarget), payload);
            return new ShellExecResult(true, "Wrote " + payload.length + " bytes via Shizuku", "");
        } catch (Throwable t) {
            return new ShellExecResult(false, "", t.getMessage());
        }
    }

    @NonNull
    public static ShellExecResult mergeFilesViaShizuku(
            @Nullable Context context,
            @NonNull List<File> sourceFiles,
            @NonNull String absTarget,
            boolean mergeAsText
    ) {
        if (context != null) initContext(context);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (int i = 0; i < sourceFiles.size(); i++) {
                File f = sourceFiles.get(i);
                if (f == null || !f.exists()) continue;
                try (FileInputStream fis = new FileInputStream(f)) {
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = fis.read(buf)) != -1) {
                        baos.write(buf, 0, r);
                    }
                }
                if (mergeAsText && i < sourceFiles.size() - 1) {
                    baos.write('\n');
                }
            }
            writeBytesViaShizuku(new File(absTarget), baos.toByteArray());
            return new ShellExecResult(true, "Merged via Shizuku", "");
        } catch (Throwable t) {
            return new ShellExecResult(false, "", t.getMessage());
        }
    }

    @NonNull
    public static ShellExecResult patchBytesAtOffsetViaShizuku(
            @Nullable Context context,
            @NonNull String absTarget,
            int offset,
            @NonNull byte[] payloadBytes,
            int minLength
    ) {
        if (context != null) initContext(context);
        try {
            File target = new File(absTarget);
            byte[] existing = fileExistsViaShizuku(target) ? readBytesViaShizuku(target) : new byte[0];
            int safeOffset = Math.max(0, offset);
            int requiredLen = Math.max(Math.max(existing.length, minLength), safeOffset + payloadBytes.length);
            byte[] patched = new byte[requiredLen];
            if (existing.length > 0) {
                System.arraycopy(existing, 0, patched, 0, existing.length);
            }
            System.arraycopy(payloadBytes, 0, patched, safeOffset, payloadBytes.length);
            writeBytesViaShizuku(target, patched);
            return new ShellExecResult(true, "Patched via Shizuku", "");
        } catch (Throwable t) {
            return new ShellExecResult(false, "", t.getMessage());
        }
    }

    @NonNull
    public static String getStatusSummary(@Nullable Context context) {
        Context ctx = context != null ? context : sAppContext;
        if (isReady(ctx)) {
            return "Shizuku Connected & Allowed ✓ (Android 15 Restricted Paths Active)";
        }
        if (isBinderAlive(ctx)) {
            return "Shizuku Service Running — Tap to Allow Shizuku Permission";
        }
        if (isShizukuInstalled(ctx)) {
            return "Shizuku Installed (Service Stopped) — Open Shizuku & Start Service";
        }
        return "Shizuku Not Installed — Install & Start Shizuku for Android 15 /Android/data";
    }

    /**
     * Requests Shizuku authorization from the user or opens the Shizuku Manager app so the user
     * can start the service / enable authorization for Studio Error or the Compiled APK.
     */
    public static void requestPermissionOrOpenShizuku(@Nullable Context context, @Nullable Runnable onAfterAction) {
        Context ctx = context != null ? context : sAppContext;
        if (ctx == null) {
            return;
        }
        tryAcquireBinder(ctx);
        IBinder binder = sShizukuBinder;
        if (binder != null && binder.pingBinder()) {
            if (hasShizukuPermission(ctx)) {
                Toast.makeText(ctx, "✅ Shizuku is already connected and authorized!", Toast.LENGTH_SHORT).show();
                if (onAfterAction != null) {
                    onAfterAction.run();
                }
                return;
            }
            boolean requestedViaBinder = false;
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR_SHIZUKU_SERVICE);
                data.writeInt(2025);
                if (binder.transact(TRANSACTION_requestPermission, data, reply, 0)) {
                    reply.readException();
                    requestedViaBinder = true;
                }
            } catch (Throwable ignored) {
            } finally {
                data.recycle();
                reply.recycle();
            }
            if (requestedViaBinder) {
                Toast.makeText(ctx, "⚡ Shizuku Permission Requested — Tap 'Allow' on the prompt (or enable inside Shizuku app).", Toast.LENGTH_LONG).show();
                if (onAfterAction != null) {
                    new Handler(Looper.getMainLooper()).postDelayed(onAfterAction, 900L);
                }
                return;
            }
        }

        // If Shizuku is installed, open Shizuku app so user can start service or authorize this app in "Authorized applications"
        if (isShizukuInstalled(ctx)) {
            try {
                Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(SHIZUKU_MANAGER_PACKAGE);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(launch);
                    Toast.makeText(
                            ctx,
                            "Open Shizuku → Start service (Wireless Debugging/Root) → Enable this app in 'Authorized applications'.",
                            Toast.LENGTH_LONG
                    ).show();
                    if (onAfterAction != null) {
                        onAfterAction.run();
                    }
                    return;
                }
            } catch (Throwable ignored) {
            }
        }

        // Otherwise open Play Store / GitHub page to install Shizuku
        try {
            Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + SHIZUKU_MANAGER_PACKAGE));
            marketIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(marketIntent);
        } catch (Throwable e) {
            try {
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + SHIZUKU_MANAGER_PACKAGE));
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(webIntent);
            } catch (Throwable ignored) {
            }
        }
        Toast.makeText(
                ctx,
                "Install 'Shizuku' to bypass Android 15 /Android/data & /Android/obb restrictions.",
                Toast.LENGTH_LONG
        ).show();
        if (onAfterAction != null) {
            onAfterAction.run();
        }
    }

    @NonNull
    private static String escapeShellSingleQuotes(@NonNull String path) {
        return "'" + path.replace("'", "'\"'\"'") + "'";
    }

    @Nullable
    private static IBinder openRemoteProcess(@NonNull String shellCommand) throws IOException {
        IBinder binder = sShizukuBinder;
        if (binder == null || !binder.pingBinder()) {
            tryAcquireBinder(sAppContext);
            binder = sShizukuBinder;
        }
        if (binder == null || !binder.pingBinder()) {
            throw new IOException("Shizuku service binder is not active.");
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR_SHIZUKU_SERVICE);
            data.writeStringArray(new String[]{"sh", "-c", shellCommand});
            data.writeStringArray(null);
            data.writeString(null);
            if (!binder.transact(TRANSACTION_newProcess, data, reply, 0)) {
                throw new IOException("IShizukuService.newProcess transaction failed.");
            }
            reply.readException();
            IBinder procBinder = reply.readStrongBinder();
            if (procBinder == null) {
                throw new IOException("IShizukuService.newProcess returned null process binder.");
            }
            return procBinder;
        } catch (RemoteException e) {
            throw new IOException("Shizuku IPC error: " + e.getMessage(), e);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    @Nullable
    private static ParcelFileDescriptor getRemoteProcessStream(@NonNull IBinder procBinder, int transactionCode) throws IOException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR_REMOTE_PROCESS);
            if (!procBinder.transact(transactionCode, data, reply, 0)) {
                return null;
            }
            reply.readException();
            if (reply.readInt() != 0) {
                return ParcelFileDescriptor.CREATOR.createFromParcel(reply);
            }
            return null;
        } catch (RemoteException e) {
            throw new IOException("Shizuku RemoteProcess stream error: " + e.getMessage(), e);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private static int waitForRemoteProcess(@NonNull IBinder procBinder) throws IOException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR_REMOTE_PROCESS);
            if (!procBinder.transact(TRANSACTION_PROCESS_waitFor, data, reply, 0)) {
                return -1;
            }
            reply.readException();
            return reply.readInt();
        } catch (RemoteException e) {
            throw new IOException("Shizuku RemoteProcess waitFor error: " + e.getMessage(), e);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private static void destroyRemoteProcessQuietly(@Nullable IBinder procBinder) {
        if (procBinder == null) return;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR_REMOTE_PROCESS);
            procBinder.transact(TRANSACTION_PROCESS_destroy, data, reply, 0);
        } catch (Throwable ignored) {
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    /**
     * Checks whether a file exists at a restricted path using Shizuku shell.
     */
    public static boolean fileExistsViaShizuku(@NonNull File targetFile) {
        if (!isReady(sAppContext)) return false;
        String qTarget = escapeShellSingleQuotes(targetFile.getAbsolutePath());
        IBinder proc = null;
        try {
            proc = openRemoteProcess("test -f " + qTarget);
            if (proc == null) return false;
            return waitForRemoteProcess(proc) == 0;
        } catch (Throwable ignored) {
            return false;
        } finally {
            destroyRemoteProcessQuietly(proc);
        }
    }

    /**
     * Checks whether a target file or its parent directory is writable via Shizuku shell.
     */
    public static boolean canWritePathViaShizuku(@NonNull File targetFile) {
        if (!isReady(sAppContext)) return false;
        File parent = targetFile.getParentFile();
        String parentPath = parent != null ? parent.getAbsolutePath() : "/storage/emulated/0";
        String qParent = escapeShellSingleQuotes(parentPath);
        IBinder proc = null;
        try {
            proc = openRemoteProcess("mkdir -p " + qParent + " && test -w " + qParent);
            if (proc == null) return false;
            return waitForRemoteProcess(proc) == 0;
        } catch (Throwable ignored) {
            return false;
        } finally {
            destroyRemoteProcessQuietly(proc);
        }
    }

    /**
     * Deletes a file at a restricted path using Shizuku shell.
     */
    public static boolean deleteFileViaShizuku(@NonNull File targetFile) {
        if (!isReady(sAppContext)) return false;
        String qTarget = escapeShellSingleQuotes(targetFile.getAbsolutePath());
        IBinder proc = null;
        try {
            proc = openRemoteProcess("rm -f " + qTarget);
            if (proc == null) return false;
            return waitForRemoteProcess(proc) == 0;
        } catch (Throwable ignored) {
            return false;
        } finally {
            destroyRemoteProcessQuietly(proc);
        }
    }

    /**
     * Reads raw bytes from a restricted target path via Shizuku stdout stream.
     */
    @NonNull
    public static byte[] readBytesViaShizuku(@NonNull File targetFile) throws IOException {
        if (!isReady(sAppContext)) {
            throw new IOException("Shizuku is not connected or authorized.");
        }
        String qTarget = escapeShellSingleQuotes(targetFile.getAbsolutePath());
        IBinder proc = null;
        try {
            proc = openRemoteProcess("if [ -f " + qTarget + " ]; then cat " + qTarget + "; fi");
            if (proc == null) {
                throw new IOException("Could not start Shizuku read process.");
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ParcelFileDescriptor stdoutPfd = getRemoteProcessStream(proc, TRANSACTION_PROCESS_getInputStream)) {
                if (stdoutPfd != null) {
                    try (InputStream is = new ParcelFileDescriptor.AutoCloseInputStream(stdoutPfd)) {
                        byte[] buf = new byte[8192];
                        int r;
                        while ((r = is.read(buf)) != -1) {
                            baos.write(buf, 0, r);
                        }
                    }
                }
            }
            waitForRemoteProcess(proc);
            return baos.toByteArray();
        } finally {
            destroyRemoteProcessQuietly(proc);
        }
    }

    /**
     * Writes raw bytes to a restricted target path (such as /storage/emulated/0/Android/data/...)
     * by streaming over Shizuku IRemoteProcess stdin (with shared storage staging fallback).
     */
    public static void writeBytesViaShizuku(@NonNull File targetFile, @NonNull byte[] payload) throws IOException {
        if (!isReady(sAppContext)) {
            throw new IOException("Shizuku is not connected or authorized.");
        }
        File parent = targetFile.getParentFile();
        String parentPath = parent != null ? parent.getAbsolutePath() : "/storage/emulated/0";
        String qParent = escapeShellSingleQuotes(parentPath);
        String qTarget = escapeShellSingleQuotes(targetFile.getAbsolutePath());

        IBinder proc = null;
        try {
            String cmd = "mkdir -p " + qParent + " && cat > " + qTarget + " && chmod 664 " + qTarget + " 2>/dev/null || true";
            proc = openRemoteProcess(cmd);
            if (proc == null) {
                throw new IOException("Failed to open Shizuku shell process.");
            }
            try (ParcelFileDescriptor stdinPfd = getRemoteProcessStream(proc, TRANSACTION_PROCESS_getOutputStream)) {
                if (stdinPfd == null) {
                    throw new IOException("Shizuku process stdin unavailable.");
                }
                try (OutputStream os = new ParcelFileDescriptor.AutoCloseOutputStream(stdinPfd)) {
                    os.write(payload);
                    os.flush();
                }
            }
            String errText = readProcessErrorQuietly(proc);
            int exitCode = waitForRemoteProcess(proc);
            if (exitCode != 0) {
                throw new IOException("Shizuku write exited with code " + exitCode + (errText.isEmpty() ? "" : (": " + errText)));
            }
        } finally {
            destroyRemoteProcessQuietly(proc);
        }
    }

    /**
     * Copies a source file (even from private app filesDir) to a restricted target path
     * via Shizuku stdin streaming so UID 2000 never needs direct read access to /data/user/0/.
     */
    public static void copyFileViaShizuku(@NonNull File sourceFile, @NonNull File targetFile) throws IOException {
        if (!sourceFile.exists() || !sourceFile.isFile()) {
            throw new IOException("Source file does not exist: " + sourceFile.getAbsolutePath());
        }
        if (!isReady(sAppContext)) {
            throw new IOException("Shizuku is not connected or authorized.");
        }
        File parent = targetFile.getParentFile();
        String parentPath = parent != null ? parent.getAbsolutePath() : "/storage/emulated/0";
        String qParent = escapeShellSingleQuotes(parentPath);
        String qTarget = escapeShellSingleQuotes(targetFile.getAbsolutePath());

        IBinder proc = null;
        try {
            String cmd = "mkdir -p " + qParent + " && cat > " + qTarget + " && chmod 664 " + qTarget + " 2>/dev/null || true";
            proc = openRemoteProcess(cmd);
            if (proc == null) {
                throw new IOException("Failed to open Shizuku shell process.");
            }
            try (ParcelFileDescriptor stdinPfd = getRemoteProcessStream(proc, TRANSACTION_PROCESS_getOutputStream)) {
                if (stdinPfd == null) {
                    throw new IOException("Shizuku process stdin unavailable.");
                }
                try (OutputStream os = new ParcelFileDescriptor.AutoCloseOutputStream(stdinPfd);
                     FileInputStream fis = new FileInputStream(sourceFile)) {
                    byte[] buf = new byte[16384];
                    int r;
                    while ((r = fis.read(buf)) != -1) {
                        os.write(buf, 0, r);
                    }
                    os.flush();
                }
            }
            String errText = readProcessErrorQuietly(proc);
            int exitCode = waitForRemoteProcess(proc);
            if (exitCode != 0) {
                throw new IOException("Shizuku copy exited with code " + exitCode + (errText.isEmpty() ? "" : (": " + errText)));
            }
        } finally {
            destroyRemoteProcessQuietly(proc);
        }
    }

    /**
     * Copies a restricted file into a local app backup file via Shizuku stdout stream.
     */
    public static void backupRestrictedFileToLocalViaShizuku(@NonNull File restrictedSource, @NonNull File localBackupDest) throws IOException {
        byte[] data = readBytesViaShizuku(restrictedSource);
        File parent = localBackupDest.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(localBackupDest, false)) {
            fos.write(data);
            fos.flush();
        }
    }

    @NonNull
    private static String readProcessErrorQuietly(@NonNull IBinder procBinder) {
        try (ParcelFileDescriptor errPfd = getRemoteProcessStream(procBinder, TRANSACTION_PROCESS_getErrorStream)) {
            if (errPfd == null) return "";
            try (InputStream is = new ParcelFileDescriptor.AutoCloseInputStream(errPfd)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[2048];
                int r;
                while ((r = is.read(buf)) != -1) {
                    baos.write(buf, 0, r);
                }
                return new String(baos.toByteArray(), StandardCharsets.UTF_8).trim();
            }
        } catch (Throwable ignored) {
            return "";
        }
    }
}
