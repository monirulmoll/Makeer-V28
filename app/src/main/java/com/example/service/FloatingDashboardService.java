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
import com.example.engine.LuaExecutionResult;
import com.example.engine.LuaScriptEngine;
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
    private static WindowManager sActiveWindowManager = null;
    private WindowManager windowManager;
    private View floatingRootView;
    private WindowManager.LayoutParams overlayLayoutParams;
    private TextView shizukuHeaderBadgeTv;

    private static volatile boolean mainActivityVisible = false;

    public static void setMainActivityVisible(boolean visible) {
        mainActivityVisible = visible;
    }

    private final LocalConfigStateWriter.OnStateWriteListener floatingWriteListener = new LocalConfigStateWriter.OnStateWriteListener() {
        @Override
        public void onWriteSuccess(String key, int offset, String oldVal, String newVal, long durationMicros, ConfigParameterSpec.StateSnapshot snapshot) {
            if (newVal != null && (newVal.contains("[Shizuku]") || newVal.contains("via Shizuku") || newVal.startsWith("Replaced") || newVal.startsWith("Merged") || newVal.startsWith("Restored"))) {
                Toast.makeText(FloatingDashboardService.this, "\u2713 Target File Changed: " + newVal, Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onWriteError(String key, String message) {
        }

        @Override
        public void onWriteDiagnosticError(LocalConfigStateWriter.WriteDiagnosticReport report) {
            // Error diagnostic floating window completely removed as requested by user
        }
    };

    private final ShizukuPrivilegeBridge.OnShizukuStateChangeListener shizukuStateListener = (binderAlive, permissionGranted, uid) -> {
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
        int panelWidthDp = Math.max(140, Math.min(380, DynamicOverlayRegistry.getActiveCanvasWidthDp()));
        int panelHeightDp = Math.max(140, Math.min(540, DynamicOverlayRegistry.getActiveCanvasHeightDp()));
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
            panelRoot.addView((View)bgIv, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -1));
        }

        final LinearLayout container = new LinearLayout((Context)this);
        container.setOrientation(1);
        container.setBackgroundColor(0);

        float headerScale = Math.max(0.55f, Math.min(1.0f, (float)panelWidthDp / 260.0f));
        int hPadH = this.dpToPx(Math.max(4, Math.round(10.0f * headerScale)));
        int hPadV = this.dpToPx(Math.max(4, Math.round(8.0f * headerScale)));
        int logoDp = Math.max(14, Math.round(24.0f * headerScale));
        int logoMarginPx = this.dpToPx(Math.max(3, Math.round(8.0f * headerScale)));
        float headerBtnSp = Math.max(5.5f, 8.5f * headerScale);
        int btnPadH = this.dpToPx(Math.max(3, Math.round(5.0f * headerScale)));
        int btnPadV = this.dpToPx(Math.max(2, Math.round(3.0f * headerScale)));

        LinearLayout header = new LinearLayout((Context)this);
        header.setOrientation(0);
        header.setGravity(16);
        header.setPadding(hPadH, hPadV, hPadH, hPadV);
        header.setBackgroundColor(0);

        List<DynamicOverlayRegistry.OverlayItemSpec> rawSpecs = DynamicOverlayRegistry.getActiveItems();
        List<DynamicOverlayRegistry.OverlayItemSpec> specs = new java.util.ArrayList<DynamicOverlayRegistry.OverlayItemSpec>();
        if (rawSpecs != null) {
            for (DynamicOverlayRegistry.OverlayItemSpec s : rawSpecs) {
                if (s != null && (s.type == null || !s.type.trim().toUpperCase(java.util.Locale.US).startsWith("S1_"))) {
                    specs.add(s);
                }
            }
        }
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
            headerLogoIv.setImageBitmap(this.createCircularBitmap(rawLogoBitmap, this.dpToPx(logoDp)));
            LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(this.dpToPx(logoDp), this.dpToPx(logoDp));
            logoLp.rightMargin = logoMarginPx;
            header.addView((View)headerLogoIv, (ViewGroup.LayoutParams)logoLp);
        } else {
            TextView badgeCircle = new TextView((Context)this);
            badgeCircle.setText((CharSequence)"\u2726");
            badgeCircle.setTextColor(headerContentColor);
            badgeCircle.setTextSize(2, Math.max(6.5f, 10.0f * headerScale));
            badgeCircle.setGravity(17);
            GradientDrawable circleDrawable = new GradientDrawable();
            circleDrawable.setShape(1);
            circleDrawable.setColor(useLightHeaderContent ? Color.parseColor((String)"#33FFFFFF") : Color.parseColor((String)"#1F0F172A"));
            circleDrawable.setStroke(this.dpToPx(1), headerContentColor);
            badgeCircle.setBackground((Drawable)circleDrawable);
            LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(this.dpToPx(logoDp), this.dpToPx(logoDp));
            logoLp.rightMargin = logoMarginPx;
            header.addView((View)badgeCircle, (ViewGroup.LayoutParams)logoLp);
        }
        int titleAvailDp = Math.max(32, panelWidthDp - Math.round(115.0f * headerScale) - logoDp);
        TextView titleTv = new TextView((Context)this);
        titleTv.setText((CharSequence)displayTitle);
        titleTv.setTextColor(headerContentColor);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        this.configureAutoFitText(titleTv, displayTitle, 4.5f, 13.0f * headerScale, titleAvailDp, 24, 1);
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
            bubbleTitleTv.setTypeface(Typeface.DEFAULT_BOLD);
            bubbleTitleTv.setGravity(17);
            this.configureAutoFitText(bubbleTitleTv, fallbackBubbleText, 4.5f, 10.0f, 52, 42, 2);
            bubbleTitleTv.setPadding(this.dpToPx(6), this.dpToPx(4), this.dpToPx(6), this.dpToPx(4));
            goalLogoBubble.addView((View)bubbleTitleTv, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -1, 17));
        }
        int pillFillColor = useLightHeaderContent ? Color.parseColor((String)"#2EFFFFFF") : Color.parseColor((String)"#140F172A");

        TextView minimizeBtn = new TextView((Context)this);
        minimizeBtn.setText((CharSequence)"Minimize");
        minimizeBtn.setTextColor(headerContentColor);
        minimizeBtn.setTextSize(2, headerBtnSp);
        minimizeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        minimizeBtn.setSingleLine(true);
        minimizeBtn.setEllipsize(null);
        minimizeBtn.setGravity(17);
        minimizeBtn.setPadding(btnPadH, btnPadV, btnPadH, btnPadV);
        GradientDrawable minBtnBg = new GradientDrawable();
        minBtnBg.setCornerRadius((float)this.dpToPx(6));
        minBtnBg.setColor(pillFillColor);
        minBtnBg.setStroke(this.dpToPx(1), headerContentColor);
        minimizeBtn.setBackground((Drawable)minBtnBg);
        LinearLayout.LayoutParams minBtnLp = new LinearLayout.LayoutParams(-2, -2);
        minBtnLp.rightMargin = this.dpToPx(Math.max(2, Math.round(4.0f * headerScale)));
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
        hideBtn.setTextSize(2, headerBtnSp);
        hideBtn.setTypeface(Typeface.DEFAULT_BOLD);
        hideBtn.setSingleLine(true);
        hideBtn.setEllipsize(null);
        hideBtn.setGravity(17);
        hideBtn.setPadding(btnPadH, btnPadV, btnPadH, btnPadV);
        GradientDrawable hideBtnBg = new GradientDrawable();
        hideBtnBg.setCornerRadius((float)this.dpToPx(6));
        hideBtnBg.setColor(pillFillColor);
        hideBtnBg.setStroke(this.dpToPx(1), headerContentColor);
        hideBtn.setBackground((Drawable)hideBtnBg);
        LinearLayout.LayoutParams hideBtnLp = new LinearLayout.LayoutParams(-2, -2);
        hideBtnLp.rightMargin = this.dpToPx(Math.max(2, Math.round(4.0f * headerScale)));
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
        killBtn.setTextSize(2, headerBtnSp);
        killBtn.setTypeface(Typeface.DEFAULT_BOLD);
        killBtn.setSingleLine(true);
        killBtn.setEllipsize(null);
        killBtn.setGravity(17);
        killBtn.setPadding(btnPadH, btnPadV, btnPadH, btnPadV);
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
        final int[] liveWinSizePx = new int[]{currentCanvasW, currentCanvasH};
        container.addView((View)header, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        container.addView((View)canvasFrame, (ViewGroup.LayoutParams)canvasLp);
        panelRoot.addView((View)container, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -1));

        // Bottom-right corner resize grip so user can resize the floating window from its corner like a computer tab
        TextView cornerResizeGrip = new TextView((Context)this);
        cornerResizeGrip.setText((CharSequence)"\u25e2");
        cornerResizeGrip.setTextSize(2, 11.0f);
        cornerResizeGrip.setTextColor(headerContentColor);
        cornerResizeGrip.setAlpha(0.65f);
        cornerResizeGrip.setGravity(85);
        cornerResizeGrip.setPadding(this.dpToPx(4), this.dpToPx(4), this.dpToPx(4), this.dpToPx(2));
        FrameLayout.LayoutParams gripLp = new FrameLayout.LayoutParams(this.dpToPx(24), this.dpToPx(24), 85);
        cornerResizeGrip.setOnTouchListener(new View.OnTouchListener(){
            private float downRawX;
            private float downRawY;
            private int startW;
            private int startH;

            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case 0: {
                        this.downRawX = event.getRawX();
                        this.downRawY = event.getRawY();
                        this.startW = liveWinSizePx[0];
                        this.startH = liveWinSizePx[1];
                        return true;
                    }
                    case 2: {
                        int dx = Math.round(event.getRawX() - this.downRawX);
                        int dy = Math.round(event.getRawY() - this.downRawY);
                        int nextW = Math.max(FloatingDashboardService.this.dpToPx(140), Math.min(FloatingDashboardService.this.dpToPx(380), this.startW + dx));
                        int nextH = Math.max(FloatingDashboardService.this.dpToPx(140), Math.min(FloatingDashboardService.this.dpToPx(540), this.startH + dy));
                        if (nextW != liveWinSizePx[0] || nextH != liveWinSizePx[1]) {
                            liveWinSizePx[0] = nextW;
                            liveWinSizePx[1] = nextH;
                            float density = FloatingDashboardService.this.getResources().getDisplayMetrics().density;
                            if (density > 0.0f) {
                                DynamicOverlayRegistry.setActiveCanvasSizeDp(Math.round((float)nextW / density), Math.round((float)nextH / density));
                            }
                            ViewGroup.LayoutParams rootLp = panelRoot.getLayoutParams();
                            if (rootLp != null) {
                                rootLp.width = nextW;
                                rootLp.height = nextH;
                                panelRoot.setLayoutParams(rootLp);
                            }
                            if (FloatingDashboardService.this.overlayLayoutParams != null && FloatingDashboardService.this.floatingRootView != null && FloatingDashboardService.this.windowManager != null) {
                                FloatingDashboardService.this.overlayLayoutParams.width = nextW;
                                FloatingDashboardService.this.overlayLayoutParams.height = nextH;
                                FloatingDashboardService.this.windowManager.updateViewLayout(FloatingDashboardService.this.floatingRootView, (ViewGroup.LayoutParams)FloatingDashboardService.this.overlayLayoutParams);
                            }
                        }
                        return true;
                    }
                }
                return false;
            }
        });
        panelRoot.addView((View)cornerResizeGrip, (ViewGroup.LayoutParams)gripLp);

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
                                FloatingDashboardService.this.overlayLayoutParams.width = liveWinSizePx[0];
                                FloatingDashboardService.this.overlayLayoutParams.height = liveWinSizePx[1];
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
        // NOTE: Do NOT execute any target path file change when the floating window first opens!
        // Target files are ONLY modified when the user explicitly turns ON or triggers a widget option inside the floating window.
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

    private static final int[] RGB_LIGHT_SWEEP_COLORS = new int[]{
            0xFFFF0040,
            0xFFFF8000,
            0xFFFFFF00,
            0xFF00FF40,
            0xFF00FFFF,
            0xFF0066FF,
            0xFF8000FF,
            0xFFFF00CC,
            0xFFFF0040
    };

    private static final class AnimatedWidgetBackgroundDrawable extends Drawable {
        private final Bitmap rawBgBmp;
        private int fillColor;
        private final int strokeWidthPx;
        private int strokeColor;
        private final int cornerRadiusPx;
        private final String animType;
        private float animProgress = 0.0f;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF rectF = new android.graphics.RectF();
        private final android.graphics.Matrix shaderMatrix = new android.graphics.Matrix();
        private Bitmap cachedCroppedBmp;
        private int cachedW = -1;
        private int cachedH = -1;

        AnimatedWidgetBackgroundDrawable(Bitmap rawBgBmp, int fillColor, int strokeWidthPx, int strokeColor, int cornerRadiusPx, String animType) {
            this.rawBgBmp = rawBgBmp;
            this.fillColor = fillColor;
            this.strokeWidthPx = strokeWidthPx;
            this.strokeColor = strokeColor;
            this.cornerRadiusPx = cornerRadiusPx;
            this.animType = animType != null ? animType.trim().toUpperCase() : "NONE";
            this.fillPaint.setStyle(Paint.Style.FILL);
            this.borderPaint.setStyle(Paint.Style.STROKE);
            this.glowPaint.setStyle(Paint.Style.STROKE);
        }

        void updateColors(int newFillColor, int newStrokeColor) {
            if (this.fillColor != newFillColor || this.strokeColor != newStrokeColor) {
                this.fillColor = newFillColor;
                this.strokeColor = newStrokeColor;
                invalidateSelf();
            }
        }

        void setAnimProgress(float progress) {
            this.animProgress = progress;
            invalidateSelf();
        }

        @Override
        public void draw(Canvas canvas) {
            android.graphics.Rect b = getBounds();
            int w = b.width();
            int h = b.height();
            if (w <= 0 || h <= 0) {
                return;
            }
            if (rawBgBmp != null) {
                if (cachedCroppedBmp == null || cachedW != w || cachedH != h) {
                    cachedW = w;
                    cachedH = h;
                    cachedCroppedBmp = createRoundedCropStatic(rawBgBmp, w, h, cornerRadiusPx);
                }
                if (cachedCroppedBmp != null) {
                    canvas.drawBitmap(cachedCroppedBmp, b.left, b.top, null);
                } else {
                    fillPaint.setColor(fillColor);
                    rectF.set(b.left, b.top, b.right, b.bottom);
                    canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, fillPaint);
                }
            } else {
                fillPaint.setColor(fillColor);
                rectF.set(b.left, b.top, b.right, b.bottom);
                canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, fillPaint);
            }

            if (strokeWidthPx <= 0) {
                return;
            }

            float inset = Math.max(1.0f, strokeWidthPx / 2.0f);
            rectF.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
            float cx = b.exactCenterX();
            float cy = b.exactCenterY();

            borderPaint.setShader(null);
            borderPaint.setStrokeWidth(strokeWidthPx);

            if ("RGB_LIGHT".equals(animType)) {
                android.graphics.SweepGradient sweep = new android.graphics.SweepGradient(cx, cy, RGB_LIGHT_SWEEP_COLORS, null);
                shaderMatrix.reset();
                shaderMatrix.postRotate(animProgress * 360.0f, cx, cy);
                sweep.setLocalMatrix(shaderMatrix);

                glowPaint.setShader(sweep);
                glowPaint.setStrokeWidth(strokeWidthPx * 1.65f);
                glowPaint.setAlpha(95);
                canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, glowPaint);

                borderPaint.setShader(sweep);
                borderPaint.setAlpha(255);
                canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, borderPaint);
            } else if ("RAINBOW".equals(animType)) {
                float[] hsv = new float[]{(animProgress * 360.0f) % 360.0f, 0.9f, 1.0f};
                int rainbowCol = Color.HSVToColor(hsv);
                glowPaint.setShader(null);
                glowPaint.setColor(rainbowCol);
                glowPaint.setStrokeWidth(strokeWidthPx * 1.5f);
                glowPaint.setAlpha(80);
                canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, glowPaint);

                borderPaint.setColor(rainbowCol);
                borderPaint.setAlpha(255);
                canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, borderPaint);
            } else if ("PULSE".equals(animType)) {
                int alpha = Math.max(60, Math.min(255, Math.round(65 + animProgress * 190)));
                int pulseCol = Color.argb(alpha, Color.red(strokeColor), Color.green(strokeColor), Color.blue(strokeColor));
                borderPaint.setColor(pulseCol);
                borderPaint.setStrokeWidth(Math.max(1.5f, strokeWidthPx * (0.65f + 0.65f * animProgress)));
                canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, borderPaint);
            } else if ("NEON_BLINK".equals(animType)) {
                boolean high = animProgress > 0.42f;
                if (high) {
                    glowPaint.setShader(null);
                    glowPaint.setColor(strokeColor);
                    glowPaint.setStrokeWidth(strokeWidthPx * 1.7f);
                    glowPaint.setAlpha(110);
                    canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, glowPaint);
                }
                int alpha = high ? 255 : 35;
                borderPaint.setColor(Color.argb(alpha, Color.red(strokeColor), Color.green(strokeColor), Color.blue(strokeColor)));
                canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, borderPaint);
            } else if ("GLOW".equals(animType)) {
                glowPaint.setShader(null);
                glowPaint.setColor(strokeColor);
                glowPaint.setStrokeWidth(strokeWidthPx * (1.2f + 0.9f * animProgress));
                glowPaint.setAlpha(Math.max(30, Math.min(160, Math.round(45 + 115 * animProgress))));
                canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, glowPaint);

                float[] hsv = new float[3];
                Color.colorToHSV(strokeColor, hsv);
                hsv[1] = Math.max(0.25f, 1.0f - (animProgress * 0.45f));
                hsv[2] = 1.0f;
                borderPaint.setColor(Color.HSVToColor(hsv));
                borderPaint.setAlpha(255);
                canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, borderPaint);
            } else {
                borderPaint.setColor(strokeColor);
                canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, borderPaint);
            }
        }

        private static Bitmap createRoundedCropStatic(Bitmap src, int targetW, int targetH, int cornerRadiusPx) {
            if (src == null || src.isRecycled() || targetW <= 0 || targetH <= 0) {
                return null;
            }
            try {
                Bitmap output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(output);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                android.graphics.RectF rectF = new android.graphics.RectF(0f, 0f, targetW, targetH);
                canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, paint);
                paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
                float scale = Math.max((float) targetW / (float) src.getWidth(), (float) targetH / (float) src.getHeight());
                float scaledW = scale * src.getWidth();
                float scaledH = scale * src.getHeight();
                float left = (targetW - scaledW) / 2.0f;
                float top = (targetH - scaledH) / 2.0f;
                android.graphics.RectF dstRect = new android.graphics.RectF(left, top, left + scaledW, top + scaledH);
                canvas.drawBitmap(src, null, dstRect, paint);
                return output;
            } catch (Throwable t) {
                return null;
            }
        }

        @Override
        public void setAlpha(int alpha) {
            fillPaint.setAlpha(alpha);
            borderPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            fillPaint.setColorFilter(colorFilter);
            borderPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    private Drawable createWidgetBackgroundDrawable(Bitmap rawBgBmp, int widthDp, int heightDp, int fillColor, int strokeWidthPx, int strokeColor) {
        return new AnimatedWidgetBackgroundDrawable(rawBgBmp, fillColor, strokeWidthPx, strokeColor, this.dpToPx(8), "NONE");
    }

    private int computeBorderStrokePx(DynamicOverlayRegistry.OverlayItemSpec spec) {
        String animType = spec != null && spec.borderAnimation != null ? spec.borderAnimation.trim().toUpperCase() : "NONE";
        boolean hasAnim = !animType.isEmpty() && !"NONE".equals(animType);
        int pct = spec != null ? Math.max(0, Math.min(100, spec.borderStrokePercent)) : 25;
        if (pct <= 0) {
            if (hasAnim) {
                pct = 35;
            } else {
                return 0;
            }
        }
        if (!hasAnim && spec != null && spec.borderColorHex != null && "#00000000".equalsIgnoreCase(spec.borderColorHex.trim())) {
            return 0;
        }
        float dpVal = (pct / 100.0f) * 7.5f;
        int minPx = hasAnim ? Math.max(2, this.dpToPx(2)) : 1;
        return Math.max(minPx, Math.round(dpVal * this.getResources().getDisplayMetrics().density));
    }

    private int resolveCustomBorderColor(DynamicOverlayRegistry.OverlayItemSpec spec, int fallbackColor) {
        if (spec == null || spec.borderColorHex == null || spec.borderColorHex.trim().isEmpty()) {
            return fallbackColor;
        }
        String animType = spec.borderAnimation != null ? spec.borderAnimation.trim().toUpperCase() : "NONE";
        boolean hasAnim = !animType.isEmpty() && !"NONE".equals(animType);
        if ("#00000000".equalsIgnoreCase(spec.borderColorHex.trim())) {
            return hasAnim ? fallbackColor : 0;
        }
        return this.parseSafeColor(spec.borderColorHex.trim(), fallbackColor);
    }

    private void attachWidgetBorderAnimationIfConfigured(final View targetView, final DynamicOverlayRegistry.OverlayItemSpec spec, final Bitmap rawBgBmp, final int fillColor, final int baseStrokeColor) {
        if (targetView == null || spec == null) {
            return;
        }
        final int strokePx = this.computeBorderStrokePx(spec);
        final String animType = spec.borderAnimation != null ? spec.borderAnimation.trim().toUpperCase() : "NONE";
        final AnimatedWidgetBackgroundDrawable animDrawable = new AnimatedWidgetBackgroundDrawable(
                rawBgBmp,
                fillColor,
                strokePx,
                baseStrokeColor,
                this.dpToPx(8),
                animType
        );
        targetView.setBackground(animDrawable);
        if (strokePx <= 0 || "NONE".equals(animType) || animType.isEmpty()) {
            return;
        }
        final boolean isContinuousCycle = "RGB_LIGHT".equals(animType) || "RAINBOW".equals(animType);
        final android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(0.0f, 1.0f);
        if ("NEON_BLINK".equals(animType)) {
            animator.setDuration(600L);
        } else if ("RGB_LIGHT".equals(animType) || "RAINBOW".equals(animType)) {
            animator.setDuration(2000L);
        } else {
            animator.setDuration(1200L);
        }
        animator.setInterpolator(new android.view.animation.LinearInterpolator());
        animator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        animator.setRepeatMode(isContinuousCycle ? android.animation.ValueAnimator.RESTART : android.animation.ValueAnimator.REVERSE);
        animator.addUpdateListener(anim -> {
            float frac = (Float) anim.getAnimatedValue();
            animDrawable.setAnimProgress(frac);
            targetView.postInvalidateOnAnimation();
        });
        targetView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                if (!animator.isStarted()) {
                    animator.start();
                }
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                animator.cancel();
            }
        });
        animator.start();
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
        final int customStrokePx = this.computeBorderStrokePx(spec);
        final int customStrokeColor = this.resolveCustomBorderColor(spec, Color.parseColor((String)"#38BDF8"));
        Drawable itemBg = this.createWidgetBackgroundDrawable(resolvedWidgetBgBmp, spec.widthDp, spec.heightDp, bgColor, customStrokePx, customStrokeColor);
        switch (type = spec.type != null ? spec.type : "BUTTON") {
            case "TOGGLE": {
                int wDp = Math.max(48, spec.widthDp);
                int hDp = Math.max(28, spec.heightDp);
                float wScale = Math.max(0.55f, Math.min(1.0f, Math.min((float)wDp / 160.0f, (float)hDp / 38.0f)));
                int padH = this.dpToPx(Math.max(4, Math.round(8.0f * wScale)));
                int padV = this.dpToPx(Math.max(2, Math.round(4.0f * wScale)));
                LinearLayout row = new LinearLayout((Context)this);
                row.setOrientation(0);
                row.setGravity(16);
                row.setPadding(padH, padV, padH, padV);
                // Start all toggle options OFF when the floating window first opens unless already turned ON in this session
                spec.currentValue = "0";
                boolean[] isCheckedState = new boolean[]{false};
                boolean[] suppressCallback = new boolean[]{false};
                LinearLayout textCol = new LinearLayout((Context)this);
                textCol.setOrientation(1);
                LinearLayout.LayoutParams textColLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
                TextView titleTv = new TextView((Context)this);
                titleTv.setText((CharSequence)spec.label);
                titleTv.setTextColor(txtColor);
                titleTv.setTypeface(Typeface.DEFAULT_BOLD);
                int textAvailW = Math.max(24, wDp - Math.round(74.0f * wScale));
                this.configureAutoFitText(titleTv, spec.label, 4.5f, 11.0f, textAvailW, hDp, 1);
                textCol.addView((View)titleTv);
                TextView onOffBadge = new TextView((Context)this);
                onOffBadge.setTextSize(2, Math.max(6.0f, 9.0f * wScale));
                onOffBadge.setTypeface(Typeface.DEFAULT_BOLD);
                onOffBadge.setTextColor(-1);
                onOffBadge.setSingleLine(true);
                onOffBadge.setEllipsize(null);
                onOffBadge.setPadding(this.dpToPx(Math.max(3, Math.round(5.0f * wScale))), this.dpToPx(1), this.dpToPx(Math.max(3, Math.round(5.0f * wScale))), this.dpToPx(1));
                LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(-2, -2);
                badgeLp.leftMargin = this.dpToPx(Math.max(2, Math.round(6.0f * wScale)));
                badgeLp.rightMargin = this.dpToPx(Math.max(2, Math.round(4.0f * wScale)));
                Switch toggleSwitch = new Switch((Context)this);
                toggleSwitch.setShowText(false);
                toggleSwitch.setChecked(false);
                toggleSwitch.setScaleX(wScale);
                toggleSwitch.setScaleY(wScale);
                this.attachWidgetBorderAnimationIfConfigured(row, spec, resolvedWidgetBgBmp, bgColor, customStrokeColor);
                Runnable updateVisuals = () -> {
                    boolean on = isCheckedState[0];
                    boolean isDefaultWhite = spec.bgColorHex == null || spec.bgColorHex.trim().isEmpty() || "#FFFFFF".equalsIgnoreCase(spec.bgColorHex.trim());
                    int fillCol = on && isDefaultWhite ? Color.parseColor((String)"#ECFDF5") : bgColor;
                    boolean hasCustomBorder = spec.borderColorHex != null && !spec.borderColorHex.trim().isEmpty() && !"#38BDF8".equalsIgnoreCase(spec.borderColorHex.trim()) && !"#00000000".equalsIgnoreCase(spec.borderColorHex.trim());
                    int strokeCol = (on && !hasCustomBorder) ? Color.parseColor((String)"#00C853") : customStrokeColor;
                    if (row.getBackground() instanceof AnimatedWidgetBackgroundDrawable) {
                        ((AnimatedWidgetBackgroundDrawable) row.getBackground()).updateColors(fillCol, strokeCol);
                    } else {
                        row.setBackground(this.createWidgetBackgroundDrawable(resolvedWidgetBgBmp, spec.widthDp, spec.heightDp, fillCol, customStrokePx, strokeCol));
                    }
                    GradientDrawable badgeBg = new GradientDrawable();
                    badgeBg.setCornerRadius((float)this.dpToPx(4));
                    badgeBg.setColor(on ? Color.parseColor((String)"#00C853") : Color.parseColor((String)"#EF4444"));
                    onOffBadge.setBackground((Drawable)badgeBg);
                    onOffBadge.setText((CharSequence)(on ? "ON" : "OFF"));
                };
                updateVisuals.run();
                toggleSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
                    if (suppressCallback[0]) {
                        return;
                    }
                    final DynamicOverlayRegistry.OverlayItemSpec latest = DynamicOverlayRegistry.getSpecById(spec.id, spec);
                    isCheckedState[0] = isChecked;
                    spec.currentValue = latest.currentValue = isChecked ? "1" : "0";
                    updateVisuals.run();
                    if (isChecked) {
                        SoundTriggerPlayer.playSoundTrigger((Context)this, (View)btn, latest.soundTrigger, latest.customSoundPath);
                    } else {
                        SoundTriggerPlayer.playSoundTrigger((Context)this, (View)btn, latest.offSoundTrigger, latest.offCustomSoundPath);
                    }
                    final String payload = isChecked ? latest.onPayloadHex : latest.offPayloadHex;
                    if (latest.isLuaScript) {
                        this.executeFloatingLuaAction(latest, isChecked, isChecked ? 1 : 0, isChecked ? "1" : "0");
                    } else {
                        new Thread(() -> {
                            LocalConfigStateWriter.getInstance().executeFloatingWidgetPatchSync(
                                    this.getFilesDir(),
                                    "widget_" + latest.id,
                                    "TOGGLE",
                                    latest.targetFilePath,
                                    latest.byteOffsetHex,
                                    latest.offPayloadHex,
                                    latest.onPayloadHex,
                                    payload,
                                    isChecked,
                                    latest.label,
                                    latest.customImagePath
                            );
                        }).start();
                    }
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
                this.attachWidgetBorderAnimationIfConfigured(box, spec, resolvedWidgetBgBmp, bgColor, customStrokeColor);
                final int maxVal = 100;
                final TextView labelTv = new TextView((Context)this);
                String initSliderText = spec.label + " (0-100): " + spec.currentValue;
                labelTv.setText((CharSequence)initSliderText);
                labelTv.setTextColor(txtColor);
                labelTv.setTypeface(Typeface.DEFAULT_BOLD);
                this.configureAutoFitText(labelTv, initSliderText, 4.5f, 11.0f, Math.max(30, spec.widthDp - 16), Math.max(16, spec.heightDp / 2), 1);
                SeekBar seekBar = new SeekBar((Context)this);
                seekBar.setMax(maxVal);
                int initProgress = 0;
                try {
                    initProgress = Math.max(0, Math.min(100, Integer.parseInt(spec.currentValue)));
                }
                catch (Exception ignored) {
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
                        labelTv.setText((CharSequence)(latest.label + " (0-100): " + progress));
                        spec.currentValue = latest.currentValue = String.valueOf(progress);
                    }

                    public void onStartTrackingTouch(SeekBar sb) {
                        SoundTriggerPlayer.playSoundTrigger((Context)FloatingDashboardService.this, (View)sb, spec.soundTrigger, spec.customSoundPath);
                    }

                    public void onStopTrackingTouch(SeekBar sb) {
                        final int progress = sb.getProgress();
                        final DynamicOverlayRegistry.OverlayItemSpec latest = DynamicOverlayRegistry.getSpecById(spec.id, spec);
                        if (progress == 0) {
                            SoundTriggerPlayer.playSoundTrigger((Context)FloatingDashboardService.this, (View)sb, latest.offSoundTrigger, latest.offCustomSoundPath);
                        } else {
                            SoundTriggerPlayer.playSoundTrigger((Context)FloatingDashboardService.this, (View)sb, latest.soundTrigger, latest.customSoundPath);
                        }
                        if (latest.isLuaScript) {
                            FloatingDashboardService.this.executeFloatingLuaAction(latest, progress > 0, progress, String.valueOf(progress));
                        } else {
                            new Thread(() -> LocalConfigStateWriter.getInstance().executeFloatingWidgetPatchSync(
                                    FloatingDashboardService.this.getFilesDir(),
                                    "widget_" + latest.id,
                                    "SLIDER",
                                    latest.targetFilePath,
                                    latest.byteOffsetHex,
                                    latest.offPayloadHex,
                                    latest.onPayloadHex,
                                    String.valueOf(progress),
                                    progress > 0,
                                    latest.label,
                                    latest.customImagePath
                            )).start();
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
                et.setText((CharSequence)("0".equals(spec.currentValue) || "false".equalsIgnoreCase(spec.currentValue) ? "" : spec.currentValue));
                et.setTextColor(txtColor);
                et.setHintTextColor(-3355444);
                et.setTextSize(2, 12.0f);
                et.setSingleLine(true);
                et.setBackground(itemBg);
                et.setPadding(this.dpToPx(10), this.dpToPx(4), this.dpToPx(10), this.dpToPx(4));
                this.attachWidgetBorderAnimationIfConfigured(et, spec, resolvedWidgetBgBmp, bgColor, customStrokeColor);
                et.setOnClickListener(v -> {
                    this.setOverlayFocusable(true);
                    SoundTriggerPlayer.playSoundTrigger((Context)this, v, spec.soundTrigger, spec.customSoundPath);
                });
                et.setOnEditorActionListener((v, actionId, event) -> {
                    final String val = v.getText().toString();
                    final boolean isOff = val.trim().isEmpty() || "0".equals(val.trim()) || "false".equalsIgnoreCase(val.trim());
                    if (isOff) {
                        SoundTriggerPlayer.playSoundTrigger((Context)this, (View)v, spec.offSoundTrigger, spec.offCustomSoundPath);
                    } else {
                        SoundTriggerPlayer.playSoundTrigger((Context)this, (View)v, spec.soundTrigger, spec.customSoundPath);
                    }
                    if (spec.isLuaScript) {
                        int numVal = 0;
                        try {
                            numVal = Integer.parseInt(val.trim());
                        } catch (Exception ignored) {
                        }
                        this.executeFloatingLuaAction(spec, !isOff, numVal, val);
                    } else {
                        new Thread(() -> LocalConfigStateWriter.getInstance().executeFloatingWidgetPatchSync(
                                this.getFilesDir(),
                                "widget_" + spec.id,
                                "INPUT",
                                spec.targetFilePath,
                                spec.byteOffsetHex,
                                spec.offPayloadHex,
                                spec.onPayloadHex,
                                val,
                                !isOff,
                                spec.label,
                                spec.customImagePath
                        )).start();
                    }
                    this.setOverlayFocusable(false);
                    return true;
                });
                return et;
            }
            case "LINK":
            case "IMAGE": {
                int wDp = Math.max(48, spec.widthDp);
                int hDp = Math.max(28, spec.heightDp);
                float wScale = Math.max(0.55f, Math.min(1.0f, Math.min((float)wDp / 160.0f, (float)hDp / 38.0f)));
                int padH = this.dpToPx(Math.max(4, Math.round(10.0f * wScale)));
                int padV = this.dpToPx(Math.max(2, Math.round(4.0f * wScale)));
                LinearLayout linkRow = new LinearLayout((Context)this);
                linkRow.setOrientation(0);
                linkRow.setGravity(16);
                linkRow.setPadding(padH, padV, padH, padV);
                linkRow.setBackground(this.createWidgetBackgroundDrawable(resolvedWidgetBgBmp, spec.widthDp, spec.heightDp, bgColor, customStrokePx, customStrokeColor));
                this.attachWidgetBorderAnimationIfConfigured(linkRow, spec, resolvedWidgetBgBmp, bgColor, customStrokeColor);
                TextView iconTv = new TextView((Context)this);
                iconTv.setText((CharSequence)"\ud83c\udf10");
                iconTv.setTextSize(2, Math.max(8.0f, 12.0f * wScale));
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(-2, -2);
                iconLp.rightMargin = this.dpToPx(Math.max(3, Math.round(6.0f * wScale)));
                TextView labelTv = new TextView((Context)this);
                labelTv.setText((CharSequence)spec.label);
                labelTv.setTextColor(txtColor);
                labelTv.setTypeface(Typeface.DEFAULT_BOLD);
                int textAvailW = Math.max(24, wDp - Math.round(64.0f * wScale));
                this.configureAutoFitText(labelTv, spec.label, 4.5f, 12.0f, textAvailW, hDp, 1);
                LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
                TextView openBadge = new TextView((Context)this);
                openBadge.setText((CharSequence)"OPEN \u2197");
                openBadge.setTextColor(-1);
                openBadge.setTextSize(2, Math.max(6.0f, 9.0f * wScale));
                openBadge.setTypeface(Typeface.DEFAULT_BOLD);
                openBadge.setSingleLine(true);
                openBadge.setEllipsize(null);
                openBadge.setPadding(this.dpToPx(Math.max(4, Math.round(7.0f * wScale))), this.dpToPx(1), this.dpToPx(Math.max(4, Math.round(7.0f * wScale))), this.dpToPx(1));
                GradientDrawable badgeBg = new GradientDrawable();
                badgeBg.setColor(Color.parseColor((String)"#0288D1"));
                badgeBg.setCornerRadius((float)this.dpToPx(99));
                openBadge.setBackground((Drawable)badgeBg);
                View.OnClickListener openClick = v -> {
                    DynamicOverlayRegistry.OverlayItemSpec latest = DynamicOverlayRegistry.getSpecById(spec.id, spec);
                    SoundTriggerPlayer.playSoundTrigger((Context)this, (View)linkRow, latest.soundTrigger, latest.customSoundPath);
                    String urlToOpen = latest.linkUrl != null && !latest.linkUrl.trim().isEmpty()
                            ? latest.linkUrl.trim()
                            : (latest.onPayloadHex != null && latest.onPayloadHex.contains(".") ? latest.onPayloadHex.trim() : "https://google.com");
                    this.openLinkUrl(urlToOpen);
                };
                linkRow.setOnClickListener(openClick);
                openBadge.setOnClickListener(openClick);
                linkRow.addView((View)iconTv, (ViewGroup.LayoutParams)iconLp);
                linkRow.addView((View)labelTv, (ViewGroup.LayoutParams)labelLp);
                linkRow.addView((View)openBadge);
                return linkRow;
            }
            case "TEXT": {
                int wDp = Math.max(48, spec.widthDp);
                int hDp = Math.max(28, spec.heightDp);
                TextView tv = new TextView((Context)this);
                tv.setText((CharSequence)spec.label);
                tv.setTextColor(txtColor);
                tv.setTypeface(Typeface.DEFAULT_BOLD);
                tv.setGravity(16);
                int padH = this.dpToPx(Math.max(4, Math.min(8, wDp / 14)));
                tv.setPadding(padH, this.dpToPx(2), padH, this.dpToPx(2));
                this.configureAutoFitText(tv, spec.label, 4.5f, 12.0f, Math.max(24, wDp - 12), hDp, 1);
                tv.setBackground(itemBg);
                this.attachWidgetBorderAnimationIfConfigured(tv, spec, resolvedWidgetBgBmp, bgColor, customStrokeColor);
                // TextView is strictly for displaying text — no file replacement on click!
                return tv;
            }
        }
        spec.currentValue = "0";
        boolean[] isBtnOn = new boolean[]{false};
        int wDp = Math.max(48, spec.widthDp);
        int hDp = Math.max(28, spec.heightDp);
        float wScale = Math.max(0.55f, Math.min(1.0f, Math.min((float)wDp / 160.0f, (float)hDp / 38.0f)));
        int padH = this.dpToPx(Math.max(4, Math.round(10.0f * wScale)));
        int padV = this.dpToPx(Math.max(2, Math.round(4.0f * wScale)));
        LinearLayout btnRow = new LinearLayout((Context)this);
        btnRow.setOrientation(0);
        btnRow.setGravity(16);
        btnRow.setPadding(padH, padV, padH, padV);
        TextView labelTv = new TextView((Context)this);
        labelTv.setText((CharSequence)spec.label);
        labelTv.setTypeface(Typeface.DEFAULT_BOLD);
        int textAvailW = Math.max(24, wDp - Math.round(52.0f * wScale));
        this.configureAutoFitText(labelTv, spec.label, 4.5f, 12.0f, textAvailW, hDp, 1);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        TextView pillBadge = new TextView((Context)this);
        pillBadge.setTextColor(-1);
        pillBadge.setTextSize(2, Math.max(6.0f, 10.0f * wScale));
        pillBadge.setTypeface(Typeface.DEFAULT_BOLD);
        pillBadge.setSingleLine(true);
        pillBadge.setEllipsize(null);
        pillBadge.setPadding(this.dpToPx(Math.max(4, Math.round(8.0f * wScale))), this.dpToPx(1), this.dpToPx(Math.max(4, Math.round(8.0f * wScale))), this.dpToPx(1));
        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(-2, -2);
        pillLp.leftMargin = this.dpToPx(Math.max(2, Math.round(6.0f * wScale)));
        this.attachWidgetBorderAnimationIfConfigured(btnRow, spec, resolvedWidgetBgBmp, bgColor, customStrokeColor);
        Runnable updateBtnVisuals = () -> {
            boolean on = isBtnOn[0];
            boolean isDefaultBlue = spec.bgColorHex == null || spec.bgColorHex.trim().isEmpty() || "#2563EB".equalsIgnoreCase(spec.bgColorHex.trim());
            int fillCol = on && isDefaultBlue ? Color.parseColor((String)"#00C853") : bgColor;
            boolean hasCustomBorder = spec.borderColorHex != null && !spec.borderColorHex.trim().isEmpty() && !"#38BDF8".equalsIgnoreCase(spec.borderColorHex.trim()) && !"#00000000".equalsIgnoreCase(spec.borderColorHex.trim());
            int strokeCol = (on && !hasCustomBorder) ? Color.parseColor((String)"#00C853") : customStrokeColor;
            if (btnRow.getBackground() instanceof AnimatedWidgetBackgroundDrawable) {
                ((AnimatedWidgetBackgroundDrawable) btnRow.getBackground()).updateColors(fillCol, strokeCol);
            } else {
                btnRow.setBackground(this.createWidgetBackgroundDrawable(resolvedWidgetBgBmp, spec.widthDp, spec.heightDp, fillCol, customStrokePx, strokeCol));
            }
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
            final DynamicOverlayRegistry.OverlayItemSpec latest = DynamicOverlayRegistry.getSpecById(spec.id, spec);
            final boolean nextOn = !isBtnOn[0];
            isBtnOn[0] = nextOn;
            spec.currentValue = latest.currentValue = nextOn ? "1" : "0";
            updateBtnVisuals.run();
            if (nextOn) {
                SoundTriggerPlayer.playSoundTrigger((Context)this, (View)btnRow, latest.soundTrigger, latest.customSoundPath);
            } else {
                SoundTriggerPlayer.playSoundTrigger((Context)this, (View)btnRow, latest.offSoundTrigger, latest.offCustomSoundPath);
            }
            final String payload = nextOn ? latest.onPayloadHex : latest.offPayloadHex;
            if (latest.isLuaScript) {
                this.executeFloatingLuaAction(latest, nextOn, nextOn ? 1 : 0, nextOn ? "1" : "0");
            } else {
                new Thread(() -> {
                    LocalConfigStateWriter.getInstance().executeFloatingWidgetPatchSync(
                            this.getFilesDir(),
                            "widget_" + latest.id,
                            "BUTTON",
                            latest.targetFilePath,
                            latest.byteOffsetHex,
                            latest.offPayloadHex,
                            latest.onPayloadHex,
                            payload,
                            nextOn,
                            latest.label,
                            latest.customImagePath
                    );
                }).start();
            }
        };
        btnRow.setOnClickListener(clickListener);
        pillBadge.setOnClickListener(clickListener);
        btnRow.addView((View)labelTv, (ViewGroup.LayoutParams)labelLp);
        btnRow.addView((View)pillBadge, (ViewGroup.LayoutParams)pillLp);
        return btnRow;
    }

    private void executeFloatingLuaAction(DynamicOverlayRegistry.OverlayItemSpec spec, boolean state, int val, String text) {
        if (spec == null) return;
        try {
            LuaExecutionResult res = LuaScriptEngine.executeOverlayLuaLogic(
                    spec.label != null ? spec.label : "Widget",
                    spec.type != null ? spec.type : "BUTTON",
                    spec.onPayloadHex,
                    spec.linkUrl,
                    state,
                    val,
                    text != null ? text : ""
            );
            if (res.getOpenedUrl() != null && !res.getOpenedUrl().trim().isEmpty()) {
                this.openLinkUrl(res.getOpenedUrl().trim());
            }
            String msg = res.getToastMessage();
            if (msg == null || msg.trim().isEmpty()) {
                msg = res.getAlertMessage();
            }
            if ((msg == null || msg.trim().isEmpty()) && res.getLogs() != null && !res.getLogs().isEmpty()) {
                msg = res.getLogs().get(res.getLogs().size() - 1);
            }
            if (msg != null && !msg.trim().isEmpty()) {
                Toast.makeText((Context)this, (CharSequence)msg, (int)0).show();
            }
        } catch (Throwable ignored) {
        }
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

    private void configureAutoFitText(TextView tv, String text, float minSp, float maxSp, int availWidthDp, int availHeightDp, int maxLines) {
        if (tv == null) {
            return;
        }
        String safe = text != null ? text : "";
        int len = Math.max(1, safe.length());
        float charFactor = maxLines > 1 ? 0.35f : 0.58f;
        float byW = (float)Math.max(20, availWidthDp) / ((float)len * charFactor);
        float byH = (float)Math.max(16, availHeightDp) * (maxLines > 1 ? 0.34f : 0.48f);
        float computedSp = Math.max(minSp, Math.min(maxSp, Math.min(byW, byH)));
        tv.setMaxLines(maxLines);
        if (maxLines == 1) {
            tv.setSingleLine(true);
        }
        tv.setEllipsize(null);
        tv.setTextSize(2, computedSp);
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                int minInt = Math.max(4, Math.round(minSp));
                int maxInt = Math.max(minInt + 1, Math.round(maxSp));
                tv.setAutoSizeTextTypeUniformWithConfiguration(minInt, maxInt, 1, 2);
            }
            catch (Throwable ignored) {
            }
        }
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
