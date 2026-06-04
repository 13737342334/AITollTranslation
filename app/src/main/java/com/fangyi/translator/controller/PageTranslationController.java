package com.fangyi.translator.controller;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.fangyi.translator.data.AppDatabase;
import com.fangyi.translator.data.TranslationCacheEntity;
import com.fangyi.translator.engine.OCREngine;
import com.fangyi.translator.engine.TranslationEngine;
import com.fangyi.translator.overlay.TranslationOverlay;
import com.fangyi.translator.service.ScreenCaptureService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class PageTranslationController {

    private static final String TAG = "PageTranslationCtrl";

    private final Context context;
    private final OCREngine ocrEngine;
    private final TranslationOverlay overlay;
    private final Executor executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean isTranslating = false;

    public PageTranslationController(Context context) {
        this.context = context;
        this.ocrEngine = new OCREngine();
        this.overlay = new TranslationOverlay(context);
    }

    /**
     * Called after CaptureActivity has obtained MediaProjection permission.
     * ScreenCaptureService will call back with the screenshot.
     */
    public void start() {
        if (isTranslating) return;
        isTranslating = true;

        overlay.show();
        overlay.showProgress();
        overlay.updateStatus("正在截取屏幕...");

        ScreenCaptureService.setScreenshotCallback(new ScreenCaptureService.ScreenshotCallback() {
            @Override
            public void onScreenshot(Bitmap bitmap) {
                mainHandler.post(() -> overlay.updateStatus("正在识别文字..."));
                processScreenshot(bitmap);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Screenshot error: " + error);
                mainHandler.post(() -> {
                    overlay.updateStatus("截图失败: " + error);
                    overlay.hideProgress();
                    overlay.scheduleAutoDismiss(5000);
                });
                isTranslating = false;
            }
        });
    }

    private void processScreenshot(Bitmap bitmap) {
        executor.execute(() -> {
            try {
                // Step 1: OCR
                List<OCREngine.TextBlock> enBlocks;
                try {
                    enBlocks = com.google.android.gms.tasks.Tasks.await(
                            ocrEngine.recognize(bitmap)
                    );
                } catch (Exception e) {
                    Log.e(TAG, "OCR failed, retrying with scaled bitmap", e);
                    Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
                            bitmap.getWidth() / 2, bitmap.getHeight() / 2, true);
                    bitmap.recycle();
                    try {
                        enBlocks = com.google.android.gms.tasks.Tasks.await(
                                ocrEngine.recognize(scaled)
                        );
                    } catch (Exception ex) {
                        Log.e(TAG, "OCR retry also failed", ex);
                        mainHandler.post(() -> {
                            overlay.updateStatus("未检测到英文文字");
                            overlay.hideProgress();
                            overlay.scheduleAutoDismiss(5000);
                        });
                        isTranslating = false;
                        return;
                    }
                }

                if (enBlocks == null || enBlocks.isEmpty()) {
                    mainHandler.post(() -> {
                        overlay.updateStatus("当前页面未检测到英文内容");
                        overlay.hideProgress();
                        overlay.scheduleAutoDismiss(5000);
                    });
                    isTranslating = false;
                    return;
                }

                // Step 2: Translate with cache
                int total = enBlocks.size();
                mainHandler.post(() -> overlay.updateStatus("正在翻译 " + total + " 段文字..."));

                List<TranslationOverlay.TranslationText> results = new ArrayList<>();
                TranslationEngine translator = TranslationEngine.getInstance();
                AppDatabase db = AppDatabase.getInstance();

                for (int i = 0; i < enBlocks.size(); i++) {
                    OCREngine.TextBlock block = enBlocks.get(i);
                    String translated;

                    TranslationCacheEntity cached = db.translationCacheDao()
                            .findByOriginal(block.text);
                    if (cached != null) {
                        translated = cached.translatedText;
                    } else {
                        try {
                            translated = com.google.android.gms.tasks.Tasks.await(
                                    translator.translate(block.text)
                            );
                            db.translationCacheDao().insert(
                                    new TranslationCacheEntity(block.text, translated, "en", "zh")
                            );
                        } catch (Exception e) {
                            translated = "[…]";
                        }
                    }

                    final int progress = i + 1;
                    mainHandler.post(() -> overlay.updateStatus("翻译中... (" + progress + "/" + total + ")"));

                    results.add(new TranslationOverlay.TranslationText(
                            translated, block.bounds, 14f
                    ));
                }

                db.translationCacheDao().trimTo(500);

                // Step 3: Display
                mainHandler.post(() -> {
                    overlay.showTranslationBlocks(results);
                    // 不自动消失，等用户点击关闭
                });
                isTranslating = false;

            } catch (Exception e) {
                Log.e(TAG, "Translation error", e);
                mainHandler.post(() -> {
                    overlay.updateStatus("翻译出错: " + e.getMessage());
                    overlay.scheduleAutoDismiss(5000);
                });
                isTranslating = false;
            }
        });
    }

    public void destroy() {
        ocrEngine.close();
        overlay.dismiss();
    }
}
