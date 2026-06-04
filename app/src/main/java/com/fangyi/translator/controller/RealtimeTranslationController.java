package com.fangyi.translator.controller;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.fangyi.translator.data.AppDatabase;
import com.fangyi.translator.data.TranslationCacheEntity;
import com.fangyi.translator.engine.SpeechEngine;
import com.fangyi.translator.engine.TranslationEngine;
import com.fangyi.translator.overlay.SubtitleOverlay;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Orchestrates the real-time subtitle workflow:
 * 1. Capture speech via Android SpeechRecognizer
 * 2. Translate recognized English text to Chinese
 * 3. Display bilingual subtitles in overlay
 */
public class RealtimeTranslationController {

    private final Context context;
    private final SpeechEngine speechEngine;
    private final SubtitleOverlay subtitleOverlay;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final TranslationEngine translator;

    private boolean isRunning = false;
    private String lastEnglishText = "";

    public RealtimeTranslationController(Context context) {
        this.context = context;
        this.speechEngine = new SpeechEngine(context);
        this.subtitleOverlay = new SubtitleOverlay(context);
        this.translator = TranslationEngine.getInstance();

        setupSpeechCallback();
    }

    private void setupSpeechCallback() {
        speechEngine.setCallback(new SpeechEngine.Callback() {
            @Override
            public void onPartialResult(String text) {
                if (!isRunning) return;
                lastEnglishText = text;
                translateAndDisplay(text);
            }

            @Override
            public void onFinalResult(String text) {
                if (!isRunning) return;
                lastEnglishText = text;
                translateAndDisplay(text);

                // Save to history
                executor.execute(() -> {
                    String translated = translateSync(text);
                    AppDatabase.getInstance().translationCacheDao().insert(
                            new TranslationCacheEntity(text, translated, "en", "zh")
                    );
                });
            }

            @Override
            public void onError(String error) {
                if (!isRunning) return;
                mainHandler.post(() -> subtitleOverlay.showNoAudio());
            }

            @Override
            public void onReadyForSpeech() {
                if (!isRunning) return;
                mainHandler.post(() -> subtitleOverlay.showListening());
            }
        });
    }

    private void translateAndDisplay(String englishText) {
        executor.execute(() -> {
            String translated = translateSync(englishText);
            mainHandler.post(() -> {
                subtitleOverlay.updateSubtitle(englishText, translated);
            });
        });
    }

    private String translateSync(String englishText) {
        // Check cache first
        try {
            TranslationCacheEntity cached = AppDatabase.getInstance()
                    .translationCacheDao().findByOriginal(englishText);
            if (cached != null) {
                return cached.translatedText;
            }
        } catch (Exception ignored) {}

        // Translate
        try {
            return com.google.android.gms.tasks.Tasks.await(
                    translator.translate(englishText)
            );
        } catch (Exception e) {
            return "[翻译中…]";
        }
    }

    public boolean start() {
        if (isRunning) return true;

        // Check if speech recognition is available
        if (speechEngine == null || !speechEngine.isAvailable()) {
            mainHandler.post(() -> subtitleOverlay.showError("语音识别不可用，请确认已安装Google语音服务"));
            return false;
        }

        isRunning = true;

        try {
            subtitleOverlay.show();
            subtitleOverlay.showListening();
            speechEngine.startListening();
            return true;
        } catch (SecurityException e) {
            isRunning = false;
            mainHandler.post(() -> subtitleOverlay.showError("请授予录音权限"));
            return false;
        } catch (Exception e) {
            isRunning = false;
            mainHandler.post(() -> subtitleOverlay.showError("启动失败: " + e.getMessage()));
            return false;
        }
    }

    public void stop() {
        isRunning = false;
        try {
            speechEngine.stopListening();
        } catch (Exception ignored) {}
        try {
            subtitleOverlay.dismiss();
        } catch (Exception ignored) {}
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void destroy() {
        stop();
        speechEngine.destroy();
    }
}
