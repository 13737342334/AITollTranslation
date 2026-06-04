package com.fangyi.translator.overlay;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.fangyi.translator.R;
import com.fangyi.translator.engine.OCREngine;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen overlay that displays translated text blocks at their original positions.
 * Touch events pass through, tap anywhere to dismiss after 3min auto-timeout.
 */
public class TranslationOverlay {

    private final Context context;
    private final WindowManager windowManager;
    private View overlayView;
    private FrameLayout textsContainer;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private WindowManager.LayoutParams overlayParams;
    private boolean isShowing = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable autoDismissRunnable;

    public TranslationOverlay(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void show() {
        if (isShowing) return;

        overlayView = LayoutInflater.from(context).inflate(R.layout.overlay_translation, null);
        textsContainer = overlayView.findViewById(R.id.translation_texts_container);
        progressBar = overlayView.findViewById(R.id.progress_translating);
        tvStatus = overlayView.findViewById(R.id.tv_translating_status);

        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
        );
        overlayParams.gravity = Gravity.TOP | Gravity.START;

        // Tap to dismiss - make touchable temporarily when tapped
        overlayView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                // First tap: make overlay touchable so second tap can dismiss
                if ((overlayParams.flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0) {
                    overlayParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    windowManager.updateViewLayout(overlayView, overlayParams);
                    return false;
                }
            }
            return false;
        });

        overlayView.setOnClickListener(v -> dismiss());

        windowManager.addView(overlayView, overlayParams);
        isShowing = true;
    }

    public void showTranslationBlocks(List<TranslationText> translations) {
        handler.post(() -> {
            hideProgress();
            textsContainer.removeAllViews();

            for (TranslationText tt : translations) {
                TextView tv = new TextView(context);
                tv.setText(tt.translatedText);
                tv.setTextSize(tt.textSize > 0 ? tt.textSize : 12);
                tv.setTextColor(0xFFFFEB3B); // yellow
                tv.setShadowLayer(2f, 1f, 1f, 0xFF000000);

                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );
                Rect bounds = tt.bounds;
                params.leftMargin = bounds.left;
                params.topMargin = bounds.top;
                params.width = bounds.width();
                params.height = bounds.height();
                tv.setLayoutParams(params);

                tv.setMaxWidth(bounds.width());
                tv.setMaxLines(3);

                textsContainer.addView(tv);
            }
        });
    }

    public void showProgress() {
        handler.post(() -> {
            if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
            if (tvStatus != null) tvStatus.setVisibility(View.VISIBLE);
        });
    }

    public void hideProgress() {
        handler.post(() -> {
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            if (tvStatus != null) tvStatus.setVisibility(View.GONE);
        });
    }

    public void updateStatus(String text) {
        handler.post(() -> {
            if (tvStatus != null) tvStatus.setText(text);
        });
    }

    public void dismiss() {
        if (!isShowing) return;

        handler.post(() -> {
            try {
                windowManager.removeView(overlayView);
            } catch (IllegalArgumentException ignored) {}
            overlayView = null;
            isShowing = false;
        });

        if (autoDismissRunnable != null) {
            handler.removeCallbacks(autoDismissRunnable);
        }

        // Restore touch-through
        if (overlayParams != null) {
            overlayParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
    }

    public void scheduleAutoDismiss(long delayMs) {
        if (autoDismissRunnable != null) {
            handler.removeCallbacks(autoDismissRunnable);
        }
        autoDismissRunnable = this::dismiss;
        handler.postDelayed(autoDismissRunnable, delayMs);
    }

    public boolean isShowing() {
        return isShowing;
    }

    private int getOverlayType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        return WindowManager.LayoutParams.TYPE_PHONE;
    }

    public static class TranslationText {
        public final String translatedText;
        public final Rect bounds;
        public final float textSize;

        public TranslationText(String translatedText, Rect bounds, float textSize) {
            this.translatedText = translatedText;
            this.bounds = bounds;
            this.textSize = textSize;
        }
    }
}
