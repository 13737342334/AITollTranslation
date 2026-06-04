package com.fangyi.translator.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.fangyi.translator.App;
import com.fangyi.translator.CaptureActivity;
import com.fangyi.translator.MainActivity;
import com.fangyi.translator.R;
import com.fangyi.translator.controller.PageTranslationController;
import com.fangyi.translator.controller.RealtimeTranslationController;

public class FloatingBallService extends Service {

    private static final String TAG = "FloatingBallService";
    private static boolean running = false;

    private WindowManager windowManager;
    private View floatingBallView;
    private View popupMenuView;
    private WindowManager.LayoutParams ballParams;
    private WindowManager.LayoutParams menuParams;

    private PageTranslationController pageTranslationController;
    private RealtimeTranslationController realtimeTranslationController;

    private boolean isSubtitleActive = false;
    private float initialTouchX, initialTouchY, initialBallX, initialBallY;
    private int screenWidth, screenHeight;

    private static final int NOTIFICATION_ID = 1001;

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        Point size = new Point();
        Display display = windowManager.getDefaultDisplay();
        display.getSize(size);
        screenWidth = size.x;
        screenHeight = size.y;

        pageTranslationController = new PageTranslationController(this);
        realtimeTranslationController = new RealtimeTranslationController(this);

        createFloatingBall();
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        running = false;
        removeFloatingBall();
        removePopupMenu();
        if (isSubtitleActive) {
            realtimeTranslationController.stop();
            isSubtitleActive = false;
        }
        pageTranslationController.destroy();
        super.onDestroy();
    }

    // --- Floating Ball ---

    private void createFloatingBall() {
        floatingBallView = LayoutInflater.from(this).inflate(R.layout.view_floating_ball, null);
        ImageView ivBall = floatingBallView.findViewById(R.id.iv_floating_ball);

        ballParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        ballParams.gravity = Gravity.TOP | Gravity.START;
        ballParams.x = screenWidth - dpToPx(72);
        ballParams.y = screenHeight / 3;

        ivBall.setOnTouchListener(new View.OnTouchListener() {
            private static final int CLICK_THRESHOLD = 10;
            private float downX, downY;
            private boolean isDragging;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        initialBallX = ballParams.x;
                        initialBallY = ballParams.y;
                        downX = event.getRawX();
                        downY = event.getRawY();
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        if (Math.abs(dx) > CLICK_THRESHOLD || Math.abs(dy) > CLICK_THRESHOLD) {
                            isDragging = true;
                        }
                        if (isDragging) {
                            ballParams.x = (int) (initialBallX + event.getRawX() - initialTouchX);
                            ballParams.y = (int) (initialBallY + event.getRawY() - initialTouchY);
                            try {
                                windowManager.updateViewLayout(floatingBallView, ballParams);
                            } catch (Exception ignored) {}
                            dismissPopupMenu();
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (isDragging) {
                            snapToEdge();
                        } else {
                            toggleMenu();
                        }
                        return true;
                }
                return false;
            }

            private void snapToEdge() {
                int ballWidth = floatingBallView.getWidth();
                if (ballWidth == 0) ballWidth = dpToPx(56);
                int centerX = ballParams.x + ballWidth / 2;
                if (centerX < screenWidth / 2) {
                    ballParams.x = dpToPx(8);
                } else {
                    ballParams.x = screenWidth - ballWidth - dpToPx(8);
                }
                ballParams.y = Math.max(0, Math.min(ballParams.y, screenHeight - dpToPx(120)));
                try {
                    windowManager.updateViewLayout(floatingBallView, ballParams);
                } catch (Exception ignored) {}
            }
        });

        try {
            windowManager.addView(floatingBallView, ballParams);
        } catch (Exception e) {
            Log.e(TAG, "Failed to add floating ball", e);
        }
    }

    private void removeFloatingBall() {
        if (floatingBallView != null) {
            try {
                windowManager.removeView(floatingBallView);
            } catch (Exception ignored) {}
            floatingBallView = null;
        }
    }

    // --- Popup Menu ---

    private void toggleMenu() {
        if (popupMenuView != null) {
            dismissPopupMenu();
        } else {
            showPopupMenu();
        }
    }

    private void showPopupMenu() {
        removePopupMenu();

        try {
            popupMenuView = LayoutInflater.from(this).inflate(R.layout.popup_menu, null);

            menuParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    getOverlayType(),
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
            );
            menuParams.gravity = Gravity.TOP | Gravity.START;

            int ballWidth = floatingBallView != null ? floatingBallView.getWidth() : dpToPx(56);
            if (ballWidth == 0) ballWidth = dpToPx(56);
            int ballCenterX = ballParams.x + ballWidth / 2;
            int ballTopY = ballParams.y;

            int menuWidth = dpToPx(200);
            menuParams.x = Math.max(0, ballCenterX - menuWidth / 2);
            menuParams.x = Math.min(menuParams.x, screenWidth - menuWidth);

            if (ballTopY > screenHeight / 2) {
                menuParams.y = ballTopY - dpToPx(220);
            } else {
                menuParams.y = ballTopY + ballWidth + dpToPx(8);
            }

            // Menu item clicks
            TextView menuTranslatePage = popupMenuView.findViewById(R.id.menu_translate_page);
            TextView menuSubtitle = popupMenuView.findViewById(R.id.menu_subtitle);
            TextView menuSettings = popupMenuView.findViewById(R.id.menu_settings);
            TextView menuHide = popupMenuView.findViewById(R.id.menu_hide);

            if (menuTranslatePage != null) {
                menuTranslatePage.setOnClickListener(v -> {
                    dismissPopupMenu();
                    startPageTranslation();
                });
            }

            if (menuSubtitle != null) {
                menuSubtitle.setOnClickListener(v -> {
                    dismissPopupMenu();
                    toggleSubtitle();
                });
            }

            if (menuSettings != null) {
                menuSettings.setOnClickListener(v -> {
                    dismissPopupMenu();
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                });
            }

            if (menuHide != null) {
                menuHide.setOnClickListener(v -> {
                    dismissPopupMenu();
                    stopSelf();
                });
            }

            windowManager.addView(popupMenuView, menuParams);
        } catch (SecurityException e) {
            Log.e(TAG, "Overlay permission denied", e);
            Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_SHORT).show();
            popupMenuView = null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to show menu", e);
            popupMenuView = null;
        }
    }

    private void startPageTranslation() {
        // Set up controller first so callback is ready when screenshot arrives
        pageTranslationController.start();

        // Launch transparent Activity to request MediaProjection
        Intent intent = new Intent(this, CaptureActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void toggleSubtitle() {
        if (isSubtitleActive) {
            realtimeTranslationController.stop();
            isSubtitleActive = false;
            showToast("实时字幕已关闭");
        } else {
            realtimeTranslationController.start();
            isSubtitleActive = true;
            showToast("实时字幕已开启，请播放英文视频");
        }
    }

    private void dismissPopupMenu() {
        removePopupMenu();
    }

    private void removePopupMenu() {
        if (popupMenuView != null) {
            try {
                windowManager.removeView(popupMenuView);
            } catch (Exception ignored) {}
            popupMenuView = null;
        }
    }

    // --- Helpers ---

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int getOverlayType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        return WindowManager.LayoutParams.TYPE_PHONE;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, App.CHANNEL_FLOATING_BALL)
                .setContentTitle("悬浮翻译助手")
                .setContentText("点击打开设置，拖拽悬浮球开始翻译")
                .setSmallIcon(R.drawable.ic_translate)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();
    }
}
