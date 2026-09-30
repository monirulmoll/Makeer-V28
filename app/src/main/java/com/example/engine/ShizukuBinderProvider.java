package com.example.engine;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import moe.shizuku.api.BinderContainer;

/**
 * ContentProvider registered at "${applicationId}.shizuku" (for both Studio Error and Compiled APKs).
 * Receives the privileged IShizukuService IBinder from the Shizuku Manager daemon (moe.shizuku.privileged.api)
 * via call("sendBinder", ...) and exposes call("getBinder", ...) so restricted Android 11-15 paths
 * (/Android/data and /Android/obb) can be modified seamlessly.
 */
public class ShizukuBinderProvider extends ContentProvider {
    public static final String METHOD_SEND_BINDER = "sendBinder";
    public static final String METHOD_GET_BINDER = "getBinder";
    public static final String EXTRA_BINDER = "moe.shizuku.privileged.api.intent.extra.BINDER";

    @Override
    public boolean onCreate() {
        Context ctx = getContext();
        if (ctx != null) {
            ShizukuPrivilegeBridge.initContext(ctx.getApplicationContext());
        }
        return true;
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        if (extras == null) {
            return null;
        }
        try {
            extras.setClassLoader(BinderContainer.class.getClassLoader());
        } catch (Throwable ignored) {
        }

        Bundle reply = new Bundle();
        if (METHOD_SEND_BINDER.equals(method)) {
            IBinder binder = extractBinderFromBundle(extras);
            if (binder != null && binder.pingBinder()) {
                ShizukuPrivilegeBridge.onBinderReceived(getContext(), binder);
            }
            return reply;
        } else if (METHOD_GET_BINDER.equals(method)) {
            IBinder current = ShizukuPrivilegeBridge.getRawBinder();
            if (current != null && current.pingBinder()) {
                reply.putParcelable(EXTRA_BINDER, new BinderContainer(current));
                try {
                    reply.putBinder(EXTRA_BINDER, current);
                } catch (Throwable ignored) {
                }
            }
            return reply;
        }
        return null;
    }

    @Nullable
    public static IBinder extractBinderFromBundle(@Nullable Bundle extras) {
        if (extras == null) {
            return null;
        }
        try {
            extras.setClassLoader(BinderContainer.class.getClassLoader());
            BinderContainer container = extras.getParcelable(EXTRA_BINDER);
            if (container != null && container.binder != null) {
                return container.binder;
            }
        } catch (Throwable ignored) {
        }
        try {
            IBinder direct = extras.getBinder(EXTRA_BINDER);
            if (direct != null) {
                return direct;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
