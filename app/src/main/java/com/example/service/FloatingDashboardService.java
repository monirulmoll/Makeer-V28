/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.annotation.SuppressLint
 *  android.app.Notification
 *  android.app.NotificationChannel
 *  android.app.NotificationManager
 *  android.app.PendingIntent
 *  android.app.Service
 *  android.content.Context
 *  android.content.Intent
 *  android.graphics.Bitmap
 *  android.graphics.Bitmap$Config
 *  android.graphics.BitmapFactory
 *  android.graphics.BitmapShader
 *  android.graphics.Canvas
 *  android.graphics.Color
 *  android.graphics.Paint
 *  android.graphics.Paint$Style
 *  android.graphics.Shader
 *  android.graphics.Shader$TileMode
 *  android.graphics.Typeface
 *  android.graphics.drawable.Drawable
 *  android.graphics.drawable.GradientDrawable
 *  android.net.Uri
 *  android.os.Build$VERSION
 *  android.os.IBinder
 *  android.provider.Settings
 *  android.text.Editable
 *  android.text.TextUtils$TruncateAt
 *  android.text.TextWatcher
 *  android.view.MotionEvent
 *  android.view.VelocityTracker
 *  android.view.View
 *  android.view.View$MeasureSpec
 *  android.view.View$OnClickListener
 *  android.view.View$OnTouchListener
 *  android.view.ViewConfiguration
 *  android.view.ViewGroup$LayoutParams
 *  android.view.WindowManager
 *  android.view.WindowManager$LayoutParams
 *  android.widget.EditText
 *  android.widget.FrameLayout
 *  android.widget.FrameLayout$LayoutParams
 *  android.widget.ImageView
 *  android.widget.ImageView$ScaleType
 *  android.widget.LinearLayout
 *  android.widget.LinearLayout$LayoutParams
 *  android.widget.OverScroller
 *  android.widget.SeekBar
 *  android.widget.SeekBar$OnSeekBarChangeListener
 *  android.widget.Switch
 *  android.widget.TextView
 *  androidx.annotation.Nullable
 *  androidx.core.app.NotificationCompat$Builder
 *  com.example.MainActivity
 *  com.example.R$mipmap
 *  com.example.R$string
 */
package com.example.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.OverScroller;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.example.MainActivity;
import com.example.R;
import com.example.engine.ConfigParameterSpec;
import com.example.engine.LocalConfigStateWriter;
import com.example.engine.ShizukuPrivilegeBridge;
import com.example.engine.SoundTriggerPlayer;
import com.example.service.DynamicOverlayRegistry;
import java.io.File;
import java.util.List;

public class FloatingDashboardService
extends Service {
    public static final String ACTION_START_OVERLAY = "com.example.service.ACTION_START_OVERLAY";
    public static final String ACTION_STOP_OVERLAY = "com.example.service.ACTION_STOP_OVERLAY";
    private static final String CHANNEL_ID = "studio_error_overlay_channel";
    private static final int NOTIFICATION_ID = 4102;
    private static final Object OVERLAY_LOCK = new Object();
    private static volatile boolean running = false;
    private static View sActiveFloatingRootView = null;
    private static View sActiveErrorDialogView = null;
    private static WindowManager sActiveWindowManager = null;
    private WindowManager windowManager;
    private View floatingRootView;
    private WindowManager.LayoutParams overlayLayoutParams;
    private TextView shizukuHeaderBadgeTv;

    private final LocalConfigStateWriter.OnStateWriteListener floatingWriteListener = new LocalConfigStateWriter.OnStateWriteListener() {
        @Override
        public void onWriteSuccess(String key, int offset, String oldVal, String newVal, long durationMicros, ConfigParameterSpec.StateSnapshot snapshot) {
            FloatingDashboardService.this.refreshShizukuHeaderBadge();
            if (newVal != null && (newVal.contains("[Shizuku]") || newVal.contains("via Shizuku") || newVal.startsWith("Replaced") || newVal.startsWith("Merged") || newVal.startsWith("Restored"))) {
                Toast.makeText(FloatingDashboardService.this, "\u2713 Target File Changed: " + newVal, Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onWriteError(String key, String message) {
        }

        @Override
        public void onWriteDiagnosticError(LocalConfigStateWriter.WriteDiagnosticReport report) {
            FloatingDashboardService.this.refreshShizukuHeaderBadge();
            FloatingDashboardService.this.showFloatingErrorDiagnosticDialog(report);
        }
    };

    private final ShizukuPrivilegeBridge.OnShizukuStateChangeListener shizukuStateListener = (binderAlive, permissionGranted, uid) -> {
        if (this.floatingRootView != null) {
            this.floatingRootView.post(this::refreshShizukuHeaderBadge);
        }
    };

    public static boolean isRunning() {
        return running;
    }

    public void onCreate() {
        super.onCreate();
        this.windowManager = (WindowManager)this.getSystemService("window");
        this.createNotificationChannel();
        LocalConfigStateWriter.getInstance().bindAppContext(this.getApplicationContext());
        LocalConfigStateWriter.getInstance().addListener(this.floatingWriteListener);
        ShizukuPrivilegeBridge.addListener(this.shizukuStateListener);
        ShizukuPrivilegeBridge.probeShizukuBinder(this.getApplicationContext());
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_OVERLAY.equals(intent.getAction())) {
            this.removeSystemOverlayWindow();
            running = false;
            this.stopSelf();
            return 2;
        }
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                this.startForeground(4102, this.buildForegroundNotification(), 0x40000000);
            } else {
                this.startForeground(4102, this.buildForegroundNotification());
            }
        }
        catch (Throwable ignored) {
            try {
                this.startForeground(4102, this.buildForegroundNotification());
            }
            catch (Throwable ignored2) {
                // empty catch block
            }
        }
        boolean hasOverlay = Settings.canDrawOverlays((Context)this);
        if (hasOverlay) {
            this.removeSystemOverlayWindow();
            this.showDynamicSystemOverlayWindow();
            running = true;
        } else {
            this.removeSystemOverlayWindow();
            running = false;
            this.stopSelf();
        }
        return 1;
    }

    private boolean isColorDark(int color) {
        double luminance = (0.299 * (double)Color.red((int)color) + 0.587 * (double)Color.green((int)color) + 0.114 * (double)Color.blue((int)color)) / 255.0;
        return luminance < 0.55 || Color.alpha((int)color) < 160;
    }

    @SuppressLint(value={"ClickableViewAccessibility"})
    private void showDynamicSystemOverlayWindow() {
        File lf;
        DynamicOverlayRegistry.loadFromBundledAssetsIfEmpty((Context)this);
        int panelWidthDp = Math.max(180, Math.min(340, DynamicOverlayRegistry.getActiveCanvasWidthDp()));
        int panelHeightDp = Math.max(160, Math.min(480, DynamicOverlayRegistry.getActiveCanvasHeightDp()));
        final int currentCanvasW = this.dpToPx(panelWidthDp);
        final int currentCanvasH = this.dpToPx(panelHeightDp);

        int overlayType = Build.VERSION.SDK_INT >= 26 ? 2038 : 2002;
        this.overlayLayoutParams = new WindowManager.LayoutParams(currentCanvasW, currentCanvasH, overlayType, 264, -3);
        this.overlayLayoutParams.gravity = 0x800033;
        this.overlayLayoutParams.x = 32;
        this.overlayLayoutParams.y = 160;

        int resolvedBgColor = this.parseSafeColor(DynamicOverlayRegistry.getActiveCanvasBgHex(), -1);
        String canvasBgImgPath = DynamicOverlayRegistry.getActiveCanvasBgImagePath();
        Bitmap bgBmp = null;
        if (canvasBgImgPath != null && !canvasBgImgPath.trim().isEmpty()) {
            File bgFile = new File(canvasBgImgPath.trim());
            if (bgFile.exists()) {
                bgBmp = BitmapFactory.decodeFile((String)bgFile.getAbsolutePath());
            }
        }
        boolean useLightHeaderContent = bgBmp != null || this.isColorDark(resolvedBgColor);
        int headerContentColor = useLightHeaderContent ? -1 : Color.parseColor((String)"#0F172A");

        final FrameLayout panelRoot = new FrameLayout((Context)this);
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(resolvedBgColor);
        panelBg.setCornerRadius((float)this.dpToPx(16));
        panelRoot.setBackground((Drawable)panelBg);
        panelRoot.setClipToOutline(true);
        panelRoot.setClipChildren(true);

        if (bgBmp != null) {
            Bitmap croppedBgBmp = this.createRoundedCenterCropBitmap(bgBmp, currentCanvasW, currentCanvasH, this.dpToPx(16));
            ImageView bgIv = new ImageView((Context)this);
            bgIv.setImageBitmap(croppedBgBmp != null ? croppedBgBmp : bgBmp);
            bgIv.setScaleType(ImageView.ScaleType.FIT_XY);
            GradientDrawable clipBg = new GradientDrawable();
            clipBg.setCornerRadius((float)this.dpToPx(16));
            bgIv.setBackground((Drawable)clipBg);
            bgIv.setClipToOutline(true);
            panelRoot.addView((View)bgIv, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(currentCanvasW, currentCanvasH));
        }

        final LinearLayout container = new LinearLayout((Context)this);
        container.setOrientation(1);
        container.setBackgroundColor(0);

        LinearLayout header = new LinearLayout((Context)this);
        header.setOrientation(0);
        header.setGravity(16);
        header.setPadding(this.dpToPx(10), this.dpToPx(8), this.dpToPx(8), this.dpToPx(8));
        header.setBackgroundColor(0);

        List<DynamicOverlayRegistry.OverlayItemSpec> specs = DynamicOverlayRegistry.getActiveItems();
        String activeTitle = DynamicOverlayRegistry.getActiveOverlayTitle();
        String displayTitle = activeTitle != null && !activeTitle.trim().isEmpty() ? activeTitle.trim() : DynamicOverlayRegistry.getActiveProjectName();
        if (displayTitle == null || displayTitle.trim().isEmpty()) {
            displayTitle = "Floating Panel";
        }
        String floatingLogoPath = DynamicOverlayRegistry.getActiveFloatingLogoPath();
        if (floatingLogoPath == null || floatingLogoPath.trim().isEmpty()) {
            floatingLogoPath = DynamicOverlayRegistry.getActiveAppLogoPath();
        }
        Bitmap rawLogoBitmap = null;
        if (floatingLogoPath != null && !floatingLogoPath.trim().isEmpty() && (lf = new File(floatingLogoPath.trim())).exists()) {
            rawLogoBitmap = BitmapFactory.decodeFile((String)lf.getAbsolutePath());
        }
        if (rawLogoBitmap != null) {
            ImageView headerLogoIv = new ImageView((Context)this);
            headerLogoIv.setImageBitmap(this.createCircularBitmap(rawLogoBitmap, this.dpToPx(24)));
            LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(this.dpToPx(24), this.dpToPx(24));
            logoLp.rightMargin = this.dpToPx(8);
            header.addView((View)headerLogoIv, (ViewGroup.LayoutParams)logoLp);
        } else {
            TextView badgeCircle = new TextView((Context)this);
            badgeCircle.setText((CharSequence)"\u2726");
            badgeCircle.setTextColor(headerContentColor);
            badgeCircle.setTextSize(2, 10.0f);
            badgeCircle.setGravity(17);
            GradientDrawable circleDrawable = new GradientDrawable();
            circleDrawable.setShape(1);
            circleDrawable.setColor(useLightHeaderContent ? Color.parseColor((String)"#33FFFFFF") : Color.parseColor((String)"#1F0F172A"));
            circleDrawable.setStroke(this.dpToPx(1), headerContentColor);
            badgeCircle.setBackground((Drawable)circleDrawable);
            LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(this.dpToPx(24), this.dpToPx(24));
            logoLp.rightMargin = this.dpToPx(8);
            header.addView((View)badgeCircle, (ViewGroup.LayoutParams)logoLp);
        }
        TextView titleTv = new TextView((Context)this);
        titleTv.setText((CharSequence)displayTitle);
        titleTv.setTextColor(headerContentColor);
        titleTv.setTextSize(2, 13.0f);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        header.addView((View)titleTv, (ViewGroup.LayoutParams)titleLp);
        int bubbleSizePx = this.dpToPx(64);
        final FrameLayout goalLogoBubble = new FrameLayout((Context)this);
        GradientDrawable bubbleBg = new GradientDrawable();
        bubbleBg.setShape(1);
        bubbleBg.setColor(resolvedBgColor);
        bubbleBg.setStroke(this.dpToPx(2), headerContentColor);
        goalLogoBubble.setBackground((Drawable)bubbleBg);
        goalLogoBubble.setVisibility(8);
        if (rawLogoBitmap != null) {
            ImageView bubbleIv = new ImageView((Context)this);
            bubbleIv.setImageBitmap(this.createCircularBitmap(rawLogoBitmap, bubbleSizePx));
            bubbleIv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            goalLogoBubble.addView((View)bubbleIv, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -1));
        } else {
            TextView bubbleTitleTv = new TextView((Context)this);
            String fallbackBubbleText = displayTitle != null && !displayTitle.trim().isEmpty() ? displayTitle.trim() : "Float";
            bubbleTitleTv.setText((CharSequence)fallbackBubbleText);
            bubbleTitleTv.setTextColor(headerContentColor);
            bubbleTitleTv.setTextSize(2, 10.0f);
            bubbleTitleTv.setTypeface(Typeface.DEFAULT_BOLD);
            bubbleTitleTv.setGravity(17);
            bubbleTitleTv.setMaxLines(2);
            bubbleTitleTv.setEllipsize(TextUtils.TruncateAt.END);
            bubbleTitleTv.setPadding(this.dpToPx(6), this.dpToPx(4), this.dpToPx(6), this.dpToPx(4));
            goalLogoBubble.addView((View)bubbleTitleTv, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -1, 17));
        }
        int pillFillColor = useLightHeaderContent ? Color.parseColor((String)"#2EFFFFFF") : Color.parseColor((String)"#140F172A");

        TextView shzBadge = new TextView((Context)this);
        shzBadge.setTextSize(2, 8.0f);
        shzBadge.setTypeface(Typeface.DEFAULT_BOLD);
        shzBadge.setSingleLine(true);
        shzBadge.setGravity(17);
        shzBadge.setPadding(this.dpToPx(5), this.dpToPx(3), this.dpToPx(5), this.dpToPx(3));
        LinearLayout.LayoutParams shzLp = new LinearLayout.LayoutParams(-2, -2);
        shzLp.rightMargin = this.dpToPx(4);
        this.shizukuHeaderBadgeTv = shzBadge;
        this.refreshShizukuHeaderBadge();
        shzBadge.setOnClickListener(v -> {
            Context appCtx = this.getApplicationContext();
            ShizukuPrivilegeBridge.probeShizukuBinder(appCtx);
            if (ShizukuPrivilegeBridge.isShizukuReady(appCtx)) {
                Toast.makeText((Context)this, (CharSequence)("Shizuku Active (" + ShizukuPrivilegeBridge.getStatusSummary(appCtx) + "). Testing target path..."), (int)0).show();
                LocalConfigStateWriter.getInstance().runFloatStartupTargetTestAndApplyAsync(appCtx, this.getFilesDir(), DynamicOverlayRegistry.getActiveItems());
            } else if (ShizukuPrivilegeBridge.isShizukuRunning(appCtx)) {
                Toast.makeText((Context)this, (CharSequence)"Requesting Shizuku permission for restricted path access...", (int)0).show();
                ShizukuPrivilegeBridge.requestShizukuPermission(appCtx, 9401);
            } else {
                LocalConfigStateWriter.WriteDiagnosticReport lastErr = LocalConfigStateWriter.getInstance().getLastDiagnosticReport();
                if (lastErr != null) {
                    this.showFloatingErrorDiagnosticDialog(lastErr);
                } else {
                    LocalConfigStateWriter.getInstance().runFloatStartupTargetTestAndApplyAsync(appCtx, this.getFilesDir(), DynamicOverlayRegistry.getActiveItems());
                }
            }
            this.refreshShizukuHeaderBadge();
        });
        header.addView((View)shzBadge, (ViewGroup.LayoutParams)shzLp);

        TextView minimizeBtn = new TextView((Context)this);
        minimizeBtn.setText((CharSequence)"Minimize");
        minimizeBtn.setTextColor(headerContentColor);
        minimizeBtn.setTextSize(2, 8.5f);
        minimizeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        minimizeBtn.setSingleLine(true);
        minimizeBtn.setGravity(17);
        minimizeBtn.setPadding(this.dpToPx(5), this.dpToPx(3), this.dpToPx(5), this.dpToPx(3));
        GradientDrawable minBtnBg = new GradientDrawable();
        minBtnBg.setCornerRadius((float)this.dpToPx(6));
        minBtnBg.setColor(pillFillColor);
        minBtnBg.setStroke(this.dpToPx(1), headerContentColor);
        minimizeBtn.setBackground((Drawable)minBtnBg);
        LinearLayout.LayoutParams minBtnLp = new LinearLayout.LayoutParams(-2, -2);
        minBtnLp.rightMargin = this.dpToPx(4);
        minimizeBtn.setOnClickListener(v -> {
            this.setOverlayFocusable(false);
            panelRoot.setVisibility(8);
            goalLogoBubble.setAlpha(1.0f);
            goalLogoBubble.setVisibility(0);
            if (this.overlayLayoutParams != null) {
                this.overlayLayoutParams.width = bubbleSizePx;
                this.overlayLayoutParams.height = bubbleSizePx;
            }
            if (this.floatingRootView != null && this.windowManager != null) {
                this.windowManager.updateViewLayout(this.floatingRootView, (ViewGroup.LayoutParams)this.overlayLayoutParams);
            }
        });
        header.addView((View)minimizeBtn, (ViewGroup.LayoutParams)minBtnLp);

        TextView hideBtn = new TextView((Context)this);
        hideBtn.setText((CharSequence)"Hide");
        hideBtn.setTextColor(headerContentColor);
        hideBtn.setTextSize(2, 8.5f);
        hideBtn.setTypeface(Typeface.DEFAULT_BOLD);
        hideBtn.setSingleLine(true);
        hideBtn.setGravity(17);
        hideBtn.setPadding(this.dpToPx(5), this.dpToPx(3), this.dpToPx(5), this.dpToPx(3));
        GradientDrawable hideBtnBg = new GradientDrawable();
        hideBtnBg.setCornerRadius((float)this.dpToPx(6));
        hideBtnBg.setColor(pillFillColor);
        hideBtnBg.setStroke(this.dpToPx(1), headerContentColor);
        hideBtn.setBackground((Drawable)hideBtnBg);
        LinearLayout.LayoutParams hideBtnLp = new LinearLayout.LayoutParams(-2, -2);
        hideBtnLp.rightMargin = this.dpToPx(4);
        hideBtn.setOnClickListener(v -> {
            this.setOverlayFocusable(false);
            panelRoot.setVisibility(8);
            goalLogoBubble.setAlpha(0.0f);
            goalLogoBubble.setVisibility(0);
            if (this.overlayLayoutParams != null) {
                this.overlayLayoutParams.width = bubbleSizePx;
                this.overlayLayoutParams.height = bubbleSizePx;
            }
            if (this.floatingRootView != null && this.windowManager != null) {
                this.windowManager.updateViewLayout(this.floatingRootView, (ViewGroup.LayoutParams)this.overlayLayoutParams);
            }
            Toast.makeText((Context)this, (CharSequence)"Floating Window Hidden. Tap spot or START to show.", (int)0).show();
        });
        header.addView((View)hideBtn, (ViewGroup.LayoutParams)hideBtnLp);

        TextView killBtn = new TextView((Context)this);
        killBtn.setText((CharSequence)"Kill");
        killBtn.setTextColor(-1);
        killBtn.setTextSize(2, 8.5f);
        killBtn.setTypeface(Typeface.DEFAULT_BOLD);
        killBtn.setSingleLine(true);
        killBtn.setGravity(17);
        killBtn.setPadding(this.dpToPx(6), this.dpToPx(3), this.dpToPx(6), this.dpToPx(3));
        GradientDrawable killBtnBg = new GradientDrawable();
        killBtnBg.setCornerRadius((float)this.dpToPx(6));
        killBtnBg.setColor(Color.parseColor((String)"#D9EF4444"));
        killBtnBg.setStroke(this.dpToPx(1), Color.parseColor((String)"#FCA5A5"));
        killBtn.setBackground((Drawable)killBtnBg);
        killBtn.setOnClickListener(v -> {
            this.setOverlayFocusable(false);
            this.removeSystemOverlayWindow();
            running = false;
            try {
                this.stopForeground(true);
            }
            catch (Throwable ignored) {
                // empty catch block
            }
            this.stopSelf();
            Toast.makeText((Context)this, (CharSequence)"Floating Window Closed", (int)0).show();
        });
        header.addView((View)killBtn);
        boolean isAutoFix = DynamicOverlayRegistry.isActiveAutoFixSize();
        FrameLayout canvasFrame = new FrameLayout((Context)this);
        LinearLayout.LayoutParams canvasLp = new LinearLayout.LayoutParams(-1, 0, 1.0f);
        OverflowOnlyScrollLayout scrollView = new OverflowOnlyScrollLayout((Context)this);
        FrameLayout.LayoutParams scrollLp = new FrameLayout.LayoutParams(-1, -1);
        if (isAutoFix) {
            LinearLayout autoStack = new LinearLayout((Context)this);
            autoStack.setOrientation(1);
            autoStack.setPadding(this.dpToPx(8), this.dpToPx(8), this.dpToPx(8), this.dpToPx(8));
            for (int i = 0; i < specs.size(); ++i) {
                DynamicOverlayRegistry.OverlayItemSpec spec = specs.get(i);
                View childView = this.buildDynamicComponentView(spec);
                LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(-1, this.dpToPx(Math.max(36, spec.heightDp)));
                if (i < specs.size() - 1) {
                    itemLp.bottomMargin = this.dpToPx(6);
                }
                autoStack.addView(childView, (ViewGroup.LayoutParams)itemLp);
            }
            scrollView.addView((View)autoStack, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -2));
        } else {
            FrameLayout freeCanvas = new FrameLayout((Context)this);
            int minTopDp = 0;
            for (DynamicOverlayRegistry.OverlayItemSpec spec : specs) {
                minTopDp = Math.min(minTopDp, spec.posYDp);
            }
            int topOverflowShiftDp = minTopDp < 0 ? -minTopDp + 8 : 0;
            int maxBottomDp = 0;
            for (DynamicOverlayRegistry.OverlayItemSpec spec : specs) {
                View childView = this.buildDynamicComponentView(spec);
                int wDp = Math.max(36, spec.widthDp);
                int hDp = Math.max(32, spec.heightDp);
                int xDp = Math.max(0, spec.posXDp);
                int yDp = spec.posYDp + topOverflowShiftDp;
                maxBottomDp = Math.max(maxBottomDp, yDp + hDp);
                FrameLayout.LayoutParams itemLp = new FrameLayout.LayoutParams(this.dpToPx(wDp), this.dpToPx(hDp));
                itemLp.leftMargin = this.dpToPx(xDp);
                itemLp.topMargin = this.dpToPx(yDp);
                freeCanvas.addView(childView, (ViewGroup.LayoutParams)itemLp);
            }
            int bodyHeightPx = Math.max(this.dpToPx(100), currentCanvasH - this.dpToPx(40));
            if (minTopDp < 0 || this.dpToPx(maxBottomDp) > bodyHeightPx) {
                freeCanvas.setPadding(0, 0, 0, this.dpToPx(8));
            }
            scrollView.addView((View)freeCanvas, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -2));
        }
        canvasFrame.addView((View)scrollView, (ViewGroup.LayoutParams)scrollLp);
        header.setOnTouchListener(new View.OnTouchListener(){
            private float downRawX;
            private float downRawY;
            private int startWinX;
            private int startWinY;

            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case 0: {
                        this.downRawX = event.getRawX();
                        this.downRawY = event.getRawY();
                        this.startWinX = FloatingDashboardService.this.overlayLayoutParams.x;
                        this.startWinY = FloatingDashboardService.this.overlayLayoutParams.y;
                        FloatingDashboardService.this.setOverlayFocusable(false);
                        return true;
                    }
                    case 2: {
                        int totalDx = Math.round(event.getRawX() - this.downRawX);
                        int totalDy = Math.round(event.getRawY() - this.downRawY);
                        int nextX = Math.max(0, this.startWinX + totalDx);
                        int nextY = Math.max(32, this.startWinY + totalDy);
                        if (FloatingDashboardService.this.floatingRootView != null && FloatingDashboardService.this.windowManager != null && (nextX != FloatingDashboardService.this.overlayLayoutParams.x || nextY != FloatingDashboardService.this.overlayLayoutParams.y)) {
                            FloatingDashboardService.this.overlayLayoutParams.x = nextX;
                            FloatingDashboardService.this.overlayLayoutParams.y = nextY;
                            FloatingDashboardService.this.windowManager.updateViewLayout(FloatingDashboardService.this.floatingRootView, (ViewGroup.LayoutParams)FloatingDashboardService.this.overlayLayoutParams);
                        }
                        return true;
                    }
                }
                return false;
            }
        });
        container.addView((View)header, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        container.addView((View)canvasFrame, (ViewGroup.LayoutParams)canvasLp);
        panelRoot.addView((View)container, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(currentCanvasW, currentCanvasH));

        final int bubbleTouchSlop = ViewConfiguration.get((Context)this).getScaledTouchSlop();
        goalLogoBubble.setOnTouchListener(new View.OnTouchListener(){
            private float downRawX;
            private float downRawY;
            private int startWinX;
            private int startWinY;
            private boolean wasDragged;

            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case 0: {
                        this.downRawX = event.getRawX();
                        this.downRawY = event.getRawY();
                        this.startWinX = FloatingDashboardService.this.overlayLayoutParams.x;
                        this.startWinY = FloatingDashboardService.this.overlayLayoutParams.y;
                        this.wasDragged = false;
                        return true;
                    }
                    case 2: {
                        int totalDx = Math.round(event.getRawX() - this.downRawX);
                        int totalDy = Math.round(event.getRawY() - this.downRawY);
                        if (Math.abs(totalDx) > bubbleTouchSlop || Math.abs(totalDy) > bubbleTouchSlop) {
                            this.wasDragged = true;
                        }
                        int nextX = Math.max(0, this.startWinX + totalDx);
                        int nextY = Math.max(32, this.startWinY + totalDy);
                        if (FloatingDashboardService.this.floatingRootView != null && FloatingDashboardService.this.windowManager != null && (nextX != FloatingDashboardService.this.overlayLayoutParams.x || nextY != FloatingDashboardService.this.overlayLayoutParams.y)) {
                            FloatingDashboardService.this.overlayLayoutParams.x = nextX;
                            FloatingDashboardService.this.overlayLayoutParams.y = nextY;
                            FloatingDashboardService.this.windowManager.updateViewLayout(FloatingDashboardService.this.floatingRootView, (ViewGroup.LayoutParams)FloatingDashboardService.this.overlayLayoutParams);
                        }
                        return true;
                    }
                    case 1: {
                        if (!this.wasDragged) {
                            goalLogoBubble.setAlpha(1.0f);
                            goalLogoBubble.setVisibility(8);
                            panelRoot.setVisibility(0);
                            if (FloatingDashboardService.this.overlayLayoutParams != null) {
                                FloatingDashboardService.this.overlayLayoutParams.width = currentCanvasW;
                                FloatingDashboardService.this.overlayLayoutParams.height = currentCanvasH;
                            }
                            if (FloatingDashboardService.this.floatingRootView != null && FloatingDashboardService.this.windowManager != null) {
                                FloatingDashboardService.this.windowManager.updateViewLayout(FloatingDashboardService.this.floatingRootView, (ViewGroup.LayoutParams)FloatingDashboardService.this.overlayLayoutParams);
                            }
                        }
                        return true;
                    }
                }
                return false;
            }
        });
        FrameLayout rootWrapper = new FrameLayout((Context)this);
        rootWrapper.addView((View)panelRoot, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(currentCanvasW, currentCanvasH));
        rootWrapper.addView((View)goalLogoBubble, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(bubbleSizePx, bubbleSizePx));
        synchronized (OVERLAY_LOCK) {
            if (sActiveFloatingRootView != null && sActiveWindowManager != null) {
                try {
                    sActiveWindowManager.removeViewImmediate(sActiveFloatingRootView);
                }
                catch (Throwable ignored) {
                    try {
                        sActiveWindowManager.removeView(sActiveFloatingRootView);
                    }
                    catch (Throwable ignored2) {
                    }
                }
                sActiveFloatingRootView = null;
            }
            this.floatingRootView = rootWrapper;
            sActiveFloatingRootView = rootWrapper;
            sActiveWindowManager = this.windowManager;
            try {
                if (this.windowManager != null) {
                    this.windowManager.addView(this.floatingRootView, (ViewGroup.LayoutParams)this.overlayLayoutParams);
                }
            }
            catch (Throwable ignored) {
            }
        }
        // Immediately run the target path file-change testing & initialization system when Float option turns ON
        LocalConfigStateWriter.getInstance().runFloatStartupTargetTestAndApplyAsync(this.getApplicationContext(), this.getFilesDir(), specs);
    }

    private Bitmap createRoundedCenterCropBitmap(Bitmap src, int targetW, int targetH, int cornerRadiusPx) {
        if (src == null || targetW <= 0 || targetH <= 0 || src.getWidth() <= 0 || src.getHeight() <= 0) {
            return null;
        }
        try {
            float scale = Math.max((float)targetW / (float)src.getWidth(), (float)targetH / (float)src.getHeight());
            int scaledW = Math.max(targetW, Math.round((float)src.getWidth() * scale));
            int scaledH = Math.max(targetH, Math.round((float)src.getHeight() * scale));
            Bitmap scaled = Bitmap.createScaledBitmap((Bitmap)src, (int)scaledW, (int)scaledH, (boolean)true);
            int cropX = Math.max(0, (scaledW - targetW) / 2);
            int cropY = Math.max(0, (scaledH - targetH) / 2);
            Bitmap cropped = Bitmap.createBitmap((Bitmap)scaled, (int)cropX, (int)cropY, (int)targetW, (int)targetH);
            Bitmap output = Bitmap.createBitmap((int)targetW, (int)targetH, (Bitmap.Config)Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            Paint paint = new Paint(1);
            BitmapShader shader = new BitmapShader(cropped, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            paint.setShader((Shader)shader);
            android.graphics.RectF rect = new android.graphics.RectF(0.0f, 0.0f, (float)targetW, (float)targetH);
            canvas.drawRoundRect(rect, (float)cornerRadiusPx, (float)cornerRadiusPx, paint);
            return output;
        }
        catch (Throwable t) {
            return null;
        }
    }

    private Bitmap createCircularBitmap(Bitmap src, int sizePx) {
        if (src == null || sizePx <= 0) {
            return null;
        }
        Bitmap scaled = Bitmap.createScaledBitmap((Bitmap)src, (int)sizePx, (int)sizePx, (boolean)true);
        Bitmap output = Bitmap.createBitmap((int)sizePx, (int)sizePx, (Bitmap.Config)Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(1);
        float radius = (float)sizePx / 2.0f;
        BitmapShader shader = new BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        paint.setShader((Shader)shader);
        canvas.drawCircle(radius, radius, radius - (float)this.dpToPx(2), paint);
        Paint borderPaint = new Paint(1);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(-1);
        borderPaint.setStrokeWidth((float)this.dpToPx(2));
        canvas.drawCircle(radius, radius, radius - (float)this.dpToPx(1), borderPaint);
        return output;
    }

    private Drawable createWidgetBackgroundDrawable(Bitmap rawBgBmp, int widthDp, int heightDp, int fillColor, int strokeWidthPx, int strokeColor) {
        int cornerRadiusPx = this.dpToPx(8);
        if (rawBgBmp != null) {
            int targetW = Math.max(this.dpToPx(48), this.dpToPx(Math.max(48, widthDp)));
            int targetH = Math.max(this.dpToPx(32), this.dpToPx(Math.max(32, heightDp)));
            Bitmap cropped = this.createRoundedCenterCropBitmap(rawBgBmp, targetW, targetH, cornerRadiusPx);
            if (cropped != null) {
                if (strokeWidthPx > 0) {
                    Canvas c = new Canvas(cropped);
                    Paint borderPaint = new Paint(1);
                    borderPaint.setStyle(Paint.Style.STROKE);
                    borderPaint.setColor(strokeColor);
                    borderPaint.setStrokeWidth((float)strokeWidthPx);
                    float inset = (float)strokeWidthPx / 2.0f;
                    android.graphics.RectF rect = new android.graphics.RectF(inset, inset, (float)targetW - inset, (float)targetH - inset);
                    c.drawRoundRect(rect, (float)cornerRadiusPx, (float)cornerRadiusPx, borderPaint);
                }
                return new android.graphics.drawable.BitmapDrawable(this.getResources(), cropped);
            }
        }
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fillColor);
        gd.setCornerRadius((float)cornerRadiusPx);
        if (strokeWidthPx > 0) {
            gd.setStroke(strokeWidthPx, strokeColor);
        }
        return gd;
    }

    private View buildDynamicComponentView(final DynamicOverlayRegistry.OverlayItemSpec spec) {
        String type;
        int bgColor = this.parseSafeColor(spec.bgColorHex, Color.parseColor((String)"#2563EB"));
        int txtColor = this.parseSafeColor(spec.textColorHex, -1);
        Bitmap widgetBgBitmap = null;
        if (spec.bgImagePath != null && !spec.bgImagePath.trim().isEmpty()) {
            File bgImgFile = new File(spec.bgImagePath.trim());
            if (bgImgFile.exists() && bgImgFile.isFile()) {
                try {
                    widgetBgBitmap = BitmapFactory.decodeFile((String)bgImgFile.getAbsolutePath());
                } catch (Throwable ignored) {
                }
            }
        }
        final Bitmap resolvedWidgetBgBmp = widgetBgBitmap;
        Drawable itemBg = this.createWidgetBackgroundDrawable(resolvedWidgetBgBmp, spec.widthDp, spec.heightDp, bgColor, this.dpToPx(1), Color.parseColor((String)"#CBD5E1"));
        switch (type = spec.type != null ? spec.type : "BUTTON") {
            case "TOGGLE": {
                LinearLayout row = new LinearLayout((Context)this);
                row.setOrientation(0);
                row.setGravity(16);
                row.setPadding(this.dpToPx(8), this.dpToPx(4), this.dpToPx(8), this.dpToPx(4));
                boolean defaultChecked = "1".equals(spec.currentValue) || "true".equalsIgnoreCase(spec.currentValue);
                boolean hasSelectedSourceFile = spec.customImagePath != null && !spec.customImagePath.trim().isEmpty() && new File(spec.customImagePath.trim()).exists();
                boolean syncedChecked = hasSelectedSourceFile ? defaultChecked : LocalConfigStateWriter.getInstance().detectInitialToggleState(this.getFilesDir(), spec.targetFilePath, spec.offPayloadHex, spec.onPayloadHex, defaultChecked);
                spec.currentValue = syncedChecked ? "1" : "0";
                boolean[] isCheckedState = new boolean[]{syncedChecked};
                LinearLayout textCol = new LinearLayout((Context)this);
                textCol.setOrientation(1);
                LinearLayout.LayoutParams textColLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
                TextView titleTv = new TextView((Context)this);
                titleTv.setText((CharSequence)spec.label);
                titleTv.setTextColor(txtColor);
                titleTv.setTextSize(2, 11.0f);
                titleTv.setTypeface(Typeface.DEFAULT_BOLD);
                titleTv.setSingleLine(true);
                textCol.addView((View)titleTv);
                TextView onOffBadge = new TextView((Context)this);
                onOffBadge.setTextSize(2, 9.0f);
                onOffBadge.setTypeface(Typeface.DEFAULT_BOLD);
                onOffBadge.setTextColor(-1);
                onOffBadge.setPadding(this.dpToPx(5), this.dpToPx(2), this.dpToPx(5), this.dpToPx(2));
                LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(-2, -2);
                badgeLp.leftMargin = this.dpToPx(6);
                badgeLp.rightMargin = this.dpToPx(4);
                Switch toggleSwitch = new Switch((Context)this);
                toggleSwitch.setShowText(false);
                toggleSwitch.setChecked(isCheckedState[0]);
                Runnable updateVisuals = () -> {
                    boolean on = isCheckedState[0];
                    boolean isDefaultWhite = spec.bgColorHex == null || spec.bgColorHex.trim().isEmpty() || "#FFFFFF".equalsIgnoreCase(spec.bgColorHex.trim());
                    int fillCol = on && isDefaultWhite ? Color.parseColor((String)"#ECFDF5") : bgColor;
                    int strokeCol = on ? Color.parseColor((String)"#00C853") : Color.parseColor((String)"#64748B");
                    row.setBackground(this.createWidgetBackgroundDrawable(resolvedWidgetBgBmp, spec.widthDp, spec.heightDp, fillCol, this.dpToPx(2), strokeCol));
                    GradientDrawable badgeBg = new GradientDrawable();
                    badgeBg.setCornerRadius((float)this.dpToPx(4));
                    badgeBg.setColor(on ? Color.parseColor((String)"#00C853") : Color.parseColor((String)"#EF4444"));
                    onOffBadge.setBackground((Drawable)badgeBg);
                    onOffBadge.setText((CharSequence)(on ? "ON" : "OFF"));
                };
                updateVisuals.run();
                toggleSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
                    DynamicOverlayRegistry.OverlayItemSpec latest = DynamicOverlayRegistry.getSpecById(spec.id, spec);
                    isCheckedState[0] = isChecked;
                    spec.currentValue = latest.currentValue = isChecked ? "1" : "0";
                    updateVisuals.run();
                    if (isChecked) {
                        SoundTriggerPlayer.playSoundTrigger((Context)this, (View)btn, latest.soundTrigger, latest.customSoundPath);
                    } else {
                        SoundTriggerPlayer.playSoundTrigger((Context)this, (View)btn, latest.offSoundTrigger, latest.offCustomSoundPath);
                    }
                    String payload = isChecked ? latest.onPayloadHex : latest.offPayloadHex;
                    LocalConfigStateWriter.getInstance().applyWidgetPatchAsync(this.getFilesDir(), "widget_" + latest.id, "TOGGLE", latest.targetFilePath, latest.byteOffsetHex, latest.offPayloadHex, latest.onPayloadHex, payload, isChecked, latest.label, latest.customImagePath);
                });
                View.OnClickListener rowClick = v -> toggleSwitch.setChecked(!toggleSwitch.isChecked());
                onOffBadge.setOnClickListener(rowClick);
                row.setOnClickListener(rowClick);
                row.addView((View)textCol, (ViewGroup.LayoutParams)textColLp);
                row.addView((View)onOffBadge, (ViewGroup.LayoutParams)badgeLp);
                row.addView((View)toggleSwitch);
                return row;
            }
            case "SLIDER": {
                LinearLayout box = new LinearLayout((Context)this);
                box.setOrientation(1);
                box.setGravity(16);
                box.setBackground(itemBg);
                box.setPadding(this.dpToPx(8), this.dpToPx(4), this.dpToPx(8), this.dpToPx(4));
                final int maxVal = Math.max(1, spec.sliderMax);
                final TextView labelTv = new TextView((Context)this);
                labelTv.setText((CharSequence)(spec.label + " (0-" + maxVal + "): " + spec.currentValue));
                labelTv.setTextColor(txtColor);
                labelTv.setTypeface(Typeface.DEFAULT_BOLD);
                labelTv.setTextSize(2, 11.0f);
                SeekBar seekBar = new SeekBar((Context)this);
                seekBar.setMax(maxVal);
                int initProgress = 50;
                try {
                    initProgress = Integer.parseInt(spec.currentValue);
                }
                catch (Exception textColLp) {
                    // empty catch block
                }
                seekBar.setProgress(initProgress);
                seekBar.setOnTouchListener((v, event) -> {
                    if (v.getParent() != null) {
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return false;
                });
                seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){

                    public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                        DynamicOverlayRegistry.OverlayItemSpec latest = DynamicOverlayRegistry.getSpecById(spec.id, spec);
                        labelTv.setText((CharSequence)(latest.label + " (0-" + maxVal + "): " + progress));
                        spec.currentValue = latest.currentValue = String.valueOf(progress);
                        if (fromUser) {
                            if (progress == 0) {
                                SoundTriggerPlayer.playSoundTrigger((Context)FloatingDashboardService.this, (View)sb, latest.offSoundTrigger, latest.offCustomSoundPath);
                            }
                            LocalConfigStateWriter.getInstance().applyWidgetPatchAsync(FloatingDashboardService.this.getFilesDir(), "widget_" + latest.id, "SLIDER", latest.targetFilePath, latest.byteOffsetHex, latest.offPayloadHex, latest.onPayloadHex, String.valueOf(progress), progress > 0, latest.label, latest.customImagePath);
                        }
                    }

                    public void onStartTrackingTouch(SeekBar sb) {
                        SoundTriggerPlayer.playSoundTrigger((Context)FloatingDashboardService.this, (View)sb, spec.soundTrigger, spec.customSoundPath);
                    }

                    public void onStopTrackingTouch(SeekBar sb) {
                        if (sb.getProgress() == 0) {
                            SoundTriggerPlayer.playSoundTrigger((Context)FloatingDashboardService.this, (View)sb, spec.offSoundTrigger, spec.offCustomSoundPath);
                        }
                    }
                });
                box.addView((View)labelTv);
                box.addView((View)seekBar);
                return box;
            }
            case "INPUT": {
                EditText et = new EditText((Context)this);
                et.setHint((CharSequence)spec.label);
                et.setText((CharSequence)("0".equals(spec.currentValue) ? "" : spec.currentValue));
                et.setTextColor(txtColor);
                et.setHintTextColor(-3355444);
                et.setTextSize(2, 12.0f);
                et.setSingleLine(true);
                et.setBackground(itemBg);
                et.setPadding(this.dpToPx(10), this.dpToPx(4), this.dpToPx(10), this.dpToPx(4));
                et.setOnClickListener(v -> {
                    this.setOverlayFocusable(true);
                    SoundTriggerPlayer.playSoundTrigger((Context)this, v, spec.soundTrigger, spec.customSoundPath);
                });
                et.addTextChangedListener(new TextWatcher(){

                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                    }

                    public void afterTextChanged(Editable s) {
                        String val;
                        spec.currentValue = val = s != null ? s.toString() : "";
                        boolean isActive = !val.trim().isEmpty() && !"0".equals(val.trim()) && !"false".equalsIgnoreCase(val.trim());
                        LocalConfigStateWriter.getInstance().applyWidgetPatchAsync(FloatingDashboardService.this.getFilesDir(), "widget_" + spec.id, "INPUT", spec.targetFilePath, spec.byteOffsetHex, spec.offPayloadHex, spec.onPayloadHex, val, isActive, spec.label);
                    }
                });
                et.setOnEditorActionListener((v, actionId, event) -> {
                    boolean isOff;
                    String val = v.getText().toString();
                    boolean bl = isOff = val.trim().isEmpty() || "0".equals(val.trim()) || "false".equalsIgnoreCase(val.trim());
                    if (isOff) {
                        SoundTriggerPlayer.playSoundTrigger((Context)this, (View)v, spec.offSoundTrigger, spec.offCustomSoundPath);
                    } else {
                        SoundTriggerPlayer.playSoundTrigger((Context)this, (View)v, spec.soundTrigger, spec.customSoundPath);
                    }
                    LocalConfigStateWriter.getInstance().applyWidgetPatchAsync(this.getFilesDir(), "widget_" + spec.id, "INPUT", spec.targetFilePath, spec.byteOffsetHex, spec.offPayloadHex, spec.onPayloadHex, val, !isOff, spec.label);
                    this.setOverlayFocusable(false);
                    return true;
                });
                return et;
            }
            case "LINK": {
                LinearLayout linkRow = new LinearLayout((Context)this);
                linkRow.setOrientation(0);
                linkRow.setGravity(16);
                linkRow.setPadding(this.dpToPx(10), this.dpToPx(4), this.dpToPx(10), this.dpToPx(4));
                linkRow.setBackground(this.createWidgetBackgroundDrawable(resolvedWidgetBgBmp, spec.widthDp, spec.heightDp, bgColor, this.dpToPx(2), Color.parseColor((String)"#38BDF8")));
                TextView iconTv = new TextView((Context)this);
                iconTv.setText((CharSequence)"\ud83c\udf10");
                iconTv.setTextSize(2, 12.0f);
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(-2, -2);
                iconLp.rightMargin = this.dpToPx(6);
                TextView labelTv = new TextView((Context)this);
                labelTv.setText((CharSequence)spec.label);
                labelTv.setTextColor(txtColor);
                labelTv.setTextSize(2, 12.0f);
                labelTv.setTypeface(Typeface.DEFAULT_BOLD);
                labelTv.setSingleLine(true);
                LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
                TextView openBadge = new TextView((Context)this);
                openBadge.setText((CharSequence)"OPEN \u2197");
                openBadge.setTextColor(-1);
                openBadge.setTextSize(2, 9.0f);
                openBadge.setTypeface(Typeface.DEFAULT_BOLD);
                openBadge.setPadding(this.dpToPx(7), this.dpToPx(2), this.dpToPx(7), this.dpToPx(2));
                GradientDrawable badgeBg = new GradientDrawable();
                badgeBg.setColor(Color.parseColor((String)"#0288D1"));
                badgeBg.setCornerRadius((float)this.dpToPx(99));
                openBadge.setBackground((Drawable)badgeBg);
                View.OnClickListener openClick = v -> {
                    SoundTriggerPlayer.playSoundTrigger((Context)this, (View)linkRow, spec.soundTrigger, spec.customSoundPath);
                    this.openLinkUrl(spec.linkUrl != null && !spec.linkUrl.trim().isEmpty() ? spec.linkUrl : spec.onPayloadHex);
                };
                linkRow.setOnClickListener(openClick);
                openBadge.setOnClickListener(openClick);
                linkRow.addView((View)iconTv, (ViewGroup.LayoutParams)iconLp);
                linkRow.addView((View)labelTv, (ViewGroup.LayoutParams)labelLp);
                linkRow.addView((View)openBadge);
                return linkRow;
            }
            case "IMAGE": {
                Bitmap bmp;
                File imgFile;
                ImageView iv = new ImageView((Context)this);
                iv.setBackground(itemBg);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                String imgPathToUse = spec.customImagePath != null && !spec.customImagePath.trim().isEmpty() ? spec.customImagePath.trim() : (spec.bgImagePath != null ? spec.bgImagePath.trim() : "");
                if (!imgPathToUse.isEmpty() && (imgFile = new File(imgPathToUse)).exists() && (bmp = BitmapFactory.decodeFile((String)imgFile.getAbsolutePath())) != null) {
                    iv.setImageBitmap(bmp);
                }
                boolean[] isImgOn = new boolean[]{"1".equals(spec.currentValue) || "true".equalsIgnoreCase(spec.currentValue)};
                iv.setOnClickListener(v -> {
                    isImgOn[0] = !isImgOn[0];
                    String string2 = spec.currentValue = isImgOn[0] ? "1" : "0";
                    if (isImgOn[0]) {
                        SoundTriggerPlayer.playSoundTrigger((Context)this, v, spec.soundTrigger, spec.customSoundPath);
                    } else {
                        SoundTriggerPlayer.playSoundTrigger((Context)this, v, spec.offSoundTrigger, spec.offCustomSoundPath);
                    }
                    String payload = isImgOn[0] ? spec.onPayloadHex : spec.offPayloadHex;
                    LocalConfigStateWriter.getInstance().applyWidgetPatchAsync(this.getFilesDir(), "widget_" + spec.id, "IMAGE", spec.targetFilePath, spec.byteOffsetHex, spec.offPayloadHex, spec.onPayloadHex, payload, isImgOn[0], spec.label);
                });
                return iv;
            }
            case "TEXT": {
                TextView tv = new TextView((Context)this);
                tv.setText((CharSequence)spec.label);
                tv.setTextColor(txtColor);
                tv.setTextSize(2, 12.0f);
                tv.setTypeface(Typeface.DEFAULT_BOLD);
                tv.setGravity(16);
                tv.setPadding(this.dpToPx(8), this.dpToPx(4), this.dpToPx(8), this.dpToPx(4));
                tv.setBackground(itemBg);
                boolean[] isTxtOn = new boolean[]{"1".equals(spec.currentValue) || "true".equalsIgnoreCase(spec.currentValue)};
                tv.setOnClickListener(v -> {
                    isTxtOn[0] = !isTxtOn[0];
                    String string2 = spec.currentValue = isTxtOn[0] ? "1" : "0";
                    if (isTxtOn[0]) {
                        SoundTriggerPlayer.playSoundTrigger((Context)this, v, spec.soundTrigger, spec.customSoundPath);
                    } else {
                        SoundTriggerPlayer.playSoundTrigger((Context)this, v, spec.offSoundTrigger, spec.offCustomSoundPath);
                    }
                    String payload = isTxtOn[0] ? spec.onPayloadHex : spec.offPayloadHex;
                    LocalConfigStateWriter.getInstance().applyWidgetPatchAsync(this.getFilesDir(), "widget_" + spec.id, "TEXT", spec.targetFilePath, spec.byteOffsetHex, spec.offPayloadHex, spec.onPayloadHex, payload, isTxtOn[0], spec.label);
                });
                return tv;
            }
        }
        boolean defaultBtnOn = "1".equals(spec.currentValue) || "true".equalsIgnoreCase(spec.currentValue);
        boolean hasBtnSourceFile = spec.customImagePath != null && !spec.customImagePath.trim().isEmpty() && new File(spec.customImagePath.trim()).exists();
        boolean syncedBtnOn = hasBtnSourceFile ? defaultBtnOn : LocalConfigStateWriter.getInstance().detectInitialToggleState(this.getFilesDir(), spec.targetFilePath, spec.offPayloadHex, spec.onPayloadHex, defaultBtnOn);
        spec.currentValue = syncedBtnOn ? "1" : "0";
        boolean[] isBtnOn = new boolean[]{syncedBtnOn};
        LinearLayout btnRow = new LinearLayout((Context)this);
        btnRow.setOrientation(0);
        btnRow.setGravity(16);
        btnRow.setPadding(this.dpToPx(10), this.dpToPx(4), this.dpToPx(10), this.dpToPx(4));
        TextView labelTv = new TextView((Context)this);
        labelTv.setText((CharSequence)spec.label);
        labelTv.setTextSize(2, 12.0f);
        labelTv.setTypeface(Typeface.DEFAULT_BOLD);
        labelTv.setSingleLine(true);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        TextView pillBadge = new TextView((Context)this);
        pillBadge.setTextColor(-1);
        pillBadge.setTextSize(2, 10.0f);
        pillBadge.setTypeface(Typeface.DEFAULT_BOLD);
        pillBadge.setPadding(this.dpToPx(8), this.dpToPx(2), this.dpToPx(8), this.dpToPx(2));
        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(-2, -2);
        pillLp.leftMargin = this.dpToPx(6);
        Runnable updateBtnVisuals = () -> {
            boolean on = isBtnOn[0];
            boolean isDefaultBlue = spec.bgColorHex == null || spec.bgColorHex.trim().isEmpty() || "#2563EB".equalsIgnoreCase(spec.bgColorHex.trim());
            int fillCol = on && isDefaultBlue ? Color.parseColor((String)"#00C853") : bgColor;
            int strokeCol = on ? Color.parseColor((String)"#00C853") : Color.parseColor((String)"#475569");
            btnRow.setBackground(this.createWidgetBackgroundDrawable(resolvedWidgetBgBmp, spec.widthDp, spec.heightDp, fillCol, this.dpToPx(2), strokeCol));
            labelTv.setTextColor(on && isDefaultBlue && resolvedWidgetBgBmp == null ? -1 : txtColor);
            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setColor(on ? Color.parseColor((String)"#047857") : Color.parseColor((String)"#EF4444"));
            pillBg.setCornerRadius((float)this.dpToPx(99));
            pillBg.setStroke(this.dpToPx(1), -1);
            pillBadge.setBackground((Drawable)pillBg);
            pillBadge.setText((CharSequence)(on ? "ON" : "OFF"));
        };
        updateBtnVisuals.run();
        View.OnClickListener clickListener = v -> {
            DynamicOverlayRegistry.OverlayItemSpec latest = DynamicOverlayRegistry.getSpecById(spec.id, spec);
            isBtnOn[0] = !isBtnOn[0];
            spec.currentValue = latest.currentValue = isBtnOn[0] ? "1" : "0";
            updateBtnVisuals.run();
            if (isBtnOn[0]) {
                SoundTriggerPlayer.playSoundTrigger((Context)this, (View)btnRow, latest.soundTrigger, latest.customSoundPath);
            } else {
                SoundTriggerPlayer.playSoundTrigger((Context)this, (View)btnRow, latest.offSoundTrigger, latest.offCustomSoundPath);
            }
            if (latest.linkUrl != null && !latest.linkUrl.trim().isEmpty() && isBtnOn[0]) {
                this.openLinkUrl(latest.linkUrl);
            }
            String payload = isBtnOn[0] ? latest.onPayloadHex : latest.offPayloadHex;
            LocalConfigStateWriter.getInstance().applyWidgetPatchAsync(this.getFilesDir(), "widget_" + latest.id, "BUTTON", latest.targetFilePath, latest.byteOffsetHex, latest.offPayloadHex, latest.onPayloadHex, payload, isBtnOn[0], latest.label, latest.customImagePath);
        };
        btnRow.setOnClickListener(clickListener);
        pillBadge.setOnClickListener(clickListener);
        btnRow.addView((View)labelTv, (ViewGroup.LayoutParams)labelLp);
        btnRow.addView((View)pillBadge, (ViewGroup.LayoutParams)pillLp);
        return btnRow;
    }

    private void openLinkUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return;
        }
        String url = rawUrl.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        try {
            Intent browserIntent = new Intent("android.intent.action.VIEW", Uri.parse((String)url));
            browserIntent.addFlags(0x10000000);
            this.startActivity(browserIntent);
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private void setOverlayFocusable(boolean focusable) {
        boolean currentlyNotFocusable;
        if (this.floatingRootView == null || this.windowManager == null || this.overlayLayoutParams == null) {
            return;
        }
        boolean bl = currentlyNotFocusable = (this.overlayLayoutParams.flags & 8) != 0;
        if (focusable && currentlyNotFocusable) {
            this.overlayLayoutParams.flags &= 0xFFFFFFF7;
            this.windowManager.updateViewLayout(this.floatingRootView, (ViewGroup.LayoutParams)this.overlayLayoutParams);
        } else if (!focusable && !currentlyNotFocusable) {
            this.overlayLayoutParams.flags |= 8;
            this.windowManager.updateViewLayout(this.floatingRootView, (ViewGroup.LayoutParams)this.overlayLayoutParams);
        }
    }

    private int dpToPx(int dp) {
        return Math.round((float)dp * this.getResources().getDisplayMetrics().density);
    }

    private int parseSafeColor(String hex, int fallback) {
        if (hex == null || hex.trim().isEmpty()) {
            return fallback;
        }
        try {
            String formatted = hex.trim().startsWith("#") ? hex.trim() : "#" + hex.trim();
            return Color.parseColor((String)formatted);
        }
        catch (Exception e) {
            return fallback;
        }
    }

    private void removeSystemOverlayWindow() {
        synchronized (OVERLAY_LOCK) {
            if (this.floatingRootView != null && this.windowManager != null) {
                try {
                    this.windowManager.removeViewImmediate(this.floatingRootView);
                }
                catch (Throwable ignored) {
                    try {
                        this.windowManager.removeView(this.floatingRootView);
                    }
                    catch (Throwable ignored2) {
                    }
                }
                if (sActiveFloatingRootView == this.floatingRootView) {
                    sActiveFloatingRootView = null;
                }
                this.floatingRootView = null;
            }
            if (sActiveFloatingRootView != null && sActiveWindowManager != null) {
                try {
                    sActiveWindowManager.removeViewImmediate(sActiveFloatingRootView);
                }
                catch (Throwable ignored) {
                    try {
                        sActiveWindowManager.removeView(sActiveFloatingRootView);
                    }
                    catch (Throwable ignored2) {
                    }
                }
                sActiveFloatingRootView = null;
            }
            this.dismissFloatingErrorDialog();
        }
    }

    private void refreshShizukuHeaderBadge() {
        TextView badge = this.shizukuHeaderBadgeTv;
        if (badge == null) {
            return;
        }
        Context ctx = this.getApplicationContext();
        boolean ready = ShizukuPrivilegeBridge.isShizukuReady(ctx);
        boolean runningShz = ShizukuPrivilegeBridge.isShizukuRunning(ctx);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius((float)this.dpToPx(6));
        if (ready) {
            badge.setText((CharSequence)"SHZ \u2713");
            badge.setTextColor(-1);
            bg.setColor(Color.parseColor((String)"#059669"));
            bg.setStroke(this.dpToPx(1), Color.parseColor((String)"#6EE7B7"));
        } else if (runningShz) {
            badge.setText((CharSequence)"SHZ !");
            badge.setTextColor(-1);
            bg.setColor(Color.parseColor((String)"#D97706"));
            bg.setStroke(this.dpToPx(1), Color.parseColor((String)"#FCD34D"));
        } else {
            badge.setText((CharSequence)"SHZ");
            badge.setTextColor(-1);
            bg.setColor(Color.parseColor((String)"#334155"));
            bg.setStroke(this.dpToPx(1), Color.parseColor((String)"#94A3B8"));
        }
        badge.setBackground((Drawable)bg);
    }

    private void dismissFloatingErrorDialog() {
        synchronized (OVERLAY_LOCK) {
            if (sActiveErrorDialogView != null && this.windowManager != null) {
                try {
                    this.windowManager.removeViewImmediate(sActiveErrorDialogView);
                } catch (Throwable ignored) {
                    try {
                        this.windowManager.removeView(sActiveErrorDialogView);
                    } catch (Throwable ignored2) {
                    }
                }
                sActiveErrorDialogView = null;
            }
        }
    }

    private void showFloatingErrorDiagnosticDialog(final LocalConfigStateWriter.WriteDiagnosticReport report) {
        if (report == null || this.windowManager == null || !Settings.canDrawOverlays((Context)this)) {
            return;
        }
        this.dismissFloatingErrorDialog();
        final Context appCtx = this.getApplicationContext();
        ShizukuPrivilegeBridge.probeShizukuBinder(appCtx);
        final boolean shzInstalled = ShizukuPrivilegeBridge.isShizukuInstalled(appCtx);
        final boolean shzRunning = ShizukuPrivilegeBridge.isShizukuRunning(appCtx);
        final boolean shzReady = ShizukuPrivilegeBridge.isShizukuReady(appCtx);
        final boolean hasAllFiles = LocalConfigStateWriter.hasStoragePermissionGranted(appCtx);

        int overlayType = Build.VERSION.SDK_INT >= 26 ? 2038 : 2002;
        int dialogWidthPx = Math.min(this.getResources().getDisplayMetrics().widthPixels - this.dpToPx(28), this.dpToPx(340));
        WindowManager.LayoutParams dialogLp = new WindowManager.LayoutParams(
                dialogWidthPx,
                -2,
                overlayType,
                8,
                -3
        );
        dialogLp.gravity = 17;

        LinearLayout card = new LinearLayout((Context)this);
        card.setOrientation(1);
        int pad = this.dpToPx(14);
        card.setPadding(pad, pad, pad, pad);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor((String)"#0F172A"));
        cardBg.setCornerRadius((float)this.dpToPx(16));
        cardBg.setStroke(this.dpToPx(2), Color.parseColor((String)"#EF4444"));
        card.setBackground((Drawable)cardBg);

        // Header row
        LinearLayout headerRow = new LinearLayout((Context)this);
        headerRow.setOrientation(0);
        headerRow.setGravity(16);
        TextView errBadge = new TextView((Context)this);
        errBadge.setText((CharSequence)(report.isRestrictedAndroidPath ? "ANDROID " + Build.VERSION.SDK_INT + " RESTRICTED PATH" : "FILE CHANGE FAILED"));
        errBadge.setTextColor(-1);
        errBadge.setTextSize(2, 9.5f);
        errBadge.setTypeface(Typeface.DEFAULT_BOLD);
        errBadge.setPadding(this.dpToPx(8), this.dpToPx(3), this.dpToPx(8), this.dpToPx(3));
        GradientDrawable errBadgeBg = new GradientDrawable();
        errBadgeBg.setColor(Color.parseColor((String)"#DC2626"));
        errBadgeBg.setCornerRadius((float)this.dpToPx(6));
        errBadge.setBackground((Drawable)errBadgeBg);
        headerRow.addView((View)errBadge, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-2, -2));

        View spacer = new View((Context)this);
        headerRow.addView(spacer, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, 1, 1.0f));

        TextView closeTv = new TextView((Context)this);
        closeTv.setText((CharSequence)"\u2715");
        closeTv.setTextColor(Color.parseColor((String)"#94A3B8"));
        closeTv.setTextSize(2, 13.0f);
        closeTv.setTypeface(Typeface.DEFAULT_BOLD);
        closeTv.setPadding(this.dpToPx(6), this.dpToPx(2), this.dpToPx(6), this.dpToPx(2));
        closeTv.setOnClickListener(v -> this.dismissFloatingErrorDialog());
        headerRow.addView((View)closeTv, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-2, -2));
        card.addView((View)headerRow, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));

        // Title
        TextView titleTv = new TextView((Context)this);
        titleTv.setText((CharSequence)(report.whyFailedTitle + " (" + report.componentLabel + ")"));
        titleTv.setTextColor(-1);
        titleTv.setTextSize(2, 13.0f);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.topMargin = this.dpToPx(8);
        card.addView((View)titleTv, (ViewGroup.LayoutParams)titleLp);

        // Target Path box
        TextView pathTv = new TextView((Context)this);
        pathTv.setText((CharSequence)("Target Path:\n" + report.targetFilePath));
        pathTv.setTextColor(Color.parseColor((String)"#38BDF8"));
        pathTv.setTextSize(2, 10.0f);
        pathTv.setTypeface(Typeface.MONOSPACE);
        pathTv.setPadding(this.dpToPx(8), this.dpToPx(6), this.dpToPx(8), this.dpToPx(6));
        GradientDrawable pathBg = new GradientDrawable();
        pathBg.setColor(Color.parseColor((String)"#1E293B"));
        pathBg.setCornerRadius((float)this.dpToPx(8));
        pathBg.setStroke(this.dpToPx(1), Color.parseColor((String)"#334155"));
        pathTv.setBackground((Drawable)pathBg);
        LinearLayout.LayoutParams pathLp = new LinearLayout.LayoutParams(-1, -2);
        pathLp.topMargin = this.dpToPx(8);
        card.addView((View)pathTv, (ViewGroup.LayoutParams)pathLp);

        // Scrollable detail explanation
        ScrollView detailScroll = new ScrollView((Context)this);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, -2);
        scrollLp.topMargin = this.dpToPx(8);
        TextView detailTv = new TextView((Context)this);
        String fullExplanation = report.whyFailedDetail
                + "\n\nOS Kernel Error: " + report.rawKernelError
                + "\nShizuku Status: " + ShizukuPrivilegeBridge.getStatusSummary(appCtx);
        detailTv.setText((CharSequence)fullExplanation);
        detailTv.setTextColor(Color.parseColor((String)"#E2E8F0"));
        detailTv.setTextSize(2, 10.5f);
        detailScroll.addView((View)detailTv, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -2));
        card.addView((View)detailScroll, (ViewGroup.LayoutParams)scrollLp);

        // Shizuku Primary Action Button (Always available for restricted paths or permission errors)
        TextView shizukuActionBtn = new TextView((Context)this);
        String shzBtnLabel;
        if (shzReady) {
            shzBtnLabel = "\u26a1 Retry Target File Change with Shizuku";
        } else if (shzRunning) {
            shzBtnLabel = "\ud83d\udee1\ufe0f Authorize Shizuku & Fix Restricted Path";
        } else if (shzInstalled) {
            shzBtnLabel = "\ud83d\ude80 Open Shizuku App (Start Service to Fix)";
        } else {
            shzBtnLabel = "\ud83d\udee1\ufe0f Use Shizuku to Fix Android 15 Restricted Path";
        }
        shizukuActionBtn.setText((CharSequence)shzBtnLabel);
        shizukuActionBtn.setTextColor(-1);
        shizukuActionBtn.setTextSize(2, 11.0f);
        shizukuActionBtn.setTypeface(Typeface.DEFAULT_BOLD);
        shizukuActionBtn.setGravity(17);
        shizukuActionBtn.setPadding(this.dpToPx(10), this.dpToPx(9), this.dpToPx(10), this.dpToPx(9));
        GradientDrawable shzBtnBg = new GradientDrawable();
        shzBtnBg.setColor(Color.parseColor((String)"#2563EB"));
        shzBtnBg.setCornerRadius((float)this.dpToPx(8));
        shzBtnBg.setStroke(this.dpToPx(1), Color.parseColor((String)"#60A5FA"));
        shizukuActionBtn.setBackground((Drawable)shzBtnBg);
        LinearLayout.LayoutParams shzBtnLp = new LinearLayout.LayoutParams(-1, -2);
        shzBtnLp.topMargin = this.dpToPx(10);
        shizukuActionBtn.setOnClickListener(v -> {
            ShizukuPrivilegeBridge.probeShizukuBinder(appCtx);
            if (ShizukuPrivilegeBridge.isShizukuReady(appCtx)) {
                this.dismissFloatingErrorDialog();
                if (report.retryAction != null) {
                    report.retryAction.run();
                } else {
                    LocalConfigStateWriter.getInstance().runFloatStartupTargetTestAndApplyAsync(appCtx, this.getFilesDir(), DynamicOverlayRegistry.getActiveItems());
                }
            } else if (ShizukuPrivilegeBridge.isShizukuRunning(appCtx)) {
                ShizukuPrivilegeBridge.requestShizukuPermission(appCtx, 9402);
                Toast.makeText((Context)this, (CharSequence)"Allow Shizuku permission prompt, then tap Retry.", (int)1).show();
            } else {
                boolean opened = ShizukuPrivilegeBridge.openOrLaunchShizukuManager(appCtx);
                if (!opened) {
                    Toast.makeText((Context)this, (CharSequence)"Install & start Shizuku (moe.shizuku.privileged.api) via Wireless Debugging to unlock Android 15 restricted paths.", (int)1).show();
                }
            }
            this.refreshShizukuHeaderBadge();
        });
        card.addView((View)shizukuActionBtn, (ViewGroup.LayoutParams)shzBtnLp);

        // If All Files Access is not granted, also show button to grant it
        if (!hasAllFiles) {
            TextView storageBtn = new TextView((Context)this);
            storageBtn.setText((CharSequence)"Grant All Files Access Permission");
            storageBtn.setTextColor(-1);
            storageBtn.setTextSize(2, 10.5f);
            storageBtn.setTypeface(Typeface.DEFAULT_BOLD);
            storageBtn.setGravity(17);
            storageBtn.setPadding(this.dpToPx(10), this.dpToPx(8), this.dpToPx(10), this.dpToPx(8));
            GradientDrawable stBg = new GradientDrawable();
            stBg.setColor(Color.parseColor((String)"#059669"));
            stBg.setCornerRadius((float)this.dpToPx(8));
            storageBtn.setBackground((Drawable)stBg);
            LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(-1, -2);
            stLp.topMargin = this.dpToPx(6);
            storageBtn.setOnClickListener(v -> {
                this.dismissFloatingErrorDialog();
                LocalConfigStateWriter.requestStoragePermission(appCtx);
            });
            card.addView((View)storageBtn, (ViewGroup.LayoutParams)stLp);
        }

        // Bottom row: Retry & Dismiss
        LinearLayout bottomRow = new LinearLayout((Context)this);
        bottomRow.setOrientation(0);
        LinearLayout.LayoutParams bottomLp = new LinearLayout.LayoutParams(-1, -2);
        bottomLp.topMargin = this.dpToPx(8);

        TextView retryBtn = new TextView((Context)this);
        retryBtn.setText((CharSequence)"Retry Test Write");
        retryBtn.setTextColor(-1);
        retryBtn.setTextSize(2, 10.5f);
        retryBtn.setTypeface(Typeface.DEFAULT_BOLD);
        retryBtn.setGravity(17);
        retryBtn.setPadding(this.dpToPx(8), this.dpToPx(8), this.dpToPx(8), this.dpToPx(8));
        GradientDrawable retryBg = new GradientDrawable();
        retryBg.setColor(Color.parseColor((String)"#334155"));
        retryBg.setCornerRadius((float)this.dpToPx(8));
        retryBtn.setBackground((Drawable)retryBg);
        LinearLayout.LayoutParams retryLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        retryLp.rightMargin = this.dpToPx(6);
        retryBtn.setOnClickListener(v -> {
            this.dismissFloatingErrorDialog();
            ShizukuPrivilegeBridge.probeShizukuBinder(appCtx);
            if (report.retryAction != null) {
                report.retryAction.run();
            } else {
                LocalConfigStateWriter.getInstance().runFloatStartupTargetTestAndApplyAsync(appCtx, this.getFilesDir(), DynamicOverlayRegistry.getActiveItems());
            }
        });
        bottomRow.addView((View)retryBtn, (ViewGroup.LayoutParams)retryLp);

        TextView closeBtn = new TextView((Context)this);
        closeBtn.setText((CharSequence)"Close");
        closeBtn.setTextColor(Color.parseColor((String)"#CBD5E1"));
        closeBtn.setTextSize(2, 10.5f);
        closeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        closeBtn.setGravity(17);
        closeBtn.setPadding(this.dpToPx(8), this.dpToPx(8), this.dpToPx(8), this.dpToPx(8));
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(Color.parseColor((String)"#1E293B"));
        closeBg.setCornerRadius((float)this.dpToPx(8));
        closeBg.setStroke(this.dpToPx(1), Color.parseColor((String)"#475569"));
        closeBtn.setBackground((Drawable)closeBg);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(0, -2, 0.7f);
        closeBtn.setOnClickListener(v -> this.dismissFloatingErrorDialog());
        bottomRow.addView((View)closeBtn, (ViewGroup.LayoutParams)closeLp);

        card.addView((View)bottomRow, (ViewGroup.LayoutParams)bottomLp);

        synchronized (OVERLAY_LOCK) {
            sActiveErrorDialogView = card;
            try {
                this.windowManager.addView((View)card, (ViewGroup.LayoutParams)dialogLp);
            } catch (Throwable ignored) {
                sActiveErrorDialogView = null;
            }
        }
    }

    public void onDestroy() {
        running = false;
        LocalConfigStateWriter.getInstance().removeListener(this.floatingWriteListener);
        ShizukuPrivilegeBridge.removeListener(this.shizukuStateListener);
        this.removeSystemOverlayWindow();
        super.onDestroy();
    }

    @Nullable
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, (CharSequence)this.getString(R.string.service_notification_channel), 2);
            NotificationManager manager = (NotificationManager)this.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildForegroundNotification() {
        Intent launchIntent = new Intent((Context)this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity((Context)this, (int)0, (Intent)launchIntent, (int)0xC000000);
        return new NotificationCompat.Builder((Context)this, CHANNEL_ID).setSmallIcon(R.mipmap.ic_launcher).setContentTitle((CharSequence)this.getString(R.string.service_notification_title)).setContentText((CharSequence)this.getString(R.string.service_notification_text)).setContentIntent(pendingIntent).setOngoing(true).build();
    }

    private static final class OverflowOnlyScrollLayout
    extends FrameLayout {
        private final OverScroller scroller;
        private final int touchSlop;
        private final int minFlingVelocity;
        private final int maxFlingVelocity;
        private VelocityTracker velocityTracker;
        private float downRawY;
        private float lastRawY;
        private boolean isBeingDragged = false;

        OverflowOnlyScrollLayout(Context context) {
            super(context);
            this.scroller = new OverScroller(context);
            ViewConfiguration vc = ViewConfiguration.get((Context)context);
            this.touchSlop = vc.getScaledTouchSlop();
            this.minFlingVelocity = vc.getScaledMinimumFlingVelocity();
            this.maxFlingVelocity = vc.getScaledMaximumFlingVelocity();
            this.setOverScrollMode(2);
            this.setClipChildren(true);
            this.setClipToPadding(true);
        }

        private int getMaxScrollRange() {
            if (this.getChildCount() == 0) {
                return 0;
            }
            View child = this.getChildAt(0);
            return Math.max(0, child.getHeight() - this.getHeight());
        }

        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (this.getChildCount() > 0) {
                View child = this.getChildAt(0);
                int widthSpec = View.MeasureSpec.makeMeasureSpec((int)this.getMeasuredWidth(), (int)0x40000000);
                int heightSpec = View.MeasureSpec.makeMeasureSpec((int)0, (int)0);
                child.measure(widthSpec, heightSpec);
            }
        }

        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int maxRange;
            if (this.getChildCount() > 0) {
                View child = this.getChildAt(0);
                child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
            }
            if ((maxRange = this.getMaxScrollRange()) <= 0) {
                if (this.getScrollY() != 0) {
                    this.scrollTo(0, 0);
                }
            } else if (this.getScrollY() > maxRange) {
                this.scrollTo(0, maxRange);
            }
        }

        public void requestChildFocus(View child, View focused) {
            super.requestChildFocus(child, focused);
        }

        public boolean onInterceptTouchEvent(MotionEvent ev) {
            int maxRange = this.getMaxScrollRange();
            if (maxRange <= 0) {
                this.isBeingDragged = false;
                if (this.getScrollY() != 0) {
                    this.scrollTo(0, 0);
                }
                return false;
            }
            int action = ev.getActionMasked();
            if (action == 3 || action == 1) {
                this.isBeingDragged = false;
                this.recycleVelocityTracker();
                return false;
            }
            if (action != 0 && this.isBeingDragged) {
                return true;
            }
            switch (action) {
                case 0: {
                    this.lastRawY = this.downRawY = ev.getRawY();
                    if (!this.scroller.isFinished()) {
                        this.scroller.abortAnimation();
                    }
                    this.isBeingDragged = false;
                    this.initOrResetVelocityTracker();
                    this.velocityTracker.addMovement(ev);
                    break;
                }
                case 2: {
                    float rawY = ev.getRawY();
                    float yDiff = Math.abs(rawY - this.downRawY);
                    if (!(yDiff > (float)this.touchSlop)) break;
                    this.isBeingDragged = true;
                    this.lastRawY = rawY;
                    this.initVelocityTrackerIfNotExists();
                    this.velocityTracker.addMovement(ev);
                    if (this.getParent() == null) break;
                    this.getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                }
            }
            return this.isBeingDragged;
        }

        public boolean onTouchEvent(MotionEvent ev) {
            int maxRange = this.getMaxScrollRange();
            if (maxRange <= 0) {
                this.isBeingDragged = false;
                if (this.getScrollY() != 0) {
                    this.scrollTo(0, 0);
                }
                return false;
            }
            this.initVelocityTrackerIfNotExists();
            this.velocityTracker.addMovement(ev);
            switch (ev.getActionMasked()) {
                case 0: {
                    if (!this.scroller.isFinished()) {
                        this.scroller.abortAnimation();
                    }
                    this.lastRawY = this.downRawY = ev.getRawY();
                    return true;
                }
                case 2: {
                    float rawY = ev.getRawY();
                    float deltaY = this.lastRawY - rawY;
                    if (!this.isBeingDragged && Math.abs(rawY - this.downRawY) > (float)this.touchSlop) {
                        this.isBeingDragged = true;
                        deltaY = deltaY > 0.0f ? (deltaY -= (float)this.touchSlop) : (deltaY += (float)this.touchSlop);
                    }
                    if (this.isBeingDragged && Math.abs(deltaY) >= 1.0f) {
                        int stepPx = Math.round(deltaY);
                        this.lastRawY = rawY;
                        int targetScrollY = Math.max(0, Math.min(maxRange, this.getScrollY() + stepPx));
                        if (targetScrollY != this.getScrollY()) {
                            this.scrollTo(0, targetScrollY);
                        }
                    }
                    return true;
                }
                case 1: {
                    if (this.isBeingDragged) {
                        this.velocityTracker.computeCurrentVelocity(1000, (float)this.maxFlingVelocity);
                        int yVelocity = (int)this.velocityTracker.getYVelocity();
                        if (Math.abs(yVelocity) > this.minFlingVelocity) {
                            this.scroller.fling(0, this.getScrollY(), 0, -yVelocity, 0, 0, 0, maxRange);
                            this.postInvalidateOnAnimation();
                        }
                        this.isBeingDragged = false;
                    }
                    this.recycleVelocityTracker();
                    return true;
                }
                case 3: {
                    this.isBeingDragged = false;
                    this.recycleVelocityTracker();
                    return true;
                }
            }
            return true;
        }

        public void computeScroll() {
            if (this.scroller.computeScrollOffset()) {
                int maxRange = this.getMaxScrollRange();
                int targetY = Math.max(0, Math.min(maxRange, this.scroller.getCurrY()));
                if (targetY != this.getScrollY()) {
                    this.scrollTo(0, targetY);
                }
                this.postInvalidateOnAnimation();
            }
        }

        private void initOrResetVelocityTracker() {
            if (this.velocityTracker == null) {
                this.velocityTracker = VelocityTracker.obtain();
            } else {
                this.velocityTracker.clear();
            }
        }

        private void initVelocityTrackerIfNotExists() {
            if (this.velocityTracker == null) {
                this.velocityTracker = VelocityTracker.obtain();
            }
        }

        private void recycleVelocityTracker() {
            if (this.velocityTracker != null) {
                this.velocityTracker.recycle();
                this.velocityTracker = null;
            }
        }
    }
}
