/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  android.text.Editable
 *  android.text.TextWatcher
 *  android.util.AttributeSet
 *  android.view.LayoutInflater
 *  android.view.ViewGroup
 *  android.widget.Button
 *  android.widget.EditText
 *  android.widget.ImageButton
 *  android.widget.LinearLayout
 *  android.widget.TextView
 *  androidx.annotation.NonNull
 *  androidx.annotation.Nullable
 *  com.example.R$id
 *  com.example.R$layout
 *  com.example.data.CanvasComponentEntity
 */
package com.example.blueprint;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.R;
import com.example.data.CanvasComponentEntity;

public class BottomPropertyInspectorView
extends LinearLayout {
    private final TextView tvWidgetId;
    private final Button btnStateToggle;
    private final EditText etLabel;
    private final EditText etWidth;
    private final EditText etHeight;
    private final EditText etTargetFilePath;
    private final EditText etByteOffsetHex;
    private final EditText etOnPayloadHex;
    private final EditText etOffPayloadHex;
    private final EditText etBgColorHex;
    private final EditText etTextColorHex;
    private CanvasComponentEntity boundComponent;
    private OnInspectorPropertyChangeListener propertyListener;
    private boolean isBinding = false;

    public BottomPropertyInspectorView(@NonNull Context context) {
        this(context, null);
    }

    public BottomPropertyInspectorView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        this.setOrientation(1);
        LayoutInflater.from((Context)context).inflate(R.layout.inspector_dock, (ViewGroup)this, true);
        this.tvWidgetId = (TextView)this.findViewById(R.id.tv_inspector_widget_id);
        this.btnStateToggle = (Button)this.findViewById(R.id.btn_inspector_state_toggle);
        this.etLabel = (EditText)this.findViewById(R.id.et_component_label);
        this.etWidth = (EditText)this.findViewById(R.id.et_component_width);
        this.etHeight = (EditText)this.findViewById(R.id.et_component_height);
        this.etTargetFilePath = (EditText)this.findViewById(R.id.et_target_file_path);
        this.etByteOffsetHex = (EditText)this.findViewById(R.id.et_byte_offset_hex);
        this.etOnPayloadHex = (EditText)this.findViewById(R.id.et_on_payload_hex);
        this.etOffPayloadHex = (EditText)this.findViewById(R.id.et_off_payload_hex);
        this.etBgColorHex = (EditText)this.findViewById(R.id.et_bg_color_hex);
        this.etTextColorHex = (EditText)this.findViewById(R.id.et_text_color_hex);
        ImageButton btnDuplicate = (ImageButton)this.findViewById(R.id.btn_inspector_duplicate);
        ImageButton btnDelete = (ImageButton)this.findViewById(R.id.btn_inspector_delete);
        ImageButton btnSaveClose = (ImageButton)this.findViewById(R.id.btn_inspector_save_close);
        Button chipAutoFix = (Button)this.findViewById(R.id.chip_auto_fix);
        Button chipInject = (Button)this.findViewById(R.id.chip_inject);
        TextWatcher autoSaveWatcher = new TextWatcher(){

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            public void afterTextChanged(Editable s) {
                if (!BottomPropertyInspectorView.this.isBinding) {
                    BottomPropertyInspectorView.this.commitCurrentFieldValues();
                }
            }
        };
        this.etLabel.addTextChangedListener(autoSaveWatcher);
        this.etWidth.addTextChangedListener(autoSaveWatcher);
        this.etHeight.addTextChangedListener(autoSaveWatcher);
        this.etTargetFilePath.addTextChangedListener(autoSaveWatcher);
        this.etByteOffsetHex.addTextChangedListener(autoSaveWatcher);
        this.etOnPayloadHex.addTextChangedListener(autoSaveWatcher);
        this.etOffPayloadHex.addTextChangedListener(autoSaveWatcher);
        this.etBgColorHex.addTextChangedListener(autoSaveWatcher);
        this.etTextColorHex.addTextChangedListener(autoSaveWatcher);
        this.btnStateToggle.setOnClickListener(v -> {
            if (this.boundComponent != null && this.propertyListener != null) {
                this.propertyListener.onTestTriggerWrite(this.boundComponent);
            }
        });
        btnDuplicate.setOnClickListener(v -> {
            if (this.boundComponent != null && this.propertyListener != null) {
                this.propertyListener.onDuplicateComponent(this.boundComponent);
            }
        });
        btnDelete.setOnClickListener(v -> {
            if (this.boundComponent != null && this.propertyListener != null) {
                this.propertyListener.onDeleteComponent(this.boundComponent);
            }
        });
        btnSaveClose.setOnClickListener(v -> {
            this.commitCurrentFieldValues();
            if (this.propertyListener != null) {
                this.propertyListener.onCloseInspector();
            }
        });
        chipAutoFix.setOnClickListener(v -> {
            if (this.propertyListener != null) {
                this.propertyListener.onToggleAutoFixSize();
            }
        });
        chipInject.setOnClickListener(v -> this.etByteOffsetHex.requestFocus());
    }

    public void setOnInspectorPropertyChangeListener(@Nullable OnInspectorPropertyChangeListener listener) {
        this.propertyListener = listener;
    }

    public void bindComponent(@NonNull CanvasComponentEntity component) {
        this.isBinding = true;
        try {
            this.boundComponent = component;
            this.tvWidgetId.setText((CharSequence)("widget" + component.getId() + " (" + component.getLabel() + ")"));
            boolean isOn = "1".equals(component.getCurrentValue()) || "true".equalsIgnoreCase(component.getCurrentValue());
            this.btnStateToggle.setText((CharSequence)(isOn ? "ON" : "OFF"));
            this.etLabel.setText((CharSequence)component.getLabel());
            this.etWidth.setText((CharSequence)String.valueOf(component.getWidthDp()));
            this.etHeight.setText((CharSequence)String.valueOf(component.getHeightDp()));
            this.etTargetFilePath.setText((CharSequence)component.getTargetFilePath());
            this.etByteOffsetHex.setText((CharSequence)component.getByteOffsetHex());
            this.etOnPayloadHex.setText((CharSequence)component.getOnPayloadHex());
            this.etOffPayloadHex.setText((CharSequence)component.getOffPayloadHex());
            this.etBgColorHex.setText((CharSequence)component.getBgColorHex());
            this.etTextColorHex.setText((CharSequence)component.getTextColorHex());
        }
        finally {
            this.isBinding = false;
        }
    }

    public void commitCurrentFieldValues() {
        CanvasComponentEntity updated;
        if (this.boundComponent == null || this.propertyListener == null) {
            return;
        }
        int w = this.parseSafeInt(this.etWidth.getText().toString(), this.boundComponent.getWidthDp(), 40, 380);
        int h = this.parseSafeInt(this.etHeight.getText().toString(), this.boundComponent.getHeightDp(), 28, 320);
        this.boundComponent = updated = new CanvasComponentEntity(this.boundComponent.getId(), this.boundComponent.getProjectId(), this.boundComponent.getType(), this.etLabel.getText().toString().trim().isEmpty() ? this.boundComponent.getLabel() : this.etLabel.getText().toString().trim(), this.boundComponent.getPosXDp(), this.boundComponent.getPosYDp(), w, h, this.etBgColorHex.getText().toString().trim(), this.etTextColorHex.getText().toString().trim(), this.boundComponent.getBgImagePath(), this.boundComponent.getCustomImagePath(), this.boundComponent.getSoundTrigger(), this.boundComponent.getCustomSoundPath(), this.boundComponent.getOffSoundTrigger(), this.boundComponent.getOffCustomSoundPath(), this.etTargetFilePath.getText().toString().trim(), this.etByteOffsetHex.getText().toString().trim(), this.etOnPayloadHex.getText().toString().trim(), this.etOffPayloadHex.getText().toString().trim(), this.boundComponent.getSliderMax(), this.boundComponent.getCurrentValue(), this.boundComponent.getLinkUrl());
        this.propertyListener.onUpdateComponent(updated);
    }

    private int parseSafeInt(String raw, int fallback, int min, int max) {
        try {
            int val = Integer.parseInt(raw.trim());
            return Math.max(min, Math.min(max, val));
        }
        catch (Exception e) {
            return fallback;
        }
    }

    public static interface OnInspectorPropertyChangeListener {
        public void onUpdateComponent(@NonNull CanvasComponentEntity var1);

        public void onToggleAutoFixSize();

        public void onDuplicateComponent(@NonNull CanvasComponentEntity var1);

        public void onDeleteComponent(@NonNull CanvasComponentEntity var1);

        public void onTestTriggerWrite(@NonNull CanvasComponentEntity var1);

        public void onCloseInspector();
    }
}
