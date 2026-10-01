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
                        int nextW = Math.max(FloatingDashboardService.this.dpToPx(170), Math.min(FloatingDashboardService.this.dpToPx(420), this.startW + dx));
                        int nextH = Math.max(FloatingDashboardService.this.dpToPx(150), Math.min(FloatingDashboardService.this.dpToPx(600), this.startH + dy));
                        if (nextW != liveWinSizePx[0] || nextH != liveWinSizePx[1]) {
                            liveWinSizePx[0] = nextW;
                            liveWinSizePx[1] = nextH;
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

    private int computeBorderStrokePx(DynamicOverlayRegistry.OverlayItemSpec spec) {
        int pct = spec != null ? Math.max(0, Math.min(100, spec.borderStrokePercent)) : 25;
        if (pct <= 0) {
            return 0;
        }
        if (spec != null && spec.borderColorHex != null && "#00000000".equalsIgnoreCase(spec.borderColorHex.trim())) {
            return 0;
        }
        float dpVal = (pct / 100.0f) * 7.5f;
        return Math.max(1, Math.round(dpVal * this.getResources().getDisplayMetrics().density));
    }

    private int resolveCustomBorderColor(DynamicOverlayRegistry.OverlayItemSpec spec, int fallbackColor) {
        if (spec == null || spec.borderColorHex == null || spec.borderColorHex.trim().isEmpty()) {
            return fallbackColor;
        }
        if ("#00000000".equalsIgnoreCase(spec.borderColorHex.trim())) {
            return 0;
        }
        return this.parseSafeColor(spec.borderColorHex.trim(), fallbackColor);
    }

    private void attachWidgetBorderAnimationIfConfigured(final View targetView, final DynamicOverlayRegistry.OverlayItemSpec spec, final Bitmap rawBgBmp, final int fillColor, final int baseStrokeColor) {
        if (targetView == null || spec == null) {
            return;
        }
        final int strokePx = this.computeBorderStrokePx(spec);
        final String animType = spec.borderAnimation != null ? spec.borderAnimation.trim().toUpperCase() : "NONE";
        if (strokePx <= 0 || "NONE".equals(animType) || animType.isEmpty()) {
            return;
        }
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(0.0f, 1.0f);
        animator.setDuration("NEON_BLINK".equals(animType) ? 650L : 1400L);
        animator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        animator.setRepeatMode("RAINBOW".equals(animType) ? android.animation.ValueAnimator.RESTART : android.animation.ValueAnimator.REVERSE);
        animator.addUpdateListener(anim -> {
            if (!targetView.isAttachedToWindow()) {
                return;
            }
            float frac = (Float) anim.getAnimatedValue();
            int animColor = baseStrokeColor;
            int animStroke = strokePx;
            if ("RAINBOW".equals(animType)) {
                float[] hsv = new float[]{frac * 360.0f, 0.9f, 1.0f};
                animColor = Color.HSVToColor(hsv);
            } else if ("PULSE".equals(animType)) {
                int alpha = Math.max(45, Math.min(255, Math.round(60 + frac * 195)));
                animColor = Color.argb(alpha, Color.red(baseStrokeColor), Color.green(baseStrokeColor), Color.blue(baseStrokeColor));
                animStroke = Math.max(1, Math.round(strokePx * (0.65f + 0.55f * frac)));
            } else if ("NEON_BLINK".equals(animType)) {
                int alpha = frac > 0.45f ? 255 : 25;
                animColor = Color.argb(alpha, Color.red(baseStrokeColor), Color.green(baseStrokeColor), Color.blue(baseStrokeColor));
            } else if ("GLOW".equals(animType)) {
                float[] hsv = new float[3];
                Color.colorToHSV(baseStrokeColor, hsv);
                hsv[1] = Math.max(0.2f, 1.0f - (frac * 0.5f));
                hsv[2] = 1.0f;
                animColor = Color.HSVToColor(hsv);
            }
            targetView.setBackground(this.createWidgetBackgroundDrawable(rawBgBmp, spec.widthDp, spec.heightDp, fillColor, animStroke, animColor));
        });
        targetView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                animator.start();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                animator.cancel();
            }
        });
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
                LinearLayout row = new LinearLayout((Context)this);
                row.setOrientation(0);
                row.setGravity(16);
                row.setPadding(this.dpToPx(8), this.dpToPx(4), this.dpToPx(8), this.dpToPx(4));
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
                toggleSwitch.setChecked(false);
                Runnable updateVisuals = () -> {
                    boolean on = isCheckedState[0];
                    boolean isDefaultWhite = spec.bgColorHex == null || spec.bgColorHex.trim().isEmpty() || "#FFFFFF".equalsIgnoreCase(spec.bgColorHex.trim());
                    int fillCol = on && isDefaultWhite ? Color.parseColor((String)"#ECFDF5") : bgColor;
                    int strokeCol = on ? Color.parseColor((String)"#00C853") : customStrokeColor;
                    row.setBackground(this.createWidgetBackgroundDrawable(resolvedWidgetBgBmp, spec.widthDp, spec.heightDp, fillCol, customStrokePx, strokeCol));
                    GradientDrawable badgeBg = new GradientDrawable();
                    badgeBg.setCornerRadius((float)this.dpToPx(4));
                    badgeBg.setColor(on ? Color.parseColor((String)"#00C853") : Color.parseColor((String)"#EF4444"));
                    onOffBadge.setBackground((Drawable)badgeBg);
                    onOffBadge.setText((CharSequence)(on ? "ON" : "OFF"));
                };
                updateVisuals.run();
                this.attachWidgetBorderAnimationIfConfigured(row, spec, resolvedWidgetBgBmp, bgColor, customStrokeColor);
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
                labelTv.setText((CharSequence)(spec.label + " (0-100): " + spec.currentValue));
                labelTv.setTextColor(txtColor);
                labelTv.setTypeface(Typeface.DEFAULT_BOLD);
                labelTv.setTextSize(2, 11.0f);
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
                    this.setOverlayFocusable(false);
                    return true;
                });
                return et;
            }
            case "LINK":
            case "IMAGE": {
                LinearLayout linkRow = new LinearLayout((Context)this);
                linkRow.setOrientation(0);
                linkRow.setGravity(16);
                linkRow.setPadding(this.dpToPx(10), this.dpToPx(4), this.dpToPx(10), this.dpToPx(4));
                linkRow.setBackground(this.createWidgetBackgroundDrawable(resolvedWidgetBgBmp, spec.widthDp, spec.heightDp, bgColor, customStrokePx, customStrokeColor));
                this.attachWidgetBorderAnimationIfConfigured(linkRow, spec, resolvedWidgetBgBmp, bgColor, customStrokeColor);
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
                TextView tv = new TextView((Context)this);
                tv.setText((CharSequence)spec.label);
                tv.setTextColor(txtColor);
                tv.setTextSize(2, 12.0f);
                tv.setTypeface(Typeface.DEFAULT_BOLD);
                tv.setGravity(16);
                tv.setPadding(this.dpToPx(8), this.dpToPx(4), this.dpToPx(8), this.dpToPx(4));
                tv.setBackground(itemBg);
                this.attachWidgetBorderAnimationIfConfigured(tv, spec, resolvedWidgetBgBmp, bgColor, customStrokeColor);
                // TextView is strictly for displaying text — no file replacement on click!
                return tv;
            }
        }
        spec.currentValue = "0";
        boolean[] isBtnOn = new boolean[]{false};
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
            int strokeCol = on ? Color.parseColor((String)"#00C853") : customStrokeColor;
            btnRow.setBackground(this.createWidgetBackgroundDrawable(resolvedWidgetBgBmp, spec.widthDp, spec.heightDp, fillCol, customStrokePx, strokeCol));
            labelTv.setTextColor(on && isDefaultBlue && resolvedWidgetBgBmp == null ? -1 : txtColor);
            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setColor(on ? Color.parseColor((String)"#047857") : Color.parseColor((String)"#EF4444"));
            pillBg.setCornerRadius((float)this.dpToPx(99));
            pillBg.setStroke(this.dpToPx(1), -1);
            pillBadge.setBackground((Drawable)pillBg);
            pillBadge.setText((CharSequence)(on ? "ON" : "OFF"));
        };
        updateBtnVisuals.run();
        this.attachWidgetBorderAnimationIfConfigured(btnRow, spec, resolvedWidgetBgBmp, bgColor, customStrokeColor);
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
