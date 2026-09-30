/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  android.graphics.Color
 *  android.graphics.Typeface
 *  android.graphics.drawable.Drawable
 *  android.graphics.drawable.GradientDrawable
 *  android.util.AttributeSet
 *  android.view.View
 *  android.view.ViewGroup$LayoutParams
 *  android.widget.Button
 *  android.widget.EditText
 *  android.widget.LinearLayout
 *  android.widget.LinearLayout$LayoutParams
 *  android.widget.ScrollView
 *  android.widget.TextView
 *  androidx.annotation.NonNull
 *  androidx.annotation.Nullable
 *  com.example.data.StudioProjectEntity
 */
package com.example.blueprint;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.data.StudioProjectEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProjectLauncherJavaView
extends LinearLayout {
    private final EditText projectNameInput;
    private final EditText overlayTitleInput;
    private final LinearLayout projectCardsContainer;
    private final List<StudioProjectEntity> currentProjects = new ArrayList<StudioProjectEntity>();
    private OnProjectActionListener actionListener;

    public ProjectLauncherJavaView(@NonNull Context context) {
        this(context, null);
    }

    public ProjectLauncherJavaView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        this.setOrientation(1);
        this.setBackgroundColor(Color.parseColor((String)"#F1F5F9"));
        int pad = this.dpToPx(16);
        this.setPadding(pad, pad, pad, pad);
        TextView titleView = new TextView(context);
        titleView.setText((CharSequence)"STUDIO ERROR \u2014 Floating APK IDE");
        titleView.setTextColor(Color.parseColor((String)"#0F172A"));
        titleView.setTextSize(2, 18.0f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        this.addView((View)titleView, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        TextView subtitleView = new TextView(context);
        subtitleView.setText((CharSequence)"Create or open a project to design your Floating Mod Menu on a 100% blank canvas and compile to a signed APK.");
        subtitleView.setTextColor(Color.parseColor((String)"#475569"));
        subtitleView.setTextSize(2, 12.0f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = this.dpToPx(4);
        subLp.bottomMargin = this.dpToPx(12);
        this.addView((View)subtitleView, (ViewGroup.LayoutParams)subLp);
        LinearLayout createCard = new LinearLayout(context);
        createCard.setOrientation(1);
        int cardPad = this.dpToPx(12);
        createCard.setPadding(cardPad, cardPad, cardPad, cardPad);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(-1);
        cardBg.setCornerRadius((float)this.dpToPx(10));
        cardBg.setStroke(this.dpToPx(1), Color.parseColor((String)"#CBD5E1"));
        createCard.setBackground((Drawable)cardBg);
        this.projectNameInput = new EditText(context);
        this.projectNameInput.setHint((CharSequence)"Project Name (e.g., My Floating Utility)");
        this.projectNameInput.setSingleLine(true);
        this.projectNameInput.setTextSize(2, 13.0f);
        createCard.addView((View)this.projectNameInput, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        this.overlayTitleInput = new EditText(context);
        this.overlayTitleInput.setHint((CharSequence)"Floating Window Header Title (e.g., Floating Mod Panel)");
        this.overlayTitleInput.setSingleLine(true);
        this.overlayTitleInput.setTextSize(2, 13.0f);
        createCard.addView((View)this.overlayTitleInput, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        Button createBtn = new Button(context);
        createBtn.setText((CharSequence)"Create Blank Project");
        createBtn.setAllCaps(false);
        createBtn.setTextColor(-1);
        createBtn.setBackgroundColor(Color.parseColor((String)"#0288D1"));
        createBtn.setOnClickListener(v -> {
            String name = this.projectNameInput.getText().toString().trim();
            String overlay = this.overlayTitleInput.getText().toString().trim();
            if (name.isEmpty()) {
                name = "My Floating Utility";
            }
            if (overlay.isEmpty()) {
                overlay = "Floating Mod Panel";
            }
            if (this.actionListener != null) {
                this.actionListener.onCreateProject(name, overlay);
            }
        });
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, this.dpToPx(42));
        btnLp.topMargin = this.dpToPx(8);
        createCard.addView((View)createBtn, (ViewGroup.LayoutParams)btnLp);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.bottomMargin = this.dpToPx(14);
        this.addView((View)createCard, (ViewGroup.LayoutParams)cardLp);
        ScrollView scrollView = new ScrollView(context);
        scrollView.setOverScrollMode(2);
        this.projectCardsContainer = new LinearLayout(context);
        this.projectCardsContainer.setOrientation(1);
        scrollView.addView((View)this.projectCardsContainer, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        this.addView((View)scrollView, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, 0, 1.0f));
    }

    public void setOnProjectActionListener(@Nullable OnProjectActionListener listener) {
        this.actionListener = listener;
    }

    public void submitProjects(@NonNull List<StudioProjectEntity> projects) {
        this.currentProjects.clear();
        this.currentProjects.addAll(projects);
        this.projectCardsContainer.removeAllViews();
        Context context = this.getContext();
        for (StudioProjectEntity project : this.currentProjects) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(0);
            row.setGravity(16);
            int pad = this.dpToPx(12);
            row.setPadding(pad, pad, pad, pad);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(-1);
            bg.setCornerRadius((float)this.dpToPx(8));
            bg.setStroke(this.dpToPx(1), Color.parseColor((String)"#CBD5E1"));
            row.setBackground((Drawable)bg);
            LinearLayout infoCol = new LinearLayout(context);
            infoCol.setOrientation(1);
            TextView nameTv = new TextView(context);
            nameTv.setText((CharSequence)project.getName());
            nameTv.setTextColor(Color.parseColor((String)"#0F172A"));
            nameTv.setTextSize(2, 14.0f);
            nameTv.setTypeface(Typeface.DEFAULT_BOLD);
            infoCol.addView((View)nameTv);
            TextView metaTv = new TextView(context);
            metaTv.setText((CharSequence)String.format(Locale.US, "%s \u2022 %dx%d dp \u2022 AutoSize: %s", project.getOverlayTitle(), project.getCanvasWidthDp(), project.getCanvasHeightDp(), project.getAutoFixSize() ? "ON" : "OFF"));
            metaTv.setTextColor(Color.parseColor((String)"#64748B"));
            metaTv.setTextSize(2, 11.0f);
            infoCol.addView((View)metaTv);
            row.addView((View)infoCol, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
            Button openBtn = new Button(context);
            openBtn.setText((CharSequence)"Open");
            openBtn.setAllCaps(false);
            openBtn.setOnClickListener(v -> {
                if (this.actionListener != null) {
                    this.actionListener.onOpenProject(project);
                }
            });
            row.addView((View)openBtn, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-2, this.dpToPx(38)));
            Button delBtn = new Button(context);
            delBtn.setText((CharSequence)"Delete");
            delBtn.setAllCaps(false);
            delBtn.setOnClickListener(v -> {
                if (this.actionListener != null) {
                    this.actionListener.onDeleteProject(project);
                }
            });
            LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(-2, this.dpToPx(38));
            delLp.leftMargin = this.dpToPx(6);
            row.addView((View)delBtn, (ViewGroup.LayoutParams)delLp);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.bottomMargin = this.dpToPx(8);
            this.projectCardsContainer.addView((View)row, (ViewGroup.LayoutParams)rowLp);
        }
    }

    private int dpToPx(int dp) {
        return Math.round((float)dp * this.getResources().getDisplayMetrics().density);
    }

    public static interface OnProjectActionListener {
        public void onCreateProject(@NonNull String var1, @NonNull String var2);

        public void onOpenProject(@NonNull StudioProjectEntity var1);

        public void onDeleteProject(@NonNull StudioProjectEntity var1);
    }
}
