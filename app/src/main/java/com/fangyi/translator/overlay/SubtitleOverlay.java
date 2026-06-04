package com.fangyi.translator.overlay;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.fangyi.translator.R;

/**
 * Bottom subtitle bar overlay. Shows bilingual subtitles (EN top, ZH bottom).
 * Supports drag to reposition. Shows listening indicator when no speech detected.
 */
public class SubtitleOverlay {

    private final Context context;
    private final WindowManager windowManager;
    private View overlayView;
    private TextView tvEnglish, tvChinese;
    private ProgressBar progressBar;
    private WindowManager.LayoutParams overlayParams;
    private boolean isShowing = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private float initialTouchX, initialTouchY;
    private int initialX, initialY;

    public SubtitleOverlay(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void show() {
        if (isShowing) return;

        overlayView = LayoutInflater.from(context).inflate(R.layout.overlay_subtitle, null);
        tvEnglish = overlayView.findViewById(R.id.tv_subtitle_en);
        tvChinese = overlayView.findViewById(R.id.tv_subtitle_zh);
        progressBar = overlayView.findViewById(R.id.progress_subtitle);

        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        overlayParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        overlayParams.y = dpToPx(60);

        // Drag to move
        overlayView.setOnTouchListener(new View.OnTouchListener() {
            private static final int DRAG_THRESHOLD = 15;
            private float downX, downY;
            private boolean isDragging;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        initialX = overlayParams.x;
                        initialY = overlayParams.y;
                        downX = event.getRawX();
                        downY = event.getRawY();
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = Math.abs(event.getRawX() - downX);
                        float dy = Math.abs(event.getRawY() - downY);
                        if (dx > DRAG_THRESHOLD || dy > DRAG_THRESHOLD) {
                            isDragging = true;
                        }
                        if (isDragging) {
                            overlayParams.gravity = Gravity.TOP | Gravity.START;
                            overlayParams.x = (int) (initialX + event.getRawX() - initialTouchX);
                            overlayParams.y = (int) (initialY + event.getRawY() - initialTouchY);
                            windowManager.updateViewLayout(overlayView, overlayParams);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        return isDragging;
                }
                return false;
            }
        });

        try {
            windowManager.addView(overlayView, overlayParams);
            isShowing = true;
        } catch (SecurityException e) {
            overlayView = null;
        } catch (Exception e) {
            overlayView = null;
        }
    }

    public void updateSubtitle(String english, String chinese) {
        handler.post(() -> {
            if (tvEnglish != null) {
                tvEnglish.setText(english != null && !english.isEmpty()
                        ? english : "…");
            }
            if (tvChinese != null) {
                tvChinese.setText(chinese != null && !chinese.isEmpty()
                        ? chinese : "");
            }
            if (progressBar != null) {
                progressBar.setVisibility(View.INVISIBLE);
            }
        });
    }

    public void showListening() {
        handler.post(() -> {
            if (tvEnglish != null) tvEnglish.setText("正在收听…");
            if (tvChinese != null) tvChinese.setText("");
            if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        });
    }

    public void showNoAudio() {
        handler.post(() -> {
            if (tvEnglish != null) tvEnglish.setText("未检测到音频");
            if (tvChinese != null) tvChinese.setText("请确保正在播放英文视频");
            if (progressBar != null) progressBar.setVisibility(View.INVISIBLE);
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

    private int dpToPx(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }
}
