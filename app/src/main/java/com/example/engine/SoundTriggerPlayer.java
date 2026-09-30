/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  android.media.MediaPlayer
 *  android.media.ToneGenerator
 *  android.net.Uri
 *  android.os.Handler
 *  android.os.Looper
 *  android.view.View
 */
package com.example.engine;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import java.io.File;

public class SoundTriggerPlayer {
    public static final String SOUND_NONE = "NONE";
    public static final String SOUND_CLICK = "SYSTEM_CLICK";
    public static final String SOUND_BEEP = "DIGITAL_BEEP";
    public static final String SOUND_CONFIRM = "CONFIRM_TONE";
    public static final String SOUND_POP = "SWITCH_POP";
    public static final String SOUND_ALERT = "ALERT_PULSE";
    public static final String SOUND_CUSTOM_FILE = "CUSTOM_FILE";

    public static void playSoundTrigger(Context context, View sourceView, String soundType, String customSoundPath) {
        if (soundType == null || SOUND_NONE.equalsIgnoreCase(soundType)) {
            return;
        }
        try {
            int durationMs;
            int toneType;
            MediaPlayer mp;
            File audioFile;
            if (SOUND_CUSTOM_FILE.equalsIgnoreCase(soundType) && customSoundPath != null && !customSoundPath.trim().isEmpty() && (audioFile = new File(customSoundPath.trim())).exists() && (mp = MediaPlayer.create((Context)context, (Uri)Uri.fromFile((File)audioFile))) != null) {
                mp.setOnCompletionListener(MediaPlayer::release);
                mp.start();
                return;
            }
            if (SOUND_CLICK.equalsIgnoreCase(soundType) && sourceView != null) {
                sourceView.playSoundEffect(0);
            }
            switch (soundType.toUpperCase()) {
                case "DIGITAL_BEEP": {
                    toneType = 24;
                    durationMs = 70;
                    break;
                }
                case "CONFIRM_TONE": {
                    toneType = 25;
                    durationMs = 110;
                    break;
                }
                case "SWITCH_POP": {
                    toneType = 28;
                    durationMs = 60;
                    break;
                }
                case "ALERT_PULSE": {
                    toneType = 93;
                    durationMs = 140;
                    break;
                }
                default: {
                    toneType = 33;
                    durationMs = 45;
                }
            }
            ToneGenerator toneGen = new ToneGenerator(5, 80);
            toneGen.startTone(toneType, durationMs);
            new Handler(Looper.getMainLooper()).postDelayed(() -> ((ToneGenerator)toneGen).release(), (long)durationMs + 60L);
        }
        catch (Exception exception) {
            // empty catch block
        }
    }
}
