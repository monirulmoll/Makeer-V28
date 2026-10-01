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
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.View;
import java.io.File;
import java.io.FileInputStream;
import java.util.Locale;

public class SoundTriggerPlayer {
    public static final String SOUND_NONE = "NONE";
    public static final String SOUND_CLICK = "SYSTEM_CLICK";
    public static final String SOUND_BEEP = "DIGITAL_BEEP";
    public static final String SOUND_CONFIRM = "CONFIRM_TONE";
    public static final String SOUND_POP = "SWITCH_POP";
    public static final String SOUND_ALERT = "ALERT_PULSE";
    public static final String SOUND_LASER = "LASER_ZAP";
    public static final String SOUND_POWER_UP = "POWER_UP";
    public static final String SOUND_POWER_DOWN = "POWER_DOWN";
    public static final String SOUND_VOICE_ON = "VOICE_ON";
    public static final String SOUND_VOICE_OFF = "VOICE_OFF";
    public static final String SOUND_VOICE_HACK_ON = "VOICE_HACK_ON";
    public static final String SOUND_VOICE_HACK_OFF = "VOICE_HACK_OFF";
    public static final String SOUND_VOICE_MOD_ON = "VOICE_MOD_ON";
    public static final String SOUND_VOICE_MOD_OFF = "VOICE_MOD_OFF";
    public static final String SOUND_VOICE_APPLIED = "VOICE_APPLIED";
    public static final String SOUND_VOICE_RESTORED = "VOICE_RESTORED";
    public static final String SOUND_CUSTOM_FILE = "CUSTOM_FILE";

    private static TextToSpeech ttsEngine = null;
    private static boolean ttsReady = false;
    private static String pendingSpeechText = null;
    private static MediaPlayer activeMediaPlayer = null;

    private static synchronized void releaseActiveMediaPlayer() {
        if (activeMediaPlayer != null) {
            try {
                if (activeMediaPlayer.isPlaying()) {
                    activeMediaPlayer.stop();
                }
            } catch (Throwable ignored) {
            }
            try {
                activeMediaPlayer.release();
            } catch (Throwable ignored) {
            }
            activeMediaPlayer = null;
        }
    }

    private static synchronized boolean playCustomAudioFile(Context context, File audioFile) {
        if (audioFile == null || !audioFile.exists() || audioFile.length() <= 0L) {
            return false;
        }
        releaseActiveMediaPlayer();
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
            );
            try (FileInputStream fis = new FileInputStream(audioFile)) {
                mp.setDataSource(fis.getFD());
                mp.prepare();
            }
            activeMediaPlayer = mp;
            mp.setOnCompletionListener(player -> {
                synchronized (SoundTriggerPlayer.class) {
                    try {
                        player.release();
                    } catch (Throwable ignored) {
                    }
                    if (activeMediaPlayer == player) {
                        activeMediaPlayer = null;
                    }
                }
            });
            mp.setOnErrorListener((player, what, extra) -> {
                synchronized (SoundTriggerPlayer.class) {
                    try {
                        player.release();
                    } catch (Throwable ignored) {
                    }
                    if (activeMediaPlayer == player) {
                        activeMediaPlayer = null;
                    }
                }
                return true;
            });
            mp.start();
            return true;
        } catch (Throwable firstErr) {
            if (context != null) {
                try {
                    MediaPlayer fallbackMp = MediaPlayer.create(context, Uri.fromFile(audioFile));
                    if (fallbackMp != null) {
                        activeMediaPlayer = fallbackMp;
                        fallbackMp.setOnCompletionListener(player -> {
                            synchronized (SoundTriggerPlayer.class) {
                                try {
                                    player.release();
                                } catch (Throwable ignored) {
                                }
                                if (activeMediaPlayer == player) {
                                    activeMediaPlayer = null;
                                }
                            }
                        });
                        fallbackMp.start();
                        return true;
                    }
                } catch (Throwable ignored) {
                }
            }
            return false;
        }
    }

    private static synchronized void speakBuiltInVoice(Context context, final String phrase) {
        if (context == null || phrase == null || phrase.trim().isEmpty()) {
            return;
        }
        try {
            if (ttsEngine == null) {
                pendingSpeechText = phrase;
                final Context appCtx = context.getApplicationContext() != null ? context.getApplicationContext() : context;
                ttsEngine = new TextToSpeech(appCtx, status -> {
                    if (status == TextToSpeech.SUCCESS && ttsEngine != null) {
                        try {
                            ttsEngine.setAudioAttributes(
                                    new AudioAttributes.Builder()
                                            .setUsage(AudioAttributes.USAGE_MEDIA)
                                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                            .build()
                            );
                            ttsEngine.setLanguage(Locale.US);
                            ttsEngine.setSpeechRate(1.0f);
                            ttsEngine.setPitch(1.0f);
                            ttsReady = true;
                            if (pendingSpeechText != null) {
                                ttsEngine.speak(pendingSpeechText, TextToSpeech.QUEUE_FLUSH, null, "studio_voice_trigger");
                                pendingSpeechText = null;
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                });
            } else if (ttsReady) {
                ttsEngine.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "studio_voice_trigger");
            } else {
                pendingSpeechText = phrase;
            }
        } catch (Throwable ignored) {
        }
    }

    public static void playSoundTrigger(Context context, View sourceView, String soundType, String customSoundPath) {
        String cleanType = soundType != null ? soundType.trim() : "";
        String cleanCustomPath = customSoundPath != null ? customSoundPath.trim() : "";

        // 1. If a custom voice file is selected, play ONLY the custom voice file (no beep-boop)
        if (!cleanCustomPath.isEmpty()) {
            File audioFile = new File(cleanCustomPath);
            boolean shouldPlayCustom = SOUND_CUSTOM_FILE.equalsIgnoreCase(cleanType)
                    || cleanType.isEmpty()
                    || SOUND_NONE.equalsIgnoreCase(cleanType);
            if (shouldPlayCustom && playCustomAudioFile(context, audioFile)) {
                return;
            }
        }

        if (SOUND_NONE.equalsIgnoreCase(cleanType)) {
            return;
        }

        if (SOUND_CUSTOM_FILE.equalsIgnoreCase(cleanType)) {
            if (!cleanCustomPath.isEmpty()) {
                playCustomAudioFile(context, new File(cleanCustomPath));
            }
            return;
        }

        try {
            String upperType = !cleanType.isEmpty() ? cleanType.toUpperCase(Locale.US) : "VOICE_ON";

            // 2. Built-in Voice Triggers — Strictly speak voice ONLY (ZERO beep/boop ToneGenerator!)
            switch (upperType) {
                case "VOICE_ON":
                    speakBuiltInVoice(context, "Option Activated");
                    return;
                case "VOICE_OFF":
                    speakBuiltInVoice(context, "Option Deactivated");
                    return;
                case "VOICE_HACK_ON":
                    speakBuiltInVoice(context, "Hack Activated");
                    return;
                case "VOICE_HACK_OFF":
                    speakBuiltInVoice(context, "Hack Deactivated");
                    return;
                case "VOICE_MOD_ON":
                    speakBuiltInVoice(context, "Mod Enabled");
                    return;
                case "VOICE_MOD_OFF":
                    speakBuiltInVoice(context, "Mod Disabled");
                    return;
                case "ACTIVATE":
                case "VOICE_APPLIED":
                    speakBuiltInVoice(context, "Target File Patched");
                    return;
                case "DEACTIVATE":
                case "VOICE_RESTORED":
                    speakBuiltInVoice(context, "Original Restored");
                    return;
                default:
                    break;
            }

            // 3. Only the 4 distinct Click / Beep sounds reach here
            if ((SOUND_CLICK.equalsIgnoreCase(upperType) || "CLICK".equalsIgnoreCase(upperType)) && sourceView != null) {
                sourceView.playSoundEffect(0);
            }

            int toneType;
            int durationMs;
            switch (upperType) {
                case "SYSTEM_CLICK":
                case "CLICK": {
                    toneType = ToneGenerator.TONE_PROP_PROMPT;
                    durationMs = 35;
                    break;
                }
                case "SWITCH_POP":
                case "POP": {
                    toneType = ToneGenerator.TONE_CDMA_PIP;
                    durationMs = 45;
                    break;
                }
                case "CONFIRM_TONE":
                case "CONFIRM": {
                    toneType = ToneGenerator.TONE_PROP_BEEP;
                    durationMs = 75;
                    break;
                }
                case "DIGITAL_BEEP":
                case "BEEP":
                default: {
                    toneType = ToneGenerator.TONE_PROP_BEEP2;
                    durationMs = 95;
                    break;
                }
            }
            ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 90);
            toneGen.startTone(toneType, durationMs);
            new Handler(Looper.getMainLooper()).postDelayed(toneGen::release, (long) durationMs + 75L);
        } catch (Exception ignored) {
        }
    }
}
