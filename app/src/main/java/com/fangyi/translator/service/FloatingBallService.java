package com.fangyi.translator.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.LayoutInflater;
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

    private static final String TAG = "FloatingBall";
    private static boolean running = false;
    private static final int NOTIFICATION_ID = 1001;

    private WindowManager wm;
    private View ballView;
    private View menuView;
    private WindowManager.LayoutParams ballLP;
    private WindowManager.LayoutParams menuLP;

    private PageTranslationController pageCtrl;
    private RealtimeTranslationController subtitleCtrl;
    private boolean subtitleActive;

    private int screenW, screenH;
    private int touchSlop;

    // Drag tracking
    private float downRawX, downRawY, downBallX, downBallY;
    private boolean dragging;

    private Handler handler = new Handler(Looper.getMainLooper());

    public static boolean isRunning() { return running; }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        Point p = new Point();
        wm.getDefaultDisplay().getRealSize(p);
        screenW = p.x;
        screenH = p.y;

        pageCtrl = new PageTranslationController(this);
        subtitleCtrl = new RealtimeTranslationController(this);

        createBall();
        startForeground(NOTIFICATION_ID, buildNotification());
        Log.d(TAG, "Service created, ball added. Screen=" + screenW + "x" + screenH);
    }

    // ==================== Ball ====================

    private void createBall() {
        // Inflate the floating ball layout (TextView with "译")
        ballView = LayoutInflater.from(this).inflate(R.layout.view_floating_ball, null);
        ballView.setClickable(true);
        ballView.setFocusableInTouchMode(true);

        ballLP = new WindowManager.LayoutParams(
                dp(56), dp(56),
                getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        ballLP.gravity = Gravity.TOP | Gravity.START;
        ballLP.x = screenW - dp(72);
        ballLP.y = screenH / 3;

        ballView.setOnTouchListener(this::onBallTouch);

        try { wm.addView(ballView, ballLP); }
        catch (Exception e) { Log.e(TAG, "addView failed", e); }
    }

    private boolean onBallTouch(View v, MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = e.getRawX();
                downRawY = e.getRawY();
                downBallX = ballLP.x;
                downBallY = ballLP.y;
                dragging = false;
                // Click feedback animation
                v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).start();
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(e.getRawX() - downRawX);
                float dy = Math.abs(e.getRawY() - downRawY);
                if (!dragging && (dx > touchSlop || dy > touchSlop)) {
                    dragging = true;
                    v.animate().cancel();
                    v.setScaleX(1f);
                    v.setScaleY(1f);
                    dismissMenu();
                }
                if (dragging) {
                    ballLP.x = (int) (downBallX + e.getRawX() - downRawX);
                    ballLP.y = (int) (downBallY + e.getRawY() - downRawY);
                    wm.updateViewLayout(ballView, ballLP);
                }
                return true;

            case MotionEvent.ACTION_UP:
                v.animate().scaleX(1f).scaleY(1f).setDuration(80).start();
                if (!dragging) {
                    // It's a tap!
                    handler.postDelayed(() -> toggleMenu(), 50);
                } else {
                    snapToEdge();
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                v.animate().scaleX(1f).scaleY(1f).setDuration(80).start();
                dragging = false;
                return true;
        }
        return false;
    }

    private void snapToEdge() {
        int bw = ballView.getWidth();
        if (bw == 0) bw = dp(56);
        ballLP.x = (ballLP.x + bw / 2 < screenW / 2) ? dp(8) : screenW - bw - dp(8);
        ballLP.y = Math.max(0, Math.min(ballLP.y, screenH - bw - dp(60)));
        wm.updateViewLayout(ballView, ballLP);
    }

    // ==================== Menu ====================

    private void toggleMenu() {
        if (menuView != null) { dismissMenu(); return; }
        showMenu();
    }

    private void showMenu() {
        dismissMenu();

        menuView = LayoutInflater.from(this).inflate(R.layout.popup_menu, null);
        menuLP = new WindowManager.LayoutParams(
                dp(200), WindowManager.LayoutParams.WRAP_CONTENT,
                getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        menuLP.gravity = Gravity.TOP | Gravity.START;

        // Position relative to ball
        int bw = ballView.getWidth(); if (bw == 0) bw = dp(56);
        int cx = ballLP.x + bw / 2;
        int cy = ballLP.y;

        menuLP.x = Math.max(0, Math.min(cx - dp(100), screenW - dp(200)));
        menuLP.y = (cy > screenH / 2) ? cy - dp(220) : cy + bw + dp(8);

        // Setup click handlers
        menuView.findViewById(R.id.menu_translate_page).setOnClickListener(v -> {
            dismissMenu();
            toast("正在请求截图权限...");
            pageCtrl.start();
            startActivity(new Intent(this, CaptureActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        });

        TextView btnSub = menuView.findViewById(R.id.menu_subtitle);
        updateSubtitleBtn(btnSub);
        btnSub.setOnClickListener(v -> {
            dismissMenu();
            if (subtitleActive) {
                subtitleCtrl.stop();
                subtitleActive = false;
                toast("字幕已停止");
            } else {
                subtitleCtrl.start();
                subtitleActive = true;
                toast("字幕已启动，请播放英文视频");
            }
        });

        menuView.findViewById(R.id.menu_settings).setOnClickListener(v -> {
            dismissMenu();
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        });

        menuView.findViewById(R.id.menu_hide).setOnClickListener(v -> {
            dismissMenu();
            stopSelf();
        });

        try { wm.addView(menuView, menuLP); }
        catch (Exception e) { Log.e(TAG, "menu addView failed", e); menuView = null; }
    }

    private void updateSubtitleBtn(TextView btn) {
        if (subtitleActive) {
            btn.setText("停止字幕翻译");
            btn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_close, 0, 0, 0);
        } else {
            btn.setText("视频语音字幕翻译");
            btn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_play, 0, 0, 0);
        }
    }

    private void dismissMenu() {
        if (menuView == null) return;
        try { wm.removeView(menuView); } catch (Exception e) {}
        menuView = null;
    }

    // ==================== Lifecycle ====================

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        running = false;
        if (subtitleActive) { subtitleCtrl.stop(); subtitleActive = false; }
        pageCtrl.destroy();
        dismissMenu();
        try { if (ballView != null) wm.removeView(ballView); } catch (Exception e) {}
        super.onDestroy();
    }

    // ==================== Helpers ====================

    private int getOverlayType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String msg) {
        handler.post(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    private Notification buildNotification() {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, App.CHANNEL_FLOATING_BALL)
                .setContentTitle("悬浮翻译助手")
                .setContentText("运行中 - 点击悬浮球开始翻译")
                .setSmallIcon(R.drawable.ic_translate)
                .setContentIntent(pi)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}
