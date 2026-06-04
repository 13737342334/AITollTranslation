package com.fangyi.translator.controller;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.fangyi.translator.data.AppDatabase;
import com.fangyi.translator.data.TranslationCacheEntity;
import com.fangyi.translator.engine.OCREngine;
import com.fangyi.translator.engine.TranslationEngine;
import com.fangyi.translator.overlay.TranslationOverlay;
import com.fangyi.translator.service.ScreenCaptureService;
import com.fangyi.translator.util.PreferencesHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Orchestrates the page translation workflow:
 * 1. Request MediaProjection permission from user
 * 2. Take screenshot
 * 3. OCR recognize text blocks
 * 4. Translate each block (with cache lookup)
 * 5. Display translation overlay
 */
public class PageTranslationController {

    private final Context context;
    private final OCREngine ocrEngine;
    private final TranslationOverlay overlay;
    private final Executor executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Activity requestingActivity;
    private boolean isTranslating = false;

    public PageTranslationController(Context context) {
        this.context = context;
        this.ocrEngine = new OCREngine();
        this.overlay = new TranslationOverlay(context);
    }

    /**
     * Start the translation workflow. If MediaProjection hasn't been set up,
     * will need an Activity to launch the permission intent.
     */
    public void start() {
        if (isTranslating) return;
        isTranslating = true;

        overlay.show();
        overlay.showProgress();
        overlay.updateStatus("准备截取屏幕...");

        // Set up screenshot callback
        ScreenCaptureService.setScreenshotCallback(new ScreenCaptureService.ScreenshotCallback() {
            @Override
            public void onScreenshot(Bitmap bitmap) {
                overlay.updateStatus("正在识别文字...");
                processScreenshot(bitmap);
            }

            @Override
            public void onError(String error) {
                overlay.updateStatus("截图失败: " + error);
                overlay.scheduleAutoDismiss(5000);
                isTranslating = false;
            }
        });

        ScreenCaptureService.takeScreenshot(context);
    }

    private void processScreenshot(Bitmap bitmap) {
        executor.execute(() -> {
            try {
                // Step 1: OCR
                List<OCREngine.TextBlock> enBlocks = null;
                try {
                    enBlocks = com.google.android.gms.tasks.Tasks.await(
                            ocrEngine.recognize(bitmap)
                    );
                } catch (Exception e) {
                    // Try again with lower resolution
                    Bitmap scaled = Bitmap.createScaledBitmap(bitmap, bitmap.getWidth() / 2,
                            bitmap.getHeight() / 2, true);
                    try {
                        enBlocks = com.google.android.gms.tasks.Tasks.await(
                                ocrEngine.recognize(scaled)
                        );
                    } catch (Exception ex) {
                        mainHandler.post(() -> {
                            overlay.updateStatus("未检测到英文文字");
                            overlay.hideProgress();
                            overlay.scheduleAutoDismiss(5000);
                            isTranslating = false;
                        });
                        return;
                    }
                }

                if (enBlocks == null || enBlocks.isEmpty()) {
                    mainHandler.post(() -> {
                        overlay.updateStatus("当前页面未检测到英文内容");
                        overlay.hideProgress();
                        overlay.scheduleAutoDismiss(5000);
                        isTranslating = false;
                    });
                    return;
                }

                // Step 2: Check cache and translate
                int total = enBlocks.size();
                overlay.updateStatus("正在翻译 " + total + " 段文字...");

                List<TranslationOverlay.TranslationText> results = new ArrayList<>();
                TranslationEngine translator = TranslationEngine.getInstance();
                AppDatabase db = AppDatabase.getInstance();

                for (int i = 0; i < enBlocks.size(); i++) {
                    OCREngine.TextBlock block = enBlocks.get(i);
                    String translated;

                    // Try cache first
                    TranslationCacheEntity cached = db.translationCacheDao()
                            .findByOriginal(block.text);
                    if (cached != null) {
                        translated = cached.translatedText;
                    } else {
                        try {
                            translated = com.google.android.gms.tasks.Tasks.await(
                                    translator.translate(block.text)
                            );
                            // Save to cache
                            db.translationCacheDao().insert(
                                    new TranslationCacheEntity(block.text, translated, "en", "zh")
                            );
                        } catch (Exception e) {
                            translated = "[…]";  // placeholder for failed translations
                        }
                    }

                    int finalI = i;
                    mainHandler.post(() -> {
                        overlay.updateStatus("翻译中... (" + (finalI + 1) + "/" + total + ")");
                    });

                    results.add(new TranslationOverlay.TranslationText(
                            translated, block.bounds, 14f
                    ));
                }

                // Trim cache to last 500 entries
                db.translationCacheDao().trimTo(500);

                // Step 3: Display
                mainHandler.post(() -> {
                    overlay.showTranslationBlocks(results);
                    overlay.scheduleAutoDismiss(3 * 60 * 1000); // 3 min auto dismiss
                    isTranslating = false;
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    overlay.updateStatus("翻译出错: " + e.getMessage());
                    overlay.scheduleAutoDismiss(5000);
                    isTranslating = false;
                });
            }
        });
    }

    public void destroy() {
        ocrEngine.close();
        overlay.dismiss();
    }
}
