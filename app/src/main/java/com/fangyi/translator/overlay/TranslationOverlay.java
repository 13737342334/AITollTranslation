package com.fangyi.translator.overlay;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.fangyi.translator.R;

import java.util.List;

public class TranslationOverlay {

    private static final String TAG = "TranslationOverlay";
    private final Context context;
    private final WindowManager windowManager;
    private View overlayView;
    private FrameLayout textsContainer;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private TextView btnClose;
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
        btnClose = overlayView.findViewById(R.id.btn_close_overlay);

        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );

        // Click on the semi-transparent background to dismiss
        overlayView.setOnClickListener(v -> dismiss());

        // Close button - more obvious way to dismiss
        if (btnClose != null) {
            btnClose.setVisibility(View.VISIBLE);
            btnClose.setOnClickListener(v -> dismiss());
        }

        try {
            windowManager.addView(overlayView, overlayParams);
            isShowing = true;
        } catch (SecurityException e) {
            Log.e(TAG, "Overlay permission denied", e);
        } catch (Exception e) {
            Log.e(TAG, "Failed to show overlay", e);
        }
    }

    public void showTranslationBlocks(List<TranslationText> translations) {
        handler.post(() -> {
            hideProgress();
            textsContainer.removeAllViews();

            if (translations == null || translations.isEmpty()) {
                return;
            }

            for (TranslationText tt : translations) {
                TextView tv = new TextView(context);
                tv.setText(tt.translatedText);
                tv.setTextSize(Math.max(tt.textSize, 10f));
                tv.setTextColor(0xFFFFEB3B);
                tv.setShadowLayer(3f, 1f, 1f, 0xFF000000);
                tv.setMaxLines(4);
                tv.setEllipsize(android.text.TextUtils.TruncateAt.END);

                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );
                Rect bounds = tt.bounds;
                params.leftMargin = Math.max(0, bounds.left);
                params.topMargin = Math.max(0, bounds.top);
                tv.setLayoutParams(params);
                tv.setMaxWidth(Math.max(bounds.width(), 100));

                // Individual text blocks pass clicks through to dismiss
                tv.setClickable(false);
                tv.setFocusable(false);

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
            if (tvStatus != null) {
                tvStatus.setText(text);
                tvStatus.setVisibility(View.VISIBLE);
            }
        });
    }

    public void dismiss() {
        if (!isShowing) return;

        handler.post(() -> {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {}
            overlayView = null;
            isShowing = false;
        });

        if (autoDismissRunnable != null) {
            handler.removeCallbacks(autoDismissRunnable);
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
