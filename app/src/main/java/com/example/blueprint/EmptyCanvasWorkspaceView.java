/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.annotation.SuppressLint
 *  android.content.Context
 *  android.graphics.Canvas
 *  android.graphics.Color
 *  android.graphics.Paint
 *  android.graphics.Paint$Style
 *  android.graphics.Typeface
 *  android.graphics.drawable.Drawable
 *  android.graphics.drawable.GradientDrawable
 *  android.util.AttributeSet
 *  android.view.MotionEvent
 *  android.view.View
 *  android.view.View$OnTouchListener
 *  android.view.ViewGroup$LayoutParams
 *  android.widget.FrameLayout
 *  android.widget.FrameLayout$LayoutParams
 *  android.widget.LinearLayout
 *  android.widget.LinearLayout$LayoutParams
 *  android.widget.SeekBar
 *  android.widget.Switch
 *  android.widget.TextView
 *  androidx.annotation.NonNull
 *  androidx.annotation.Nullable
 *  com.example.data.CanvasComponentEntity
 *  com.example.data.StudioProjectEntity
 */
package com.example.blueprint;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.data.CanvasComponentEntity;
import com.example.data.StudioProjectEntity;
import java.util.ArrayList;
import java.util.List;

public class EmptyCanvasWorkspaceView
extends FrameLayout {
    private final Paint gridPaint = new Paint(1);
    private final Paint bracketPaint = new Paint(1);
    private final List<CanvasComponentEntity> components = new ArrayList<CanvasComponentEntity>();
    private final TextView emptyPlaceholderView;
    private StudioProjectEntity activeProject;
    private Long selectedComponentId = null;
    private OnCanvasElementInteractionListener interactionListener;

    public EmptyCanvasWorkspaceView(@NonNull Context context) {
        this(context, null);
    }

    public EmptyCanvasWorkspaceView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        this.setWillNotDraw(false);
        this.setClipChildren(true);
        this.setClipToPadding(true);
        this.gridPaint.setColor(Color.argb((int)45, (int)148, (int)163, (int)184));
        this.gridPaint.setStrokeWidth((float)this.dpToPx(1));
        this.bracketPaint.setColor(Color.parseColor((String)"#0288D1"));
        this.bracketPaint.setStrokeWidth((float)this.dpToPx(3));
        this.bracketPaint.setStyle(Paint.Style.STROKE);
        this.emptyPlaceholderView = new TextView(context);
        this.emptyPlaceholderView.setText((CharSequence)"100% Blank Canvas \u2014 Tap Left Palette to add Switch, Button, or Slide Bar");
        this.emptyPlaceholderView.setTextColor(Color.parseColor((String)"#64748B"));
        this.emptyPlaceholderView.setTextSize(2, 12.0f);
        this.emptyPlaceholderView.setGravity(17);
        int pad = this.dpToPx(20);
        this.emptyPlaceholderView.setPadding(pad, pad, pad, pad);
        this.addView((View)this.emptyPlaceholderView, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -1));
        this.setOnClickListener(v -> {
            this.selectedComponentId = null;
            if (this.interactionListener != null) {
                this.interactionListener.onComponentSelected(null);
            }
            this.rebuildCanvasViews();
        });
    }

    public void setOnCanvasElementInteractionListener(@Nullable OnCanvasElementInteractionListener listener) {
        this.interactionListener = listener;
    }

    public void bindWorkspaceState(@NonNull StudioProjectEntity project, @NonNull List<CanvasComponentEntity> items, @Nullable Long selectedId) {
        this.activeProject = project;
        this.selectedComponentId = selectedId;
        this.components.clear();
        this.components.addAll(items);
        this.setBackgroundColor(this.parseSafeColor(project.getCanvasBgColorHex(), Color.parseColor((String)"#F8FAFC")));
        this.rebuildCanvasViews();
        this.invalidate();
    }

    @SuppressLint(value={"ClickableViewAccessibility"})
    private void rebuildCanvasViews() {
        this.removeAllViews();
        if (this.components.isEmpty()) {
            this.addView((View)this.emptyPlaceholderView, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -1));
            return;
        }
        boolean autoFix = this.activeProject != null && this.activeProject.getAutoFixSize();
        Context context = this.getContext();
        for (final CanvasComponentEntity comp : this.components) {
            boolean isSelected = this.selectedComponentId != null && this.selectedComponentId.equals(comp.getId());
            View itemView = this.createWidgetView(context, comp, isSelected);
            int wPx = this.dpToPx(Math.max(40, comp.getWidthDp()));
            int hPx = this.dpToPx(Math.max(32, comp.getHeightDp()));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(wPx, hPx);
            lp.leftMargin = this.dpToPx(Math.max(0, comp.getPosXDp()));
            lp.topMargin = this.dpToPx(Math.max(0, comp.getPosYDp()));
            if (!autoFix) {
                itemView.setOnTouchListener(new View.OnTouchListener(){
                    private float downRawX;
                    private float downRawY;
                    private int startXDp;
                    private int startYDp;
                    private boolean moved;

                    public boolean onTouch(View v, MotionEvent event) {
                        switch (event.getActionMasked()) {
                            case 0: {
                                this.downRawX = event.getRawX();
                                this.downRawY = event.getRawY();
                                this.startXDp = comp.getPosXDp();
                                this.startYDp = comp.getPosYDp();
                                this.moved = false;
                                return true;
                            }
                            case 2: {
                                float dxPx = event.getRawX() - this.downRawX;
                                float dyPx = event.getRawY() - this.downRawY;
                                if (Math.abs(dxPx) > (float)EmptyCanvasWorkspaceView.this.dpToPx(4) || Math.abs(dyPx) > (float)EmptyCanvasWorkspaceView.this.dpToPx(4)) {
                                    this.moved = true;
                                    v.setTranslationX(dxPx);
                                    v.setTranslationY(dyPx);
                                }
                                return true;
                            }
                            case 1: {
                                v.setTranslationX(0.0f);
                                v.setTranslationY(0.0f);
                                if (this.moved && EmptyCanvasWorkspaceView.this.interactionListener != null) {
                                    float density = EmptyCanvasWorkspaceView.this.getResources().getDisplayMetrics().density;
                                    int deltaXDp = Math.round((event.getRawX() - this.downRawX) / density);
                                    int deltaYDp = Math.round((event.getRawY() - this.downRawY) / density);
                                    EmptyCanvasWorkspaceView.this.interactionListener.onComponentMoved(comp, this.startXDp + deltaXDp, this.startYDp + deltaYDp);
                                } else if (EmptyCanvasWorkspaceView.this.interactionListener != null) {
                                    EmptyCanvasWorkspaceView.this.interactionListener.onComponentSelected(comp);
                                }
                                return true;
                            }
                        }
                        return false;
                    }
                });
            } else {
                itemView.setOnClickListener(v -> {
                    if (this.interactionListener != null) {
                        this.interactionListener.onComponentSelected(comp);
                    }
                });
            }
            this.addView(itemView, (ViewGroup.LayoutParams)lp);
        }
    }

    private View createWidgetView(@NonNull Context context, @NonNull CanvasComponentEntity comp, boolean isSelected) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(0);
        box.setGravity(16);
        box.setPadding(this.dpToPx(8), this.dpToPx(4), this.dpToPx(8), this.dpToPx(4));
        boolean isOn = "1".equals(comp.getCurrentValue()) || "true".equalsIgnoreCase(comp.getCurrentValue());
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius((float)this.dpToPx(6));
        bg.setColor(this.parseSafeColor(comp.getBgColorHex(), -1));
        bg.setStroke(this.dpToPx(isSelected ? 3 : 1), isSelected ? Color.parseColor((String)"#0288D1") : Color.parseColor((String)"#475569"));
        box.setBackground((Drawable)bg);
        TextView labelTv = new TextView(context);
        labelTv.setText((CharSequence)comp.getLabel());
        labelTv.setTextColor(this.parseSafeColor(comp.getTextColorHex(), Color.parseColor((String)"#0F172A")));
        labelTv.setTextSize(2, 11.0f);
        labelTv.setTypeface(Typeface.DEFAULT_BOLD);
        labelTv.setSingleLine(true);
        box.addView((View)labelTv, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        if ("TOGGLE".equals(comp.getType())) {
            Switch sw = new Switch(context);
            sw.setChecked(isOn);
            sw.setOnCheckedChangeListener((btn, checked) -> {
                if (this.interactionListener != null) {
                    this.interactionListener.onComponentToggled(comp, checked ? "1" : "0");
                }
            });
            box.addView((View)sw);
        } else if ("SLIDER".equals(comp.getType())) {
            SeekBar sb = new SeekBar(context);
            sb.setMax(Math.max(1, comp.getSliderMax()));
            box.addView((View)sb, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.dpToPx(80), -2));
        } else {
            TextView badge = new TextView(context);
            badge.setText((CharSequence)(isOn ? "ON" : "OFF"));
            badge.setTextColor(-1);
            badge.setTextSize(2, 9.0f);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setPadding(this.dpToPx(6), this.dpToPx(2), this.dpToPx(6), this.dpToPx(2));
            badge.setBackgroundColor(isOn ? Color.parseColor((String)"#00C853") : Color.parseColor((String)"#EF4444"));
            box.addView((View)badge);
        }
        return box;
    }

    protected void onDraw(@NonNull Canvas canvas) {
        boolean autoFix;
        super.onDraw(canvas);
        int w = this.getWidth();
        int h = this.getHeight();
        int step = this.dpToPx(18);
        for (int x = 0; x <= w; x += step) {
            canvas.drawLine((float)x, 0.0f, (float)x, (float)h, this.gridPaint);
        }
        for (int y = 0; y <= h; y += step) {
            canvas.drawLine(0.0f, (float)y, (float)w, (float)y, this.gridPaint);
        }
        boolean bl = autoFix = this.activeProject != null && this.activeProject.getAutoFixSize();
        if (!autoFix && w > 0 && h > 0) {
            int bLen = this.dpToPx(16);
            canvas.drawLine(0.0f, 0.0f, (float)bLen, 0.0f, this.bracketPaint);
            canvas.drawLine(0.0f, 0.0f, 0.0f, (float)bLen, this.bracketPaint);
            canvas.drawLine((float)(w - bLen), 0.0f, (float)w, 0.0f, this.bracketPaint);
            canvas.drawLine((float)w, 0.0f, (float)w, (float)bLen, this.bracketPaint);
            canvas.drawLine(0.0f, (float)(h - bLen), 0.0f, (float)h, this.bracketPaint);
            canvas.drawLine(0.0f, (float)h, (float)bLen, (float)h, this.bracketPaint);
            canvas.drawLine((float)(w - bLen), (float)h, (float)w, (float)h, this.bracketPaint);
            canvas.drawLine((float)w, (float)(h - bLen), (float)w, (float)h, this.bracketPaint);
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
            String clean = hex.trim().startsWith("#") ? hex.trim() : "#" + hex.trim();
            return Color.parseColor((String)clean);
        }
        catch (Exception e) {
            return fallback;
        }
    }

    public static interface OnCanvasElementInteractionListener {
        public void onComponentSelected(@Nullable CanvasComponentEntity var1);

        public void onComponentMoved(@NonNull CanvasComponentEntity var1, int var2, int var3);

        public void onComponentToggled(@NonNull CanvasComponentEntity var1, @Nullable String var2);
    }
}
